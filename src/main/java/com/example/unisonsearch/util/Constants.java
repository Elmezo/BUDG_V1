package com.example.unisonsearch.util;

/**
 * Shared constants for configuration keys, log tags, and modules.
 */
public final class Constants {
    private Constants() {}

    // Config keys
    public static final String CONFIG_FUZZY_DEFAULT = "UNISON_FUZZY_DEFAULT";
    public static final String CONFIG_HIDE_NON_PUBLIC = "Hide_Non_Public";
    public static final String CONFIG_AUTHORIZATION_ENABLED = "Authorization_enabled";
    public static final String CONFIG_EVENT_MONITOR_ENABLED = "Event_Monitor_enabled";
    public static final String CONFIG_UNISON_DEFAULTS = "UNISON_DEFAULTS";
    public static final String CONFIG_SEARCH_MIGRATION_V1_TO_V2 = "SEARCH_MIGRATION_V1_TO_V2";
    public static final String CONFIG_DATA_MIGRATION_ENABLED = "DATA_MIGRATION_ENABLED";
    /** When true (default), Unison Search excludes system nobject_id (CR clones). Set to false to show them (e.g. if system 128 is missing). */
    public static final String CONFIG_EXCLUDE_SYSTEM_NOBJECT_ID = "Unison_Exclude_System_NObjectId";
}


