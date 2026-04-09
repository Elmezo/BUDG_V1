// periodic-review-crud.js - CRUD Operations for Periodic Review Configuration
(function() {
    'use strict';

    var PR_CRUD_PREFIX = 'adminPanel.operatingModel.periodicReview';
    function crudT(key, fallback) {
        if (typeof adminT === 'function') return adminT(PR_CRUD_PREFIX + '.' + key, fallback);
        return fallback;
    }
    function crudEscape(s) {
        if (s == null) return '';
        var d = document.createElement('div');
        d.textContent = s;
        return d.innerHTML;
    }
    function crudTpl(str, vars) {
        if (!str || !vars) return str;
        var out = str;
        Object.keys(vars).forEach(function (k) {
            out = out.split('{{' + k + '}}').join(String(vars[k]));
        });
        return out;
    }

// Global variables
let lookupData = null;
let currentConfigId = null;
let currentPeriodicReviewModuleId = null;

/**
 * Load all lookup data from the server
 */
async function loadLookupData(entityId = null) {
    try {
        const url = entityId
            ? `/api/lookup-data?entityId=${entityId}`
            : '/api/lookup-data';

        const response = await fetch(url);
        const data = await response.json();

        if (data.success) {
            lookupData = data;
            return lookupData;
        } else {
            console.error('Failed to load lookup data');
            return null;
        }
    } catch (error) {
        console.error('Error loading lookup data:', error);
        return null;
    }
}

/**
 * Populate dropdown with data
 */
function populateDropdown(selectId, data, placeholder) {
    const select = document.getElementById(selectId);
    if (!select) return;
    var ph = placeholder != null ? placeholder : crudT('selectPlaceholder', 'Select...');

    select.innerHTML = '<option value="">' + crudEscape(ph) + '</option>';

    if (data && data.length > 0) {
        data.forEach(item => {
            const option = document.createElement('option');
            option.value = item.id;
            option.textContent = item.name || item.primaryName;
            if (item.isDefault) {
                option.selected = true;
            }
            select.appendChild(option);
        });
    }
}

/**
 * Populate all dropdowns in the form
 */
function populateAllDropdowns(moduleId) {
    if (!lookupData) return;

    // Filters Section
    populateDropdown('filterType', getTypeDataByModule(moduleId), crudT('selectType', 'Select Type'));
    populateDropdown('filterStatus', lookupData.status, crudT('selectBudgStatus', 'Select BUDG Status'));
    populateDropdown('filterLifecycle', getLifecycleDataByModule(moduleId), crudT('selectLifecycle', 'Select Lifecycle'));

    // Workflow and Change Request Section
    populateDropdown('crSystem', lookupData.system, crudT('selectSystem', 'Select System'));
    populateDropdown('crWorkflow', lookupData.processDefinition, crudT('selectWorkflow', 'Select Workflow'));
    populateDropdown('crType', lookupData.changeRequestType, crudT('selectDefaultType', 'Select Default Type'));
    populateDropdown('crUrgency', lookupData.changeRequestUrgency, crudT('selectDefaultUrgency', 'Select Default Urgency'));
    populateDropdown('crSeverity', lookupData.changeRequestSeverity, crudT('selectDefaultSeverity', 'Select Default Severity'));
}

/**
 * Get Type data based on module ID
 */
function getTypeDataByModule(moduleId) {
    if (!lookupData) return [];

    const moduleTypes = {
        '11': lookupData.datasetType,      // Dataset
        '12': lookupData.glossaryType,     // Glossary
        '13': lookupData.systemType,       // System
        '14': lookupData.processType       // Process
    };

    return moduleTypes[moduleId] || [];
}

/**
 * Get Lifecycle data based on module ID
 */
function getLifecycleDataByModule(moduleId) {
    if (!lookupData) return [];

    const moduleLifecycles = {
        '11': lookupData.datasetLifecycle,        // Dataset
        '12': lookupData.glossaryLifecycle,       // Glossary
        '13': lookupData.systemLifecycle,         // System
        '14': lookupData.processLifecycleStatus   // Process
    };

    return moduleLifecycles[moduleId] || [];
}

/**
 * Get configuration by ID for editing
 */
async function getConfigurationById(configId) {
    try {
        const response = await fetch(`/api/periodic-review-config?action=getById&id=${configId}`);
        const data = await response.json();

        if (data.success && data.data) {
            return data.data;
        } else {
            if (window.showAdminNotification) window.showAdminNotification(crudT('crudFailedLoadConfig', 'Failed to load configuration'), 'error');
            return null;
        }
    } catch (error) {
        console.error('Error loading configuration:', error);
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudErrorLoadConfig', 'Error loading configuration'), 'error');
        return null;
    }
}

/**
 * Populate form with configuration data for editing
 */
function populateFormWithData(config) {
    // Definition Section
    setValue('configName', config.name);
    setValue('configDescription', config.description);

    // Periodic Review Date and Recurrence
    setValue('startDate', config.reviewStartDate);
    setValue('generateDays', config.threshold || 15);

    // Parse and set recurrence
    if (config.recurrenceConfig) {
        try {
            const recurrence = JSON.parse(config.recurrenceConfig);
            setRecurrenceFromData(recurrence);
        } catch (e) {
            console.error('Error parsing recurrence config:', e);
        }
    }

    // Parse and set filters
    if (config.filterConfig) {
        try {
            const filters = JSON.parse(config.filterConfig);
            setValue('filterType', filters.type);
            setValue('filterStatus', filters.status);
            setValue('filterLifecycle', filters.lifecycle);
        } catch (e) {
            console.error('Error parsing filter config:', e);
        }
    }

    // Workflow and Change Request Configuration
    setValue('crTitle', config.crTitle);
    setValue('crSummary', config.crSummary);
    setValue('crSystem', config.crProviderRef);
    setValue('crWorkflow', config.workflowId);
    setValue('crType', config.crType);
    setValue('crUrgency', config.crUrgency);
    setValue('crSeverity', config.crSeverity);
    setValue('crInitiatorEmail', config.crInitiatorEmail);
}

/**
 * Helper function to set value safely
 */
function setValue(elementId, value) {
    const element = document.getElementById(elementId);
    if (element && value !== null && value !== undefined) {
        element.value = value;
    }
}

/**
 * Set recurrence fields from data
 */
function setRecurrenceFromData(recurrence) {
    const summaryInput = document.getElementById('recurrenceSummary');
    if (summaryInput && recurrence.summary) {
        summaryInput.value = recurrence.summary;
    }

    // Set modal fields if needed
    if (recurrence.type) {
        setValue('prrFrequencyType', recurrence.type);
    }
    if (recurrence.value) {
        setValue('prrYearlyMonths', recurrence.value);
    }
}

/**
 * Collect form data for saving
 */
function collectFormData() {
    // Definition
    const name = document.getElementById('configName')?.value;
    const description = document.getElementById('configDescription')?.value;

    // Validation
    if (!name || name.length < 6 || name.length > 256) {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudNameLength', 'Name must be between 6 and 256 characters'), 'error');
        return null;
    }

    if (!description || description.length < 6 || description.length > 256) {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudDescriptionLength', 'Description must be between 6 and 256 characters'), 'error');
        return null;
    }

    // Periodic Review Date and Recurrence
    const startDate = document.getElementById('startDate')?.value;
    const generateDays = document.getElementById('generateDays')?.value;

    if (!startDate) {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudSelectStartDate', 'Please select a periodic review start date'), 'error');
        return null;
    }

    // Recurrence Config
    const recurrenceType = document.getElementById('prrFrequencyType')?.value;
    const recurrenceSummary = document.getElementById('recurrenceSummary')?.value;
    const recurrenceConfig = {
        type: recurrenceType || 'none',
        summary: recurrenceSummary || crudT('doNotRecur', 'Do Not Recur')
    };

    if (recurrenceType === 'yearly' || recurrenceType === 'monthly') {
        const selectedRadio = document.querySelector('input[name="prrFrequencyRadio"]:checked');
        if (selectedRadio) {
            recurrenceConfig.mode = selectedRadio.value;

            if (selectedRadio.value === 'yearly_prr') {
                recurrenceConfig.value = document.getElementById('prrYearlyMonths')?.value;
            } else if (selectedRadio.value === 'monthly_prr') {
                recurrenceConfig.day = document.getElementById('prrMonthlyDay')?.value;
                recurrenceConfig.month = document.getElementById('prrMonthlyMonth')?.value;
                recurrenceConfig.every = document.getElementById('prrMonthlyEvery')?.value;
            } else if (selectedRadio.value === 'weekly_prr') {
                recurrenceConfig.week = document.getElementById('prrWeeklyWeek')?.value;
                recurrenceConfig.weekday = document.getElementById('prrWeeklyDay')?.value;
                recurrenceConfig.month = document.getElementById('prrWeeklyMonth')?.value;
                recurrenceConfig.every = document.getElementById('prrWeeklyEvery')?.value;
            }
        }
    }

    // Filters
    const filterType = document.getElementById('filterType')?.value;
    const filterStatus = document.getElementById('filterStatus')?.value;
    const filterLifecycle = document.getElementById('filterLifecycle')?.value;

    const filterConfig = {
        type: filterType || null,
        status: filterStatus || null,
        lifecycle: filterLifecycle || null
    };

    // Workflow and Change Request
    const crTitle = document.getElementById('crTitle')?.value;
    const crSummary = document.getElementById('crSummary')?.value;
    const crSystem = document.getElementById('crSystem')?.value;
    const crWorkflow = document.getElementById('crWorkflow')?.value;
    const crType = document.getElementById('crType')?.value;
    const crUrgency = document.getElementById('crUrgency')?.value;
    const crSeverity = document.getElementById('crSeverity')?.value;
    const crInitiatorEmail = document.getElementById('crInitiatorEmail')?.value;

    if (!crTitle || crTitle.length < 6 || crTitle.length > 256) {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudCrTitleLength', 'Change Request Title must be between 6 and 256 characters'), 'error');
        return null;
    }

    if (!crSummary || crSummary.length < 6 || crSummary.length > 256) {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudCrSummaryLength', 'Change Request Summary must be between 6 and 256 characters'), 'error');
        return null;
    }

    if (!crSystem || !crWorkflow || !crType || !crUrgency || !crSeverity) {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudCrRequiredFields', 'Please fill all required Change Request fields'), 'error');
        return null;
    }

    if (!crInitiatorEmail || crInitiatorEmail.trim() === '') {
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudEnterUserEmail', 'Please enter User Email'), 'error');
        return null;
    }

    // Build the data object
    const data = {
        moduleId: parseInt(currentPeriodicReviewModuleId),
        name: name,
        description: description,
        filterConfig: JSON.stringify(filterConfig),
        threshold: parseInt(generateDays) || 15,
        reviewStartDate: startDate,
        recurrenceConfig: JSON.stringify(recurrenceConfig),
        workflowId: crWorkflow,
        crProviderRef: crSystem,
        crTitle: crTitle,
        crSummary: crSummary,
        crType: parseInt(crType),
        crSeverity: parseInt(crSeverity),
        crUrgency: parseInt(crUrgency),
        crInitiatorEmail: crInitiatorEmail,
        isEnabled: true,
        createdById: 1, // TODO: Get from session
        lastUpdatedById: 1 // TODO: Get from session
    };

    // Add ID if updating
    if (currentConfigId) {
        data.id = currentConfigId;
    }

    return data;
}

/**
 * Save configuration (Create or Update)
 */
async function saveConfiguration() {
    const data = collectFormData();
    if (!data) return false;

    try {
        const isUpdate = currentConfigId !== null;
        const url = '/api/periodic-review-config';
        const method = isUpdate ? 'PUT' : 'POST';

        const response = await fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (result.success) {
            if (window.showAdminNotification) window.showAdminNotification(isUpdate ? crudT('crudUpdatedSuccess', 'Configuration updated successfully') : crudT('crudCreatedSuccess', 'Configuration created successfully'), 'success');
            return true;
        } else {
            if (window.showAdminNotification) window.showAdminNotification(crudTpl(crudT('crudFailedSave', 'Failed to save configuration: {{message}}'), {
                message: result.message || crudT('unknown', 'Unknown error')
            }), 'error');
            return false;
        }
    } catch (error) {
        console.error('Error saving configuration:', error);
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudErrorSave', 'Error saving configuration'), 'error');
        return false;
    }
}

/**
 * Delete configuration
 */
async function deleteConfiguration(configId) {
    if (!confirm(crudT('crudConfirmDelete', 'Are you sure you want to delete this configuration?'))) {
        return false;
    }

    try {
        const response = await fetch(`/api/periodic-review-config?id=${configId}`, {
            method: 'DELETE'
        });

        const result = await response.json();

        if (result.success) {
            if (window.showAdminNotification) window.showAdminNotification(crudT('crudDeletedSuccess', 'Configuration deleted successfully'), 'success');
            return true;
        } else {
            if (window.showAdminNotification) window.showAdminNotification(crudTpl(crudT('crudFailedDelete', 'Failed to delete configuration: {{message}}'), {
                message: result.message || crudT('unknown', 'Unknown error')
            }), 'error');
            return false;
        }
    } catch (error) {
        console.error('Error deleting configuration:', error);
        if (window.showAdminNotification) window.showAdminNotification(crudT('crudErrorDelete', 'Error deleting configuration'), 'error');
        return false;
    }
}

/**
 * Initialize form for creating new configuration
 */
async function initializeNewConfigForm(moduleId, entityId = null) {
    currentConfigId = null;
    currentPeriodicReviewModuleId = moduleId;

    // Load lookup data
    await loadLookupData(entityId);

    // Populate dropdowns
    populateAllDropdowns(moduleId);

    // Set default values
    const today = new Date().toISOString().split('T')[0];
    setValue('startDate', today);
    setValue('generateDays', 15);
    setValue('recurrenceSummary', 'Do Not Recur');
}

/**
 * Initialize form for editing existing configuration
 */
async function initializeEditConfigForm(configId, moduleId, entityId = null) {
    currentConfigId = configId;
    currentPeriodicReviewModuleId = moduleId;

    // Load lookup data first
    await loadLookupData(entityId);

    // Populate dropdowns
    populateAllDropdowns(moduleId);

    // Load and populate configuration data
    const config = await getConfigurationById(configId);
    if (config) {
        populateFormWithData(config);
    }
}

// Export functions for global access
window.periodicReviewCRUD = {
    initializeNewConfigForm,
    initializeEditConfigForm,
    saveConfiguration,
    deleteConfiguration,
    loadLookupData,
    getConfigurationById
};

})(); // End IIFE