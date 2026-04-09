// Committee Page JavaScript
document.addEventListener('DOMContentLoaded', function() {
    // Get references to all DOM elements
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    // Form elements
    const committeeForm = document.getElementById('committeeForm');
    const primaryNameInput = document.getElementById('primaryName');
    const descriptionInput = document.getElementById('description');
    const refInput = document.getElementById('ref');
    const parentSelect = document.getElementById('parent');
    const statusSelect = document.getElementById('budgStatus');
    const viewingSelect = document.getElementById('budgViewing');
    const lifecycleSelect = document.getElementById('lifecycle');
    const classificationSelect = document.getElementById('classification');
    const committeeTypeSelect = document.getElementById('committeeType');

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
    if (!primaryNameInput || !descriptionInput || !statusSelect || !viewingSelect || !lifecycleSelect || !classificationSelect || !committeeTypeSelect) {
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
            saveCommittee(true);
        });
    }

    if (saveAndCloseBtn) {
        saveAndCloseBtn.addEventListener('click', function() {
            saveCommittee(true);
        });
    }

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            cancelForm();
        });
    }

    // BUDG Viewing select change event listener
    if (viewingSelect) {
        viewingSelect.addEventListener('change', function() {
            const selectedId = this.value;
            const selectedText = this.options[this.selectedIndex].textContent;
            //('BUDG Viewing selected - ID:', selectedId, 'Text:', selectedText);
        });
    }

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

    // Form submission handler - removed to prevent double submission
    // The form submission is now handled by individual button click events

    // Save committee to API
    async function saveCommittee(closeAfterSave = false) {
        // Sync advanced rich text editor content to textarea before saving
        if (typeof window.syncAdvancedRichTextToTextarea === 'function') {
            window.syncAdvancedRichTextToTextarea('description');
        }
        try {
            // Validate required fields
            if (!primaryNameInput || !primaryNameInput.value.trim()) {
                const m = I18n.t('createPage.message.primaryNameRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                primaryNameInput?.focus();
                return;
            }

            if (!descriptionInput || !descriptionInput.value.trim()) {
                const m = I18n.t('createPage.message.descriptionRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                descriptionInput?.focus();
                return;
            }

            if (!statusSelect || !statusSelect.value) {
                const m = I18n.t('createPage.message.statusRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                statusSelect?.focus();
                return;
            }

            if (!viewingSelect || !viewingSelect.value) {
                const m = I18n.t('createPage.message.viewingRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                viewingSelect?.focus();
                return;
            }

            if (!lifecycleSelect || !lifecycleSelect.value) {
                const m = I18n.t('createPage.message.lifecycleRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                lifecycleSelect?.focus();
                return;
            }

            if (!classificationSelect || !classificationSelect.value) {
                const m = I18n.t('createPage.message.classificationRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                classificationSelect?.focus();
                return;
            }

            if (!committeeTypeSelect || !committeeTypeSelect.value) {
                const m = I18n.t('createPage.message.committeeTypeRequired');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                committeeTypeSelect?.focus();
                return;
            }
            
            // Validate custom fields
            if (window.customFieldsContext && window.customFieldsContext.validate) {
                const customFieldsValid = window.customFieldsContext.validate();
                if (!customFieldsValid) {
                    return;
                }
            }

            // Client-side uniqueness checks (Primary Name, Ref Number)
            try {
                const list = await fetch('/api/committee').then(r => r.json());
                const rows = Array.isArray(list) ? list : [];

                const primaryNameVal = (primaryNameInput.value || '').trim();
                const refNumberVal = (refInput.value || '').trim();

                // Check if PrimaryName is not empty after trimming
                if (!primaryNameVal) {
                    const m = I18n.t('createPage.message.primaryNameEmpty');
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    primaryNameInput.focus();
                    return;
                }

                // Check for duplicate Ref Number if provided (only if not empty after trim)
                if (refNumberVal && refNumberVal.trim() !== '') {
                    const refNumberClash = rows.some(r => {
                        const existingRefNumber = String(r.RefNumber || r.refNumber || r.ref || '').trim().toLowerCase();
                        return existingRefNumber === refNumberVal.toLowerCase();
                    });
                    if (refNumberClash) {
                        const m = I18n.t('createPage.message.duplicateRefNumber', {facet: 'Committees'});
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        refInput.focus();
                        return;
                    }
                }
                // Note: If no reference is provided, it will be auto-generated by the server
            } catch (_) { /* fall back to server-side validation */ }

            // Collect form data
            const formData = {
                primaryName: primaryNameInput.value.trim(),
                description: descriptionInput.value.trim(),
                refNumber: refInput?.value?.trim() || null, // Will be auto-generated if empty
                parentId: parentSelect?.value && parentSelect.value !== "" ? parseInt(parentSelect.value) : null,
                status: statusSelect.value ? parseInt(statusSelect.value) : null,
                isPublic: viewingSelect?.value && viewingSelect.value !== "" ? parseInt(viewingSelect.value) : null,
                lifecycle: lifecycleSelect.value ? parseInt(lifecycleSelect.value) : null,
                classification: classificationSelect.value ? parseInt(classificationSelect.value) : null,
                committeeType: committeeTypeSelect.value ? parseInt(committeeTypeSelect.value) : null,
                segmentId: window.segmentField ? window.segmentField.getValue() : 1,
                lastUpdateUserID: getCurrentUserId(),
                createdById: getCurrentUserId()
            };

            // Debug logging
            //('Form data being sent:', formData);
            //('viewingSelect element:', viewingSelect);
            //('viewingSelect value:', viewingSelect?.value);
            //('viewingSelect selectedIndex:', viewingSelect?.selectedIndex);
            //('viewingSelect options:', viewingSelect?.options);
            //('isPublic value:', formData.isPublic);

            // Show loading state on buttons
            const buttons = [saveBtn, saveAndCloseBtn, closeBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = I18n.t('createPage.message.saving');
                }
            });

            // Call API to save committee
            const response = await fetch('/api/committee', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(formData)
            });

            // Handle API response
            if (response.ok) {
                const result = await response.json();
                if (result && result.id) {

                    // Check if reference was auto-generated
                    const wasRefEmpty = !refInput?.value?.trim();
                    const generatedRef = result.refNumber || result.RefNumber;

                    // Role assignment is now handled automatically by the backend

                    let successMessage = I18n.t('createPage.message.committeeSaved');
                    if (wasRefEmpty && generatedRef) {
                        successMessage += '\n' + I18n.t('createPage.message.referenceAutoGenerated', {ref: generatedRef});
                    }
                    if (typeof window.showNotification === 'function') { window.showNotification(successMessage, 'success'); } else { alert(successMessage); }
                    
                    // Get the ID from the response
                    const id = result?.id || result?.data?.id || result?.committeeId || result?.insertId || result?.createdId;
                    
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
                            window.location.href = `/view/committee/${id}`;
                        } else {
                            window.location.href = 'index.html';
                        }
                    } else {
                        // Keep form data - user can continue editing or manually clear if needed
                        console.log('✅ Committee saved. Form data preserved for continued editing.');
                    }
                } else {
                    const serverMsg = result?.error || result?.message;
                    const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMsg, 'Committees') : null;
                    if (errInfo) {
                        const m = I18n.t(errInfo.key, errInfo.params);
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                        if (errInfo.focus === 'name') primaryNameInput?.focus(); else if (errInfo.focus === 'reference') refInput?.focus();
                    } else {
                        const m = I18n.t('createPage.message.failedToSaveWithHint', { facet: 'Committees' });
                        if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                    }
                }
            } else {
                const errorData = await response.json();
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

        } catch (error) {
            console.error('Error saving committee:', error);
            const serverMessage = error.message || (error.body && (error.body.error || error.body.message));
            const errInfo = window.getCreateSaveErrorInfo ? window.getCreateSaveErrorInfo(serverMessage, 'Committees') : null;
            if (errInfo) {
                const m = I18n.t(errInfo.key, errInfo.params);
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                if (errInfo.focus === 'name') primaryNameInput?.focus(); else if (errInfo.focus === 'reference') refInput?.focus();
            } else {
                let errorMessage = I18n.t('createPage.message.unknownError');
                if (error.message) errorMessage = error.message;
                else if (error.status) errorMessage = I18n.t('createPage.message.serverError', {status: error.status});
                else if (error.name === 'TypeError') errorMessage = I18n.t('createPage.message.networkError');
                const m = I18n.t('createPage.message.errorSaving', {error: errorMessage});
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            }
        } finally {
            // Reset button states
            const buttons = [saveBtn, saveAndCloseBtn, closeBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn === saveBtn) btn.textContent = I18n.t('button.save');
                    if (btn === saveAndCloseBtn) btn.textContent = I18n.t('button.saveAndClose');
                    if (btn === closeBtn) btn.textContent = I18n.t('button.close');
                }
            });
        }
    }

    // Check if form has any data entered
    function checkIfFormHasData() {
        return (primaryNameInput && primaryNameInput.value.trim()) ||
            (descriptionInput && descriptionInput.value.trim()) ||
            (refInput && refInput.value.trim()) ||
            (statusSelect && statusSelect.value) ||
            (viewingSelect && viewingSelect.value) ||
            (lifecycleSelect && lifecycleSelect.value) ||
            (classificationSelect && classificationSelect.value) ||
            (committeeTypeSelect && committeeTypeSelect.value) ||
            (parentSelect && parentSelect.value);
    }

    // Handle form cancellation
    function cancelForm() {
        const hasData = checkIfFormHasData();
        if (hasData) {
            const confirmClose = confirm(I18n.t('createPage.message.unsavedChanges'));
            if (confirmClose) {
                window.location.href = './index.html';
            }
        } else {
            window.location.href = './index.html';
        }
    }

    // Clear form inputs
    function clearForm() {
        if (primaryNameInput) primaryNameInput.value = '';
        if (descriptionInput) descriptionInput.value = '';
        if (refInput) refInput.value = '';
        if (statusSelect) statusSelect.value = '';
        if (viewingSelect) viewingSelect.value = '';
        if (lifecycleSelect) lifecycleSelect.value = '';
        if (classificationSelect) classificationSelect.value = '';
        if (committeeTypeSelect) committeeTypeSelect.value = '';
        if (parentSelect) parentSelect.selectedIndex = 0;
        primaryNameInput?.focus();
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

        // Fallback to default user ID (should fetch from /api/me in production)
        console.warn('User ID not found in session, using default ID: 1');
        return null;
    }

    // Fetch and cache current user information
    async function fetchCurrentUser() {
        try {
            const response = await fetch('/api/me');
            if (response.ok) {
                const user = await response.json();
                sessionStorage.setItem('currentUser', JSON.stringify(user));
                return user;
            }
        } catch (error) {
            console.error('Error fetching current user:', error);
        }
        return null;
    }

    // Initialize the page
    async function initPage() {
        try {
            // Fetch current user first
            await fetchCurrentUser();

            // Debug: Check if user was fetched successfully
            const userStr = sessionStorage.getItem('currentUser');
            if (userStr) {
                const user = JSON.parse(userStr);
                console.log('👤 Current User Loaded:', user);
                console.log('👤 User ID:', user.id || user.ID || user.userId);
            } else {
                console.warn('⚠️ No user found in session storage');
            }

            // Then load all dropdown data
            await Promise.all([
                loadStatuses(),
                loadViewingOptions(),
                loadLifecycles(),
                loadClassifications(),
                loadCommitteeTypes(),
                loadParentCommittees()
            ]);
            
            // Initialize custom fields
            await initializeCustomFields();
            
        } catch (error) {
            console.error('Error initializing page:', error);
        }
    }
    
    // Initialize custom fields
    async function initializeCustomFields() {
        try {
            if (window.CustomFields) {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Committee',
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
                    objectType: 'Committee',
                    fieldId: 'committeeSegment',
                    errorId: 'committeeSegmentError',
                    onChange: async () => {
                        const previousParentId = parentSelect ? parentSelect.value : '';
                        await loadParentCommittees();
                        if (!parentSelect || !previousParentId) return;
                        const stillExists = Array.from(parentSelect.options || [])
                            .some(opt => String(opt.value) === String(previousParentId));
                        if (!stillExists) {
                            parentSelect.value = '';
                            const parentMsg = (window.I18n && window.I18n.t('system.messages.parentClearedInvalidSegment')) || 'Parent was cleared because it is not valid for the selected segment.';
                            if (typeof window.showNotification === 'function') { window.showNotification(parentMsg, 'info'); } else { alert(parentMsg); }
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

            const response = await fetch('/api/committee/lookup?type=status');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                statusSelect.innerHTML = '';

                data.forEach((status, index) => {
                    const option = document.createElement('option');
                    option.value = status.ID;
                    option.textContent = status.primaryname;
                    // Select the first option
                    if (index === 0) {
                        option.selected = true;
                    }
                    statusSelect.appendChild(option);
                });
            } else {
                statusSelect.innerHTML = '<option value="">Status not available</option>';
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
            statusSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load viewing options from API for dropdown
    async function loadViewingOptions() {
        try {
            if (!viewingSelect) return;

            viewingSelect.innerHTML = '<option value="">Loading viewing options...</option>';

            const response = await fetch('/api/committee/lookup?type=viewing');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                viewingSelect.innerHTML = '';

                //('Loading viewing options:', data);
                //('First viewing object keys:', data[0] ? Object.keys(data[0]) : 'No data');
                //('First viewing object:', data[0]);

                data.forEach((viewing, index) => {
                    const option = document.createElement('option');
                    // Try different possible ID field names
                    const idValue = viewing.ID || viewing.id || viewing.Id || viewing.value || viewing.Value || index;
                    option.value = idValue;
                    option.textContent = viewing.Name || viewing.name || viewing.text || viewing.Text || 'Unknown';
                    // Select the first option
                    if (index === 0) {
                        option.selected = true;
                    }
                    viewingSelect.appendChild(option);
                    //(`Added option: value=${idValue}, text=${option.textContent}`);
                });

                //('Final viewingSelect value after loading:', viewingSelect.value);
            } else {
                viewingSelect.innerHTML = '<option value="">Viewing not available</option>';
            }
        } catch (error) {
            console.error('Error loading viewing options:', error);
            viewingSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load lifecycles from API for dropdown
    async function loadLifecycles() {
        try {
            if (!lifecycleSelect) return;

            lifecycleSelect.innerHTML = '<option value="">Loading lifecycles...</option>';

            const response = await fetch('/api/committee/lookup?type=lifecycle');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                lifecycleSelect.innerHTML = '';

                data.forEach((lifecycle, index) => {
                    const option = document.createElement('option');
                    option.value = lifecycle.ID;
                    option.textContent = lifecycle.PrimaryName;
                    // Select the first option
                    if (index === 0) {
                        option.selected = true;
                    }
                    lifecycleSelect.appendChild(option);
                });
            } else {
                lifecycleSelect.innerHTML = '<option value="">Lifecycle not available</option>';
            }
        } catch (error) {
            console.error('Error loading lifecycles:', error);
            lifecycleSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load classifications from API for dropdown
    async function loadClassifications() {
        try {
            if (!classificationSelect) return;

            classificationSelect.innerHTML = '<option value="">Loading classifications...</option>';

            const response = await fetch('/api/committee/lookup?type=classification');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                classificationSelect.innerHTML = '';

                data.forEach((classification, index) => {
                    const option = document.createElement('option');
                    option.value = classification.ID;
                    option.textContent = classification.PrimaryName;
                    // Select the first option
                    if (index === 0) {
                        option.selected = true;
                    }
                    classificationSelect.appendChild(option);
                });
            } else {
                classificationSelect.innerHTML = '<option value="">Classification not available</option>';
            }
        } catch (error) {
            console.error('Error loading classifications:', error);
            classificationSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load committee types from API for dropdown
    async function loadCommitteeTypes() {
        try {
            if (!committeeTypeSelect) return;

            committeeTypeSelect.innerHTML = '<option value="">Loading committee types...</option>';

            const response = await fetch('/api/committee/lookup?type=committeetype');
            const data = await response.json();

            if (data && Array.isArray(data) && data.length > 0) {
                committeeTypeSelect.innerHTML = '';

                data.forEach((committeeType, index) => {
                    const option = document.createElement('option');
                    option.value = committeeType.ID;
                    option.textContent = committeeType.PrimaryName;
                    // Select the first option
                    if (index === 0) {
                        option.selected = true;
                    }
                    committeeTypeSelect.appendChild(option);
                });
            } else {
                committeeTypeSelect.innerHTML = '<option value="">Committee Type not available</option>';
            }
        } catch (error) {
            console.error('Error loading committee types:', error);
            committeeTypeSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }

    // Load parent committees from API for dropdown
    async function loadParentCommittees() {
        try {
            if (!parentSelect) return;

            parentSelect.innerHTML = '<option value="">Loading parent committees...</option>';

            const selectedSegmentId = window.segmentField && typeof window.segmentField.getValue === 'function'
                ? parseInt(window.segmentField.getValue(), 10)
                : NaN;
            let data = [];
            if (window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE.getCommitteeParentCommittees === 'function') {
                const apiResult = await window.BUDG_API_SERVICE.getCommitteeParentCommittees(
                    Number.isInteger(selectedSegmentId) && selectedSegmentId > 0 ? { segmentId: selectedSegmentId } : {}
                );
                data = Array.isArray(apiResult?.data) ? apiResult.data : (Array.isArray(apiResult) ? apiResult : []);
            } else {
                const query = Number.isInteger(selectedSegmentId) && selectedSegmentId > 0
                    ? `?type=committees&segmentId=${encodeURIComponent(selectedSegmentId)}`
                    : '?type=committees';
                const response = await fetch(`/api/committee/lookup${query}`);
                data = await response.json();
            }

            if (data && Array.isArray(data) && data.length > 0) {
                parentSelect.innerHTML = '';

                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'No Parent Committee';
                parentSelect.appendChild(emptyOption);

                data.forEach((committee) => {
                    const option = document.createElement('option');
                    option.value = committee.ID;
                    option.textContent = committee.Display_Name || committee.PrimaryName;
                    parentSelect.appendChild(option);
                });
            } else {
                parentSelect.innerHTML = '<option value="">No parent committees available</option>';
            }
        } catch (error) {
            console.error('Error loading parent committees:', error);
            parentSelect.innerHTML = `<option value="">${I18n.t('createPage.message.loading')}</option>`;
        }
    }
});

