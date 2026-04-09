package com.example.budg_v2;

import com.example.budg_v2.repository.PersonFollowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * Provides the data that powers the "Following" tab in the people view.
 */
@WebServlet("/api/people/following/*")
public class PersonFollowingServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathInfo.substring(1));
            Map<String, Object> payload = PersonFollowRepository.getFollowingData(personId);

            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), payload);
        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid person ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
            sendError(response, "Database error: " + sqlEx.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            String pathInfo = request.getPathInfo();
            if (pathInfo == null || pathInfo.length() <= 1) {
                sendError(response, "Person ID is required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            // Parse the path to get personId and followId
            String[] pathParts = pathInfo.substring(1).split("/");
            if (pathParts.length < 2) {
                sendError(response, "Both person ID and follow ID are required", HttpServletResponse.SC_BAD_REQUEST);
                return;
            }

            int personId = Integer.parseInt(pathParts[0]);
            int followId = Integer.parseInt(pathParts[1]);

            boolean success = PersonFollowRepository.removeFollowing(personId, followId);
            
            if (success) {
                response.setStatus(HttpServletResponse.SC_OK);
                Map<String, Object> result = Map.of("success", true, "message", "Following relationship removed successfully");
                objectMapper.writeValue(response.getWriter(), result);
            } else {
                sendError(response, "Failed to remove following relationship", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (NumberFormatException nfe) {
            sendError(response, "Invalid ID format", HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException sqlEx) {
            sqlEx.printStackTrace();
            sendError(response, "Database error: " + sqlEx.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception ex) {
            ex.printStackTrace();
            sendError(response, "Internal server error: " + ex.getMessage(),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void sendError(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        Map<String, Object> error = Map.of("error", message);
        objectMapper.writeValue(response.getWriter(), error);
    }
}

