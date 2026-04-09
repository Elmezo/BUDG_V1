package com.example.unisonsearch.model;

/**
 * Types of filters available in the system.
 */
public enum FilterType {
    DROPDOWN,      // Dropdown with checkboxes (e.g., Lifecycle, Status)
    DATE_RANGE,    // Date range with from/to pickers
    PEOPLE,        // People search filter
    BOOLEAN,       // Boolean Yes/No filter
    TEXT           // Simple text input
}
