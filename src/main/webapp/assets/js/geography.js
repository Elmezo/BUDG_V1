// Geography Page JavaScript
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
                facetId: 'Geography',
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
                objectType: 'Geography',
                fieldId: 'geographySegment',
                errorId: 'geographySegmentError'
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
        const payload = collectGeographyFormData();

        // Validate form
        if (!validateGeographyForm(payload)) {
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

        // Make API call
        const apiService = new ApiService();
        const response = await apiService.post('/geography', payload);

        if (response && response.success) {
            showSuccess(I18n.t('createPage.message.geographySaved'));

            // Get the ID from the response
            const geographyId = response.data?.id || response.id;
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues && geographyId != null) {
                try {
                    await window.customFieldsContext.saveValues(geographyId);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }

            if (closeAfterSave) {
                // Show message for 2 seconds then go to view page with ID
                setTimeout(() => {
                    window.location.href = `/view/geography/${geographyId}`;
                }, 2000);
            } else {
                // For regular Save, just show the success message (already shown above)
                // Reset form for new entry
                setTimeout(() => {
                    document.getElementById('name').value = '';
                    document.getElementById('description').value = '';
                    // Reset parent selection
                    clearParentSelection();
                }, 2000);
            }
        } else {
            const serverMsg = response?.error || response?.message;
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Geographies') : null;
            if (errInfo) {
                showStyledError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') document.getElementById('name')?.focus();
            } else {
                showStyledError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Geographies' }));
            }
        }

    } catch (error) {
        const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Geographies') : null;
        if (errInfo) {
            showStyledError(I18n.t(errInfo.key, errInfo.params));
            if (errInfo.focus === 'name') document.getElementById('name')?.focus();
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

function collectGeographyFormData() {
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
    const descValue = document.getElementById('description')?.value || '';

    // Build payload with correct field names matching Geography model
    const data = {
        primaryName: getStringValue(nameValue),
        description: getStringValue(descValue),
        parentId: getNumericValue(parentIdValue),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1,
        lastUpdateUserId: getCurrentUserId()
    };

    return data;
}

function validateGeographyForm(data) {
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

// Parent Picker Functions
let parentGeographies = [];
let filteredParentGeographies = [];

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

    // Search functionality enabled for creation pages
    parentSearchInput.addEventListener('input', function() {
        filterParentGeographies(this.value);
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent geographies
    loadParentGeographies();
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
    // Enable search input for creation pages
    if (parentSearchInput) {
        parentSearchInput.style.display = 'block';
        parentSearchInput.value = '';
        parentSearchInput.focus();
    }
    // Show all items without filtering
    filteredParentGeographies = [...parentGeographies];
    renderParentGeographies();
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

async function loadParentGeographies() {
    try {
        const response = await fetch('/api/geography/parent-picker');
        if (response.ok) {
            const geographies = await response.json();
            if (geographies && Array.isArray(geographies)) {
                parentGeographies = geographies;
                filteredParentGeographies = [...geographies];
                renderParentGeographies();
            }
        }
    } catch (error) {
        console.error('Failed to load parent geographies:', error);
        // Fallback to empty array
        parentGeographies = [];
        filteredParentGeographies = [];
    }
}

function filterParentGeographies(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredParentGeographies = [...parentGeographies];
    } else {
        filteredParentGeographies = parentGeographies.filter(geography =>
            geography.primaryname?.toLowerCase().includes(term) ||
            geography.description?.toLowerCase().includes(term)
        );
    }

    renderParentGeographies();
}

function renderParentGeographies() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    if (filteredParentGeographies.length === 0) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">No geographies found</div>';
        return;
    }

    filteredParentGeographies.forEach(geography => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${geography.primaryName || 'Unnamed Geography'}</div>
            <div class="searchable-dropdown-item-description">${geography.description || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectParentGeography(geography);
        });

        parentDropdownList.appendChild(item);
    });
}

function selectParentGeography(geography) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = geography.primaryName;
        parentIdInput.value = geography.id;
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
        collectGeographyFormData,
        validateGeographyForm,
        savePage,
        showSuccess,
        showError,
        getCurrentUserId,
        fetchCurrentUser
    };
}

