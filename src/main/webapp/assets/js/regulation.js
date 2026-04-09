// Regulation Page JavaScript
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

    // Initialize dropdowns and parent picker
    initRegulationFormDropdowns();
    initParentPicker();
    
    // Initialize custom fields
    initializeCustomFields();

    // Advanced Rich Text Editor toggles
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('description', showDescriptionEditorBtn);
        });
    }
    const showLegalAdviceEditorBtn = document.getElementById('showLegalAdviceEditorBtn');
    if (showLegalAdviceEditorBtn) {
        showLegalAdviceEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('legalAdvice', showLegalAdviceEditorBtn);
        });
    }
    const showAdditionalInfoEditorBtn = document.getElementById('showAdditionalInfoEditorBtn');
    if (showAdditionalInfoEditorBtn) {
        showAdditionalInfoEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('additionalInfo', showAdditionalInfoEditorBtn);
        });
    }
});

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Regulation',
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
                objectType: 'Regulation',
                fieldId: 'regulationSegment',
                errorId: 'regulationSegmentError'
            });
            console.log('Segment field initialized');
        }
    } catch (error) {
        console.error('Error initializing segment field:', error);
    }
}

// Initialize all dropdowns with reference data
async function initRegulationFormDropdowns() {
    try {
        const response = await fetch('/api/regulation/reference-data');
        if (response.ok) {
            const referenceData = await response.json();
            
            // Fill all dropdowns
            fillSelect('complianceLevel', referenceData.regulationComplianceLevel, 'primaryname');
            fillSelect('maturity', referenceData.regulationMaturity, 'primaryname');
            fillSelect('probability', referenceData.regulationProbability, 'primaryname');
            fillSelect('budgStatus', referenceData.regulationStatus, 'primaryname');
            fillSelect('stage', referenceData.regulationStage, 'primaryname');
            fillSelect('accessControl', referenceData.viewing, 'primaryname');
            fillSelect('legalAdviceType', referenceData.legalAdviceTypes, 'primaryname');
            
            console.log('All regulation dropdowns initialized successfully');
        } else {
            console.error('Failed to load reference data');
        }
    } catch (error) {
        console.error('Error loading reference data:', error);
    }
}

// Helper function to fill select elements
function fillSelect(selectId, options, valueField) {
    const select = document.getElementById(selectId);
    if (!select || !options) return;

    // Clear existing options entirely; default to first real value
    select.innerHTML = '';

    // Add new options
    options.forEach(option => {
        const optionElement = document.createElement('option');
        optionElement.value = option.id;
        optionElement.textContent = option[valueField] || option.primaryname || option.name;
        select.appendChild(optionElement);
    });

    // Select first option by default like other creation pages
    if (select.options.length > 0) {
        select.selectedIndex = 0;
    }
}

async function savePage(closeAfterSave) {
    // Sync advanced rich text editor content to textareas before saving
    if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
        window.syncAdvancedRichTextToTextarea('description');
        window.syncAdvancedRichTextToTextarea('legalAdvice');
        window.syncAdvancedRichTextToTextarea('additionalInfo');
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
        const payload = collectRegulationFormData();

        // Validate form
        if (!validateRegulationForm(payload)) {
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

        // Uniqueness checks: Long Name
        try {
            const response = await fetch('/api/regulation');
            const list = await response.json();
            const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];

            const refVal = String(payload.refNumber || '').trim().toLowerCase();

            // Check for duplicate Reference Number if provided (case-insensitive)
            if (refVal && refVal !== '') {
                const refClash = rows.some(r => String(r.refNumber || r.RefNumber || r.ref || '').trim().toLowerCase() === refVal);
                if (refClash) {
                    showStyledError(I18n.t('createPage.message.duplicateRefNumber', {facet: 'Regulations'}));
                    document.getElementById('refNumber')?.focus();
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
        const response = await apiService.post('/regulation', payload);

        if (response && response.success) {
            showSuccess(I18n.t('createPage.message.regulationSaved'));

            // Get the ID from the response
            const regulationId = response.data?.id || response.id;
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues && regulationId != null) {
                try {
                    await window.customFieldsContext.saveValues(regulationId);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }

            if (closeAfterSave) {
                // Show message for 2 seconds then go to view page with ID
                setTimeout(() => {
                    window.location.href = `/view/regulation/${regulationId}`;
                }, 2000);
            } else {
                // For regular Save, just show the success message (already shown above)
                // Reset form for new entry
                setTimeout(() => {
                    resetForm();
                }, 2000);
            }
        } else {
            const serverMsg = response?.error || response?.message;
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Regulations') : null;
            if (errInfo) {
                showStyledError(I18n.t(errInfo.key, errInfo.params));
                if (errInfo.focus === 'name') document.getElementById('longName')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('refNumber')?.focus();
            } else {
                showStyledError(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Regulations' }));
            }
        }

    } catch (error) {
        const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Regulations') : null;
        if (errInfo) {
            showStyledError(I18n.t(errInfo.key, errInfo.params));
            if (errInfo.focus === 'name') document.getElementById('longName')?.focus(); else if (errInfo.focus === 'reference') document.getElementById('refNumber')?.focus();
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

function collectRegulationFormData() {
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

    // Helper function to convert date strings to SQL Date format
    const getDateValue = (value) => {
        if (value && value.trim() !== '') {
            return value; // HTML date input already provides YYYY-MM-DD format
        }
        return null;
    };

    // Get form values
    const longNameValue = document.getElementById('longName')?.value || '';
    const parentIdValue = document.getElementById('parentId')?.value;
    const refNumberValue = document.getElementById('refNumber')?.value || '';
    const descValue = document.getElementById('description')?.value || '';
    const shortNameValue = document.getElementById('shortName')?.value || '';
    const legalAdviceValue = document.getElementById('legalAdvice')?.value || '';
    const additionalInfoValue = document.getElementById('additionalInfo')?.value || '';
    
    // Date values
    const publicationDateValue = document.getElementById('publicationDate')?.value || '';
    const complianceDateValue = document.getElementById('complianceDate')?.value || '';
    const commentsDateValue = document.getElementById('commentsDate')?.value || '';
    const finalisationDateValue = document.getElementById('finalisationDate')?.value || '';
    
    // Dropdown values
    const complianceLevelValue = document.getElementById('complianceLevel')?.value;
    const maturityValue = document.getElementById('maturity')?.value;
    const probabilityValue = document.getElementById('probability')?.value;
    const budgStatusValue = document.getElementById('budgStatus')?.value;
    const stageValue = document.getElementById('stage')?.value;
    const accessControlValue = document.getElementById('accessControl')?.value;
    const legalAdviceTypeValue = document.getElementById('legalAdviceType')?.value;

    // Build payload with correct field names matching Regulation model
    const data = {
        primaryName: getStringValue(longNameValue),
        description: getStringValue(descValue),
        parentId: getNumericValue(parentIdValue),
        refNumber: getStringValue(refNumberValue),
        shortName: getStringValue(shortNameValue),
        legalAdvice: getStringValue(legalAdviceValue),
        additionalInfo: getStringValue(additionalInfoValue),
        
        // Dates
        publicationDate: getDateValue(publicationDateValue),
        complianceDate: getDateValue(complianceDateValue),
        commentsDate: getDateValue(commentsDateValue),
        finalisationDate: getDateValue(finalisationDateValue),
        
        // Foreign keys
        complianceLevelId: getNumericValue(complianceLevelValue),
        regulationMaturityId: getNumericValue(maturityValue),
        regulationProbabilityId: getNumericValue(probabilityValue),
        regulationStatusId: getNumericValue(budgStatusValue),
        regulationStageId: getNumericValue(stageValue),
        isPublic: getNumericValue(accessControlValue),
        legalAdviceTypeId: getNumericValue(legalAdviceTypeValue),
        segmentId: window.segmentField ? window.segmentField.getValue() : 1,
        
        lastUpdateUserId: getCurrentUserId()
    };

    return data;
}

function validateRegulationForm(data) {
    let isValid = true;

    // Clear previous errors
    document.querySelectorAll('.field-error').forEach(error => {
        error.style.display = 'none';
        error.textContent = '';
    });

    // Validate Long Name
    if (!data.primaryName || !data.primaryName.trim()) {
        showFieldError('longNameError', 'Long Name is required');
        isValid = false;
    }

    // Validate Description
    if (!data.description || !data.description.trim()) {
        showFieldError('descriptionError', 'Description is required');
        isValid = false;
    }

    // Validate required dropdowns
    if (!data.complianceLevelId) {
        showFieldError('complianceLevelError', 'Compliance Level is required');
        isValid = false;
    }

    if (!data.regulationMaturityId) {
        showFieldError('maturityError', 'Maturity is required');
        isValid = false;
    }

    if (!data.regulationProbabilityId) {
        showFieldError('probabilityError', 'Probability is required');
        isValid = false;
    }

    if (!data.regulationStatusId) {
        showFieldError('budgStatusError', 'BUDG Status is required');
        isValid = false;
    }

    if (!data.regulationStageId) {
        showFieldError('stageError', 'Stage is required');
        isValid = false;
    }

    if (!data.isPublic) {
        showFieldError('accessControlError', 'Access Control is required');
        isValid = false;
    }

    // Validate required dates
    if (!data.publicationDate) {
        showFieldError('publicationDateError', 'Publication Date is required');
        isValid = false;
    }

    if (!data.complianceDate) {
        showFieldError('complianceDateError', 'Compliance Date is required');
        isValid = false;
    }

    return isValid;
}

function resetForm() {
    // Reset text inputs
    document.getElementById('longName').value = '';
    document.getElementById('refNumber').value = '';
    document.getElementById('description').value = '';
    document.getElementById('shortName').value = '';
    document.getElementById('legalAdvice').value = '';
    document.getElementById('additionalInfo').value = '';
    
    // Reset date inputs
    document.getElementById('publicationDate').value = '';
    document.getElementById('complianceDate').value = '';
    document.getElementById('commentsDate').value = '';
    document.getElementById('finalisationDate').value = '';
    
    // Reset dropdowns to first option
    const dropdowns = ['complianceLevel', 'maturity', 'probability', 'budgStatus', 'stage', 'accessControl', 'legalAdviceType'];
    dropdowns.forEach(id => {
        const select = document.getElementById(id);
        if (select && select.options.length > 0) {
            select.selectedIndex = 0;
        }
    });
    
    // Reset parent selection
    clearParentSelection();
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
let parentRegulations = [];
let filteredParentRegulations = [];

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
    //     filterParentRegulations(this.value);
    // });

    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!e.target.closest('.searchable-dropdown-container')) {
            closeParentDropdown();
        }
    });

    // Load parent regulations
    loadParentRegulations();
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
    filteredParentRegulations = [...parentRegulations];
    renderParentRegulations();
}

function closeParentDropdown() {
    const parentDropdownMenu = document.getElementById('parentDropdownMenu');
    const parentDropdown = document.querySelector('.searchable-dropdown');

    parentDropdownMenu.style.display = 'none';
    parentDropdown.classList.remove('open');
}

async function loadParentRegulations() {
    try {
        const response = await fetch('/api/regulation/parent-picker');
        if (response.ok) {
            const regulations = await response.json();
            if (regulations && Array.isArray(regulations)) {
                parentRegulations = regulations;
                filteredParentRegulations = [...regulations];
                renderParentRegulations();
            }
        }
    } catch (error) {
        console.error('Failed to load parent regulations:', error);
        // Fallback to empty array
        parentRegulations = [];
        filteredParentRegulations = [];
    }
}

function filterParentRegulations(searchTerm) {
    const term = searchTerm.toLowerCase().trim();

    if (term === '') {
        filteredParentRegulations = [...parentRegulations];
    } else {
        filteredParentRegulations = parentRegulations.filter(regulation =>
            regulation.primaryname?.toLowerCase().includes(term) ||
            regulation.description?.toLowerCase().includes(term)
        );
    }

    renderParentRegulations();
}

function renderParentRegulations() {
    const parentDropdownList = document.getElementById('parentDropdownList');
    if (!parentDropdownList) return;

    parentDropdownList.innerHTML = '';

    if (filteredParentRegulations.length === 0) {
        parentDropdownList.innerHTML = '<div class="searchable-dropdown-item">No regulations found</div>';
        return;
    }

    filteredParentRegulations.forEach(regulation => {
        const item = document.createElement('div');
        item.className = 'searchable-dropdown-item';
        item.innerHTML = `
            <div class="searchable-dropdown-item-name">${regulation.primaryname || 'Unnamed Regulation'}</div>
            <div class="searchable-dropdown-item-description">${regulation.description || ''}</div>
        `;

        item.addEventListener('click', function() {
            selectParentRegulation(regulation);
        });

        parentDropdownList.appendChild(item);
    });
}

function selectParentRegulation(regulation) {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');
    const parentClearSelection = document.getElementById('parentClearSelection');

    if (parentNameInput && parentIdInput) {
        parentNameInput.value = regulation.primaryname;
        parentIdInput.value = regulation.id;
        
        // Ensure clear button is visible
        if (parentClearSelection) {
            parentClearSelection.style.display = 'block';
        }
    }

    closeParentDropdown();
}

function clearParentSelection() {
    const parentNameInput = document.getElementById('parentName');
    const parentIdInput = document.getElementById('parentId');
    const parentClearSelection = document.getElementById('parentClearSelection');
    
    if (parentNameInput) parentNameInput.value = '';
    if (parentIdInput) parentIdInput.value = '';
    // Keep X visible even when cleared
    if (parentClearSelection) parentClearSelection.style.display = 'block';
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
        collectRegulationFormData,
        validateRegulationForm,
        savePage,
        showSuccess,
        showError,
        getCurrentUserId,
        fetchCurrentUser
    };
}

