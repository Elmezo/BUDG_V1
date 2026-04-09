// Dropdown Configurations functionality for Meta-Model Administration

function ddT(key, fallback) {
    return typeof adminT === 'function' ? adminT(key, fallback) : fallback;
}

let dropdownConfigState = {
    allConfigs: [],
    filteredConfigs: [],
    selectedConfigId: null,
    configDetails: null,
    dropdownValues: [],
    editMode: false,
    lastUpdatedByFilter: null,
    filters: {
        choice: '',
        module: ''
    },
    // Pending changes tracking (only saved when user clicks Save / Save & Close)
    pendingChanges: new Map(),   // valueId -> { field, newValue, originalValue }
    pendingAdds: [],             // [{ name, description }]
    pendingDeletes: new Set()    // Set of valueIds to delete
};

function showDropdownConfigurationsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.dropdownConfigs');
    if (typeof addToNavigationHistory === 'function') {
        addToNavigationHistory((typeof adminT === 'function' ? adminT('adminPanel.submenu.dropdownConfigs', 'Dropdown Configurations') : 'Dropdown Configurations'), showDropdownConfigurationsContent, applyDropdownConfigsHeader);
    }
    applyDropdownConfigsHeader();
    if (contentArea) {
        contentArea.innerHTML = getDropdownConfigsLayout();
        initializeDropdownConfigsModule();
    }
}

function applyDropdownConfigsHeader() {
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (!adminHeaderBar) return;
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    adminHeaderBar.innerHTML = `
        <div class="header-left">
            <button class="back-button" onclick="goBack()">
                <i class="fas fa-arrow-left"></i>
            </button>
        </div>
        <div class="header-center">
            <h1 class="system-title">${T('adminPanel.dropdownConfigs.pageTitle', 'Dropdown Configurations')}</h1>
            <div class="system-status">
                <span class="status-dot"></span>
                ${T('adminPanel.staticPageEditor.subtitleBudg', 'BUDG Management')}
            </div>
        </div>
    `;
}

function getDropdownConfigsLayout() {
    const T = ddT;
    const h = escapeHtml;
    return `
        <div class="dropdown-configs-page">
            <!-- Config List View -->
            <div class="dropdown-configs-list-view" id="dropdownConfigsListView">
                <div class="config-filters-section">
                    <div class="section-header">
                        <h3>${h(T('adminPanel.dropdownConfigs.configurationOptionsSection', 'CONFIGURATION OPTIONS'))}</h3>
                    </div>
                    <div class="filters-row">
                        <div class="filter-group">
                            <label>${h(T('adminPanel.dropdownConfigs.labelChoice', 'Choice'))}</label>
                            <select id="filterChoice" class="filter-select">
                                <option value="">${T('adminPanel.dropdownConfigs.allChoices', 'All Choices')}</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>${h(T('adminPanel.dropdownConfigs.labelModule', 'Module'))}</label>
                            <select id="filterModule" class="filter-select">
                                <option value="">${T('adminPanel.dropdownConfigs.allModules', 'All Modules')}</option>
                            </select>
                        </div>
                        <div class="filter-group">
                            <label>${h(T('adminPanel.dropdownConfigs.labelLatestUpdate', 'Latest Update'))}</label>
                            <input type="date" id="filterUpdate" class="filter-input">
                        </div>
                        <div class="filter-group">
                            <label>${h(T('adminPanel.dropdownConfigs.labelUpdatedBy', 'Updated by'))}</label>
                            <select id="filterUpdatedBy" class="filter-select">
                                <option value="">${T('adminPanel.dropdownConfigs.allUsers', 'All Users')}</option>
                            </select>
                        </div>
                    </div>
                </div>
                
                <div class="configs-table-container">
                    <table class="configs-table">
                        <tbody id="configsTableBody">
                            <tr class="loading-row">
                                <td><i class="fas fa-spinner fa-spin"></i> ${T('adminPanel.dropdownConfigs.loadingConfigurations', 'Loading configurations...')}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <!-- Config Detail View (Hidden initially) -->
            <div class="dropdown-config-detail-view" id="dropdownConfigDetailView" style="display:none;">
                <div class="detail-header">
                    <button class="back-to-list-btn" id="backToListBtn">
                        <i class="fas fa-arrow-left"></i> ${T('adminPanel.dropdownConfigs.backToConfigurations', 'Back to Configurations')}
                    </button>
                    <h2 id="configDetailTitle">${T('adminPanel.dropdownConfigs.configurationName', 'Configuration Name')}</h2>
                    <div class="detail-actions">
                        <button class="btn btn-primary" id="saveDetailBtn"><i class="fas fa-save"></i> ${T('adminPanel.dropdownConfigs.save', 'Save')}</button>
                        <button class="btn btn-secondary" id="saveCloseDetailBtn"><i class="fas fa-save"></i> ${T('adminPanel.dropdownConfigs.saveAndClose', 'Save & Close')}</button>
                        <button class="btn btn-secondary" id="closeDetailBtn">${T('adminPanel.dropdownConfigs.close', 'Close')}</button>
                    </div>
                </div>
                
                <div class="detail-content">
                    <div class="section-header">
                        <h3 id="configSectionTitle">${T('adminPanel.dropdownConfigs.configurationValues', 'CONFIGURATION VALUES')}</h3>
                        <button class="btn btn-icon" id="toggleEditModeBtn" title="${T('adminPanel.dropdownConfigs.editModeTitle', 'Edit mode')}">
                    <i class="fas fa-cog"></i>
                        </button>
                    </div>
                    
                    <div class="values-table-container">
                        <table class="values-table">
                            <thead>
                                <tr>
                                    <th>${h(T('adminPanel.dropdownConfigs.colPrimaryName', 'Primary Name'))}</th>
                                    <th>${h(T('adminPanel.dropdownConfigs.colDescription', 'Description'))}</th>
                                    <th>${h(T('adminPanel.dropdownConfigs.colLastUpdated', 'Last Updated'))}</th>
                                    <th>${h(T('adminPanel.dropdownConfigs.colUpdatedBy', 'Updated By'))}</th>
                                    <th class="action-column">${h(T('adminPanel.dropdownConfigs.colAction', 'Action'))}</th>
                                </tr>
                            </thead>
                            <tbody id="valuesTableBody">
                                <tr class="loading-row">
                                    <td colspan="5"><i class="fas fa-spinner fa-spin"></i> ${T('adminPanel.dropdownConfigs.loadingValues', 'Loading values...')}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    <!-- Unsaved changes banner -->
                    <div class="unsaved-changes-banner" id="unsavedChangesBanner" style="display:none;">
                        <i class="fas fa-exclamation-triangle"></i>
                        <span>${T('adminPanel.dropdownConfigs.unsavedChanges', 'You have unsaved changes.')}</span>
                    </div>
                </div>
            </div>
        </div>
        ${getValueModalMarkup()}
    `;
}

function getValueModalMarkup() {
    const T = ddT;
    const h = escapeHtml;
    return `
        <div class="modal-overlay" id="valueModal">
            <div class="modal-dialog">
                <div class="modal-header">
                    <h3 id="valueModalTitle">${h(T('adminPanel.dropdownConfigs.addValue', 'Add Value'))}</h3>
                    <button class="modal-close" id="valueModalClose">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <form id="valueForm">
                    <div class="modal-body">
                        <div class="form-group">
                            <label for="valueName">${h(T('adminPanel.dropdownConfigs.labelPrimaryNameRequired', 'Primary Name'))} <span class="required">*</span></label>
                            <input type="text" id="valueName" name="name" required>
                        </div>
                        <div class="form-group">
                            <label for="valueDescription">${h(T('adminPanel.dropdownConfigs.colDescription', 'Description'))}</label>
                            <textarea id="valueDescription" name="description" rows="3"></textarea>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" id="valueModalCancel">${h(T('adminPanel.dropdownConfigs.cancel', 'Cancel'))}</button>
                        <button type="submit" class="btn btn-primary" id="valueModalSave">${h(T('adminPanel.dropdownConfigs.add', 'Add'))}</button>
                    </div>
                </form>
            </div>
        </div>
    `;
}

async function initializeDropdownConfigsModule() {
    setupEventListeners();
    await loadAllConfigs();
}

function setupEventListeners() {
    // Filters
    const filterChoice = document.getElementById('filterChoice');
    const filterModule = document.getElementById('filterModule');
    const filterUpdate = document.getElementById('filterUpdate');
    const filterUpdatedBy = document.getElementById('filterUpdatedBy');
    
    if (filterChoice) filterChoice.addEventListener('change', () => applyFilters());
    if (filterModule) filterModule.addEventListener('change', () => applyFilters());
    if (filterUpdate) filterUpdate.addEventListener('change', () => applyFilters());
    if (filterUpdatedBy) filterUpdatedBy.addEventListener('change', () => applyFilters());
    
    // Detail view
    document.getElementById('backToListBtn')?.addEventListener('click', handleBackToList);
    document.getElementById('closeDetailBtn')?.addEventListener('click', handleBackToList);
    document.getElementById('toggleEditModeBtn')?.addEventListener('click', toggleEditMode);
    
    // Save buttons
    document.getElementById('saveDetailBtn')?.addEventListener('click', () => saveAllChanges(false));
    document.getElementById('saveCloseDetailBtn')?.addEventListener('click', () => saveAllChanges(true));
    
    // Modal
    document.getElementById('valueModalClose')?.addEventListener('click', closeValueModal);
    document.getElementById('valueModalCancel')?.addEventListener('click', closeValueModal);
    document.getElementById('valueForm')?.addEventListener('submit', handleValueFormSubmit);
    document.getElementById('valueModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'valueModal') closeValueModal();
    });
}

// ============================================================================
// PENDING CHANGES TRACKING
// ============================================================================

function clearPendingChanges() {
    dropdownConfigState.pendingChanges.clear();
    dropdownConfigState.pendingAdds = [];
    dropdownConfigState.pendingDeletes.clear();
    updateUnsavedBanner();
}

function hasPendingChanges() {
    return dropdownConfigState.pendingChanges.size > 0 ||
           dropdownConfigState.pendingAdds.length > 0 ||
           dropdownConfigState.pendingDeletes.size > 0;
}

function updateUnsavedBanner() {
    const banner = document.getElementById('unsavedChangesBanner');
    if (banner) {
        banner.style.display = hasPendingChanges() ? 'flex' : 'none';
    }
    // Also update save buttons opacity
    const saveBtn = document.getElementById('saveDetailBtn');
    const saveCloseBtn = document.getElementById('saveCloseDetailBtn');
    const hasChanges = hasPendingChanges();
    if (saveBtn) saveBtn.disabled = !hasChanges;
    if (saveCloseBtn) saveCloseBtn.disabled = !hasChanges;
}

function trackInlineChange(valueId, field, newValue, originalValue) {
    const key = `${valueId}:${field}`;
    if (newValue === originalValue) {
        dropdownConfigState.pendingChanges.delete(key);
    } else {
        dropdownConfigState.pendingChanges.set(key, { valueId, field, newValue, originalValue });
    }
    updateUnsavedBanner();
}

function trackAdd(name, description) {
    dropdownConfigState.pendingAdds.push({ name, description });
    updateUnsavedBanner();
}

function trackDelete(valueId) {
    dropdownConfigState.pendingDeletes.add(valueId);
    // Remove any pending edits for this value
    for (const key of dropdownConfigState.pendingChanges.keys()) {
        if (key.startsWith(`${valueId}:`)) {
            dropdownConfigState.pendingChanges.delete(key);
        }
    }
    updateUnsavedBanner();
}

async function saveAllChanges(closeAfter) {
    if (!hasPendingChanges()) {
        if (closeAfter) showListView();
        return;
    }

    const colDesc = dropdownConfigState.configDetails?.columnDescription;
    const hasDescription = colDesc && colDesc !== 'null' && colDesc !== null && String(colDesc).trim() !== '';
    let errorOccurred = false;

    // 1. Process deletes
    for (const valueId of dropdownConfigState.pendingDeletes) {
        try {
            const response = await fetch(`/admin/api/dropdown-config/values/${valueId}?configId=${dropdownConfigState.selectedConfigId}`, {
                method: 'DELETE'
            });
            const result = await response.json();
            if (!result.success) {
                notify('Error deleting value: ' + (result.error || 'Unknown error'), 'error');
                errorOccurred = true;
            }
        } catch (error) {
            console.error('Error deleting value:', error);
            notify('Error deleting value', 'error');
            errorOccurred = true;
        }
    }

    // 2. Process inline edits (group by valueId)
    const editsByValue = new Map();
    for (const change of dropdownConfigState.pendingChanges.values()) {
        if (!editsByValue.has(change.valueId)) {
            editsByValue.set(change.valueId, {});
        }
        editsByValue.get(change.valueId)[change.field] = change.newValue;
    }

    for (const [valueId, fields] of editsByValue) {
        // Skip if this value was also deleted
        if (dropdownConfigState.pendingDeletes.has(valueId)) continue;

        const value = dropdownConfigState.dropdownValues.find(v => v.id === valueId);
        if (!value) continue;

        const requestData = {
            configId: dropdownConfigState.selectedConfigId,
            name: fields.name !== undefined ? fields.name : value.name
        };
        if (hasDescription) {
            requestData.description = fields.description !== undefined ? fields.description : (value.description || '');
        }

        try {
            const response = await fetch(`/admin/api/dropdown-config/values/${valueId}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            const result = await response.json();
            if (!result.success) {
                notify('Error updating value: ' + (result.error || 'Unknown error'), 'error');
                errorOccurred = true;
            }
        } catch (error) {
            console.error('Error updating value:', error);
            notify('Error updating value', 'error');
            errorOccurred = true;
        }
    }

    // 3. Process adds
    for (const add of dropdownConfigState.pendingAdds) {
        const requestData = {
            configId: dropdownConfigState.selectedConfigId,
            name: add.name
        };
        if (hasDescription) {
            requestData.description = add.description || '';
        }

        try {
            const response = await fetch('/admin/api/dropdown-config/values', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(requestData)
            });
            const result = await response.json();
            if (!result.success) {
                notify(ddT('adminPanel.dropdownConfigs.errorAddingValueWithMsg', '') + (result.error || ddT('adminPanel.customFieldsPage.unknownError', 'Unknown error')), 'error');
                errorOccurred = true;
            }
        } catch (error) {
            console.error('Error adding value:', error);
            notify(ddT('adminPanel.dropdownConfigs.errorAddingValue', 'Error adding value'), 'error');
            errorOccurred = true;
        }
    }

    // Clear pending changes and reload
    clearPendingChanges();

    if (!errorOccurred) {
        notify(ddT('adminPanel.dropdownConfigs.changesSaved', 'Changes saved successfully'), 'success');
    }

    // Reload values from server
    await loadConfigValues(dropdownConfigState.selectedConfigId);

    if (closeAfter) {
        showListView();
        // Refresh the list to show updated timestamps
        await loadAllConfigs();
    }
}

function handleBackToList() {
    if (hasPendingChanges()) {
        const msg = ddT('adminPanel.dropdownConfigs.discardUnsavedConfirm', 'You have unsaved changes. Discard them?');
        if (!confirm(msg)) return;
    }
    clearPendingChanges();
    showListView();
}

// ============================================================================
// DATA LOADING
// ============================================================================

async function loadAllConfigs(updatedByUserId = null, skipPopulateFilters = false) {
    try {
        let url = '/admin/api/dropdown-config';
        if (updatedByUserId) {
            url += `?updatedByUserId=${updatedByUserId}`;
        }
        
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            dropdownConfigState.allConfigs = data.data || [];
            if (!skipPopulateFilters) {
                await populateFilters();
                await applyFilters(true);
            }
        } else {
            console.error('Failed to load configs:', data);
            notify(ddT('adminPanel.dropdownConfigs.errorLoadingConfigs', 'Error loading configurations'), 'error');
        }
    } catch (error) {
        console.error('Error loading configurations:', error);
        notify(ddT('adminPanel.dropdownConfigs.errorLoadingConfigs', '') + ': ' + error.message, 'error');
    }
}

async function populateFilters() {
    const choiceFilter = document.getElementById('filterChoice');
    const uniqueChoices = [...new Set(dropdownConfigState.allConfigs.map(c => c.dropdownDisplayName))].sort();
    if (choiceFilter) {
        const allChoices = typeof adminT === 'function' ? adminT('adminPanel.dropdownConfigs.allChoices', 'All Choices') : 'All Choices';
        choiceFilter.innerHTML = '<option value="">' + allChoices + '</option>' +
            uniqueChoices.map(choice => `<option value="${escapeHtml(choice)}">${escapeHtml(choice)}</option>`).join('');
    }
    
    const moduleFilter = document.getElementById('filterModule');
    const uniqueModules = [...new Set(dropdownConfigState.allConfigs.map(c => c.moduleName))].sort();
    if (moduleFilter) {
        const allModules = typeof adminT === 'function' ? adminT('adminPanel.dropdownConfigs.allModules', 'All Modules') : 'All Modules';
        moduleFilter.innerHTML = '<option value="">' + allModules + '</option>' +
            uniqueModules.map(module => `<option value="${escapeHtml(module)}">${escapeHtml(module)}</option>`).join('');
    }
    
    await populateUpdatedByFilter();
}

async function populateUpdatedByFilter() {
    const updatedByFilter = document.getElementById('filterUpdatedBy');
    if (!updatedByFilter) return;
    
    try {
        const response = await fetch('/api/people');
        if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
        
        const people = await response.json();
        let peopleArray = [];
        if (Array.isArray(people)) {
            peopleArray = people;
        } else if (people && Array.isArray(people.data)) {
            peopleArray = people.data;
        } else if (people && people.success && Array.isArray(people.data)) {
            peopleArray = people.data;
        } else {
            const allUsers = typeof adminT === 'function' ? adminT('adminPanel.dropdownConfigs.allUsers', 'All Users') : 'All Users';
            updatedByFilter.innerHTML = '<option value="">' + allUsers + '</option>';
            return;
        }
        
        const userMap = new Map();
        peopleArray.forEach(person => {
            const firstName = person.First_Name || person.first_name || person.FirstName || '';
            const lastName = person.Last_Name || person.last_name || person.LastName || '';
            const fullName = `${firstName} ${lastName}`.trim();
            const userId = person.ID || person.id || person.userId;
            if (fullName && userId) userMap.set(userId, fullName);
        });
        
        const sortedUsers = Array.from(userMap.entries())
            .map(([id, name]) => ({ id, name }))
            .sort((a, b) => a.name.localeCompare(b.name));
        
        const allUsers2 = typeof adminT === 'function' ? adminT('adminPanel.dropdownConfigs.allUsers', 'All Users') : 'All Users';
        updatedByFilter.innerHTML = '<option value="">' + allUsers2 + '</option>' +
            sortedUsers.map(user => 
                `<option value="${user.id}">${escapeHtml(user.name)}</option>`
            ).join('');
        
    } catch (error) {
        console.error('Error loading users for filter:', error);
        const allUsers3 = typeof adminT === 'function' ? adminT('adminPanel.dropdownConfigs.allUsers', 'All Users') : 'All Users';
        updatedByFilter.innerHTML = '<option value="">' + allUsers3 + '</option>';
    }
}

async function applyFilters(skipUserFilterReload = false) {
    try {
        const choiceFilter = document.getElementById('filterChoice')?.value || '';
        const moduleFilter = document.getElementById('filterModule')?.value || '';
        const updateFilter = document.getElementById('filterUpdate')?.value || '';
        const updatedByFilter = document.getElementById('filterUpdatedBy')?.value || '';
        
        const currentUserFilter = updatedByFilter || null;
        const previousUserFilter = dropdownConfigState.lastUpdatedByFilter;
        const userFilterChanged = !skipUserFilterReload && (currentUserFilter !== previousUserFilter);
        
        if (userFilterChanged) {
            dropdownConfigState.lastUpdatedByFilter = currentUserFilter;
            await loadAllConfigs(currentUserFilter, true);
            
            if (choiceFilter) {
                const choiceEl = document.getElementById('filterChoice');
                if (choiceEl) choiceEl.value = choiceFilter;
            }
            if (moduleFilter) {
                const moduleEl = document.getElementById('filterModule');
                if (moduleEl) moduleEl.value = moduleFilter;
            }
            if (updateFilter) {
                const updateEl = document.getElementById('filterUpdate');
                if (updateEl) updateEl.value = updateFilter;
            }
        }
        
        dropdownConfigState.filteredConfigs = dropdownConfigState.allConfigs.filter(config => {
            const matchesChoice = !choiceFilter || config.dropdownDisplayName === choiceFilter;
            const matchesModule = !moduleFilter || config.moduleName === moduleFilter;
            let matchesUpdate = true;
            if (updateFilter && config.updatedAt) {
                const filterDate = new Date(updateFilter);
                const configDate = new Date(config.updatedAt);
                matchesUpdate = configDate >= filterDate;
            }
            return matchesChoice && matchesModule && matchesUpdate;
        });
        
        renderConfigsList();
    } catch (error) {
        console.error('Error applying filters:', error);
    }
}

function renderConfigsList() {
    const tbody = document.getElementById('configsTableBody');
    if (!tbody) return;
    
    if (dropdownConfigState.filteredConfigs.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td><i class="fas fa-info-circle"></i> No configurations found</td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = dropdownConfigState.filteredConfigs.map(config => `
        <tr class="config-row" data-config-id="${config.id}" onclick="openConfigDetail(${config.id})">
            <td>
                <div class="config-item">
                    <i class="fas fa-file-alt"></i>
                    <div class="config-info">
                        <div class="config-name">${escapeHtml(config.dropdownDisplayName)}</div>
                        <div class="config-meta">
                            <span class="config-module">${escapeHtml(config.moduleName)}</span>
                            ${config.updatedAt ? `<span class="config-date">${formatDate(config.updatedAt)}</span>` : ''}
                        </div>
                    </div>
                </div>
            </td>
        </tr>
    `).join('');
}

// ============================================================================
// DETAIL VIEW
// ============================================================================

async function openConfigDetail(configId) {
    dropdownConfigState.selectedConfigId = configId;
    dropdownConfigState.configDetails = dropdownConfigState.allConfigs.find(c => c.id === configId);
    
    if (!dropdownConfigState.configDetails) return;
    
    // Enable edit mode by default
    dropdownConfigState.editMode = true;
    updateEditModeButton();
    
    // Clear any pending changes from previous detail view
    clearPendingChanges();
    
    // Update title
    document.getElementById('configDetailTitle').textContent = dropdownConfigState.configDetails.dropdownDisplayName;
    document.getElementById('configSectionTitle').textContent = dropdownConfigState.configDetails.dropdownDisplayName.toUpperCase();
    
    // Update description field visibility
    updateDescriptionFieldVisibility();
    
    // Load values
    await loadConfigValues(configId);
    
    // Show detail view
    document.getElementById('dropdownConfigsListView').style.display = 'none';
    document.getElementById('dropdownConfigDetailView').style.display = 'block';
}

async function loadConfigValues(configId) {
    const tbody = document.getElementById('valuesTableBody');
    if (!tbody) return;
    
    tbody.innerHTML = `
        <tr class="loading-row">
            <td colspan="5"><i class="fas fa-spinner fa-spin"></i> Loading values...</td>
        </tr>
    `;
    
    try {
        const response = await fetch(`/admin/api/dropdown-config/values?configId=${configId}`);
        const data = await response.json();
        
        if (data.success) {
            dropdownConfigState.dropdownValues = data.data || [];
            renderValuesTable();
        } else {
            tbody.innerHTML = `
                <tr class="error-row">
                    <td colspan="5"><i class="fas fa-exclamation-triangle"></i> Error loading values</td>
                </tr>
            `;
        }
    } catch (error) {
        console.error('Error loading values:', error);
        tbody.innerHTML = `
            <tr class="error-row">
                <td colspan="5"><i class="fas fa-exclamation-triangle"></i> Error loading values</td>
            </tr>
        `;
    }
}

function renderValuesTable() {
    const tbody = document.getElementById('valuesTableBody');
    const thead = document.querySelector('.values-table thead tr');
    if (!tbody) return;
    
    // Check if config has description column
    const colDesc = dropdownConfigState.configDetails?.columnDescription;
    const hasDescription = colDesc && 
                          colDesc !== 'null' &&
                          colDesc !== null &&
                          String(colDesc).trim() !== '';
    
    // Always show Last Updated and Updated By columns
    const hasLastUpdated = true;
    const hasUpdatedBy = true;
    
    // Build header
    const T = ddT;
    const h = escapeHtml;
    if (thead) {
        let headerHtml = '<th>' + h(T('adminPanel.dropdownConfigs.colPrimaryName', 'Primary Name')) + '</th>';
        if (hasDescription) headerHtml += '<th>' + h(T('adminPanel.dropdownConfigs.colDescription', 'Description')) + '</th>';
        headerHtml += '<th>' + h(T('adminPanel.dropdownConfigs.colLastUpdated', 'Last Updated')) + '</th>';
        headerHtml += '<th>' + h(T('adminPanel.dropdownConfigs.colUpdatedBy', 'Updated By')) + '</th>';
        headerHtml += '<th class="action-column">' + h(T('adminPanel.dropdownConfigs.colAction', 'Action')) + '</th>';
        thead.innerHTML = headerHtml;
    }
    
    const colCount = 1 + (hasDescription ? 1 : 0) + 2 + 1; // +2 for Last Updated + Updated By
    
    if (dropdownConfigState.dropdownValues.length === 0) {
        const noValues = h(T('adminPanel.dropdownConfigs.noValuesConfigured', 'No values configured'));
        const addVal = h(T('adminPanel.dropdownConfigs.addValue', 'Add Value'));
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="${colCount}">
                    <i class="fas fa-info-circle"></i> ${noValues}
                    ${dropdownConfigState.editMode ? '<button class="btn btn-sm btn-primary" onclick="openValueModal()"><i class="fas fa-plus"></i> ' + addVal + '</button>' : ''}
                </td>
            </tr>
        `;
        return;
    }
    
    // Filter out pending deletes for display
    const visibleValues = dropdownConfigState.dropdownValues.filter(
        v => !dropdownConfigState.pendingDeletes.has(v.id)
    );

    // Add pending adds to display
    let rows = visibleValues.map((value, index) => {
        const isEdited = hasFieldPendingChange(value.id);
        const rowClass = isEdited ? 'value-row edited-row' : 'value-row';

        const titleAdd = escapeHtml(T('adminPanel.dropdownConfigs.titleAddNewValue', 'Add new value'));
        const titleDel = escapeHtml(T('adminPanel.dropdownConfigs.titleDeleteValue', 'Delete value'));
        const actions = dropdownConfigState.editMode ? `
            <div class="action-buttons">
                <button class="btn-icon btn-add" onclick="openValueModal()" title="${titleAdd}">
                    <i class="fas fa-plus"></i>
                </button>
                <button class="btn-icon btn-delete" onclick="markDeleteValue(${value.id})" title="${titleDel}">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        ` : '<span class="text-muted">—</span>';
        
        // Get display values (use pending change if exists)
        const displayName = getPendingOrOriginal(value.id, 'name', value.name || '');
        const displayDesc = getPendingOrOriginal(value.id, 'description', value.description || '');

        let cells = `<td class="editable-cell" data-field="name" data-value-id="${value.id}">${escapeHtml(displayName)}</td>`;
        if (hasDescription) {
            cells += `<td class="editable-cell" data-field="description" data-value-id="${value.id}">${escapeHtml(displayDesc)}</td>`;
        }
        if (hasLastUpdated) {
            cells += `<td class="meta-cell">${value.lastUpdated ? formatDate(value.lastUpdated) : '—'}</td>`;
        }
        if (hasUpdatedBy) {
            cells += `<td class="meta-cell">${escapeHtml(value.updatedByName || '—')}</td>`;
        }
        cells += `<td class="action-column">${actions}</td>`;
        
        return `<tr class="${rowClass}" data-value-id="${value.id}">${cells}</tr>`;
    });

    // Show pending adds as new rows at the bottom
    dropdownConfigState.pendingAdds.forEach((add, idx) => {
        const badgeNew = escapeHtml(T('adminPanel.dropdownConfigs.badgeNew', 'new'));
        let cells = `<td class="pending-add-cell">${escapeHtml(add.name)} <span class="badge-new">${badgeNew}</span></td>`;
        if (hasDescription) cells += `<td class="pending-add-cell">${escapeHtml(add.description || '')}</td>`;
        if (hasLastUpdated) cells += `<td class="meta-cell">—</td>`;
        if (hasUpdatedBy) cells += `<td class="meta-cell">—</td>`;
        cells += `<td class="action-column">
            <div class="action-buttons">
                <button class="btn-icon btn-delete" onclick="undoPendingAdd(${idx})" title="${escapeHtml(T('adminPanel.dropdownConfigs.titleRemove', 'Remove'))}">
                    <i class="fas fa-times"></i>
                </button>
            </div>
        </td>`;
        rows.push(`<tr class="value-row pending-add-row">${cells}</tr>`);
    });

    tbody.innerHTML = rows.join('');
    
    // Attach double-click event listeners for inline editing
    attachInlineEditListeners();
}

function hasFieldPendingChange(valueId) {
    for (const key of dropdownConfigState.pendingChanges.keys()) {
        if (key.startsWith(`${valueId}:`)) return true;
    }
    return false;
}

function getPendingOrOriginal(valueId, field, original) {
    const key = `${valueId}:${field}`;
    const change = dropdownConfigState.pendingChanges.get(key);
    return change ? change.newValue : original;
}

function markDeleteValue(valueId) {
    const value = dropdownConfigState.dropdownValues.find(v => v.id === valueId);
    if (!value) return;
    
    const delMsg = ddT('adminPanel.dropdownConfigs.markForDeletionConfirm', 'Mark "{name}" for deletion?').replace('{name}', value.name);
    if (!confirm(delMsg)) return;
    
    trackDelete(valueId);
    renderValuesTable();
}

function undoPendingAdd(index) {
    dropdownConfigState.pendingAdds.splice(index, 1);
    updateUnsavedBanner();
    renderValuesTable();
}

function toggleEditMode() {
    dropdownConfigState.editMode = !dropdownConfigState.editMode;
    updateEditModeButton();
    renderValuesTable();
}

function updateEditModeButton() {
    const btn = document.getElementById('toggleEditModeBtn');
    if (btn) {
        btn.classList.toggle('active', dropdownConfigState.editMode);
        btn.title = dropdownConfigState.editMode
            ? ddT('adminPanel.dropdownConfigs.exitEditMode', 'Exit edit mode')
            : ddT('adminPanel.dropdownConfigs.enterEditMode', 'Enter edit mode');
        const icon = btn.querySelector('i');
        if (icon) {
            icon.className = dropdownConfigState.editMode ? 'fas fa-cog fa-spin' : 'fas fa-cog';
        }
    }
}

function showListView() {
    document.getElementById('dropdownConfigDetailView').style.display = 'none';
    document.getElementById('dropdownConfigsListView').style.display = 'block';
    dropdownConfigState.selectedConfigId = null;
    dropdownConfigState.editMode = false;
    clearPendingChanges();
}

let editingValueId = null;

function updateDescriptionFieldVisibility() {
    const colDesc = dropdownConfigState.configDetails?.columnDescription;
    const hasDescription = colDesc && 
                          colDesc !== 'null' &&
                          colDesc !== null &&
                          String(colDesc).trim() !== '';
    
    const descriptionField = document.getElementById('valueDescription');
    let descriptionGroup = null;
    if (descriptionField) {
        descriptionGroup = descriptionField.closest('.form-group');
    }
    if (descriptionGroup) {
        descriptionGroup.style.display = hasDescription ? 'block' : 'none';
    }
}

function openValueModal(valueId = null) {
    const modal = document.getElementById('valueModal');
    const title = document.getElementById('valueModalTitle');
    const form = document.getElementById('valueForm');
    
    if (!modal || !form) return;
    
    form.reset();
    editingValueId = valueId;
    
    updateDescriptionFieldVisibility();
    
    const saveBtn = document.getElementById('valueModalSave');
    if (valueId) {
        const value = dropdownConfigState.dropdownValues.find(v => v.id === valueId);
        if (value) {
            title.textContent = ddT('adminPanel.dropdownConfigs.editValue', 'Edit Value');
            if (saveBtn) saveBtn.textContent = ddT('adminPanel.dropdownConfigs.saveChanges', 'Save');
            document.getElementById('valueName').value = value.name || '';
            const colDesc = dropdownConfigState.configDetails?.columnDescription;
            const hasDescription = colDesc && colDesc !== 'null' && colDesc !== null && String(colDesc).trim() !== '';
            if (hasDescription) {
                document.getElementById('valueDescription').value = value.description || '';
            }
        }
    } else {
        title.textContent = ddT('adminPanel.dropdownConfigs.addValue', 'Add Value');
        if (saveBtn) saveBtn.textContent = ddT('adminPanel.dropdownConfigs.add', 'Add');
    }
    
    modal.classList.add('show');
}

function closeValueModal() {
    const modal = document.getElementById('valueModal');
    if (modal) {
        modal.classList.remove('show');
        editingValueId = null;
    }
}

async function handleValueFormSubmit(e) {
    e.preventDefault();
    
    const name = document.getElementById('valueName').value.trim();
    if (!name) {
        notify(ddT('adminPanel.dropdownConfigs.primaryNameRequired', 'Primary name is required'), 'error');
        return;
    }
    
    const colDesc = dropdownConfigState.configDetails?.columnDescription;
    const hasDescription = colDesc && colDesc !== 'null' && colDesc !== null && String(colDesc).trim() !== '';
    const description = hasDescription ? (document.getElementById('valueDescription').value.trim() || '') : '';
    
    // Add to pending instead of saving immediately
    trackAdd(name, description);
    closeValueModal();
    renderValuesTable();
    notify(ddT('adminPanel.dropdownConfigs.valueAddedPending', 'Value added (pending save)'), 'info');
}

async function deleteValue(valueId) {
    // Legacy - now routed through markDeleteValue
    markDeleteValue(valueId);
}

// ============================================================================
// INLINE EDITING FUNCTIONALITY
// ============================================================================

let currentlyEditingCell = null;

function attachInlineEditListeners() {
    const editableCells = document.querySelectorAll('.editable-cell');
    
    editableCells.forEach(cell => {
        cell.addEventListener('dblclick', function() {
            if (currentlyEditingCell && currentlyEditingCell !== this) {
                finishInlineEdit();
            }
            startInlineEdit(this);
        });
    });
}

function startInlineEdit(cell) {
    currentlyEditingCell = cell;
    
    const valueId = parseInt(cell.dataset.valueId);
    const field = cell.dataset.field;
    const currentValue = cell.textContent.trim();
    
    const value = dropdownConfigState.dropdownValues.find(v => v.id === valueId);
    if (!value) return;
    
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'inline-edit-input';
    input.value = currentValue;
    input.dataset.valueId = valueId;
    input.dataset.field = field;
    // Store the real original from the server (not from pending changes)
    input.dataset.originalValue = field === 'name' ? (value.name || '') : (value.description || '');
    
    cell.innerHTML = '';
    cell.appendChild(input);
    cell.classList.add('editing');
    
    input.focus();
    input.select();
    
    input.addEventListener('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            finishInlineEdit();
        } else if (e.key === 'Escape') {
            e.preventDefault();
            cancelInlineEdit();
        }
    });
    
    input.addEventListener('blur', function() {
        setTimeout(() => {
            if (currentlyEditingCell === cell) {
                finishInlineEdit();
            }
        }, 200);
    });
}

function finishInlineEdit() {
    if (!currentlyEditingCell) return;
    
    const input = currentlyEditingCell.querySelector('.inline-edit-input');
    if (!input) return;
    
    const valueId = parseInt(input.dataset.valueId);
    const field = input.dataset.field;
    const newValue = input.value.trim();
    const originalValue = input.dataset.originalValue;
    
    if (field === 'name' && !newValue) {
        notify(ddT('adminPanel.dropdownConfigs.primaryNameCannotBeEmpty', 'Primary name cannot be empty'), 'error');
        input.focus();
        return;
    }
    
    // Track the change as pending (won't save until Save button is clicked)
    trackInlineChange(valueId, field, newValue, originalValue);
    
    // Update cell display
    currentlyEditingCell.textContent = newValue;
    currentlyEditingCell.classList.remove('editing');
    
    // Highlight the row if it has changes
    const row = currentlyEditingCell.closest('tr');
    if (row) {
        row.classList.toggle('edited-row', hasFieldPendingChange(valueId));
    }
    
    currentlyEditingCell = null;
}

function cancelInlineEdit() {
    if (!currentlyEditingCell) return;
    
    const input = currentlyEditingCell.querySelector('.inline-edit-input');
    if (input) {
        const valueId = parseInt(input.dataset.valueId);
        const field = input.dataset.field;
        // Restore to pending value or original
        const value = dropdownConfigState.dropdownValues.find(v => v.id === valueId);
        const display = getPendingOrOriginal(valueId, field, value ? (field === 'name' ? value.name : value.description) || '' : '');
        currentlyEditingCell.textContent = display;
        currentlyEditingCell.classList.remove('editing');
    }
    
    currentlyEditingCell = null;
}

// ============================================================================
// UTILITIES
// ============================================================================

function formatDate(dateString) {
    if (!dateString) return '';
    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = months[date.getMonth()];
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${day}-${month}-${year} ${hours}:${minutes}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function notify(message, type) {
    if (typeof showNotification === 'function') {
        showNotification(message, type);
    } else {
        console.log(`[${type}] ${message}`);
    }
}
