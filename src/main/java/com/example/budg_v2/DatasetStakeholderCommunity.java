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
 * Dataset Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a dataset through related entities
 * Endpoint: GET /api/dataset/stakeholder-community?dataset_id={id}
 */
@WebServlet("/api/dataset/stakeholder-community")
public class DatasetStakeholderCommunity extends HttpServlet {
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

        String datasetIdStr = null;
        int datasetId = 0;
        
        try {
            datasetIdStr = request.getParameter("dataset_id");
            if (datasetIdStr == null || datasetIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"dataset_id parameter is required\"}");
                return;
            }

            try {
                datasetId = Integer.parseInt(datasetIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid dataset_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getDatasetStakeholderCommunity(conn, datasetId);
                
                JsonObject result = new JsonObject();
                result.addProperty("dataset_id", datasetId);
                
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
     * Get all stakeholders indirectly related to a dataset through various entity relationships
     */
    private List<Map<String, Object>> getDatasetStakeholderCommunity(Connection conn, int datasetId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Dataset stakeholders are excluded - we only want indirect stakeholders
        
        // 1. System stakeholders (from systems linked to the dataset)
        allStakeholders.addAll(getSystemStakeholders(conn, datasetId));

        // 2. Glossary stakeholders (from glossary linked to the dataset)
        allStakeholders.addAll(getGlossaryStakeholders(conn, datasetId));

        // 3. Process stakeholders
        allStakeholders.addAll(getProcessStakeholders(conn, datasetId));

        // 4. Product stakeholders
        allStakeholders.addAll(getProductStakeholders(conn, datasetId));

        // 5. Project stakeholders
        allStakeholders.addAll(getProjectStakeholders(conn, datasetId));

        // 6. Client stakeholders
        allStakeholders.addAll(getClientStakeholders(conn, datasetId));

        // 7. Legal Entity stakeholders
        allStakeholders.addAll(getLegalEntityStakeholders(conn, datasetId));

        // 8. Policy stakeholders
        allStakeholders.addAll(getPolicyStakeholders(conn, datasetId));

        return allStakeholders;
    }

    /**
     * Get System stakeholders (from systems linked to the dataset via MasterSource)
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int datasetId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'System' AS objectType,
                s.Name AS objectName,
                s.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM dataset d
            JOIN system s ON s.id = d.MasterSource
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = s.ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            WHERE d.ID = ? AND d.MasterSource IS NOT NULL
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Glossary stakeholders (from glossary linked to the dataset)
     */
    private List<Map<String, Object>> getGlossaryStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM dataset d
            JOIN glossary g ON g.ID = d.glossary
            JOIN glossary_x_objectxpeople gxop ON gxop.GlossaryID = g.ID
            JOIN object_x_people oxp ON oxp.id = gxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            WHERE d.ID = ? AND d.glossary IS NOT NULL
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM process_x_dataset pxd
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxd.processid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxd.processid
            WHERE pxd.datasetid = ?
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM product_x_dataset pxd
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxd.Product_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxd.Product_ID
            WHERE pxd.Dataset_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Project stakeholders
     */
    private List<Map<String, Object>> getProjectStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM project_x_dataset pxd
            JOIN project_x_objectxpeople pxop ON pxop.project_id = pxd.projectid
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN project prj ON prj.id = pxd.projectid
            WHERE pxd.dataset_id = ?
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Client stakeholders
     */
    private List<Map<String, Object>> getClientStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM client_x_dataset cxd
            JOIN client_x_objectxpeople cxop ON cxop.ClientID = cxd.Client_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN client c ON c.id = cxd.Client_ID
            WHERE cxd.Dataset_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Legal Entity stakeholders
     */
    private List<Map<String, Object>> getLegalEntityStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM dataset_x_legal dxl
            JOIN legal_x_objectxpeople lxop ON lxop.Legal_ID = dxl.Legal_ID
            JOIN object_x_people oxp ON oxp.id = lxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN legal le ON le.id = dxl.Legal_ID
            WHERE dxl.Dataset_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int datasetId) throws SQLException {
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
            FROM policy_x_dataset pxd
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxd.PolicyID
            JOIN object_x_people oxp ON oxp.id = pxop.Object_X_IP
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxd.PolicyID
            WHERE pxd.DatasetID = ?
            """;

        return executeStakeholderQuery(conn, sql, datasetId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int datasetId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, datasetId);
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

