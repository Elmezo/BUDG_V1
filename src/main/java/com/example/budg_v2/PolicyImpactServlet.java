package com.example.budg_v2;

import com.example.budg_v2.service.PolicyImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/policy-impact/*")
public class PolicyImpactServlet extends HttpServlet {
    private final PolicyImpactService policyImpactService;
    private final Gson gson = new Gson();

    public PolicyImpactServlet() {
        this.policyImpactService = new PolicyImpactService();
    }

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

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(response, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            // Check for reverse lookup first: /api/policy-impact/{entityType}s/{entityId}/policies
            if (parts.length >= 3) {
                if ("products".equals(parts[0])) {
                    try {
                        int productId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByProductId(productId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid product ID: " + parts[1], 400);
                        return;
                    }
                } else if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByClientId(clientId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("processes".equals(parts[0])) {
                    try {
                        int processId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByProcessId(processId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid process ID: " + parts[1], 400);
                        return;
                    }
                } else if ("projects".equals(parts[0])) {
                    try {
                        int projectId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByProjectId(projectId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid project ID: " + parts[1], 400);
                        return;
                    }
                } else if ("systems".equals(parts[0])) {
                    try {
                        int systemId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsBySystemId(systemId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid system ID: " + parts[1], 400);
                        return;
                    }
                } else if ("businessareas".equals(parts[0])) {
                    try {
                        int businessAreaId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByBusinessAreaId(businessAreaId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid business area ID: " + parts[1], 400);
                        return;
                    }
                } else if ("legals".equals(parts[0]) || "legal-entities".equals(parts[0])) {
                    try {
                        int legalId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByLegalId(legalId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid legal ID: " + parts[1], 400);
                        return;
                    }
                } else if ("glossaries".equals(parts[0])) {
                    try {
                        int glossaryId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByGlossaryId(glossaryId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid glossary ID: " + parts[1], 400);
                        return;
                    }
                } else if ("datasets".equals(parts[0])) {
                    try {
                        int datasetId = Integer.parseInt(parts[1]);
                        if ("policies".equals(parts[2])) {
                            handleGetPolicyRelationshipsByDatasetId(datasetId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid dataset ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("health".equals(parts[0])) {
                handleHealthCheck(response);
            } else if ("product-relation-types".equals(parts[0])) {
                handleGetProductRelationTypes(response);
            } else if ("client-relation-types".equals(parts[0])) {
                handleGetClientRelationTypes(response);
            } else if ("product-owner".equals(parts[0]) && parts.length >= 2) {
                int productId = Integer.parseInt(parts[1]);
                handleGetProductOwner(productId, response);
            } else if ("client-owner".equals(parts[0]) && parts.length >= 2) {
                int clientId = Integer.parseInt(parts[1]);
                handleGetClientOwner(clientId, response);
            } else if ("process-relation-types".equals(parts[0])) {
                handleGetProcessRelationTypes(response);
            } else if ("project-relation-types".equals(parts[0])) {
                handleGetProjectRelationTypes(response);
            } else if ("process-owner".equals(parts[0]) && parts.length >= 2) {
                int processId = Integer.parseInt(parts[1]);
                handleGetProcessOwner(processId, response);
            } else if ("project-owner".equals(parts[0]) && parts.length >= 2) {
                int projectId = Integer.parseInt(parts[1]);
                handleGetProjectOwner(projectId, response);
            } else if ("system-relation-types".equals(parts[0])) {
                handleGetSystemRelationTypes(response);
            } else if ("businessarea-relation-types".equals(parts[0])) {
                handleGetBusinessAreaRelationTypes(response);
            } else if ("legal-relation-types".equals(parts[0])) {
                handleGetLegalRelationTypes(response);
            } else if ("dataset-relation-types".equals(parts[0])) {
                handleGetDatasetRelationTypes(response);
            } else if ("attribute-relation-types".equals(parts[0])) {
                handleGetAttributeRelationTypes(response);
            } else if ("datasets-list".equals(parts[0])) {
                handleGetDatasetsList(response);
            } else if ("attributes-list".equals(parts[0])) {
                handleGetAttributesList(response);
            } else if ("system-owner".equals(parts[0]) && parts.length >= 2) {
                int systemId = Integer.parseInt(parts[1]);
                handleGetSystemOwner(systemId, response);
            } else if ("businessarea-owner".equals(parts[0]) && parts.length >= 2) {
                int businessAreaId = Integer.parseInt(parts[1]);
                handleGetBusinessAreaOwner(businessAreaId, response);
            } else if ("legal-owner".equals(parts[0]) && parts.length >= 2) {
                int legalId = Integer.parseInt(parts[1]);
                handleGetLegalOwner(legalId, response);
            } else if ("dataset-owner".equals(parts[0]) && parts.length >= 2) {
                int datasetId = Integer.parseInt(parts[1]);
                handleGetDatasetOwner(datasetId, response);
            } else if ("attribute-owner".equals(parts[0]) && parts.length >= 2) {
                int attributeId = Integer.parseInt(parts[1]);
                handleGetAttributeOwner(attributeId, response);
            } else if ("glossary-relation-types".equals(parts[0])) {
                handleGetGlossaryRelationTypes(response);
            } else if ("glossary-owner".equals(parts[0]) && parts.length >= 2) {
                int glossaryId = Integer.parseInt(parts[1]);
                handleGetGlossaryOwner(glossaryId, response);
            } else if (parts.length >= 2) {
                int policyId = Integer.parseInt(parts[0]);
                
                if ("products".equals(parts[1])) {
                    handleGetProductRelationships(policyId, response, request);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(policyId, response, request);
                } else if ("processes".equals(parts[1])) {
                    handleGetProcessRelationships(policyId, response, request);
                } else if ("projects".equals(parts[1])) {
                    handleGetProjectRelationships(policyId, response, request);
                } else if ("systems".equals(parts[1])) {
                    handleGetSystemRelationships(policyId, response, request);
                } else if ("businessareas".equals(parts[1])) {
                    handleGetBusinessAreaRelationships(policyId, response, request);
                } else if ("legals".equals(parts[1])) {
                    handleGetLegalRelationships(policyId, response, request);
                } else if ("datasets".equals(parts[1])) {
                    handleGetDatasetRelationships(policyId, response, request);
                } else if ("attributes".equals(parts[1])) {
                    handleGetAttributeRelationships(policyId, response);
                } else if ("glossaries".equals(parts[1])) {
                    handleGetGlossaryRelationships(policyId, response, request);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid policy ID", 400);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        String pathInfo = request.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(response, "Invalid endpoint", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            if ("products".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProductRelationships(request, response);
            } else if ("clients".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveClientRelationships(request, response);
            } else if ("processes".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProcessRelationships(request, response);
            } else if ("projects".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProjectRelationships(request, response);
            } else if ("systems".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveSystemRelationships(request, response);
            } else if ("businessareas".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveBusinessAreaRelationships(request, response);
            } else if ("legals".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveLegalRelationships(request, response);
            } else if ("datasets".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveDatasetRelationships(request, response);
            } else if ("attributes".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveAttributeRelationships(request, response);
            } else if ("glossaries".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveGlossaryRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    private void handleGetProductRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting product relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getProductRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " product relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching product relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching product relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetClientRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting client relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getClientRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " client relationships");
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching client relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching client relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetProductRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = policyImpactService.getProductRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error fetching product relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetClientOwner(int clientId, HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting client owners for client ID: " + clientId);
            String ownersName = policyImpactService.getClientOwnersString(clientId);
            String ownersEmail = policyImpactService.getClientOwnersEmail(clientId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved client owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching client owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching client owners: " + e.getMessage(), 500);
        }
    }

    private void handleGetProductOwner(int productId, HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting product owners for product ID: " + productId);
            String ownersName = policyImpactService.getProductOwnersString(productId);
            String ownersEmail = policyImpactService.getProductOwnersEmail(productId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved product owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching product owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching product owners: " + e.getMessage(), 500);
        }
    }

    private void handleGetClientRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationTypes = policyImpactService.getClientRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error fetching client relation types: " + e.getMessage(), 500);
        }
    }

    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("PolicyImpactServlet: Received product save request: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            
            int policyId = jsonObject.get("policyId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Policy", policyId)) {
                return; // Response already sent
            }
            
            Type relationshipsListType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                relationshipsListType
            );
            
            //system.out.println("PolicyImpactServlet: Policy ID: " + policyId);
            //system.out.println("PolicyImpactServlet: Relationships count: " + relationships.size());
            
            // Get user ID from session (default to 1 if not available)
            int userId = getUserIdFromSession(request);
            //system.out.println("PolicyImpactServlet: User ID: " + userId);
            
            try {
                var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                        policyId, "Policy", relationships, "Product", "productId");
                if (!validationResult.isValid) {
                    sendError(response, validationResult.message, 400);
                    return;
                }

                boolean success = policyImpactService.saveProductRelationships(policyId, relationships, userId);
                
                //system.out.println("PolicyImpactServlet: Save result: " + success);
                
                JsonObject result = new JsonObject();
                result.addProperty("success", success);
                if (!success) {
                    result.addProperty("message", "Failed to save product relationships to database");
                }
                response.getWriter().write(gson.toJson(result));
                
            } catch (Exception e) {
                System.err.println("PolicyImpactServlet: Error saving product relationships: " + e.getMessage());
                e.printStackTrace();
                
                JsonObject errorResult = new JsonObject();
                errorResult.addProperty("success", false);
                errorResult.addProperty("message", "Error saving product relationships: " + e.getMessage());
                response.setStatus(200); // Return 200 to avoid frontend error handling
                response.setContentType("application/json");
                response.getWriter().write(gson.toJson(errorResult));
            }
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Unexpected error in handleSaveProductRelationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving product relationships: " + e.getMessage(), 500);
        }
    }

    private void handleSaveClientRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            
            int policyId = jsonObject.get("policyId").getAsInt();
            Type relationshipsListType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                relationshipsListType
            );
            
            // Get user ID from session (default to 1 if not available)
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", relationships, "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveClientRelationships(policyId, relationships, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving client relationships: " + e.getMessage(), 500);
        }
    }

    private void handleHealthCheck(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Health check requested");
            
            JsonObject health = new JsonObject();
            health.addProperty("status", "healthy");
            health.addProperty("service", "PolicyImpactServlet");
            health.addProperty("timestamp", System.currentTimeMillis());
            
            // Check database connectivity
            try {
                boolean schemaValid = policyImpactService.validateSchema();
                health.addProperty("database", schemaValid ? "connected" : "error");
            } catch (Exception e) {
                health.addProperty("database", "error");
                health.addProperty("databaseError", e.getMessage());
            }
            
            response.getWriter().write(gson.toJson(health));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Health check failed: " + e.getMessage());
            sendError(response, "Health check failed: " + e.getMessage(), 500);
        }
    }

    private void handleGetProcessRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting process relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getProcessRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " process relation types");
            
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.add("data", gson.toJsonTree(relationTypes));
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching process relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching process relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetProjectRelationTypes(HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting project relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getProjectRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " project relation types");
            
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.add("data", gson.toJsonTree(relationTypes));
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching project relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching project relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetProcessRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting process relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getProcessRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " process relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching process relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching process relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetProjectRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting project relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getProjectRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " project relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching project relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching project relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetProcessOwner(int processId, HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting process owners for process ID: " + processId);
            String ownersName = policyImpactService.getProcessOwnersString(processId);
            String ownersEmail = policyImpactService.getProcessOwnersEmail(processId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved process owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching process owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching process owners: " + e.getMessage(), 500);
        }
    }

    private void handleGetProjectOwner(int projectId, HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting project owners for project ID: " + projectId);
            String ownersName = policyImpactService.getProjectOwnersString(projectId);
            String ownersEmail = policyImpactService.getProjectOwnersEmail(projectId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved project owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching project owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching project owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveProcessRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("PolicyImpactServlet: Received process relationships data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            //system.out.println("PolicyImpactServlet: Saving process relationships for policy ID: " + policyId + ", user ID: " + userId);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "Process", "processId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveProcessRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Process relationships saved successfully" : "Failed to save process relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving process relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving process relationships: " + e.getMessage(), 500);
        }
    }

    private void handleSaveProjectRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("PolicyImpactServlet: Received project relationships data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            //system.out.println("PolicyImpactServlet: Saving project relationships for policy ID: " + policyId + ", user ID: " + userId);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "Project", "projectId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveProjectRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Project relationships saved successfully" : "Failed to save project relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving project relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving project relationships: " + e.getMessage(), 500);
        }
    }

    // System handlers
    private void handleGetSystemRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting system relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getSystemRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " system relation types");
            
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching system relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching system relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetSystemRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting system relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getSystemRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " system relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching system relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching system relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetSystemOwner(int systemId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting system owner for system ID: " + systemId);
            Map<String, Object> owner = policyImpactService.getSystemOwner(systemId);
            //system.out.println("PolicyImpactServlet: Retrieved system owner: " + owner);
            
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching system owner: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching system owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveSystemRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Saving system relationships");
            
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            String jsonData = jsonBuffer.toString();
            //system.out.println("PolicyImpactServlet: Received JSON data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "System", "systemId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveSystemRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "System relationships saved successfully" : "Failed to save system relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving system relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving system relationships: " + e.getMessage(), 500);
        }
    }

    // Business Area handlers
    private void handleGetBusinessAreaRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting business area relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getBusinessAreaRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " business area relation types");
            
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching business area relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching business area relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetBusinessAreaRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting business area relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getBusinessAreaRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " business area relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching business area relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching business area relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetBusinessAreaOwner(int businessAreaId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting business area owner for business area ID: " + businessAreaId);
            Map<String, Object> owner = policyImpactService.getBusinessAreaOwner(businessAreaId);
            //system.out.println("PolicyImpactServlet: Retrieved business area owner: " + owner);
            
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching business area owner: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching business area owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveBusinessAreaRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Saving business area relationships");
            
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            String jsonData = jsonBuffer.toString();
            //system.out.println("PolicyImpactServlet: Received JSON data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "BusinessArea", "businessAreaId", "businessareaId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveBusinessAreaRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Business area relationships saved successfully" : "Failed to save business area relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving business area relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving business area relationships: " + e.getMessage(), 500);
        }
    }

    // Legal Entity handlers
    private void handleGetLegalRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting legal relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getLegalRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " legal relation types");
            
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching legal relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching legal relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetLegalRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting legal relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getLegalRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " legal relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching legal relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching legal relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetLegalOwner(int legalId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting legal owner for legal ID: " + legalId);
            Map<String, Object> owner = policyImpactService.getLegalOwner(legalId);
            //system.out.println("PolicyImpactServlet: Retrieved legal owner: " + owner);
            
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error fetching legal owner: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching legal owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveLegalRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Saving legal relationships");
            
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            String jsonData = jsonBuffer.toString();
            //system.out.println("PolicyImpactServlet: Received JSON data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "LegalEntity", "legalId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveLegalRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Legal relationships saved successfully" : "Failed to save legal relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving legal relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving legal relationships: " + e.getMessage(), 500);
        }
    }

    // Dataset handlers
    private void handleGetDatasetRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting dataset relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getDatasetRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " dataset relation types");
            
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting dataset relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting dataset relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetDatasetRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting dataset relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getDatasetRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " dataset relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Dataset", "datasetId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting dataset relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting dataset relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetDatasetOwner(int datasetId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting dataset owners for dataset ID: " + datasetId);
            String ownersName = policyImpactService.getDatasetOwnersString(datasetId);
            String ownersEmail = policyImpactService.getDatasetOwnersEmail(datasetId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved dataset owners: " + owner);
            
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting dataset owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting dataset owners: " + e.getMessage(), 500);
        }
    }

    private void handleSaveDatasetRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("PolicyImpactServlet: Received dataset save request: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "Dataset", "datasetId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveDatasetRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving dataset relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving dataset relationships: " + e.getMessage(), 500);
        }
    }

    // Attribute handlers
    private void handleGetAttributeRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting attribute relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getAttributeRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " attribute relation types");
            
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting attribute relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting attribute relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetDatasetsList(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting datasets list");
            List<Map<String, Object>> datasets = policyImpactService.getDatasetsList();
            //system.out.println("PolicyImpactServlet: Retrieved " + datasets.size() + " datasets");
            
            response.getWriter().write(gson.toJson(datasets));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting datasets list: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting datasets list: " + e.getMessage(), 500);
        }
    }

    private void handleGetAttributesList(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting attributes list");
            List<Map<String, Object>> attributes = policyImpactService.getAttributesList();
            //system.out.println("PolicyImpactServlet: Retrieved " + attributes.size() + " attributes");
            
            response.getWriter().write(gson.toJson(attributes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting attributes list: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting attributes list: " + e.getMessage(), 500);
        }
    }

    private void handleGetAttributeRelationships(int policyId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting attribute relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getAttributeRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " attribute relationships");
            
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting attribute relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting attribute relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetAttributeOwner(int attributeId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting attribute owners for attribute ID: " + attributeId);
            String ownersName = policyImpactService.getAttributeOwnersString(attributeId);
            String ownersEmail = policyImpactService.getAttributeOwnersEmail(attributeId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved attribute owners: " + owner);
            
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting attribute owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting attribute owners: " + e.getMessage(), 500);
        }
    }

    private void handleSaveAttributeRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("PolicyImpactServlet: Received attribute save request: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    policyId, "Policy", jsonObject.get("relationships"), "Attribute", "attributeId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = policyImpactService.saveAttributeRelationships(policyId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error saving attribute relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving attribute relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== GLOSSARY HANDLERS ====================
    
    private void handleGetGlossaryRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting glossary relation types");
            List<Map<String, Object>> relationTypes = policyImpactService.getGlossaryRelationTypes();
            //system.out.println("PolicyImpactServlet: Retrieved " + relationTypes.size() + " glossary relation types");
            
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting glossary relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting glossary relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationships(int policyId, HttpServletResponse response, HttpServletRequest request) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting glossary relationships for policy ID: " + policyId);
            List<Map<String, Object>> relationships = policyImpactService.getGlossaryRelationships(policyId);
            //system.out.println("PolicyImpactServlet: Retrieved " + relationships.size() + " glossary relationships");
            
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting glossary relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryOwner(int glossaryId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("PolicyImpactServlet: Getting glossary owners for glossary ID: " + glossaryId);
            String ownersName = policyImpactService.getGlossaryOwnersString(glossaryId);
            String ownersEmail = policyImpactService.getGlossaryOwnersEmail(glossaryId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName != null && !ownersName.isEmpty() ? ownersName : "No owner");
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("PolicyImpactServlet: Retrieved glossary owners: " + owner);
            
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Error getting glossary owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting glossary owners: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveGlossaryRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("PolicyImpactServlet: Received glossary save request: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int policyId = jsonObject.get("policyId").getAsInt();
            Type relationshipsListType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                relationshipsListType
            );
            
            //system.out.println("PolicyImpactServlet: Policy ID: " + policyId);
            //system.out.println("PolicyImpactServlet: Relationships count: " + relationships.size());
            
            int userId = getUserIdFromSession(request);
            //system.out.println("PolicyImpactServlet: User ID: " + userId);
            
            try {
                var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                        policyId, "Policy", relationships, "Glossary", "glossaryId");
                if (!validationResult.isValid) {
                    sendError(response, validationResult.message, 400);
                    return;
                }

                boolean success = policyImpactService.saveGlossaryRelationships(policyId, relationships, userId);
                
                //system.out.println("PolicyImpactServlet: Save result: " + success);
                
                JsonObject result = new JsonObject();
                result.addProperty("success", success);
                if (!success) {
                    result.addProperty("message", "Failed to save glossary relationships to database");
                }
                response.getWriter().write(gson.toJson(result));
                
            } catch (Exception e) {
                System.err.println("PolicyImpactServlet: Error saving glossary relationships: " + e.getMessage());
                e.printStackTrace();
                
                JsonObject errorResult = new JsonObject();
                errorResult.addProperty("success", false);
                errorResult.addProperty("message", "Error saving glossary relationships: " + e.getMessage());
                response.setStatus(200); // Return 200 to avoid frontend error handling
                response.setContentType("application/json");
                response.getWriter().write(gson.toJson(errorResult));
            }
        } catch (Exception e) {
            System.err.println("PolicyImpactServlet: Unexpected error in handleSaveGlossaryRelationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetPolicyRelationshipsByProductId(int productId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByClientId(int clientId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByProcessId(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByProcessId(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByProjectId(int projectId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByProjectId(projectId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsBySystemId(int systemId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByBusinessAreaId(int businessAreaId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByLegalId(int legalId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByGlossaryId(int glossaryId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByGlossaryId(glossaryId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationshipsByDatasetId(int datasetId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int datasetIdToLoad = datasetId;
            
            if ("changes".equals(view)) {
                try {
                    com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
                    // Only check for automatic CRs for pending changes
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(11, datasetId); // 11 is DATASET_FACET_ID
                    if (activeCrId != null) {
                        // Check for impact-specific mapping first
                        Integer nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "impact#dataset_X_policy", activeCrId);
                        // If no impact-specific mapping, fall back to summary mapping (cloned dataset ID)
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            datasetIdToLoad = nObjectId;
                            //system.out.println("[PolicyImpactServlet] Using cloned dataset ID " + nObjectId + " for policy relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = policyImpactService.getPolicyRelationshipsByDatasetId(datasetIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int status) 
            throws IOException {
        response.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(gson.toJson(error));
    }

    /**
     * Get user ID from session, default to 1 if not available
     */
    private int getUserIdFromSession(HttpServletRequest request) {
        try {
            // Try to get user ID from session attribute
            Object userIdObj = request.getSession().getAttribute("userId");
            if (userIdObj instanceof Integer) {
                return (Integer) userIdObj;
            }
            
            // Try to get user ID from session attribute as string
            Object userIdStrObj = request.getSession().getAttribute("userId");
            if (userIdStrObj instanceof String) {
                return Integer.parseInt((String) userIdStrObj);
            }
            
            // Default to user ID 1 if not found
            return 1;
        } catch (Exception e) {
            // Default to user ID 1 if any error occurs
            return 1;
        }
    }
}
