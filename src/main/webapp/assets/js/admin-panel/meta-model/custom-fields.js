// Custom Fields Admin Configuration - Simplified Implementation

function cfT(key, fallback) {
    return typeof adminT === 'function' ? adminT(key, fallback) : fallback;
}

function getCustomFieldTypes() {
    const T = cfT;
    return [
        { value: 'text', label: T('adminPanel.customFieldsPage.typeText', 'Text') },
        { value: 'number', label: T('adminPanel.customFieldsPage.typeNumber', 'Number') },
        { value: 'decimal', label: T('adminPanel.customFieldsPage.typeDecimal', 'Decimal') },
        { value: 'date', label: T('adminPanel.customFieldsPage.typeDate', 'Date') },
        { value: 'checkbox', label: T('adminPanel.customFieldsPage.typeCheckbox', 'Checkbox') },
        { value: 'time', label: T('adminPanel.customFieldsPage.typeTime', 'Time') },
        { value: 'percentage', label: T('adminPanel.customFieldsPage.typePercentage', 'Percentage') },
        { value: 'dropdown', label: T('adminPanel.customFieldsPage.typeDropdown', 'Single Selection Dropdown') },
        { value: 'multiselect', label: T('adminPanel.customFieldsPage.typeMultiselect', 'Multiple Selection Dropdown') }
    ];
}

let customFieldsState = {
    selectedFacetId: '',
    editMode: false,
    activeFieldId: null,
    facetBlockingCr: false
};

function showCustomFieldsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.customFields');
    if (typeof addToNavigationHistory === 'function') {
        addToNavigationHistory(cfT('adminPanel.customFieldsPage.headerTitle', 'Configure Custom Fields'), showCustomFieldsContent, applyCustomFieldsHeader);
    }
    applyCustomFieldsHeader();
    if (contentArea) {
        contentArea.innerHTML = getCustomFieldsLayout();
        initializeCustomFieldsModule();
    }
}

function applyCustomFieldsHeader() {
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (!adminHeaderBar) return;
    const T = cfT;
    const h = escapeHtml;
    adminHeaderBar.innerHTML = `
        <div class="header-left">
            <button class="back-button" onclick="goBack()">
                <i class="fas fa-arrow-left"></i>
            </button>
        </div>
        <div class="header-center">
            <h1 class="system-title">${h(T('adminPanel.customFieldsPage.headerTitle', 'Configure Custom Fields'))}</h1>
            <div class="system-status">
                <span class="status-dot"></span>
                ${h(T('adminPanel.customFieldsPage.budgManagement', 'BUDG Management'))}
            </div>
        </div>
    `;
}

function getCustomFieldsLayout() {
    const T = cfT;
    const h = escapeHtml;
    return `
        <section class="custom-fields-page">
            <div class="custom-fields-card">
                <div class="custom-fields-card-header">
                    <div class="card-title-block">
                        <label class="card-subtitle" for="customFieldsFacetSelect">${h(T('adminPanel.customFieldsPage.facets', 'Facets'))}</label>
                        <div class="custom-field-select-wrapper">
                            <i class="fas fa-layer-group"></i>
                            <select id="customFieldsFacetSelect" aria-label="${h(T('adminPanel.customFieldsPage.facets', 'Facets'))}">
                                <option value="">${h(T('adminPanel.customFieldsPage.selectFacet', 'Select facet'))}</option>
                            </select>
                        </div>
                    </div>
                    <div class="card-actions">
                        <button class="btn btn-primary" id="customFieldsEditBtn" disabled>
                            <i class="fas fa-edit"></i> ${h(T('adminPanel.customFieldsPage.edit', 'Edit'))}
                        </button>
                        <button class="btn btn-secondary" id="customFieldsCloseBtn" style="display:none;">
                            <i class="fas fa-times"></i> ${h(T('adminPanel.customFieldsPage.close', 'Close'))}
                        </button>
                    </div>
                </div>
                <div class="custom-fields-table-section">
                    <div class="table-toolbar">
                        <span class="table-title" id="customFieldsFacetSummary">${h(T('adminPanel.customFieldsPage.selectFacetSummary', 'Select a facet...'))}</span>
                        <button class="btn btn-success" id="customFieldsAddBtn" style="display:none;">
                            <i class="fas fa-plus"></i> ${h(T('adminPanel.customFieldsPage.addCustomField', 'Add Custom Field'))}
                        </button>
                    </div>
                    <div class="table-responsive">
                        <table class="custom-fields-table">
                            <thead>
                                <tr>
                                    <th>${h(T('adminPanel.customFieldsPage.colDisplayName', 'Display Name'))}</th>
                                    <th>${h(T('adminPanel.customFieldsPage.colTechnicalName', 'Technical Name'))}</th>
                                    <th>${h(T('adminPanel.customFieldsPage.colType', 'Type'))}</th>
                                    <th>${h(T('adminPanel.customFieldsPage.colDefaultValue', 'Default Value'))}</th>
                                    <th>${h(T('adminPanel.customFieldsPage.colMandatory', 'Mandatory'))}</th>
                                    <th>${h(T('adminPanel.customFieldsPage.colLastUpdated', 'Last Updated Time'))}</th>
                                    <th>${h(T('adminPanel.customFieldsPage.colUpdatedBy', 'Updated By'))}</th>
                                    <th class="action-column">${h(T('adminPanel.customFieldsPage.colAction', 'Action'))}</th>
                                </tr>
                            </thead>
                            <tbody id="customFieldsTableBody">
                                <tr class="empty-state">
                                    <td colspan="8">
                                        <div class="empty-message">
                                            <i class="fas fa-layer-group"></i>
                                            <p>${h(T('adminPanel.customFieldsPage.emptySelectFacet', 'Select a facet...'))}</p>
                                        </div>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </section>
        ${getCustomFieldModalMarkup()}
    `;
}

function getCustomFieldModalMarkup() {
    const T = cfT;
    const h = escapeHtml;
    const typeOptions = getCustomFieldTypes().map(t => `<option value="${t.value}">${h(t.label)}</option>`).join('');
    return `
        <div class="custom-field-modal-overlay" id="customFieldModal" aria-hidden="true">
            <div class="custom-field-modal" role="dialog" aria-modal="true">
                <div class="modal-header">
                    <h3 id="customFieldModalTitle">${h(T('adminPanel.customFieldsPage.modalAddTitle', 'Add Custom Field'))}</h3>
                    <button type="button" class="modal-close" id="customFieldModalClose">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <form id="customFieldForm" novalidate>
                    <div class="modal-body">
                        <div class="form-sections">
                            <div class="form-section">
                                <h4 class="section-title">${h(T('adminPanel.customFieldsPage.sectionBasic', 'Basic Information'))}</h4>
                                <div class="form-grid">
                                    <div class="form-group full-width">
                                        <label for="cfDisplayName">${h(T('adminPanel.customFieldsPage.labelDisplayName', 'Display Name'))} <span class="required">*</span></label>
                                        <input type="text" id="cfDisplayName" name="displayName" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDisplayName', ''))}" required>
                                        <small class="field-hint">${h(T('adminPanel.customFieldsPage.hintDisplayName', ''))}</small>
                                        <div class="field-error" data-error-for="displayName"></div>
                                    </div>
                                    <div class="form-group full-width">
                                        <label for="cfDescription">${h(T('adminPanel.customFieldsPage.labelDescription', 'Description'))}</label>
                                        <textarea id="cfDescription" name="description" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDescription', ''))}" rows="3"></textarea>
                                    </div>
                                </div>
                            </div>
                            <div class="form-section">
                                <h4 class="section-title">${h(T('adminPanel.customFieldsPage.sectionFieldConfig', 'Field Configuration'))}</h4>
                                <div class="form-grid">
                                    <div class="form-group">
                                        <label for="cfType">${h(T('adminPanel.customFieldsPage.labelType', 'Type'))} <span class="required">*</span></label>
                                        <select id="cfType" name="type" required>
                                            <option value="">${h(T('adminPanel.customFieldsPage.selectFieldType', 'Select field type'))}</option>
                                            ${typeOptions}
                                        </select>
                                    </div>
                                    <div class="form-group">
                                        <label for="cfMandatory" class="checkbox-label">
                                            <input type="checkbox" id="cfMandatory" name="mandatory">
                                            <span class="checkmark"></span>
                                            ${h(T('adminPanel.customFieldsPage.mandatory', 'Mandatory'))}
                                        </label>
                                    </div>
                                </div>
                            </div>
                            <div class="form-section dropdown-values-group" id="cfDropdownValuesGroup" style="display:none;">
                                <h4 class="section-title">${h(T('adminPanel.customFieldsPage.sectionFieldOptions', 'Field Options'))}</h4>
                                <div class="form-grid">
                                    <div class="form-group full-width">
                                        <label for="cfDropdownValues">${h(T('adminPanel.customFieldsPage.labelDropdownValues', 'Dropdown Values'))} <span class="required">*</span></label>
                                        <textarea id="cfDropdownValues" name="dropdownValues" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDropdownValues', ''))}" rows="5"></textarea>
                                        <small class="field-hint">${h(T('adminPanel.customFieldsPage.hintDropdownValues', ''))}</small>
                                        <div class="field-error" data-error-for="dropdownValues"></div>
                                    </div>
                                </div>
                            </div>
                            <div class="form-section">
                                <h4 class="section-title">${h(T('adminPanel.customFieldsPage.sectionDefaultValue', 'Default Value'))}</h4>
                                <div class="form-grid">
                                    <div class="form-group full-width" id="cfDefaultValueGroup">
                                        <label for="cfDefaultValue">${h(T('adminPanel.common.defaultValue', 'Default Value'))}</label>
                                        <div id="cfDefaultValueContainer">
                                            <input type="text" id="cfDefaultValue" name="defaultValue" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDefaultValue', ''))}">
                                        </div>
                                        <div class="field-error" data-error-for="defaultValue"></div>
                                    </div>
                                </div>
                            </div>
                            <div class="form-section" id="cfAdditionalSettingsSection">
                                <h4 class="section-title">${h(T('adminPanel.customFieldsPage.sectionAdditional', 'Additional Settings'))}</h4>
                                <div class="form-grid">
                                    <div class="form-group full-width">
                                        <label for="cfPlaceholder">${h(T('adminPanel.customFieldsPage.labelPlaceholderText', 'Placeholder Text'))}</label>
                                        <input type="text" id="cfPlaceholder" name="placeholder" placeholder="${h(T('adminPanel.customFieldsPage.placeholderInstruction', ''))}">
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" id="customFieldModalCancel">${h(T('adminPanel.customFieldsPage.close', 'Close'))}</button>
                        <button type="submit" class="btn btn-primary" id="customFieldModalSave">${h(T('adminPanel.customFieldsPage.save', 'Save'))}</button>
                    </div>
                </form>
            </div>
        </div>
    `;
}

function initializeCustomFieldsModule() {
    customFieldsState.selectedFacetId = '';
    customFieldsState.editMode = false;
    customFieldsState.activeFieldId = null;
    customFieldsState.facetBlockingCr = false;

    const facetSelect = document.getElementById('customFieldsFacetSelect');
    const editBtn = document.getElementById('customFieldsEditBtn');
    const closeBtn = document.getElementById('customFieldsCloseBtn');
    const addBtn = document.getElementById('customFieldsAddBtn');
    const tableBody = document.getElementById('customFieldsTableBody');
    const modal = document.getElementById('customFieldModal');
    const form = document.getElementById('customFieldForm');
    const typeSelect = document.getElementById('cfType');
    const mandatoryCheckbox = document.getElementById('cfMandatory');

    loadFacetsIntoSelect(facetSelect);

    facetSelect?.addEventListener('change', e => handleFacetChange(e.target.value));
    editBtn?.addEventListener('click', () => toggleEditMode(true));
    closeBtn?.addEventListener('click', () => toggleEditMode(false));
    addBtn?.addEventListener('click', () => openCustomFieldModal());
    document.getElementById('customFieldModalClose')?.addEventListener('click', closeCustomFieldModal);
    document.getElementById('customFieldModalCancel')?.addEventListener('click', closeCustomFieldModal);
    modal?.addEventListener('click', e => { if (e.target === modal) closeCustomFieldModal(); });
    typeSelect?.addEventListener('change', () => adjustModalForType(typeSelect.value));
    mandatoryCheckbox?.addEventListener('change', () => handleMandatoryChange());
    form?.addEventListener('submit', handleCustomFieldFormSubmit);
    tableBody?.addEventListener('click', handleTableClick);
}

async function loadFacetsIntoSelect(selectElement) {
    if (!selectElement) return;
    const T = cfT;
    selectElement.disabled = true;
    selectElement.innerHTML = '<option value="">' + escapeHtml(T('adminPanel.customFieldsPage.loadingFacets', 'Loading facets...')) + '</option>';

    try {
        const response = await fetch('/admin/api/facets');
        if (response.ok) {
            const data = await response.json();
            const allFacets = Array.isArray(data.data) ? data.data : [];
            const excludedFacets = ['active tasks', 'role'];
            const facets = allFacets.filter(f => {
                const name = (f.name || '').toLowerCase().trim();
                return !excludedFacets.includes(name);
            });
            selectElement.innerHTML = '<option value="">' + escapeHtml(T('adminPanel.customFieldsPage.selectFacet', 'Select facet')) + '</option>' +
                facets.map(f => `<option value="${f.id}">${escapeHtml(f.name)}</option>`).join('');
        }
    } catch (error) {
        console.error('Error loading facets:', error);
    } finally {
        selectElement.disabled = false;
    }
}

function handleFacetChange(facetId) {
    customFieldsState.selectedFacetId = facetId || '';
    customFieldsState.editMode = false;
    toggleEditMode(false);
    updateFacetSummary();
    if (!facetId) {
        renderEmptyState();
        return;
    }
    refreshCustomFieldsForFacet(facetId);
}

async function refreshCustomFieldsForFacet(facetId) {
    if (!facetId) return;
    setTableLoadingState();
    try {
        const response = await fetch(`/api/custom-fields/metadata?facetId=${encodeURIComponent(facetId)}`);
        if (response.ok) {
            const data = await response.json();
            const fields = Array.isArray(data.data) ? data.data : [];
            renderCustomFieldsTable(fields);
        } else {
            renderEmptyState();
        }
    } catch (error) {
        console.error('Error loading custom fields:', error);
        renderEmptyState();
    }
    updateEditControls();
    updateFacetSummary();
}

function renderEmptyState() {
    const tableBody = document.getElementById('customFieldsTableBody');
    if (!tableBody) return;
    const msg = escapeHtml(cfT('adminPanel.customFieldsPage.emptySelectFacet', 'Select a facet...'));
    tableBody.innerHTML = `
        <tr class="empty-state">
            <td colspan="8">
                <div class="empty-message">
                    <i class="fas fa-layer-group"></i>
                    <p>${msg}</p>
                </div>
            </td>
        </tr>
    `;
}

function renderCustomFieldsTable(fields) {
    const tableBody = document.getElementById('customFieldsTableBody');
    if (!tableBody) return;
    const T = cfT;

    if (!Array.isArray(fields) || fields.length === 0) {
        const noFields = escapeHtml(T('adminPanel.customFieldsPage.noFieldsForFacet', 'No custom fields found...'));
        const addLabel = escapeHtml(T('adminPanel.customFieldsPage.addCustomField', 'Add Custom Field'));
        tableBody.innerHTML = `
            <tr class="empty-state">
                <td colspan="8">
                    <div class="empty-message">
                        <i class="fas fa-table"></i>
                        <p>${noFields}</p>
                        ${customFieldsState.editMode ? '<button class="btn btn-outline empty-add-btn" type="button" id="emptyStateAddFieldBtn"><i class="fas fa-plus"></i> ' + addLabel + '</button>' : ''}
                    </div>
                </td>
            </tr>
        `;
        if (customFieldsState.editMode) {
            document.getElementById('emptyStateAddFieldBtn')?.addEventListener('click', () => openCustomFieldModal());
        }
        return;
    }

    const titleEdit = escapeHtml(T('adminPanel.customFieldsPage.titleEditField', 'Edit field'));
    const titleDelete = escapeHtml(T('adminPanel.customFieldsPage.titleDeleteField', 'Delete field'));
    const yesLabel = escapeHtml(T('adminPanel.customFieldsPage.yes', 'Yes'));
    const noLabel = escapeHtml(T('adminPanel.customFieldsPage.no', 'No'));

    const rowsHtml = fields.map(field => {
        const actionsHtml = customFieldsState.editMode ? `
            <div class="table-actions">
                <button type="button" class="table-icon-btn edit-field" data-action="edit" data-id="${field.id}" title="${titleEdit}">
                    <i class="fas fa-pen"></i>
                </button>
                <button type="button" class="table-icon-btn delete-field" data-action="delete" data-id="${field.id}" title="${titleDelete}">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        ` : '<span class="action-placeholder">—</span>';

        return `
            <tr data-field-id="${field.id}">
                <td>${escapeHtml(field.displayName)}</td>
                <td>${escapeHtml(field.technicalName)}</td>
                <td>${escapeHtml(formatFieldType(field.type))}</td>
                <td>${field.defaultValue ? escapeHtml(field.defaultValue) : '<span class="muted">-</span>'}</td>
                <td>${field.mandatory ? yesLabel : noLabel}</td>
                <td>${formatTimestamp(field.lastUpdatedAt)}</td>
                <td>${escapeHtml(field.lastUpdatedBy || '-')}</td>
                <td class="action-column">${actionsHtml}</td>
            </tr>
        `;
    }).join('');

    tableBody.innerHTML = rowsHtml;
}

function handleTableClick(event) {
    if (!customFieldsState.editMode) return;
    const button = event.target.closest('.table-icon-btn');
    if (!button) return;
    const fieldId = button.dataset.id;
    const action = button.dataset.action;
    if (action === 'edit') {
        editCustomField(fieldId);
    } else if (action === 'delete') {
        deleteCustomField(fieldId);
    }
}

function toggleEditMode(enable) {
    if (!customFieldsState.selectedFacetId) return;
    customFieldsState.editMode = Boolean(enable);
    const card = document.querySelector('.custom-fields-card');
    const editBtn = document.getElementById('customFieldsEditBtn');
    const closeBtn = document.getElementById('customFieldsCloseBtn');
    const addBtn = document.getElementById('customFieldsAddBtn');
    card?.classList.toggle('editing', customFieldsState.editMode);
    if (editBtn) editBtn.style.display = customFieldsState.editMode ? 'none' : '';
    if (closeBtn) closeBtn.style.display = customFieldsState.editMode ? '' : 'none';
    if (addBtn) addBtn.style.display = customFieldsState.editMode ? '' : 'none';
    refreshCustomFieldsForFacet(customFieldsState.selectedFacetId);
}

function updateEditControls() {
    const editBtn = document.getElementById('customFieldsEditBtn');
    const hasFacet = Boolean(customFieldsState.selectedFacetId);
    if (editBtn) editBtn.disabled = !hasFacet;
}

function updateFacetSummary() {
    const summary = document.getElementById('customFieldsFacetSummary');
    if (!summary) return;
    const T = cfT;
    if (!customFieldsState.selectedFacetId) {
        summary.textContent = T('adminPanel.customFieldsPage.selectFacetSummary', 'Select a facet...');
        return;
    }
    const facetSelect = document.getElementById('customFieldsFacetSelect');
    const facetName = facetSelect?.options[facetSelect.selectedIndex]?.text || 'Facet';
    summary.textContent = facetName + T('adminPanel.customFieldsPage.facetSuffix', ' - Custom Fields');
}

function setTableLoadingState() {
    const tableBody = document.getElementById('customFieldsTableBody');
    if (!tableBody) return;
    const loading = escapeHtml(cfT('adminPanel.customFieldsPage.loadingCustomFields', 'Loading custom fields...'));
    tableBody.innerHTML = `
        <tr class="loading-state">
            <td colspan="8">
                <div class="loading-message">
                    <i class="fas fa-spinner fa-spin"></i>
                    ${loading}
                </div>
            </td>
        </tr>
    `;
}

async function openCustomFieldModal(field = null) {
    const modal = document.getElementById('customFieldModal');
    const title = document.getElementById('customFieldModalTitle');
    const form = document.getElementById('customFieldForm');
    if (!modal || !form || !title) return;
    const T = cfT;

    // If editing an existing field, check for blocking CR BEFORE opening
    customFieldsState.facetBlockingCr = false;
    let hasBlockingCR = false;
    if (field && customFieldsState.selectedFacetId) {
        try {
            const crResp = await fetch(`/api/custom-fields/has-blocking-cr?facetId=${encodeURIComponent(customFieldsState.selectedFacetId)}`);
            if (crResp.ok) {
                const crData = await crResp.json();
                if (crData.success && crData.hasBlockingCR) {
                    hasBlockingCR = true;
                    customFieldsState.facetBlockingCr = true;
                }
            }
        } catch (e) {
            console.warn('Could not check blocking CR status:', e);
        }
    }

    clearFieldErrors(form);
    // Reset CR-locked state and type lock before (re)populating
    clearCustomFieldCrRestrictions();
    setTypeSelectLocked(false);
    setModalCRLockedState(false);

    if (field) {
        customFieldsState.activeFieldId = field.id;
        title.textContent = T('adminPanel.customFieldsPage.modalEditTitle', 'Edit Custom Field');
        fillForm(field);
        // Type cannot be changed after creation — lock it
        setTypeSelectLocked(true);
    } else {
        customFieldsState.activeFieldId = null;
        title.textContent = T('adminPanel.customFieldsPage.modalAddTitle', 'Add Custom Field');
        form.reset();
        const tagsContainer = document.getElementById('cfDefaultValueTags');
        if (tagsContainer) tagsContainer.innerHTML = '';
        const hiddenInput = document.getElementById('cfDefaultValueHidden');
        if (hiddenInput) hiddenInput.value = '';
    }
    adjustModalForType(document.getElementById('cfType')?.value || '');
    const mandatoryCheckbox = document.getElementById('cfMandatory');
    if (mandatoryCheckbox && !mandatoryCheckbox.hasAttribute('data-listener-added')) {
        mandatoryCheckbox.addEventListener('change', () => handleMandatoryChange());
        mandatoryCheckbox.setAttribute('data-listener-added', 'true');
    }
    handleMandatoryChange();
    modal.classList.add('show');
    modal.setAttribute('aria-hidden', 'false');
    setTimeout(() => document.getElementById('cfDisplayName')?.focus(), 20);

    // Apply CR lock AFTER fillForm's setTimeout(10) has completed rebuilding the DOM
    if (hasBlockingCR) {
        setTimeout(() => setModalCRLockedState(true), 50);
    }
}

/**
 * Lock or unlock restricted fields in the custom field modal when a blocking CR exists.
 * Only name (cfDisplayName), description (cfDescription), and mandatory (cfMandatory) remain editable.
 * All other inputs/selects/textareas in the form are disabled.
 */
function setModalCRLockedState(locked) {
    // IDs that are ALLOWED to remain editable even when locked
    const allowedIds = new Set(['cfDisplayName', 'cfDescription', 'cfMandatory']);

    const form = document.getElementById('customFieldForm');
    if (!form) return;

    // Disable/enable every input, select, textarea in the form except allowed ones
    form.querySelectorAll('input, select, textarea').forEach(el => {
        // Skip the allowed fields
        if (allowedIds.has(el.id)) return;
        // Skip submit/button inputs (Close/Save buttons)
        if (el.type === 'submit' || el.type === 'button') return;

        el.disabled = locked;
        if (locked) {
            el.style.opacity = '0.5';
            el.style.cursor = 'not-allowed';
        } else {
            el.style.opacity = '';
            el.style.cursor = '';
        }
    });

    // Disable/enable multiselect tag remove buttons
    document.querySelectorAll('#cfDefaultValueTags .tag-remove').forEach(btn => {
        btn.disabled = locked;
        btn.style.pointerEvents = locked ? 'none' : '';
    });

    // Show/hide a warning banner
    let banner = document.getElementById('cfCRLockedBanner');
    if (locked) {
        if (!banner) {
            banner = document.createElement('div');
            banner.id = 'cfCRLockedBanner';
            banner.style.cssText = 'background:#fff3cd;color:#856404;border:1px solid #ffc107;border-radius:6px;padding:10px 14px;margin:0 0 12px 0;font-size:0.92em;display:flex;align-items:center;gap:8px;';
            banner.innerHTML = '<i class="fas fa-exclamation-triangle"></i> <span>A change request is running or pending start for this facet. Only <b>Name</b>, <b>Description</b>, and <b>Mandatory</b> can be edited.</span>';
            const modalBody = document.querySelector('#customFieldForm .modal-body');
            if (modalBody) {
                modalBody.insertBefore(banner, modalBody.firstChild);
            }
        }
        banner.style.display = 'flex';
    } else {
        if (banner) banner.style.display = 'none';
    }
}

function clearCustomFieldCrRestrictions() {
    const banner = document.getElementById('cfCRLockedBanner');
    if (banner) banner.remove();
}

function closeCustomFieldModal() {
    const modal = document.getElementById('customFieldModal');
    if (!modal) return;
    customFieldsState.activeFieldId = null;
    customFieldsState.facetBlockingCr = false;
    clearCustomFieldCrRestrictions();
    setTypeSelectLocked(false);
    setModalCRLockedState(false);
    const form = document.getElementById('customFieldForm');
    if (form) form.reset();
    const tagsContainer = document.getElementById('cfDefaultValueTags');
    if (tagsContainer) tagsContainer.innerHTML = '';
    const hiddenInput = document.getElementById('cfDefaultValueHidden');
    if (hiddenInput) hiddenInput.value = '';
    modal.classList.remove('show');
    modal.setAttribute('aria-hidden', 'true');
}

/** Field type is fixed after creation; disable the control when editing. */
function setTypeSelectLocked(locked) {
    const typeSelect = document.getElementById('cfType');
    if (!typeSelect) return;
    const T = cfT;
    if (locked) {
        typeSelect.disabled = true;
        typeSelect.removeAttribute('required');
        typeSelect.setAttribute('title', T('adminPanel.customFieldsPage.typeLockedHint', 'Field type cannot be changed after creation'));
        typeSelect.setAttribute('aria-readonly', 'true');
    } else {
        typeSelect.disabled = false;
        typeSelect.setAttribute('required', 'required');
        typeSelect.removeAttribute('title');
        typeSelect.removeAttribute('aria-readonly');
    }
}

function syncDropdownValuesFromEditor() {
    const list = document.getElementById('cfDropdownChoicesList');
    const ta = document.getElementById('cfDropdownValues');
    if (!list || !ta) return;
    const inputs = list.querySelectorAll('.cf-dropdown-choice-input');
    const lines = Array.from(inputs).map(inp => String(inp.value ?? ''));
    ta.value = lines.join('\n');
}

function onDropdownChoicesChanged() {
    const type = document.getElementById('cfType')?.value || '';
    if (type === 'dropdown') {
        updateDropdownDefaultValue();
    } else if (type === 'multiselect') {
        updateMultiselectDefaultValue();
    }
    refreshDropdownChoiceRowLocks();
}

function appendDropdownChoiceRow(list, initialValue) {
    const row = document.createElement('div');
    row.className = 'cf-dropdown-choice-row';
    row.setAttribute('role', 'listitem');
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'cf-dropdown-choice-input text-input';
    input.setAttribute('autocomplete', 'off');
    input.value = initialValue != null ? String(initialValue) : '';
    const del = document.createElement('button');
    del.type = 'button';
    del.className = 'btn btn-secondary btn-sm cf-dropdown-choice-delete';
    del.innerHTML = '<i class="fas fa-trash-alt" aria-hidden="true"></i>';
    del.setAttribute('aria-label', cfT('adminPanel.customFieldsPage.ariaDeleteChoice', 'Remove choice'));
    row.appendChild(input);
    row.appendChild(del);
    list.appendChild(row);
}

function renderDropdownChoicesEditor(values) {
    const list = document.getElementById('cfDropdownChoicesList');
    if (!list) return;
    const arr = Array.isArray(values) ? values : [];
    const trimmed = arr.map(v => (v == null ? '' : String(v)).trim()).filter(v => v !== '');
    const finalRows = trimmed.length > 0 ? trimmed : [''];
    list.innerHTML = '';
    finalRows.forEach(val => appendDropdownChoiceRow(list, val));
    syncDropdownValuesFromEditor();
    onDropdownChoicesChanged();
}

function refreshDropdownChoiceRowLocks() {
    const type = document.getElementById('cfType')?.value || '';
    if (type !== 'dropdown' && type !== 'multiselect') return;
    const list = document.getElementById('cfDropdownChoicesList');
    if (!list) return;
    if (customFieldsState.facetBlockingCr && customFieldsState.activeFieldId) {
        list.querySelectorAll('.cf-dropdown-choice-row').forEach(row => {
            const input = row.querySelector('.cf-dropdown-choice-input');
            if (input) input.readOnly = true;
            const delBtn = row.querySelector('.cf-dropdown-choice-delete');
            if (delBtn) {
                delBtn.disabled = true;
                delBtn.setAttribute('aria-disabled', 'true');
            }
            row.classList.add('is-locked');
        });
        return;
    }
    const lockedSet = new Set();
    if (type === 'dropdown') {
        const sel = document.getElementById('cfDefaultValue');
        const v = sel && sel.value ? String(sel.value).trim() : '';
        if (v) lockedSet.add(v);
    } else {
        getSelectedDefaultValues().forEach(v => {
            const t = String(v).trim();
            if (t) lockedSet.add(t);
        });
    }
    list.querySelectorAll('.cf-dropdown-choice-row').forEach(row => {
        const input = row.querySelector('.cf-dropdown-choice-input');
        if (!input) return;
        const val = String(input.value || '').trim();
        const locked = val !== '' && lockedSet.has(val);
        row.classList.toggle('is-locked', locked);
        input.readOnly = locked;
        const delBtn = row.querySelector('.cf-dropdown-choice-delete');
        if (delBtn) {
            delBtn.disabled = locked;
            delBtn.setAttribute('aria-disabled', locked ? 'true' : 'false');
        }
    });
}

function ensureDropdownChoicesEditorWired() {
    const group = document.getElementById('cfDropdownValuesGroup');
    if (!group || group.dataset.choiceEditorWired === '1') return;
    group.dataset.choiceEditorWired = '1';
    group.addEventListener('click', (e) => {
        if (e.target.closest('#cfAddDropdownChoice')) {
            e.preventDefault();
            const list = document.getElementById('cfDropdownChoicesList');
            if (list) {
                appendDropdownChoiceRow(list, '');
                syncDropdownValuesFromEditor();
                onDropdownChoicesChanged();
            }
            return;
        }
        const del = e.target.closest('.cf-dropdown-choice-delete');
        if (del && !del.disabled) {
            const row = del.closest('.cf-dropdown-choice-row');
            if (row && !row.classList.contains('is-locked')) {
                row.remove();
                const list = document.getElementById('cfDropdownChoicesList');
                if (list && !list.querySelector('.cf-dropdown-choice-row')) {
                    appendDropdownChoiceRow(list, '');
                }
                syncDropdownValuesFromEditor();
                onDropdownChoicesChanged();
            }
        }
    });
    group.addEventListener('input', (e) => {
        if (e.target.classList.contains('cf-dropdown-choice-input')) {
            syncDropdownValuesFromEditor();
            onDropdownChoicesChanged();
        }
    });
}

function fillForm(field) {
    document.getElementById('cfDisplayName').value = field.displayName || '';
    document.getElementById('cfDescription').value = field.description || '';
    document.getElementById('cfType').value = field.type || '';
    document.getElementById('cfMandatory').checked = Boolean(field.mandatory);
    document.getElementById('cfPlaceholder').value = field.placeholder || '';
    const ft = field.type || '';
    if (ft === 'dropdown' || ft === 'multiselect') {
        const lines = Array.isArray(field.enumValues)
            ? field.enumValues.map(e => e.enumValue).filter(v => v != null && String(v).trim() !== '')
            : [];
        const ta = document.getElementById('cfDropdownValues');
        if (ta) ta.value = lines.join('\n');
    }
    adjustModalForType(ft);
    setTimeout(() => {
        if (field.type === 'dropdown') {
            updateDropdownDefaultValue();
            const defSel = document.getElementById('cfDefaultValue');
            if (defSel) defSel.value = field.defaultValue || '';
            refreshDropdownChoiceRowLocks();
        } else if (field.type === 'multiselect') {
            const tagsContainer = document.getElementById('cfDefaultValueTags');
            if (tagsContainer) tagsContainer.innerHTML = '';
            if (field.defaultValue) {
                const defaultValues = field.defaultValue.split(',').map(v => v.trim()).filter(v => v);
                defaultValues.forEach(value => addDefaultValueTag(value));
            }
            updateMultiselectDefaultValue();
            updateDefaultValueHidden();
            refreshDropdownChoiceRowLocks();
        } else {
            const defaultInput = document.getElementById('cfDefaultValue');
            if (defaultInput) defaultInput.value = field.defaultValue || '';
        }
        handleMandatoryChange();
    }, 10);
}

function adjustModalForType(type) {
    const T = cfT;
    const h = escapeHtml;
    const dropdownGroup = document.getElementById('cfDropdownValuesGroup');
    const defaultValueContainer = document.getElementById('cfDefaultValueContainer');
    const additionalSettingsSection = document.getElementById('cfAdditionalSettingsSection');
    if (!dropdownGroup || !defaultValueContainer) return;

    dropdownGroup.style.display = (type === 'dropdown' || type === 'multiselect') ? '' : 'none';

    if (additionalSettingsSection) {
        const shouldHide = ['checkbox', 'date', 'time'].includes(type);
        additionalSettingsSection.style.display = shouldHide ? 'none' : '';
        if (shouldHide) {
            const placeholderInput = document.getElementById('cfPlaceholder');
            if (placeholderInput) placeholderInput.value = '';
        }
    }

    let html = '';
    if (type === 'checkbox') {
        html = `
            <div class="checkbox-options">
                <label class="radio-option">
                    <input type="radio" name="defaultValue" value="checked">
                    <span class="radio-label">${h(T('adminPanel.customFieldsPage.checked', 'Checked'))}</span>
                </label>
                <label class="radio-option">
                    <input type="radio" name="defaultValue" value="unchecked">
                    <span class="radio-label">${h(T('adminPanel.customFieldsPage.unchecked', 'Unchecked'))}</span>
                </label>
            </div>
        `;
    } else if (type === 'date') {
        html = `<input type="date" id="cfDefaultValue" name="defaultValue" class="date-input">`;
    } else if (type === 'time') {
        html = `<input type="time" id="cfDefaultValue" name="defaultValue" class="time-input">`;
    } else if (type === 'number') {
        html = `<input type="number" id="cfDefaultValue" name="defaultValue" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDefaultValue', ''))}" class="text-input" step="1">`;
    } else if (type === 'decimal') {
        html = `<input type="number" id="cfDefaultValue" name="defaultValue" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDefaultValue', ''))}" class="text-input" step="0.01">`;
    } else if (type === 'percentage') {
        html = `
            <div class="cf-percentage-wrapper" style="position:relative;display:flex;align-items:center;">
                <input type="number" id="cfDefaultValue" name="defaultValue" class="text-input" step="1" placeholder="${h(T('adminPanel.customFieldsPage.placeholderPercentage', ''))}" style="padding-right:2.2rem">
                <span class="cf-percentage-icon" style="position:absolute;right:0.7rem;top:50%;transform:translateY(-50%);color:#248567;pointer-events:none;"><i class="fas fa-percent"></i></span>
            </div>
        `;
    } else if (type === 'dropdown') {
        html = `<select id="cfDefaultValue" name="defaultValue" class="default-value-select"><option value="">${h(T('adminPanel.customFieldsPage.selectDefaultValue', ''))}</option></select>`;
    } else if (type === 'multiselect') {
        html = `
            <div class="multiselect-default-container">
                <div class="multiselect-tags" id="cfDefaultValueTags"></div>
                <select id="cfDefaultValue" name="defaultValue" class="default-value-multiselect">
                    <option value="">${h(T('adminPanel.customFieldsPage.selectDefaultValues', ''))}</option>
                </select>
                <input type="hidden" id="cfDefaultValueHidden" name="defaultValueHidden">
            </div>
        `;
    } else {
        html = `<input type="text" id="cfDefaultValue" name="defaultValue" placeholder="${h(T('adminPanel.customFieldsPage.placeholderDefaultValue', ''))}" class="text-input">`;
    }
    defaultValueContainer.innerHTML = html;

    if (type === 'dropdown') {
        const defSel = document.getElementById('cfDefaultValue');
        if (defSel) {
            defSel.addEventListener('change', () => refreshDropdownChoiceRowLocks());
        }
    }

    const placeholderInput = document.getElementById('cfPlaceholder');
    if (placeholderInput && additionalSettingsSection && additionalSettingsSection.style.display !== 'none') {
        const placeholders = {
            'text': T('adminPanel.customFieldsPage.placeholderTextHere', ''),
            'number': T('adminPanel.customFieldsPage.placeholderNumber', ''),
            'decimal': T('adminPanel.customFieldsPage.placeholderDecimal', ''),
            'percentage': T('adminPanel.customFieldsPage.placeholderPercentageRange', ''),
            'dropdown': T('adminPanel.customFieldsPage.placeholderSelectOption', ''),
            'multiselect': T('adminPanel.customFieldsPage.placeholderSelectMultiple', '')
        };
        if (placeholders[type]) {
            placeholderInput.placeholder = placeholders[type];
        } else {
            placeholderInput.placeholder = T('adminPanel.customFieldsPage.placeholderInstruction', '');
        }
    }

    if (type === 'dropdown') {
        ensureDropdownChoicesEditorWired();
        const ta = document.getElementById('cfDropdownValues');
        const existing = ta && ta.value
            ? ta.value.split('\n').map(v => v.trim()).filter(v => v)
            : [];
        renderDropdownChoicesEditor(existing.length > 0 ? existing : ['']);
        updateDropdownDefaultValue();
    } else if (type === 'multiselect') {
        ensureDropdownChoicesEditorWired();
        const ta = document.getElementById('cfDropdownValues');
        const existing = ta && ta.value
            ? ta.value.split('\n').map(v => v.trim()).filter(v => v)
            : [];
        renderDropdownChoicesEditor(existing.length > 0 ? existing : ['']);
        updateMultiselectDefaultValue();
        setupMultiselectDefaultValue();
    }
    handleMandatoryChange();
}

function updateDropdownDefaultValue() {
    const defaultValueSelect = document.getElementById('cfDefaultValue');
    const dropdownValuesTextarea = document.getElementById('cfDropdownValues');
    if (!defaultValueSelect || !dropdownValuesTextarea) return;
    const T = cfT;
    const currentValue = defaultValueSelect.value;
    defaultValueSelect.innerHTML = '<option value="">' + escapeHtml(T('adminPanel.customFieldsPage.selectDefaultValue', 'Select default value')) + '</option>';
    const values = dropdownValuesTextarea.value.split('\n').map(v => v.trim()).filter(v => v);
    values.forEach(value => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        defaultValueSelect.appendChild(option);
    });
    if (currentValue && values.includes(currentValue)) {
        defaultValueSelect.value = currentValue;
    }
}

function updateMultiselectDefaultValue() {
    const defaultValueSelect = document.getElementById('cfDefaultValue');
    const dropdownValuesTextarea = document.getElementById('cfDropdownValues');
    if (!defaultValueSelect || !dropdownValuesTextarea) return;
    const T = cfT;
    const selectedValues = getSelectedDefaultValues();
    defaultValueSelect.innerHTML = '<option value="">' + escapeHtml(T('adminPanel.customFieldsPage.selectDefaultValues', 'Select default values')) + '</option>';
    const values = dropdownValuesTextarea.value.split('\n').map(v => v.trim()).filter(v => v);
    values.forEach(value => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = value;
        if (!selectedValues.includes(value)) {
            defaultValueSelect.appendChild(option);
        }
    });
}

function setupMultiselectDefaultValue() {
    const defaultValueSelect = document.getElementById('cfDefaultValue');
    const tagsContainer = document.getElementById('cfDefaultValueTags');
    if (!defaultValueSelect || !tagsContainer) return;

    defaultValueSelect.addEventListener('change', function() {
        if (this.value && this.value !== '') {
            addDefaultValueTag(this.value);
            this.value = '';
        }
    });
    defaultValueSelect.addEventListener('keydown', function(e) {
        if (e.key === 'Enter' && this.value && this.value !== '') {
            e.preventDefault();
            addDefaultValueTag(this.value);
            this.value = '';
        }
    });
}

function addDefaultValueTag(value) {
    const tagsContainer = document.getElementById('cfDefaultValueTags');
    const defaultValueSelect = document.getElementById('cfDefaultValue');
    if (!tagsContainer || !defaultValueSelect) return;
    const existingTags = Array.from(tagsContainer.querySelectorAll('.default-value-tag'));
    if (existingTags.some(tag => tag.dataset.value === value)) return;

    const tag = document.createElement('div');
    tag.className = 'default-value-tag';
    tag.dataset.value = value;
    const ariaRemove = escapeHtml(cfT('adminPanel.customFieldsPage.ariaRemove', 'Remove'));
    tag.innerHTML = `
        <span>${escapeHtml(value)}</span>
        <button type="button" class="tag-remove" aria-label="${ariaRemove}">
            <i class="fas fa-times"></i>
        </button>
    `;
    tag.querySelector('.tag-remove').addEventListener('click', () => {
        removeDefaultValueTag(value);
    });
    tagsContainer.appendChild(tag);
    updateMultiselectDefaultValue();
    updateDefaultValueHidden();
    refreshDropdownChoiceRowLocks();
}

function removeDefaultValueTag(value) {
    const tagsContainer = document.getElementById('cfDefaultValueTags');
    if (!tagsContainer) return;
    const tags = tagsContainer.querySelectorAll('.default-value-tag');
    tags.forEach(tag => {
        if (tag.dataset.value === value) {
            tag.remove();
            updateMultiselectDefaultValue();
            updateDefaultValueHidden();
            refreshDropdownChoiceRowLocks();
        }
    });
}

function getSelectedDefaultValues() {
    const tagsContainer = document.getElementById('cfDefaultValueTags');
    if (!tagsContainer) return [];
    return Array.from(tagsContainer.querySelectorAll('.default-value-tag'))
        .map(tag => tag.dataset.value);
}

function updateDefaultValueHidden() {
    const hiddenInput = document.getElementById('cfDefaultValueHidden');
    const selectedValues = getSelectedDefaultValues();
    if (hiddenInput) {
        hiddenInput.value = selectedValues.join(',');
    }
}

function handleMandatoryChange() {
    const mandatoryCheckbox = document.getElementById('cfMandatory');
    const defaultValueLabel = document.querySelector('#cfDefaultValueGroup label');
    const type = document.getElementById('cfType')?.value || '';
    const isMandatory = mandatoryCheckbox?.checked || false;
    if (!defaultValueLabel) return;

    const defaultValueText = cfT('adminPanel.common.defaultValue', 'Default Value');
    if (isMandatory) {
        if (!defaultValueLabel.textContent.includes('*')) {
            defaultValueLabel.innerHTML = `${defaultValueText} <span class="required">*</span>`;
        }
        if (type === 'checkbox') {
            document.querySelectorAll('input[name="defaultValue"][type="radio"]').forEach(radio => {
                radio.setAttribute('required', 'required');
            });
        } else if (type === 'multiselect') {
            const hiddenInput = document.getElementById('cfDefaultValueHidden');
            if (hiddenInput) hiddenInput.setAttribute('required', 'required');
        } else {
            const defaultValueInput = document.getElementById('cfDefaultValue');
            if (defaultValueInput) defaultValueInput.setAttribute('required', 'required');
        }
    } else {
        defaultValueLabel.innerHTML = defaultValueText;
        if (type === 'checkbox') {
            document.querySelectorAll('input[name="defaultValue"][type="radio"]').forEach(radio => {
                radio.removeAttribute('required');
            });
        } else if (type === 'multiselect') {
            const hiddenInput = document.getElementById('cfDefaultValueHidden');
            if (hiddenInput) hiddenInput.removeAttribute('required');
        } else {
            const defaultValueInput = document.getElementById('cfDefaultValue');
            if (defaultValueInput) defaultValueInput.removeAttribute('required');
        }
    }
}

async function handleCustomFieldFormSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const T = cfT;
    clearFieldErrors(form);

    if (!customFieldsState.selectedFacetId) {
        notify(T('adminPanel.customFieldsPage.notifySelectFacetFirst', 'Select a facet...'), 'warning');
        return;
    }

    const fieldData = extractFormData(form);
    const errors = validateFormData(fieldData);
    if (errors.length) {
        errors.forEach(e => setFieldError(form, e.field, e.message));
        notify(T('adminPanel.customFieldsPage.notifyResolveIssues', 'Please resolve...'), 'error');
        return;
    }

    const success = await saveCustomField(fieldData);
    if (success) {
        closeCustomFieldModal();
        notify(T('adminPanel.customFieldsPage.notifySaved', 'Custom field saved...'), 'success');
        refreshCustomFieldsForFacet(customFieldsState.selectedFacetId);
    }
}

function extractFormData(form) {
    const formData = new FormData(form);
    const typeSelect = document.getElementById('cfType');
    const typeFromForm = (formData.get('type') != null ? String(formData.get('type')) : '').trim();
    const type = (typeFromForm || (typeSelect && typeSelect.value ? String(typeSelect.value) : '')).trim();
    let defaultValue = '';
    if (type === 'checkbox') {
        const checked = form.querySelector('input[name="defaultValue"]:checked');
        defaultValue = checked ? checked.value : '';
    } else if (type === 'multiselect') {
        const hiddenInput = document.getElementById('cfDefaultValueHidden');
        defaultValue = hiddenInput ? (hiddenInput.value || '').trim() : '';
    } else {
        defaultValue = (formData.get('defaultValue') || '').trim();
    }
    return {
        displayName: (formData.get('displayName') || '').trim(),
        description: (formData.get('description') || '').trim(),
        type: type,
        mandatory: formData.get('mandatory') === 'on',
        defaultValue: defaultValue,
        placeholder: (formData.get('placeholder') || '').trim(),
        dropdownValues: (formData.get('dropdownValues') || '').trim()
    };
}

function validateFormData(data) {
    const T = cfT;
    const errors = [];
    if (!data.displayName) {
        errors.push({ field: 'displayName', message: T('adminPanel.customFieldsPage.errDisplayNameRequired', '') });
    }
    if (!data.type) {
        errors.push({ field: 'type', message: T('adminPanel.customFieldsPage.errTypeRequired', '') });
    }
    if ((data.type === 'dropdown' || data.type === 'multiselect') && !data.dropdownValues) {
        errors.push({ field: 'dropdownValues', message: T('adminPanel.customFieldsPage.errDropdownValues', '') });
    }
    if (data.mandatory && !data.defaultValue) {
        errors.push({ field: 'defaultValue', message: T('adminPanel.customFieldsPage.errDefaultMandatory', '') });
    }
    if ((data.type === 'number' || data.type === 'decimal') && data.defaultValue) {
        if (isNaN(parseFloat(data.defaultValue))) {
            errors.push({ field: 'defaultValue', message: T('adminPanel.customFieldsPage.errDefaultNumber', '') });
        }
    }
    return errors;
}

async function saveCustomField(fieldData) {
    const T = cfT;
    try {
        const url = `/api/custom-fields/metadata`;
        const method = customFieldsState.activeFieldId ? 'PUT' : 'POST';
        const requestData = {
            facetId: customFieldsState.selectedFacetId,
            displayName: fieldData.displayName,
            description: fieldData.description,
            type: fieldData.type,
            mandatory: fieldData.mandatory,
            defaultValue: fieldData.defaultValue,
            placeholder: fieldData.placeholder,
            dropdownValues: (fieldData.type === 'dropdown' || fieldData.type === 'multiselect') ? fieldData.dropdownValues : ''
        };
        if (customFieldsState.activeFieldId) {
            requestData.fieldId = parseInt(customFieldsState.activeFieldId);
        }
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestData)
        });
        const result = await response.json();
        if (result.success) {
            return true;
        } else {
            notify(T('adminPanel.customFieldsPage.errSave', '') + (result.error || T('adminPanel.customFieldsPage.unknownError', '')), 'error');
            return false;
        }
    } catch (error) {
        console.error('Error saving custom field:', error);
        notify(T('adminPanel.customFieldsPage.errSave', '') + error.message, 'error');
        return false;
    }
}

async function editCustomField(fieldId) {
    const T = cfT;
    try {
        const response = await fetch(`/api/custom-fields/metadata?facetId=${encodeURIComponent(customFieldsState.selectedFacetId)}`);
        if (response.ok) {
            const data = await response.json();
            const field = Array.isArray(data.data) ? data.data.find(f => f.id == fieldId) : null;
            if (field) await openCustomFieldModal(field);
        }
    } catch (error) {
        console.error('Error loading field:', error);
        notify(T('adminPanel.customFieldsPage.errLoadField', ''), 'error');
    }
}

async function deleteCustomField(fieldId) {
    const T = cfT;
    if (!confirm(T('adminPanel.customFieldsPage.confirmDelete', ''))) return;
    try {
        const response = await fetch(`/api/custom-fields/metadata/${fieldId}`, { method: 'DELETE' });
        const result = await response.json();
        if (result.success) {
            notify(T('adminPanel.customFieldsPage.notifyDeleted', ''), 'success');
            refreshCustomFieldsForFacet(customFieldsState.selectedFacetId);
        } else {
            notify(T('adminPanel.customFieldsPage.errDelete', '') + (result.error || T('adminPanel.customFieldsPage.unknownError', '')), 'error');
        }
    } catch (error) {
        console.error('Error deleting custom field:', error);
        notify(T('adminPanel.customFieldsPage.errDelete', '') + error.message, 'error');
    }
}

function setFieldError(form, fieldName, message) {
    const errorElement = form.querySelector(`[data-error-for="${fieldName}"]`);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

function clearFieldErrors(form) {
    form.querySelectorAll('.field-error').forEach(el => {
        el.textContent = '';
        el.style.display = 'none';
    });
}

function formatFieldType(type) {
    const match = getCustomFieldTypes().find(t => t.value === type);
    return match ? match.label : type;
}

function formatTimestamp(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (isNaN(date.getTime())) return '-';
    const day = String(date.getDate()).padStart(2, '0');
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const month = months[date.getMonth()];
    const year = date.getFullYear();
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');
    return `${day}-${month}-${year} ${hours}:${minutes}:${seconds}`;
}

function escapeHtml(value) {
    return String(value ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function notify(message, type) {
    if (typeof showNotification === 'function') {
        showNotification(message, type);
    } else {
        console.log(`[${type || 'info'}] ${message}`);
    }
}
