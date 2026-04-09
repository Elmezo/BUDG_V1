// Dynamic Table Generator and sorting

// Helper function to get visible columns with UNISON_DEFAULTS (no hardcoded fallback)
function getVisibleColumnsHelper(category, columns, fallbackDefaults) {
    console.log('[TABLE] Getting visible columns for category:', category, 'Available columns:', columns.length);
    let visibleColumns = [];

    // Check if there are saved columns for this category
    if (typeof getVisibleColumnsForCategory === 'function') {
        const savedColumns = getVisibleColumnsForCategory(category);
        if (savedColumns && savedColumns.length > 0) {
            visibleColumns = savedColumns.filter(col => columns.includes(col));
            console.log('[TABLE] Using saved columns from sessionStorage:', visibleColumns);
        }
    }

    // If no saved columns, ALWAYS get from UNISON_DEFAULTS
    if (visibleColumns.length === 0) {
        const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
        if (defaultsFromDB && defaultsFromDB.length > 0) {
            visibleColumns = defaultsFromDB.filter(col => columns.includes(col));
            console.log('[TABLE] Using database defaults:', visibleColumns);
        } else {
            console.error('[TABLE] NO DATABASE DEFAULTS for category:', category);
            console.error('[TABLE] Check UNISON_DEFAULTS in app_config table');
            // Show all columns as fallback
            visibleColumns = columns;
            console.warn('[TABLE] Showing ALL columns as emergency fallback');
        }
    }

    // Special handling for role: ensure essential columns are always visible
    const normalizedCategory = (typeof categoryToModule === 'function') ? categoryToModule(category) : category.toLowerCase();
    if (normalizedCategory === 'role') {
        const essentialColumns = ['ID', 'Role', 'Description', 'Role type', 'Full Name'];
        
        // Ensure essential columns are included (add at the beginning if missing)
        essentialColumns.forEach(col => {
            if (columns.includes(col) && !visibleColumns.includes(col)) {
                visibleColumns.unshift(col); // Add at the beginning
            }
        });
        
        // Reorder visibleColumns to have essential columns first, then others
        const essentialInVisible = essentialColumns.filter(col => visibleColumns.includes(col));
        const otherColumns = visibleColumns.filter(col => !essentialColumns.includes(col));
        visibleColumns = [...essentialInVisible, ...otherColumns];
        
        console.log('[TABLE] Role essential columns ensured:', visibleColumns);
    }

    return visibleColumns;
}

function isSegmentAliasColumn(columnKey) {
    if (!columnKey) return false;
    const normalized = String(columnKey).trim().toLowerCase();
    return normalized === 'segment' || normalized === 'segments';
}

function getCanonicalSegmentColumnForCategory(normalizedCategory) {
    return normalizedCategory === 'change-requests' ? 'Segments' : 'Segment';
}

function normalizeSegmentColumns(columns, normalizedCategory) {
    if (!Array.isArray(columns) || columns.length === 0) return [];

    const canonicalSegmentKey = getCanonicalSegmentColumnForCategory(normalizedCategory);
    const result = [];
    let segmentAdded = false;

    columns.forEach((columnKey) => {
        if (isSegmentAliasColumn(columnKey)) {
            if (!segmentAdded) {
                result.push(canonicalSegmentKey);
                segmentAdded = true;
            }
            return;
        }
        if (!result.includes(columnKey)) {
            result.push(columnKey);
        }
    });

    return result;
}

function normalizeVisibleColumns(visibleColumns, allColumns, normalizedCategory) {
    if (!Array.isArray(visibleColumns) || visibleColumns.length === 0) return [];
    const canonicalSegmentKey = getCanonicalSegmentColumnForCategory(normalizedCategory);
    const normalizedVisible = [];

    visibleColumns.forEach((columnKey) => {
        const targetKey = isSegmentAliasColumn(columnKey) ? canonicalSegmentKey : columnKey;
        if (allColumns.includes(targetKey) && !normalizedVisible.includes(targetKey)) {
            normalizedVisible.push(targetKey);
        }
    });

    return normalizedVisible;
}

function getRowValueForColumn(row, columnKey) {
    if (!row || !columnKey) return undefined;
    if (Object.prototype.hasOwnProperty.call(row, columnKey)) {
        return row[columnKey];
    }

    // Canonical value fallbacks for common backend aliases
    if (columnKey === 'Name') {
        return row.Name ?? row.name ?? row.PrimaryName ?? row.primaryName ?? row.primaryname;
    }
    if (columnKey === 'Short Name') {
        return row['Short Name'] ?? row.ShortName ?? row.shortName ?? row.shortname;
    }
    if (columnKey === 'Description') {
        return row.Description ?? row.description;
    }
    if (columnKey === 'Ref.') {
        return row['Ref.']
            ?? row.Reference
            ?? row.reference
            ?? row.REFERENCE
            ?? row.Ref_Number
            ?? row.refNumber
            ?? row.RefNumber;
    }
    if (columnKey === 'Parent') {
        return row.Parent ?? row.parent;
    }
    if (columnKey === 'BUDG Status') {
        return row['BUDG Status'] ?? row.budg_status ?? row.BUDG_Status;
    }
    if (columnKey === 'Created Date') {
        return row['Created Date'] ?? row['Create Date'] ?? row.Created_Date ?? row.createdDate ?? row.CreatedDate
            ?? row.createdAt ?? row.CreatedAt;
    }
    if (columnKey === 'Create Date') {
        return row['Create Date'] ?? row['Created Date'] ?? row.createdAt ?? row.CreatedAt ?? row.created_date;
    }
    if (columnKey === 'Last Updated') {
        return row['Last Updated'] ?? row.last_updated_date ?? row.LastUpdated ?? row.lastUpdated;
    }
    if (columnKey === 'Last Update Date' || columnKey === 'Last Updated Date') {
        return row['Last Update Date'] ?? row['Last Updated Date'] ?? row.updatedAt ?? row.UpdatedAt
            ?? row.last_updated_date;
    }
    if (columnKey === 'Type') {
        return row.Type ?? row.type ?? row.typeName ?? row.TypeName;
    }
    if (columnKey === 'Status') {
        return row.Status ?? row.status ?? row.statusName ?? row.StatusName;
    }
    if (columnKey === 'Severity') {
        return row.Severity ?? row.severity ?? row.severityName ?? row.SeverityName;
    }
    if (columnKey === 'Urgency') {
        return row.Urgency ?? row.urgency ?? row.urgencyName ?? row.UrgencyName;
    }
    if (columnKey === 'Object') {
        return row.Object ?? row.object;
    }
    if (columnKey === 'Object Type') {
        return row['Object Type'] ?? row.object_type ?? row.objectType;
    }

    if (columnKey === 'Source System Short Name') {
        return row['Source System Short Name'] ?? row.Source_System_Short_Name ?? row.sourceSystemName;
    }
    if (columnKey === 'Target System Short Name') {
        return row['Target System Short Name'] ?? row.Target_System_Short_Name ?? row.targetSystemName;
    }
    if (columnKey === 'Asset ID') {
        return row['Asset ID'] ?? row.Asset_ID ?? row.assetId;
    }
    if (columnKey === 'Transfer Format') {
        return row['Transfer Format'] ?? row.Transfer_Format ?? row.transferFormat;
    }
    if (columnKey === 'Transfer Method') {
        return row['Transfer Method'] ?? row.Transfer_Method ?? row.transferMethod;
    }
    if (columnKey === 'BUDG Viewing') {
        return row['BUDG Viewing'] ?? row.BUDG_Viewing ?? row.budgViewing;
    }
    if (columnKey === 'Lifecycle') {
        return row.Lifecycle ?? row.lifecycle;
    }
    if (columnKey === 'Automation') {
        return row.Automation ?? row.automation;
    }
    if (columnKey === 'Frequency') {
        return row.Frequency ?? row.frequency;
    }
    if (columnKey === 'Classification') {
        return row.Classification ?? row.classification;
    }
    if (columnKey === 'Synchronisation' || columnKey === 'Synchronization') {
        return row.Synchronisation ?? row.Synchronization ?? row.synchronisation ?? row.synchronization;
    }

    if (columnKey === 'Segment') {
        return row.Segment ?? row.segment ?? row.Segments ?? row.segments;
    }
    if (columnKey === 'Segments') {
        return row.Segments ?? row.segments ?? row.Segment ?? row.segment;
    }

    if (columnKey === 'Created By') {
        return row['Created By'] ?? row.createdByName ?? row.CreatedByName ?? '';
    }

    return undefined;
}

function normalizeSegmentAliasesInRows(data, normalizedCategory) {
    if (!Array.isArray(data) || data.length === 0) return;

    if (normalizedCategory === 'interface') {
        data.forEach((row) => {
            if (!row || typeof row !== 'object') return;
            const refVal = row['Ref.'] ?? row.Ref_number ?? row.ref_number ?? row.RefNumber ?? row.reference;
            if (refVal != null && refVal !== '' && row['Ref.'] === undefined) {
                row['Ref.'] = refVal;
            }
            ['Ref_number', 'ref_number', 'RefNumber', 'reference', 'REFERENCE', 'Reference'].forEach((k) => {
                if (Object.prototype.hasOwnProperty.call(row, k)) {
                    delete row[k];
                }
            });
        });
    }

    // Org Unit is not shown with a Segment column in Unison (no segment select in list SQL).
    if (normalizedCategory === 'orgunit' || normalizedCategory === 'org_unit') {
        data.forEach((row) => {
            if (!row || typeof row !== 'object') return;
            // One canonical ref column: Ref. (avoid duplicate Reference / reference headers)
            const refVal = row['Ref.'] ?? row.Reference ?? row.reference ?? row.REFERENCE;
            if (refVal != null && row['Ref.'] === undefined) {
                row['Ref.'] = refVal;
            }
            ['Reference', 'reference', 'REFERENCE', 'Ref_Number', 'refNumber'].forEach((k) => {
                if (k !== 'Ref.' && Object.prototype.hasOwnProperty.call(row, k)) {
                    delete row[k];
                }
            });
            Object.keys(row).forEach((key) => {
                if (isSegmentAliasColumn(key)
                    || key === 'Segment' || key === 'Segments'
                    || key === 'segment_id' || key === 'Segment_ID') {
                    delete row[key];
                }
            });
        });
        return;
    }

    const canonicalSegmentKey = getCanonicalSegmentColumnForCategory(normalizedCategory);

    data.forEach((row) => {
        if (!row || typeof row !== 'object') return;
        const segmentValue = getRowValueForColumn(row, canonicalSegmentKey);

        Object.keys(row).forEach((key) => {
            if (isSegmentAliasColumn(key)) {
                delete row[key];
            }
        });

        // Keep exactly one canonical segment key in every row.
        row[canonicalSegmentKey] = segmentValue ?? 'Not Assigned';
    });
}

function createDynamicTable(data, category) {
    if (!Array.isArray(data) || data.length === 0) {
        return null;
    }

    const normalizedCategory = categoryToModule(category);
    normalizeSegmentAliasesInRows(data, normalizedCategory);

    const firstRow = data[0];
    let columns = Object.keys(firstRow);

    // For dataset, attribute, system, glossary, and people categories, filter to only allowed columns and exclude _ID columns
    
    
    // Special handling for Active Tasks
    if (normalizedCategory === 'activeTasks' || category.toLowerCase() === 'active-tasks' || category.toLowerCase() === 'activetasks') {
        return createActiveTasksTable(data, category);
    }
    if (normalizedCategory === 'dataset' && typeof DATASET_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        DATASET_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            DATASET_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
        
        // Add System Impact column for datasets if any row has systemImpact data
        const hasSystemImpact = data.some(row => row.systemImpact && Object.keys(row.systemImpact).length > 0);
        if (hasSystemImpact && !columns.includes('SystemImpact')) {
            columns.push('SystemImpact');
        }
    } else if (normalizedCategory === 'attribute' && typeof ATTRIBUTE_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        ATTRIBUTE_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            ATTRIBUTE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'system' && typeof SYSTEM_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        SYSTEM_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            SYSTEM_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'glossary' && typeof GLOSSARY_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        GLOSSARY_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            GLOSSARY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'people' && typeof PEOPLE_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        PEOPLE_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            PEOPLE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'role' && typeof ROLE_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        ROLE_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            ROLE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'business-area' && typeof BUSINESS_AREA_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        BUSINESS_AREA_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            BUSINESS_AREA_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'client' && typeof CLIENT_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        CLIENT_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            CLIENT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'committee' && typeof COMMITTEE_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        COMMITTEE_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            COMMITTEE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'policy' && typeof POLICY_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        POLICY_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            POLICY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'process' && typeof PROCESS_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        PROCESS_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            PROCESS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'interface' && typeof INTERFACE_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        INTERFACE_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            INTERFACE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'capability' && typeof CAPABILITY_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        CAPABILITY_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            CAPABILITY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'legal-entity' && typeof LEGAL_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        LEGAL_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            LEGAL_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'orgunit' && typeof ORGUNIT_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        ORGUNIT_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            ORGUNIT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'product' && typeof PRODUCT_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        PRODUCT_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            PRODUCT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'geography' && typeof GEOGRAPHY_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        GEOGRAPHY_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            GEOGRAPHY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulation' && typeof REGULATION_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        REGULATION_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            REGULATION_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulator' && typeof REGULATOR_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        REGULATOR_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            REGULATOR_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulatory-theme' && typeof REGULATORY_THEME_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        REGULATORY_THEME_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            REGULATORY_THEME_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'change-requests' && typeof CHANGE_REQUESTS_ALLOWED_COLUMNS !== 'undefined') {
        // Get all columns from first row
        const allColumns = Object.keys(firstRow);
        // Also check other rows to find columns that might be null in first row
        const allPossibleColumns = new Set(allColumns);
        data.slice(0, 10).forEach(row => {
            Object.keys(row).forEach(key => allPossibleColumns.add(key));
        });

        // Add all allowed columns even if they're not in the data (to handle null values)
        CHANGE_REQUESTS_ALLOWED_COLUMNS.forEach(col => {
            if (!col.endsWith('_ID')) {
                allPossibleColumns.add(col);
            }
        });

        // Filter to only allowed columns and exclude _ID columns
        columns = Array.from(allPossibleColumns).filter(key =>
            CHANGE_REQUESTS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    }

    // Detect custom field columns from data that are not in the standard allowed lists.
    // The backend enriches results with CF values as additional keys in each row.
    const INTERNAL_COLUMNS = new Set(['ID', 'id', 'systemImpact', 'relatedCRs', 'activeTasks', 'segments']);
    const normalizeCustomKey = (key) => key.toString().toLowerCase().replace(/[\s._-]+/g, '');
    const aliasToCanonical = {
        primaryname: 'name',
        shortname: 'shortname',
        refnumber: 'ref',
        ref: 'ref',
        regulatorid: 'id',
        regulatorythemeid: 'id'
    };
    const canonicalizeKey = (key) => {
        const normalized = normalizeCustomKey(key);
        return aliasToCanonical[normalized] || normalized;
    };
    const existingCanonicalKeys = new Set(columns.map(canonicalizeKey));
    const allDataKeys = new Set();
    data.slice(0, Math.min(data.length, 20)).forEach(row => {
        Object.keys(row).forEach(key => allDataKeys.add(key));
    });
    allDataKeys.forEach(key => {
        // Change-requests table uses a fixed allowlist only (no extra API field columns).
        if (normalizedCategory === 'change-requests') {
            return;
        }
        // Avoid re-adding alias keys as custom columns (e.g. PrimaryName vs Name)
        if (existingCanonicalKeys.has(canonicalizeKey(key))) {
            return;
        }
        if (!columns.includes(key) && !INTERNAL_COLUMNS.has(key) &&
            !key.endsWith('_ID') && !key.endsWith('_id') &&
            typeof data[0][key] !== 'object') {
            columns.push(key);
        }
    });

    // Ensure Segment column is available even when allowed-columns lists are used
    if (Object.prototype.hasOwnProperty.call(firstRow, 'Segment') && !columns.includes('Segment')) {
        columns.push('Segment');
    }
    // Ensure Segments column is available for change-requests (BUDG-style)
    if (normalizedCategory === 'change-requests' && (Object.prototype.hasOwnProperty.call(firstRow, 'Segments') || Object.prototype.hasOwnProperty.call(firstRow, 'Segment')) && !columns.includes('Segments')) {
        columns.push('Segments');
    }
    columns = normalizeSegmentColumns(columns, normalizedCategory);

    // Get default visible columns for dataset, attribute, system, glossary, and people categories
    let visibleColumns = [];
    if (normalizedCategory === 'dataset' && typeof DATASET_DEFAULT_COLUMNS !== 'undefined') {
        // Check if there are saved columns for this category
        if (typeof getVisibleColumnsForCategory === 'function') {
            const savedColumns = getVisibleColumnsForCategory(category);
            if (savedColumns && savedColumns.length > 0) {
                visibleColumns = savedColumns.filter(col => columns.includes(col));
            }
        }

        // If no saved columns, try to get from UNISON_DEFAULTS first
        if (visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => columns.includes(col));
            }
        }

        // If still no columns, use fallback default columns
        if (visibleColumns.length === 0) {
            visibleColumns = DATASET_DEFAULT_COLUMNS.filter(col => columns.includes(col));
        }
    } else if (normalizedCategory === 'attribute' && typeof ATTRIBUTE_DEFAULT_COLUMNS !== 'undefined') {
        // Check if there are saved columns for this category
        if (typeof getVisibleColumnsForCategory === 'function') {
            const savedColumns = getVisibleColumnsForCategory(category);
            if (savedColumns && savedColumns.length > 0) {
                visibleColumns = savedColumns.filter(col => columns.includes(col));
            }
        }

        // If no saved columns, try to get from UNISON_DEFAULTS first
        if (visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => columns.includes(col));
            }
        }

        // If still no columns, use fallback default columns
        if (visibleColumns.length === 0) {
            visibleColumns = ATTRIBUTE_DEFAULT_COLUMNS.filter(col => columns.includes(col));
        }
    } else if (normalizedCategory === 'system' && typeof SYSTEM_DEFAULT_COLUMNS !== 'undefined') {
        // Check if there are saved columns for this category
        if (typeof getVisibleColumnsForCategory === 'function') {
            const savedColumns = getVisibleColumnsForCategory(category);
            if (savedColumns && savedColumns.length > 0) {
                visibleColumns = savedColumns.filter(col => columns.includes(col));
            }
        }

        // If no saved columns, try to get from UNISON_DEFAULTS first
        if (visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => columns.includes(col));
            }
        }

        // If still no columns, use fallback default columns
        if (visibleColumns.length === 0) {
            visibleColumns = SYSTEM_DEFAULT_COLUMNS.filter(col => columns.includes(col));
        }
    } else if (normalizedCategory === 'glossary' && typeof GLOSSARY_DEFAULT_COLUMNS !== 'undefined') {
        // Check if there are saved columns for this category
        if (typeof getVisibleColumnsForCategory === 'function') {
            const savedColumns = getVisibleColumnsForCategory(category);
            if (savedColumns && savedColumns.length > 0) {
                visibleColumns = savedColumns.filter(col => columns.includes(col));
            }
        }

        // If no saved columns, try to get from UNISON_DEFAULTS first
        if (visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => columns.includes(col));
            }
        }

        // If still no columns, use fallback default columns
        if (visibleColumns.length === 0) {
            visibleColumns = GLOSSARY_DEFAULT_COLUMNS.filter(col => columns.includes(col));
        }
    } else if (normalizedCategory === 'people' && typeof PEOPLE_DEFAULT_COLUMNS !== 'undefined') {
        // Check if there are saved columns for this category
        if (typeof getVisibleColumnsForCategory === 'function') {
            const savedColumns = getVisibleColumnsForCategory(category);
            if (savedColumns && savedColumns.length > 0) {
                visibleColumns = savedColumns.filter(col => columns.includes(col));
            }
        }

        // If no saved columns, try to get from UNISON_DEFAULTS first
        if (visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => columns.includes(col));
            }
        }

        // If still no columns, use fallback default columns
        if (visibleColumns.length === 0) {
            visibleColumns = PEOPLE_DEFAULT_COLUMNS.filter(col => columns.includes(col));
        }
    } else if (normalizedCategory === 'role' && typeof ROLE_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, ROLE_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'business-area' && typeof BUSINESS_AREA_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, BUSINESS_AREA_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'client' && typeof CLIENT_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, CLIENT_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'committee' && typeof COMMITTEE_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, COMMITTEE_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'policy' && typeof POLICY_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, POLICY_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'process' && typeof PROCESS_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, PROCESS_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'interface' && typeof INTERFACE_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, INTERFACE_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'capability' && typeof CAPABILITY_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, CAPABILITY_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'legal-entity' && typeof LEGAL_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, LEGAL_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'orgunit' && typeof ORGUNIT_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, ORGUNIT_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'product' && typeof PRODUCT_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, PRODUCT_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'geography' && typeof GEOGRAPHY_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, GEOGRAPHY_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'regulation' && typeof REGULATION_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, REGULATION_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'regulator' && typeof REGULATOR_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, REGULATOR_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'regulatory-theme' && typeof REGULATORY_THEME_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, REGULATORY_THEME_DEFAULT_COLUMNS);
    } else if (normalizedCategory === 'change-requests' && typeof CHANGE_REQUESTS_DEFAULT_COLUMNS !== 'undefined') {
        visibleColumns = getVisibleColumnsHelper(category, columns, CHANGE_REQUESTS_DEFAULT_COLUMNS);
    } else {
        // For other categories, show all columns
        visibleColumns = columns;
    }
    visibleColumns = normalizeVisibleColumns(visibleColumns, columns, normalizedCategory);

    // Create table element
    const table = document.createElement('table');
    table.classList.add('search-table');

    // Create header
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');

    // Add checkbox column header (first column)
    const checkboxHeader = document.createElement('th');
    checkboxHeader.className = 'checkbox-column-header';
    checkboxHeader.style.width = '40px';
    checkboxHeader.style.textAlign = 'center';
    checkboxHeader.innerHTML = '<input type="checkbox" id="select-all-checkbox" class="select-all-checkbox" title="Select All" />';
    headerRow.appendChild(checkboxHeader);

    columns.forEach(columnKey => {
        const th = document.createElement('th');
        th.setAttribute('data-column', columnKey);
        th.classList.add('sortable');
        // Hide columns that are not in visibleColumns
        if (visibleColumns.length > 0 && !visibleColumns.includes(columnKey)) {
            th.style.display = 'none';
        }
        th.innerHTML = `<div class="th-content"><span>${prettifyLabel(columnKey)}</span><i class="fas fa-sort"></i></div>`;
        headerRow.appendChild(th);
    });

    thead.appendChild(headerRow);
    table.appendChild(thead);

    // Create body
    const tbody = document.createElement('tbody');

    data.forEach((row) => {
        const tr = document.createElement('tr');

        // Add row reference if available (use value resolver so Ref. works when only Reference was loaded)
        const refKey = getRefKey(row, category);
        const refVal = refKey ? getRowValueForColumn(row, refKey) : null;
        if (row && refVal != null && refVal !== '') {
            tr.setAttribute('data-ref', String(refVal));
        }

        // Add facet information to row data for bulk update selection
        const rowWithFacet = { ...row };
        // Try to get facet ID from category
        if (category) {
            if (typeof categoryToFacetId === 'function') {
                rowWithFacet.facet = categoryToFacetId(category);
            } else if (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function') {
                rowWithFacet.facet = window.categoryToFacetId(category);
            } else {
                rowWithFacet.facet = category.toUpperCase();
            }
            rowWithFacet.category = category; // Also store category for reference
        }
        
        // Add row data for selection functionality
        tr.setAttribute('data-row-data', JSON.stringify(rowWithFacet));

        // Add checkbox column (first cell)
        const checkboxCell = document.createElement('td');
        checkboxCell.className = 'checkbox-column-cell';
        checkboxCell.style.textAlign = 'center';
        const rowId = rowWithFacet.ID || rowWithFacet.id || rowWithFacet.Id || rowWithFacet.Ref || '';
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'row-select-checkbox';
        checkbox.setAttribute('data-row-id', String(rowId));
        checkboxCell.appendChild(checkbox);
        tr.appendChild(checkboxCell);

        columns.forEach(columnKey => {
            const td = document.createElement('td');
            td.setAttribute('data-column', columnKey);
            // Replace spaces and special characters in class name with hyphens
            // Valid CSS class names: letters, numbers, hyphens, underscores only
            const safeClassName = columnKey.replace(/[^\w]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase() + '-cell';
            td.classList.add(safeClassName);

            // Hide columns that are not in visibleColumns
            if (visibleColumns.length > 0 && !visibleColumns.includes(columnKey)) {
                td.style.display = 'none';
            }

            const value = getRowValueForColumn(row, columnKey);
            let formattedValue = formatCellValue(value, columnKey, row, category);

            // Add hierarchy indicator for Name column if hierarchy data exists
            if ((columnKey === 'Name' || columnKey === 'PrimaryName' || columnKey === 'primaryName') && 
                (row.level !== undefined || row.parentId !== undefined || row.Parent_ID !== undefined)) {
                const level = row.level || 0;
                const parentId = row.parentId || row.Parent_ID || row.parent_id;
                
                // Add hierarchy class for indentation
                if (level > 0) {
                    td.classList.add(`hierarchy-level-${Math.min(level, 5)}`);
                }
                
                // Add hierarchy indicator icon if it's a child
                if (parentId !== null && parentId !== undefined) {
                    const hierarchyIcon = `<span class="hierarchy-indicator" title="Child item (Level ${level})"><i class="fas fa-level-down-alt"></i></span>`;
                    // Prepend icon to formatted value
                    if (formattedValue.includes('<a ')) {
                        // If it's a link, add icon inside the link
                        formattedValue = formattedValue.replace(/(<a [^>]*>)/, `$1${hierarchyIcon}`);
                    } else {
                        formattedValue = hierarchyIcon + formattedValue;
                    }
                }
            }

            // Use innerHTML for HTML content (links, SystemImpact display, etc.)
            if (formattedValue.includes('<a ') || formattedValue.includes('<div') || formattedValue.includes('<span') || columnKey === 'SystemImpact') {
                td.innerHTML = formattedValue;
            } else {
                // Handle empty values
                if (formattedValue === '') {
                    td.innerHTML = '&nbsp;';
                } else {
                    td.textContent = formattedValue;
                }
            }

            tr.appendChild(td);
        });

        // Add expand button cell for related objects (if available)
        if (typeof hasRelatedObjects === 'function' && hasRelatedObjects(category, rowWithFacet.ID || rowWithFacet.id)) {
            const expandCell = document.createElement('td');
            expandCell.className = 'expand-column-cell';
            expandCell.style.textAlign = 'center';
            expandCell.innerHTML = `
                <button class="btn-icon expand-related" data-id="${rowWithFacet.ID || rowWithFacet.id}" data-facet="${category}" title="Show related objects">
                    <i class="icon-chevron-down"></i>
                </button>
            `;
            tr.appendChild(expandCell);
            
            // Add click handler for expand button
            const expandBtn = expandCell.querySelector('.expand-related');
            if (expandBtn && typeof toggleRelatedObjectsPanel === 'function') {
                expandBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    toggleRelatedObjectsPanel(tr, category, rowWithFacet.ID || rowWithFacet.id);
                });
            }
        }

        tbody.appendChild(tr);
    });

    table.appendChild(tbody);
    return table;
}

function generateTable(data, columns, currentCategory) {
    if (!tableContainer) {
        // محاولة إعادة تهيئة tableContainer
        tableContainer = document.querySelector('.data-table-wrapper');
        if (!tableContainer) {
            return;
        }
    }

    // فلترة البيانات إذا كان هناك عنصر محدد في نفس الـ module
    const selectedItem = getSelectedItem();
    if (selectedItem && selectedItem.category === currentCategory) {
        const selectedId = selectedItem.id;
        data = data.filter(row => {
            const rowId = row.ID || row.id;
            return rowId == selectedId;
        });
    }

    // التحقق من صحة البيانات
    if (!validateData(data, currentCategory)) {
        const noData = document.createElement('div');
        noData.classList.add('no-data-content');
        noData.innerHTML = `
            <i class="fas fa-database"></i>
            <p>No data available for ${currentCategory}</p>
        `;
        tableContainer.appendChild(noData);
        return;
    }

    // Clear previous content
    tableContainer.innerHTML = '';

    // Use the new dynamic table creation function
    const table = createDynamicTable(data, currentCategory);

    if (table) {
        tableContainer.appendChild(table);

        // Add event listeners for sorting and interactions
        addTableEventListeners();

        // Sync checkbox states after table is generated
        if (typeof window !== 'undefined' && window.bulkSelection && typeof window.bulkSelection.syncCheckboxStates === 'function') {
            setTimeout(() => {
                window.bulkSelection.syncCheckboxStates();
            }, 100);
        }

        // Update bulk buttons visibility based on current category
        if (typeof window !== 'undefined' && window.bulkSelection && typeof window.bulkSelection.updateButtonsVisibility === 'function' && currentCategory) {
            setTimeout(() => {
                window.bulkSelection.updateButtonsVisibility(currentCategory);
            }, 150);
        }

        // Hide specific columns for certain categories
        hideColumnsForCategory(currentCategory);

        // Apply saved column visibility settings with proper timing
        // Use requestAnimationFrame to ensure DOM is fully updated
        if (typeof applySavedColumnVisibility === 'function') {
            requestAnimationFrame(() => {
                // Double check table is still in DOM
                const currentTable = document.querySelector('.search-table');
                if (currentTable) {
                    // Don't await - let it run in background
                    applySavedColumnVisibility(currentCategory).catch(err => {
                        // Silent fail
                    });
                }
            });
        }
    } else {
        showErrorMessage('Error: Failed to create table');
    }
}

function addTableEventListeners() {
    if (!tableContainer) {
        return;
    }

    // Remove previous listener to prevent duplicate handlers accumulating
    if (tableContainer._searchTableClickHandler) {
        tableContainer.removeEventListener('click', tableContainer._searchTableClickHandler);
    }

    // Event delegation for clicks within the table
    tableContainer._searchTableClickHandler = function(e) {
        const target = e.target;

        // Handle clickable links
        const clickableLink = target.closest && target.closest('.clickable-link');
        if (clickableLink) {
            // All clickable links now have proper href and target="_blank",
            // so allow the browser's default link navigation to handle them.
            // Only use JS fallback for links that still have href="#" (shouldn't happen).
            const href = clickableLink.getAttribute('href');
            if (href && href !== '#') {
                // Let the browser handle navigation naturally via href + target="_blank"
                return;
            }

            // Fallback for links without a proper href
            e.preventDefault();
            e.stopPropagation();

            const module = clickableLink.getAttribute('data-module');
            const id = clickableLink.getAttribute('data-id');
            const fieldName = clickableLink.getAttribute('data-field');

            navigateToView(module, id, fieldName);
            return;
        }

        // allow normal navigation on name links (especially attribute->dataset)
        const nameLink = target.closest && target.closest('.search-table .name-link');
        if (nameLink) {
            return; // don't block default
        }

        // status badge click
        const statusBadge = target.closest && target.closest('.search-table .status-badge');
        if (statusBadge) {
            e.preventDefault();
            return;
        }

        // NO ROW CLICK HANDLING - completely removed
    };

    tableContainer.addEventListener('click', tableContainer._searchTableClickHandler);
}

function initTableSorting() {
    if (!tableContainer) {
        return;
    }
    tableContainer.addEventListener('click', (e) => {
        const header = e.target.closest && e.target.closest('.search-table th.sortable');
        if (!header) return;
        const column = header.getAttribute('data-column');
        const currentOrder = header.getAttribute('data-sort') || 'none';
        const newOrder = currentOrder === 'asc' ? 'desc' : 'asc';

        tableContainer.querySelectorAll('.search-table th.sortable').forEach(h => {
            h.setAttribute('data-sort', 'none');
            const iconEl = h.querySelector('i');
            if (iconEl) iconEl.className = 'fas fa-sort';
        });

        header.setAttribute('data-sort', newOrder);
        const icon = header.querySelector('i');
        if (icon) icon.className = newOrder === 'asc' ? 'fas fa-sort-up' : 'fas fa-sort-down';

        sortTableData(column, newOrder);
    });
}

function sortTableData(column, order) {
    if (!tableContainer) {
        return;
    }
    const tbody = tableContainer.querySelector('tbody');
    if (!tbody) return;
    const rows = Array.from(tbody.querySelectorAll('tr')).filter(r => !r.querySelector('td[colspan]'));
    
    rows.sort((a, b) => {
        const aCell = a.querySelector(`td[data-column="${column}"]`);
        const bCell = b.querySelector(`td[data-column="${column}"]`);
        
        if (!aCell || !bCell) return 0;
        
        // Get raw text content (before HTML formatting)
        const aVal = aCell.textContent.trim() || '';
        const bVal = bCell.textContent.trim() || '';
        
        // Special handling for numeric columns (id, dueInDays)
        if (column === 'id' || column === 'dueInDays') {
            const aNum = parseInt(aVal) || 0;
            const bNum = parseInt(bVal) || 0;
            return order === 'asc' ? aNum - bNum : bNum - aNum;
        }
        
        // Special handling for date columns (assignDate, dueDate)
        if (column === 'assignDate' || column === 'dueDate') {
            const aDate = new Date(aVal);
            const bDate = new Date(bVal);
            if (!isNaN(aDate.getTime()) && !isNaN(bDate.getTime())) {
                return order === 'asc' ? aDate - bDate : bDate - aDate;
            }
        }
        
        // Default string comparison
        return order === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);
    });
    rows.forEach(r => tbody.appendChild(r));
}

// Remove columns by names
function removeColumnsByNames(columnNames) {
    columnNames.forEach(columnName => {
        // احذف رأس العمود (th)
        document.querySelectorAll(`th[data-column="${columnName}"]`).forEach(th => th.remove());

        // احذف الخلايا (td) الخاصة بنفس العمود
        document.querySelectorAll(`td[data-column="${columnName}"]`).forEach(td => td.remove());
    });
}
window.removeColumnsByNames = removeColumnsByNames;

// Hide columns for category
function hideColumnsForCategory(category) {
    // Define which columns to hide for each category
    const hiddenColumnsByCategory = {
        'client': ['Parent_ID', 'Name_ID'],
        'committee': ['Parent_ID', 'Name_ID'],
        'legal-entity': ['Short Name_ID', 'Long Name_ID', 'Parent Short Name_ID', 'Parent Long Name_ID'],
        'system': ['Parent Short Name_ID', 'Short Name_ID', 'Created By_ID'],
        'role': ['Full Name_ID', 'Object_ID'],
        'business-area': ['Parent_ID', 'Name_ID'],
        'people': ['Org_Unit_Ref'],
        'geography': ['Name_ID', 'Parent_ID'],
        'regulation': ['Name_ID', 'Parent_ID', 'Created_By_ID'],
        'regulator': ['Name_ID'],
        'regulatory-theme': ['Created_By_ID'],
        'policy': ['Name_ID', 'Parent Name_ID', 'Created By_ID'],
        'process': ['Name_ID', 'Parent Name_ID', 'Created By_ID'],
        'interface': ['Name_ID', 'Source System Short Name_ID', 'Target System Short Name_ID', 'Created By_ID'],
        'capability': ['Name_ID', 'Parent_ID'],
        'product': ['Name_ID', 'Long Name_ID', 'Parent_ID']
        // أضف المزيد من الفئات هنا حسب الحاجة
    };

    const columnsToHide = hiddenColumnsByCategory[category];
    if (columnsToHide && columnsToHide.length > 0) {
        // تأخير بسيط للتأكد من أن الجدول تم إنشاؤه بالكامل
        setTimeout(() => {
            removeColumnsByNames(columnsToHide);
        }, 10);
    }

    // Always hide raw Segment_ID column if it exists (we only want to show Segment name)
    setTimeout(() => {
        removeColumnsByNames(['Segment_ID', 'segment_id']);
    }, 10);
}
window.hideColumnsForCategory = hideColumnsForCategory;

// Skeleton renderer
function renderSkeleton(wrapperEl) {
    if (!wrapperEl) return;
    const rows = SKELETON_ROWS;
    const cols = SKELETON_COLS;
    const frag = document.createDocumentFragment();
    const table = document.createElement('table');
    table.className = 'search-table'; // استخدام search-table بدلاً من data-table
    const thead = document.createElement('thead');
    const trh = document.createElement('tr');
    for (let c = 0; c < cols; c++) {
        const th = document.createElement('th');
        th.innerHTML = '<div class="th-content"><span>&nbsp;</span></div>';
        trh.appendChild(th);
    }
    thead.appendChild(trh);
    const tbody = document.createElement('tbody');
    for (let r = 0; r < rows; r++) {
        const tr = document.createElement('tr');
        for (let c = 0; c < cols; c++) {
            const td = document.createElement('td');
            td.innerHTML = '<div class="skeleton-line"></div>';
            tr.appendChild(td);
        }
        tbody.appendChild(tr);
    }
    table.appendChild(thead);
    table.appendChild(tbody);
    frag.appendChild(table);
}

/**
 * Create Active Tasks table with special formatting
 */
function createActiveTasksTable(data, category) {
    const table = document.createElement('table');
    table.classList.add('search-table', 'active-tasks-table');

    // Handle empty data
    if (!data || data.length === 0) {
        const tbody = document.createElement('tbody');
        const tr = document.createElement('tr');
        const td = document.createElement('td');
        td.colSpan = 11; // All columns
        td.style.textAlign = 'center';
        td.style.padding = '40px';
        td.innerHTML = '<div style="color: #888;"><i class="fas fa-inbox" style="font-size: 2rem; margin-bottom: 10px; display: block;"></i><p>No active tasks found</p></div>';
        tr.appendChild(td);
        tbody.appendChild(tr);
        table.appendChild(tbody);
        return table;
    }

    // Define columns for Active Tasks
    const columns = ['id', 'name', 'title', 'objectType', 'object', 'assignDate', 'dueDate', 'dueInDays', 'owner', 'segments', 'actions'];
    const columnLabels = {
        'id': 'Id',
        'name': 'Name',
        'title': 'Title',
        'objectType': 'Object Type',
        'object': 'Object',
        'assignDate': 'Assign Date',
        'dueDate': 'Due Date',
        'dueInDays': 'Due Days',
        'owner': 'Owner',
        'segments': 'Segments',
        'actions': 'Actions'
    };

    // Create header
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    
    columns.forEach(columnKey => {
        const th = document.createElement('th');
        th.setAttribute('data-column', columnKey);
        if (['id', 'name', 'title', 'assignDate', 'dueDate', 'dueInDays', 'owner'].includes(columnKey)) {
            th.classList.add('sortable');
        }
        th.innerHTML = `<div class="th-content"><span>${columnLabels[columnKey]}</span>${['id', 'name', 'title', 'assignDate', 'dueDate', 'dueInDays', 'owner'].includes(columnKey) ? '<i class="fas fa-sort"></i>' : ''}</div>`;
        headerRow.appendChild(th);
    });

    thead.appendChild(headerRow);
    table.appendChild(thead);

    // Create body
    const tbody = document.createElement('tbody');

    data.forEach((task) => {
        const tr = document.createElement('tr');
        tr.setAttribute('data-task-id', task.taskId || task.id);
        tr.setAttribute('data-row-data', JSON.stringify(task));
        
        // Add overdue class to row if task is overdue
        if (task.isOverdue || (task.dueInDays !== null && task.dueInDays < 0)) {
            tr.classList.add('overdue-row');
        }

        columns.forEach(columnKey => {
            const td = document.createElement('td');
            td.setAttribute('data-column', columnKey);
            td.classList.add(`${columnKey.replace(/[^\w]+/g, '-')}-cell`);

            let cellContent = '';

            switch (columnKey) {
                case 'id':
                    cellContent = task.id || task.taskId || '';
                    break;
                case 'name':
                    cellContent = task.name || '';
                    break;
                case 'title':
                    // Title with comment icon and clickable link
                    const titleText = task.title || '';
                    const changeRequestId = task.changeRequestId;
                    const taskId = task.taskId;
                    if (changeRequestId && taskId) {
                        cellContent = `<i class="fas fa-comment" style="color: #888; margin-right: 4px;"></i><a href="/view/change-request/change-request-view.html?id=${changeRequestId}&taskId=${taskId}" style="color: #248567; text-decoration: none;">${escapeHtml(titleText)}</a>`;
                    } else {
                        cellContent = `<i class="fas fa-comment" style="color: #888; margin-right: 4px;"></i>${escapeHtml(titleText)}`;
                    }
                    break;
                case 'objectType':
                    cellContent = task.objectType || 'Change Request';
                    break;
                case 'object':
                    // Object with icon and clickable link
                    const objectType = task.objectType || '';
                    const objectName = task.object || 'N/A';
                    const objectLink = getObjectLinkForTask(objectType, task);
                    if (objectLink) {
                        cellContent = `${getObjectIcon(objectType)}<a href="${objectLink}" style="color: #248567; text-decoration: none;">${escapeHtml(objectName)}</a>`;
                    } else {
                        cellContent = `${getObjectIcon(objectType)}${escapeHtml(objectName)}`;
                    }
                    break;
                case 'assignDate':
                    cellContent = task.assignDate || '';
                    break;
                case 'dueDate':
                    cellContent = task.dueDate || '';
                    break;
                case 'dueInDays':
                    // Highlight overdue tasks
                    const dueInDays = task.dueInDays;
                    if (dueInDays !== null && dueInDays !== undefined) {
                        if (dueInDays < 0) {
                            // Overdue - red highlight
                            const overdueDays = Math.abs(dueInDays);
                            cellContent = `<span class="overdue-task" style="background-color: #dc3545; color: white; padding: 4px 8px; border-radius: 4px; font-weight: bold;">Overdue by ${overdueDays}</span>`;
                        } else {
                            cellContent = String(dueInDays);
                        }
                    } else {
                        // No due date specified - display "Not Specified"
                        cellContent = 'Not Specified';
                    }
                    break;
                case 'owner':
                    cellContent = task.owner || 'Unassigned';
                    break;
                case 'segments':
                    cellContent = task.segments || 'Not Assigned';
                    break;
                case 'actions':
                    // Action buttons based on decisionOptions
                    const decisionOptions = task.decisionOptions || ['complete'];
                    const actionsHtml = decisionOptions.map(option => {
                        // Handle both object format {value, label} and string format
                        let value, label;
                        if (typeof option === 'object' && option !== null) {
                            value = option.value || option;
                            label = option.label || option.value || option;
                        } else {
                            value = option;
                            label = option.charAt(0).toUpperCase() + option.slice(1);
                        }
                        
                        // Determine button class and tooltip based on value (not label)
                        const buttonClass = value === 'approve' ? 'task-action-btn approve' :
                                          value === 'reject' ? 'task-action-btn reject' :
                                          'task-action-btn complete';
                        const tooltipText = value === 'approve' ? 'Approve this task' :
                                          value === 'reject' ? 'Reject this task' :
                                          'Complete this task';
                        
                        // Use value for data-action (what gets sent to backend), label for display
                        return `<button class="${buttonClass}" data-task-id="${task.taskId}" data-action="${value}" title="${tooltipText}" style="padding: 4px 12px; margin: 0 2px; border: none; border-radius: 4px; cursor: pointer; font-size: 0.875rem;">${label}</button>`;
                    }).join('');
                    cellContent = actionsHtml;
                    break;
                default:
                    cellContent = task[columnKey] || '';
            }

            // Ensure cellContent is a string before checking for HTML characters
            const cellContentStr = String(cellContent);
            if (cellContentStr.includes('<') || cellContentStr.includes('&')) {
                td.innerHTML = cellContentStr;
            } else {
                td.textContent = cellContentStr || '\u00A0';
            }

            tr.appendChild(td);
        });

        tbody.appendChild(tr);
    });

    table.appendChild(tbody);

    // Add event listeners for action buttons
    setTimeout(() => {
        table.querySelectorAll('.task-action-btn').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const taskId = btn.getAttribute('data-task-id');
                const action = btn.getAttribute('data-action');
                await handleTaskAction(taskId, action, e);
            });
        });
        
        // Initialize sorting for Active Tasks table
        if (typeof initTableSorting === 'function') {
            // Sorting is handled globally via event delegation in initTableSorting
            // But we need to ensure the table is in the container
            const container = tableContainer || document.querySelector('.data-table-wrapper');
            if (container && !container.contains(table)) {
                // Table will be added by generateTable, sorting will work automatically
            }
        }
    }, 100);

    return table;
}

/**
 * Get object link for task based on object type
 */
function getObjectLinkForTask(objectType, task) {
    if (!objectType || !task) return null;
    
    // Get objectId from task data if available
    const objectId = task.objectId;
    if (!objectId) return null;
    
    const normalizedType = (task.objectTypeNormalized || objectType).toLowerCase().trim();
    
    // Map object types to view URLs
    const typeMap = {
        'data sets': '/view/dataset/',
        'dataset': '/view/dataset/',
        'process': '/view/process/',
        'system': '/view/system/',
        'glossary': '/view/glossary/',
        'policy': '/view/policy/',
        'product': '/view/product/',
        'project': '/view/project/',
        'business area': '/view/business-area/',
        'business-area': '/view/business-area/',
        'businessarea': '/view/business-area/',
        'client': '/view/client/',
        'committee': '/view/committee/',
        'legal entity': '/view/LegalEntity/legal-entity.html?id=',
        'legal-entity': '/view/LegalEntity/legal-entity.html?id=',
        'legalentity': '/view/LegalEntity/legal-entity.html?id=',
        'capability': '/view/capability/',
        'regulation': '/view/regulation/',
        'regulator': '/view/regulator/',
        'regulatory theme': '/view/regulatory-theme/',
        'regulatory-theme': '/view/regulatory-theme/',
        'regulatorytheme': '/view/regulatory-theme/',
        'interface': '/view/system-interface/',
        'system-interface': '/view/system-interface/',
        'systeminterface': '/view/system-interface/',
        'org-unit': '/view/org-unit/',
        'orgunit': '/view/org-unit/',
        'geography': '/view/geography/'
    };
    
    const baseUrl = typeMap[normalizedType];
    if (!baseUrl) return null;
    
    // Build full URL
    if (baseUrl.endsWith('=')) {
        // URL with query parameter
        return baseUrl + objectId;
    } else {
        // URL with path parameter
        return baseUrl + objectId;
    }
}

/**
 * Get icon for object type
 */
function getObjectIcon(objectType) {
    if (!objectType) return '<i class="fas fa-file" style="color: #888; margin-right: 4px;"></i>';
    
    const normalizedType = objectType.toLowerCase().trim();
    const iconMap = {
        'data sets': '<i class="fas fa-database" style="color: #888; margin-right: 4px;"></i>',
        'dataset': '<i class="fas fa-database" style="color: #888; margin-right: 4px;"></i>',
        'process': '<i class="fas fa-project-diagram" style="color: #888; margin-right: 4px;"></i>',
        'system': '<i class="fas fa-server" style="color: #888; margin-right: 4px;"></i>',
        'glossary': '<i class="fas fa-book" style="color: #888; margin-right: 4px;"></i>',
        'policy': '<i class="fas fa-file-contract" style="color: #888; margin-right: 4px;"></i>',
        'product': '<i class="fas fa-box" style="color: #888; margin-right: 4px;"></i>',
        'project': '<i class="fas fa-folder" style="color: #888; margin-right: 4px;"></i>',
        'business area': '<i class="fas fa-sitemap" style="color: #888; margin-right: 4px;"></i>',
        'businessarea': '<i class="fas fa-sitemap" style="color: #888; margin-right: 4px;"></i>',
        'client': '<i class="fas fa-user-tie" style="color: #888; margin-right: 4px;"></i>',
        'committee': '<i class="fas fa-users" style="color: #888; margin-right: 4px;"></i>',
        'legal entity': '<i class="fas fa-building" style="color: #888; margin-right: 4px;"></i>',
        'legalentity': '<i class="fas fa-building" style="color: #888; margin-right: 4px;"></i>',
        'capability': '<i class="fas fa-cogs" style="color: #888; margin-right: 4px;"></i>',
        'regulation': '<i class="fas fa-gavel" style="color: #888; margin-right: 4px;"></i>',
        'regulator': '<i class="fas fa-balance-scale" style="color: #888; margin-right: 4px;"></i>',
        'regulatory theme': '<i class="fas fa-theater-masks" style="color: #888; margin-right: 4px;"></i>',
        'regulatorytheme': '<i class="fas fa-theater-masks" style="color: #888; margin-right: 4px;"></i>'
    };
    
    return iconMap[normalizedType] || '<i class="fas fa-file" style="color: #888; margin-right: 4px;"></i>';
}

/**
 * Handle task action (Approve, Reject, Complete)
 */
async function handleTaskAction(taskId, action, event) {
    if (!taskId || !action) {
        console.error('Invalid task action:', { taskId, action });
        alert('Invalid task action. Please try again.');
        return;
    }

    // Confirm action
    const actionText = action.charAt(0).toUpperCase() + action.slice(1);
    if (!confirm(`Are you sure you want to ${actionText} this task?`)) {
        return;
    }

    // Get button from event
    const button = event?.target || (typeof window.event !== 'undefined' ? window.event.target : null);

    try {
        // Disable button during request
        if (button) {
            button.disabled = true;
            button.style.opacity = '0.5';
            button.textContent = 'Processing...';
        }

        const response = await fetch(`/api/workflow_tasks/${taskId}/complete`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                decision: action,
                comment: '' // Can be enhanced to add comment input
            })
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            const errorMessage = errorData.error || `Failed to ${action} task`;
            alert(errorMessage);
            if (button) {
                button.disabled = false;
                button.style.opacity = '1';
            }
            return;
        }

        const result = await response.json();
        if (result.success) {
            // Show success message
            if (typeof showSuccessMessage === 'function') {
                showSuccessMessage(`Task ${action}d successfully`);
            } else {
                alert(`Task ${action}d successfully`);
            }
            
            // Reload active tasks
            const activeCategory = getActiveCategory();
            if (activeCategory && typeof loadCategoryData === 'function') {
                await loadCategoryData(activeCategory);
            } else {
                // Fallback: reload page
                window.location.reload();
            }
        } else {
            const errorMessage = result.error || `Failed to ${action} task`;
            alert(errorMessage);
            if (button) {
                button.disabled = false;
                button.style.opacity = '1';
            }
        }
    } catch (error) {
        console.error('Error handling task action:', error);
        alert(`Error ${action}ing task: ${error.message || 'Unknown error'}`);
        if (button) {
            button.disabled = false;
            button.style.opacity = '1';
        }
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

/**
 * Get active category.
 * Avoid calling getActiveCategoryWithFallback() first — that function may call
 * setActiveCategory() which triggers code paths that call getActiveCategory again
 * (stack overflow). Prefer DOM reads only here; use getActiveCategoryWithFallback
 * from callers that need a guaranteed non-null category.
 */
function getActiveCategory() {
    const activeItem = document.querySelector('.category-item.current, .category-item.active');
    if (activeItem) {
        const cat = activeItem.getAttribute('data-category');
        if (cat) return cat;
    }
    const activeModule = document.querySelector('.selected-module.active');
    if (activeModule) {
        return activeModule.getAttribute('data-facet-id') || activeModule.textContent.trim();
    }
    if (typeof getActiveCategoryWithFallback === 'function') {
        return getActiveCategoryWithFallback();
    }
    return null;
}


