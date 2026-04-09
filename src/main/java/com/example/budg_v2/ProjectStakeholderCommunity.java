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
 * Project Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a project through related entities
 * Endpoint: GET /api/project/stakeholder-community?project_id={id}
 */
@WebServlet("/api/project/stakeholder-community")
public class ProjectStakeholderCommunity extends HttpServlet {
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

        String projectIdStr = null;
        int projectId = 0;
        
        try {
            projectIdStr = request.getParameter("project_id");
            if (projectIdStr == null || projectIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"project_id parameter is required\"}");
                return;
            }

            try {
                projectId = Integer.parseInt(projectIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid project_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getProjectStakeholderCommunity(conn, projectId);
                
                JsonObject result = new JsonObject();
                result.addProperty("project_id", projectId);
                
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
     * Get all stakeholders indirectly related to a project through various entity relationships
     */
    private List<Map<String, Object>> getProjectStakeholderCommunity(Connection conn, int projectId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Project stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Attribute stakeholders
        allStakeholders.addAll(getAttributeStakeholders(conn, projectId));

        // 2. Dataset stakeholders
        allStakeholders.addAll(getDatasetStakeholders(conn, projectId));

        // 3. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, projectId));

        // 4. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, projectId));

        // 5. Capability stakeholders
        allStakeholders.addAll(getCapabilityStakeholders(conn, projectId));

        // 6. Business Area stakeholders
        allStakeholders.addAll(getBusinessAreaStakeholders(conn, projectId));

        // 7. Glossary stakeholders (reversed direction)
        allStakeholders.addAll(getGlossaryStakeholders(conn, projectId));

        // 8. Policy stakeholders (reversed direction)
        allStakeholders.addAll(getPolicyStakeholders(conn, projectId));

        // 9. Product stakeholders (reversed direction)
        allStakeholders.addAll(getProductStakeholders(conn, projectId));

        // 10. Client stakeholders (reversed direction)
        allStakeholders.addAll(getClientStakeholders(conn, projectId));

        // 11. Regulation stakeholders (reversed direction)
        allStakeholders.addAll(getRegulationStakeholders(conn, projectId));

        return allStakeholders;
    }

    /**
     * Get Attribute stakeholders
     */
    private List<Map<String, Object>> getAttributeStakeholders(Connection conn, int projectId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Attribute' AS objectType,
                a.primaryname AS objectName,
                a.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM project_x_attribute pxa
            JOIN attribute_x_objectxpeople axop ON axop.AttributeID = pxa.attribute_id
            JOIN object_x_people oxp ON oxp.id = axop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN attribute a ON a.ID = pxa.attribute_id
            WHERE pxa.projectid = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Dataset stakeholders
     */
    private List<Map<String, Object>> getDatasetStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM project_x_dataset pxd
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = pxd.dataset_id
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset d ON d.ID = pxd.dataset_id
            WHERE pxd.projectid = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM project_x_process pxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxp.process_id
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxp.process_id
            WHERE pxp.projectid = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM project_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.systemid
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.systemid
            WHERE pxs.projectid = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM project_x_capability pxc
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = pxc.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = pxc.Capability_ID
            WHERE pxc.Project_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Business Area stakeholders
     */
    private List<Map<String, Object>> getBusinessAreaStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM project_x_businessarea pxb
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = pxb.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = pxb.BusinessArea_ID
            WHERE pxb.Project_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Glossary stakeholders (reversed direction)
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM glossary_x_project gxp
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = gxp.Glossary_ID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = gxp.Glossary_ID
            WHERE gxp.Project_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Policy stakeholders (reversed direction)
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM policy_x_project pxp
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxp.policy_id
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxp.policy_id
            WHERE pxp.project_id = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Product stakeholders (reversed direction)
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM product_x_project pxp
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxp.productid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxp.productid
            WHERE pxp.projectid = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Client stakeholders (reversed direction)
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int projectId) throws SQLException {
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
            FROM client_x_project cxp
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxp.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxp.Client_ID
            WHERE cxp.Project_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Get Regulation stakeholders (reversed direction)
     */
    private List<Map<String, Object>> getRegulationStakeholders(Connection conn, int projectId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Regulation' AS objectType,
                reg.primaryName AS objectName,
                reg.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM regulation_x_project rxp
            JOIN regulation_x_objectxpeople rxop ON rxop.RegulationID = rxp.RegulationID
            JOIN object_x_people oxp ON oxp.id = rxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN regulation reg ON reg.ID = rxp.RegulationID
            WHERE rxp.ProjectID = ?
            """;

        return executeStakeholderQuery(conn, sql, projectId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int projectId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, projectId);
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

