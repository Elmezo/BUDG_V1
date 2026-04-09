// Project Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');
    const parentPicker = document.getElementById('parentPicker');
    const classificationPicker = document.getElementById('classificationPicker');

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

    if (parentPicker) {
        parentPicker.addEventListener('click', function() {
            openParentPicker();
        });
    }

    if (classificationPicker) {
        classificationPicker.addEventListener('click', function() {
            openClassificationPicker();
        });
    }

    // Initialize form dropdowns
    initProjectFormDropdowns();
    
    // Initialize parent picker
    initParentPicker();
    
    // Initialize classification picker
    initClassificationPicker();

    // Initialize automatic reference generation
    initAutoReferenceGeneration();

    // Initialize custom fields
    initializeCustomFields();

    // Advanced Rich Text Editor toggle for description
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('description', showDescriptionEditorBtn);
        });
    }
    
});

// Initialize custom fields (create mode)
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Project',
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
                objectType: 'Project',
                fieldId: 'projectSegment',
                errorId: 'projectSegmentError',
                onChange: async () => {
                    const parentNameInput = document.getElementById('parentName');
                    const parentIdInput = document.getElementById('parentId');
                    const previousParentId = parentIdInput?.value ? parseInt(parentIdInput.value, 10) : null;
                    if (typeof loadParentProjects === 'function') {
                        await loadParentProjects();
                    }
                    if (previousParentId && Number.isInteger(previousParentId)) {
                        const stillAllowed = Array.isArray(parentProjects) &&
                            parentProjects.some(p => parseInt(p.id || p.ID, 10) === previousParentId);
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

// Initialize automatic reference generation
// Note: Ref is only auto-generated on save if empty (like process & policy)
// No auto-generation on name input - removed to match other facets
function initAutoReferenceGeneration() {
    // This function is kept for compatibility but does nothing
    // Ref generation happens in the backend when saving if ref is empty
}

async function savePage(closeAfterSave) {
    try {
        // Sync advanced rich text editor content to textarea before saving
        window.syncAdvancedRichTextToTextarea('description');

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                btn.textContent = I18n.t('createPage.message.saving');
            }
        });

        const payload = collectProjectFormData();

        // Validate custom fields
        if (window.customFieldsContext && window.customFieldsContext.validate) {
            const customFieldsValid = window.customFieldsContext.validate();
            if (!customFieldsValid) {
                buttons.forEach(btn => {
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose');
                    }
                });
                showError('Please fix Custom Fields errors before saving.');
                return;
            }
        }

        // Validate required fields
        if (!validateProjectForm(payload)) {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
                }
            });
            return;
        }

        // Uniqueness checks: Name and Ref_Number
        try {
            if (typeof window.BUDG_API_SERVICE?.getProjectList === 'function') {
                const list = await window.BUDG_API_SERVICE.getProjectList();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                const refVal = String(payload.refnumber || '').trim().toLowerCase();
                
                // Only validate ref if it's provided (allow empty ref - will be auto-generated)
                if (refVal) {
                    const refClash = rows.some(r => String(r.refnumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                    if (refClash) {
                        const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Projects'});
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        document.getElementById('ref')?.focus();
                        buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; } });
                        return;
                    }
                }
                // If ref is empty, allow it - will be auto-generated after creation
            }
        } catch (_) { /* fall back to server-side validation */ }

        // Call real API
        try {
            const svc = window.BUDG_API_SERVICE;
            if (!svc) {
                throw new Error('API service not available');
            }

            const response = await svc.post('/project', payload);

            if (response && response.success) {
                
                showSuccess(I18n.t('createPage.message.projectSaved'));
                
                // Get the ID from the response
                const projectId = response.data?.id || response.id;

                // Save custom fields if context is available
                if (window.customFieldsContext && window.customFieldsContext.saveValues && projectId) {
                    try {
                        await window.customFieldsContext.saveValues(projectId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (customFieldsError) {
                        console.error('❌ Error saving custom fields:', customFieldsError);
                        showError('Project saved, but Custom Fields failed to save.');
                    }
                }

                if (closeAfterSave) {
                    // Show message for 2 seconds then go to view page with ID
                    setTimeout(() => {
                        window.location.href = `view/project/project.html?id=${projectId}`;
                    }, 2000);
                } else {
                    // For regular Save, just show the success message (already shown above)
                    // Reset form for new entry
                    setTimeout(() => {
                        document.getElementById('name').value = '';
                        document.getElementById('description').value = '';
                        document.getElementById('ref').value = '';
                        // Reset other fields as needed
                        
                        // Re-initialize custom fields for new entry
                        if (window.CustomFields) {
                            initializeCustomFields();
                        }
                    }, 2000);
                }
            } else {
                const serverMsg = response?.error || response?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Projects') : null;
                if (errInfo) {
                    showError(I18n.t(errInfo.key, errInfo.params));
                    if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
                } else {
                    showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Projects' }));
                }
            }
        } catch (error) {
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Projects') : null;
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
                    btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
                }
            });
        }
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

function collectProjectFormData() {
    // Helper function to convert empty strings to null for numeric fields
    const getNumericValue = (value) => {
        if (!value || value.trim() === '') {
            return null;
        }
        // Convert to integer to ensure it's a valid number
        const num = parseInt(value, 10);
        return isNaN(num) ? null : num;
    };

    // Helper function to get date value or current date
    const getDateValue = (value) => {
        if (value && value.trim() !== '') {
            return value;
        }
        // Return current date in YYYY-MM-DD format
        const today = new Date();
        return today.toISOString().split('T')[0];
    };

    const data = {
        primaryname: document.getElementById('name')?.value || '',
        parentName: document.getElementById('parentName')?.value || '',
        parentid: getNumericValue(document.getElementById('parentId')?.value),
        refnumber: document.getElementById('ref')?.value || '',
        description: document.getElementById('description')?.value || '',
        project_type: getNumericValue(document.getElementById('type')?.value),
        classification: getNumericValue(document.getElementById('classificationId')?.value),
        rag: getNumericValue(document.getElementById('rag')?.value),
        lifecycle_status: getNumericValue(document.getElementById('lifecycle')?.value),
        status: getNumericValue(document.getElementById('budgStatus')?.value),
        is_public: getNumericValue(document.getElementById('budgViewing')?.value),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1
    };

    // Handle dates - both are required
    const startDate = document.getElementById('startDate')?.value;
    const endDate = document.getElementById('endDate')?.value;
    
    // Both dates are required
    if (startDate && startDate.trim() !== '') {
        data.startdate = startDate;
    }
    
    if (endDate && endDate.trim() !== '') {
        data.enddate = endDate;
    }

    return data;
}

function validateProjectForm(data) {
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

    // Validate Type
    if (!data.project_type) {
        showFieldError('typeError', 'Type is required');
        isValid = false;
    }

    // Validate RAG
    if (!data.rag) {
        showFieldError('ragError', 'RAG is required');
        isValid = false;
    }

    // Validate Lifecycle
    if (!data.lifecycle_status) {
        showFieldError('lifecycleError', 'Lifecycle is required');
        isValid = false;
    }

    // Validate BUDG Status
    if (!data.status) {
        showFieldError('budgStatusError', 'BUDG Status is required');
        isValid = false;
    }

    // Validate BUDG Viewing
    if (!data.is_public) {
        showFieldError('budgViewingError', 'BUDG Viewing is required');
        isValid = false;
    }

    // Validate Start Date
    if (!data.startdate || !data.startdate.trim()) {
        showFieldError('startDateError', 'Start Date is required');
        isValid = false;
    }

    // Validate End Date
    if (!data.enddate || !data.enddate.trim()) {
        showFieldError('endDateError', 'End Date is required');
        isValid = false;
    }

    // Validate that End Date is after Start Date
    if (data.startdate && data.enddate) {
        const start = new Date(data.startdate);
        const end = new Date(data.enddate);
        if (end < start) {
            showFieldError('endDateError', 'End Date must be after Start Date');
            isValid = false;
        }
    }

    if (window.customFieldsContext && window.customFieldsContext.validate) {
        const customFieldsValid = window.customFieldsContext.validate();
        if (!customFieldsValid) {
            isValid = false;
        }
    }

    return isValid;
}

function showFieldError(errorId, message) {
    const errorElement = document.getElementById(errorId);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

function showFieldError(errorId, message) {
    const errorElement = document.getElementById(errorId);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

function openParentPicker() {
    // This would open a picker dialog to select parent project
    const m = (window.I18n && window.I18n.t('createPage.message.parentPickerNotImplemented')) || 'Parent picker functionality would be implemented here';
    if (typeof window.showNotification === 'function') { window.showNotification(m, 'info'); } else { alert(m); }
}

function openClassificationPicker() {
    // This would open a picker dialog to select classification
    const m = (window.I18n && window.I18n.t('createPage.message.classificationPickerNotImplemented')) || 'Classification picker functionality would be implemented here';
    if (typeof window.showNotification === 'function') { window.showNotification(m, 'info'); } else { alert(m); }
}

// Helper function to format date for display (DD/MM/YYYY format like in the image)
function formatDateForDisplay(dateString) {
    if (!dateString) return '';

    const date = new Date(dateString);
    const day = String(date.getDate()).padStart(2, '0');
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const year = date.getFullYear();

    return `${day}/${month}/${year}`;
}

// Helper function to parse display date back to ISO format
function parseDateFromDisplay(displayDate) {
    if (!displayDate || !displayDate.includes('/')) return '';

    const parts = displayDate.split('/');
    if (parts.length !== 3) return '';

    const day = parts[0];
    const month = parts[1];
    const year = parts[2];

    return `${year}-${month}-${day}`;
}

// Helper function to get RAG color for styling
function getRagColor(ragValue) {
    switch (ragValue?.toLowerCase()) {
        case 'green':
            return '#16a34a';
        case 'amber':
            return '#d97706';
        case 'red':
            return '#dc2626';
        case 'blue':
            return '#2563eb';
        default:
            return '#6b7280';
    }
}

// Initialize project form dropdowns
async function initProjectFormDropdowns() {
    // Dates will remain empty - no default values set

    try {
        await loadProjectDropdowns();
    } catch (error) {
        showDatabaseConnectionError();
    }
}

// Load project dropdowns from API
async function loadProjectDropdowns() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            return;
        }

        // Load data from database in parallel
        const [projectTypes, projectClassifications, projectLifecycles, projectRags, statusList, viewingList] = await Promise.all([
            svc.getProjectTypeList(),
            svc.getProjectClassificationList(),
            svc.getProjectLifecycleList(),
            svc.getProjectRagList(),
            svc.getStatusList(),
            svc.getViewingList()
        ]);

        // Fill dropdowns with database data using correct column names
        fillSelect('type', projectTypes, 'primaryname');
        fillSelect('lifecycle', projectLifecycles, 'primaryname');
        fillSelect('rag', projectRags, 'primaryname');
        fillSelect('budgStatus', statusList, 'primaryname');
        fillSelect('budgViewing', viewingList, 'name'); // viewing table uses 'name' column
    } catch (error) {
        throw error; // Re-throw to trigger fallback
    }
}

// Fill select dropdown with data
function fillSelect(selectId, list, labelKey) {
    const el = document.getElementById(selectId);
    if (!el) {
        return;
    }

    // Clear existing options
    el.innerHTML = '';

    if (Array.isArray(list) && list.length > 0) {
        list.forEach((item, index) => {
            const option = document.createElement('option');
            option.value = item.id || item.ID;
            option.textContent = item[labelKey] || item.name;
            el.appendChild(option);
            
            // Select the first option by default for mandatory fields
            if (index === 0 && (selectId === 'budgStatus' || selectId === 'budgViewing' || 
                                selectId === 'lifecycle' || selectId === 'type' || selectId === 'rag')) {
                option.selected = true;
            }
        });
    } else {
        // Add placeholder if no data
        const placeholderOption = document.createElement('option');
        placeholderOption.value = "";
        placeholderOption.textContent = "Please select";
        el.appendChild(placeholderOption);
    }
}

// Populate a dropdown with data (legacy function - keeping for fallback)
function populateDropdown(elementId, data, placeholder = '') {
    const dropdown = document.getElementById(elementId);
    if (!dropdown) {
        return;
    }

    // Clear existing options
    dropdown.innerHTML = '';

    // Add placeholder option
    if (placeholder) {
        const placeholderOption = document.createElement('option');
        placeholderOption.value = '';
        placeholderOption.textContent = placeholder;
        dropdown.appendChild(placeholderOption);
    }

    // Add data options
    if (Array.isArray(data)) {
        data.forEach(item => {
            const option = document.createElement('option');
            option.value = item.id;
            option.textContent = item.primaryname || item.primaryName;
            dropdown.appendChild(option);
        });
    }
}

// Load fallback dropdown values if API fails
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
    const dropdowns = ['type', 'classificationId', 'rag', 'lifecycle', 'budgStatus', 'budgViewing'];
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

// Parent Picker Functions
let parentProjects = [];
let filteredParentProjects = [];

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
        filterParentProjects(this.value);
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent projects
    loadParentProjects();
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
    filterParentProjects('');
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

function openParentPicker() {
    // Legacy function for compatibility
    openParentDropdown();
}

async function loadParentProjects() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            return;
        }

        const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
            ? parseInt(window.segmentField.getValue(), 10)
            : NaN;
        const projects = await svc.getProjectList(
            Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
        );

        if (projects && Array.isArray(projects)) {
            parentProjects = projects;
            filteredParentProjects = [...projects];
            renderParentProjects();
        }
    } catch (error) {
        // Fallback to empty array
        parentProjects = [];
        filteredParentProjects = [];
    }
}

function filterParentProjects(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredParentProjects = [...parentProjects];
    } else {
        filteredParentProjects = parentProjects.filter(project =>
            project.primaryname?.toLowerCase().includes(term) ||
            project.description?.toLowerCase().includes(term) ||
            project.refnumber?.toLowerCase().includes(term)
        );
    }

    renderParentProjects();
}

function renderParentProjects() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    if (filteredParentProjects.length === 0) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">No projects found</div>';
        return;
    }

    filteredParentProjects.forEach(project => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${project.primaryname || 'Unnamed Project'}</div>
            <div class="searchable-dropdown-item-description">${project.description || project.refnumber || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectParentProject(project);
        });

        parentDropdownList.appendChild(item);
    });
}

function selectParentProject(project) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = project.primaryname;
        parentIdInput.value = project.id;
    }

    closeParentDropdown();
}

// Classification Picker Functions
let classifications = [];
let filteredClassifications = [];

function initClassificationPicker() {
    const classificationNameInput = document.getElementById('classificationName');
    const classificationDropdownToggle = document.getElementById('classificationDropdownToggle');
    const classificationDropdownMenu = document.getElementById('classificationDropdownMenu');
    const classificationSearchInput = document.getElementById('classificationSearchInput');
    const classificationDropdownList = document.getElementById('classificationDropdownList');

    if (!classificationNameInput || !classificationDropdownToggle || !classificationDropdownMenu || !classificationSearchInput || !classificationDropdownList) {
        // console.error('Classification picker elements not found');
        return;
    }

    // Toggle dropdown
    classificationDropdownToggle.addEventListener('click', function(e) {
        e.stopPropagation();
        toggleClassificationDropdown();
    });

    classificationNameInput.addEventListener('click', function() {
        toggleClassificationDropdown();
    });

    // Search functionality
    classificationSearchInput.addEventListener('input', function() {
        filterClassifications(this.value);
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeClassificationDropdown();
        }
    });

    // Load classifications
    loadClassifications();
}

function toggleClassificationDropdown() {
    const classificationDropdownMenu = document.getElementById('classificationDropdownMenu');
    const classificationDropdown = document.querySelector('#classificationDropdownMenu').closest('.searchable-dropdown-container').querySelector('.searchable-dropdown');

    if (classificationDropdownMenu.style.display === 'none' || classificationDropdownMenu.style.display === '') {
        openClassificationDropdown();
    } else {
        closeClassificationDropdown();
    }
}

function openClassificationDropdown() {
    const classificationDropdownMenu = document.getElementById('classificationDropdownMenu');
    const classificationDropdown = document.querySelector('#classificationDropdownMenu').closest('.searchable-dropdown-container').querySelector('.searchable-dropdown');
    const classificationSearchInput = document.getElementById('classificationSearchInput');

    classificationDropdownMenu.style.display = 'block';
    classificationDropdown.classList.add('open');
    classificationSearchInput.focus();
    classificationSearchInput.value = '';
    filterClassifications('');
}

function closeClassificationDropdown() {
    const classificationDropdownMenu = document.getElementById('classificationDropdownMenu');
    const classificationDropdown = document.querySelector('#classificationDropdownMenu').closest('.searchable-dropdown-container').querySelector('.searchable-dropdown');

    classificationDropdownMenu.style.display = 'none';
    classificationDropdown.classList.remove('open');
}

function openClassificationPicker() {
    // Legacy function for compatibility
    openClassificationDropdown();
}

async function loadClassifications() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            // console.error('API service not available');
            return;
        }

        // console.log('Loading classifications...');
        const classificationData = await svc.getProjectClassificationList();

        if (classificationData && Array.isArray(classificationData)) {
            classifications = classificationData;
            filteredClassifications = [...classificationData];
            // console.log(`Loaded ${classificationData.length} classifications`);
            renderClassifications();
        } else {
            // console.error('Invalid classification data received:', classificationData);
        }
    } catch (error) {
        // console.error('Failed to load classifications:', error);
        // Fallback to empty array
        classifications = [];
        filteredClassifications = [];
    }
}

function filterClassifications(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredClassifications = [...classifications];
    } else {
        filteredClassifications = classifications.filter(classification =>
            classification.primaryname?.toLowerCase().includes(term) ||
            classification.description?.toLowerCase().includes(term)
        );
    }

    renderClassifications();
}

function renderClassifications() {
    const classificationDropdownList = document.getElementById('classificationDropdownList');
    if (!classificationDropdownList) return;

    classificationDropdownList.innerHTML = '';

    if (filteredClassifications.length === 0) {
        classificationDropdownList.innerHTML = '<div class="searchable-dropdown-item">No classifications found</div>';
        return;
    }

    filteredClassifications.forEach(classification => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${classification.primaryname || 'Unnamed Classification'}</div>
            <div class="searchable-dropdown-item-description">${classification.description || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectClassification(classification);
        });

        classificationDropdownList.appendChild(item);
    });
}

function selectClassification(classification) {
    const classificationNameInput = document.getElementById('classificationName');
    const classificationIdInput = document.getElementById('classificationId');

    if (classificationNameInput && classificationIdInput) {
        classificationNameInput.value = classification.primaryname;
        classificationIdInput.value = classification.id;
    }

    closeClassificationDropdown();
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
window.PROJECT_PAGE = {
    savePage,
    closePage,
    collectProjectFormData,
    validateProjectForm,
    formatDateForDisplay,
    parseDateFromDisplay,
    getRagColor,
    showSuccess,
    showError
};

