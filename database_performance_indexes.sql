-- ============================================
-- Unison Search Performance Indexes
-- Database: MySQL/MariaDB (InnoDB Engine)
-- ============================================
-- This script creates indexes to improve search performance
-- Execute: mysql -u username -p database_name < database_performance_indexes.sql
-- ============================================

-- 1. Glossary Table Indexes
-- ============================================
-- Note: Column name is DeletedDatetime (not DeletedDate)
-- Note: MySQL/MariaDB doesn't support WHERE clause in CREATE INDEX, so we create indexes without filters
-- Note: Glossary table doesn't have a Ref column, only Name
CREATE INDEX IF NOT EXISTS idx_glossary_name 
    ON glossary(Name(100));

CREATE INDEX IF NOT EXISTS idx_glossary_deleted 
    ON glossary(Deleted_Datetime);

CREATE INDEX IF NOT EXISTS idx_glossary_type_status 
    ON glossary(Type, AxonStatus);

CREATE INDEX IF NOT EXISTS idx_glossary_lifecycle 
    ON glossary(Lifecycle);

-- 2. Dataset Table Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_dataset_name 
    ON dataset(PrimaryName(100));

CREATE INDEX IF NOT EXISTS idx_dataset_ref 
    ON dataset(RefNumber(100));

CREATE INDEX IF NOT EXISTS idx_dataset_deleted 
    ON dataset(DeletedDatetime);

CREATE INDEX IF NOT EXISTS idx_dataset_system 
    ON dataset(MasterSource);

CREATE INDEX IF NOT EXISTS idx_dataset_glossary 
    ON dataset(glossary);

-- 3. Attribute Table Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_attribute_name 
    ON attribute(PrimaryName(100));

CREATE INDEX IF NOT EXISTS idx_attribute_ref 
    ON attribute(RefNumber(100));

CREATE INDEX IF NOT EXISTS idx_attribute_dataset 
    ON attribute(Dataset_ID);

CREATE INDEX IF NOT EXISTS idx_attribute_glossary 
    ON attribute(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_attribute_deleted 
    ON attribute(DeletedDatetime);

-- 4. System Table Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_system_name 
    ON system(Name(100));

CREATE INDEX IF NOT EXISTS idx_system_long_name 
    ON system(Long_Name(100));

CREATE INDEX IF NOT EXISTS idx_system_deleted 
    ON system(Deleted_datetime);

CREATE INDEX IF NOT EXISTS idx_system_parent 
    ON system(parent_id);

-- 5. People Table Indexes
-- ============================================
CREATE INDEX IF NOT EXISTS idx_people_name 
    ON people(First_Name(50), Last_Name(50));

CREATE INDEX IF NOT EXISTS idx_people_email 
    ON people(Email(100));

CREATE INDEX IF NOT EXISTS idx_people_deleted 
    ON people(Deleted_date);

-- 6. Relationship Tables Indexes (للـ Graph Traversal)
-- ============================================
-- Business Area x Glossary
CREATE INDEX IF NOT EXISTS idx_bxg_businessarea 
    ON businessarea_x_glossary(BusinessArea_ID);

CREATE INDEX IF NOT EXISTS idx_bxg_glossary 
    ON businessarea_x_glossary(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_bxg_both 
    ON businessarea_x_glossary(BusinessArea_ID, Glossary_ID);

-- Client x Glossary
CREATE INDEX IF NOT EXISTS idx_cxg_client 
    ON client_x_glossary(ClientID);

CREATE INDEX IF NOT EXISTS idx_cxg_glossary 
    ON client_x_glossary(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_cxg_both 
    ON client_x_glossary(ClientID, Glossary_ID);

-- Capability x Glossary
CREATE INDEX IF NOT EXISTS idx_capxg_capability 
    ON capability_x_glossary(CapabilityID);

CREATE INDEX IF NOT EXISTS idx_capxg_glossary 
    ON capability_x_glossary(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_capxg_both 
    ON capability_x_glossary(CapabilityID, Glossary_ID);

-- Committee x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_comxg_committee 
--     ON committee_x_glossary(Committee_ID);
-- CREATE INDEX IF NOT EXISTS idx_comxg_glossary 
--     ON committee_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_comxg_both 
--     ON committee_x_glossary(Committee_ID, Glossary_ID);

-- Geography x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_geoxg_geography 
--     ON geography_x_glossary(Geography_ID);
-- CREATE INDEX IF NOT EXISTS idx_geoxg_glossary 
--     ON geography_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_geoxg_both 
--     ON geography_x_glossary(Geography_ID, Glossary_ID);

-- Legal x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_lexg_legal 
--     ON legal_x_glossary(Legal_ID);
-- CREATE INDEX IF NOT EXISTS idx_lexg_glossary 
--     ON legal_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_lexg_both 
--     ON legal_x_glossary(Legal_ID, Glossary_ID);

-- Org Unit x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_ouxg_orgunit 
--     ON orgunit_x_glossary(OrgUnit_ID);
-- CREATE INDEX IF NOT EXISTS idx_ouxg_glossary 
--     ON orgunit_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_ouxg_both 
--     ON orgunit_x_glossary(OrgUnit_ID, Glossary_ID);

-- Policy x Glossary
CREATE INDEX IF NOT EXISTS idx_poxg_policy 
    ON policy_x_glossary(Policy_ID);

CREATE INDEX IF NOT EXISTS idx_poxg_glossary 
    ON policy_x_glossary(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_poxg_both 
    ON policy_x_glossary(Policy_ID, Glossary_ID);

-- Process x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_prxg_process 
--     ON process_x_glossary(Process_ID);
-- CREATE INDEX IF NOT EXISTS idx_prxg_glossary 
--     ON process_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_prxg_both 
--     ON process_x_glossary(Process_ID, Glossary_ID);

-- Product x Glossary
CREATE INDEX IF NOT EXISTS idx_prdxg_product 
    ON product_x_glossary(Product_ID);

CREATE INDEX IF NOT EXISTS idx_prdxg_glossary 
    ON product_x_glossary(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_prdxg_both 
    ON product_x_glossary(Product_ID, Glossary_ID);

-- Regulation x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_regxg_regulation 
--     ON regulation_x_glossary(RegulationID);
-- CREATE INDEX IF NOT EXISTS idx_regxg_glossary 
--     ON regulation_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_regxg_both 
--     ON regulation_x_glossary(RegulationID, Glossary_ID);

-- Regulator x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_reguxg_regulator 
--     ON regulator_x_glossary(RegulatorID);
-- CREATE INDEX IF NOT EXISTS idx_reguxg_glossary 
--     ON regulator_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_reguxg_both 
--     ON regulator_x_glossary(RegulatorID, Glossary_ID);

-- Regulatory Theme x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_rtxg_regulatory_theme 
--     ON regulatory_theme_x_glossary(RegulatoryThemeID);
-- CREATE INDEX IF NOT EXISTS idx_rtxg_glossary 
--     ON regulatory_theme_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_rtxg_both 
--     ON regulatory_theme_x_glossary(RegulatoryThemeID, Glossary_ID);

-- System x Glossary (TABLE DOES NOT EXIST - COMMENTED OUT)
-- CREATE INDEX IF NOT EXISTS idx_sxg_system 
--     ON system_x_glossary(SystemID);
-- CREATE INDEX IF NOT EXISTS idx_sxg_glossary 
--     ON system_x_glossary(Glossary_ID);
-- CREATE INDEX IF NOT EXISTS idx_sxg_both 
--     ON system_x_glossary(SystemID, Glossary_ID);

-- Dataset x Glossary (if exists)
CREATE INDEX IF NOT EXISTS idx_dxg_dataset 
    ON dataset_x_glossary(Dataset_ID);

CREATE INDEX IF NOT EXISTS idx_dxg_glossary 
    ON dataset_x_glossary(Glossary_ID);

CREATE INDEX IF NOT EXISTS idx_dxg_both 
    ON dataset_x_glossary(Dataset_ID, Glossary_ID);

-- System x Dataset (if exists)
CREATE INDEX IF NOT EXISTS idx_sxd_system 
    ON system_x_dataset(System_ID);

CREATE INDEX IF NOT EXISTS idx_sxd_dataset 
    ON system_x_dataset(Dataset_ID);

CREATE INDEX IF NOT EXISTS idx_sxd_both 
    ON system_x_dataset(System_ID, Dataset_ID);

-- 7. Full-Text Search Indexes (MySQL FULLTEXT)
-- ============================================
-- Note: FULLTEXT works only with InnoDB in MySQL 5.6+
-- These indexes enable fast full-text search on Name and Definition columns

ALTER TABLE glossary 
    ADD FULLTEXT INDEX idx_glossary_fulltext (Name, Description);

ALTER TABLE dataset 
    ADD FULLTEXT INDEX idx_dataset_fulltext (PrimaryName, definition);

ALTER TABLE attribute 
    ADD FULLTEXT INDEX idx_attribute_fulltext (PrimaryName, Definition);

ALTER TABLE system 
    ADD FULLTEXT INDEX idx_system_fulltext (Name, Long_Name, Description);

ALTER TABLE people 
    ADD FULLTEXT INDEX idx_people_fulltext (First_Name, Last_Name, Email);

-- 8. Composite Indexes للاستعلامات الشائعة
-- ============================================
CREATE INDEX IF NOT EXISTS idx_glossary_search 
    ON glossary(Name(100), Type, AxonStatus, Deleted_Datetime);

CREATE INDEX IF NOT EXISTS idx_dataset_search 
    ON dataset(PrimaryName(100), MasterSource, status, DeletedDatetime);

CREATE INDEX IF NOT EXISTS idx_attribute_search 
    ON attribute(PrimaryName(100), Dataset_ID, Glossary_ID, DeletedDatetime);

CREATE INDEX IF NOT EXISTS idx_system_search 
    ON system(Name(100), Type, status, Deleted_datetime);

-- ============================================
-- تحليل الجداول بعد إنشاء الـ Indexes
-- ============================================
ANALYZE TABLE glossary;
ANALYZE TABLE dataset;
ANALYZE TABLE attribute;
ANALYZE TABLE system;
ANALYZE TABLE people;
ANALYZE TABLE businessarea_x_glossary;
ANALYZE TABLE client_x_glossary;
ANALYZE TABLE capability_x_glossary;
-- Tables below do not exist - commented out:
-- ANALYZE TABLE committee_x_glossary;
-- ANALYZE TABLE geography_x_glossary;
-- ANALYZE TABLE legal_x_glossary;
-- ANALYZE TABLE orgunit_x_glossary;
ANALYZE TABLE policy_x_glossary;
-- ANALYZE TABLE process_x_glossary;
ANALYZE TABLE product_x_glossary;
-- ANALYZE TABLE regulation_x_glossary;
-- ANALYZE TABLE regulator_x_glossary;
-- ANALYZE TABLE regulatory_theme_x_glossary;
-- ANALYZE TABLE system_x_glossary;

-- ============================================
-- التحقق من الـ Indexes
-- ============================================
-- Uncomment to verify indexes were created:
-- SHOW INDEX FROM glossary;
-- SHOW INDEX FROM dataset;
-- SHOW INDEX FROM attribute;
-- SHOW INDEX FROM system;
-- SHOW INDEX FROM people;

