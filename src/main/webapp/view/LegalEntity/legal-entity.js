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
        const idx = parts.indexOf('legal-entity') !== -1 ? parts.indexOf('legal-entity') : parts.indexOf('LegalEntity');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

    let _legalEntityGuestCache = null;
    async function isGuestVisitorForLegalEntity() {
        if (_legalEntityGuestCache !== null) {
            return _legalEntityGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _legalEntityGuestCache = true;
                return _legalEntityGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _legalEntityGuestCache = !isAuthenticated || isGuestRole;
            return _legalEntityGuestCache;
        } catch (_) {
            _legalEntityGuestCache = true;
            return _legalEntityGuestCache;
        }
    }

    function renderLegalEntityGuestAccessDenied(container, legalEntityId, segmentName, segmentId) {
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
                console.log('[Legal Entity] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Legal Entity', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Legal Entity] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Legal Entity] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Legal Entity] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Legal Entity', { method: 'GET', credentials: 'include' });
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
            '#deleteLegalEntityBtn'
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
        // Hide the edit dropdown for unauthorized users
        if (window.EditDropdown) {
            window.EditDropdown.hideEditControls();
        }
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

        // Viewing name from viewing_id
        if (!entity.Viewing_Name && (entity.viewing_id || entity.Viewing_ID)) {
            const viewingId = entity.viewing_id ?? entity.Viewing_ID;
            tasks.push((async () => {
                try {
                    // Since there's no specific getViewingById, we'll try to get from the list
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
        const container = document.getElementById('legalEntityViewContainer');
        const nameEl = document.getElementById('legalEntityName');
        if (nameEl) nameEl.textContent = (window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...');
        container.innerHTML = `<div class="view-section" style="grid-column:1/-1;">${window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...'}</div>`;
        try {
            // Check if API service is available
            if (!window.BUDG_API_SERVICE) {
                throw new Error('API service not available');
            }
            
            let entity = await window.BUDG_API_SERVICE.getLegalEntityById(id);
            try { 
                window.BUDG_API_SERVICE.logVisit({
                    entity: 'LegalEntity', 
                    entityId: String(id), 
                    route: `/view/LegalEntity/${id}` 
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
                const isGuest = await isGuestVisitorForLegalEntity();
                const segmentName = entity?.segmentName || entity?.segment_name || entity?.segment;
                const segmentId = entity?.segmentId ?? entity?.segment_id ?? entity?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[LegalEntity] Guest visitor blocked from non-Enterprise segment view', { legalEntityId: id, segmentName, segmentId });
                    renderLegalEntityGuestAccessDenied(container, id, segmentName, segmentId);
                    const titleNameEl = document.getElementById('legalEntityName');
                    if (titleNameEl) {
                        titleNameEl.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                    }
                    return;
                }
            } catch (gateError) {
                console.warn('[LegalEntity] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            
            // Update page title with entity name (main title shows object name; subtitle stays "Legal Entity")
            const titleNameEl = document.getElementById('legalEntityName');
            if (titleNameEl) {
                const entityName = entity.longname || entity.Name || entity.primaryname || entity.name || (window.I18n ? window.I18n.t('legalEntity.page.title') : 'Legal Entity');
                titleNameEl.textContent = entityName;
            }
            
            // The API already returns joined data with proper field names
            // No need to resolve references as they're already available

            const left = `
                <div class="view-section">
                    <div class="section-title">${window.I18n ? window.I18n.t('legalEntity.labels.definition') : 'DEFINITION'}</div>
                    ${renderItem(window.I18n ? window.I18n.t('legalEntity.labels.description') : 'Description', _richHtml(entity.Description || entity.description || ''))}
                    ${renderItem((window.I18n ? window.I18n.t('legalEntity.labels.shortName') : 'Short Name') + ':', escapeHtml(entity.Short_Name || entity.short_name || entity.shortName || entity.shortname || ''))}
                </div>
            `;

            const statusName = (entity.BUDG_status || entity.Status_Name || entity.status_name || entity.status || '').toString();
            const statusColor = /deleted/i.test(statusName) ? '#dc2626' : /active/i.test(statusName) ? '#16a34a' : '#9ca3af';
            const viewingName = (entity.BUDG_viewing || entity.Viewing_Name || entity.viewing_name || entity.viewing || '').toString();

            const right = `
                <div class="view-section">
                    <div class="section-title">${window.I18n ? window.I18n.t('legalEntity.labels.classifications') : 'CLASSIFICATIONS'}</div>
                    <div class="section-subtitle">${window.I18n ? window.I18n.t('legalEntity.labels.basicClassifications') : 'BASIC CLASSIFICATIONS'}</div>
                    ${renderItem((window.I18n ? window.I18n.t('legalEntity.labels.budgStatus') : 'BUDG Status') + ':', `<span style="display:inline-flex;align-items:center;gap:.4rem;"><span style="width:.5rem;height:.5rem;border-radius:9999px;background:${statusColor};display:inline-block"></span>${escapeHtml(statusName || (window.I18n ? window.I18n.t('common.active') : 'Active'))}</span>`)}
                    ${renderItem((window.I18n ? window.I18n.t('legalEntity.labels.budgViewing') : 'BUDG Viewing') + ':', escapeHtml(viewingName || (window.I18n ? window.I18n.t('value.viewing') : 'Public')))}
                </div>
                <div class="view-section">
                    <div class="section-title">${window.I18n ? window.I18n.t('legalEntity.labels.otherInformation') : 'OTHER INFORMATION'}</div>
                    ${renderItem((window.I18n ? window.I18n.t('legalEntity.labels.created') : 'Created') + ':', formatDateTime(entity.createdatetime || entity.Created_Date || entity.Created_On || entity.created_on || entity.createdAt || entity.created_at || entity.Created || entity.created))}
                    ${renderItem((window.I18n ? window.I18n.t('legalEntity.labels.lastUpdatedBy') : 'Last Updated By') + ':', entity.last_updated_by ? (entity.lastupdateuser_id || entity.LastUpdate_UserID || entity.last_update_user_id) ? `<a href="/view/people/${entity.lastupdateuser_id || entity.LastUpdate_UserID || entity.last_update_user_id}" class="person-link">${escapeHtml(entity.last_updated_by)}</a>` : escapeHtml(entity.last_updated_by) : '<span class="empty">-</span>')}
                    ${renderItem((window.I18n ? window.I18n.t('legalEntity.labels.lastUpdated') : 'Last Updated') + ':', formatDateTime(entity.lastupdatedatetime || entity.last_updated_date || entity.Last_Updated_On || entity.last_updated_on || entity.updatedAt || entity.updated_at || entity.Last_Updated || entity.updated))}
                    ${renderItem(window.I18n ? window.I18n.t('legalEntity.labels.segment') : 'Segment', escapeHtml(entity.segmentName || entity.segment_name || entity.segment || ((entity.segmentId ?? entity.segment_id ?? entity.Segment_ID) != null ? `ID ${entity.segmentId ?? entity.segment_id ?? entity.Segment_ID}` : (window.I18n ? window.I18n.t('message.notSpecified') : 'Not specified'))))}
                </div>
            `;

            // Render the grid without hierarchy (hierarchy moved to relationships tab)
            container.innerHTML = left + right;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Legal Entity',
                        containerId: 'legalEntityViewContainer',
                        objectId: id,
                        title: window.I18n ? window.I18n.t('legalEntity.labels.customFields') : 'CUSTOM FIELDS'
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }
            
        } catch (error) {
            console.error('Error loading legal entity:', error);
            const isForbidden = error?.status === 403 || String(error?.message || '').includes('403');
            const errorMsg = isForbidden
                ? 'This object is not available.'
                : (window.I18n ? window.I18n.t('legalEntity.errors.failedToLoad', {id: id}) : `Failed to load legal entity (id=${id}).`);
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">${errorMsg}</div>`;
            const errNameEl = document.getElementById('legalEntityName');
            if (errNameEl) errNameEl.textContent = (window.I18n ? window.I18n.t('legalEntity.page.title') : 'Legal Entity');
        }
    }

    // Build parent chain recursively with cycle protection
    async function buildParentChain(parentId, entities, visited = new Set(), maxDepth = 10) {
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
        
        const parent = entities.find(entity => 
            (entity.id || entity.ID) == parentId
        );
        
        if (!parent) return [];
        
        // Add current ID to visited set
        visited.add(parentId);
        
        const parentChain = [];
        const grandParentId = parent.parent_id || parent.Parent_ID || parent.parent_legal_entity_id;
        
        if (grandParentId && grandParentId != parentId) { // Prevent self-reference
            try {
                const grandParentChain = await buildParentChain(grandParentId, entities, new Set(visited), maxDepth);
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

    // Legal Entity relationships functionality
    async function loadRelationships(id) {
        const container = document.getElementById('legalEntityRelationshipsContainer');
        if (!container) return;

        const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...';
        container.innerHTML = `
            <div class="view-section relationships-hierarchy hierarchy-container" style="grid-column:1/-1;">
                <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${loadingMsg}</div>
            </div>
        `;
        await loadLegalHierarchy(id);
    }

    // Build family lineage tree for legal entities - following Regulatory Theme pattern
    function buildFamilyLineage(legalEntities, currentLegalEntityId) {
        const byId = new Map();
        legalEntities.forEach(le => {
            const id = parseInt(le.ID ?? le.id);
            byId.set(id, le);
        });
        
        const currentId = parseInt(currentLegalEntityId);
        const currentLegalEntity = byId.get(currentId);
        
        if (!currentLegalEntity) {
            console.log('Current legal entity not found');
            return legalEntities;
        }
        
        // Find all ancestors (parents, grandparents, etc.)
        const ancestors = new Set();
        let current = currentLegalEntity;
        
        while (current) {
            const parentId = current.Parent_ID ?? current.parent_id ?? current.parentId;
            const parentIdNum = parseInt(parentId);
            
            if (parentId && parentId !== 0 && !isNaN(parentIdNum) && byId.has(parentIdNum)) {
                ancestors.add(parentIdNum);
                current = byId.get(parentIdNum);
            } else {
                break;
            }
        }
        
        // Find all descendants (children, grandchildren, etc.)
        const descendants = new Set();
        function findDescendants(id) {
            const children = legalEntities.filter(le => {
                const parentId = le.Parent_ID ?? le.parent_id ?? le.parentId;
                return parseInt(parentId) === parseInt(id);
            });
            
            children.forEach(child => {
                const childId = parseInt(child.ID ?? child.id);
                descendants.add(childId);
                findDescendants(childId);
            });
        }
        findDescendants(currentId);
        
        // Find siblings and their descendants
        if (currentLegalEntity) {
            const currentParentId = currentLegalEntity.Parent_ID ?? currentLegalEntity.parent_id ?? currentLegalEntity.parentId;
            if (currentParentId) {
                const parentId = parseInt(currentParentId);
                const siblings = legalEntities.filter(le => {
                    const leParentId = parseInt(le.Parent_ID ?? le.parent_id ?? le.parentId);
                    const leId = parseInt(le.ID ?? le.id);
                    return leParentId === parentId && leId !== currentId;
                });
                
                siblings.forEach(sibling => {
                    const siblingId = parseInt(sibling.ID ?? sibling.id);
                    descendants.add(siblingId);
                    findDescendants(siblingId);
                });
            }
        }
        
        // Include current, ancestors, and descendants
        const includedIds = new Set([currentId, ...ancestors, ...descendants]);
        
        return legalEntities.filter(le => {
            const id = parseInt(le.ID ?? le.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure following Regulatory Theme pattern
    function buildHierarchyTree(legalEntities, rootId) {
        const byParent = new Map();
        const byId = new Map();
        
        legalEntities.forEach(le => {
            const id = parseInt(le.ID ?? le.id);
            const parentId = le.Parent_ID ?? le.parent_id ?? le.parentId;
            const parentIdNum = parentId ? parseInt(parentId) : null;
            byId.set(id, le);
            
            if (!byParent.has(parentIdNum)) {
                byParent.set(parentIdNum, []);
            }
            byParent.get(parentIdNum).push(le);
        });
        
        const rows = [];
        function buildRows(legalEntityId, depth = 0) {
            const currentLegalEntity = byId.get(legalEntityId);
            if (currentLegalEntity) {
                const childCount = (byParent.get(legalEntityId) || []).length;
                
                rows.push({
                    node: currentLegalEntity,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                const children = byParent.get(legalEntityId) || [];
                children.forEach(le => {
                    buildRows(parseInt(le.ID ?? le.id), depth + 1);
                });
            } else {
                const children = byParent.get(legalEntityId) || [];
                children.forEach(le => {
                    buildRows(parseInt(le.ID ?? le.id), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    // Render legal entity hierarchy table following Regulatory Theme pattern
    function renderLegalEntityTable(hierarchyRows, currentId) {
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const defaultName = window.I18n ? window.I18n.t('legalEntity.relationships.legalEntity') : 'Unnamed Legal Entity';
            const name = node.longName ?? node.longname ?? node.shortname ?? node.shortName ?? node.displayName ?? node.name ?? defaultName;
            const desc = node.description ?? node.Description ?? '';
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'le-link current-le-link' : 'le-link';
            const link = `<a class="${linkClass}" href="/view/LegalEntity/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-building item-icon"></i><span class="le-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize legal entity hierarchy interactions following Regulatory Theme pattern
    function initLegalEntityInteractions(containerEl, hierarchyRows) {
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
                            if (childExpander && childExpander.classList.contains('fa-caret-down')) {
                                childExpander.classList.remove('fa-caret-down');
                                childExpander.classList.add('fa-caret-right');
                            }
                        }
                    } else if (childDepth > currentDepth + 1) {
                        if (isExpanded) {
                            nextRow.style.display = 'none';
                        }
                    }
                    
                    nextRow = nextRow.nextElementSibling;
                }
            }
        });
    }

    // Helper function to escape HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Build direct lineage tree (current + ancestors + descendants + siblings)
    function buildDirectLineageTree(legalEntities, currentLegalEntityId) {
        const byId = new Map();
        legalEntities.forEach(le => byId.set(parseInt(le.id), le));

        const current = byId.get(parseInt(currentLegalEntityId));
        if (!current) return [];

        const ancestors = new Set();
        const descendants = new Set();

        // Add current legal entity
        descendants.add(parseInt(currentLegalEntityId));

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

        // Find all descendants (including current legal entity's children and their descendants)
        function findDescendants(legalEntityId) {
            legalEntities.forEach(le => {
                if (parseInt(le.parentId) === legalEntityId) {
                    const childId = parseInt(le.id);
                    descendants.add(childId);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentLegalEntityId));

        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = legalEntities.filter(le => {
                const leParentId = parseInt(le.parentId);
                const leId = parseInt(le.id);
                return leParentId === parentId && leId !== parseInt(currentLegalEntityId);
            });

            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }

        // Include the current legal entity, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentLegalEntityId), ...ancestors, ...descendants]);

        // Filter legal entities to include only the complete family tree
        return legalEntities.filter(le => {
            const id = parseInt(le.id);
            return includedIds.has(id);
        });
    }

    async function loadLegalHierarchy(id) {
        const container = document.querySelector('.relationships-hierarchy');
        if (!container) return;
        
        try {
            const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loading') : 'Loading...';
            container.innerHTML = `<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${loadingMsg}</div>`;
            
            // Fetch all legal entities from hierarchy endpoint
            console.log('Fetching legal entities from /api/LegalEntity/hierarchy');
            const response = await fetch('/api/LegalEntity/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const legalEntities = await response.json();
            
            console.log('Legal entities loaded for hierarchy:', legalEntities);
            
            if (!Array.isArray(legalEntities) || legalEntities.length === 0) {
                const noDataMsg = window.I18n ? window.I18n.t('legalEntity.relationships.noData') : 'No legal entities found in database';
                container.innerHTML = `<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">${noDataMsg}</div>`;
                return;
            }

            // Build direct lineage tree (current + ancestors + descendants + siblings)
            const filteredLegalEntities = buildDirectLineageTree(legalEntities, id);
            
            if (filteredLegalEntities.length === 0) {
                const noRelatedMsg = window.I18n ? window.I18n.t('legalEntity.relationships.noRelated') : 'No related legal entities found';
                container.innerHTML = `<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">${noRelatedMsg}</div>`;
                return;
            }

            // Build hierarchy tree
            const hierarchyRows = buildHierarchyTree(filteredLegalEntities, 0);
            
            // Render table
            const hierarchyTitle = window.I18n ? window.I18n.t('legalEntity.relationships.title') : 'LEGAL ENTITY HIERARCHY';
            const legalEntityLabel = window.I18n ? window.I18n.t('legalEntity.relationships.legalEntity') : 'Legal Entity';
            const descriptionLabel = window.I18n ? window.I18n.t('legalEntity.relationships.description') : 'Description';
            const recordText = filteredLegalEntities.length === 1 
                ? (window.I18n ? window.I18n.t('legalEntity.relationships.records', {count: filteredLegalEntities.length}) : `${filteredLegalEntities.length} record`)
                : (window.I18n ? window.I18n.t('legalEntity.relationships.recordsPlural', {count: filteredLegalEntities.length}) : `${filteredLegalEntities.length} records`);
            const tableHtml = `
                <div class="hierarchy-header">
                    <div class="hierarchy-title">${hierarchyTitle}</div>
                    <div class="hierarchy-actions">
                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                    </div>
                </div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>${legalEntityLabel}</th>
                                <th>${descriptionLabel}</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderLegalEntityTable(hierarchyRows, id)}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${recordText}
                </div>
            `;
            
            container.innerHTML = tableHtml;
            
            // Initialize interactions
            initLegalEntityInteractions(container, hierarchyRows);
            
        } catch (error) {
            console.error('Failed to load legal entity hierarchy:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.errors.failedToLoadHierarchy', {error: error.message || 'Unknown error'}) : `Failed to load hierarchy data: ${error.message || 'Unknown error'}`;
            container.innerHTML = `<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">${errorMsg}</div>`;
        }
    }

    function addLegalExpandCollapseFunctionality() {
        const expandIcons = document.querySelectorAll('.relationships-hierarchy .expand-icon');
        
        expandIcons.forEach(function(icon) {
            icon.addEventListener('click', function() {
                const groupIndex = this.getAttribute('data-group');
                const parentRow = this.closest('.hierarchy-parent');
                const childRows = document.querySelectorAll(`.hierarchy-row[data-group="${groupIndex}"]`);
                
                if (this.classList.contains('expanded')) {
                    // Collapse
                    this.classList.remove('expanded');
                    this.classList.add('collapsed');
                    this.className = this.className.replace('fa-chevron-down', 'fa-chevron-right');
                    
                    // Hide child rows
                    childRows.forEach(function(row) {
                        row.style.display = 'none';
                    });
                } else {
                    // Expand
                    this.classList.remove('collapsed');
                    this.classList.add('expanded');
                    this.className = this.className.replace('fa-chevron-right', 'fa-chevron-down');
                    
                    // Show child rows
                    childRows.forEach(function(row) {
                        row.style.display = '';
                    });
                }
            });
        });
    }

    async function loadStakeholders(id) {
        // Load stakeholders using the dedicated stakeholder view module
        if (window.LegalEntityStakeholderView) {
            window.LegalEntityStakeholderView.init(id);
        } else {
            const container = document.getElementById('legalEntityStakeholdersContainer');
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.messages.stakeholdersModuleNotLoaded') : 'Stakeholders module not loaded';
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;">${errorMsg}</div>`;
        }
    }

    async function loadImpact(id) {
        const container = document.getElementById('legalEntityImpactContainer');
        if (!container) return;
        
        // Use the legal impact view function if available
        if (window.loadLegalImpact) {
            await window.loadLegalImpact(id);
        } else {
            const loadingMsg = window.I18n ? window.I18n.t('legalEntity.messages.loadingImpact') : 'Loading impact...';
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;">${loadingMsg}</div>`;
        }
    }

    async function loadHistory(id) {
        const container = document.getElementById('legalEntityHistoryContainer');
        if (!container) return;
        
        try {
            // Use the history component to create the history interface
            if (window.HistoryComponent) {
                await window.HistoryComponent.initialize('Legal Entities', id, 'legalEntityHistoryContainer');
            } else if (window.createHistoryComponent) {
                // Fallback for backward compatibility
                window.createHistoryComponent('Legal Entities', id, 'legalEntityHistoryContainer');
            } else {
                throw new Error('History component not available');
            }
        } catch (error) {
            console.error('Failed to load history component:', error);
            const errorMsg = window.I18n ? window.I18n.t('legalEntity.messages.failedToLoadHistory') : 'Failed to load history component';
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">${errorMsg}</div>`;
        }
    }

    document.addEventListener('DOMContentLoaded', function() {
        console.log('Legal Entity view page loaded');
        
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
            console.log('Loading legal entity with ID:', id);
            load(id).then(async function() {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('legal-entity', id);
                }
                
                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('legal-entity', id, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for legal entity:', e);
                    }
                }
            });
            
            // Initialize unified edit dropdown
            if (window.EditDropdown && id != null) {
                try {
                    window.EditDropdown.initialize('legal-entity', id, {
                        container: '.tab-actions',
                        editUrl: `/view/LegalEntity/legal-entity-edit.html?id=${id}`
                    });
                } catch (error) {
                    console.error('Failed to initialize edit dropdown:', error);
                }
            }
        } else {
            console.error('No legal entity ID found in URL');
            const container = document.getElementById('legalEntityViewContainer');
            if (container) {
                const errorTitle = window.I18n ? window.I18n.t('message.error') : 'Error: No Legal Entity ID';
                const errorMsg = window.I18n ? window.I18n.t('legalEntity.messages.noIdFound') : 'No legal entity ID found in the URL. Please check the URL and try again.';
                const backBtnText = window.I18n ? window.I18n.t('legalEntity.buttons.back') : 'Go Back';
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    <h3>${errorTitle}</h3>
                    <p>${errorMsg}</p>
                    <button onclick="window.history.back()" style="margin-top: 1rem; padding: 0.5rem 1rem; background: #248567;color: white; border: none; border-radius: 0.25rem; cursor: pointer;">
                        ${backBtnText}
                    </button>
                </div>`;
            }
        }

        // Tab functionality
        const tabs = document.querySelectorAll('.tab-container .tab');
        const summaryEl = document.getElementById('legalEntityViewContainer');
        const relationshipsEl = document.getElementById('legalEntityRelationshipsContainer');
        const stakeholdersEl = document.getElementById('legalEntityStakeholdersContainer');
        const impactEl = document.getElementById('legalEntityImpactContainer');
        const historyEl = document.getElementById('legalEntityHistoryContainer');
        
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
            
            // Show selected container and load data if needed
            if (tab === 'relationships') {
                relationshipsEl.style.display = '';
                if (!relationshipsEl.dataset.loaded && id) {
                    loadRelationships(id).then(() => { relationshipsEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'stakeholders') {
                stakeholdersEl.style.display = '';
                if (!stakeholdersEl.dataset.loaded && id) {
                    loadStakeholders(id).then(() => { stakeholdersEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'impact') {
                impactEl.style.display = '';
                if (!impactEl.dataset.loaded && id) {
                    loadImpact(id).then(() => { impactEl.dataset.loaded = '1'; });
                }
            } else if (tab === 'history') {
                historyEl.style.display = '';
                if (!historyEl.dataset.loaded && id) {
                    loadHistory(id).then(() => { historyEl.dataset.loaded = '1'; });
                }
            } else {
                // Default to summary
                summaryEl.style.display = '';
            }

            // Sync injected Stakeholders/Followers sub-tabs visibility (only show when main tab is STAKEHOLDERS)
            if (typeof window._legalEntitySyncStakeholderVisibility === 'function') {
                window._legalEntitySyncStakeholderVisibility();
            }
            
        }));
    });
})();

