(function () {
    let currentView = 'original'; // 'original' | 'changes'
    let _canEditObject = false; // Whether user can normally edit this object (without auto CR restriction)
    let _hasActiveAutoCR = false; // Whether an active auto CR is blocking edits

    // Grid Settings – delegate to /assets/js/grid-settings.js (loaded before this file)
    var gridSettingsHtml = window.GridSettings ? window.GridSettings.html : function() { return ''; };
    var initGridSettings = window.GridSettings ? window.GridSettings.init : function() {};

    function getCurrentViewMode() {
        const activeToggle = document.querySelector('.revision-toggle-link.active');
        if (activeToggle) {
            const v = activeToggle.dataset.view;
            if (v === 'original' || v === 'changes') {
                currentView = v;
                return v;
            }
        }
        return currentView || 'original';
    }
    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        // expect /view/system/{id}
        const idx = parts.indexOf('system');
        if (idx !== -1 && parts.length >= idx + 2) {
            const id = parseInt(parts[idx + 1], 10);
            if (!Number.isNaN(id)) return id;
        }
        const qp = new URLSearchParams(window.location.search).get('id');
        if (qp != null && qp !== '') {
            const id = parseInt(qp, 10);
            if (!Number.isNaN(id)) return id;
        }
        return null;
    }
    function sT(key, fallback, params) {
        if (!window.I18n || typeof window.I18n.t !== 'function') return fallback != null ? fallback : key;
        const str = window.I18n.t(key, params);
        if (str === key && fallback != null) return fallback;
        return str;
    }
    function hideEditControls() {
        // Hide the edit dropdown for unauthorized users
        if (window.EditDropdown) {
            window.EditDropdown.hideEditControls();
        }
        const ids = [
            'editStakeholdersBtn'
        ];
        ids.forEach(function (id) {
            const el = document.getElementById(id);
            if (el) el.style.display = 'none';
        });
        try {
            document.querySelectorAll('[data-action="edit"], #attrEnterEdit').forEach(function (el) {
                el.style.display = 'none';
            });
        } catch (_) { }
    }

    async function hideEditsIfUnauthenticated() {
        try {
            const id = parseId();
            if (!id) {
                console.log('[System] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the new permissions API to check user permissions for System module
            const permResp = await fetch('/api/user/permissions/System', { method: 'GET', credentials: 'include' });
            if (!permResp.ok) { 
                console.log('API /api/user/permissions/System failed:', permResp.status);
                // Fallback to old method
                await hideEditsIfUnauthenticatedFallback();
                return; 
            }
            const perms = await permResp.json();
            console.log('User permissions for System:', perms);
            
            if (perms.success) {
                const hasRoleEditPermission = perms.canEdit || perms.isAdmin;
                const canDelete = perms.canDelete; // Only Super Admins can delete
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
                        const stakeholderResp = await fetch(`/api/check-stakeholder/System/${id}`, {
                            method: 'GET',
                            credentials: 'include'
                        });
                        if (stakeholderResp.ok) {
                            const stakeholderData = await stakeholderResp.json();
                            canEditObject = stakeholderData.isStakeholder === true;
                            console.log('[System] User is stakeholder?', canEditObject);
                        } else {
                            console.warn('[System] Failed to check stakeholder status:', stakeholderResp.status);
                            canEditObject = false; // Fail securely
                        }
                    } catch (stakeholderError) {
                        console.error('[System] Error checking stakeholder status:', stakeholderError);
                        canEditObject = false; // Fail securely
                    }
                }
                
                // Store canEditObject at module level for stakeholder tab redirect
                _canEditObject = canEditObject;
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[System] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[System] User cannot edit this object - hiding edit controls');
                    hideEditControls();
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
                hideEditControls();
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
                hideEditControls(); 
                return; 
            }
            const me = await meResp.json();
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            if (!isAdmin) {
                hideEditControls();
            } else {
                showEditControls();
            }
        } catch (e) {
            hideEditControls();
        }
    }
    
    function hideDeleteControls() {
        // Hide delete buttons - only admin/super admin can delete
        const deleteSelectors = [
            '[data-action="delete"]',
            '.delete-btn',
            '.btn-delete',
            '#deleteBtn',
            '#deleteSystemBtn'
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
        const ids = [
            'editStakeholdersBtn'
        ];
        ids.forEach(function (id) {
            const el = document.getElementById(id);
            if (el) {
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            }
        });
        try {
            document.querySelectorAll('[data-action="edit"], #attrEnterEdit').forEach(function (el) {
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            });
        } catch (_) { }
        console.log('Edit controls shown for admin user');
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

    /** DB External: false/0 = Internal, true/1 = External (same as edit form). */
    function systemExternalScopeSuffix(data) {
        if (!data) return '';
        const raw = Object.prototype.hasOwnProperty.call(data, 'external') ? data.external : data.External;
        if (raw === null || raw === undefined) return '';
        if (typeof raw === 'boolean') return raw ? ' - External' : ' - Internal';
        if (typeof raw === 'number' && !Number.isNaN(raw)) return raw !== 0 ? ' - External' : ' - Internal';
        const s = String(raw).trim().toLowerCase();
        if (s === 'true' || s === '1' || s === 'yes') return ' - External';
        if (s === 'false' || s === '0' || s === 'no') return ' - Internal';
        return '';
    }

    function renderSystemTypeDisplayHtml(data) {
        let base = '';
        if (data.typeName != null && String(data.typeName).trim() !== '') {
            base = String(data.typeName).trim();
        } else if (data.Type != null && String(data.Type).trim() !== '') {
            base = String(data.Type).trim();
        }
        if (!base) {
            return '<span class="empty">Type not available</span>';
        }
        return escapeHtml(base + systemExternalScopeSuffix(data));
    }

    function renderItem(label, valueHtml, fieldKey) {
        // Handle empty strings and null/undefined values
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
    function truncateUrl(url, maxLength = 50) {
        if (!url || url.length <= maxLength) return url;
        return url.substring(0, maxLength) + '...';
    }
    function formatDateTime(dt) {
        if (!dt) return '<span class="empty">-</span>';
        try {
            const d = typeof dt === 'string' || typeof dt === 'number' ? new Date(dt) : dt;
            if (isNaN(d.getTime())) return escapeHtml(String(dt));
            return escapeHtml(d.toLocaleString());
        } catch (_) {
            return escapeHtml(String(dt));
        }
    }

    function renderCollapsible(title, bodyHtml, opts) {
        const id = (opts && opts.id) ? String(opts.id) : `sec-${Math.random().toString(36).slice(2, 8)}`;
        const startOpen = !!(opts && opts.open);
        const iconClass = (opts && opts.icon) ? String(opts.icon) : '';
        const gsId = (opts && opts.gridSettingsId) ? opts.gridSettingsId : '';
        return `
            <div class="collapsible-section ${startOpen ? 'open' : ''}" data-collapsible id="${id}">
                <div style="display:flex;align-items:center;">
                    <button type="button" class="collapsible-header" aria-expanded="${startOpen}" style="flex:1;">
                        <span class="caret" aria-hidden="true"></span>
                        ${iconClass ? `<i class="${iconClass}" aria-hidden="true" style="color:var(--text-muted,#6b7280);"></i>` : ''}
                        <span class="title">${title}</span>
                    </button>
                    ${gsId ? `<div style="padding-right:0.75rem;">${gridSettingsHtml(gsId)}</div>` : ''}
                </div>
                <div class="collapsible-body" ${startOpen ? '' : 'style="display:none;"'}>
                    ${bodyHtml}
                </div>
            </div>
        `;
    }

    function renderHierarchy(h, currentSystemName, currentSystemId) {
        if (!h || (!h.parentId && !h.parentName && (!h.parentChain || h.parentChain.length === 0))) {
            // No parent - just show current system as the only node
            if (currentSystemName) {
                return `<div class="hierarchy-path"><span class="hierarchy-current">${escapeHtml(currentSystemName)}</span></div>`;
            }
            return '<span class="empty">No parent</span>';
        }

        // Build full path: all ancestors → current system (with arrows)
        if (h.parentChain && Array.isArray(h.parentChain) && h.parentChain.length > 0) {
            const parts = h.parentChain.map((ancestor) => {
                const name = escapeHtml(ancestor.name || `#${ancestor.id}`);
                return `<a href="/view/system/${encodeURIComponent(ancestor.id)}" class="hierarchy-link">${name}</a>`;
            });

            // Add current system at the end (highlighted, not a link)
            if (currentSystemName) {
                parts.push(`<span class="hierarchy-current">${escapeHtml(currentSystemName)}</span>`);
            }

            const arrow = '<span class="hierarchy-arrow"><i class="fas fa-chevron-right"></i></span>';
            return `<div class="hierarchy-path">${parts.join(arrow)}</div>`;
        }

        // Fallback to single parent + current system
        const parent = escapeHtml(h.parentName || `#${h.parentId}`);
        const arrow = '<span class="hierarchy-arrow"><i class="fas fa-chevron-right"></i></span>';
        const parentLink = h.parentId 
            ? `<a href="/view/system/${encodeURIComponent(h.parentId)}" class="hierarchy-link">${parent}</a>`
            : `<span class="hierarchy-link">${parent}</span>`;
        const currentPart = currentSystemName ? `${arrow}<span class="hierarchy-current">${escapeHtml(currentSystemName)}</span>` : '';
        return `<div class="hierarchy-path">${parentLink}${currentPart}</div>`;
    }

    function renderPerson(name, id, data, rawKey) {
        if (!name) return '<span class="empty">-</span>';
        // تحديد كل الحقول المحتملة للمعرف إذا لم يُمرر مباشرة
        let personId = id;
        if (!personId && data && rawKey) {
            const possibleKeys = [
                `${rawKey}Id`, // lastUpdatedById
                `updatedById`,
                `last_updateuser_id`,
                `lastUpdatedUserId`,
                `last_updated_userid`,
                // دعم camelCase/PascalCase وأحرف مختلفة
                `${rawKey}_id`,
                `${rawKey}ID`,
            ];
            for (const k of possibleKeys) {
                if (data[k]) { personId = data[k]; break; }
            }
        }
        if (personId) {
            return `<a href="/view/people/${encodeURIComponent(personId)}" class="people-link">${escapeHtml(name)}</a>`;
        }
        return escapeHtml(name);
    }


    // Guest / segment access control for System view
    let _systemGuestCache = null;
    async function isGuestVisitorForSystem() {
        if (_systemGuestCache !== null) {
            return _systemGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _systemGuestCache = true;
                return _systemGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _systemGuestCache = !isAuthenticated || isGuestRole;
            return _systemGuestCache;
        } catch (_) {
            _systemGuestCache = true;
            return _systemGuestCache;
        }
    }

    function renderSystemGuestAccessDenied(container, systemId, segmentName, segmentId) {
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

    async function load(id, view = null) {
        const container = document.getElementById('systemViewContainer');
        if (!container) {
            console.error('[System] Container not found: systemViewContainer');
            return;
        }
        
        // Ensure container is visible before loading
        container.style.display = '';
        container.style.visibility = 'visible';
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading...</div>';
        try {
            // MANDATORY: Always default to 'original' view on page load/refresh
            // User can manually switch to 'changes' via toggle, but default is always 'original'
            let viewToUse = view;
            if (viewToUse == null) {
                try {
                    const activeToggle = document.querySelector('.revision-toggle-link.active');
                    if (activeToggle && (activeToggle.dataset.view === 'original' || activeToggle.dataset.view === 'changes')) {
                        viewToUse = activeToggle.dataset.view;
                    }
                } catch (_) { }
            }
            if (viewToUse == null) {
                // Clear any saved 'changes' from sessionStorage to ensure default is always 'original'
                try {
                    const key = `pendingChanges:view:System#${id}`;
                    if (typeof sessionStorage !== 'undefined') {
                        const saved = sessionStorage.getItem(key);
                        if (saved === 'changes') {
                            sessionStorage.removeItem(key);
                        }
                    }
                } catch (_) { }
                // Always default to 'original' - mandatory requirement
                viewToUse = 'original';
            }
            currentView = viewToUse;

            const data = await window.BUDG_API_SERVICE.getSystemById(id, viewToUse === 'changes' ? 'changes' : null);
            try { window.BUDG_API_SERVICE.logVisit({ userId: 1, entity: 'System', entityId: String(id), route: `/view/system/${id}` }); } catch (_) { }

            // Guest / segment gating: unauthenticated users may only view Enterprise segment
            try {
                const isGuest = await isGuestVisitorForSystem();
                const segmentName = data?.segmentName || data?.segment_name || data?.segment;
                const segmentId = data?.segmentId ?? data?.segment_id ?? data?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();

                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[System] Guest visitor blocked from non-Enterprise segment view', {
                        systemId: id,
                        segmentName,
                        segmentId
                    });
                    renderSystemGuestAccessDenied(container, id, segmentName, segmentId);
                    return;
                }
            } catch (gateError) {
                console.warn('[System] Error during guest/segment access check, falling back to normal render:', gateError);
            }

            // Log loaded data for debugging
            console.log('[System] Loaded data:', {
                id: data?.id,
                name: data?.name,
                viewToUse: viewToUse,
                expectedId: id,
                isCloned: data?.id !== id
            });

            // Update page title with system name IMMEDIATELY after loading data (same pattern as Dataset)
            // Store the correct name to use for title updates
            let systemNameForTitle = null;
            if (data && data.name) {
                // Verify that we're using the correct data based on viewToUse
                // If viewToUse is 'original', data.id should match the original id
                if (viewToUse === 'original' && data.id !== id) {
                    console.warn('[System] WARNING: Loaded original view but got cloned data! data.id=', data.id, 'expected id=', id);
                    // Reload with explicit original view to get correct name
                    try {
                        const originalData = await window.BUDG_API_SERVICE.getSystemById(id, null);
                        console.log('[System] Reloaded original data:', {
                            id: originalData?.id,
                            name: originalData?.name,
                            matchesExpected: originalData?.id === id
                        });
                        if (originalData && originalData.name && originalData.id === id) {
                            systemNameForTitle = originalData.name;
                            // Update data reference to use correct original data
                            data.name = originalData.name;
                            data.id = originalData.id;
                        } else {
                            systemNameForTitle = data.name;
                        }
                    } catch (e) {
                        console.warn('[System] Failed to reload original data:', e);
                        systemNameForTitle = data.name;
                    }
                } else {
                    systemNameForTitle = data.name;
                }
            }
            
            // Function to update title (can be called multiple times to ensure it stays correct)
            const updateTitleElement = () => {
                const pageTitleMain = document.querySelector('.page-title-main');
                const pageTitleSub = document.querySelector('.page-title-sub');
                if (pageTitleMain) {
                    if (systemNameForTitle) {
                        pageTitleMain.textContent = escapeHtml(systemNameForTitle);
                        console.log('[System] Title updated to:', systemNameForTitle, '(view:', viewToUse, ', id:', id, ')');
                    } else {
                        console.warn('[System] System name not found in data:', data);
                        pageTitleMain.textContent = 'System';
                    }
                } else {
                    console.warn('[System] Title element not found (.page-title-main)');
                }
                if (pageTitleSub) pageTitleSub.textContent = 'System';
            };
            
            // Update title immediately
            updateTitleElement();

            // Initialize pending changes toggle if object is under revision AFTER updating title
            if (window.PendingChanges) {
                await window.PendingChanges.initToggle('System', id, '.page-title-text', {
                    applyChanges: (changesMap) => {
                        console.log('[System] Applying pending changes:', changesMap);
                        Object.keys(changesMap).forEach(fieldName => {
                            const value = changesMap[fieldName];
                            let elements = document.querySelectorAll(`[data-field="${fieldName}"]`);
                            if (elements.length === 0) {
                                elements = document.querySelectorAll(`[data-field="${fieldName.toLowerCase()}"]`);
                            }
                            if (!elements.length) return;

                            let displayValue = value;
                            elements.forEach(el => {
                                const empty = displayValue === null || displayValue === undefined || displayValue === '' || displayValue === 'null';
                                el.textContent = empty ? (window.I18n?.t('message.notSpecified') || 'Not specified') : displayValue;
                                el.classList.add('pending-change-applied');
                            });
                        });
                    },
                    restoreOriginal: () => { }
                });
                
                // Re-update title after toggle init to ensure it's still correct (same pattern as Dataset)
                // Use setTimeout to ensure DOM is fully settled after initToggle
                setTimeout(() => {
                    // Re-update title using the stored system name
                    updateTitleElement();
                    
                    // Double-check after a short delay to ensure it wasn't overwritten
                    setTimeout(() => {
                        const titleEl = document.querySelector('.page-title-main');
                        if (titleEl && systemNameForTitle) {
                            const currentTitle = titleEl.textContent;
                            if (currentTitle === 'System' && systemNameForTitle !== 'System') {
                                console.warn('[System] Title was overwritten to "System", correcting to:', systemNameForTitle);
                                titleEl.textContent = escapeHtml(systemNameForTitle);
                            }
                        }
                    }, 100);
                }, 50); // Small delay to ensure DOM is settled
                
                // After toggle is initialized, check the toggle state and sync currentView (same pattern as Dataset)
                // Ensure 'original' toggle is active if we loaded with original view
                const allToggles = document.querySelectorAll('.revision-toggle-link');
                const originalToggle = Array.from(allToggles).find(t => t.dataset.view === 'original');
                const changesToggle = Array.from(allToggles).find(t => t.dataset.view === 'changes');
                
                if (viewToUse === 'original' && originalToggle && !originalToggle.classList.contains('active')) {
                    // Ensure original toggle is active
                    originalToggle.classList.add('active');
                    originalToggle.textContent = 'Viewing Original';
                    if (changesToggle) {
                        changesToggle.classList.remove('active');
                        changesToggle.textContent = 'View Changes';
                    }
                    console.log('[System] Ensured original toggle is active');
                }
                
                const activeToggle = document.querySelector('.revision-toggle-link.active');
                if (activeToggle) {
                    const toggleView = activeToggle.dataset.view;
                    if (toggleView === 'original' || toggleView === 'changes') {
                        currentView = toggleView;
                        console.log('[System] Synced currentView with toggle state:', currentView);
                    }
                }
            }

            const cia = data.cia || {};
            const ciaHtml = `<div class=\"cia-inline\">\n                <div class=\"cia-item\"><span class=\"view-badge\">C</span> ${escapeHtml(cia.c ?? '')}</div>\n                <div class=\"cia-item\"><span class=\"view-badge\">I</span> ${escapeHtml(cia.i ?? '')}</div>\n                <div class=\"cia-item\"><span class=\"view-badge\">A</span> ${escapeHtml(cia.a ?? '')}</div>\n            </div>`;

            const docsEmpty = '<div class="empty">' + escapeHtml(sT('system.view.noDocuments', 'This System has no documents.')) + '</div>';
            const dcsEmpty = '<div id="dataContentContainer" class="data-table-wrapper"><div class="empty">' + escapeHtml(sT('system.view.dataContentLoading', 'Loading...')) + '</div></div>';

            container.innerHTML = `
                <div class="system-container">
                    <!-- Left Column: DESCRIPTION + collapsibles -->
                    <div class="system-left-column">
                        <div class="form-card description-card">
                            <div class="card-header">
                                <h3 class="card-title">${escapeHtml(sT('card.description_upper', 'DESCRIPTION'))}</h3>
                            </div>
                            <div class="card-body">
                            <div class="description-text" data-field="description">${escapeHtml(data.description || data.name || '')}</div>
                            ${data.hierarchy && (data.hierarchy.parentId || (data.hierarchy.parentChain && data.hierarchy.parentChain.length > 0)) ? renderItem(sT('label.hierarchy', 'Hierarchy'), renderHierarchy(data.hierarchy, data.name, data.id), 'hierarchy') : ''}
                            ${renderItem(sT('label.longName', 'Long Name'), escapeHtml(data.longName || sT('system.messages.notSpecified', 'Not specified')), 'long_name')}
                                ${renderItem(sT('label.type', 'Type'), renderSystemTypeDisplayHtml(data), 'type')}
                                ${renderItem(sT('label.url', 'URL'), data.url ? `<a href="${escapeHtml(data.url)}" target="_blank" title="${escapeHtml(data.url)}">${escapeHtml(truncateUrl(data.url))}</a>` : '', 'url')}
                            </div>
                        </div>
                        <div class="view-section">
                            ${renderCollapsible((window.I18n && window.I18n.t('system.dataContentSummary.title')) || 'DATA CONTENT SUMMARY', dcsEmpty, { id: 'data-content-summary', icon: 'fa-solid fa-database', gridSettingsId: 'viewDcs' })}
                        </div>
                        <div class="view-section">
                            ${renderCollapsible((window.I18n?.t('card.documents') || 'DOCUMENTS'), docsEmpty, { id: 'documents', icon: 'fa-solid fa-file-lines', gridSettingsId: 'viewDocs' })}
                        </div>
                    </div>

                    <!-- Right Column: CLASSIFICATIONS -->
                    <div class="form-card classifications-card">
                        <div class="card-header">
                            <h3 class="card-title">${window.I18n?.t('card.classifications') || 'CLASSIFICATIONS'}</h3>
                        </div>
                        <div class="card-body">
                            <div class="section-subtitle">${escapeHtml(sT('glossary.sections.basicClassifications', 'BASIC CLASSIFICATIONS'))}</div>
                            ${renderItem(sT('glossary.labels.budgStatus', 'BUDG Status'), escapeHtml(data.statusName || ''), 'status')}
                            ${renderItem(sT('glossary.labels.lifecycle', 'Lifecycle'), escapeHtml(data.lifecycleName || ''), 'lifecycle')}
                            ${renderItem(sT('glossary.labels.budgViewing', 'BUDG Viewing'), escapeHtml(data.viewingName != null ? data.viewingName : ''), 'viewing')}
                            ${renderItem(sT('label.classification', 'Classification'), escapeHtml(data.classificationName || data.Classification || ''), 'classification')}
                            ${renderItem(sT('glossary.labels.createdBy', 'Created By'), renderPerson(data.createdByName, data.createdById, data, 'createdBy'))}
                            ${renderItem(sT('glossary.labels.created', 'Created'), formatDateTime(data.createdDatetime), 'created')}
                            ${renderItem(sT('glossary.labels.lastUpdatedBy', 'Last Updated By'), renderPerson(data.lastUpdatedByName, data.lastUpdatedById, data, 'lastUpdatedBy'), 'lastUpdatedBy')}
                            ${renderItem(sT('glossary.labels.lastUpdated', 'Last Updated'), formatDateTime(data.lastUpdatedDatetime), 'lastUpdated')}
                            ${renderItem(sT('glossary.labels.lastApprovedDate', 'Last Approved Date'), escapeHtml(data.lastApprovedDate || sT('glossary.values.notAvailable', 'Not Available')), 'lastApprovedDate')}
                            ${renderItem(sT('glossary.labels.nextReviewDate', 'Next Review Date'), escapeHtml(data.nextReviewDate || sT('glossary.values.notEnforced', 'Not Enforced')), 'nextReviewDate')}
                            <div class="section-subtitle" style="margin-top: 0.5rem;">${escapeHtml(sT('glossary.sections.otherInformation', 'OTHER INFORMATION'))}</div>
                            ${renderItem(sT('label.automaticallyControlLocalRules', 'Automatically Control Local Rules'), (function () {
                const v = data.dqAutomation;
                if (v == null) return '<span class="empty">-</span>';
                const yes = String(v) === '1' || String(v).toLowerCase() === 'true';
                return yes ? sT('common.yes', 'Yes') : sT('common.no', 'No');
            })(), 'automaticallyControlLocalRules')}
                            ${renderItem(sT('glossary.labels.ciaRating', 'CIA Rating'), ciaHtml, 'ciaRating')}
                            ${renderItem(sT('glossary.labels.segment', 'Segment'), escapeHtml(data.segmentName || data.segment_name || data.segment || ((data.segmentId ?? data.segment_id ?? data.Segment_ID) != null ? sT('common.segmentIdDisplay', 'ID {id}', { id: data.segmentId ?? data.segment_id ?? data.Segment_ID }) : sT('system.messages.notSpecified', 'Not specified'))), 'segment')}
                        </div>
                    </div>
                </div>
            `;

            // Ensure container is visible after loading data - use requestAnimationFrame for better rendering
            if (container) {
                // Use requestAnimationFrame to ensure DOM is ready
                requestAnimationFrame(() => {
                    container.style.display = '';
                    container.style.visibility = 'visible';
                    container.style.opacity = '1';
                    console.log('[System] Container displayed after loading data');
                    
                    // Force a reflow to ensure rendering
                    void container.offsetHeight;
                });
            }

            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('system', id, null, '.form-actions');
            }


            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'System',
                        containerId: 'systemViewContainer',
                        objectId: id,
                        title: 'CUSTOM FIELDS'
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

            // Wire up collapsible toggles
            container.querySelectorAll('[data-collapsible] .collapsible-header').forEach(function (btn) {
                btn.addEventListener('click', function () {
                    const section = this.closest('[data-collapsible]');
                    const body = section.querySelector('.collapsible-body');
                    const isOpen = section.classList.contains('open');
                    section.classList.toggle('open');
                    this.setAttribute('aria-expanded', String(!isOpen));
                    if (body) body.style.display = isOpen ? 'none' : '';
                });
            });

            // Initialize grid settings for collapsible section headers
            initGridSettings(container);

            // Load Data Content Summary when present (view-aware)
            const dcsBody = document.getElementById('dataContentContainer');
            if (dcsBody) loadDataContentSummary(id, dcsBody, getCurrentViewMode());

            // Load Documents (pass view mode to exclude pending documents in original view)
            loadDocuments(id, view);
        } catch (e) {
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const message = isForbidden ? 'This object is not available.' : `Failed to load system (id=${id}).`;
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column: 1/-1; color: var(--danger, #b91c1c);\">${message}</div>`;
        }
    }

    // Store document table instance for view mode switching
    let systemDocumentTable = null;
    
    async function loadDocuments(systemId, viewMode = null) {
        // Find the documents collapsible section
        const documentsSection = document.querySelector('#documents .collapsible-body');
        if (!documentsSection) {
            console.warn('Documents section not found');
            return;
        }

        // Create container for document table
        const containerId = 'systemDocumentsTableContainer';
        documentsSection.innerHTML = `<div id="${containerId}"></div>`;

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                systemDocumentTable = new DocumentTableComponent({
                    facetType: 'system',
                    facetId: systemId,
                    container: `#${containerId}`,
                    canEdit: false, // Read-only in view mode
                    viewMode: viewMode // Pass view mode to filter pending documents
                });
            } catch (error) {
                console.error('Error initializing document table:', error);
                documentsSection.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">' + (window.I18n?.t('message.failedToLoadDocuments') || 'Failed to load documents.') + '</div>';
            }
        } else {
            console.error('DocumentTableComponent not found');
            documentsSection.innerHTML = '<div class="empty">Document component not available.</div>';
        }
    }

    async function loadDataContentSummary(systemId, mountEl, viewMode = 'original') {
        const sT = (k) => (window.I18n && window.I18n.t(k)) || k;
        try {
            let list = await window.BUDG_API_SERVICE.getSystemDataContent(systemId, viewMode === 'changes' ? 'changes' : null);
            if (list && list.data) list = list.data;
            const rows = Array.isArray(list) ? list : [];

            const rowsHtml = rows.map(r => {
                const aliasHtml = (Array.isArray(r.aliasNames) && r.aliasNames.length)
                    ? r.aliasNames.map(a => `<span class="view-badge">${escapeHtml(a)}</span>`).join(' ')
                    : '<span class="empty">-</span>';
                const glossaryLink = r.glossaryId != null
                    ? `<a href="/view/glossary/${encodeURIComponent(r.glossaryId)}">${escapeHtml(r.glossary || '')}</a>`
                    : escapeHtml(r.glossary || '');
                const typeBadge = r.glossaryType ? `<span class="view-badge" style="background: var(--primary-50,#eef2ff); color: var(--primary-700,#4338ca); border-color: var(--primary-100,#e0e7ff);">${escapeHtml(r.glossaryType)}</span>` : '<span class="empty">-</span>';
                return `
                    <tr>
                        <td>${escapeHtml(r.relationshipType || '')}</td>
                        <td>${glossaryLink}</td>
                        <td>${typeBadge}</td>
                        <td>${escapeHtml(r.definition || '')}</td>
                        <td>${escapeHtml(r.relationshipStatus || '')}</td>
                        <td>${aliasHtml}</td>
                    </tr>
                `;
            }).join('');

            const n = rows.length;
            const footerText = n === 0 ? sT('system.dataContentSummary.zeroRecords') : (n === 1 ? sT('system.dataContentSummary.recordOne') : (sT('system.dataContentSummary.recordCount') || '{count} records').replace('{count}', n));

            mountEl.innerHTML = `
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><i class="fa-solid fa-link"></i> ${sT('system.dataContentSummary.relationshipType')}</th>
                                <th><i class="fa-solid fa-book"></i> ${sT('system.dataContentSummary.glossary')}</th>
                                <th><i class="fa-solid fa-tag"></i> ${sT('system.dataContentSummary.glossaryType')}</th>
                                <th><i class="fa-solid fa-align-left"></i> ${sT('system.dataContentSummary.definition')}</th>
                                <th><i class="fa-regular fa-circle-dot"></i> ${sT('system.dataContentSummary.relationshipStatus')}</th>
                                <th><i class="fa-solid fa-tags"></i> ${sT('system.dataContentSummary.aliasName')}</th>
                            </tr>
                        </thead>
                        <tbody>${rowsHtml}</tbody>
                    </table>
                    <div class="table-footer">${footerText}</div>
                </div>
            `;

            const editBtn = document.getElementById('dcsEditBtn');
            editBtn?.addEventListener('click', () => enterDcsEditMode(systemId, rows));
        } catch (e) {
            const failMsg = (window.I18n && window.I18n.t('system.dataContentSummary.failedToLoad')) || 'Failed to load.';
            mountEl.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">' + failMsg + '</div>';
        }
    }

    async function loadInterfaces(systemId, viewMode = 'original') {
        const container = document.getElementById('systemInterfacesContainer');
        if (!container) return;
        if (systemId == null || String(systemId).trim() === '' || String(systemId).toLowerCase() === 'null') {
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">Invalid system id. Use a URL like /view/system/123 or add a numeric <code>id</code> query parameter.</div>';
            return;
        }
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading...</div>';
        try {
            let list = await window.BUDG_API_SERVICE.getSystemInterfaces(systemId, viewMode === 'changes' ? 'changes' : null);
            if (list && list.data) list = list.data;
            let rows = Array.isArray(list) ? list : [];

            // Get current system data to compare
            const currentSystem = await window.BUDG_API_SERVICE.getSystemById(systemId, viewMode === 'changes' ? 'changes' : null);
            const currentSystemName = currentSystem?.name || '';

            // Filter interfaces to show only those where current system is FROM or TO
            // This ensures the table shows only interfaces directly related to the current system
            const filterInterfacesForCurrentSystem = () => {
                // Keep only interfaces where current system is the source (from) or target (to)
                rows = rows.filter(r => {
                    const fromMatches = String(r.fromId || r.sourceSystemId || '') === String(systemId) ||
                                       (r.from || r.fromSystem || '').toLowerCase() === currentSystemName.toLowerCase();
                    const toMatches = String(r.toId || r.targetSystemId || '') === String(systemId) ||
                                      (r.to || r.toSystem || '').toLowerCase() === currentSystemName.toLowerCase();
                    return fromMatches || toMatches;
                });
            };

            // Check if there are any data attribute links (data flow or interfaces with attributes)
            const hasDataAttributeLinks = async () => {
                try {
                    // Check data flow outside interfaces
                    const dataFlowData = await window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId);
                    const hasDataFlow = Array.isArray(dataFlowData?.data) ? dataFlowData.data.length > 0 : 
                                      (Array.isArray(dataFlowData) ? dataFlowData.length > 0 : false);
                    
                    // Check if any interface has data attributes > 0
                    const hasInterfaceAttributes = rows.some(r => (r.dataAttributes || 0) > 0);
                    
                    return hasDataFlow || hasInterfaceAttributes;
                } catch (error) {
                    console.warn('[INTERFACE-MAP] Error checking data attribute links:', error);
                    return false;
                }
            };

            // Function to render interfaces table
            const renderInterfacesTable = (interfacesRows, systemName) => {
                const rowsHtml = interfacesRows.map(r => {
                    // Determine direction indicator
                    const isOutgoing = r.from === systemName;
                    const directionIcon = isOutgoing
                        ? '<i class="fas fa-arrow-right" style="color: var(--success, #059669);" title="Outgoing"></i>'
                        : '<i class="fas fa-arrow-left" style="color: var(--info, #0284c7);" title="Incoming"></i>';

                    // Determine if this interface has data attributes (solid line) or not (dotted line)
                    const hasAttributes = (r.dataAttributes || 0) > 0;
                    const lineTypeIndicator = hasAttributes 
                        ? '<span class="line-type-indicator solid" title="Has attribute lineage (solid line)">●</span>'
                        : '<span class="line-type-indicator dotted" title="Interface only (dotted line)">○</span>';

                    const fromId = r.fromId ?? r.sourceSystemId;
                    const toId = r.toId ?? r.targetSystemId;
                    const fromCell = fromId
                        ? `<a href="/view/system/${encodeURIComponent(fromId)}" class="relationship-link" title="View system">${escapeHtml(r.from || '')}</a>`
                        : escapeHtml(r.from || '');
                    const toCell = toId
                        ? `<a href="/view/system/${encodeURIComponent(toId)}" class="relationship-link" title="View system">${escapeHtml(r.to || '')}</a>`
                        : escapeHtml(r.to || '');
                    const dataAttrsCell = r.id
                        ? `<a href="/view/system-interface/${encodeURIComponent(r.id)}?tab=data&subtab=data-within" class="relationship-link" title="View data within this interface">${escapeHtml(String(r.dataAttributes ?? 0))}</a>`
                        : escapeHtml(String(r.dataAttributes ?? 0));

                    return `
                    <tr data-interface-id="${r.id || ''}" data-from-id="${r.fromId || ''}" data-to-id="${r.toId || ''}">
                        <td>${directionIcon} ${fromCell}</td>
                        <td>${toCell}</td>
                        <td>
                            ${lineTypeIndicator}
                            <a href="/view/system-interface/${encodeURIComponent(r.id || '')}">${escapeHtml(r.name || '')}</a>
                        </td>
                        <td>${escapeHtml(r.description || '')}</td>
                        <td>${dataAttrsCell}</td>
                        <td>${escapeHtml(r.automation || 'Unknown')}</td>
                        <td>${escapeHtml(r.frequency || '')}</td>
                        <td>${escapeHtml(r.syncControl || '')}</td>
                        <td>${escapeHtml(r.transferMethod || '')}</td>
                        <td>${escapeHtml(r.transferFormat || '')}</td>
                    </tr>
                `;
                }).join('');

                return rowsHtml;
            };

            const rowsHtml = renderInterfacesTable(rows, currentSystemName);
            container.innerHTML = `
                <!-- MAP Section -->
                <div class="map-section" id="interfaceMapSection" style="grid-column:1/-1; margin-bottom: 1.5rem;">
                    <div class="map-section-header">
                        <div class="map-section-title">MAP</div>
                        <button type="button" class="map-collapse-btn" id="interfaceMapCollapseBtn" title="Collapse/Expand">
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                    <!-- Map Toolbar -->
                    <div class="interface-map-toolbar">
                        <!-- Map Type -->
                        <div class="map-control-group">
                            <label>Map type:</label>
                            <select id="interfaceMapTypeSelect" class="map-select">
                                <option value="system-lineage" selected>System Lineage</option>
                                <option value="dataset-lineage">Dataset Lineage</option>
                            </select>
                        </div>
                        
                        <!-- Layout -->
                        <div class="map-control-group">
                            <label>Layout:</label>
                            <div class="map-layout-controls">
                                ${typeof window.SharedMapLayoutRichControlsHtml === 'function' ? window.SharedMapLayoutRichControlsHtml('interfaceMap') : ''}
                            </div>
                        </div>
                        
                        <div class="map-control-group map-hops-group" id="interfaceMapHopsGroup">
                            <label>Hops:</label>
                            <input type="number" id="interfaceMapHopsCount" class="map-hops-input" min="1" max="99" value="15" title="Upstream/downstream lineage depth (1-99, recommend 15)">
                        </div>
                        
                        <!-- Overlay -->
                        <div class="map-control-group">
                            <label>Overlay:</label>
                            <div class="map-overlay-controls">
                                <div class="map-overlay-dropdown">
                                    <button type="button" class="map-select-btn" id="interfaceMapOverlayBtn">
                                        <span>None</span>
                                        <i class="fas fa-chevron-down"></i>
                                    </button>
                                    <!-- System Lineage Overlay Menu -->
                                    <div class="map-overlay-menu" id="interfaceMapOverlayMenu" data-map-type="system-lineage">
                                        <div class="overlay-menu-grid">
                                            <div class="overlay-menu-column">
                                                <div class="overlay-menu-header">Data</div>
                                                <div class="overlay-menu-item" data-overlay="description">
                                                    <i class="fas fa-info-circle"></i> Description
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="glossary">
                                                    <i class="fas fa-book"></i> Glossary
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="datasets">
                                                    <i class="fas fa-layer-group"></i> Data Sets
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="attributes">
                                                    <i class="fas fa-th"></i> Attributes
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="linking-attributes">
                                                    <i class="fas fa-th"></i> Linking Attributes
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="data-quality">
                                                    <i class="fas fa-bullseye"></i> Data Quality
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="data-privacy">
                                                    <i class="fas fa-lock"></i> Data Privacy
                                                </div>
                                            </div>
                                            <div class="overlay-menu-column">
                                                <div class="overlay-menu-header">Business</div>
                                                <div class="overlay-menu-item" data-overlay="stakeholders">
                                                    <i class="fas fa-users"></i> Stakeholders
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="processes">
                                                    <i class="fas fa-play"></i> Processes
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="projects">
                                                    <i class="fas fa-project-diagram"></i> Projects
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="policies">
                                                    <i class="fas fa-file-alt"></i> Policies
                                                </div>
                                            </div>
                                            <div class="overlay-menu-column">
                                                <div class="overlay-menu-header">Organizational</div>
                                                <div class="overlay-menu-item" data-overlay="business-area">
                                                    <i class="fas fa-briefcase"></i> Business Area
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="products">
                                                    <i class="fas fa-tag"></i> Products
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="legal-entities">
                                                    <i class="fas fa-landmark"></i> Legal Entities
                                                </div>
                                            </div>
                                            <div class="overlay-menu-column">
                                                <div class="overlay-menu-header">Regulatory</div>
                                                <div class="overlay-menu-item" data-overlay="geography">
                                                    <i class="fas fa-globe"></i> Geography
                                                </div>
                                            </div>
                                        </div>
                                        <div class="overlay-menu-footer">
                                            <button type="button" class="overlay-clear-btn" id="clearOverlaysBtn">Clear Overlays</button>
                                        </div>
                                    </div>
                                    <!-- Dataset Lineage Overlay Menu (Dataset-specific overlays) -->
                                    <div class="map-overlay-menu" id="datasetMapOverlayMenu" data-map-type="dataset-lineage">
                                        <div class="overlay-menu-grid dataset-overlay-grid">
                                            <div class="overlay-menu-column">
                                                <div class="overlay-menu-header">Data</div>
                                                <div class="overlay-menu-item" data-overlay="description">
                                                    <i class="fas fa-info-circle"></i> Definition
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="glossary">
                                                    <i class="fas fa-book"></i> Glossary
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="attributes">
                                                    <i class="fas fa-th"></i> Attributes
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="linking-attributes">
                                                    <i class="fas fa-th"></i> Linking Attributes
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="data-quality">
                                                    <i class="fas fa-bullseye"></i> Data Quality
                                                </div>
                                            </div>
                                            <div class="overlay-menu-column">
                                                <div class="overlay-menu-header">Business</div>
                                                <div class="overlay-menu-item" data-overlay="stakeholders">
                                                    <i class="fas fa-users"></i> Stakeholders
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="processes">
                                                    <i class="fas fa-play-circle"></i> Processes
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="projects">
                                                    <i class="fas fa-tasks"></i> Projects
                                                </div>
                                                <div class="overlay-menu-item" data-overlay="policies">
                                                    <i class="fas fa-file-alt"></i> Policies
                                                </div>
                                            </div>
                                        </div>
                                        <div class="overlay-menu-footer">
                                            <button type="button" class="overlay-clear-btn dataset-clear-overlays-btn">Clear Overlays</button>
                                        </div>
                                    </div>
                                </div>
                                <button type="button" class="map-toolbar-btn-sm" id="interfaceMapOverlayGrid" title="Overlay fields as columns">
                                    <i class="fas fa-th"></i>
                                    <i class="fas fa-chevron-down" style="font-size: 8px; margin-left: 2px;"></i>
                                </button>
                                <div class="map-overlay-columns-menu map-filter-menu" id="interfaceMapOverlayColumnsMenu" style="display: none;">
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">OVERLAY FIELDS AS COLUMNS</div>
                                        <div id="interfaceMapOverlayColumnsOptions" class="map-filter-options-container">
                                            <!-- Populated by JS when grid is clicked -->
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Filters -->
                        <div class="map-control-group">
                            <label>Filters:</label>
                            <div class="map-filter-dropdown">
                                <button type="button" class="map-select-btn" id="interfaceMapFilterBtn">
                                    <span id="interfaceMapFilterBtnText">All selected (5)</span>
                                    <i class="fas fa-chevron-down"></i>
                                </button>
                                <!-- System Lineage Filter Menu -->
                                <div class="map-filter-menu" id="interfaceMapFilterMenu" data-map-type="system-lineage">
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">LINKS</div>
                                        <div class="map-filter-option">
                                            <input type="checkbox" id="filterSystemInterfaces" data-filter-link="systemInterfaces" checked>
                                            <label for="filterSystemInterfaces">System Interfaces</label>
                                        </div>
                                        <div class="map-filter-option" id="filterDataAttributeLinksOption">
                                            <input type="checkbox" id="filterDataAttributeLinks" data-filter-link="dataAttributeLinks" checked>
                                            <label for="filterDataAttributeLinks">Data Attribute Links</label>
                                        </div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category" id="filterClassificationCategory">
                                        <div class="map-filter-category-header">CLASSIFICATION</div>
                                        <div id="filterClassificationOptions">
                                            <!-- Dynamic options will be added here -->
                                        </div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category" id="filterTypeCategory">
                                        <div class="map-filter-category-header">TYPE</div>
                                        <div id="filterTypeOptions">
                                            <!-- Dynamic options will be added here -->
                                        </div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category" id="filterLifecycleCategory">
                                        <div class="map-filter-category-header">LIFECYCLE</div>
                                        <div id="filterLifecycleOptions">
                                            <!-- Dynamic options will be added here -->
                                        </div>
                                    </div>
                                </div>
                                <!-- Dataset Lineage Filter Menu -->
                                <div class="map-filter-menu" id="datasetMapFilterMenu" data-map-type="dataset-lineage">
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">TYPE</div>
                                        <div id="datasetFilterTypeOptions">
                                            <!-- Dynamic options will be added here -->
                                        </div>
                                    </div>
                                    <div class="map-filter-separator"></div>
                                    <div class="map-filter-category">
                                        <div class="map-filter-category-header">LIFECYCLE</div>
                                        <div id="datasetFilterLifecycleOptions">
                                            <!-- Dynamic options will be added here -->
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Toolbar Buttons -->
                        <div class="map-toolbar-buttons">
                            <button type="button" class="map-toolbar-btn" id="interfaceMapToggleLabels" title="Toggle interface labels">
                                <i class="fas fa-exchange-alt"></i>
                            </button>
                            <div class="map-toolbar-separator"></div>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapZoomIn" title="Zoom in">
                                <i class="fas fa-search-plus"></i>
                            </button>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapZoomOut" title="Zoom out">
                                <i class="fas fa-search-minus"></i>
                            </button>
                            <div class="map-toolbar-separator"></div>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapRedraw" title="Redraw">
                                <i class="fas fa-sync-alt"></i>
                            </button>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapReset" title="Reset">
                                <i class="fas fa-undo"></i>
                            </button>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapExport" title="Export as PNG">
                                <i class="fas fa-save"></i>
                            </button>
                            <div class="map-toolbar-separator"></div>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapNavigator" title="Map navigator">
                                <i class="fas fa-eye"></i>
                            </button>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapFullscreen" title="Fullscreen">
                                <i class="fas fa-external-link-alt"></i>
                            </button>
                            <button type="button" class="map-toolbar-btn" id="interfaceMapLegend" title="Open the Legend" aria-label="Open the Legend – refer to the legend to identify the symbols used in the map (Insight Maps Palette)">
                                <i class="fas fa-list-ul"></i>
                            </button>
                        </div>
                    </div>
                    <!-- Map Body -->
                    <div class="interface-map-body">
                        <div class="interface-map-canvas" id="interfaceMapCanvas">
                            <div class="interface-map-loading" data-interface-map-loading style="display:none;">
                                <i class="fas fa-spinner fa-spin"></i>
                                <span>Building lineage map...</span>
                            </div>
                        </div>
                        <div class="interface-map-side-panel" id="interfaceMapSidePanel" style="display:none;">
                            <div class="map-side-panel-section">
                                <h4><i class="fas fa-info-circle"></i> Selection</h4>
                                <div class="selection-placeholder" data-interface-map-placeholder>
                                    Select a node to see its details.
                                </div>
                                <div class="selection-info" data-interface-map-details style="display:none;"></div>
                            </div>
                            <div class="map-side-panel-section">
                                <h4 class="map-legend-palette-title"><i class="fas fa-layer-group"></i> Insight Maps Palette</h4>
                                <div data-interface-map-legend></div>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- System Interfaces Table -->
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title" style="position:relative;">
                        <span>SYSTEM INTERFACES</span>
                        <span style="position:absolute;right:0.75rem;top:50%;transform:translateY(-50%);">${gridSettingsHtml('interfaces')}</span>
                    </div>
                    <div class="interfaces-table-wrapper">
                        <table class="interfaces-table">
                            <thead>
                                <tr>
                                    <th><i class="fas fa-project-diagram"></i> From</th>
                                    <th><i class="fas fa-bullseye"></i> To</th>
                                    <th><i class="fas fa-tag"></i> Name</th>
                                    <th><i class="fas fa-align-left"></i> Description</th>
                                    <th><i class="fas fa-database"></i> Data Attributes</th>
                                    <th><i class="fas fa-robot"></i> Automation</th>
                                    <th><i class="fas fa-clock"></i> Frequency</th>
                                    <th><i class="fas fa-sync"></i> Synchronisation Control</th>
                                    <th><i class="fas fa-exchange-alt"></i> Transfer Method</th>
                                    <th><i class="fas fa-file-code"></i> Transfer Format</th>
                                </tr>
                            </thead>
                            <tbody>${rowsHtml}</tbody>
                        </table>
                        <div class="table-footer">${rows.length} record${rows.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
                <div id="systemDataFlowOutsideInterfacesContainer" class="view-grid"></div>
            `;

            initGridSettings(container);

            // Check if there are data attribute links and update filter label
            const checkAndUpdateFilters = async () => {
                try {
                    // Both filter options (System Interfaces + Data Attribute Links) are always visible
                    // Just update the filter label count
                    if (typeof updateFilterLabel === 'function') {
                        const mapSection = container.querySelector('#interfaceMapSection');
                        updateFilterLabel(mapSection || container);
                    }
                } catch (error) {
                    console.warn('[INTERFACE-MAP] Error updating filters:', error);
                }
            };

            // Initialize the map for this rendered container instance
            initializeInterfaceMap(systemId, container);

            // Check and update filters after a short delay to allow map to load
            setTimeout(() => {
                checkAndUpdateFilters();
            }, 1000);

            // Function to update interfaces table
            const updateInterfacesTable = (interfacesRows, systemName, containerEl) => {
                const tableBody = containerEl.querySelector('.interfaces-table tbody');
                const tableFooter = containerEl.querySelector('.table-footer');
                
                if (tableBody) {
                    const newRowsHtml = renderInterfacesTable(interfacesRows, systemName);
                    tableBody.innerHTML = newRowsHtml;
                }
                
                if (tableFooter) {
                    tableFooter.textContent = `${interfacesRows.length} record${interfacesRows.length !== 1 ? 's' : ''}`;
                }
            };

            // Filter interfaces to show only those related to current system
            filterInterfacesForCurrentSystem();

            // Load Data Flow Outside Interfaces section
            if (window.loadDataFlowOutsideInterfaces) {
                window.loadDataFlowOutsideInterfaces(systemId).catch(error => {
                    console.error('Failed to load data flow outside interfaces:', error);
                });
            }
        } catch (e) {
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">Failed to load interfaces.</div>';
        }
    }

    // Initialize the interface map with controls
    function initializeInterfaceMap(systemId, containerRoot) {
        const mapRoot = (containerRoot && containerRoot.querySelector)
            ? (containerRoot.querySelector('#interfaceMapSection') || containerRoot)
            : document;
        const byId = (id) => mapRoot.querySelector ? mapRoot.querySelector(`#${id}`) : document.getElementById(id);

        // Setup toolbar controls first
        const mapTypeSelect = byId('interfaceMapTypeSelect');
        
        // Initialize the map - ensure it starts with system-lineage
        if (window.SystemInterfacesMap) {
            // Update the dropdown to reflect system-lineage
            if (mapTypeSelect) {
                mapTypeSelect.value = 'system-lineage';
            }
            // Initialize the map (this will set the systemId, map type, and load data)
            // The init() function automatically sets map type to 'system-lineage' for interfaceMapCanvas
            const canvasEl = byId('interfaceMapCanvas');
            window.SystemInterfacesMap.init(systemId, canvasEl || '#interfaceMapCanvas');
            // Note: No need to call setMapType() here - init() already handles it
        }
        const filterBtn = byId('interfaceMapFilterBtn');
        const filterMenu = byId('interfaceMapFilterMenu');
        const filterSystemInterfaces = byId('filterSystemInterfaces');
        const filterDataAttributeLinks = byId('filterDataAttributeLinks');

        // Overlay and Filter menus
        const overlayBtn = byId('interfaceMapOverlayBtn');
        const systemOverlayMenu = byId('interfaceMapOverlayMenu');
        const datasetOverlayMenu = byId('datasetMapOverlayMenu');
        const systemFilterMenu = byId('interfaceMapFilterMenu');
        const datasetFilterMenu = byId('datasetMapFilterMenu');
        const clearOverlaysBtn = byId('clearOverlaysBtn');
        const datasetClearOverlaysBtn = mapRoot.querySelector('.dataset-clear-overlays-btn');

        // Track current map type for menu switching
        let currentMapType = 'system-lineage';

        // LINKS filters: delegated change (works with data-filter-link; avoids missed direct listeners)
        mapRoot.addEventListener('change', function (e) {
            const t = e.target;
            if (!t || t.type !== 'checkbox') return;
            const dl = t.getAttribute('data-filter-link');
            if (dl !== 'systemInterfaces' && dl !== 'dataAttributeLinks') return;
            updateFilterLabel(mapRoot);
            if (window.SystemInterfacesMap && typeof window.SystemInterfacesMap.setFilter === 'function') {
                window.SystemInterfacesMap.setFilter(dl, t.checked);
            }
        });
        
        // Function to get active overlay menu based on map type
        const getActiveOverlayMenu = () => {
            return currentMapType === 'dataset-lineage' ? datasetOverlayMenu : systemOverlayMenu;
        };

        // Function to get active filter menu based on map type
        const getActiveFilterMenu = () => {
            return currentMapType === 'dataset-lineage' ? datasetFilterMenu : systemFilterMenu;
        };

        // Function to switch menus based on map type
        const switchMenus = (mapType) => {
            console.log('[INTERFACE-MAP] Switching menus to:', mapType);
            currentMapType = mapType;
            
            // Show overlay menu for current map type, hide the other (so Dataset Lineage overlay is visible in interface tab)
            if (systemOverlayMenu) {
                systemOverlayMenu.style.display = mapType === 'dataset-lineage' ? 'none' : '';
            }
            if (datasetOverlayMenu) {
                datasetOverlayMenu.style.display = mapType === 'dataset-lineage' ? '' : 'none';
            }
            // Show filter menu for current map type, hide the other
            if (systemFilterMenu) {
                systemFilterMenu.style.display = mapType === 'dataset-lineage' ? 'none' : '';
            }
            if (datasetFilterMenu) {
                datasetFilterMenu.style.display = mapType === 'dataset-lineage' ? '' : 'none';
            }
            
            // Reset overlay button text
            if (overlayBtn) {
                overlayBtn.querySelector('span').textContent = 'None';
            }
            // Clear active overlays
            mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
            // Hide any open menus
            if (systemOverlayMenu) systemOverlayMenu.classList.remove('open');
            if (datasetOverlayMenu) datasetOverlayMenu.classList.remove('open');
            if (systemFilterMenu) systemFilterMenu.classList.remove('open');
            if (datasetFilterMenu) datasetFilterMenu.classList.remove('open');
            
            // Clear overlay on map
            if (window.SystemInterfacesMap) {
                window.SystemInterfacesMap.setOverlay('none');
            }
            
            // Update filter label based on map type
            updateFilterLabelForMapType(mapType);
        };

        // Update filter label for specific map type
        const updateFilterLabelForMapType = (mapType) => {
            const filterBtnText = byId('interfaceMapFilterBtnText');
            if (!filterBtnText) return;
            
            const activeFilterMenu = mapType === 'dataset-lineage' ? datasetFilterMenu : systemFilterMenu;
            if (!activeFilterMenu) return;
            
            const checkboxes = activeFilterMenu.querySelectorAll('input[type="checkbox"]');
            const checkedCount = Array.from(checkboxes).filter(c => c.checked && c.closest('.map-filter-option')?.style.display !== 'none').length;
            const totalCount = Array.from(checkboxes).filter(c => c.closest('.map-filter-option')?.style.display !== 'none').length;
            
            if (totalCount === 0) {
                filterBtnText.textContent = 'No filters';
            } else if (checkedCount === totalCount) {
                filterBtnText.textContent = `All selected (${totalCount})`;
            } else {
                filterBtnText.textContent = `${checkedCount} of ${totalCount}`;
            }
        };

        // Map type change
        if (mapTypeSelect) {
            mapTypeSelect.addEventListener('change', (e) => {
                const mapType = e.target.value;
                
                // Switch menus
                switchMenus(mapType);
                
                // Update map
                if (window.SystemInterfacesMap) {
                    window.SystemInterfacesMap.setMapType(mapType);
                }
            });
        }

        // Hops Count (1-99, default 15)
        const hopsInput = byId('interfaceMapHopsCount');
        if (hopsInput) {
            hopsInput.addEventListener('change', (e) => {
                let val = parseInt(e.target.value, 10);
                if (isNaN(val) || val < 1) val = 1;
                if (val > 99) val = 99;
                e.target.value = val;
                if (window.SystemInterfacesMap && typeof window.SystemInterfacesMap.setHopsCount === 'function') {
                    window.SystemInterfacesMap.setHopsCount(val);
                }
            });
        }

        // Overlay dropdown click handler
        if (overlayBtn) {
            overlayBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const activeOverlayMenu = getActiveOverlayMenu();
                if (activeOverlayMenu) {
                    activeOverlayMenu.classList.toggle('open');
                }
                // Close filter menus if open
                if (systemFilterMenu) systemFilterMenu.classList.remove('open');
                if (datasetFilterMenu) datasetFilterMenu.classList.remove('open');
            });
        }

        // Single delegated handler for overlay menu items (works for both system and dataset menus)
        const overlayDropdown = overlayBtn ? overlayBtn.closest('.map-overlay-dropdown') : null;
        if (overlayDropdown) {
            overlayDropdown.addEventListener('click', (e) => {
                const item = e.target.closest('.overlay-menu-item');
                if (!item) return;
                e.preventDefault();
                e.stopPropagation();
                const overlayMenu = item.closest('.map-overlay-menu');
                if (!overlayMenu) return;
                // Only handle if this menu is the active one for current map type
                if (overlayMenu !== getActiveOverlayMenu()) return;
                const overlayType = item.getAttribute('data-overlay');
                if (!overlayType) return;
                console.log('[INTERFACE-MAP] Overlay item clicked:', overlayType);
                const wasActive = item.classList.contains('active');
                overlayMenu.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                if (!wasActive) {
                    item.classList.add('active');
                    if (overlayBtn) overlayBtn.querySelector('span').textContent = item.textContent.trim();
                    if (window.SystemInterfacesMap) {
                        window.SystemInterfacesMap.setOverlay(overlayType);
                    } else {
                        console.error('[INTERFACE-MAP] SystemInterfacesMap not found!');
                    }
                } else {
                    if (overlayBtn) overlayBtn.querySelector('span').textContent = 'None';
                    if (window.SystemInterfacesMap) window.SystemInterfacesMap.setOverlay('none');
                }
                overlayMenu.classList.remove('open');
            });
        }
        
        // Ensure correct overlay/filter menus are visible for initial map type (system-lineage)
        switchMenus(currentMapType);
        
        console.log('[INTERFACE-MAP] Overlay handlers setup complete. systemOverlayMenu:', !!systemOverlayMenu, 'datasetOverlayMenu:', !!datasetOverlayMenu);

        // Clear overlays button (system)
        if (clearOverlaysBtn) {
            clearOverlaysBtn.addEventListener('click', () => {
                mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                if (overlayBtn) overlayBtn.querySelector('span').textContent = 'None';
                if (window.SystemInterfacesMap) {
                    window.SystemInterfacesMap.setOverlay('none');
                }
                if (systemOverlayMenu) systemOverlayMenu.classList.remove('open');
            });
        }

        // Clear overlays button (dataset)
        if (datasetClearOverlaysBtn) {
            datasetClearOverlaysBtn.addEventListener('click', () => {
                mapRoot.querySelectorAll('.overlay-menu-item').forEach(i => i.classList.remove('active'));
                if (overlayBtn) overlayBtn.querySelector('span').textContent = 'None';
                if (window.SystemInterfacesMap) {
                    window.SystemInterfacesMap.setOverlay('none');
                }
                if (datasetOverlayMenu) datasetOverlayMenu.classList.remove('open');
            });
        }

        // Filter dropdown toggle
        if (filterBtn) {
            filterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const activeFilterMenu = getActiveFilterMenu();
                if (activeFilterMenu) {
                    activeFilterMenu.classList.toggle('open');
                }
                // Close overlay menus if open
                if (systemOverlayMenu) systemOverlayMenu.classList.remove('open');
                if (datasetOverlayMenu) datasetOverlayMenu.classList.remove('open');
            });
        }

        // Listen for dynamic filter options from map
        window.addEventListener('systemMapFilterOptionsUpdated', (event) => {
            const { classifications, types, lifecycles } = event.detail || {};
            
            // Update Classification filter options
            const classificationContainer = byId('filterClassificationOptions');
            if (classificationContainer && classifications && classifications.length > 0) {
                classificationContainer.innerHTML = classifications.map((cls, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="filterClassification_${idx}" data-filter-type="classification" data-filter-value="${cls}" checked>
                        <label for="filterClassification_${idx}">${cls}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                classificationContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyNodeFilters();
                        updateFilterLabel(mapRoot);
                    });
                });
            }
            
            // Update Type filter options
            const typeContainer = byId('filterTypeOptions');
            if (typeContainer && types && types.length > 0) {
                typeContainer.innerHTML = types.map((type, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="filterType_${idx}" data-filter-type="type" data-filter-value="${type}" checked>
                        <label for="filterType_${idx}">${type}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                typeContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyNodeFilters();
                        updateFilterLabel(mapRoot);
                    });
                });
            }
            
            // Update Lifecycle filter options
            const lifecycleContainer = byId('filterLifecycleOptions');
            if (lifecycleContainer && lifecycles && lifecycles.length > 0) {
                lifecycleContainer.innerHTML = lifecycles.map((lc, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="filterLifecycle_${idx}" data-filter-type="lifecycle" data-filter-value="${lc}" checked>
                        <label for="filterLifecycle_${idx}">${lc}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                lifecycleContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyNodeFilters();
                        updateFilterLabel(mapRoot);
                    });
                });
            }
            
            // Update filter label count
            updateFilterLabel(mapRoot);
        });

        // Listen for dynamic filter options from dataset lineage map
        window.addEventListener('datasetMapFilterOptionsUpdated', (event) => {
            const { types, lifecycles } = event.detail || {};
            
            // Update Type filter options for dataset lineage
            const typeContainer = byId('datasetFilterTypeOptions');
            if (typeContainer && types && types.length > 0) {
                typeContainer.innerHTML = types.map((type, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="datasetFilterType_${idx}" data-filter-type="type" data-filter-value="${type}" checked>
                        <label for="datasetFilterType_${idx}">${type}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                typeContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyDatasetNodeFilters();
                        updateFilterLabelForMapType('dataset-lineage');
                    });
                });
            } else if (typeContainer) {
                typeContainer.innerHTML = '<div class="map-filter-option" style="color: var(--text-muted, #6b7280); font-size: 0.75rem; padding: 0.25rem 0.75rem;">No types available</div>';
            }
            
            // Update Lifecycle filter options for dataset lineage
            const lifecycleContainer = byId('datasetFilterLifecycleOptions');
            if (lifecycleContainer && lifecycles && lifecycles.length > 0) {
                lifecycleContainer.innerHTML = lifecycles.map((lc, idx) => `
                    <div class="map-filter-option">
                        <input type="checkbox" id="datasetFilterLifecycle_${idx}" data-filter-type="lifecycle" data-filter-value="${lc}" checked>
                        <label for="datasetFilterLifecycle_${idx}">${lc}</label>
                    </div>
                `).join('');
                
                // Add event listeners to new checkboxes
                lifecycleContainer.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => {
                        applyDatasetNodeFilters();
                        updateFilterLabelForMapType('dataset-lineage');
                    });
                });
            } else if (lifecycleContainer) {
                lifecycleContainer.innerHTML = '<div class="map-filter-option" style="color: var(--text-muted, #6b7280); font-size: 0.75rem; padding: 0.25rem 0.75rem;">No lifecycles available</div>';
            }
            
            // Update filter label count
            updateFilterLabelForMapType('dataset-lineage');
        });

        // Apply node filters for dataset lineage based on type, lifecycle
        function applyDatasetNodeFilters() {
            const selectedTypes = [];
            const selectedLifecycles = [];
            
            mapRoot.querySelectorAll('#datasetFilterTypeOptions input:checked').forEach(cb => {
                selectedTypes.push(cb.dataset.filterValue);
            });
            mapRoot.querySelectorAll('#datasetFilterLifecycleOptions input:checked').forEach(cb => {
                selectedLifecycles.push(cb.dataset.filterValue);
            });
            
            if (window.SystemInterfacesMap) {
                window.SystemInterfacesMap.setDatasetNodeFilters({
                    types: selectedTypes,
                    lifecycles: selectedLifecycles
                });
            }
        }

        // Apply node filters based on classification, type, lifecycle
        function applyNodeFilters() {
            const selectedClassifications = [];
            const selectedTypes = [];
            const selectedLifecycles = [];
            
            mapRoot.querySelectorAll('#filterClassificationOptions input:checked').forEach(cb => {
                selectedClassifications.push(cb.dataset.filterValue);
            });
            mapRoot.querySelectorAll('#filterTypeOptions input:checked').forEach(cb => {
                selectedTypes.push(cb.dataset.filterValue);
            });
            mapRoot.querySelectorAll('#filterLifecycleOptions input:checked').forEach(cb => {
                selectedLifecycles.push(cb.dataset.filterValue);
            });
            
            if (window.SystemInterfacesMap) {
                window.SystemInterfacesMap.setNodeFilters({
                    classifications: selectedClassifications,
                    types: selectedTypes,
                    lifecycles: selectedLifecycles
                });
            }
        }

        // Close dropdowns when clicking outside
        document.addEventListener('click', (e) => {
            const activeFilterMenu = getActiveFilterMenu();
            const activeOverlayMenu = getActiveOverlayMenu();
            
            if (filterBtn && activeFilterMenu && !filterBtn.contains(e.target) && !activeFilterMenu.contains(e.target)) {
                activeFilterMenu.classList.remove('open');
            }
            if (overlayBtn && activeOverlayMenu && !overlayBtn.contains(e.target) && !activeOverlayMenu.contains(e.target)) {
                activeOverlayMenu.classList.remove('open');
            }
            const overlayColumnsMenu = byId('interfaceMapOverlayColumnsMenu');
            const overlayGridBtn = byId('interfaceMapOverlayGrid');
            if (overlayColumnsMenu && overlayGridBtn && overlayColumnsMenu.classList.contains('open') && !overlayColumnsMenu.contains(e.target) && !overlayGridBtn.contains(e.target)) {
                overlayColumnsMenu.classList.remove('open');
                overlayColumnsMenu.style.display = 'none';
                overlayGridBtn.classList.remove('active');
            }
        });

        // Filter checkboxes (LINKS use data-filter-link + delegation above)
        const filterCheckboxes = [
            'filterSystemType',
            'filterSystemLifecycle',
            'filterAllSystems'
        ];

        filterCheckboxes.forEach(id => {
            const checkbox = byId(id);
            if (checkbox) {
                checkbox.addEventListener('change', () => {
                    updateFilterLabel(mapRoot);
                    if (window.SystemInterfacesMap && window.SystemInterfacesMap.setFilter) {
                        const filterName = id.replace('filter', '').charAt(0).toLowerCase() + id.replace('filter', '').slice(1);
                        window.SystemInterfacesMap.setFilter(filterName, checkbox.checked);
                    }
                });
            }
        });

        // Toolbar buttons
        const zoomInBtn = byId('interfaceMapZoomIn');
        const zoomOutBtn = byId('interfaceMapZoomOut');
        const redrawBtn = byId('interfaceMapRedraw');
        const resetBtn = byId('interfaceMapReset');
        const exportBtn = byId('interfaceMapExport');
        const fullscreenBtn = byId('interfaceMapFullscreen');
        const legendBtn = byId('interfaceMapLegend');
        const toggleLabelsBtn = byId('interfaceMapToggleLabels');
        const navigatorBtn = byId('interfaceMapNavigator');

        if (zoomInBtn) {
            zoomInBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.zoomIn();
            });
        }

        if (zoomOutBtn) {
            zoomOutBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.zoomOut();
            });
        }

        if (redrawBtn) {
            redrawBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.redrawMap();
            });
        }

        if (resetBtn) {
            resetBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.resetMap();
            });
        }

        if (exportBtn) {
            exportBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.exportAsPng();
            });
        }

        if (fullscreenBtn) {
            fullscreenBtn.addEventListener('click', () => {
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.openFullscreen();
            });
        }

        if (legendBtn && typeof window.setupMapLegendDropdown === 'function') {
            window.setupMapLegendDropdown('interfaceMapLegend', 'interfaceMapLegendDropdown', function() {
                return window.SystemInterfacesMap && window.SystemInterfacesMap.getLegendHtml ? window.SystemInterfacesMap.getLegendHtml() : '';
            });
        } else if (legendBtn) {
            const sidePanel = byId('interfaceMapSidePanel');
            legendBtn.addEventListener('click', () => {
                if (sidePanel) {
                    const isVisible = sidePanel.style.display !== 'none';
                    sidePanel.style.display = isVisible ? 'none' : 'flex';
                    legendBtn.classList.toggle('active', !isVisible);
                }
            });
        }

        if (navigatorBtn) {
            navigatorBtn.addEventListener('click', () => {
                navigatorBtn.classList.toggle('active');
                if (window.SystemInterfacesMap && window.SystemInterfacesMap.toggleNavigator) {
                    window.SystemInterfacesMap.toggleNavigator();
                }
            });
        }

        if (toggleLabelsBtn) {
            let labelsVisible = true;
            toggleLabelsBtn.classList.add('active');
            toggleLabelsBtn.addEventListener('click', () => {
                labelsVisible = !labelsVisible;
                if (window.SystemInterfacesMap) window.SystemInterfacesMap.toggleInterfaceLabels(labelsVisible);
                toggleLabelsBtn.classList.toggle('active', labelsVisible);
            });
        }

        // Collapse button
        const collapseBtn = byId('interfaceMapCollapseBtn');
        const mapSection = byId('interfaceMapSection');

        if (collapseBtn && mapSection) {
            collapseBtn.addEventListener('click', () => {
                mapSection.classList.toggle('collapsed');
                const icon = collapseBtn.querySelector('i');
                if (mapSection.classList.contains('collapsed')) {
                    icon.className = 'fas fa-plus';
                    collapseBtn.title = 'Expand';
                } else {
                    icon.className = 'fas fa-minus';
                    collapseBtn.title = 'Collapse';
                    // Redraw map when expanding (in case it wasn't rendered properly)
                    if (window.SystemInterfacesMap) {
                        setTimeout(() => window.SystemInterfacesMap.redrawMap(), 100);
                    }
                }
            });
        }

        // Initialize spacing and edge-style dropdowns using SharedMapDropdowns
        if (typeof window.SharedMapDropdowns === 'function') {
            window.SharedMapDropdowns({
                mapId: 'interfaceMap',
                getNetwork: () => window.SystemInterfacesMap ? window.SystemInterfacesMap.cy : null,
                setLayout: (dir) => {
                    if (window.SystemInterfacesMap) window.SystemInterfacesMap.setLayout(dir);
                },
                getCanvas: () => mapRoot.querySelector('.interface-map-container, .map-network-canvas')
            });
        }

        const overlayGridBtn = byId('interfaceMapOverlayGrid');

        // Overlay grid button (toggle overlay panel columns)
        const overlayColumnsMenu = byId('interfaceMapOverlayColumnsMenu');
        if (overlayGridBtn && overlayColumnsMenu) {
            function populateOverlayColumnsMenu() {
                const overlayType = window.SystemInterfacesMap && window.SystemInterfacesMap.getState ? window.SystemInterfacesMap.getState().overlay : '';
                const container = byId('interfaceMapOverlayColumnsOptions');
                if (!container) return;
                container.innerHTML = '';
                if (!overlayType || overlayType === 'none') {
                    container.innerHTML = '<div class="map-overlay-columns-empty">Select an overlay first.</div>';
                    return;
                }
                const columns = window.OverlayColumns && window.OverlayColumns.getOverlayColumns(overlayType);
                const selectedIds = window.SystemInterfacesMap && window.SystemInterfacesMap.getOverlayColumns ? window.SystemInterfacesMap.getOverlayColumns(overlayType) : [];
                if (!columns || columns.length === 0) {
                    container.innerHTML = '<div class="map-overlay-columns-empty">No columns for this overlay.</div>';
                    return;
                }
                columns.forEach(col => {
                    const div = document.createElement('div');
                    div.className = 'map-filter-option';
                    const input = document.createElement('input');
                    input.type = 'checkbox';
                    input.id = 'overlayCol_' + overlayType + '_' + col.id;
                    input.checked = selectedIds.indexOf(col.id) !== -1;
                    input.dataset.columnId = col.id;
                    const label = document.createElement('label');
                    label.htmlFor = input.id;
                    label.textContent = col.label;
                    div.appendChild(input);
                    div.appendChild(label);
                    input.addEventListener('change', () => {
                        const checked = Array.from(container.querySelectorAll('input:checked')).map(i => i.dataset.columnId);
                        if (window.SystemInterfacesMap && window.SystemInterfacesMap.setOverlayColumns) {
                            window.SystemInterfacesMap.setOverlayColumns(overlayType, checked);
                        }
                    });
                    container.appendChild(div);
                });
            }
            overlayGridBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                const filterMenu = byId('interfaceMapFilterMenu');
                const systemOverlayMenu = byId('interfaceMapOverlayMenu');
                const datasetOverlayMenu = byId('datasetMapOverlayMenu');
                if (filterMenu) filterMenu.classList.remove('open');
                if (systemOverlayMenu) systemOverlayMenu.classList.remove('open');
                if (datasetOverlayMenu) datasetOverlayMenu.classList.remove('open');
                const isOpen = overlayColumnsMenu.classList.toggle('open');
                if (isOpen) {
                    overlayColumnsMenu.style.display = 'block';
                    populateOverlayColumnsMenu();
                    const rect = overlayGridBtn.getBoundingClientRect();
                    overlayColumnsMenu.style.position = 'fixed';
                    overlayColumnsMenu.style.left = rect.left + 'px';
                    overlayColumnsMenu.style.top = (rect.bottom + 4) + 'px';
                    overlayColumnsMenu.style.minWidth = '200px';
                } else {
                    overlayColumnsMenu.style.display = 'none';
                }
                overlayGridBtn.classList.toggle('active', isOpen);
            });
        }

        // Initialize filter label
        updateFilterLabel(mapRoot);
    }

    // Update filter label based on selections
    function updateFilterLabel(mapScope) {
        const scope = (mapScope && mapScope.querySelector) ? mapScope : document;
        const byId = (id) => scope.querySelector ? scope.querySelector(`#${id}`) : document.getElementById(id);
        const filterBtn = byId('interfaceMapFilterBtn');
        const filterBtnText = byId('interfaceMapFilterBtnText');
        
        if (filterBtn) {
            let checkedCount = 0;
            let totalCount = 0;

            // Helper function to check if element is visible
            const isElementVisible = (element) => {
                if (!element) return false;
                const style = window.getComputedStyle(element);
                return style.display !== 'none' && 
                       style.visibility !== 'hidden' && 
                       style.opacity !== '0' &&
                       !element.hasAttribute('hidden');
            };

            // Count LINKS checkboxes - only count visible ones
            const linksCheckboxes = ['filterSystemInterfaces', 'filterDataAttributeLinks'];
            linksCheckboxes.forEach(id => {
                const checkbox = byId(id);
                if (checkbox) {
                    const option = checkbox.closest('.map-filter-option');
                    if (isElementVisible(option)) {
                        totalCount++;
                        if (checkbox.checked) checkedCount++;
                    }
                }
            });

            // Count dynamic filter checkboxes (Classification, Type, Lifecycle)
            const dynamicContainers = [
                'filterClassificationOptions',
                'filterTypeOptions',
                'filterLifecycleOptions'
            ];
            
            dynamicContainers.forEach(containerId => {
                const container = byId(containerId);
                if (container) {
                    // Check if container's parent category is visible
                    const category = container.closest('.map-filter-category');
                    if (isElementVisible(category)) {
                        const checkboxes = container.querySelectorAll('input[type="checkbox"]');
                        checkboxes.forEach(checkbox => {
                            const option = checkbox.closest('.map-filter-option');
                            if (isElementVisible(option)) {
                                totalCount++;
                                if (checkbox.checked) checkedCount++;
                            }
                        });
                    }
                }
            });

            console.log('[FILTER-COUNT] Total checkboxes:', totalCount, 'Checked:', checkedCount);

            const label = filterBtnText || filterBtn.querySelector('span');
            if (label) {
                if (totalCount === 0) {
                    label.textContent = 'No filters';
                } else if (checkedCount === 0) {
                    label.textContent = 'None selected';
                } else if (checkedCount === totalCount) {
                    label.textContent = `All selected (${totalCount})`;
                } else {
                    label.textContent = `${checkedCount} of ${totalCount}`;
                }
            }
        }
    }

    async function loadSystemImpactData(id) {
        const container = document.getElementById('systemImpactContainer');
        if (!container) return;

        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading impact data...</div>';

        try {
            // Load impact data from API
            const impactData = await window.BUDG_API_SERVICE.getSystemImpact(id);

            if (!impactData || impactData.length === 0) {
                container.innerHTML = `
                    <div class="impact-section">
                        <div class="impact-header">
                            <div class="impact-title">Impact Analysis</div>
                        </div>
                        <div class="impact-content">
                            <div class="impact-empty">
                                <i class="fas fa-chart-line"></i>
                                <span>No impact data available for this system</span>
                            </div>
                        </div>
                    </div>
                `;
                return;
            }

            // Render impact data
            const impactHtml = impactData.map(item => `
                <div class="impact-item">
                    <div class="impact-item-header">
                        <h4>${escapeHtml(item.title || 'Impact Item')}</h4>
                        <span class="impact-severity ${item.severity || 'medium'}">${escapeHtml(item.severity || 'Medium')}</span>
                    </div>
                    <div class="impact-item-content">
                        <p>${escapeHtml(item.description || 'No description available')}</p>
                        <div class="impact-metrics">
                            <span class="metric">Affected Systems: ${item.affectedSystems || 0}</span>
                            <span class="metric">Risk Level: ${escapeHtml(item.riskLevel || 'Unknown')}</span>
                        </div>
                    </div>
                </div>
            `).join('');

            container.innerHTML = `
                <div class="impact-section">
                    <div class="impact-header">
                        <div class="impact-title">Impact Analysis</div>
                    </div>
                    <div class="impact-content">
                        ${impactHtml}
                    </div>
                </div>
            `;
        } catch (e) {
            console.error('Failed to load impact data:', e);
            container.innerHTML = `
                <div class="impact-section">
                    <div class="impact-header">
                        <div class="impact-title">Impact Analysis</div>
                    </div>
                    <div class="impact-content">
                        <div class="impact-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>Failed to load impact data</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        // Hide edit controls for guests
        hideEditsIfUnauthenticated();
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', function () { window.history.length > 1 ? window.history.back() : window.location.assign('/'); });
        const id = parseId();

        // Cache to store summary content to avoid reloading
        let summaryContentCache = null;

        if (id != null) {
            // Ensure content body and container are visible
            const contentBody = document.querySelector('.content-body');
            if (contentBody) {
                contentBody.style.display = '';
            }
            const systemViewContainer = document.getElementById('systemViewContainer');
            if (systemViewContainer) {
                systemViewContainer.style.display = '';
                systemViewContainer.style.visibility = 'visible';
            }
            
            load(id).then(async () => {
                // Force a re-render to ensure data is visible
                const container = document.getElementById('systemViewContainer');
                if (container) {
                    container.style.display = '';
                    container.style.visibility = 'visible';
                    
                    // Ensure summary tab is active and content is visible
                    const summaryTab = document.querySelector('.tab[data-tab="summary"]');
                    if (summaryTab) {
                        summaryTab.classList.add('active');
                        // Remove active from other tabs
                        document.querySelectorAll('.tab-container .tab').forEach(t => {
                            if (t !== summaryTab) {
                                t.classList.remove('active');
                            }
                        });
                    }
                    
                    // Trigger a small delay to ensure DOM is updated
                    await new Promise(resolve => setTimeout(resolve, 50));
                }
                
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('system', id);
                }
            }).catch(error => {
                console.error('Failed to load system:', error);
            });

            // Check for URL parameters and auto-switch to Data Flow tab if present
            const urlParams = new URLSearchParams(window.location.search);
            const tab = urlParams.get('tab');
            const filterFrom = urlParams.get('filterFrom');
            const filterTo = urlParams.get('filterTo');

            if (tab === 'DataFlow' && filterFrom && filterTo) {
                setTimeout(() => {
                    if (window.addDataFlowTab) {
                        window.addDataFlowTab(id, filterFrom, filterTo);
                    } else {
                        console.error('addDataFlowTab function not available');
                    }
                }, 500);
            }

            // Check for hash parameters and auto-switch to Relationships tab if present
            const hashParams = parseHashParameters();
            if (hashParams.filterFrom && hashParams.filterTo && window.location.hash.includes('Relationships')) {
                setTimeout(() => {
                    const relationshipsTab = document.querySelector('.tab[data-tab="relationships"]');
                    if (relationshipsTab) {
                        relationshipsTab.click();
                    }
                }, 300);
            }
        }

        async function updateTitleOnly(systemId, viewMode) {
            try {
                const pageTitleMain = document.querySelector('.page-title-main');
                const pageTitleSub = document.querySelector('.page-title-sub');
                const viewParam = viewMode === 'changes' ? 'changes' : null;
                console.log('[System] updateTitleOnly called:', { systemId, viewMode, viewParam });
                const data = await window.BUDG_API_SERVICE.getSystemById(systemId, viewParam);
                console.log('[System] updateTitleOnly - loaded data:', { id: data?.id, name: data?.name, expectedId: systemId });
                
                // Verify data is correct for the view mode
                if (viewMode === 'original' && data && data.id !== systemId) {
                    console.warn('[System] updateTitleOnly - Data ID mismatch! Expected:', systemId, 'Got:', data.id);
                    // Reload with explicit original view
                    const originalData = await window.BUDG_API_SERVICE.getSystemById(systemId, null);
                    if (originalData && originalData.id === systemId) {
                        if (pageTitleMain) {
                            pageTitleMain.textContent = escapeHtml(originalData.name || 'System');
                            console.log('[System] updateTitleOnly - Title corrected to original:', originalData.name);
                        }
                    } else {
                        if (pageTitleMain) {
                            pageTitleMain.textContent = escapeHtml(data?.name || 'System');
                            console.log('[System] updateTitleOnly - Title updated (fallback):', data?.name);
                        }
                    }
                } else {
                    if (pageTitleMain) {
                        pageTitleMain.textContent = escapeHtml(data?.name || 'System');
                        console.log('[System] updateTitleOnly - Title updated:', data?.name, '(viewMode:', viewMode, ')');
                    }
                }
                if (pageTitleSub) pageTitleSub.textContent = 'System';
            } catch (e) {
                console.error('[System] Failed to update title on view switch:', e);
            }
        }

        // Wire up tab edit button based on active tab
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.addEventListener('click', async function () {
                if (id != null) {
                    // Check lock status before navigating
                    if (window.ViewLockHelper) {
                        const canEdit = await window.ViewLockHelper.interceptEditButton(
                            'system',
                            id,
                            function() {
                                const activeTab = document.querySelector('.tab.active');
                                const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';

                                console.log('Edit button clicked for active tab:', tabName);

                                if (tabName === 'interfaces') {
                                    // For interfaces, they have their own view/edit pages
                                    console.log('Interfaces tab has inline editing via interfaces table');
                                } else {
                                    // For all other tabs, navigate to edit page with the tab parameter
                                    window.location.href = `/view/system/system-edit.html?id=${id}&tab=${tabName}`;
                                }
                            }
                        );
                        if (!canEdit) return; // Lock check failed, navigation prevented
                    } else {
                        // Fallback if ViewLockHelper not available
                        const activeTab = document.querySelector('.tab.active');
                        const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';

                        console.log('Edit button clicked for active tab:', tabName);

                        if (tabName === 'interfaces') {
                            // For interfaces, they have their own view/edit pages
                            console.log('Interfaces tab has inline editing via interfaces table');
                        } else {
                            // For all other tabs, navigate to edit page with the tab parameter
                            window.location.href = `/view/system/system-edit.html?id=${id}&tab=${tabName}`;
                        }
                    }
                }
            });
        }

        // Helper function to hide all containers
        function hideAllSystemContainers() {
            const containerIds = [
                'systemViewContainer',
                'systemStakeholdersContainer',
                'systemImpactContainer',
                'systemInterfacesContainer',
                'systemRelationshipsContainer',
                'systemDataContainer',
                'systemHistoryContainer',
                'systemChangeContainer'
            ];
            containerIds.forEach(function (containerId) {
                const container = document.getElementById(containerId);
                if (container) container.style.display = 'none';
            });
        }

        // Traditional tab handling
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(function (btn) {
            btn.addEventListener('click', function () {
                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                
                // Toggle stakeholder-only edit mode based on active tab and auto CR state
                if (window.EditDropdown && _hasActiveAutoCR && _canEditObject) {
                    if (which === 'stakeholders') {
                        window.EditDropdown.enableStakeholderOnlyEdit(`/view/system/system-edit.html?id=${id}`);
                    } else {
                        window.EditDropdown.disableStakeholderOnlyEdit();
                    }
                }
                
                tabs.forEach(function (b) { b.classList.remove('active'); });
                this.classList.add('active');

                const body = document.querySelector('.content-body');
                if (!body) return;
                const viewToUse = getCurrentViewMode();
                if (which === 'impact') {
                    // Hide all existing content
                    const existingContent = body.querySelectorAll('.view-section, .view-grid');
                    existingContent.forEach(el => el.style.display = 'none');

                    // Create or show impact container
                    let impactContainer = document.getElementById('systemImpactContainer');
                    if (!impactContainer) {
                        impactContainer = document.createElement('div');
                        impactContainer.id = 'systemImpactContainer';
                        impactContainer.className = 'view-section';
                        impactContainer.style.gridColumn = '1/-1';
                        body.appendChild(impactContainer);
                    }
                    impactContainer.style.display = 'block';

                    const id = parseId();
                    if (id && window.loadSystemImpact && typeof window.loadSystemImpact === 'function') {
                        window.loadSystemImpact(id, viewToUse);
                    }
                } else if (which === 'relationships') {
                    // Save summary content before replacing it
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    // Check for filter parameters in URL hash
                    const hashParams = parseHashParameters();
                    const filterFrom = hashParams.filterFrom ? parseInt(hashParams.filterFrom) : null;
                    const filterTo = hashParams.filterTo ? parseInt(hashParams.filterTo) : null;

                    body.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1;">
                            <div id="relationshipsHierarchyContainer">
                                <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading system hierarchy...</div>
                            </div>
                            ${filterFrom && filterTo ? `
                            <div id="attributeRelationshipsSection" class="view-section" style="grid-column:1/-1; margin-top: 2rem;">
                                <div class="section-title" style="position:relative;">
                                    <span>ATTRIBUTE RELATIONSHIPS</span>
                                    <span style="position:absolute;right:0.75rem;top:50%;transform:translateY(-50%);">${gridSettingsHtml('attrRel')}</span>
                                </div>
                                <div class="interfaces-table-wrapper">
                                    <table class="interfaces-table" id="attributeRelationshipsTable">
                                        <thead>
                                            <tr>
                                                <th>Source Attribute</th>
                                                <th>Source Dataset</th>
                                                <th>Target Attribute</th>
                                                <th>Target Dataset</th>
                                                <th>Type</th>
                                                <th>Scope</th>
                                            </tr>
                                        </thead>
                                        <tbody id="attributeRelationshipsTbody">
                                            <tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading attribute relationships...</td></tr>
                                        </tbody>
                                    </table>
                                    <div class="table-footer" id="attributeRelationshipsFooter">0 records</div>
                                </div>
                            </div>
                            ` : ''}
                        </div>
                    `;
                    console.log('System - About to call loadSystemHierarchy with ID:', id);
                    loadSystemHierarchy(id).catch(error => {
                        console.error('Failed to load system hierarchy:', error);
                    });

                    // Initialize grid settings in relationships tab
                    initGridSettings(body);

                    // Load filtered attribute relationships if filters are present
                    if (filterFrom && filterTo) {
                        loadFilteredAttributeRelationships(id, filterFrom, filterTo).catch(error => {
                            console.error('Failed to load filtered attribute relationships:', error);
                        });
                    }
                } else if (which === 'stakeholders') {
                    // Save summary content before replacing it
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    body.innerHTML = `
                        <div id="systemStakeholdersContainer" class="view-section" style="grid-column:1/-1;">
                            <div class="stakeholders-view-header" style="display: flex; justify-content: flex-end; margin-bottom: 1rem;">
                                <div class="stakeholders-actions">
                                    <button type="button" class="btn btn-primary btn-sm" id="editStakeholdersBtn" title="Edit Stakeholders">
                                        <i class="fas fa-pen"></i> Edit
                                    </button>
                                </div>
                            </div>
                        </div>
                    `;

                    // Initialize system stakeholders view
                    if (window.SystemStakeholderView) {
                        window.SystemStakeholderView.init(id, viewToUse);
                    } else {
                        console.error('SystemStakeholderView not loaded');
                        body.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">Stakeholders view not available</div>';
                    }

                    // Add event listener for edit button
                    const editBtn = document.getElementById('editStakeholdersBtn');
                    if (editBtn) {
                        editBtn.addEventListener('click', function () {
                            // Navigate to edit page with stakeholders tab
                            window.location.href = `/view/system/system-edit.html?id=${id}&tab=stakeholders`;
                        });

                        // Hide edit button if user is not admin (same logic as other edit buttons)
                        hideEditsIfUnauthenticated();
                    }
                } else if (which === 'interfaces') {
                    // Save summary content before replacing it
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    body.innerHTML = '<div id="systemInterfacesContainer" class="view-grid"></div>';
                    loadInterfaces(parseId() ?? id, viewToUse).catch(error => {
                        console.error('Failed to load interfaces:', error);
                    });
                } else if (which === 'impact') {
                    // Save summary content before replacing it (if not already hidden by impact tab logic)
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    body.innerHTML = '<div id="systemImpactContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadSystemImpactData(id).catch(error => {
                        console.error('Failed to load system impact data:', error);
                    });
                } else if (which === 'data') {
                    // Save summary content before replacing it
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    // Show data tab using isolated module
                    body.innerHTML = '<div id="systemViewContainer" class="view-grid"></div>';
                    if (window.SystemDataView && id) {
                        window.SystemDataView.init(id);
                    }
                } else if (which === 'history') {
                    // Save summary content before replacing it
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    body.innerHTML = '<div id="systemHistoryContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadSystemHistory(id).catch(error => {
                        console.error('Failed to load system history:', error);
                    });
                } else if (which === 'change') {
                    // Save summary content before replacing it
                    const summaryContainer = document.getElementById('systemViewContainer');
                    if (summaryContainer && !summaryContentCache) {
                        summaryContentCache = summaryContainer.innerHTML;
                    }

                    // Hide all containers
                    const existingContainers = body.querySelectorAll('.view-grid, .view-section');
                    existingContainers.forEach(el => el.style.display = 'none');

                    // Show or create change container
                    let changeContainer = document.getElementById('systemChangeContainer');
                    if (!changeContainer) {
                        changeContainer = document.createElement('div');
                        changeContainer.id = 'systemChangeContainer';
                        changeContainer.className = 'view-grid';
                        changeContainer.style.gridColumn = '1/-1';
                        body.appendChild(changeContainer);
                    }
                    changeContainer.style.display = 'grid';

                    // Initialize change tab component
                    if (window.ChangeTabComponent) {
                        window.ChangeTabComponent.initialize('system', id, 'systemChangeContainer');
                    } else {
                        changeContainer.innerHTML = '<div class="empty-state"><i class="fas fa-exclamation-triangle"></i><p>Change component not available</p></div>';
                    }
                } else {
                    // Summary tab - restore from cache if available
                    body.innerHTML = '<div id="systemViewContainer" class="view-grid"></div>';
                    const summaryContainer = document.getElementById('systemViewContainer');

                    if (summaryContentCache && summaryContainer) {
                        // Restore cached content
                        summaryContainer.innerHTML = summaryContentCache;
                        // Re-initialize collapsible sections and other interactive elements
                        summaryContainer.querySelectorAll('[data-collapsible] .collapsible-header').forEach(function (btn) {
                            btn.addEventListener('click', function () {
                                const section = this.closest('[data-collapsible]');
                                const body = section.querySelector('.collapsible-body');
                                const isOpen = section.classList.contains('open');
                                section.classList.toggle('open');
                                this.setAttribute('aria-expanded', String(!isOpen));
                                if (body) body.style.display = isOpen ? 'none' : '';
                            });
                        });

                        // Re-initialize data content summary if needed
                        const dcsBody = document.getElementById('dataContentContainer');
                        if (dcsBody && id != null) {
                            const dcsBodyContent = dcsBody.innerHTML.trim();
                            if (dcsBodyContent === '' || dcsBodyContent.includes('Loading...')) {
                                loadDataContentSummary(id, dcsBody).catch(error => {
                                    console.error('Failed to reload data content summary:', error);
                                });
                            }
                        }
                    } else if (id != null && summaryContainer) {
                        // No cache available, load fresh
                        load(id).catch(error => {
                            console.error('Failed to load system:', error);
                        });
                    }
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('systemStakeholdersContainer');
                }
            });
        });

        // Toggle event: keep the active tab in sync with the selected view (original/changes)
        document.addEventListener('pendingChangesViewSwitch', async function (event) {
            const { view, facetType, objectId } = (event && event.detail) ? event.detail : {};
            if (facetType !== 'System' || !objectId) return;
            currentView = view;
            console.log('[System] pendingChangesViewSwitch event received, view:', view);

            // Always keep title/header in sync (Glossary behavior)
            await updateTitleOnly(objectId, view);

            const activeTab = document.querySelector('.tab-container .tab.active');
            const which = activeTab ? (activeTab.getAttribute('data-tab') || 'summary') : 'summary';
            console.log('[System] Active tab:', which);

            switch (which) {
                case 'summary':
                    // Clear cache to force reload with new view mode
                    summaryContentCache = null;
                    await load(objectId, view);
                    break;
                case 'stakeholders':
                    if (window.SystemStakeholderView) {
                        window.SystemStakeholderView.init(objectId, view);
                    }
                    break;
                case 'impact':
                    if (window.loadSystemImpact) {
                        window.loadSystemImpact(objectId, view);
                    }
                    break;
                case 'data':
                    if (window.SystemDataTab) {
                        window.SystemDataTab.init(objectId, view);
                    }
                    break;
                case 'interfaces':
                    loadInterfaces(objectId, view).catch(error => console.error('Failed to load interfaces:', error));
                    break;
                case 'relationships':
                    loadSystemHierarchy(objectId, view).catch(error => console.error('Failed to load hierarchy:', error));
                    break;
                case 'history':
                case 'change':
                    // Not view-dependent
                    break;
                default:
                    await load(objectId, view);
                    break;
            }
            
            // Also reload documents with new view mode
            if (systemDocumentTable) {
                systemDocumentTable.setViewMode(view);
            }
        });

        // Initialize unified edit dropdown
        if (window.EditDropdown && id != null) {
            (async () => {
                try {
                    // Check Auto CR status and determine edit restrictions
                    let disableEditOption = false;
                    let disableEditReason = '';
                    try {
                        const statusRes = await fetch(`/api/pending-changes/status/System/${id}`, {
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
                                    } catch (e) { console.warn('[System] Failed to fetch CR details:', e); }
                                }

                                const statusLower = (crStatusName || '').toLowerCase();
                                const isFinished = statusLower.includes('complete') || statusLower.includes('cancelled') || statusLower.includes('canceled') || statusLower.includes('closed') || statusLower.includes('reject');

                                if (isAutoCR && !isFinished) {
                                    const isRequester = crCreatedBy != null && currentUserId != null && String(crCreatedBy) === String(currentUserId);
                                    const isRunning = statusLower.includes('running') || statusLower.includes('in progress');
                                    console.log('[System] Auto CR active: status=' + crStatusName + ', isRequester=' + isRequester);

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
                        console.warn('[System] Could not check CR status:', e);
                    }
                    
                    // Store auto CR state for stakeholder tab redirect
                    _hasActiveAutoCR = disableEditOption;
                    
                    window.EditDropdown.initialize('system', id, {
                        container: '.tab-actions',
                        editUrl: `/view/system/system-edit.html?id=${id}`,
                        hideEditOption: false,
                        disableEditOption: disableEditOption,
                        disableEditReason: disableEditReason
                    });
                } catch (error) {
                    console.error('Failed to initialize edit dropdown:', error);
                }
            })();
        }
    });

    // Build complete family tree for systems (grandparents to grandchildren)
    function buildDirectLineageTree(systems, currentId) {
        const byId = new Map();
        systems.forEach(s => {
            const id = s.ID ?? s.id;
            byId.set(id, s);
        });

        console.log('System - Building lineage tree for ID:', currentId);
        console.log('System - Available systems:', systems.map(s => ({ id: s.ID ?? s.id, name: s.name || s.Name, parent_id: s.parent_id ?? s.Parent_ID ?? s.parentId })));

        // Find all ancestors (parents, grandparents, great-grandparents, etc.) of the current system
        const ancestors = new Set();
        let current = byId.get(currentId);
        console.log('System - Current system:', current);

        while (current) {
            const parentId = current.parent_id ?? current.Parent_ID ?? current.parentId;
            console.log('System - Looking for parent ID:', parentId);
            if (parentId && parentId !== 0 && byId.has(parentId)) {
                ancestors.add(parentId);
                current = byId.get(parentId);
                console.log('System - Found ancestor:', current);
            } else {
                break;
            }
        }

        // Find all descendants (children, grandchildren, great-grandchildren, etc.) of the current system
        const descendants = new Set();
        function findDescendants(id) {
            systems.forEach(s => {
                const parentId = s.parent_id ?? s.Parent_ID ?? s.parentId;
                if (parentId === id) {
                    const childId = s.ID ?? s.id;
                    descendants.add(childId);
                    console.log('System - Found descendant:', s);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(currentId);

        // Find all siblings (other children of the same parent)
        const siblings = new Set();
        const currentSystem = byId.get(currentId);
        if (currentSystem) {
            const currentParentId = currentSystem.parent_id ?? currentSystem.Parent_ID ?? currentSystem.parentId;
            console.log('System - Current parent ID:', currentParentId);
            if (currentParentId) {
                systems.forEach(s => {
                    const parentId = s.parent_id ?? s.Parent_ID ?? s.parentId;
                    if (parentId === currentParentId && (s.ID ?? s.id) !== currentId) {
                        siblings.add(s.ID ?? s.id);
                        console.log('System - Found sibling:', s);
                    }
                });
            }
        }

        console.log('System - Ancestors:', Array.from(ancestors));
        console.log('System - Descendants:', Array.from(descendants));
        console.log('System - Siblings:', Array.from(siblings));

        // CRITICAL: Find children of siblings (nieces and nephews)
        const childrenOfSiblings = new Set();
        siblings.forEach(siblingId => {
            systems.forEach(s => {
                const parentId = s.parent_id ?? s.Parent_ID ?? s.parentId;
                if (parentId === siblingId) {
                    childrenOfSiblings.add(s.ID ?? s.id);
                }
            });
        });

        // CRITICAL: Find children of siblings of parent (cousins) - SIMPLIFIED
        const childrenOfParentSiblings = new Set();
        if (currentSystem) {
            const currentParentId = currentSystem.parent_id ?? currentSystem.Parent_ID ?? currentSystem.parentId;
            if (currentParentId) {
                const parent = byId.get(currentParentId);
                if (parent) {
                    const grandparentId = parent.parent_id ?? parent.Parent_ID ?? parent.parentId;
                    if (grandparentId) {
                        // Find siblings of parent (uncles/aunts)
                        systems.forEach(uncleAunt => {
                            const uncleAuntParentId = uncleAunt.parent_id ?? uncleAunt.Parent_ID ?? uncleAunt.parentId;
                            if (uncleAuntParentId === grandparentId && (uncleAunt.ID ?? uncleAunt.id) !== currentParentId) {
                                console.log('Found uncle/aunt:', uncleAunt);
                                // Find children of this uncle/aunt (cousins)
                                systems.forEach(s => {
                                    const parentId = s.parent_id ?? s.Parent_ID ?? s.parentId;
                                    if (parentId === (uncleAunt.ID ?? uncleAunt.id)) {
                                        childrenOfParentSiblings.add(s.ID ?? s.id);
                                        console.log('Found cousin:', s);
                                    }
                                });
                            }
                        });
                    }
                }
            }
        }

        // CRITICAL: Ensure we ALWAYS include basic relationships
        const basicRelationships = new Set([currentId, ...ancestors, ...descendants, ...siblings]);
        console.log('System - Basic relationships:', Array.from(basicRelationships));

        // Include the current system, all its ancestors, all its descendants, all its siblings, nieces/nephews, and cousins
        const includedIds = new Set([
            ...basicRelationships,        // CRITICAL: Always include basic relationships
            ...childrenOfSiblings,        // CRITICAL: Children of siblings (nieces/nephews)
            ...childrenOfParentSiblings   // CRITICAL: Children of parent siblings (cousins)
        ]);

        console.log('System - Final included IDs:', Array.from(includedIds));

        // Filter systems to include the COMPLETE EXTENDED FAMILY TREE
        let result = systems.filter(s => {
            const id = s.ID ?? s.id;
            return includedIds.has(id);
        });

        // FALLBACK: If no extended family found, ensure we at least have basic relationships
        if (result.length <= 1) {
            console.log('System - FALLBACK: Using basic relationships only');
            result = systems.filter(s => {
                const id = s.ID ?? s.id;
                return basicRelationships.has(id);
            });
        }

        console.log('Final COMPLETE FAMILY TREE systems:', result);
        return result;
    }

    // Robust buildHierarchyTree - normalizes IDs, sets childCount for every node, avoids undefined
    function buildHierarchyTree(systems, rootId) {
        console.log('System - Building hierarchy tree with systems:', systems.length);
        console.log('System - Root ID:', rootId);

        // Helper getters and normalizer (use string keys everywhere)
        const getId = s => String(s.ID ?? s.id);
        const getParentId = s => {
            const p = s.parent_id ?? s.Parent_ID ?? s.parentId;
            return (p === null || p === undefined) ? null : String(p);
        };
        const rootKey = rootId == null ? null : String(rootId);

        // Build maps: byParent (parentId -> [children]) and byId (id -> node)
        const byParent = new Map();
        const byId = new Map();

        systems.forEach(s => {
            const id = getId(s);
            const parentId = getParentId(s); // may be null
            byId.set(id, s);

            if (!byParent.has(parentId)) byParent.set(parentId, []);
            byParent.get(parentId).push(s);
        });

        // Ensure every id has an entry in byParent (even if no children) for consistency
        byId.forEach((_, id) => {
            if (!byParent.has(id)) byParent.set(id, []);
        });

        // Sort children lists by name to keep stable ordering
        byParent.forEach(list => list.sort((a, b) => {
            const an = String(a.name || a.Name || '');
            const bn = String(b.name || b.Name || '');
            return an.localeCompare(bn);
        }));

        // Compute child counts recursively
        const childCount = new Map();
        function countDescendants(id) {
            if (id == null) return 0;
            id = String(id);
            if (childCount.has(id)) return childCount.get(id);

            const children = byParent.get(id) || [];
            let total = children.length;
            children.forEach(child => {
                const childId = getId(child);
                total += countDescendants(childId);
            });
            childCount.set(id, total);
            return total;
        }

        // Compute child counts for all systems
        byId.forEach((_, id) => {
            const count = countDescendants(id);
            console.log(`System - Child count for ${id}: ${count}`);
        });

        // Build flattened result rows with depth and hasChildren and consistent parent mapping
        const result = [];

        // We'll compute depth by walking up parents (safe, stops if missing)
        function computeDepth(node) {
            let depth = 0;
            let parentId = getParentId(node);
            while (parentId) {
                depth++;
                const parentNode = byId.get(parentId);
                if (!parentNode) break;
                parentId = getParentId(parentNode);
            }
            return depth;
        }

        // DFS traversal to build tree order (children appear directly below their parent)
        function dfs(parentId, depth) {
            const children = byParent.get(parentId) || [];
            children.forEach(node => {
                const id = getId(node);
                const nodeChildren = byParent.get(id) || [];
                result.push({
                    node,
                    depth,
                    childCount: childCount.get(id) || 0,
                    hasChildren: nodeChildren.length > 0
                });
                dfs(id, depth + 1);
            });
        }
        // Start from roots (nodes with no parent or parent not in the set)
        dfs(null, 0);
        // Also handle roots whose parent_id is not null but parent is missing from data
        byId.forEach((node, id) => {
            const pid = getParentId(node);
            if (pid !== null && !byId.has(pid) && !result.some(r => getId(r.node) === id)) {
                const nodeChildren = byParent.get(id) || [];
                result.push({
                    node,
                    depth: 0,
                    childCount: childCount.get(id) || 0,
                    hasChildren: nodeChildren.length > 0
                });
                dfs(id, 1);
            }
        });

        // parentMap for initSystemsInteractions: map id -> parentId (string or null)
        const parentMap = new Map();
        byId.forEach((node, id) => {
            parentMap.set(id, getParentId(node));
        });

        console.log('System - Final hierarchy rows:', result.length);
        return { rows: result, parentMap };
    }

    // Render systems table with tree structure (similar to org units)
    function renderSystemsTable(hierarchyRows, currentId) {
        const table = document.createElement('div');
        table.className = 'view-section';

        // Debug: Log the hierarchy rows data
        console.log('Rendering hierarchy rows:', hierarchyRows);
        console.log('Current ID:', currentId);

        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.name || node.Name || '';
            const desc = node.description || node.Description || '';
            const type = node.typeName || node.Type_Name || node.type || node.Type || node.systemType || node.System_Type || node.type_name || '';
            const isCurrent = String(node.ID ?? node.id) === String(currentId);
            const id = node.ID ?? node.id;
            const parentId = node.parent_id ?? node.Parent_ID ?? node.parentId ?? '';

            // Debug: Log each node's data
            console.log('Rendering node:', { name, desc, type, id, isCurrent, depth, hasChildren });

            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = '';
            const linkClass = isCurrent ? 'system-link current-system-link' : 'system-link';
            const link = `<a class="${linkClass}" href="/view/system/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}<i class="fas fa-desktop item-icon"></i><span class="system-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
                <td><span title="${escapeHtml(type)}">${escapeHtml(type)}</span></td>
            </tr>`;
        }).join('');

        const hT = (k) => (window.I18n && window.I18n.t(k)) || k;
        table.innerHTML = `
            <div class="section-title">
                <span>${hT('system.hierarchy.title')}</span>
                ${gridSettingsHtml('hierarchy')}
            </div>
            <div class="hierarchy-table-wrapper">
                <table class="hierarchy-table">
                    <thead>
                        <tr>
                            <th>${hT('system.hierarchy.shortName')}</th>
                            <th>${hT('system.hierarchy.description')}</th>
                            <th>${hT('system.hierarchy.type')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${rowsHtml}
                    </tbody>
                </table>
            </div>
        `;
        return table.outerHTML;
    }

    // Initialize systems interactions (similar to org units)
    function initSystemsInteractions(containerEl, hierarchyRows) {
        const collapsed = new Set();
        const parentMap = hierarchyRows.parentMap;
        const tbody = containerEl.querySelector('tbody');
        if (!tbody) return;
        function computeParent(id) {
            return parentMap.get(Number(id)) ?? parentMap.get(id) ?? null;
        }
        function updateVisibility() {
            tbody.querySelectorAll('tr').forEach(tr => {
                let pid = tr.getAttribute('data-parent-id');
                let hide = false;
                while (pid && !hide) {
                    if (collapsed.has(String(pid))) hide = true;
                    pid = computeParent(pid);
                }
                tr.style.display = hide ? 'none' : '';
            });
            tbody.querySelectorAll('tr').forEach(tr => {
                const id = tr.getAttribute('data-id');
                const btn = tr.querySelector('.tree-expander i');
                if (btn) btn.style.transform = collapsed.has(String(id)) ? 'rotate(-90deg)' : 'rotate(0deg)';
            });
        }
        tbody.addEventListener('click', (e) => {
            // Handle tree expander clicks
            const button = e.target.closest('.tree-expander');
            if (button) {
                e.preventDefault();
                e.stopPropagation();
                const tr = button.closest('tr');
                const id = tr.getAttribute('data-id');
                if (collapsed.has(id)) collapsed.delete(id); else collapsed.add(id);
                updateVisibility();
                return;
            }

            // Handle system link clicks
            const link = e.target.closest('.system-link');
            if (link) {
                // Allow normal link navigation
                return;
            }
        });
        updateVisibility();
    }
    // Build full recursive hierarchy tree (includes all ancestors, siblings, cousins, descendants)
    function buildFullHierarchyTree(systems, currentId) {
        const byId = new Map();
        const childrenMap = new Map();

        const getId = s => s.ID ?? s.id;
        const getParentId = s => s.parent_id ?? s.Parent_ID ?? s.parentId ?? null;

        systems.forEach(s => {
            const id = getId(s);
            const parentId = getParentId(s);
            byId.set(id, s);
            if (!childrenMap.has(parentId)) childrenMap.set(parentId, []);
            childrenMap.get(parentId).push(s);
        });

        // Find top-most ancestor (root)
        function findRoot(id) {
            const sys = byId.get(id);
            if (!sys) return id;
            const pid = getParentId(sys);
            return pid && byId.has(pid) ? findRoot(pid) : id;
        }
        const rootId = findRoot(currentId);

        // Recursive tree builder
        function buildNode(id, depth = 0, visited = new Set(), parentId = null) {
            if (visited.has(id)) return null;
            visited.add(id);

            const node = byId.get(id);
            if (!node) return null;

            const children = childrenMap.get(id) || [];
            const childNodes = children.map(c => buildNode(getId(c), depth + 1, visited, id)).filter(Boolean);

            return {
                node,
                depth,
                parentId,
                hasChildren: childNodes.length > 0,
                children: childNodes
            };
        }

        const rootNode = buildNode(rootId);

        // Flatten recursively for rendering and tracking parent relationships
        const flattened = [];
        (function flatten(n) {
            if (!n) return;
            flattened.push({
                node: n.node,
                depth: n.depth,
                parentId: n.parentId,
                hasChildren: n.hasChildren
            });
            n.children.forEach(flatten);
        })(rootNode);

        console.log(`Full hierarchy built for ${currentId}: ${flattened.length} systems total`);
        return flattened;
    }

    // Load system hierarchy data with full recursive tree
    async function loadSystemHierarchy(systemId, viewMode = 'original') {
        const container = document.getElementById('relationshipsHierarchyContainer');

        console.log('System - Loading FULL hierarchy for system ID:', systemId);
        if (!container) return;

        try {
            // Fetch full hierarchy for this system (ancestors, siblings, descendants)
            let list = await window.BUDG_API_SERVICE.getSystemHierarchy(systemId, viewMode === 'changes' ? 'changes' : null);
            if (list && list.data) list = list.data;
            const normalized = Array.isArray(list) ? list : [];
            const sysHT = (k) => (window.I18n && window.I18n.t(k)) || k;
            if (normalized.length === 0) {
                container.innerHTML = '<div class="view-section"><div class="section-title">' + sysHT('system.hierarchy.title') + '</div><div style="text-align:center;padding:1rem;color:var(--text-muted,#9ca3af);">' + sysHT('system.hierarchy.noData') + '</div></div>';
                return;
            }

            // Build tree and render with full tree structure
            const hierarchyRows = buildHierarchyTree(normalized, systemId);
            const tableHtml = renderSystemsTable(hierarchyRows, systemId);
            container.innerHTML = tableHtml;

            // Apply card styling
            const viewSection = container.querySelector('.view-section');
            if (viewSection) {
                viewSection.style.cssText = 'background:#fff;border:1px solid var(--border-color,#e9ecef);border-radius:12px;overflow:visible;box-shadow:0 1px 3px rgba(0,0,0,0.08);';
            }
            const sectionTitle = container.querySelector('.section-title');
            if (sectionTitle) {
                sectionTitle.style.cssText = 'display:flex;align-items:center;font-weight:600;font-size:0.9rem;color:var(--text-primary,#2c3e50);letter-spacing:0.04em;padding:0.75rem 1.25rem;border-left:4px solid var(--secondary-color,#48bb78);background:var(--background-primary,#fff);border-bottom:1px solid var(--border-color,#e9ecef);margin-bottom:0;';
                // Push gear icon to far right
                const gsWrapper = sectionTitle.querySelector('.grid-settings-wrapper');
                if (gsWrapper) gsWrapper.style.marginLeft = 'auto';
            }
            const tableWrapper = container.querySelector('.hierarchy-table-wrapper');
            if (tableWrapper) {
                tableWrapper.style.cssText = 'border:none;border-radius:0;box-shadow:none;max-height:none;overflow:auto;';
            }

            // Add footer with record count
            const n = normalized.length;
            const footerText = n === 1 ? sysHT('system.hierarchy.recordOne') : (sysHT('system.hierarchy.recordCount') || '{count} records').replace('{count}', n);
            const footerEl = document.createElement('div');
            footerEl.className = 'table-footer';
            footerEl.textContent = footerText;
            tableWrapper?.appendChild(footerEl);

            // Initialize expand/collapse interactions
            initSystemsInteractions(container, hierarchyRows);

            // Initialize grid settings
            initGridSettings(container);

        } catch (e) {
            console.error('Failed to load full hierarchy:', e);
            const sysHT = (k) => (window.I18n && window.I18n.t(k)) || k;
            container.innerHTML = '<div class="view-section"><div class="section-title">' + sysHT('system.hierarchy.title') + '</div><div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + sysHT('system.hierarchy.failedToLoad') + ': ' + (e.message || 'Unknown error') + '</div></div>';
        }
    }

    // Load system hierarchy for the summary page (uses existing tree rendering)
    async function loadSummaryHierarchy(systemId) {
        const container = document.getElementById('summaryHierarchyContainer');
        if (!container) return;

        const sysHT = (k) => (window.I18n && window.I18n.t(k)) || k;
        try {
            let list = await window.BUDG_API_SERVICE.getSystemHierarchy(systemId, null);
            if (list && list.data) list = list.data;
            const normalized = Array.isArray(list) ? list : [];
            if (normalized.length === 0) {
                container.innerHTML = '<div class="view-section"><div class="section-title">' + sysHT('system.hierarchy.title') + '</div><div style="text-align:center;padding:1rem;color:var(--text-muted,#9ca3af);">' + sysHT('system.hierarchy.noData') + '</div></div>';
                return;
            }

            // Use existing tree-building and rendering functions
            const hierarchyRows = buildHierarchyTree(normalized, systemId);
            const tableHtml = renderSystemsTable(hierarchyRows, systemId);
            container.innerHTML = tableHtml;

            // Apply card styling to the view-section wrapper
            const viewSection = container.querySelector('.view-section');
            if (viewSection) {
                viewSection.style.cssText = 'background:#fff;border:1px solid var(--border-color,#e9ecef);border-radius:12px;overflow:visible;box-shadow:0 1px 3px rgba(0,0,0,0.08);';
            }

            // Style the section title with left accent bar
            const sectionTitle = container.querySelector('.section-title');
            if (sectionTitle) {
                sectionTitle.style.cssText = 'display:flex;align-items:center;font-weight:600;font-size:0.9rem;color:var(--text-primary,#2c3e50);letter-spacing:0.04em;padding:0.75rem 1.25rem;border-left:4px solid var(--secondary-color,#48bb78);background:var(--background-primary,#fff);border-bottom:1px solid var(--border-color,#e9ecef);margin-bottom:0;';
                // Push gear icon to far right
                const gsWrapper = sectionTitle.querySelector('.grid-settings-wrapper');
                if (gsWrapper) gsWrapper.style.marginLeft = 'auto';
            }

            // Remove duplicate border/shadow from inner table wrapper
            const tableWrapper = container.querySelector('.hierarchy-table-wrapper');
            if (tableWrapper) {
                tableWrapper.style.cssText = 'border:none;border-radius:0;box-shadow:none;max-height:none;overflow:auto;';
            }

            // Add footer with record count
            const n = normalized.length;
            const footerText = n === 1 ? sysHT('system.hierarchy.recordOne') : (sysHT('system.hierarchy.recordCount') || '{count} records').replace('{count}', n);
            const footerEl = document.createElement('div');
            footerEl.className = 'table-footer';
            footerEl.textContent = footerText;
            container.querySelector('.hierarchy-table-wrapper')?.appendChild(footerEl);

            // Initialize expand/collapse interactions
            initSystemsInteractions(container, hierarchyRows);

            // Initialize grid settings
            initGridSettings(container);

        } catch (e) {
            console.error('Failed to load summary hierarchy:', e);
            container.innerHTML = '<div class="view-section"><div class="section-title">' + sysHT('system.hierarchy.title') + '</div><div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + sysHT('system.hierarchy.failedToLoad') + ': ' + (e.message || 'Unknown error') + '</div></div>';
        }
    }

    // Parse hash parameters from URL
    function parseHashParameters() {
        const hash = window.location.hash.substring(1); // Remove #
        const params = {};
        if (hash) {
            const parts = hash.split('&');
            parts.forEach(part => {
                const [key, value] = part.split('=');
                if (key && value) {
                    params[key] = decodeURIComponent(value);
                }
            });
        }
        return params;
    }

    // Load filtered attribute relationships between two systems
    async function loadFilteredAttributeRelationships(targetSystemId, filterFromSystemId, filterToSystemId) {
        const tbody = document.getElementById('attributeRelationshipsTbody');
        const footer = document.getElementById('attributeRelationshipsFooter');

        if (!tbody || !footer) return;

        try {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading attribute relationships...</td></tr>';
            footer.textContent = 'Loading...';

            // Get all datasets for the target system
            const systemData = await window.BUDG_API_SERVICE.getSystemDataContent(targetSystemId);
            const datasets = systemData?.datasets || [];

            if (datasets.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No datasets found for this system.</td></tr>';
                footer.textContent = '0 records';
                return;
            }

            // Fetch attribute relationships for each dataset and filter
            // We need relationships FROM filterFrom system TO filterTo system
            // Since we're viewing filterTo system, check inbound relationships (where this dataset is target)
            const allRelationships = [];
            for (const dataset of datasets) {
                try {
                    const response = await fetch(`/api/dataset-relationships/${dataset.ID || dataset.id}`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: { 'Content-Type': 'application/json' }
                    });

                    if (response.ok) {
                        const data = await response.json();
                        // Check inbound relationships (this dataset is the target)
                        const inbound = data.inbound || [];
                        // Filter relationships where source system = filterFrom and this dataset's system = filterTo
                        const filtered = inbound.filter(rel => {
                            const sourceSystemId = rel.systemId || rel.sourceDatasetMasterSource;
                            // This dataset belongs to filterToSystemId, and source should be filterFromSystemId
                            return sourceSystemId == filterFromSystemId;
                        });

                        allRelationships.push(...filtered);
                    }
                } catch (error) {
                    console.error(`Error loading relationships for dataset ${dataset.ID || dataset.id}:`, error);
                }
            }

            if (allRelationships.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No attribute relationships found between the specified systems.</td></tr>';
                footer.textContent = '0 records';
                return;
            }

            // Render filtered relationships
            const rowsHtml = allRelationships.map(rel => {
                return `
                    <tr class="highlighted-attribute-row" data-attribute-relationship-id="${rel.id || ''}">
                        <td>${escapeHtml(rel.sourceAttributeName || '')} ${rel.sourceAttributeRef ? `(${escapeHtml(rel.sourceAttributeRef)})` : ''}</td>
                        <td>${escapeHtml(rel.sourceDatasetName || '')} ${rel.sourceDatasetRef ? `(${escapeHtml(rel.sourceDatasetRef)})` : ''}</td>
                        <td>${escapeHtml(rel.targetAttributeName || '')} ${rel.targetAttributeRef ? `(${escapeHtml(rel.targetAttributeRef)})` : ''}</td>
                        <td>${escapeHtml(rel.targetDatasetName || '')} ${rel.targetDatasetRef ? `(${escapeHtml(rel.targetDatasetRef)})` : ''}</td>
                        <td>${escapeHtml(rel.relationType || '')}</td>
                        <td>${escapeHtml(rel.relationScope || '')}</td>
                    </tr>
                `;
            }).join('');

            tbody.innerHTML = rowsHtml;
            footer.textContent = `${allRelationships.length} record${allRelationships.length !== 1 ? 's' : ''}`;

            // Auto-scroll to first row and apply highlight
            setTimeout(() => {
                const firstRow = tbody.querySelector('tr.highlighted-attribute-row');
                if (firstRow) {
                    firstRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    // Add highlight class (already in HTML, but ensure it's visible)
                    firstRow.style.backgroundColor = '#fff3cd';
                    firstRow.style.border = '2px solid #ffc107';

                    // Remove highlight after a few seconds
                    setTimeout(() => {
                        firstRow.style.backgroundColor = '';
                        firstRow.style.border = '';
                    }, 3000);
                }
            }, 500);

        } catch (error) {
            console.error('Failed to load filtered attribute relationships:', error);
            tbody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load attribute relationships.</td></tr>`;
            footer.textContent = '0 records';
        }
    }

    // Global function to test hierarchy building
    window.testSystemHierarchy = async function (systemId) {
        console.log('=== Testing System Hierarchy ===');
        try {
            const list = await window.BUDG_API_SERVICE.getSystemsList();
            const normalized = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];

            console.log('Total systems:', normalized.length);
            console.log('Testing with system ID:', systemId);

            // Show all systems with their parent relationships
            console.log('All systems with parent info:');
            normalized.forEach((system, index) => {
                const parentId = system.parent_id || system.Parent_ID || system.parentId;
                console.log(`${index + 1}. ${system.name || system.Name} (ID: ${system.ID || system.id}, Parent: ${parentId || 'None'}, Type: ${system.typeName || system.type || system.Type || 'None'})`);
            });

            if (filteredSystems.length > 0) {
                console.log('Hierarchy systems:');
                filteredSystems.forEach((system, index) => {
                    const parentId = system.parent_id || system.Parent_ID || system.parentId;
                    console.log(`${index + 1}. ${system.name || system.Name} (ID: ${system.ID || system.id}, Parent: ${parentId || 'None'}, Type: ${system.typeName || system.type || system.Type || 'None'})`);
                });
            } else {
                console.log('No hierarchy found - this system has no parents or children');
            }

            return { normalized, filteredSystems };
        } catch (error) {
            console.error('Error testing hierarchy:', error);
            return null;
        }
    };

    // Global function to show child systems
    window.showChildSystems = async function (systemId) {
        console.log('=== Showing Child Systems ===');
        try {
            const list = await window.BUDG_API_SERVICE.getSystemsList();
            const normalized = Array.isArray(list?.data) ? list.data : Array.isArray(list) ? list : [];

            // Find child systems
            const childSystems = normalized.filter(s => {
                const parentId = s.parent_id || s.Parent_ID || s.parentId;
                return parentId && String(parentId) === String(systemId);
            });

            console.log(`Child systems for system ID ${systemId}:`, childSystems.length);
            if (childSystems.length > 0) {
                childSystems.forEach((child, index) => {
                    console.log(`${index + 1}. ${child.name || child.Name} (ID: ${child.ID || child.id})`);
                });
            } else {
                console.log('No child systems found. To create child systems:');
                console.log('1. Go to system creation page');
                console.log('2. Set the parent system to this system');
                console.log('3. Save the new system');
            }

            return childSystems;
        } catch (error) {
            console.error('Error showing child systems:', error);
            return null;
        }
    };

    // Load system history using the reusable component
    async function loadSystemHistory(systemId) {
        const container = document.getElementById('systemHistoryContainer');
        if (!container) return;

        // Wait for HistoryComponent to be available
        let attempts = 0;
        const maxAttempts = 10;

        while (attempts < maxAttempts) {
            if (window.HistoryComponent) {
                console.log('HistoryComponent found, initializing...');
                try {
                    await window.HistoryComponent.initialize('Systems', systemId, 'systemHistoryContainer');
                    console.log('History component initialized successfully');
                    return;
                } catch (error) {
                    console.error('Error initializing history component:', error);
                    container.innerHTML = '<div class="error">Failed to initialize history component</div>';
                    return;
                }
            }

            console.log(`HistoryComponent not found, attempt ${attempts + 1}/${maxAttempts}`);
            await new Promise(resolve => setTimeout(resolve, 100));
            attempts++;
        }

        console.error('HistoryComponent not loaded after waiting. Please include history-component.js');
        container.innerHTML = '<div class="error">History component not available</div>';
    }
})();

