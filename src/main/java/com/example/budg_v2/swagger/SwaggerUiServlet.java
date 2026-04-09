package com.example.budg_v2.swagger;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/swagger-ui.html")
public class SwaggerUiServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Redirect so the browser URL becomes /swagger-ui/index.html; then relative paths
        // in index.html (e.g. ./swagger-initializer.js) resolve correctly under /swagger-ui/
        String path = req.getContextPath();
        if (path == null || path.isEmpty()) path = "";
        resp.sendRedirect(path + "/swagger-ui/index.html");
    }
}
