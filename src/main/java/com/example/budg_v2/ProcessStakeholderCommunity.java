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
 * Process Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a process through related entities
 * Endpoint: GET /api/process/stakeholder-community?process_id={id}
 */
@WebServlet("/api/process/stakeholder-community")
public class ProcessStakeholderCommunity extends HttpServlet {
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

        String processIdStr = null;
        int processId = 0;
        
        try {
            processIdStr = request.getParameter("process_id");
            if (processIdStr == null || processIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"process_id parameter is required\"}");
                return;
            }

            try {
                processId = Integer.parseInt(processIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid process_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getProcessStakeholderCommunity(conn, processId);
                
                JsonObject result = new JsonObject();
                result.addProperty("process_id", processId);
                
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
     * Get all stakeholders indirectly related to a process through various entity relationships
     */
    private List<Map<String, Object>> getProcessStakeholderCommunity(Connection conn, int processId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Process stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Attribute stakeholders
        allStakeholders.addAll(getAttributeStakeholders(conn, processId));

        // 2. Dataset stakeholders
        allStakeholders.addAll(getDatasetStakeholders(conn, processId));

        // 3. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, processId));

        // 4. Interface stakeholders
        allStakeholders.addAll(getInterfaceStakeholders(conn, processId));

        // 5. Legal Entity stakeholders
        allStakeholders.addAll(getLegalStakeholders(conn, processId));

        // 6. Glossary stakeholders
        allStakeholders.addAll(getGlossaryStakeholders(conn, processId));

        // 7. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, processId));

        // 8. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, processId));

        // 9. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, processId));

        // 10. Client stakeholders
        allStakeholders.addAll(getClientStakeholders(conn, processId));

        // 11. Capability stakeholders
        allStakeholders.addAll(getCapabilityStakeholders(conn, processId));

        // 12. Business Area stakeholders
        allStakeholders.addAll(getBusinessAreaStakeholders(conn, processId));

        return allStakeholders;
    }

    /**
     * Get Attribute stakeholders
     */
    private List<Map<String, Object>> getAttributeStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM process_x_attribute pxa
            JOIN attribute_x_objectxpeople axop ON axop.AttributeID = pxa.attributeid
            JOIN object_x_people oxp ON oxp.id = axop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN attribute a ON a.ID = pxa.attributeid
            WHERE pxa.processid = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Dataset stakeholders
     */
    private List<Map<String, Object>> getDatasetStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM process_x_dataset pxd
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = pxd.datasetid
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset d ON d.ID = pxd.datasetid
            WHERE pxd.processid = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM process_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.system_id
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.system_id
            WHERE pxs.process_id = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Interface stakeholders
     */
    private List<Map<String, Object>> getInterfaceStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM process_x_interface pxi
            JOIN interface_x_objectxpeople ixop ON ixop.InterfaceID = pxi.interface_id
            JOIN object_x_people oxp ON oxp.id = ixop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN interface i ON i.id = pxi.interface_id
            WHERE pxi.process_id = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Legal Entity stakeholders
     */
    private List<Map<String, Object>> getLegalStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM process_x_legal pxl
            JOIN legal_x_objectxpeople lxop ON lxop.Legal_ID = pxl.Legal_ID
            JOIN object_x_people oxp ON oxp.id = lxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN legal le ON le.id = pxl.Legal_ID
            WHERE pxl.Process_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Glossary stakeholders
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM glossary_x_process gxp
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = gxp.Glossary_ID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = gxp.Glossary_ID
            WHERE gxp.Process_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM policy_x_process pxp
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxp.policy_id
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxp.policy_id
            WHERE pxp.process_id = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM project_x_process pxp
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxp.projectid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxp.projectid
            WHERE pxp.process_id = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM product_x_process pxp
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxp.productid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxp.productid
            WHERE pxp.processid = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Client stakeholders
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM client_x_process cxp
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxp.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxp.Client_ID
            WHERE cxp.Process_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM capability_x_process cxp
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxp.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxp.Capability_ID
            WHERE cxp.Process_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Get Business Area stakeholders
     */
    private List<Map<String, Object>> getBusinessAreaStakeholders(Connection conn, int processId) throws SQLException {
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
            FROM businessarea_x_process bxp
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = bxp.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = bxp.BusinessArea_ID
            WHERE bxp.Process_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, processId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int processId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processId);
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

