package com.example.budg_v2;

import com.example.budg_v2.dao.SystemDataDAO;
import com.example.budg_v2.dao.FacetChangesDAO;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Isolated servlet for System Data Tab
 * Handles datasets and attributes endpoints
 */
@WebServlet(name = "SystemDataServlet", urlPatterns = {"/api/system-data/*"})
public class SystemDataServlet extends HttpServlet {

    private static final int SYSTEM_FACET_ID = 13;

    private final SystemDataDAO systemDataDAO = new SystemDataDAO();
    private final FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        //system.out.println("SystemDataServlet: pathInfo = " + pathInfo);
        
        if (pathInfo == null || "/".equals(pathInfo)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid path. Use /api/system-data/{systemId}/datasets or /api/system-data/{systemId}/attributes\"}");
            return;
        }

        // Parse path: /{systemId}/datasets or /{systemId}/attributes
        // Remove leading slash and split
        String cleanPath = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] pathParts = cleanPath.split("/");
        //system.out.println("SystemDataServlet: pathParts = " + java.util.Arrays.toString(pathParts));
        
        if (pathParts.length < 2) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid path format. Use /api/system-data/{systemId}/datasets or /api/system-data/{systemId}/attributes\"}");
            return;
        }

        try {
            int originalSystemId = Integer.parseInt(pathParts[0]);
            String resource = pathParts.length > 1 ? pathParts[1].toLowerCase() : "";

            // view=changes support (use cloned system id when CR active)
            String view = req.getParameter("view");
            int systemIdToLoad = originalSystemId;
            if ("changes".equals(view)) {
                try {
                    // Only check for automatic CRs for pending changes
                    Integer activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, originalSystemId);
                    if (activeCrId != null) {
                        Integer nobjectId = facetChangesDAO.getNObjectId("system", originalSystemId, "summary", activeCrId);
                        if (nobjectId != null) systemIdToLoad = nobjectId;
                    }
                } catch (Exception e) {
                    // Fall back to original if mapping fails
                    System.err.println("SystemDataServlet: Error resolving nobject_id for view=changes: " + e.getMessage());
                }
            }

            //system.out.println("SystemDataServlet: systemId = " + systemIdToLoad + ", resource = " + resource + ", view=" + view);

            if ("datasets".equals(resource)) {
                var datasets = systemDataDAO.getDatasetsBySystemId(systemIdToLoad);
                
                // Filter out pending datasets from "View Original" mode
                // and mark them in "View Changes" mode
                Integer activeCrId = null;
                try {
                    activeCrId = facetChangesDAO.getActiveAutomaticChangeRequestId(SYSTEM_FACET_ID, originalSystemId);
                } catch (SQLException ignored) {}
                
                if (activeCrId != null) {
                    // Get all pending dataset IDs from the mapping
                    String areaKey = "relationships#dataset_mastersource";
                    java.util.List<Integer> pendingIds = facetChangesDAO.getAllNObjectIds("system", originalSystemId, areaKey, activeCrId);
                    java.util.Set<Integer> pendingIdSet = new java.util.HashSet<>(pendingIds);
                    
                    if ("changes".equals(view)) {
                        // Mark pending datasets
                        for (var dataset : datasets) {
                            Object idObj = dataset.get("id");
                            if (idObj != null) {
                                int datasetId = ((Number) idObj).intValue();
                                if (pendingIdSet.contains(datasetId)) {
                                    dataset.put("pending", true);
                                }
                            }
                        }
                    } else {
                        // Filter out pending datasets for View Original
                        java.util.List<java.util.Map<String, Object>> originalDatasets = new java.util.ArrayList<>();
                        for (var dataset : datasets) {
                            Object idObj = dataset.get("id");
                            if (idObj != null) {
                                int datasetId = ((Number) idObj).intValue();
                                if (!pendingIdSet.contains(datasetId)) {
                                    originalDatasets.add(dataset);
                                }
                            } else {
                                originalDatasets.add(dataset);
                            }
                        }
                        datasets = originalDatasets;
                    }
                }
                
                resp.getWriter().write(gson.toJson(datasets));
            } else if ("attributes".equals(resource)) {
                //system.out.println("SystemDataServlet: Fetching attributes for systemId=" + systemIdToLoad);
                var attributes = systemDataDAO.getAttributesBySystemId(systemIdToLoad);
                //system.out.println("SystemDataServlet: Retrieved " + (attributes != null ? attributes.size() : 0) + " attributes");
                resp.getWriter().write(gson.toJson(attributes));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid resource. Use 'datasets' or 'attributes'\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid system id\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Server error: " + e.getMessage() + "\"}");
            e.printStackTrace();
        }
    }
}

