-- ============================================
-- Create LDAP OrgUnit Mapping Table
-- This table maps LDAP Group DNs to OrgUnits with priority
-- ============================================

CREATE TABLE IF NOT EXISTS ldap_orgunit_mapping (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ldap_group_dn VARCHAR(500) NOT NULL COMMENT 'LDAP Group Distinguished Name',
    org_unit_id INT NOT NULL COMMENT 'Reference to org_unit.ID',
    priority INT NOT NULL DEFAULT 100 COMMENT 'Higher priority = selected first (DESC order)',
    is_active TINYINT(1) DEFAULT 1 COMMENT 'Enable/disable mapping',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by INT NOT NULL,
    UNIQUE KEY uk_group_orgunit (ldap_group_dn, org_unit_id),
    INDEX idx_ldap_group_dn (ldap_group_dn),
    INDEX idx_org_unit_id (org_unit_id),
    INDEX idx_priority (priority DESC),
    FOREIGN KEY (org_unit_id) REFERENCES org_unit(ID) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES people(ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
COMMENT='Maps LDAP Group DNs to OrgUnits for automatic user assignment during sync';

