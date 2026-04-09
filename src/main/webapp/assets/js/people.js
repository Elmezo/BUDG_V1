// People Page Specific JavaScript
function _toast(msg, type) { if (typeof window.showNotification === 'function') { window.showNotification(msg, type || 'error'); } else { alert(msg); } }
document.addEventListener('DOMContentLoaded', function() {
    initFormValidation();
    initRichTextEditor();
    initThemeSync();
    initOrgUnitDropdown();
    initRolesDropdown();
    
    // Initialize custom fields
    initializeCustomFields();
});

// Initialize custom fields
async function initializeCustomFields() {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'People',
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
}


// Theme synchronization for People page
function initThemeSync() {
    // Listen for theme change events
    window.addEventListener('themeChanged', function(e) {
        updateThemeDependentComponents(e.detail.theme);
    });

    // Get current theme and update components
    const currentTheme = window.BUDG.theme.getCurrentTheme();
    updateThemeDependentComponents(currentTheme);
}

// Update components based on theme
function updateThemeDependentComponents(theme) {
    // This function silently handles theme updates
    // Could be extended to update specific UI elements based on theme
}

// ------------------- Form Validation -------------------
function initFormValidation() {
    const form = document.querySelector('.content-body');
    if (!form) {
        return;
    }

    // Get all form inputs
    const inputs = form.querySelectorAll('input, select, textarea');

    // Add validation events to each input
    inputs.forEach((input, index) => {
        input.addEventListener('blur', validateField);
        input.addEventListener('input', function(event) {
            clearFieldError(event.target);
        });
    });

    // Add event listener for save button
    const saveBtn = document.getElementById('saveBtn');
    if (saveBtn) {
        saveBtn.addEventListener('click', () => validateForm(inputs));
    }

    // Add event listener for save and close button
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', () => validateFormAndClose(inputs));
    }

    // Add event listener for cancel/close button
    const closeBtn = document.getElementById('closeBtn');
    if (closeBtn) {
        closeBtn.addEventListener('click', handleCloseButton);
    }

    // Validate individual field on blur event
    function validateField(event) {
        const field = event.target;
        const label = field.previousElementSibling;

        // Check if field has a proper label
        if (!label || !label.classList.contains('form-label')) {
            return true;
        }

        // Check if field is required and empty
        const isRequired = label.querySelector('.required');
        if (isRequired && !field.value.trim()) {
            showFieldError(field, I18n.t('createPage.message.required'));
            return false;
        }

        // Validate email format if field is email type
        if (field.type === 'email' && field.value.trim()) {
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(field.value)) {
                showFieldError(field, I18n.t('createPage.message.emailRequired'));
                return false;
            }
        }

        // Validate password confirmation
        if (field.id === 'confirmPassword') {
            const passwordField = document.getElementById('passwordInput');
            if (passwordField && field.value !== passwordField.value) {
                showFieldError(field, I18n.t('label.confirmPassword') + ' ' + I18n.t('createPage.message.required'));
                return false;
            }
        }

        // Clear any previous errors if validation passes
        clearFieldError(field);
        return true;
    }

    // Display field error message
    function showFieldError(field, message) {
        clearFieldError(field);

        // Style field to indicate error
        field.style.borderColor = 'var(--required-color)';

        // Create and append error message element
        const errorDiv = document.createElement('div');
        errorDiv.className = 'field-error';
        errorDiv.textContent = message;
        errorDiv.style.color = 'var(--required-color)';
        errorDiv.style.fontSize = '0.75rem';
        errorDiv.style.marginTop = '0.25rem';

        field.parentNode.appendChild(errorDiv);
    }

    // Remove error styling and message from field
    function clearFieldError(field) {
        field.style.borderColor = '';
        const errorDiv = field.parentNode.querySelector('.field-error');
        if (errorDiv) {
            errorDiv.remove();
        }
    }

    // Validate entire form
    function validateForm(inputs) {
        let isValid = true;

        // Validate each field
        inputs.forEach((input, index) => {
            if (!validateField({ target: input })) {
                isValid = false;
            }
        });

        // If all valid, save data
        if (isValid) {
            savePersonDataAndClose();
        }
    }

    // Validate form and close if valid
    function validateFormAndClose(inputs) {
        let isValid = true;

        // Validate each field
        inputs.forEach((input, index) => {
            if (!validateField({ target: input })) {
                isValid = false;
            }
        });

        // If all valid, save data and close
        if (isValid) {
            savePersonDataAndClose();
        }
    }

    // Handle close button click with confirmation if data exists
    function handleCloseButton() {
        const hasData = checkIfFormHasData();
        if (hasData) {
            const confirmClose = confirm('You have unsaved changes. Are you sure you want to cancel without saving?');
            if (confirmClose) {
                window.location.href = 'index.html';
            }
        } else {
            window.location.href = 'index.html';
        }
    }
}

// Check if form has any data entered
function checkIfFormHasData() {
    const firstNameInput = document.getElementById('firstNameInput');
    const lastNameInput = document.getElementById('lastNameInput');
    const emailInput = document.getElementById('emailInput');
    const descriptionInput = document.getElementById('descriptionInput');
    const functionNameInput = document.getElementById('functionNameInput');
    const functionDescriptionInput = document.getElementById('functionDescriptionInput');

    return (firstNameInput && firstNameInput.value.trim()) ||
        (lastNameInput && lastNameInput.value.trim()) ||
        (emailInput && emailInput.value.trim()) ||
        (descriptionInput && descriptionInput.value.trim()) ||
        (functionNameInput && functionNameInput.value.trim()) ||
        (functionDescriptionInput && functionDescriptionInput.value.trim());
}

// Save person data to API
async function savePersonData() {
    // Sync advanced rich text editor content to textareas before saving
    if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
        window.syncAdvancedRichTextToTextarea('descriptionInput');
        window.syncAdvancedRichTextToTextarea('functionDescriptionInput');
    }

    try {
        // Get required field values
        const statusSelect = document.getElementById('statusSelect');
        const orgUnitDisplay = document.getElementById('orgUnitSelectDisplay');
        const firstNameInput = document.getElementById('firstNameInput');
        const lastNameInput = document.getElementById('lastNameInput');
        const emailInput = document.getElementById('emailInput');

        // Debug: Log all field values
        console.log('Form validation - Field values:');
        console.log('Status:', statusSelect?.value);
        console.log('Org Unit:', orgUnitDisplay?.value, 'ID:', orgUnitDisplay?.getAttribute('data-org-unit-id'));
        console.log('First Name:', firstNameInput?.value);
        console.log('Last Name:', lastNameInput?.value);
        console.log('Email:', emailInput?.value);

        // Validate required fields
        if (!statusSelect || !statusSelect.value) {
            _toast((window.I18n && window.I18n.t('people.messages.budgStatusRequired')) || 'Please select a BUDG Status (required field)', 'error');
            statusSelect?.focus();
            return;
        }

        if (!orgUnitDisplay || !orgUnitDisplay.value.trim() || !orgUnitDisplay.getAttribute('data-org-unit-id')) {
            _toast((window.I18n && window.I18n.t('people.messages.orgUnitRequired')) || 'Please select an Org Unit (required field)', 'error');
            orgUnitDisplay?.focus();
            return;
        }

        if (!firstNameInput || !firstNameInput.value.trim()) {
            _toast((window.I18n && window.I18n.t('people.messages.firstNameRequired')) || 'First Name is required', 'error');
            firstNameInput?.focus();
            return;
        }

        if (!lastNameInput || !lastNameInput.value.trim()) {
            _toast((window.I18n && window.I18n.t('people.messages.lastNameRequired')) || 'Last Name is required', 'error');
            lastNameInput?.focus();
            return;
        }

        if (!emailInput || !emailInput.value.trim()) {
            _toast((window.I18n && window.I18n.t('people.messages.emailRequired')) || 'Email is required', 'error');
            emailInput?.focus();
            return;
        }
        

        // Client-side uniqueness check for Email and Name/Reference if available
        try {
            if (typeof window.BUDG_API_SERVICE?.getPeople === 'function') {
                const list = await window.BUDG_API_SERVICE.getPeople();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                const emailVal = (emailInput?.value || '').trim().toLowerCase();
                if (emailVal) {
                    const emailClash = rows.some(r => String(r.email || r.Email || '').trim().toLowerCase() === emailVal);
                    if (emailClash) { _toast((window.I18n && window.I18n.t('common.messages.duplicateEmail')) || 'Error: Email already exists. Email must be unique to distinguish users and avoid account conflicts.', 'error'); emailInput?.focus(); return; }
                }
            }
        } catch (_) { /* rely on server-side validation if list endpoint not present */ }

        // Get selected system role ID directly from dropdown
        const selectedRoleId = document.getElementById('profileSelect')?.value;
        const systemRoleId = selectedRoleId ? parseInt(selectedRoleId) : null;

        if (!systemRoleId) {
            _toast((window.I18n && window.I18n.t('people.messages.profileRequired')) || 'Profile is required', 'error');
            document.getElementById('profileSelect')?.focus();
            return;
        }

        // Prepare data object for API
        const personData = {
            status_id: parseInt(statusSelect.value),
            org_unit_id: orgUnitDisplay.getAttribute('data-org-unit-id') ? parseInt(orgUnitDisplay.getAttribute('data-org-unit-id')) : null,
            first_name: firstNameInput.value.trim(),
            last_name: lastNameInput.value.trim(),
            email: emailInput?.value?.trim() || '',
            password: document.getElementById('passwordInput')?.value || '',
            description: document.getElementById('descriptionInput')?.value?.trim() || '',
            function_name: document.getElementById('functionNameInput')?.value?.trim() || '',
            function_description: document.getElementById('functionDescriptionInput')?.value?.trim() || '',
            system_role: systemRoleId, // Send system role ID instead of profile name
            employment_type: 1, // Default value: 1
            lifecycle: 1, // Default value: 1
        };

        // Debug: Log the data being sent to API
        console.log('Sending person data to API:', personData);

        // Call API to create person
        if (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.createPerson === 'function') {
            try {
                console.log('Calling API createPerson...');
                const response = await window.BUDG_API_SERVICE.createPerson(personData);
                // API returns the created person data directly, not wrapped in success object
                const responseKeys = Object.keys(response || {});
                const responseSummary = {
                    hasId: !!(response?.id || response?.ID),
                    hasSuccess: !!(response?.success || response?.status === 'success'),
                    keys: responseKeys,
                    id: response?.id || response?.ID || null
                };
                console.log('API response summary:', responseSummary);
                
                // Check for various success indicators
                if (response && (response.id || response.ID || response.data?.id || response.data?.ID || response.success === true || response.status === 'success' || response.message === 'success')) {
                    // Get the ID from the response — handle both flat and {success,data} envelope formats
                    const id = response?.id || response?.ID || response?.data?.id || response?.data?.ID || response?.personId || response?.insertId || response?.createdId;
                    
                    // Save custom fields if context exists
                    if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
                        try {
                            await window.customFieldsContext.saveValues(id);
                            console.log('✅ Custom fields saved successfully');
                        } catch (error) {
                            console.error('Error saving custom fields:', error);
                        }
                    }
                    
                    if (id) {
                        _toast((window.I18n && window.I18n.t('people.messages.personSavedRedirect')) || 'Person saved successfully! Redirecting to person view...', 'success');
                        setTimeout(() => {
                            window.location.href = `/view/people/${id}`;
                        }, 1000);
                        return;
                    }
                    
                    _toast((window.I18n && window.I18n.t('createPage.message.personSaved')) || 'Person saved successfully!', 'success');
                    clearForm();
                } else if (response && response.message) {
                    _toast('Person save result: ' + response.message, 'info');
                    if (response.message.toLowerCase().includes('success')) {
                        clearForm();
                    }
                } else {
                    console.error('Unexpected API response format:', response);
                    _toast('Failed to save person: ' + (response?.message || 'Unknown error - check console for details'), 'error');
                }
            } catch (error) {
                console.error('Error creating person:', error);
                const errorMessage = error?.body?.message || error?.message || 'Unknown error - check console for details';
                _toast((window.I18n && window.I18n.t('createPage.message.errorSaving', { error: errorMessage })) || ('Error saving person: ' + errorMessage), 'error');
            }
        } else {
            _toast((window.I18n && window.I18n.t('people.messages.apiNotAvailable')) || 'API service not available', 'error');
        }

    } catch (error) {
        console.error('Error saving person:', error);
        _toast((window.I18n && window.I18n.t('createPage.message.errorSaving', { error: error.message })) || ('Error saving person: ' + error.message), 'error');
    }
}

// Save person data and close the page
async function savePersonDataAndClose() {
    // Sync advanced rich text editor content to textareas before saving
    if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
        window.syncAdvancedRichTextToTextarea('descriptionInput');
        window.syncAdvancedRichTextToTextarea('functionDescriptionInput');
    }

    try {
        // Get required field values
        const statusSelect = document.getElementById('statusSelect');
        const orgUnitDisplay = document.getElementById('orgUnitSelectDisplay');
        const firstNameInput = document.getElementById('firstNameInput');
        const lastNameInput = document.getElementById('lastNameInput');
        const emailInput = document.getElementById('emailInput');

        // Validate required fields
        if (!statusSelect || !statusSelect.value) {
            _toast((window.I18n && window.I18n.t('people.messages.budgStatusRequired')) || 'Please select a BUDG Status', 'error');
            statusSelect?.focus();
            return;
        }

        if (!orgUnitDisplay || !orgUnitDisplay.value.trim() || !orgUnitDisplay.getAttribute('data-org-unit-id')) {
            _toast((window.I18n && window.I18n.t('people.messages.orgUnitRequired')) || 'Please select an Org Unit', 'error');
            orgUnitDisplay?.focus();
            return;
        }

        if (!firstNameInput || !firstNameInput.value.trim()) {
            _toast((window.I18n && window.I18n.t('people.messages.firstNameRequired')) || 'First Name is required', 'error');
            firstNameInput?.focus();
            return;
        }

        if (!lastNameInput || !lastNameInput.value.trim()) {
            _toast((window.I18n && window.I18n.t('people.messages.lastNameRequired')) || 'Last Name is required', 'error');
            lastNameInput?.focus();
            return;
        }

        if (!emailInput || !emailInput.value.trim()) {
            _toast((window.I18n && window.I18n.t('people.messages.emailRequired')) || 'Email is required', 'error');
            emailInput?.focus();
            return;
        }

        // Get selected system role ID directly from dropdown
        const selectedRoleId = document.getElementById('profileSelect')?.value;
        const systemRoleId = selectedRoleId ? parseInt(selectedRoleId) : null;

        if (!systemRoleId) {
            _toast((window.I18n && window.I18n.t('people.messages.profileRequired')) || 'Profile is required', 'error');
            document.getElementById('profileSelect')?.focus();
            return;
        }

        // Prepare data object for API
        const personData = {
            status_id: parseInt(statusSelect.value),
            org_unit_id: orgUnitDisplay.getAttribute('data-org-unit-id') ? parseInt(orgUnitDisplay.getAttribute('data-org-unit-id')) : null,
            first_name: firstNameInput.value.trim(),
            last_name: lastNameInput.value.trim(),
            email: emailInput?.value?.trim() || '',
            password: document.getElementById('passwordInput')?.value || '',
            description: document.getElementById('descriptionInput')?.value?.trim() || '',
            function_name: document.getElementById('functionNameInput')?.value?.trim() || '',
            function_description: document.getElementById('functionDescriptionInput')?.value?.trim() || '',
            system_role: systemRoleId, // Send system role ID instead of profile name
            employment_type: 1, // Default value: 1
            lifecycle: 1, // Default value: 1
        };

        // Call API to create person
        if (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.createPerson === 'function') {
            try {
                const response = await window.BUDG_API_SERVICE.createPerson(personData);
                
                // API returns the created person data directly, not wrapped in success object
                const responseKeys = Object.keys(response || {});
                const responseSummary = {
                    hasId: !!(response?.id || response?.ID),
                    hasSuccess: !!(response?.success || response?.status === 'success'),
                    keys: responseKeys,
                    id: response?.id || response?.ID || null
                };
                console.log('API response summary:', responseSummary);
                
                // Check for various success indicators
                if (response && (response.id || response.ID || response.data?.id || response.data?.ID || response.success === true || response.status === 'success' || response.message === 'success')) {
                    // Get the ID from the response — handle both flat and {success,data} envelope formats
                    const id = response?.id || response?.ID || response?.data?.id || response?.data?.ID || response?.personId || response?.insertId || response?.createdId;
                    
                    // Save custom fields if context exists
                    if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
                        try {
                            await window.customFieldsContext.saveValues(id);
                            console.log('✅ Custom fields saved successfully');
                        } catch (error) {
                            console.error('Error saving custom fields:', error);
                        }
                    }
                    
                    if (id) {
                        _toast((window.I18n && window.I18n.t('people.messages.personSavedRedirect')) || 'Person saved successfully! Redirecting to person view...', 'success');
                        setTimeout(() => {
                            window.location.href = `/view/people/${id}`;
                        }, 1000);
                    } else {
                        _toast((window.I18n && window.I18n.t('createPage.message.personSaved')) || 'Person saved successfully!', 'success');
                        setTimeout(() => {
                            window.location.href = 'people.html';
                        }, 1000);
                    }
                } else if (response && response.message) {
                    _toast('Person save result: ' + response.message, 'info');
                    if (response.message.toLowerCase().includes('success')) {
                        const id = response?.id || response?.ID || response?.data?.id || response?.data?.ID || response?.personId || response?.insertId || response?.createdId;
                        if (id) {
                            setTimeout(() => {
                                window.location.href = `/view/people/${id}`;
                            }, 1000);
                        } else {
                            setTimeout(() => {
                                window.location.href = 'people.html';
                            }, 1000);
                        }
                    }
                } else {
                    const serverMsg = response?.error || response?.message;
                    const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'People') : null;
                    if (errInfo) {
                        _toast(I18n.t(errInfo.key, errInfo.params), 'error');
                        if (errInfo.focus === 'email') document.getElementById('emailInput')?.focus(); else if (errInfo.focus === 'name') document.getElementById('firstNameInput')?.focus();
                    } else {
                        _toast(I18n.t('createPage.message.failedToSaveWithHint', { facet: 'People' }), 'error');
                    }
                }
            } catch (error) {
                console.error('Error creating person:', error);
                const serverMessage = error?.body?.error || error?.body?.message || error?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'People') : null;
                if (errInfo) {
                    _toast(I18n.t(errInfo.key, errInfo.params), 'error');
                    if (errInfo.focus === 'email') document.getElementById('emailInput')?.focus(); else if (errInfo.focus === 'name') document.getElementById('firstNameInput')?.focus();
                } else {
                    _toast(I18n.t('createPage.message.errorSaving', { error: serverMessage || I18n.t('createPage.message.unknownError') }), 'error');
                }
            }
        } else {
            _toast((window.I18n && window.I18n.t('people.messages.apiNotAvailable')) || 'API service not available', 'error');
        }

    } catch (error) {
        console.error('Error saving person:', error);
        const serverMessage = error?.message || (error?.body && (error.body.error || error.body.message));
        const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'People') : null;
        if (errInfo) {
            _toast(I18n.t(errInfo.key, errInfo.params), 'error');
            if (errInfo.focus === 'email') document.getElementById('emailInput')?.focus(); else if (errInfo.focus === 'name') document.getElementById('firstNameInput')?.focus();
        } else {
            _toast(I18n.t('createPage.message.errorSaving', { error: serverMessage || I18n.t('createPage.message.unknownError') }), 'error');
        }
    }
}

// Clear form inputs
function clearForm() {
    // Get all input elements
    const firstNameInput = document.getElementById('firstNameInput');
    const lastNameInput = document.getElementById('lastNameInput');
    const emailInput = document.getElementById('emailInput');
    const descriptionInput = document.getElementById('descriptionInput');
    const functionNameInput = document.getElementById('functionNameInput');
    const functionDescriptionInput = document.getElementById('functionDescriptionInput');
    const passwordInput = document.getElementById('passwordInput');
    const confirmPassword = document.getElementById('confirmPassword');

    // Clear input values
    if (firstNameInput) firstNameInput.value = '';
    if (lastNameInput) lastNameInput.value = '';
    if (emailInput) emailInput.value = '';
    if (descriptionInput) descriptionInput.value = '';
    if (functionNameInput) functionNameInput.value = '';
    if (functionDescriptionInput) functionDescriptionInput.value = '';
    if (passwordInput) passwordInput.value = '';
    if (confirmPassword) confirmPassword.value = '';

    // Reset profile select to default
    const profileSelect = document.getElementById('profileSelect');
    if (profileSelect) {
        profileSelect.value = 'Standard';
    }

    // Reset org unit dropdown
    const orgUnitDisplay = document.getElementById('orgUnitSelectDisplay');
    if (orgUnitDisplay) {
        orgUnitDisplay.value = '';
        orgUnitDisplay.removeAttribute('data-org-unit-id');
    }
}

// Initialize rich text editor - wire up advanced editor buttons
function initRichTextEditor() {
    // Wire up advanced rich text editor for description
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('descriptionInput', showDescriptionEditorBtn);
        });
    }

    // Wire up advanced rich text editor for function description
    const showFunctionDescriptionEditorBtn = document.getElementById('showFunctionDescriptionEditorBtn');
    if (showFunctionDescriptionEditorBtn) {
        showFunctionDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('functionDescriptionInput', showFunctionDescriptionEditorBtn);
        });
    }
}

// ------------------- Roles Dropdown -------------------
async function initRolesDropdown() {
    const profileSelect = document.getElementById('profileSelect');
    if (!profileSelect) {
        console.error('Profile select element not found');
        return;
    }

    try {
        // Clear existing options
        profileSelect.innerHTML = '';
        
        // Add loading option
        profileSelect.innerHTML = '<option value="">Loading roles...</option>';
        
        // Fetch roles from API (same as edit page)
        const response = await window.BUDG_API_SERVICE.getRolesList();
        const roles = Array.isArray(response) ? response : (response.data || []);
        
        // Clear loading option
        profileSelect.innerHTML = '';
        
        // Add default option
        profileSelect.innerHTML = '<option value="">Select Profile...</option>';
        
        // Add role options
        roles.forEach(role => {
            const option = document.createElement('option');
            option.value = role.id;
            option.textContent = role.primaryname || role.name;
            profileSelect.appendChild(option);
        });
        
        console.log('Roles loaded successfully:', roles);
    } catch (error) {
        console.error('Failed to load roles:', error);
        profileSelect.innerHTML = '<option value="">Error loading roles</option>';
    }
}

// ------------------- Org Unit Select Dropdown -------------------
function initOrgUnitDropdown() {
    const dropdownContainer = document.getElementById('orgUnitSelectDropdown');
    const dropdownDisplay = document.getElementById('orgUnitSelectDisplay');
    const dropdownArrow = document.getElementById('orgUnitSelectArrow');
    const dropdownMenu = document.getElementById('orgUnitSelectMenu');
    const dropdownSearch = document.getElementById('orgUnitSelectSearch');
    const dropdownItems = document.getElementById('orgUnitSelectItems');

    // Check if all required elements exist
    if (!dropdownContainer || !dropdownDisplay || !dropdownArrow || !dropdownMenu || !dropdownSearch || !dropdownItems) {
        console.error('Org Unit select dropdown elements not found');
        return;
    }

    let allOrgUnits = [];
    let selectedOrgUnit = null;
    let isDropdownOpen = false;
    let currentFocusIndex = -1;

    // Toggle dropdown open/closed
    function toggleDropdown() {
        if (isDropdownOpen) {
            closeDropdown();
        } else {
            openDropdown();
        }
    }

    // Open dropdown and load data if needed
    function openDropdown() {
        isDropdownOpen = true;
        dropdownContainer.classList.add('active');
        dropdownMenu.classList.add('show');

        // Focus on search input when dropdown opens
        setTimeout(() => {
            dropdownSearch.focus();
        }, 100);

        // Load org units if not already loaded
        if (allOrgUnits.length === 0) {
            loadOrgUnits();
        } else {
            populateDropdownItems(allOrgUnits);
        }
    }

    // Close dropdown and reset state
    function closeDropdown() {
        isDropdownOpen = false;
        dropdownContainer.classList.remove('active');
        dropdownMenu.classList.remove('show');
        dropdownSearch.value = '';
        currentFocusIndex = -1;
        clearDropdownItems();
    }

    // Load org units from API
    async function loadOrgUnits() {
        try {
            showDropdownLoading();

            // Wait for API service to be available
            let attempts = 0;
            const maxAttempts = 10;
            while (typeof window.BUDG_API_SERVICE?.getOrgUnits !== 'function' && attempts < maxAttempts) {
                await new Promise(resolve => setTimeout(resolve, 100));
                attempts++;
            }

            if (typeof window.BUDG_API_SERVICE?.getOrgUnits !== 'function') {
                throw new Error('API service not available');
            }

            // Fetch org units from API
            const data = await window.BUDG_API_SERVICE.getOrgUnits();
            allOrgUnits = data || [];
            populateDropdownItems(allOrgUnits);

        } catch (error) {
            console.error('Error loading org units:', error);
            showDropdownError('Failed to load organization units');
        }
    }

    // Search org units based on input
    async function searchOrgUnits(searchTerm) {
        try {
            if (!searchTerm.trim()) {
                populateDropdownItems(allOrgUnits);
                return;
            }

            // Use API search if available, otherwise filter locally
            if (typeof window.BUDG_API_SERVICE?.searchOrgUnits === 'function') {
                const data = await window.BUDG_API_SERVICE.searchOrgUnits(searchTerm);
                populateDropdownItems(data || []);
            } else {
                const filtered = allOrgUnits.filter(unit =>
                    unit.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                    unit.description?.toLowerCase().includes(searchTerm.toLowerCase())
                );
                populateDropdownItems(filtered);
            }
        } catch (error) {
            console.error('Error searching org units:', error);
            showDropdownError('Search failed. Please try again.');
        }
    }

    // Populate dropdown with items
    function populateDropdownItems(data) {
        clearDropdownItems();

        if (!data || data.length === 0) {
            showDropdownEmpty();
            return;
        }

        // Create an item for each org unit
        data.forEach((item, index) => {
            const itemElement = createDropdownItem(item, index);
            dropdownItems.appendChild(itemElement);
        });
    }

    // Create a dropdown item element
    function createDropdownItem(item, index) {
        const itemElement = document.createElement('div');
        itemElement.className = 'select-dropdown-item';
        itemElement.dataset.id = item.id;
        itemElement.dataset.name = item.name;
        itemElement.dataset.description = item.description;
        itemElement.dataset.index = index;
        itemElement.tabIndex = 0;

        const fullName = item.name || item.id || 'Unnamed';
        const fullDescription = item.description || 'No description';

        // Create HTML structure for dropdown item
        itemElement.innerHTML = `
            <i class="fas fa-sitemap select-dropdown-item-icon"></i>
            <div class="select-dropdown-item-content">
                <div class="select-dropdown-item-name">${fullName}</div>
                <div class="select-dropdown-item-description">${fullDescription}</div>
            </div>
        `;

        // Add event listeners for selection
        itemElement.addEventListener('click', () => selectOrgUnit(item, itemElement));
        itemElement.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                selectOrgUnit(item, itemElement);
            }
        });

        return itemElement;
    }

    // Handle org unit selection
    function selectOrgUnit(item, itemElement) {
        // Remove previous selection
        const prevSelected = dropdownItems.querySelector('.selected');
        if (prevSelected) {
            prevSelected.classList.remove('selected');
        }

        // Add selection to current item
        itemElement.classList.add('selected');
        selectedOrgUnit = item;

        // Update display value
        dropdownDisplay.value = item.name || item.id;

        // Store the selected org unit ID for form submission
        dropdownDisplay.setAttribute('data-org-unit-id', item.id);

        // Close dropdown after selection
        closeDropdown();
    }

    // Show loading state in dropdown
    function showDropdownLoading() {
        dropdownItems.innerHTML = `
            <div class="select-dropdown-loading">
                <i class="fas fa-spinner"></i>
                Loading organization units...
            </div>
        `;
    }

    // Show error state in dropdown
    function showDropdownError(message) {
        dropdownItems.innerHTML = `
            <div class="select-dropdown-empty">
                <i class="fas fa-exclamation-triangle"></i>
                ${message}
            </div>
        `;
    }

    // Show empty state in dropdown
    function showDropdownEmpty() {
        dropdownItems.innerHTML = `
            <div class="select-dropdown-empty">
                <i class="fas fa-search"></i>
                No organization units found
            </div>
        `;
    }

    // Clear all dropdown items
    function clearDropdownItems() {
        dropdownItems.innerHTML = '';
    }

    // Handle keyboard navigation in dropdown
    function handleKeyboardNavigation(e) {
        if (!isDropdownOpen) return;

        const items = dropdownItems.querySelectorAll('.select-dropdown-item');
        if (items.length === 0) return;

        // Handle arrow keys, enter, and escape
        switch (e.key) {
            case 'ArrowDown':
                e.preventDefault();
                currentFocusIndex = Math.min(currentFocusIndex + 1, items.length - 1);
                focusItem(items[currentFocusIndex]);
                break;
            case 'ArrowUp':
                e.preventDefault();
                currentFocusIndex = Math.max(currentFocusIndex - 1, 0);
                focusItem(items[currentFocusIndex]);
                break;
            case 'Enter':
                e.preventDefault();
                if (currentFocusIndex >= 0 && items[currentFocusIndex]) {
                    const item = items[currentFocusIndex];
                    const itemData = {
                        id: item.dataset.id,
                        name: item.dataset.name,
                        description: item.dataset.description
                    };
                    selectOrgUnit(itemData, item);
                }
                break;
            case 'Escape':
                e.preventDefault();
                closeDropdown();
                break;
        }
    }

    // Focus on a specific dropdown item
    function focusItem(item) {
        if (!item) return;

        // Remove focus from all items
        dropdownItems.querySelectorAll('.select-dropdown-item').forEach(i => {
            i.classList.remove('focused');
        });

        // Add focus to current item
        item.classList.add('focused');
        item.focus();

        // Scroll item into view if needed
        item.scrollIntoView({ block: 'nearest' });
    }

    // Event listeners for dropdown interactions
    dropdownDisplay.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleDropdown();
    });

    dropdownDisplay.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            toggleDropdown();
        }
    });

    dropdownArrow.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleDropdown();
    });

    // Debounced search input handler
    dropdownSearch.addEventListener('input', debounce(function() {
        const searchTerm = this.value.trim();
        searchOrgUnits(searchTerm);
    }, 300));

    dropdownSearch.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            this.value = '';
            populateDropdownItems(allOrgUnits);
            closeDropdown();
        } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            // Let the main keyboard handler deal with navigation
            return;
        }
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (!dropdownContainer.contains(e.target) && e.target !== dropdownSearch) {
            closeDropdown();
        }
    });

    // Handle keyboard navigation
    document.addEventListener('keydown', handleKeyboardNavigation);

    // Debounce function to limit API calls during search
    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func.apply(this, args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }
}

// Load statuses for dropdown from API
async function loadStatuses() {
    const statusSelect = document.getElementById('statusSelect');
    if (!statusSelect) return;

    let attempts = 0;
    const maxAttempts = 10;

    // Wait for API service to be available
    while (typeof window.BUDG_API_SERVICE.getStatusesForDropdown !== 'function' && attempts < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 100));
        attempts++;
    }

    try {
        statusSelect.innerHTML = '<option value="">Loading statuses...</option>';

        // Fetch statuses from API
        const response = await window.BUDG_API_SERVICE.getStatusesForDropdown();

        if (response && Array.isArray(response)) {
            statusSelect.innerHTML = '';

            // Populate dropdown with status options
            response.forEach(status => {
                const option = document.createElement('option');
                option.value = status.id || status.ID;
                option.textContent = status.primaryname || status.PrimaryName || status.name || status.Name;
                statusSelect.appendChild(option);
            });
        } else if (response && response.data && Array.isArray(response.data)) {
            statusSelect.innerHTML = '';

            // Populate dropdown with status options
            response.data.forEach(status => {
                const option = document.createElement('option');
                option.value = status.id || status.ID;
                option.textContent = status.primaryname || status.PrimaryName || status.name || status.Name;
                statusSelect.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading statuses:', error);
    }
}

// Load org units for dropdown from API
async function loadOrgUnits() {
    const orgUnitSelect = document.getElementById('orgUnitSelect');
    if (!orgUnitSelect) return;

    let attempts = 0;
    const maxAttempts = 10;

    // Wait for API service to be available
    while (typeof window.BUDG_API_SERVICE.getPeopleOrgUnits !== 'function' && attempts < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 100));
        attempts++;
    }

    try {
        orgUnitSelect.innerHTML = '<option value="">Loading org units...</option>';

        // Fetch org units from API
        const response = await window.BUDG_API_SERVICE.getPeopleOrgUnits();

        if (response && Array.isArray(response)) {
            orgUnitSelect.innerHTML = '';

            // Add default option
            const defaultOption = document.createElement('option');
            defaultOption.value = '';
            defaultOption.textContent = 'Select an org unit';
            orgUnitSelect.appendChild(defaultOption);

            // Populate dropdown with org unit options
            response.forEach(orgUnit => {
                const option = document.createElement('option');
                option.value = orgUnit.id || orgUnit.ID;
                option.textContent = orgUnit.name || orgUnit.Name || orgUnit.primaryname || orgUnit.PrimaryName;
                orgUnitSelect.appendChild(option);
            });
        } else if (response && response.data && Array.isArray(response.data)) {
            orgUnitSelect.innerHTML = '';

            // Add default option
            const defaultOption = document.createElement('option');
            defaultOption.value = '';
            defaultOption.textContent = 'Select an org unit';
            orgUnitSelect.appendChild(defaultOption);

            // Populate dropdown with org unit options
            response.data.forEach(orgUnit => {
                const option = document.createElement('option');
                option.value = orgUnit.id || orgUnit.ID;
                option.textContent = orgUnit.name || orgUnit.Name || orgUnit.primaryname || orgUnit.PrimaryName;
                orgUnitSelect.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error loading org units:', error);
        orgUnitSelect.innerHTML = '<option value="">Error loading org units</option>';
    }
}

// Fetch roles from API and populate profile select dropdown
async function fetchRoles() {
    try {
        const response = await fetch('/api/roles');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const roles = await response.json();
        const profileSelect = document.getElementById('profileSelect');

        if (profileSelect) {
            profileSelect.innerHTML = ''; // Clear existing options

            // Add each role as an option
            roles.forEach(role => {
                const option = document.createElement('option');
                option.value = role.primaryname;
                option.textContent = role.primaryname;
                profileSelect.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error fetching roles:', error);
    }
}

// Initialize page components after DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    // Load dropdown data after a short delay to ensure API is available
    setTimeout(() => {
        if (!window.BUDG_API_SERVICE) {
            setTimeout(() => {
                loadStatuses();
                loadOrgUnits();
            }, 300);
        } else {
            loadStatuses();
            loadOrgUnits();
        }
    }, 200);
});
