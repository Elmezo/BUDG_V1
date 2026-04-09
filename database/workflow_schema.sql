-- ============================================
-- Workflow Management System - Full Schema
-- Includes sample data for testing
-- ============================================

-- 2️⃣ Change Request Entities
CREATE TABLE IF NOT EXISTS changerequest_entity (
    ID int(11) NOT NULL AUTO_INCREMENT,
    PrimaryName varchar(255) DEFAULT NULL,
    Status enum('Enabled','Disabled') DEFAULT 'Enabled',
    LastUserChange int(11) DEFAULT NULL,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    Updated_At datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    KEY idx_lastuser (LastUserChange),
    CONSTRAINT fk_cre_ent_lastuser FOREIGN KEY (LastUserChange) REFERENCES people(ID) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Sample entities
INSERT INTO changerequest_entity (PrimaryName, Status, LastUserChange) VALUES
('Entity A', 'Enabled', 1),
('Entity B', 'Enabled', 2);

-- 3️⃣ Process Definitions
CREATE TABLE IF NOT EXISTS process_definition (
    ID int(11) NOT NULL AUTO_INCREMENT,
    PrimaryName varchar(255) DEFAULT NULL,
    Reference varchar(255) DEFAULT NULL,
    Is_Default tinyint(1) DEFAULT 0,
    Description mediumtext DEFAULT NULL,
    Status enum('Enabled','Disabled') DEFAULT 'Enabled',
    LastUserChange int(11) DEFAULT NULL,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    Updated_At datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    Entity_ID int(11) DEFAULT NULL,
    PRIMARY KEY (ID),
    KEY idx_entity (Entity_ID),
    KEY idx_lastuser (LastUserChange),
    KEY idx_status (Status),
    CONSTRAINT fk_procdef_lastuser FOREIGN KEY (LastUserChange) REFERENCES people(ID) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_procdef_entity FOREIGN KEY (Entity_ID) REFERENCES changerequest_entity(ID) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Sample process definitions
INSERT INTO process_definition (PrimaryName, Reference, Is_Default, Description, Status, LastUserChange, Entity_ID) VALUES
('Workflow 1', 'WF1', 1, 'Sample workflow 1', 'Enabled', 1, 1),
('Workflow 2', 'WF2', 0, 'Sample workflow 2', 'Enabled', 2, 2);

-- 4️⃣ Change Request Types
CREATE TABLE IF NOT EXISTS changerequest_type (
    ID int(11) NOT NULL AUTO_INCREMENT,
    PrimaryName varchar(255) DEFAULT NULL,
    Description mediumtext DEFAULT NULL,
    Status enum('Enabled','Disabled') DEFAULT 'Enabled',
    LastUserChange int(11) DEFAULT NULL,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    Updated_At datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    KEY idx_lastuser (LastUserChange),
    CONSTRAINT fk_crtype_lastuser FOREIGN KEY (LastUserChange) REFERENCES people(ID) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Sample CR Types
INSERT INTO changerequest_type (PrimaryName) VALUES ('Create'), ('Edit'), ('Delete'), ('Archive');

-- 5️⃣ Workflow to CR Type Mapping
CREATE TABLE IF NOT EXISTS changerequest_workflow_crtype (
    ID int(11) NOT NULL AUTO_INCREMENT,
    Process_Definition_ID int(11) NOT NULL,
    CR_Type varchar(255) NOT NULL,
    Entity_ID int(11) DEFAULT NULL,
    LastUserChange int(11) DEFAULT NULL,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    Updated_At datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    KEY idx_process_def (Process_Definition_ID),
    KEY idx_entity (Entity_ID),
    CONSTRAINT fk_workflow_mapping_process FOREIGN KEY (Process_Definition_ID) REFERENCES process_definition(ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 6️⃣ Workflow Instances
CREATE TABLE IF NOT EXISTS workflow_instance (
    ID int(11) NOT NULL AUTO_INCREMENT,
    Process_Definition_ID int(11) NOT NULL,
    ChangeRequest_ID int(11) DEFAULT NULL,
    Current_Bpmn_Node_Id varchar(255) DEFAULT NULL,
    Status enum('Enabled','Disabled','Paused','Completed') DEFAULT 'Enabled',
    Started_At datetime DEFAULT CURRENT_TIMESTAMP,
    Ended_At datetime DEFAULT NULL,
    PRIMARY KEY (ID),
    KEY idx_process_def (Process_Definition_ID),
    KEY idx_cr (ChangeRequest_ID),
    KEY idx_status (Status),
    CONSTRAINT fk_instance_process FOREIGN KEY (Process_Definition_ID) REFERENCES process_definition(ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Phase 3 Migration: Add 'Completed' status if table already exists
-- ALTER TABLE workflow_instance 
-- MODIFY COLUMN Status enum('Enabled','Disabled','Paused','Completed') DEFAULT 'Enabled';

-- 7️⃣ Workflow Instance Tasks
CREATE TABLE IF NOT EXISTS workflow_instance_task (
    ID int(11) NOT NULL AUTO_INCREMENT,
    Workflow_Instance_ID int(11) NOT NULL,
    Bpmn_Node_Id varchar(255) NOT NULL,
    Name varchar(255) DEFAULT NULL,
    Status enum('Pending','Completed','Rejected','InProgress') DEFAULT 'Pending',
    Assigned_To int(11) DEFAULT NULL,
    Due_Date datetime DEFAULT NULL,
    Started_At datetime DEFAULT NULL,
    Completed_At datetime DEFAULT NULL,
    PRIMARY KEY (ID),
    KEY idx_instance (Workflow_Instance_ID),
    KEY idx_status (Status),
    KEY idx_assigned (Assigned_To),
    CONSTRAINT fk_task_instance FOREIGN KEY (Workflow_Instance_ID) REFERENCES workflow_instance(ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Phase 2 Migration: Add new columns for role-based task assignment
-- Run this migration if the table already exists:
-- ALTER TABLE workflow_instance_task 
-- ADD COLUMN IF NOT EXISTS Role_Name varchar(100) AFTER Name,
-- ADD COLUMN IF NOT EXISTS Assigned_At datetime DEFAULT CURRENT_TIMESTAMP AFTER Status,
-- ADD COLUMN IF NOT EXISTS Completed_By int DEFAULT NULL AFTER Completed_At,
-- ADD COLUMN IF NOT EXISTS Decision varchar(50) DEFAULT NULL AFTER Completed_By;

-- 7️⃣ Workflow Task Comments (Phase 3)
CREATE TABLE IF NOT EXISTS workflow_task_comment (
    ID int(11) NOT NULL AUTO_INCREMENT,
    Workflow_Task_ID int(11) NOT NULL,
    Comment_Text text NOT NULL,
    Created_By int(11) NOT NULL,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    KEY idx_task (Workflow_Task_ID),
    KEY idx_created_by (Created_By),
    CONSTRAINT fk_task_comment 
        FOREIGN KEY (Workflow_Task_ID) 
        REFERENCES workflow_instance_task(ID) 
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 8️⃣ Process Definition BPMN
CREATE TABLE IF NOT EXISTS process_definition_bpmn (
    ID int(11) NOT NULL AUTO_INCREMENT,
    Process_Definition_ID int(11) NOT NULL,
    Xml_Content LONGTEXT,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    Updated_At datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    UNIQUE KEY uk_process_def (Process_Definition_ID),
    CONSTRAINT fk_bpmn_process FOREIGN KEY (Process_Definition_ID) REFERENCES process_definition(ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 9️⃣ Workflow Instance Variables
CREATE TABLE IF NOT EXISTS workflow_instance_variable (
    ID int(11) NOT NULL AUTO_INCREMENT,
    Workflow_Instance_ID int(11) NOT NULL,
    Variable_Name varchar(255) NOT NULL,
    Variable_Value text DEFAULT NULL,
    Created_At datetime DEFAULT CURRENT_TIMESTAMP,
    Updated_At datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ID),
    KEY idx_instance (Workflow_Instance_ID),
    KEY idx_var_name (Variable_Name),
    CONSTRAINT fk_variable_instance FOREIGN KEY (Workflow_Instance_ID) REFERENCES workflow_instance(ID) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================
-- End of Full Schema
-- ============================================
