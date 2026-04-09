package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
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

/**
 * Servlet for retrieving document types
 * Endpoint: GET /api/document-types
 */
@WebServlet("/api/document-types")
public class DocumentTypeServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DocumentTypeServlet.class);
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.setCorsHeaders(resp);
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        CorsUtil.setCorsHeaders(response);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            JsonArray types = getDocumentTypes();

            JsonObject responseData = new JsonObject();
            responseData.addProperty("success", true);
            responseData.add("types", types);

            response.getWriter().write(gson.toJson(responseData));

        } catch (SQLException e) {
            logger.error("Database error retrieving document types", e);
            JsonObject error = new JsonObject();
            error.addProperty("success", false);
            error.addProperty("error", "Database error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write(gson.toJson(error));
        }
    }

    /**
     * Get all document types from database
     * Returns unique document types by PrimaryName (removes duplicates)
     */
    private JsonArray getDocumentTypes() throws SQLException {
        String sql = "SELECT ID, PrimaryName, Description FROM document " +
                "WHERE DeletedDatetime IS NULL ORDER BY PrimaryName";

        JsonArray types = new JsonArray();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                JsonObject type = new JsonObject();
                type.addProperty("id", rs.getInt("ID"));
                type.addProperty("name", rs.getString("PrimaryName"));
                type.addProperty("description", rs.getString("Description"));
                types.add(type);
            }
        }

        return types;
    }
}
