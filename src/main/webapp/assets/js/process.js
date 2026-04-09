// Process Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');
    const parentPicker = document.getElementById('parentPicker');

    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            savePage(true);
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            savePage(true);
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            closePage();
        });
    }

    // Initialize parent picker dropdown
    initParentPicker();

    // Initialize form dropdowns
    initProcessFormDropdowns();
    
    // Initialize custom fields
    initializeCustomFields();

    // Wire up advanced rich text editors for all description textareas
    const editorButtons = document.querySelectorAll('.editor-button');
    const textareaIds = ['description', 'inputDescription', 'outputDescription'];
    editorButtons.forEach(function(btn, index) {
        if (textareaIds[index]) {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                if (typeof window.toggleAdvancedRichTextEditor === 'function') {
                    window.toggleAdvancedRichTextEditor(textareaIds[index], btn);
                }
            });
        }
    });
});

// Initialize custom fields (create mode)
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Process',
                containerId: 'customFieldsContainer',
                mode: 'create',
                objectId: null
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } else {
            console.warn('CustomFields not available');
        }
    } catch (error) {
        console.error('Error initializing custom fields:', error);
    }
    
    // Initialize segment field
    try {
        if (window.SegmentField) {
            window.segmentField = await SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                defaultValue: 1,
                sectionTitle: 'SEGMENTATION',
                objectType: 'Process',
                fieldId: 'processSegment',
                errorId: 'processSegmentError',
                onChange: async () => {
                    const parentNameInput = document.getElementById('parentName');
                    const parentIdInput = document.getElementById('parentId');
                    const previousParentId = parentIdInput?.value ? parseInt(parentIdInput.value, 10) : null;
                    if (typeof loadParentProcesses === 'function') {
                        await loadParentProcesses();
                    }
                    if (previousParentId && Number.isInteger(previousParentId)) {
                        const stillAllowed = Array.isArray(parentProcesses) &&
                            parentProcesses.some(p => parseInt(p.id || p.ID, 10) === previousParentId);
                        if (!stillAllowed) {
                            if (typeof showError === 'function') {
                                showError('This parent is not valid for the selected segment. Please remove the parent first.');
                            }
                            return false;
                        }
                    }
                }
            });
            console.log('Segment field initialized');
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }
}


async function savePage(closeAfterSave) {
    try {
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => { 
            if (btn) { 
                btn.disabled = true; 
                btn.textContent = I18n.t('createPage.message.saving'); 
            } 
        });
        
        // Sync advanced rich text editor content to textareas before saving
        if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
            window.syncAdvancedRichTextToTextarea('description');
            window.syncAdvancedRichTextToTextarea('inputDescription');
            window.syncAdvancedRichTextToTextarea('outputDescription');
        }

        const payload = collectProcessFormData();
        // Process payload collected
        
        // Validate required fields
        if (!validateProcessForm(payload)) {
            buttons.forEach(btn => { 
                if (btn) { 
                    btn.disabled = false; 
                    btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); 
                } 
            });
            return;
        }

        // Uniqueness checks: Name and Ref_Number
        try {
            if (typeof window.BUDG_API_SERVICE?.getProcessList === 'function') {
                const list = await window.BUDG_API_SERVICE.getProcessList();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                const refVal = String(payload.refnumber || '').trim().toLowerCase();
                
                if (refVal) {
                    const refClash = rows.some(r => String(r.refnumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                    if (refClash) {
                        const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Processes'}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        document.getElementById('ref')?.focus();
                        buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; } });
                        return;
                    }
                }
            }
        } catch (_) { /* fall back to server-side validation */ }

        // Call real API
        try {
            
            const svc = window.BUDG_API_SERVICE;
            if (!svc) {
                throw new Error('API service not available');
            }

            const response = await svc.post('/process', payload);
            
            // Response received
            // Response type checked
            // Response success checked
            // Response message checked
            // Response error checked
            
            if (response && response.success) {
                
                // Process saved successfully
                showSuccess(I18n.t('createPage.message.processSaved'));
                
                // Get the ID from the response
                const processId = response.data?.id || response.id;
                // Saved process ID stored in variable
                
                // Save custom fields if available
                if (window.customFieldsContext && window.customFieldsContext.saveValues && processId) {
                    try {
                        await window.customFieldsContext.saveValues(processId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (customFieldsError) {
                        console.error('❌ Error saving custom fields:', customFieldsError);
                    }
                }
                
                if (closeAfterSave) {
                    // Show message for 2 seconds then go to view page with ID
                    setTimeout(() => {
                        window.location.href = `view/process/process.html?id=${processId}`;
                    }, 2000);
                } else {
                    // For regular Save, just show the success message (already shown above)
                    // Reset form for new entry
                    setTimeout(() => {
                        document.getElementById('name').value = '';
                        document.getElementById('description').value = '';
                        document.getElementById('ref').value = '';
                        // Reset other fields as needed
                        // Re-initialize custom fields
                        if (window.CustomFields) {
                            initializeCustomFields();
                        }
                    }, 2000);
                }
            } else {
                const serverMsg = response?.error || response?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Processes') : null;
                if (errInfo) {
                    showError(I18n.t(errInfo.key, errInfo.params));
                    if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
                } else {
                    showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Processes' }));
                }
            }
        } catch (error) {
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Processes') : null;
            if (errInfo) {
                showError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
            } else {
                showError(I18n.t('createPage.message.errorSaving', { error: serverMessage || I18n.t('createPage.message.unknownError') }));
            }
        } finally {
            buttons.forEach(btn => { 
                if (btn) { 
                    btn.disabled = false; 
                    btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); 
                } 
            });
        }

        // Uncomment when API is ready
        /*
        window.BUDG_API_SERVICE.saveProcess(payload)
            .then(res => {
                const savedMsg = (window.I18n && window.I18n.t('createPage.message.processSaved')) || 'Process saved successfully'; if (typeof window.showNotification === 'function') { window.showNotification(savedMsg, 'success'); } else { alert(savedMsg); }
                if (closeAfterSave) {
                    window.location.href = 'index.html';
                }
            })
            .catch(err => {
                const msg = err?.body?.message || err?.message || 'Save failed';
                const errMsg = (window.I18n && window.I18n.t('createPage.message.errorSaving', { error: msg })) || ('Error: ' + msg); if (typeof window.showNotification === 'function') { window.showNotification(errMsg, 'error'); } else { alert(errMsg); }
            })
            .finally(() => {
                buttons.forEach(btn => { 
                    if (btn) { 
                        btn.disabled = false; 
                        btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose'); 
                    } 
                });
            });
        */
    } catch (error) {
        showError('An error occurred while saving');
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => { 
            if (btn) { 
                btn.disabled = false; 
                btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; 
            } 
        });
    }
}

function closePage() {
    if (confirm('Are you sure you want to close? Any unsaved changes will be lost.')) {
        window.location.href = 'index.html';
    }
}

function collectProcessFormData() {
    // Helper function to convert empty strings to null for numeric fields
    const getNumericValue = (value) => {
        if (!value || value.trim() === '') {
            return null;
        }
        // Convert to integer to ensure it's a valid number
        const num = parseInt(value, 10);
        return isNaN(num) ? null : num;
    };
    
    const getStringValue = (value) => {
        return value && value.trim() !== '' ? value.trim() : null;
    };
    
    const data = {
        primaryname: document.getElementById('name')?.value || '',
        parentName: document.getElementById('parentName')?.value || '',
        parent_id: getNumericValue(document.getElementById('parentId')?.value),
        refnumber: document.getElementById('ref')?.value?.trim() || null, // Send null if empty, backend will auto-generate
        description: document.getElementById('description')?.value || '',
        input_description: document.getElementById('inputDescription')?.value || '',
        output_description: document.getElementById('outputDescription')?.value || '',
        processclass_id: getNumericValue(document.getElementById('classification')?.value),
        processautomation_id: getNumericValue(document.getElementById('automation')?.value),
        cancreate: document.getElementById('permCreate')?.checked ? 1 : 0,
        canread: document.getElementById('permRead')?.checked ? 1 : 0,
        canupdate: document.getElementById('permUpdate')?.checked ? 1 : 0,
        candelete: document.getElementById('permDelete')?.checked ? 1 : 0,
        canarchive: document.getElementById('permArchive')?.checked ? 1 : 0,
        duration_type: getNumericValue(document.getElementById('durationType')?.value),
        duration: getNumericValue(document.getElementById('durationValue')?.value),
        status: getNumericValue(document.getElementById('budgStatus')?.value),
        lifecycle_status: getNumericValue(document.getElementById('lifecycle')?.value),
        type: getNumericValue(document.getElementById('type')?.value),
        step_type: getStringValue(document.getElementById('stepType')?.value),
        ispublic: getNumericValue(document.getElementById('budgViewing')?.value),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1
    };
    
    return data;
}

function validateProcessForm(data) {
    let isValid = true;
    
    // Clear previous errors
    document.querySelectorAll('.field-error').forEach(error => {
        error.style.display = 'none';
        error.textContent = '';
    });
    
    // Validate Name
    if (!data.primaryname || !data.primaryname.trim()) {
        showFieldError('nameError', 'Name is required');
        isValid = false;
    }
    
    // Validate Description
    if (!data.description || !data.description.trim()) {
        showFieldError('descriptionError', 'Description is required');
        isValid = false;
    }
    
    // Validate BUDG Status
    if (!data.status) {
        showFieldError('budgStatusError', 'BUDG Status is required');
        isValid = false;
    }
    
    // Validate Lifecycle
    if (!data.lifecycle_status) {
        showFieldError('lifecycleError', 'Lifecycle is required');
        isValid = false;
    }
    
    // Validate Type
    if (!data.type) {
        showFieldError('typeError', 'Type is required');
        isValid = false;
    }
    
    // Validate Step Type
    if (!data.step_type) {
        showFieldError('stepTypeError', 'Step Type is required');
        isValid = false;
    }
    
    // Validate BUDG Viewing
    if (!data.ispublic) {
        showFieldError('budgViewingError', 'BUDG Viewing is required');
        isValid = false;
    }
    
    // Duration is optional even when duration type is selected
    // No validation required
    
    
    return isValid;
}

function showFieldError(errorId, message) {
    const errorElement = document.getElementById(errorId);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

function openParentPicker() {
    // This would open a picker dialog to select parent process
    const m = (window.I18n && window.I18n.t('createPage.message.parentPickerNotImplemented')) || 'Parent picker functionality would be implemented here'; if (typeof window.showNotification === 'function') { window.showNotification(m, 'info'); } else { alert(m); }
}

function initProcessFormDropdowns() {
    // Load dropdown data from database
    loadProcessDropdowns();
    
    // Handle duration field visibility
    const durationTypeSelect = document.getElementById('durationType');
    const durationValueInput = document.getElementById('durationValue');
    
    if (durationTypeSelect && durationValueInput) {
        durationTypeSelect.addEventListener('change', function() {
            if (this.value === 'Unspecified') {
                durationValueInput.disabled = true;
                durationValueInput.value = '';
                durationValueInput.placeholder = 'Not applicable';
            } else {
                durationValueInput.disabled = false;
                durationValueInput.placeholder = 'Duration value';
            }
        });
        
        // Initialize state
        if (durationTypeSelect.value === 'Unspecified') {
            durationValueInput.disabled = true;
            durationValueInput.placeholder = 'Not applicable';
        }
    }
}

async function loadProcessDropdowns() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            return;
        }

        // Load data from database in parallel
        const [statuses, processLifecycles, processTypes, viewings, processClasses, processAutomations, processDurationTypes, processStepTypesResponse] = await Promise.all([
            svc.getStatusList(),
            svc.getProcessLifecycleList(),
            svc.getProcessTypeList(),
            svc.getViewingList(),
            svc.getProcessClassList(),
            svc.getProcessAutomationList(),
            svc.getProcessDurationTypeList(),
            svc.getProcessStepTypeList()
        ]);

        // Extract data from processStepTypesResponse
        let processStepTypes = [];
        
        if (processStepTypesResponse && processStepTypesResponse.success && processStepTypesResponse.data) {
            processStepTypes = processStepTypesResponse.data;
        } else if (Array.isArray(processStepTypesResponse)) {
            processStepTypes = processStepTypesResponse;
        }

        // Fill dropdowns with database data
        fillSelect('budgStatus', statuses, 'primaryname');
        fillSelect('lifecycle', processLifecycles, 'primaryname');
        fillSelect('type', processTypes, 'primaryname');
        fillSelect('budgViewing', viewings, 'name');
        fillSelect('classification', processClasses, 'primaryname');
        fillSelect('automation', processAutomations, 'primaryname');
        fillSelect('durationType', processDurationTypes, 'primaryname');
        fillSelect('stepType', processStepTypes, 'primaryName');
        
        // Apply DFCR locked fields for Process facet (create mode)
        if (window.DFCRUtils) {
            try {
                await window.DFCRUtils.applyLockedFields('Process', {
                    status: '#budgStatus',
                    lifecycle: '#lifecycle'
                });
                console.log('DFCR locked fields applied for Process create page');
            } catch (dfcrError) {
                console.warn('Error applying DFCR locked fields:', dfcrError);
            }
        }
    } catch (error) {
        // Show database connection error
        showDatabaseConnectionError();
    }
}

function fillSelect(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el) {
        return;
    }
    
    // Clear existing options
    el.innerHTML = '';
    
    // For durationType, always add placeholder first and don't select any default
    if (selectId === 'durationType') {
        const placeholderOption = document.createElement('option');
        placeholderOption.value = "";
        placeholderOption.textContent = "Please select";
        placeholderOption.selected = true; // Make placeholder selected by default
        el.appendChild(placeholderOption);
    }
    
    if (Array.isArray(list) && list.length > 0) {
        list.forEach((item, index) => {
            const option = document.createElement('option');
            option.value = item.id || item.ID;
            option.textContent = item[labelKey] || item.name;
            el.appendChild(option);
            
            // Select the first option by default for mandatory fields (but NOT durationType)
            if (index === 0 && (selectId === 'budgStatus' || selectId === 'budgViewing' || selectId === 'lifecycle' || 
                                selectId === 'type' || selectId === 'stepType')) {
                option.selected = true;
            }
        });
    } else {
        // Add placeholder if no data (only if not already added for durationType)
        if (selectId !== 'durationType') {
            const placeholderOption = document.createElement('option');
            placeholderOption.value = "";
            placeholderOption.textContent = "Please select";
            el.appendChild(placeholderOption);
        }
    }
}

function showDatabaseConnectionError() {
    // Show error message to user
    const errorMessage = (window.I18n && window.I18n.t('createPage.message.databaseConnectionError')) || 'Error: Database connection failed. Please check your connection and try again.';
    if (typeof window.showNotification === 'function') { window.showNotification(errorMessage, 'error'); } else { alert(errorMessage); }
    
    // Disable form elements
    const formElements = document.querySelectorAll('select, input, button');
    formElements.forEach(element => {
        element.disabled = true;
    });
    
    // Show error in dropdowns
    const dropdowns = ['budgStatus', 'lifecycle', 'type', 'budgViewing', 'classification', 'automation', 'durationType', 'stepType'];
    dropdowns.forEach(dropdownId => {
        const select = document.getElementById(dropdownId);
        if (select) {
            // Clear existing options
            select.innerHTML = '';
            // Add error option
            const errorOption = document.createElement('option');
            errorOption.value = '';
            errorOption.textContent = 'Database Connection Error';
            errorOption.disabled = true;
            select.appendChild(errorOption);
        }
    });
}

// Helper function to get selected permissions as an array
function getSelectedPermissions() {
    const permissions = [];
    const permissionCheckboxes = [
        { id: 'permCreate', name: 'Create' },
        { id: 'permRead', name: 'Read' },
        { id: 'permUpdate', name: 'Update' },
        { id: 'permDelete', name: 'Delete' },
        { id: 'permArchive', name: 'Archive' }
    ];
    
    permissionCheckboxes.forEach(perm => {
        const checkbox = document.getElementById(perm.id);
        if (checkbox && checkbox.checked) {
            permissions.push(perm.name);
        }
    });
    
    return permissions;
}

// Helper function to set permissions from an array
function setSelectedPermissions(permissions) {
    const permissionCheckboxes = [
        { id: 'permCreate', name: 'Create' },
        { id: 'permRead', name: 'Read' },
        { id: 'permUpdate', name: 'Update' },
        { id: 'permDelete', name: 'Delete' },
        { id: 'permArchive', name: 'Archive' }
    ];
    
    permissionCheckboxes.forEach(perm => {
        const checkbox = document.getElementById(perm.id);
        if (checkbox) {
            checkbox.checked = permissions.includes(perm.name);
        }
    });
}

// Parent Picker Functions
let parentProcesses = [];
let filteredParentProcesses = [];

function initParentPicker() {
    const parentNameInput = document.getElementById('parentName');
    const parentDropdownToggle = document.getElementById('parentDropdownToggle');
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentSearchInput = document.getElementById('parentSearchInput');
    const parentDropdownList = document.getElementById('parentDropdownList');

    if (!parentNameInput || !parentDropdownToggle || !parentDropdownMenu || !parentSearchInput || !parentDropdownList) {
        return;
    }

    // Toggle dropdown
    parentDropdownToggle.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleParentDropdown();
    });

    parentNameInput.addEventListener('click', function() {
        toggleParentDropdown();
    });

    // Search functionality
    parentSearchInput.addEventListener('input', function() {
        filterParentProcesses(this.value);
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent processes
    loadParentProcesses();
}

function toggleParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');
    
    if (parentDropdownMenu.style.display === 'none' || parentDropdownMenu.style.display === '') {
        openParentDropdown();
    } else {
        closeParentDropdown();
    }
}

function openParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');
    const parentSearchInput = document.getElementById('parentSearchInput');
    
    parentDropdownMenu.style.display = 'block';
    parentDropdown.classList.add('open');
    parentSearchInput.focus();
    parentSearchInput.value = '';
    filterParentProcesses('');
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');
    
    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

async function loadParentProcesses() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            return;
        }

        const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
            ? parseInt(window.segmentField.getValue(), 10)
            : NaN;
        const response = await svc.getProcessList(
            Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
        );
        
        // Handle different response formats
        if (Array.isArray(response)) {
            parentProcesses = response;
        } else if (response && Array.isArray(response.data)) {
            parentProcesses = response.data;
        } else {
            parentProcesses = [];
        }
        
        filteredParentProcesses = [...parentProcesses];
        renderParentProcesses();
    } catch (error) {
        parentProcesses = [];
        filteredParentProcesses = [];
        
        // Show error message in dropdown
        const parentDropdownList = document.getElementById('parentDropdownList');
        if (parentDropdownList) {
            parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">Error loading data</div>';
        }
    }
}

function filterParentProcesses(searchTerm) {
    // Ensure parentProcesses is an array
    if (!Array.isArray(parentProcesses)) {
        filteredParentProcesses = [];
        renderParentProcesses();
        return;
    }

    if (!searchTerm || searchTerm.trim() === '') {
        filteredParentProcesses = [...parentProcesses];
    } else {
        const term = searchTerm.toLowerCase();
        filteredParentProcesses = parentProcesses.filter(process => {
            if (!process || typeof process !== 'object') {
                return false;
            }
            return (process.primaryname && process.primaryname.toLowerCase().includes(term)) ||
                   (process.description && process.description.toLowerCase().includes(term));
        });
    }
    renderParentProcesses();
}

function renderParentProcesses() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    // Ensure filteredParentProcesses is an array
    if (!Array.isArray(filteredParentProcesses)) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">Error loading processes</div>';
        return;
    }

    if (filteredParentProcesses.length === 0) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">No processes found</div>';
        return;
    }

    filteredParentProcesses.forEach(process => {
        // Validate process object
        if (!process || typeof process !== 'object') {
            return;
        }

        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${process.primaryname || 'Unnamed Process'}</div>
            <div class="searchable-dropdown-item-description">${process.description || ''}</div>
        `;
        
        item.addEventListener('click', function() {
            selectParentProcess(process);
        });
        
        parentDropdownList.appendChild(item);
    });
}

function selectParentProcess(process) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');
    
    if (parentNameInput && parentIdInput) {
        parentNameInput.value = process.primaryname;
        parentIdInput.value = process.id;
    }
    
    closeParentDropdown();
}

function openParentPicker() {
    // Legacy function for compatibility
    openParentDropdown();
}

// Success and Error message functions
function showSuccess(message) {
    // Create a temporary success message
    const successDiv = document.createElement('div');
    successDiv.className = 'success-message';
    successDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background-color: #248567;
        color: white;
        padding: 1rem;
        border-radius: 0.5rem;
        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        z-index: 1000;
        font-size: 0.875rem;
    `;
    successDiv.textContent = message;
    
    document.body.appendChild(successDiv);
    
    setTimeout(() => {
        successDiv.remove();
    }, 3000);
}

function showError(message) {
    // Create a temporary error message
    const errorDiv = document.createElement('div');
    errorDiv.className = 'error-message';
    errorDiv.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        background-color: #ef4444;
        color: white;
        padding: 1rem;
        border-radius: 0.5rem;
        box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
        z-index: 1000;
        font-size: 0.875rem;
    `;
    errorDiv.textContent = message;
    
    document.body.appendChild(errorDiv);
    
    setTimeout(() => {
        errorDiv.remove();
    }, 5000);
}

// Export functions for potential external use
window.PROCESS_PAGE = {
    savePage,
    closePage,
    collectProcessFormData,
    validateProcessForm,
    getSelectedPermissions,
    setSelectedPermissions,
    initParentPicker,
    selectParentProcess,
    showSuccess,
    showError
};
