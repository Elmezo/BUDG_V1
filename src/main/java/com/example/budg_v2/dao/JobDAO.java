package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JobDAO {

    // Job queries
    private static final String INSERT_JOB = "INSERT INTO job (Created_date, Completed_date, Type, Reference_Name, Items_Count, Status, Created_By, Child_Jobs_Order) "
            +
            "VALUES (NOW(), NULL, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_JOB_STATUS = "UPDATE job SET Status = ?, Completed_date = ? WHERE ID = ?";

    private static final String UPDATE_JOB_ITEMS_COUNT = "UPDATE job SET Items_Count = ? WHERE ID = ?";

    private static final String SELECT_JOB_BY_ID = "SELECT * FROM job WHERE ID = ?";

    // Job Resource File queries
    private static final String INSERT_JOB_RESOURCE_FILE = "INSERT INTO job_resource_filename (JobID, FileName, Original_FileName, Storage_Path, StoreFile, Retention_Days, Delete_At) "
            +
            "VALUES (?, ?, ?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL ? DAY))";

    private static final String SELECT_JOB_RESOURCE_BY_JOB_ID = "SELECT * FROM job_resource_filename WHERE JobID = ?";

    // Job Progress queries
    private static final String INSERT_JOB_PROGRESS = "INSERT INTO job_progress (Job_ID, Expected_ticks, Status, Message) "
            +
            "VALUES (?, ?, ?, ?)";

    private static final String UPDATE_JOB_PROGRESS = "UPDATE job_progress SET Status = ?, Message = ? WHERE Job_ID = ?";

    private static final String SELECT_JOB_PROGRESS = "SELECT * FROM job_progress WHERE Job_ID = ? ORDER BY ID DESC LIMIT 1";

    // Job Report Item queries
    private static final String INSERT_JOB_REPORT_ITEM = "INSERT INTO job_report_item (Job_ID, Field_Name, Status, Position) "
            +
            "VALUES (?, ?, ?, ?)";

    private static final String INSERT_JOB_REPORT_MESSAGE = "INSERT INTO job_report_item_messages (Report_ID, Error_Code, Message, Type) "
            +
            "VALUES (?, ?, ?, ?)";

    private static final String SELECT_JOB_REPORT_ITEMS = "SELECT * FROM job_report_item WHERE Job_ID = ? ORDER BY Position";

    private static final String SELECT_JOB_REPORT_MESSAGES = "SELECT * FROM job_report_item_messages WHERE Report_ID = ?";

    // Job Configuration queries
    private static final String INSERT_JOB_CONFIGURATION = "INSERT INTO job_configuration (Job_Entity_ID, Option_KeyID, Option_value) "
            +
            "VALUES (?, ?, ?)";

    private static final String SELECT_JOB_CONFIGURATIONS = "SELECT * FROM job_configuration WHERE Job_Entity_ID = ?";

    private static final String DELETE_JOB_CONFIGURATIONS = "DELETE FROM job_configuration WHERE Job_Entity_ID = ?";

    // Job Field Mapping queries
    private static final String INSERT_JOB_FIELD_MAPPING = "INSERT INTO job_field_mapping (Job_Entity_ID, Source_field, Target_field) "
            +
            "VALUES (?, ?, ?)";

    private static final String SELECT_JOB_FIELD_MAPPINGS = "SELECT * FROM job_field_mapping WHERE Job_Entity_ID = ?";

    private static final String DELETE_JOB_FIELD_MAPPINGS = "DELETE FROM job_field_mapping WHERE Job_Entity_ID = ?";

    // Job Resource File Binary queries
    private static final String INSERT_JOB_RESOURCE_FILE_BINARY = "INSERT INTO job_resource_file (FileID, File) VALUES (?, ?)";

    private static final String SELECT_JOB_RESOURCE_FILE_BINARY = "SELECT * FROM job_resource_file WHERE FileID = ?";

    private static final String DELETE_JOB_RESOURCE_FILE_BINARY = "DELETE FROM job_resource_file WHERE FileID = ?";

    // Job Config Keys queries
    private static final String SELECT_ALL_CONFIG_KEYS = "SELECT * FROM job_configkeys ORDER BY KeyID";

    private static final String SELECT_CONFIG_KEY_BY_ID = "SELECT * FROM job_configkeys WHERE KeyID = ?";

    private static final String SELECT_CONFIG_KEY_BY_NAME = "SELECT * FROM job_configkeys WHERE Option_Key = ?";

    /**
     * Create a new job record
     * 
     * @param type           Job type in format: <FacetName> + (Upload / Update /
     *                       Delete), e.g., "System Upload", "Dataset Delete"
     * @param referenceName  Reference name for the job
     * @param itemsCount     Number of items to process
     * @param status         Job status (Pending, Processing, Completed, Failed)
     * @param createdBy      User ID who created the job
     * @param childJobsOrder Order of child jobs if job is split into multiple parts
     *                       (null if not split)
     * @return Generated job ID
     */
    public int createJob(String type, String referenceName, Integer itemsCount,
            String status, Integer createdBy, Integer childJobsOrder) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, type);
            pstmt.setString(2, referenceName);
            pstmt.setObject(3, itemsCount);
            pstmt.setString(4, status);
            pstmt.setObject(5, createdBy);
            pstmt.setObject(6, childJobsOrder);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Create a new job record (overloaded without childJobsOrder)
     */
    public int createJob(String type, String referenceName, Integer itemsCount,
            String status, Integer createdBy) throws SQLException {
        return createJob(type, referenceName, itemsCount, status, createdBy, null);
    }

    /**
     * Update job status and optionally set completed date
     */
    public boolean updateJobStatus(int jobId, String status, boolean setCompletedDate) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(UPDATE_JOB_STATUS)) {

            pstmt.setString(1, status);
            if (setCompletedDate) {
                pstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            } else {
                pstmt.setNull(2, Types.TIMESTAMP);
            }
            pstmt.setInt(3, jobId);

            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Update job items count
     */
    public boolean updateJobItemsCount(int jobId, int itemsCount) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(UPDATE_JOB_ITEMS_COUNT)) {

            pstmt.setInt(1, itemsCount);
            pstmt.setInt(2, jobId);

            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Get job by ID
     */
    public Job getJobById(int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_BY_ID)) {

            pstmt.setInt(1, jobId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJob(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get current job status by ID (e.g. to avoid overwriting Completed/Partially
     * Completed with Failed).
     * 
     * @return Status string or null if job not found
     */
    public String getJobStatus(int jobId) throws SQLException {
        Job job = getJobById(jobId);
        return job != null ? job.getStatus() : null;
    }

    /**
     * Create job resource file record
     */
    public int createJobResourceFile(int jobId, String fileName, String originalFileName,
            String storagePath, boolean storeFile,
            int retentionDays) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_RESOURCE_FILE,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, jobId);
            pstmt.setString(2, fileName);
            pstmt.setString(3, originalFileName);
            pstmt.setString(4, storagePath);
            pstmt.setBoolean(5, storeFile);
            pstmt.setInt(6, retentionDays);
            pstmt.setInt(7, retentionDays);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job resource file failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job resource file failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get job resource file by job ID
     */
    public JobResourceFile getJobResourceFileByJobId(int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_RESOURCE_BY_JOB_ID)) {

            pstmt.setInt(1, jobId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJobResourceFile(rs);
                }
            }
        }
        return null;
    }

    /**
     * Create job progress record
     */
    public int createJobProgress(int jobId, int expectedTicks, String status,
            String message) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_PROGRESS,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, jobId);
            pstmt.setInt(2, expectedTicks);
            pstmt.setString(3, status);
            pstmt.setString(4, message);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job progress failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job progress failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Update job progress
     */
    public boolean updateJobProgress(int jobId, String status, String message) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(UPDATE_JOB_PROGRESS)) {

            pstmt.setString(1, status);
            pstmt.setString(2, message);
            pstmt.setInt(3, jobId);

            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Get job progress
     */
    public JobProgress getJobProgress(int jobId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_PROGRESS)) {

            pstmt.setInt(1, jobId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJobProgress(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get latest job progress rows in batch for job IDs.
     */
    public Map<Integer, JobProgress> getJobProgressForJobIds(List<Integer> jobIds) throws SQLException {
        if (jobIds == null || jobIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = String.join(",", Collections.nCopies(jobIds.size(), "?"));
        String query = "SELECT jp.* FROM job_progress jp " +
                "JOIN (" +
                "  SELECT Job_ID, MAX(ID) AS max_id " +
                "  FROM job_progress " +
                "  WHERE Job_ID IN (" + placeholders + ") " +
                "  GROUP BY Job_ID" +
                ") latest ON latest.max_id = jp.ID";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            int idx = 1;
            for (Integer jobId : jobIds) {
                pstmt.setInt(idx++, jobId);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Integer, JobProgress> result = new LinkedHashMap<>();
                while (rs.next()) {
                    JobProgress progress = mapResultSetToJobProgress(rs);
                    result.put(progress.getJobId(), progress);
                }
                return result;
            }
        }
    }

    /**
     * Get job IDs that have a resource file row in batch.
     */
    public Set<Integer> getJobIdsWithResourceFile(List<Integer> jobIds) throws SQLException {
        if (jobIds == null || jobIds.isEmpty()) {
            return Collections.emptySet();
        }
        String placeholders = String.join(",", Collections.nCopies(jobIds.size(), "?"));
        String query = "SELECT DISTINCT JobID FROM job_resource_filename WHERE JobID IN (" + placeholders + ")";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            int idx = 1;
            for (Integer jobId : jobIds) {
                pstmt.setInt(idx++, jobId);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                Set<Integer> result = new LinkedHashSet<>();
                while (rs.next()) {
                    result.add(rs.getInt("JobID"));
                }
                return result;
            }
        }
    }

    /**
     * Create job report item
     */
    public int createJobReportItem(int jobId, String fieldName, String status,
            int position) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_REPORT_ITEM,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, jobId);
            pstmt.setString(2, fieldName);
            pstmt.setString(3, status);
            pstmt.setInt(4, position);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job report item failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job report item failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Create job report item message
     */
    public int createJobReportItemMessage(int reportId, String errorCode,
            String message, String type) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_REPORT_MESSAGE,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, reportId);
            pstmt.setString(2, errorCode);
            pstmt.setString(3, message);
            pstmt.setString(4, type);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job report message failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job report message failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get job report items with messages
     */
    public List<JobReportItem> getJobReportItems(int jobId) throws SQLException {
        List<JobReportItem> reportItems = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_REPORT_ITEMS)) {

            pstmt.setInt(1, jobId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JobReportItem item = mapResultSetToJobReportItem(rs);

                    // Load messages for this report item
                    item.setMessages(getJobReportMessages(conn, item.getId()));

                    reportItems.add(item);
                }
            }
        }

        return reportItems;
    }

    /**
     * Get job report messages for a specific report item
     */
    private List<JobReportItemMessage> getJobReportMessages(Connection conn,
            int reportId) throws SQLException {
        List<JobReportItemMessage> messages = new ArrayList<>();

        try (PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_REPORT_MESSAGES)) {
            pstmt.setInt(1, reportId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToJobReportItemMessage(rs));
                }
            }
        }

        return messages;
    }

    // Helper methods to map ResultSet to model objects

    private Job mapResultSetToJob(ResultSet rs) throws SQLException {
        Job job = new Job();
        job.setId(rs.getInt("ID"));
        job.setCreatedDate(rs.getTimestamp("Created_date"));
        job.setCompletedDate(rs.getTimestamp("Completed_date"));
        job.setType(rs.getString("Type"));
        job.setReferenceName(rs.getString("Reference_Name"));
        job.setItemsCount(rs.getObject("Items_Count", Integer.class));
        job.setStatus(rs.getString("Status"));
        job.setCreatedBy(rs.getObject("Created_By", Integer.class));
        job.setChildJobsOrder(rs.getObject("Child_Jobs_Order", Integer.class));
        return job;
    }

    private JobResourceFile mapResultSetToJobResourceFile(ResultSet rs) throws SQLException {
        JobResourceFile file = new JobResourceFile();
        file.setId(rs.getInt("ID"));
        file.setJobId(rs.getInt("JobID"));
        file.setFileName(rs.getString("FileName"));
        file.setOriginalFileName(rs.getString("Original_FileName"));
        file.setStoragePath(rs.getString("Storage_Path"));
        file.setStoreFile(rs.getBoolean("StoreFile"));
        file.setRetentionDays(rs.getObject("Retention_Days", Integer.class));
        file.setDeleteAt(rs.getTimestamp("Delete_At"));
        return file;
    }

    private JobProgress mapResultSetToJobProgress(ResultSet rs) throws SQLException {
        JobProgress progress = new JobProgress();
        progress.setId(rs.getInt("ID"));
        progress.setJobId(rs.getInt("Job_ID"));
        progress.setExpectedTicks(rs.getObject("Expected_ticks", Integer.class));
        progress.setStatus(rs.getString("Status"));
        progress.setMessage(rs.getString("Message"));
        return progress;
    }

    private JobReportItem mapResultSetToJobReportItem(ResultSet rs) throws SQLException {
        JobReportItem item = new JobReportItem();
        item.setId(rs.getInt("ID"));
        item.setJobId(rs.getInt("Job_ID"));
        item.setFieldName(rs.getString("Field_Name"));
        item.setStatus(rs.getString("Status"));
        item.setPosition(rs.getObject("Position", Integer.class));
        return item;
    }

    private JobReportItemMessage mapResultSetToJobReportItemMessage(ResultSet rs) throws SQLException {
        JobReportItemMessage message = new JobReportItemMessage();
        message.setId(rs.getInt("ID"));
        message.setReportId(rs.getInt("Report_ID"));
        message.setErrorCode(rs.getString("Error_Code"));
        message.setMessage(rs.getString("Message"));
        message.setType(rs.getString("Type"));
        return message;
    }

    /**
     * Get recent bulk upload jobs
     * 
     * @param limit  Maximum number of jobs to return
     * @param userId Filter by user ID (null for all users)
     * @return List of recent jobs
     */
    public List<Job> getRecentBulkJobs(int limit, Integer userId) throws SQLException {
        StringBuilder query = new StringBuilder(
                "SELECT * FROM Job WHERE Type LIKE '%bulk%' ");

        if (userId != null) {
            query.append("AND Created_By = ? ");
        }

        query.append("ORDER BY Created_date DESC LIMIT ?");

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query.toString())) {

            int paramIndex = 1;
            if (userId != null) {
                pstmt.setInt(paramIndex++, userId);
            }
            pstmt.setInt(paramIndex, limit);

            try (ResultSet rs = pstmt.executeQuery()) {
                List<Job> jobs = new ArrayList<>();
                while (rs.next()) {
                    jobs.add(mapResultSetToJob(rs));
                }
                return jobs;
            }
        }
    }

    /**
     * Get all bulk upload jobs (no limit)
     * 
     * @param userId Filter by user ID (null for all users)
     * @return List of all bulk jobs
     */
    public List<Job> getAllBulkJobs(Integer userId) throws SQLException {
        return getAllBulkJobs(userId, null, null);
    }

    /**
     * Get bulk upload jobs with optional pagination.
     * 
     * @param userId Filter by user ID (null for all users)
     * @param limit  Maximum rows to return (null for no limit)
     * @param offset Rows to skip before returning data (null/<=0 for none)
     * @return List of bulk jobs
     */
    public List<Job> getAllBulkJobs(Integer userId, Integer limit, Integer offset) throws SQLException {
        StringBuilder query = new StringBuilder(
                "SELECT * FROM Job WHERE Type LIKE '%bulk%' ");

        if (userId != null) {
            query.append("AND Created_By = ? ");
        }

        query.append("ORDER BY Created_date DESC ");

        if (limit != null && limit > 0) {
            query.append("LIMIT ? ");
            if (offset != null && offset > 0) {
                query.append("OFFSET ? ");
            }
        }

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query.toString())) {

            int paramIndex = 1;
            if (userId != null) {
                pstmt.setInt(paramIndex++, userId);
            }
            if (limit != null && limit > 0) {
                pstmt.setInt(paramIndex++, limit);
                if (offset != null && offset > 0) {
                    pstmt.setInt(paramIndex, offset);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                List<Job> jobs = new ArrayList<>();
                while (rs.next()) {
                    jobs.add(mapResultSetToJob(rs));
                }
                return jobs;
            }
        }
    }

    /**
     * Get job statistics from report items
     * 
     * @param jobId Job ID
     * @return Map with statistics (inserted, updated, deleted, failed)
     */
    public Map<String, Integer> getJobStatistics(int jobId) throws SQLException {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("inserted", 0);
        stats.put("updated", 0);
        stats.put("deleted", 0);
        stats.put("failed", 0);

        // Aggregate by Excel row (Position): success on a row overrides stale "skipped" placeholders from validation.
        String query = "SELECT jri.Position, jri.Status, " +
                "(SELECT jrim2.Message FROM Job_Report_Item_Messages jrim2 " +
                " WHERE jrim2.Report_ID = jri.ID AND jrim2.Error_Code = 'SUCCESS' LIMIT 1) as Message " +
                "FROM Job_Report_Item jri " +
                "WHERE jri.Job_ID = ?";

        Map<Integer, java.util.List<ReportRowStat>> byPosition = new java.util.HashMap<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, jobId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Integer pos = rs.getObject("Position", Integer.class);
                    if (pos == null) {
                        pos = -1;
                    }
                    String status = rs.getString("Status");
                    String message = rs.getString("Message");
                    byPosition.computeIfAbsent(pos, k -> new java.util.ArrayList<>())
                            .add(new ReportRowStat(status, message));
                }
            }
        }

        for (java.util.List<ReportRowStat> rowItems : byPosition.values()) {
            boolean hasSuccess = false;
            String successMessage = null;
            for (ReportRowStat item : rowItems) {
                if ("success".equalsIgnoreCase(item.status)) {
                    hasSuccess = true;
                    if (successMessage == null) {
                        successMessage = item.message;
                    }
                }
            }
            if (hasSuccess) {
                if (successMessage != null) {
                    String lowerMessage = successMessage.toLowerCase();
                    if (lowerMessage.contains("inserted") || lowerMessage.contains("created")) {
                        stats.put("inserted", stats.get("inserted") + 1);
                    } else if (lowerMessage.contains("updated")) {
                        stats.put("updated", stats.get("updated") + 1);
                    } else if (lowerMessage.contains("deleted")) {
                        stats.put("deleted", stats.get("deleted") + 1);
                    } else {
                        stats.put("inserted", stats.get("inserted") + 1);
                    }
                } else {
                    stats.put("inserted", stats.get("inserted") + 1);
                }
                continue;
            }
            boolean rowFailed = false;
            for (ReportRowStat item : rowItems) {
                if ("error".equalsIgnoreCase(item.status)
                        || "failed".equalsIgnoreCase(item.status)
                        || "skipped".equalsIgnoreCase(item.status)) {
                    rowFailed = true;
                    break;
                }
            }
            if (rowFailed) {
                stats.put("failed", stats.get("failed") + 1);
            }
        }

        return stats;
    }

    private static final class ReportRowStat {
        final String status;
        final String message;

        ReportRowStat(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    // ========== Job Configuration Methods ==========

    /**
     * Create a job configuration record
     * 
     * @param jobEntityId Job ID
     * @param optionKeyId Configuration key ID
     * @param optionValue Configuration value
     * @return Generated configuration ID
     */
    public int createJobConfiguration(int jobEntityId, int optionKeyId, String optionValue) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_CONFIGURATION,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, jobEntityId);
            pstmt.setInt(2, optionKeyId);
            pstmt.setString(3, optionValue);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job configuration failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job configuration failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get all configurations for a job
     * 
     * @param jobEntityId Job ID
     * @return List of job configurations
     */
    public List<JobConfiguration> getJobConfigurations(int jobEntityId) throws SQLException {
        List<JobConfiguration> configurations = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_CONFIGURATIONS)) {

            pstmt.setInt(1, jobEntityId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    configurations.add(mapResultSetToJobConfiguration(rs));
                }
            }
        }

        return configurations;
    }

    /**
     * Delete all configurations for a job
     * 
     * @param jobEntityId Job ID
     * @return true if deleted successfully
     */
    public boolean deleteJobConfigurations(int jobEntityId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(DELETE_JOB_CONFIGURATIONS)) {

            pstmt.setInt(1, jobEntityId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Get configuration value for a specific key
     * 
     * @param jobEntityId Job ID
     * @param optionKeyId Configuration key ID
     * @return Configuration value or null if not found
     */
    public String getJobConfigurationValue(int jobEntityId, int optionKeyId) throws SQLException {
        List<JobConfiguration> configs = getJobConfigurations(jobEntityId);
        for (JobConfiguration config : configs) {
            if (config.getOptionKeyId().equals(optionKeyId)) {
                return config.getOptionValue();
            }
        }
        return null;
    }

    // ========== Job Field Mapping Methods ==========

    /**
     * Create a job field mapping record
     * 
     * @param jobEntityId Job ID
     * @param sourceField Excel column name
     * @param targetField Database field name
     * @return Generated mapping ID
     */
    public int createJobFieldMapping(int jobEntityId, String sourceField, String targetField) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_FIELD_MAPPING,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, jobEntityId);
            pstmt.setString(2, sourceField);
            pstmt.setString(3, targetField);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job field mapping failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job field mapping failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get all field mappings for a job
     * 
     * @param jobEntityId Job ID
     * @return List of field mappings
     */
    public List<JobFieldMapping> getJobFieldMappings(int jobEntityId) throws SQLException {
        List<JobFieldMapping> mappings = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_FIELD_MAPPINGS)) {

            pstmt.setInt(1, jobEntityId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    mappings.add(mapResultSetToJobFieldMapping(rs));
                }
            }
        }

        return mappings;
    }

    /**
     * Delete all field mappings for a job
     * 
     * @param jobEntityId Job ID
     * @return true if deleted successfully
     */
    public boolean deleteJobFieldMappings(int jobEntityId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(DELETE_JOB_FIELD_MAPPINGS)) {

            pstmt.setInt(1, jobEntityId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Create multiple field mappings in batch
     * 
     * @param jobEntityId Job ID
     * @param mappings    Map of source field to target field
     * @return Number of mappings created
     */
    public int createJobFieldMappings(int jobEntityId, Map<String, String> mappings) throws SQLException {
        int count = 0;
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            createJobFieldMapping(jobEntityId, entry.getKey(), entry.getValue());
            count++;
        }
        return count;
    }

    // ========== Job Resource File Binary Methods ==========

    /**
     * Store binary file content
     * 
     * @param fileId      File ID from job_resource_filename table
     * @param fileContent Binary content of the file
     * @return Generated record ID
     */
    public int createJobResourceFileBinary(int fileId, byte[] fileContent) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(INSERT_JOB_RESOURCE_FILE_BINARY,
                        Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, fileId);
            pstmt.setBytes(2, fileContent);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating job resource file binary failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating job resource file binary failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Get binary file content
     * 
     * @param fileId File ID from job_resource_filename table
     * @return JobResourceFileBinary object with file content
     */
    public JobResourceFileBinary getJobResourceFileBinary(int fileId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_JOB_RESOURCE_FILE_BINARY)) {

            pstmt.setInt(1, fileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJobResourceFileBinary(rs);
                }
            }
        }
        return null;
    }

    /**
     * Delete binary file content
     * 
     * @param fileId File ID from job_resource_filename table
     * @return true if deleted successfully
     */
    public boolean deleteJobResourceFileBinary(int fileId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(DELETE_JOB_RESOURCE_FILE_BINARY)) {

            pstmt.setInt(1, fileId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // ========== Job Config Keys Methods ==========

    /**
     * Get all configuration keys
     * 
     * @return List of all configuration keys
     */
    public List<JobConfigKey> getAllConfigKeys() throws SQLException {
        List<JobConfigKey> keys = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL_CONFIG_KEYS)) {

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    keys.add(mapResultSetToJobConfigKey(rs));
                }
            }
        }

        return keys;
    }

    /**
     * Get configuration key by ID
     * 
     * @param keyId Key ID
     * @return JobConfigKey object or null if not found
     */
    public JobConfigKey getConfigKeyById(int keyId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_CONFIG_KEY_BY_ID)) {

            pstmt.setInt(1, keyId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJobConfigKey(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get configuration key by name
     * 
     * @param optionKey Key name (e.g., "Cancel on Warning")
     * @return JobConfigKey object or null if not found
     */
    public JobConfigKey getConfigKeyByName(String optionKey) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(SELECT_CONFIG_KEY_BY_NAME)) {

            pstmt.setString(1, optionKey);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToJobConfigKey(rs);
                }
            }
        }
        return null;
    }

    // ========== Helper Methods for Mapping ==========

    private JobConfiguration mapResultSetToJobConfiguration(ResultSet rs) throws SQLException {
        JobConfiguration config = new JobConfiguration();
        config.setId(rs.getInt("ID"));
        config.setJobEntityId(rs.getInt("Job_Entity_ID"));
        config.setOptionKeyId(rs.getInt("Option_KeyID"));
        config.setOptionValue(rs.getString("Option_value"));
        return config;
    }

    private JobFieldMapping mapResultSetToJobFieldMapping(ResultSet rs) throws SQLException {
        JobFieldMapping mapping = new JobFieldMapping();
        mapping.setId(rs.getInt("ID"));
        mapping.setJobEntityId(rs.getInt("Job_Entity_ID"));
        mapping.setSourceField(rs.getString("Source_field"));
        mapping.setTargetField(rs.getString("Target_field"));
        return mapping;
    }

    private JobResourceFileBinary mapResultSetToJobResourceFileBinary(ResultSet rs) throws SQLException {
        JobResourceFileBinary fileBinary = new JobResourceFileBinary();
        fileBinary.setId(rs.getInt("ID"));
        fileBinary.setFileId(rs.getInt("FileID"));
        fileBinary.setFile(rs.getBytes("File"));
        return fileBinary;
    }

    private JobConfigKey mapResultSetToJobConfigKey(ResultSet rs) throws SQLException {
        JobConfigKey key = new JobConfigKey();
        key.setKeyId(rs.getInt("KeyID"));
        key.setOptionKey(rs.getString("Option_Key"));
        return key;
    }

    // ========== Values Job Dataset Methods ==========

    /**
     * Insert a record into valuesjob_dataset table linking a job to a dataset
     */
    public void insertValuesjobDataset(int jobId, int datasetId) throws SQLException {
        String sql = "INSERT INTO valuesjob_dataset (JobID, Dataset_ID) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            ps.setInt(2, datasetId);
            ps.executeUpdate();
        }
    }

    // ========== Values Job Meta Attribute Methods ==========

    /**
     * Insert a record into valuesjob_meta_attribute table for job metadata
     */
    public void insertValuesjobMetaAttribute(int jobId, String metaAttributeName, String metaAttributeValue)
            throws SQLException {
        String sql = "INSERT INTO valuesjob_meta_attribute (JobID, Meta_Attribute_Name, Meta_Attribute_Value) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, jobId);
            ps.setString(2, metaAttributeName);
            ps.setString(3, metaAttributeValue);
            ps.executeUpdate();
        }
    }
}
