// Label and value formatting

function prettifyLabel(key) {
    if (!key) return '';

    // Helper to normalize key for translation lookup (e.g. "System Short Name" -> "systemShortName")
    const normalizeForTr = (k) => {
        return k.replace(/\./g, '') // Remove dots
            .split(/[\s_-]+/)
            .map((word, index) => {
                if (index === 0) return word.toLowerCase();
                return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
            })
            .join('');
    };

    const normalizedKey = normalizeForTr(key);

    // 1. Try translation
    if (window.I18n && typeof window.I18n.t === 'function') {
        // Try normalized key first (e.g. label.systemShortName)
        const trKey = 'label.' + normalizedKey;
        const translated = window.I18n.t(trKey);
        if (translated !== trKey) return translated;

        // Try original key as fallback (e.g. label.System Short Name)
        const trKeyOrig = 'label.' + key;
        const translatedOrig = window.I18n.t(trKeyOrig);
        if (translatedOrig !== trKeyOrig) return translatedOrig;
    }

    // Special handling for common field acronyms/display names if no translation found
    const specialLabels = {
        'id': 'ID',
        'ref': 'Ref.',
        'cia_rating': 'CIA Rating',
        'kde': 'KDE',
        'budg_status': 'BUDG Status',
        'budg_viewing': 'BUDG Viewing'
    };

    const lowerKey = key.toLowerCase().replace(/[.\s_-]+/g, '_');
    if (specialLabels[lowerKey]) return specialLabels[lowerKey];
    if (specialLabels[normalizedKey.toLowerCase()]) return specialLabels[normalizedKey.toLowerCase()];

    // Fallback: If it already has spaces and no translation, return as-is
    if (/\s/.test(key)) return key;

    // Convert camelCase or snake_case to Title Case with spaces
    const spaced = key
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .replace(/[_-]+/g, ' ')
        .trim();

    return spaced.split(' ').map(s => s.charAt(0).toUpperCase() + s.slice(1).toLowerCase()).join(' ');
}

function formatDateDisplay(dateValue) {
    if (!dateValue || dateValue === 'undefined' || dateValue === 'null') {
        return '';
    }

    try {
        // Try to parse the date
        let date;

        // If it's already a Date object
        if (dateValue instanceof Date) {
            date = dateValue;
        } else {
            let s = String(dateValue).trim();
            // Java/Gson LocalDateTime: "yyyy-MM-dd HH:mm:ss" (not always parsed by Date in all engines)
            if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(s)) {
                s = s.replace(' ', 'T');
            }
            date = new Date(s);
        }

        // Check if date is valid
        if (isNaN(date.getTime())) {
            return dateValue; // Return original value if not a valid date
        }

        // Month abbreviations
        const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
            'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

        const day = date.getDate();
        const month = monthNames[date.getMonth()];
        const year = date.getFullYear();

        return `${day}-${month}-${year}`;
    } catch (error) {
        // If any error occurs, return original value
        return dateValue;
    }
}

function isDateField(fieldName) {
    if (!fieldName) return false;
    const n = String(fieldName).toLowerCase();
    // Person fields must never be formatted as dates ("Created By" contains "Created").
    if (n === 'created by' || n === 'last updated by' || n === 'last user change'
        || n.endsWith(' by') && (n.includes('created') || n.includes('updated') || n.includes('modified'))) {
        return false;
    }

    const dateKeywords = [
        'date', 'Date', 'DATE',
        'created', 'Created', 'CREATED',
        'updated', 'Updated', 'UPDATED',
        'modified', 'Modified', 'MODIFIED',
        'timestamp', 'Timestamp', 'TIMESTAMP',
        'time', 'Time', 'TIME'
    ];

    return dateKeywords.some(keyword => fieldName.includes(keyword));
}

/**
 * Render system impact for a dataset row
 * @param {Object} systemImpact - System impact object with facet types as keys and arrays of IDs as values
 * @returns {string} HTML string displaying the system impact
 */
function renderSystemImpact(systemImpact) {
    if (!systemImpact || typeof systemImpact !== 'object' || Object.keys(systemImpact).length === 0) {
        return '<span class="no-impact">No impact data</span>';
    }

    const impactTypes = {
        'PRODUCT': 'Products',
        'CLIENT': 'Clients',
        'LEGAL_ENTITY': 'Legal Entities',
        'PROCESS': 'Processes',
        'CAPABILITY': 'Capabilities',
        'PROJECT': 'Projects',
        'BUSINESS_AREA': 'Business Areas',
        'POLICY': 'Policies'
    };

    const impactItems = [];
    for (const [facetType, ids] of Object.entries(systemImpact)) {
        if (Array.isArray(ids) && ids.length > 0) {
            const displayName = impactTypes[facetType] || facetType;
            impactItems.push(`<span class="impact-item"><strong>${ids.length}</strong> ${displayName}</span>`);
        }
    }

    if (impactItems.length === 0) {
        return '<span class="no-impact">No impact relationships</span>';
    }

    return `<div class="system-impact-display">${impactItems.join(', ')}</div>`;
}

function formatCellValue(value, fieldName, row, currentCategory) {
    // Special handling for SystemImpact column
    if (fieldName === 'SystemImpact') {
        if (row.systemImpact) {
            return renderSystemImpact(row.systemImpact);
        }
        return '<span class="no-impact">No impact data</span>';
    }

    // Handle undefined, null, and empty values - show empty cell
    if (value == null || value === undefined || value === 'undefined') return '';
    if (typeof value === 'object') {
        try { return JSON.stringify(value); } catch (_) { return String(value); }
    }

    const stringValue = String(value);

    // Show empty cell for empty values (check early to avoid unnecessary processing)
    if (!stringValue || !stringValue.trim() || stringValue === 'undefined' || stringValue.trim() === '') {
        return '';
    }

    // Rich-text fields (Description/Definition/Business Logic/Examples/Usage/etc.)
    // should render stored HTML as formatted content instead of raw tags.
    if (shouldRenderRichHtmlField(fieldName) && hasHtmlMarkup(stringValue)) {
        return renderRichHtmlCell(stringValue);
    }

    // TRANSLATION for specific columns (Status, Lifecycle, Type, Viewing, etc.)
    // Check if field match (case-insensitive)
    const lowerField = fieldName.toLowerCase();
    if (['status', 'lifecycle', 'type', 'format_type', 'viewing', 'visualization'].includes(lowerField)) {
        if (window.I18n && typeof window.I18n.t === 'function') {
            // Only translate if value is not empty
            const trimmedValue = stringValue.toLowerCase().trim();
            if (trimmedValue) {
                // Create key: value.active, value.production, etc.
                const valKey = 'value.' + trimmedValue;
                const translatedVal = window.I18n.t(valKey);
                // If translation exists (doesn't return key), return it
                if (translatedVal !== valKey) {
                    return translatedVal;
                }
            }
        }
    }

    // Format date fields to "14-Sep-2025" format
    if (isDateField(fieldName)) {
        const formattedDate = formatDateDisplay(value);
        // If formatting succeeded and resulted in a date string, use it
        if (formattedDate && formattedDate !== value && formattedDate.includes('-')) {
            return formattedDate;
        }
    }

    // Special handling for CIA_Rating - show as string (e.g., "3-4-5")
    if (fieldName === 'CIA_Rating') {
        return stringValue;
    }

    // Special handling for numeric values that might be 0
    if (fieldName === 'ID' || fieldName === 'id') {
        const numericValue = parseFloat(value);
        if (!isNaN(numericValue)) {
            return numericValue.toString();
        }
    }

    // Check if this field should be clickable
    const module = categoryToModule(currentCategory);
    if (!module) return stringValue;

    // Special handling for attribute fields that link to dataset page
    if (module === 'attribute') {
        // Try multiple possible field names for dataset ID
        let datasetId = row['Data Set Name_ID'] || row.Dataset_ID || row.dataset_id || row.DatasetID || row.datasetId ||
            row.Dataset_Id || row['Dataset ID'] || row.DATASET_ID;

        // Cache functionality removed - Dataset_ID must be provided directly

        // For Name field in attribute, link to the parent dataset
        if (fieldName === 'Name') {
            if (datasetId) {
                const attributeId = row.ID || row.id;
                return createAttributeToDatasetLink(stringValue, datasetId, attributeId, fieldName);
            }
            // If no dataset ID, return plain text instead of linking to attribute view
            return stringValue;
        }

        // For Data Set Name field, link to the dataset page
        if (fieldName === 'Data Set Name') {
            if (datasetId) {
                return createDatasetLink(stringValue, datasetId);
            }
            // If no dataset ID, return plain text
            return stringValue;
        }
    }

    // Special handling for Object field in role - MUST come before isFieldClickable check
    if (module === 'role' && fieldName.toLowerCase() === 'object') {
        // Try multiple possible field names for Object Type and Object ID
        const objectType = row['Object Type'] || row['ObjectType'] || row['object_type'] || row['objectType'];
        const objectId = row['Object_ID'] || row['ObjectID'] || row['object_id'] || row['objectId'];

        if (objectType && objectId) {
            // Normalize object type (trim, lowercase, handle spaces and hyphens)
            const normalizedObjectType = String(objectType).trim().toLowerCase()
                .replace(/\s+/g, ' ') // Normalize multiple spaces to single space
                .replace(/-/g, ' '); // Replace hyphens with spaces for consistency

            // Map Object Type to module name - expanded mapping
            const objectTypeToModule = {
                'glossary': 'glossary',
                'data sets': 'dataset',
                'dataset': 'dataset',
                'datasets': 'dataset',
                'system': 'system',
                'systems': 'system',
                'process': 'process',
                'processes': 'process',
                'project': 'project',
                'projects': 'project',
                'product': 'product',
                'products': 'product',
                'attribute': 'attribute',
                'attributes': 'attribute',
                'policy': 'policy',
                'policies': 'policy',
                'interface': 'interface',
                'interfaces': 'interface',
                'system-interface': 'interface',
                'system interface': 'interface',
                'regulation': 'regulation',
                'regulations': 'regulation',
                'committee': 'committee',
                'committees': 'committee',
                'client': 'client',
                'clients': 'client',
                'legal entity': 'legal-entity',
                'legal-entity': 'legal-entity',
                'legalentity': 'legal-entity',
                'business area': 'business-area',
                'business-area': 'business-area',
                'businessarea': 'business-area',
                'capability': 'capability',
                'capabilities': 'capability',
                'regulatory theme': 'regulatory-theme',
                'regulatory-theme': 'regulatory-theme',
                'regulatorytheme': 'regulatory-theme',
                'geography': 'geography',
                'geographies': 'geography',
                'regulator': 'regulator',
                'regulators': 'regulator'
            };

            const targetModule = objectTypeToModule[normalizedObjectType];

            if (targetModule) {
                return createClickableLink(stringValue, targetModule, objectId, fieldName, 'reference-entity');
            }
        }
    }

    // Special handling for Full Name field in role - MUST come before isFieldClickable check
    // because 'Full Name' is in CLICKABLE_FIELDS_MAPPING for role
    if (module === 'role' && fieldName.toLowerCase() === 'full name') {
        // Try multiple possible field names for Full Name ID
        const fullNameId = row['Full Name_ID'] || row['FullName_ID'] || row['Full_Name_ID'] ||
            row['full_name_id'] || row['fullNameId'] || row['FullNameID'];
        if (fullNameId) {
            return createClickableLink(stringValue, 'people', fullNameId, fieldName, 'reference-entity');
        }
    }

    // Check if it's a clickable field for the current entity
    // BUT skip fields that are reference fields pointing to a parent/related entity,
    // because getReferenceId returns the current row's own ID, not the parent's ID.
    // Those reference fields (Parent, Parent Name, Parent Short Name, etc.) are
    // handled correctly by getReferenceTargetModule and specific handlers below.
    if (isFieldClickable(module, fieldName)) {
        const referenceTarget = getReferenceTargetModule(module, fieldName);
        // Only use the generic handler for "self" fields (fields that link to the
        // current entity's own view, like Name or Short Name), not reference fields
        // that link to a related/parent entity.
        if (!referenceTarget) {
            const entityId = getReferenceId(row, module, fieldName);
            if (entityId) {
                return createClickableLink(stringValue, module, entityId, fieldName, 'current-entity');
            }
        }
    }

    // Check if it's a reference field to another entity
    const targetModule = getReferenceTargetModule(module, fieldName);
    if (targetModule) {
        // Special handling for fields with _ID suffix
        const idFieldName = fieldName + '_ID';
        let refId = row[idFieldName];
        // Same-entity links (e.g. Data Sets Name -> dataset view): Unison rows often omit Name_ID but include id
        if (!refId && targetModule === module) {
            refId = row.ID ?? row.id ?? row.Id;
        }
        if (refId) {
            return createClickableLink(stringValue, targetModule, refId, fieldName, 'reference-entity');
        }

        // Special handling for Created By field - use Created By_ID directly
        if (fieldName === 'Created By' && row['Created By_ID']) {
            return createClickableLink(stringValue, targetModule, row['Created By_ID'], fieldName, 'reference-entity');
        }

        // Legacy support for Created_By (with underscore)
        if (fieldName === 'Created_By' && row['Created_By_ID']) {
            return createClickableLink(stringValue, targetModule, row['Created_By_ID'], fieldName, 'reference-entity');
        }

        // Special handling for fields with spaces that use underscore in _ID field
        const idFieldNameWithUnderscore = fieldName.replace(/\s+/g, '_') + '_ID';
        if (row[idFieldNameWithUnderscore]) {
            return createClickableLink(stringValue, targetModule, row[idFieldNameWithUnderscore], fieldName, 'reference-entity');
        }

        // Skip creating links for fields that commonly cause 500 errors
        const problematicFields = ['Last_Updated_By'];
        if (problematicFields.includes(fieldName)) {
            // For problematic fields, just return the text without creating links
            return stringValue;
        }

        // Cache functionality removed - return as plain text to avoid 500 errors
        return stringValue;
    }

    // Special handling for Name field in dataset - link to dataset page (fallback if reference block above missed)
    if (module === 'dataset' && fieldName === 'Name') {
        const entityId = row['Name_ID'] || row.ID || row.id || row.Id;
        if (entityId) {
            return createClickableLink(stringValue, 'dataset', entityId, fieldName, 'current-entity');
        }
    }

    // Special handling for Name field in attribute - link to attribute page (if Name_ID exists)
    if (module === 'attribute' && fieldName === 'Name' && row['Name_ID']) {
        // But only if not already handled by attribute-to-dataset link above
        // This is a fallback in case the attribute-to-dataset link logic didn't work
        return stringValue;
    }

    // Special handling for Short Name field in system - link to system page
    if (module === 'system' && fieldName === 'Short Name' && row['Short Name_ID']) {
        return createClickableLink(stringValue, 'system', row['Short Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Name field in glossary - link to glossary page
    if (module === 'glossary' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'glossary', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent Name field in glossary - link to glossary page (or show "Top Level")
    if (module === 'glossary' && fieldName === 'Parent Name') {
        if (row['Parent Name_ID']) {
            return createClickableLink(stringValue, 'glossary', row['Parent Name_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for First Name field in people - link to people page
    if (module === 'people' && fieldName === 'First Name' && row['First Name_ID']) {
        return createClickableLink(stringValue, 'people', row['First Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Last Name field in people - link to people page
    if (module === 'people' && fieldName === 'Last Name' && row['Last Name_ID']) {
        return createClickableLink(stringValue, 'people', row['Last Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in business-area - show "Top Level" if no parent ID
    if (module === 'business-area' && fieldName === 'Parent' && !row['Parent_ID'] && stringValue === 'Top Level') {
        return 'Top Level';
    }

    // Special handling for Parent field in client - show "Top Level" if no parent ID
    if (module === 'client' && fieldName === 'Parent' && !row['Parent_ID'] && stringValue === 'Top Level') {
        return 'Top Level';
    }

    // Special handling for Name field in capability - link to capability page
    if (module === 'capability' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'capability', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in capability - link to capability page (or show "Top Level")
    if (module === 'capability' && fieldName === 'Parent') {
        if (row['Parent_ID']) {
            return createClickableLink(stringValue, 'capability', row['Parent_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Name field in orgunit - link to orgunit page
    if (module === 'orgunit' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'orgunit', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in orgunit - link to orgunit page (or show "Top Level")
    if (module === 'orgunit' && fieldName === 'Parent') {
        if (row['Parent_ID']) {
            return createClickableLink(stringValue, 'orgunit', row['Parent_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Name field in process - link to process page
    if (module === 'process' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'process', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Name field in interface - link to interface page
    if (module === 'interface' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'interface', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent Name field in process - link to process page (or show "Top Level")
    if (module === 'process' && fieldName === 'Parent Name') {
        if (row['Parent Name_ID']) {
            return createClickableLink(stringValue, 'process', row['Parent Name_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }


    // Special handling for Short Name field in legal-entity - link to legal-entity page
    if (module === 'legal-entity' && fieldName === 'Short Name' && row['Short Name_ID']) {
        return createClickableLink(stringValue, 'legal-entity', row['Short Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Long Name field in legal-entity - link to legal-entity page
    if (module === 'legal-entity' && fieldName === 'Long Name' && row['Long Name_ID']) {
        return createClickableLink(stringValue, 'legal-entity', row['Long Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent Short Name field in legal-entity - link to legal-entity page (or show "Top Level")
    if (module === 'legal-entity' && fieldName === 'Parent Short Name') {
        if (row['Parent Short Name_ID']) {
            return createClickableLink(stringValue, 'legal-entity', row['Parent Short Name_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Parent Long Name field in legal-entity - link to legal-entity page (or show "Top Level")
    if (module === 'legal-entity' && fieldName === 'Parent Long Name') {
        if (row['Parent Long Name_ID']) {
            return createClickableLink(stringValue, 'legal-entity', row['Parent Long Name_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Name field in product - link to product page
    if (module === 'product' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'product', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Long Name field in product - link to product page
    if (module === 'product' && fieldName === 'Long Name' && row['Long Name_ID']) {
        return createClickableLink(stringValue, 'product', row['Long Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in product - link to product page (or show "Top Level")
    if (module === 'product' && fieldName === 'Parent') {
        if (row['Parent_ID']) {
            return createClickableLink(stringValue, 'product', row['Parent_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Name field in geography - link to geography page
    if (module === 'geography' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'geography', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in geography - link to geography page (or show "Top Level")
    if (module === 'geography' && fieldName === 'Parent') {
        if (row['Parent_ID']) {
            return createClickableLink(stringValue, 'geography', row['Parent_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Name field in regulation - link to regulation page
    if (module === 'regulation' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'regulation', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in regulation - link to regulation page (or show "Top Level")
    if (module === 'regulation' && fieldName === 'Parent') {
        if (row['Parent_ID']) {
            return createClickableLink(stringValue, 'regulation', row['Parent_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for Name field in regulator - link to regulator page
    if (module === 'regulator' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'regulator', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Name field in regulatory-theme - link to regulatory-theme page
    if (module === 'regulatory-theme' && fieldName === 'Name' && row['Name_ID']) {
        return createClickableLink(stringValue, 'regulatory-theme', row['Name_ID'], fieldName, 'current-entity');
    }

    // Special handling for Parent field in regulatory-theme - link to regulatory-theme page (or show "Top Level")
    if (module === 'regulatory-theme' && fieldName === 'Parent') {
        if (row['Parent_ID']) {
            return createClickableLink(stringValue, 'regulatory-theme', row['Parent_ID'], fieldName, 'reference-entity');
        }
        // If no parent ID but value is "Top Level", just return it as text
        return stringValue;
    }

    // Special handling for change-requests category
    if (module === 'change-requests') {
        // Subject field - link to change request view page
        if (fieldName === 'Subject' && row['Subject_ID']) {
            const crId = row['Subject_ID'];
            const escapedSubject = escapeHtml(stringValue);
            return `<a href="/view/change-request/change-request-view.html?id=${encodeURIComponent(crId)}" style="color: #248567; text-decoration: none;" title="View Change Request">${escapedSubject}</a>`;
        }

        // Object field - link to the referenced object
        if (fieldName === 'Object' && row['Object Type'] && row['Object_ID']) {
            const objectType = String(row['Object Type']).toLowerCase().trim();
            const objectId = row['Object_ID'];

            // Map Object Type to module name
            const objectTypeToModule = {
                'glossary': 'glossary',
                'data set': 'dataset',
                'dataset': 'dataset',
                'datasets': 'dataset',
                'system': 'system',
                'systems': 'system',
                'process': 'process',
                'processes': 'process',
                'project': 'project',
                'projects': 'project',
                'product': 'product',
                'products': 'product',
                'attribute': 'attribute',
                'attributes': 'attribute',
                'policy': 'policy',
                'policies': 'policy',
                'interface': 'interface',
                'interfaces': 'interface',
                'system-interface': 'interface',
                'system interface': 'interface',
                'regulation': 'regulation',
                'regulations': 'regulation',
                'committee': 'committee',
                'committees': 'committee',
                'client': 'client',
                'clients': 'client',
                'legal entity': 'legal-entity',
                'legal-entity': 'legal-entity',
                'legalentity': 'legal-entity',
                'business area': 'business-area',
                'business-area': 'business-area',
                'businessarea': 'business-area',
                'capability': 'capability',
                'capabilities': 'capability',
                'regulatory theme': 'regulatory-theme',
                'regulatory-theme': 'regulatory-theme',
                'regulatorytheme': 'regulatory-theme',
                'geography': 'geography',
                'geographies': 'geography',
                'regulator': 'regulator',
                'regulators': 'regulator',
                'people': 'people',
                'role': 'role'
            };

            const targetModule = objectTypeToModule[objectType];
            if (targetModule && objectId) {
                // Use the Object value (which should be the name, not ID)
                return createClickableLink(stringValue, targetModule, objectId, fieldName, 'reference-entity');
            }
        }
    }

    return stringValue;
}

// Helper function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function hasHtmlMarkup(value) {
    if (!value) return false;
    return /<[a-z][\s\S]*>/i.test(String(value));
}

function shouldRenderRichHtmlField(fieldName) {
    if (!fieldName) return false;
    var normalized = String(fieldName).trim().toLowerCase();
    var richFields = new Set([
        'description',
        'definition',
        'examples',
        'business logic',
        'format description',
        'usage',
        'summary'
    ]);
    return richFields.has(normalized);
}

function renderRichHtmlCell(rawHtml) {
    var safeHtml = '';
    if (typeof window.sanitizeRichHtml === 'function') {
        safeHtml = window.sanitizeRichHtml(rawHtml);
    } else {
        // Defensive fallback if shared sanitizer isn't loaded yet.
        var t = document.createElement('template');
        t.innerHTML = String(rawHtml);
        t.content.querySelectorAll('script,style,iframe,object,embed,link,meta').forEach(function (e) {
            e.remove();
        });
        t.content.querySelectorAll('*').forEach(function (el) {
            Array.from(el.attributes).forEach(function (a) {
                var n = a.name.toLowerCase();
                var v = a.value || '';
                if (n.indexOf('on') === 0) el.removeAttribute(a.name);
                if ((n === 'href' || n === 'src') && /^\s*javascript:/i.test(v)) el.removeAttribute(a.name);
            });
        });
        safeHtml = t.innerHTML;
    }
    return '<div class="unison-rich-cell" style="max-width:100%;overflow-x:auto;word-break:break-word;">' + safeHtml + '</div>';
}


