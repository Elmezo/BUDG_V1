package com.example.budg_v2;

import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.HttpClientUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Proxy servlet for EDC API calls
 * 
 * This servlet acts as a proxy to avoid CORS issues when calling EDC APIs from the browser.
 * It forwards requests to EDC and returns the response.
 * 
 * Endpoint: POST /api/test/edc-proxy
 * 
 * Request body:
 * {
 *   "method": "GET" | "POST",
 *   "url": "https://edc.local:9185/access/2/catalog/data/...",
 *   "username": "Administrator",
 *   "password": "password",
 *   "sslInsecure": false,
 *   "body": { ... } // Optional, for POST requests
 * }
 */
@WebServlet("/api/test/edc-proxy")
public class EdcApiProxyServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(EdcApiProxyServlet.class);
    private final Gson gson = new Gson();
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Enable CORS
        CorsUtil.setCorsHeaders(request, response);
        
        // Only allow POST
        if (!"POST".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        
        try {
            // Read request body
            BufferedReader reader = request.getReader();
            StringBuilder requestBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
            
            // Parse request
            JsonObject requestJson = gson.fromJson(requestBody.toString(), JsonObject.class);
            
            if (!requestJson.has("method") || !requestJson.has("url") || 
                !requestJson.has("username") || !requestJson.has("password")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Missing required fields: method, url, username, password");
                response.setContentType("application/json");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            String method = requestJson.get("method").getAsString();
            String url = requestJson.get("url").getAsString();
            String username = requestJson.get("username").getAsString();
            String password = requestJson.get("password").getAsString();
            boolean sslInsecure = requestJson.has("sslInsecure") && 
                                requestJson.get("sslInsecure").getAsBoolean();
            
            // Get timeout (default 120 seconds)
            int timeout = requestJson.has("timeout") ? 
                         requestJson.get("timeout").getAsInt() : 120;
            
            // Get proxy settings (optional)
            String proxyHost = requestJson.has("proxyHost") && 
                              !requestJson.get("proxyHost").isJsonNull() ?
                              requestJson.get("proxyHost").getAsString() : null;
            Integer proxyPort = requestJson.has("proxyPort") && 
                              !requestJson.get("proxyPort").isJsonNull() ?
                              requestJson.get("proxyPort").getAsInt() : null;
            
            logger.info("EDC Proxy: {} {} (user: {})", method, url, username);
            
            // Call EDC API
            JsonObject resultJson;
            if ("GET".equalsIgnoreCase(method)) {
                String result = HttpClientUtil.getJsonWithBasicAuth(
                    url, username, password, timeout, proxyHost, proxyPort, sslInsecure
                );
                resultJson = gson.fromJson(result, JsonObject.class);
            } else if ("POST".equalsIgnoreCase(method)) {
                JsonObject body = requestJson.has("body") && !requestJson.get("body").isJsonNull() ?
                                 requestJson.get("body").getAsJsonObject() : null;
                
                if (body != null) {
                    resultJson = HttpClientUtil.postJsonGetJson(
                        url, body, username, password, timeout, proxyHost, proxyPort, sslInsecure
                    );
                } else {
                    resultJson = HttpClientUtil.postJsonGetJson(
                        url, "{}", username, password, timeout, proxyHost, proxyPort, sslInsecure
                    );
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Unsupported method: " + method);
                response.setContentType("application/json");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(resultJson));
            
            logger.info("EDC Proxy: Success");
            
        } catch (Exception e) {
            logger.error("EDC Proxy error", e);
            
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            if (e.getCause() != null) {
                error.addProperty("cause", e.getCause().getMessage());
            }
            response.setContentType("application/json");
            response.getWriter().write(gson.toJson(error));
        }
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws IOException {
        CorsUtil.handlePreflight(response);
    }
}

