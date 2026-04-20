(function() {
    function parseId() {
        // First try URL params (for edit page redirects)
        const urlParams = new URLSearchParams(window.location.search);
        const idFromParams = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(idFromParams)) return idFromParams;
        
        // Fallback to path-based ID (for direct navigation)
        const parts = window.location.pathname.split('/').filter(Boolean);
        // expect /view/project/{id}
        const idx = parts.indexOf('project');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

    function vT(key, fallback, params) {
        if (!window.I18n || typeof window.I18n.t !== 'function') return fallback != null ? fallback : key;
        const str = window.I18n.t(key, params);
        if (str === key && fallback != null) return fallback;
        return str;
    }

    function projectRecordCount(n) {
        const num = Number(n);
        if (num === 1) return vT('project.view.recordOne', '1 record');
        return vT('project.view.recordMany', '{n} records', { n: String(num) });
    }

    function hideEditControls() {
        // Hide the edit dropdown for unauthorized users
        if (window.EditDropdown) {
            window.EditDropdown.hideEditControls();
        }
        const ids = [
            'tabEditBtn',
            'editStakeholdersBtn'
        ];
        ids.forEach(function(id){
            const el = document.getElementById(id);
            if (el) el.style.display = 'none';
        });
        try {
            document.querySelectorAll('[data-action="edit"], #attrEnterEdit').forEach(function(el){
                el.style.display = 'none';
            });
        } catch(_) {}
    }

    async function hideEditsIfUnauthenticated() {
        try {
            const id = parseId();
            if (!id) {
                console.log('[Project] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Project', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Project] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Project] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Project] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Project', { method: 'GET', credentials: 'include' });
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
            '#deleteProjectBtn'
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
        const ids = [
            'tabEditBtn'
        ];
        ids.forEach(function(id){
            const el = document.getElementById(id);
            if (el) {
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            }
        });
        try {
            document.querySelectorAll('[data-action="edit"]').forEach(function(el){
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            });
        } catch(_) {}
        console.log('Edit controls shown');
    }

    function showEditControls() {
        const ids = [
            'tabEditBtn',
            'editStakeholdersBtn'
        ];
        ids.forEach(function(id){
            const el = document.getElementById(id);
            if (el) {
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            }
        });
        try {
            document.querySelectorAll('[data-action="edit"], #attrEnterEdit').forEach(function(el){
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
            });
        } catch(_) {}
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

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class=\"empty\">-</span>'}</div></div>`;
    }

    function renderCollapsible(title, bodyHtml, opts) {
        const id = (opts && opts.id) ? String(opts.id) : `sec-${Math.random().toString(36).slice(2, 8)}`;
        const startOpen = !!(opts && opts.open);
        const iconClass = (opts && opts.icon) ? String(opts.icon) : '';
        return `
            <div class="collapsible-section ${startOpen ? 'open' : ''}" data-collapsible id="${id}">
                <button type="button" class="collapsible-header" aria-expanded="${startOpen}">
                    <span class="caret" aria-hidden="true"></span>
                    ${iconClass ? `<i class="${iconClass}" aria-hidden="true" style="color:var(--text-muted,#6b7280);"></i>` : ''}
                    <span class="title">${title}</span>
                </button>
                <div class="collapsible-body" ${startOpen ? '' : 'style="display:none;"'}>
                    ${bodyHtml}
                </div>
            </div>
        `;
    }

    function renderHierarchy(h) {
        if (!h || (!h.parentId && !h.parentName)) return '<span class="empty">No parent</span>';
        const parent = escapeHtml(h.parentName || `#${h.parentId}`);
        return `<div class="hierarchy"><span class="crumb">${parent}</span></div>`;
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

    function renderRagBadge(rag) {
        if (!rag) return '<span class="empty">-</span>';
        const ragLower = rag.toLowerCase();
        let color = '#6b7280'; // default gray
        if (ragLower.includes('green')) color = '#248567';
        else if (ragLower.includes('amber') || ragLower.includes('yellow')) color = '#f59e0b';
        else if (ragLower.includes('red')) color = '#ef4444';
        return `<span class="badge" style="background-color: ${color}; color: white;">${escapeHtml(rag)}</span>`;
    }

    function renderPublicStatus(viewing) {
        if (!viewing) return '<span class="empty">-</span>';
        
        // If it's a string (resolved name), use it directly
        if (typeof viewing === 'string') {
            const viewingLower = viewing.toLowerCase();
            if (viewingLower.includes('public') || viewingLower.includes('open')) {
                return `<span class="status-badge status-active">${escapeHtml(viewing)}</span>`;
            } else if (viewingLower.includes('private') || viewingLower.includes('restricted')) {
                return `<span class="status-badge status-inactive">${escapeHtml(viewing)}</span>`;
            } else {
                return `<span class="status-badge status-active">${escapeHtml(viewing)}</span>`;
            }
        }
        
        // If it's a number (ID), determine based on value
        if (viewing === 1 || viewing === true || viewing === '1') {
            const pub = vT('systemInterface.options.public', 'Public');
            return `<span class="status-badge status-active">${escapeHtml(pub)}</span>`;
        } else {
            const priv = vT('systemInterface.options.private', 'Private');
            return `<span class="status-badge status-inactive">${escapeHtml(priv)}</span>`;
        }
    }

    function renderUserLink(name, id) {
        if (!name) return '<span class="empty">-</span>';
        if (id) {
            return `<a href="/view/people/${id}">${escapeHtml(name)}</a>`;
        }
        return escapeHtml(name);
    }

    function renderDate(date) {
        if (!date) return '<span class="empty">-</span>';
        try {
            const d = typeof date === 'string' || typeof date === 'number' ? new Date(date) : date;
            if (isNaN(d.getTime())) return escapeHtml(String(date));
            // Format as date only (DD/MM/YYYY) instead of full timestamp
            return escapeHtml(d.toLocaleDateString('en-GB'));
        } catch (_) {
            return escapeHtml(String(date));
        }
    }

    async function resolveReferences(project) {
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // Project type name from project_type
        if (!project.typeName && project.project_type) {
            const typeId = project.project_type;
            console.log('Resolving project type ID:', typeId);
            tasks.push((async () => {
                try {
                    let type = await api.getProjectTypeById(typeId);
                    console.log('Project Type API response:', type);
                    if (type && type.data) type = type.data;
                    project.typeName = type?.primaryname || type?.Name || type?.name || String(typeId);
                    console.log('Resolved project type name:', project.typeName);
                } catch(e) { 
                    console.error('Failed to resolve project type name:', e);
                    project.typeName = String(typeId);
                }
            })());
        }

        // Project classification name from classification
        if (!project.classificationName && project.classification) {
            const classificationId = project.classification;
            console.log('Resolving project classification ID:', classificationId);
            tasks.push((async () => {
                try {
                    let classification = await api.getProjectClassificationById(classificationId);
                    console.log('Project Classification API response:', classification);
                    if (classification && classification.data) classification = classification.data;
                    project.classificationName = classification?.primaryname || classification?.Name || classification?.name || String(classificationId);
                    console.log('Resolved project classification name:', project.classificationName);
                } catch(e) { 
                    console.error('Failed to resolve project classification name:', e);
                    project.classificationName = String(classificationId);
                }
            })());
        }

        // Status name from status
        if (!project.statusName && project.status) {
            const statusId = project.status;
            tasks.push((async () => {
                try {
                    let status = await api.getStatusById(statusId);
                    if (status && status.data) status = status.data;
                    project.statusName = status?.primaryname || status?.Name || status?.name || String(statusId);
                    console.log('Resolved status name:', project.statusName);
                } catch(e) { 
                    console.error('Failed to resolve status name:', e);
                    project.statusName = String(statusId);
                }
            })());
        }

            // Lifecycle status name from lifecycle_status
            if (!project.lifecycleName && project.lifecycle_status) {
                const lifecycleId = project.lifecycle_status;
                console.log('Resolving project lifecycle status ID:', lifecycleId);
                tasks.push((async () => {
                    try {
                        let lifecycle = await api.getProjectLifecycleById(lifecycleId);
                        console.log('Project Lifecycle API response:', lifecycle);
                        if (lifecycle && lifecycle.data) lifecycle = lifecycle.data;
                        project.lifecycleName = lifecycle?.primaryname || lifecycle?.Name || lifecycle?.name || String(lifecycleId);
                        console.log('Resolved project lifecycle name:', project.lifecycleName);
                    } catch(e) {
                        console.error('Failed to resolve project lifecycle name:', e);
                        project.lifecycleName = String(lifecycleId);
                    }
                })());
            }

            // RAG status name from rag
            if (!project.ragName && project.rag) {
                const ragId = project.rag;
                console.log('Resolving project RAG ID:', ragId);
                tasks.push((async () => {
                    try {
                        let rag = await api.getProjectRagById(ragId);
                        console.log('Project RAG API response:', rag);
                        if (rag && rag.data) rag = rag.data;
                        project.ragName = rag?.primaryname || rag?.Name || rag?.name || String(ragId);
                        console.log('Resolved project RAG name:', project.ragName);
                    } catch(e) {
                        console.error('Failed to resolve project RAG name:', e);
                        project.ragName = String(ragId);
                    }
                })());
            }

        // Created by person name from createdby_id
        if (!project.createdByName && project.createdby_id) {
            const userId = project.createdby_id;
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(userId);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    project.createdByName = fullName || person?.Email || person?.email || String(userId);
                    console.log('Resolved created by name:', project.createdByName);
                } catch(e) {
                    console.error('Failed to resolve created by name:', e);
                    project.createdByName = String(userId);
                }
            })());
        }

        // Last updated by person name from lastupdateuser_id
        // If lastupdateuser_id is null/0 or lastupdatedatetime is null/same as created, use CreatedBy and CreatedDate instead
        const lastUpdateUserId = project.lastupdateuser_id || project.last_updated_user_id || project.lastUpdateUserId;
        const lastUpdateDatetime = project.lastupdatedatetime || project.last_updated_datetime || project.lastUpdatedDatetime;
        const createdDatetime = project.createdatetime || project.created_datetime || project.createdDatetime;
        const createdById = project.createdby_id || project.created_by_id || project.createdById;
        
        // Resolve lastUpdatedByName if lastUpdateUserId exists
        // We'll check after resolution if we should use CreatedBy instead
        if (lastUpdateUserId && lastUpdateUserId > 0 && !project.lastUpdatedByName) {
            const userId = lastUpdateUserId;
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(userId);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    project.lastUpdatedByName = fullName || person?.Email || person?.email || String(userId);
                    console.log('Resolved last updated by name:', project.lastUpdatedByName);
                } catch(e) {
                    console.error('Failed to resolve last updated by name:', e);
                    project.lastUpdatedByName = String(userId);
                }
            })());
        }

        // BUDG Viewing from is_public field
        if (!project.viewingName && project.is_public) {
            const viewingId = project.is_public;
            console.log('Resolving BUDG Viewing ID:', viewingId);
            tasks.push((async () => {
                try {
                    let viewing = await api.getViewingById(viewingId);
                    console.log('Viewing API response:', viewing);
                    if (viewing && viewing.data) viewing = viewing.data;
                    project.viewingName = viewing?.primaryname || viewing?.Name || viewing?.name || String(viewingId);
                    console.log('Resolved BUDG Viewing name:', project.viewingName);
                } catch(e) {
                    console.error('Failed to resolve BUDG Viewing name:', e);
                    project.viewingName = String(viewingId);
                }
            })());
        }

        // Parent project name from parentid
        if (!project.parentName && (project.parentid || project.parent_id || project.Parent_ID)) {
            const parentId = project.parentid || project.parent_id || project.Parent_ID;
            console.log('Resolving parent project ID:', parentId);
            tasks.push((async () => {
                try {
                    let parent = await api.getProjectById(parentId);
                    console.log('Parent project API response:', parent);
                    if (parent && parent.data) parent = parent.data;
                    project.parentName = parent?.primaryname || parent?.PrimaryName || parent?.name || parent?.Name || String(parentId);
                    project.parentId = parentId; // Store parent ID for reference
                    console.log('Resolved parent project name:', project.parentName);
                } catch(e) {
                    console.error('Failed to resolve parent project name:', e);
                    project.parentName = String(parentId);
                    project.parentId = parentId;
                }
            })());
        }

        await Promise.all(tasks);
        
        // After all resolutions, check if we should use CreatedBy for Last Updated
        // This ensures createdByName is already resolved
        const finalLastUpdateUserId = project.lastupdateuser_id || lastUpdateUserId;
        const finalLastUpdateDatetime = project.lastupdatedatetime || lastUpdateDatetime;
        const finalCreatedDatetime = project.createdatetime || createdDatetime;
        const finalCreatedById = project.createdby_id || createdById;
        
        console.log('Last Updated check:', {
            finalLastUpdateUserId,
            finalCreatedById,
            finalLastUpdateDatetime,
            finalCreatedDatetime,
            lastUpdatedByName: project.lastUpdatedByName,
            createdByName: project.createdByName
        });
        
        // Check if last updated is actually different from created
        let finalHasBeenUpdated = false;
        if (finalLastUpdateUserId && finalLastUpdateUserId > 0 && finalLastUpdateDatetime && finalCreatedDatetime) {
            const lastUpdateTime = new Date(finalLastUpdateDatetime).getTime();
            const createdTime = new Date(finalCreatedDatetime).getTime();
            // Consider timestamps equal if within 1 second (to handle precision differences)
            const timeDiff = Math.abs(lastUpdateTime - createdTime);
            const timesAreEqual = timeDiff < 1000; // Less than 1 second difference
            // Consider it updated only if datetime is significantly different OR user is different
            finalHasBeenUpdated = !timesAreEqual || 
                            (finalLastUpdateUserId !== finalCreatedById && finalCreatedById != null);
            console.log('Has been updated check:', {
                lastUpdateTime,
                createdTime,
                timeDiff,
                timesAreEqual,
                userDiff: finalLastUpdateUserId !== finalCreatedById,
                finalHasBeenUpdated
            });
        }
        
        // Logic for Last Updated display:
        // 1. If lastupdateuser_id is NULL → never edited → show empty ("-")
        // 2. If lastupdateuser_id == createdById AND lastupdatedatetime == createdatetime → first edit with no changes → show CreatedBy/CreatedDate
        // 3. Otherwise → real update → show actual Last Updated values
        
        // Use loose equality (==) to handle string/number type differences
        const userIdsMatch = finalLastUpdateUserId == finalCreatedById; // Use == for type coercion
        
        if (!finalLastUpdateUserId || finalLastUpdateUserId === 0 || finalLastUpdateUserId === null) {
            // Object has never been edited - keep NULL to show empty
            project.lastUpdatedByName = null;
            project.lastupdateuser_id = null;
            project.lastupdatedatetime = null;
        } else if (userIdsMatch && !finalHasBeenUpdated) {
            // Object was edited but no changes were made (first edit) - use CreatedBy and CreatedDate
            project.lastUpdatedByName = project.createdByName;
            project.lastupdateuser_id = finalCreatedById;
            project.lastupdatedatetime = finalCreatedDatetime;
        } else {
            // Real update detected - keep actual Last Updated values
        }
        
        return project;
    }

    // Guest / segment access control for Project view
    let _projectGuestCache = null;
    async function isGuestVisitorForProject() {
        if (_projectGuestCache !== null) {
            return _projectGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _projectGuestCache = true;
                return _projectGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _projectGuestCache = !isAuthenticated || isGuestRole;
            return _projectGuestCache;
        } catch (_) {
            _projectGuestCache = true;
            return _projectGuestCache;
        }
    }

    function renderProjectGuestAccessDenied(container, projectId, segmentName, segmentId) {
        if (!container) return;
        const title = vT('error.segmentAccess.title', 'Cannot access this item');
        const message = vT('error.segmentAccess.message', 'This item is not available for guest users.');
        const code = 'ERR-SEGMENT-NOT-ENTERPRISE';
        const segmentLabel = segmentName || ((segmentId !== undefined && segmentId !== null)
            ? vT('common.segmentIdDisplay', 'ID {id}', { id: String(segmentId) })
            : vT('common.unknown', 'Unknown'));
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
                    <div style="margin-bottom: 0.5rem;">${esc(vT('label.segment', 'Segment'))}: <strong>${esc(segmentLabel)}</strong></div>
                    <div>${esc(vT('project.view.errorCodeLabel', 'Error code:'))} <code>${code}</code></div>
                </div>
            </div>
        `;
    }

    async function load(id) {
        const container = document.getElementById('projectViewContainer');
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + escapeHtml(vT('message.loading', 'Loading...')) + '</div>';
        try {
            const data = await window.BUDG_API_SERVICE.getProjectById(id);

            // Resolve foreign key references
            const resolvedData = await resolveReferences(data);

            // Guest / segment gating: unauthenticated users may only view Enterprise segment
            try {
                const isGuest = await isGuestVisitorForProject();
                const segmentName = resolvedData.segmentName || resolvedData.segment_name || resolvedData.segment;
                const segmentId = resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();

                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Project] Guest visitor blocked from non-Enterprise segment view', {
                        projectId: id,
                        segmentName,
                        segmentId
                    });
                    renderProjectGuestAccessDenied(container, id, segmentName, segmentId);
                    return;
                }
            } catch (gateError) {
                console.warn('[Project] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            
            try { window.BUDG_API_SERVICE.logVisit({ userId: 1, entity: 'Project', entityId: String(id), route: `/view/project/${id}` }); } catch(_) {}

            // Update header with project data (dynamic title policy)
            const titleElement = document.getElementById('projectTitle');
            const breadcrumbElement = document.getElementById('projectBreadcrumb');
            
            if (titleElement) {
                // Get project name - try multiple field name variations
                const projectName = resolvedData.primaryname || resolvedData.primaryName || 
                                  resolvedData.name || resolvedData.Name || 
                                  resolvedData.description || vT('project.view.unnamedProject', 'Unnamed Project');
                
                // Get reference number if available
                const refNumber = resolvedData.refnumber || resolvedData.refNumber || 
                                 resolvedData.ref_number || '';
                
                // Format title: "REF: Name" or just "Name"
                const titleText = refNumber ? `${refNumber}: ${projectName}` : projectName;
                titleElement.textContent = titleText;
                
                console.log('Updated project title to:', titleText);
            } else {
                console.warn('projectTitle element not found - cannot update title');
            }
            
            if (breadcrumbElement) {
                breadcrumbElement.textContent = 'Project';
            }

            // Render parent field with link if parent exists
            const parentId = resolvedData.parentid || resolvedData.parent_id || resolvedData.Parent_ID || resolvedData.parentId;
            const parentName = resolvedData.parentName || resolvedData.parent_name || resolvedData.Parent_Name;
            let parentHtml = '<span class="empty">-</span>';
            if (parentName) {
                if (parentId) {
                    parentHtml = `<a href="/view/project/${parentId}">${escapeHtml(parentName)}</a>`;
                } else {
                    parentHtml = escapeHtml(parentName);
                }
            } else if (parentId) {
                parentHtml = `<a href="/view/project/${parentId}">#${parentId}</a>`;
            }

            const left = `
                <div class="view-section">
                    <div class="section-title"><i class="fa-solid fa-circle-info"></i>${escapeHtml(vT('glossary.sections.definition', 'DEFINITION'))}</div>
                    ${renderItem(vT('label.name', 'Name'), escapeHtml(resolvedData.primaryname || resolvedData.name || ''))}
                    ${renderItem(vT('label.ref', 'Ref.'), escapeHtml(resolvedData.refnumber || resolvedData.ref_number))}
                    ${renderItem(vT('label.parent', 'Parent'), parentHtml)}
                    ${renderItem(vT('label.type', 'Type'), escapeHtml(resolvedData.typeName || resolvedData.project_type || resolvedData.project_type_name || resolvedData.type || vT('value.unknown', 'Unknown')))}
                    ${renderItem(vT('label.classification', 'Classification'), escapeHtml(resolvedData.classificationName || resolvedData.classification || resolvedData.classification_name || vT('message.notSpecified', 'Not specified')))}
                </div>
            `;

            const right = `
                <div class="view-section">
                    <div class="section-title"><i class="fa-solid fa-layer-group"></i>${escapeHtml(vT('glossary.sections.classifications', 'CLASSIFICATIONS'))}</div>
                    <div class="section-subtitle">${escapeHtml(vT('glossary.sections.basicClassifications', 'BASIC CLASSIFICATIONS'))}</div>
                    ${renderItem(vT('glossary.labels.budgStatus', 'BUDG Status'), renderStatusBadge(resolvedData.statusName || resolvedData.status_name || resolvedData.status || vT('value.unknown', 'Unknown'), (resolvedData.statusName || resolvedData.status) === 'Active'))}
                    ${renderItem(vT('glossary.labels.lifecycle', 'Lifecycle'), renderLifecycleBadge(resolvedData.lifecycleName || resolvedData.lifecycle_name || resolvedData.lifecycle_status || vT('value.unknown', 'Unknown')))}
                    ${renderItem(vT('glossary.labels.budgViewing', 'BUDG Viewing'), renderPublicStatus(resolvedData.viewingName || resolvedData.is_public || resolvedData.isPublic))}
                    <div class="section-subtitle">${escapeHtml(vT('glossary.sections.otherInformation', 'OTHER INFORMATION'))}</div>
                    ${renderItem(vT('label.rag', 'RAG'), renderRagBadge(resolvedData.ragName || resolvedData.rag_name || resolvedData.rag))}
                    ${renderItem(vT('label.startDate', 'Start Date'), renderDate(resolvedData.startdate || resolvedData.start_date))}
                    ${renderItem(vT('label.endDate', 'End Date'), renderDate(resolvedData.enddate || resolvedData.end_date))}
                    ${renderItem(vT('glossary.labels.createdBy', 'Created By'), renderUserLink(resolvedData.createdByName || resolvedData.created_by_name, resolvedData.createdby_id || resolvedData.created_by_id))}
                    ${renderItem(vT('glossary.labels.created', 'Created'), renderDate(resolvedData.createdatetime || resolvedData.created_datetime || resolvedData.created_date))}
                    ${renderItem(vT('glossary.labels.lastUpdatedBy', 'Last Updated By'), renderUserLink(resolvedData.lastUpdatedByName || resolvedData.last_updated_by_name, resolvedData.lastupdateuser_id || resolvedData.last_updated_user_id))}
                    ${renderItem(vT('glossary.labels.lastUpdated', 'Last Updated'), renderDate(resolvedData.lastupdatedatetime || resolvedData.last_updated_datetime || resolvedData.last_updated_date))}
                    ${renderItem(vT('glossary.labels.segment', 'Segment'), escapeHtml(resolvedData.segmentName || resolvedData.segment_name || resolvedData.segment || ((resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID) != null ? vT('common.segmentIdDisplay', 'ID {id}', { id: resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID }) : vT('message.notSpecified', 'Not specified'))))}
                </div>
            `;

            const noDocsText = window.I18n?.t('message.noDocuments') || 'No documents';
            const docsEmpty = '<div class="empty">' + noDocsText + '.</div>';
            const documentsTitle = window.I18n?.t('card.documents') || 'DOCUMENTS';

            const fullWidth = `
                <div class="view-section" style="grid-column:1/-1;">
                    ${renderCollapsible(documentsTitle, docsEmpty, { id: 'documents', icon: 'fa-solid fa-file-lines', open: true })}
                </div>
            `;

            container.innerHTML = left + right + fullWidth;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Project',
                        containerId: 'projectViewContainer',
                        objectId: id,
                        title: vT('card.customFields', 'CUSTOM FIELDS')
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }
            console.log('Project content loaded successfully');
            console.log('Container innerHTML length:', container.innerHTML.length);
            console.log('Full width section:', fullWidth);
            console.log('Container height:', container.offsetHeight);
            console.log('Container scrollHeight:', container.scrollHeight);

            // Wire up collapsible toggles
            container.querySelectorAll('[data-collapsible] .collapsible-header').forEach(function(btn){
                btn.addEventListener('click', function(){
                    const section = this.closest('[data-collapsible]');
                    const body = section.querySelector('.collapsible-body');
                    const isOpen = section.classList.contains('open');
                    section.classList.toggle('open');
                    this.setAttribute('aria-expanded', String(!isOpen));
                    if (body) body.style.display = isOpen ? 'none' : '';
                });
            });

            // Load Documents
            loadDocuments(id);

            return resolvedData;
        } catch (e) {
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const message = isForbidden ? vT('capability.view.objectNotAvailable', 'This object is not available.') : vT('project.view.loadFailed', 'Failed to load project (id={id}).', { id: String(id) });
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column: 1/-1; color: var(--danger, #b91c1c);\">${escapeHtml(message)}</div>`;
            return null;
        }
    }

    async function loadDocuments(projectId) {
        // Find the documents collapsible section
        const documentsSection = document.querySelector('#documents .collapsible-body');
        if (!documentsSection) {
            console.warn('Documents section not found');
            return;
        }

        // Create container for document table
        const containerId = 'projectDocumentsTableContainer';
        documentsSection.innerHTML = `<div id="${containerId}"></div>`;

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                new DocumentTableComponent({
                    facetType: 'project',
                    facetId: projectId,
                    container: `#${containerId}`,
                    canEdit: false // Read-only in view mode
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

    async function loadProjectImpactData(id) {
        const container = document.getElementById('projectImpactContainer');
        if (!container) return Promise.resolve();
        
        console.log('Loading project impact data for project ID:', id);
        
        try {
            // Call the impact view initialization function from project-impact.js
            if (typeof initImpactView === 'function') {
                await initImpactView(id, container);
            } else {
                console.warn('initImpactView function not found, impact tab may not be loaded');
                container.innerHTML = `
                    <div class="view-section" style="grid-column: 1/-1;">
                        <div class="impact-section">
                            <div class="impact-header">
                                <div class="impact-title">${escapeHtml(vT('project.view.impactAnalysis', 'Impact Analysis'))}</div>
                            </div>
                            <div class="impact-content">
                                <div class="impact-empty">
                                    <i class="fas fa-exclamation-triangle"></i>
                                    <span>${escapeHtml(vT('project.view.impactModuleNotLoaded', 'Impact module not loaded'))}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
            }
            return Promise.resolve();
        } catch (e) {
            console.error('Failed to load impact data:', e);
            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1;">
                    <div class="impact-section">
                        <div class="impact-header">
                            <div class="impact-title">${escapeHtml(vT('project.view.impactAnalysis', 'Impact Analysis'))}</div>
                        </div>
                        <div class="impact-content">
                            <div class="impact-empty">
                                <i class="fas fa-exclamation-triangle"></i>
                                <span>${escapeHtml(vT('project.view.loadImpactFailed', 'Failed to load impact data'))}</span>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            return Promise.resolve();
        }
    }

    async function loadProjectData(id) {
        const container = document.getElementById('projectDataContainer');
        if (!container) return Promise.resolve();
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + escapeHtml(vT('project.view.loadingData', 'Loading data...')) + '</div>';
        
        try {
            // Placeholder for project data
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">${escapeHtml(vT('project.view.dataSection', 'PROJECT DATA'))}</div>
                    <div class="empty">${escapeHtml(vT('project.view.noData', 'No data available for this project'))}</div>
                </div>
            `;
            return Promise.resolve();
        } catch (e) {
            console.error('Failed to load project data:', e);
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    ${escapeHtml(vT('project.view.loadDataFailed', 'Failed to load project data'))}
                </div>
            `;
            return Promise.resolve();
        }
    }

    async function loadProjectHistory(id) {
        const container = document.getElementById('projectHistoryContainer');
        if (!container) return Promise.resolve();
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + escapeHtml(vT('project.view.loadingHistory', 'Loading history...')) + '</div>';
        
        try {
            // Placeholder for project history
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">${escapeHtml(vT('project.view.historySection', 'PROJECT HISTORY'))}</div>
                    <div class="empty">${escapeHtml(vT('project.view.noHistory', 'No history available for this project'))}</div>
                </div>
            `;
            return Promise.resolve();
        } catch (e) {
            console.error('Failed to load project history:', e);
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    ${escapeHtml(vT('project.view.loadHistoryFailed', 'Failed to load project history'))}
                </div>
            `;
            return Promise.resolve();
        }
    }

    async function loadProjectChange(id) {
        const container = document.getElementById('projectChangeContainer');
        if (!container) {
            console.error('Project change container not found');
            return Promise.resolve();
        }
        
        try {
            // Initialize change tab component with sub-tabs (Change Requests and Workflow)
            if (window.ChangeTabComponent) {
                window.ChangeTabComponent.initialize('project', id, 'projectChangeContainer');
            } else {
                console.error('ChangeTabComponent not available');
                container.innerHTML = `
                    <div class="view-section" style="grid-column:1/-1;">
                        <div class="section-title">${escapeHtml(vT('project.view.changeSection', 'PROJECT CHANGE'))}</div>
                        <div class="empty-state">
                            <i class="fas fa-exclamation-triangle"></i>
                            <p>${escapeHtml(vT('project.view.changeScriptHint', 'Change component not available. Please ensure change-tab-component.js is loaded.'))}</p>
                        </div>
                    </div>
                `;
            }
            return Promise.resolve();
        } catch (e) {
            console.error('Failed to load project change:', e);
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                    ${escapeHtml(vT('project.view.changeLoadFailed', 'Failed to load project change data: {error}', { error: e.message || vT('common.unknownError', 'Unknown error') }))}
                </div>
            `;
            return Promise.resolve();
        }
    }

    document.addEventListener('DOMContentLoaded', function() {
        // Hide edit controls for guests
        hideEditsIfUnauthenticated();
        
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', function(){ window.history.length > 1 ? window.history.back() : window.location.assign('/'); });
        
        const id = parseId();
        if (id != null) {
            load(id).then(async function(data) {
                const summaryContainer = document.getElementById('projectViewContainer');
                if (summaryContainer) {
                    window.projectTabData.summary = summaryContainer.innerHTML;
                }

                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('project', id);
                }

                // Initialize unified edit dropdown
                if (window.EditDropdown && id != null) {
                    try {
                        // Check if user can edit this object (has permission AND is stakeholder)
                        let canEditThisObject = false;
                        try {
                            if (window.checkCanEditObject) {
                                const editCheck = await window.checkCanEditObject('Project', id);
                                canEditThisObject = editCheck.canEdit;
                                console.log('[Project] EditDropdown: User can edit object?', canEditThisObject, '(isStakeholder:', editCheck.isStakeholder, ')');
                            }
                        } catch (permError) {
                            console.error('[Project] EditDropdown: Error checking permissions:', permError);
                            canEditThisObject = false; // Fail securely
                        }
                        
                        window.EditDropdown.initialize('project', id, {
                            container: '.tab-actions',
                            editUrl: `/view/project/project-edit.html?id=${id}`,
                            hideEditOption: !canEditThisObject // Hide if user cannot edit
                        });
                    } catch (error) {
                        console.error('Failed to initialize edit dropdown:', error);
                    }
                }

                // Initialize follow button in header actions
                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('project', id, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for project:', e);
                    }
                }
            });
        }

        // Wire up tab edit button based on active tab
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.addEventListener('click', function() {
                if (id != null) {
                    const activeTab = document.querySelector('.tab.active');
                    const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                    
                    // Navigate to edit page with tab parameter
                    window.location.href = `/view/project/project-edit.html?id=${id}&tab=${tabName}`;
                }
            });
        }

        // Tab data storage - make it global
        if (!window.projectTabData) {
            window.projectTabData = {
                summary: null,
                relationships: null,
                stakeholders: null,
                impact: null,
                data: null,
                history: null,
                change: null
            };
        }

        // Tabs: Summary, Relationships, Stakeholders, Impact, Data, History, Change
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(function(btn){
            btn.addEventListener('click', function(){
                tabs.forEach(function(b){ b.classList.remove('active'); });
                this.classList.add('active');

                const body = document.querySelector('.content-body');
                if (!body) return;

                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                
                // Hide all containers and clear their display property
                const containers = [
                    'projectViewContainer',
                    'projectRelationshipsContainer',
                    'projectStakeholdersContainer', 
                    'projectImpactContainer',
                    'projectDataContainer',
                    'projectHistoryContainer',
                    'projectChangeContainer'
                ];
                containers.forEach(function(containerId) {
                    const container = document.getElementById(containerId);
                    if (container) {
                        container.style.display = 'none';
                        container.style.removeProperty('grid-column');
                    }
                });

                if (which === 'summary') {
                    const summaryContainer = document.getElementById('projectViewContainer');
                    if (summaryContainer) {
                        summaryContainer.style.display = 'grid';
                        // Always reload data when switching to summary tab
                        if (id != null && !window.projectTabData.summary) {
                            load(id).then(function(data) {
                                window.projectTabData.summary = summaryContainer.innerHTML;
                            });
                        }
                        
                    }
                } else if (which === 'relationships') {
                    const relationshipsContainer = document.getElementById('projectRelationshipsContainer');
                    if (relationshipsContainer) {
                        relationshipsContainer.style.display = 'grid';
                        // Load data only if not cached
                        
                        if (id != null && !window.projectTabData.relationships) {
                            relationshipsContainer.innerHTML = `
                                <div class="view-section relationships-hierarchy" style="grid-column:1/-1;">
                                    <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${escapeHtml(vT('message.loading', 'Loading...'))}</div>
                                </div>
                            `;

                            // Load hierarchy data immediately
                            loadProjectHierarchy(id).then(function() {
                                window.projectTabData.relationships = relationshipsContainer.innerHTML;
                            });
                        }
                    }
                } else if (which === 'stakeholders') {
                    const stakeholdersContainer = document.getElementById('projectStakeholdersContainer');
                    if (stakeholdersContainer) {
                        stakeholdersContainer.style.display = 'grid';
                        // Load stakeholders using the same approach as committee
                        if (id != null && !window.projectTabData.stakeholders) {
                            // Initialize project stakeholder view
                            if (window.ProjectStakeholderView) {
                                window.ProjectStakeholderView.init(id);
                                setTimeout(function() {
                                    window.projectTabData.stakeholders = stakeholdersContainer.innerHTML;
                                }, 500);
                            }
                        }
                    }
                } else if (which === 'impact') {
                    const impactContainer = document.getElementById('projectImpactContainer');
                    if (impactContainer) {
                        impactContainer.style.display = 'grid';
                        // Load data only if not cached
                        if (id != null && !window.projectTabData.impact) {
                            loadProjectImpactData(id).then(function() {
                                window.projectTabData.impact = impactContainer.innerHTML;
                            });
                        }
                    }
                } else if (which === 'data') {
                    const dataContainer = document.getElementById('projectDataContainer');
                    if (dataContainer) {
                        dataContainer.style.display = 'grid';
                        dataContainer.style.visibility = 'visible';
                        // Always reload data when switching to data tab (same as process facet: Data + Data Map sub-tabs)
                        const projectId = id;
                        if (projectId != null && window.loadProjectData) {
                            window.loadProjectData(projectId);
                        } else if (projectId != null) {
                            dataContainer.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">' + escapeHtml(vT('project.view.projectDataViewUnavailable', 'Project data view not available')) + '</div>';
                        } else {
                            dataContainer.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">' + escapeHtml(vT('project.view.dataSection', 'PROJECT DATA')) + '</div><p style="color:var(--text-muted,#6b7280);">' + escapeHtml(vT('project.view.openProjectToViewData', 'Open a project to view data.')) + '</p></div>';
                        }
                    }
                } else if (which === 'history') {
                    const historyContainer = document.getElementById('projectHistoryContainer');
                    if (historyContainer) {
                        historyContainer.style.display = 'block';
                        // Always reload data when switching to history tab
                        if (id != null) {
                            console.log('Loading HISTORY tab for project ID:', id);
                            
                            // Initialize history component for Project
                            if (window.initializeHistoryComponent) {
                                window.initializeHistoryComponent('Projects', id, 'projectHistoryContainer');
                            } else {
                                console.error('History component not available');
                                historyContainer.innerHTML = `
                                    <div class="view-section" style="grid-column:1/-1;">
                                        <div class="section-title">${escapeHtml(vT('project.view.historySection', 'PROJECT HISTORY'))}</div>
                                        <div class="empty">${escapeHtml(vT('project.view.historyComponentUnavailable', 'History component not available'))}</div>
                                    </div>
                                `;
                            }
                        }
                    }
                } else if (which === 'change') {
                    const changeContainer = document.getElementById('projectChangeContainer');
                    if (changeContainer) {
                        changeContainer.style.display = 'grid';
                        // Always initialize change tab component (don't cache, needs to be re-initialized)
                        if (id != null) {
                            // Clear any existing content first
                            changeContainer.innerHTML = '';
                            // Initialize change tab component
                            loadProjectChange(id).catch(error => {
                                console.error('Error loading project change:', error);
                            });
                        } else {
                            console.error('Project ID is null, cannot load change tab');
                            changeContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">
                                    <p>Error: Project ID not found</p>
                                </div>
                            `;
                        }
                    } else {
                        console.error('Project change container not found');
                    }
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('projectStakeholdersContainer');
                }
            });
        });
    });
    
    // Debug DOM elements when ready
    document.addEventListener('DOMContentLoaded', function() {
        console.log('=== DOM CONTENT LOADED ===');
        console.log('projectViewContainer exists:', !!document.getElementById('projectViewContainer'));
        console.log('projectTitle exists:', !!document.getElementById('projectTitle'));
        console.log('projectBreadcrumb exists:', !!document.getElementById('projectBreadcrumb'));
        console.log('All elements with IDs:', Array.from(document.querySelectorAll('[id]')).map(el => el.id));
        
        const id = parseId();
        console.log('Parsed ID from URL:', id);
        
        if (id) {
            console.log('Auto-loading project with ID:', id);
            load(id).then(function(data) {
                const summaryContainer = document.getElementById('projectViewContainer');
                if (summaryContainer && window.projectTabData) {
                    // Add small delay to ensure all styling is applied
                    setTimeout(function() {
                        window.projectTabData.summary = summaryContainer.innerHTML;
                    }, 100);
                }
            }).catch(e => console.error('Auto-load failed:', e));
        } else {
            console.log('No valid ID found in URL, skipping auto-load');
        }
    });

    // Build family lineage tree for projects - following Regulatory Theme pattern
    function buildFamilyLineage(projects, currentProjectId) {
        const byId = new Map();
        projects.forEach(p => {
            const id = parseInt(p.ID ?? p.id);
            byId.set(id, p);
        });
        
        const currentId = parseInt(currentProjectId);
        const currentProject = byId.get(currentId);
        
        if (!currentProject) {
            console.log('Current project not found');
            return projects;
        }
        
        // Find all ancestors (parents, grandparents, etc.)
        const ancestors = new Set();
        let current = currentProject;
        
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
            const children = projects.filter(p => {
                const parentId = p.Parent_ID ?? p.parent_id ?? p.parentId;
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
        if (currentProject) {
            const currentParentId = currentProject.Parent_ID ?? currentProject.parent_id ?? currentProject.parentId;
            if (currentParentId) {
                const parentId = parseInt(currentParentId);
                const siblings = projects.filter(p => {
                    const pParentId = parseInt(p.Parent_ID ?? p.parent_id ?? p.parentId);
                    const pId = parseInt(p.ID ?? p.id);
                    return pParentId === parentId && pId !== currentId;
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
        
        return projects.filter(p => {
            const id = parseInt(p.ID ?? p.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure following Regulatory Theme pattern
    function buildHierarchyTree(projects, rootId) {
        const byParent = new Map();
        const byId = new Map();
        
        projects.forEach(p => {
            const id = parseInt(p.ID ?? p.id);
            const parentId = p.Parent_ID ?? p.parent_id ?? p.parentId;
            const parentIdNum = parentId ? parseInt(parentId) : null;
            byId.set(id, p);
            
            if (!byParent.has(parentIdNum)) {
                byParent.set(parentIdNum, []);
            }
            byParent.get(parentIdNum).push(p);
        });
        
        const rows = [];
        function buildRows(projectId, depth = 0) {
            const currentProject = byId.get(projectId);
            if (currentProject) {
                const childCount = (byParent.get(projectId) || []).length;
                
                rows.push({
                    node: currentProject,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                const children = byParent.get(projectId) || [];
                children.forEach(p => {
                    buildRows(parseInt(p.ID ?? p.id), depth + 1);
                });
            } else {
                const children = byParent.get(projectId) || [];
                children.forEach(p => {
                    buildRows(parseInt(p.ID ?? p.id), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    // Render project hierarchy table following Regulatory Theme pattern - with all columns
    function renderProjectTable(hierarchyRows, currentId) {
        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const fallbackName = node.primaryname ?? node.primaryName ?? node.name ?? vT('project.view.unnamedProject', 'Unnamed Project');
            const fallbackDesc = node.description ?? node.Description ?? '';
            const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
            const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
            const refNumber = isMaskedNode ? Mask.PLACEHOLDER : (node.refnumber ?? node.refNumber ?? '');
            const ragName = isMaskedNode ? '' : (node.ragName ?? '');
            const lifecycleName = isMaskedNode ? '' : (node.lifecycleName ?? '');
            const startDate = (!isMaskedNode && node.startdate) ? formatDate(node.startdate) : '';
            const endDate = (!isMaskedNode && node.enddate) ? formatDate(node.enddate) : '';
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentId ?? node.parentid ?? '';

            // Ensure depth is non-negative for Array() constructor
            const safeDepth = Math.max(0, depth || 0);
            const indent = Array(safeDepth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle" data-id="${id}"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'project-link current-project-link' : 'project-link';
            const link = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/project/${encodeURIComponent(id)}" title="${escapeHtml(vT('project.view.viewProjectTitle', 'View {name}', { name: name }))}">${escapeHtml(name)}</a>`;
            const branchLine = safeDepth > 0 ? '<span class="tree-branch"></span>' : '';
            const rowClasses = `hierarchy-row level-${safeDepth} ${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${safeDepth}" data-level="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td class="ref-cell">
                    <div class="tree-cell">
                        ${indent}${expander}${branchLine}
                        <i class="fas fa-project-diagram item-icon"></i>
                        <span class="ref-number">${escapeHtml(refNumber || 'N/A')}</span>
                        ${countBadge}
                    </div>
                </td>
                <td class="project-cell">
                    <div class="project-name">
                        ${link}
                    </div>
                </td>
                <td class="description-cell">
                    <span title="${escapeHtml(desc)}">${escapeHtml(desc || 'No description')}</span>
                </td>
                <td class="rag-cell">
                    ${ragName ? `<span class="rag-badge">${escapeHtml(ragName)}</span>` : '<span class="empty">-</span>'}
                </td>
                <td class="lifecycle-cell">
                    ${lifecycleName ? `<span class="lifecycle-badge">${escapeHtml(lifecycleName)}</span>` : '<span class="empty">-</span>'}
                </td>
                <td class="startdate-cell">
                    ${startDate ? `<span>${escapeHtml(startDate)}</span>` : '<span class="empty">-</span>'}
                </td>
                <td class="enddate-cell">
                    ${endDate ? `<span>${escapeHtml(endDate)}</span>` : '<span class="empty">-</span>'}
                </td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }
    
    // Helper function to format date
    function formatDate(dateValue) {
        if (!dateValue) return '';
        try {
            const date = new Date(dateValue);
            if (isNaN(date.getTime())) return String(dateValue);
            const day = String(date.getDate()).padStart(2, '0');
            const month = date.toLocaleString('en-US', { month: 'short' });
            const year = date.getFullYear();
            return `${day}-${month}-${year}`;
        } catch (e) {
            return String(dateValue);
        }
    }

    // Initialize project hierarchy interactions following Regulatory Theme pattern (like process hierarchy)
    function initProjectInteractions(containerEl, hierarchyRows, currentProjectId = null) {
        const tbody = containerEl.querySelector('.hierarchy-table tbody');
        if (!tbody) return;
        
        // Get current project ID from parameter or URL
        if (!currentProjectId) {
            const urlParams = new URLSearchParams(window.location.search);
            currentProjectId = urlParams.get('id') ? parseInt(urlParams.get('id')) : null;
        }
        
        // Build children map - group by parent ID
        const childrenByParent = new Map();
        const allRows = Array.from(tbody.querySelectorAll('tr.hierarchy-row'));
        const rowById = new Map();
        
        allRows.forEach(tr => {
            const id = parseInt(tr.getAttribute('data-id'));
            const parentId = tr.getAttribute('data-parent-id');
            const parentIdNum = parentId && parentId !== '' ? parseInt(parentId) : null;
            
            rowById.set(id, tr);
            
            if (!childrenByParent.has(parentIdNum)) {
                childrenByParent.set(parentIdNum, []);
            }
            childrenByParent.get(parentIdNum).push(tr);
        });
        
        // Function to get all ancestors of a project
        function getAncestors(projectId) {
            const ancestors = [];
            let currentId = projectId;
            const visited = new Set();
            
            while (currentId && !visited.has(currentId)) {
                visited.add(currentId);
                const row = rowById.get(currentId);
                if (row) {
                    const parentId = row.getAttribute('data-parent-id');
                    if (parentId && parentId !== '') {
                        const parentIdNum = parseInt(parentId);
                        ancestors.push(parentIdNum);
                        currentId = parentIdNum;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return ancestors;
        }
        
        // Function to expand path to current project
        function expandPathToProject(projectId) {
            const row = rowById.get(projectId);
            if (!row) return;
            
            // Show this row
            row.style.display = '';
            row.classList.remove('collapsed');
            row.classList.add('expanded');
            
            // Update expander icon
            const expander = row.querySelector('.tree-expander');
            if (expander) {
                const icon = expander.querySelector('i');
                if (icon) {
                    icon.classList.remove('fa-caret-right');
                    icon.classList.add('fa-caret-down');
                }
            }
            
            // If it has a parent, expand the parent path recursively
            const parentId = row.getAttribute('data-parent-id');
            if (parentId && parentId !== '') {
                expandPathToProject(parseInt(parentId));
            }
        }
        
        // Recursive function to toggle children visibility
        function toggleChildren(parentId, show) {
            const children = childrenByParent.get(parentId) || [];
            children.forEach(childRow => {
                if (show) {
                    childRow.style.display = '';
                    childRow.classList.remove('collapsed');
                    childRow.classList.add('expanded');
                } else {
                    childRow.style.display = 'none';
                    childRow.classList.remove('expanded');
                    childRow.classList.add('collapsed');
                }
                
                // Recursively handle grandchildren
                const childId = parseInt(childRow.getAttribute('data-id'));
                const childExpander = childRow.querySelector('.tree-expander');
                if (childExpander) {
                    const isExpanded = childRow.classList.contains('expanded');
                    toggleChildren(childId, show && isExpanded);
                }
            });
        }
        
        // Expand all items by default - show all children immediately
        allRows.forEach(row => {
            row.style.display = '';
            row.classList.add('expanded');
            row.classList.remove('collapsed');
            
            // Update expander icon to show expanded state
            const expander = row.querySelector('.tree-expander');
            if (expander) {
                const icon = expander.querySelector('i');
                if (icon) {
                    icon.classList.remove('fa-caret-right');
                    icon.classList.add('fa-caret-down');
                }
            }
        });
        
        // Highlight current project
        if (currentProjectId) {
            const currentRow = rowById.get(currentProjectId);
            if (currentRow) {
                currentRow.classList.add('current-row');
            }
        }
        
        // Handle expand/collapse clicks
        tbody.addEventListener('click', function(e) {
            const expander = e.target.closest('.tree-expander');
            if (expander) {
                e.preventDefault();
                e.stopPropagation();
                
                const row = expander.closest('tr.hierarchy-row');
                const projectId = parseInt(row.getAttribute('data-id'));
                const icon = expander.querySelector('i');
                const isExpanded = row.classList.contains('expanded');
                
                if (isExpanded) {
                    // Collapse
                    row.classList.remove('expanded');
                    row.classList.add('collapsed');
                    icon.classList.remove('fa-caret-down');
                    icon.classList.add('fa-caret-right');
                    toggleChildren(projectId, false);
                } else {
                    // Expand
                    row.classList.remove('collapsed');
                    row.classList.add('expanded');
                    icon.classList.remove('fa-caret-right');
                    icon.classList.add('fa-caret-down');
                    toggleChildren(projectId, true);
                }
            }
        });
    }

    // Build direct lineage tree - Following Regulatory Theme pattern
    function buildDirectLineageTree(projects, currentProjectId) {
        const byId = new Map();
        projects.forEach(p => byId.set(parseInt(p.id), p));
        
        const current = byId.get(parseInt(currentProjectId));
        if (!current) return [];
        
        const ancestors = new Set();
        const descendants = new Set();
        
        // Add current project
        descendants.add(parseInt(currentProjectId));
        
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
        
        // Find all descendants (including current project's children and their descendants)
        function findDescendants(projectId) {
            projects.forEach(p => {
                if (parseInt(p.parentId) === projectId) {
                    const childId = parseInt(p.id);
                    descendants.add(childId);
                    findDescendants(childId); // Recursively find all descendants
                }
            });
        }
        findDescendants(parseInt(currentProjectId));
        
        // Find siblings (other children of the same parent) and their descendants
        if (current.parentId) {
            const parentId = parseInt(current.parentId);
            const siblings = projects.filter(p => {
                const pParentId = parseInt(p.parentId);
                const pId = parseInt(p.id);
                return pParentId === parentId && pId !== parseInt(currentProjectId);
            });
            
            // Add siblings and their descendants
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId); // Add all descendants of siblings (nephews/nieces and their descendants)
            });
        }
        
        // Include the current project, all its ancestors, and all its descendants (including siblings and their descendants)
        const includedIds = new Set([parseInt(currentProjectId), ...ancestors, ...descendants]);
        
        // Filter projects to include only the complete family tree
        return projects.filter(p => {
            const id = parseInt(p.id);
            return includedIds.has(id);
        });
    }

    // Function to load project hierarchy data - Following Regulatory Theme pattern
    async function loadProjectHierarchy(projectId) {
        const container = document.querySelector('.relationships-hierarchy');
        if (!container) {
            console.error('Project hierarchy container not found');
            return;
        }
        
        try {
            container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + escapeHtml(vT('message.loading', 'Loading...')) + '</div>';
            
            // Fetch project hierarchy from the /hierarchy/{id} endpoint (includes siblings logic)
            const response = await fetch(`/api/project/hierarchy/${projectId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const hierarchy = await response.json();
            
            if (!Array.isArray(hierarchy) || hierarchy.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">' + escapeHtml(vT('project.view.noHierarchyData', 'No hierarchy data found')) + '</div>';
                return;
            }
            
            // Convert hierarchy to format expected by renderProjectTable
            // Build children map for tree structure (like process hierarchy)
            const childrenMap = new Map();
            const projectById = new Map();
            
            hierarchy.forEach(project => {
                const id = project.id;
                const parentId = project.parentId || project.parentid;
                projectById.set(id, project);
                
                if (parentId) {
                    if (!childrenMap.has(parentId)) {
                        childrenMap.set(parentId, []);
                    }
                    childrenMap.get(parentId).push(project);
                }
            });
            
            // Sort children by name for each parent
            childrenMap.forEach((children, parentId) => {
                children.sort((a, b) => {
                    return (a.primaryname || a.name || '').localeCompare(b.primaryname || b.name || '');
                });
            });
            
            // Build tree-ordered hierarchy (parent followed by its children)
            function buildTreeOrder(rootId) {
                const result = [];
                const project = projectById.get(rootId);
                
                if (!project) return result;
                
                const children = childrenMap.get(rootId) || [];
                const childCount = children.length;
                const hasChildren = childCount > 0;
                const level = Math.max(0, project.level || 0); // Ensure non-negative level
                
                // Add current project
                result.push({
                    node: project,
                    depth: level,
                    childCount: childCount,
                    hasChildren: hasChildren
                });
                
                // Add children recursively (they will have level + 1)
                children.forEach(child => {
                    result.push(...buildTreeOrder(child.id));
                });
                
                return result;
            }
            
            // Find root projects (no parent or parent not in hierarchy)
            const rootProjects = hierarchy.filter(project => {
                const parentId = project.parentId || project.parentid;
                return !parentId || !projectById.has(parentId);
            });
            
            // Sort roots by level, then by name
            rootProjects.sort((a, b) => {
                const levelDiff = (a.level || 0) - (b.level || 0);
                if (levelDiff !== 0) return levelDiff;
                return (a.primaryname || a.name || '').localeCompare(b.primaryname || b.name || '');
            });
            
            // Build tree-ordered rows
            const treeOrderedRows = [];
            rootProjects.forEach(root => {
                treeOrderedRows.push(...buildTreeOrder(root.id));
            });
            
            // Build hierarchy rows structure
            const hierarchyRows = {
                rows: treeOrderedRows,
                parentMap: childrenMap
            };
            
            // Create the hierarchy table HTML
            const tableHtml = `
                <div class="hierarchy-header">
                    <div class="hierarchy-title">${escapeHtml(vT('project.view.hierarchyTitle', 'PROJECT HIERARCHY'))}</div>
                    <div class="hierarchy-actions">
                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                    </div>
                </div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th class="ref-col">${escapeHtml(vT('project.view.colRef', 'Ref.'))}</th>
                                <th class="project-col">${escapeHtml(vT('project.view.colProject', 'Project'))}</th>
                                <th class="description-col">${escapeHtml(vT('project.view.colDescription', 'Description'))}</th>
                                <th class="rag-col">${escapeHtml(vT('project.view.colRag', 'RAG'))}</th>
                                <th class="lifecycle-col">${escapeHtml(vT('project.view.colLifecycle', 'Lifecycle'))}</th>
                                <th class="startdate-col">${escapeHtml(vT('project.view.colStartDate', 'Start Date'))}</th>
                                <th class="enddate-col">${escapeHtml(vT('project.view.colEndDate', 'End Date'))}</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderProjectTable(hierarchyRows, projectId)}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${escapeHtml(projectRecordCount(hierarchy.length))}
                </div>
            `;
            
            // Load other relationships
            const otherRelationshipsHtml = await loadProjectOtherRelationshipsView(projectId);
            
            container.innerHTML = tableHtml + otherRelationshipsHtml;
            
            // Initialize interactions (pass current project ID for auto-expansion)
            initProjectInteractions(container, hierarchyRows, projectId);
            
        } catch (error) {
            console.error('Failed to load project hierarchy:', error);
            container.innerHTML = '<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + escapeHtml(vT('project.view.failedToLoadHierarchy', 'Failed to load hierarchy data: {error}', { error: error.message || vT('common.unknownError', 'Unknown error') })) + '</div>';
        }
    }

    // Function to load project relationships data
    async function loadProjectRelationships(projectId) {
        const tbody = document.getElementById('projectRelationshipsTableBody');
        const footer = document.querySelector('#projectRelationshipsContent .hierarchy-footer');
        
        if (!tbody) return;
        
        try {
            const unknownLabel = vT('value.unknown', 'Unknown');
            const unnamedProjectLabel = vT('project.view.unnamedProject', 'Unnamed Project');

            // Show loading state
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + escapeHtml(vT('message.loading', 'Loading...')) + '</td></tr>';
            
            // Fetch relationships where this project is the SOURCE
            const relationships = await window.BUDG_API_SERVICE.getProjectRelationshipsBySourceId(projectId);
            
            if (!Array.isArray(relationships) || relationships.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + escapeHtml(vT('project.view.relationshipsNotFound', 'No relationships found')) + '</td></tr>';
                if (footer) footer.textContent = vT('project.view.zeroRecords', '0 records');
                return;
            }

            // Helper function to get project name with fallbacks
            async function getProjectName(rel) {
                // Try multiple field name variations (case-insensitive)
                let projectName = rel.targetProjectName || rel.targetprojectname || 
                                 rel.target_project_name || rel.name || rel.projectName;
                
                // If still missing and we have a targetProjectId, fetch it
                if ((!projectName || projectName === 'null' || projectName.trim() === '') && rel.targetProjectId) {
                    try {
                        const targetProject = await window.BUDG_API_SERVICE.getProjectById(rel.targetProjectId);
                        if (targetProject) {
                            projectName = targetProject.primaryname || targetProject.primaryName || 
                                        targetProject.name || targetProject.Name || 
                                        targetProject.description || unnamedProjectLabel;
                        }
                    } catch (e) {
                        console.warn('Failed to fetch project name for ID ' + rel.targetProjectId, e);
                    }
                }
                
                return projectName || unnamedProjectLabel;
            }

            // Process relationships and resolve project names
            const processedRelationships = await Promise.all(relationships.map(async (rel) => {
                const projectName = await getProjectName(rel);
                return { ...rel, resolvedProjectName: projectName };
            }));

            // Render relationships table
            tbody.innerHTML = processedRelationships.map(function(rel) {
                // Try multiple field name variations for relation type
                const relationTypeName = rel.relationTypeName || rel.relationtypename || 
                                       rel.relation_type_name || rel.relationType || unknownLabel;
                
                // Try multiple field name variations for project type
                const projectType = rel.targetProjectType || rel.targetprojecttype || 
                                  rel.target_project_type || rel.type || unknownLabel;
                
                return `
                    <tr>
                        <td>
                            <span class="view-badge">${escapeHtml(relationTypeName)}</span>
                        </td>
                        <td>
                            <div class="project-item">
                                <i class="fas fa-project-diagram project-icon"></i>
                                <span>${escapeHtml(rel.resolvedProjectName || unnamedProjectLabel)}</span>
                            </div>
                        </td>
                        <td>
                            <span class="view-badge">${escapeHtml(projectType)}</span>
                        </td>
                        <td>
                            <div class="action-buttons">
                                <button type="button" class="btn btn-sm btn-secondary" onclick="viewProject(${rel.targetProjectId || rel.targetprojectid})">
                                    <i class="fas fa-eye"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-danger" onclick="deleteProjectRelationship(${rel.id})">
                                    <i class="fas fa-trash"></i>
                                </button>
                            </div>
                        </td>
                    </tr>
                `;
            }).join('');

            if (footer) {
                footer.textContent = projectRecordCount(processedRelationships.length);
            }

        } catch (error) {
            console.error('Failed to load project relationships:', error);
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">' + escapeHtml(vT('project.view.failedToLoadRelationships', 'Failed to load relationships: {error}', { error: error.message || vT('common.unknownError', 'Unknown error') })) + '</td></tr>';
            if (footer) footer.textContent = vT('project.view.zeroRecords', '0 records');
        }
    }

    // Function to view project details
    function viewProject(projectId) {
        window.location.href = `/view/project/${projectId}`;
    }

    // Function to load project other relationships for view page
    async function loadProjectOtherRelationshipsView(projectId) {
        const unnamedPL = vT('project.view.unnamedProject', 'Unnamed Project');
        const unknownL = vT('value.unknown', 'Unknown');
        try {
            const relationships = await window.BUDG_API_SERVICE.getProjectRelationshipsBySourceId(projectId);
            
            if (!Array.isArray(relationships) || relationships.length === 0) {
                return `
                    <div class="other-relationships-panel" style="margin-top: 2rem;">
                        <div class="panel-header">
                            <div class="panel-title">${escapeHtml(vT('project.view.otherRelationships', 'OTHER RELATIONSHIPS'))}</div>
                            <div class="panel-actions">
                                <button type="button" class="action-icon" title="${escapeHtml(vT('common.settings', 'Settings'))}">
                                    <i class="fas fa-cog"></i>
                                </button>
                                <button type="button" class="action-icon" title="${escapeHtml(vT('project.view.moreOptions', 'More Options'))}">
                                    <i class="fas fa-chevron-down"></i>
                                </button>
                            </div>
                        </div>
                        <div class="panel-body">
                            <div class="relationships-header">
                                <div class="header-cell">${escapeHtml(vT('project.view.colRelationshipType', 'Relationship Type'))}</div>
                                <div class="header-cell">${escapeHtml(vT('project.view.colProject', 'Project'))}</div>
                                <div class="header-cell">${escapeHtml(vT('project.view.colRef', 'Ref.'))}</div>
                                <div class="header-cell">${escapeHtml(vT('label.description', 'Description'))}</div>
                            </div>
                            <div class="relationships-list">
                                <div style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">${escapeHtml(vT('project.view.relationshipsNotFound', 'No relationships found'))}</div>
                            </div>
                        </div>
                    </div>
                `;
            }
            
            // Process relationships and resolve project names (bidirectional support)
            const processedRelationships = await Promise.all(relationships.map(async (rel) => {
                // Use otherProjectId/otherProjectName if available (from bidirectional query), otherwise fall back to target
                const otherProjectId = rel.otherProjectId || rel.targetProjectId || rel.targetprojectid;
                let projectName = rel.otherProjectName || rel.targetProjectName || rel.targetprojectname || '';
                let projectRef = rel.otherProjectRef || rel.targetProjectRef || rel.targetprojectref || '';
                
                // If project name is missing, fetch it
                if ((!projectName || projectName === 'null' || projectName.trim() === '') && otherProjectId) {
                    try {
                        const targetProject = await window.BUDG_API_SERVICE.getProjectById(otherProjectId);
                        if (targetProject) {
                            projectName = targetProject.primaryname || targetProject.primaryName || targetProject.name || unnamedPL;
                            if (!projectRef) {
                                projectRef = targetProject.refnumber || targetProject.refNumber || targetProject.ref || '';
                            }
                        }
                    } catch (e) {
                        console.warn('Failed to fetch project for ID ' + otherProjectId, e);
                    }
                }
                
                // If ref is missing, fetch it
                if (!projectRef && otherProjectId) {
                    try {
                        const targetProject = await window.BUDG_API_SERVICE.getProjectById(otherProjectId);
                        if (targetProject) {
                            projectRef = targetProject.refnumber || targetProject.refNumber || targetProject.ref || '';
                        }
                    } catch (e) {
                        console.warn('Failed to fetch project ref for ID ' + otherProjectId, e);
                    }
                }
                
                return { 
                    ...rel, 
                    otherProjectId: otherProjectId,
                    resolvedProjectName: projectName || unnamedPL, 
                    resolvedProjectRef: projectRef 
                };
            }));
            
            const relationTypeName = (rel) => rel.relationTypeName || rel.relationtypename || rel.relation_type_name || unknownL;
            const description = (rel) => rel.description || '';
            
            const relationshipsHtml = processedRelationships.map(rel => {
                const otherProjectId = rel.otherProjectId || rel.targetProjectId || rel.targetprojectid;
                const otherProjectName = rel.otherProjectName || rel.resolvedProjectName || unnamedPL;
                const otherProjectRef = rel.otherProjectRef || rel.resolvedProjectRef || '';
                const projectLink = otherProjectId ? 
                    `<a href="/view/project/${otherProjectId}" class="entity-link" style="color: var(--secondary-color, #248567); text-decoration: none;" title="${escapeHtml(vT('project.view.viewProjectTitle', 'View {name}', { name: otherProjectName }))}">${escapeHtml(otherProjectName)}</a>` :
                    escapeHtml(otherProjectName);
                
                return `
                    <div class="relationship-item">
                        <div class="relationship-type">
                            <span class="view-badge">${escapeHtml(relationTypeName(rel))}</span>
                        </div>
                        <div class="relationship-project">
                            <div class="project-item">
                                <i class="fas fa-project-diagram project-icon"></i>
                                ${projectLink}
                            </div>
                        </div>
                        <div class="relationship-ref">
                            <span class="ref-display">${escapeHtml(otherProjectRef || '')}</span>
                        </div>
                        <div class="relationship-description">
                            <span>${escapeHtml(description(rel))}</span>
                        </div>
                    </div>
                `;
            }).join('');
            
            return `
                <div class="other-relationships-panel" style="margin-top: 2rem;">
                    <div class="panel-header">
                        <div class="panel-title">${escapeHtml(vT('project.view.otherRelationships', 'OTHER RELATIONSHIPS'))}</div>
                        <div class="panel-actions">
                            <button type="button" class="action-icon" title="${escapeHtml(vT('common.settings', 'Settings'))}">
                                <i class="fas fa-cog"></i>
                            </button>
                            <button type="button" class="action-icon" title="${escapeHtml(vT('project.view.moreOptions', 'More Options'))}">
                                <i class="fas fa-chevron-down"></i>
                            </button>
                        </div>
                    </div>
                    <div class="panel-body">
                        <div class="relationships-header">
                            <div class="header-cell">${escapeHtml(vT('project.view.colRelationshipType', 'Relationship Type'))}</div>
                            <div class="header-cell">${escapeHtml(vT('project.view.colProject', 'Project'))}</div>
                            <div class="header-cell">${escapeHtml(vT('project.view.colRef', 'Ref.'))}</div>
                            <div class="header-cell">${escapeHtml(vT('label.description', 'Description'))}</div>
                        </div>
                        <div class="relationships-list">
                            ${relationshipsHtml}
                        </div>
                    </div>
                </div>
            `;
        } catch (error) {
            console.error('Failed to load project other relationships:', error);
            return `
                <div class="other-relationships-panel" style="margin-top: 2rem;">
                    <div class="panel-header">
                        <div class="panel-title">${escapeHtml(vT('project.view.otherRelationships', 'OTHER RELATIONSHIPS'))}</div>
                    </div>
                    <div class="panel-body">
                        <div style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">${escapeHtml(vT('project.view.failedToLoadRelationships', 'Failed to load relationships: {error}', { error: error.message || vT('common.unknownError', 'Unknown error') }))}</div>
                    </div>
                </div>
            `;
        }
    }
    
    // Function to view project from relationship
    function viewProjectFromRelationship(projectId) {
        if (projectId) {
            window.location.href = `/view/project/project.html?id=${projectId}`;
        }
    }
    
    window.viewProjectFromRelationship = viewProjectFromRelationship;

    // Function to delete project relationship
    async function deleteProjectRelationship(relationshipId) {
        if (!confirm(vT('project.view.confirmDeleteRelationship', 'Are you sure you want to delete this relationship?'))) {
            return;
        }

        try {
            await window.BUDG_API_SERVICE.deleteProjectRelationship(relationshipId);
            
            // Reload relationships data
            const id = parseId();
            if (id) {
                loadProjectRelationships(id);
            }
            
            // Show success message
            alert(vT('project.view.relationshipDeleted', 'Relationship deleted successfully'));
        } catch (error) {
            console.error('Failed to delete project relationship:', error);
            alert(vT('project.view.failedToDeleteRelationship', 'Failed to delete relationship: {error}', { error: error.message || vT('common.unknownError', 'Unknown error') }));
        }
    }

    // Make functions globally available
    window.viewProject = viewProject;
    window.deleteProjectRelationship = deleteProjectRelationship;
})();

