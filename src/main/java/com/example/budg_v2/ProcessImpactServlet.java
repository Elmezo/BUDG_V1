package com.example.budg_v2;

import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.service.ProcessImpactService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.ImpactSegmentValidationUtil;
import com.example.budg_v2.util.PermissionCheckUtil;
import com.example.budg_v2.util.RelationshipAccessUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/process-impact/*")
public class ProcessImpactServlet extends HttpServlet {
    private static final int PROCESS_FACET_ID = 4;
    private static final Type RELATIONSHIPS_LIST_TYPE = new TypeToken<List<Map<String, Object>>>(){}.getType();
    private final ProcessImpactService processImpactService;
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final Gson gson = new Gson();

    public ProcessImpactServlet() {
        this.processImpactService = new ProcessImpactService();
    }

    @SuppressWarnings("unused")
    private Integer resolveOrCloneProcessIdForChanges(int processId, String areaKey) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Only check for automatic CRs for pending changes
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, processId);
            if (activeCrId == null) return null;

            Integer nobjectId = facetChangesDAO.getNObjectId("process", processId, areaKey, activeCrId);
            if (nobjectId == null) {
                nobjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
            }
            if (nobjectId == null) {
                nobjectId = cloneProcessRow(conn, processId);
                if (nobjectId != null) {
                    facetChangesDAO.saveMapping("process", processId, nobjectId, "summary", activeCrId);
                }
            }
            if (nobjectId != null) {
                facetChangesDAO.saveMapping("process", processId, nobjectId, areaKey, activeCrId);
            }
            return nobjectId;
        } catch (Exception e) {
            return null;
        }
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
            
            // Check for relation types endpoint
            if (parts.length == 1 && "process-relation-types".equals(parts[0])) {
                handleGetProcessRelationTypes(response);
                return;
            }
            
            // Check for reverse lookup first: /api/process-impact/{entityType}s/{entityId}/processes
            if (parts.length >= 3) {
                if ("systems".equals(parts[0])) {
                    try {
                        int systemId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsBySystemId(systemId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid system ID: " + parts[1], 400);
                        return;
                    }
                } else if ("glossaries".equals(parts[0])) {
                    try {
                        int glossaryId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsByGlossaryId(glossaryId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid glossary ID: " + parts[1], 400);
                        return;
                    }
                } else if ("capabilities".equals(parts[0])) {
                    try {
                        int capabilityId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsByCapabilityId(capabilityId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid capability ID: " + parts[1], 400);
                        return;
                    }
                } else if ("clients".equals(parts[0])) {
                    try {
                        int clientId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsByClientId(clientId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid client ID: " + parts[1], 400);
                        return;
                    }
                } else if ("datasets".equals(parts[0])) {
                    try {
                        int datasetId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsByDatasetId(datasetId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid dataset ID: " + parts[1], 400);
                        return;
                    }
                } else if ("products".equals(parts[0])) {
                    try {
                        int productId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsByProductId(productId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid product ID: " + parts[1], 400);
                        return;
                    }
                } else if ("legals".equals(parts[0]) || "legal-entities".equals(parts[0])) {
                    try {
                        int legalId = Integer.parseInt(parts[1]);
                        if ("processes".equals(parts[2])) {
                            handleGetProcessRelationshipsByLegalId(legalId, response, request);
                            return;
                        }
                    } catch (NumberFormatException e) {
                        sendError(response, "Invalid legal entity ID: " + parts[1], 400);
                        return;
                    }
                }
            }
            
            if ("health".equals(parts[0])) {
                handleHealthCheck(response);
            } else if ("system-relation-types".equals(parts[0])) {
                handleGetSystemRelationTypes(response);
            } else if ("product-relation-types".equals(parts[0])) {
                handleGetProductRelationTypes(response);
            } else if ("client-relation-types".equals(parts[0])) {
                handleGetClientRelationTypes(response);
            } else if ("glossary-relation-types".equals(parts[0])) {
                handleGetGlossaryRelationTypes(response);
            } else if ("project-relation-types".equals(parts[0])) {
                handleGetProjectRelationTypes(response);
            } else if ("policy-relation-types".equals(parts[0])) {
                handleGetPolicyRelationTypes(response);
            } else if ("interface-relation-types".equals(parts[0])) {
                handleGetInterfaceRelationTypes(response);
            } else if ("interfaces".equals(parts[0])) {
                handleGetAllInterfaces(response);
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
            } else if ("product-owner".equals(parts[0]) && parts.length >= 2) {
                int productId = Integer.parseInt(parts[1]);
                handleGetProductOwner(productId, response);
            } else if ("client-owner".equals(parts[0]) && parts.length >= 2) {
                int clientId = Integer.parseInt(parts[1]);
                handleGetClientOwner(clientId, response);
            } else if ("glossary-owner".equals(parts[0]) && parts.length >= 2) {
                int glossaryId = Integer.parseInt(parts[1]);
                handleGetGlossaryOwner(glossaryId, response);
            } else if ("project-owner".equals(parts[0]) && parts.length >= 2) {
                int projectId = Integer.parseInt(parts[1]);
                handleGetProjectOwner(projectId, response);
            } else if ("policy-owner".equals(parts[0]) && parts.length >= 2) {
                int policyId = Integer.parseInt(parts[1]);
                handleGetPolicyOwner(policyId, response);
            } else if ("interface-owner".equals(parts[0]) && parts.length >= 2) {
                int interfaceId = Integer.parseInt(parts[1]);
                handleGetInterfaceOwner(interfaceId, response);
            } else if ("legal-owner".equals(parts[0]) && parts.length >= 2) {
                int legalId = Integer.parseInt(parts[1]);
                handleGetLegalOwner(legalId, response);
            } else if ("dataset-owner".equals(parts[0]) && parts.length >= 2) {
                int datasetId = Integer.parseInt(parts[1]);
                handleGetDatasetOwner(datasetId, response);
            } else if ("attribute-owner".equals(parts[0]) && parts.length >= 2) {
                int attributeId = Integer.parseInt(parts[1]);
                handleGetAttributeOwner(attributeId, response);
            } else if (parts.length >= 2) {
                int processId = Integer.parseInt(parts[0]);
                
                // Resolve processId for view=changes mode
                String view = request.getParameter("view");
                int processIdToLoad = processId;
                
                if ("changes".equals(view)) {
                    try {
                        // Only check for automatic CRs for pending changes
            Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, processId);
                        if (activeCrId != null) {
                            // Prefer impact-specific mapping for each table, then fall back to summary clone
                            String impactAreaKey = null;
                            if ("systems".equals(parts[1])) impactAreaKey = "impact#process_X_system";
                            if ("products".equals(parts[1])) impactAreaKey = "impact#process_X_product";
                            if ("clients".equals(parts[1])) impactAreaKey = "impact#process_X_client";
                            if ("glossaries".equals(parts[1])) impactAreaKey = "impact#process_X_glossary";
                            if ("projects".equals(parts[1])) impactAreaKey = "impact#process_X_project";
                            if ("policies".equals(parts[1])) impactAreaKey = "impact#process_X_policy";
                            if ("interfaces".equals(parts[1])) impactAreaKey = "impact#process_X_interface";
                            if ("datasets".equals(parts[1])) impactAreaKey = "impact#process_X_dataset";
                            if ("attributes".equals(parts[1])) impactAreaKey = "impact#process_X_attribute";
                            if ("legals".equals(parts[1]) || "legal-entities".equals(parts[1])) impactAreaKey = "impact#process_X_legal";
                            if ("predecessors".equals(parts[1])) impactAreaKey = "impact#process_X_process";

                            // Get or create summary mapping (cloned process)
                            Integer nObjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
                            if (nObjectId == null) {
                                // No summary mapping exists - clone the process
                                try (Connection conn = DatabaseConnection.getConnection()) {
                                    nObjectId = cloneProcessRow(conn, processId);
                                    if (nObjectId != null) {
                                        facetChangesDAO.saveMapping("process", processId, nObjectId, "summary", activeCrId);
                                        //system.out.println("[ProcessImpactServlet] Cloned process " + processId + " to " + nObjectId + " for view=changes");
                                    }
                                } catch (Exception e) {
                                    System.err.println("[ProcessImpactServlet] Error cloning process for view=changes: " + e.getMessage());
                                }
                            }
                            
                            if (nObjectId != null) {
                                // Check if impact relationships have been initialized for this area
                                if (impactAreaKey != null) {
                                    Integer impactMapping = facetChangesDAO.getNObjectId("process", processId, impactAreaKey, activeCrId);
                                    if (impactMapping == null) {
                                        // First time viewing this impact type - initialize relationships in cloned process
                                        try (Connection conn = DatabaseConnection.getConnection()) {
                                            String relationType = impactAreaKey.substring("impact#".length());
                                            initializeImpactRelationshipsInClonedProcess(conn, processId, nObjectId, relationType);
                                            facetChangesDAO.saveMapping("process", processId, nObjectId, impactAreaKey, activeCrId);
                                            //system.out.println("[ProcessImpactServlet] Initialized " + relationType + " relationships in cloned process " + nObjectId);
                                        } catch (Exception e) {
                                            System.err.println("[ProcessImpactServlet] Error initializing impact relationships: " + e.getMessage());
                                        }
                                    }
                                }
                                
                                processIdToLoad = nObjectId;
                                //system.out.println("[ProcessImpactServlet] ✅ Using cloned process ID " + nObjectId + " for relationships (view=changes, area=" + impactAreaKey + ", original=" + processId + ")");
                            } else {
                                System.err.println("[ProcessImpactServlet] ⚠️ WARNING: Could not get/create cloned process ID for view=changes, using original ID " + processId);
                            }
                        } else {
                            //system.out.println("[ProcessImpactServlet] No active CR found for process " + processId + ", using original ID");
                        }
                    } catch (Exception e) {
                        System.err.println("[ProcessImpactServlet] Error getting nobject_id for view=changes: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                if ("systems".equals(parts[1])) {
                    handleGetSystemRelationships(processIdToLoad, response, request);
                } else if ("products".equals(parts[1])) {
                    handleGetProductRelationships(processIdToLoad, response, request);
                } else if ("clients".equals(parts[1])) {
                    handleGetClientRelationships(processIdToLoad, response, request);
                } else if ("glossaries".equals(parts[1])) {
                    handleGetGlossaryRelationships(processIdToLoad, response, request);
                } else if ("projects".equals(parts[1])) {
                    handleGetProjectRelationships(processIdToLoad, response, request);
                } else if ("policies".equals(parts[1])) {
                    handleGetPolicyRelationships(processIdToLoad, response, request);
                } else if ("interfaces".equals(parts[1])) {
                    handleGetInterfaceRelationships(processIdToLoad, response, request);
                } else if ("legals".equals(parts[1])) {
                    handleGetLegalRelationships(processIdToLoad, response, request);
                } else if ("datasets".equals(parts[1])) {
                    handleGetDatasetRelationships(processIdToLoad, response, request);
                } else if ("attributes".equals(parts[1])) {
                    handleGetAttributeRelationships(processIdToLoad, response);
                } else if ("predecessors".equals(parts[1])) {
                    handleGetPredecessorRelationships(processId, view, response, request);
                } else if ("successors".equals(parts[1])) {
                    handleGetSuccessorRelationships(processId, view, response, request);
                } else {
                    sendError(response, "Invalid endpoint", 400);
                }
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (NumberFormatException e) {
            sendError(response, "Invalid process ID", 400);
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
            } else if ("products".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProductRelationships(request, response);
            } else if ("clients".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveClientRelationships(request, response);
            } else if ("glossaries".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveGlossaryRelationships(request, response);
            } else if ("projects".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveProjectRelationships(request, response);
            } else if ("policies".equals(parts[0]) && "save".equals(parts[1])) {
                handleSavePolicyRelationships(request, response);
            } else if ("interfaces".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveInterfaceRelationships(request, response);
            } else if ("legals".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveLegalRelationships(request, response);
            } else if ("datasets".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveDatasetRelationships(request, response);
            } else if ("attributes".equals(parts[0]) && "save".equals(parts[1])) {
                handleSaveAttributeRelationships(request, response);
            } else if (parts.length >= 3) {
                try {
                    int processId = Integer.parseInt(parts[0]);
                    if ("predecessors".equals(parts[1]) && "save".equals(parts[2])) {
                        handleSavePredecessorRelationships(processId, request, response);
                    } else {
                        sendError(response, "Invalid endpoint", 400);
                    }
                } catch (NumberFormatException e) {
                    if ("process-relation-types".equals(parts[0])) {
                        handleGetProcessRelationTypes(response);
                    } else {
                        sendError(response, "Invalid endpoint", 400);
                    }
                }
            } else if ("process-relation-types".equals(parts[0])) {
                handleGetProcessRelationTypes(response);
            } else {
                sendError(response, "Invalid endpoint", 400);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Server error: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATIONSHIP HANDLERS =====
    
    private void handleGetSystemRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        //system.out.println("[ProcessImpactServlet] handleGetSystemRelationships called with processId=" + processId);
        try {
            List<Map<String, Object>> relationships = processImpactService.getSystemRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "System", "systemId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProductRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProductRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Product", "productId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetClientRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getClientRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Client", "clientId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetGlossaryRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getGlossaryRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Glossary", "glossaryId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProjectRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProjectRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Project", "projectId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve project relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPolicyRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getPolicyRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Policy", "policyId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetInterfaceRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getInterfaceRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "SystemInterface", "interfaceId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve interface relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetLegalRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getLegalRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "LegalEntity", "legalId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetDatasetRelationships(int processId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getDatasetRelationships(processId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Dataset", "datasetId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve dataset relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetAttributeRelationships(int processId, HttpServletResponse response) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getAttributeRelationships(processId);
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve attribute relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetPredecessorRelationships(int processId, String view, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            // Check if viewing changes - use cloned process ID like Impact tab
            int processIdToLoad = processId; // Default to original ID
            
            if (view != null && "changes".equals(view.trim())) {
                // Like Impact tab: use cloned process ID for view=changes
                try {
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, processId);
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
                        if (nobjectId == null) {
                            // Clone process if needed
                            try (Connection conn = DatabaseConnection.getConnection()) {
                                nobjectId = cloneProcessRow(conn, processId);
                                if (nobjectId != null) {
                                    facetChangesDAO.saveMapping("process", processId, nobjectId, "summary", activeCrId);
                                    System.out.println("Cloned process " + processId + " to " + nobjectId + " for predecessors view=changes");
                                }
                            }
                        }
                        if (nobjectId != null) {
                            processIdToLoad = nobjectId;
                            System.out.println("Using cloned process ID " + nobjectId + " (original: " + processId + ") for predecessors view=changes");
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting cloned process for view=changes: " + e.getMessage());
                }
            }
            
            // Load relationships using processIdToLoad (cloned if view=changes, original otherwise)
            List<Map<String, Object>> relationships = processImpactService.getPredecessorRelationships(processIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve predecessor relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetSuccessorRelationships(int processId, String view, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            int processIdToLoad = processId;
            if (view != null && "changes".equals(view.trim())) {
                try {
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, processId);
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
                        if (nobjectId == null) {
                            try (Connection conn = DatabaseConnection.getConnection()) {
                                nobjectId = cloneProcessRow(conn, processId);
                                if (nobjectId != null) {
                                    facetChangesDAO.saveMapping("process", processId, nobjectId, "summary", activeCrId);
                                }
                            }
                        }
                        if (nobjectId != null) {
                            processIdToLoad = nobjectId;
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting cloned process for successors view=changes: " + e.getMessage());
                }
            }
            List<Map<String, Object>> relationships = processImpactService.getSuccessorRelationships(processIdToLoad);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve successor relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getProcessRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relation types", 500);
        }
    }
    
    private void handleSavePredecessorRelationships(int processId, HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            JsonObject body = JsonParser.parseReader(request.getReader()).getAsJsonObject();
            JsonArray relationshipsArray = body.getAsJsonArray("relationships");
            
            List<Map<String, Object>> relationships = new ArrayList<>();
            for (JsonElement element : relationshipsArray) {
                JsonObject rel = element.getAsJsonObject();
                Map<String, Object> relMap = new HashMap<>();
                if (rel.has("id") && !rel.get("id").isJsonNull()) {
                    relMap.put("id", rel.get("id").getAsInt());
                }
                relMap.put("targetProcessId", rel.get("targetProcessId").getAsInt());
                relMap.put("relationType", rel.get("relationType").getAsInt());
                if (rel.has("annotations") && !rel.get("annotations").isJsonNull()) {
                    relMap.put("annotations", rel.get("annotations").getAsString());
                }
                relationships.add(relMap);
            }

            int dfcrUserId = com.example.budg_v2.util.UserContextUtil.getCurrentUserId(request);
            Integer processType = null;
            try {
                com.example.budg_v2.service.ProcessService processService = new com.example.budg_v2.service.ProcessService();
                com.example.budg_v2.model.Process process = processService.getProcessById(processId);
                processType = process != null ? process.getType() : null;
            } catch (Exception ignored) {
            }
            boolean isAdmin = com.example.budg_v2.util.UserContextUtil.isCurrentUserAdmin(request);
            Integer activeCrId = dfcrUserId > 0
                    ? new com.example.budg_v2.service.DFCRService().ensureEditAutoCrIfMissing(
                            "Process", PROCESS_FACET_ID, processId, processType, dfcrUserId, isAdmin)
                    : null;
            
            // Handle pending changes for predecessor relationships (process_x_process)
            // Check for active CR and get cloned process ID (like Impact tab)
            int processIdToUse = processId; // Default to original ID
            
            try {
                if (activeCrId == null) {
                    activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(PROCESS_FACET_ID, processId);
                }
                if (activeCrId != null) {
                    // Get or create cloned process (like Impact tab does)
                    try (Connection conn = DatabaseConnection.getConnection()) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
                        
                        if (nobjectId == null) {
                            // No summary mapping exists - clone the process
                            nobjectId = cloneProcessRow(conn, processId);
                            if (nobjectId != null) {
                                facetChangesDAO.saveMapping("process", processId, nobjectId, "summary", activeCrId);
                                System.out.println("Cloned process " + processId + " to " + nobjectId + " for predecessors pending changes");
                            }
                        }
                        
                        if (nobjectId != null) {
                            processIdToUse = nobjectId;
                            System.out.println("Using cloned process ID " + nobjectId + " (original: " + processId + ") for predecessors");
                        } else {
                            System.err.println("Could not get/create cloned process for predecessors, using original ID " + processId);
                        }
                    } catch (SQLException e) {
                        System.err.println("Error getting/cloning process for predecessors: " + e.getMessage());
                        // Continue with original ID
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error checking for active CR: " + e.getMessage());
            }
            
            boolean success = processImpactService.savePredecessorRelationships(processIdToUse, relationships, request);
            
            // Save mappings for newly created relationships (if CR is active)
            if (success && activeCrId != null) {
                try {
                    String areaKey = "relationships#process_x_process";
                    // Get original relationship IDs before save
                    List<Map<String, Object>> originalRelationships = processImpactService.getPredecessorRelationships(processId);
                    java.util.Set<Integer> originalIds = new java.util.HashSet<>();
                    for (Map<String, Object> origRel : originalRelationships) {
                        Object idObj = origRel.get("id");
                        if (idObj != null) {
                            originalIds.add(((Number) idObj).intValue());
                        }
                    }
                    
                    // Get saved relationships and save mapping for new ones
                    List<Map<String, Object>> savedRelationships = processImpactService.getPredecessorRelationships(processIdToUse);
                    for (Map<String, Object> rel : savedRelationships) {
                        Object idObj = rel.get("id");
                        if (idObj != null) {
                            int relId = ((Number) idObj).intValue();
                            // If this ID is not in original, it's a new relationship - save mapping
                            if (!originalIds.contains(relId)) {
                                facetChangesDAO.saveMapping("process", processId, relId, areaKey, activeCrId);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[ProcessImpactServlet] Error saving predecessor relationship mappings: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            JsonObject result = new JsonObject();
            result.addProperty("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.addProperty("message", "Predecessor relationships saved as pending. They will apply when the Change Request is completed.");
                    result.addProperty("pendingChanges", true);
                    result.addProperty("changeRequestId", activeCrId);
                } else {
                    result.addProperty("message", "Predecessor relationships saved successfully");
                }
            } else {
                result.addProperty("message", "Failed to save predecessor relationships");
            }
            response.getWriter().write(gson.toJson(result));
            
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to save predecessor relationships: " + e.getMessage(), 500);
        }
    }

    // ===== GET RELATION TYPES HANDLERS =====
    
    private void handleGetSystemRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getSystemRelationTypes();
            //system.out.println("ProcessImpactServlet: Retrieved " + types.size() + " system relation types");
            if (types.size() > 0) {
                //system.out.println("ProcessImpactServlet: First system relation type: " + gson.toJson(types.get(0)));
            }
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system relation types", 500);
        }
    }
    
    private void handleGetProductRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getProductRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product relation types", 500);
        }
    }
    
    private void handleGetClientRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getClientRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client relation types", 500);
        }
    }
    
    private void handleGetGlossaryRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getGlossaryRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary relation types", 500);
        }
    }
    
    private void handleGetProjectRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getProjectRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve project relation types", 500);
        }
    }
    
    private void handleGetPolicyRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getPolicyRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy relation types", 500);
        }
    }
    
    private void handleGetInterfaceRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getInterfaceRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve interface relation types", 500);
        }
    }
    
    private void handleGetAllInterfaces(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> interfaces = processImpactService.getAllInterfaces();
            response.getWriter().write(gson.toJson(interfaces));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve interfaces", 500);
        }
    }
    
    private void handleGetLegalRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getLegalRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal relation types", 500);
        }
    }
    
    private void handleGetDatasetRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getDatasetRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve dataset relation types", 500);
        }
    }
    
    private void handleGetAttributeRelationTypes(HttpServletResponse response) throws IOException {
        try {
            List<Map<String, Object>> types = processImpactService.getAttributeRelationTypes();
            response.getWriter().write(gson.toJson(types));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve attribute relation types", 500);
        }
    }

    // ===== GET OWNER HANDLERS =====
    
    private void handleGetSystemOwner(int systemId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getSystemOwnersString(systemId);
            String ownerEmail = processImpactService.getSystemOwnersEmail(systemId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve system owner", 500);
        }
    }
    
    private void handleGetProductOwner(int productId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getProductOwnersString(productId);
            String ownerEmail = processImpactService.getProductOwnersEmail(productId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve product owner", 500);
        }
    }
    
    private void handleGetClientOwner(int clientId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getClientOwnersString(clientId);
            String ownerEmail = processImpactService.getClientOwnersEmail(clientId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve client owner", 500);
        }
    }
    
    private void handleGetGlossaryOwner(int glossaryId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getGlossaryOwnersString(glossaryId);
            String ownerEmail = processImpactService.getGlossaryOwnersEmail(glossaryId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve glossary owner", 500);
        }
    }
    
    private void handleGetProjectOwner(int projectId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getProjectOwnersString(projectId);
            String ownerEmail = processImpactService.getProjectOwnersEmail(projectId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve project owner", 500);
        }
    }
    
    private void handleGetPolicyOwner(int policyId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getPolicyOwnersString(policyId);
            String ownerEmail = processImpactService.getPolicyOwnersEmail(policyId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve policy owner", 500);
        }
    }
    
    private void handleGetInterfaceOwner(int interfaceId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getInterfaceOwnersString(interfaceId);
            String ownerEmail = processImpactService.getInterfaceOwnersEmail(interfaceId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve interface owner", 500);
        }
    }
    
    private void handleGetLegalOwner(int legalId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getLegalOwnersString(legalId);
            String ownerEmail = processImpactService.getLegalOwnersEmail(legalId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve legal owner", 500);
        }
    }
    
    private void handleGetDatasetOwner(int datasetId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getDatasetOwnersString(datasetId);
            String ownerEmail = processImpactService.getDatasetOwnersEmail(datasetId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve dataset owner", 500);
        }
    }
    
    private void handleGetAttributeOwner(int attributeId, HttpServletResponse response) throws IOException {
        try {
            String ownerName = processImpactService.getAttributeOwnersString(attributeId);
            String ownerEmail = processImpactService.getAttributeOwnersEmail(attributeId);
            
            Map<String, String> ownerData = new HashMap<>();
            ownerData.put("ownerName", ownerName);
            ownerData.put("ownerEmail", ownerEmail);
            
            response.getWriter().write(gson.toJson(ownerData));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve attribute owner", 500);
        }
    }
    
    private void handleGetDatasetsList(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("ProcessImpactServlet: Getting datasets list");
            List<Map<String, Object>> datasets = processImpactService.getDatasetsList();
            //system.out.println("ProcessImpactServlet: Retrieved " + datasets.size() + " datasets");
            
            response.getWriter().write(gson.toJson(datasets));
        } catch (Exception e) {
            System.err.println("ProcessImpactServlet: Error getting datasets list: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting datasets list: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetAttributesList(HttpServletResponse response) throws IOException {
        try {
            //system.out.println("ProcessImpactServlet: Getting attributes list");
            List<Map<String, Object>> attributes = processImpactService.getAttributesList();
            //system.out.println("ProcessImpactServlet: Retrieved " + attributes.size() + " attributes");
            
            response.getWriter().write(gson.toJson(attributes));
        } catch (Exception e) {
            System.err.println("ProcessImpactServlet: Error getting attributes list: " + e.getMessage());
            e.printStackTrace();
            sendError(response, "Error getting attributes list: " + e.getMessage(), 500);
        }
    }

    // ===== SAVE HANDLERS =====
    
    /**
     * All save handlers below follow the same pattern:
     * 1. Call handlePendingChangesForImpact() to get the correct process ID (cloned if CR active, original otherwise)
     * 2. Use processIdToUse when calling the service method to save relationships
     * 3. This ensures all impact relationship tables are saved to the right place:
     *    - If Change Request is active: saves go to cloned process (nobject_id) in the appropriate impact table
     *    - If no Change Request: saves go to original process (object_id) in the appropriate impact table
     * 
     * Impact relationship tables handled:
     * - process_x_system (System)
     * - product_x_process (Product)
     * - client_x_process (Client)
     * - glossary_x_process (Glossary)
     * - project_x_process (Project)
     * - policy_x_process (Policy)
     * - process_x_interface (Interface)
     * - process_x_legal (Legal)
     * - process_x_dataset (Dataset)
     * - process_x_attribute (Attribute)
     */
    
    /**
     * Helper method to handle pending changes logic for impact relationships
     * Returns the process ID to use (cloned if active CR exists, original otherwise) and the active CR ID
     */
    private int[] handlePendingChangesForImpact(HttpServletRequest request, int processId, String impactAreaKey, int userId) {
        int[] result = new int[]{processId, 0}; // [processIdToUse, activeCrId]
        
        try {
            Integer processType = null;
            try {
                com.example.budg_v2.service.ProcessService processService = new com.example.budg_v2.service.ProcessService();
                com.example.budg_v2.model.Process process = processService.getProcessById(processId);
                processType = process != null ? process.getType() : null;
            } catch (Exception ignored) {
                // facet-level DFCR still applies when type is unknown
            }
            boolean isAdmin = com.example.budg_v2.util.UserContextUtil.isCurrentUserAdmin(request);
            Integer activeCrId = new com.example.budg_v2.service.DFCRService().ensureEditAutoCrIfMissing(
                    "Process", PROCESS_FACET_ID, processId, processType, userId, isAdmin);
            
            if (activeCrId != null) {
                // Object is under revision - use cloned process ID
                try (Connection conn = DatabaseConnection.getConnection()) {
                    // Get or create mapping for 'summary' area (this creates the cloned process if needed)
                    Integer nobjectId = facetChangesDAO.getNObjectId("process", processId, "summary", activeCrId);
                    
                    if (nobjectId == null) {
                        // No summary mapping exists yet - need to clone the process row first
                        nobjectId = cloneProcessRow(conn, processId);
                        if (nobjectId != null) {
                            facetChangesDAO.saveMapping("process", processId, nobjectId, "summary", activeCrId);
                        }
                    }
                    
                    if (nobjectId != null) {
                        // Check if impact relationships have been initialized in cloned process
                        Integer impactMapping = facetChangesDAO.getNObjectId("process", processId, impactAreaKey, activeCrId);
                        if (impactMapping == null) {
                            // First time editing this impact type - copy all relationships from original to cloned
                            String relationType = impactAreaKey.substring("impact#".length());
                            initializeImpactRelationshipsInClonedProcess(conn, processId, nobjectId, relationType);
                            // Create mapping for this impact area
                            facetChangesDAO.saveMapping("process", processId, nobjectId, impactAreaKey, activeCrId);
                            //system.out.println("[ProcessImpactServlet] Initialized " + relationType + " relationships in cloned process " + nobjectId + " (original: " + processId + ")");
                        }
                        
                        result[0] = nobjectId;
                        result[1] = activeCrId;
                        //system.out.println("[ProcessImpactServlet] ✅ Saving " + impactAreaKey + " to cloned process ID " + nobjectId + " (original: " + processId + ", CR: " + activeCrId + ")");
                    } else {
                        System.err.println("[ProcessImpactServlet] ⚠️ WARNING: Could not get/create cloned process for " + impactAreaKey + ", saving to original process ID " + processId);
                    }
                } catch (Exception e) {
                    System.err.println("[ProcessImpactServlet] ❌ Error handling pending changes for " + impactAreaKey + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                //system.out.println("[ProcessImpactServlet] No active CR for process " + processId + ", saving " + impactAreaKey + " to original process");
            }
        } catch (Exception e) {
            System.err.println("[ProcessImpactServlet] ❌ Error in handlePendingChangesForImpact: " + e.getMessage());
            e.printStackTrace();
        }
        
        //system.out.println("[ProcessImpactServlet] handlePendingChangesForImpact returning: processIdToUse=" + result[0] + ", activeCrId=" + result[1]);
        return result;
    }
    
    private void handleSaveSystemRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_system", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "System", "systemId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveSystemRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "System relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "System relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save system relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving system relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveProductRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_product", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Product", "productId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveProductRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Product relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Product relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save product relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving product relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveClientRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_client", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Client", "clientId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveClientRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Client relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Client relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save client relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving client relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveGlossaryRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_glossary", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Glossary", "glossaryId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveGlossaryRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Glossary relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Glossary relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save glossary relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving glossary relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveProjectRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_project", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Project", "projectId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveProjectRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Project relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Project relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save project relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving project relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSavePolicyRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_policy", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Policy", "policyId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.savePolicyRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Policy relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Policy relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save policy relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving policy relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveInterfaceRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            //system.out.println("ProcessImpactServlet: handleSaveInterfaceRelationships called");
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            //system.out.println("ProcessImpactServlet: processId = " + processId);
            
            JsonElement relationshipsElement = jsonObject.get("relationships");
            List<Map<String, Object>> relationships = null;
            
            if (relationshipsElement != null && relationshipsElement.isJsonArray()) {
                relationships = gson.fromJson(relationshipsElement, RELATIONSHIPS_LIST_TYPE);
                //system.out.println("ProcessImpactServlet: Parsed " + (relationships != null ? relationships.size() : 0) + " interface relationships");
            } else {
                //system.out.println("ProcessImpactServlet: relationships element is null or not an array");
                relationships = new ArrayList<>();
            }
            
            int userId = getUserIdFromSession(request);
            //system.out.println("ProcessImpactServlet: userId = " + userId);

            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_interface", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;

            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Interface", "interfaceId", "systemInterfaceId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveInterfaceRelationships(processIdToUse, relationships, userId);
            
            //system.out.println("ProcessImpactServlet: saveInterfaceRelationships returned: " + success);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Interface relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Interface relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save interface relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            System.err.println("ProcessImpactServlet: Error in handleSaveInterfaceRelationships: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving interface relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveLegalRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_legal", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "LegalEntity", "legalId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveLegalRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Legal relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Legal relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save legal relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving legal relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveDatasetRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_dataset", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Dataset", "datasetId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveDatasetRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Dataset relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Dataset relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save dataset relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving dataset relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }
    
    private void handleSaveAttributeRelationships(HttpServletRequest request, HttpServletResponse response) 
            throws IOException {
        try {
            JsonObject jsonObject = parseRequestBody(request);
            int processId = jsonObject.get("processId").getAsInt();
            
            // Check edit permission + stakeholder status
            if (!PermissionCheckUtil.checkEditPermissionWithStakeholder(request, response, "Process", processId)) {
                return; // Response already sent
            }
            
            List<Map<String, Object>> relationships = gson.fromJson(
                jsonObject.get("relationships"),
                RELATIONSHIPS_LIST_TYPE
            );
            
            int userId = getUserIdFromSession(request);
            int[] pendingChanges = handlePendingChangesForImpact(request, processId, "impact#process_X_attribute", userId);
            int processIdToUse = pendingChanges[0];
            Integer activeCrId = pendingChanges[1] > 0 ? pendingChanges[1] : null;
            
            var validationResult = ImpactSegmentValidationUtil.validateRelationships(
                    processIdToUse, "Process", relationships, "Attribute", "attributeId");
            if (!validationResult.isValid) {
                sendError(response, validationResult.message, 400);
                return;
            }

            boolean success = processImpactService.saveAttributeRelationships(processIdToUse, relationships, userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", success);
            if (success) {
                if (activeCrId != null) {
                    result.put("message", "Attribute relationships saved as pending. They will apply when the Change Request is completed.");
                    result.put("pendingChanges", true);
                    result.put("changeRequestId", activeCrId);
                } else {
                    result.put("message", "Attribute relationships saved successfully");
                }
            } else {
                result.put("message", "Failed to save attribute relationships");
            }
            
            response.getWriter().write(gson.toJson(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "Error saving attribute relationships: " + e.getMessage());
            response.getWriter().write(gson.toJson(result));
        }
    }

    // ===== REVERSE LOOKUP HANDLERS =====
    
    private void handleGetProcessRelationshipsBySystemId(int systemId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProcessRelationshipsBySystemId(systemId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationshipsByGlossaryId(int glossaryId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProcessRelationshipsByGlossaryId(glossaryId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationshipsByCapabilityId(int capabilityId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            com.example.budg_v2.service.CapabilityImpactService capabilityImpactService = new com.example.budg_v2.service.CapabilityImpactService();
            List<Map<String, Object>> relationships = capabilityImpactService.getProcessRelationships(capabilityId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationshipsByClientId(int clientId, HttpServletResponse response, HttpServletRequest request) 
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProcessRelationshipsByClientId(clientId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationshipsByDatasetId(int datasetId, HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        try {
            // Check if viewing changes (pending changes mode)
            String view = request.getParameter("view");
            int datasetIdToLoad = datasetId;
            
            if ("changes".equals(view)) {
                try {
                    // Only check for automatic CRs for pending changes
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(11, datasetId); // 11 is DATASET_FACET_ID
                    if (activeCrId != null) {
                        // Check for impact-specific mapping first
                        Integer nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "impact#dataset_X_process", activeCrId);
                        // If no impact-specific mapping, fall back to summary mapping (cloned dataset ID)
                        if (nObjectId == null) {
                            nObjectId = facetChangesDAO.getNObjectId("dataset", datasetId, "summary", activeCrId);
                        }
                        if (nObjectId != null) {
                            datasetIdToLoad = nObjectId;
                            //system.out.println("[ProcessImpactServlet] Using cloned dataset ID " + nObjectId + " for process relationships (view=changes)");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error getting nobject_id for view=changes: " + e.getMessage());
                }
            }
            
            List<Map<String, Object>> relationships = processImpactService.getProcessRelationshipsByDatasetId(datasetIdToLoad);
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationshipsByProductId(int productId, HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProcessRelationshipsByProductId(productId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    private void handleGetProcessRelationshipsByLegalId(int legalId, HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        try {
            List<Map<String, Object>> relationships = processImpactService.getProcessRelationshipsByLegalId(legalId);
            relationships = RelationshipAccessUtil.filterBySegmentAccess(relationships, request, "Process", "processId");
            response.getWriter().write(gson.toJson(relationships));
        } catch (Exception e) {
            e.printStackTrace();
            sendError(response, "Failed to retrieve process relationships: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Initialize impact relationships in cloned process by copying from original process
     * This is called the first time an impact type is edited for a cloned process
     */
    private void initializeImpactRelationshipsInClonedProcess(Connection conn, int originalProcessId, int clonedProcessId, String relationType) throws SQLException {
        String insertSql = null;
        String whereColumn = null;
        
        // Build SQL based on actual table schemas
        switch (relationType) {
            case "process_X_system":
                // process_x_system: id (NOT AUTO_INCREMENT), process_id, system_id, relationtype, created_datetime, lastupdatedatetime, last_update_userid
                // Need to generate new IDs - first get max ID, then insert with calculated IDs
                // Use a cross join to get the max ID once, then increment per row
                insertSql = "INSERT INTO process_x_system (id, process_id, system_id, relationtype, created_datetime, lastupdatedatetime, last_update_userid) " +
                           "SELECT (@row_num := @row_num + 1) + base_id.max_id, " +
                           "?, system_id, relationtype, created_datetime, NOW(), last_update_userid " +
                           "FROM process_x_system " +
                           "CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM process_x_system) AS base_id " +
                           "CROSS JOIN (SELECT @row_num := 0) AS r " +
                           "WHERE process_id = ?";
                whereColumn = "process_id";
                break;
                
            case "process_X_interface":
                // process_x_interface: id (AUTO), process_id, interface_id, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid
                insertSql = "INSERT INTO process_x_interface (process_id, interface_id, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid) " +
                           "SELECT ?, interface_id, relationtype, createdatetime, NOW(), lastupdate_userid " +
                           "FROM process_x_interface WHERE process_id = ?";
                whereColumn = "process_id";
                break;
                
            case "process_X_dataset":
                // process_x_dataset: id (AUTO), processid, relation_type, description, datasetid, createdatetime, lastupdatedatetime, lastudpate_userid
                insertSql = "INSERT INTO process_x_dataset (processid, relation_type, description, datasetid, createdatetime, lastupdatedatetime, lastudpate_userid) " +
                           "SELECT ?, relation_type, description, datasetid, createdatetime, NOW(), lastudpate_userid " +
                           "FROM process_x_dataset WHERE processid = ?";
                whereColumn = "processid";
                break;
                
            case "process_X_attribute":
                // process_x_attribute: id (AUTO), processid, relation_type, description, attributeid, createdatetime, lastupdatedatetime, lastudpate_userid
                insertSql = "INSERT INTO process_x_attribute (processid, relation_type, description, attributeid, createdatetime, lastupdatedatetime, lastudpate_userid) " +
                           "SELECT ?, relation_type, description, attributeid, createdatetime, NOW(), lastudpate_userid " +
                           "FROM process_x_attribute WHERE processid = ?";
                whereColumn = "processid";
                break;
                
            case "process_X_product":
                // product_x_process: id (AUTO), productid, processid, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid
                insertSql = "INSERT INTO product_x_process (productid, processid, relationtype, createdatetime, lastupdatedatetime, lastupdate_userid) " +
                           "SELECT productid, ?, relationtype, createdatetime, NOW(), lastupdate_userid " +
                           "FROM product_x_process WHERE processid = ?";
                whereColumn = "processid";
                break;
                
            case "process_X_client":
                // client_x_process: ID (AUTO), Process_ID, Client_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID
                insertSql = "INSERT INTO client_x_process (Process_ID, Client_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdate_UserID) " +
                           "SELECT ?, Client_ID, RelationType, CreateDatetime, NOW(), LastUpdate_UserID " +
                           "FROM client_x_process WHERE Process_ID = ?";
                whereColumn = "Process_ID";
                break;
                
            case "process_X_glossary":
                // glossary_x_process: id (AUTO), Glossary_ID, Process_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID
                insertSql = "INSERT INTO glossary_x_process (Glossary_ID, Process_ID, RelationType, Description, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) " +
                           "SELECT Glossary_ID, ?, RelationType, Description, CreateDatetime, NOW(), LastUpdateUser_ID " +
                           "FROM glossary_x_process WHERE Process_ID = ?";
                whereColumn = "Process_ID";
                break;
                
            case "process_X_project":
                // project_x_process: id (AUTO), projectid, process_id, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid
                insertSql = "INSERT INTO project_x_process (projectid, process_id, relationtype, description, createdatetime, lastupdatedatetime, lastupdate_userid) " +
                           "SELECT projectid, ?, relationtype, description, createdatetime, NOW(), lastupdate_userid " +
                           "FROM project_x_process WHERE process_id = ?";
                whereColumn = "process_id";
                break;
                
            case "process_X_policy":
                // policy_x_process: id (AUTO), policy_id, process_id, relation_type, description, createdatetime, lastupdatedatetime, lastupdate_userid
                insertSql = "INSERT INTO policy_x_process (policy_id, process_id, relation_type, description, createdatetime, lastupdatedatetime, lastupdate_userid) " +
                           "SELECT policy_id, ?, relation_type, description, createdatetime, NOW(), lastupdate_userid " +
                           "FROM policy_x_process WHERE process_id = ?";
                whereColumn = "process_id";
                break;
                
            case "process_X_legal":
                // process_x_legal: ID (AUTO), Process_ID, Legal_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID
                insertSql = "INSERT INTO process_x_legal (Process_ID, Legal_ID, RelationType, CreateDatetime, LastUpdateDatetime, LastUpdateUser_ID) " +
                           "SELECT ?, Legal_ID, RelationType, CreateDatetime, NOW(), LastUpdateUser_ID " +
                           "FROM process_x_legal WHERE Process_ID = ?";
                whereColumn = "Process_ID";
                break;
                
            case "process_X_process":
                // process_x_process: id (AUTO), sourceprocess_id, targetprocess_id, relationtype, rank, annotations, createdatetime, lastupdatedateime, lastupdate_userid
                insertSql = "INSERT INTO process_x_process (sourceprocess_id, targetprocess_id, relationtype, rank, annotations, createdatetime, lastupdatedateime, lastupdate_userid) " +
                           "SELECT ?, targetprocess_id, relationtype, rank, annotations, createdatetime, NOW(), lastupdate_userid " +
                           "FROM process_x_process WHERE sourceprocess_id = ?";
                whereColumn = "sourceprocess_id";
                break;
                
            default:
                System.err.println("[ProcessImpactServlet] Unknown relation type: " + relationType);
                return;
        }
        
        if (insertSql == null || whereColumn == null) {
            System.err.println("[ProcessImpactServlet] Failed to build SQL for relation type: " + relationType);
            return;
        }
        
        //system.out.println("[ProcessImpactServlet] Initializing " + relationType + " relationships:");
        //system.out.println("  SQL: " + insertSql);
        //system.out.println("  Original Process ID: " + originalProcessId);
        //system.out.println("  Cloned Process ID: " + clonedProcessId);
        
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setInt(1, clonedProcessId);
            stmt.setInt(2, originalProcessId);
            stmt.executeUpdate();
            //system.out.println("[ProcessImpactServlet] ✅ Successfully copied " + rowsCopied + " " + relationType + " relationships from process " + originalProcessId + " to " + clonedProcessId);
        } catch (SQLException e) {
            System.err.println("[ProcessImpactServlet] ❌ Error copying " + relationType + " relationships: " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Failed to initialize " + relationType + " relationships: " + e.getMessage(), e);
        }
    }
    
    // ===== UTILITY METHODS =====
    
    private void handleHealthCheck(HttpServletResponse response) throws IOException {
        Map<String, String> health = new HashMap<>();
        health.put("status", "OK");
        health.put("service", "ProcessImpactServlet");
        response.getWriter().write(gson.toJson(health));
    }
    
    private JsonObject parseRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder buffer = new StringBuilder();
        BufferedReader reader = request.getReader();
        String line;
        while ((line = reader.readLine()) != null) {
            buffer.append(line);
        }
        return gson.fromJson(buffer.toString(), JsonObject.class);
    }
    
    private int getUserIdFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            return (int) session.getAttribute("userId");
        }
        return 1; // Default user ID for testing
    }
    
    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        response.getWriter().write(gson.toJson(error));
    }

    /**
     * Clone a process row for pending changes (same as ProcessServlet.cloneProcessRow) using an existing connection.
     */
    private Integer cloneProcessRow(Connection conn, int originalId) throws Exception {
        String sql = "INSERT INTO process (" +
                "primaryname, description, parentid, ispublic, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id" +
                ") SELECT " +
                "primaryname, description, parentid, ispublic, status, type, duration_type, duration, " +
                "lifecycle_status, processclass_id, processautomation_id, refnumber, " +
                "input_description, output_description, step_type, " +
                "cancreate, canread, canupdate, candelete, canarchive, " +
                "createdby_id, createdatetime, lastupdatedatetime, lastupdateuser_id " +
                "FROM process WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, originalId);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        }
        return null;
    }
    
    // OLD METHODS REMOVED - Now using Impact tab pattern (cloned process ID)
    // getPredecessorsWithPendingChanges and getOriginalPredecessors are no longer needed
    
    /**
     * Get a single predecessor relationship with all names (targetProcessName, relationTypeName, targetProcessType, targetProcessRef)
     * This is used for pending predecessors to ensure proper display
     */
    @SuppressWarnings("unused")
    private Map<String, Object> getPredecessorWithNames(int relationshipId) throws SQLException {
        String sql = """
            SELECT 
                pxp.id,
                pxp.sourceprocess_id as sourceProcessId,
                pxp.targetprocess_id as targetProcessId,
                pxp.relationtype as relationType,
                pxp.rank,
                pxp.annotations,
                p.primaryname as targetProcessName,
                p.refnumber as targetProcessRef,
                pt.primaryname as targetProcessType,
                rt.primaryname as relationTypeName
            FROM process_x_process pxp
            LEFT JOIN process p ON pxp.targetprocess_id = p.id
            LEFT JOIN process_type pt ON pt.id = p.type
            LEFT JOIN process_x_process_relationtype rt ON pxp.relationtype = rt.id
            WHERE pxp.id = ? AND (p.deleteddatetime IS NULL OR p.deleteddatetime = '1970-01-01 00:00:00')
        """;
        
        try (java.sql.Connection conn = DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, relationshipId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("sourceProcessId", rs.getInt("sourceProcessId"));
                map.put("targetProcessId", rs.getInt("targetProcessId"));
                map.put("relationType", rs.getInt("relationType"));
                map.put("rank", rs.getInt("rank"));
                map.put("annotations", rs.getString("annotations"));
                map.put("targetProcessName", rs.getString("targetProcessName"));
                map.put("targetProcessRef", rs.getString("targetProcessRef"));
                map.put("targetProcessType", rs.getString("targetProcessType"));
                map.put("relationTypeName", rs.getString("relationTypeName"));
                return map;
            }
        }
    }
}

