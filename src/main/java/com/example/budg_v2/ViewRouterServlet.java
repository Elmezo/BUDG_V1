package com.example.budg_v2;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "ViewRouterServlet", urlPatterns = {"/view/*"})
public class ViewRouterServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();

        // Serve static assets under /view/** that contain a dot like .js, .css, .png, etc.
        if (path != null && path.contains(".")) {
            var defaultDispatcher = getServletContext().getNamedDispatcher("default");
            if (defaultDispatcher != null) {
                defaultDispatcher.forward(req, resp);
                return;
            }
            // Fallback to manual streaming
            try (var in = getServletContext().getResourceAsStream("/view" + path);
                 var out = resp.getOutputStream()) {
                if (in == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                if (path.endsWith(".js")) resp.setContentType("application/javascript;charset=UTF-8");
                else if (path.endsWith(".css")) resp.setContentType("text/css;charset=UTF-8");
                in.transferTo(out);
                return;
            }
        }

        // Fullscreen lineage map: /view/full_map/{facet}/{entityId} or /view/full_map/{facet}/{variant}/{entityId}
        if (path != null && path.startsWith("/full_map/")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/full_map/full_map.html");
                 var out = resp.getOutputStream()) {
                if (in == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                in.transferTo(out);
            }
            return;
        }

        // Serve entity-specific shells when available
        if (path != null && path.matches("^/system/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/system/system.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }
        if (path != null && path.matches("^/dataset/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/dataset/dataset.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }
        if (path != null && (path.matches("^/system-interface/\\d+$") || path.matches("^/interface/\\d+$"))) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/system-interface/system-interface.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }
        if (path != null && path.matches("^/glossary/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/glossary/glossary.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        if (path != null && path.matches("^/people/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/people/people.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        if (path != null && path.matches("^/org-unit/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/org-unit/org-unit.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        if (path != null && path.matches("^/role/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/role/role.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Added from EDITOR: Policy routes
        if (path != null && path.matches("^/policy/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/policy/policy.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Added from EDITOR: Process routes
        if (path != null && path.matches("^/process/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/process/process.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Added from EDITOR: Project routes
        if (path != null && path.matches("^/project/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/project/project.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Added from EDITOR: Product routes
        if (path != null && path.matches("^/product/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/product/product.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Committee routes
        if (path != null && path.matches("^/committee/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/committee/committee.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // LegalEntity routes
        if (path != null && path.matches("^/LegalEntity/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/LegalEntity/legal-entity.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Client routes
        if (path != null && path.matches("^/client/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/client/client.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Business Area routes
        if (path != null && path.matches("^/business-area/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/business-area/business-area.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Capability routes
        if (path != null && path.matches("^/capability/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/capability/capability.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Regulatory Theme routes
        if (path != null && path.matches("^/regulatory-theme/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/regulatory-theme/regulatory-theme.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Regulator routes
        if (path != null && path.matches("^/regulator/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/regulator/regulator.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Geography routes
        if (path != null && path.matches("^/geography/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/geography/geography.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }

        // Regulation routes
        if (path != null && path.matches("^/regulation/\\d+$")) {
            resp.setContentType("text/html;charset=UTF-8");
            try (var in = getServletContext().getResourceAsStream("/view/regulation/regulation.html");
                 var out = resp.getOutputStream()) {
                if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
                in.transferTo(out);
            }
            return;
        }
        // Attribute routes - attributes don't have their own view page;
        // redirect to the parent dataset with the attribute tab open.
        if (path != null && path.matches("^/attribute/\\d+$")) {
            String attrId = path.substring("/attribute/".length());
            resp.setContentType("text/html;charset=UTF-8");
            resp.getWriter().write(
                "<!DOCTYPE html><html><head><title>Redirecting…</title></head><body>" +
                "<p>Looking up attribute…</p><script>" +
                "fetch('/UnisonSearch/attribute?q=' + encodeURIComponent('" + attrId + "'))" +
                ".then(r => r.json()).then(data => {" +
                "  if (Array.isArray(data) && data.length > 0) {" +
                "    var attr = data.find(a => String(a.ID||a.id) === '" + attrId + "');" +
                "    if (attr) { var ds = attr.Dataset_ID||attr.DatasetID||attr['Data Set Name_ID'];" +
                "      if (ds) { window.location.replace('/view/dataset/' + ds + '?tab=attribute&attributeId=" + attrId + "'); return; }" +
                "    }" +
                "  }" +
                "  document.body.innerHTML = '<p>Could not find parent dataset for this attribute.</p>';" +
                "}).catch(() => { document.body.innerHTML = '<p>Error looking up attribute.</p>'; });" +
                "</script></body></html>"
            );
            return;
        }

        // Return the generic SPA shell for any other deep link like /view/{entity}/{id}
        resp.setContentType("text/html;charset=UTF-8");
        try (var in = getServletContext().getResourceAsStream("/view/view.html");
             var out = resp.getOutputStream()) {
            if (in == null) { resp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }
            in.transferTo(out);
        }
    }

    @Override
    protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}


