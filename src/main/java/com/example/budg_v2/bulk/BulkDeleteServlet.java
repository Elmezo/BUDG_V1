package com.example.budg_v2.bulk;

import com.example.budg_v2.service.BulkDeleteService;
import com.example.budg_v2.service.BulkDeleteService.BulkDeleteResult;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.example.budg_v2.util.JwtUtil;
import com.example.budg_v2.util.UserContextUtil;
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
 * @file BulkDeleteServlet.java
 * @brief REST API Servlet for executing bulk delete operations across various
 *        facets.
 * 
 *        ### File Overview
 *        This servlet handles HTTP requests related to bulk deletion of objects
 *        in the system.
 *        It ensures that only authorized administrators can perform these
 *        destructive actions.
 * 
 *        **Business Capability**: Bulk Data Management and Governance.
 *        **Modules**: Depends on
 *        `com.example.budg_v2.service.BulkDeleteService` for business logic.
 * 
 *        ### Responsibility
 *        - Validates user authentication and administrative privileges.
 *        - Parses incoming JSON requests containing facets and object
 *        identifiers.
 *        - Orchestrates pre-validation and execution of bulk delete operations.
 *        - Formats and returns standardized JSON responses to the frontend.
 * 
 *        ### Typical Flow
 *        1. Receive POST request at `/api/bulk-delete`.
 *        2. Authenticate the user and verify admin role via
 *        `getUserIdFromRequest` and `bulkDeleteService.isUserAdmin`.
 *        3. Extract target `facet` and `objectIds` from the request body.
 *        4. Delegate validation and deletion logic to
 *        `BulkDeleteService.executeBulkDelete`.
 *        5. Construct a comprehensive response including success status, count
 *        of deleted rows, and any skipped items.
 *        6. Forward results back to the client.
 */
@WebServlet(urlPatterns = { "/api/bulk-delete" })
public class BulkDeleteServlet extends HttpServlet {

    private final Gson gson = new Gson();
    private BulkDeleteService bulkDeleteService;

    /**
     * Initializes the servlet and its dependencies.
     * 
     * @purpose Sets up the BulkDeleteService instance.
     * @side_effects Instantiate the service layer component.
     */
    @Override
    public void init() throws ServletException {
        bulkDeleteService = new BulkDeleteService();
    }

    /**
     * Handles CORS preflight requests.
     * 
     * @purpose Configures browser CORS headers for cross-origin bulk delete calls.
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
     * Handles GET requests for pre-validation of bulk deletion.
     * 
     * @purpose Provides a way to check if objects can be deleted before actually
     *          executing the operation.
     * @param req  The HTTP servlet request (expects 'facet' and 'objectIds' query
     *             params).
     * @param resp The HTTP servlet response returning validation details.
     * @throws ServletException, IOException If server or I/O errors occur.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            // Get user from session
            int userId = getUserIdFromRequest(req);
            if (userId <= 0) {
                sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
                return;
            }

            // Admin/Super Admin: full bulk delete access. Web User: may only set status to Deleted (per-object permission checked in service).
            boolean isAdmin = bulkDeleteService.isUserAdmin(userId);
            boolean isSuperAdmin = UserContextUtil.isCurrentUserSuperAdmin(req);

            // Parse query parameters for pre-validation
            String facet = req.getParameter("facet");
            String objectIdsParam = req.getParameter("objectIds");

            if (facet == null || objectIdsParam == null) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing facet or objectIds parameter");
                return;
            }

            // Parse object IDs from comma-separated string
            List<Integer> objectIds = new ArrayList<>();
            String[] ids = objectIdsParam.split(",");
            for (String id : ids) {
                try {
                    objectIds.add(Integer.parseInt(id.trim()));
                } catch (NumberFormatException e) {
                    // Skip invalid IDs rather than failing the whole request to allow partial
                    // processing
                }
            }

            if (objectIds.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "No valid object IDs provided");
                return;
            }

            // Pre-validate - check for dependencies or locking constraints
            Map<String, Object> validationResult = bulkDeleteService.preValidateBulkDelete(facet, objectIds);
            validationResult.put("isSuperAdmin", isSuperAdmin);
            validationResult.put("isWebUser", !isAdmin);
            resp.getWriter().write(JsonUtil.toJson(validationResult));

        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Handles POST requests to execute bulk deletion.
     * 
     * @purpose Executes the bulk deletion of specified objects after validating
     *          permissions and optional confirmations.
     * @param req  The HTTP servlet request containing JSON payload with facet,
     *             objectIds, and optional confirmations.
     * @param resp The HTTP servlet response returning the outcome of each deletion
     *             attempt.
     * @throws ServletException, IOException If server or I/O errors occur.
     * @side_effects Modifies system state by deleting records from the database.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            // Get user from session
            int userId = getUserIdFromRequest(req);
            if (userId <= 0) {
                sendError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not authenticated");
                return;
            }

            // Only Super Admin can delete objects. Regular Admins cannot delete.
            boolean isSuperAdmin = UserContextUtil.isCurrentUserSuperAdmin(req);
            if (!isSuperAdmin) {
                sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Only Super Administrators can delete objects");
                return;
            }

            // Super Admin: full bulk delete
            boolean isAdmin = bulkDeleteService.isUserAdmin(userId);
            boolean isWebUser = !isAdmin;

            // Parse request body
            String body = readRequestBody(req);
            JsonObject json = gson.fromJson(body, JsonObject.class);

            String facet = json.has("facet") ? json.get("facet").getAsString() : null;
            List<Integer> objectIds = parseIntegerList(json.get("objectIds"));

            // Primary validation check before processing any deletions (facet, objectIds; no longer requires admin for Web User)
            BulkDeleteResult validationResult = bulkDeleteService.validateRequest(facet, objectIds, userId);
            if (!validationResult.isSuccess()) {
                resp.getWriter().write(JsonUtil.toJson(validationResult.toMap()));
                return;
            }

            // Parse confirmations if provided - used for bypassing non-blocking warnings
            Map<Integer, Map<BulkDeleteService.WarningType, Boolean>> confirmations = null;
            if (json.has("confirmations")) {
                confirmations = parseConfirmations(json.get("confirmations"));
            }

            // Execute bulk delete: SuperAdmin = final soft delete; Admin/WebUser = set status to Deleted only (Web User: per-object permission checked)
            BulkDeleteResult result = bulkDeleteService.executeBulkDelete(facet, objectIds, userId, isSuperAdmin, isWebUser, confirmations);

            // Return results in the format expected by frontend: array of {id, deleted,
            // error}
            JsonArray resultsArray = new JsonArray();
            if (result.getItemResults() != null) {
                for (BulkDeleteService.ItemResult itemResult : result.getItemResults()) {
                    JsonObject itemJson = new JsonObject();
                    itemJson.addProperty("id", itemResult.getId());
                    itemJson.addProperty("deleted", itemResult.isDeleted());
                    if (itemResult.getError() != null) {
                        itemJson.addProperty("error", itemResult.getError());
                    }
                    resultsArray.add(itemJson);
                }
            }

            // Include summary in response for aggregated UI display (counts)
            JsonObject response = new JsonObject();
            response.addProperty("success", result.isSuccess());
            response.addProperty("totalRows", result.getTotalRows());
            response.addProperty("deletedRows", result.getDeletedRows());
            response.addProperty("skippedRows", result.getSkippedRows());
            if (result.getMessage() != null) {
                response.addProperty("message", result.getMessage());
            }
            if (result.getError() != null) {
                response.addProperty("error", result.getError());
            }
            response.add("results", resultsArray);

            resp.getWriter().write(gson.toJson(response));

        } catch (Exception e) {
            e.printStackTrace();
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Extracts the authenticated user's ID from the request context.
     * 
     * @purpose Identification of the acting user for permission checks and audit
     *          logs.
     * @param req The HTTP servlet request.
     * @return The integer user ID, or -1 if the user is not authenticated.
     * @side_effects None.
     * @implNote Checks request attributes (set by AuthFilter), ACCESS_TOKEN cookie,
     *           and HTTPSession in sequential order.
     */
    private int getUserIdFromRequest(HttpServletRequest req) {
        // First, try to get from request attributes (set by AuthFilter)
        Object userIdAttr = req.getAttribute("userId");
        if (userIdAttr != null) {
            if (userIdAttr instanceof Integer) {
                return (Integer) userIdAttr;
            } else if (userIdAttr instanceof Number) {
                return ((Number) userIdAttr).intValue();
            } else if (userIdAttr instanceof String) {
                try {
                    return Integer.parseInt((String) userIdAttr);
                } catch (NumberFormatException e) {
                    // Invalid user ID
                }
            }
        }

        // Fallback: parse ACCESS_TOKEN cookie directly if filter didn't set attributes
        // This is a safety measure for direct API calls bypassing some filters
        try {
            jakarta.servlet.http.Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (jakarta.servlet.http.Cookie cookie : cookies) {
                    if ("ACCESS_TOKEN".equals(cookie.getName())) {
                        String token = cookie.getValue();
                        if (token != null) {
                            Integer userId = JwtUtil.getUserIdFromToken(token);
                            if (userId != null && userId > 0) {
                                return userId;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired, continue to check session
        }

        // Last fallback: try to get from session
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

        return -1;
    }

    /**
     * Reads the entire request body as a string.
     * 
     * @purpose Consumption of JSON payload from the request stream.
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
     * @purpose Transformation of JSON list data into Java collection.
     * @param element The JsonElement (expected to be an array).
     * @return List of integers extracted from the array.
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
     * Parses the 'confirmations' object from the request JSON.
     * 
     * @purpose Extraction of user acknowledgment for specific warnings (e.g.,
     *          deleting items with active CRs).
     * @param element The JsonElement containing confirmation mappings.
     * @return A map where key is objectId, and value is a map of WarningType to
     *         Boolean confirmation.
     */
    private Map<Integer, Map<BulkDeleteService.WarningType, Boolean>> parseConfirmations(JsonElement element) {
        Map<Integer, Map<BulkDeleteService.WarningType, Boolean>> confirmations = new HashMap<>();

        if (element != null && element.isJsonObject()) {
            JsonObject confObj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : confObj.entrySet()) {
                try {
                    int objectId = Integer.parseInt(entry.getKey());
                    JsonElement value = entry.getValue();

                    if (value.isJsonObject()) {
                        JsonObject objConf = value.getAsJsonObject();
                        Map<BulkDeleteService.WarningType, Boolean> warningConfirmations = new HashMap<>();

                        for (Map.Entry<String, JsonElement> warningEntry : objConf.entrySet()) {
                            try {
                                BulkDeleteService.WarningType type = BulkDeleteService.WarningType.valueOf(
                                        warningEntry.getKey().toUpperCase());
                                boolean confirmed = warningEntry.getValue().getAsBoolean();
                                warningConfirmations.put(type, confirmed);
                            } catch (IllegalArgumentException e) {
                                // Invalid warning type - skip to maintain robustness against future API
                                // changes
                            }
                        }

                        confirmations.put(objectId, warningConfirmations);
                    }
                } catch (NumberFormatException e) {
                    // Invalid object ID - skip
                }
            }
        }

        return confirmations;
    }

    /**
     * Sends a standardized error response in JSON format.
     * 
     * @purpose Consistent error reporting across all endpoints.
     * @param resp    The HTTP servlet response.
     * @param status  The HTTP status code.
     * @param message The error message to be returned in the JSON body.
     * @throws IOException If an output error occurs.
     */
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        resp.getWriter().write(JsonUtil.toJson(error));
    }
}
