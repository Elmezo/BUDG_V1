// Regulatory Theme Page JavaScript
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
    initRegulatoryThemeFormDropdowns();
    
    // Initialize parent picker
    initParentPicker();
    
    // Initialize custom fields
    initializeCustomFields();

    // Advanced Rich Text Editor toggle
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('description', showDescriptionEditorBtn);
        });
    }
});

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Regulatory Theme',
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
                objectType: 'Regulatory Theme',
                fieldId: 'regulatoryThemeSegment',
                errorId: 'regulatoryThemeSegmentError'
            });
            console.log('Segment field initialized');
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }
}


async function savePage(closeAfterSave) {
    // Sync advanced rich text editor content to textarea before saving
    if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
        window.syncAdvancedRichTextToTextarea('description');
    }

    try {
        // Disable buttons and show loading state
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                btn.textContent = I18n.t('createPage.message.saving');
            }
        });

        // Collect form data
        const payload = collectRegulatoryThemeFormData();

        // Validate form
        if (!validateRegulatoryThemeForm(payload)) {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = btn.id === 'saveBtn' ? I18n.t('button.save') : I18n.t('button.saveAndClose');
                }
            });
            return;
        }
        
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
                return;
            }
        }

        // Uniqueness checks: Name and Ref_Number
        try {
            const response = await fetch('/api/regulatory-theme');
            const list = await response.json();
            const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];

            const refVal = String(payload.refNumber || '').trim().toLowerCase();

            // Check for duplicate Reference Number if provided (case-insensitive)
            if (refVal && refVal !== '') {
                const refClash = rows.some(r => String(r.refNumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                if (refClash) {
                    showStyledError(I18n.t('createPage.message.duplicateRefNumber', {facet: 'Regulatory Themes'}));
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
        const response = await apiService.post('/regulatory-theme', payload);

        if (response && response.success) {
            showSuccess(I18n.t('createPage.message.regulatoryThemeSaved'));

            // Get the ID from the response
            const themeId = response.data?.id || response.id;
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues && themeId != null) {
                try {
                    await window.customFieldsContext.saveValues(themeId);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }

            if (closeAfterSave) {
                // Show message for 2 seconds then go to view page with ID
                setTimeout(() => {
                    window.location.href = `/view/regulatory-theme/${themeId}`;
                }, 2000);
            } else {
                // For regular Save, just show the success message (already shown above)
                // Reset form for new entry
                setTimeout(() => {
                    document.getElementById('name').value = '';
                    document.getElementById('description').value = '';
                    document.getElementById('ref').value = '';
                    document.getElementById('shortName').value = '';
                    // Reset other fields as needed
                }, 2000);
            }
        } else {
            const serverMsg = response?.error || response?.message;
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Regulatory Themes') : null;
            if (errInfo) {
                showStyledError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') document.getElementById('name')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('ref')?.focus();
            } else {
                showStyledError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Regulatory Themes' }));
            }
        }

    } catch (error) {
        const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Regulatory Themes') : null;
        if (errInfo) {
            showStyledError(I18n.t(errInfo.key, errInfo.params));
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
                btn.textContent = btn.id === 'saveBtn' ? 'Save' : 'Save & Close';
            }
        });
    }
}

function collectRegulatoryThemeFormData() {
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
    const shortNameValue = document.getElementById('shortName')?.value || '';
    const descValue = document.getElementById('description')?.value || '';
    const statusValue = document.getElementById('BUDGStatus')?.value;

    // Build payload with correct field names matching RegulatoryTheme model
    const data = {
        primaryName: getStringValue(nameValue),
        description: getStringValue(descValue),
        parentId: getNumericValue(parentIdValue),
        refNumber: getStringValue(refValue),
        shortName: getStringValue(shortNameValue),
        statusId: getNumericValue(statusValue),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1,
        lastUpdateUserId: getCurrentUserId()
    };

    return data;
}

function validateRegulatoryThemeForm(data) {
    let isValid = true;

    // Clear previous errors
    document.querySelectorAll('.field-error').forEach(error => {
        error.style.display = 'none';
        error.textContent = '';
    });

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

    // Validate Status
    if (!data.statusId) {
        showFieldError('BUDGStatusError', 'BUDG Status is required');
        isValid = false;
    }

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
    if (confirm('Are you sure you want to close? Any unsaved changes will be lost.')) {
        window.location.href = 'index.html';
    }
}

async function initRegulatoryThemeFormDropdowns() {
    try {
        const apiService = new ApiService();

        // Load status dropdown
        const statusList = await apiService.getStatusList();

        // Fill dropdown
        fillSelect('BUDGStatus', statusList, 'primaryname');

    } catch (error) {
        // Show database connection error
        showDatabaseConnectionError();
    }
}

function fillSelect(selectId, data, valueField) {
    const select = document.getElementById(selectId);
    if (!select) {
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
            if (index === 0 && selectId === 'BUDGStatus') {
                option.selected = true;
            }
        });
    } else {
        // Add placeholder if no data
        const placeholderOption = document.createElement('option');
        placeholderOption.value = "";
        placeholderOption.textContent = "Please select";
        select.appendChild(placeholderOption);
    }
}

function showDatabaseConnectionError() {
    // Show error message to user
    const errorMessage = 'Error: Database connection failed. Please check your connection and try again.';
    if (typeof window.showNotification === 'function') { window.showNotification(errorMessage, 'error'); } else { alert(errorMessage); }

    // Disable form elements
    const formElements = document.querySelectorAll('select, input, button');
    formElements.forEach(element => {
        element.disabled = true;
    });

    // Show error in dropdowns
    const dropdowns = ['BUDGStatus'];
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
let parentRegulatoryThemes = [];
let filteredParentRegulatoryThemes = [];

function initParentPicker() {
    const parentNameInput = document.getElementById('parentName');
    const parentDropdownToggle = document.getElementById('parentDropdownToggle');
    const parentClearSelection = document.getElementById('parentClearSelection');
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

    // Clear selected parent
    if (parentClearSelection) {
        parentClearSelection.addEventListener('click', function(e) {
            e.stopPropagation();
            clearParentSelection();
        });
    }

    // Search functionality disabled for creation pages
    // parentSearchInput.addEventListener('input', function() {
    //     filterParentRegulatoryThemes(this.value);
    // });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent regulatory themes
    loadParentRegulatoryThemes();
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
    // Disable search input for creation pages
    if (parentSearchInput) {
        parentSearchInput.style.display = 'none';
    }
    // Show all items without filtering
    filteredParentRegulatoryThemes = [...parentRegulatoryThemes];
    renderParentRegulatoryThemes();
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

async function loadParentRegulatoryThemes() {
    try {
        const response = await fetch('/api/regulatory-theme/parent-picker');
        if (response.ok) {
            const themes = await response.json();
            if (themes && Array.isArray(themes)) {
                parentRegulatoryThemes = themes;
                filteredParentRegulatoryThemes = [...themes];
                renderParentRegulatoryThemes();
            }
        }
    } catch (error) {
        console.error('Failed to load parent regulatory themes:', error);
        // Fallback to empty array
        parentRegulatoryThemes = [];
        filteredParentRegulatoryThemes = [];
    }
}

function filterParentRegulatoryThemes(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredParentRegulatoryThemes = [...parentRegulatoryThemes];
    } else {
        filteredParentRegulatoryThemes = parentRegulatoryThemes.filter(theme =>
            theme.primaryname?.toLowerCase().includes(term) ||
            theme.description?.toLowerCase().includes(term) ||
            theme.refnumber?.toLowerCase().includes(term)
        );
    }

    renderParentRegulatoryThemes();
}

function renderParentRegulatoryThemes() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    if (filteredParentRegulatoryThemes.length === 0) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">No regulatory themes found</div>';
        return;
    }

    filteredParentRegulatoryThemes.forEach(theme => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${theme.primaryname || 'Unnamed Theme'}</div>
            <div class="searchable-dropdown-item-description">${theme.description || theme.refnumber || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectParentRegulatoryTheme(theme);
        });

        parentDropdownList.appendChild(item);
    });
}

function selectParentRegulatoryTheme(theme) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = theme.primaryname;
        parentIdInput.value = theme.id;
    }

    closeParentDropdown();
}

function clearParentSelection() {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');
    if (parentNameInput) parentNameInput.value = '';
    if (parentIdInput) parentIdInput.value = '';
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
        background-color: #10b981;
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

function showStyledError(message) {
    // Create a temporary error message with red styling
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
        max-width: 400px;
        word-wrap: break-word;
    `;
    errorDiv.innerHTML = message.replace(/\n/g, '<br>');

    document.body.appendChild(errorDiv);

    setTimeout(() => {
        errorDiv.remove();
    }, 5000);
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
        collectRegulatoryThemeFormData,
        validateRegulatoryThemeForm,
        savePage,
        showSuccess,
        showError,
        getCurrentUserId,
        fetchCurrentUser
    };
}

