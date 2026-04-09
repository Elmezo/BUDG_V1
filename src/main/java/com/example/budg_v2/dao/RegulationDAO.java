package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Regulation;
import com.example.budg_v2.service.SegmentAccessService;
import com.example.budg_v2.util.DefaultStakeholderUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.sql.SQLIntegrityConstraintViolationException;

public class RegulationDAO {

    // SQL Queries
    private static final String SELECT_ALL = "SELECT * FROM regulation WHERE DeletedDatetime IS NULL ORDER BY primaryName";
    private static final String SELECT_BY_ID = "SELECT r.*, rs.PrimaryName AS statusName FROM regulation r " +
            "LEFT JOIN regulation_status rs ON rs.ID = r.RegulationStatus_ID " +
            "WHERE r.ID = ? AND r.DeletedDatetime IS NULL";
    private static final String SELECT_FOR_DROPDOWN = "SELECT ID, primaryName, Description FROM regulation WHERE DeletedDatetime IS NULL ORDER BY primaryName";
    private static final String SELECT_FOR_PARENT_PICKER = "SELECT ID, primaryName, Description FROM regulation WHERE ID != ? AND DeletedDatetime IS NULL ORDER BY primaryName";
    
    private static final String INSERT = "INSERT INTO regulation (ID, Parent_ID, Is_Public, RefNumber, ShortName, Rank, primaryName, Description, AdditionalInfo, PublicationDate, CommentsDate, FinalisationDate, ComplianceDate, LegalAdvice, CreateDatetime, LastUpdateDatetime, RegulationMaturity_ID, RegulationProbability_ID, RegulationStatus_ID, RegulationImpactRating_ID, LegalAdviceType_ID, RegulationStage_ID, ComplianceLevel_ID, LastUpdate_UserID) VALUES ((SELECT COALESCE(MAX(ID), 0) + 1 FROM regulation r), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, @now := NOW(), @now, ?, ?, ?, ?, ?, ?, ?, ?)";
    
    private static final String UPDATE = "UPDATE regulation SET Parent_ID = ?, Is_Public = ?, RefNumber = ?, ShortName = ?, Rank = ?, primaryName = ?, Description = ?, AdditionalInfo = ?, PublicationDate = ?, CommentsDate = ?, FinalisationDate = ?, ComplianceDate = ?, LegalAdvice = ?, LastUpdateDatetime = NOW(), RegulationMaturity_ID = ?, RegulationProbability_ID = ?, RegulationStatus_ID = ?, LegalAdviceType_ID = ?, RegulationStage_ID = ?, ComplianceLevel_ID = ?, LastUpdate_UserID = ? WHERE ID = ?";
    
    private static final String SOFT_DELETE = "UPDATE regulation SET DeletedDatetime = NOW() WHERE ID = ?";
    private static final String SEARCH = "SELECT * FROM regulation WHERE (primaryName LIKE ? OR Description LIKE ? OR RefNumber LIKE ?) AND DeletedDatetime IS NULL ORDER BY primaryName";
    private static final String GET_NEXT_ID_SQL = "SELECT COALESCE(MAX(ID), 0) + 1 FROM regulation";
    private static final String SELECT_BY_REFNUMBER_EXCLUDE_ID = "SELECT * FROM regulation WHERE LOWER(RefNumber) = LOWER(?) AND ID != ? AND DeletedDatetime IS NULL";
    private static final String SELECT_BY_PRIMARYNAME = "SELECT * FROM regulation WHERE LOWER(primaryName) = LOWER(?) AND DeletedDatetime IS NULL LIMIT 1";

    // Reference table queries
    private static final String SELECT_VIEWING = "SELECT id, Name as primaryname, Description FROM viewing ORDER BY Name";
    private static final String SELECT_LEGAL_ADVICE_TYPE = "SELECT ID, PrimaryName, Description FROM legal_advice_type ORDER BY PrimaryName";
    private static final String SELECT_REGULATION_MATURITY = "SELECT ID, PrimaryName, Description FROM regulation_maturity ORDER BY PrimaryName";
    private static final String SELECT_REGULATION_PROBABILITY = "SELECT ID, PrimaryName, Description FROM regulation_probability ORDER BY PrimaryName";
    private static final String SELECT_REGULATION_STATUS = "SELECT ID, PrimaryName, Description FROM regulation_status ORDER BY PrimaryName";
    private static final String SELECT_REGULATION_IMPACT_RATING = "SELECT ID, PrimaryName, Description FROM regulation_impact_rating ORDER BY PrimaryName";
    private static final String SELECT_REGULATION_STAGE = "SELECT ID, PrimaryName, Description FROM regulation_stage ORDER BY PrimaryName";
    private static final String SELECT_REGULATION_COMPLIANCE_LEVEL = "SELECT ID, PrimaryName, Description FROM regulation_compliance_level ORDER BY PrimaryName";

    public List<Regulation> getAllRegulations() throws SQLException {
        return getAllRegulationsForGuest();
    }

    /**
     * Get all regulations for guest users (public, Enterprise only, not deleted)
     */
    private List<Regulation> getAllRegulationsForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("Regulation", "r", "r.ID");
        if (guestFilter == null) {
            return getAllRegulationsUnfiltered();
        }
        String sql = "SELECT r.* FROM regulation r WHERE " + guestFilter + " ORDER BY r.primaryName";
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                regulations.add(mapResultSetToRegulation(rs));
            }
        }
        return regulations;
    }

    private List<Regulation> getAllRegulationsUnfiltered() throws SQLException {
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_ALL);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                regulations.add(mapResultSetToRegulation(rs));
            }
        }
        return regulations;
    }

    public Regulation getRegulationById(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_ID)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulation(rs);
                }
            }
        }
        return null;
    }

    public List<Regulation> getRegulationsForDropdown() throws SQLException {
        return getRegulationsForDropdownForGuest();
    }

    /**
     * Get regulations for dropdown for guest users (public, Enterprise only, not deleted)
     */
    private List<Regulation> getRegulationsForDropdownForGuest() throws SQLException {
        String guestFilter = com.example.budg_v2.service.SegmentAccessService.buildGuestFilterClause("Regulation", "r", "r.ID");
        if (guestFilter == null) {
            return getRegulationsForDropdownUnfiltered();
        }
        String sql = "SELECT r.ID, r.primaryName, r.Description FROM regulation r WHERE " + guestFilter + " ORDER BY r.primaryName";
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Regulation regulation = new Regulation();
                regulation.setId(rs.getInt("ID"));
                regulation.setPrimaryName(rs.getString("primaryName"));
                regulation.setDescription(rs.getString("Description"));
                regulations.add(regulation);
            }
        }
        return regulations;
    }

    private List<Regulation> getRegulationsForDropdownUnfiltered() throws SQLException {
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_DROPDOWN);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Regulation regulation = new Regulation();
                regulation.setId(rs.getInt("ID"));
                regulation.setPrimaryName(rs.getString("primaryName"));
                regulation.setDescription(rs.getString("Description"));
                regulations.add(regulation);
            }
        }
        return regulations;
    }
    
    /**
     * Get all regulations filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Users can only see objects from segments they have access to.
     */
    public List<Regulation> getAllRegulationsBySegmentAccess(int userId) throws SQLException {
        if (userId <= 0) {
            return getAllRegulationsForGuest();
        }
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Regulation", "r.ID");
        
        String sql = "SELECT * FROM regulation r WHERE r.DeletedDatetime IS NULL AND " + 
                     segmentFilter + " ORDER BY r.primaryName";
        
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                regulations.add(mapResultSetToRegulation(rs));
            }
        }
        //system.out.println("📋 Found " + regulations.size() + " accessible regulations for user " + userId);
        return regulations;
    }
    
    /**
     * Get regulations for dropdown filtered by user's segment access.
     * Per BUDG Segmentation v7.0-7.2: Parent selection only shows accessible objects.
     */
    public List<Regulation> getRegulationsForDropdownBySegmentAccess(int userId) throws SQLException {
        if (userId <= 0) {
            return getRegulationsForDropdownForGuest();
        }
        String segmentFilter = com.example.budg_v2.service.SegmentAccessService
                .buildSelectedSegmentFilterClause(userId, "Regulation", "r.ID");
        
        String sql = "SELECT r.ID, r.primaryName, r.Description, r.Parent_ID " +
                     "FROM regulation r WHERE r.DeletedDatetime IS NULL AND " + 
                     segmentFilter + " ORDER BY r.primaryName";
        
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Regulation regulation = new Regulation();
                regulation.setId(rs.getInt("ID"));
                regulation.setPrimaryName(rs.getString("primaryName"));
                regulation.setDescription(rs.getString("Description"));
                regulation.setParentId(rs.getObject("Parent_ID") != null ? rs.getInt("Parent_ID") : null);
                regulations.add(regulation);
            }
        }
        //system.out.println("📋 Found " + regulations.size() + " accessible regulations for dropdown for user " + userId);
        return regulations;
    }

    public List<Regulation> getRegulationsForParentPicker(int excludeId) throws SQLException {
        List<Regulation> regulations = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_FOR_PARENT_PICKER)) {

            pstmt.setInt(1, excludeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Regulation regulation = new Regulation();
                    regulation.setId(rs.getInt("ID"));
                    regulation.setPrimaryName(rs.getString("primaryName"));
                    regulation.setDescription(rs.getString("Description"));
                    regulations.add(regulation);
                }
            }
        }
        return regulations;
    }

    public Regulation createRegulation(Regulation regulation, int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setObject(1, regulation.getParentId());
            pstmt.setObject(2, regulation.getIsPublic());
            pstmt.setString(3, regulation.getRefNumber());
            pstmt.setString(4, regulation.getShortName());
            pstmt.setObject(5, regulation.getRank());
            pstmt.setString(6, regulation.getPrimaryName());
            pstmt.setString(7, regulation.getDescription());
            pstmt.setString(8, regulation.getAdditionalInfo());
            // Convert string dates (YYYY-MM-DD) to SQL Date; allow nulls
            pstmt.setDate(9, toSqlDate(regulation.getPublicationDate()));
            pstmt.setDate(10, toSqlDate(regulation.getCommentsDate()));
            pstmt.setDate(11, toSqlDate(regulation.getFinalisationDate()));
            pstmt.setDate(12, toSqlDate(regulation.getComplianceDate()));
            pstmt.setString(13, regulation.getLegalAdvice());
            pstmt.setObject(14, regulation.getRegulationMaturityId());
            pstmt.setObject(15, regulation.getRegulationProbabilityId());
            pstmt.setObject(16, regulation.getRegulationStatusId());
            pstmt.setObject(17, regulation.getRegulationImpactRatingId());
            pstmt.setObject(18, regulation.getLegalAdviceTypeId());
            pstmt.setObject(19, regulation.getRegulationStageId());
            pstmt.setObject(20, regulation.getComplianceLevelId());
            pstmt.setObject(21, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating regulation failed, no rows affected.");
            }

            // Since we're using a subquery for ID generation, we need to get the ID differently
            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT ID FROM regulation WHERE primaryName = ? AND Description = ? AND CreateDatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY ID DESC LIMIT 1")) {
                idStmt.setString(1, regulation.getPrimaryName());
                idStmt.setString(2, regulation.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        int generatedId = idRs.getInt(1);
                        regulation.setId(generatedId);
                        
                        // Get user name once for all audit operations
                        String userName = getPersonFullName(userId);
                        
                        // Create audit records
                        try {
                            if (userName != null) {
                                createRegulationAuditRecords(conn, generatedId, userName);
                                //system.out.println("✅ Regulation audit records created for ID: " + generatedId);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error creating regulation audit records: " + e.getMessage());
                            e.printStackTrace();
                            // Don't fail the main operation if audit fails
                        }

                        // Create regulation audit snapshot
                        try {
                            createRegulationAuditRecord(conn, generatedId);
                            //system.out.println("✅ RegulationDAO: regulation_audit snapshot created for ID: " + generatedId);
                        } catch (Exception e) {
                            System.err.println("❌ Error creating regulation_audit snapshot: " + e.getMessage());
                            e.printStackTrace();
                        }
                        
                        // Create default stakeholder if userId is provided
                        if (userId > 0) {
                            try {
                                createDefaultStakeholder(conn, generatedId, userId);
                                //system.out.println("✅ Default stakeholder created for regulation ID: " + generatedId);
                                
                                // Create stakeholder audit records
                                if (userName != null) {
                                    int defaultRoleId = getDefaultRoleId(conn);
                                    createStakeholderAuditRecords(conn, generatedId, userName, userName, defaultRoleId);
                                    //system.out.println("✅ Default stakeholder audit records created for regulation ID: " + generatedId);
                                }
                            } catch (Exception e) {
                                System.err.println("❌ Error creating default stakeholder: " + e.getMessage());
                                e.printStackTrace();
                                // Don't fail the main operation if stakeholder creation fails
                            }
                        }
                        
                        return regulation;
                    } else {
                        throw new SQLException("Creating regulation failed, no ID obtained.");
                    }
                }
            }
        }
    }

    /**
     * Create a regulation using the provided connection (e.g. for bulk upload so that
     * rollback on the same connection undoes the insert when a row fails).
     * Caller is responsible for the connection lifecycle.
     */
    public Regulation createRegulation(Connection conn, Regulation regulation, int userId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setObject(1, regulation.getParentId());
            pstmt.setObject(2, regulation.getIsPublic());
            pstmt.setString(3, regulation.getRefNumber());
            pstmt.setString(4, regulation.getShortName());
            pstmt.setObject(5, regulation.getRank());
            pstmt.setString(6, regulation.getPrimaryName());
            pstmt.setString(7, regulation.getDescription());
            pstmt.setString(8, regulation.getAdditionalInfo());
            pstmt.setDate(9, toSqlDate(regulation.getPublicationDate()));
            pstmt.setDate(10, toSqlDate(regulation.getCommentsDate()));
            pstmt.setDate(11, toSqlDate(regulation.getFinalisationDate()));
            pstmt.setDate(12, toSqlDate(regulation.getComplianceDate()));
            pstmt.setString(13, regulation.getLegalAdvice());
            pstmt.setObject(14, regulation.getRegulationMaturityId());
            pstmt.setObject(15, regulation.getRegulationProbabilityId());
            pstmt.setObject(16, regulation.getRegulationStatusId());
            pstmt.setObject(17, regulation.getRegulationImpactRatingId());
            pstmt.setObject(18, regulation.getLegalAdviceTypeId());
            pstmt.setObject(19, regulation.getRegulationStageId());
            pstmt.setObject(20, regulation.getComplianceLevelId());
            pstmt.setObject(21, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating regulation failed, no rows affected.");
            }

            try (PreparedStatement idStmt = conn.prepareStatement(
                    "SELECT ID FROM regulation WHERE primaryName = ? AND Description = ? AND CreateDatetime >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) ORDER BY ID DESC LIMIT 1")) {
                idStmt.setString(1, regulation.getPrimaryName());
                idStmt.setString(2, regulation.getDescription());
                try (ResultSet idRs = idStmt.executeQuery()) {
                    if (idRs.next()) {
                        int generatedId = idRs.getInt(1);
                        regulation.setId(generatedId);

                        String userName = getPersonFullName(userId);

                        try {
                            if (userName != null) {
                                createRegulationAuditRecords(conn, generatedId, userName);
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Error creating regulation audit records: " + e.getMessage());
                            e.printStackTrace();
                        }

                        try {
                            createRegulationAuditRecord(conn, generatedId);
                        } catch (Exception e) {
                            System.err.println("❌ Error creating regulation_audit snapshot: " + e.getMessage());
                            e.printStackTrace();
                        }

                        if (userId > 0) {
                            try {
                                createDefaultStakeholder(conn, generatedId, userId);
                                if (userName != null) {
                                    int defaultRoleId = getDefaultRoleId(conn);
                                    createStakeholderAuditRecords(conn, generatedId, userName, userName, defaultRoleId);
                                }
                            } catch (Exception e) {
                                System.err.println("❌ Error creating default stakeholder: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }

                        return regulation;
                    } else {
                        throw new SQLException("Creating regulation failed, no ID obtained.");
                    }
                }
            }
        }
    }

    public boolean updateRegulation(int id, Regulation regulation, int userId) throws SQLException {
        // Step 1: Get old data before update
        Regulation oldRegulation = getRegulationById(id);
        if (oldRegulation == null) {
            throw new SQLException("Regulation not found with ID: " + id);
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE)) {

            pstmt.setObject(1, regulation.getParentId());
            pstmt.setObject(2, regulation.getIsPublic());
            pstmt.setString(3, regulation.getRefNumber());
            pstmt.setString(4, regulation.getShortName());
            pstmt.setObject(5, regulation.getRank());
            pstmt.setString(6, regulation.getPrimaryName());
            pstmt.setString(7, regulation.getDescription());
            pstmt.setString(8, regulation.getAdditionalInfo());
            pstmt.setDate(9, toSqlDate(regulation.getPublicationDate()));
            pstmt.setDate(10, toSqlDate(regulation.getCommentsDate()));
            pstmt.setDate(11, toSqlDate(regulation.getFinalisationDate()));
            pstmt.setDate(12, toSqlDate(regulation.getComplianceDate()));
            pstmt.setString(13, regulation.getLegalAdvice());
            pstmt.setObject(14, regulation.getRegulationMaturityId());
            pstmt.setObject(15, regulation.getRegulationProbabilityId());
            pstmt.setObject(16, regulation.getRegulationStatusId());
            pstmt.setObject(17, regulation.getLegalAdviceTypeId());
            pstmt.setObject(18, regulation.getRegulationStageId());
            pstmt.setObject(19, regulation.getComplianceLevelId());
            pstmt.setObject(20, userId);
            pstmt.setInt(21, id);

            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                // Step 2: Create update audit records
                try {
                    String userName = "System"; // Default fallback
                    if (userId > 0) {
                        String fullName = getPersonFullName(userId);
                        if (fullName != null && !fullName.trim().isEmpty()) {
                            userName = fullName;
                        } else {
                            // إذا لم نجد الاسم، استخدم User ID كبديل أفضل من "System"
                            userName = "User ID: " + userId;
                        }
                    }
                    
                    createRegulationUpdateAuditRecords(id, oldRegulation, regulation, userName);
                    //system.out.println("✅ Regulation update audit records created for ID: " + id + " with author: " + userName);
                } catch (Exception e) {
                    System.err.println("❌ Error creating regulation update audit records: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the update if audit fails
                }

                // Step 3: Create snapshot in regulation_audit
                try {
                    createRegulationUpdateAuditSnapshot(id);
                    //system.out.println("✅ RegulationDAO: regulation_audit update snapshot created for ID: " + id);
                } catch (Exception e) {
                    System.err.println("❌ Error creating regulation_audit update snapshot: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            return affectedRows > 0;
        }
    }

    public boolean deleteRegulation(int id) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SOFT_DELETE)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }

    public List<Regulation> searchRegulations(String searchTerm) throws SQLException {
        List<Regulation> regulations = new ArrayList<>();
        String searchPattern = "%" + searchTerm + "%";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SEARCH)) {

            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            pstmt.setString(3, searchPattern);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    regulations.add(mapResultSetToRegulation(rs));
                }
            }
        }
        return regulations;
    }

    public List<Regulation> searchRegulationsBySegmentAccess(String searchTerm, int userId) throws SQLException {
        List<Regulation> regulations = searchRegulations(searchTerm);
        if (regulations.isEmpty()) {
            return regulations;
        }

        List<Integer> allIds = regulations.stream().map(Regulation::getId).toList();
        Set<Integer> accessibleIds = SegmentAccessService.filterBySelectedSegments(userId, "Regulation", allIds);

        return regulations.stream()
                .filter(regulation -> accessibleIds.contains(regulation.getId()))
                .toList();
    }

    public int getNextId() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(GET_NEXT_ID_SQL);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 1; // Default starting ID
    }

    // Reference data methods
    public List<Map<String, Object>> getViewingOptions() throws SQLException {
        return getDropdownData(SELECT_VIEWING);
    }

    public List<Map<String, Object>> getLegalAdviceTypes() throws SQLException {
        return getDropdownData(SELECT_LEGAL_ADVICE_TYPE);
    }

    public List<Map<String, Object>> getRegulationMaturityOptions() throws SQLException {
        return getDropdownData(SELECT_REGULATION_MATURITY);
    }

    public List<Map<String, Object>> getRegulationProbabilityOptions() throws SQLException {
        return getDropdownData(SELECT_REGULATION_PROBABILITY);
    }

    public List<Map<String, Object>> getRegulationStatusOptions() throws SQLException {
        return getDropdownData(SELECT_REGULATION_STATUS);
    }

    public List<Map<String, Object>> getRegulationImpactRatingOptions() throws SQLException {
        return getDropdownData(SELECT_REGULATION_IMPACT_RATING);
    }

    public List<Map<String, Object>> getRegulationStageOptions() throws SQLException {
        return getDropdownData(SELECT_REGULATION_STAGE);
    }

    public List<Map<String, Object>> getRegulationComplianceLevelOptions() throws SQLException {
        return getDropdownData(SELECT_REGULATION_COMPLIANCE_LEVEL);
    }

    private List<Map<String, Object>> getDropdownData(String query) throws SQLException {
        List<Map<String, Object>> options = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> option = new HashMap<>();
                option.put("id", rs.getInt("ID"));
                option.put("primaryname", rs.getString("primaryname"));
                option.put("description", rs.getString("Description"));
                options.add(option);
            }
        }
        return options;
    }

    private Regulation mapResultSetToRegulation(ResultSet rs) throws SQLException {
        Regulation regulation = new Regulation();
        regulation.setId(rs.getInt("ID"));
        regulation.setParentId(rs.getObject("Parent_ID", Integer.class));
        regulation.setIsPublic(rs.getObject("Is_Public", Integer.class));
        regulation.setRefNumber(rs.getString("RefNumber"));
        regulation.setShortName(rs.getString("ShortName"));
        regulation.setRank(rs.getObject("Rank", Integer.class));
        regulation.setPrimaryName(rs.getString("primaryName"));
        regulation.setDescription(rs.getString("Description"));
        regulation.setAdditionalInfo(rs.getString("AdditionalInfo"));
        regulation.setPublicationDate(fromSqlDate(rs.getDate("PublicationDate")));
        regulation.setCommentsDate(fromSqlDate(rs.getDate("CommentsDate")));
        regulation.setFinalisationDate(fromSqlDate(rs.getDate("FinalisationDate")));
        regulation.setComplianceDate(fromSqlDate(rs.getDate("ComplianceDate")));
        regulation.setLegalAdvice(rs.getString("LegalAdvice"));
        regulation.setCreateDateTime(rs.getTimestamp("CreateDatetime"));
        regulation.setLastUpdateDateTime(rs.getTimestamp("LastUpdateDatetime"));
        regulation.setDeletedDateTime(rs.getTimestamp("DeletedDatetime"));
        regulation.setRegulationMaturityId(rs.getObject("RegulationMaturity_ID", Integer.class));
        regulation.setRegulationProbabilityId(rs.getObject("RegulationProbability_ID", Integer.class));
        regulation.setRegulationStatusId(rs.getObject("RegulationStatus_ID", Integer.class));
        try {
            regulation.setStatusName(rs.getString("statusName"));
        } catch (SQLException e) {
            regulation.setStatusName(null);
        }
        regulation.setRegulationImpactRatingId(rs.getObject("RegulationImpactRating_ID", Integer.class));
        regulation.setLegalAdviceTypeId(rs.getObject("LegalAdviceType_ID", Integer.class));
        regulation.setRegulationStageId(rs.getObject("RegulationStage_ID", Integer.class));
        regulation.setComplianceLevelId(rs.getObject("ComplianceLevel_ID", Integer.class));
        regulation.setLastUpdateUserId(rs.getObject("LastUpdate_UserID", Integer.class));
        return regulation;
    }

    private java.sql.Date toSqlDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        try {
            return java.sql.Date.valueOf(dateStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String fromSqlDate(java.sql.Date date) {
        return date == null ? null : date.toString();
    }

    // ============================================
    // STAKEHOLDER METHODS
    // ============================================

    public java.util.List<java.util.Map<String, Object>> getDirectStakeholdersForRegulation(int regulationId) throws SQLException {
        String sql = """
            SELECT 
                oxp.ID as object_x_ipid,
                oxp.ipid AS people_id,
                oxp.RoleID AS role_id,
                oxp.statusID AS status_id,
                st.primaryname AS status_name,
                oxp.isDelegateOF AS delegate_of_id,
                r.PrimaryName AS role,
                CONCAT(p.First_Name, ' ', p.Last_Name) AS name,
                ou.Name AS org_unit,
                ou.ID AS org_unit_id,
                CASE oxp.AcceptedID WHEN 1 THEN 'True' ELSE 'False' END AS role_accepted,
                CONCAT(dp.First_Name, ' ', dp.Last_Name) AS delegate_of,
                oxp.createdatetime AS date_accepted
            FROM regulation_x_objectxpeople rx
            JOIN object_x_people oxp ON rx.Object_x_ipid = oxp.ID
            JOIN object_role r ON oxp.RoleID = r.ID
            JOIN people p ON oxp.ipid = p.ID
            JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
            LEFT JOIN status st ON oxp.statusID = st.ID
            LEFT JOIN object_x_people d_oxp ON oxp.isDelegateOF = d_oxp.ID
            LEFT JOIN people dp ON d_oxp.ipid = dp.ID
            WHERE rx.RegulationID = ?
            ORDER BY r.PrimaryName, p.Last_Name, p.First_Name
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<java.util.Map<String, Object>> stakeholders = new java.util.ArrayList<>();
                while (rs.next()) {
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("objectXPeopleId", rs.getInt("object_x_ipid"));
                    row.put("peopleId", rs.getInt("people_id"));
                    row.put("roleId", rs.getInt("role_id"));
                    row.put("statusId", rs.getObject("status_id"));
                    row.put("statusName", rs.getString("status_name"));
                    row.put("delegateOfId", rs.getObject("delegate_of_id"));
                    row.put("role", rs.getString("role"));
                    row.put("name", rs.getString("name"));
                    row.put("orgUnit", rs.getString("org_unit"));
                    row.put("orgUnitId", rs.getInt("org_unit_id"));
                    row.put("roleAccepted", rs.getString("role_accepted"));
                    row.put("delegateOf", rs.getString("delegate_of"));
                    row.put("dateAccepted", rs.getTimestamp("date_accepted"));
                    stakeholders.add(row);
                }
                return stakeholders;
            }
        }
    }

    public void saveStakeholdersChanges(int regulationId, java.util.Map<String, Object> changes, int currentUserId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                @SuppressWarnings("unchecked") java.util.List<java.util.Map<String, Object>> inserts = (java.util.List<java.util.Map<String, Object>>) changes.getOrDefault("inserts", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") java.util.List<java.util.Map<String, Object>> updates = (java.util.List<java.util.Map<String, Object>>) changes.getOrDefault("updates", java.util.Collections.emptyList());
                @SuppressWarnings("unchecked") java.util.List<java.util.Map<String, Object>> deletes = (java.util.List<java.util.Map<String, Object>>) changes.getOrDefault("deletes", java.util.Collections.emptyList());

                String userName = "System"; // Default fallback
                if (currentUserId > 0) {
                    String fullName = getPersonFullName(currentUserId);
                    if (fullName != null && !fullName.trim().isEmpty()) {
                        userName = fullName;
                    } else {
                        userName = "User ID: " + currentUserId;
                    }
                }

                // Handle inserts with audit logging
                for (java.util.Map<String, Object> row : inserts) {
                    validateRequired(row);
                    java.util.Map<String, Object> convertedRow = convertRow(conn, row);
                    int oxpId = createObjectXPeople(conn, convertedRow, currentUserId);
                    linkStakeholderToRegulation(conn, regulationId, oxpId);
                    
                    // Insert audit records for new stakeholder
                    Integer userId = (Integer) convertedRow.get("userId");
                    Integer roleId = (Integer) convertedRow.get("roleId");
                    Integer statusId = (Integer) convertedRow.get("statusId");
                    if (userId != null && roleId != null) {
                        String fullName = getPersonFullName(userId);
                        String roleName = getRoleNameForStakeholder(conn, roleId);
                        String statusName = statusId != null ? getStatusNameById(conn, statusId) : "Active";
                        insertStakeholderAudit(conn, regulationId, "Name", null, fullName, "link", "Added", userName);
                        insertStakeholderAudit(conn, regulationId, "Role", null, roleName, "link", "Added", userName);
                        insertStakeholderAudit(conn, regulationId, "Role Status", null, statusName, "link", "Added", userName);
                    }
                }

                // Handle updates with audit logging
                for (java.util.Map<String, Object> row : updates) {
                    validateRequired(row);
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in update row");
                    
                    // Get old data before update
                    StakeholderSnapshot oldData = getStakeholderInfoByOxpId(conn, oxpId);
                    
                    // Perform update
                    java.util.Map<String, Object> convertedRow = convertRow(conn, row);
                    updateObjectXPeople(conn, oxpId, convertedRow);
                    
                    // Get new data after update
                    StakeholderSnapshot newData = getStakeholderInfoByOxpId(conn, oxpId);
                    
                    // Insert audit records for changed fields
                    if (oldData != null && newData != null) {
                        if (!safeEquals(oldData.fullName, newData.fullName))
                            insertStakeholderAudit(conn, regulationId, "Name", oldData.fullName, newData.fullName, "edit", "Updated", userName);
                        if (!safeEquals(oldData.roleName, newData.roleName))
                            insertStakeholderAudit(conn, regulationId, "Role", oldData.roleName, newData.roleName, "edit", "Updated", userName);
                        if (!safeEquals(oldData.statusName, newData.statusName))
                            insertStakeholderAudit(conn, regulationId, "Role Status", oldData.statusName, newData.statusName, "edit", "Updated", userName);
                    }
                }

                // Handle deletes with audit logging
                for (java.util.Map<String, Object> row : deletes) {
                    Integer oxpId = getInt(row.get("objectXPeopleId"));
                    if (oxpId == null) throw new IllegalArgumentException("Missing objectXPeopleId in delete row");
                    
                    // Get old data before deletion
                    StakeholderSnapshot oldData = getStakeholderInfoByOxpId(conn, oxpId);
                    
                    // Perform deletion
                    unlinkStakeholderFromRegulation(conn, regulationId, oxpId);
                    if (!hasAnyOtherLink(conn, oxpId)) {
                        deleteObjectXPeople(conn, oxpId);
                    }
                    
                    // Insert audit records for deletion
                    if (oldData != null) {
                        insertStakeholderAudit(conn, regulationId, "Name", oldData.fullName, null, "delete", "Deleted", userName);
                        insertStakeholderAudit(conn, regulationId, "Role", oldData.roleName, null, "delete", "Deleted", userName);
                        insertStakeholderAudit(conn, regulationId, "Role Status", oldData.statusName, null, "delete", "Deleted", userName);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof SQLException) throw (SQLException) e;
                throw new SQLException(e.getMessage(), e);
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private java.util.Map<String, Object> convertRow(Connection conn, java.util.Map<String, Object> row) throws SQLException {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("roleId", getInt(row.get("roleId")));
        m.put("userId", getInt(row.get("ipid")));
        m.put("statusId", getInt(row.get("statusId")));
        m.put("acceptedId", 1);
        Integer delegateOxpId = getInt(row.get("delegateIpId"));
        m.put("delegateOfId", delegateOxpId);
        return m;
    }

    private void validateRequired(java.util.Map<String, Object> row) {
        if (getInt(row.get("roleId")) == null) throw new IllegalArgumentException("Role is required");
        if (getInt(row.get("ipid")) == null) throw new IllegalArgumentException("Name is required");
    }

    private Integer getInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }

    public int createObjectXPeople(Connection conn, java.util.Map<String, Object> stakeholder, int currentUserId) throws SQLException {
        String sql = """
            INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
            VALUES (?, ?, ?, 2, 1, NOW(), ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Integer delegateOfId = (Integer) stakeholder.get("delegateIpId");
            if (delegateOfId != null) {
                ps.setInt(1, delegateOfId);
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setInt(2, (Integer) stakeholder.get("userId")); // ipid
            ps.setInt(3, (Integer) stakeholder.get("roleId")); // RoleID
            ps.setInt(4, currentUserId); // lastupdateuser_id

            ps.executeUpdate();

            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating object_x_people failed, no ID obtained.");
                }
            }
        }
    }

    private void updateObjectXPeople(Connection conn, int objectXPeopleId, java.util.Map<String, Object> stakeholder) throws SQLException {
        String sql = """
            UPDATE object_x_people 
            SET RoleID = ?, ipid = ?, statusID = ?, AcceptedID = 2, isDelegateOF = ?, lastupdatedatetime = NOW()
            WHERE ID = ?
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, (Integer) stakeholder.get("roleId"));
            ps.setInt(2, (Integer) stakeholder.get("userId"));
            Integer statusId = (Integer) stakeholder.get("statusId");
            if (statusId != null) {
                ps.setInt(3, statusId);
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            Integer delegateOfId = (Integer) stakeholder.get("delegateIpId");
            if (delegateOfId != null) {
                ps.setInt(4, delegateOfId);
            } else {
                ps.setNull(4, Types.INTEGER);
            }
            ps.setInt(5, objectXPeopleId);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("No rows updated for object_x_people ID: " + objectXPeopleId);
            }
        }
    }

    private void deleteObjectXPeople(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM object_x_people WHERE ID = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.executeUpdate();
        }
    }

    public void linkStakeholderToRegulation(Connection conn, int regulationId, int objectXPeopleId) throws SQLException {
        String sql = """
            INSERT INTO regulation_x_objectxpeople (RegulationID, Object_x_ipid, CreateDatetime)
            VALUES (?, ?, NOW())
        """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            ps.setInt(2, objectXPeopleId);
            try {
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                //system.out.println("⚠️ Link already exists: RegulationID=" + regulationId + ", Object_x_ipid=" + objectXPeopleId);
            }
        }
    }

    private void unlinkStakeholderFromRegulation(Connection conn, int regulationId, int objectXPeopleId) throws SQLException {
        String sql = "DELETE FROM regulation_x_objectxpeople WHERE RegulationID = ? AND Object_x_ipid = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            ps.setInt(2, objectXPeopleId);
            ps.executeUpdate();
        }
    }

    private boolean hasAnyOtherLink(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = "SELECT ("
                + " (SELECT COUNT(*) FROM system_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM dataset_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM interface_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM glossary_x_objectxpeople WHERE Object_x_ipid=?) +"
                + " (SELECT COUNT(*) FROM regulation_x_objectxpeople WHERE Object_x_ipid=?)"
                + ") AS cnt";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectXPeopleId);
            ps.setInt(2, objectXPeopleId);
            ps.setInt(3, objectXPeopleId);
            ps.setInt(4, objectXPeopleId);
            ps.setInt(5, objectXPeopleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    // ============================================
    // AUDIT METHODS
    // ============================================

    /**
     * Writes regulation_audit_history rows for a new regulation using the caller's connection (no commit).
     */
    private void insertRegulationAuditHistoryRecords(Connection conn, int regulationId, String userName) throws SQLException {
        String regulationDataSql = "SELECT * FROM regulation WHERE ID = ?";
        try (PreparedStatement regulationStmt = conn.prepareStatement(regulationDataSql)) {
            regulationStmt.setInt(1, regulationId);
            try (ResultSet regulationRs = regulationStmt.executeQuery()) {
                if (!regulationRs.next()) {
                    throw new SQLException("Regulation not found with ID: " + regulationId);
                }

                String auditSql = """
                    INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                    VALUES (?, ?, ?, ?, ?, NULL, ?, ?)
                    """;
                try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {

            // Primary Name
            String primaryName = regulationRs.getString("primaryName");
            if (primaryName != null && !primaryName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Primary Name", primaryName, userName);
            }
            
            // Ref Number
            String refNumber = regulationRs.getString("RefNumber");
            if (refNumber != null && !refNumber.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Ref Number", refNumber, userName);
            }
            
            // Short Name
            String shortName = regulationRs.getString("ShortName");
            if (shortName != null && !shortName.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Short Name", shortName, userName);
            }
            
            // Description
            String description = regulationRs.getString("Description");
            if (description != null && !description.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Description", description, userName);
            }
            
            // Additional Info
            String additionalInfo = regulationRs.getString("AdditionalInfo");
            if (additionalInfo != null && !additionalInfo.trim().isEmpty()) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Additional Info", additionalInfo, userName);
            }
            
            // Parent Regulation
            Integer parentId = regulationRs.getObject("Parent_ID", Integer.class);
            if (parentId != null) {
                String parentName = getRegulationName(conn, parentId);
                if (parentName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Parent Regulation", parentName, userName);
                }
            }
            
            // Status
            Integer statusId = regulationRs.getObject("RegulationStatus_ID", Integer.class);
            if (statusId != null) {
                String statusName = getRegulationStatusName(conn, statusId);
                if (statusName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Status Change", "Status", statusName, userName);
                }
            }
            
            // Stage
            Integer stageId = regulationRs.getObject("RegulationStage_ID", Integer.class);
            if (stageId != null) {
                String stageName = getRegulationStageName(conn, stageId);
                if (stageName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Stage", stageName, userName);
                }
            }
            
            // Maturity
            Integer maturityId = regulationRs.getObject("RegulationMaturity_ID", Integer.class);
            if (maturityId != null) {
                String maturityName = getRegulationMaturityName(conn, maturityId);
                if (maturityName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Maturity", maturityName, userName);
                }
            }
            
            // Probability
            Integer probabilityId = regulationRs.getObject("RegulationProbability_ID", Integer.class);
            if (probabilityId != null) {
                String probabilityName = getRegulationProbabilityName(conn, probabilityId);
                if (probabilityName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Probability", probabilityName, userName);
                }
            }
            
            // Compliance Level
            Integer complianceLevelId = regulationRs.getObject("ComplianceLevel_ID", Integer.class);
            if (complianceLevelId != null) {
                String complianceLevelName = getRegulationComplianceLevelName(conn, complianceLevelId);
                if (complianceLevelName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Compliance Level", complianceLevelName, userName);
                }
            }
            
            // Impact Rating
            Integer impactRatingId = regulationRs.getObject("RegulationImpactRating_ID", Integer.class);
            if (impactRatingId != null) {
                String impactRatingName = getRegulationImpactRatingName(conn, impactRatingId);
                if (impactRatingName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Impact Rating", impactRatingName, userName);
                }
            }
            
            // Legal Advice Type
            Integer legalAdviceTypeId = regulationRs.getObject("LegalAdviceType_ID", Integer.class);
            if (legalAdviceTypeId != null) {
                String legalAdviceTypeName = getLegalAdviceTypeName(conn, legalAdviceTypeId);
                if (legalAdviceTypeName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Legal Advice Type", legalAdviceTypeName, userName);
                }
            }
            
            // Is Public
            Integer isPublicId = regulationRs.getObject("Is_Public", Integer.class);
            if (isPublicId != null) {
                String isPublicName = getViewingName(conn, isPublicId);
                if (isPublicName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Is Public", isPublicName, userName);
                }
            }
            
            // Publication Date
            Date publicationDate = regulationRs.getDate("PublicationDate");
            if (publicationDate != null) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Publication Date", publicationDate.toString(), userName);
            }
            
            // Comments Date
            Date commentsDate = regulationRs.getDate("CommentsDate");
            if (commentsDate != null) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Comments Date", commentsDate.toString(), userName);
            }
            
            // Finalisation Date
            Date finalisationDate = regulationRs.getDate("FinalisationDate");
            if (finalisationDate != null) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Finalisation Date", finalisationDate.toString(), userName);
            }
            
            // Compliance Date
            Date complianceDate = regulationRs.getDate("ComplianceDate");
            if (complianceDate != null) {
                createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Compliance Date", complianceDate.toString(), userName);
            }
            
            // Created By
            Integer createdById = regulationRs.getObject("LastUpdate_UserID", Integer.class);
            if (createdById != null) {
                String createdByName = getPersonFullName(conn, createdById);
                if (createdByName != null) {
                    createNewAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", "Added", "Created By", createdByName, userName);
                }
            }
                }
            }
        }
    }

    /**
     * إنشاء audit records للـ regulation الجديد
     * يتم استدعاء هذا method بعد إنشاء الـ regulation بنجاح
     */
    public void createRegulationAuditRecords(int regulationId, String userName) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            insertRegulationAuditHistoryRecords(conn, regulationId, userName);
            conn.commit();
        } catch (SQLException e) {
            System.err.println("❌ RegulationDAO.createRegulationAuditRecords - ERROR: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Same as {@link #createRegulationAuditRecords(int, String)} using the caller's connection
     * (for example bulk upload with an open transaction). Does not commit or close the connection.
     */
    public void createRegulationAuditRecords(Connection conn, int regulationId, String userName) throws SQLException {
        insertRegulationAuditHistoryRecords(conn, regulationId, userName);
    }

    /**
     * إنشاء سجل في جدول regulation_audit بعد إنشاء الـ regulation
     * يتم استدعاء هذا method بعد إنشاء الـ regulation بنجاح
     */
    public void createRegulationAuditRecord(int regulationId) throws SQLException {
        //system.out.println("🔍 RegulationDAO.createRegulationAuditRecord - Creating snapshot for ID: " + regulationId);
        try (Connection conn = DatabaseConnection.getConnection()) {
            createRegulationAuditRecord(conn, regulationId);
        } catch (SQLException e) {
            System.err.println("❌ RegulationDAO.createRegulationAuditRecord - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Snapshot row in regulation_audit using the caller's connection (no commit).
     */
    public void createRegulationAuditRecord(Connection conn, int regulationId) throws SQLException {
        String sql = """
            INSERT INTO regulation_audit (
                ID, Parent_ID, Is_Public, RefNumber, ShortName, Rank, primaryName, Description, AdditionalInfo,
                PublicationDate, CommentsDate, FinalisationDate, ComplianceDate, LegalAdvice,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime,
                RegulationMaturity_ID, RegulationProbability_ID, RegulationStatus_ID,
                RegulationImpactRating_ID, LegalAdviceType_ID, RegulationStage_ID,
                ComplianceLevel_ID, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, RefNumber, ShortName, Rank, primaryName, Description, AdditionalInfo,
                PublicationDate, CommentsDate, FinalisationDate, ComplianceDate, LegalAdvice,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime,
                RegulationMaturity_ID, RegulationProbability_ID, RegulationStatus_ID,
                RegulationImpactRating_ID, LegalAdviceType_ID, RegulationStage_ID,
                ComplianceLevel_ID, LastUpdate_UserID, 'Added'
            FROM regulation 
            WHERE ID = ?
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ RegulationDAO.createRegulationAuditRecord - ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * إنشاء audit record جديد والحصول على auditidpk
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int regulationId, String object, String event, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("    📝 Creating audit record: [" + object + "][" + event + "][" + field + "] = " + value);
        auditStmt.setInt(1, regulationId);      // id
        auditStmt.setString(2, object);         // object
        auditStmt.setString(3, event);          // event
        auditStmt.setString(4, updateType);     // updateType
        auditStmt.setString(5, field);          // field
        auditStmt.setString(6, value);          // to
        auditStmt.setString(7, userName);       // author
        
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Overloaded version for stakeholder audit records (object and event are hardcoded in SQL)
     */
    private int createNewAuditRecord(Connection conn, PreparedStatement auditStmt, int regulationId, String updateType, String field, String value, String userName) throws SQLException {
        //system.out.println("    📝 Creating stakeholder audit record: [" + field + "] = " + value);
        auditStmt.setInt(1, regulationId);      // id
        auditStmt.setString(2, updateType);     // updateType
        auditStmt.setString(3, field);          // field
        auditStmt.setString(4, value);          // to
        auditStmt.setString(5, userName);       // author
        
        auditStmt.executeUpdate();
        
        try (ResultSet generatedKeys = auditStmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }
        //system.out.println("    ⚠️ No generated key returned");
        return -1;
    }

    /**
     * إنشاء audit records عند تحديث الـ regulation
     * يقارن القيم القديمة بالجديدة ويسجل الفروقات
     */
    public void createRegulationUpdateAuditRecords(int regulationId, Regulation oldRegulation, Regulation newRegulation, String userName) throws SQLException {
        //system.out.println("🔍 RegulationDAO.createRegulationUpdateAuditRecords - START for ID: " + regulationId);
        Connection conn = null;
        PreparedStatement auditStmt = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            
            String auditSql = """
                INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
            auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS);
            
            // Primary Name
            if (!isEqual(oldRegulation.getPrimaryName(), newRegulation.getPrimaryName())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Primary Name", oldRegulation.getPrimaryName(), newRegulation.getPrimaryName(), userName);
            }
            
            // Ref Number
            if (!isEqual(oldRegulation.getRefNumber(), newRegulation.getRefNumber())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Ref Number", oldRegulation.getRefNumber(), newRegulation.getRefNumber(), userName);
            }
            
            // Short Name
            if (!isEqual(oldRegulation.getShortName(), newRegulation.getShortName())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Short Name", oldRegulation.getShortName(), newRegulation.getShortName(), userName);
            }
            
            // Description
            if (!isEqual(oldRegulation.getDescription(), newRegulation.getDescription())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Description", oldRegulation.getDescription(), newRegulation.getDescription(), userName);
            }
            
            // Additional Info
            if (!isEqual(oldRegulation.getAdditionalInfo(), newRegulation.getAdditionalInfo())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Additional Info", oldRegulation.getAdditionalInfo(), newRegulation.getAdditionalInfo(), userName);
            }
            
            // Parent Regulation
            if (!isEqual(oldRegulation.getParentId(), newRegulation.getParentId())) {
                String oldParentName = oldRegulation.getParentId() != null ? getRegulationName(conn, oldRegulation.getParentId()) : null;
                String newParentName = newRegulation.getParentId() != null ? getRegulationName(conn, newRegulation.getParentId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Parent Regulation", oldParentName, newParentName, userName);
            }
            
            // Status (يستخدم "Status Change" كـ updateType)
            if (!isEqual(oldRegulation.getRegulationStatusId(), newRegulation.getRegulationStatusId())) {
                String oldStatusName = oldRegulation.getRegulationStatusId() != null ? getRegulationStatusName(conn, oldRegulation.getRegulationStatusId()) : null;
                String newStatusName = newRegulation.getRegulationStatusId() != null ? getRegulationStatusName(conn, newRegulation.getRegulationStatusId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Status Change", "Status", oldStatusName, newStatusName, userName);
            }
            
            // Stage
            if (!isEqual(oldRegulation.getRegulationStageId(), newRegulation.getRegulationStageId())) {
                String oldStageName = oldRegulation.getRegulationStageId() != null ? getRegulationStageName(conn, oldRegulation.getRegulationStageId()) : null;
                String newStageName = newRegulation.getRegulationStageId() != null ? getRegulationStageName(conn, newRegulation.getRegulationStageId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Stage", oldStageName, newStageName, userName);
            }
            
            // Maturity
            if (!isEqual(oldRegulation.getRegulationMaturityId(), newRegulation.getRegulationMaturityId())) {
                String oldMaturityName = oldRegulation.getRegulationMaturityId() != null ? getRegulationMaturityName(conn, oldRegulation.getRegulationMaturityId()) : null;
                String newMaturityName = newRegulation.getRegulationMaturityId() != null ? getRegulationMaturityName(conn, newRegulation.getRegulationMaturityId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Maturity", oldMaturityName, newMaturityName, userName);
            }
            
            // Probability
            if (!isEqual(oldRegulation.getRegulationProbabilityId(), newRegulation.getRegulationProbabilityId())) {
                String oldProbabilityName = oldRegulation.getRegulationProbabilityId() != null ? getRegulationProbabilityName(conn, oldRegulation.getRegulationProbabilityId()) : null;
                String newProbabilityName = newRegulation.getRegulationProbabilityId() != null ? getRegulationProbabilityName(conn, newRegulation.getRegulationProbabilityId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Probability", oldProbabilityName, newProbabilityName, userName);
            }
            
            // Compliance Level
            if (!isEqual(oldRegulation.getComplianceLevelId(), newRegulation.getComplianceLevelId())) {
                String oldComplianceLevelName = oldRegulation.getComplianceLevelId() != null ? getRegulationComplianceLevelName(conn, oldRegulation.getComplianceLevelId()) : null;
                String newComplianceLevelName = newRegulation.getComplianceLevelId() != null ? getRegulationComplianceLevelName(conn, newRegulation.getComplianceLevelId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Compliance Level", oldComplianceLevelName, newComplianceLevelName, userName);
            }
            
            // Legal Advice Type
            if (!isEqual(oldRegulation.getLegalAdviceTypeId(), newRegulation.getLegalAdviceTypeId())) {
                String oldLegalAdviceTypeName = oldRegulation.getLegalAdviceTypeId() != null ? getLegalAdviceTypeName(conn, oldRegulation.getLegalAdviceTypeId()) : null;
                String newLegalAdviceTypeName = newRegulation.getLegalAdviceTypeId() != null ? getLegalAdviceTypeName(conn, newRegulation.getLegalAdviceTypeId()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Legal Advice Type", oldLegalAdviceTypeName, newLegalAdviceTypeName, userName);
            }
            
            // Is Public
            if (!isEqual(oldRegulation.getIsPublic(), newRegulation.getIsPublic())) {
                String oldIsPublicName = oldRegulation.getIsPublic() != null ? getViewingName(conn, oldRegulation.getIsPublic()) : null;
                String newIsPublicName = newRegulation.getIsPublic() != null ? getViewingName(conn, newRegulation.getIsPublic()) : null;
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Is Public", oldIsPublicName, newIsPublicName, userName);
            }
            
            // Publication Date
            if (!isEqual(oldRegulation.getPublicationDate(), newRegulation.getPublicationDate())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Publication Date", oldRegulation.getPublicationDate(), newRegulation.getPublicationDate(), userName);
            }
            
            // Comments Date
            if (!isEqual(oldRegulation.getCommentsDate(), newRegulation.getCommentsDate())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Comments Date", oldRegulation.getCommentsDate(), newRegulation.getCommentsDate(), userName);
            }
            
            // Finalisation Date
            if (!isEqual(oldRegulation.getFinalisationDate(), newRegulation.getFinalisationDate())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Finalisation Date", oldRegulation.getFinalisationDate(), newRegulation.getFinalisationDate(), userName);
            }
            
            // Compliance Date
            if (!isEqual(oldRegulation.getComplianceDate(), newRegulation.getComplianceDate())) {
                createUpdateAuditRecord(conn, auditStmt, regulationId, "Regulation", "Details", 
                    "Updated", "Compliance Date", oldRegulation.getComplianceDate(), newRegulation.getComplianceDate(), userName);
            }
            
            conn.commit();
            //system.out.println("✅ RegulationDAO.createRegulationUpdateAuditRecords - COMMITTED successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ RegulationDAO.createRegulationUpdateAuditRecords - ERROR: " + e.getMessage());
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
            int regulationId, String object, String event, String updateType, 
            String field, String fromValue, String toValue, String userName) throws SQLException {
        
        //system.out.println("    📝 Update audit: [" + field + "] from '" + fromValue + "' to '" + toValue + "'");
        
        auditStmt.setInt(1, regulationId);
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
     * Method لإنشاء snapshot جديد في regulation_audit عند الـ update
     */
    public void createRegulationUpdateAuditSnapshot(int regulationId) throws SQLException {
        //system.out.println("🔍 RegulationDAO.createRegulationUpdateAuditSnapshot - Creating update snapshot for ID: " + regulationId);
        String sql = """
            INSERT INTO regulation_audit (
                ID, Parent_ID, Is_Public, RefNumber, ShortName, Rank, primaryName, Description, AdditionalInfo,
                PublicationDate, CommentsDate, FinalisationDate, ComplianceDate, LegalAdvice,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime,
                RegulationMaturity_ID, RegulationProbability_ID, RegulationStatus_ID,
                RegulationImpactRating_ID, LegalAdviceType_ID, RegulationStage_ID,
                ComplianceLevel_ID, LastUpdate_UserID, RevType
            )
            SELECT 
                ID, Parent_ID, Is_Public, RefNumber, ShortName, Rank, primaryName, Description, AdditionalInfo,
                PublicationDate, CommentsDate, FinalisationDate, ComplianceDate, LegalAdvice,
                CreateDatetime, LastUpdateDatetime, DeletedDatetime,
                RegulationMaturity_ID, RegulationProbability_ID, RegulationStatus_ID,
                RegulationImpactRating_ID, LegalAdviceType_ID, RegulationStage_ID,
                ComplianceLevel_ID, LastUpdate_UserID, 'Updated'
            FROM regulation 
            WHERE ID = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ Error creating update snapshot: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Helper method للمقارنة بين القيم (يتعامل مع null)
     */
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    // Helper methods للحصول على الأسماء (مع استخدام Connection موجود)
    
    private String getRegulationName(Connection conn, int regulationId) throws SQLException {
        String sql = "SELECT primaryName FROM regulation WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryName");
            }
        }
        return null;
    }

    private String getRegulationStatusName(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_status WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getRegulationStageName(Connection conn, int stageId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_stage WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, stageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getRegulationMaturityName(Connection conn, int maturityId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_maturity WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, maturityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getRegulationProbabilityName(Connection conn, int probabilityId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_probability WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, probabilityId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getRegulationComplianceLevelName(Connection conn, int levelId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_compliance_level WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, levelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getRegulationImpactRatingName(Connection conn, int ratingId) throws SQLException {
        String sql = "SELECT PrimaryName FROM regulation_impact_rating WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ratingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getLegalAdviceTypeName(Connection conn, int typeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM legal_advice_type WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    private String getViewingName(Connection conn, int viewingId) throws SQLException {
        String sql = "SELECT Name FROM viewing WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, viewingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    private String getPersonFullName(Connection conn, int personId) throws SQLException {
        String sql = "SELECT CONCAT(First_Name, ' ', Last_Name) as fullName FROM people WHERE ID = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, personId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("fullName");
            }
        }
        return null;
    }

    // Helper method without Connection for use outside audit context
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

    // ============================================
    // DEFAULT STAKEHOLDER METHODS
    // ============================================

    /**
     * Get module ID by module name
     */
    private int getModuleId(Connection conn, String moduleName) throws SQLException {
        String sql = "SELECT id FROM module WHERE primaryname = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, moduleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("Module not found: " + moduleName);
    }

    /**
     * Get default role ID for Regulation module
     */
    private int getDefaultRoleId(Connection conn) throws SQLException {
        int moduleId = getModuleId(conn, "Regulation");
        String sql = "SELECT id FROM object_role WHERE module = ? ORDER BY primaryname LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, moduleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        throw new SQLException("No role found for Regulation module");
    }

    /**
     * Create default stakeholder for a new regulation
     * Creates stakeholders for all default roles the creator should receive based on role assignments
     */
    private void createDefaultStakeholder(Connection conn, int regulationId, int userId) throws SQLException {
        try {
            // Get module ID for Regulation
            int moduleId = DefaultStakeholderUtil.getModuleId(conn, "Regulation");
            
            // Get all default roles the creator should receive
            List<Integer> rolesToAssign = DefaultStakeholderUtil.getDefaultRolesForCreator(conn, moduleId, userId);
            
            if (rolesToAssign.isEmpty()) {
                // No default roles to assign
                return;
            }
            
            // Create stakeholders for each role
            for (Integer roleId : rolesToAssign) {
                try {
                    // Insert into object_x_people
                    String insertOXP = """
                        INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
                        VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
                    """;
                    
                    int objectXPeopleId;
                    try (PreparedStatement ps = conn.prepareStatement(insertOXP, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, userId); // ipid
                        ps.setInt(2, roleId); // RoleID
                        ps.setInt(3, userId); // lastupdateuser_id
                        
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected == 0) {
                            throw new SQLException("Failed to insert object_x_people");
                        }
                        
                        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                objectXPeopleId = generatedKeys.getInt(1);
                            } else {
                                throw new SQLException("Failed to get generated key for object_x_people");
                            }
                        }
                    }
                    
                    // Insert into regulation_x_objectxpeople
                    String insertRXOP = """
                        INSERT INTO regulation_x_objectxpeople (RegulationID, Object_x_ipid, CreateDatetime)
                        VALUES (?, ?, NOW())
                    """;
                    
                    try (PreparedStatement ps = conn.prepareStatement(insertRXOP)) {
                        ps.setInt(1, regulationId);
                        ps.setInt(2, objectXPeopleId);
                        
                        int rowsAffected = ps.executeUpdate();
                        if (rowsAffected == 0) {
                            throw new SQLException("Failed to insert regulation_x_objectxpeople");
                        }
                    }
                } catch (SQLException e) {
                    // Log error but continue with other roles
                    System.err.println("❌ Error creating default stakeholder for role " + roleId + ": " + e.getMessage());
                    // Continue processing other roles
                }
            }
        } catch (SQLException e) {
            // If module not found or other critical error, log but don't fail object creation
            System.err.println("❌ Error in createDefaultStakeholder: " + e.getMessage());
            // Don't throw - allow object creation to continue
        }
    }

    /**
     * Create audit records for stakeholder after creating regulation
     */
    public void createStakeholderAuditRecords(int regulationId, String userName, String userFullName, int roleId) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            createStakeholderAuditRecords(conn, regulationId, userName, userFullName, roleId);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    /**
     * Same as {@link #createStakeholderAuditRecords(int, String, String, int)} using the caller's connection.
     * Does not commit or close the connection.
     */
    public void createStakeholderAuditRecords(Connection conn, int regulationId, String userName, String userFullName,
            int roleId) throws SQLException {
        String auditSql = """
                INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author)
                VALUES (?, 'Stakeholder', 'link', ?, ?, NULL, ?, ?)
            """;

        Integer actualRoleId = getStakeholderRoleId(conn, regulationId);
        if (actualRoleId == null) {
            actualRoleId = roleId;
        }

        String roleName = getRoleNameForStakeholder(conn, actualRoleId);
        if (roleName == null) {
            roleName = "Regulation Owner";
        }

        try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
            createNewAuditRecord(conn, auditStmt, regulationId, "Added", "Role", roleName, userName);
        }

        Integer statusId = getStakeholderStatusId(conn, regulationId);
        String statusName = "Active";
        if (statusId != null) {
            String fetchedStatusName = getStatusNameById(conn, statusId);
            if (fetchedStatusName != null) {
                statusName = fetchedStatusName;
            }
        }
        try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
            createNewAuditRecord(conn, auditStmt, regulationId, "Added", "Role Status", statusName, userName);
        }

        try (PreparedStatement auditStmt = conn.prepareStatement(auditSql, Statement.RETURN_GENERATED_KEYS)) {
            createNewAuditRecord(conn, auditStmt, regulationId, "Added", "Name", userFullName, userName);
        }
    }

    /**
     * Get role name from object_role
     */
    private String getRoleNameForStakeholder(Connection conn, int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM object_role WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * Get actual roleID from object_x_people for the stakeholder
     */
    private Integer getStakeholderRoleId(Connection conn, int regulationId) throws SQLException {
        String sql = "SELECT oxp.roleID FROM object_x_people oxp " +
                    "JOIN regulation_x_objectxpeople rxop ON rxop.Object_x_ipid = oxp.id " +
                    "WHERE rxop.RegulationID = ? " +
                    "ORDER BY rxop.id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("roleID");
            }
        }
        return null;
    }

    /**
     * Get statusID from object_x_people for the stakeholder
     */
    private Integer getStakeholderStatusId(Connection conn, int regulationId) throws SQLException {
        String sql = "SELECT oxp.statusID FROM object_x_people oxp " +
                    "JOIN regulation_x_objectxpeople rxop ON rxop.Object_x_ipid = oxp.id " +
                    "WHERE rxop.RegulationID = ? " +
                    "ORDER BY rxop.id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, regulationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("statusID");
            }
        }
        return null;
    }

    /**
     * Get status name from object_x_ip_status by statusID
     */
    private String getStatusNameById(Connection conn, int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM object_x_ip_status WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, statusId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    // Helper methods for audit logging
    private static class StakeholderSnapshot {
        String fullName;
        String roleName;
        String statusName;
    }

    private StakeholderSnapshot getStakeholderInfoByOxpId(Connection conn, int objectXPeopleId) throws SQLException {
        String sql = """
            SELECT oxp.id, ipid, CONCAT(p.First_Name, ' ', p.Last_Name) as fullName, 
              orl.primaryname as roleName, ips.primaryname as statusName
            FROM object_x_people oxp
            LEFT JOIN people p ON oxp.ipid = p.ID
            LEFT JOIN object_role orl ON oxp.roleID = orl.ID
            LEFT JOIN object_x_ip_status ips ON oxp.statusID = ips.ID
            WHERE oxp.id = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, objectXPeopleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    StakeholderSnapshot s = new StakeholderSnapshot();
                    s.fullName = rs.getString("fullName");
                    s.roleName = rs.getString("roleName");
                    s.statusName = rs.getString("statusName");
                    return s;
                }
            }
        }
        return null;
    }

    private boolean safeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    private void insertStakeholderAudit(Connection conn, int regulationId, String field, String oldValue, String newValue, String event, String updateType, String userName) throws SQLException {
        String sql = "INSERT INTO regulation_audit_history (id, object, event, updateType, field, `from`, `to`, author) VALUES (?, 'Stakeholder', ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, regulationId);
            stmt.setString(2, event);
            stmt.setString(3, updateType);
            stmt.setString(4, field);
            stmt.setString(5, oldValue);
            stmt.setString(6, newValue);
            stmt.setString(7, userName);
            stmt.executeUpdate();
        }
    }

    public boolean acceptStakeholderStatus(int objectXPeopleId, int currentUserId, int regulationId) throws SQLException {
        //system.out.println("Accepting stakeholder status - objectXPeopleId: " + objectXPeopleId + ", currentUserId: " + currentUserId);
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            // First, verify that the stakeholder's ipid matches the current user ID
            String verifySql = "SELECT ipid FROM object_x_people WHERE id = ?";
            try (PreparedStatement verifyStmt = conn.prepareStatement(verifySql)) {
                verifyStmt.setInt(1, objectXPeopleId);
                try (ResultSet rs = verifyStmt.executeQuery()) {
                    if (!rs.next()) {
                        //system.out.println("Stakeholder not found with objectXPeopleId: " + objectXPeopleId);
                        return false;
                    }
                    int stakeholderUserId = rs.getInt("ipid");
                    if (stakeholderUserId != currentUserId) {
                        //system.out.println("Security violation: User " + currentUserId + " tried to update stakeholder for user " + stakeholderUserId);
                        throw new SQLException("Users can only update their own stakeholder status");
                    }
                }
            }

            // Get old data for audit trail
            StakeholderSnapshot oldData = getStakeholderInfoByOxpId(conn, objectXPeopleId);
            String userName = "System";
            
            conn.setAutoCommit(false);

            try {
                // Update AcceptedID from 2 to 1
                String updateSql = """
                    UPDATE object_x_people 
                    SET AcceptedID = 1, lastupdatedatetime = NOW(), lastupdateuser_id = ?
                    WHERE id = ? AND ipid = ?
                    """;
                
                int rowsAffected;
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setInt(1, currentUserId);
                    stmt.setInt(2, objectXPeopleId);
                    stmt.setInt(3, currentUserId);
                    
                    rowsAffected = stmt.executeUpdate();
                }

                if (rowsAffected == 0) {
                    throw new SQLException("No stakeholder updated with objectXPeopleId: " + objectXPeopleId);
                }

                // Insert audit trail for status acceptance
                if (oldData != null) {
                    insertStakeholderAudit(conn, regulationId, "Role Status", oldData.statusName, "Yes", "status", "Accepted", userName);
                }
                
                conn.commit();
                //system.out.println("Stakeholder status accepted successfully");
                return true;

            } catch (SQLException e) {
                System.err.println("Error accepting stakeholder status: " + e.getMessage());
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Create or update stakeholder from bulk upload
     * If stakeholder already exists for this regulation and user, update the role
     * Otherwise, create new stakeholder
     */
    public void createOrUpdateStakeholderFromBulk(int regulationId, int userId, int roleId, int currentUserId, String userName) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check if stakeholder already exists for this regulation and user
                String checkSql = """
                    SELECT oxp.ID 
                    FROM object_x_people oxp
                    JOIN regulation_x_objectxpeople rxop ON rxop.Object_x_ipid = oxp.ID
                    WHERE rxop.RegulationID = ? AND oxp.ipid = ?
                    LIMIT 1
                """;
                
                Integer existingOxpId = null;
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, regulationId);
                    checkStmt.setInt(2, userId);
                    try (ResultSet rs = checkStmt.executeQuery()) {
                        if (rs.next()) {
                            existingOxpId = rs.getInt("ID");
                        }
                    }
                }
                
                int objectXPeopleId;
                StakeholderSnapshot oldData = null;
                
                if (existingOxpId != null) {
                    // Update existing stakeholder
                    objectXPeopleId = existingOxpId;
                    oldData = getStakeholderInfoByOxpId(conn, objectXPeopleId);
                    
                    // Update role if changed
                    String updateSql = """
                        UPDATE object_x_people 
                        SET RoleID = ?, lastupdatedatetime = NOW(), lastupdateuser_id = ?
                        WHERE ID = ?
                    """;
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, roleId);
                        updateStmt.setInt(2, currentUserId);
                        updateStmt.setInt(3, objectXPeopleId);
                        updateStmt.executeUpdate();
                    }
                    
                    // Create audit records for update
                    StakeholderSnapshot newData = getStakeholderInfoByOxpId(conn, objectXPeopleId);
                    if (oldData != null && newData != null) {
                        if (!safeEquals(oldData.roleName, newData.roleName)) {
                            insertStakeholderAudit(conn, regulationId, "Role", oldData.roleName, newData.roleName, "edit", "Updated", userName);
                        }
                    }
                } else {
                    // Create new stakeholder
                    String insertSql = """
                        INSERT INTO object_x_people (isDelegateOF, ipid, RoleID, AcceptedID, statusID, lastupdatedatetime, lastupdateuser_id)
                        VALUES (NULL, ?, ?, 2, 1, NOW(), ?)
                    """;
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        insertStmt.setInt(1, userId);
                        insertStmt.setInt(2, roleId);
                        insertStmt.setInt(3, currentUserId);
                        insertStmt.executeUpdate();
                        
                        try (ResultSet generatedKeys = insertStmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                objectXPeopleId = generatedKeys.getInt(1);
                            } else {
                                throw new SQLException("Failed to get generated key for object_x_people");
                            }
                        }
                    }
                    
                    // Link to regulation
                    linkStakeholderToRegulation(conn, regulationId, objectXPeopleId);
                    
                    // Create audit records for new stakeholder
                    String fullName = getPersonFullName(conn, userId);
                    String roleName = getRoleNameForStakeholder(conn, roleId);
                    String statusName = "Active";
                    
                    if (fullName != null) {
                        insertStakeholderAudit(conn, regulationId, "Name", null, fullName, "link", "Added", userName);
                    }
                    if (roleName != null) {
                        insertStakeholderAudit(conn, regulationId, "Role", null, roleName, "link", "Added", userName);
                    }
                    insertStakeholderAudit(conn, regulationId, "Role Status", null, statusName, "link", "Added", userName);
                }
                
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Get regulation by RefNumber.
     * @param refNumber Reference number to search for
     * @return Regulation if found, null otherwise
     */
    public Regulation getRegulationByRefNumber(String refNumber) throws SQLException {
        return getRegulationByRefNumberExcludingId(refNumber, -1);
    }

    /**
     * Get regulation by PrimaryName.
     * @param primaryName Primary name to search for
     * @return Regulation if found, null otherwise
     */
    public Regulation getRegulationByPrimaryName(String primaryName) throws SQLException {
        if (primaryName == null || primaryName.trim().isEmpty()) {
            return null;
        }
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_PRIMARYNAME)) {
            pstmt.setString(1, primaryName.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulation(rs);
                }
            }
        }
        return null;
    }

    /**
     * Get regulation by RefNumber excluding a specific ID (for update validation)
     * @param refNumber Reference number to search for
     * @param excludeId ID to exclude from search
     * @return Regulation if found, null otherwise
     */
    public Regulation getRegulationByRefNumberExcludingId(String refNumber, int excludeId) throws SQLException {
        if (refNumber == null || refNumber.trim().isEmpty()) {
            return null;
        }
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(SELECT_BY_REFNUMBER_EXCLUDE_ID)) {

            pstmt.setString(1, refNumber.trim());
            pstmt.setInt(2, excludeId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToRegulation(rs);
                }
            }
        }
        return null;
    }

    /**
     * Check if RefNumber is unique for update (excluding current ID)
     * @param refNumber Reference number to check
     * @param excludeId ID to exclude from check
     * @return true if unique, false if duplicate exists
     */
    /**
     * Check if RefNumber is unique for update (excluding current ID).
     * Uses centralized RefNumberValidator for consistent validation across all facets.
     * Rule: Can repeat ref if it's the same object (excludeId), but cannot repeat for different objects.
     */
    public boolean isRefNumberUniqueForUpdate(String refNumber, int excludeId) throws SQLException {
        return com.example.budg_v2.util.RefNumberValidator.isRefNumberUniqueForUpdate("Regulation", refNumber, excludeId);
    }

}
