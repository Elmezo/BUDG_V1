package com.example.budg_v2.dao;

import com.example.budg_v2.database.DatabaseConnection;
import java.sql.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * DAO for managing user preferences including language/locale settings
 */
public class UserPreferencesDAO {
    
    private static final Logger logger = Logger.getLogger(UserPreferencesDAO.class.getName());
    
    private static final String GET_USER_LOCALE = "SELECT Locale FROM people WHERE ID = ? AND Deleted_date IS NULL";
    private static final String UPDATE_USER_LOCALE = "UPDATE people SET Locale = ?, Last_Updated = NOW() WHERE ID = ?";
    private static final String GET_USER_BY_ID = "SELECT ID, Email, First_Name, Last_Name, Locale FROM people WHERE ID = ? AND Deleted_date IS NULL";
    
    /**
     * Get user's locale/language preference
     * @param userId User ID
     * @return Locale string (e.g., 'en', 'ar') or null if not set
     */
    public String getUserLocale(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(GET_USER_LOCALE)) {
            
            pstmt.setInt(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("Locale");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving user locale for userId: " + userId, e);
            throw e;
        }
        
        return null;
    }
    
    /**
     * Update user's locale/language preference
     * @param userId User ID
     * @param locale Locale value (e.g., 'en', 'ar')
     * @return true if update was successful, false otherwise
     */
    public boolean updateUserLocale(int userId, String locale) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(UPDATE_USER_LOCALE)) {
            
            pstmt.setString(1, locale != null ? locale.trim() : null);
            pstmt.setInt(2, userId);
            
            int affectedRows = pstmt.executeUpdate();
            
            if (affectedRows > 0) {
                logger.log(Level.INFO, "User locale updated for userId: " + userId + " to: " + locale);
                return true;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating user locale for userId: " + userId, e);
            throw e;
        }
        
        return false;
    }
    
    /**
     * Get user information including locale
     * @param userId User ID
     * @return User data as UserPreference object
     */
    public UserPreference getUserPreferences(int userId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(GET_USER_BY_ID)) {
            
            pstmt.setInt(1, userId);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    UserPreference pref = new UserPreference();
                    pref.setUserId(rs.getInt("ID"));
                    pref.setEmail(rs.getString("Email"));
                    pref.setFirstName(rs.getString("First_Name"));
                    pref.setLastName(rs.getString("Last_Name"));
                    pref.setLocale(rs.getString("Locale"));
                    return pref;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving user preferences for userId: " + userId, e);
            throw e;
        }
        
        return null;
    }
    
    /**
     * Inner class for user preferences response
     */
    public static class UserPreference {
        private int userId;
        private String email;
        private String firstName;
        private String lastName;
        private String locale;
        
        public int getUserId() {
            return userId;
        }
        
        public void setUserId(int userId) {
            this.userId = userId;
        }
        
        public String getEmail() {
            return email;
        }
        
        public void setEmail(String email) {
            this.email = email;
        }
        
        public String getFirstName() {
            return firstName;
        }
        
        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
        
        public String getLastName() {
            return lastName;
        }
        
        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
        
        public String getLocale() {
            return locale;
        }
        
        public void setLocale(String locale) {
            this.locale = locale;
        }
    }
}
