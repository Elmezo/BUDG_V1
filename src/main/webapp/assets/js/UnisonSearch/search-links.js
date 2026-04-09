// Entity links and navigation

// Mapping of modules to their view pages
// All view pages use the pattern: /view/{entity}/{id}
const MODULE_VIEW_MAPPING = {
    'dataset': '/view/dataset/',
    'attribute': '/view/attribute/', // Assuming this exists
    'system': '/view/system/',
    'glossary': '/view/glossary/',
    'people': '/view/people/',
    'interface': '/view/system-interface/',
    'orgunit': '/view/org-unit/',
    'process': '/view/process/',
    'project': '/view/project/',
    'product': '/view/product/',
    'policy': '/view/policy/',
    'legal-entity': '/view/LegalEntity/legal-entity.html?id=',
    'business-area': '/view/business-area/business-area.html?id=',
    'capability': '/view/capability/',
    'client': '/view/client/client.html?id=',
    'committee': '/view/committee/',
    'role': '/view/role/',
    'geography': '/view/geography/geography.html?id=',
    'regulation': '/view/regulation/regulation.html?id=',
    'regulator': '/view/regulator/regulator.html?id=',
    'regulatory-theme': '/view/regulatory-theme/regulatory-theme.html?id='
};

// Fields that should be clickable links for each module
const CLICKABLE_FIELDS_MAPPING = {
    'dataset': ['Name'],
    'role': ['Full Name', 'Object'],
    'attribute': ['Name', 'Data Set Name'], // Name links to attribute in dataset, Data Set Name links to dataset
    'system': ['Short Name', 'Long Name', 'Parent Short Name'],
    'glossary': ['Name', 'Ref.'],
    'people': ['First Name', 'Last Name'],
    'interface': ['Name'],
    'orgunit': ['Name', 'Parent'],
    'process': ['Name', 'Ref.', 'Parent Name'],
    'project': ['Name', 'Ref', 'Parent'],
    'product': ['Name', 'Long Name', 'Parent'],
    'policy': ['Name'],
    'legal-entity': ['Short Name', 'Long Name', 'Parent Short Name', 'Parent Long Name'],
    'business-area': ['Name', 'Parent'],
    'capability': ['Name', 'Parent'],
    'client': ['Name', 'Parent'],
    'committee': ['Name', 'Ref', 'Parent'],
    'geography': ['Name', 'Parent'],
    'regulation': ['Name', 'Parent'],
    'regulator': ['Name'],
    'regulatory-theme': ['Name', 'Parent']
};

function navigateToView(module, id, fieldName) {
    // Check if this is a placeholder ID (still loading)
    if (id && id.toString().startsWith('ref-')) {
        return;
    }

    if (!id || id === 'null' || id === 'undefined') {
        return;
    }

    // Special handling for attribute - attributes don't have their own view page
    // They need to link to dataset with attribute highlighted
    if (module === 'attribute') {
        // Fetch dataset ID for this attribute using UnisonSearch API
        fetch(`/UnisonSearch/attribute?q=${encodeURIComponent(id)}`)
            .then(response => response.json())
            .then(data => {
                // The API returns an array of attributes, find the one matching our ID
                if (Array.isArray(data) && data.length > 0) {
                    const attribute = data.find(attr => (attr.ID || attr.id) == id);
                    if (attribute && (attribute.Dataset_ID || attribute.DatasetID || attribute['Data Set Name_ID'])) {
                        const datasetId = attribute.Dataset_ID || attribute.DatasetID || attribute['Data Set Name_ID'];
                        const url = `/view/dataset/${encodeURIComponent(datasetId)}?tab=attribute&attributeId=${encodeURIComponent(id)}`;
                        window.open(url, '_blank');
                        return;
                    }
                }
                // Fallback: try to open attribute view anyway (might not work)
                const viewPath = MODULE_VIEW_MAPPING[module];
                if (viewPath) {
                    const url = viewPath.endsWith('?id=') 
                        ? `${viewPath}${encodeURIComponent(id)}`
                        : `${viewPath}${encodeURIComponent(id)}`;
                    window.open(url, '_blank');
                }
            })
            .catch(error => {
                console.error('Error fetching attribute dataset ID:', error);
                // Fallback: try to open attribute view anyway
                const viewPath = MODULE_VIEW_MAPPING[module];
                if (viewPath) {
                    const url = viewPath.endsWith('?id=') 
                        ? `${viewPath}${encodeURIComponent(id)}`
                        : `${viewPath}${encodeURIComponent(id)}`;
                    window.open(url, '_blank');
                }
            });
        return;
    }

    const viewPath = MODULE_VIEW_MAPPING[module];
    if (!viewPath) {
        return;
    }

    // Build URL - some use query params (end with ?id=), others use path pattern
    const url = viewPath.endsWith('?id=') 
        ? `${viewPath}${encodeURIComponent(id)}`
        : `${viewPath}${encodeURIComponent(id)}`;

    window.open(url, '_blank');
}

function isFieldClickable(module, fieldName) {
    if (!module || !fieldName) return false;
    const clickableFields = CLICKABLE_FIELDS_MAPPING[module] || [];
    return clickableFields.some(f => f.toLowerCase() === fieldName.toLowerCase());
}

function getReferenceId(row, module, fieldName) {
    // For reference fields, we need to get the ID from the row
    // This might need to be adjusted based on your data structure
    const idFieldMap = {
        'dataset': 'ID',
        'attribute': 'ID',
        'system': 'ID',
        'glossary': 'id',
        'people': 'ID',
        'interface': 'ID',
        'orgunit': 'ID',
        'process': 'ID',
        'project': 'ID',
        'product': 'ID',
        'policy': 'ID',
        'legal-entity': 'ID',
        'business-area': 'ID',
        'capability': 'ID',
        'client': 'ID',
        'committee': 'ID',
        'role': 'ID',
        'geography': 'ID',
        'regulation': 'ID',
        'regulator': 'ID',
        'regulatory-theme': 'ID'
    };

    const idField = idFieldMap[module];
    if (!idField) return null;
    const v = row[idField];
    if (v !== undefined && v !== null && v !== '') return v;
    if (idField === 'ID' || idField === 'id') {
        return row.ID ?? row.id ?? row.Id ?? null;
    }
    return null;
}

// Helper function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function createClickableLink(text, module, id, fieldName, linkType) {
    const linkClass = `clickable-link ${linkType}`;
    const title = `View ${module} details`;
    
    // Escape HTML to prevent XSS
    const escapedText = escapeHtml(text);

    // Build actual navigation URL so links work without relying on window.open
    const viewPath = MODULE_VIEW_MAPPING[module];
    let href = '#';
    if (viewPath && id && id !== 'null' && id !== 'undefined') {
        href = viewPath.endsWith('?id=')
            ? `${viewPath}${encodeURIComponent(id)}`
            : `${viewPath}${encodeURIComponent(id)}`;
    }

    return `<a href="${href}" target="_blank" class="${linkClass}"
                data-module="${module}"
                data-id="${id}"
                data-field="${fieldName}"
                title="${title}">${escapedText}</a>`;
}

function createAttributeToDatasetLink(text, datasetId, attributeId, fieldName) {
    const url = `/view/dataset/${encodeURIComponent(datasetId)}?tab=attribute&attributeId=${encodeURIComponent(attributeId)}&modal=true`;
    const title = `View attribute in dataset`;

    return `<a href="${url}"
                class="clickable-link attribute-to-dataset"
                data-dataset-id="${datasetId}"
                data-attribute-id="${attributeId}"
                data-field="${fieldName}"
                title="${title}"
                target="_blank">${text}</a>`;
}

function createDatasetLink(text, datasetId) {
    const url = `/view/dataset/${encodeURIComponent(datasetId)}`;
    const title = `View dataset details`;

    return `<a href="${url}"
                class="clickable-link dataset-link"
                data-dataset-id="${datasetId}"
                title="${title}"
                target="_blank">${text}</a>`;
}

// Fields that reference other entities and should link to their views
const REFERENCE_FIELDS_MAPPING = {
    'dataset': {
        'Name': 'dataset',
        'System Short Name': 'system',
        'Glossary Name': 'glossary',
        'Created By': 'people',
        'Last_Updated_By': 'people'
    },
    'attribute': {
        'Name': 'attribute',
        'Data Set Name': 'dataset',
        'System Short Name': 'system',
        'Glossary Name': 'glossary',
        'Created By': 'people'
    },
    'system': {
        'Short Name': 'system',
        'Parent Short Name': 'system',
        'Created By': 'people'
    },
    'glossary': {
        'Name': 'glossary',
        'Parent Name': 'glossary',
        'Created By': 'people',
        'Last Updated By': 'people'
    },
    'people': {
        'First Name': 'people',
        'Last Name': 'people',
        'Org Unit': 'orgunit'
    },
    'interface': {
        'Name': 'interface',
        'Source System Short Name': 'system',
        'Target System Short Name': 'system',
        'Created By': 'people'
    },
    'orgunit': {
        'Name': 'orgunit',
        'Parent': 'orgunit'
    },
    'process': {
        'Name': 'process',
        'Parent Name': 'process',
        'Created By': 'people'
    },
    'project': {
        'Parent': 'project',
        'Created_By': 'people',
        'Last_Updated_By': 'people'
    },
    'product': {
        'Name': 'product',
        'Long Name': 'product',
        'Parent': 'product'
    },
    'policy': {
        'Parent Name': 'policy',
        'Created By': 'people'
    },
    'legal-entity': {
        'Short Name': 'legal-entity',
        'Long Name': 'legal-entity',
        'Parent Short Name': 'legal-entity',
        'Parent Long Name': 'legal-entity'
    },
    'business-area': {
        'Name': 'business-area',
        'Parent': 'business-area'
    },
    'capability': {
        'Name': 'capability',
        'Parent': 'capability'
    },
    'client': {
        'Name': 'client',
        'Parent': 'client'
    },
    'committee': {
        'Name': 'committee',
        'Parent': 'committee'
    },
    'role': {
        'Full Name': 'people',
        'Object': {
            'glossary': 'glossary',
            'dataset': 'dataset',
            'system': 'system',
            'process': 'process',
            'project': 'project',
            'product': 'product',
            'attribute': 'attribute',
            'policy': 'policy',
            'interface': 'interface',
            'regulation': 'regulation',
            'committee': 'committee',
            'client': 'client',
            'legal-entity': 'legal-entity',
            'business-area': 'business-area',
            'capability': 'capability'
        }
    },
    'geography': {
        'Name': 'geography',
        'Parent': 'geography'
    },
    'regulation': {
        'Name': 'regulation',
        'Parent': 'regulation',
        'Created_By': 'people'
    },
    'regulator': {},
    'regulatory-theme': {
        'Name': 'regulatory-theme',
        'Parent': 'regulatory-theme'
    }
};

function getReferenceTargetModule(module, fieldName) {
    if (!module || !fieldName) return null;
    
    // Special handling for Object field in role - it needs to be handled differently
    // based on Object Type, so return null to let special handling in formatCellValue handle it
    if (module === 'role' && fieldName.toLowerCase() === 'object') {
        return null;
    }
    
    const referenceFields = REFERENCE_FIELDS_MAPPING[module];
    if (!referenceFields) return null;

    // تطابق بدون حساسية حالة الحروف
    const normalizedKey = Object.keys(referenceFields).find(
        key => key.toLowerCase() === fieldName.toLowerCase()
    );
    const target = normalizedKey ? referenceFields[normalizedKey] : null;
    
    // If target is an object (like Object field in role), return null to let special handling take over
    if (target && typeof target === 'object' && !Array.isArray(target)) {
        return null;
    }
    
    return target;
}


