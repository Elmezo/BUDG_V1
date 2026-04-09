package com.example.budg_v2;

import com.example.budg_v2.service.CapabilityImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/capability-impact/*")
public class CapabilityImpactServlet extends HttpServlet {
    private final CapabilityImpactService capabilityImpactService;
    private final Gson gson = new Gson();

    public CapabilityImpactServlet() {
        this.capabilityImpactService = new CapabilityImpactService();
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
            
            // Check for reverse lookup first: /api/capability-impact/{entityType}s/{entityId}/capabilities
            if (parts.length >= 3) {
                if ("systems".equals(parts[0])) {
                    try {
                        int systemId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsBySystemId(systemId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid system ID: " + parts[1], 400);
                        return;
                    }
                } else if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsByClientId(clientId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("products".equals(parts[0])) {
                    try {
                        int productId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsByProductId(productId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid product ID: " + parts[1], 400);
                        return;
                    }
                } else if ("processes".equals(parts[0])) {
                    try {
                        int processId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsByProcessId(processId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid process ID: " + parts[1], 400);
                        return;
                    }
                } else if ("glossaries".equals(parts[0])) {
                    try {
                        int glossaryId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsByGlossaryId(glossaryId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid glossary ID: " + parts[1], 400);
                        return;
                    }
                } else if ("businessareas".equals(parts[0])) {
                    try {
                        int businessAreaId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsByBusinessAreaId(businessAreaId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid business area ID: " + parts[1], 400);
                        return;
                    }
                } else if ("legals".equals(parts[0]) || "legal-entities".equals(parts[0])) {
                    try {
                        int legalId = Integer.parseInt(parts[1]);
                        if ("capabilities".equals(parts[2])) {
                            handleGetCapabilityRelationshipsByLegalId(legalId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid legal ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("system-relation-types".equals(parts[0])) {
                handleGetSystemRelationTypes(response);
            } else if ("client-relation-types".equals(parts[0])) {
                handleGetClientRelationTypes(response);
            } else if ("product-relation-types".equals(parts[0])) {
                handleGetProductRelationTypes(response);
            } else if ("process-relation-types".equals(parts[0])) {
                handleGetProcessRelationTypes(response);
            } else if ("glossary-relation-types".equals(parts[0])) {
                handleGetGlossaryRelationTypes(response);
            } else if ("businessarea-relation-types".equals(parts[0])) {
                handleGetBusinessAreaRelationTypes(response);
            } else if ("legal-relation-types".equals(parts[0])) {
                handleGetLegalRelationTypes(response);
            } else if ("system-owner".equals(parts[0]) && parts.length >= 2) {
                int systemId = Integer.parseInt(parts[1]);
                handleGetSystemOwner(systemId, response);
            } else if ("client-owner".equals(parts[0]) && parts.length >= 2) {
                int clientId = Integer.parseInt(parts[1]);
                handleGetClientOwner(clientId, response);
            } else if ("product-owner".equals(parts[0]) && parts.length >= 2) {
                int productId = Integer.parseInt(parts[1]);
                handleGetProductOwner(productId, response);
            } else if ("process-owner".equals(parts[0]) && parts.length >= 2) {
                int processId = Integer.parseInt(parts[1]);
                handleGetProcessOwner(processId, response);
            } else if ("glossary-owner".equals(parts[0]) && parts.length >= 2) {
                int glossaryId = Integer.parseInt(parts[1]);
                handleGetGlossaryOwner(glossaryId, response);
            } else if ("businessarea-owner".equals(parts[0]) && parts.length >= 2) {
                int businessAreaId = Integer.parseInt(parts[1]);
                handleGetBusinessAreaOwner(businessAreaId, response);
            } else if ("legal-owner".equals(parts[0]) && parts.length >= 2) {
                int legalId = Integer.parseInt(parts[1]);
                handleGetLegalOwner(legalId, response);
            } else if (parts.length >= 2) {
                int capabilityId = Integer.parseInt(parts[0]);
                
                if ("systems".equals(parts[1])) {
                    handleGetSystemRelationships(capabilityId, request, response);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(capabilityId, request, response);
                } else if ("products".equals(parts[1])) {
                    handleGetProductRelationships(capabilityId, request, response);
                } else if ("processes".equals(parts[1])) {
                    handleGetProcessRelationships(capabilityId, request, response);
                } else if ("glossaries".equals(parts[1])) {
                    handleGetGlossaryRelationships(capabilityId, request, response);
                } else if ("businessareas".equals(parts[1])) {
                    handleGetBusinessAreaRelationships(capabilityId, request, response);
                } else if ("legals".equals(parts[1])) {
                    handleGetLegalRelationships(capabilityId, request, response);
                } else if ("projects".equals(parts[1])) {
                    handleGetProjectRelationships(capabilityId, request, response);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid capability ID", 400);
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
            
            if ("systems".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveSystemRelationships(request, response);
            } else if ("clients".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveClientRelationships(request, response);
            } else if ("products".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProductRelationships(request, response);
            } else if ("processes".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProcessRelationships(request, response);
            } else if ("glossaries".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveGlossaryRelationships(request, response);
            } else if ("businessareas".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveBusinessAreaRelationships(request, response);
            } else if ("legals".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveLegalRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ==================== SYSTEM HANDLERS ====================
    
    private void handleGetSystemRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting system relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getSystemRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " system relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching system relationships: " + e.getMessage());
            e.printStackTrace();
            System.err.println("CapabilityImpactServlet: Stack trace:");
            e.printStackTrace();
            sendError(response, "Error fetching system relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetSystemRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting system relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getSystemRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " system relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching system relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching system relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetSystemOwner(int systemId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting system owner for system ID: " + systemId);
            Map<String, Object> owner = capabilityImpactService.getSystemOwner(systemId);
            //system.out.println("CapabilityImpactServlet: Retrieved system owner: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching system owner: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching system owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveSystemRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Saving system relationships");
            
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            String jsonData = jsonBuffer.toString();
            //system.out.println("CapabilityImpactServlet: Received JSON data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "System", "systemId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveSystemRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "System relationships saved successfully" : "Failed to save system relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error saving system relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving system relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== CLIENT HANDLERS ====================
    
    private void handleGetClientRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting client relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getClientRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " client relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching client relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching client relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetClientRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting client relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getClientRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " client relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching client relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching client relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetClientOwner(int clientId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting client owners for client ID: " + clientId);
            String ownersName = capabilityImpactService.getClientOwnersString(clientId);
            String ownersEmail = capabilityImpactService.getClientOwnersEmail(clientId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("CapabilityImpactServlet: Retrieved client owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching client owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching client owners: " + e.getMessage(), 500);
        }
    }

    private void handleSaveClientRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveClientRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving client relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== PRODUCT HANDLERS ====================
    
    private void handleGetProductRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting product relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getProductRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " product relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching product relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching product relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetProductRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting product relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getProductRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " product relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching product relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching product relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetProductOwner(int productId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting product owners for product ID: " + productId);
            String ownersName = capabilityImpactService.getProductOwnersString(productId);
            String ownersEmail = capabilityImpactService.getProductOwnersEmail(productId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("CapabilityImpactServlet: Retrieved product owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching product owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching product owners: " + e.getMessage(), 500);
        }
    }

    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "Product", "productId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveProductRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Error saving product relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== PROCESS HANDLERS ====================
    
    private void handleGetProcessRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting process relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getProcessRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " process relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching process relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching process relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetProcessRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting process relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getProcessRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " process relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching process relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching process relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetProcessOwner(int processId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting process owners for process ID: " + processId);
            String ownersName = capabilityImpactService.getProcessOwnersString(processId);
            String ownersEmail = capabilityImpactService.getProcessOwnersEmail(processId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("CapabilityImpactServlet: Retrieved process owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching process owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching process owners: " + e.getMessage(), 500);
        }
    }

    private void handleSaveProcessRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonData = jsonBuilder.toString();
            //system.out.println("CapabilityImpactServlet: Received process relationships data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            //system.out.println("CapabilityImpactServlet: Saving process relationships for capability ID: " + capabilityId + ", user ID: " + userId);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "Process", "processId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveProcessRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Process relationships saved successfully" : "Failed to save process relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error saving process relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving process relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== PROJECT HANDLERS ====================
    
    private void handleGetProjectRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting project relationships for capability ID: " + capabilityId);
            com.example.budg_v2.service.ProjectImpactService projectImpactService = new com.example.budg_v2.service.ProjectImpactService();
            List<Map<String, Object>> relationships = projectImpactService.getProjectRelationshipsByCapabilityId(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " project relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching project relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching project relationships: " + e.getMessage(), 500);
        }
    }
    
    // ==================== GLOSSARY HANDLERS ====================
    
    private void handleGetGlossaryRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting glossary relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getGlossaryRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " glossary relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching glossary relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching glossary relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetGlossaryRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting glossary relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getGlossaryRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " glossary relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching glossary relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching glossary relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetGlossaryOwner(int glossaryId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting glossary owners for glossary ID: " + glossaryId);
            String ownersName = capabilityImpactService.getGlossaryOwnersString(glossaryId);
            String ownersEmail = capabilityImpactService.getGlossaryOwnersEmail(glossaryId);
            
            Map<String, Object> owner = new HashMap<>();
            owner.put("ownerName", ownersName);
            owner.put("ownerEmail", ownersEmail);
            
            //system.out.println("CapabilityImpactServlet: Retrieved glossary owners: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching glossary owners: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching glossary owners: " + e.getMessage(), 500);
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
            //system.out.println("CapabilityImpactServlet: Received glossary relationships data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            //system.out.println("CapabilityImpactServlet: Saving glossary relationships for capability ID: " + capabilityId + ", user ID: " + userId);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "Glossary", "glossaryId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveGlossaryRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Glossary relationships saved successfully" : "Failed to save glossary relationships");
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error saving glossary relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving glossary relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== BUSINESS AREA HANDLERS ====================
    
    private void handleGetBusinessAreaRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting business area relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getBusinessAreaRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " business area relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching business area relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching business area relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetBusinessAreaRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting business area relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getBusinessAreaRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " business area relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching business area relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching business area relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetBusinessAreaOwner(int businessAreaId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting business area owner for business area ID: " + businessAreaId);
            Map<String, Object> owner = capabilityImpactService.getBusinessAreaOwner(businessAreaId);
            //system.out.println("CapabilityImpactServlet: Retrieved business area owner: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching business area owner: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching business area owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveBusinessAreaRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            String jsonData = jsonBuffer.toString();
            //system.out.println("CapabilityImpactServlet: Received business area relationships data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "BusinessArea", "businessAreaId", "businessareaId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveBusinessAreaRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Business area relationships saved successfully" : "Failed to save business area relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error saving business area relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving business area relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== LEGAL ENTITY HANDLERS ====================
    
    private void handleGetLegalRelationships(int capabilityId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting legal entity relationships for capability ID: " + capabilityId);
            List<Map<String, Object>> relationships = capabilityImpactService.getLegalRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationships.size() + " legal entity relationships");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching legal entity relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching legal entity relationships: " + e.getMessage(), 500);
        }
    }

    private void handleGetLegalRelationTypes(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting legal entity relation types");
            List<Map<String, Object>> relationTypes = capabilityImpactService.getLegalRelationTypes();
            //system.out.println("CapabilityImpactServlet: Retrieved " + relationTypes.size() + " legal entity relation types");
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching legal entity relation types: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching legal entity relation types: " + e.getMessage(), 500);
        }
    }

    private void handleGetLegalOwner(int legalId, HttpServletResponse response) throws IOException {
        try {
            //system.out.println("CapabilityImpactServlet: Getting legal entity owner for legal ID: " + legalId);
            Map<String, Object> owner = capabilityImpactService.getLegalOwner(legalId);
            //system.out.println("CapabilityImpactServlet: Retrieved legal entity owner: " + owner);
            response.getWriter().write(gson.toJson(owner));
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error fetching legal entity owner: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error fetching legal entity owner: " + e.getMessage(), 500);
        }
    }

    private void handleSaveLegalRelationships(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            StringBuilder jsonBuffer = new StringBuilder();
            String line;
            while ((line = request.getReader().readLine()) != null) {
                jsonBuffer.append(line);
            }
            
            String jsonData = jsonBuffer.toString();
            //system.out.println("CapabilityImpactServlet: Received legal entity relationships data: " + jsonData);
            
            JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
            int capabilityId = jsonObject.get("capabilityId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Capability", capabilityId)) {
                return; // Response already sent
            }
            
            int userId = getUserIdFromSession(request);
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    capabilityId, "Capability", jsonObject.get("relationships"), "LegalEntity", "legalId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = capabilityImpactService.saveLegalRelationships(capabilityId, jsonObject, userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            result.addProperty("message", success ? "Legal entity relationships saved successfully" : "Failed to save legal entity relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            System.err.println("CapabilityImpactServlet: Error saving legal entity relationships: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error saving legal entity relationships: " + e.getMessage(), 500);
        }
    }

    // ==================== REVERSE LOOKUP HANDLERS ====================
    
    private void handleGetCapabilityRelationshipsBySystemId(int systemId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCapabilityRelationshipsByClientId(int clientId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCapabilityRelationshipsByProductId(int productId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCapabilityRelationshipsByProcessId(int processId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsByProcessId(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCapabilityRelationshipsByGlossaryId(int glossaryId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsByGlossaryId(glossaryId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCapabilityRelationshipsByBusinessAreaId(int businessAreaId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetCapabilityRelationshipsByLegalId(int legalId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = capabilityImpactService.getCapabilityRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Capability", "capabilityId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve capability relationships: " + e.getMessage(), 500);
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    private void sendError(HttpServletResponse response, String message, int status) throws IOException {
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

