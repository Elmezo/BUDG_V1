package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides assigned role information for a person.
 */
@WebServlet("/api/people/roles/*")
public class PersonRolesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathInfo.substring(1));
            Map<String, Object> payload = loadRoles(personId);

            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), payload);
        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid person ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
            sendError(response, "Database error: " + sqlEx.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> loadRoles(int personId) throws SQLException {
        // Use a Map to track unique role+facet combinations and store the record
        // Key: roleName|facet (lowercase), Value: record
        Map<String, Map<String, Object>> uniqueRolesMap = new HashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();

        // First, get roles from role_assignment (this is the source of truth - 19 roles)
        final String sqlFromRoleAssignment = """
            SELECT DISTINCT
                ra.id AS assignment_id,
                orole.primaryname AS role_name,
                orole.description AS role_description,
                m.primaryname AS facet_name,
                m.id AS module_id,
                ort.primaryname AS role_type_name,
                ort.id AS role_type_id
            FROM role_assignment ra
            JOIN object_role orole ON ra.objectroleid = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN object_role_type ort ON orole.objectroletype_id = ort.id
            WHERE FIND_IN_SET(?, REPLACE(REPLACE(ra.users, '[', ''), ']', '')) > 0
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFromRoleAssignment)) {

            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String facet = safeTrim(rs.getString("facet_name"));
                    if (facet.isEmpty()) facet = "Other";
                    String roleName = safeTrim(rs.getString("role_name"));
                    String roleKey = roleName.toLowerCase() + "|" + facet.toLowerCase();

                    Map<String, Object> record = new HashMap<>();
                    record.put("id", rs.getInt("assignment_id"));
                    record.put("roleName", roleName);
                    record.put("roleDescription", safeTrim(rs.getString("role_description")));
                    record.put("facet", facet);
                    record.put("moduleId", rs.getInt("module_id"));

                    String roleType = safeTrim(rs.getString("role_type_name"));
                    record.put("roleType", roleType.isEmpty() ? null : roleType);
                    record.put("roleTypeId", rs.getInt("role_type_id"));

                    // No accepted status for role_assignment roles initially
                    record.put("acceptedId", null);
                    record.put("acceptedLabel", null);
                    record.put("roleAccepted", null);

                    // Determine if this is an ownership role
                    boolean ownership = roleType.toLowerCase(Locale.ENGLISH).contains("owner");
                    record.put("ownershipRole", ownership ? "Yes" : "No");

                    // Store in map (will overwrite if duplicate, keeping first occurrence)
                    uniqueRolesMap.put(roleKey, record);
                }
            }
        }
        
        // Second, enrich with data from object_x_people if available (for accepted status, etc.)
        // This will update existing records but won't add new ones
        // Only include records where the linked object is not deleted
        final String sqlFromObjectXPeople = """
            SELECT DISTINCT
                orole.primaryname AS role_name,
                m.primaryname AS facet_name,
                oxp.AcceptedID AS accepted_id,
                ra.Message AS role_accepted_message
            FROM object_x_people oxp
            JOIN object_role orole ON oxp.roleID = orole.id
            LEFT JOIN module m ON orole.module = m.id
            LEFT JOIN roleaccepted ra ON oxp.AcceptedID = ra.ID
            -- Join to check if object exists and is not deleted
            LEFT JOIN dataset_x_objectxpeople dxoxp ON oxp.id = dxoxp.Object_x_ipid
            LEFT JOIN dataset d ON dxoxp.Dataset_ID = d.id AND d.DeletedDatetime IS NULL
            LEFT JOIN system_x_objectxpeople sxoxp ON oxp.id = sxoxp.Object_x_ipid
            LEFT JOIN system s ON sxoxp.SystemID = s.id AND s.Deleted_datetime IS NULL
            LEFT JOIN interface_x_objectxpeople ixoxp ON oxp.id = ixoxp.Object_x_ipid
            LEFT JOIN interface i ON ixoxp.InterfaceID = i.id AND i.deleted_datetime IS NULL
            LEFT JOIN glossary_x_objectxpeople gxoxp ON oxp.id = gxoxp.Object_x_ipid
            LEFT JOIN glossary g ON gxoxp.GlossaryID = g.id AND g.Deleted_datetime IS NULL
            LEFT JOIN process_x_objectxpeople prcxoxp ON oxp.id = prcxoxp.object_x_ip
            LEFT JOIN process p ON prcxoxp.process_id = p.id AND p.DeletedDatetime IS NULL
            LEFT JOIN policy_x_objectxpeople polxoxp ON oxp.id = polxoxp.Object_X_IP
            LEFT JOIN policy pol ON polxoxp.Policy_ID = pol.id AND pol.DeletedDatetime IS NULL
            LEFT JOIN product_x_objectxpeople prodxoxp ON oxp.id = prodxoxp.Object_x_ip
            LEFT JOIN product prod ON prodxoxp.product_id = prod.id AND prod.DeletedDatetime IS NULL
            LEFT JOIN project_x_objectxpeople projxoxp ON oxp.id = projxoxp.object_x_ip
            LEFT JOIN project proj ON projxoxp.project_id = proj.id AND proj.deletedatetime IS NULL
            LEFT JOIN businessarea_x_objectxpeople baxoxp ON oxp.id = baxoxp.Object_x_ipid
            LEFT JOIN business_area ba ON baxoxp.BusinessAreaID = ba.id AND ba.deletedatetime IS NULL
            LEFT JOIN client_x_objectxpeople cxoxp ON oxp.id = cxoxp.Object_x_ipid
            LEFT JOIN client c ON cxoxp.ClientID = c.id AND c.DeleteDatetime IS NULL
            LEFT JOIN committee_x_objectxpeople comxoxp ON oxp.id = comxoxp.Object_x_ipid
            LEFT JOIN committee com ON comxoxp.Committee_ID = com.id AND com.DeleteDatetime IS NULL
            LEFT JOIN legal_x_objectxpeople lexoxp ON oxp.id = lexoxp.Object_x_ip
            LEFT JOIN legal le ON lexoxp.Legal_ID = le.id AND le.DeleteDatetime IS NULL
            LEFT JOIN capability_x_objectxpeople capxoxp ON oxp.id = capxoxp.Object_x_ipid
            LEFT JOIN capability cap ON capxoxp.CapabilityID = cap.id AND cap.DeletedDatetime IS NULL
            LEFT JOIN regulation_x_objectxpeople regxoxp ON oxp.id = regxoxp.Object_x_ipid
            LEFT JOIN regulation reg ON regxoxp.RegulationID = reg.id AND reg.DeletedDatetime IS NULL
            LEFT JOIN attribute_x_objectxpeople axoxp ON oxp.id = axoxp.Object_x_ipid
            LEFT JOIN attribute a ON axoxp.AttributeID = a.id AND a.DeletedDatetime IS NULL
            WHERE oxp.ipid = ?
            -- Only include records where at least one non-deleted object exists
            AND (
                d.id IS NOT NULL OR
                s.id IS NOT NULL OR
                i.id IS NOT NULL OR
                g.id IS NOT NULL OR
                p.id IS NOT NULL OR
                pol.id IS NOT NULL OR
                prod.id IS NOT NULL OR
                proj.id IS NOT NULL OR
                ba.id IS NOT NULL OR
                c.id IS NOT NULL OR
                com.id IS NOT NULL OR
                le.id IS NOT NULL OR
                cap.id IS NOT NULL OR
                reg.id IS NOT NULL OR
                a.id IS NOT NULL
            )
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlFromObjectXPeople)) {

            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String facet = safeTrim(rs.getString("facet_name"));
                    if (facet.isEmpty()) facet = "Other";
                    String roleName = safeTrim(rs.getString("role_name"));
                    String roleKey = roleName.toLowerCase() + "|" + facet.toLowerCase();
                    
                    // Only update if this role exists in our map (from role_assignment)
                    Map<String, Object> existingRecord = uniqueRolesMap.get(roleKey);
                    if (existingRecord != null) {
                        // Update with accepted status from object_x_people
                        String roleAcceptedMessage = rs.getString("role_accepted_message");
                        existingRecord.put("acceptedId", rs.getInt("accepted_id"));
                        existingRecord.put("acceptedLabel", roleAcceptedMessage);
                        existingRecord.put("roleAccepted", roleAcceptedMessage);
                    }
                }
            }
        }
        
        // Convert map to list and sort
        List<Map<String, Object>> records = new ArrayList<>(uniqueRolesMap.values());
        records.sort((a, b) -> {
            String facetA = (String) a.get("facet");
            String facetB = (String) b.get("facet");
            int facetCompare = facetA.compareToIgnoreCase(facetB);
            if (facetCompare != 0) return facetCompare;
            String roleA = (String) a.get("roleName");
            String roleB = (String) b.get("roleName");
            return roleA.compareToIgnoreCase(roleB);
        });
        
        // Calculate facet counts
        for (Map<String, Object> record : records) {
            String facet = (String) record.get("facet");
            counts.merge(facet, 1, (Integer a, Integer b) -> (a != null ? a : 0) + (b != null ? b : 0));
        }

        List<Map<String, Object>> facets = new ArrayList<>();
        facets.add(buildFacet("All", records.size()));
        counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> facets.add(buildFacet(entry.getKey(), entry.getValue())));

        Map<String, Object> payload = new HashMap<>();
        payload.put("records", records);
        payload.put("facets", facets);
        payload.put("total", records.size());
        return payload;
    }

    private Map<String, Object> buildFacet(String label, int count) {
        Map<String, Object> facet = new HashMap<>();
        facet.put("type", label);
        facet.put("count", count);
        return facet;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), error);
    }
}

