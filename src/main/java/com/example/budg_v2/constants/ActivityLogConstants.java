package com.example.budg_v2.constants;

/**
 * Constants for Admin Activity Logs system
 * Contains all setting names, change types, and component names
 */
public class ActivityLogConstants {
    
    // ========== Settings ==========
    public static final String SETTING_SYSTEM_SETTINGS = "System Settings";
    public static final String SETTING_OWNERSHIP_TRANSFER = "Ownership Transfer";
    public static final String SETTING_ROLE_PERMISSIONS = "Role Permissions";
    public static final String SETTING_DEFAULT_WORKFLOWS = "Default Workflows";
    public static final String SETTING_DEFAULT_CRS = "Default Change Requests";
    public static final String SETTING_ROLES_RESPONSIBILITIES = "Roles & Responsibilities";
    public static final String SETTING_CUSTOM_FIELDS = "Custom Fields";
    public static final String SETTING_DROPDOWN_CONFIG = "Dropdown Configurations";
    public static final String SETTING_DOWNLOAD_LOGS = "Download Logs";
    public static final String SETTING_APP_SETTINGS = "App Settings";
    public static final String SETTING_SEGMENTS = "Segments";
    public static final String SETTING_DELETED_OBJECTS = "Deleted Objects";
    public static final String SETTING_ROLES_ASSIGNMENT = "Roles Assignment";
    public static final String SETTING_MANAGE_LOCKS = "Manage Locks";
    public static final String SETTING_LOCKED_USERS = "Locked Users";
    public static final String SETTING_USER_PREFERENCE = "User Preference";
    public static final String SETTING_EDC_INTEGRATION = "EDC Integration";
    
    // ========== Change Types ==========
    public static final String CHANGE_TYPE_CREATE = "Create";
    public static final String CHANGE_TYPE_UPDATE = "Update";
    public static final String CHANGE_TYPE_DELETE = "Delete";
    public static final String CHANGE_TYPE_OTHER_ACTIONS = "Other Actions";
    
    // ========== System Settings Components ==========
    public static final String COMPONENT_ENVIRONMENT = "Environment";
    public static final String COMPONENT_DASHBOARD = "Dashboard";
    
    // ========== Ownership Transfer Components ==========
    public static final String COMPONENT_DASHBOARD_OWNERSHIP_TRANSFER = "Dashboard Ownership Transfer";
    public static final String COMPONENT_SAVED_SEARCH_OWNERSHIP_TRANSFER = "Saved Search Ownership Transfer";
    
    // ========== Download Logs Component ==========
    public static final String COMPONENT_LOGS = "Logs";
    
    // ========== App Settings Components ==========
    public static final String COMPONENT_QUICK_LINKS = "Quick Link";
    public static final String COMPONENT_DISPLAY_SETTINGS = "Display Settings";
    public static final String COMPONENT_SEARCH_SETTINGS = "Search Settings";
    public static final String COMPONENT_GLOSSARY_ROLLUP = "Glossary RollUp";
    
    // ========== Manage Locks Component ==========
    public static final String COMPONENT_OBJECT_LOCK = "Object Lock";
    
    // ========== User Preference Component ==========
    public static final String COMPONENT_UNISON_GRID = "Unison Grid";
    
    // ========== EDC Integration Components ==========
    public static final String COMPONENT_EDC_SETTINGS = "EDC Settings";
    public static final String COMPONENT_EDC_TEST_CONNECTION = "EDC Test Connection";
    
    // Private constructor to prevent instantiation
    private ActivityLogConstants() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}

