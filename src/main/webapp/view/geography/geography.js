(function() {
    function parseId() {
        // Primary: support query param pattern /geography.html?id=123
        try {
            const params = new URLSearchParams(window.location.search);
            const qId = params.get('id');
            if (qId) {
                const n = parseInt(qId, 10);
                if (!Number.isNaN(n)) return n;
            }
        } catch(_) {}

        // Fallback: support route pattern /view/geography/123
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('geography');
        if (idx !== -1 && parts.length >= idx + 2) {
            const id = parseInt(parts[idx + 1], 10);
            return Number.isNaN(id) ? null : id;
        }
        return null;
    }

    let _geographyGuestCache = null;
    async function isGuestVisitorForGeography() {
        if (_geographyGuestCache !== null) {
            return _geographyGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _geographyGuestCache = true;
                return _geographyGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _geographyGuestCache = !isAuthenticated || isGuestRole;
            return _geographyGuestCache;
        } catch (_) {
            _geographyGuestCache = true;
            return _geographyGuestCache;
        }
    }

    function renderGeographyGuestAccessDenied(container, geographyId, segmentName, segmentId) {
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
            // Use the new permissions API to check user permissions for Geography module
            const permResp = await fetch('/api/user/permissions/Geography', { method: 'GET', credentials: 'include' });
            if (!permResp.ok) { 
                console.log('API /api/user/permissions/Geography failed:', permResp.status);
                // Fallback to old method
                await hideEditsIfUnauthenticatedFallback();
                return; 
            }
            const perms = await permResp.json();
            console.log('User permissions for Geography:', perms);
            
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
            '#deleteGeographyBtn'
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

    // Helper function to format date
    function formatDate(dateString) {
        if (!dateString) return '';
        try {
            return new Date(dateString).toLocaleDateString('en-GB');
        } catch (error) {
            return dateString;
        }
    }

    async function loadImpact(id) {
        const container = document.getElementById('geographyImpactContainer');
        if (!container) {
            console.error('geographyImpactContainer not found');
            return;
        }
        
        // Show loading state
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading impact data...</div>';
        
        // Wait a bit for scripts to load if needed, then try to load impact
        let retries = 0;
        const maxRetries = 10;
        
        while (retries < maxRetries) {
            if (window.loadGeographyImpact && typeof window.loadGeographyImpact === 'function') {
                try {
                    console.log('Loading geography impact for ID:', id);
                    await window.loadGeographyImpact(id);
                    return;
                } catch (error) {
                    console.error('Error loading geography impact:', error);
                    container.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                            <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                            <p>Error loading impact data: ${error.message}</p>
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
        console.error('loadGeographyImpact function not available after', maxRetries, 'retries');
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                <p>Impact view script not loaded. Please refresh the page.</p>
            </div>
        `;
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
            // Fetch geography data
            const response = await fetch(`/api/geography/${id}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            const geography = await response.json();

            try {
                const isGuest = await isGuestVisitorForGeography();
                const segmentName = geography?.segmentName || geography?.segment_name || geography?.segment;
                const segmentId = geography?.segmentId ?? geography?.segment_id ?? geography?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Geography] Guest visitor blocked from non-Enterprise segment view', { geographyId: id, segmentName, segmentId });
                    renderGeographyGuestAccessDenied(container, id, segmentName, segmentId);
                    if (titleElement) {
                        titleElement.removeAttribute('data-i18n');
                        titleElement.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                    }
                    document.title = 'BUDG';
                    return;
                }
            } catch (gateError) {
                console.warn('[Geography] Error during guest/segment access check, falling back to normal render:', gateError);
            }

            // Update header with geography data - ensure it's always updated
            if (titleElement) {
                const geographyName = geography.primaryName || geography.PrimaryName || geography.name || geography.Name || 'Unnamed Geography';
                // Remove data-i18n attribute to prevent i18n from overwriting
                titleElement.removeAttribute('data-i18n');
                titleElement.textContent = escapeHtml(geographyName);
                // Also update page title
                document.title = `BUDG - ${geographyName}`;
                
                // Stop observing if observer was set
                if (window._geographyTitleObserver) {
                    window._geographyTitleObserver.disconnect();
                    delete window._geographyTitleObserver;
                }
            }
            if (breadcrumbElement) {
                breadcrumbElement.innerHTML = 'Geography';
            }

            // Get additional reference data
            let lookupData = {};
            
            // Load parent geographies for parent name resolution
            try {
                const parentResponse = await fetch('/api/geography/parent-picker');
                if (parentResponse.ok) {
                    lookupData.parentGeographies = await parentResponse.json();
                }
            } catch (error) {
                console.log('Could not load parent geographies:', error);
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

            // Get parent geography name if ParentID exists
            let parentGeographyName = '';
            const parentId = geography.parentId || geography.ParentID;
            if (parentId && lookupData.parentGeographies) {
                const parentGeo = lookupData.parentGeographies.find(p => (p.id === parentId) || (p.ID === parentId));
                if (parentGeo) {
                    const parentName = parentGeo.primaryname || parentGeo.PrimaryName || parentGeo.name || parentGeo.Name || '';
                    parentGeographyName = `<a href="/view/geography/${parentGeo.id || parentGeo.ID}" class="parent-geography-link">${escapeHtml(parentName)}</a>`;
                }
            }

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

            const definitionSection = `
                <div class="view-section">
                    <div class="section-title">DEFINITION</div>
                    <div class="view-item"><div class="view-value" style="flex:1">${_richHtml(geography.description || geography.Description || '')}</div></div>
                </div>
            `;

            // Classifications sidebar
            const classificationsSection = `
                <div class="view-section sidebar-section">
                    <div class="section-title">CLASSIFICATIONS</div>
                    
                    <div class="classifications-group">
                        <div class="group-title">OTHER INFORMATION</div>
                        <div class="classification-item">
                            <span class="classification-label">Created:</span>
                            <span class="classification-value">${escapeHtml(
                                geography.createDateTime || geography.CreateDatetime ? 
                                formatDate(geography.createDateTime || geography.CreateDatetime) : 
                                formatDate(new Date().toISOString())
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">Last Updated By:</span>
                            <span class="classification-value">${(() => {
                                // Try multiple field name variations - check for null explicitly
                                let uid = null;
                                if (geography.lastUpdateUserId != null && geography.lastUpdateUserId !== undefined) {
                                    uid = geography.lastUpdateUserId;
                                } else if (geography.LastUpdate_UserID != null && geography.LastUpdate_UserID !== undefined) {
                                    uid = geography.LastUpdate_UserID;
                                } else if (geography.last_update_user_id != null && geography.last_update_user_id !== undefined) {
                                    uid = geography.last_update_user_id;
                                } else if (geography.LastUpdateUserId != null && geography.LastUpdateUserId !== undefined) {
                                    uid = geography.LastUpdateUserId;
                                } else if (geography.Last_Update_User_ID != null && geography.Last_Update_User_ID !== undefined) {
                                    uid = geography.Last_Update_User_ID;
                                }
                                
                                // If LastUpdate_UserID is null, it means the geography hasn't been updated yet
                                if (uid == null || uid === undefined) {
                                    return '—';
                                }
                                
                                // Try to get name from API response
                                let name = null;
                                if (geography.lastUpdatedByName != null && geography.lastUpdatedByName !== undefined && String(geography.lastUpdatedByName).trim() !== '') {
                                    name = String(geography.lastUpdatedByName).trim();
                                } else if (geography.LastUpdatedByName != null && geography.LastUpdatedByName !== undefined && String(geography.LastUpdatedByName).trim() !== '') {
                                    name = String(geography.LastUpdatedByName).trim();
                                } else if (geography.last_updated_by_name != null && geography.last_updated_by_name !== undefined && String(geography.last_updated_by_name).trim() !== '') {
                                    name = String(geography.last_updated_by_name).trim();
                                } else if (geography.Last_Updated_By_Name != null && geography.Last_Updated_By_Name !== undefined && String(geography.Last_Updated_By_Name).trim() !== '') {
                                    name = String(geography.Last_Updated_By_Name).trim();
                                }
                                
                                // If name is provided from API, use it with link
                                if (name && name !== 'null' && name !== 'undefined') {
                                    return `<a href="/view/people/${uid}" class="user-link">${escapeHtml(name)}</a>`;
                                }
                                
                                // Fallback to resolving from people array (like glossary does)
                                const nm = resolveUserNameWithFallback(uid);
                                if (nm && nm !== '—' && nm.trim() !== '') {
                                    return `<a href="/view/people/${uid}" class="user-link">${escapeHtml(nm)}</a>`;
                                }
                                
                                // If we have uid but no name, show link with "Unknown User" (like glossary does)
                                if (uid) {
                                    return `<a href="/view/people/${uid}" class="user-link">Unknown User</a>`;
                                }
                                
                                return '—';
                            })()}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">Last Updated:</span>
                            <span class="classification-value">${escapeHtml(
                                (() => {
                                    const dateValue = geography.lastUpdateDateTime || geography.LastUpdateDatetime || geography.lastUpdateDatetime || geography.last_update_datetime;
                                    if (dateValue) {
                                        return formatDate(dateValue);
                                    }
                                    // If no last update date, use create date as fallback
                                    const createDate = geography.createDateTime || geography.CreateDatetime || geography.createDatetime || geography.create_datetime;
                                    if (createDate) {
                                        return formatDate(createDate);
                                    }
                                    return '—';
                                })()
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">Segment:</span>
                            <span class="classification-value">${escapeHtml(geography.segmentName || geography.segment_name || geography.segment || ((geography.segmentId ?? geography.segment_id ?? geography.Segment_ID) != null ? `ID ${geography.segmentId ?? geography.segment_id ?? geography.Segment_ID}` : (window.I18n?.t('message.notSpecified') || 'Not specified')))}</span>
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
                        facetId: 'Geography',
                        containerId: 'glossaryViewContainer',
                        objectId: id,
                        title: 'CUSTOM FIELDS'
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

        } catch (e) {
            console.error('Error loading geography:', e);
            const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;
            const backToCreate = t('geography.page.backToCreate');
            
            // Update title to show error state
            if (titleElement) {
                if (e.message && e.message.includes('404')) {
                    titleElement.textContent = 'Geography Not Found';
                } else {
                    titleElement.textContent = 'Error Loading Geography';
                }
                document.title = 'BUDG - Error';
            }
            
            if (e.message && e.message.includes('404')) {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('geography.page.notFound')}</h3>
                        <p>${t('geography.page.doesNotExist', { id })}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/geography.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('geography.messages.errorLoading')}</h3>
                        <p>${(window.I18n && window.I18n.t('message.failedToLoad')) || 'Failed to load'} geography (id=${id}). ${e.message || (window.I18n && window.I18n.t('message.error')) || 'Unknown error occurred.'}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/geography.html'">
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
        console.log('Geography page loaded');
        
        // Initialize title element immediately to avoid showing static text
        const titleElement = document.getElementById('glossaryTitle');
        if (titleElement) {
            // Remove data-i18n to prevent i18n from overwriting
            titleElement.removeAttribute('data-i18n');
            titleElement.textContent = 'Loading...';
            
            // Watch for i18n trying to overwrite and prevent it
            const observer = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    if (mutation.type === 'childList' || mutation.type === 'characterData') {
                        // If i18n tries to set a static text, restore Loading... or actual title
                        const currentText = titleElement.textContent;
                        if (currentText && currentText.trim() !== '' && currentText !== 'Loading...') {
                            // Title was set by load() function, keep it
                            return;
                        }
                        // If it's empty or was reset, set back to Loading...
                        if (!currentText || currentText.trim() === '') {
                            titleElement.textContent = 'Loading...';
                        }
                    }
                });
            });
            
            observer.observe(titleElement, {
                childList: true,
                characterData: true,
                subtree: true
            });
            
            // Stop observing after data is loaded (will be stopped in load() function)
            window._geographyTitleObserver = observer;
        }
        
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
                        'summary': 'summary'
                    };
                    
                    const targetTab = tabMapping[currentTab] || 'summary';
                    
                    // Navigate to edit page with tab parameter
                    window.location.href = `/view/geography/geography-edit.html?id=${id}&tab=${targetTab}`;
                }
            });
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
                        window.location.href = `/view/geography/geography-edit.html?id=${id}&tab=${tabName}`;
                    } else {
                        // For summary tab, just pass id
                        window.location.href = `/view/geography/geography-edit.html?id=${id}`;
                    }
                }
            });
        }
        
        const id = parseId();
        if (id != null) {
            load(id).then(async () => {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('geography', id);
                }
            });
            try { 
                window.BUDG_API_SERVICE?.logVisit({ 
                    entity: 'Geography', 
                    entityId: String(id), 
                    route: `/view/geography/${id}` 
                }); 
            } catch(_) {}
        }

        // Tab functionality
        const tabs = document.querySelectorAll('.tab-container .tab');
        const summaryContainer = document.getElementById('glossaryViewContainer');
        const impactContainer = document.getElementById('geographyImpactContainer');
        const historyContainer = document.getElementById('geographyHistoryContainer');
        
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                console.log('Tab clicked:', tabName);

                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                this.classList.add('active');

                // Get geography ID
                const geographyId = parseId();

                // Hide all containers
                if (summaryContainer) summaryContainer.style.display = 'none';
                if (impactContainer) impactContainer.style.display = 'none';
                if (historyContainer) historyContainer.style.display = 'none';

                // Handle tab content
                const container = document.getElementById('glossaryViewContainer');
                if (!container) return;

                // Tab-specific content handling
                switch (tabName) {
                    case 'summary':
                        // Show summary - reload geography
                        if (summaryContainer) summaryContainer.style.display = '';
                        if (geographyId) {
                            load(geographyId);
                        }
                        break;

                    case 'impact':
                        // Show impact tab
                        if (impactContainer) {
                            impactContainer.style.display = '';
                            // Always reload impact data when tab is clicked (in case data changed)
                            if (geographyId) {
                                loadImpact(geographyId).then(() => {
                                    impactContainer.dataset.loaded = '1';
                                }).catch(error => {
                                    console.error('Error in loadImpact:', error);
                                    impactContainer.innerHTML = `
                                        <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                                            <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                                            <p>Error loading impact data. Please refresh the page.</p>
                                        </div>
                                    `;
                                });
                            } else {
                                impactContainer.innerHTML = `
                                    <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                                        <p>Geography ID not found. Please navigate from a geography list page.</p>
                                    </div>
                                `;
                            }
                        } else {
                            console.error('Impact container not found');
                        }
                        break;

                    case 'history':
                        // Show history tab with History Component
                        if (historyContainer) {
                            historyContainer.style.display = '';
                            historyContainer.innerHTML = '';
                            // Use the history component
                            if (typeof createHistoryComponent === 'function') {
                                createHistoryComponent('Geography', geographyId, 'geographyHistoryContainer');
                            } else if (window.HistoryComponent) {
                                window.HistoryComponent.initialize('Geography', geographyId, 'geographyHistoryContainer');
                            } else {
                                console.error('createHistoryComponent is not available');
                                historyContainer.innerHTML = `
                                    <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center; color: var(--danger, #dc3545);">
                                        <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 1rem; display: block;"></i>
                                        <p>History component is not loaded. Please refresh the page.</p>
                                    </div>
                                `;
                            }
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
        
        // Initialize unified edit dropdown (id already declared above)
        if (window.EditDropdown && id != null) {
            try {
                window.EditDropdown.initialize('geography', id, {
                    container: '.tab-actions',
                    editUrl: `/view/geography/geography-edit.html?id=${id}`
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }
    });



    // Make functions globally available
})();

