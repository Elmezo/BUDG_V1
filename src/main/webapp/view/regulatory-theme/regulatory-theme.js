(function() {
    function parseId() {
        // Primary: support query param pattern /regulatory-theme.html?id=123
        try {
            const params = new URLSearchParams(window.location.search);
            const qId = params.get('id');
            if (qId) {
                const n = parseInt(qId, 10);
                if (!Number.isNaN(n)) return n;
            }
        } catch(_) {}

        // Fallback: support route pattern /view/regulatory-theme/123
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('regulatory-theme');
        if (idx !== -1 && parts.length >= idx + 2) {
            const id = parseInt(parts[idx + 1], 10);
            return Number.isNaN(id) ? null : id;
        }
        return null;
    }

    let _regulatoryThemeGuestCache = null;
    async function isGuestVisitorForRegulatoryTheme() {
        if (_regulatoryThemeGuestCache !== null) {
            return _regulatoryThemeGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _regulatoryThemeGuestCache = true;
                return _regulatoryThemeGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _regulatoryThemeGuestCache = !isAuthenticated || isGuestRole;
            return _regulatoryThemeGuestCache;
        } catch (_) {
            _regulatoryThemeGuestCache = true;
            return _regulatoryThemeGuestCache;
        }
    }

    function renderRegulatoryThemeGuestAccessDenied(container, themeId, segmentName, segmentId) {
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

    // Robust hide function for edit controls
    function hideEditControls() {
        // Hide the edit dropdown for unauthorized users
        if (window.EditDropdown) {
            window.EditDropdown.hideEditControls();
        }
        const selectors = [
            '#editBtn',
            '#editBtn2',
            '#tabEditBtn',
            'editStakeholdersBtn'
        ];
        try {
            const nodes = document.querySelectorAll(selectors.join(','));
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

    // Ensure hide edit controls with DOM observation
    function ensureHideEditControls(options = {}) {
        const timeout = typeof options.timeout === 'number' ? options.timeout : 15000;
        hideEditControls();

        const observer = new MutationObserver(mutations => {
            let found = false;
            for (const m of mutations) {
                for (const node of m.addedNodes) {
                    if (!(node instanceof Element)) continue;
                    if (node.matches && node.matches('#editBtn,#editBtn2,#tabEditBtn,#editStakeholdersBtn,[data-action="edit"]')) {
                        found = true;
                    } else if (node.querySelector && node.querySelector('#editBtn,#editBtn2,#tabEditBtn,#editStakeholdersBtn,[data-action="edit"]')) {
                        found = true;
                    }
                }
            }
            if (found) hideEditControls();
        });

        const target = document.body || document.documentElement;
        try {
            observer.observe(target, { childList: true, subtree: true });
        } catch (e) {
            // ignore observe errors
        }

        setTimeout(() => {
            try { observer.disconnect(); } catch(_) {}
        }, timeout);
    }

    // Auto-run on load - check auth first
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => hideEditsIfUnauthenticated());
    } else {
        hideEditsIfUnauthenticated();
    }

    async function hideEditsIfUnauthenticated() {
        try {
            // Use the new permissions API to check user permissions for Regulatory Theme module
            const permResp = await fetch('/api/user/permissions/Regulatory Theme', { method: 'GET', credentials: 'include' });
            if (!permResp.ok) { 
                console.log('API /api/user/permissions/Regulatory Theme failed:', permResp.status);
                // Fallback to old method
                await hideEditsIfUnauthenticatedFallback();
                return; 
            }
            const perms = await permResp.json();
            console.log('User permissions for Regulatory Theme:', perms);
            
            if (perms.success) {
                const canEdit = perms.canEdit || perms.isAdmin;
                const canDelete = perms.canDelete; // Only Super Admins can delete
                const isAdmin = perms.isAdmin;
                
                console.log('Can edit?', canEdit);
                console.log('Can delete?', canDelete);
                console.log('Is admin?', isAdmin);
                
                // Show the main edit dropdown if user has edit permission
                // Note: Create functionality is now in the header Create menu, not here
                if (canEdit || isAdmin) {
                    console.log('User has edit permission - showing edit controls');
                    showEditControls();
                } else {
                    console.log('User does not have edit permission - hiding edit controls');
                    ensureHideEditControls();
                }
                
                // Hide delete buttons for non-admins (only admin/super admin can delete)
                if (!canDelete) {
                    hideDeleteControls();
                }
                
                // Apply permission visibility to the dropdown
                if (window.EditDropdown && typeof window.EditDropdown.applyPermissionVisibility === 'function') {
                    window.EditDropdown.applyPermissionVisibility({
                        canEdit: canEdit,
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
            // Cache current user for name resolving fallback
            try { window.__BUDG_CURRENT_USER__ = me; } catch(_) {}
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
            '#deleteRegulatoryThemeBtn'
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
        const selectors = [
            '#editBtn',
            '#editBtn2',
            '#tabEditBtn',
            '#editStakeholdersBtn',
            '[data-action="edit"]'
        ];
        try {
            const nodes = document.querySelectorAll(selectors.join(','));
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

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class="empty">-</span>'}</div></div>`;
    }

    function renderUserLink(name, id) {
        if (!name) return '<span class="empty">-</span>';
        if (id) {
            return `<a href="/view/people/${id}" class="user-link">${escapeHtml(name)}</a>`;
        }
        return escapeHtml(name);
    }

    // Helper function to format date
    function formatDate(dateString) {
        if (!dateString) return '';
        try {
            return new Date(dateString).toLocaleDateString('en-GB');
        } catch (error) {
            return dateString;
        }
    }

    async function load(id) {
        const container = document.getElementById('glossaryViewContainer');
        const titleElement = document.getElementById('glossaryTitle');
        const breadcrumbElement = document.getElementById('glossaryBreadcrumb');
        
        // Update title immediately to show loading state
        if (titleElement) {
            titleElement.textContent = 'Loading...';
        }
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading...</div>';
        
        try {
            // Fetch regulatory theme data
            const response = await fetch(`/api/regulatory-theme/${id}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            const rt = await response.json();

            try {
                const isGuest = await isGuestVisitorForRegulatoryTheme();
                const segmentName = rt?.segmentName || rt?.segment_name || rt?.segment;
                const segmentId = rt?.segmentId ?? rt?.segment_id ?? rt?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[RegulatoryTheme] Guest visitor blocked from non-Enterprise segment view', { themeId: id, segmentName, segmentId });
                    renderRegulatoryThemeGuestAccessDenied(container, id, segmentName, segmentId);
                    if (titleElement) {
                        titleElement.removeAttribute('data-i18n');
                        titleElement.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                    }
                    document.title = 'BUDG';
                    return;
                }
            } catch (gateError) {
                console.warn('[RegulatoryTheme] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            
            console.log('Regulatory Theme data:', rt);

            // Update header with regulatory theme data - ensure it's always updated
            if (titleElement) {
                const themeName = rt.primaryName || rt.PrimaryName || rt.name || rt.Name || 'Unnamed Regulatory Theme';
                // Remove data-i18n attribute to prevent i18n from overwriting
                titleElement.removeAttribute('data-i18n');
                titleElement.textContent = escapeHtml(themeName);
                // Also update page title
                document.title = `BUDG - ${themeName}`;
                
                // Stop observing if observer was set
                if (window._regulatoryThemeTitleObserver) {
                    window._regulatoryThemeTitleObserver.disconnect();
                    delete window._regulatoryThemeTitleObserver;
                }
            }
            if (breadcrumbElement) {
                breadcrumbElement.innerHTML = 'Regulatory Theme';
            }

            // Get additional reference data
            let lookupData = {};
            
            try {
                const statusResponse = await fetch('/api/status/list');
                if (statusResponse.ok) {
                    lookupData.statusList = await statusResponse.json();
                }
            } catch (error) {
                console.log('Could not load status data:', error);
            }

            // Load people to resolve LastUpdate_UserID → Name
            try {
                if (window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getPeople) {
                    const people = await window.BUDG_API_SERVICE.getPeople();
                    lookupData.people = Array.isArray(people?.data) ? people.data : (Array.isArray(people) ? people : []);
                } else {
                    const peopleResp = await fetch('/api/people');
                    if (peopleResp.ok) {
                        const people = await peopleResp.json();
                        lookupData.people = Array.isArray(people?.data) ? people.data : (Array.isArray(people) ? people : []);
                    }
                }
            } catch (error) {
                console.log('Could not load people data:', error);
            }

            // Get status name from Status_ID
            let statusName = '';
            let statusClass = '';
            const statusId = rt.statusId || rt.Status_ID || rt.status_ID;
            if (statusId && lookupData.statusList) {
                const status = lookupData.statusList.find(s => (s.id === statusId) || (s.ID === statusId));
                if (status) {
                    statusName = status.primaryname || status.PrimaryName || status.name || status.Name || '';
                    statusClass = status.primaryname?.toLowerCase() === 'active' ? 'active' : 'pending';
                }
            }

            // Resolve user names
            const resolveUserName = (userId) => {
                if (userId == null || !lookupData.people || !Array.isArray(lookupData.people)) return '';
                // Coerce id types and check common keys
                const uidStr = String(userId);
                const uidNum = Number(userId);
                const person = lookupData.people.find(p => {
                    const ids = [p.ID, p.Id, p.id, p.userId, p.UserID, p.User_Id, p.User_ID]
                        .filter(v => v != null)
                        .map(v => [String(v), Number(v)]).flat();
                    return ids.includes(uidStr) || ids.includes(uidNum);
                });
                if (!person) return '';
                const first = person.First_Name || person.firstName || person.FirstName || '';
                const last = person.Last_Name || person.lastName || person.LastName || '';
                const primary = person.primaryName || person.PrimaryName || person.Name || person.name || '';
                const name = `${first} ${last}`.trim();
                return (name || primary || '').trim();
            };

            const resolveUserNameWithFallback = (userId) => {
                // Try full directory
                let name = resolveUserName(userId);
                if (name) return name;
                // Fallback to current user if matches id
                try {
                    const me = window.__BUDG_CURRENT_USER__ || {};
                    const meIds = [me.id, me.ID, me.userId, me.UserID].filter(v => v != null).map(v => String(v));
                    if (meIds.includes(String(userId))) {
                        const first = me.firstName || me.First_Name || '';
                        const last = me.lastName || me.Last_Name || '';
                        const primary = me.primaryName || me.PrimaryName || me.name || me.Name || '';
                        const nm = `${first || ''} ${last || ''}`.trim() || primary;
                        if (nm) return nm;
                    }
                } catch(_) {}
                return '';
            };

            const rtT = (k) => (window.I18n && window.I18n.t(k)) || k;
            const definitionSection = `
                <div class="view-section">
                    <div class="section-title">${rtT('regulatoryTheme.sections.definition')}</div>
                    <div class="view-item"><div class="view-value" style="flex:1">${_richHtml(rt.description || rt.Description || '')}</div></div>
                    ${renderItem(rtT('regulatoryTheme.labels.ref') + ':', escapeHtml(rt.refNumber || rt.RefNumber || ''))}
                    ${renderItem(rtT('regulatoryTheme.labels.shortName') + ':', escapeHtml(rt.shortName || rt.ShortName || '') || '<span class="empty">' + (window.I18n?.t('message.notSpecified') || 'Not specified') + '</span>')}
                </div>
            `;

            // Classifications sidebar
            const classificationsSection = `
                <div class="view-section sidebar-section">
                    <div class="section-title">${rtT('regulatoryTheme.sections.classifications')}</div>
                    
                    <div class="classifications-group">
                        <div class="group-title">${rtT('regulatoryTheme.sections.basicClassifications')}</div>
                        <div class="classification-item">
                            <span class="classification-label">${rtT('regulatoryTheme.labels.budgStatus')}:</span>
                            <span class="classification-value">
                                <span class="status-dot ${statusClass}"></span>
                                ${escapeHtml(statusName || 'Unknown')}
                            </span>
                        </div>
                    </div>

                    <div class="classifications-group">
                        <div class="group-title">${rtT('regulatoryTheme.sections.otherInformation')}</div>
                        <div class="classification-item">
                            <span class="classification-label">${rtT('regulatoryTheme.labels.created')}:</span>
                            <span class="classification-value">${rt.createDateTime || rt.CreateDatetime ? new Date(rt.createDateTime || rt.CreateDatetime).toLocaleString() : '<span class="empty">-</span>'}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${rtT('regulatoryTheme.labels.lastUpdatedBy')}:</span>
                            <span class="classification-value">${renderUserLink(rt.updatedByName, rt.lastUpdateUserId || rt.LastUpdate_UserID)}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${rtT('regulatoryTheme.labels.lastUpdated')}:</span>
                            <span class="classification-value">${rt.lastUpdateDateTime || rt.LastUpdateDatetime ? new Date(rt.lastUpdateDateTime || rt.LastUpdateDatetime).toLocaleString() : '<span class="empty">-</span>'}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${rtT('regulatoryTheme.labels.segment')}:</span>
                            <span class="classification-value">${escapeHtml(rt.segmentName || rt.segment_name || rt.segment || ((rt.segmentId ?? rt.segment_id ?? rt.Segment_ID) != null ? `ID ${rt.segmentId ?? rt.segment_id ?? rt.Segment_ID}` : (window.I18n?.t('message.notSpecified') || 'Not specified')))}</span>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = `
                <div class="glossary-container">
                    <div class="left-column">
                        ${definitionSection}
                    </div>
                    <div class="right-column">
                        ${classificationsSection}
                    </div>
                </div>
            `;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Regulatory Theme',
                        containerId: 'glossaryViewContainer',
                        objectId: id,
                        title: (window.I18n && window.I18n.t('regulatoryTheme.sections.customFields')) || 'CUSTOM FIELDS'
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

        } catch (e) {
            console.error('Error loading regulatory theme:', e);
            const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;
            const backToCreate = t('regulatoryTheme.page.backToCreate');
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            if (isForbidden) {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>Regulatory Theme Not Available</h3>
                        <p>This object is not available.</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulatorytheme.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            } else if (String(e?.message || '').includes('404')) {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('regulatoryTheme.page.notFound')}</h3>
                        <p>${t('regulatoryTheme.page.doesNotExist', { id })}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulatorytheme.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('regulatoryTheme.page.errorLoading')}</h3>
                        <p>${(window.I18n && window.I18n.t('message.failedToLoad')) || 'Failed to load'} (id=${id}). ${e.message || (window.I18n && window.I18n.t('message.error')) || 'Unknown error occurred.'}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulatorytheme.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            }
        }
    }

    // Tab switching logic
    document.addEventListener('DOMContentLoaded', function() {
        hideEditsIfUnauthenticated();
        console.log('Regulatory Theme page loaded');
        
        const backBtn = document.getElementById('backBtn');
        console.log('Back button found:', backBtn);
        
        if (backBtn) backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
        
        // Edit button functionality
        const editBtn = document.getElementById('editBtn');
        if (editBtn) {
            editBtn.addEventListener('click', function() {
                const id = parseId();
                if (id != null) {
                    // Get current active tab
                    const activeTab = document.querySelector('.tab.active');
                    const currentTab = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                    
                    // Define tab mapping: view tab -> edit tab (or 'summary' if not available)
                    const tabMapping = {
                        'summary': 'summary',
                        'components': 'summary', // fallback to summary
                        'regulations': 'regulations',
                        'history': 'summary' // fallback to summary
                    };
                    
                    const targetTab = tabMapping[currentTab] || 'summary';
                    
                    // Navigate to edit page with tab parameter
                    window.location.href = `/view/regulatory-theme/regulatory-theme-edit.html?id=${id}&tab=${targetTab}`;
                }
            });
        }

        // Initialize unified edit dropdown
        const id = parseId();
        if (window.EditDropdown && id != null) {
            try {
                window.EditDropdown.initialize('regulatory-theme', id, {
                    container: '.tab-actions',
                    editUrl: `/view/regulatory-theme/regulatory-theme-edit.html?id=${id}`
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }

        // Wire up tab edit button - general for all tabs
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.addEventListener('click', function() {
                const id = parseId();
                if (id != null) {
                    const activeTab = document.querySelector('.tab.active');
                    const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';

                    // Navigate to edit page with active tab parameter
                    if (tabName && tabName !== 'summary') {
                        window.location.href = `/view/regulatory-theme/regulatory-theme-edit.html?id=${id}&tab=${tabName}`;
                    } else {
                        // For summary tab, just pass id
                        window.location.href = `/view/regulatory-theme/regulatory-theme-edit.html?id=${id}`;
                    }
                }
            });
        }
        
        // Load regulatory theme (id already declared above)
        if (id != null) {
            load(id).then(async () => {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('regulatory-theme', id);
                }
            });
            try { 
                window.BUDG_API_SERVICE?.logVisit({ 
                    entity: 'RegulatoryTheme', 
                    entityId: String(id), 
                    route: `/view/regulatory-theme/${id}` 
                }); 
            } catch(_) {}
        }

        // Tab functionality
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                console.log('Tab clicked:', tabName);

                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                this.classList.add('active');

                // Get regulatory theme ID
                const regulatoryThemeId = parseId();

                // Handle tab content
                const container = document.getElementById('glossaryViewContainer');
                if (!container) return;

                // Tab-specific content handling
                switch (tabName) {
                    case 'summary':
                        // Show summary - reload regulatory theme
                        if (regulatoryThemeId) {
                            load(regulatoryThemeId);
                        }
                        break;

                    case 'components':
                        // Show components tab with regulatory theme hierarchy
                        const compT = (k) => (window.I18n && window.I18n.t(k)) || k;
                        container.innerHTML = `
                            <div class="view-section" style="grid-column: 1/-1;">
                                <div class="relationships-hierarchy">
                                    <div class="hierarchy-header">
                                        <div class="hierarchy-title">${compT('regulatoryTheme.sections.hierarchy')}</div>
                                        <div class="hierarchy-actions">
                                            <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                                        </div>
                                    </div>
                                    <div class="hierarchy-table-wrapper">
                                        <table class="hierarchy-table">
                                            <thead>
                                                <tr>
                                                    <th>${compT('regulatoryTheme.labels.regulatoryTheme')}</th>
                                                    <th>${compT('regulatoryTheme.labels.description')}</th>
                                                    <th>${compT('regulatoryTheme.labels.lastUpdated')}</th>
                                                </tr>
                                            </thead>
                                            <tbody id="hierarchyTbody">
                                                <tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${(window.I18n && window.I18n.t('regulatoryTheme.messages.loading')) || 'Loading...'}</td></tr>
                                            </tbody>
                                        </table>
                                    </div>
                                    <div class="table-footer" id="hierarchyFooter">
                                        ${(window.I18n && window.I18n.t('message.zeroRecords')) || '0 records'}
                                    </div>
                                </div>
                            </div>
                        `;
                        loadRegulatoryThemeHierarchy(regulatoryThemeId).catch(error => {
                            console.error('Failed to load regulatory theme hierarchy:', error);
                        });
                        break;

                    case 'regulations':
                        // Show regulations tab
                        loadRegulationsTab(container, regulatoryThemeId);
                        break;

                    case 'history':
                        // Show history tab with History Component
                        container.innerHTML = '<div id="regulatoryThemeHistoryContainer"></div>';
                        // Use the history component
                        if (typeof createHistoryComponent === 'function') {
                            createHistoryComponent('RegulatoryTheme', regulatoryThemeId, 'regulatoryThemeHistoryContainer');
                        } else {
                            console.error('createHistoryComponent is not available');
                            container.innerHTML = `
                                <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center; color: var(--danger, #dc3545);">
                                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 1rem; display: block;"></i>
                                    <p>History component is not loaded. Please refresh the page.</p>
                                </div>
                            `;
                        }
                        break;

                    default:
                        // Fallback for unknown tabs
                        container.innerHTML = `
                            <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                                <i class="fas fa-info-circle" style="font-size: 3rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                                <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                                    ${tabName.replace('-', ' ').toUpperCase()} tab content will be available soon.
                                </p>
                            </div>
                        `;
                        break;
                }
            });
        });
    });



    // Regulatory Theme Hierarchy Functions - matching system hierarchy logic
    function buildDirectLineageTree(themes, currentThemeId) {
        console.log('buildDirectLineageTree called with themes:', themes.length, 'currentThemeId:', currentThemeId);
        
        const byId = new Map();
        themes.forEach(t => {
            const id = parseInt(t.id);
            byId.set(id, t);
            console.log('Added to byId map:', id, '->', t.primaryName, '(parent:', t.parentId, ')');
        });
        
        // Convert currentThemeId to number for consistent comparison
        const currentId = parseInt(currentThemeId);
        const currentTheme = byId.get(currentId);
        console.log('Current theme ID (converted):', currentId);
        console.log('byId map size:', byId.size);
        console.log('byId map keys:', Array.from(byId.keys()));
        
        // Find all ancestors (parents, grandparents, great-grandparents, etc.) of the current theme
        const ancestors = new Set();
        let current = currentTheme;
        console.log('Starting ancestor search from theme:', current ? current.primaryName : 'Not found');
        
        while (current) {
            const parentId = current.parentId;
            const parentIdNum = parseInt(parentId);
            console.log('Checking parent ID:', parentId, '(parsed:', parentIdNum, ') for theme:', current.primaryName);
            console.log('Parent ID checks:', {
                exists: !!parentId,
                notZero: parentId !== 0,
                notNaN: !isNaN(parentIdNum),
                inMap: byId.has(parentIdNum)
            });
            
            if (parentId && parentId !== 0 && !isNaN(parentIdNum) && byId.has(parentIdNum)) {
                console.log('Adding ancestor:', parentIdNum);
                ancestors.add(parentIdNum);
                current = byId.get(parentIdNum);
                console.log('Moving to parent theme:', current ? current.primaryName : 'Not found');
            } else {
                console.log('No more parents found - stopping ancestor search');
                console.log('Available theme IDs in map:', Array.from(byId.keys()));
                break;
            }
        }
        
        // Find all descendants (children, grandchildren, great-grandchildren, etc.) of the current theme
        const descendants = new Set();
        function findDescendants(id) {
            console.log('Finding descendants for theme ID:', id);
            const children = themes.filter(t => parseInt(t.parentId) === parseInt(id));
            console.log('Found direct children:', children.length);
            
            children.forEach(child => {
                const childId = parseInt(child.id);
                console.log('Adding descendant:', childId, child.primaryName);
                descendants.add(childId);
                findDescendants(childId); // Recursively find all descendants
            });
        }
        findDescendants(currentId);
        
        // Find siblings (other children of the same parent) and their descendants
        if (currentTheme && currentTheme.parentId) {
            const parentId = parseInt(currentTheme.parentId);
            console.log('Finding siblings for parent ID:', parentId);
            const siblings = themes.filter(t => {
                const tParentId = parseInt(t.parentId);
                const tId = parseInt(t.id);
                return tParentId === parentId && tId !== currentId;
            });
            console.log('Found siblings:', siblings.length);
            
            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                console.log('Adding sibling and its descendants:', siblingId, sibling.primaryName);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }
        
        // Include the current theme, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([currentId, ...ancestors, ...descendants]);
        
        console.log('Ancestors found:', ancestors.size);
        console.log('Descendants found:', descendants.size);
        console.log('Total included IDs:', includedIds.size);
        console.log('Included IDs:', Array.from(includedIds));
        
        // Filter themes to include only the complete family tree
        return themes.filter(t => {
            const id = parseInt(t.id);
            const isIncluded = includedIds.has(id);
            if (isIncluded) {
                console.log('Including theme in hierarchy:', id, t.primaryName);
            }
            return isIncluded;
        });
    }

    function buildHierarchyTree(themes, rootId) {
        const byParent = new Map();
        const byId = new Map();
        themes.forEach(t => {
            const id = parseInt(t.id);
            const parentId = t.parentId ? parseInt(t.parentId) : null;
            byId.set(id, t);
            if (!byParent.has(parentId)) {
                byParent.set(parentId, []);
            }
            byParent.get(parentId).push(t);
        });
        
        console.log('buildHierarchyTree - Root ID:', rootId);
        console.log('buildHierarchyTree - Themes by parent:', byParent);
        
        const rows = [];
        function buildRows(themeId, depth = 0) {
            console.log('buildRows - Looking for theme ID:', themeId, 'at depth:', depth);
            
            // First, add the current theme itself (if it exists)
            const currentTheme = byId.get(themeId);
            if (currentTheme) {
                const childCount = (byParent.get(themeId) || []).length;
                console.log('buildRows - Adding current theme:', currentTheme.primaryName, 'with', childCount, 'children');
                
                rows.push({
                    node: currentTheme,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                // Then add its children
                const children = byParent.get(themeId) || [];
                console.log('buildRows - Found children:', children.length);
                
                children.forEach(theme => {
                    buildRows(parseInt(theme.id), depth + 1);
                });
            } else {
                // If no current theme, just process children (for null parent case)
                const children = byParent.get(themeId) || [];
                console.log('buildRows - No current theme, processing', children.length, 'children');
                
                children.forEach(theme => {
                    buildRows(parseInt(theme.id), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        console.log('buildHierarchyTree - Starting with root ID:', rootIdNum);
        console.log('buildHierarchyTree - Available parent keys:', Array.from(byParent.keys()));
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    function renderRegulatoryThemeTable(hierarchyRows, currentId) {
        const table = document.createElement('div');
        table.className = 'view-section';
        
        // Debug: Log the hierarchy rows data
        console.log('Rendering regulatory theme hierarchy rows:', hierarchyRows);
        console.log('Current ID:', currentId);
        
        const formatDate = (dateString) => {
            if (!dateString) return '';
            try {
                return new Date(dateString).toLocaleDateString();
            } catch (error) {
                return dateString;
            }
        };

        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const ph = isMaskedNode ? Mask.PLACEHOLDER : null;
            const name = isMaskedNode ? ph : (node.primaryName || 'Unnamed Theme');
            const desc = isMaskedNode ? ph : (node.description || '');
            const lastUpdated = isMaskedNode ? ph : formatDate(node.lastUpdateDatetime);
            const isCurrent = String(node.id) === String(currentId);
            const id = node.id;
            const parentId = node.parentId || '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'theme-link current-theme-link' : 'theme-link';
            const link = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/regulatory-theme/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            const refNumber = isMaskedNode
                ? `<span class="theme-ref">(${escapeHtml(ph)})</span>`
                : (node.refNumber ? `<span class="theme-ref">(${escapeHtml(node.refNumber)})</span>` : '');
            const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();
            
            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-file-contract item-icon"></i><span class="theme-name">${link}</span>${refNumber}${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
                <td><span title="${lastUpdated}">${lastUpdated}</span></td>
            </tr>`;
        }).join('');

        const hierT = (k) => (window.I18n && window.I18n.t(k)) || k;
        table.innerHTML = `
            <div class="section-title">${hierT('regulatoryTheme.sections.hierarchy')}</div>
            <div class="hierarchy-table-wrapper">
                <table class="hierarchy-table">
                    <thead>
                        <tr>
                            <th>${hierT('regulatoryTheme.labels.regulatoryTheme')}</th>
                            <th>${hierT('regulatoryTheme.labels.description')}</th>
                            <th>${hierT('regulatoryTheme.labels.lastUpdated')}</th>
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

    async function loadRegulationsTab(container, themeId) {
        try {
            console.log('Loading regulations for regulatory theme ID:', themeId);
            
            // Show loading state
            const loadRegMsg = (window.I18n && window.I18n.t('regulatoryTheme.messages.loadingRegulations')) || 'Loading regulations...';
            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1; padding: 2rem; text-align: center;">
                    <i class="fas fa-spinner fa-spin" style="font-size: 2rem; color: var(--secondary-color, #248567); margin-bottom: 1rem;"></i>
                    <p>${loadRegMsg}</p>
                </div>
            `;
            
            // Fetch regulations data
            const response = await fetch(`/api/regulation-x-regulatorytheme/regulatory-theme/${themeId}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const regulations = await response.json();
            console.log('Regulations loaded:', regulations);
            
            if (!Array.isArray(regulations) || regulations.length === 0) {
                const noRegMsg = (window.I18n && window.I18n.t('regulatoryTheme.messages.noRegulationsFound')) || 'No regulations found for this regulatory theme.';
                container.innerHTML = `
                    <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                        <i class="fas fa-balance-scale" style="font-size: 3rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                        <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                            ${noRegMsg}
                        </p>
                    </div>
                `;
                return;
            }
            
            // Build regulations table
            const regT = (k) => (window.I18n && window.I18n.t(k)) || k;
            const regulationsTableHtml = `
                <div class="view-section">
                    <div class="section-title">${regT('regulatoryTheme.sections.regulations')}</div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>${regT('regulatoryTheme.labels.rootRegulation')}</th>
                                    <th>${regT('regulatoryTheme.labels.ref')}</th>
                                    <th>${regT('regulatoryTheme.labels.regulation')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${regulations.map(reg => {
                                    const regulation = reg.regulation || 'Unnamed Regulation';
                                    const refNumber = reg.refNumber || '—';
                                    
                                    // Show parent regulation as root regulation, or the regulation itself if no parent (without ref numbers)
                                    const parentRegulation = reg.parentRegulation;
                                    
                                    let rootRegulationDisplay;
                                    if (parentRegulation && parentRegulation.trim() !== '') {
                                        // Has parent - show parent name only
                                        rootRegulationDisplay = escapeHtml(parentRegulation);
                                    } else {
                                        // No parent - show the regulation itself as the root
                                        rootRegulationDisplay = escapeHtml(regulation);
                                    }
                                    
                                    // Make ref number clickable
                                    const refLink = reg.regulationId ? 
                                        `<a href="/view/regulation/${reg.regulationId}" class="regulation-link">${escapeHtml(refNumber)}</a>` : 
                                        escapeHtml(refNumber);
                                    
                                    return `
                                        <tr>
                                            <td>${rootRegulationDisplay}</td>
                                            <td>${refLink}</td>
                                            <td>${escapeHtml(regulation)}</td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                    <div class="table-footer">
                        ${regulations.length} record${regulations.length !== 1 ? 's' : ''}
                    </div>
                </div>
            `;
            
            container.innerHTML = regulationsTableHtml;
            
        } catch (error) {
            console.error('Error loading regulations:', error);
            const errTitle = (window.I18n && window.I18n.t('regulatoryTheme.messages.errorLoadingRegulations')) || 'Error Loading Regulations';
            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center; color: var(--danger, #dc3545);">
                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 1rem; display: block;"></i>
                    <h3>${errTitle}</h3>
                    <p>${(window.I18n && window.I18n.t('message.failedToLoad')) || 'Failed to load'} regulations data: ${error.message}</p>
                </div>
            `;
        }
    }

    function initRegulatoryThemeInteractions(containerEl, hierarchyRows) {
        const parentMap = hierarchyRows.parentMap;
        
        // Handle expand/collapse functionality
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
                const isExpanded = icon.classList.contains('fa-caret-down');
                icon.classList.toggle('fa-caret-down', !isExpanded);
                icon.classList.toggle('fa-caret-right', isExpanded);
                
                // Find all child rows
                let nextRow = row.nextElementSibling;
                while (nextRow && parseInt(nextRow.dataset.depth) > currentDepth) {
                    const childDepth = parseInt(nextRow.dataset.depth);
                    
                    if (childDepth === currentDepth + 1) {
                        // Direct children - toggle visibility
                        nextRow.style.display = isExpanded ? 'none' : '';
                        
                        // If collapsing, also collapse any expanded grandchildren
                        if (isExpanded) {
                            const childExpander = nextRow.querySelector('.tree-expander i');
                            if (childExpander && childExpander.classList.contains('fa-caret-down')) {
                                childExpander.classList.remove('fa-caret-down');
                                childExpander.classList.add('fa-caret-right');
                            }
                        }
                    } else if (childDepth > currentDepth + 1) {
                        // Grandchildren and beyond - hide when collapsing
                        if (isExpanded) {
                            nextRow.style.display = 'none';
                        }
                    }
                    
                    nextRow = nextRow.nextElementSibling;
                }
            }
        });
        
        // Handle row clicks for navigation (but not on expander)
        containerEl.addEventListener('click', function(e) {
            if (!e.target.closest('.tree-expander')) {
                const row = e.target.closest('tr');
                if (row) {
                    const themeId = row.dataset.id;
                    if (themeId) {
                        window.location.href = `/view/regulatory-theme/${themeId}`;
                    }
                }
            }
        });
    }

    // Load regulatory theme hierarchy data
    async function loadRegulatoryThemeHierarchy(themeId) {
        const tbody = document.getElementById('hierarchyTbody');
        const footer = document.getElementById('hierarchyFooter');

        if (!tbody || !footer) return;

        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading regulatory theme hierarchy...</td></tr>';
            footer.textContent = 'Loading...';

            // Fetch all regulatory themes
            console.log('Fetching regulatory themes from /api/regulatory-theme/hierarchy');
            const response = await fetch('/api/regulatory-theme/hierarchy');
            console.log('Response status:', response.status, response.statusText);
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const themes = await response.json();
            console.log('Regulatory themes loaded for hierarchy:', themes);
            console.log('Themes type:', typeof themes, 'Is array:', Array.isArray(themes));
            console.log('Themes length:', themes ? themes.length : 'null/undefined');
            
            // Debug: Log all themes with their IDs and parent IDs
            themes.forEach((theme, index) => {
                console.log(`Theme ${index + 1}: ID=${theme.id}, Name="${theme.primaryName}", ParentID=${theme.parentId}`);
            });
            
            // Debug: Check for parent-child relationships
            const themesWithParents = themes.filter(t => t.parentId && t.parentId !== 0);
            console.log('Themes with parent relationships:', themesWithParents.length);
            if (themesWithParents.length > 0) {
                console.log('Example theme with parent:', themesWithParents[0]);
                console.log('All parent-child relationships:');
                themesWithParents.forEach(theme => {
                    const parent = themes.find(p => p.id == theme.parentId);
                    console.log(`- ${theme.primaryName} (ID: ${theme.id}) -> Parent: ${parent ? parent.primaryName : 'NOT FOUND'} (ID: ${theme.parentId})`);
                });
            }
            
            // Debug: Check if current theme has a parent
            const currentTheme = themes.find(t => String(t.id) === String(themeId));
            if (currentTheme) {
                console.log('Current theme:', currentTheme.primaryName, 'ID:', currentTheme.id, 'Parent ID:', currentTheme.parentId);
                if (currentTheme.parentId) {
                    const parent = themes.find(p => p.id == currentTheme.parentId);
                    console.log('Current theme parent found:', parent ? parent.primaryName : 'NOT FOUND');
                }
            }
            
            if (!Array.isArray(themes) || themes.length === 0) {
                console.log('No themes found or themes is not an array');
                tbody.innerHTML = '<tr><td colspan="3" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No regulatory themes found in database</td></tr>';
                footer.textContent = '0 records';
                return;
            }

            // Build hierarchy tree showing current theme and its relationships
            console.log('Building hierarchy tree for theme ID:', themeId);
            
            // Simple test: manually find parent and children
            const testCurrentTheme = themes.find(t => String(t.id) === String(themeId));
            const testParentTheme = testCurrentTheme && testCurrentTheme.parentId ? themes.find(t => String(t.id) === String(testCurrentTheme.parentId)) : null;
            const testChildThemes = themes.filter(t => String(t.parentId) === String(themeId));
            
            console.log('=== SIMPLE RELATIONSHIP TEST ===');
            console.log('Current theme:', testCurrentTheme ? testCurrentTheme.primaryName : 'Not found');
            console.log('Parent theme:', testParentTheme ? testParentTheme.primaryName : 'None');
            console.log('Child themes:', testChildThemes.map(c => c.primaryName));
            console.log('=== END TEST ===');
            
            const filteredThemes = buildDirectLineageTree(themes, themeId);
            console.log('Filtered themes for hierarchy:', filteredThemes);
            console.log('Filtered themes count:', filteredThemes.length);
            
            // Always show the hierarchy if we have themes, even if it's just the current theme
            if (filteredThemes.length === 0) {
                // If no hierarchy found, show all themes with their relationships
                console.log('No hierarchy found, showing all themes with relationships');
                
                // Find themes that have this theme as parent (children)
                const fallbackChildThemes = themes.filter(t => {
                    const parentId = t.parentId;
                    return parentId && String(parentId) === String(themeId);
                });
                
                // Find themes that are parents of this theme
                const fallbackCurrentTheme = themes.find(t => String(t.id) === String(themeId));
                const fallbackParentThemes = [];
                if (fallbackCurrentTheme) {
                    const parentId = fallbackCurrentTheme.parentId;
                    if (parentId) {
                        const parentTheme = themes.find(t => String(t.id) === String(parentId));
                        if (parentTheme) {
                            fallbackParentThemes.push(parentTheme);
                        }
                    }
                }
                
                // Create hierarchy with current theme, its parents, and its children
                const allRelatedThemes = [...fallbackParentThemes, fallbackCurrentTheme, ...fallbackChildThemes].filter(Boolean);
                
                if (allRelatedThemes.length > 0) {
                    const hierarchyRows = {
                        rows: allRelatedThemes.map((theme, index) => {
                            const isCurrent = String(theme.id) === String(themeId);
                            const isParent = fallbackParentThemes.includes(theme);
                            const isChild = fallbackChildThemes.includes(theme);
                            
                            return {
                                node: theme,
                                depth: isParent ? 0 : (isCurrent ? 1 : 2),
                                childCount: isChild ? 0 : (isCurrent ? fallbackChildThemes.length : 0),
                                hasChildren: isCurrent && fallbackChildThemes.length > 0
                            };
                        }),
                        parentMap: new Map()
                    };
                    
                    const html = renderRegulatoryThemeTable(hierarchyRows, themeId);
                    const container = tbody.closest('.view-section');
                    if (container) {
                        container.outerHTML = html;
                        const newContainer = document.querySelector('.view-section');
                        if (newContainer) {
                            initRegulatoryThemeInteractions(newContainer, hierarchyRows);
                        }
                    }
                    footer.textContent = `${allRelatedThemes.length} record${allRelatedThemes.length !== 1 ? 's' : ''} (related themes)`;
                    return;
                } else {
                    // Show all themes as flat list if no relationships found
                    const hierarchyRows = {
                        rows: themes.map(theme => ({
                            node: theme,
                            depth: 0,
                            childCount: 0,
                            hasChildren: false
                        })),
                        parentMap: new Map()
                    };
                    const html = renderRegulatoryThemeTable(hierarchyRows, themeId);
                    const container = tbody.closest('.view-section');
                    if (container) {
                        container.outerHTML = html;
                        const newContainer = document.querySelector('.view-section');
                        if (newContainer) {
                            initRegulatoryThemeInteractions(newContainer, hierarchyRows);
                        }
                    }
                    footer.textContent = `${themes.length} record${themes.length !== 1 ? 's' : ''} (all themes)`;
                    return;
                }
            }
            
            // Find the root of the filtered tree (the topmost ancestor)
            const rootTheme = filteredThemes.find(theme => {
                const parentId = theme.parentId;
                return !parentId || parentId === 0 || parentId === null;
            });
            
            console.log('Root theme found:', rootTheme ? rootTheme.primaryName : 'None');
            console.log('Root theme details:', rootTheme);
            
            // If no root found in filtered themes, use the current theme as root
            const rootId = rootTheme ? rootTheme.id : themeId;
            console.log('Using root ID:', rootId);
            const hierarchyRows = buildHierarchyTree(filteredThemes, rootId);
            const html = renderRegulatoryThemeTable(hierarchyRows, themeId);
            
            // Update the container with the new HTML
            const container = tbody.closest('.view-section');
            if (container) {
                container.outerHTML = html;
                const newContainer = document.querySelector('.view-section');
                if (newContainer) {
                    initRegulatoryThemeInteractions(newContainer, hierarchyRows);
                }
            }

            footer.textContent = `${filteredThemes.length} record${filteredThemes.length !== 1 ? 's' : ''}`;
        } catch (e) {
            console.error('Failed to load regulatory theme hierarchy:', e);
            const failedMsg = (window.I18n && window.I18n.t('message.failedToLoad')) || 'Failed to load';
            tbody.innerHTML = `<tr><td colspan="3" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">${failedMsg} hierarchy data: ${e.message || (window.I18n && window.I18n.t('message.error')) || 'Unknown error'}</td></tr>`;
            footer.textContent = (window.I18n && window.I18n.t('message.zeroRecords')) || '0 records';
        }
    }

    // Make functions globally available
    window.loadRegulatoryThemeHierarchy = loadRegulatoryThemeHierarchy;
})();

