package com.example.budg_v2.bulk;

/**
 * One row from {@code Manifest.json} for Excel-based bulk migration / ENV import packages.
 */
public final class MigrationManifestEntry {

    public final String targetRef;
    public final String fileEntry;

    public MigrationManifestEntry(String targetRef, String fileEntry) {
        this.targetRef = targetRef;
        this.fileEntry = fileEntry;
    }
}
