package com.example.budg_v2;

import com.example.budg_v2.model.EmailSettings;
import com.example.budg_v2.service.EmailService;
import com.example.budg_v2.service.EmailSettingsService;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Servlet for email settings management in Admin Panel
 * Endpoints:
 * GET /api/email-settings - Get current SMTP settings
 * PUT /api/email-settings - Update SMTP settings
 * POST /api/email-settings/test - Test SMTP connection
 * GET /api/email-settings/enabled - Check if email is enabled
 */
@WebServlet("/api/email-settings/*")
public class EmailSettingsServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(EmailSettingsServlet.class);
    private final Gson gson = new Gson();
    private final EmailSettingsService emailSettingsService = new EmailSettingsService();
    private final EmailService emailService = new EmailService();

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) {
        CorsUtil.handlePreflight(response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        try {
            if (pathInfo != null && pathInfo.equals("/enabled")) {
                // Check if email is enabled
                boolean enabled = emailSettingsService.isEmailEnabled();
                JsonObject result = new JsonObject();
                result.addProperty("enabled", enabled);
                response.getWriter().write(gson.toJson(result));

            } else {
                // Get current email settings
                EmailSettings settings = emailSettingsService.getCurrentSettings();
                if (settings == null) {
                    // Return default settings
                    settings = new EmailSettings();
                    settings.setSmtpHost("smtp.gmail.com");
                    settings.setSmtpPort(587);
                    settings.setEncryption("TLS");
                    settings.setEnabled(false);
                } else {
                    // Don't send password in response
                    settings.setSmtpPassword(""); // Clear password
                }
                response.getWriter().write(gson.toJson(settings));
            }

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            BufferedReader reader = request.getReader();
            StringBuilder jsonBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBody.append(line);
            }

            EmailSettings settings = gson.fromJson(jsonBody.toString(), EmailSettings.class);

            // Validate settings
            if (!emailSettingsService.validateSettings(settings)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Invalid email settings: all fields are required");
                response.getWriter().write(gson.toJson(error));
                return;
            }

            // If password is empty, keep existing password
            if (settings.getSmtpPassword() == null || settings.getSmtpPassword().trim().isEmpty()) {
                EmailSettings existing = emailSettingsService.getCurrentSettings();
                if (existing != null && existing.getSmtpPassword() != null) {
                    settings.setSmtpPassword(existing.getSmtpPassword());
                }
            }

            // Save settings
            emailSettingsService.saveSettings(settings);

            // Don't send password in response
            settings.setSmtpPassword("");

            response.getWriter().write(gson.toJson(settings));

        } catch (SQLException e) {
            logger.error("Database error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(request, response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String pathInfo = request.getPathInfo();

        if (pathInfo != null && pathInfo.equals("/test")) {
            // Test SMTP connection
            try {
                BufferedReader reader = request.getReader();
                StringBuilder jsonBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonBody.append(line);
                }

                EmailSettings settings = gson.fromJson(jsonBody.toString(), EmailSettings.class);

                // Validate settings
                if (!emailSettingsService.validateSettings(settings)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Invalid email settings: all fields are required");
                    response.getWriter().write(gson.toJson(error));
                    return;
                }

                // Test connection
                boolean success = emailService.testConnection(settings);

                JsonObject result = new JsonObject();
                result.addProperty("success", success);
                if (success) {
                    result.addProperty("message", "SMTP connection test successful");
                } else {
                    result.addProperty("message", "SMTP connection test failed. Please check your settings.");
                }
                response.getWriter().write(gson.toJson(result));

            } catch (Exception e) {
                logger.error("Error testing SMTP connection", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Error testing connection: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
            }
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Invalid endpoint");
            response.getWriter().write(gson.toJson(error));
        }
    }
}




















































