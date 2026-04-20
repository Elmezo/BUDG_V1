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
        const idx = parts.indexOf('committee');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
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
                console.log('[Committee] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Committee', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Committee] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Committee] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Committee] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Committee', { method: 'GET', credentials: 'include' });
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
                //('Hiding edit controls - user is not admin');
                hideEditControls();
            } else {
                //('User is admin - edit controls should be visible');
                showEditControls();
            }
        } catch(e) {
            //('Error in hideEditsIfUnauthenticated:', e, '- hiding edit controls by default');
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
            '#deleteCommitteeBtn'
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
        //('Edit controls shown for admin user');
    }

    // Check if user has admin permissions
    async function checkAdminPermissions() {
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!meResp.ok) {
                //('API /api/me failed:', meResp.status);
                return false;
            }
            const me = await meResp.json();
            //('User data from /api/me:', me);
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            //('Normalized role:', role);
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            //('Is admin?', isAdmin);
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
                    const response = await fetch(`/api/committee/lookup?type=status`);
                    const data = await response.json();
                    if (data && Array.isArray(data)) {
                        const status = data.find(s => s.ID === statusId);
                        entity.Status_Name = status?.primaryname || status?.Name || status?.name || String(statusId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // Lifecycle name from lifecycle_id
        if (!entity.Lifecycle_Name && (entity.lifecycle_id || entity.Lifecycle_ID)) {
            const lifecycleId = entity.lifecycle_id ?? entity.Lifecycle_ID;
            tasks.push((async () => {
                try {
                    const response = await fetch(`/api/committee/lookup?type=lifecycle`);
                    const data = await response.json();
                    if (data && Array.isArray(data)) {
                        const lifecycle = data.find(l => l.ID === lifecycleId);
                        entity.Lifecycle_Name = lifecycle?.PrimaryName || lifecycle?.primaryname || lifecycle?.name || String(lifecycleId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // BUDG Viewing from isPublic field (check this first, like other objects)
        if (!entity.viewingName && (entity.isPublic || entity.is_public)) {
            const viewingId = entity.isPublic || entity.is_public;
            tasks.push((async () => {
                try {
                    let viewing = await api.getViewingById(viewingId);
                    if (viewing && viewing.data) viewing = viewing.data;
                    entity.viewingName = viewing?.primaryname || viewing?.Name || viewing?.name || String(viewingId);
                } catch(e) {
                    console.error('Failed to resolve BUDG Viewing name:', e);
                    entity.viewingName = String(viewingId);
                }
            })());
        }

        // Viewing name from viewing_id (fallback)
        if (!entity.Viewing_Name && !entity.viewingName && (entity.viewing_id || entity.Viewing_ID)) {
            const viewingId = entity.viewing_id ?? entity.Viewing_ID;
            tasks.push((async () => {
                try {
                    const response = await fetch(`/api/committee/lookup?type=viewing`);
                    const data = await response.json();
                    if (data && Array.isArray(data)) {
                        const viewing = data.find(v => v.ID === viewingId);
                        entity.Viewing_Name = viewing?.Name || viewing?.name || viewing?.primaryname || String(viewingId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // Classification name from classification_id
        if (!entity.Classification_Name && (entity.classification_id || entity.Classification_ID)) {
            const classificationId = entity.classification_id ?? entity.Classification_ID;
            tasks.push((async () => {
                try {
                    const response = await fetch(`/api/committee/lookup?type=classification`);
                    const data = await response.json();
                    if (data && Array.isArray(data)) {
                        const classification = data.find(c => c.ID === classificationId);
                        entity.Classification_Name = classification?.PrimaryName || classification?.primaryname || classification?.name || String(classificationId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // Committee Type name from committee_type_id
        if (!entity.Committee_Type_Name && (entity.committee_type_id || entity.Committee_Type_ID)) {
            const committeeTypeId = entity.committee_type_id ?? entity.Committee_Type_ID;
            tasks.push((async () => {
                try {
                    const response = await fetch(`/api/committee/lookup?type=committeetype`);
                    const data = await response.json();
                    if (data && Array.isArray(data)) {
                        const committeeType = data.find(ct => ct.ID === committeeTypeId);
                        entity.Committee_Type_Name = committeeType?.PrimaryName || committeeType?.primaryname || committeeType?.name || String(committeeTypeId);
                    }
                } catch(_) { /* ignore */ }
            })());
        }

        // Parent Committee name from parent_id
        if (!entity.Parent_Name && (entity.parent_id || entity.Parent_ID)) {
            const parentId = entity.parent_id ?? entity.Parent_ID;
            tasks.push((async () => {
                try {
                    const response = await fetch(`/api/committee/lookup?type=committees`);
                    const data = await response.json();
                    if (data && Array.isArray(data)) {
                        const parent = data.find(p => p.ID === parentId);
                        entity.Parent_Name = parent?.Display_Name || parent?.PrimaryName || parent?.primaryname || parent?.name || String(parentId);
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


    // Guest / segment access control for Committee view
    let _committeeGuestCache = null;
    async function isGuestVisitorForCommittee() {
        if (_committeeGuestCache !== null) {
            return _committeeGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _committeeGuestCache = true;
                return _committeeGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _committeeGuestCache = !isAuthenticated || isGuestRole;
            return _committeeGuestCache;
        } catch (_) {
            _committeeGuestCache = true;
            return _committeeGuestCache;
        }
    }

    function renderCommitteeGuestAccessDenied(container, committeeId, segmentName, segmentId) {
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

    async function load(id) {
        const container = document.getElementById('committeeViewContainer');
        container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">' + (window.I18n?.t('committee.loading') || 'Loading...') + '</div>';
        try {
            // Check if API service is available
            if (!window.BUDG_API_SERVICE) {
                throw new Error('API service not available');
            }

            let entity = await window.BUDG_API_SERVICE.getCommitteeById(id);
            try {
                window.BUDG_API_SERVICE.logVisit({
                    entity: 'Committee',
                    entityId: String(id),
                    route: `/view/committee/${id}`
                }).catch(error => {
                    console.warn('Failed to log visit (API error):', error);
                    // Don't throw - this is not critical for page functionality
                });
            } catch(error) {
                console.warn('Failed to log visit (service error):', error);
                // Don't throw - this is not critical for page functionality
            }
            if (entity && entity.data) entity = entity.data;

            // Guest / segment gating: unauthenticated users may only view Enterprise segment
            try {
                const isGuest = await isGuestVisitorForCommittee();
                const segmentName =
                    (typeof entity.segmentName === 'string' && entity.segmentName.trim()) ? entity.segmentName.trim() :
                    (typeof entity.segment_name === 'string' && entity.segment_name.trim()) ? entity.segment_name.trim() :
                    (typeof entity.segment === 'string' && entity.segment.trim()) ? entity.segment.trim() :
                    (entity.segment && typeof entity.segment === 'object' && typeof entity.segment.name === 'string' && entity.segment.name.trim())
                        ? entity.segment.name.trim()
                        : '';
                const segmentId = entity.segmentId ?? entity.segment_id ?? entity.Segment_ID ?? entity.segment?.id ?? entity.segment?.ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();

                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Committee] Guest visitor blocked from non-Enterprise segment view', {
                        committeeId: id,
                        segmentName,
                        segmentId
                    });
                    renderCommitteeGuestAccessDenied(container, id, segmentName, segmentId);
                    return;
                }
            } catch (gateError) {
                console.warn('[Committee] Error during guest/segment access check, falling back to normal render:', gateError);
            }

            // Update page title with entity name
            const nameEl = document.getElementById('committeeName');
            if (nameEl) {
                nameEl.textContent = entity.Name || entity.primaryName || entity.primary_name || entity.PrimaryName || entity.primaryname || entity.name || 'Committee';
            }

            // Resolve references (especially for BUDG Viewing from isPublic field)
            entity = await resolveReferences(entity);

            const I = window.I18n?.t.bind(window.I18n) || (k => k);
            const statusName = (entity.Status || entity.Status_Name || entity.status_name || entity.status || '').toString() || 'Active';
            const lifecycleName = (entity.Lifecycle || entity.Lifecycle_Name || entity.lifecycle_name || entity.lifecycle || '').toString();
            const viewingName = (entity.viewingName || entity.Viewing || entity.Viewing_Name || entity.viewing_name || entity.viewing || '').toString();
            const classificationName = (entity.Classification || entity.Classification_Name || entity.classification_name || entity.classification || '').toString();
            const committeeTypeName = (entity.Committee_Type || entity.Committee_Type_Name || entity.committee_type_name || entity.committee_type || '').toString();
            const segmentName =
                (typeof entity.segmentName === 'string' && entity.segmentName.trim()) ? entity.segmentName.trim() :
                (typeof entity.segment_name === 'string' && entity.segment_name.trim()) ? entity.segment_name.trim() :
                (typeof entity.segment === 'string' && entity.segment.trim()) ? entity.segment.trim() :
                (entity.segment && typeof entity.segment === 'object' && typeof entity.segment.name === 'string' && entity.segment.name.trim())
                    ? entity.segment.name.trim()
                    : '';
            const segmentId = entity.segmentId ?? entity.segment_id ?? entity.Segment_ID ?? entity.segment?.id ?? entity.segment?.ID;
            const segmentDisplay = entity.segmentRestricted
                ? 'Hidden due to access restrictions'
                : (segmentName || (segmentId != null ? `ID ${segmentId}` : (window.I18n?.t('message.notSpecified') || 'Not specified')));

            const left = `
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${I('committee.sections.definition')}</div>
                    ${renderItem(I('committee.primaryName') || 'Primary Name', escapeHtml(entity.Name || entity.primary_name || entity.PrimaryName || entity.primaryname || entity.name || ''))}
                    ${renderItem(I('committee.parentCommittee') || 'Parent Committee', entity.Parent_Name ? `<a href="/view/committee/${entity.Parent_ID || entity.parent_id}" class="committee-link">${escapeHtml(entity.Parent_Name)}</a>` : '<span class="empty">-</span>')}
                    ${renderItem(I('committee.referenceNumber') || 'Ref.', escapeHtml(entity.Ref || entity.ref_number || entity.RefNumber || entity.refNumber || entity.ref || ''))}
                    ${renderItem(I('committee.description') || 'Description', _richHtml(entity.Description || entity.description || entity.definition || ''))}
                </div>
            `;

            const right = `
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${I('committee.sections.classifications')}</div>
                    ${renderItem(I('committee.labels.budgStatus'), renderStatusBadge(statusName, /active/i.test(statusName)))}
                    ${renderItem(I('committee.labels.lifecycle'), renderLifecycleBadge(lifecycleName || 'In Production'))}
                    ${renderItem(I('committee.labels.budgViewing'), renderPublicStatus(viewingName || 'Public'))}
                    ${renderItem(I('committee.labels.classification'), escapeHtml(classificationName))}
                    ${renderItem(I('committee.labels.committeeType'), escapeHtml(committeeTypeName))}
                    ${renderItem(I('committee.labels.segment'), escapeHtml(segmentDisplay))}
                </div>
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${I('committee.sections.otherInformation')}</div>
                    ${renderItem(I('committee.labels.createdBy'), renderUserLink(entity.Created_By_Name || entity.createdByName || entity.created_by_name, entity.Created_By_ID || entity.createdById || entity.created_by_id))}
                    ${renderItem(I('committee.labels.created'), renderDate(entity.Created || entity.created || entity.Created_Date || entity.Created_On || entity.created_on || entity.createdAt || entity.created_at))}
                    ${renderItem(I('committee.labels.lastUpdatedBy'), renderUserLink(entity.Last_Updated_By, entity.lastupdateuser_id || entity.LastUpdate_UserID || entity.last_update_user_id))}
                    ${renderItem(I('committee.labels.lastUpdated'), renderDate(entity.Last_Updated || entity.last_updated || entity.last_updated_date || entity.Last_Updated_On || entity.last_updated_on || entity.updatedAt || entity.updated_at))}
                </div>
            `;

            const gridWrapper = `
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; align-items: start; width: 100%; box-sizing: border-box; overflow: visible;">
                    <div style="display: flex; flex-direction: column; gap: 1.25rem; width: 100%; box-sizing: border-box; overflow: visible; min-height: fit-content;">
                        ${left}
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 1.25rem; width: 100%; box-sizing: border-box; overflow: visible; min-height: fit-content;">
                        ${right}
                    </div>
                    <div class="form-section" id="customFieldsViewSection" style="grid-column: 1 / -1; width: 100%;">
                        <h3 class="section-title" data-i18n="committee.customFields">CUSTOM FIELDS</h3>
                        <div class="form-fields-container">
                            <div id="customFieldsViewContainer"></div>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = gridWrapper;

            // Render custom fields section — show only when there are fields (like System)
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Committee',
                        containerId: 'customFieldsViewContainer',
                        objectId: id,
                        title: I('committee.customFields') || 'CUSTOM FIELDS'
                    });
                    const cfSection = document.getElementById('customFieldsViewSection');
                    const cfContainer = document.getElementById('customFieldsViewContainer');
                    if (cfSection && cfContainer) {
                        if (cfContainer.innerHTML.trim()) {
                            cfSection.style.display = '';
                        } else {
                            cfSection.style.display = 'none';
                        }
                    }
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                    const cfSection = document.getElementById('customFieldsViewSection');
                    if (cfSection) cfSection.style.display = 'none';
                }
            } else {
                const cfSection = document.getElementById('customFieldsViewSection');
                if (cfSection) cfSection.style.display = 'none';
            }
            
        } catch (e) {
            console.error('Error loading committee:', e);
            const I = window.I18n?.t.bind(window.I18n) || (k => k);
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            // Check if it's a 404 error (entity not found)
            if (isForbidden) {
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    <h3>Committee Not Available</h3>
                    <p>This object is not available.</p>
                </div>`;
            } else if (e.status === 404 || (e.message && e.message.includes('not found'))) {
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    <h3>${I('committee.notFound')}</h3>
                    <p>${(I('committee.notFoundMessage') || '').replace('{id}', id)}</p>
                    <p>${I('committee.notFoundHint')}</p>
                    <button onclick="window.history.back()" style="margin-top: 1rem; padding: 0.5rem 1rem; background: #248567;color: white; border: none; border-radius: 0.25rem; cursor: pointer;">${I('committee.goBack')}</button>
                </div>`;
            } else {
                container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    <h3>${I('committee.errorLoading')}</h3>
                    <p>${(I('committee.errorLoadingMessage') || '').replace('{id}', id)}</p>
                    <p>Error: ${e.message || 'Unknown error'}</p>
                    <button onclick="location.reload()" style="margin-top: 1rem; padding: 0.5rem 1rem; background: #248567;color: white; border: none; border-radius: 0.25rem; cursor: pointer;">${I('committee.retry')}</button>
                </div>`;
            }
        }
    }

    // Build parent chain recursively with cycle protection
    async function buildParentChain(parentId, committees, visited = new Set(), maxDepth = 10) {
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

        const parent = committees.find(committee =>
            (committee.id || committee.ID) == parentId
        );

        if (!parent) return [];

        // Add current ID to visited set
        visited.add(parentId);

        const parentChain = [];
        const grandParentId = parent.parentId || parent.parent_id || parent.Parent_ID || parent.parent_committee_id;

        if (grandParentId && grandParentId != parentId) { // Prevent self-reference
            try {
                const grandParentChain = await buildParentChain(grandParentId, committees, new Set(visited), maxDepth);
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

    // Tab switching functionality
    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        const containers = document.querySelectorAll('.content-body > [id$="Container"]');

        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                // Add active class to clicked tab
                tab.classList.add('active');

                // Hide all tab containers (exclude nested ones inside committeeViewContainer)
                const nestedContainers = ['customFieldsViewContainer'];
                containers.forEach(container => {
                    if (!nestedContainers.includes(container.id)) {
                        container.style.setProperty('display', 'none', 'important');
                    }
                });

                const tabName = tab.getAttribute('data-tab');
                const viewContainer = document.getElementById('committeeViewContainer');
                if (tabName === 'summary') {
                    if (viewContainer) viewContainer.style.setProperty('display', 'block', 'important');
                } else {
                    if (viewContainer) viewContainer.style.setProperty('display', 'none', 'important');
                }

                // Show the corresponding container (summary tab uses committeeViewContainer)
                const containerId = tabName === 'summary' ? 'committeeViewContainer' : `committee${tabName.charAt(0).toUpperCase() + tabName.slice(1)}Container`;
                const targetContainer = document.getElementById(containerId);
                if (targetContainer) {
                    targetContainer.style.setProperty('display', tabName === 'summary' ? 'block' : 'grid', 'important');
                }

                // Load tab-specific content
                loadTabContent(tabName);
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('committeeStakeholdersContainer');
                }
            });
        });
    }

    // Load content for specific tab
    function loadTabContent(tabName) {
        if (tabName === 'impact') {
            const container = document.getElementById('committeeImpactContainer');
            if (container && window.loadCommitteeImpact) {
                const committeeId = parseId();
                if (committeeId) {
                    window.loadCommitteeImpact(committeeId);
                }
            }
            return;
        }
        const id = parseId();
        if (!id) return;

        switch(tabName) {
            case 'stakeholders':
                // Load stakeholders in view mode
                if (window.CommitteeStakeholderView) {
                    window.CommitteeStakeholderView.init(id);
                }
                break;
            case 'relationships':
                // Load relationships tab
                loadCommitteeRelationships(id);
                break;
            case 'history':
                // Load history tab
                if (window.HistoryComponent) {
                    window.HistoryComponent.initialize('Committees', id, 'committeeHistoryContainer');
                }
                break;
            case 'change':
                // Load change tab
                const changeContainer = document.getElementById('committeeChangeContainer');
                if (changeContainer) {
                    changeContainer.style.display = 'grid';
                    if (window.ChangeTabComponent) {
                        window.ChangeTabComponent.initialize('committee', id, 'committeeChangeContainer');
                    }
                }
                break;
            case 'summary':
                // Already loaded in main load function
                break;
            default:
                // Handle other tabs if needed
                break;
        }
    }

    // Edit button functionality
    function initEditButton() {
        const editBtn = document.getElementById('tabEditBtn');
        if (editBtn) {
            editBtn.addEventListener('click', () => {
                const id = parseId();
                if (id) {
                    // Get the currently active tab
                    const activeTab = document.querySelector('.tab.active');
                    const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'view';

                    // Navigate to edit page with tab parameter
                    window.location.href = `/view/committee/committee-edit.html?id=${id}&tab=${activeTabName}`;
                }
            });
        }
    }

    // Back button functionality
    function initBackButton() {
        const backBtn = document.getElementById('backBtn');
        if (backBtn) {
            backBtn.addEventListener('click', () => {
                window.history.back();
            });
        }
    }

    // Committee relationships functionality
    async function loadCommitteeRelationships(id) {
        const container = document.getElementById('committeeRelationshipsContainer');
        if (!container) return;

        const relI = window.I18n?.t.bind(window.I18n) || (k => k);
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">
                        <button class="sub-tab active" data-sub-tab="hierarchy">${relI('committee.hierarchy')}</button>
                        <button class="sub-tab" data-sub-tab="relationships">${relI('committee.relationships')}</button>
                    </div>
                    <div class="relationships-content">
                        <div id="committeeHierarchyContent" class="sub-tab-content active">
                            <div class="relationships-hierarchy">
                                <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${relI('committee.loading')}</div>
                            </div>
                        </div>
                        <div id="committeeRelationshipsContent" class="sub-tab-content">
                            <div class="relationships-hierarchy">
                                <div class="hierarchy-header">
                                    <div class="hierarchy-title">${relI('committee.relationships')}</div>
                                    <div class="hierarchy-actions">
                                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i></button>
                                    </div>
                                </div>
                                <div class="data-table-wrapper">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th style="text-align: center;"><div class="th-content"><span>${relI('committee.relationshipType')}</span><i class="fas fa-sort"></i></div></th>
                                                <th style="text-align: center;"><div class="th-content"><span>${relI('committee.targetCommittee')}</span><i class="fas fa-sort"></i></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="committeeRelationshipsTableBody">
                                            <tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${relI('committee.loading')}</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="hierarchy-footer">
                                    0 ${relI('committee.records')}
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Initialize sub-tab switching
        initCommitteeSubTabs();

        // Load hierarchy data
        await loadCommitteeHierarchy(id);

        // Load relationships data
        await loadCommitteeRelationshipsData(id);
    }

    // Initialize sub-tab switching for relationships tab
    function initCommitteeSubTabs() {
        const subTabs = document.querySelectorAll('.relationships-sub-tabs .sub-tab');
        const subTabContents = document.querySelectorAll('.relationships-content .sub-tab-content');

        subTabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const targetSubTab = tab.getAttribute('data-sub-tab');

                // Remove active class from all tabs and contents
                subTabs.forEach(t => t.classList.remove('active'));
                subTabContents.forEach(c => c.classList.remove('active'));

                // Add active class to clicked tab
                tab.classList.add('active');

                // Show corresponding content
                const targetContent = document.getElementById(`committee${targetSubTab.charAt(0).toUpperCase() + targetSubTab.slice(1)}Content`);
                if (targetContent) {
                    targetContent.classList.add('active');
                }
            });
        });
    }

    // Load committee relationships data
    async function loadCommitteeRelationshipsData(id) {
        const tbody = document.getElementById('committeeRelationshipsTableBody');
        const footer = document.querySelector('#committeeRelationshipsContent .hierarchy-footer');
        if (!tbody) return;

        try {
            const loadI = window.I18n?.t.bind(window.I18n) || (k => k);
            tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + loadI('committee.loading') + '</td></tr>';

            const relationships = await window.BUDG_API_SERVICE.getCommitteeRelationshipsBySourceId(id);
            const relationshipsList = relationships?.data || relationships || [];

            if (relationshipsList.length === 0) {
                tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No relationships found</td></tr>';
                if (footer) footer.textContent = '0 records';
                return;
            }

            const rowsHtml = relationshipsList.map(rel => {
                const relationshipType = escapeHtml(rel.relationshipType || rel.Relationship_Type || rel.relationship_type || '');
                const targetCommittee = rel.targetCommittee || rel.Target_Committee || rel.target_committee || '';
                const targetCommitteeId = rel.targetCommitteeId || rel.Target_Committee_ID || rel.target_committee_id;
                const targetCommitteeName = escapeHtml(rel.targetCommitteeName || rel.Target_Committee_Name || rel.target_committee_name || targetCommittee || '');

                const targetLink = targetCommitteeId 
                    ? `<a href="/view/committee/${targetCommitteeId}" class="committee-link">${targetCommitteeName}</a>`
                    : targetCommitteeName || '<span class="empty">-</span>';

                return `
                    <tr>
                        <td>${relationshipType || '<span class="empty">-</span>'}</td>
                        <td>${targetLink}</td>
                    </tr>
                `;
            }).join('');

            tbody.innerHTML = rowsHtml;
            const recKey = relationshipsList.length !== 1 ? 'committee.records' : 'committee.record';
            if (footer) footer.textContent = relationshipsList.length + ' ' + (loadI(recKey) || (relationshipsList.length !== 1 ? 'records' : 'record'));
        } catch (error) {
            console.error('Failed to load committee relationships:', error);
            tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">' + (loadI('committee.failedLoadRelationships') || 'Failed to load relationships').replace('{error}', error.message || 'Unknown error') + '</td></tr>';
            if (footer) footer.textContent = '0 ' + (loadI('committee.records') || 'records');
        }
    }

    // Build family lineage tree for committees - following Regulatory Theme pattern
    function buildFamilyLineage(committees, currentCommitteeId) {
        const byId = new Map();
        committees.forEach(c => {
            const id = parseInt(c.ID ?? c.id);
            byId.set(id, c);
        });
        
        const currentId = parseInt(currentCommitteeId);
        const currentCommittee = byId.get(currentId);
        
        if (!currentCommittee) {
            console.log('Current committee not found');
            return committees;
        }
        
        // Find all ancestors (parents, grandparents, etc.)
        const ancestors = new Set();
        let current = currentCommittee;
        
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
            const children = committees.filter(c => {
                const parentId = c.Parent_ID ?? c.parent_id ?? c.parentId;
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
        if (currentCommittee) {
            const currentParentId = currentCommittee.Parent_ID ?? currentCommittee.parent_id ?? currentCommittee.parentId;
            if (currentParentId) {
                const parentId = parseInt(currentParentId);
                const siblings = committees.filter(c => {
                    const cParentId = parseInt(c.Parent_ID ?? c.parent_id ?? c.parentId);
                    const cId = parseInt(c.ID ?? c.id);
                    return cParentId === parentId && cId !== currentId;
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
        
        return committees.filter(c => {
            const id = parseInt(c.ID ?? c.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure following Regulatory Theme pattern
    function buildHierarchyTree(committees, rootId) {
        const byParent = new Map();
        const byId = new Map();
        
        committees.forEach(c => {
            const id = parseInt(c.ID ?? c.id);
            const parentId = c.Parent_ID ?? c.parent_id ?? c.parentId;
            const parentIdNum = parentId ? parseInt(parentId) : null;
            byId.set(id, c);
            
            if (!byParent.has(parentIdNum)) {
                byParent.set(parentIdNum, []);
            }
            byParent.get(parentIdNum).push(c);
        });
        
        const rows = [];
        function buildRows(committeeId, depth = 0) {
            const currentCommittee = byId.get(committeeId);
            if (currentCommittee) {
                const childCount = (byParent.get(committeeId) || []).length;
                
                rows.push({
                    node: currentCommittee,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                const children = byParent.get(committeeId) || [];
                children.forEach(c => {
                    buildRows(parseInt(c.ID ?? c.id), depth + 1);
                });
            } else {
                const children = byParent.get(committeeId) || [];
                children.forEach(c => {
                    buildRows(parseInt(c.ID ?? c.id), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    // Render committee hierarchy table following Regulatory Theme pattern
    function renderCommitteeTable(hierarchyRows, currentId) {
        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const treeI = window.I18n?.t.bind(window.I18n) || (k => k);
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const fallbackName = node.primaryName ?? node.PrimaryName ?? node.Name ?? node.name ?? treeI('committee.unnamedCommittee');
            const fallbackDesc = node.description ?? node.Description ?? '';
            const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
            const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="${escapeHtml(treeI('committee.children'))}">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'committee-link current-committee-link' : 'committee-link';
            const viewTitle = (treeI('committee.viewCommittee') || 'View {name}').replace('{name}', escapeHtml(name));
            const link = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/committee/${encodeURIComponent(id)}" title="${viewTitle}">${escapeHtml(name)}</a>`;
            const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();
            
            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-users item-icon"></i><span class="committee-name">${link}</span>${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize committee hierarchy interactions following Regulatory Theme pattern
    function initCommitteeInteractions(containerEl, hierarchyRows) {
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
    function buildDirectLineageTree(committees, currentCommitteeId) {
        const byId = new Map();
        committees.forEach(c => byId.set(parseInt(c.id), c));

        const current = byId.get(parseInt(currentCommitteeId));
        if (!current) return [];

        const ancestors = new Set();
        const descendants = new Set();

        // Add current committee
        descendants.add(parseInt(currentCommitteeId));

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

        // Find all descendants (including current committee's children and their descendants)
        function findDescendants(committeeId) {
            committees.forEach(c => {
                if (parseInt(c.parentId) === committeeId) {
                    const childId = parseInt(c.id);
                    descendants.add(childId);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentCommitteeId));

        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = committees.filter(c => {
                const cParentId = parseInt(c.parentId);
                const cId = parseInt(c.id);
                return cParentId === parentId && cId !== parseInt(currentCommitteeId);
            });

            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }

        // Include the current committee, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentCommitteeId), ...ancestors, ...descendants]);

        // Filter committees to include only the complete family tree
        return committees.filter(c => {
            const id = parseInt(c.id);
            return includedIds.has(id);
        });
    }

    async function loadCommitteeHierarchy(id) {
        const container = document.querySelector('#committeeHierarchyContent .relationships-hierarchy');
        if (!container) return;
        
        try {
            const hierI = window.I18n?.t.bind(window.I18n) || (k => k);
            container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + hierI('committee.loading') + '</div>';
            
            // Fetch all committees from hierarchy endpoint
            console.log('Fetching committees from /api/committee/hierarchy');
            const response = await fetch('/api/committee/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const committees = await response.json();
            
            console.log('Committees loaded for hierarchy:', committees);
            
            if (!Array.isArray(committees) || committees.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No committees found in database</div>';
                return;
            }

            // Build direct lineage tree (current + ancestors + descendants + siblings)
            const filteredCommittees = buildDirectLineageTree(committees, id);
            
            if (filteredCommittees.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">' + hierI('committee.noRelatedCommittees') + '</div>';
                return;
            }

            // Build hierarchy tree
            const hierarchyRows = buildHierarchyTree(filteredCommittees, 0);
            
            // Render table
            const tableHtml = `
                <div class="hierarchy-header">
                    <div class="hierarchy-title">COMMITTEE HIERARCHY</div>
                    <div class="hierarchy-actions">
                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                    </div>
                </div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>Committee</th>
                                <th>Description</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderCommitteeTable(hierarchyRows, id)}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${filteredCommittees.length} record${filteredCommittees.length !== 1 ? 's' : ''}
                </div>
            `;
            
            container.innerHTML = tableHtml;
            
            // Initialize interactions
            initCommitteeInteractions(container, hierarchyRows);
            
        } catch (error) {
            console.error('Failed to load committee hierarchy:', error);
            const errI = window.I18n?.t.bind(window.I18n) || (k => k);
            container.innerHTML = '<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + (errI('committee.failedLoadHierarchy') || 'Failed to load hierarchy data').replace('{error}', error.message || 'Unknown error') + '</div>';
        }
    }

    // Initialize the page
    async function init() {
        const id = parseId();
        if (!id) {
            console.error('No committee ID found in URL');
            document.getElementById('committeeViewContainer').innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);"><h3>' + (window.I18n?.t('message.error') || 'Error') + '</h3><p>' + (window.I18n?.t('committee.noIdInUrl') || 'No committee ID found in URL') + '</p></div>';
            return;
        }

        // Initialize UI components
        initTabs();
        initEditButton();
        initBackButton();

        // Hide edit controls if user is not admin
        await hideEditsIfUnauthenticated();

        // Load the committee data
        await load(id);
        
        // Check and display lock status
        if (window.ViewLockHelper) {
            await window.ViewLockHelper.checkAndDisplayLockStatus('committee', id);
        }

        // Initialize unified edit dropdown
        if (window.EditDropdown && id != null) {
            try {
                window.EditDropdown.initialize('committee', id, {
                    container: '.tab-actions',
                    editUrl: `/view/committee/committee-edit.html?id=${id}`
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }

        // Initialize follow button in header actions
        if (window.addFollowButton) {
            try {
                await window.addFollowButton('committee', id, null, '.form-actions');
            } catch (e) {
                console.error('Failed to initialize follow button for committee:', e);
            }
        }

        // Load initial tab content (default to view tab)
        setTimeout(() => {
            const initialTab = document.querySelector('.tab.active');
            if (initialTab) {
                const tabName = initialTab.getAttribute('data-tab');
                loadTabContent(tabName);
            }
        }, 100);
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();

