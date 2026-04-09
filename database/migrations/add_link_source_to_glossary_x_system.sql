-- ============================================
-- Add Link_Source to glossary_x_system for mutual exclusion
-- between Data Content Summary (system) and Strategic Source (glossary).
-- Values: 'glossary' | 'system' | NULL (legacy, shown in both).
-- ============================================

ALTER TABLE glossary_x_system
ADD COLUMN Link_Source VARCHAR(20) DEFAULT NULL
COMMENT 'Where link was created: glossary=Strategic Source, system=Data Content Summary, NULL=legacy (both)';

ALTER TABLE glossary_x_system
ADD INDEX idx_link_source (Link_Source);

-- Optionally add to audit table for history
ALTER TABLE glossary_x_system_audit
ADD COLUMN Link_Source VARCHAR(20) DEFAULT NULL
COMMENT 'Where link was created: glossary=Strategic Source, system=Data Content Summary, NULL=legacy (both)';
