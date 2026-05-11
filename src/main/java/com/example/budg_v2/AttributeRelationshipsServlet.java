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
import java.util.logging.Level;

@WebServlet(name = "AttributeRelationshipsServlet", urlPatterns = {"/api/attribute-relationships"})
public class AttributeRelationshipsServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(AttributeRelationshipsServlet.class.getName());
    private final Gson gson = new Gson();

    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LOGGER.info("========== Attribute Relationships Request Started ==========");
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        CorsUtil.setCorsHeaders(resp);

        String sourceSystemParam = req.getParameter("sourceSystem");
        String targetSystemParam = req.getParameter("targetSystem");
        boolean normalizeDirection = Boolean.parseBoolean(req.getParameter("normalizeDirection"));
        
        LOGGER.info("Source System: " + sourceSystemParam + ", Target System: " + targetSystemParam);

        if (sourceSystemParam == null || targetSystemParam == null) {
            LOGGER.warning("Missing required parameters");
            resp.getWriter().write(gson.toJson(new ArrayList<>()));
            return;
        }

        try {
            int sourceSystemId = Integer.parseInt(sourceSystemParam);
            int targetSystemId = Integer.parseInt(targetSystemParam);
            
            List<Map<String, Object>> results = getAttributeRelationships(sourceSystemId, targetSystemId, normalizeDirection);
            LOGGER.info("Found " + results.size() + " relationships");
            resp.getWriter().write(gson.toJson(results));
            
        } catch (NumberFormatException e) {
            LOGGER.log(Level.SEVERE, "Invalid system ID format", e);
            resp.getWriter().write(gson.toJson(new ArrayList<>()));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "SQL Error while fetching attribute relationships", e);
            resp.getWriter().write(gson.toJson(new ArrayList<>()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in Attribute Relationships servlet", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error");
            error.put("message", e.getMessage());
            resp.getWriter().write(gson.toJson(error));
        }
        
        LOGGER.info("========== Attribute Relationships Request Ended ==========");
    }

    private List<Map<String, Object>> getAttributeRelationships(int sourceSystemId, int targetSystemId, boolean normalizeDirection) throws SQLException {
        String sql = "SELECT " +
                "rt.PrimaryName AS relationshipType, " +
                "s_src.ID AS sourceSystemIdFromRow, " +
                "s_tgt.ID AS targetSystemIdFromRow, " +
                "a_src.ID AS sourceAttributeId, " +
                "a_src.PrimaryName AS sourceAttribute, " +
                "a_src.Definition AS sourceAttributeDescription, " +
                "a_src.DataLength AS sourceDataLength, " +
                "d_src.ID AS sourceDatasetId, " +
                "d_src.PrimaryName AS sourceDataSet, " +
                "d_src.RefNumber AS sourceRef, " +
                "adt_src.PrimaryName AS sourceDataType, " +
                "ae_src.PrimaryName AS sourceEditability, " +
                "aer_src.PrimaryName AS sourceEditabilityRole, " +
                "g_src.Name AS sourceGlossary, " +
                "g_src.Description AS sourceGlossaryDescription, " +
                "a_src.Is_Mandatory AS sourceMandatory, " +
                "ao_src.PrimaryName AS sourceOrigination, " +
                "a_tgt.ID AS targetAttributeId, " +
                "a_tgt.PrimaryName AS targetAttribute, " +
                "a_tgt.Definition AS targetAttributeDescription, " +
                "a_tgt.DataLength AS targetDataLength, " +
                "d_tgt.ID AS targetDatasetId, " +
                "d_tgt.PrimaryName AS targetDataSet, " +
                "d_tgt.RefNumber AS targetRef, " +
                "adt_tgt.PrimaryName AS targetDataType, " +
                "ae_tgt.PrimaryName AS targetEditability, " +
                "aer_tgt.PrimaryName AS targetEditabilityRole, " +
                "g_tgt.Name AS targetGlossary, " +
                "g_tgt.Description AS targetGlossaryDescription, " +
                "a_tgt.Is_Mandatory AS targetMandatory, " +
                "ao_tgt.PrimaryName AS targetOrigination " +
                "FROM attribute_x_attribute axa " +
                "JOIN attribute a_src ON a_src.ID = axa.Source_AttributeID " +
                "JOIN attribute a_tgt ON a_tgt.ID = axa.Target_AttributeID " +
                "JOIN dataset d_src ON d_src.ID = a_src.Dataset_ID " +
                "JOIN dataset d_tgt ON d_tgt.ID = a_tgt.Dataset_ID " +
                "JOIN system s_src ON s_src.ID = d_src.MasterSource " +
                "JOIN system s_tgt ON s_tgt.ID = d_tgt.MasterSource " +
                "LEFT JOIN attribute_origination ao_src ON ao_src.ID = a_src.Origination " +
                "LEFT JOIN attribute_origination ao_tgt ON ao_tgt.ID = a_tgt.Origination " +
                "LEFT JOIN attribute_x_attribute_relationtype rt ON rt.ID = axa.Relation_Type " +
                "LEFT JOIN attribute_datatype adt_src ON adt_src.ID = a_src.Data_type_ID " +
                "LEFT JOIN attribute_datatype adt_tgt ON adt_tgt.ID = a_tgt.Data_type_ID " +
                "LEFT JOIN attribute_editability ae_src ON ae_src.ID = a_src.Editability " +
                "LEFT JOIN attribute_editability ae_tgt ON ae_tgt.ID = a_tgt.Editability " +
                "LEFT JOIN attribute_edit_role aer_src ON aer_src.ID = a_src.Editability_role " +
                "LEFT JOIN attribute_edit_role aer_tgt ON aer_tgt.ID = a_tgt.Editability_role " +
                "LEFT JOIN glossary g_src ON g_src.ID = a_src.Glossary_ID " +
                "LEFT JOIN glossary g_tgt ON g_tgt.ID = a_tgt.Glossary_ID " +
                "WHERE (" +
                "   (s_src.ID = ? AND s_tgt.ID = ?) " +
                (normalizeDirection ? "   OR (s_src.ID = ? AND s_tgt.ID = ?) " : "") +
                ") " +
                "AND axa.Relation_Method IS NULL " +
                "ORDER BY d_src.PrimaryName, a_src.PrimaryName";

        LOGGER.info("Executing attribute relationships query");
        LOGGER.fine("SQL: " + sql);

        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            LOGGER.info("Database connection established");
            ps.setInt(1, sourceSystemId);
            ps.setInt(2, targetSystemId);
            if (normalizeDirection) {
                ps.setInt(3, targetSystemId);
                ps.setInt(4, sourceSystemId);
            }
            LOGGER.info("Parameters set: sourceSystem=" + sourceSystemId + ", targetSystem=" + targetSystemId);

            try (ResultSet rs = ps.executeQuery()) {
                LOGGER.info("Query executed, processing results...");
                int rowCount = 0;
                
                while (rs.next()) {
                    rowCount++;
                    Map<String, Object> row = new HashMap<>();
                    boolean isReversedForRequestedDirection = normalizeDirection
                            && rs.getInt("sourceSystemIdFromRow") == targetSystemId
                            && rs.getInt("targetSystemIdFromRow") == sourceSystemId;
                    
                    row.put("relationshipType", rs.getString("relationshipType"));

                    if (!isReversedForRequestedDirection) {
                        row.put("sourceAttributeId", rs.getInt("sourceAttributeId"));
                        row.put("sourceAttribute", rs.getString("sourceAttribute"));
                        row.put("sourceAttributeDescription", rs.getString("sourceAttributeDescription"));
                        row.put("sourceDataLength", rs.getInt("sourceDataLength"));
                        row.put("sourceDatasetId", rs.getInt("sourceDatasetId"));
                        row.put("sourceDataSet", rs.getString("sourceDataSet"));
                        row.put("sourceRef", rs.getString("sourceRef"));
                        row.put("sourceDataType", rs.getString("sourceDataType"));
                        row.put("sourceEditability", rs.getString("sourceEditability"));
                        row.put("sourceEditabilityRole", rs.getString("sourceEditabilityRole"));
                        row.put("sourceGlossary", rs.getString("sourceGlossary"));
                        row.put("sourceGlossaryDescription", rs.getString("sourceGlossaryDescription"));
                        row.put("sourceMandatory", rs.getInt("sourceMandatory"));
                        row.put("sourceOrigination", rs.getString("sourceOrigination"));

                        row.put("targetAttributeId", rs.getInt("targetAttributeId"));
                        row.put("targetAttribute", rs.getString("targetAttribute"));
                        row.put("targetAttributeDescription", rs.getString("targetAttributeDescription"));
                        row.put("targetDataLength", rs.getInt("targetDataLength"));
                        row.put("targetDatasetId", rs.getInt("targetDatasetId"));
                        row.put("targetDataSet", rs.getString("targetDataSet"));
                        row.put("targetRef", rs.getString("targetRef"));
                        row.put("targetDataType", rs.getString("targetDataType"));
                        row.put("targetEditability", rs.getString("targetEditability"));
                        row.put("targetEditabilityRole", rs.getString("targetEditabilityRole"));
                        row.put("targetGlossary", rs.getString("targetGlossary"));
                        row.put("targetGlossaryDescription", rs.getString("targetGlossaryDescription"));
                        row.put("targetMandatory", rs.getInt("targetMandatory"));
                        row.put("targetOrigination", rs.getString("targetOrigination"));
                    } else {
                        row.put("sourceAttributeId", rs.getInt("targetAttributeId"));
                        row.put("sourceAttribute", rs.getString("targetAttribute"));
                        row.put("sourceAttributeDescription", rs.getString("targetAttributeDescription"));
                        row.put("sourceDataLength", rs.getInt("targetDataLength"));
                        row.put("sourceDatasetId", rs.getInt("targetDatasetId"));
                        row.put("sourceDataSet", rs.getString("targetDataSet"));
                        row.put("sourceRef", rs.getString("targetRef"));
                        row.put("sourceDataType", rs.getString("targetDataType"));
                        row.put("sourceEditability", rs.getString("targetEditability"));
                        row.put("sourceEditabilityRole", rs.getString("targetEditabilityRole"));
                        row.put("sourceGlossary", rs.getString("targetGlossary"));
                        row.put("sourceGlossaryDescription", rs.getString("targetGlossaryDescription"));
                        row.put("sourceMandatory", rs.getInt("targetMandatory"));
                        row.put("sourceOrigination", rs.getString("targetOrigination"));

                        row.put("targetAttributeId", rs.getInt("sourceAttributeId"));
                        row.put("targetAttribute", rs.getString("sourceAttribute"));
                        row.put("targetAttributeDescription", rs.getString("sourceAttributeDescription"));
                        row.put("targetDataLength", rs.getInt("sourceDataLength"));
                        row.put("targetDatasetId", rs.getInt("sourceDatasetId"));
                        row.put("targetDataSet", rs.getString("sourceDataSet"));
                        row.put("targetRef", rs.getString("sourceRef"));
                        row.put("targetDataType", rs.getString("sourceDataType"));
                        row.put("targetEditability", rs.getString("sourceEditability"));
                        row.put("targetEditabilityRole", rs.getString("sourceEditabilityRole"));
                        row.put("targetGlossary", rs.getString("sourceGlossary"));
                        row.put("targetGlossaryDescription", rs.getString("sourceGlossaryDescription"));
                        row.put("targetMandatory", rs.getInt("sourceMandatory"));
                        row.put("targetOrigination", rs.getString("sourceOrigination"));
                    }
                    
                    results.add(row);
                }
                
                LOGGER.info("Total rows processed: " + rowCount);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "SQL Exception in getAttributeRelationships", e);
            LOGGER.severe("Failed SQL: " + sql);
            throw e;
        }

        return results;
    }
}

