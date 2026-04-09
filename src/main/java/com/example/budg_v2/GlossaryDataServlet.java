package com.example.budg_v2;

import com.example.budg_v2.dao.GlossaryDataDAO;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Isolated servlet for Glossary Data Tab
 * Handles datasets and attributes endpoints
 */
@WebServlet(name = "GlossaryDataServlet", urlPatterns = {"/api/glossary-data/*"})
public class GlossaryDataServlet extends HttpServlet {

    private final GlossaryDataDAO glossaryDataDAO = new GlossaryDataDAO();
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
        //system.out.println("GlossaryDataServlet: pathInfo = " + pathInfo);
        
        if (pathInfo == null || "/".equals(pathInfo)) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid path. Use /api/glossary-data/{glossaryId}/datasets or /api/glossary-data/{glossaryId}/attributes\"}");
            return;
        }

        // Parse path: /{glossaryId}/datasets or /{glossaryId}/attributes
        // Remove leading slash and split
        String cleanPath = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        String[] pathParts = cleanPath.split("/");
        //system.out.println("GlossaryDataServlet: pathParts = " + java.util.Arrays.toString(pathParts));
        
        if (pathParts.length < 2) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid path format. Use /api/glossary-data/{glossaryId}/datasets or /api/glossary-data/{glossaryId}/attributes\"}");
            return;
        }

        try {
            int glossaryId = Integer.parseInt(pathParts[0]);
            String resource = pathParts.length > 1 ? pathParts[1].toLowerCase() : "";
            //system.out.println("GlossaryDataServlet: glossaryId = " + glossaryId + ", resource = " + resource);

            if ("datasets".equals(resource)) {
                boolean includeRollup = "true".equalsIgnoreCase(req.getParameter("rollup"));
                var datasets = glossaryDataDAO.getDatasetsByGlossaryId(glossaryId, includeRollup);
                resp.getWriter().write(gson.toJson(datasets));
            } else if ("attributes".equals(resource)) {
                boolean includeRollup = "true".equalsIgnoreCase(req.getParameter("rollup"));
                //system.out.println("GlossaryDataServlet: Fetching attributes for glossaryId=" + glossaryId + ", rollup=" + includeRollup);
                var attributes = glossaryDataDAO.getAttributesByGlossaryId(glossaryId, includeRollup);
                //system.out.println("GlossaryDataServlet: Retrieved " + (attributes != null ? attributes.size() : 0) + " attributes");
                resp.getWriter().write(gson.toJson(attributes));
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"error\":\"Invalid resource. Use 'datasets' or 'attributes'\"}");
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid glossary id\"}");
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

