package com.example.budg_v2;

import com.example.budg_v2.dao.DatasetDAO;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;

@WebServlet(name = "DatasetByGlossaryServlet", urlPatterns = {"/api/dataset/glossary/*"})
public class DatasetByGlossaryServlet extends HttpServlet {

    private final DatasetDAO datasetDAO = new DatasetDAO();
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
        String idStr = null;
        if (pathInfo != null && !"/".equals(pathInfo)) {
            idStr = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
            // trim any trailing slash
            if (idStr.endsWith("/")) idStr = idStr.substring(0, idStr.length() - 1);
        } else {
            // Fallback: support /api/dataset/glossary?id=123
            idStr = req.getParameter("id");
        }
        
        if (idStr == null || idStr.isBlank()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Missing glossary id\"}");
            return;
        }
        
        try {
            int glossaryId = Integer.parseInt(idStr);
            var datasets = datasetDAO.getDatasetsByGlossaryId(glossaryId);
            resp.getWriter().write(gson.toJson(datasets));
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"error\":\"Invalid glossary id\"}");
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"error\":\"Database error\"}");
        }
    }
}
