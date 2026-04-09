package com.example.budg_v2.bulk;

import com.example.budg_v2.service.DatasetMigrationService;
import com.example.budg_v2.service.FacetClassificationService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.example.unisonsearch.service.ConfigurationService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @file BulkMigrateServlet.java
 * @brief REST API Servlet for executing bulk migration/export operations.
 * 
 *        ### File Overview
 *        This servlet handles HTTP requests related to exporting system objects
 *        and their
 *        associated metadata, relationships, and roles into a BUDG-compatible
 *        ZIP format.
 *        It provides endpoints for metadata discovery and the actual extraction
 *        process.
 * 
 *        **Business Capability**: Data Portability, Migration, and Backup.
 *        **Modules**:
 *        - `com.example.budg_v2.service.DatasetMigrationService` (Core
 *        extraction logic)
 *        - `com.example.budg_v2.service.FacetClassificationService` (Facet
 *        validation)
 *        - `com.example.unisonsearch.service.ConfigurationService` (Feature
 *        flags)
 * 
 *        ### Responsibility
 *        - Enforces feature-flag based access control for migration tools.
 *        - Validates administrative permissions for sensitive data export.
 *        - Discovery of exportable facets and cross-facet relationships.
 *        - Orchestration of ZIP file generation and streaming to the client.
 * 
 *        ### Typical Flow
 *        1. Client requests available facets via
 *        `/api/bulk-migrate/available-facets`.
 *        2. Client selects objects and facets, then calls
 *        `/api/bulk-migrate/selected`.
 *        3. Servlet verifies `isDataMigrationEnabled` and user's admin status.
 *        4. Logic delegates to `DatasetMigrationService.generateMigrationZip`.
 *        All-mode JSON may include `"exportScope":"full_tenant"` to export every allowed verified
 *        facet with full-table IDs; omit the field or use `"ecosystem"` for the default
 *        source-centric graph (DatasetMigrationService FACET_RELATIONSHIPS).
 *        5. Resulting binary data is streamed as `application/zip` with an
 *        attachment disposition.
 */
@WebServlet(urlPatterns = { "/api/bulk-migrate", "/api/bulk-migrate/*" })
public class BulkMigrateServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkMigrateServlet.class);
    private final Gson gson = new Gson();
    private DatasetMigrationService migrationService;
    private final ConfigurationService configurationService = new ConfigurationService();

    /**
     * Initializes the servlet and its dependencies.
     * 
     * @purpose Sets up the DatasetMigrationService instance.
     * @side_effects Instantiate the migration logic component.
     */
    @Override
    public void init() throws ServletException {
        migrationService = new DatasetMigrationService();
    }

    /**
     * Handles CORS preflight requests.
     * 
     * @purpose Configures browser CORS headers for cross-origin migration calls.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     * @side_effects Sets CORS headers in the response.
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.setCorsHeaders(req, resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    /**
     * Dispatches GET requests to specific metadata discovery handlers.
     * 
     * @purpose Routes requests for available facets or relationships.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     * @throws ServletException, IOException If server or I/O errors occur.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // system.out.println("[BulkMigrateServlet] DEBUG: doGet called - pathInfo=" +
        // req.getPathInfo());
        logger.info("[BulkMigrateServlet] doGet called - pathInfo={}, requestURI={}", req.getPathInfo(),
                req.getRequestURI());

        CorsUtil.setCorsHeaders(req, resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Check if the data migration feature is globally enabled in system settings
        if (!configurationService.isDataMigrationEnabled()) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN,
                    "Data Migration is not enabled. Please enable it in Admin Panel > System Settings > Environment.");
            return;
        }

        // Migration/Export is a highly privileged administrative task
        if (!UserContextUtil.isCurrentUserAdmin(req)) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Admin or SuperAdmin access required");
            return;
        }

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing path");
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            // Route based on sub-resource path
            if (pathParts[0].equals("available-facets")) {
                handleGetAvailableFacets(req, resp);
            } else if (pathParts[0].equals("available-relationships")) {
                handleGetAvailableRelationships(req, resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint");
            }
        } catch (Exception e) {
            logger.error("Error in doGet", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Dispatches POST requests to specific migration execution handlers.
     * 
     * @purpose Routes requests for 'selected' or 'all' object migration.
     * @param req  The HTTP servlet request.
     * @param resp The HTTP servlet response.
     * @throws ServletException, IOException If server or I/O errors occur.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // system.out.println("[BulkMigrateServlet] DEBUG: doPost called - pathInfo=" +
        // req.getPathInfo());
        logger.info("[BulkMigrateServlet] doPost called - pathInfo={}, requestURI={}", req.getPathInfo(),
                req.getRequestURI());

        CorsUtil.setCorsHeaders(req, resp);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        // Global safeguard for migration features
        if (!configurationService.isDataMigrationEnabled()) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN,
                    "Data Migration is not enabled. Please enable it in Admin Panel > System Settings > Environment.");
            return;
        }

        // Verification of administrative authority
        if (!UserContextUtil.isCurrentUserAdmin(req)) {
            sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Admin or SuperAdmin access required");
            return;
        }

        String pathInfo = req.getPathInfo();

        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing path");
                return;
            }

            String[] pathParts = pathInfo.substring(1).split("/");

            // Route execution based on scope (selected vs all)
            if (pathParts[0].equals("selected")) {
                handleMigrateSelected(req, resp);
            } else if (pathParts[0].equals("all")) {
                handleMigrateAll(req, resp);
            } else if (pathParts[0].equals("env")) {
                handleMigrateEnv(req, resp);
            } else {
                sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Unknown endpoint: " + pathInfo);
            }
        } catch (Exception e) {
            logger.error("Error in doPost", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Executes migration of a specific set of objects.
     * 
     * @purpose Generates a ZIP file containing data for selected IDs, facets, and
     *          relationships.
     * @param req  The HTTP servlet request with JSON body (facet, objectIds,
     *             selectedFacets, selectedRelationships).
     * @param resp The HTTP servlet response containing binary ZIP data.
     * @throws IOException If I/O or ZIP generation fails.
     * @side_effects Reads large amounts of data from DB; consumes server memory
     *               temporarily for ZIP buffer.
     */
    private void handleMigrateSelected(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = readRequestBody(req);
            // system.out.println("[BulkMigrateServlet] DEBUG: handleMigrateSelected -
            // body=" + body);
            logger.info("[BulkMigrateServlet] Selected migration request body: {}", body);

            JsonObject json = gson.fromJson(body, JsonObject.class);

            String facet = json.has("facet") ? json.get("facet").getAsString() : null;
            List<Integer> objectIds = parseIntegerList(json.get("objectIds"));
            Set<String> selectedFacets = parseStringSet(json.get("selectedFacets"));
            Set<String> selectedRelationships = parseStringSet(json.get("selectedRelationships"));

            if (facet == null || facet.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing 'facet' field");
                return;
            }

            // Important: certain facets (People, Workflows) are strictly excluded from
            // bulk migration to comply with BUDG security/schema standards.
            if (!FacetClassificationService.isFacetAllowed(facet)) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Bulk migration not allowed for facet: " + facet
                                + ". Forbidden facets: People, Change Request, Workflow, Tasks.");
                return;
            }

            if (objectIds == null || objectIds.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing or empty 'objectIds' field");
                return;
            }

            // Validate each objectId is a positive integer
            for (Integer id : objectIds) {
                if (id == null || id <= 0) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid objectId value: " + id + ". All object IDs must be positive integers.");
                    return;
                }
            }

            // Validate that at least one facet is selected (source facet is required)
            if (selectedFacets == null || selectedFacets.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "At least one facet must be selected for migration");
                return;
            }

            // Validate that source facet is included in selectedFacets
            if (!selectedFacets.contains(facet)) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Source facet '" + facet + "' must be included in the selected facets");
                return;
            }

            // Validate each selected facet is allowed (no forbidden facets)
            for (String selectedFacet : selectedFacets) {
                if (!FacetClassificationService.isFacetAllowed(selectedFacet)) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Bulk migration not allowed for selected facet: " + selectedFacet
                                    + ". Forbidden facets: People, Change Request, Workflow, Tasks.");
                    return;
                }
            }

            logger.info(
                    "[BulkMigrateServlet] Processing selected migration - facet: {}, objectIds: {}, selectedFacets: {}, selectedRelationships: {}",
                    facet, objectIds, selectedFacets, selectedRelationships);

            // Trigger the heavy lifting logic in DatasetMigrationService
            byte[] zipData = migrationService.generateMigrationZip(facet, objectIds, selectedFacets,
                    selectedRelationships, false);

            if (zipData == null) {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to generate migration ZIP file");
                return;
            }

            // Stream binary data back to the client as an attachment
            String zipFileName = "UnisonSearch_" + facet + ".zip";
            resp.setContentType("application/zip");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
            resp.setContentLength(zipData.length);
            resp.getOutputStream().write(zipData);
            resp.getOutputStream().flush();

            logger.info(
                    "[BulkMigrateServlet] Selected migration completed successfully - facet: {}, objectIds count: {}",
                    facet, objectIds.size());

        } catch (Exception e) {
            logger.error("Error in handleMigrateSelected", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error processing migration: " + e.getMessage());
        }
    }

    /**
     * Executes migration of ALL objects within a facet.
     * 
     * @purpose Generates a full export ZIP for an entire facet ecosystem.
     * @param req  The HTTP servlet request with JSON body (facet).
     * @param resp The HTTP servlet response containing binary ZIP data.
     * @throws IOException If I/O or ZIP generation fails.
     */
    private void handleMigrateAll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = readRequestBody(req);
            // system.out.println("[BulkMigrateServlet] DEBUG: handleMigrateAll - body=" +
            // body);
            logger.info("[BulkMigrateServlet] All migration request body: {}", body);

            JsonObject json = gson.fromJson(body, JsonObject.class);

            String facet = json.has("facet") ? json.get("facet").getAsString() : null;
            List<Integer> objectIds = parseIntegerList(json.get("objectIds")); // Can be empty to signify "All"

            if (facet == null || facet.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing 'facet' field");
                return;
            }

            // Security check for allowed facets
            if (!FacetClassificationService.isFacetAllowed(facet)) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Bulk migration not allowed for facet: " + facet
                                + ". Forbidden facets: People, Change Request, Workflow, Tasks.");
                return;
            }

            // Validate any provided objectIds are positive integers (optional in all-mode
            // but must be valid if present)
            for (Integer id : objectIds) {
                if (id == null || id <= 0) {
                    sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                            "Invalid objectId value: " + id + ". All object IDs must be positive integers.");
                    return;
                }
            }

            boolean fullTenantExport = false;
            if (json.has("exportScope")) {
                String scope = json.get("exportScope").getAsString();
                if ("full_tenant".equalsIgnoreCase(scope)) {
                    fullTenantExport = true;
                }
            }

            logger.info("[BulkMigrateServlet] Processing all migration - facet: {}, objectIds: {}, exportScope: {}",
                    facet, objectIds, fullTenantExport ? "full_tenant" : "ecosystem");

            // Request full export (ecosystem or entire allowed facet set when full_tenant)
            byte[] zipData = migrationService.generateMigrationZip(facet, objectIds, null, null, true,
                    fullTenantExport);

            if (zipData == null) {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to generate migration ZIP file");
                return;
            }

            String zipFileName = "UnisonSearch_" + facet + ".zip";
            resp.setContentType("application/zip");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");
            resp.setContentLength(zipData.length);
            resp.getOutputStream().write(zipData);
            resp.getOutputStream().flush();

            logger.info("[BulkMigrateServlet] All migration completed successfully - facet: {}", facet);

        } catch (Exception e) {
            logger.error("Error in handleMigrateAll", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error processing migration: " + e.getMessage());
        }
    }

    /**
     * Full environment export (stable Excel names + metadata.json) as env_export.zip.
     */
    private void handleMigrateEnv(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String body = readRequestBody(req);
            logger.info("[BulkMigrateServlet] ENV migration request body: {}", body);

            JsonObject json = gson.fromJson(body, JsonObject.class);
            String facet = json.has("facet") ? json.get("facet").getAsString() : null;
            if (facet == null || facet.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing 'facet' field");
                return;
            }
            if (!FacetClassificationService.isFacetAllowed(facet)) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Bulk migration not allowed for facet: " + facet);
                return;
            }

            String hostHint = req.getServerName();
            byte[] zipData = migrationService.generateEnvironmentExportZip(facet, hostHint);

            if (zipData == null) {
                sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to generate ENV ZIP");
                return;
            }

            resp.setContentType("application/zip");
            resp.setHeader("Content-Disposition", "attachment; filename=\"env_export.zip\"");
            resp.setContentLength(zipData.length);
            resp.getOutputStream().write(zipData);
            resp.getOutputStream().flush();

            logger.info("[BulkMigrateServlet] ENV export completed for facet: {}", facet);
        } catch (IllegalArgumentException e) {
            sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            logger.error("Error in handleMigrateEnv", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error processing ENV export: " + e.getMessage());
        }
    }

    /**
     * Fetches metadata about facets related to a source facet.
     * 
     * @purpose Categorizes facets into 'dependent' (mandatory for export) and
     *          'related' (optional).
     * @param req  The HTTP servlet request with 'sourceFacet' param.
     * @param resp The HTTP servlet response containing JSON categorization.
     * @throws IOException If lookup or response writing fails.
     */
    private void handleGetAvailableFacets(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String sourceFacet = req.getParameter("sourceFacet");
            if (sourceFacet == null || sourceFacet.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing sourceFacet parameter");
                return;
            }

            // Strict enforcement of allowed migration sources
            if (!FacetClassificationService.isFacetAllowed(sourceFacet)) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "Bulk migration not allowed for facet: " + sourceFacet);
                return;
            }

            Set<String> availableFacets = migrationService.getAvailableFacets(sourceFacet);
            List<String> mandatoryFacets = migrationService.getMandatoryFacets(sourceFacet);

            // Split into UI-friendly groups: Dependent (Must have) vs Related (Optional)
            List<String> dependentFacets = new ArrayList<>(mandatoryFacets);
            List<String> relatedFacets = new ArrayList<>();
            for (String facet : availableFacets) {
                if (!mandatoryFacets.contains(facet)) {
                    relatedFacets.add(facet);
                }
            }

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("sourceFacet", sourceFacet);

            // Standard list of all facets for backward compatibility
            JsonArray facetsArray = new JsonArray();
            for (String facet : availableFacets) {
                facetsArray.add(facet);
            }
            result.add("facets", facetsArray);

            // Mandatory facets list (e.g., Parent objects in a hierarchy)
            JsonArray dependentArray = new JsonArray();
            for (String facet : dependentFacets) {
                dependentArray.add(facet);
            }
            result.add("dependent", dependentArray);

            // Optional facets list (e.g., Neighbors/Peers in a graph)
            JsonArray relatedArray = new JsonArray();
            for (String facet : relatedFacets) {
                relatedArray.add(facet);
            }
            result.add("related", relatedArray);

            // Legacy support mapping
            result.add("mandatoryFacets", dependentArray);

            resp.getWriter().write(gson.toJson(result));

        } catch (Exception e) {
            logger.error("Error in handleGetAvailableFacets", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Identifies valid relationship types between a group of facets.
     * 
     * @purpose Discovery of link types (e.g., Parent-Child, Source-Target) for
     *          export tuning.
     * @param req  The HTTP servlet request with 'facets' comma-separated param.
     * @param resp The HTTP servlet response returning available relationship names.
     * @throws IOException If lookup fails.
     */
    private void handleGetAvailableRelationships(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            String facetsParam = req.getParameter("facets");
            if (facetsParam == null || facetsParam.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Missing facets parameter");
                return;
            }

            Set<String> facets = new HashSet<>();
            for (String facet : facetsParam.split(",")) {
                String trimmed = facet.trim();
                if (!trimmed.isEmpty()) {
                    // Validate each facet is allowed before querying relationships
                    if (!FacetClassificationService.isFacetAllowed(trimmed)) {
                        sendError(resp, HttpServletResponse.SC_BAD_REQUEST,
                                "Bulk migration not allowed for facet: " + trimmed
                                        + ". Forbidden facets: People, Change Request, Workflow, Tasks.");
                        return;
                    }
                    facets.add(trimmed);
                }
            }

            if (facets.isEmpty()) {
                sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "No valid facets provided");
                return;
            }

            Set<String> relationships = migrationService.getAvailableRelationships(facets);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            JsonArray relArray = new JsonArray();
            for (String rel : relationships) {
                relArray.add(rel);
            }
            result.add("relationships", relArray);

            resp.getWriter().write(gson.toJson(result));

        } catch (Exception e) {
            logger.error("Error in handleGetAvailableRelationships", e);
            sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Reads the entire request body as a string.
     * 
     * @purpose Consumption of JSON metadata from the request stream.
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
     * @purpose Data mapping from JSON transport to Java collection for IDs.
     * @param element The JsonElement (expected to be an array).
     * @return List of integers extracted from the array.
     */
    private List<Integer> parseIntegerList(JsonElement element) {
        List<Integer> result = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
                    result.add(e.getAsInt());
                }
            }
        }
        return result;
    }

    /**
     * Converts a JsonElement array into a set of Strings.
     * 
     * @purpose Data mapping from JSON transport to Java collection for facet/rel
     *          names.
     * @param element The JsonElement (expected to be an array).
     * @return Set of unique strings extracted from the array.
     */
    private Set<String> parseStringSet(JsonElement element) {
        Set<String> result = new HashSet<>();
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement e : array) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
                    result.add(e.getAsString());
                }
            }
        }
        return result;
    }

    /**
     * Sends a standardized error response in JSON format.
     * 
     * @purpose Consistent error reporting for the migration API.
     * @param resp    The HTTP servlet response.
     * @param status  The HTTP status code.
     * @param message The error message to be returned in the JSON body.
     * @throws IOException If an output error occurs.
     */
    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        resp.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("success", false);
        error.addProperty("error", message);
        resp.getWriter().write(gson.toJson(error));
    }
}
