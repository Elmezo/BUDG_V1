package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;

/**
 * DAO for process_definition_bpmn table operations
 */
public class BpmnContentDAO {

    /**
     * Save or update BPMN XML content
     */
    public void saveOrUpdate(int processDefinitionId, String xmlContent) throws SQLException {
        // First try to update
        String updateSql = "UPDATE process_definition_bpmn SET Xml_Content = ?, Updated_At = NOW() " +
                "WHERE Process_Definition_ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(updateSql)) {

            stmt.setString(1, xmlContent);
            stmt.setInt(2, processDefinitionId);

            int updated = stmt.executeUpdate();

            // If no rows updated, insert new record
            if (updated == 0) {
                String insertSql = "INSERT INTO process_definition_bpmn (Process_Definition_ID, Xml_Content, " +
                        "Created_At, Updated_At) VALUES (?, ?, NOW(), NOW())";

                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setInt(1, processDefinitionId);
                    insertStmt.setString(2, xmlContent);
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    /**
     * Get BPMN XML content by process definition ID
     */
    public String getXmlContent(int processDefinitionId) throws SQLException {
        String sql = "SELECT Xml_Content FROM process_definition_bpmn WHERE Process_Definition_ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, processDefinitionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Xml_Content");
                }
            }
        }
        return null;
    }

    /**
     * Delete BPMN content
     */
    public void delete(int processDefinitionId) throws SQLException {
        String sql = "DELETE FROM process_definition_bpmn WHERE Process_Definition_ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, processDefinitionId);
            stmt.executeUpdate();
        }
    }
}
