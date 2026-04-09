package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for resolving OrgUnit assignments from LDAP Group DNs
 * Implements priority-based resolution matching BUDG behavior
 */
public class LdapOrgUnitMappingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LdapOrgUnitMappingService.class);
    
    /**
     * Resolve OrgUnit ID from LDAP Group DNs
     * 
     * Priority resolution logic:
     * 1. Find all mappings where ldap_group_dn IN (groupDns) AND is_active = 1
     * 2. Order by priority DESC, id ASC (deterministic behavior)
     * 3. Return first match's org_unit_id
     * 4. If no match, return null
     * 
     * @param groupDns List of LDAP Group Distinguished Names
     * @return org_unit_id if match found, null otherwise
     */
    public Integer resolveOrgUnitFromGroups(List<String> groupDns) {
        if (groupDns == null || groupDns.isEmpty()) {
            logger.debug("No group DNs provided, returning null OrgUnit");
            return null;
        }
        
        // Remove null/empty DNs
        List<String> validGroupDns = new ArrayList<>();
        for (String dn : groupDns) {
            if (dn != null && !dn.trim().isEmpty()) {
                validGroupDns.add(dn.trim());
            }
        }
        
        if (validGroupDns.isEmpty()) {
            logger.debug("No valid group DNs provided, returning null OrgUnit");
            return null;
        }
        
        String sql = "SELECT org_unit_id " +
                    "FROM ldap_orgunit_mapping " +
                    "WHERE ldap_group_dn IN (" + String.join(",", Collections.nCopies(validGroupDns.size(), "?")) + ") " +
                    "AND is_active = 1 " +
                    "AND org_unit_id IN (SELECT ID FROM org_unit WHERE deleted_Date IS NULL) " +
                    "ORDER BY priority DESC, id ASC " +
                    "LIMIT 1";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set parameters for IN clause
            for (int i = 0; i < validGroupDns.size(); i++) {
                pstmt.setString(i + 1, validGroupDns.get(i));
            }
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Integer orgUnitId = rs.getInt("org_unit_id");
                    logger.debug("Resolved OrgUnit ID {} from {} group DNs", orgUnitId, validGroupDns.size());
                    return orgUnitId;
                } else {
                    logger.debug("No OrgUnit mapping found for {} group DNs", validGroupDns.size());
                    return null;
                }
            }
            
        } catch (SQLException e) {
            logger.error("Error resolving OrgUnit from group DNs", e);
            // Return null on error - don't fail the sync
            return null;
        }
    }
}

