package com.example.budg_v2.service;

import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.util.AppRoleNames;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeopleService {

    private static final Logger logger = LoggerFactory.getLogger(PeopleService.class);

    /* =========================
       Public API
       ========================= */

    public List<Map<String, Object>> getAllPeople() throws SQLException {
        String sql = """
            SELECT p.*, 
                   s.primaryname AS status_name,
                   ou.Name AS org_unit_name,
				   r.primaryname AS system_role_name,
                   pd.office_telephone AS office_telephone,
                   pd.mobile_telephone AS mobile_telephone
            FROM people p
            LEFT JOIN status s ON p.status_id = s.ID
            LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
			LEFT JOIN role r ON p.System_Role = r.id
            LEFT JOIN people_details pd ON p.ip_details = pd.id
            ORDER BY p.Last_Name, p.First_Name
        """;
        return executePeopleQuery(sql);
    }

    public Map<String, Object> getPersonById(int id) throws SQLException {
        String sql = """
            SELECT
                   p.*,
                   s.primaryname AS status_name,
                   pd.lifecycle AS lifecycle_id,
                   pd.employment_type AS employment_type_id,
                   ls.primary_Name AS lifecycle_name,
                   et.primary_Name AS employment_type_name,
                   r.primaryname AS system_role_name,
                   ou.Name AS org_unit_name,
                   pd.office_telephone AS office_telephone,
                   pd.mobile_telephone AS mobile_telephone,
                   pd.external_company_name AS company_name,
                   pd.office_location AS office_location,
                   pd.internal_mail_code AS internal_mail_code,
                   pd.linkedIn_url AS linkedin_url,
                   pd.twitter_handle AS twitter,
                   pd.lan_id AS lan_id,
                   pd.employed_since AS employed_since
            FROM people p
            LEFT JOIN status s                   ON p.status_id = s.ID
            LEFT JOIN people_details pd          ON p.ip_details = pd.id
            LEFT JOIN people_lifecycle_status ls ON pd.lifecycle = ls.ID
            LEFT JOIN employment_type et         ON pd.employment_type = et.id
            LEFT JOIN role r                     ON p.System_Role = r.id
            LEFT JOIN org_unit ou                ON p.Org_Unit_ID = ou.ID
            WHERE p.ID = ?
        """;
        List<Map<String, Object>> results = executePeopleQuery(sql, id);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Map<String, Object>> getPeopleByOrgUnitId(int orgUnitId) throws SQLException {
        String sql = """
            SELECT p.*, s.primaryname AS status_name
            FROM people p
            LEFT JOIN status s ON p.status_id = s.ID
            WHERE p.Org_Unit_ID = ?
            ORDER BY p.Last_Name, p.First_Name
        """;
        return executePeopleQuery(sql, orgUnitId);
    }

    public List<Map<String, Object>> searchPeople(String searchQuery) throws SQLException {
        String sql = """
            SELECT p.*, 
                   s.primaryname AS status_name,
                   ou.Name AS org_unit_name,
				   r.primaryname AS system_role_name,
                   pd.office_telephone AS office_telephone,
                   pd.mobile_telephone AS mobile_telephone
            FROM people p
            LEFT JOIN status s ON p.status_id = s.ID
            LEFT JOIN org_unit ou ON p.Org_Unit_ID = ou.ID
			LEFT JOIN role r ON p.System_Role = r.id
            LEFT JOIN people_details pd ON p.ip_details = pd.id
            WHERE p.First_Name LIKE ? OR p.Last_Name LIKE ? OR p.Email LIKE ? OR ou.Name LIKE ?
            ORDER BY p.ID
        """;
        String pattern = "%" + searchQuery + "%";
        return executePeopleQuery(sql, pattern, pattern, pattern, pattern);
    }

    public List<Map<String, Object>> getOrgUnitsForPeople() throws SQLException {
        // Your schema uses "org_unit" here (not "org_units"); keeping as-is.
        String sql = "SELECT ID, Name, Description FROM org_unit ORDER BY Name";
        List<Map<String, Object>> orgUnits = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Map<String, Object> orgUnit = new HashMap<>();
                orgUnit.put("id", rs.getInt("ID"));
                orgUnit.put("name", rs.getString("Name"));
                orgUnit.put("description", rs.getString("Description"));
                orgUnits.add(orgUnit);
            }
        }

        return orgUnits;
    }

    public Map<String, Object> createPerson(Map<String, Object> personData, int userId) throws SQLException {
        // Start transaction
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try {
                // Step 1: Create people_details record first with default values
                int detailsId = createPeopleDetailsFirst(personData, userId);
                
                // Step 2: Create people record with ip_details pointing to people_details
        String sql = """
            INSERT INTO people
                    (First_Name, Last_Name, Email, Password, Description, Function_Name, Function_Description, Org_Unit_ID, status_id, System_Role, source_id, profile_ImageID, ip_details, lastupdateuser_id, external_id, auth_source, last_synced_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

                try (PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    setPersonParametersWithDetails(pstmt, personData, detailsId);
                    // position 14 = lastupdateuser_id
                    pstmt.setInt(14, userId);
                    // position 15 = external_id
                    pstmt.setString(15, (String) personData.get("external_id"));
                    // position 16 = auth_source
                    String authSource = (String) personData.get("auth_source");
                    if (authSource != null) {
                        pstmt.setString(16, authSource);
                    } else {
                        pstmt.setNull(16, java.sql.Types.VARCHAR);
                    }
                    // position 17 = last_synced_at
                    java.sql.Timestamp lastSyncedAt = (java.sql.Timestamp) personData.get("last_synced_at");
                    if (lastSyncedAt != null) {
                        pstmt.setTimestamp(17, lastSyncedAt);
                    } else {
                        pstmt.setNull(17, java.sql.Types.TIMESTAMP);
                    }

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) throw new SQLException("Creating person failed, no rows affected.");

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int personId = generatedKeys.getInt(1);
                    personData.put("id", personId);

                            // Commit transaction
                            conn.commit();
                    return personData;
                } else {
                    throw new SQLException("Creating person failed, no ID obtained.");
                }
            }
        }
            } catch (Exception e) {
                // Rollback transaction on error
                conn.rollback();
                throw e;
            }
        }
    }

    public Map<String, Object> updatePerson(Map<String, Object> personData, int userId) throws SQLException {
        //system.out.println("=== PEOPLE SERVICE updatePerson CALLED ===");
        //system.out.println("Person Data: " + personData);
        //system.out.println("User ID: " + userId);
        
        Integer personId = (Integer) personData.get("id");
        if (personId == null) {
            //system.out.println("ERROR: Person ID is null!");
            throw new SQLException("Person ID is required for update");
        }
        
        //system.out.println("Person ID: " + personId);
        
        // Check if Super Admin is trying to downgrade their own role
        Object newRoleId = personData.get("system_role");
        if (newRoleId != null && personId.equals(userId)) {
            // User is updating their own profile
            // Check if current user is Super Admin and trying to downgrade their role
            try {
                boolean isSuperAdmin = SegmentAccessService.isSuperAdmin(userId);
                if (isSuperAdmin) {
                    // Get the role name for the new role
                    Integer newRole = null;
                    if (newRoleId instanceof Integer) {
                        newRole = (Integer) newRoleId;
                    } else if (newRoleId instanceof String) {
                        try {
                            newRole = Integer.parseInt((String) newRoleId);
                        } catch (NumberFormatException e) {
                            // Not a valid integer
                        }
                    }
                    
                    if (newRole != null) {
                        String newRoleName = getRoleNameById(newRole);
                        // If the new role is not "Super Admin", block the update
                        if (newRoleName != null && !AppRoleNames.isSuperAdminName(newRoleName)) {
                            throw new SQLException("Super Admins cannot change their own role. Another Super Admin must change your role.");
                        }
                    }
                }
            } catch (SQLException e) {
                if (e.getMessage().contains("Super Admins cannot change")) {
                    throw e; // Re-throw the blocking exception
                }
                // Otherwise, log and continue
                logger.warn("Error checking Super Admin status: " + e.getMessage());
            }
        }

        // Apply field-level restrictions when user is editing their own profile
        if (personId.equals(userId)) {
            try {
                String userRole = SegmentAccessService.getUserRole(userId);
                if (userRole != null) {
                    boolean isSuperAdmin = AppRoleNames.isSuperAdminName(userRole);
                    boolean isAdmin = AppRoleNames.isAdminOnlyName(userRole);
                    
                    if (!isSuperAdmin) {
                        // Admins and Web Users: remove system_role, org_unit_id, and employed_since
                        personData.remove("system_role");
                        personData.remove("org_unit_id");
                        personData.remove("employed_since");
                        
                        if (!isAdmin) {
                            // Web Users only: also remove status_id
                            personData.remove("status_id");
                        }
                    }
                }
            } catch (SQLException e) {
                logger.warn("Error getting user role for field restrictions: " + e.getMessage());
                // Continue without restrictions if we can't determine role
            }
        }

        // Start transaction
        try (Connection conn = DatabaseConnection.getConnection()) {
            //system.out.println("Database connection established, starting transaction");
            conn.setAutoCommit(false);
            
            try {
                // Step 1: Get current ip_details from people table
                Integer currentDetailsId = getCurrentDetailsId(conn, personId);
                //system.out.println("Current ip_details ID: " + currentDetailsId);
                
                // Step 2: Update people table
                boolean peopleUpdated = updatePeopleTable(conn, personData, userId);
                if (!peopleUpdated) {
                    throw new SQLException("Failed to update people table");
                }
                
                // Step 3: Update or create people_details record
                Integer finalDetailsId = updateOrCreatePeopleDetails(conn, personId, currentDetailsId, personData, userId);
                
                // Step 4: Update people.ip_details if needed
                if (finalDetailsId != null && !finalDetailsId.equals(currentDetailsId)) {
                    updatePeopleIpDetails(conn, personId, finalDetailsId, userId);
                }
                
                // Step 5: Commit transaction
                conn.commit();
                
                // Step 6: Return merged response (people + people_details)
                //system.out.println("=== Getting merged person data for ID: " + personId + " ===");
                Map<String, Object> mergedData = getMergedPersonData(personId);
                //system.out.println("=== Merged data retrieved: " + (mergedData != null ? "SUCCESS" : "NULL") + " ===");
                return mergedData;
                
            } catch (Exception e) {
                // Rollback transaction on error
                //system.out.println("=== ERROR in updatePerson: " + e.getMessage() + " ===");
                e.printStackTrace();
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean deletePerson(int id) throws SQLException {
        // Block deletion if person has raised a Running or Pending Start CR
        com.example.budg_v2.dao.FacetChangesDAO facetChangesDAO = new com.example.budg_v2.dao.FacetChangesDAO();
        if (facetChangesDAO.hasActiveCRsCreatedByPerson(id)) {
            throw new SQLException("Cannot delete person: This person has a Running or Pending Start change request. Please complete or cancel the change request first.");
        }
        
        String sql = "DELETE FROM people WHERE ID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        }
    }
    
    /**
     * Get role name by role ID
     */
    private String getRoleNameById(int roleId) throws SQLException {
        String sql = "SELECT primaryname FROM role WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, roleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("primaryname");
                }
            }
        }
        return null;
    }

    /* =========================
       Internal helpers
       ========================= */

    /** Get current ip_details ID from people table */
    private Integer getCurrentDetailsId(Connection conn, int personId) throws SQLException {
        String sql = "SELECT ip_details FROM people WHERE ID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, personId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("ip_details", Integer.class);
                }
            }
        }
        return null;
    }

    /** Update people table with basic person information */
    private boolean updatePeopleTable(Connection conn, Map<String, Object> personData, int userId) throws SQLException {
        Integer personId = (Integer) personData.get("id");
        if (personId == null) {
            throw new SQLException("Person ID is required for update");
        }
        
        // Check if user is LDAP user - protect Org_Unit_ID from manual updates
        String currentAuthSource = getCurrentAuthSource(conn, personId);
        boolean isLdapUser = "LDAP".equals(currentAuthSource);
        
        // If LDAP user and trying to update Org_Unit_ID, skip it (controlled by LDAP Sync only)
        Integer newOrgUnitId = (Integer) personData.get("org_unit_id");
        if (isLdapUser && newOrgUnitId != null) {
            // Check if this is coming from LDAP Sync (has external_id or last_synced_at in update)
            // If not from LDAP Sync, block the Org_Unit_ID update
            boolean isFromLdapSync = personData.containsKey("external_id") || 
                                     personData.containsKey("last_synced_at");
            
            if (!isFromLdapSync) {
                // Manual update attempt - log warning and remove Org_Unit_ID from update
                logger.warn("Attempted manual OrgUnit update for LDAP user (ID: {}). " +
                           "OrgUnit is controlled by LDAP Sync only. Update blocked.", personId);
                personData = new HashMap<>(personData); // Create copy to avoid modifying original
                personData.remove("org_unit_id"); // Remove from update
            }
        }
        
        String sql = """
            UPDATE people SET
              First_Name = ?,
              Last_Name = ?,
              Email = ?,
              Password = COALESCE(NULLIF(?, ''), Password),
              Description = ?,
              Function_Name = ?,
              Function_Description = ?,
              Org_Unit_ID = ?,
              status_id = ?,
              System_Role = ?,
              source_id = ?,
              profile_ImageID = ?,
              lastupdateuser_id = ?,
              external_id = ?,
              auth_source = ?,
              last_synced_at = ?,
              Last_Updated = NOW()
            WHERE ID = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setUpdatePersonParameters(pstmt, personData, userId);
            int rowsAffected = pstmt.executeUpdate();
            //system.out.println("People table update - rows affected: " + rowsAffected);
            return rowsAffected > 0;
        }
    }
    
    /**
     * Get current auth_source for a person
     */
    private String getCurrentAuthSource(Connection conn, int personId) throws SQLException {
        String sql = "SELECT auth_source FROM people WHERE ID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, personId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("auth_source");
                }
            }
        }
        return "LOCAL"; // Default if not found
    }

    /** Update existing people_details or create new one if needed */
    private Integer updateOrCreatePeopleDetails(Connection conn, int personId, Integer currentDetailsId, 
                                               Map<String, Object> personData, int userId) throws SQLException {
        
        // Debug: Log all personData keys and values
        //system.out.println("=== updateOrCreatePeopleDetails DEBUG ===");
        //system.out.println("Person ID: " + personId);
        //system.out.println("Current Details ID: " + currentDetailsId);
        //system.out.println("Person Data Keys: " + personData.keySet());
        // Check if we have any people_details fields to update
        boolean hasDetailsData = hasPeopleDetailsData(personData);
        //system.out.println("Has people_details data: " + hasDetailsData);
        if (!hasDetailsData) {
            //system.out.println("No people_details data to update");
            return currentDetailsId;
        }

        if (currentDetailsId != null) {
            // Update existing people_details record
            //system.out.println("Updating existing people_details record: " + currentDetailsId);
            updateExistingPeopleDetails(conn, currentDetailsId, personData, userId);
            return currentDetailsId;
        } else {
            // Create new people_details record
            //system.out.println("Creating new people_details record");
            return createNewPeopleDetails(conn, personData, userId);
        }
    }

    /** Check if personData contains any people_details fields */
    private boolean hasPeopleDetailsData(Map<String, Object> personData) {
        // Check if fields exist (they are being updated even if empty)
        boolean hasLifecycle = personData.containsKey("lifecycle_id");
        boolean hasEmploymentType = personData.containsKey("employment_type_id");
        boolean hasCompanyName = personData.containsKey("company_name");
        boolean hasOfficeLocation = personData.containsKey("office_location");
        boolean hasInternalMailCode = personData.containsKey("internal_mail_code");
        boolean hasOfficePhone = personData.containsKey("office_phone");
        boolean hasMobilePhone = personData.containsKey("mobile_phone");
        boolean hasLinkedinUrl = personData.containsKey("linkedin_url");
        boolean hasTwitter = personData.containsKey("twitter");
        boolean hasLanId = personData.containsKey("lan_id");
        boolean hasEmployedSince = personData.containsKey("employed_since");
        
        //system.out.println("=== hasPeopleDetailsData DEBUG ===");
        //system.out.println("lifecycle_id: " + hasLifecycle + " (value: " + personData.get("lifecycle_id") + ")");
        //system.out.println("employment_type_id: " + hasEmploymentType + " (value: " + personData.get("employment_type_id") + ")");
        //system.out.println("company_name: " + hasCompanyName + " (value: " + personData.get("company_name") + ")");
        //system.out.println("office_location: " + hasOfficeLocation + " (value: " + personData.get("office_location") + ")");
        //system.out.println("internal_mail_code: " + hasInternalMailCode + " (value: " + personData.get("internal_mail_code") + ")");
        //system.out.println("office_phone: " + hasOfficePhone + " (value: " + personData.get("office_phone") + ")");
        //system.out.println("mobile_phone: " + hasMobilePhone + " (value: " + personData.get("mobile_phone") + ")");
        //system.out.println("linkedin_url: " + hasLinkedinUrl + " (value: " + personData.get("linkedin_url") + ")");
        //system.out.println("twitter: " + hasTwitter + " (value: " + personData.get("twitter") + ")");
        //system.out.println("lan_id: " + hasLanId + " (value: " + personData.get("lan_id") + ")");
        //system.out.println("employed_since: " + hasEmployedSince + " (value: " + personData.get("employed_since") + ")");
        
        boolean result = hasLifecycle || hasEmploymentType || hasCompanyName || hasOfficeLocation ||
                        hasInternalMailCode || hasOfficePhone || hasMobilePhone || hasLinkedinUrl ||
                        hasTwitter || hasLanId || hasEmployedSince;
        
        //system.out.println("Final result: " + result);
        return result;
    }

    /** Update existing people_details record */
    private void updateExistingPeopleDetails(Connection conn, int detailsId, Map<String, Object> personData, int userId) throws SQLException {
        String sql = """
            UPDATE people_details SET
              lifecycle = ?, employment_type = ?, external_company_name = ?, 
              office_location = ?, internal_mail_code = ?, office_telephone = ?,
              mobile_telephone = ?, linkedIn_url = ?, twitter_handle = ?, 
              lan_id = ?, employed_since = ?, last_updateuser_id = ?, last_updatedtime = NOW()
            WHERE id = ?
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            
            Integer lifecycleId = (Integer) personData.get("lifecycle_id");
            if (lifecycleId != null) pstmt.setInt(paramIndex++, lifecycleId);
            else pstmt.setNull(paramIndex++, java.sql.Types.INTEGER);

            Integer employmentTypeId = (Integer) personData.get("employment_type_id");
            if (employmentTypeId != null) pstmt.setInt(paramIndex++, employmentTypeId);
            else pstmt.setNull(paramIndex++, java.sql.Types.INTEGER);

            pstmt.setString(paramIndex++, (String) personData.get("company_name"));
            pstmt.setString(paramIndex++, (String) personData.get("office_location"));
            pstmt.setString(paramIndex++, (String) personData.get("internal_mail_code"));
            pstmt.setString(paramIndex++, (String) personData.get("office_phone"));
            pstmt.setString(paramIndex++, (String) personData.get("mobile_phone"));
            pstmt.setString(paramIndex++, (String) personData.get("linkedin_url"));
            pstmt.setString(paramIndex++, (String) personData.get("twitter"));
            pstmt.setString(paramIndex++, (String) personData.get("lan_id"));
            
            // Handle employed_since - convert empty string to null for datetime field
            String employedSince = (String) personData.get("employed_since");
            if (employedSince != null && !employedSince.trim().isEmpty()) {
                pstmt.setString(paramIndex++, employedSince);
            } else {
                pstmt.setNull(paramIndex++, java.sql.Types.TIMESTAMP);
            }
            
            pstmt.setInt(paramIndex++, userId);
            pstmt.setInt(paramIndex++, detailsId);

            pstmt.executeUpdate();
        }
    }

    /** Create new people_details record */
    private Integer createNewPeopleDetails(Connection conn, Map<String, Object> personData, int userId) throws SQLException {
        String sql = """
            INSERT INTO people_details (lifecycle, employment_type, external_company_name, 
              office_location, internal_mail_code, office_telephone, mobile_telephone, 
              linkedIn_url, twitter_handle, lan_id, employed_since, last_updateuser_id, 
              created_datetime, last_updatedtime)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            int paramIndex = 1;
            
            Integer lifecycleId = (Integer) personData.get("lifecycle_id");
            if (lifecycleId != null) pstmt.setInt(paramIndex++, lifecycleId);
            else pstmt.setNull(paramIndex++, java.sql.Types.INTEGER);

            Integer employmentTypeId = (Integer) personData.get("employment_type_id");
            if (employmentTypeId != null) pstmt.setInt(paramIndex++, employmentTypeId);
            else pstmt.setNull(paramIndex++, java.sql.Types.INTEGER);

            pstmt.setString(paramIndex++, (String) personData.get("company_name"));
            pstmt.setString(paramIndex++, (String) personData.get("office_location"));
            pstmt.setString(paramIndex++, (String) personData.get("internal_mail_code"));
            pstmt.setString(paramIndex++, (String) personData.get("office_phone"));
            pstmt.setString(paramIndex++, (String) personData.get("mobile_phone"));
            pstmt.setString(paramIndex++, (String) personData.get("linkedin_url"));
            pstmt.setString(paramIndex++, (String) personData.get("twitter"));
            pstmt.setString(paramIndex++, (String) personData.get("lan_id"));
            
            // Handle employed_since - convert empty string to null for datetime field
            String employedSince = (String) personData.get("employed_since");
            if (employedSince != null && !employedSince.trim().isEmpty()) {
                pstmt.setString(paramIndex++, employedSince);
            } else {
                pstmt.setNull(paramIndex++, java.sql.Types.TIMESTAMP);
            }
            
            pstmt.setInt(paramIndex++, userId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Creating people_details failed, no rows affected.");
            }

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newDetailsId = generatedKeys.getInt(1);
                    //system.out.println("Created new people_details with ID: " + newDetailsId);
                    return newDetailsId;
                } else {
                    throw new SQLException("Creating people_details failed, no ID obtained.");
                }
            }
        }
    }

    /** Update people.ip_details field */
    private void updatePeopleIpDetails(Connection conn, int personId, int detailsId, int userId) throws SQLException {
        String sql = "UPDATE people SET ip_details = ?, lastupdateuser_id = ? WHERE ID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, detailsId);
            pstmt.setInt(2, userId);
            pstmt.setInt(3, personId);
            pstmt.executeUpdate();
        }
    }

    /** Get merged person data (people + people_details) */
    private Map<String, Object> getMergedPersonData(int personId) throws SQLException {
        // Use the existing getPersonById method which already merges the data
        return getPersonById(personId);
    }

    private List<Map<String, Object>> executePeopleQuery(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> people = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                Set<String> columnLabels = new HashSet<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    columnLabels.add(metaData.getColumnLabel(i).toLowerCase(Locale.ROOT));
                }

                while (rs.next()) {
                    Map<String, Object> person = new HashMap<>();
                    person.put("id", rs.getInt("ID"));
                    person.put("first_name", rs.getString("First_Name"));
                    person.put("last_name", rs.getString("Last_Name"));
                    person.put("email", rs.getString("Email"));
                    person.put("password", rs.getString("Password"));
                    person.put("description", rs.getString("Description"));
                    person.put("function_name", rs.getString("Function_Name"));
                    person.put("function_description", rs.getString("Function_Description"));
                    person.put("org_unit_id", rs.getInt("Org_Unit_ID"));
                    person.put("status_id", rs.getInt("status_id"));
					person.put("status_name", rs.getString("status_name"));
					// No profile_name in API; use system_role_name directly where needed by clients
                    person.put("created_date", rs.getTimestamp("Created_Date"));
                    person.put("last_updated", rs.getTimestamp("Last_Updated"));
                    person.put("lastupdateuser_id", rs.getInt("lastupdateuser_id"));
                    person.put("last_user_login", rs.getTimestamp("last_User_LogIn"));

                    if (hasColumn(columnLabels, "system_role")) {
                        Number systemRoleValue = (Number) rs.getObject("System_Role");
                        Integer systemRole = systemRoleValue != null ? systemRoleValue.intValue() : null;
                        person.put("system_role", systemRole);
                        person.put("System_Role", systemRole);
                    }

                    if (hasColumn(columnLabels, "system_role_name")) {
                        String roleName = rs.getString("system_role_name");
                        person.put("system_role_name", roleName);
                        person.put("System_Role_Name", roleName);
                    }

                    if (hasColumn(columnLabels, "lifecycle_id")) {
                        Number lifecycleValue = (Number) rs.getObject("lifecycle_id");
                        Integer lifecycleId = lifecycleValue != null ? lifecycleValue.intValue() : null;
                        person.put("lifecycle_id", lifecycleId);
                    }

                    if (hasColumn(columnLabels, "lifecycle_name")) {
                        person.put("lifecycle_name", rs.getString("lifecycle_name"));
                    }

                    if (hasColumn(columnLabels, "employment_type_id")) {
                        Number employmentValue = (Number) rs.getObject("employment_type_id");
                        Integer employmentTypeId = employmentValue != null ? employmentValue.intValue() : null;
                        person.put("employment_type_id", employmentTypeId);
                    }

                    if (hasColumn(columnLabels, "employment_type_name")) {
                        person.put("employment_type_name", rs.getString("employment_type_name"));
                    }

                    if (hasColumn(columnLabels, "org_unit_name")) {
                        person.put("org_unit_name", rs.getString("org_unit_name"));
                    }

                    if (hasColumn(columnLabels, "office_telephone")) {
                        person.put("office_telephone", rs.getString("office_telephone"));
                    }

                    if (hasColumn(columnLabels, "mobile_telephone")) {
                        person.put("mobile_telephone", rs.getString("mobile_telephone"));
                    }

                    if (hasColumn(columnLabels, "company_name")) {
                        person.put("company_name", rs.getString("company_name"));
                    }

                    if (hasColumn(columnLabels, "office_location")) {
                        person.put("office_location", rs.getString("office_location"));
                    }

                    if (hasColumn(columnLabels, "internal_mail_code")) {
                        person.put("internal_mail_code", rs.getString("internal_mail_code"));
                    }

                    if (hasColumn(columnLabels, "linkedin_url")) {
                        person.put("linkedin_url", rs.getString("linkedin_url"));
                    }

                    if (hasColumn(columnLabels, "twitter")) {
                        person.put("twitter", rs.getString("twitter"));
                    }

                    if (hasColumn(columnLabels, "lan_id")) {
                        person.put("lan_id", rs.getString("lan_id"));
                    }

                    if (hasColumn(columnLabels, "employed_since")) {
                        person.put("employed_since", rs.getString("employed_since"));
                    }

                    people.add(person);
                }
            }
        }
        return people;
    }

    private boolean hasColumn(Set<String> columns, String columnName) {
        return columns.contains(columnName.toLowerCase(Locale.ROOT));
    }
    private void setPersonParametersWithDetails(PreparedStatement pstmt, Map<String, Object> personData, int detailsId) throws SQLException {
        pstmt.setString(1,  (String) personData.get("first_name"));
        pstmt.setString(2,  (String) personData.get("last_name"));
        pstmt.setString(3,  (String) personData.get("email"));
        pstmt.setString(4,  (String) personData.get("password"));
        pstmt.setString(5,  (String) personData.get("description"));
        pstmt.setString(6,  (String) personData.get("function_name"));
        pstmt.setString(7,  (String) personData.get("function_description"));

        Integer orgUnitId = (Integer) personData.get("org_unit_id");
        if (orgUnitId != null) pstmt.setInt(8, orgUnitId); else pstmt.setNull(8, java.sql.Types.INTEGER);

        Integer statusId = (Integer) personData.get("status_id");
        if (statusId != null) pstmt.setInt(9, statusId); else pstmt.setNull(9, java.sql.Types.INTEGER);

        Integer systemRole = (Integer) personData.get("system_role");
        if (systemRole != null) pstmt.setInt(10, systemRole); else pstmt.setNull(10, java.sql.Types.INTEGER);

        Integer sourceId = (Integer) personData.get("source_id");
        if (sourceId != null) pstmt.setInt(11, sourceId); else pstmt.setNull(11, java.sql.Types.INTEGER);

        Integer profileImageId = (Integer) personData.get("profile_ImageID");
        if (profileImageId != null) pstmt.setInt(12, profileImageId); else pstmt.setNull(12, java.sql.Types.INTEGER);
        
        // Set ip_details to the people_details ID
        pstmt.setInt(13, detailsId);
    }
    private void setUpdatePersonParameters(PreparedStatement pstmt, Map<String, Object> personData, int userId) throws SQLException {
        //system.out.println("=== setUpdatePersonParameters called ===");
        //system.out.println("Person data keys: " + personData.keySet());
        //system.out.println("User ID: " + userId);
        
        // 1..3
        pstmt.setString(1,  (String) personData.get("first_name"));
        pstmt.setString(2,  (String) personData.get("last_name"));
        pstmt.setString(3,  (String) personData.get("email"));

        // 4 - Password: "" keeps old password due to COALESCE(NULLIF(?, ''), Password)
        pstmt.setString(4,  (String) personData.get("password"));

        // 5..7
        pstmt.setString(5,  (String) personData.get("description"));
        pstmt.setString(6,  (String) personData.get("function_name"));
        pstmt.setString(7,  (String) personData.get("function_description"));

        // 8 - Org_Unit_ID
        Integer orgUnitId = (Integer) personData.get("org_unit_id");
        if (orgUnitId != null) pstmt.setInt(8, orgUnitId); else pstmt.setNull(8, java.sql.Types.INTEGER);

        // 9 - status_id
        Integer statusId = (Integer) personData.get("status_id");
        if (statusId != null) pstmt.setInt(9, statusId); else pstmt.setNull(9, java.sql.Types.INTEGER);

        // 10 - System_Role
        Integer systemRole = (Integer) personData.get("system_role");
        if (systemRole != null) pstmt.setInt(10, systemRole); else pstmt.setNull(10, java.sql.Types.INTEGER);

        // 11 - source_id
        Integer sourceId = (Integer) personData.get("source_id");
        if (sourceId != null) pstmt.setInt(11, sourceId); else pstmt.setNull(11, java.sql.Types.INTEGER);

        // 12 - profile_ImageID
        Integer profileImageId = (Integer) personData.get("profile_ImageID");
        if (profileImageId != null) pstmt.setInt(12, profileImageId); else pstmt.setNull(12, java.sql.Types.INTEGER);

        // 13 - lastupdateuser_id
        pstmt.setInt(13, userId);

        // 14 - external_id
        pstmt.setString(14, (String) personData.get("external_id"));
        
        // 15 - auth_source
        String authSource = (String) personData.get("auth_source");
        if (authSource != null) {
            pstmt.setString(15, authSource);
        } else {
            pstmt.setNull(15, java.sql.Types.VARCHAR);
        }
        
        // 16 - last_synced_at
        java.sql.Timestamp lastSyncedAt = (java.sql.Timestamp) personData.get("last_synced_at");
        if (lastSyncedAt != null) {
            pstmt.setTimestamp(16, lastSyncedAt);
        } else {
            pstmt.setNull(16, java.sql.Types.TIMESTAMP);
        }

        // 17 - WHERE ID = ?
        pstmt.setInt(17, (Integer) personData.get("id"));
    }

    /* =========================
       people_details helpers
       ========================= */

    /** Create people_details record first with default values */
    private int createPeopleDetailsFirst(Map<String, Object> personData, int userId) throws SQLException {
        //system.out.println("=== createPeopleDetailsFirst called ===");
        //system.out.println("Person data: " + personData);
        //system.out.println("User ID: " + userId);
        
        // Get values from personData or use defaults
        Integer lifecycleId = (Integer) personData.get("lifecycle");
        Integer employmentTypeId = (Integer) personData.get("employment_type");
        String companyName = (String) personData.get("company_name");
        String officeLocation = (String) personData.get("office_location");
        String internalMailCode = (String) personData.get("internal_mail_code");
        String officePhone = (String) personData.get("office_phone");
        String mobilePhone = (String) personData.get("mobile_phone");
        String linkedinUrl = (String) personData.get("linkedin_url");
        String twitter = (String) personData.get("twitter");
        String lanId = (String) personData.get("lan_id");
        String employedSince = (String) personData.get("employed_since");

        // Use defaults if not provided
        if (lifecycleId == null) lifecycleId = 1; // Default lifecycle
        if (employmentTypeId == null) employmentTypeId = 1; // Default employment type
        
        String sql = """
            INSERT INTO people_details (lifecycle, employment_type, external_company_name, 
              office_location, internal_mail_code, office_telephone, mobile_telephone, 
              linkedIn_url, twitter_handle, lan_id, employed_since, last_updateuser_id, 
              created_datetime, last_updatedtime)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NULL)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            int paramIndex = 1;
            pstmt.setInt(paramIndex++, lifecycleId);
            pstmt.setInt(paramIndex++, employmentTypeId);
            pstmt.setString(paramIndex++, companyName);
            pstmt.setString(paramIndex++, officeLocation);
            pstmt.setString(paramIndex++, internalMailCode);
            pstmt.setString(paramIndex++, officePhone);
            pstmt.setString(paramIndex++, mobilePhone);
            pstmt.setString(paramIndex++, linkedinUrl);
            pstmt.setString(paramIndex++, twitter);
            pstmt.setString(paramIndex++, lanId);
            pstmt.setString(paramIndex++, employedSince);
            pstmt.setInt(paramIndex++, userId);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) throw new SQLException("Creating people_details failed, no rows affected.");

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int detailsId = generatedKeys.getInt(1);
                    //system.out.println("Created people_details with ID: " + detailsId);
                    return detailsId;
                } else {
                    throw new SQLException("Creating people_details failed, no ID obtained.");
                }
            }
        }
    }

    


    /* =========================
       Extra utility (example)
       ========================= */

    /**
     * Search people by first name only (unchanged)
     */
    public List<Map<String, Object>> searchPeopleByFirstName(String firstName) throws SQLException {
        List<Map<String, Object>> people = new ArrayList<>();

        String sql = "SELECT p.*, s.primaryname as status_name FROM people p " +
                "LEFT JOIN status s ON p.status_id = s.ID " +
                "WHERE p.First_Name LIKE ? " +
                "ORDER BY p.First_Name, p.Last_Name";

        try (Connection conn = DatabaseConnection.getConnection();
             var pstmt = conn.prepareStatement(sql)) {

            String searchPattern = "%" + firstName + "%";
            pstmt.setString(1, searchPattern);

            try (var rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> person = new HashMap<>();
                    person.put("id", rs.getInt("ID"));
                    person.put("first_name", rs.getString("First_Name"));
                    person.put("last_name", rs.getString("Last_Name"));
                    person.put("email", rs.getString("Email"));
                    person.put("password", rs.getString("Password"));
                    person.put("description", rs.getString("Description"));
                    person.put("function_name", rs.getString("Function_Name"));
                    person.put("function_description", rs.getString("Function_Description"));
                    person.put("org_unit_id", rs.getInt("Org_Unit_ID"));
                    person.put("status_id", rs.getInt("status_id"));
                    person.put("status_name", rs.getString("status_name"));
                    person.put("created_date", rs.getTimestamp("Created_Date"));
                    person.put("last_updated", rs.getTimestamp("Last_Updated"));
                    people.add(person);
                }
            }
        }

        return people;
    }
}
