package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.ChangeRequest;
import com.example.budg_v2.model.ChangeRequestValue;
import com.example.budg_v2.service.SegmentAccessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DAO for ChangeRequest operations
 */
public class ChangeRequestDAO {

    private static final Logger logger = LoggerFactory.getLogger(ChangeRequestDAO.class);

    /**
     * Create a new change request value entry
     */
    public Integer createChangeRequestValue(ChangeRequestValue value) throws SQLException {
        String sql = "INSERT INTO changerequest_value (Value, Type, LastUserChange, Created_At, Updated_At, CR_CurrencyID) "
                +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // First, try to get the actual enum values to verify what's available
            String enumDefinition = null;
            try {
                enumDefinition = getEnumValues(conn, "changerequest_value", "Type");
                logger.info("Database enum definition for Type column: {}", enumDefinition);
            } catch (Exception e) {
                logger.warn("Could not query enum definition: {}", e.getMessage());
            }

            stmt.setFloat(1, value.getValue());

            // Set Type value - try to use the provided type, fallback to 'Enabled' if not
            // supported
            String typeValue = value.getType();
            if (typeValue == null || typeValue.trim().isEmpty()) {
                typeValue = "Enabled"; // Default fallback
            } else {
                typeValue = typeValue.trim();
            }

            // Log the value we're trying to insert for debugging
            logger.info("Inserting changerequest_value with Type: '{}'", typeValue);

            // Try to insert the enum value directly
            stmt.setString(2, typeValue);

            stmt.setInt(3, value.getLastUserChange());
            stmt.setTimestamp(4, Timestamp.valueOf(value.getCreatedAt()));
            stmt.setTimestamp(5, Timestamp.valueOf(value.getUpdatedAt()));

            if (value.getCrCurrencyId() != null) {
                stmt.setInt(6, value.getCrCurrencyId());
            } else {
                stmt.setNull(6, Types.INTEGER);
            }

            try {
                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating change request value failed, no rows affected.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        return generatedKeys.getInt(1);
                    } else {
                        throw new SQLException("Creating change request value failed, no ID obtained.");
                    }
                }
            } catch (SQLException e) {
                // If error is about Type enum, log more details
                if (e.getMessage() != null && e.getMessage().contains("Data truncated for column 'Type'")) {
                    logger.error("Type enum error when trying to insert: '{}'", typeValue);
                    logger.error("Error message: {}", e.getMessage());
                    logger.error("Database enum definition: {}", enumDefinition);

                    throw new SQLException("Database error: Cannot insert Type value '" + typeValue +
                            "'. Database enum: " + enumDefinition +
                            ". Error: " + e.getMessage(), e);
                }
                throw e;
            }
        }
    }

    /**
     * Update an existing change request value entry
     */
    public boolean updateChangeRequestValue(ChangeRequestValue value) throws SQLException {
        if (value.getId() == null) {
            throw new SQLException("Cannot update change request value: ID is required");
        }
        
        String sql = "UPDATE changerequest_value SET Value = ?, Type = ?, LastUserChange = ?, " +
                "Updated_At = ?, CR_CurrencyID = ? WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setFloat(1, value.getValue());
            
            String typeValue = value.getType();
            if (typeValue == null || typeValue.trim().isEmpty()) {
                typeValue = "Enabled"; // Default fallback
            } else {
                typeValue = typeValue.trim();
            }
            stmt.setString(2, typeValue);
            
            stmt.setInt(3, value.getLastUserChange());
            stmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            
            if (value.getCrCurrencyId() != null) {
                stmt.setInt(5, value.getCrCurrencyId());
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            
            stmt.setInt(6, value.getId());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Delete a change request value entry
     */
    public boolean deleteChangeRequestValue(Integer valueId) throws SQLException {
        if (valueId == null) {
            throw new SQLException("Cannot delete change request value: ID is required");
        }
        
        String sql = "DELETE FROM changerequest_value WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, valueId);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Create a new change request
     */
    public Integer createChangeRequest(ChangeRequest changeRequest) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Ensure process_definition_id column exists
            ensureProcessDefinitionIdColumn(conn);

            // Check if process_definition_id column exists
            boolean hasProcessDefinitionIdColumn = false;
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "changerequest",
                    "process_definition_id")) {
                hasProcessDefinitionIdColumn = rs.next();
            } catch (SQLException e) {
                logger.debug("Could not check for process_definition_id column: {}", e.getMessage());
            }

            String sql;
            if (hasProcessDefinitionIdColumn) {
                sql = "INSERT INTO changerequest (PrimaryName, Parent_ID, Reference, Summary, " +
                        "LastUserChange, Created_By, Created_At, Updated_At, CR_StatusID, CR_TypeID, " +
                        "CR_SeverityID, CR_UrgencyID, Estimated_BenefitID, Estimated_CostID, " +
                        "Visibility, Mandatory_Workflow, process_definition_id, Delta) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO changerequest (PrimaryName, Parent_ID, Reference, Summary, " +
                        "LastUserChange, Created_By, Created_At, Updated_At, CR_StatusID, CR_TypeID, " +
                        "CR_SeverityID, CR_UrgencyID, Estimated_BenefitID, Estimated_CostID, " +
                        "Visibility, Mandatory_Workflow, Delta) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                stmt.setString(1, changeRequest.getPrimaryName());

                if (changeRequest.getParentId() != null) {
                    stmt.setInt(2, changeRequest.getParentId());
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }

                stmt.setString(3, changeRequest.getReference());
                stmt.setString(4, changeRequest.getSummary());
                stmt.setInt(5, changeRequest.getLastUserChange());
                // IMPORTANT: Use the actual Created_By value from the ChangeRequest object
                // This should be the user ID who created the manual CR, not a default value
                Integer createdBy = changeRequest.getCreatedBy();
                logger.info("[ChangeRequestDAO] Creating CR with Created_By: {} (from changeRequest.getCreatedBy())", createdBy);
                stmt.setInt(6, createdBy);
                stmt.setTimestamp(7, Timestamp.valueOf(changeRequest.getCreatedAt()));
                stmt.setTimestamp(8, Timestamp.valueOf(changeRequest.getUpdatedAt()));

                if (changeRequest.getCrStatusId() != null) {
                    stmt.setInt(9, changeRequest.getCrStatusId());
                } else {
                    stmt.setNull(9, Types.INTEGER);
                }

                if (changeRequest.getCrTypeId() != null) {
                    stmt.setInt(10, changeRequest.getCrTypeId());
                } else {
                    stmt.setNull(10, Types.INTEGER);
                }
                if (changeRequest.getCrSeverityId() != null) {
                    stmt.setInt(11, changeRequest.getCrSeverityId());
                } else {
                    stmt.setNull(11, Types.INTEGER);
                }
                if (changeRequest.getCrUrgencyId() != null) {
                    stmt.setInt(12, changeRequest.getCrUrgencyId());
                } else {
                    stmt.setNull(12, Types.INTEGER);
                }

                if (changeRequest.getEstimatedBenefitId() != null) {
                    stmt.setInt(13, changeRequest.getEstimatedBenefitId());
                } else {
                    stmt.setNull(13, Types.INTEGER);
                }

                if (changeRequest.getEstimatedCostId() != null) {
                    stmt.setInt(14, changeRequest.getEstimatedCostId());
                } else {
                    stmt.setNull(14, Types.INTEGER);
                }

                if (changeRequest.getVisibility() != null) {
                    stmt.setInt(15, changeRequest.getVisibility());
                } else {
                    stmt.setNull(15, Types.INTEGER);
                }

                if (changeRequest.getMandatoryWorkflow() != null) {
                    stmt.setBoolean(16, changeRequest.getMandatoryWorkflow());
                } else {
                    stmt.setNull(16, Types.BOOLEAN);
                }

                int deltaIndex = 17;
                if (hasProcessDefinitionIdColumn) {
                    if (changeRequest.getProcessDefinitionId() != null) {
                        stmt.setInt(17, changeRequest.getProcessDefinitionId());
                    } else {
                        stmt.setNull(17, Types.INTEGER);
                    }
                    deltaIndex = 18;
                }

                stmt.setString(deltaIndex, changeRequest.getDelta());

                int affectedRows = stmt.executeUpdate();
                if (affectedRows == 0) {
                    throw new SQLException("Creating change request failed, no rows affected.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int newId = generatedKeys.getInt(1);
                        insertInitialHistory(newId, changeRequest, conn);
                        return newId;
                    } else {
                        throw new SQLException("Creating change request failed, no ID obtained.");
                    }
                }
            }
        }
    }

    /**
     * Insert initial history records when a change request is created.
     */
    private void insertInitialHistory(int crId, ChangeRequest cr, Connection conn) {
        try {
            ChangeRequestHistoryDAO historyDAO = new ChangeRequestHistoryDAO();
            String author = getPersonNameForHistory(conn, cr.getCreatedBy());
            if (author == null) author = "System";

            insertHistoryRecord(historyDAO, crId, "Reference", null, cr.getReference(), author);
            insertHistoryRecord(historyDAO, crId, "Title", null, cr.getPrimaryName(), author);
            insertHistoryRecord(historyDAO, crId, "Summary", null, cr.getSummary(), author);
            insertHistoryRecord(historyDAO, crId, "Type", null, getLookupName(conn, "changerequest_type", cr.getCrTypeId()), author);
            insertHistoryRecord(historyDAO, crId, "Severity", null, getLookupName(conn, "changerequest_severity", cr.getCrSeverityId()), author);
            insertHistoryRecord(historyDAO, crId, "Urgency", null, getLookupName(conn, "changerequest_urgency", cr.getCrUrgencyId()), author);
            insertHistoryRecord(historyDAO, crId, "Status", null, getLookupName(conn, "changerequeststatus", cr.getCrStatusId()), author);
        } catch (SQLException e) {
            logger.warn("Could not insert initial history for CR {}: {}", crId, e.getMessage());
        }
    }

    private void insertHistoryRecord(ChangeRequestHistoryDAO dao, int crId, String field, String fromVal, String toVal, String author) throws SQLException {
        if (toVal != null || fromVal != null) {
            dao.insertHistoryRecord(crId, field, fromVal, toVal, author);
        }
    }

    private String getLookupName(Connection conn, String table, Integer id) throws SQLException {
        if (id == null) return null;
        String sql = "SELECT PrimaryName FROM " + table + " WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("PrimaryName") : null;
            }
        }
    }

    private String getPersonNameForHistory(Connection conn, Integer personId) throws SQLException {
        if (personId == null) return "System";
        String sql = "SELECT TRIM(CONCAT(COALESCE(First_Name,''), ' ', COALESCE(Last_Name,''))) AS displayName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("displayName");
                    return (name != null && !name.trim().isEmpty()) ? name.trim() : "System";
                }
                return "System";
            }
        }
    }

    /**
     * Get change request by ID
     */
    public ChangeRequest getChangeRequestById(Integer id) throws SQLException {
        String sql = "SELECT * FROM changerequest WHERE ID = ? AND Deleted_At IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ChangeRequest cr = mapResultSetToChangeRequest(rs);
                    // Load related data (status names, etc.)
                    loadRelatedData(cr, conn);
                    return cr;
                }
            }
        }
        return null;
    }

    /**
     * Get status ID by name from changerequeststatus table (case-insensitive LIKE
     * search)
     */
    public Integer getStatusIdByName(String name) throws SQLException {
        String sql = "SELECT ID FROM changerequeststatus WHERE PrimaryName LIKE ?";
        logger.debug("🔍 [getStatusIdByName] Looking for status: '{}'", name);
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            String searchPattern = "%" + name + "%";
            stmt.setString(1, searchPattern);
            logger.debug("   📝 SQL: {} with pattern: '{}'", sql, searchPattern);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Integer statusId = rs.getInt("ID");
                    logger.debug("   ✅ Found status '{}' with ID: {}", name, statusId);
                    return statusId;
                } else {
                    logger.warn("   ⚠️  Status '{}' not found in changerequeststatus table", name);
                    // Debug: List all available statuses
                    try {
                        String debugSql = "SELECT ID, PrimaryName FROM changerequeststatus ORDER BY ID";
                        try (PreparedStatement debugStmt = conn.prepareStatement(debugSql);
                             ResultSet debugRs = debugStmt.executeQuery()) {
                            logger.warn("   📋 Available statuses in database:");
                            while (debugRs.next()) {
                                logger.warn("      └─ ID: {}, Name: '{}'", 
                                    debugRs.getInt("ID"), debugRs.getString("PrimaryName"));
                            }
                        }
                    } catch (SQLException e) {
                        logger.debug("   Could not list available statuses: {}", e.getMessage());
                    }
                }
            }
        }
        return null; // Status not found
    }

    /**
     * Get status name by ID from changerequeststatus table
     */
    public String getStatusNameById(Integer statusId) throws SQLException {
        if (statusId == null) {
            return null;
        }
        String sql = "SELECT PrimaryName FROM changerequeststatus WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, statusId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("PrimaryName");
                }
            }
        }
        return null; // Status not found
    }

    /**
     * Check if there are any active (non-completed, non-cancelled) change requests
     * for the given reference string
     * 
     * @param reference The CR reference (e.g., "Dataset 15")
     * @return true if there are active CRs, false otherwise
     */
    public boolean hasActiveChangeRequests(String reference) throws SQLException {
        if (reference == null || reference.trim().isEmpty()) {
            return false;
        }

        // Get status IDs for Completed and Cancelled
        Integer completedStatusId = getStatusIdByName("Completed");
        Integer cancelledStatusId = getStatusIdByName("Cancelled");
        if (cancelledStatusId == null) {
            cancelledStatusId = getStatusIdByName("Canceled"); // Alternative spelling
        }

        // Query to check for active CRs
        String sql = "SELECT COUNT(*) as count " +
                "FROM changerequest cr " +
                "LEFT JOIN changerequeststatus cs ON cr.CR_StatusID = cs.ID " +
                "WHERE cr.Reference = ? " +
                "AND cr.Deleted_At IS NULL " +
                "AND (" +
                "  cr.CR_StatusID IS NULL " + // NULL status is considered active
                "  OR (" +
                "    (cr.CR_StatusID != ? OR ? IS NULL) " +
                "    AND (cr.CR_StatusID != ? OR ? IS NULL) " +
                "    AND (cs.PrimaryName IS NULL " +
                "         OR (UPPER(cs.PrimaryName) NOT LIKE '%COMPLETED%' " +
                "             AND UPPER(cs.PrimaryName) NOT LIKE '%CANCELLED%' " +
                "             AND UPPER(cs.PrimaryName) NOT LIKE '%CANCELED%'))" +
                "  )" +
                ")";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            stmt.setObject(2, completedStatusId);
            stmt.setObject(3, completedStatusId);
            stmt.setObject(4, cancelledStatusId);
            stmt.setObject(5, cancelledStatusId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("count");
                    return count > 0;
                }
            }
        }

        return false;
    }

    /**
     * Update CR on workflow start: link instance, set status to Running.
     * ⚠️ CRITICAL: Do NOT change Mandatory_Workflow - preserve original value (auto vs manual CR)
     * Only auto-created CRs have Mandatory_Workflow=1, manual CRs should remain 0 or NULL
     */
    public boolean updateChangeRequestOnWorkflowStart(int changeRequestId, int workflowInstanceId) throws SQLException {
        logger.info("═══════════════════════════════════════════════════════════════");
        logger.info("🔄 [UPDATE CR ON WORKFLOW START] Starting update for CR: {}, Instance: {}", changeRequestId, workflowInstanceId);
        logger.info("═══════════════════════════════════════════════════════════════");
        
        // ⚠️ CRITICAL: Check current Mandatory_Workflow value to preserve it
        // Manual CRs should NOT be converted to auto CRs when workflow starts
        Boolean currentMandatoryWorkflow = null;
        try (Connection conn = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT Mandatory_Workflow FROM changerequest WHERE ID = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, changeRequestId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        int mandatoryWorkflow = rs.getInt("Mandatory_Workflow");
                        if (!rs.wasNull()) {
                            currentMandatoryWorkflow = (mandatoryWorkflow == 1);
                        }
                        logger.info("   📋 Current Mandatory_Workflow value: {} (will be preserved)", 
                            currentMandatoryWorkflow != null ? (currentMandatoryWorkflow ? "1 (auto CR)" : "0 (manual CR)") : "NULL");
                    }
                }
            }
        }
        
        // Get 'Running' status ID
        Integer runningStatusId = getStatusIdByName("Running");
        logger.info("   📋 Running status ID lookup result: {}", runningStatusId);

        // Build SQL - preserve Mandatory_Workflow, only update if it was originally 1 (auto CR)
        StringBuilder sql = new StringBuilder(
                "UPDATE changerequest SET Process_InstanceID = ?, Updated_At = ?");
        
        // Only set Mandatory_Workflow=1 if it was already 1 (auto CR)
        // Manual CRs should remain 0 or NULL
        if (currentMandatoryWorkflow != null && currentMandatoryWorkflow) {
            sql.append(", Mandatory_Workflow = 1");
            logger.info("   ✅ Preserving Mandatory_Workflow=1 (auto CR)");
        } else {
            logger.info("   ✅ Preserving Mandatory_Workflow={} (manual CR - not changing to auto)", 
                currentMandatoryWorkflow != null ? "0" : "NULL");
        }
        
        if (runningStatusId != null) {
            sql.append(", CR_StatusID = ?");
            logger.info("   ✅ Running status found (ID: {}) - will update CR_StatusID", runningStatusId);
        } else {
            logger.error("   ❌ [CRITICAL] Status 'Running' not found in database! CR status will NOT be updated to Running.");
            logger.error("   ⚠️  This means the CR will remain in 'Pending Start' status even after workflow starts.");
            logger.error("   ⚠️  Please check the changerequeststatus table for a status named 'Running'.");
        }
        sql.append(" WHERE ID = ?");
        
        logger.info("   📝 SQL Query: {}", sql.toString());

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            stmt.setInt(paramIndex++, workflowInstanceId);
            logger.info("   📌 Parameter {}: Process_InstanceID = {}", paramIndex - 1, workflowInstanceId);
            
            stmt.setTimestamp(paramIndex++, Timestamp.valueOf(LocalDateTime.now()));
            logger.info("   📌 Parameter {}: Updated_At = {}", paramIndex - 1, LocalDateTime.now());

            if (runningStatusId != null) {
                stmt.setInt(paramIndex++, runningStatusId);
                logger.info("   📌 Parameter {}: CR_StatusID = {}", paramIndex - 1, runningStatusId);
            }

            stmt.setInt(paramIndex, changeRequestId);
            logger.info("   📌 Parameter {}: ID (WHERE) = {}", paramIndex, changeRequestId);

            int rows = stmt.executeUpdate();
            logger.info("═══════════════════════════════════════════════════════════════");
            if (rows > 0 && runningStatusId != null) {
                try {
                    ChangeRequestHistoryDAO historyDAO = new ChangeRequestHistoryDAO();
                    String oldStatus = getLookupName(conn, "changerequeststatus",
                            getStatusIdByName("Pending Start"));
                    String newStatus = getLookupName(conn, "changerequeststatus", runningStatusId);
                    historyDAO.insertHistoryRecord(changeRequestId, "Status", oldStatus, newStatus, "System");
                } catch (SQLException e) {
                    logger.warn("Could not insert history for workflow start: {}", e.getMessage());
                }
            }
            if (rows > 0) {
                logger.info("✅ [SUCCESS] Updated CR {} on workflow start:", changeRequestId);
                logger.info("   └─ Process_InstanceID: {}", workflowInstanceId);
                logger.info("   └─ Mandatory_Workflow: {} (preserved)", 
                    currentMandatoryWorkflow != null ? (currentMandatoryWorkflow ? "1 (auto CR)" : "0 (manual CR)") : "NULL");
                if (runningStatusId != null) {
                    logger.info("   └─ CR_StatusID: {} (Running)", runningStatusId);
                } else {
                    logger.warn("   └─ CR_StatusID: NOT UPDATED (Running status not found!)");
                }
                logger.info("   └─ Rows affected: {}", rows);
            } else {
                logger.error("❌ [FAILED] No rows were updated for CR {}! This means the CR was not found or the update failed.", changeRequestId);
            }
            logger.info("═══════════════════════════════════════════════════════════════");
            return rows > 0;
        }
    }

    /**
     * Get all change requests
     */
    public List<ChangeRequest> getAllChangeRequests() throws SQLException {
        String sql = "SELECT * FROM changerequest WHERE Deleted_At IS NULL ORDER BY Created_At DESC";
        List<ChangeRequest> changeRequests = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                ChangeRequest cr = mapResultSetToChangeRequest(rs);
                // Load related data (typeName, statusName, etc.) for display in Unison Search
                loadRelatedDataForList(cr, conn);
                changeRequests.add(cr);
            }
        }
        return changeRequests;
    }

    /**
     * Get all change requests filtered by user's segment access
     */
    public List<ChangeRequest> getAllChangeRequests(int userId) throws SQLException {
        List<ChangeRequest> allChangeRequests = getAllChangeRequests();
        if (allChangeRequests.isEmpty()) {
            return allChangeRequests;
        }

        // Get accessible change request IDs for this user
        List<Integer> allIds = allChangeRequests.stream().map(ChangeRequest::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(userId, "ChangeRequest",
                allIds);

        // Filter to only accessible change requests
        return allChangeRequests.stream()
                .filter(cr -> accessibleIds.contains(cr.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Get a single change request by ID (simplified version)
     */
    public ChangeRequest getChangeRequestById(int id) throws SQLException {
        String sql = "SELECT * FROM changerequest WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            logger.info("Executing query for change request ID: {}", id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Check if process_definition_id column exists
                    boolean hasProcessDefinitionId = false;
                    try {
                        java.sql.ResultSetMetaData metaData = rs.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            String columnName = metaData.getColumnName(i).toLowerCase();
                            if (columnName.equals("process_definition_id")) {
                                hasProcessDefinitionId = true;
                                break;
                            }
                        }
                    } catch (SQLException e) {
                        logger.debug("Could not check for process_definition_id column: {}", e.getMessage());
                    }

                    ChangeRequest cr = new ChangeRequest();
                    cr.setId(rs.getInt("ID"));
                    cr.setPrimaryName(rs.getString("PrimaryName"));
                    cr.setParentId(rs.getObject("Parent_ID", Integer.class));
                    cr.setReference(rs.getString("Reference"));
                    cr.setSummary(rs.getString("Summary"));
                    cr.setCrTypeId(rs.getObject("CR_TypeID", Integer.class));
                    cr.setCrSeverityId(rs.getObject("CR_SeverityID", Integer.class));
                    cr.setCrUrgencyId(rs.getObject("CR_UrgencyID", Integer.class));
                    cr.setCrStatusId(rs.getObject("CR_StatusID", Integer.class));
                    cr.setProcessInstanceId(rs.getObject("Process_InstanceID", Integer.class));
                    // Safely read process_definition_id (column may not exist in older databases)
                    if (hasProcessDefinitionId) {
                        try {
                            Integer processDefinitionId = rs.getObject("process_definition_id", Integer.class);
                            if (processDefinitionId != null) {
                                cr.setProcessDefinitionId(processDefinitionId);
                            }
                        } catch (SQLException e) {
                            logger.debug("Could not read process_definition_id: {}", e.getMessage());
                        }
                    }
                    cr.setEstimatedBenefitId(rs.getObject("Estimated_BenefitID", Integer.class));
                    cr.setEstimatedCostId(rs.getObject("Estimated_CostID", Integer.class));
                    cr.setCreatedBy(rs.getObject("Created_By", Integer.class));
                    cr.setLastUserChange(rs.getObject("LastUserChange", Integer.class));
                    
                    // Load Mandatory_Workflow
                    try {
                        Object mandatoryWorkflowObj = rs.getObject("Mandatory_Workflow");
                        if (mandatoryWorkflowObj != null) {
                            boolean mandatoryWorkflow = rs.getBoolean("Mandatory_Workflow");
                            cr.setMandatoryWorkflow(mandatoryWorkflow);
                            logger.debug("Loaded Mandatory_Workflow for CR {}: {}", id, mandatoryWorkflow);
                        } else {
                            logger.debug("Mandatory_Workflow is NULL for CR {}", id);
                        }
                    } catch (SQLException e) {
                        logger.warn("Could not read Mandatory_Workflow for CR {}: {}", id, e.getMessage());
                    }
                    
                    // Load Visibility
                    try {
                        Integer visibility = rs.getObject("Visibility", Integer.class);
                        if (visibility != null) {
                            cr.setVisibility(visibility);
                        }
                    } catch (SQLException e) {
                        logger.debug("Could not read Visibility: {}", e.getMessage());
                    }
                    
                    // Load Delta
                    try {
                        String delta = rs.getString("Delta");
                        if (delta != null) {
                            cr.setDelta(delta);
                        }
                    } catch (SQLException e) {
                        logger.debug("Could not read Delta: {}", e.getMessage());
                    }

                    Timestamp createdAt = rs.getTimestamp("Created_At");
                    if (createdAt != null) {
                        cr.setCreatedAt(createdAt.toLocalDateTime());
                    }

                    Timestamp updatedAt = rs.getTimestamp("Updated_At");
                    if (updatedAt != null) {
                        cr.setUpdatedAt(updatedAt.toLocalDateTime());
                    }

                    logger.info("Found change request: {} with reference: {}, createdBy: {}, lastUserChange: {}",
                            cr.getPrimaryName(), cr.getReference(), cr.getCreatedBy(), cr.getLastUserChange());

                    // Load related data separately to avoid JOIN issues
                    loadRelatedData(cr, conn);

                    logger.info("After loading related data - createdByName: {}, lastUserChangeName: {}",
                            cr.getCreatedByName(), cr.getLastUserChangeName());

                    return cr;
                }
            }
        } catch (SQLException e) {
            logger.error("SQL error getting change request by ID {}: {}", id, e.getMessage(), e);
            throw e;
        }

        logger.warn("No change request found with ID: {}", id);
        return null;
    }

    /**
     * Load related data for a change request
     */
    private void loadRelatedData(ChangeRequest cr, Connection conn) throws SQLException {
        // Load type name
        if (cr.getCrTypeId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequest_type WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrTypeId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setTypeName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load type name: {}", e.getMessage());
            }
        }

        // Load status name (added to fix BUDG Status display)
        if (cr.getCrStatusId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequeststatus WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrStatusId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String statusName = rs.getString("PrimaryName");
                        // system.out.println("=== BUDG STATUS DEBUG ===");
                        // system.out.println("CR ID: " + cr.getId());
                        // system.out.println("Status ID: " + cr.getCrStatusId());
                        // system.out.println("Status Name: '" + statusName + "'");
                        // system.out.println("=========================");
                        cr.setStatusName(statusName);
                    } else {
                        // system.out.println("=== BUDG STATUS WARNING ===");
                        // system.out.println("No status found for CR ID: " + cr.getId() + ", Status ID:
                        // " + cr.getCrStatusId());
                        // system.out.println("===========================");
                    }
                }
            } catch (SQLException e) {
                // system.out.println("=== BUDG STATUS ERROR ===");
                // system.out.println("Could not load status for CR ID: " + cr.getId() + ",
                // Error: " + e.getMessage());
                // system.out.println("=========================");
            }
        } else {
            // system.out.println("=== BUDG STATUS INFO ===");
            // system.out.println("CR ID: " + cr.getId() + " has null status ID");
            // system.out.println("========================");
        }

        // Load severity name
        if (cr.getCrSeverityId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequest_severity WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrSeverityId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setSeverityName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load severity name: {}", e.getMessage());
            }
        }

        // Load urgency name
        if (cr.getCrUrgencyId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequest_urgency WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrUrgencyId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setUrgencyName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load urgency name: {}", e.getMessage());
            }
        }

        // Load estimated benefit
        if (cr.getEstimatedBenefitId() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT crv.Value, curr.PrimaryName as Currency " +
                            "FROM changerequest_value crv " +
                            "LEFT JOIN changerequest_currency curr ON crv.CR_CurrencyID = curr.ID " +
                            "WHERE crv.ID = ?")) {
                stmt.setInt(1, cr.getEstimatedBenefitId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setEstimatedBenefit(rs.getFloat("Value"));
                        cr.setEstimatedBenefitCurrency(rs.getString("Currency"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load estimated benefit: {}", e.getMessage());
            }
        }

        // Load estimated cost
        if (cr.getEstimatedCostId() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT crv.Value, curr.PrimaryName as Currency " +
                            "FROM changerequest_value crv " +
                            "LEFT JOIN changerequest_currency curr ON crv.CR_CurrencyID = curr.ID " +
                            "WHERE crv.ID = ?")) {
                stmt.setInt(1, cr.getEstimatedCostId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setEstimatedCost(rs.getFloat("Value"));
                        cr.setEstimatedCostCurrency(rs.getString("Currency"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load estimated cost: {}", e.getMessage());
            }
        }

        // Load created by user name
        if (cr.getCreatedBy() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT CONCAT(First_Name, ' ', Last_Name) as FullName FROM people WHERE ID = ?")) {
                stmt.setInt(1, cr.getCreatedBy());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setCreatedByName(rs.getString("FullName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load created by user name: {}", e.getMessage());
            }
        }

        // Load last user change name
        if (cr.getLastUserChange() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT CONCAT(First_Name, ' ', Last_Name) as FullName FROM people WHERE ID = ?")) {
                stmt.setInt(1, cr.getLastUserChange());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setLastUserChangeName(rs.getString("FullName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load last user change name: {}", e.getMessage());
            }
        }
    }

    public List<Integer> getOlderIncompleteCRs(int changeRequestId, String reference,
            Integer completedStatusId, Integer cancelledStatusId) throws SQLException {
        // Get the creation time of the current CR
        ChangeRequest currentCR = getChangeRequestById(changeRequestId);
        if (currentCR == null || currentCR.getCreatedAt() == null) {
            logger.warn("Cannot check older CRs: current CR {} not found or has no Created_At", changeRequestId);
            return new ArrayList<>();
        }

        LocalDateTime currentCreatedAt = currentCR.getCreatedAt();
        logger.debug("Checking for older incomplete CRs for CR {} (reference: {}, created at: {})",
                changeRequestId, reference, currentCreatedAt);

        // Use a more robust query that handles NULL statuses and uses proper date
        // comparison
        // We'll use a JOIN to get status names, but also handle NULL statuses
        String sql = "SELECT cr.ID, cr.Created_At, cr.CR_StatusID, cs.PrimaryName as StatusName " +
                "FROM changerequest cr " +
                "LEFT JOIN changerequeststatus cs ON cr.CR_StatusID = cs.ID " +
                "WHERE cr.Reference = ? " +
                "AND cr.Deleted_At IS NULL " +
                "AND cr.ID != ? " +
                "AND cr.Created_At < ? " +
                "ORDER BY cr.Created_At ASC";

        List<Integer> olderCRIds = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            stmt.setInt(2, changeRequestId);
            stmt.setTimestamp(3, Timestamp.valueOf(currentCreatedAt));

            logger.debug("Executing query: reference='{}', currentCRId={}, createdBefore={}",
                    reference, changeRequestId, currentCreatedAt);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int olderCRId = rs.getInt("ID");
                    Timestamp olderCreatedAt = rs.getTimestamp("Created_At");
                    Integer olderStatusId = rs.getObject("CR_StatusID", Integer.class);
                    String statusName = rs.getString("StatusName");

                    // Check if this CR is incomplete (not completed or cancelled)
                    boolean isIncomplete = true;

                    if (olderStatusId != null) {
                        // Check by status ID if provided
                        if (completedStatusId != null && olderStatusId.equals(completedStatusId)) {
                            isIncomplete = false;
                        }
                        if (cancelledStatusId != null && olderStatusId.equals(cancelledStatusId)) {
                            isIncomplete = false;
                        }

                        // Also check by status name as fallback
                        if (statusName != null) {
                            String statusUpper = statusName.toUpperCase();
                            if (statusUpper.contains("COMPLETED") || statusUpper.contains("CANCELLED") ||
                                    statusUpper.contains("CANCELED")) {
                                isIncomplete = false;
                            }
                        }
                    }
                    // If status is NULL, consider it incomplete (needs to be completed)

                    if (isIncomplete) {
                        olderCRIds.add(olderCRId);
                        logger.debug("Found older incomplete CR {} (created: {}, status: {})",
                                olderCRId, olderCreatedAt, statusName != null ? statusName : "NULL");
                    } else {
                        logger.debug("Skipping older CR {} - status indicates it's complete/cancelled (status: {})",
                                olderCRId, statusName);
                    }
                }
            }
        }

        logger.info("Found {} older incomplete CR(s) for reference '{}' (current CR: {})",
                olderCRIds.size(), reference, changeRequestId);

        return olderCRIds;
    }

    /**
     * Get list of older incomplete CRs for the same object created by the same user
     * Returns list of CR IDs that are blocking the current CR
     */
    public List<Integer> getOlderIncompleteCRsBySameUser(int changeRequestId, String reference,
            Integer createdBy, Integer completedStatusId, Integer cancelledStatusId) throws SQLException {
        // Get the creation time of the current CR
        ChangeRequest currentCR = getChangeRequestById(changeRequestId);
        if (currentCR == null || currentCR.getCreatedAt() == null) {
            logger.warn("Cannot check older CRs: current CR {} not found or has no Created_At", changeRequestId);
            return new ArrayList<>();
        }

        if (reference == null || reference.trim().isEmpty() || createdBy == null) {
            logger.debug("Cannot check older CRs by same user: reference or createdBy is null");
            return new ArrayList<>();
        }

        LocalDateTime currentCreatedAt = currentCR.getCreatedAt();
        logger.debug(
                "Checking for older incomplete CRs by same user for CR {} (reference: {}, createdBy: {}, created at: {})",
                changeRequestId, reference, createdBy, currentCreatedAt);

        // Query for older CRs with same reference and same user
        String sql = "SELECT cr.ID, cr.Created_At, cr.CR_StatusID, cs.PrimaryName as StatusName " +
                "FROM changerequest cr " +
                "LEFT JOIN changerequeststatus cs ON cr.CR_StatusID = cs.ID " +
                "WHERE cr.Reference = ? " +
                "AND cr.Deleted_At IS NULL " +
                "AND cr.ID != ? " +
                "AND cr.Created_By = ? " +
                "AND cr.Created_At < ? " +
                "ORDER BY cr.Created_At ASC";

        List<Integer> olderCRIds = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);
            stmt.setInt(2, changeRequestId);
            stmt.setInt(3, createdBy);
            stmt.setTimestamp(4, Timestamp.valueOf(currentCreatedAt));

            logger.debug("Executing query: reference='{}', currentCRId={}, createdBy={}, createdBefore={}",
                    reference, changeRequestId, createdBy, currentCreatedAt);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int olderCRId = rs.getInt("ID");
                    Timestamp olderCreatedAt = rs.getTimestamp("Created_At");
                    Integer olderStatusId = rs.getObject("CR_StatusID", Integer.class);
                    String statusName = rs.getString("StatusName");

                    // Check if this CR is incomplete (not completed or cancelled)
                    boolean isIncomplete = true;

                    if (olderStatusId != null) {
                        // Check by status ID if provided
                        if (completedStatusId != null && olderStatusId.equals(completedStatusId)) {
                            isIncomplete = false;
                        }
                        if (cancelledStatusId != null && olderStatusId.equals(cancelledStatusId)) {
                            isIncomplete = false;
                        }

                        // Also check by status name as fallback
                        if (statusName != null) {
                            String statusUpper = statusName.toUpperCase();
                            if (statusUpper.contains("COMPLETED") || statusUpper.contains("CANCELLED") ||
                                    statusUpper.contains("CANCELED")) {
                                isIncomplete = false;
                            }
                        }
                    }
                    // If status is NULL, consider it incomplete (needs to be completed)

                    if (isIncomplete) {
                        olderCRIds.add(olderCRId);
                        logger.debug("Found older incomplete CR {} by same user (created: {}, status: {})",
                                olderCRId, olderCreatedAt, statusName != null ? statusName : "NULL");
                    } else {
                        logger.debug("Skipping older CR {} - status indicates it's complete/cancelled (status: {})",
                                olderCRId, statusName);
                    }
                }
            }
        }

        logger.info("Found {} older incomplete CR(s) by same user {} for reference '{}' (current CR: {})",
                olderCRIds.size(), createdBy, reference, changeRequestId);

        return olderCRIds;
    }

    /**
     * Get change requests by facet reference with full related data
     */
    public List<ChangeRequest> getChangeRequestsByReference(String reference) throws SQLException {
        String sql = "SELECT * FROM changerequest WHERE Reference = ? AND Deleted_At IS NULL ORDER BY Created_At DESC";
        List<ChangeRequest> changeRequests = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, reference);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeRequest cr = mapResultSetToChangeRequest(rs);
                    changeRequests.add(cr);
                }
            }

            // Load related data for all change requests
            for (ChangeRequest cr : changeRequests) {
                loadRelatedDataForList(cr, conn);
            }
        }
        return changeRequests;
    }

    /**
     * Get change requests where the user is a stakeholder (even if not the creator)
     * @param personId The person ID to search for
     * @return List of change requests where the person is a stakeholder
     */
    public List<ChangeRequest> getChangeRequestsByStakeholder(int personId) throws SQLException {
        String sql = "SELECT DISTINCT cr.* FROM changerequest cr " +
                    "INNER JOIN cr_stakeholders crs ON cr.ID = crs.CR_ID " +
                    "WHERE crs.User_ID = ? AND cr.Deleted_At IS NULL " +
                    "ORDER BY cr.Created_At DESC";
        List<ChangeRequest> changeRequests = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, personId);
            logger.info("Executing query for change requests where person {} is a stakeholder", personId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ChangeRequest cr = mapResultSetToChangeRequest(rs);
                    loadRelatedDataForList(cr, conn);
                    changeRequests.add(cr);
                }
            }
        }
        return changeRequests;
    }

    /**
     * V-01: Get CRs by stakeholder AND enforce segment access for the requesting user.
     * A WebUser who is a CR Stakeholder must ALSO have access to that CR's Segment.
     *
     * @param personId     The person whose stakeholder entries we query
     * @param requestingUserId The user making the request (segment check applied against this user)
     */
    public List<ChangeRequest> getChangeRequestsByStakeholderWithSegmentCheck(int personId, int requestingUserId) throws SQLException {
        List<ChangeRequest> crsByStakeholder = getChangeRequestsByStakeholder(personId);
        if (crsByStakeholder.isEmpty()) {
            return crsByStakeholder;
        }

        List<Integer> allIds = crsByStakeholder.stream().map(ChangeRequest::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.getAccessibleObjectIdsInSegments(requestingUserId, "ChangeRequest", allIds);

        return crsByStakeholder.stream()
                .filter(cr -> accessibleIds.contains(cr.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Load related data for change requests in list view (includes status)
     */
    public void loadRelatedDataForList(ChangeRequest cr, Connection conn) throws SQLException {
        // Load type name
        if (cr.getCrTypeId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequest_type WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrTypeId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setTypeName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load type name: {}", e.getMessage());
            }
        }

        // Load status name
        if (cr.getCrStatusId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequeststatus WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrStatusId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setStatusName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load status name: {}", e.getMessage());
            }
        }

        // Load severity name
        if (cr.getCrSeverityId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequest_severity WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrSeverityId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setSeverityName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load severity name: {}", e.getMessage());
            }
        }

        // Load urgency name
        if (cr.getCrUrgencyId() != null) {
            try (PreparedStatement stmt = conn
                    .prepareStatement("SELECT PrimaryName FROM changerequest_urgency WHERE ID = ?")) {
                stmt.setInt(1, cr.getCrUrgencyId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setUrgencyName(rs.getString("PrimaryName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load urgency name: {}", e.getMessage());
            }
        }

        // Load estimated benefit with currency
        if (cr.getEstimatedBenefitId() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT crv.Value, curr.PrimaryName as Currency " +
                            "FROM changerequest_value crv " +
                            "LEFT JOIN changerequest_currency curr ON crv.CR_CurrencyID = curr.ID " +
                            "WHERE crv.ID = ?")) {
                stmt.setInt(1, cr.getEstimatedBenefitId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setEstimatedBenefit(rs.getFloat("Value"));
                        cr.setEstimatedBenefitCurrency(rs.getString("Currency"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load estimated benefit: {}", e.getMessage());
            }
        }

        // Load estimated cost with currency
        if (cr.getEstimatedCostId() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT crv.Value, curr.PrimaryName as Currency " +
                            "FROM changerequest_value crv " +
                            "LEFT JOIN changerequest_currency curr ON crv.CR_CurrencyID = curr.ID " +
                            "WHERE crv.ID = ?")) {
                stmt.setInt(1, cr.getEstimatedCostId());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setEstimatedCost(rs.getFloat("Value"));
                        cr.setEstimatedCostCurrency(rs.getString("Currency"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load estimated cost: {}", e.getMessage());
            }
        }

        // Load created by user name
        if (cr.getCreatedBy() != null) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT CONCAT(First_Name, ' ', Last_Name) as FullName FROM people WHERE ID = ?")) {
                stmt.setInt(1, cr.getCreatedBy());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cr.setCreatedByName(rs.getString("FullName"));
                    }
                }
            } catch (SQLException e) {
                logger.warn("Could not load created by user name: {}", e.getMessage());
            }
        }
    }

    /**
     * Update change request
     */
    public boolean updateChangeRequest(ChangeRequest changeRequest) throws SQLException {
        ChangeRequest oldCR = getChangeRequestById(changeRequest.getId());
        if (oldCR == null) {
            return false;
        }

        String sql = "UPDATE changerequest SET PrimaryName = ?, Parent_ID = ?, Reference = ?, " +
                "Summary = ?, LastUserChange = ?, Updated_At = ?, CR_StatusID = ?, " +
                "CR_TypeID = ?, CR_SeverityID = ?, CR_UrgencyID = ?, Estimated_BenefitID = ?, " +
                "Estimated_CostID = ?, Visibility = ?, Mandatory_Workflow = ?, Delta = ? " +
                "WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, changeRequest.getPrimaryName());

            if (changeRequest.getParentId() != null) {
                stmt.setInt(2, changeRequest.getParentId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }

            stmt.setString(3, changeRequest.getReference());
            stmt.setString(4, changeRequest.getSummary());
            stmt.setInt(5, changeRequest.getLastUserChange());
            stmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));

            if (changeRequest.getCrStatusId() != null) {
                stmt.setInt(7, changeRequest.getCrStatusId());
            } else {
                stmt.setNull(7, Types.INTEGER);
            }

            if (changeRequest.getCrTypeId() != null) {
                stmt.setInt(8, changeRequest.getCrTypeId());
            } else {
                stmt.setNull(8, Types.INTEGER);
            }
            if (changeRequest.getCrSeverityId() != null) {
                stmt.setInt(9, changeRequest.getCrSeverityId());
            } else {
                stmt.setNull(9, Types.INTEGER);
            }
            if (changeRequest.getCrUrgencyId() != null) {
                stmt.setInt(10, changeRequest.getCrUrgencyId());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }

            if (changeRequest.getEstimatedBenefitId() != null) {
                stmt.setInt(11, changeRequest.getEstimatedBenefitId());
            } else {
                stmt.setNull(11, Types.INTEGER);
            }

            if (changeRequest.getEstimatedCostId() != null) {
                stmt.setInt(12, changeRequest.getEstimatedCostId());
            } else {
                stmt.setNull(12, Types.INTEGER);
            }

            if (changeRequest.getVisibility() != null) {
                stmt.setInt(13, changeRequest.getVisibility());
            } else {
                stmt.setNull(13, Types.INTEGER);
            }

            if (changeRequest.getMandatoryWorkflow() != null) {
                stmt.setBoolean(14, changeRequest.getMandatoryWorkflow());
            } else {
                stmt.setNull(14, Types.BOOLEAN);
            }

            stmt.setString(15, changeRequest.getDelta());
            stmt.setInt(16, changeRequest.getId());

            boolean updated = stmt.executeUpdate() > 0;
            if (updated) {
                insertHistoryForUpdate(changeRequest.getId(), oldCR, changeRequest, conn);
            }
            return updated;
        }
    }

    /**
     * Insert history records for changed fields on update.
     */
    private void insertHistoryForUpdate(int crId, ChangeRequest oldCR, ChangeRequest newCR, Connection conn) {
        try {
            ChangeRequestHistoryDAO historyDAO = new ChangeRequestHistoryDAO();
            String author = getPersonNameForHistory(conn, newCR.getLastUserChange());
            if (author == null) author = "System";

            if (!equals(oldCR.getReference(), newCR.getReference())) {
                insertHistoryRecord(historyDAO, crId, "Reference", oldCR.getReference(), newCR.getReference(), author);
            }
            if (!equals(oldCR.getPrimaryName(), newCR.getPrimaryName())) {
                insertHistoryRecord(historyDAO, crId, "Title", oldCR.getPrimaryName(), newCR.getPrimaryName(), author);
            }
            if (!equals(oldCR.getSummary(), newCR.getSummary())) {
                insertHistoryRecord(historyDAO, crId, "Summary", oldCR.getSummary(), newCR.getSummary(), author);
            }
            if (!equals(oldCR.getCrTypeId(), newCR.getCrTypeId())) {
                insertHistoryRecord(historyDAO, crId, "Type",
                        getLookupName(conn, "changerequest_type", oldCR.getCrTypeId()),
                        getLookupName(conn, "changerequest_type", newCR.getCrTypeId()), author);
            }
            if (!equals(oldCR.getCrSeverityId(), newCR.getCrSeverityId())) {
                insertHistoryRecord(historyDAO, crId, "Severity",
                        getLookupName(conn, "changerequest_severity", oldCR.getCrSeverityId()),
                        getLookupName(conn, "changerequest_severity", newCR.getCrSeverityId()), author);
            }
            if (!equals(oldCR.getCrUrgencyId(), newCR.getCrUrgencyId())) {
                insertHistoryRecord(historyDAO, crId, "Urgency",
                        getLookupName(conn, "changerequest_urgency", oldCR.getCrUrgencyId()),
                        getLookupName(conn, "changerequest_urgency", newCR.getCrUrgencyId()), author);
            }
            if (!equals(oldCR.getCrStatusId(), newCR.getCrStatusId())) {
                insertHistoryRecord(historyDAO, crId, "Status",
                        getLookupName(conn, "changerequeststatus", oldCR.getCrStatusId()),
                        getLookupName(conn, "changerequeststatus", newCR.getCrStatusId()), author);
            }
        } catch (SQLException e) {
            logger.warn("Could not insert history for CR {} update: {}", crId, e.getMessage());
        }
    }

    private boolean equals(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Soft delete change request
     */
    public boolean deleteChangeRequest(Integer id, Integer userId) throws SQLException {
        String sql = "UPDATE changerequest SET Deleted_At = ?, LastUserChange = ? WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, userId);
            stmt.setInt(3, id);

            return stmt.executeUpdate() > 0;
        }
    }

    /**
     * Cascade soft delete - soft deletes all change requests for a given object
     * reference
     * This should be called when an object is soft deleted to cascade the delete to
     * its change requests
     *
     * @param reference The object reference (e.g., "System 63", "Glossary 14",
     *                  "Dataset 25")
     * @param userId    The user performing the delete
     * @return Number of change requests soft deleted
     */
    public int cascadeSoftDeleteByReference(String reference, Integer userId) throws SQLException {
        if (reference == null || reference.trim().isEmpty()) {
            logger.warn("Cannot cascade soft delete: reference is null or empty");
            return 0;
        }

        String sql = "UPDATE changerequest SET Deleted_At = ?, LastUserChange = ? " +
                "WHERE Reference = ? AND Deleted_At IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, userId);
            stmt.setString(3, reference.trim());

            int affectedRows = stmt.executeUpdate();
            logger.info("Cascade soft deleted {} change requests for reference: {}", affectedRows, reference);
            return affectedRows;
        }
    }

    /**
     * Cascade soft delete by facet type and ID
     * Constructs the reference string and calls cascadeSoftDeleteByReference
     *
     * @param facetType The type of object (e.g., "System", "Glossary", "Dataset")
     * @param facetId   The ID of the object
     * @param userId    The user performing the delete
     * @return Number of change requests soft deleted
     */
    public int cascadeSoftDeleteByFacet(String facetType, Integer facetId, Integer userId) throws SQLException {
        if (facetType == null || facetType.trim().isEmpty() || facetId == null) {
            logger.warn("Cannot cascade soft delete: facetType or facetId is null/empty");
            return 0;
        }

        // Normalize facet type to match the reference format used in change requests
        String normalizedFacetType = normalizeFacetTypeForReference(facetType);
        String reference = normalizedFacetType + " " + facetId;

        logger.info("Cascade soft deleting change requests for {} (reference: {})", facetType, reference);
        return cascadeSoftDeleteByReference(reference, userId);
    }

    /**
     * Normalize facet type to match the reference format used in change requests
     * e.g., "system" -> "System", "business-area" -> "Business Area"
     */
    private String normalizeFacetTypeForReference(String facetType) {
        if (facetType == null)
            return "";

        String normalized = facetType.trim().toLowerCase();

        // Handle special cases with spaces or hyphens
        switch (normalized) {
            case "system":
                return "System";
            case "dataset":
            case "data-set":
                return "Dataset";
            case "glossary":
                return "Glossary";
            case "process":
                return "Process";
            case "product":
                return "Product";
            case "project":
                return "Project";
            case "policy":
                return "Policy";
            case "client":
                return "Client";
            case "committee":
                return "Committee";
            case "capability":
                return "Capability";
            case "business-area":
            case "businessarea":
                return "Business Area";
            case "legal-entity":
            case "legalentity":
                return "Legal Entity";
            case "regulation":
                return "Regulation";
            case "regulator":
                return "Regulator";
            case "org-unit":
            case "orgunit":
                return "Org Unit";
            case "geography":
                return "Geography";
            case "interface":
            case "system-interface":
                return "Interface";
            default:
                // Capitalize first letter of each word
                String[] words = normalized.replace("-", " ").replace("_", " ").split("\\s+");
                StringBuilder result = new StringBuilder();
                for (String word : words) {
                    if (word.length() > 0) {
                        if (result.length() > 0)
                            result.append(" ");
                        result.append(Character.toUpperCase(word.charAt(0)));
                        if (word.length() > 1) {
                            result.append(word.substring(1));
                        }
                    }
                }
                return result.toString();
        }
    }

    /**
     * Restore (un-delete) all change requests for a given object reference
     * This can be called when an object is restored from soft delete
     *
     * @param reference The object reference (e.g., "System 63", "Glossary 14")
     * @param userId    The user performing the restore
     * @return Number of change requests restored
     */
    public int cascadeRestoreByReference(String reference, Integer userId) throws SQLException {
        if (reference == null || reference.trim().isEmpty()) {
            logger.warn("Cannot cascade restore: reference is null or empty");
            return 0;
        }

        String sql = "UPDATE changerequest SET Deleted_At = NULL, LastUserChange = ?, Updated_At = ? " +
                "WHERE Reference = ? AND Deleted_At IS NOT NULL";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setString(3, reference.trim());

            int affectedRows = stmt.executeUpdate();
            logger.info("Cascade restored {} change requests for reference: {}", affectedRows, reference);
            return affectedRows;
        }
    }

    /**
     * Cascade restore by facet type and ID
     *
     * @param facetType The type of object (e.g., "System", "Glossary", "Dataset")
     * @param facetId   The ID of the object
     * @param userId    The user performing the restore
     * @return Number of change requests restored
     */
    public int cascadeRestoreByFacet(String facetType, Integer facetId, Integer userId) throws SQLException {
        if (facetType == null || facetType.trim().isEmpty() || facetId == null) {
            logger.warn("Cannot cascade restore: facetType or facetId is null/empty");
            return 0;
        }

        String normalizedFacetType = normalizeFacetTypeForReference(facetType);
        String reference = normalizedFacetType + " " + facetId;

        logger.info("Cascade restoring change requests for {} (reference: {})", facetType, reference);
        return cascadeRestoreByReference(reference, userId);
    }

    /**
     * Get enum values for a column (for debugging)
     */
    private String getEnumValues(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("COLUMN_TYPE");
                }
            }
        }
        return "Not found";
    }

    /**
     * Get currency ID by currency code
     */
    public Integer getCurrencyIdByCode(String currencyCode) throws SQLException {
        String sql = "SELECT ID FROM changerequest_currency WHERE PrimaryName = ? AND Status = 'Enabled'";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, currencyCode);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }

    /**
     * Ensure process_definition_id column exists in changerequest table
     */
    private void ensureProcessDefinitionIdColumn(Connection conn) {
        // Check if column exists
        boolean columnExists = false;
        try (java.sql.ResultSet rs = conn.getMetaData().getColumns(null, null, "changerequest",
                "process_definition_id")) {
            columnExists = rs.next();
        } catch (SQLException e) {
            logger.debug("Could not check for process_definition_id column: {}", e.getMessage());
        }

        if (!columnExists) {
            // Add the column
            String sql = "ALTER TABLE changerequest ADD COLUMN process_definition_id INT NULL";
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                logger.info("Added process_definition_id column to changerequest table");
            } catch (SQLException e) {
                String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                if (msg.contains("duplicate column") || msg.contains("already exists")) {
                    // Column was added by another thread/request, ignore
                    logger.debug("process_definition_id column already exists (race condition)");
                } else {
                    logger.warn("Could not add process_definition_id column to changerequest table: {}",
                            e.getMessage());
                }
            }
        }
    }

    /**
     * Map ResultSet to ChangeRequest object
     */
    private ChangeRequest mapResultSetToChangeRequest(ResultSet rs) throws SQLException {
        // Check if process_definition_id column exists in ResultSet
        boolean hasProcessDefinitionId = false;
        try {
            java.sql.ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i).toLowerCase();
                if (columnName.equals("process_definition_id")) {
                    hasProcessDefinitionId = true;
                    break;
                }
            }
        } catch (SQLException e) {
            logger.debug("Could not check for process_definition_id column: {}", e.getMessage());
        }

        ChangeRequest cr = new ChangeRequest();
        cr.setId(rs.getInt("ID"));
        cr.setPrimaryName(rs.getString("PrimaryName"));

        int parentId = rs.getInt("Parent_ID");
        if (!rs.wasNull()) {
            cr.setParentId(parentId);
        }

        cr.setReference(rs.getString("Reference"));
        cr.setSummary(rs.getString("Summary"));

        int lastUserChange = rs.getInt("LastUserChange");
        if (!rs.wasNull()) {
            cr.setLastUserChange(lastUserChange);
        }

        int createdBy = rs.getInt("Created_By");
        if (!rs.wasNull()) {
            cr.setCreatedBy(createdBy);
        }

        Timestamp createdAt = rs.getTimestamp("Created_At");
        if (createdAt != null) {
            cr.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("Updated_At");
        if (updatedAt != null) {
            cr.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        Timestamp deletedAt = rs.getTimestamp("Deleted_At");
        if (deletedAt != null) {
            cr.setDeletedAt(deletedAt.toLocalDateTime());
        }

        int crStatusId = rs.getInt("CR_StatusID");
        if (!rs.wasNull()) {
            cr.setCrStatusId(crStatusId);
            // system.out.println("=== MAP RESULT SET ===");
            // system.out.println("CR ID: " + cr.getId() + ", CR_StatusID from DB: " +
            // crStatusId);
            // system.out.println("=======================");
        } else {
            // system.out.println("=== MAP RESULT SET ===");
            // system.out.println("CR ID: " + cr.getId() + ", CR_StatusID is NULL in
            // database");
            // system.out.println("=======================");
        }

        int crTypeId = rs.getInt("CR_TypeID");
        if (!rs.wasNull()) {
            cr.setCrTypeId(crTypeId);
        }

        int crSeverityId = rs.getInt("CR_SeverityID");
        if (!rs.wasNull()) {
            cr.setCrSeverityId(crSeverityId);
        }

        int crUrgencyId = rs.getInt("CR_UrgencyID");
        if (!rs.wasNull()) {
            cr.setCrUrgencyId(crUrgencyId);
        }

        int processInstanceId = rs.getInt("Process_InstanceID");
        if (!rs.wasNull()) {
            cr.setProcessInstanceId(processInstanceId);
        }

        // Safely read process_definition_id (column may not exist in older databases)
        if (hasProcessDefinitionId) {
            try {
                Integer processDefinitionId = rs.getObject("process_definition_id", Integer.class);
                if (processDefinitionId != null) {
                    cr.setProcessDefinitionId(processDefinitionId);
                }
            } catch (SQLException e) {
                // Column exists but couldn't read it - ignore
                logger.debug("Could not read process_definition_id: {}", e.getMessage());
            }
        }

        int estimatedBenefitId = rs.getInt("Estimated_BenefitID");
        if (!rs.wasNull()) {
            cr.setEstimatedBenefitId(estimatedBenefitId);
        }

        int estimatedCostId = rs.getInt("Estimated_CostID");
        if (!rs.wasNull()) {
            cr.setEstimatedCostId(estimatedCostId);
        }

        int visibility = rs.getInt("Visibility");
        if (!rs.wasNull()) {
            cr.setVisibility(visibility);
        }

        boolean mandatoryWorkflow = rs.getBoolean("Mandatory_Workflow");
        if (!rs.wasNull()) {
            cr.setMandatoryWorkflow(mandatoryWorkflow);
        }

        cr.setDelta(rs.getString("Delta"));

        return cr;
    }

    /**
     * Reset workflow fields for a change request (used during rollback)
     * Clears Process_InstanceID, sets Mandatory_Workflow to 0
     */
    public boolean resetWorkflowFields(int changeRequestId) throws SQLException {
        String sql = "UPDATE changerequest SET Process_InstanceID = NULL, Mandatory_Workflow = 0, Updated_At = ? WHERE ID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            stmt.setInt(2, changeRequestId);

            int rows = stmt.executeUpdate();
            logger.info("Reset workflow fields for CR {} (cleared instance ID, set Mandatory=0)", changeRequestId);
            return rows > 0;
        }
    }

    /**
     * Delete change requests that are scheduled for deletion (scheduled_delete_at
     * <= NOW())
     * 
     * @return Number of change requests deleted
     */
    public int deleteScheduledChangeRequests() throws SQLException {
        String sql = "UPDATE changerequest " +
                "SET Deleted_At = NOW(), " +
                "    Updated_At = NOW() " +
                "WHERE scheduled_delete_at IS NOT NULL " +
                "  AND scheduled_delete_at <= NOW() " +
                "  AND Deleted_At IS NULL";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            int affectedRows = stmt.executeUpdate();
            logger.info("Deleted {} change requests that were scheduled for deletion", affectedRows);
            return affectedRows;
        }
    }
}
