// Policy Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

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

    // Initialize form dropdowns
    initPolicyFormDropdowns();
    
    // Initialize parent picker
    initParentPicker();
    
    // Initialize custom fields
    initializeCustomFields();

    // Wire up advanced rich text editor for description
    const editorButton = document.querySelector('.editor-button');
    if (editorButton) {
        editorButton.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            if (typeof window.toggleAdvancedRichTextEditor === 'function') {
                window.toggleAdvancedRichTextEditor('description', editorButton);
            }
        });
    }
});

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Policy',
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
                objectType: 'Policy',
                fieldId: 'policySegment',
                errorId: 'policySegmentError',
                onChange: async () => {
                    const parentNameInput = document.getElementById('parentName');
                    const parentIdInput = document.getElementById('parentId');
                    const previousParentId = parentIdInput?.value ? parseInt(parentIdInput.value, 10) : null;
                    if (typeof loadParentPolicies === 'function') {
                        await loadParentPolicies();
                    }
                    if (previousParentId && Number.isInteger(previousParentId)) {
                        const stillAllowed = Array.isArray(parentPolicies) &&
                            parentPolicies.some(p => parseInt(p.id || p.ID, 10) === previousParentId);
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
        // Disable buttons and show loading state
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                btn.textContent = I18n.t('createPage.message.saving');
            }
        });

        // Sync advanced rich text editor content to textarea before saving
        if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
            window.syncAdvancedRichTextToTextarea('description');
        }

        // Collect form data
        const payload = collectPolicyFormData();

        // Keep refNumber empty on client; server will auto-generate a simple value

        // Validate form
        if (!validatePolicyForm(payload)) {
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
            const response = await fetch('/api/policy');
            const list = await response.json();
            const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];

            const refVal = String(payload.refNumber || '').trim().toLowerCase();

            // Check for duplicate Reference Number if provided (case-insensitive)
            if (refVal && refVal !== '') {
                const refClash = rows.some(r => String(r.refNumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                if (refClash) {
                    const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Policies'}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    document.getElementById('ref')?.focus();
                    buttons.forEach(btn => { if (btn) { btn.disabled = false; btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close'; } });
                    return;
                }
            }
        } catch (_) {
            console.warn('Client-side duplicate check failed, falling back to server-side validation');
            // fall back to server-side validation
        }

        // Make API call
        const apiService = new ApiService();
        const response = await apiService.post('/policy', payload);

        // console.log('API response:', response);
            // console.log('Response type:', typeof response);
            // console.log('Response success:', response?.success);
        // console.log('Response message:', response?.message);
        // console.log('Response error:', response?.error);

            if (response && response.success) {
            // console.log('Policy saved successfully');
            showSuccess(I18n.t('createPage.message.policySaved'));

                // Get the ID from the response
                const policyId = response.data?.id || response.id;
                // Saved policy ID stored in variable
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && policyId) {
                    try {
                        await window.customFieldsContext.saveValues(policyId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (customFieldsError) {
                        console.error('❌ Error saving custom fields:', customFieldsError);
                        // Don't fail the whole save if custom fields fail
                    }
                }

                if (closeAfterSave) {
                    // Show message for 2 seconds then go to view page with ID
                    setTimeout(() => {
                        window.location.href = `view/policy/policy.html?id=${policyId}`;
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
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Policies') : null;
                if (errInfo) {
                    showError(I18n.t(errInfo.key, errInfo.params));
                    if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
                } else {
                    showError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Policies' }));
                }
            }

    } catch (error) {
        const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Policies') : null;
        if (errInfo) {
            showError(I18n.t(errInfo.key, errInfo.params));
            if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
        } else {
            showError(I18n.t('createPage.message.errorSaving', { error: serverMessage || I18n.t('createPage.message.unknownError') }));
        }
        } finally {
        // Re-enable buttons
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose');
                }
            });
    }

    // console.log('=== SAVE POLICY END ===');
}

function collectPolicyFormData() {
    // console.log('=== COLLECTING POLICY FORM DATA ===');

    // Helper function to convert empty strings to null for numeric fields
    const getNumericValue = (value) => {
        if (value && value.trim() !== '') {
            const num = parseInt(value, 10);
            return isNaN(num) ? null : num;
        }
        return null;
    };

    // Helper function to convert empty strings to null for string fields
    const getStringValue = (value) => {
        if (value && value.trim() !== '') {
            return value.trim();
        }
        return null;
    };

    // Get form values
    const nameValue = document.getElementById('name')?.value || '';
    const parentIdValue = document.getElementById('parentId')?.value;
    const refValue = document.getElementById('ref')?.value || '';
    const descValue = document.getElementById('description')?.value || '';
    const typeValue = document.getElementById('type')?.value;
    const internalValue = document.getElementById('internal')?.checked ? 1 : 0;
    const urlValue = document.getElementById('url')?.value || '';
    const effectiveDateValue = document.getElementById('effectiveDate')?.value || '';
    const endDateValue = document.getElementById('endDate')?.value || '';
    const statusValue = document.getElementById('BUDGStatus')?.value;
    const lifecycleValue = document.getElementById('lifecycle')?.value;
    const viewingValue = document.getElementById('BUDGViewing')?.value;

    // Build payload with correct field names matching Policy model
    const data = {
        primaryName: getStringValue(nameValue),
        description: getStringValue(descValue),
        parentId: getNumericValue(parentIdValue),
        refNumber: getStringValue(refValue),
        effectiveDate: getStringValue(effectiveDateValue),
        endDate: getStringValue(endDateValue),
        status: getNumericValue(statusValue),
        policyType: getNumericValue(typeValue),
        lifecycleStatus: getNumericValue(lifecycleValue),
        internal: internalValue,
        isPublic: getNumericValue(viewingValue),
        url: getStringValue(urlValue),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1,
        createdById: getCurrentUserId(),
        lastUpdateUserId: getCurrentUserId()
    };

    // console.log('=== FINAL PAYLOAD ===');
    // console.log(JSON.stringify(data, null, 2));

    // Validation checks
    // console.log('=== VALIDATION CHECKS ===');
    // console.log('Required fields:');
    // console.log('- primaryName:', data.primaryName ? '✓' : '✗');
    // console.log('- description:', data.description ? '✓' : '✗');
    // console.log('- policyType:', data.policyType ? '✓' : '✗');
    // console.log('- status:', data.status ? '✓' : '✗');
    // console.log('- lifecycleStatus:', data.lifecycleStatus ? '✓' : '✗');
    // console.log('- isPublic:', data.isPublic ? '✓' : '✗');

    // console.log('Data types:');
    // console.log('- internal is boolean:', typeof data.internal === 'boolean');
    // console.log('- numeric fields are numbers:',
    //     typeof data.policyType === 'number' &&
    //     typeof data.status === 'number' &&
    //     typeof data.lifecycleStatus === 'number' &&
    //     typeof data.isPublic === 'number');

    return data;
}

function validatePolicyForm(data) {
    // console.log('=== VALIDATING POLICY FORM ===');
    let isValid = true;

    // Clear previous errors
    document.querySelectorAll('.field-error').forEach(error => {
        error.style.display = 'none';
        error.textContent = '';
    });
    
    // Validate custom fields
    if (window.customFieldsContext && window.customFieldsContext.validate) {
        const customFieldsValid = window.customFieldsContext.validate();
        if (!customFieldsValid) {
            isValid = false;
        }
    }

    // Validate Name
    if (!data.primaryName || !data.primaryName.trim()) {
        showFieldError('nameError', 'Name is required');
        isValid = false;
    }

    // Validate Description
    if (!data.description || !data.description.trim()) {
        showFieldError('descriptionError', 'Description is required');
        isValid = false;
    }

    // Validate Type
    if (!data.policyType) {
        showFieldError('typeError', 'Type is required');
        isValid = false;
    }

    // Validate Status
    if (!data.status) {
        showFieldError('BUDGStatusError', 'Status is required');
        isValid = false;
    }

    // Validate Lifecycle
    if (!data.lifecycleStatus) {
        showFieldError('lifecycleError', 'Lifecycle is required');
        isValid = false;
    }

    // Validate Viewing
    if (!data.isPublic) {
        showFieldError('BUDGViewingError', 'Viewing is required');
        isValid = false;
    }
    

    // console.log('Form validation result:', isValid ? 'PASSED' : 'FAILED');
    return isValid;
}

function showFieldError(fieldId, message) {
    const errorElement = document.getElementById(fieldId);
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
}

function closePage() {
    // console.log('Closing policy page');

    if (confirm('Are you sure you want to close? Any unsaved changes will be lost.')) {
        window.location.href = 'index.html';
    }
}

async function initPolicyFormDropdowns() {
    // console.log('Initializing policy form dropdowns...');

    try {
        const apiService = new ApiService();

        // Load all dropdowns in parallel
        const [statusList, viewingList, lifecycleList, typeList] = await Promise.all([
            apiService.getStatusList(),
            apiService.getViewingList(),
            apiService.getPolicyLifecycleList(),
            apiService.getPolicyTypeList()
        ]);

        // console.log('Dropdown data loaded:', {
        //     status: statusList?.length || 0,
        //     viewing: viewingList?.length || 0,
        //     lifecycle: lifecycleList?.length || 0,
        //     type: typeList?.length || 0
        // });

        // Fill dropdowns
        fillSelect('BUDGStatus', statusList, 'primaryname');
        fillSelect('BUDGViewing', viewingList, 'name');
        fillSelect('lifecycle', lifecycleList, 'primaryname');
        fillSelect('type', typeList, 'primaryname');

        // console.log('Policy form dropdowns initialized successfully');

    } catch (error) {
        // console.error('Failed to load policy dropdowns:', error);

        // Show database connection error
        showDatabaseConnectionError();
    }
}

function fillSelect(selectId, data, valueField) {
    const select = document.getElementById(selectId);
    if (!select) {
        // console.warn('Select element not found:', selectId);
        return;
    }

    // Clear existing options
    select.innerHTML = '';

    if (data && Array.isArray(data) && data.length > 0) {
        data.forEach((item, index) => {
            const option = document.createElement('option');
            option.value = item.id;
            option.textContent = item[valueField] || item.name || item.primaryname || 'Unknown';
            select.appendChild(option);

            // Select the first option by default for mandatory fields
            if (index === 0 && (selectId === 'BUDGStatus' || selectId === 'BUDGViewing' ||
                                selectId === 'lifecycle' || selectId === 'type')) {
                option.selected = true;
            }
        });
        // console.log(`Filled ${selectId} with ${data.length} options`);
    } else {
        // Add placeholder if no data
        const placeholderOption = document.createElement('option');
        placeholderOption.value = "";
        placeholderOption.textContent = "Please select";
        select.appendChild(placeholderOption);
        // console.warn('No data provided for select:', selectId);
    }
}

function showDatabaseConnectionError() {
    // console.error('Database connection error - cannot load dropdown data');

    // Show error message to user
    const errorMessage = 'Error: Database connection failed. Please check your connection and try again.';
    if (typeof window.showNotification === 'function') { window.showNotification(errorMessage, 'error'); } else { alert(errorMessage); }

    // Disable form elements
    const formElements = document.querySelectorAll('select, input, button');
    formElements.forEach(element => {
        element.disabled = true;
    });

    // Show error in dropdowns
    const dropdowns = ['BUDGStatus', 'BUDGViewing', 'lifecycle', 'type'];
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
let parentPolicies = [];
let filteredParentPolicies = [];

function initParentPicker() {
    const parentNameInput = document.getElementById('parentName');
    const parentDropdownToggle = document.getElementById('parentDropdownToggle');
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentSearchInput = document.getElementById('parentSearchInput');
    const parentDropdownList = document.getElementById('parentDropdownList');

    if (!parentNameInput || !parentDropdownToggle || !parentDropdownMenu || !parentSearchInput || !parentDropdownList) {
        // console.error('Parent picker elements not found');
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
        filterParentPolicies(this.value);
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent policies
    loadParentPolicies();
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
    filterParentPolicies('');
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

async function loadParentPolicies() {
    try {
        const svc = window.BUDG_API_SERVICE;
        if (!svc) {
            // console.error('API service not available');
            return;
        }

        // console.log('Loading parent policies...');
        const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
            ? parseInt(window.segmentField.getValue(), 10)
            : NaN;
        const policies = await svc.getPolicyParentOptions(
            Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
        );

        if (policies && Array.isArray(policies)) {
            parentPolicies = policies;
            filteredParentPolicies = [...policies];
            // console.log(`Loaded ${policies.length} parent policies`);
            renderParentPolicies();
        } else {
            // console.error('Invalid policy data received:', policies);
        }
    } catch (error) {
        // console.error('Failed to load parent policies:', error);
        // Fallback to empty array
        parentPolicies = [];
        filteredParentPolicies = [];
    }
}

function filterParentPolicies(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredParentPolicies = [...parentPolicies];
    } else {
        filteredParentPolicies = parentPolicies.filter(policy =>
            policy.primaryname?.toLowerCase().includes(term) ||
            policy.description?.toLowerCase().includes(term) ||
            policy.refnumber?.toLowerCase().includes(term)
        );
    }

    renderParentPolicies();
}

function renderParentPolicies() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    if (filteredParentPolicies.length === 0) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">No policies found</div>';
        return;
    }

    filteredParentPolicies.forEach(policy => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${policy.primaryname || 'سياسة غير مسماة'}</div>
            <div class="searchable-dropdown-item-description">${policy.description || policy.refnumber || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectParentPolicy(policy);
        });

        parentDropdownList.appendChild(item);
    });
}

function selectParentPolicy(policy) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = policy.primaryname;
        parentIdInput.value = policy.id;
    }

    closeParentDropdown();
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

// Get current user ID from session/API
function getCurrentUserId() {
    // Try to get from session storage first
    const userStr = sessionStorage.getItem('currentUser');
    if (userStr) {
        try {
            const user = JSON.parse(userStr);
            if (user && (user.id || user.ID || user.userId)) {
                return user.id || user.ID || user.userId;
            }
        } catch (e) {
            console.error('Error parsing user from session:', e);
        }
    }

    // Fallback to default user ID
    console.warn('User ID not found in session, using default ID: 1');
    return 0;
}

// Fetch and cache current user information
async function fetchCurrentUser() {
    try {
        const response = await fetch('/api/me');
        if (response.ok) {
            const user = await response.json();
            sessionStorage.setItem('currentUser', JSON.stringify(user));
            console.log('👤 Current user fetched and cached:', user);
            return user;
        }
    } catch (error) {
        console.error('Error fetching current user:', error);
    }
    return null;
}

// Initialize user on page load
document.addEventListener('DOMContentLoaded', async function() {
    await fetchCurrentUser();
});

// Export functions for testing
if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
    collectPolicyFormData,
        validatePolicyForm,
        savePage,
        showSuccess,
        showError,
        getCurrentUserId,
        fetchCurrentUser
};
}
