package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Servlet to check if a workflow is currently used in active change requests
 * Endpoint: /admin/api/check-workflow-in-use
 */
@WebServlet("/admin/api/check-workflow-in-use")
public class CheckWorkflowInUseServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CheckWorkflowInUseServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String workflowIdParam = req.getParameter("workflowId");
        String facet = req.getParameter("facet");
        String checkFacetWorkflows = req.getParameter("checkFacetWorkflows"); // New parameter
        
        // If checkFacetWorkflows is set, check if facet has active CRs using default workflows
        if ("true".equalsIgnoreCase(checkFacetWorkflows)) {
            if (facet == null || facet.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing facet parameter\"}");
                return;
            }
            
            try {
                JsonObject result = checkFacetWorkflowsInUse(facet);
                resp.getWriter().write(result.toString());
            } catch (SQLException e) {
                logger.error("Database error checking facet workflows usage", e);
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            }
            return;
        }
        
        if (workflowIdParam == null || workflowIdParam.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing workflowId parameter\"}");
            return;
        }

        try {
            int workflowId = Integer.parseInt(workflowIdParam);
            JsonObject result = checkWorkflowInUse(workflowId, facet);
            resp.getWriter().write(result.toString());
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid workflowId format\"}");
        } catch (SQLException e) {
            logger.error("Database error checking workflow usage", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    /**
     * Check if a workflow (process definition) is used in active change requests for a specific facet
     * Active CRs are those that are NOT Completed or Cancelled
     * If facet is provided, only check CRs for that facet (based on Reference field)
     */
    private JsonObject checkWorkflowInUse(int workflowId, String facet) throws SQLException {
        JsonObject result = new JsonObject();
        JsonArray activeCRs = new JsonArray();

        // Build SQL query with optional facet filtering
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT DISTINCT cr.ID, cr.PrimaryName, cr.Reference, crs.PrimaryName as statusName ");
        sqlBuilder.append("FROM changerequest cr ");
        sqlBuilder.append("LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID ");
        sqlBuilder.append("LEFT JOIN workflow_instance wi ON cr.ID = wi.ChangeRequest_ID ");
        sqlBuilder.append("WHERE (cr.Process_Definition_ID = ? OR wi.Process_Definition_ID = ?) ");
        sqlBuilder.append("AND (crs.PrimaryName IS NULL OR ");
        sqlBuilder.append("     (LOWER(crs.PrimaryName) NOT LIKE '%completed%' AND ");
        sqlBuilder.append("      LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' AND ");
        sqlBuilder.append("      LOWER(crs.PrimaryName) NOT LIKE '%canceled%')) ");
        
        // Add facet filtering if facet is provided
        if (facet != null && !facet.trim().isEmpty()) {
            // Handle different facet name variations (e.g., "Data Set" vs "Dataset")
            sqlBuilder.append("AND (LOWER(cr.Reference) LIKE LOWER(?) OR ");
            sqlBuilder.append("     LOWER(cr.Reference) LIKE LOWER(?)) ");
        }
        
        sqlBuilder.append("ORDER BY cr.ID");

        String sql = sqlBuilder.toString();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int paramIndex = 1;
            stmt.setInt(paramIndex++, workflowId);
            stmt.setInt(paramIndex++, workflowId);
            
            // Add facet parameters if facet is provided
            if (facet != null && !facet.trim().isEmpty()) {
                String facetPattern = facet.trim();
                // Normalize facet name for matching (handle variations like "Data Set" -> "Dataset")
                String normalizedFacet = facetPattern;
                if (facetPattern.equalsIgnoreCase("Data Set")) {
                    normalizedFacet = "Dataset";
                }
                // Match CRs where Reference starts with facet name (e.g., "Glossary 123", "Dataset 456")
                stmt.setString(paramIndex++, normalizedFacet + " %");
                stmt.setString(paramIndex++, facetPattern + " %");
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject cr = new JsonObject();
                    cr.addProperty("id", rs.getInt("ID"));
                    cr.addProperty("title", rs.getString("PrimaryName"));
                    cr.addProperty("reference", rs.getString("Reference"));
                    cr.addProperty("status", rs.getString("statusName"));
                    activeCRs.add(cr);
                }
            }
        }

        boolean inUse = activeCRs.size() > 0;
        result.addProperty("inUse", inUse);
        result.addProperty("workflowId", workflowId);
        result.add("activeCRs", activeCRs);
        result.addProperty("activeCRCount", activeCRs.size());
        if (facet != null) {
            result.addProperty("facet", facet);
        }

        logger.info("Checked workflow {} usage for facet {}: inUse={}, activeCRCount={}", 
                    workflowId, facet, inUse, activeCRs.size());
        
        return result;
    }

    /**
     * Check if a facet has active change requests using default workflows (for creating or editing)
     * This is used to prevent disabling "Enable Workflow Approval" if there are active CRs
     * Active CRs are those that are NOT Completed or Cancelled
     */
    private JsonObject checkFacetWorkflowsInUse(String facet) throws SQLException {
        JsonObject result = new JsonObject();
        JsonArray activeCRs = new JsonArray();

        // First, get the default workflows for this facet from DF_CR table
        // Map facet name to module name (as DF_CR uses module ID as facet_id)
        String moduleName = mapFacetNameToModuleName(facet);
        
        String getWorkflowsSql = "SELECT workflow_create_id, workflow_edit_id FROM DF_CR WHERE facet_id = " +
                "(SELECT id FROM module WHERE primaryname = ?)";
        
        Integer workflowCreateId = null;
        Integer workflowEditId = null;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(getWorkflowsSql)) {
            
            stmt.setString(1, moduleName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int createId = rs.getInt("workflow_create_id");
                    if (!rs.wasNull()) {
                        workflowCreateId = createId;
                    }
                    int editId = rs.getInt("workflow_edit_id");
                    if (!rs.wasNull()) {
                        workflowEditId = editId;
                    }
                }
            }
        }
        
        // If no workflows are configured, there are no active CRs to check
        if (workflowCreateId == null && workflowEditId == null) {
            result.addProperty("inUse", false);
            result.addProperty("facet", facet);
            result.add("activeCRs", activeCRs);
            result.addProperty("activeCRCount", 0);
            logger.info("Checked facet {} workflows: no workflows configured, inUse=false", facet);
            return result;
        }
        
        // Build SQL query to find active CRs using either workflow for this facet
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT DISTINCT cr.ID, cr.PrimaryName, cr.Reference, crs.PrimaryName as statusName ");
        sqlBuilder.append("FROM changerequest cr ");
        sqlBuilder.append("LEFT JOIN changerequeststatus crs ON cr.CR_StatusID = crs.ID ");
        sqlBuilder.append("LEFT JOIN workflow_instance wi ON cr.ID = wi.ChangeRequest_ID ");
        sqlBuilder.append("WHERE (crs.PrimaryName IS NULL OR ");
        sqlBuilder.append("     (LOWER(crs.PrimaryName) NOT LIKE '%completed%' AND ");
        sqlBuilder.append("      LOWER(crs.PrimaryName) NOT LIKE '%cancelled%' AND ");
        sqlBuilder.append("      LOWER(crs.PrimaryName) NOT LIKE '%canceled%')) ");
        
        // Match CRs where Reference starts with facet name (e.g., "Glossary 123", "Dataset 456")
        String facetPattern = facet.trim();
        String normalizedFacet = facetPattern;
        if (facetPattern.equalsIgnoreCase("Data Set")) {
            normalizedFacet = "Dataset";
        }
        sqlBuilder.append("AND (LOWER(cr.Reference) LIKE LOWER(?) OR ");
        sqlBuilder.append("     LOWER(cr.Reference) LIKE LOWER(?)) ");
        
        // Check if CR uses either default workflow
        sqlBuilder.append("AND (");
        boolean hasCondition = false;
        if (workflowCreateId != null) {
            sqlBuilder.append("(cr.Process_Definition_ID = ? OR wi.Process_Definition_ID = ?)");
            hasCondition = true;
        }
        if (workflowEditId != null) {
            if (hasCondition) {
                sqlBuilder.append(" OR ");
            }
            sqlBuilder.append("(cr.Process_Definition_ID = ? OR wi.Process_Definition_ID = ?)");
        }
        sqlBuilder.append(") ");
        
        sqlBuilder.append("ORDER BY cr.ID");

        String sql = sqlBuilder.toString();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            int paramIndex = 1;
            stmt.setString(paramIndex++, normalizedFacet + " %");
            stmt.setString(paramIndex++, facetPattern + " %");
            
            if (workflowCreateId != null) {
                stmt.setInt(paramIndex++, workflowCreateId);
                stmt.setInt(paramIndex++, workflowCreateId);
            }
            if (workflowEditId != null) {
                stmt.setInt(paramIndex++, workflowEditId);
                stmt.setInt(paramIndex++, workflowEditId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    JsonObject cr = new JsonObject();
                    cr.addProperty("id", rs.getInt("ID"));
                    cr.addProperty("title", rs.getString("PrimaryName"));
                    cr.addProperty("reference", rs.getString("Reference"));
                    cr.addProperty("status", rs.getString("statusName"));
                    activeCRs.add(cr);
                }
            }
        }

        boolean inUse = activeCRs.size() > 0;
        result.addProperty("inUse", inUse);
        result.addProperty("facet", facet);
        result.add("activeCRs", activeCRs);
        result.addProperty("activeCRCount", activeCRs.size());
        if (workflowCreateId != null) {
            result.addProperty("workflowCreateId", workflowCreateId);
        }
        if (workflowEditId != null) {
            result.addProperty("workflowEditId", workflowEditId);
        }

        logger.info("Checked facet {} workflows usage: inUse={}, activeCRCount={}, workflowCreateId={}, workflowEditId={}", 
                    facet, inUse, activeCRs.size(), workflowCreateId, workflowEditId);
        
        return result;
    }

    /**
     * Map facet name to module name as it appears in the database
     * This matches the logic in DFCRDao.mapFacetNameToModuleName()
     */
    private String mapFacetNameToModuleName(String facetName) {
        if (facetName == null) {
            return null;
        }
        String normalized = facetName.trim();
        // Map facet names to module names
        switch (normalized.toLowerCase()) {
            case "glossary":
                return "Glossary";
            case "data set":
            case "dataset":
                return "Data Sets";
            case "process":
                return "Process";
            case "system":
                return "System";
            default:
                return normalized;
        }
    }
}

