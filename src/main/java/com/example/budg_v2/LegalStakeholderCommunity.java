package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;

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
 * Legal Stakeholder Community Servlet
 * Returns all stakeholders indirectly related to a legal entity through related entities
 * Endpoint: GET /api/legal/stakeholder-community?legal_id={id}
 */
@WebServlet("/api/legal/stakeholder-community")
public class LegalStakeholderCommunity extends HttpServlet {
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

        String legalIdStr = null;
        int legalId = 0;
        
        try {
            legalIdStr = request.getParameter("legal_id");
            if (legalIdStr == null || legalIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"legal_id parameter is required\"}");
                return;
            }

            try {
                legalId = Integer.parseInt(legalIdStr);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"error\": \"Invalid legal_id format\"}");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                List<Map<String, Object>> stakeholders = getLegalStakeholderCommunity(conn, legalId);
                
                JsonObject result = new JsonObject();
                result.addProperty("legal_id", legalId);
                
                JsonArray stakeholdersArray = new JsonArray();
                for (Map<String, Object> stakeholder : stakeholders) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("objectType", (String) stakeholder.get("objectType"));
                    obj.addProperty("objectName", (String) stakeholder.get("objectName"));
                    
                    // Handle nullable Integer fields
                    Object objectIdObj = stakeholder.get("objectId");
                    if (objectIdObj != null) {
                        obj.addProperty("objectId", (Integer) objectIdObj);
                    } else {
                        obj.add("objectId", JsonNull.INSTANCE);
                    }
                    
                    obj.addProperty("role", (String) stakeholder.get("role"));
                    obj.addProperty("name", (String) stakeholder.get("name"));
                    
                    Object personIdObj = stakeholder.get("personId");
                    if (personIdObj != null) {
                        obj.addProperty("personId", (Integer) personIdObj);
                    } else {
                        obj.add("personId", JsonNull.INSTANCE);
                    }
                    
                    obj.addProperty("orgUnit", (String) stakeholder.get("orgUnit"));
                    
                    Object orgUnitIdObj = stakeholder.get("orgUnitId");
                    if (orgUnitIdObj != null) {
                        obj.addProperty("orgUnitId", (Integer) orgUnitIdObj);
                    } else {
                        obj.add("orgUnitId", JsonNull.INSTANCE);
                    }
                    
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
     * Get all stakeholders indirectly related to a legal entity through various entity relationships
     */
    private List<Map<String, Object>> getLegalStakeholderCommunity(Connection conn, int legalId) throws SQLException {
        List<Map<String, Object>> allStakeholders = new ArrayList<>();

        // Note: Legal stakeholders are excluded - we only want indirect stakeholders
        
        // 1. System stakeholders
        try {
            allStakeholders.addAll(getSystemStakeholders(conn, legalId));
        } catch (SQLException e) {
            System.err.println("Error getting System stakeholders for legal entity " + legalId + ": " + e.getMessage());
            e.printStackTrace();
            // Continue with other queries
        }

        // 2. Policy stakeholders
        try {
            allStakeholders.addAll(getPolicyStakeholders(conn, legalId));
        } catch (SQLException e) {
            System.err.println("Error getting Policy stakeholders for legal entity " + legalId + ": " + e.getMessage());
            e.printStackTrace();
            // Continue with other queries
        }

        // 3. Process stakeholders
        try {
            allStakeholders.addAll(getProcessStakeholders(conn, legalId));
        } catch (SQLException e) {
            System.err.println("Error getting Process stakeholders for legal entity " + legalId + ": " + e.getMessage());
            e.printStackTrace();
            // Continue with other queries
        }

        // 4. Dataset stakeholders
        try {
            allStakeholders.addAll(getDatasetStakeholders(conn, legalId));
        } catch (SQLException e) {
            System.err.println("Error getting Dataset stakeholders for legal entity " + legalId + ": " + e.getMessage());
            e.printStackTrace();
            // Continue with other queries
        }

        // 5. Product stakeholders
        try {
            allStakeholders.addAll(getProductStakeholders(conn, legalId));
        } catch (SQLException e) {
            System.err.println("Error getting Product stakeholders for legal entity " + legalId + ": " + e.getMessage());
            e.printStackTrace();
            // Continue with other queries
        }

        // 6. Capability stakeholders
        try {
            allStakeholders.addAll(getCapabilityStakeholders(conn, legalId));
        } catch (SQLException e) {
            System.err.println("Error getting Capability stakeholders for legal entity " + legalId + ": " + e.getMessage());
            e.printStackTrace();
            // Continue with other queries
        }

        return allStakeholders;
    }

    /**
     * Get System stakeholders
     */
    private List<Map<String, Object>> getSystemStakeholders(Connection conn, int legalId) throws SQLException {
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
            FROM system_x_legal sxl
            JOIN system_x_objectxpeople sxop ON sxop.SystemID = sxl.System_ID
            JOIN object_x_people oxp ON oxp.id = sxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN system sys ON sys.id = sxl.System_ID
            WHERE sxl.Legal_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, legalId);
    }

    /**
     * Get Policy stakeholders
     */
    private List<Map<String, Object>> getPolicyStakeholders(Connection conn, int legalId) throws SQLException {
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
            FROM policy_x_legal pxl
            JOIN policy_x_objectxpeople pxop ON pxop.Policy_ID = pxl.Policy_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN policy pol ON pol.id = pxl.Policy_ID
            WHERE pxl.Legal_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, legalId);
    }

    /**
     * Get Process stakeholders
     */
    private List<Map<String, Object>> getProcessStakeholders(Connection conn, int legalId) throws SQLException {
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
            FROM process_x_legal pxl
            JOIN process_x_objectxpeople pxop ON pxop.process_id = pxl.Process_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN process pr ON pr.id = pxl.Process_ID
            WHERE pxl.Legal_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, legalId);
    }

    /**
     * Get Dataset stakeholders
     */
    private List<Map<String, Object>> getDatasetStakeholders(Connection conn, int legalId) throws SQLException {
        String sql = """
            SELECT DISTINCT
                'Dataset' AS objectType,
                ds.PrimaryName AS objectName,
                ds.ID AS objectId,
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId
            FROM dataset_x_legal dxl
            JOIN dataset_x_objectxpeople dxop ON dxop.Dataset_ID = dxl.Dataset_ID
            JOIN object_x_people oxp ON oxp.id = dxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN dataset ds ON ds.id = dxl.Dataset_ID
            WHERE dxl.Legal_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, legalId);
    }

    /**
     * Get Product stakeholders
     */
    private List<Map<String, Object>> getProductStakeholders(Connection conn, int legalId) throws SQLException {
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
            FROM product_x_legal pxl
            JOIN product_x_objectxpeople pxop ON pxop.product_id = pxl.Product_ID
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN product prod ON prod.id = pxl.Product_ID
            WHERE pxl.Legal_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, legalId);
    }

    /**
     * Get Capability stakeholders
     */
    private List<Map<String, Object>> getCapabilityStakeholders(Connection conn, int legalId) throws SQLException {
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
            FROM capability_x_legal cxl
            JOIN capability_x_objectxpeople cxop ON cxop.CapabilityID = cxl.Capability_ID
            JOIN object_x_people oxp ON oxp.id = cxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            JOIN capability cap ON cap.id = cxl.Capability_ID
            WHERE cxl.Legal_ID = ?
            """;

        return executeStakeholderQuery(conn, sql, legalId);
    }

    /**
     * Execute a stakeholder query and return results as a list of maps
     */
    private List<Map<String, Object>> executeStakeholderQuery(Connection conn, String sql, int legalId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, legalId);
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

