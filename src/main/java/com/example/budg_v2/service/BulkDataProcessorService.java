package com.example.budg_v2.service;

import com.example.budg_v2.bulk.objects.BulkUploadBroadcaster;
import com.example.budg_v2.bulk.objects.CustomFieldBulkUploadHelper;
import com.example.budg_v2.dao.*;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Dataset;
import com.example.budg_v2.model.WorkflowNotification;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for processing validated bulk upload data
 * Handles INSERT, UPDATE, DELETE operations for all entity types
 * Can be used by bulk upload servlets and other migration-related flows
 */
public class BulkDataProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(BulkDataProcessorService.class);

    private final JobDAO jobDAO = new JobDAO();
    private final SystemDAO systemDAO = new SystemDAO();
    private final DatasetDAO datasetDAO = new DatasetDAO();
    private final GlossaryDAO glossaryDAO = new GlossaryDAO();
    private final AttributeDAO attributeDAO = new AttributeDAO();
    private final InterfaceDAO interfaceDAO = new InterfaceDAO();
    private final WorkflowNotificationDAO notificationDAO = new WorkflowNotificationDAO();

    private final SmartErrorHandler errorHandler = new SmartErrorHandler();

    /**
     * Main entry point - processes validated data for any entity type
     * Now uses EntityProcessorRegistry for dynamic processing
     */
    public void processEntityData(String entityName, int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        // Normalize entity name
        String normalizedEntity = normalizeEntityName(entityName);

        logger.info("Processing {} data for job {}: {} rows", normalizedEntity, jobId, validatedData.size());

        // Use EntityProcessorRegistry for dynamic processing
        EntityProcessor processor = EntityProcessorRegistry.getInstance().getProcessor(normalizedEntity);
        processor.process(jobId, validatedData, userId, errorHandling, uploadOption, segmentMode, segment);
    }

    /**
     * Normalize entity name to standard format
     */
    private String normalizeEntityName(String entityName) {
        if (entityName == null)
            return "Unknown";
        String normalized = entityName.trim();
        // Remove common variations
        normalized = normalized.replace(" (Roles)", "");
        normalized = normalized.replace("Roles", "");
        return normalized;
    }

    /**
     * Process System data
     */
    public void processSystemData(int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();

        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.createJobProgress(jobId, 30, "Processing", "Starting data insertion...");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                    "Starting data insertion...", 0, 0, 0, 0);

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.get("operation").getAsString();
                int rowNumber = rowData.get("row_number").getAsInt();

                if (i % 10 == 0) {
                    int progressPercent = 30 + (int) ((i / (double) totalRows) * 65);
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                }

                try {
                    if ("INSERT".equals(operation)) {
                        int systemId = insertSystem(conn, rowData, userId);

                        // Assign object to segment
                        Long segmentIdToAssign = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                segmentIdToAssign = determineSegmentId(null, null, rowData);
                            }
                        }

                        if (segmentIdToAssign != null && systemId > 0) {
                            try {
                                ObjectSegmentService.assignObjectToSegment(
                                        (long) systemId,
                                        "System",
                                        segmentIdToAssign,
                                        userId);
                                logger.info("Assigned System {} to Segment {}", systemId, segmentIdToAssign);
                            } catch (Exception segEx) {
                                logger.error("Failed to assign System {} to segment: {}", systemId, segEx.getMessage(),
                                        segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    systemId,
                                    "System",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for System {}: {}", systemId, cfEx.getMessage(),
                                    cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "System", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "System created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        int systemId = rowData.get("ID").getAsInt();
                        updateSystem(conn, rowData, userId);

                        // Handle segment assignment for UPDATE operations
                        Long newSegmentId = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            newSegmentId = determineSegmentId(segmentMode, segment, rowData);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                newSegmentId = determineSegmentId(null, null, rowData);
                            }
                        }

                        if (newSegmentId != null) {
                            try {
                                // Get current segment
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) systemId,
                                        "System");

                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change
                                    SegmentValidationService validator = new SegmentValidationService();
                                    Integer parentId = getInteger(rowData, "Parent_ID");
                                    SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            systemId, newSegmentId.intValue(), "System", parentId);

                                    if (!result.isValid && !result.canProceedWithWarning) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }

                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                            (long) systemId,
                                            "System",
                                            newSegmentId,
                                            userId);
                                    logger.info("Assigned System {} to Segment {} (updated)", systemId, newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign System {} to segment: {}", systemId, segEx.getMessage(),
                                        segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    systemId,
                                    "System",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for System {}: {}", systemId, cfEx.getMessage(),
                                    cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "System", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "System updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        deleteSystem(conn, rowData, userId);
                        deletedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "System", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "System deleted successfully", "info");
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "System", "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                            "Failed to process: " + e.getMessage(), "error");

                    if ("Cancel on Warning".equalsIgnoreCase(errorHandling)) {
                        conn.rollback();
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed",
                                "Processing failed at row " + rowNumber + ": " + e.getMessage());
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                "Processing failed at row " + rowNumber, insertedCount, updatedCount, deletedCount,
                                failedCount);
                        return;
                    }
                }
            }

            conn.commit();
            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.updateJobProgress(jobId, "Completed",
                    String.format("Processing complete: %d inserted, %d updated, %d deleted, %d failed",
                            insertedCount, updatedCount, deletedCount, failedCount));

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Processing complete", insertedCount, updatedCount, deletedCount, failedCount);

            logger.info("Bulk upload job {} completed: {} inserted, {} updated, {} deleted, {} failed",
                    jobId, insertedCount, updatedCount, deletedCount, failedCount);

            // Send success notification
            sendNotification(userId, "System", uploadOption, insertedCount, updatedCount,
                    deletedCount, failedCount, true, null, jobId);

        } catch (Exception e) {
            logger.error("Error processing system bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Processing error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Processing failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "System", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, false, e.getMessage(), jobId);
            } catch (Exception e2) {
                logger.error("Error updating job status after failure", e2);
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Insert System
     */
    private int insertSystem(Connection conn, JsonObject rowData, int userId) throws SQLException {
        String sql = "INSERT INTO system (Name, Long_Name, AssetID, External, Description, URL, DQ_Automation, " +
                "parent_id, is_Public, status, Lifecycle, Type, Classification, Confidentiality_Rating, " +
                "Integrity_Rating, Availability_Rating, CreatedBy_ID, Last_updated_UserID, " +
                "Created_Datetime, Last_Updated_Datetime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            int idx = 1;

            ps.setString(idx++, getString(rowData, "Short Name"));
            ps.setString(idx++, getString(rowData, "Long Name"));
            ps.setString(idx++, getString(rowData, "Asset ID"));
            ps.setBoolean(idx++, getBoolean(rowData, "External", false));
            ps.setString(idx++, getString(rowData, "Description"));
            ps.setString(idx++, getString(rowData, "URL"));
            setNullableBoolean(ps, idx++, getBooleanOrNull(rowData, "DQAutomation"));
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));

            Integer viewingId = coalesce(getInteger(rowData, "BUDG Viewing_ID"), getFirstViewingId());
            setNullableInt(ps, idx++, viewingId);

            Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"), getFirstStatusId());
            setNullableInt(ps, idx++, statusId);

            Integer lifecycleId = coalesce(getInteger(rowData, "Lifecycle_ID"),
                    getFirstLookupId("system_lifecycle"));
            setNullableInt(ps, idx++, lifecycleId);

            Integer typeId = coalesce(getInteger(rowData, "Type_ID"),
                    getFirstLookupId("system_type"));
            setNullableInt(ps, idx++, typeId);

            setNullableInt(ps, idx++, getInteger(rowData, "Classification_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Confidentiality_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Integrity_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Availability_ID"));

            ps.setInt(idx++, userId);
            ps.setInt(idx++, userId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert failed, no rows affected");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int systemId = rs.getInt(1);

                    // Create audit records
                    // Create audit records
                    String userName = getUserName(conn, userId);
                    systemDAO.createSystemAuditRecords(systemId, userName, conn);
                    // Initial snapshot for updates
                    systemDAO.createSystemUpdateAuditSnapshot(systemId, conn);

                    // Link stakeholder if role specified
                    linkSystemStakeholder(conn, systemId, rowData, userId);

                    logger.info("System created with ID: {}", systemId);
                    return systemId;
                }
            }
        }
        throw new SQLException("Failed to insert system, no ID generated");
    }

    /**
     * Update System
     */
    private void updateSystem(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int systemId = rowData.get("ID").getAsInt();

        // Fetch old state for audit
        Map<String, Object> oldSystem = systemDAO.getSystemState(conn, systemId);
        String userName = getUserName(conn, userId);

        String sql = "UPDATE system SET Name = ?, Long_Name = ?, AssetID = ?, External = ?, Description = ?, " +
                "URL = ?, DQ_Automation = ?, parent_id = ?, is_Public = ?, status = ?, Lifecycle = ?, " +
                "Type = ?, Classification = ?, Confidentiality_Rating = ?, Integrity_Rating = ?, " +
                "Availability_Rating = ?, Last_updated_UserID = ?, Last_Updated_Datetime = NOW() " +
                "WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // ... (parameters remain the same)
            int idx = 1;
            ps.setString(idx++, getString(rowData, "Short Name"));
            ps.setString(idx++, getString(rowData, "Long Name"));
            ps.setString(idx++, getString(rowData, "Asset ID"));
            ps.setBoolean(idx++, getBoolean(rowData, "External", false));
            ps.setString(idx++, getString(rowData, "Description"));
            ps.setString(idx++, getString(rowData, "URL"));
            setNullableBoolean(ps, idx++, getBooleanOrNull(rowData, "DQAutomation"));
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "BUDG Viewing_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "BUDG Status_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Lifecycle_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Type_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Classification_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Confidentiality_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Integrity_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "Availability_ID"));
            ps.setInt(idx++, userId);
            ps.setInt(idx++, systemId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed, system not found: " + systemId);
            }

            // Create update audit records
            if (oldSystem != null) {
                // Map new data for comparison
                Map<String, Object> newSystem = new HashMap<>();
                newSystem.put("name", getString(rowData, "Short Name"));
                newSystem.put("parentId", getInteger(rowData, "Parent_ID"));
                newSystem.put("description", getString(rowData, "Description"));
                newSystem.put("Type", getInteger(rowData, "Type_ID"));
                newSystem.put("external", getBoolean(rowData, "External", false) ? 1 : 0);
                newSystem.put("longName", getString(rowData, "Long Name"));
                newSystem.put("url", getString(rowData, "URL"));
                newSystem.put("status", getInteger(rowData, "BUDG Status_ID"));
                newSystem.put("lifecycle", getInteger(rowData, "Lifecycle_ID"));
                newSystem.put("isPublic", getInteger(rowData, "BUDG Viewing_ID"));
                newSystem.put("confidentialityRating", getInteger(rowData, "Confidentiality_ID"));
                newSystem.put("integrityRating", getInteger(rowData, "Integrity_ID"));
                newSystem.put("availabilityRating", getInteger(rowData, "Availability_ID"));
                newSystem.put("assetId", getString(rowData, "Asset ID"));
                newSystem.put("classification", getInteger(rowData, "Classification_ID"));
                newSystem.put("dqAutomation", getBooleanOrNull(rowData, "DQAutomation") != null
                        ? (getBooleanOrNull(rowData, "DQAutomation") ? 1 : 0)
                        : null);

                systemDAO.createSystemUpdateAuditRecords(systemId, oldSystem, newSystem, userName, conn);
            }

            // Create update audit snapshot
            systemDAO.createSystemUpdateAuditSnapshot(systemId, conn);

            logger.info("System updated with ID: {}", systemId);
        }
    }

    /**
     * Delete System (soft delete)
     */
    private void deleteSystem(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int systemId = rowData.get("ID").getAsInt();

        String sql = "UPDATE system SET Deleted_datetime = NOW() WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, systemId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Delete failed, system not found: " + systemId);
            }

            // Create audit record for soft delete
            systemDAO.createSystemAuditRecord(systemId, conn);

            logger.info("System soft deleted with ID: {}", systemId);
        }
    }

    /**
     * Process Dataset data
     */
    public void processDatasetData(int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();

        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.createJobProgress(jobId, 30, "Processing", "Starting data insertion...");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                    "Starting data insertion...", 0, 0, 0, 0);

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.get("operation").getAsString();
                int rowNumber = rowData.get("row_number").getAsInt();

                if (i % 10 == 0) {
                    int progressPercent = 30 + (int) ((i / (double) totalRows) * 65);
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                }

                try {
                    if ("INSERT".equals(operation)) {
                        int datasetId = insertDataset(conn, rowData, userId, segmentMode, segment);

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    datasetId,
                                    "Data Sets",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Dataset {}: {}", datasetId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;

                        // Insert into valuesjob_dataset table
                        jobDAO.insertValuesjobDataset(jobId, datasetId);

                        // Insert into valuesjob_meta_attribute with Append mode
                        jobDAO.insertValuesjobMetaAttribute(jobId, "Suffix_Type", "Append");
                        jobDAO.insertValuesjobMetaAttribute(jobId, "IsOverwrite", "0");

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Dataset created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        int datasetId = updateDataset(conn, rowData, userId);

                        // Handle segment assignment for UPDATE operations
                        Long newSegmentId = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            newSegmentId = determineSegmentId(segmentMode, segment, rowData);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                newSegmentId = determineSegmentId(null, null, rowData);
                            }
                        }

                        if (newSegmentId != null) {
                            try {
                                // Get current segment
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) datasetId,
                                        "Dataset");

                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change
                                    SegmentValidationService validator = new SegmentValidationService();
                                    Integer systemId = getInteger(rowData, "System_ID");
                                    SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            datasetId, newSegmentId.intValue(), "Dataset", systemId);

                                    if (!result.isValid && !result.canProceedWithWarning) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }

                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                            (long) datasetId,
                                            "Dataset",
                                            newSegmentId,
                                            userId);
                                    logger.info("Assigned Dataset {} to Segment {} (updated)", datasetId, newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign Dataset {} to segment: {}", datasetId,
                                        segEx.getMessage(), segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    datasetId,
                                    "Data Sets",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Dataset {}: {}", datasetId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;

                        // Insert into valuesjob_dataset table for update too
                        jobDAO.insertValuesjobDataset(jobId, datasetId);

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Dataset updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        deleteDataset(conn, rowData);
                        deletedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Dataset deleted successfully", "info");
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Dataset", "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                            "Failed: " + e.getMessage(), "error");

                    if ("Cancel on Warning".equalsIgnoreCase(errorHandling)) {
                        conn.rollback();
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Failed at row " + rowNumber);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                "Failed at row " + rowNumber, insertedCount, updatedCount, deletedCount, failedCount);
                        return;
                    }
                }
            }

            conn.commit();
            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.updateJobProgress(jobId, "Completed",
                    String.format("Complete: %d inserted, %d updated, %d deleted, %d failed",
                            insertedCount, updatedCount, deletedCount, failedCount));

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Processing complete", insertedCount, updatedCount, deletedCount, failedCount);

            // Send success notification
            sendNotification(userId, "Dataset", uploadOption, insertedCount, updatedCount,
                    deletedCount, failedCount, true, null, jobId);

        } catch (Exception e) {
            logger.error("Error processing dataset bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "Dataset", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, false, e.getMessage(), jobId);
            } catch (Exception e2) {
                logger.error("Error updating job status after failure", e2);
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Insert Dataset
     */
    private int insertDataset(Connection conn, JsonObject rowData, int userId, String segmentMode, String segment)
            throws SQLException {
        Dataset dataset = new Dataset();

        // Auto-generate Ref if empty
        String refNumber = getString(rowData, "Ref.");
        if (refNumber == null || refNumber.trim().isEmpty()) {
            refNumber = ReferenceNumberGenerator.generateDatasetRefNumber();
        }
        dataset.setRefNumber(refNumber);

        dataset.setPrimaryName(getString(rowData, "Name"));
        dataset.setDefinition(getString(rowData, "Definition"));
        dataset.setUsage(getString(rowData, "Usage"));
        dataset.setMasterSource(getInteger(rowData, "System_ID"));
        dataset.setGlossary(getInteger(rowData, "Glossary_ID"));
        dataset.setDatasetType(getInteger(rowData, "Type_ID"));
        dataset.setLifecycle(getInteger(rowData, "Lifecycle_ID"));
        dataset.setStatus(getInteger(rowData, "BUDG Status_ID"));
        dataset.setAccessControlType(getInteger(rowData, "BUDG Viewing_ID"));

        int datasetId = datasetDAO.insert(dataset, userId);

        // Assign object to segment
        Long segmentIdToAssign = null;
        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
            segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
        } else {
            // Check if Segment column exists in Excel even when segmentMode is null
            String segmentName = getString(rowData, "Segment");
            if (segmentName != null && !segmentName.trim().isEmpty()) {
                segmentIdToAssign = determineSegmentId(null, null, rowData);
            }
        }

        if (segmentIdToAssign != null) {
            try {
                ObjectSegmentService.assignObjectToSegment(
                        (long) datasetId,
                        "Dataset",
                        segmentIdToAssign,
                        userId);
                logger.info("Assigned Dataset {} to Segment {}", datasetId, segmentIdToAssign);
            } catch (Exception segEx) {
                logger.error("Failed to assign Dataset {} to segment: {}", datasetId, segEx.getMessage(), segEx);
                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(), segEx);
            }
        }

        return datasetId;
    }

    /**
     * Update Dataset
     */
    private int updateDataset(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int datasetId = rowData.get("ID").getAsInt();

        Dataset dataset = new Dataset();
        dataset.setId(datasetId);
        dataset.setPrimaryName(getString(rowData, "Name"));
        dataset.setDefinition(getString(rowData, "Definition"));
        dataset.setUsage(getString(rowData, "Usage"));
        dataset.setMasterSource(getInteger(rowData, "System_ID"));
        dataset.setGlossary(getInteger(rowData, "Glossary_ID"));
        dataset.setDatasetType(getInteger(rowData, "Type_ID"));
        dataset.setLifecycle(getInteger(rowData, "Lifecycle_ID"));
        dataset.setStatus(getInteger(rowData, "BUDG Status_ID"));
        dataset.setAccessControlType(getInteger(rowData, "BUDG Viewing_ID"));

        datasetDAO.update(dataset, userId);
        return datasetId;
    }

    /**
     * Delete Dataset (soft delete)
     */
    private void deleteDataset(Connection conn, JsonObject rowData) throws SQLException {
        int datasetId = rowData.get("ID").getAsInt();
        datasetDAO.deleteById(datasetId);
    }

    /**
     * Process Glossary data
     */
    public void processGlossaryData(int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        int insertedCount = 0;
        int updatedCount = 0;
        int deletedCount = 0;
        int failedCount = 0;
        int totalRows = validatedData.size();

        Connection conn = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            jobDAO.updateJobStatus(jobId, "Processing", false);
            jobDAO.createJobProgress(jobId, 30, "Processing", "Starting data insertion...");
            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", 30,
                    "Starting data insertion...", 0, 0, 0, 0);

            for (int i = 0; i < validatedData.size(); i++) {
                JsonObject rowData = validatedData.get(i).getAsJsonObject();
                String operation = rowData.get("operation").getAsString();
                int rowNumber = rowData.get("row_number").getAsInt();

                if (i % 10 == 0) {
                    int progressPercent = 30 + (int) ((i / (double) totalRows) * 65);
                    BulkUploadBroadcaster.getInstance().broadcast(jobId, "Processing", progressPercent,
                            String.format("Processing row %d of %d", i + 1, totalRows),
                            insertedCount, updatedCount, deletedCount, failedCount);
                }

                try {
                    if ("INSERT".equals(operation)) {
                        int glossaryId = insertGlossary(conn, rowData, userId, segmentMode, segment);

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    glossaryId,
                                    "Glossary",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Glossary {}: {}", glossaryId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        insertedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Glossary created successfully", "info");

                    } else if ("UPDATE".equals(operation)) {
                        int glossaryId = updateGlossary(conn, rowData, userId);

                        // Handle segment assignment for UPDATE operations
                        Long newSegmentId = null;
                        if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                            newSegmentId = determineSegmentId(segmentMode, segment, rowData);
                        } else {
                            // Check if Segment column exists in Excel even when segmentMode is null
                            String segmentName = getString(rowData, "Segment");
                            if (segmentName != null && !segmentName.trim().isEmpty()) {
                                newSegmentId = determineSegmentId(null, null, rowData);
                            }
                        }

                        if (newSegmentId != null) {
                            try {
                                // Get current segment
                                Long currentSegmentId = ObjectSegmentService.getObjectSegment((long) glossaryId,
                                        "Glossary");

                                // Only assign if segment is changing
                                if (currentSegmentId == null || !currentSegmentId.equals(newSegmentId)) {
                                    // Validate segment change
                                    SegmentValidationService validator = new SegmentValidationService();
                                    Integer parentId = getInteger(rowData, "Parent_ID");
                                    SegmentValidationService.ValidationResult result = validator.validateSegmentMove(
                                            glossaryId, newSegmentId.intValue(), "Glossary", parentId);

                                    if (!result.isValid && !result.canProceedWithWarning) {
                                        throw new RuntimeException("Segment validation failed: " + result.message);
                                    }

                                    // Assign new segment
                                    ObjectSegmentService.assignObjectToSegment(
                                            (long) glossaryId,
                                            "Glossary",
                                            newSegmentId,
                                            userId);
                                    logger.info("Assigned Glossary {} to Segment {} (updated)", glossaryId,
                                            newSegmentId);
                                }
                            } catch (Exception segEx) {
                                logger.error("Failed to assign Glossary {} to segment: {}", glossaryId,
                                        segEx.getMessage(), segEx);
                                throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                        segEx);
                            }
                        }

                        // Save custom field values
                        try {
                            CustomFieldBulkUploadHelper.saveCustomFieldValuesFromRowData(
                                    glossaryId,
                                    "Glossary",
                                    rowData,
                                    userId,
                                    conn);
                        } catch (Exception cfEx) {
                            logger.error("Failed to save custom fields for Glossary {}: {}", glossaryId,
                                    cfEx.getMessage(), cfEx);
                            throw new RuntimeException("Failed to save custom fields: " + cfEx.getMessage(), cfEx);
                        }

                        updatedCount++;

                        int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Glossary updated successfully", "info");

                    } else if ("DELETE".equals(operation)) {
                        deleteGlossary(conn, rowData);
                        deletedCount++;
                        int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "success", rowNumber);
                        jobDAO.createJobReportItemMessage(reportItemId, "SUCCESS",
                                "Glossary deleted successfully", "info");
                    }

                } catch (Exception e) {
                    logger.error("Error processing row {}: {}", rowNumber, e.getMessage(), e);
                    failedCount++;
                    int reportItemId = jobDAO.createJobReportItem(jobId, "Glossary", "failed", rowNumber);
                    jobDAO.createJobReportItemMessage(reportItemId, "ERROR",
                            "Failed: " + e.getMessage(), "error");

                    if ("Cancel on Warning".equalsIgnoreCase(errorHandling)) {
                        conn.rollback();
                        jobDAO.updateJobStatus(jobId, "Failed", true);
                        jobDAO.updateJobProgress(jobId, "Failed", "Failed at row " + rowNumber);
                        BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                                "Failed at row " + rowNumber, insertedCount, updatedCount, deletedCount, failedCount);
                        return;
                    }
                }
            }

            conn.commit();
            jobDAO.updateJobStatus(jobId, "Completed", true);
            jobDAO.updateJobProgress(jobId, "Completed",
                    String.format("Complete: %d inserted, %d updated, %d deleted, %d failed",
                            insertedCount, updatedCount, deletedCount, failedCount));

            BulkUploadBroadcaster.getInstance().broadcast(jobId, "Completed", 100,
                    "Processing complete", insertedCount, updatedCount, deletedCount, failedCount);

            // Send success notification
            sendNotification(userId, "Glossary", uploadOption, insertedCount, updatedCount,
                    deletedCount, failedCount, true, null, jobId);

        } catch (Exception e) {
            logger.error("Error processing glossary bulk upload job {}", jobId, e);
            try {
                if (conn != null)
                    conn.rollback();
                jobDAO.updateJobStatus(jobId, "Failed", true);
                jobDAO.updateJobProgress(jobId, "Failed", "Error: " + e.getMessage());
                BulkUploadBroadcaster.getInstance().broadcast(jobId, "Failed", 100,
                        "Failed: " + e.getMessage(), insertedCount, updatedCount, deletedCount, failedCount);

                // Send failure notification
                sendNotification(userId, "Glossary", uploadOption, insertedCount, updatedCount,
                        deletedCount, failedCount, false, e.getMessage(), jobId);
            } catch (Exception e2) {
                logger.error("Error updating job status after failure", e2);
            }
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    logger.error("Error closing connection", e);
                }
            }
        }
    }

    /**
     * Insert Glossary
     */
    private int insertGlossary(Connection conn, JsonObject rowData, int userId, String segmentMode, String segment)
            throws SQLException {
        String refNumber = getString(rowData, "Ref.");
        if (refNumber == null || refNumber.trim().isEmpty()) {
            refNumber = ReferenceNumberGenerator.generateGlossaryRefNumber();
        }

        String sql = "INSERT INTO glossary (Name, Long_Name, Definition, Ref_Number, Acronym, " +
                "Parent_ID, is_Public, status, Createdby_UserID, CreateDatetime) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int idx = 1;
            ps.setString(idx++, getString(rowData, "Primary Name"));
            ps.setString(idx++, getString(rowData, "Long Name"));
            ps.setString(idx++, getString(rowData, "Definition"));
            ps.setString(idx++, refNumber);
            ps.setString(idx++, getString(rowData, "Acronym"));
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));

            Integer viewingId = coalesce(getInteger(rowData, "BUDG Viewing_ID"), getFirstViewingId());
            setNullableInt(ps, idx++, viewingId);

            Integer statusId = coalesce(getInteger(rowData, "BUDG Status_ID"), getFirstStatusId());
            setNullableInt(ps, idx++, statusId);

            ps.setInt(idx++, userId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert failed, no rows affected");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int glossaryId = rs.getInt(1);

                    // Handle alias names
                    String aliasNames = getString(rowData, "Alias Names");
                    if (aliasNames != null && !aliasNames.trim().isEmpty()) {
                        insertGlossaryAliasNames(conn, glossaryId, aliasNames);
                    }

                    // Link stakeholder
                    linkGlossaryStakeholder(conn, glossaryId, rowData, userId);

                    // Assign object to segment
                    Long segmentIdToAssign = null;
                    if (segmentMode != null && !segmentMode.trim().isEmpty()) {
                        segmentIdToAssign = determineSegmentId(segmentMode, segment, rowData);
                    } else {
                        // Check if Segment column exists in Excel even when segmentMode is null
                        String segmentName = getString(rowData, "Segment");
                        if (segmentName != null && !segmentName.trim().isEmpty()) {
                            segmentIdToAssign = determineSegmentId(null, null, rowData);
                        }
                    }

                    if (segmentIdToAssign != null) {
                        try {
                            ObjectSegmentService.assignObjectToSegment(
                                    (long) glossaryId,
                                    "Glossary",
                                    segmentIdToAssign,
                                    userId);
                            logger.info("Assigned Glossary {} to Segment {}", glossaryId, segmentIdToAssign);
                        } catch (Exception segEx) {
                            logger.error("Failed to assign Glossary {} to segment: {}", glossaryId, segEx.getMessage(),
                                    segEx);
                            throw new RuntimeException("Failed to assign object to segment: " + segEx.getMessage(),
                                    segEx);
                        }
                    }

                    // Create audit records
                    glossaryDAO.createGlossaryAuditRecord(glossaryId);

                    logger.info("Glossary created with ID: {}", glossaryId);
                    return glossaryId;
                }
            }
        }
        throw new SQLException("Failed to insert glossary, no ID generated");
    }

    /**
     * Update Glossary
     */
    private int updateGlossary(Connection conn, JsonObject rowData, int userId) throws SQLException {
        int glossaryId = rowData.get("ID").getAsInt();

        String sql = "UPDATE glossary SET PrimaryName = ?, Long_Name = ?, Definition = ?, Acronym = ?, " +
                "Parent_ID = ?, is_Public = ?, status = ?, Last_updated_UserID = ?, Last_Updated_Datetime = NOW() " +
                "WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, getString(rowData, "Primary Name"));
            ps.setString(idx++, getString(rowData, "Long Name"));
            ps.setString(idx++, getString(rowData, "Definition"));
            ps.setString(idx++, getString(rowData, "Acronym"));
            setNullableInt(ps, idx++, getInteger(rowData, "Parent_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "BUDG Viewing_ID"));
            setNullableInt(ps, idx++, getInteger(rowData, "BUDG Status_ID"));
            ps.setInt(idx++, userId);
            ps.setInt(idx++, glossaryId);

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Update failed, glossary not found: " + glossaryId);
            }

            // Handle alias names
            String aliasNames = getString(rowData, "Alias Names");
            if (aliasNames != null && !aliasNames.trim().isEmpty()) {
                // Delete existing aliases and insert new ones
                deleteGlossaryAliasNames(conn, glossaryId);
                insertGlossaryAliasNames(conn, glossaryId, aliasNames);
            }

            // Create audit record
            glossaryDAO.createGlossaryAuditRecord(glossaryId);

            logger.info("Glossary updated with ID: {}", glossaryId);
            return glossaryId;
        }
    }

    /**
     * Delete Glossary (soft delete)
     */
    private void deleteGlossary(Connection conn, JsonObject rowData) throws SQLException {
        int glossaryId = rowData.get("ID").getAsInt();
        String sql = "UPDATE glossary SET Deleted_datetime = NOW() WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Delete failed, glossary not found: " + glossaryId);
            }
        }
    }

    /**
     * Insert glossary alias names
     */
    private void insertGlossaryAliasNames(Connection conn, int glossaryId, String aliasNames) throws SQLException {
        String[] aliases = aliasNames.split(",");
        String sql = "INSERT INTO glossary_alias_name (glossary_id, alias_name) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String alias : aliases) {
                String trimmed = alias.trim();
                if (!trimmed.isEmpty()) {
                    ps.setInt(1, glossaryId);
                    ps.setString(2, trimmed);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    /**
     * Delete glossary alias names
     */
    private void deleteGlossaryAliasNames(Connection conn, int glossaryId) throws SQLException {
        String sql = "DELETE FROM glossary_alias_name WHERE glossary_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, glossaryId);
            ps.executeUpdate();
        }
    }

    /**
     * Link glossary stakeholder
     */
    private void linkGlossaryStakeholder(Connection conn, int glossaryId, JsonObject rowData, int userId) {
        try {
            Integer stakeholderUserId = userId;
            String userEmail = getString(rowData, "User Email");
            if (userEmail != null && !userEmail.trim().isEmpty()) {
                Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
                if (resolvedUserId != null) {
                    stakeholderUserId = resolvedUserId;
                }
            }

            Integer governanceRoleId = coalesce(getInteger(rowData, "Governance Role_ID"),
                    getRoleIdByName(getString(rowData, "Governance Role")));

            if (governanceRoleId != null) {
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", stakeholderUserId);
                stakeholderData.put("roleId", governanceRoleId);

                // Create object_x_people first using GlossaryDAO
                int objectXPeopleId = glossaryDAO.createObjectXPeople(conn, stakeholderData, userId);

                // Link to glossary
                glossaryDAO.linkStakeholderToGlossary(conn, glossaryId, objectXPeopleId, userId);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder to glossary {}: {}", glossaryId, e.getMessage(), e);
        }
    }

    /**
     * Process Attribute data (placeholder - to be implemented)
     */
    public void processAttributeData(int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        // TODO: Implement attribute processing
        logger.warn("Attribute processing not yet implemented");
        try {
            jobDAO.updateJobStatus(jobId, "Failed", true);
            jobDAO.updateJobProgress(jobId, "Failed", "Attribute processing not yet implemented");
        } catch (SQLException e) {
            logger.error("Error updating job status", e);
        }
    }

    /**
     * Process Interface data (placeholder - to be implemented)
     */
    public void processInterfaceData(int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        // TODO: Implement interface processing
        logger.warn("Interface processing not yet implemented");
        try {
            jobDAO.updateJobStatus(jobId, "Failed", true);
            jobDAO.updateJobProgress(jobId, "Failed", "Interface processing not yet implemented");
        } catch (SQLException e) {
            logger.error("Error updating job status", e);
        }
    }

    /**
     * Process generic entity data (fallback)
     */
    private void processGenericEntityData(String entityName, int jobId, JsonArray validatedData, int userId,
            String errorHandling, String uploadOption, String segmentMode, String segment) {
        logger.warn("Generic processing not implemented for entity: {}", entityName);
        try {
            jobDAO.updateJobStatus(jobId, "Failed", true);
            jobDAO.updateJobProgress(jobId, "Failed", "Processing not implemented for entity: " + entityName);
        } catch (SQLException e) {
            logger.error("Error updating job status", e);
        }
    }

    // ========== Helper Methods ==========

    private String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private Integer getInteger(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsInt();
        }
        return null;
    }

    private boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return defaultValue;
    }

    private Boolean getBooleanOrNull(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsBoolean();
        }
        return null;
    }

    private Integer coalesce(Integer... values) {
        for (Integer value : values) {
            if (value != null)
                return value;
        }
        return null;
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value != null) {
            ps.setBoolean(index, value);
        } else {
            ps.setNull(index, Types.BOOLEAN);
        }
    }

    private Integer getFirstLookupId(String tableName) {
        try {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn
                            .prepareStatement("SELECT id FROM " + tableName + " ORDER BY id ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first lookup ID from table {}: {}", tableName, e.getMessage());
        }
        return null;
    }

    private Integer getFirstViewingId() {
        try {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT id FROM viewing ORDER BY id ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first viewing ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getFirstStatusId() {
        try {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT ID FROM status ORDER BY ID ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting first status ID: {}", e.getMessage());
        }
        return null;
    }

    private Integer getRoleIdByName(String roleName) {
        if (roleName == null || roleName.trim().isEmpty()) {
            return null;
        }
        try {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn
                            .prepareStatement("SELECT id FROM object_role WHERE primaryname = ? LIMIT 1")) {
                ps.setString(1, roleName.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting role ID by name: {}", roleName, e);
        }
        return null;
    }

    private String getUserName(int userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getUserName(conn, userId);
        } catch (SQLException e) {
            logger.error("Error getting user name for ID {}: {}", userId, e.getMessage());
        }
        return "Unknown User";
    }

    private String getUserName(Connection conn, int userId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT CONCAT(First_Name, ' ', Last_Name) as full_name FROM people WHERE ID = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("full_name");
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user name for ID {}: {}", userId, e.getMessage());
        }
        return "Unknown User";
    }

    private Integer getUserIdByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        try {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT ID FROM people WHERE Email = ? AND Deleted_date IS NULL LIMIT 1")) {
                ps.setString(1, email.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("ID");
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user ID by email: {}", email, e);
        }
        return null;
    }

    private void linkSystemStakeholder(Connection conn, int systemId, JsonObject rowData, int userId) {
        try {
            Integer stakeholderUserId = userId;
            String userEmail = getString(rowData, "User Email");
            if (userEmail != null && !userEmail.trim().isEmpty()) {
                Integer resolvedUserId = getUserIdByEmail(userEmail.trim());
                if (resolvedUserId != null) {
                    stakeholderUserId = resolvedUserId;
                }
            }

            Integer governanceRoleId = coalesce(getInteger(rowData, "Governance Role_ID"),
                    getRoleIdByName(getString(rowData, "Governance Role")));

            if (governanceRoleId == null) {
                governanceRoleId = getRoleIdByName("System Owner");
            }

            if (governanceRoleId != null) {
                Map<String, Object> stakeholderData = new HashMap<>();
                stakeholderData.put("userId", stakeholderUserId);
                stakeholderData.put("roleId", governanceRoleId);

                int objectXPeopleId = systemDAO.createObjectXPeople(conn, stakeholderData, userId);
                systemDAO.linkStakeholderToSystem(conn, systemId, objectXPeopleId);

                String userName = getUserName(stakeholderUserId);
                systemDAO.createStakeholderAuditRecords(systemId, userName, userName, governanceRoleId, conn);
            }
        } catch (Exception e) {
            logger.error("Error linking stakeholder to system {}: {}", systemId, e.getMessage(), e);
        }
    }

    /**
     * Determine segment ID based on segment mode
     */
    private Long determineSegmentId(String segmentMode, String selectedSegment, JsonObject rowData)
            throws SQLException {
        if (segmentMode == null || segmentMode.trim().isEmpty()) {
            // Even if segmentMode is null, check if Segment column exists in Excel
            String segmentName = getString(rowData, "Segment");
            if (segmentName != null && !segmentName.trim().isEmpty()) {
                try (Connection conn = DatabaseConnection.getConnection();
                        PreparedStatement ps = conn.prepareStatement(
                                "SELECT id FROM segment WHERE name = ? AND Deleted_At IS NULL LIMIT 1")) {
                    ps.setString(1, segmentName.trim());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            return rs.getLong("id");
                        }
                    }
                }
            }
            return null;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            switch (segmentMode) {
                case "Enterprise":
                case "ENTERPRISE":
                    // Return Enterprise segment (ID = 1)
                    return 1L;

                case "Multiple":
                case "MULTIPLE":
                    // Get segment from row data
                    String segmentName = getString(rowData, "Segment");
                    if (segmentName == null || segmentName.trim().isEmpty()) {
                        throw new IllegalArgumentException("Segment is required when 'Multiple' mode is selected");
                    }

                    // Look up segment by name
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM segment WHERE name = ? AND Deleted_At IS NULL LIMIT 1")) {
                        ps.setString(1, segmentName.trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                return rs.getLong("id");
                            }
                        }
                    }
                    throw new IllegalArgumentException("Segment '" + segmentName + "' does not exist");

                case "Specific":
                case "SPECIFIC":
                    // Use selected segment from UI
                    if (selectedSegment == null || selectedSegment.trim().isEmpty()) {
                        throw new IllegalArgumentException("Segment ID is required when 'Specific' mode is selected");
                    }
                    try {
                        return Long.parseLong(selectedSegment.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid segment ID: " + selectedSegment);
                    }

                default:
                    logger.warn("Unknown segment mode: {}, defaulting to Enterprise", segmentMode);
                    return 1L;
            }
        }
    }

    /**
     * Send notification
     */
    private void sendNotification(int userId, String entityName, String uploadOption,
            int insertedCount, int updatedCount, int deletedCount,
            int failedCount, boolean success, String errorMessage, int jobId) {
        try {
            WorkflowNotification n = new WorkflowNotification();
            n.setRecipientUserId(userId);
            n.setCategory("bulk_upload");

            String titleSuffix = uploadOption != null ? uploadOption : "Bulk Upload";
            n.setTitle(entityName + " : " + titleSuffix);

            if (success) {
                n.setMessage(String.format("Bulk upload completed: %d inserted, %d updated, %d deleted, %d failed",
                        insertedCount, updatedCount, deletedCount, failedCount));
                n.setEventType("UPLOAD_COMPLETE");
            } else {
                String failureMsg = "Bulk upload failed";
                if (errorMessage != null && !errorMessage.isEmpty()) {
                    failureMsg = errorMessage.length() > 200 ? errorMessage.substring(0, 197) + "..." : errorMessage;
                }
                n.setMessage(failureMsg);
                n.setEventType("UPLOAD_FAILED");
            }

            n.setChannel("ui");
            n.setFacetType("system");
            n.setObjectId(jobId);
            n.setRead(false);

            notificationDAO.create(n);
        } catch (Exception e) {
            logger.error("Failed to send notification: {}", e.getMessage(), e);
        }
    }
}
