package com.example.budg_v2.bulk;

/**
 * Versioning and package identity for Bulk Migrate / Import ENV (Axon-style ZIP).
 */
public final class EnvironmentMigrationConstants {

    public static final String PACKAGE_TYPE = "budg_env";
    public static final int SCHEMA_VERSION = 1;
    public static final String METADATA_FILE = "metadata.json";
    /** Value written to metadata.json when workbooks use stable ENV names (no timestamp). */
    public static final String EXCEL_NAMING_STABLE = "stable_v1";

    private EnvironmentMigrationConstants() {
    }
}
