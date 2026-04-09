package com.example.budg_v2;

import com.example.budg_v2.dao.DFCRDao;
import com.example.budg_v2.dao.DFCRTypeSettingsDAO;
import com.example.budg_v2.model.DFCRTypeSetting;
import com.google.gson.*;
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
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Servlet for managing DFCR type-level settings
 * Endpoints:
 * - GET /admin/api/dfcr-type-settings?facet=Glossary - Get all type settings for a facet
 * - GET /admin/api/dfcr-type-settings?facet=Glossary&types=true - Get all available types for a facet
 * - GET /admin/api/dfcr-type-settings?facet=Glossary&workflows=true - Get workflows filtered by facet
 * - POST /admin/api/dfcr-type-settings - Save type settings
 * - DELETE /admin/api/dfcr-type-settings?facet=Glossary - Restore defaults (delete all type settings)
 */
@WebServlet("/admin/api/dfcr-type-settings")
public class DFCRTypeSettingsServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DFCRTypeSettingsServlet.class);
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final DFCRTypeSettingsDAO typeSettingsDAO = new DFCRTypeSettingsDAO();
    private final DFCRDao dfcrDao = new DFCRDao();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String facet = req.getParameter("facet");
        String getTypes = req.getParameter("types");
        String getWorkflows = req.getParameter("workflows");

        if (facet == null || facet.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing facet parameter\"}");
            return;
        }

        try {
            Integer facetId = dfcrDao.getFacetIdByName(facet);
            if (facetId == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Unknown facet: " + facet + "\"}");
                return;
            }

            JsonObject result = new JsonObject();
            result.addProperty("facet", facet);
            result.addProperty("facetId", facetId);

            // Get workflows filtered by facet
            if ("true".equalsIgnoreCase(getWorkflows)) {
                JsonArray workflowsArray = new JsonArray();
                List<Object[]> workflows = typeSettingsDAO.getWorkflowsForFacet(facetId);
                for (Object[] wf : workflows) {
                    JsonObject wfObj = new JsonObject();
                    wfObj.addProperty("id", (Integer) wf[0]);
                    wfObj.addProperty("name", (String) wf[1]);
                    wfObj.addProperty("reference", (String) wf[2]);
                    workflowsArray.add(wfObj);
                }
                result.add("workflows", workflowsArray);
                logger.info("Loaded {} workflows for facet {} (ID={})", workflows.size(), facet, facetId);
                resp.getWriter().write(gson.toJson(result));
                return;
            }

            // Get all available types for a facet
            if ("true".equalsIgnoreCase(getTypes)) {
                List<DFCRTypeSetting> allTypes = typeSettingsDAO.getAllTypesForFacet(facetId, facet);
                JsonArray typesArray = new JsonArray();
                for (DFCRTypeSetting type : allTypes) {
                    JsonObject typeObj = new JsonObject();
                    typeObj.addProperty("typeId", type.getTypeId());
                    typeObj.addProperty("typeName", type.getTypeName());
                    typesArray.add(typeObj);
                }
                result.add("types", typesArray);
                logger.info("Loaded {} types for facet {} (ID={})", allTypes.size(), facet, facetId);
                resp.getWriter().write(gson.toJson(result));
                return;
            }

            // Get saved type settings for a facet
            List<DFCRTypeSetting> savedSettings = typeSettingsDAO.getByFacetId(facetId);
            
            // Also get all available types to merge with saved settings
            List<DFCRTypeSetting> allTypes = typeSettingsDAO.getAllTypesForFacet(facetId, facet);
            
            // Create a map of saved settings by typeId
            Map<Integer, DFCRTypeSetting> savedMap = new HashMap<>();
            for (DFCRTypeSetting setting : savedSettings) {
                savedMap.put(setting.getTypeId(), setting);
            }
            
            // Merge: for each type, use saved settings if available, otherwise show as inherited
            JsonArray settingsArray = new JsonArray();
            for (DFCRTypeSetting type : allTypes) {
                DFCRTypeSetting saved = savedMap.get(type.getTypeId());
                JsonObject settingObj = new JsonObject();
                settingObj.addProperty("typeId", type.getTypeId());
                settingObj.addProperty("typeName", type.getTypeName());
                
                if (saved != null) {
                    // Use saved values (null means inherited)
                    settingObj.addProperty("workflowCreateId", saved.getWorkflowCreateId());
                    settingObj.addProperty("workflowEditId", saved.getWorkflowEditId());
                    settingObj.addProperty("crTypeId", saved.getCrTypeId());
                    settingObj.addProperty("workflowCreateName", saved.getWorkflowCreateName());
                    settingObj.addProperty("workflowEditName", saved.getWorkflowEditName());
                    settingObj.addProperty("crTypeName", saved.getCrTypeName());
                } else {
                    // All inherited (null values)
                    settingObj.add("workflowCreateId", JsonNull.INSTANCE);
                    settingObj.add("workflowEditId", JsonNull.INSTANCE);
                    settingObj.add("crTypeId", JsonNull.INSTANCE);
                }
                
                settingsArray.add(settingObj);
            }
            
            result.add("typeSettings", settingsArray);
            logger.info("Loaded {} type settings for facet {} (ID={})", settingsArray.size(), facet, facetId);
            resp.getWriter().write(gson.toJson(result));

        } catch (SQLException e) {
            logger.error("Database error loading type settings", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try (BufferedReader reader = req.getReader()) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            JsonObject requestData = JsonParser.parseString(jsonBuilder.toString()).getAsJsonObject();
            logger.info("DFCR Type Settings POST payload: {}", requestData);

            if (!requestData.has("facet")) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing facet parameter\"}");
                return;
            }

            String facet = requestData.get("facet").getAsString();
            Integer facetId = dfcrDao.getFacetIdByName(facet);
            
            if (facetId == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Unknown facet: " + facet + "\"}");
                return;
            }

            if (!requestData.has("typeSettings") || !requestData.get("typeSettings").isJsonArray()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Missing or invalid typeSettings array\"}");
                return;
            }

            JsonArray typeSettingsArray = requestData.getAsJsonArray("typeSettings");
            
            for (JsonElement element : typeSettingsArray) {
                JsonObject settingObj = element.getAsJsonObject();
                
                DFCRTypeSetting setting = new DFCRTypeSetting();
                setting.setFacetId(facetId);
                setting.setTypeId(settingObj.get("typeId").getAsInt());
                setting.setTypeName(getStringOrNull(settingObj, "typeName"));
                setting.setWorkflowCreateId(getIntegerOrNull(settingObj, "workflowCreateId"));
                setting.setWorkflowEditId(getIntegerOrNull(settingObj, "workflowEditId"));
                setting.setCrTypeId(getIntegerOrNull(settingObj, "crTypeId"));
                
                // Only save if at least one value is not inherited
                if (!setting.isFullyInherited()) {
                    typeSettingsDAO.upsert(setting);
                    logger.info("Saved type setting: {}", setting);
                } else {
                    // Delete any existing record to restore inherited behavior
                    typeSettingsDAO.delete(facetId, setting.getTypeId());
                    logger.info("Deleted type setting (restored to inherited): facetId={}, typeId={}", 
                            facetId, setting.getTypeId());
                }
            }

            logger.info("Successfully saved {} type settings for facet {}", typeSettingsArray.size(), facet);
            resp.getWriter().write("{\"success\":true,\"message\":\"Type settings saved successfully\"}");

        } catch (SQLException e) {
            logger.error("Database error saving type settings", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            logger.error("Error processing request", e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid request: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        String facet = req.getParameter("facet");

        if (facet == null || facet.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing facet parameter\"}");
            return;
        }

        try {
            Integer facetId = dfcrDao.getFacetIdByName(facet);
            if (facetId == null) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Unknown facet: " + facet + "\"}");
                return;
            }

            // Delete all type settings for this facet (restore to inherited)
            typeSettingsDAO.deleteByFacetId(facetId);
            
            logger.info("Restored defaults for facet {} (ID={})", facet, facetId);
            resp.getWriter().write("{\"success\":true,\"message\":\"Defaults restored successfully\"}");

        } catch (SQLException e) {
            logger.error("Database error restoring defaults", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
        }
    }

    // Helper methods

    private String getStringOrNull(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return null;
    }

    private Integer getIntegerOrNull(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                JsonElement elem = json.get(key);
                if (elem.isJsonPrimitive()) {
                    if (elem.getAsJsonPrimitive().isNumber()) {
                        return elem.getAsInt();
                    } else if (elem.getAsJsonPrimitive().isString()) {
                        String val = elem.getAsString();
                        if (!val.isEmpty() && !"inherited".equalsIgnoreCase(val)) {
                            return Integer.parseInt(val);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}

