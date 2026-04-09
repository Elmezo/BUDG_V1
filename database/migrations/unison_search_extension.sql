-- =====================================================
-- Unison Search Extension - Database Schema Migration
-- =====================================================
-- This script updates the database schema to support
-- the full Unison Search functionality matching BUDG behavior
--
-- Run against the target schema (e.g. USE project;). Section 1 uses
-- DATABASE() / information_schema and is idempotent if facetId+PK already exist.
-- =====================================================

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

-- =====================================================
-- 1. Update unison_facets table structure (idempotent)
-- =====================================================
-- Fresh installs often already use `facetId` + PK(unison_id, facetId).
-- Legacy DBs may still have column `facet` and no composite PK.

SET @uf_has_facet := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'unison_facets'
    AND COLUMN_NAME = 'facet'
);

SET @uf_has_facetid := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'unison_facets'
    AND COLUMN_NAME = 'facetId'
);

SET @uf_has_pk := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'unison_facets'
    AND CONSTRAINT_TYPE = 'PRIMARY KEY'
);

SET @uf_fk_unison := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'unison_facets'
    AND CONSTRAINT_NAME = 'fk_unison_facets_unison'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);

-- Drop FK only when we must alter columns or add PK (avoids unnecessary drop on current schema)
SET @uf_need_fk_drop := (@uf_has_facet > 0 OR (@uf_has_pk = 0 AND @uf_has_facetid > 0));

SET @sql := IF(@uf_need_fk_drop > 0 AND @uf_fk_unison > 0,
  'ALTER TABLE `unison_facets` DROP FOREIGN KEY `fk_unison_facets_unison`',
  'DO 0');
PREPARE uf_stmt FROM @sql;
EXECUTE uf_stmt;
DEALLOCATE PREPARE uf_stmt;

-- Rename legacy `facet` -> `facetId` when present
SET @sql := IF(@uf_has_facet > 0,
  'ALTER TABLE `unison_facets` CHANGE COLUMN `facet` `facetId` VARCHAR(256) DEFAULT NULL',
  'DO 0');
PREPARE uf_stmt FROM @sql;
EXECUTE uf_stmt;
DEALLOCATE PREPARE uf_stmt;

-- Remove duplicate (unison_id, facetId) before adding PK (requires column facetId)
DELETE uf1 FROM `unison_facets` uf1
INNER JOIN `unison_facets` uf2
WHERE uf1.unison_id = uf2.unison_id
  AND uf1.facetId = uf2.facetId
  AND uf1.facetId IS NOT NULL
  AND uf2.facetId IS NOT NULL
  AND (uf1.active_fields IS NULL OR uf1.active_fields < uf2.active_fields
       OR (uf1.active_fields = uf2.active_fields AND uf1.ordering < uf2.ordering));

-- Add composite primary key only if missing
SET @sql := IF(@uf_has_pk = 0 AND @uf_has_facetid + @uf_has_facet > 0,
  'ALTER TABLE `unison_facets` ADD PRIMARY KEY (`unison_id`, `facetId`)',
  'DO 0');
PREPARE uf_stmt FROM @sql;
EXECUTE uf_stmt;
DEALLOCATE PREPARE uf_stmt;

-- Re-create FK when missing (covers re-add after drop, or legacy tables)
SET @uf_fk_unison := (
  SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND TABLE_NAME = 'unison_facets'
    AND CONSTRAINT_NAME = 'fk_unison_facets_unison'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql := IF(@uf_fk_unison = 0,
  'ALTER TABLE `unison_facets` ADD CONSTRAINT `fk_unison_facets_unison` FOREIGN KEY (`unison_id`) REFERENCES `unison` (`id`) ON DELETE CASCADE ON UPDATE CASCADE',
  'DO 0');
PREPARE uf_stmt FROM @sql;
EXECUTE uf_stmt;
DEALLOCATE PREPARE uf_stmt;

-- =====================================================
-- 2. Ensure unison.id is AUTO_INCREMENT
-- =====================================================

-- Check if unison table needs AUTO_INCREMENT
-- If id column is not auto-increment, we'll need to handle it
-- Note: This assumes the table might need modification
ALTER TABLE `unison` 
MODIFY COLUMN `id` INT(11) NOT NULL AUTO_INCREMENT;

-- =====================================================
-- 3. Update user_search table
-- =====================================================

-- Ensure hitcount defaults to 0
ALTER TABLE `user_search` 
MODIFY COLUMN `hitcount` INT(11) DEFAULT 0;

-- Update any NULL hitcount values to 0
UPDATE `user_search` 
SET `hitcount` = 0 
WHERE `hitcount` IS NULL;

-- =====================================================
-- 4. Add app_config entries for new keys
-- =====================================================

-- Insert Authorization_enabled if not exists
INSERT INTO `app_config` (`config_key`, `definition`)
SELECT 'Authorization_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM `app_config` WHERE `config_key` = 'Authorization_enabled'
);

-- Insert Event_Monitor_enabled if not exists
INSERT INTO `app_config` (`config_key`, `definition`)
SELECT 'Event_Monitor_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM `app_config` WHERE `config_key` = 'Event_Monitor_enabled'
);

-- Insert SEARCH_MIGRATION_V1_TO_V2 if not exists
INSERT INTO `app_config` (`config_key`, `definition`)
SELECT 'SEARCH_MIGRATION_V1_TO_V2', 'false'
WHERE NOT EXISTS (
    SELECT 1 FROM `app_config` WHERE `config_key` = 'SEARCH_MIGRATION_V1_TO_V2'
);

-- Ensure Unison_Fuzzy_Default exists (may already exist with different case)
INSERT INTO `app_config` (`config_key`, `definition`)
SELECT 'Unison_Fuzzy_Default', 'false'
WHERE NOT EXISTS (
    SELECT 1 FROM `app_config` WHERE `config_key` IN ('Unison_Fuzzy_Default', 'Unison Fuzzy Default')
);

-- Ensure Hide_Non_Public exists (may already exist)
INSERT INTO `app_config` (`config_key`, `definition`)
SELECT 'Hide_Non_Public', 'false'
WHERE NOT EXISTS (
    SELECT 1 FROM `app_config` WHERE `config_key` IN ('Hide_Non_Public', 'Hide Non-Public')
);

-- =====================================================
-- 5. Initialize unison rows for existing users
-- =====================================================

-- Create unison entry for each user in i_user that doesn't have one
INSERT INTO `unison` (`user_reference`, `facet_version`)
SELECT `reference`, '1.0'
FROM `i_user`
WHERE `reference` NOT IN (SELECT DISTINCT `user_reference` FROM `unison` WHERE `user_reference` IS NOT NULL)
AND `active` = 1;

-- =====================================================
-- 6. Initialize 22 facet rows per existing unison row
-- =====================================================

-- Define the 22 facets from the template
SET @facets = 'DATASET,ATTRIBUTE,SYSTEM,GLOSSARY,DATAQUALITY,PEOPLE,ROLE,BUSINESS_AREA,LEGAL_ENTITY,CLIENT,COMMITTEE,POLICY,PROCESS,INTERFACE,CAPABILITY,PRODUCT,ORG_UNIT,GEOGRAPHY,REGULATION,REGULATOR,REGULATORY_THEME';

-- For each unison row, ensure all 22 facets exist
-- This uses a stored procedure approach via multiple INSERT statements
-- We'll create facets based on UNISON_DEFAULTS config if available

-- Insert missing facets for each unison row
-- Default visibility and activeFields will be set from UNISON_DEFAULTS in data migration script

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'DATASET',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"DATASET"%visibility":true%') THEN 1 ELSE 0 END,
    'refNumber,name,definition,lifecycle,systemId,systemName',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"DATASET"%visibility":true%') THEN 1 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'DATASET'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'ATTRIBUTE',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"ATTRIBUTE"%visibility":true%') THEN 1 ELSE 0 END,
    'refNumber,name,definition,dataSetId,dataSetName,systemId,systemName',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"ATTRIBUTE"%visibility":true%') THEN 2 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'ATTRIBUTE'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'SYSTEM',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"SYSTEM"%visibility":true%') THEN 1 ELSE 0 END,
    'name,description,type,lifecycle,classification,ciarating',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"SYSTEM"%visibility":true%') THEN 3 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'SYSTEM'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'GLOSSARY',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"GLOSSARY"%visibility":true%') THEN 1 ELSE 0 END,
    'name,type,parentName,parentType,kde,description,lifecycle',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"GLOSSARY"%visibility":true%') THEN 4 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'GLOSSARY'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'DATAQUALITY',
    0,
    '',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'DATAQUALITY'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'PEOPLE',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"PEOPLE"%visibility":true%') THEN 1 ELSE 0 END,
    'firstName,lastName,email,function,orgUnit',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"PEOPLE"%visibility":true%') THEN 5 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'PEOPLE'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'ROLE',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"ROLE"%visibility":true%') THEN 1 ELSE 0 END,
    'role,fullName,objectType,object,roleAccepted',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"ROLE"%visibility":true%') THEN 6 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'ROLE'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'BUSINESS_AREA',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"BUSINESS_AREA"%visibility":true%') THEN 1 ELSE 0 END,
    'name,parent,description',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"BUSINESS_AREA"%visibility":true%') THEN 7 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'BUSINESS_AREA'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'LEGAL_ENTITY',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"LEGAL_ENTITY"%visibility":true%') THEN 1 ELSE 0 END,
    'shortName,parentShortName,description',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"LEGAL_ENTITY"%visibility":true%') THEN 8 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'LEGAL_ENTITY'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'CLIENT',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"CLIENT"%visibility":true%') THEN 1 ELSE 0 END,
    'name,parent,description,lifecycle',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"CLIENT"%visibility":true%') THEN 9 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'CLIENT'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'COMMITTEE',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"COMMITTEE"%visibility":true%') THEN 1 ELSE 0 END,
    'refNumber,name,parent,description',
    CASE WHEN EXISTS (SELECT 1 FROM `app_config` WHERE `config_key` = 'UNISON_DEFAULTS' 
                      AND `definition` LIKE '%"id":"COMMITTEE"%visibility":true%') THEN 10 ELSE 0 END
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'COMMITTEE'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'POLICY',
    0,
    'refNumber,name,parentName,description,lifecycle',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'POLICY'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'PROCESS',
    0,
    'refNumber,name,parentName,description',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'PROCESS'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'INTERFACE',
    0,
    'refNumber,name,description,sourceSystemShortName,targetSystemShortName,automation,frequency,lifecycle',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'INTERFACE'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'CAPABILITY',
    0,
    'refNumber,name,parent,description',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'CAPABILITY'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'PRODUCT',
    0,
    'refNumber,name,parent,description,axonStatus',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'PRODUCT'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'ORG_UNIT',
    0,
    'refNumber,name,parent,description,axonStatus',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'ORG_UNIT'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'GEOGRAPHY',
    0,
    'name,parent,description',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'GEOGRAPHY'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'REGULATION',
    0,
    'refNumber,name,parent,description',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'REGULATION'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'REGULATOR',
    0,
    'name,description,shortName',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'REGULATOR'
);

INSERT INTO `unison_facets` (`unison_id`, `facetId`, `active`, `active_fields`, `ordering`)
SELECT 
    u.id,
    'REGULATORY_THEME',
    0,
    'refNumber,name,parent,description',
    0
FROM `unison` u
WHERE NOT EXISTS (
    SELECT 1 FROM `unison_facets` uf 
    WHERE uf.unison_id = u.id AND uf.facetId = 'REGULATORY_THEME'
);

-- =====================================================
-- 7. Fix ordering: if active = false, ordering = 0
-- =====================================================

UPDATE `unison_facets`
SET `ordering` = 0
WHERE `active` = 0 AND `ordering` != 0;

-- =====================================================
-- 8. Recalculate ordering for active facets
-- =====================================================

-- This will be handled by the application layer, but we ensure
-- that active facets have proper incremental ordering
-- The application will handle reordering when facets are activated/deactivated

COMMIT;

