package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Process;

import com.example.budg_v2.service.SegmentAccessService;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessDAO {

    private static final String SELECT_ALL = "SELECT * FROM process WHERE deleteddatetime IS NULL ORDER BY primaryname";
    private static final String SELECT_BY_ID = "SELECT p.*, " +
            "CONCAT(cb.First_Name, ' ', cb.Last_Name) AS createdByName, " +
            "CONCAT(ub.First_Name, ' ', ub.Last_Name) AS lastUpdatedByName, " +
            "s.primaryname AS statusName " +
            "FROM process p " +
            "LEFT JOIN people cb ON cb.ID = p.createdby_id " +
            "LEFT JOIN people ub ON ub.ID = p.lastupdateuser_id " +
            "LEFT JOIN status s ON s.id = p.status " +
            "WHERE p.id = ? AND p.deleteddatetime IS NULL";
    private static final String SELECT_BY_NAME = "SELECT * FROM process WHERE primaryname = ? AND deleteddatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER = "SELECT * FROM process WHERE refnumber = ? AND deleteddatetime IS NULL";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM process WHERE LOWER(TRIM(refnumber)) = LOWER(TRIM(?)) AND id != ? AND deleteddatetime IS NULL";
    @SuppressWarnings("unused")
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_IDS = "SELECT * FROM process WHERE LOWER(TRIM(refnumber)) = LOWER(TRIM(?)) AND id NOT IN (?, ?) AND deleteddatetime IS NULL";
    private static final String SEARCH = "SELECT * FROM process WHERE (primaryname LIKE ? OR description LIKE ?) AND deleteddatetime IS NULL ORDER BY primaryname";
    private static final String SELECT_FOR_DROPDOWN = "SELECT id, primaryname, description FROM process ORDER BY primaryname";
    private static final String SELECT_FOR_PARENT_PICKER = "SELECT id, primaryname, description FROM process WHERE id != ? ORDER BY primaryname";
    private static final String INSERT = "INSERT INTO process (id, parentid, ispublic, status, type, duration_type, duration, lifecycle_status, processclass_id, processautomation_id, primaryname, refnumber, description, input_description, output_description, step_type, cancreate, canread, canupdate, candelete, canarchive, createdatetime, lastupdatedatetime, createdby_id, lastupdateuser_id) VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM process p), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL, ?, ?)";
    private static final String UPDATE = "UPDATE process SET parentid = ?, ispublic = ?, status = ?, type = ?, duration_type = ?, duration = ?, lifecycle_status = ?, processclass_id = ?, processautomation_id = ?, primaryname = ?, refnumber = ?, description = ?, input_description = ?, output_description = ?, step_type = ?, cancreate = ?, canread = ?, canupdate = ?, candelete = ?, canarchive = ?, lastupdatedatetime = ?, lastupdateuser_id = ? WHERE id = ?";
    private static final String SOFT_DELETE = "UPDATE process SET deleteddatetime = NOW() WHERE id = ?";
    
    // Recursive query: limit results to the selected process path (ancestors to root)
    // and the selected process subtree (descendants). Excludes siblings not on the path/subtree.
    private static final String SELECT_HIERARCHY = 
        "WITH RECURSIVE ancestors AS (\n" +
        "  SELECT p.id, p.parentid\n" +
        "  FROM process p\n" +
        "  WHERE p.id = ? AND p.deleteddatetime IS NULL\n" +
        "  UNION ALL\n" +
        "  SELECT parent.id, parent.parentid\n" +
        "  FROM process parent\n" +
        "  JOIN ancestors a ON a.parentid = parent.id\n" +
        "  WHERE parent.deleteddatetime IS NULL\n" +
        "), root AS (\n" +
        "  SELECT a.id\n" +
        "  FROM ancestors a\n" +
        "  WHERE a.parentid IS NULL\n" +
        "  LIMIT 1\n" +
        "), ph AS (\n" +
        "  SELECT p.id, p.parentid, 0 AS level, CAST(p.id AS CHAR(1000)) AS path\n" +
        "  FROM process p\n" +
        "  JOIN root r ON r.id = p.id\n" +
        "  WHERE p.deleteddatetime IS NULL\n" +
        "  UNION ALL\n" +
        "  SELECT c.id, c.parentid, ph.level + 1, CONCAT(ph.path, ',', c.id)\n" +
        "  FROM process c\n" +
        "  JOIN ph ON c.parentid = ph.id\n" +
        "  WHERE c.deleteddatetime IS NULL\n" +
        ")\n" +
        " , sel AS (SELECT ? AS selected_id)\n" +
        " , sel_path AS (\n" +
        "    SELECT ph.path AS path\n" +
        "    FROM ph JOIN sel ON ph.id = sel.selected_id\n" +
        "    LIMIT 1\n" +
        " )\n" +
        "SELECT p.id, p.parentid, p.ispublic, p.status, p.type, p.duration_type, p.duration,\n" +
        "       p.lifecycle_status, p.processclass_id, p.processautomation_id, p.primaryname,\n" +
        "       p.refnumber, p.description, p.input_description, p.output_description, p.step_type,\n" +
        "       p.cancreate, p.canread, p.canupdate, p.candelete, p.canarchive,\n" +
        "       p.createdatetime, p.lastupdatedatetime, p.deleteddatetime, p.createdby_id, p.lastupdateuser_id,\n" +
        "       CONCAT(cb.First_Name, ' ', cb.Last_Name) AS createdByName,\n" +
        "       CONCAT(ub.First_Name, ' ', ub.Last_Name) AS lastUpdatedByName,\n" +
        "       st.primaryname AS stepTypeName,\n" +
        "       s.primaryname AS statusName,\n" +
        "       ls.primaryname AS lifecycleName,\n" +
        "       pt.primaryname AS typeName,\n" +
        "       pc.primaryname AS classificationName,\n" +
        "       pa.primaryname AS automationName,\n" +
        "       parent.primaryname AS parentName,\n" +
        "       ph.level AS level,\n" +
        "       ph.path AS path,\n" +
        "       (SELECT GROUP_CONCAT(DISTINCT pred.refnumber ORDER BY pred.refnumber SEPARATOR ', ')\n" +
        "          FROM process_x_process pxp\n" +
        "          JOIN process pred ON pred.id = pxp.targetprocess_id AND pred.deleteddatetime IS NULL\n" +
        "         WHERE pxp.sourceprocess_id = p.id) AS predecessors\n" +
        "FROM ph\n" +
        "JOIN process p ON p.id = ph.id\n" +
        "LEFT JOIN people cb ON cb.ID = p.createdby_id\n" +
        "LEFT JOIN people ub ON ub.ID = p.lastupdateuser_id\n" +
        "LEFT JOIN process_step_type st ON st.id = p.step_type\n" +
        "LEFT JOIN status s ON s.id = p.status\n" +
        "LEFT JOIN process_lifecycle_status ls ON ls.id = p.lifecycle_status\n" +
        "LEFT JOIN process_type pt ON pt.id = p.type\n" +
        "LEFT JOIN process_class pc ON pc.id = p.processclass_id\n" +
        "LEFT JOIN process_automation pa ON pa.id = p.processautomation_id\n" +
        "LEFT JOIN process parent ON parent.id = p.parentid AND parent.deleteddatetime IS NULL\n" +
        "CROSS JOIN sel\n" +
        "LEFT JOIN sel_path sp ON 1=1\n" +
        "WHERE p.deleteddatetime IS NULL\n" +
        // Keep only nodes that are: ancestors of selected (their path is a prefix of selected path)
        // or selected subtree (their path contains selected_id token)
        "  AND ( (sp.path IS NOT NULL AND sp.path LIKE CONCAT(ph.path, '%'))\n" +
        "        OR ph.path = CAST(sel.selected_id AS CHAR(1000))\n" +
        "        OR ph.path LIKE CONCAT('%,', sel.selected_id, ',%')\n" +
        "        OR ph.path LIKE CONCAT(sel.selected_id, ',%')\n" +
        "      )\n" +
        "ORDER BY ph.level, p.primaryname";

    public List<Process> getAllProcesses() throws SQLException {
        List<Process> processes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SELECT_ALL)) {

            while (rs.next()) {
                processes.add(mapResultSetToProcess(rs));
            }
        }
        return processes;
    }

    /**
     * Get all processes filtered by user's segment access
     */
    public List<Process> getAllProcesses(int userId) throws SQLException {
        List<Process> allProcesses = getAllProcesses();
        if (allProcesses.isEmpty()) {
            return allProcesses;
        }
        
        // Get accessible process IDs for this user (filtered by selected segments)
        List<Integer> allIds = allProcesses.stream().map(Process::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Process", allIds);
        
        // Filter to only accessible processes
        return allProcesses.stream()
                .filter(p -> accessibleIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public List<Process> getProcessHierarchy(int processId) throws SQLException {
        List<Process> hierarchy = new ArrayList<>();
        
        //system.out.println("ProcessDAO: Getting hierarchy for process ID: " + processId);
        //system.out.println("ProcessDAO: SQL Query: " + SELECT_HIERARCHY);
        
        // Note: predecessors are derived where the current process is the source,
        // and we collect the refnumbers of target processes.
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_HIERARCHY)) {
            
            pstmt.setInt(1, processId);
            // Bind selected_id for sel CTE
            pstmt.setInt(2, processId);
            //system.out.println("ProcessDAO: Executing query with processId: " + processId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                Process process = mapResultSetToProcess(rs);
                // Add hierarchy-specific fields
                process.setLevel(rs.getInt("level"));
                process.setPath(rs.getString("path"));
                String predecessors = rs.getString("predecessors");
                process.setPredecessors(predecessors);
                process.setParentName(rs.getString("parentName"));
                process.setTypeName(rs.getString("typeName"));
                
                // Set classification name from SQL query
                try {
                    process.setClassificationName(rs.getString("classificationName"));
                } catch (SQLException e) {
                    // classificationName may not be present in all queries, that's okay
                }
                
                //system.out.println("ProcessDAO: Process " + process.getId() + " predecessors: " + predecessors);
                //system.out.println("ProcessDAO: Process " + process.getId() + " refnumber: " + process.getRefNumber());
                //system.out.println("ProcessDAO: Process " + process.getId() + " level: " + process.getLevel());
                //system.out.println("ProcessDAO: Process " + process.getId() + " parentid: " + process.getParentId());
                
                hierarchy.add(process);
            }
        }
        
        return hierarchy;
    }

    public Process getProcessById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcess(rs);
            }
        }
        return null;
    }

    public Process getProcessByPrimaryName(String primaryName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_NAME)) {

            pstmt.setString(1, primaryName);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcess(rs);
            }
        }
        return null;
    }

    public Process getProcessByRefNumber(String refNumber) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER)) {

            pstmt.setString(1, refNumber.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcess(rs);
            }
        }
        return null;
    }

    public Process getProcessByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {

            pstmt.setString(1, refNumber.trim());
            pstmt.setInt(2, excludeId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToProcess(rs);
            }
        }
        return null;
    }

    /**
     * Check if RefNumber is unique (for create operations).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     */
    public boolean isRefNumberUnique(String refNumber) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUnique("Process", refNumber);
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Process", refNumber, excludeId);
    }
    
    /**
     * Check if RefNumber is unique for update (excluding multiple IDs).
     * Useful when updating cloned objects (exclude both original and cloned IDs).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId1, int excludeId2) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Process", refNumber, excludeId1, excludeId2);
    }

    public List<Process> searchProcesses(String searchQuery) throws SQLException {
        List<Process> processes = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            String searchPattern = "%" + searchQuery + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                processes.add(mapResultSetToProcess(rs));
            }
        }
        return processes;
    }

    public List<Process> getAllProcessesForDropdown() throws SQLException {
        List<Process> processes = new ArrayList<>();
        //system.out.println("Executing query: " + SELECT_FOR_DROPDOWN);

        try (Connection conn = DatabaseConnection.getConnection()) {
            //system.out.println("Database connection established successfully");
            
            // First, let's check if there are any records in the process table
            try (Statement countStmt = conn.createStatement();
                 ResultSet countRs = countStmt.executeQuery("SELECT COUNT(*) as total FROM process")) {
                
                if (countRs.next()) {
                    countRs.getInt("total");
                }
            }
            
            // Let's also check the table structure
            try (Statement descStmt = conn.createStatement();
                 ResultSet descRs = descStmt.executeQuery("DESCRIBE process")) {
                
                //system.out.println("Process table structure:");
                while (descRs.next()) {
                    descRs.getString("Field");
                    descRs.getString("Type");
                    descRs.getString("Null");
                }
            }
            
            // Let's also check what data exists
            try (Statement sampleStmt = conn.createStatement();
                 ResultSet sampleRs = sampleStmt.executeQuery("SELECT id, primaryname, description FROM process LIMIT 5")) {
                
                while (sampleRs.next()) {
                    sampleRs.getInt("id");
                    sampleRs.getString("primaryname");
                    sampleRs.getString("description");
                }
            }
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SELECT_FOR_DROPDOWN)) {

                while (rs.next()) {
                    Process process = new Process();
                    process.setId(rs.getInt("id"));
                    process.setPrimaryName(rs.getString("primaryname"));
                    process.setDescription(rs.getString("description"));
                    processes.add(process);
                }
                
                if (processes.isEmpty()) {
                    //system.out.println("No processes found in database!");
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error in getAllProcessesForDropdown: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            throw e;
        }
        return processes;
    }

    /**
     * Get all processes for dropdown filtered by user's segment access
     */
    public List<Process> getAllProcessesForDropdown(int userId) throws SQLException {
        List<Process> allProcesses = getAllProcessesForDropdown();
        if (allProcesses.isEmpty()) {
            return allProcesses;
        }
        
        // Get accessible process IDs for this user (filtered by selected segments)
        List<Integer> allIds = allProcesses.stream().map(Process::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Process", allIds);
        
        // Filter to only accessible processes
        return allProcesses.stream()
                .filter(p -> accessibleIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public List<Process> getProcessesForParentPicker(int excludeId) throws SQLException {
        List<Process> processes = new ArrayList<>();
        //system.out.println("Executing parent picker query with excludeId: " + excludeId);
        //system.out.println("Query: " + SELECT_FOR_PARENT_PICKER);

        try (Connection conn = DatabaseConnection.getConnection()) {
            //system.out.println("Database connection established successfully for parent picker");
            
            try (PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_PARENT_PICKER)) {

                pstmt.setInt(1, excludeId);
                //system.out.println("Prepared statement set with excludeId: " + excludeId);
                
                ResultSet rs = pstmt.executeQuery();
                //system.out.println("Parent picker query executed successfully");

                int count = 0;
                while (rs.next()) {
                    Process process = new Process();
                    process.setId(rs.getInt("id"));
                    process.setPrimaryName(rs.getString("primaryname"));
                    process.setDescription(rs.getString("description"));
                    processes.add(process);
                    count++;
                    //system.out.println("Loaded parent process: " + process.getPrimaryName() + " (ID: " + process.getId() + ")");
                }
                //system.out.println("Total parent processes loaded: " + count);
                
                if (count == 0) {
                    //system.out.println("No parent processes found in database!");
                }
            }
        } catch (SQLException e) {
            System.err.println("SQL Error in getProcessesForParentPicker: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            throw e;
        }
        return processes;
    }

    /**
     * Get processes for parent picker filtered by user's segment access
     */
    public List<Process> getProcessesForParentPicker(int excludeId, int userId) throws SQLException {
        List<Process> allProcesses = getProcessesForParentPicker(excludeId);
        if (allProcesses.isEmpty()) {
            return allProcesses;
        }
        
        // Get accessible process IDs for this user (filtered by selected segments)
        List<Integer> allIds = allProcesses.stream().map(Process::getId).collect(Collectors.toList());
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Process", allIds);
        
        // Filter to only accessible processes
        return allProcesses.stream()
                .filter(p -> accessibleIds.contains(p.getId()))
                .collect(Collectors.toList());
    }

    public Process createProcess(Process process) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setObject(1, process.getParentId());
            pstmt.setObject(2, process.getIsPublic());
            pstmt.setObject(3, process.getStatus());
            pstmt.setObject(4, process.getType());
            pstmt.setObject(5, process.getDurationType());
            pstmt.setObject(6, process.getDuration());
            pstmt.setObject(7, process.getLifecycleStatus());
            pstmt.setObject(8, process.getProcessClassId());
            pstmt.setObject(9, process.getProcessAutomationId());
            pstmt.setString(10, process.getPrimaryName());
            pstmt.setString(11, process.getRefNumber());
            pstmt.setString(12, process.getDescription());
            pstmt.setString(13, process.getInputDescription());
            pstmt.setString(14, process.getOutputDescription());
            pstmt.setObject(15, process.getStepType());
            pstmt.setObject(16, process.getCanCreate());
            pstmt.setObject(17, process.getCanRead());
            pstmt.setObject(18, process.getCanUpdate());
            pstmt.setObject(19, process.getCanDelete());
            pstmt.setObject(20, process.getCanArchive());
            pstmt.setObject(21, process.getLastUpdateUserId()); // createdby_id
            pstmt.setObject(22, process.getLastUpdateUserId()); // lastupdateuser_id

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process failed, no rows affected.");
            }

            // Since we're using a subquery for ID generation, we need to get the ID differently
            // Prefer refnumber (unique) to avoid wrong ID when name+description duplicate in same batch
            String refNumber = process.getRefNumber();
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                try (PreparedStatement idStmt = conn.prepareStatement(
                        "SELECT id FROM process WHERE refnumber = ? AND deleteddatetime IS NULL ORDER BY id DESC LIMIT 1")) {
                    idStmt.setString(1, refNumber.trim());
                    try (ResultSet idRs = idStmt.executeQuery()) {
                        if (idRs.next()) {
                            process.setId(idRs.getInt(1));
                            return process;
                        }
                    }
                }
            }
            // Fallback: name + description + recent time (for callers that don't set refnumber yet)
            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT id FROM process WHERE primaryname = ? AND description = ? AND createdatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY id DESC LIMIT 1")) {
                idStmt.setString(1, process.getPrimaryName());
                idStmt.setString(2, process.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        int generatedId = idRs.getInt(1);
                        process.setId(generatedId);
                        return process;
                    } else {
                        throw new SQLException("Creating process failed, no ID obtained.");
                    }
                }
            }
        }
    }

    /**
     * Creates a process using the provided connection (for bulk upload transactional consistency).
     * Does not close the connection. Caller must manage transaction commit/rollback.
     * Retrieves the new ID by refnumber when available to avoid wrong ID when name+description duplicate in same batch.
     */
    public Process createProcess(Process process, Connection conn) throws SQLException {
        if (conn == null) {
            return createProcess(process);
        }
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setObject(1, process.getParentId());
            pstmt.setObject(2, process.getIsPublic());
            pstmt.setObject(3, process.getStatus());
            pstmt.setObject(4, process.getType());
            pstmt.setObject(5, process.getDurationType());
            pstmt.setObject(6, process.getDuration());
            pstmt.setObject(7, process.getLifecycleStatus());
            pstmt.setObject(8, process.getProcessClassId());
            pstmt.setObject(9, process.getProcessAutomationId());
            pstmt.setString(10, process.getPrimaryName());
            pstmt.setString(11, process.getRefNumber());
            pstmt.setString(12, process.getDescription());
            pstmt.setString(13, process.getInputDescription());
            pstmt.setString(14, process.getOutputDescription());
            pstmt.setObject(15, process.getStepType());
            pstmt.setObject(16, process.getCanCreate());
            pstmt.setObject(17, process.getCanRead());
            pstmt.setObject(18, process.getCanUpdate());
            pstmt.setObject(19, process.getCanDelete());
            pstmt.setObject(20, process.getCanArchive());
            pstmt.setObject(21, process.getLastUpdateUserId());
            pstmt.setObject(22, process.getLastUpdateUserId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating process failed, no rows affected.");
            }

            String refNumber = process.getRefNumber();
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                try (PreparedStatement idStmt = conn.prepareStatement(
                        "SELECT id FROM process WHERE refnumber = ? AND deleteddatetime IS NULL ORDER BY id DESC LIMIT 1")) {
                    idStmt.setString(1, refNumber.trim());
                    try (ResultSet idRs = idStmt.executeQuery()) {
                        if (idRs.next()) {
                            process.setId(idRs.getInt(1));
                            return process;
                        }
                    }
                }
            }
            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT id FROM process WHERE primaryname = ? AND description = ? AND createdatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY id DESC LIMIT 1")) {
                idStmt.setString(1, process.getPrimaryName());
                idStmt.setString(2, process.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        process.setId(idRs.getInt(1));
                        return process;
                    }
                }
            }
            throw new SQLException("Creating process failed, no ID obtained.");
        }
    }

    public boolean updateProcess(Process process) throws SQLException {
        // الخطوة 1: الحصول على القيم القديمة
        Process oldProcess = getProcessById(process.getId());
        if (oldProcess == null) {
            throw new SQLException("Process not found with ID: " + process.getId());
        }
        
        // Check if there are actual changes
        boolean hasChanges = !Objects.equals(oldProcess.getParentId(), process.getParentId()) ||
                            !Objects.equals(oldProcess.getIsPublic(), process.getIsPublic()) ||
                            !Objects.equals(oldProcess.getStatus(), process.getStatus()) ||
                            !Objects.equals(oldProcess.getType(), process.getType()) ||
                            !Objects.equals(oldProcess.getDurationType(), process.getDurationType()) ||
                            !Objects.equals(oldProcess.getDuration(), process.getDuration()) ||
                            !Objects.equals(oldProcess.getLifecycleStatus(), process.getLifecycleStatus()) ||
                            !Objects.equals(oldProcess.getProcessClassId(), process.getProcessClassId()) ||
                            !Objects.equals(oldProcess.getProcessAutomationId(), process.getProcessAutomationId()) ||
                            !Objects.equals(oldProcess.getPrimaryName(), process.getPrimaryName()) ||
                            !Objects.equals(oldProcess.getRefNumber(), process.getRefNumber()) ||
                            !Objects.equals(oldProcess.getDescription(), process.getDescription()) ||
                            !Objects.equals(oldProcess.getInputDescription(), process.getInputDescription()) ||
                            !Objects.equals(oldProcess.getOutputDescription(), process.getOutputDescription()) ||
                            !Objects.equals(oldProcess.getStepType(), process.getStepType()) ||
                            !Objects.equals(oldProcess.getCanCreate(), process.getCanCreate()) ||
                            !Objects.equals(oldProcess.getCanRead(), process.getCanRead()) ||
                            !Objects.equals(oldProcess.getCanUpdate(), process.getCanUpdate()) ||
                            !Objects.equals(oldProcess.getCanDelete(), process.getCanDelete()) ||
                            !Objects.equals(oldProcess.getCanArchive(), process.getCanArchive());
        
        // Determine LastUpdateDatetime and LastUpdateUserId based on whether there are changes
        Timestamp lastUpdateDatetime;
        Integer lastUpdateUserId;
        Timestamp now = new Timestamp(System.currentTimeMillis());
        
        if (!hasChanges) {
            // If no changes, check if object has been edited before
            Integer oldLastUpdateUserId = oldProcess.getLastUpdateUserId();
            Timestamp oldLastUpdateDatetime = oldProcess.getLastUpdateDateTime();
            
            if (oldLastUpdateUserId != null && oldLastUpdateDatetime != null) {
                // Object has been edited before - keep the previous last update values
                lastUpdateUserId = oldLastUpdateUserId;
                lastUpdateDatetime = oldLastUpdateDatetime;
            } else {
                // Object has never been edited - use CreatedBy and CreatedDatetime
                lastUpdateUserId = oldProcess.getCreatedById();
                Timestamp createDatetime = oldProcess.getCreateDateTime();
                if (createDatetime != null) {
                    lastUpdateDatetime = createDatetime;
                } else {
                    // Fallback to current time if createDatetime is null
                    lastUpdateDatetime = now;
                }
            }
        } else {
            // There are changes - use current timestamp and provided user ID
            lastUpdateDatetime = now;
            lastUpdateUserId = process.getLastUpdateUserId();
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setObject(1, process.getParentId());
            pstmt.setObject(2, process.getIsPublic());
            pstmt.setObject(3, process.getStatus());
            pstmt.setObject(4, process.getType());
            pstmt.setObject(5, process.getDurationType());
            pstmt.setObject(6, process.getDuration());
            pstmt.setObject(7, process.getLifecycleStatus());
            pstmt.setObject(8, process.getProcessClassId());
            pstmt.setObject(9, process.getProcessAutomationId());
            pstmt.setString(10, process.getPrimaryName());
            pstmt.setString(11, process.getRefNumber());
            pstmt.setString(12, process.getDescription());
            pstmt.setString(13, process.getInputDescription());
            pstmt.setString(14, process.getOutputDescription());
            pstmt.setObject(15, process.getStepType());
            pstmt.setObject(16, process.getCanCreate());
            pstmt.setObject(17, process.getCanRead());
            pstmt.setObject(18, process.getCanUpdate());
            pstmt.setObject(19, process.getCanDelete());
            pstmt.setObject(20, process.getCanArchive());
            pstmt.setTimestamp(21, lastUpdateDatetime);
            pstmt.setObject(22, lastUpdateUserId);
            pstmt.setInt(23, process.getId());

            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit records للتحديثات
                try {
                    // Use the actual lastUpdateUserId that was set (previous value if no changes, or provided user if changes)
                    String userName = "System"; // Default fallback
                    Integer userId = lastUpdateUserId;
                    if (userId != null && userId > 0) {
                        String fullName = getPersonFullName(userId);
                        if (fullName != null && !fullName.trim().isEmpty()) {
                            userName = fullName;
                        } else {
                            // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                            userName = "User ID: " + userId;
                        }
                    }
                    
                    createProcessUpdateAuditRecords(process.getId(), oldProcess, process, userName);
                    //system.out.println("✅ Process update audit records created for ID: " + process.getId() + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating process update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // الخطوة 3: إنشاء snapshot جديد في process_audit
                try {
                    createProcessUpdateAuditSnapshot(process.getId());
                    //system.out.println("✅ ProcessDAO: process_audit update snapshot created for ID: " + process.getId());
                } catch (Exception e) {
                    System.err.println("❌ Error creating process_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }

    /**
     * Updates only the parentid of a process (for bulk upload two-pass parent resolution).
     * Uses the provided connection so it participates in the same transaction.
     */
    public boolean updateProcessParentId(int processId, Integer parentId, Connection conn) throws SQLException {
        if (conn == null) {
            try (Connection c = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = c.prepareStatement("UPDATE process SET parentid = ? WHERE id = ?")) {
                pstmt.setObject(1, parentId);
                pstmt.setInt(2, processId);
                return pstmt.executeUpdate() > 0;
            }
        }
        try (PreparedStatement pstmt = conn.prepareStatement("UPDATE process SET parentid = ? WHERE id = ?")) {
            pstmt.setObject(1, parentId);
            pstmt.setInt(2, processId);
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean deleteProcess(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    private Process mapResultSetToProcess(ResultSet rs) throws SQLException {
        Process process = new Process();
        process.setId(rs.getInt("id"));
        // Use getObject for nullable integers so NULL stays null (getInt returns 0 for NULL)
        process.setParentId(rs.getObject("parentid", Integer.class));
        process.setIsPublic(rs.getObject("ispublic", Integer.class));
        process.setStatus(rs.getObject("status", Integer.class));
        try {
            process.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            process.setStatusName(null);
        }
        process.setType(rs.getObject("type", Integer.class));
        process.setDurationType(rs.getObject("duration_type", Integer.class));
        process.setDuration(rs.getObject("duration", Integer.class));
        process.setLifecycleStatus(rs.getObject("lifecycle_status", Integer.class));
        process.setProcessClassId(rs.getObject("processclass_id", Integer.class));
        process.setProcessAutomationId(rs.getObject("processautomation_id", Integer.class));
        process.setPrimaryName(rs.getString("primaryname"));
        process.setRefNumber(rs.getString("refnumber"));
        process.setDescription(rs.getString("description"));
        process.setInputDescription(rs.getString("input_description"));
        process.setOutputDescription(rs.getString("output_description"));
        process.setStepType(rs.getObject("step_type", Integer.class));
        process.setCanCreate(rs.getObject("cancreate", Integer.class));
        process.setCanRead(rs.getObject("canread", Integer.class));
        process.setCanUpdate(rs.getObject("canupdate", Integer.class));
        process.setCanDelete(rs.getObject("candelete", Integer.class));
        process.setCanArchive(rs.getObject("canarchive", Integer.class));
        process.setCreateDateTime(rs.getTimestamp("createdatetime"));
        process.setLastUpdateDateTime(rs.getTimestamp("lastupdatedatetime"));
        process.setLastUpdateUserId(rs.getObject("lastupdateuser_id", Integer.class));
        process.setDeletedDateTime(rs.getTimestamp("deleteddatetime"));
        
        // Add user ID and name fields if available (only present in SELECT_BY_ID query)
        try {
            process.setCreatedById(rs.getObject("createdby_id", Integer.class));
            process.setCreatedByName(rs.getString("createdByName"));
            process.setLastUpdatedByName(rs.getString("lastUpdatedByName"));
        } catch (SQLException e) {
            // These fields may not be present in all queries, that's okay
        }
        
        // Add segment info
        try {
            int processId = process.getId();
            Integer segmentId = getSegmentIdForProcess(processId);
            process.setSegmentId(segmentId);
            String segmentName = segmentId != null ? getSegmentName(segmentId) : "Not Specified";
            process.setSegmentName(segmentName);
        } catch (Exception e) {
            // Show "Not Specified" so errors are visible
            process.setSegmentId(null);
            process.setSegmentName("Not Specified");
            System.err.println("❌ Error getting segment for Process " + process.getId() + ": " + e.getMessage());
        }

        return process;
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int processId, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("ProcessDAO: createNewAuditRecord called - processId: " + processId + ", field: " + field + ", value: " + value);
        
        auditStmt.setInt(1, processId);        // id
        auditStmt.setString(2, updateType);    // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);         // to
        auditStmt.setString(5, userName);       // author
        
        //system.out.println("ProcessDAO: Executing audit insert for field: " + field);
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                int auditId = generatedKeys.getInt(1);
                //system.out.println("ProcessDAO: Audit record created with ID: " + auditId);
                return auditId;
            }
        }
        //system.out.println("ProcessDAO: No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records للعملية الجديدة
     * يتم استدعاء هذا method بعد إنشاء العملية بنجاح
     */
    public void createProcessAuditRecords(int processId, String userName) throws SQLException {
        //system.out.println("ProcessDAO: createProcessAuditRecords called with processId: " + processId + ", userName: " + userName);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            //system.out.println("ProcessDAO: Transaction started");
            
            // 1. الحصول على بيانات العملية
            String processDataSql = "SELECT * FROM process WHERE id = ?";
            //system.out.println("ProcessDAO: Executing SQL: " + processDataSql);
            PreparedStatement processStmt = conn.prepareStatement(processDataSql);
            processStmt.setInt(1, processId);
            ResultSet processRs = processStmt.executeQuery();
            
            if (!processRs.next()) {
                //system.out.println("ProcessDAO: Process not found with ID: " + processId);
                throw new SQLException("Process not found with ID: " + processId);
            }
            //system.out.println("ProcessDAO: Process found, starting audit record creation");

            // 2. إعداد audit statement
            String auditSql = """
                INSERT INTO process_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Process', 'Details', ?, ?, NULL, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            String primaryName = processRs.getString("primaryname");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, processId, "Added", "Primary Name", primaryName, userName);
            }
            
            // Description
            String description = processRs.getString("description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, processId, "Added", "Description", description, userName);
            }
            
            // Reference Number
            String refNumber = processRs.getString("refnumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, processId, "Added", "Reference Number", refNumber, userName);
            }
            
            // Parent Process (store Name for readability in audit_history)
            Integer parentId = processRs.getObject("parentid", Integer.class);
            if (parentId != null) {
                String parentName = getProcessName(parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Parent Process", parentName, userName);
                }
            }
            
            // Status (store Name for readability in audit_history)
            Integer statusId = processRs.getObject("status", Integer.class);
            if (statusId != null) {
                String statusName = getStatusPrimaryName(statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Status Change", "Status", statusName, userName);
                }
            }
            
            // Lifecycle Status (store Name for readability in audit_history)
            Integer lifecycleStatusId = processRs.getObject("lifecycle_status", Integer.class);
            if (lifecycleStatusId != null) {
                String lifecycleStatusName = getProcessLifecycleStatusName(lifecycleStatusId);
                if (lifecycleStatusName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Status Change", "Lifecycle Status", lifecycleStatusName, userName);
                }
            }
            
            // Process Type (store Name for readability in audit_history)
            Integer typeId = processRs.getObject("type", Integer.class);
            if (typeId != null) {
                String typeName = getProcessTypeName(typeId);
                if (typeName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Process Type", typeName, userName);
                }
            }
            
            // Is Public (store Name for readability in audit_history)
            Integer isPublic = processRs.getObject("ispublic", Integer.class);
            if (isPublic != null) {
                String isPublicName = getViewingName(isPublic);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Is Public", isPublicName, userName);
                }
            }
            
            // Process Class (store Name for readability in audit_history)
            Integer processClassId = processRs.getObject("processclass_id", Integer.class);
            if (processClassId != null) {
                String processClassName = getProcessClassName(processClassId);
                if (processClassName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Process Class", processClassName, userName);
                }
            }
            
            // Process Automation (store Name for readability in audit_history)
            Integer processAutomationId = processRs.getObject("processautomation_id", Integer.class);
            if (processAutomationId != null) {
                String processAutomationName = getProcessAutomationName(processAutomationId);
                if (processAutomationName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Process Automation", processAutomationName, userName);
                }
            }
            
            // Duration Type (store Name for readability in audit_history)
            Integer durationTypeId = processRs.getObject("duration_type", Integer.class);
            if (durationTypeId != null) {
                String durationTypeName = getDurationTypeName(durationTypeId);
                if (durationTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Duration Type", durationTypeName, userName);
                }
            }
            
            // Created By
            Integer createdById = processRs.getObject("createdby_id", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, processId, "Added", "Created By", createdByName, userName);
                }
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * إنشاء سجل في جدول process_audit بعد إنشاء العملية
     * يتم استدعاء هذا method بعد إنشاء العملية بنجاح
     */
    public void createProcessAuditRecord(int processId) throws SQLException {
        String sql = """
            INSERT INTO process_audit (
                id, parentid, ispublic, status, type, duration_type, lifecycle_status, processclass_id, processautomation_id,
                primaryname, refnumber, description, input_description, output_description, step_type, cancreate, canread, canupdate, candelete, canarchive, duration,
                createdatetime, lastupdatedatetime, deleteddatetime, createdby_id, lastupdateuser_id, revtype
            )
            SELECT 
                id, parentid, ispublic, status, type, duration_type, lifecycle_status, processclass_id, processautomation_id,
                primaryname, refnumber, description, input_description, output_description, step_type, cancreate, canread, canupdate, candelete, canarchive, duration,
                createdatetime, lastupdatedatetime, deleteddatetime, createdby_id, lastupdateuser_id, 'Added'
            FROM process 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            ps.executeUpdate();
        }
    }

    // Helper methods للحصول على الأسماء
    private String getProcessName(int processId) throws SQLException {
        String sql = "SELECT primaryname FROM process WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Overloaded method that uses existing connection
    private String getProcessName(Connection conn, int processId) throws SQLException {
        String sql = "SELECT primaryname FROM process WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getStatusPrimaryName(int statusId) throws SQLException {
        //system.out.println("ProcessDAO: getStatusPrimaryName called with statusId: " + statusId);
        
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            //system.out.println("ProcessDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("PrimaryName");
                        //system.out.println("ProcessDAO: Found PrimaryName: " + result);
                        return result;
                    } else {
                        //system.out.println("ProcessDAO: No record found for statusId: " + statusId);
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("ProcessDAO: Error with PrimaryName - " + e.getMessage());
        }
        
        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            //system.out.println("ProcessDAO: Trying SQL: " + sql);
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String result = rs.getString("primaryname");
                        //system.out.println("ProcessDAO: Found primaryname: " + result);
                        return result;
                    }
                }
            }
        } catch (SQLException e) {
            //system.out.println("ProcessDAO: Error with primaryname - " + e.getMessage());
        }
        
        //system.out.println("ProcessDAO: Using fallback for statusId: " + statusId);
        return "Status " + statusId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getStatusPrimaryName(Connection conn, int statusId) throws SQLException {
        // Try PrimaryName first (most likely)
        try {
            String sql = "SELECT PrimaryName FROM status WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("PrimaryName");
                    }
                }
            }
        } catch (SQLException e) {
            // Continue to fallback
        }
        
        // Try primaryname as fallback
        try {
            String sql = "SELECT primaryname FROM status WHERE ID = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, statusId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("primaryname");
                    }
                }
            }
        } catch (SQLException e) {
            // Continue to fallback
        }
        
        return "Status " + statusId; // Fallback
    }

    private String getProcessLifecycleStatusName(int lifecycleStatusId) throws SQLException {
        String sql = "SELECT primaryname FROM process_lifecycle_status WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Overloaded method that uses existing connection
    private String getProcessLifecycleStatusName(Connection conn, int lifecycleStatusId) throws SQLException {
        String sql = "SELECT primaryname FROM process_lifecycle_status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleStatusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getProcessTypeName(int typeId) throws SQLException {
        String sql = "SELECT primaryname FROM process_type WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Overloaded method that uses existing connection
    private String getProcessTypeName(Connection conn, int typeId) throws SQLException {
        String sql = "SELECT primaryname FROM process_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    private String getPersonFullName(int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    private String getViewingName(int viewingId) throws SQLException {
        // Try different possible column names for viewing table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "viewing_name"};
        String[] possibleTables = {"viewing", "Viewing", "viewing_primary"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, viewingId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Viewing " + viewingId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        // Try different possible column names for viewing table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "viewing_name"};
        String[] possibleTables = {"viewing", "Viewing", "viewing_primary"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, viewingId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Viewing " + viewingId; // Fallback
    }

    private String getProcessClassName(int processClassId) throws SQLException {
        // Try different possible column names for process_class table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "class_name"};
        String[] possibleTables = {"process_class", "Process_Class", "processclass"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, processClassId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Process Class " + processClassId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getProcessClassName(Connection conn, int processClassId) throws SQLException {
        // Try different possible column names for process_class table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "class_name"};
        String[] possibleTables = {"process_class", "Process_Class", "processclass"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, processClassId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Process Class " + processClassId; // Fallback
    }

    private String getProcessAutomationName(int processAutomationId) throws SQLException {
        // Try different possible column names for process_automation table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "automation_name"};
        String[] possibleTables = {"process_automation", "Process_Automation", "processautomation"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, processAutomationId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Process Automation " + processAutomationId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getProcessAutomationName(Connection conn, int processAutomationId) throws SQLException {
        // Try different possible column names for process_automation table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "automation_name"};
        String[] possibleTables = {"process_automation", "Process_Automation", "processautomation"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, processAutomationId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Process Automation " + processAutomationId; // Fallback
    }

    private String getDurationTypeName(int durationTypeId) throws SQLException {
        // Try different possible column names for process_duration_type table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "duration_type_name"};
        String[] possibleTables = {"process_duration_type", "Process_Duration_Type", "processdurationtype"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE ID = ?";
                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, durationTypeId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Duration Type " + durationTypeId; // Fallback
    }
    
    // Overloaded method that uses existing connection
    private String getDurationTypeName(Connection conn, int durationTypeId) throws SQLException {
        // Try different possible column names for process_duration_type table
        String[] possibleColumns = {"primaryname", "PrimaryName", "name", "Name", "duration_type_name"};
        String[] possibleTables = {"process_duration_type", "Process_Duration_Type", "processdurationtype"};
        
        for (String table : possibleTables) {
            for (String column : possibleColumns) {
                try {
                    String sql = "SELECT " + column + " FROM " + table + " WHERE ID = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, durationTypeId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                String result = rs.getString(column);
                                if (result != null && !result.trim().isEmpty()) {
                                    return result;
                                }
                            }
                        }
                    }
                } catch (SQLException e) {
                    // Continue to next column/table combination
                    continue;
                }
            }
        }
        return "Duration Type " + durationTypeId; // Fallback
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء العملية
     * يتم استدعاء هذا method بعد إنشاء العملية بنجاح
     */
    public void createStakeholderAuditRecords(int processId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // بداية transaction
            
            // 1. إعداد audit statement للـ stakeholder
            String auditSql = """
                INSERT INTO process_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;
            
            // الحصول على roleID الفعلي من object_x_people
            Integer actualRoleId = getStakeholderRoleId(processId);
            if (actualRoleId == null) {
                actualRoleId = roleId; // fallback to the passed roleId
            }
            
            // الحصول على اسم الدور من object_role بناءً على roleID من object_x_people
            String roleName = getRoleName(actualRoleId);
            if (roleName == null) roleName = "Process Owner"; // fallback
            
            // Role
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, processId, "Added", "Role", roleName, userName);
            }
            
            // Role Status - الحصول على statusID من object_x_people ثم اسم الـ status
            Integer statusId = getStakeholderStatusId(processId);
            String statusName = "Active"; // fallback
            if (statusId != null) {
                String fetchedStatusName = getStatusNameById(statusId);
                if (fetchedStatusName != null) statusName = fetchedStatusName;
            }
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, processId, "Added", "Role Status", statusName, userName);
            }
            
            // Name
            try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
                createNewAuditRecord(conn, auditStmt, processId, "Added", "Name", userFullName, userName);
            }
            
            conn.commit(); // تأكيد الـ transaction
            
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // إلغاء الـ transaction في حالة الخطأ
            }
            throw e;
        } finally {
            // تنظيف الموارد
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    // Helper methods للحصول على أسماء الـ stakeholders
    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }
    
    // Get actual roleID from object_x_people for the stakeholder
    private Integer getStakeholderRoleId(int processId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN process_x_objectxpeople pxo ON pxo.object_x_ip = oxp.id " +
                    "WHERE pxo.process_id = ? " +
                    "ORDER BY pxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }
    
    // Get statusID from object_x_people for the stakeholder
    private Integer getStakeholderStatusId(int processId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN process_x_objectxpeople pxo ON pxo.object_x_ip = oxp.id " +
                    "WHERE pxo.process_id = ? " +
                    "ORDER BY pxo.id DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }
    
    // Get status name from object_x_ip_status by statusID
    private String getStatusNameById(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * إنشاء audit records عند تحديث العملية
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createProcessUpdateAuditRecords(int processId, Process oldProcess, Process newProcess, String userName) throws SQLException {
        //system.out.println("🔍 ProcessDAO.createProcessUpdateAuditRecords - START for ID: " + processId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO process_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldProcess.getPrimaryName(), newProcess.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Primary Name", oldProcess.getPrimaryName(), newProcess.getPrimaryName(), userName);
            }
            
            // Ref Number
            if (!isEqual(oldProcess.getRefNumber(), newProcess.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Reference Number", oldProcess.getRefNumber(), newProcess.getRefNumber(), userName);
            }
            
            // Description
            if (!isEqual(oldProcess.getDescription(), newProcess.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Description", oldProcess.getDescription(), newProcess.getDescription(), userName);
            }
            
            // Input Description
            if (!isEqual(oldProcess.getInputDescription(), newProcess.getInputDescription())) {
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Input Description", oldProcess.getInputDescription(), newProcess.getInputDescription(), userName);
            }
            
            // Output Description
            if (!isEqual(oldProcess.getOutputDescription(), newProcess.getOutputDescription())) {
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Output Description", oldProcess.getOutputDescription(), newProcess.getOutputDescription(), userName);
            }
            
            // Parent Process (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getParentId(), newProcess.getParentId())) {
                String oldParentName = oldProcess.getParentId() != null ? getProcessName(conn, oldProcess.getParentId()) : null;
                String newParentName = newProcess.getParentId() != null ? getProcessName(conn, newProcess.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Parent Process", oldParentName, newParentName, userName);
            }
            
            // Status (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getStatus(), newProcess.getStatus())) {
                String oldStatusName = oldProcess.getStatus() != null ? getStatusPrimaryName(conn, oldProcess.getStatus()) : null;
                String newStatusName = newProcess.getStatus() != null ? getStatusPrimaryName(conn, newProcess.getStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Lifecycle Status (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getLifecycleStatus(), newProcess.getLifecycleStatus())) {
                String oldLifecycleName = oldProcess.getLifecycleStatus() != null ? getProcessLifecycleStatusName(conn, oldProcess.getLifecycleStatus()) : null;
                String newLifecycleName = newProcess.getLifecycleStatus() != null ? getProcessLifecycleStatusName(conn, newProcess.getLifecycleStatus()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Status Change", "Lifecycle Status", oldLifecycleName, newLifecycleName, userName);
            }
            
            // Process Type (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getType(), newProcess.getType())) {
                String oldTypeName = oldProcess.getType() != null ? getProcessTypeName(conn, oldProcess.getType()) : null;
                String newTypeName = newProcess.getType() != null ? getProcessTypeName(conn, newProcess.getType()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Process Type", oldTypeName, newTypeName, userName);
            }
            
            // Is Public (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getIsPublic(), newProcess.getIsPublic())) {
                String oldIsPublicName = oldProcess.getIsPublic() != null ? getViewingName(conn, oldProcess.getIsPublic()) : null;
                String newIsPublicName = newProcess.getIsPublic() != null ? getViewingName(conn, newProcess.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            // Process Class (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getProcessClassId(), newProcess.getProcessClassId())) {
                String oldClassName = oldProcess.getProcessClassId() != null ? getProcessClassName(conn, oldProcess.getProcessClassId()) : null;
                String newClassName = newProcess.getProcessClassId() != null ? getProcessClassName(conn, newProcess.getProcessClassId()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Process Class", oldClassName, newClassName, userName);
            }
            
            // Process Automation (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getProcessAutomationId(), newProcess.getProcessAutomationId())) {
                String oldAutomationName = oldProcess.getProcessAutomationId() != null ? getProcessAutomationName(conn, oldProcess.getProcessAutomationId()) : null;
                String newAutomationName = newProcess.getProcessAutomationId() != null ? getProcessAutomationName(conn, newProcess.getProcessAutomationId()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Process Automation", oldAutomationName, newAutomationName, userName);
            }
            
            // Duration Type (store Name for readability in audit_history)
            if (!isEqual(oldProcess.getDurationType(), newProcess.getDurationType())) {
                String oldDurationTypeName = oldProcess.getDurationType() != null ? getDurationTypeName(conn, oldProcess.getDurationType()) : null;
                String newDurationTypeName = newProcess.getDurationType() != null ? getDurationTypeName(conn, newProcess.getDurationType()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Duration Type", oldDurationTypeName, newDurationTypeName, userName);
            }
            
            // Duration
            if (!isEqual(oldProcess.getDuration(), newProcess.getDuration())) {
                String oldDuration = oldProcess.getDuration() != null ? String.valueOf(oldProcess.getDuration()) : null;
                String newDuration = newProcess.getDuration() != null ? String.valueOf(newProcess.getDuration()) : null;
                createUpdateAuditRecord(conn, auditStmt, processId, "Process", "Details", 
                    "Updated", "Duration", oldDuration, newDuration, userName);
            }
            
            conn.commit();
            //system.out.println("✅ ProcessDAO.createProcessUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ ProcessDAO.createProcessUpdateAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (auditStmt != null) auditStmt.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Helper method لإنشاء audit record للـ update مع from و to
     */
    private int createUpdateAuditRecord(Connection conn, PreparedStatement auditStmt, 
            int processId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, processId);
        auditStmt.setString(2, object);
        auditStmt.setString(3, event);
        auditStmt.setString(4, updateType);
        auditStmt.setString(5, field);
        auditStmt.setString(6, fromValue);      // from
        auditStmt.setString(7, toValue);        // to
        auditStmt.setString(8, userName);
        
        auditStmt.executeUpdate();
        //system.out.println("    ✓ Update audit record inserted");
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Method لإنشاء snapshot جديد في process_audit عند الـ update
     */
    public void createProcessUpdateAuditSnapshot(int processId) throws SQLException {
        //system.out.println("🔍 ProcessDAO.createProcessUpdateAuditSnapshot - Creating update snapshot for ID: " + processId);
        String sql = """
            INSERT INTO process_audit (
                id, parentid, ispublic, status, type, duration_type, duration, lifecycle_status, 
                processclass_id, processautomation_id, primaryname, refnumber, description, 
                input_description, output_description, step_type, cancreate, canread, canupdate, 
                candelete, canarchive, createdatetime, lastupdatedatetime, deleteddatetime, 
                createdby_id, lastupdateuser_id, revtype
            )
            SELECT 
                id, parentid, ispublic, status, type, duration_type, duration, lifecycle_status, 
                processclass_id, processautomation_id, primaryname, refnumber, description, 
                input_description, output_description, step_type, cancreate, canread, canupdate, 
                candelete, canarchive, createdatetime, lastupdatedatetime, deleteddatetime, 
                createdby_id, lastupdateuser_id, 'Updated'
            FROM process 
            WHERE id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method لتطبيع القيم (تحويل null و empty string و 0 إلى null)
     */
    private String normalizeValue(Object value) {
        if (value == null) return null;
        
        // التعامل مع Integer الذي قيمته 0 (يعتبر null في سياق foreign keys)
        if (value instanceof Integer && ((Integer) value) == 0) {
            return null;
        }
        
        String strValue = value.toString().trim();
        // تحويل empty string و "null" string و "0" string إلى null
        if (strValue.isEmpty() || strValue.equalsIgnoreCase("null") || strValue.equals("0")) {
            return null;
        }
        return strValue;
    }

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null و empty strings و 0)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        // تطبيع القيم أولاً
        String normalized1 = normalizeValue(obj1);
        String normalized2 = normalizeValue(obj2);
        
        // المقارنة بعد التطبيع
        if (normalized1 == null && normalized2 == null) return true;
        if (normalized1 == null || normalized2 == null) return false;
        return normalized1.equals(normalized2);
    }

    /**
     * حذف العملية مع تسجيل audit records
     */
    public boolean deleteProcessWithAudit(int id, String userName) throws SQLException {
        Connection conn = null;
        PreparedStatement deleteStmt = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            // الخطوة 1: الحذف الناعم
            String deleteSql = "UPDATE process SET DeletedDatetime = NOW() WHERE ID = ?";
            deleteStmt = conn.prepareStatement(deleteSql);
            deleteStmt.setInt(1, id);
            int affectedRows = deleteStmt.executeUpdate();
            
            if (affectedRows > 0) {
                // الخطوة 2: إنشاء audit history record
                String auditSql = """
                    INSERT INTO process_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL, ?)
                """;
                auditStmt = conn.prepareStatement(auditSql);
                auditStmt.setInt(1, id);
                auditStmt.setString(2, "Process");
                auditStmt.setString(3, "Details");
                auditStmt.setString(4, "Deleted");
                auditStmt.setString(5, "Process");
                auditStmt.setString(6, userName);
                auditStmt.executeUpdate();
                
                // الخطوة 3: إنشاء snapshot في process_audit
                String snapshotSql = """
                    INSERT INTO process_audit (
                        ID, Parent_ID, Business_Area_ID, Is_Public, Classification, Status, Lifecycle,
                        Automation, Duration_Type, Process_Class, Process_Type, RefNumber, PrimaryName,
                        Description, CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, RevType
                    )
                    SELECT 
                        ID, Parent_ID, Business_Area_ID, Is_Public, Classification, Status, Lifecycle,
                        Automation, Duration_Type, Process_Class, Process_Type, RefNumber, PrimaryName,
                        Description, CreateDatetime, LastUpdateDatetime, DeleteDatetime, LastUpdate_UserID, 'Deleted'
                    FROM process 
                    WHERE ID = ?
                """;
                try (PreparedStatement snapshotStmt = conn.prepareStatement(snapshotSql)) {
                    snapshotStmt.setInt(1, id);
                    snapshotStmt.executeUpdate();
                }
                
                //system.out.println("✅ Process deleted with audit for ID: " + id);
            }
            
            conn.commit();
            return affectedRows > 0;
            
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    System.err.println("❌ Error during rollback: " + rollbackEx.getMessage());
                }
            }
            throw e;
        } finally {
            try {
                if (deleteStmt != null) deleteStmt.close();
                if (auditStmt != null) auditStmt.close();
            } catch (SQLException e) {
                System.err.println("❌ Error closing statement: " + e.getMessage());
            }
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                System.err.println("❌ Error closing connection: " + e.getMessage());
            }
        }
    }

    /**
     * Create object_x_people record for stakeholder
     * Always creates a NEW record (no reuse)
     */
    public int createObjectXPeople(Connection conn, java.util.Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
            VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(2, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(3, currentUserId); // lastupdateuser_id

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    //system.out.println("✅ Generated object_x_people ID: " + newId);
                    return newId;
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    /**
     * Link stakeholder to process via junction table
     * Prevents duplicate links through DB constraint
     */
    public void linkStakeholderToProcess(Connection conn, int processId, int objectXPeopleId, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO process_x_objectxpeople (process_id, object_x_ip, lastupdate_userid, createdatetime)
            VALUES (?, ?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, processId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, currentUserId);
            try {
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: ProcessID=" + processId + ", Object_x_ipid=" + objectXPeopleId);
                // Link already exists, ignore
            }
        }
    }

    /**
     * Get the segment ID for a process
     * @param processId The process ID
     * @return The segment ID or null if not assigned
     */
    private Integer getSegmentIdForProcess(int processId) throws SQLException {
        String sql = """
            SELECT sxr.Segment_ID
            FROM segment_x_resource sxr
            JOIN object_reference orr ON sxr.Object_Reference_ID = orr.ID
            JOIN segment_object_type sot ON orr.Object_Type_ID = sot.ID
            WHERE orr.Object_ID = ?
            AND sot.Type = 'Process'
            AND sxr.Deleted_At IS NULL
            LIMIT 1
        """;
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, processId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("Segment_ID");
                }
            }
        }
        return null; // Return null if not assigned - show "Not Specified"
    }

    /**
     * Get segment name by ID
     * @param segmentId The segment ID
     * @return The segment name or "Not Specified" if not found
     */
    private String getSegmentName(int segmentId) throws SQLException {
        String sql = "SELECT Name FROM segment WHERE ID = ? AND Deleted_At IS NULL";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, segmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Name");
                }
            }
        }
        return "Not Specified";
    }
}
