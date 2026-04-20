// Project Edit Page JavaScript - Based on Policy Edit
let segmentField = null; // Segment field component reference
let pendingProjectSegmentId = null;
let originalProjectSegmentId = null; // segment loaded from server; used to detect segment change

function normalizeFacetSegmentId(record) {
    const raw = record?.segmentId ?? record?.segment_id ?? record?.Segment_ID;
    const parsed = parseInt(raw, 10);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('Initializing project edit page...');

    const id = parseId();
    if (!id) {
        console.error('No project ID found in URL');
        return;
    }

    // Initialize lock manager
    const lockManager = new window.LockManager('project', id);
    window.currentLockManager = lockManager;
    
    // Setup auto-release on page unload
    lockManager.setupBeforeUnload();
    
    // Check lock status
    const lockStatus = await lockManager.checkLockStatus();
    const status = lockStatus?.status || 'no_lock';
    
    // Handle lock conflicts
    if (status === 'locked_by_other') {
        const lockedBy = lockStatus.lockedByName || 'another user';
        alert(`This project is currently locked by ${lockedBy}. Please try again later.`);
        window.location.href = `/view/project/project.html?id=${id}`;
        return;
    } else if (status === 'permanently_locked') {
        const isSuperAdmin = await lockManager.checkIsSuperAdmin();
        if (!isSuperAdmin) {
            const lockedBy = lockStatus.lockedByName || 'an administrator';
            alert(`This project has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
            window.location.href = `/view/project/project.html?id=${id}`;
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
        window.location.href = `/view/project/project.html?id=${id}`;
        return;
    }
    
    // Update global lock count if available
    if (window.globalLockUI) {
        await window.globalLockUI.updateLockCount();
    }

    console.log('Initializing project edit page for ID:', id);

    // Initialize the page
    initializePage();
});

function parseId() {
    console.log('Parsing ID from URL:', window.location.href);
    console.log('Pathname:', window.location.pathname);
    console.log('Search:', window.location.search);

    // First try URL params (for separate edit pages)
    const urlParams = new URLSearchParams(window.location.search);
    const id = parseInt(urlParams.get('id'), 10);
    console.log('ID from URL params:', id);
    if (!Number.isNaN(id)) return id;

    // Fallback to path-based ID (for main view pages)
    const parts = window.location.pathname.split('/').filter(Boolean);
    console.log('Path parts:', parts);
    // expect /view/project/project-edit.html?id={id}
    const idx = parts.indexOf('project-edit.html');
    console.log('Project edit index:', idx);
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    console.log('ID from path:', pathId);
    return Number.isNaN(pathId) ? null : pathId;
}

// Get active tab from URL parameters
function getActiveTabFromURL() {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get('tab') || 'summary';
}

function populateSelectSimple(selectId, list, getLabel) {
    const el = document.getElementById(selectId);
    console.log(`Populating select ${selectId}:`, { element: el, list: list });
    if (!el) {
        console.log(`Element ${selectId} not found`);
        return;
    }
    el.innerHTML = '';
    console.log(`Adding ${(Array.isArray(list) ? list : []).length} options to ${selectId}`);
    (Array.isArray(list) ? list : []).forEach((item, index) => {
        const op = document.createElement('option');
        op.value = String(item.ID || item.id);
        let textContent;
        if (getLabel) {
            textContent = getLabel(item);
            console.log(`Using getLabel for item ${index}:`, item, 'result:', textContent);
        } else {
            textContent = (item.PrimaryName || item.primaryname || item.name || '');
            console.log(`Using fallback for item ${index}:`, item, 'result:', textContent);
        }
        op.textContent = textContent;
        console.log(`Option ${index}: value=${op.value}, text=${op.textContent}`);
        el.appendChild(op);
    });
    console.log(`Final ${selectId} options count:`, el.children.length);
}

async function loadProjectEditLookups() {
    console.log('Loading project edit lookups...');

    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
        console.log('API service not ready, waiting...');
        await new Promise(resolve => {
            const checkApi = () => {
                if (window.BUDG_API_SERVICE) {
                    resolve();
                } else {
                    setTimeout(checkApi, 100);
                }
            };
            checkApi();
        });
    }

    try {
        const [projectTypes, statuses, lifecycle, viewing, rag, classifications] = await Promise.all([
            window.BUDG_API_SERVICE.getProjectTypes(),
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getProjectLifecycleList(),
            window.BUDG_API_SERVICE.getViewingList(),
            window.BUDG_API_SERVICE.getProjectRagList(),
            window.BUDG_API_SERVICE.getProjectClassifications()
        ]);

        console.log('Project types:', projectTypes);
        console.log('Statuses:', statuses);
        console.log('Project lifecycle data:', lifecycle);
        console.log('Lifecycle array length:', lifecycle?.length);
        console.log('First lifecycle item:', lifecycle?.[0]);
        console.log('Viewing:', viewing);
        console.log('RAG:', rag);
        console.log('Classifications:', classifications);

        // Populate dropdowns
        populateSelectSimple('projectType', projectTypes, x => x.PrimaryName || x.primaryname || x.name);
        populateSelectSimple('budgStatus', (Array.isArray(statuses?.data) ? statuses.data : statuses), x => x.name);
        populateSelectSimple('lifecycle', lifecycle, x => x.PrimaryName || x.primaryname || x.name);
        populateSelectSimple('budgViewing', viewing, x => x.name);
        populateSelectSimple('rag', rag, x => x.PrimaryName || x.primaryname || x.name);

        console.log('Project edit lookups loaded successfully');
    } catch (error) {
        console.error('Error loading project edit lookups:', error);
    }
}

async function loadProject(id) {
    console.log('Loading project with ID:', id);

    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
        console.log('API service not ready, waiting...');
        await new Promise(resolve => {
            const checkApi = () => {
                if (window.BUDG_API_SERVICE) {
                    resolve();
                } else {
                    setTimeout(checkApi, 100);
                }
            };
            checkApi();
        });
    }

    try {
        const project = await window.BUDG_API_SERVICE.getProjectById(id);
        console.log('Loaded project data:', project);

        if (project) {
            populateForm(project);
            updateTitle(project);
            loadParentProject(project);
        }
    } catch (error) {
        console.error('Error loading project:', error);
    }
}

function populateForm(project) {
    console.log('Project data fields:', Object.keys(project));
    console.log('Project values:', project);
    const loadedSegmentId = normalizeFacetSegmentId(project);
    window.currentImpactSegmentId = loadedSegmentId || 1;

    const setVal = (i, v) => {
        const el = document.getElementById(i);
        if (el) {
            el.value = v ?? '';
            console.log(`Set ${i} to:`, v, 'element found:', !!el);
        } else {
            console.log(`Element ${i} not found`);
        }
    };

    const setSel = (i, v) => {
        const el = document.getElementById(i);
        if (el && v != null) {
            el.value = String(v);
            console.log(`Set select ${i} to:`, v, 'element found:', !!el);
        } else {
            console.log(`Select element ${i} not found or value is null:`, v);
        }
    };

    // Populate form fields based on schema.sql project table structure
    setVal('projectName', project.primaryname || project.PrimaryName);
    setVal('projectRef', project.refnumber || project.RefNumber);
    setVal('projectDescription', project.description || project.Description);

    // Set dates
    if (project.startdate || project.StartDate) {
        const startDate = new Date(project.startdate || project.StartDate);
        setVal('startDate', startDate.toISOString().split('T')[0]);
    }
    if (project.enddate || project.EndDate) {
        const endDate = new Date(project.enddate || project.EndDate);
        setVal('endDate', endDate.toISOString().split('T')[0]);
    }

    // Set dropdowns
    setSel('budgStatus', project.status || project.Status);
    setSel('lifecycle', project.lifecycle_status || project.Lifecycle_Status);
    setSel('budgViewing', project.is_public || project.IsPublic);
    setSel('rag', project.rag || project.RAG);
    setSel('projectType', project.project_type || project.Project_Type);

    // Set classification
    if (project.classification || project.Classification) {
        loadClassification(project.classification || project.Classification);
    }
    
    // Set segment value if available (or defer until segment field initializes)
    if (loadedSegmentId != null) {
        pendingProjectSegmentId = loadedSegmentId;
        if (segmentField) {
            segmentField.setValue(loadedSegmentId);
        }
    }
    if (segmentField && typeof segmentField.getValue === 'function') {
        const gv = parseInt(segmentField.getValue(), 10);
        originalProjectSegmentId = Number.isInteger(gv) && gv > 0 ? gv : null;
    } else {
        originalProjectSegmentId = loadedSegmentId;
    }
}

function loadClassification(classificationId) {
    if (classificationId) {
        // Load classification name
        window.BUDG_API_SERVICE.getProjectClassifications()
            .then(classifications => {
                const classification = classifications.find(c => c.id === classificationId);
                const classificationInput = document.getElementById('classification');
                if (classificationInput && classification) {
                    classificationInput.value = classification.primaryname || classification.PrimaryName || 'Unnamed Classification';
                    classificationInput.dataset.classificationId = classificationId;
                }
            })
            .catch(error => {
                console.error('Error loading classification:', error);
            });
    }
}

function loadParentProject(project) {
    const parentId = project.parentid;
    if (parentId) {
        // Load parent project name
        window.BUDG_API_SERVICE.getProjectById(parentId)
            .then(parentProject => {
                const parentNameInput = document.getElementById('parentName');
                if (parentNameInput && parentProject) {
                    parentNameInput.value = parentProject.primaryname || parentProject.PrimaryName || 'Unnamed Project';
                    parentNameInput.dataset.parentId = parentId;
                }
            })
            .catch(error => {
                console.error('Error loading parent project:', error);
            });
    }
}

function updateTitle(project) {
    const titleElement = document.getElementById('projectTitle');
    if (titleElement && project) {
        const name = project.primaryname || project.PrimaryName || 'Unnamed Project';
        const ref = project.refnumber || project.RefNumber || '';
        titleElement.textContent = ref ? `${ref}: ${name}` : name;
    }
}

async function saveProject(id, closeAfter) {
    const activeTab = getCurrentActiveTab();
    console.log(`=== SAVING PROJECT (active tab: ${activeTab}) ===`);

    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    const restoreButtons = () => { buttons.forEach(b => { if (b) b.disabled = false; }); };
    buttons.forEach(b => { if (b) b.disabled = true; });

    try {
        if (activeTab === 'relationships') {
            try {
                const relationshipsSaved = await saveProjectOtherRelationships(id);
                if (relationshipsSaved && relationshipsSaved.success) {
                    console.log('✅ Project relationships saved successfully');
                } else {
                    console.warn('Failed to save project relationships:', relationshipsSaved?.message);
                }
            } catch (relationshipsError) {
                console.error('Error saving relationships:', relationshipsError);
                throw relationshipsError;
            }

        } else if (activeTab === 'stakeholders') {
            if (window.ProjectStakeholderEdit && window.ProjectStakeholderEdit.saveStakeholders) {
                const stakeholdersSaved = await window.ProjectStakeholderEdit.saveStakeholders();
                if (!stakeholdersSaved) {
                    showSuccessMessage('Failed to save stakeholders.', true);
                    restoreButtons();
                    return;
                }
                console.log('✅ Stakeholders saved successfully');
            } else {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }

        } else if (activeTab === 'impact') {
            const projectImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
            if (!projectImpactDirty) {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }
            const impactResult = await window.saveAllImpactData(id);
            if (impactResult && impactResult.success) {
                if (window.initImpactEdit) {
                    window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                    await window.initImpactEdit(id);
                }
                console.log('✅ Impact saved successfully');
            } else if (impactResult) {
                const details = Array.isArray(impactResult.errorDetails) ? impactResult.errorDetails.join('. ') : '';
                const msg = impactResult.message || 'Impact save failed';
                throw new Error(details ? `${msg}. ${details}` : msg);
            }

        } else if (activeTab === 'data') {
            showSuccessMessage(window.I18n ? window.I18n.t('regulation.messages.noDataToSaveOnTab') : 'No data to save on this tab', false);
            restoreButtons();
            return;

        } else {
            // summary tab
            const hasCustomFieldsContext = window.customFieldsContext && window.customFieldsContext.saveValues;
            if (hasCustomFieldsContext && window.customFieldsContext.validate) {
                const customFieldsValid = window.customFieldsContext.validate();
                if (!customFieldsValid) {
                    showSuccessMessage('Please fix Custom Fields errors before saving.', true);
                    restoreButtons();
                    return;
                }
            }

            let currentUserId = null;
            try {
                const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
                if (meResp.ok) {
                    const me = await meResp.json();
                    currentUserId = me.id || me.ID || me.userId || me.UserID;
                }
            } catch (e) {
                console.warn('Error fetching current user:', e);
            }

            // Sync rich-text editor content back to textarea before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('projectDescription');
            }

            if (segmentField && !segmentField.validate()) {
                restoreButtons();
                return;
            }

            const payload = collectFormData(currentUserId);
            const res = await window.BUDG_API_SERVICE.updateProject(id, payload);

            if (res && res.success === false) {
                if (res.locked) {
                    const lockedBy = res.lockedBy || 'another user';
                    const isPermanent = res.isPermanent || false;
                    showSuccessMessage(`This project is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`, true);
                    if (window.currentLockManager) { await window.currentLockManager.releaseLock(); }
                    setTimeout(() => { window.location.href = `/view/project/project.html?id=${id}`; }, 3000);
                    restoreButtons();
                    return false;
                }
                throw new Error(res.message || 'Update failed');
            }

            const customFieldsCtx = window.customFieldsContext && window.customFieldsContext.saveValues;
            if (customFieldsCtx) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (customFieldsError) {
                    console.error('❌ Error saving custom fields:', customFieldsError);
                    showSuccessMessage('Project saved, but Custom Fields failed', true);
                }
            }

            // If segment changed, reload impact data (do not save impact from summary)
            const currentProjectSegment = payload.segmentId != null ? parseInt(payload.segmentId, 10) : null;
            const projectSegmentChanged = currentProjectSegment !== originalProjectSegmentId;
            if (projectSegmentChanged && window.initImpactEdit) {
                console.log('=== Segment changed; reloading project impact from server ===');
                window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                await window.initImpactEdit(id);
            }

            // Release lock after successful save
            if (window.currentLockManager) { await window.currentLockManager.releaseLock(); }

            markAsClean();
            originalProjectSegmentId = payload.segmentId != null ? parseInt(payload.segmentId, 10) : null;
        }

        showSuccessMessage('UPDATES SAVED');
        if (closeAfter) {
            setTimeout(() => { window.location.href = `/view/project/project.html?id=${id}`; }, 1500);
        }
    } catch (error) {
        console.error('Error saving:', error);
        const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Failed to save';
        alert(errorMsg);
    } finally {
        restoreButtons();
    }
}

// Get current active tab
function getCurrentActiveTab() {
    const activeTab = document.querySelector('.tab.active');
    return activeTab ? activeTab.getAttribute('data-tab') : 'summary';
}

// Save stakeholders data (updated to use external function)
async function saveStakeholdersData() {
    if (window.ProjectStakeholderEdit && window.ProjectStakeholderEdit.saveStakeholders) {
        return await window.ProjectStakeholderEdit.saveStakeholders();
    } else {
        alert('Stakeholder edit functionality not available.');
        return false;
    }
}

// Save impact data
async function saveImpactData(id) {
    try {
        if (window.saveAllImpactData) {
            console.log('Calling saveAllImpactData...');
            const result = await window.saveAllImpactData();
            
            if (result && result.success === true) {
                showSuccessMessage('IMPACT DATA SAVED');
                // Reload impact data to refresh the UI
                if (window.initImpactEdit) {
                    window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                    window.initImpactEdit(id);
                }
                return true;
            } else {
                const errorMsg = result?.message || result?.errorDetails?.join(', ') || 'Failed to save impact data';
                
                // Check if it's an authentication error
                if (errorMsg.includes('Authentication') || errorMsg.includes('401') || errorMsg.includes('Not authenticated')) {
                    alert('Your session has expired. Please refresh the page and log in again.');
                    // Optionally redirect to login or refresh
                    setTimeout(() => {
                        window.location.reload();
                    }, 2000);
                } else {
                    alert('Error saving impact data: ' + errorMsg);
                }
                
                console.error('Save impact data failed:', result);
                return false;
            }
        } else {
            alert('Impact edit functionality not available.');
            console.error('saveAllImpactData function not found');
            return false;
        }
    } catch (error) {
        console.error('Error saving impact data:', error);
        alert('Error saving impact data: ' + (error.message || 'Unknown error'));
        return false;
    }
}

// Save project main data
async function saveProjectData(id) {
    try {
        // Get current user ID from api/me (like process does)
        let currentUserId = null;
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (meResp.ok) {
                const me = await meResp.json();
                currentUserId = me.id || me.ID || me.userId || me.UserID;
                console.log('Current user ID from api/me:', currentUserId);
            } else {
                console.warn('Failed to get current user from api/me:', meResp.status);
            }
        } catch (e) {
            console.warn('Error fetching current user:', e);
        }

        // Check if there are any changes
        const hasCustomFieldsContext = window.customFieldsContext && window.customFieldsContext.saveValues;
        if (hasCustomFieldsContext && window.customFieldsContext.validate) {
            const customFieldsValid = window.customFieldsContext.validate();
            if (!customFieldsValid) {
                showSuccessMessage('Please fix Custom Fields errors before saving.', true);
                return false;
            }
        }

        if (!hasFormChanges) {
            if (hasCustomFieldsContext) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    showSuccessMessage('UPDATES SAVED');
                } catch (customFieldsError) {
                    console.error('Error saving custom fields:', customFieldsError);
                    showSuccessMessage('Custom Fields failed to save', true);
                    return false;
                }
            } else {
                showSuccessMessage('NO CHANGES', true);
            }
            return true;
        }

        // Sync rich-text editor content back to textarea before reading
        if (typeof syncAdvancedRichTextToTextarea === 'function') {
            syncAdvancedRichTextToTextarea('projectDescription');
        }

        if (segmentField && !segmentField.validate()) {
            return false;
        }

        const payload = collectFormData(currentUserId);
        console.log('Payload before sending:', payload);
        console.log('Payload JSON:', JSON.stringify(payload, null, 2));

        console.log('Calling updateProject API with ID:', id);
        const res = await window.BUDG_API_SERVICE.updateProject(id, payload);
        console.log('API response:', res);

        if (hasCustomFieldsContext) {
            try {
                await window.customFieldsContext.saveValues(id);
                console.log('✅ Custom fields saved successfully');
            } catch (customFieldsError) {
                console.error('❌ Error saving custom fields:', customFieldsError);
                showSuccessMessage('Project saved, but Custom Fields failed', true);
            }
        }

        // Mark form as clean after successful save
        markAsClean();
        showSuccessMessage('UPDATES SAVED');

        return true;
    } catch (err) {
        console.error('Save error:', err);
        console.error('Error details:', {
            message: err.message,
            status: err.status,
            body: err.body
        });
        alert(typeof window.formatSaveError === 'function' ? window.formatSaveError(err) : (err?.body?.error || err?.body?.message || err?.message || 'Failed to save'));
        return false;
    }
}

// Check if any data has changed across ALL tabs (not just active)
function hasAnyDataChanged() {
    // Check form (summary) changes
    if (hasFormChanges) return true;
    
    // Check stakeholders changes
    if (window.ProjectStakeholderEdit && window.ProjectStakeholderEdit.hasDataChanged && window.ProjectStakeholderEdit.hasDataChanged()) return true;
    
    // Check impact changes
    if (window.hasImpactChanges && window.hasImpactChanges()) return true;
    
    return false;
}

function collectFormData(currentUserId = null) {
    const name = document.getElementById('projectName')?.value?.trim();
    const description = document.getElementById('projectDescription')?.value?.trim();
    const projectType = parseInt(document.getElementById('projectType')?.value || '', 10);
    const status = parseInt(document.getElementById('budgStatus')?.value || '', 10);
    const lifecycle = parseInt(document.getElementById('lifecycle')?.value || '', 10);
    const viewing = parseInt(document.getElementById('budgViewing')?.value || '', 10);
    const rag = parseInt(document.getElementById('rag')?.value || '', 10);
    const refNumber = document.getElementById('projectRef')?.value?.trim();
    const startDate = document.getElementById('startDate')?.value;
    const endDate = document.getElementById('endDate')?.value;
    const classificationId = document.getElementById('classification')?.dataset.classificationId;

    console.log('Validation values:', { name, description, projectType, status, lifecycle, viewing, classificationId });

    const required = [
        { ok: !!name, field: 'Name' },
        { ok: !!description, field: 'Description' },
        { ok: Number.isInteger(status), field: 'BUDG Status' },
        { ok: Number.isInteger(lifecycle), field: 'Lifecycle' },
        { ok: Number.isInteger(viewing), field: 'BUDG Viewing' }
    ];

    const missing = required.filter(r => !r.ok).map(r => r.field);
    console.log('Required fields check:', required);
    console.log('Missing fields:', missing);

    if (missing.length > 0) {
        throw new Error(`Please fill required fields: ${missing.join(', ')}`);
    }

    const payload = {
        primaryname: name,
        description: description,
        refnumber: refNumber || null,
        project_type: Number.isInteger(projectType) ? projectType : null,
        status: status,
        lifecycle_status: lifecycle,
        is_public: viewing,
        rag: Number.isInteger(rag) ? rag : null,
        startdate: startDate || null,
        enddate: endDate || null,
        classification: classificationId ? parseInt(classificationId) : null,
        segmentId: segmentField ? segmentField.getValue() : 1
    };

    if (payload.segmentId == null || payload.segmentId === '') {
        payload.segmentId = pendingProjectSegmentId;
    }

    // Add parent ID if selected
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput && parentNameInput.dataset.parentId) {
        payload.parentid = parseInt(parentNameInput.dataset.parentId);
        console.log('Adding parent ID to payload:', payload.parentid);
    } else {
        payload.parentid = null;
        console.log('No parent ID found in parentName input');
    }

    return payload;
}

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
        right: 20px;
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

    // Add animation styles
    const style = document.createElement('style');
    style.textContent = `
        @keyframes slideDown {
            from {
                opacity: 0;
                transform: translateY(-20px);
            }
            to {
                opacity: 1;
                transform: translateY(0);
            }
        }
        @keyframes slideUp {
            from {
                opacity: 1;
                transform: translateY(0);
            }
            to {
                opacity: 0;
                transform: translateY(-20px);
            }
        }
    `;
    document.head.appendChild(style);
    document.body.appendChild(successDiv);

    // Remove message after 3 seconds
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

function markAsClean() {
    document.body.classList.remove('dirty');
    preventUnloadWarning = true;
    hasFormChanges = false;
    updateSaveButtons();
    console.log('Form marked as clean');
}

function markAsDirty(e) {
    if (e && e.isTrusted === false) return;
    isDirty = true;
    hasFormChanges = true;
    preventUnloadWarning = false;
    updateSaveButtons();
}

// Update save buttons - always keep enabled so user can always save
function updateSaveButtons() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    
    // Always keep save buttons enabled - the save function handles "no changes" gracefully
    if (saveBtn) saveBtn.disabled = false;
    if (saveAndCloseBtn) saveAndCloseBtn.disabled = false;
}

// Global variable to store form data
let formDataCache = {};

// Global variable to track if form has been modified
let hasFormChanges = false;

function saveCurrentFormData() {
    const currentTab = document.querySelector('.tab.active');
    if (!currentTab) return;

    const tabName = currentTab.getAttribute('data-tab');
    if (!tabName) return;

    // Save all form data in the current tab
    const formData = {};
    const currentTabContent = document.getElementById(tabName);
    if (currentTabContent) {
        const inputs = currentTabContent.querySelectorAll('input, textarea, select');
        inputs.forEach(input => {
            if (input.type === 'checkbox' || input.type === 'radio') {
                formData[input.name || input.id] = input.checked;
            } else {
                formData[input.name || input.id] = input.value;
            }
        });
    }

    formDataCache[tabName] = formData;
    console.log('Saved form data for tab:', tabName, formData);
}

function restoreFormData(tabName) {
    if (!formDataCache[tabName]) return;

    const tabContent = document.getElementById(tabName);
    if (!tabContent) return;

    const formData = formDataCache[tabName];
    const inputs = tabContent.querySelectorAll('input, textarea, select');

    inputs.forEach(input => {
        const key = input.name || input.id;
        if (formData.hasOwnProperty(key)) {
            if (input.type === 'checkbox' || input.type === 'radio') {
                input.checked = formData[key];
            } else {
                input.value = formData[key];
            }
        }
    });

    console.log('Restored form data for tab:', tabName, formData);
}

function switchTab(tabName) {
    // Save current form data before switching tabs
    saveCurrentFormData();

    // Remove active class from all tabs and content
    document.querySelectorAll('.tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

    // Add active class to selected tab and content
    const selectedTab = document.querySelector(`[data-tab="${tabName}"]`);
    const selectedContent = document.getElementById(tabName);

    if (selectedTab) selectedTab.classList.add('active');
    if (selectedContent) selectedContent.classList.add('active');

    // Load tab content
    loadTabContent(tabName);

    // Restore form data for the new tab
    restoreFormData(tabName);
}

// Load content for specific tab
function loadTabContent(tabName) {
    const id = parseId();
    if (!id) return;

    switch(tabName) {
        case 'relationships':
            // Load relationships data
            loadProjectHierarchy(id);
            loadProjectOtherRelationships(id);
            break;
        case 'stakeholders':
            // Initialize project stakeholder edit
            if (window.ProjectStakeholderEdit) {
                window.ProjectStakeholderEdit.init(id);
            }
            break;
        case 'impact':
            // Initialize project impact edit
            console.log('Loading Impact tab for project:', id);
            if (typeof initImpactEdit === 'function') {
                window.currentImpactSegmentId = segmentField ? segmentField.getValue() : (window.currentImpactSegmentId || 1);
                initImpactEdit(id);
            } else {
                console.warn('initImpactEdit function not found');
            }
            break;
        case 'data':
            // Data tab: Data Sets, Attributes, and Data Map (same as process facet)
            if (window.loadProjectData) {
                window.loadProjectData(id);
            }
            break;
        case 'summary':
            // Main project details (Summary) - already loaded
            break;
        default:
            // Handle other tabs if needed
            break;
    }
}

// Helper function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Build direct lineage tree - Following Regulatory Theme pattern
function buildDirectLineageTree(projects, currentProjectId) {
    const byId = new Map();
    projects.forEach(p => byId.set(parseInt(p.id), p));
    
    const current = byId.get(parseInt(currentProjectId));
    if (!current) return [];
    
    const ancestors = new Set();
    const descendants = new Set();
    
    // Add current project
    descendants.add(parseInt(currentProjectId));
    
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
    
    // Find all descendants (including current project's children and their descendants)
    function findDescendants(projectId) {
        projects.forEach(p => {
            if (parseInt(p.parentId) === projectId) {
                const childId = parseInt(p.id);
                descendants.add(childId);
                findDescendants(childId); // Recursively find all descendants
            }
        });
    }
    findDescendants(parseInt(currentProjectId));
    
    // Find siblings (other children of the same parent) and their descendants
    if (current.parentId) {
        const parentId = parseInt(current.parentId);
        const siblings = projects.filter(p => {
            const pParentId = parseInt(p.parentId);
            const pId = parseInt(p.id);
            return pParentId === parentId && pId !== parseInt(currentProjectId);
        });
        
        // Add siblings and their descendants
        siblings.forEach(sibling => {
            const siblingId = parseInt(sibling.id);
            descendants.add(siblingId);
            findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
        });
    }
    
    // Include the current project, all its ancestors, and all its descendants (including siblings and their descendants)
    const includedIds = new Set([parseInt(currentProjectId), ...ancestors, ...descendants]);
    
    // Filter projects to include only the complete family tree
    return projects.filter(p => {
        const id = parseInt(p.id);
        return includedIds.has(id);
    });
}

// Build hierarchy tree - Following Regulatory Theme pattern
function buildHierarchyTree(projects, rootId) {
    const byParent = new Map();
    const byId = new Map();
    
    projects.forEach(p => {
        const id = parseInt(p.id);
        const parentId = p.parentId;
        const parentIdNum = parentId ? parseInt(parentId) : null;
        byId.set(id, p);
        
        if (!byParent.has(parentIdNum)) {
            byParent.set(parentIdNum, []);
        }
        byParent.get(parentIdNum).push(p);
    });
    
    const rows = [];
    function buildRows(projectId, depth = 0) {
        const currentProject = byId.get(projectId);
        if (currentProject) {
            const childCount = (byParent.get(projectId) || []).length;
            
            rows.push({
                node: currentProject,
                depth: depth,
                childCount: childCount,
                hasChildren: childCount > 0
            });
            
            const children = byParent.get(projectId) || [];
            children.forEach(p => {
                buildRows(parseInt(p.id), depth + 1);
            });
        } else {
            const children = byParent.get(projectId) || [];
            children.forEach(p => {
                buildRows(parseInt(p.id), depth);
            });
        }
    }
    
    const rootIdNum = rootId ? parseInt(rootId) : null;
    buildRows(rootIdNum);
    
    return { rows, parentMap: byParent };
}

// Render project hierarchy table following Regulatory Theme pattern
function renderProjectTable(hierarchyRows, currentId) {
    const Mask = window.HierarchyMask;
    const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
        const isMaskedNode = Mask ? Mask.isMasked(node) : false;
        const fallbackName = node.primaryName ?? node.PrimaryName ?? node.Name ?? node.name ?? 'Unnamed Project';
        const fallbackDesc = node.description ?? node.Description ?? '';
        const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
        const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
        const isCurrent = String(node.id ?? node.ID) === String(currentId);
        const id = node.id ?? node.ID;
        const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';

        const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
        const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
        const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
        const linkClass = isCurrent ? 'project-link current-project-link' : 'project-link';
        const link = isMaskedNode
            ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
            : `<a class="${linkClass}" href="/view/project/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
        const refNumber = (!isMaskedNode && node.refNumber)
            ? `<span class="project-ref">(${escapeHtml(node.refNumber)})</span>` : '';
        const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

        return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
            <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-project-diagram item-icon"></i><span class="project-name">${link}</span>${refNumber}${countBadge}</div></td>
            <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
        </tr>`;
    }).join('');

    return rowsHtml;
}

// Initialize project hierarchy interactions following Regulatory Theme pattern
function initProjectInteractions(containerEl, hierarchyRows) {
    containerEl.addEventListener('click', function(e) {
        if (e.target.closest('.tree-expander')) {
            e.preventDefault();
            e.stopPropagation();
            
            const button = e.target.closest('.tree-expander');
            const row = button.closest('tr');
            const parentId = parseInt(row.dataset.id);
            const currentDepth = parseInt(row.dataset.depth);
            const icon = button.querySelector('i');
            
            const isExpanded = icon.classList.contains('fa-caret-down');
            icon.classList.toggle('fa-caret-down', !isExpanded);
            icon.classList.toggle('fa-caret-right', isExpanded);
            
            let nextRow = row.nextElementSibling;
            while (nextRow && parseInt(nextRow.dataset.depth) > currentDepth) {
                const childDepth = parseInt(nextRow.dataset.depth);
                
                if (childDepth === currentDepth + 1) {
                    nextRow.style.display = isExpanded ? 'none' : '';
                    
                    if (isExpanded) {
                        const childExpander = nextRow.querySelector('.tree-expander i');
                        if (childExpander) {
                            childExpander.classList.remove('fa-caret-down');
                            childExpander.classList.add('fa-caret-right');
                        }
                    }
                }
                
                nextRow = nextRow.nextElementSibling;
            }
        }
    });
}

// Function to load project hierarchy data - Following Regulatory Theme pattern
async function loadProjectHierarchy(projectId) {
    const container = document.querySelector('.relationships-hierarchy');
    if (!container) {
        console.error('Project hierarchy container not found');
        return;
    }
    
    try {
        container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>';
        
        // Fetch all projects from the new /hierarchy endpoint
        const response = await fetch('/api/project/hierarchy');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const projects = await response.json();
        
        if (!Array.isArray(projects) || projects.length === 0) {
            container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No projects found</div>';
            return;
        }

        // Build direct lineage tree (current + ancestors + descendants + siblings)
        const filteredProjects = buildDirectLineageTree(projects, projectId);
        
        if (filteredProjects.length === 0) {
            container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No related projects found</div>';
            return;
        }
        
        // Find the root of the filtered tree
        const rootProject = filteredProjects.find(project => {
            const parentId = project.parentId;
            return !parentId || parentId === 0 || parentId === null;
        });
        
        const rootId = rootProject ? rootProject.id : projectId;
        const hierarchyRows = buildHierarchyTree(filteredProjects, rootId);
        
        // Create the hierarchy table HTML
        const tableHtml = `
            <div class="hierarchy-header">
                <div class="hierarchy-title">PROJECT HIERARCHY</div>
                <div class="hierarchy-actions">
                    <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                        </div>
                        </div>
            <div class="hierarchy-table-wrapper">
                <table class="hierarchy-table">
                    <thead>
                        <tr>
                            <th>Project</th>
                            <th>Description</th>
                </tr>
                    </thead>
                    <tbody>
                        ${renderProjectTable(hierarchyRows, projectId)}
                    </tbody>
                </table>
                                </div>
            <div class="table-footer">
                ${filteredProjects.length} record${filteredProjects.length !== 1 ? 's' : ''}
                                </div>
        `;
        
        container.innerHTML = tableHtml;
        
        // Initialize interactions
        initProjectInteractions(container, hierarchyRows);

    } catch (error) {
        console.error('Failed to load project hierarchy:', error);
        container.innerHTML = `<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${error.message || 'Unknown error'}</div>`;
    }
}

function setupEventListeners() {
    const id = parseId();
    if (!id) return;

    const saveBtn = document.getElementById('saveBtn');
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

    if (saveBtn) saveBtn.addEventListener('click', () => saveProject(id, false));
    if (saveCloseBtn) saveCloseBtn.addEventListener('click', () => saveProject(id, true));

    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('projectDescription', showEditorBtn);
        });
    }

    if (closeBtn) closeBtn.addEventListener('click', async () => {
        // Release lock before canceling
        if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }
        console.log('Close button clicked, redirecting to project view with ID:', id);
        console.log('Close redirect URL:', `/view/project/project.html?id=${id}`);

        // Simple redirect to project view page
        window.location.href = `/view/project/project.html?id=${id}`;
    });

    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const tabName = tab.dataset.tab;
            switchTab(tabName);
            // Update save buttons when switching tabs
            updateSaveButtons();
        });
    });

    // Form change detection
    const formElements = document.querySelectorAll('input, textarea, select');
    formElements.forEach(element => {
        element.addEventListener('change', markAsDirty);
        element.addEventListener('input', markAsDirty);
    });

    // Monitor stakeholder data changes (only when on stakeholders tab)
    setInterval(() => {
        const activeTab = getCurrentActiveTab();
        if (activeTab === 'stakeholders' && window.ProjectStakeholderEdit && window.ProjectStakeholderEdit.hasDataChanged) {
            const hasStakeholderChanges = window.ProjectStakeholderEdit.hasDataChanged();
            if (hasStakeholderChanges) {
                updateSaveButtons();
            }
        }
    }, 1000);

    // Parent selection modal
    document.getElementById('selectParentBtn')?.addEventListener('click', selectParent);
    document.getElementById('closeParentModal')?.addEventListener('click', closeParentSelectionModal);
    document.getElementById('cancelParentSelection')?.addEventListener('click', closeParentSelectionModal);
    document.getElementById('parentSearchInput')?.addEventListener('input', function(e) {
        filterProjects(e.target.value);
    });
    document.getElementById('parentSelectionModal')?.addEventListener('click', function(e) {
        if (e.target === this) {
            closeParentSelectionModal();
        }
    });

    // Classification selection modal
    document.getElementById('selectClassificationBtn')?.addEventListener('click', selectClassification);
    document.getElementById('closeClassificationModal')?.addEventListener('click', closeClassificationSelectionModal);
    document.getElementById('cancelClassificationSelection')?.addEventListener('click', closeClassificationSelectionModal);
    document.getElementById('classificationSearchInput')?.addEventListener('input', function(e) {
        filterClassifications(e.target.value);
    });
    document.getElementById('classificationSelectionModal')?.addEventListener('click', function(e) {
        if (e.target === this) {
            closeClassificationSelectionModal();
        }
    });
}

// Global variables for dirty state
let isDirty = false;

// Prevent browser warning when leaving page after successful save
let preventUnloadWarning = false;

// Remove any existing beforeunload listeners
window.removeEventListener('beforeunload', window.projectEditBeforeUnload);

window.projectEditBeforeUnload = function(e) {
    console.log('beforeunload triggered:', {
        isDirty: document.body.classList.contains('dirty'),
        preventUnloadWarning: preventUnloadWarning,
        shouldPrevent: document.body.classList.contains('dirty') && !preventUnloadWarning
    });

    if (document.body.classList.contains('dirty') && !preventUnloadWarning) {
        console.log('Preventing unload - showing browser warning');
        e.preventDefault();
        e.returnValue = '';
    } else {
        console.log('Allowing unload - no warning needed');
    }
};

window.addEventListener('beforeunload', window.projectEditBeforeUnload);

// Classification selection functionality
let allClassificationsEdit = [];
let filteredClassificationsEdit = [];

async function selectClassification() {
    try {
        allClassificationsEdit = await window.BUDG_API_SERVICE.getProjectClassifications();
        filteredClassificationsEdit = allClassificationsEdit;

        showClassificationSelectionModal();
    } catch (error) {
        console.error('Error loading classifications for selection:', error);
        alert('Failed to load classifications');
    }
}

function showClassificationSelectionModal() {
    const modal = document.getElementById('classificationSelectionModal');
    if (modal) {
        modal.style.display = 'flex';
        renderClassifications(filteredClassificationsEdit);
    }
}

function renderClassifications(classifications) {
    const container = document.getElementById('classificationsList');
    if (!container) return;

    container.innerHTML = '';

    if (classifications.length === 0) {
        container.innerHTML = '<div class="no-classifications">No classifications available</div>';
        return;
    }

    classifications.forEach(classification => {
        const classificationItem = document.createElement('div');
        classificationItem.className = 'classification-item';

        const classificationName = document.createElement('div');
        classificationName.className = 'classification-name';
        classificationName.textContent = classification.primaryname || classification.PrimaryName || 'Unnamed Classification';

        const classificationDescription = document.createElement('div');
        classificationDescription.className = 'classification-description';
        classificationDescription.textContent = classification.description || classification.Description || '';

        classificationItem.appendChild(classificationName);
        classificationItem.appendChild(classificationDescription);

        classificationItem.addEventListener('click', () => {
            selectClassificationAsParent(classification);
        });

        // Add hover effects
        classificationItem.addEventListener('mouseenter', () => {
            classificationItem.style.backgroundColor = '#f9fafb';
        });

        classificationItem.addEventListener('mouseleave', () => {
            classificationItem.style.backgroundColor = '';
        });

        container.appendChild(classificationItem);
    });
}

function selectClassificationAsParent(classification) {
    console.log('Selected classification:', classification);
    const classificationInput = document.getElementById('classification');
    classificationInput.value = classification.primaryname || classification.PrimaryName || 'Unnamed Classification';
    classificationInput.dataset.classificationId = classification.id;
    console.log('Set classification ID in dataset:', classificationInput.dataset.classificationId);
    closeClassificationSelectionModal();
    markAsDirty();
}

function closeClassificationSelectionModal() {
    const modal = document.getElementById('classificationSelectionModal');
    if (modal) {
        modal.style.display = 'none';
    }
    const searchInput = document.getElementById('classificationSearchInput');
    if (searchInput) {
        searchInput.value = '';
    }
}

function filterClassifications(searchTerm) {
    const filtered = filteredClassificationsEdit.filter(classification => {
        const name = (classification.primaryname || classification.PrimaryName || '').toLowerCase();
        const description = (classification.description || classification.Description || '').toLowerCase();
        const search = searchTerm.toLowerCase();

        return name.includes(search) || description.includes(search);
    });

    renderClassifications(filtered);
}

// Parent selection functionality
let currentProjectId = null;
let allProjects = [];
let filteredProjects = [];

// Normalize project identifiers - use exact schema field names
function getProjectId(project) {
    return project?.id ?? project?.ID ?? project?.Id ?? null;
}

function getProjectParentId(project) {
    // UnisionSearch might use different field names, check all possibilities
    return project?.parentid ?? project?.Parent_ID ?? project?.parent_id ?? project?.ParentID ?? null;
}

function isDescendantOf(project, ancestorId, allProjects) {
    const projectId = getProjectId(project);
    const parentId = getProjectParentId(project);

    console.log(`  🔍 isDescendantOf: project ${projectId}, parent ${parentId}, ancestor ${ancestorId}`);

    if (parentId == null) {
        console.log(`  ❌ No parent - not descendant`);
        return false;
    }

    if (String(parentId) === String(ancestorId)) {
        console.log(`  ✅ Direct child of ancestor`);
        return true;
    }

    const parentProject = allProjects.find(p => String(getProjectId(p)) === String(parentId));
    if (!parentProject) {
        console.log(`  ❌ Parent project not found`);
        return false;
    }

    console.log(`  🔄 Checking parent project ${getProjectId(parentProject)}`);
    const result = isDescendantOf(parentProject, ancestorId, allProjects);
    console.log(`  📋 Result for project ${projectId}: ${result}`);
    return result;
}

async function selectParent() {
    try {
        currentProjectId = parseId();
        console.log('=== PARENT SELECTION DEBUG START ===');
        console.log('Current project ID:', currentProjectId);

        // Check if server-side parent picker includes hierarchy data
        let parentOptions = null;
        let useServerFilter = false;

        try {
            const svc = window.BUDG_API_SERVICE;
            const endpoint = svc.config?.ENDPOINTS?.PROJECT?.PARENT_PICKER || '/project/parent-picker';
            const pickerParams = { excludeId: currentProjectId };
            const activeSegId = segmentField ? parseInt(segmentField.getValue(), 10) : null;
            if (Number.isInteger(activeSegId) && activeSegId > 0) {
                pickerParams.segmentId = activeSegId;
            }
            parentOptions = await svc.get(endpoint, pickerParams);
            console.log('Parent options (server-filtered):', parentOptions);

            if (Array.isArray(parentOptions) && parentOptions.length > 0) {
                const hasParentId = parentOptions.some(p => p.hasOwnProperty('parentid'));
                if (hasParentId) {
                    console.log('Server response includes parentid - using server-filtered results');
                    useServerFilter = true;
                } else {
                    console.log('Server response missing parentid - falling back to full project list');
                }
            }
        } catch (e) {
            console.warn('getProjectParentOptions failed or not available, falling back to all projects', e);
        }

        if (useServerFilter) {
            allProjects = parentOptions;
        } else {
            // Use UnisionSearch endpoint which includes full project data with parentid
            allProjects = await window.BUDG_API_SERVICE.getUnisionSearchData('project');
            console.log('Using UnisionSearch project data for hierarchy checking');
        }
        console.log('Projects loaded for parent picker:', allProjects);

        console.log('Number of projects loaded:', allProjects.length);

        // Log each project's structure for debugging
        allProjects.forEach((project, index) => {
            console.log(`--- Project ${index} ---`);
            console.log('Project ID:', getProjectId(project));
            console.log('Project name:', project.primaryname || project.PrimaryName || project.name || project.Name);
            console.log('parentid field:', getProjectParentId(project));
            console.log('All object keys:', Object.keys(project));
        });

        // Apply client-side safety filter to exclude current and descendants
        console.log('=== APPLYING CLIENT-SIDE FILTERING LOGIC ===');
        console.log('Current project ID for filtering:', currentProjectId);

        filteredProjects = allProjects.filter(project => {
            const pid = getProjectId(project);
            const parentId = getProjectParentId(project);

            console.log(`\n--- Checking project ${pid} ---`);
            console.log('Project name:', project.primaryname || project.PrimaryName);
            console.log('Project parentid:', parentId);
            console.log('Is current project?', String(pid) === String(currentProjectId));

            if (String(pid) === String(currentProjectId)) {
                console.log('❌ EXCLUDED: Current project');
                return false;
            }

            const isDescendant = isDescendantOf(project, currentProjectId, allProjects);
            console.log('Is descendant?', isDescendant);

            if (isDescendant) {
                console.log('❌ EXCLUDED: Descendant project');
            } else {
                console.log('✅ INCLUDED: Valid parent candidate');
            }

            return !isDescendant;
        });

        console.log('=== FILTERING RESULTS ===');
        console.log('Original projects count:', allProjects.length);
        console.log('Filtered projects count:', filteredProjects.length);
        console.log('Filtered projects:', filteredProjects.map(p => ({
            id: getProjectId(p),
            name: p.primaryname || p.PrimaryName,
            parentid: getProjectParentId(p)
        })));
        console.log('=== PARENT SELECTION DEBUG END ===');

        showParentSelectionModal();
    } catch (error) {
        console.error('Error loading projects for parent selection:', error);
        console.error('Error details:', error);
        alert('Failed to load projects: ' + error.message);
    }
}

function showParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    if (modal) {
        modal.style.display = 'flex';
        renderProjects(filteredProjects);
    }
}

function renderProjects(projects) {
    const container = document.getElementById('projectsList');
    if (!container) return;

    container.innerHTML = '';

    if (projects.length === 0) {
        container.innerHTML = '<div class="no-projects">No projects available</div>';
        return;
    }

    projects.forEach(project => {
        const projectItem = document.createElement('div');
        projectItem.className = 'project-item';

        // Get project name from various possible field names
        const projectName = project.primaryname || project.PrimaryName || project.name || project.Name || 'Unnamed Project';

        const projectNameDiv = document.createElement('div');
        projectNameDiv.className = 'project-name';
        projectNameDiv.textContent = projectName;

        const projectDescription = document.createElement('div');
        projectDescription.className = 'project-description';
        projectDescription.textContent = project.description || project.Description || '';

        projectItem.appendChild(projectNameDiv);
        projectItem.appendChild(projectDescription);

        projectItem.addEventListener('click', () => {
            selectProjectAsParent(project);
        });

        // Add hover effects
        projectItem.addEventListener('mouseenter', () => {
            projectItem.style.backgroundColor = '#f9fafb';
        });

        projectItem.addEventListener('mouseleave', () => {
            projectItem.style.backgroundColor = '';
        });

        container.appendChild(projectItem);
    });
}

function selectProjectAsParent(project) {
    console.log('Selected parent project:', project);
    const parentNameInput = document.getElementById('parentName');
    const projectName = project.primaryname || project.PrimaryName || project.name || project.Name || 'Unnamed Project';
    parentNameInput.value = projectName;
    parentNameInput.dataset.parentId = getProjectId(project);
    console.log('Set parent ID in dataset:', parentNameInput.dataset.parentId);
    closeParentSelectionModal();
    markAsDirty();
}

function closeParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    if (modal) {
        modal.style.display = 'none';
    }
    const searchInput = document.getElementById('parentSearchInput');
    if (searchInput) {
        searchInput.value = '';
    }
}

async function refreshProjectParentForSelectedSegment(previousSegmentId) {
    const currentProjectId = parseId();
    if (!currentProjectId) return;

    const activeSegmentId = segmentField ? parseInt(segmentField.getValue(), 10) : null;
    let candidates = [];
    try {
        const svc = window.BUDG_API_SERVICE;
        const endpoint = svc.config?.ENDPOINTS?.PROJECT?.PARENT_PICKER || '/project/parent-picker';
        const params = { excludeId: currentProjectId };
        if (Number.isInteger(activeSegmentId) && activeSegmentId > 0) {
            params.segmentId = activeSegmentId;
        }
        const parentOptions = await svc.get(endpoint, params);
        candidates = Array.isArray(parentOptions?.data) ? parentOptions.data : (Array.isArray(parentOptions) ? parentOptions : []);
    } catch (_) {
        candidates = [];
    }

    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentNameInput?.dataset?.parentId || '', 10);
    if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
        return;
    }

    const allowedParentIds = new Set(
        candidates
            .map(p => parseInt(getProjectId(p), 10))
            .filter(id => Number.isInteger(id) && id > 0)
    );

    if (!allowedParentIds.has(selectedParentId)) {
        alert('This parent is not valid for the selected segment. Please remove the parent first.');
        return false;
    }
    return true;
}

function filterProjects(searchTerm) {
    const filtered = filteredProjects.filter(project => {
        const name = (project.primaryname || project.PrimaryName || '').toLowerCase();
        const description = (project.description || project.Description || '').toLowerCase();
        const search = searchTerm.toLowerCase();

        return name.includes(search) || description.includes(search);
    });

    renderProjects(filtered);
}


async function initializeCustomFields(projectId) {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Project',
                containerId: 'customFieldsContainer',
                mode: 'edit',
                objectId: projectId
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } else {
            console.warn('CustomFields not available');
        }
    } catch (error) {
        console.error('Error initializing custom fields:', error);
    }
}

async function initializePage() {
    const id = parseId();
    if (!id) return;

    try {
        // Set active tab from URL parameter
        setActiveTabFromURL();

        // Load lookups and project data in parallel
        await Promise.all([
            loadProjectEditLookups(),
            loadProject(id)
        ]);

        // Initialize custom fields
        await initializeCustomFields(id);

        // Initialize segment field
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Project',
                    fieldId: 'projectSegment',
                    errorId: 'projectSegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        return await refreshProjectParentForSelectedSegment(previousSegmentId);
                    }
                });
                console.log('Segment field initialized');
                if (pendingProjectSegmentId != null) {
                    segmentField.setValue(pendingProjectSegmentId);
                }
            } catch (error) {
                console.error('Error initializing segment field:', error);
            }
        }

        // Setup event listeners after data is loaded
        setupEventListeners();

        console.log('Project edit page initialized successfully');
    } catch (error) {
        console.error('Error initializing project edit page:', error);
    }
}

// Set active tab from URL parameter
function setActiveTabFromURL() {
    const activeTabName = getActiveTabFromURL();
    const targetTab = document.querySelector(`[data-tab="${activeTabName}"]`);

    if (targetTab) {
        // Remove active class from all tabs
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        // Add active class to target tab
        targetTab.classList.add('active');

        // Hide all tab content
        document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));

        // Show target tab content
        const targetContent = document.getElementById(activeTabName);
        if (targetContent) {
            targetContent.classList.add('active');
        }

        // Load content for the active tab (same logic as switchTab)
        loadTabContent(activeTabName);

        console.log(`Set active tab to: ${activeTabName}`);
    }
}

// Header ready event
window.addEventListener('headerReady', function() {
    // Header is ready
});

// ===== PROJECT OTHER RELATIONSHIPS =====
let projectOtherRelationships = [];
let projectRelationTypes = [];
let allProjectsForRelationships = [];
let originalProjectOtherRelationships = [];

// Load project other relationships
async function loadProjectOtherRelationships(projectId) {
    const relationshipsList = document.getElementById('projectOtherRelationshipsList');
    if (!relationshipsList) {
        console.error('projectOtherRelationshipsList not found');
        return;
    }
    
    try {
        relationshipsList.innerHTML = '<div class="loading-message">Loading...</div>';
        
        // Load relation types and projects in parallel
        const [relationTypesRes, projectsRes, relationshipsRes] = await Promise.all([
            fetch('/api/project/relation-types', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            }),
            fetch('/api/project', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            }),
            fetch(`/api/project/relationships/${projectId}`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            })
        ]);
        
        if (relationTypesRes.ok) {
            const relationTypesData = await relationTypesRes.json();
            projectRelationTypes = Array.isArray(relationTypesData) ? relationTypesData : [];
        }
        
        if (projectsRes.ok) {
            const projectsData = await projectsRes.json();
            allProjectsForRelationships = Array.isArray(projectsData) ? projectsData.filter(p => (p.id || p.ID) != projectId) : [];
        }
        
        if (relationshipsRes.ok) {
            const relationshipsData = await relationshipsRes.json();
            projectOtherRelationships = Array.isArray(relationshipsData) ? relationshipsData : [];
        } else if (relationshipsRes.status === 404) {
            projectOtherRelationships = [];
        }
        originalProjectOtherRelationships = JSON.parse(JSON.stringify(projectOtherRelationships));
        
        renderProjectOtherRelationships();
        
    } catch (error) {
        console.error('Failed to load project other relationships:', error);
        relationshipsList.innerHTML = '<div class="error-message">Failed to load relationships: ' + error.message + '</div>';
    }
}

// Render project other relationships table
function renderProjectOtherRelationships() {
    const relationshipsList = document.getElementById('projectOtherRelationshipsList');
    if (!relationshipsList) return;
    
    relationshipsList.innerHTML = '';
    
    // Always show at least one empty row
    if (projectOtherRelationships.length === 0) {
        projectOtherRelationships.push({
            id: 'new-empty',
            relationType: null,
            targetProjectId: null,
            description: ''
        });
    }
    
    projectOtherRelationships.forEach((relationship, index) => {
        const rowId = relationship.id || 'new-' + index;
        const isNewRow = !relationship.id || String(relationship.id).startsWith('new-');
        
        // Get target project info
        const targetProject = allProjectsForRelationships.find(p => (p.id || p.ID) == (relationship.targetProjectId || relationship.targetprojectid));
        const projectRef = targetProject ? (targetProject.refnumber || targetProject.refNumber || targetProject.ref || '') : '';
        const projectName = targetProject ? (targetProject.primaryname || targetProject.primaryName || targetProject.name || '') : '';
        
        // Create relationship type options
        const relationTypeOptions = projectRelationTypes.map(rt => {
            const name = rt.primaryname || rt.primaryName || rt.name || 'Unknown Type';
            const selected = (rt.id == (relationship.relationType || relationship.relationtype)) ? 'selected' : '';
            return `<option value="${rt.id}" ${selected}>${escapeHtml(name)}</option>`;
        }).join('');
        
        // Create project options
        const projectOptions = allProjectsForRelationships.map(p => {
            const name = p.primaryname || p.primaryName || p.name || 'Unknown Project';
            const selected = (p.id == (relationship.targetProjectId || relationship.targetprojectid)) ? 'selected' : '';
            return `<option value="${p.id || p.ID}" ${selected} data-ref="${escapeHtml(p.refnumber || p.refNumber || p.ref || '')}">${escapeHtml(name)}</option>`;
        }).join('');
        
        const row = document.createElement('div');
        row.className = 'relationship-item';
        row.setAttribute('data-id', rowId);
        row.innerHTML = `
            <div class="relationship-type">
                <select class="form-select relationship-type-select" data-field="relationType" onchange="updateProjectOtherRelationship(this)">
                    <option value="">Select Relationship Type</option>
                    ${relationTypeOptions}
                </select>
            </div>
            <div class="relationship-project">
                <select class="form-select project-select" data-field="projectId" onchange="updateProjectRefAndRelationship(this)">
                    <option value="">Select Project</option>
                    ${projectOptions}
                </select>
            </div>
            <div class="relationship-ref">
                <span class="ref-display">${escapeHtml(projectRef || '')}</span>
            </div>
            <div class="relationship-description">
                <input type="text" class="form-input description-input" data-field="description" value="${escapeHtml(relationship.description || '')}" placeholder="Enter description" onchange="updateProjectOtherRelationship(this)">
            </div>
            <div class="relationship-actions">
                <button type="button" class="btn btn-sm btn-success" onclick="addProjectOtherRelationshipRow()" title="Add Row">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="btn btn-sm btn-danger" onclick="removeProjectOtherRelationshipRow('${rowId}')" title="Remove Row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        `;
        
        relationshipsList.appendChild(row);
    });
}

// Update project ref when project is selected
function updateProjectRefAndRelationship(selectElement) {
    const row = selectElement.closest('.relationship-item');
    const selectedOption = selectElement.options[selectElement.selectedIndex];
    const refDisplay = row.querySelector('.ref-display');
    const ref = selectedOption.getAttribute('data-ref') || '';
    
    if (refDisplay) {
        refDisplay.textContent = ref;
    }
    
    updateProjectOtherRelationship(selectElement);
}

// Update relationship data
function updateProjectOtherRelationship(element) {
    const row = element.closest('.relationship-item');
    const rowId = row.getAttribute('data-id');
    let index = projectOtherRelationships.findIndex(r => (r.id || 'new-empty') === rowId);
    
    if (index === -1) {
        projectOtherRelationships.push({
            id: rowId,
            relationType: null,
            targetProjectId: null,
            description: ''
        });
        index = projectOtherRelationships.length - 1;
    }
    
    const relationship = projectOtherRelationships[index];
    const field = element.getAttribute('data-field');
    
    if (field === 'relationType') {
        relationship.relationType = element.value ? parseInt(element.value) : null;
    } else if (field === 'projectId') {
        relationship.targetProjectId = element.value ? parseInt(element.value) : null;
    } else if (field === 'description') {
        relationship.description = element.value || '';
    }
    
    markAsDirty();
}

// Add new relationship row
function addProjectOtherRelationshipRow() {
    projectOtherRelationships.push({
        id: 'new-' + Date.now(),
        relationType: null,
        targetProjectId: null,
        description: ''
    });
    renderProjectOtherRelationships();
    markAsDirty();
}

// Remove relationship row
function removeProjectOtherRelationshipRow(rowId) {
    // Convert rowId to string for comparison
    const rowIdStr = String(rowId);
    
    // Find the index using the same logic as renderProjectOtherRelationships
    // In renderProjectOtherRelationships: rowId = relationship.id || 'new-' + index
    const foundIndex = projectOtherRelationships.findIndex((r, idx) => {
        // Generate the same rowId that would be used in rendering
        const generatedRowId = r.id ? String(r.id) : `new-${idx}`;
        return generatedRowId === rowIdStr;
    });
    
    if (foundIndex !== -1) {
        projectOtherRelationships.splice(foundIndex, 1);
        
        // Always ensure at least one empty row exists
        if (projectOtherRelationships.length === 0) {
            projectOtherRelationships.push({
                id: 'new-empty',
                relationType: null,
                targetProjectId: null,
                description: ''
            });
        }
        renderProjectOtherRelationships();
        markAsDirty();
    } else {
        console.warn('Could not find relationship to remove with rowId:', rowId);
    }
}

// Collect project other relationships data for saving
function collectProjectOtherRelationships() {
    const relationshipsList = document.getElementById('projectOtherRelationshipsList');
    if (!relationshipsList) return [];
    
    const relationships = [];
    const allRows = relationshipsList.querySelectorAll('.relationship-item');
    
    allRows.forEach(row => {
        const rowId = row.getAttribute('data-id');
        const relationTypeSelect = row.querySelector('.relationship-type-select');
        const projectSelect = row.querySelector('.project-select');
        const descriptionInput = row.querySelector('.description-input');
        
        const relationType = relationTypeSelect?.value ? parseInt(relationTypeSelect.value) : null;
        const targetProjectId = projectSelect?.value ? parseInt(projectSelect.value) : null;
        const description = descriptionInput?.value || '';
        
        // Only include if both relationType and targetProjectId are set
        if (relationType && targetProjectId) {
            relationships.push({
                id: rowId, // Preserve the ID for update logic
                relationType: relationType,
                targetProjectId: targetProjectId,
                description: description || null
            });
        }
    });
    
    return relationships;
}

// Save project other relationships
async function saveProjectOtherRelationships(projectId) {
    try {
        const relationships = collectProjectOtherRelationships();
        
        // Use the last-loaded snapshot to detect deletions reliably.
        const existingRelationshipIds = new Set();
        originalProjectOtherRelationships.forEach(r => {
            if (r.id && !String(r.id).startsWith('new-')) {
                existingRelationshipIds.add(String(r.id));
            }
        });
        
        // Get current relationship IDs from the form
        const currentRelationshipIds = new Set();
        relationships.forEach(r => {
            if (r.id && !String(r.id).startsWith('new-')) {
                currentRelationshipIds.add(String(r.id));
            }
        });
        
        // Delete relationships that were removed (exist in DB but not in form)
        for (const existingId of existingRelationshipIds) {
            if (!currentRelationshipIds.has(existingId)) {
                try {
                    await fetch(`/api/project/relationship/${existingId}`, {
                        method: 'DELETE',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });
                } catch (e) {
                    console.warn('Failed to delete relationship:', existingId, e);
                }
            }
        }
        
        // Update existing relationships or create new ones
        for (const rel of relationships) {
            const isNew = !rel.id || String(rel.id).startsWith('new-');
            
            if (isNew) {
                // Create new relationship
                await fetch('/api/project/relationship', {
                    method: 'POST',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        sourceProjectId: projectId,
                        targetProjectId: rel.targetProjectId,
                        relationType: rel.relationType,
                        description: rel.description
                    })
                });
            } else {
                // Update existing relationship
                await fetch(`/api/project/relationship/${rel.id}`, {
                    method: 'PUT',
                    credentials: 'include',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        targetProjectId: rel.targetProjectId,
                        relationType: rel.relationType,
                        description: rel.description
                    })
                });
            }
        }

        // Refresh local state with DB IDs so subsequent saves/deletes diff correctly.
        await loadProjectOtherRelationships(projectId);
        
        return { success: true };
    } catch (error) {
        console.error('Error saving project other relationships:', error);
        return { success: false, message: error.message };
    }
}

// Make functions globally available
window.addProjectOtherRelationshipRow = addProjectOtherRelationshipRow;
window.removeProjectOtherRelationshipRow = removeProjectOtherRelationshipRow;
window.updateProjectOtherRelationship = updateProjectOtherRelationship;
window.updateProjectRefAndRelationship = updateProjectRefAndRelationship;
