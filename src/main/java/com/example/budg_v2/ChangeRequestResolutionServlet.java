package com.example.budg_v2;

import com.example.budg_v2.dao.ChangeRequestDAO;
import com.example.budg_v2.dao.ChangeRequestResolutionDAO;
import com.example.budg_v2.dao.CRStakeholderDAO;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.model.ChangeRequestResolution;
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

@WebServlet("/api/changerequest-resolution/*")
public class ChangeRequestResolutionServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestResolutionServlet.class);
    private final ChangeRequestResolutionDAO resolutionDAO = new ChangeRequestResolutionDAO();
    
    // ===== Inline thread-safe cache (no extra files needed) =====
    // Reduced TTL to 2 seconds to ensure all devices see latest resolution status in high-scale concurrent access
    private static final long CACHE_TTL_MS = 2_000; // 2 seconds
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
        
        // Add no-cache headers to ensure all devices see latest data in high-scale concurrent access
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        try {
            String changeRequestIdStr = request.getParameter("changeRequestId");
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
            // Cache TTL is reduced to 2 seconds to ensure consistency across multiple devices
            CachedResult cached = cache.get(changeRequestId);
            if (cached != null && cached.isValid()) {
                logger.debug("Cache HIT for resolution CR: {}", changeRequestId);
                response.setHeader("X-Cache", "HIT");
                response.getWriter().write(cached.json);
                return;
            }
            
            List<ChangeRequestResolution> resolutionList = resolutionDAO.getResolutionsByChangeRequestId(changeRequestId);
            String jsonResult = gson.toJson(resolutionList);
            
            // Store in cache
            cache.put(changeRequestId, new CachedResult(jsonResult));
            
            logger.debug("Cache MISS for resolution CR: {} (returned {} entries)", changeRequestId, resolutionList.size());
            response.setHeader("X-Cache", "MISS");
            response.getWriter().write(jsonResult);
            
        } catch (NumberFormatException e) {
            logger.error("Invalid change request ID: {}", request.getParameter("changeRequestId"));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid change request ID");
            response.getWriter().write(gson.toJson(error));
        } catch (SQLException e) {
            // If table doesn't exist, return empty array instead of error
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || 
                e.getMessage().contains("Unknown table") || 
                e.getMessage().contains("Table") && e.getMessage().contains("doesn't exist"))) {
                logger.warn("Resolution table may not exist, returning empty array: {}", e.getMessage());
                response.getWriter().write(gson.toJson(new ArrayList<>()));
            } else {
                logger.error("Database error in GET /api/changerequest-resolution", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Database error: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
            }
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
            
            // Verify change request exists
            ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            // Get resolution data from request
            Integer resolutionStatusId = null;
            if (requestData.has("resolutionStatusId") && !requestData.get("resolutionStatusId").isJsonNull()) {
                resolutionStatusId = requestData.get("resolutionStatusId").getAsInt();
            }
            String description = requestData.has("description") ? requestData.get("description").getAsString() : "";
            
            // Description is optional now (can be empty)

            // Create new resolution record
            ChangeRequestResolution resolution = new ChangeRequestResolution();
            resolution.setChangeRequestId(changeRequestId);
            resolution.setResolutionStatusId(resolutionStatusId);
            resolution.setDescription(description);
            resolution.setLastUserChange(userId);
            LocalDateTime now = LocalDateTime.now();
            resolution.setCreatedAt(now);
            resolution.setUpdatedAt(now);
            
            Integer newResolutionId = resolutionDAO.createResolution(resolution);
            
            if (newResolutionId != null && newResolutionId > 0) {
                // Invalidate cache so next GET fetches fresh data
                invalidateCache(changeRequestId);
                
                JsonObject result = new JsonObject();
                result.addProperty("message", "Resolution created successfully");
                result.addProperty("id", newResolutionId);
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Failed to create resolution");
                response.getWriter().write(gson.toJson(error));
            }
            
        } catch (SQLException e) {
            logger.error("Database error in POST /api/changerequest-resolution: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in POST /api/changerequest-resolution: {}", e.getMessage(), e);
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
            // Get resolution ID from path
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Resolution ID is required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int resolutionId;
            try {
                resolutionId = Integer.parseInt(pathInfo.substring(1));
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid resolution ID");
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

            // Verify resolution exists and get change request ID
            ChangeRequestResolution existingResolution = resolutionDAO.getResolutionById(resolutionId);
            if (existingResolution == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Resolution not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            int changeRequestId = existingResolution.getChangeRequestId();

            // Verify change request exists
            ChangeRequestDAO changeRequestDAO = new ChangeRequestDAO();
            ChangeRequest changeRequest = changeRequestDAO.getChangeRequestById(changeRequestId);
            if (changeRequest == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Change request not found");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            logger.info("User {} updating resolution {} for CR {}", userId, resolutionId, changeRequestId);

            // Parse request body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            
            JsonObject requestData = JsonParser.parseString(sb.toString()).getAsJsonObject();
            Integer resolutionStatusId = null;
            if (requestData.has("resolutionStatusId") && !requestData.get("resolutionStatusId").isJsonNull()) {
                resolutionStatusId = requestData.get("resolutionStatusId").getAsInt();
            }
            String description = requestData.has("description") ? requestData.get("description").getAsString() : "";
            
            // Description is optional now (can be empty)

            // Update resolution record
            ChangeRequestResolution resolution = new ChangeRequestResolution();
            resolution.setId(resolutionId);
            resolution.setResolutionStatusId(resolutionStatusId);
            resolution.setDescription(description);
            resolution.setLastUserChange(userId);
            
            boolean updated = resolutionDAO.updateResolution(resolution);
            
            if (updated) {
                // Invalidate cache so next GET fetches fresh data
                invalidateCache(changeRequestId);
                
                JsonObject result = new JsonObject();
                result.addProperty("message", "Resolution updated successfully");
                response.getWriter().write(gson.toJson(result));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Resolution not found");
                response.getWriter().write(gson.toJson(error));
            }
            
        } catch (SQLException e) {
            logger.error("Database error in PUT /api/changerequest-resolution: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        } catch (Exception e) {
            logger.error("Error in PUT /api/changerequest-resolution: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Internal server error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
}
