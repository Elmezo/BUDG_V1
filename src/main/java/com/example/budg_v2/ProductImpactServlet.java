package com.example.budg_v2;

import com.example.budg_v2.service.ProductImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/product-impact/*")
public class ProductImpactServlet extends HttpServlet {
    private final ProductImpactService productImpactService;
    private final Gson gson = new Gson();

    public ProductImpactServlet() {
        this.productImpactService = new ProductImpactService();
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
            
            // Check for reverse lookup first: /api/product-impact/{entityType}s/{entityId}/products
            if (parts.length >= 3) {
                if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("products".equals(parts[2])) {
                            handleGetProductRelationshipsByClientId(clientId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("legals".equals(parts[0]) || "legal-entities".equals(parts[0])) {
                    try {
                        int legalId = Integer.parseInt(parts[1]);
                        if ("products".equals(parts[2])) {
                            handleGetProductRelationshipsByLegalId(legalId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid legal ID: " + parts[1], 400);
                        return;
                    }
                } else if ("businessareas".equals(parts[0]) || "business-areas".equals(parts[0])) {
                    try {
                        int businessAreaId = Integer.parseInt(parts[1]);
                        if ("products".equals(parts[2])) {
                            handleGetProductRelationshipsByBusinessAreaId(businessAreaId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid business area ID: " + parts[1], 400);
                        return;
                    }
                } else if ("capabilities".equals(parts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(parts[1]);
                        if ("products".equals(parts[2])) {
                            handleGetProductRelationshipsByCapabilityId(capabilityId, request, response);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid capability ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("legal-relation-types".equals(parts[0])) {
                handleGetLegalRelationTypes(response);
            } else if ("client-relation-types".equals(parts[0])) {
                handleGetClientRelationTypes(response);
            } else if ("businessarea-relation-types".equals(parts[0]) || "business-area-relation-types".equals(parts[0])) {
                handleGetBusinessAreaRelationTypes(response);
            } else if ("legal-owner".equals(parts[0]) && parts.length >= 2) {
                int legalId = Integer.parseInt(parts[1]);
                handleGetLegalOwner(legalId, response);
            } else if ("client-owner".equals(parts[0]) && parts.length >= 2) {
                int clientId = Integer.parseInt(parts[1]);
                handleGetClientOwner(clientId, response);
            } else if ("businessarea-owner".equals(parts[0]) && parts.length >= 2 || "business-area-owner".equals(parts[0]) && parts.length >= 2) {
                int businessAreaId = Integer.parseInt(parts[1]);
                handleGetBusinessAreaOwner(businessAreaId, response);
            } else if (parts.length >= 2) {
                int productId = Integer.parseInt(parts[0]);
                
                if ("legals".equals(parts[1]) || "legal-entities".equals(parts[1])) {
                    handleGetLegalRelationships(productId, request, response);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(productId, request, response);
                } else if ("businessareas".equals(parts[1]) || "business-areas".equals(parts[1])) {
                    handleGetBusinessAreaRelationships(productId, request, response);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid product ID", 400);
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
            
            if ("legals".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveLegalRelationships(request, response);
            } else if ("clients".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveClientRelationships(request, response);
            } else if ("businessareas".equals(parts[0]) && "save".equals(parts[1]) || "business-areas".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveBusinessAreaRelationships(request, response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetLegalRelationships(int productId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = productImpactService.getLegalRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal entity relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationships(int productId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = productImpactService.getClientRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetBusinessAreaRelationships(int productId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = productImpactService.getBusinessAreaRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "BusinessArea", "businessAreaId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area relationships: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATION TYPE HANDLERS =====
    
    private void handleGetLegalRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = productImpactService.getLegalRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = productImpactService.getClientRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relation types: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetBusinessAreaRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> relationTypes = productImpactService.getBusinessAreaRelationTypes();
            response.getWriter().write(gson.toJson(relationTypes));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area relation types: " + e.getMessage(), 500);
        }
    }

    // ===== GET OWNER HANDLERS =====
    
    private void handleGetLegalOwner(int legalId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = productImpactService.getLegalOwnersString(legalId);
            String ownerEmail = productImpactService.getLegalOwnersEmail(legalId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("legalOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("legalOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientOwner(int clientId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = productImpactService.getClientOwnersString(clientId);
            String ownerEmail = productImpactService.getClientOwnersEmail(clientId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("clientOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("clientOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client owner: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetBusinessAreaOwner(int businessAreaId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = productImpactService.getBusinessAreaOwnersString(businessAreaId);
            String ownerEmail = productImpactService.getBusinessAreaOwnersEmail(businessAreaId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("ownerName", ownerName != null ? ownerName : "No owner");
            result.put("businessAreaOwnerName", ownerName != null ? ownerName : "No owner");
            result.put("ownerEmail", ownerEmail);
            result.put("businessAreaOwnerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve business area owner: " + e.getMessage(), 500);
        }
    }

    // ===== SAVE HANDLERS =====
    
    private void handleSaveLegalRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            StringBuilder jsonBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
            }
            
            JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int productId = parseProductIdForSave(jsonObject, response);
            if (productId <= 0) {
                return;
            }
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Product", productId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                relationships = gson.fromJson(relationshipsElement, listType);
            }

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    productId, "Product", relationships, "LegalEntity", "legalId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            
            boolean success = productImpactService.saveLegalRelationships(productId, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Legal relationships saved successfully" : "Failed to save legal relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save legal relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveClientRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            StringBuilder jsonBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
            }
            
            JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int productId = parseProductIdForSave(jsonObject, response);
            if (productId <= 0) {
                return;
            }
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Product", productId)) {
                return; // Response already sent
            }
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                relationships = gson.fromJson(relationshipsElement, listType);
            }

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    productId, "Product", relationships, "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            
            boolean success = productImpactService.saveClientRelationships(productId, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Client relationships saved successfully" : "Failed to save client relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save client relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleSaveBusinessAreaRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\": \"User authentication required\"}");
                return;
            }
            
            StringBuilder jsonBody = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }
            }
            
            JsonObject jsonObject = gson.fromJson(jsonBody.toString(), JsonObject.class);
            int productId = parseProductIdForSave(jsonObject, response);
            if (productId <= 0) {
                return;
            }
            JsonElement relationshipsElement = jsonObject.get("relationships");
            
            System.out.println("=== ProductImpactServlet.handleSaveBusinessAreaRelationships ===");
            System.out.println("productId: " + productId + ", userId: " + userId);
            System.out.println("JSON body: " + jsonBody.toString());
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
                relationships = gson.fromJson(relationshipsElement, listType);
            }
            
            System.out.println("Parsed relationships size: " + relationships.size());
            if (!relationships.isEmpty()) {
                System.out.println("First relationship: " + relationships.get(0));
            }

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    productId, "Product", relationships, "BusinessArea", "businessAreaId", "businessareaId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }
            
            boolean success = productImpactService.saveBusinessAreaRelationships(productId, relationships, userId);
            System.out.println("Save result: " + success);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            result.put("message", success ? "Business area relationships saved successfully" : "Failed to save business area relationships");
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save business area relationships: " + e.getMessage(), 500);
        }
    }

    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetProductRelationshipsByClientId(int clientId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = productImpactService.getProductRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProductRelationshipsByLegalId(int legalId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = productImpactService.getProductRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProductRelationshipsByCapabilityId(int capabilityId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            com.example.budg_v2.service.CapabilityImpactService capabilityImpactService = new com.example.budg_v2.service.CapabilityImpactService();
            List<Map<String, Object>> relationships = capabilityImpactService.getProductRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProductRelationshipsByBusinessAreaId(int businessAreaId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = productImpactService.getProductRelationshipsByBusinessAreaId(businessAreaId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        response.getWriter().write(gson.toJson(error));
    }

    /** Parses productId from save request body; on failure sends 400 and returns a non-positive sentinel. */
    private int parseProductIdForSave(JsonObject jsonObject, HttpServletResponse response) throws IOException {
        JsonElement el = jsonObject.get("productId");
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            sendError(response, "productId is required and must be a valid number", 400);
            return -1;
        }
        try {
            int id = el.getAsInt();
            if (id <= 0) {
                sendError(response, "productId is required and must be a valid number", 400);
                return -1;
            }
            return id;
        } catch (Exception e) {
            sendError(response, "productId is required and must be a valid number", 400);
            return -1;
        }
    }
}

