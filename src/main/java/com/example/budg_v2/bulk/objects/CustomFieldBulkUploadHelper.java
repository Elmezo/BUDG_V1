package com.example.budg_v2.bulk.objects;

import com.example.budg_v2.database.DatabaseConnection;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Helper class for handling custom fields in bulk upload operations
 */
public class CustomFieldBulkUploadHelper {
    
    private static final Logger logger = LoggerFactory.getLogger(CustomFieldBulkUploadHelper.class);
    
    // Pattern for decimal validation (max 13 digits for integer part)
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d{1,13}(\\.\\d+)?$");

    private static String stringFromJsonElement(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isNumber()) {
                return p.getAsNumber().toString();
            }
            if (p.isBoolean()) {
                return p.getAsBoolean() ? "true" : "false";
            }
            return p.getAsString();
        }
        return null;
    }

    private static String getCustomFieldRawValue(JsonObject rowData, String key) {
        if (key == null || !rowData.has(key)) {
            return null;
        }
        return stringFromJsonElement(rowData.get(key));
    }

    /** Strip BOM, NBSP, and other Unicode spaces (Java 11+ strip semantics). */
    private static String normalizeBulkFieldWhitespace(String s) {
        if (s == null) {
            return null;
        }
        return s.strip();
    }

    private static String normalizeNumberStringForValidation(String value) {
        if (value == null) {
            return null;
        }
        String t = normalizeBulkFieldWhitespace(value.replace(",", ""));
        return t == null ? null : t;
    }
    
    /**
     * Save custom field values from row data (uses its own connection)
     * @param objectId The object ID
     * @param objectType The object type (e.g., "Regulatory Theme", "Product")
     * @param rowData The row data from Python validation (contains custom field values)
     * @param userId The user ID
     * @throws SQLException if database error occurs
     * @throws IllegalArgumentException if validation fails
     * @deprecated Use the overloaded method with Connection parameter for better transaction control
     */
    @Deprecated
    public static void saveCustomFieldValuesFromRowData(int objectId, String objectType, JsonObject rowData, int userId) 
            throws SQLException, IllegalArgumentException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                saveCustomFieldValuesFromRowData(objectId, objectType, rowData, userId, conn);
                conn.commit();
            } catch (SQLException | IllegalArgumentException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    /**
     * Save custom field values from row data
     * @param objectId The object ID
     * @param objectType The object type (e.g., "Regulatory Theme", "Product")
     * @param rowData The row data from Python validation (contains custom field values)
     * @param userId The user ID
     * @param conn The database connection to use (must be part of an active transaction)
     * @throws SQLException if database error occurs
     * @throws IllegalArgumentException if validation fails
     */
    public static void saveCustomFieldValuesFromRowData(int objectId, String objectType, JsonObject rowData, int userId, Connection conn) 
            throws SQLException, IllegalArgumentException {
        
        try {
            // Get module ID from object type
            Integer moduleId = getModuleIdByObjectType(conn, objectType);
            if (moduleId == null) {
                throw new IllegalArgumentException(
                        "No module found for object type '" + objectType + "'. Custom fields cannot be saved.");
            }
            
            // Get all custom fields for this module
            List<Map<String, Object>> customFields = getCustomFieldsForModule(conn, moduleId);
            if (customFields.isEmpty()) {
                return; // No custom fields to save
            }
            
            // Validate and collect custom field values
            List<Map<String, Object>> valuesToSave = new ArrayList<>();
            
            for (Map<String, Object> field : customFields) {
                String displayName = (String) field.get("displayName");
                String customFieldName = (String) field.get("customFieldName");
                String dataType = (String) field.get("dataType");
                int metadataId = (Integer) field.get("id");
                boolean isMandatory = (Boolean) field.get("isMandatory");
                String defaultValue = (String) field.get("defaultValue");
                
                // Row keys must match metadata: user-defined DisplayName (any label), "DisplayName *",
                // CustomFieldName (e.g. cf_*), or "cf_* *" — all resolved from DB per module, not hardcoded.
                String value = null;
                String mandatoryStarDisplay = displayName != null ? displayName + " *" : null;
                String mandatoryStarCf = (customFieldName != null && !customFieldName.trim().isEmpty())
                        ? customFieldName.trim() + " *" : null;

                if (displayName != null && rowData.has(displayName) && !rowData.get(displayName).isJsonNull()) {
                    value = getCustomFieldRawValue(rowData, displayName);
                } else if (mandatoryStarDisplay != null && rowData.has(mandatoryStarDisplay)
                        && !rowData.get(mandatoryStarDisplay).isJsonNull()) {
                    value = getCustomFieldRawValue(rowData, mandatoryStarDisplay);
                } else if (customFieldName != null && !customFieldName.trim().isEmpty()
                        && rowData.has(customFieldName.trim()) && !rowData.get(customFieldName.trim()).isJsonNull()) {
                    value = getCustomFieldRawValue(rowData, customFieldName.trim());
                } else if (mandatoryStarCf != null && rowData.has(mandatoryStarCf)
                        && !rowData.get(mandatoryStarCf).isJsonNull()) {
                    value = getCustomFieldRawValue(rowData, mandatoryStarCf);
                } else {
                    logger.debug("Custom field '{}' (cf={}) not found in rowData. Available keys: {}",
                            displayName, customFieldName, rowData.keySet());
                }
                if (value != null) {
                    value = normalizeBulkFieldWhitespace(value);
                }
                if (value != null && value.isEmpty()) {
                    value = null;
                }
                if (value != null) {
                    logger.debug("Found custom field value for '{}': '{}'", displayName, value);
                }

                // Bulk: mandatory requires a non-empty cell; metadata default does not satisfy mandatory
                if (isMandatory && (value == null || value.isEmpty())) {
                    throw new IllegalArgumentException("Field '" + displayName + "' is mandatory and cannot be empty");
                }

                if (value == null && defaultValue != null && !defaultValue.trim().isEmpty()) {
                    value = defaultValue.trim();
                }
                
                // Validate value format if not empty
                if (value != null && !value.trim().isEmpty()) {
                    validateCustomFieldValue(value, dataType, metadataId, displayName);
                    
                    // Handle multiselect separately (multiple records)
                    if ("multiselect".equalsIgnoreCase(dataType)) {
                        insertMultiselectValues(conn, value, metadataId, objectId, userId);
                    } else {
                        // Prepare value data for saving
                        Map<String, Object> valueData = prepareValueData(value, dataType, metadataId, conn);
                        if (valueData != null) {
                            valuesToSave.add(valueData);
                        }
                    }
                }
            }
            
            // Delete existing custom field values for this object
            deleteExistingCustomFieldValues(conn, moduleId, objectId);
            
            // Insert new values
            if (!valuesToSave.isEmpty()) {
                insertCustomFieldValues(conn, valuesToSave, objectId, userId);
            }
            
            logger.info("Saved {} custom field values for {} {} (ID: {})", 
                valuesToSave.size(), objectType, objectId, objectId);
            // Note: Transaction commit is managed by the caller
                
        } catch (SQLException | IllegalArgumentException e) {
            logger.error("Error saving custom field values for {} {} (ID: {}): {}", objectType, objectId, objectId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Validate custom field value based on data type
     * @param value The value to validate
     * @param dataType The data type
     * @param metadataId The custom field metadata ID
     * @param displayName The display name (for error messages)
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateCustomFieldValue(String value, String dataType, int metadataId, String displayName) 
            throws IllegalArgumentException {
        
        if (value == null || value.trim().isEmpty()) {
            return; // Empty values are handled separately (mandatory check)
        }
        
        value = normalizeBulkFieldWhitespace(value);
        
        switch (dataType.toLowerCase()) {
            case "text":
                // Text - any string is valid
                break;
                
            case "number":
                // Number - integer (Excel often sends "42.0" or decimals that are whole numbers)
                try {
                    String t = normalizeNumberStringForValidation(value);
                    if (t == null || t.isEmpty()) {
                        return;
                    }
                    double d = Double.parseDouble(t);
                    if (!Double.isFinite(d) || d != Math.rint(d)) {
                        throw new IllegalArgumentException("Field '" + displayName + "' must be a valid integer number");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be a valid integer number");
                }
                break;
                
            case "decimal":
                // Decimal - must be decimal, integer part max 13 digits
                if (!DECIMAL_PATTERN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be a valid decimal number. Integer part cannot exceed 13 digits");
                }
                // Additional check for integer part length
                String[] parts = value.split("\\.");
                String integerPart = parts[0].replace("-", "");
                if (integerPart.length() > 13) {
                    throw new IllegalArgumentException("Field '" + displayName + "' integer part cannot exceed 13 digits");
                }
                break;
                
            case "date":
                // Date - validate format (dd/mm/yyyy or yyyy-mm-dd)
                if (!isValidDate(value)) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be a valid date (dd/mm/yyyy or yyyy-mm-dd)");
                }
                break;
                
            case "checkbox":
            case "boolean":
                // Checkbox / boolean — accept true or false in any letter case
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be 'true' or 'false'");
                }
                break;
                
            case "time":
                // Time - validate format (HH:mm:ss or HH:mm)
                if (!isValidTime(value)) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be a valid time (HH:mm:ss or HH:mm)");
                }
                break;
                
            case "percentage":
                // Percentage - must be number between 0 and 100
                try {
                    double pct = Double.parseDouble(value);
                    if (pct < 0 || pct > 100) {
                        throw new IllegalArgumentException("Field '" + displayName + "' must be a percentage value between 0 and 100");
                    }
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be a valid percentage number");
                }
                break;
                
            case "dropdown":
                // Dropdown - must be in enum values
                if (!isValidEnumValue(metadataId, value)) {
                    throw new IllegalArgumentException("Field '" + displayName + "' must be one of the allowed dropdown values");
                }
                break;
                
            case "multiselect":
                // Multiselect - each value must be in enum values (comma-separated)
                String[] multiselectValues = value.split(",");
                for (String v : multiselectValues) {
                    String trimmed = v.trim();
                    if (!trimmed.isEmpty() && !isValidEnumValue(metadataId, trimmed)) {
                        throw new IllegalArgumentException("Field '" + displayName + "' contains invalid value: " + trimmed);
                    }
                }
                break;
                
            default:
                logger.warn("Unknown data type for custom field validation: {}", dataType);
                break;
        }
    }
    
    /**
     * Prepare value data for saving based on data type
     * @param value The value
     * @param dataType The data type
     * @param metadataId The metadata ID
     * @param conn Database connection
     * @return Map with metadataId, enumId (if applicable), and value
     */
    private static Map<String, Object> prepareValueData(String value, String dataType, int metadataId, Connection conn) 
            throws SQLException {
        
        Map<String, Object> valueData = new HashMap<>();
        valueData.put("metadataId", metadataId);
        
        if ("dropdown".equalsIgnoreCase(dataType) || "multiselect".equalsIgnoreCase(dataType)) {
            // For dropdown/multiselect, resolve canonical enum value first, then find enum ID.
            String canonicalEnumValue = getCanonicalEnumValue(conn, metadataId, value);
            if (canonicalEnumValue == null) {
                return null; // Invalid enum value
            }

            Integer enumId = getEnumIdByValue(conn, metadataId, canonicalEnumValue);
            if (enumId != null) {
                valueData.put("enumId", enumId);
                valueData.put("value", null);
            } else {
                return null; // Invalid enum value
            }
        } else if ("checkbox".equalsIgnoreCase(dataType) || "boolean".equalsIgnoreCase(dataType)) {
            // For checkbox / boolean, normalize to lowercase true/false
            String normalized = ("true".equalsIgnoreCase(value.trim())) ? "true" : "false";
            valueData.put("enumId", null);
            valueData.put("value", normalized);
            } else {
            // For text, number, decimal, date, time, percentage
            valueData.put("enumId", null);
            valueData.put("value", value);
        }
        
        return valueData;
    }
    
    /**
     * Insert custom field values
     */
    private static void insertCustomFieldValues(Connection conn, List<Map<String, Object>> valuesToSave, 
            int objectId, int userId) throws SQLException {
        
        String insertSql = """
            INSERT INTO Custom_Field_Data
            (Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, Custom_Field_Value, LastUpdate_UserID, CreateDatetime)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Map<String, Object> valueData : valuesToSave) {
                int metadataId = (Integer) valueData.get("metadataId");
                Integer enumId = (Integer) valueData.get("enumId");
                String value = (String) valueData.get("value");
                
                stmt.setInt(1, metadataId);
                if (enumId != null) {
                    stmt.setInt(2, enumId);
                } else {
                    stmt.setNull(2, Types.INTEGER);
                }
                stmt.setInt(3, objectId);
                if (value != null) {
                    stmt.setString(4, value);
                } else {
                    stmt.setNull(4, Types.VARCHAR);
                }
                stmt.setInt(5, userId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    /**
     * Handle multiselect values (create multiple records)
     */
    private static void insertMultiselectValues(Connection conn, String value, int metadataId, int objectId, int userId) 
            throws SQLException {
        
        String[] values = value.split(",");
        String insertSql = """
            INSERT INTO Custom_Field_Data
            (Custom_Field_Metadata_ID, Custom_Field_Enum_ID, Facet_Object_ID, Custom_Field_Value, LastUpdate_UserID, CreateDatetime)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (String v : values) {
                String trimmed = v.trim();
                if (trimmed.isEmpty()) continue;
                
                String canonicalEnumValue = getCanonicalEnumValue(conn, metadataId, trimmed);
                if (canonicalEnumValue == null) {
                    continue;
                }

                Integer enumId = getEnumIdByValue(conn, metadataId, canonicalEnumValue);
                if (enumId != null) {
                    stmt.setInt(1, metadataId);
                    stmt.setInt(2, enumId);
                    stmt.setInt(3, objectId);
                    stmt.setNull(4, Types.VARCHAR);
                    stmt.setInt(5, userId);
                    stmt.addBatch();
                }
            }
            if (stmt.getParameterMetaData().getParameterCount() > 0) {
                stmt.executeBatch();
            }
        }
    }
    
    /**
     * Delete existing custom field values for an object
     */
    private static void deleteExistingCustomFieldValues(Connection conn, int moduleId, int objectId) throws SQLException {
        String deleteSql = """
            DELETE cfd FROM Custom_Field_Data cfd
            INNER JOIN Custom_Field_Metadata cfm ON cfd.Custom_Field_Metadata_ID = cfm.ID
            WHERE cfm.Module_ID = ? AND cfd.Facet_Object_ID = ?
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, moduleId);
            stmt.setInt(2, objectId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Get module ID by object type
     */
    private static Integer getModuleIdByObjectType(Connection conn, String objectType) throws SQLException {
        String sql = "SELECT id FROM module WHERE LOWER(primaryname) = LOWER(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, objectType);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }
        return null;
    }
    
    /**
     * Get object type from entity name (for bulk upload servlets)
     * Maps entity names to module primary names
     */
    public static String getObjectTypeFromEntity(String entity) {
        if (entity == null) return null;
        
        String entityLower = entity.toLowerCase();
        switch (entityLower) {
            case "regulatorytheme":
            case "regulatory_theme":
                return "Regulatory Theme";
            case "product":
                return "Product";
            case "project":
                return "Project";
            case "policy":
                return "Policy";
            case "regulation":
                return "Regulation";
            case "regulator":
                return "Regulator";
            case "process":
                return "Process";
            case "people":
            case "person":
                return "People";
            case "legal":
            case "legalentity":
            case "legal_entity":
                return "Legal Entity";
            case "client":
                return "Client";
            case "committee":
                return "Committee";
            case "orgunit":
            case "org_unit":
                return "Org Unit";
            case "businessarea":
            case "business_area":
                return "Business Area";
            case "capability":
                return "Capability";
            case "system":
                return "System";
            case "dataset":
                return "Data Sets";
            case "attribute":
                return "Attribute";
            case "interface":
                return "Interface";
            case "glossary":
                return "Glossary";
            case "geography":
                return "Geography";
            default:
                // Try to capitalize and use as-is
                return capitalizeFirst(entity);
        }
    }
    
    /**
     * Capitalize first letter of string
     */
    private static String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    /**
     * Get custom fields for a module
     */
    private static List<Map<String, Object>> getCustomFieldsForModule(Connection conn, int moduleId) throws SQLException {
        List<Map<String, Object>> customFields = new ArrayList<>();
        
        String sql = """
            SELECT ID, DisplayName, CustomFieldName, DataType, is_Mandatory, Default_Value
            FROM Custom_Field_Metadata
            WHERE Module_ID = ?
            ORDER BY ID
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, moduleId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> field = new HashMap<>();
                    field.put("id", rs.getInt("ID"));
                    field.put("displayName", rs.getString("DisplayName"));
                    field.put("customFieldName", rs.getString("CustomFieldName"));
                    field.put("dataType", rs.getString("DataType"));
                    field.put("isMandatory", rs.getBoolean("is_Mandatory"));
                    field.put("defaultValue", rs.getString("Default_Value"));
                    customFields.add(field);
                }
            }
        }
        
        return customFields;
    }
    
    /**
     * Get enum ID by value
     */
    private static Integer getEnumIdByValue(Connection conn, int metadataId, String enumValue) throws SQLException {
        String sql = "SELECT ID FROM Custom_Field_Enum WHERE Custom_Field_Metadata_ID = ? AND EnumValue = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, metadataId);
            stmt.setString(2, enumValue);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ID");
                }
            }
        }
        return null;
    }

    /**
     * Excel often sends numeric dropdowns as 1.0; DB EnumValue may be "1".
     */
    private static String normalizeExcelFloatLike(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            double d = Double.parseDouble(s.replace(",", "").trim());
            if (Double.isFinite(d) && d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
        } catch (NumberFormatException ignored) {
            // not a number
        }
        return null;
    }

    /**
     * Resolve enum value case-insensitively and return canonical DB value.
     * Matches EnumValue text, Excel-style "1.0" vs "1", or Custom_Field_Enum row ID.
     */
    private static String getCanonicalEnumValue(Connection conn, int metadataId, String value) throws SQLException {
        String sql = """
            SELECT EnumValue
            FROM Custom_Field_Enum
            WHERE Custom_Field_Metadata_ID = ?
              AND LOWER(EnumValue) = LOWER(?)
            ORDER BY ID
            LIMIT 1
            """;

        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String found = queryCanonicalEnumByLabel(conn, metadataId, trimmed, sql);
        if (found != null) {
            return found;
        }

        String alt = normalizeExcelFloatLike(trimmed);
        if (alt != null && !alt.equalsIgnoreCase(trimmed)) {
            found = queryCanonicalEnumByLabel(conn, metadataId, alt, sql);
            if (found != null) {
                return found;
            }
        }

        if (trimmed.matches("^\\d+$")) {
            String sqlId = """
                SELECT EnumValue FROM Custom_Field_Enum
                WHERE Custom_Field_Metadata_ID = ? AND ID = ?
                LIMIT 1
                """;
            try (PreparedStatement stmt = conn.prepareStatement(sqlId)) {
                stmt.setInt(1, metadataId);
                stmt.setInt(2, Integer.parseInt(trimmed));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("EnumValue");
                    }
                }
            }
        }
        return null;
    }

    private static String queryCanonicalEnumByLabel(Connection conn, int metadataId, String candidate, String sql)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, metadataId);
            stmt.setString(2, candidate);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("EnumValue");
                }
            }
        }
        return null;
    }
    
    /**
     * Check if enum value is valid
     */
    private static boolean isValidEnumValue(int metadataId, String value) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return getCanonicalEnumValue(conn, metadataId, value) != null;
        } catch (SQLException e) {
            logger.error("Error checking enum value: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Validate date format
     */
    private static boolean isValidDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return false;
        }
        // Try common date formats: dd/mm/yyyy, yyyy-mm-dd, etc.
        try {
            // Simple validation - can be enhanced
            return dateStr.matches("\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}") || 
                   dateStr.matches("\\d{4}-\\d{2}-\\d{2}");
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Validate time format
     */
    private static boolean isValidTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return false;
        }
        // Validate HH:mm:ss or HH:mm format
        return timeStr.matches("\\d{1,2}:\\d{2}(:\\d{2})?");
    }
}

