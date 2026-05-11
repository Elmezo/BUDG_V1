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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebServlet(name = "Data_Flow_Outside_InterfacesServlet", urlPatterns = {"/api/system/data-flow-outside-interfaces"})
public class Data_Flow_Outside_InterfacesServlet extends HttpServlet {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logger.getLogger(Data_Flow_Outside_InterfacesServlet.class.getName());
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

        String systemIdParam = req.getParameter("system_id");
        
        if (systemIdParam == null || systemIdParam.trim().isEmpty()) {
            resp.getWriter().write(gson.toJson(new ArrayList<>()));
            return;
        }

        try {
            int systemId = Integer.parseInt(systemIdParam);
            List<Map<String, Object>> results = getDataFlowOutsideInterfaces(systemId);
            resp.getWriter().write(gson.toJson(results));
            
        } catch (NumberFormatException e) {
            resp.getWriter().write(gson.toJson(new ArrayList<>()));
        } catch (SQLException e) {
            resp.getWriter().write(gson.toJson(new ArrayList<>()));
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
    }

    private List<Map<String, Object>> getDataFlowOutsideInterfaces(int systemId) throws SQLException {
        String sql = "SELECT " +
                "s2.Name AS source_system, " +
                "s2.id AS source_system_id, " +
                "s.Name AS target_system, " +
                "s.id AS target_system_id, " +
                "COUNT(*) AS mapping_count " +
                "FROM attribute_x_attribute axa " +
                "JOIN attribute a_src ON a_src.ID = axa.Source_AttributeID " +
                "JOIN attribute a_tgt ON a_tgt.ID = axa.Target_AttributeID " +
                "JOIN dataset d_src ON d_src.ID = a_src.Dataset_ID " +
                "JOIN dataset d_tgt ON d_tgt.ID = a_tgt.Dataset_ID " +
                "JOIN system s ON s.id = d_tgt.MasterSource AND s.Deleted_Datetime IS NULL " +
                "JOIN system s2 ON s2.id = d_src.MasterSource AND s2.Deleted_Datetime IS NULL " +
                "WHERE axa.Relation_Method IS NULL " +
                "AND (s.id = ? OR s2.id = ?) " +
                "GROUP BY s2.Name, s2.id, s.Name, s.id " +
                "ORDER BY mapping_count DESC";
        
        Map<String, Integer> preferredSourceByPair = new HashMap<>();
        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            ps.setInt(2, systemId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int sourceId = rs.getInt("source_system_id");
                    int targetId = rs.getInt("target_system_id");
                    String sourceName = rs.getString("source_system");
                    String targetName = rs.getString("target_system");
                    int count = rs.getInt("mapping_count");

                    int preferredSourceId = resolvePreferredSourceSystemId(
                            conn,
                            sourceId,
                            targetId,
                            systemId,
                            preferredSourceByPair
                    );

                    int fromId = sourceId;
                    String fromName = sourceName;
                    int toId = targetId;
                    String toName = targetName;

                    // Normalize direction to the interface direction when it is uniquely defined.
                    if (preferredSourceId == targetId) {
                        fromId = targetId;
                        fromName = targetName;
                        toId = sourceId;
                        toName = sourceName;
                    }

                    String key = fromId + "->" + toId;
                    Map<String, Object> row = aggregated.get(key);
                    if (row == null) {
                        row = new HashMap<>();
                        row.put("from", fromName);
                        row.put("fromId", fromId);
                        row.put("to", toName);
                        row.put("toId", toId);
                        row.put("dataAttributes", 0);
                        aggregated.put(key, row);
                    }
                    int runningCount = ((Number) row.get("dataAttributes")).intValue();
                    row.put("dataAttributes", runningCount + count);
                }
            }
        } catch (SQLException e) {
            throw e;
        }

        List<Map<String, Object>> results = new ArrayList<>(aggregated.values());
        results.sort((a, b) -> Integer.compare(
                ((Number) b.get("dataAttributes")).intValue(),
                ((Number) a.get("dataAttributes")).intValue()
        ));
        return results;
    }

    private int resolvePreferredSourceSystemId(Connection conn,
                                               int sourceId,
                                               int targetId,
                                               int viewingSystemId,
                                               Map<String, Integer> preferredSourceByPair) throws SQLException {
        int smaller = Math.min(sourceId, targetId);
        int larger = Math.max(sourceId, targetId);
        String pairKey = smaller + ":" + larger;
        Integer cached = preferredSourceByPair.get(pairKey);
        if (cached != null) {
            return cached;
        }

        String sql = "SELECT Source_systemID, Target_systemID " +
                "FROM interface " +
                "WHERE deleted_datetime IS NULL " +
                "AND ( " +
                "   (Source_systemID = ? AND Target_systemID = ?) " +
                "   OR " +
                "   (Source_systemID = ? AND Target_systemID = ?) " +
                ")";

        int forwardCount = 0;
        int reverseCount = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, sourceId);
            ps.setInt(2, targetId);
            ps.setInt(3, targetId);
            ps.setInt(4, sourceId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int ifaceSource = rs.getInt("Source_systemID");
                    int ifaceTarget = rs.getInt("Target_systemID");
                    if (ifaceSource == sourceId && ifaceTarget == targetId) {
                        forwardCount++;
                    } else if (ifaceSource == targetId && ifaceTarget == sourceId) {
                        reverseCount++;
                    }
                }
            }
        }

        int preferredSource = sourceId;
        if (forwardCount > 0 && reverseCount == 0) {
            preferredSource = sourceId;
        } else if (reverseCount > 0 && forwardCount == 0) {
            preferredSource = targetId;
        } else if (viewingSystemId == sourceId || viewingSystemId == targetId) {
            // If interface direction is ambiguous or unavailable, keep the current system as FROM.
            preferredSource = viewingSystemId;
        }
        preferredSourceByPair.put(pairKey, preferredSource);
        return preferredSource;
    }
}

