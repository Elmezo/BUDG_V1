// Glossary Edit Page JavaScript

// Global variables
let currentGlossaryId = null;
let isDirty = false;
let hasFormChanges = false;
let globalGlossaryList = []; // Store glossary list globally for relationship dropdowns
let segmentField = null; // Segment field component reference
let editViewMode = 'original'; // 'original' | 'changes' (when under active CR, edit should load nobject_id data)
let originalGlossarySegmentId = null; // segment loaded from server; used to detect segment change

function normalizeGlossarySegmentId(value) {
    if (value == null || value === '') return null;
    const n = parseInt(value, 10);
    return Number.isInteger(n) ? n : null;
}

// Permission check variables
let userCanEdit = false;
let isAdminUser = false;
let hasRoleEditPermission = false;

// Check user permissions
async function checkUserPermissions() {
    try {
        const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
        if (!meResp.ok) {
            userCanEdit = false;
            return;
        }
        const me = await meResp.json();
        const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
        isAdminUser = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
        
        // Check role-based edit permission
        if (!isAdminUser) {
            try {
                const permResp = await fetch('/api/user/permissions/Glossary', { 
                    method: 'GET', 
                    credentials: 'include' 
                });
                if (permResp.ok) {
                    const permData = await permResp.json();
                    if (permData.success) {
                        hasRoleEditPermission = permData.canEdit === true || permData.isAdmin === true;
                    }
                }
            } catch (permError) {
                console.warn('[GlossaryEdit] Error checking permissions:', permError);
            }
        }
        
        userCanEdit = isAdminUser || hasRoleEditPermission;
        if (window.__BUDG_DEBUG__) console.log('[GlossaryEdit] User can edit:', userCanEdit, 'isAdmin:', isAdminUser, 'hasRolePermission:', hasRoleEditPermission);
        
        // Apply permissions to UI
        applyPermissionsToUI();
    } catch (e) {
        console.error('Error checking user permissions:', e);
        userCanEdit = false;
        applyPermissionsToUI();
    }
}

// Apply permissions to UI - hide/show edit buttons and disable form controls
function applyPermissionsToUI() {
    // Apply to strategic source table
    const strategicSourceButtons = document.querySelectorAll('#strategicSourceTableBody button, #strategicSourceTableBody .action-button');
    strategicSourceButtons.forEach(btn => {
        if (userCanEdit) {
            btn.style.display = '';
            btn.disabled = false;
        } else {
            btn.style.display = 'none';
            btn.disabled = true;
        }
    });
    
    // Disable strategic source selects
    const strategicSourceSelects = document.querySelectorAll('#strategicSourceTableBody select');
    strategicSourceSelects.forEach(select => {
        select.disabled = !userCanEdit;
        if (!userCanEdit) {
            select.style.backgroundColor = '#f5f5f5';
            select.style.cursor = 'not-allowed';
        } else {
            select.style.backgroundColor = '';
            select.style.cursor = '';
        }
    });
    
    // Apply to relationships table if exists
    const relationshipButtons = document.querySelectorAll('#relationshipsTableBody button, #relationshipsTableBody .action-button');
    relationshipButtons.forEach(btn => {
        if (userCanEdit) {
            btn.style.display = '';
            btn.disabled = false;
        } else {
            btn.style.display = 'none';
            btn.disabled = true;
        }
    });
    
    // Disable relationship selects
    const relationshipSelects = document.querySelectorAll('#relationshipsTableBody select');
    relationshipSelects.forEach(select => {
        select.disabled = !userCanEdit;
        if (!userCanEdit) {
            select.style.backgroundColor = '#f5f5f5';
            select.style.cursor = 'not-allowed';
        } else {
            select.style.backgroundColor = '';
            select.style.cursor = '';
        }
    });
}

// Helper function for HTML escaping
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

document.addEventListener('DOMContentLoaded', async function() {
    if (window.__BUDG_DEBUG__) console.log('Initializing glossary edit page...');
    
    const id = parseId();
    if (!id) {
        console.error('No glossary ID found in URL');
        return;
    }
    
    if (window.__BUDG_DEBUG__) console.log('Initializing glossary edit page for ID:', id);
    currentGlossaryId = id;
    
    // Initialize lock manager
    const lockManager = new window.LockManager('glossary', id);
    window.currentLockManager = lockManager;
    
    // Setup auto-release on page unload
    lockManager.setupBeforeUnload();
    
    // Check lock status
    const lockStatus = await lockManager.checkLockStatus();
    const status = lockStatus?.status || 'no_lock';
    
    // Handle lock conflicts
    if (status === 'locked_by_other') {
        const lockedBy = lockStatus.lockedByName || 'another user';
        alert(`This glossary is currently locked by ${lockedBy}. Please try again later.`);
        window.location.href = `/view/glossary/glossary.html?id=${id}`;
        return;
    } else if (status === 'permanently_locked') {
        const isSuperAdmin = await lockManager.checkIsSuperAdmin();
        if (!isSuperAdmin) {
            const lockedBy = lockStatus.lockedByName || 'an administrator';
            alert(`This glossary has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
            window.location.href = `/view/glossary/glossary.html?id=${id}`;
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
        window.location.href = `/view/glossary/glossary.html?id=${id}`;
        return;
    }
    
    // Update global lock count if available
    if (window.globalLockUI) {
        await window.globalLockUI.updateLockCount();
    }
    
    // Check permissions first
    await checkUserPermissions();
    
    // Initialize the page
    initializePage();
});

function parseId() {
    if (window.__BUDG_DEBUG__) console.log('Parsing ID from URL:', window.location.href);
    
    // First try URL params (for separate edit pages)
    const urlParams = new URLSearchParams(window.location.search);
    const id = parseInt(urlParams.get('id'), 10);
    if (!Number.isNaN(id)) return id;
    
    // Fallback to path-based ID (for main view pages)
    const parts = window.location.pathname.split('/').filter(Boolean);
    const idx = parts.indexOf('glossary-edit.html');
    if (idx === -1 || parts.length < idx + 2) return null;
    const pathId = parseInt(parts[idx + 1], 10);
    return Number.isNaN(pathId) ? null : pathId;
}

/**
 * Edit pages should load the cloned row (nobject_id) when the object is under an active CR.
 * We determine that by calling the pending-changes status endpoint.
 */
async function determineEditViewMode(glossaryId) {
    // For EDIT pages: If an active CR exists, load the pending changes (nobject_id data)
    // This ensures the edit form shows the latest pending changes, not the original data
    try {
        const res = await fetch(`/api/pending-changes/status/Glossary/${glossaryId}`, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' }
        });
        if (res.ok) {
            const status = await res.json();
            if (status?.underRevision) {
                console.log('[Glossary Edit] Object is under revision, loading changes view');
                return 'changes';
            }
        }
    } catch (e) {
        console.warn('[Glossary Edit] Failed to determine pending-changes status:', e);
    }
    return 'original';
}

function populateSelectSimple(selectId, list, getLabel) {
    const el = document.getElementById(selectId);
    if (!el) {
        console.warn(`Select element ${selectId} not found`);
        return;
    }
    
    if (window.__BUDG_DEBUG__) console.log(`Populating select ${selectId} with list:`, list);
    
    el.innerHTML = '';
    (Array.isArray(list) ? list : []).forEach((item, index) => {
        const op = document.createElement('option');
        op.value = String(item.ID || item.id);
        op.textContent = getLabel ? getLabel(item) : (item.PrimaryName || item.primaryName || item.primaryname || item.name || item.Name || '');
        el.appendChild(op);
        if (window.__BUDG_DEBUG__) console.log(`Added option ${index}:`, {
            value: op.value,
            text: op.textContent,
            originalItem: item
        });
    });
}

async function loadGlossaryEditLookups() {
    if (window.__BUDG_DEBUG__) console.log('=== LOADING GLOSSARY EDIT LOOKUPS ===');
        if (window.__BUDG_DEBUG__) console.log('Loading glossary edit lookups...');
    
    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
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
        // Load lookups with individual error handling
        const [statuses, lifecycle, viewing, types, security, kdeTypes, ciaRatings, formatTypes] = await Promise.allSettled([
            window.BUDG_API_SERVICE.getStatusList(),
            window.BUDG_API_SERVICE.getGlossaryLifecycleList(),
            window.BUDG_API_SERVICE.getViewingList(),
            window.BUDG_API_SERVICE.getGlossaryTypeList(),
            window.BUDG_API_SERVICE.getGlossarySecurityList(),
            window.BUDG_API_SERVICE.getGlossaryKdeList(),
            window.BUDG_API_SERVICE.getCiaRatings(),
            window.BUDG_API_SERVICE.getGlossaryFormatTypeList()
        ]);
        
        // Populate dropdowns with safe access
        if (statuses.status === 'fulfilled') {
            populateSelectSimple('budgStatus', (Array.isArray(statuses.value?.data) ? statuses.value.data : statuses.value), x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        if (lifecycle.status === 'fulfilled') {
            if (window.__BUDG_DEBUG__) console.log('Glossary lifecycle data loaded:', lifecycle.value);
            const lifecycleData = Array.isArray(lifecycle.value?.data) ? lifecycle.value.data : lifecycle.value;
            populateSelectSimple('lifecycle', lifecycleData, x => x.PrimaryName || x.primaryname || x.name || x.Name);
        }
        if (viewing.status === 'fulfilled') {
            populateSelectSimple('budgViewing', viewing.value, x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        if (types.status === 'fulfilled') {
            populateSelectSimple('type', types.value, x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        if (security.status === 'fulfilled') {
            populateSelectSimple('securityClassification', security.value, x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        if (kdeTypes.status === 'fulfilled') {
            populateSelectSimple('kde', kdeTypes.value, x => x.name || x.Name || x.primaryName || x.PrimaryName);
        }
        if (ciaRatings.status === 'fulfilled') {
            // Populate CIA rating dropdowns
            const ciaData = Array.isArray(ciaRatings.value?.data) ? ciaRatings.value.data : ciaRatings.value;
            populateCiaRatings(ciaData);
        }
        if (formatTypes.status === 'fulfilled') {
            populateSelectSimple('formatType', (Array.isArray(formatTypes.value?.data) ? formatTypes.value.data : formatTypes.value), x => x.Name || x.name);
        }
        
        // Apply DFCR locked fields for Glossary facet
        // NOTE: This is an EDIT page. Locks will be applied only if workflow_create_id == workflow_edit_id
        // If only workflow_edit_id is set (different from workflow_create_id), locks will NOT be applied
        if (window.DFCRUtils) {
            try {
                // Get glossary ID first to pass to DFCRUtils
                const glossaryId = parseId();
                
                await window.DFCRUtils.applyLockedFields('Glossary', {
                    status: '#budgStatus',
                    lifecycle: '#lifecycle'
                }, { isEditPage: true, objectId: glossaryId });
                console.log('DFCR locked fields check done for Glossary edit page (objectId:', glossaryId, ')');
                
                // Check if edit workflow is enabled and show Save & Submit button
                const dfcrInfo = await window.DFCRUtils.getInfo('Glossary', glossaryId);
                console.log('DFCR Info for Glossary edit:', dfcrInfo);
                
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
        
        if (window.__BUDG_DEBUG__) console.log('Glossary edit lookups loaded successfully');
    } catch (error) {
        console.error('Error loading glossary edit lookups:', error);
    }
}

function populateCiaRatings(ciaRatings) {
    const confidentialitySelect = document.getElementById('confidentialityRating');
    const integritySelect = document.getElementById('integrityRating');
    const availabilitySelect = document.getElementById('availabilityRating');
    
    if (confidentialitySelect) {
        confidentialitySelect.innerHTML = '<option value="">-</option>';
        ciaRatings.forEach(rating => {
            const option = document.createElement('option');
            option.value = rating.id || rating.ID;
            option.textContent = rating.Values || rating.values || rating.name || rating.Name;
            confidentialitySelect.appendChild(option);
        });
    }
    
    if (integritySelect) {
        integritySelect.innerHTML = '<option value="">-</option>';
        ciaRatings.forEach(rating => {
            const option = document.createElement('option');
            option.value = rating.id || rating.ID;
            option.textContent = rating.Values || rating.values || rating.name || rating.Name;
            integritySelect.appendChild(option);
        });
    }
    
    if (availabilitySelect) {
        availabilitySelect.innerHTML = '<option value="">-</option>';
        ciaRatings.forEach(rating => {
            const option = document.createElement('option');
            option.value = rating.id || rating.ID;
            option.textContent = rating.Values || rating.values || rating.name || rating.Name;
            availabilitySelect.appendChild(option);
        });
    }
}

async function loadGlossary(id, viewMode = 'original') {
    if (window.__BUDG_DEBUG__) console.log('=== LOADING GLOSSARY ===');
        if (window.__BUDG_DEBUG__) console.log('Loading glossary with ID:', id);
    
    // Wait for API service to be available
    if (!window.BUDG_API_SERVICE) {
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
        const response = await window.BUDG_API_SERVICE.getGlossaryById(id, viewMode === 'changes' ? 'changes' : null);
        console.log('=== LOADED GLOSSARY DATA ===');
        console.log('API response:', response);
        
        // Handle both direct data and wrapped response
        const glossary = response?.data || response;
        if (window.__BUDG_DEBUG__) console.log('Glossary data after unwrapping:', glossary);
        
        if (glossary) {
            if (window.__BUDG_DEBUG__) console.log('Calling populateForm...');
            populateForm(glossary);
            updateTitle(glossary);
            
            // Ensure segment value is set even if segmentField wasn't ready during populateForm
            // This is a fallback in case segmentField is initialized after populateForm runs
            if (segmentField) {
                const segmentId = glossary.segmentId ?? glossary.segment_id ?? glossary.Segment_ID;
                if (segmentId != null && segmentId !== undefined && segmentId !== -1) {
                    console.log('Setting segment field value (fallback):', segmentId);
                    segmentField.setValue(parseInt(segmentId, 10));
                } else if (segmentId === -1 || segmentId == null) {
                    console.warn('⚠️ Glossary has no segment assignment (segmentId: ' + segmentId + ')');
                }
            } else {
                console.warn('Segment field not initialized yet, will set value after initialization');
                // Store glossary data to set segment value later
                window._pendingGlossaryData = glossary;
            }
            
            // Load strategic source data
            await loadStrategicSourceData(id, viewMode);
        } else {
            console.error('No glossary data found in response');
        }
    } catch (error) {
        console.error('Error loading glossary:', error);
    }
}

function updateTitle(glossary) {
    const titleElement = document.getElementById('glossaryTitle');
    if (titleElement && glossary) {
        const name = glossary.Name || glossary.name || 'Glossary';
        titleElement.textContent = name;
        if (window.__BUDG_DEBUG__) console.log('Title updated to:', titleElement.textContent);
    }
}

function populateForm(glossary) {
    if (window.__BUDG_DEBUG__) console.log('=== POPULATING FORM ===');
        if (window.__BUDG_DEBUG__) console.log('Glossary data received:', glossary);
    
    const setVal = (i, v) => { 
        const el = document.getElementById(i); 
        if (el) {
            el.value = v ?? '';
            if (window.__BUDG_DEBUG__) console.log(`Set ${i} to:`, v);
        } else {
            console.warn(`Element ${i} not found`);
        }
    };
    
    const setSel = (i, v) => { 
        const el = document.getElementById(i); 
        if (window.__BUDG_DEBUG__) console.log(`Setting select ${i} with value:`, v, 'Element found:', !!el);
        if (el && v != null) {
            const value = String(v);
            el.value = value;
            if (window.__BUDG_DEBUG__) console.log(`Set select ${i} to:`, value);
        } else if (el) {
            if (window.__BUDG_DEBUG__) console.log(`Select ${i} not set (value is null/undefined)`);
        } else {
            console.warn(`Select element ${i} not found`);
        }
    };
    
    // Populate form fields based on glossary data structure
    setVal('glossaryName', glossary.Name || glossary.name);
    setVal('glossaryDefinition', glossary.Description || glossary.description);
    setVal('glossaryRef', glossary.Ref_Number || glossary.ref);
    setSel('formatType', glossary.Format_type || glossary.format_type);
    setVal('aliasNames', glossary.aliases ? glossary.aliases.join(', ') : '');
    setVal('formatDescription', glossary.Format || glossary.format);
    setVal('ldmReference', glossary.LDM || glossary.ldm);
    setVal('businessLogic', glossary.Business_Logic || glossary.businessLogic);
    setVal('examples', glossary.Examples || glossary.examples);
    
    // Populate parent
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput && glossary.Parent_ID) {
        // Load parent glossary name
        loadParentGlossaryName(glossary.Parent_ID);
    } else if (parentNameInput) {
        parentNameInput.value = '';
        parentNameInput.dataset.parentId = '';
    }
    
    // Set dropdowns
    setSel('budgStatus', glossary.Status || glossary.status);
    setSel('lifecycle', glossary.Lifecycle || glossary.lifecycle);
    setSel('budgViewing', glossary.Is_Public || glossary.isPublic);
    setSel('type', glossary.Type || glossary.type);
    setSel('securityClassification', glossary.Security_Classification || glossary.security);
    // KDE is optional - only set if value is a valid number (not null, undefined, or boolean)
    const kdeValue = glossary.KDE || glossary.kde;
    if (kdeValue != null && typeof kdeValue !== 'boolean' && (typeof kdeValue === 'number' || (typeof kdeValue === 'string' && kdeValue.trim() !== '' && !isNaN(kdeValue)))) {
        setSel('kde', kdeValue);
    } else {
        // Clear KDE selection - leave it empty (not mandatory)
        const kdeSelect = document.getElementById('kde');
        if (kdeSelect) {
            kdeSelect.value = '';
        }
    }
    
    // Set CIA ratings
    setSel('confidentialityRating', glossary.Confidentiality_Rating || glossary.confidentiality_Rating);
    setSel('integrityRating', glossary.Integrity_Rating || glossary.integrity_Rating);
    setSel('availabilityRating', glossary.Availability_Rating || glossary.availability_Rating);
    
    // Set segment value if available (check for null/undefined/-1, not falsy, since 0 could be valid)
    if (segmentField) {
        const segmentId = glossary.segmentId ?? glossary.segment_id ?? glossary.Segment_ID;
        if (segmentId != null && segmentId !== undefined && segmentId !== -1) {
            if (window.__BUDG_DEBUG__) console.log('Setting segment field to:', segmentId);
            segmentField.setValue(parseInt(segmentId, 10));
        } else if (segmentId === -1 || segmentId == null) {
            console.warn('⚠️ Glossary has no segment assignment (segmentId: ' + segmentId + ')');
        } else {
            console.warn('No segment ID found in glossary data:', glossary);
        }
        if (typeof segmentField.getValue === 'function') {
            originalGlossarySegmentId = normalizeGlossarySegmentId(segmentField.getValue());
        } else {
            originalGlossarySegmentId = normalizeGlossarySegmentId(segmentId);
        }
    }
    
    if (window.__BUDG_DEBUG__) console.log('Form populated successfully');
    
    // ⚠️ CRITICAL: Re-apply DFCR locks after populating form
    // This ensures locks are applied based on current Auto CR state from backend
    const glossaryId = parseId();
    if (glossaryId && window.reapplyDFCRLocks) {
        // Use setTimeout to ensure DOM is ready
        setTimeout(async () => {
            await window.reapplyDFCRLocks('Glossary', glossaryId);
        }, 50);
    }
}

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
    
    // Add to page
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

// Form dirty state management
function markAsDirty(e) {
    if (e && e.isTrusted === false) return;
    isDirty = true;
    hasFormChanges = true;
    updateSaveButtons();
}

function markAsClean() {
    isDirty = false;
    hasFormChanges = false;
    updateSaveButtons();
}


function updateSaveButtons() {
    const saveBtn = document.getElementById('saveBtn');
    const saveAndCloseBtn = document.getElementById('saveAndCloseBtn');
    
    if (isDirty) {
        saveBtn?.classList.add('btn-warning');
        saveAndCloseBtn?.classList.add('btn-warning');
    } else {
        saveBtn?.classList.remove('btn-warning');
        saveAndCloseBtn?.classList.remove('btn-warning');
    }
}

async function saveGlossaryActiveTab(closeAfter = false) {
    const activeTabEl = document.querySelector('.tab.active');
    const activeTab = activeTabEl ? activeTabEl.getAttribute('data-tab') : 'summary';
    const id = parseId();

    if (activeTab === 'relationships') {
        const hasRelChanges = checkRelationshipsChanges();
        if (!hasRelChanges) {
            showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
            return;
        }
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(b => { if (b) { b.disabled = true; b.dataset._txt = b.textContent; b.textContent = 'Saving...'; } });
        try {
            const ok = await saveRelationshipsData(id);
            if (ok) {
                showSuccessMessage('UPDATES SAVED');
            } // if not ok, an alert with the prevention/error message has already been shown
            if (closeAfter) {
                window.onbeforeunload = null;
                setTimeout(() => { window.location.href = `/view/glossary/${id}`; }, 1500);
            }
        } catch (err) {
            console.error('Error saving relationships:', err);
            alert(err?.message || 'Failed to save relationships');
        } finally {
            buttons.forEach(b => { if (b) { b.disabled = false; b.textContent = b.dataset._txt || (b.id === 'saveAndCloseBtn' ? 'Save & Close' : 'Save'); } });
        }
        return;
    }

    if (activeTab === 'stakeholders') {
        if (window.GlossaryStakeholderEdit && window.GlossaryStakeholderEdit.saveStakeholders) {
            const success = await window.GlossaryStakeholderEdit.saveStakeholders();
            if (success && closeAfter) {
                window.onbeforeunload = null;
                setTimeout(() => { window.location.href = `/view/glossary/${id}`; }, 1000);
            }
        }
        return;
    }

    if (activeTab === 'impact') {
        const glossaryImpactDirty = typeof window.hasImpactChanges === 'function' && window.hasImpactChanges();
        if (!glossaryImpactDirty) {
            showSuccessMessage(window.I18n ? window.I18n.t('message.noChangesToSave') : 'No changes to save', false);
            return;
        }
        const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
        buttons.forEach(b => { if (b) { b.disabled = true; b.dataset._txt = b.textContent; b.textContent = 'Saving...'; } });
        try {
            const impactResult = await window.saveAllImpactData(id);
            if (impactResult && (impactResult.product?.success !== false && impactResult.client?.success !== false)) {
                if (window.initImpactEdit && id) {
                    await window.initImpactEdit(id);
                }
                console.log('✅ Impact saved successfully');
            }
            showSuccessMessage('UPDATES SAVED');
            if (closeAfter) {
                window.onbeforeunload = null;
                setTimeout(() => { window.location.href = `/view/glossary/${id}`; }, 1500);
            }
        } catch (err) {
            console.error('Error saving impact:', err);
            alert(err?.message || 'Failed to save impact');
        } finally {
            buttons.forEach(b => { if (b) { b.disabled = false; b.textContent = b.dataset._txt || (b.id === 'saveAndCloseBtn' ? 'Save & Close' : 'Save'); } });
        }
        return;
    }

    // summary tab (default) — delegate to existing saveGlossary which handles CR/approval flows
    await saveGlossary(closeAfter);
}

async function saveGlossary(closeAfter = false) {
    console.log('=== SAVING GLOSSARY (ALL TABS) ===');
    
    const id = parseId();
    const buttons = [document.getElementById('saveBtn'), document.getElementById('saveAndCloseBtn')];
    
    // Always save summary data first (if there are form changes)
    const hasRelationshipChanges = checkRelationshipsChanges();
    const shouldSaveGlossary = hasFormChanges;
    const shouldSaveRelationships = hasRelationshipChanges;

    console.log('Save flags - shouldSaveGlossary:', shouldSaveGlossary, 'shouldSaveRelationships:', shouldSaveRelationships);
    
    // Sync rich-text editor content back to textareas before reading
    if (typeof syncAdvancedRichTextToTextarea === 'function') {
        syncAdvancedRichTextToTextarea('businessLogic');
        syncAdvancedRichTextToTextarea('examples');
    }

    // Get form values
    const glossaryName = document.getElementById('glossaryName')?.value?.trim();
    const glossaryDefinition = document.getElementById('glossaryDefinition')?.value?.trim();
    const glossaryRef = document.getElementById('glossaryRef')?.value?.trim();
    const formatType = parseInt(document.getElementById('formatType')?.value || '', 10);
    const aliasNames = document.getElementById('aliasNames')?.value?.trim();
    const formatDescription = document.getElementById('formatDescription')?.value?.trim();
    const ldmReference = document.getElementById('ldmReference')?.value?.trim();
    const businessLogic = document.getElementById('businessLogic')?.value?.trim();
    const examples = document.getElementById('examples')?.value?.trim();
    
    // Get dropdown values
    const budgStatus = parseInt(document.getElementById('budgStatus')?.value || '', 10);
    const lifecycle = parseInt(document.getElementById('lifecycle')?.value || '', 10);
    const budgViewing = parseInt(document.getElementById('budgViewing')?.value || '', 10);
    const type = parseInt(document.getElementById('type')?.value || '', 10);
    const securityClassification = parseInt(document.getElementById('securityClassification')?.value || '', 10);
    const kde = parseInt(document.getElementById('kde')?.value || '', 10);
    const confidentialityRating = parseInt(document.getElementById('confidentialityRating')?.value || '', 10);
    const integrityRating = parseInt(document.getElementById('integrityRating')?.value || '', 10);
    const availabilityRating = parseInt(document.getElementById('availabilityRating')?.value || '', 10);
    
    // Get parent ID
    const parentNameInput = document.getElementById('parentName');
    const parentId = parentNameInput?.dataset.parentId ? parseInt(parentNameInput.dataset.parentId, 10) : null;
    
    console.log('Form values:', {
        glossaryName,
        glossaryDefinition,
        glossaryRef,
        formatType,
        aliasNames,
        formatDescription,
        ldmReference,
        businessLogic,
        examples,
        budgStatus,
        lifecycle,
        budgViewing,
        type,
        securityClassification,
        kde,
        confidentialityRating,
        integrityRating,
        availabilityRating,
        parentId
    });
    
    // Validate required fields
    const required = [
        { ok: !!glossaryName, field: 'Name' },
        { ok: !!glossaryDefinition, field: 'Definition' },
        { ok: Number.isInteger(formatType), field: 'Format Type' },
        { ok: Number.isInteger(budgStatus), field: 'BUDG Status' },
        { ok: Number.isInteger(lifecycle), field: 'Lifecycle' },
        { ok: Number.isInteger(budgViewing), field: 'BUDG Viewing' },
        { ok: Number.isInteger(type), field: 'Type' },
        { ok: Number.isInteger(securityClassification), field: 'Security Classification' }
    ];
    
    if (segmentField && !segmentField.validate()) {
        return false;
    }

    const missing = required.filter(r => !r.ok).map(r => r.field);
    if (missing.length > 0) {
        alert(`Please fill in the following required fields: ${missing.join(', ')}`);
        return false;
    }

    // Disable buttons only after passing validation
    buttons.forEach(b => { 
        if (b) { 
            b.disabled = true; 
            b.dataset._txt = b.textContent; 
            b.textContent = 'Saving...'; 
        }
    });
    
    // Prepare payload - using field names expected by the servlet
    const payload = {
        name: glossaryName,
        description: glossaryDefinition,
        ref_number: glossaryRef || null,
        format_type: Number.isInteger(formatType) ? formatType : null,
        aliases: aliasNames ? aliasNames.split(',').map(alias => alias.trim()).filter(alias => alias) : [],
        format: formatDescription || null,
        ldm: ldmReference || null,
        business_logic: businessLogic || null,
        examples: examples || null,
        parent_id: parentId,
        status: Number.isInteger(budgStatus) ? budgStatus : null,
        lifecycle: Number.isInteger(lifecycle) ? lifecycle : null,
        is_public: Number.isInteger(budgViewing) ? budgViewing : null,
        type: Number.isInteger(type) ? type : null,
        security_classification: Number.isInteger(securityClassification) ? securityClassification : null,
        kde: Number.isInteger(kde) ? kde : null,
        confidentiality_rating: Number.isInteger(confidentialityRating) ? confidentialityRating : null,
        integrity_rating: Number.isInteger(integrityRating) ? integrityRating : null,
        availability_rating: Number.isInteger(availabilityRating) ? availabilityRating : null,
        segmentId: segmentField ? segmentField.getValue() : null
    };
    
    console.log('Payload to send:', payload);
    
    try {
        // Buttons already set to Saving... above; do not overwrite dataset._txt here or restore will show "Saving..." again
        const id = parseId();
        
        // If only relationships have changes, skip the main glossary update
        let response = null;
        if (shouldSaveGlossary) {
            console.log('=== CALLING updateGlossary API ===');
            console.log('ID:', id);
            console.log('Payload:', payload);
            
            response = await window.BUDG_API_SERVICE.updateGlossary(id, payload);
            console.log('Save response:', response);
            
            // Check if a CR was auto-created (pending changes mode)
            if (response && (response.pendingChanges === true || response.changeRequestId)) {
                console.log('[Glossary Edit] CR was auto-created, reloading with view=changes');
                
                // Save custom fields before reloading
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved (pending changes path)');
                    } catch (cfError) {
                        console.error('Error saving custom fields:', cfError);
                    }
                }
                
                // IMPORTANT: Save Strategic Sources BEFORE reloading (they need to be saved as pending changes)
                try {
                    console.log('[Glossary Edit] Saving strategic sources before reloading...');
                    const strategicSourceSaved = await saveStrategicSourceData(id);
                    if (strategicSourceSaved === false) {
                        // Validation failed, don't proceed
                        buttons.forEach(b => { 
                            if (b) { 
                                b.disabled = false; 
                                if (b.dataset._txt) {
                                    b.textContent = b.dataset._txt;
                                } else {
                                    if (b.id === 'saveBtn') {
                                        b.textContent = 'Save';
                                    } else if (b.id === 'saveAndCloseBtn') {
                                        b.textContent = 'Save & Close';
                                    }
                                }
                            }
                        });
                        return false;
                    }
                    console.log('[Glossary Edit] Strategic sources saved successfully');
                } catch (error) {
                    console.error('[Glossary Edit] Error saving strategic sources:', error);
                    // Continue anyway - don't block the reload
                }
                
                // Reload the page with view=changes to show pending changes
                editViewMode = 'changes';
                await loadGlossary(id, 'changes');
                // Re-initialize active tabs with changes view
                const activeTab = document.querySelector('.tab.active');
                const currentTab = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                if (currentTab === 'stakeholders' && window.GlossaryStakeholderEdit) {
                    window.GlossaryStakeholderEdit.init(id, 'changes');
                } else if (currentTab === 'impact' && window.initImpactEdit) {
                    window.initImpactEdit(id, 'changes');
                }
                // Reload strategic sources with changes view
                await loadStrategicSourceData(id, 'changes');
                
                // Mark form as clean after successful save (to prevent "Leave site?" warning)
                markAsClean();
                
                showSuccessMessage('Changes saved as pending. They will apply when the Change Request is completed.');
                // Restore button states
                buttons.forEach(b => {
                    if (b) {
                        b.disabled = false;
                        if (b.dataset._txt) {
                            b.textContent = b.dataset._txt;
                        } else {
                            if (b.id === 'saveBtn') {
                                b.textContent = 'Save';
                            } else if (b.id === 'saveAndCloseBtn') {
                                b.textContent = 'Save & Close';
                            }
                        }
                    }
                });
                if (closeAfter) {
                    window.onbeforeunload = null;
                    setTimeout(() => {
                        window.location.href = `/view/glossary/${id}`;
                    }, 3000);
                }
                return true;
            }
            
            if (response && response.success === false) {
                // Check if it's a lock error
                if (response.locked) {
                    const lockedBy = response.lockedBy || 'another user';
                    const isPermanent = response.isPermanent || false;
                    const message = `This glossary is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`;
                    showSuccessMessage(message, true);
                    // Release our lock attempt
                    if (window.currentLockManager) {
                        await window.currentLockManager.releaseLock();
                    }
                    window.onbeforeunload = null;
                    setTimeout(() => {
                        window.location.href = `/view/glossary/${id}`;
                    }, 3000);
                    return false;
                }
                throw new Error(response.message || 'Update failed');
            }
            
            // Check if changes are pending approval (workflow enabled)
            if (response && response.pendingApproval) {
                console.log('Changes saved as pending - awaiting approval');
                console.log('Change Request ID:', response.changeRequestId);
                
                // Save custom fields before handling approval flow
                if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                    try {
                        await window.customFieldsContext.saveValues(id);
                        console.log('✅ Custom fields saved (pending approval path)');
                    } catch (cfError) {
                        console.error('Error saving custom fields:', cfError);
                    }
                }
                
                // IMPORTANT: Save Strategic Sources as pending changes (they need to be tracked in the CR)
                try {
                    console.log('[Glossary Edit] Saving strategic sources as pending changes...');
                    const strategicSourceSaved = await saveStrategicSourceData(id);
                    if (strategicSourceSaved === false) {
                        // Validation failed, don't proceed
                        buttons.forEach(b => { 
                            if (b) { 
                                b.disabled = false; 
                                if (b.dataset._txt) {
                                    b.textContent = b.dataset._txt;
                                } else {
                                    if (b.id === 'saveBtn') {
                                        b.textContent = 'Save';
                                    } else if (b.id === 'saveAndCloseBtn') {
                                        b.textContent = 'Save & Close';
                                    }
                                }
                            }
                        });
                        return false;
                    }
                    console.log('[Glossary Edit] Strategic sources saved as pending changes');
                } catch (error) {
                    console.error('[Glossary Edit] Error saving strategic sources:', error);
                    // Continue anyway - don't block the save
                }
                
                // Restore button states before showing success message
                buttons.forEach(b => {
                    if (b) {
                        b.disabled = false;
                        if (b.dataset._txt) {
                            b.textContent = b.dataset._txt;
                        } else {
                            if (b.id === 'saveBtn') {
                                b.textContent = 'Save';
                            } else if (b.id === 'saveAndCloseBtn') {
                                b.textContent = 'Save & Close';
                            }
                        }
                    }
                });
                
                // Mark form as clean after successful save (to prevent "Leave site?" warning)
                markAsClean();
                
                if (closeAfter) {
                    showSuccessMessage('CHANGES SAVED AS PENDING - AWAITING APPROVAL');
                    // Release lock after successful save
                    if (window.currentLockManager) {
                        await window.currentLockManager.releaseLock();
                    }
                    window.onbeforeunload = null;
                    setTimeout(() => {
                        console.log('Redirecting to glossary view with ID:', id);
                        window.location.href = `/view/glossary/${id}`;
                    }, 1500);
                } else {
                    showSuccessMessage('CHANGES SAVED AS PENDING - AWAITING APPROVAL');
                }
                
                // Strategic sources are now saved as pending changes above
                return true;
            }
        } else {
            console.log('=== SKIPPING GLOSSARY UPDATE (only relationship changes) ===');
            // Create a mock success response to continue with relationship save
            response = { success: true, id: id };
        }
        
        // Continue with saving relationships and other data if glossary save was successful or skipped
        if (response && (response.success || response.id || !shouldSaveGlossary)) {
            console.log('Glossary saved successfully');
            
            // Handle pending parent segment move (if user confirmed hierarchy conflict)
            if (window.pendingParentSegmentMove) {
                try {
                    console.log('Moving parent to child segment:', window.pendingParentSegmentMove);
                    const moveResponse = await fetch('/api/segments/move-parent', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        credentials: 'include',
                        body: JSON.stringify(window.pendingParentSegmentMove)
                    });
                    
                    if (moveResponse.ok) {
                        console.log('✅ Parent moved to child segment successfully');
                    } else {
                        console.error('❌ Failed to move parent to child segment');
                    }
                } catch (error) {
                    console.error('Error moving parent segment:', error);
                } finally {
                    // Clear the pending move
                    window.pendingParentSegmentMove = null;
                }
            }
            
            // Save custom fields if context exists
            if (window.customFieldsContext && window.customFieldsContext.saveValues) {
                try {
                    await window.customFieldsContext.saveValues(id);
                    console.log('✅ Custom fields saved successfully');
                } catch (error) {
                    console.error('Error saving custom fields:', error);
                }
            }
            
            // Save Strategic Source data with validation
            const strategicSourceSaved = await saveStrategicSourceData(id);
            if (strategicSourceSaved === false) {
                // Validation failed, don't proceed with closing
                buttons.forEach(b => { 
                    if (b) { 
                        b.disabled = false; 
                        if (b.dataset._txt) {
                            b.textContent = b.dataset._txt;
                        } else {
                            if (b.id === 'saveBtn') {
                                b.textContent = 'Save';
                            } else if (b.id === 'saveAndCloseBtn') {
                                b.textContent = 'Save & Close';
                            }
                        }
                    }
                });
                return false;
            }
            
            // Save Relationships data — skip: handled per-tab when on relationships tab
            
            // Save stakeholders data — skip: handled per-tab when on stakeholders tab
            
            // Save impact — skip: handled per-tab when on impact tab
            // If segment changed, reload impact so it stays valid after summary save
            if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                try {
                    const currentGlossarySegment = normalizeGlossarySegmentId(payload.segmentId ?? (segmentField && segmentField.getValue()));
                    const glossarySegmentChanged = currentGlossarySegment !== normalizeGlossarySegmentId(originalGlossarySegmentId);
                    if (glossarySegmentChanged && window.initImpactEdit) {
                        console.log('=== Segment changed; reloading glossary impact from server ===');
                        await window.initImpactEdit(id, editViewMode);
                    }
                } catch (impactError) {
                    console.error('Error reloading impact after segment change:', impactError);
                }
            }
            
            // Mark form as clean after successful save
            markAsClean();
            originalGlossarySegmentId = normalizeGlossarySegmentId(payload.segmentId ?? (segmentField && segmentField.getValue()));
            
            // Release lock after successful save
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }
            
            // Restore button states before showing success message
            buttons.forEach(b => { 
                if (b) { 
                    b.disabled = false; 
                    if (b.dataset._txt) {
                        b.textContent = b.dataset._txt;
                    } else {
                        // Fallback: restore default text based on button ID
                        if (b.id === 'saveBtn') {
                            b.textContent = 'Save';
                        } else if (b.id === 'saveAndCloseBtn') {
                            b.textContent = 'Save & Close';
                        }
                    }
                }
            });
            
            showSuccessMessage('UPDATES SAVED');
            
            if (closeAfter) { 
                window.onbeforeunload = null;
                setTimeout(() => {
                    console.log('Redirecting to glossary view with ID:', id);
                    window.location.href = `/view/glossary/${id}`;
                }, 1500);
            }
            
            return true;
        } else {
            console.error('Save failed:', response);
            // Restore button states before showing error
            buttons.forEach(b => { 
                if (b) { 
                    b.disabled = false; 
                    if (b.dataset._txt) {
                        b.textContent = b.dataset._txt;
                    } else {
                        if (b.id === 'saveBtn') {
                            b.textContent = 'Save';
                        } else if (b.id === 'saveAndCloseBtn') {
                            b.textContent = 'Save & Close';
                        }
                    }
                }
            });
            let detail = '';
            try {
                if (response && typeof response.json === 'function') {
                    const errorData = await response.json();
                    detail = errorData.error || errorData.message || '';
                }
            } catch (_) { /* ignore */ }
            if (typeof window.formatSaveError === 'function' && detail) {
                alert(window.formatSaveError({ body: { error: detail }, message: detail }));
            } else if (detail) {
                alert((window.I18n ? window.I18n.t('common.messages.errorSaving', { error: detail }) : null) || ('Failed to save glossary: ' + detail));
            } else {
                alert(window.I18n ? window.I18n.t('common.messages.failedToSave') : 'Failed to save glossary. Please try again.');
            }
            return false;
        }
    } catch (error) {
        console.error('Error saving glossary:', error);
        // Restore button states before showing error
        buttons.forEach(b => { 
            if (b) { 
                b.disabled = false; 
                if (b.dataset._txt) {
                    b.textContent = b.dataset._txt;
                } else {
                    if (b.id === 'saveBtn') {
                        b.textContent = 'Save';
                    } else if (b.id === 'saveAndCloseBtn') {
                        b.textContent = 'Save & Close';
                    }
                }
            }
        });
        const errorMsg = error?.body?.error || error?.body?.message || error?.message || 'An error occurred while saving. Please try again.';
        alert(errorMsg);
        return false;
    } finally {
        // Always restore button states, even if there was an error
        buttons.forEach(b => { 
            if (b) { 
                b.disabled = false; 
                if (b.dataset._txt) {
                    b.textContent = b.dataset._txt;
                } else {
                    // Fallback: restore default text based on button ID
                    if (b.id === 'saveBtn') {
                        b.textContent = 'Save';
                    } else if (b.id === 'saveAndCloseBtn') {
                        b.textContent = 'Save & Close';
                    }
                }
            }
        });
    }
}

function setupEventListeners() {
    console.log('Setting up event listeners...');
    
    // Tab switching
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const targetTab = this.getAttribute('data-tab');
            switchTab(targetTab);
        });
    });
    
    // Save buttons
    const saveBtn = document.getElementById('saveBtn');
    const saveCloseBtn = document.getElementById('saveAndCloseBtn');
    const closeBtn = document.getElementById('closeBtn');

        if (window.__BUDG_DEBUG__) console.log('Setting up buttons:', {
        saveBtn: !!saveBtn,
        saveCloseBtn: !!saveCloseBtn,
        closeBtn: !!closeBtn
    });

    if (saveBtn) saveBtn.addEventListener('click', async () => {
        await saveGlossaryActiveTab(false);
    });

    if (saveCloseBtn) saveCloseBtn.addEventListener('click', async () => {
        await saveGlossaryActiveTab(true);
    });
    
    // Save & Submit button handler - for edit workflow on existing objects
    const saveSubmitBtn = document.getElementById('saveAndSubmitBtn');
    if (saveSubmitBtn) {
        saveSubmitBtn.addEventListener('click', async () => {
            console.log('Save & Submit clicked - will save and create change request');
            const glossaryId = parseId();
            const glossaryName = document.getElementById('glossaryName')?.value || 'Glossary';
            
            // Get active tab
            const activeTab = document.querySelector('.tab.active');
            const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
            
            let saveSuccess = false;
            
            // Save based on active tab
            if (tabName === 'stakeholders') {
                // Save stakeholders
                if (window.GlossaryStakeholderEdit && window.GlossaryStakeholderEdit.saveStakeholders) {
                    saveSuccess = await window.GlossaryStakeholderEdit.saveStakeholders();
                }
            } else if (tabName === 'impact') {
                // Save impact data
                if (window.saveAllImpactData && typeof window.saveAllImpactData === 'function') {
                    const impactResult = await window.saveAllImpactData();
                    saveSuccess = impactResult && (impactResult.product?.success !== false && impactResult.client?.success !== false);
                } else {
                    saveSuccess = true; // No changes to save
                }
            } else {
                // Save glossary details (summary tab)
                saveSuccess = await saveGlossary(false);
            }
            
            if (saveSuccess) {
                // Check if a CR was auto-created in the save response
                let changeRequestId = null;
                
                // Create a change request for the edit
                try {
                    const crPayload = {
                        title: `Edit request: ${glossaryName}`,
                        summary: `Auto-generated CR for editing Glossary: ${glossaryName}`,
                        facetType: 'glossary',
                        facetId: String(glossaryId),
                        facetName: glossaryName,
                        type: dfcrInfo?.defaultCrTypeId || 1,
                        urgency: dfcrInfo?.defaultCrUrgencyId || 1,
                        severity: dfcrInfo?.defaultCrSeverityId || 1
                    };
                    
                    console.log('Creating change request with payload:', crPayload);
                    
                    const crResponse = await fetch('/api/changerequests', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        credentials: 'include',
                        body: JSON.stringify(crPayload)
                    });
                    
                    if (crResponse.ok) {
                        const crResult = await crResponse.json();
                        console.log('Change request created:', crResult);
                        alert('Changes saved and submitted for approval. Change Request created.');
                        window.onbeforeunload = null;
                        window.location.href = `/view/glossary/${glossaryId}`;
                    } else {
                        const errorText = await crResponse.text();
                        console.error('Failed to create change request:', crResponse.status, errorText);
                        alert('Changes saved but failed to create change request: ' + errorText);
                    }
                } catch (error) {
                    console.error('Error creating change request:', error);
                    alert('Changes saved but failed to create change request: ' + error.message);
                }
                window.onbeforeunload = null;
                window.location.href = `/view/glossary/${glossaryId}`;
            }
        });
    }
    if (closeBtn) {
        console.log('Close button found, adding event listener');
        
        // Add multiple event listeners to ensure it works
        closeBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('Close button clicked!');
            handleClose();
        });
        
        closeBtn.addEventListener('mousedown', function(e) {
            console.log('Close button mousedown');
        });
        
        closeBtn.addEventListener('mouseup', function(e) {
            console.log('Close button mouseup');
        });
        
        // Also add a direct onclick handler as backup
        closeBtn.onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log('Close button onclick (backup)');
            handleClose();
            return false;
        };
        
    } else {
        console.error('Close button not found!');
    }
    
    // Function to handle close action
    async function handleClose() {
        const id = parseId();
        console.log('handleClose - Parsed ID:', id);
        console.log('handleClose - Current URL:', window.location.href);
        
        // Release lock before closing - use synchronous XHR to ensure it completes
        if (window.currentLockManager && window.currentLockManager.isLockAcquired) {
            console.log('handleClose - Releasing lock synchronously...');
            try {
                // Use synchronous XHR to ensure lock is released before navigation
                const xhr = new XMLHttpRequest();
                xhr.open('DELETE', `/api/lock/glossary/${id}`, false); // false = synchronous
                xhr.setRequestHeader('Content-Type', 'application/json');
                xhr.withCredentials = true;
                xhr.send();
                console.log('handleClose - Lock release response:', xhr.status, xhr.responseText);
            } catch (e) {
                console.error('handleClose - Error releasing lock:', e);
            }
        } else {
            console.log('handleClose - No lock to release (isLockAcquired:', window.currentLockManager?.isLockAcquired, ')');
        }
        
        window.onbeforeunload = null;
        if (id) {
            const targetUrl = `/view/glossary/${id}`;
            console.log('handleClose - Redirecting to:', targetUrl);
            window.location.href = targetUrl;
        } else {
            console.error('handleClose - No ID found, redirecting to glossary list');
            window.location.href = '/view/glossary/glossary.html';
        }
    }
    
    // Form change tracking
    const formInputs = document.querySelectorAll('input, select, textarea');
    formInputs.forEach(input => {
        input.addEventListener('input', markAsDirty);
        input.addEventListener('change', markAsDirty);
    });
    
    // Set beforeunload handler
    window.onbeforeunload = function(e) {
        if (isDirty || hasFormChanges) {
            console.log('onbeforeunload triggered - showing warning');
            e.preventDefault();
            e.returnValue = '';
        }
    };
    
    // Add keyboard shortcut for close (Escape key)
    document.addEventListener('keydown', async function(e) {
        if (e.key === 'Escape') {
            console.log('Escape key pressed, closing...');
            const id = parseId();
            // Release lock before canceling
            if (window.currentLockManager) {
                await window.currentLockManager.releaseLock();
            }
            window.onbeforeunload = null;
            if (id) {
                window.location.href = `/view/glossary/${id}`;
            } else {
                window.location.href = '/view/glossary/glossary.html';
            }
        }
    });
    
    // Show editor buttons – advanced rich text editor
    const showBusinessLogicEditorBtn = document.getElementById('showBusinessLogicEditorBtn');
    if (showBusinessLogicEditorBtn) {
        showBusinessLogicEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('businessLogic', showBusinessLogicEditorBtn);
        });
    }
    const showExamplesEditorBtn = document.getElementById('showExamplesEditorBtn');
    if (showExamplesEditorBtn) {
        showExamplesEditorBtn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            toggleAdvancedRichTextEditor('examples', showExamplesEditorBtn);
        });
    }

    // Initialize Strategic Source Table
    initializeStrategicSourceTable();
    
    // Parent name input click handler
    const parentNameInput = document.getElementById('parentName');
    if (parentNameInput) {
        parentNameInput.addEventListener('click', function() {
            console.log('Parent name input clicked');
            selectParent();
        });
    }
    
    // Parent selection button
    const selectParentBtn = document.getElementById('selectParentBtn');
    if (selectParentBtn) {
        selectParentBtn.addEventListener('click', selectParent);
    }
    
    // Clear parent button
    const clearParentBtn = document.getElementById('clearParentBtn');
    if (clearParentBtn) {
        clearParentBtn.addEventListener('click', clearParent);
    }
    
    // Parent selection modal events
    const closeParentModal = document.getElementById('closeParentModal');
    if (closeParentModal) {
        closeParentModal.addEventListener('click', closeParentSelectionModal);
    }
    
    const cancelParentSelection = document.getElementById('cancelParentSelection');
    if (cancelParentSelection) {
        cancelParentSelection.addEventListener('click', closeParentSelectionModal);
    }
    
    // Parent search functionality
    const parentSearchInput = document.getElementById('parentSearchInput');
    if (parentSearchInput) {
        parentSearchInput.addEventListener('input', function() {
            const searchTerm = this.value.toLowerCase();
            const glossaryItems = document.querySelectorAll('.glossary-item');
            
            glossaryItems.forEach(item => {
                const name = item.querySelector('.glossary-name')?.textContent.toLowerCase() || '';
                const description = item.querySelector('.glossary-description')?.textContent.toLowerCase() || '';
                
                if (name.includes(searchTerm) || description.includes(searchTerm)) {
                    item.style.display = 'block';
                } else {
                    item.style.display = 'none';
                }
            });
        });
    }
    
    // Make strategic source functions globally available
    window.addStrategicSource = addStrategicSource;
    window.removeStrategicSource = removeStrategicSource;
    window.loadDatasetsForSystem = loadDatasetsForSystem;
    window.saveStrategicSourceOnly = saveStrategicSourceOnly;
    window.validateStrategicSourceRow = validateStrategicSourceRow;
    window.markRowAsChanged = markRowAsChanged;
    window.hasUnsavedChanges = hasUnsavedChanges;
    window.loadDatasetsForExistingRows = loadDatasetsForExistingRows;
    
    // Make relationship functions globally available
    window.addRelationship = addRelationship;
    window.removeRelationship = removeRelationship;
    window.updateGlossaryType = updateGlossaryType;
    window.getGlossaryTypeById = getGlossaryTypeById;
    
    // Store global variables
    window.globalGlossaryList = globalGlossaryList;
}

function switchTab(tabName) {
    console.log('Switching to tab:', tabName);
    
    // Update tab buttons
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        if (tab.getAttribute('data-tab') === tabName) {
            tab.classList.add('active');
        } else {
            tab.classList.remove('active');
        }
    });
    
    // Update tab content
    const containers = [
        'summaryContainer',
        'relationshipsContainer',
        'stakeholdersContainer',
        'impactContainer'
    ];
    
    containers.forEach(containerId => {
        const container = document.getElementById(containerId);
        if (container) {
            if (containerId === `${tabName}Container`) {
                container.style.display = 'block';
                container.classList.add('active');
                
                // Initialize stakeholders edit if switching to stakeholders tab
                if (tabName === 'stakeholders' && window.GlossaryStakeholderEdit && !window.GlossaryStakeholderEdit._initialized) {
                    const glossaryId = parseId();
                    if (glossaryId) {
                        window.GlossaryStakeholderEdit.init(glossaryId, editViewMode);
                        window.GlossaryStakeholderEdit._initialized = true;
                    }
                }
                
                // Initialize Impact edit if switching to impact tab (only once)
                if (tabName === 'impact' && window.initImpactEdit && !window._impactEditInitialized) {
                    const glossaryId = parseId();
                    if (glossaryId) {
                        window.initImpactEdit(glossaryId, editViewMode);
                        window._impactEditInitialized = true;
                    }
                }
                
                // Initialize relationships edit if switching to relationships tab
                if (tabName === 'relationships') {
                    const glossaryId = parseId();
                    console.log('Relationships tab clicked, glossaryId:', glossaryId);
                    if (glossaryId) {
                        console.log('Calling loadRelationshipsData with ID:', glossaryId);
                        loadRelationshipsData(glossaryId).catch(error => {
                            console.error('Error loading relationships data:', error);
                        });
                    } else {
                        console.error('No glossary ID found when switching to relationships tab');
                    }
                }
            } else {
                container.style.display = 'none';
                container.classList.remove('active');
            }
        }
    });
}

async function initializePage() {
    const id = parseId();
    if (!id) {
        console.error('No glossary ID found');
        return;
    }
    
    try {
        if (window.__BUDG_DEBUG__) console.log('=== INITIALIZING PAGE ===');
        if (window.__BUDG_DEBUG__) console.log('Loading lookups and glossary data...');
        // Load lookups first, then glossary data
        if (window.__BUDG_DEBUG__) console.log('Step 1: Loading lookups...');
        await loadGlossaryEditLookups();
        
        // Under active CR, edit page must load pending (nobject_id) data
        editViewMode = await determineEditViewMode(id);
        console.log('[Glossary Edit] View mode:', editViewMode);
        
        // Initialize segment field BEFORE loading data so it's available when populateForm runs
        if (window.SegmentField) {
            try {
                segmentField = await SegmentField.init('segmentFieldContainer', {
                    label: 'Segment',
                    required: true,
                    // Removed defaultValue - let API data set the correct value
                    sectionTitle: 'SEGMENTATION',
                    objectType: 'Glossary',
                    fieldId: 'glossarySegment',
                    errorId: 'glossarySegmentError',
                    onChange: async (selectedSegmentId, previousSegmentId) => {
                        const parentIsValid = await refreshGlossaryParentForSelectedSegment(previousSegmentId);
                        await refreshStrategicSourceSystemsForSelectedSegment();
                        return parentIsValid;
                    }
                });
                console.log('Segment field initialized');
                
                // If glossary data was loaded before segmentField was ready, set the value now
                if (window._pendingGlossaryData) {
                    const glossary = window._pendingGlossaryData;
                    const segmentId = glossary.segmentId ?? glossary.segment_id ?? glossary.Segment_ID;
                    if (segmentId != null && segmentId !== undefined) {
                        console.log('Setting segment field value from pending data:', segmentId);
                        segmentField.setValue(parseInt(segmentId, 10));
                    }
                    delete window._pendingGlossaryData;
                }
            } catch (error) {
                console.error('Error initializing segment field:', error);
            }
        }
        
        if (window.__BUDG_DEBUG__) console.log('Step 2: Loading glossary data...');
        await loadGlossary(id, editViewMode);
        
        // Initialize custom fields
        if (window.CustomFields) {
            try {
                window.customFieldsContext = await window.CustomFields.initForm({
                    facetId: 'Glossary',
                    containerId: 'customFieldsContainer',
                    mode: 'edit',
                    objectId: id,
                    view: editViewMode === 'changes' ? 'changes' : null
                });
                console.log('Custom fields initialized:', window.customFieldsContext);
            } catch (error) {
                console.error('Error initializing custom fields:', error);
            }
        }
        
        // Initialize Documents section
        if (window.__BUDG_DEBUG__) console.log('Step 3: Loading documents...');
        await loadDocuments(id, editViewMode);
        
        if (window.__BUDG_DEBUG__) console.log('Setting up event listeners...');
        // Setup event listeners after data is loaded
        setupEventListeners();
        
        
        if (window.__BUDG_DEBUG__) console.log('Glossary edit page initialized successfully');
        
        // Check if we should open a specific tab from URL parameter
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        const isStakeholderOnly = urlParams.get('stakeholderOnly') === 'true';
        
        if (tabParam) {
            setTimeout(() => {
                switchTab(tabParam);
            }, 100);
        }
        
        // Stakeholder-only mode: disable all other tabs when opened from view page with auto CR active
        if (isStakeholderOnly && tabParam === 'stakeholders') {
            console.log('[Glossary Edit] Stakeholder-only mode enabled - locking other tabs');
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
    } catch (error) {
        console.error('Error initializing glossary edit page:', error);
    }
}

// Parent Selection Functions
async function refreshGlossaryParentForSelectedSegment(previousSegmentId) {
    const parentNameInput = document.getElementById('parentName');
    const selectedParentId = parseInt(parentNameInput?.dataset?.parentId || '', 10);

    const response = await window.BUDG_API_SERVICE.getGlossaryList();
    const glossaries = Array.isArray(response?.data) ? response.data : (Array.isArray(response) ? response : []);
    allGlossaries = Array.isArray(glossaries) ? glossaries : [];

    if (!Number.isInteger(selectedParentId) || selectedParentId <= 0) {
        return;
    }

    const allowedParentIds = new Set(
        allGlossaries
            .map(g => parseInt(g.ID || g.id, 10))
            .filter(id => Number.isInteger(id) && id > 0)
    );

    if (!allowedParentIds.has(selectedParentId)) {
        alert('This parent is not valid for the selected segment. Please remove the parent first.');
        return false;
    }
    return true;
}

async function selectParent() {
    try {
        const currentGlossaryId = parseId();
        console.log('Opening parent selection modal for glossary ID:', currentGlossaryId);
        
        // Load all glossaries
        console.log('=== CALLING getGlossaryList API ===');
        const response = await window.BUDG_API_SERVICE.getGlossaryList();
        console.log('Loaded glossaries response:', response);
        console.log('Response type:', typeof response);
        console.log('Response keys:', Object.keys(response || {}));
        
        let glossaries = [];
        if (Array.isArray(response)) {
            glossaries = response;
        } else if (response && response.data && Array.isArray(response.data)) {
            glossaries = response.data;
        } else {
            console.warn('Unexpected response format:', response);
            glossaries = [];
        }
        
        console.log('Processed glossaries:', glossaries);
        console.log('Current glossary ID:', currentGlossaryId);
        
        // Log each glossary's parent relationship
        glossaries.forEach((glossary, index) => {
            console.log(`Glossary ${index}: ID=${glossary.ID || glossary.id}, Name="${glossary.Name || glossary.name}", Parent_ID=${glossary.Parent_ID || glossary.parentId || 'undefined'}`);
        });
        
        // Filter out current glossary and its descendants to prevent circular references
        const filteredGlossaries = glossaries.filter(glossary => {
            // Exclude current glossary
            if (glossary.ID === currentGlossaryId || glossary.id === currentGlossaryId) {
                console.log(`Excluding current glossary: ${glossary.Name || glossary.name} (ID: ${glossary.ID || glossary.id})`);
                return false;
            }
            
            // Check if this glossary is a descendant of the current glossary
            if (isDescendantOf(glossary, currentGlossaryId, glossaries)) {
                console.log(`Excluding descendant: ${glossary.Name || glossary.name} (ID: ${glossary.ID || glossary.id})`);
                return false;
            }
            
            console.log(`Including glossary: ${glossary.Name || glossary.name} (ID: ${glossary.ID || glossary.id})`);
            return true;
        });
        
        console.log('Filtered glossaries (excluding current and descendants):', filteredGlossaries);
        console.log(`Excluded ${glossaries.length - filteredGlossaries.length} glossaries (current + descendants)`);
        
        // Show parent selection modal
        showParentSelectionModal(filteredGlossaries);
        
    } catch (error) {
        console.error('Error loading glossaries for parent selection:', error);
        alert('Error loading glossaries. Please try again.');
    }
}

function showParentSelectionModal(glossaries) {
    console.log('Showing parent selection modal with glossaries:', glossaries);
    
    const modal = document.getElementById('parentSelectionModal');
    const glossaryList = document.getElementById('glossaryList');
    const searchInput = document.getElementById('parentSearchInput');
    
    if (!modal) {
        console.error('Parent selection modal not found!');
        return;
    }
    
    if (!glossaryList) {
        console.error('Glossary list container not found!');
        return;
    }
    
    // Clear previous content
    glossaryList.innerHTML = '';
    if (searchInput) {
        searchInput.value = '';
    }
    
    // Render glossaries
    renderGlossaryList(glossaries);
    
    // Show modal
    modal.style.display = 'flex';
    console.log('Modal displayed');
}

function renderGlossaryList(glossaries) {
    const glossaryList = document.getElementById('glossaryList');
    
    console.log('Rendering glossary list with', glossaries.length, 'glossaries');
    
    // Add "No Parent" option
    const noParentItem = document.createElement('div');
    noParentItem.className = 'glossary-item';
    noParentItem.style.borderBottom = '2px solid var(--border-color, #e2e8f0)';
    noParentItem.innerHTML = `
        <div class="glossary-name" style="font-style: italic; color: var(--text-secondary, #64748b);">No Parent</div>
        <div class="glossary-description" style="font-style: italic; color: var(--text-secondary, #64748b);">Remove parent glossary</div>
    `;
    noParentItem.addEventListener('click', () => clearParent());
    glossaryList.appendChild(noParentItem);
    
    if (glossaries.length === 0) {
        const noGlossariesItem = document.createElement('div');
        noGlossariesItem.className = 'no-glossaries';
        noGlossariesItem.textContent = 'No other glossaries found';
        glossaryList.appendChild(noGlossariesItem);
        console.log('No glossaries to render, showing "No other glossaries found"');
        return;
    }
    
    // Render glossary items
    glossaries.forEach((glossary, index) => {
        console.log(`Rendering glossary ${index}:`, glossary);
        const glossaryItem = document.createElement('div');
        glossaryItem.className = 'glossary-item';
        glossaryItem.innerHTML = `
            <div class="glossary-name">${glossary.Name || glossary.name || 'Unnamed'}</div>
            <div class="glossary-description">${glossary.Description || glossary.description || 'No description'}</div>
        `;
        
        glossaryItem.addEventListener('click', () => selectGlossaryAsParent(glossary));
        glossaryList.appendChild(glossaryItem);
    });
    
    console.log('Finished rendering glossary list');
}

async function selectGlossaryAsParent(glossary) {
    console.log('Selected glossary as parent:', glossary);
    
    const parentId = glossary.ID || glossary.id;
    const currentGlossaryId = parseId();
    
    // Get the current segment from the segment field (if exists)
    const segmentField = window.segmentField;
    let currentSegmentId = 1; // Default to Enterprise
    
    if (segmentField && typeof segmentField.getValue === 'function') {
        currentSegmentId = segmentField.getValue();
    }
    
    // Validate segment hierarchy - Per BUDG v7.0-7.2: Parent-child must be in SAME segment
    try {
        const validationResponse = await fetch('/api/segments/validate-hierarchy', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                parentId: parentId,
                childSegmentId: currentSegmentId,
                objectType: 'Glossary'
            })
        });
        
        const validationResult = await validationResponse.json();
        console.log('Segment hierarchy validation result:', validationResult);
        
        if (!validationResult.isValid && validationResult.canProceed) {
            // Show hierarchy conflict warning
            const message = 
                `⚠️ Segment Hierarchy Conflict\n\n` +
                `The selected parent "${glossary.Name || glossary.name}" is in segment "${validationResult.parentSegmentName}" ` +
                `but this glossary is in segment "${validationResult.childSegmentName}".\n\n` +
                `📋 Per BUDG Segmentation Rules (v7.0-7.2):\n` +
                `Parent-child relationships must be within the SAME segment.\n\n` +
                `Click OK to continue and move the PARENT to segment "${validationResult.childSegmentName}" when you save.\n` +
                `Click Cancel to choose a different parent.`;
            
            const userConfirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: message, type: 'warning' })
                : Promise.resolve(confirm(message)));
            
            if (!userConfirmed) {
                console.log('❌ User cancelled parent selection due to segment conflict');
                return; // Don't set the parent
            }
            
            // User confirmed - store that we need to move the parent when saving
            console.log('✅ User confirmed: Parent will be moved to child segment on save');
            window.pendingParentSegmentMove = {
                parentId: parentId,
                targetSegmentId: currentSegmentId,
                objectType: 'Glossary'
            };
        } else if (!validationResult.isValid && !validationResult.canProceed) {
            // Cannot proceed at all
            alert(`Cannot select this parent: ${validationResult.message}`);
            return;
        }
    } catch (error) {
        console.warn('Unable to validate segment hierarchy:', error);
        // Continue anyway on validation errors
    }
    
    // Update parent name input
    const parentNameInput = document.getElementById('parentName');
    parentNameInput.value = glossary.Name || glossary.name || 'Unnamed';
    parentNameInput.dataset.parentId = parentId;
    
    // Mark form as dirty since parent changed
    markAsDirty();
    
    // Close modal
    closeParentSelectionModal();
}

function closeParentSelectionModal() {
    const modal = document.getElementById('parentSelectionModal');
    modal.style.display = 'none';
}

function clearParent() {
    console.log('Clearing parent selection');
    
    // Clear parent name input
    const parentNameInput = document.getElementById('parentName');
    parentNameInput.value = '';
    parentNameInput.dataset.parentId = '';
    
    // Mark form as dirty since parent changed
    markAsDirty();
    
    // Close modal
    closeParentSelectionModal();
}

async function loadParentGlossaryName(parentId) {
    try {
        const response = await window.BUDG_API_SERVICE.getGlossaryById(parentId);
        const parentGlossary = response.data || response;
        
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput && parentGlossary) {
            parentNameInput.value = parentGlossary.Name || parentGlossary.name || 'Unnamed';
            parentNameInput.dataset.parentId = parentId;
        }
    } catch (error) {
        console.error('Error loading parent glossary:', error);
        const parentNameInput = document.getElementById('parentName');
        if (parentNameInput) {
            parentNameInput.value = '';
            parentNameInput.dataset.parentId = '';
        }
    }
}

function isDescendantOf(glossary, ancestorId, allGlossaries) {
    // Convert ancestorId to number for comparison
    const ancestorIdNum = parseInt(ancestorId, 10);
    
    // Check if glossary is a direct child of ancestor
    const parentId = glossary.Parent_ID || glossary.parentId;
    if (parentId && parseInt(parentId, 10) === ancestorIdNum) {
        console.log(`Glossary "${glossary.Name || glossary.name}" is direct child of ancestor ${ancestorId}`);
        return true;
    }
    
    // If glossary has no parent, it's not a descendant
    if (!parentId) {
        return false;
    }
    
    // Find parent glossary and check recursively
    const parentGlossary = allGlossaries.find(g => {
        const gId = g.ID || g.id;
        return gId && parseInt(gId, 10) === parseInt(parentId, 10);
    });
    
    if (!parentGlossary) {
        console.log(`Parent glossary ${parentId} not found for glossary "${glossary.Name || glossary.name}"`);
        return false;
    }
    
    // Recursively check if parent is descendant of ancestor
    const isDescendant = isDescendantOf(parentGlossary, ancestorId, allGlossaries);
    if (isDescendant) {
        console.log(`Glossary "${glossary.Name || glossary.name}" is descendant of ancestor ${ancestorId} through parent "${parentGlossary.Name || parentGlossary.name}"`);
    }
    return isDescendant;
}

// Strategic Source Table Management - Updated to match v100 approach
const strategicSourceSystemSegmentCache = new Map();
/** System IDs whose /api/system/:id returned 404 — skip repeat fetches in enrichStrategicSystemsSegmentInfo */
const strategicSourceSystem404Cache = new Set();

function normalizeApiList(payload) {
    if (Array.isArray(payload)) return payload;
    if (Array.isArray(payload?.data)) return payload.data;
    if (Array.isArray(payload?.results)) return payload.results;
    return [];
}

function normalizeStrategicSystemsList(payload) {
    // Normalize fields
    const normalized = normalizeApiList(payload).map((system) => ({
        ...system,
        id: system?.id ?? system?.ID,
        name: system?.name ?? system?.Name ?? system?.systemName ?? ''
    }));

    // Deduplicate strictly by name (case-insensitive) to avoid same-name duplicates
    // even when IDs differ. Keep the first occurrence.
    const seenByName = new Set();
    const unique = [];
    for (const sys of normalized) {
        const keyName = (sys.name || '').toLowerCase().trim();
        if (keyName && seenByName.has(keyName)) continue;
        if (keyName) seenByName.add(keyName);
        unique.push(sys);
    }

    return unique;
}

function normalizeStrategicRelationTypesList(payload) {
    return normalizeApiList(payload).map((relationType) => ({
        ...relationType,
        id: relationType?.id ?? relationType?.ID,
        name: relationType?.name ?? relationType?.Name ?? relationType?.relationTypeName ?? ''
    }));
}

function getCurrentGlossarySegmentIdForStrategicSource() {
    const current = segmentField && typeof segmentField.getValue === 'function'
        ? parseInt(segmentField.getValue(), 10)
        : NaN;
    return Number.isInteger(current) && current > 0 ? current : NaN;
}

function resolveStrategicSystemSegmentId(systemRecord) {
    const systemId = parseInt(systemRecord?.id ?? systemRecord?.ID, 10);
    if (Number.isInteger(systemId) && strategicSourceSystemSegmentCache.has(systemId)) {
        const cachedSegment = parseInt(strategicSourceSystemSegmentCache.get(systemId), 10);
        if (Number.isInteger(cachedSegment) && cachedSegment > 0) {
            return cachedSegment;
        }
    }

    const parsed = parseInt(
        systemRecord?.segmentId ??
        systemRecord?.segment_id ??
        systemRecord?.Segment_ID ??
        systemRecord?.segmentID ??
        systemRecord?.segment?.id ??
        systemRecord?.segment?.ID,
        10
    );
    return Number.isInteger(parsed) && parsed > 0 ? parsed : NaN;
}

async function enrichStrategicSystemsSegmentInfo(systems) {
    const rows = Array.isArray(systems) ? systems : [];
    if (rows.length === 0) {
        return rows;
    }

    return Promise.all(rows.map(async (s) => {
        const systemId = parseInt(s?.id ?? s?.ID, 10);
        const existingSegment = resolveStrategicSystemSegmentId(s);
        if (!Number.isInteger(systemId) || systemId <= 0 || (Number.isInteger(existingSegment) && existingSegment > 0)) {
            return s;
        }

        if (strategicSourceSystemSegmentCache.has(systemId)) {
            const cachedSegmentId = parseInt(strategicSourceSystemSegmentCache.get(systemId), 10);
            if (Number.isInteger(cachedSegmentId) && cachedSegmentId > 0) {
                return { ...s, segmentId: cachedSegmentId };
            }
        }
        if (strategicSourceSystem404Cache.has(systemId)) return s;

        try {
            const resp = await fetch(`/api/system/${systemId}`, { credentials: 'include' });
            if (!resp.ok) {
                if (resp.status === 404) strategicSourceSystem404Cache.add(systemId);
                return s;
            }
            const data = await resp.json();
            const systemSegmentId = resolveStrategicSystemSegmentId(data);
            if (Number.isInteger(systemSegmentId) && systemSegmentId > 0) {
                strategicSourceSystemSegmentCache.set(systemId, systemSegmentId);
                return { ...s, segmentId: systemSegmentId };
            }
        } catch (error) {
            console.warn('Failed to enrich strategic source system segment:', error);
        }

        return s;
    }));
}

function isAllowedGlossarySystemSegmentPair(glossarySegmentId, systemSegmentId) {
    if (!Number.isInteger(glossarySegmentId) || !Number.isInteger(systemSegmentId)) {
        return true;
    }
    if (glossarySegmentId === systemSegmentId) {
        return true;
    }
    return glossarySegmentId === 1 || systemSegmentId === 1;
}

async function enforceStrategicSourceSystemSegmentConstraint(systemSelect, options = {}) {
    const { notify = true } = options;
    const glossarySegmentId = getCurrentGlossarySegmentIdForStrategicSource();
    const selectedSystemId = parseInt(systemSelect?.value || '', 10);
    const row = systemSelect?.closest('tr');
    const datasetSelect = row?.querySelector('.dataset-select');

    if (!Number.isInteger(selectedSystemId) || selectedSystemId <= 0) {
        return true;
    }

    const systemSegmentId = await resolveSelectedSystemSegmentId(systemSelect);
    if (!Number.isInteger(glossarySegmentId) || !Number.isInteger(systemSegmentId)) {
        return true;
    }

    if (isAllowedGlossarySystemSegmentPair(glossarySegmentId, systemSegmentId)) {
        return true;
    }

    // Prevent selecting systems from another private segment.
    systemSelect.value = '';
    if (datasetSelect) {
        datasetSelect.value = '';
        datasetSelect.disabled = true;
        datasetSelect.innerHTML = '<option value="">Select Dataset (Optional)</option>';
    }

    if (row?.classList?.contains('existing-row') && typeof markRowAsChanged === 'function') {
        markRowAsChanged(systemSelect);
    }
    if (typeof validateStrategicSourceRow === 'function') {
        validateStrategicSourceRow(systemSelect);
    }
    if (notify) {
        alert('You cannot add a system from another private segment. Only matching segment systems are allowed (enterprise segment is allowed).');
    }
    return false;
}

function filterStrategicSystemsForGlossary(systems, selectedSystemId = null) {
    const glossarySegmentId = getCurrentGlossarySegmentIdForStrategicSource();
    const rows = Array.isArray(systems) ? systems : [];
    if (!Number.isInteger(glossarySegmentId) || glossarySegmentId <= 0) {
        return rows;
    }

    const filtered = rows.filter((s) => {
        const systemSegmentId = resolveStrategicSystemSegmentId(s);
        if (!Number.isInteger(systemSegmentId) || systemSegmentId <= 0) {
            return true;
        }
        return isAllowedGlossarySystemSegmentPair(glossarySegmentId, systemSegmentId);
    });

    if (selectedSystemId != null) {
        const selectedNumeric = parseInt(selectedSystemId, 10);
        const exists = filtered.some(s => parseInt(s.id || s.ID, 10) === selectedNumeric);
        if (!exists) {
            const selectedRow = rows.find(s => parseInt(s.id || s.ID, 10) === selectedNumeric);
            if (selectedRow) {
                filtered.push(selectedRow);
            }
        }
    }

    return filtered;
}

function buildStrategicSystemOptionsHtml(systems, selectedSystemId = null) {
    const allRows = Array.isArray(systems) ? systems : [];
    const filteredRows = filterStrategicSystemsForGlossary(allRows, selectedSystemId);

    if (allRows.length === 0) {
        return '<option value="" disabled>No systems available</option>';
    }
    if (filteredRows.length === 0) {
        return '<option value="" disabled>No systems available for selected segment</option>';
    }

    return filteredRows
        .map((s) => {
            const systemId = s.id || s.ID;
            const selected = selectedSystemId != null && String(systemId) === String(selectedSystemId) ? 'selected' : '';
            const systemSegmentId = resolveStrategicSystemSegmentId(s);
            const segmentAttr = Number.isInteger(systemSegmentId) ? ` data-segment-id="${systemSegmentId}"` : '';
            const systemName = s.name || s.Name || '';
            return `<option value="${systemId}" ${selected}${segmentAttr}>${escapeHtml(systemName)}</option>`;
        }).join('');
}

async function refreshStrategicSourceSystemsForSelectedSegment() {
    const tbody = document.getElementById('strategicSourceTableBody');
    if (!tbody) {
        return;
    }

    const glossaryId = parseId();
    if (!glossaryId) {
        return;
    }

    try {
        const systemsResponse = await fetch(`/api/glossary-x-system/${glossaryId}/systems`);
        if (!systemsResponse.ok) {
            return;
        }

        const systemsPayload = await systemsResponse.json();
        const systems = await enrichStrategicSystemsSegmentInfo(normalizeStrategicSystemsList(systemsPayload));
        const systemSelects = tbody.querySelectorAll('.system-select');

        for (const select of systemSelects) {
            const currentValue = select.value;
            select.innerHTML = '<option value="">Select System</option>' + buildStrategicSystemOptionsHtml(systems);

            const optionStillAllowed = currentValue && Array.from(select.options).some(opt => opt.value === currentValue);
            if (optionStillAllowed) {
                select.value = currentValue;
            } else if (currentValue) {
                const row = select.closest('tr');
                const datasetSelect = row?.querySelector('.dataset-select');
                if (datasetSelect) {
                    datasetSelect.value = '';
                    datasetSelect.disabled = true;
                    datasetSelect.innerHTML = '<option value="">Select Dataset (Optional)</option>';
                }
                if (typeof validateStrategicSourceRow === 'function') {
                    validateStrategicSourceRow(select);
                }
                if (row?.classList?.contains('existing-row') && typeof markRowAsChanged === 'function') {
                    markRowAsChanged(select);
                }
            }
        }
    } catch (error) {
        console.warn('Failed to refresh strategic source systems for segment change:', error);
    }
}

async function resolveSelectedSystemSegmentId(systemSelect) {
    const fromOption = parseInt(systemSelect?.selectedOptions?.[0]?.dataset?.segmentId || '', 10);
    if (Number.isInteger(fromOption) && fromOption > 0) {
        return fromOption;
    }

    const systemId = parseInt(systemSelect?.value || '', 10);
    if (!Number.isInteger(systemId) || systemId <= 0) {
        return NaN;
    }

    if (strategicSourceSystemSegmentCache.has(systemId)) {
        return strategicSourceSystemSegmentCache.get(systemId);
    }
    if (strategicSourceSystem404Cache.has(systemId)) return NaN;

    try {
        const resp = await fetch(`/api/system/${systemId}`, { credentials: 'include' });
        if (resp.ok) {
            const data = await resp.json();
            const systemSegmentId = resolveStrategicSystemSegmentId(data);
            if (Number.isInteger(systemSegmentId) && systemSegmentId > 0) {
                strategicSourceSystemSegmentCache.set(systemId, systemSegmentId);
                return systemSegmentId;
            }
        }
        if (resp.status === 404) strategicSourceSystem404Cache.add(systemId);
    } catch (error) {
        if (window.__BUDG_DEBUG__) console.warn('Failed to resolve strategic source system segment:', error);
    }

    return NaN;
}

function getStrategicSystemSegmentIdFromSelect(systemSelect) {
    const selectedOption = systemSelect?.selectedOptions?.[0];
    const selectedSystemId = parseInt(systemSelect?.value || '', 10);
    const systemSegmentFromOption = parseInt(selectedOption?.dataset?.segmentId || '', 10);
    const cachedSystemSegment = parseInt(strategicSourceSystemSegmentCache.get(selectedSystemId), 10);
    return Number.isInteger(systemSegmentFromOption) && systemSegmentFromOption > 0
        ? systemSegmentFromOption
        : cachedSystemSegment;
}

function hasStrategicSourceSystemSegmentConflict(systemSelect) {
    if (!systemSelect?.value) {
        return false;
    }
    const glossarySegmentId = getCurrentGlossarySegmentIdForStrategicSource();
    const systemSegmentId = getStrategicSystemSegmentIdFromSelect(systemSelect);
    return Number.isInteger(glossarySegmentId) && glossarySegmentId > 0 &&
        Number.isInteger(systemSegmentId) && systemSegmentId > 0 &&
        !isAllowedGlossarySystemSegmentPair(glossarySegmentId, systemSegmentId);
}

function initializeStrategicSourceTable() {
    const tableBody = document.getElementById('strategicSourceTableBody');
    if (!tableBody) return;

    // Add event listeners for add/remove buttons
    tableBody.addEventListener('click', function(e) {
        if (e.target.closest('.action-button.add') || e.target.closest('#addStrategicSourceBtn')) {
            addStrategicSource();
        } else if (e.target.closest('.action-button.remove') || e.target.closest('#removeStrategicSourceBtn')) {
            removeStrategicSource(e.target.closest('button'));
        }
    });
}

// Add new strategic source row - Updated to match v100 approach
async function addStrategicSource() {
    const tbody = document.getElementById('strategicSourceTableBody');
    const glossaryId = parseId();
    
    try {
        // Load relation types and systems
        const [relationTypesResponse, systemsResponse] = await Promise.all([
            fetch(`/api/glossary-x-system/${glossaryId}/relation-types`),
            fetch(`/api/glossary-x-system/${glossaryId}/systems`)
        ]);
        
        if (!relationTypesResponse.ok) {
            throw new Error(`Failed to load relation types: ${relationTypesResponse.status}`);
        }
        if (!systemsResponse.ok) {
            throw new Error(`Failed to load systems: ${systemsResponse.status}`);
        }
        
        const relationTypesPayload = await relationTypesResponse.json();
        const systemsPayload = await systemsResponse.json();
        const relationTypes = normalizeStrategicRelationTypesList(relationTypesPayload);
        const systems = await enrichStrategicSystemsSegmentInfo(normalizeStrategicSystemsList(systemsPayload));
        
        console.log('Relation types:', relationTypes);
        console.log('Systems:', systems);
        
        // Only show edit buttons if user has permission
        const editButtonsHtml = userCanEdit ? `
            <div class="action-buttons">
                <button type="button" class="btn btn-sm btn-success" onclick="addStrategicSource()" title="Add Row">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="btn btn-sm btn-danger" onclick="removeStrategicSource(this)" title="Remove Row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        ` : '';
        
        const newRow = document.createElement('tr');
        newRow.className = 'empty-row';
        newRow.innerHTML = `
            <td>
                <select class="form-select form-select-sm relation-type-select" onchange="validateStrategicSourceRow(this)" ${!userCanEdit ? 'disabled' : ''}>
                    <option value="">Select Relation Type</option>
                    ${relationTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.name)}</option>`).join('')}
                </select>
            </td>
            <td>
                <select class="form-select form-select-sm system-select" onchange="loadDatasetsForSystem(this); validateStrategicSourceRow(this)" ${!userCanEdit ? 'disabled' : ''}>
                    <option value="">Select System</option>
                    ${buildStrategicSystemOptionsHtml(systems)}
                </select>
            </td>
            <td>
                <select class="form-select form-select-sm dataset-select" ${!userCanEdit ? 'disabled' : ''}>
                    <option value="">Select Dataset (Optional)</option>
                </select>
            </td>
            <td>
                ${editButtonsHtml}
            </td>
        `;
        tbody.appendChild(newRow);
    } catch (error) {
        console.error('Failed to load data for new strategic source:', error);
        alert('Failed to load data for new strategic source');
    }
}

// Remove strategic source row - Updated to match v100 approach
async function removeStrategicSource(button) {
    const row = button.closest('tr');
    const tbody = document.getElementById('strategicSourceTableBody');
    const allRows = tbody.querySelectorAll('tr');
    const isFirstRow = row === allRows[0];
    const id = row.getAttribute('data-id');
    
    if (id) {
        // Delete from database
        try {
            const response = await fetch(`/api/glossary-x-system/${id}`, {
                method: 'DELETE'
            });
            
            if (response.ok) {
                if (isFirstRow) {
                    // For first row, only clear the data but keep the row structure
                    await clearRowData(row);
                } else {
                    // For other rows, remove the entire row
                    row.remove();
                }
            } else {
                const error = await response.json();
                alert('Failed to delete strategic source: ' + (error.message || 'Unknown error'));
            }
    } catch (error) {
            console.error('Failed to delete strategic source:', error);
            alert('Failed to delete strategic source: ' + error.message);
        }
    } else {
        // For empty rows (no data-id)
        if (isFirstRow) {
            // For first empty row, only clear the data
            await clearRowData(row);
        } else {
            // For other empty rows, remove the entire row
            row.remove();
        }
    }
}

// Clear row data - Updated to match v100 approach
async function clearRowData(row) {
    const glossaryId = parseId();
    
    try {
        // Load relation types and systems for the empty row
        const [relationTypesResponse, systemsResponse] = await Promise.all([
            fetch(`/api/glossary-x-system/${glossaryId}/relation-types`),
            fetch(`/api/glossary-x-system/${glossaryId}/systems`)
        ]);
        
        if (relationTypesResponse.ok && systemsResponse.ok) {
            const relationTypesPayload = await relationTypesResponse.json();
            const systemsPayload = await systemsResponse.json();
            const relationTypes = normalizeStrategicRelationTypesList(relationTypesPayload);
            const systems = await enrichStrategicSystemsSegmentInfo(normalizeStrategicSystemsList(systemsPayload));
            
            row.innerHTML = `
                <td>
                    <select class="form-select form-select-sm relation-type-select" onchange="validateStrategicSourceRow(this)">
                        <option value="">Select Relation Type</option>
                        ${relationTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.name)}</option>`).join('')}
                    </select>
                </td>
                <td>
                    <select class="form-select form-select-sm system-select" onchange="loadDatasetsForSystem(this); validateStrategicSourceRow(this)">
                        <option value="">Select System</option>
                        ${buildStrategicSystemOptionsHtml(systems)}
                    </select>
                </td>
                <td>
                    <select class="form-select form-select-sm dataset-select" disabled>
                        <option value="">Select Dataset (Optional)</option>
                    </select>
                </td>
                <td>
                    <div class="action-buttons">
                        <button type="button" class="btn btn-sm btn-success" onclick="addStrategicSource()" title="Add Row">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button type="button" class="btn btn-sm btn-danger" onclick="removeStrategicSource(this)" title="Remove Row">
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                </td>
            `;
    } else {
            // Fallback: simple empty row
            row.innerHTML = `
                <td>
                    <select class="form-select form-select-sm relation-type-select">
                        <option value="">Select Relation Type</option>
                    </select>
                </td>
                <td>
                    <select class="form-select form-select-sm system-select" onchange="loadDatasetsForSystem(this)">
                        <option value="">Select System</option>
                    </select>
                </td>
                <td>
                    <select class="form-select form-select-sm dataset-select" disabled>
                        <option value="">Select Dataset (Optional)</option>
                    </select>
                </td>
                <td>
                    <div class="action-buttons">
                        <button type="button" class="btn btn-sm btn-success" onclick="addStrategicSource()" title="Add Row">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button type="button" class="btn btn-sm btn-danger" onclick="removeStrategicSource(this)" title="Remove Row">
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                </td>
            `;
        }
        
        row.className = 'empty-row';
        row.removeAttribute('data-id');
    } catch (error) {
        console.error('Failed to clear row data:', error);
    }
}

// Load Strategic Source data - Updated to match v100 approach
async function loadStrategicSourceData(glossaryId, viewMode = 'original') {
    try {
        const viewParam = viewMode === 'changes' ? '?view=changes' : '';
        const response = await fetch(`/api/glossary-x-system/${glossaryId}${viewParam}`);
        const strategicSourceData = await response.json();
        const tbody = document.getElementById('strategicSourceTableBody');
        
        if (!tbody) return;
        
        // Clear existing content
        tbody.innerHTML = '';
        
        // Load relation types and systems FIRST before creating rows
        const [relationTypesResponse, systemsResponse] = await Promise.all([
            fetch(`/api/glossary-x-system/${glossaryId}/relation-types${viewParam}`),
            fetch(`/api/glossary-x-system/${glossaryId}/systems${viewParam}`)
        ]);
        
        let relationTypes = [];
        let systems = [];
        
        if (relationTypesResponse.ok) {
            const relationTypesPayload = await relationTypesResponse.json();
            relationTypes = normalizeStrategicRelationTypesList(relationTypesPayload);
        }
        if (systemsResponse.ok) {
            const systemsPayload = await systemsResponse.json();
            systems = await enrichStrategicSystemsSegmentInfo(normalizeStrategicSystemsList(systemsPayload));
        }
        
        // Check if there's actual data from database
        if (Array.isArray(strategicSourceData) && strategicSourceData.length > 0) {
            // Show existing data as editable rows with ALL dropdown options
            const existingRows = strategicSourceData.map((item, index) => {
                const relationTypeOptions = relationTypes.map(rt => 
                    `<option value="${rt.id}" ${rt.id == item.relationTypeId ? 'selected' : ''}>${escapeHtml(rt.name)}</option>`
                ).join('');
                
                const systemOptions = buildStrategicSystemOptionsHtml(systems, item.systemId);
                
                // Only show edit buttons if user has permission
                const editButtonsHtml = userCanEdit ? `
                    <div class="action-buttons">
                        <button type="button" class="btn btn-sm btn-success" onclick="addStrategicSource()" title="Add Row">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button type="button" class="btn btn-sm btn-danger" onclick="removeStrategicSource(this)" title="Remove Row">
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                ` : '';
                
                return `
                <tr data-id="${item.id}" class="existing-row" data-row-index="${index}">
                    <td>
                        <select class="form-select form-select-sm relation-type-select" onchange="validateStrategicSourceRow(this); markRowAsChanged(this)" data-original-value="${item.relationTypeId || ''}" ${!userCanEdit ? 'disabled' : ''}>
                            <option value="">Select Relation Type</option>
                            ${relationTypeOptions}
                        </select>
                    </td>
                    <td>
                        <select class="form-select form-select-sm system-select" onchange="loadDatasetsForSystem(this); validateStrategicSourceRow(this); markRowAsChanged(this)" data-original-value="${item.systemId || ''}" ${!userCanEdit ? 'disabled' : ''}>
                            <option value="">Select System</option>
                            ${systemOptions}
                        </select>
                    </td>
                    <td>
                        <select class="form-select form-select-sm dataset-select" onchange="markRowAsChanged(this)" data-original-value="${item.datasetId || ''}" ${!userCanEdit ? 'disabled' : ''}>
                            <option value="">Select Dataset (Optional)</option>
                            <option value="${item.datasetId || ''}" selected>${escapeHtml(item.datasetName || '')}</option>
                        </select>
                    </td>
                    <td>
                        ${editButtonsHtml}
                    </td>
                </tr>
            `;
            }).join('');
            
            tbody.innerHTML = existingRows;
            
            // Load datasets for existing rows
            await loadDatasetsForExistingRows(strategicSourceData);
        }
        
        // Always add one empty row for adding new strategic source
        await showEmptyStrategicSourceRow(tbody, glossaryId, viewMode);
        
        // Apply permissions after loading
        applyPermissionsToUI();
        
    } catch (error) {
        console.error('Failed to load strategic source data:', error);
        // Show empty row even if there's an error
        const tbody = document.getElementById('strategicSourceTableBody');
        if (tbody) {
            tbody.innerHTML = '';
            await showEmptyStrategicSourceRow(tbody, glossaryId, viewMode);
            applyPermissionsToUI();
        }
    }
}

// Show empty strategic source row - Updated to match v100 approach
async function showEmptyStrategicSourceRow(tbody, glossaryId, viewMode = 'original') {
    try {
        const viewParam = viewMode === 'changes' ? '?view=changes' : '';
        // Load relation types and systems for the empty row
        const [relationTypesResponse, systemsResponse] = await Promise.all([
            fetch(`/api/glossary-x-system/${glossaryId}/relation-types${viewParam}`),
            fetch(`/api/glossary-x-system/${glossaryId}/systems${viewParam}`)
        ]);
        
        if (!relationTypesResponse.ok || !systemsResponse.ok) {
            throw new Error('Failed to load data for empty row');
        }
        
        const relationTypesPayload = await relationTypesResponse.json();
        const systemsPayload = await systemsResponse.json();
        const relationTypes = normalizeStrategicRelationTypesList(relationTypesPayload);
        const systems = await enrichStrategicSystemsSegmentInfo(normalizeStrategicSystemsList(systemsPayload));
        
        // Only show edit buttons if user has permission
        const editButtonsHtml = userCanEdit ? `
            <div class="action-buttons">
                <button type="button" class="btn btn-sm btn-success" onclick="addStrategicSource()" title="Add Row">
                    <i class="fas fa-plus"></i>
                </button>
                <button type="button" class="btn btn-sm btn-danger" onclick="removeStrategicSource(this)" title="Remove Row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        ` : '';
        
        const emptyRow = document.createElement('tr');
        emptyRow.className = 'empty-row';
        emptyRow.innerHTML = `
            <td>
                <select class="form-select form-select-sm relation-type-select" onchange="validateStrategicSourceRow(this)" ${!userCanEdit ? 'disabled' : ''}>
                    <option value="">Select Relation Type</option>
                    ${relationTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.name)}</option>`).join('')}
            </select>
        </td>
        <td>
                <select class="form-select form-select-sm system-select" onchange="loadDatasetsForSystem(this); validateStrategicSourceRow(this)" ${!userCanEdit ? 'disabled' : ''}>
                    <option value="">Select System</option>
                    ${buildStrategicSystemOptionsHtml(systems)}
            </select>
        </td>
        <td>
                <select class="form-select form-select-sm dataset-select" ${!userCanEdit ? 'disabled' : ''}>
                    <option value="">Select Dataset (Optional)</option>
            </select>
        </td>
        <td>
                ${editButtonsHtml}
        </td>
    `;
        tbody.appendChild(emptyRow);
    } catch (error) {
        console.error('Failed to create empty row:', error);
        // Fallback: show simple empty row
        const emptyRow = document.createElement('tr');
        emptyRow.className = 'empty-row';
        emptyRow.innerHTML = `
            <td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-tertiary, #adb5bd);">
                <button type="button" class="btn btn-primary" onclick="addStrategicSource()">
                    <i class="fas fa-plus"></i> Add Strategic Source
                </button>
            </td>
        `;
        tbody.appendChild(emptyRow);
    }
}

// Load datasets for system - Updated to match v100 approach
async function loadDatasetsForSystem(selectElement) {
    const row = selectElement.closest('tr');
    const datasetSelect = row.querySelector('.dataset-select');
    
    if (!selectElement.value) {
        datasetSelect.disabled = true;
        datasetSelect.innerHTML = '<option value="">Select Dataset</option>';
        return;
    }

    const isAllowedForSegment = await enforceStrategicSourceSystemSegmentConstraint(selectElement, { notify: true });
    if (!isAllowedForSegment) {
        return;
    }

    const systemId = selectElement.value;
    
    try {
        const response = await fetch(`/api/glossary-x-system/${parseId()}/datasets/${systemId}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const datasets = await response.json();
        console.log('Datasets response:', datasets);
        
        // Check if datasets is an array
        if (!Array.isArray(datasets)) {
            console.error('Datasets is not an array:', datasets);
            datasetSelect.innerHTML = '<option value="">No datasets available</option>';
            datasetSelect.disabled = false;
            return;
        }
        
        // Get current value to preserve selection
        const currentValue = datasetSelect.value;
        
        // If no current value, try to get from original value
        const originalValue = datasetSelect.getAttribute('data-original-value');
        const valueToPreserve = currentValue || originalValue;
        
        datasetSelect.innerHTML = '<option value="">Select Dataset (Optional)</option>' +
            datasets.map(d => `<option value="${d.id}" ${d.id == valueToPreserve ? 'selected' : ''}>${escapeHtml(d.name)}</option>`).join('');
        datasetSelect.disabled = false;
    } catch (error) {
        console.error('Failed to load datasets:', error);
        datasetSelect.innerHTML = '<option value="">Error loading datasets</option>';
        datasetSelect.disabled = false;
    }
}

// Save Strategic Source data - Updated to handle both new rows and existing row updates
async function saveStrategicSourceData(glossaryId) {
    const tbody = document.getElementById('strategicSourceTableBody');
    if (!tbody) {
        console.log('Strategic source table body not found, skipping save');
        return true; // Return true if table doesn't exist (not an error)
    }

    // Validate data before saving
    const validation = validateStrategicSourceData();
    if (!validation.isValid) {
        showStrategicSourceValidationErrors(validation.errors);
        return false; // Return false to indicate validation failed
    }

    // Clear any previous validation errors
    clearStrategicSourceValidationErrors();

    let savedCount = 0;
    let updatedCount = 0;
    const errors = [];
    
    try {
        // Handle existing rows that have been changed
        const changedRows = tbody.querySelectorAll('.existing-row.row-changed');
        for (const row of changedRows) {
            const rowId = row.getAttribute('data-id');
            const relationTypeId = row.querySelector('.relation-type-select')?.value;
            const systemId = row.querySelector('.system-select')?.value;
            const datasetId = row.querySelector('.dataset-select')?.value;
            
            if (relationTypeId && systemId) {
                const glossarySegmentId = getCurrentGlossarySegmentIdForStrategicSource();
                const systemSegmentId = await resolveSelectedSystemSegmentId(row.querySelector('.system-select'));
                if (Number.isInteger(glossarySegmentId) && glossarySegmentId > 0 &&
                    Number.isInteger(systemSegmentId) && systemSegmentId > 0 &&
                    !isAllowedGlossarySystemSegmentPair(glossarySegmentId, systemSegmentId)) {
                    errors.push(`Failed to update strategic source ${rowId}: selected system belongs to another private segment`);
                    continue;
                }
                try {
                    const response = await fetch(`/api/glossary-x-system/${rowId}`, {
                        method: 'PUT',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            relationTypeId: parseInt(relationTypeId),
                            systemId: parseInt(systemId),
                            datasetId: datasetId ? parseInt(datasetId) : null
                        })
                    });
                    
                    {
                        const responseData = await response.json().catch(() => ({}));
                        // Treat payloads that carry { error, status>=400, success:false } as failures even if HTTP 200
                        const payloadSignalsError = !!responseData?.error || responseData?.success === false || (responseData?.status && Number(responseData.status) >= 400);
                        if (response.ok && !payloadSignalsError) {
                            updatedCount++;
                            console.log('Strategic source updated successfully:', responseData);
                            
                            // Update original values to current values
                            const relationTypeSelect = row.querySelector('.relation-type-select');
                            const systemSelect = row.querySelector('.system-select');
                            const datasetSelect = row.querySelector('.dataset-select');
                            
                            if (relationTypeSelect) {
                                relationTypeSelect.setAttribute('data-original-value', relationTypeId);
                            }
                            if (systemSelect) {
                                systemSelect.setAttribute('data-original-value', systemId);
                            }
                            if (datasetSelect) {
                                datasetSelect.setAttribute('data-original-value', datasetId || '');
                            }
                            
                            // Remove changed styling
                            row.classList.remove('row-changed');
                            row.style.backgroundColor = '';
                            row.style.borderLeft = '';
                        } else {
                            const message = responseData?.message || responseData?.error || 'Unknown error';
                            console.error('Failed to update strategic source:', responseData);
                            errors.push(`Failed to update strategic source ${rowId}: ${message}`);
                        }
                    }
                    /* else branch removed - handled above */
                } catch (error) {
                    console.error('Failed to update strategic source:', error);
                    errors.push(`Failed to update strategic source ${rowId}: ${error.message || 'Unknown error'}`);
                }
            }
        }
        
        // Handle new empty rows
        const emptyRows = tbody.querySelectorAll('.empty-row');
        for (const row of emptyRows) {
            const relationTypeId = row.querySelector('.relation-type-select')?.value;
            const systemId = row.querySelector('.system-select')?.value;
            const datasetId = row.querySelector('.dataset-select')?.value;
            
            // Only save if both relation type and system are selected
            if (relationTypeId && systemId) {
                const glossarySegmentId = getCurrentGlossarySegmentIdForStrategicSource();
                const systemSegmentId = await resolveSelectedSystemSegmentId(row.querySelector('.system-select'));
                if (Number.isInteger(glossarySegmentId) && glossarySegmentId > 0 &&
                    Number.isInteger(systemSegmentId) && systemSegmentId > 0 &&
                    !isAllowedGlossarySystemSegmentPair(glossarySegmentId, systemSegmentId)) {
                    errors.push('Failed to save strategic source: selected system belongs to another private segment');
                    continue;
                }
                try {
                    const response = await fetch(`/api/glossary-x-system/${glossaryId}`, {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({
                            relationTypeId: parseInt(relationTypeId),
                            systemId: parseInt(systemId),
                            datasetId: datasetId ? parseInt(datasetId) : null
                        })
                    });
                    
                    {
                        const responseData = await response.json().catch(() => ({}));
                        const payloadSignalsError = !!responseData?.error || responseData?.success === false || (responseData?.status && Number(responseData.status) >= 400);
                        if (response.ok && !payloadSignalsError) {
                            savedCount++;
                            console.log('Strategic source saved successfully:', responseData);
                            
                            // Mark row as saved (will be reloaded)
                            row.classList.remove('empty-row');
                        } else {
                            const message = responseData?.message || responseData?.error || 'Unknown error';
                            console.error('Failed to save strategic source:', responseData);
                            errors.push(`Failed to save strategic source: ${message}`);
                        }
                    }
                } catch (error) {
                    console.error('Failed to save strategic source:', error);
                    errors.push(`Failed to save strategic source: ${error.message || 'Unknown error'}`);
                }
            }
        }
        
        // If there were errors, show them but don't fail completely
        if (errors.length > 0) {
            console.warn('Some strategic sources failed to save:', errors);
            alert('Some strategic sources failed to save:\n' + errors.join('\n'));
            // Signal failure to the caller so it won't show a generic success message
            return false;
        }
        
        // Log save results (don't reload here - caller will handle reload if needed)
        if (savedCount > 0 || updatedCount > 0) {
            console.log(`Saved ${savedCount} new strategic source(s) and updated ${updatedCount} existing row(s)`);
        }
        
        return true; // Return true to indicate successful save with no errors
    } catch (error) {
        console.error('Critical error saving strategic sources:', error);
        alert('Failed to save strategic sources: ' + (error.message || 'Unknown error'));
        return false; // Return false on critical error
    }
}

// Function to validate strategic source data before saving
function validateStrategicSourceData() {
    const tbody = document.getElementById('strategicSourceTableBody');
    if (!tbody) return { isValid: true, errors: [] };

    const emptyRows = tbody.querySelectorAll('.empty-row');
    const changedRows = tbody.querySelectorAll('.existing-row.row-changed');
    const errors = [];

    // Track duplicates across all rows that will be submitted (new + changed)
    const combinationSeen = new Set();
    const combinationKey = (relTypeId, systemId, datasetId) =>
        `${String(relTypeId || '')}::${String(systemId || '')}::${String(datasetId || '')}`;
    // Also include already-saved existing rows (unchanged) to prevent creating duplicates of them
    const existingRows = tbody.querySelectorAll('.existing-row');
    const existingOriginalCombos = new Set();
    existingRows.forEach((row) => {
        const relOrig = row.querySelector('.relation-type-select')?.getAttribute('data-original-value') || '';
        const sysOrig = row.querySelector('.system-select')?.getAttribute('data-original-value') || '';
        const dsOrig = row.querySelector('.dataset-select')?.getAttribute('data-original-value') || '';
        existingOriginalCombos.add(combinationKey(relOrig, sysOrig, dsOrig));
    });
    
    // Validate empty rows (new rows)
    emptyRows.forEach((row, index) => {
        const relationTypeSelect = row.querySelector('.relation-type-select');
        const systemSelect = row.querySelector('.system-select');
        const datasetSelect = row.querySelector('.dataset-select');
        
        // Check if any field has a value (indicating user is trying to save this row)
        const hasAnyValue = relationTypeSelect?.value || systemSelect?.value || datasetSelect?.value;
        
        if (hasAnyValue) {
            // If user is trying to save this row, both relation type and system are required
            if (!relationTypeSelect?.value) {
                errors.push(`New Row ${index + 1}: Relationship Type is required`);
            }
            if (!systemSelect?.value) {
                errors.push(`New Row ${index + 1}: System is required`);
            }
            if (hasStrategicSourceSystemSegmentConflict(systemSelect)) {
                errors.push(`New Row ${index + 1}: Selected system belongs to another private segment`);
            }

            // Duplicate check among rows being saved
            const key = combinationKey(relationTypeSelect?.value, systemSelect?.value, datasetSelect?.value);
            if (combinationSeen.has(key)) {
                errors.push(`New Row ${index + 1}: Duplicate of another strategic source row (same Relation, System, Dataset)`);
            } else {
                combinationSeen.add(key);
            }
        }
    });
    
    // Validate changed rows (existing rows being edited)
    changedRows.forEach((row, index) => {
        const relationTypeSelect = row.querySelector('.relation-type-select');
        const systemSelect = row.querySelector('.system-select');
        const datasetSelect = row.querySelector('.dataset-select');
        const relOrig = relationTypeSelect?.getAttribute('data-original-value') || '';
        const sysOrig = systemSelect?.getAttribute('data-original-value') || '';
        const dsOrig = datasetSelect?.getAttribute('data-original-value') || '';
        const thisRowOriginalKey = combinationKey(relOrig, sysOrig, dsOrig);
        
        // For changed rows, both relation type and system are required
        if (!relationTypeSelect?.value) {
            errors.push(`Row ${index + 1}: Relationship Type is required`);
        }
        if (!systemSelect?.value) {
            errors.push(`Row ${index + 1}: System is required`);
        }
        if (hasStrategicSourceSystemSegmentConflict(systemSelect)) {
            errors.push(`Row ${index + 1}: Selected system belongs to another private segment`);
        }

        // Duplicate check among rows being saved
        const key = combinationKey(relationTypeSelect?.value, systemSelect?.value, datasetSelect?.value);
        if (combinationSeen.has(key)) {
            errors.push(`Row ${index + 1}: Duplicate of another strategic source row (same Relation, System, Dataset)`);
        } else {
            combinationSeen.add(key);
        }
        // Also compare against unchanged existing rows; allow if it's this row's original combo
        if (existingOriginalCombos.has(key) && key !== thisRowOriginalKey) {
            errors.push(`Row ${index + 1}: Matches an existing strategic source row (duplicate of a saved row)`);
        }
    });
    
    return {
        isValid: errors.length === 0,
        errors: errors
    };
}

// Function to show validation errors for strategic source
function showStrategicSourceValidationErrors(errors) {
    // Remove any existing error messages
    clearStrategicSourceValidationErrors();
    
    // Create error message element
    const errorDiv = document.createElement('div');
    errorDiv.className = 'strategic-source-validation-error';
    errorDiv.style.cssText = `
        color: #dc3545;
        font-size: 0.9rem;
        margin-top: 1rem;
        padding: 1rem;
        background-color: #f8d7da;
        border: 2px solid #dc3545;
        border-radius: 6px;
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        font-weight: 500;
        box-shadow: 0 2px 4px rgba(220, 53, 69, 0.2);
        animation: shake 0.5s ease-in-out;
    `;
    
    errorDiv.innerHTML = `
        <i class="fas fa-exclamation-triangle" style="color: #dc3545; font-size: 1.1rem; margin-top: 0.2rem;"></i>
        <div>
            <strong>Strategic Source Validation Failed:</strong><br>
            <p style="margin: 0.5rem 0;">Please complete the required fields before saving:</p>
            <ul style="margin: 0.5rem 0 0 0; padding-left: 1.5rem;">
                ${errors.map(error => `<li>${escapeHtml(error)}</li>`).join('')}
            </ul>
            <p style="margin: 0.5rem 0 0 0; font-size: 0.85rem; color: #6c757d;">
                <i class="fas fa-info-circle"></i> Both Relationship Type and System are required. Systems from another private segment are not allowed (enterprise segment is allowed).
            </p>
        </div>
    `;
    
    // Add shake animation CSS if not already present
    if (!document.querySelector('#shake-animation-style')) {
        const style = document.createElement('style');
        style.id = 'shake-animation-style';
        style.textContent = `
            @keyframes shake {
                0%, 100% { transform: translateX(0); }
                25% { transform: translateX(-5px); }
                75% { transform: translateX(5px); }
            }
        `;
        document.head.appendChild(style);
    }
    
    // Insert error message after the strategic source table
    const strategicSourceSection = document.querySelector('.form-section:has(#strategicSourceTableBody)');
    if (strategicSourceSection) {
        strategicSourceSection.appendChild(errorDiv);
    }
    
    // Scroll to the error message
    errorDiv.scrollIntoView({ behavior: 'smooth', block: 'center' });
}

// Function to clear strategic source validation errors
function clearStrategicSourceValidationErrors() {
    const existingError = document.querySelector('.strategic-source-validation-error');
    if (existingError) {
        existingError.remove();
    }
}

// Function to load dropdown options for existing rows
async function loadDropdownOptionsForExistingRows(strategicSourceData) {
    try {
        const glossaryId = parseId();
        const [relationTypesResponse, systemsResponse] = await Promise.all([
            fetch(`/api/glossary-x-system/${glossaryId}/relation-types`),
            fetch(`/api/glossary-x-system/${glossaryId}/systems`)
        ]);
        
        if (!relationTypesResponse.ok || !systemsResponse.ok) {
            console.warn('Failed to load dropdown options for existing rows');
            return;
        }

        const relationTypesPayload = await relationTypesResponse.json();
        const systemsPayload = await systemsResponse.json();
        const relationTypes = normalizeStrategicRelationTypesList(relationTypesPayload);
        const systems = await enrichStrategicSystemsSegmentInfo(normalizeStrategicSystemsList(systemsPayload));
        
        // Update relation type dropdowns
        const relationTypeSelects = document.querySelectorAll('.existing-row .relation-type-select');
        relationTypeSelects.forEach(select => {
            const currentValue = select.value;
            select.innerHTML = '<option value="">Select Relation Type</option>' +
                relationTypes.map(rt => `<option value="${rt.id}" ${rt.id == currentValue ? 'selected' : ''}>${escapeHtml(rt.name)}</option>`).join('');
        });
        
        // Update system dropdowns
        const systemSelects = document.querySelectorAll('.existing-row .system-select');
        systemSelects.forEach(select => {
            const currentValue = select.value;
            select.innerHTML = '<option value="">Select System</option>' +
                buildStrategicSystemOptionsHtml(systems, currentValue);
        });
        
        // Load datasets for each system and preserve current selection
        for (const select of systemSelects) {
            if (select.value) {
                const currentDatasetId = select.closest('tr').querySelector('.dataset-select').getAttribute('data-original-value');
                await loadDatasetsForSystem(select);
                
                // Restore the current dataset selection
                const datasetSelect = select.closest('tr').querySelector('.dataset-select');
                if (datasetSelect && currentDatasetId) {
                    datasetSelect.value = currentDatasetId;
                }
            }
        }
        
    } catch (error) {
        console.error('Failed to load dropdown options for existing rows:', error);
    }
}

// Function to load datasets for existing rows
async function loadDatasetsForExistingRows(strategicSourceData) {
    try {
        const systemSelects = document.querySelectorAll('.existing-row .system-select');
        
        for (const select of systemSelects) {
            if (select.value) {
                // Get the original dataset ID for this row
                const row = select.closest('tr');
                const rowId = row.getAttribute('data-id');
                const originalData = strategicSourceData.find(item => item.id == rowId);
                
                if (originalData && originalData.datasetId) {
                    // Load datasets for the system
                    await loadDatasetsForSystem(select);
                    
                    // Set the original dataset value
                    const datasetSelect = row.querySelector('.dataset-select');
                    if (datasetSelect) {
                        datasetSelect.value = originalData.datasetId;
                    }
                }
            }
        }
    } catch (error) {
        console.error('Failed to load datasets for existing rows:', error);
    }
}

// Function to mark a row as changed
function markRowAsChanged(selectElement) {
    const row = selectElement.closest('tr');
    const originalValue = selectElement.getAttribute('data-original-value');
    const currentValue = selectElement.value;
    
    // Mark row as changed if value is different from original
    if (originalValue !== currentValue) {
        row.classList.add('row-changed');
        row.style.backgroundColor = '#fff3cd';
        row.style.borderLeft = '4px solid #ffc107';
    } else {
        // Check if all fields are back to original values
        const relationTypeSelect = row.querySelector('.relation-type-select');
        const systemSelect = row.querySelector('.system-select');
        const datasetSelect = row.querySelector('.dataset-select');
        
        const relationTypeChanged = relationTypeSelect.getAttribute('data-original-value') !== relationTypeSelect.value;
        const systemChanged = systemSelect.getAttribute('data-original-value') !== systemSelect.value;
        const datasetChanged = datasetSelect.getAttribute('data-original-value') !== datasetSelect.value;
        
        if (!relationTypeChanged && !systemChanged && !datasetChanged) {
            row.classList.remove('row-changed');
            row.style.backgroundColor = '';
            row.style.borderLeft = '';
        }
    }
}

// Function to validate a single strategic source row in real-time
function validateStrategicSourceRow(selectElement) {
    const row = selectElement.closest('tr');
    const relationTypeSelect = row.querySelector('.relation-type-select');
    const systemSelect = row.querySelector('.system-select');
    
    // Clear any existing visual indicators
    relationTypeSelect.style.borderColor = '';
    systemSelect.style.borderColor = '';
    
    // Check if both fields have values
    const hasRelationType = relationTypeSelect.value && relationTypeSelect.value !== '';
    const hasSystem = systemSelect.value && systemSelect.value !== '';
    
    // If user has started filling the row (any field has a value)
    const hasAnyValue = relationTypeSelect.value || systemSelect.value;
    
    if (hasAnyValue) {
        // Highlight missing required fields
        if (!hasRelationType) {
            relationTypeSelect.style.borderColor = '#dc3545';
        }
        if (!hasSystem) {
            systemSelect.style.borderColor = '#dc3545';
        }
        
        // If both are filled, clear any error indicators and validation messages
        if (hasRelationType && hasSystem) {
            relationTypeSelect.style.borderColor = '#28a745';
            systemSelect.style.borderColor = '#28a745';
            // Clear any existing validation error messages
            clearStrategicSourceValidationErrors();
        }
        } else {
        // If no values, clear all indicators
        relationTypeSelect.style.borderColor = '';
        systemSelect.style.borderColor = '';
    }
}

// Function to check if there are any unsaved changes
function hasUnsavedChanges() {
    const changedRows = document.querySelectorAll('.existing-row.row-changed');
    const emptyRows = document.querySelectorAll('.empty-row');
    
    // Check if any existing rows have been changed
    if (changedRows.length > 0) {
            return true;
    }
    
    // Check if any empty rows have data
    for (const row of emptyRows) {
        const relationTypeSelect = row.querySelector('.relation-type-select');
        const systemSelect = row.querySelector('.system-select');
        const datasetSelect = row.querySelector('.dataset-select');
        
        if (relationTypeSelect?.value || systemSelect?.value || datasetSelect?.value) {
            return true;
        }
    }
    
            return false;
        }

// Function to save only strategic source data (can be called independently)
async function saveStrategicSourceOnly() {
    const id = parseId();
    if (!id) {
        alert('Invalid glossary ID');
        return;
    }

    // Validate data before saving
    const validation = validateStrategicSourceData();
    if (!validation.isValid) {
        showStrategicSourceValidationErrors(validation.errors);
        return;
    }

    // Clear any previous validation errors
    clearStrategicSourceValidationErrors();

    try {
        const ok = await saveStrategicSourceData(id);
        if (ok) {
            alert('Strategic source data saved successfully');
        } else {
            // saveStrategicSourceData already displayed detailed errors (including duplication)
            // Do not show the generic success message.
            return;
        }
    } catch (error) {
        console.error('Failed to save strategic source data:', error);
        alert('Failed to save strategic source data: ' + (error.message || 'Unknown error'));
    }
}

// ===== GLOSSARY RELATIONSHIPS FUNCTIONALITY =====

// Load relationships data for edit page
// Store initial relationship IDs to detect deletions
let initialRelationshipIds = new Set();

async function loadRelationshipsData(glossaryId) {
    console.log('loadRelationshipsData called with glossaryId:', glossaryId);
    const tbody = document.getElementById('relationshipsTableBody');
    if (!tbody) {
        console.error('relationshipsTableBody element not found in DOM');
        return;
    }
    
    console.log('Loading bidirectional relationships for glossary ID:', glossaryId);
    
    try {
        // Show loading state
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
        
        // Fetch bidirectional relationships (both as source and target)
        const allRelationships = await window.BUDG_API_SERVICE.getGlossaryBidirectionalRelationships(glossaryId);
        console.log('API Response (bidirectional):', allRelationships);
        
        // Filter to only show outgoing relationships for editing (where glossary is source)
        // Incoming relationships are managed from the other glossary's edit page
        const outgoingRelationships = Array.isArray(allRelationships) 
            ? allRelationships.filter(rel => rel.direction === 'outgoing')
            : [];
        
        // Transform outgoing relationships to match the expected format for buildRelationshipRow
        const relationships = outgoingRelationships.map(rel => ({
            id: rel.id,
            sourceGlossaryId: rel.sourceGlossaryId,
            targetGlossaryId: rel.relatedGlossaryId, // Use relatedGlossaryId for target
            relationType: rel.relationType,
            relationTypeName: rel.relationTypeName,
            targetGlossaryName: rel.relatedGlossaryName,
            targetGlossaryType: rel.relatedGlossaryType,
            direction: rel.direction
        }));
        
        console.log('Filtered outgoing relationships for editing:', relationships);
        
        // Store initial relationship IDs for change detection
        initialRelationshipIds = new Set();
        if (Array.isArray(relationships)) {
            relationships.forEach(rel => {
                if (rel.id) {
                    initialRelationshipIds.add(String(rel.id));
                }
            });
        }
        console.log('Initial relationship IDs stored:', Array.from(initialRelationshipIds));
        
        if (!Array.isArray(relationships) || relationships.length === 0) {
            tbody.innerHTML = '';
            // Always append an empty row for adding new entries
            const blankRow = await buildRelationshipRow({ glossaryId, data: null });
            tbody.appendChild(blankRow);
            return;
        }

        // Render relationships table with edit functionality
        tbody.innerHTML = '';
        
        for (const rel of relationships) {
            const row = await buildRelationshipRow({ glossaryId, data: rel });
            tbody.appendChild(row);
        }
        
        // Populate dropdowns for existing relationships
        await populateExistingRelationships(relationships);

        // Always append an empty row for adding new entries
        const blankRow = await buildRelationshipRow({ glossaryId, data: null });
        tbody.appendChild(blankRow);

    } catch (error) {
        console.error('Failed to load relationships:', error);
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ' + error.message + '</td></tr>';
    }
}

// Build relationship row
async function buildRelationshipRow({ glossaryId, data = null }) {
    const row = document.createElement('tr');
    const isExisting = data && data.id != null;
    row.className = isExisting ? 'existing-row' : 'empty-row';

    if (isExisting) {
        row.setAttribute('data-id', data.id);
    }

    // Load relationship types and glossary list
    const [relationshipTypes, glossaryList] = await Promise.all([
        fetch('/api/glossary-relation-type/list').then(res => res.json()),
        window.BUDG_API_SERVICE.getGlossaryList()
    ]);

    // Update global glossary list
    globalGlossaryList = glossaryList;

    const relationshipTypeSelect = document.createElement('select');
    relationshipTypeSelect.className = 'form-select form-select-sm relationship-type-select';
    relationshipTypeSelect.innerHTML = '<option value="">Select Relationship Type</option>' +
        relationshipTypes.map(rt => `<option value="${rt.id}">${escapeHtml(rt.name || rt.primaryname || '')}</option>`).join('');
    // Handle multiple naming conventions: relationType (camelCase), relationTypeId (camelCase), or RelationType (PascalCase)
    const relationTypeId = data?.relationType || data?.relationTypeId || data?.RelationType;
    if (relationTypeId) {
        relationshipTypeSelect.value = String(relationTypeId);
        relationshipTypeSelect.setAttribute('data-original-value', String(relationTypeId));
    }

    const glossarySelect = document.createElement('select');
    glossarySelect.className = 'form-select form-select-sm glossary-select';
    glossarySelect.setAttribute('onchange', 'updateGlossaryType(this)');
    glossarySelect.innerHTML = '<option value="">Select Glossary</option>' +
        glossaryList.map(g => `<option value="${g.id}" data-type="${escapeHtml(g.typeName || '')}">${escapeHtml(g.name || '')}</option>`).join('');
    // Handle both naming conventions: targetGlossaryId (camelCase) or TargetGlossaryID (PascalCase)
    const targetGlossaryId = data?.targetGlossaryId || data?.TargetGlossaryID;
    if (targetGlossaryId) {
        glossarySelect.value = String(targetGlossaryId);
        glossarySelect.setAttribute('data-original-value', String(targetGlossaryId));
    }

    const typeInput = document.createElement('input');
    typeInput.type = 'text';
    typeInput.className = 'form-input form-input-sm glossary-type-input';
    typeInput.readOnly = true;
    typeInput.placeholder = 'Auto-filled';
    typeInput.style.cssText = 'background-color: #f8f9fa; color: #6c757d; font-style: italic;';
    // Handle both naming conventions: targetGlossaryType (camelCase) or TargetGlossaryType (PascalCase)
    const targetGlossaryType = data?.targetGlossaryType || data?.TargetGlossaryType;
    if (targetGlossaryType) {
        typeInput.value = targetGlossaryType;
        typeInput.style.fontStyle = 'normal';
        typeInput.style.color = 'inherit';
    }

    const relationshipTypeCell = document.createElement('td');
    relationshipTypeCell.appendChild(relationshipTypeSelect);

    const glossaryCell = document.createElement('td');
    glossaryCell.appendChild(glossarySelect);

    const typeCell = document.createElement('td');
    typeCell.appendChild(typeInput);

    const actionCell = document.createElement('td');
    actionCell.innerHTML = `
        <div class="action-buttons">
            <button type="button" class="btn btn-sm btn-success" onclick="addRelationship()" title="Add Row">
                <i class="fas fa-plus"></i>
            </button>
            <button type="button" class="btn btn-sm btn-danger" onclick="removeRelationship(this)" title="Remove Row">
                <i class="fas fa-minus"></i>
            </button>
        </div>
    `;

    row.appendChild(relationshipTypeCell);
    row.appendChild(glossaryCell);
    row.appendChild(typeCell);
    row.appendChild(actionCell);

    return row;
}

// Populate existing relationships with data
async function populateExistingRelationships(relationships) {
    try {
        // Load relationship types and glossary list
        const [relationshipTypes, glossaryList] = await Promise.all([
            fetch('/api/glossary-relation-type/list').then(res => res.json()),
            window.BUDG_API_SERVICE.getGlossaryList()
        ]);

        // Update global glossary list
        globalGlossaryList = glossaryList;

        // Populate each existing relationship row
        relationships.forEach(function(rel) {
            const row = document.querySelector(`tr[data-id="${rel.id}"]`);
            if (!row) return;

            const relationshipSelect = row.querySelector('.relationship-type-select');
            const glossarySelect = row.querySelector('.glossary-select');
            const typeInput = row.querySelector('.glossary-type-input');

            // Populate relationship types
            if (relationshipSelect) {
                // Clear existing options first (except the default one)
                relationshipSelect.innerHTML = '<option value="">Select Relationship Type</option>';
                
                // Handle multiple naming conventions: relationType (camelCase), relationTypeId (camelCase), or RelationType (PascalCase)
                const relRelationTypeId = rel.relationType || rel.relationTypeId || rel.RelationType;
                
                relationshipTypes.forEach(type => {
                    const option = document.createElement('option');
                    option.value = type.id;
                    option.textContent = type.name || type.primaryname || '';
                    if (relRelationTypeId && String(relRelationTypeId) === String(type.id)) {
                        option.selected = true;
                    }
                    relationshipSelect.appendChild(option);
                });
                
                // Set the value explicitly to ensure it's selected
                if (relRelationTypeId) {
                    relationshipSelect.value = String(relRelationTypeId);
                    relationshipSelect.setAttribute('data-original-value', String(relRelationTypeId));
                }
            }

            // Populate glossary items
            if (glossarySelect) {
                // Clear existing options first (except the default one)
                glossarySelect.innerHTML = '<option value="">Select Glossary</option>';
                
                // Handle multiple naming conventions: targetGlossaryId (camelCase) or TargetGlossaryID (PascalCase)
                const relTargetGlossaryId = rel.targetGlossaryId || rel.TargetGlossaryID;
                
                glossaryList.forEach(glossary => {
                    const option = document.createElement('option');
                    option.value = glossary.id;
                    option.textContent = glossary.name || '';
                    option.dataset.type = glossary.typeName || '';
                    if (relTargetGlossaryId && String(relTargetGlossaryId) === String(glossary.id)) {
                        option.selected = true;
                    }
                    glossarySelect.appendChild(option);
                });
                
                // Set the value explicitly to ensure it's selected
                if (relTargetGlossaryId) {
                    glossarySelect.value = String(relTargetGlossaryId);
                    glossarySelect.setAttribute('data-original-value', String(relTargetGlossaryId));
                }
            }

            // Set glossary type
            if (typeInput) {
                const relTargetGlossaryType = rel.targetGlossaryType || rel.TargetGlossaryType;
                if (relTargetGlossaryType) {
                    typeInput.value = relTargetGlossaryType;
                    typeInput.style.fontStyle = 'normal';
                    typeInput.style.color = 'inherit';
                }
            }
        });

    } catch (error) {
        console.error('Failed to populate existing relationships:', error);
    }
}

// Add new relationship row
async function addRelationship() {
    const tbody = document.getElementById('relationshipsTableBody');
    const glossaryId = parseId();

    try {
        const newRow = await buildRelationshipRow({ glossaryId, data: null });
        tbody.appendChild(newRow);
    } catch (error) {
        console.error('Failed to load data for new relationship:', error);
        alert('Failed to load data for new relationship');
    }
}

// Update glossary type when glossary is selected
function updateGlossaryType(selectElement) {
    const row = selectElement.closest('tr');
    const typeInput = row.querySelector('.glossary-type-input');
    const selectedOption = selectElement.options[selectElement.selectedIndex];
    const selectedValue = selectElement.value;

    if (selectedValue && globalGlossaryList.length > 0) {
        // Find the selected glossary in the global list
        const selectedGlossary = globalGlossaryList.find(g => g.id == selectedValue);
        if (selectedGlossary && selectedGlossary.typeName) {
            typeInput.value = selectedGlossary.typeName;
            typeInput.style.fontStyle = 'normal';
            typeInput.style.color = 'inherit';
            typeInput.placeholder = '';
        } else {
            typeInput.value = '';
            typeInput.placeholder = 'Auto-filled';
            typeInput.style.fontStyle = 'italic';
            typeInput.style.color = '#6c757d';
        }
    } else {
        typeInput.value = '';
        typeInput.placeholder = 'Auto-filled';
        typeInput.style.fontStyle = 'italic';
        typeInput.style.color = '#6c757d';
    }

    // Check if this is an existing row and mark as modified
    if (row.classList.contains('existing-row')) {
        checkRelationshipsChanges();
    }
}

// Get glossary type by ID (helper function)
function getGlossaryTypeById(glossaryId) {
    const glossary = globalGlossaryList.find(g => g.id == glossaryId);
    return glossary ? glossary.typeName : '';
}

// Remove relationship row
async function removeRelationship(button) {
    const row = button.closest('tr');
    const tbody = document.getElementById('relationshipsTableBody');
    const allRows = tbody.querySelectorAll('tr');
    const isFirstRow = row === allRows[0];
    const id = row.getAttribute('data-id');
    
    if (id) {
        // Delete from database
        try {
            const response = await fetch(`/api/glossary-x-glossary/${id}`, {
                method: 'DELETE'
            });

            if (response.ok) {
                // DO NOT remove from initialRelationshipIds here - we need to keep it
                // so that checkRelationshipsChanges() can detect the deletion
                // The ID will be removed when relationships are reloaded after save
                console.log('Relationship ID', id, 'deleted. Initial IDs still tracked:', Array.from(initialRelationshipIds));
                
                if (isFirstRow) {
                    // For first row, only clear the data but keep the row structure
                    await clearRelationshipRowData(row);
                } else {
                    // For other rows, remove the entire row
                    row.remove();
                }
            } else {
                const error = await response.json();
                alert('Failed to delete relationship: ' + (error.message || 'Unknown error'));
            }
        } catch (error) {
            console.error('Failed to delete relationship:', error);
            alert('Failed to delete relationship: ' + (error.message || 'Unknown error'));
        }
    } else {
        // For empty rows (no data-id)
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
    const glossaryId = parseId();
    const oldId = row.getAttribute('data-id');
    
    console.log('Clearing relationship row data. Old ID:', oldId, 'Initial IDs before clear:', Array.from(initialRelationshipIds));

    try {
        const newRow = await buildRelationshipRow({ glossaryId, data: null });
        row.replaceWith(newRow);
        console.log('Row cleared. Initial IDs after clear (should still contain old ID):', Array.from(initialRelationshipIds));
    } catch (error) {
        console.error('Failed to clear row data:', error);
    }
}

// Check if there are relationship changes
function checkRelationshipsChanges() {
    const tbody = document.getElementById('relationshipsTableBody');
    if (!tbody) return false;

    const emptyRows = tbody.querySelectorAll('.empty-row');
    const existingRows = tbody.querySelectorAll('.existing-row');
    
    // Check for deletions: compare current existing rows with initial relationship IDs
    const currentRelationshipIds = new Set();
    existingRows.forEach(row => {
        const id = row.getAttribute('data-id');
        if (id) {
            currentRelationshipIds.add(String(id));
        }
    });
    
    console.log('=== CHECKING RELATIONSHIP CHANGES ===');
    console.log('Initial relationship IDs:', Array.from(initialRelationshipIds));
    console.log('Current relationship IDs:', Array.from(currentRelationshipIds));
    
    // If any initial relationship is missing from current rows, there's a deletion
    for (const initialId of initialRelationshipIds) {
        if (!currentRelationshipIds.has(initialId)) {
            console.log('✅ Deletion detected: relationship ID', initialId, 'was removed');
            console.log('Initial IDs:', Array.from(initialRelationshipIds), 'Current IDs:', Array.from(currentRelationshipIds));
            return true; // Found a deleted relationship
        }
    }
    
    // Check if there are any empty rows with data to save
    for (const row of emptyRows) {
        const relationshipTypeId = row.querySelector('.relationship-type-select')?.value;
        const targetGlossaryId = row.querySelector('.glossary-select')?.value;
        if (relationshipTypeId && targetGlossaryId && relationshipTypeId !== '' && targetGlossaryId !== '') {
            return true; // Found a new relationship to save
        }
    }
    
    // Check if there are any existing rows with changes
    for (const row of existingRows) {
        const relationshipId = row.getAttribute('data-id');
        const relationshipTypeId = row.querySelector('.relationship-type-select')?.value;
        const targetGlossaryId = row.querySelector('.glossary-select')?.value;
        const originalRelationTypeId = row.querySelector('.relationship-type-select')?.getAttribute('data-original-value');
        const originalTargetGlossaryId = row.querySelector('.glossary-select')?.getAttribute('data-original-value');
        
        if (relationshipId && (relationshipTypeId !== originalRelationTypeId || targetGlossaryId !== originalTargetGlossaryId)) {
            return true; // Found a changed relationship
        }
    }
    
    // Check empty rows for new relationships
    for (const row of emptyRows) {
        const relationshipTypeId = row.querySelector('.relationship-type-select')?.value;
        const targetGlossaryId = row.querySelector('.glossary-select')?.value;
        
        // If any field has a value, there are changes
        if (relationshipTypeId || targetGlossaryId) {
            return true;
        }
    }

    // Check existing rows for modifications
    for (const row of existingRows) {
        const relationshipTypeId = row.querySelector('.relationship-type-select')?.value;
        const targetGlossaryId = row.querySelector('.glossary-select')?.value;
        const originalRelationTypeId = row.querySelector('.relationship-type-select')?.getAttribute('data-original-value');
        const originalTargetGlossaryId = row.querySelector('.glossary-select')?.getAttribute('data-original-value');

        // If values have changed, there are modifications
        if (relationshipTypeId !== originalRelationTypeId || targetGlossaryId !== originalTargetGlossaryId) {
            // Add visual indicator for modified rows
            row.classList.add('modified-row');
            return true;
        } else {
            // Remove visual indicator if values match original
            row.classList.remove('modified-row');
        }
    }

    return false;
}

// Save relationships data to database
async function saveRelationshipsData(glossaryId) {
    console.log('=== SAVING RELATIONSHIPS DATA ===');
    console.log('Glossary ID:', glossaryId);
    
    const tbody = document.getElementById('relationshipsTableBody');
    if (!tbody) {
        console.warn('relationshipsTableBody not found');
        return true;
    }

    const emptyRows = tbody.querySelectorAll('.empty-row');
    const existingRows = tbody.querySelectorAll('.existing-row');
    console.log('Found empty rows:', emptyRows.length);
    console.log('Found existing rows:', existingRows.length);
    
    let savedCount = 0;
    let hadError = false;

    // Save new relationships (empty rows)
    for (const row of emptyRows) {
        const relationshipTypeSelect = row.querySelector('.relationship-type-select');
        const glossarySelect = row.querySelector('.glossary-select');
        const relationshipTypeId = relationshipTypeSelect?.value?.trim() || '';
        const targetGlossaryId = glossarySelect?.value?.trim() || '';

        console.log('Processing empty row:', {
            hasRelationshipTypeSelect: !!relationshipTypeSelect,
            hasGlossarySelect: !!glossarySelect,
            relationshipTypeId: relationshipTypeId,
            targetGlossaryId: targetGlossaryId
        });

        // Only save if both relationship type and target glossary are selected
        if (relationshipTypeId && targetGlossaryId && relationshipTypeId !== '' && targetGlossaryId !== '') {
            const parsedRelationType = parseInt(relationshipTypeId, 10);
            const parsedTargetGlossaryId = parseInt(targetGlossaryId, 10);
            const sourceGlossaryId = parseInt(glossaryId, 10);
            
            if (isNaN(parsedRelationType) || isNaN(parsedTargetGlossaryId)) {
                console.error('Cannot save relationship: invalid numeric values', {
                    parsedRelationType,
                    parsedTargetGlossaryId
                });
                alert('Cannot save relationship: Invalid Relationship Type or Glossary ID');
                hadError = true; 
                continue;
            }
            // Prevent self-relationship on the client for immediate feedback
            if (sourceGlossaryId === parsedTargetGlossaryId) {
                alert('This relationship is not allowed: a glossary cannot relate to itself.');
                hadError = true; 
                continue;
            }
            
            try {
                console.log('Saving new relationship:', {
                    sourceGlossaryId: glossaryId,
                    targetGlossaryId: parsedTargetGlossaryId,
                    relationType: parsedRelationType
                });

                const response = await window.BUDG_API_SERVICE.createGlossaryRelationship({
                    sourceGlossaryId: glossaryId,
                    targetGlossaryId: parsedTargetGlossaryId,
                    relationType: parsedRelationType
                });

                console.log('Save relationship response:', response);

                // Check for error in response
                if (response && response.error) {
                    const errorMsg = response.error || response.message || 'Unknown error';
                    console.error('Failed to save relationship:', errorMsg, response);
                    alert(`Failed to save relationship: ${errorMsg}`);
                    hadError = true; 
                    continue;
                }
                
                // Check for success
                if (response && (response.success === true || (response.success !== false && !response.error))) {
                    savedCount++;
                    console.log('Relationship saved successfully:', response);
                } else {
                    const errorMsg = response?.error || response?.message || 'Unknown error';
                    console.error('Failed to save relationship:', errorMsg, response);
                    alert(`Failed to save relationship: ${errorMsg}`);
                    hadError = true; 
                }
            } catch (error) {
                console.error('Failed to save relationship:', error);
                alert(`Failed to save relationship: ${error.message || error}`);
                hadError = true; 
            }
        }
    }

    // Update existing relationships
    for (const row of existingRows) {
        const relationshipId = row.getAttribute('data-id');
        const relationshipTypeSelect = row.querySelector('.relationship-type-select');
        const glossarySelect = row.querySelector('.glossary-select');
        const relationshipTypeId = relationshipTypeSelect?.value?.trim() || '';
        const targetGlossaryId = glossarySelect?.value?.trim() || '';
        const originalRelationTypeId = relationshipTypeSelect?.getAttribute('data-original-value')?.trim() || '';
        const originalTargetGlossaryId = glossarySelect?.getAttribute('data-original-value')?.trim() || '';

        // Only update if values have changed
        if (relationshipId && (relationshipTypeId !== originalRelationTypeId || targetGlossaryId !== originalTargetGlossaryId)) {
            // Validate that we have valid values
            const finalRelationTypeId = relationshipTypeId || originalRelationTypeId;
            const finalTargetGlossaryId = targetGlossaryId || originalTargetGlossaryId;
            
            if (!finalRelationTypeId || !finalTargetGlossaryId) {
                console.error('Cannot update relationship: missing required values', {
                    relationshipId,
                    finalRelationTypeId,
                    finalTargetGlossaryId
                });
                alert('Cannot update relationship: Relationship Type and Glossary are required');
                continue;
            }
            
            const parsedRelationType = parseInt(finalRelationTypeId, 10);
            const parsedTargetGlossaryId = parseInt(finalTargetGlossaryId, 10);
            const sourceGlossaryId = parseInt(glossaryId, 10);
            
            if (isNaN(parsedRelationType) || isNaN(parsedTargetGlossaryId)) {
                console.error('Cannot update relationship: invalid numeric values', {
                    relationshipId,
                    parsedRelationType,
                    parsedTargetGlossaryId
                });
                alert('Cannot update relationship: Invalid Relationship Type or Glossary ID');
                hadError = true; 
                continue;
            }
            // Prevent self-relationship on the client for immediate feedback
            if (sourceGlossaryId === parsedTargetGlossaryId) {
                alert('Update would create an invalid relationship: a glossary cannot relate to itself.');
                hadError = true; 
                continue;
            }
            
            try {
                console.log('Updating relationship:', {
                    relationshipId: relationshipId,
                    sourceGlossaryId: glossaryId,
                    targetGlossaryId: parsedTargetGlossaryId,
                    relationType: parsedRelationType
                });

                const response = await window.BUDG_API_SERVICE.updateGlossaryRelationship(relationshipId, {
                    sourceGlossaryId: glossaryId,
                    targetGlossaryId: parsedTargetGlossaryId,
                    relationType: parsedRelationType
                });

                console.log('Update relationship response:', response);

                // Check for error in response
                if (response && response.error) {
                    const errorMsg = response.error || response.message || 'Unknown error';
                    console.error('Failed to update relationship:', errorMsg, response);
                    alert(`Failed to update relationship: ${errorMsg}`);
                    hadError = true; 
                    continue;
                }
                
                // Check for success
                if (response && (response.success === true || (response.success !== false && !response.error))) {
                    savedCount++;
                    console.log('Relationship updated successfully');
                    // Update the original values after successful save
                    if (relationshipTypeSelect) relationshipTypeSelect.setAttribute('data-original-value', relationshipTypeId || originalRelationTypeId);
                    if (glossarySelect) glossarySelect.setAttribute('data-original-value', targetGlossaryId || originalTargetGlossaryId);
                } else {
                    const errorMsg = response?.error || response?.message || 'Unknown error';
                    console.error('Failed to update relationship:', errorMsg, response);
                    alert(`Failed to update relationship: ${errorMsg}`);
                    hadError = true; 
                }
            } catch (error) {
                console.error('Failed to update relationship:', error);
                alert(`Failed to update relationship: ${error.message || error}`);
                hadError = true; 
            }
        }
    }

    console.log(`=== RELATIONSHIPS SAVE COMPLETE ===`);
    console.log(`Total saved/updated: ${savedCount} relationship(s)`);
    
    if (hadError) {
        // If any error occurred (including prevention), do not show success outside.
        return false;
    }

    if (savedCount > 0) {
        // Add a small delay to ensure database transaction is committed
        await new Promise(resolve => setTimeout(resolve, 100));
        // Reload relationships data to show the newly saved relationships
        console.log('Reloading relationships data after save...');
        await loadRelationshipsData(glossaryId);
        console.log('Relationships data reloaded');
    } else {
        console.warn('No relationships were saved. Check if rows have valid data.');
    }
    return true;
}

// =============================================
// DOCUMENTS SECTION
// =============================================

// Store document table instance for view mode switching
let glossaryEditDocumentTable = null;

/**
 * Load and display documents for the glossary edit page
 */
async function loadDocuments(glossaryId, viewMode = null) {
    // Use #documentTableContainer only — do not replace #documentsContainer (that removes upload UI)
    const tableHost = document.getElementById('documentTableContainer');
    if (!tableHost) {
        console.warn('[Documents] documentTableContainer not found');
        return;
    }

    if (typeof DocumentTableComponent !== 'undefined') {
        try {
            if (glossaryEditDocumentTable && typeof glossaryEditDocumentTable.setViewMode === 'function') {
                glossaryEditDocumentTable.facetId = glossaryId;
                glossaryEditDocumentTable.setViewMode(viewMode);
                window.glossaryDocumentTableComponent = glossaryEditDocumentTable;
            } else {
                glossaryEditDocumentTable = new DocumentTableComponent({
                    facetType: 'glossary',
                    facetId: glossaryId,
                    container: '#documentTableContainer',
                    canEdit: true,
                    viewMode: viewMode
                });
            }
            window.glossaryDocumentTableComponent = glossaryEditDocumentTable;
            console.log('[Documents] Document table initialized');
        } catch (error) {
            console.error('[Documents] Error initializing document table:', error);
            tableHost.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">' + (window.I18n ? window.I18n.t('message.failedToLoadDocuments') : 'Failed to load documents.') + '</div>';
        }
    } else {
        console.error('[Documents] DocumentTableComponent not found');
        tableHost.innerHTML = '<div class="empty">Document component not available.</div>';
    }
}
