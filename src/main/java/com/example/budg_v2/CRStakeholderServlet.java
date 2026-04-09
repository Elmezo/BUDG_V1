package com.example.budg_v2;

import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for CR Stakeholder operations
 * Endpoints:
 * GET /api/cr-stakeholders?crId={id} - Get stakeholders for a change request
 * GET /api/cr-stakeholders/community?crId={id} - Get stakeholder community for a change request
 * POST /api/cr-stakeholders/sync - Sync stakeholders from source object to all its CRs
 */
@WebServlet(urlPatterns = {"/api/cr-stakeholders", "/api/cr-stakeholders/*"})
public class CRStakeholderServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(CRStakeholderServlet.class);
    
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
    private final CRStakeholderDAO crStakeholderDAO = new CRStakeholderDAO();
    private final ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
    
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

        String pathInfo = request.getPathInfo();
        logger.info("CRStakeholderServlet doGet - pathInfo: {}", pathInfo);
        
        // Handle stakeholder community endpoint
        if (pathInfo != null && pathInfo.equals("/community")) {
            logger.info("Handling stakeholder community request");
            handleGetStakeholderCommunity(request, response);
            return;
        }

        try {
            String crIdParam = request.getParameter("crId");
            
            if (crIdParam == null || crIdParam.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "crId parameter is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            int crId = Integer.parseInt(crIdParam);
            
            // Get the change request to find its reference
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(crId);
            
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Check if this is an auto CR (mandatory workflow)
            Boolean mandatoryWorkflow = changeRequest.getMandatoryWorkflow();
            boolean isAutoCR = (mandatoryWorkflow != null && mandatoryWorkflow);
            
            List<Map<String, Object>> stakeholders;
            
            if (isAutoCR) {
                // For auto CRs, get stakeholders from the source object (e.g., System)
                String reference = changeRequest.getReference();
                logger.info("Fetching stakeholders for auto CR {} from source object: {}", crId, reference);
                
                if (reference != null && !reference.trim().isEmpty()) {
                    stakeholders = crStakeholderDAO.getStakeholdersFromSourceObject(reference);
                } else {
                    logger.warn("Auto CR {} has no reference - cannot fetch stakeholders from source object", crId);
                    stakeholders = new java.util.ArrayList<>();
                }
            } else {
                // For manual CRs, get stakeholders from cr_stakeholders table
                logger.info("Fetching stakeholders for manual CR {} from cr_stakeholders table", crId);
                stakeholders = crStakeholderDAO.getStakeholdersForChangeRequest(crId);
            }
            
            logger.info("Found {} stakeholders for CR {} (isAutoCR: {})", stakeholders.size(), crId, isAutoCR);
            response.getWriter().write(gson.toJson(stakeholders));
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid crId parameter");
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            logger.error("Database error in GET /api/cr-stakeholders", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();
        
        // Handle sync endpoint
        if (pathInfo != null && pathInfo.equals("/sync")) {
            handleSync(request, response);
            return;
        }

        // Default: bad request
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        JsonObject error = new JsonObject();
        error.addProperty("error", "Invalid endpoint");
        response.getWriter().write(gson.toJson(error));
    }

    /**
     * Handle GET /api/cr-stakeholders/community?crId={id}
     * Get stakeholder community for a change request (stakeholders from related objects)
     */
    private void handleGetStakeholderCommunity(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        
        logger.info("handleGetStakeholderCommunity called");
        
        try {
            String crIdParam = request.getParameter("crId");
            logger.info("crId parameter: {}", crIdParam);
            
            if (crIdParam == null || crIdParam.trim().isEmpty()) {
                logger.warn("crId parameter is missing or empty");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "crId parameter is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            int crId = Integer.parseInt(crIdParam);
            
            // Get the change request to find its reference
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(crId);
            
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            String reference = changeRequest.getReference();
            logger.info("Fetching stakeholder community for CR {} with reference: {}", crId, reference);
            
            // Parse the reference to get facet type and ID
            Map<String, Object> parsed = crStakeholderDAO.parseReference(reference);
            if (parsed == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid reference format: " + reference);
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            String facetType = (String) parsed.get("facetType");
            int facetId = (int) parsed.get("facetId");
            
            // Get stakeholder community based on facet type
            List<Map<String, Object>> communityStakeholders = crStakeholderDAO.getStakeholderCommunityForObject(facetType, facetId);
            
            // Format response similar to other stakeholder community endpoints
            Map<String, Object> result = new HashMap<>();
            result.put("crId", crId);
            result.put("reference", reference);
            result.put("stakeholders", communityStakeholders);
            
            response.getWriter().write(gson.toJson(result));
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid crId parameter");
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            logger.error("Database error in GET /api/cr-stakeholders/community", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Handle POST /api/cr-stakeholders/sync
     * Syncs stakeholders from a source object to all its change requests
     */
    private void handleSync(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        try {
            // Get current user ID
            int userId = UserContextUtil.getCurrentUserId(request);
            if (userId <= 0) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                JsonObject error = new JsonObject();
                error.addProperty("error", "User authentication required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // Read request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();

            // Validate required fields
            if (!requestData.has("facetType") || !requestData.has("facetId")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required fields: facetType, facetId");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            String facetType = requestData.get("facetType").getAsString();
            int facetId = requestData.get("facetId").getAsInt();

            logger.info("Syncing stakeholders for {} {} by user {}", facetType, facetId, userId);

            int updatedCount = crStakeholderDAO.syncStakeholdersToChangeRequests(facetType, facetId, userId);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("success", true);
            responseData.put("message", "Stakeholders synced successfully");
            responseData.put("changeRequestsUpdated", updatedCount);

            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("Database error in POST /api/cr-stakeholders/sync", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in POST /api/cr-stakeholders/sync", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
}

