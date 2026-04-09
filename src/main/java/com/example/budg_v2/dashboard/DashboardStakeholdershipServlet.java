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
 * Dashboard widget: Stakeholdership
 * Returns count of objects where user is stakeholder or owner by facet (top 5 + others)
 */
@WebServlet(name = "DashboardStakeholdershipServlet", urlPatterns = "/api/dashboard/stakeholdership")
public class DashboardStakeholdershipServlet extends HttpServlet {

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
            List<Map<String, Object>> result = getStakeholdership(userId);
            objectMapper.writeValue(response.getWriter(), result);
        } catch (SQLException e) {
            e.printStackTrace(); // Log the full stack trace
            System.err.println("SQL Error in Stakeholdership: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            try {
                objectMapper.writeValue(response.getWriter(), error);
            } catch (IOException ioException) {
                response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log the full stack trace
            System.err.println("Error in Stakeholdership: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Error: " + e.getMessage());
            try {
                objectMapper.writeValue(response.getWriter(), error);
            } catch (IOException ioException) {
                response.getWriter().write("{\"error\":\"Error: " + e.getMessage() + "\"}");
            }
        }
    }

    private List<Map<String, Object>> getStakeholdership(int userId) throws SQLException {
        Map<String, FacetCounts> facetCounts = new LinkedHashMap<>();

        // Build segment filter conditions for each object type
        String datasetSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Dataset", "d.ID");
        String systemSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "System", "s.id");
        String interfaceSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "SystemInterface", "i.id");
        String glossarySegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Glossary", "g.ID");
        String processSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Process", "p.id");
        String productSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Product", "prod.id");
        String businessAreaSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "BusinessConnection", "ba.id");
        String clientSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Client", "c.id");
        String committeeSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Committee", "com.id");
        String legalSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Legal", "le.id");
        String attributeSegmentFilter = SegmentAccessService.buildSelectedSegmentFilterClause(userId, "Attribute", "a.id");

        // Query to count DISTINCT objects where user is owner or stakeholder, grouped by facet
        // Count distinct objects (not role assignments) - if user has multiple roles on same object, count as 1
        // Exclude deleted objects by checking deletion datetime fields
        // Use UNION ALL to count distinct objects from each junction table separately
        // Owner detection: Check ONLY role type (contains "owner" or "ownership")
        String sql = """
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT dxoxp.Dataset_ID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN dataset_x_objectxpeople dxoxp ON oxp.id = dxoxp.Object_x_ipid
            INNER JOIN dataset d ON dxoxp.Dataset_ID = d.ID AND d.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? AND dxoxp.Object_x_ipid IS NOT NULL
            """ + (datasetSegmentFilter != null && !datasetSegmentFilter.isEmpty() ? "AND " + datasetSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT sxoxp.SystemID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN system_x_objectxpeople sxoxp ON oxp.id = sxoxp.Object_x_ipid
            INNER JOIN system s ON sxoxp.SystemID = s.id AND s.Deleted_datetime IS NULL
            WHERE oxp.ipid = ? AND sxoxp.Object_x_ipid IS NOT NULL
            """ + (systemSegmentFilter != null && !systemSegmentFilter.isEmpty() ? "AND " + systemSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT ixoxp.InterfaceID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN interface_x_objectxpeople ixoxp ON oxp.id = ixoxp.Object_x_ipid
            INNER JOIN interface i ON ixoxp.InterfaceID = i.id AND i.deleted_datetime IS NULL
            WHERE oxp.ipid = ? AND ixoxp.Object_x_ipid IS NOT NULL
            """ + (interfaceSegmentFilter != null && !interfaceSegmentFilter.isEmpty() ? "AND " + interfaceSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT gxoxp.GlossaryID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN glossary_x_objectxpeople gxoxp ON oxp.id = gxoxp.Object_x_ipid
            INNER JOIN glossary g ON gxoxp.GlossaryID = g.ID AND g.Deleted_datetime IS NULL
            WHERE oxp.ipid = ? AND gxoxp.Object_x_ipid IS NOT NULL
            """ + (glossarySegmentFilter != null && !glossarySegmentFilter.isEmpty() ? "AND " + glossarySegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT pxoxp.process_id) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN process_x_objectxpeople pxoxp ON oxp.id = pxoxp.Object_x_ip
            INNER JOIN process p ON pxoxp.process_id = p.id AND p.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? AND pxoxp.Object_x_ip IS NOT NULL
            """ + (processSegmentFilter != null && !processSegmentFilter.isEmpty() ? "AND " + processSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT prodxoxp.product_id) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN product_x_objectxpeople prodxoxp ON oxp.id = prodxoxp.Object_x_ip
            INNER JOIN product prod ON prodxoxp.product_id = prod.id AND prod.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? AND prodxoxp.Object_x_ip IS NOT NULL
            """ + (productSegmentFilter != null && !productSegmentFilter.isEmpty() ? "AND " + productSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT baxoxp.BusinessAreaID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN businessarea_x_objectxpeople baxoxp ON oxp.id = baxoxp.Object_x_ipid
            INNER JOIN business_area ba ON baxoxp.BusinessAreaID = ba.id AND ba.deletedatetime IS NULL
            WHERE oxp.ipid = ? AND baxoxp.Object_x_ipid IS NOT NULL
            """ + (businessAreaSegmentFilter != null && !businessAreaSegmentFilter.isEmpty() ? "AND " + businessAreaSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT cxoxp.ClientID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN client_x_objectxpeople cxoxp ON oxp.id = cxoxp.Object_x_ipid
            INNER JOIN client c ON cxoxp.ClientID = c.id
            WHERE oxp.ipid = ? AND cxoxp.Object_x_ipid IS NOT NULL
            """ + (clientSegmentFilter != null && !clientSegmentFilter.isEmpty() ? "AND " + clientSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT comxoxp.Committee_ID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN committee_x_objectxpeople comxoxp ON oxp.id = comxoxp.Object_x_ipid
            INNER JOIN committee com ON comxoxp.Committee_ID = com.id AND com.DeleteDatetime IS NULL
            WHERE oxp.ipid = ? AND comxoxp.Object_x_ipid IS NOT NULL
            """ + (committeeSegmentFilter != null && !committeeSegmentFilter.isEmpty() ? "AND " + committeeSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT lexoxp.Legal_ID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN legal_x_objectxpeople lexoxp ON oxp.id = lexoxp.Object_x_ip
            INNER JOIN legal le ON lexoxp.Legal_ID = le.id AND le.DeleteDatetime IS NULL
            WHERE oxp.ipid = ? AND lexoxp.Object_x_ip IS NOT NULL
            """ + (legalSegmentFilter != null && !legalSegmentFilter.isEmpty() ? "AND " + legalSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                orole.primaryname AS role_name,
                ort.primaryname AS role_type_name,
                COUNT(DISTINCT axoxp.AttributeID) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            LEFT JOIN attribute_x_objectxpeople axoxp ON oxp.id = axoxp.Object_x_ipid
            INNER JOIN attribute a ON axoxp.AttributeID = a.id AND a.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? AND axoxp.Object_x_ipid IS NOT NULL
            """ + (attributeSegmentFilter != null && !attributeSegmentFilter.isEmpty() ? "AND " + attributeSegmentFilter : "") + """
            GROUP BY m.primaryname, orole.primaryname, ort.primaryname
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            // Set userId for all 11 UNION queries
            for (int i = 1; i <= 11; i++) {
                ps.setInt(i, userId);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String facetName = rs.getString("facet_name");
                    rs.getString("role_name");
                    String roleTypeName = rs.getString("role_type_name");
                    int count = rs.getInt("count");
                    
                    if (facetName != null && !facetName.isEmpty() && count > 0) {
                        facetCounts.putIfAbsent(facetName, new FacetCounts());
                        FacetCounts fc = facetCounts.get(facetName);
                        
                        // All roles (including owners) count as stakeholders
                        // Owners are a subset of stakeholders, so they appear in both counts
                        fc.stakeholder += count;
                        
                        // Determine if this is also an owner role
                        // Check ONLY role type name (contains "owner" or "ownership" case-insensitive)
                        // Examples: "Ownership Role", "Authority Role" (if it contains "owner")
                        boolean isOwner = false;
                        if (roleTypeName != null && !roleTypeName.trim().isEmpty()) {
                            String roleTypeLower = roleTypeName.toLowerCase().trim();
                            // Check if role type name contains "owner" or "ownership"
                            isOwner = roleTypeLower.contains("owner") || roleTypeLower.contains("ownership");
                        }
                        
                        // If it's an owner role, also count it in the owner count
                        if (isOwner) {
                            fc.owner += count;
                        }
                    }
                }
            }
        }

        // Sort by total count (stakeholder + owner)
        List<Map.Entry<String, FacetCounts>> sortedEntries = new ArrayList<>(facetCounts.entrySet());
        sortedEntries.sort((a, b) -> {
            int totalA = a.getValue().stakeholder + a.getValue().owner;
            int totalB = b.getValue().stakeholder + b.getValue().owner;
            return Integer.compare(totalB, totalA);
        });

        // Get top 5 and calculate others
        List<Map<String, Object>> result = new ArrayList<>();
        int othersStakeholder = 0;
        int othersOwner = 0;

        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<String, FacetCounts> entry = sortedEntries.get(i);
            if (i < 5) {
                Map<String, Object> item = new HashMap<>();
                item.put("facet", entry.getKey());
                item.put("stakeholder", entry.getValue().stakeholder);
                item.put("owner", entry.getValue().owner);
                result.add(item);
            } else {
                othersStakeholder += entry.getValue().stakeholder;
                othersOwner += entry.getValue().owner;
            }
        }

        // Add others if there are any
        if (othersStakeholder > 0 || othersOwner > 0) {
            Map<String, Object> others = new HashMap<>();
            others.put("facet", "Others");
            others.put("stakeholder", othersStakeholder);
            others.put("owner", othersOwner);
            result.add(others);
        }

        return result;
    }

    private static class FacetCounts {
        int stakeholder = 0;
        int owner = 0;
    }
}

