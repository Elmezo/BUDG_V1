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
 * Client Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a client through related entities
 * Endpoint: GET /api/client/stakeholder-community?client_id={id}
 */
@WebServlet("/api/client/stakeholder-community")
public class ClientStakeholderCommunity extends HttpServlet {
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

        String clientIdStr = null;
        int clientId = 0;
        
        try {
            clientIdStr = request.getParameter("client_id");
            if (clientIdStr == null || clientIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"client_id parameter is required\"}");
                return;
            }

            try {
                clientId = Integer.parseInt(clientIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid client_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getClientStakeholderCommunity(conn, clientId);
                
                JsonObject result = new JsonObject();
                result.addProperty("client_id", clientId);
                
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
     * Get all stakeholders indirectly related to a client through various entity relationships
     */
    private List<Map<String, Object>> getClientStakeholderCommunity(Connection conn, int clientId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Client stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Capability stakeholders
        allStakeholders.addAll(getCapabilityStakeholders(conn, clientId));

        // 2. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, clientId));

        // 3. Dataset stakeholders
        allStakeholders.addAll(getDatasetStakeholders(conn, clientId));

        // 4. Glossary stakeholders
        allStakeholders.addAll(getGlossaryStakeholders(conn, clientId));

        // 5. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, clientId));

        // 6. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, clientId));

        // 7. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, clientId));

        // 8. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, clientId));

        return allStakeholders;
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int clientId) throws SQLException {
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
            FROM capability_x_client cxc
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxc.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxc.Capability_ID
            WHERE cxc.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int clientId) throws SQLException {
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
            FROM product_x_client pxc
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxc.Product_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxc.Product_ID
            WHERE pxc.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get Dataset stakeholders
     */
    private List<Map<String, Object>> getDatasetStakeholders(Connection conn, int clientId) throws SQLException {
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
            FROM client_x_dataset cxd
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = cxd.Dataset_ID
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset d ON d.ID = cxd.Dataset_ID
            WHERE cxd.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get Glossary stakeholders
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int clientId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Glossary' AS objectType,
                g.name AS objectName,
                g.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM client_x_glossary cxg
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = cxg.Glossary_ID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.ID = cxg.Glossary_ID
            WHERE cxg.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int clientId) throws SQLException {
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
            FROM client_x_policy cxp
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = cxp.Policy_ID
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = cxp.Policy_ID
            WHERE cxp.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int clientId) throws SQLException {
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
            FROM client_x_process cxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = cxp.Process_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = cxp.Process_ID
            WHERE cxp.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int clientId) throws SQLException {
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
            FROM client_x_project cxp
            JOIN project_x_objectxpeople pxop ON pxop.project_id = cxp.Project_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = cxp.Project_ID
            WHERE cxp.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int clientId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                sys.Name AS objectName,
                sys.id AS objectId,
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
            JOIN system sys ON sys.id = cxs.System_ID
            WHERE cxs.Client_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, clientId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int clientId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, clientId);
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

