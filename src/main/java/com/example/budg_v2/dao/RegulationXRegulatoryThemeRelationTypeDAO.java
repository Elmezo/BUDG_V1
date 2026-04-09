package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.RegulationXRegulatoryThemeRelationType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RegulationXRegulatoryThemeRelationTypeDAO {

    private static final String SELECT_ALL = "SELECT * FROM regulation_x_regulatorytheme_relationtype WHERE DeleteDatetime IS NULL ORDER BY ID";
    private static final String SELECT_BY_ID = "SELECT * FROM regulation_x_regulatorytheme_relationtype WHERE ID = ? AND DeleteDatetime IS NULL";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, PrimaryName, Description FROM regulation_x_regulatorytheme_relationtype WHERE DeleteDatetime IS NULL ORDER BY PrimaryName";

    public List<RegulationXRegulatoryThemeRelationType> getAllRelationTypes() throws SQLException {
        List<RegulationXRegulatoryThemeRelationType> relationTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                relationTypes.add(mapResultSetToRelationType(rs));
            }
        }
        return relationTypes;
    }

    public RegulationXRegulatoryThemeRelationType getRelationTypeById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToRelationType(rs);
            }
        }
        return null;
    }

    public List<RegulationXRegulatoryThemeRelationType> getAllRelationTypesForDropdown() throws SQLException {
        List<RegulationXRegulatoryThemeRelationType> relationTypes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

            while (rs.next()) {
                RegulationXRegulatoryThemeRelationType relationType = new RegulationXRegulatoryThemeRelationType();
                relationType.setId(rs.getInt("ID"));
                relationType.setPrimaryName(rs.getString("PrimaryName"));
                relationType.setDescription(rs.getString("Description"));
                relationTypes.add(relationType);
            }
        }
        return relationTypes;
    }

    private RegulationXRegulatoryThemeRelationType mapResultSetToRelationType(ResultSet rs) throws SQLException {
        RegulationXRegulatoryThemeRelationType relationType = new RegulationXRegulatoryThemeRelationType();
        relationType.setId(rs.getInt("ID"));
        relationType.setPrimaryName(rs.getString("PrimaryName"));
        relationType.setDescription(rs.getString("Description"));
        
        int priority = rs.getInt("Priority");
        if (!rs.wasNull()) {
            relationType.setPriority(priority);
        }
        
        relationType.setReverseName(rs.getString("ReverseName"));
        
        Timestamp lastUpdateDatetime = rs.getTimestamp("LastUpdateDatetime");
        if (lastUpdateDatetime != null) {
            relationType.setLastUpdateDatetime(lastUpdateDatetime.toLocalDateTime());
        }
        
        Timestamp deleteDatetime = rs.getTimestamp("DeleteDatetime");
        if (deleteDatetime != null) {
            relationType.setDeleteDatetime(deleteDatetime.toLocalDateTime());
        }
        
        int lastUpdateUserId = rs.getInt("LastUpdate_UserID");
        if (!rs.wasNull()) {
            relationType.setLastUpdateUserId(lastUpdateUserId);
        }
        
        return relationType;
    }
}
