(function() {
    function parseId() {
        // Primary: support query param pattern /regulator.html?id=123
        try {
            const params = new URLSearchParams(window.location.search);
            const qId = params.get('id');
            if (qId) {
                const n = parseInt(qId, 10);
                if (!Number.isNaN(n)) return n;
            }
        } catch(_) {}

        // Fallback: support route pattern /view/regulator/123
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('regulator');
        if (idx !== -1 && parts.length >= idx + 2) {
            const id = parseInt(parts[idx + 1], 10);
            return Number.isNaN(id) ? null : id;
        }
        return null;
    }

    let _regulatorGuestCache = null;
    async function isGuestVisitorForRegulator() {
        if (_regulatorGuestCache !== null) {
            return _regulatorGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _regulatorGuestCache = true;
                return _regulatorGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _regulatorGuestCache = !isAuthenticated || isGuestRole;
            return _regulatorGuestCache;
        } catch (_) {
            _regulatorGuestCache = true;
            return _regulatorGuestCache;
        }
    }

    function renderRegulatorGuestAccessDenied(container, regulatorId, segmentName, segmentId) {
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
                    if (node.matches && node.matches('#editBtn2,#tabEditBtn,#editStakeholdersBtn,[data-action="edit"]')) {
                        found = true;
                    } else if (node.querySelector && node.querySelector('#editBtn2,#tabEditBtn,#editStakeholdersBtn,[data-action="edit"]')) {
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
            // Use the new permissions API to check user permissions for Regulator module
            const permResp = await fetch('/api/user/permissions/Regulator', { method: 'GET', credentials: 'include' });
            if (!permResp.ok) { 
                console.log('API /api/user/permissions/Regulator failed:', permResp.status);
                // Fallback to old method
                await hideEditsIfUnauthenticatedFallback();
                return; 
            }
            const perms = await permResp.json();
            console.log('User permissions for Regulator:', perms);
            
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
            '#deleteRegulatorBtn'
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

    async function load(id) {
        const container = document.getElementById('glossaryViewContainer');
        const titleElement = document.getElementById('glossaryTitle');
        const breadcrumbElement = document.getElementById('glossaryBreadcrumb');
        
        const t = (key, opts) => (window.I18n && window.I18n.t(key, opts)) || key;
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + (t('message.loading') || 'Loading...') + '</div>';
        
        try {
            // Fetch regulator data
            const response = await fetch(`/api/regulator/${id}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            const regulator = await response.json();

            try {
                const isGuest = await isGuestVisitorForRegulator();
                const segmentName = regulator?.segmentName || regulator?.segment_name || regulator?.segment;
                const segmentId = regulator?.segmentId ?? regulator?.segment_id ?? regulator?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Regulator] Guest visitor blocked from non-Enterprise segment view', { regulatorId: id, segmentName, segmentId });
                    renderRegulatorGuestAccessDenied(container, id, segmentName, segmentId);
                    if (titleElement) {
                        titleElement.removeAttribute('data-i18n');
                        titleElement.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                    }
                    document.title = 'BUDG';
                    return;
                }
            } catch (gateError) {
                console.warn('[Regulator] Error during guest/segment access check, falling back to normal render:', gateError);
            }

            // Update header with regulator data - ensure it's always updated
            if (titleElement) {
                const regulatorName = regulator.primaryName || regulator.PrimaryName || regulator.name || regulator.Name || 'Unnamed Regulator';
                // Remove data-i18n attribute to prevent i18n from overwriting
                titleElement.removeAttribute('data-i18n');
                titleElement.textContent = escapeHtml(regulatorName);
                // Also update page title
                document.title = `BUDG - ${regulatorName}`;
                
                // Stop observing if observer was set
                if (window._regulatorTitleObserver) {
                    window._regulatorTitleObserver.disconnect();
                    delete window._regulatorTitleObserver;
                }
            }
            if (breadcrumbElement) {
                breadcrumbElement.innerHTML = t('regulator.page.title');
            }

            // Load people to resolve LastUpdate_UserID → Name
            let lookupData = {};
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
                    <div class="section-title">${t('regulator.sections.definition')}</div>
                    <div class="view-item"><div class="view-value" style="flex:1">${_richHtml(regulator.description || regulator.Description || '')}</div></div>
                    ${renderItem(t('regulator.labels.shortNameLabel'), escapeHtml(regulator.shortName || regulator.ShortName || '') || '<span class="empty">' + (t('message.notSpecified') || 'Not specified') + '</span>')}
                </div>
            `;

            // Load geography relationships
            let geographyData = [];
            try {
                const geographyResponse = await fetch(`/api/regulator-x-geography/${id}`);
                if (geographyResponse.ok) {
                    geographyData = await geographyResponse.json();
                    console.log('Geography relationships loaded:', geographyData);
                }
            } catch (error) {
                console.log('Could not load geography relationships:', error);
            }

            // Geography section
            let geographyTableRows = '';
            if (geographyData && geographyData.length > 0) {
                geographyTableRows = geographyData.map(geo => `
                    <tr>
                        <td>${escapeHtml(geo.geographyName || 'Unknown Geography')}</td>
                        <td>${escapeHtml(geo.description || '')}</td>
                    </tr>
                `).join('');
            } else {
                const noGeo = (window.I18n && window.I18n.t('regulator.messages.noGeographyForRegulator')) || 'No geography found for this regulator';
                geographyTableRows = `
                    <tr>
                        <td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">
                            ${noGeo}
                        </td>
                    </tr>
                `;
            }

            const geographySection = `
                <div class="view-section">
                    <div class="section-title">${t('regulator.sections.geography')}</div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>${t('regulator.labels.geography')}</th>
                                    <th>${t('regulator.labels.description')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${geographyTableRows}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;

            // Classifications sidebar
            const classificationsSection = `
                <div class="view-section sidebar-section">
                    <div class="section-title">${t('regulator.sections.classifications')}</div>
                    
                    <div class="classifications-group">
                        <div class="group-title">${t('regulator.sections.otherInformation')}</div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulator.labels.created')}</span>
                            <span class="classification-value">${escapeHtml(
                                regulator.createDateTime || regulator.CreateDatetime ? 
                                formatDate(regulator.createDateTime || regulator.CreateDatetime) : 
                                formatDate(new Date().toISOString())
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulator.labels.lastUpdatedBy')}</span>
                            <span class="classification-value">${(() => {
                                const uid = regulator.lastUpdateUserId || regulator.LastUpdate_UserID;
                                
                                // If no user ID, return dash
                                if (!uid || uid === null || uid === undefined) {
                                    return '—';
                                }
                                
                                // Try to get name from API response
                                let name = null;
                                if (regulator.lastUpdatedByName != null && regulator.lastUpdatedByName !== undefined && String(regulator.lastUpdatedByName).trim() !== '') {
                                    name = String(regulator.lastUpdatedByName).trim();
                                } else if (regulator.LastUpdatedByName != null && regulator.LastUpdatedByName !== undefined && String(regulator.LastUpdatedByName).trim() !== '') {
                                    name = String(regulator.LastUpdatedByName).trim();
                                } else if (regulator.last_updated_by_name != null && regulator.last_updated_by_name !== undefined && String(regulator.last_updated_by_name).trim() !== '') {
                                    name = String(regulator.last_updated_by_name).trim();
                                } else if (regulator.Last_Updated_By_Name != null && regulator.Last_Updated_By_Name !== undefined && String(regulator.Last_Updated_By_Name).trim() !== '') {
                                    name = String(regulator.Last_Updated_By_Name).trim();
                                }
                                
                                // If name is provided from API, use it with link
                                if (name && name !== 'null' && name !== 'undefined') {
                                    return `<a href="/view/people/${uid}" class="user-link">${escapeHtml(name)}</a>`;
                                }
                                
                                // Fallback to resolveUserNameWithFallback if name not available
                                const nm = resolveUserNameWithFallback(uid);
                                return escapeHtml(nm || '—');
                            })()}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulator.labels.lastUpdated')}</span>
                            <span class="classification-value">${escapeHtml(
                                regulator.lastUpdateDateTime || regulator.LastUpdateDatetime ? 
                                formatDate(regulator.lastUpdateDateTime || regulator.LastUpdateDatetime) : 
                                formatDate(new Date().toISOString())
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulator.labels.segmentLabel')}</span>
                            <span class="classification-value">${escapeHtml(regulator.segmentName || regulator.segment_name || regulator.segment || ((regulator.segmentId ?? regulator.segment_id ?? regulator.Segment_ID) != null ? `ID ${regulator.segmentId ?? regulator.segment_id ?? regulator.Segment_ID}` : (t('message.notSpecified') || 'Not specified')))}</span>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = `
                <div class="glossary-container">
                    <div class="left-column">
                        ${definitionSection}
                        ${geographySection}
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
                        facetId: 'Regulator',
                        containerId: 'glossaryViewContainer',
                        objectId: id,
                        title: t('regulator.sections.customFields')
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }


        } catch (e) {
            console.error('Error loading regulator:', e);
            const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;
            const backToCreate = t('regulator.page.backToCreate');
            if (e.message.includes('404')) {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('regulator.messages.notFound')}</h3>
                        <p>${t('regulator.page.doesNotExist', { id })}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulator.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('regulator.messages.errorLoading')}</h3>
                        <p>${(window.I18n && window.I18n.t('message.failedToLoad')) || 'Failed to load'} regulator (id=${id}). ${e.message || (window.I18n && window.I18n.t('message.error')) || 'Unknown error occurred.'}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulator.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            }
        }
    }

    // Load regulator history merged with regulator X geography history
    async function loadRegulatorHistoryWithGeography(regulatorId) {
        try {
            // Initialize history component first
            if (typeof createHistoryComponent !== 'function') {
                throw new Error('createHistoryComponent is not available');
            }
            
            // Fetch regulator X geography relationships
            const geographyRelationsResponse = await fetch(`/api/regulator-x-geography/${regulatorId}`);
            let geographyRelations = [];
            if (geographyRelationsResponse.ok) {
                geographyRelations = await geographyRelationsResponse.json();
            }
            
            // Get module ID for Regulator
            const modulesResponse = await fetch('/api/modules');
            if (!modulesResponse.ok) {
                throw new Error('Failed to load modules');
            }
            const modulesData = await modulesResponse.json();
            const modules = modulesData.modules || [];
            
            // Fetch history for each regulator X geography relationship
            let geographyHistory = [];
            for (const relation of geographyRelations) {
                if (relation.ID || relation.id) {
                    const relationId = relation.ID || relation.id;
                    try {
                        // Try to find RegulatorXGeography module
                        const regXGeoModule = modules.find(m => {
                            const name = (m.primaryName || '').toLowerCase();
                            return name === 'regulatorxgeography' || name === 'regulator x geography' || 
                                   name === 'regulator_x_geography' || name === 'regulator-geography';
                        });
                        
                        if (regXGeoModule) {
                            const regXGeoModuleId = regXGeoModule.id;
                            const relationHistoryResponse = await fetch(`/api/history?module_id=${regXGeoModuleId}&object_id=${relationId}&page=1&limit=1000`);
                            if (relationHistoryResponse.ok) {
                                const relationHistoryData = await relationHistoryResponse.json();
                                // Handle API response structure: {success: true, data: [...]}
                                let relationHistory = [];
                                if (relationHistoryData.success && Array.isArray(relationHistoryData.data)) {
                                    relationHistory = relationHistoryData.data;
                                } else if (Array.isArray(relationHistoryData)) {
                                    relationHistory = relationHistoryData;
                                }
                                geographyHistory = geographyHistory.concat(relationHistory);
                            }
                        } else {
                            // If no module exists, try to query regulator_x_geography_audit_history directly
                            // This would require a backend endpoint, so for now we'll skip it
                            console.log('RegulatorXGeography module not found, skipping history for relation ID:', relationId);
                        }
                    } catch (error) {
                        console.log('Error fetching history for regulator X geography relation:', relationId, error);
                    }
                }
            }
            
            // Initialize history component with additional geography history data
            createHistoryComponent('Regulator', regulatorId, 'regulatorHistoryContainer', geographyHistory);
        } catch (error) {
            console.error('Error loading regulator history with geography:', error);
            // Fallback to standard history component
            if (typeof createHistoryComponent === 'function') {
                createHistoryComponent('Regulator', regulatorId, 'regulatorHistoryContainer');
            }
        }
    }

    // Tab switching logic
    document.addEventListener('DOMContentLoaded', function() {
        hideEditsIfUnauthenticated();
        console.log('Regulator page loaded');
        
        const backBtn = document.getElementById('backBtn');
        console.log('Back button found:', backBtn);
        
        if (backBtn) backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));

        // Initialize unified edit dropdown
        const id = parseId();
        if (window.EditDropdown && id != null) {
            try {
                window.EditDropdown.initialize('regulator', id, {
                    container: '.tab-actions',
                    editUrl: `/view/regulator/regulator-edit.html?id=${id}`
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
                        window.location.href = `/view/regulator/regulator-edit.html?id=${id}&tab=${tabName}`;
                    } else {
                        // For summary tab, just pass id
                        window.location.href = `/view/regulator/regulator-edit.html?id=${id}`;
                    }
                }
            });
        }
        
        // Load regulator (id already declared above)
        if (id != null) {
            load(id).then(async () => {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('regulator', id);
                }
            });
            try { 
                window.BUDG_API_SERVICE?.logVisit({ 
                    entity: 'Regulator', 
                    entityId: String(id), 
                    route: `/view/regulator/${id}` 
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

                // Get regulator ID
                const regulatorId = parseId();

                // Handle tab content
                const container = document.getElementById('glossaryViewContainer');
                if (!container) return;

                // Tab-specific content handling
                switch (tabName) {
                    case 'summary':
                        // Show summary - reload regulator
                        if (regulatorId) {
                            load(regulatorId);
                        }
                        break;

                    case 'history':
                        // Show history tab with History Component
                        container.innerHTML = '<div id="regulatorHistoryContainer"></div>';
                        // Use the history component with merged regulator X geography history
                        if (typeof createHistoryComponent === 'function') {
                            loadRegulatorHistoryWithGeography(regulatorId);
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



    // Make functions globally available
})();

