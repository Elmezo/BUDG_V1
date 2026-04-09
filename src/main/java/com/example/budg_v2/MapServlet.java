package com.example.budg_v2;

import com.example.budg_v2.dao.MapDAO;
import com.example.budg_v2.dao.MapLayerDAO;
import com.example.budg_v2.dao.MapMarkerDAO;
import com.example.budg_v2.dao.MapShapeDAO;
import com.example.budg_v2.model.Map;
import com.example.budg_v2.model.MapLayer;
import com.example.budg_v2.model.MapMarker;
import com.example.budg_v2.model.MapShape;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.JsonUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * REST API servlet for geographic map operations
 * Handles maps, layers, markers, and shapes CRUD operations
 */
@WebServlet("/api/maps/*")
public class MapServlet extends HttpServlet {
    
    private final MapDAO mapDAO = new MapDAO();
    private final MapLayerDAO layerDAO = new MapLayerDAO();
    private final MapMarkerDAO markerDAO = new MapMarkerDAO();
    private final MapShapeDAO shapeDAO = new MapShapeDAO();
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            // GET /api/maps - Get all maps
            if (pathInfo.isEmpty() || pathInfo.equals("/")) {
                List<Map> maps = mapDAO.getAllMaps();
                JsonUtil.sendJsonResponse(resp.getWriter(), maps);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");
            
            // GET /api/maps/{mapId} - Get map by ID
            if (parts.length == 1 && isNumeric(parts[0])) {
                long mapId = Long.parseLong(parts[0]);
                Map map = mapDAO.getMapById(mapId);
                if (map != null) {
                    JsonUtil.sendJsonResponse(resp.getWriter(), map);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Map not found", 404);
                }
                return;
            }

            // GET /api/maps/{mapId}/layers - Get layers for a map
            if (parts.length == 2 && isNumeric(parts[0]) && "layers".equals(parts[1])) {
                long mapId = Long.parseLong(parts[0]);
                List<MapLayer> layers = layerDAO.getLayersByMapId(mapId);
                JsonUtil.sendJsonResponse(resp.getWriter(), layers);
                return;
            }

            // GET /api/layers/{layerId}/markers - Get markers for a layer
            if (parts.length == 3 && "layers".equals(parts[0]) && isNumeric(parts[1]) && "markers".equals(parts[2])) {
                long layerId = Long.parseLong(parts[1]);
                List<MapMarker> markers = markerDAO.getMarkersByLayerId(layerId);
                JsonUtil.sendJsonResponse(resp.getWriter(), markers);
                return;
            }

            // GET /api/layers/{layerId}/shapes - Get shapes for a layer
            if (parts.length == 3 && "layers".equals(parts[0]) && isNumeric(parts[1]) && "shapes".equals(parts[2])) {
                long layerId = Long.parseLong(parts[1]);
                List<MapShape> shapes = shapeDAO.getShapesByLayerId(layerId);
                JsonUtil.sendJsonResponse(resp.getWriter(), shapes);
                return;
            }

            // GET /api/maps/{mapId}/search - Search map features
            if (parts.length == 2 && isNumeric(parts[0]) && "search".equals(parts[1])) {
                String query = req.getParameter("q");
                if (query == null || query.trim().isEmpty()) {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Search query parameter 'q' is required", 400);
                    return;
                }
                // TODO: Implement search functionality
                JsonUtil.sendJsonResponse(resp.getWriter(), new java.util.ArrayList<>());
                return;
            }

            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid endpoint", 404);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            String jsonData = readRequestBody(req);
            if (jsonData == null || jsonData.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Request body is required", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");

            // POST /api/maps - Create new map
            if (pathInfo.isEmpty() || pathInfo.equals("/")) {
                Map map = gson.fromJson(jsonData, Map.class);
                Map created = mapDAO.createMap(map);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonUtil.sendJsonResponse(resp.getWriter(), created);
                return;
            }

            // POST /api/maps/{mapId}/layers - Create new layer
            if (parts.length == 2 && isNumeric(parts[0]) && "layers".equals(parts[1])) {
                long mapId = Long.parseLong(parts[0]);
                MapLayer layer = gson.fromJson(jsonData, MapLayer.class);
                layer.setMapId(mapId);
                MapLayer created = layerDAO.createLayer(layer);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonUtil.sendJsonResponse(resp.getWriter(), created);
                return;
            }

            // POST /api/layers/{layerId}/markers - Create new marker
            if (parts.length == 3 && "layers".equals(parts[0]) && isNumeric(parts[1]) && "markers".equals(parts[2])) {
                long layerId = Long.parseLong(parts[1]);
                MapMarker marker = gson.fromJson(jsonData, MapMarker.class);
                marker.setLayerId(layerId);
                MapMarker created = markerDAO.createMarker(marker);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonUtil.sendJsonResponse(resp.getWriter(), created);
                return;
            }

            // POST /api/layers/{layerId}/shapes - Create new shape
            if (parts.length == 3 && "layers".equals(parts[0]) && isNumeric(parts[1]) && "shapes".equals(parts[2])) {
                long layerId = Long.parseLong(parts[1]);
                MapShape shape = gson.fromJson(jsonData, MapShape.class);
                shape.setLayerId(layerId);
                MapShape created = shapeDAO.createShape(shape);
                resp.setStatus(HttpServletResponse.SC_CREATED);
                JsonUtil.sendJsonResponse(resp.getWriter(), created);
                return;
            }

            // POST /api/maps/{mapId}/import - Import map data
            if (parts.length == 2 && isNumeric(parts[0]) && "import".equals(parts[1])) {
                // TODO: Implement import functionality
                JsonObject response = new JsonObject();
                response.addProperty("message", "Import functionality not yet implemented");
                resp.getWriter().write(gson.toJson(response));
                return;
            }

            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid endpoint", 404);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            String jsonData = readRequestBody(req);
            if (jsonData == null || jsonData.trim().isEmpty()) {
                JsonUtil.sendErrorResponse(resp.getWriter(), "Request body is required", 400);
                return;
            }

            String[] parts = pathInfo.substring(1).split("/");

            // PUT /api/maps/{mapId} - Update map
            if (parts.length == 1 && isNumeric(parts[0])) {
                long mapId = Long.parseLong(parts[0]);
                Map map = gson.fromJson(jsonData, Map.class);
                map.setId(mapId);
                boolean updated = mapDAO.updateMap(map);
                if (updated) {
                    JsonUtil.sendJsonResponse(resp.getWriter(), map);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Map not found", 404);
                }
                return;
            }

            // PUT /api/layers/{layerId} - Update layer
            if (parts.length == 2 && "layers".equals(parts[0]) && isNumeric(parts[1])) {
                long layerId = Long.parseLong(parts[1]);
                MapLayer layer = gson.fromJson(jsonData, MapLayer.class);
                layer.setId(layerId);
                boolean updated = layerDAO.updateLayer(layer);
                if (updated) {
                    JsonUtil.sendJsonResponse(resp.getWriter(), layer);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Layer not found", 404);
                }
                return;
            }

            // PUT /api/markers/{markerId} - Update marker
            if (parts.length == 2 && "markers".equals(parts[0]) && isNumeric(parts[1])) {
                long markerId = Long.parseLong(parts[1]);
                MapMarker marker = gson.fromJson(jsonData, MapMarker.class);
                marker.setId(markerId);
                boolean updated = markerDAO.updateMarker(marker);
                if (updated) {
                    JsonUtil.sendJsonResponse(resp.getWriter(), marker);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Marker not found", 404);
                }
                return;
            }

            // PUT /api/shapes/{shapeId} - Update shape
            if (parts.length == 2 && "shapes".equals(parts[0]) && isNumeric(parts[1])) {
                long shapeId = Long.parseLong(parts[1]);
                MapShape shape = gson.fromJson(jsonData, MapShape.class);
                shape.setId(shapeId);
                boolean updated = shapeDAO.updateShape(shape);
                if (updated) {
                    JsonUtil.sendJsonResponse(resp.getWriter(), shape);
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Shape not found", 404);
                }
                return;
            }

            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid endpoint", 404);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String pathInfo = req.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        try {
            String[] parts = pathInfo.substring(1).split("/");

            // DELETE /api/maps/{mapId} - Delete map
            if (parts.length == 1 && isNumeric(parts[0])) {
                long mapId = Long.parseLong(parts[0]);
                boolean deleted = mapDAO.deleteMap(mapId);
                if (deleted) {
                    JsonObject response = new JsonObject();
                    response.addProperty("message", "Map deleted successfully");
                    resp.getWriter().write(gson.toJson(response));
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Map not found", 404);
                }
                return;
            }

            // DELETE /api/layers/{layerId} - Delete layer
            if (parts.length == 2 && "layers".equals(parts[0]) && isNumeric(parts[1])) {
                long layerId = Long.parseLong(parts[1]);
                boolean deleted = layerDAO.deleteLayer(layerId);
                if (deleted) {
                    JsonObject response = new JsonObject();
                    response.addProperty("message", "Layer deleted successfully");
                    resp.getWriter().write(gson.toJson(response));
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Layer not found", 404);
                }
                return;
            }

            // DELETE /api/markers/{markerId} - Delete marker
            if (parts.length == 2 && "markers".equals(parts[0]) && isNumeric(parts[1])) {
                long markerId = Long.parseLong(parts[1]);
                boolean deleted = markerDAO.deleteMarker(markerId);
                if (deleted) {
                    JsonObject response = new JsonObject();
                    response.addProperty("message", "Marker deleted successfully");
                    resp.getWriter().write(gson.toJson(response));
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Marker not found", 404);
                }
                return;
            }

            // DELETE /api/shapes/{shapeId} - Delete shape
            if (parts.length == 2 && "shapes".equals(parts[0]) && isNumeric(parts[1])) {
                long shapeId = Long.parseLong(parts[1]);
                boolean deleted = shapeDAO.deleteShape(shapeId);
                if (deleted) {
                    JsonObject response = new JsonObject();
                    response.addProperty("message", "Shape deleted successfully");
                    resp.getWriter().write(gson.toJson(response));
                } else {
                    JsonUtil.sendErrorResponse(resp.getWriter(), "Shape not found", 404);
                }
                return;
            }

            JsonUtil.sendErrorResponse(resp.getWriter(), "Invalid endpoint", 404);
        } catch (SQLException e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendErrorResponse(resp.getWriter(), "Error: " + e.getMessage(), 500);
        }
    }

    private String readRequestBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = req.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

