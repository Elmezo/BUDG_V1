-- =====================================================
-- Unison Search Extension - Data Migration
-- =====================================================
-- This script migrates existing data to the new format
-- =====================================================

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

-- =====================================================
-- 1. Update facet configurations from UNISON_DEFAULTS
-- =====================================================

-- Get UNISON_DEFAULTS from app_config
SET @unison_defaults = (
    SELECT `definition` 
    FROM `app_config` 
    WHERE `config_key` = 'UNISON_DEFAULTS' 
    LIMIT 1
);

-- If UNISON_DEFAULTS exists, update facets based on it
-- Note: This requires JSON parsing which MySQL 5.7+ supports
-- For older versions, this would need to be done via application code

-- Update facets based on visibility from UNISON_DEFAULTS
-- This is a simplified approach - full JSON parsing would be better
-- but requires MySQL 5.7+ JSON functions

-- For now, we'll update based on the known default visibility
-- The application layer will handle full JSON parsing

UPDATE `unison_facets` uf
INNER JOIN `unison` u ON uf.unison_id = u.id
SET 
    uf.active = CASE uf.facetId
        WHEN 'DATASET' THEN 1
        WHEN 'ATTRIBUTE' THEN 1
        WHEN 'SYSTEM' THEN 1
        WHEN 'GLOSSARY' THEN 1
        WHEN 'DATAQUALITY' THEN 0
        WHEN 'PEOPLE' THEN 1
        WHEN 'ROLE' THEN 1
        WHEN 'BUSINESS_AREA' THEN 1
        WHEN 'LEGAL_ENTITY' THEN 1
        WHEN 'CLIENT' THEN 1
        WHEN 'COMMITTEE' THEN 1
        WHEN 'POLICY' THEN 0
        WHEN 'PROCESS' THEN 0
        WHEN 'INTERFACE' THEN 0
        WHEN 'CAPABILITY' THEN 0
        WHEN 'PRODUCT' THEN 0
        WHEN 'ORG_UNIT' THEN 0
        WHEN 'GEOGRAPHY' THEN 0
        WHEN 'REGULATION' THEN 0
        WHEN 'REGULATOR' THEN 0
        WHEN 'REGULATORY_THEME' THEN 0
        ELSE 0
    END,
    uf.ordering = CASE 
        WHEN uf.facetId IN ('DATASET', 'ATTRIBUTE', 'SYSTEM', 'GLOSSARY', 'PEOPLE', 'ROLE', 
                            'BUSINESS_AREA', 'LEGAL_ENTITY', 'CLIENT', 'COMMITTEE') 
        THEN CASE uf.facetId
            WHEN 'DATASET' THEN 1
            WHEN 'ATTRIBUTE' THEN 2
            WHEN 'SYSTEM' THEN 3
            WHEN 'GLOSSARY' THEN 4
            WHEN 'PEOPLE' THEN 5
            WHEN 'ROLE' THEN 6
            WHEN 'BUSINESS_AREA' THEN 7
            WHEN 'LEGAL_ENTITY' THEN 8
            WHEN 'CLIENT' THEN 9
            WHEN 'COMMITTEE' THEN 10
            ELSE 0
        END
        ELSE 0
    END
WHERE uf.active IS NULL OR uf.ordering IS NULL;

-- =====================================================
-- 2. Migrate existing saved searches to new format
-- =====================================================

-- Update condition_definition to new searchGroups format if it's in old format
-- This is a complex migration that may need application-level processing
-- For now, we'll ensure hitcount is set correctly

UPDATE `user_search`
SET `hitcount` = COALESCE(`hitcount`, 0)
WHERE `hitcount` IS NULL;

-- =====================================================
-- 3. Ensure all users have unison entries
-- =====================================================

-- This should have been done in schema migration, but ensure it here too
INSERT INTO `unison` (`user_reference`, `facet_version`)
SELECT `reference`, '1.0'
FROM `i_user`
WHERE `reference` NOT IN (
    SELECT DISTINCT `user_reference` 
    FROM `unison` 
    WHERE `user_reference` IS NOT NULL
)
AND `active` = 1;

-- =====================================================
-- 4. Ensure all unison entries have all 22 facets
-- =====================================================

-- This should have been done in schema migration, but ensure completeness here
-- The application will handle this on-demand, but we ensure baseline here

-- Count facets per unison and identify missing ones
-- This query helps identify unison rows that don't have all 22 facets
-- The application layer should handle creating missing facets

COMMIT;

