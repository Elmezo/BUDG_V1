package com.example.budg_v2;

import com.example.budg_v2.model.ProductLifecycle;
import com.example.budg_v2.service.ProductLifecycleService;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet("/api/product/lifecycle/list")
public class ProductLifecycleServlet extends HttpServlet {

    private ProductLifecycleService productLifecycleService;

    @Override
    public void init() {
        this.productLifecycleService = new ProductLifecycleService();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);

        try {
            List<ProductLifecycle> lifecycles = productLifecycleService.getAllProductLifecycles();
            com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
            for (ProductLifecycle pl : lifecycles) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("id", pl.getId());
                o.addProperty("primaryname", pl.getPrimaryName());
                arr.add(o);
            }
            response.getWriter().write(arr.toString());
        } catch (Exception e) {
            JsonUtil.sendErrorResponse(response.getWriter(), "Error retrieving product lifecycles: " + e.getMessage(), 500);
        }
    }

    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.setCorsHeaders(response);
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
