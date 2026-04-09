package com.example.budg_v2.service;

import com.example.budg_v2.dao.ProcessDAO;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Process;
import com.example.budg_v2.util.DefaultStakeholderUtil;
import com.example.budg_v2.util.ReferenceNumberGenerator;
import com.example.budg_v2.util.UserContextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class ProcessService {

    private static final Logger logger = LoggerFactory.getLogger(ProcessService.class);
    private final ProcessDAO processDAO;
    private final DFCRService dfcrService;

    public ProcessService() {
        this.processDAO = new ProcessDAO();
        this.dfcrService = new DFCRService();
    }

    public List<Process> getAllProcesses() throws SQLException {
        return processDAO.getAllProcesses();
    }

    public List<Process> getAllProcesses(int userId) throws SQLException {
        return processDAO.getAllProcesses(userId);
    }

    public Process getProcessById(int id) throws SQLException {
        return processDAO.getProcessById(id);
    }

    public List<Process> getProcessHierarchy(int processId) throws SQLException {
        return processDAO.getProcessHierarchy(processId);
    }

    public List<Process> searchProcesses(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllProcesses();
        }
        return processDAO.searchProcesses(searchQuery.trim());
    }

    public List<Process> getAllProcessesForDropdown() throws SQLException {
        return processDAO.getAllProcessesForDropdown();
    }

    public List<Process> getAllProcessesForDropdown(int userId) throws SQLException {
        return processDAO.getAllProcessesForDropdown(userId);
    }

    public List<Process> getProcessesForParentPicker(int excludeId) throws SQLException {
        return processDAO.getProcessesForParentPicker(excludeId);
    }

    public List<Process> getProcessesForParentPicker(int excludeId, int userId) throws SQLException {
        return processDAO.getProcessesForParentPicker(excludeId, userId);
    }

    public Process createProcess(Process process, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        if (process.getPrimaryName() == null || process.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (process.getDescription() == null) process.setDescription("");
        if (process.getIsPublic() == null) process.setIsPublic(1);
        
        // Apply DF_CR defaults if workflow is enabled for Process
        boolean isAdmin = UserContextUtil.isCurrentUserAdmin(request);
        int userId = UserContextUtil.getCurrentUserId(request);
        logger.info("[DFCR ProcessService] isAdmin check result: {}", isAdmin);
        
        DFCRService.LockedFieldsInfo lockedFields = dfcrService.getLockedFieldsInfo("Process", isAdmin, userId);
        logger.info("[DFCR ProcessService] LockedFieldsInfo - workflowEnabled: {}, statusLocked: {}, lifecycleLocked: {}", 
            lockedFields.isWorkflowEnabled(), lockedFields.isStatusLocked(), lockedFields.isLifecycleLocked());
        
        if (lockedFields.isWorkflowEnabled()) {
            // Override status with default if locked
            if (lockedFields.isStatusLocked() && lockedFields.getDefaultStatusId() != null) {
                process.setStatus(lockedFields.getDefaultStatusId());
                logger.info("Applying DF_CR default status {} for Process", process.getStatus());
            }
            // Override lifecycle with default if locked
            if (lockedFields.isLifecycleLocked() && lockedFields.getDefaultLifecycleId() != null) {
                process.setLifecycleStatus(lockedFields.getDefaultLifecycleId());
                logger.info("Applying DF_CR default lifecycle {} for Process", process.getLifecycleStatus());
            }
        } else {
            logger.info("[DFCR ProcessService] Workflow NOT enabled or admin bypass active - not applying defaults");
        }
        
        // Auto-generate refnumber if empty
        if (process.getRefNumber() == null || process.getRefNumber().trim().isEmpty()) {
            try {
                process.setRefNumber(ReferenceNumberGenerator.generateProcessRefNumber());
            } catch (SQLException e) {
                logger.warn("Failed to auto-generate process ref number, using null: {}", e.getMessage());
                // Continue with null ref number
            }
        } else {
            // Validate refnumber uniqueness if provided
            if (!processDAO.isRefNumberUnique(process.getRefNumber())) {
                throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
            }
        }
        
        // Create the process in the main table
        Process createdProcess = processDAO.createProcess(process);
        
        // Create audit records for history tracking
        try {
            // Get current user name from request attributes
            String userName = getCurrentUserName(request);
            
            // Create detailed audit history records (process_audit_history table)
            processDAO.createProcessAuditRecords(createdProcess.getId(), userName);
            
            // Create snapshot in process_audit table with revtype='Added'
            processDAO.createProcessAuditRecord(createdProcess.getId());
            
            // Create default stakeholder records if lastUpdateUserId is provided
            if (process.getLastUpdateUserId() != null) {
                try {
                    String userFullName = getPersonFullName(process.getLastUpdateUserId());
                    if (userFullName != null) {
                        // Create stakeholders for all default roles the creator should receive
                        createDefaultStakeholder(createdProcess.getId(), process.getLastUpdateUserId(), request);
                        //system.out.println("✅ Default stakeholder records created for process ID: " + createdProcess.getId());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error creating default stakeholder records: " + e.getMessage());
                    // Continue - don't fail the main operation
                }
            }
        } catch (SQLException e) {
            // Log the error but don't fail the main operation
            System.err.println("Failed to create audit records: " + e.getMessage());
        }
        
        // Auto-create change request if DF_CR workflow is enabled
        try {
            // Use the current user ID from request (already defined above)
            Integer typeId = process.getType();
            Integer changeRequestId = dfcrService.applyDefaultsOnCreate("Process", createdProcess.getId(), typeId, userId, isAdmin);
            if (changeRequestId != null) {
                logger.info("Auto-created change request {} for new Process {} (typeId: {})", changeRequestId, createdProcess.getId(), typeId);
                createdProcess.setAutoGeneratedCRId(changeRequestId);
            }
        } catch (Exception e) {
            logger.error("Error auto-creating change request for Process {}: {}", createdProcess.getId(), e.getMessage());
            // Don't fail the main operation if CR creation fails
        }
        
        return createdProcess;
    }

    /**
     * إنشاء audit records للـ stakeholder بعد إنشاء العملية
     * يتم استدعاء هذا method بعد إنشاء العملية بنجاح
     */
    public void createStakeholderAuditRecords(int processId, String userName, String userFullName, int roleId) throws SQLException {
        try {
            processDAO.createStakeholderAuditRecords(processId, userName, userFullName, roleId);
        } catch (SQLException e) {
            // Log the error but don't fail the main operation
            System.err.println("Failed to create stakeholder audit records: " + e.getMessage());
            throw e;
        }
    }

    public Process createProcess(String primaryName, String description, Integer parentId, Integer status, 
                                 Integer type, Integer durationType, Integer duration, Integer lifecycleStatus, 
                                 Integer processClassId, Integer processAutomationId, String refNumber,
                                 String inputDescription, String outputDescription, Integer stepType,
                                 Integer isPublic, Integer canCreate, Integer canRead, Integer canUpdate, 
                                 Integer canDelete, Integer canArchive, Integer lastUpdateUserId, HttpServletRequest request) throws SQLException, IllegalArgumentException {
        Process process = new Process();
        process.setPrimaryName(primaryName);
        process.setDescription(description != null ? description : "");
        process.setParentId(parentId);
        process.setStatus(status);
        process.setType(type);
        process.setDurationType(durationType);
        process.setDuration(duration);
        process.setLifecycleStatus(lifecycleStatus);
        process.setProcessClassId(processClassId);
        process.setProcessAutomationId(processAutomationId);
        process.setRefNumber(refNumber);
        process.setInputDescription(inputDescription);
        process.setOutputDescription(outputDescription);
        process.setStepType(stepType);
        process.setIsPublic(isPublic);
        process.setCanCreate(canCreate);
        process.setCanRead(canRead);
        process.setCanUpdate(canUpdate);
        process.setCanDelete(canDelete);
        process.setCanArchive(canArchive);
        process.setLastUpdateUserId(lastUpdateUserId);
        return createProcess(process, request);
    }

    public boolean updateProcess(Process process) throws SQLException, IllegalArgumentException {
        return updateProcess(process, null);
    }
    
    public boolean updateProcess(Process process, Integer originalProcessId) throws SQLException, IllegalArgumentException {
        if (process.getId() == null) throw new IllegalArgumentException("Process ID is required for update");
        if (process.getPrimaryName() == null || process.getPrimaryName().trim().isEmpty()) {
            throw new IllegalArgumentException("Primary name is required");
        }
        if (process.getDescription() == null) process.setDescription("");
        if (process.getIsPublic() == null) process.setIsPublic(1);
        
        // Validate refnumber uniqueness for update
        // Only validate if the ref number has actually changed
        if (process.getRefNumber() != null && !process.getRefNumber().trim().isEmpty()) {
            // Get the current process to check if ref number has changed
            Process currentProcess = processDAO.getProcessById(process.getId());
            if (currentProcess != null) {
                String currentRefNumber = currentProcess.getRefNumber();
                String newRefNumber = process.getRefNumber().trim();
                
                // Only validate if the ref number has actually changed
                if (currentRefNumber == null || !currentRefNumber.trim().equalsIgnoreCase(newRefNumber)) {
                    boolean isUnique;
                    if (originalProcessId != null && !originalProcessId.equals(process.getId())) {
                        // Updating a cloned process - exclude both cloned ID and original ID
                        isUnique = processDAO.isRefNumberUniqueForUpdate(process.getRefNumber(), process.getId(), originalProcessId);
                    } else {
                        // Normal update - exclude only the current process ID
                        isUnique = processDAO.isRefNumberUniqueForUpdate(process.getRefNumber(), process.getId());
                    }
                    if (!isUnique) {
                        throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
                    }
                }
                // If ref number hasn't changed, skip validation (allow update of other fields like parent_id)
            } else {
                // If we can't get current process, validate anyway (shouldn't happen, but be safe)
                boolean isUnique;
                if (originalProcessId != null && !originalProcessId.equals(process.getId())) {
                    isUnique = processDAO.isRefNumberUniqueForUpdate(process.getRefNumber(), process.getId(), originalProcessId);
                } else {
                    isUnique = processDAO.isRefNumberUniqueForUpdate(process.getRefNumber(), process.getId());
                }
                if (!isUnique) {
                    throw new IllegalArgumentException("This reference number is already in use. Please enter a unique reference number.");
                }
            }
        }
        
        // Update the process in the main table
        // The audit snapshot with revtype='Updated' is created inside processDAO.updateProcess()
        boolean success = processDAO.updateProcess(process);
        
        return success;
    }

    public boolean updateProcess(int id, String primaryName, String description, Integer parentId, 
                                Integer status, Integer type, Integer durationType, Integer duration, Integer lifecycleStatus,
                                Integer processClassId, Integer processAutomationId, String refNumber,
                                String inputDescription, String outputDescription, Integer stepType,
                                Integer isPublic, Integer canCreate, Integer canRead, Integer canUpdate, 
                                Integer canDelete, Integer canArchive, Integer lastUpdateUserId) throws SQLException, IllegalArgumentException {
        Process process = new Process();
        process.setId(id);
        process.setPrimaryName(primaryName);
        process.setDescription(description != null ? description : "");
        process.setParentId(parentId);
        process.setStatus(status);
        process.setType(type);
        process.setDurationType(durationType);
        process.setDuration(duration);
        process.setLifecycleStatus(lifecycleStatus);
        process.setProcessClassId(processClassId);
        process.setProcessAutomationId(processAutomationId);
        process.setRefNumber(refNumber);
        process.setInputDescription(inputDescription);
        process.setOutputDescription(outputDescription);
        process.setStepType(stepType);
        process.setIsPublic(isPublic);
        process.setCanCreate(canCreate);
        process.setCanRead(canRead);
        process.setCanUpdate(canUpdate);
        process.setCanDelete(canDelete);
        process.setCanArchive(canArchive);
        process.setLastUpdateUserId(lastUpdateUserId);
        return updateProcess(process);
    }

    public boolean deleteProcess(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return processDAO.deleteProcessWithAudit(id, userName);
    }

    // Helper method to get current user name from request
    private String getCurrentUserName(HttpServletRequest request) {
        Object userNameObj = request.getAttribute("userName");
        if (userNameObj != null) {
            return userNameObj.toString();
        }
        return "System"; // Fallback if no user name found
    }

    // Helper method to get person full name
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

    // Create default stakeholder using ProcessStakeholderServlet logic
    // Creates stakeholders for all default roles the creator should receive based on role assignments
    private void createDefaultStakeholder(int processId, int userId, HttpServletRequest request) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Get module ID for Process
                int moduleId = DefaultStakeholderUtil.getModuleId(conn, "Process");
                
                // Get all default roles the creator should receive
                List<Integer> rolesToAssign = DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);
                
                if (rolesToAssign.isEmpty()) {
                    // No default roles to assign
                    conn.commit();
                    return;
                }
                
                String userName = getCurrentUserName(request);
                String userFullName = getPersonFullName(userId);
                
                // Create stakeholders for each role
                for (Integer roleId : rolesToAssign) {
                    try {
                        // Insert into object_x_people (same logic as ProcessStakeholderServlet)
                        String insertOXP = """
                            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdateuser_id)
                            VALUES (NULL, ?, ?, 2, 1, ?)
                            """;

                        int objectXPeopleId;
                        try (PreparedStatement stmt = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setInt(1, userId); // ipid
                            stmt.setInt(2, roleId); // RoleID
                            stmt.setInt(3, userId); // lastupdateuser_id

                            int rowsAffected = stmt.executeUpdate();
                            if (rowsAffected == 0) {
                                throw new SQLException("Failed to insert object_x_people");
                            }

                            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                                if (generatedKeys.next()) {
                                    objectXPeopleId = generatedKeys.getInt(1);
                                } else {
                                    throw new SQLException("Failed to get generated key for object_x_people");
                                }
                            }
                        }

                        // Insert into process_x_objectxpeople (same logic as ProcessStakeholderServlet)
                        String insertPXOP = """
                            INSERT INTO process_x_objectxpeople (object_x_ip, process_id, lastupdate_userid)
                            VALUES (?, ?, ?)
                            """;

                        try (PreparedStatement stmt = conn.prepareStatement(insertPXOP)) {
                            stmt.setInt(1, objectXPeopleId);
                            stmt.setInt(2, processId);
                            stmt.setInt(3, userId);

                            int rowsAffected = stmt.executeUpdate();
                            
                            if (rowsAffected == 0) {
                                throw new SQLException("Failed to insert process_x_objectxpeople");
                            }
                        }
                        
                        // Create stakeholder audit records
                        if (userFullName != null) {
                            processDAO.createStakeholderAuditRecords(processId, userName, userFullName, roleId);
                        }
                    } catch (SQLException e) {
                        // Log error but continue with other roles
                        System.err.println("❌ Error creating default stakeholder for role " + roleId + ": " + e.getMessage());
                        // Continue processing other roles
                    }
                }

                conn.commit();
                //system.out.println("✅ Default stakeholders created successfully for process ID: " + processId);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // If module not found or other critical error, log but don't fail object creation
            System.err.println("❌ Error in createDefaultStakeholder: " + e.getMessage());
            // Don't throw - allow object creation to continue
        }
    }
}
