package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.model.Person;
import com.example.budg_v2.model.OrgUnit;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PeopleDAO {

    @Deprecated
    public List<Person> getAllPeople() throws SQLException {
        List<Person> people = new ArrayList<>();
        
        String sql = "SELECT\n" +
                    "  p.ID,\n" +
                    "  p.status_id,\n" +
                    "  s.primaryname AS status_name,\n" +
                    "  ou.Name AS org_unit_name,\n" +
                    "  p.First_Name AS first_name,\n" +
                    "  p.Last_Name AS last_name,\n" +
                    "  p.Email AS email,\n" +
                    "  NULL AS phone,\n" +
                    "  p.Description AS description,\n" +
                    "  p.Created_Date AS created_at,\n" +
                    "  p.Last_Updated AS updated_at\n" +
                    "FROM people p\n" +
                    "LEFT JOIN status s ON p.status_id = s.ID\n" +
                    "LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID\n" +
                    "ORDER BY p.Last_Name, p.First_Name";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                Person person = new Person();
                person.setId(rs.getInt("ID"));
                person.setStatusId(rs.getInt("status_id"));
                person.setStatusName(rs.getString("status_name"));
                person.setOrgUnitName(rs.getString("org_unit_name"));
                person.setFirstName(rs.getString("first_name"));
                person.setLastName(rs.getString("last_name"));
                person.setEmail(rs.getString("email"));
                person.setPhone(rs.getString("phone"));
                person.setDescription(rs.getString("description"));
                person.setCreatedAt(rs.getTimestamp("created_at"));
                person.setUpdatedAt(rs.getTimestamp("updated_at"));
                people.add(person);
            }
        }
        return people;
    }


    @Deprecated
    public List<Person> searchPeople(String query) throws SQLException {
        List<Person> people = new ArrayList<>();
        
        String sql = "SELECT\n" +
                    "  p.ID,\n" +
                    "  p.status_id,\n" +
                    "  s.primaryname AS status_name,\n" +
                    "  ou.Name AS org_unit_name,\n" +
                    "  p.First_Name AS first_name,\n" +
                    "  p.Last_Name AS last_name,\n" +
                    "  p.Email AS email,\n" +
                    "  NULL AS phone,\n" +
                    "  p.Description AS description,\n" +
                    "  p.Created_Date AS created_at,\n" +
                    "  p.Last_Updated AS updated_at\n" +
                    "FROM people p\n" +
                    "LEFT JOIN status s ON p.status_id = s.ID\n" +
                    "LEFT JOIN org_unit ou ON ou.ID = p.Org_Unit_ID\n" +
                    "WHERE p.First_Name LIKE ? OR p.Last_Name LIKE ? OR p.Email LIKE ? OR ou.Name LIKE ? OR s.primaryname LIKE ?\n" +
                    "ORDER BY p.Last_Name, p.First_Name";
        
        String searchPattern = "%" + query + "%";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, searchPattern);
            stmt.setString(2, searchPattern);
            stmt.setString(3, searchPattern);
            stmt.setString(4, searchPattern);
            stmt.setString(5, searchPattern);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Person person = new Person();
                    person.setId(rs.getInt("ID"));
                    person.setStatusId(rs.getInt("status_id"));
                    person.setStatusName(rs.getString("status_name"));
                    person.setOrgUnitName(rs.getString("org_unit_name"));
                    person.setFirstName(rs.getString("first_name"));
                    person.setLastName(rs.getString("last_name"));
                    person.setEmail(rs.getString("email"));
                    person.setPhone(rs.getString("phone"));
                    person.setDescription(rs.getString("description"));
                    person.setCreatedAt(rs.getTimestamp("created_at"));
                    person.setUpdatedAt(rs.getTimestamp("updated_at"));
                    people.add(person);
                }
            }
        }
        return people;
    }

    @Deprecated
    public List<OrgUnit> getOrgUnitsForPeople() throws SQLException {
        List<OrgUnit> orgUnits = new ArrayList<>();
        
        String sql = "SELECT DISTINCT\n" +
                    "  ou.ID,\n" +
                    "  ou.Reference,\n" +
                    "  ou.Name,\n" +
                    "  ou.Description,\n" +
                    "  ou.Parent_ID,\n" +
                    "  ou.status_id,\n" +
                    "  ou.Created_Date AS created_at,\n" +
                    "  ou.last_updated_date AS updated_at\n" +
                    "FROM org_unit ou\n" +
                    "INNER JOIN people p ON ou.ID = p.Org_Unit_ID\n" +
                    "ORDER BY ou.Name";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                OrgUnit orgUnit = new OrgUnit();
                orgUnit.setId(rs.getInt("ID"));
                orgUnit.setName(rs.getString("name"));
                orgUnit.setStatusId(rs.getInt("status_id"));
                orgUnit.setDescription(rs.getString("description"));
                orgUnit.setCreatedDate(rs.getTimestamp("created_at"));
                orgUnit.setLastUpdatedDate(rs.getTimestamp("updated_at"));
                orgUnits.add(orgUnit);
            }
        }
        return orgUnits;
    }

    public int createPerson(Person person, int userId) throws SQLException {
        String sql = "INSERT INTO people (status_id, Org_Unit_ID, First_Name, Last_Name, Email, Password, Description, Function_Name, Function_Description, System_Role, source_id, profile_ImageID, Created_Date, Last_Updated, lastupdateuser_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, person.getStatusId());
            // Use person.getOrgUnitId() instead of always null
            if (person.getOrgUnitId() != null) {
                stmt.setInt(2, person.getOrgUnitId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, person.getFirstName());
            stmt.setString(4, person.getLastName());
            stmt.setString(5, person.getEmail());
            stmt.setString(6, person.getPassword());
            stmt.setString(7, person.getDescription());
            stmt.setString(8, person.getFunctionName());
            stmt.setString(9, person.getFunctionDescription());
            
            // Handle system_role - can be null
            if (person.getSystemRole() != null) {
                stmt.setInt(10, person.getSystemRole());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }
            
            // Handle source_id - can be null
            if (person.getSourceId() != null) {
                stmt.setInt(11, person.getSourceId());
            } else {
                stmt.setNull(11, Types.INTEGER);
            }
            
            // Handle profile_ImageID - can be null
            if (person.getProfileImageId() != null) {
                stmt.setInt(12, person.getProfileImageId());
            } else {
                stmt.setNull(12, Types.INTEGER);
            }
            
            stmt.setInt(13, userId);
            
            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Creating person failed, no rows affected.");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int personId = generatedKeys.getInt(1);
                    
                    // Create people_details record if lifecycle or employment_type is provided
                    createPeopleDetails(personId, person, userId);
                    
                    return personId;
                } else {
                    throw new SQLException("Creating person failed, no ID obtained.");
                }
            }
        }
    }

    public boolean updatePerson(Person person, int userId) throws SQLException {
        String sql = "UPDATE people SET status_id = ?, Org_Unit_ID = ?, First_Name = ?, " +
                    "Last_Name = ?, Email = ?, Password = ?, Description = ?, Function_Name = ?, Function_Description = ?, System_Role = ?, source_id = ?, profile_ImageID = ?, Last_Updated = NOW(), lastupdateuser_id = ? " +
                    "WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, person.getStatusId());
            // Use person.getOrgUnitId() instead of always null
            if (person.getOrgUnitId() != null) {
                stmt.setInt(2, person.getOrgUnitId());
            } else {
                stmt.setNull(2, Types.INTEGER);
            }
            stmt.setString(3, person.getFirstName());
            stmt.setString(4, person.getLastName());
            stmt.setString(5, person.getEmail());
            stmt.setString(6, person.getPassword());
            stmt.setString(7, person.getDescription());
            stmt.setString(8, person.getFunctionName());
            stmt.setString(9, person.getFunctionDescription());
            
            // Handle system_role - can be null
            if (person.getSystemRole() != null) {
                stmt.setInt(10, person.getSystemRole());
            } else {
                stmt.setNull(10, Types.INTEGER);
            }
            
            // Handle source_id - can be null
            if (person.getSourceId() != null) {
                stmt.setInt(11, person.getSourceId());
            } else {
                stmt.setNull(11, Types.INTEGER);
            }
            
            // Handle profile_ImageID - can be null
            if (person.getProfileImageId() != null) {
                stmt.setInt(12, person.getProfileImageId());
            } else {
                stmt.setNull(12, Types.INTEGER);
            }
            
            stmt.setInt(13, userId);
            stmt.setInt(14, person.getId());
            
            int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Update people_details record (lifecycle/employment_type)
                updatePeopleDetails(person.getId(), person, userId);
            }
            
            return rowsAffected > 0;
        }
    }

    public boolean deletePerson(int id) throws SQLException {
        // Block deletion if person has raised a Running or Pending Start CR
        FacetChangesDAO facetChangesDAO = new FacetChangesDAO();
        if (facetChangesDAO.hasActiveCRsCreatedByPerson(id)) {
            throw new SQLException("Cannot delete person: This person has a Running or Pending Start change request. Please complete or cancel the change request first.");
        }
        
        String sql = "DELETE FROM people WHERE ID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    /* =========================
       people_details helpers
       ========================= */

    /** Create people_details record for lifecycle and employment_type (upsert behavior) */
    private void createPeopleDetails(int personId, Person person, int userId) throws SQLException {
        Integer lifecycleId = person.getLifecycleId();
        Integer employmentTypeId = person.getEmploymentTypeId();

        // Only create if at least one field is provided
        if (lifecycleId != null || employmentTypeId != null) {
            String sql = """
                INSERT INTO people_details (id, lifecycle, employment_type, last_updateuser_id, created_datetime, last_updatedtime)
                VALUES (?, ?, ?, ?, NOW(), NULL)
                ON DUPLICATE KEY UPDATE
                  lifecycle = VALUES(lifecycle),
                  employment_type = VALUES(employment_type),
                  last_updateuser_id = VALUES(last_updateuser_id),
                  last_updatedtime = NOW()
            """;

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, personId);

                if (lifecycleId != null) pstmt.setInt(2, lifecycleId);
                else pstmt.setNull(2, Types.INTEGER);

                if (employmentTypeId != null) pstmt.setInt(3, employmentTypeId);
                else pstmt.setNull(3, Types.INTEGER);

                pstmt.setInt(4, userId);
                pstmt.executeUpdate();
            }
        }
    }

    /** Update people_details record for lifecycle and employment_type */
    private void updatePeopleDetails(int personId, Person person, int userId) throws SQLException {
        Integer lifecycleId = person.getLifecycleId();
        Integer employmentTypeId = person.getEmploymentTypeId();

        String checkSql = "SELECT COUNT(*) FROM people_details WHERE id = ?";
        boolean recordExists = false;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
            checkStmt.setInt(1, personId);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    recordExists = rs.getInt(1) > 0;
                }
            }
        }

        if (recordExists) {
            String updateSql = """
                UPDATE people_details SET
                  lifecycle = ?, employment_type = ?, last_updateuser_id = ?, last_updatedtime = NOW()
                WHERE id = ?
            """;

            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(updateSql)) {

                if (lifecycleId != null) pstmt.setInt(1, lifecycleId);
                else pstmt.setNull(1, Types.INTEGER);

                if (employmentTypeId != null) pstmt.setInt(2, employmentTypeId);
                else pstmt.setNull(2, Types.INTEGER);

                pstmt.setInt(3, userId);
                pstmt.setInt(4, personId);
                pstmt.executeUpdate();
            }
        } else if (lifecycleId != null || employmentTypeId != null) {
            createPeopleDetails(personId, person, userId);
        }
    }

    /* =========================
       Audit Helper Methods
       ========================= */

    /**
     * Helper method to compare two values for equality (handles nulls)
     */
    @SuppressWarnings("unused")
    private boolean isEqual(Object obj1, Object obj2) {
        if (obj1 == null && obj2 == null) return true;
        if (obj1 == null || obj2 == null) return false;
        return obj1.equals(obj2);
    }

    /**
     * Get role name from role table by ID
     */
    @SuppressWarnings("unused")
    private String getRoleName(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("primaryname");
            }
        }
        return null;
    }

    /**
     * Get status name from status table by ID
     */
    @SuppressWarnings("unused")
    private String getStatusName(int statusId) throws SQLException {
        String sql = "SELECT primaryname FROM status WHERE ID = ?";
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
     * Get employment type name from employment_type table by ID
     */
    @SuppressWarnings("unused")
    private String getEmploymentTypeName(int typeId) throws SQLException {
        String sql = "SELECT PrimaryName FROM employment_type WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, typeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Get lifecycle name from people_lifecycle_status table by ID
     */
    private String getLifecycleName(int lifecycleId) throws SQLException {
        String sql = "SELECT PrimaryName FROM people_lifecycle_status WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, lifecycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("PrimaryName");
            }
        }
        return null;
    }

    /**
     * Get org unit name from org_unit table by ID
     */
    @SuppressWarnings("unused")
    private String getOrgUnitName(int orgUnitId) throws SQLException {
        String sql = "SELECT Name FROM org_unit WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orgUnitId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("Name");
            }
        }
        return null;
    }

    /* =========================
       Audit Record Creation Methods
       ========================= */

    /**
     * Create audit records for a new person (INSERT operation)
     * Records are saved in people_audit and people_details_audit tables
     */
    public void createPeopleAuditRecords(int personId, String userName) throws SQLException {
        //system.out.println("PeopleDAO: createPeopleAuditRecords called for personId: " + personId);
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Get person data
            String personSql = "SELECT * FROM people WHERE ID = ?";
            PreparedStatement personStmt = conn.prepareStatement(personSql);
            personStmt.setInt(1, personId);
            ResultSet personRs = personStmt.executeQuery();
            
            if (!personRs.next()) {
                throw new SQLException("Person not found with ID: " + personId);
            }
            
            // Insert into people_audit (rev is AUTO_INCREMENT)
            String auditSql = """
                INSERT INTO people_audit (
                    ID, First_Name, Last_Name, Description, Function_Name, Function_Description,
                    Org_Unit_ID, Email, Password, Created_Date, Last_Updated, ip_details,
                    System_Role, status_id, source_id, Deleted_date, last_User_LogIn,
                    Locale, profile_ImageID, lastupdateuser_id
                )
                SELECT 
                    ID, First_Name, Last_Name, Description, Function_Name, Function_Description,
                    Org_Unit_ID, Email, Password, Created_Date, Last_Updated, ip_details,
                    System_Role, status_id, source_id, Deleted_date, last_User_LogIn,
                    Locale, profile_ImageID, lastupdateuser_id
                FROM people 
                WHERE ID = ?
            """;
            
            PreparedStatement auditStmt = conn.prepareStatement(auditSql);
            auditStmt.setInt(1, personId);
            auditStmt.executeUpdate();
            //system.out.println("✅ People audit record created");
            
            // Check if people_details exists and insert into people_details_audit
            String detailsCheckSql = "SELECT * FROM people_details WHERE id = ?";
            PreparedStatement detailsCheckStmt = conn.prepareStatement(detailsCheckSql);
            detailsCheckStmt.setInt(1, personId);
            ResultSet detailsRs = detailsCheckStmt.executeQuery();
            
            if (detailsRs.next()) {
                // Insert into people_details_audit (rev is AUTO_INCREMENT)
                String detailsAuditSql = """
                    INSERT INTO people_details_audit (
                        id, employment_type, lifecycle, frequency, employed_since,
                        external_company_name, office_location, internal_mail_code,
                        other_info_description, office_telephone, mobile_telephone,
                        linkedIn_url, twitter_handle, other_url, lan_id,
                        created_datetime, last_updatedtime, deleted_datetime, last_updateuser_id
                    )
                    SELECT 
                        id, employment_type, lifecycle, frequency, employed_since,
                        external_company_name, office_location, internal_mail_code,
                        other_info_description, office_telephone, mobile_telephone,
                        linkedIn_url, twitter_handle, other_url, lan_id,
                        created_datetime, last_updatedtime, deleted_datetime, last_updateuser_id
                    FROM people_details 
                    WHERE id = ?
                """;
                
                PreparedStatement detailsAuditStmt = conn.prepareStatement(detailsAuditSql);
                detailsAuditStmt.setInt(1, personId);
                detailsAuditStmt.executeUpdate();
                //system.out.println("✅ People details audit record created");
            }
            
            conn.commit();
            //system.out.println("✅ Audit records committed successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ Error creating audit records: " + e.getMessage());
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
     * Create audit records for person update (UPDATE operation)
     * Compares old and new values and records changes
     */
    public void createPeopleUpdateAuditRecords(int personId, Person oldPerson, Person newPerson, String userName) throws SQLException {
        //system.out.println("PeopleDAO: createPeopleUpdateAuditRecords called for personId: " + personId);
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Insert snapshot into people_audit (rev is AUTO_INCREMENT)
            String auditSql = """
                INSERT INTO people_audit (
                    ID, First_Name, Last_Name, Description, Function_Name, Function_Description,
                    Org_Unit_ID, Email, Password, Created_Date, Last_Updated, ip_details,
                    System_Role, status_id, source_id, Deleted_date, last_User_LogIn,
                    Locale, profile_ImageID, lastupdateuser_id
                )
                SELECT 
                    ID, First_Name, Last_Name, Description, Function_Name, Function_Description,
                    Org_Unit_ID, Email, Password, Created_Date, Last_Updated, ip_details,
                    System_Role, status_id, source_id, Deleted_date, last_User_LogIn,
                    Locale, profile_ImageID, lastupdateuser_id
                FROM people 
                WHERE ID = ?
            """;
            
            PreparedStatement auditStmt = conn.prepareStatement(auditSql);
            auditStmt.setInt(1, personId);
            auditStmt.executeUpdate();
            //system.out.println("✅ People update audit record created");
            
            // Check if people_details exists and insert into people_details_audit
            String detailsCheckSql = "SELECT COUNT(*) FROM people_details WHERE id = ?";
            PreparedStatement detailsCheckStmt = conn.prepareStatement(detailsCheckSql);
            detailsCheckStmt.setInt(1, personId);
            ResultSet detailsRs = detailsCheckStmt.executeQuery();
            
            if (detailsRs.next() && detailsRs.getInt(1) > 0) {
                // Insert into people_details_audit (rev is AUTO_INCREMENT)
                String detailsAuditSql = """
                    INSERT INTO people_details_audit (
                        id, employment_type, lifecycle, frequency, employed_since,
                        external_company_name, office_location, internal_mail_code,
                        other_info_description, office_telephone, mobile_telephone,
                        linkedIn_url, twitter_handle, other_url, lan_id,
                        created_datetime, last_updatedtime, deleted_datetime, last_updateuser_id
                    )
                    SELECT 
                        id, employment_type, lifecycle, frequency, employed_since,
                        external_company_name, office_location, internal_mail_code,
                        other_info_description, office_telephone, mobile_telephone,
                        linkedIn_url, twitter_handle, other_url, lan_id,
                        created_datetime, last_updatedtime, deleted_datetime, last_updateuser_id
                    FROM people_details 
                    WHERE id = ?
                """;
                
                PreparedStatement detailsAuditStmt = conn.prepareStatement(detailsAuditSql);
                detailsAuditStmt.setInt(1, personId);
                detailsAuditStmt.executeUpdate();
                //system.out.println("✅ People details update audit record created");
            }
            
            conn.commit();
            //system.out.println("✅ Update audit records committed successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ Error creating update audit records: " + e.getMessage());
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
     * Create audit record for person deletion (DELETE operation)
     * Records the state before deletion
     */
    public void createPeopleDeleteAuditRecord(int personId, String userName) throws SQLException {
        //system.out.println("PeopleDAO: createPeopleDeleteAuditRecord called for personId: " + personId);
        Connection conn = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Start transaction
            
            // Insert snapshot into people_audit before deletion (rev is AUTO_INCREMENT)
            String auditSql = """
                INSERT INTO people_audit (
                    ID, First_Name, Last_Name, Description, Function_Name, Function_Description,
                    Org_Unit_ID, Email, Password, Created_Date, Last_Updated, ip_details,
                    System_Role, status_id, source_id, Deleted_date, last_User_LogIn,
                    Locale, profile_ImageID, lastupdateuser_id
                )
                SELECT 
                    ID, First_Name, Last_Name, Description, Function_Name, Function_Description,
                    Org_Unit_ID, Email, Password, Created_Date, NOW(), ip_details,
                    System_Role, status_id, source_id, NOW(), last_User_LogIn,
                    Locale, profile_ImageID, lastupdateuser_id
                FROM people 
                WHERE ID = ?
            """;
            
            PreparedStatement auditStmt = conn.prepareStatement(auditSql);
            auditStmt.setInt(1, personId);
            auditStmt.executeUpdate();
            //system.out.println("✅ People delete audit record created");
            
            // Check if people_details exists and insert into people_details_audit
            String detailsCheckSql = "SELECT COUNT(*) FROM people_details WHERE id = ?";
            PreparedStatement detailsCheckStmt = conn.prepareStatement(detailsCheckSql);
            detailsCheckStmt.setInt(1, personId);
            ResultSet detailsRs = detailsCheckStmt.executeQuery();
            
            if (detailsRs.next() && detailsRs.getInt(1) > 0) {
                // Insert into people_details_audit (rev is AUTO_INCREMENT)
                String detailsAuditSql = """
                    INSERT INTO people_details_audit (
                        id, employment_type, lifecycle, frequency, employed_since,
                        external_company_name, office_location, internal_mail_code,
                        other_info_description, office_telephone, mobile_telephone,
                        linkedIn_url, twitter_handle, other_url, lan_id,
                        created_datetime, last_updatedtime, deleted_datetime, last_updateuser_id
                    )
                    SELECT 
                        id, employment_type, lifecycle, frequency, employed_since,
                        external_company_name, office_location, internal_mail_code,
                        other_info_description, office_telephone, mobile_telephone,
                        linkedIn_url, twitter_handle, other_url, lan_id,
                        created_datetime, last_updatedtime, NOW(), last_updateuser_id
                    FROM people_details 
                    WHERE id = ?
                """;
                
                PreparedStatement detailsAuditStmt = conn.prepareStatement(detailsAuditSql);
                detailsAuditStmt.setInt(1, personId);
                detailsAuditStmt.executeUpdate();
                //system.out.println("✅ People details delete audit record created");
            }
            
            conn.commit();
            //system.out.println("✅ Delete audit records committed successfully");
            
        } catch (SQLException e) {
            System.err.println("❌ Error creating delete audit record: " + e.getMessage());
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
}
