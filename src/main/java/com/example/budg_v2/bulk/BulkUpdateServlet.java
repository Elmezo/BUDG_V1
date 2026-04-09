package com.example.budg_v2.bulk;

import com.example.budg_v2.config.BulkUpdateDefinitionConfig;
import com.example.budg_v2.service.BulkUpdateService;
import com.example.budg_v2.service.BulkUpdateService.BulkUpdateResult;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

/**
 * @file BulkUpdateServlet.java
 * @brief REST API Servlet for executing bulk update operations on system
 *        objects.
 * 
 *        ### File Overview
 *        This servlet serves as the entry point for mass-modifying attributes,
 *        roles,
 *        and definitions of objects within various facets. It provides the
 *        necessary
 *        metadata for the bulk update UI and processes the batch modification
 *        requests.
 * 
 *        **Business Capability**: Bulk Data Stewardship and Governance.
 *        **Modules**: Depends on
 *        `com.example.budg_v2.service.BulkUpdateService` for core logic.
 * 
 *        ### Responsibility
 *        - Verifies user credentials and administrative rights for bulk
 *        modifications.
 *        - Provides field definitions and lookup values to dynamically build
 *        update forms.
 *        - Fetches preview data for items selected for update.
 *        - Orchestrates the execution and validation of batch updates.
 * 
 *        ### Typical Flow
 *        1. UI calls `/api/bulk-update/can-access` to verify user permissions.
 *        2. UI fetches field metadata via
 *        `/api/bulk-update/definitions/{facet}`.
 *        3. UI fetches valid values for dropdowns via
 *        `/api/bulk-update/lookup/{facet}/{fieldId}`.
 *        4. User submits a list of `objectIds` and a map of `updates` to the
 *        main endpoint.
 *        5. Servlet delegates validation and persistence to
 *        `BulkUpdateService`.
 *        6. Standardized result (success/failure per item) is returned to the
 *        client.
 */
@WebServlet(urlPatterns = { "/api/bulk-update", "/api/bulk-update/*" })
public class BulkUpdateServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private BulkUpdateService bulkUpdateService;

    /**
     * Initializes the servlet and its dependencies.
     * 
     * @purpose Sets up the BulkUpdateService instance.
     * @side_effects Instantiate the update service component.
     */
    @Override
    public void init() throws ServletException {
        bulkUpdateService = new BulkUpdateService();
    }

    /**
     * Handles CORS preflight requests.
     * 
     * @purpose Configures browser CORS headers for cross-origin update calls.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     * @side_effects Sets CORS headers in the response.
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Dispatches GET requests to specific metadata or data retrieval handlers.
     * 
     * @purpose Routes requests for permissions, definitions, lookups, and item
     *          data.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     * @throws ServletException, IOException If server or I/O errors occur.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // system.out.println("[BulkUpdateServlet] doGet called - pathInfo=" +
        // req.getPathInfo() + ", requestURI=" + req.getRequestURI());

        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // system.out.println("[BulkUpdateServlet] doGet - Missing path (pathInfo=" +
                // pathInfo + ")");
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing path");
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            // Routing logic based on URI sub-paths
            if (pathParts[0].equals("can-access")) {
                handleCanAccess(req, resp);
            } else if (pathParts[0].equals("definitions") && pathParts.length >= 2) {
                handleGetDefinitions(pathParts[1], resp);
            } else if (pathParts[0].equals("lookup") && pathParts.length >= 3) {
                handleGetLookup(pathParts[1], pathParts[2], resp);
            } else if (pathParts[0].equals("items") && pathParts.length >= 2) {
                handleGetItems(req, pathParts[1], resp);
            } else if (pathParts[0].equals("role-items")) {
                handleGetRoleItems(req, resp);
            } else if (pathParts[0].equals("parent-options") && pathParts.length >= 2) {
                handleGetParentOptions(req, pathParts[1], resp);
            } else if (pathParts[0].equals("dataset-reference-options") && pathParts.length >= 3) {
                handleGetDatasetReferenceOptions(req, pathParts[1], pathParts[2], resp);
            } else if (pathParts[0].equals("attribute-segments")) {
                handleGetAttributeSegments(req, resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Dispatches POST requests to the main bulk update execution handler.
     * 
     * @purpose Main entry point for submitting batch modifications.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     * @throws ServletException, IOException If server or I/O errors occur.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        // system.out.println("[BulkUpdateServlet] doPost called - pathInfo=" + pathInfo
        // + ", requestURI=" + req.getRequestURI() + ", method=" + req.getMethod());

        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Main bulk update endpoint (typically /api/bulk-update with no extra path)
        if (pathInfo == null || pathInfo.equals("/") || pathInfo.isEmpty()) {
            // system.out.println("[BulkUpdateServlet] doPost - Handling main bulk update
            // endpoint");
            handleBulkUpdate(req, resp);
            return;
        }

        // Catch-all for undefined POST sub-paths
        // system.out.println("[BulkUpdateServlet] doPost - Path info present: " +
        // pathInfo + ", treating as unknown endpoint");
        sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown POST endpoint: " + pathInfo);
    }

    /**
     * Orchestrates the core bulk update process.
     * 
     * @purpose Processes the incoming batch of updates after validating
     *          permissions and payload.
     * @param req  The HTTP servlet request with JSON body (facet, objectIds,
     *             updates).
     * @param resp The HTTP servlet response returning the update summary.
     * @throws IOException If I/O or JSON parsing fails.
     * @side_effects Modifies database records across multiple tables based on the
     *               facet.
     */
    private void handleBulkUpdate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // Retrieve acting user identity for authorization and auditing
            int userId = getUserIdFromRequest(req);
            // system.out.println("[BulkUpdateServlet] doPost - userId=" + userId);

            if (userId <= 0) {
                // system.out.println("[BulkUpdateServlet] doPost - User not authenticated");
                sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
                return;
            }

            // Bulk update is a privileged operation restricted to administrators
            boolean isAdmin = bulkUpdateService.isUserAdmin(userId);
            // system.out.println("[BulkUpdateServlet] doPost - isAdmin=" + isAdmin);

            if (!isAdmin) {
                // system.out.println("[BulkUpdateServlet] doPost - User is not admin");
                sendError(resp, HttpServletResponse.SC_FORBIDDEN, BulkUpdateService.ERROR_UNAUTHORIZED);
                return;
            }

            // Extract the update instructions from the request body
            String body = readRequestBody(req);
            // system.out.println("[BulkUpdateServlet] doPost - Request body: " + body);

            JsonObject json = gson.fromJson(body, JsonObject.class);

            String facet = json.has("facet") ? json.get("facet").getAsString() : null;
            List<Integer> objectIds = parseIntegerList(json.get("objectIds"));
            Map<String, Object> updates = parseUpdates(json.get("updates"));

            // system.out.println("[BulkUpdateServlet] doPost - Parsed: facet=" + facet + ",
            // objectIds=" + objectIds + ", updates=" + updates);

            // Synchronous validation of the entire batch request
            BulkUpdateResult validationResult = bulkUpdateService.validateRequest(facet, objectIds, updates, userId,
                    isAdmin);
            if (!validationResult.isSuccess()) {
                resp.getWriter().write(JsonUtil.toJson(validationResult.toMap()));
                return;
            }

            // Execute the persistence logic
            // system.out.println("[BulkUpdateServlet] doPost - Executing bulk update...");
            BulkUpdateResult result = bulkUpdateService.executeBulkUpdate(facet, objectIds, updates, userId);
            // system.out.println("[BulkUpdateServlet] doPost - Result: " +
            // JsonUtil.toJson(result.toMap()));
            resp.getWriter().write(JsonUtil.toJson(result.toMap()));

        } catch (Exception e) {
            // system.out.println("[BulkUpdateServlet] handleBulkUpdate - Exception: " +
            // e.getMessage());
            e.printStackTrace();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Checks if the current user has access to bulk update features.
     * 
     * @purpose Permissions pre-check for UI elements visibility.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response returning 'canAccess' boolean.
     * @throws IOException If response writing fails.
     */
    private void handleCanAccess(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int userId = getUserIdFromRequest(req);
        boolean canAccess = false;
        if (userId > 0) {
            canAccess = bulkUpdateService.isUserAdmin(userId);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("canAccess", canAccess);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Retrieves updatable field definitions for a specific facet.
     * 
     * @purpose Metadata discovery for dynamic UI form generation.
     * @param facet The target facet (e.g., 'dataset', 'glossary').
     * @param resp  The HTTP servlet response returning definition list.
     * @throws IOException If lookup or response writing fails.
     */
    private void handleGetDefinitions(String facet, HttpServletResponse resp) throws IOException {
        // Enforce exclusion list for facets that do not support bulk update
        if (BulkUpdateDefinitionConfig.isFacetExcluded(facet)) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, BulkUpdateService.ERROR_EXCLUDED_FACET);
            return;
        }

        List<Map<String, Object>> definitions = bulkUpdateService.getDefinitions(facet);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("facet", facet);
        result.put("definitions", definitions);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Fetches valid lookup values for a specific field.
     * 
     * @purpose Population of dropdown menus in the update form.
     * @param facet   The target facet.
     * @param fieldId The specific field ID within the facet.
     * @param resp    The HTTP servlet response returning value list.
     * @throws IOException If lookup or response writing fails.
     */
    private void handleGetLookup(String facet, String fieldId, HttpServletResponse resp) throws IOException {
        List<Map<String, Object>> values = bulkUpdateService.getLookupValues(facet, fieldId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("values", values);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Retrieves preview information for a list of items.
     * 
     * @purpose Displaying selected objects in the UI before confirmation.
     * @param req   The HTTP servlet request with 'ids' query param.
     * @param facet The target facet.
     * @param resp  The HTTP servlet response returning brief item info.
     * @throws IOException If lookup or response writing fails.
     */
    private void handleGetItems(HttpServletRequest req, String facet, HttpServletResponse resp) throws IOException {
        String idsParam = req.getParameter("ids");
        if (idsParam == null || idsParam.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing ids parameter");
            return;
        }

        List<Integer> objectIds = new ArrayList<>();
        for (String id : idsParam.split(",")) {
            try {
                // Parse IDs manually to allow partial success even if some are malformed
                objectIds.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        List<Map<String, Object>> items = bulkUpdateService.getObjectsInfo(facet, objectIds);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("facet", facet);
        result.put("items", items);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Retrieves role relationship data with extended columns for display.
     * 
     * @purpose Specialized preview for role updates which involve junction tables.
     * @param req  The HTTP servlet request with 'ids' query param (junction IDs).
     * @param resp The HTTP servlet response returning role details.
     * @throws IOException If lookup fails.
     */
    private void handleGetRoleItems(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idsParam = req.getParameter("ids");
        if (idsParam == null || idsParam.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing ids parameter");
            return;
        }

        List<Integer> objectXPeopleIds = new ArrayList<>();
        for (String id : idsParam.split(",")) {
            try {
                objectXPeopleIds.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        List<Map<String, Object>> items = bulkUpdateService.getRoleDataWithExtendedColumns(objectXPeopleIds);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("facet", "role");
        result.put("items", items);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Retrieves filtered parent options based on segment rules.
     * 
     * @purpose Filter parent field options based on selected objects' segments.
     * @param req   The HTTP servlet request with 'objectIds' query param.
     * @param facet The target facet.
     * @param resp  The HTTP servlet response returning filtered parent options.
     * @throws IOException If lookup fails.
     */
    private void handleGetParentOptions(HttpServletRequest req, String facet, HttpServletResponse resp) throws IOException {
        int userId = getUserIdFromRequest(req);
        if (userId <= 0) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
            return;
        }

        String objectIdsParam = req.getParameter("objectIds");
        if (objectIdsParam == null || objectIdsParam.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing objectIds parameter");
            return;
        }

        List<Integer> objectIds = new ArrayList<>();
        for (String id : objectIdsParam.split(",")) {
            try {
                objectIds.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        if (objectIds.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "No valid object IDs provided");
            return;
        }

        List<Map<String, Object>> parentOptions = bulkUpdateService.getFilteredParentOptions(facet, objectIds, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("facet", facet);
        result.put("values", parentOptions);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    private void handleGetDatasetReferenceOptions(HttpServletRequest req, String facet, String fieldId, HttpServletResponse resp) throws IOException {
        int userId = getUserIdFromRequest(req);
        if (userId <= 0) {
            sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
            return;
        }

        String objectIdsParam = req.getParameter("objectIds");
        if (objectIdsParam == null || objectIdsParam.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing objectIds parameter");
            return;
        }

        List<Integer> objectIds = new ArrayList<>();
        for (String id : objectIdsParam.split(",")) {
            try {
                objectIds.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        if (objectIds.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "No valid object IDs provided");
            return;
        }

        List<Map<String, Object>> referenceOptions = bulkUpdateService.getFilteredDatasetReferenceOptions(facet, fieldId, objectIds, userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("facet", facet);
        result.put("fieldId", fieldId);
        result.put("values", referenceOptions);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Extracts the authenticated user's ID from the session or request filters.
     * 
     * @purpose security identity resolution for all endpoints.
     * @param req The HTTP servlet request.
     * @return The user ID, or -1 if not authenticated.
     */
    private int getUserIdFromRequest(HttpServletRequest req) {
        // Attempt to retrieve from session first
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object userIdObj = session.getAttribute("userId");
            if (userIdObj instanceof Integer) {
                return (Integer) userIdObj;
            } else if (userIdObj instanceof String) {
                try {
                    return Integer.parseInt((String) userIdObj);
                } catch (NumberFormatException e) {
                    // Invalid user ID
                }
            }
        }

        // Fallback to request attribute set by authentication filters
        Object userIdAttr = req.getAttribute("userId");
        if (userIdAttr instanceof Integer) {
            return (Integer) userIdAttr;
        }

        return -1;
    }

    /**
     * Reads the entire request body as a string.
     * 
     * @purpose Consumption of JSON payload for updates.
     * @param req The HTTP servlet request.
     * @return String containing the request body.
     * @throws IOException If an input error occurs.
     */
    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /**
     * Converts a JsonElement array into a list of Integers.
     * 
     * @purpose Identification transformation from JSON to Java list.
     * @param element The JsonElement array.
     * @return List of integers.
     */
    private List<Integer> parseIntegerList(JsonElement element) {
        List<Integer> result = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) {
                if (e.isJsonPrimitive()) {
                    result.add(e.getAsInt());
                }
            }
        }
        return result;
    }

    /**
     * Parses the 'updates' map from the request JSON.
     * 
     * @purpose Extraction of field-to-value mappings for the update operation.
     * @param element The JsonElement object.
     * @return Map of field names to their new values (supporting Boolean, Integer,
     *         and String).
     */
    private Map<String, Object> parseUpdates(JsonElement element) {
        Map<String, Object> result = new HashMap<>();
        if (element != null && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String key : obj.keySet()) {
                JsonElement value = obj.get(key);
                // Handle different JSON types and map them to appropriate Java objects
                if (value.isJsonNull()) {
                    result.put(key, null);
                } else if (value.isJsonPrimitive()) {
                    if (value.getAsJsonPrimitive().isBoolean()) {
                        result.put(key, value.getAsBoolean());
                    } else if (value.getAsJsonPrimitive().isNumber()) {
                        result.put(key, value.getAsInt());
                    } else {
                        result.put(key, value.getAsString());
                    }
                }
            }
        }
        return result;
    }

    /**
     * Retrieves segment information for a list of attributes.
     * 
     * @purpose Determine if attributes belong to the same segment for filtering datasets/glossaries.
     * @param req  The HTTP servlet request with 'ids' query param.
     * @param resp The HTTP servlet response returning segment information.
     * @throws IOException If lookup or response writing fails.
     */
    private void handleGetAttributeSegments(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idsParam = req.getParameter("ids");
        if (idsParam == null || idsParam.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing ids parameter");
            return;
        }

        List<Integer> attributeIds = new ArrayList<>();
        for (String id : idsParam.split(",")) {
            try {
                attributeIds.add(Integer.parseInt(id.trim()));
            } catch (NumberFormatException e) {
                // Skip invalid IDs
            }
        }

        if (attributeIds.isEmpty()) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "No valid attribute IDs provided");
            return;
        }

        Map<String, Object> segmentInfo = bulkUpdateService.getAttributeSegmentInfo(attributeIds);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.putAll(segmentInfo);
        resp.getWriter().write(JsonUtil.toJson(result));
    }

    /**
     * Sends a standardized error response in JSON format.
     * 
     * @purpose Consistent error reporting across the bulk update API.
     * @param resp    The HTTP servlet response.
     * @param status  The HTTP status code.
     * @param message The error message.
     * @throws IOException If response writing fails.
     */
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        resp.getWriter().write(JsonUtil.toJson(error));
    }
}
