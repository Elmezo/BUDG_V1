package com.example.budg_v2;

import com.example.budg_v2.dao.BpmnContentDAO;
import com.example.budg_v2.dao.ProcessDefinitionDAO;
import com.example.budg_v2.dao.ProcessDefinitionObjectScopeDAO;
import com.example.budg_v2.dao.WorkflowMappingDAO;
import com.example.budg_v2.model.ProcessDefinition;
import com.example.budg_v2.model.ProcessDefinitionObjectScope;
import com.example.budg_v2.model.WorkflowMapping;
import com.example.budg_v2.util.BpmnFileManager;
import com.example.budg_v2.util.BpmnParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * Object-private workflows: CRUD and BPMN save for workflows scoped to a facet instance.
 * <p>
 * GET /api/object_workflows?mode=view|edit&amp;facetType=...&amp;objectId=...&amp;entityId=... — list definitions<br>
 * POST /api/object_workflows — create scoped workflow (JSON body)<br>
 * PUT /api/object_workflows/{id} — update if scoped to object (JSON body includes facetType, objectId, entityId)<br>
 * GET/POST /api/object_workflows/{id}/bpmn — read/save BPMN for scoped workflow (save requires facetType+objectId match)
 */
@WebServlet(name = "ObjectWorkflowServlet", urlPatterns = {"/api/object_workflows", "/api/object_workflows/*"})
public class ObjectWorkflowServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ObjectWorkflowServlet.class);
    private static final Set<String> ALLOWED_FACET_TYPES = new HashSet<>(Arrays.asList(
            "dataset", "system", "capability", "client", "product", "system-interface", "policy", "committee",
            "process", "business-area", "glossary", "geography", "legal-entity", "org-unit", "project",
            "regulation", "regulator", "regulatory-theme"
    ));

    private final Gson gson = new Gson();
    private final ProcessDefinitionDAO processDefinitionDAO = new ProcessDefinitionDAO();
    private final ProcessDefinitionObjectScopeDAO scopeDAO = new ProcessDefinitionObjectScopeDAO();
    private final WorkflowMappingDAO workflowMappingDAO = new WorkflowMappingDAO();
    private final BpmnContentDAO bpmnContentDAO = new BpmnContentDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        if (pathInfo != null && pathInfo.endsWith("/bpmn")) {
            handleBpmnGet(request, response);
            return;
        }

        try {
            String mode = request.getParameter("mode");
            String facetType = request.getParameter("facetType");
            String objectIdStr = request.getParameter("objectId");
            String entityIdStr = request.getParameter("entityId");

            if (facetType == null || objectIdStr == null || entityIdStr == null
                    || facetType.isBlank() || objectIdStr.isBlank() || entityIdStr.isBlank()) {
                badRequest(response, "facetType, objectId, and entityId are required");
                return;
            }
            if (!isAllowedFacetType(facetType)) {
                badRequest(response, "Invalid facetType");
                return;
            }
            int objectId = Integer.parseInt(objectIdStr);
            int moduleEntityId = Integer.parseInt(entityIdStr);

            List<ProcessDefinition> list;
            if ("edit".equalsIgnoreCase(mode)) {
                list = listForEdit(facetType, objectId);
            } else {
                // default: view (combined global + this object's private workflows)
                list = listForView(moduleEntityId, facetType, objectId);
            }
            response.getWriter().write(gson.toJson(list));

        } catch (NumberFormatException e) {
            badRequest(response, "Invalid numeric parameter");
        } catch (SQLException e) {
            logger.error("ObjectWorkflowServlet GET", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String pathInfo = request.getPathInfo();
        if (pathInfo != null && pathInfo.endsWith("/bpmn")) {
            handleBpmnPost(request, response);
            return;
        }

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!isAuthenticated(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        try {
            JsonObject body = gson.fromJson(request.getReader(), JsonObject.class);
            if (body == null) {
                badRequest(response, "JSON body required");
                return;
            }
            String facetType = getString(body, "facetType");
            int objectId = body.get("objectId").getAsInt();
            int entityId = body.get("entityId").getAsInt();
            String primaryName = getString(body, "primaryName");
            String description = getString(body, "description");

            if (!isAllowedFacetType(facetType)) {
                badRequest(response, "Invalid facetType");
                return;
            }
            if (primaryName == null || primaryName.length() < 6) {
                badRequest(response, "Workflow name must be at least 6 characters");
                return;
            }
            if (description == null || description.length() < 6) {
                badRequest(response, "Description must be at least 6 characters");
                return;
            }
            if (!processDefinitionDAO.isNameUnique(primaryName, entityId, null)) {
                badRequest(response, "Workflow name must be unique for this facet");
                return;
            }

            ProcessDefinition pd = new ProcessDefinition();
            pd.setPrimaryName(primaryName);
            pd.setReference(body.has("reference") && !body.get("reference").isJsonNull()
                    ? body.get("reference").getAsString() : primaryName);
            pd.setDescription(description);
            pd.setEntityId(entityId);
            pd.setDefault(false);
            String status = body.has("status") && !body.get("status").isJsonNull()
                    ? body.get("status").getAsString() : "Enabled";
            pd.setStatus(status);

            HttpSession session = request.getSession(false);
            int userId = 0;
            if (session != null && session.getAttribute("userId") != null) {
                userId = (Integer) session.getAttribute("userId");
            }
            pd.setLastUserChange(userId);

            int newId = processDefinitionDAO.create(pd);
            pd.setId(newId);
            scopeDAO.insert(newId, facetType, objectId);

            saveCrTypeMappingsFromBody(body, newId, entityId, userId);

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write(gson.toJson(pd));

        } catch (SQLException e) {
            logger.error("ObjectWorkflowServlet POST create", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        } catch (Exception e) {
            logger.error("ObjectWorkflowServlet POST", e);
            badRequest(response, e.getMessage() != null ? e.getMessage() : "Bad request");
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!isAuthenticated(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            badRequest(response, "ID required in path");
            return;
        }

        try {
            String idPart = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            if (idPart.contains("/")) {
                idPart = idPart.substring(0, idPart.indexOf('/'));
            }
            int id = Integer.parseInt(idPart);

            JsonObject body = gson.fromJson(request.getReader(), JsonObject.class);
            if (body == null) {
                badRequest(response, "JSON body required");
                return;
            }
            String facetType = getString(body, "facetType");
            int objectId = body.get("objectId").getAsInt();
            int entityId = body.get("entityId").getAsInt();

            if (!isAllowedFacetType(facetType)) {
                badRequest(response, "Invalid facetType");
                return;
            }
            if (!isScopedToObject(id, facetType, objectId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Workflow is not an object-private workflow for this object\"}");
                return;
            }

            ProcessDefinition existing = processDefinitionDAO.findById(id);
            if (existing == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"Process definition not found\"}");
                return;
            }
            if (existing.getEntityId() != entityId) {
                badRequest(response, "entityId does not match workflow facet module");
                return;
            }

            String primaryName = getString(body, "primaryName");
            String description = getString(body, "description");
            if (primaryName == null || primaryName.length() < 6) {
                badRequest(response, "Workflow name must be at least 6 characters");
                return;
            }
            if (description == null || description.length() < 6) {
                badRequest(response, "Description must be at least 6 characters");
                return;
            }
            if (!processDefinitionDAO.isNameUnique(primaryName, entityId, id)) {
                badRequest(response, "Workflow name must be unique for this facet");
                return;
            }

            ProcessDefinition pd = new ProcessDefinition();
            pd.setId(id);
            pd.setPrimaryName(primaryName);
            pd.setReference(body.has("reference") && !body.get("reference").isJsonNull()
                    ? body.get("reference").getAsString() : primaryName);
            pd.setDescription(description);
            pd.setEntityId(entityId);
            pd.setDefault(body.has("isDefault") && !body.get("isDefault").isJsonNull() && body.get("isDefault").getAsBoolean());
            String status = body.has("status") && !body.get("status").isJsonNull()
                    ? body.get("status").getAsString() : "Enabled";
            pd.setStatus(status);

            HttpSession session = request.getSession(false);
            int userId = 0;
            if (session != null && session.getAttribute("userId") != null) {
                userId = (Integer) session.getAttribute("userId");
            }
            pd.setLastUserChange(userId);

            processDefinitionDAO.update(pd);

            saveCrTypeMappingsFromBody(body, id, entityId, userId);

            ProcessDefinition updated = processDefinitionDAO.findById(id);
            response.getWriter().write(gson.toJson(updated != null ? updated : pd));

        } catch (NumberFormatException e) {
            badRequest(response, "Invalid ID");
        } catch (SQLException e) {
            logger.error("ObjectWorkflowServlet PUT", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    private void handleBpmnGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        try {
            int processDefId = extractIdBeforeBpmn(request.getPathInfo());
            String facetType = request.getParameter("facetType");
            String objectIdStr = request.getParameter("objectId");

            if (facetType != null && objectIdStr != null && !facetType.isBlank() && !objectIdStr.isBlank()) {
                int objectId = Integer.parseInt(objectIdStr);
                if (!isScopedToObject(processDefId, facetType, objectId)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"BPMN not available for this object workflow\"}");
                    return;
                }
            }

            String xmlContent = bpmnContentDAO.getXmlContent(processDefId);
            if (xmlContent == null) {
                xmlContent = BpmnFileManager.loadBpmnFile(processDefId);
            }
            if (xmlContent == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"BPMN definition not found\"}");
                return;
            }
            JsonObject result = new JsonObject();
            result.addProperty("xml", xmlContent);
            response.getWriter().write(gson.toJson(result));
        } catch (NumberFormatException e) {
            badRequest(response, "Invalid id");
        } catch (SQLException e) {
            logger.error("handleBpmnGet", e);
            error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error: " + e.getMessage());
        }
    }

    private void handleBpmnPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (!isAuthenticated(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Authentication required\"}");
            return;
        }

        try {
            int processDefId = extractIdBeforeBpmn(request.getPathInfo());
            JsonObject requestBody = gson.fromJson(request.getReader(), JsonObject.class);
            if (requestBody == null || !requestBody.has("xml") || requestBody.get("xml").isJsonNull()) {
                badRequest(response, "XML content is required");
                return;
            }
            String facetType = getString(requestBody, "facetType");
            if (!requestBody.has("objectId") || requestBody.get("objectId").isJsonNull()) {
                badRequest(response, "objectId is required");
                return;
            }
            int objectId = requestBody.get("objectId").getAsInt();
            if (facetType == null || facetType.isBlank() || !isAllowedFacetType(facetType)) {
                badRequest(response, "facetType is required");
                return;
            }
            if (!isScopedToObject(processDefId, facetType, objectId)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("{\"error\":\"Not an object-private workflow for this object\"}");
                return;
            }

            String xmlContent = requestBody.get("xml").getAsString();
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                badRequest(response, "XML content cannot be empty");
                return;
            }
            if (!BpmnParser.hasAtLeastOneEndEvent(xmlContent)) {
                badRequest(response, "BPMN must contain at least one end event");
                return;
            }

            bpmnContentDAO.saveOrUpdate(processDefId, xmlContent);
            BpmnFileManager.saveBpmnFile(processDefId, xmlContent);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("path", BpmnFileManager.getAbsolutePath(processDefId));
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            logger.warn("handleBpmnPost: {}", e.getMessage());
            badRequest(response, "Invalid BPMN XML: " + e.getMessage());
        }
    }

    private List<ProcessDefinition> listForView(int moduleEntityId, String facetType, int objectId)
            throws SQLException {
        return processDefinitionDAO.findByEntityIdForObjectWorkflowView(moduleEntityId, facetType, objectId);
    }

    private List<ProcessDefinition> listForEdit(String facetType, int objectId) throws SQLException {
        List<Integer> ids = scopeDAO.findProcessDefinitionIdsForObject(facetType, objectId);
        List<ProcessDefinition> out = new ArrayList<>();
        for (int id : ids) {
            ProcessDefinition pd = processDefinitionDAO.findById(id);
            if (pd != null) {
                out.add(pd);
            }
        }
        return out;
    }

    private boolean isScopedToObject(int processDefinitionId, String facetType, int objectId)
            throws SQLException {
        Optional<ProcessDefinitionObjectScope> sc = scopeDAO.findByProcessDefinitionId(processDefinitionId);
        return sc.filter(s -> facetType.equals(s.getFacetType()) && objectId == s.getObjectId()).isPresent();
    }

    private void saveCrTypeMappingsFromBody(JsonObject body, int processDefId,
                                            int entityId, int userId) throws SQLException {
        if (!body.has("crTypes") || !body.get("crTypes").isJsonArray()) {
            return;
        }
        JsonArray crTypesArray = body.get("crTypes").getAsJsonArray();
        if (crTypesArray.size() == 0) {
            return;
        }
        workflowMappingDAO.deleteByProcessDefinitionId(processDefId);
        for (int i = 0; i < crTypesArray.size(); i++) {
            String crType = crTypesArray.get(i).getAsString();
            if ("Type 2".equals(crType) || "2".equals(crType)) {
                WorkflowMapping m1 = new WorkflowMapping(processDefId, "Type 1", entityId);
                m1.setLastUserChange(userId);
                workflowMappingDAO.create(m1);
                WorkflowMapping m2 = new WorkflowMapping(processDefId, "Type 2", entityId);
                m2.setLastUserChange(userId);
                workflowMappingDAO.create(m2);
            } else {
                WorkflowMapping mapping = new WorkflowMapping(processDefId, crType, entityId);
                mapping.setLastUserChange(userId);
                workflowMappingDAO.create(mapping);
            }
        }
    }

    private static int extractIdBeforeBpmn(String pathInfo) {
        if (pathInfo == null || pathInfo.isEmpty()) {
            throw new IllegalArgumentException("Missing path");
        }
        String p = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        if (p.endsWith("/bpmn")) {
            p = p.substring(0, p.length() - 5);
        }
        int slash = p.indexOf('/');
        if (slash > 0) {
            p = p.substring(0, slash);
        }
        return Integer.parseInt(p);
    }

    private static boolean isAuthenticated(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid instanceof Integer && (Integer) uid > 0;
    }

    private static boolean isAllowedFacetType(String facetType) {
        return facetType != null && ALLOWED_FACET_TYPES.contains(facetType.trim().toLowerCase(Locale.ROOT));
    }

    private static String getString(JsonObject body, String key) {
        if (!body.has(key) || body.get(key).isJsonNull()) {
            return null;
        }
        return body.get(key).getAsString();
    }

    private static void badRequest(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        response.getWriter().write(err.toString());
    }

    private static void error(HttpServletResponse response, int code, String msg) throws IOException {
        response.setStatus(code);
        JsonObject err = new JsonObject();
        err.addProperty("error", msg);
        response.getWriter().write(err.toString());
    }
}
