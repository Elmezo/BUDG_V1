package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DAO for CR_Stakeholders operations
 * Handles copying stakeholders from source objects to change requests
 * and keeping them in sync
 */
public class CRStakeholderDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(CRStakeholderDAO.class);

    // Mapping of facet types to their stakeholder junction tables
    private static final Map<String, String> FACET_STAKEHOLDER_TABLES = new HashMap<>();
    private static final Map<String, String> FACET_ID_COLUMNS = new HashMap<>();
    private static final Map<String, String> FACET_OBJECT_X_IPID_COLUMNS = new HashMap<>();
    
    static {
        // Table mappings: facet type -> junction table name
        FACET_STAKEHOLDER_TABLES.put("business-area", "businessarea_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("businessarea", "businessarea_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("dataset", "dataset_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("data-set", "dataset_x_objectxpeople"); // Support "Data Set" -> "data-set"
        FACET_STAKEHOLDER_TABLES.put("system", "system_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("capability", "capability_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("client", "client_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("product", "product_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("system-interface", "interface_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("interface", "interface_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("policy", "policy_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("committee", "committee_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("process", "process_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("glossary", "glossary_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("geography", "geography_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("legal-entity", "legalentity_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("legalentity", "legalentity_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("org-unit", "orgunit_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("orgunit", "orgunit_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("project", "project_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("regulation", "regulation_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("regulator", "regulator_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("regulatory-theme", "regulatorytheme_x_objectxpeople");
        FACET_STAKEHOLDER_TABLES.put("regulatorytheme", "regulatorytheme_x_objectxpeople");
        
        // ID column mappings: facet type -> ID column name in junction table (actual DB column names)
        FACET_ID_COLUMNS.put("business-area", "BusinessAreaID");
        FACET_ID_COLUMNS.put("businessarea", "BusinessAreaID");
        FACET_ID_COLUMNS.put("dataset", "Dataset_ID");
        FACET_ID_COLUMNS.put("data-set", "Dataset_ID"); // Support "Data Set" -> "data-set"
        FACET_ID_COLUMNS.put("system", "SystemID");
        FACET_ID_COLUMNS.put("capability", "CapabilityID");
        FACET_ID_COLUMNS.put("client", "ClientID");
        FACET_ID_COLUMNS.put("product", "product_id");
        FACET_ID_COLUMNS.put("system-interface", "InterfaceID");
        FACET_ID_COLUMNS.put("interface", "InterfaceID");
        FACET_ID_COLUMNS.put("policy", "Policy_ID");
        FACET_ID_COLUMNS.put("committee", "Committee_ID");
        FACET_ID_COLUMNS.put("process", "process_id");
        FACET_ID_COLUMNS.put("glossary", "GlossaryID");
        FACET_ID_COLUMNS.put("geography", "GeographyID");
        FACET_ID_COLUMNS.put("legal-entity", "LegalEntityID");
        FACET_ID_COLUMNS.put("legalentity", "LegalEntityID");
        FACET_ID_COLUMNS.put("org-unit", "OrgUnitID");
        FACET_ID_COLUMNS.put("orgunit", "OrgUnitID");
        FACET_ID_COLUMNS.put("project", "ProjectID");
        FACET_ID_COLUMNS.put("regulation", "RegulationID");
        FACET_ID_COLUMNS.put("regulator", "RegulatorID");
        FACET_ID_COLUMNS.put("regulatory-theme", "RegulatoryThemeID");
        FACET_ID_COLUMNS.put("regulatorytheme", "RegulatoryThemeID");
        
        // Object_x_ipid column name variations (different tables use different column names)
        FACET_OBJECT_X_IPID_COLUMNS.put("committee", "Object_X_ipid");
        FACET_OBJECT_X_IPID_COLUMNS.put("policy", "Object_X_IP");
        FACET_OBJECT_X_IPID_COLUMNS.put("process", "object_x_ip");
        FACET_OBJECT_X_IPID_COLUMNS.put("product", "object_x_ip");
        // Default is Object_x_ipid for all others
    }
    
    /**
     * Get the Object_x_ipid column name for a facet type
     */
    private String getObjectXIpidColumn(String normalizedFacetType) {
        return FACET_OBJECT_X_IPID_COLUMNS.getOrDefault(normalizedFacetType, "Object_x_ipid");
    }

    /**
     * Parse a reference string like "Glossary 15" into facet type and ID
     * @param reference The reference string
     * @return Map with "facetType" and "facetId" keys, or null if invalid
     */
    public Map<String, Object> parseReference(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            return null;
        }
        
        // Match pattern: "Facet Name 123" or "Facet-Name 123"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(.+?)\\s+(\\d+)$");
        java.util.regex.Matcher matcher = pattern.matcher(reference.trim());
        
        if (matcher.matches()) {
            String facetName = matcher.group(1).trim();
            int facetId = Integer.parseInt(matcher.group(2));
            
            // Normalize facet name to match our mapping keys
            String normalizedFacetType = facetName.toLowerCase()
                .replace(" ", "-")
                .replace("_", "-");
            
            Map<String, Object> result = new HashMap<>();
            result.put("facetType", normalizedFacetType);
            result.put("facetId", facetId);
            return result;
        }
        
        return null;
    }

    /**
     * Get stakeholders directly from the source object's facet table
     * This fetches live data from the object, not from cr_stakeholders
     * @param reference The change request reference (e.g., "Glossary 15")
     * @return List of stakeholder data
     */
    public List<Map<String, Object>> getStakeholdersFromSourceObject(String reference) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        Map<String, Object> parsed = parseReference(reference);
        if (parsed == null) {
            logger.warn("Could not parse reference: {}", reference);
            return stakeholders;
        }
        
        String facetType = (String) parsed.get("facetType");
        int facetId = (int) parsed.get("facetId");
        
        String tableName = FACET_STAKEHOLDER_TABLES.get(facetType);
        String idColumn = FACET_ID_COLUMNS.get(facetType);
        
        if (tableName == null || idColumn == null) {
            logger.warn("No stakeholder table mapping for facet type: {}", facetType);
            return stakeholders;
        }
        
        String objectXIpidColumn = getObjectXIpidColumn(facetType);
        
        // One row per person+role on this object (duplicate junction rows collapse).
        // MAX(roleAccepted) prefers a positive acceptance when multiple values exist.
        String sql = String.format("""
            SELECT
                personId,
                personName,
                roleId,
                roleName,
                orgUnitId,
                orgUnitName,
                roleAccepted
            FROM (
                SELECT
                    oxp.ipid as personId,
                    MAX(CONCAT(p.First_Name, ' ', p.Last_Name)) as personName,
                    oxp.roleID as roleId,
                    MAX(orl.primaryname) as roleName,
                    MAX(ou.ID) as orgUnitId,
                    MAX(ou.Name) as orgUnitName,
                    MAX(ra.Message) as roleAccepted
                FROM %s jt
                JOIN object_x_people oxp ON jt.%s = oxp.id
                JOIN people p ON oxp.ipid = p.ID
                LEFT JOIN object_role orl ON oxp.roleID = orl.ID
                LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
                LEFT JOIN roleaccepted ra ON oxp.AcceptedID = ra.ID
                WHERE jt.%s = ?
                GROUP BY oxp.ipid, oxp.roleID
            ) grouped
            ORDER BY roleName, personName
            """, tableName, objectXIpidColumn, idColumn);
        
        logger.debug("Fetching stakeholders for {} {} with SQL: {}", facetType, facetId, sql);
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, facetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    int personId = rs.getInt("personId");
                    int roleId = rs.getInt("roleId");
                    
                    stakeholder.put("personId", personId);
                    stakeholder.put("personName", rs.getString("personName"));
                    stakeholder.put("roleId", roleId);
                    stakeholder.put("roleName", rs.getString("roleName"));
                    stakeholder.put("orgUnitId", rs.getInt("orgUnitId"));
                    stakeholder.put("orgUnitName", rs.getString("orgUnitName"));
                    stakeholder.put("roleAccepted", rs.getString("roleAccepted"));
                    
                    // Validate role assignment for default roles
                    try {
                        DefaultStakeholderUtil.ValidationResult validation = 
                            DefaultStakeholderUtil.validateStakeholderRoleAssignment(conn, personId, roleId);
                        stakeholder.put("roleAssignmentValid", validation.isValid());
                        if (!validation.isValid() && validation.getWarningMessage() != null) {
                            stakeholder.put("roleAssignmentWarning", validation.getWarningMessage());
                        }
                    } catch (SQLException e) {
                        // If validation fails, assume valid to not break existing functionality
                        stakeholder.put("roleAssignmentValid", true);
                        logger.warn("Error validating role assignment for user {} role {}: {}", 
                            personId, roleId, e.getMessage());
                    }
                    
                    stakeholders.add(stakeholder);
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching stakeholders from source object {} {}: {}", facetType, facetId, e.getMessage());
            throw e;
        }
        
        logger.info("Found {} stakeholders for {} {}", stakeholders.size(), facetType, facetId);
        return stakeholders;
    }

    /**
     * Get stakeholder community for an object (stakeholders from related objects)
     * Delegates to the appropriate facet's stakeholder community servlet
     * @param facetType The type of the source object
     * @param facetId The ID of the source object
     * @return List of stakeholder community data
     */
    public List<Map<String, Object>> getStakeholderCommunityForObject(String facetType, int facetId) throws SQLException {
        List<Map<String, Object>> communityStakeholders = new ArrayList<>();
        
        logger.info("Fetching stakeholder community for {} {}", facetType, facetId);
        
        // Route to the appropriate facet's stakeholder community method
        switch (facetType.toLowerCase()) {
            case "system":
                communityStakeholders = getSystemStakeholderCommunity(facetId);
                break;
            case "dataset":
            case "data-set":
                communityStakeholders = getDatasetStakeholderCommunity(facetId);
                break;
            case "process":
                communityStakeholders = getProcessStakeholderCommunity(facetId);
                break;
            case "product":
                communityStakeholders = getProductStakeholderCommunity(facetId);
                break;
            case "project":
                communityStakeholders = getProjectStakeholderCommunity(facetId);
                break;
            case "policy":
                communityStakeholders = getPolicyStakeholderCommunity(facetId);
                break;
            case "glossary":
                logger.info("Calling getGlossaryStakeholderCommunity for glossary {}", facetId);
                communityStakeholders = getGlossaryStakeholderCommunity(facetId);
                logger.info("getGlossaryStakeholderCommunity returned {} stakeholders", communityStakeholders.size());
                break;
            case "business-area":
            case "businessarea":
                communityStakeholders = getBusinessAreaStakeholderCommunity(facetId);
                break;
            case "client":
                communityStakeholders = getClientStakeholderCommunity(facetId);
                break;
            case "legal-entity":
            case "legalentity":
                communityStakeholders = getLegalEntityStakeholderCommunity(facetId);
                break;
            case "regulation":
                communityStakeholders = getRegulationStakeholderCommunity(facetId);
                break;
            default:
                logger.warn("No stakeholder community implementation for facet type: {}", facetType);
        }
        
        logger.info("Found {} community stakeholders for {} {}", communityStakeholders.size(), facetType, facetId);
        return communityStakeholders;
    }
    
    /**
     * Get stakeholder community for a System
     * This matches SystemStakeholderCommunity.java exactly
     */
    private List<Map<String, Object>> getSystemStakeholderCommunity(int systemId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        // Query to get stakeholders from related objects (matching SystemStakeholderCommunity exactly)
        String sql = """
            SELECT DISTINCT
                'Process' AS objectType,
                pr.primaryname AS objectName,
                pr.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM process_x_system pxs
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxs.process_id
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxs.process_id
            WHERE pxs.system_id = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Product' AS objectType,
                prod.primaryname AS objectName,
                prod.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM product_x_system pxs
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxs.Product_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxs.Product_ID
            WHERE pxs.System_ID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Project' AS objectType,
                prj.primaryname AS objectName,
                prj.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM project_x_system pxs
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxs.projectid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxs.projectid
            WHERE pxs.systemid = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Client' AS objectType,
                c.PrimaryName AS objectName,
                c.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM client_x_system cxs
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxs.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxs.Client_ID
            WHERE cxs.System_ID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Glossary' AS objectType,
                g.Name AS objectName,
                g.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM glossary_x_system gxs
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = gxs.GlossaryID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = gxs.GlossaryID
            WHERE gxs.SystemID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Business Area' AS objectType,
                ba.PrimaryName AS objectName,
                ba.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM businessarea_x_system bxs
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = bxs.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = bxs.BusinessArea_ID
            WHERE bxs.System_ID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Capability' AS objectType,
                cap.PrimaryName AS objectName,
                cap.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM capability_x_system cxs
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxs.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxs.Capability_ID
            WHERE cxs.System_ID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Policy' AS objectType,
                pol.PrimaryName AS objectName,
                pol.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_system pxs
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxs.Policy_ID
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxs.Policy_ID
            WHERE pxs.System_ID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Legal' AS objectType,
                le.ShortName AS objectName,
                le.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM system_x_legal sxl
            JOIN legal_x_objectxpeople lxop ON lxop.Legal_ID = sxl.Legal_ID
            JOIN object_x_people oxp ON oxp.id = lxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN legal le ON le.id = sxl.Legal_ID
            WHERE sxl.System_ID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, systemId);  // Process
            stmt.setInt(2, systemId);  // Product
            stmt.setInt(3, systemId);  // Project
            stmt.setInt(4, systemId);  // Client
            stmt.setInt(5, systemId);  // Glossary
            stmt.setInt(6, systemId);  // Business Area
            stmt.setInt(7, systemId);  // Capability
            stmt.setInt(8, systemId);  // Policy
            stmt.setInt(9, systemId);  // Legal Entity
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Dataset
     */
    private List<Map<String, Object>> getDatasetStakeholderCommunity(int datasetId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM dataset_x_system dxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = dxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = dxs.System_ID
            WHERE dxs.Dataset_ID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Process' AS objectType,
                pr.primaryname AS objectName,
                pr.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM dataset_x_process dxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = dxp.process_id
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = dxp.process_id
            WHERE dxp.dataset_id = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, datasetId);
            stmt.setInt(2, datasetId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Process
     */
    private List<Map<String, Object>> getProcessStakeholderCommunity(int processId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM process_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.system_id
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.system_id
            WHERE pxs.process_id = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Dataset' AS objectType,
                d.PrimaryName AS objectName,
                d.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM dataset_x_process dxp
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = dxp.dataset_id
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset d ON d.id = dxp.dataset_id
            WHERE dxp.process_id = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, processId);
            stmt.setInt(2, processId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Product
     */
    private List<Map<String, Object>> getProductStakeholderCommunity(int productId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM product_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.System_ID
            WHERE pxs.Product_ID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, productId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Project
     */
    private List<Map<String, Object>> getProjectStakeholderCommunity(int projectId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM project_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.systemid
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.systemid
            WHERE pxs.projectid = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, projectId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Policy
     */
    private List<Map<String, Object>> getPolicyStakeholderCommunity(int policyId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.System_ID
            WHERE pxs.Policy_ID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, policyId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Glossary
     */
    private List<Map<String, Object>> getGlossaryStakeholderCommunity(int glossaryId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        logger.debug("Fetching stakeholder community for glossary {}", glossaryId);
        
        // First, check if glossary has any system relationships
        String checkSql = "SELECT COUNT(*) as count FROM glossary_x_system WHERE GlossaryID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            
            checkStmt.setInt(1, glossaryId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    int relationshipCount = rs.getInt("count");
                    logger.debug("Glossary {} has {} system relationships", glossaryId, relationshipCount);
                }
            }
        }
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM glossary_x_system gxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = gxs.SystemID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = gxs.SystemID
            WHERE gxs.GlossaryID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Glossary' AS objectType,
                g.PrimaryName AS objectName,
                g.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM glossary_x_glossary gxg
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = gxg.TargetGlossaryID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = gxg.TargetGlossaryID
            WHERE gxg.SourceGlossaryID = ?
            
            UNION ALL
            
            SELECT DISTINCT
                'Glossary' AS objectType,
                g.PrimaryName AS objectName,
                g.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM glossary_x_glossary gxg
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = gxg.SourceGlossaryID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = gxg.SourceGlossaryID
            WHERE gxg.TargetGlossaryID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, glossaryId);  // For systems related to this glossary
            stmt.setInt(2, glossaryId);  // For glossaries where this is the source
            stmt.setInt(3, glossaryId);  // For glossaries where this is the target
            logger.debug("Executing glossary stakeholder community query for glossary {}", glossaryId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                    
                    logger.debug("Found stakeholder: {} from system {}", rs.getString("name"), rs.getString("objectName"));
                }
            }
        }
        
        logger.debug("Found {} stakeholders for glossary {} community", stakeholders.size(), glossaryId);
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Business Area
     */
    private List<Map<String, Object>> getBusinessAreaStakeholderCommunity(int businessAreaId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM businessarea_x_system bxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = bxs.SystemID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = bxs.SystemID
            WHERE bxs.BusinessAreaID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, businessAreaId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Client
     */
    private List<Map<String, Object>> getClientStakeholderCommunity(int clientId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM client_x_system cxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = cxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = cxs.System_ID
            WHERE cxs.Client_ID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, clientId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }
    
    /**
     * Get stakeholder community for a Legal Entity
     */
    private List<Map<String, Object>> getLegalEntityStakeholderCommunity(int legalEntityId) throws SQLException {
        // Legal entities typically don't have many direct relationships in most schemas
        // Return empty list for now - can be enhanced based on specific relationships
        return new ArrayList<>();
    }
    
    /**
     * Get stakeholder community for a Regulation
     */
    private List<Map<String, Object>> getRegulationStakeholderCommunity(int regulationId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM regulation_x_system rxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = rxs.SystemID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = rxs.SystemID
            WHERE rxs.RegulationID = ?
            
            ORDER BY objectType, objectName, role, name
            """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, regulationId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    stakeholder.put("objectId", rs.getInt("objectId"));
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }

    /**
     * Copy stakeholders from a source object to a change request
     * @param facetType The type of facet (e.g., "business-area", "glossary")
     * @param facetId The ID of the source object
     * @param changeRequestId The ID of the change request
     * @param userId The ID of the user performing the action
     * @return Number of stakeholders copied
     */
    public int copyStakeholdersToChangeRequest(String facetType, int facetId, int changeRequestId, int userId) throws SQLException {
        String normalizedFacetType = facetType.toLowerCase().replace(" ", "-");
        String junctionTable = FACET_STAKEHOLDER_TABLES.get(normalizedFacetType);
        String idColumn = FACET_ID_COLUMNS.get(normalizedFacetType);
        String objectXIpidColumn = getObjectXIpidColumn(normalizedFacetType);
        
        if (junctionTable == null || idColumn == null) {
            logger.warn("Unknown facet type for stakeholder copy: {}", facetType);
            return 0;
        }
        
        logger.info("Copying stakeholders from {} (ID: {}) to CR {} using table {} column {} objectXIpid {}", 
                   facetType, facetId, changeRequestId, junctionTable, idColumn, objectXIpidColumn);
        
        // First, get the stakeholders from the source object
        // We filter by the specific object ID first, then group by person+role
        // This ensures we only get stakeholders that are actually linked to this specific object
        // and we get one stakeholder record per unique person+role combination for this object
        // We explicitly include jt.%s in the SELECT and GROUP BY to ensure we only get stakeholders for this object
        String selectSql = String.format(
            "SELECT jt.%s AS object_id, oxp.ipid AS user_id, oxp.roleID AS role_id " +
            "FROM %s jt " +
            "INNER JOIN object_x_people oxp ON jt.%s = oxp.id " +
            "WHERE jt.%s = ? " +
            "GROUP BY jt.%s, oxp.ipid, oxp.roleID",
            idColumn, junctionTable, objectXIpidColumn, idColumn, idColumn
        );
        
        logger.info("Executing SQL: {} with facetId={}", selectSql, facetId);
        
        // First, verify the query will only return stakeholders for this specific object
        // by checking the junction table directly
        String verifySql = String.format("SELECT COUNT(*) as count FROM %s WHERE %s = ?", junctionTable, idColumn);
        try (Connection verifyConn = DatabaseConnection.getConnection();
             PreparedStatement verifyStmt = verifyConn.prepareStatement(verifySql)) {
            verifyStmt.setInt(1, facetId);
            try (ResultSet verifyRs = verifyStmt.executeQuery()) {
                if (verifyRs.next()) {
                    int junctionRowCount = verifyRs.getInt("count");
                    logger.info("Found {} junction table rows for {} {} in table {}", junctionRowCount, facetType, facetId, junctionTable);
                }
            }
        }
        
        List<int[]> stakeholders = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
            
            selectStmt.setInt(1, facetId);
            
            logger.debug("Executing query with parameter: facetId={}", facetId);
            
            try (ResultSet rs = selectStmt.executeQuery()) {
                while (rs.next()) {
                    // Verify that the object_id matches the expected facetId (safety check)
                    int returnedObjectId = rs.getInt("object_id");
                    if (returnedObjectId != facetId) {
                        logger.warn("Query returned stakeholder for wrong object! Expected {} but got {}", facetId, returnedObjectId);
                        continue; // Skip this row
                    }
                    
                    int personId = rs.getInt("user_id");
                    // Only require personId to be valid; roleId can be null
                    if (!rs.wasNull() && personId > 0) {
                        int roleId = rs.getInt("role_id");
                        // Store roleId as -1 if null to indicate it should be NULL in DB
                        int roleIdValue = rs.wasNull() ? -1 : roleId;
                        stakeholders.add(new int[]{personId, roleIdValue});
                        logger.debug("Found unique stakeholder for {} {}: personId={}, roleId={}", 
                                   facetType, facetId, personId, roleIdValue == -1 ? "NULL" : roleIdValue);
                    }
                }
            }
            
            logger.info("Found {} unique stakeholder records (grouped by person+role) to copy from {} {}", 
                       stakeholders.size(), facetType, facetId);
            
            if (stakeholders.isEmpty()) {
                logger.info("No stakeholders found for {} {}, nothing to copy", facetType, facetId);
                return 0;
            }
            
            // Each row in the junction table represents a unique stakeholder assignment
            // We should copy all of them, even if they have the same person+role combination
            // This matches the source object's stakeholder structure
            
            // Before inserting, check what stakeholders already exist for this CR
            // to avoid creating duplicates
            String checkExistingSql = "SELECT User_ID, Object_Role_ID FROM cr_stakeholders WHERE CR_ID = ?";
            Set<String> existingStakeholders = new HashSet<>();
            
            try (PreparedStatement checkStmt = conn.prepareStatement(checkExistingSql)) {
                checkStmt.setInt(1, changeRequestId);
                try (ResultSet checkRs = checkStmt.executeQuery()) {
                    while (checkRs.next()) {
                        int existingUserId = checkRs.getInt("User_ID");
                        int existingRoleId = checkRs.getInt("Object_Role_ID");
                        String key = existingUserId + "_" + (checkRs.wasNull() ? "-1" : existingRoleId);
                        existingStakeholders.add(key);
                    }
                }
            }
            
            logger.info("Found {} existing stakeholders in CR {}", existingStakeholders.size(), changeRequestId);
            
            // Insert stakeholders into cr_stakeholders
            // We only insert unique person+role combinations to match how the source object displays stakeholders
            String insertSql = "INSERT INTO cr_stakeholders (User_ID, Object_Role_ID, LastUser_Change, Created_At, Updated_At, CR_ID) " +
                              "VALUES (?, ?, ?, ?, ?, ?)";
            
            int copiedCount = 0;
            int skippedCount = 0;
            LocalDateTime now = LocalDateTime.now();
            
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (int[] stakeholder : stakeholders) {
                    String key = stakeholder[0] + "_" + stakeholder[1];
                    
                    // Skip if this person+role combination already exists for this CR
                    if (existingStakeholders.contains(key)) {
                        skippedCount++;
                        logger.debug("Stakeholder already exists, skipping: personId={}, roleId={}", 
                                   stakeholder[0], stakeholder[1] == -1 ? "NULL" : stakeholder[1]);
                        continue;
                    }
                    
                    insertStmt.setInt(1, stakeholder[0]); // User_ID
                    
                    // Handle null roleId (stored as -1)
                    if (stakeholder[1] == -1) {
                        insertStmt.setNull(2, Types.INTEGER); // Object_Role_ID
                    } else {
                        insertStmt.setInt(2, stakeholder[1]); // Object_Role_ID
                    }
                    
                    insertStmt.setInt(3, userId);         // LastUser_Change
                    insertStmt.setTimestamp(4, Timestamp.valueOf(now)); // Created_At
                    insertStmt.setTimestamp(5, Timestamp.valueOf(now)); // Updated_At
                    insertStmt.setInt(6, changeRequestId); // CR_ID
                    
                    try {
                        int rowsAffected = insertStmt.executeUpdate();
                        if (rowsAffected > 0) {
                            copiedCount++;
                            existingStakeholders.add(key); // Track to avoid duplicates in same batch
                            logger.debug("Copied stakeholder: personId={}, roleId={}", 
                                       stakeholder[0], stakeholder[1] == -1 ? "NULL" : stakeholder[1]);
                        }
                    } catch (SQLException e) {
                        // If it's a duplicate key error, that's okay - stakeholder already exists
                        // This can happen if the method is called multiple times
                        if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                            logger.debug("Stakeholder already exists (duplicate key): personId={}, roleId={}", 
                                       stakeholder[0], stakeholder[1] == -1 ? "NULL" : stakeholder[1]);
                        } else {
                            logger.warn("Failed to copy stakeholder (user: {}, role: {}): {}", 
                                       stakeholder[0], stakeholder[1] == -1 ? "NULL" : stakeholder[1], e.getMessage());
                        }
                    }
                }
            }
            
            if (skippedCount > 0) {
                logger.info("Skipped {} stakeholders that already existed in CR {}", skippedCount, changeRequestId);
            }
            
            logger.info("Successfully copied {} stakeholders to CR {}", copiedCount, changeRequestId);
            
            // Clean up any duplicates that may exist
            int duplicatesRemoved = removeDuplicateStakeholders(changeRequestId);
            if (duplicatesRemoved > 0) {
                logger.info("Cleaned up {} duplicate stakeholders from CR {}", duplicatesRemoved, changeRequestId);
            }
            
            return copiedCount;
            
        } catch (SQLException e) {
            logger.error("Error copying stakeholders: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Sync stakeholders from a source object to all its change requests
     * This should be called when stakeholders of an object are modified
     * @param facetType The type of facet
     * @param facetId The ID of the source object
     * @param userId The ID of the user performing the action
     * @return Number of change requests updated
     */
    public int syncStakeholdersToChangeRequests(String facetType, int facetId, int userId) throws SQLException {
        // Build the reference string to find related change requests
        String reference = buildReference(facetType, facetId);
        
        logger.info("Syncing stakeholders for {} to all CRs with reference: {}", facetType + " " + facetId, reference);
        
        // Find all change requests for this object
        String findCRsSql = "SELECT ID FROM changerequest WHERE Reference = ? AND Deleted_At IS NULL";
        List<Integer> changeRequestIds = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(findCRsSql)) {
            
            stmt.setString(1, reference);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    changeRequestIds.add(rs.getInt("ID"));
                }
            }
        }
        
        logger.info("Found {} change requests to sync stakeholders for", changeRequestIds.size());
        
        if (changeRequestIds.isEmpty()) {
            return 0;
        }
        
        // For each change request, delete existing stakeholders and re-copy
        int updatedCount = 0;
        for (Integer crId : changeRequestIds) {
            try {
                // Delete existing stakeholders for this CR
                deleteStakeholdersForChangeRequest(crId);
                
                // Copy fresh stakeholders from source object
                copyStakeholdersToChangeRequest(facetType, facetId, crId, userId);
                updatedCount++;
            } catch (SQLException e) {
                logger.error("Failed to sync stakeholders for CR {}: {}", crId, e.getMessage());
            }
        }
        
        return updatedCount;
    }

    /**
     * Delete all stakeholders for a change request
     */
    public void deleteStakeholdersForChangeRequest(int changeRequestId) throws SQLException {
        String sql = "DELETE FROM cr_stakeholders WHERE CR_ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            int deleted = stmt.executeUpdate();
            logger.info("Deleted {} stakeholders from CR {}", deleted, changeRequestId);
        }
    }

    /**
     * Remove duplicate stakeholders for a change request
     * Keeps only the first occurrence (lowest ID) of each unique User_ID + Object_Role_ID combination
     */
    public int removeDuplicateStakeholders(int changeRequestId) throws SQLException {
        // Find all IDs to keep (minimum ID for each User_ID + Object_Role_ID combination)
        String findKeepIdsSql = "SELECT MIN(ID) as min_id " +
                               "FROM cr_stakeholders " +
                               "WHERE CR_ID = ? " +
                               "GROUP BY User_ID, COALESCE(Object_Role_ID, -1)";
        
        Set<Integer> keepIds = new HashSet<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(findKeepIdsSql)) {
            
            stmt.setInt(1, changeRequestId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    keepIds.add(rs.getInt("min_id"));
                }
            }
            
            if (keepIds.isEmpty()) {
                logger.debug("No stakeholders found for CR {}", changeRequestId);
                return 0;
            }
            
            // Get all stakeholder IDs for this CR
            String getAllIdsSql = "SELECT ID FROM cr_stakeholders WHERE CR_ID = ?";
            List<Integer> allIds = new ArrayList<>();
            
            try (PreparedStatement getAllStmt = conn.prepareStatement(getAllIdsSql)) {
                getAllStmt.setInt(1, changeRequestId);
                try (ResultSet rs = getAllStmt.executeQuery()) {
                    while (rs.next()) {
                        allIds.add(rs.getInt("ID"));
                    }
                }
            }
            
            // Find IDs to delete (all IDs that are not in keepIds)
            List<Integer> idsToDelete = new ArrayList<>();
            for (Integer id : allIds) {
                if (!keepIds.contains(id)) {
                    idsToDelete.add(id);
                }
            }
            
            if (idsToDelete.isEmpty()) {
                logger.debug("No duplicates found for CR {}", changeRequestId);
                return 0;
            }
            
            // Delete duplicates
            String deleteSql = "DELETE FROM cr_stakeholders WHERE ID = ?";
            int deletedCount = 0;
            
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                for (Integer id : idsToDelete) {
                    deleteStmt.setInt(1, id);
                    deletedCount += deleteStmt.executeUpdate();
                }
            }
            
            if (deletedCount > 0) {
                logger.info("Removed {} duplicate stakeholders from CR {}", deletedCount, changeRequestId);
            }
            
            return deletedCount;
        }
    }

    /**
     * Get stakeholders for a change request
     */
    public List<Map<String, Object>> getStakeholdersForChangeRequest(int changeRequestId) throws SQLException {
        // Role accepted: resolve via a scalar subquery. Joining object_x_people only on
        // (ipid, roleID) matches every assignment of that role for the person app-wide and
        // multiplies cr_stakeholders rows — one row per crs.ID only.
        String sql = "SELECT crs.ID, crs.User_ID, crs.Object_Role_ID, " +
                    "CONCAT(p.First_Name, ' ', p.Last_Name) AS user_name, " +
                    "p.Org_Unit_ID, ou.Name AS org_unit_name, " +
                    "orl.primaryname AS role_name, " +
                    "(SELECT ra_inner.Message FROM object_x_people oxp_inner " +
                    " LEFT JOIN roleaccepted ra_inner ON oxp_inner.AcceptedID = ra_inner.ID " +
                    " WHERE oxp_inner.ipid = crs.User_ID AND oxp_inner.roleID = crs.Object_Role_ID " +
                    " ORDER BY oxp_inner.ID ASC LIMIT 1) AS accepted_status " +
                    "FROM cr_stakeholders crs " +
                    "LEFT JOIN people p ON crs.User_ID = p.ID " +
                    "LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID " +
                    "LEFT JOIN object_role orl ON crs.Object_Role_ID = orl.id " +
                    "WHERE crs.CR_ID = ?";
        
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, changeRequestId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("id", rs.getInt("ID"));
                    stakeholder.put("userId", rs.getInt("User_ID"));
                    stakeholder.put("roleId", rs.getInt("Object_Role_ID"));
                    stakeholder.put("userName", rs.getString("user_name"));
                    stakeholder.put("orgUnitId", rs.getObject("Org_Unit_ID"));
                    stakeholder.put("orgUnitName", rs.getString("org_unit_name"));
                    stakeholder.put("roleName", rs.getString("role_name"));
                    stakeholder.put("acceptedStatus", rs.getString("accepted_status"));
                    stakeholders.add(stakeholder);
                }
            }
        }
        
        return stakeholders;
    }

    /**
     * Build reference string from facet type and ID
     */
    private String buildReference(String facetType, int facetId) {
        String normalizedType = facetType.toLowerCase().replace("-", " ");
        String[] words = normalizedType.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(" ");
            }
            if (words[i].length() > 0) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
        }
        
        result.append(" ").append(facetId);
        return result.toString();
    }
}

