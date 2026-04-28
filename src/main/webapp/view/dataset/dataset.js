(function() {
    const DATASET_CUSTOM_FACET = (document.body && document.body.getAttribute('data-custom-facet')) || 'Data Sets';
    function dT(key, fallback, params) {
        if (!window.I18n || typeof window.I18n.t !== 'function') return fallback != null ? fallback : key;
        const str = window.I18n.t(key, params);
        if (str === key && fallback != null) return fallback;
        return str;
    }
    let currentView = 'original'; // 'original' | 'changes'
    let isReloading = false; // Prevent re-entrant calls during view switch
    let _canEditObject = false; // Whether user can normally edit this object (without auto CR restriction)
    let _hasActiveAutoCR = false; // Whether an active auto CR is blocking edits

    /**
     * Central function to update page title
     * @param {number} id - Dataset ID
     * @param {string} view - 'original' or 'changes'
     * @param {Object} datasetData - Optional dataset data object (if already loaded, avoids extra API call)
     * @returns {Promise<void>}
     */
    async function updatePageTitle(id, view, datasetData = null) {
        try {
            const titleElement = document.getElementById('datasetPageTitle');
            if (!titleElement) {
                console.warn('[Dataset] Title element (datasetPageTitle) not found');
                return;
            }

            let name = null;
            
            // Use provided data if available, otherwise fetch
            if (datasetData && datasetData.name) {
                name = datasetData.name;
            } else {
                try {
                    const data = await window.BUDG_API_SERVICE.getDatasetById(id, view === 'changes' ? 'changes' : null);
                    if (data && data.name) {
                        name = data.name;
                    }
                } catch (apiError) {
                    // If API call fails (e.g., database error, 500), don't update title
                    // Log the error but don't throw - this prevents cascading failures
                    console.warn('[Dataset] Failed to fetch dataset name for title update:', apiError.message || apiError);
                    return; // Exit early without updating title
                }
            }

            if (name) {
                titleElement.textContent = name;
                console.log('[Dataset] Page title updated to:', name);
            } else {
                console.warn('[Dataset] Dataset name not found for ID:', id);
            }
        } catch (error) {
            // Catch any unexpected errors and log them without breaking the flow
            console.error('[Dataset] Unexpected error updating page title:', error);
        }
    }

    /**
     * Update title for view switch events (same pattern as Glossary and System)
     */
    async function updateTitleOnly(id, view) {
        try {
            const titleElement = document.getElementById('datasetPageTitle');
            if (titleElement) {
                // Fetch dataset data to get updated name
                const d = await window.BUDG_API_SERVICE.getDatasetById(id, view === 'changes' ? 'changes' : null);
                if (d && d.name) {
                    // Remove data-i18n attribute to prevent i18n.applyTranslations() from overwriting the title
                    titleElement.removeAttribute('data-i18n');
                    titleElement.textContent = d.name;
                    console.log('[Dataset] Title updated via updateTitleOnly to:', d.name);
                }
            }
        } catch (error) {
            console.error('[Dataset] Error updating title:', error);
        }
    }

    function getCurrentViewMode() {
        // MANDATORY: Always default to 'original' view on page load/refresh
        // User can manually switch to 'changes' via toggle, but default is always 'original'
        const activeToggle = document.querySelector('.revision-toggle-link.active');
        if (activeToggle) {
            const v = activeToggle.dataset.view;
            if (v === 'original' || v === 'changes') {
                currentView = v;
                return v;
            }
        }
        
        // Always default to 'original' - clear any 'changes' from sessionStorage
        try {
            const id = parseId();
            if (id != null) {
                const key = `pendingChanges:view:Dataset#${id}`;
                if (typeof sessionStorage !== 'undefined') {
                    // Clear any saved 'changes' view to ensure default is always 'original'
                    const saved = sessionStorage.getItem(key);
                    if (saved === 'changes') {
                        sessionStorage.removeItem(key);
                    }
                }
            }
        } catch (e) {
            // ignore storage errors
        }
        
        // Always default to 'original' - mandatory requirement
        currentView = 'original';
        return 'original';
    }

    // Guest / segment access control for Dataset view
    let _datasetGuestCache = null;
    async function isGuestVisitorForDataset() {
        if (_datasetGuestCache !== null) {
            return _datasetGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _datasetGuestCache = true;
                return _datasetGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _datasetGuestCache = !isAuthenticated || isGuestRole;
            return _datasetGuestCache;
        } catch (_) {
            _datasetGuestCache = true;
            return _datasetGuestCache;
        }
    }

    function renderDatasetGuestAccessDenied(container, datasetId, segmentName, segmentId) {
        if (!container) return;
        const title = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
        const message = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.message')) || 'This item is not available for guest users.';
        const code = 'ERR-SEGMENT-NOT-ENTERPRISE';
        const segmentLabel = segmentName || ((segmentId !== undefined && segmentId !== null) ? `ID ${segmentId}` : 'Unknown');
        const esc = (text) => {
            if (text == null) return '';
            const div = document.createElement('div');
            div.textContent = String(text);
            return div.innerHTML;
        };
        container.innerHTML = `
            <div class="view-section" style="grid-column: 1/-1; text-align: center; padding: 3rem 1rem;">
                <div class="access-denied-title" style="font-size: 1.5rem; font-weight: 600; margin-bottom: 0.75rem;">${esc(title)}</div>
                <div class="access-denied-message" style="margin-bottom: 1.25rem; color: var(--text-muted, #4b5563);">
                    ${esc(message)}
                </div>
                <div class="access-denied-details" style="font-size: 0.95rem; color: var(--text-muted, #6b7280);">
                    <div style="margin-bottom: 0.5rem;">Segment: <strong>${esc(segmentLabel)}</strong></div>
                    <div>Error code: <code>${code}</code></div>
                </div>
            </div>
        `;
    }
    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        // expect /view/dataset/{id}
        const idx = parts.indexOf('dataset');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

// Robust hide function: hides existing elements and watches for elements added later
function hideEditControls() {
    // Hide the edit dropdown for unauthorized users
    if (window.EditDropdown) {
        window.EditDropdown.hideEditControls();
    }
    try {
        // Hide any other edit controls
        const nodes = document.querySelectorAll('[data-action="edit"]');
        nodes.forEach(el => {
            if (!el) return;
            try {
                el.style.setProperty('display', 'none', 'important');
            } catch (_) {
                el.style.display = 'none';
            }
            el.setAttribute('data-hidden-by', 'hideEditControls');
            try { if (typeof el.blur === 'function') el.blur(); } catch(_) {}
        });
    } catch (err) {
        console.error('hideEditControls error:', err);
    }
}

// ensure: run hideEditControls immediately and observe DOM for new matching nodes
function ensureHideEditControls(options = {}) {
    const timeout = typeof options.timeout === 'number' ? options.timeout : 15000; // stop observing after 15s
    hideEditControls();

    // If the page adds elements later (e.g. via XHR/templating), observe and hide them when they appear
    const observer = new MutationObserver(mutations => {
        let found = false;
        for (const m of mutations) {
            for (const node of m.addedNodes) {
                if (!(node instanceof Element)) continue;
                if (node.matches && node.matches('#tabEditBtn')) {
                    found = true;
                } else if (node.querySelector && node.querySelector('#tabEditBtn')) {
                    found = true;
                }
            }
        }
        if (found) hideEditControls();
    });

    // observe body (or documentElement) for subtree changes
    const target = document.body || document.documentElement;
    try {
        observer.observe(target, { childList: true, subtree: true });
    } catch (e) {
        // ignore observe errors
    }

    // Stop observing after timeout to avoid long-lived observer
    setTimeout(() => {
        try { observer.disconnect(); } catch(_) {}
    }, timeout);
}

// Auto-run on load (safe)
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => ensureHideEditControls());
} else {
    ensureHideEditControls();
}

// ---- Optional: integrate with your auth-check function ----
// Replace calls to hideEditControls() with ensureHideEditControls() to get the robust behavior:

async function hideEditsIfUnauthenticated() {
    try {
        const id = parseId();
        if (!id) {
            console.log('[Dataset] No ID found - hiding edit controls');
            ensureHideEditControls();
            return;
        }

        // Use the new permissions API to check user permissions for Data Sets module
        const permResp = await fetch('/api/user/permissions/Data Sets', { method: 'GET', credentials: 'include' });
        if (!permResp.ok) { 
            console.log('API /api/user/permissions/Data Sets failed:', permResp.status);
            // Fallback to old method
            await hideEditsIfUnauthenticatedFallback();
            return; 
        }
        const perms = await permResp.json();
        console.log('User permissions for Data Sets:', perms);
        
        if (perms.success) {
            const hasRoleEditPermission = perms.canEdit || perms.isAdmin;
            const canDelete = perms.canDelete || perms.isAdmin;
            const isAdmin = perms.isAdmin;
            
            console.log('Has role edit permission?', hasRoleEditPermission);
            console.log('Can delete?', canDelete);
            console.log('Is admin?', isAdmin);
            
            // Check if user can edit this specific object (has permission AND is stakeholder)
            let canEditObject = false;
            if (isAdmin) {
                canEditObject = true; // Admins can always edit
            } else if (hasRoleEditPermission) {
                // Check stakeholder status if user has role permission
                try {
                    const stakeholderResp = await fetch(`/api/check-stakeholder/Data Sets/${id}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (stakeholderResp.ok) {
                        const stakeholderData = await stakeholderResp.json();
                        canEditObject = stakeholderData.isStakeholder === true;
                        console.log('[Dataset] User is stakeholder?', canEditObject);
                    } else {
                        console.warn('[Dataset] Failed to check stakeholder status:', stakeholderResp.status);
                        canEditObject = false; // Fail securely
                    }
                } catch (stakeholderError) {
                    console.error('[Dataset] Error checking stakeholder status:', stakeholderError);
                    canEditObject = false; // Fail securely
                }
            }
            
            // Store canEditObject at module level for stakeholder tab redirect
            _canEditObject = canEditObject;
            
            // Show the main edit dropdown only if user can edit this object
            if (canEditObject) {
                console.log('[Dataset] User can edit this object - showing edit controls');
                showEditControls();
            } else {
                console.log('[Dataset] User cannot edit this object - hiding edit controls');
                ensureHideEditControls();
            }
            
            // Hide delete buttons for non-admins (only admin/super admin can delete)
            if (!canDelete) {
                hideDeleteControls();
            }
            
            // Apply permission visibility to the dropdown
            if (window.EditDropdown && typeof window.EditDropdown.applyPermissionVisibility === 'function') {
                window.EditDropdown.applyPermissionVisibility({
                    canEdit: canEditObject,
                    canDelete: canDelete,
                    isAdmin: isAdmin
                });
            }
        } else {
            ensureHideEditControls();
        }
    } catch (e) {
        console.log('Error in hideEditsIfUnauthenticated:', e);
        // Fallback to old method
        await hideEditsIfUnauthenticatedFallback();
    }
}

// Fallback to old admin check method
async function hideEditsIfUnauthenticatedFallback() {
    try {
        const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
        if (!meResp.ok) { 
            ensureHideEditControls(); 
            return; 
        }
        const me = await meResp.json();
        const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
        const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
        if (!isAdmin) {
            ensureHideEditControls();
        } else {
            showEditControls();
        }
    } catch (e) {
        ensureHideEditControls();
    }
}

function hideDeleteControls() {
    // Hide delete buttons - only admin/super admin can delete
    const deleteSelectors = [
        '[data-action="delete"]',
        '.delete-btn',
        '.btn-delete',
        '#deleteBtn',
        '#deleteDatasetBtn'
    ];
    deleteSelectors.forEach(selector => {
        try {
            document.querySelectorAll(selector).forEach(el => {
                el.style.display = 'none';
            });
        } catch (_) {}
    });
    console.log('Delete controls hidden - only admin can delete');
}

function showEditControls() {
    // Show the edit dropdown for authorized users
    if (window.EditDropdown) {
        window.EditDropdown.showEditControls();
    }
    try {
        // Show any other edit controls
        const nodes = document.querySelectorAll('[data-action="edit"]');
        nodes.forEach(el => {
            if (!el) return;
            el.style.display = '';
            el.style.setProperty('display', '', 'important');
            el.removeAttribute('data-hidden-by');
        });
        console.log('Edit controls shown for admin user');
    } catch (err) {
        console.error('showEditControls error:', err);
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

    function _richHtml(val) {
        if (val == null || String(val).trim() === '') return '<span class="empty">-</span>';
        var s = String(val);
        if (!/<[a-z][\s\S]*>/i.test(s)) return escapeHtml(s);
        var t = document.createElement('template'); t.innerHTML = s;
        t.content.querySelectorAll('script,style,iframe,object,embed,link,meta').forEach(function(e){e.remove();});
        t.content.querySelectorAll('*').forEach(function(el){
            Array.from(el.attributes).forEach(function(a){
                if(a.name.toLowerCase().indexOf('on')===0) el.removeAttribute(a.name);
                if((a.name==='href'||a.name==='src')&&/^\s*javascript:/i.test(a.value)) el.removeAttribute(a.name);
            });
        });
        return '<div class="rich-html-content">' + t.innerHTML + '</div>';
    }

    // Helper function to create entity links for relationships
    function createRelationshipLink(entityType, id, name, datasetId = null) {
        if (!id || !name || name === 'N/A' || name === '') {
            return escapeHtml(name || '');
        }
        
        let url;
        switch(entityType) {
            case 'system':
                url = `/view/system/${encodeURIComponent(id)}`;
                break;
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(id)}`;
                break;
            case 'interface':
                url = `/view/system-interface/${encodeURIComponent(id)}`;
                break;
            case 'attribute':
                // Attributes link to dataset with attribute highlighted
                if (datasetId) {
                    url = `/view/dataset/${encodeURIComponent(datasetId)}?tab=attribute&attributeId=${encodeURIComponent(id)}`;
                } else {
                    return escapeHtml(name);
                }
                break;
            default:
                return escapeHtml(name);
        }
        
        const viewText = window.I18n?.t('dataset.messages.view') || 'View';
        return `<a href="${url}" class="relationship-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${viewText} ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    function renderItem(label, valueHtml, fieldKey) {
        const isEmpty = valueHtml == null || valueHtml === '' || valueHtml === 'null' || valueHtml === 'undefined';
        const displayValue = isEmpty ? '<span class="empty">-</span>' : valueHtml;
        const dataAttr = fieldKey ? ` data-field="${fieldKey}"` : '';

        // Translate the label (not the value - data from backend stays as is)
        let translatedLabel = label;
        if (window.I18n && typeof window.I18n.t === 'function' && window.I18n.translations && Object.keys(window.I18n.translations).length > 0) {
            const normalizedLabel = label.replace(/\s+/g, ' ').trim().replace(/\./g, '').replace(/:/g, '');
            const translationKeys = [];

            // Pattern 1: Try fieldKey if provided (e.g., 'label.name' for fieldKey 'name')
            if (fieldKey) {
                translationKeys.push('label.' + fieldKey);
            }
            // Pattern 2: Convert "Short Name" to "label.shortName" (camelCase)
            const camelCaseKey = normalizedLabel.replace(/\s+/g, '');
            if (camelCaseKey.length > 0) {
                translationKeys.push('label.' + camelCaseKey.charAt(0).toLowerCase() + camelCaseKey.slice(1));
            }
            // Pattern 3: Convert "Short Name" to "label.ShortName" (PascalCase)
            if (camelCaseKey.length > 0) {
                translationKeys.push('label.' + camelCaseKey);
            }
            // Pattern 4: Try exact label match
            translationKeys.push('label.' + normalizedLabel);
            // Pattern 5: Try lowercase version
            translationKeys.push('label.' + normalizedLabel.toLowerCase());

            for (const trKey of translationKeys) {
                const tr = window.I18n.t(trKey);
                if (tr && tr !== trKey && typeof tr === 'string') {
                    translatedLabel = tr;
                    break;
                }
            }
        }
        return `<div class="view-item"><div class="view-label">${translatedLabel}</div><div class="view-value"${dataAttr}>${displayValue}</div></div>`;
    }

    function renderUserLink(name, id) {
        if (!name) return '<span class="empty">-</span>';
        if (id) {
            return `<a href="/view/people/${id}" class="user-link">${escapeHtml(name)}</a>`;
        }
        return escapeHtml(name);
    }

    // Check and display lock status (using new lock-ui styles)
    async function checkAndDisplayLockStatus(datasetId) {
        try {
            const lockInfo = await window.BUDG_API_SERVICE.checkLock('dataset', datasetId);
            const status = lockInfo?.status || 'no_lock';
            
            // Show lock indicator if locked by someone else (not the current user)
            if (status === 'locked_by_other' || status === 'permanently_locked') {
                const lockedBy = lockInfo.lockedByName || (window.I18n?.t('dataset.lock.unknown') || 'Unknown');
                const isPermanent = lockInfo.isPermanent || false;
                
                // Add red lock icon in form-actions for permanent locks by other users
                // Show for permanent locks regardless of status (permanently_locked or locked_by_other)
                if (isPermanent) {
                    let topLockIndicator = document.getElementById('topLockIndicator');
                    if (!topLockIndicator) {
                        const formActions = document.querySelector('.form-actions');
                        if (formActions) {
                            topLockIndicator = document.createElement('div');
                            topLockIndicator.id = 'topLockIndicator';
                            topLockIndicator.className = 'view-lock-indicator-top';
                            topLockIndicator.style.cssText = 'background: #dc3545; color: white; padding: 8px 12px; border-radius: 4px; display: flex; align-items: center; gap: 8px;';
                            topLockIndicator.innerHTML = `
                                <i class="fas fa-lock"></i>
                                <span>Locked by ${escapeHtml(lockedBy)}</span>
                            `;
                            // Insert at the beginning of form-actions
                            formActions.insertBefore(topLockIndicator, formActions.firstChild);
                        }
                    }
                }
                
                // Add lock indicator next to edit button
                const tabEditBtn = document.getElementById('tabEditBtn');
                if (tabEditBtn) {
                    const lockText = isPermanent ? (window.I18n?.t('dataset.lock.permanentlyLocked') || 'Permanently Locked') : (window.I18n?.t('dataset.lock.locked') || 'Locked');
                    const lockedSince = lockInfo.createdDatetime ? new Date(lockInfo.createdDatetime).toLocaleString() : '';
                    
                    let lockIndicator = document.getElementById('lockIndicator');
                    if (!lockIndicator) {
                        lockIndicator = document.createElement('div');
                        lockIndicator.id = 'lockIndicator';
                        lockIndicator.className = isPermanent ? 'view-lock-indicator' : 'view-lock-indicator temporary';
                        const lockTypeText = isPermanent ? (window.I18n?.t('dataset.lock.permanent') || 'Permanent') : (window.I18n?.t('dataset.lock.temporary') || 'Temporary');
                        const sinceText = window.I18n?.t('dataset.lock.since') || 'Since:';
                        const lockedByText = window.I18n?.t('dataset.lock.lockedBy') || 'Locked by:';
                        const lockTypeLabel = window.I18n?.t('dataset.lock.lockType') || 'Lock type:';
                        lockIndicator.title = `${lockedByText} ${lockedBy}\n${lockTypeLabel} ${lockTypeText}\n${lockedSince ? sinceText + ' ' + lockedSince : ''}`;
                        const byText = window.I18n?.t('common.by') || 'by';
                        lockIndicator.innerHTML = `
                            <i class="fas fa-${isPermanent ? 'lock' : 'lock-open'}"></i>
                            <span>${lockText} ${byText} ${escapeHtml(lockedBy)}</span>
                        `;
                        tabEditBtn.parentElement.appendChild(lockIndicator);
                    }
                }
            }
        } catch (error) {
            console.error('Failed to check lock status:', error);
        }
    }

    async function load(id, view = null) {
        const container = document.getElementById('datasetViewContainer');
        if (!container) {
            console.error('[Dataset] Container not found: datasetViewContainer');
            return;
        }

        const urlTab = new URLSearchParams(window.location.search).get('tab');
        const isDetailsTab = urlTab !== 'attribute' && urlTab !== 'stakeholders' && urlTab !== 'values' && urlTab !== 'impact' && urlTab !== 'relationships' && urlTab !== 'history' && urlTab !== 'change';
        
        // Ensure container is visible only when Details tab is active (avoids showing summary when URL has tab=attribute)
        if (isDetailsTab) {
            container.style.display = 'flex'; // view-grid uses flex in view.css
            container.style.flexDirection = 'column';
            container.style.visibility = 'visible';
            container.style.opacity = '1';
            container.style.gap = '1.25rem';
        }
        
        // Also ensure content-body is visible
        const contentBody = document.querySelector('.content-body');
        if (contentBody) {
            contentBody.style.display = 'flex';
            contentBody.style.flexDirection = 'column';
            contentBody.style.visibility = 'visible';
            contentBody.style.opacity = '1';
            console.log('[Dataset] Content-body set to visible in load function');
        } else {
            console.error('[Dataset] Content-body not found in load function!');
        }
        
        const loadingText = window.I18n?.t('dataset.loading.loading') || 'Loading...';
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${loadingText}</div>`;
        try {
            // Load dataset data FIRST to get the name for title
            // If view is still null, get it from toggle state or use default 'original'
            if (view === null) {
                view = getCurrentViewMode();
                currentView = view;
            }
            
            // Load dataset data with the correct view
            let d = null;
            try {
                d = await window.BUDG_API_SERVICE.getDatasetById(id, view === 'changes' ? 'changes' : null);
            } catch (error) {
                console.error('[Dataset] Failed to load dataset data:', error);
                const isForbidden = error?.status === 403 || String(error?.message || '').includes('403');
                const errorMessage = isForbidden
                    ? 'This object is not available.'
                    : (error.message || 'Database connection failed. Please check your connection and try again.');
                container.innerHTML = `<div class="view-section" style="grid-column: 1/-1; color: red; padding: 1rem;">
                    <strong>Error loading dataset:</strong> ${errorMessage}
                </div>`;
                return; // Exit early if data load fails
            }
            
            // Guest / segment gating: unauthenticated users may only view Enterprise segment
            try {
                const isGuest = await isGuestVisitorForDataset();
                const segmentName = d?.segmentName || d?.segment_name || d?.segment;
                const segmentId = d?.segmentId ?? d?.segment_id ?? d?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();

                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Dataset] Guest visitor blocked from non-Enterprise segment view', {
                        datasetId: id,
                        segmentName,
                        segmentId
                    });
                    renderDatasetGuestAccessDenied(container, id, segmentName, segmentId);
                    return;
                }
            } catch (gateError) {
                console.warn('[Dataset] Error during guest/segment access check, falling back to normal render:', gateError);
            }

            // Update page title IMMEDIATELY after loading data (same pattern as Glossary and System)
            const titleElement = document.getElementById('datasetPageTitle');
            if (titleElement) {
                if (d && d.name) {
                    // Remove data-i18n attribute to prevent i18n.applyTranslations() from overwriting the title
                    titleElement.removeAttribute('data-i18n');
                    titleElement.textContent = d.name;
                    console.log('[Dataset] Title updated to:', d.name);
                } else {
                    console.warn('[Dataset] Dataset name not found in data:', d);
                }
            } else {
                console.warn('[Dataset] Title element not found (datasetPageTitle)');
            }
            
            // Initialize pending changes toggle AFTER loading data and updating title (same pattern as Glossary and System)
            if (window.PendingChanges && window.PendingChanges.initToggle) {
                try {
                    await window.PendingChanges.initToggle('Dataset', id, '.page-title-text', {
                        applyChanges: (changesMap) => {
                            console.log('[Dataset] Applying pending changes:', changesMap);
                            Object.keys(changesMap).forEach(fieldName => {
                                const value = changesMap[fieldName];
                                let elements = document.querySelectorAll(`[data-field="${fieldName}"]`);
                                if (elements.length === 0) {
                                    elements = document.querySelectorAll(`[data-field="${fieldName.toLowerCase()}"]`);
                                }
                                if (!elements.length) return;
                                
                                let displayValue = value;
                                const notSpecifiedText = window.I18n?.t('dataset.messages.notSpecified') || 'Not specified';
                                elements.forEach(el => {
                                    const empty = displayValue === null || displayValue === undefined || displayValue === '' || displayValue === 'null';
                                    el.textContent = empty ? notSpecifiedText : displayValue;
                                    el.classList.add('pending-change-applied');
                                });
                            });
                        },
                        restoreOriginal: () => {
                            // Generic restore is handled by PendingChanges restoring textContent for original DOM
                        }
                    });
                    
                    // Re-update title after toggle init to ensure it's still correct (same pattern as Glossary)
                    // Re-fetch titleElement after initToggle in case DOM changed
                    const titleElementAfterToggle = document.getElementById('datasetPageTitle');
                    if (titleElementAfterToggle && d && d.name) {
                        // Ensure data-i18n is removed to prevent i18n from overwriting
                        titleElementAfterToggle.removeAttribute('data-i18n');
                        titleElementAfterToggle.textContent = d.name;
                        console.log('[Dataset] Title re-updated after toggle init to:', d.name);
                    }
                    
                    // After toggle is initialized, check the toggle state and sync currentView
                    const activeToggle = document.querySelector('.revision-toggle-link.active');
                    if (activeToggle) {
                        const toggleView = activeToggle.dataset.view;
                        if (toggleView === 'original' || toggleView === 'changes') {
                            currentView = toggleView;
                            view = toggleView; // Update view to match toggle
                            console.log('[Dataset] Synced currentView with toggle state:', currentView);
                        }
                    }
                } catch (error) {
                    console.error('[Dataset] Error initializing pending changes toggle:', error);
                }
            }
            
            // Handle segment-based edit permission
            // If canEdit is false, hide the edit button
            if (d.canEdit === false) {
                console.log('User does not have edit permission for this dataset (segment-based access control)');
                ensureHideEditControls();
            }
            
            // Log visit for Recent items
            try { window.BUDG_API_SERVICE.logVisit({ entity: 'CatalogueItem', entityId: String(id), route: `/view/dataset/${id}` }); } catch(_) {}
            const glossaryValueHtml = (function(){
                const glossaryId = d.glossaryId || d.glossaryID || d.glossary_id;
                const glossaryName = d.glossaryName;
                const glossaryHierarchy = d.glossaryHierarchy; // Expected format: ["Great-grandparent", "Grandparent", "Parent"]
                
                console.log('Dataset glossary data:', {
                    glossaryId,
                    glossaryName,
                    glossaryHierarchy,
                    glossaryParentName: d.glossaryParentName
                });
                
                // Build the full hierarchy display
                let hierarchyParts = [];
                
                // Add all ancestors from hierarchy array (if available)
                if (glossaryHierarchy && Array.isArray(glossaryHierarchy) && glossaryHierarchy.length > 0) {
                    // Add all ancestors as plain text
                    hierarchyParts = glossaryHierarchy.map(ancestor => escapeHtml(ancestor));
                }
                // Fallback to old parent name field if hierarchy not available
                else if (d.glossaryParentName) {
                    hierarchyParts.push(escapeHtml(d.glossaryParentName));
                }
                
                // Add the used glossary itself as a hyperlink (only this one is clickable)
                if (glossaryName) {
                    const glossaryLink = glossaryId 
                        ? `<a href="/view/glossary/${encodeURIComponent(glossaryId)}" style="color: var(--secondary-color, #248567); text-decoration: none;">${escapeHtml(glossaryName)}</a>`
                        : escapeHtml(glossaryName);
                    hierarchyParts.push(glossaryLink);
                }
                
                // Join with arrow separator
                return hierarchyParts.length > 0 
                    ? hierarchyParts.join(' <i class="fas fa-chevron-right" style="font-size: 0.7rem; color: var(--text-tertiary, #adb5bd); margin: 0 0.25rem;"></i> ')
                    : '<span class="empty" style="color: var(--text-tertiary, #adb5bd);">-</span>';
            })();

            const score = [];
            if (d.dqScore != null) score.push(`Score: ${escapeHtml(d.dqScore)}`);
            if (d.dqGreen != null) score.push(`Green: ${escapeHtml(d.dqGreen)}`);
            if (d.dqAmber != null) score.push(`Amber: ${escapeHtml(d.dqAmber)}`);

            // Debug user data
            console.log('Dataset user data:', {
                createdByName: d.createdByName,
                createdById: d.createdById,
                updatedByName: d.updatedByName,
                updatedById: d.updatedById
            });

            const createdBy = renderUserLink(d.createdByName, d.createdById);
            const createdAt = d.created ? new Date(d.created).toLocaleString() : '<span class="empty">-</span>';
            
            // If no updates happened (lastUpdated is null or equals created), show Created By/Date instead of Updated By/Date
            const hasBeenUpdated = d.lastUpdated && d.created && 
                new Date(d.lastUpdated).getTime() !== new Date(d.created).getTime();
            const updatedBy = hasBeenUpdated 
                ? renderUserLink(d.updatedByName, d.updatedById)
                : createdBy;
            const updatedAt = hasBeenUpdated 
                ? (d.lastUpdated ? new Date(d.lastUpdated).toLocaleString() : '<span class="empty">-</span>')
                : createdAt;

            // Create card-based layout
            const datasetContainer = `
                <div class="dataset-container">
                    <!-- DEFINITION Card -->
                    <div class="form-card definition-card">
                        <div class="card-header">
                            <h3 class="card-title">${window.I18n?.t('card.definition') || 'DEFINITION'}</h3>
                        </div>
                        <div class="card-body">
                            ${renderItem(dT('label.name', 'Name'), escapeHtml(d.name), 'name')}
                            ${renderItem(dT('label.systemShortName', 'System Short Name'), escapeHtml(d.systemName), 'systemShortName')}
                            ${renderItem(dT('label.ref', 'Ref'), escapeHtml(d.ref), 'ref')}
                            ${renderItem(dT('label.glossaryName', 'Glossary Name'), glossaryValueHtml, 'glossaryName')}
                            ${renderItem(dT('label.definition', 'Definition'), _richHtml(d.definition), 'definition')}
                            ${renderItem(dT('label.usage', 'Usage'), _richHtml(d.usage), 'usage')}
                        </div>
                    </div>

                    <!-- CLASSIFICATIONS Card -->
                    <div class="form-card classifications-card">
                        <div class="card-header">
                            <h3 class="card-title">${window.I18n?.t('card.classifications') || 'CLASSIFICATIONS'}</h3>
                        </div>
                        <div class="card-body">
                            ${renderItem(dT('glossary.labels.budgStatus', 'BUDG Status'), escapeHtml(d.statusName), 'budgStatus')}
                            ${renderItem(dT('label.type', 'Type'), escapeHtml(d.typeName), 'type')}
                            ${renderItem(dT('glossary.labels.budgViewing', 'BUDG Viewing'), escapeHtml(d.viewingName), 'viewing')}
                            ${renderItem(dT('glossary.labels.lifecycle', 'Lifecycle'), escapeHtml(d.lifecycleName), 'lifecycle')}
                        </div>
                        
                        <div class="card-body-meta" style="border-top: 1px solid var(--border-color, #e5e7eb); margin-top: 1rem; padding: 1.5rem;">
                            ${renderItem(dT('glossary.labels.createdBy', 'Created By'), createdBy, 'createdBy')}
                            ${renderItem(dT('glossary.labels.created', 'Created'), createdAt, 'created')}
                            ${renderItem(dT('label.updatedBy', 'Updated By'), updatedBy, 'updatedBy')}
                            ${renderItem(dT('glossary.labels.lastUpdated', 'Last Updated'), updatedAt, 'lastUpdated')}
                        </div>
                         <!-- OTHER INFORMATION Section -->
                        <div class="card-section-divider" style="border-top: 1px solid var(--border-color, #e5e7eb); margin-top: 1rem; padding: 1.5rem;">
                            <div class="section-label" style="font-size: 0.75rem; font-weight: 600; color: #248567; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 0.75rem;">${window.I18n?.t('card.otherInformation') || 'OTHER INFORMATION'}</div>
                            ${renderItem(dT('glossary.labels.segment', 'Segment'), escapeHtml(d.segmentName || d.segment_name || d.segment || ((d.segmentId ?? d.segment_id ?? d.Segment_ID) != null ? dT('common.segmentIdDisplay', 'ID {id}', { id: d.segmentId ?? d.segment_id ?? d.Segment_ID }) : dT('dataset.messages.notSpecified', 'Not specified'))), 'segment')}
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = datasetContainer;
            
            // Debug: Check container content
            console.log('[Dataset] Container innerHTML length:', container.innerHTML.length);
            console.log('[Dataset] Container has children:', container.children.length);
            console.log('[Dataset] Container computed display:', window.getComputedStyle(container).display);
            console.log('[Dataset] Container computed visibility:', window.getComputedStyle(container).visibility);
            
            // Ensure container is visible after loading data only when Details tab is active
            if (container) {
                const activeTabElNow = document.querySelector('.tab-container .tab.active');
                const activeTabNow = activeTabElNow ? activeTabElNow.getAttribute('data-tab') : 'details';
                if (activeTabNow === 'details') {
                    container.style.display = 'flex'; // view-grid uses flex in view.css
                    container.style.flexDirection = 'column';
                    container.style.visibility = 'visible';
                    container.style.opacity = '1';
                    container.style.gap = '1.25rem';
                }
                console.log('[Dataset] Container after loading data (active tab:', activeTabNow, ')');
                console.log('[Dataset] Container style after setting:', {
                    display: container.style.display,
                    visibility: container.style.visibility,
                    opacity: container.style.opacity,
                    computedDisplay: window.getComputedStyle(container).display,
                    computedVisibility: window.getComputedStyle(container).visibility
                });
                
                // Also use requestAnimationFrame to ensure DOM is ready
                requestAnimationFrame(() => {
                    const activeTabEl = document.querySelector('.tab-container .tab.active');
                    const currentTab = activeTabEl ? activeTabEl.getAttribute('data-tab') : 'details';
                    if (currentTab === 'details') {
                        container.style.display = 'flex';
                        container.style.flexDirection = 'column';
                        container.style.visibility = 'visible';
                        container.style.opacity = '1';
                        container.style.gap = '1.25rem';
                    }
                    // Force a reflow to ensure rendering
                    void container.offsetHeight;
                    // Debug after requestAnimationFrame
                    console.log('[Dataset] Container after requestAnimationFrame:', {
                        display: container.style.display,
                        computedDisplay: window.getComputedStyle(container).display,
                        hasContent: container.innerHTML.length > 0,
                        childrenCount: container.children.length
                    });
                });
            }
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Dataset',
                        containerId: 'datasetViewContainer',
                        objectId: id,
                        title: window.I18n?.t('dataset.messages.customFields') || 'CUSTOM FIELDS',
                        view: view === 'changes' ? 'changes' : null
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

            // Add DOCUMENTS collapsible section
            const docsEmptyText = window.I18n?.t('message.datasetHasNoDocuments') || 'This dataset has no documents.';
            const docsEmpty = '<div class="empty">' + docsEmptyText + '</div>';
            const documentsTitle = window.I18n?.t('card.documents') || 'DOCUMENTS';
            const documentsSection = `
                <div class="view-section" style="grid-column: 1/-1; margin-top: 2rem;" data-collapsible>
                    <div class="collapsible-header" style="display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.5rem; background: var(--background-secondary, #f8f9fa); border: 1px solid var(--border-color, #e5e7eb); border-radius: 8px; cursor: pointer;">
                        <div style="display: flex; align-items: center; gap: 0.75rem;">
                            <i class="fa-solid fa-file-lines" style="color: var(--secondary-color, #248567);"></i>
                            <h3 style="margin: 0; font-size: 0.875rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">${documentsTitle}</h3>
                        </div>
                        <i class="fas fa-chevron-down" style="transition: transform 0.2s;"></i>
                    </div>
                    <div class="collapsible-body" style="display: none; padding: 1.5rem; border: 1px solid var(--border-color, #e5e7eb); border-top: none; border-radius: 0 0 8px 8px;">
                        ${docsEmpty}
                    </div>
                </div>
            `;
            container.insertAdjacentHTML('beforeend', documentsSection);

            // Wire up collapsible toggle
            const documentsCollapsible = container.querySelector('[data-collapsible]');
            if (documentsCollapsible) {
                const header = documentsCollapsible.querySelector('.collapsible-header');
                const body = documentsCollapsible.querySelector('.collapsible-body');
                if (header && body) {
                    header.addEventListener('click', function() {
                        const isOpen = documentsCollapsible.classList.contains('open');
                        documentsCollapsible.classList.toggle('open');
                        header.setAttribute('aria-expanded', String(!isOpen));
                        body.style.display = isOpen ? 'none' : 'block';
                        const chevron = header.querySelector('.fa-chevron-down');
                        if (chevron) {
                            chevron.style.transform = isOpen ? 'rotate(0deg)' : 'rotate(180deg)';
                        }
                    });
                }
            }

            // Load Documents (pass view mode to exclude pending documents in original view)
            loadDocuments(id, view);

            // If user opened with ?tab=attribute (or switched tab) while load was in progress, keep summary hidden
            const activeTabEl = document.querySelector('.tab-container .tab.active');
            const activeTabName = activeTabEl ? activeTabEl.getAttribute('data-tab') : 'details';
            if (activeTabName !== 'details' && container) {
                container.style.display = 'none';
            }
            
        } catch (e) {
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const errorText = isForbidden
                ? 'This object is not available.'
                : (window.I18n?.t('dataset.errors.failedToLoad') || 'Failed to load dataset');
            const suffix = isForbidden ? '' : ` (id=${id}).`;
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">${errorText}${suffix}</div>`;
        }
    }

    // Store document table instance for view mode switching
    let datasetDocumentTable = null;
    
    async function loadDocuments(datasetId, viewMode = null) {
        // Find the documents collapsible section
        const documentsSection = document.querySelector('[data-collapsible] .collapsible-body');
        if (!documentsSection) {
            console.warn('Documents section not found');
            return;
        }

        // Create container for document table
        const containerId = 'datasetDocumentsTableContainer';
        documentsSection.innerHTML = `<div id="${containerId}"></div>`;

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                datasetDocumentTable = new DocumentTableComponent({
                    facetType: 'dataset',
                    facetId: datasetId,
                    container: `#${containerId}`,
                    canEdit: false, // Read-only in view mode
                    viewMode: viewMode // Pass view mode to filter pending documents
                });
            } catch (error) {
                console.error('Error initializing document table:', error);
                const failedToLoadDocs = window.I18n?.t('message.failedToLoadDocuments') || 'Failed to load documents.';
                documentsSection.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">' + failedToLoadDocs + '</div>';
            }
        } else {
            console.error('DocumentTableComponent not found');
            const docComponentNotAvailable = window.I18n?.t('message.documentComponentNotAvailable') || 'Document component not available.';
            documentsSection.innerHTML = '<div class="empty">' + docComponentNotAvailable + '</div>';
        }
    }

    async function loadImpactData(id, viewMode = 'original') {
        const container = document.getElementById('datasetImpactContainer');
        if (!container) {
            console.error('datasetImpactContainer not found in loadImpactData');
            return;
        }

        const loadingImpactText = window.I18n?.t('dataset.loading.loadingImpact') || 'Loading impact data...';
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1; padding: 2rem; text-align: center;"><i class="fas fa-spinner fa-spin"></i> ${loadingImpactText}</div>`;

        // Wait a bit for scripts to load if needed, then try to load impact
        let retries = 0;
        const maxRetries = 10;
        
        while (retries < maxRetries) {
            if (window.loadDatasetImpact && typeof window.loadDatasetImpact === 'function') {
                try {
                    console.log('Loading dataset impact for ID:', id, 'view:', viewMode);
                    await window.loadDatasetImpact(id, viewMode);
                    return;
                } catch (error) {
                    console.error('Error loading dataset impact:', error);
                    const errorText = window.I18n?.t('dataset.errors.errorLoadingImpact') || 'Error loading impact data';
                    container.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                            <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                            <p>${errorText}: ${error.message}</p>
                        </div>
                    `;
                    return;
                }
            }
            
            // Wait 100ms before retrying
            await new Promise(resolve => setTimeout(resolve, 100));
            retries++;
        }
        
        // If function still not available, show error
        console.error('loadDatasetImpact function not available after', maxRetries, 'retries');
        const scriptNotLoadedText = window.I18n?.t('dataset.errors.impactScriptNotLoaded') || 'Impact view script not loaded. Please refresh the page.';
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                <p>${scriptNotLoadedText}</p>
            </div>
        `;
    }

    function tDataset(key, fallback) {
        const translated = window.I18n?.t ? window.I18n.t(key) : null;
        return (!translated || translated === key) ? fallback : translated;
    }

    function getRelationshipsColumns() {
        return [
            { id: 'attribute', label: tDataset('dataset.columns.attribute', 'Attribute') },
            { id: 'sourceSystem', label: tDataset('dataset.columns.sourceSystem', 'Source System') },
            { id: 'interface', label: tDataset('dataset.columns.interface', 'Interface') },
            { id: 'relatedDataSet', label: tDataset('dataset.columns.relatedDataSet', 'Related Data Set') },
            { id: 'relatedAttributes', label: tDataset('dataset.columns.relatedAttributes', 'Related Attributes') },
            { id: 'type', label: tDataset('dataset.columns.type', 'Type') },
            { id: 'scopeOfData', label: tDataset('dataset.columns.scopeOfData', 'Scope of Data') },
            { id: 'sourcingLogic', label: tDataset('dataset.columns.sourcingLogic', 'Sourcing Logic') },
            { id: 'reviewStatus', label: tDataset('dataset.columns.reviewStatus', 'Review Status') }
        ];
    }

    // Toggle column visibility for relationships tables
    window.toggleRelationshipsColumn = function(columnId) {
        const checkbox = document.querySelector(`input[type="checkbox"][value="${columnId}"]`);
        if (!checkbox) return;
        
        // Use checkbox state to determine visibility (checked = visible, unchecked = hidden)
        const shouldBeVisible = checkbox.checked;
        const displayValue = shouldBeVisible ? 'table-cell' : 'none';
        
        const tables = document.querySelectorAll('.relationships-table');
        tables.forEach(table => {
            // Update header cells
            const headerCells = table.querySelectorAll(`th[data-column-id="${columnId}"]`);
            headerCells.forEach(cell => {
                cell.style.display = displayValue;
            });
            
            // Update data cells
            const dataCells = table.querySelectorAll(`td[data-column-id="${columnId}"]`);
            dataCells.forEach(cell => {
                cell.style.display = displayValue;
            });
        });
    };

    // Toggle column selector dropdown
    window.toggleRelationshipsColumnSelector = function(button) {
        if (!button) return;
        
        // Find the dropdown in the same relationships-actions container
        const actionsContainer = button.closest('.relationships-actions');
        if (!actionsContainer) return;
        
        const dropdown = actionsContainer.querySelector('.relationships-column-dropdown');
        if (!dropdown) return;
        
        // Close all other dropdowns first
        document.querySelectorAll('.relationships-column-dropdown').forEach(d => {
            if (d !== dropdown) {
                d.style.display = 'none';
            }
        });
        
        // Toggle current dropdown
        const isVisible = dropdown.style.display !== 'none';
        dropdown.style.display = isVisible ? 'none' : 'block';
    };

    // Close dropdown when clicking outside
    document.addEventListener('click', function(event) {
        const dropdowns = document.querySelectorAll('.relationships-column-dropdown');
        const buttons = document.querySelectorAll('.relationships-actions .btn');
        let clickedButton = false;
        let clickedDropdown = false;
        
        buttons.forEach(btn => {
            if (btn.contains(event.target)) {
                clickedButton = true;
            }
        });
        
        dropdowns.forEach(dropdown => {
            if (dropdown.contains(event.target)) {
                clickedDropdown = true;
            }
        });
        
        if (!clickedButton && !clickedDropdown) {
            dropdowns.forEach(dropdown => {
                dropdown.style.display = 'none';
            });
        }
    });

    async function loadRelationshipsData(id, view = null) {
        const container = document.getElementById('datasetRelationshipsContainer');
        if (!container) return;

        const loadingRelationshipsText = window.I18n?.t('dataset.loading.loadingRelationships') || 'Loading relationships data...';
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${loadingRelationshipsText}</div>`;

        try {
            // Build URL with view parameter and cache-busting
            let url = `/api/dataset-relationships/${id}`;
            if (view === 'changes') {
                url += '?view=changes';
            }
            url += (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime(); // Cache-busting
            
            // Fetch relationships data from API
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const relationshipsData = await response.json();

            // Generate column selector dropdown HTML (will be added inside each relationships-actions div)
            const relationshipColumns = getRelationshipsColumns();
            const columnSelectorHtml = `
                <div class="relationships-column-dropdown" style="display: none; position: absolute; right: 0; top: 100%; margin-top: 0.5rem; background: white; border: 1px solid #d1d5db; border-radius: 0.5rem; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); z-index: 9999; min-width: 250px; max-height: 400px; overflow-y: auto;">
                    <div style="padding: 0.75rem 1rem; border-bottom: 1px solid #e5e7eb; background: #f9fafb; font-weight: 600; display: flex; justify-content: space-between; align-items: center;">
                        <span>${tDataset('dataset.messages.selectColumns', 'Select Columns')}</span>
                        <button type="button" onclick="document.querySelectorAll('.relationships-column-dropdown').forEach(d => d.style.display='none')" style="background: none; border: none; color: #6b7280; cursor: pointer; font-size: 1.25rem; padding: 0; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center;">&times;</button>
                    </div>
                    <div style="padding: 0.5rem 0;">
                        ${relationshipColumns.map(col => `
                            <label style="display: flex; align-items: center; padding: 0.5rem 1rem; cursor: pointer; white-space: nowrap; hover:background-color: #f9fafb;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor=''">
                                <input type="checkbox" 
                                       value="${col.id}" 
                                       checked 
                                       onchange="toggleRelationshipsColumn('${col.id}')"
                                       style="margin-right: 0.5rem; cursor: pointer;">
                                <span>${col.label}</span>
                            </label>
                        `).join('')}
                    </div>
                </div>
            `;

            // Render relationships data
            const inboundHtml = relationshipsData.inbound && relationshipsData.inbound.length > 0 ? `
                <div class="relationships-section">
                    <div class="relationships-header">
                        <div class="relationships-title">${window.I18n?.t('dataset.relationships.inbound') || 'INBOUND RELATIONSHIPS'}</div>
                        <div class="relationships-actions" style="position: relative; z-index: 10000;">
                            <button type="button" class="btn btn-secondary btn-sm" onclick="toggleRelationshipsColumnSelector(this)">
                                <i class="fas fa-cog"></i>
                                <i class="fas fa-chevron-down"></i>
                            </button>
                            ${columnSelectorHtml}
                        </div>
                    </div>
                    <div class="relationships-table-wrapper">
                        <table class="relationships-table">
                            <thead>
                                <tr>
                                    <th data-column-id="relatedDataSet">${tDataset('dataset.columns.relatedDataSet', 'Related Data Set')}</th>
                                    <th data-column-id="relatedAttributes">${tDataset('dataset.columns.relatedAttributes', 'Related Attributes')}</th>
                                    <th data-column-id="sourceSystem">${tDataset('dataset.columns.sourceSystem', 'Source System')}</th>
                                    <th data-column-id="interface">${tDataset('dataset.columns.interface', 'Interface')}</th>
                                    <th data-column-id="attribute">${tDataset('dataset.columns.attribute', 'Attribute')}</th>
                                    <th data-column-id="type">${tDataset('dataset.columns.type', 'Type')}</th>
                                    <th data-column-id="scopeOfData">${tDataset('dataset.columns.scopeOfData', 'Scope of Data')}</th>
                                    <th data-column-id="sourcingLogic">${tDataset('dataset.columns.sourcingLogic', 'Sourcing Logic')}</th>
                                    <th data-column-id="reviewStatus">${tDataset('dataset.columns.reviewStatus', 'Review Status')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${relationshipsData.inbound.map(rel => {
                                    const sourceAttrLink = rel.Source_AttributeID && rel.sourceAttributeName 
                                        ? createRelationshipLink('attribute', rel.Source_AttributeID, rel.sourceAttributeName, rel.targetDatasetId || id)
                                        : escapeHtml(rel.sourceAttributeName || '');
                                    const systemLink = rel.systemId && rel.systemName 
                                        ? createRelationshipLink('system', rel.systemId, rel.systemName)
                                        : escapeHtml(rel.systemName || '');
                                    const interfaceLink = rel.interfaceId && rel.interfaceName 
                                        ? createRelationshipLink('interface', rel.interfaceId, rel.interfaceName)
                                        : escapeHtml(rel.interfaceName || '');
                                    const datasetDisplay = (rel.targetDatasetRef ? rel.targetDatasetRef + ': ' : '') + (rel.targetDatasetName || '');
                                    const datasetLink = rel.targetDatasetId && rel.targetDatasetName 
                                        ? createRelationshipLink('dataset', rel.targetDatasetId, datasetDisplay)
                                        : escapeHtml(datasetDisplay);
                                    const targetAttrLink = rel.Target_AttributeID && rel.targetAttributeName 
                                        ? createRelationshipLink('attribute', rel.Target_AttributeID, rel.targetAttributeName, rel.targetDatasetId)
                                        : escapeHtml(rel.targetAttributeName || '');
                                    
                                    return `
                                    <tr>
                                        <td data-column-id="relatedDataSet">${datasetLink}</td>
                                        <td data-column-id="relatedAttributes">${targetAttrLink}</td>
                                        <td data-column-id="sourceSystem">${systemLink}</td>
                                        <td data-column-id="interface">${interfaceLink}</td>
                                        <td data-column-id="attribute">${sourceAttrLink}</td>
                                        <td data-column-id="type">${escapeHtml(rel.relationType || '')}</td>
                                        <td data-column-id="scopeOfData">${escapeHtml(rel.relationScope || '')}</td>
                                        <td data-column-id="sourcingLogic">${escapeHtml(rel.sourcingLogic || rel.Sourcing_Logic || '')}</td>
                                        <td data-column-id="reviewStatus">${escapeHtml(rel.reviewStatus || rel.Review_Status || '')}</td>
                                    </tr>
                                `;
                                }).join('')}
                            </tbody>
                        </table>
                        <div class="relationships-count">
                            ${relationshipsData.inbound.length} ${relationshipsData.inbound.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}
                        </div>
                    </div>
                </div>
            ` : `
                <div class="relationships-section">
                    <div class="relationships-header">
                        <div class="relationships-title">${window.I18n?.t('dataset.relationships.inbound') || 'INBOUND RELATIONSHIPS'}</div>
                    </div>
                    <div class="relationships-content">
                        <div class="relationships-empty">
                            <i class="fas fa-info-circle"></i>
                            <span>${window.I18n?.t('dataset.relationships.noInboundFound') || 'No inbound relationships found.'}</span>
                        </div>
                    </div>
                </div>
            `;

            const outboundHtml = relationshipsData.outbound && relationshipsData.outbound.length > 0 ? `
                <div class="relationships-section">
                    <div class="relationships-header">
                        <div class="relationships-title">${window.I18n?.t('dataset.relationships.outbound') || 'OUTBOUND RELATIONSHIPS'}</div>
                        <div class="relationships-actions" style="position: relative; z-index: 10000;">
                            <button type="button" class="btn btn-secondary btn-sm" onclick="toggleRelationshipsColumnSelector(this)">
                                <i class="fas fa-cog"></i>
                                <i class="fas fa-chevron-down"></i>
                            </button>
                            ${columnSelectorHtml}
                        </div>
                    </div>
                    <div class="relationships-table-wrapper">
                        <table class="relationships-table">
                            <thead>
                                <tr>
                                    <th data-column-id="relatedDataSet">${tDataset('dataset.columns.relatedDataSet', 'Related Data Set')}</th>
                                    <th data-column-id="relatedAttributes">${tDataset('dataset.columns.relatedAttributes', 'Related Attributes')}</th>
                                    <th data-column-id="sourceSystem">${tDataset('dataset.columns.sourceSystem', 'Source System')}</th>
                                    <th data-column-id="interface">${tDataset('dataset.columns.interface', 'Interface')}</th>
                                    <th data-column-id="attribute">${tDataset('dataset.columns.attribute', 'Attribute')}</th>
                                    <th data-column-id="type">${tDataset('dataset.columns.type', 'Type')}</th>
                                    <th data-column-id="scopeOfData">${tDataset('dataset.columns.scopeOfData', 'Scope of Data')}</th>
                                    <th data-column-id="sourcingLogic">${tDataset('dataset.columns.sourcingLogic', 'Sourcing Logic')}</th>
                                    <th data-column-id="reviewStatus">${tDataset('dataset.columns.reviewStatus', 'Review Status')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${relationshipsData.outbound.map(rel => {
                                    const targetAttrLink = rel.Target_AttributeID && rel.targetAttributeName 
                                        ? createRelationshipLink('attribute', rel.Target_AttributeID, rel.targetAttributeName, id)
                                        : escapeHtml(rel.targetAttributeName || '');
                                    const systemLink = rel.systemId && rel.systemName 
                                        ? createRelationshipLink('system', rel.systemId, rel.systemName)
                                        : escapeHtml(rel.systemName || '');
                                    const interfaceLink = rel.interfaceId && rel.interfaceName 
                                        ? createRelationshipLink('interface', rel.interfaceId, rel.interfaceName)
                                        : escapeHtml(rel.interfaceName || '');
                                    const datasetDisplay = (rel.sourceDatasetRef ? rel.sourceDatasetRef + ': ' : '') + (rel.sourceDatasetName || '');
                                    const datasetLink = rel.sourceDatasetId && rel.sourceDatasetName 
                                        ? createRelationshipLink('dataset', rel.sourceDatasetId, datasetDisplay)
                                        : escapeHtml(datasetDisplay);
                                    const sourceAttrLink = rel.Source_AttributeID && rel.sourceAttributeName 
                                        ? createRelationshipLink('attribute', rel.Source_AttributeID, rel.sourceAttributeName, rel.sourceDatasetId)
                                        : escapeHtml(rel.sourceAttributeName || '');
                                    
                                    return `
                                    <tr>
                                        <td data-column-id="relatedDataSet">${datasetLink}</td>
                                        <td data-column-id="relatedAttributes">${sourceAttrLink}</td>
                                        <td data-column-id="sourceSystem">${systemLink}</td>
                                        <td data-column-id="interface">${interfaceLink}</td>
                                        <td data-column-id="attribute">${targetAttrLink}</td>
                                        <td data-column-id="type">${escapeHtml(rel.relationType || '')}</td>
                                        <td data-column-id="scopeOfData">${escapeHtml(rel.relationScope || '')}</td>
                                        <td data-column-id="sourcingLogic">${escapeHtml(rel.sourcingLogic || rel.Sourcing_Logic || '')}</td>
                                        <td data-column-id="reviewStatus">${escapeHtml(rel.reviewStatus || rel.Review_Status || '')}</td>
                                    </tr>
                                `;
                                }).join('')}
                            </tbody>
                        </table>
                        <div class="relationships-count">
                            ${relationshipsData.outbound.length} ${relationshipsData.outbound.length !== 1 ? (window.I18n?.t('dataset.messages.records') || 'records') : (window.I18n?.t('dataset.messages.record') || 'record')}
                        </div>
                    </div>
                </div>
            ` : `
                <div class="relationships-section">
                    <div class="relationships-header">
                        <div class="relationships-title">${window.I18n?.t('dataset.relationships.outbound') || 'OUTBOUND RELATIONSHIPS'}</div>
                    </div>
                    <div class="relationships-content">
                        <div class="relationships-empty">
                            <i class="fas fa-info-circle"></i>
                            <span>${window.I18n?.t('dataset.relationships.notUsedByOthers') || 'This data item is not used by any other data item.'}</span>
                        </div>
                    </div>
                </div>
            `;

            // Add map section before the relationships tables
            const mapHtml = (typeof window.getDatasetRelationshipsMapHtml === 'function' ? window.getDatasetRelationshipsMapHtml() : '');
            container.innerHTML = mapHtml + inboundHtml + outboundHtml;

            // Initialize map after HTML is added
            if (window.DatasetRelationshipsMap) {
                setTimeout(() => {
                    window.DatasetRelationshipsMap.init(id, '#datasetRelationshipsMapCanvas');
                    window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.initToolbar && window.DatasetRelationshipsMap.initToolbar();
                }, 100);
            }

        } catch (e) {
            console.error('Failed to load relationships data:', e);
            container.innerHTML = `
                <div class="relationships-section">
                    <div class="relationships-header">
                        <div class="relationships-title">${window.I18n?.t('dataset.relationships.relationships') || 'RELATIONSHIPS'}</div>
                    </div>
                    <div class="relationships-content">
                        <div class="relationships-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n?.t('dataset.errors.failedToLoadRelationships') || 'Failed to load relationships data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    // Initialize dataset relationships map
    function initializeDatasetRelationshipsMap(datasetId) {
        const container = document.getElementById('datasetRelationshipsContainer');
        if (!container) return;

        const mapHtml = (typeof window.getDatasetRelationshipsMapHtml === 'function' ? window.getDatasetRelationshipsMapHtml() : '');
        const existingContent = container.innerHTML;
        container.innerHTML = mapHtml + existingContent;

        // Initialize map
        if (window.DatasetRelationshipsMap) {
            setTimeout(() => {
                window.DatasetRelationshipsMap.init(datasetId, '#datasetRelationshipsMapCanvas');
                window.DatasetRelationshipsMap && window.DatasetRelationshipsMap.initToolbar && window.DatasetRelationshipsMap.initToolbar();
            }, 100);
        }
    }


    // Check URL parameters early
    const urlParams = new URLSearchParams(window.location.search);
    const shouldSwitchTab = urlParams.get('tab');
    const attributeIdParam = urlParams.get('attributeId');

    document.addEventListener('DOMContentLoaded', function() {
			// Hide edit controls for guests
			hideEditsIfUnauthenticated();
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
        const id = parseId();
        if (id != null) {
            // Ensure content body and container are visible - immediate and synchronous
            const contentBody = document.querySelector('.content-body');
            if (contentBody) {
                contentBody.style.display = 'flex';
                contentBody.style.flexDirection = 'column';
                contentBody.style.visibility = 'visible';
                contentBody.style.opacity = '1';
                console.log('[Dataset] Content-body initialized as visible before load');
            } else {
                console.error('[Dataset] Content-body not found in DOM!');
            }
            const datasetViewContainer = document.getElementById('datasetViewContainer');
            if (datasetViewContainer) {
                datasetViewContainer.style.display = 'flex';
                datasetViewContainer.style.flexDirection = 'column';
                datasetViewContainer.style.visibility = 'visible';
                datasetViewContainer.style.opacity = '1';
                datasetViewContainer.style.gap = '1.25rem';
                console.log('[Dataset] Container initialized as visible before load');
            } else {
                console.error('[Dataset] datasetViewContainer not found in DOM!');
            }
            
            // Load with null view first, then toggle will set the correct view (same pattern as Glossary)
            // The toggle initialization in load() will sync currentView
            load(id, null).then(async function(){
                // Title is already updated in load function (same pattern as Glossary and System)
                
                // Only show details container if Details tab is still active (e.g. avoid showing summary when URL had tab=attribute)
                const activeTabEl = document.querySelector('.tab-container .tab.active');
                const activeTabName = activeTabEl ? activeTabEl.getAttribute('data-tab') : 'details';
                const container = document.getElementById('datasetViewContainer');
                if (container && activeTabName === 'details') {
                    container.style.display = '';
                    container.style.visibility = 'visible';
                }
                
                // Add follow button to header actions
                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('dataset', id, null, '.form-actions');
                    } catch (e) {
                        console.error('[Dataset] Failed to add follow button:', e);
                    }
                }
            });
        }

// Initialize Edit Dropdown (like BUDG) - wait for page to be fully loaded
if (window.EditDropdown && id != null) {
    // Use setTimeout to ensure DOM is ready and data is loaded
    setTimeout(() => {
        (async () => {
            try {
                // Check Auto CR status and determine edit restrictions
                let disableEditOption = false;
                let disableEditReason = '';
                try {
                    const statusRes = await fetch(`/api/pending-changes/status/Dataset/${id}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (statusRes.ok) {
                        const statusData = await statusRes.json();
                        if (statusData.changeRequestId) {
                            const currentUserRes = await fetch('/api/me', { credentials: 'include' });
                            const currentUser = currentUserRes.ok ? await currentUserRes.json() : null;
                            const currentUserId = currentUser?.id != null ? currentUser.id : currentUser?.userId;

                            let crStatusName = statusData.crStatusName || '';
                            let crCreatedBy = statusData.crCreatedBy;
                            let isAutoCR = statusData.isAutoCR === true;

                            if (!isAutoCR && !crStatusName) {
                                try {
                                    const crRes = await fetch(`/api/changerequests/${statusData.changeRequestId}`, { method: 'GET', credentials: 'include' });
                                    if (crRes.ok) {
                                        const crData = await crRes.json();
                                        const mandatoryWorkflow = crData.mandatoryWorkflow === true || crData.mandatoryWorkflow === 1;
                                        const pName = (crData.primaryName || crData.PrimaryName || '').toLowerCase();
                                        const summ = (crData.summary || crData.Summary || '').toLowerCase();
                                        isAutoCR = mandatoryWorkflow || pName.includes('auto-generated cr for') || summ.includes('auto-generated cr for');
                                        crStatusName = crData.statusName || crData.StatusName || '';
                                        crCreatedBy = crData.createdBy != null ? crData.createdBy : crData.Created_By;
                                    }
                                } catch (e) { console.warn('[Dataset] Failed to fetch CR details:', e); }
                            }

                            const statusLower = (crStatusName || '').toLowerCase();
                            const isFinished = statusLower.includes('complete') || statusLower.includes('cancelled') || statusLower.includes('canceled') || statusLower.includes('closed') || statusLower.includes('reject');

                            if (isAutoCR && !isFinished) {
                                const isRequester = crCreatedBy != null && currentUserId != null && String(crCreatedBy) === String(currentUserId);
                                const isRunning = statusLower.includes('running') || statusLower.includes('in progress');
                                console.log('[Dataset] Auto CR active: status=' + crStatusName + ', isRequester=' + isRequester);

                                if (!isRequester) {
                                    disableEditOption = true;
                                    disableEditReason = 'Cannot edit: Only the requester can edit during an active Auto CR.';
                                } else if (isRunning) {
                                    disableEditOption = true;
                                    disableEditReason = 'Cannot edit: Auto CR is ' + crStatusName + '. Wait for completion.';
                                }
                            }
                        }
                    }
                } catch (e) {
                    console.warn('[Dataset] Could not check CR status:', e);
                }
                
                // Store auto CR state for stakeholder tab redirect
                _hasActiveAutoCR = disableEditOption;
                
            window.EditDropdown.initialize('dataset', id, {
                container: '.tab-actions',
                editUrl: `/view/dataset/dataset-edit.html?id=${id}`,
                hideEditOption: false,
                disableEditOption: disableEditOption,
                disableEditReason: disableEditReason
            });
            } catch (error) {
                console.error('[Dataset] Failed to initialize edit dropdown:', error);
            }
        })();
    }, 500); // Wait 500ms for page to be fully loaded
}

// Wire up tab edit button - general for all tabs
const tabEditBtn = document.getElementById('tabEditBtn');
if (tabEditBtn) {
    tabEditBtn.addEventListener('click', async function() {
        if (id != null) {
            // Check if dataset is locked before navigating to edit
            try {
                const lockInfo = await window.BUDG_API_SERVICE.checkLock('dataset', id);
                if (lockInfo && lockInfo.status !== 'no_lock' && lockInfo.status !== 'locked_by_self') {
                    const lockedBy = lockInfo.lockedByName || 'another user';
                    const isPermanent = lockInfo.isPermanent || false;
                    const message = `This dataset is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`;
                    alert(message);
                    return;
                }
            } catch (error) {
                console.error('Failed to check lock:', error);
                // Continue to edit page even if lock check fails
            }

            const activeTab = document.querySelector('.tab.active');
            const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'details';

            // Navigate to edit page with active tab parameter
            if (tabName && tabName !== 'details') {
                window.location.href = `/view/dataset/dataset-edit.html?id=${id}&tab=${tabName}`;
            } else {
                window.location.href = `/view/dataset/dataset-edit.html?id=${id}`;
            }
        }
    });
    
}

        
        // Check and display lock status
        if (id != null) {
            checkAndDisplayLockStatus(id);
        }

        // Traditional tab handling
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(function(tab) {
            tab.addEventListener('click', async function() {
                const tabName = this.getAttribute('data-tab');
                console.log('[Dataset] Tab clicked:', tabName);
                
                // Toggle stakeholder-only edit mode based on active tab and auto CR state
                if (window.EditDropdown && _hasActiveAutoCR && _canEditObject) {
                    if (tabName === 'stakeholders') {
                        window.EditDropdown.enableStakeholderOnlyEdit(`/view/dataset/dataset-edit.html?id=${id}`);
                    } else {
                        window.EditDropdown.disableStakeholderOnlyEdit();
                    }
                }

                // Remove active class from all tabs
                tabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');

                const body = document.querySelector('.content-body');
                if (!body) {
                    console.error('Content body not found');
                    return;
                }

                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                console.log('Switching to tab:', which);

               if (which === 'stakeholders') {
                   // Hide all existing content
                   const existingContent = body.querySelectorAll('.view-section, .view-grid');
                   existingContent.forEach(el => el.style.display = 'none');

                   // Create or show stakeholders container
                   let stakeholdersContainer = document.getElementById('datasetStakeholdersContainer');
                   if (!stakeholdersContainer) {
                       stakeholdersContainer = document.createElement('div');
                       stakeholdersContainer.id = 'datasetStakeholdersContainer';
                       stakeholdersContainer.className = 'view-section';
                       stakeholdersContainer.style.gridColumn = '1/-1';
                       body.appendChild(stakeholdersContainer);
                   }
                   stakeholdersContainer.style.display = 'block';

                    // Wait a bit for toggle to initialize if needed, then get view mode
                    // This ensures we get the correct view mode even on page refresh
                    await new Promise(resolve => setTimeout(resolve, 100));
                    const viewMode = getCurrentViewMode();
                    console.log('Creating DatasetStakeholderView with id:', id, 'view:', viewMode);
                    if (window.DatasetStakeholderView) {
                        window.DatasetStakeholderView.init(id, viewMode);
                    } else {
                        console.error('DatasetStakeholderView class not found');
                    }
                } else if (which === 'values') {
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show values container
                    let valuesContainer = document.getElementById('datasetValuesContainer');
                    if (!valuesContainer) {
                        valuesContainer = document.createElement('div');
                        valuesContainer.id = 'datasetValuesContainer';
                        valuesContainer.className = 'view-section';
                        valuesContainer.style.gridColumn = '1/-1';
                        body.appendChild(valuesContainer);
                    }
                    valuesContainer.style.display = 'block';

                    // Initialize values view
                    console.log('Initializing values view for dataset ID:', id);
                    if (window.DatasetValuesView) {
                        // Check if instance already exists
                        if (window.datasetValuesViewInstance && window.datasetValuesViewInstance.datasetId === id) {
                            // Reuse existing instance - just update view and reload
                            console.log('Reusing existing DatasetValuesView instance');
                            const viewParam = (currentView === 'changes') ? 'changes' : null;
                            window.datasetValuesViewInstance.setView(viewParam);
                            await window.datasetValuesViewInstance.loadMetadata();
                        } else {
                            // Create new instance
                            console.log('Creating new DatasetValuesView instance');
                            const viewParam = (currentView === 'changes') ? 'changes' : null;
                            console.log('Creating DatasetValuesView with view:', viewParam, '(currentView:', currentView, ')');
                            const valuesView = new window.DatasetValuesView('datasetValuesContainer', id, viewParam);
                            window.datasetValuesViewInstance = valuesView; // Store instance globally
                            await valuesView.init();
                        }
                    } else {
                        console.error('DatasetValuesView class not found');
                    }
                } else if (which === 'attribute') {
                    
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show attribute container
                    let attributeContainer = document.getElementById('datasetAttributeContainer');
                    if (!attributeContainer) {
                        console.log('→ Creating NEW attribute container');
                        attributeContainer = document.createElement('div');
                        attributeContainer.id = 'datasetAttributeContainer';
                        attributeContainer.className = 'view-section';
                        attributeContainer.style.gridColumn = '1/-1';
                        body.appendChild(attributeContainer);
                    } else {
                        console.log('→ Container exists, clearing old content');
                        // Clear existing content to avoid stale data
                        attributeContainer.innerHTML = '';
                    }
                    attributeContainer.style.display = 'block';

                    // Check if old instance exists
                    if (window.datasetAttributeTableInstance) {
                        console.log('→ OLD instance found, isEditMode:', window.datasetAttributeTableInstance.isEditMode);
                        console.log('→ Destroying old instance and creating fresh one');
                        window.datasetAttributeTableInstance = null;
                    } else {
                        console.log('→ No old instance found');
                    }

                    console.log('→ Creating NEW AttributeTable instance for dataset ID:', id);
                    if (window.AttributeTable) {
                        // Always create a fresh instance in view mode to avoid stale edit mode
                        // Pass view parameter to filter pending changes in View Original mode
                        const viewParam = (currentView === 'changes') ? 'changes' : null;
                        console.log('→ Creating AttributeTable with view:', viewParam, '(currentView:', currentView, ')');
                        const table = new window.AttributeTable('datasetAttributeContainer', id, viewParam);
                        console.log('→ Instance created, isEditMode BEFORE setting:', table.isEditMode);
                        table.isEditMode = false; // Explicitly set view mode
                        console.log('→ Instance created, isEditMode AFTER setting:', table.isEditMode);
                        console.log('→ Calling init()...');
                        table.init();
                        // Store for potential reuse
                        window.datasetAttributeTableInstance = table;
                        console.log('→ Instance stored globally');
                        console.log('========================================');
                    } else {
                        console.error('AttributeTable class not found');
                    }
                } else if (which === 'impact') {
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show impact container
                    let impactContainer = document.getElementById('datasetImpactContainer');
                    if (!impactContainer) {
                        impactContainer = document.createElement('div');
                        impactContainer.id = 'datasetImpactContainer';
                        impactContainer.className = 'view-section';
                        impactContainer.style.gridColumn = '1/-1';
                        body.appendChild(impactContainer);
                    }
                    impactContainer.style.display = 'block';

                    // Wait a bit for toggle to initialize if needed, then get view mode
                    // This ensures we get the correct view mode even on page refresh
                    await new Promise(resolve => setTimeout(resolve, 100));
                    const viewMode = getCurrentViewMode();
                    loadImpactData(id, viewMode);
                } else if (which === 'relationships') {
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show relationships container
                    let relationshipsContainer = document.getElementById('datasetRelationshipsContainer');
                    if (!relationshipsContainer) {
                        relationshipsContainer = document.createElement('div');
                        relationshipsContainer.id = 'datasetRelationshipsContainer';
                        relationshipsContainer.className = 'view-section';
                        relationshipsContainer.style.gridColumn = '1/-1';
                        body.appendChild(relationshipsContainer);
                    }
                    relationshipsContainer.style.display = 'block';

                    // Let loadRelationshipsData inject map HTML and call init after content is set.
                    // Do not call initializeDatasetRelationshipsMap or init here: loadRelationshipsData
                    // first replaces the container with "Loading...", which would remove the canvas
                    // and cause the scheduled init to run before the canvas exists.
                    const viewMode = getCurrentViewMode();
                    loadRelationshipsData(id, viewMode === 'changes' ? 'changes' : null);
                } else if (which === 'history') {
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show history container
                    let historyContainer = document.getElementById('datasetHistoryContainer');
                    if (!historyContainer) {
                        historyContainer = document.createElement('div');
                        historyContainer.id = 'datasetHistoryContainer';
                        historyContainer.className = 'view-section';
                        historyContainer.style.gridColumn = '1/-1';
                        body.appendChild(historyContainer);
                    }
                    historyContainer.style.display = 'block';

                    // Initialize history component for Data Sets
                    console.log('Creating HistoryComponent for Data Sets with id:', id);
                    if (window.HistoryComponent) {
                        // Wait a bit for the container to be ready
                        setTimeout(async () => {
                            try {
                                console.log('Initializing history component with parameters:', {
                                    facetName: 'Data Sets',
                                    objectId: id,
                                    containerId: 'datasetHistoryContainer'
                                });
                                await window.HistoryComponent.initialize('Data Sets', id, 'datasetHistoryContainer');
                                console.log('HistoryComponent initialized successfully');
                            } catch (error) {
                                console.error('Error initializing HistoryComponent:', error);
                                historyContainer.innerHTML = `
                                    <div style="color: red; padding: 1rem; border: 1px solid red; border-radius: 4px; margin: 1rem;">
                                        <h4>Error loading history component</h4>
                                        <p><strong>Error:</strong> ${error.message}</p>
                                        <p><strong>Dataset ID:</strong> ${id}</p>
                                        <p><strong>URL:</strong> ${window.location.href}</p>
                                    </div>
                                `;
                            }
                        }, 100);
                    } else {
                        console.error('HistoryComponent not found');
                        historyContainer.innerHTML = '<div style="color: red; padding: 1rem;">History component not available</div>';
                    }
                } else if (which === 'change') {
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show change container
                    let changeContainer = document.getElementById('datasetChangeContainer');
                    if (!changeContainer) {
                        changeContainer = document.createElement('div');
                        changeContainer.id = 'datasetChangeContainer';
                        changeContainer.className = 'view-grid';
                        changeContainer.style.gridColumn = '1/-1';
                        body.appendChild(changeContainer);
                    }
                    changeContainer.style.display = 'grid';

                    // Initialize change tab component
                    if (window.ChangeTabComponent) {
                        window.ChangeTabComponent.initialize('dataset', id, 'datasetChangeContainer');
                    } else {
                        const changeNotAvailableText = window.I18n?.t('message.changeComponentNotAvailable') || 'Change component not available';
                        changeContainer.innerHTML = `<div class="empty-state"><i class="fas fa-exclamation-triangle"></i><p>${changeNotAvailableText}</p></div>`;
                    }
                } else {
                    // Default to details tab - Hide all other containers first
                    const otherContainers = [
                        'datasetStakeholdersContainer',
                        'datasetValuesContainer',
                        'datasetAttributeContainer',
                        'datasetRelationshipsContainer',
                        'datasetImpactContainer',
                        'datasetHistoryContainer',
                        'datasetChangeContainer'
                    ];
                    otherContainers.forEach(containerId => {
                        const el = document.getElementById(containerId);
                        if (el) {
                            el.style.display = 'none';
                        }
                    });

                    // Show or create main dataset view
                    let mainContainer = document.getElementById('datasetViewContainer');
                    if (!mainContainer) {
                        mainContainer = document.createElement('div');
                        mainContainer.id = 'datasetViewContainer';
                        mainContainer.className = 'view-grid';
                        body.appendChild(mainContainer);
                    }
                    // Force visibility with proper display type
                    mainContainer.style.display = 'flex';
                    mainContainer.style.flexDirection = 'column';
                    mainContainer.style.visibility = 'visible';
                    mainContainer.style.opacity = '1';
                    mainContainer.style.gap = '1.25rem';
                    console.log('[Dataset] Default/Details tab - showing main container');

                    // Also ensure content-body is visible
                    if (body) {
                        body.style.display = 'flex';
                        body.style.flexDirection = 'column';
                        body.style.visibility = 'visible';
                        body.style.opacity = '1';
                    }

                    if (id != null) {
                        const view = getCurrentViewMode();
                        load(id, view).then(async (d) => {
                            // After load, ensure everything is visible
                            if (mainContainer) {
                                mainContainer.style.display = 'flex';
                                mainContainer.style.flexDirection = 'column';
                                mainContainer.style.visibility = 'visible';
                                mainContainer.style.opacity = '1';
                                mainContainer.style.gap = '1.25rem';
                                console.log('[Dataset] Default/Details tab - container visible after load');
                            }
                            
                            // Title is already updated in load function
                        });
                    }
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('datasetStakeholdersContainer');
                }
            });
        });

        // Auto-switch to tab if specified in URL
        if (shouldSwitchTab) {
            console.log('URL parameter tab detected:', shouldSwitchTab);
            setTimeout(() => {
                const targetTab = document.querySelector(`.tab[data-tab="${shouldSwitchTab}"]`);
                if (targetTab) {
                    console.log('Switching to tab:', shouldSwitchTab);
                    targetTab.click();
                    
                    // If switching to attribute tab and attributeId is specified, highlight it and open modal
                    if (shouldSwitchTab === 'attribute' && attributeIdParam) {
                        console.log('Attribute ID to highlight and open:', attributeIdParam);
                        setTimeout(() => {
                            const attributeRow = document.querySelector(`tr[data-attribute-id="${attributeIdParam}"]`);
                            if (attributeRow) {
                                console.log('Found attribute row, scrolling, highlighting, and opening modal');
                                attributeRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                attributeRow.classList.add('highlighted-row');
                                
                                // Open the modal after scrolling
                                setTimeout(() => {
                                    // Trigger click to open the viewer modal
                                    attributeRow.click();
                                    console.log('Modal opened for attribute ID:', attributeIdParam);
                                    
                                    // Remove highlight after modal opens
                                    setTimeout(() => {
                                        attributeRow.classList.remove('highlighted-row');
                                    }, 1000);
                                }, 800); // Wait for scroll animation to complete
                            } else {
                                console.log('Attribute row not found in DOM');
                            }
                        }, 500); // Wait for table to render
                    }
                } else {
                    console.log('Tab not found:', shouldSwitchTab);
                }
            }, 300); // Wait for tabs to be initialized
        }

        // Listen for pending changes view switch event (to handle Original/Changes toggle)
        document.addEventListener('pendingChangesViewSwitch', async function(event) {
            const { view, facetType, objectId } = event.detail;
            // Support both 'Dataset' and 'Data Sets' facet names
            if ((facetType === 'Dataset' || facetType === 'Data Sets') && objectId) {
                // Prevent re-entrant calls that cause infinite loops
                if (isReloading) {
                    console.log('[Dataset] Skipping view switch - already reloading');
                    return;
                }
                
                console.log('[Dataset] View switch event received:', view);
                currentView = view; // Update currentView to match toggle
                
                // Always update the title/header regardless of which tab is active (same pattern as Glossary and System)
                await updateTitleOnly(objectId, view);
                
                // Get current active tab
                const activeTab = document.querySelector('.tab-container .tab.active');
                const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'details';
                console.log('[Dataset] Active tab:', tabName);
                
                // Reload current tab with new view
                const body = document.querySelector('.content-body');
                if (body) {
                    isReloading = true;
                    try {
                        switch (tabName) {
                            case 'details':
                                await load(objectId, view);
                                // Title is already updated in load function (same pattern as Glossary and System)
                                break;
                            case 'stakeholders':
                                // Clear old content immediately so we don't show stale data while switching views
                                let stakeholdersContainer = document.getElementById('datasetStakeholdersContainer');
                                if (stakeholdersContainer) {
                                    const loadingStakeholdersText = window.I18n?.t('dataset.loading.loadingStakeholders') || 'Loading stakeholders...';
                                    stakeholdersContainer.innerHTML = `<div class="loading">${loadingStakeholdersText}</div>`;
                                }
                                if (window.DatasetStakeholderView) {
                                    window.DatasetStakeholderView.init(objectId, view);
                                }
                                break;
                            case 'impact':
                                // Clear old content immediately so we don't show stale data while switching views
                                let impactContainer = document.getElementById('datasetImpactContainer');
                                if (impactContainer) {
                                    const loadingImpactText2 = window.I18n?.t('dataset.loading.loadingImpact') || 'Loading impact...';
                                    impactContainer.innerHTML = `<div class="loading">${loadingImpactText2}</div>`;
                                }
                                await loadImpactData(objectId, view);
                                break;
                            case 'values':
                                // Reload values view with new view
                                if (window.datasetValuesViewInstance && window.datasetValuesViewInstance.datasetId === objectId) {
                                    // Reuse existing instance - just update view and reload
                                    console.log('[Dataset] Reloading values view with view:', view);
                                    window.datasetValuesViewInstance.setView(view === 'changes' ? 'changes' : null);
                                    await window.datasetValuesViewInstance.loadMetadata();
                                } else if (window.DatasetValuesView) {
                                    // Create new instance if it doesn't exist
                                    console.log('[Dataset] Creating new values view instance with view:', view);
                                    const viewParam = (view === 'changes') ? 'changes' : null;
                                    const valuesView = new window.DatasetValuesView('datasetValuesContainer', objectId, viewParam);
                                    window.datasetValuesViewInstance = valuesView;
                                    await valuesView.init();
                                }
                                break;
                            case 'attribute':
                                // Clear old content immediately so we don't show stale data while switching views
                                let attributeContainer = document.getElementById('datasetAttributeContainer');
                                if (attributeContainer) {
                                    attributeContainer.innerHTML = '<div class="loading">Loading attributes...</div>';
                                }
                                // Reload attribute table with new view
                                if (window.datasetAttributeTableInstance) {
                                    window.datasetAttributeTableInstance.setView(view === 'changes' ? 'changes' : null);
                                    await window.datasetAttributeTableInstance.load();
                                    window.datasetAttributeTableInstance.render();
                                } else if (window.AttributeTable) {
                                    // Create new instance if it doesn't exist
                                    const viewParam = (view === 'changes') ? 'changes' : null;
                                    const table = new window.AttributeTable('datasetAttributeContainer', objectId, viewParam);
                                    table.isEditMode = false;
                                    await table.init();
                                    window.datasetAttributeTableInstance = table;
                                }
                                break;
                            case 'relationships':
                                // Clear old content immediately so we don't show stale data while switching views
                                let relationshipsContainer = document.getElementById('datasetRelationshipsContainer');
                                if (relationshipsContainer) {
                                    const loadingRelationshipsText = window.I18n?.t('dataset.loading.loadingRelationships') || 'Loading relationships data...';
                                    relationshipsContainer.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${loadingRelationshipsText}</div>`;
                                }
                                // Reload relationships with correct view
                                loadRelationshipsData(objectId, view === 'changes' ? 'changes' : null);
                                break;
                            case 'history':
                            case 'change':
                                // These tabs don't change based on view mode but title still updates
                                break;
                            default:
                                await load(objectId, view);
                                break;
                        }
                        
                        // Also reload documents with new view mode
                        if (datasetDocumentTable) {
                            datasetDocumentTable.setViewMode(view);
                        }
                    } finally {
                        isReloading = false;
                    }
                }
            }
        });

    });

})();

