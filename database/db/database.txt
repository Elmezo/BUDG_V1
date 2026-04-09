

CREATE TABLE `app_config` (
  `config_key` varchar(45) NOT NULL,
  `definition` text DEFAULT NULL,
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `association_origin` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `association_origin_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `fk_assocorig_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revinfo` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_assocorig_main` FOREIGN KEY (`ID`) REFERENCES `association_origin` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Data_type_ID` int(11) DEFAULT NULL,
  `Requirement_ID` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `Origination` int(11) DEFAULT NULL,
  `Editability` int(11) DEFAULT NULL,
  `Editability_role` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Definition` varchar(256) DEFAULT NULL,
  `Is_Mandatory` int(11) DEFAULT NULL,
  `Is_PrimaryKey` int(11) DEFAULT NULL,
  `Rank` int(11) DEFAULT NULL,
  `Business_Logic` varchar(256) DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT current_timestamp(),
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `Confidence_score` float DEFAULT NULL,
  `DataLength` int(11) DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Data_type_ID` (`Data_type_ID`),
  KEY `Requirement_ID` (`Requirement_ID`),
  KEY `Dataset_ID` (`Dataset_ID`),
  KEY `Glossary_ID` (`Glossary_ID`),
  KEY `Origination` (`Origination`),
  KEY `Editability` (`Editability`),
  KEY `Editability_role` (`Editability_role`),
  KEY `CreatedBy` (`CreatedBy`),
  KEY `Last_UpdatedUser_ID` (`Last_UpdatedUser_ID`),
  CONSTRAINT `attribute_ibfk_1` FOREIGN KEY (`Data_type_ID`) REFERENCES `attribute_datatype` (`ID`),
  CONSTRAINT `attribute_ibfk_10` FOREIGN KEY (`Data_type_ID`) REFERENCES `attribute_datatype` (`ID`),
  CONSTRAINT `attribute_ibfk_11` FOREIGN KEY (`Requirement_ID`) REFERENCES `requirement` (`ID`),
  CONSTRAINT `attribute_ibfk_12` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `attribute_ibfk_13` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `attribute_ibfk_14` FOREIGN KEY (`Origination`) REFERENCES `attribute_origination` (`ID`),
  CONSTRAINT `attribute_ibfk_15` FOREIGN KEY (`Editability`) REFERENCES `attribute_editability` (`ID`),
  CONSTRAINT `attribute_ibfk_16` FOREIGN KEY (`Editability_role`) REFERENCES `attribute_edit_role` (`ID`),
  CONSTRAINT `attribute_ibfk_17` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_ibfk_18` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_ibfk_2` FOREIGN KEY (`Requirement_ID`) REFERENCES `requirement` (`ID`),
  CONSTRAINT `attribute_ibfk_3` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `attribute_ibfk_4` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `attribute_ibfk_5` FOREIGN KEY (`Origination`) REFERENCES `attribute_origination` (`ID`),
  CONSTRAINT `attribute_ibfk_6` FOREIGN KEY (`Editability`) REFERENCES `attribute_editability` (`ID`),
  CONSTRAINT `attribute_ibfk_7` FOREIGN KEY (`Editability_role`) REFERENCES `attribute_edit_role` (`ID`),
  CONSTRAINT `attribute_ibfk_8` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_ibfk_9` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Attribute_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Attribute_DataType` FOREIGN KEY (`Data_type_ID`) REFERENCES `attribute_datatype` (`ID`),
  CONSTRAINT `fk_Attribute_Dataset` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_Attribute_Editability` FOREIGN KEY (`Editability`) REFERENCES `attribute_editability` (`ID`),
  CONSTRAINT `fk_Attribute_EditabilityRole` FOREIGN KEY (`Editability_role`) REFERENCES `attribute_edit_role` (`ID`),
  CONSTRAINT `fk_Attribute_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_Attribute_LastUpdatedUser` FOREIGN KEY (`Last_UpdatedUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Attribute_Origination` FOREIGN KEY (`Origination`) REFERENCES `attribute_origination` (`ID`),
  CONSTRAINT `fk_Attribute_Requirement` FOREIGN KEY (`Requirement_ID`) REFERENCES `requirement` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_alias_name` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name_Type` int(11) DEFAULT NULL,
  `AttributeID` int(11) DEFAULT NULL,
  `Name` varchar(45) DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Name_Type` (`Name_Type`),
  KEY `AttributeID` (`AttributeID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `attribute_alias_name_ibfk_1` FOREIGN KEY (`Name_Type`) REFERENCES `attribute_name_type` (`ID`),
  CONSTRAINT `attribute_alias_name_ibfk_2` FOREIGN KEY (`AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `attribute_alias_name_ibfk_3` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_alias_name_ibfk_4` FOREIGN KEY (`Name_Type`) REFERENCES `attribute_name_type` (`ID`),
  CONSTRAINT `attribute_alias_name_ibfk_5` FOREIGN KEY (`AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `attribute_alias_name_ibfk_6` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AttrAlias_Attribute` FOREIGN KEY (`AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_AttrAlias_LastUpdate` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AttrAlias_NameType` FOREIGN KEY (`Name_Type`) REFERENCES `attribute_name_type` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Data_type_ID` int(11) DEFAULT NULL,
  `Requirement_ID` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `Origination` int(11) DEFAULT NULL,
  `Editability` int(11) DEFAULT NULL,
  `Editability_role` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Definition` varchar(256) DEFAULT NULL,
  `Is_Mandatory` int(11) DEFAULT NULL,
  `Is_PrimaryKey` int(11) DEFAULT NULL,
  `Rank` int(11) DEFAULT NULL,
  `Business_Logic` varchar(256) DEFAULT NULL,
  `CreatedDatetime` datetime DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `Confidence_score` float DEFAULT NULL,
  `DataLength` int(11) DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Last_UpdatedUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `attribute_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `attribute_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_Attribute_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_audit_history` (
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `id` int(11) NOT NULL COMMENT 'Attribute ID',
  `object` varchar(100) DEFAULT NULL COMMENT 'Object type (e.g., Attribute, Stakeholder)',
  `event` varchar(100) DEFAULT NULL COMMENT 'Event type (e.g., Details, link)',
  `updateType` varchar(50) DEFAULT NULL COMMENT 'Update type (e.g., Added, Updated, Removed)',
  `field` varchar(255) DEFAULT NULL COMMENT 'Field name that was changed',
  `from` text DEFAULT NULL COMMENT 'Old value',
  `to` text DEFAULT NULL COMMENT 'New value',
  `author` varchar(255) DEFAULT NULL COMMENT 'User who made the change',
  `date` datetime DEFAULT NULL COMMENT 'Date of change',
  `lastChange` datetime DEFAULT NULL COMMENT 'Last change timestamp',
  PRIMARY KEY (`auditidpk`),
  KEY `idx_attribute_id` (`id`),
  KEY `idx_date` (`date`),
  KEY `idx_object` (`object`)
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Audit history for attribute changes';



CREATE TABLE `attribute_datatype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUpdated_UserID` (`LastUpdated_UserID`),
  CONSTRAINT `attribute_datatype_ibfk_1` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_datatype_ibfk_2` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AttrDataType_LastUpdatedUser` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_edit_role` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUpdated_UserID` (`LastUpdated_UserID`),
  CONSTRAINT `attribute_edit_role_ibfk_1` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_edit_role_ibfk_2` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AttrEditRole_LastUpdatedUser` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_editability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUpdated_UserID` (`LastUpdated_UserID`),
  CONSTRAINT `attribute_editability_ibfk_1` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_editability_ibfk_2` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AttrEditability_LastUpdatedUser` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_name_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_origination` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUpdated_UserID` (`LastUpdated_UserID`),
  CONSTRAINT `attribute_origination_ibfk_1` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `attribute_origination_ibfk_2` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AttrOrigination_LastUpdatedUser` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_x_attribute` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Relation_Type` int(11) DEFAULT NULL,
  `Source_AttributeID` int(11) DEFAULT NULL,
  `Target_AttributeID` int(11) DEFAULT NULL,
  `Relation_Scope` int(11) DEFAULT NULL,
  `Relation_Method` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Relation_Type` (`Relation_Type`),
  KEY `Source_AttributeID` (`Source_AttributeID`),
  KEY `Target_AttributeID` (`Target_AttributeID`),
  KEY `Relation_Scope` (`Relation_Scope`),
  KEY `Relation_Method` (`Relation_Method`),
  KEY `Last_UpdateUserID` (`Last_UpdateUserID`),
  CONSTRAINT `attribute_x_attribute_ibfk_1` FOREIGN KEY (`Relation_Type`) REFERENCES `attribute_x_attribute_relationtype` (`ID`),
  CONSTRAINT `attribute_x_attribute_ibfk_2` FOREIGN KEY (`Source_AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `attribute_x_attribute_ibfk_3` FOREIGN KEY (`Target_AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `attribute_x_attribute_ibfk_4` FOREIGN KEY (`Relation_Scope`) REFERENCES `attribute_x_attribute_relationscope` (`ID`),
  CONSTRAINT `attribute_x_attribute_ibfk_5` FOREIGN KEY (`Relation_Method`) REFERENCES `interface` (`id`),
  CONSTRAINT `attribute_x_attribute_ibfk_6` FOREIGN KEY (`Last_UpdateUserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AxA_LastUpdateUser` FOREIGN KEY (`Last_UpdateUserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AxA_Method_Interface` FOREIGN KEY (`Relation_Method`) REFERENCES `interface` (`id`),
  CONSTRAINT `fk_AxA_RelType` FOREIGN KEY (`Relation_Type`) REFERENCES `attribute_x_attribute_relationtype` (`ID`),
  CONSTRAINT `fk_AxA_Scope` FOREIGN KEY (`Relation_Scope`) REFERENCES `attribute_x_attribute_relationscope` (`ID`),
  CONSTRAINT `fk_AxA_SourceAttr` FOREIGN KEY (`Source_AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_AxA_TargetAttr` FOREIGN KEY (`Target_AttributeID`) REFERENCES `attribute` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_x_attribute_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Relation_Type` int(11) DEFAULT NULL,
  `Source_AttributeID` int(11) DEFAULT NULL,
  `Target_AttributeID` int(11) DEFAULT NULL,
  `Relation_Scope` int(11) DEFAULT NULL,
  `Relation_Method` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUserID` int(11) DEFAULT NULL,
  `Rev_Type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `attribute_x_attribute_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `attribute_x_attribute` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_x_attribute_relationscope` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_x_attribute_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_UpdateUserID` (`Last_UpdateUserID`),
  CONSTRAINT `attribute_x_attribute_relationtype_ibfk_1` FOREIGN KEY (`Last_UpdateUserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_AxA_RelType_LastUpdate` FOREIGN KEY (`Last_UpdateUserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `AttributeID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_attxip_attr_idx` (`AttributeID`),
  KEY `fk_attxip_obj_idx` (`Object_x_ipid`),
  KEY `fk_attxip_user_idx` (`Last_UpdateUser_ID`),
  CONSTRAINT `fk_AxObjPeople_Attr` FOREIGN KEY (`AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_AxObjPeople_Object` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_AxObjPeople_User` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_attxip_attr` FOREIGN KEY (`AttributeID`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_attxip_obj` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_attxip_user` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `attribute_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `AttributeID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_AxObjPeople_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `attribute_x_objectxpeople` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Action` varchar(255) DEFAULT NULL,
  `Field_Name` varchar(255) DEFAULT NULL,
  `Old_Value` mediumtext DEFAULT NULL,
  `New_Value` mediumtext DEFAULT NULL,
  `Created_time` datetime DEFAULT NULL,
  `UserName` varchar(255) DEFAULT NULL,
  `Data_Store_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Data_Store_ID` (`Data_Store_ID`),
  CONSTRAINT `fk_audit_datastore` FOREIGN KEY (`Data_Store_ID`) REFERENCES `data_store` (`Dataset_ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `auth_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `jwt_validity_seconds` int(11) NOT NULL DEFAULT 7200,
  `refresh_validity_seconds` int(11) NOT NULL DEFAULT 2592000,
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `auth_sessions` (
  `session_id` varchar(64) NOT NULL,
  `user_id` int(11) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `user_agent` varchar(255) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `last_activity` timestamp NOT NULL DEFAULT current_timestamp(),
  `device_info` varchar(255) DEFAULT NULL,
  `location` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`session_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `auth_sessions_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `people` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `auth_tokens` (
  `token_id` varchar(128) NOT NULL,
  `user_id` int(11) NOT NULL,
  `type` enum('ACCESS','REFRESH') NOT NULL,
  `is_revoked` tinyint(1) DEFAULT 0,
  `issued_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `expires_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `session_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`token_id`),
  KEY `user_id` (`user_id`),
  KEY `idx_auth_tokens_session` (`session_id`),
  CONSTRAINT `auth_tokens_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `people` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `automation_level` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUpdate_UserID` (`LastUpdate_UserID`),
  CONSTRAINT `fk_AutomationLevel_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `business_area` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `deletedatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BArea_Parent` (`Parent_ID`),
  KEY `fk_BArea_IsPublic` (`Is_Public`),
  KEY `fk_BArea_Status` (`Status`),
  KEY `fk_BArea_Lifecycle` (`Lifecycle`),
  KEY `fk_BArea_LastUser` (`LastUpdate_UserID`),
  KEY `createdby_id` (`createdby_id`),
  CONSTRAINT `business_area_ibfk_1` FOREIGN KEY (`createdby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_BArea_IsPublic` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_BArea_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_BArea_Lifecycle` FOREIGN KEY (`Lifecycle`) REFERENCES `business_area_lifecycle` (`ID`),
  CONSTRAINT `fk_BArea_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_BArea_Status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `business_area_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_BArea_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `business_area` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `business_area_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `business_area_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `business_area` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=234 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `business_area_lifecycle` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BAreaLifecycle_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_BAreaLifecycle_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_glossary` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXGloss_BArea` (`BusinessArea_ID`),
  KEY `fk_BXGloss_Glossary` (`Glossary_ID`),
  KEY `fk_BXGloss_RelType` (`RelationType`),
  KEY `fk_BXGloss_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_BXGloss_BArea` FOREIGN KEY (`BusinessArea_ID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_BXGloss_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_BXGloss_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_BXGloss_RelType` FOREIGN KEY (`RelationType`) REFERENCES `businessarea_x_glossary_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_BXGloss_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `businessarea_x_glossary` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_glossary_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXGlossRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_BXGlossRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `BusinessAreaID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXObjPeople_Object` (`Object_x_ipid`),
  KEY `fk_BXObjPeople_BArea` (`BusinessAreaID`),
  KEY `fk_BXObjPeople_User` (`Last_UpdateUser_ID`),
  CONSTRAINT `fk_BXObjPeople_BArea` FOREIGN KEY (`BusinessAreaID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_BXObjPeople_Object` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_BXObjPeople_User` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `BusinessAreaID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_BXObjPeople_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `businessarea_x_objectxpeople` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_process` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXProcess_BArea` (`BusinessArea_ID`),
  KEY `fk_BXProcess_Process` (`Process_ID`),
  KEY `fk_BXProcess_RelType` (`RelationType`),
  KEY `fk_BXProcess_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_BXProcess_BArea` FOREIGN KEY (`BusinessArea_ID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_BXProcess_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_BXProcess_Process` FOREIGN KEY (`Process_ID`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_BXProcess_RelType` FOREIGN KEY (`RelationType`) REFERENCES `businessarea_x_process_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_process_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_BXProcess_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `businessarea_x_process` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_process_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXProcessRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_BXProcessRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_system` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXSystem_BArea` (`BusinessArea_ID`),
  KEY `fk_BXSystem_System` (`System_ID`),
  KEY `fk_BXSystem_RelType` (`RelationType`),
  KEY `fk_BXSystem_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_BXSystem_BArea` FOREIGN KEY (`BusinessArea_ID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_BXSystem_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_BXSystem_RelType` FOREIGN KEY (`RelationType`) REFERENCES `businessarea_x_system_relationtype` (`ID`),
  CONSTRAINT `fk_BXSystem_System` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_system_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_BXSystem_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `businessarea_x_system` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `businessarea_x_system_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_BXSystemRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_BXSystemRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capabilit_x_follow` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXFollow_CapE` (`Capability_ID`),
  KEY `fk_CXFollow_FollowE` (`follow_id`),
  KEY `fk_CXFollow_LastUserE` (`LastUpdate_UserID`),
  KEY `idx_capability_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_CXFollokkw_FollowE` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_CXFollowoo_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXFollowppp_LastUserE` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Classification` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Capability_Type` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Capability_Parent` (`Parent_ID`),
  KEY `fk_Capability_IsPublic` (`Is_Public`),
  KEY `fk_Capability_Class` (`Classification`),
  KEY `fk_Capability_Status` (`Status`),
  KEY `fk_Capability_Lifecycle` (`Lifecycle`),
  KEY `fk_Capability_Type` (`Capability_Type`),
  KEY `fk_Capability_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_Capability_Class` FOREIGN KEY (`Classification`) REFERENCES `capability_classification` (`ID`),
  CONSTRAINT `fk_Capability_IsPublic` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_Capability_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Capability_Lifecycle` FOREIGN KEY (`Lifecycle`) REFERENCES `capability_lifecyle` (`ID`),
  CONSTRAINT `fk_Capability_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_Capability_Status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_Capability_Type` FOREIGN KEY (`Capability_Type`) REFERENCES `capability_type` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Classification` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Capability_Type` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_Capability_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `capability` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `capability_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `capability` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=139 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_classification` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CapClass_Parent` (`Parent_ID`),
  KEY `fk_CapClass_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CapClass_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CapClass_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `capability_classification` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_lifecyle` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CapLifecycle_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CapLifecycle_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CapType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CapType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_businessarea` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `Capability_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXBArea_BAreEa` (`BusinessArea_ID`),
  KEY `fk_CXBArea_CapE` (`Capability_ID`),
  KEY `fk_CXBArea_RelTEype` (`RelationType`),
  KEY `fk_CXBArea_LastUEser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXBArea_BAreEa` FOREIGN KEY (`BusinessArea_ID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_CXBArea_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXBArea_LastUEser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXBArea_RelTEype` FOREIGN KEY (`RelationType`) REFERENCES `businessarea_x_system_relationtype` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_businessarea_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `Capability_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXBArea_Audit_ERev` FOREIGN KEY (`ID`) REFERENCES `capability_x_businessarea` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_businessarea_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXBAreaRelType_LaEstUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXBAreaRelType_LaEstUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_capability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Source_ID` int(11) DEFAULT NULL,
  `Target_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXCap_SourceE` (`Source_ID`),
  KEY `fk_CXCap_TargetE` (`Target_ID`),
  KEY `fk_CXCap_RelTypeE` (`RelationType`),
  KEY `fk_CXCap_LastUserE` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXCap_LastUserE` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXCap_RelTypeE` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_capability_relationtype` (`ID`),
  CONSTRAINT `fk_CXCap_SourceE` FOREIGN KEY (`Source_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXCap_TargetE` FOREIGN KEY (`Target_ID`) REFERENCES `capability` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_capability_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Source_ID` int(11) DEFAULT NULL,
  `Target_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXCap_Audit_RevE` FOREIGN KEY (`ID`) REFERENCES `capability_x_capability` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_capability_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_client` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXClient_CapE` (`Capability_ID`),
  KEY `fk_CXClient_ClientE` (`Client_ID`),
  KEY `fk_CXClient_RelTypeE` (`RelationType`),
  KEY `fk_CXClient_LastUserE` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXClient_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXClient_ClientE` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXClient_LastUserE` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXClient_RelTypeE` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_client_relationtype` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_client_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXClient_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `capability_x_client` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_client_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXClientRelType_LastUseEr` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXClientRelType_LastUseEr` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_follow` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXFollow_CapE` (`Capability_ID`),
  KEY `fk_CXFollow_FollowE` (`follow_id`),
  KEY `fk_CXFollow_LastUserE` (`LastUpdate_UserID`),
  KEY `idx_capability_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_CXFollow_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXFollow_FollowE` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_CXFollow_LastUserE` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_glossary` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXGloss_CapE` (`Capability_ID`),
  KEY `fk_CXGloss_GlossaryE` (`Glossary_ID`),
  KEY `fk_CXGloss_RelTypeE` (`RelationType`),
  KEY `fk_CXGloss_LastUserE` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXGloss_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXGloss_GlossaryE` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_CXGloss_LastUserE` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXGloss_RelTypeE` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_glossary_relationtype` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXGloss_Audit_RevE` FOREIGN KEY (`ID`) REFERENCES `capability_x_glossary` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_glossary_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXGlossRelType_LastUserE` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXGlossRelType_LastUserE` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXLegal_CapEE` (`Capability_ID`),
  KEY `fk_CXLegal_LegalE` (`Legal_ID`),
  KEY `fk_CXLegal_RelTypeE` (`RelationType`),
  KEY `fk_CXLegal_LastUserE` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXLegal_CapEE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXLegal_LastUserE` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXLegal_LegalE` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_CXLegal_RelTypeE` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_legal_relationtype` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_legal_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXLegal_Audit_RevE` FOREIGN KEY (`ID`) REFERENCES `capability_x_legal` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_legal_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXLegalRelType_LastUserE` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXLegalRelType_LastUserE` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `CapabilityID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXObjPeople_ObjectE` (`Object_x_ipid`),
  KEY `fk_CXObjPeople_CapE` (`CapabilityID`),
  KEY `fk_CXObjPeople_UserE` (`Last_UpdateUser_ID`),
  CONSTRAINT `fk_CXObjPeople_CapE` FOREIGN KEY (`CapabilityID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXObjPeople_ObjectE` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_CXObjPeople_UserE` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `CapabilityID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXObjPeople_Audit_RevE` FOREIGN KEY (`ID`) REFERENCES `capability_x_objectxpeople` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_process` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProcess_CapE` (`Capability_ID`),
  KEY `fk_CXProcess_ProcEess` (`Process_ID`),
  KEY `fk_CXProcess_RelTyEpe` (`RelationType`),
  KEY `fk_CXProcess_LastUsEer` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXProcess_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXProcess_LastUsEer` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXProcess_ProcEess` FOREIGN KEY (`Process_ID`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_CXProcess_RelTyEpe` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_process_relationtype` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_process_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXProcess_Audit_REev` FOREIGN KEY (`ID`) REFERENCES `capability_x_process` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_process_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProcessRelType_LastUseEr` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXProcessRelType_LastUseEr` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_product` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProduct_CapE` (`Capability_ID`),
  KEY `fk_CXProduct_ProdEuct` (`Product_ID`),
  KEY `fk_CXProduct_RelTyEpe` (`RelationType`),
  KEY `fk_CXProduct_LastUsEer` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXProduct_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXProduct_LastUsEer` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXProduct_ProdEuct` FOREIGN KEY (`Product_ID`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_CXProduct_RelTyEpe` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_product_relationtype` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_product_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXProduct_Audit_REev` FOREIGN KEY (`ID`) REFERENCES `capability_x_product` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_product_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProductRelType_LEastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXProductRelType_LEastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_system` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXSystem_CapE` (`Capability_ID`),
  KEY `fk_CXSystem_SysteEm` (`System_ID`),
  KEY `fk_CXSystem_RelTEype` (`RelationType`),
  KEY `fk_CXSystem_LastUsEer` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXSystem_CapE` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXSystem_LastUsEer` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXSystem_RelTEype` FOREIGN KEY (`RelationType`) REFERENCES `capability_x_system_relationtype` (`ID`),
  CONSTRAINT `fk_CXSystem_SysteEm` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_system_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Capability_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXSystem_Audit_REev` FOREIGN KEY (`ID`) REFERENCES `capability_x_system` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `capability_x_system_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXSystemRelType_LaEstUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXSystemRelType_LaEstUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Parent_ID` int(11) DEFAULT NULL,
  `Reference` varchar(255) DEFAULT NULL,
  `Summary` mediumtext DEFAULT NULL,
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_By` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  `CR_StatusID` int(11) DEFAULT NULL,
  `CR_TypeID` int(11) DEFAULT NULL,
  `CR_SeverityID` int(11) DEFAULT NULL,
  `CR_UrgencyID` int(11) DEFAULT NULL,
  `Process_InstanceID` int(11) DEFAULT NULL,
  `Estimated_BenefitID` int(11) DEFAULT NULL,
  `Estimated_CostID` int(11) DEFAULT NULL,
  `Visibility` int(11) DEFAULT NULL,
  `Mandatory_Workflow` tinyint(1) DEFAULT NULL,
  `Delta` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`Delta`)),
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=186 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_analysis` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `ChangeRequest_ID` int(11) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `ChangeRequest_ID` (`ChangeRequest_ID`),
  KEY `LastUserChange` (`LastUserChange`),
  CONSTRAINT `fk_cranalysis_cr` FOREIGN KEY (`ChangeRequest_ID`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_currency` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  CONSTRAINT `fk_crcurry_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_entity` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  CONSTRAINT `fk_cre_ent_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_resolution` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Description` mediumtext DEFAULT NULL,
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  `ChangeRequest_ID` int(11) DEFAULT NULL,
  `CR_ResolutionStatus_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  KEY `ChangeRequest_ID` (`ChangeRequest_ID`),
  KEY `CR_ResolutionStatus_ID` (`CR_ResolutionStatus_ID`),
  CONSTRAINT `fk_crres_cr` FOREIGN KEY (`ChangeRequest_ID`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_crres_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_crres_status` FOREIGN KEY (`CR_ResolutionStatus_ID`) REFERENCES `changerequest_resolution_status` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_resolution_status` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  CONSTRAINT `fk_crresstat_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_severity` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  CONSTRAINT `fk_crsev_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`ID`),
  KEY `idx_lastuser` (`LastUserChange`),
  CONSTRAINT `fk_crtype_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_urgency` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_value` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Value` float DEFAULT NULL,
  `Type` enum('Beneift','Cost') DEFAULT NULL,
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  `CR_CurrencyID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  KEY `CR_CurrencyID` (`CR_CurrencyID`)
) ENGINE=InnoDB AUTO_INCREMENT=39 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequest_workflow_crtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Process_Definition_ID` int(11) NOT NULL,
  `CR_Type` varchar(255) NOT NULL,
  `Entity_ID` int(11) DEFAULT NULL,
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`ID`),
  KEY `idx_process_def` (`Process_Definition_ID`),
  KEY `idx_entity` (`Entity_ID`),
  CONSTRAINT `fk_workflow_mapping_process` FOREIGN KEY (`Process_Definition_ID`) REFERENCES `process_definition` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `changerequeststatus` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` mediumtext DEFAULT NULL,
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUserChange` (`LastUserChange`),
  CONSTRAINT `fk_crstatus_lastuser` FOREIGN KEY (`LastUserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `cia_rating` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Values` int(11) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_Updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `cia_rating_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `cia_rating_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `IsPublic` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `LongName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `DeleteDatetime` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Client_Parent` (`Parent_ID`),
  KEY `fk_Client_Lifecycle` (`Lifecycle`),
  KEY `fk_Client_Status` (`Status`),
  KEY `fk_Client_IsPublic` (`IsPublic`),
  KEY `fk_Client_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_Client_IsPublic` FOREIGN KEY (`IsPublic`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_Client_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Client_Lifecycle` FOREIGN KEY (`Lifecycle`) REFERENCES `client_lifecycle` (`ID`),
  CONSTRAINT `fk_Client_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_Client_Status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `IsPublic` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `LongName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_Client_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `client_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `client` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=101 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_lifecycle` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_ClientLifecycle_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_ClientLifecycle_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_dataset` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXDataset_Dataset` (`Dataset_ID`),
  KEY `fk_CXDataset_Client` (`Client_ID`),
  KEY `fk_CXDataset_RelType` (`RelationType`),
  KEY `fk_CXDataset_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXDataset_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXDataset_Dataset` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_CXDataset_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXDataset_RelType` FOREIGN KEY (`RelationType`) REFERENCES `client_x_dataset_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_dataset_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXDataset_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_dataset` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_dataset_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXDatasetRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXDatasetRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_follow` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Client_ID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXFollow_Client` (`Client_ID`),
  KEY `fk_CXFollow_Follow` (`follow_id`),
  KEY `fk_CXFollow_LastUser` (`LastUpdate_UserID`),
  KEY `idx_client_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_CXFollow_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXFollow_Follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_CXFollow_FollowC` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_CXFollow_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_glossary` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Glossary_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXGloss_Glossary` (`Glossary_ID`),
  KEY `fk_CXGloss_Client` (`Client_ID`),
  KEY `fk_CXGloss_RelType` (`RelationType`),
  KEY `fk_CXGloss_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXGloss_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXGloss_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_CXGloss_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXGloss_RelType` FOREIGN KEY (`RelationType`) REFERENCES `client_x_glossary_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Glossary_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXGloss_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_glossary` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_glossary_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXGlossRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXGlossRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `ClientID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXObjPeople_Object` (`Object_x_ipid`),
  KEY `fk_CXObjPeople_Client` (`ClientID`),
  KEY `fk_CXObjPeople_User` (`Last_UpdateUser_ID`),
  CONSTRAINT `fk_CXObjPeople_Client` FOREIGN KEY (`ClientID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXObjPeople_Object` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_CXObjPeople_User` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `ClientID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXObjPeople_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_objectxpeople` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_policy` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Policy_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXPolicy_Policy` (`Policy_ID`),
  KEY `fk_CXPolicy_Client` (`Client_ID`),
  KEY `fk_CXPolicy_RelType` (`RelationType`),
  KEY `fk_CXPolicy_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXPolicy_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXPolicy_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXPolicy_Policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_CXPolicy_RelType` FOREIGN KEY (`RelationType`) REFERENCES `client_x_policy_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=61 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_policy_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Policy_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXPolicy_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_policy` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_policy_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXPolicyRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXPolicyRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_process` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Process_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProcess_Process` (`Process_ID`),
  KEY `fk_CXProcess_Client` (`Client_ID`),
  KEY `fk_CXProcess_RelType` (`RelationType`),
  KEY `fk_CXProcess_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXProcess_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXProcess_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXProcess_Process` FOREIGN KEY (`Process_ID`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_CXProcess_RelType` FOREIGN KEY (`RelationType`) REFERENCES `client_x_process_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=35 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_process_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Process_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXProcess_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_process` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_process_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProcessRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXProcessRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_project` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Project_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProject_Project` (`Project_ID`),
  KEY `fk_CXProject_Client` (`Client_ID`),
  KEY `fk_CXProject_RelType` (`RelationType`),
  KEY `fk_CXProject_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXProject_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXProject_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXProject_Project` FOREIGN KEY (`Project_ID`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_CXProject_RelType` FOREIGN KEY (`RelationType`) REFERENCES `client_x_project_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_project_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Project_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXProject_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_project` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_project_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXProjectRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXProjectRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_system` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `System_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXSystem_System` (`System_ID`),
  KEY `fk_CXSystem_Client` (`Client_ID`),
  KEY `fk_CXSystem_RelType` (`RelationType`),
  KEY `fk_CXSystem_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXSystem_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_CXSystem_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXSystem_RelType` FOREIGN KEY (`RelationType`) REFERENCES `client_x_system_relationtype` (`ID`),
  CONSTRAINT `fk_CXSystem_System` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_system_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `System_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXSystem_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `client_x_system` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `client_x_system_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXSystemRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXSystemRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Classification` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Committee_Type` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Committee_Parent` (`Parent_ID`),
  KEY `fk_Committee_IsPublic` (`Is_Public`),
  KEY `fk_Committee_Class` (`Classification`),
  KEY `fk_Committee_Status` (`Status`),
  KEY `fk_Committee_Lifecycle` (`Lifecycle`),
  KEY `fk_Committee_Type` (`Committee_Type`),
  KEY `fk_Committee_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_Committee_Class` FOREIGN KEY (`Classification`) REFERENCES `committee_classification` (`ID`),
  CONSTRAINT `fk_Committee_IsPublic` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_Committee_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Committee_Lifecycle` FOREIGN KEY (`Lifecycle`) REFERENCES `committee_lifecycle` (`ID`),
  CONSTRAINT `fk_Committee_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `committee` (`ID`),
  CONSTRAINT `fk_Committee_Status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_Committee_Type` FOREIGN KEY (`Committee_Type`) REFERENCES `committee_type` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_audit` (
  `ID` int(11) NOT NULL,
  `rev_id` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Classification` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Committee_Type` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`rev_id`),
  UNIQUE KEY `Rev` (`rev_id`),
  CONSTRAINT `fk_Committee_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `committee` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `Committee_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `committee` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=180 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_classification` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CommClass_Parent` (`Parent_ID`),
  KEY `fk_CommClass_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CommClass_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CommClass_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `committee_classification` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_lifecycle` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CommLifecycle_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CommLifecycle_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CommType_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CommType_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_capability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Committee_ID` int(11) DEFAULT NULL,
  `Capability_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXCap_Comm` (`Committee_ID`),
  KEY `fk_CXCap_Cap` (`Capability_ID`),
  KEY `fk_CXCap_RelType` (`RelationType`),
  KEY `fk_CXCap_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXCap_Cap` FOREIGN KEY (`Capability_ID`) REFERENCES `capability` (`ID`),
  CONSTRAINT `fk_CXCap_Comm` FOREIGN KEY (`Committee_ID`) REFERENCES `committee` (`ID`),
  CONSTRAINT `fk_CXCap_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXCap_RelType` FOREIGN KEY (`RelationType`) REFERENCES `committee_x_capability_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_capability_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Committee_ID` int(11) DEFAULT NULL,
  `Capability_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXCap_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `committee_x_capability` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_capability_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXCapRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXCapRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_committee` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Source_ID` int(11) DEFAULT NULL,
  `Target_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXComm_Source` (`Source_ID`),
  KEY `fk_CXComm_Target` (`Target_ID`),
  KEY `fk_CXComm_RelType` (`RelationType`),
  KEY `fk_CXComm_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CXComm_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CXComm_RelType` FOREIGN KEY (`RelationType`) REFERENCES `committee_x_committee_relationtype` (`ID`),
  CONSTRAINT `fk_CXComm_Source` FOREIGN KEY (`Source_ID`) REFERENCES `committee` (`ID`),
  CONSTRAINT `fk_CXComm_Target` FOREIGN KEY (`Target_ID`) REFERENCES `committee` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_committee_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Source_ID` int(11) DEFAULT NULL,
  `Target_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXComm_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `committee_x_committee` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_committee_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXCommRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXCommRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_follow` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Committee_ID` int(11) DEFAULT NULL,
  `follow_id` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXFollow_FollowC` (`follow_id`),
  KEY `fk_CXFollow_LastUserC` (`LastUpdate_UserID`),
  KEY `fk_CXFollow_Committee` (`Committee_ID`),
  CONSTRAINT `fk_CXFollow_Committee` FOREIGN KEY (`Committee_ID`) REFERENCES `committee` (`ID`),
  CONSTRAINT `fk_CXFollow_LastUserC` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_X_ipid` int(11) DEFAULT NULL,
  `Committee_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CXObjPeople_ObjectC` (`Object_X_ipid`),
  KEY `fk_CXObjPeople_CommVV` (`Committee_ID`),
  KEY `fk_CXObjPeople_UserC` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_CXObjPeople_CommVV` FOREIGN KEY (`Committee_ID`) REFERENCES `committee` (`ID`),
  CONSTRAINT `fk_CXObjPeople_ObjectC` FOREIGN KEY (`Object_X_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_CXObjPeople_UserC` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `committee_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_X_ipid` int(11) DEFAULT NULL,
  `Committee_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `Revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CXObjPeople_Audit_RevC` FOREIGN KEY (`ID`) REFERENCES `committee_x_objectxpeople` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `cr_relationship` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Source_ID` int(11) NOT NULL,
  `Target_ID` int(11) NOT NULL,
  `CR_Relationship_Type_ID` int(11) NOT NULL,
  `Last_UserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`ID`),
  KEY `fk_cr_rel_source` (`Source_ID`),
  KEY `fk_cr_rel_target` (`Target_ID`),
  KEY `fk_cr_rel_type` (`CR_Relationship_Type_ID`),
  KEY `fk_cr_rel_userchange` (`Last_UserChange`),
  CONSTRAINT `fk_cr_rel_source` FOREIGN KEY (`Source_ID`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_cr_rel_target` FOREIGN KEY (`Target_ID`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_cr_rel_type` FOREIGN KEY (`CR_Relationship_Type_ID`) REFERENCES `cr_relationship_type` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_cr_rel_userchange` FOREIGN KEY (`Last_UserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `cr_relationship_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(255) NOT NULL,
  `Description` text DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `Last_UserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`ID`),
  KEY `fk_cr_type_userchange` (`Last_UserChange`),
  CONSTRAINT `fk_cr_type_userchange` FOREIGN KEY (`Last_UserChange`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `cr_stakeholders` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `User_ID` int(11) DEFAULT NULL,
  `Object_Role_ID` int(11) DEFAULT NULL,
  `LastUser_Change` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  `CR_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `User_ID` (`User_ID`),
  KEY `Object_Role_ID` (`Object_Role_ID`),
  KEY `LastUser_Change` (`LastUser_Change`),
  KEY `CR_ID` (`CR_ID`),
  CONSTRAINT `fk_crstake_cr` FOREIGN KEY (`CR_ID`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_crstake_lastuser` FOREIGN KEY (`LastUser_Change`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_crstake_objrole` FOREIGN KEY (`Object_Role_ID`) REFERENCES `object_role` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_crstake_user` FOREIGN KEY (`User_ID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `custom_field_data` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Custom_Field_Metadata_ID` int(11) DEFAULT NULL,
  `Custom_Field_Enum_ID` int(11) DEFAULT NULL,
  `Facet_Object_ID` int(11) DEFAULT NULL,
  `Custom_Field_Value` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CFD_Metadata` (`Custom_Field_Metadata_ID`),
  KEY `fk_CFD_Enum` (`Custom_Field_Enum_ID`),
  KEY `fk_CFD_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CFD_Enum` FOREIGN KEY (`Custom_Field_Enum_ID`) REFERENCES `custom_field_enum` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_CFD_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CFD_Metadata` FOREIGN KEY (`Custom_Field_Metadata_ID`) REFERENCES `custom_field_metadata` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=109 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `custom_field_data_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Custom_Field_Metadata_ID` int(11) DEFAULT NULL,
  `Custom_Field_Enum_ID` int(11) DEFAULT NULL,
  `Facet_Object_ID` int(11) DEFAULT NULL,
  `Custom_Field_Value` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_CFD_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `custom_field_data` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `custom_field_enum` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Custom_Field_Metadata_ID` int(11) DEFAULT NULL,
  `EnumValue` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CFE_Metadata` (`Custom_Field_Metadata_ID`),
  CONSTRAINT `fk_CFE_Metadata` FOREIGN KEY (`Custom_Field_Metadata_ID`) REFERENCES `custom_field_metadata` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=62 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `custom_field_metadata` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Module_ID` int(11) DEFAULT NULL,
  `CustomFieldName` varchar(128) DEFAULT NULL,
  `DisplayName` varchar(128) DEFAULT NULL,
  `is_Mandatory` tinyint(1) DEFAULT NULL,
  `is_Searchable` tinyint(1) DEFAULT NULL,
  `DataType` varchar(128) DEFAULT NULL,
  `Default_Value` longtext DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `Placeholder_Text` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_CFM_Module` (`Module_ID`),
  KEY `fk_CFM_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_CFM_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_CFM_Module` FOREIGN KEY (`Module_ID`) REFERENCES `module` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dashboard_widgets` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Dashboard_ID` int(11) NOT NULL,
  `Widget_Type` varchar(100) NOT NULL,
  `Widget_Template_ID` int(11) DEFAULT NULL,
  `Title` varchar(200) DEFAULT NULL,
  `Position_X` int(11) DEFAULT 0,
  `Position_Y` int(11) DEFAULT 0,
  `Width` int(11) DEFAULT 1,
  `Height` int(11) DEFAULT 1,
  `Widget_Order` int(11) DEFAULT 0,
  `Is_Visible` tinyint(1) NOT NULL DEFAULT 1,
  `Widget_Config` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`Widget_Config`)),
  `Created_At` timestamp NOT NULL DEFAULT current_timestamp(),
  `Updated_At` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_dashboard_order` (`Dashboard_ID`,`Widget_Order`),
  KEY `idx_dashboard_position` (`Dashboard_ID`,`Position_Y`,`Position_X`),
  CONSTRAINT `dashboard_widgets_ibfk_1` FOREIGN KEY (`Dashboard_ID`) REFERENCES `dashboards` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=213 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dashboard_x_user` (
  `Dashboard_ID` int(11) NOT NULL,
  `User_ID` int(11) NOT NULL,
  `sharing` int(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`Dashboard_ID`,`User_ID`),
  KEY `User_ID` (`User_ID`),
  KEY `dashboard_x_user_ibfk_3` (`sharing`),
  CONSTRAINT `dashboard_x_user_ibfk_1` FOREIGN KEY (`Dashboard_ID`) REFERENCES `dashboards` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `dashboard_x_user_ibfk_2` FOREIGN KEY (`User_ID`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `dashboard_x_user_ibfk_3` FOREIGN KEY (`sharing`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dashboards` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Title` varchar(145) NOT NULL,
  `Description` text DEFAULT NULL,
  `Is_Public` tinyint(1) NOT NULL DEFAULT 0,
  `Is_Default` tinyint(1) NOT NULL DEFAULT 0,
  `Created_By` int(11) DEFAULT NULL,
  `Created_At` timestamp NOT NULL DEFAULT current_timestamp(),
  `Updated_At` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `Layout_Type` varchar(50) DEFAULT 'grid',
  PRIMARY KEY (`ID`),
  KEY `dashboards_ibfk_1` (`Created_By`),
  CONSTRAINT `dashboards_ibfk_1` FOREIGN KEY (`Created_By`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `data_store` (
  `Dataset_ID` int(11) NOT NULL,
  PRIMARY KEY (`Dataset_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `ValueInfoID` int(11) DEFAULT NULL,
  `AccessControlType` int(11) DEFAULT NULL,
  `DatasetType` int(11) DEFAULT NULL,
  `locked` int(11) DEFAULT NULL,
  `MasterSource` int(11) DEFAULT NULL,
  `lifecycle` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `glossary` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `version` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `definition` varchar(256) DEFAULT NULL,
  `Usage` varchar(256) DEFAULT NULL,
  `AccessControlType_Desc` varchar(256) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `DQ_Score` int(11) DEFAULT NULL,
  `DQ_Green` int(11) DEFAULT NULL,
  `DQ_Amber` int(11) DEFAULT NULL,
  `Createdby_ID` int(11) DEFAULT NULL,
  `LastUpdateUser_id` int(11) DEFAULT NULL,
  `under_revision` tinyint(1) NOT NULL DEFAULT 0,
  `active_cr_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `AccessControlType` (`AccessControlType`),
  KEY `ValueInfoID` (`ValueInfoID`),
  KEY `DatasetType` (`DatasetType`),
  KEY `locked` (`locked`),
  KEY `MasterSource` (`MasterSource`),
  KEY `lifecycle` (`lifecycle`),
  KEY `glossary` (`glossary`),
  KEY `status` (`status`),
  KEY `Createdby_ID` (`Createdby_ID`),
  KEY `LastUpdateUser_id` (`LastUpdateUser_id`),
  KEY `idx_dataset_revision` (`under_revision`,`active_cr_id`),
  CONSTRAINT `dataset_ibfk_1` FOREIGN KEY (`AccessControlType`) REFERENCES `viewing` (`id`),
  CONSTRAINT `dataset_ibfk_10` FOREIGN KEY (`LastUpdateUser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_ibfk_11` FOREIGN KEY (`AccessControlType`) REFERENCES `viewing` (`id`),
  CONSTRAINT `dataset_ibfk_12` FOREIGN KEY (`ValueInfoID`) REFERENCES `dataset_value_info` (`ID`),
  CONSTRAINT `dataset_ibfk_13` FOREIGN KEY (`DatasetType`) REFERENCES `dataset_type` (`ID`),
  CONSTRAINT `dataset_ibfk_14` FOREIGN KEY (`locked`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_ibfk_15` FOREIGN KEY (`MasterSource`) REFERENCES `system` (`id`),
  CONSTRAINT `dataset_ibfk_16` FOREIGN KEY (`lifecycle`) REFERENCES `dataset_lifecycle` (`ID`),
  CONSTRAINT `dataset_ibfk_17` FOREIGN KEY (`glossary`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `dataset_ibfk_18` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `dataset_ibfk_19` FOREIGN KEY (`Createdby_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_ibfk_2` FOREIGN KEY (`ValueInfoID`) REFERENCES `dataset_value_info` (`ID`),
  CONSTRAINT `dataset_ibfk_20` FOREIGN KEY (`LastUpdateUser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_ibfk_3` FOREIGN KEY (`DatasetType`) REFERENCES `dataset_type` (`ID`),
  CONSTRAINT `dataset_ibfk_4` FOREIGN KEY (`locked`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_ibfk_5` FOREIGN KEY (`MasterSource`) REFERENCES `system` (`id`),
  CONSTRAINT `dataset_ibfk_6` FOREIGN KEY (`lifecycle`) REFERENCES `dataset_lifecycle` (`ID`),
  CONSTRAINT `dataset_ibfk_7` FOREIGN KEY (`glossary`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `dataset_ibfk_8` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `dataset_ibfk_9` FOREIGN KEY (`Createdby_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `ValueInfoID` int(11) DEFAULT NULL,
  `AccessControlType` int(11) DEFAULT NULL,
  `DatasetType` int(11) DEFAULT NULL,
  `locked` int(11) DEFAULT NULL,
  `MasterSource` int(11) DEFAULT NULL,
  `lifecycle` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `glossary` int(11) DEFAULT NULL,
  `RefNumber` varchar(45) DEFAULT NULL,
  `version` varchar(45) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `definition` varchar(256) DEFAULT NULL,
  `Usage` varchar(256) DEFAULT NULL,
  `AccessControlType_Desc` varchar(256) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `DQ_Score` int(11) DEFAULT NULL,
  `DQ_Green` int(11) DEFAULT NULL,
  `DQ_Amber` int(11) DEFAULT NULL,
  `Createdby_ID` int(11) DEFAULT NULL,
  `LastUpdateUser_id` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `dataset_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `dataset` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `dataset_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `dataset` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=560 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_changes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `change_request_id` int(11) NOT NULL,
  `object_id` int(11) NOT NULL,
  `nobject_id` int(11) NOT NULL,
  `area_key` varchar(100) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_dataset_changes_cr_obj_area` (`change_request_id`,`object_id`,`area_key`),
  KEY `idx_dataset_changes_obj` (`object_id`),
  KEY `idx_dataset_changes_cr` (`change_request_id`),
  CONSTRAINT `fk_dataset_changes_cr` FOREIGN KEY (`change_request_id`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_dataset_changes_object` FOREIGN KEY (`object_id`) REFERENCES `dataset` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_follow` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Dataset_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `Created_datetime` datetime DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Dataset_id` (`Dataset_id`),
  KEY `Follow_id` (`follow_id`),
  KEY `Last_updatedUser_ID` (`Last_updatedUser_ID`),
  KEY `idx_dataset_follow_follow_id` (`follow_id`),
  CONSTRAINT `dataset_follow_ibfk_1` FOREIGN KEY (`Dataset_id`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `dataset_follow_ibfk_2` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `dataset_follow_ibfk_3` FOREIGN KEY (`Last_updatedUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_follow_ibfk_4` FOREIGN KEY (`Dataset_id`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `dataset_follow_ibfk_6` FOREIGN KEY (`Last_updatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_lifecycle` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(250) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `dataset_lifecycle_ibfk_1` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_lifecycle_ibfk_2` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(250) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `dataset_type_ibfk_1` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_type_ibfk_2` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_value_availability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updateUser_ID` int(11) DEFAULT NULL,
  `last_updateDatetime` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updateUser_ID` (`Last_updateUser_ID`),
  CONSTRAINT `dataset_value_availability_ibfk_1` FOREIGN KEY (`Last_updateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_value_availability_ibfk_2` FOREIGN KEY (`Last_updateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_value_info` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Refresh_rate` int(11) DEFAULT NULL,
  `Availability` int(11) DEFAULT NULL,
  `Type` int(11) DEFAULT NULL,
  `Volumne` varchar(256) DEFAULT NULL,
  `Refresh_rate_comment` varchar(256) DEFAULT NULL,
  `Availability_comment` varchar(256) DEFAULT NULL,
  `Created_datetime` datetime DEFAULT NULL,
  `last_updateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Refresh_rate` (`Refresh_rate`),
  KEY `Availability` (`Availability`),
  KEY `Type` (`Type`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `dataset_value_info_ibfk_1` FOREIGN KEY (`Refresh_rate`) REFERENCES `dataset_value_refreshrate` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_2` FOREIGN KEY (`Availability`) REFERENCES `dataset_value_availability` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_3` FOREIGN KEY (`Type`) REFERENCES `dataset_value_type` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_4` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_5` FOREIGN KEY (`Refresh_rate`) REFERENCES `dataset_value_refreshrate` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_6` FOREIGN KEY (`Availability`) REFERENCES `dataset_value_availability` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_7` FOREIGN KEY (`Type`) REFERENCES `dataset_value_type` (`ID`),
  CONSTRAINT `dataset_value_info_ibfk_8` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_value_info_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Refresh_rate` int(11) DEFAULT NULL,
  `Availability` int(11) DEFAULT NULL,
  `Type` int(11) DEFAULT NULL,
  `Volumne` varchar(256) DEFAULT NULL,
  `Refresh_rate_comment` varchar(256) DEFAULT NULL,
  `Availability_comment` varchar(256) DEFAULT NULL,
  `Created_datetime` datetime DEFAULT NULL,
  `last_updateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `dataset_value_info_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `dataset_value_info_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_value_refreshrate` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updateUser_ID` int(11) DEFAULT NULL,
  `last_updateDatetime` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updateUser_ID` (`Last_updateUser_ID`),
  CONSTRAINT `dataset_value_refreshrate_ibfk_1` FOREIGN KEY (`Last_updateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_value_refreshrate_ibfk_2` FOREIGN KEY (`Last_updateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_value_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_dataset` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Source_ID` int(11) DEFAULT NULL,
  `Target_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Source_ID` (`Source_ID`),
  KEY `Target_ID` (`Target_ID`),
  KEY `RelationType` (`RelationType`),
  KEY `LastUpdate_UserID` (`LastUpdate_UserID`),
  CONSTRAINT `fk_DatasetXDataset_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_DatasetXDataset_RelationType` FOREIGN KEY (`RelationType`) REFERENCES `dataset_x_dataset_relationtype` (`ID`),
  CONSTRAINT `fk_DatasetXDataset_Source` FOREIGN KEY (`Source_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_DatasetXDataset_Target` FOREIGN KEY (`Target_ID`) REFERENCES `dataset` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_dataset_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_DXL_Dataset` (`Dataset_ID`),
  KEY `fk_DXL_Legal` (`Legal_ID`),
  KEY `fk_DXL_RelType` (`RelationType`),
  KEY `fk_DXL_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_DXL_Dataset` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_DXL_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_DXL_Legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_DXL_RelType` FOREIGN KEY (`RelationType`) REFERENCES `dataset_x_legal_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_legal_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_DXL_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `dataset_x_legal` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_legal_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_DXLRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_DXLRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_legalentity_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_DXLEntRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_DXLEntRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdate_Datetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Object_x_ipid` (`Object_x_ipid`),
  KEY `Dataset_ID` (`Dataset_ID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `dataset_x_objectxpeople_ibfk_1` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `dataset_x_objectxpeople_ibfk_2` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `dataset_x_objectxpeople_ibfk_3` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `dataset_x_objectxpeople_ibfk_4` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `dataset_x_objectxpeople_ibfk_5` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `dataset_x_objectxpeople_ibfk_6` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dataset_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdate_Datetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `dataset_x_objectxpeople_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `dataset_x_objectxpeople` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `df_cr` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `facet_id` int(11) NOT NULL,
  `status_id` int(11) DEFAULT NULL,
  `lifecycle_table` varchar(255) DEFAULT NULL,
  `cr_type_id` int(11) DEFAULT NULL,
  `cr_urgency_id` int(11) DEFAULT NULL,
  `cr_severity_id` int(11) DEFAULT NULL,
  `process_definition_id` int(11) DEFAULT NULL,
  `can_create` tinyint(1) NOT NULL DEFAULT 0 CHECK (`can_create` in (0,1)),
  `can_read` tinyint(1) NOT NULL DEFAULT 0 CHECK (`can_read` in (0,1)),
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `workflow_create_id` int(11) DEFAULT NULL,
  `workflow_edit_id` int(11) DEFAULT NULL,
  `cr_system` enum('Native','ServiceNow','JIRA') NOT NULL DEFAULT 'Native',
  `workflow_approval_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `workflow_for_types_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `admin_workflow_bypass` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_df_cr_facet` (`facet_id`),
  KEY `fk_dfcr_status` (`status_id`),
  KEY `fk_dfcr_type` (`cr_type_id`),
  KEY `fk_dfcr_urgency` (`cr_urgency_id`),
  KEY `fk_dfcr_severity` (`cr_severity_id`),
  KEY `fk_dfcr_process_definition` (`process_definition_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `df_cr_type_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `facet_id` int(11) NOT NULL,
  `type_id` int(11) NOT NULL,
  `type_name` varchar(255) DEFAULT NULL,
  `workflow_create_id` int(11) DEFAULT NULL,
  `workflow_edit_id` int(11) DEFAULT NULL,
  `cr_type_id` int(11) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_dfcr_type_facet_type` (`facet_id`,`type_id`),
  KEY `fk_dfcr_type_cr_type` (`cr_type_id`),
  KEY `fk_dfcr_type_wf_create` (`workflow_create_id`),
  KEY `fk_dfcr_type_wf_edit` (`workflow_edit_id`),
  KEY `idx_dfcr_type_facet` (`facet_id`),
  CONSTRAINT `fk_dfcr_type_cr_type` FOREIGN KEY (`cr_type_id`) REFERENCES `changerequest_type` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_dfcr_type_module` FOREIGN KEY (`facet_id`) REFERENCES `module` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_dfcr_type_wf_create` FOREIGN KEY (`workflow_create_id`) REFERENCES `process_definition` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_dfcr_type_wf_edit` FOREIGN KEY (`workflow_edit_id`) REFERENCES `process_definition` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `dropdown_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `module_id` int(11) DEFAULT NULL COMMENT 'Foreign key to module.id',
  `dropdown_display_name` varchar(255) NOT NULL COMMENT 'Human-readable dropdown name',
  `table_name` varchar(255) NOT NULL COMMENT 'Actual database table name',
  `column_id` varchar(50) DEFAULT 'id' COMMENT 'Primary key column name (usually id or ID)',
  `column_name` varchar(50) DEFAULT 'name' COMMENT 'Display value column name (usually name or Name)',
  `column_description` varchar(50) DEFAULT 'description' COMMENT 'Description column name if exists',
  `api_endpoint` varchar(255) DEFAULT NULL COMMENT 'API endpoint to fetch values',
  `is_relation_type` tinyint(1) DEFAULT 0 COMMENT 'True if this is a relationship type dropdown (warning on delete)',
  `is_protected` tinyint(1) DEFAULT 0 COMMENT 'True if cannot delete values in use',
  `allow_add` tinyint(1) DEFAULT 1 COMMENT 'Allow adding new values',
  `allow_edit` tinyint(1) DEFAULT 1 COMMENT 'Allow editing values',
  `allow_delete` tinyint(1) DEFAULT 1 COMMENT 'Allow deleting values',
  `sort_order` int(11) DEFAULT 0 COMMENT 'Display order within facet',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_module_dropdown` (`module_id`,`dropdown_display_name`),
  KEY `idx_module_id` (`module_id`),
  KEY `idx_table_name` (`table_name`),
  CONSTRAINT `fk_dropdown_config_module` FOREIGN KEY (`module_id`) REFERENCES `module` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=133 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Metadata for dropdown configurations';



CREATE TABLE `email_queue` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `notification_id` bigint(20) DEFAULT NULL COMMENT 'FK to workflow_notification',
  `recipient_user_id` int(11) NOT NULL COMMENT 'Target user ID',
  `subject` varchar(255) NOT NULL COMMENT 'Email subject',
  `body` text NOT NULL COMMENT 'Email body (HTML)',
  `status` enum('PENDING','SENT','FAILED') DEFAULT 'PENDING',
  `scheduled_at` datetime NOT NULL COMMENT 'When to send this email (based on user frequency)',
  `sent_at` datetime DEFAULT NULL COMMENT 'When email was actually sent',
  `error_message` text DEFAULT NULL COMMENT 'Error message if sending failed',
  `retry_count` int(11) DEFAULT 0 COMMENT 'Number of retry attempts',
  `created_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_recipient_status` (`recipient_user_id`,`status`,`scheduled_at`),
  KEY `idx_scheduled` (`scheduled_at`,`status`),
  KEY `idx_notification` (`notification_id`),
  CONSTRAINT `fk_email_queue_notification` FOREIGN KEY (`notification_id`) REFERENCES `workflow_notification` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `email_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `smtp_host` varchar(255) NOT NULL COMMENT 'SMTP server hostname',
  `smtp_port` int(11) NOT NULL COMMENT 'SMTP server port (e.g., 587 for TLS, 465 for SSL)',
  `smtp_username` varchar(255) NOT NULL COMMENT 'SMTP authentication username',
  `smtp_password` varchar(255) NOT NULL COMMENT 'SMTP authentication password (should be encrypted)',
  `encryption` enum('SSL','TLS') DEFAULT 'TLS' COMMENT 'Encryption method',
  `enabled` tinyint(1) DEFAULT 0 COMMENT 'Global email enable/disable flag',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `employment_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primary_Name` varchar(45) DEFAULT NULL,
  `last_updated_date` datetime DEFAULT NULL,
  `last_update_user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `last_update_user_id` (`last_update_user_id`),
  CONSTRAINT `employment_type_ibfk_1` FOREIGN KEY (`last_update_user_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `employment_type_ibfk_2` FOREIGN KEY (`last_update_user_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `entry` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Value` mediumtext DEFAULT NULL,
  `Entry_Ref_ID` int(11) DEFAULT NULL,
  `Field_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Entry_Ref_ID` (`Entry_Ref_ID`),
  KEY `Field_ID` (`Field_ID`),
  CONSTRAINT `fk_entry_field` FOREIGN KEY (`Field_ID`) REFERENCES `field` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_entry_ref` FOREIGN KEY (`Entry_Ref_ID`) REFERENCES `entry_ref` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `entry_ref` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `field` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Attribute_Name` mediumtext DEFAULT NULL,
  `Attribute_Type` varchar(255) DEFAULT NULL,
  `Data_StoreID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Data_StoreID` (`Data_StoreID`),
  CONSTRAINT `fk_field_datastore` FOREIGN KEY (`Data_StoreID`) REFERENCES `data_store` (`Dataset_ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `files` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `path` varchar(255) DEFAULT NULL,
  `mimetype` varchar(255) DEFAULT NULL,
  `size` int(11) DEFAULT NULL,
  `createdatetime` datetime DEFAULT NULL,
  `lastupdatedatetime` datetime DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  CONSTRAINT `files_ibfk_1` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `files_ibfk_2` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `follow` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `ip_id` int(11) DEFAULT NULL,
  `Follow_type` int(11) DEFAULT NULL,
  `With_Children` tinyint(1) NOT NULL DEFAULT 0 COMMENT '0=No, 1=Yes',
  `Description` varchar(256) DEFAULT NULL,
  `Created_datetime` datetime DEFAULT NULL,
  `last_updated_datetime` datetime DEFAULT NULL,
  `Module_id` int(11) DEFAULT NULL,
  `objectID` int(11) DEFAULT NULL,
  `last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `ip_id` (`ip_id`),
  KEY `Follow_type` (`Follow_type`),
  KEY `last_updated_userID` (`last_updated_userID`),
  CONSTRAINT `follow_ibfk_1` FOREIGN KEY (`ip_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `follow_ibfk_2` FOREIGN KEY (`Follow_type`) REFERENCES `follow_type` (`id`),
  CONSTRAINT `follow_ibfk_3` FOREIGN KEY (`last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `follow_ibfk_4` FOREIGN KEY (`ip_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `follow_ibfk_5` FOREIGN KEY (`Follow_type`) REFERENCES `follow_type` (`id`),
  CONSTRAINT `follow_ibfk_6` FOREIGN KEY (`last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `chk_follow_with_children` CHECK (`With_Children` in (0,1))
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `follow_audit` (
  `ID` int(10) unsigned NOT NULL,
  `rev_id` int(11) NOT NULL AUTO_INCREMENT,
  `ip_id` int(11) DEFAULT NULL,
  `Follow_type` int(11) DEFAULT NULL,
  `With_Children` int(11) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Created_datetetime` datetime DEFAULT NULL,
  `last_updated_datetime` datetime DEFAULT NULL,
  `Module_id` int(11) DEFAULT NULL,
  `objectID` int(11) DEFAULT NULL,
  `last_updated_userID` int(11) DEFAULT NULL,
  `rev_type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`rev_id`),
  UNIQUE KEY `rev_id` (`rev_id`),
  CONSTRAINT `follow_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `follow_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(45) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `frequency` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primary_Name` varchar(45) DEFAULT NULL,
  `last_updated_date` datetime DEFAULT NULL,
  `last_update_user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `last_update_user_id` (`last_update_user_id`),
  CONSTRAINT `frequency_ibfk_1` FOREIGN KEY (`last_update_user_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `frequency_ibfk_2` FOREIGN KEY (`last_update_user_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `geography` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `ParentID` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Geo_Parent` (`ParentID`),
  KEY `fk_Geo_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_Geo_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Geo_Parent` FOREIGN KEY (`ParentID`) REFERENCES `geography` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `geography_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `ParentID` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_Geo_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `geography` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `geography_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `geography_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `geography` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=213 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Format_type` int(11) DEFAULT NULL,
  `KDE` int(11) DEFAULT NULL,
  `Security_Classification` int(11) DEFAULT NULL,
  `Type` int(11) DEFAULT NULL,
  `Confidentiality_Rating` int(11) DEFAULT NULL,
  `Integrity_Rating` int(11) DEFAULT NULL,
  `Availability_Rating` int(11) DEFAULT NULL,
  `Name` varchar(1024) DEFAULT NULL,
  `Description` varchar(1024) DEFAULT NULL,
  `Ref_Number` varchar(45) DEFAULT NULL,
  `Examples` varchar(256) DEFAULT NULL,
  `Business_Logic` varchar(256) DEFAULT NULL,
  `Format` varchar(256) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_Updated_Datetime` datetime DEFAULT NULL,
  `Deleted_datetime` datetime DEFAULT NULL,
  `LDM` varchar(256) DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `under_revision` tinyint(1) NOT NULL DEFAULT 0,
  `active_cr_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Parent_ID` (`Parent_ID`),
  KEY `Is_Public` (`Is_Public`),
  KEY `Status` (`Status`),
  KEY `Lifecycle` (`Lifecycle`),
  KEY `Format_type` (`Format_type`),
  KEY `KDE` (`KDE`),
  KEY `Security_Classification` (`Security_Classification`),
  KEY `Type` (`Type`),
  KEY `Confidentiality_Rating` (`Confidentiality_Rating`),
  KEY `Integrity_Rating` (`Integrity_Rating`),
  KEY `Availability_Rating` (`Availability_Rating`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  KEY `CreatedBy_ID` (`CreatedBy_ID`),
  KEY `idx_glossary_revision` (`under_revision`,`active_cr_id`),
  CONSTRAINT `glossary_ibfk_1` FOREIGN KEY (`Parent_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_ibfk_10` FOREIGN KEY (`Integrity_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `glossary_ibfk_11` FOREIGN KEY (`Availability_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `glossary_ibfk_12` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_ibfk_13` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_ibfk_14` FOREIGN KEY (`Parent_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_ibfk_15` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `glossary_ibfk_16` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`),
  CONSTRAINT `glossary_ibfk_17` FOREIGN KEY (`Lifecycle`) REFERENCES `glossary_lifecycle` (`ID`),
  CONSTRAINT `glossary_ibfk_18` FOREIGN KEY (`Format_type`) REFERENCES `glossary_format_type` (`ID`),
  CONSTRAINT `glossary_ibfk_19` FOREIGN KEY (`KDE`) REFERENCES `glossary_kde_type` (`ID`),
  CONSTRAINT `glossary_ibfk_2` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `glossary_ibfk_20` FOREIGN KEY (`Security_Classification`) REFERENCES `security_classification` (`ID`),
  CONSTRAINT `glossary_ibfk_21` FOREIGN KEY (`Type`) REFERENCES `glossary_type` (`ID`),
  CONSTRAINT `glossary_ibfk_22` FOREIGN KEY (`Confidentiality_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `glossary_ibfk_23` FOREIGN KEY (`Integrity_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `glossary_ibfk_24` FOREIGN KEY (`Availability_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `glossary_ibfk_25` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_ibfk_26` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_ibfk_3` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`),
  CONSTRAINT `glossary_ibfk_4` FOREIGN KEY (`Lifecycle`) REFERENCES `glossary_lifecycle` (`ID`),
  CONSTRAINT `glossary_ibfk_5` FOREIGN KEY (`Format_type`) REFERENCES `glossary_format_type` (`ID`),
  CONSTRAINT `glossary_ibfk_6` FOREIGN KEY (`KDE`) REFERENCES `glossary_kde_type` (`ID`),
  CONSTRAINT `glossary_ibfk_7` FOREIGN KEY (`Security_Classification`) REFERENCES `security_classification` (`ID`),
  CONSTRAINT `glossary_ibfk_8` FOREIGN KEY (`Type`) REFERENCES `glossary_type` (`ID`),
  CONSTRAINT `glossary_ibfk_9` FOREIGN KEY (`Confidentiality_Rating`) REFERENCES `cia_rating` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_alias_names` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Glossary_id` int(11) DEFAULT NULL,
  `Name` varchar(512) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Glossary_id` (`Glossary_id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `glossary_alias_names_ibfk_1` FOREIGN KEY (`Glossary_id`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_alias_names_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_alias_names_ibfk_3` FOREIGN KEY (`Glossary_id`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_alias_names_ibfk_4` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=94 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_alias_names_audit` (
  `ID` int(11) NOT NULL,
  `Rev_id` int(11) DEFAULT NULL,
  `Glossary_id` int(11) DEFAULT NULL,
  `Name` varchar(512) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  `Rev_Type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev_id` (`Rev_id`),
  CONSTRAINT `glossary_alias_names_audit_ibfk_1` FOREIGN KEY (`Rev_id`) REFERENCES `revisions` (`id`),
  CONSTRAINT `glossary_alias_names_audit_ibfk_2` FOREIGN KEY (`Rev_id`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev_ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Format_type` int(11) DEFAULT NULL,
  `KDE` int(11) DEFAULT NULL,
  `Security_Classification` int(11) DEFAULT NULL,
  `Type` int(11) DEFAULT NULL,
  `Confidentiality_Rating` int(11) DEFAULT NULL,
  `Integrity_Rating` int(11) DEFAULT NULL,
  `Availability_Rating` int(11) DEFAULT NULL,
  `Name` varchar(1024) DEFAULT NULL,
  `Description` varchar(1024) DEFAULT NULL,
  `Ref_Number` varchar(45) DEFAULT NULL,
  `Examples` varchar(256) DEFAULT NULL,
  `Business_Logic` varchar(256) DEFAULT NULL,
  `Format` varchar(256) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_Updated_Datetime` datetime DEFAULT NULL,
  `Deleted_datetime` datetime DEFAULT NULL,
  `LDM` varchar(256) DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `Rev_Type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev_ID`),
  UNIQUE KEY `Rev_ID` (`Rev_ID`),
  CONSTRAINT `glossary_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `glossary` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `glossary_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `glossary` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=234 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_changes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `change_request_id` int(11) NOT NULL,
  `object_id` int(11) NOT NULL,
  `nobject_id` int(11) NOT NULL,
  `area_key` varchar(100) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_glossary_changes_cr_obj_area` (`change_request_id`,`object_id`,`area_key`),
  KEY `idx_glossary_changes_obj` (`object_id`),
  KEY `idx_glossary_changes_cr` (`change_request_id`)
) ENGINE=InnoDB AUTO_INCREMENT=82 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_follow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Glossary_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Glossary_id` (`Glossary_id`),
  KEY `Follow_id` (`follow_id`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  KEY `idx_glossary_follow_follow_id` (`follow_id`),
  CONSTRAINT `glossary_follow_ibfk_1` FOREIGN KEY (`Glossary_id`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_follow_ibfk_2` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `glossary_follow_ibfk_3` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_follow_ibfk_4` FOREIGN KEY (`Glossary_id`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_follow_ibfk_6` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_format_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  CONSTRAINT `glossary_format_type_ibfk_1` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_format_type_ibfk_2` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_kde_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  CONSTRAINT `glossary_kde_type_ibfk_1` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_kde_type_ibfk_2` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_lifecycle` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  CONSTRAINT `glossary_lifecycle_ibfk_1` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_lifecycle_ibfk_2` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  CONSTRAINT `glossary_type_ibfk_1` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_type_ibfk_2` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_glossary` (
  `ID` int(11) NOT NULL,
  `SourceGlossaryID` int(11) DEFAULT NULL,
  `TargetGlossaryID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `SourceGlossaryID` (`SourceGlossaryID`),
  KEY `TargetGlossaryID` (`TargetGlossaryID`),
  KEY `RelationType` (`RelationType`),
  KEY `LastUpdateUser_ID` (`LastUpdateUser_ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_1` FOREIGN KEY (`SourceGlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_2` FOREIGN KEY (`TargetGlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_3` FOREIGN KEY (`RelationType`) REFERENCES `glossary_x_glossary_reltype` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_4` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_5` FOREIGN KEY (`SourceGlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_6` FOREIGN KEY (`TargetGlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_7` FOREIGN KEY (`RelationType`) REFERENCES `glossary_x_glossary_reltype` (`ID`),
  CONSTRAINT `glossary_x_glossary_ibfk_8` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) DEFAULT NULL,
  `SourceGlossaryID` int(11) DEFAULT NULL,
  `TargetGlossaryID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `glossary_x_glossary_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `glossary_x_glossary_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_glossary_reltype` (
  `ID` int(11) NOT NULL,
  `LastUpdatetime` datetime DEFAULT NULL,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `LastUpdateUser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUpdateUser_id` (`LastUpdateUser_id`),
  CONSTRAINT `glossary_x_glossary_reltype_ibfk_1` FOREIGN KEY (`LastUpdateUser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_x_glossary_reltype_ibfk_2` FOREIGN KEY (`LastUpdateUser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `GlossaryID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Object_x_ipid` (`Object_x_ipid`),
  KEY `GlossaryID` (`GlossaryID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `glossary_x_objectxpeople_ibfk_1` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `glossary_x_objectxpeople_ibfk_2` FOREIGN KEY (`GlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_objectxpeople_ibfk_3` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `glossary_x_objectxpeople_ibfk_4` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `glossary_x_objectxpeople_ibfk_5` FOREIGN KEY (`GlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_objectxpeople_ibfk_6` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) DEFAULT NULL,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `GlossaryID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `glossary_x_objectxpeople_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `glossary_x_objectxpeople_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_process` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Glossary_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_GXP_Glossary` (`Glossary_ID`),
  KEY `fk_GXP_Process` (`Process_ID`),
  KEY `fk_GXP_RelType` (`RelationType`),
  KEY `fk_GXP_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_GXP_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_GXP_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_GXP_Process` FOREIGN KEY (`Process_ID`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_GXP_RelType` FOREIGN KEY (`RelationType`) REFERENCES `glossary_x_process_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_process_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_GXPAudit_Rev` (`Rev`),
  KEY `fk_GXPAudit_Glossary` (`Glossary_ID`),
  KEY `fk_GXPAudit_Process` (`Process_ID`),
  KEY `fk_GXPAudit_RelType` (`RelationType`),
  KEY `fk_GXPAudit_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_GXPAudit_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_GXPAudit_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_GXPAudit_Process` FOREIGN KEY (`Process_ID`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_GXPAudit_RelType` FOREIGN KEY (`RelationType`) REFERENCES `glossary_x_process_relationtype` (`ID`),
  CONSTRAINT `fk_GXPAudit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_process_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_GXPRelType_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_GXPRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_project` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Project_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_GXPj_Project` (`Project_ID`),
  KEY `fk_GXPj_Glossary` (`Glossary_ID`),
  KEY `fk_GXPj_RelType` (`RelationType`),
  KEY `fk_GXPj_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_GXPj_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_GXPj_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_GXPj_Project` FOREIGN KEY (`Project_ID`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_GXPj_RelType` FOREIGN KEY (`RelationType`) REFERENCES `glossary_x_project_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_project_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Project_ID` int(11) DEFAULT NULL,
  `Glossary_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_GXPjAudit_Rev` (`Rev`),
  KEY `fk_GXPjAudit_Project` (`Project_ID`),
  KEY `fk_GXPjAudit_Glossary` (`Glossary_ID`),
  KEY `fk_GXPjAudit_RelType` (`RelationType`),
  KEY `fk_GXPjAudit_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_GXPjAudit_Glossary` FOREIGN KEY (`Glossary_ID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_GXPjAudit_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_GXPjAudit_Project` FOREIGN KEY (`Project_ID`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_GXPjAudit_RelType` FOREIGN KEY (`RelationType`) REFERENCES `glossary_x_project_relationtype` (`ID`),
  CONSTRAINT `fk_GXPjAudit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_project_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_GXPjRelType_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_GXPjRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_system` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `GlossaryID` int(11) DEFAULT NULL,
  `SystemID` int(11) DEFAULT NULL,
  `Strategic_DatasetID` int(11) DEFAULT NULL,
  `Relation_TypeID` int(11) DEFAULT NULL,
  `LastUpdate_Datetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `GlossaryID` (`GlossaryID`),
  KEY `SystemID` (`SystemID`),
  KEY `Strategic_DatasetID` (`Strategic_DatasetID`),
  KEY `Relation_TypeID` (`Relation_TypeID`),
  KEY `LastUpdate_UserID` (`LastUpdate_UserID`),
  CONSTRAINT `glossary_x_system_ibfk_1` FOREIGN KEY (`GlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `glossary_x_system_ibfk_2` FOREIGN KEY (`SystemID`) REFERENCES `system` (`id`),
  CONSTRAINT `glossary_x_system_ibfk_3` FOREIGN KEY (`Strategic_DatasetID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `glossary_x_system_ibfk_4` FOREIGN KEY (`Relation_TypeID`) REFERENCES `glossary_x_system_relationtype` (`ID`),
  CONSTRAINT `glossary_x_system_ibfk_5` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_system_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) DEFAULT NULL,
  `GlossaryID` int(11) DEFAULT NULL,
  `SystemID` int(11) DEFAULT NULL,
  `Strategic_DatasetID` int(11) DEFAULT NULL,
  `Relation_TypeID` int(11) DEFAULT NULL,
  `LastUpdate_Datetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `Rev_Type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `glossary_x_system_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_system_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(225) DEFAULT NULL,
  `Description` varchar(300) DEFAULT NULL,
  `TypeID` int(11) DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `Last_Update_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `TypeID` (`TypeID`),
  KEY `Last_Update_UserID` (`Last_Update_UserID`),
  CONSTRAINT `glossary_x_system_relationtype_ibfk_1` FOREIGN KEY (`TypeID`) REFERENCES `glossary_x_system_relationtype_type` (`ID`),
  CONSTRAINT `glossary_x_system_relationtype_ibfk_2` FOREIGN KEY (`Last_Update_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `glossary_x_system_relationtype_type` (
  `ID` int(11) NOT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `create_date_time` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdateuser_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_history_lastupdateuser` (`lastupdateuser_id`),
  KEY `idx_history_created` (`create_date_time`)
) ENGINE=InnoDB AUTO_INCREMENT=8998 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE `history_visited_entity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `entity` varchar(64) NOT NULL,
  `entity_id` varchar(64) NOT NULL,
  `route` varchar(128) NOT NULL,
  `route_params` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`route_params`)),
  PRIMARY KEY (`id`),
  KEY `idx_hve_entity` (`entity`,`entity_id`),
  KEY `idx_hve_route` (`route`),
  CONSTRAINT `fk_hve_history` FOREIGN KEY (`id`) REFERENCES `history` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8998 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE `i_user` (
  `reference` int(11) NOT NULL,
  `active` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`reference`),
  UNIQUE KEY `reference` (`reference`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(255) DEFAULT NULL,
  `Ref_number` varchar(255) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Transfer_Method_ID` int(11) DEFAULT NULL,
  `Transfer_Format_ID` int(11) DEFAULT NULL,
  `Classification_id` int(11) DEFAULT NULL,
  `Lifecycle_id` int(11) DEFAULT NULL,
  `status_id` int(11) DEFAULT NULL,
  `Source_systemID` int(11) DEFAULT NULL,
  `Target_systemID` int(11) DEFAULT NULL,
  `Automation_ID` int(11) DEFAULT NULL,
  `Frequency_ID` int(11) DEFAULT NULL,
  `is_public` int(11) DEFAULT NULL,
  `Asset_ID` varchar(45) DEFAULT NULL,
  `Synchronisation_Control` varchar(256) DEFAULT NULL,
  `created_datetime` datetime DEFAULT NULL,
  `last_updatedtime` datetime DEFAULT NULL,
  `deleted_datetime` datetime DEFAULT NULL,
  `createdBy_ID` int(11) DEFAULT NULL,
  `last_updateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Transfer_Method_ID` (`Transfer_Method_ID`),
  KEY `Transfer_Format_ID` (`Transfer_Format_ID`),
  KEY `status_id` (`status_id`),
  KEY `Automation_ID` (`Automation_ID`),
  KEY `Lifecycle_id` (`Lifecycle_id`),
  KEY `Frequency_ID` (`Frequency_ID`),
  KEY `is_public` (`is_public`),
  KEY `createdBy_ID` (`createdBy_ID`),
  KEY `last_updateuser_id` (`last_updateuser_id`),
  KEY `Source_systemID` (`Source_systemID`),
  KEY `Target_systemID` (`Target_systemID`),
  KEY `Classification_id` (`Classification_id`),
  CONSTRAINT `interface_ibfk_1` FOREIGN KEY (`Transfer_Method_ID`) REFERENCES `interface_transfer` (`id`),
  CONSTRAINT `interface_ibfk_10` FOREIGN KEY (`Source_systemID`) REFERENCES `system` (`id`),
  CONSTRAINT `interface_ibfk_11` FOREIGN KEY (`Target_systemID`) REFERENCES `system` (`id`),
  CONSTRAINT `interface_ibfk_12` FOREIGN KEY (`Classification_id`) REFERENCES `interface_classification` (`id`),
  CONSTRAINT `interface_ibfk_13` FOREIGN KEY (`Transfer_Method_ID`) REFERENCES `interface_transfer` (`id`),
  CONSTRAINT `interface_ibfk_14` FOREIGN KEY (`Transfer_Format_ID`) REFERENCES `interface_transfer_format` (`id`),
  CONSTRAINT `interface_ibfk_15` FOREIGN KEY (`status_id`) REFERENCES `status` (`ID`),
  CONSTRAINT `interface_ibfk_16` FOREIGN KEY (`Automation_ID`) REFERENCES `interface_automation` (`id`),
  CONSTRAINT `interface_ibfk_17` FOREIGN KEY (`Lifecycle_id`) REFERENCES `interface_lifecycle` (`id`),
  CONSTRAINT `interface_ibfk_18` FOREIGN KEY (`Frequency_ID`) REFERENCES `interface_frequency` (`id`),
  CONSTRAINT `interface_ibfk_19` FOREIGN KEY (`is_public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `interface_ibfk_2` FOREIGN KEY (`Transfer_Format_ID`) REFERENCES `interface_transfer_format` (`id`),
  CONSTRAINT `interface_ibfk_20` FOREIGN KEY (`createdBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_ibfk_21` FOREIGN KEY (`last_updateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_ibfk_22` FOREIGN KEY (`Source_systemID`) REFERENCES `system` (`id`),
  CONSTRAINT `interface_ibfk_23` FOREIGN KEY (`Target_systemID`) REFERENCES `system` (`id`),
  CONSTRAINT `interface_ibfk_24` FOREIGN KEY (`Classification_id`) REFERENCES `interface_classification` (`id`),
  CONSTRAINT `interface_ibfk_3` FOREIGN KEY (`status_id`) REFERENCES `status` (`ID`),
  CONSTRAINT `interface_ibfk_4` FOREIGN KEY (`Automation_ID`) REFERENCES `interface_automation` (`id`),
  CONSTRAINT `interface_ibfk_5` FOREIGN KEY (`Lifecycle_id`) REFERENCES `interface_lifecycle` (`id`),
  CONSTRAINT `interface_ibfk_6` FOREIGN KEY (`Frequency_ID`) REFERENCES `interface_frequency` (`id`),
  CONSTRAINT `interface_ibfk_7` FOREIGN KEY (`is_public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `interface_ibfk_8` FOREIGN KEY (`createdBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_ibfk_9` FOREIGN KEY (`last_updateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=38 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_audit` (
  `id` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(255) DEFAULT NULL,
  `Ref_number` varchar(255) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Transfer_Method_ID` int(11) DEFAULT NULL,
  `Transfer_Format_ID` int(11) DEFAULT NULL,
  `Classification_id` int(11) DEFAULT NULL,
  `Lifecycle_id` int(11) DEFAULT NULL,
  `status_id` int(11) DEFAULT NULL,
  `Source_systemID` int(11) DEFAULT NULL,
  `Target_systemID` int(11) DEFAULT NULL,
  `Automation_ID` int(11) DEFAULT NULL,
  `Frequency_ID` int(11) DEFAULT NULL,
  `is_public` int(11) DEFAULT NULL,
  `Asset_ID` varchar(45) DEFAULT NULL,
  `Synchronisation_Control` varchar(256) DEFAULT NULL,
  `created_datetime` datetime DEFAULT NULL,
  `last_updatedtime` datetime DEFAULT NULL,
  `deleted_datetime` datetime DEFAULT NULL,
  `createdBy_ID` int(11) DEFAULT NULL,
  `last_updateuser_id` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_interface_audit_rev` FOREIGN KEY (`id`) REFERENCES `interface` (`id`) ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `interface_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `interface` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_automation` (
  `id` int(11) NOT NULL,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_classification` (
  `id` int(11) NOT NULL,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `interface_classification_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_classification_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_follow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Interface_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `Created_datetime` datetime DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Interface_id` (`Interface_id`),
  KEY `Follow_id` (`follow_id`),
  KEY `Last_updatedUser_ID` (`Last_updatedUser_ID`),
  KEY `idx_interface_follow_follow_id` (`follow_id`),
  CONSTRAINT `interface_follow_ibfk_1` FOREIGN KEY (`Interface_id`) REFERENCES `interface` (`id`),
  CONSTRAINT `interface_follow_ibfk_2` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `interface_follow_ibfk_3` FOREIGN KEY (`Last_updatedUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_follow_ibfk_4` FOREIGN KEY (`Interface_id`) REFERENCES `interface` (`id`),
  CONSTRAINT `interface_follow_ibfk_6` FOREIGN KEY (`Last_updatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_frequency` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `interface_frequency_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_frequency_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_lifecycle` (
  `id` int(11) NOT NULL,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `interface_lifecycle_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_lifecycle_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_transfer` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `interface_transfer_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_transfer_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_transfer_format` (
  `id` int(11) NOT NULL,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_Datetime` datetime DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `interface_transfer_format_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_transfer_format_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_x_glossary` (
  `ID` int(11) NOT NULL,
  `Glossary_RelationType` int(11) DEFAULT NULL,
  `Glossary` int(11) DEFAULT NULL,
  `Interface` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdate_datetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Glossary` (`Glossary`),
  KEY `Interface` (`Interface`),
  KEY `Glossary_RelationType` (`Glossary_RelationType`),
  KEY `LastUpdate_UserID` (`LastUpdate_UserID`),
  CONSTRAINT `interface_x_glossary_ibfk_1` FOREIGN KEY (`Glossary`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `interface_x_glossary_ibfk_2` FOREIGN KEY (`Interface`) REFERENCES `interface` (`id`),
  CONSTRAINT `interface_x_glossary_ibfk_3` FOREIGN KEY (`Glossary_RelationType`) REFERENCES `interface_x_glossary_relationtype` (`ID`),
  CONSTRAINT `interface_x_glossary_ibfk_4` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_x_glossary_ibfk_5` FOREIGN KEY (`Glossary`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `interface_x_glossary_ibfk_6` FOREIGN KEY (`Interface`) REFERENCES `interface` (`id`),
  CONSTRAINT `interface_x_glossary_ibfk_7` FOREIGN KEY (`Glossary_RelationType`) REFERENCES `interface_x_glossary_relationtype` (`ID`),
  CONSTRAINT `interface_x_glossary_ibfk_8` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_x_glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) DEFAULT NULL,
  `Glossary_RelationType` int(11) DEFAULT NULL,
  `Glossary` int(11) DEFAULT NULL,
  `Interface` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdate_datetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `interface_x_glossary_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `interface_x_glossary_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_x_glossary_relationtype` (
  `ID` int(11) NOT NULL,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `InterfaceID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Object_x_ipid` (`Object_x_ipid`),
  KEY `InterfaceID` (`InterfaceID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `interface_x_objectxpeople_ibfk_1` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `interface_x_objectxpeople_ibfk_2` FOREIGN KEY (`InterfaceID`) REFERENCES `interface` (`id`),
  CONSTRAINT `interface_x_objectxpeople_ibfk_3` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `interface_x_objectxpeople_ibfk_4` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `interface_x_objectxpeople_ibfk_5` FOREIGN KEY (`InterfaceID`) REFERENCES `interface` (`id`),
  CONSTRAINT `interface_x_objectxpeople_ibfk_6` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `interface_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) DEFAULT NULL,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `InterfaceID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `interface_x_objectxpeople_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `interface_x_objectxpeople_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Created_date` datetime DEFAULT NULL,
  `Completed_date` datetime DEFAULT NULL,
  `Type` varchar(255) DEFAULT NULL,
  `Reference_Name` varchar(255) DEFAULT NULL,
  `Items_Count` int(11) DEFAULT NULL,
  `Status` varchar(255) DEFAULT NULL,
  `Created_By` int(11) DEFAULT NULL,
  `Child_Jobs_Order` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Created_By` (`Created_By`),
  CONSTRAINT `fk_job_createdby` FOREIGN KEY (`Created_By`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_configkeys` (
  `KeyID` int(11) NOT NULL AUTO_INCREMENT,
  `Option_Key` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`KeyID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_configuration` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Job_Entity_ID` int(11) DEFAULT NULL,
  `Option_KeyID` int(11) DEFAULT NULL,
  `Option_value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Job_Entity_ID` (`Job_Entity_ID`),
  KEY `Option_KeyID` (`Option_KeyID`),
  CONSTRAINT `fk_jobconf_job` FOREIGN KEY (`Job_Entity_ID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_jobconf_key` FOREIGN KEY (`Option_KeyID`) REFERENCES `job_configkeys` (`KeyID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_field_mapping` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Job_Entity_ID` int(11) DEFAULT NULL,
  `Source_field` varchar(255) DEFAULT NULL,
  `Target_field` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Job_Entity_ID` (`Job_Entity_ID`),
  CONSTRAINT `fk_jobfield_job` FOREIGN KEY (`Job_Entity_ID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_progress` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Job_ID` int(11) DEFAULT NULL,
  `Expected_ticks` int(11) DEFAULT NULL,
  `Status` varchar(255) DEFAULT NULL,
  `Message` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Job_ID` (`Job_ID`),
  CONSTRAINT `fk_jobprogress_job` FOREIGN KEY (`Job_ID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_report_item` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Job_ID` int(11) DEFAULT NULL,
  `Field_Name` varchar(255) DEFAULT NULL,
  `Status` varchar(255) DEFAULT NULL,
  `Position` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Job_ID` (`Job_ID`),
  CONSTRAINT `fk_jri_job` FOREIGN KEY (`Job_ID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_report_item_messages` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Report_ID` int(11) DEFAULT NULL,
  `Error_Code` varchar(255) DEFAULT NULL,
  `Message` mediumtext DEFAULT NULL,
  `Type` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Report_ID` (`Report_ID`),
  CONSTRAINT `fk_jrim_report` FOREIGN KEY (`Report_ID`) REFERENCES `job_report_item` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_resource_file` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `FileID` int(11) DEFAULT NULL,
  `File` longblob DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `FileID` (`FileID`),
  CONSTRAINT `fk_jrf_fileid` FOREIGN KEY (`FileID`) REFERENCES `job_resource_filename` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `job_resource_filename` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `JobID` int(11) DEFAULT NULL,
  `FileName` mediumtext DEFAULT NULL,
  `Original_FileName` mediumtext DEFAULT NULL,
  `Storage_Path` mediumtext DEFAULT NULL,
  `StoreFile` tinyint(1) DEFAULT 0,
  `Retention_Days` int(11) DEFAULT NULL,
  `Delete_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `JobID` (`JobID`),
  CONSTRAINT `fk_jrfn_job` FOREIGN KEY (`JobID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `LongName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Legal_Parent` (`Parent_ID`),
  KEY `fk_Legal_Status` (`Status`),
  KEY `fk_Legal_IsPublic` (`Is_Public`),
  KEY `fk_Legal_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_Legal_IsPublic` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_Legal_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Legal_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_Legal_Status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_advice_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `LongName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `legal_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `legal` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `legal_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `legal` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=125 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_x_follow` (
  `Id` int(11) NOT NULL AUTO_INCREMENT,
  `Legal_ID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`Id`),
  KEY `fk_LegalXFollow_Legal` (`Legal_ID`),
  KEY `fk_LegalXFollow_Follow` (`follow_id`),
  KEY `fk_LegalXFollow_LastUser` (`LastUpdate_UserID`),
  KEY `idx_legal_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_LegalXFollow_Follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_LegalXFollow_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_LegalXFollow_Legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_x_geo_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_LXGeoRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_LXGeoRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_x_geography` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Legal_ID` int(11) DEFAULT NULL,
  `Geography_ID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_LXGeo_Legal` (`Legal_ID`),
  KEY `fk_LXGeo_Geography` (`Geography_ID`),
  KEY `fk_LXGeo_RelType` (`Relation_Type`),
  KEY `fk_LXGeo_LastUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_LXGeo_Geography` FOREIGN KEY (`Geography_ID`) REFERENCES `geography` (`ID`),
  CONSTRAINT `fk_LXGeo_LastUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_LXGeo_Legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_LXGeo_RelType` FOREIGN KEY (`Relation_Type`) REFERENCES `legal_x_geo_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_x_geography_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Geography_ID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_LXGeo_Audit_Rev` (`Rev`),
  CONSTRAINT `fk_LXGeo_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_X_IP` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_LegalXObjPeople_Object` (`Object_X_IP`),
  KEY `fk_LegalXObjPeople_Legal` (`Legal_ID`),
  KEY `fk_LegalXObjPeople_User` (`LastUpdate_UserID`),
  CONSTRAINT `fk_LegalXObjPeople_Legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_LegalXObjPeople_Object` FOREIGN KEY (`Object_X_IP`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_LegalXObjPeople_User` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `legal_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Object_X_IP` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_LegalXObjPeople_Audit_Rev` (`Rev`),
  CONSTRAINT `fk_LegalXObjPeople_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `login_attempts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `client_identifier` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `attempt_time` timestamp NOT NULL DEFAULT current_timestamp(),
  `success` tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_client_time` (`client_identifier`,`attempt_time`),
  KEY `idx_attempt_time` (`attempt_time`),
  KEY `idx_client_identifier` (`client_identifier`)
) ENGINE=InnoDB AUTO_INCREMENT=240 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `map_layers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `map_id` bigint(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` varchar(50) NOT NULL,
  `url_template` varchar(1024) DEFAULT NULL,
  `visible` tinyint(1) DEFAULT 1,
  `order_index` int(11) DEFAULT 0,
  `style_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`style_json`)),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_layers_map_id` (`map_id`),
  KEY `idx_layers_type` (`type`),
  KEY `idx_layers_visible` (`visible`),
  KEY `idx_layers_order` (`order_index`),
  CONSTRAINT `map_layers_ibfk_1` FOREIGN KEY (`map_id`) REFERENCES `maps` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `map_markers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `layer_id` bigint(20) NOT NULL,
  `lat` decimal(10,7) NOT NULL,
  `lng` decimal(10,7) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `icon_url` varchar(1024) DEFAULT NULL,
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`metadata_json`)),
  `created_by` bigint(20) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_marker_coords` (`lat`,`lng`),
  KEY `idx_marker_layer_id` (`layer_id`),
  KEY `idx_marker_created_by` (`created_by`),
  CONSTRAINT `map_markers_ibfk_1` FOREIGN KEY (`layer_id`) REFERENCES `map_layers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `map_shapes` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `layer_id` bigint(20) NOT NULL,
  `type` varchar(50) NOT NULL,
  `coordinates_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL CHECK (json_valid(`coordinates_json`)),
  `style_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`style_json`)),
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`metadata_json`)),
  `created_by` bigint(20) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_shapes_layer_id` (`layer_id`),
  KEY `idx_shapes_type` (`type`),
  KEY `idx_shapes_created_by` (`created_by`),
  CONSTRAINT `map_shapes_ibfk_1` FOREIGN KEY (`layer_id`) REFERENCES `map_layers` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `maps` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `default_center_lat` decimal(10,7) DEFAULT NULL,
  `default_center_lng` decimal(10,7) DEFAULT NULL,
  `default_zoom` int(11) DEFAULT 10,
  `enabled` tinyint(1) DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `created_by` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_maps_enabled` (`enabled`),
  KEY `idx_maps_created_by` (`created_by`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `module` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `group_id` int(11) DEFAULT NULL,
  `primaryname` varchar(256) NOT NULL,
  `tablename` varchar(255) NOT NULL,
  `icon` varchar(256) DEFAULT NULL,
  `aclrole` tinyint(1) DEFAULT NULL,
  `cfenabled` tinyint(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `group_id` (`group_id`),
  CONSTRAINT `module_ibfk_1` FOREIGN KEY (`group_id`) REFERENCES `module_group` (`id`),
  CONSTRAINT `module_ibfk_2` FOREIGN KEY (`group_id`) REFERENCES `module_group` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `module_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) NOT NULL,
  `createdatetime` datetime DEFAULT NULL,
  `lastupdatedatetime` datetime DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  CONSTRAINT `module_group_ibfk_1` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `module_group_ibfk_2` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `myitems` (
  `ID` int(10) unsigned NOT NULL AUTO_INCREMENT,
  `Facet` varchar(125) DEFAULT NULL,
  `ObjectID` int(11) DEFAULT NULL,
  `Type` varchar(45) DEFAULT NULL,
  `Pinned` tinyint(1) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `VisitedBy_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_myitems_visitedby_idx` (`VisitedBy_ID`),
  CONSTRAINT `fk_myitems_visitedby` FOREIGN KEY (`VisitedBy_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=627 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `note_frequency` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Last_updateUserID` int(11) DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_notefreq_user_idx` (`Last_updateUserID`),
  CONSTRAINT `fk_NoteFreq_LastUpdateUser` FOREIGN KEY (`Last_updateUserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_NoteFrequ_LastUpdateUser` FOREIGN KEY (`Last_updateUserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_notefreq_user` FOREIGN KEY (`Last_updateUserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_lock` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Module_ID` int(11) DEFAULT NULL,
  `Object_ID` int(11) DEFAULT NULL,
  `Is_Permanent` tinyint(1) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Updated_Datetime` datetime DEFAULT NULL,
  `LockedBy_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Module_ID` (`Module_ID`),
  KEY `LockedBy_ID` (`LockedBy_ID`),
  CONSTRAINT `object_lock_ibfk_1` FOREIGN KEY (`Module_ID`) REFERENCES `module` (`id`),
  CONSTRAINT `object_lock_ibfk_2` FOREIGN KEY (`LockedBy_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_reference` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_ID` int(11) DEFAULT NULL,
  `Object_Type_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Object_Type_ID` (`Object_Type_ID`),
  CONSTRAINT `fk_objref_objtype` FOREIGN KEY (`Object_Type_ID`) REFERENCES `segment_object_type` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_role` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `module` int(11) DEFAULT NULL,
  `primaryname` varchar(45) NOT NULL,
  `description` text DEFAULT NULL,
  `defaultrole` tinyint(1) DEFAULT NULL,
  `objectroletype_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `objectroletype_id` (`objectroletype_id`),
  KEY `module` (`module`),
  CONSTRAINT `object_role_ibfk_1` FOREIGN KEY (`objectroletype_id`) REFERENCES `object_role_type` (`id`),
  CONSTRAINT `object_role_ibfk_2` FOREIGN KEY (`module`) REFERENCES `module` (`id`),
  CONSTRAINT `object_role_ibfk_3` FOREIGN KEY (`objectroletype_id`) REFERENCES `object_role_type` (`id`),
  CONSTRAINT `object_role_ibfk_4` FOREIGN KEY (`module`) REFERENCES `module` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=46 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_role_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) NOT NULL,
  `description` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_x_ip_status` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(45) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_x_people` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `isDelegateOF` int(11) DEFAULT NULL,
  `ipid` int(11) DEFAULT NULL,
  `roleID` int(11) DEFAULT NULL,
  `AcceptedID` int(11) DEFAULT NULL,
  `statusID` int(11) DEFAULT NULL,
  `createdatetime` datetime DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `isDelegateOF` (`isDelegateOF`),
  KEY `ipid` (`ipid`),
  KEY `roleID` (`roleID`),
  KEY `AcceptedID` (`AcceptedID`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  KEY `fk_objectxpeople_status` (`statusID`),
  CONSTRAINT `fk_objectxpeople_status` FOREIGN KEY (`statusID`) REFERENCES `object_x_ip_status` (`id`),
  CONSTRAINT `object_x_people_ibfk_1` FOREIGN KEY (`isDelegateOF`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `object_x_people_ibfk_10` FOREIGN KEY (`AcceptedID`) REFERENCES `roleaccepted` (`ID`),
  CONSTRAINT `object_x_people_ibfk_12` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `object_x_people_ibfk_2` FOREIGN KEY (`ipid`) REFERENCES `people` (`ID`),
  CONSTRAINT `object_x_people_ibfk_3` FOREIGN KEY (`roleID`) REFERENCES `object_role` (`id`),
  CONSTRAINT `object_x_people_ibfk_4` FOREIGN KEY (`AcceptedID`) REFERENCES `roleaccepted` (`ID`),
  CONSTRAINT `object_x_people_ibfk_6` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `object_x_people_ibfk_7` FOREIGN KEY (`isDelegateOF`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `object_x_people_ibfk_8` FOREIGN KEY (`ipid`) REFERENCES `people` (`ID`),
  CONSTRAINT `object_x_people_ibfk_9` FOREIGN KEY (`roleID`) REFERENCES `object_role` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=210 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `object_x_people_audit` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) NOT NULL,
  `isDelegateOF` int(11) DEFAULT NULL,
  `ipid` int(11) DEFAULT NULL,
  `roleID` int(11) DEFAULT NULL,
  `AcceptedID` int(11) DEFAULT NULL,
  `statusID` int(11) DEFAULT NULL,
  `createdatetime` datetime DEFAULT NULL,
  `lastupdatedatetime` datetime DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`Rev`),
  KEY `fk_oxp_audit_rev` (`Rev`),
  CONSTRAINT `fk_oxp_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `org_unit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Reference` varchar(10) DEFAULT NULL,
  `Name` varchar(20) DEFAULT NULL,
  `Description` varchar(30) DEFAULT NULL,
  `Parent_ID` int(11) DEFAULT NULL,
  `status_id` int(11) DEFAULT NULL,
  `Created_Date` datetime DEFAULT NULL,
  `last_updated_date` datetime DEFAULT NULL,
  `deleted_Date` datetime DEFAULT NULL,
  `lastupdateuser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `Reference` (`Reference`),
  KEY `Parent_ID` (`Parent_ID`),
  KEY `status_id` (`status_id`),
  KEY `lastupdateuser_ID` (`lastupdateuser_ID`),
  CONSTRAINT `org_unit_ibfk_1` FOREIGN KEY (`Parent_ID`) REFERENCES `org_unit` (`ID`),
  CONSTRAINT `org_unit_ibfk_2` FOREIGN KEY (`status_id`) REFERENCES `status` (`ID`),
  CONSTRAINT `org_unit_ibfk_3` FOREIGN KEY (`lastupdateuser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `org_unit_ibfk_4` FOREIGN KEY (`Parent_ID`) REFERENCES `org_unit` (`ID`),
  CONSTRAINT `org_unit_ibfk_5` FOREIGN KEY (`status_id`) REFERENCES `status` (`ID`),
  CONSTRAINT `org_unit_ibfk_6` FOREIGN KEY (`lastupdateuser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `org_unit_audit` (
  `id` int(11) NOT NULL,
  `rev_id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `refnumber` varchar(128) DEFAULT NULL,
  `createdatetime` datetime DEFAULT NULL,
  `lastupdatedatetime` datetime DEFAULT NULL,
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `rev_type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev_id`),
  UNIQUE KEY `rev_id` (`rev_id`),
  CONSTRAINT `fk_org_unit_audit_id` FOREIGN KEY (`id`) REFERENCES `org_unit` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `orgunit_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `orgunit_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `org_unit` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `pending_changes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `facet_type` varchar(50) NOT NULL,
  `object_id` int(11) NOT NULL,
  `change_request_id` int(11) NOT NULL,
  `tab_name` varchar(50) NOT NULL DEFAULT 'Summary',
  `component` varchar(100) DEFAULT NULL,
  `field_name` varchar(100) NOT NULL,
  `old_value` text DEFAULT NULL,
  `new_value` text DEFAULT NULL,
  `operation` enum('Created','Updated','Deleted') NOT NULL DEFAULT 'Updated',
  `user_id` int(11) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `applied_at` datetime DEFAULT NULL,
  `rejected_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pending_facet_object` (`facet_type`,`object_id`),
  KEY `idx_pending_cr` (`change_request_id`),
  KEY `idx_pending_status` (`applied_at`,`rejected_at`),
  KEY `fk_pending_user` (`user_id`),
  CONSTRAINT `fk_pending_cr` FOREIGN KEY (`change_request_id`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_pending_user` FOREIGN KEY (`user_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `First_Name` varchar(10) DEFAULT NULL,
  `Last_Name` varchar(10) DEFAULT NULL,
  `Description` varchar(50) DEFAULT NULL,
  `Function_Name` varchar(20) DEFAULT NULL,
  `Function_Description` varchar(50) DEFAULT NULL,
  `Org_Unit_ID` int(11) DEFAULT NULL,
  `Email` varchar(100) DEFAULT NULL,
  `Password` varchar(255) DEFAULT NULL,
  `Created_Date` datetime DEFAULT current_timestamp(),
  `Last_Updated` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `ip_details` int(11) DEFAULT NULL,
  `System_Role` int(11) DEFAULT NULL,
  `status_id` int(11) DEFAULT NULL,
  `source_id` int(11) DEFAULT NULL,
  `Deleted_date` datetime DEFAULT NULL,
  `last_User_LogIn` datetime DEFAULT NULL,
  `Locale` varchar(20) DEFAULT NULL,
  `profile_ImageID` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `is_locked` tinyint(1) DEFAULT 0 COMMENT 'Account lock status: 0=unlocked, 1=locked',
  `locked_date` datetime DEFAULT NULL COMMENT 'Timestamp when account was locked',
  `lock_reason` varchar(255) DEFAULT NULL COMMENT 'Reason for account lock',
  PRIMARY KEY (`ID`),
  KEY `Org_Unit_ID` (`Org_Unit_ID`),
  KEY `status_id` (`status_id`),
  KEY `source_id` (`source_id`),
  KEY `profile_ImageID` (`profile_ImageID`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  KEY `System_Role` (`System_Role`),
  KEY `people_ibfk_13` (`ip_details`),
  KEY `idx_is_locked` (`is_locked`,`locked_date`),
  CONSTRAINT `people_ibfk_1` FOREIGN KEY (`Org_Unit_ID`) REFERENCES `org_unit` (`ID`),
  CONSTRAINT `people_ibfk_10` FOREIGN KEY (`profile_ImageID`) REFERENCES `files` (`id`),
  CONSTRAINT `people_ibfk_11` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `people_ibfk_12` FOREIGN KEY (`System_Role`) REFERENCES `role` (`id`),
  CONSTRAINT `people_ibfk_13` FOREIGN KEY (`ip_details`) REFERENCES `people_details` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `people_ibfk_2` FOREIGN KEY (`status_id`) REFERENCES `status` (`ID`),
  CONSTRAINT `people_ibfk_3` FOREIGN KEY (`source_id`) REFERENCES `people_source` (`id`),
  CONSTRAINT `people_ibfk_4` FOREIGN KEY (`profile_ImageID`) REFERENCES `files` (`id`),
  CONSTRAINT `people_ibfk_5` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `people_ibfk_6` FOREIGN KEY (`System_Role`) REFERENCES `role` (`id`),
  CONSTRAINT `people_ibfk_7` FOREIGN KEY (`Org_Unit_ID`) REFERENCES `org_unit` (`ID`),
  CONSTRAINT `people_ibfk_8` FOREIGN KEY (`status_id`) REFERENCES `status` (`ID`),
  CONSTRAINT `people_ibfk_9` FOREIGN KEY (`source_id`) REFERENCES `people_source` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_audit` (
  `ID` int(11) NOT NULL,
  `rev` int(11) NOT NULL AUTO_INCREMENT,
  `First_Name` varchar(10) DEFAULT NULL,
  `Last_Name` varchar(10) DEFAULT NULL,
  `Description` varchar(50) DEFAULT NULL,
  `Function_Name` varchar(20) DEFAULT NULL,
  `Function_Description` varchar(50) DEFAULT NULL,
  `Org_Unit_ID` int(11) DEFAULT NULL,
  `Email` varchar(100) DEFAULT NULL,
  `Password` varchar(255) DEFAULT NULL,
  `Created_Date` datetime DEFAULT NULL,
  `Last_Updated` datetime DEFAULT NULL,
  `ip_details` int(11) DEFAULT NULL,
  `System_Role` int(11) DEFAULT NULL,
  `status_id` int(11) DEFAULT NULL,
  `source_id` int(11) DEFAULT NULL,
  `Deleted_date` datetime DEFAULT NULL,
  `last_User_LogIn` datetime DEFAULT NULL,
  `Locale` varchar(20) DEFAULT NULL,
  `profile_ImageID` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`,`rev`),
  UNIQUE KEY `rev` (`rev`),
  CONSTRAINT `people_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_details` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `employment_type` int(11) DEFAULT NULL,
  `lifecycle` int(11) DEFAULT NULL,
  `frequency` int(11) DEFAULT NULL,
  `employed_since` datetime DEFAULT NULL,
  `external_company_name` varchar(256) DEFAULT NULL,
  `office_location` varchar(256) DEFAULT NULL,
  `internal_mail_code` varchar(128) DEFAULT NULL,
  `other_info_description` varchar(256) DEFAULT NULL,
  `office_telephone` varchar(28) DEFAULT NULL,
  `mobile_telephone` varchar(28) DEFAULT NULL,
  `linkedIn_url` varchar(2048) DEFAULT NULL,
  `twitter_handle` varchar(100) DEFAULT NULL,
  `other_url` varchar(2048) DEFAULT NULL,
  `lan_id` varchar(1024) DEFAULT NULL,
  `created_datetime` datetime DEFAULT NULL,
  `last_updatedtime` datetime DEFAULT NULL,
  `deleted_datetime` datetime DEFAULT NULL,
  `last_updateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `employment_type` (`employment_type`),
  KEY `lifecycle` (`lifecycle`),
  KEY `frequency` (`frequency`),
  KEY `last_updateuser_id` (`last_updateuser_id`),
  CONSTRAINT `people_details_ibfk_1` FOREIGN KEY (`employment_type`) REFERENCES `employment_type` (`id`),
  CONSTRAINT `people_details_ibfk_2` FOREIGN KEY (`lifecycle`) REFERENCES `people_lifecycle_status` (`id`),
  CONSTRAINT `people_details_ibfk_3` FOREIGN KEY (`frequency`) REFERENCES `frequency` (`id`),
  CONSTRAINT `people_details_ibfk_4` FOREIGN KEY (`last_updateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `people_details_ibfk_5` FOREIGN KEY (`employment_type`) REFERENCES `employment_type` (`id`),
  CONSTRAINT `people_details_ibfk_6` FOREIGN KEY (`lifecycle`) REFERENCES `people_lifecycle_status` (`id`),
  CONSTRAINT `people_details_ibfk_7` FOREIGN KEY (`frequency`) REFERENCES `frequency` (`id`),
  CONSTRAINT `people_details_ibfk_8` FOREIGN KEY (`last_updateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_details_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL AUTO_INCREMENT,
  `employment_type` int(11) DEFAULT NULL,
  `lifecycle` int(11) DEFAULT NULL,
  `frequency` int(11) DEFAULT NULL,
  `employed_since` datetime DEFAULT NULL,
  `external_company_name` varchar(256) DEFAULT NULL,
  `office_location` varchar(256) DEFAULT NULL,
  `internal_mail_code` varchar(128) DEFAULT NULL,
  `other_info_description` varchar(256) DEFAULT NULL,
  `office_telephone` varchar(28) DEFAULT NULL,
  `mobile_telephone` varchar(28) DEFAULT NULL,
  `linkedIn_url` varchar(2048) DEFAULT NULL,
  `twitter_handle` varchar(100) DEFAULT NULL,
  `other_url` varchar(2048) DEFAULT NULL,
  `lan_id` varchar(1024) DEFAULT NULL,
  `created_datetime` datetime DEFAULT NULL,
  `last_updatedtime` datetime DEFAULT NULL,
  `deleted_datetime` datetime DEFAULT NULL,
  `last_updateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  UNIQUE KEY `rev` (`rev`),
  CONSTRAINT `people_details_audit_ibfk_1` FOREIGN KEY (`id`) REFERENCES `people_details` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_lifecycle_status` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primary_Name` varchar(256) DEFAULT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `last_updated_date` datetime DEFAULT NULL,
  `last_update_user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `last_update_user_id` (`last_update_user_id`),
  CONSTRAINT `people_lifecycle_status_ibfk_1` FOREIGN KEY (`last_update_user_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `people_lifecycle_status_ibfk_2` FOREIGN KEY (`last_update_user_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_source` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `source` varchar(20) NOT NULL,
  `external_identifier` varchar(40) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_x_people` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Manager` int(11) DEFAULT NULL,
  `Employee` int(11) DEFAULT NULL,
  `Description` varchar(100) DEFAULT NULL,
  `ipXip_RelationType` int(11) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_Update_Datetime` datetime DEFAULT NULL,
  `Last_Update_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pxp_mgr_idx` (`Manager`),
  KEY `fk_pxp_emp_idx` (`Employee`),
  KEY `fk_pxp_reltype_idx` (`ipXip_RelationType`),
  KEY `fk_pxp_user_idx` (`Last_Update_UserID`),
  CONSTRAINT `fk_pxp_emp` FOREIGN KEY (`Employee`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxp_mgr` FOREIGN KEY (`Manager`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxp_reltype` FOREIGN KEY (`ipXip_RelationType`) REFERENCES `people_x_people_relationtype` (`ID`),
  CONSTRAINT `fk_pxp_user` FOREIGN KEY (`Last_Update_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `people_x_people_relationtype` (
  `ID` int(11) NOT NULL,
  `PrimaryName` varchar(225) DEFAULT NULL,
  `Description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `periodic_review_config` (
  `ID` int(11) NOT NULL,
  `ModuleID` int(11) DEFAULT NULL,
  `Name` varchar(100) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `FilterConfig` longtext DEFAULT NULL,
  `Threshold` int(11) DEFAULT NULL,
  `ReviewStartDate` varchar(256) DEFAULT NULL,
  `RecurrenceConfig` longtext DEFAULT NULL,
  `WorkflowID` varchar(256) DEFAULT NULL,
  `CR_ProviderRef` varchar(128) DEFAULT NULL,
  `CR_Title` varchar(256) DEFAULT NULL,
  `CR_Summary` varchar(256) DEFAULT NULL,
  `CR_Type` int(11) DEFAULT NULL,
  `CR_Severity` int(11) DEFAULT NULL,
  `CR_Urgency` int(11) DEFAULT NULL,
  `IsEnabled` tinyint(1) DEFAULT NULL,
  `Createdate` datetime DEFAULT NULL,
  `LastUpdatedDate` datetime DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `LastUpdatedBy_ID` int(11) DEFAULT NULL,
  `cr_initiatoremail` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prc_createdby_idx` (`CreatedBy_ID`),
  KEY `fk_prc_lastupdatedby_idx` (`LastUpdatedBy_ID`),
  CONSTRAINT `fk_PR_Config_CreatedBy` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PR_Config_LastUpdatedBy` FOREIGN KEY (`LastUpdatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prc_createdby` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prc_lastupdatedby` FOREIGN KEY (`LastUpdatedBy_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `periodic_review_modules` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `ModuleID` int(11) DEFAULT NULL,
  `IsEnabled` tinyint(1) DEFAULT NULL,
  `Last_updatedDate` datetime DEFAULT NULL,
  `Last_updatedBy_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prm_user_idx` (`Last_updatedBy_ID`),
  CONSTRAINT `fk_PR_Modules_LastUpdatedBy` FOREIGN KEY (`Last_updatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prm_user` FOREIGN KEY (`Last_updatedBy_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `permission_names` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `permissions` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Object_Role_ID` int(11) DEFAULT NULL,
  `moduleid` int(11) DEFAULT NULL,
  `ipid` int(11) DEFAULT NULL,
  `permission` int(11) DEFAULT NULL,
  `objectid` int(11) DEFAULT NULL,
  `createdatetime` datetime DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime DEFAULT current_timestamp(),
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Object_Role_ID` (`Object_Role_ID`),
  KEY `objectid` (`objectid`),
  KEY `moduleid` (`moduleid`),
  KEY `ipid` (`ipid`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  KEY `permission` (`permission`),
  CONSTRAINT `permissions_ibfk_1` FOREIGN KEY (`Object_Role_ID`) REFERENCES `object_role` (`id`),
  CONSTRAINT `permissions_ibfk_10` FOREIGN KEY (`ipid`) REFERENCES `people` (`ID`),
  CONSTRAINT `permissions_ibfk_11` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `permissions_ibfk_12` FOREIGN KEY (`permission`) REFERENCES `permission_names` (`id`),
  CONSTRAINT `permissions_ibfk_2` FOREIGN KEY (`objectid`) REFERENCES `object_role_type` (`id`),
  CONSTRAINT `permissions_ibfk_3` FOREIGN KEY (`moduleid`) REFERENCES `module` (`id`),
  CONSTRAINT `permissions_ibfk_4` FOREIGN KEY (`ipid`) REFERENCES `people` (`ID`),
  CONSTRAINT `permissions_ibfk_5` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `permissions_ibfk_6` FOREIGN KEY (`permission`) REFERENCES `permission_names` (`id`),
  CONSTRAINT `permissions_ibfk_7` FOREIGN KEY (`Object_Role_ID`) REFERENCES `object_role` (`id`),
  CONSTRAINT `permissions_ibfk_8` FOREIGN KEY (`objectid`) REFERENCES `object_role_type` (`id`),
  CONSTRAINT `permissions_ibfk_9` FOREIGN KEY (`moduleid`) REFERENCES `module` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `ParentID` int(11) DEFAULT NULL,
  `isPublic` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle_Status` int(11) DEFAULT NULL,
  `Policy_Type` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `refNumber` varchar(45) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `EffectiveDate` datetime DEFAULT NULL,
  `EndDate` datetime DEFAULT NULL,
  `Internal` tinyint(1) DEFAULT NULL,
  `URL` text DEFAULT NULL,
  `createDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_policy_parent_idx` (`ParentID`),
  KEY `fk_policy_public_idx` (`isPublic`),
  KEY `fk_policy_status_idx` (`Status`),
  KEY `fk_policy_lifecycle_idx` (`Lifecycle_Status`),
  KEY `fk_policy_type_idx` (`Policy_Type`),
  KEY `fk_policy_createdby_idx` (`CreatedBy_ID`),
  KEY `fk_policy_lastuser_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_Policy_IsPublic` FOREIGN KEY (`isPublic`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_policy_createdby` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_policy_lastuser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_policy_lifecycle` FOREIGN KEY (`Lifecycle_Status`) REFERENCES `policy_lifecycle_status` (`ID`),
  CONSTRAINT `fk_policy_parent` FOREIGN KEY (`ParentID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_policy_public` FOREIGN KEY (`isPublic`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_policy_status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_policy_type` FOREIGN KEY (`Policy_Type`) REFERENCES `policy_type` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `ParentID` int(11) DEFAULT NULL,
  `isPublic` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Lifecycle_Status` int(11) DEFAULT NULL,
  `Policy_Type` int(11) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `refNumber` varchar(45) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `EffectiveDate` datetime DEFAULT NULL,
  `EndDate` datetime DEFAULT NULL,
  `Internal` tinyint(1) DEFAULT NULL,
  `URL` text DEFAULT NULL,
  `createDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `Rev_Type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_policy_audit_rev` FOREIGN KEY (`ID`) REFERENCES `policy` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `Policy_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `policy` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=301 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_lifecycle_status` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_policylc_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PolicyLCS_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_policylc_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_type` (
  `ID` int(11) NOT NULL,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_policytype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PolicyType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_policytype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_attribute` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `policyid` int(11) DEFAULT NULL,
  `relation_type` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `attributeid` int(11) DEFAULT NULL,
  `createdatetime` date DEFAULT NULL,
  `lastupdatedatetime` date DEFAULT NULL,
  `lastudpate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_p_x_attr_policy` (`policyid`),
  KEY `fk_p_x_attr_reltype` (`relation_type`),
  KEY `fk_p_x_attr_attr` (`attributeid`),
  KEY `fk_p_x_attr_user` (`lastudpate_userid`),
  CONSTRAINT `fk_p_x_attr_attr` FOREIGN KEY (`attributeid`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_p_x_attr_policy` FOREIGN KEY (`policyid`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_p_x_attr_reltype` FOREIGN KEY (`relation_type`) REFERENCES `policy_x_attribute_relationtype` (`id`),
  CONSTRAINT `fk_p_x_attr_user` FOREIGN KEY (`lastudpate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_attribute_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `policyid` int(11) DEFAULT NULL,
  `relation_type` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `attributeid` int(11) DEFAULT NULL,
  `createdatetime` date DEFAULT NULL,
  `lastupdatedatetime` date DEFAULT NULL,
  `lastudpate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_p_x_attr_audit_rev` (`rev`),
  CONSTRAINT `fk_p_x_attr_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_attribute_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(90) DEFAULT NULL,
  `description` varchar(128) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `deleteddatetime` date DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_p_x_attr_reltype_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_p_x_attr_reltype_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_businessarea` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Policy_ID` int(11) DEFAULT NULL,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PxBArea_Policy` (`Policy_ID`),
  KEY `fk_PxBArea_BArea` (`BusinessArea_ID`),
  KEY `fk_PxBArea_RelType` (`RelationType`),
  KEY `fk_PxBArea_LastUpdate` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxBArea_BArea` FOREIGN KEY (`BusinessArea_ID`) REFERENCES `business_area` (`ID`),
  CONSTRAINT `fk_PxBArea_LastUpdate` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxBArea_Policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxBArea_RelType` FOREIGN KEY (`RelationType`) REFERENCES `policy_x_businessarea_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_businessarea_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PxBArea_Audit_Rev` (`Rev`),
  CONSTRAINT `fk_PxBArea_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_businessarea_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PxBAreaRelType_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxBAreaRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_dataset` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `PolicyID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `DatasetID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUdpate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxd_policy_idx` (`PolicyID`),
  KEY `fk_pxd_reltype_idx` (`Relation_Type`),
  KEY `fk_pxd_dataset_idx` (`DatasetID`),
  KEY `fk_pxd_user_idx` (`LastUdpate_UserID`),
  CONSTRAINT `fk_PxDataset_Dataset` FOREIGN KEY (`DatasetID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_PxDataset_LastUpdate` FOREIGN KEY (`LastUdpate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxDataset_Policy` FOREIGN KEY (`PolicyID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxDataset_RelType` FOREIGN KEY (`Relation_Type`) REFERENCES `policy_x_dataset_relationtype` (`ID`),
  CONSTRAINT `fk_pxd_dataset` FOREIGN KEY (`DatasetID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_pxd_policy` FOREIGN KEY (`PolicyID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxd_reltype` FOREIGN KEY (`Relation_Type`) REFERENCES `policy_x_dataset_relationtype` (`ID`),
  CONSTRAINT `fk_pxd_user` FOREIGN KEY (`LastUdpate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_dataset_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `PolicyID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `DatasetID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUdpate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxd_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxDataset_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxd_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_dataset_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxd_reltype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxDatasetRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxd_reltype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_follow` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Policy_ID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pxf_policy_idx` (`Policy_ID`),
  KEY `fk_pxf_follow_idx` (`follow_id`),
  KEY `fk_pxf_user_idx` (`LastUpdate_UserID`),
  KEY `idx_policy_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_PxFollow_Follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_PxFollow_LastUpdate` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxFollow_Policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxf_follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_pxf_policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxf_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_glossary` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `PolicyID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `GlossaryID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUdpate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxg_policy_idx` (`PolicyID`),
  KEY `fk_pxg_reltype_idx` (`Relation_Type`),
  KEY `fk_pxg_glossary_idx` (`GlossaryID`),
  KEY `fk_pxg_user_idx` (`LastUdpate_UserID`),
  CONSTRAINT `fk_PxGloss_Glossary` FOREIGN KEY (`GlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_PxGloss_LastUpdate` FOREIGN KEY (`LastUdpate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxGloss_Policy` FOREIGN KEY (`PolicyID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxGloss_RelType` FOREIGN KEY (`Relation_Type`) REFERENCES `policy_x_glossary_relationtype` (`ID`),
  CONSTRAINT `fk_pxg_glossary` FOREIGN KEY (`GlossaryID`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_pxg_policy` FOREIGN KEY (`PolicyID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxg_reltype` FOREIGN KEY (`Relation_Type`) REFERENCES `policy_x_glossary_relationtype` (`ID`),
  CONSTRAINT `fk_pxg_user` FOREIGN KEY (`LastUdpate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_glossary_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `PolicyID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `GlossaryID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUdpate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxg_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxGloss_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxg_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_glossary_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxg_reltype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxGlossRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxg_reltype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Policy_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PxLegal_Policy` (`Policy_ID`),
  KEY `fk_PxLegal_Legal` (`Legal_ID`),
  KEY `fk_PxLegal_RelType` (`RelationType`),
  KEY `fk_PxLegal_LastUpdate` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxLegal_LastUpdate` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxLegal_Legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_PxLegal_Policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxLegal_RelType` FOREIGN KEY (`RelationType`) REFERENCES `policy_x_legal_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_legal_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PxLegal_Audit_Rev` (`Rev`),
  CONSTRAINT `fk_PxLegal_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_legal_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PxLegalRelType_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxLegalRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_X_IP` int(11) DEFAULT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdate_Datetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pxop_obj_idx` (`Object_X_IP`),
  KEY `fk_pxop_policy_idx` (`Policy_ID`),
  KEY `fk_pxop_user_idx` (`LastUpdate_UserID`),
  CONSTRAINT `fk_PxObjPeople_Object` FOREIGN KEY (`Object_X_IP`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_PxObjPeople_Policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxObjPeople_User` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxop_obj` FOREIGN KEY (`Object_X_IP`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_pxop_policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxop_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `Object_X_IP` int(11) DEFAULT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdate_Datetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxop_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxObjPeople_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxop_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_policy` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sourceid` int(11) DEFAULT NULL,
  `targetid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_p_x_p_source` (`sourceid`),
  KEY `fk_p_x_p_target` (`targetid`),
  KEY `fk_p_x_p_reltype` (`relationtype`),
  KEY `fk_p_x_p_user` (`lastupdate_userid`),
  CONSTRAINT `fk_PxPolicy_LastUpdate` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxPolicy_RelType` FOREIGN KEY (`relationtype`) REFERENCES `policy_x_policy_relation_type` (`ID`),
  CONSTRAINT `fk_PxPolicy_Source` FOREIGN KEY (`sourceid`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxPolicy_Target` FOREIGN KEY (`targetid`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_p_x_p_reltype` FOREIGN KEY (`relationtype`) REFERENCES `policy_x_policy_relation_type` (`ID`),
  CONSTRAINT `fk_p_x_p_source` FOREIGN KEY (`sourceid`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_p_x_p_target` FOREIGN KEY (`targetid`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_p_x_p_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_policy_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `SourceID` int(11) DEFAULT NULL,
  `TargetID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxp_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxPolicy_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxp_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_policy_relation_type` (
  `ID` int(11) NOT NULL,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pxp_reltype_user_idx` (`LastUpdate_UserID`),
  CONSTRAINT `fk_PxPolicyRelType_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxp_reltype_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_process` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `policy_id` int(11) NOT NULL,
  `process_id` int(11) NOT NULL,
  `relation_type` int(11) NOT NULL,
  `description` mediumtext DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxpc_user` (`lastupdate_userid`),
  KEY `idx_pxp_policy` (`policy_id`),
  KEY `idx_pxp_process` (`process_id`),
  KEY `idx_pxp_reltype` (`relation_type`),
  CONSTRAINT `fk_PxProcess_LastUpdate` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxProcess_Policy` FOREIGN KEY (`policy_id`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxProcess_Process` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_PxProcess_RelType` FOREIGN KEY (`relation_type`) REFERENCES `policy_x_process_relationtype` (`ID`),
  CONSTRAINT `fk_pxpc_policy` FOREIGN KEY (`policy_id`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxpc_process` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxpc_reltype` FOREIGN KEY (`relation_type`) REFERENCES `policy_x_process_relationtype` (`ID`),
  CONSTRAINT `fk_pxpc_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=47 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_process_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxpr_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxProcess_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxpr_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_process_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pxpr_reltype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxProcessRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxpr_reltype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_project` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `policy_id` int(11) NOT NULL,
  `project_id` int(11) NOT NULL,
  `relation_type` int(11) NOT NULL,
  `description` mediumtext DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxproj_user` (`lastupdate_userid`),
  KEY `idx_pxproj_policy` (`policy_id`),
  KEY `idx_pxproj_project` (`project_id`),
  KEY `idx_pxproj_reltype` (`relation_type`),
  CONSTRAINT `fk_PxProject_LastUpdate` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxProject_Policy` FOREIGN KEY (`policy_id`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxProject_Project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_PxProject_RelType` FOREIGN KEY (`relation_type`) REFERENCES `policy_x_project_relationtype` (`ID`),
  CONSTRAINT `fk_pxproj_policy` FOREIGN KEY (`policy_id`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxproj_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_pxproj_reltype` FOREIGN KEY (`relation_type`) REFERENCES `policy_x_project_relationtype` (`ID`),
  CONSTRAINT `fk_pxproj_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_project_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `Project_ID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxproj_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxProject_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxproj_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_project_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pxproj_reltype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxProjectRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxproj_reltype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_system` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Policy_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxsys_policy_idx` (`Policy_ID`),
  KEY `fk_pxsys_system_idx` (`System_ID`),
  KEY `fk_pxsys_reltype_idx` (`Relation_Type`),
  KEY `fk_pxsys_user_idx` (`LastUpdate_UserID`),
  CONSTRAINT `fk_PxSystem_LastUpdate` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PxSystem_Policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_PxSystem_RelType` FOREIGN KEY (`Relation_Type`) REFERENCES `policy_x_system_relationtype` (`ID`),
  CONSTRAINT `fk_PxSystem_System` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`),
  CONSTRAINT `fk_pxsys_policy` FOREIGN KEY (`Policy_ID`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_pxsys_reltype` FOREIGN KEY (`Relation_Type`) REFERENCES `policy_x_system_relationtype` (`ID`),
  CONSTRAINT `fk_pxsys_system` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`),
  CONSTRAINT `fk_pxsys_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_system_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `Policy_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `Relation_Type` int(11) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `fk_pxsys_audit_rev_idx` (`Rev`),
  CONSTRAINT `fk_PxSystem_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `fk_pxsys_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `policy_x_system_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxsys_reltype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PxSystemRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_pxsys_reltype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `precompute` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_ID` int(11) DEFAULT NULL,
  `Object_Type_ID` int(11) DEFAULT NULL,
  `Action` varchar(10) DEFAULT NULL,
  `Segment_ID` int(11) DEFAULT NULL,
  `Context` varchar(128) DEFAULT NULL,
  `Action_TimeStamp` datetime DEFAULT NULL,
  `Action_By` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Object_Type_ID` (`Object_Type_ID`),
  KEY `Segment_ID` (`Segment_ID`),
  KEY `Action_By` (`Action_By`),
  CONSTRAINT `fk_precompute_actionby` FOREIGN KEY (`Action_By`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_precompute_objtype` FOREIGN KEY (`Object_Type_ID`) REFERENCES `segment_object_type` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_precompute_segment` FOREIGN KEY (`Segment_ID`) REFERENCES `segment` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parentid` int(11) DEFAULT NULL,
  `ispublic` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `duration_type` int(11) DEFAULT NULL,
  `lifecycle_status` int(11) DEFAULT NULL,
  `processclass_id` int(11) DEFAULT NULL,
  `processautomation_id` int(11) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `refnumber` varchar(45) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `input_description` text DEFAULT NULL,
  `output_description` text DEFAULT NULL,
  `step_type` int(11) NOT NULL,
  `cancreate` tinyint(1) DEFAULT NULL,
  `canread` tinyint(1) DEFAULT NULL,
  `canupdate` tinyint(1) DEFAULT NULL,
  `candelete` tinyint(1) DEFAULT NULL,
  `canarchive` tinyint(1) DEFAULT NULL,
  `duration` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `under_revision` tinyint(1) NOT NULL DEFAULT 0,
  `active_cr_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_proc_parent` (`parentid`),
  KEY `fk_proc_public` (`ispublic`),
  KEY `fk_proc_status` (`status`),
  KEY `fk_proc_type` (`type`),
  KEY `fk_proc_lifecycle` (`lifecycle_status`),
  KEY `fk_proc_automation` (`processautomation_id`),
  KEY `fk_proc_duration` (`duration_type`),
  KEY `fk_proc_createdby` (`createdby_id`),
  KEY `fk_proc_lastuser` (`lastupdateuser_id`),
  KEY `fk_proc_class` (`processclass_id`),
  KEY `idx_process_step_type` (`step_type`),
  KEY `idx_process_revision` (`under_revision`,`active_cr_id`),
  CONSTRAINT `fk_proc_automation` FOREIGN KEY (`processautomation_id`) REFERENCES `process_automation` (`id`),
  CONSTRAINT `fk_proc_class` FOREIGN KEY (`processclass_id`) REFERENCES `process_class` (`id`),
  CONSTRAINT `fk_proc_createdby` FOREIGN KEY (`createdby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_proc_duration` FOREIGN KEY (`duration_type`) REFERENCES `process_duration_type` (`ID`),
  CONSTRAINT `fk_proc_lastuser` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_proc_lifecycle` FOREIGN KEY (`lifecycle_status`) REFERENCES `process_lifecycle_status` (`id`),
  CONSTRAINT `fk_proc_parent` FOREIGN KEY (`parentid`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_proc_public` FOREIGN KEY (`ispublic`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_proc_status` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_proc_type` FOREIGN KEY (`type`) REFERENCES `process_type` (`ID`),
  CONSTRAINT `fk_process_step_type` FOREIGN KEY (`step_type`) REFERENCES `process_step_type` (`ID`) ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL AUTO_INCREMENT,
  `parentid` int(11) DEFAULT NULL,
  `ispublic` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `duration_type` int(11) DEFAULT NULL,
  `lifecycle_status` int(11) DEFAULT NULL,
  `processclass_id` int(11) DEFAULT NULL,
  `processautomation_id` int(11) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `refnumber` varchar(45) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `input_description` text DEFAULT NULL,
  `output_description` text DEFAULT NULL,
  `step_type` varchar(20) DEFAULT NULL,
  `cancreate` tinyint(1) DEFAULT NULL,
  `canread` tinyint(1) DEFAULT NULL,
  `canupdate` tinyint(1) DEFAULT NULL,
  `candelete` tinyint(1) DEFAULT NULL,
  `canarchive` tinyint(1) DEFAULT NULL,
  `duration` int(11) DEFAULT NULL,
  `createdatetime` datetime DEFAULT NULL,
  `lastupdatedatetime` datetime DEFAULT NULL,
  `deleteddatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  UNIQUE KEY `rev` (`rev`),
  CONSTRAINT `fk_proc_audit_rev` FOREIGN KEY (`id`) REFERENCES `process` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=92 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `Process_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `process` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=298 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_automation` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_proc_automation_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_proc_automation_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_changes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `change_request_id` int(11) NOT NULL,
  `object_id` int(11) NOT NULL,
  `nobject_id` int(11) NOT NULL,
  `area_key` varchar(100) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_process_changes_cr_obj_area` (`change_request_id`,`object_id`,`area_key`),
  KEY `idx_process_changes_obj` (`object_id`),
  KEY `idx_process_changes_cr` (`change_request_id`),
  CONSTRAINT `fk_process_changes_cr` FOREIGN KEY (`change_request_id`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_process_changes_object` FOREIGN KEY (`object_id`) REFERENCES `process` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_class` (
  `id` int(11) NOT NULL,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_proc_class_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_proc_class_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_definition` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Reference` varchar(255) DEFAULT NULL,
  `Is_Default` tinyint(1) DEFAULT 0,
  `Description` mediumtext DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUserChange` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `Entity_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_entity` (`Entity_ID`),
  KEY `idx_status` (`Status`),
  CONSTRAINT `process_definition_ibfk_1` FOREIGN KEY (`Entity_ID`) REFERENCES `module` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_definition_bpmn` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Process_Definition_ID` int(11) NOT NULL,
  `Xml_Content` longtext DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`ID`),
  UNIQUE KEY `uk_process_def` (`Process_Definition_ID`),
  CONSTRAINT `fk_bpmn_process` FOREIGN KEY (`Process_Definition_ID`) REFERENCES `process_definition` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_duration_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_procduration_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_procduration_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_instance` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Reference` varchar(255) DEFAULT NULL,
  `Status` enum('Enabled','Disabled') DEFAULT 'Enabled',
  `LastUser_Change` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Updated_At` datetime DEFAULT NULL,
  `Process_Definition_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `LastUser_Change` (`LastUser_Change`),
  KEY `Process_Definition_ID` (`Process_Definition_ID`),
  CONSTRAINT `fk_procinst_lastuser` FOREIGN KEY (`LastUser_Change`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_procinst_procdef` FOREIGN KEY (`Process_Definition_ID`) REFERENCES `process_definition` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_lifecycle_status` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_proc_lifecycle_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_proc_lifecycle_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_step_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) NOT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_pst_lastupdateuser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_pst_lastupdateuser_people` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(128) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_proctype_user_idx` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_proctype_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_attribute` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `processid` int(11) DEFAULT NULL,
  `relation_type` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `attributeid` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastudpate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxattr_proc` (`processid`),
  KEY `fk_pxattr_rel` (`relation_type`),
  KEY `fk_pxattr_attr` (`attributeid`),
  KEY `fk_pxattr_user` (`lastudpate_userid`),
  CONSTRAINT `fk_pxattr_attr` FOREIGN KEY (`attributeid`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_pxattr_proc` FOREIGN KEY (`processid`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxattr_rel` FOREIGN KEY (`relation_type`) REFERENCES `process_x_attribute_relationtype` (`id`),
  CONSTRAINT `fk_pxattr_user` FOREIGN KEY (`lastudpate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_attribute_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `processid` int(11) DEFAULT NULL,
  `relation_type` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `attributeid` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastudpate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_pxattr_audit_rev` (`rev`),
  CONSTRAINT `fk_pxattr_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_attribute_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(90) DEFAULT NULL,
  `description` varchar(128) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxattr_reltype_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_pxattr_reltype_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_dataset` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `processid` int(11) DEFAULT NULL,
  `relation_type` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `datasetid` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastudpate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxd2_proc` (`processid`),
  KEY `fk_pxd_2rel` (`relation_type`),
  KEY `fk_pxd_d2s` (`datasetid`),
  KEY `fk_pxd_us2er` (`lastudpate_userid`),
  CONSTRAINT `fk_pxd2_proc` FOREIGN KEY (`processid`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxd_2rel` FOREIGN KEY (`relation_type`) REFERENCES `process_x_dataset_relationtype` (`id`),
  CONSTRAINT `fk_pxd_d2s` FOREIGN KEY (`datasetid`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_pxd_us2er` FOREIGN KEY (`lastudpate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_dataset_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `processid` int(11) DEFAULT NULL,
  `relation_type` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `datasetid` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastudpate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_pxd_aud2it_rev` (`rev`),
  CONSTRAINT `fk_pxd_aud2it_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_dataset_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(90) DEFAULT NULL,
  `description` varchar(128) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxd2_reltype_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_pxd2_reltype_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_follow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `process_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxf2_proc` (`process_id`),
  KEY `fk_pxf_2follow` (`follow_id`),
  KEY `fk_pxf_u2ser` (`lastupdate_userid`),
  KEY `idx_process_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_pxf2_proc` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxf_2follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_pxf_u2ser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_interface` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `process_id` int(11) DEFAULT NULL,
  `interface_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxi2f_proc` (`process_id`),
  KEY `fk_pxif2_if` (`interface_id`),
  KEY `fk_pxif_2rel` (`relationtype`),
  KEY `fk_pxif_u2ser` (`lastupdate_userid`),
  CONSTRAINT `fk_pxi2f_proc` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxif2_if` FOREIGN KEY (`interface_id`) REFERENCES `interface` (`id`),
  CONSTRAINT `fk_pxif_2rel` FOREIGN KEY (`relationtype`) REFERENCES `process_x_interface_relationtype` (`id`),
  CONSTRAINT `fk_pxif_u2ser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_interface_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `process_id` int(11) DEFAULT NULL,
  `interface_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_pxif_au2dit_rev` (`rev`),
  CONSTRAINT `fk_pxif_au2dit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_interface_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxi2f_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_pxi2f_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Process_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_legal_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_legall_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Process_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_objectxpeople` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `object_x_ip` int(11) DEFAULT NULL,
  `process_id` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_p_x_oxp_2oxp` (`object_x_ip`),
  KEY `fk_p_x_ox2p_proc` (`process_id`),
  KEY `fk_p_x_oxp2_user` (`lastupdate_userid`),
  CONSTRAINT `fk_p_x_ox2p_proc` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_p_x_oxp2_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_p_x_oxp_2oxp` FOREIGN KEY (`object_x_ip`) REFERENCES `object_x_people` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_objectxpeople_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `object_x_ip` int(11) DEFAULT NULL,
  `process_id` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_p_x_oxp_a2udit_rev` (`rev`),
  CONSTRAINT `fk_p_x_oxp_a2udit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_process` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sourceprocess_id` int(11) DEFAULT NULL,
  `targetprocess_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `rank` int(11) DEFAULT NULL,
  `annotations` varchar(40) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedateime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxp_sour2ce` (`sourceprocess_id`),
  KEY `fk_pxp_target2` (`targetprocess_id`),
  KEY `fk_pxp_relty2pe` (`relationtype`),
  KEY `fk_pxp_us2er` (`lastupdate_userid`),
  CONSTRAINT `fk_pxp_relty2pe` FOREIGN KEY (`relationtype`) REFERENCES `process_x_process_relationtype` (`id`),
  CONSTRAINT `fk_pxp_sour2ce` FOREIGN KEY (`sourceprocess_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxp_target2` FOREIGN KEY (`targetprocess_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxp_us2er` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_process_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `sourceprocess_id` int(11) DEFAULT NULL,
  `targetprocess_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `rank` int(11) DEFAULT NULL,
  `annotations` varchar(40) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedateime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_pxp_au2dit_rev` (`rev`),
  CONSTRAINT `fk_pxp_au2dit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_process_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxp_reltyp2e_user` (`lastupdate_userid`),
  CONSTRAINT `fk_pxp_reltyp2e_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_system` (
  `id` int(11) NOT NULL,
  `process_id` int(11) DEFAULT NULL,
  `system_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `created_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `last_update_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxsys_pr2oc` (`process_id`),
  KEY `fk_pxsys_sys2` (`system_id`),
  KEY `fk_pxsys_rel2` (`relationtype`),
  KEY `fk_pxsys_user2` (`last_update_userid`),
  CONSTRAINT `fk_pxsys_pr2oc` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_pxsys_rel2` FOREIGN KEY (`relationtype`) REFERENCES `process_x_system_relationtype` (`id`),
  CONSTRAINT `fk_pxsys_sys2` FOREIGN KEY (`system_id`) REFERENCES `system` (`id`),
  CONSTRAINT `fk_pxsys_user2` FOREIGN KEY (`last_update_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_system_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `process_id` int(11) DEFAULT NULL,
  `system_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `created_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `last_update_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_px2sys_audit_rev` (`rev`),
  CONSTRAINT `fk_px2sys_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `process_x_system_relationtype` (
  `id` int(11) NOT NULL,
  `primaryname` varchar(128) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_pxsys_r2eltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_pxsys_r2eltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `lifecycle_status` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `is_public` int(11) DEFAULT NULL,
  `refnumber` varchar(45) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `longname` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_product_parent` (`parent_id`),
  KEY `fk_product_public` (`is_public`),
  KEY `fk_product_status` (`status`),
  KEY `fk_product_user` (`lastupdate_userid`),
  KEY `fk_product_life` (`lifecycle_status`),
  KEY `fk_Create_product_user` (`createdby_id`),
  CONSTRAINT `fk_Create_product_user` FOREIGN KEY (`createdby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_product_life` FOREIGN KEY (`lifecycle_status`) REFERENCES `product_lifecycle` (`id`),
  CONSTRAINT `fk_product_parent` FOREIGN KEY (`parent_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_product_public` FOREIGN KEY (`is_public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_product_status` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_product_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `lifecycle_status` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `is_public` int(11) DEFAULT NULL,
  `refnumber` varchar(45) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `longname` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL,
  `lastupdatedatetime` datetime NOT NULL,
  `deleteddatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  UNIQUE KEY `rev` (`rev`),
  CONSTRAINT `fk_product_audit_rev` FOREIGN KEY (`id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `Product_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=121 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_lifecycle` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_product_lifecycle_user` (`lastupdate_userid`),
  CONSTRAINT `fk_ProductLifecycle_LastUser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_product_lifecycle_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_businessarea` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Product_ID` int(11) DEFAULT NULL,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_businessarea_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_businessarea_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXBAreaRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PXBAreaRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_client` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Product_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT NULL ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXClient_Product` (`Product_ID`),
  KEY `fk_PXClient_Client` (`Client_ID`),
  KEY `fk_PXClient_RelType` (`RelationType`),
  KEY `fk_PXClient_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PXClient_Client` FOREIGN KEY (`Client_ID`) REFERENCES `client` (`ID`),
  CONSTRAINT `fk_PXClient_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_PXClient_Product` FOREIGN KEY (`Product_ID`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_PXClient_RelType` FOREIGN KEY (`RelationType`) REFERENCES `product_x_client_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_client_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `Client_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXClient_Audit_Rev` (`Rev`),
  CONSTRAINT `fk_PXClient_Audit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_client_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXClientRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PXClientRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_dataset` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Product_ID` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Product_Dataset_Relation_Type` int(11) DEFAULT NULL,
  `Dataset_Legal_Relation_Type` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_product_x_dataset_product` (`Product_ID`),
  KEY `fk_product_x_dataset_dataset` (`Dataset_ID`),
  KEY `fk_product_dataset_relation_type` (`Product_Dataset_Relation_Type`),
  KEY `fk_product_x_dataset_lastupdated_user` (`LastUpdated_UserID`),
  CONSTRAINT `fk_product_dataset_relation_type` FOREIGN KEY (`Product_Dataset_Relation_Type`) REFERENCES `product_x_dataset_relationtype` (`ID`),
  CONSTRAINT `fk_product_x_dataset_dataset` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_product_x_dataset_lastupdated_user` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_product_x_dataset_product` FOREIGN KEY (`Product_ID`) REFERENCES `product` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_dataset_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Product_Dataset_Relation_Type` int(11) DEFAULT NULL,
  `Dataset_Legal_Relation_Type` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_dataset_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXDatasetRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PXDatasetRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_follow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `product_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_follow_prod` (`product_id`),
  KEY `fk_prod_x_follow_follow` (`follow_id`),
  KEY `fk_prod_x_follow_user` (`lastupdate_userid`),
  KEY `idx_product_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_prod_x_follow_follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_prod_x_follow_prod` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_prod_x_follow_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_glossary` (
  `productid` int(11) DEFAULT NULL,
  `glossaryid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  KEY `fk_prod_x_gl_prod` (`productid`),
  KEY `fk_prod_x_gl_glossary` (`glossaryid`),
  KEY `fk_prod_x_gl_rel` (`relationtype`),
  KEY `fk_prod_x_gl_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prod_x_gl_glossary` FOREIGN KEY (`glossaryid`) REFERENCES `glossary` (`ID`),
  CONSTRAINT `fk_prod_x_gl_prod` FOREIGN KEY (`productid`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_prod_x_gl_rel` FOREIGN KEY (`relationtype`) REFERENCES `product_x_glossary_relationtype` (`id`),
  CONSTRAINT `fk_prod_x_gl_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_glossary_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `productid` int(11) DEFAULT NULL,
  `glossaryid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prod_x_gl_audit_rev` (`rev`),
  CONSTRAINT `fk_prod_x_gl_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_glossary_relationtype` (
  `id` int(11) NOT NULL,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_gl_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_PXGlossRelType_LastUser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prod_x_gl_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Product_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_productxlegal_product` (`Product_ID`),
  KEY `fk_productxlegal_legal` (`Legal_ID`),
  KEY `fk_productxlegal_relationtype` (`RelationType`),
  KEY `fk_productxlegal_user` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_productxlegal_legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_productxlegal_product` FOREIGN KEY (`Product_ID`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_productxlegal_relationtype` FOREIGN KEY (`RelationType`) REFERENCES `product_x_legalentity_relationtype` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_productxlegal_user` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_legal_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_legal_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_legalentity_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXLegalEntRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PXLegalEntRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_objectxpeople` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `object_x_ip` int(11) DEFAULT NULL,
  `product_id` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_oxp_oxp` (`object_x_ip`),
  KEY `fk_prod_x_oxp_prod` (`product_id`),
  KEY `fk_prod_x_oxp_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prod_x_oxp_oxp` FOREIGN KEY (`object_x_ip`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_prod_x_oxp_prod` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_prod_x_oxp_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_objectxpeople_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `object_x_ip` int(11) DEFAULT NULL,
  `product_id` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prod_x_oxp_audit_rev` (`rev`),
  CONSTRAINT `fk_prod_x_oxp_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_policy` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `productid` int(11) DEFAULT NULL,
  `policyid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_pol_prod` (`productid`),
  KEY `fk_prod_x_pol_pol` (`policyid`),
  KEY `fk_prod_x_pol_rel` (`relationtype`),
  KEY `fk_prod_x_pol_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prod_x_pol_pol` FOREIGN KEY (`policyid`) REFERENCES `policy` (`ID`),
  CONSTRAINT `fk_prod_x_pol_prod` FOREIGN KEY (`productid`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_prod_x_pol_rel` FOREIGN KEY (`relationtype`) REFERENCES `product_x_policy_relationtype` (`id`),
  CONSTRAINT `fk_prod_x_pol_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_policy_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `productid` int(11) DEFAULT NULL,
  `policyid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prod_x_pol_audit_rev` (`rev`),
  CONSTRAINT `fk_prod_x_pol_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_policy_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prod_x_pol_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_PXPolicyRelType_LastUser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prod_x_pol_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_proces_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `ProductID` int(11) DEFAULT NULL,
  `ProcessID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_process` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `productid` int(11) DEFAULT NULL,
  `processid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_proc_prod` (`productid`),
  KEY `fk_prod_x_proc_proc` (`processid`),
  KEY `fk_prod_x_proc_rel` (`relationtype`),
  KEY `fk_prod_x_proc_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prod_x_proc_proc` FOREIGN KEY (`processid`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_prod_x_proc_prod` FOREIGN KEY (`productid`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_prod_x_proc_rel` FOREIGN KEY (`relationtype`) REFERENCES `product_x_process_relationtype` (`id`),
  CONSTRAINT `fk_prod_x_proc_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_process_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `productid` int(11) DEFAULT NULL,
  `processid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prod_x_proc_audit_rev` (`rev`),
  CONSTRAINT `fk_prod_x_proc_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_process_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_proc_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_PXProcessRelType_LastUser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prod_x_proc_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_project` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `productid` int(11) DEFAULT NULL,
  `projectid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prod_x_prj_prod` (`productid`),
  KEY `fk_prod_x_prj_proj` (`projectid`),
  KEY `fk_prod_x_prj_rel` (`relationtype`),
  KEY `fk_prod_x_prj_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prod_x_prj_prod` FOREIGN KEY (`productid`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_prod_x_prj_proj` FOREIGN KEY (`projectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prod_x_prj_rel` FOREIGN KEY (`relationtype`) REFERENCES `product_x_project_relationtype` (`id`),
  CONSTRAINT `fk_prod_x_prj_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_project_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `productid` int(11) DEFAULT NULL,
  `projectid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prod_x_prj_audit_rev` (`rev`),
  CONSTRAINT `fk_prod_x_prj_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_project_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prod_x_prj_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_PXProjectRelType_LastUser` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_prod_x_prj_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_system` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Product_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Product_System_Relation_Type` int(11) DEFAULT NULL,
  `Legal_Relation_Type` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_product_x_system_product` (`Product_ID`),
  KEY `fk_product_x_system_system` (`System_ID`),
  KEY `fk_product_x_system_legal` (`Legal_ID`),
  KEY `fk_product_x_system_relation` (`Product_System_Relation_Type`),
  KEY `fk_product_x_system_lastupdated_user` (`LastUpdated_UserID`),
  KEY `fk_product_x_system_legal_relation` (`Legal_Relation_Type`),
  CONSTRAINT `fk_product_x_system_lastupdated_user` FOREIGN KEY (`LastUpdated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_product_x_system_legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_product_x_system_legal_relation` FOREIGN KEY (`Legal_Relation_Type`) REFERENCES `product_x_legal_relationtype` (`ID`),
  CONSTRAINT `fk_product_x_system_product` FOREIGN KEY (`Product_ID`) REFERENCES `product` (`id`),
  CONSTRAINT `fk_product_x_system_relation` FOREIGN KEY (`Product_System_Relation_Type`) REFERENCES `product_x_system_relationtype` (`ID`),
  CONSTRAINT `fk_product_x_system_system` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_system_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `System_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Product_System_Relation_Type` int(11) DEFAULT NULL,
  `Legal_Relation_Type` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdated_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `product_x_system_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_PXSystemRelType_LastUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_PXSystemRelType_LastUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parentid` int(11) DEFAULT NULL,
  `is_public` int(11) DEFAULT NULL,
  `rag` int(11) DEFAULT NULL,
  `classification` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `lifecycle_status` int(11) DEFAULT NULL,
  `project_type` int(11) DEFAULT NULL,
  `refnumber` varchar(45) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `startdate` date NOT NULL DEFAULT current_timestamp(),
  `enddate` date DEFAULT current_timestamp(),
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime DEFAULT NULL,
  `deletedatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_project_parent` (`parentid`),
  KEY `fk_project_public` (`is_public`),
  KEY `fk_project_class` (`classification`),
  KEY `fk_project_rag` (`rag`),
  KEY `fk_project_status` (`status`),
  KEY `fk_project_lifecycle` (`lifecycle_status`),
  KEY `fk_project_type_fk` (`project_type`),
  KEY `fk_project_createdby` (`createdby_id`),
  KEY `fk_project_lastuser` (`lastupdateuser_id`),
  CONSTRAINT `fk_project_class` FOREIGN KEY (`classification`) REFERENCES `project_classification` (`id`),
  CONSTRAINT `fk_project_createdby` FOREIGN KEY (`createdby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_project_lastuser` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_project_lifecycle` FOREIGN KEY (`lifecycle_status`) REFERENCES `project_lifecycle` (`id`),
  CONSTRAINT `fk_project_parent` FOREIGN KEY (`parentid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_project_public` FOREIGN KEY (`is_public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_project_rag` FOREIGN KEY (`rag`) REFERENCES `project_rag` (`id`),
  CONSTRAINT `fk_project_status` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_project_type_fk` FOREIGN KEY (`project_type`) REFERENCES `project_comment_type` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL AUTO_INCREMENT,
  `parentid` int(11) DEFAULT NULL,
  `is_public` int(11) DEFAULT NULL,
  `rag` int(11) DEFAULT NULL,
  `classification` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `lifecycle_status` int(11) DEFAULT NULL,
  `project_type` int(11) DEFAULT NULL,
  `refnumber` varchar(45) DEFAULT NULL,
  `primaryname` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `startdate` date NOT NULL,
  `enddate` date DEFAULT NULL,
  `createdatetime` datetime NOT NULL,
  `lastupdatedatetime` datetime NOT NULL,
  `deletedatetime` datetime DEFAULT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  UNIQUE KEY `rev` (`rev`),
  CONSTRAINT `fk_project_audit_rev` FOREIGN KEY (`id`) REFERENCES `project` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `Project_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `project` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=215 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_classification` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_proj_class_parent` (`parent_id`),
  KEY `fk_proj_class_user` (`lastupdate_userid`),
  CONSTRAINT `fk_proj_class_parent` FOREIGN KEY (`parent_id`) REFERENCES `project_classification` (`id`),
  CONSTRAINT `fk_proj_class_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_comment_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_lifecycle` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_proj_lifecycle_user` (`lastupdate_userid`),
  CONSTRAINT `fk_proj_lifecycle_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_rag` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(45) DEFAULT NULL,
  `description` varchar(128) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `fk_project_type_user` (`lastupdateuser_id`),
  CONSTRAINT `fk_project_type_user` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_attribute` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `projectid` int(11) DEFAULT NULL,
  `attribute_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_attr_proj` (`projectid`),
  KEY `fk_prj_x_attr_attr` (`attribute_id`),
  KEY `fk_prj_x_attr_rel` (`relationtype`),
  KEY `fk_prj_x_attr_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_attr_attr` FOREIGN KEY (`attribute_id`) REFERENCES `attribute` (`ID`),
  CONSTRAINT `fk_prj_x_attr_proj` FOREIGN KEY (`projectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_attr_rel` FOREIGN KEY (`relationtype`) REFERENCES `project_x_attribute_relationtype` (`id`),
  CONSTRAINT `fk_prj_x_attr_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_attribute_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `projectid` int(11) DEFAULT NULL,
  `attribute_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prj_x_attr_audit_rev` (`rev`),
  CONSTRAINT `fk_prj_x_attr_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_attribute_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prj_x_attr_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_attr_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_businessarea` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Project_ID` int(11) DEFAULT NULL,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_businessarea_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Project_ID` int(11) DEFAULT NULL,
  `BusinessArea_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_businessarea_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_capability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Project_ID` int(11) DEFAULT NULL,
  `Capability_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_capability_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Project_ID` int(11) DEFAULT NULL,
  `Capability_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_capability_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) NOT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_pxcrt_lastupdateuser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_pxcrt_people` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_dataset` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `projectid` int(11) DEFAULT NULL,
  `dataset_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_ds_proj` (`projectid`),
  KEY `fk_prj_x_ds_ds` (`dataset_id`),
  KEY `fk_prj_x_ds_rel` (`relationtype`),
  KEY `fk_prj_x_ds_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_ds_ds` FOREIGN KEY (`dataset_id`) REFERENCES `dataset` (`ID`),
  CONSTRAINT `fk_prj_x_ds_proj` FOREIGN KEY (`projectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_ds_rel` FOREIGN KEY (`relationtype`) REFERENCES `project_x_dataset_relationtype` (`id`),
  CONSTRAINT `fk_prj_x_ds_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_dataset_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `projectid` int(11) DEFAULT NULL,
  `dataset_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prj_x_ds_audit_rev` (`rev`),
  CONSTRAINT `fk_prj_x_ds_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_dataset_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prj_x_ds_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_ds_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_follow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `project_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_follow_proj` (`project_id`),
  KEY `fk_prj_x_follow_follow` (`follow_id`),
  KEY `fk_prj_x_follow_user` (`lastupdate_userid`),
  KEY `idx_project_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_prj_x_follow_follow` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_prj_x_follow_proj` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_follow_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_objectxpeople` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `object_x_ip` int(11) DEFAULT NULL,
  `project_id` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_oxp_oxp` (`object_x_ip`),
  KEY `fk_prj_x_oxp_proj` (`project_id`),
  KEY `fk_prj_x_oxp_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_oxp_oxp` FOREIGN KEY (`object_x_ip`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `fk_prj_x_oxp_proj` FOREIGN KEY (`project_id`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_oxp_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_objectxpeople_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `object_x_ip` int(11) DEFAULT NULL,
  `project_id` int(11) DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_datetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prj_x_oxp_audit_rev` (`rev`),
  CONSTRAINT `fk_prj_x_oxp_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_process` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `projectid` int(11) DEFAULT NULL,
  `process_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_proc_proj` (`projectid`),
  KEY `fk_prj_x_proc_proc` (`process_id`),
  KEY `fk_prj_x_proc_rel` (`relationtype`),
  KEY `fk_prj_x_proc_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_proc_proc` FOREIGN KEY (`process_id`) REFERENCES `process` (`id`),
  CONSTRAINT `fk_prj_x_proc_proj` FOREIGN KEY (`projectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_proc_rel` FOREIGN KEY (`relationtype`) REFERENCES `project_x_process_relationtype` (`id`),
  CONSTRAINT `fk_prj_x_proc_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_process_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `projectid` int(11) DEFAULT NULL,
  `process_id` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prj_x_proc_audit_rev` (`rev`),
  CONSTRAINT `fk_prj_x_proc_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_process_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_proc_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_proc_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_project` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `sourceprojectid` int(11) DEFAULT NULL,
  `targetprojectid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `startdate` datetime NOT NULL DEFAULT current_timestamp(),
  `enddate` datetime DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_prj_source` (`sourceprojectid`),
  KEY `fk_prj_x_prj_target` (`targetprojectid`),
  KEY `fk_prj_x_prj_rel` (`relationtype`),
  KEY `fk_prj_x_prj_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_prj_rel` FOREIGN KEY (`relationtype`) REFERENCES `project_x_project_relationtype` (`id`),
  CONSTRAINT `fk_prj_x_prj_source` FOREIGN KEY (`sourceprojectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_prj_target` FOREIGN KEY (`targetprojectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_prj_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_project_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `sourceprojectid` int(11) DEFAULT NULL,
  `targetprojectid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `startdate` datetime NOT NULL DEFAULT current_timestamp(),
  `enddate` datetime DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prj_x_prj_audit_rev` (`rev`),
  CONSTRAINT `fk_prj_x_prj_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_project_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_prj_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_prj_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_system` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `projectid` int(11) DEFAULT NULL,
  `systemid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_sys_proj` (`projectid`),
  KEY `fk_prj_x_sys_sys` (`systemid`),
  KEY `fk_prj_x_sys_rel` (`relationtype`),
  KEY `fk_prj_x_sys_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_sys_proj` FOREIGN KEY (`projectid`) REFERENCES `project` (`id`),
  CONSTRAINT `fk_prj_x_sys_rel` FOREIGN KEY (`relationtype`) REFERENCES `project_x_system_relationtype` (`id`),
  CONSTRAINT `fk_prj_x_sys_sys` FOREIGN KEY (`systemid`) REFERENCES `system` (`id`),
  CONSTRAINT `fk_prj_x_sys_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_system_audit` (
  `id` int(11) NOT NULL,
  `rev` int(11) NOT NULL,
  `projectid` int(11) DEFAULT NULL,
  `systemid` int(11) DEFAULT NULL,
  `relationtype` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `createdatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `lastupdate_userid` int(11) DEFAULT NULL,
  `revtype` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev`),
  KEY `fk_prj_x_sys_audit_rev` (`rev`),
  CONSTRAINT `fk_prj_x_sys_audit_rev` FOREIGN KEY (`rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `project_x_system_relationtype` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `reversename` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime NOT NULL DEFAULT current_timestamp(),
  `deleteddatetime` datetime DEFAULT NULL,
  `lastupdate_userid` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_prj_x_sys_reltype_user` (`lastupdate_userid`),
  CONSTRAINT `fk_prj_x_sys_reltype_user` FOREIGN KEY (`lastupdate_userid`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `RefNumber` varchar(255) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `Rank` int(11) DEFAULT NULL,
  `primaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `AdditionalInfo` longtext DEFAULT NULL,
  `PublicationDate` date DEFAULT NULL,
  `CommentsDate` date DEFAULT NULL,
  `FinalisationDate` date DEFAULT NULL,
  `ComplianceDate` date DEFAULT NULL,
  `LegalAdvice` varchar(255) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `RegulationMaturity_ID` int(11) DEFAULT NULL,
  `RegulationProbability_ID` int(11) DEFAULT NULL,
  `RegulationStatus_ID` int(11) DEFAULT NULL,
  `RegulationImpactRating_ID` int(11) DEFAULT NULL,
  `LegalAdviceType_ID` int(11) DEFAULT NULL,
  `RegulationStage_ID` int(11) DEFAULT NULL,
  `ComplianceLevel_ID` int(11) DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Regulation_Parent` (`Parent_ID`),
  KEY `fk_Regulation_ComplianceLevel` (`ComplianceLevel_ID`),
  KEY `fk_Regulation_LastUpdateUser` (`LastUpdate_UserID`),
  KEY `fk_Regulation_LegalAdviceType` (`LegalAdviceType_ID`),
  KEY `fk_Regulation_ImpactRating` (`RegulationImpactRating_ID`),
  KEY `fk_Regulation_Maturity` (`RegulationMaturity_ID`),
  KEY `fk_Regulation_Probability` (`RegulationProbability_ID`),
  KEY `fk_Regulation_Stage` (`RegulationStage_ID`),
  KEY `fk_Regulation_Status` (`RegulationStatus_ID`),
  KEY `fk_Regulation_IsPublic` (`Is_Public`),
  CONSTRAINT `fk_Regulation_ComplianceLevel` FOREIGN KEY (`ComplianceLevel_ID`) REFERENCES `regulation_compliance_level` (`ID`),
  CONSTRAINT `fk_Regulation_ImpactRating` FOREIGN KEY (`RegulationImpactRating_ID`) REFERENCES `regulation_impact_rating` (`ID`),
  CONSTRAINT `fk_Regulation_IsPublic` FOREIGN KEY (`Is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `fk_Regulation_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_Regulation_LegalAdviceType` FOREIGN KEY (`LegalAdviceType_ID`) REFERENCES `legal_advice_type` (`ID`),
  CONSTRAINT `fk_Regulation_Maturity` FOREIGN KEY (`RegulationMaturity_ID`) REFERENCES `regulation_maturity` (`ID`),
  CONSTRAINT `fk_Regulation_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `regulation` (`ID`),
  CONSTRAINT `fk_Regulation_Probability` FOREIGN KEY (`RegulationProbability_ID`) REFERENCES `regulation_probability` (`ID`),
  CONSTRAINT `fk_Regulation_Stage` FOREIGN KEY (`RegulationStage_ID`) REFERENCES `regulation_stage` (`ID`),
  CONSTRAINT `fk_Regulation_Status` FOREIGN KEY (`RegulationStatus_ID`) REFERENCES `regulation_status` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Is_Public` int(11) DEFAULT NULL,
  `RefNumber` varchar(255) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `Rank` int(11) DEFAULT NULL,
  `primaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `AdditionalInfo` longtext DEFAULT NULL,
  `PublicationDate` date DEFAULT NULL,
  `CommentsDate` date DEFAULT NULL,
  `FinalisationDate` date DEFAULT NULL,
  `ComplianceDate` date DEFAULT NULL,
  `LegalAdvice` varchar(255) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `RegulationMaturity_ID` int(11) DEFAULT NULL,
  `RegulationProbability_ID` int(11) DEFAULT NULL,
  `RegulationStatus_ID` int(11) DEFAULT NULL,
  `RegulationImpactRating_ID` int(11) DEFAULT NULL,
  `LegalAdviceType_ID` int(11) DEFAULT NULL,
  `RegulationStage_ID` int(11) DEFAULT NULL,
  `ComplianceLevel_ID` int(11) DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_Regulation_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `regulation_audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=184 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_compliance_level` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulationComplianceLevel_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationComplianceLevel_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_impact_rating` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulationImpactRating_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationImpactRating_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_maturity` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulationMaturity_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationMaturity_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_probability` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulationProbability_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationProbability_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_stage` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulationStage_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationStage_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_status` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulationStatus_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationStatus_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_follow` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `RegulationID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RXFollow_RegE` (`RegulationID`),
  KEY `fk_RXFollow_FollowE` (`follow_id`),
  KEY `fk_RXFollow_LastUserE` (`LastUpdate_UserID`),
  KEY `idx_regulation_x_follow_follow_id` (`follow_id`),
  CONSTRAINT `fk_RXFollow_FollowE` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_RXFollow_LastUserE` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_RXFollow_RegE` FOREIGN KEY (`RegulationID`) REFERENCES `regulation` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_follow_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `RegulationID` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL COMMENT 'Values: Added, Modified, Deleted',
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `regulation_x_follow_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `regulation_x_follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `RegulationID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_RegulationID` (`RegulationID`),
  KEY `idx_Object_x_ipid` (`Object_x_ipid`),
  KEY `idx_LastUpdate_UserID` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulationXObjectXPeople_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_RegulationXObjectXPeople_ObjectXPeople` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_RegulationXObjectXPeople_Regulation` FOREIGN KEY (`RegulationID`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE `regulation_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `RegulationID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `regulation_x_objectxpeople_audit_ibfk_1` FOREIGN KEY (`ID`) REFERENCES `regulation_x_objectxpeople` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;



CREATE TABLE `regulation_x_policy` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `RegulationID` int(11) NOT NULL,
  `PolicyID` int(11) NOT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_reg` (`RegulationID`),
  KEY `idx_pol` (`PolicyID`),
  KEY `idx_reltype` (`RelationType`),
  KEY `idx_user` (`LastUpdate_UserID`),
  CONSTRAINT `fk_regxpol_pol` FOREIGN KEY (`PolicyID`) REFERENCES `policy` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_regxpol_reg` FOREIGN KEY (`RegulationID`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_regxpol_reltype` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_policy_relationtype` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_regxpol_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_policy_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryName` varchar(255) NOT NULL,
  `Description` text DEFAULT NULL,
  `Priority` int(11) DEFAULT 0,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_pol_reltype_user` (`LastUpdate_UserID`),
  CONSTRAINT `fk_pol_reltype_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_product` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `RegulationID` int(11) NOT NULL,
  `ProductID` int(11) NOT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_reg` (`RegulationID`),
  KEY `idx_prod` (`ProductID`),
  KEY `idx_reltype` (`RelationType`),
  KEY `idx_user` (`LastUpdate_UserID`),
  CONSTRAINT `fk_regxprod_prod` FOREIGN KEY (`ProductID`) REFERENCES `product` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_regxprod_reg` FOREIGN KEY (`RegulationID`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_regxprod_reltype` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_product_relationtype` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_regxprod_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_product_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryName` varchar(255) NOT NULL,
  `Description` text DEFAULT NULL,
  `Priority` int(11) DEFAULT 0,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_prod_reltype_user` (`LastUpdate_UserID`),
  CONSTRAINT `fk_prod_reltype_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_project` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `RegulationID` int(11) NOT NULL,
  `ProjectID` int(11) NOT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_reg` (`RegulationID`),
  KEY `idx_proj` (`ProjectID`),
  KEY `idx_reltype` (`RelationType`),
  KEY `idx_user` (`LastUpdate_UserID`),
  CONSTRAINT `fk_regxproj_proj` FOREIGN KEY (`ProjectID`) REFERENCES `project` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_regxproj_reg` FOREIGN KEY (`RegulationID`) REFERENCES `regulation` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_regxproj_reltype` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_project_relationtype` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_regxproj_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_project_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryName` varchar(255) NOT NULL,
  `ReverseName` varchar(255) DEFAULT NULL,
  `Description` text DEFAULT NULL,
  `Priority` int(11) DEFAULT 0,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_proj_reltype_user` (`LastUpdate_UserID`),
  CONSTRAINT `fk_proj_reltype_user` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulation` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `SourceRegulationID` int(11) DEFAULT NULL,
  `TargetRegulationID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `FK_RegulationXRegulation_SourceRegulation` (`SourceRegulationID`),
  KEY `FK_RegulationXRegulation_TargetRegulation` (`TargetRegulationID`),
  KEY `FK_RegulationXRegulation_RelationType` (`RelationType`),
  KEY `FK_RegulationXRegulation_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `FK_RegulationXRegulation_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `FK_RegulationXRegulation_RelationType` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_regulation_relationtype` (`ID`),
  CONSTRAINT `FK_RegulationXRegulation_SourceRegulation` FOREIGN KEY (`SourceRegulationID`) REFERENCES `regulation` (`ID`),
  CONSTRAINT `FK_RegulationXRegulation_TargetRegulation` FOREIGN KEY (`TargetRegulationID`) REFERENCES `regulation` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulation_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `SourceRegulationID` int(11) DEFAULT NULL,
  `TargetRegulationID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `FK_RegulationXRegulationAudit_Rev` (`Rev`),
  CONSTRAINT `FK_RegulationXRegulationAudit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulation_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulator` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Regulator_Reg_Ref` varchar(255) DEFAULT NULL,
  `RegulationID` int(11) DEFAULT NULL,
  `RegulatorID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `FK_RegulationXRegulator_Regulation` (`RegulationID`),
  KEY `FK_RegulationXRegulator_Regulator` (`RegulatorID`),
  KEY `FK_RegulationXRegulator_RelationType` (`RelationType`),
  KEY `FK_RegulationXRegulator_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `FK_RegulationXRegulator_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `FK_RegulationXRegulator_Regulation` FOREIGN KEY (`RegulationID`) REFERENCES `regulation` (`ID`),
  CONSTRAINT `FK_RegulationXRegulator_Regulator` FOREIGN KEY (`RegulatorID`) REFERENCES `regulator` (`ID`),
  CONSTRAINT `FK_RegulationXRegulator_RelationType` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_regulator_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulator_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryName` varchar(255) DEFAULT NULL,
  `ReverseName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulator_x_geography` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Regulation_X_Regulator_ID` int(11) DEFAULT NULL,
  `Regulator_X_Geography_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `FK_RegulationXRegulatorXGeography_RegulatorXGeography` (`Regulator_X_Geography_ID`),
  KEY `FK_RegulationXRegulatorXGeography_LastUpdateUser` (`LastUpdate_UserID`),
  KEY `FK_RegulationXRegulatorXGeography_RegulationXRegulator` (`Regulation_X_Regulator_ID`),
  KEY `FK_RegulationXRegulatorXGeography_RelationType` (`RelationType`),
  CONSTRAINT `FK_RegulationXRegulatorXGeography_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `FK_RegulationXRegulatorXGeography_RegulationXRegulator` FOREIGN KEY (`Regulation_X_Regulator_ID`) REFERENCES `regulation_x_regulator` (`ID`),
  CONSTRAINT `FK_RegulationXRegulatorXGeography_RegulatorXGeography` FOREIGN KEY (`Regulator_X_Geography_ID`) REFERENCES `regulator_x_geography` (`ID`),
  CONSTRAINT `FK_RegulationXRegulatorXGeography_RelationType` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_regulator_x_geography_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulator_x_geography_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `ReverseName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulatorytheme` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Regulation_ID` int(11) DEFAULT NULL,
  `RegulatoryTheme_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `FK_RegulationXRegulatoryTheme_Regulation` (`Regulation_ID`),
  KEY `FK_RegulationXRegulatoryTheme_RegulatoryTheme` (`RegulatoryTheme_ID`),
  KEY `FK_RegulationXRegulatoryTheme_RelationType` (`RelationType`),
  KEY `FK_RegulationXRegulatoryTheme_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `FK_RegulationXRegulatoryTheme_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `FK_RegulationXRegulatoryTheme_Regulation` FOREIGN KEY (`Regulation_ID`) REFERENCES `regulation` (`ID`),
  CONSTRAINT `FK_RegulationXRegulatoryTheme_RegulatoryTheme` FOREIGN KEY (`RegulatoryTheme_ID`) REFERENCES `regulatorytheme` (`ID`),
  CONSTRAINT `FK_RegulationXRegulatoryTheme_RelationType` FOREIGN KEY (`RelationType`) REFERENCES `regulation_x_regulatorytheme_relationtype` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulatorytheme_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `Regulation_ID` int(11) DEFAULT NULL,
  `RegulatoryTheme_ID` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `FK_RegulationXRegulatoryThemeAudit_Rev` (`Rev`),
  CONSTRAINT `FK_RegulationXRegulatoryThemeAudit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulation_x_regulatorytheme_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeleteDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RelationType_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RelationType_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulator` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_Regulator_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_Regulator_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulator_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_Regulator_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `regulator` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulator_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `regulator_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `regulator` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=218 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulator_x_geography` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Regulator_ID` int(11) DEFAULT NULL,
  `Geography_ID` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `FK_RegulatorXGeography_LastUpdateUser` (`LastUpdate_UserID`),
  KEY `FK_RegulatorXGeography_Geography` (`Geography_ID`),
  KEY `FK_RegulatorXGeography_Regulator` (`Regulator_ID`),
  CONSTRAINT `FK_RegulatorXGeography_Geography` FOREIGN KEY (`Geography_ID`) REFERENCES `geography` (`ID`),
  CONSTRAINT `FK_RegulatorXGeography_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `FK_RegulatorXGeography_Regulator` FOREIGN KEY (`Regulator_ID`) REFERENCES `regulator` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulator_x_geography_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL,
  `Regulator_ID` int(11) DEFAULT NULL,
  `Geography_ID` int(11) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `FK_RegulatorXGeographyAudit_Rev` (`Rev`),
  CONSTRAINT `FK_RegulatorXGeographyAudit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulatory_theme_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `regulatory_theme_Audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `regulatorytheme` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=227 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulatorytheme` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Status_ID` int(11) DEFAULT NULL,
  `RefNumber` varchar(255) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_RegulatoryTheme_Parent` (`Parent_ID`),
  KEY `fk_RegulatoryTheme_Status` (`Status_ID`),
  KEY `fk_RegulatoryTheme_LastUpdateUser` (`LastUpdate_UserID`),
  CONSTRAINT `fk_RegulatoryTheme_LastUpdateUser` FOREIGN KEY (`LastUpdate_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_RegulatoryTheme_Parent` FOREIGN KEY (`Parent_ID`) REFERENCES `regulatorytheme` (`ID`),
  CONSTRAINT `fk_RegulatoryTheme_Status` FOREIGN KEY (`Status_ID`) REFERENCES `status` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `regulatorytheme_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Parent_ID` int(11) DEFAULT NULL,
  `Status_ID` int(11) DEFAULT NULL,
  `RefNumber` varchar(255) DEFAULT NULL,
  `ShortName` varchar(255) DEFAULT NULL,
  `PrimaryName` varchar(255) DEFAULT NULL,
  `Description` longtext DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdate_UserID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  UNIQUE KEY `Rev` (`Rev`),
  CONSTRAINT `fk_RegulatoryTheme_Audit_Rev` FOREIGN KEY (`ID`) REFERENCES `regulatorytheme` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `requirement` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_UpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  CONSTRAINT `fk_Requirement_LastUpdateUser` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `requirement_ibfk_1` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `requirement_ibfk_2` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `revinfo` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `RevStamp` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `role` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `roletype` int(11) DEFAULT NULL,
  `primaryname` varchar(45) NOT NULL,
  `description` varchar(128) DEFAULT NULL,
  `parent` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `roletype` (`roletype`),
  KEY `parent` (`parent`),
  CONSTRAINT `role_ibfk_1` FOREIGN KEY (`roletype`) REFERENCES `role_type` (`id`),
  CONSTRAINT `role_ibfk_2` FOREIGN KEY (`parent`) REFERENCES `role` (`id`),
  CONSTRAINT `role_ibfk_3` FOREIGN KEY (`roletype`) REFERENCES `role_type` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `role_assignment` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `objectroleid` int(11) DEFAULT NULL,
  `users` text NOT NULL,
  `createdate` datetime NOT NULL,
  `lastupdateddate` datetime NOT NULL,
  `createdby_id` int(11) DEFAULT NULL,
  `lastupdatedby_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `objectroleid` (`objectroleid`),
  KEY `createdby_id` (`createdby_id`),
  KEY `lastupdatedby_id` (`lastupdatedby_id`),
  CONSTRAINT `role_assignment_ibfk_1` FOREIGN KEY (`objectroleid`) REFERENCES `object_role` (`id`),
  CONSTRAINT `role_assignment_ibfk_2` FOREIGN KEY (`createdby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `role_assignment_ibfk_3` FOREIGN KEY (`lastupdatedby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `role_assignment_ibfk_4` FOREIGN KEY (`objectroleid`) REFERENCES `object_role` (`id`),
  CONSTRAINT `role_assignment_ibfk_5` FOREIGN KEY (`createdby_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `role_assignment_ibfk_6` FOREIGN KEY (`lastupdatedby_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=50 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `role_permission` (
  `id` int(11) NOT NULL,
  `permissionclass` varchar(256) NOT NULL,
  `roleclass` varchar(256) NOT NULL,
  `roleid` int(11) NOT NULL,
  `createdatetime` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `lastupdatedatetime` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `roleid` (`roleid`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  CONSTRAINT `role_permission_ibfk_1` FOREIGN KEY (`roleid`) REFERENCES `role` (`id`),
  CONSTRAINT `role_permission_ibfk_2` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `role_permission_ibfk_3` FOREIGN KEY (`roleid`) REFERENCES `role` (`id`),
  CONSTRAINT `role_permission_ibfk_4` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `role_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(128) NOT NULL,
  `description` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `roleaccepted` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Message` varchar(256) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `rule` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Type` varchar(255) DEFAULT NULL,
  `Value` varchar(255) DEFAULT NULL,
  `Field_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Field_ID` (`Field_ID`),
  CONSTRAINT `fk_rule_field` FOREIGN KEY (`Field_ID`) REFERENCES `field` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `security_classification` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updated_userID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Last_updated_userID` (`Last_updated_userID`),
  CONSTRAINT `security_classification_ibfk_1` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`),
  CONSTRAINT `security_classification_ibfk_2` FOREIGN KEY (`Last_updated_userID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Last_UpdatedAt` datetime DEFAULT NULL,
  `Last_UpdatedBy` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Status` (`Status`),
  KEY `CreatedBy` (`CreatedBy`),
  KEY `Last_UpdatedBy` (`Last_UpdatedBy`),
  CONSTRAINT `fk_segment_createdby` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_segment_lastupdatedby` FOREIGN KEY (`Last_UpdatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_segment_status` FOREIGN KEY (`Status`) REFERENCES `segment_status` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Last_UpdatedAt` datetime DEFAULT NULL,
  `Last_UpdatedBy` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `Rev` (`Rev`),
  KEY `CreatedBy` (`CreatedBy`),
  KEY `Last_UpdatedBy` (`Last_UpdatedBy`),
  KEY `Status` (`Status`),
  CONSTRAINT `fk_seg_audit_createdby` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_seg_audit_lastupdatedby` FOREIGN KEY (`Last_UpdatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_seg_audit_main` FOREIGN KEY (`ID`) REFERENCES `segment` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_seg_audit_revinfo` FOREIGN KEY (`Rev`) REFERENCES `revinfo` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_seg_audit_status` FOREIGN KEY (`Status`) REFERENCES `segment_status` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_object_type` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Type` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_object_type_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Type` varchar(128) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `fk_sota_main` FOREIGN KEY (`ID`) REFERENCES `segment_object_type` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sota_revinfo_rev` FOREIGN KEY (`Rev`) REFERENCES `revinfo` (`ID`) ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_status` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `Label` varchar(128) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_status_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(255) DEFAULT NULL,
  `Label` varchar(128) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`,`Rev`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `fk_segstatus_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revinfo` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_segstatus_main` FOREIGN KEY (`ID`) REFERENCES `segment_status` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_x_identity` (
  `Segment_ID` int(11) NOT NULL,
  `Object_Ref_ID` int(11) NOT NULL,
  `Role` varchar(10) DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Last_Updated_By` int(11) DEFAULT NULL,
  `Last_Updated_At` datetime DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  `Origin_ID` int(11) NOT NULL,
  PRIMARY KEY (`Segment_ID`,`Object_Ref_ID`,`Origin_ID`),
  KEY `CreatedBy` (`CreatedBy`),
  KEY `Last_Updated_By` (`Last_Updated_By`),
  KEY `fk_sxi_objref` (`Object_Ref_ID`),
  KEY `fk_sxi_origin` (`Origin_ID`),
  CONSTRAINT `fk_sxi_createdby` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_lastupdatedby` FOREIGN KEY (`Last_Updated_By`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_objref` FOREIGN KEY (`Object_Ref_ID`) REFERENCES `object_reference` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_origin` FOREIGN KEY (`Origin_ID`) REFERENCES `association_origin` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_segment` FOREIGN KEY (`Segment_ID`) REFERENCES `segment` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_x_identity_audit` (
  `Segment_ID` int(11) NOT NULL,
  `Object_Ref_ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Role` varchar(10) DEFAULT NULL,
  `CreatedBy` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Last_Updated_By` int(11) DEFAULT NULL,
  `Last_Updated_At` datetime DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  `Origin_ID` int(11) NOT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`Segment_ID`,`Object_Ref_ID`,`Rev`),
  KEY `Rev` (`Rev`),
  KEY `CreatedBy` (`CreatedBy`),
  KEY `Last_Updated_By` (`Last_Updated_By`),
  KEY `fk_sxi_audit_origin` (`Origin_ID`),
  KEY `fk_sxi_audit_objref` (`Object_Ref_ID`),
  CONSTRAINT `fk_sxi_audit_createdby` FOREIGN KEY (`CreatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_audit_lastupdatedby` FOREIGN KEY (`Last_Updated_By`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_audit_objref` FOREIGN KEY (`Object_Ref_ID`) REFERENCES `object_reference` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_audit_origin` FOREIGN KEY (`Origin_ID`) REFERENCES `association_origin` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revinfo` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_sxi_audit_segment` FOREIGN KEY (`Segment_ID`) REFERENCES `segment` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_x_resource` (
  `Segment_ID` int(11) NOT NULL,
  `Object_Reference_ID` int(11) NOT NULL,
  `Created_By` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Last_UpdatedBy` int(11) DEFAULT NULL,
  `Last_Updated_At` datetime DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  PRIMARY KEY (`Segment_ID`,`Object_Reference_ID`),
  KEY `Created_By` (`Created_By`),
  KEY `Last_UpdatedBy` (`Last_UpdatedBy`),
  KEY `fk_sxr_objref` (`Object_Reference_ID`),
  CONSTRAINT `fk_sxr_createdby` FOREIGN KEY (`Created_By`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_lastupdatedby` FOREIGN KEY (`Last_UpdatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_objref` FOREIGN KEY (`Object_Reference_ID`) REFERENCES `object_reference` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_segment` FOREIGN KEY (`Segment_ID`) REFERENCES `segment` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `segment_x_resource_audit` (
  `Segment_ID` int(11) NOT NULL,
  `Object_Reference_ID` int(11) NOT NULL,
  `Rev` int(11) NOT NULL AUTO_INCREMENT,
  `Created_By` int(11) DEFAULT NULL,
  `Created_At` datetime DEFAULT NULL,
  `Last_UpdatedBy` int(11) DEFAULT NULL,
  `Last_Updated_At` datetime DEFAULT NULL,
  `Deleted_At` datetime DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`Segment_ID`,`Object_Reference_ID`,`Rev`),
  KEY `Rev` (`Rev`),
  KEY `Created_By` (`Created_By`),
  KEY `Last_UpdatedBy` (`Last_UpdatedBy`),
  KEY `fk_sxr_audit_objref` (`Object_Reference_ID`),
  CONSTRAINT `fk_sxr_audit_createdby` FOREIGN KEY (`Created_By`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_audit_lastupdatedby` FOREIGN KEY (`Last_UpdatedBy`) REFERENCES `people` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_audit_objref` FOREIGN KEY (`Object_Reference_ID`) REFERENCES `object_reference` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_audit_rev` FOREIGN KEY (`Rev`) REFERENCES `revinfo` (`ID`) ON UPDATE CASCADE,
  CONSTRAINT `fk_sxr_audit_segment` FOREIGN KEY (`Segment_ID`) REFERENCES `segment` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `static_pages` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `link` varchar(255) NOT NULL,
  `context` varchar(50) DEFAULT NULL,
  `is_homepage` tinyint(1) DEFAULT 0,
  `content` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `status` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `primaryname` varchar(90) DEFAULT NULL,
  `description` varchar(128) DEFAULT NULL,
  `lastupdatedatetime` datetime DEFAULT NULL,
  `deletedate` datetime DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `lastupdateuser_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `lastupdateuser_id` (`lastupdateuser_id`),
  CONSTRAINT `status_ibfk_1` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`),
  CONSTRAINT `status_ibfk_2` FOREIGN KEY (`lastupdateuser_id`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `is_Public` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Type` int(11) DEFAULT NULL,
  `Classification` int(11) DEFAULT NULL,
  `Confidentiality_Rating` int(11) DEFAULT NULL,
  `Integrity_Rating` int(11) DEFAULT NULL,
  `Availability_Rating` int(11) DEFAULT NULL,
  `Name` varchar(256) DEFAULT NULL,
  `Long_Name` varchar(256) DEFAULT NULL,
  `AssetID` varchar(45) DEFAULT NULL,
  `External` tinyint(1) DEFAULT NULL,
  `Description` varchar(300) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_Updated_Datetime` datetime DEFAULT NULL,
  `Deleted_datetime` datetime DEFAULT NULL,
  `URL` varchar(256) DEFAULT NULL,
  `DQ_Automation` tinyint(1) DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  `under_revision` tinyint(1) NOT NULL DEFAULT 0,
  `active_cr_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `parent_id` (`parent_id`),
  KEY `Confidentiality_Rating` (`Confidentiality_Rating`),
  KEY `Integrity_Rating` (`Integrity_Rating`),
  KEY `Availability_Rating` (`Availability_Rating`),
  KEY `CreatedBy_ID` (`CreatedBy_ID`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  KEY `status` (`status`),
  KEY `Lifecycle` (`Lifecycle`),
  KEY `Classification` (`Classification`),
  KEY `Type` (`Type`),
  KEY `is_Public` (`is_Public`),
  KEY `idx_system_revision` (`under_revision`,`active_cr_id`),
  CONSTRAINT `system_ibfk_1` FOREIGN KEY (`parent_id`) REFERENCES `system` (`id`),
  CONSTRAINT `system_ibfk_10` FOREIGN KEY (`Type`) REFERENCES `system_type` (`id`),
  CONSTRAINT `system_ibfk_11` FOREIGN KEY (`is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `system_ibfk_12` FOREIGN KEY (`parent_id`) REFERENCES `system` (`id`),
  CONSTRAINT `system_ibfk_13` FOREIGN KEY (`Confidentiality_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `system_ibfk_14` FOREIGN KEY (`Integrity_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `system_ibfk_15` FOREIGN KEY (`Availability_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `system_ibfk_16` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_ibfk_17` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_ibfk_18` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `system_ibfk_19` FOREIGN KEY (`Lifecycle`) REFERENCES `system_lifecycle` (`id`),
  CONSTRAINT `system_ibfk_2` FOREIGN KEY (`Confidentiality_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `system_ibfk_20` FOREIGN KEY (`Classification`) REFERENCES `system_classification` (`id`),
  CONSTRAINT `system_ibfk_21` FOREIGN KEY (`Type`) REFERENCES `system_type` (`id`),
  CONSTRAINT `system_ibfk_22` FOREIGN KEY (`is_Public`) REFERENCES `viewing` (`id`),
  CONSTRAINT `system_ibfk_3` FOREIGN KEY (`Integrity_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `system_ibfk_4` FOREIGN KEY (`Availability_Rating`) REFERENCES `cia_rating` (`id`),
  CONSTRAINT `system_ibfk_5` FOREIGN KEY (`CreatedBy_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_ibfk_6` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_ibfk_7` FOREIGN KEY (`status`) REFERENCES `status` (`ID`),
  CONSTRAINT `system_ibfk_8` FOREIGN KEY (`Lifecycle`) REFERENCES `system_lifecycle` (`id`),
  CONSTRAINT `system_ibfk_9` FOREIGN KEY (`Classification`) REFERENCES `system_classification` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_audit` (
  `id` int(11) NOT NULL,
  `rev_id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `is_Public` int(11) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `Lifecycle` int(11) DEFAULT NULL,
  `Type` int(11) DEFAULT NULL,
  `Classification` int(11) DEFAULT NULL,
  `Confidentiality_Rating` int(11) DEFAULT NULL,
  `Integrity_Rating` int(11) DEFAULT NULL,
  `Availability_Rating` int(11) DEFAULT NULL,
  `Name` varchar(256) DEFAULT NULL,
  `Long_Name` varchar(256) DEFAULT NULL,
  `AssetID` varchar(45) DEFAULT NULL,
  `External` tinyint(1) DEFAULT NULL,
  `Descritpion` varchar(300) DEFAULT NULL,
  `Created_Datetime` datetime DEFAULT NULL,
  `Last_Updated_Datetime` datetime DEFAULT NULL,
  `Deleted_datetime` datetime DEFAULT NULL,
  `URL` varchar(256) DEFAULT NULL,
  `DQ_Automation` tinyint(1) DEFAULT NULL,
  `CreatedBy_ID` int(11) DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  `rev_type` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`id`,`rev_id`),
  UNIQUE KEY `rev_id` (`rev_id`),
  CONSTRAINT `system_audit_ibfk_1` FOREIGN KEY (`id`) REFERENCES `system` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=64 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_audit_history` (
  `id` int(11) NOT NULL,
  `auditidpk` int(11) NOT NULL AUTO_INCREMENT,
  `object` varchar(255) DEFAULT NULL,
  `event` varchar(255) DEFAULT NULL,
  `updateType` varchar(255) DEFAULT NULL,
  `field` varchar(255) DEFAULT NULL,
  `from` varchar(255) DEFAULT NULL,
  `to` varchar(255) DEFAULT NULL,
  `author` varchar(255) DEFAULT NULL,
  `date` datetime DEFAULT current_timestamp(),
  `lastChange` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`id`,`auditidpk`),
  UNIQUE KEY `auditidpk` (`auditidpk`),
  CONSTRAINT `system_audit_history_ibfk_1` FOREIGN KEY (`id`) REFERENCES `system` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=321 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_changes` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `change_request_id` int(11) NOT NULL,
  `object_id` int(11) NOT NULL,
  `nobject_id` int(11) NOT NULL,
  `area_key` varchar(100) NOT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_system_changes_cr_obj_area` (`change_request_id`,`object_id`,`area_key`),
  KEY `idx_system_changes_obj` (`object_id`),
  KEY `idx_system_changes_cr` (`change_request_id`),
  CONSTRAINT `fk_system_changes_cr` FOREIGN KEY (`change_request_id`) REFERENCES `changerequest` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_system_changes_object` FOREIGN KEY (`object_id`) REFERENCES `system` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_classification` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `parent_id` int(11) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `system_classification_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_classification_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_follow` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `System_id` int(11) DEFAULT NULL,
  `follow_id` int(10) unsigned NOT NULL,
  `Created_datetime` datetime DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Last_updatedUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `System_id` (`System_id`),
  KEY `Follow_id` (`follow_id`),
  KEY `Last_updatedUser_ID` (`Last_updatedUser_ID`),
  KEY `idx_system_follow_follow_id` (`follow_id`),
  CONSTRAINT `system_follow_ibfk_1` FOREIGN KEY (`System_id`) REFERENCES `system` (`id`),
  CONSTRAINT `system_follow_ibfk_2` FOREIGN KEY (`follow_id`) REFERENCES `follow` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `system_follow_ibfk_3` FOREIGN KEY (`Last_updatedUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_follow_ibfk_4` FOREIGN KEY (`System_id`) REFERENCES `system` (`id`),
  CONSTRAINT `system_follow_ibfk_6` FOREIGN KEY (`Last_updatedUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_lifecycle` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Name` varchar(45) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `system_lifecycle_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_lifecycle_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_settings` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `setting_group` varchar(50) NOT NULL COMMENT 'Settings group: Environment, General, etc.',
  `setting_key` varchar(100) NOT NULL COMMENT 'Setting key identifier',
  `setting_value` varchar(255) DEFAULT NULL COMMENT 'Setting value as string',
  `data_type` varchar(20) NOT NULL DEFAULT 'string' COMMENT 'Data type: int, string, boolean',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_setting_group_key` (`setting_group`,`setting_key`),
  KEY `idx_setting_group` (`setting_group`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='System-wide configuration settings organized by groups';

INSERT INTO `system_settings` (`setting_group`, `setting_key`, `setting_value`, `data_type`) VALUES 
('Environment', 'clear_notifications_days', '0', 'int'),
('JWT Settings', 'jwt_validity_seconds', '86400', 'int'),
('JWT Settings', 'refresh_validity_seconds', '2592000', 'int');



CREATE TABLE `system_type` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Name` varchar(128) DEFAULT NULL,
  `Description` varchar(256) DEFAULT NULL,
  `Last_updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_updated_UserID` (`Last_updated_UserID`),
  CONSTRAINT `system_type_ibfk_1` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_type_ibfk_2` FOREIGN KEY (`Last_updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_x_legal` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `System_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_SXL_System` (`System_ID`),
  KEY `fk_SXL_Legal` (`Legal_ID`),
  KEY `fk_SXL_Status` (`Status`),
  KEY `fk_SXL_RelType` (`RelationType`),
  KEY `fk_SXL_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_SXL_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `fk_SXL_Legal` FOREIGN KEY (`Legal_ID`) REFERENCES `legal` (`ID`),
  CONSTRAINT `fk_SXL_RelType` FOREIGN KEY (`RelationType`) REFERENCES `system_x_legal_relationtype` (`ID`),
  CONSTRAINT `fk_SXL_Status` FOREIGN KEY (`Status`) REFERENCES `status` (`ID`),
  CONSTRAINT `fk_SXL_System` FOREIGN KEY (`System_ID`) REFERENCES `system` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_x_legal_audit` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Rev` int(11) DEFAULT NULL,
  `Product_ID` int(11) DEFAULT NULL,
  `Legal_ID` int(11) DEFAULT NULL,
  `Status` int(11) DEFAULT NULL,
  `RelationType` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_SXLAudit_Rev` (`Rev`),
  CONSTRAINT `fk_SXLAudit_Rev` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_x_legal_relationtype` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `PrimaryName` varchar(90) DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Priority` int(11) DEFAULT NULL,
  `ReverseName` varchar(128) DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `DeletedDatetime` datetime DEFAULT NULL,
  `LastUpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `fk_SXLRelType_LastUpdateUser` (`LastUpdateUser_ID`),
  CONSTRAINT `fk_SXLRelType_LastUpdateUser` FOREIGN KEY (`LastUpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_x_objectxpeople` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `SystemID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT current_timestamp(),
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Object_x_ipid` (`Object_x_ipid`),
  KEY `Last_UpdateUser_ID` (`Last_UpdateUser_ID`),
  KEY `SystemID` (`SystemID`),
  CONSTRAINT `SystemID` FOREIGN KEY (`SystemID`) REFERENCES `system` (`id`) ON DELETE CASCADE,
  CONSTRAINT `system_x_objectxpeople_ibfk_1` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `system_x_objectxpeople_ibfk_2` FOREIGN KEY (`SystemID`) REFERENCES `system` (`id`),
  CONSTRAINT `system_x_objectxpeople_ibfk_3` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`),
  CONSTRAINT `system_x_objectxpeople_ibfk_4` FOREIGN KEY (`Object_x_ipid`) REFERENCES `object_x_people` (`id`),
  CONSTRAINT `system_x_objectxpeople_ibfk_5` FOREIGN KEY (`SystemID`) REFERENCES `system` (`id`),
  CONSTRAINT `system_x_objectxpeople_ibfk_6` FOREIGN KEY (`Last_UpdateUser_ID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `system_x_objectxpeople_audit` (
  `ID` int(11) NOT NULL,
  `Rev` int(11) DEFAULT NULL,
  `Object_x_ipid` int(11) DEFAULT NULL,
  `SystemID` int(11) DEFAULT NULL,
  `CreateDatetime` datetime DEFAULT NULL,
  `LastUpdateDatetime` datetime DEFAULT NULL,
  `Last_UpdateUser_ID` int(11) DEFAULT NULL,
  `RevType` varchar(10) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `Rev` (`Rev`),
  CONSTRAINT `system_x_objectxpeople_audit_ibfk_1` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`),
  CONSTRAINT `system_x_objectxpeople_audit_ibfk_2` FOREIGN KEY (`Rev`) REFERENCES `revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `unison` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `facet_version` varchar(256) DEFAULT NULL,
  `user_reference` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_unison_user` (`user_reference`),
  CONSTRAINT `fk_unison_user` FOREIGN KEY (`user_reference`) REFERENCES `i_user` (`reference`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `unison_facets` (
  `facetId` varchar(256) NOT NULL,
  `active` tinyint(1) DEFAULT NULL,
  `active_fields` text DEFAULT NULL,
  `ordering` int(11) DEFAULT NULL,
  `unison_id` int(11) NOT NULL,
  PRIMARY KEY (`unison_id`,`facetId`),
  KEY `fk_unison_facets_unison` (`unison_id`),
  CONSTRAINT `fk_unison_facets_unison` FOREIGN KEY (`unison_id`) REFERENCES `unison` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `user_measurements` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20) NOT NULL,
  `map_id` bigint(20) DEFAULT NULL,
  `measurement_type` varchar(50) DEFAULT NULL,
  `value` decimal(15,2) DEFAULT NULL,
  `unit` varchar(20) DEFAULT NULL,
  `coordinates_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`coordinates_json`)),
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_measurements_user_id` (`user_id`),
  KEY `idx_measurements_map_id` (`map_id`),
  CONSTRAINT `user_measurements_ibfk_1` FOREIGN KEY (`map_id`) REFERENCES `maps` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `user_search` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `condition_definition` text DEFAULT NULL,
  `description` varchar(256) DEFAULT NULL,
  `name` varchar(256) DEFAULT NULL,
  `user_reference` int(11) DEFAULT NULL,
  `hitcount` int(11) DEFAULT 0,
  `is_public` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `fk_user_search_user` (`user_reference`),
  KEY `idx_user_search_id` (`id`),
  CONSTRAINT `fk_user_search_user` FOREIGN KEY (`user_reference`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `user_search_usage` (
  `user_reference` int(11) DEFAULT NULL,
  `search_reference` int(11) DEFAULT NULL,
  `visited_at` datetime NOT NULL DEFAULT current_timestamp(),
  KEY `fk_user_search_usage_user` (`user_reference`),
  KEY `fk_user_search_usage_search` (`search_reference`),
  CONSTRAINT `fk_user_search_usage_search` FOREIGN KEY (`search_reference`) REFERENCES `user_search` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_search_usage_user` FOREIGN KEY (`user_reference`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `user_segment_selection` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `user_id` int(11) NOT NULL,
  `segment_id` int(11) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_user_segment` (`user_id`,`segment_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `segment_id` (`segment_id`),
  CONSTRAINT `user_segment_selection_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `people` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `user_segment_selection_ibfk_2` FOREIGN KEY (`segment_id`) REFERENCES `segment` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `user_x_search` (
  `search_id` int(11) DEFAULT NULL,
  `user_reference` int(11) DEFAULT NULL,
  KEY `fk_user_x_search_search` (`search_id`),
  KEY `fk_user_x_search_user` (`user_reference`),
  CONSTRAINT `fk_user_x_search_search` FOREIGN KEY (`search_id`) REFERENCES `user_search` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_user_x_search_user` FOREIGN KEY (`user_reference`) REFERENCES `people` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @saved_cs_client     = @@character_set_client;
SET character_set_client = utf8;
/*!50001 CREATE VIEW `v_user_accessible_segments` AS SELECT
 1 AS `user_id`,
  1 AS `segment_id`,
  1 AS `segment_name`,
  1 AS `segment_description` */;
SET character_set_client = @saved_cs_client;


CREATE TABLE `values_audit` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `dataset_id` int(11) NOT NULL,
  `action_type` varchar(50) NOT NULL,
  `action_timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `user_id` int(11) DEFAULT NULL,
  `details` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `values_datastore` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `dataset_id` int(11) NOT NULL,
  `frequency` varchar(50) DEFAULT NULL,
  `frequency_comments` text DEFAULT NULL,
  `availability` varchar(100) DEFAULT NULL,
  `availability_comments` text DEFAULT NULL,
  `values_in_axon` tinyint(1) DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `dataset_id` (`dataset_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `values_entry` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `entry_ref_id` int(11) NOT NULL,
  `field_id` int(11) NOT NULL,
  `row_index` int(11) DEFAULT NULL,
  `value_text` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=66 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `values_entry_ref` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `datastore_id` int(11) NOT NULL,
  `upload_timestamp` timestamp NOT NULL DEFAULT current_timestamp(),
  `user_id` int(11) DEFAULT NULL,
  `row_count` int(11) DEFAULT NULL,
  `upload_type` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `values_field` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `datastore_id` int(11) NOT NULL,
  `column_name` varchar(255) NOT NULL,
  `data_type` varchar(50) DEFAULT NULL,
  `dataset_attribute_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `values_rule` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `field_id` int(11) NOT NULL,
  `rule_type` varchar(50) NOT NULL,
  `rule_value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `valuesjob_dataset` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `JobID` int(11) DEFAULT NULL,
  `Dataset_ID` int(11) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `JobID` (`JobID`),
  KEY `Dataset_ID` (`Dataset_ID`),
  CONSTRAINT `fk_vjd_dataset` FOREIGN KEY (`Dataset_ID`) REFERENCES `dataset` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_vjd_job` FOREIGN KEY (`JobID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `valuesjob_meta_attribute` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `JobID` int(11) DEFAULT NULL,
  `Meta_Attribute_Name` varchar(255) DEFAULT NULL,
  `Meta_Attribute_Value` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `JobID` (`JobID`),
  CONSTRAINT `fk_vjm_job` FOREIGN KEY (`JobID`) REFERENCES `job` (`ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `viewing` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `Name` varchar(45) DEFAULT NULL,
  `Last_updated_datetime` datetime DEFAULT NULL,
  `Description` varchar(128) DEFAULT NULL,
  `Last_Updated_UserID` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `Last_Updated_UserID` (`Last_Updated_UserID`),
  CONSTRAINT `viewing_ibfk_1` FOREIGN KEY (`Last_Updated_UserID`) REFERENCES `people` (`ID`),
  CONSTRAINT `viewing_ibfk_2` FOREIGN KEY (`Last_Updated_UserID`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `workflow_instance` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Process_Definition_ID` int(11) NOT NULL,
  `ChangeRequest_ID` int(11) DEFAULT NULL,
  `Current_Bpmn_Node_Id` varchar(255) DEFAULT NULL,
  `Status` enum('Enabled','Disabled','Paused','Completed') DEFAULT 'Enabled',
  `Started_At` datetime DEFAULT current_timestamp(),
  `Ended_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_process_def` (`Process_Definition_ID`),
  KEY `idx_cr` (`ChangeRequest_ID`),
  KEY `idx_status` (`Status`),
  KEY `idx_workflow_instance_change_request` (`ChangeRequest_ID`),
  CONSTRAINT `fk_instance_process` FOREIGN KEY (`Process_Definition_ID`) REFERENCES `process_definition` (`ID`),
  CONSTRAINT `fk_workflow_instance_change_request` FOREIGN KEY (`ChangeRequest_ID`) REFERENCES `changerequest` (`ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=21 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `workflow_instance_task` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Workflow_Instance_ID` int(11) NOT NULL,
  `Bpmn_Node_Id` varchar(255) NOT NULL,
  `Parent_Gateway_ID` varchar(255) DEFAULT NULL,
  `Name` varchar(255) DEFAULT NULL,
  `Role_Name` varchar(100) DEFAULT NULL,
  `Status` enum('Pending','Completed','Rejected','InProgress') DEFAULT 'Pending',
  `Assigned_At` datetime DEFAULT current_timestamp(),
  `Assigned_To` int(11) DEFAULT NULL,
  `Due_Date` datetime DEFAULT NULL,
  `Started_At` datetime DEFAULT NULL,
  `Completed_At` datetime DEFAULT NULL,
  `Completed_By` int(11) DEFAULT NULL,
  `Decision` varchar(50) DEFAULT NULL,
  `Due_At` datetime DEFAULT NULL,
  `Is_Overdue` tinyint(1) DEFAULT 0,
  `Escalated_At` datetime DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `idx_instance` (`Workflow_Instance_ID`),
  KEY `idx_status` (`Status`),
  KEY `idx_assigned` (`Assigned_To`),
  KEY `idx_sla_evaluation` (`Status`,`Due_At`,`Is_Overdue`,`Escalated_At`),
  KEY `idx_parent_gateway` (`Parent_Gateway_ID`,`Workflow_Instance_ID`),
  CONSTRAINT `fk_task_instance` FOREIGN KEY (`Workflow_Instance_ID`) REFERENCES `workflow_instance` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `workflow_instance_variable` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Workflow_Instance_ID` int(11) NOT NULL,
  `Variable_Name` varchar(255) NOT NULL,
  `Variable_Value` text DEFAULT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  `Updated_At` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`ID`),
  KEY `idx_instance` (`Workflow_Instance_ID`),
  KEY `idx_var_name` (`Variable_Name`),
  CONSTRAINT `fk_variable_instance` FOREIGN KEY (`Workflow_Instance_ID`) REFERENCES `workflow_instance` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `workflow_notification` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `notification_rule_id` bigint(20) DEFAULT NULL COMMENT 'FK to workflow_notification_rule',
  `workflow_task_id` int(11) DEFAULT NULL COMMENT 'FK to workflow_instance_task (NULL for role notifications)',
  `change_request_id` int(11) DEFAULT NULL COMMENT 'FK to changerequest',
  `recipient_user_id` int(11) NOT NULL COMMENT 'Target user ID',
  `event_type` varchar(50) NOT NULL,
  `title` varchar(255) NOT NULL,
  `message` text NOT NULL,
  `channel` varchar(50) NOT NULL COMMENT 'Channel: ui, email, sms',
  `category` varchar(50) NOT NULL DEFAULT 'workflow',
  `email_sent` tinyint(1) DEFAULT 0,
  `email_sent_at` datetime DEFAULT NULL,
  `read` tinyint(1) DEFAULT 0,
  `created_at` datetime DEFAULT current_timestamp(),
  `object_id` int(11) DEFAULT NULL COMMENT 'ID of the object for role notifications (dataset, system, etc.)',
  `facet_type` varchar(50) DEFAULT NULL COMMENT 'Type of facet for role notifications (e.g., "Data Set", "System", "Glossary")',
  PRIMARY KEY (`id`),
  KEY `idx_recipient_unread` (`recipient_user_id`,`read`,`created_at`),
  KEY `idx_task` (`workflow_task_id`),
  KEY `idx_change_request` (`change_request_id`),
  KEY `fk_notification_rule` (`notification_rule_id`),
  KEY `idx_category_recipient_unread` (`category`,`recipient_user_id`,`read`,`created_at`),
  KEY `idx_workflow_notification_object_facet` (`object_id`,`facet_type`),
  CONSTRAINT `fk_notification_rule` FOREIGN KEY (`notification_rule_id`) REFERENCES `workflow_notification_rule` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_notification_task` FOREIGN KEY (`workflow_task_id`) REFERENCES `workflow_instance_task` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=48 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `workflow_notification_rule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `module` varchar(255) NOT NULL COMMENT 'Workflow name (Process Definition PrimaryName) or "*" for all workflows',
  `event_type` varchar(50) NOT NULL,
  `recipient_role` varchar(100) DEFAULT NULL COMMENT 'Target role name',
  `recipient_user_id` bigint(20) DEFAULT NULL COMMENT 'Specific user ID (optional)',
  `channels` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL COMMENT 'Notification channels: ["email","ui","sms"]' CHECK (json_valid(`channels`)),
  `delivery_mode` enum('IMMEDIATE','BATCHED') DEFAULT 'IMMEDIATE',
  `active` tinyint(1) DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `idx_module_event` (`module`,`event_type`,`active`),
  KEY `idx_recipient` (`recipient_role`,`recipient_user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;



CREATE TABLE `workflow_task_comment` (
  `ID` int(11) NOT NULL AUTO_INCREMENT,
  `Workflow_Task_ID` int(11) NOT NULL,
  `Comment_Text` text NOT NULL,
  `Created_By` int(11) NOT NULL,
  `Created_At` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`ID`),
  KEY `idx_task` (`Workflow_Task_ID`),
  KEY `idx_created_by` (`Created_By`),
  CONSTRAINT `fk_task_comment` FOREIGN KEY (`Workflow_Task_ID`) REFERENCES `workflow_instance_task` (`ID`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

/*!50001 DROP VIEW IF EXISTS `v_user_accessible_segments`*/;






/*!50001 CREATE ALGORITHM=UNDEFINED */
/*!50013 DEFINER=`root`@`localhost` SQL SECURITY DEFINER */




