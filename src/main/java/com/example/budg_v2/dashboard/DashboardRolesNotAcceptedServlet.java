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
 * Dashboard widget: Roles Not Accepted
 * Returns count of unaccepted roles by facet for current user (top 5 + others)
 */
@WebServlet(name = "DashboardRolesNotAcceptedServlet", urlPatterns = "/api/dashboard/roles-not-accepted")
public class DashboardRolesNotAcceptedServlet extends HttpServlet {

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
            Map<String, Object> result = getRolesNotAccepted(userId);
            objectMapper.writeValue(response.getWriter(), result);
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Database error: " + e.getMessage());
            objectMapper.writeValue(response.getWriter(), error);
        }
    }

    private Map<String, Object> getRolesNotAccepted(int userId) throws SQLException {
        Map<String, Integer> facetCounts = new LinkedHashMap<>();

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

        // Use UNION to query each object type separately and apply segment filtering
        String sql = """
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN dataset_x_objectxpeople dxoxp ON oxp.id = dxoxp.Object_x_ipid
            INNER JOIN dataset d ON dxoxp.Dataset_ID = d.ID AND d.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND dxoxp.Object_x_ipid IS NOT NULL
            """ + (datasetSegmentFilter != null && !datasetSegmentFilter.isEmpty() ? "AND " + datasetSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN system_x_objectxpeople sxoxp ON oxp.id = sxoxp.Object_x_ipid
            INNER JOIN system s ON sxoxp.SystemID = s.id AND s.Deleted_datetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND sxoxp.Object_x_ipid IS NOT NULL
            """ + (systemSegmentFilter != null && !systemSegmentFilter.isEmpty() ? "AND " + systemSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN interface_x_objectxpeople ixoxp ON oxp.id = ixoxp.Object_x_ipid
            INNER JOIN interface i ON ixoxp.InterfaceID = i.id AND i.deleted_datetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND ixoxp.Object_x_ipid IS NOT NULL
            """ + (interfaceSegmentFilter != null && !interfaceSegmentFilter.isEmpty() ? "AND " + interfaceSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN glossary_x_objectxpeople gxoxp ON oxp.id = gxoxp.Object_x_ipid
            INNER JOIN glossary g ON gxoxp.GlossaryID = g.ID AND g.Deleted_datetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND gxoxp.Object_x_ipid IS NOT NULL
            """ + (glossarySegmentFilter != null && !glossarySegmentFilter.isEmpty() ? "AND " + glossarySegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN process_x_objectxpeople pxoxp ON oxp.id = pxoxp.Object_x_ip
            INNER JOIN process p ON pxoxp.process_id = p.id AND p.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND pxoxp.Object_x_ip IS NOT NULL
            """ + (processSegmentFilter != null && !processSegmentFilter.isEmpty() ? "AND " + processSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN product_x_objectxpeople prodxoxp ON oxp.id = prodxoxp.Object_x_ip
            INNER JOIN product prod ON prodxoxp.product_id = prod.id AND prod.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND prodxoxp.Object_x_ip IS NOT NULL
            """ + (productSegmentFilter != null && !productSegmentFilter.isEmpty() ? "AND " + productSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN businessarea_x_objectxpeople baxoxp ON oxp.id = baxoxp.Object_x_ipid
            INNER JOIN business_area ba ON baxoxp.BusinessAreaID = ba.id AND ba.deletedatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND baxoxp.Object_x_ipid IS NOT NULL
            """ + (businessAreaSegmentFilter != null && !businessAreaSegmentFilter.isEmpty() ? "AND " + businessAreaSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN client_x_objectxpeople cxoxp ON oxp.id = cxoxp.Object_x_ipid
            INNER JOIN client c ON cxoxp.ClientID = c.id
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND cxoxp.Object_x_ipid IS NOT NULL
            """ + (clientSegmentFilter != null && !clientSegmentFilter.isEmpty() ? "AND " + clientSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN committee_x_objectxpeople comxoxp ON oxp.id = comxoxp.Object_x_ipid
            INNER JOIN committee com ON comxoxp.Committee_ID = com.id AND com.DeleteDatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND comxoxp.Object_x_ipid IS NOT NULL
            """ + (committeeSegmentFilter != null && !committeeSegmentFilter.isEmpty() ? "AND " + committeeSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN legal_x_objectxpeople lexoxp ON oxp.id = lexoxp.Object_x_ip
            INNER JOIN legal le ON lexoxp.Legal_ID = le.id AND le.DeleteDatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND lexoxp.Object_x_ip IS NOT NULL
            """ + (legalSegmentFilter != null && !legalSegmentFilter.isEmpty() ? "AND " + legalSegmentFilter : "") + """
            GROUP BY m.primaryname
            
            UNION ALL
            
            SELECT 
                m.primaryname AS facet_name,
                COUNT(*) AS count
            FROM object_x_people oxp
            LEFT JOIN object_role orole ON oxp.RoleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN attribute_x_objectxpeople axoxp ON oxp.id = axoxp.Object_x_ipid
            INNER JOIN attribute a ON axoxp.AttributeID = a.id AND a.DeletedDatetime IS NULL
            WHERE oxp.ipid = ? 
            AND oxp.AcceptedID = 2
            AND axoxp.Object_x_ipid IS NOT NULL
            """ + (attributeSegmentFilter != null && !attributeSegmentFilter.isEmpty() ? "AND " + attributeSegmentFilter : "") + """
            GROUP BY m.primaryname
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
                    int count = rs.getInt("count");
                    if (facetName != null && !facetName.isEmpty()) {
                        facetCounts.put(facetName, facetCounts.getOrDefault(facetName, 0) + count);
                    }
                }
            }
        }

        // Get top 5 and calculate others
        List<Map<String, Object>> facetList = new ArrayList<>();
        int othersCount = 0;
        int totalCount = 0;
        int index = 0;

        for (Map.Entry<String, Integer> entry : facetCounts.entrySet()) {
            totalCount += entry.getValue();
            if (index < 5) {
                Map<String, Object> item = new HashMap<>();
                item.put("facet", entry.getKey());
                item.put("count", entry.getValue());
                facetList.add(item);
            } else {
                othersCount += entry.getValue();
            }
            index++;
        }

        // Add others if there are any
        if (othersCount > 0) {
            Map<String, Object> others = new HashMap<>();
            others.put("facet", "Others");
            others.put("count", othersCount);
            facetList.add(others);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("facets", facetList);
        result.put("total", totalCount);
        
        return result;
    }
}

