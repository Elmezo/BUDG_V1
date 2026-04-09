package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.example.budg_v2.util.UserContextUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servlet to manage user's selected segments (cube selection).
 * Stores the user's segment selection in the database for persistence across sessions.
 * 
 * Table: user_segment_selection (must be created separately)
 * 
 * GET /api/user/segment-selection - Get user's current segment selection
 * POST /api/user/segment-selection - Save user's segment selection
 */
@WebServlet(urlPatterns = {"/api/user/segment-selection"})
public class UserSegmentSelectionServlet extends HttpServlet {
    
    private static final Gson gson = new Gson();
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        PrintWriter out = response.getWriter();
        
        Integer userId = UserContextUtil.getCurrentUserIdOrNull(request);
        if (userId == null || userId <= 0) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"success\": false, \"error\": \"User not authenticated\"}");
            return;
        }
        
        try {
            List<Integer> selectedSegmentIds = getUserSelectedSegments(userId);
            
            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("userId", userId);
            
            JsonArray segmentsArray = new JsonArray();
            for (Integer segmentId : selectedSegmentIds) {
                segmentsArray.add(segmentId);
            }
            result.add("selectedSegmentIds", segmentsArray);
            
            out.print(gson.toJson(result));
            
        } catch (SQLException e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"success\": false, \"error\": \"Database error: " + e.getMessage() + "\"}");
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(response);
        PrintWriter out = response.getWriter();

        Integer userId = UserContextUtil.getCurrentUserIdOrNull(request);
        if (userId == null || userId <= 0) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"success\": false, \"error\": \"User not authenticated\"}");
            return;
        }
        
        try {
            // Read request body
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JsonObject requestBody = JsonParser.parseString(sb.toString()).getAsJsonObject();
            JsonArray segmentIdsArray = requestBody.getAsJsonArray("selectedSegmentIds");

            if (segmentIdsArray == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"success\": false, \"error\": \"Missing selectedSegmentIds\"}");
                return;
            }
            
            List<Integer> segmentIds = new ArrayList<>();
            for (int i = 0; i < segmentIdsArray.size(); i++) {
                segmentIds.add(segmentIdsArray.get(i).getAsInt());
            }
            
            // Save the selection
            saveUserSelectedSegments(userId, segmentIds);

            JsonObject result = new JsonObject();
            result.addProperty("success", true);
            result.addProperty("message", "Segment selection saved");
            result.addProperty("count", segmentIds.size());

            out.print(gson.toJson(result));

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"success\": false, \"error\": \"Error saving selection: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Get user's selected segment IDs
     */
    private List<Integer> getUserSelectedSegments(int userId) throws SQLException {
        List<Integer> segmentIds = new ArrayList<>();
        
        String sql = "SELECT segment_id FROM user_segment_selection WHERE user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, userId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    segmentIds.add(rs.getInt("segment_id"));
                }
            }
        }
        
        return segmentIds;
    }
    
    /**
     * Save user's selected segment IDs using diff-based approach
     * Only deletes removed segments and inserts new ones (more efficient)
     */
    private void saveUserSelectedSegments(int userId, List<Integer> segmentIds) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Get current selection
                Set<Integer> currentSelection = new HashSet<>();
                String selectSql = "SELECT segment_id FROM user_segment_selection WHERE user_id = ?";
                try (PreparedStatement selectStmt = conn.prepareStatement(selectSql)) {
                    selectStmt.setInt(1, userId);
                    try (ResultSet rs = selectStmt.executeQuery()) {
                        while (rs.next()) {
                            currentSelection.add(rs.getInt("segment_id"));
                        }
                    }
                }
                
                Set<Integer> newSelection = new HashSet<>(segmentIds);
                
                // Calculate what to delete (in current but not in new)
                Set<Integer> toDelete = new HashSet<>(currentSelection);
                toDelete.removeAll(newSelection);
                
                // Calculate what to insert (in new but not in current)
                Set<Integer> toInsert = new HashSet<>(newSelection);
                toInsert.removeAll(currentSelection);
                
                // Delete removed segments
                if (!toDelete.isEmpty()) {
                    String deleteSql = "DELETE FROM user_segment_selection WHERE user_id = ? AND segment_id = ?";
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        for (Integer segmentId : toDelete) {
                            deleteStmt.setInt(1, userId);
                            deleteStmt.setInt(2, segmentId);
                            deleteStmt.addBatch();
                        }
                        deleteStmt.executeBatch();
                    }
                }
                
                // Insert new segments
                if (!toInsert.isEmpty()) {
                    String insertSql = "INSERT INTO user_segment_selection (user_id, segment_id) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        for (Integer segmentId : toInsert) {
                            insertStmt.setInt(1, userId);
                            insertStmt.setInt(2, segmentId);
                            insertStmt.addBatch();
                        }
                        insertStmt.executeBatch();
                    }
                }
                
                conn.commit();
                System.out.println("💾 Updated segment selection for user " + userId + 
                    " (deleted: " + toDelete.size() + ", inserted: " + toInsert.size() + ", total: " + segmentIds.size() + ")");
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}

