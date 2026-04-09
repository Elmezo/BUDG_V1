(function() {
    let segmentField = null; // Segment field component reference
    let editViewMode = 'original'; // 'original' | 'changes' (under active CR, edit should load nobject_id data)
    let originalDatasetSegmentId = null; // segment loaded from server; used to detect segment change

    function normalizeDatasetSegmentId(value) {
        if (value == null || value === '') return null;
        const n = parseInt(value, 10);
        return Number.isInteger(n) ? n : null;
    }

    function parseId() {
        // First try URL params (for separate edit pages)
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(id)) return id;

        // Fallback to path-based ID (for main view pages)
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('dataset');
        if (idx === -1 || parts.length < idx + 2) return null;
        const pathId = parseInt(parts[idx + 1], 10);
        return Number.isNaN(pathId) ? null : pathId;
    }

    const DATASET_EDIT_CUSTOM_FACET = (document.body && document.body.getAttribute('data-custom-facet')) || 'Data Sets';

    function populateSelectSimple(selectId, list, getLabel) {
        const el = document.getElementById(selectId);
        if (!el) return;
        el.innerHTML = '';
        (Array.isArray(list) ? list : []).forEach(item => {
            const op = document.createElement('option');
            op.value = String(item.id);
            op.textContent = getLabel ? getLabel(item) : (item.name || '');
            el.appendChild(op);
        });
    }

    async function loadDatasetEditLookups() {
        const svc = window.BUDG_API_SERVICE;
        const selectedSegmentId = segmentField ? segmentField.getValue() : 1;
        const [systems, glossary, statuses, types, viewing, lifecycle] = await Promise.all([
            svc.getSystemsList({ segmentId: selectedSegmentId }),
            svc.getGlossaryList({ segmentId: selectedSegmentId }),
            svc.getStatusList(),
            svc.getDatasetTypes(),
            svc.getViewingList(),
            svc.getLifecycleList()
        ]);
        populateSelectSimple('dsSystem', systems, x => x.name);
        populateSelectSimple('dsGlossary', glossary, x => x.description ? `${x.name} — ${x.description}` : x.name);
        populateSelectSimple('dsStatus', (Array.isArray(statuses?.data) ? statuses.data : statuses), x => x.name);
        populateSelectSimple('dsType', types, x => x.primaryName || x.name);
        populateSelectSimple('dsViewing', viewing, x => x.name);
        populateSelectSimple('dsLifecycle', lifecycle, x => x.primaryName || x.name);

        // Apply DFCR locked fields for Data Set facet
        // NOTE: This is an EDIT page. Locks will be applied only if workflow_create_id == workflow_edit_id
        // If only workflow_edit_id is set (different from workflow_create_id), locks will NOT be applied
        if (window.DFCRUtils) {
            try {
                // Get dataset ID first to pass to DFCRUtils
                const datasetId = parseId();
                
                await window.DFCRUtils.applyLockedFields('Data Set', {
                    status: '#dsStatus',
                    lifecycle: '#dsLifecycle'
                }, { isEditPage: true, objectId: datasetId });
                console.log('DFCR locked fields check done for Data Set edit page (objectId:', datasetId, ')');
                
                // Check if edit workflow is enabled and show Save & Submit button
                const dfcrInfo = await window.DFCRUtils.getInfo('Data Set', datasetId);
                console.log('DFCR Info for Data Set edit:', dfcrInfo);
                
                if (dfcrInfo.editWorkflowEnabled && !dfcrInfo.adminBypassEnabled) {
                    const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
                    if (saveSubmitBtn) {
                        saveSubmitBtn.style.display = 'inline-block';
                        console.log('Save & Submit button shown for edit workflow');
                    }
                }
            } catch (dfcrError) {
                console.warn('Error applying DFCR locked fields:', dfcrError);
            }
        }

        // Note: Edit page uses simple select dropdowns, not searchable ones
        // The searchable dropdown functionality is only for the creation page
    }

    async function determineEditViewMode(datasetId) {
        // For EDIT pages: If an active CR exists, load the pending changes (nobject_id data)
        // This ensures the edit form shows the latest pending changes, not the original data
        try {
            const res = await fetch(`/api/pending-changes/status/Dataset/${datasetId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (res.ok) {
                const status = await res.json();
                if (status?.underRevision) {
                    console.log('[Dataset Edit] Object is under revision, loading changes view');
                    return 'changes';
                }
            }
        } catch (e) {
            console.warn('[Dataset Edit] Failed to determine pending-changes status:', e);
        }
        return 'original';
    }

    function updateTitle(dataset) {
        const titleElement = document.getElementById('datasetTitle');
        if (titleElement && dataset) {
            const name = dataset.name || dataset.Name || 'Dataset';
            titleElement.textContent = name;
            console.log('Title updated to:', titleElement.textContent);
        }
    }

    async function loadDataset(id, viewMode = 'original') {
        try {
            const d = await window.BUDG_API_SERVICE.getDatasetById(id, viewMode === 'changes' ? 'changes' : null);
            // Update page title with dataset name
            updateTitle(d);
            const setVal = (i, v) => { const el = document.getElementById(i); if (el) el.value = v ?? ''; };
            const setSel = (i, v) => { const el = document.getElementById(i); if (el && v!=null) el.value = String(v); };

            setVal('dsName', d.name);
            setSel('dsSystem', d.systemId);
            setVal('dsRef', d.ref || '');
            setVal('dsDefinition', d.definition || '');
            setSel('dsGlossary', d.glossaryId);
            updateGlossaryDisplay();
            setVal('glossaryHint', '');
            setVal('dsUsage', d.usage || '');
            setSel('dsStatus', d.statusId);
            setSel('dsType', d.typeId);
            setSel('dsViewing', d.viewingId);
            setSel('dsLifecycle', d.lifecycleId);
            
            // Set segment value if available
            const loadedSegmentId = d.segmentId ?? d.segment_id ?? d.Segment_ID;
            if (segmentField && loadedSegmentId != null && loadedSegmentId !== '') {
                segmentField.setValue(loadedSegmentId);
            }
            if (segmentField && typeof segmentField.getValue === 'function') {
                originalDatasetSegmentId = normalizeDatasetSegmentId(segmentField.getValue());
            } else {
                originalDatasetSegmentId = normalizeDatasetSegmentId(loadedSegmentId);
            }
            
            // ⚠️ CRITICAL: Re-apply DFCR locks after populating form
            // This ensures locks are applied based on current Auto CR state from backend
            if (window.reapplyDFCRLocks) {
                setTimeout(async () => {
                    await window.reapplyDFCRLocks('Data Set', id);
                }, 50);
            }

            // After segment is set, refresh systems list to enforce:
            // Enterprise systems OR same segment as dataset segment
            try {
                const currentSystemId = d.systemId;
                const systems = await window.BUDG_API_SERVICE.getSystemsList({ segmentId: segmentField ? segmentField.getValue() : 1 });
                populateSelectSimple('dsSystem', systems, x => x.name);
                if (currentSystemId != null) {
                    const el = document.getElementById('dsSystem');
                    if (el) el.value = String(currentSystemId);
                }
            } catch (e) {
                console.warn('Failed to reload systems for segment constraint', e);
            }

            // Refresh glossaries list to enforce:
            // Enterprise glossaries OR same segment as dataset segment
            try {
                const currentGlossaryId = d.glossaryId;
                const glossaries = await window.BUDG_API_SERVICE.getGlossaryList({ segmentId: segmentField ? segmentField.getValue() : 1 });
                populateSelectSimple('dsGlossary', glossaries, x => x.description ? `${x.name} — ${x.description}` : x.name);
                if (currentGlossaryId != null) {
                    const el = document.getElementById('dsGlossary');
                    if (el) el.value = String(currentGlossaryId);
                    updateGlossaryDisplay();
                }
            } catch (e) {
                console.warn('Failed to reload glossaries for segment constraint', e);
            }

        } catch(err) {
            console.error('Failed to load dataset:', err);
            alert('Failed to load dataset data');
        }
    }

    async function saveDataset(id, closeAfter = false) {
        const name = document.getElementById('dsName')?.value.trim();
        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('dsDefinition');
            syncAdvancedRichTextToTextarea('dsUsage');
        }

        const masterSource = parseInt(document.getElementById('dsSystem')?.value || '', 10);
        const refNumber = document.getElementById('dsRef')?.value.trim() || null;
        const definition = document.getElementById('dsDefinition')?.value.trim();
        const glossary = parseInt(document.getElementById('dsGlossary')?.value || '', 10);
        const usage = document.getElementById('dsUsage')?.value.trim() || null;
        const status = parseInt(document.getElementById('dsStatus')?.value || '', 10);
        const accessControlType = parseInt(document.getElementById('dsViewing')?.value || '', 10);
        const datasetType = parseInt(document.getElementById('dsType')?.value || '', 10);
        const lifecycle = parseInt(document.getElementById('dsLifecycle')?.value || '', 10);
        const segmentId = segmentField ? segmentField.getValue() : null;

        const required = [
            { ok: !!name, field: 'Name' },
            { ok: Number.isInteger(masterSource), field: 'System Short Name' },
            { ok: !!definition, field: 'Definition' },
            { ok: Number.isInteger(glossary), field: 'Glossary Name' },
            { ok: Number.isInteger(status), field: 'BUDG Status' },
            { ok: Number.isInteger(datasetType), field: 'Type' },
            { ok: Number.isInteger(accessControlType), field: 'BUDG Viewing' },
            { ok: Number.isInteger(lifecycle), field: 'Lifecycle' }
        ];
        const missing = required.filter(r => !r.ok).map(r => r.field);
        
        // Validate segment
        if (segmentField && !segmentField.validate()) {
            missing.push('Segment');
        }

        if (missing.length) {
            alert('Please fill required fields: ' + missing.join(', '));
            return false;
        }

        const payload = {
            primaryName: name,
            masterSource,
            refNumber,
            definition,
            glossary,
            usage,
            status,
            datasetType,
            accessControlType,
            lifecycle,
            segmentId
        };

        const buttons = [document.getElementById('editSaveBtn'), document.getElementById('editSaveCloseBtn')];

        async function saveCustomFieldsForDataset() {
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                    return { success: true };
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                    return { success: false, message: error?.message || 'Failed to save custom fields' };
                }
            }
            return { success: true };
        }

        try {
            buttons.forEach(b=>{
                if (b){
                    b.disabled = true;
                    b.dataset._txt = b.textContent;
                    b.textContent = 'Saving...';
                }
            });

            // Validate and save relationships BEFORE dataset save
            // If relationships validation fails, stop the save process and show error to user
            let relationshipsSaved = false;
            if (window.saveDatasetRelationships) {
                try {
                    console.log('[Dataset Edit] Validating and saving relationships before dataset save...');
                    const relationshipsResult = await window.saveDatasetRelationships();
                    if (relationshipsResult && relationshipsResult.noChanges) {
                        console.log('[Dataset Edit] No relationship changes to save');
                    } else {
                        console.log('[Dataset Edit] ✅ Relationships saved successfully');
                        relationshipsSaved = true;
                    }
                } catch (relationshipsError) {
                    console.error('[Dataset Edit] Error saving relationships:', relationshipsError);
                    // Show persistent error message to user (won't auto-close)
                    const errorMessage = relationshipsError.message || 'Failed to save relationships. Please fix the errors and try again.';
                    showSuccessMessage(errorMessage, true, true); // persistent = true
                    // Stop the save process - don't continue to dataset save
                    buttons.forEach(b => {
                        if (b) {
                            b.disabled = false;
                            if (b.dataset._txt) b.textContent = b.dataset._txt;
                        }
                    });
                    return false; // Exit early - don't save dataset if relationships failed, don't close page
                }
            }
            
            const res = await window.BUDG_API_SERVICE.updateDataset(id, payload);
            if (res && res.success === false) {
                // Check if it's a lock error
                if (res.locked) {
                    const lockedBy = res.lockedBy || 'another user';
                    const isPermanent = res.isPermanent || false;
                    const message = `This dataset is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`;
                    showSuccessMessage(message, true);
                    // Release our lock attempt
                    await releaseLock(id);
                    setTimeout(() => {
                        window.location.href = `/view/dataset/${id}`;
                    }, 3000);
                    return false;
                }

                // Allow CF-only save path when dataset itself has no updates
                const errorMessage = (res.message || '').toLowerCase();
                const noDatasetChanges = errorMessage.includes('no update') || errorMessage.includes('no change');
                if (noDatasetChanges) {
                    const customFieldsSaved = await saveCustomFieldsForDataset();
                    if (customFieldsSaved.success) {
                        buttons.forEach(b => {
                            if (b) { b.disabled = false; if (b.dataset._txt) b.textContent = b.dataset._txt; }
                        });
                        showSuccessMessage('UPDATES SAVED');
                        if (closeAfter) {
                            setTimeout(() => {
                                window.location.href = `/view/dataset/${id}`;
                            }, 1200);
                        }
                        return true;
                    }
                }

                // If relationships were saved but dataset save failed, inform user
                const serverErrorMessage = res.message || 'Update failed';
                if (relationshipsSaved) {
                    showSuccessMessage(`Relationships were saved, but dataset save failed: ${serverErrorMessage}. Please fix the dataset errors and try again.`, true, true); // persistent
                } else {
                    showSuccessMessage(serverErrorMessage, true, true); // persistent
                }
                buttons.forEach(b => {
                    if (b) {
                        b.disabled = false;
                        if (b.dataset._txt) b.textContent = b.dataset._txt;
                    }
                });
                return false; // Don't throw - keep page open and signal failure
            }
            
            // Check if a CR was auto-created (pending changes mode)
            if (res && (res.pendingChanges === true || res.changeRequestId)) {
                const pendingCustomFieldsResult = await saveCustomFieldsForDataset();
                if (!pendingCustomFieldsResult.success) {
                    showSuccessMessage(`Changes were saved, but custom fields failed: ${pendingCustomFieldsResult.message}`, true, true);
                }
                console.log('[Dataset Edit] CR was auto-created, updating view mode to changes');
                // Update view mode without full reload to avoid delay
                editViewMode = 'changes';
                
                // Only reload the active tab, not the entire page
                const activeTab = document.querySelector('.tab.active');
                const currentTab = activeTab ? activeTab.getAttribute('data-tab') : 'details';
                
                // Reload only the active tab with changes view (async, don't wait)
                Promise.all([
                    currentTab === 'stakeholders' && window.DatasetStakeholderEdit ? 
                        window.DatasetStakeholderEdit.init(id, 'changes') : Promise.resolve(),
                    currentTab === 'impact' && window.initImpactEdit ? 
                        window.initImpactEdit(id, 'changes') : Promise.resolve(),
                    currentTab === 'attribute' && window._attributeTableInstance ? 
                        (async () => {
                            window._attributeTableInstance.setView('changes');
                            await window._attributeTableInstance.load();
                            window._attributeTableInstance.render();
                        })() : Promise.resolve(),
                    currentTab === 'values' && window._valuesEditInstance ? 
                        (async () => {
                            window._valuesEditInstance.setView('changes');
                            await window._valuesEditInstance.loadMetadata();
                        })() : Promise.resolve(),
                    currentTab === 'relationships' && window.initDatasetRelationshipEdit ? 
                        (async () => {
                            window._relationshipsTableInitialized = false;
                            window.initDatasetRelationshipEdit(id, 'changes');
                            window._relationshipsTableInitialized = true;
                        })() : Promise.resolve()
                ]).catch(err => {
                    console.warn('[Dataset Edit] Error reloading tab with changes view:', err);
                });
                
                showSuccessMessage('Changes saved as pending. They will apply when the Change Request is completed.');
                buttons.forEach(b => {
                    if (b) {
                        b.disabled = false;
                        b.textContent = b.dataset._txt || 'Save';
                    }
                });
                
                // If closeAfter is true, close the page after showing success message
                if (closeAfter) {
                    setTimeout(() => {
                        window.location.href = `/view/dataset/${id}`;
                    }, 1000); // Reduced delay from 1500ms to 1000ms
                }
                return res || true;
            }
            
            // Save custom fields if context exists
            const customFieldsResult = await saveCustomFieldsForDataset();

            // Relationships are already saved before dataset save (see above)
            // This block is kept for backward compatibility but should not be needed
            
            const saveWarnings = [];
            if (!customFieldsResult.success) {
                saveWarnings.push(`Custom Fields: ${customFieldsResult.message}`);
            }

            // Save stakeholders data (if available and has changes)
            if (window.DatasetStakeholderEdit && window.DatasetStakeholderEdit.saveStakeholders) {
                try {
                    const stakeholdersHasChanges = window.DatasetStakeholderEdit.hasDataChanged && 
                        window.DatasetStakeholderEdit.hasDataChanged();
                    if (stakeholdersHasChanges) {
                        await window.DatasetStakeholderEdit.saveStakeholders();
                        console.log('✅ Stakeholders saved successfully');
                    }
                } catch (stakeholdersError) {
                    console.error('Error saving stakeholders:', stakeholdersError);
                    saveWarnings.push(`Stakeholders: ${stakeholdersError?.message || 'Failed to save stakeholders'}`);
                }
            }
            
            // Save impact when impact rows changed, or when segment changed (re-validate existing links).
            if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                try {
                    const datasetImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
                    const currentDatasetSegment = normalizeDatasetSegmentId(segmentId);
                    const datasetSegmentChanged = currentDatasetSegment !== normalizeDatasetSegmentId(originalDatasetSegmentId);
                    const shouldSaveDatasetImpact = datasetImpactDirty || datasetSegmentChanged;
                    if (shouldSaveDatasetImpact) {
                        if (datasetSegmentChanged && !datasetImpactDirty && window.initImpactEdit) {
                            console.log('=== Segment changed; reloading dataset impact from server before save ===');
                            await window.initImpactEdit(id, editViewMode);
                        }
                        const impactResult = await window.saveAllImpactData(id);
                        if (impactResult && impactResult.success !== false) {
                            if (window.initImpactEdit && id) {
                                await window.initImpactEdit(id);
                            }
                            console.log('✅ Impact saved successfully');
                        }
                    }
                } catch (impactError) {
                    console.error('Error saving impact:', impactError);
                    saveWarnings.push(`Impact: ${impactError?.message || 'Failed to save impact data'}`);
                }
            }

            // Release lock after successful save
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }
            
            originalDatasetSegmentId = normalizeDatasetSegmentId(segmentId);
            if (saveWarnings.length > 0) {
                showSuccessMessage(`Dataset details saved, but some sections failed: ${saveWarnings.join(' | ')}`, true, true);
            } else {
                showSuccessMessage('Success Updates');
            }
            
            if (closeAfter) {
                setTimeout(() => {
                    window.location.href = `/view/dataset/${id}`;
                }, 1500);
            }
            return res || true;
        } catch (err) {
            // Extract error message from various possible error formats
            let errorMsg = 'Failed to save dataset';
            if (err?.body?.error) {
                errorMsg = err.body.error;
            } else if (err?.body?.message) {
                errorMsg = err.body.message;
            } else if (err?.message) {
                errorMsg = err.message;
            } else if (typeof err === 'string') {
                errorMsg = err;
            }
            
            // If error message contains "segment assignment", make it more user-friendly (i18n-aware)
            if (errorMsg.includes('segment assignment') || errorMsg.includes('has no segment')) {
                const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;
                const systemMatch = errorMsg.match(/System\s+['"]([^'"]+)['"]\s+\(ID:\s*(\d+)\)/);
                if (systemMatch) {
                    const systemName = systemMatch[1];
                    const systemId = systemMatch[2];
                    const safeName = typeof escapeHtml === 'function' ? escapeHtml(systemName) : systemName;
                    const editUrl = `/view/system/system-edit.html?id=${encodeURIComponent(systemId)}`;
                    const msgText = t('dataset.errors.systemNoSegment', { systemName: safeName });
                    const linkText = t('dataset.errors.editSystem');
                    errorMsg = `${msgText} <a href="${editUrl}" style="color:white;text-decoration:underline;font-weight:bold;">${linkText}</a>`;
                } else {
                    const glossaryMatch = errorMsg.match(/Glossary\s+['"]([^'"]+)['"]/);
                    const glossaryName = glossaryMatch ? glossaryMatch[1] : 'the selected glossary';
                    errorMsg = t('dataset.errors.glossaryNoSegment', { glossaryName });
                }
            }
            
            showSuccessMessage(errorMsg, true, true); // persistent error message (supports HTML for link)
            // Don't close page on error - keep it open so user can fix issues
            return false;
        } finally {
            buttons.forEach(b=>{
                if (b){
                    b.disabled = false;
                    if (b.dataset._txt) b.textContent = b.dataset._txt;
                }
            });
        }
    }

    // Global functions for dataset editing that can be called from dataset.js
    window.editDatasetStakeholders = function(datasetId) {
        // Create stakeholders table in edit mode
        const body = document.querySelector('.content-body');
        body.innerHTML = `<div id="datasetStakeholdersEditTable" class="view-section" style="grid-column:1/-1;"></div>`;

        const stakeholdersTable = new window.StakeholdersTable('datasetStakeholdersEditTable', 'dataset', datasetId);
        stakeholdersTable.loadStakeholders().then(() => {
            stakeholdersTable.enterEditMode();
        });
    };

    window.editDatasetAttributes = function(datasetId) {
        // Create attribute table in edit mode
        const body = document.querySelector('.content-body');
        body.innerHTML = '<div id="datasetAttributeEditContainer" class="view-section" style="grid-column:1/-1;"></div>';

        // Pass view parameter to filter pending changes in View Original mode
        const viewParam = (editViewMode === 'changes') ? 'changes' : null;
        const table = new window.AttributeTable('datasetAttributeEditContainer', datasetId, viewParam);
        table.init().then(() => {
            table.enterEditMode();
        });
    };

    // Success message function (same as other edit pages)
    function showSuccessMessage(message, isError = false, persistent = false) {
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
            z-index: 10000;
            box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
            animation: slideDown 0.3s ease-out;
            ${persistent ? 'cursor: pointer;' : ''}
        `;
        
        // Add close button for persistent error messages
        if (persistent && isError) {
            const closeBtn = document.createElement('span');
            closeBtn.innerHTML = '&times;';
            closeBtn.style.cssText = 'margin-left: 12px; font-size: 20px; cursor: pointer; opacity: 0.9;';
            closeBtn.onclick = () => successDiv.remove();
            successDiv.innerHTML = `<span>${message}</span>`;
            successDiv.appendChild(closeBtn);
        } else {
            successDiv.textContent = message;
        }
        
        const style = document.createElement('style');
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
        document.body.appendChild(successDiv);
        
        // Only auto-remove if not persistent
        if (!persistent) {
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
    }

    async function flushCrossTabPendingChanges(activeTab) {
        if (activeTab !== 'attribute' && window._attributeTableInstance && window._attributeTableInstance.isEditMode) {
            try {
                await window._attributeTableInstance.saveAttributes();
            } catch (error) {
                const msg = error?.message || 'Data Attributes were not saved.';
                showSuccessMessage(`Data Attributes were not saved: ${msg}`, true, true);
                return false;
            }
        }

        if (activeTab !== 'relationships' && window._relationshipsTableInitialized && typeof window.saveDatasetRelationships === 'function') {
            try {
                const relResult = await window.saveDatasetRelationships();
                if (relResult && relResult.success === false) {
                    const msg = relResult.message || 'Relationships were not saved.';
                    showSuccessMessage(`Relationships were not saved: ${msg}`, true, true);
                    return false;
                }
            } catch (error) {
                const msg = error?.message || 'Relationships were not saved.';
                showSuccessMessage(`Relationships were not saved: ${msg}`, true, true);
                return false;
            }
        }

        return true;
    }

    // Lock management is now handled by LockManager class (lock-manager.js)

    document.addEventListener('DOMContentLoaded', async function() {
        const id = parseId();
        if (!id) {
            alert('No dataset ID provided');
            window.location.href = '/';
            return;
        }

        // Initialize lock manager
        const lockManager = new window.LockManager('dataset', id);
        window.currentLockManager = lockManager;
        
        // Setup auto-release on page unload
        lockManager.setupBeforeUnload();
        
        // Check lock status
        const lockStatus = await lockManager.checkLockStatus();
        const status = lockStatus?.status || 'no_lock';
        
        // Handle lock conflicts
        if (status === 'locked_by_other') {
            const lockedBy = lockStatus.lockedByName || 'another user';
            alert(`This dataset is currently locked by ${lockedBy}. Please try again later.`);
             window.location.href = `/view/dataset/${id}`;
            return;
        } else if (status === 'permanently_locked') {
            const isSuperAdmin = await lockManager.checkIsSuperAdmin();
            if (!isSuperAdmin) {
                const lockedBy = lockStatus.lockedByName || 'an administrator';
                alert(`This dataset has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
                 window.location.href = `/view/dataset/${id}`;
                return;
            }
        }
        
        // Acquire lock
        const lockResult = await lockManager.acquireLock(false);
        if (!lockResult || !lockResult.success) {
            // Check if we have details about who locked it
            if (lockResult && lockResult.lockedBy) {
                const lockedBy = lockResult.lockedBy;
                alert(`The object is currently locked by ${lockedBy}. Try again later.`);
            } else {
                alert('Failed to acquire lock. Please try again.');
            }
            window.location.href = `/view/dataset/${id}`;
            return;
        }
        
        // Update global lock count if available
        if (window.globalLockUI) {
            await window.globalLockUI.updateLockCount();
        }

        // Load dropdown data
        try {
            await loadDatasetEditLookups();
        } catch(err) {
            console.error('Failed to load lookups:', err);
        }
        
        // Clean up any searchable-select-wrapper that might have been created by dataset.js
        const glossarySelectEl = document.getElementById('dsGlossary');
        if (glossarySelectEl) {
            const wrapper = glossarySelectEl.closest('.searchable-select-wrapper');
            if (wrapper) {
                // Remove the wrapper but keep the select
                const parent = wrapper.parentNode;
                parent.insertBefore(glossarySelectEl, wrapper);
                wrapper.remove();
            }
            // Ensure select is hidden
            glossarySelectEl.style.display = 'none';
        }
        
        // Initialize segment field
        if (window.SegmentField) {
            segmentField = await SegmentField.init('segmentFieldContainer', {
                label: 'Segment',
                required: true,
                // Removed defaultValue - let API data set the correct value
                sectionTitle: 'OTHER INFORMATION',
                onChange: async () => {
                    try {
                        const currentValue = document.getElementById('dsSystem')?.value || null;
                        const systems = await window.BUDG_API_SERVICE.getSystemsList({ segmentId: segmentField ? segmentField.getValue() : 1 });
                        populateSelectSimple('dsSystem', systems, x => x.name);
                        const el = document.getElementById('dsSystem');
                        if (el) {
                            if (currentValue && Array.from(el.options).some(o => o.value === String(currentValue))) {
                                el.value = String(currentValue);
                            } else if (el.options.length > 0) {
                                el.selectedIndex = 0;
                            }
                        }
                    } catch (e) {
                        console.warn('Failed to reload systems after segment change', e);
                    }

                    try {
                        const currentGlossaryValue = document.getElementById('dsGlossary')?.value || null;
                        const glossaries = await window.BUDG_API_SERVICE.getGlossaryList({ segmentId: segmentField ? segmentField.getValue() : 1 });
                        populateSelectSimple('dsGlossary', glossaries, x => x.description ? `${x.name} — ${x.description}` : x.name);
                        const elG = document.getElementById('dsGlossary');
                        if (elG) {
                            if (currentGlossaryValue && Array.from(elG.options).some(o => o.value === String(currentGlossaryValue))) {
                                elG.value = String(currentGlossaryValue);
                            }
                            // Don't set default - preserve existing selection or leave empty
                            updateGlossaryDisplay();
                        }
                    } catch (e) {
                        console.warn('Failed to reload glossaries after segment change', e);
                    }
                }
            });
        }
        
        // Under active CR, edit page must load pending (nobject_id) data
        editViewMode = await determineEditViewMode(id);
        // Load existing dataset data (this will also set the segment value)
        await loadDataset(id, editViewMode);
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Dataset',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }

        // Wire up action buttons
        const saveBtn = document.getElementById('editSaveBtn');
        const saveCloseBtn = document.getElementById('editSaveCloseBtn');
        const cancelBtn = document.getElementById('editCancelBtn');

        if (saveBtn) saveBtn.addEventListener('click', async () => {
            const activeTab = document.querySelector('.tab.active');
            const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'details';
            const crossTabOk = await flushCrossTabPendingChanges(tabName);
            if (!crossTabOk) return;

            if (tabName === 'stakeholders') {
                // Save stakeholders
                if (window.DatasetStakeholderEdit && window.DatasetStakeholderEdit.saveStakeholders) {
                    try {
                        const saved = await window.DatasetStakeholderEdit.saveStakeholders();
                        if (saved === false) {
                            showSuccessMessage('Stakeholders were not saved. Please review the data and try again.', true, true);
                        } else {
                            showSuccessMessage('UPDATES SAVED');
                        }
                    } catch (error) {
                        showSuccessMessage(`Stakeholders were not saved: ${error?.message || 'Unknown error'}`, true, true);
                    }
                }
            } else if (tabName === 'values') {
                if (window._valuesEditInstance && window._valuesEditInstance.saveValues) {
                    try {
                        const result = await window._valuesEditInstance.saveValues();
                        if (result && result.success === false) {
                            showSuccessMessage(result.message || 'Failed to save values', true, true);
                        } else {
                            showSuccessMessage((result && result.message) || 'Values saved successfully');
                        }
                    } catch (e) {
                        showSuccessMessage('Failed to save values: ' + e.message, true);
                    }
                }
            } else if (tabName === 'attribute') {
                // Save attributes
                if (window._attributeTableInstance && window._attributeTableInstance.saveAttributes) {
                    console.log('Saving attributes...');
                    try {
                        const success = await window._attributeTableInstance.saveAttributes();
                        if (success) {
                            showSuccessMessage('Attributes saved successfully!');
                        } else {
                            const errText = (window.I18n && window.I18n.t('createPage.message.duplicateRefNumber', { facet: 'Attributes' })) || 'Failed to save attributes.';
                            showSuccessMessage(errText, true, true);
                        }
                    } catch (error) {
                        console.error('Error saving attributes:', error);
                        let errorMsg = error && error.message ? error.message : 'Failed to save attributes.';
                        if (errorMsg.includes('reference') && errorMsg.includes('already exists') && window.I18n) {
                            errorMsg = window.I18n.t('createPage.message.duplicateRefNumber', { facet: 'Attributes' });
                        }
                        showSuccessMessage(errorMsg, true, true);
                    }
                } else {
                    console.error('Attribute table instance not found or saveAttributes method not available');
                    showSuccessMessage('Attribute save functionality not available', true, true);
                }
            } else if (tabName === 'impact') {
                // Save impact data - same logic as process and business area impact
                // Check if there are any changes first
                const impactHasChanges = window.hasImpactChanges && window.hasImpactChanges();
                
                console.log('Impact changes:', impactHasChanges);
                
                if (!impactHasChanges) {
                    // No changes - show NO CHANGES message
                    showSuccessMessage('NO CHANGES', true);
                    return;
                }
                
                // There are changes - proceed with save
                if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                    const impactResult = await window.saveAllImpactData();
                    if (impactResult && impactResult.success !== false) {
                        // Update original data after successful save to prevent false "NO CHANGES" on next save
                        // Reload impact data to sync original data with server state
                        if (window.initImpactEdit && id) {
                            await window.initImpactEdit(id);
                        }
                        showSuccessMessage('UPDATES SAVED');
                    } else {
                        showSuccessMessage(impactResult?.message || 'Failed to save impact data', true);
                    }
                } else {
                    console.error('Impact save functionality not available');
                    showSuccessMessage('Impact save functionality not available', true);
                }
            } else {
                // Save dataset details
                await saveDataset(id, false);
            }
        });

        if (saveCloseBtn) saveCloseBtn.addEventListener('click', async () => {
            const activeTab = document.querySelector('.tab.active');
            const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'details';
            const crossTabOk = await flushCrossTabPendingChanges(tabName);
            if (!crossTabOk) return;

            if (tabName === 'stakeholders') {
                // Save stakeholders and close
                if (window.DatasetStakeholderEdit && window.DatasetStakeholderEdit.saveStakeholders) {
                    const success = await window.DatasetStakeholderEdit.saveStakeholders();
                    if (success) {
                        setTimeout(() => {
                            window.location.href = `/view/dataset/${id}`;
                        }, 1000);
                    }
                }
            } else if (tabName === 'values') {
                if (window._valuesEditInstance && window._valuesEditInstance.saveValues) {
                    try {
                        const result = await window._valuesEditInstance.saveValues();
                        if (result && result.success === false) {
                            showSuccessMessage(result.message || 'Failed to save values', true, true);
                        } else {
                            showSuccessMessage((result && result.message) || 'Values saved successfully');
                            setTimeout(() => {
                                window.location.href = `/view/dataset/${id}`;
                            }, 1000);
                        }
                    } catch (e) {
                        showSuccessMessage('Failed to save values: ' + e.message, true);
                    }
                }
            } else if (tabName === 'attribute') {
                // Save attributes and close
                if (window._attributeTableInstance && window._attributeTableInstance.saveAttributes) {
                    console.log('Saving attributes and closing...');
                    try {
                        const success = await window._attributeTableInstance.saveAttributes();
                        if (success) {
                            showSuccessMessage('Attributes saved successfully!');
                            setTimeout(() => {
                                window.location.href = `/view/dataset/${id}`;
                            }, 1000);
                        } else {
                            const errText = (window.I18n && window.I18n.t('createPage.message.duplicateRefNumber', { facet: 'Attributes' })) || 'Failed to save attributes.';
                            showSuccessMessage(errText, true, true);
                        }
                    } catch (error) {
                        console.error('Error saving attributes:', error);
                        let errorMsg = error && error.message ? error.message : 'Failed to save attributes.';
                        if (errorMsg.includes('reference') && errorMsg.includes('already exists') && window.I18n) {
                            errorMsg = window.I18n.t('createPage.message.duplicateRefNumber', { facet: 'Attributes' });
                        }
                        showSuccessMessage(errorMsg, true, true);
                    }
                } else {
                    console.error('Attribute table instance not found or saveAttributes method not available');
                    showSuccessMessage('Attribute save functionality not available', true, true);
                }
            } else if (tabName === 'impact') {
                // Save impact data and close - same logic as process and business area impact
                // Check if there are any changes first
                const impactHasChanges = window.hasImpactChanges && window.hasImpactChanges();
                
                console.log('Impact changes:', impactHasChanges);
                
                if (!impactHasChanges) {
                    // No changes - show NO CHANGES message and close
                    showSuccessMessage('NO CHANGES', true);
                    setTimeout(() => {
                        window.location.href = `/view/dataset/${id}`;
                    }, 1500);
                    return;
                }
                
                // There are changes - proceed with save
                if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                    const impactResult = await window.saveAllImpactData();
                    if (impactResult && impactResult.success !== false) {
                        // Update original data after successful save to prevent false "NO CHANGES" on next save
                        // Reload impact data to sync original data with server state
                        if (window.initImpactEdit && id) {
                            await window.initImpactEdit(id);
                        }
                        showSuccessMessage('UPDATES SAVED');
                        setTimeout(() => {
                            window.location.href = `/view/dataset/${id}`;
                        }, 1500);
                    } else {
                        showSuccessMessage(impactResult?.message || 'Failed to save impact data', true);
                    }
                } else {
                    console.error('Impact save functionality not available');
                    showSuccessMessage('Impact save functionality not available', true);
                }
            } else {
                // Save dataset details and close
                try {
                    await saveDataset(id, true);
                } catch (error) {
                    // If save fails, don't close the page - error is already shown
                    console.error('Error saving dataset:', error);
                }
            }
        });

        if (cancelBtn) cancelBtn.addEventListener('click', async () => {
            // Release lock before canceling
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }
            window.location.href = `/view/dataset/${id}`;
        });

        // Show editor buttons – advanced rich text editor
        const showDefinitionEditorBtn = document.getElementById('showDefinitionEditorBtn');
        if (showDefinitionEditorBtn) {
            showDefinitionEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('dsDefinition', showDefinitionEditorBtn);
            });
        }
        const showUsageEditorBtn = document.getElementById('showUsageEditorBtn');
        if (showUsageEditorBtn) {
            showUsageEditorBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                toggleAdvancedRichTextEditor('dsUsage', showUsageEditorBtn);
            });
        }
        
        // Save & Submit button handler - for edit workflow on existing objects
        const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
        if (saveSubmitBtn) {
            saveSubmitBtn.addEventListener('click', async () => {
                console.log('Save & Submit clicked - will save and create change request');
                const datasetId = id;
                const datasetName = document.getElementById('dsName')?.value || 'Dataset';
                
                // Get active tab
                const activeTab = document.querySelector('.tab.active');
                const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'details';
                const crossTabOk = await flushCrossTabPendingChanges(tabName);
                if (!crossTabOk) return;
                
                let saveSuccess = false;
                
                // Save based on active tab
                if (tabName === 'stakeholders') {
                    // Save stakeholders
                    if (window.DatasetStakeholderEdit && window.DatasetStakeholderEdit.saveStakeholders) {
                        saveSuccess = await window.DatasetStakeholderEdit.saveStakeholders();
                    }
                } else if (tabName === 'impact') {
                    // Save impact data
                    const impactHasChanges = window.hasImpactChanges && window.hasImpactChanges();
                    if (impactHasChanges) {
                        if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                            const impactResult = await window.saveAllImpactData();
                            saveSuccess = impactResult && impactResult.success !== false;
                        }
                    } else {
                        saveSuccess = true; // No changes to save
                    }
                } else {
                    // Save dataset details (summary tab)
                    saveSuccess = await saveDataset(datasetId, false);
                }
                
                const isSaveSuccessful = !!saveSuccess && (saveSuccess.success !== false);
                if (isSaveSuccessful) {
                    // Check if a CR was auto-created in the save response
                    let changeRequestId = null;
                    
                    // Try to get CR ID from save response
                    if (typeof saveSuccess === 'object' && saveSuccess?.changeRequestId) {
                        changeRequestId = saveSuccess.changeRequestId;
                    } else if (typeof saveSuccess === 'object' && saveSuccess?.pendingChanges && saveSuccess?.id) {
                        changeRequestId = saveSuccess.id;
                    }
                    
                    // If no CR was auto-created, check if one exists
                    if (!changeRequestId) {
                        try {
                            // Check if a CR already exists for this dataset
                            const checkResponse = await fetch(`/api/dataset/${datasetId}`, {
                                method: 'GET',
                                credentials: 'include'
                            });
                            if (checkResponse.ok) {
                                const datasetData = await checkResponse.json();
                                // The backend should return CR info if one exists
                                // For now, we'll try to create one if needed
                            }
                        } catch (e) {
                            console.warn('Could not check for existing CR:', e);
                        }
                    }
                    
                    // Changes are saved - backend auto-creates CR when workflow is enabled
                    // Do NOT create CR manually from frontend to avoid duplicates
                    if (changeRequestId) {
                        alert('Changes saved and submitted for approval. Change Request ID: ' + changeRequestId);
                    } else {
                        // Backend handles CR creation automatically, just show success
                        alert('Changes saved successfully.');
                    }
                    window.location.href = `/view/dataset/${datasetId}`;
                }
            });
        }

        // Tab switching logic
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                console.log('Tab clicked:', tabName);

                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                this.classList.add('active');

                // Hide all tab panels
                const datasetEditContainer = document.getElementById('datasetEditContainer');
                if (datasetEditContainer) datasetEditContainer.style.display = 'none';
                
                const stakeholdersTab = document.getElementById('stakeholdersTab');
                if (stakeholdersTab) stakeholdersTab.style.display = 'none';

                const valuesTab = document.getElementById('valuesTab');
                if (valuesTab) valuesTab.style.display = 'none';

                const attributeTab = document.getElementById('attributeTab');
                if (attributeTab) attributeTab.style.display = 'none';
                
                const relationshipsTab = document.getElementById('relationshipsTab');
                if (relationshipsTab) relationshipsTab.style.display = 'none';
                
                const impactTab = document.getElementById('impactTab');
                if (impactTab) impactTab.style.display = 'none';

                // Show selected tab
                if (tabName === 'details') {
                    const datasetEditContainer = document.getElementById('datasetEditContainer');
                    if (datasetEditContainer) datasetEditContainer.style.display = 'block';
                } else if (tabName === 'stakeholders') {
                    const stakeholdersTab = document.getElementById('stakeholdersTab');
                    if (stakeholdersTab) stakeholdersTab.style.display = 'block';
                    
                    // Initialize stakeholders edit if not already done
                    if (window.DatasetStakeholderEdit && !window.DatasetStakeholderEdit._initialized) {
                        window.DatasetStakeholderEdit.init(id, editViewMode);
                        window.DatasetStakeholderEdit._initialized = true;
                    }
                } else if (tabName === 'values') {
                    const valuesTab = document.getElementById('valuesTab');
                    if (valuesTab) valuesTab.style.display = 'block';

                    if (window.DatasetValuesEdit && !window._valuesEditInitialized) {
                        // Pass view parameter to filter pending changes in View Original mode
                        const viewParam = (editViewMode === 'changes') ? 'changes' : null;
                        const valuesEdit = new window.DatasetValuesEdit('datasetValuesEditContainer', id, viewParam);
                        window._valuesEditInstance = valuesEdit;
                        valuesEdit.init();
                        window._valuesEditInitialized = true;
                    }
                } else if (tabName === 'attribute') {
                    const attributeTab = document.getElementById('attributeTab');
                    if (attributeTab) attributeTab.style.display = 'block';
                    
                    // Initialize attribute table if needed
                    if (window.AttributeTable && !window._attributeTableInitialized) {
                        const container = document.getElementById('datasetAttributeContainer');
                        if (container) {
                            // Clear any existing content
                            container.innerHTML = '';
                            
                            // Add proper styling classes
                            container.className = 'view-section attribute-tab-container';
                            container.style.gridColumn = '1/-1';
                            container.style.padding = '0';
                            container.style.margin = '0';
                            
                            // Pass view parameter to filter pending changes in View Original mode
                            const viewParam = (editViewMode === 'changes') ? 'changes' : null;
                            const table = new window.AttributeTable('datasetAttributeContainer', id, viewParam);
                            window._attributeTableInstance = table; // Save instance globally
                            table.init().then(() => {
                                table.enterEditMode();
                            });
                            window._attributeTableInitialized = true;
                        }
                    }
                } else if (tabName === 'relationships') {
                    const relationshipsTab = document.getElementById('relationshipsTab');
                    if (relationshipsTab) relationshipsTab.style.display = 'block';
                    
                    // Initialize relationships edit if not already done
                    if (window.initDatasetRelationshipEdit && !window._relationshipsTableInitialized) {
                        window.initDatasetRelationshipEdit(id, editViewMode);
                        window._relationshipsTableInitialized = true;
                    }
                } else if (tabName === 'impact') {
                    const impactTab = document.getElementById('impactTab');
                    if (impactTab) {
                        impactTab.style.display = 'block';
                        // Initialize impact edit if not already initialized
                        if (window.initImpactEdit && typeof window.initImpactEdit === 'function') {
                            window.initImpactEdit(id, editViewMode);
                        }
                    }
                }
            });
        });

        // Check if we should open a specific tab from URL parameter
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        const isStakeholderOnly = urlParams.get('stakeholderOnly') === 'true';
        
        if (tabParam) {
            const targetTab = document.querySelector(`.tab[data-tab="${tabParam}"]`);
            if (targetTab) {
                setTimeout(() => {
                    targetTab.click();
                }, 100);
            }
        }
        
        // Stakeholder-only mode: disable all other tabs when opened from view page with auto CR active
        if (isStakeholderOnly && tabParam === 'stakeholders') {
            console.log('[Dataset Edit] Stakeholder-only mode enabled - locking other tabs');
            document.querySelectorAll('.tab').forEach(function(tab) {
                if (tab.getAttribute('data-tab') !== 'stakeholders') {
                    tab.disabled = true;
                    tab.classList.add('disabled');
                    tab.style.opacity = '0.4';
                    tab.style.cursor = 'not-allowed';
                    tab.style.pointerEvents = 'none';
                    tab.title = 'Only stakeholder editing is allowed during an active Auto CR';
                }
            });
        }

        // Setup Glossary Picker Icon
        setupGlossaryPicker();
        
        // Update glossary display when select changes
        const glossarySelect = document.getElementById('dsGlossary');
        if (glossarySelect) {
            glossarySelect.addEventListener('change', updateGlossaryDisplay);
            // Ensure select is hidden and remove any searchable-select-wrapper
            glossarySelect.style.display = 'none';
            const wrapper = glossarySelect.closest('.searchable-select-wrapper');
            if (wrapper) {
                wrapper.remove();
            }
        }
    });

    // Glossary Selection Modal Functions
    async function setupGlossaryPicker() {
        const glossaryPickerIcon = document.getElementById('glossaryPickerIcon');
        if (!glossaryPickerIcon) return;

        glossaryPickerIcon.addEventListener('click', async () => {
            await openGlossarySelectionModal();
        });

        // Setup modal close handlers
        const closeBtn = document.getElementById('closeGlossaryModal');
        const cancelBtn = document.getElementById('cancelGlossarySelection');
        const modal = document.getElementById('glossarySelectionModal');

        if (closeBtn) {
            closeBtn.addEventListener('click', closeGlossarySelectionModal);
        }
        if (cancelBtn) {
            cancelBtn.addEventListener('click', closeGlossarySelectionModal);
        }
        if (modal) {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    closeGlossarySelectionModal();
                }
            });
        }
    }

    async function openGlossarySelectionModal() {
        try {
            const modal = document.getElementById('glossarySelectionModal');
            const glossaryList = document.getElementById('glossaryList');
            const searchInput = document.getElementById('glossarySearchInput');
            
            if (!modal || !glossaryList) {
                console.error('Glossary selection modal elements not found');
                return;
            }

            // Get current segment ID
            const selectedSegmentId = segmentField ? segmentField.getValue() : 1;
            
            // Load glossaries
            const glossaries = await loadGlossariesForSelection(selectedSegmentId);
            
            // Clear previous content
            glossaryList.innerHTML = '';
            if (searchInput) {
                searchInput.value = '';
            }
            
            // Render glossaries
            renderGlossaryList(glossaries);
            
            // Setup search functionality
            if (searchInput) {
                searchInput.addEventListener('input', (e) => {
                    const query = e.target.value.toLowerCase();
                    filterGlossaryList(query, glossaries);
                });
            }
            
            // Show modal
            modal.style.display = 'flex';
            
            // Focus on search input
            if (searchInput) {
                setTimeout(() => searchInput.focus(), 100);
            }
        } catch (error) {
            console.error('Error opening glossary selection modal:', error);
            alert('Error loading glossaries. Please try again.');
        }
    }

    async function loadGlossariesForSelection(segmentId) {
        try {
            const glossaries = await window.BUDG_API_SERVICE.getGlossaryList({ segmentId: segmentId });
            return Array.isArray(glossaries) ? glossaries : (glossaries?.data || []);
        } catch (error) {
            console.error('Error loading glossaries:', error);
            return [];
        }
    }

    function renderGlossaryList(glossaries) {
        const glossaryList = document.getElementById('glossaryList');
        if (!glossaryList) return;

        if (glossaries.length === 0) {
            const noGlossariesItem = document.createElement('div');
            noGlossariesItem.className = 'no-glossaries';
            noGlossariesItem.textContent = 'No glossaries found';
            glossaryList.appendChild(noGlossariesItem);
            return;
        }

        glossaries.forEach((glossary) => {
            const glossaryItem = document.createElement('div');
            glossaryItem.className = 'glossary-item';
            glossaryItem.dataset.glossaryId = String(glossary.id || glossary.ID);
            glossaryItem.innerHTML = `
                <div class="glossary-name">${escapeHtml(glossary.name || glossary.Name || 'Unnamed')}</div>
                <div class="glossary-description">${escapeHtml(glossary.description || glossary.Description || 'No description')}</div>
            `;
            
            glossaryItem.addEventListener('click', () => {
                selectGlossary(glossary);
            });
            
            glossaryList.appendChild(glossaryItem);
        });
    }

    function filterGlossaryList(query, allGlossaries) {
        const glossaryList = document.getElementById('glossaryList');
        if (!glossaryList) return;

        glossaryList.innerHTML = '';

        if (!query || query.trim() === '') {
            renderGlossaryList(allGlossaries);
            return;
        }

        const filtered = allGlossaries.filter(glossary => {
            const name = (glossary.name || glossary.Name || '').toLowerCase();
            const description = (glossary.description || glossary.Description || '').toLowerCase();
            const searchable = name + ' ' + description;
            return searchable.includes(query.toLowerCase());
        });

        if (filtered.length === 0) {
            const noResultsItem = document.createElement('div');
            noResultsItem.className = 'no-glossaries';
            noResultsItem.textContent = 'No glossaries found matching your search';
            glossaryList.appendChild(noResultsItem);
            return;
        }

        renderGlossaryList(filtered);
    }

    function selectGlossary(glossary) {
        const glossaryId = glossary.id || glossary.ID;
        const glossarySelect = document.getElementById('dsGlossary');
        
        if (glossarySelect && glossaryId) {
            glossarySelect.value = String(glossaryId);
            // Trigger change event to ensure any listeners are notified
            glossarySelect.dispatchEvent(new Event('change'));
            // Update display field
            updateGlossaryDisplay();
        }
        
        closeGlossarySelectionModal();
    }

    function updateGlossaryDisplay() {
        const glossarySelect = document.getElementById('dsGlossary');
        const glossaryDisplay = document.getElementById('dsGlossaryDisplay');
        
        if (!glossarySelect || !glossaryDisplay) return;
        
        const selectedValue = glossarySelect.value;
        if (selectedValue) {
            const selectedOption = glossarySelect.options[glossarySelect.selectedIndex];
            if (selectedOption) {
                glossaryDisplay.value = selectedOption.textContent;
            } else {
                glossaryDisplay.value = '';
            }
        } else {
            glossaryDisplay.value = '';
        }
    }

    function closeGlossarySelectionModal() {
        const modal = document.getElementById('glossarySelectionModal');
        if (modal) {
            modal.style.display = 'none';
        }
    }

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
})();

