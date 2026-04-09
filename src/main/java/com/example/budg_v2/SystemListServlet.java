package com.example.budg_v2;

import com.example.budg_v2.dao.SegmentDAO;
import com.example.budg_v2.dao.SystemDAO;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.RequestedSegmentFilterUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * System List Servlet
 * 
 * Returns list of systems filtered by:
 * 1. User's segment access (cube filter) - users only see systems from segments they can access
 * 2. Source segment constraint (segmentId and/or sourceObjectId) - private source: Enterprise or same segment only
 * 
 * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
 * When creating/editing a dataset, systems are further filtered to show only those compatible with the selected dataset segment.
 */
@WebServlet(name = "SystemListServlet", urlPatterns = {"/api/system/list"})
public class SystemListServlet extends HttpServlet {

    private final SystemDAO systemDAO = new SystemDAO();
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
            int userId = UserContextUtil.getCurrentUserId(req);

            SegmentDAO segmentDAO = new SegmentDAO();
            Integer effectiveSegmentId = RequestedSegmentFilterUtil.resolveEffectiveSegmentId(req, segmentDAO);

            if (effectiveSegmentId != null && effectiveSegmentId > 1) {
                var list = userId > 0
                        ? systemDAO.listSystemsBySegmentAccessAndDatasetSegment(userId, effectiveSegmentId)
                        : systemDAO.listSystemsByDatasetSegment(effectiveSegmentId);
                resp.getWriter().write(gson.toJson(list));
                return;
            }

            var list = userId > 0
                ? systemDAO.listSystemsBySegmentAccess(userId)
                : systemDAO.listSystems();
            
            //system.out.println("📋 SystemListServlet: Returning " + list.size() + " systems for user " + userId);
            resp.getWriter().write(gson.toJson(list));
        } catch (SQLException e) {
            System.err.println("❌ SystemListServlet error: " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error: " + e.getMessage().replace("\"", "\\\"") + "\"}");
        } catch (Exception e) {
            System.err.println("❌ SystemListServlet unexpected error: " + e.getMessage());
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Error: " + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
}


