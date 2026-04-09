// Legal Entity Edit Page JavaScript
let segmentField = null; // Segment field component reference
let originalLegalSegmentId = null; // segment loaded from server; used to detect segment change

function normalizeLegalSegmentId(value) {
    if (value == null || value === '') return null;
    const n = parseInt(value, 10);
    return Number.isInteger(n) ? n : null;
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('DOMContentLoaded event fired');
    
    // Check if we're on the edit page (not view page)
    if (!document.getElementById('legalEntityForm')) {
        console.log('Not on edit page, skipping initialization');
        return;
    }
    
    // Get ID from URL parameters
    function getEntityId() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('id');
    }

    const entityId = getEntityId();
    if (!entityId) {
        const errorMsg = window.I18n ? window.I18n.t('legalEntity.messages.noIdFound') : 'No entity ID provided';
        alert(errorMsg);
        window.location.href = '/';
        return;
    }

    // Initialize lock
    try {
    const lockAcquired = await window.LockInitHelper.initializeLock('legal-entity', parseInt(entityId), 'legal-entity');
    if (!lockAcquired) {
        return; // Lock initialization failed, user was redirected
        }
    } catch (error) {
        console.error('[LegalEntityEdit] Error initializing lock:', error);
        alert('Failed to acquire lock. Please try again.');
        window.location.href = `/view/LegalEntity/legal-entity.html?id=${entityId}`;
        return;
    }

    console.log('Entity ID found:', entityId);

    // Store original data for change detection
    let originalData = null;

    // Check if form data has changed
    function hasDataChanged() {
        if (!originalData) return true; // If no original data, consider it changed
        
        const nameInput = document.getElementById('nameInput');
        const shortNameInput = document.getElementById('shortNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
        
        // Get segment ID, handling null/undefined properly
        let currentSegmentId = null;
        if (segmentField) {
            try {
                const value = segmentField.getValue();
                currentSegmentId = (value != null && value !== undefined && value !== -1) ? parseInt(value, 10) : null;
            } catch (error) {
                console.warn('Error getting segment field value:', error);
                currentSegmentId = null;
            }
        }
        
        const currentData = {
            longname: nameInput?.value?.trim() || '',
            shortname: shortNameInput?.value?.trim() || '',
            description: descriptionInput?.value?.trim() || '',
            status: parseInt(statusSelect?.value) || 1,
            is_public: parseInt(viewingSelect?.value) || 1,
            parent_id: parentLegalEntitySelect?.value || null,
            segment_id: currentSegmentId
        };
        
        // Normalize parent_id for comparison (empty string vs null)
        const currentParentId = currentData.parent_id === '' ? null : currentData.parent_id;
        const originalParentId = originalData.parent_id === '' ? null : originalData.parent_id;
        
        // Normalize segment_id for comparison (handle null, undefined, and type mismatches)
        const currentSegId = currentData.segment_id != null ? parseInt(currentData.segment_id, 10) : null;
        const originalSegId = originalData.segment_id != null ? parseInt(originalData.segment_id, 10) : null;
        
        // Compare current data with original data
        const hasChanged = (
            currentData.longname !== originalData.longname ||
            currentData.shortname !== originalData.shortname ||
            currentData.description !== (originalData.description || '') ||
            currentData.status !== originalData.status ||
            currentData.is_public !== originalData.is_public ||
            currentParentId !== originalParentId ||
            currentSegId !== originalSegId
        );
        
        if (hasChanged) {
            console.log('🔍 Data changed detected:', {
                longname: currentData.longname !== originalData.longname,
                shortname: currentData.shortname !== originalData.shortname,
                description: currentData.description !== (originalData.description || ''),
                status: currentData.status !== originalData.status,
                is_public: currentData.is_public !== originalData.is_public,
                parent_id: currentParentId !== originalParentId,
                segment_id: currentSegId !== originalSegId,
                currentSegmentId: currentSegId,
                originalSegmentId: originalSegId
            });
        }
        
        return hasChanged;
    }

    // Wait for all scripts to load
    function waitForDependencies() {
        const maxWait = 5000; // 5 seconds max wait
        const checkInterval = 100; // Check every 100ms
        let elapsed = 0;
        
        const checkDependencies = () => {
            console.log('Checking dependencies...', {
                BUDG_API_SERVICE: !!window.BUDG_API_SERVICE,
                DOMReady: document.readyState,
                elapsed: elapsed
            });
            
            if (window.BUDG_API_SERVICE && document.readyState === 'complete') {
                console.log('All dependencies ready, initializing...');
                initPageWithRetry();
            } else if (elapsed < maxWait) {
                elapsed += checkInterval;
                setTimeout(checkDependencies, checkInterval);
            } else {
                console.error('Timeout waiting for dependencies');
            }
        };
        
        checkDependencies();
    }

    // Start dependency checking
    waitForDependencies();

    // Retry mechanism for initialization
    function initPageWithRetry(retryCount = 0) {
        const maxRetries = 5;
        const retryDelay = 200;
        
        try {
            initPage();
        } catch (error) {
            if (retryCount < maxRetries) {
                console.warn(`InitPage failed, retrying... (${retryCount + 1}/${maxRetries})`, error);
                setTimeout(() => {
                    initPageWithRetry(retryCount + 1);
                }, retryDelay * (retryCount + 1));
            } else {
                console.error('Failed to initialize page after maximum retries:', error);
            }
        }
    }

    // Load existing data
    async function loadEntityData() {
        try {
            console.log('Loading legal entity data for ID:', entityId);
            const entity = await window.BUDG_API_SERVICE.getLegalEntityById(entityId);
            const data = entity.data || entity;
            
            console.log('Loaded entity data:', data);
            
            // Get form elements again to ensure they're available
            const pageTitle = document.getElementById('pageTitle');
            const nameInput = document.getElementById('nameInput');
            const shortNameInput = document.getElementById('shortNameInput');
            const descriptionInput = document.getElementById('descriptionInput');
            const statusSelect = document.getElementById('statusSelect');
            const viewingSelect = document.getElementById('viewingSelect');
            const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
            
            // Update page title
            if (pageTitle) {
                const defaultName = window.I18n ? window.I18n.t('legalEntity.page.title') : 'Legal Entity';
                const entityName = data.longname || data.Name || data.primaryname || data.name || defaultName;
                const editTitle = window.I18n ? window.I18n.t('legalEntity.page.editTitle') : 'Edit Legal Entity';
                pageTitle.textContent = `${editTitle} ${entityName}`;
                console.log('Updated page title to:', entityName);
            }
            
            // Populate form fields with correct API field names
            if (nameInput) {
                const longName = data.longname || data.Name || data.primaryname || data.name || '';
                nameInput.value = longName;
                console.log('Set Long Name to:', longName);
            }
            
            if (shortNameInput) {
                const shortName = data.shortname || data.Short_Name || data.short_name || data.shortName || '';
                shortNameInput.value = shortName;
                console.log('Set Short Name to:', shortName);
            }

            if (descriptionInput) {
                const description = data.description || data.Description || '';
                descriptionInput.value = description;
                console.log('Set Description to:', description);
            }
            
            // Set status and viewing selections after they're loaded
            // Use explicit check so that 0 or other valid IDs are not treated as missing (avoid resetting to Active after reload)
            if (statusSelect) {
                const statusValue = data.status ?? data.status_id ?? data.Status_ID;
                if (statusValue !== undefined && statusValue !== null && statusValue !== '') {
                    const valueStr = String(statusValue);
                    if (Array.from(statusSelect.options).some(opt => opt.value === valueStr)) {
                        statusSelect.value = valueStr;
                        console.log('Set Status to:', valueStr);
                    } else {
                        setDefaultStatus();
                    }
                } else {
                    setDefaultStatus();
                }
            }
            
            if (viewingSelect) {
                const viewingValue = data.is_public ?? data.viewing_id ?? data.Viewing_ID;
                if (viewingValue !== undefined && viewingValue !== null && viewingValue !== '') {
                    const valueStr = String(viewingValue);
                    if (Array.from(viewingSelect.options).some(opt => opt.value === valueStr)) {
                        viewingSelect.value = valueStr;
                        console.log('Set Viewing to:', valueStr);
                    } else {
                        setDefaultViewing();
                    }
                } else {
                    setDefaultViewing();
                }
            }
            
            // Set parent legal entity selection
            if (parentLegalEntitySelect) {
                const parentValue = data.parent_id || data.Parent_ID || data.parent_legal_entity_id;
                if (parentValue) {
                    parentLegalEntitySelect.value = parentValue;
                    console.log('Set Parent Legal Entity to:', parentValue);
                } else {
                    // Set to empty (No Parent Legal Entity) if no existing value
                    parentLegalEntitySelect.value = '';
                    console.log('Set Parent Legal Entity to empty (No Parent)');
                }
            }

            // Set segment value if available (check for null/undefined, not falsy, since 0 could be valid)
            const serverSegmentId = data.segmentId ?? data.segment_id ?? data.Segment_ID;
            originalLegalSegmentId = normalizeLegalSegmentId(serverSegmentId);
            console.log('🔍 Legal Entity segment data:', {
                segmentId: data.segmentId,
                segment_id: data.segment_id,
                Segment_ID: data.Segment_ID,
                resolvedSegmentId: serverSegmentId,
                segmentName: data.segmentName
            });
            
            if (segmentField) {
                if (serverSegmentId != null && serverSegmentId !== undefined && serverSegmentId !== -1) {
                    console.log('🔧 Setting segment field to:', serverSegmentId, 'Type:', typeof serverSegmentId);
                    // Use a small delay to ensure segment field options are loaded
                    setTimeout(() => {
                        try {
                            const numericId = parseInt(serverSegmentId, 10);
                            console.log('🔧 Calling setValue with numeric ID:', numericId);
                            segmentField.setValue(numericId);
                            
                            // Verify it was set
                            setTimeout(() => {
                                const currentValue = segmentField.getValue();
                                console.log('🔧 Segment field value after setting:', currentValue, 'Expected:', numericId);
                                if (currentValue !== numericId) {
                                    console.error('❌ Segment value mismatch! Expected', numericId, 'but got', currentValue);
                                    // Retry once
                                    segmentField.setValue(numericId);
                                } else {
                                    console.log('✅ Segment value successfully set to:', currentValue);
                                }
                            }, 100);
                        } catch (error) {
                            console.error('❌ Error setting segment value:', error);
                        }
                    }, 100);
                } else {
                    console.warn('⚠️ No valid segment ID found in legal entity data. segmentId:', serverSegmentId);
                }
            } else {
                console.warn('⚠️ Segment field not initialized yet');
            }

            setTimeout(() => {
                if (segmentField && typeof segmentField.getValue === 'function') {
                    originalLegalSegmentId = normalizeLegalSegmentId(segmentField.getValue());
                }
            }, 400);
            
            // Store original data for change detection
            const resolvedSegmentId = (serverSegmentId != null && serverSegmentId !== undefined && serverSegmentId !== -1) 
                ? parseInt(serverSegmentId, 10) 
                : (segmentField ? segmentField.getValue() : null);
            
            originalData = {
                longname: data.longname || data.Name || data.primaryname || data.name || '',
                shortname: data.shortname || data.Short_Name || data.short_name || data.shortName || '',
                description: data.description || data.Description || '',
                status: parseInt(data.status_id || data.statusId || data.status || 1),
                is_public: parseInt(data.is_public || data.isPublic || data.viewing || 1),
                parent_id: data.parent_id || data.Parent_ID || data.parent_legal_entity_id || null,
                segment_id: resolvedSegmentId
            };
            window._legalEntitySegmentId = resolvedSegmentId != null ? resolvedSegmentId : (segmentField ? segmentField.getValue() : null);
            
            console.log('Original data stored:', originalData);
            console.log('Form fields populated successfully');
            
            // Double-check that fields were actually populated
            setTimeout(() => {
                console.log('Verifying form field values:');
                console.log('Long Name:', nameInput?.value);
                console.log('Short Name:', shortNameInput?.value);
                console.log('Description:', descriptionInput?.value);
                console.log('Status:', statusSelect?.value);
                console.log('Viewing:', viewingSelect?.value);
            }, 100);
            
        } catch (error) {
            console.error('Error loading legal entity data:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToLoad') : 'Failed to load legal entity data. Please try again.';
            alert(errorMsg);
        }
    }

    // Robust success detection for update API response (backend returns { success: true, message: "..." })
    function isUpdateSuccess(response) {
        if (!response || typeof response !== 'object') return false;
        if (response.error) return false;
        if (response.success === true) return true;
        if (response.id != null) return true;
        if (response.message && response.message.length > 0) return true;
        return false;
    }

    // Save legal entity to API
    async function saveLegalEntity(closeAfterSave = false) {
        try {
            // Check if data has changed
            if (!hasDataChanged()) {
                showSuccessMessage('NO CHANGES', true);
                if (closeAfterSave) {
                    setTimeout(() => {
                        window.location.href = `/view/LegalEntity/legal-entity.html?id=${entityId}`;
                    }, 1500);
                }
                return;
            }
            
            console.log('Data has changed, proceeding with save...');
            
            // Get form elements
            const nameInput = document.getElementById('nameInput');
            const shortNameInput = document.getElementById('shortNameInput');
            const descriptionInput = document.getElementById('descriptionInput');
            const statusSelect = document.getElementById('statusSelect');
            const viewingSelect = document.getElementById('viewingSelect');
            const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
            
            // Validate required fields
            if (!nameInput || !nameInput.value.trim()) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.longNameRequired') : 'Long Name is required';
                alert(errorMsg);
                nameInput?.focus();
                return;
            }

            if (!shortNameInput || !shortNameInput.value.trim()) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.shortNameRequired') : 'Short Name is required';
                alert(errorMsg);
                shortNameInput?.focus();
                return;
            }

            if (!statusSelect || !statusSelect.value) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.statusRequired') : 'BUDG Status is required';
                alert(errorMsg);
                statusSelect?.focus();
                return;
            }

            if (!viewingSelect || !viewingSelect.value) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.viewingRequired') : 'BUDG Viewing is required';
                alert(errorMsg);
                viewingSelect?.focus();
                return;
            }

            // Client-side uniqueness checks (Long Name, Short Name) - only if names have changed
            try {
                if (typeof window.BUDG_API_SERVICE?.getLegalEntities === 'function') {
                    console.log('Checking for duplicate names in database...');
                    const list = await window.BUDG_API_SERVICE.getLegalEntities();
                    const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                    
                    const longNameVal = (nameInput.value || '').trim().toLowerCase();
                    const shortNameVal = (shortNameInput.value || '').trim().toLowerCase();
                    
                    console.log('Checking against', rows.length, 'existing entities');
                    
                    // Get current entity data to compare
                    const currentEntity = await window.BUDG_API_SERVICE.getLegalEntityById(entityId);
                    const currentData = currentEntity.data || currentEntity;
                    const currentLongName = (currentData.longname || currentData.Name || currentData.primaryname || currentData.name || '').trim().toLowerCase();
                    const currentShortName = (currentData.shortname || currentData.Short_Name || currentData.short_name || currentData.shortName || '').trim().toLowerCase();
                    
                    // Check for duplicate Long Name only if it has changed
                    if (longNameVal !== currentLongName) {
                        const longNameClash = rows.some(r => {
                            const existingId = String(r.ID || r.id || '');
                            if (existingId === String(entityId)) return false; // Skip current entity
                            const existingLongName = String(r.Name || r.primaryname || r.name || r.longname || '').trim().toLowerCase();
                            return existingLongName === longNameVal && existingLongName !== '';
                        });
                        if (longNameClash) {
                            const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.longNameExists') : 'Error: Long Name already exists in database.\nThe Long Name must be unique to avoid duplicates.';
                            alert(errorMsg);
                            nameInput.focus();
                            return;
                        }
                    }
                    
                    // Check for duplicate Short Name only if it has changed
                    if (shortNameVal !== currentShortName) {
                        const shortNameClash = rows.some(r => {
                            const existingId = String(r.ID || r.id || '');
                            if (existingId === String(entityId)) return false; // Skip current entity
                            const existingShortName = String(r.Short_Name || r.short_name || r.shortName || r.shortname || '').trim().toLowerCase();
                            return existingShortName === shortNameVal && existingShortName !== '';
                        });
                        if (shortNameClash) {
                            const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.shortNameExists') : 'Error: Short Name already exists in database.\nThe Short Name must be unique to avoid duplicates.';
                            alert(errorMsg);
                            shortNameInput.focus();
                            return;
                        }
                    }
                    
                    console.log('No duplicate names found, proceeding with save...');
                }
            } catch (error) {
                console.warn('Client-side validation failed, falling back to server-side validation:', error);
            }

            // Sync rich-text editor content back to textarea before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('descriptionInput');
            }

            // Collect form data
            const formData = {
                longname: nameInput.value.trim(),
                shortname: shortNameInput.value.trim(),
                description: descriptionInput?.value?.trim() || '',
                status: parseInt(statusSelect.value) || 1,
                is_public: parseInt(viewingSelect.value) || 1,
                parent_id: parentLegalEntitySelect?.value || null,
                segmentId: segmentField ? segmentField.getValue() : null
            };

            // Show loading state on buttons
            const savingText = window.I18n ? window.I18n.t('legalEntity.messages.saving') : 'Saving...';
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = savingText;
                }
            });

            // Call API to update legal entity
            console.log('Sending update request with data:', formData);
            const response = await window.BUDG_API_SERVICE.updateLegalEntity(entityId, formData);
            console.log('Update response:', response);

            // Handle API response
            console.log('Checking response:', {
                response: response,
                hasSuccess: response && response.success,
                hasId: response && response.id,
                hasMessage: response && response.message,
                responseType: typeof response
            });

            if (isUpdateSuccess(response)) {
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(entityId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
                
                // Release lock after successful save
                await window.LockInitHelper.releaseLock();
                
                const successMsg = window.I18n ? window.I18n.t('legalEntity.messages.savedSuccessfully') : 'Legal entity updated successfully!';
                alert(successMsg);

                if (closeAfterSave) {
                window.location.href = `/view/LegalEntity/${entityId}`;
                } else {
                    // Reload the data to show updated values
                    await loadEntityData();
                }
            } else {
                console.error('Update failed. Response:', response);
                console.error('Response details:', {
                    success: response?.success,
                    id: response?.id,
                    message: response?.message,
                    raw: response,
                    isUndefined: response === undefined,
                    isNull: response === null,
                    isEmpty: response === '',
                    type: typeof response
                });
                
                // Try to stringify the response for better debugging
                try {
                    console.error('Response JSON:', JSON.stringify(response, null, 2));
                } catch (e) {
                    console.error('Could not stringify response:', e);
                }
                
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToUpdate') : 'Failed to update legal entity. Please check the console for details.';
                alert(errorMsg);
            }

        } catch (error) {
            console.error('Error saving legal entity:', error);
            let errorMessage = 'Unknown error occurred';
            
            // Check for specific server error messages
            if (error.body && error.body.error) {
                errorMessage = error.body.error;
            } else if (error.body && error.body.message) {
                errorMessage = error.body.message;
            } else if (error.message) {
                errorMessage = error.message;
            } else if (error.status) {
                errorMessage = `Server error (${error.status})`;
            } else if (error.name === 'TypeError') {
                errorMessage = 'Network error - please check your connection';
            }
            
            // Log full error for debugging
            console.error('Full error object:', error);
            console.error('Error status:', error.status);
            console.error('Error body:', error.body);
            
            // Show specific error message based on content
            if (errorMessage.toLowerCase().includes('longname') || errorMessage.toLowerCase().includes('long name')) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.longNameExists') : 'Error: Long Name already exists in database.\nThe Long Name must be unique to avoid duplicates.';
                alert(errorMsg);
                nameInput?.focus();
            } else if (errorMessage.toLowerCase().includes('shortname') || errorMessage.toLowerCase().includes('short name')) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.shortNameExists') : 'Error: Short Name already exists in database.\nThe Short Name must be unique to avoid duplicates.';
                alert(errorMsg);
                shortNameInput?.focus();
            } else if (errorMessage.toLowerCase().includes('no changes detected')) {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.noChangesDetected') : 'No changes detected. The data is identical to the existing record.';
                alert(errorMsg);
            } else {
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToUpdate') : 'Error updating legal entity: ' + errorMessage + '\n\nPlease check the console for more details.';
                alert(errorMsg);
            }
        } finally {
            // Reset button states
            const saveText = window.I18n ? window.I18n.t('legalEntity.buttons.save') : 'Save';
            const saveAndCloseText = window.I18n ? window.I18n.t('legalEntity.buttons.saveAndClose') : 'Save & Close';
            const closeText = window.I18n ? window.I18n.t('legalEntity.buttons.close') : 'Close';
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn === saveBtn) btn.textContent = saveText;
                    if (btn === saveAndCloseBtn) btn.textContent = saveAndCloseText;
                    if (btn === cancelBtn) btn.textContent = closeText;
                }
            });
        }
    }

    // Check if form has any data entered
    function checkIfFormHasData() {
        const nameInput = document.getElementById('nameInput');
        const shortNameInput = document.getElementById('shortNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
        
        return (nameInput && nameInput.value.trim()) ||
            (shortNameInput && shortNameInput.value.trim()) ||
            (descriptionInput && descriptionInput.value.trim()) ||
            (statusSelect && statusSelect.value) ||
            (viewingSelect && viewingSelect.value) ||
            (parentLegalEntitySelect && parentLegalEntitySelect.value);
    }

    // Handle form cancellation
    async function cancelForm() {
        // Check if form has unsaved changes
        if (hasAnyDataChanged()) {
            const confirmMsg = window.I18n ? window.I18n.t('legalEntity.confirm.cancelWithChanges') : 'You have unsaved changes. Are you sure you want to cancel without saving?';
            const confirmClose = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMsg, type: 'warning' })
                : Promise.resolve(confirm(confirmMsg)));
            if (confirmClose) {
                await window.LockInitHelper.releaseLock();
                window.location.href = `/view/LegalEntity/${entityId}`;
            }
        } else {
            await window.LockInitHelper.releaseLock();
            window.location.href = `/view/LegalEntity/${entityId}`;
        }
    }


    // Initialize the page
    async function initPage() {
        try {
            console.log('Initializing legal entity edit page...');
            
            // Get references to all DOM elements
            console.log('Looking for form elements...');
            const saveBtn = document.getElementById('saveBtn');
            const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
            const cancelBtn = document.getElementById('cancelBtn');
            const legalEntityForm = document.getElementById('legalEntityForm');
            const nameInput = document.getElementById('nameInput');
            const shortNameInput = document.getElementById('shortNameInput');
            const descriptionInput = document.getElementById('descriptionInput');
            const statusSelect = document.getElementById('statusSelect');
            const viewingSelect = document.getElementById('viewingSelect');
            const pageTitle = document.getElementById('pageTitle');
            
            // Debug: Log all found elements
            console.log('Form elements found:', {
                saveBtn: !!saveBtn,
                saveAndCloseBtn: !!saveAndCloseBtn,
                cancelBtn: !!cancelBtn,
                legalEntityForm: !!legalEntityForm,
                nameInput: !!nameInput,
                shortNameInput: !!shortNameInput,
                descriptionInput: !!descriptionInput,
                statusSelect: !!statusSelect,
                viewingSelect: !!viewingSelect,
                pageTitle: !!pageTitle
            });
            
            // Debug: Check if form exists
            if (!legalEntityForm) {
                console.error('Form element not found! Available elements:', document.querySelectorAll('form'));
                throw new Error('Form element not found. Page structure may be incorrect.');
            }
            
            // Validate that all required elements exist
            if (!nameInput || !statusSelect || !viewingSelect) {
                console.error('Required form elements not found:', {
                    nameInput: !!nameInput,
                    statusSelect: !!statusSelect,
                    viewingSelect: !!viewingSelect
                });
                
                // Try to find elements by different selectors
                console.log('Trying alternative selectors...');
                const altNameInput = document.querySelector('input[id="nameInput"]');
                const altStatusSelect = document.querySelector('select[id="statusSelect"]');
                const altViewingSelect = document.querySelector('select[id="viewingSelect"]');
                
                console.log('Alternative selectors found:', {
                    altNameInput: !!altNameInput,
                    altStatusSelect: !!altStatusSelect,
                    altViewingSelect: !!altViewingSelect
                });
                
                throw new Error('Required form elements not found. DOM may not be fully loaded.');
            }

            // Check if API service is available
            if (!window.BUDG_API_SERVICE) {
                console.error('BUDG_API_SERVICE not found. Make sure api-service.js is loaded.');
                alert('API service not available. Please refresh the page.');
                return;
            }
            
            console.log('Form elements validated successfully');
            
            // Set up event listeners
            if (saveBtn) {
                saveBtn.addEventListener('click', function() {
                    saveBasedOnActiveTab(false);
                });
            }

            if (saveAndCloseBtn) {
                saveAndCloseBtn.addEventListener('click', function() {
                    saveBasedOnActiveTab(true);
                });
            }

            if (cancelBtn) {
                cancelBtn.addEventListener('click', function() {
                    cancelForm();
                });
            }

            if (legalEntityForm) {
                legalEntityForm.addEventListener('submit', function(e) {
                    e.preventDefault();
                    saveBasedOnActiveTab(false);
                });
            }

            // Wire up editor button – advanced rich text editor
            const editorButton = document.querySelector('.editor-button');
            if (editorButton) {
                editorButton.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    toggleAdvancedRichTextEditor('descriptionInput', editorButton);
                });
            }
            
            // Initialize segment field BEFORE loading data so it's available when populateForm runs
            if (window.SegmentField) {
                try {
                    const segmentLabel = window.I18n ? window.I18n.t('legalEntity.labels.segment') : 'Segment';
                    const sectionTitle = window.I18n ? window.I18n.t('legalEntity.labels.segmentation') : 'SEGMENTATION';
                    segmentField = await SegmentField.init('segmentFieldContainer', {
                        label: segmentLabel,
                        required: true,
                        // Removed defaultValue - let API data set the correct value
                        sectionTitle: sectionTitle,
                        objectType: 'Legal Entity',
                        fieldId: 'legalEntitySegment',
                        errorId: 'legalEntitySegmentError',
                        onChange: async (selectedSegmentId, previousSegmentId) => {
                            return await refreshLegalEntityParentForSelectedSegment(previousSegmentId);
                        }
                    });
                    console.log('Segment field initialized');
                } catch (error) {
                    console.error('Error initializing segment field:', error);
                }
            }
            
            // Load statuses and viewing options for dropdowns
            console.log('Loading dropdown options...');
            await Promise.all([
                loadStatuses(),
                loadViewingOptions(),
                loadParentLegalEntities()
            ]);
            
            console.log('Dropdown options loaded, now loading entity data...');
            
            // Load existing entity data
            await loadEntityData();
            
            // Ensure segment value is set after a short delay to allow segment field options to load
            if (segmentField) {
                setTimeout(() => {
                    // Re-fetch the current value to verify it was set correctly
                    const currentValue = segmentField.getValue();
                    console.log('🔧 Verifying segment field value after load:', currentValue);
                }, 300);
            }
            
            // Initialize custom fields
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'Legal Entity',
                        containerId: 'customFieldsContainer',
                        mode: 'edit',
                        objectId: entityId
                    });
                    console.log('Custom fields initialized:', window.customFieldsContext);
                } catch (error) {
                    console.error('Error initializing custom fields:', error);
                }
            }
            
            console.log('Page initialization completed successfully');
        } catch (error) {
            console.error('Error initializing page:', error);
        }
    }

    // Load statuses from API for dropdown
    async function loadStatuses() {
        try {
            const statusSelect = document.getElementById('statusSelect');
            if (!statusSelect) return;

            const loadingStatuses = window.I18n ? window.I18n.t('legalEntity.messages.loadingStatuses') : 'Loading statuses...';
            statusSelect.innerHTML = `<option value="">${loadingStatuses}</option>`;

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getStatusList !== "function") {
                const notAvailable = window.I18n ? window.I18n.t('legalEntity.placeholders.statusNotAvailable') : 'Status not available';
                statusSelect.innerHTML = `<option value="">${notAvailable}</option>`;
                return;
            }

            const response = await window.BUDG_API_SERVICE.getStatusList();

            if (response && Array.isArray(response)) {
                statusSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = window.I18n ? window.I18n.t('legalEntity.placeholders.selectStatus') : 'Select Status';
                statusSelect.appendChild(defaultOption);

                response.forEach(status => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.name || status.primaryname || status.title;
                    statusSelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data)) {
                statusSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = window.I18n ? window.I18n.t('legalEntity.placeholders.selectStatus') : 'Select Status';
                statusSelect.appendChild(defaultOption);

                response.data.forEach(status => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.name || status.primaryname || status.title;
                    statusSelect.appendChild(option);
                });
            } else {
                const notAvailable = window.I18n ? window.I18n.t('legalEntity.placeholders.statusNotAvailable') : 'Status not available';
                statusSelect.innerHTML = `<option value="">${notAvailable}</option>`;
            }
        } catch (error) {
            console.error('Error loading statuses:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.placeholders.errorLoadingStatuses') : 'Error loading statuses';
            statusSelect.innerHTML = `<option value="">${errorMsg}</option>`;
        }
    }

    // Load viewing options from API for dropdown
    async function loadViewingOptions() {
        try {
            const viewingSelect = document.getElementById('viewingSelect');
            if (!viewingSelect) return;

            const loadingViewing = window.I18n ? window.I18n.t('legalEntity.messages.loadingViewingOptions') : 'Loading viewing options...';
            viewingSelect.innerHTML = `<option value="">${loadingViewing}</option>`;

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getViewingList !== "function") {
                const notAvailable = window.I18n ? window.I18n.t('legalEntity.placeholders.viewingNotAvailable') : 'Viewing not available';
                viewingSelect.innerHTML = `<option value="">${notAvailable}</option>`;
                return;
            }

            const response = await window.BUDG_API_SERVICE.getViewingList();

            if (response && Array.isArray(response)) {
                viewingSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = window.I18n ? window.I18n.t('legalEntity.placeholders.selectViewing') : 'Select Viewing';
                viewingSelect.appendChild(defaultOption);

                response.forEach(viewing => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.primaryname || viewing.title;
                    viewingSelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data)) {
                viewingSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = window.I18n ? window.I18n.t('legalEntity.placeholders.selectViewing') : 'Select Viewing';
                viewingSelect.appendChild(defaultOption);

                response.data.forEach(viewing => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.primaryname || viewing.title;
                    viewingSelect.appendChild(option);
                });
            } else {
                const notAvailable = window.I18n ? window.I18n.t('legalEntity.placeholders.viewingNotAvailable') : 'Viewing not available';
                viewingSelect.innerHTML = `<option value="">${notAvailable}</option>`;
            }
        } catch (error) {
            console.error('Error loading viewing options:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.placeholders.errorLoadingViewing') : 'Error loading viewing options';
            viewingSelect.innerHTML = `<option value="">${errorMsg}</option>`;
        }
    }

    // Set default status (try to find "Active" or use first available)
    function setDefaultStatus() {
        const statusSelect = document.getElementById('statusSelect');
        if (statusSelect && statusSelect.options.length > 1) {
            let foundActive = false;
            for (let i = 1; i < statusSelect.options.length; i++) {
                const optionText = statusSelect.options[i].textContent.toLowerCase();
                if (optionText.includes('active') || optionText.includes('نشط')) {
                    statusSelect.value = statusSelect.options[i].value;
                    foundActive = true;
                    break;
                }
            }
            if (!foundActive) {
                statusSelect.value = statusSelect.options[1].value;
            }
        }
    }

    // Set default viewing (try to find "Public" or use first available)
    function setDefaultViewing() {
        const viewingSelect = document.getElementById('viewingSelect');
        if (viewingSelect && viewingSelect.options.length > 1) {
            let foundPublic = false;
            for (let i = 1; i < viewingSelect.options.length; i++) {
                const optionText = viewingSelect.options[i].textContent.toLowerCase();
                if (optionText.includes('public') ) {
                    viewingSelect.value = viewingSelect.options[i].value;
                    foundPublic = true;
                    break;
                }
            }
            if (!foundPublic) {
                viewingSelect.value = viewingSelect.options[1].value;
            }
        }
    }

    // Load parent legal entities from API for dropdown
    async function loadParentLegalEntities() {
        try {
            const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
            if (!parentLegalEntitySelect) return;

            const loadingParent = window.I18n ? window.I18n.t('legalEntity.messages.loadingParentLegalEntities') : 'Loading parent legal entities...';
            parentLegalEntitySelect.innerHTML = `<option value="">${loadingParent}</option>`;

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getLegalEntities !== "function") {
                const notAvailable = window.I18n ? window.I18n.t('legalEntity.placeholders.parentNotAvailable') : 'Parent legal entities not available';
                parentLegalEntitySelect.innerHTML = `<option value="">${notAvailable}</option>`;
                return;
            }

            const response = await window.BUDG_API_SERVICE.getLegalEntities();

            if (response && Array.isArray(response) && response.length > 0) {
                parentLegalEntitySelect.innerHTML = '';
                
                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = window.I18n ? window.I18n.t('legalEntity.placeholders.selectParent') : 'No Parent Legal Entity';
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
                emptyOption.textContent = window.I18n ? window.I18n.t('legalEntity.placeholders.selectParent') : 'No Parent Legal Entity';
                parentLegalEntitySelect.appendChild(emptyOption);
                
                response.data.forEach((legalEntity) => {
                    const option = document.createElement('option');
                    option.value = legalEntity.id;
                    option.textContent = legalEntity.name || legalEntity.longname || legalEntity.primaryname || legalEntity.title;
                    parentLegalEntitySelect.appendChild(option);
                });
            } else {
                const notAvailable = window.I18n ? window.I18n.t('legalEntity.placeholders.parentNotAvailable') : 'No parent legal entities available';
                parentLegalEntitySelect.innerHTML = `<option value="">${notAvailable}</option>`;
            }
        } catch (error) {
            console.error('Error loading parent legal entities:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.placeholders.errorLoadingParent') : 'Error loading parent legal entities';
            parentLegalEntitySelect.innerHTML = `<option value="">${errorMsg}</option>`;
        }
    }

    async function refreshLegalEntityParentForSelectedSegment(previousSegmentId) {
        const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
        const previousParentId = parentLegalEntitySelect?.value || '';
        await loadParentLegalEntities();

        if (!parentLegalEntitySelect || !previousParentId) {
            return;
        }

        const stillAllowed = Array.from(parentLegalEntitySelect.options || [])
            .some(option => String(option.value) === String(previousParentId));

        if (!stillAllowed) {
            alert('This parent is not valid for the selected segment. Please remove the parent first.');
            return false;
        } else {
            parentLegalEntitySelect.value = previousParentId;
        }
        return true;
    }

    // Tab system functions
    function getLegalEntityTabPanels() {
        // Only hide top-level tab panels. Do not include nested form containers
        // like segmentFieldContainer/customFieldsContainer.
        return [
            document.getElementById('legalEntityRelationshipsContainer'),
            document.getElementById('legalEntityStakeholdersContainer')
        ].filter(Boolean);
    }

    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        const containers = getLegalEntityTabPanels();
        
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                // Add active class to clicked tab
                tab.classList.add('active');
                
                // Hide all containers
                containers.forEach(container => {
                    container.style.display = 'none';
                });
                
                // Also hide impactTab and viewContainer
                const impactTab = document.getElementById('impactTab');
                const viewContainer = document.getElementById('legalEntityViewContainer');
                if (impactTab) {
                    impactTab.style.display = 'none';
                }
                if (viewContainer) {
                    viewContainer.style.display = 'none';
                }
                
                // Show the corresponding container
                const tabName = tab.getAttribute('data-tab');
                
                // Special handling for impact tab
                if (tabName === 'impact') {
                    if (impactTab) {
                        impactTab.style.display = 'block';
                        // Initialize impact edit with legal entity's segment so geography dropdown is filtered
                        if (entityId && window.initImpactEdit) {
                            const segId = segmentField ? segmentField.getValue() : (window._legalEntitySegmentId != null ? window._legalEntitySegmentId : null);
                            window.initImpactEdit(parseInt(entityId), segId);
                        }
                    }
                } else if (tabName === 'view') {
                    // Show view container for details tab
                    if (viewContainer) {
                        viewContainer.style.display = 'grid';
                    }
                } else {
                    const targetContainer = document.getElementById(`legalEntity${tabName.charAt(0).toUpperCase() + tabName.slice(1)}Container`);
                    if (targetContainer) {
                        targetContainer.style.display = 'grid';
                    }
                }
                
                // Load tab-specific content for editing
                loadTabContentForEdit(tabName);
            });
        });
    }

    function loadTabContentForEdit(tabName) {
        switch(tabName) {
            case 'stakeholders':
                // Load stakeholders in edit mode
                if (window.LegalEntityStakeholderEdit) {
                    window.LegalEntityStakeholderEdit.init(entityId);
                }
                break;
            case 'relationships':
                // Load relationships tab content
                loadLegalEntityRelationships(entityId);
                break;
            case 'impact':
                // Impact tab is initialized when tab is clicked
                break;
            case 'view':
                // Main legal entity details (Summary) - already loaded
                break;
            case 'summary':
                // Handle summary tab if it exists separately
                break;
            default:
                // Handle other tabs if needed
                break;
        }
    }

    // Get currently active tab
    function getCurrentActiveTab() {
        const activeTab = document.querySelector('.tab.active');
        return activeTab ? activeTab.getAttribute('data-tab') : 'view';
    }

    // Check if any data has changed across all tabs
    function hasAnyDataChanged() {
        // Check all tabs, not just the active one, to avoid losing unsaved changes
        if (hasDataChanged()) return true;
        
        if (window.LegalEntityStakeholderEdit && window.LegalEntityStakeholderEdit.hasDataChanged && 
            window.LegalEntityStakeholderEdit.hasDataChanged()) return true;
        
        if (window.hasImpactChanges && typeof window.hasImpactChanges === 'function' && 
            window.hasImpactChanges()) return true;
        
        return false;
    }

    // Restore active main tab and (if impact) impact sub-tab after save
    function restoreActiveTab(mainTabName, impactSubTabName) {
        const tabs = document.querySelectorAll('.tab');
        const containers = getLegalEntityTabPanels();
        const impactTab = document.getElementById('impactTab');
        const viewContainer = document.getElementById('legalEntityViewContainer');
        const tabName = mainTabName || 'summary';
        tabs.forEach(t => {
            t.classList.remove('active');
            if (t.getAttribute('data-tab') === tabName) t.classList.add('active');
        });
        containers.forEach(container => { container.style.display = 'none'; });
        if (impactTab) impactTab.style.display = 'none';
        if (viewContainer) viewContainer.style.display = 'none';
        if (tabName === 'impact' && impactTab) {
            impactTab.style.display = 'block';
            if (entityId && window.initImpactEdit) {
                const segId = segmentField ? segmentField.getValue() : (window._legalEntitySegmentId != null ? window._legalEntitySegmentId : null);
                window.initImpactEdit(parseInt(entityId), segId);
            }
            const subTab = document.querySelector(`#impact .sub-tab[data-sub-tab="${impactSubTabName || 'geography'}"]`);
            if (subTab) {
                document.querySelectorAll('#impact .sub-tab').forEach(st => st.classList.remove('active'));
                subTab.classList.add('active');
                document.querySelectorAll('#impact .sub-tab-content').forEach(c => { c.classList.remove('active'); c.style.display = 'none'; });
                const contentId = impactSubTabName === 'geography' ? 'impactGeographyContent' : null;
                const content = contentId ? document.getElementById(contentId) : document.querySelector('#impact .sub-tab-content');
                if (content) { content.classList.add('active'); content.style.display = 'block'; }
            }
        } else if (tabName === 'view' && viewContainer) {
            viewContainer.style.display = 'grid';
        } else {
            const targetContainer = document.getElementById(`legalEntity${(tabName || '').charAt(0).toUpperCase() + (tabName || '').slice(1)}Container`);
            if (targetContainer) targetContainer.style.display = 'grid';
        }
        loadTabContentForEdit(tabName);
    }

    // Save data based on active tab
    async function saveBasedOnActiveTab(closeAfterSave = false) {
        const savingText = window.I18n ? window.I18n.t('legalEntity.messages.saving') : 'Saving...';
        const saveText = window.I18n ? window.I18n.t('legalEntity.buttons.save') : 'Save';
        const saveAndCloseText = window.I18n ? window.I18n.t('legalEntity.buttons.saveAndClose') : 'Save & Close';
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn'), document.getElementById('cancelBtn')];
        const restoreButtons = () => {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn.id === 'saveBtn') btn.textContent = saveText;
                    if (btn.id === 'saveAndCloseBtn') btn.textContent = saveAndCloseText;
                }
            });
        };
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                if (btn.id === 'saveBtn') btn.textContent = savingText;
                if (btn.id === 'saveAndCloseBtn') btn.textContent = savingText;
            }
        });

        const activeMainTab = getCurrentActiveTab();
        console.log(`=== SAVING LEGAL ENTITY (active tab: ${activeMainTab}) ===`);

        try {
            if (activeMainTab === 'relationships') {
                // Relationships tab is read-only for legal entity
                const noDataMsg = window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab';
                showSuccessMessage(noDataMsg, false);
                restoreButtons();
                return true;

            } else if (activeMainTab === 'stakeholders') {
                const stakeholdersHaveChanges = window.LegalEntityStakeholderEdit &&
                    window.LegalEntityStakeholderEdit.hasDataChanged &&
                    window.LegalEntityStakeholderEdit.hasDataChanged();
                if (!stakeholdersHaveChanges) {
                    const noChangesMsg = window.I18n ? window.I18n.t('legalEntity.messages.noChangesToSave') : 'No changes to save.';
                    showSuccessMessage(noChangesMsg, false);
                    restoreButtons();
                    return true;
                }
                await window.LegalEntityStakeholderEdit.saveStakeholders();
                console.log('✅ Stakeholders saved successfully');

            } else if (activeMainTab === 'impact') {
                const impactHasChanges = window.hasImpactChanges && typeof window.hasImpactChanges === 'function' &&
                    window.hasImpactChanges();
                if (!impactHasChanges) {
                    const noChangesMsg = window.I18n ? window.I18n.t('legalEntity.messages.noChangesToSave') : 'No changes to save.';
                    showSuccessMessage(noChangesMsg, false);
                    restoreButtons();
                    return true;
                }
                const impactResult = await window.saveAllImpactData(entityId);
                if (impactResult && impactResult.geography && impactResult.geography.success !== false) {
                    if (window.initImpactEdit && entityId) {
                        const segId = segmentField ? segmentField.getValue() : (window._legalEntitySegmentId != null ? window._legalEntitySegmentId : null);
                        await window.initImpactEdit(entityId, segId);
                    }
                    console.log('✅ Impact saved successfully');
                } else {
                    console.warn('Impact save returned unsuccessful result:', impactResult);
                }

            } else {
                // view (summary) tab
                const formHasChanges = hasDataChanged();
                const hasCustomFieldsContext = window.customFieldsContext && window.customFieldsContext.saveValues;
                if (!formHasChanges && !hasCustomFieldsContext) {
                    const noChangesMsg = window.I18n ? window.I18n.t('legalEntity.messages.noChangesToSave') : 'No changes to save. No data has been modified.';
                    showSuccessMessage(noChangesMsg, false);
                    restoreButtons();
                    return true;
                }

                if (formHasChanges) {
                    const saveSuccess = await saveLegalEntityData();
                    if (!saveSuccess) {
                        restoreButtons();
                        return false;
                    }
                }

                // Save custom fields if context exists
                if (hasCustomFieldsContext && entityId) {
                    try {
                        await window.customFieldsContext.saveValues(entityId);
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }

                // If segment changed, reload impact data (do not save impact from summary)
                const currentLegalSegment = normalizeLegalSegmentId(segmentField ? segmentField.getValue() : null);
                const legalSegmentChanged = currentLegalSegment !== normalizeLegalSegmentId(originalLegalSegmentId);
                if (legalSegmentChanged && window.initImpactEdit) {
                    console.log('=== Segment changed; reloading legal entity impact from server ===');
                    const segId = segmentField ? segmentField.getValue() : (window._legalEntitySegmentId != null ? window._legalEntitySegmentId : null);
                    await window.initImpactEdit(entityId, segId);
                }

                originalLegalSegmentId = normalizeLegalSegmentId(segmentField ? segmentField.getValue() : null);
            }

            const savedMsg = window.I18n ? window.I18n.t('legalEntity.messages.updatesSaved') : 'UPDATES SAVED';
            showSuccessMessage(savedMsg);

            if (closeAfterSave) {
                setTimeout(() => {
                    window.location.href = `/view/LegalEntity/legal-entity.html?id=${entityId}`;
                }, 1500);
            }

            return true;
        } catch (error) {
            console.error('Error saving:', error);
            alert('Error saving: ' + (error.message || 'Unknown error'));
            return false;
        } finally {
            restoreButtons();
        }
    }

    // Save legal entity main data (extracted from saveLegalEntity function)
    async function saveLegalEntityData() {
        // Check if data has changed - caller is responsible for checking this
        // before calling, so just return true silently if no changes
        if (!hasDataChanged()) {
            console.log('No main form changes to save, skipping summary data save.');
            return true; // Return true since no error occurred
        }
        
        console.log('Data has changed, proceeding with save...');
        
        // Get form elements
        const nameInput = document.getElementById('nameInput');
        const shortNameInput = document.getElementById('shortNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const parentLegalEntitySelect = document.getElementById('parentLegalEntitySelect');
        
        // Validate required fields
        if (!nameInput || !nameInput.value.trim()) {
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.longNameRequired') : 'Long Name is required';
            alert(errorMsg);
            nameInput?.focus();
            return false;
        }

        if (!shortNameInput || !shortNameInput.value.trim()) {
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.shortNameRequired') : 'Short Name is required';
            alert(errorMsg);
            shortNameInput?.focus();
            return false;
        }

        if (!statusSelect || !statusSelect.value) {
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.statusRequired') : 'BUDG Status is required';
            alert(errorMsg);
            statusSelect?.focus();
            return false;
        }

        if (!viewingSelect || !viewingSelect.value) {
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.viewingRequired') : 'BUDG Viewing is required';
            alert(errorMsg);
            viewingSelect?.focus();
            return false;
        }
        

        // Client-side uniqueness checks (Long Name, Short Name) - only if names have changed
        try {
            if (typeof window.BUDG_API_SERVICE?.getLegalEntities === 'function') {
                console.log('Checking for duplicate names in database...');
                const list = await window.BUDG_API_SERVICE.getLegalEntities();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                
                const longNameVal = (nameInput.value || '').trim().toLowerCase();
                const shortNameVal = (shortNameInput.value || '').trim().toLowerCase();
                
                console.log('Checking against', rows.length, 'existing entities');
                
                // Get current entity data to compare
                const currentEntity = await window.BUDG_API_SERVICE.getLegalEntityById(entityId);
                const currentData = currentEntity.data || currentEntity;
                const currentLongName = (currentData.longname || currentData.Name || currentData.primaryname || currentData.name || '').trim().toLowerCase();
                const currentShortName = (currentData.shortname || currentData.Short_Name || currentData.short_name || currentData.shortName || '').trim().toLowerCase();
                
                // Check for duplicate Long Name only if it has changed
                if (longNameVal !== currentLongName) {
                    const longNameClash = rows.some(r => {
                        const existingId = String(r.ID || r.id || '');
                        if (existingId === String(entityId)) return false; // Skip current entity
                        const existingLongName = String(r.Name || r.primaryname || r.name || r.longname || '').trim().toLowerCase();
                        return existingLongName === longNameVal && existingLongName !== '';
                    });
                    if (longNameClash) {
                        const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.longNameExists') : 'Error: Long Name already exists in database.\nThe Long Name must be unique to avoid duplicates.';
                        alert(errorMsg);
                        nameInput.focus();
                        return false;
                    }
                }
                
                // Check for duplicate Short Name only if it has changed
                if (shortNameVal !== currentShortName) {
                    const shortNameClash = rows.some(r => {
                        const existingId = String(r.ID || r.id || '');
                        if (existingId === String(entityId)) return false; // Skip current entity
                        const existingShortName = String(r.Short_Name || r.short_name || r.shortName || r.shortname || '').trim().toLowerCase();
                        return existingShortName === shortNameVal && existingShortName !== '';
                    });
                    if (shortNameClash) {
                        const errorMsg = window.I18n ? window.I18n.t('legalEntity.validation.shortNameExists') : 'Error: Short Name already exists in database.\nThe Short Name must be unique to avoid duplicates.';
                        alert(errorMsg);
                        shortNameInput.focus();
                        return false;
                    }
                }
                
                console.log('No duplicate names found, proceeding with save...');
            }
        } catch (error) {
            console.warn('Client-side validation failed, falling back to server-side validation:', error);
        }

        // Collect form data
        const formData = {
            longname: nameInput.value.trim(),
            shortname: shortNameInput.value.trim(),
            description: descriptionInput?.value?.trim() || '',
            status: parseInt(statusSelect.value) || 1,
            is_public: parseInt(viewingSelect.value) || 1,
            parent_id: parentLegalEntitySelect?.value || null,
            segmentId: segmentField ? segmentField.getValue() : null
        };

        // Call API to update legal entity
        console.log('Sending update request with data:', formData);
        
        let response;
        try {
            response = await window.BUDG_API_SERVICE.updateLegalEntity(entityId, formData);
        } catch (apiError) {
            // Handle "no changes detected" from backend as non-fatal
            const errorMessage = (apiError.message || '').toLowerCase();
            if (errorMessage.includes('no changes detected') || errorMessage.includes('identical')) {
                console.log('Backend detected no actual changes - treating as success (client-side detection was a false positive).');
                return true; // Not an error, just nothing to update
            }
            // Re-throw actual errors
            throw apiError;
        }
        
        console.log('Update response:', response);

        // Handle API response (use same robust success detection as saveLegalEntity)
        if (isUpdateSuccess(response)) {
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(entityId);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }
            
            console.log('✅ Legal entity summary data saved successfully');
            
            // Update original data to reflect changes
            originalData = {
                longname: formData.longname,
                shortname: formData.shortname,
                description: formData.description,
                status: formData.status,
                is_public: formData.is_public,
                parent_id: formData.parent_id
            };
            
            return true;
        } else {
            console.warn('Update response did not indicate success:', response);
            return false;
        }
    }

    // Legal Entity Relationships Tab Functionality
    async function loadLegalEntityRelationships(id) {
        const container = document.getElementById('legalEntityRelationshipsContainer');
        if (!container) return;

        const hierarchyTitle = window.I18n ? window.I18n.t('legalEntity.relationships.title') : 'LEGAL ENTITY HIERARCHY';
        const showRelationships = window.I18n ? window.I18n.t('legalEntity.relationships.showRelationships') : 'Show Relationships';
        const legalEntityLabel = window.I18n ? window.I18n.t('legalEntity.relationships.legalEntity') : 'Legal Entity';
        const descriptionLabel = window.I18n ? window.I18n.t('legalEntity.relationships.description') : 'Description';
        const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...';
        const zeroRecords = window.I18n ? window.I18n.t('legalEntity.relationships.zeroRecords') : '0 records';
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-hierarchy">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${hierarchyTitle}</div>
                        <div class="hierarchy-actions">
                            <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem;color:var(--text-muted,#6b7280);">
                                <input type="checkbox" checked style="margin-right:.3rem;">
                                ${showRelationships}
                            </label>
                            <button type="button" class="btn btn-secondary" id="editBtn2"><i class="fas fa-cog"></i></button>
                        </div>
                    </div>
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>${legalEntityLabel}</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>${descriptionLabel}</span><i class="fas fa-sort"></i></div></th>
                            </tr>
                        </thead>
                        <tbody id="relationshipsTbody">
                            <tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${loadingMsg}</td></tr>
                        </tbody>
                    </table>
                    <div class="hierarchy-footer" id="hierarchyFooter">
                        ${zeroRecords}
                    </div>
                </div>
            </div>
        `;

        // Load hierarchy data
        await loadLegalHierarchy(id);
    }

    // Show success/error message (like product-edit.js)
    function showSuccessMessage(message, isError = false) {
        const existingMessage = document.getElementById('success-message');
        if (existingMessage) {
            existingMessage.remove();
        }
        
        const successDiv = document.createElement('div');
        successDiv.id = 'success-message';
        const backgroundColor = isError ? '#ef4444' : '#248567';
        successDiv.style.cssText = `
            position: fixed;
            top: 80px;
            left: 50%;
            transform: translateX(-50%);
            background-color: ${backgroundColor};
            color: white;
            padding: 12px 24px;
            border-radius: 6px;
            font-weight: 600;
            font-size: 14px;
            z-index: 1000;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            animation: slideDown 0.3s ease-out;
        `;
        successDiv.textContent = message;
        
        const styleId = 'success-message-style';
        let style = document.getElementById(styleId);
        if (!style) {
            style = document.createElement('style');
            style.id = styleId;
            style.textContent = `
                @keyframes slideDown {
                    from {
                        opacity: 0;
                        transform: translateX(-50%) translateY(-20px);
                    }
                    to {
                        opacity: 1;
                        transform: translateX(-50%) translateY(0);
                    }
                }
                @keyframes slideUp {
                    from {
                        opacity: 1;
                        transform: translateX(-50%) translateY(0);
                    }
                    to {
                        opacity: 0;
                        transform: translateX(-50%) translateY(-20px);
                    }
                }
            `;
            document.head.appendChild(style);
        }
        document.body.appendChild(successDiv);
        
        setTimeout(() => {
            if (successDiv.parentNode) {
                successDiv.style.animation = 'slideUp 0.3s ease-out';
                setTimeout(() => {
                    if (successDiv.parentNode) {
                        successDiv.remove();
                    }
                }, 300);
            }
        }, 3000);
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Build direct lineage tree (current + ancestors + descendants + siblings)
    function buildDirectLineageTree(legalEntities, currentLegalEntityId) {
        const byId = new Map();
        legalEntities.forEach(le => byId.set(parseInt(le.id), le));

        const current = byId.get(parseInt(currentLegalEntityId));
        if (!current) return [];

        const ancestors = new Set();
        const descendants = new Set();

        // Add current legal entity
        descendants.add(parseInt(currentLegalEntityId));

        // Find all ancestors
        let parent = current;
        while (parent && parent.parentId) {
            const parentId = parseInt(parent.parentId);
            if (byId.has(parentId)) {
                parent = byId.get(parentId);
                ancestors.add(parentId);
            } else {
                break;
            }
        }

        // Find all descendants (including current legal entity's children and their descendants)
        function findDescendants(legalEntityId) {
            legalEntities.forEach(le => {
                if (parseInt(le.parentId) === legalEntityId) {
                    const childId = parseInt(le.id);
                    descendants.add(childId);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentLegalEntityId));

        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = legalEntities.filter(le => {
                const leParentId = parseInt(le.parentId);
                const leId = parseInt(le.id);
                return leParentId === parentId && leId !== parseInt(currentLegalEntityId);
            });

            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }

        // Include the current legal entity, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentLegalEntityId), ...ancestors, ...descendants]);

        // Filter legal entities to include only the complete family tree
        return legalEntities.filter(le => {
            const id = parseInt(le.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure
    function buildHierarchyTree(legalEntities, rootId) {
        const byId = new Map();
        const byParent = new Map();
        
        legalEntities.forEach(le => {
            byId.set(parseInt(le.id), le);
            const parentId = parseInt(le.parentId) || 0;
            if (!byParent.has(parentId)) byParent.set(parentId, []);
            byParent.get(parentId).push(le);
        });

        const rows = [];
        const parentMap = new Map();

        function buildRows(parentId, depth = 0) {
            const children = byParent.get(parentId) || [];
            children.forEach(child => {
                const childId = parseInt(child.id);
                const childChildren = byParent.get(childId) || [];
                const hasChildren = childChildren.length > 0;
                const childCount = childChildren.length;
                
                rows.push({
                    node: child,
                    depth,
                    childCount,
                    hasChildren
                });
                
                parentMap.set(childId, parentId);
                
                if (hasChildren) {
                    buildRows(childId, depth + 1);
                }
            });
        }

        buildRows(rootId);
        
        return { rows, parentMap: byParent };
    }

    // Render legal entity hierarchy table following Regulatory Theme pattern
    function renderLegalEntityTable(hierarchyRows, currentId) {
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.longName ?? node.longname ?? node.shortname ?? node.shortName ?? node.displayName ?? node.name ?? 'Unnamed Legal Entity';
            const desc = node.description ?? node.Description ?? '';
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'le-link current-le-link' : 'le-link';
            const link = `<a class="${linkClass}" href="/view/LegalEntity/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-building item-icon"></i><span class="le-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize legal entity hierarchy interactions following Regulatory Theme pattern
    function initLegalEntityInteractions(containerEl, hierarchyRows) {
        containerEl.addEventListener('click', function(e) {
            if (e.target.closest('.tree-expander')) {
                e.preventDefault();
                e.stopPropagation();
                
                const button = e.target.closest('.tree-expander');
                const row = button.closest('tr');
                const parentId = parseInt(row.dataset.id);
                const currentDepth = parseInt(row.dataset.depth);
                const icon = button.querySelector('i');
                
                // Toggle icon
                if (icon.classList.contains('fa-caret-down')) {
                    icon.classList.remove('fa-caret-down');
                    icon.classList.add('fa-caret-right');
                } else {
                    icon.classList.remove('fa-caret-right');
                    icon.classList.add('fa-caret-down');
                }
                
                // Toggle children visibility
                const tbody = row.parentNode;
                const rows = Array.from(tbody.querySelectorAll('tr'));
                const currentIndex = rows.indexOf(row);
                
                // Find all direct children
                for (let i = currentIndex + 1; i < rows.length; i++) {
                    const childRow = rows[i];
                    const childDepth = parseInt(childRow.dataset.depth);
                    
                    if (childDepth <= currentDepth) {
                        break; // We've reached a sibling or parent level
                    }
                    
                    if (childDepth === currentDepth + 1) {
                        // This is a direct child
                        if (icon.classList.contains('fa-caret-right')) {
                            childRow.style.display = 'none';
                        } else {
                            childRow.style.display = '';
                        }
                    } else if (childDepth > currentDepth + 1) {
                        // This is a grandchild or deeper - hide/show based on parent state
                        if (icon.classList.contains('fa-caret-right')) {
                            childRow.style.display = 'none';
                        }
                    }
                }
            }
        });
    }

    // Load legal entity hierarchy data
    async function loadLegalHierarchy(id) {
        const container = document.querySelector('.relationships-hierarchy');
        if (!container) return;
        
        try {
            const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...';
            container.innerHTML = `<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${loadingMsg}</div>`;
            
            // Fetch all legal entities from hierarchy endpoint
            console.log('Fetching legal entities from /api/LegalEntity/hierarchy');
            const response = await fetch('/api/LegalEntity/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const legalEntities = await response.json();
            
            console.log('Legal entities loaded for hierarchy:', legalEntities);
            
            if (!Array.isArray(legalEntities) || legalEntities.length === 0) {
                const noDataMsg = window.I18n ? window.I18n.t('legalEntity.relationships.noData') : 'No legal entities found in database';
                container.innerHTML = `<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">${noDataMsg}</div>`;
                return;
            }

            // Build direct lineage tree (current + ancestors + descendants + siblings)
            const filteredLegalEntities = buildDirectLineageTree(legalEntities, id);
            
            if (filteredLegalEntities.length === 0) {
                const noRelatedMsg = window.I18n ? window.I18n.t('legalEntity.relationships.noRelated') : 'No related legal entities found';
                container.innerHTML = `<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">${noRelatedMsg}</div>`;
                return;
            }

            // Build hierarchy tree
            const hierarchyRows = buildHierarchyTree(filteredLegalEntities, 0);
            
            // Render table
            const hierarchyTitle = window.I18n ? window.I18n.t('legalEntity.relationships.title') : 'LEGAL ENTITY HIERARCHY';
            const legalEntityLabel = window.I18n ? window.I18n.t('legalEntity.relationships.legalEntity') : 'Legal Entity';
            const descriptionLabel = window.I18n ? window.I18n.t('legalEntity.relationships.description') : 'Description';
            const recordText = filteredLegalEntities.length === 1 
                ? (window.I18n ? window.I18n.t('legalEntity.relationships.records', {count: filteredLegalEntities.length}) : `${filteredLegalEntities.length} record`)
                : (window.I18n ? window.I18n.t('legalEntity.relationships.recordsPlural', {count: filteredLegalEntities.length}) : `${filteredLegalEntities.length} records`);
            const tableHtml = `
                <div class="hierarchy-header">
                    <div class="hierarchy-title">${hierarchyTitle}</div>
                    <div class="hierarchy-actions">
                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                                    </div>
                                    </div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>${legalEntityLabel}</th>
                                <th>${descriptionLabel}</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderLegalEntityTable(hierarchyRows, id)}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${recordText}
                </div>
            `;
            
            container.innerHTML = tableHtml;
            
            // Initialize interactions
            initLegalEntityInteractions(container, hierarchyRows);
            
        } catch (error) {
            console.error('Failed to load legal entity hierarchy:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToLoadHierarchy', {error: error.message || 'Unknown error'}) : `Failed to load hierarchy data: ${error.message || 'Unknown error'}`;
            container.innerHTML = `<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">${errorMsg}</div>`;
        }
    }

    // Add expand/collapse functionality to legal entity hierarchy
    function addLegalExpandCollapseFunctionality() {
        const expandIcons = document.querySelectorAll('.relationships-hierarchy .expand-icon');
        
        expandIcons.forEach(function(icon) {
            icon.addEventListener('click', function() {
                const groupIndex = this.getAttribute('data-group');
                const parentRow = this.closest('.hierarchy-parent');
                const childRows = document.querySelectorAll(`.hierarchy-row[data-group="${groupIndex}"]`);
                
                if (this.classList.contains('expanded')) {
                    // Collapse
                    this.classList.remove('expanded');
                    this.classList.add('collapsed');
                    this.className = this.className.replace('fa-chevron-down', 'fa-chevron-right');
                    
                    // Hide child rows
                    childRows.forEach(function(row) {
                        row.style.display = 'none';
                    });
                } else {
                    // Expand
                    this.classList.remove('collapsed');
                    this.classList.add('expanded');
                    this.className = this.className.replace('fa-chevron-right', 'fa-chevron-down');
                    
                    // Show child rows
                    childRows.forEach(function(row) {
                        row.style.display = '';
                    });
                }
            });
        });
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (text == null) return '';
        const map = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return text.toString().replace(/[&<>"']/g, function(m) { return map[m]; });
    }

    // Initialize tabs after DOM is ready
    setTimeout(() => {
        initTabs();
    }, 100);
});


