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
 * Business Area Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a business area through related entities
 * Endpoint: GET /api/businessarea/stakeholder-community?businessarea_id={id}
 */
@WebServlet("/api/businessarea/stakeholder-community")
public class BusinessAreaStakeholderCommunity extends HttpServlet {
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

        String businessAreaIdStr = null;
        int businessAreaId = 0;
        
        try {
            businessAreaIdStr = request.getParameter("businessarea_id");
            if (businessAreaIdStr == null || businessAreaIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"businessarea_id parameter is required\"}");
                return;
            }

            try {
                businessAreaId = Integer.parseInt(businessAreaIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid businessarea_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getBusinessAreaStakeholderCommunity(conn, businessAreaId);
                
                JsonObject result = new JsonObject();
                result.addProperty("businessarea_id", businessAreaId);
                
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
     * Get all stakeholders indirectly related to a business area through various entity relationships
     */
    private List<Map<String, Object>> getBusinessAreaStakeholderCommunity(Connection conn, int businessAreaId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Business Area stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, businessAreaId));

        // 2. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, businessAreaId));

        // 3. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, businessAreaId));

        // 4. Capability stakeholders
        allStakeholders.addAll(getCapabilityStakeholders(conn, businessAreaId));

        // 5. Glossary stakeholders
        allStakeholders.addAll(getGlossaryStakeholders(conn, businessAreaId));

        // 6. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, businessAreaId));

        // 7. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, businessAreaId));

        return allStakeholders;
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM policy_x_businessarea pxb
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxb.Policy_ID
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxb.Policy_ID
            WHERE pxb.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM project_x_businessarea pxb
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxb.Project_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxb.Project_ID
            WHERE pxb.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM product_x_businessarea pxb
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxb.Product_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxb.Product_ID
            WHERE pxb.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM capability_x_businessarea cxb
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxb.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxb.Capability_ID
            WHERE cxb.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Get Glossary stakeholders
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM businessarea_x_glossary bxg
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = bxg.Glossary_ID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = bxg.Glossary_ID
            WHERE bxg.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM businessarea_x_process bxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = bxp.Process_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = bxp.Process_ID
            WHERE bxp.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int businessAreaId) throws SQLException {
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
            FROM businessarea_x_system bxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = bxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system sys ON sys.id = bxs.System_ID
            WHERE bxs.BusinessArea_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, businessAreaId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int businessAreaId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, businessAreaId);
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

