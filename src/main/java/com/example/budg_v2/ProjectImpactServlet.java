package com.example.budg_v2;
import com.example.budg_v2.service.ProjectImpactService;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/project-impact/*")
public class ProjectImpactServlet extends HttpServlet {
    private ProjectImpactService projectImpactService;
    private Gson gson;

    private void writeValidationError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }
    
    /**
     * Safely extract projectId from request data, returning null if not present or invalid
     */
    private Integer getProjectIdSafely(Map<String, Object> data) {
        Object projectIdObj = data.get("projectId");
        if (projectIdObj == null) {
            return null;
        }
        if (projectIdObj instanceof Number) {
            return ((Number) projectIdObj).intValue();
        }
        if (projectIdObj instanceof String) {
            try {
                return Integer.parseInt((String) projectIdObj);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        this.projectImpactService = new ProjectImpactService();
        this.gson = new Gson();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            return;
        }

        try {
            String[] pathParts = pathInfo.substring(1).split("/");
            
            // Check for reverse lookup first: /api/project-impact/{entityType}s/{entityId}/projects
            if (pathParts.length >= 3) {
                if ("systems".equals(pathParts[0])) {
                    try {
                        int systemId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsBySystemId(systemId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid system ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("products".equals(pathParts[0])) {
                    try {
                        int productId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByProductId(productId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid product ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("businessareas".equals(pathParts[0]) || "business-areas".equals(pathParts[0])) {
                    try {
                        int businessAreaId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByBusinessAreaId(businessAreaId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid business area ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("legals".equals(pathParts[0]) || "legal-entities".equals(pathParts[0])) {
                    try {
                        int legalId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByLegalId(legalId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid legal ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("capabilities".equals(pathParts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByCapabilityId(capabilityId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid capability ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("clients".equals(pathParts[0])) {
                    try {
                        int clientId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByClientId(clientId, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid client ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("datasets".equals(pathParts[0])) {
                    try {
                        int datasetId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByDatasetId(datasetId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid dataset ID: " + pathParts[1] + "\"}");
                        return;
                    }
                } else if ("glossaries".equals(pathParts[0])) {
                    try {
                        int glossaryId = Integer.parseInt(pathParts[1]);
                        if ("projects".equals(pathParts[2])) {
                            handleGetProjectRelationshipsByGlossaryId(glossaryId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        response.getWriter().write("{\"error\":\"Invalid glossary ID: " + pathParts[1] + "\"}");
                        return;
                    }
                }
            }
            
            // Handle relationship type endpoints
            if (pathInfo.endsWith("/system-relation-types")) {
                handleGetSystemRelationTypes(response);
            } else if (pathInfo.endsWith("/process-relation-types")) {
                handleGetProcessRelationTypes(response);
            } else if (pathInfo.endsWith("/glossary-relation-types")) {
                handleGetGlossaryRelationTypes(response);
            } else if (pathInfo.endsWith("/policy-relation-types")) {
                handleGetPolicyRelationTypes(response);
            } else if (pathInfo.endsWith("/product-relation-types")) {
                handleGetProductRelationTypes(response);
            } else if (pathInfo.endsWith("/client-relation-types")) {
                handleGetClientRelationTypes(response);
            } else if (pathInfo.endsWith("/capability-relation-types")) {
                handleGetCapabilityRelationTypes(response);
            } else if (pathInfo.endsWith("/businessarea-relation-types")) {
                handleGetBusinessAreaRelationTypes(response);
            } else if (pathInfo.endsWith("/dataset-relation-types")) {
                handleGetDatasetRelationTypes(response);
            } else if (pathInfo.endsWith("/attribute-relation-types")) {
                handleGetAttributeRelationTypes(response);
            } else if (pathInfo.endsWith("/datasets-list")) {
                handleGetDatasetsList(response);
            } else if (pathInfo.endsWith("/attributes-list")) {
                handleGetAttributesList(response);
            }
            // Handle owner endpoints
            else if (pathInfo.matches(".*/system-owner/\\d+")) {
                int systemId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetSystemOwner(response, systemId);
            } else if (pathInfo.matches(".*/process-owner/\\d+")) {
                int processId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetProcessOwner(response, processId);
            } else if (pathInfo.matches(".*/glossary-owner/\\d+")) {
                int glossaryId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetGlossaryOwner(response, glossaryId);
            } else if (pathInfo.matches(".*/policy-owner/\\d+")) {
                int policyId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetPolicyOwner(response, policyId);
            } else if (pathInfo.matches(".*/product-owner/\\d+")) {
                int productId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetProductOwner(response, productId);
            } else if (pathInfo.matches(".*/client-owner/\\d+")) {
                int clientId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetClientOwner(response, clientId);
            } else if (pathInfo.matches(".*/capability-owner/\\d+")) {
                int capabilityId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetCapabilityOwner(response, capabilityId);
            } else if (pathInfo.matches(".*/businessarea-owner/\\d+")) {
                int businessAreaId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetBusinessAreaOwner(response, businessAreaId);
            } else if (pathInfo.matches(".*/dataset-owner/\\d+")) {
                int datasetId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetDatasetOwner(response, datasetId);
            } else if (pathInfo.matches(".*/attribute-owner/\\d+")) {
                int attributeId = Integer.parseInt(pathParts[pathParts.length - 1]);
                handleGetAttributeOwner(response, attributeId);
            }
            // Handle relationship data endpoints
            else if (pathInfo.matches(".*/\\d+/systems")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetSystemRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/processes")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetProcessRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/glossaries")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetGlossaryRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/policies")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetPolicyRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/products")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetProductRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/clients")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetClientRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/capabilities")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetCapabilityRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/businessareas")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetBusinessAreaRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/datasets")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetDatasetRelationships(response, projectId, request);
            } else if (pathInfo.matches(".*/\\d+/attributes")) {
                int projectId = Integer.parseInt(pathParts[0]);
                handleGetAttributeRelationships(response, projectId);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid ID format\"}");
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        if (pathInfo == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Invalid endpoint\"}");
            return;
        }

        // Get user ID from session, default to 1 if not available (matching ProcessImpactServlet and PolicyImpactServlet behavior)
        int userId = getUserIdFromSession(request);

        try {
            // Read request body
            BufferedReader reader = request.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            String requestBody = sb.toString();

            if (pathInfo.endsWith("/systems/save")) {
                handleSaveSystemRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/processes/save")) {
                handleSaveProcessRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/glossaries/save")) {
                handleSaveGlossaryRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/policies/save")) {
                handleSavePolicyRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/products/save")) {
                handleSaveProductRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/clients/save")) {
                handleSaveClientRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/capabilities/save")) {
                handleSaveCapabilityRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/businessareas/save")) {
                handleSaveBusinessAreaRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/datasets/save")) {
                handleSaveDatasetRelationships(request, response, requestBody, userId);
            } else if (pathInfo.endsWith("/attributes/save")) {
                handleSaveAttributeRelationships(request, response, requestBody, userId);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"error\":\"Endpoint not found\"}");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    // GET handlers for relation types
    private void handleGetSystemRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getSystemRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetProcessRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getProcessRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetGlossaryRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getGlossaryRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetPolicyRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getPolicyRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetProductRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getProductRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetClientRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        try {
            List<Map<String, Object>> relationTypes = projectImpactService.getClientRelationTypes();
            //system.out.println("ProjectImpactServlet: Retrieved " + relationTypes.size() + " client relation types");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("ProjectImpactServlet: Error in handleGetClientRelationTypes: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve client relation types\"}");
        }
    }

    private void handleGetCapabilityRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getCapabilityRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetBusinessAreaRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getBusinessAreaRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetDatasetRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getDatasetRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetAttributeRelationTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> relationTypes = projectImpactService.getAttributeRelationTypes();
        response.getWriter().write(gson.toJson(relationTypes));
    }

    private void handleGetDatasetsList(HttpServletResponse response) throws SQLException, IOException {
        try {
            //system.out.println("ProjectImpactServlet: Getting datasets list");
            List<Map<String, Object>> datasets = projectImpactService.getDatasetsList();
            //system.out.println("ProjectImpactServlet: Retrieved " + datasets.size() + " datasets");
            response.getWriter().write(gson.toJson(datasets));
        } catch (Exception e) {
            System.err.println("ProjectImpactServlet: Error getting datasets list: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Error getting datasets list: " + e.getMessage() + "\"}");
        }
    }

    private void handleGetAttributesList(HttpServletResponse response) throws SQLException, IOException {
        try {
            //system.out.println("ProjectImpactServlet: Getting attributes list");
            List<Map<String, Object>> attributes = projectImpactService.getAttributesList();
            //system.out.println("ProjectImpactServlet: Retrieved " + attributes.size() + " attributes");
            response.getWriter().write(gson.toJson(attributes));
        } catch (Exception e) {
            System.err.println("ProjectImpactServlet: Error getting attributes list: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Error getting attributes list: " + e.getMessage() + "\"}");
        }
    }

    // GET handlers for owners
    private void handleGetSystemOwner(HttpServletResponse response, int systemId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getSystemOwnerBySystemId(systemId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetProcessOwner(HttpServletResponse response, int processId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getProcessOwnerByProcessId(processId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetGlossaryOwner(HttpServletResponse response, int glossaryId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getGlossaryOwnerByGlossaryId(glossaryId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetPolicyOwner(HttpServletResponse response, int policyId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getPolicyOwnerByPolicyId(policyId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetProductOwner(HttpServletResponse response, int productId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getProductOwnerByProductId(productId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetClientOwner(HttpServletResponse response, int clientId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getClientOwnerByClientId(clientId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetCapabilityOwner(HttpServletResponse response, int capabilityId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getCapabilityOwnerByCapabilityId(capabilityId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetBusinessAreaOwner(HttpServletResponse response, int businessAreaId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getBusinessAreaOwnerByBusinessAreaId(businessAreaId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetDatasetOwner(HttpServletResponse response, int datasetId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getDatasetOwnerByDatasetId(datasetId);
        response.getWriter().write(gson.toJson(owner));
    }

    private void handleGetAttributeOwner(HttpServletResponse response, int attributeId) throws SQLException, IOException {
        Map<String, Object> owner = projectImpactService.getAttributeOwnerByAttributeId(attributeId);
        response.getWriter().write(gson.toJson(owner));
    }

    // GET handlers for relationships
    private void handleGetSystemRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getSystemRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetProcessRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getProcessRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetGlossaryRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getGlossaryRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetPolicyRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getPolicyRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetProductRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getProductRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetClientRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getClientRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetCapabilityRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getCapabilityRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetBusinessAreaRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getBusinessAreaRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetDatasetRelationships(HttpServletResponse response, int projectId, HttpServletRequest request) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getDatasetRelationshipsByProjectId(projectId);
        relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Dataset", "datasetId");
        response.getWriter().write(gson.toJson(relationships));
    }

    private void handleGetAttributeRelationships(HttpServletResponse response, int projectId) throws SQLException, IOException {
        List<Map<String, Object>> relationships = projectImpactService.getAttributeRelationshipsByProjectId(projectId);
        response.getWriter().write(gson.toJson(relationships));
    }

    // POST handlers for saving relationships
    private void handleSaveSystemRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                String requestBody, int userId) throws SQLException, IOException {
        //system.out.println("=== handleSaveSystemRelationships START ===");
        //system.out.println("Request body: " + requestBody);
        //system.out.println("User ID: " + userId);
        
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        
        // Check edit permission + stakeholder status
        if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Project", projectId)) {
            return; // Response already sent
        }
        
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        //system.out.println("Project ID: " + projectId);
        //system.out.println("Relationships count: " + (relationships != null ? relationships.size() : 0));
        if (relationships != null) {
            for (int i = 0; i < relationships.size(); i++) {
                //system.out.println("Relationship " + i + ": " + relationships.get(i));
            }
        }
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "System", "systemId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveSystemRelationships(projectId, relationships, userId);
        
        //system.out.println("Save result: " + success);
        //system.out.println("=== handleSaveSystemRelationships END ===");
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveProcessRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                 String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        
        // Check edit permission + stakeholder status
        if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Project", projectId)) {
            return; // Response already sent
        }
        
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Process", "processId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveProcessRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveGlossaryRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                  String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Glossary", "glossaryId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveGlossaryRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSavePolicyRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Policy", "policyId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.savePolicyRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                 String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Product", "productId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveProductRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveClientRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Client", "clientId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveClientRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveCapabilityRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                    String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Capability", "capabilityId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveCapabilityRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveBusinessAreaRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                      String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "BusinessArea", "businessAreaId", "businessareaId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveBusinessAreaRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveDatasetRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                 String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Dataset", "datasetId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveDatasetRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }

    private void handleSaveAttributeRelationships(HttpServletRequest request, HttpServletResponse response, 
                                                   String requestBody, int userId) throws SQLException, IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> data = gson.fromJson(requestBody, type);
        
        Integer projectId = getProjectIdSafely(data);
        if (projectId == null) {
            writeValidationError(response, "projectId is required and must be a valid number");
            return;
        }
        @SuppressWarnings("unchecked") List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.get("relationships");
        
        var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                projectId, "Project", relationships, "Attribute", "attributeId");
        if (!validationResult.isValid) {
            writeValidationError(response, validationResult.message);
            return;
        }

        boolean success = projectImpactService.saveAttributeRelationships(projectId, relationships, userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        response.getWriter().write(gson.toJson(result));
    }
    
    /**
     * Get user ID from session, default to 1 if not available (matching ProcessImpactServlet and PolicyImpactServlet behavior)
     */
    private int getUserIdFromSession(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("userId") != null) {
                Object userIdObj = session.getAttribute("userId");
                if (userIdObj instanceof Integer) {
                    return (Integer) userIdObj;
                } else if (userIdObj instanceof String) {
                    return Integer.parseInt((String) userIdObj);
                } else if (userIdObj instanceof Number) {
                    return ((Number) userIdObj).intValue();
                }
            }
            // Default to user ID 1 if not found (matching ProcessImpactServlet and PolicyImpactServlet)
            return 1;
        } catch (Exception e) {
            // Default to user ID 1 if any error occurs
            return 1;
        }
    }
    
    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetProjectRelationshipsBySystemId(int systemId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
    
    private void handleGetProjectRelationshipsByProductId(int productId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
    
    private void handleGetProjectRelationshipsByBusinessAreaId(int businessAreaId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
    
    private void handleGetProjectRelationshipsByLegalId(int legalId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Note: There is no direct project_x_legal table in the database
            // Projects are related to legal entities through clients or products
            // For now, return empty array. This can be enhanced later to find projects
            // through clients/products that are related to the legal entity
            List<Map<String, Object>> relationships = new ArrayList<>();
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(new com.google.gson.Gson().toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
    
    private void handleGetProjectRelationshipsByCapabilityId(int capabilityId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            com.example.budg_v2.service.ProjectImpactService projectImpactService = new com.example.budg_v2.service.ProjectImpactService();
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByCapabilityId(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(new com.google.gson.Gson().toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
    
    private void handleGetProjectRelationshipsByClientId(int clientId, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByClientId(clientId);
            response.getWriter().write(new com.google.gson.Gson().toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
    
    private void handleGetProjectRelationshipsByDatasetId(int datasetId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByDatasetId(datasetId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(new com.google.gson.Gson().toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }

    private void handleGetProjectRelationshipsByGlossaryId(int glossaryId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByGlossaryId(glossaryId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(new com.google.gson.Gson().toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Failed to retrieve project relationships: " + e.getMessage() + "\"}");
        }
    }
}

