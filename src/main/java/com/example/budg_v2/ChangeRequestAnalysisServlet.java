package com.example.budg_v2;

import com.example.budg_v2.dao.ChangeRequestAnalysisDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.model.ChangeRequestAnalysis;
import com.example.budg_v2.service.PermissionService;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@WebServlet("/api/changerequest-analysis/*")
public class ChangeRequestAnalysisServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestAnalysisServlet.class);
    private final ChangeRequestAnalysisDAO analysisDAO = new ChangeRequestAnalysisDAO();
    
    // ===== Inline thread-safe cache (no extra files needed) =====
    private static final long CACHE_TTL_MS = 30_000; // 30 seconds
    private static final ConcurrentHashMap<Integer, CachedResult> cache = new ConcurrentHashMap<>();
    
    private static class CachedResult {
        final String json;
        final long timestamp;
        CachedResult(String json) { this.json = json; this.timestamp = System.currentTimeMillis(); }
        boolean isValid() { return (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS; }
    }
    
    /** Invalidate cache for a given CR – called after POST/PUT */
    private static void invalidateCache(int changeRequestId) {
        cache.remove(changeRequestId);
    }
    
    // Gson instance configured with LocalDateTime adapter
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
    // LocalDateTime adapter for Gson
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return LocalDateTime.parse(dateTimeString, formatter);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String changeRequestIdStr = request.getParameter("changeRequestId");
        
        try {
            if (changeRequestIdStr == null || changeRequestIdStr.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int changeRequestId = Integer.parseInt(changeRequestIdStr);
            
            // No permission check for viewing - all authenticated users can view analysis/resolution
            // Permission restrictions apply only to POST/PUT (create/edit) operations
            
            // Check cache first — avoids DB hit for concurrent requests on the same CR
            CachedResult cached = cache.get(changeRequestId);
            if (cached != null && cached.isValid()) {
                logger.debug("Cache HIT for analysis CR: {}", changeRequestId);
                response.setHeader("X-Cache", "HIT");
                response.getWriter().write(cached.json);
                return;
            }
            
            if (analysisDAO == null) {
                logger.error("AnalysisDAO is null!");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "DAO initialization error");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            List<ChangeRequestAnalysis> analysisList = analysisDAO.getAnalysisByChangeRequestId(changeRequestId);
            String jsonResult = gson.toJson(analysisList);
            
            // Store in cache
            cache.put(changeRequestId, new CachedResult(jsonResult));
            
            logger.debug("Cache MISS for analysis CR: {} (returned {} entries)", changeRequestId, analysisList.size());
            response.setHeader("X-Cache", "MISS");
            response.getWriter().write(jsonResult);
            
        } catch (NumberFormatException e) {
            logger.error("Invalid change request ID: {}", changeRequestIdStr);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid change request ID");
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            // If table doesn't exist, return empty array instead of error
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || 
                e.getMessage().contains("Unknown table") || 
                e.getMessage().contains("Table") && e.getMessage().contains("doesn't exist"))) {
                logger.warn("Analysis table may not exist, returning empty array: {}", e.getMessage());
                response.getWriter().write(gson.toJson(new ArrayList<>()));
            } else {
                logger.error("Database error in GET /api/changerequest-analysis for changeRequestId {}: {}", 
                           changeRequestIdStr, e.getMessage(), e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Database error: " + e.getMessage());
                if (changeRequestIdStr != null) {
                    error.addProperty("changeRequestId", changeRequestIdStr);
                }
                response.getWriter().write(gson.toJson(error));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in GET /api/changerequest-analysis for changeRequestId {}: {}", 
                       changeRequestIdStr != null ? changeRequestIdStr : "unknown", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Parse request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();
            
            // Get change request ID from request
            if (!requestData.has("changeRequestId")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            int changeRequestId = requestData.get("changeRequestId").getAsInt();
            
            // Check if analysis already exists for this CR
            boolean hasAnalysis = analysisDAO.hasAnalysisForChangeRequest(changeRequestId);
            if (hasAnalysis) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Analysis already exists for this change request. Only editing existing analysis is permitted.");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Get change request to check permissions
            ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check permission to add analysis:
            // Allowed: 1. requester (creator), 2. super admin/admin, 3. web user with edit permission on CR && stakeholder
            boolean canAdd = false;
            
            // 1. Check if user is the requester (creator)
            Integer createdBy = changeRequest.getCreatedBy();
            boolean isCreator = (createdBy != null && createdBy.equals(userId));
            
            if (isCreator) {
                canAdd = true;
            } else {
                // 2. Check if user is super admin or admin
                boolean isSuperAdmin = false;
                boolean isAdmin = false;
                try {
                    isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                    if (!isSuperAdmin) {
                        isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                    }
                } catch (SQLException e) {
                    logger.warn("Error checking admin status: {}", e.getMessage());
                }
                
                if (isSuperAdmin || isAdmin) {
                    canAdd = true;
                } else {
                    // 3. Check if user is a stakeholder with edit permission on Change Requests
                    // For auto CRs, check stakeholders on the CR itself (cr_stakeholders table)
                    // For manual CRs, check stakeholders from source object
                    boolean isAutoCR = changeRequest.getMandatoryWorkflow() != null && changeRequest.getMandatoryWorkflow();
                    boolean isStakeholder = false;
                    
                    try {
                        CRStakeholderDAO stakeholderDAO = new CRStakeholderDAO();
                        List<Map<String, Object>> stakeholders;
                        
                        if (isAutoCR) {
                            // For auto CRs, first check stakeholders directly on the CR
                            stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
                            logger.info("POST: Auto CR detected - checking stakeholders on CR {} itself, found {} stakeholders", changeRequestId, stakeholders.size());
                            
                            // If no stakeholders found in cr_stakeholders, fall back to source object
                            if (stakeholders.isEmpty()) {
                                String reference = changeRequest.getReference();
                                if (reference != null && !reference.trim().isEmpty()) {
                                    logger.info("POST: No stakeholders in cr_stakeholders for auto CR {}, falling back to source object: {}", changeRequestId, reference);
                                    stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                                    logger.info("POST: Found {} stakeholders from source object", stakeholders.size());
                                }
                            }
                        } else {
                            // For manual CRs, check stakeholders from source object
                            String reference = changeRequest.getReference();
                            if (reference != null && !reference.trim().isEmpty()) {
                                stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                                logger.info("POST: Manual CR detected - checking stakeholders from source object: {}", reference);
                            } else {
                                stakeholders = new ArrayList<>();
                            }
                        }
                        
                        logger.info("POST: Checking {} stakeholders for user {} on CR {} (isAutoCR: {})", stakeholders.size(), userId, changeRequestId, isAutoCR);
                        
                        // Log all stakeholder keys for debugging
                        if (!stakeholders.isEmpty()) {
                            logger.info("POST: Sample stakeholder keys: {}", stakeholders.get(0).keySet());
                        }
                        
                        for (Map<String, Object> stakeholder : stakeholders) {
                            Integer stakeholderUserId = null;
                            
                            // Try multiple field name variations
                            Object userIdObj = null;
                            String foundKey = null;
                            if (stakeholder.containsKey("personId")) {
                                userIdObj = stakeholder.get("personId");
                                foundKey = "personId";
                            } else if (stakeholder.containsKey("userId")) {
                                userIdObj = stakeholder.get("userId");
                                foundKey = "userId";
                            } else if (stakeholder.containsKey("User_ID")) {
                                userIdObj = stakeholder.get("User_ID");
                                foundKey = "User_ID";
                            } else if (stakeholder.containsKey("user_id")) {
                                userIdObj = stakeholder.get("user_id");
                                foundKey = "user_id";
                            }
                            
                            logger.debug("POST: Stakeholder entry - foundKey: {}, userIdObj: {} (type: {})", foundKey, userIdObj, userIdObj != null ? userIdObj.getClass().getName() : "null");
                            
                            // Extract integer value from various types
                            if (userIdObj != null) {
                                if (userIdObj instanceof Number) {
                                    stakeholderUserId = ((Number) userIdObj).intValue();
                                } else if (userIdObj instanceof String) {
                                    try {
                                        stakeholderUserId = Integer.parseInt((String) userIdObj);
                                    } catch (NumberFormatException e) {
                                        logger.warn("POST: Could not parse userId as integer: {}", userIdObj);
                                    }
                                }
                            }
                            
                            logger.debug("POST: Comparing stakeholderUserId: {} with current userId: {}", stakeholderUserId, userId);
                            
                            if (stakeholderUserId != null && stakeholderUserId.equals(userId)) {
                                isStakeholder = true;
                                logger.info("POST: ✓ User {} is a stakeholder on CR {} (matched stakeholder userId: {} from key: {})", userId, changeRequestId, stakeholderUserId, foundKey);
                                break;
                            }
                        }
                        
                        if (isStakeholder) {
                            // Check edit permission on Change Requests module
                            PermissionService permissionService = new PermissionService();
                            boolean hasEditPermission = permissionService.canEdit(userId, "Change Requests");
                            
                            logger.info("POST: User {} stakeholder check: isStakeholder={}, hasEditPermission={}", userId, isStakeholder, hasEditPermission);
                            
                            if (hasEditPermission) {
                                canAdd = true;
                                logger.info("POST: ✓ User {} authorized to add analysis as stakeholder with edit permission on Change Requests", userId);
                            } else {
                                logger.error("POST: ✗ User {} is stakeholder but does NOT have edit permission on Change Requests - PERMISSION DENIED", userId);
                            }
                        } else {
                            logger.error("POST: ✗ User {} is NOT a stakeholder on CR {} (checked {} stakeholders) - STAKEHOLDER CHECK FAILED", userId, changeRequestId, stakeholders.size());
                            if (!stakeholders.isEmpty()) {
                                logger.error("POST: Available stakeholder user IDs: {}", 
                                    stakeholders.stream()
                                        .map(s -> {
                                            Object id = s.get("userId") != null ? s.get("userId") : 
                                                       s.get("personId") != null ? s.get("personId") :
                                                       s.get("User_ID") != null ? s.get("User_ID") : "unknown";
                                            return id.toString();
                                        })
                                        .collect(java.util.stream.Collectors.joining(", ")));
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("POST: Error checking stakeholder status or permissions: {}", e.getMessage(), e);
                    }
                }
            }
            
            if (!canAdd) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Access denied. Only requester, super admin/admin, or web user with edit permission on Change Requests and stakeholder can add analysis.");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Get analysis text from request
            String analysisText = requestData.has("analysis") ? requestData.get("analysis").getAsString() : "";
            
            if (analysisText == null || analysisText.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Analysis text is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Create new analysis record
            ChangeRequestAnalysis analysis = new ChangeRequestAnalysis();
            analysis.setChangeRequestId(changeRequestId);
            analysis.setAnalysis(analysisText);
            analysis.setLastUserChange(userId);
            LocalDateTime now = LocalDateTime.now();
            analysis.setCreatedAt(now);
            analysis.setUpdatedAt(now);
            
            int newAnalysisId = analysisDAO.createAnalysis(analysis);
            
            if (newAnalysisId > 0) {
                // Invalidate cache so next GET fetches fresh data
                invalidateCache(changeRequestId);
                
                JsonObject result = new JsonObject();
                result.addProperty("message", "Analysis created successfully");
                result.addProperty("id", newAnalysisId);
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Failed to create analysis");
                response.getWriter().write(gson.toJson(error));
            }
            
        } catch (SQLException e) {
            logger.error("Database error in POST /api/changerequest-analysis: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in POST /api/changerequest-analysis: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Get analysis ID from path
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Analysis ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int analysisId;
            try {
                analysisId = Integer.parseInt(pathInfo.substring(1));
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid analysis ID");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User not authenticated");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Verify analysis exists and get change request ID
            ChangeRequestAnalysis existingAnalysis = analysisDAO.getAnalysisById(analysisId);
            if (existingAnalysis == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Analysis not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int changeRequestId = existingAnalysis.getChangeRequestId();

            // Get change request to check permissions
            ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Check CR status first
            String statusName = changeRequest.getStatusName();
            if (statusName == null) {
                statusName = changeRequestDAO.getStatusNameById(changeRequest.getCrStatusId());
            }
            String statusLower = (statusName != null) ? statusName.toLowerCase() : "";
            boolean isRunning = statusLower.contains("running") || statusLower.contains("in progress");
            
            // Check edit permission: 
            // Allowed: super admin, admin, requester (creator),
            //          OR web user with edit permission on Change Requests facet + stakeholder on this CR
            boolean canEdit = false;
            String reason = "";
            boolean isStakeholder = false; // Declare at higher scope for error logging
            
            // 1. Check if user is super admin or admin
            boolean isSuperAdmin = false;
            boolean isAdmin = false;
            try {
                isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                if (!isSuperAdmin) {
                    isAdmin = UserContextUtil.isCurrentUserAdmin(request);
                }
            } catch (SQLException e) {
                logger.warn("Error checking admin status: {}", e.getMessage());
            }
            
            if (isSuperAdmin || isAdmin) {
                canEdit = true;
                reason = isSuperAdmin ? "super admin" : "admin";
            } else {
                // 2. Check if user is the requester (creator)
                Integer createdBy = changeRequest.getCreatedBy();
                boolean isCreator = (createdBy != null && createdBy.equals(userId));
                
                if (isCreator) {
                    canEdit = true;
                    reason = "requester (creator)";
                } else {
                    // 3. Check if user is a stakeholder with edit permission on Change Requests
                    // For auto CRs, check stakeholders on the CR itself (cr_stakeholders table)
                    // If cr_stakeholders is empty, fall back to source object stakeholders
                    // For manual CRs, check stakeholders from source object
                    boolean isAutoCR = changeRequest.getMandatoryWorkflow() != null && changeRequest.getMandatoryWorkflow();
                    
                    try {
                        CRStakeholderDAO stakeholderDAO = new CRStakeholderDAO();
                        List<Map<String, Object>> stakeholders;
                        
                        if (isAutoCR) {
                            // For auto CRs, first check stakeholders directly on the CR
                            stakeholders = stakeholderDAO.getStakeholdersForChangeRequest(changeRequestId);
                            logger.info("PUT: Auto CR detected - checking stakeholders on CR {} itself, found {} stakeholders", changeRequestId, stakeholders.size());
                            
                            // If no stakeholders found in cr_stakeholders, fall back to source object
                            if (stakeholders.isEmpty()) {
                                String reference = changeRequest.getReference();
                                if (reference != null && !reference.trim().isEmpty()) {
                                    logger.info("PUT: No stakeholders in cr_stakeholders for auto CR {}, falling back to source object: {}", changeRequestId, reference);
                                    stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                                    logger.info("PUT: Found {} stakeholders from source object", stakeholders.size());
                                }
                            }
                        } else {
                            // For manual CRs, check stakeholders from source object
                            String reference = changeRequest.getReference();
                            if (reference != null && !reference.trim().isEmpty()) {
                                stakeholders = stakeholderDAO.getStakeholdersFromSourceObject(reference);
                                logger.info("PUT: Manual CR detected - checking stakeholders from source object: {}", reference);
                            } else {
                                stakeholders = new ArrayList<>();
                            }
                        }
                        
                        logger.info("PUT: Checking {} stakeholders for user {} on CR {} (isAutoCR: {})", stakeholders.size(), userId, changeRequestId, isAutoCR);
                        
                        // Log all stakeholder keys for debugging
                        if (!stakeholders.isEmpty()) {
                            logger.info("PUT: Sample stakeholder keys: {}", stakeholders.get(0).keySet());
                        }
                        
                        for (Map<String, Object> stakeholder : stakeholders) {
                            Integer stakeholderUserId = null;
                            
                            // Try multiple field name variations
                            Object userIdObj = null;
                            String foundKey = null;
                            if (stakeholder.containsKey("personId")) {
                                userIdObj = stakeholder.get("personId");
                                foundKey = "personId";
                            } else if (stakeholder.containsKey("userId")) {
                                userIdObj = stakeholder.get("userId");
                                foundKey = "userId";
                            } else if (stakeholder.containsKey("User_ID")) {
                                userIdObj = stakeholder.get("User_ID");
                                foundKey = "User_ID";
                            } else if (stakeholder.containsKey("user_id")) {
                                userIdObj = stakeholder.get("user_id");
                                foundKey = "user_id";
                            }
                            
                            logger.debug("PUT: Stakeholder entry - foundKey: {}, userIdObj: {} (type: {})", foundKey, userIdObj, userIdObj != null ? userIdObj.getClass().getName() : "null");
                            
                            // Extract integer value from various types
                            if (userIdObj != null) {
                                if (userIdObj instanceof Number) {
                                    stakeholderUserId = ((Number) userIdObj).intValue();
                                } else if (userIdObj instanceof String) {
                                    try {
                                        stakeholderUserId = Integer.parseInt((String) userIdObj);
                                    } catch (NumberFormatException e) {
                                        logger.warn("PUT: Could not parse userId as integer: {}", userIdObj);
                                    }
                                }
                            }
                            
                            logger.debug("PUT: Comparing stakeholderUserId: {} with current userId: {}", stakeholderUserId, userId);
                            
                            if (stakeholderUserId != null && stakeholderUserId.equals(userId)) {
                                isStakeholder = true;
                                logger.info("PUT: ✓ User {} is a stakeholder on CR {} (matched stakeholder userId: {} from key: {})", userId, changeRequestId, stakeholderUserId, foundKey);
                                break;
                            }
                        }
                        
                        if (isStakeholder) {
                            // Check edit permission on Change Requests module
                            PermissionService permissionService = new PermissionService();
                            boolean hasEditPermission = permissionService.canEdit(userId, "Change Requests");
                            
                            logger.info("PUT: User {} stakeholder check: isStakeholder={}, hasEditPermission={}", userId, isStakeholder, hasEditPermission);
                            
                            if (hasEditPermission) {
                                canEdit = true;
                                reason = "stakeholder with edit permission on Change Requests";
                                logger.info("PUT: ✓ User {} authorized to edit analysis as stakeholder with edit permission on Change Requests", userId);
                            } else {
                                logger.error("PUT: ✗ User {} is stakeholder but does NOT have edit permission on Change Requests - PERMISSION DENIED", userId);
                            }
                        } else {
                            logger.error("PUT: ✗ User {} is NOT a stakeholder on CR {} (checked {} stakeholders) - STAKEHOLDER CHECK FAILED", userId, changeRequestId, stakeholders.size());
                            if (!stakeholders.isEmpty()) {
                                logger.error("PUT: Available stakeholder user IDs: {}", 
                                    stakeholders.stream()
                                        .map(s -> {
                                            Object id = s.get("userId") != null ? s.get("userId") : 
                                                       s.get("personId") != null ? s.get("personId") :
                                                       s.get("User_ID") != null ? s.get("User_ID") : "unknown";
                                            return id.toString();
                                        })
                                        .collect(java.util.stream.Collectors.joining(", ")));
                            }
                        }
                    } catch (SQLException e) {
                        logger.error("PUT: Error checking stakeholder status or permissions: {}", e.getMessage(), e);
                    }
                }
            }
            
            // If CR status is RUNNING, only users who can edit CR can edit analysis/resolution
            if (isRunning && !canEdit) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Cannot edit analysis when change request status is RUNNING. Only users who can edit the change request can edit analysis.");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            if (!canEdit) {
                boolean isCreator = changeRequest.getCreatedBy() != null && changeRequest.getCreatedBy().equals(userId);
                logger.error("PUT: ✗ EDIT DENIED for user {} on analysis {} (CR {}). Reason: Not admin, not creator, not stakeholder with edit permission. isSuperAdmin: {}, isAdmin: {}, isCreator: {}, isStakeholder: {}", 
                    userId, analysisId, changeRequestId, isSuperAdmin, isAdmin, isCreator, isStakeholder);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                JsonObject error = new JsonObject();
                String errorMessage = "Access denied. Only super admin, admin, requester, or stakeholder with edit permission can edit analysis.";
                if (!isStakeholder) {
                    errorMessage += " (User is not a stakeholder on this CR)";
                } else {
                    errorMessage += " (User is a stakeholder but does not have edit permission on Change Requests)";
                }
                error.addProperty("error", errorMessage);
                error.addProperty("debug", String.format("isSuperAdmin: %s, isAdmin: %s, isCreator: %s, isStakeholder: %s", 
                    isSuperAdmin, isAdmin, isCreator, isStakeholder));
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            logger.info("User {} authorized to edit analysis {} for CR {} (reason: {}, status: {})", userId, analysisId, changeRequestId, reason, statusName);

            // Parse request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();
            String analysisText = requestData.get("analysis").getAsString();
            
            if (analysisText == null || analysisText.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Analysis text is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Update analysis record
            ChangeRequestAnalysis analysis = new ChangeRequestAnalysis();
            analysis.setId(analysisId);
            analysis.setAnalysis(analysisText);
            analysis.setLastUserChange(userId);
            
            boolean updated = analysisDAO.updateAnalysis(analysis);
            
            if (updated) {
                // Invalidate cache so next GET fetches fresh data
                invalidateCache(changeRequestId);
                
                JsonObject result = new JsonObject();
                result.addProperty("message", "Analysis updated successfully");
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Analysis not found");
                response.getWriter().write(gson.toJson(error));
            }
            
        } catch (SQLException e) {
            logger.error("Database error in PUT /api/changerequest-analysis: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in PUT /api/changerequest-analysis: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
}
