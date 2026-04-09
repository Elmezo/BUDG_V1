package com.example.budg_v2;

import com.example.budg_v2.dao.*;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.util.BpmnParser;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Servlet for workflow validation operations
 * Validates that all roles required by a workflow have stakeholders on the
 * related object
 * 
 * Endpoints:
 * GET
 * /api/workflow/validate-start?changeRequestId={crId}&processDefId={procDefId}
 */
@WebServlet("/api/workflow/*")
public class WorkflowValidationServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowValidationServlet.class);
    private final Gson gson = new Gson();
    private final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    private final BpmnContentDAO bpmnDAO = new BpmnContentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/validate-start")) {
                handleValidateStart(request, response);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid endpoint");
                response.getWriter().write(gson.toJson(error));
            }

        } catch (Exception e) {
            logger.error("Error in workflow validation", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Validate workflow start requirements
     * GET
     * /api/workflow/validate-start?changeRequestId={crId}&processDefId={procDefId}
     */
    private void handleValidateStart(HttpServletRequest request, HttpServletResponse response)
            throws IOException, SQLException {

        // Get parameters
        String crIdParam = request.getParameter("changeRequestId");
        String procDefIdParam = request.getParameter("processDefId");

        if (crIdParam == null || procDefIdParam == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Missing required parameters: changeRequestId and processDefId");
            response.getWriter().write(gson.toJson(error));
            return;
        }

        try {
            int changeRequestId = Integer.parseInt(crIdParam);
            int processDefId = Integer.parseInt(procDefIdParam);

            // Step 1: Get change request to extract object reference
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Step 2: Parse reference to get facet type and ID
            String reference = changeRequest.getReference();
            if (reference == null || reference.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request has no object reference");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Parse reference format: "FacetType FacetId" (e.g., "Dataset 47", "System 5")
            String[] parts = reference.trim().split("\\s+");
            if (parts.length < 2) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid reference format: " + reference);
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Extract facet type and ID
            String facetId = parts[parts.length - 1]; // Last part is ID
            StringBuilder facetTypeBuilder = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0)
                    facetTypeBuilder.append(" ");
                facetTypeBuilder.append(parts[i]);
            }
            String facetType = facetTypeBuilder.toString().toLowerCase().replace(" ", "");
            int objectId = Integer.parseInt(facetId);

            logger.info("Validating workflow start for CR {}: facetType={}, objectId={}",
                    changeRequestId, facetType, objectId);

            // Step 3: Load BPMN XML
            String bpmnXml = bpmnDAO.getXmlContent(processDefId);
            if (bpmnXml == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "BPMN definition not found for process: " + processDefId);
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Step 4: Extract required roles from BPMN
            Set<String> requiredRoles = extractRequiredRoles(bpmnXml);
            logger.info("Required roles from BPMN: {}", requiredRoles);

            // Step 5: Get stakeholders for the object
            List<Map<String, Object>> stakeholders = getStakeholdersForObject(facetType, objectId);
            logger.info("Found {} stakeholders for {} {}: {}", stakeholders.size(), facetType, objectId, stakeholders);

            // Step 6: Get creator ID for Requestor role validation (changeRequest already loaded in Step 1)
            Integer creatorId = changeRequest.getCreatedBy();
            
            // Step 7: Validate role coverage (with special handling for Requestor role)
            Map<String, Object> validationResult = validateRoleCoverage(requiredRoles, stakeholders, creatorId);

            // Step 8: Return validation result
            response.getWriter().write(gson.toJson(validationResult));

        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid ID format");
            response.getWriter().write(gson.toJson(error));

        } catch (Exception e) {
            logger.error("Error validating workflow start", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Validation error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Extract required roles from BPMN XML
     * Roles are extracted from:
     * 1. User task properties (camunda:candidateGroups / role)
     * 2. BPMN Lanes (if task is inside a lane, the lane name is the role)
     */
    private Set<String> extractRequiredRoles(String bpmnXml) throws Exception {
        Set<String> roles = new HashSet<>();

        // Parse BPMN XML
        Document doc = BpmnParser.parseXml(bpmnXml);

        // Map task IDs to Lane Roles
        Map<String, String> taskLaneRoles = new HashMap<>();
        List<Element> lanes = BpmnParser.findLanes(doc);
        logger.info("Found {} lanes in BPMN", lanes.size());

        for (Element lane : lanes) {
            String laneRole = BpmnParser.getLaneName(lane);
            if (laneRole != null && !laneRole.trim().isEmpty()) {
                List<String> nodeIds = BpmnParser.getFlowNodesInLane(lane);
                logger.info("Lane '{}' contains nodes: {}", laneRole, nodeIds);
                for (String nodeId : nodeIds) {
                    taskLaneRoles.put(nodeId, laneRole.trim());
                }
            }
        }

        // Find all user tasks
        List<Element> userTasks = BpmnParser.findUserTasks(doc);
        logger.info("Found {} user tasks", userTasks.size());

        for (Element userTask : userTasks) {
            String taskId = userTask.getAttribute("id");
            // Extract task properties
            Map<String, String> properties = BpmnParser.extractTaskProperties(userTask);
            String taskRole = properties.get("role"); // from camunda:candidateGroups

            if (taskRole != null && !taskRole.trim().isEmpty()) {
                // Priority 1: Explicit role on task
                String[] roleNames = taskRole.split(",");
                for (String roleName : roleNames) {
                    String trimmedRole = roleName.trim();
                    if (!trimmedRole.isEmpty()) {
                        roles.add(trimmedRole);
                    }
                }
            } else {
                // Priority 2: Role from Lane
                String laneRole = taskLaneRoles.get(taskId);
                logger.info("Checking lane role for task {}: {}", taskId, laneRole);

                if (laneRole != null && !laneRole.isEmpty()) {
                    roles.add(laneRole);
                }
            }
        }

        return roles;
    }

    /**
     * Get stakeholders for an object based on facet type
     */
    private List<Map<String, Object>> getStakeholdersForObject(String facetType, int objectId) throws SQLException {
        switch (facetType) {
            case "dataset":
                DatasetDAO datasetDAO = new DatasetDAO();
                return datasetDAO.getDirectStakeholdersForDataset(objectId);

            case "system":
                SystemDAO systemDAO = new SystemDAO();
                return systemDAO.getDirectStakeholdersForSystem(objectId);

            case "glossary":
                GlossaryDAO glossaryDAO = new GlossaryDAO();
                return glossaryDAO.getDirectStakeholdersForGlossary(objectId);

            case "systeminterface":
            case "interface":
                InterfaceDAO interfaceDAO = new InterfaceDAO();
                return interfaceDAO.getDirectStakeholdersForInterface(objectId);

            case "regulation":
                RegulationDAO regulationDAO = new RegulationDAO();
                return regulationDAO.getDirectStakeholdersForRegulation(objectId);

            case "businessarea":
            case "business_area":
                return getDirectStakeholdersForBusinessArea(objectId);

            case "process":
                return getDirectStakeholdersForProcess(objectId);

            default:
                logger.warn("Unknown facet type for stakeholder retrieval: {}", facetType);
                return new ArrayList<>();
        }
    }

    /**
     * Get direct stakeholders for a Business Area
     */
    private List<Map<String, Object>> getDirectStakeholdersForBusinessArea(int businessAreaId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        logger.info("Fetching stakeholders for Business Area ID: {}", businessAreaId);
        
        String sql = """
            SELECT 
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId,
                ips.primaryname AS statusName
            FROM businessarea_x_objectxpeople bxop
            JOIN object_x_people oxp ON oxp.id = bxop.Object_x_ipid
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            LEFT JOIN object_x_ip_status ips ON ips.id = oxp.statusID
            WHERE bxop.BusinessAreaID = ?
            ORDER BY orl.primaryname, p.First_Name, p.Last_Name
            """;

        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, businessAreaId);
            
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    String role = rs.getString("role");
                    stakeholder.put("role", role);
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholder.put("statusName", rs.getString("statusName"));
                    stakeholders.add(stakeholder);
                    logger.debug("Found stakeholder: role={}, name={}", role, rs.getString("name"));
                }
            }
        }
        
        logger.info("Retrieved {} stakeholders for Business Area {}", stakeholders.size(), businessAreaId);
        return stakeholders;
    }

    /**
     * Get direct stakeholders for a Process
     */
    private List<Map<String, Object>> getDirectStakeholdersForProcess(int processId) throws SQLException {
        List<Map<String, Object>> stakeholders = new ArrayList<>();
        
        logger.info("Fetching stakeholders for Process ID: {}", processId);
        
        String sql = """
            SELECT 
                orl.primaryname AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                p.ID AS personId,
                COALESCE(ou.Name, '') AS orgUnit,
                ou.ID AS orgUnitId,
                ips.primaryname AS statusName
            FROM process_x_objectxpeople pxop
            JOIN object_x_people oxp ON oxp.id = pxop.object_x_ip
            JOIN people p ON p.ID = oxp.ipid
            JOIN object_role orl ON orl.id = oxp.roleID
            LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID
            LEFT JOIN object_x_ip_status ips ON ips.id = oxp.statusID
            WHERE pxop.process_id = ?
            ORDER BY orl.primaryname, p.First_Name, p.Last_Name
            """;

        try (java.sql.Connection conn = com.example.budg_v2.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, processId);
            
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> stakeholder = new HashMap<>();
                    String role = rs.getString("role");
                    stakeholder.put("role", role);
                    stakeholder.put("name", rs.getString("name"));
                    stakeholder.put("personId", rs.getInt("personId"));
                    stakeholder.put("orgUnit", rs.getString("orgUnit"));
                    stakeholder.put("orgUnitId", rs.getObject("orgUnitId"));
                    stakeholder.put("statusName", rs.getString("statusName"));
                    stakeholders.add(stakeholder);
                    logger.debug("Found stakeholder: role={}, name={}", role, rs.getString("name"));
                }
            }
        }
        
        logger.info("Retrieved {} stakeholders for Process {}", stakeholders.size(), processId);
        return stakeholders;
    }

    /**
     * Validate that all required roles have at least one stakeholder
     * CRITICAL: Normalizes role names for consistent comparison
     * SPECIAL HANDLING: Requestor role is automatically satisfied if creatorId is provided
     */
    private Map<String, Object> validateRoleCoverage(Set<String> requiredRoles,
            List<Map<String, Object>> stakeholders, Integer creatorId) {
        Map<String, Object> result = new HashMap<>();

        // Build set of available roles from stakeholders (NORMALIZED)
        Set<String> availableRoles = new HashSet<>();
        for (Map<String, Object> stakeholder : stakeholders) {
            String role = (String) stakeholder.get("role");
            if (role != null && !role.trim().isEmpty()) {
                // Normalize role: UPPERCASE, NO SPACES, UNDERSCORES ONLY
                String normalizedRole = normalizeRole(role);
                availableRoles.add(normalizedRole);
            }
        }

        // Special handling: Requestor role is automatically satisfied if creator exists
        // The Requestor role doesn't need a stakeholder - it refers to the change request creator
        if (creatorId != null) {
            availableRoles.add("REQUESTOR");
            logger.info("Requestor role automatically satisfied - creator ID: {}", creatorId);
        }

        // Find missing roles (NORMALIZED COMPARISON)
        Set<String> missingRoles = new HashSet<>();
        for (String requiredRole : requiredRoles) {
            // Normalize required role for comparison
            String normalizedRequired = normalizeRole(requiredRole);
            
            // Skip Requestor role if creator exists (already added to availableRoles)
            if ("REQUESTOR".equals(normalizedRequired) && creatorId != null) {
                continue; // Requestor is satisfied
            }
            
            if (!availableRoles.contains(normalizedRequired)) {
                missingRoles.add(requiredRole); // Return original format for error message
            }
        }

        // Build result
        boolean valid = missingRoles.isEmpty();
        result.put("valid", valid);
        result.put("requiredRoles", new ArrayList<>(requiredRoles));
        result.put("objectStakeholders", stakeholders);
        result.put("missingRoles", new ArrayList<>(missingRoles));

        return result;
    }

    /**
     * Normalize role name for consistent comparison
     * Format: UPPERCASE, NO SPACES, UNDERSCORES ONLY
     * 
     * Examples:
     * "Data Owner" -> "DATA_OWNER"
     * "13:Data Owner" -> "DATA_OWNER"
     * "R01:Requestor" -> "REQUESTOR"
     */
    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "";
        }

        String cleanRole = role.trim();

        // Remove prefix like "13:" or "R01:" if present
        if (cleanRole.contains(":")) {
            cleanRole = cleanRole.substring(cleanRole.indexOf(":") + 1);
        }

        return cleanRole.trim()
                .toUpperCase()
                .replaceAll("\\s+", "_") // Replace spaces with underscores
                .replaceAll("-", "_"); // Replace dashes with underscores
    }
}
