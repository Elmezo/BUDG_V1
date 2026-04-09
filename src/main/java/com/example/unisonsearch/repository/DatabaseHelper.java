package com.example.unisonsearch.repository;

import com.example.budg_v2.database.DatabaseConnection;

import java.sql.*;
import java.util.*;

/**
 * Executes SQL with parameters and maps ResultSet to List<Map<String,Object>> identical to original.
 */
public class DatabaseHelper {

    public List<Map<String, Object>> executeQuery(String sql, List<Object> parameters) throws SQLException {
        // Validate SQL string before execution
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("Cannot execute empty or null SQL query");
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (parameters != null) {
                for (int i = 0; i < parameters.size(); i++) {
                    Object param = parameters.get(i);
                    if (param instanceof Integer ii) {
                        ps.setInt(i + 1, ii);
                    } else if (param instanceof String s) {
                        ps.setString(i + 1, s);
                    } else {
                        ps.setObject(i + 1, param);
                    }
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = toList(rs);
                return rows;
            }
        }
    }

    private List<Map<String, Object>> toList(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        int columnCount = rs.getMetaData().getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = rs.getMetaData().getColumnLabel(i);
                Object value = rs.getObject(i);
                row.put(columnName, normalizeValue(value));
            }
            list.add(row);
        }
        return list;
    }

    private Object normalizeValue(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.LocalDateTime ldt) return ldt.toString();
        if (value instanceof java.time.LocalDate ld) return ld.toString();
        if (value instanceof java.time.LocalTime lt) return lt.toString();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant().toString();
        if (value instanceof java.sql.Date sd) return sd.toLocalDate().toString();
        if (value instanceof java.sql.Time st) return st.toLocalTime().toString();
        return value;
    }

    /**
     * Query connected objects via direct foreign key relationship.
     * 
     * @param table The target table name
     * @param fkColumn The foreign key column name
     * @param ids Set of source IDs
     * @return Set of connected object IDs
     * @throws SQLException if database error occurs
     */
    public java.util.Set<Integer> queryDirectFK(String table, String fkColumn, java.util.Set<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return new java.util.HashSet<>();
        }

        // Build IN clause with placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String sql = "SELECT ID FROM " + table + " WHERE " + fkColumn + " IN (" + placeholders + ")";

        java.util.List<Object> parameters = new java.util.ArrayList<>(ids);
        java.util.List<Map<String, Object>> rows = executeQuery(sql, parameters);

        java.util.Set<Integer> result = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("ID");
            if (id instanceof Integer) {
                result.add((Integer) id);
            } else if (id instanceof Number) {
                result.add(((Number) id).intValue());
            }
        }
        return result;
    }

    /**
     * Query connected objects via junction table.
     * 
     * @param junctionTable The junction table name
     * @param sourceCol The source column name in junction table
     * @param targetCol The target column name in junction table
     * @param ids Set of source IDs
     * @return Set of connected object IDs (empty set if table doesn't exist or error occurs)
     */
    public java.util.Set<Integer> queryJunctionTable(String junctionTable, String sourceCol, String targetCol, java.util.Set<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.HashSet<>();
        }

        try {
            // Check if table exists first
            if (!tableExists(junctionTable)) {
                // Table doesn't exist - return empty set instead of throwing error
                return new java.util.HashSet<>();
            }

            // Build IN clause with placeholders
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql = "SELECT DISTINCT " + targetCol + " FROM " + junctionTable + 
                         " WHERE " + sourceCol + " IN (" + placeholders + ")";

            java.util.List<Object> parameters = new java.util.ArrayList<>(ids);
            java.util.List<Map<String, Object>> rows = executeQuery(sql, parameters);

            java.util.Set<Integer> result = new java.util.HashSet<>();
            for (Map<String, Object> row : rows) {
                Object id = row.get(targetCol);
                if (id instanceof Integer) {
                    result.add((Integer) id);
                } else if (id instanceof Number) {
                    result.add(((Number) id).intValue());
                }
            }
            return result;
        } catch (SQLException e) {
            // If table doesn't exist or any other SQL error, return empty set
            // This allows the system to continue with other relationships
            return new java.util.HashSet<>();
        }
    }

    /**
     * Check if a table exists in the database.
     * 
     * @param tableName The table name to check
     * @return true if table exists, false otherwise
     */
    private boolean tableExists(String tableName) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Query connected objects via reverse foreign key relationship.
     * Finds objects in target table that reference the source IDs.
     * 
     * @param table The target table name
     * @param fkColumn The foreign key column name in target table
     * @param ids Set of source IDs
     * @return Set of connected object IDs
     * @throws SQLException if database error occurs
     */
    public java.util.Set<Integer> queryReverseFK(String table, String fkColumn, java.util.Set<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) {
            return new java.util.HashSet<>();
        }

        // Build IN clause with placeholders
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String sql = "SELECT ID FROM " + table + " WHERE " + fkColumn + " IN (" + placeholders + ")";

        java.util.List<Object> parameters = new java.util.ArrayList<>(ids);
        java.util.List<Map<String, Object>> rows = executeQuery(sql, parameters);

        java.util.Set<Integer> result = new java.util.HashSet<>();
        for (Map<String, Object> row : rows) {
            Object id = row.get("ID");
            if (id instanceof Integer) {
                result.add((Integer) id);
            } else if (id instanceof Number) {
                result.add(((Number) id).intValue());
            }
        }
        return result;
    }
}


