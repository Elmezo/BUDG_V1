// Legal Entity Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    // Get references to all DOM elements
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const cancelBtn = document.getElementById('cancelBtn');

    // Form elements
    const legalEntityForm = document.getElementById('legalEntityForm');
    const nameInput = document.getElementById('nameInput');
    const shortNameInput = document.getElementById('shortNameInput');
    const descriptionInput = document.getElementById('descriptionInput');
    const statusSelect = document.getElementById('statusSelect');
    const viewingSelect = document.getElementById('viewingSelect');
    const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');

    // Initialize with delay to ensure API service is loaded
    setTimeout(() => {
        if (!window.BUDG_API_SERVICE) {
            setTimeout(() => {
                initPage();
            }, 300);
        } else {
            initPage();
        }
    }, 200);

    // Validate that all required elements exist
    if (!nameInput || !shortNameInput || !statusSelect || !viewingSelect) {
        console.error('Required elements not found');
        return;
    }

    // Check if API service is available
    if (!window.BUDG_API_SERVICE) {
        console.error('BUDG_API_SERVICE not found. Make sure api-service.js is loaded.');
        return;
    }

    // Form button event listeners
    if (saveBtn) {
        saveBtn.addEventListener('click', function() {
            saveLegalEntity(true);
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            saveLegalEntity(true);
        });
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', function() {
            cancelForm();
        });
    }

    // Form submission handler
    if (legalEntityForm) {
        legalEntityForm.addEventListener('submit', function(e) {
            e.preventDefault();
            saveLegalEntity(false);
        });
    }

    // Advanced Rich Text Editor toggle
    const showDescriptionEditorBtn = document.getElementById('showDescriptionEditorBtn');
    if (showDescriptionEditorBtn && descriptionInput) {
        showDescriptionEditorBtn.addEventListener('click', function() {
            window.toggleAdvancedRichTextEditor('descriptionInput', showDescriptionEditorBtn);
        });
    }

    // Save legal entity to API
    async function saveLegalEntity(closeAfterSave = false) {
        // Sync advanced rich text editor content to textarea before saving
        if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
            window.syncAdvancedRichTextToTextarea('descriptionInput');
        }

        try {
            // Validate required fields
            if (!nameInput || !nameInput.value.trim()) {
                const m = I18n.t('createPage.message.primaryNameRequired'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                nameInput?.focus();
                return;
            }

            if (!shortNameInput || !shortNameInput.value.trim()) {
                alert(I18n.t('createPage.message.nameRequired'));
                shortNameInput?.focus();
                return;
            }

            // Reference field is not required as it's not in the database schema

            if (!statusSelect || !statusSelect.value) {
                const m = I18n.t('createPage.message.statusRequired'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                statusSelect?.focus();
                return;
            }

            if (!viewingSelect || !viewingSelect.value) {
                const m = I18n.t('createPage.message.viewingRequired'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                viewingSelect?.focus();
                return;
            }
            
            // Validate custom fields
            if (window.customFieldsContext && window.customFieldsContext.validate) {
                const customFieldsValid = window.customFieldsContext.validate();
                if (!customFieldsValid) {
                    return;
                }
            }

            // Client-side uniqueness checks (Long Name, Short Name, Reference)
            try {
                if (typeof window.BUDG_API_SERVICE?.getLegalEntities === 'function') {
                    const list = await window.BUDG_API_SERVICE.getLegalEntities();
                    const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                    
                    const longNameVal = (nameInput.value || '').trim().toLowerCase();
                    const shortNameVal = (shortNameInput.value || '').trim().toLowerCase();
                    
                    // Check for duplicate Long Name
                    const longNameClash = rows.some(r => {
                        const existingLongName = String(r.Name || r.primaryname || r.name || r.longname || '').trim().toLowerCase();
                        return existingLongName === longNameVal;
                    });
                    if (longNameClash) {
                        const m = I18n.t('createPage.message.duplicatePrimaryName', {facet: 'Legal Entities'}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        nameInput.focus();
                        return;
                    }
                    
                    // Check for duplicate Short Name
                    const shortNameClash = rows.some(r => {
                        const existingShortName = String(r.Short_Name || r.short_name || r.shortName || r.shortname || '').trim().toLowerCase();
                        return existingShortName === shortNameVal;
                    });
                    if (shortNameClash) {
                        const m = I18n.t('createPage.message.duplicateName', {facet: 'Legal Entities'}); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        shortNameInput.focus();
                        return;
                    }
                    
                    // Reference field is not in database schema, so no validation needed
                }
            } catch (_) { /* fall back to server-side validation */ }

            // Collect form data
            const formData = {
                longname: nameInput.value.trim(),
                shortname: shortNameInput.value.trim(),
                description: descriptionInput?.value?.trim() || '',
                status: parseInt(statusSelect.value) || 1,
                is_public: parseInt(viewingSelect.value) || 1,
                parent_id: parentLegalEntitySelect?.value ? parseInt(parentLegalEntitySelect.value) : null,
                segmentId: window.segmentField ? window.segmentField.getValue() : 1
            };

            // Show loading state on buttons
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = I18n.t('createPage.message.saving');
                }
            });

            // Call API to save legal entity
            //('Sending form data:', formData);
            //('API endpoint:', window.BUDG_CONFIG?.ENDPOINTS?.LEGAL_ENTITY?.CREATE);
            
            const response = await window.BUDG_API_SERVICE.createLegalEntity(formData);
            //('API response:', response);
            //('Response type:', typeof response);
            //('Response success:', response?.success);
            //('Response data:', response?.data);

            // Handle API response
            if (response && response.success) {
                
                const m = I18n.t('createPage.message.legalEntitySaved'); if (typeof window.showNotification === 'function') { window.showNotification(m, 'success'); } else { alert(m); }
                
                // Get the ID from the response
                const id = response?.data?.id || response?.id || response?.legalEntityId || response?.insertId || response?.createdId;
                
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && id != null) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }

                if (closeAfterSave) {
                    if (id != null) {
                        window.location.href = `/view/LegalEntity/${encodeURIComponent(id)}`;
                    } else {
                        window.location.href = 'index.html';
                    }
                } else {
                    clearForm();
                }
            } else {
                const serverMsg = response?.error || response?.message;
                const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Legal Entities') : null;
                if (errInfo) {
                    const m = I18n.t(errInfo.key, errInfo.params); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    if (errInfo.focus === 'name') nameInput?.focus(); else if (errInfo.focus === 'reference') document.querySelector('[name="reference"]')?.focus();
                } else {
                    const m = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Legal Entities' }); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                }
            }

        } catch (error) {
            console.error('Error saving legal entity:', error);
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Legal Entities') : null;
            if (errInfo) {
                const m = I18n.t(errInfo.key, errInfo.params); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                if (errInfo.focus === 'name') nameInput?.focus(); else if (errInfo.focus === 'reference') document.querySelector('[name="reference"]')?.focus();
            } else {
                let errorMessage = I18n.t('createPage.message.unknownError');
                if (error.message) errorMessage = error.message;
                else if (error.status) errorMessage = I18n.t('createPage.message.serverError', { status: error.status });
                else if (error.name === 'TypeError') errorMessage = I18n.t('createPage.message.networkError');
                const m = I18n.t('createPage.message.errorSaving', { error: errorMessage }); if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            }
        } finally {
            // Reset button states
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn === saveBtn) btn.textContent = 'Save';
                    if (btn === saveAndCloseBtn) btn.textContent = 'Save & Close';
                    if (btn === cancelBtn) btn.textContent = 'Close';
                }
            });
        }
    }

    // Check if form has any data entered
    function checkIfFormHasData() {
        return (nameInput && nameInput.value.trim()) ||
            (shortNameInput && shortNameInput.value.trim()) ||
            (descriptionInput && descriptionInput.value.trim()) ||
            (statusSelect && statusSelect.value) ||
            (viewingSelect && viewingSelect.value) ||
            (parentLegalEntitySelect && parentLegalEntitySelect.value);
    }

    // Handle form cancellation
    function cancelForm() {
        const hasData = checkIfFormHasData();
        if (hasData) {
            const confirmClose = confirm('You have unsaved changes. Are you sure you want to cancel without saving?');
            if (confirmClose) {
                window.location.href = './index.html';
            }
        } else {
            window.location.href = './index.html';
        }
    }

    // Clear form inputs
    function clearForm() {
        if (nameInput) nameInput.value = '';
        if (shortNameInput) shortNameInput.value = '';
        if (descriptionInput) descriptionInput.value = '';
        if (statusSelect) statusSelect.value = '';
        if (viewingSelect) viewingSelect.value = '';
        if (parentLegalEntitySelect) parentLegalEntitySelect.selectedIndex = 0; // "No Parent Legal Entity" option
        nameInput?.focus();
    }

    // Initialize the page
    async function initPage() {
        try {
            await Promise.all([
                loadStatuses(),
                loadViewingOptions(),
                loadParentLegalEntities()
            ]);
            
            await initializeCustomFields();
        } catch (error) {
            console.error('Error initializing page:', error);
        }
    }

    async function initializeCustomFields() {
        try {
            if (window.CustomFields) {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Legal Entity',
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

        try {
            if (window.SegmentField) {
                window.segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    defaultValue: 1,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Legal Entity',
                    fieldId: 'legalEntitySegment',
                    errorId: 'legalEntitySegmentError',
                    onChange: async () => {
                        const previousParentId = parentLegalEntitySelect ? parentLegalEntitySelect.value : '';
                        await loadParentLegalEntities();
                        if (!parentLegalEntitySelect || !previousParentId) return;
                        const stillExists = Array.from(parentLegalEntitySelect.options || [])
                            .some(opt => String(opt.value) === String(previousParentId));
                        if (!stillExists) {
                            alert('This parent is not valid for the selected segment. Please remove the parent first.');
                            return false;
                        }
                    }
                });
                console.log('Segment field initialized');
            }
        } catch (error) {
            console.error('Error initializing segment field:', error);
        }
    }
    // Load statuses from API for dropdown
    async function loadStatuses() {
        try {
            if (!statusSelect) return;

            statusSelect.innerHTML = '<option value="">Loading statuses...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getStatusList !== "function") {
                statusSelect.innerHTML = '<option value="">Status not available</option>';
                return;
            }

            const response = await window.BUDG_API_SERVICE.getStatusList();

            if (response && Array.isArray(response) && response.length > 0) {
                statusSelect.innerHTML = '';
                
                response.forEach((status, index) => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.name || status.primaryname || status.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    statusSelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data) && response.data.length > 0) {
                statusSelect.innerHTML = '';
                
                response.data.forEach((status, index) => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.name || status.primaryname || status.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    statusSelect.appendChild(option);
                });
            } else {
                statusSelect.innerHTML = '<option value="">Status not available</option>';
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
            statusSelect.innerHTML = '<option value="">Error loading statuses</option>';
        }
    }

    // Load viewing options from API for dropdown
    async function loadViewingOptions() {
        try {
            if (!viewingSelect) return;

            viewingSelect.innerHTML = '<option value="">Loading viewing options...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getViewingList !== "function") {
                viewingSelect.innerHTML = '<option value="">Viewing not available</option>';
                return;
            }

            const response = await window.BUDG_API_SERVICE.getViewingList();

            if (response && Array.isArray(response) && response.length > 0) {
                viewingSelect.innerHTML = '';
                
                response.forEach((viewing, index) => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.primaryname || viewing.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    viewingSelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data) && response.data.length > 0) {
                viewingSelect.innerHTML = '';
                
                response.data.forEach((viewing, index) => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.primaryname || viewing.title;
                    if (index === 0) {
                        option.selected = true; // Select first option as default
                    }
                    viewingSelect.appendChild(option);
                });
            } else {
                viewingSelect.innerHTML = '<option value="">Viewing not available</option>';
            }
        } catch (error) {
            console.error('Error loading viewing options:', error);
            viewingSelect.innerHTML = '<option value="">Error loading viewing options</option>';
        }
    }

    // Load parent legal entities from API for dropdown
    async function loadParentLegalEntities() {
        try {
            if (!parentLegalEntitySelect) return;

            parentLegalEntitySelect.innerHTML = '<option value="">Loading parent legal entities...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getLegalEntities !== "function") {
                parentLegalEntitySelect.innerHTML = '<option value="">Parent legal entities not available</option>';
                return;
            }

            const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
                ? parseInt(window.segmentField.getValue(), 10)
                : NaN;
            const response = await window.BUDG_API_SERVICE.getLegalEntities(
                Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
            );

            if (response && Array.isArray(response) && response.length > 0) {
                parentLegalEntitySelect.innerHTML = '';
                
                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'No Parent Legal Entity';
                parentLegalEntitySelect.appendChild(emptyOption);
                
                response.forEach((legalEntity) => {
                    const option = document.createElement('option');
                    option.value = legalEntity.id;
                    option.textContent = legalEntity.name || legalEntity.longname || legalEntity.primaryname || legalEntity.title;
                    parentLegalEntitySelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data) && response.data.length > 0) {
                parentLegalEntitySelect.innerHTML = '';
                
                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'No Parent Legal Entity';
                parentLegalEntitySelect.appendChild(emptyOption);
                
                response.data.forEach((legalEntity) => {
                    const option = document.createElement('option');
                    option.value = legalEntity.id;
                    option.textContent = legalEntity.name || legalEntity.longname || legalEntity.primaryname || legalEntity.title;
                    parentLegalEntitySelect.appendChild(option);
                });
            } else {
                parentLegalEntitySelect.innerHTML = '<option value="">No parent legal entities available</option>';
            }
        } catch (error) {
            console.error('Error loading parent legal entities:', error);
            parentLegalEntitySelect.innerHTML = '<option value="">Error loading parent legal entities</option>';
        }
    }
});
