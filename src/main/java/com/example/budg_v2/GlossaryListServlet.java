package com.example.budg_v2;

import com.example.budg_v2.dao.GlossaryDAO;
import com.example.budg_v2.dao.SegmentDAO;
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
 * Glossary List Servlet
 * 
 * Returns list of glossaries filtered by:
 * 1. User's segment access (cube filter) - users only see glossaries from segments they can access
 * 2. Dataset segment constraint (if segmentId parameter provided) - shows Enterprise glossaries OR glossaries in same segment as dataset
 * 
 * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
 * When creating/editing a dataset, glossaries are further filtered to show only those compatible with the selected dataset segment.
 */
@WebServlet(name = "GlossaryListServlet", urlPatterns = {"/api/glossary/list"})
public class GlossaryListServlet extends HttpServlet {

    private final GlossaryDAO glossaryDAO = new GlossaryDAO();
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
                        ? glossaryDAO.listGlossaryBySegmentAccessAndDatasetSegment(userId, effectiveSegmentId)
                        : glossaryDAO.listGlossaryByDatasetSegment(effectiveSegmentId);
                resp.getWriter().write(gson.toJson(list));
                return;
            }

            var list = userId > 0
                ? glossaryDAO.listGlossaryBySegmentAccess(userId)
                : glossaryDAO.listGlossary();
            
            //system.out.println("📋 GlossaryListServlet: Returning " + list.size() + " glossaries for user " + userId);
            resp.getWriter().write(gson.toJson(list));
        } catch (SQLException e) {
            System.err.println("❌ GlossaryListServlet error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}


