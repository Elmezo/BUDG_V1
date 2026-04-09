// Dashboard functionality for unison search

let dashboardCharts = {}; // Store chart instances to destroy them when needed
let dashboardActiveFilters = { // Track active filters for cross-chart filtering
    type: new Set(),
    lifecycle: new Set(),
    status: new Set(),
    security: new Set(),
    editability: new Set(),
    mandatory: new Set(),
    origin: new Set(),
    classification: new Set(),
    orgUnit: new Set(),
    profile: new Set(),
    automation: new Set(),
    level: new Set(),
    frequency: new Set(),
    stage: new Set(),
    maturity: new Set(),
    probability: new Set(),
    complianceLevel: new Set(),
    roleType: new Set(),
    objectType: new Set(),
    roleAccepted: new Set()
};

/**
 * Update dashboard with filtered data from search results
 * @param {string} category - The category name
 * @param {Array} filteredData - The filtered data array from search
 */
async function updateDashboardWithFilteredData(category, filteredData) {
    if (!filteredData || !Array.isArray(filteredData)) {
        console.warn('No filtered data provided for dashboard update');
        return;
    }

    console.log('Updating dashboard with filtered data:', filteredData.length, 'items');

    // Generate statistics from filtered data
    const stats = generateStatsFromData(category, filteredData);

    // Get the dashboard container
    const contentMain = document.querySelector('.content-main');
    if (!contentMain) {
        console.error('Content main container not found');
        return;
    }

    // Hide the data-table-wrapper if it exists
    const dataTableWrapper = document.querySelector('.data-table-wrapper');
    if (dataTableWrapper) {
        dataTableWrapper.style.display = 'none';
    }

    // Get or create the dashboard container
    let dashboardContainer = contentMain.querySelector('.dashboard-container');

    if (!dashboardContainer) {
        // Create dashboard container if it doesn't exist
        dashboardContainer = document.createElement('div');
        dashboardContainer.className = 'dashboard-container';
        contentMain.appendChild(dashboardContainer);
    } else {
        // Clear existing content
        dashboardContainer.innerHTML = '';
    }

    // Update dashboard with filtered stats
    const module = categoryToModule(category);
    if (module === 'glossary') {
        renderGlossaryDashboard(dashboardContainer, stats);
    } else if (module === 'dataset') {
        renderDatasetDashboard(dashboardContainer, stats);
    } else if (module === 'attribute') {
        renderAttributeDashboard(dashboardContainer, stats);
    } else if (module === 'system') {
        renderSystemDashboard(dashboardContainer, stats);
    } else if (module === 'people') {
        renderPeopleDashboard(dashboardContainer, stats);
    } else if (module === 'business-area') {
        renderBusinessAreaDashboard(dashboardContainer, stats);
    } else if (module === 'client') {
        renderClientDashboard(dashboardContainer, stats);
    } else if (module === 'project') {
        renderProjectDashboard(dashboardContainer, stats);
    } else if (module === 'committee') {
        renderCommitteeDashboard(dashboardContainer, stats);
    } else if (module === 'policy') {
        renderPolicyDashboard(dashboardContainer, stats);
    } else if (module === 'process') {
        renderProcessDashboard(dashboardContainer, stats);
    } else if (module === 'interface') {
        renderInterfaceDashboard(dashboardContainer, stats);
    } else if (module === 'legal-entity') {
        renderLegalEntityDashboard(dashboardContainer, stats);
    } else if (module === 'orgunit') {
        renderOrgUnitDashboard(dashboardContainer, stats);
    } else if (module === 'product') {
        renderProductDashboard(dashboardContainer, stats);
    } else if (module === 'capability') {
        renderCapabilityDashboard(dashboardContainer, stats);
    } else if (module === 'regulation') {
        renderRegulationDashboard(dashboardContainer, stats);
    } else if (module === 'regulatory-theme') {
        renderRegulatoryThemeDashboard(dashboardContainer, stats);
    } else if (module === 'role') {
        renderRoleDashboard(dashboardContainer, stats);
    }
}

/**
 * Generate statistics from filtered data array
 * @param {string} category - The category name
 * @param {Array} data - The data array
 * @returns {Object} Statistics object
 */
function generateStatsFromData(category, data) {
    if (!data || data.length === 0) {
        console.warn('No data provided to generateStatsFromData');
        return {
            totalCount: 0,
            lifecycle: [],
            type: [],
            status: [],
            security: [],
            editability: [],
            mandatory: [],
            origin: [],
            classification: [],
            orgUnit: [],
            profile: [],
            automation: [],
            level: [],
            frequency: [],
            stage: [],
            maturity: [],
            probability: [],
            complianceLevel: [],
            roleType: [],
            objectType: [],
            roleAccepted: []
        };
    }

    // Debug: Log first item to see structure
    if (data.length > 0) {
        console.log('Sample data item structure:', Object.keys(data[0]));
        console.log('Sample data item:', data[0]);
    }

    const stats = {
        totalCount: data.length,
        lifecycle: [],
        type: [],
        status: [],
        security: [],
        editability: [],
        mandatory: [],
        origin: [],
        classification: [],
        orgUnit: [],
        profile: [],
        automation: [],
        level: [],
        frequency: [],
        stage: [],
        maturity: [],
        probability: [],
        complianceLevel: [],
        roleType: [],
        objectType: [],
        roleAccepted: []
    };

    // Count occurrences of each field value
    const counters = {};

    // Get field mappings for this category
    const fieldMappings = getCategoryFieldMappings(category);
    console.log('Field mappings for category:', category, fieldMappings);

    data.forEach((item, index) => {
        // Helper function to count field values with multiple field name attempts
        const countField = (fieldName, statKey, alternativeNames = []) => {
            // Try primary field name
            let value = getFieldValue(item, fieldName);

            // If not found, try alternative names
            if ((value === null || value === undefined || value === '') && alternativeNames.length > 0) {
                for (const altName of alternativeNames) {
                    value = getFieldValue(item, altName);
                    if (value !== null && value !== undefined && value !== '') {
                        break;
                    }
                }
            }

            // If still not found, try case-insensitive search
            if (value === null || value === undefined || value === '') {
                const itemKeys = Object.keys(item || {});
                const lowerFieldName = fieldName.toLowerCase();
                const matchingKey = itemKeys.find(key => key.toLowerCase() === lowerFieldName);
                if (matchingKey) {
                    value = item[matchingKey];
                }
            }

            // If still not found, try fuzzy matching (contains the statKey)
            if (value === null || value === undefined || value === '') {
                const itemKeys = Object.keys(item || {});
                const lowerStatKey = statKey.toLowerCase();
                const matchingKey = itemKeys.find(key => {
                    const lowerKey = key.toLowerCase();
                    return lowerKey.includes(lowerStatKey) || lowerStatKey.includes(lowerKey);
                });
                if (matchingKey) {
                    value = item[matchingKey];
                    console.log(`Found field by fuzzy match: ${matchingKey} for statKey: ${statKey}`);
                }
            }

            if (value !== null && value !== undefined && value !== '') {
                if (!counters[statKey]) counters[statKey] = {};
                const valueStr = String(value).trim();
                if (valueStr) {
                    counters[statKey][valueStr] = (counters[statKey][valueStr] || 0) + 1;
                }
            }
        };

        // Count fields based on category-specific mappings with alternative field names
        Object.entries(fieldMappings).forEach(([statKey, fieldName]) => {
            // Define alternative field names to try
            const alternatives = getAlternativeFieldNames(statKey, fieldName);
            countField(fieldName, statKey, alternatives);
        });
    });

    // Convert counters to array format expected by charts
    Object.keys(counters).forEach(statKey => {
        stats[statKey] = Object.entries(counters[statKey]).map(([label, count]) => ({
            label,
            count
        }));
    });

    console.log('Generated stats:', stats);
    return stats;
}

/**
 * Get alternative field names to try when primary field name is not found
 * @param {string} statKey - The statistics key (e.g., 'status', 'lifecycle')
 * @param {string} primaryFieldName - The primary field name
 * @returns {Array<string>} Array of alternative field names
 */
function getAlternativeFieldNames(statKey, primaryFieldName) {
    const alternatives = {
        'status': ['status', 'Status', 'budgStatus', 'BUDGStatus', 'BUDG Status', 'budg_status', 'state', 'currentStatus', 'CurrentStatus'],
        'lifecycle': ['lifecycle', 'Lifecycle', 'lifeCycle', 'life_cycle', 'lifecycleStatus', 'lifecycle_status', 'LifecycleStatus'],
        'type': ['type', 'Type', 'dataType', 'data_type', 'itemType', 'item_type', 'DataType', 'ItemType'],
        'security': ['security', 'Security', 'securityLevel', 'security_level', 'secLevel', 'SecurityLevel'],
        'editability': ['editability', 'Editability', 'editable', 'isEditable', 'canEdit', 'Editable'],
        'mandatory': ['mandatory', 'Mandatory', 'isMandatory', 'required', 'isRequired', 'Required'],
        'origin': ['origin', 'Origin', 'source', 'dataOrigin', 'data_origin', 'DataOrigin'],
        'classification': ['classification', 'Classification', 'class', 'dataClassification', 'data_classification', 'DataClassification'],
        'orgUnit': ['orgUnit', 'OrgUnit', 'org_unit', 'organizationUnit', 'organization_unit', 'orgUnitName', 'OrganizationUnit'],
        'profile': ['profile', 'Profile', 'userProfile', 'user_profile', 'UserProfile'],
        'automation': ['automation', 'Automation', 'automated', 'isAutomated', 'automationLevel', 'AutomationLevel'],
        'level': ['level', 'Level', 'processLevel', 'process_level', 'ProcessLevel'],
        'frequency': ['frequency', 'Frequency', 'updateFrequency', 'update_frequency', 'UpdateFrequency'],
        'stage': ['stage', 'Stage', 'processStage', 'process_stage', 'ProcessStage'],
        'maturity': ['maturity', 'Maturity', 'maturityLevel', 'maturity_level', 'MaturityLevel'],
        'probability': ['probability', 'Probability', 'riskProbability', 'risk_probability', 'RiskProbability'],
        'complianceLevel': ['complianceLevel', 'ComplianceLevel', 'compliance_level', 'compliance', 'Compliance'],
        'roleType': ['roleType', 'RoleType', 'role_type', 'type', 'Type'],
        'objectType': ['objectType', 'ObjectType', 'object_type', 'type', 'Type'],
        'roleAccepted': ['roleAccepted', 'RoleAccepted', 'role_accepted', 'accepted', 'isAccepted', 'Accepted']
    };

    // Always include the primary field name and its lowercase version
    const result = [primaryFieldName];
    if (primaryFieldName !== primaryFieldName.toLowerCase()) {
        result.push(primaryFieldName.toLowerCase());
    }
    if (primaryFieldName !== primaryFieldName.toUpperCase()) {
        result.push(primaryFieldName.toUpperCase());
    }

    // Add statKey-specific alternatives
    if (alternatives[statKey]) {
        result.push(...alternatives[statKey]);
    }

    return result;
}

/**
 * Get field value from data item, handling nested properties
 * @param {Object} item - Data item
 * @param {string} fieldName - Field name to get
 * @returns {*} Field value
 */
function getFieldValue(item, fieldName) {
    if (!item || fieldName == null) return null;

    // Handle array of field paths (e.g. ['First_Name', 'Last_Name'])
    if (Array.isArray(fieldName)) {
        const parts = fieldName.map(f => getFieldValue(item, f)).filter(v => v != null && v !== '');
        return parts.length ? parts.join(' ').trim() : null;
    }

    if (typeof fieldName !== 'string') return null;

    // Handle direct property access
    if (item.hasOwnProperty(fieldName)) {
        const value = item[fieldName];
        // Handle objects that might have a 'value' or 'label' property
        if (value && typeof value === 'object' && !Array.isArray(value)) {
            if (value.hasOwnProperty('value')) return value.value;
            if (value.hasOwnProperty('label')) return value.label;
            if (value.hasOwnProperty('name')) return value.name;
        }
        return value;
    }

    // Handle nested property access (e.g., 'parent.name')
    const parts = fieldName.split('.');
    let value = item;
    for (const part of parts) {
        if (value && typeof value === 'object') {
            if (value.hasOwnProperty(part)) {
                value = value[part];
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    // Handle objects that might have a 'value' or 'label' property
    if (value && typeof value === 'object' && !Array.isArray(value)) {
        if (value.hasOwnProperty('value')) return value.value;
        if (value.hasOwnProperty('label')) return value.label;
        if (value.hasOwnProperty('name')) return value.name;
    }

    return value;
}

/**
 * Get category-specific field mappings for statistics generation
 * @param {string} category - The category name
 * @returns {Object} Field mappings for the category
 */
function getCategoryFieldMappings(category) {
    const module = categoryToModule(category);

    // Define field mappings for different categories
    // These use the actual property names from the data objects
    const mappings = {
        'glossary': {
            lifecycle: 'Lifecycle',
            type: 'Type',
            status: 'BUDG Status',  // or 'BUDGStatus' or 'Status'
            security: 'Security',
            editability: 'Editability',
            mandatory: 'Mandatory'
        },
        'dataset': {
            lifecycle: 'Lifecycle',
            type: 'Type',
            status: 'BUDG Status',  // Dataset uses 'BUDG Status' based on column names
            security: 'Security',
            editability: 'Editability',
            mandatory: 'Mandatory',
            origin: 'Origin'
        },
        'attribute': {
            lifecycle: 'lifecycle',
            type: 'type',
            status: 'budgStatus',
            security: 'security',
            editability: 'editability',
            mandatory: 'mandatory'
        },
        'system': {
            lifecycle: 'lifecycle',
            type: 'type',
            status: 'budgStatus',
            classification: 'classification'
        },
        'people': {
            orgUnit: 'orgUnit',
            profile: 'profile'
        },
        'business-area': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'client': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'project': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'committee': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'policy': {
            lifecycle: 'lifecycle',
            type: 'type',
            status: 'budgStatus',
            classification: 'classification'
        },
        'process': {
            type: 'type',
            lifecycle: 'lifecycle',
            status: 'budgStatus',
            classification: 'classification',
            automation: 'automation',
            level: 'level'
        },
        'interface': {
            lifecycle: 'lifecycle',
            type: 'type',
            status: 'budgStatus',
            classification: 'classification'
        },
        'legal-entity': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'orgunit': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'product': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'capability': {
            lifecycle: 'lifecycle',
            type: 'type',
            status: 'budgStatus',
            classification: 'classification'
        },
        'regulation': {
            lifecycle: 'lifecycle',
            type: 'type',
            status: 'budgStatus',
            classification: 'classification'
        },
        'regulatory-theme': {
            lifecycle: 'lifecycle',
            status: 'budgStatus'
        },
        'role': {
            roleType: 'roleType',
            objectType: 'objectType',
            roleAccepted: 'roleAccepted'
        }
    };

    return mappings[module] || {};
}

/**
 * Fetch dashboard statistics for a category
 * @param {string} category - The category name (e.g., 'glossary')
 * @param {Object} filters - Optional filters object
 * @returns {Promise<Object>} Dashboard statistics data
 */
async function fetchDashboardStats(category, filters = null) {
    const module = categoryToModule(category);
    if (!module) {
        throw new Error('Invalid category');
    }

    // Support Glossary, Dataset, Attribute, System, People, Business Area, Client, Project, Committee, Policy, Process, Interface, Legal Entity, Org Unit, Product, Capability, Regulation, Regulatory Theme, and Role
    if (module !== 'glossary' && module !== 'dataset' && module !== 'attribute' && module !== 'system' &&
        module !== 'people' && module !== 'business-area' && module !== 'client' && module !== 'project' &&
        module !== 'committee' && module !== 'policy' && module !== 'process' && module !== 'interface' &&
        module !== 'legal-entity' && module !== 'orgunit' && module !== 'product' &&
        module !== 'capability' && module !== 'regulation' && module !== 'regulatory-theme' && module !== 'role') {
        throw new Error('Dashboard not available for this category');
    }

    try {
        // Build query parameters from filters
        const params = new URLSearchParams();
        if (filters) {
            if (filters.type && filters.type.size > 0) {
                params.append('excludeType', Array.from(filters.type).join(','));
            }
            if (filters.lifecycle && filters.lifecycle.size > 0) {
                params.append('excludeLifecycle', Array.from(filters.lifecycle).join(','));
            }
            if (filters.status && filters.status.size > 0) {
                params.append('excludeStatus', Array.from(filters.status).join(','));
            }
            if (filters.security && filters.security.size > 0) {
                params.append('excludeSecurity', Array.from(filters.security).join(','));
            }
            if (filters.editability && filters.editability.size > 0) {
                params.append('excludeEditability', Array.from(filters.editability).join(','));
            }
            if (filters.mandatory && filters.mandatory.size > 0) {
                params.append('excludeMandatory', Array.from(filters.mandatory).join(','));
            }
            if (filters.origin && filters.origin.size > 0) {
                params.append('excludeOrigin', Array.from(filters.origin).join(','));
            }
            if (filters.classification && filters.classification.size > 0) {
                params.append('excludeClassification', Array.from(filters.classification).join(','));
            }
            if (filters.orgUnit && filters.orgUnit.size > 0) {
                params.append('excludeOrgUnit', Array.from(filters.orgUnit).join(','));
            }
            if (filters.profile && filters.profile.size > 0) {
                params.append('excludeProfile', Array.from(filters.profile).join(','));
            }
            if (filters.automation && filters.automation.size > 0) {
                params.append('excludeAutomation', Array.from(filters.automation).join(','));
            }
            if (filters.level && filters.level.size > 0) {
                params.append('excludeLevel', Array.from(filters.level).join(','));
            }
            if (filters.frequency && filters.frequency.size > 0) {
                params.append('excludeFrequency', Array.from(filters.frequency).join(','));
            }
            if (filters.stage && filters.stage.size > 0) {
                params.append('excludeStage', Array.from(filters.stage).join(','));
            }
            if (filters.maturity && filters.maturity.size > 0) {
                params.append('excludeMaturity', Array.from(filters.maturity).join(','));
            }
            if (filters.probability && filters.probability.size > 0) {
                params.append('excludeProbability', Array.from(filters.probability).join(','));
            }
            if (filters.complianceLevel && filters.complianceLevel.size > 0) {
                params.append('excludeComplianceLevel', Array.from(filters.complianceLevel).join(','));
            }
            if (filters.roleType && filters.roleType.size > 0) {
                params.append('excludeRoleType', Array.from(filters.roleType).join(','));
            }
            if (filters.objectType && filters.objectType.size > 0) {
                params.append('excludeObjectType', Array.from(filters.objectType).join(','));
            }
            if (filters.roleAccepted && filters.roleAccepted.size > 0) {
                params.append('excludeRoleAccepted', Array.from(filters.roleAccepted).join(','));
            }
        }

        let endpoint;
        if (module === 'glossary') {
            endpoint = '/api/glossary/dashboard/stats';
        } else if (module === 'dataset') {
            endpoint = '/api/dataset/dashboard/stats';
        } else if (module === 'attribute') {
            endpoint = '/api/attribute/dashboard/stats';
        } else if (module === 'system') {
            endpoint = '/api/system/dashboard/stats';
        } else if (module === 'people') {
            endpoint = '/api/people/dashboard/stats';
        } else if (module === 'business-area') {
            endpoint = '/api/business-area/dashboard/stats';
        } else if (module === 'client') {
            endpoint = '/api/client/dashboard/stats';
        } else if (module === 'project') {
            endpoint = '/api/project/dashboard/stats';
        } else if (module === 'committee') {
            endpoint = '/api/committee/dashboard/stats';
        } else if (module === 'policy') {
            endpoint = '/api/policy/dashboard/stats';
        } else if (module === 'process') {
            endpoint = '/api/process/dashboard/stats';
        } else if (module === 'interface') {
            endpoint = '/api/interface/dashboard/stats';
        } else if (module === 'legal-entity') {
            endpoint = '/api/legal-entity/dashboard/stats';
        } else if (module === 'orgunit') {
            endpoint = '/api/org-unit/dashboard/stats';
        } else if (module === 'product') {
            endpoint = '/api/product/dashboard/stats';
        } else if (module === 'capability') {
            endpoint = '/api/capability/dashboard/stats';
        } else if (module === 'regulation') {
            endpoint = '/api/regulation/dashboard/stats';
        } else if (module === 'regulatory-theme') {
            endpoint = '/api/regulatory-theme/dashboard/stats';
        } else if (module === 'role') {
            endpoint = '/api/role/dashboard/stats';
        }
        const url = endpoint + (params.toString() ? '?' + params.toString() : '');
        const response = await fetch(url, {
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to fetch dashboard stats');
        }

        return await response.json();
    } catch (error) {
        console.error('Error fetching dashboard stats:', error);
        throw error;
    }
}

/**
 * Render dashboard view for a category
 * @param {string} category - The category name
 */
async function renderDashboard(category) {
    const contentMain = document.querySelector('.content-main');
    if (!contentMain) {
        console.error('Content main container not found');
        return;
    }

    // Hide the data-table-wrapper if it exists
    const dataTableWrapper = document.querySelector('.data-table-wrapper');
    if (dataTableWrapper) {
        dataTableWrapper.style.display = 'none';
    }

    // Remove any existing dashboard containers
    const existingDashboard = contentMain.querySelector('.dashboard-container');
    if (existingDashboard) {
        existingDashboard.remove();
    }

    const module = categoryToModule(category);
    if (module !== 'glossary' && module !== 'dataset' && module !== 'attribute' && module !== 'system' &&
        module !== 'people' && module !== 'business-area' && module !== 'client' && module !== 'project' &&
        module !== 'committee' && module !== 'policy' && module !== 'process' && module !== 'interface' &&
        module !== 'legal-entity' && module !== 'orgunit' && module !== 'product' &&
        module !== 'capability' && module !== 'regulation' && module !== 'regulatory-theme' && module !== 'role') {
        const dashboardContainer = document.createElement('div');
        dashboardContainer.className = 'dashboard-container';
        dashboardContainer.innerHTML = `
            <div class="dashboard-message">
                <i class="fas fa-info-circle"></i>
                <p>Dashboard is not available for this category.</p>
            </div>
        `;
        contentMain.appendChild(dashboardContainer);
        return;
    }

    // Show loading state
    const dashboardContainer = document.createElement('div');
    dashboardContainer.className = 'dashboard-container';
    dashboardContainer.innerHTML = `
        <div class="dashboard-loading">
            <i class="fas fa-spinner fa-spin"></i>
            <p>Loading dashboard...</p>
        </div>
    `;
    contentMain.appendChild(dashboardContainer);

    try {
        const stats = await fetchDashboardStats(category, dashboardActiveFilters);
        console.log('Dashboard stats received:', stats);
        console.log('Total count:', stats.totalCount);
        console.log('Active filters:', dashboardActiveFilters);

        // Log verification data
        if (stats.verification) {
            console.log('Verification sums:', stats.verification);
            console.log('Lifecycle sum:', stats.verification.lifecycleSum, 'vs Total:', stats.totalCount);
            console.log('Type sum:', stats.verification.typeSum, 'vs Total:', stats.totalCount);
            console.log('Status sum:', stats.verification.statusSum, 'vs Total:', stats.totalCount);
            if (stats.verification.securitySum !== undefined) {
                console.log('Security sum:', stats.verification.securitySum, 'vs Total:', stats.totalCount);
            }
        }

        // Update the loading dashboard container with actual content
        if (module === 'glossary') {
            renderGlossaryDashboard(dashboardContainer, stats);
        } else if (module === 'dataset') {
            renderDatasetDashboard(dashboardContainer, stats);
        } else if (module === 'attribute') {
            renderAttributeDashboard(dashboardContainer, stats);
        } else if (module === 'system') {
            renderSystemDashboard(dashboardContainer, stats);
        } else if (module === 'people') {
            renderPeopleDashboard(dashboardContainer, stats);
        } else if (module === 'business-area') {
            renderBusinessAreaDashboard(dashboardContainer, stats);
        } else if (module === 'client') {
            renderClientDashboard(dashboardContainer, stats);
        } else if (module === 'project') {
            renderProjectDashboard(dashboardContainer, stats);
        } else if (module === 'committee') {
            renderCommitteeDashboard(dashboardContainer, stats);
        } else if (module === 'policy') {
            renderPolicyDashboard(dashboardContainer, stats);
        } else if (module === 'process') {
            renderProcessDashboard(dashboardContainer, stats);
        } else if (module === 'interface') {
            renderInterfaceDashboard(dashboardContainer, stats);
        } else if (module === 'legal-entity') {
            renderLegalEntityDashboard(dashboardContainer, stats);
        } else if (module === 'orgunit') {
            renderOrgUnitDashboard(dashboardContainer, stats);
        } else if (module === 'product') {
            renderProductDashboard(dashboardContainer, stats);
        } else if (module === 'capability') {
            renderCapabilityDashboard(dashboardContainer, stats);
        } else if (module === 'regulation') {
            renderRegulationDashboard(dashboardContainer, stats);
        } else if (module === 'regulatory-theme') {
            renderRegulatoryThemeDashboard(dashboardContainer, stats);
        } else if (module === 'role') {
            renderRoleDashboard(dashboardContainer, stats);
        }
    } catch (error) {
        // Update the loading container with error message
        dashboardContainer.innerHTML = `
            <div class="dashboard-error">
                <i class="fas fa-exclamation-triangle"></i>
                <p>Failed to load dashboard data. Please try again.</p>
            </div>
        `;
        console.error('Error rendering dashboard:', error);
    }
}

/**
 * Render Glossary dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderGlossaryDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Security Classification</h3>
                    <div class="chart-container">
                        <canvas id="chart-security"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering charts with data:', {
            lifecycle: stats.lifecycle,
            type: stats.type,
            status: stats.status,
            securityClassification: stats.securityClassification
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.type && stats.type.length > 0) {
            console.log('Creating type chart with', stats.type.length, 'items');
            createPieChart('chart-type', 'Type', stats.type, 'type');
        } else {
            console.warn('No type data available');
        }
        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
        if (stats.securityClassification && stats.securityClassification.length > 0) {
            console.log('Creating security chart with', stats.securityClassification.length, 'items');
            createBarChart('chart-security', 'Security Classification', stats.securityClassification, 'security');
        } else {
            console.warn('No security classification data available');
        }
    }, 100);
}

/**
 * Render Dataset dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderDatasetDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering dataset charts with data:', {
            lifecycle: stats.lifecycle,
            type: stats.type,
            status: stats.status
        });

        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.type && stats.type.length > 0) {
            console.log('Creating type chart with', stats.type.length, 'items');
            createPieChart('chart-type', 'Type', stats.type, 'type');
        } else {
            console.warn('No type data available');
        }
    }, 100);
}

/**
 * Render Attribute dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderAttributeDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Editability</h3>
                    <div class="chart-container">
                        <canvas id="chart-editability"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Mandatory</h3>
                    <div class="chart-container">
                        <canvas id="chart-mandatory"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Origin</h3>
                    <div class="chart-container">
                        <canvas id="chart-origin"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering attribute charts with data:', {
            editability: stats.editability,
            mandatory: stats.mandatory,
            origin: stats.origin
        });

        if (stats.editability && stats.editability.length > 0) {
            console.log('Creating editability chart with', stats.editability.length, 'items');
            createPieChart('chart-editability', 'Editability', stats.editability, 'editability');
        } else {
            console.warn('No editability data available');
        }
        if (stats.mandatory && stats.mandatory.length > 0) {
            console.log('Creating mandatory chart with', stats.mandatory.length, 'items');
            createPieChart('chart-mandatory', 'Mandatory', stats.mandatory, 'mandatory');
        } else {
            console.warn('No mandatory data available');
        }
        if (stats.origin && stats.origin.length > 0) {
            console.log('Creating origin chart with', stats.origin.length, 'items');
            createPieChart('chart-origin', 'Origin', stats.origin, 'origin');
        } else {
            console.warn('No origin data available');
        }
    }, 100);
}

/**
 * Render System dashboard with pie charts and bar chart
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderSystemDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Classification</h3>
                    <div class="chart-container">
                        <canvas id="chart-classification"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering system charts with data:', {
            lifecycle: stats.lifecycle,
            type: stats.type,
            status: stats.status,
            classification: stats.classification
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.type && stats.type.length > 0) {
            console.log('Creating type chart with', stats.type.length, 'items');
            createPieChart('chart-type', 'Type', stats.type, 'type');
        } else {
            console.warn('No type data available');
        }
        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
        if (stats.classification && stats.classification.length > 0) {
            console.log('Creating classification chart with', stats.classification.length, 'items');
            createBarChart('chart-classification', 'Classification', stats.classification, 'classification');
        } else {
            console.warn('No classification data available');
        }
    }, 100);
}

/**
 * Render People dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderPeopleDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Org Unit</h3>
                    <div class="chart-container">
                        <canvas id="chart-orgUnit"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Profile Name</h3>
                    <div class="chart-container">
                        <canvas id="chart-profile"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering people charts with data:', {
            lifecycle: stats.lifecycle,
            orgUnit: stats.orgUnit,
            status: stats.status,
            profile: stats.profile
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.orgUnit && stats.orgUnit.length > 0) {
            console.log('Creating orgUnit chart with', stats.orgUnit.length, 'items');
            createPieChart('chart-orgUnit', 'Org Unit', stats.orgUnit, 'orgUnit');
        } else {
            console.warn('No orgUnit data available');
        }
        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
        if (stats.profile && stats.profile.length > 0) {
            console.log('Creating profile chart with', stats.profile.length, 'items');
            createPieChart('chart-profile', 'Profile Name', stats.profile, 'profile');
        } else {
            console.warn('No profile data available');
        }
    }, 100);
}

/**
 * Render Business Area dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderBusinessAreaDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering business area charts with data:', {
            lifecycle: stats.lifecycle,
            status: stats.status
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
    }, 100);
}

/**
 * Render Client dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderClientDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering client charts with data:', {
            lifecycle: stats.lifecycle,
            status: stats.status
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
    }, 100);
}

/**
 * Render Project dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderProjectDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering project charts with data:', {
            lifecycle: stats.lifecycle,
            status: stats.status
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            console.log('Creating lifecycle chart with', stats.lifecycle.length, 'items');
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        } else {
            console.warn('No lifecycle data available');
        }
        if (stats.status && stats.status.length > 0) {
            console.log('Creating status chart with', stats.status.length, 'items');
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        } else {
            console.warn('No status data available');
        }
    }, 100);
}

/**
 * Render Committee dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderCommitteeDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Classification</h3>
                    <div class="chart-container">
                        <canvas id="chart-classification"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering committee charts with data:', {
            type: stats.type,
            lifecycle: stats.lifecycle,
            classification: stats.classification,
            status: stats.status
        });

        if (stats.type && stats.type.length > 0) {
            createPieChart('chart-type', 'Type', stats.type, 'type');
        }
        if (stats.lifecycle && stats.lifecycle.length > 0) {
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        }
        if (stats.classification && stats.classification.length > 0) {
            createPieChart('chart-classification', 'Classification', stats.classification, 'classification');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
    }, 100);
}

/**
 * Render Policy dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderPolicyDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering policy charts with data:', {
            type: stats.type,
            status: stats.status,
            lifecycle: stats.lifecycle
        });

        if (stats.type && stats.type.length > 0) {
            createPieChart('chart-type', 'Type', stats.type, 'type');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
        if (stats.lifecycle && stats.lifecycle.length > 0) {
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        }
    }, 100);
}

/**
 * Render Process dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderProcessDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Classification</h3>
                    <div class="chart-container">
                        <canvas id="chart-classification"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Automation</h3>
                    <div class="chart-container">
                        <canvas id="chart-automation"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Level</h3>
                    <div class="chart-container">
                        <canvas id="chart-level"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering process charts with data:', {
            type: stats.type,
            lifecycle: stats.lifecycle,
            status: stats.status,
            classification: stats.classification,
            automation: stats.automation,
            level: stats.level
        });

        if (stats.type && stats.type.length > 0) {
            createPieChart('chart-type', 'Type', stats.type, 'type');
        }
        if (stats.lifecycle && stats.lifecycle.length > 0) {
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
        if (stats.classification && stats.classification.length > 0) {
            createPieChart('chart-classification', 'Classification', stats.classification, 'classification');
        }
        if (stats.automation && stats.automation.length > 0) {
            createPieChart('chart-automation', 'Automation', stats.automation, 'automation');
        }
        if (stats.level && stats.level.length > 0) {
            createPieChart('chart-level', 'Level', stats.level, 'level');
        }
    }, 100);
}

/**
 * Render Interface dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderInterfaceDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Automation</h3>
                    <div class="chart-container">
                        <canvas id="chart-automation"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Frequency</h3>
                    <div class="chart-container">
                        <canvas id="chart-frequency"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering interface charts with data:', {
            lifecycle: stats.lifecycle,
            status: stats.status,
            automation: stats.automation,
            frequency: stats.frequency
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
        if (stats.automation && stats.automation.length > 0) {
            createPieChart('chart-automation', 'Automation', stats.automation, 'automation');
        }
        if (stats.frequency && stats.frequency.length > 0) {
            createPieChart('chart-frequency', 'Frequency', stats.frequency, 'frequency');
        }
    }, 100);
}

/**
 * Render Legal Entity dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderLegalEntityDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Level</h3>
                    <div class="chart-container">
                        <canvas id="chart-level"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering legal entity charts with data:', {
            level: stats.level,
            status: stats.status
        });

        if (stats.level && stats.level.length > 0) {
            createPieChart('chart-level', 'Level', stats.level, 'level');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
    }, 100);
}

/**
 * Render Org Unit dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderOrgUnitDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering org unit charts with data:', {
            status: stats.status
        });

        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
    }, 100);
}

/**
 * Render Product dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderProductDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering product charts with data:', {
            lifecycle: stats.lifecycle,
            status: stats.status
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
    }, 100);
}

/**
 * Render Capability dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderCapabilityDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Lifecycle</h3>
                    <div class="chart-container">
                        <canvas id="chart-lifecycle"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Classification</h3>
                    <div class="chart-container">
                        <canvas id="chart-classification"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-type"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering capability charts with data:', {
            lifecycle: stats.lifecycle,
            status: stats.status,
            classification: stats.classification,
            type: stats.type
        });

        if (stats.lifecycle && stats.lifecycle.length > 0) {
            createPieChart('chart-lifecycle', 'Lifecycle', stats.lifecycle, 'lifecycle');
        }
        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
        if (stats.classification && stats.classification.length > 0) {
            createPieChart('chart-classification', 'Classification', stats.classification, 'classification');
        }
        if (stats.type && stats.type.length > 0) {
            createPieChart('chart-type', 'Type', stats.type, 'type');
        }
    }, 100);
}

/**
 * Render Regulation dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderRegulationDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Stage</h3>
                    <div class="chart-container">
                        <canvas id="chart-stage"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Maturity</h3>
                    <div class="chart-container">
                        <canvas id="chart-maturity"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Probability</h3>
                    <div class="chart-container">
                        <canvas id="chart-probability"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Compliance Level</h3>
                    <div class="chart-container">
                        <canvas id="chart-complianceLevel"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering regulation charts with data:', {
            status: stats.status,
            stage: stats.stage,
            maturity: stats.maturity,
            probability: stats.probability,
            complianceLevel: stats.complianceLevel
        });

        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
        if (stats.stage && stats.stage.length > 0) {
            createPieChart('chart-stage', 'Stage', stats.stage, 'stage');
        }
        if (stats.maturity && stats.maturity.length > 0) {
            createPieChart('chart-maturity', 'Maturity', stats.maturity, 'maturity');
        }
        if (stats.probability && stats.probability.length > 0) {
            createPieChart('chart-probability', 'Probability', stats.probability, 'probability');
        }
        if (stats.complianceLevel && stats.complianceLevel.length > 0) {
            createPieChart('chart-complianceLevel', 'Compliance Level', stats.complianceLevel, 'complianceLevel');
        }
    }, 100);
}

/**
 * Render Regulatory Theme dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderRegulatoryThemeDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">BUDG Status</h3>
                    <div class="chart-container">
                        <canvas id="chart-status"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering regulatory theme charts with data:', {
            status: stats.status
        });

        if (stats.status && stats.status.length > 0) {
            createPieChart('chart-status', 'BUDG Status', stats.status, 'status');
        }
    }, 100);
}

/**
 * Render Role dashboard with pie charts
 * @param {HTMLElement} container - Container element
 * @param {Object} stats - Statistics data from backend
 */
function renderRoleDashboard(container, stats) {
    // Destroy existing charts
    destroyAllCharts();

    const dashboardHtml = `
        <div class="dashboard-container">
            <div class="dashboard-grid">
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Role Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-roleType"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Object Type</h3>
                    <div class="chart-container">
                        <canvas id="chart-objectType"></canvas>
                    </div>
                </div>
                <div class="dashboard-chart-card">
                    <h3 class="chart-title">Role Accepted</h3>
                    <div class="chart-container">
                        <canvas id="chart-roleAccepted"></canvas>
                    </div>
                </div>
            </div>
    `;

    container.innerHTML = dashboardHtml;

    // Create charts after DOM is updated
    setTimeout(() => {
        console.log('Rendering role charts with data:', {
            roleType: stats.roleType,
            objectType: stats.objectType,
            roleAccepted: stats.roleAccepted
        });

        if (stats.roleType && stats.roleType.length > 0) {
            createPieChart('chart-roleType', 'Role Type', stats.roleType, 'roleType');
        }
        if (stats.objectType && stats.objectType.length > 0) {
            createPieChart('chart-objectType', 'Object Type', stats.objectType, 'objectType', true); // true = manyCategories mode
        }
        if (stats.roleAccepted && stats.roleAccepted.length > 0) {
            createPieChart('chart-roleAccepted', 'Role Accepted', stats.roleAccepted, 'roleAccepted');
        }
    }, 100);
}

/**
 * Create a pie chart using Chart.js
 * @param {string} canvasId - ID of the canvas element
 * @param {string} fieldName - Name of the field (for title)
 * @param {Array} data - Array of {label, count} objects
 * @param {string} filterField - Field name for filtering (e.g., 'type', 'lifecycle', 'status')
 * @param {boolean} manyCategories - Optional: true if chart has many categories (adjusts layout)
 */
function createPieChart(canvasId, fieldName, data, filterField, manyCategories = false) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.error(`Canvas element not found: ${canvasId}`);
        return;
    }

    // Destroy existing chart if it exists
    if (dashboardCharts[canvasId]) {
        dashboardCharts[canvasId].destroy();
    }

    // Prepare chart data
    const labels = data.map(item => item.label || 'Not Set');
    const counts = data.map(item => item.count || 0);
    const total = counts.reduce((sum, count) => sum + count, 0);

    // Generate colors - pass labels array to ensure null values get consistent color
    const colors = generateColors(labels);

    const chartData = {
        labels: labels,
        datasets: [{
            data: counts,
            backgroundColor: colors.background,
            borderColor: colors.border,
            borderWidth: 2
        }]
    };

    // Adjust settings for many categories
    const legendPosition = manyCategories ? 'bottom' : 'right';
    const legendFontSize = manyCategories ? 10 : 12;
    const legendPadding = manyCategories ? 10 : 15;
    const legendBoxWidth = manyCategories ? 12 : 15;
    const legendBoxHeight = manyCategories ? 12 : 15;

    const config = {
        type: 'doughnut',
        data: chartData,
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true,
                    position: legendPosition,
                    onClick: function(e, legendItem, legend) {
                        // Custom onClick to toggle filtering across all charts
                        const chart = legend.chart;
                        const index = legendItem.index;

                        if (index !== undefined && filterField && chart.data && chart.data.labels) {
                            const label = chart.data.labels[index];

                            // Toggle filter for this field/value
                            if (dashboardActiveFilters[filterField].has(label)) {
                                dashboardActiveFilters[filterField].delete(label);
                            } else {
                                dashboardActiveFilters[filterField].add(label);
                            }

                            // Reload all charts with updated filters
                            const category = getCurrentCategory();
                            if (category) {
                                renderDashboard(category);
                            }
                        }
                    },
                    labels: {
                        padding: legendPadding,
                        usePointStyle: true,
                        boxWidth: legendBoxWidth,
                        boxHeight: legendBoxHeight,
                        font: {
                            size: legendFontSize
                        },
                        generateLabels: function(chart) {
                            // Generate legend items with visual indication of filtered state
                            const data = chart.data;
                            if (data.labels && data.labels.length > 0) {
                                const pieColors = chart._pieColors || { background: [], border: [] };

                                return data.labels.map((label, i) => {
                                    const isFiltered = filterField && dashboardActiveFilters[filterField] && dashboardActiveFilters[filterField].has(label);
                                    return {
                                        text: label,
                                        fillStyle: isFiltered ? 'rgba(156, 163, 175, 0.3)' : (pieColors.background[i] || 'rgba(156, 163, 175, 0.8)'),
                                        strokeStyle: isFiltered ? 'rgba(156, 163, 175, 0.5)' : (pieColors.border[i] || 'rgba(156, 163, 175, 1)'),
                                        lineWidth: 2,
                                        hidden: false,
                                        index: i,
                                        datasetIndex: 0
                                    };
                                });
                            }
                            return [];
                        }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const label = context.label || '';
                            const value = context.parsed || 0;
                            const percentage = total > 0 ? ((value / total) * 100).toFixed(2) : 0;
                            return `${label}: ${value} (${percentage}%)`;
                        }
                    }
                }
            }
        }
    };

    // Create and store chart
    const chart = new Chart(canvas, config);

    // Store original data and colors on chart instance
    chart._originalCounts = [...counts];
    chart._originalLabels = [...labels];
    chart._pieColors = colors;
    chart._filterField = filterField;

    dashboardCharts[canvasId] = chart;
}

/**
 * Get current active category from the search interface
 */
function getCurrentCategory() {
    // Use the existing utility function if available
    if (typeof getActiveCategory === 'function') {
        const category = getActiveCategory();
        if (category) return category;
    }

    // Fallback: try to get from DOM
    const categoryElement = document.querySelector('.category-item.active, .category-item.current');
    if (categoryElement) {
        return categoryElement.getAttribute('data-category') || categoryElement.textContent.trim().toLowerCase();
    }

    // Default fallback
    return 'glossary';
}

/**
 * Create a bar chart using Chart.js for Security Classification
 * @param {string} canvasId - ID of the canvas element
 * @param {string} fieldName - Name of the field (for title)
 * @param {Array} data - Array of {label, count} objects
 * @param {string} filterField - Field name for filtering (e.g., 'security')
 */
function createBarChart(canvasId, fieldName, data, filterField) {
    const canvas = document.getElementById(canvasId);
    if (!canvas) {
        console.error(`Canvas element not found: ${canvasId}`);
        return;
    }

    // Destroy existing chart if it exists
    if (dashboardCharts[canvasId]) {
        dashboardCharts[canvasId].destroy();
    }

    // Prepare chart data
    const labels = data.map(item => item.label || 'Not Set');
    const counts = data.map(item => item.count || 0);
    const maxCount = Math.max(...counts, 0);

    // Calculate Y-axis max: round up to next multiple of 5
    // If maxCount is 0, show at least 5; otherwise round up to nearest multiple of 5
    const yAxisMax = maxCount === 0 ? 5 : Math.ceil(maxCount / 5) * 5;

    // Generate colors for bar chart - use project green for non-zero values
    const colors = generateBarChartColors(labels);

    // Create a single dataset with all data
    const chartData = {
        labels: labels,
        datasets: [{
            label: 'Count',
            data: counts,
            backgroundColor: colors.background,
            borderColor: colors.border,
            borderWidth: 1
        }]
    };

    const config = {
        type: 'bar',
        data: chartData,
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true,
                    position: 'bottom',
                    onClick: function(e, legendItem, legend) {
                        // Custom onClick to toggle filtering across all charts
                        const chart = legend.chart;
                        const index = legendItem.index;

                        if (index !== undefined && filterField && chart.data && chart.data.labels) {
                            const label = chart.data.labels[index];

                            // Toggle filter for this field/value
                            if (dashboardActiveFilters[filterField].has(label)) {
                                dashboardActiveFilters[filterField].delete(label);
                            } else {
                                dashboardActiveFilters[filterField].add(label);
                            }

                            // Reload all charts with updated filters
                            const category = getCurrentCategory();
                            if (category) {
                                renderDashboard(category);
                            }
                        }
                    },
                    labels: {
                        padding: 15,
                        usePointStyle: true,
                        font: {
                            size: 12
                        },
                        generateLabels: function(chart) {
                            // Generate legend items with visual indication of filtered state
                            const data = chart.data;
                            if (data.labels && data.labels.length > 0) {
                                const barColors = chart._barColors || { background: [], border: [] };

                                return data.labels.map((label, i) => {
                                    const isFiltered = filterField && dashboardActiveFilters[filterField] && dashboardActiveFilters[filterField].has(label);
                                    return {
                                        text: label,
                                        fillStyle: isFiltered ? 'rgba(156, 163, 175, 0.3)' : (barColors.background[i] || 'rgba(156, 163, 175, 0.8)'),
                                        strokeStyle: isFiltered ? 'rgba(156, 163, 175, 0.5)' : (barColors.border[i] || 'rgba(156, 163, 175, 1)'),
                                        lineWidth: 1,
                                        hidden: false,
                                        index: i,
                                        datasetIndex: 0
                                    };
                                });
                            }
                            return [];
                        }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const label = context.dataset.label || '';
                            const value = context.parsed.y || 0;
                            return `${label}: ${value}`;
                        }
                    }
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    max: yAxisMax,
                    title: {
                        display: true,
                        text: 'Count'
                    },
                    ticks: {
                        stepSize: 5,
                        precision: 0,
                        callback: function(value) {
                            // Only show values that are multiples of 5
                            return value % 5 === 0 ? value : '';
                        },
                        maxTicksLimit: 20 // Limit number of ticks to ensure readability
                    },
                    stacked: false
                },
                x: {
                    stacked: false,
                    title: {
                        display: true,
                        text: fieldName
                    }
                }
            }
        }
    };

    // Create and store chart
    const chart = new Chart(canvas, config);

    // Store original data and colors on chart instance
    chart._originalCounts = [...counts];
    chart._originalLabels = [...labels];
    chart._barColors = colors;
    chart._filterField = filterField;

    dashboardCharts[canvasId] = chart;
}

/**
 * Generate color palette for bar charts
 * Uses project green for values, different colors for different categories
 * @param {Array} labels - Array of label strings to match colors
 * @returns {Object} Object with background and border color arrays
 */
function generateBarChartColors(labels) {
    // Consistent color for null values across all charts
    const NULL_COLOR = { bg: 'rgba(156, 163, 175, 0.8)', border: 'rgba(156, 163, 175, 1)' }; // gray

    // Project colors: #48bb78 (secondary-color), #248567 (darker green)
    // Color mapping for Security Classification categories
    const colorMap = {
        'Restricted': { bg: 'rgba(59, 130, 246, 0.8)', border: 'rgba(59, 130, 246, 1)' }, // blue
        'Internal': { bg: 'rgba(168, 85, 247, 0.8)', border: 'rgba(168, 85, 247, 1)' }, // purple
        'Public': { bg: 'rgba(36, 133, 103, 0.8)', border: 'rgba(36, 133, 103, 1)' }, // project darker green (#248567)
        'Confidential': { bg: 'rgba(72, 187, 120, 0.8)', border: 'rgba(72, 187, 120, 1)' }, // project green (#48bb78)
        'Secret': { bg: 'rgba(236, 72, 153, 0.8)', border: 'rgba(236, 72, 153, 1)' }, // pink
        'FAO': { bg: 'rgba(251, 146, 60, 0.8)', border: 'rgba(251, 146, 60, 1)' }, // orange
        'null': NULL_COLOR, // Consistent null color
        'Not Set': { bg: 'rgba(156, 163, 175, 0.8)', border: 'rgba(156, 163, 175, 1)' } // gray
    };

    const background = [];
    const border = [];

    // Map colors based on labels
    labels.forEach(label => {
        const normalizedLabel = label.trim();
        const color = colorMap[normalizedLabel] || colorMap['Not Set'];
        background.push(color.bg);
        border.push(color.border);
    });

    return { background, border };
}

/**
 * Generate color palette for charts
 * @param {number|Array} countOrLabels - Number of colors needed OR array of labels
 * @returns {Object} Object with background and border color arrays
 */
function generateColors(countOrLabels) {
    // Consistent color for null values across all charts
    const NULL_COLOR = { bg: 'rgba(156, 163, 175, 0.8)', border: 'rgba(156, 163, 175, 1)' }; // gray

    // Color palette - using project's green color (#48bb78) as primary, with complementary colors
    // Project colors: #48bb78 (secondary-color), #248567 (darker green)
    const palette = [
        { bg: 'rgba(72, 187, 120, 0.8)', border: 'rgba(72, 187, 120, 1)' }, // Project green (#48bb78)
        { bg: 'rgba(36, 133, 103, 0.8)', border: 'rgba(36, 133, 103, 1)' }, // Project darker green (#248567)
        { bg: 'rgba(59, 130, 246, 0.8)', border: 'rgba(59, 130, 246, 1)' }, // blue
        { bg: 'rgba(168, 85, 247, 0.8)', border: 'rgba(168, 85, 247, 1)' }, // purple
        { bg: 'rgba(236, 72, 153, 0.8)', border: 'rgba(236, 72, 153, 1)' }, // pink
        { bg: 'rgba(251, 146, 60, 0.8)', border: 'rgba(251, 146, 60, 1)' }, // orange
        { bg: 'rgba(34, 197, 94, 0.8)', border: 'rgba(34, 197, 94, 1)' }, // green variant
        { bg: 'rgba(239, 68, 68, 0.8)', border: 'rgba(239, 68, 68, 1)' }, // red
        { bg: 'rgba(139, 92, 246, 0.8)', border: 'rgba(139, 92, 246, 1)' }, // indigo
        { bg: 'rgba(14, 165, 233, 0.8)', border: 'rgba(14, 165, 233, 1)' }, // sky blue
        { bg: 'rgba(245, 158, 11, 0.8)', border: 'rgba(245, 158, 11, 1)' }  // amber
    ];

    const background = [];
    const border = [];

    // Check if we received labels array or just a count
    const isLabelsArray = Array.isArray(countOrLabels);
    const labels = isLabelsArray ? countOrLabels : null;
    const count = isLabelsArray ? countOrLabels.length : countOrLabels;

    let paletteIndex = 0;

    for (let i = 0; i < count; i++) {
        // If we have labels, check for "null" and assign consistent color
        if (isLabelsArray && labels[i] && labels[i].trim().toLowerCase() === 'null') {
            background.push(NULL_COLOR.bg);
            border.push(NULL_COLOR.border);
        } else {
            const color = palette[paletteIndex % palette.length];
            background.push(color.bg);
            border.push(color.border);
            paletteIndex++;
        }
    }

    return { background, border };
}

/**
 * Destroy all chart instances
 */
function destroyAllCharts() {
    Object.values(dashboardCharts).forEach(chart => {
        if (chart && typeof chart.destroy === 'function') {
            chart.destroy();
        }
    });
    dashboardCharts = {};
}

/**
 * Clear all active filters
 */
function clearDashboardFilters() {
    dashboardActiveFilters = {
        type: new Set(),
        lifecycle: new Set(),
        status: new Set(),
        security: new Set(),
        editability: new Set(),
        mandatory: new Set(),
        origin: new Set(),
        classification: new Set(),
        orgUnit: new Set(),
        profile: new Set(),
        automation: new Set(),
        level: new Set(),
        frequency: new Set(),
        stage: new Set(),
        maturity: new Set(),
        probability: new Set(),
        complianceLevel: new Set(),
        roleType: new Set(),
        objectType: new Set(),
        roleAccepted: new Set()
    };
}

