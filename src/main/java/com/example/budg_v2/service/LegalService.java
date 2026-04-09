package com.example.budg_v2.service;

import com.example.budg_v2.dao.LegalDAO;
import com.example.budg_v2.model.Legal;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;

public class LegalService {

    private final LegalDAO legalDAO;

    public LegalService() {
        this.legalDAO = new LegalDAO();
    }

    public List<Legal> getAllLegals() throws SQLException {
        return legalDAO.getAllLegals();
    }

    public List<Legal> getAllLegals(int userId) throws SQLException {
        return legalDAO.getAllLegals(userId);
    }

    public Legal getLegalById(int id) throws SQLException {
        return legalDAO.getLegalById(id);
    }

    public List<Legal> searchLegals(String searchQuery) throws SQLException {
        if (searchQuery == null || searchQuery.trim().isEmpty()) {
            return getAllLegals();
        }
        return legalDAO.searchLegals(searchQuery.trim());
    }

    public Legal createLegal(Legal legal) throws SQLException, IllegalArgumentException {
        // Validate mandatory fields
        if (legal.getLongName() == null || legal.getLongName().trim().isEmpty()) {
            throw new IllegalArgumentException("Long Name is required");
        }
        if (legal.getShortName() == null || legal.getShortName().trim().isEmpty()) {
            throw new IllegalArgumentException("Short Name is required");
        }
        if (legal.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        if (legal.getIsPublic() == null) {
            throw new IllegalArgumentException("Is Public is required");
        }

        // Check for duplicate ShortName
        if (legal.getShortName() != null && !legal.getShortName().trim().isEmpty()) {
            if (legalDAO.isShortNameExists(legal.getShortName(), null)) {
                throw new IllegalArgumentException("Short Name already exists. Please choose another.");
            }
        }

        // Check for duplicate LongName
        if (legal.getLongName() != null && !legal.getLongName().trim().isEmpty()) {
            if (legalDAO.isLongNameExists(legal.getLongName(), null)) {
                throw new IllegalArgumentException("Long Name already exists. Please choose another.");
            }
        }

        // Additional validation for data integrity
        validateDataIntegrity(legal);
        
        // Validate foreign key references
        validateForeignKeyReferences(legal);

        // Set default values for optional fields
        if (legal.getDescription() == null) {
            legal.setDescription("");
        }

        return legalDAO.createLegal(legal);
    }

    public Legal createLegal(String longName, String shortName, String description,
                           Integer parentId, Integer status, Integer isPublic, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        Legal legal = new Legal();
        legal.setLongName(longName);
        legal.setShortName(shortName);
        legal.setDescription(description);
        legal.setParentId(parentId);
        legal.setStatus(status);
        legal.setIsPublic(isPublic);
        legal.setLastUpdateUserId(lastUpdateUserId);
        return createLegal(legal);
    }

    public boolean updateLegal(Legal legal) throws SQLException, IllegalArgumentException {
        if (legal.getId() == null) {
            throw new IllegalArgumentException("Legal ID is required for update");
        }
        
        // Validate mandatory fields
        if (legal.getLongName() == null || legal.getLongName().trim().isEmpty()) {
            throw new IllegalArgumentException("Long Name is required");
        }
        if (legal.getShortName() == null || legal.getShortName().trim().isEmpty()) {
            throw new IllegalArgumentException("Short Name is required");
        }
        if (legal.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        if (legal.getIsPublic() == null) {
            throw new IllegalArgumentException("Is Public is required");
        }

        // Get existing legal entity to compare names
        Legal existingLegal = legalDAO.getLegalById(legal.getId());
        if (existingLegal == null) {
            throw new IllegalArgumentException("Legal entity not found");
        }

        // Check for duplicate ShortName only if it has changed
        if (legal.getShortName() != null && !legal.getShortName().trim().isEmpty()) {
            if (!legal.getShortName().trim().equals(existingLegal.getShortName())) {
                if (legalDAO.isShortNameExists(legal.getShortName(), legal.getId())) {
                    throw new IllegalArgumentException("Short Name already exists. Please choose another.");
                }
            }
        }

        // Check for duplicate LongName only if it has changed
        if (legal.getLongName() != null && !legal.getLongName().trim().isEmpty()) {
            if (!legal.getLongName().trim().equals(existingLegal.getLongName())) {
                if (legalDAO.isLongNameExists(legal.getLongName(), legal.getId())) {
                    throw new IllegalArgumentException("Long Name already exists. Please choose another.");
                }
            }
        }

        // Additional validation for data integrity
        validateDataIntegrity(legal);
        
        // Validate foreign key references
        validateForeignKeyReferences(legal);

        // Set default values for optional fields
        if (legal.getDescription() == null) {
            legal.setDescription("");
        }

        return legalDAO.updateLegal(legal);
    }

    public boolean updateLegal(int id, String longName, String shortName, String description,
                             Integer parentId, Integer status, Integer isPublic, Integer lastUpdateUserId)
            throws SQLException, IllegalArgumentException {
        Legal legal = new Legal();
        legal.setId(id);
        legal.setLongName(longName);
        legal.setShortName(shortName);
        legal.setDescription(description);
        legal.setParentId(parentId);
        legal.setStatus(status);
        legal.setIsPublic(isPublic);
        legal.setLastUpdateUserId(lastUpdateUserId);
        return updateLegal(legal);
    }

    public boolean deleteLegal(int id, HttpServletRequest request) throws SQLException {
        String userName = getCurrentUserName(request);
        return legalDAO.deleteLegalWithAudit(id, userName);
    }

    private String getCurrentUserName(HttpServletRequest request) {
        try {
            String userJson = (String) request.getAttribute("user");
            if (userJson != null && userJson.contains("\"username\":")) {
                int start = userJson.indexOf("\"username\":\"") + 12;
                int end = userJson.indexOf("\"", start);
                if (end > start) {
                    return userJson.substring(start, end);
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting username: " + e.getMessage());
        }
        return "Unknown User";
    }

    /**
     * Validate data integrity for legal entity
     */
    private void validateDataIntegrity(Legal legal) throws IllegalArgumentException {
        // Validate Long Name length and content
        if (legal.getLongName() != null) {
            String longName = legal.getLongName().trim();
            if (longName.length() < 2) {
                throw new IllegalArgumentException("Long Name must be at least 2 characters long.");
            }
            if (longName.length() > 255) {
                throw new IllegalArgumentException("Long Name must not exceed 255 characters.");
            }
            // Check for invalid characters
            if (longName.matches(".*[<>\"'&].*")) {
                throw new IllegalArgumentException("Long Name contains invalid characters. Please avoid < > \" ' & characters.");
            }
        }

        // Validate Short Name length and content
        if (legal.getShortName() != null) {
            String shortName = legal.getShortName().trim();
            if (shortName.length() < 2) {
                throw new IllegalArgumentException("Short Name must be at least 2 characters long.");
            }
            if (shortName.length() > 100) {
                throw new IllegalArgumentException("Short Name must not exceed 100 characters.");
            }
            // Check for invalid characters
            if (shortName.matches(".*[<>\"'&].*")) {
                throw new IllegalArgumentException("Short Name contains invalid characters. Please avoid < > \" ' & characters.");
            }
        }

        // Validate Description length
        if (legal.getDescription() != null && legal.getDescription().length() > 1000) {
            throw new IllegalArgumentException("Description must not exceed 1000 characters.");
        }

        // Validate Status ID
        if (legal.getStatus() != null && legal.getStatus() <= 0) {
            throw new IllegalArgumentException("Status must be a valid positive number.");
        }

        // Validate Is Public ID
        if (legal.getIsPublic() != null && legal.getIsPublic() <= 0) {
            throw new IllegalArgumentException("Is Public must be a valid positive number.");
        }

        // Validate Parent ID if provided
        if (legal.getParentId() != null && legal.getParentId() <= 0) {
            throw new IllegalArgumentException("Parent ID must be a valid positive number.");
        }

        // Validate Last Update User ID if provided
        if (legal.getLastUpdateUserId() != null && legal.getLastUpdateUserId() <= 0) {
            throw new IllegalArgumentException("Last Update User ID must be a valid positive number.");
        }
    }

    /**
     * Validate foreign key references
     */
    private void validateForeignKeyReferences(Legal legal) throws SQLException, IllegalArgumentException {
        // Validate Status ID exists
        if (legal.getStatus() != null && !legalDAO.isStatusExists(legal.getStatus())) {
            throw new IllegalArgumentException("Status ID " + legal.getStatus() + " does not exist in the Status table.");
        }

        // Validate Is Public ID exists
        if (legal.getIsPublic() != null && !legalDAO.isViewingExists(legal.getIsPublic())) {
            throw new IllegalArgumentException("Viewing ID " + legal.getIsPublic() + " does not exist in the Viewing table.");
        }

        // Validate Parent ID exists (if provided)
        if (legal.getParentId() != null && !legalDAO.isParentLegalExists(legal.getParentId())) {
            throw new IllegalArgumentException("Parent Legal ID " + legal.getParentId() + " does not exist in the Legal table.");
        }

        // Validate Last Update User ID exists (if provided)
        if (legal.getLastUpdateUserId() != null && !legalDAO.isUserExists(legal.getLastUpdateUserId())) {
            throw new IllegalArgumentException("User ID " + legal.getLastUpdateUserId() + " does not exist in the People table.");
        }
    }
}
