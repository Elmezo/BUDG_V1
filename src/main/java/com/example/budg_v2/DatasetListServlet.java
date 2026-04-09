package com.example.budg_v2;

import com.example.budg_v2.dao.DatasetDAO;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Dataset List Servlet
 * 
 * Returns list of datasets filtered by:
 * 1. User's segment access (cube filter)
 * 2. Optional segmentId (e.g. for attribute create/edit):
 *    - Segment Enterprise (1): show ALL datasets from all segments the user can access
 *    - Private segment: show only datasets in that same private segment OR Enterprise
 * 
 * Relationship rule: Enterprise↔Private OK; Private↔Private (different) not allowed.
 */
@WebServlet(name = "DatasetListServlet", urlPatterns = {"/api/dataset/list"})
public class DatasetListServlet extends HttpServlet {

    private final DatasetDAO datasetDAO = new DatasetDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);
        try {
            // Get current user ID for segment filtering
            int userId = UserContextUtil.getCurrentUserId(req);
            
            // Check for optional segmentId parameter (e.g. attribute segment when picking dataset)
            String segmentIdParam = req.getParameter("segmentId");
            Integer segmentId = null;
            if (segmentIdParam != null && !segmentIdParam.trim().isEmpty()) {
                try {
                    segmentId = Integer.parseInt(segmentIdParam.trim());
                } catch (NumberFormatException e) {
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    resp.getWriter().write("{\"error\":\"Invalid segmentId parameter\"}");
                    return;
                }
            }
            
            java.util.List<java.util.Map<String, Object>> list;
            if (segmentId != null) {
                if (segmentId == 1) {
                    // Enterprise: show ALL datasets from all segments the user can access
                    list = userId > 0
                        ? datasetDAO.listDatasetsBySegmentAccess(userId)
                        : datasetDAO.listDatasets();
                } else {
                    // Private segment: filter to datasets in same segment OR Enterprise
                    list = datasetDAO.listDatasetsBySegmentAccessAndAttributeSegment(userId, segmentId);
                }
            } else {
                list = userId > 0
                    ? datasetDAO.listDatasetsBySegmentAccess(userId)
                    : datasetDAO.listDatasets();
            }
            
            resp.getWriter().write(gson.toJson(list));
        } catch (SQLException e) {
            System.err.println("❌ DatasetListServlet error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}

