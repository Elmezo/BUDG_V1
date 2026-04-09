package com.example.budg_v2.dashboard;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.SegmentAccessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Dashboard widget: Object Counts
 * Returns count of objects by facet type (top 5 + others)
 */
@WebServlet(name = "DashboardObjectCountsServlet", urlPatterns = "/api/dashboard/object-counts")
public class DashboardObjectCountsServlet extends HttpServlet {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer userId = (Integer) request.getAttribute("userId");
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, String> error = new HashMap<>();
            error.put("error", "User not authenticated");
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        try {
            List<Map<String, Object>> counts = getObjectCounts(userId);
            objectMapper.writeValue(response.getWriter(), counts);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private List<Map<String, Object>> getObjectCounts(int userId) throws SQLException {
        Map<String, Integer> facetCounts = new LinkedHashMap<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Count datasets
            facetCounts.put("Data Set", countTable(conn, "dataset", "ID", "Dataset", userId));
            
            // Count glossary
            facetCounts.put("Glossary", countTable(conn, "glossary", "ID", "Glossary", userId));
            
            // Count systems
            facetCounts.put("System", countTable(conn, "system", "id", "System", userId));
            
            // Count processes
            facetCounts.put("Process", countTable(conn, "process", "id", "Process", userId));
            
            // Count attributes
            facetCounts.put("Attribute", countTable(conn, "attribute", "id", "Attribute", userId));
            
            // Count roles
            facetCounts.put("Role", countTable(conn, "object_role", "id", "Role", userId));
            
            // Count interfaces
            facetCounts.put("System Interface", countTable(conn, "interface", "id", "SystemInterface", userId));
            
            // Count products
            facetCounts.put("Product", countTable(conn, "product", "id", "Product", userId));
            
            // Count policies
            facetCounts.put("Policy", countTable(conn, "policy", "ID", "Policy", userId));
            
            // Count projects
            facetCounts.put("Project", countTable(conn, "project", "id", "Project", userId));
            
            // Count business areas
            facetCounts.put("Business Area", countTable(conn, "business_area", "id", "BusinessConnection", userId));
            
            // Count capabilities
            facetCounts.put("Capability", countTable(conn, "capability", "id", "Capability", userId));
            
            // Count clients
            facetCounts.put("Client", countTable(conn, "client", "id", "Client", userId));
            
            // Count committees
            facetCounts.put("Committee", countTable(conn, "committee", "id", "Committee", userId));
            
            // Count legal entities
            facetCounts.put("Legal Entity", countTable(conn, "legal", "id", "Legal", userId));
            
            // Count org units
            facetCounts.put("Org Unit", countTable(conn, "org_unit", "ID", "OrgUnit", userId));
            
            // Count regulations
            facetCounts.put("Regulation", countTable(conn, "regulation", "id", "Regulation", userId));
            
            // Count people
            facetCounts.put("People", countTable(conn, "people", "ID", "InvolvedParty", userId));
        }

        // Sort by count descending
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(facetCounts.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Get top 5 and calculate others
        List<Map<String, Object>> result = new ArrayList<>();
        int othersCount = 0;
        
        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<String, Integer> entry = sortedEntries.get(i);
            if (i < 5) {
                Map<String, Object> item = new HashMap<>();
                item.put("facet", entry.getKey());
                item.put("count", entry.getValue());
                result.add(item);
            } else {
                othersCount += entry.getValue();
            }
        }

        // Add others if there are any
        if (othersCount > 0) {
            Map<String, Object> others = new HashMap<>();
            others.put("facet", "Others");
            others.put("count", othersCount);
            result.add(others);
        }

        return result;
    }

    private int countTable(Connection conn, String tableName, String idColumn, String objectType, int userId) throws SQLException {
        return countTable(conn, tableName, idColumn, objectType, userId, null);
    }

    private int countTable(Connection conn, String tableName, String idColumn, String objectType, int userId, String whereClause) throws SQLException {
        String tableAlias = "t";
        String sql = "SELECT COUNT(" + tableAlias + "." + idColumn + ") as count FROM " + tableName + " " + tableAlias;
        
        // Build WHERE clause - combine soft delete filter, segment filter, and any provided whereClause
        List<String> conditions = new ArrayList<>();
        
        // Add soft delete condition if table supports it
        String deletedColumn = getSoftDeleteColumn(tableName);
        if (deletedColumn != null) {
            conditions.add(tableAlias + "." + deletedColumn + " IS NULL");
        }
        
        // Add segment filter condition
        try {
            String segmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, objectType, tableAlias + "." + idColumn);
            if (segmentFilter != null && !segmentFilter.isEmpty()) {
                conditions.add(segmentFilter);
            }
        } catch (SQLException e) {
            System.err.println("Error building segment filter for " + objectType + ": " + e.getMessage());
            // Continue without segment filter if there's an error
        }
        
        // Add any additional where clause conditions
        if (whereClause != null && !whereClause.isEmpty()) {
            conditions.add(whereClause);
        }
        
        // Add WHERE clause if we have any conditions
        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("count");
            }
        }
        return 0;
    }
    
    /**
     * Get the soft delete column name for a given table name.
     * Returns null if the table doesn't have a soft delete column.
     */
    private String getSoftDeleteColumn(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            return null;
        }
        
        String table = tableName.trim().toLowerCase();
        switch (table) {
            case "dataset":
                return "DeletedDatetime";
            case "attribute":
                return "DeletedDatetime";
            case "system":
                return "Deleted_datetime";
            case "glossary":
                return "Deleted_datetime";
            case "people":
                return "Deleted_date";
            case "interface":
                return "deleted_datetime";
            case "orgunit":
            case "org_unit":
                return "deleted_Date";
            case "process":
                return "deleteddatetime";
            case "project":
                return "deletedatetime";
            case "product":
                return "deleteddatetime";
            case "policy":
                return "DeletedDatetime";
            case "legal-entity":
            case "legalentity":
            case "legal":
                return "DeleteDatetime";
            case "business-area":
            case "business_area":
                return "deletedatetime";
            case "capability":
                return "DeletedDatetime";
            case "client":
                return "DeleteDatetime";
            case "committee":
                return "DeleteDatetime";
            case "geography":
                return "DeletedDatetime";
            case "regulation":
                return "DeletedDatetime";
            case "regulator":
                return "DeletedDatetime";
            case "regulatory-theme":
            case "regulatorytheme":
                return "DeletedDatetime";
            case "object_role":
                // object_role table may not have soft delete - check if it exists
                return null; // Assuming no soft delete for now
            default:
                return null; // No known soft delete column for this table
        }
    }
}

