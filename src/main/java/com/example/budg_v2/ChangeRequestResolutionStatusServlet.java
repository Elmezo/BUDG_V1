package com.example.budg_v2;

import com.example.budg_v2.dao.ChangeRequestResolutionDAO;
import com.example.budg_v2.model.ChangeRequestResolution;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@WebServlet("/api/changerequest-resolution-status")
public class ChangeRequestResolutionStatusServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestResolutionStatusServlet.class);
    private final ChangeRequestResolutionDAO resolutionDAO = new ChangeRequestResolutionDAO();
    
    // Gson instance configured with LocalDateTime adapter
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();
    
    // LocalDateTime adapter for Gson
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.format(formatter));
            }
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String dateTimeString = in.nextString();
            return LocalDateTime.parse(dateTimeString, formatter);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            logger.info("GET /api/changerequest-resolution-status - Fetching all resolution statuses");
            
            if (resolutionDAO == null) {
                logger.error("ResolutionDAO is null!");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "DAO initialization error");
                response.getWriter().write(gson.toJson(error));
                return;
            }
            
            List<ChangeRequestResolution> statusList = resolutionDAO.getAllResolutionStatuses();
            logger.info("Returning {} resolution statuses", statusList.size());
            response.getWriter().write(gson.toJson(statusList));
            
        } catch (SQLException e) {
            // If table doesn't exist, return empty array instead of error
            if (e.getMessage() != null && (e.getMessage().contains("doesn't exist") || 
                e.getMessage().contains("Unknown table") || 
                e.getMessage().contains("Table") && e.getMessage().contains("doesn't exist"))) {
                logger.warn("Resolution status table may not exist, returning empty array: {}", e.getMessage());
                response.getWriter().write(gson.toJson(new java.util.ArrayList<>()));
            } else {
                logger.error("Database error in GET /api/changerequest-resolution-status: {}", e.getMessage(), e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JsonObject error = new JsonObject();
                error.addProperty("error", "Database error: " + e.getMessage());
                response.getWriter().write(gson.toJson(error));
            }
        } catch (Exception e) {
            logger.error("Unexpected error in GET /api/changerequest-resolution-status: {}", e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JsonObject error = new JsonObject();
            error.addProperty("error", "Unexpected error: " + e.getMessage());
            response.getWriter().write(gson.toJson(error));
        }
    }
}
