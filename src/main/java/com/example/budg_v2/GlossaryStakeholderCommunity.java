package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Glossary Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a glossary through related entities
 * Endpoint: GET /api/glossary/stakeholder-community?glossary_id={id}
 */
@WebServlet("/api/glossary/stakeholder-community")
public class GlossaryStakeholderCommunity extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        PrintWriter out = response.getWriter();

        try {
            String glossaryIdStr = request.getParameter("glossary_id");
            if (glossaryIdStr == null || glossaryIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"glossary_id parameter is required\"}");
                return;
            }

            int glossaryId;
            try {
                glossaryId = Integer.parseInt(glossaryIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid glossary_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getGlossaryStakeholderCommunity(conn, glossaryId);
                
                JsonObject result = new JsonObject();
                result.addProperty("glossary_id", glossaryId);
                
                JsonArray stakeholdersArray = new JsonArray();
                for (Map<String, Object> stakeholder : stakeholders) {
                JsonObject obj = new JsonObject();
                obj.addProperty("objectType", (String) stakeholder.get("objectType"));
                obj.addProperty("objectName", (String) stakeholder.get("objectName"));
                obj.addProperty("objectId", stakeholder.get("objectId") != null ? ((Integer) stakeholder.get("objectId")) : null);
                obj.addProperty("datasetId", stakeholder.get("datasetId") != null ? ((Integer) stakeholder.get("datasetId")) : null);
                obj.addProperty("role", (String) stakeholder.get("role"));
                obj.addProperty("name", (String) stakeholder.get("name"));
                obj.addProperty("personId", stakeholder.get("personId") != null ? ((Integer) stakeholder.get("personId")) : null);
                obj.addProperty("orgUnit", (String) stakeholder.get("orgUnit"));
                obj.addProperty("orgUnitId", stakeholder.get("orgUnitId") != null ? ((Integer) stakeholder.get("orgUnitId")) : null);
                stakeholdersArray.add(obj);
                }
                
                result.add("stakeholders", stakeholdersArray);
                
                out.print(gson.toJson(result));
            } catch (SQLException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"error\": \"Database error: " + e.getMessage() + "\"}");
                e.printStackTrace();
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\": \"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }

    /**
     * Get all stakeholders indirectly related to a glossary through various entity relationships
     */
    private List<Map<String, Object>> getGlossaryStakeholderCommunity(Connection conn, int glossaryId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Glossary stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Business Area stakeholders
        allStakeholders.addAll(getBusinessAreaStakeholders(conn, glossaryId));

        // 2. Capability stakeholders
        allStakeholders.addAll(getCapabilityStakeholders(conn, glossaryId));

        // 3. Client stakeholders
        allStakeholders.addAll(getClientStakeholders(conn, glossaryId));

        // 4. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, glossaryId));

        // 5. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, glossaryId));

        // 6. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, glossaryId));

        // 7. Interface stakeholders
        allStakeholders.addAll(getInterfaceStakeholders(conn, glossaryId));

        // 8. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, glossaryId));

        // 9. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, glossaryId));

        // 10. Attribute stakeholders
        allStakeholders.addAll(getAttributeStakeholders(conn, glossaryId));

        return allStakeholders;
    }

    /**
     * Get Business Area stakeholders
     */
    private List<Map<String, Object>> getBusinessAreaStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Business Area' AS objectType,
                ba.PrimaryName AS objectName,
                ba.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM businessarea_x_glossary bxg
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = bxg.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = bxg.BusinessArea_ID
            WHERE bxg.Glossary_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Capability' AS objectType,
                cap.PrimaryName AS objectName,
                cap.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM capability_x_glossary cxg
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxg.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxg.Capability_ID
            WHERE cxg.Glossary_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Client stakeholders
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Client' AS objectType,
                c.PrimaryName AS objectName,
                c.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM client_x_glossary cxg
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxg.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxg.Client_ID
            WHERE cxg.Glossary_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int glossaryId) throws SQLException {
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
            FROM glossary_x_process gxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = gxp.Process_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = gxp.Process_ID
            WHERE gxp.Glossary_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Project' AS objectType,
                prj.primaryname AS objectName,
                prj.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM glossary_x_project gxpj
            JOIN project_x_objectxpeople pxop ON pxop.project_id = gxpj.Project_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = gxpj.Project_ID
            WHERE gxpj.Glossary_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM glossary_x_system gxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = gxs.SystemID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_X_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = gxs.SystemID
            WHERE gxs.GlossaryID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Interface stakeholders
     */
    private List<Map<String, Object>> getInterfaceStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Interface' AS objectType,
                i.Name AS objectName,
                i.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM interface_x_glossary ixg
            JOIN interface_x_objectxpeople ixop ON ixop.InterfaceID = ixg.Interface
            JOIN object_x_people oxp ON oxp.id = ixop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN interface i ON i.id = ixg.Interface
            WHERE ixg.Glossary = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Policy' AS objectType,
                pol.PrimaryName AS objectName,
                pol.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_glossary pxg
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxg.PolicyID
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxg.PolicyID
            WHERE pxg.GlossaryID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Product' AS objectType,
                prod.primaryname AS objectName,
                prod.id AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM product_x_glossary pxg
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxg.productid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxg.productid
            WHERE pxg.glossaryid = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Get Attribute stakeholders
     */
    private List<Map<String, Object>> getAttributeStakeholders(Connection conn, int glossaryId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Attribute' AS objectType,
                a.PrimaryName AS objectName,
                a.ID AS objectId,
                a.Dataset_ID AS datasetId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM attribute a
            JOIN attribute_x_objectxpeople axop ON axop.AttributeID = a.ID
            JOIN object_x_people oxp ON oxp.id = axop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            WHERE a.Glossary_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, glossaryId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int glossaryId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, glossaryId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    
                    // Get objectId (can be NULL)
                    int objectId = rs.getInt("objectId");
                    stakeholder.put("objectId", rs.wasNull() ? null : objectId);
                    
                    // Get datasetId for Attribute type (can be NULL)
                    try {
                        int datasetId = rs.getInt("datasetId");
                        stakeholder.put("datasetId", rs.wasNull() ? null : datasetId);
                    } catch (SQLException e) {
                        // Column doesn't exist for non-Attribute types, ignore
                        stakeholder.put("datasetId", null);
                    }
                    
                    stakeholder.put("role", rs.getString("role"));
                    stakeholder.put("name", rs.getString("name"));
                    
                    // Get personId (can be NULL)
                    int personId = rs.getInt("personId");
                    stakeholder.put("personId", rs.wasNull() ? null : personId);
                    
                    String orgUnit = rs.getString("orgUnit");
                    stakeholder.put("orgUnit", orgUnit != null ? orgUnit : "");
                    
                    // Get orgUnitId (can be NULL)
                    int orgUnitId = rs.getInt("orgUnitId");
                    stakeholder.put("orgUnitId", rs.wasNull() ? null : orgUnitId);
                    
                    stakeholders.add(stakeholder);
                }
            }
        }
        return stakeholders;
    }
}

