// Table column logic (fully dynamic)

// Global cache for UNISON_DEFAULTS
let unisonDefaultsCache = null;

function invalidateUnisonDefaultsCache() {
    unisonDefaultsCache = null;
    defaultColumnsCache.clear();
}

// Load UNISON_DEFAULTS from API
async function loadUnisonDefaultsForColumns() {
    if (unisonDefaultsCache !== null) {
        return unisonDefaultsCache;
    }
    
    try {
        const response = await fetch('/UnisonSearch/api/defaults');
        if (response.ok) {
            unisonDefaultsCache = await response.json();
            // Clear column cache when defaults are reloaded
            defaultColumnsCache.clear();
            return unisonDefaultsCache;
        } else {
            console.error('[COLUMNS] Failed to load UNISON_DEFAULTS, status:', response.status);
        }
    } catch (e) {
        console.error('[COLUMNS] Exception loading UNISON_DEFAULTS:', e);
    }
    
    unisonDefaultsCache = {};
    defaultColumnsCache.clear();
    return unisonDefaultsCache;
}

/**
 * Default column widths for a category from UNISON_DEFAULTS (facet.columnWidths), if any.
 */
function getDefaultColumnWidthsFromUnisonDefaults(category) {
    if (!unisonDefaultsCache || !unisonDefaultsCache.facets) {
        return null;
    }
    let normalizedCategory = category;
    if (typeof categoryToModule === 'function') {
        normalizedCategory = categoryToModule(category);
    }
    const facetId = window.moduleNameToFacetId ?
        window.moduleNameToFacetId(normalizedCategory) :
        normalizedCategory.toUpperCase();
    const facet = unisonDefaultsCache.facets.find(function (f) { return f.id === facetId; });
    if (!facet || facet.columnWidths == null) {
        return null;
    }
    if (typeof facet.columnWidths === 'object' && !Array.isArray(facet.columnWidths)) {
        return Object.assign({}, facet.columnWidths);
    }
    return null;
}

// Cache for default columns per category to avoid repeated lookups
const defaultColumnsCache = new Map();

// Get default columns for a category from UNISON_DEFAULTS
function getDefaultColumnsFromUnisonDefaults(category) {
    // Check cache first
    if (defaultColumnsCache.has(category)) {
        return defaultColumnsCache.get(category);
    }
    
    // Only log on cache miss to reduce console noise
    if (!unisonDefaultsCache || !unisonDefaultsCache.facets) {
        console.warn('[COLUMNS] No UNISON_DEFAULTS cache available');
        defaultColumnsCache.set(category, null);
        return null;
    }
    
    // Normalize category first using categoryToModule if available
    let normalizedCategory = category;
    if (typeof categoryToModule === 'function') {
        normalizedCategory = categoryToModule(category);
    }
    
    // Convert category to facet ID
    const facetId = window.moduleNameToFacetId ? 
        window.moduleNameToFacetId(normalizedCategory) : 
        normalizedCategory.toUpperCase();
    
    // Find the facet in the defaults
    const facet = unisonDefaultsCache.facets.find(f => f.id === facetId);
    if (!facet) {
        console.warn('[COLUMNS] Facet not found:', facetId, 'for category:', category);
        defaultColumnsCache.set(category, null);
        return null;
    }
    
    if (!facet.activeFields) {
        console.warn('[COLUMNS] No activeFields in facet:', facetId);
        defaultColumnsCache.set(category, null);
        return null;
    }
    
    // Parse activeFields (comma-separated string)
    let fields = facet.activeFields
        .split(',')
        .map(f => f.trim())
        .filter(f => f.length > 0);
    
    // Special handling for active-tasks: map display names to column keys
    if (facetId === 'ACTIVE_TASKS' || normalizedCategory === 'activeTasks' || category === 'active-tasks') {
        const columnMapping = {
            'Id': 'id',
            'Name': 'name',
            'Title': 'title',
            'Object Type': 'objectType',
            'Object': 'object',
            'Assign Date': 'assignDate',
            'Due Date': 'dueDate',
            'Due Days': 'dueInDays',
            'Due In (days)': 'dueInDays',
            'Due In': 'dueInDays',
            'Owner': 'owner',
            'Segments': 'segments',
            'Actions': 'actions'
        };
        
        // Map display names to column keys
        fields = fields.map(field => {
            // Try exact match first
            if (columnMapping[field]) {
                return columnMapping[field];
            }
            // Try case-insensitive match
            const lowerField = field.toLowerCase();
            for (const [displayName, columnKey] of Object.entries(columnMapping)) {
                if (displayName.toLowerCase() === lowerField) {
                    return columnKey;
                }
            }
            // If no match, return as-is (might be a column key already)
            return field.toLowerCase();
        }).filter(f => f.length > 0);
    }
    
    // Special handling for change-requests: Subject, Object, Segments (referenced object), Created By, etc.
    if (facetId === 'CHANGE_REQUESTS' || normalizedCategory === 'change-requests' || category === 'change-requests') {
        const columnMapping = {
            'ID': 'Id',
            'Id': 'Id',
            'id': 'Id',
            'Subject': 'Subject',
            'Summary': 'Summary',
            'Type': 'Type',
            'Object Type': 'Object Type',
            'Object': 'Object',
            'Status': 'Status',
            'Create Date': 'Create Date',
            'Created Date': 'Create Date',
            'Last Update Date': 'Last Update Date',
            'Last Updated Date': 'Last Update Date',
            'Severity': 'Severity',
            'Urgency': 'Urgency',
            'Segments': 'Segments',
            'Segment': 'Segments',
            'Created By': 'Created By'
        };
        
        // Map display names to exact column keys (preserve case and spacing)
        fields = fields.map(field => {
            // Try exact match first
            if (columnMapping[field]) {
                return columnMapping[field];
            }
            // Try case-insensitive match
            const lowerField = field.toLowerCase();
            for (const [displayName, columnKey] of Object.entries(columnMapping)) {
                if (displayName.toLowerCase() === lowerField) {
                    return columnKey;
                }
            }
            // If no match, return as-is (preserve original case and spacing)
            return field;
        }).filter(f => f.length > 0);
    }
    
    // Special handling for role: map camelCase/lowercase column names to display format
    if (facetId === 'ROLE' || normalizedCategory === 'role') {
        const columnMapping = {
            'role': 'Role',
            'Role': 'Role',
            'fullName': 'Full Name',
            'Full Name': 'Full Name',
            'FullName': 'Full Name',
            'full_name': 'Full Name',
            'objectType': 'Object Type',
            'Object Type': 'Object Type',
            'ObjectType': 'Object Type',
            'object_type': 'Object Type',
            'object': 'Object',
            'Object': 'Object',
            'roleAccepted': 'Role Accepted',
            'Role Accepted': 'Role Accepted',
            'RoleAccepted': 'Role Accepted',
            'role_accepted': 'Role Accepted',
            'ID': 'ID',
            'Id': 'ID',
            'id': 'ID',
            'Description': 'Description',
            'description': 'Description',
            'Role type': 'Role type',
            'Role Type': 'Role type',
            'roleType': 'Role type',
            'roletype': 'Role type',
            'role_type': 'Role type',
            'Date Accepted': 'Date Accepted',
            'dateAccepted': 'Date Accepted',
            'DateAccepted': 'Date Accepted',
            'date_accepted': 'Date Accepted'
        };
        
        // Map column names to display format
        const originalFields = [...fields];
        fields = fields.map(field => {
            // Try exact match first
            if (columnMapping[field]) {
                return columnMapping[field];
            }
            // Try case-insensitive match
            const lowerField = field.toLowerCase();
            for (const [dbName, displayName] of Object.entries(columnMapping)) {
                if (dbName.toLowerCase() === lowerField) {
                    return displayName;
                }
            }
            // If no match, try using fieldNameToColumnName if available
            if (typeof fieldNameToColumnName === 'function') {
                return fieldNameToColumnName(field);
            }
            // If still no match, return as-is (might be already in display format)
            return field;
        }).filter(f => f.length > 0);
        
        // Log the transformation for debugging
        if (originalFields.length > 0 && originalFields.some(f => f !== fields[originalFields.indexOf(f)])) {
            console.log('[COLUMNS] Role column mapping:', { original: originalFields, transformed: fields });
        }
    }
    
    const result = fields.length > 0 ? fields : null;
    // Cache the result
    defaultColumnsCache.set(category, result);
    return result;
}

// Allowed columns for dataset category
const DATASET_ALLOWED_COLUMNS = [
    'BUDG Status',
    'BUDG Viewing',
    'Created By',
    'Created Date',
    'Definition',
    'Glossary Name',
    'ID',
    'Last Approved Date',
    'Last Updated',
    'Lifecycle',
    'Name',
    'Ref.',
    'System Short Name',
    'Type',
    'Usage',
    'Segment'
];

// Default visible columns for dataset category (shown by default)
// These are fallback defaults; actual defaults come from UNISON_DEFAULTS
const DATASET_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Definition',
    'Lifecycle',
    'System Short Name'
];

// Allowed columns for attribute category
const ATTRIBUTE_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Definition',
    'Review Status',
    'Confidence Score (%)',
    'Data Set Name',
    'System Short Name',
    'Glossary Name',
    'DB Field Name',
    'Data Type ( Data Length )',
    'Origin',
    'Editability Role',
    'Editability',
    'Requirement',
    'Created By',
    'Created Date',
    'Last Updated',
    'Business Logic',
    'Segment'
];

// Default visible columns for attribute category (shown by default)
const ATTRIBUTE_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Definition',
    'Data Set Name',
    'System Short Name'
];

// Allowed columns for system category
const SYSTEM_ALLOWED_COLUMNS = [
    'ID',
    'Short Name',
    'Description',
    'Type',
    'URL',
    'External',
    'BUDG Status',
    'Long Name',
    'Parent Short Name',
    'Lifecycle',
    'Classification',
    'Created By',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'CIA Rating',
    'Asset ID',
    'Last Approved Date',
    'Segment'
];

// Default visible columns for system category (shown by default)
const SYSTEM_DEFAULT_COLUMNS = [
    'Short Name',
    'Description',
    'Type',
    'Lifecycle',
    'Classification',
    'CIA Rating'
];

// Allowed columns for glossary category
const GLOSSARY_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Type',
    'Definition',
    'Alias Names',
    'Parent Name',
    'Parent Type',
    'KDE',
    'Lifecycle',
    'BUDG Status',
    'Security Classification',
    'CIA Rating',
    'Created By',
    'Last Updated By',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'LDM Reference',
    'Business Logic',
    'Examples',
    'Format Description',
    'Format Type',
    'Last Approved Date',
    'Segment'
];

// Default visible columns for glossary category (shown by default)
const GLOSSARY_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Type',
    'Definition',
    'Parent Name',
    'Parent Type',
    'KDE',
    'Lifecycle'
];

// Allowed columns for people category
const PEOPLE_ALLOWED_COLUMNS = [
    'ID',
    'First Name',
    'Last Name',
    'Email',
    'Function',
    'Org Unit',
    'BUDG Status',
    'Profile Name',
    'System Role',
    'Last Login',
    'Lifecycle',
    'Employee Type',
    'LAN ID',
    'Created Date',
    'Last Updated'
];

// Default visible columns for people category (shown by default)
const PEOPLE_DEFAULT_COLUMNS = [
    'First Name',
    'Last Name',
    'Email',
    'Function',
    'Org Unit'
];

// Allowed columns for role category
const ROLE_ALLOWED_COLUMNS = [
    'ID',
    'Role',
    'Role type',
    'Description',
    'Full Name',
    'Object Type',
    'Object',
    'Role Accepted',
    'Date Accepted'
];

// Default visible columns for role category (shown by default)
const ROLE_DEFAULT_COLUMNS = [
    'ID',
    'Role',
    'Description',
    'Role type',
    'Full Name',
    'Object Type',
    'Object',
    'Role Accepted',
    'Date Accepted'
];

// Allowed columns for business-area category
const BUSINESS_AREA_ALLOWED_COLUMNS = [
    'ID',
    'Name',
    'Parent',
    'Description',
    'Lifecycle',
    'BUDG Status',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for business-area category (shown by default)
const BUSINESS_AREA_DEFAULT_COLUMNS = [
    'Name',
    'Parent',
    'Description'
];

// Allowed columns for client category
const CLIENT_ALLOWED_COLUMNS = [
    'ID',
    'Name',
    'Long Name',
    'Parent',
    'Description',
    'BUDG Status',
    'Lifecycle',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for client category (shown by default)
const CLIENT_DEFAULT_COLUMNS = [
    'Name',
    'Parent',
    'Description',
    'Lifecycle'
];

// Allowed columns for committee category
const COMMITTEE_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'Lifecycle',
    'BUDG Status',
    'Created Date',
    'Last Updated',
    'Classification',
    'Type',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for committee category (shown by default)
const COMMITTEE_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent',
    'Description'
];

// Allowed columns for policy category
const POLICY_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent Name',
    'Description',
    'Type',
    'BUDG Status',
    'Internal',
    'Lifecycle',
    'Effective Date',
    'End Date',
    'BUDG Viewing',
    'Created By',
    'Created Date',
    'Last Updated',
    'Segment'
];

// Default visible columns for policy category (shown by default)
const POLICY_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent Name',
    'Description',
    'Lifecycle'
];

// Allowed columns for process category
const PROCESS_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent Name',
    'Description',
    'Lifecycle',
    'Automation',
    'Classification',
    'BUDG Status',
    'Type',
    'BUDG Viewing',
    'Step Type',
    'Created By',
    'Created Date',
    'Last Updated',
    'Last Approved Date',
    'Segment'
];

// Default visible columns for process category (shown by default)
const PROCESS_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent Name',
    'Description'
];

// Allowed columns for interface category
const INTERFACE_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Description',
    'Source System Short Name',
    'Target System Short Name',
    'BUDG Status',
    'Automation',
    'Frequency',
    'Lifecycle',
    'Synchronisation',
    'Asset ID',
    'Classification',
    'Transfer Format',
    'Transfer Method',
    'Created By',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for interface category (shown by default)
const INTERFACE_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Description',
    'Source System Short Name',
    'Target System Short Name',
    'Automation',
    'Frequency',
    'Lifecycle'
];

// Allowed columns for capability category
const CAPABILITY_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'Lifecycle',
    'BUDG Status',
    'Classification',
    'Type',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for capability category (shown by default)
const CAPABILITY_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent',
    'Description'
];

// Allowed columns for legal category
const LEGAL_ALLOWED_COLUMNS = [
    'ID',
    'Short Name',
    'Long Name',
    'Parent Short Name',
    'Parent Long Name',
    'Description',
    'BUDG Status',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for legal category (shown by default)
const LEGAL_DEFAULT_COLUMNS = [
    'Short Name',
    'Parent Short Name',
    'Description'
];

// Allowed columns for orgunit category
const ORGUNIT_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'BUDG Status',
    'Created Date',
    'Last Updated'
];

// Default visible columns for orgunit category (shown by default)
const ORGUNIT_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'BUDG Status'
];

// Allowed columns for product category
const PRODUCT_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Long Name',
    'Parent',
    'Description',
    'BUDG Status',
    'Lifecycle',
    'Created Date',
    'Last Updated',
    'BUDG Viewing',
    'Segment'
];

// Default visible columns for product category (shown by default)
const PRODUCT_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'BUDG Status'
];

// Allowed columns for geography category
const GEOGRAPHY_ALLOWED_COLUMNS = [
    'ID',
    'Name',
    'Parent',
    'Description',
    'Created Date',
    'Last Updated',
    'Segment'
];

// Default visible columns for geography category (shown by default)
const GEOGRAPHY_DEFAULT_COLUMNS = [
    'Name',
    'Parent',
    'Description'
];

// Allowed columns for regulation category
const REGULATION_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'BUDG Status',
    'Stage',
    'Maturity',
    'Probability',
    'Compliance Level',
    'Compliance Date',
    'Publication Date',
    'Short Name',
    'Created Date',
    'Last Updated',
    'Segment'
];

// Default visible columns for regulation category (shown by default)
const REGULATION_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent',
    'Description'
];

// Allowed columns for regulator category
const REGULATOR_ALLOWED_COLUMNS = [
    'ID',
    'Name',
    'Description',
    'Short Name',
    'Created Date',
    'Last Updated',
    'Last Updated By',
    'Segment'
];

// Default visible columns for regulator category (shown by default)
const REGULATOR_DEFAULT_COLUMNS = [
    'Name',
    'Description',
    'Short Name'
];

// Allowed columns for regulatory-theme category
const REGULATORY_THEME_ALLOWED_COLUMNS = [
    'ID',
    'Ref.',
    'Name',
    'Parent',
    'Description',
    'BUDG Status',
    'Created Date',
    'Last Updated',
    'Segment'
];

// Default visible columns for regulatory-theme category (shown by default)
const REGULATORY_THEME_DEFAULT_COLUMNS = [
    'Ref.',
    'Name',
    'Parent',
    'Description'
];

// Allowed columns for change-requests category
const CHANGE_REQUESTS_ALLOWED_COLUMNS = [
    'Subject',
    'Summary',
    'Type',
    'Object',
    'Status',
    'Segments',
    'Created By'
];

// Default visible columns for change-requests category (shown by default)
const CHANGE_REQUESTS_DEFAULT_COLUMNS = [
    'Subject',
    'Summary',
    'Type',
    'Object',
    'Status',
    'Segments',
    'Created By'
];

function getColumnsForCategory(category, data) {
    if (!Array.isArray(data) || data.length === 0) {
        return [];
    }

    const firstRow = data[0];
    const orderedKeys = Object.keys(firstRow);

    // For dataset, attribute, system, glossary, and people categories, filter to only allowed columns and exclude _ID columns
    let filteredKeys = orderedKeys;
    const normalizedCategory = categoryToModule(category);
    if (normalizedCategory === 'dataset') {
        filteredKeys = orderedKeys.filter(key => 
            DATASET_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'attribute') {
        filteredKeys = orderedKeys.filter(key => 
            ATTRIBUTE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'system') {
        filteredKeys = orderedKeys.filter(key => 
            SYSTEM_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'glossary') {
        filteredKeys = orderedKeys.filter(key => 
            GLOSSARY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'people') {
        filteredKeys = orderedKeys.filter(key => 
            PEOPLE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'role') {
        filteredKeys = orderedKeys.filter(key => 
            ROLE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'business-area') {
        filteredKeys = orderedKeys.filter(key => 
            BUSINESS_AREA_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'client') {
        filteredKeys = orderedKeys.filter(key => 
            CLIENT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'committee') {
        filteredKeys = orderedKeys.filter(key => 
            COMMITTEE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'policy') {
        filteredKeys = orderedKeys.filter(key => 
            POLICY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'process') {
        filteredKeys = orderedKeys.filter(key => 
            PROCESS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'interface') {
        filteredKeys = orderedKeys.filter(key => 
            INTERFACE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'capability') {
        filteredKeys = orderedKeys.filter(key => 
            CAPABILITY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'legal-entity') {
        filteredKeys = orderedKeys.filter(key => 
            LEGAL_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'orgunit') {
        filteredKeys = orderedKeys.filter(key => 
            ORGUNIT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'product') {
        filteredKeys = orderedKeys.filter(key => 
            PRODUCT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'geography') {
        filteredKeys = orderedKeys.filter(key => 
            GEOGRAPHY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulation') {
        filteredKeys = orderedKeys.filter(key => 
            REGULATION_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulator') {
        filteredKeys = orderedKeys.filter(key => 
            REGULATOR_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulatory-theme') {
        filteredKeys = orderedKeys.filter(key => 
            REGULATORY_THEME_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'change-requests') {
        filteredKeys = orderedKeys.filter(key => 
            CHANGE_REQUESTS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    }

    const entries = filteredKeys.map(key => ({
        key: key,
        normKey: key, // Keep original key as normalized key
        label: prettifyLabel(key)
    }));

    return entries;
}

function applyColumnConfiguration(columns, category) {
    // Return all columns as-is for dynamic display
    return columns;
}

function getAvailableColumns(category, data) {
    if (!Array.isArray(data) || data.length === 0) return [];

    const firstRow = data[0];
    let allKeys = Object.keys(firstRow);
    
    // For dataset, attribute, system, glossary, and people categories, filter to only allowed columns and exclude _ID columns
    const normalizedCategory = categoryToModule(category);
    if (normalizedCategory === 'dataset') {
        allKeys = allKeys.filter(key => 
            DATASET_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'attribute') {
        allKeys = allKeys.filter(key => 
            ATTRIBUTE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'system') {
        allKeys = allKeys.filter(key => 
            SYSTEM_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'glossary') {
        allKeys = allKeys.filter(key => 
            GLOSSARY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'people') {
        allKeys = allKeys.filter(key => 
            PEOPLE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'role') {
        allKeys = allKeys.filter(key => 
            ROLE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'business-area') {
        allKeys = allKeys.filter(key => 
            BUSINESS_AREA_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'client') {
        allKeys = allKeys.filter(key => 
            CLIENT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'committee') {
        allKeys = allKeys.filter(key => 
            COMMITTEE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'policy') {
        allKeys = allKeys.filter(key => 
            POLICY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'process') {
        allKeys = allKeys.filter(key => 
            PROCESS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'interface') {
        allKeys = allKeys.filter(key => 
            INTERFACE_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'capability') {
        allKeys = allKeys.filter(key => 
            CAPABILITY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'legal-entity') {
        allKeys = allKeys.filter(key => 
            LEGAL_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'orgunit') {
        allKeys = allKeys.filter(key => 
            ORGUNIT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'product') {
        allKeys = allKeys.filter(key => 
            PRODUCT_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'geography') {
        allKeys = allKeys.filter(key => 
            GEOGRAPHY_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulation') {
        allKeys = allKeys.filter(key => 
            REGULATION_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulator') {
        allKeys = allKeys.filter(key => 
            REGULATOR_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'regulatory-theme') {
        allKeys = allKeys.filter(key => 
            REGULATORY_THEME_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'change-requests' && typeof CHANGE_REQUESTS_ALLOWED_COLUMNS !== 'undefined') {
        allKeys = allKeys.filter(key => 
            CHANGE_REQUESTS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    } else if (normalizedCategory === 'activeTasks' || category === 'active-tasks') {
        // For Active Tasks, use predefined columns
        const ACTIVE_TASKS_ALLOWED_COLUMNS = [
            'id', 'name', 'title', 'objectType', 'object', 
            'assignDate', 'dueDate', 'dueInDays', 'owner', 'segments', 'actions'
        ];
        allKeys = allKeys.filter(key => 
            ACTIVE_TASKS_ALLOWED_COLUMNS.includes(key) && !key.endsWith('_ID')
        );
    }
    
    return allKeys.map(key => ({
        key: key,
        normKey: key,
        label: prettifyLabel(key)
    }));
}

// Make functions available globally for console access
window.searchColumnControl = {
    getAvailableColumns,
    loadUnisonDefaultsForColumns,
    getDefaultColumnsFromUnisonDefaults,
    getDefaultColumnWidthsFromUnisonDefaults,
    invalidateUnisonDefaultsCache
};


