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
 * Policy Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a policy through related entities
 * Endpoint: GET /api/policy/stakeholder-community?policy_id={id}
 */
@WebServlet("/api/policy/stakeholder-community")
public class PolicyStakeholderCommunity extends HttpServlet {
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

        String policyIdStr = null;
        int policyId = 0;
        
        try {
            policyIdStr = request.getParameter("policy_id");
            if (policyIdStr == null || policyIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"policy_id parameter is required\"}");
                return;
            }

            try {
                policyId = Integer.parseInt(policyIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid policy_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getPolicyStakeholderCommunity(conn, policyId);
                
                JsonObject result = new JsonObject();
                result.addProperty("policy_id", policyId);
                
                JsonArray stakeholdersArray = new JsonArray();
                for (Map<String, Object> stakeholder : stakeholders) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("objectType", (String) stakeholder.get("objectType"));
                    obj.addProperty("objectName", (String) stakeholder.get("objectName"));
                    obj.addProperty("objectId", stakeholder.get("objectId") != null ? ((Integer) stakeholder.get("objectId")) : null);
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
     * Get all stakeholders indirectly related to a policy through various entity relationships
     */
    private List<Map<String, Object>> getPolicyStakeholderCommunity(Connection conn, int policyId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Policy stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, policyId));

        // 2. Client stakeholders
        allStakeholders.addAll(getClientStakeholders(conn, policyId));

        // 3. Regulation stakeholders
        allStakeholders.addAll(getRegulationStakeholders(conn, policyId));

        // 4. Dataset stakeholders
        allStakeholders.addAll(getDatasetStakeholders(conn, policyId));

        // 5. Glossary stakeholders
        allStakeholders.addAll(getGlossaryStakeholders(conn, policyId));

        // 6. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, policyId));

        // 7. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, policyId));

        // 8. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, policyId));

        // 9. Legal Entity stakeholders
        allStakeholders.addAll(getLegalEntityStakeholders(conn, policyId));

        // 10. Business Area stakeholders
        allStakeholders.addAll(getBusinessAreaStakeholders(conn, policyId));

        // 11. Attribute stakeholders
        allStakeholders.addAll(getAttributeStakeholders(conn, policyId));

        return allStakeholders;
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int policyId) throws SQLException {
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
            FROM product_x_policy pxp
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxp.productid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxp.productid
            WHERE pxp.policyid = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Client stakeholders
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int policyId) throws SQLException {
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
            FROM client_x_policy cxp
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxp.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxp.Client_ID
            WHERE cxp.Policy_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Regulation stakeholders
     */
    private List<Map<String, Object>> getRegulationStakeholders(Connection conn, int policyId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Regulation' AS objectType,
                r.PrimaryName AS objectName,
                r.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM regulation_x_policy rxp
            JOIN regulation_x_objectxpeople rxop ON rxop.RegulationID = rxp.RegulationID
            JOIN object_x_people oxp ON oxp.id = rxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN regulation r ON r.id = rxp.RegulationID
            WHERE rxp.PolicyID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Dataset stakeholders
     */
    private List<Map<String, Object>> getDatasetStakeholders(Connection conn, int policyId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Dataset' AS objectType,
                d.PrimaryName AS objectName,
                d.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_dataset pxd
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = pxd.DatasetID
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset d ON d.id = pxd.DatasetID
            WHERE pxd.PolicyID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Glossary stakeholders
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int policyId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Glossary' AS objectType,
                g.Name AS objectName,
                g.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_glossary pxg
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = pxg.GlossaryID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = pxg.GlossaryID
            WHERE pxg.PolicyID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int policyId) throws SQLException {
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
            FROM policy_x_process pxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxp.process_id
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxp.process_id
            WHERE pxp.policy_id = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int policyId) throws SQLException {
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
            FROM policy_x_project pxp
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxp.project_id
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxp.project_id
            WHERE pxp.policy_id = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int policyId) throws SQLException {
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
            FROM policy_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.System_ID
            WHERE pxs.Policy_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Legal Entity stakeholders
     */
    private List<Map<String, Object>> getLegalEntityStakeholders(Connection conn, int policyId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Legal' AS objectType,
                le.ShortName AS objectName,
                le.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_legal pxl
            JOIN legal_x_objectxpeople lxop ON lxop.Legal_ID = pxl.Legal_ID
            JOIN object_x_people oxp ON oxp.id = lxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN legal le ON le.id = pxl.Legal_ID
            WHERE pxl.Policy_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Business Area stakeholders
     */
    private List<Map<String, Object>> getBusinessAreaStakeholders(Connection conn, int policyId) throws SQLException {
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
            FROM policy_x_businessarea pxb
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = pxb.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = pxb.BusinessArea_ID
            WHERE pxb.Policy_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Get Attribute stakeholders
     */
    private List<Map<String, Object>> getAttributeStakeholders(Connection conn, int policyId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Attribute' AS objectType,
                a.PrimaryName AS objectName,
                a.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM policy_x_attribute pxa
            JOIN attribute_x_objectxpeople axop ON axop.AttributeID = pxa.attributeid
            JOIN object_x_people oxp ON oxp.id = axop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN attribute a ON a.id = pxa.attributeid
            WHERE pxa.policyid = ?
            """;

        return executeStakeholderQuery(conn, sql, policyId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int policyId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, policyId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    stakeholder.put("objectType", rs.getString("objectType"));
                    stakeholder.put("objectName", rs.getString("objectName"));
                    
                    // Get objectId (can be NULL)
                    int objectId = rs.getInt("objectId");
                    stakeholder.put("objectId", rs.wasNull() ? null : objectId);
                    
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

