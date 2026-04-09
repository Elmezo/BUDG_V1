package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Change Request audit history operations.
 * Stores and retrieves field change history for Change Requests.
 */
public class ChangeRequestHistoryDAO {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestHistoryDAO.class);
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", java.util.Locale.ENGLISH);

    /**
     * Get history records for a change request, ordered by date descending.
     */
    public List<ChangeRequestHistoryRecord> getHistoryByChangeRequestId(int crId, int page, int limit) throws SQLException {
        List<ChangeRequestHistoryRecord> records = new ArrayList<>();
        String sql = """
            SELECT id, auditidpk, field, `from`, `to`, author, date, lastChange
            FROM changerequest_audit_history
            WHERE id = ?
            ORDER BY date DESC, auditidpk DESC
            LIMIT ? OFFSET ?
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, crId);
            ps.setInt(2, limit);
            ps.setInt(3, (page - 1) * limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ChangeRequestHistoryRecord record = new ChangeRequestHistoryRecord();
                    record.setId(rs.getInt("id"));
                    record.setAuditIdPk(rs.getInt("auditidpk"));
                    record.setField(rs.getString("field"));
                    record.setFrom(rs.getString("from"));
                    record.setTo(rs.getString("to"));
                    record.setAuthor(rs.getString("author"));
                    record.setDate(formatDateForDisplay(rs.getString("date")));
                    record.setLastChange(formatDateForDisplay(rs.getString("lastChange")));
                    records.add(record);
                }
            }
        }
        return records;
    }

    /**
     * Insert a history record for a change request field change.
     */
    public void insertHistoryRecord(int crId, String field, String fromVal, String toVal, String author) throws SQLException {
        String sql = """
            INSERT INTO changerequest_audit_history (id, field, `from`, `to`, author, date, lastChange)
            VALUES (?, ?, ?, ?, ?, NOW(), NOW())
            """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, crId);
            ps.setString(2, field);
            ps.setString(3, fromVal);
            ps.setString(4, toVal);
            ps.setString(5, author != null ? author : "System");
            ps.executeUpdate();
            logger.debug("Inserted history record for CR {}: {} from '{}' to '{}'", crId, field, fromVal, toVal);
        }
    }

    /**
     * Get total count of history records for a change request.
     */
    public int getHistoryCount(int crId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM changerequest_audit_history WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, crId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    private String formatDateForDisplay(String dateString) {
        if (dateString == null || dateString.trim().isEmpty()) {
            return "";
        }
        try {
            if (dateString.matches("\\d{2}-[A-Za-z]{3}-\\d{4} \\d{2}:\\d{2}:\\d{2}")) {
                return dateString;
            }
            LocalDateTime dateTime;
            if (dateString.contains(" ")) {
                dateTime = LocalDateTime.parse(dateString,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } else {
                dateTime = LocalDateTime.parse(dateString + " 00:00:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return dateTime.format(DISPLAY_FORMATTER);
        } catch (Exception e) {
            logger.warn("Failed to format date: {} - {}", dateString, e.getMessage());
            return dateString;
        }
    }

    /**
     * Simple record for change request history.
     */
    public static class ChangeRequestHistoryRecord {
        private int id;
        private int auditIdPk;
        private String field;
        private String from;
        private String to;
        private String author;
        private String date;
        private String lastChange;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public int getAuditIdPk() { return auditIdPk; }
        public void setAuditIdPk(int auditIdPk) { this.auditIdPk = auditIdPk; }
        public String getField() { return field; }
        public void setField(String field) { this.field = field; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getLastChange() { return lastChange; }
        public void setLastChange(String lastChange) { this.lastChange = lastChange; }
    }
}
