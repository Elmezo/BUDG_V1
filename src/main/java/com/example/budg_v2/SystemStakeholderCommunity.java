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
 * System Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a system through related entities
 * Endpoint: GET /api/system/stakeholder-community?system_id={id}
 */
@WebServlet("/api/system/stakeholder-community")
public class SystemStakeholderCommunity extends HttpServlet {
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

        String systemIdStr = null;
        int systemId = 0;
        
        try {
            systemIdStr = request.getParameter("system_id");
            if (systemIdStr == null || systemIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"system_id parameter is required\"}");
                return;
            }

            try {
                systemId = Integer.parseInt(systemIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid system_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getSystemStakeholderCommunity(conn, systemId);
                
                JsonObject result = new JsonObject();
                result.addProperty("system_id", systemId);
                
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
     * Get all stakeholders indirectly related to a system through various entity relationships
     */
    private List<Map<String, Object>> getSystemStakeholderCommunity(Connection conn, int systemId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: System stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, systemId));

        // 2. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, systemId));

        // 3. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, systemId));

        // 4. Client stakeholders
        allStakeholders.addAll(getClientStakeholders(conn, systemId));

        // 5. Glossary stakeholders
        allStakeholders.addAll(getGlossaryStakeholders(conn, systemId));

        // 6. Business Area stakeholders
        allStakeholders.addAll(getBusinessAreaStakeholders(conn, systemId));

        // 7. Capability stakeholders
        allStakeholders.addAll(getCapabilityStakeholders(conn, systemId));

        // 8. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, systemId));

        // 9. Legal Entity stakeholders
        allStakeholders.addAll(getLegalEntityStakeholders(conn, systemId));

        return allStakeholders;
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM process_x_system pxs
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxs.process_id
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxs.process_id
            WHERE pxs.system_id = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM product_x_system pxs
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxs.Product_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxs.Product_ID
            WHERE pxs.System_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM project_x_system pxs
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxs.projectid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxs.projectid
            WHERE pxs.systemid = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Client stakeholders
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM client_x_system cxs
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxs.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxs.Client_ID
            WHERE cxs.System_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Glossary stakeholders
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM glossary_x_system gxs
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = gxs.GlossaryID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = gxs.GlossaryID
            WHERE gxs.SystemID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Business Area stakeholders
     */
    private List<Map<String, Object>> getBusinessAreaStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM businessarea_x_system bxs
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = bxs.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = bxs.BusinessArea_ID
            WHERE bxs.System_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM capability_x_system cxs
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxs.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxs.Capability_ID
            WHERE cxs.System_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM policy_x_system pxs
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxs.Policy_ID
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxs.Policy_ID
            WHERE pxs.System_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Get Legal Entity stakeholders
     */
    private List<Map<String, Object>> getLegalEntityStakeholders(Connection conn, int systemId) throws SQLException {
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
            FROM system_x_legal sxl
            JOIN legal_x_objectxpeople lxop ON lxop.Legal_ID = sxl.Legal_ID
            JOIN object_x_people oxp ON oxp.id = lxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN legal le ON le.id = sxl.Legal_ID
            WHERE sxl.System_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, systemId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int systemId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, systemId);
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

