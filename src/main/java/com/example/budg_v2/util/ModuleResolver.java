package com.example.budg_v2.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.example.budg_v2.database.DatabaseConnection;

/**
 * Utility class to resolve module IDs based on entity type
 */
public class ModuleResolver {
    
    /**
     * Get module ID based on entity type
     * @param entityType The type of entity (system, dataset, interface, glossary)
     * @return The module ID for the given entity type
     * @throws SQLException if database error occurs
     */
    public static int getModuleId(String entityType) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            String moduleName = getModuleName(entityType);
            ps.setString(1, moduleName);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                } else {
                    throw new SQLException("Module not found for entity type: " + entityType);
                }
            }
        }
    }
    
    /**
     * Get module name based on entity type
     * @param entityType The type of entity
     * @return The module name
     */
    private static String getModuleName(String entityType) {
        switch (entityType.toLowerCase()) {
            case "system":
                return "System";
            case "dataset":
                return "Data Sets";
            case "interface":
            case "system-interface":
                return "Interface";
            case "glossary":
                return "Glossary";
            case "business-area":
            case "businessarea":
                return "Business Area";
            case "capability":
                return "Capability";
            case "client":
                return "Client";
            case "committee":
                return "Committee";
            case "legal-entity":
            case "legalentity":
            case "legal":
            case "legal entity":
                return "Legal Entity";
            case "datasets":
            case "data sets":
                return "Data Sets";
            case "org-unit":
            case "orgunit":
            case "org. unit":
            case "org unit":
                return "Org Unit";
            case "people":
            case "person":
                return "People";
            case "policy":
                return "Policy";
            case "process":
                return "Process";
            case "product":
                return "Product";
            case "project":
                return "Project";
            case "geography":
                return "Geography";
            case "regulation":
                return "Regulation";
            case "regulator":
                return "Regulator";
            case "regulatory-theme":
            case "regulatorytheme":
                return "Regulatory Theme";
            case "attribute":
                return "Attribute";
            default:
                // Try to find by exact match in database
                try {
                    String sql = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(?) OR LOWER(primaryname) = LOWER(?) LIMIT 1";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, entityType);
                        ps.setString(2, entityType.replace("-", " ").replace("_", " "));
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                // Found in database, get the actual name
                                String nameSql = "SELECT primaryname FROM module WHERE id = ?";
                                try (PreparedStatement namePs = conn.prepareStatement(nameSql)) {
                                    namePs.setInt(1, rs.getInt("id"));
                                    try (ResultSet nameRs = namePs.executeQuery()) {
                                        if (nameRs.next()) {
                                            return nameRs.getString("primaryname");
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Fall through to throw exception
                }
                throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }
    
    /**
     * Get all available modules
     * @return List of module names
     * @throws SQLException if database error occurs
     */
    public static java.util.List<String> getAllModuleNames() throws SQLException {
        String sql = "SELECT primaryname FROM module ORDER BY primaryname";
        java.util.List<String> modules = new java.util.ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    modules.add(rs.getString("primaryname"));
                }
            }
        }
        return modules;
    }
}
