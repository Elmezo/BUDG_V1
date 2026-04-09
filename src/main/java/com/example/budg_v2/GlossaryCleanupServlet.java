package com.example.budg_v2;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet(name = "GlossaryCleanupServlet", urlPatterns = {"/api/glossary/cleanup-circular"})
public class GlossaryCleanupServlet extends HttpServlet {

    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        try {
            List<String> cleanupResults = cleanupCircularRelationships();
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Circular relationships cleanup completed");
            result.put("details", cleanupResults);
            
            resp.getWriter().write(gson.toJson(result));
            
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Database error during cleanup: " + e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }

    private List<String> cleanupCircularRelationships() throws SQLException {
        List<String> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            // Get all glossary records
            String selectSql = "SELECT ID, Name, Parent_ID FROM glossary WHERE Parent_ID IS NOT NULL ORDER BY ID";
            List<GlossaryRecord> allGlossaries = new ArrayList<>();
            
            try (PreparedStatement ps = conn.prepareStatement(selectSql);
                 ResultSet rs = ps.executeQuery()) {
                
                while (rs.next()) {
                    GlossaryRecord record = new GlossaryRecord();
                    record.id = rs.getInt("ID");
                    record.name = rs.getString("Name");
                    record.parentId = rs.getInt("Parent_ID");
                    allGlossaries.add(record);
                }
            }
            
            // Find and fix circular relationships
            Set<Integer> processedIds = new HashSet<>();
            
            for (GlossaryRecord record : allGlossaries) {
                if (processedIds.contains(record.id)) {
                    continue;
                }
                
                List<Integer> circularChain = findCircularChain(record.id, allGlossaries);
                if (!circularChain.isEmpty()) {
                    // Fix the circular relationship by removing the parent from the last item in the chain
                    int lastItemId = circularChain.get(circularChain.size() - 1);
                    
                    String updateSql = "UPDATE glossary SET Parent_ID = NULL WHERE ID = ?";
                    try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                        updatePs.setInt(1, lastItemId);
                        updatePs.executeUpdate();
                    }
                    
                    // Get names for the result
                    String chainNames = getChainNames(circularChain, allGlossaries);
                    results.add("Fixed circular relationship: " + chainNames + " - Removed parent from ID " + lastItemId);
                    
                    // Mark all items in this chain as processed
                    processedIds.addAll(circularChain);
                }
            }
            
            if (results.isEmpty()) {
                results.add("No circular relationships found.");
            }
            
            conn.commit();
            return results;
            
        } catch (SQLException e) {
            throw e;
        }
    }
    
    private List<Integer> findCircularChain(int startId, List<GlossaryRecord> allGlossaries) {
        Map<Integer, GlossaryRecord> glossaryMap = new HashMap<>();
        for (GlossaryRecord record : allGlossaries) {
            glossaryMap.put(record.id, record);
        }
        
        List<Integer> chain = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        
        int currentId = startId;
        
        while (currentId != 0) {
            if (visited.contains(currentId)) {
                // Found circular relationship - extract the circular part
                int circularStartIndex = chain.indexOf(currentId);
                if (circularStartIndex >= 0) {
                    return chain.subList(circularStartIndex, chain.size());
                }
                break;
            }
            
            visited.add(currentId);
            chain.add(currentId);
            
            GlossaryRecord current = glossaryMap.get(currentId);
            if (current == null || current.parentId == 0) {
                break;
            }
            
            currentId = current.parentId;
        }
        
        return new ArrayList<>(); // No circular relationship found
    }
    
    private String getChainNames(List<Integer> chain, List<GlossaryRecord> allGlossaries) {
        Map<Integer, String> nameMap = new HashMap<>();
        for (GlossaryRecord record : allGlossaries) {
            nameMap.put(record.id, record.name);
        }
        
        List<String> names = new ArrayList<>();
        for (Integer id : chain) {
            String name = nameMap.get(id);
            names.add(name != null ? name : "ID:" + id);
        }
        
        return String.join(" → ", names);
    }
    
    private static class GlossaryRecord {
        int id;
        String name;
        int parentId;
    }
}
