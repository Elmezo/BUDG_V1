// Client Edit Page JavaScript
document.addEventListener('DOMContentLoaded', async function() {
    console.log('DOMContentLoaded event fired');
    
    // Check if we're on the edit page (not view page)
    if (!document.getElementById('clientForm')) {
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
        alert(window.I18n ? window.I18n.t('client.messages.noIdProvided') : 'No entity ID provided');
        window.location.href = '/';
        return;
    }

    // Initialize lock
    const lockAcquired = await window.LockInitHelper.initializeLock('client', parseInt(entityId), 'client');
    if (!lockAcquired) {
        return; // Lock initialization failed, user was redirected
    }

    console.log('Entity ID found:', entityId);

    // Store original data for change detection
    let originalData = null;
    let segmentField = null; // Segment selection widget reference

    // Check if form data has changed
    function hasDataChanged() {
        if (!originalData) return true; // If no original data, consider it changed
        
        const primaryNameInput = document.getElementById('primaryNameInput');
        const longNameInput = document.getElementById('longNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const parentClientSelect = document.getElementById('parentClientSelect');
        
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
            primary_name: primaryNameInput?.value?.trim() || '',
            long_name: longNameInput?.value?.trim() || '',
            description: descriptionInput?.value?.trim() || '',
            status: parseInt(statusSelect?.value) || 1,
            lifecycle: parseInt(lifecycleSelect?.value) || 1,
            is_public: parseInt(viewingSelect?.value) || 1,
            parent_id: parentClientSelect?.value || null,
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
            currentData.primary_name !== originalData.primary_name ||
            currentData.long_name !== originalData.long_name ||
            currentData.description !== originalData.description ||
            currentData.status !== originalData.status ||
            currentData.lifecycle !== originalData.lifecycle ||
            currentData.is_public !== originalData.is_public ||
            currentParentId !== originalParentId ||
            currentSegId !== originalSegId
        );
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

    function ensureSegmentFieldContainerVisible() {
        const container = document.getElementById('segmentFieldContainer');
        if (!container) return null;

        container.style.setProperty('display', 'block', 'important');
        container.style.setProperty('visibility', 'visible', 'important');
        container.style.setProperty('opacity', '1', 'important');
        container.style.setProperty('height', 'auto', 'important');
        container.style.setProperty('overflow', 'visible', 'important');
        container.style.setProperty('min-height', '40px', 'important');
        return container;
    }

    function renderSegmentFieldFallback(message) {
        const container = ensureSegmentFieldContainerVisible();
        if (!container) return;

        let fallback = document.getElementById('clientSegmentFallback');
        if (!fallback) {
            fallback = document.createElement('div');
            fallback.id = 'clientSegmentFallback';
            fallback.className = 'form-error';
            fallback.style.marginTop = '0.5rem';
            container.appendChild(fallback);
        }
        fallback.textContent = message;
    }

    async function initializeSegmentField(retryCount = 0) {
        const container = ensureSegmentFieldContainerVisible();
        if (!container) {
            console.warn('Segment field container not found');
            return null;
        }

        if (!window.SegmentField) {
            if (retryCount < 5) {
                await new Promise(resolve => setTimeout(resolve, 200 * (retryCount + 1)));
                return initializeSegmentField(retryCount + 1);
            }
            renderSegmentFieldFallback('Segment field failed to load. Please refresh the page.');
            return null;
        }

        try {
            segmentField = await window.SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                sectionTitle: 'SEGMENTATION',
                objectType: 'Client',
                fieldId: 'clientSegment',
                errorId: 'clientSegmentError',
                onChange: async (selectedSegmentId, previousSegmentId) => {
                    return await refreshClientParentForSelectedSegment(previousSegmentId);
                }
            });
            container._segmentFieldInstance = segmentField;
            return segmentField;
        } catch (err) {
            console.error('Error initializing segment field:', err);
            if (retryCount < 2) {
                await new Promise(resolve => setTimeout(resolve, 250 * (retryCount + 1)));
                return initializeSegmentField(retryCount + 1);
            }
            renderSegmentFieldFallback('Segment field could not be initialized. Please refresh the page.');
            return null;
        }
    }

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

    // Helper function to find option value by text content
    function findOptionValueByText(selectElement, searchText) {
        if (!selectElement || !searchText) return null;
        
        console.log('FindOptionValueByText - Searching for:', searchText, 'in element:', selectElement.id);
        console.log('FindOptionValueByText - Available options:', Array.from(selectElement.options).map(opt => ({value: opt.value, text: opt.text})));
        
        for (let i = 0; i < selectElement.options.length; i++) {
            const option = selectElement.options[i];
            const optionText = option.text.toLowerCase().trim();
            const searchTextLower = searchText.toLowerCase().trim();
            
            console.log('FindOptionValueByText - Comparing:', optionText, 'with:', searchTextLower);
            
            // Try exact match first
            if (optionText === searchTextLower) {
                console.log('FindOptionValueByText - Exact match found:', option.value);
                return option.value;
            }
            
            // Try partial match (contains)
            if (optionText.includes(searchTextLower) || searchTextLower.includes(optionText)) {
                console.log('FindOptionValueByText - Partial match found:', option.value);
                return option.value;
            }
        }
        
        console.log('FindOptionValueByText - No match found for:', searchText);
        return null;
    }

    // Load existing data
    async function loadEntityData() {
        try {
            console.log('Loading client data for ID:', entityId);
            const entity = await window.BUDG_API_SERVICE.getClientById(entityId);
            const data = entity.data || entity;
            
            console.log('Loaded entity data:', data);
            console.log('LoadEntityData - All lifecycle-related fields in data:', {
                Lifecycle_ID: data.Lifecycle_ID,
                lifecycle: data.lifecycle,
                lifecycle_id: data.lifecycle_id,
                lifecycleId: data.lifecycleId,
                LifecycleId: data.LifecycleId,
                Lifecycle: data.Lifecycle
            });
            
            // Get form elements again to ensure they're available
            const pageTitle = document.getElementById('pageTitle');
            const primaryNameInput = document.getElementById('primaryNameInput');
            const longNameInput = document.getElementById('longNameInput');
            const descriptionInput = document.getElementById('descriptionInput');
            const statusSelect = document.getElementById('statusSelect');
            const lifecycleSelect = document.getElementById('lifecycleSelect');
            const viewingSelect = document.getElementById('viewingSelect');
            const parentClientSelect = document.getElementById('parentClientSelect');
            const segmentId = segmentField ? segmentField.getValue() : 1;
            const segmentVal = data.segmentId || data.segment_id || data.Segment_ID;
            
            // Update page title
            if (pageTitle) {
                const entityName = data.PrimaryName || data.primaryName || data.primary_name || data.primaryname || data.name || (window.I18n ? window.I18n.t('client.page.title') : 'Client');
                pageTitle.textContent = (window.I18n ? window.I18n.t('client.page.editTitle', { name: entityName }) : `Edit ${entityName}`);
                console.log('Updated page title to:', entityName);
            }
            
            // Populate form fields with correct API field names
            if (primaryNameInput) {
                const primaryName = data.PrimaryName || data.primaryName || data.primary_name || data.primaryname || data.name || '';
                primaryNameInput.value = primaryName;
                console.log('Set Primary Name to:', primaryName);
            }
            
            if (longNameInput) {
                const longName = data['Long Name'] || data.LongName || data.longName || data.long_name || data.longname || '';
                longNameInput.value = longName;
                console.log('Set Long Name to:', longName);
            }
            
            if (descriptionInput) {
                const description = data.Definition || data.description || data.Description || data.definition || '';
                descriptionInput.value = description;
                console.log('Set Description to:', description);
            }
            
            // Set status, lifecycle and viewing selections after they're loaded
            if (statusSelect) {
                // Try both ID and text values from the new API format
                const statusText = data.BUDG_status || data.Status_ID || data.status || data.status_id;
                const statusId = data.status_id; // This is the numeric ID
                
                console.log('Available status data:', {
                    BUDG_status: data.BUDG_status,
                    status_id: data.status_id,
                    Status_ID: data.Status_ID,
                    status: data.status,
                    finalText: statusText,
                    finalId: statusId
                });
                
                if (statusText) {
                    console.log('Attempting to set status to:', statusText);
                    
                    // First try direct value assignment (in case it's an ID)
                    statusSelect.value = statusText;
                    
                    // If that didn't work, try to find by text
                    if (statusSelect.value !== statusText) {
                        const foundValue = findOptionValueByText(statusSelect, statusText);
                        if (foundValue) {
                            statusSelect.value = foundValue;
                            console.log('Status set by text search to value:', foundValue);
                        } else {
                            console.log('Could not find matching status option, setting default');
                            setDefaultStatus();
                        }
                    } else {
                        console.log('Status set directly to:', statusText);
                    }
                    
                    console.log('Final status value:', statusSelect.value);
                } else {
                    console.log('No status data found, setting default');
                    setDefaultStatus();
                }
            }
            
            if (lifecycleSelect) {
                console.log('LifecycleSelect element found:', !!lifecycleSelect);
                console.log('LifecycleSelect options available:', lifecycleSelect.options.length);
                console.log('LifecycleSelect current options:', Array.from(lifecycleSelect.options).map(opt => ({value: opt.value, text: opt.text})));
                
                // Try both text and ID values from the new API format
                const lifecycleText = data.lifecycle || data.Lifecycle_ID || data.lifecycle_id || data.lifecycleId || data.LifecycleId;
                const lifecycleId = data.lifecycle_id; // This is the numeric ID
                
                console.log('Available lifecycle data:', {
                    lifecycle: data.lifecycle,
                    lifecycle_id: data.lifecycle_id,
                    Lifecycle_ID: data.Lifecycle_ID,
                    lifecycleId: data.lifecycleId,
                    LifecycleId: data.LifecycleId,
                    finalText: lifecycleText,
                    finalId: lifecycleId
                });
                
                if (lifecycleText) {
                    console.log('Attempting to set lifecycle to:', lifecycleText);
                    
                    // First try direct value assignment (in case it's an ID)
                    lifecycleSelect.value = lifecycleText;
                    
                    // If that didn't work, try to find by text
                    if (lifecycleSelect.value !== lifecycleText) {
                        const foundValue = findOptionValueByText(lifecycleSelect, lifecycleText);
                        if (foundValue) {
                            lifecycleSelect.value = foundValue;
                            console.log('Lifecycle set by text search to value:', foundValue);
                        } else {
                            console.log('Could not find matching lifecycle option, setting default');
                            setDefaultLifecycle();
                        }
                    } else {
                        console.log('Lifecycle set directly to:', lifecycleText);
                    }
                    
                    console.log('Final lifecycle value:', lifecycleSelect.value);
                    console.log('Selected lifecycle option text:', lifecycleSelect.options[lifecycleSelect.selectedIndex]?.text);
                } else {
                    console.log('No lifecycle data found, setting default');
                    setDefaultLifecycle();
                }
            } else {
                console.error('LifecycleSelect element not found!');
            }
            
            if (viewingSelect) {
                // Try both text and ID values from the new API format
                const viewingText = data.BUDG_viewing || data.IsPublic_ID || data.isPublic || data.is_public || data.viewing_id || data.Viewing_ID || data.viewingId || data.ViewingId;
                const viewingId = data.is_public_id; // This is the numeric ID
                
                console.log('Available viewing data:', {
                    BUDG_viewing: data.BUDG_viewing,
                    is_public_id: data.is_public_id,
                    IsPublic_ID: data.IsPublic_ID,
                    isPublic: data.isPublic,
                    is_public: data.is_public,
                    viewing_id: data.viewing_id,
                    Viewing_ID: data.Viewing_ID,
                    viewingId: data.viewingId,
                    ViewingId: data.ViewingId,
                    finalText: viewingText,
                    finalId: viewingId
                });
                
                if (viewingText) {
                    console.log('Attempting to set viewing to:', viewingText);
                    
                    // First try direct value assignment (in case it's an ID)
                    viewingSelect.value = viewingText;
                    
                    // If that didn't work, try to find by text
                    if (viewingSelect.value !== viewingText) {
                        const foundValue = findOptionValueByText(viewingSelect, viewingText);
                        if (foundValue) {
                            viewingSelect.value = foundValue;
                            console.log('Viewing set by text search to value:', foundValue);
                        } else {
                            console.log('Could not find matching viewing option, setting default');
                            setDefaultViewing();
                        }
                    } else {
                        console.log('Viewing set directly to:', viewingText);
                    }
                    
                    console.log('Final viewing value:', viewingSelect.value);
                } else {
                    console.log('No viewing data found, setting default');
                    setDefaultViewing();
                }
            }
            
            // Set parent client selection
            if (parentClientSelect) {
                const parentValue = data.Parent_ID || data.parentId || data.parent_id || data.parent_client_id;
                if (parentValue) {
                    parentClientSelect.value = parentValue;
                    console.log('Set Parent Client to:', parentValue);
                } else {
                    // Set to empty (No Parent Client) if no existing value
                    parentClientSelect.value = '';
                    console.log('Set Parent Client to empty (No Parent)');
                }
            }
            
            // Set segment value if available (check for null/undefined, not falsy, since 0 could be valid)
            const serverSegmentId = data.segmentId ?? data.segment_id ?? data.Segment_ID;
            console.log('🔍 Client segment data:', {
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
                    console.warn('⚠️ No valid segment ID found in client data. segmentId:', serverSegmentId);
                }
            } else {
                console.warn('⚠️ Segment field not initialized yet');
            }
            
            // Store original data for change detection
            const resolvedSegmentId = (serverSegmentId != null && serverSegmentId !== undefined && serverSegmentId !== -1) 
                ? parseInt(serverSegmentId, 10) 
                : (segmentField ? segmentField.getValue() : null);
            
            originalData = {
                primary_name: data.PrimaryName || data.primaryName || data.primary_name || data.primaryname || data.name || '',
                long_name: data['Long Name'] || data.LongName || data.longName || data.long_name || data.longname || '',
                description: data.Definition || data.description || data.Description || data.definition || '',
                status: parseInt(data.Status_ID || data.status || data.status_id || data.statusId || 1),
                lifecycle: parseInt(data.Lifecycle_ID || data.lifecycle || data.lifecycle_id || data.lifecycleId || data.LifecycleId || 1),
                is_public: parseInt(data.IsPublic_ID || data.isPublic || data.is_public || data.viewing_id || data.Viewing_ID || data.viewingId || data.ViewingId || 1),
                parent_id: data.Parent_ID || data.parentId || data.parent_id || data.parent_client_id || null,
                segment_id: resolvedSegmentId
            };
            
            console.log('Original data stored:', originalData);
            console.log('Form fields populated successfully');
            
            // Double-check that fields were actually populated
            setTimeout(() => {
                console.log('Verifying form field values:');
                console.log('Primary Name:', primaryNameInput?.value);
                console.log('Long Name:', longNameInput?.value);
                console.log('Description:', descriptionInput?.value);
                console.log('Status:', statusSelect?.value);
                console.log('Lifecycle:', lifecycleSelect?.value);
                console.log('Viewing:', viewingSelect?.value);
            }, 100);
            
        } catch (error) {
            console.error('Error loading client data:', error);
            alert(window.I18n ? window.I18n.t('client.messages.failedToLoad') : 'Failed to load client data. Please try again.');
        }
    }

    // Save client to API
    async function saveClient(closeAfterSave = false) {
        try {
            // Check if data has changed
            if (!hasDataChanged()) {
                // Still save custom fields even if main form hasn't changed
                if (window.customFieldsContext && window.customFieldsContext.saveValues && entityId) {
                    try {
                        await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                        console.log('✅ Custom fields saved (no other changes)');
                        alert(window.I18n ? window.I18n.t('client.messages.saved') : 'Custom fields saved successfully.');
                    } catch (cfError) {
                        console.error('Error saving custom fields:', cfError);
                    }
                } else {
                    alert(window.I18n ? window.I18n.t('client.messages.noChangesToSave') : 'No changes to save. No data has been modified.');
                }
                return;
            }
            
            console.log('Data has changed, proceeding with save...');
            
            // Get form elements
            const primaryNameInput = document.getElementById('primaryNameInput');
            const longNameInput = document.getElementById('longNameInput');
            const descriptionInput = document.getElementById('descriptionInput');
            const statusSelect = document.getElementById('statusSelect');
            const lifecycleSelect = document.getElementById('lifecycleSelect');
            const viewingSelect = document.getElementById('viewingSelect');
            const parentClientSelect = document.getElementById('parentClientSelect');
            
            // Validate required fields
            if (!primaryNameInput || !primaryNameInput.value.trim()) {
                alert(window.I18n ? window.I18n.t('client.validation.primaryNameRequired') : 'Primary Name is required');
                primaryNameInput?.focus();
                return;
            }

            if (!descriptionInput || !descriptionInput.value.trim()) {
                alert(window.I18n ? window.I18n.t('client.validation.descriptionRequired') : 'Description is required');
                descriptionInput?.focus();
                return;
            }

            if (!statusSelect || !statusSelect.value) {
                alert(window.I18n ? window.I18n.t('client.validation.statusRequired') : 'BUDG Status is required');
                statusSelect?.focus();
                return;
            }

            if (!lifecycleSelect || !lifecycleSelect.value) {
                alert('Lifecycle is required');
                lifecycleSelect?.focus();
                return;
            }

            if (!viewingSelect || !viewingSelect.value) {
                alert(window.I18n ? window.I18n.t('client.validation.viewingRequired') : 'BUDG Viewing is required');
                viewingSelect?.focus();
                return;
            }

            if (segmentField && !segmentField.validate()) {
                alert('Segment is required');
                return;
            }

            // Client-side uniqueness checks (Primary Name) - only if names have changed
            try {
                if (typeof window.BUDG_API_SERVICE?.getClients === 'function') {
                    console.log('Checking for duplicate names in database...');
                    const list = await window.BUDG_API_SERVICE.getClients();
                    const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                    
                    const primaryNameVal = (primaryNameInput.value || '').trim().toLowerCase();
                    
                    console.log('Checking against', rows.length, 'existing clients');
                    
                    // Get current entity data to compare
                    const currentEntity = await window.BUDG_API_SERVICE.getClientById(entityId);
                    const currentData = currentEntity.data || currentEntity;
                    const currentPrimaryName = (currentData.primary_name || currentData.PrimaryName || currentData.primaryname || currentData.name || '').trim().toLowerCase();
                    
                    // Check for duplicate Primary Name only if it has changed
                    if (primaryNameVal !== currentPrimaryName) {
                        const primaryNameClash = rows.some(r => {
                            const existingId = String(r.ID || r.id || '');
                            if (existingId === String(entityId)) return false; // Skip current entity
                            const existingPrimaryName = String(r.primary_name || r.PrimaryName || r.primaryname || r.name || '').trim().toLowerCase();
                            return existingPrimaryName === primaryNameVal && existingPrimaryName !== '';
                        });
                        if (primaryNameClash) {
                            alert(window.I18n ? window.I18n.t('client.validation.primaryNameExists') : 'Error: Primary Name already exists in database.\nThe Primary Name must be unique to avoid duplicates.');
                            primaryNameInput.focus();
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
                primary_name: primaryNameInput.value.trim(),
                long_name: longNameInput.value.trim() || null,
                description: descriptionInput.value.trim(),
                status: parseInt(statusSelect.value) || 1,
                lifecycle: parseInt(lifecycleSelect.value) || 1,
                is_public: parseInt(viewingSelect.value) || 1,
                parent_id: parentClientSelect?.value || null,
                // Backend expects segmentId (camelCase). Keep snake_case for safety.
                segmentId: segmentField ? segmentField.getValue() : null,
                segment_id: segmentField ? segmentField.getValue() : null
            };

            // Show loading state on buttons
            const buttons = [saveBtn, saveAndCloseBtn, cancelBtn];
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = true;
                    btn.textContent = 'Saving...';
                }
            });

            // Call API to update client
            console.log('Sending update request with data:', formData);
            const response = await window.BUDG_API_SERVICE.updateClient(entityId, formData);
            console.log('Update response:', response);

            // Handle API response
            console.log('Checking response:', {
                response: response,
                hasSuccess: response && response.success,
                hasId: response && response.id,
                hasMessage: response && response.message,
                responseType: typeof response
            });

            // Treat {success:false} as failure even if message exists
            const ok = !!response && response.success !== false;
            if (ok) {
                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
                
                // Release lock after successful save
                await window.LockInitHelper.releaseLock();
                
                alert('Client updated successfully!');

                if (closeAfterSave) {
                    window.location.href = `/view/client/${entityId}`;
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
                
                alert(window.I18n ? window.I18n.t('client.messages.failedToUpdate') : 'Failed to update client. Please check the console for details.');
            }

        } catch (error) {
            console.error('Error saving client:', error);
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
            if (errorMessage.toLowerCase().includes('primary_name') || errorMessage.toLowerCase().includes('primary name')) {
                alert('Error: Primary Name already exists in database.\nThe Primary Name must be unique to avoid duplicates.');
                primaryNameInput?.focus();
            } else if (errorMessage.toLowerCase().includes('no changes detected')) {
                alert(window.I18n ? window.I18n.t('client.messages.noChangesDetected') : 'No changes detected. The data is identical to the existing record.');
            } else {
                alert(window.I18n ? window.I18n.t('client.messages.errorUpdating', { error: errorMessage }) : 'Error updating client: ' + errorMessage + '\n\nPlease check the console for more details.');
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
        const primaryNameInput = document.getElementById('primaryNameInput');
        const longNameInput = document.getElementById('longNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const parentClientSelect = document.getElementById('parentClientSelect');
        
        return (primaryNameInput && primaryNameInput.value.trim()) ||
            (longNameInput && longNameInput.value.trim()) ||
            (descriptionInput && descriptionInput.value.trim()) ||
            (statusSelect && statusSelect.value) ||
            (lifecycleSelect && lifecycleSelect.value) ||
            (viewingSelect && viewingSelect.value) ||
            (parentClientSelect && parentClientSelect.value);
    }

    // Handle form cancellation
    async function cancelForm() {
        // Check if form has unsaved changes
        if (hasAnyDataChanged()) {
            const confirmMessage = window.I18n ? window.I18n.t('client.confirm.cancelWithChanges') : 'You have unsaved changes. Are you sure you want to cancel without saving?';
            const confirmClose = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmClose) {
                await window.LockInitHelper.releaseLock();
                window.location.href = `/view/client/${entityId}`;
            }
        } else {
            await window.LockInitHelper.releaseLock();
            window.location.href = `/view/client/${entityId}`;
        }
    }


    // Initialize the page
    async function initPage() {
        try {
            console.log('Initializing client edit page...');
            
            // Get references to all DOM elements
            console.log('Looking for form elements...');
            const saveBtn = document.getElementById('saveBtn');
            const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
            const cancelBtn = document.getElementById('cancelBtn');
            const clientForm = document.getElementById('clientForm');
            const primaryNameInput = document.getElementById('primaryNameInput');
            const longNameInput = document.getElementById('longNameInput');
            const descriptionInput = document.getElementById('descriptionInput');
            const statusSelect = document.getElementById('statusSelect');
            const lifecycleSelect = document.getElementById('lifecycleSelect');
            const viewingSelect = document.getElementById('viewingSelect');
            const pageTitle = document.getElementById('pageTitle');
            
            // Debug: Log all found elements
            console.log('Form elements found:', {
                saveBtn: !!saveBtn,
                saveAndCloseBtn: !!saveAndCloseBtn,
                cancelBtn: !!cancelBtn,
                clientForm: !!clientForm,
                primaryNameInput: !!primaryNameInput,
                longNameInput: !!longNameInput,
                descriptionInput: !!descriptionInput,
                statusSelect: !!statusSelect,
                lifecycleSelect: !!lifecycleSelect,
                viewingSelect: !!viewingSelect,
                pageTitle: !!pageTitle
            });
            
            // Debug: Check if form exists
            if (!clientForm) {
                console.error('Form element not found! Available elements:', document.querySelectorAll('form'));
                throw new Error('Form element not found. Page structure may be incorrect.');
            }
            
            // Validate that all required elements exist
            if (!primaryNameInput || !statusSelect || !lifecycleSelect || !viewingSelect) {
                console.error('Required form elements not found:', {
                    primaryNameInput: !!primaryNameInput,
                    statusSelect: !!statusSelect,
                    lifecycleSelect: !!lifecycleSelect,
                    viewingSelect: !!viewingSelect
                });
                
                // Try to find elements by different selectors
                console.log('Trying alternative selectors...');
                const altPrimaryNameInput = document.querySelector('input[id="primaryNameInput"]');
                const altStatusSelect = document.querySelector('select[id="statusSelect"]');
                const altLifecycleSelect = document.querySelector('select[id="lifecycleSelect"]');
                const altViewingSelect = document.querySelector('select[id="viewingSelect"]');
                
                console.log('Alternative selectors found:', {
                    altPrimaryNameInput: !!altPrimaryNameInput,
                    altStatusSelect: !!altStatusSelect,
                    altLifecycleSelect: !!altLifecycleSelect,
                    altViewingSelect: !!altViewingSelect
                });
                
                throw new Error('Required form elements not found. DOM may not be fully loaded.');
            }

            // Check if API service is available
            if (!window.BUDG_API_SERVICE) {
                console.error('BUDG_API_SERVICE not found. Make sure api-service.js is loaded.');
                alert(window.I18n ? window.I18n.t('client.messages.apiNotAvailable') : 'API service not available. Please refresh the page.');
                return;
            }
            
            console.log('Form elements validated successfully');

            await initializeSegmentField();
            
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

            // Wire up editor button – advanced rich text editor
            const editorButton = document.querySelector('.editor-button');
            if (editorButton) {
                editorButton.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    toggleAdvancedRichTextEditor('descriptionInput', editorButton);
                });
            }

            if (clientForm) {
                clientForm.addEventListener('submit', function(e) {
                    e.preventDefault();
                    saveBasedOnActiveTab(false);
                });
            }
            
            // Load statuses, lifecycles and viewing options for dropdowns
            console.log('Loading dropdown options...');
            await Promise.all([
                loadStatuses(),
                loadLifecycles(),
                loadViewingOptions(),
                loadParentClients()
            ]);
            
            console.log('Dropdown options loaded, now loading entity data...');
            
            // Add small delay to ensure dropdown options are fully rendered
            await new Promise(resolve => setTimeout(resolve, 100));
            
            // Load existing entity data
            await loadEntityData();
            
            // Initialize custom fields
            if (window.CustomFields) {
                try {
                    window.customFieldsContext = await window.CustomFields.initForm({
                        facetId: 'Client',
                        containerId: 'customFieldsContainer',
                        mode: 'edit',
                        objectId: parseInt(entityId, 10)
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

            statusSelect.innerHTML = '<option value="">Loading statuses...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getStatusList !== "function") {
                statusSelect.innerHTML = '<option value="">Status not available</option>';
                return;
            }

            const response = await window.BUDG_API_SERVICE.getStatusList();

            if (response && Array.isArray(response)) {
                statusSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = 'Select Status';
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
                defaultOption.textContent = 'Select Status';
                statusSelect.appendChild(defaultOption);

                response.data.forEach(status => {
                    const option = document.createElement('option');
                    option.value = status.id;
                    option.textContent = status.name || status.primaryname || status.title;
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

    // Load lifecycles from API for dropdown
    async function loadLifecycles() {
        try {
            const lifecycleSelect = document.getElementById('lifecycleSelect');
            if (!lifecycleSelect) return;

            lifecycleSelect.innerHTML = '<option value="">Loading lifecycles...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getClientLifecycleList !== "function") {
                lifecycleSelect.innerHTML = '<option value="">Lifecycle not available</option>';
                return;
            }

            const response = await window.BUDG_API_SERVICE.getClientLifecycleList();

            if (response && Array.isArray(response)) {
                lifecycleSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = 'Select Lifecycle';
                lifecycleSelect.appendChild(defaultOption);

                response.forEach(lifecycle => {
                    const option = document.createElement('option');
                    option.value = lifecycle.id;
                    option.textContent = lifecycle.name || lifecycle.primaryname || lifecycle.title;
                    lifecycleSelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data)) {
                lifecycleSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = 'Select Lifecycle';
                lifecycleSelect.appendChild(defaultOption);

                response.data.forEach(lifecycle => {
                    const option = document.createElement('option');
                    option.value = lifecycle.id;
                    option.textContent = lifecycle.name || lifecycle.primaryname || lifecycle.title;
                    lifecycleSelect.appendChild(option);
                });
            } else {
                lifecycleSelect.innerHTML = '<option value="">Lifecycle not available</option>';
            }
        } catch (error) {
            console.error('Error loading lifecycles:', error);
            lifecycleSelect.innerHTML = '<option value="">Error loading lifecycles</option>';
        }
    }

    // Load viewing options from API for dropdown
    async function loadViewingOptions() {
        try {
            const viewingSelect = document.getElementById('viewingSelect');
            if (!viewingSelect) return;

            viewingSelect.innerHTML = '<option value="">Loading viewing options...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getViewingList !== "function") {
                viewingSelect.innerHTML = '<option value="">Viewing not available</option>';
                return;
            }

            const response = await window.BUDG_API_SERVICE.getViewingList();

            if (response && Array.isArray(response)) {
                viewingSelect.innerHTML = '';
                const defaultOption = document.createElement('option');
                defaultOption.value = '';
                defaultOption.textContent = 'Select Viewing';
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
                defaultOption.textContent = 'Select Viewing';
                viewingSelect.appendChild(defaultOption);

                response.data.forEach(viewing => {
                    const option = document.createElement('option');
                    option.value = viewing.id;
                    option.textContent = viewing.name || viewing.primaryname || viewing.title;
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

    // Set default lifecycle (try to find "Active" or use first available)
    function setDefaultLifecycle() {
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        console.log('SetDefaultLifecycle - LifecycleSelect found:', !!lifecycleSelect);
        console.log('SetDefaultLifecycle - Options length:', lifecycleSelect?.options.length);
        if (lifecycleSelect && lifecycleSelect.options.length > 1) {
            let foundActive = false;
            console.log('SetDefaultLifecycle - Searching for active option...');
            for (let i = 1; i < lifecycleSelect.options.length; i++) {
                const optionText = lifecycleSelect.options[i].textContent.toLowerCase();
                console.log('SetDefaultLifecycle - Checking option:', {index: i, text: optionText, value: lifecycleSelect.options[i].value});
                if (optionText.includes('active') || optionText.includes('نشط')) {
                    lifecycleSelect.value = lifecycleSelect.options[i].value;
                    console.log('SetDefaultLifecycle - Found active option, set to:', lifecycleSelect.options[i].value);
                    foundActive = true;
                    break;
                }
            }
            if (!foundActive) {
                const defaultValue = lifecycleSelect.options[1].value;
                lifecycleSelect.value = defaultValue;
                console.log('SetDefaultLifecycle - No active option found, set to first option:', defaultValue);
            }
            console.log('SetDefaultLifecycle - Final value:', lifecycleSelect.value);
        } else {
            console.warn('SetDefaultLifecycle - No options available or lifecycleSelect not found');
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

    // Load parent clients from API for dropdown
    async function loadParentClients() {
        try {
            const parentClientSelect = document.getElementById('parentClientSelect');
            if (!parentClientSelect) return;

            parentClientSelect.innerHTML = '<option value="">Loading parent clients...</option>';

            if (!window.BUDG_API_SERVICE || typeof window.BUDG_API_SERVICE.getClients !== "function") {
                parentClientSelect.innerHTML = '<option value="">Parent clients not available</option>';
                return;
            }

            const response = await window.BUDG_API_SERVICE.getClients();

            if (response && Array.isArray(response) && response.length > 0) {
                parentClientSelect.innerHTML = '';
                
                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'No Parent Client';
                parentClientSelect.appendChild(emptyOption);
                
                response.forEach((client) => {
                    const option = document.createElement('option');
                    option.value = client.id;
                    option.textContent = client.name || client.primary_name || client.primaryname || client.title;
                    parentClientSelect.appendChild(option);
                });
            } else if (response && response.data && Array.isArray(response.data) && response.data.length > 0) {
                parentClientSelect.innerHTML = '';
                
                // Add empty option for no parent
                const emptyOption = document.createElement('option');
                emptyOption.value = '';
                emptyOption.textContent = 'No Parent Client';
                parentClientSelect.appendChild(emptyOption);
                
                response.data.forEach((client) => {
                    const option = document.createElement('option');
                    option.value = client.id;
                    option.textContent = client.name || client.primary_name || client.primaryname || client.title;
                    parentClientSelect.appendChild(option);
                });
            } else {
                parentClientSelect.innerHTML = '<option value="">No parent clients available</option>';
            }
        } catch (error) {
            console.error('Error loading parent clients:', error);
            parentClientSelect.innerHTML = '<option value="">Error loading parent clients</option>';
        }
    }

    async function refreshClientParentForSelectedSegment(previousSegmentId) {
        const parentClientSelect = document.getElementById('parentClientSelect');
        const previousParentId = parentClientSelect?.value || '';
        await loadParentClients();

        if (!parentClientSelect || !previousParentId) {
            return;
        }

        const stillAllowed = Array.from(parentClientSelect.options || [])
            .some(option => String(option.value) === String(previousParentId));

        if (!stillAllowed) {
            alert('This parent is not valid for the selected segment. Please remove the parent first.');
            return false;
        } else {
            parentClientSelect.value = previousParentId;
        }
        return true;
    }

    // Tab system functions
    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        const containers = document.querySelectorAll('[id$="Container"]');
        
        console.log('Initializing tabs...');
        console.log('Found tabs:', tabs.length);
        console.log('Found containers:', containers.length);
        
        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const tabName = tab.getAttribute('data-tab');
                console.log('Tab clicked:', tabName);
                
                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                // Add active class to clicked tab
                tab.classList.add('active');
                
                // Hide all containers EXCEPT those nested inside clientViewContainer
                const nestedContainers = ['segmentFieldContainer', 'customFieldsContainer'];
                containers.forEach(container => {
                    if (!nestedContainers.includes(container.id)) {
                        container.style.display = 'none';
                    }
                });
                
                // Show the corresponding container
                const targetContainerId = `client${tabName.charAt(0).toUpperCase() + tabName.slice(1)}Container`;
                const targetContainer = document.getElementById(targetContainerId);
                
                console.log('Looking for container:', targetContainerId);
                console.log('Container found:', !!targetContainer);
                
                if (targetContainer) {
                    // Set display based on container type
                    // Stakeholders container should be block, others should be grid
                    if (tabName === 'stakeholders') {
                        targetContainer.style.display = 'block';
                        console.log('Container display set to block (stakeholders)');
                    } else {
                        targetContainer.style.display = 'grid';
                        console.log('Container display set to grid');
                    }
                } else {
                    console.error('Target container not found:', targetContainerId);
                }
                
                // Load tab-specific content for editing
                loadTabContentForEdit(tabName);
            });
        });
        
        console.log('Tabs initialized successfully');
    }

    function loadTabContentForEdit(tabName) {
        console.log('Loading tab content for edit:', tabName);
        console.log('Entity ID:', entityId);
        console.log('ClientStakeholderEdit available:', !!window.ClientStakeholderEdit);
        
        switch(tabName) {
            case 'stakeholders':
                // Load stakeholders in edit mode
                try {
                    if (window.ClientStakeholderEdit) {
                        const clientIdNumber = parseInt(entityId, 10);
                        if (isNaN(clientIdNumber)) {
                            console.error('Invalid client ID:', entityId);
                            const container = document.getElementById('clientStakeholdersContainer');
                            if (container) {
                                container.innerHTML = '<div class="error" style="padding: 2rem; text-align: center; color: var(--danger, #dc3545);">Invalid client ID. Please refresh the page and try again.</div>';
                            }
                            return;
                        }
                        console.log('Initializing ClientStakeholderEdit with ID:', clientIdNumber);
                        window.ClientStakeholderEdit.init(clientIdNumber);
                    } else {
                        console.error('ClientStakeholderEdit not available');
                        const container = document.getElementById('clientStakeholdersContainer');
                        if (container) {
                            container.innerHTML = '<div class="error" style="padding: 2rem; text-align: center; color: var(--danger, #dc3545);">Stakeholder edit functionality not loaded. Please refresh the page and try again.</div>';
                        }
                    }
                } catch (error) {
                    console.error('Error initializing stakeholder edit:', error);
                    const container = document.getElementById('clientStakeholdersContainer');
                    if (container) {
                        container.innerHTML = '<div class="error" style="padding: 2rem; text-align: center; color: var(--danger, #dc3545);">Error loading stakeholders: ' + (error.message || 'Unknown error') + '</div>';
                    }
                }
                break;
            case 'view':
                // Main client details (Summary) - already loaded
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
        const activeTab = getCurrentActiveTab();
        
        switch(activeTab) {
            case 'stakeholders':
                // Check if stakeholders data has changed
                console.log('Checking stakeholders data changes...');
                return window.ClientStakeholderEdit && window.ClientStakeholderEdit.hasDataChanged ? 
                       window.ClientStakeholderEdit.hasDataChanged() : false;
            case 'view':
            case 'summary':
                // Check if client summary/main data has changed
                console.log('Checking client summary/main data changes...');
                return hasDataChanged();
            default:
                // Default to checking client data for unknown tabs
                console.warn(`Unknown tab: ${activeTab}, defaulting to client data change check`);
                return hasDataChanged();
        }
    }

    // Save data based on active tab
    async function saveBasedOnActiveTab(closeAfterSave = false) {
        const activeTab = getCurrentActiveTab();
        console.log(`=== SAVING CLIENT (active tab: ${activeTab}) ===`);

        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn'), document.getElementById('cancelBtn')];
        const restoreButtons = () => {
            buttons.forEach(btn => {
                if (btn) {
                    btn.disabled = false;
                    if (btn.id === 'saveBtn') btn.textContent = (window.I18n ? window.I18n.t('button.save') : 'Save');
                    if (btn.id === 'saveAndCloseBtn') btn.textContent = 'Save & Close';
                }
            });
        };
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = true;
                if (btn.id === 'saveBtn') btn.textContent = 'Saving...';
                if (btn.id === 'saveAndCloseBtn') btn.textContent = 'Saving...';
            }
        });

        try {
            if (activeTab === 'stakeholders') {
                if (window.ClientStakeholderEdit && window.ClientStakeholderEdit.saveStakeholders) {
                    const stakeholdersHasChanges = window.ClientStakeholderEdit.hasDataChanged &&
                        window.ClientStakeholderEdit.hasDataChanged();
                    if (!stakeholdersHasChanges) {
                        alert(window.I18n ? window.I18n.t('client.messages.noChangesToSave') : 'No changes to save.');
                        restoreButtons();
                        return true;
                    }
                    await window.ClientStakeholderEdit.saveStakeholders();
                    console.log('✅ Stakeholders saved successfully');
                } else {
                    alert(window.I18n ? window.I18n.t('client.messages.noChangesToSave') : 'No changes to save.');
                    restoreButtons();
                    return true;
                }

            } else {
                // view (summary) tab
                const saveSuccess = await saveClientData();
                if (!saveSuccess) {
                    restoreButtons();
                    return false;
                }

                // Save custom fields if context exists
                if (window.customFieldsContext && window.customFieldsContext.saveValues && entityId) {
                    try {
                        await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                        console.log('✅ Custom fields saved successfully');
                    } catch (error) {
                        console.error('Error saving custom fields:', error);
                    }
                }
            }

            if (closeAfterSave) {
                setTimeout(() => { window.location.href = `/view/client/client.html?id=${entityId}`; }, 1500);
            }

            return true;
        } catch (error) {
            console.error('Error saving:', error);
            alert(window.I18n ? window.I18n.t('client.messages.errorUpdating', { error: error.message || 'Unknown error' }) : 'Error saving: ' + (error.message || 'Unknown error'));
            return false;
        } finally {
            restoreButtons();
        }
    }

    // Save client main data (extracted from saveClient function)
    async function saveClientData() {
        // Check if data has changed
        if (!hasDataChanged()) {
            alert(window.I18n ? window.I18n.t('client.messages.noChangesToSave') : 'No changes to save. No data has been modified.');
            if (window.customFieldsContext && window.customFieldsContext.saveValues && entityId) {
                try {
                    await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                    console.log('✅ Custom fields saved (no other changes)');
                } catch (cfError) {
                    console.error('Error saving custom fields:', cfError);
                }
            }
            return true; // Return true since no error occurred
        }
        
        console.log('Data has changed, proceeding with save...');
        
        // Get form elements
        const primaryNameInput = document.getElementById('primaryNameInput');
        const longNameInput = document.getElementById('longNameInput');
        const descriptionInput = document.getElementById('descriptionInput');
        const statusSelect = document.getElementById('statusSelect');
        const lifecycleSelect = document.getElementById('lifecycleSelect');
        const viewingSelect = document.getElementById('viewingSelect');
        const parentClientSelect = document.getElementById('parentClientSelect');
        
        // Validate required fields
        if (!primaryNameInput || !primaryNameInput.value.trim()) {
            alert('Primary Name is required');
            primaryNameInput?.focus();
            return false;
        }

        if (!descriptionInput || !descriptionInput.value.trim()) {
            alert('Description is required');
            descriptionInput?.focus();
            return false;
        }

        if (!statusSelect || !statusSelect.value) {
            alert('BUDG Status is required');
            statusSelect?.focus();
            return false;
        }

        if (!lifecycleSelect || !lifecycleSelect.value) {
            alert('Lifecycle is required');
            lifecycleSelect?.focus();
            return false;
        }

        if (!viewingSelect || !viewingSelect.value) {
            alert('BUDG Viewing is required');
            viewingSelect?.focus();
            return false;
        }
        

        // Client-side uniqueness checks (Primary Name) - only if names have changed
        try {
            if (typeof window.BUDG_API_SERVICE?.getClients === 'function') {
                console.log('Checking for duplicate names in database...');
                const list = await window.BUDG_API_SERVICE.getClients();
                const rows = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];
                
                const primaryNameVal = (primaryNameInput.value || '').trim().toLowerCase();
                
                console.log('Checking against', rows.length, 'existing clients');
                
                // Get current entity data to compare
                const currentEntity = await window.BUDG_API_SERVICE.getClientById(entityId);
                const currentData = currentEntity.data || currentEntity;
                const currentPrimaryName = (currentData.primary_name || currentData.PrimaryName || currentData.primaryname || currentData.name || '').trim().toLowerCase();
                
                // Check for duplicate Primary Name only if it has changed
                if (primaryNameVal !== currentPrimaryName) {
                    const primaryNameClash = rows.some(r => {
                        const existingId = String(r.ID || r.id || '');
                        if (existingId === String(entityId)) return false; // Skip current entity
                        const existingPrimaryName = String(r.primary_name || r.PrimaryName || r.primaryname || r.name || '').trim().toLowerCase();
                        return existingPrimaryName === primaryNameVal && existingPrimaryName !== '';
                    });
                    if (primaryNameClash) {
                        alert('Error: Primary Name already exists in database.\nThe Primary Name must be unique to avoid duplicates.');
                        primaryNameInput.focus();
                        return false;
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
            primary_name: primaryNameInput.value.trim(),
            long_name: longNameInput.value.trim() || null,
            description: descriptionInput.value.trim(),
            status: parseInt(statusSelect.value) || 1,
            lifecycle: parseInt(lifecycleSelect.value) || 1,
            is_public: parseInt(viewingSelect.value) || 1,
            parent_id: parentClientSelect?.value || null,
            // Backend expects segmentId (camelCase). Keep snake_case for safety.
            segmentId: segmentField ? segmentField.getValue() : null,
            segment_id: segmentField ? segmentField.getValue() : null
        };

        // Call API to update client
        console.log('Sending update request with data:', formData);
        const response = await window.BUDG_API_SERVICE.updateClient(entityId, formData);
        console.log('Update response:', response);

        // Handle API response
        // Treat {success:false} as failure even if message exists
        const ok = !!response && response.success !== false;
        if (ok) {
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(parseInt(entityId, 10));
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }
            
            alert('Client updated successfully!');
            
            // Update original data to reflect changes
            originalData = {
                primary_name: formData.primary_name,
                long_name: formData.long_name,
                description: formData.description,
                status: formData.status,
                lifecycle: formData.lifecycle,
                is_public: formData.is_public,
                parent_id: formData.parent_id,
                segment_id: formData.segment_id
            };
            
            
            return true;
        } else {
            console.error('Update failed. Response:', response);
            alert('Failed to update client. Please check the console for details.');
            return false;
        }
    }

    // Initialize tabs after DOM is ready
    setTimeout(() => {
        initTabs();
    }, 100);
});


