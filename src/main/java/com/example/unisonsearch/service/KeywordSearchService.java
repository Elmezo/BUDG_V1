package com.example.unisonsearch.service;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Service for searching objects by keyword/name
 */
public class KeywordSearchService {
    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchService.class);
    
    /**
     * Search for objects in a facet by keyword.
     * Binds the keyword pattern to every ? placeholder in the SQL so any number
     * of searchable columns is supported without hard-coding a fixed parameter count.
     */
    public Set<Integer> searchByKeyword(String facet, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new HashSet<>();
        }
        
        // Check if keyword is a numeric ID
        try {
            int id = Integer.parseInt(keyword.trim());
            return Set.of(id);
        } catch (NumberFormatException e) {
            // Not a number, proceed with text search
        }
        
        String keywordPattern = "%" + keyword.trim() + "%";
        Set<Integer> results = new HashSet<>();
        
        String sql = getSearchSQL(facet);
        if (sql == null) {
            logger.warn("No search SQL defined for facet: {}", facet);
            return results;
        }
        
        // Count placeholders so any number of searchable columns is supported
        int paramCount = (int) sql.chars().filter(c -> c == '?').count();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 1; i <= paramCount; i++) {
                ps.setString(i, keywordPattern);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getInt("ID"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching {} by keyword '{}': {}", facet, keyword, e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Normalize facet name to standard format for switch matching
     * Handles: ATTRIBUTE, Attribute, attribute, ATTRIBUTES, attributes -> "Attribute"
     * Handles: ORG_UNIT, OrgUnit, org-unit -> "OrgUnit"
     * Handles: CHANGE_REQUEST, ChangeRequest, change-request -> "ChangeRequest"
     */
    private String normalizeFacetName(String facet) {
        if (facet == null || facet.trim().isEmpty()) {
            return null;
        }
        
        String normalized = facet.trim().toLowerCase().replace("_", "").replace("-", "");
        
        // Handle plural forms and special cases
        if (normalized.endsWith("s") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        // Map to standard names
        return switch (normalized) {
            case "dataset" -> "Dataset";
            case "system" -> "System";
            case "attribute", "attr" -> "Attribute";
            case "glossary" -> "Glossary";
            case "interface" -> "Interface";
            case "person", "people" -> "Person";
            case "orgunit" -> "OrgUnit";
            case "project" -> "Project";
            case "process" -> "Process";
            case "policy" -> "Policy";
            case "changerequest", "changerequests" -> "ChangeRequest";
            case "legalentity", "legal" -> "LegalEntity";
            case "businessarea", "business" -> "BusinessArea";
            case "regulatorytheme" -> "RegulatoryTheme";
            case "capability" -> "Capability";
            case "client" -> "Client";
            case "product" -> "Product";
            case "geography" -> "Geography";
            case "regulation" -> "Regulation";
            case "regulator" -> "Regulator";
            case "committee" -> "Committee";
            case "role" -> "Role";
            default -> {
                // Default: capitalize first letter, lowercase rest
                if (normalized.isEmpty()) {
                    yield null;
                }
                yield normalized.substring(0, 1).toUpperCase() + 
                      (normalized.length() > 1 ? normalized.substring(1) : "");
            }
        };
    }
    
    /**
     * Get search SQL for a facet.
     * Each WHERE clause ORs together all user-visible searchable columns.
     * The number of ? placeholders matches the number of columns; searchByKeyword
     * binds each one to the same keyword pattern automatically.
     */
    private String getSearchSQL(String facet) {
        String normalizedFacet = normalizeFacetName(facet);
        if (normalizedFacet == null) {
            return null;
        }
        
        switch (normalizedFacet) {
            case "Dataset":
                return "SELECT DISTINCT d.ID " +
                       "FROM dataset d " +
                       "WHERE (d.PrimaryName LIKE ? OR d.RefNumber LIKE ? OR d.definition LIKE ? OR d.Usage LIKE ?) " +
                       "AND d.DeletedDatetime IS NULL";
            case "Attribute":
                return "SELECT DISTINCT a.ID " +
                       "FROM attribute a " +
                       "WHERE (a.PrimaryName LIKE ? OR a.RefNumber LIKE ? OR a.Definition LIKE ? OR a.Business_Logic LIKE ?) " +
                       "AND (a.DeletedDatetime IS NULL OR a.DeletedDatetime = '')";
            case "System":
                return "SELECT DISTINCT s.id AS ID " +
                       "FROM system s " +
                       "WHERE (s.Name LIKE ? OR s.Long_Name LIKE ? OR s.AssetID LIKE ? OR s.Description LIKE ?) " +
                       "AND s.Deleted_datetime IS NULL";
            case "Glossary":
                return "SELECT DISTINCT g.ID " +
                       "FROM glossary g " +
                       "LEFT JOIN glossary_alias_names ga ON ga.glossary_id = g.ID " +
                       "WHERE (g.Name LIKE ? OR g.Ref_Number LIKE ? OR g.Description LIKE ? OR g.Business_Logic LIKE ? OR ga.Name LIKE ?) " +
                       "AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '')";
            case "Policy":
                return "SELECT DISTINCT po.ID " +
                       "FROM policy po " +
                       "WHERE (po.PrimaryName LIKE ? OR po.RefNumber LIKE ? OR po.Description LIKE ?) " +
                       "AND (po.DeletedDatetime IS NULL OR po.DeletedDatetime = '')";
            case "Process":
                return "SELECT DISTINCT pr.id AS ID " +
                       "FROM process pr " +
                       "WHERE (pr.primaryname LIKE ? OR pr.refnumber LIKE ? OR pr.description LIKE ?) " +
                       "AND (pr.DeletedDatetime IS NULL OR pr.DeletedDatetime = '')";
            case "Project":
                return "SELECT DISTINCT prj.id AS ID " +
                       "FROM project prj " +
                       "WHERE (prj.primaryname LIKE ? OR prj.refnumber LIKE ? OR prj.description LIKE ?) " +
                       "AND (prj.DeletedDatetime IS NULL OR prj.DeletedDatetime = '')";
            case "Person":
                return "SELECT DISTINCT p.ID " +
                       "FROM people p " +
                       "WHERE (CONCAT(p.First_Name, ' ', p.Last_Name) LIKE ? OR p.First_Name LIKE ? OR p.Last_Name LIKE ? OR p.Email LIKE ?) " +
                       "AND (p.Deleted_date IS NULL OR p.Deleted_date = '')";
            case "ChangeRequest":
                return "SELECT DISTINCT cr.ID " +
                       "FROM changerequest cr " +
                       "WHERE (cr.PrimaryName LIKE ? OR cr.Reference LIKE ? OR cr.Summary LIKE ?) " +
                       "AND cr.Deleted_At IS NULL";
            case "Committee":
                return "SELECT DISTINCT c.ID " +
                       "FROM committee c " +
                       "WHERE (c.PrimaryName LIKE ? OR c.RefNumber LIKE ? OR c.Description LIKE ?) " +
                       "AND (c.DeleteDatetime IS NULL OR c.DeleteDatetime = '')";
            case "Role":
                return "SELECT DISTINCT orl.ID " +
                       "FROM object_role orl " +
                       "WHERE (orl.primaryname LIKE ? OR orl.description LIKE ?) " +
                       "AND (orl.DeletedDatetime IS NULL OR orl.DeletedDatetime = '')";
            case "Interface":
                return "SELECT DISTINCT i.id AS ID " +
                       "FROM interface i " +
                       "WHERE (i.Name LIKE ? OR i.Ref_number LIKE ? OR i.Description LIKE ?) " +
                       "AND (i.deleted_datetime IS NULL OR i.deleted_datetime = '')";
            case "BusinessArea":
                return "SELECT DISTINCT ba.ID " +
                       "FROM business_area ba " +
                       "WHERE (ba.PrimaryName LIKE ? OR ba.Description LIKE ?) " +
                       "AND (ba.deletedatetime IS NULL OR ba.deletedatetime = '')";
            case "LegalEntity":
                return "SELECT DISTINCT l.ID " +
                       "FROM legal l " +
                       "WHERE (l.ShortName LIKE ? OR l.LongName LIKE ? OR l.Description LIKE ?) " +
                       "AND (l.DeleteDatetime IS NULL OR l.DeleteDatetime = '')";
            case "OrgUnit":
                return "SELECT DISTINCT ou.ID " +
                       "FROM org_unit ou " +
                       "WHERE (ou.Name LIKE ? OR ou.Reference LIKE ? OR ou.Description LIKE ?) " +
                       "AND (ou.deleted_Date IS NULL OR ou.deleted_Date = '')";
            case "Capability":
                return "SELECT DISTINCT c.ID " +
                       "FROM capability c " +
                       "WHERE (c.PrimaryName LIKE ? OR c.RefNumber LIKE ? OR c.Description LIKE ?) " +
                       "AND (c.DeletedDatetime IS NULL OR c.DeletedDatetime = '')";
            case "Client":
                return "SELECT DISTINCT c.ID " +
                       "FROM client c " +
                       "WHERE (c.PrimaryName LIKE ? OR c.LongName LIKE ? OR c.Description LIKE ?) " +
                       "AND (c.DeleteDatetime IS NULL OR c.DeleteDatetime = '')";
            case "Product":
                return "SELECT DISTINCT prd.id AS ID " +
                       "FROM product prd " +
                       "WHERE (prd.primaryname LIKE ? OR prd.longname LIKE ? OR prd.description LIKE ?) " +
                       "AND (prd.DeletedDatetime IS NULL OR prd.DeletedDatetime = '')";
            case "Geography":
                return "SELECT DISTINCT g.ID " +
                       "FROM geography g " +
                       "WHERE (g.PrimaryName LIKE ? OR g.Description LIKE ?) " +
                       "AND (g.DeletedDatetime IS NULL OR g.DeletedDatetime = '')";
            case "Regulation":
                return "SELECT DISTINCT r.ID " +
                       "FROM regulation r " +
                       "WHERE (r.primaryName LIKE ? OR r.RefNumber LIKE ? OR r.ShortName LIKE ? OR r.Description LIKE ?) " +
                       "AND (r.DeletedDatetime IS NULL OR r.DeletedDatetime = '')";
            case "Regulator":
                return "SELECT DISTINCT reg.ID " +
                       "FROM regulator reg " +
                       "WHERE (reg.PrimaryName LIKE ? OR reg.ShortName LIKE ? OR reg.Description LIKE ?) " +
                       "AND (reg.DeletedDatetime IS NULL OR reg.DeletedDatetime = '')";
            case "RegulatoryTheme":
                return "SELECT DISTINCT rt.ID " +
                       "FROM regulatorytheme rt " +
                       "WHERE (rt.PrimaryName LIKE ? OR rt.RefNumber LIKE ? OR rt.ShortName LIKE ? OR rt.Description LIKE ?) " +
                       "AND (rt.DeletedDatetime IS NULL OR rt.DeletedDatetime = '')";
            default:
                return null;
        }
    }
    
    /**
     * Search by exact name match (for RefNumber or exact name)
     */
    public Set<Integer> searchByExactName(String facet, String name) {
        if (name == null || name.trim().isEmpty()) {
            return new HashSet<>();
        }
        
        Set<Integer> results = new HashSet<>();
        String sql = getExactSearchSQL(facet);
        if (sql == null) {
            return searchByKeyword(facet, name); // Fallback to LIKE search
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, name.trim());
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getInt("ID"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching {} by exact name '{}': {}", facet, name, e.getMessage());
        }
        
        return results;
    }
    
    /**
     * Get exact search SQL (for RefNumber or exact name match)
     * Case-insensitive matching
     */
    private String getExactSearchSQL(String facet) {
        String normalizedFacet = normalizeFacetName(facet);
        if (normalizedFacet == null) {
            return null;
        }
        
        switch (normalizedFacet) {
            case "Dataset":
                return "SELECT DISTINCT d.ID " +
                       "FROM dataset d " +
                       "WHERE (d.PrimaryName = ? OR d.RefNumber = ?) " +
                       "AND d.DeletedDatetime IS NULL";
            case "Attribute":
                return "SELECT DISTINCT a.ID " +
                       "FROM attribute a " +
                       "WHERE (a.PrimaryName = ? OR a.RefNumber = ?) " +
                       "AND (a.DeletedDatetime IS NULL OR a.DeletedDatetime = '')";
            default:
                return null;
        }
    }
    
    /**
     * Get object name by facet and ID
     */
    public String getObjectName(String facet, int objectId) {
        String sql = getNameSQL(facet);
        if (sql == null) {
            return "Unknown";
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting object name for {} ID {}: {}", facet, objectId, e.getMessage());
        }
        
        return "Unknown";
    }
    
    /**
     * Get SQL to fetch object name by ID
     * Case-insensitive matching
     */
    private String getNameSQL(String facet) {
        String normalizedFacet = normalizeFacetName(facet);
        if (normalizedFacet == null) {
            return null;
        }
        
        switch (normalizedFacet) {
            case "Dataset":
                return "SELECT d.PrimaryName AS Name " +
                       "FROM dataset d " +
                       "WHERE d.ID = ? AND d.DeletedDatetime IS NULL";
            case "System":
                return "SELECT s.Name " +
                       "FROM system s " +
                       "WHERE s.id = ? AND s.Deleted_datetime IS NULL";
            case "Attribute":
                return "SELECT a.PrimaryName AS Name " +
                       "FROM attribute a " +
                       "WHERE a.ID = ? AND (a.DeletedDatetime IS NULL OR a.DeletedDatetime = '')";
            case "Glossary":
                return "SELECT g.Name " +
                       "FROM glossary g " +
                       "WHERE g.ID = ? AND (g.Deleted_datetime IS NULL OR g.Deleted_datetime = '')";
            case "Interface":
                return "SELECT i.Name " +
                       "FROM interface i " +
                       "WHERE i.id = ? AND (i.deleted_datetime IS NULL OR i.deleted_datetime = '')";
            case "Person":
                return "SELECT CONCAT(p.First_Name, ' ', p.Last_Name) AS Name " +
                       "FROM people p " +
                       "WHERE p.ID = ? AND (p.Deleted_date IS NULL OR p.Deleted_date = '')";
            case "OrgUnit":
                return "SELECT ou.Name " +
                       "FROM org_unit ou " +
                       "WHERE ou.ID = ? AND (ou.deleted_Date IS NULL OR ou.deleted_Date = '')";
            case "Project":
                return "SELECT prj.primaryname AS Name " +
                       "FROM project prj " +
                       "WHERE prj.id = ? AND (prj.DeletedDatetime IS NULL OR prj.DeletedDatetime = '')";
            case "Process":
                return "SELECT pr.primaryname AS Name " +
                       "FROM process pr " +
                       "WHERE pr.id = ? AND (pr.DeletedDatetime IS NULL OR pr.DeletedDatetime = '')";
            case "Policy":
                return "SELECT po.PrimaryName AS Name " +
                       "FROM policy po " +
                       "WHERE po.ID = ? AND (po.DeletedDatetime IS NULL OR po.DeletedDatetime = '')";
            case "ChangeRequest":
                return "SELECT cr.PrimaryName AS Name " +
                       "FROM changerequest cr " +
                       "WHERE cr.ID = ? AND cr.Deleted_At IS NULL";
            case "LegalEntity":
                return "SELECT COALESCE(l.ShortName, l.LongName) AS Name " +
                       "FROM legal l " +
                       "WHERE l.ID = ? AND (l.DeleteDatetime IS NULL OR l.DeleteDatetime = '')";
            case "BusinessArea":
                return "SELECT ba.PrimaryName AS Name " +
                       "FROM business_area ba " +
                       "WHERE ba.ID = ? AND (ba.deletedatetime IS NULL OR ba.deletedatetime = '')";
            case "RegulatoryTheme":
                return "SELECT rt.PrimaryName AS Name " +
                       "FROM regulatorytheme rt " +
                       "WHERE rt.ID = ? AND (rt.DeletedDatetime IS NULL OR rt.DeletedDatetime = '')";
            default:
                return null;
        }
    }
}

