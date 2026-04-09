-- ============================================================
-- Migration: Create user_quick_link table
-- Replaces the QUICK_LINK key in app_config with a dedicated
-- table that supports per-user quick link assignments.
-- ============================================================

CREATE TABLE IF NOT EXISTS `user_quick_link` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `target_user_id` INT(11) DEFAULT NULL COMMENT 'NULL = global quick link for all users',
  `search_id` INT(11) NOT NULL,
  `search_name` VARCHAR(256) DEFAULT NULL,
  `description` VARCHAR(256) DEFAULT NULL,
  `created_by` INT(11) NOT NULL COMMENT 'Admin/SuperAdmin who set this link',
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_target_user` (`target_user_id`),
  KEY `fk_uql_search` (`search_id`),
  KEY `fk_uql_creator` (`created_by`),
  CONSTRAINT `fk_uql_search` FOREIGN KEY (`search_id`) REFERENCES `user_search` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_uql_target` FOREIGN KEY (`target_user_id`) REFERENCES `people` (`ID`) ON DELETE CASCADE,
  CONSTRAINT `fk_uql_creator` FOREIGN KEY (`created_by`) REFERENCES `people` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- ============================================================
-- Migration: Move existing QUICK_LINK from app_config into
-- user_quick_link as the global link (target_user_id = NULL).
--
-- Notes:
--   - created_by is assigned to the first (lowest ID) SuperAdmin
--     as a safe default since the original creator is unknown.
--   - The AND NOT EXISTS guard makes this idempotent (safe to re-run).
--   - The AND EXISTS guard skips migration if the referenced search
--     no longer exists (avoids FK violation).
-- ============================================================

INSERT INTO user_quick_link (target_user_id, search_id, search_name, description, created_by)
SELECT
  NULL,
  CAST(JSON_UNQUOTE(JSON_EXTRACT(definition, '$.savedSearchId')) AS UNSIGNED),
  JSON_UNQUOTE(JSON_EXTRACT(definition, '$.savedSearchName')),
  JSON_UNQUOTE(JSON_EXTRACT(definition, '$.description')),
  (SELECT p.ID FROM people p
   JOIN role r ON p.System_Role = r.id
   WHERE REPLACE(REPLACE(REPLACE(LOWER(r.primaryname), ' ', ''), '_', ''), '-', '') IN ('superadmin', 'suberadmin')
     AND p.Deleted_date IS NULL
   ORDER BY p.ID ASC
   LIMIT 1)
FROM app_config
WHERE config_key = 'QUICK_LINK'
  AND definition IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(definition, '$.savedSearchId')) IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM user_search
    WHERE id = CAST(JSON_UNQUOTE(JSON_EXTRACT(definition, '$.savedSearchId')) AS UNSIGNED)
  )
  AND NOT EXISTS (
    SELECT 1 FROM user_quick_link WHERE target_user_id IS NULL
  );
