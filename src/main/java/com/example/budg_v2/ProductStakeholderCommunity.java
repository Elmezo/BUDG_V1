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
 * Product Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a product through related entities
 * Endpoint: GET /api/product/stakeholder-community?product_id={id}
 */
@WebServlet("/api/product/stakeholder-community")
public class ProductStakeholderCommunity extends HttpServlet {
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

        String productIdStr = null;
        int productId = 0;
        
        try {
            productIdStr = request.getParameter("product_id");
            if (productIdStr == null || productIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"product_id parameter is required\"}");
                return;
            }

            try {
                productId = Integer.parseInt(productIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid product_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getProductStakeholderCommunity(conn, productId);
                
                JsonObject result = new JsonObject();
                result.addProperty("product_id", productId);
                
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
     * Get all stakeholders indirectly related to a product through various entity relationships
     */
    private List<Map<String, Object>> getProductStakeholderCommunity(Connection conn, int productId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Product stakeholders are excluded - we only want indirect stakeholders
        
        // 1. Glossary stakeholders
        allStakeholders.addAll(getGlossaryStakeholders(conn, productId));

        // 2. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, productId));

        // 3. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, productId));

        // 4. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, productId));

        // 5. Dataset stakeholders
        allStakeholders.addAll(getDatasetStakeholders(conn, productId));

        // 6. System stakeholders
        allStakeholders.addAll(getSystemStakeholders(conn, productId));

        // 7. Client stakeholders
        allStakeholders.addAll(getClientStakeholders(conn, productId));

        // 8. Legal stakeholders
        allStakeholders.addAll(getLegalStakeholders(conn, productId));

        // 9. Business Area stakeholders
        allStakeholders.addAll(getBusinessAreaStakeholders(conn, productId));

        // 10. Capability stakeholders (reversed direction)
        allStakeholders.addAll(getCapabilityStakeholders(conn, productId));

        // 11. Regulation stakeholders (reversed direction)
        allStakeholders.addAll(getRegulationStakeholders(conn, productId));

        return allStakeholders;
    }

    /**
     * Get Glossary stakeholders
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_glossary pxg
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = pxg.glossaryid
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN glossary g ON g.id = pxg.glossaryid
            WHERE pxg.productid = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_policy pxp
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxp.policyid
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxp.policyid
            WHERE pxp.productid = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_process pxp
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxp.processid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxp.processid
            WHERE pxp.productid = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_project pxp
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxp.projectid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxp.projectid
            WHERE pxp.productid = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Dataset stakeholders
     */
    private List<Map<String, Object>> getDatasetStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_dataset pxd
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = pxd.Dataset_ID
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset d ON d.ID = pxd.Dataset_ID
            WHERE pxd.Product_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_system pxs
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = pxs.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system s ON s.id = pxs.System_ID
            WHERE pxs.Product_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Client stakeholders
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_client pxc
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = pxc.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = pxc.Client_ID
            WHERE pxc.Product_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Legal stakeholders
     */
    private List<Map<String, Object>> getLegalStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_legal pxl
            JOIN legal_x_objectxpeople lxop ON lxop.Legal_ID = pxl.Legal_ID
            JOIN object_x_people oxp ON oxp.id = lxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN legal le ON le.id = pxl.Legal_ID
            WHERE pxl.Product_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Business Area stakeholders
     */
    private List<Map<String, Object>> getBusinessAreaStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM product_x_businessarea pxb
            JOIN businessarea_x_objectxpeople bxop ON bxop.BusinessAreaID = pxb.BusinessArea_ID
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN business_area ba ON ba.id = pxb.BusinessArea_ID
            WHERE pxb.Product_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Capability stakeholders (reversed direction - capability_x_product)
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM capability_x_product cxp
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxp.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxp.Capability_ID
            WHERE cxp.Product_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Get Regulation stakeholders (reversed direction - regulation_x_product)
     */
    private List<Map<String, Object>> getRegulationStakeholders(Connection conn, int productId) throws SQLException {
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
            FROM regulation_x_product rxp
            JOIN regulation_x_objectxpeople rxop ON rxop.RegulationID = rxp.RegulationID
            JOIN object_x_people oxp ON oxp.id = rxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN regulation reg ON reg.ID = rxp.RegulationID
            WHERE rxp.ProductID = ?
            """;

        return executeStakeholderQuery(conn, sql, productId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int productId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, productId);
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

