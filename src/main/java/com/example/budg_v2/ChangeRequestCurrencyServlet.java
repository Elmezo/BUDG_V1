package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servlet for Change Request Currency operations
 * Endpoints:
 * GET /api/changerequest_currencies - List all currencies
 * GET /api/changerequest_systems - List all change request systems (placeholder)
 * GET /api/changerequest_severity - List all severity levels
 * GET /api/changerequest_urgency - List all urgency levels
 */
@WebServlet({"/api/changerequest_currencies", "/api/changerequest_systems", 
             "/api/changerequest_severity", "/api/changerequest_urgency",
             "/api/changerequest-currencies", "/api/changerequest-systems", 
             "/api/changerequest-severities", "/api/changerequest-urgencies",
             "/api/changerequest-types"})
public class ChangeRequestCurrencyServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestCurrencyServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String servletPath = request.getServletPath();
            
            switch (servletPath) {
                case "/api/changerequest_currencies":
                case "/api/changerequest-currencies":
                    getCurrencies(response);
                    break;
                case "/api/changerequest_systems":
                case "/api/changerequest-systems":
                    getChangeRequestSystems(response);
                    break;
                case "/api/changerequest_severity":
                case "/api/changerequest-severities":
                    getSeverityLevels(response);
                    break;
                case "/api/changerequest_urgency":
                case "/api/changerequest-urgencies":
                    getUrgencyLevels(response);
                    break;
                case "/api/changerequest-types":
                    getTypes(response);
                    break;
                default:
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    JsonObject error = new JsonObject();
                    error.addProperty("error", "Endpoint not found");
                    response.getWriter().write(gson.toJson(error));
            }

        } catch (SQLException e) {
            logger.error("Database error in ChangeRequestCurrencyServlet", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Database error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }

    private void getCurrencies(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> currencies = new ArrayList<>();

        String sql = "SELECT ID, PrimaryName, Description FROM changerequest_currency " +
                    "WHERE Status = 'Enabled' ORDER BY PrimaryName";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> currency = new HashMap<>();
                currency.put("id", rs.getInt("ID"));
                currency.put("name", rs.getString("PrimaryName"));
                currency.put("description", rs.getString("Description"));
                currencies.add(currency);
            }
        }

        // If no currencies found, add default ones
        if (currencies.isEmpty()) {
            currencies.add(createCurrencyMap(1, "GBP", "British Pound Sterling"));
            currencies.add(createCurrencyMap(2, "USD", "US Dollar"));
            currencies.add(createCurrencyMap(3, "EUR", "Euro"));
        }

        response.getWriter().write(gson.toJson(currencies));
    }

    private void getChangeRequestSystems(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> systems = new ArrayList<>();

        // For now, return a default "Native" system
        // This can be expanded later to read from a proper table
        Map<String, Object> nativeSystem = new HashMap<>();
        nativeSystem.put("id", "native");
        nativeSystem.put("name", "Native");
        nativeSystem.put("description", "Native BUDG Change Request System");
        systems.add(nativeSystem);

        response.getWriter().write(gson.toJson(systems));
    }

    private void getSeverityLevels(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> severityLevels = new ArrayList<>();

        String sql = "SELECT ID, PrimaryName FROM changerequest_severity ORDER BY ID";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> severity = new HashMap<>();
                severity.put("id", rs.getInt("ID"));
                severity.put("primaryName", rs.getString("PrimaryName"));
                severity.put("name", rs.getString("PrimaryName"));
                severityLevels.add(severity);
            }
        }

        // If no severity levels found, add default ones
        if (severityLevels.isEmpty()) {
            severityLevels.add(createTypeMap(1, "Low"));
            severityLevels.add(createTypeMap(2, "Medium"));
            severityLevels.add(createTypeMap(3, "High"));
            severityLevels.add(createTypeMap(4, "Critical"));
        }

        response.getWriter().write(gson.toJson(severityLevels));
    }

    private void getUrgencyLevels(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> urgencyLevels = new ArrayList<>();

        String sql = "SELECT ID, PrimaryName FROM changerequest_urgency ORDER BY ID";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> urgency = new HashMap<>();
                urgency.put("id", rs.getInt("ID"));
                urgency.put("primaryName", rs.getString("PrimaryName"));
                urgency.put("name", rs.getString("PrimaryName"));
                urgencyLevels.add(urgency);
            }
        }

        // If no urgency levels found, add default ones
        if (urgencyLevels.isEmpty()) {
            urgencyLevels.add(createTypeMap(1, "Low"));
            urgencyLevels.add(createTypeMap(2, "Medium"));
            urgencyLevels.add(createTypeMap(3, "High"));
            urgencyLevels.add(createTypeMap(4, "Critical"));
        }

        response.getWriter().write(gson.toJson(urgencyLevels));
    }

    private void getTypes(HttpServletResponse response) throws SQLException, IOException {
        List<Map<String, Object>> types = new ArrayList<>();

        String sql = "SELECT ID, PrimaryName FROM changerequest_type ORDER BY ID";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> type = new HashMap<>();
                type.put("id", rs.getInt("ID"));
                type.put("primaryName", rs.getString("PrimaryName"));
                types.add(type);
            }
        }

        // If no types found, add default ones
        if (types.isEmpty()) {
            types.add(createTypeMap(1, "Request For Change"));
            types.add(createTypeMap(2, "Bug Fix"));
            types.add(createTypeMap(3, "Enhancement"));
            types.add(createTypeMap(4, "New Feature"));
        }

        response.getWriter().write(gson.toJson(types));
    }

    private Map<String, Object> createCurrencyMap(int id, String name, String description) {
        Map<String, Object> currency = new HashMap<>();
        currency.put("id", id);
        currency.put("name", name);
        currency.put("description", description);
        return currency;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> createLookupMap(int id, String name) {
        Map<String, Object> lookup = new HashMap<>();
        lookup.put("id", id);
        lookup.put("name", name);
        return lookup;
    }

    private Map<String, Object> createTypeMap(int id, String name) {
        Map<String, Object> type = new HashMap<>();
        type.put("id", id);
        type.put("primaryName", name);
        return type;
    }
}
