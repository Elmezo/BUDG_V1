(function() {
    function parseId() {
        // First try to get from URL parameters
        const urlParams = new URLSearchParams(window.location.search);
        const idFromParams = urlParams.get('id');
        if (idFromParams) {
            const id = parseInt(idFromParams, 10);
            return Number.isNaN(id) ? null : id;
        }
        
        // Fallback to path parsing
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('client');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

    let _clientGuestCache = null;
    async function isGuestVisitorForClient() {
        if (_clientGuestCache !== null) {
            return _clientGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _clientGuestCache = true;
                return _clientGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _clientGuestCache = !isAuthenticated || isGuestRole;
            return _clientGuestCache;
        } catch (_) {
            _clientGuestCache = true;
            return _clientGuestCache;
        }
    }

    function renderClientGuestAccessDenied(container, clientId, segmentName, segmentId) {
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

    function hideEditControls() {
        // Hide the edit dropdown for unauthorized users
        if (window.EditDropdown) {
            window.EditDropdown.hideEditControls();
        }
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) tabEditBtn.style.display = 'none';
        try {
            document.querySelectorAll('[data-action="edit"]').forEach(function(el){
                el.style.display = 'none';
            });
        } catch(_) {}
    }

    async function hideEditsIfUnauthenticated() {
        try {
            const id = parseId();
            if (!id) {
                console.log('[Client] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Client', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Client] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Client] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Client] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Client', { method: 'GET', credentials: 'include' });
                const canDelete = permResp.ok ? (await permResp.json()).canDelete : false; // Only Super Admins can delete
                
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
                // Fallback to old method if utility function not available
                await hideEditsIfUnauthenticatedFallback();
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
            const isAdmin = await checkAdminPermissions();
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
            '#deleteClientBtn'
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
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.style.display = '';
            tabEditBtn.style.setProperty('display', '', 'important');
        }
        try { 
            document.querySelectorAll('[data-action="edit"]').forEach(function(el){ 
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            }); 
        } catch(_) {}
        console.log('Edit controls shown for admin user');
    }

    function hideEditControls() {
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.style.display = 'none';
            tabEditBtn.style.setProperty('display', 'none', 'important');
        }
        try { 
            document.querySelectorAll('[data-action="edit"]').forEach(function(el){ 
                el.style.display = 'none';
                el.style.setProperty('display', 'none', 'important');
            }); 
        } catch(_) {}
        console.log('Edit controls hidden for non-admin user');
    }

    // Check if user has admin permissions
    async function checkAdminPermissions() {
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!meResp.ok) {
                console.log('API /api/me failed:', meResp.status);
                return false;
            }
            const me = await meResp.json();
            console.log('User data from /api/me:', me);
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            console.log('Normalized role:', role);
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            console.log('Is admin?', isAdmin);
            return isAdmin;
        } catch(e) {
            console.error('Error checking admin permissions:', e);
            return false;
        }
    }

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
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

    function formatDateTime(value) {
        if (!value) return '';
        try {
            const d = new Date(value);
            if (isNaN(d.getTime())) return escapeHtml(String(value));
            return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        } catch (_) {
            return escapeHtml(String(value));
        }
    }

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class="empty">-</span>'}</div></div>`;
    }

    function renderStatusBadge(status, isActive) {
        if (!status) return '<span class="empty">-</span>';
        const badgeClass = isActive ? 'status-active' : 'status-inactive';
        return `<span class="status-badge ${badgeClass}">${escapeHtml(status)}</span>`;
    }

    function renderLifecycleBadge(lifecycle) {
        if (!lifecycle) return '<span class="empty">-</span>';
        return `<span class="lifecycle-badge">${escapeHtml(lifecycle)}</span>`;
    }

    function renderPublicStatus(viewing) {
        if (!viewing) return '<span class="empty">-</span>';
        if (typeof viewing === 'string') {
            const viewingLower = viewing.toLowerCase();
            if (viewingLower.includes('public') || viewingLower.includes('open')) {
                return `<span class="status-badge status-active">${escapeHtml(viewing)}</span>`;
            } else if (viewingLower.includes('private') || viewingLower.includes('restricted') || viewingLower.includes('non-public')) {
                return `<span class="status-badge status-inactive">${escapeHtml(viewing)}</span>`;
            }
            return `<span class="status-badge status-active">${escapeHtml(viewing)}</span>`;
        }
        if (viewing === 1 || viewing === true || viewing === '1') {
            return '<span class="status-badge status-active">Public</span>';
        }
        return '<span class="status-badge status-inactive">Private</span>';
    }

    function renderUserLink(name, id) {
        if (!name) return '<span class="empty">-</span>';
        if (id) return `<a href="/view/people/${id}">${escapeHtml(name)}</a>`;
        return escapeHtml(name);
    }

    function renderDate(date) {
        if (!date) return '<span class="empty">-</span>';
        return formatDateTime(date);
    }

    async function resolveReferences(entity) {
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // Status name from status_id
        if (!entity.Status_Name && (entity.status_id || entity.Status_ID)) {
            const statusId = entity.status_id ?? entity.Status_ID;
            tasks.push((async () => {
                try {
                    let s = await api.getStatusById(statusId);
                    if (s && s.data) s = s.data;
                    entity.Status_Name = s?.primaryname || s?.Name || s?.name || String(statusId);
                } catch(_) { /* ignore */ }
            })());
        }

        // Lifecycle name from lifecycle_id
        if (!entity.Lifecycle_Name && (entity.lifecycle_id || entity.Lifecycle_ID)) {
            const lifecycleId = entity.lifecycle_id ?? entity.Lifecycle_ID;
            tasks.push((async () => {
                try {
                    let lifecycleList = await api.getClientLifecycleList();
                    if (lifecycleList && Array.isArray(lifecycleList)) {
                        const lifecycle = lifecycleList.find(l => l.id === lifecycleId);
                        entity.Lifecycle_Name = lifecycle?.name || lifecycle?.primaryname || lifecycle?.title || String(lifecycleId);
                    } else if (lifecycleList && lifecycleList.data && Array.isArray(lifecycleList.data)) {
                        const lifecycle = lifecycleList.data.find(l => l.id === lifecycleId);
                        entity.Lifecycle_Name = lifecycle?.name || lifecycle?.primaryname || lifecycle?.title || String(lifecycleId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // Viewing name from viewing_id
        if (!entity.Viewing_Name && (entity.viewing_id || entity.Viewing_ID)) {
            const viewingId = entity.viewing_id ?? entity.Viewing_ID;
            tasks.push((async () => {
                try {
                    let viewingList = await api.getViewingList();
                    if (viewingList && Array.isArray(viewingList)) {
                        const viewing = viewingList.find(v => v.id === viewingId);
                        entity.Viewing_Name = viewing?.name || viewing?.primaryname || viewing?.title || String(viewingId);
                    } else if (viewingList && viewingList.data && Array.isArray(viewingList.data)) {
                        const viewing = viewingList.data.find(v => v.id === viewingId);
                        entity.Viewing_Name = viewing?.name || viewing?.primaryname || viewing?.title || String(viewingId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // Last updated by person
        if (!entity.Last_Updated_By && (entity.lastupdateuser_ID || entity.last_updated_by)) {
            const userId = entity.lastupdateuser_ID ?? entity.last_updated_by;
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(userId);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    entity.Last_Updated_By = fullName || person?.Email || person?.email || String(userId);
                } catch(_) { /* ignore */ }
            })());
        }

        await Promise.all(tasks);
        return entity;
    }

    async function load(id) {
        const container = document.getElementById('clientViewContainer');
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading...</div>';
        try {
            // Check if API service is available
            if (!window.BUDG_API_SERVICE) {
                throw new Error('API service not available');
            }
            
            let entity = await window.BUDG_API_SERVICE.getClientById(id);
            try { 
                window.BUDG_API_SERVICE.logVisit({
                    entity: 'Client', 
                    entityId: String(id), 
                    route: `/view/client/${id}` 
                }).catch(error => {
                    console.warn('Failed to log visit (API error):', error);
                    // Don't throw - this is not critical for page functionality
                }); 
            } catch(error) {
                console.warn('Failed to log visit (service error):', error);
                // Don't throw - this is not critical for page functionality
            }
            if (entity && entity.data) entity = entity.data;

            try {
                const isGuest = await isGuestVisitorForClient();
                const segmentName = entity?.segmentName || entity?.segment_name || entity?.segment;
                const segmentId = entity?.segmentId ?? entity?.segment_id ?? entity?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Client] Guest visitor blocked from non-Enterprise segment view', { clientId: id, segmentName, segmentId });
                    renderClientGuestAccessDenied(container, id, segmentName, segmentId);
                    const nameEl = document.getElementById('clientName');
                    if (nameEl) {
                        nameEl.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                    }
                    document.title = 'BUDG';
                    return;
                }
            } catch (gateError) {
                console.warn('[Client] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            
            // Update page title with entity name (avoid i18n overwriting #clientName — data-i18n removed in client.html)
            const displayName = entity.primary_name || entity.PrimaryName || entity.primaryname || entity.name || entity.Name || 'Client';
            const nameEl = document.getElementById('clientName');
            if (nameEl) {
                nameEl.textContent = displayName;
            }
            if (document.title && displayName && displayName !== 'Client') {
                document.title = displayName + ' - BUDG';
            }
            // If i18n runs late, re-apply once next frame
            requestAnimationFrame(function() {
                const el = document.getElementById('clientName');
                if (el && el.textContent !== displayName && displayName !== 'Client') {
                    el.textContent = displayName;
                }
            });
            
            // The API already returns joined data with proper field names
            // No need to resolve references as they're already available

            const statusName = (entity.BUDG_status || entity.Status_Name || entity.status_name || entity.status || '').toString() || 'Active';
            const lifecycleName = (entity.lifecycle || entity.Lifecycle_Name || entity.lifecycle_name || '').toString();
            const viewingName = (entity.BUDG_viewing || entity.Viewing_Name || entity.viewing_name || entity.viewing || '').toString();
            const createdById = entity.created_by_id ?? entity.createdBy_ID ?? entity.created_by ?? entity.Created_By_ID;
            const createdByName = entity.created_by_name ?? entity.createdByName ?? entity.Created_By_Name;
            const segmentDisplay = entity.segmentRestricted
                ? 'Hidden due to access restrictions'
                : (entity.segmentName || entity.segment_name || entity.segment || ((entity.segmentId ?? entity.segment_id ?? entity.Segment_ID) != null
                    ? `ID ${entity.segmentId ?? entity.segment_id ?? entity.Segment_ID}`
                    : (window.I18n?.t('message.notSpecified') || 'Not specified')));

            const cT = (k) => (window.I18n && window.I18n.t(k)) || k;
            // view-grid is 2 columns: left column = definition, right column = stacked classifications + other info (same pattern as capability)
            const left = `
                <div class="view-section">
                    <div class="section-title">${cT('client.sections.definition')}</div>
                    ${renderItem(cT('client.labels.description'), _richHtml(entity.definition || entity.Description || entity.description || ''))}
                    ${renderItem(cT('client.labels.longName'), escapeHtml(entity.long_name || entity.Long_Name || entity.longName || entity.longname || ''))}
                </div>
            `;

            const right = `
                <div class="view-section">
                    <div class="section-title">${cT('client.sections.classifications')}</div>
                    ${renderItem(cT('client.labels.budgStatus'), renderStatusBadge(statusName, /active/i.test(statusName)))}
                    ${renderItem(cT('client.labels.lifecycle'), renderLifecycleBadge(lifecycleName || 'In Production'))}
                    ${renderItem(cT('client.labels.budgViewing'), renderPublicStatus(viewingName || 'Public'))}
                </div>
                <div class="view-section">
                    <div class="section-title">${cT('client.sections.otherInformation')}</div>
                    ${renderItem(cT('client.labels.createdBy'), renderUserLink(createdByName, createdById))}
                    ${renderItem(cT('client.labels.created'), renderDate(entity.created || entity.Created_Date || entity.Created_On || entity.created_on || entity.createdAt || entity.created_at || entity.Created))}
                    ${renderItem(cT('client.labels.lastUpdatedBy'), renderUserLink(entity.last_updated_by || entity.Last_Updated_By, entity.lastupdateuser_id || entity.LastUpdate_UserID || entity.last_update_user_id))}
                    ${renderItem(cT('client.labels.lastUpdated'), renderDate(entity.last_updated || entity.last_updated_date || entity.Last_Updated_On || entity.last_updated_on || entity.updatedAt || entity.updated_at || entity.Last_Updated || entity.updated))}
                    ${renderItem(cT('client.labels.segment'), escapeHtml(segmentDisplay))}
                </div>
            `;

            container.innerHTML = `
                <div class="client-summary-column">${left}</div>
                <div class="client-summary-column">${right}</div>
            `;

        // Render custom fields section
        if (window.CustomFields) {
            try {
                await window.CustomFields.renderViewSection({
                    facetId: 'Client',
                    containerId: 'clientViewContainer',
                    objectId: id,
                    title: (window.I18n && window.I18n.t('client.sections.customFields')) || 'CUSTOM FIELDS'
                });
            } catch (error) {
                console.error('Error rendering custom fields:', error);
            }
        }

        } catch (e) {
            console.error('Error loading client:', e);
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            if (isForbidden) {
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    <h3>Client Not Available</h3>
                    <p>This object is not available.</p>
                </div>`;
                return;
            }
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                <h3>Error Loading Client</h3>
                <p>Failed to load client (ID: ${id}).</p>
                <p>Error: ${e.message || 'Unknown error'}</p>
                <button onclick="location.reload()" style="margin-top: 1rem; padding: 0.5rem 1rem; background: var(--primary, #3b82f6); color: white; border: none; border-radius: 0.25rem; cursor: pointer;">
                    Retry
                </button>
            </div>`;
        }
    }

    // Load hierarchy data for client
    async function loadHierarchy(id, currentEntity) {
        try {
            // Get all clients to build hierarchy
            const allClients = await window.BUDG_API_SERVICE.getClients();
            const clients = allClients.data || allClients || [];
            
            // Find parent and children
            const parentId = currentEntity.parent_id || currentEntity.Parent_ID || currentEntity.parent_client_id;
            const children = clients.filter(client => 
                (client.parent_id || client.Parent_ID || client.parent_client_id) == id
            );
            
            // If no parent and no children, don't show hierarchy
            if (!parentId && children.length === 0) {
                return '';
            }
            
            const cT = (k) => (window.I18n && window.I18n.t(k)) || k;
            let hierarchyHtml = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">${cT('client.sections.hierarchy')}</div>
                    <div class="hierarchy-container">
                        <div class="hierarchy-table">
                            <div class="hierarchy-header">
                                <div class="hierarchy-col-entity">${cT('client.sections.clientHierarchy')}</div>
                                <div class="hierarchy-col-relationship">Relationship</div>
                                <div class="hierarchy-col-desc">${cT('client.labels.description')}</div>
                            </div>
                            <div class="hierarchy-body">
            `;
            
            // Build hierarchy tree
            const hierarchyItems = [];
            
            // Add parent chain
            if (parentId && parentId != id) { // Prevent self-reference
                try {
                    const parentChain = await buildParentChain(parentId, clients);
                    hierarchyItems.push(...parentChain);
                } catch (error) {
                    console.warn('Error building parent chain:', error);
                }
            }
            
            // Add current entity
            hierarchyItems.push({
                entity: currentEntity,
                level: parentId ? 1 : 0,
                isCurrent: true
            });
            
            // Add children (filter out self-reference)
            children.filter(child => (child.id || child.ID) != id).forEach(child => {
                hierarchyItems.push({
                    entity: child,
                    level: 1,
                    isChild: true
                });
            });
            
            // Render hierarchy items
            hierarchyItems.forEach((item, index) => {
                const entity = item.entity;
                const level = item.level;
                const isCurrent = item.isCurrent;
                const isChild = item.isChild;
                const isParent = !isCurrent && !isChild;
                
                const entityName = entity.primary_name || entity.PrimaryName || entity.primaryname || entity.name || 'Unnamed';
                const description = entity.definition || entity.Description || entity.description || '';
                const entityId = entity.id || entity.ID;
                
                let indentClass = '';
                let relationshipLabel = '';
                let entityIcon = '<i class="fas fa-users"></i>';
                let rowClass = '';
                
                if (level > 0) {
                    indentClass = `hierarchy-indent-${level}`;
                }
                
                // Determine relationship and styling
                if (isCurrent) {
                    relationshipLabel = '<span class="relationship-current"><i class="fas fa-arrow-right"></i> Current</span>';
                    entityIcon = '<i class="fas fa-users current-icon"></i>';
                    rowClass = 'hierarchy-current';
                } else if (isChild) {
                    relationshipLabel = '<span class="relationship-child"><i class="fas fa-arrow-down"></i> Child</span>';
                    entityIcon = '<i class="fas fa-users child-icon"></i>';
                    rowClass = 'hierarchy-child';
                } else if (isParent) {
                    relationshipLabel = '<span class="relationship-parent"><i class="fas fa-arrow-up"></i> Parent</span>';
                    entityIcon = '<i class="fas fa-users parent-icon"></i>';
                    rowClass = 'hierarchy-parent';
                }
                
                hierarchyHtml += `
                    <div class="hierarchy-row ${indentClass} ${rowClass}">
                        <div class="hierarchy-col-entity">
                            ${entityIcon}
                            <a href="/view/client/${entityId}" class="hierarchy-entity-link">${escapeHtml(entityName)}</a>
                        </div>
                        <div class="hierarchy-col-relationship">
                            ${relationshipLabel}
                        </div>
                        <div class="hierarchy-col-desc">${escapeHtml(description)}</div>
                    </div>
                `;
            });
            
            hierarchyHtml += `
                            </div>
                        </div>
                    </div>
                </div>
            `;
            
            return hierarchyHtml;
            
        } catch (error) {
            console.error('Error loading hierarchy:', error);
            return `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">HIERARCHY</div>
                    <div class="hierarchy-error">Error loading hierarchy data</div>
                </div>
            `;
        }
    }
    
    // Build parent chain recursively with cycle protection
    async function buildParentChain(parentId, clients, visited = new Set(), maxDepth = 10) {
        // Check for cycles
        if (visited.has(parentId)) {
            console.warn('Cycle detected in parent chain for ID:', parentId);
            return [];
        }
        
        // Check for maximum depth to prevent infinite recursion
        if (visited.size >= maxDepth) {
            console.warn('Maximum depth reached in parent chain for ID:', parentId);
            return [];
        }
        
        const parent = clients.find(client => 
            (client.id || client.ID) == parentId
        );
        
        if (!parent) return [];
        
        // Add current ID to visited set
        visited.add(parentId);
        
        const parentChain = [];
        const grandParentId = parent.parent_id || parent.Parent_ID || parent.parent_client_id;
        
        if (grandParentId && grandParentId != parentId) { // Prevent self-reference
            try {
                const grandParentChain = await buildParentChain(grandParentId, clients, new Set(visited), maxDepth);
                parentChain.push(...grandParentChain);
            } catch (error) {
                console.warn('Error in recursive parent chain build:', error);
            }
        }
        
        parentChain.push({
            entity: parent,
            level: grandParentId ? 1 : 0,
            isParent: true
        });
        
        return parentChain;
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Build direct lineage tree (current + ancestors + descendants + siblings)
    function buildDirectLineageTree(clients, currentClientId) {
        const byId = new Map();
        clients.forEach(c => byId.set(parseInt(c.id), c));

        const current = byId.get(parseInt(currentClientId));
        if (!current) return [];

        const ancestors = new Set();
        const descendants = new Set();
        const visited = new Set(); // Cycle protection

        // Add current client
        descendants.add(parseInt(currentClientId));

        // Find all ancestors with cycle protection
        let parent = current;
        let depth = 0;
        const maxDepth = 10; // Prevent infinite loops
        
        while (parent && parent.parentId && depth < maxDepth) {
            const parentId = parseInt(parent.parentId);
            
            // Skip self-reference and cycle detection
            if (parentId === parseInt(currentClientId) || visited.has(parentId)) {
                break;
            }
            
            visited.add(parentId);
            if (byId.has(parentId)) {
                parent = byId.get(parentId);
                ancestors.add(parentId);
                depth++;
            } else {
                break;
            }
        }

        // Find all descendants (including current client's children and their descendants) with cycle protection
        function findDescendants(clientId, depth = 0) {
            if (depth > maxDepth) return; // Prevent infinite recursion
            
            clients.forEach(c => {
                const childId = parseInt(c.id);
                const parentId = parseInt(c.parentId);
                
                // Skip self-reference and cycle detection
                if (parentId === clientId && childId !== parseInt(currentClientId) && !visited.has(childId)) {
                    visited.add(childId);
                    descendants.add(childId);
                    findDescendants(childId, depth + 1); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentClientId));

        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = clients.filter(c => {
                const cParentId = parseInt(c.parentId);
                const cId = parseInt(c.id);
                return cParentId === parentId && cId !== parseInt(currentClientId);
            });

            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                if (!visited.has(siblingId)) {
                    visited.add(siblingId);
                    descendants.add(siblingId);
                    findDescendants(siblingId, 1); // Add all descendants of siblings (nephews/nieces and their descendants)
                }
            });
        }

        // Include the current client, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentClientId), ...ancestors, ...descendants]);

        // Filter clients to include only the complete family tree
        return clients.filter(c => {
            const id = parseInt(c.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure
    function buildHierarchyTree(clients, rootId) {
        const byId = new Map();
        const byParent = new Map();
        
        clients.forEach(c => {
            byId.set(parseInt(c.id), c);
            const parentId = parseInt(c.parentId) || 0;
            if (!byParent.has(parentId)) byParent.set(parentId, []);
            byParent.get(parentId).push(c);
        });

        const rows = [];
        const parentMap = new Map();

        function buildRows(parentId, depth = 0) {
            const children = byParent.get(parentId) || [];
            children.forEach(child => {
                const childId = parseInt(child.id);
                const childChildren = byParent.get(childId) || [];
                const hasChildren = childChildren.length > 0;
                const childCount = childChildren.length;
                
                rows.push({
                    node: child,
                    depth,
                    childCount,
                    hasChildren
                });
                
                parentMap.set(childId, parentId);
                
                if (hasChildren) {
                    buildRows(childId, depth + 1);
                }
            });
        }

        buildRows(rootId);
        
        return { rows, parentMap: byParent };
    }

    // Render client hierarchy table following Regulatory Theme pattern
    function renderClientTable(hierarchyRows, currentId) {
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.primaryName ?? node.PrimaryName ?? node.Name ?? node.name ?? 'Unnamed Client';
            const desc = node.description || node.Description || '';
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';
            
            // Debug logging
            console.log('Client node data:', { id, name, desc, node });
            console.log('Full node object:', node);
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'client-link current-client-link' : 'client-link';
            const link = `<a class="${linkClass}" href="/view/client/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-user-tie item-icon"></i><span class="client-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc || 'No description')}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize client hierarchy interactions following Regulatory Theme pattern
    function initClientInteractions(containerEl, hierarchyRows) {
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
                if (icon.classList.contains('fa-caret-down')) {
                    icon.classList.remove('fa-caret-down');
                    icon.classList.add('fa-caret-right');
                } else {
                    icon.classList.remove('fa-caret-right');
                    icon.classList.add('fa-caret-down');
                }
                
                // Toggle children visibility
                const tbody = row.parentNode;
                const rows = Array.from(tbody.querySelectorAll('tr'));
                const currentIndex = rows.indexOf(row);
                
                // Find all direct children
                for (let i = currentIndex + 1; i < rows.length; i++) {
                    const childRow = rows[i];
                    const childDepth = parseInt(childRow.dataset.depth);
                    
                    if (childDepth <= currentDepth) {
                        break; // We've reached a sibling or parent level
                    }
                    
                    if (childDepth === currentDepth + 1) {
                        // This is a direct child
                        if (icon.classList.contains('fa-caret-right')) {
                            childRow.style.display = 'none';
                        } else {
                            childRow.style.display = '';
                        }
                    } else if (childDepth > currentDepth + 1) {
                        // This is a grandchild or deeper - hide/show based on parent state
                        if (icon.classList.contains('fa-caret-right')) {
                            childRow.style.display = 'none';
                        }
                    }
                }
            }
        });
    }

    // Placeholder functions for other tabs
    async function loadRelationships(id) {
        const container = document.getElementById('clientRelationshipsContainer');
        if (!container) return;
        const cT = (k) => (window.I18n && window.I18n.t(k)) || k;
        const clientHierarchyTitle = cT('client.sections.clientHierarchy');
        const noClientsMsg = cT('client.messages.noClientsInDb');
        const noRelatedMsg = cT('client.messages.noRelatedClients');
        const failedHierarchyMsg = cT('client.messages.failedLoadHierarchy');
        try {
            container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>';
            
            // Fetch all clients from hierarchy endpoint
            console.log('Fetching clients from /api/client/hierarchy');
            const response = await fetch('/api/client/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const clients = await response.json();
            
            console.log('Clients loaded for hierarchy:', clients);

            if (!Array.isArray(clients) || clients.length === 0) {
                container.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">' + clientHierarchyTitle + '</div><div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">' + noClientsMsg + '</div></div>';
                return;
            }

            // Build direct lineage tree (current + ancestors + descendants + siblings)
            const filteredClients = buildDirectLineageTree(clients, id);
            
            if (filteredClients.length === 0) {
                container.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">' + clientHierarchyTitle + '</div><div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">' + noRelatedMsg + '</div></div>';
                return;
            }

            // Build hierarchy tree
            const hierarchyRows = buildHierarchyTree(filteredClients, 0);
            
            // Render table
            const tableHtml = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="hierarchy-header">
                        <div class="hierarchy-title">${clientHierarchyTitle}</div>
                        <div class="hierarchy-actions">
                            <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                        </div>
                    </div>
                    <div class="hierarchy-table-wrapper">
                        <table class="hierarchy-table">
                            <thead>
                                <tr>
                                    <th>${cT('client.page.title')}</th>
                                    <th>${cT('client.labels.description')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${renderClientTable(hierarchyRows, id)}
                            </tbody>
                        </table>
                    </div>
                    <div class="table-footer">
                        ${filteredClients.length} record${filteredClients.length !== 1 ? 's' : ''}
                    </div>
                </div>
            `;
            
            container.innerHTML = tableHtml;
            
            // Initialize interactions
            initClientInteractions(container, hierarchyRows);
            
        } catch (error) {
            console.error('Error loading client hierarchy:', error);
            const failedMsg = (failedHierarchyMsg && failedHierarchyMsg.indexOf('{error}') !== -1) ? failedHierarchyMsg.replace('{error}', error.message || 'Unknown error') : (failedHierarchyMsg || 'Failed to load hierarchy data: ') + (error.message || 'Unknown error');
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">' + clientHierarchyTitle + '</div><div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + failedMsg + '</div></div>';
        }
    }

    async function loadStakeholders(id) {
        // Load stakeholders using the dedicated stakeholder view module
        if (window.ClientStakeholderView) {
            window.ClientStakeholderView.init(id);
        } else {
            const container = document.getElementById('clientStakeholdersContainer');
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Stakeholders module not loaded</div>';
        }
    }

    async function loadImpact(id) {
        const container = document.getElementById('clientImpactContainer');
        if (!container) {
            console.error('clientImpactContainer not found');
            return;
        }
        
        // Show loading state
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center;"><i class="fas fa-spinner fa-spin"></i> Loading impact data...</div>';
        
        // Wait a bit for scripts to load if needed, then try to load impact
        let retries = 0;
        const maxRetries = 10;
        
        while (retries < maxRetries) {
            if (window.loadClientImpact && typeof window.loadClientImpact === 'function') {
                try {
                    console.log('Loading client impact for ID:', id);
                    await window.loadClientImpact(id);
                    return;
                } catch (error) {
                    console.error('Error loading client impact:', error);
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
        console.error('loadClientImpact function not available after', maxRetries, 'retries');
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1; padding: 2rem; text-align: center; color: var(--danger, #dc3545);">
                <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem;"></i>
                <p>Impact view script not loaded. Please refresh the page.</p>
            </div>
        `;
    }

    async function loadHistory(id) {
        const container = document.getElementById('clientHistoryContainer');
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div id="clientHistoryComponentContainer"></div>
            </div>
        `;
        // Initialize history component for client
        if (window.HistoryComponent && id) {
            console.log('Initializing history component for Client with ID:', id);
            window.HistoryComponent.initialize('Client', id, 'clientHistoryComponentContainer');
        } else {
            console.error('HistoryComponent not available or ID not found:', { HistoryComponent: !!window.HistoryComponent, id });
        }
    }

    document.addEventListener('DOMContentLoaded', async function() {
        console.log('Client view page loaded');
        
        // Wait for API service to be available before checking authentication
        function waitForApiService() {
            if (window.BUDG_API_SERVICE) {
                hideEditsIfUnauthenticated();
            } else {
                console.log('Waiting for BUDG_API_SERVICE to be available...');
                setTimeout(waitForApiService, 100);
            }
        }
        
        // Start checking for API service
        waitForApiService();
        
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', () => window.history.length>1?window.history.back():window.location.assign('/'));
        
        const id = parseId();
        if (id != null) {
            console.log('Loading client with ID:', id);
            // Wait for i18n so summary labels (DEFINITION, CLASSIFICATIONS, etc.) use the correct locale
            if (window.i18nReadyPromise) {
                try { await window.i18nReadyPromise; } catch (e) { /* ignore */ }
            }
            load(id).then(async function() {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('client', id);
                }
                
                // Initialize unified edit dropdown
                if (window.EditDropdown && id != null) {
                    try {
                        window.EditDropdown.initialize('client', id, {
                            container: '.tab-actions',
                            editUrl: `/view/client/client-edit.html?id=${id}`
                        });
                    } catch (error) {
                        console.error('Failed to initialize edit dropdown:', error);
                    }
                }

                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('client', id, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for client:', e);
                    }
                }
            });
        } else {
            console.error('No client ID found in URL');
            const container = document.getElementById('clientViewContainer');
            if (container) {
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    <h3>Error: No Client ID</h3>
                    <p>No client ID found in the URL. Please check the URL and try again.</p>
                    <button onclick="window.history.back()" style="margin-top: 1rem; padding: 0.5rem 1rem; background: var(--primary, #3b82f6); color: white; border: none; border-radius: 0.25rem; cursor: pointer;">
                        Go Back
                    </button>
                </div>`;
            }
        }

        // Wire up tab edit button based on active tab
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.addEventListener('click', async function() {
                // Check if user is admin before allowing edit
                const isAdmin = await checkAdminPermissions();
                if (!isAdmin) {
                    alert('Access denied. You must be an admin to edit this client.');
                    return;
                }
                
                // Check lock status before navigating
                if (window.ViewLockHelper) {
                    const canEdit = await window.ViewLockHelper.interceptEditButton(
                        'client',
                        id,
                        function() {
                            const activeTab = document.querySelector('.tab.active');
                            const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                            
                            if (tabName === 'summary') {
                                // For summary tab, navigate to edit page
                                window.location.href = `/view/client/client-edit.html?id=${id}`;
                            } else {
                                // For other tabs, navigate to edit page
                                window.location.href = `/view/client/client-edit.html?id=${id}`;
                            }
                        }
                    );
                    if (!canEdit) return; // Lock check failed, navigation prevented
                } else {
                    // Fallback if ViewLockHelper not available
                    const activeTab = document.querySelector('.tab.active');
                    const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                    
                    if (tabName === 'summary') {
                        window.location.href = `/view/client/client-edit.html?id=${id}`;
                    } else {
                        window.location.href = `/view/client/client-edit.html?id=${id}`;
                    }
                }
            });
        }

        // Tabs handling
        const tabs = document.querySelectorAll('.tab-container .tab');
        const summaryEl = document.getElementById('clientViewContainer');
        const relationshipsEl = document.getElementById('clientRelationshipsContainer');
        const stakeholdersEl = document.getElementById('clientStakeholdersContainer');
        const impactEl = document.getElementById('clientImpactContainer');
        const historyEl = document.getElementById('clientHistoryContainer');
        const changeEl = document.getElementById('clientChangeContainer');
        
        tabs.forEach(btn => btn.addEventListener('click', () => {
            tabs.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const tab = btn.getAttribute('data-tab');
            
            // Hide all containers
            summaryEl.style.display = 'none';
            relationshipsEl.style.display = 'none';
            stakeholdersEl.style.display = 'none';
            impactEl.style.display = 'none';
            historyEl.style.display = 'none';
            if (changeEl) changeEl.style.display = 'none';
            
            // Show selected container and load data if needed
            if (tab === 'relationships') {
                relationshipsEl.style.display = '';
                if (!relationshipsEl.dataset.loaded) {
                    loadRelationships(id).then(() => { relationshipsEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'stakeholders') {
                stakeholdersEl.style.display = '';
                if (!stakeholdersEl.dataset.loaded) {
                    loadStakeholders(id).then(() => { stakeholdersEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'impact') {
                impactEl.style.display = '';
                if (!impactEl.dataset.loaded) {
                    loadImpact(id).then(() => { impactEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'history') {
                historyEl.style.display = '';
                if (!historyEl.dataset.loaded) {
                    loadHistory(id).then(() => { historyEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'change') {
                let changeEl = document.getElementById('clientChangeContainer');
                if (changeEl) {
                    changeEl.style.display = 'grid';
                    if (window.ChangeTabComponent) {
                        window.ChangeTabComponent.initialize('client', id, 'clientChangeContainer');
                    }
                }
            } else {
                // Default to summary
                summaryEl.style.display = '';
            }
            if (typeof window.syncStakeholderVisibility === 'function') {
                window.syncStakeholderVisibility('clientStakeholdersContainer');
            }
        }));
    });
})();

