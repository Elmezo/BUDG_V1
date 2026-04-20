// Policy Edit Page JavaScript
let segmentField = null; // Segment field component reference
let originalPolicySegmentId = null; // segment loaded from server; used to detect segment change

function normalizePolicySegmentId(value) {
    if (value == null || value === '') return null;
    const n = parseInt(value, 10);
    return Number.isInteger(n) ? n : null;
}

document.addEventListener('DOMContentLoaded', async function() {
    console.log('Initializing policy edit page...');

    const id = parseId();
    if (!id) {
        console.error('No policy ID found in URL');
        return;
    }

    // Check edit permission before allowing access
    try {
        const permResp = await fetch('/api/user/permissions/Policy', { method: 'GET', credentials: 'include' });
        if (permResp.ok) {
            const perms = await permResp.json();
            if (perms.success && !perms.canEdit && !perms.isAdmin) {
                alert(window.I18n ? (window.I18n.t('message.noPermissionToEdit') || 'You do not have permission to edit policies.') : 'You do not have permission to edit policies.');
                window.location.href = `/view/policy/policy.html?id=${id}`;
                return;
            }
        }
    } catch (e) {
        console.error('Error checking edit permission:', e);
    }

    // Initialize lock manager
    const lockManager = new window.LockManager('policy', id);
    window.currentLockManager = lockManager;
    
    // Setup auto-release on page unload
    lockManager.setupBeforeUnload();
    
    // Check lock status
    const lockStatus = await lockManager.checkLockStatus();
    const status = lockStatus?.status || 'no_lock';
    
    // Handle lock conflicts
    if (status === 'locked_by_other') {
        const lockedBy = lockStatus.lockedByName || 'another user';
        alert(window.I18n ? window.I18n.t('lock.objectLockedBy', { user: lockedBy }) : `This policy is currently locked by ${lockedBy}. Please try again later.`);
        window.location.href = `/view/policy/policy.html?id=${id}`;
        return;
    } else if (status === 'permanently_locked') {
        const isSuperAdmin = await lockManager.checkIsSuperAdmin();
        if (!isSuperAdmin) {
            const lockedBy = lockStatus.lockedByName || 'an administrator';
            alert(window.I18n ? window.I18n.t('lock.objectPermanentLockBy', { user: lockedBy }) : `This policy has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
            window.location.href = `/view/policy/policy.html?id=${id}`;
            return;
        }
    }
    
    // Acquire lock
    const lockResult = await lockManager.acquireLock(false);
    if (!lockResult || !lockResult.success) {
        // Check if we have details about who locked it
        if (lockResult && lockResult.lockedBy) {
            const lockedBy = lockResult.lockedBy;
            alert(window.I18n ? window.I18n.t('lock.objectLockedTryLater', { user: lockedBy }) : `The object is currently locked by ${lockedBy}. Try again later.`);
        } else {
            alert('Failed to acquire lock. Please try again.');
        }
        window.location.href = `/view/policy/policy.html?id=${id}`;
        return;
    }
    
    // Update global lock count if available
    if (window.globalLockUI) {
        await window.globalLockUI.updateLockCount();
    }

    console.log('Initializing policy edit page for ID:', id);

    // Initialize the page (await so custom fields and lookups finish before interactions)
    await initializePage(id);

});

function parseId() {
    console.log('Parsing ID from URL:', window.location.href);

    // First try URL params (for separate edit pages)
    const urlParams = new URLSearchParams(window.location.search);
    const id = parseInt(urlParams.get('id'), 10);
    if (!Number.isNaN(id)) return id;

    // Fallback to path-based ID (for main view pages)
    const parts = window.location.pathname.split('/').filter(Boolean);
    const idx = parts.indexOf('policy-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

// Show success/error message (same design as other edit pages)
function showSuccessMessage(message, isError = false) {
    // Remove any existing success message
    const existingMessage = document.getElementById('success-message');
    if (existingMessage) {
        existingMessage.remove();
    }
    
    // Create success message element
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
    `;
    successDiv.textContent = message;
    
    // Add CSS animation
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
    
    // Auto remove after 3 seconds
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

function populateSelectSimple(selectId, list, getLabel) {
    const el = document.getElementById(selectId);
    if (!el) return;

    el.innerHTML = '';
    (Array.isArray(list) ? list : []).forEach(item => {
        const op = document.createElement('option');
        op.value = String(item.ID || item.id);
        op.textContent = getLabel ? getLabel(item) : (item.PrimaryName || item.primaryname || item.name || '');
        el.appendChild(op);
    });
}

async function initializePage(policyId) {
    try {
        console.log('Initializing policy edit page...');
        
        // Initialize segment field FIRST so segmentField is ready when populateFormFields runs
        try {
            if (window.SegmentField) {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Policy',
                    fieldId: 'policySegment',
                    errorId: 'policySegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        const sid = parseInt(selectedSegmentId, 10);
                        if (Number.isInteger(sid) && sid > 0) {
                            window.currentImpactSegmentId = sid;
                        }
                        return await refreshPolicyParentForSelectedSegment(previousSegmentId);
                    }
                });
                console.log('Segment field initialized');
            }
        } catch (error) {
            console.error('Error initializing segment field:', error);
        }

        // Load dropdown options first
        await loadPolicyEditLookups();
        
        // Load policy data (segment field is now ready)
        await loadPolicy(policyId);
        
        // Initialize custom fields
        await initializeCustomFields(policyId);
        
        // Set up event listeners
        setupEventListeners(policyId);
        
        // Set up tab switching
        setupTabSwitching();
        
        // Handle URL tab parameter
        handleUrlTabParameter();
        
    } catch (error) {
        console.error('Failed to initialize policy edit page:', error);
    }
}

async function loadPolicyEditLookups() {
    console.log('Loading policy edit lookups...');
    
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
        const [policyTypes, statuses, lifecycleStatuses, viewingList] = await Promise.all([
            window.BUDG_API_SERVICE.getPolicyTypeList(),
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getPolicyLifecycleList(),
            window.BUDG_API_SERVICE.getViewingList()
        ]);

        console.log('Policy edit lookups loaded:', {
            policyTypes: policyTypes?.length || 0,
            statuses: statuses?.length || 0,
            lifecycleStatuses: lifecycleStatuses?.length || 0,
            viewing: viewingList?.length || 0
        });

        // Populate dropdowns
        populateSelectSimple('policyType', policyTypes, x => x.PrimaryName || x.primaryname || x.name);
        populateSelectSimple('status', (Array.isArray(statuses?.data) ? statuses.data : statuses), x => x.name || x.primaryname);
        populateSelectSimple('lifecycleStatus', lifecycleStatuses, x => x.PrimaryName || x.primaryname || x.name);
        populateSelectSimple('isPublic', viewingList, x => x.name || x.primaryname);

        console.log('Policy edit lookups loaded successfully');
    } catch (error) {
        console.error('Failed to load policy edit lookups:', error);
    }
}

async function loadPolicy(policyId) {
    try {
        console.log('Loading policy data for ID:', policyId);
        
        // Load policy data from API
        const response = await window.BUDG_API_SERVICE.getPolicyById(policyId);
        const policy = response?.data || response;
        
        if (!policy) {
            throw new Error('Policy not found');
        }
        
        // Populate form fields
        populateFormFields(policy);
        
        // Update page title
        updatePageTitle(policy);
        
    } catch (error) {
        console.error('Failed to load policy:', error);
        alert(window.I18n ? window.I18n.t('message.failedToLoad') : 'Failed to load policy data');
    }
}

function updatePageTitle(policy) {
    const titleElement = document.getElementById('policyTitle');
    if (titleElement && policy) {
        const policyName = policy.primaryName || policy.name || policy.PrimaryName || 'Unnamed Policy';
        titleElement.textContent = policyName;
        console.log('Updated page title to:', policyName);
    }
}

// Initialize custom fields
async function initializeCustomFields(policyId) {
    try {
        if (window.CustomFields) {
            window.customFieldsContext = await window.CustomFields.initForm({
                facetId: 'Policy',
                containerId: 'customFieldsContainer',
                mode: 'edit',
                objectId: policyId
            });
            console.log('Custom fields initialized:', window.customFieldsContext);
        } else {
            console.warn('CustomFields not available');
        }
    } catch (error) {
        console.error('Error initializing custom fields:', error);
    }
}

function populateFormFields(policy) {
    // Populate basic fields
    const loadedSegmentId = policy.segmentId ?? policy.segment_id ?? policy.Segment_ID;
    window.currentImpactSegmentId = loadedSegmentId || 1;
    const fields = [
        { id: 'name', value: policy.primaryName || policy.name },
        { id: 'description', value: policy.description },
        { id: 'ref', value: policy.refNumber || policy.ref },
        { id: 'policyType', value: policy.policyType },
        { id: 'status', value: policy.status },
        { id: 'lifecycleStatus', value: policy.lifecycleStatus },
        { id: 'isPublic', value: policy.isPublic },
        { id: 'policyUrl', value: policy.policyUrl || policy.url || policy.URL }
    ];
    
    fields.forEach(field => {
        const element = document.getElementById(field.id);
        if (element && field.value !== undefined && field.value !== null) {
            element.value = field.value;
        }
    });
    
    // Populate date fields (Effective/End)
    const effectiveDateInput = document.getElementById('effectiveDate');
    const endDateInput = document.getElementById('endDate');
    if (effectiveDateInput) {
        const eff = policy.effectiveDate || policy.effective_date || policy.EffectiveDate || policy.Effective_Date;
        if (eff) {
            const d = new Date(eff);
            if (!isNaN(d.getTime())) {
                effectiveDateInput.value = d.toISOString().slice(0, 10);
            }
        }
    }
    if (endDateInput) {
        const end = policy.endDate || policy.end_date || policy.EndDate || policy.End_Date;
        if (end) {
            const d = new Date(end);
            if (!isNaN(d.getTime())) {
                endDateInput.value = d.toISOString().slice(0, 10);
            }
        }
    }
    
    // Populate parent fields
    const parentIdInput = document.getElementById('parentId');
    const parentNameInput = document.getElementById('parentName');
    if (parentIdInput && policy.parentId) {
        parentIdInput.value = policy.parentId;
    }
    if (parentNameInput && policy.parentName) {
        parentNameInput.value = policy.parentName;
    }
    
    // Populate checkbox field - isInternal
    const isInternalCheckbox = document.getElementById('isInternal');
    if (isInternalCheckbox) {
        // Handle different possible field names (API returns 'internal' in lowercase)
        const internalValue = policy.internal !== undefined ? policy.internal : 
                             (policy.Internal !== undefined ? policy.Internal : 
                             (policy.isInternal !== undefined ? policy.isInternal : 
                             (policy.IsInternal !== undefined ? policy.IsInternal : 0)));
        
        // Handle both boolean and numeric values (0/1 from tinyint)
        isInternalCheckbox.checked = internalValue === true || internalValue === 1 || internalValue === '1';
        console.log('Populated isInternal checkbox:', isInternalCheckbox.checked, 'from API value:', internalValue);
    }
    
    // Set segment value if available
    if (segmentField && loadedSegmentId != null && loadedSegmentId !== '') {
        segmentField.setValue(loadedSegmentId);
    }
    // Baseline must match what collectPolicyFormData submits. When the server has no segment, the
    // dropdown still has a value (browser selects the first option), so using only loadedSegmentId
    // made policySegmentChanged true on every save and spuriously ran full Impact save.
    if (segmentField && typeof segmentField.getValue === 'function') {
        originalPolicySegmentId = normalizePolicySegmentId(segmentField.getValue());
    } else {
        originalPolicySegmentId = normalizePolicySegmentId(loadedSegmentId);
    }
}


function setupEventListeners(policyId) {
    // Save button
    const saveBtn = document.getElementById('saveBtn');
    if (saveBtn) {
        saveBtn.addEventListener('click', () => savePolicy(policyId, false));
    }
    
    // Save and close button
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    if (saveCloseBtn) {
        saveCloseBtn.addEventListener('click', () => savePolicy(policyId, true));
    }
    
    // Cancel button
    const cancelBtn = document.getElementById('cancelBtn');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', async () => {
            // Release lock before canceling
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }
            window.location.href = `/view/policy/policy.html?id=${policyId}`;
        });
    }
    
    // Show editor button – advanced rich text editor
    const showEditorBtn = document.getElementById('showEditorBtn');
    if (showEditorBtn) {
        showEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('description', showEditorBtn);
        });
    }

    // Update title when name field changes
    const nameField = document.getElementById('name');
    if (nameField) {
        nameField.addEventListener('input', (e) => {
            const titleElement = document.getElementById('policyTitle');
            if (titleElement) {
                const newName = e.target.value.trim() || 'Unnamed Policy';
                titleElement.textContent = newName;
            }
        });
    }
    
    // Parent selection button
    const selectParentBtn = document.getElementById('selectParentBtn');
    if (selectParentBtn) {
        selectParentBtn.addEventListener('click', async () => {
            try {
                // Load policies for selection
                const policies = await window.BUDG_API_SERVICE.getPolicyList();
                const policyList = Array.isArray(policies?.data) ? policies.data : (Array.isArray(policies) ? policies : []);
                
                // Helper function to get policy name from various field name variations
                function getPolicyName(policy) {
                    return policy.PrimaryName || policy.primaryName || policy.name || policy.Name || policy.Primaryname || policy.primaryname || 'Unnamed';
                }
                
                // Helper function to get policy ID from various field name variations
                function getPolicyId(policy) {
                    return policy.id || policy.ID || policy.Id || policy.ID || null;
                }
                
                // Helper function to get ref number from various field name variations
                function getRefNumber(policy) {
                    return policy.refNumber || policy.RefNumber || policy.ref || policy.Ref || policy.refnumber || '';
                }
                
                // Helper function for escaping HTML
                function escapeHtml(text) {
                    if (!text) return '';
                    const div = document.createElement('div');
                    div.textContent = text;
                    return div.innerHTML;
                }
                
                console.log('Policy list loaded:', policyList.length, 'policies');
                console.log('Sample policy:', policyList[0]);
                
                // Create a simple selection modal
                const modal = document.createElement('div');
                modal.style.cssText = 'position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;';
                modal.innerHTML = `
                    <div style="background: white; padding: 2rem; border-radius: 8px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto;">
                        <h3 style="margin-top: 0;">Select Parent Policy</h3>
                        <div style="margin-bottom: 1rem;">
                            <input type="text" id="parentSearch" placeholder="Search policies..." style="width: 100%; padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px;">
                        </div>
                        <div id="parentList" style="max-height: 400px; overflow-y: auto;">
                            ${policyList.map(p => {
                                const policyName = getPolicyName(p);
                                const policyId = getPolicyId(p);
                                const refNum = getRefNumber(p);
                                return `
                                <div class="parent-option" data-id="${policyId}" data-name="${escapeHtml(policyName).replace(/"/g, '&quot;')}" style="padding: 0.75rem; border: 1px solid #ddd; margin-bottom: 0.5rem; border-radius: 4px; cursor: pointer; transition: background 0.2s;" onmouseover="this.style.background='#f0f0f0'" onmouseout="this.style.background='white'">
                                    <strong>${escapeHtml(policyName)}</strong>
                                    ${refNum ? `<div style="font-size: 0.875rem; color: #666;">Ref: ${escapeHtml(refNum)}</div>` : ''}
                                </div>
                            `;
                            }).join('')}
                        </div>
                        <div style="margin-top: 1rem; text-align: right;">
                            <button id="cancelParentSelect" style="padding: 0.5rem 1rem; margin-right: 0.5rem; background: #ccc; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                        </div>
                    </div>
                `;
                document.body.appendChild(modal);
                
                // Search functionality
                const searchInput = modal.querySelector('#parentSearch');
                const parentList = modal.querySelector('#parentList');
                const options = parentList.querySelectorAll('.parent-option');
                
                searchInput.addEventListener('input', (e) => {
                    const searchTerm = e.target.value.toLowerCase();
                    options.forEach(opt => {
                        const name = opt.getAttribute('data-name').toLowerCase();
                        opt.style.display = name.includes(searchTerm) ? 'block' : 'none';
                    });
                });
                
                // Selection functionality
                options.forEach(opt => {
                    opt.addEventListener('click', () => {
                        const parentId = opt.getAttribute('data-id');
                        const parentName = opt.getAttribute('data-name');
                        document.getElementById('parentId').value = parentId;
                        document.getElementById('parentName').value = parentName;
                        document.body.removeChild(modal);
                    });
                });
                
                // Cancel button
                modal.querySelector('#cancelParentSelect').addEventListener('click', () => {
                    document.body.removeChild(modal);
                });
            } catch (error) {
                console.error('Error loading parent policies:', error);
                alert(window.I18n ? window.I18n.t('message.failedToLoad') : 'Failed to load parent policies');
            }
        });
    }
    
    // Clear parent button
    const clearParentBtn = document.getElementById('clearParentBtn');
    if (clearParentBtn) {
        clearParentBtn.addEventListener('click', () => {
            document.getElementById('parentId').value = '';
            document.getElementById('parentName').value = '';
        });
    }
    
    // Components edit button
    const componentsEditBtn = document.getElementById('componentsEditBtn');
    if (componentsEditBtn) {
        componentsEditBtn.addEventListener('click', function() {
            console.log('Components edit button clicked');
            // For now, just reload the components data
            loadComponentsData();
        });
    }
    
        // Show add relationship row is now handled in loadPolicyComponentsRelationships
}

async function refreshPolicyParentForSelectedSegment(previousSegmentId) {
    const parentIdInput = document.getElementById('parentId');
    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentIdInput?.value || '', 10);

    const policies = await window.BUDG_API_SERVICE.getPolicyList();
    const policyList = Array.isArray(policies?.data) ? policies.data : (Array.isArray(policies) ? policies : []);
    if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
        return;
    }

    const allowedParentIds = new Set(
        policyList
            .map(p => parseInt(p.id || p.ID || p.Id, 10))
            .filter(id => Number.isInteger(id) && id > 0)
    );

    if (!allowedParentIds.has(selectedParentId)) {
        alert('This parent is not valid for the selected segment. Please remove the parent first.');
        return false;
    }
    return true;
}

function getCurrentActiveTab() {
    const activeTab = document.querySelector('.tab-container .tab.active');
    return activeTab ? activeTab.getAttribute('data-tab') : 'summary';
}

async function savePolicy(policyId, closeAfter = false) {
    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    const restoreButtons = () => {
        buttons.forEach(btn => {
            if (btn) {
                btn.disabled = false;
                btn.textContent = btn.id === 'saveBtn' ? (window.I18n ? window.I18n.t('button.save') : 'Save') : (window.I18n ? window.I18n.t('button.saveAndClose') : 'Save & Close');
            }
        });
    };

    buttons.forEach(btn => {
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Saving...';
        }
    });

    const activeTab = getCurrentActiveTab();
    console.log(`=== SAVING POLICY (active tab: ${activeTab}) ===`);

    try {
        if (activeTab === 'components') {
            // Save relationships (components tab)
            try {
                await savePolicyRelationships();
                console.log('✅ Relationships saved successfully');
            } catch (relationshipsError) {
                console.error('Error saving relationships:', relationshipsError);
                throw relationshipsError;
            }

        } else if (activeTab === 'stakeholders') {
            if (typeof window.PolicyStakeholderEdit !== 'undefined' && typeof window.PolicyStakeholderEdit.saveStakeholders === 'function') {
                try {
                    await window.PolicyStakeholderEdit.saveStakeholders();
                    console.log('✅ Stakeholders saved successfully');
                } catch (stakeholderError) {
                    console.error('Error saving stakeholders:', stakeholderError);
                    throw stakeholderError;
                }
            } else {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }

        } else if (activeTab === 'impact') {
            const impactLoaded = typeof window.isPolicyImpactDataLoaded === 'function' && window.isPolicyImpactDataLoaded();
            const policyImpactDirty = impactLoaded && typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();

            if (!policyImpactDirty) {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }

            try {
                const impactResult = await window.saveAllImpactData(policyId);
                if (impactResult && typeof impactResult === 'object' && impactResult.success === true) {
                    console.log('✅ Impact saved successfully');
                } else if (impactResult && typeof impactResult === 'object') {
                    throw new Error(impactResult.message || 'Impact save failed');
                }
            } catch (impactError) {
                console.error('Error saving impact:', impactError);
                throw impactError;
            }

        } else if (activeTab === 'workflow') {
            if (typeof saveWorkflowData === 'function') {
                try {
                    await saveWorkflowData(policyId);
                    console.log('✅ Workflow saved successfully');
                } catch (workflowError) {
                    console.error('Error saving workflow:', workflowError);
                    throw workflowError;
                }
            } else {
                showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
                restoreButtons();
                return;
            }

        } else {
            // summary tab (default)
            // Sync rich-text editor content back to textarea before reading
            if (typeof syncAdvancedRichTextToTextarea === 'function') {
                syncAdvancedRichTextToTextarea('description');
            }
            const policyData = collectPolicyFormData();

            // Validate form
            if (!validatePolicyForm(policyData)) {
                restoreButtons();
                return;
            }

            if (segmentField && !segmentField.validate()) {
                restoreButtons();
                return;
            }

            // Update policy
            const response = await window.BUDG_API_SERVICE.updatePolicy(policyId, policyData);

            if (response && response.success === false) {
                if (response.locked) {
                    const lockedBy = response.lockedBy || 'another user';
                    const isPermanent = response.isPermanent || false;
                    const message = `This policy is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`;
                    showSuccessMessage(message, true);
                    if (window.currentLockManager) {
                        await window.currentLockManager.releaseLock();
                    }
                    setTimeout(() => {
                        window.location.href = `/view/policy/policy.html?id=${policyId}`;
                    }, 3000);
                    restoreButtons();
                    return false;
                }
                throw new Error(response?.message || 'Failed to save policy');
            }

            if (!response || !response.success) {
                throw new Error(response?.message || 'Failed to save policy');
            }

            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(policyId);
                    console.log('✅ Custom fields saved successfully');
                } catch (customFieldsError) {
                    console.error('Error saving custom fields:', customFieldsError);
                }
            }

            // If segment changed, reload impact data (do not save impact from summary)
            const currentPolicySegment = normalizePolicySegmentId(policyData.segmentId ?? (segmentField && segmentField.getValue()));
            const policySegmentChanged = currentPolicySegment !== normalizePolicySegmentId(originalPolicySegmentId);

            if (policySegmentChanged && typeof window.initImpactEdit === 'function') {
                console.log('=== Segment changed; reloading policy impact from server ===');
                await window.initImpactEdit(policyId);
            }

            originalPolicySegmentId = normalizePolicySegmentId(policyData.segmentId ?? (segmentField && segmentField.getValue()));
        }

        // Release lock after successful save
        if (window.currentLockManager) {
            await window.currentLockManager.releaseLock();
        }

        showSuccessMessage(window.I18n ? window.I18n.t('message.updatesSaved') : 'UPDATES SAVED', false);

        if (closeAfter) {
            setTimeout(() => {
                window.location.href = `/view/policy/policy.html?id=${policyId}`;
            }, 1500);
        }

    } catch (error) {
        console.error('Error saving policy:', error);
        const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'Unknown error';
        showSuccessMessage(errorMsg, true);
    } finally {
        restoreButtons();
    }
}

function collectPolicyFormData() {
    // Collect checkbox value for isInternal
    const isInternalCheckbox = document.getElementById('isInternal');
    const isInternal = isInternalCheckbox ? isInternalCheckbox.checked : false;
    
    // Convert boolean to tinyint (0 or 1) for database
    const internalValue = isInternal ? 1 : 0;
    
    console.log('Collecting form data - isInternal checkbox checked:', isInternal, '-> DB value:', internalValue);
    
    // Get parentId - convert empty string to null
    const parentIdInput = document.getElementById('parentId');
    const parentIdValue = parentIdInput?.value || '';
    const parentId = parentIdValue && parentIdValue.trim() !== '' ? parseInt(parentIdValue, 10) : null;
    
    // Dates
    const effectiveDate = document.getElementById('effectiveDate')?.value || null;
    const endDate = document.getElementById('endDate')?.value || null;
    
    return {
        primaryName: document.getElementById('name')?.value || '',
        description: document.getElementById('description')?.value || '',
        refNumber: document.getElementById('ref')?.value || '',
        policyType: document.getElementById('policyType')?.value || '',
        status: document.getElementById('status')?.value || '',
        lifecycleStatus: document.getElementById('lifecycleStatus')?.value || '',
        isPublic: document.getElementById('isPublic')?.value || '',
        url: document.getElementById('policyUrl')?.value || '',  // Send as 'url' to match API
        internal: internalValue,  // Send as 'internal' (lowercase) with tinyint value (0 or 1)
        parentId: parentId,  // Include parentId
        effectiveDate: effectiveDate || null,
        endDate: endDate || null,
        segmentId: segmentField ? segmentField.getValue() : 1
    };
}

function validatePolicyForm(data) {
    let isValid = true;
    
    // Clear previous errors
    document.querySelectorAll('.field-error').forEach(error => {
        error.style.display = 'none';
        error.textContent = '';
    });
    
    // Validate required fields
    if (!data.primaryName || !data.primaryName.trim()) {
        showFieldError('nameError', 'Name is required');
        isValid = false;
    }
    
    if (!data.description || !data.description.trim()) {
        showFieldError('descriptionError', 'Description is required');
        isValid = false;
    }
    
    if (!data.policyType) {
        showFieldError('typeError', 'Type is required');
        isValid = false;
    }
    
    if (!data.status) {
        showFieldError('statusError', 'Status is required');
        isValid = false;
    }
    
    if (!data.lifecycleStatus) {
        showFieldError('lifecycleError', 'Lifecycle is required');
        isValid = false;
    }
    
    if (!data.isPublic) {
        showFieldError('viewingError', 'Viewing is required');
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

// Tab switching functionality
function setupTabSwitching() {
    console.log('Setting up tab switching...');
    
    const tabs = document.querySelectorAll('.tab-container .tab');
    console.log('Found tabs:', tabs.length);
    
    tabs.forEach(function(tab) {
        tab.addEventListener('click', function() {
            console.log('Tab clicked:', this.textContent.trim());
            
            // Remove active class from all tabs
            tabs.forEach(function(t) { t.classList.remove('active'); });
            this.classList.add('active');
            
            const tabName = this.getAttribute('data-tab');
            console.log('Switching to tab:', tabName);
            
            // Hide all tab contents
            const tabContents = document.querySelectorAll('.tab-content');
            tabContents.forEach(function(content) {
                content.classList.remove('active');
                content.style.display = 'none';
            });
            
            // Show selected tab content
            const targetTab = document.getElementById(tabName + 'Tab');
            if (targetTab) {
                targetTab.classList.add('active');
                targetTab.style.display = 'block';
                
            // Load data for specific tabs
            if (tabName === 'components') {
                console.log('Loading components data and setting up sub-tabs');
                loadComponentsData();
                setupComponentsSubTabs();
            } else if (tabName === 'stakeholders') {
                console.log('Loading stakeholders data');
                const policyId = parseId();
                if (policyId && typeof window.PolicyStakeholderEdit !== 'undefined' && typeof window.PolicyStakeholderEdit.init === 'function') {
                    window.PolicyStakeholderEdit.init(policyId);
                } else {
                    console.warn('PolicyStakeholderEdit not available');
                }
            } else if (tabName === 'impact') {
                console.log('Loading Impact data and setting up sub-tabs');
                const policyId = parseId();
                if (policyId && typeof window.initImpactEdit === 'function') {
                    if (segmentField && typeof segmentField.getValue === 'function') {
                        const sv = parseInt(segmentField.getValue(), 10);
                        if (Number.isInteger(sv) && sv > 0) {
                            window.currentImpactSegmentId = sv;
                        }
                    }
                    void window.initImpactEdit(policyId);
                }
            }
            } else {
                console.error('Tab content not found for:', tabName);
            }
        });
    });
}

// Load components data
async function loadComponentsData() {
    console.log('Loading components data...');
    
    const policyId = parseId();
    if (!policyId) {
        console.error('No policy ID found for components data');
        return;
    }
    
    try {
        // Load hierarchy data
        await loadPolicyComponentsHierarchy(policyId);
        
        // Load relationships data
        await loadPolicyComponentsRelationships(policyId);
        
    } catch (error) {
        console.error('Failed to load components data:', error);
    }
}

// Load policy components hierarchy data - using same logic as glossary
async function loadPolicyComponentsHierarchy(policyId) {
    console.log('[Policy Hierarchy Edit] Loading policy hierarchy for ID:', policyId);
    const tbody = document.getElementById('componentsHierarchyTbody');
    const footer = document.getElementById('componentsHierarchyFooter');
    
    if (!tbody || !footer) {
        console.log('[Policy Hierarchy Edit] Missing elements: tbody or footer');
        return;
    }
    
    try {
        // Show loading state
        tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + (window.I18n ? window.I18n.t('message.loading') : 'Loading policy hierarchy...') + '</td></tr>';
        footer.textContent = 'Loading...';
        
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        // Fetch hierarchy data from API (includes ancestors, current, siblings, children, and siblings' children)
        console.log('[Policy Hierarchy Edit] Fetching hierarchy for policy ID:', policyId);
        const hierarchyData = await api.getPolicyHierarchy(policyId);
        console.log('[Policy Hierarchy Edit] Raw API response:', hierarchyData);
        
        // Handle different response formats
        let items = [];
        if (Array.isArray(hierarchyData)) {
            items = hierarchyData;
        } else if (hierarchyData && hierarchyData.data && Array.isArray(hierarchyData.data)) {
            items = hierarchyData.data;
        } else if (hierarchyData && Array.isArray(hierarchyData.items)) {
            items = hierarchyData.items;
        } else if (hierarchyData && typeof hierarchyData === 'object') {
            // Try to extract array from object
            const keys = Object.keys(hierarchyData);
            for (const key of keys) {
                if (Array.isArray(hierarchyData[key])) {
                    items = hierarchyData[key];
                    break;
                }
            }
        }
        
        console.log('[Policy Hierarchy Edit] Processed items:', items);
        console.log('[Policy Hierarchy Edit] Items count:', items.length);
        
        if (items.length === 0) {
            console.warn('[Policy Hierarchy Edit] No hierarchy data found for policy ID:', policyId);
            tbody.innerHTML = '<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data found</td></tr>';
            footer.textContent = '0 records';
            return;
        }

        // Build hierarchy tree structure from flat data with levels
        // Organize by relation type: ancestors (negative levels), current (level 0), siblings (level 0), descendants (positive levels), sibling_children (positive levels)
        const byId = new Map();
        const childrenMap = new Map();
        
        items.forEach(item => {
            const id = item.id;
            const parentId = item.parentId;
            byId.set(id, item);
            if (!childrenMap.has(parentId)) childrenMap.set(parentId, []);
            childrenMap.get(parentId).push(item);
        });
        
        // Find current policy to determine its parent and calculate base depth
        const currentPolicy = items.find(item => item.relation === 'current');
        const currentParentId = currentPolicy ? currentPolicy.parentId : null;
        
        // Find the maximum ancestor level (most negative) to calculate base depth
        const ancestors = items.filter(item => item.relation === 'ancestor');
        const maxAncestorLevel = ancestors.length > 0 ? Math.min(...ancestors.map(a => a.level)) : 0;
        const ancestorDepthOffset = Math.abs(maxAncestorLevel); // How many ancestor levels we have
        
        // Calculate current depth: if has ancestors, depth = ancestorDepthOffset, else 0
        const currentDepth = currentParentId ? ancestorDepthOffset : 0;
        
        // Build tree structure: ancestors -> siblings -> current -> descendants -> siblings' children
        const hierarchyRows = [];
        
        // Helper to calculate depth based on level and relation
        function calculateDepth(item) {
            if (item.relation === 'ancestor') {
                // Ancestors: convert negative level to positive depth (most negative = depth 0)
                return ancestorDepthOffset + item.level; // -1 becomes (offset-1), -2 becomes (offset-2), etc.
            } else if (item.relation === 'current') {
                return currentDepth;
            } else if (item.relation === 'sibling') {
                return currentDepth; // Same depth as current
            } else if (item.relation === 'descendant') {
                // Descendants: current depth + level (1, 2, 3...)
                return currentDepth + item.level;
            } else if (item.relation === 'sibling_child') {
                // Sibling children: sibling depth + level (1, 2, 3...)
                return currentDepth + item.level;
            }
            return 0;
        }
        
        // Build proper hierarchical tree structure using parent-child relationships
        // This ensures children appear directly under their parents, not just sorted by level
        function buildHierarchicalOrder(items, currentPolicyId) {
            const result = [];
            const processed = new Set();
            
            // Helper function to recursively add node and its children
            function addNodeAndChildren(nodeId, depth) {
                if (processed.has(nodeId)) return;
                processed.add(nodeId);
                
                const node = byId.get(nodeId);
                if (!node) return;
                
                // Add current node
                result.push({ node, depth, parentId: node.parentId });
                
                // Get and sort children by name
                const children = (childrenMap.get(nodeId) || []).sort((a, b) => {
                    return (a.name || a.displayName || '').localeCompare(b.name || b.displayName || '');
                });
                
                // Recursively add children
                children.forEach(child => {
                    addNodeAndChildren(child.id, depth + 1);
                });
            }
            
            // First, add ancestors in order (from root to current's parent)
            const ancestors = items.filter(item => item.relation === 'ancestor')
                .sort((a, b) => a.level - b.level); // Most negative first (root ancestor)
            ancestors.forEach(ancestor => {
                if (!processed.has(ancestor.id)) {
                    addNodeAndChildren(ancestor.id, calculateDepth(ancestor));
                }
            });
            
            // Then add siblings (before current)
            const siblings = items.filter(item => item.relation === 'sibling')
                .sort((a, b) => (a.name || a.displayName || '').localeCompare(b.name || b.displayName || ''));
            siblings.forEach(sibling => {
                if (!processed.has(sibling.id)) {
                    addNodeAndChildren(sibling.id, calculateDepth(sibling));
                }
            });
            
            // Then add current
            if (currentPolicy) {
                addNodeAndChildren(currentPolicy.id, calculateDepth(currentPolicy));
            }
            
            // Then add descendants (children of current) - they will be added recursively
            const currentId = currentPolicy ? currentPolicy.id : currentPolicyId;
            const descendants = items.filter(item => item.relation === 'descendant')
                .filter(item => item.parentId === currentId); // Only direct children
            descendants.forEach(descendant => {
                if (!processed.has(descendant.id)) {
                    addNodeAndChildren(descendant.id, calculateDepth(descendant));
                }
            });
            
            // Finally, add sibling children (children of siblings) - they will be added recursively
            const siblingChildren = items.filter(item => item.relation === 'sibling_child');
            const siblingIds = siblings.map(s => s.id);
            siblingChildren
                .filter(item => siblingIds.includes(item.parentId)) // Only direct children of siblings
                .forEach(child => {
                    if (!processed.has(child.id)) {
                        addNodeAndChildren(child.id, calculateDepth(child));
                    }
                });
            
            return result;
        }
        
        // Build hierarchy rows using proper tree structure
        const hierarchyRowsData = buildHierarchicalOrder(items, policyId);
        
        hierarchyRowsData.forEach(({ node, depth, parentId }) => {
            const children = childrenMap.get(node.id) || [];
            const hasChildren = children.length > 0;
            
            hierarchyRows.push({
                node: node,
                depth: depth,
                parentId: parentId,
                hasChildren: hasChildren,
                relation: node.relation
            });
        });
        
        console.log('[Policy Hierarchy Edit] Processed hierarchy rows:', hierarchyRows);
        
        // Render the hierarchy
        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.map(({ node, depth, hasChildren, relation }) => {
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const fallbackName = node.name || node.displayName || '';
            const fallbackDesc = node.description || '';
            const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
            const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
            const isCurrent = relation === 'current';
            const id = node.id;
            const parentId = node.parentId || null;

            // Calculate visual depth (indentation)
            const visualDepth = depth;
            const indent = Array(visualDepth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const childCount = childrenMap.get(id)?.length || 0;
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'policy-link current-policy-link' : 'policy-link';
            const link = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/policy/${encodeURIComponent(id)}">${escapeHtml(name)}</a>`;
            const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId || ''}" data-depth="${visualDepth}" data-relation="${relation}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td><div class="tree-cell">${indent}${expander}${visualDepth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-file-alt item-icon"></i><span class="policy-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc || '-')}</span></td>
            </tr>`;
        }).join('');

        tbody.innerHTML = rowsHtml;
        footer.textContent = `${hierarchyRows.length} record${hierarchyRows.length !== 1 ? 's' : ''}`;
        
        // Build parent map for interactions
        const parentMap = new Map();
        items.forEach(item => {
            if (item.parentId) {
                if (!parentMap.has(item.parentId)) {
                    parentMap.set(item.parentId, []);
                }
                parentMap.get(item.parentId).push(item.id);
            }
        });
        
        const hierarchyRowsObj = {
            rows: hierarchyRows,
            parentMap: parentMap
        };
        
        // Initialize interactions (same as glossary)
        initPolicyHierarchyInteractionsEdit(tbody.closest('.relationships-hierarchy'), hierarchyRowsObj);
    } catch (error) {
        console.error('[Policy Hierarchy Edit] Failed to load hierarchy:', error);
        console.error('[Policy Hierarchy Edit] Error stack:', error.stack);
        tbody.innerHTML = `<tr><td colspan="2" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${error.message || 'Unknown error'}<br><small>Check console for details</small></td></tr>`;
        footer.textContent = '0 records';
    }
}

// Initialize policy hierarchy interactions for edit page (same as glossary)
function initPolicyHierarchyInteractionsEdit(containerEl, hierarchyRows) {
    const collapsed = new Set();
    const parentMap = hierarchyRows.parentMap;
    const tbody = containerEl ? containerEl.querySelector('tbody') : document.getElementById('componentsHierarchyTbody');
    if (!tbody) return;
    
    // Build a map of parent ID to child rows for faster lookup
    const childrenByParent = new Map();
    tbody.querySelectorAll('tr').forEach(tr => {
        const parentId = tr.getAttribute('data-parent-id');
        if (parentId) {
            if (!childrenByParent.has(parentId)) {
                childrenByParent.set(parentId, []);
            }
            childrenByParent.get(parentId).push(tr);
        }
    });
    
    function toggleChildren(parentId, isCollapsed) {
        const children = childrenByParent.get(String(parentId)) || [];
        children.forEach(childTr => {
            const childId = childTr.getAttribute('data-id');
            const childParentId = childTr.getAttribute('data-parent-id');
            
            if (isCollapsed) {
                // Hide this child and all its descendants
                childTr.style.display = 'none';
                childTr.classList.add('collapsed');
                // Recursively hide all descendants
                toggleChildren(childId, true);
            } else {
                // Show this child
                childTr.style.display = '';
                childTr.classList.remove('collapsed');
                // Recursively show children if parent is not collapsed
                if (!collapsed.has(String(childId))) {
                    toggleChildren(childId, false);
                }
            }
        });
    }
    
    function updateVisibility() {
        // Update all rows based on collapsed state
        tbody.querySelectorAll('tr').forEach(tr => {
            const id = tr.getAttribute('data-id');
            const parentId = tr.getAttribute('data-parent-id');
            
            // Check if any ancestor is collapsed
            let shouldHide = false;
            let currentParentId = parentId;
            const visited = new Set();
            
            while (currentParentId && !shouldHide && !visited.has(currentParentId)) {
                visited.add(currentParentId);
                if (collapsed.has(String(currentParentId))) {
                    shouldHide = true;
                    break;
                }
                // Find the parent row to get its parent ID
                const parentRow = Array.from(tbody.querySelectorAll('tr')).find(r => 
                    r.getAttribute('data-id') === currentParentId
                );
                if (parentRow) {
                    currentParentId = parentRow.getAttribute('data-parent-id');
                } else {
                    break;
                }
            }
            
            if (shouldHide) {
                tr.style.display = 'none';
                tr.classList.add('collapsed');
                } else {
                tr.style.display = '';
                tr.classList.remove('collapsed');
            }
        });
        
        // Update expander button states
        tbody.querySelectorAll('tr').forEach(tr => {
            const id = tr.getAttribute('data-id');
            const btn = tr.querySelector('.tree-expander');
            if (btn) {
                if (collapsed.has(String(id))) {
                    btn.classList.add('collapsed');
                    } else {
                    btn.classList.remove('collapsed');
                }
            }
        });
    }
    
    tbody.addEventListener('click', (e) => {
        const button = e.target.closest('.tree-expander');
        if (button) {
            const tr = button.closest('tr');
            const id = tr.getAttribute('data-id');
            
            // Toggle collapsed state
            const wasCollapsed = collapsed.has(String(id));
            if (wasCollapsed) {
                collapsed.delete(String(id));
            } else {
                collapsed.add(String(id));
            }
            
            // Immediately update visibility
            updateVisibility();
        }
    });
    
    // Initial visibility update
    updateVisibility();
}

// Load policy components relationships data
async function loadPolicyComponentsRelationships(policyId) {
    console.log('Loading policy components relationships for ID:', policyId);
    const relationshipsList = document.getElementById('componentsRelationshipsList');
    
    console.log('Found relationships list:', !!relationshipsList);
    
    if (!relationshipsList) {
        console.error('componentsRelationshipsList not found');
        return;
    }
    
    try {
        // Check if API service is available
        if (!window.BUDG_API_SERVICE) {
            throw new Error('API service not available');
        }
        
        console.log('API service available, fetching relationships...');
        
        // Fetch relationships where this policy is the SOURCE
        const relationships = await window.BUDG_API_SERVICE.getPolicyRelationshipsBySourceId(policyId);
        
        console.log('Relationships received:', relationships);
        console.log('Relationships length:', relationships.length);
        if (relationships.length > 0) {
            console.log('First relationship data:', relationships[0]);
            console.log('First relationship keys:', Object.keys(relationships[0]));
        }
        
        // Clear the container first
        relationshipsList.innerHTML = '';
        
        // If there are existing relationships, show them as editable rows
        if (Array.isArray(relationships) && relationships.length > 0) {
            console.log('Rendering existing relationships with', relationships.length, 'items');
            
            // Load relationship types and policies first
            const [relationTypes, policies] = await Promise.all([
                loadRelationshipTypes(),
                loadPolicies()
            ]);
            
            console.log('Loaded relation types for existing rows:', relationTypes);
            console.log('Loaded policies for existing rows:', policies);
            
            const existingRows = relationships.map(function(rel) {
                console.log('Processing relationship:', rel);
                
                // Get all possible field names for debugging
                const allKeys = Object.keys(rel);
                console.log('Available keys in relationship:', allKeys);
                
                // Try to find the correct field names
                const relationshipType = rel.relationshipType || rel.relationType || rel.type || rel.relationship_type || '';
                const relationshipTypeName = rel.relationshipTypeName || rel.relationTypeName || rel.typeName || rel.relationship_type_name || 'Unknown Type';
                const targetPolicyId = rel.targetPolicyId || rel.targetPolicy || rel.target_policy_id || rel.targetPolicyId || '';
                const targetPolicyName = rel.targetPolicyName || rel.targetPolicy || rel.target_policy_name || rel.targetPolicyName || 'Unknown Policy';
                const description = rel.description || rel.desc || '';
                
                console.log('Extracted values:', {
                    relationshipType,
                    relationshipTypeName,
                    targetPolicyId,
                    targetPolicyName,
                    description,
                    targetPolicyIdType: typeof targetPolicyId,
                    targetPolicyIdValue: targetPolicyId
                });
                
                // Create relationship type options
                const relationshipTypeOptions = relationTypes.map(rt => {
                    const name = rt.primaryName || rt.PrimaryName || rt.name || rt.Name || rt.primaryname || 'Unknown Type';
                    const selected = rt.id == relationshipType ? 'selected' : '';
                    return `<option value="${rt.id}" ${selected}>${escapeHtml(name)}</option>`;
                }).join('');
                
                // Create target policy options
                const targetPolicyOptions = policies.map(p => {
                    const name = p.name || p.Name || p.primaryname || p.PrimaryName || 'Unknown Policy';
                    const selected = p.id == targetPolicyId ? 'selected' : '';
                    console.log(`Target policy option: id=${p.id}, name=${name}, targetPolicyId=${targetPolicyId}, selected=${selected}`);
                    return `<option value="${p.id}" ${selected}>${escapeHtml(name)}</option>`;
                }).join('');
                
                return `
                    <div class="relationship-item existing-row" data-id="${rel.id || rel.ID}">
                        <div class="relationship-type">
                            <select class="form-select form-select-sm relationship-type-select" data-original-value="${relationshipType}" onchange="markRowAsChanged(this)">
                                <option value="">Select Relationship Type</option>
                                ${relationshipTypeOptions}
                            </select>
                        </div>
                        <div class="relationship-target">
                            <select class="form-select form-select-sm target-policy-select" data-original-value="${targetPolicyId}" onchange="markRowAsChanged(this)">
                                <option value="">Select Target Policy</option>
                                ${targetPolicyOptions}
                            </select>
                        </div>
                        <div class="relationship-description">
                            <input type="text" class="form-control form-control-sm description-input" value="${escapeHtml(description)}" data-original-value="${escapeHtml(description)}" onchange="markRowAsChanged(this)" placeholder="Enter description...">
                        </div>
                        <div class="relationship-actions">
                            <button type="button" class="action-btn add-btn" onclick="addPolicyRelationship()" title="Add Row">
                                <i class="fas fa-plus"></i>
                            </button>
                            <button type="button" class="action-btn remove-btn" onclick="removePolicyRelationship(this)" title="Remove Row">
                                <i class="fas fa-minus"></i>
                            </button>
                        </div>
                    </div>
                `;
            }).join('');
            
            relationshipsList.innerHTML = existingRows;
            console.log('HTML content set:', relationshipsList.innerHTML);
            
            console.log('Container children count:', relationshipsList.children.length);
            console.log('Container visible:', relationshipsList.offsetHeight > 0);
            console.log('Container display:', window.getComputedStyle(relationshipsList).display);
            console.log('Container visibility:', window.getComputedStyle(relationshipsList).visibility);
            console.log('Container height:', relationshipsList.offsetHeight);
            console.log('First child visible:', relationshipsList.children[0] ? window.getComputedStyle(relationshipsList.children[0]).display : 'No children');
            
            
            // Force a re-render to ensure visibility
            setTimeout(() => {
                console.log('After timeout - Container height:', relationshipsList.offsetHeight);
                console.log('After timeout - First child height:', relationshipsList.children[0]?.offsetHeight);
            }, 100);
        }
        
        // Always show at least one empty row for adding new relationships
        console.log('Creating empty relationship row for policy ID:', policyId);
        await showEmptyRelationshipRow(relationshipsList, policyId);

        console.log('Relationships list rendered successfully');

    } catch (error) {
        console.error('Failed to load components relationships:', error);
        relationshipsList.innerHTML = '<div class="error-message">Failed to load relationships: ' + error.message + '</div>';
    }
}

// Show empty relationship row for adding new relationships
async function showEmptyRelationshipRow(container, policyId) {
    try {
        console.log('showEmptyRelationshipRow called for policy ID:', policyId);
        console.log('Container:', container);
        
        // Load relationship types and policies
        const [relationTypes, policies] = await Promise.all([
            loadRelationshipTypes(),
            loadPolicies()
        ]);
        
        console.log('Loaded relation types:', relationTypes);
        console.log('Loaded policies:', policies);
        
        const emptyRow = document.createElement('div');
        emptyRow.className = 'relationship-item empty-row';
        emptyRow.innerHTML = `
            <div class="relationship-type">
                <select class="form-select form-select-sm relationship-type-select" onchange="validateRelationshipRow(this)">
                    <option value="">Select Relationship Type</option>
                    ${relationTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.primaryName || rt.PrimaryName || rt.name || rt.Name || rt.primaryname || 'Unknown Type')}</option>`).join('')}
                </select>
            </div>
            <div class="relationship-target">
                <select class="form-select form-select-sm target-policy-select" onchange="validateRelationshipRow(this)">
                    <option value="">Select Target Policy</option>
                    ${policies.map(p => `<option value="${p.id}">${escapeHtml(p.name || p.Name || p.primaryname || p.PrimaryName || 'Unknown Policy')}</option>`).join('')}
                </select>
            </div>
            <div class="relationship-description">
                <input type="text" class="form-control form-control-sm description-input" placeholder="Enter description..." onchange="validateRelationshipRow(this)">
            </div>
            <div class="relationship-actions">
                <button type="button" class="action-btn add-btn" onclick="addPolicyRelationship()" title="Add Row">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="action-btn remove-btn" onclick="removePolicyRelationship(this)" title="Remove Row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        `;
        
        container.appendChild(emptyRow);
        console.log('Empty relationship row added to container');
    } catch (error) {
        console.error('Failed to create empty relationship row:', error);
    }
}


// Load policies
async function loadPolicies() {
    try {
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        const policies = await api.getPolicyList();
        return Array.isArray(policies) ? policies : [];
    } catch (error) {
        console.error('Failed to load policies:', error);
        return [];
    }
}

// Load dropdown options for existing rows
async function loadDropdownOptionsForExistingRows(relationships) {
    try {
        const [relationTypes, policies] = await Promise.all([
            loadRelationshipTypes(),
            loadPolicies()
        ]);
        
        console.log('Loaded relation types:', relationTypes);
        console.log('First relation type:', relationTypes[0]);
        console.log('First relation type keys:', relationTypes[0] ? Object.keys(relationTypes[0]) : 'No relation types');
        console.log('Loaded policies:', policies);
        console.log('First policy:', policies[0]);
        
        // Update relationship type selects
        const relationshipTypeSelects = document.querySelectorAll('.existing-row .relationship-type-select');
        console.log('Found relationship type selects:', relationshipTypeSelects.length);
        
        relationshipTypeSelects.forEach((select, index) => {
            const currentValue = select.getAttribute('data-original-value');
            console.log(`Updating relationship type select ${index}, current value: ${currentValue}`);
            console.log('Select onchange attribute:', select.getAttribute('onchange'));
            
            const options = relationTypes.map(rt => {
                const name = rt.primaryName || rt.PrimaryName || rt.name || rt.Name || rt.primaryname || 'Unknown Type';
                const selected = rt.id == currentValue ? 'selected' : '';
                console.log(`Creating option for relation type: id=${rt.id}, name=${name}, selected=${selected}`);
                return `<option value="${rt.id}" ${selected}>${escapeHtml(name)}</option>`;
            }).join('');
            
            select.innerHTML = `
                <option value="">Select Relationship Type</option>
                ${options}
            `;
            
            
            console.log(`Updated relationship type select ${index}:`, select.innerHTML);
        });
        
        // Update target policy selects
        const targetPolicySelects = document.querySelectorAll('.existing-row .target-policy-select');
        console.log('Found target policy selects:', targetPolicySelects.length);
        
        targetPolicySelects.forEach((select, index) => {
            const currentValue = select.getAttribute('data-original-value');
            console.log(`Updating target policy select ${index}, current value: ${currentValue}`);
            console.log('Target policy select onchange attribute:', select.getAttribute('onchange'));
            
            select.innerHTML = `
                <option value="">Select Target Policy</option>
                ${policies.map(p => `<option value="${p.id}" ${p.id == currentValue ? 'selected' : ''}>${escapeHtml(p.name || p.Name || p.primaryname || p.PrimaryName || 'Unknown Policy')}</option>`).join('')}
            `;
            
            
            console.log(`Updated target policy select ${index}:`, select.innerHTML);
        });
        
        
    } catch (error) {
        console.error('Failed to load dropdown options for existing rows:', error);
    }
}

// Mark row as changed
function markRowAsChanged(element) {
    console.log('=== markRowAsChanged called ===');
    console.log('Element:', element);
    console.log('Element value:', element.value);
    
    const row = element.closest('.relationship-item');
    if (!row) {
        console.log('Row not found for element:', element);
        return;
    }
    
    const originalValue = element.getAttribute('data-original-value');
    const currentValue = element.value;
    
    console.log('Original value:', originalValue);
    console.log('Current value:', currentValue);
    
    // Mark row as changed if value is different from original
    if (originalValue !== currentValue) {
        row.classList.add('row-changed');
        row.style.backgroundColor = '#fff3cd';
        row.style.borderLeft = '4px solid #ffc107';
        console.log('Row marked as changed - values different');
    } else {
        // Check if all fields are back to original values
        const relationshipTypeSelect = row.querySelector('.relationship-type-select');
        const targetPolicySelect = row.querySelector('.target-policy-select');
        const descriptionInput = row.querySelector('.description-input');
        
        const relationshipTypeChanged = relationshipTypeSelect && 
            relationshipTypeSelect.getAttribute('data-original-value') !== relationshipTypeSelect.value;
        const targetPolicyChanged = targetPolicySelect && 
            targetPolicySelect.getAttribute('data-original-value') !== targetPolicySelect.value;
        const descriptionChanged = descriptionInput && 
            descriptionInput.getAttribute('data-original-value') !== descriptionInput.value;
            
        console.log('Field change status:', {
            relationshipTypeChanged,
            targetPolicyChanged,
            descriptionChanged,
            relationshipTypeOriginal: relationshipTypeSelect?.getAttribute('data-original-value'),
            relationshipTypeCurrent: relationshipTypeSelect?.value,
            targetPolicyOriginal: targetPolicySelect?.getAttribute('data-original-value'),
            targetPolicyCurrent: targetPolicySelect?.value,
            descriptionOriginal: descriptionInput?.getAttribute('data-original-value'),
            descriptionCurrent: descriptionInput?.value
        });
        
        if (!relationshipTypeChanged && !targetPolicyChanged && !descriptionChanged) {
            row.classList.remove('row-changed');
            row.style.backgroundColor = '';
            row.style.borderLeft = '';
            console.log('Row marked as unchanged - all values back to original');
        } else {
            console.log('Row still changed - other fields modified');
        }
    }
    
    console.log('Row classes after change:', row.className);
}

// Validate relationship row
function validateRelationshipRow(element) {
    const row = element.closest('.relationship-item');
    if (row) {
        const relationshipType = row.querySelector('.relationship-type-select')?.value;
        const targetPolicy = row.querySelector('.target-policy-select')?.value;
        
        // Basic validation - you can add more complex validation here
        if (relationshipType && targetPolicy) {
            row.classList.add('row-valid');
            row.classList.remove('row-invalid');
        } else {
            row.classList.remove('row-valid');
            row.classList.add('row-invalid');
        }
    }
}

// Collect relationship data for saving
function collectRelationshipData() {
    console.log('=== collectRelationshipData called ===');
    const relationshipsList = document.getElementById('componentsRelationshipsList');
    if (!relationshipsList) {
        console.log('componentsRelationshipsList not found');
        return { relationships: [], errors: [] };
    }
    
    console.log('Collecting relationship data from container:', relationshipsList);
    
    const relationships = [];
    const errors = [];
    
    // Get all relationship rows (both existing and new)
    const allRows = relationshipsList.querySelectorAll('.relationship-item');
    console.log('Found relationship rows:', allRows.length);
    
    allRows.forEach((row, index) => {
        const relationshipType = row.querySelector('.relationship-type-select')?.value;
        const targetPolicy = row.querySelector('.target-policy-select')?.value;
        const description = row.querySelector('.description-input')?.value || '';
        const rowId = row.getAttribute('data-id');
        
        console.log(`Row ${index + 1}:`, {
            relationshipType,
            targetPolicy,
            targetPolicyType: typeof targetPolicy,
            description,
            rowId,
            isDeleted: row.classList.contains('row-deleted'),
            isChanged: row.classList.contains('row-changed'),
            isNew: !rowId
        });
        
        // Skip empty rows (new rows that haven't been filled)
        if (!relationshipType && !targetPolicy) {
            console.log(`Skipping empty row ${index + 1}`);
            return;
        }
        
        // Validate required fields
        if (!relationshipType) {
            errors.push(`Row ${index + 1}: Relationship Type is required`);
            return;
        }
        
        if (!targetPolicy) {
            errors.push(`Row ${index + 1}: Target Policy is required`);
            return;
        }
        
        const relationshipData = {
            id: rowId || null, // null for new relationships
            relationshipType: parseInt(relationshipType),
            targetPolicy: parseInt(targetPolicy),
            description: description,
            isNew: !rowId, // true for new relationships
            isChanged: row.classList.contains('row-changed')
        };
        
        relationships.push(relationshipData);
    });
    
    console.log('Collected relationships:', relationships);
    console.log('Collected errors:', errors);
    console.log('Relationships summary:', relationships.map(rel => ({
        id: rel.id,
        isNew: rel.isNew,
        isChanged: rel.isChanged,
        relationshipType: rel.relationshipType,
        targetPolicyId: rel.targetPolicyId
    })));
    
    return { relationships, errors };
}

// Save policy relationships (main function called from save button)
async function savePolicyRelationships() {
    try {
        console.log('=== savePolicyRelationships called ===');
        
        const relationshipsList = document.getElementById('componentsRelationshipsList');
        if (!relationshipsList) {
            console.log('componentsRelationshipsList not found');
            return true; // No relationships to save
        }
        
        // Check if there are any relationship rows
        const allRows = relationshipsList.querySelectorAll('.relationship-item');
        if (allRows.length === 0) {
            console.log('No relationship rows found');
            return true; // No relationships to save
        }
        
        // Collect and save relationship data
        const { relationships, errors } = collectRelationshipData();
        
        if (errors.length > 0) {
            alert('Please fix the following errors:\n' + errors.join('\n'));
            return false;
        }
        
        if (relationships.length === 0) {
            console.log('No relationship data to save');
            return true;
        }
        
        console.log('Saving relationship data:', relationships);
        
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        console.log('API service available:', !!api);
        console.log('API methods available:', {
            createPolicyRelationship: typeof api.createPolicyRelationship,
            updatePolicyRelationship: typeof api.updatePolicyRelationship,
            deletePolicyRelationship: typeof api.deletePolicyRelationship
        });
        
        const policyId = parseId();
        if (!policyId) {
            throw new Error('Policy ID not found');
        }
        
        // Process each relationship
        for (const rel of relationships) {
            if (rel.isNew) {
                // Create new relationship
                const relationshipData = {
                    sourceId: policyId,
                    targetId: parseInt(rel.targetPolicy),
                    relationType: rel.relationshipType,
                    description: rel.description,
                    userId: null // Set to null to avoid foreign key constraint issues
                };
                
                console.log('Creating relationship with data:', relationshipData);
                const response = await api.createPolicyRelationship(relationshipData);
                console.log('Create relationship response:', response);
                
                if (response && response.success) {
                    console.log('Created new relationship successfully:', relationshipData);
                } else {
                    console.error('Failed to create relationship. Response:', response);
                    throw new Error('Failed to create relationship');
                }
                
            } else if (rel.isChanged) {
                // Update existing relationship
                const relationshipData = {
                    relationType: rel.relationshipType,
                    targetPolicyId: rel.targetPolicy,
                    description: rel.description,
                    userId: null // Set to null to avoid foreign key constraint issues
                };
                
                console.log('Updating relationship with ID:', rel.id, 'data:', relationshipData);
                console.log('targetPolicy value:', rel.targetPolicy, 'type:', typeof rel.targetPolicy);
                const response = await api.updatePolicyRelationship(rel.id, relationshipData);
                console.log('Update relationship response:', response);
                
                if (response && response.success) {
                    console.log('Updated relationship successfully:', relationshipData);
                } else {
                    console.error('Failed to update relationship. Response:', response);
                    throw new Error('Failed to update relationship');
                }
            }
        }
        
        // Process deleted relationships
        const deletedRows = relationshipsList.querySelectorAll('.row-deleted');
        for (const row of deletedRows) {
            const id = row.getAttribute('data-id');
            if (id) {
                const response = await api.deletePolicyRelationship(id);
                if (response && response.success) {
                    console.log('Deleted relationship:', id);
                } else {
                    throw new Error('Failed to delete relationship');
                }
            }
        }
        
        console.log('All relationships saved successfully');
        
        // Reload the relationships data to reflect changes
        await loadPolicyComponentsRelationships(policyId);
        
        return true;
        
    } catch (error) {
        console.error('Failed to save relationship data:', error);
        alert('Failed to save relationships: ' + error.message);
        return false;
    }
}

// Save relationship data (legacy function - kept for compatibility)
async function saveRelationshipData() {
    try {
        console.log('saveRelationshipData called');
        const { relationships, errors } = collectRelationshipData();
        
        if (errors.length > 0) {
            alert('Please fix the following errors:\n' + errors.join('\n'));
            return false;
        }
        
        if (relationships.length === 0) {
            console.log('No relationship data to save');
            return true;
        }
        
        console.log('Saving relationship data:', relationships);
        
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        console.log('API service available:', !!api);
        console.log('API methods available:', {
            createPolicyRelationship: typeof api.createPolicyRelationship,
            updatePolicyRelationship: typeof api.updatePolicyRelationship,
            deletePolicyRelationship: typeof api.deletePolicyRelationship
        });
        
        const policyId = parseId();
        if (!policyId) {
            throw new Error('Policy ID not found');
        }
        
        // Process each relationship
        for (const rel of relationships) {
            if (rel.isNew) {
                // Create new relationship
                const relationshipData = {
                    sourceId: policyId,
                    targetId: parseInt(rel.targetPolicy),
                    relationType: rel.relationshipType,
                    description: rel.description,
                    userId: null // Set to null to avoid foreign key constraint issues
                };
                
                console.log('Creating relationship with data:', relationshipData);
                const response = await api.createPolicyRelationship(relationshipData);
                console.log('Create relationship response:', response);
                
                if (response && response.success) {
                    console.log('Created new relationship successfully:', relationshipData);
                } else {
                    console.error('Failed to create relationship. Response:', response);
                    throw new Error('Failed to create relationship');
                }
                
            } else if (rel.isChanged) {
                // Update existing relationship
                const relationshipData = {
                    relationType: rel.relationshipType,
                    targetPolicyId: rel.targetPolicy,
                    description: rel.description,
                    userId: null // Set to null to avoid foreign key constraint issues
                };
                
                console.log('Updating relationship with ID:', rel.id, 'data:', relationshipData);
                console.log('targetPolicy value:', rel.targetPolicy, 'type:', typeof rel.targetPolicy);
                const response = await api.updatePolicyRelationship(rel.id, relationshipData);
                console.log('Update relationship response:', response);
                
                if (response && response.success) {
                    console.log('Updated relationship successfully:', relationshipData);
                } else {
                    console.error('Failed to update relationship. Response:', response);
                    throw new Error('Failed to update relationship');
                }
            }
        }
        
        // Process deleted relationships
        const relationshipsList = document.getElementById('componentsRelationshipsList');
        if (!relationshipsList) {
            console.log('componentsRelationshipsList not found for deletion processing');
            return true;
        }
        
        const deletedRows = relationshipsList.querySelectorAll('.row-deleted');
        for (const row of deletedRows) {
            const id = row.getAttribute('data-id');
            if (id) {
                const response = await api.deletePolicyRelationship(id);
                if (response && response.success) {
                    console.log('Deleted relationship:', id);
                } else {
                    throw new Error('Failed to delete relationship');
                }
            }
        }
        
        console.log('All relationships saved successfully');
        
        // Reload the relationships data to reflect changes
        await loadPolicyComponentsRelationships(policyId);
        
        return true;
        
    } catch (error) {
        console.error('Failed to save relationship data:', error);
        alert('Failed to save relationships: ' + error.message);
        return false;
    }
}

// Add policy relationship (add new row)
async function addPolicyRelationship() {
    const relationshipsList = document.getElementById('componentsRelationshipsList');
    const policyId = parseId();
    
    if (policyId) {
        await showEmptyRelationshipRow(relationshipsList, policyId);
    }
}

// Remove policy relationship
async function removePolicyRelationship(button) {
    const row = button.closest('.relationship-item');
    const relationshipsList = document.getElementById('componentsRelationshipsList');
    const allRows = relationshipsList.querySelectorAll('.relationship-item');
    const isFirstRow = row === allRows[0];
    const id = row.getAttribute('data-id');
    
    if (id) {
        // Mark for deletion (don't delete from database yet)
        row.classList.add('row-deleted');
        row.style.display = 'none';
        console.log('Row marked for deletion:', row);
        
        // If this was the first row, show an empty row
        if (isFirstRow) {
            await showEmptyRelationshipRow(relationshipsList, parseId());
        }
    } else {
        // For empty rows (no data-id), just remove them
        if (isFirstRow) {
            // For first empty row, only clear the data
            await clearRelationshipRowData(row);
        } else {
            // For other empty rows, remove the entire row
            row.remove();
        }
    }
}

// Clear relationship row data
async function clearRelationshipRowData(row) {
    const policyId = parseId();
    
    try {
        const [relationTypes, policies] = await Promise.all([
            loadRelationshipTypes(),
            loadPolicies()
        ]);
        
        row.innerHTML = `
            <div class="relationship-type">
                <select class="form-select form-select-sm relationship-type-select" onchange="validateRelationshipRow(this)">
                    <option value="">Select Relationship Type</option>
                    ${relationTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.name)}</option>`).join('')}
                </select>
            </div>
            <div class="relationship-target">
                <select class="form-select form-select-sm target-policy-select" onchange="validateRelationshipRow(this)">
                    <option value="">Select Target Policy</option>
                    ${policies.map(p => `<option value="${p.id}">${escapeHtml(p.name)}</option>`).join('')}
                </select>
            </div>
            <div class="relationship-description">
                <input type="text" class="form-control form-control-sm description-input" placeholder="Enter description..." onchange="validateRelationshipRow(this)">
            </div>
            <div class="relationship-actions">
                <button type="button" class="action-btn add-btn" onclick="addPolicyRelationship()" title="Add Row">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="action-btn remove-btn" onclick="removePolicyRelationship(this)" title="Remove Row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        `;
    } catch (error) {
        console.error('Failed to clear relationship row data:', error);
    }
}





// Handle URL tab parameter
function handleUrlTabParameter() {
    const urlParams = new URLSearchParams(window.location.search);
    const tabParam = urlParams.get('tab');
    
    if (tabParam) {
        console.log('URL tab parameter found:', tabParam);
        
        // Find and click the tab
        const targetTab = document.querySelector(`[data-tab="${tabParam}"]`);
        if (targetTab) {
            console.log('Switching to tab from URL:', tabParam);
            targetTab.click();
        } else {
            console.warn('Tab not found for parameter:', tabParam);
        }
    }
}

// Setup components sub-tabs
function setupComponentsSubTabs() {
    console.log('Setting up components sub-tabs...');
    
    const subTabs = document.querySelectorAll('#componentsTab .sub-tab');
    console.log('Found sub-tabs:', subTabs.length);
    
    subTabs.forEach(function(subTab) {
        subTab.addEventListener('click', function(e) {
            e.preventDefault();
            console.log('Sub-tab clicked:', this.textContent.trim());
            
            // Remove active class from all sub-tabs
            subTabs.forEach(function(t) { t.classList.remove('active'); });
            this.classList.add('active');
            
            const subTabName = this.getAttribute('data-sub-tab');
            console.log('Switching to sub-tab:', subTabName);
            
            // Hide all sub-tab contents
            const subTabContents = document.querySelectorAll('#componentsTab .sub-tab-content');
            subTabContents.forEach(function(content) {
                content.classList.remove('active');
                content.style.display = 'none';
            });
            
            // Show selected sub-tab content
            let targetSubTab;
            if (subTabName === 'hierarchy') {
                targetSubTab = document.getElementById('componentsHierarchyContent');
                console.log('Found hierarchy content:', !!targetSubTab);
            } else if (subTabName === 'relationships') {
                targetSubTab = document.getElementById('componentsRelationshipsContent');
                console.log('Found relationships content:', !!targetSubTab);
            }
            
            if (targetSubTab) {
                targetSubTab.classList.add('active');
                targetSubTab.style.display = 'block';
                console.log('Switched to sub-tab:', subTabName);
                
                // Load data for specific sub-tabs
                if (subTabName === 'relationships') {
                    const policyId = parseId();
                    console.log('Loading relationships for policy ID:', policyId);
                    if (policyId) {
                        loadPolicyComponentsRelationships(policyId);
                    }
                }
            } else {
                console.error('Sub-tab content not found for:', subTabName);
            }
        });
    });
}

// Utility function to escape HTML
function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// View policy function
function viewPolicy(policyId) {
    console.log('viewPolicy called with ID:', policyId);
    if (policyId) {
        console.log('Navigating to policy view page for ID:', policyId);
        window.location.href = `/view/policy/policy.html?id=${policyId}`;
    } else {
        console.error('No policy ID provided to viewPolicy');
    }
}

// Make viewPolicy globally available
window.viewPolicy = viewPolicy;

// Show Add Relationship Modal
function showAddRelationshipModal(policyId) {
    console.log('Showing add relationship modal for policy ID:', policyId);
    
    // Create modal HTML
    const modalHTML = `
        <div id="addRelationshipModal" class="modal-overlay" style="display: flex;">
            <div class="modal-content" style="max-width: 600px; width: 90%;">
                <div class="modal-header">
                    <h3>Add Policy Relationship</h3>
                    <button type="button" class="modal-close" onclick="closeAddRelationshipModal()">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
                <div class="modal-body">
                    <form id="addRelationshipForm">
                        <div class="form-group">
                            <label for="relationshipType" class="form-label required">Relationship Type</label>
                            <select id="relationshipType" class="form-select" required>
                                <option value="">Select relationship type</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="targetPolicy" class="form-label required">Target Policy</label>
                            <div class="input-group">
                                <input type="text" id="targetPolicySearch" class="form-input" placeholder="Search for policy..." autocomplete="off">
                                <input type="hidden" id="targetPolicyId" name="targetPolicyId">
                                <button type="button" class="btn btn-secondary" id="searchPolicyBtn">
                                    <i class="fas fa-search"></i>
                                </button>
                            </div>
                            <div id="policySearchResults" class="search-results" style="display: none;"></div>
                        </div>
                        <div class="form-group">
                            <label for="relationshipDescription" class="form-label">Description</label>
                            <textarea id="relationshipDescription" class="form-textarea" rows="3" placeholder="Enter relationship description"></textarea>
                        </div>
                    </form>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" onclick="closeAddRelationshipModal()">Cancel</button>
                    <button type="button" class="btn btn-primary" onclick="savePolicyRelationship(${policyId})">Add Relationship</button>
                </div>
            </div>
        </div>
    `;
    
    // Add modal to page
    document.body.insertAdjacentHTML('beforeend', modalHTML);
    
    // Load relationship types
    loadRelationshipTypes();
    
    // Setup search functionality
    setupPolicySearch();
}

// Close Add Relationship Modal
function closeAddRelationshipModal() {
    const modal = document.getElementById('addRelationshipModal');
    if (modal) {
        modal.remove();
    }
}

// Load relationship types
async function loadRelationshipTypes() {
    try {
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        const relationTypes = await api.getPolicyRelationTypes();
        return Array.isArray(relationTypes) ? relationTypes : [];
    } catch (error) {
        console.error('Failed to load relationship types:', error);
        return [];
    }
}

// Setup policy search
function setupPolicySearch() {
    const searchInput = document.getElementById('targetPolicySearch');
    const searchBtn = document.getElementById('searchPolicyBtn');
    const resultsDiv = document.getElementById('policySearchResults');
    const targetPolicyIdInput = document.getElementById('targetPolicyId');
    
    let searchTimeout;
    
    searchInput.addEventListener('input', function() {
        clearTimeout(searchTimeout);
        const query = this.value.trim();
        
        if (query.length < 2) {
            resultsDiv.style.display = 'none';
            return;
        }
        
        searchTimeout = setTimeout(() => {
            searchPolicies(query, resultsDiv, targetPolicyIdInput, searchInput);
        }, 300);
    });
    
    searchBtn.addEventListener('click', function() {
        const query = searchInput.value.trim();
        if (query.length >= 2) {
            searchPolicies(query, resultsDiv, targetPolicyIdInput, searchInput);
        }
    });
}

// Search policies
async function searchPolicies(query, resultsDiv, targetPolicyIdInput, searchInput) {
    try {
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        // Use search API if available, otherwise get all policies and filter
        let policies = [];
        try {
            policies = await api.searchPolicies(query);
        } catch (e) {
            // Fallback to getting all policies and filtering
            const allPolicies = await api.getPolicyList();
            policies = allPolicies.filter(policy => 
                (policy.primaryname || policy.PrimaryName || '').toLowerCase().includes(query.toLowerCase()) ||
                (policy.description || policy.Description || '').toLowerCase().includes(query.toLowerCase())
            );
        }
        
        if (policies.length === 0) {
            resultsDiv.innerHTML = '<div class="search-result-item">No policies found</div>';
        } else {
            resultsDiv.innerHTML = policies.map(policy => {
                const name = policy.primaryname || policy.PrimaryName || 'Unnamed Policy';
                const id = policy.id || policy.ID;
                return `
                    <div class="search-result-item" onclick="selectPolicy(${id}, '${name.replace(/'/g, "\\'")}', '${targetPolicyIdInput.id}', '${searchInput.id}')">
                        <div class="policy-name">${escapeHtml(name)}</div>
                        <div class="policy-description">${escapeHtml(policy.description || policy.Description || 'No description')}</div>
                    </div>
                `;
            }).join('');
        }
        
        resultsDiv.style.display = 'block';
    } catch (error) {
        console.error('Failed to search policies:', error);
        resultsDiv.innerHTML = '<div class="search-result-item">Error searching policies</div>';
        resultsDiv.style.display = 'block';
    }
}

// Select policy from search results
function selectPolicy(policyId, policyName, targetIdInputId, searchInputId) {
    document.getElementById(targetIdInputId).value = policyId;
    document.getElementById(searchInputId).value = policyName;
    document.getElementById('policySearchResults').style.display = 'none';
}

// Save policy relationship
async function savePolicyRelationship(sourcePolicyId) {
    try {
        const relationshipType = document.getElementById('relationshipType').value;
        const targetPolicyId = document.getElementById('targetPolicyId').value;
        const description = document.getElementById('relationshipDescription').value;
        
        if (!relationshipType || !targetPolicyId) {
            alert('Please fill in all required fields');
            return;
        }
        
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        const relationshipData = {
            sourcePolicyId: sourcePolicyId,
            targetPolicyId: parseInt(targetPolicyId),
            relationshipType: parseInt(relationshipType),
            description: description
        };
        
        console.log('Creating policy relationship:', relationshipData);
        
        await api.createPolicyRelationship(relationshipData);
        
        console.log('Policy relationship created successfully');
        
        // Close modal
        closeAddRelationshipModal();
        
        // Reload relationships data
        loadPolicyComponentsRelationships(sourcePolicyId);
        
        // Show success message
        alert('Relationship added successfully');
        
    } catch (error) {
        console.error('Failed to save policy relationship:', error);
        alert('Failed to save relationship: ' + error.message);
    }
}

// Edit policy relationship
function editPolicyRelationship(relationshipId) {
    console.log('Edit relationship clicked for ID:', relationshipId);
    
    // For now, we'll show the add modal with pre-filled data
    // In a full implementation, you would load the relationship data and pre-fill the form
    const policyId = parseId();
    if (policyId) {
        showAddRelationshipModal(policyId);
    }
}

// Delete policy relationship
async function deletePolicyRelationship(relationshipId) {
    console.log('Delete relationship clicked for ID:', relationshipId);
    
    const confirmed = await (typeof window.showConfirmDialog === 'function'
        ? window.showConfirmDialog({ message: 'Are you sure you want to delete this relationship?', type: 'warning' })
        : Promise.resolve(confirm('Are you sure you want to delete this relationship?')));
    if (!confirmed) {
        return;
    }
    
    try {
        const api = window.BUDG_API_SERVICE;
        if (!api) {
            throw new Error('API service not available');
        }
        
        console.log('Deleting policy relationship:', relationshipId);
        
        await api.deletePolicyRelationship(relationshipId);
        
        console.log('Policy relationship deleted successfully');
        
        // Reload relationships data
        const policyId = parseId();
        if (policyId) {
            loadPolicyComponentsRelationships(policyId);
        }
        
        // Show success message
        alert('Relationship deleted successfully');
        
    } catch (error) {
        console.error('Failed to delete policy relationship:', error);
        alert('Failed to delete relationship: ' + error.message);
    }
}