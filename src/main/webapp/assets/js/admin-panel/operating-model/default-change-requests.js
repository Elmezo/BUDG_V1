// Default Change Requests functionality for Admin Panel

// Store original settings to detect changes (scoped to avoid conflicts with other modules)
const DefaultChangeRequestsState = {
    originalSettings: null,
    originalTypeSettings: null
};

var DFCR_PREFIX = 'adminPanel.operatingModel.defaultChangeRequests';
function dfcrT(key, fallback) {
    if (typeof adminT === 'function') return adminT(DFCR_PREFIX + '.' + key, fallback);
    return fallback;
}
function dfcrEscape(s) {
    if (s == null) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}
function dfcrTpl(str, vars) {
    if (!str || !vars) return str;
    var out = str;
    Object.keys(vars).forEach(function (k) {
        out = out.split('{{' + k + '}}').join(String(vars[k]));
    });
    return out;
}

async function showDefaultChangeRequestsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.defaultChangeRequests');
    var pageTitle = dfcrT('pageTitle', 'Default Change Requests');
    var budgMgmt = typeof adminT === 'function' ? adminT('adminPanel.header.budgManagement', 'BUDG Management') : 'BUDG Management';
    updateSystemTitle(pageTitle, budgMgmt);

    var h = dfcrEscape;
    var t = dfcrT;
    contentArea.innerHTML = `
        <div class="default-change-requests-container">
            <div class="page-header">
                <h2>${h(t('pageHeading', 'DEFAULT CHANGE REQUESTS'))}</h2>
            </div>

            <!-- Alert message for workflow change prevention -->
            <div id="workflowChangeAlert" class="alert alert-warning" style="display: none; position: fixed; top: 20px; right: 20px; z-index: 10000; max-width: 400px; background-color: #ff9800; color: white; padding: 1rem; border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.2);">
                <div style="display: flex; align-items: center; gap: 0.75rem;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 1.25rem;"></i>
                    <span id="workflowChangeAlertMessage" style="flex: 1;">${h(t('workflowAlertCannotChange', 'Default Workflow cannot be changed while the workflow is used in an active Change Request.'))}</span>
                    <button type="button" id="closeWorkflowAlertBtn" style="background: none; border: none; color: white; cursor: pointer; font-size: 1.25rem; padding: 0; margin-left: 0.5rem;">&times;</button>
                </div>
            </div>

            <div class="facet-selector-section">
                <div class="form-group">
                    <label for="facetSelect">${h(t('facetLabel', 'Facet'))} <span class="required">*</span></label>
                    <select id="facetSelect" class="form-control">
                        <option value="">${h(t('selectFacet', 'Select a Facet'))}</option>
                        <option value="Glossary">${h(t('facetGlossary', 'Glossary'))}</option>
                        <option value="Data Set">${h(t('facetDataSet', 'Data Set'))}</option>
                        <option value="Process">${h(t('facetProcess', 'Process'))}</option>
                        <option value="System">${h(t('facetSystem', 'System'))}</option>
                    </select>
                </div>
            </div>

            <div id="settingsFormContainer" class="settings-form-container" style="display: none;">
                <div class="settings-section">
                    <div class="approval-toggles">
                        <div class="toggle-group">
                            <label class="toggle-label">
                                <span>${h(t('enableWorkflowApproval', 'Enable Workflow Approval'))}</span>
                                <label class="switch">
                                    <input type="checkbox" id="workflowApprovalEnabled">
                                    <span class="slider round"></span>
                                </label>
                            </label>
                        </div>

                        <div class="toggle-group">
                            <label class="toggle-label">
                                <span>${h(t('enableWorkflowForFacetTypes', 'Enable Workflow for Facet Types'))}</span>
                                <label class="switch">
                                    <input type="checkbox" id="workflowForTypesEnabled" disabled>
                                    <span class="slider round"></span>
                                </label>
                            </label>
                            <small id="workflowForTypesHint" style="display: none; color: #888; font-style: italic; margin-top: 4px; display: block;">
                                ${h(t('enableWorkflowApprovalFirstHint', 'Please enable "Workflow Approval" first'))}
                            </small>
                        </div>
                    </div>
                </div>

                <div id="workflowSettingsSection" class="settings-section" style="display: none;">
                    <h3>${h(t('configureWorkflowApprovalHeading', 'CONFIGURE WORKFLOW APPROVAL SETTINGS'))}</h3>

                    <div class="workflow-settings-tabs">
                        <button type="button" class="workflow-tab active" data-tab="facet">${h(t('tabSettingsForFacet', 'Settings for Facet'))}</button>
                        <button type="button" class="workflow-tab" data-tab="facet-types" style="display: none;">${h(t('tabSettingsForFacetTypes', 'Settings for Facet Types'))}</button>
                    </div>

                    <div id="settingsForFacetContent" class="tab-content active">
                    <div class="form-row">
                        <div class="form-group">
                            <label for="defaultChangeRequestSystem">${h(t('defaultChangeRequestSystemLabel', 'Default Change Request System'))} <span class="required">*</span></label>
                            <select id="defaultChangeRequestSystem" class="form-control">
                                <option value="Native">${h(t('systemNative', 'Native'))}</option>
                                <option value="ServiceNow">${h(t('systemServiceNow', 'ServiceNow'))}</option>
                                <option value="JIRA">${h(t('systemJira', 'JIRA'))}</option>
                            </select>
                        </div>
                    </div>

                    <div class="form-row">
                        <div class="form-group">
                            <label for="defaultWorkflowForCreating">${h(t('defaultWorkflowForCreatingLabel', 'Default Workflow for Creating Object'))} <span class="required">*</span></label>
                            <select id="defaultWorkflowForCreating" class="form-control">
                                <option value="">${h(t('selectWorkflow', 'Select a workflow'))}</option>
                            </select>
                        </div>

                        <div class="form-group">
                            <label for="defaultWorkflowForEditing">${h(t('defaultWorkflowForEditingLabel', 'Default Workflow for Editing Object'))} <span class="required">*</span></label>
                            <select id="defaultWorkflowForEditing" class="form-control">
                                <option value="">${h(t('selectWorkflow', 'Select a workflow'))}</option>
                            </select>
                        </div>
                    </div>

                    <div class="form-row">
                        <div class="form-group">
                        <label for="defaultBUDGStatusForCreating">${h(t('defaultBudgStatusForCreatingLabel', 'Default BUDG Status for Creating Object'))} <span class="required">*</span></label>
                        <select id="defaultBUDGStatusForCreating" class="form-control">
                            <option value="">${h(t('selectDefaultBudgStatus', 'Select Default BUDG Status'))}</option>
                        </select>
                        </div>

                        <div class="form-group">
                            <label for="defaultLifecycleForCreating">${h(t('defaultLifecycleForCreatingLabel', 'Default Lifecycle for Creating Object'))} <span class="required">*</span></label>
                            <select id="defaultLifecycleForCreating" class="form-control">
                                <option value="">${h(t('selectDefaultLifecycle', 'Select Default Lifecycle'))}</option>
                            </select>
                        </div>
                    </div>

                    <div class="form-row">
                        <div class="form-group">
                            <label for="defaultChangeRequestType">${h(t('defaultChangeRequestTypeLabel', 'Default Change Request Type'))} <span class="required">*</span></label>
                            <select id="defaultChangeRequestType" class="form-control">
                                <option value="">${h(t('selectDefaultType', 'Select Default Type'))}</option>
                            </select>
                        </div>

                        <div class="form-group">
                            <label for="defaultChangeRequestUrgency">${h(t('defaultChangeRequestUrgencyLabel', 'Default Change Request Urgency'))} <span class="required">*</span></label>
                            <select id="defaultChangeRequestUrgency" class="form-control">
                                <option value="">${h(t('selectDefaultUrgency', 'Select Default Urgency'))}</option>
                            </select>
                        </div>
                    </div>

                    <div class="form-row">
                        <div class="form-group">
                            <label for="defaultChangeRequestSeverity">${h(t('defaultChangeRequestSeverityLabel', 'Default Change Request Severity'))} <span class="required">*</span></label>
                            <select id="defaultChangeRequestSeverity" class="form-control">
                                <option value="">${h(t('selectDefaultSeverity', 'Select Default Severity'))}</option>
                            </select>
                        </div>
                    </div>

                    <div class="approval-admin-toggle">
                        <label class="toggle-label">
                            <span>${h(t('enableWorkflowApprovalForAdmins', 'Enable Workflow Approval for Administrators'))}</span>
                            <label class="switch">
                                <input type="checkbox" id="enableWorkflowApprovalForAdministrators">
                                <span class="slider round"></span>
                            </label>
                        </label>
                    </div>

                    <div class="stakeholder-roles-section-wrapper">
                        <h3>${h(t('stakeholderRolesHeading', 'DEFAULT STAKEHOLDER ROLES & RESPONSIBILITIES FOR SELECTED FACET'))}</h3>
                        <div class="stakeholder-table-container">
                            <table class="stakeholder-roles-table">
                                <thead>
                                    <tr>
                                        <th>${h(t('tableRoleName', 'Role Name'))}</th>
                                        <th>${h(t('tableDescription', 'Description'))}</th>
                                        <th>${h(t('tableRoleType', 'Role Type'))}</th>
                                    </tr>
                                </thead>
                                <tbody id="stakeholderRolesTableBody">
                                    <!-- Populated dynamically -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                    </div>

                    <div id="settingsForFacetTypesContent" class="tab-content" style="display: none;">
                        <div class="facet-types-table-container">
                            <table class="facet-types-table">
                                <thead>
                                    <tr>
                                        <th>${h(t('tableFacetType', 'Facet Type'))}</th>
                                        <th>${h(t('tableDefaultWorkflowCreating', 'Default Workflow for Creating Object'))}</th>
                                        <th>${h(t('tableDefaultWorkflowEditing', 'Default Workflow for Editing Object'))}</th>
                                        <th>${h(t('tableDefaultCrType', 'Default Change Request Type'))}</th>
                                    </tr>
                                </thead>
                                <tbody id="facetTypesTableBody">
                                    <!-- Populated dynamically -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                <div class="form-actions">
                    <button type="button" class="btn btn-primary" id="saveSettingsBtn">${h(t('save', 'Save'))}</button>
                    <button type="button" class="btn btn-secondary" id="closeSettingsBtn">${h(t('close', 'Close'))}</button>
                </div>
            </div>
        </div>
    `;

    // Initialize event listeners
    initializeDefaultChangeRequestsPage();
    
    // Setup alert close button
    setTimeout(() => {
        const closeAlertBtn = document.getElementById('closeWorkflowAlertBtn');
        if (closeAlertBtn) {
            closeAlertBtn.addEventListener('click', () => {
                const alert = document.getElementById('workflowChangeAlert');
                if (alert) {
                    alert.style.display = 'none';
                }
            });
        }
    }, 100);
}

function initializeDefaultChangeRequestsPage() {
    const facetSelect = document.getElementById('facetSelect');
    const workflowApprovalEnabled = document.getElementById('workflowApprovalEnabled');
    const workflowForTypesEnabled = document.getElementById('workflowForTypesEnabled');
    const settingsFormContainer = document.getElementById('settingsFormContainer');
    const workflowSettingsSection = document.getElementById('workflowSettingsSection');
    const saveBtn = document.getElementById('saveSettingsBtn');
    const closeBtn = document.getElementById('closeSettingsBtn');

    // Dropdown data will be loaded when "Enable Workflow Approval" is toggled

    // Facet selection
    facetSelect.addEventListener('change', async (e) => {
        const facet = e.target.value;
        if (facet) {
            settingsFormContainer.style.display = 'block';
            
            // Hide/show "Enable Workflow for Facet Types" toggle and tab for Process facet
            const isProcessFacet = facet.toLowerCase() === 'process';
            const workflowForTypesToggle = document.getElementById('workflowForTypesEnabled')?.closest('.toggle-group');
            const facetTypesTab = document.querySelector('.workflow-tab[data-tab="facet-types"]');
            
            if (isProcessFacet) {
                // Hide toggle and tab for Process
                if (workflowForTypesToggle) {
                    workflowForTypesToggle.style.display = 'none';
                }
                if (facetTypesTab) {
                    facetTypesTab.style.display = 'none';
                }
                // Also hide the tab content
                const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
                if (facetTypesContent) {
                    facetTypesContent.style.display = 'none';
                }
            } else {
                // Show toggle for other facets (tab visibility controlled by toggle state)
                if (workflowForTypesToggle) {
                    workflowForTypesToggle.style.display = '';
                }
            }
            
            await loadFacetSettings(facet); // This already loads lifecycle and sets the value
            await loadStakeholderRoles(facet); // Load roles for the selected facet only
            
            // If we're on the facet-types tab, reload the table after facet change
            const isFacetTypesTabActive = facetTypesTab?.classList.contains('active');
            if (isFacetTypesTabActive && !isProcessFacet) {
                const workflowApprovalEnabled = document.getElementById('workflowApprovalEnabled')?.checked;
                const workflowForTypesEnabled = document.getElementById('workflowForTypesEnabled')?.checked;
                if (workflowApprovalEnabled && workflowForTypesEnabled) {
                    await loadFacetTypesTable();
                }
            }
            
            // NOTE: Do NOT call loadLifecycleForFacet here - it's already called inside loadFacetSettings
            // and calling it again would reset the dropdown and clear the selected value
        } else {
            settingsFormContainer.style.display = 'none';
        }
    });

    // Workflow approval toggle
    workflowApprovalEnabled.addEventListener('change', async (e) => {
        const isBeingDisabled = !e.target.checked;
        const originalValue = e.target.dataset.originalValue === 'true';
        
        // If trying to disable workflow approval, check if there are active CRs using default workflows
        if (isBeingDisabled && originalValue) {
            const facet = document.getElementById('facetSelect').value;
            if (facet) {
                const canDisable = await validateWorkflowApprovalDisable(facet);
                if (!canDisable) {
                    // Revert checkbox to checked state
                    e.target.checked = true;
                    return;
                }
            }
        }
        
        const workflowForTypesCheckbox = document.getElementById('workflowForTypesEnabled');
        const workflowForTypesHint = document.getElementById('workflowForTypesHint');
        
        workflowSettingsSection.style.display = e.target.checked ? 'block' : 'none';
        if (e.target.checked) {
            // Enable the "Workflow for Facet Types" toggle when workflow approval is enabled
            if (workflowForTypesCheckbox) {
                workflowForTypesCheckbox.disabled = false;
            }
            if (workflowForTypesHint) {
                workflowForTypesHint.style.display = 'none';
            }
            
            // Load dropdown data from database
            await loadWorkflowDropdowns();
            await loadChangeRequestDropdowns();
            await loadStatusAndLifecycleDropdowns();
            
            // Load lifecycle for current facet if selected
            const currentFacet = document.getElementById('facetSelect').value;
            if (currentFacet) {
                await loadLifecycleForFacet(currentFacet);
            }
            
            // Load facet types table if workflow for types is also enabled
            const workflowForTypesEnabled = document.getElementById('workflowForTypesEnabled').checked;
            if (workflowForTypesEnabled && currentFacet) {
                await loadFacetTypesTable();
            }
            
            // If we're on the facet-types tab, ensure table is visible/reloaded
            const isFacetTypesTabActive = document.querySelector('.workflow-tab[data-tab="facet-types"]')?.classList.contains('active');
            if (isFacetTypesTabActive) {
                await switchWorkflowTab('facet-types');
            }
        } else {
            // Disable and uncheck "Workflow for Facet Types" when workflow approval is disabled
            if (workflowForTypesCheckbox) {
                workflowForTypesCheckbox.disabled = true;
                workflowForTypesCheckbox.checked = false;
            }
            if (workflowForTypesHint) {
                workflowForTypesHint.style.display = 'block';
            }
            
            // Hide the facet types tab
            const facetTypesTab = document.querySelector('.workflow-tab[data-tab="facet-types"]');
            if (facetTypesTab) {
                facetTypesTab.style.display = 'none';
            }
            
            // Hide the facet types content
            const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
            if (facetTypesContent) {
                facetTypesContent.style.display = 'none';
            }
            
            // Clear the facet types table
            const tbody = document.getElementById('facetTypesTableBody');
            if (tbody) {
                tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('pleaseEnableWorkflowFirstRow', 'Please enable "Workflow Approval" first')) + '</td></tr>';
            }
            
            // Switch back to facet tab if types tab was active
            const isFacetTypesTabActive = document.querySelector('.workflow-tab[data-tab="facet-types"]')?.classList.contains('active');
            if (isFacetTypesTabActive) {
                await switchWorkflowTab('facet');
            }
        }
    });

    // Workflow for Facet Types toggle
    workflowForTypesEnabled.addEventListener('change', async (e) => {
        const workflowApprovalEnabled = document.getElementById('workflowApprovalEnabled').checked;
        
        // Prevent enabling if workflow approval is not enabled
        if (e.target.checked && !workflowApprovalEnabled) {
            e.target.checked = false;
            showNotification(dfcrT('alertEnableWorkflowFirst', 'Please enable "Workflow Approval" first before enabling "Workflow for Facet Types".'), 'error');
            return;
        }
        
        const facetTypesTab = document.querySelector('.workflow-tab[data-tab="facet-types"]');
        const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
        
        if (facetTypesTab) {
            facetTypesTab.style.display = e.target.checked ? 'inline-block' : 'none';
            
            if (!e.target.checked) {
                // Hide the tab content completely when toggle is off
                if (facetTypesContent) {
                    facetTypesContent.style.display = 'none';
                }
                
                // Clear the table body
                const tbody = document.getElementById('facetTypesTableBody');
                if (tbody) {
                    tbody.innerHTML = '';
                }
                
                // Switch back to facet tab if types tab was active
                const isFacetTypesTabActive = facetTypesTab.classList.contains('active');
                if (isFacetTypesTabActive) {
                    await switchWorkflowTab('facet');
                }
            } else {
                // If enabling, show the content and load the facet types table
                if (facetTypesContent) {
                    facetTypesContent.style.display = 'block';
                }
                
                const currentFacet = document.getElementById('facetSelect').value;
                if (currentFacet && workflowApprovalEnabled) {
                    await loadFacetTypesTable();
                }
                
                // If we're already on the facet-types tab, ensure table is visible
                const isFacetTypesTabActive = facetTypesTab.classList.contains('active');
                if (isFacetTypesTabActive) {
                    await switchWorkflowTab('facet-types');
                }
            }
        }
    });

    // Tab switching
    document.addEventListener('click', (e) => {
        if (e.target.classList.contains('workflow-tab')) {
            const tab = e.target.dataset.tab;
            switchWorkflowTab(tab);
        }
    });

    // Restore defaults button removed - no longer needed

    // Save button
    saveBtn.addEventListener('click', saveDefaultChangeRequestSettings);

    // Close button
    closeBtn.addEventListener('click', () => {
        facetSelect.value = '';
        settingsFormContainer.style.display = 'none';
        resetForm();
        clearDFCRFieldErrors();
    });
    
    // Add event listeners to clear field errors when user changes values
    const mandatoryFields = [
        'facetSelect',
        'defaultChangeRequestSystem',
        'defaultWorkflowForCreating',
        'defaultWorkflowForEditing',
        'defaultBUDGStatusForCreating',
        'defaultLifecycleForCreating',
        'defaultChangeRequestType',
        'defaultChangeRequestUrgency',
        'defaultChangeRequestSeverity'
    ];
    
    mandatoryFields.forEach(fieldId => {
        const field = document.getElementById(fieldId);
        if (field) {
            field.addEventListener('change', () => {
                // Clear error for this specific field when user changes it
                const fieldElement = document.getElementById(fieldId);
                if (fieldElement) {
                    fieldElement.style.borderColor = '';
                    fieldElement.style.borderWidth = '';
                    const errorMessage = fieldElement.parentElement.querySelector('.field-error-message');
                    if (errorMessage) {
                        errorMessage.remove();
                    }
                }
            });
        }
    });
}

async function loadFacetSettings(facet) {
    try {
        const response = await fetch(`/admin/api/default-change-requests?facet=${encodeURIComponent(facet)}`);
        if (!response.ok) throw new Error('Failed to load settings');

        const settings = await response.json();
        console.log('[DFCR] Loaded settings for facet:', facet, settings);

        // Always load dropdown data first so saved IDs have matching options to render
        await loadWorkflowDropdowns();
        await loadChangeRequestDropdowns();
        await loadStatusAndLifecycleDropdowns();
        await loadLifecycleForFacet(facet);

        // Populate form fields
        const workflowApprovalEnabled = settings.workflowApprovalEnabled || false;
        const workflowApprovalCheckbox = document.getElementById('workflowApprovalEnabled');
        workflowApprovalCheckbox.checked = workflowApprovalEnabled;
        // Store original state for validation
        workflowApprovalCheckbox.dataset.originalValue = workflowApprovalEnabled ? 'true' : 'false';
        document.getElementById('defaultChangeRequestSystem').value = settings.defaultChangeRequestSystem || 'Native';
        
        // Enable/disable "Workflow for Facet Types" toggle based on workflow approval status
        const workflowForTypesCheckbox = document.getElementById('workflowForTypesEnabled');
        const workflowForTypesHint = document.getElementById('workflowForTypesHint');
        if (workflowForTypesCheckbox) {
            workflowForTypesCheckbox.disabled = !workflowApprovalEnabled;
            // Only set checked if workflow approval is enabled, otherwise force to false
            if (workflowApprovalEnabled) {
                workflowForTypesCheckbox.checked = settings.workflowForTypesEnabled || false;
            } else {
                workflowForTypesCheckbox.checked = false;
            }
        }
        if (workflowForTypesHint) {
            workflowForTypesHint.style.display = workflowApprovalEnabled ? 'none' : 'block';
        }
        
        // Hide "Enable Workflow for Facet Types" toggle and tab for Process facet
        const isProcessFacet = facet.toLowerCase() === 'process';
        const workflowForTypesToggle = document.getElementById('workflowForTypesEnabled')?.closest('.toggle-group');
        const facetTypesTab = document.querySelector('.workflow-tab[data-tab="facet-types"]');
        
        if (isProcessFacet) {
            // Hide toggle and tab for Process
            if (workflowForTypesToggle) {
                workflowForTypesToggle.style.display = 'none';
            }
            if (facetTypesTab) {
                facetTypesTab.style.display = 'none';
            }
            // Also hide the tab content
            const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
            if (facetTypesContent) {
                facetTypesContent.style.display = 'none';
            }
        } else {
            // Show toggle and tab for other facets
            if (workflowForTypesToggle) {
                workflowForTypesToggle.style.display = '';
            }
            // Tab visibility is controlled by the toggle state
        }
        // Store original workflow values for validation
        const creatingSelect = document.getElementById('defaultWorkflowForCreating');
        const editingSelect = document.getElementById('defaultWorkflowForEditing');
        
        if (creatingSelect) {
            creatingSelect.dataset.originalValue = settings.defaultWorkflowForCreating || '';
            creatingSelect.value = settings.defaultWorkflowForCreating || '';
        }
        if (editingSelect) {
            editingSelect.dataset.originalValue = settings.defaultWorkflowForEditing || '';
            editingSelect.value = settings.defaultWorkflowForEditing || '';
        }
        
        // Setup workflow change validation
        setupWorkflowChangeValidation();
        document.getElementById('defaultBUDGStatusForCreating').value = settings.defaultBUDGStatusForCreating || '';
        const lifecycleSelect = document.getElementById('defaultLifecycleForCreating');
        let savedLifecycleRaw = settings.defaultLifecycleForCreating || '';
        const facetKey = getFacetKey(facet);

        // Normalize legacy/partial saved values to the canonical "{facetKey}_{id}" form
        if (savedLifecycleRaw && typeof savedLifecycleRaw === 'string') {
            const lowerSaved = savedLifecycleRaw.toLowerCase().trim();

            // If only an ID was stored, prefix with facetKey
            if (!lowerSaved.includes('_')) {
                savedLifecycleRaw = `${facetKey}_${lowerSaved}`;
            } else {
                const [prefix, idPart] = lowerSaved.split('_');
                // Legacy dataset prefix "data" -> "dataset"
                if (prefix === 'data' && facetKey === 'dataset') {
                    savedLifecycleRaw = `${facetKey}_${idPart}`;
                } else if (prefix !== facetKey && idPart) {
                    // Mismatch prefix: realign to current facetKey while keeping the ID
                    savedLifecycleRaw = `${facetKey}_${idPart}`;
                }
            }
        }
        const savedLifecycle = String(savedLifecycleRaw);
        const savedLcLower = savedLifecycle.toLowerCase();

        // Restore lifecycle with robust matching/fallback
        const idPart = savedLcLower.includes('_') ? savedLcLower.split('_').pop() : savedLcLower;

        const trySelect = (candidate) => {
            if (!candidate) return false;
            const match = Array.from(lifecycleSelect.options).find(
                opt => (opt.value || '').toLowerCase().trim() === candidate.toLowerCase().trim()
            );
            if (match) {
                lifecycleSelect.value = match.value;
                return true;
            }
            return false;
        };

        // 1) exact value
        let matched = trySelect(savedLifecycle);

        // 2) suffix match by id
        if (!matched && idPart) {
            const suffixMatch = Array.from(lifecycleSelect.options).find(
                opt => (opt.value || '').toLowerCase().trim().endsWith(`_${idPart}`)
            );
            if (suffixMatch) {
                lifecycleSelect.value = suffixMatch.value;
                matched = true;
            }
        }

        // 3) add fallback option if still not matched
        if (!matched && savedLifecycle) {
            const lcList = lifecycleOptionCache[facetKey] || [];
            const cached = lcList.find(lc => (lc.id || '').toLowerCase().trim() === savedLcLower)
                || lcList.find(lc => (lc.id || '').toLowerCase().trim().endsWith(`_${idPart}`));
            const label = cached ? cached.name : `(saved) Lifecycle ${savedLifecycle}`;
            const newOpt = new Option(label, savedLifecycle);
            lifecycleSelect.add(newOpt);
            lifecycleSelect.value = savedLifecycle;
            matched = true;
        }

        if (!matched) {
            lifecycleSelect.value = '';
        }

        // Force visual update
        lifecycleSelect.dispatchEvent(new Event('change'));
        lifecycleSelect.dispatchEvent(new Event('input')); // some UIs listen to input for redraw
        
        // If value is set but placeholder still selected, force the correct option and remove placeholder
        if (lifecycleSelect.value) {
            for (let i = 0; i < lifecycleSelect.options.length; i++) {
                if (lifecycleSelect.options[i].value === lifecycleSelect.value) {
                    lifecycleSelect.selectedIndex = i;
                    break;
                }
            }
            const placeholderOpt = lifecycleSelect.querySelector('option[value=""]');
            if (placeholderOpt && lifecycleSelect.selectedIndex === 0) {
                // Remove placeholder to avoid showing it when a value is set
                lifecycleSelect.removeChild(placeholderOpt);
            }
        }

        // Debug: log current state
        const selectedOption = lifecycleSelect.options[lifecycleSelect.selectedIndex];
        console.log('Saved lifecycle:', savedLifecycle, 'Selected VALUE:', lifecycleSelect.value, 'Selected TEXT:', selectedOption ? selectedOption.text : 'NONE');
        console.log('All Options:', Array.from(lifecycleSelect.options).map(o => ({value:o.value, text:o.text, selected:o.selected})));
        console.log('[DFCR] Facet:', facet, 'facetKey:', facetKey, 'savedLcLower:', savedLcLower, 'idPart:', idPart);
        document.getElementById('defaultChangeRequestType').value = settings.defaultChangeRequestType || '';
        document.getElementById('defaultChangeRequestUrgency').value = settings.defaultChangeRequestUrgency || '';
        document.getElementById('defaultChangeRequestSeverity').value = settings.defaultChangeRequestSeverity || '';
        document.getElementById('enableWorkflowApprovalForAdministrators').checked = settings.enableWorkflowApprovalForAdministrators || false;

        // Store original settings for change detection
        DefaultChangeRequestsState.originalSettings = JSON.parse(JSON.stringify(settings));
        
        // Store original type settings if workflow for types is enabled
        if (settings.workflowForTypesEnabled && settings.workflowApprovalEnabled) {
            await loadOriginalTypeSettings(facet);
        } else {
            DefaultChangeRequestsState.originalTypeSettings = null;
        }

        // Show/hide workflow settings based on approval toggle
        document.getElementById('workflowSettingsSection').style.display = 
            settings.workflowApprovalEnabled ? 'block' : 'none';

        // Show/hide facet types tab based on workflowForTypesEnabled setting (but not for Process)
        // isProcessFacet and facetTypesTab are already declared above
        const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
        
        if (facetTypesTab && !isProcessFacet) {
            facetTypesTab.style.display = settings.workflowForTypesEnabled ? 'inline-block' : 'none';
        }
        
        // Hide/show the tab content based on toggle state
        if (facetTypesContent && !isProcessFacet) {
            if (!settings.workflowForTypesEnabled) {
                // Hide content and clear table when toggle is off
                facetTypesContent.style.display = 'none';
                const tbody = document.getElementById('facetTypesTableBody');
                if (tbody) {
                    tbody.innerHTML = '';
                }
            } else {
                facetTypesContent.style.display = 'block';
            }
        }

        // If workflow approval is enabled, load facet types table if workflow for types is also enabled
        // Also check if we're currently on the facet-types tab - if so, ensure table is loaded
        // (facetTypesTab is already declared above)
        const isFacetTypesTabActive = facetTypesTab?.classList.contains('active');
        
        if (settings.workflowApprovalEnabled && settings.workflowForTypesEnabled) {
            await loadFacetTypesTable();
        } else if (isFacetTypesTabActive && settings.workflowForTypesEnabled) {
            // If we're on the facet-types tab but workflow approval isn't enabled, show message
            const tbody = document.getElementById('facetTypesTableBody');
            if (tbody) {
                if (!settings.workflowApprovalEnabled) {
                    tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('pleaseEnableWorkflowFirstRow', 'Please enable "Workflow Approval" first')) + '</td></tr>';
                }
            }
        } else if (isFacetTypesTabActive && !settings.workflowForTypesEnabled) {
            // If toggle is off, switch back to facet tab
            await switchWorkflowTab('facet');
        }

    } catch (error) {
        console.error('Error loading settings:', error);
        showNotification(dfcrT('errorLoadingSettings', 'Error loading settings'), 'error');
    }
}

// Load original type settings for comparison
async function loadOriginalTypeSettings(facet) {
    try {
        const response = await fetch(`/admin/api/dfcr-type-settings?facet=${encodeURIComponent(facet)}`);
        if (response.ok) {
            const data = await response.json();
            DefaultChangeRequestsState.originalTypeSettings = data.typeSettings || [];
        } else {
            DefaultChangeRequestsState.originalTypeSettings = [];
        }
    } catch (error) {
        console.error('Error loading original type settings:', error);
        DefaultChangeRequestsState.originalTypeSettings = [];
    }
}

// Check if settings have changed
function hasSettingsChanged(currentSettings) {
    if (!DefaultChangeRequestsState.originalSettings) {
        return true; // No original settings, assume changed
    }
    
    // Compare all fields
    const fieldsToCompare = [
        'workflowApprovalEnabled',
        'workflowForTypesEnabled',
        'defaultChangeRequestSystem',
        'defaultWorkflowForCreating',
        'defaultWorkflowForEditing',
        'defaultBUDGStatusForCreating',
        'defaultLifecycleForCreating',
        'defaultChangeRequestType',
        'defaultChangeRequestUrgency',
        'defaultChangeRequestSeverity',
        'enableWorkflowApprovalForAdministrators'
    ];
    
    for (const field of fieldsToCompare) {
        const currentValue = String(currentSettings[field] || '');
        const originalValue = String(DefaultChangeRequestsState.originalSettings[field] || '');
        
        if (currentValue !== originalValue) {
            console.log(`[DFCR Save] Change detected in ${field}: "${originalValue}" -> "${currentValue}"`);
            return true;
        }
    }
    
    return false;
}

// Check if type settings have changed
async function hasTypeSettingsChanged(facet) {
    if (!DefaultChangeRequestsState.originalTypeSettings || DefaultChangeRequestsState.originalTypeSettings.length === 0) {
        // If no original type settings, check if current settings exist
        const tbody = document.getElementById('facetTypesTableBody');
        if (!tbody) return false;
        
        const rows = tbody.querySelectorAll('tr[data-type-id]');
        if (rows.length === 0) return false;
        
        // If there are rows but no original settings, there might be changes
        // But we need to check if they're different from default/inherited
        for (const row of rows) {
            const wfCreateSelect = row.querySelector(`select[id^="wfCreate_"]`);
            const wfEditSelect = row.querySelector(`select[id^="wfEdit_"]`);
            const crTypeSelect = row.querySelector(`select[id^="crType_"]`);
            
            if (wfCreateSelect?.value !== 'inherited' || 
                wfEditSelect?.value !== 'inherited' || 
                crTypeSelect?.value !== 'inherited') {
                return true; // Has non-inherited values
            }
        }
        return false;
    }
    
    const tbody = document.getElementById('facetTypesTableBody');
    if (!tbody) return false;
    
    const rows = tbody.querySelectorAll('tr[data-type-id]');
    
    // Build current type settings map
    const currentTypeSettingsMap = new Map();
    rows.forEach(row => {
        const typeId = parseInt(row.dataset.typeId);
        const wfCreateSelect = row.querySelector(`select[id^="wfCreate_"]`);
        const wfEditSelect = row.querySelector(`select[id^="wfEdit_"]`);
        const crTypeSelect = row.querySelector(`select[id^="crType_"]`);
        
        currentTypeSettingsMap.set(typeId, {
            workflowCreateId: wfCreateSelect?.value === 'inherited' ? null : (wfCreateSelect?.value || null),
            workflowEditId: wfEditSelect?.value === 'inherited' ? null : (wfEditSelect?.value || null),
            crTypeId: crTypeSelect?.value === 'inherited' ? null : (crTypeSelect?.value || null)
        });
    });
    
    // Build original type settings map
    const originalTypeSettingsMap = new Map();
    DefaultChangeRequestsState.originalTypeSettings.forEach(setting => {
        originalTypeSettingsMap.set(setting.typeId, {
            workflowCreateId: setting.workflowCreateId,
            workflowEditId: setting.workflowEditId,
            crTypeId: setting.crTypeId
        });
    });
    
    // Compare all type settings
    const allTypeIds = new Set([...currentTypeSettingsMap.keys(), ...originalTypeSettingsMap.keys()]);
    
    for (const typeId of allTypeIds) {
        const current = currentTypeSettingsMap.get(typeId);
        const original = originalTypeSettingsMap.get(typeId);
        
        // Normalize null/undefined/empty to null for comparison
        const normalize = (val) => {
            if (val === null || val === undefined || val === '' || val === 'inherited') return null;
            return String(val);
        };
        
        const currentWfCreate = normalize(current?.workflowCreateId);
        const originalWfCreate = normalize(original?.workflowCreateId);
        const currentWfEdit = normalize(current?.workflowEditId);
        const originalWfEdit = normalize(original?.workflowEditId);
        const currentCrType = normalize(current?.crTypeId);
        const originalCrType = normalize(original?.crTypeId);
        
        if (currentWfCreate !== originalWfCreate || 
            currentWfEdit !== originalWfEdit || 
            currentCrType !== originalCrType) {
            console.log(`[DFCR Save] Type settings change detected for type ${typeId}`);
            return true;
        }
    }
    
    return false;
}

// Validate all mandatory fields
function validateMandatoryFields() {
    const errors = [];
    const errorFields = [];
    
    // Clear previous errors
    clearDFCRFieldErrors();
    
    // 1. Facet validation
    const facet = document.getElementById('facetSelect').value;
    if (!facet) {
        var msgFacet = dfcrT('validationSelectFacet', 'Please select a facet');
        errors.push(msgFacet);
        errorFields.push({ id: 'facetSelect', message: msgFacet });
    }
    
    // 2. Default Change Request System validation
    const defaultChangeRequestSystem = document.getElementById('defaultChangeRequestSystem').value;
    if (!defaultChangeRequestSystem) {
        var msgSys = dfcrT('validationSelectChangeRequestSystem', 'Please select a Default Change Request System');
        errors.push(msgSys);
        errorFields.push({ id: 'defaultChangeRequestSystem', message: msgSys });
    }
    
    // 3. Workflow validation - at least one must be selected
    const workflowForCreating = document.getElementById('defaultWorkflowForCreating').value;
    const workflowForEditing = document.getElementById('defaultWorkflowForEditing').value;
    if (!workflowForCreating && !workflowForEditing) {
        var msgWfBoth = dfcrT('validationSelectAtLeastOneWorkflow', 'Please select at least one workflow (for creating or editing objects)');
        var msgWfOne = dfcrT('validationSelectWorkflow', 'Please select at least one workflow');
        errors.push(msgWfBoth);
        errorFields.push({ id: 'defaultWorkflowForCreating', message: msgWfOne });
        errorFields.push({ id: 'defaultWorkflowForEditing', message: msgWfOne });
    }
    
    // 4. Default BUDG Status validation
    const defaultBUDGStatus = document.getElementById('defaultBUDGStatusForCreating').value;
    if (!defaultBUDGStatus) {
        var msgStatus = dfcrT('validationSelectBudgStatus', 'Please select a Default BUDG Status for Creating Object');
        errors.push(msgStatus);
        errorFields.push({ id: 'defaultBUDGStatusForCreating', message: msgStatus });
    }
    
    // 5. Default Lifecycle validation
    const defaultLifecycle = document.getElementById('defaultLifecycleForCreating').value;
    if (!defaultLifecycle) {
        var msgLc = dfcrT('validationSelectLifecycle', 'Please select a Default Lifecycle for Creating Object');
        errors.push(msgLc);
        errorFields.push({ id: 'defaultLifecycleForCreating', message: msgLc });
    }
    
    // 6. Default Change Request Type validation
    const defaultChangeRequestType = document.getElementById('defaultChangeRequestType').value;
    if (!defaultChangeRequestType) {
        var msgType = dfcrT('validationSelectCrType', 'Please select a Default Change Request Type');
        errors.push(msgType);
        errorFields.push({ id: 'defaultChangeRequestType', message: msgType });
    }
    
    // 7. Default Change Request Urgency validation
    const defaultChangeRequestUrgency = document.getElementById('defaultChangeRequestUrgency').value;
    if (!defaultChangeRequestUrgency) {
        var msgUrg = dfcrT('validationSelectUrgency', 'Please select a Default Change Request Urgency');
        errors.push(msgUrg);
        errorFields.push({ id: 'defaultChangeRequestUrgency', message: msgUrg });
    }
    
    // 8. Default Change Request Severity validation
    const defaultChangeRequestSeverity = document.getElementById('defaultChangeRequestSeverity').value;
    if (!defaultChangeRequestSeverity) {
        var msgSev = dfcrT('validationSelectSeverity', 'Please select a Default Change Request Severity');
        errors.push(msgSev);
        errorFields.push({ id: 'defaultChangeRequestSeverity', message: msgSev });
    }
    
    // Show errors for all invalid fields
    if (errorFields.length > 0) {
        errorFields.forEach(field => {
            showFieldError(field.id, field.message);
        });
        
        // Show general notification with all errors
        const errorMessage = errors.join('\n');
        showNotification(errorMessage, 'error');
        
        // Scroll to first error field
        const firstErrorField = document.getElementById(errorFields[0].id);
        if (firstErrorField) {
            firstErrorField.scrollIntoView({ behavior: 'smooth', block: 'center' });
            firstErrorField.focus();
        }
    }
    
    return errorFields.length === 0;
}

// Show error for a specific field
function showFieldError(fieldId, message) {
    const field = document.getElementById(fieldId);
    if (!field) return;
    
    // Highlight the field with red border
    field.style.borderColor = '#dc3545';
    field.style.borderWidth = '2px';
    
    // Remove existing error message if any
    const existingError = field.parentElement.querySelector('.field-error-message');
    if (existingError) {
        existingError.remove();
    }
    
    // Create and add error message
    const errorDiv = document.createElement('div');
    errorDiv.className = 'field-error-message';
    errorDiv.style.color = '#dc3545';
    errorDiv.style.fontSize = '0.875rem';
    errorDiv.style.marginTop = '4px';
    errorDiv.textContent = message;
    
    // Insert error message after the field
    field.parentElement.appendChild(errorDiv);
}

// Clear all field errors for DFCR form
function clearDFCRFieldErrors() {
    // Clear border styles for all form fields
    const formFields = [
        'facetSelect',
        'defaultChangeRequestSystem',
        'defaultWorkflowForCreating',
        'defaultWorkflowForEditing',
        'defaultBUDGStatusForCreating',
        'defaultLifecycleForCreating',
        'defaultChangeRequestType',
        'defaultChangeRequestUrgency',
        'defaultChangeRequestSeverity'
    ];
    
    formFields.forEach(fieldId => {
        const field = document.getElementById(fieldId);
        if (field) {
            field.style.borderColor = '';
            field.style.borderWidth = '';
            
            // Remove error messages
            const errorMessage = field.parentElement?.querySelector('.field-error-message');
            if (errorMessage) {
                errorMessage.remove();
            }
        }
    });
}

async function saveDefaultChangeRequestSettings() {
    console.log('[DFCR Save] Save button clicked');
    
    // Clear previous errors
    clearDFCRFieldErrors();
    
    // Validate all mandatory fields
    if (!validateMandatoryFields()) {
        console.log('[DFCR Save] Validation failed');
        return;
    }
    
    const facet = document.getElementById('facetSelect').value;
    console.log('[DFCR Save] Selected facet:', facet);
    
    // Get workflow values
    const workflowForCreating = document.getElementById('defaultWorkflowForCreating').value;
    const workflowForEditing = document.getElementById('defaultWorkflowForEditing').value;

    const settings = {
        facet: facet,
        workflowApprovalEnabled: document.getElementById('workflowApprovalEnabled').checked,
        workflowForTypesEnabled: document.getElementById('workflowForTypesEnabled').checked,
        defaultChangeRequestSystem: document.getElementById('defaultChangeRequestSystem').value,
        defaultWorkflowForCreating: workflowForCreating,
        defaultWorkflowForEditing: workflowForEditing,
        defaultBUDGStatusForCreating: document.getElementById('defaultBUDGStatusForCreating').value,
        defaultLifecycleForCreating: document.getElementById('defaultLifecycleForCreating').value,
        defaultChangeRequestType: document.getElementById('defaultChangeRequestType').value,
        defaultChangeRequestUrgency: document.getElementById('defaultChangeRequestUrgency').value,
        defaultChangeRequestSeverity: document.getElementById('defaultChangeRequestSeverity').value,
        enableWorkflowApprovalForAdministrators: document.getElementById('enableWorkflowApprovalForAdministrators').checked
    };

    console.log('[DFCR Save] Settings to save:', settings);

    // Check if there are any changes
    const hasChanges = hasSettingsChanged(settings);
    
    // Check type settings changes if workflow for types is enabled
    let hasTypeSettingsChanges = false;
    if (settings.workflowForTypesEnabled) {
        hasTypeSettingsChanges = await hasTypeSettingsChanged(facet);
    }
    
    if (!hasChanges && !hasTypeSettingsChanges) {
        console.log('[DFCR Save] No changes detected');
        showNotification(dfcrT('noChangesToSave', 'No changes to save'), 'info');
        return;
    }

    try {
        console.log('[DFCR Save] Sending POST request...');
        const response = await fetch('/admin/api/default-change-requests', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(settings)
        });

        console.log('[DFCR Save] Response status:', response.status);
        
        if (!response.ok) {
            let errorMessage = dfcrT('failedToSaveSettings', 'Failed to save settings');
            try {
                const errorData = await response.json();
                if (errorData.error) {
                    errorMessage = errorData.error;
                }
            } catch (e) {
                const errorText = await response.text();
                console.error('[DFCR Save] Response error:', errorText);
            }
            console.error('[DFCR Save] Error:', errorMessage);
            showNotification(errorMessage, 'error');
            return;
        }

        const result = await response.json();
        console.log('[DFCR Save] Save successful:', result);
        
        // Clear any validation errors on successful save
        clearDFCRFieldErrors();
        
        // Update original value for workflow approval checkbox after successful save
        const workflowApprovalCheckbox = document.getElementById('workflowApprovalEnabled');
        if (workflowApprovalCheckbox) {
            workflowApprovalCheckbox.dataset.originalValue = settings.workflowApprovalEnabled ? 'true' : 'false';
        }
        
        // Also save type settings if workflow for types is enabled
        if (settings.workflowForTypesEnabled) {
            await saveTypeSettings(facet);
        }
        
        showNotification(result.message || dfcrT('settingsSavedSuccess', 'Settings saved successfully'), 'success');

    } catch (error) {
        console.error('[DFCR Save] Error saving settings:', error);
        showNotification(dfcrTpl(dfcrT('errorSavingSettingsWithMessage', 'Error saving settings: {{message}}'), { message: error.message }), 'error');
    }
}

async function saveTypeSettings(facet) {
    console.log('[DFCR Save] Saving type settings for facet:', facet);
    
    const tbody = document.getElementById('facetTypesTableBody');
    const rows = tbody.querySelectorAll('tr[data-type-id]');
    
    const typeSettings = [];
    
    rows.forEach(row => {
        const typeId = parseInt(row.dataset.typeId);
        const typeName = row.dataset.typeName;
        
        const wfCreateSelect = row.querySelector(`select[id^="wfCreate_"]`);
        const wfEditSelect = row.querySelector(`select[id^="wfEdit_"]`);
        const crTypeSelect = row.querySelector(`select[id^="crType_"]`);
        
        const setting = {
            typeId: typeId,
            typeName: typeName,
            workflowCreateId: wfCreateSelect?.value === 'inherited' ? null : (wfCreateSelect?.value || null),
            workflowEditId: wfEditSelect?.value === 'inherited' ? null : (wfEditSelect?.value || null),
            crTypeId: crTypeSelect?.value === 'inherited' ? null : (crTypeSelect?.value || null)
        };
        
        typeSettings.push(setting);
    });
    
    console.log('[DFCR Save] Type settings to save:', typeSettings);
    
    try {
        const response = await fetch('/admin/api/dfcr-type-settings', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                facet: facet,
                typeSettings: typeSettings
            })
        });
        
        if (!response.ok) {
            const errorText = await response.text();
            console.error('[DFCR Save] Type settings error:', errorText);
            throw new Error(dfcrT('failedToSaveTypeSettings', 'Failed to save type settings'));
        }
        
        const result = await response.json();
        console.log('[DFCR Save] Type settings saved:', result);
        
    } catch (error) {
        console.error('[DFCR Save] Error saving type settings:', error);
        throw error;
    }
}

async function loadWorkflowDropdowns() {
    // Load workflows from process_definition table, filtered by Entity_ID (facet) and Status = 'Enabled'
    try {
        const facet = document.getElementById('facetSelect').value;
        
        if (!facet) {
            console.warn('No facet selected, cannot load workflows');
            const creatingSelect = document.getElementById('defaultWorkflowForCreating');
            const editingSelect = document.getElementById('defaultWorkflowForEditing');
            var optWf = '<option value="">' + dfcrEscape(dfcrT('selectWorkflow', 'Select a workflow')) + '</option>';
            if (creatingSelect) creatingSelect.innerHTML = optWf;
            if (editingSelect) editingSelect.innerHTML = optWf;
            return;
        }
        
        // Always get workflows filtered by facet (Entity_ID) and Status = 'Enabled'
        const filteredResponse = await fetch(`/admin/api/dfcr-type-settings?facet=${encodeURIComponent(facet)}&workflows=true`);
        if (!filteredResponse.ok) {
            throw new Error('Failed to load workflows for facet: ' + filteredResponse.status);
        }
        
        const data = await filteredResponse.json();
        const workflows = data.workflows || [];
        console.log('Loaded workflows filtered by facet (Entity_ID) and Status=Enabled:', facet, workflows);
        
        const creatingSelect = document.getElementById('defaultWorkflowForCreating');
        const editingSelect = document.getElementById('defaultWorkflowForEditing');
        
        if (!creatingSelect || !editingSelect) {
            console.error('Workflow select elements not found');
            return;
        }
        
        // Clear existing options except the first one
        var optWf2 = '<option value="">' + dfcrEscape(dfcrT('selectWorkflow', 'Select a workflow')) + '</option>';
        creatingSelect.innerHTML = optWf2;
        editingSelect.innerHTML = optWf2;

        workflows.forEach(workflow => {
            const name = workflow.name || workflow.primaryName || workflow.PrimaryName || dfcrT('unknown', 'Unknown');
            const id = workflow.id || workflow.ID;
            if (name && id) {
                creatingSelect.add(new Option(name, id));
                editingSelect.add(new Option(name, id));
            }
        });
        
        console.log('Workflow dropdowns populated with filtered workflows. Creating options:', creatingSelect.options.length, 'Editing options:', editingSelect.options.length);
    } catch (error) {
        console.error('Error loading workflows:', error);
        const creatingSelect = document.getElementById('defaultWorkflowForCreating');
        const editingSelect = document.getElementById('defaultWorkflowForEditing');
        var optErrWf = '<option value="">' + dfcrEscape(dfcrT('errorLoadingWorkflows', 'Error loading workflows')) + '</option>';
        if (creatingSelect) creatingSelect.innerHTML = optErrWf;
        if (editingSelect) editingSelect.innerHTML = optErrWf;
    }
}

async function loadChangeRequestDropdowns() {
    try {
        // Load all change request related data from lookup-data endpoint
        const response = await fetch('/api/lookup-data');
        if (response.ok) {
            const data = await response.json();
            console.log('Lookup data loaded:', data);
            
            // Load types from changerequest_type table
            const types = data.changeRequestType || [];
            const typeSelect = document.getElementById('defaultChangeRequestType');
            if (typeSelect) {
                typeSelect.innerHTML = '<option value="">' + dfcrEscape(dfcrT('selectDefaultType', 'Select Default Type')) + '</option>';
                types.forEach(type => {
                    const name = type.name || type.PrimaryName || dfcrT('unknown', 'Unknown');
                    const id = type.id || type.ID;
                    if (name && id) {
                        typeSelect.add(new Option(name, id));
                    }
                });
                console.log('Change request types loaded:', types.length);
            }

            // Load urgencies from changerequest_urgency table
            const urgencies = data.changeRequestUrgency || [];
            const urgencySelect = document.getElementById('defaultChangeRequestUrgency');
            if (urgencySelect) {
                urgencySelect.innerHTML = '<option value="">' + dfcrEscape(dfcrT('selectDefaultUrgency', 'Select Default Urgency')) + '</option>';
                urgencies.forEach(urgency => {
                    const name = urgency.name || urgency.PrimaryName || dfcrT('unknown', 'Unknown');
                    const id = urgency.id || urgency.ID;
                    if (name && id) {
                        urgencySelect.add(new Option(name, id));
                    }
                });
                console.log('Change request urgencies loaded:', urgencies.length);
            }

            // Load severities from changerequest_severity table
            const severities = data.changeRequestSeverity || [];
            const severitySelect = document.getElementById('defaultChangeRequestSeverity');
            if (severitySelect) {
                severitySelect.innerHTML = '<option value="">' + dfcrEscape(dfcrT('selectDefaultSeverity', 'Select Default Severity')) + '</option>';
                severities.forEach(severity => {
                    const name = severity.name || severity.PrimaryName || dfcrT('unknown', 'Unknown');
                    const id = severity.id || severity.ID;
                    if (name && id) {
                        severitySelect.add(new Option(name, id));
                    }
                });
                console.log('Change request severities loaded:', severities.length);
            }
        } else {
            console.error('Failed to load lookup data. Status:', response.status);
        }
    } catch (error) {
        console.error('Error loading change request dropdowns:', error);
    }
}

async function loadStatusAndLifecycleDropdowns() {
    try {
        // Load BUDG Status from status table via lookup-data endpoint
        const response = await fetch('/api/lookup-data');
        if (response.ok) {
            const data = await response.json();
            const statuses = data.status || [];
            console.log('Statuses loaded:', statuses);
            const statusSelect = document.getElementById('defaultBUDGStatusForCreating');
            if (statusSelect) {
                statusSelect.innerHTML = '<option value="">' + dfcrEscape(dfcrT('selectDefaultBudgStatus', 'Select Default BUDG Status')) + '</option>';
                statuses.forEach(status => {
                    const name = status.name || status.PrimaryName || status.primaryname || dfcrT('unknown', 'Unknown');
                    const id = status.id || status.ID;
                    if (name && id) {
                        statusSelect.add(new Option(name, id));
                    }
                });
                console.log('Status dropdown populated with', statusSelect.options.length, 'options');
            } else {
                console.error('Status select element not found');
            }
        } else {
            console.error('Failed to load status data. Status:', response.status);
        }
    } catch (error) {
        console.error('Error loading status dropdown:', error);
    }
}

// Cache lifecycle options per facet so we can map ID -> name when restoring saved values
const lifecycleOptionCache = {};

function getFacetKey(facet) {
    const f = (facet || '').toLowerCase().trim();
    if (f === 'data set' || f === 'data sets') return 'dataset';
    return f.replace(/\s+/g, '');
}

async function loadLifecycleForFacet(facet) {
    try {
        const lifecycleSelect = document.getElementById('defaultLifecycleForCreating');
        lifecycleSelect.innerHTML = '<option value="">' + dfcrEscape(dfcrT('selectDefaultLifecycle', 'Select Default Lifecycle')) + '</option>';
        
        let lifecycleEndpoint = '';
        let lifecycleDataKey = '';
        const facetKey = getFacetKey(facet);
        
        // Determine lifecycle endpoint based on facet
        switch(facet.toLowerCase()) {
            case 'glossary':
                lifecycleEndpoint = '/api/glossary/lifecycle/list';
                break;
            case 'data set':
                lifecycleEndpoint = '/api/lifecycle/list'; // Dataset lifecycle endpoint
                break;
            case 'process':
                lifecycleEndpoint = '/api/process/lifecycle/list';
                break;
            case 'system':
                lifecycleEndpoint = '/api/system/lifecycle/list';
                break;
            default:
                console.warn('No lifecycle endpoint defined for facet:', facet);
                return;
        }
        
        // For some facets, we can also get lifecycle from lookup-data
        if (facet.toLowerCase() === 'glossary' || facet.toLowerCase() === 'data set' || facet.toLowerCase() === 'system') {
            try {
                const lookupResponse = await fetch('/api/lookup-data');
                if (lookupResponse.ok) {
                    const lookupData = await lookupResponse.json();
                    let lifecycles = [];
                    
                    switch(facet.toLowerCase()) {
                        case 'glossary':
                            lifecycles = lookupData.glossaryLifecycle || [];
                            break;
                        case 'data set':
                            lifecycles = lookupData.datasetLifecycle || [];
                            break;
                        case 'system':
                            lifecycles = lookupData.systemLifecycle || [];
                            break;
                    }
                    
                    lifecycles.forEach(lifecycle => {
                        const id = lifecycle.id || lifecycle.ID;
                        const name = lifecycle.name || lifecycle.Name || lifecycle.primaryname || lifecycle.PrimaryName || `(ID ${id})`;
                        if (name && id != null) {
                            const val = `${facetKey}_${id}`;
                            lifecycleSelect.add(new Option(name, val));
                        }
                    });
                    lifecycleOptionCache[facetKey] = lifecycles.map(lc => ({
                        id: `${facetKey}_${(lc.id || lc.ID)}`,
                        name: lc.name || lc.Name || lc.primaryname || lc.PrimaryName || `(ID ${lc.id || lc.ID})`
                    }));
                    console.log(`[Lifecycle] Loaded ${lifecycles.length} options for ${facet} from lookup-data, cached:`, lifecycleOptionCache[facetKey]);
                    return; // Successfully loaded from lookup-data
                }
            } catch (lookupError) {
                console.warn('Failed to load from lookup-data, trying direct endpoint:', lookupError);
            }
        }
        
        // Fallback to direct endpoint
        const response = await fetch(lifecycleEndpoint);
        if (response.ok) {
            const lifecycles = await response.json();
            lifecycles.forEach(lifecycle => {
                // Handle different field names from different endpoints
                const id = lifecycle.id || lifecycle.ID;
                const name = lifecycle.name || lifecycle.Name || lifecycle.primaryname || lifecycle.PrimaryName || `(ID ${id})`;
                if (name && id) {
                    const val = `${facetKey}_${id}`;
                    lifecycleSelect.add(new Option(name, val));
                }
            });
            lifecycleOptionCache[facetKey] = lifecycles
                .map(lc => ({
                    id: `${facetKey}_${lc.id || lc.ID}`,
                    name: lc.name || lc.Name || lc.primaryname || lc.PrimaryName || `(ID ${lc.id || lc.ID})`
                }));
            console.log(`[Lifecycle] Loaded ${lifecycles.length} options for ${facet} from direct endpoint, cached:`, lifecycleOptionCache[facetKey]);
        }
    } catch (error) {
        console.error('Error loading lifecycle dropdown:', error);
    }
}

async function loadStakeholderRoles(facet) {
    try {
        const tbody = document.getElementById('stakeholderRolesTableBody');
        tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('loadingRoles', 'Loading roles...')) + '</td></tr>';

        if (!facet) {
            tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('selectFacetToViewRoles', 'Please select a facet to view roles')) + '</td></tr>';
            return;
        }
        
        // Map facet names to module names (as they appear in the database)
        const facetToModuleMap = {
            'Glossary': 'Glossary',
            'Data Set': 'Data Sets', 
            'Process': 'Process',
            'System': 'System'
        };

        // First, fetch role types to create a mapping
        let roleTypeMap = {};
        try {
            const roleTypesResponse = await fetch('/api/object-roles');
            if (roleTypesResponse.ok) {
                const roleTypesData = await roleTypesResponse.json();
                const roleTypes = roleTypesData.roleTypes || [];
                // Create a map of role type ID to role type name
                roleTypes.forEach(roleType => {
                    const id = roleType.id || roleType.ID;
                    const name = roleType.primaryname || roleType.primaryName || roleType.name;
                    if (id && name) {
                        roleTypeMap[id] = name;
                    }
                });
                console.log('Role type mapping:', roleTypeMap);
            }
        } catch (roleTypeError) {
            console.warn('Failed to fetch role types, will show IDs:', roleTypeError);
        }

        // Get all modules first to get their IDs
        const modulesResponse = await fetch('/api/modules');
        if (!modulesResponse.ok) {
            throw new Error('Failed to fetch modules');
        }
        
        const modulesData = await modulesResponse.json();
        const modules = modulesData.modules || modulesData.data || [];
        console.log('Available modules:', modules);

        const moduleName = facetToModuleMap[facet];
        const module = modules.find(m => 
            m.primaryName === moduleName || 
            m.primaryname === moduleName || 
            m.name === moduleName
        );
        
        if (!module) {
            console.warn(`Module not found for facet: ${facet} (looking for: ${moduleName})`);
            tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('noModuleForFacet', 'No module found for selected facet')) + '</td></tr>';
            return;
        }

        console.log(`Loading roles for ${facet} (Module ID: ${module.id})`);
        
        try {
            // Fetch roles for this module
            const rolesResponse = await fetch(`/api/object-roles?action=getByFacet&facetId=${module.id}`);
            if (rolesResponse.ok) {
                const rolesData = await rolesResponse.json();
                const roles = rolesData.data || [];
                
                console.log(`Found ${roles.length} roles for ${facet}:`, roles);
                
                // Clear the loading message
                tbody.innerHTML = '';
                
                // Add roles to the table
                roles.forEach(role => {
                    const row = document.createElement('tr');
                    // Get role type ID and map it to name
                    const roleTypeId = role.objectroletypeId || role.objectRoleTypeId || role.roleTypeId;
                    const roleTypeName = roleTypeId && roleTypeMap[roleTypeId] 
                        ? roleTypeMap[roleTypeId] 
                        : (roleTypeId ? dfcrT('roleTypeIdPrefix', 'ID:') + ' ' + roleTypeId : dfcrT('notSpecified', 'Not specified'));
                    
                    row.innerHTML = `
                        <td>${dfcrEscape(role.primaryname || role.name || dfcrT('unknown', 'Unknown'))}</td>
                        <td>${dfcrEscape(role.description || role.Description || dfcrT('noDescription', 'No description'))}</td>
                        <td>${dfcrEscape(roleTypeName)}</td>
                    `;
                    tbody.appendChild(row);
                });

                // If no roles were loaded, show a message
                if (roles.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('noRolesForFacet', 'No roles found for this facet')) + '</td></tr>';
                }
            } else {
                console.error(`Failed to fetch roles for ${facet}:`, rolesResponse.status);
                tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('failedToLoadRoles', 'Failed to load roles')) + '</td></tr>';
            }
        } catch (roleError) {
            console.error(`Error fetching roles for ${facet}:`, roleError);
            tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('errorLoadingRoles', 'Error loading roles')) + '</td></tr>';
        }

    } catch (error) {
        console.error('Error loading stakeholder roles:', error);
        const tbody = document.getElementById('stakeholderRolesTableBody');
        tbody.innerHTML = '<tr><td colspan="3">' + dfcrEscape(dfcrT('errorLoadingRoles', 'Error loading roles')) + '</td></tr>';
    }
}

function resetForm() {
    document.getElementById('workflowApprovalEnabled').checked = false;
    document.getElementById('workflowForTypesEnabled').checked = false;
    document.getElementById('defaultChangeRequestSystem').value = 'Native';
    document.getElementById('defaultWorkflowForCreating').value = '';
    document.getElementById('defaultWorkflowForEditing').value = '';
    document.getElementById('defaultBUDGStatusForCreating').value = '';
    document.getElementById('defaultLifecycleForCreating').value = '';
    document.getElementById('defaultChangeRequestType').value = '';
    document.getElementById('defaultChangeRequestUrgency').value = '';
    document.getElementById('defaultChangeRequestSeverity').value = '';
    document.getElementById('enableWorkflowApprovalForAdministrators').checked = false;
    document.getElementById('workflowSettingsSection').style.display = 'none';
    document.getElementById('stakeholderRolesTableBody').innerHTML = '';
}

async function switchWorkflowTab(tab) {
    // Update tab buttons
    document.querySelectorAll('.workflow-tab').forEach(btn => {
        btn.classList.remove('active');
    });
    document.querySelector(`.workflow-tab[data-tab="${tab}"]`)?.classList.add('active');

    // Update tab content - hide all first
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
        content.style.display = 'none'; // Explicitly hide all tab contents
    });

    if (tab === 'facet') {
        const facetContent = document.getElementById('settingsForFacetContent');
        if (facetContent) {
            facetContent.classList.add('active');
            facetContent.style.display = 'block';
        }
        // Ensure facet types content is hidden
        const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
        if (facetTypesContent) {
            facetTypesContent.style.display = 'none';
        }
    } else if (tab === 'facet-types') {
        const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
        const workflowForTypesEnabled = document.getElementById('workflowForTypesEnabled')?.checked;
        
        // Only show the tab content if the toggle is enabled
        if (!workflowForTypesEnabled) {
            // If toggle is off, don't show the content at all
            if (facetTypesContent) {
                facetTypesContent.style.display = 'none';
            }
            // Switch back to facet tab
            await switchWorkflowTab('facet');
            return;
        }
        
        if (facetTypesContent) {
            facetTypesContent.classList.add('active');
            // Show the content and ensure table is visible
            facetTypesContent.style.display = 'block';
            
            // Always reload the table when switching to this tab (if conditions are met)
            const facet = document.getElementById('facetSelect').value;
            const workflowApprovalEnabled = document.getElementById('workflowApprovalEnabled')?.checked;
            
            if (facet && workflowApprovalEnabled && workflowForTypesEnabled) {
                await loadFacetTypesTable();
            } else if (!facet) {
                const tbody = document.getElementById('facetTypesTableBody');
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('pleaseSelectFacetFirst', 'Please select a facet first')) + '</td></tr>';
                }
            } else if (!workflowApprovalEnabled) {
                const tbody = document.getElementById('facetTypesTableBody');
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('pleaseEnableWorkflowFirstRow', 'Please enable "Workflow Approval" first')) + '</td></tr>';
                }
            }
        }
        
        // Ensure facet content is hidden when on facet-types tab
        const facetContent = document.getElementById('settingsForFacetContent');
        if (facetContent) {
            facetContent.style.display = 'none';
        }
    }
}

// Cache for facet type settings
let facetTypeSettingsCache = {};
let facetWorkflowsCache = {};
let crTypesCache = [];

async function loadFacetTypesTable() {
    const facet = document.getElementById('facetSelect').value;
    if (!facet) {
        console.warn('[DFCR] Cannot load facet types table: no facet selected');
        const tbody = document.getElementById('facetTypesTableBody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('pleaseSelectFacetFirst', 'Please select a facet first')) + '</td></tr>';
        }
        return;
    }

    const tbody = document.getElementById('facetTypesTableBody');
    if (!tbody) {
        console.warn('[DFCR] Cannot load facet types table: tbody element not found');
        return;
    }
    
    // Only show the table container if we're on the facet-types tab
    const facetTypesContent = document.getElementById('settingsForFacetTypesContent');
    const isFacetTypesTabActive = document.querySelector('.workflow-tab[data-tab="facet-types"]')?.classList.contains('active');
    const workflowForTypesEnabled = document.getElementById('workflowForTypesEnabled')?.checked;
    
    // Only show content if we're on the correct tab AND toggle is enabled
    if (facetTypesContent && isFacetTypesTabActive && workflowForTypesEnabled) {
        facetTypesContent.style.display = 'block';
    } else if (facetTypesContent) {
        // Hide it if we're not on the right tab or toggle is off
        facetTypesContent.style.display = 'none';
    }
    
    tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('loading', 'Loading...')) + '</td></tr>';

    try {
        // Load type settings from database
        const response = await fetch(`/admin/api/dfcr-type-settings?facet=${encodeURIComponent(facet)}`);
        if (!response.ok) {
            const errorText = await response.text();
            console.error('[DFCR] Failed to load type settings:', errorText);
            throw new Error('Failed to load type settings: ' + response.status);
        }
        
        const data = await response.json();
        const typeSettings = data.typeSettings || [];
        facetTypeSettingsCache[facet] = typeSettings;
        
        console.log('[DFCR] Loaded type settings for', facet, ':', typeSettings);
        
        if (typeSettings.length === 0) {
            console.warn('[DFCR] No type settings returned for facet:', facet);
            tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('noTypesForFacet', 'No types found for this facet')) + '</td></tr>';
            return;
        }
        
        // Load workflows for this facet
        const wfResponse = await fetch(`/admin/api/dfcr-type-settings?facet=${encodeURIComponent(facet)}&workflows=true`);
        if (wfResponse.ok) {
            const wfData = await wfResponse.json();
            facetWorkflowsCache[facet] = wfData.workflows || [];
            console.log('[DFCR] Loaded workflows for', facet, ':', facetWorkflowsCache[facet]);
        }
        
        // Load CR types (shared across facets)
        if (crTypesCache.length === 0) {
            const lookupResponse = await fetch('/api/lookup-data');
            if (lookupResponse.ok) {
                const lookupData = await lookupResponse.json();
                crTypesCache = lookupData.changeRequestType || [];
                console.log('[DFCR] Loaded CR types:', crTypesCache);
            }
        }
        
        // Render the table
        renderFacetTypesTable(facet, typeSettings);
        
    } catch (error) {
        console.error('Error loading facet types table:', error);
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('errorLoadingTypes', 'Error loading types') + ': ' + error.message) + '</td></tr>';
        }
    }
}

function renderFacetTypesTable(facet, typeSettings) {
    const tbody = document.getElementById('facetTypesTableBody');
    if (!tbody) {
        console.error('[DFCR] Cannot render table: tbody element not found');
        return;
    }
    
    tbody.innerHTML = '';
    
    const workflows = facetWorkflowsCache[facet] || [];
    
    console.log('[DFCR] Rendering table for', facet, 'with', typeSettings.length, 'types and', workflows.length, 'workflows');
    
    if (typeSettings.length === 0) {
        tbody.innerHTML = '<tr><td colspan="4">' + dfcrEscape(dfcrT('noTypesForFacet', 'No types found for this facet')) + '</td></tr>';
        return;
    }
    
    typeSettings.forEach(type => {
        const row = document.createElement('tr');
        row.dataset.typeId = type.typeId;
        row.dataset.typeName = type.typeName;
        
        // Get saved values (handle both null and undefined, and also handle string "null")
        const workflowCreateId = (type.workflowCreateId === null || type.workflowCreateId === undefined || type.workflowCreateId === 'null') ? null : type.workflowCreateId;
        const workflowEditId = (type.workflowEditId === null || type.workflowEditId === undefined || type.workflowEditId === 'null') ? null : type.workflowEditId;
        const crTypeId = (type.crTypeId === null || type.crTypeId === undefined || type.crTypeId === 'null') ? null : type.crTypeId;
        
        // Convert to integers if they're strings
        const createWfId = workflowCreateId ? (typeof workflowCreateId === 'string' ? parseInt(workflowCreateId) : workflowCreateId) : null;
        const editWfId = workflowEditId ? (typeof workflowEditId === 'string' ? parseInt(workflowEditId) : workflowEditId) : null;
        const crTypeIdInt = crTypeId ? (typeof crTypeId === 'string' ? parseInt(crTypeId) : crTypeId) : null;
        
        console.log('[DFCR] Rendering type:', type.typeName, 'workflowCreateId:', createWfId, 'workflowEditId:', editWfId, 'crTypeId:', crTypeIdInt);
        
        // Create workflow dropdowns
        const inheritedLabel = dfcrT('inheritedFromFacet', 'Inherited from Facet');
        const createWfHtml = createDropdownHtml(
            `wfCreate_${type.typeId}`,
            createWfId,
            workflows,
            inheritedLabel
        );
        
        const editWfHtml = createDropdownHtml(
            `wfEdit_${type.typeId}`,
            editWfId,
            workflows,
            inheritedLabel
        );
        
        const crTypeHtml = createDropdownHtml(
            `crType_${type.typeId}`,
            crTypeIdInt,
            crTypesCache,
            inheritedLabel
        );
        
        row.innerHTML = `
            <td class="type-name-cell">${dfcrEscape(type.typeName)}</td>
            <td class="dropdown-cell">${createWfHtml}</td>
            <td class="dropdown-cell">${editWfHtml}</td>
            <td class="dropdown-cell">${crTypeHtml}</td>
        `;
        tbody.appendChild(row);
    });
    
    console.log('[DFCR] Rendered', typeSettings.length, 'rows in facet types table');
}

// Setup workflow change validation
function setupWorkflowChangeValidation() {
    const creatingSelect = document.getElementById('defaultWorkflowForCreating');
    const editingSelect = document.getElementById('defaultWorkflowForEditing');
    
    if (creatingSelect) {
        creatingSelect.addEventListener('change', async (e) => {
            const facet = document.getElementById('facetSelect').value;
            await validateWorkflowChange(e.target, 'Creating', facet);
        });
    }
    
    if (editingSelect) {
        editingSelect.addEventListener('change', async (e) => {
            const facet = document.getElementById('facetSelect').value;
            await validateWorkflowChange(e.target, 'Editing', facet);
        });
    }
}

// Validate if workflow approval can be disabled - check if facet has active CRs using default workflows
async function validateWorkflowApprovalDisable(facet) {
    try {
        const response = await fetch(`/admin/api/check-workflow-in-use?checkFacetWorkflows=true&facet=${encodeURIComponent(facet)}`);
        if (!response.ok) {
            console.error('Failed to check facet workflows usage');
            return true; // Allow disable if check fails (fail open)
        }
        
        const data = await response.json();
        if (data.inUse && data.activeCRs && data.activeCRs.length > 0) {
            // Facet has active CRs using default workflows - prevent disable and show alert
            const alert = document.getElementById('workflowChangeAlert');
            const alertMessage = document.getElementById('workflowChangeAlertMessage');
            if (alert && alertMessage) {
                alertMessage.textContent = dfcrTpl(dfcrT('workflowAlertCannotDisable', 'Workflow Approval cannot be disabled because there are {{count}} active Change Request(s) using the default workflows for this facet.'), { count: data.activeCRCount });
                alert.style.display = 'block';
                
                // Auto-hide after 5 seconds
                setTimeout(() => {
                    alert.style.display = 'none';
                }, 5000);
            }
            
            return false; // Prevent disable
        }
        
        return true; // No active CRs, allow disable
    } catch (error) {
        console.error('Error validating workflow approval disable:', error);
        return true; // Allow disable if validation fails (fail open)
    }
}

// Validate workflow change - check if ORIGINAL workflow (being changed FROM) is used in active CRs for the specific facet
async function validateWorkflowChange(selectElement, workflowType, facet) {
    const newValue = selectElement.value;
    const originalValue = selectElement.dataset.originalValue || '';
    
    // If value hasn't changed, no validation needed
    if (newValue === originalValue) {
        return true;
    }
    
    // If there's no original value (first time setting), allow it
    if (!originalValue) {
        return true;
    }
    
    // If no facet selected, can't validate
    if (!facet) {
        return true;
    }
    
    // Check if the ORIGINAL workflow (the one being changed FROM) is used in active CRs for this facet
    try {
        const response = await fetch(`/admin/api/check-workflow-in-use?workflowId=${encodeURIComponent(originalValue)}&facet=${encodeURIComponent(facet)}`);
        if (!response.ok) {
            console.error('Failed to check workflow usage');
            return true; // Allow change if check fails
        }
        
        const data = await response.json();
        if (data.inUse && data.activeCRs && data.activeCRs.length > 0) {
            // Original workflow is in use - prevent change and show alert
            selectElement.value = originalValue; // Revert to original value
            
            const alert = document.getElementById('workflowChangeAlert');
            const alertMessage = document.getElementById('workflowChangeAlertMessage');
            if (alert && alertMessage) {
                var wfTypeLabel = workflowType === 'Creating' ? dfcrT('workflowTypeCreating', 'Creating') : dfcrT('workflowTypeEditing', 'Editing');
                alertMessage.textContent = dfcrTpl(dfcrT('workflowAlertCannotChangeType', 'Default Workflow for {{workflowType}} Object cannot be changed while the workflow is used in an active Change Request.'), { workflowType: wfTypeLabel });
                alert.style.display = 'block';
                
                // Auto-hide after 5 seconds
                setTimeout(() => {
                    alert.style.display = 'none';
                }, 5000);
            }
            
            return false;
        }
        
        return true; // Original workflow not in use, allow change
    } catch (error) {
        console.error('Error validating workflow change:', error);
        return true; // Allow change if validation fails
    }
}

function createDropdownHtml(id, selectedValue, options, inheritedLabel) {
    const isInherited = selectedValue === null || selectedValue === undefined;
    
    let html = `<select id="${id}" class="type-setting-dropdown" data-field="${id.split('_')[0]}">`;
    html += `<option value="inherited"${isInherited ? ' selected' : ''}>${dfcrEscape(inheritedLabel)}</option>`;
    
    options.forEach(opt => {
        const optId = opt.id || opt.ID;
        const optName = opt.name || opt.primaryName || opt.PrimaryName || dfcrT('unknown', 'Unknown');
        const selected = String(optId) === String(selectedValue) ? ' selected' : '';
        html += `<option value="${optId}"${selected}>${dfcrEscape(optName)}</option>`;
    });
    
    html += '</select>';
    return html;
}

// getFacetTypesForFacet is no longer needed - types are loaded from database via loadFacetTypesTable()

function getFacetBadgeClass(facetName) {
    const classMap = {
        'Glossary': 'facet-glossary',
        'Data Set': 'facet-dataset', 
        'Process': 'facet-process',
        'System': 'facet-system'
    };
    return classMap[facetName] || 'facet-default';
}

// restoreFacetTypesDefaults function removed - button removed from UI

function escapeHtml(text) {
    return dfcrEscape(text);
}

// showNotification provided by admin-notifications.js (window.showNotification)
