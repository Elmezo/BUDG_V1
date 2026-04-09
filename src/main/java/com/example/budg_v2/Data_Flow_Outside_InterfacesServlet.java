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
        
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, systemId);
            ps.setInt(2, systemId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    
                    row.put("from", rs.getString("source_system"));
                    row.put("fromId", rs.getInt("source_system_id"));
                    row.put("to", rs.getString("target_system"));
                    row.put("toId", rs.getInt("target_system_id"));
                    row.put("dataAttributes", rs.getInt("mapping_count"));
                    
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            throw e;
        }

        return results;
    }
}

