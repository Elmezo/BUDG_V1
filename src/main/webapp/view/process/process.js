(function() {
    console.log('=== PROCESS VIEW JAVASCRIPT LOADING ===');
    console.log('Process view JavaScript loaded');
    console.log('Current timestamp:', new Date().toISOString());
    
    let _canEditObject = false; // Whether user can normally edit this object (without auto CR restriction)
    let _hasActiveAutoCR = false; // Whether an active auto CR is blocking edits
    
    // Test function that can be called manually from console
    window.testProcessLoad = function(id = 1) {
        console.log('=== MANUAL TEST PROCESS LOAD ===');
        console.log('Testing with ID:', id);
        load(id).catch(e => console.error('Manual test failed:', e));
    };
    
    function parseId() {
        console.log('=== PARSING ID FROM URL ===');
        console.log('Current URL:', window.location.href);
        console.log('Current pathname:', window.location.pathname);
        console.log('Current search:', window.location.search);
        
        // First try query parameters (e.g., ?id=1)
        const urlParams = new URLSearchParams(window.location.search);
        const queryId = urlParams.get('id');
        console.log('Query ID:', queryId);
        
        if (queryId) {
            const id = parseInt(queryId, 10);
            console.log('Parsed query ID:', id, 'Type:', typeof id);
            if (!Number.isNaN(id)) {
                console.log('Using query ID:', id);
                return id;
            }
        }
        
        // Fallback to path-based ID (e.g., /view/process/1)
        const parts = window.location.pathname.split('/').filter(Boolean);
        console.log('URL parts:', parts);
        
        const idx = parts.indexOf('process');
        console.log('Process index in parts:', idx);
        
        if (idx === -1 || parts.length < idx + 2) {
            console.log('Process not found in URL or no ID after process');
            return null;
        }
        
        const id = parseInt(parts[idx + 1], 10);
        console.log('Parsed path ID:', id, 'Type:', typeof id);
        console.log('Is NaN:', Number.isNaN(id));
        
        return Number.isNaN(id) ? null : id;
    }

    function vT(key, fallback, params) {
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
            'tabEditBtn'
        ];
        ids.forEach(function(id){
            const el = document.getElementById(id);
            if (el) el.style.display = 'none';
        });
    }

    async function hideEditsIfUnauthenticated() {
        try {
            const id = parseId();
            if (!id) {
                console.log('[Process] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Process', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Process] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Store canEditObject at module level for stakeholder tab redirect
                _canEditObject = canEditObject;
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Process] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Process] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Process', { method: 'GET', credentials: 'include' });
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
            '#deleteProcessBtn'
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

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
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

    function formatDateTime(dateString) {
        if (!dateString) return '-';
        try {
            const date = new Date(dateString);
            return date.toLocaleDateString('en-GB', {
                day: '2-digit',
                month: 'short',
                year: 'numeric'
            });
        } catch (e) {
            return dateString;
        }
    }

    function renderItem(label, valueHtml) {
        return `
            <div class="view-item">
                <div class="view-label">${escapeHtml(label)}</div>
                <div class="view-value">${valueHtml}</div>
            </div>
        `;
    }

    function renderStatusBadge(status, isActive = false) {
        if (!status) return '<span class="empty">-</span>';
        const badgeClass = isActive ? 'status-active' : 'status-inactive';
        return `<span class="status-badge ${badgeClass}">${escapeHtml(status)}</span>`;
    }

    function renderLifecycleBadge(lifecycle) {
        if (!lifecycle) return '<span class="empty">-</span>';
        return `<span class="lifecycle-badge">${escapeHtml(lifecycle)}</span>`;
    }

    function renderUserLink(name, id) {
        if (!name) return '<span class="empty">-</span>';
        if (id) {
            return `<a href="/view/people/${id}" class="user-link">${escapeHtml(name)}</a>`;
        }
        return escapeHtml(name);
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
    
    // Cache guest status per page-load
    let _cachedGuestStatus = null;
    async function isGuestVisitor() {
        if (_cachedGuestStatus !== null) {
            return _cachedGuestStatus;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _cachedGuestStatus = true;
                return _cachedGuestStatus;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _cachedGuestStatus = !isAuthenticated || isGuestRole;
            return _cachedGuestStatus;
        } catch (_) {
            _cachedGuestStatus = true;
            return _cachedGuestStatus;
        }
    }
    
    function renderGuestAccessDenied(container, processId, segmentName, segmentId) {
        if (!container) return;
        const title = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
        const message = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.message')) || 'This item is not available for guest users.';
        const code = 'ERR-SEGMENT-NOT-ENTERPRISE';
        const segmentLabel = segmentName || ((segmentId !== undefined && segmentId !== null) ? `ID ${segmentId}` : 'Unknown');
        
        container.innerHTML = `
            <div class="view-section" style="grid-column: 1/-1; text-align: center; padding: 3rem 1rem;">
                <div class="access-denied-title" style="font-size: 1.5rem; font-weight: 600; margin-bottom: 0.75rem;">${escapeHtml(title)}</div>
                <div class="access-denied-message" style="margin-bottom: 1.25rem; color: var(--text-muted, #4b5563);">
                    ${escapeHtml(message)}
                </div>
                <div class="access-denied-details" style="font-size: 0.95rem; color: var(--text-muted, #6b7280);">
                    <div style="margin-bottom: 0.5rem;">Segment: <strong>${escapeHtml(segmentLabel)}</strong></div>
                    <div>Error code: <code>${code}</code></div>
                </div>
            </div>
        `;
    }
    
    function renderDate(date) {
        if (!date) return '<span class="empty">-</span>';
        return formatDateTime(date);
    }

    function renderCollapsible(title, content, options = {}) {
        const { id = '', icon = 'fa-solid fa-file-lines', open = false } = options;
        const isOpen = open ? 'open' : '';
        return `
            <div class="collapsible-section ${isOpen}" id="${id}">
                <div class="collapsible-header">
                    <i class="${icon}" style="margin-right:.4rem;"></i>
                    <span>${title}</span>
                    <i class="fas fa-chevron-right caret"></i>
                </div>
                <div class="collapsible-body">
                    ${content}
                </div>
            </div>
        `;
    }

    function renderPermissions(permissions) {
        if (!permissions) {
            return `
                <div class="permissions-grid">
                    <label class="permission-item">
                        <input type="checkbox" disabled> Create
                    </label>
                    <label class="permission-item">
                        <input type="checkbox" disabled> Read
                    </label>
                    <label class="permission-item">
                        <input type="checkbox" disabled> Update
                    </label>
                    <label class="permission-item">
                        <input type="checkbox" disabled> Delete
                    </label>
                    <label class="permission-item">
                        <input type="checkbox" disabled> Archive
                    </label>
                </div>
            `;
        }
        
        return `
            <div class="permissions-grid">
                <label class="permission-item">
                    <input type="checkbox" ${permissions.cancreate ? 'checked' : ''} disabled> Create
                </label>
                <label class="permission-item">
                    <input type="checkbox" ${permissions.canread ? 'checked' : ''} disabled> Read
                </label>
                <label class="permission-item">
                    <input type="checkbox" ${permissions.canupdate ? 'checked' : ''} disabled> Update
                </label>
                <label class="permission-item">
                    <input type="checkbox" ${permissions.candelete ? 'checked' : ''} disabled> Delete
                </label>
                <label class="permission-item">
                    <input type="checkbox" ${permissions.canarchive ? 'checked' : ''} disabled> Archive
                </label>
            </div>
        `;
    }

    async function resolveProcessReferences(process) {
        console.log('=== RESOLVING PROCESS REFERENCES ===');
        console.log('Process data before resolution:', process);
        console.log('Created By ID fields:', {
            createdby_id: process.createdby_id,
            created_by_id: process.created_by_id,
            createdById: process.createdById,
            createdBy: process.createdBy,
            created_by: process.created_by,
            createdBy_ID: process.createdBy_ID
        });
        
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // User names are now provided directly by the backend API

        // BUDG Viewing from is_public field
        if (!process.viewingName && process.is_public) {
            const viewingId = process.is_public;
            console.log('Resolving BUDG Viewing ID:', viewingId);
            tasks.push((async () => {
                try {
                    let viewing = await api.getViewingById(viewingId);
                    console.log('Viewing API response:', viewing);
                    if (viewing && viewing.data) viewing = viewing.data;
                    process.viewingName = viewing?.primaryname || viewing?.Name || viewing?.name || String(viewingId);
                    console.log('Resolved BUDG Viewing name:', process.viewingName);
                } catch(e) {
                    console.error('Failed to resolve BUDG Viewing name:', e);
                    process.viewingName = String(viewingId);
                }
            })());
        }

        // Status name
        const statusId = process.status_id || process.Status_ID || process.status;
        if (!process.statusName && statusId) {
            console.log('Resolving status ID:', statusId);
            tasks.push((async () => {
                try {
                    let status = await api.getStatusById(statusId);
                    if (status && status.data) status = status.data;
                    process.statusName = status?.primaryname || status?.Name || status?.name || String(statusId);
                    console.log('Resolved status name:', process.statusName);
                } catch(e) { 
                    console.error('Failed to resolve status name:', e);
                    process.statusName = String(statusId);
                }
            })());
        }

        // Lifecycle status name
        const lifecycleId = process.lifecycle_status_id || process.Lifecycle_Status_ID || process.lifecycleStatus || process.lifecycle_status;
        if (!process.lifecycleName && lifecycleId) {
            console.log('Resolving lifecycle ID:', lifecycleId);
            tasks.push((async () => {
                try {
                    let lifecycle = await api.getProcessLifecycleById(lifecycleId);
                    console.log('Process Lifecycle API response:', lifecycle);
                    if (lifecycle && lifecycle.data) lifecycle = lifecycle.data;
                    process.lifecycleName = lifecycle?.primaryname || lifecycle?.Name || lifecycle?.name || String(lifecycleId);
                    console.log('Resolved lifecycle name:', process.lifecycleName);
                } catch(e) { 
                    console.error('Failed to resolve lifecycle name:', e);
                    process.lifecycleName = String(lifecycleId);
                }
            })());
        }

        // Process type name
        const typeId = process.type_id || process.Type_ID || process.type || process.processType;
        if (!process.typeName && typeId) {
            console.log('Resolving process type ID:', typeId);
            tasks.push((async () => {
                try {
                    let type = await api.getProcessTypeById(typeId);
                    console.log('Process Type API response:', type);
                    if (type && type.data) type = type.data;
                    process.typeName = type?.primaryname || type?.Name || type?.name || String(typeId);
                    console.log('Resolved process type name:', process.typeName);
                } catch(e) { 
                    console.error('Failed to resolve process type name:', e);
                    process.typeName = String(typeId);
                }
            })());
        }

        // Classification name
        const classificationId = process.processclass_id || process.processClass_id || process.ProcessClass_ID || process.classification_id || process.Classification_ID || process.classification || process.classificationName;
        if (!process.classificationName && classificationId) {
            console.log('Resolving classification ID:', classificationId);
            tasks.push((async () => {
                try {
                    let classificationList = await api.getProcessClassList();
                    console.log('Process classification list:', classificationList);
                    
                    let classificationData = null;
                    if (classificationList && Array.isArray(classificationList)) {
                        classificationData = classificationList.find(c => c.id === classificationId || c.ID === classificationId);
                    } else if (classificationList && classificationList.data && Array.isArray(classificationList.data)) {
                        classificationData = classificationList.data.find(c => c.id === classificationId || c.ID === classificationId);
                    }
                    
                    if (classificationData) {
                        process.classificationName = classificationData.primaryname || classificationData.Name || classificationData.name || String(classificationId);
                        console.log('Resolved classification name:', process.classificationName);
                    } else {
                        console.log('Classification not found in list, using ID as name');
                        process.classificationName = String(classificationId);
                    }
                } catch(e) { 
                    console.error('Failed to resolve classification name:', e);
                    process.classificationName = String(classificationId);
                }
            })());
        }

        // Automation name
        const automationId = process.processautomation_id || process.processAutomation_id || process.ProcessAutomation_ID || process.automation_id || process.Automation_ID || process.automation || process.automationName;
        if (!process.automationName && automationId) {
            console.log('Resolving automation ID:', automationId);
            tasks.push((async () => {
                try {
                    let automationList = await api.getProcessAutomationList();
                    console.log('Process automation list:', automationList);
                    
                    let automationData = null;
                    if (automationList && Array.isArray(automationList)) {
                        automationData = automationList.find(a => a.id === automationId || a.ID === automationId);
                    } else if (automationList && automationList.data && Array.isArray(automationList.data)) {
                        automationData = automationList.data.find(a => a.id === automationId || a.ID === automationId);
                    }
                    
                    if (automationData) {
                        process.automationName = automationData.primaryname || automationData.Name || automationData.name || String(automationId);
                        console.log('Resolved automation name:', process.automationName);
                    } else {
                        console.log('Automation not found in list, using ID as name');
                        process.automationName = String(automationId);
                    }
                } catch(e) { 
                    console.error('Failed to resolve automation name:', e);
                    process.automationName = String(automationId);
                }
            })());
        }

        // Step type name
        const stepTypeId = process.stepType_id || process.StepType_ID || process.stepType || process.step_type || process.stepTypeName;
        const stepTypeIdInt = parseInt(stepTypeId);
        console.log('Step type ID extraction debug:', {
            stepType_id: process.stepType_id,
            StepType_ID: process.StepType_ID,
            stepType: process.stepType,
            step_type: process.step_type,
            stepTypeName: process.stepTypeName,
            finalStepTypeId: stepTypeId,
            stepTypeIdInt: stepTypeIdInt
        });
        if (!process.stepTypeName && stepTypeId) {
            console.log('Resolving step type ID:', stepTypeId);
            tasks.push((async () => {
                try {
                    let stepTypeList = await api.getProcessStepTypeList();
                    console.log('Process step type list:', stepTypeList);
                    
                    let stepTypeData = null;
                    console.log('Step Type ID to search:', stepTypeId, 'Type:', typeof stepTypeId);
                    console.log('Step Type List structure:', stepTypeList);
                    
                    if (stepTypeList && Array.isArray(stepTypeList)) {
                        console.log('Searching in direct array, length:', stepTypeList.length);
                        stepTypeList.forEach((st, index) => {
                            console.log(`Item ${index}:`, {
                                id: st.id, 
                                ID: st.ID, 
                                primaryName: st.primaryName,
                                primaryname: st.primaryname, 
                                PrimaryName: st.PrimaryName,
                                Name: st.Name,
                                name: st.name,
                                idType: typeof st.id,
                                IDType: typeof st.ID,
                                matchesId: st.id === stepTypeIdInt,
                                matchesID: st.ID === stepTypeIdInt,
                                matchesIdString: st.id === stepTypeId,
                                matchesIDString: st.ID === stepTypeId
                            });
                        });
                        stepTypeData = stepTypeList.find(st => 
                            st.id === stepTypeIdInt || st.ID === stepTypeIdInt ||
                            st.id === stepTypeId || st.ID === stepTypeId
                        );
                        console.log('Found in array:', stepTypeData);
                    } else if (stepTypeList && stepTypeList.data && Array.isArray(stepTypeList.data)) {
                        console.log('Searching in data array, length:', stepTypeList.data.length);
                        stepTypeList.data.forEach((st, index) => {
                            console.log(`Item ${index}:`, {
                                id: st.id, 
                                ID: st.ID, 
                                primaryName: st.primaryName,
                                primaryname: st.primaryname, 
                                PrimaryName: st.PrimaryName,
                                Name: st.Name,
                                name: st.name,
                                idType: typeof st.id,
                                IDType: typeof st.ID,
                                matchesId: st.id === stepTypeIdInt,
                                matchesID: st.ID === stepTypeIdInt,
                                matchesIdString: st.id === stepTypeId,
                                matchesIDString: st.ID === stepTypeId
                            });
                        });
                        stepTypeData = stepTypeList.data.find(st => 
                            st.id === stepTypeIdInt || st.ID === stepTypeIdInt ||
                            st.id === stepTypeId || st.ID === stepTypeId
                        );
                        console.log('Found in data array:', stepTypeData);
                    }
                    
                    if (stepTypeData) {
                        process.stepTypeName = stepTypeData.primaryName || stepTypeData.primaryname || stepTypeData.PrimaryName || stepTypeData.Name || stepTypeData.name || String(stepTypeId);
                        console.log('Resolved step type name:', process.stepTypeName);
                        console.log('Step type data fields:', {
                            primaryName: stepTypeData.primaryName,
                            primaryname: stepTypeData.primaryname,
                            PrimaryName: stepTypeData.PrimaryName,
                            Name: stepTypeData.Name,
                            name: stepTypeData.name
                        });
                    } else {
                        console.log('Step type not found in list, using ID as name');
                        process.stepTypeName = String(stepTypeId);
                    }
                } catch(e) { 
                    console.error('Failed to resolve step type name:', e);
                    process.stepTypeName = String(stepTypeId);
                }
            })());
        }

        // Viewing name
        const viewingId = process.ispublic || process.is_public || process.viewing_id || process.Viewing_ID || process.viewing || process.viewingName;
        if (!process.viewingName && viewingId) {
            console.log('Resolving viewing ID:', viewingId);
            tasks.push((async () => {
                try {
                    let viewingData = await api.getViewingById(viewingId);
                    console.log('Viewing API response:', viewingData);
                    
                    if (viewingData) {
                        process.viewingName = viewingData.primaryname || viewingData.Name || viewingData.name || String(viewingId);
                        console.log('Resolved viewing name:', process.viewingName);
                    } else {
                        console.log('Viewing not found, using ID as name');
                        process.viewingName = String(viewingId);
                    }
                } catch(e) { 
                    console.error('Failed to resolve viewing name:', e);
                    process.viewingName = String(viewingId);
                }
            })());
        }

        // Duration Type name
        const durationTypeId = process.duration_type || process.durationType || process.durationType_id || process.DurationType_ID;
        if (!process.durationTypeName && durationTypeId) {
            console.log('Resolving duration type ID:', durationTypeId);
            tasks.push((async () => {
                try {
                    let durationTypeList = await api.getDurationTypeList();
                    console.log('Duration Type list:', durationTypeList);
                    
                    let durationTypeData = null;
                    if (durationTypeList && Array.isArray(durationTypeList)) {
                        durationTypeData = durationTypeList.find(dt => dt.id === durationTypeId || dt.ID === durationTypeId);
                    } else if (durationTypeList && durationTypeList.data && Array.isArray(durationTypeList.data)) {
                        durationTypeData = durationTypeList.data.find(dt => dt.id === durationTypeId || dt.ID === durationTypeId);
                    }
                    
                    if (durationTypeData) {
                        process.durationTypeName = durationTypeData.primaryname || durationTypeData.PrimaryName || durationTypeData.name || durationTypeData.Name || String(durationTypeId);
                        console.log('Resolved duration type name:', process.durationTypeName);
                    } else {
                        console.log('Duration type not found in list, using ID as name');
                        process.durationTypeName = String(durationTypeId);
                    }
                } catch(e) { 
                    console.error('Failed to resolve duration type name:', e);
                    process.durationTypeName = String(durationTypeId);
                }
            })());
        }

        console.log('Starting resolution tasks:', tasks.length);
        await Promise.all(tasks);
        console.log('All resolution tasks completed');
        console.log('Process data after resolution:', process);
        return process;
    }

    // Resolve status ID to name
    async function resolveStatusName(statusId) {
        if (!statusId) return 'Unknown';
        
        try {
            const api = window.BUDG_API_SERVICE;
            if (!api || !api.getStatuses) {
                console.warn('API service or getStatuses method not available');
                return `Status ID: ${statusId}`;
            }
            
            const response = await api.getStatuses();
            let statuses = response;
            if (response && response.data) {
                statuses = response.data;
            }
            
            if (Array.isArray(statuses)) {
                const status = statuses.find(s => s.id === statusId || s.ID === statusId);
                return status ? (status.primaryname || status.PrimaryName || status.name || status.Name || `Status ID: ${statusId}`) : `Status ID: ${statusId}`;
            }
            
            return `Status ID: ${statusId}`;
        } catch (error) {
            console.error('Error resolving status name:', error);
            return `Status ID: ${statusId}`;
        }
    }

    // Load process components (hierarchy) + Process Map for parents with children
    async function loadProcessComponents(processId, container) {
        console.log('=== LOADING PROCESS COMPONENTS ===');
        console.log('Process ID:', processId);
        console.log('Container:', container);
        
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">COMPONENTS</div>
                <div id="processComponentsHierarchyContent">
                    <div class="loading">Loading hierarchy...</div>
                </div>
            </div>
        `;
        
        try {
            const api = window.BUDG_API_SERVICE;
            if (!api) {
                throw new Error('BUDG_API_SERVICE not available');
            }
            
            const response = await api.getProcessHierarchy(processId);
            let hierarchy = response;
            if (response && response.data) {
                hierarchy = response.data;
            }
            
            if (!hierarchy || hierarchy.length === 0) {
                const hierarchyContent = document.getElementById('processComponentsHierarchyContent');
                if (hierarchyContent) {
                    hierarchyContent.innerHTML = '<div class="empty">No hierarchy available for this process</div>';
                }
                window.processTabData.components = container.innerHTML;
                return;
            }
            
            // Build children-by-parent for Process Map (only show map when current process has immediate children)
            const childrenByParent = new Map();
            hierarchy.forEach(process => {
                const pid = process.id || process.ID;
                const parentId = process.parentid !== undefined && process.parentid !== null
                    ? process.parentid
                    : (process.parentId !== undefined && process.parentId !== null
                        ? process.parentId
                        : (process.Parent_ID !== undefined && process.Parent_ID !== null
                            ? process.Parent_ID
                            : null));
                if (parentId != null) {
                    if (!childrenByParent.has(parentId)) {
                        childrenByParent.set(parentId, []);
                    }
                    childrenByParent.get(parentId).push(process);
                }
            });
            
            const currentProcess = hierarchy.find(p => (p.id || p.ID) === processId);
            const immediateChildren = (currentProcess && childrenByParent.get(processId)) || [];
            const tableHtml = await renderProcessHierarchyTable(hierarchy, processId);
            
            if (immediateChildren.length > 0 && window.ProcessComponentsMap && typeof window.ProcessComponentsMap.getComponentsMapHtml === 'function') {
                // Parent process with children: show Process Map above Components table
                const mapSectionTitle = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">PROCESS MAP</div></div>';
                container.innerHTML = mapSectionTitle + window.ProcessComponentsMap.getComponentsMapHtml() +
                    '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">COMPONENTS</div><div id="processComponentsHierarchyContent">' + tableHtml + '</div></div>';
                window.ProcessComponentsMap.init(processId, currentProcess, immediateChildren);
            } else {
                container.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">COMPONENTS</div><div id="processComponentsHierarchyContent">' + tableHtml + '</div></div>';
            }
            
            initHierarchyInteractions(processId);
            window.processTabData.components = container.innerHTML;
            
        } catch (error) {
            console.error('Error loading process components:', error);
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">COMPONENTS</div>
                    <div class="error">Failed to load components: ${error.message}</div>
                </div>
            `;
        }
    }
    

    // Render process hierarchy table
    async function renderProcessHierarchyTable(hierarchy, currentProcessId) {
        console.log('Rendering hierarchy table with', hierarchy.length, 'items');
        console.log('Current process ID:', currentProcessId);
        
        if (!hierarchy || hierarchy.length === 0) {
            return '<div class="empty">No hierarchy data available</div>';
        }
        
        // Build maps for efficient lookup first
        const processById = new Map();
        const childrenByParent = new Map();
        const siblingsByParent = new Map();
        
        hierarchy.forEach(process => {
            // Use lowercase 'id' and 'parentid' as per schema.sql
            const processId = process.id || process.ID;
            processById.set(processId, process);
            // Schema uses lowercase 'parentid' - check all possible field names
            const parentId = process.parentid !== undefined && process.parentid !== null 
                ? process.parentid 
                : (process.parentId !== undefined && process.parentId !== null 
                    ? process.parentId 
                    : (process.Parent_ID !== undefined && process.Parent_ID !== null 
                        ? process.Parent_ID 
                        : null));
            
            // Build children map - group processes by their parentid
            if (parentId !== null && parentId !== undefined) {
                if (!childrenByParent.has(parentId)) {
                    childrenByParent.set(parentId, []);
                }
                childrenByParent.get(parentId).push(process);
            }
            
            // Build siblings map - group processes by their parentid
            if (parentId !== null && parentId !== undefined) {
                if (!siblingsByParent.has(parentId)) {
                    siblingsByParent.set(parentId, []);
                }
                siblingsByParent.get(parentId).push(process);
            }
        });
        
        // Find current process
        const currentProcess = hierarchy.find(p => {
            const pid = p.id || p.ID;
            return pid === currentProcessId;
        });
        if (!currentProcess) {
            console.warn('Current process not found in hierarchy, using first item');
            // If current process not found, use first item as current
            if (hierarchy.length > 0) {
                const firstProcess = hierarchy[0];
                const firstProcessId = firstProcess.id || firstProcess.ID;
                console.log('Using first process as current:', firstProcessId);
                // Recursively call with first process as current
                return await renderProcessHierarchyTable(hierarchy, firstProcessId);
            } else {
                return '<div class="empty">No hierarchy data available</div>';
            }
        }
        
        const currentProcessIdValue = currentProcess.id || currentProcess.ID;
        // Get parentid - schema uses lowercase 'parentid'
        const currentParentId = currentProcess.parentid !== undefined && currentProcess.parentid !== null
            ? currentProcess.parentid
            : (currentProcess.parentId !== undefined && currentProcess.parentId !== null
                ? currentProcess.parentId
                : (currentProcess.Parent_ID !== undefined && currentProcess.Parent_ID !== null
                    ? currentProcess.Parent_ID
                    : null));
        
        console.log('Current process ID:', currentProcessIdValue);
        console.log('Current parent ID:', currentParentId);
        console.log('Processes in hierarchy:', hierarchy.length);
        console.log('Children by parent map:', Array.from(childrenByParent.entries()));
        
        // Build hierarchy structure: ancestors → siblings → current → children
        const hierarchyRows = [];
        
        // 1. Get all ancestors (parents up to root)
        const ancestors = [];
        let parentIdToCheck = currentParentId;
        while (parentIdToCheck !== null && parentIdToCheck !== undefined) {
            const parent = processById.get(parentIdToCheck);
            if (parent) {
                ancestors.unshift(parent); // Add to beginning to maintain order (root first)
                // Get next parent's parentid
                parentIdToCheck = parent.parentid !== undefined && parent.parentid !== null
                    ? parent.parentid
                    : (parent.parentId !== undefined && parent.parentId !== null
                        ? parent.parentId
                        : (parent.Parent_ID !== undefined && parent.Parent_ID !== null
                            ? parent.Parent_ID
                            : null));
            } else {
                break;
            }
        }
        
        console.log('Ancestors found:', ancestors.length);
        
        // 2. Get siblings (same parent, excluding current)
        const siblings = (currentParentId !== null && siblingsByParent.has(currentParentId))
            ? siblingsByParent.get(currentParentId)
                .filter(p => {
                    const pid = p.id || p.ID;
                    return pid !== currentProcessIdValue;
                })
                .sort((a, b) => (a.primaryname || a.primaryName || '').localeCompare(b.primaryname || b.primaryName || ''))
            : [];
        
        console.log('Siblings found:', siblings.length);
        
        // 3. Get children (direct children only) - use currentProcessIdValue to find children
        const children = (childrenByParent.has(currentProcessIdValue))
            ? childrenByParent.get(currentProcessIdValue)
                .sort((a, b) => (a.primaryname || a.primaryName || '').localeCompare(b.primaryname || b.primaryName || ''))
            : [];
        
        console.log('Children found:', children.length);
        
        // Build hierarchy rows in order: ancestors → siblings → current → children
        ancestors.forEach((ancestor, index) => {
            const ancestorId = ancestor.id || ancestor.ID;
            const ancestorChildren = childrenByParent.get(ancestorId) || [];
            hierarchyRows.push({
                process: ancestor,
                depth: index,
                hasChildren: ancestorChildren.length > 0,
                isAncestor: true,
                isCurrent: false,
                isSibling: false
            });
        });
        
        siblings.forEach(sibling => {
            const siblingId = sibling.id || sibling.ID;
            const siblingChildren = childrenByParent.get(siblingId) || [];
            hierarchyRows.push({
                process: sibling,
                depth: ancestors.length,
                hasChildren: siblingChildren.length > 0,
                isAncestor: false,
                isCurrent: false,
                isSibling: true
            });
        });
        
        // Add current process
        hierarchyRows.push({
            process: currentProcess,
            depth: ancestors.length,
            hasChildren: children.length > 0,
            isAncestor: false,
            isCurrent: true,
            isSibling: false
        });
        
        // Add children (direct children only)
        children.forEach(child => {
            const childId = child.id || child.ID;
            const childChildren = childrenByParent.get(childId) || [];
            hierarchyRows.push({
                process: child,
                depth: ancestors.length + 1,
                hasChildren: childChildren.length > 0,
                isAncestor: false,
                isCurrent: false,
                isSibling: false
            });
        });
        
        // Render table
        let tableHtml = `
            <div class="hierarchy-table-container">
                <div class="hierarchy-table-wrapper">
                <table class="hierarchy-table">
                    <thead>
                        <tr>
                            <th class="ref-col">Ref.</th>
                            <th class="process-col">Process</th>
                            <th class="description-col">Description</th>
                            <th class="parent-col">Parent</th>
                            <th class="status-col">BUDG Status</th>
                            <th class="predecessors-col">Predecessors</th>
                            <th class="type-col">Type</th>
                            <th class="class-col">Class</th>
                            <th class="input-col">Input / Trigger</th>
                            <th class="output-col">Output / Result</th>
                        </tr>
                    </thead>
                    <tbody>
        `;
        
        for (const row of hierarchyRows) {
            const process = row.process;
            const processId = process.id || process.ID;
            // Get parentid - schema uses lowercase 'parentid'
            const parentId = process.parentid !== undefined && process.parentid !== null
                ? process.parentid
                : (process.parentId !== undefined && process.parentId !== null
                    ? process.parentId
                    : (process.Parent_ID !== undefined && process.Parent_ID !== null
                        ? process.Parent_ID
                        : null));
            const depth = row.depth;
            const hasChildren = row.hasChildren;
            const isCurrent = row.isCurrent;
            const isAncestor = row.isAncestor;
            const isSibling = row.isSibling;
            
            // Get children count
            const childCount = (childrenByParent.get(processId) || []).length;
            
            // Resolve status name
            const resolvedStatusName = await resolveStatusName(process.status || process.Status);
            
            // Calculate visual indentation: 
            // - Ancestors: left (depth decreases)
            // - Siblings: same level as current
            // - Current: same level
            // - Children: right (depth increases)
            let visualDepth = depth;
            // Visual depth is already correct based on actual hierarchy depth
            
            // Build tree structure
            const indent = Array(visualDepth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle" data-id="${processId}"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const branchLine = visualDepth > 0 ? '<span class="tree-branch"></span>' : '';
            
            const Mask = window.HierarchyMask;
            const isMaskedNode = Mask ? Mask.isMasked(process) : false;
            const ph = isMaskedNode ? Mask.PLACEHOLDER : null;

            let rowClass = 'hierarchy-row';
            if (isCurrent) rowClass += ' current-row';
            if (isAncestor) rowClass += ' ancestor-row';
            if (isSibling) rowClass += ' sibling-row';
            if (isMaskedNode) rowClass += ' masked-row';

            const refText = isMaskedNode ? ph : (process.refnumber || process.refNumber || 'N/A');
            const nameText = isMaskedNode ? ph : (process.primaryname || process.primaryName || 'Unnamed Process');
            const nameCell = isMaskedNode
                ? `<span class="process-link masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(nameText)}</span>`
                : `<a class="process-link ${isCurrent ? 'current-process-link' : ''}" href="/view/process/${processId}">${escapeHtml(nameText)}</a>`;
            const descText = isMaskedNode ? ph : (process.description || 'No description');
            const parentCell = isMaskedNode
                ? `<span class="parent-name">${escapeHtml(ph)}</span>`
                : (parentId
                    ? `<span class="parent-name">${escapeHtml(process.parentName || `Process ID: ${parentId}`)}</span>`
                    : '<span class="empty">-</span>');
            const statusText = isMaskedNode ? ph : resolvedStatusName;
            const predecessorsCell = isMaskedNode
                ? `<span class="predecessors-list">${escapeHtml(ph)}</span>`
                : (process.predecessors
                    ? `<span class="predecessors-list">${escapeHtml(process.predecessors)}</span>`
                    : '<span class="empty">-</span>');
            const typeText = isMaskedNode ? ph : (process.typeName || process.type || 'Process');
            const classCell = isMaskedNode
                ? `<span class="classification-name">${escapeHtml(ph)}</span>`
                : (process.classificationName
                    ? `<span class="classification-name">${escapeHtml(process.classificationName)}</span>`
                    : '<span class="empty">-</span>');
            const inputCell = isMaskedNode
                ? `<span class="input-description">${escapeHtml(ph)}</span>`
                : (process.input_description
                    ? `<span class="input-description">${escapeHtml(process.input_description)}</span>`
                    : '<span class="empty">-</span>');
            const outputCell = isMaskedNode
                ? `<span class="output-description">${escapeHtml(ph)}</span>`
                : (process.output_description
                    ? `<span class="output-description">${escapeHtml(process.output_description)}</span>`
                    : '<span class="empty">-</span>');

            tableHtml += `
                <tr class="${rowClass}" data-level="${visualDepth}" data-id="${processId}" data-parent-id="${parentId || ''}" data-has-children="${hasChildren}"${isMaskedNode ? ' data-masked="true"' : ''}>
                    <td class="ref-cell">
                        <div class="tree-cell">
                            ${indent}${expander}${branchLine}
                            <i class="fas fa-cogs item-icon"></i>
                            <span class="ref-number">${escapeHtml(refText)}</span>
                            ${countBadge}
                        </div>
                    </td>
                    <td class="process-cell">
                        <div class="process-name">
                            ${nameCell}
                        </div>
                    </td>
                    <td class="description-cell">
                        ${escapeHtml(descText)}
                    </td>
                    <td class="parent-cell">
                        ${parentCell}
                    </td>
                    <td class="status-cell">
                        <span class="status-badge ${statusText === 'Active' ? 'status-active' : 'status-inactive'}">
                            ${escapeHtml(statusText)}
                        </span>
                    </td>
                    <td class="predecessors-cell">
                        ${predecessorsCell}
                    </td>
                    <td class="type-cell">
                        <span class="type-badge">
                            ${escapeHtml(typeText)}
                        </span>
                    </td>
                    <td class="class-cell">
                        ${classCell}
                    </td>
                    <td class="input-cell">
                        ${inputCell}
                    </td>
                    <td class="output-cell">
                        ${outputCell}
                    </td>
                </tr>
            `;
        }
        
        tableHtml += `
                    </tbody>
                </table>
                </div>
                <div class="hierarchy-footer">${hierarchyRows.length} record${hierarchyRows.length !== 1 ? 's' : ''}</div>
            </div>
        `;
        
        return tableHtml;
    }

    // Initialize hierarchy interactions (expand/collapse)
    function initHierarchyInteractions(currentProcessId = null) {
        console.log('Initializing hierarchy interactions');
        
        const tbody = document.querySelector('.hierarchy-table tbody');
        if (!tbody) return;
        
        // Get current process ID from parameter or URL
        if (!currentProcessId) {
            const urlParams = new URLSearchParams(window.location.search);
            currentProcessId = urlParams.get('id') ? parseInt(urlParams.get('id')) : null;
        }
        console.log('Current process ID:', currentProcessId);
        
        // Build children map - group by parent ID
        const childrenByParent = new Map();
        const allRows = Array.from(tbody.querySelectorAll('tr.hierarchy-row'));
        const rowById = new Map();
        
        allRows.forEach(tr => {
            const parentId = tr.getAttribute('data-parent-id');
            const rowId = tr.getAttribute('data-id');
            const rowLevel = parseInt(tr.getAttribute('data-level') || '0');
            const hasChildren = tr.getAttribute('data-has-children') === 'true';
            
            rowById.set(rowId, { tr, id: rowId, level: rowLevel, parentId, hasChildren });
            
            if (parentId) {
                if (!childrenByParent.has(parentId)) {
                    childrenByParent.set(parentId, []);
                }
                childrenByParent.get(parentId).push({ tr, id: rowId, level: rowLevel });
            }
        });
        
        const collapsed = new Set();
        
        // Initially, all rows are visible (ancestors, siblings, current, direct children)
        // But we can collapse children of ancestors/siblings/current if they have children
        // For now, show everything initially
        
        // Highlight current process row
        if (currentProcessId) {
            const currentRow = rowById.get(String(currentProcessId));
            if (currentRow) {
                currentRow.tr.classList.add('current-row');
                // Scroll to current row after a short delay
                setTimeout(() => {
                    currentRow.tr.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }, 100);
            }
        }
        
        function toggleChildren(parentId, isCollapsed) {
            const directChildren = childrenByParent.get(String(parentId)) || [];
            
            directChildren.forEach(({ tr, id, level }) => {
                if (isCollapsed) {
                    // Hide this child and all its descendants
                    tr.style.display = 'none';
                    tr.classList.add('collapsed');
                    // Recursively hide all descendants
                    if (childrenByParent.has(String(id))) {
                        toggleChildren(id, true);
                    }
                } else {
                    // Show direct children only
                    tr.style.display = '';
                    tr.classList.remove('collapsed');
                    // Recursively show descendants only if they are also expanded
                    if (childrenByParent.has(String(id)) && !collapsed.has(String(id))) {
                        toggleChildren(id, false);
                    }
                }
            });
        }
        
        // Add click handlers to expanders
        tbody.querySelectorAll('.tree-expander').forEach(btn => {
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                
                const processId = this.getAttribute('data-id');
                const icon = this.querySelector('i');
                
                if (collapsed.has(processId)) {
                    // Expand
                    collapsed.delete(processId);
                    icon.style.transform = 'rotate(0deg)';
                    toggleChildren(processId, false);
                } else {
                    // Collapse
                    collapsed.add(processId);
                    icon.style.transform = 'rotate(-90deg)';
                    toggleChildren(processId, true);
                }
            });
        });
    }



    let currentView = 'original'; // Track current view mode for pending changes
    
    /**
     * Determine initial view mode for the view page
     * Always defaults to 'original' - user can switch to 'changes' via toggle
     */
    async function determineInitialViewMode(processId) {
        // MANDATORY: Always default to 'original' view on page load/refresh
        // User can manually switch to 'changes' via toggle, but default is always 'original'
        try {
            // Clear any saved 'changes' view from sessionStorage to ensure default is always 'original'
            const key = `pendingChanges:view:Process#${processId}`;
            if (typeof sessionStorage !== 'undefined') {
                const saved = sessionStorage.getItem(key);
                if (saved === 'changes') {
                    sessionStorage.removeItem(key);
                }
            }
        } catch (e) {
            // ignore storage errors
        }
        
        // Always default to 'original' - mandatory requirement
        return 'original';
    }
    
    async function load(id, view = null) {
        console.log('=== LOADING PROCESS ===');
        console.log('Loading process with ID:', id);
        console.log('View mode:', view);
        console.log('Current URL:', window.location.href);
        console.log('Document ready state:', document.readyState);
        
        const container = document.getElementById('processViewContainer');
        const titleElement = document.getElementById('processTitle');
        const breadcrumbElement = document.getElementById('processBreadcrumb');
        
        console.log('Container found:', container);
        console.log('Title element found:', titleElement);
        console.log('Breadcrumb element found:', breadcrumbElement);
        
        if (!container) {
            console.error('[Process] Container not found: processViewContainer');
            console.error('[Process] Available elements with IDs:', Array.from(document.querySelectorAll('[id]')).map(el => el.id));
            return;
        }
        
        // Ensure container is visible before loading
        container.style.display = '';
        container.style.visibility = 'visible';
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading...</div>';
        
        try {
            if (view !== null) currentView = view;
            console.log('Calling API: getProcessById(' + id + ', view=' + (view === 'changes' ? 'changes' : 'null') + ')');
            
            // Check if API service is available
            console.log('=== API SERVICE CHECK ===');
            console.log('BUDG_API_SERVICE available:', !!window.BUDG_API_SERVICE);
            console.log('BUDG_API_SERVICE type:', typeof window.BUDG_API_SERVICE);
            
            if (!window.BUDG_API_SERVICE) {
                throw new Error('BUDG_API_SERVICE not available');
            }
            
            console.log('getProcessById method available:', typeof window.BUDG_API_SERVICE.getProcessById);
            console.log('getProcessById method type:', typeof window.BUDG_API_SERVICE.getProcessById);
            
            if (typeof window.BUDG_API_SERVICE.getProcessById !== 'function') {
                throw new Error('getProcessById method not available');
            }
            
            const data = await window.BUDG_API_SERVICE.getProcessById(id, view === 'changes' ? 'changes' : null);
            console.log('=== FULL API RESPONSE ===');
            console.log('API response:', data);
            console.log('Response type:', typeof data);
            console.log('Is array:', Array.isArray(data));
            console.log('API Response keys:', Object.keys(data));
            console.log('API Response values:', Object.values(data));
            
            // Store original data for comparison when switching views
            if (view === 'original') {
                window.__processOriginalData = null; // Clear when viewing original
            } else if (view === 'changes') {
                // We'll compare after loading
            }
            
            // Extract actual data - handle different response formats
            let actualData = data;
            if (data && data.data) {
                actualData = data.data;
                console.log('Using data.data as actual data');
            } else if (data && data.result) {
                actualData = data.result;
                console.log('Using data.result as actual data');
            } else if (data && data.process) {
                actualData = data.process;
                console.log('Using data.process as actual data');
            }
            
            console.log('=== EXTRACTED ACTUAL DATA ===');
            console.log('Actual data:', actualData);
            console.log('Actual data keys:', Object.keys(actualData || {}));
            console.log('Primary name fields:', {
                primaryName: actualData?.primaryName,
                primaryname: actualData?.primaryname,
                name: actualData?.name,
                Name: actualData?.Name
            });
            
            // If actualData is still an array, take the first element
            if (Array.isArray(actualData) && actualData.length > 0) {
                actualData = actualData[0];
                console.log('Using first array element as actual data');
            }
            
            console.log('Final data to use:', actualData);
            
            // Resolve foreign key references
            console.log('=== RESOLVING REFERENCES ===');
            actualData = await resolveProcessReferences(actualData);
            console.log('References resolved, final data:', actualData);
            
            // Guest / segment gating: unauthenticated users may only view Enterprise segment
            try {
                const isGuest = await isGuestVisitor();
                const segmentName = actualData?.segmentName || actualData?.segment_name || actualData?.segment;
                const segmentId = actualData?.segmentId ?? actualData?.segment_id ?? actualData?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Process] Guest visitor blocked from non-Enterprise segment view', {
                        processId: id,
                        segmentName: segmentName,
                        segmentId: segmentId
                    });
                    renderGuestAccessDenied(container, id, segmentName, segmentId);
                    return null;
                }
            } catch (gateError) {
                console.warn('[Process] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            
            // Log visit for Recent items
            try { window.BUDG_API_SERVICE.logVisit({ entity: 'Process', entityId: String(id), route: `/view/process/${id}` }); } catch(_) {}

            // Update header with process data
            console.log('=== UPDATING HEADER ===');
            console.log('Title element found:', !!titleElement);
            console.log('Breadcrumb element found:', !!breadcrumbElement);
            console.log('Actual data keys:', Object.keys(actualData));
            console.log('Process data for title:', {
                primaryName: actualData.primaryName,
                name: actualData.name,
                primaryname: actualData.primaryname,
                Name: actualData.Name
            });
            
            // Update title IMMEDIATELY after loading data
            if (titleElement) {
                const titleText = actualData.primaryName || actualData.primaryname || actualData.name || actualData.Name || vT('facet.process', 'Process');
                titleElement.textContent = titleText;
                console.log('[Process] Title updated to:', titleText);
                console.log('[Process] Title element after update:', titleElement.textContent);
            } else {
                console.error('[Process] Title element not found!');
            }
            if (breadcrumbElement) {
                breadcrumbElement.textContent = vT('facet.process', 'Process');
                console.log('[Process] Breadcrumb updated to: Process');
            }
            
            // Initialize pending changes toggle if object is under revision AFTER updating title
            if (window.PendingChanges) {
                await window.PendingChanges.initToggle('Process', id, '.page-title-text', {
                    applyChanges: (changesMap) => {
                        console.log('[Process] Applying pending changes:', changesMap);
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
                    restoreOriginal: () => {
                        // Generic restore is handled by PendingChanges restoring textContent for original DOM
                    }
                });
                
                // Re-update title after toggle init to ensure it's still correct
                if (titleElement) {
                    const titleText = actualData.primaryName || actualData.primaryname || actualData.name || actualData.Name || vT('facet.process', 'Process');
                    titleElement.textContent = titleText;
                }
            }

            // Debug: Log the actual data being rendered to see if there are differences
            console.log('=== PROCESS DATA COMPARISON ===');
            console.log('View mode:', view);
            console.log('Process ID in data:', actualData.id);
            console.log('Primary Name:', actualData.primaryname);
            console.log('Description:', actualData.description);
            console.log('Ref Number:', actualData.refnumber);
            console.log('Status:', actualData.status);
            console.log('Lifecycle:', actualData.lifecycle_status);
            console.log('Type:', actualData.type);
            
            // Store original data for comparison (only on first load with original view)
            if (view === 'original' || view === null) {
                window.__processOriginalData = JSON.parse(JSON.stringify(actualData));
                console.log('[Process] Stored original data for comparison');
            } else if (view === 'changes' && window.__processOriginalData) {
                console.log('[Process] Comparing changes data with original:');
                console.log('  Original ID:', window.__processOriginalData.id);
                console.log('  Changes ID:', actualData.id);
                console.log('  Primary Name - Original:', window.__processOriginalData.primaryname, 'Changes:', actualData.primaryname);
                console.log('  Description - Original:', window.__processOriginalData.description, 'Changes:', actualData.description);
                console.log('  Ref Number - Original:', window.__processOriginalData.refnumber, 'Changes:', actualData.refnumber);
            }

            // Summary layout: 2 columns matching Policy view pattern
            // Add data-field attributes for pending changes highlighting
            const left = `
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${escapeHtml(vT('glossary.sections.definition', 'DEFINITION'))}</div>
                    ${renderItem(vT('label.description', 'Description'), `<span data-field="description">${_richHtml(actualData.description)}</span>`)}
                    ${renderItem(vT('label.refNumber', 'Ref. Number'), `<span data-field="refnumber">${escapeHtml(actualData.refnumber || actualData.refNumber || actualData.ref_number || vT('message.notSpecified', 'Not specified'))}</span>`)}
                    ${renderItem(vT('label.classification', 'Classification'), `<span data-field="classification">${escapeHtml(actualData.classificationName || actualData.classification || vT('message.notSpecified', 'Not specified'))}</span>`)}
                    ${renderItem(vT('label.automation', 'Automation'), `<span data-field="automation">${escapeHtml(actualData.automationName || actualData.automation || vT('message.notSpecified', 'Not specified'))}</span>`)}
                    ${renderItem(vT('label.inputDescription', 'Input Description'), `<span data-field="input_description">${_richHtml(actualData.input_description || actualData.inputDescription)}</span>`)}
                    ${renderItem(vT('label.outputDescription', 'Output Description'), `<span data-field="output_description">${_richHtml(actualData.output_description || actualData.outputDescription)}</span>`)}
                    ${renderItem(vT('label.permissions', 'Permissions'), renderPermissions({
                        cancreate: actualData.cancreate,
                        canread: actualData.canread,
                        canupdate: actualData.canupdate,
                        candelete: actualData.candelete,
                        canarchive: actualData.canarchive
                    }))}
                </div>
            `;

            const right = `
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${escapeHtml(vT('glossary.sections.classifications', 'CLASSIFICATIONS'))}</div>
                    ${renderItem(vT('glossary.labels.budgStatus', 'BUDG Status'), `<span data-field="status">${renderStatusBadge(actualData.statusName || actualData.status || vT('value.unknown', 'Unknown'), (actualData.statusName || actualData.status) === 'Active')}</span>`)}
                    ${renderItem(vT('glossary.labels.lifecycle', 'Lifecycle'), `<span data-field="lifecycle_status">${renderLifecycleBadge(actualData.lifecycleName || actualData.lifecycle_status || actualData.lifecycleStatus || vT('value.unknown', 'Unknown'))}</span>`)}
                    ${renderItem(vT('glossary.labels.budgViewing', 'BUDG Viewing'), `<span data-field="ispublic">${renderPublicStatus(actualData.viewingName || actualData.ispublic || actualData.is_public)}</span>`)}
                    ${renderItem(vT('label.type', 'Type'), `<span data-field="type">${escapeHtml(actualData.typeName || actualData.type || vT('message.notSpecified', 'Not specified'))}</span>`)}
                    ${renderItem(vT('label.stepType', 'Step Type'), `<span data-field="step_type">${escapeHtml(actualData.stepTypeName || actualData.stepType_id || actualData.step_type || actualData.stepType || vT('message.notSpecified', 'Not specified'))}</span>`)}
                    ${renderItem(vT('label.duration', 'Duration'), `<span data-field="duration">${actualData.duration ? escapeHtml(actualData.duration) : '<span class="empty">-</span>'}</span>`)}
                    ${renderItem(vT('label.durationType', 'Duration Type'), `<span data-field="duration_type">${escapeHtml(actualData.durationTypeName || actualData.duration_type_name || actualData.durationType || actualData.duration_type || vT('message.notSpecified', 'Not specified'))}</span>`)}
                </div>
            `;

            const otherInformationFullWidth = `
                <div class="view-section process-summary-other-information" style="padding: 1.25rem; width: 100%; max-width: 100%; box-sizing: border-box; margin-bottom: 1.25rem;">
                    <div class="section-title">${escapeHtml(vT('glossary.sections.otherInformation', 'OTHER INFORMATION'))}</div>
                    ${renderItem(vT('glossary.labels.createdBy', 'Created By'), renderUserLink(actualData.createdByName || actualData.created_by_name || vT('message.notSpecified', 'Not specified'), actualData.createdby_id || actualData.created_by_id || actualData.createdById || actualData.createdBy_ID))}
                    ${renderItem(vT('glossary.labels.created', 'Created'), renderDate(actualData.createdatetime || actualData.created_datetime || actualData.created_date || vT('message.notSpecified', 'Not specified')))}
                    ${renderItem(vT('glossary.labels.lastUpdatedBy', 'Last Updated By'), renderUserLink(actualData.lastUpdatedByName || actualData.last_updated_by_name || vT('message.notSpecified', 'Not specified'), actualData.lastUpdateUserId || actualData.lastUpdatedUser_ID || actualData.lastupdateuser_id || actualData.last_updated_user_id))}
                    ${renderItem(vT('glossary.labels.lastUpdated', 'Last Updated'), renderDate(actualData.lastupdatedatetime || actualData.last_updated_datetime || actualData.last_updated_date || vT('message.notSpecified', 'Not specified')))}
                    ${renderItem(vT('glossary.labels.segment', 'Segment'), escapeHtml(actualData.segmentName || actualData.segment_name || actualData.segment || ((actualData.segmentId ?? actualData.segment_id ?? actualData.Segment_ID) != null ? vT('common.segmentIdDisplay', 'ID {id}', { id: actualData.segmentId ?? actualData.segment_id ?? actualData.Segment_ID }) : vT('message.notSpecified', 'Not specified'))))}
                </div>
            `;

            // Render collapsible sections
            const docsEmpty = '<div class="empty">' + escapeHtml(vT('process.view.noDocuments', 'This Process has no documents.')) + '</div>';
            const predecessorsContent = await renderPredecessorsView(id, view);
            const contextEmpty = '<div class="empty">' + escapeHtml(vT('process.view.noContext', 'This Process has no context information.')) + '</div>';

            const fullWidthBelowCustomFields = `
                <div class="view-section" style="grid-column:1/-1; padding: 1.25rem;">
                    ${renderCollapsible((window.I18n?.t('card.documents') || 'DOCUMENTS'), docsEmpty, { id: 'documents', icon: 'fa-solid fa-file-lines', open: true })}
                    ${renderCollapsible(vT('card.processPredecessors', 'PROCESS PREDECESSORS'), predecessorsContent, { id: 'predecessors', icon: 'fa-solid fa-arrow-left' })}
                    ${renderCollapsible(vT('card.context', 'CONTEXT'), contextEmpty, { id: 'context', icon: 'fa-solid fa-info-circle' })}
                </div>
            `;

            console.log('=== RENDERED HTML DEBUG ===');
            console.log('Left HTML length:', left.length);
            console.log('Right HTML length:', right.length);
            console.log('Full width HTML length:', fullWidthBelowCustomFields.length);
            console.log('Left HTML preview:', left.substring(0, 200) + '...');
            console.log('Right HTML preview:', right.substring(0, 200) + '...');

            // Two-column summary (Definition | Classifications); full-width Other Information; custom fields; then documents block
            const gridWrapper = `
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1.25rem; align-items: start; width: 100%; box-sizing: border-box; overflow: visible; margin-bottom: 1.25rem;">
                    <div style="display: flex; flex-direction: column; gap: 1.25rem; width: 100%; box-sizing: border-box; overflow: visible; min-height: fit-content;">
                        ${left}
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 1.25rem; width: 100%; box-sizing: border-box; overflow: visible; min-height: fit-content;">
                        ${right}
                    </div>
                </div>
            `;
            
            container.innerHTML = gridWrapper;
            container.insertAdjacentHTML('beforeend', otherInformationFullWidth);

            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Process',
                        containerId: 'processViewContainer',
                        objectId: id,
                        title: vT('card.customFields', 'CUSTOM FIELDS'),
                        view: view === 'changes' ? 'changes' : null
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

            container.insertAdjacentHTML('beforeend', fullWidthBelowCustomFields);
            
            if (container) {
                requestAnimationFrame(() => {
                    container.style.display = '';
                    container.style.visibility = 'visible';
                    container.style.opacity = '1';
                    console.log('[Process] Container displayed after loading data');
                    
                    const contextBody = container.querySelector('#context .collapsible-body');
                    if (contextBody && window.ProcessContextMap) {
                        contextBody.innerHTML = window.ProcessContextMap.getContextMapHtml();
                        window.ProcessContextMap.init(id, actualData);
                    }
                    
                    void container.offsetHeight;
                });
            }

            loadDocuments(id, view);

            console.log('Process content loaded successfully');
            console.log('Container innerHTML length:', container.innerHTML.length);
            
            
            return actualData;

        } catch (e) {
            console.error('=== ERROR LOADING PROCESS ===');
            console.error('Error message:', e.message);
            console.error('Error stack:', e.stack);
            console.error('Error type:', typeof e);
            console.error('Full error object:', e);

            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const errorMessage = isForbidden
                ? 'This object is not available.'
                : `Failed to load process (id=${id}). Error: ${e.message}`;
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column: 1/-1; color: var(--danger, #b91c1c);\">${errorMessage}</div>`;
            return null;
        }
    }

    // Store document table instance for view mode switching
    let processDocumentTable = null;
    
    async function loadDocuments(processId, viewMode = null) {
        // Find the existing documents collapsible section (rendered in fullWidth block)
        const documentsSection = document.querySelector('#documents .collapsible-body');
        if (!documentsSection) {
            console.warn('Documents section not found');
            return;
        }

        // Create container for document table
        const containerId = 'processDocumentsTableContainer';
        documentsSection.innerHTML = `<div id="${containerId}"></div>`;

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                processDocumentTable = new DocumentTableComponent({
                    facetType: 'process',
                    facetId: processId,
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

    // Initialize expandable sections
    function initExpandableSections() {
        const expandableHeaders = document.querySelectorAll('.expandable-header');
        expandableHeaders.forEach(header => {
            header.addEventListener('click', function() {
                const targetId = this.getAttribute('data-target');
                const section = this.closest('.expandable-section');
                const content = document.getElementById(targetId);
                
                if (content) {
                    section.classList.toggle('expanded');
                }
            });
        });
    }

    // Initialize dropdown
    function initDropdown() {
        const dropdownToggle = document.getElementById('followDropdown');
        const dropdownMenu = dropdownToggle?.nextElementSibling;
        
        if (dropdownToggle && dropdownMenu) {
            dropdownToggle.addEventListener('click', function(e) {
                e.stopPropagation();
                dropdownMenu.classList.toggle('show');
            });
            
            // Close dropdown when clicking outside
            document.addEventListener('click', function() {
                dropdownMenu.classList.remove('show');
            });
        }
    }

    // Initialize collapsible sections
    function initCollapsibleSections() {
        const collapsibleHeaders = document.querySelectorAll('.collapsible-header');
        collapsibleHeaders.forEach(header => {
            header.addEventListener('click', function() {
                const section = this.closest('.collapsible-section');
                section.classList.toggle('open');
            });
        });
    }

    // Initialize page
    document.addEventListener('DOMContentLoaded', function() {
        console.log('Process page DOM loaded');
        
        // Check if required services are available
        console.log('BUDG_CONFIG available:', !!window.BUDG_CONFIG);
        console.log('BUDG_API_SERVICE available:', !!window.BUDG_API_SERVICE);
        
        if (window.BUDG_API_SERVICE) {
            console.log('API service methods:', Object.getOwnPropertyNames(Object.getPrototypeOf(window.BUDG_API_SERVICE)));
        }
        
        try {
            hideEditsIfUnauthenticated();
            console.log('Process page loaded');
            
            const backBtn = document.getElementById('backBtn');
            console.log('Back button found:', backBtn);
            
            if (backBtn) {
                backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
            }

            // Initialize collapsible sections
            initCollapsibleSections();
            
            // Edit button functionality
            const editBtn = document.getElementById('tabEditBtn');
            console.log('Edit button found:', editBtn);
            
            if (editBtn) {
                editBtn.addEventListener('click', function() {
                    console.log('=== PARSING ID FOR EDIT ===');
                    console.log('Current URL before parseId:', window.location.href);
                    const id = parseId();
                    console.log('Process ID for edit:', id);
                    if (id != null) {
                        // Get the currently active tab
                        const activeTab = document.querySelector('.tab.active');
                        const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                        console.log('Active tab:', tabName);
                        
                        // Navigate to edit page with tab parameter
                        window.location.href = `/view/process/process-edit.html?id=${id}&tab=${tabName}`;
                    }
                });
            }
            
            console.log('=== PARSING ID FOR LOAD ===');
            console.log('Current URL before parseId:', window.location.href);
            const id = parseId();
            console.log('Process ID parsed:', id);
            
            if (id != null) {
                (async () => {
                    // Ensure content body and container are visible
                    const contentBody = document.querySelector('.content-body');
                    if (contentBody) {
                        contentBody.style.display = '';
                    }
                    const processViewContainer = document.getElementById('processViewContainer');
                    if (processViewContainer) {
                        processViewContainer.style.display = '';
                        processViewContainer.style.visibility = 'visible';
                    }
                    
                    // Resolve initial view BEFORE first render so it behaves like the other facets
                    const initialView = await determineInitialViewMode(id);
                    currentView = initialView;

                    await load(id, initialView);
                    
                    // Force a re-render to ensure data is visible
                    const container = document.getElementById('processViewContainer');
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
                    
                    const summaryContainer = document.getElementById('processViewContainer');
                    if (summaryContainer) {
                        window.processTabData.summary = summaryContainer.innerHTML;
                    }

                    // Initialize unified edit dropdown
                    if (window.EditDropdown && id != null) {
                        (async () => {
                            try {
                                // Check Auto CR status and determine edit restrictions
                                let disableEditOption = false;
                                let disableEditReason = '';
                                try {
                                    const statusRes = await fetch(`/api/pending-changes/status/Process/${id}`, {
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
                                                } catch (e) { console.warn('[Process] Failed to fetch CR details:', e); }
                                            }

                                            const statusLower = (crStatusName || '').toLowerCase();
                                            const isFinished = statusLower.includes('complete') || statusLower.includes('cancelled') || statusLower.includes('canceled') || statusLower.includes('closed') || statusLower.includes('reject');

                                            if (isAutoCR && !isFinished) {
                                                const isRequester = crCreatedBy != null && currentUserId != null && String(crCreatedBy) === String(currentUserId);
                                                const isRunning = statusLower.includes('running') || statusLower.includes('in progress');
                                                console.log('[Process] Auto CR active: status=' + crStatusName + ', isRequester=' + isRequester);

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
                                    console.warn('[Process] Could not check CR status:', e);
                                }
                                
                                // Store auto CR state for stakeholder tab redirect
                                _hasActiveAutoCR = disableEditOption;
                                
                                window.EditDropdown.initialize('process', id, {
                                    container: '.tab-actions',
                                    editUrl: `/view/process/process-edit.html?id=${id}`,
                                    hideEditOption: false,
                                    disableEditOption: disableEditOption,
                                    disableEditReason: disableEditReason
                                });
                            } catch (error) {
                                console.error('Failed to initialize edit dropdown:', error);
                            }
                        })();
                    }

                    // Initialize follow button in header actions
                    if (window.addFollowButton) {
                        try {
                            await window.addFollowButton('process', id, null, '.form-actions');
                        } catch (e) {
                            console.error('Failed to initialize follow button for process:', e);
                        }
                    }
                })().catch(e => console.error('Failed to initialize process view:', e));
            } else {
                console.error('No valid process ID found in URL');
            }
            
            // Initialize expandable sections
            initExpandableSections();
            
            // Set default tab to summary
            const defaultTab = document.querySelector('.tab[data-tab="summary"]');
            if (defaultTab) {
                defaultTab.click();
            }
            
            // Initialize dropdown
            initDropdown();
            
        } catch (error) {
            console.error('Error initializing process page:', error);
        }

        // Tab data storage - make it global
        if (!window.processTabData) {
            window.processTabData = {
                summary: null,
                components: null,
                stakeholders: null,
                impact: null,
                data: null,
                history: null,
                change: null
            };
        }

        // Helper function to hide all containers
        function hideAllProcessContainers() {
            const containerIds = [
                'processViewContainer',
                'processComponentsContainer',
                'processStakeholdersContainer',
                'processImpactContainer',
                'processDataContainer',
                'processHistoryContainer',
                'processChangeContainer'
            ];
            containerIds.forEach(function(containerId) {
                const container = document.getElementById(containerId);
                if (container) container.style.display = 'none';
            });
        }

        // Traditional tab handling
        const tabs = document.querySelectorAll('.tab-container .tab');
        console.log('Found tabs:', tabs.length);
        
        tabs.forEach(function(tab) {
            tab.addEventListener('click', function() {
                console.log('Tab clicked:', this.textContent, 'data-tab:', this.getAttribute('data-tab'));
                if (this.disabled || this.classList.contains('disabled')) {
                    return;
                }

                const which = this.getAttribute('data-tab');
                const processId = parseId();
                
                // Toggle stakeholder-only edit mode based on active tab and auto CR state
                if (window.EditDropdown && _hasActiveAutoCR && _canEditObject) {
                    if (which === 'stakeholders') {
                        window.EditDropdown.enableStakeholderOnlyEdit(`/view/process/process-edit.html?id=${processId}`);
                    } else {
                        window.EditDropdown.disableStakeholderOnlyEdit();
                    }
                }
                
                tabs.forEach(function(t){ t.classList.remove('active'); });
                this.classList.add('active');

                const body = document.querySelector('.content-body');
                
                console.log('Switching to tab:', which);
                
                // Hide all containers
                hideAllProcessContainers();
                
                switch (which) {
                    case 'summary':
                        const summaryContainer = document.getElementById('processViewContainer');
                        if (summaryContainer) {
                            summaryContainer.style.display = 'grid';
                            if (processId != null) {
                                load(processId).then(async function(data) {
                                    setTimeout(function() {
                                        window.processTabData.summary = summaryContainer.innerHTML;
                                    }, 100);
                                    
                                    // Check and display lock status
                                    if (window.ViewLockHelper) {
                                        await window.ViewLockHelper.checkAndDisplayLockStatus('process', processId);
                                    }
                                });
                            }
                        }
                        break;

                    case 'components':
                        const componentsContainer = document.getElementById('processComponentsContainer');
                        if (componentsContainer) {
                            componentsContainer.style.display = 'block';
                            if (processId != null) {
                                loadProcessComponents(processId, componentsContainer);
                            }
                        }
                        break;

                    case 'stakeholders':
                        const stakeholdersContainer = document.getElementById('processStakeholdersContainer');
                        if (stakeholdersContainer) {
                            stakeholdersContainer.style.display = 'block';
                            if (processId != null && window.ProcessStakeholderView) {
                                // Use current view mode (original or changes) based on toggle state
                                window.ProcessStakeholderView.init(processId, currentView);
                                window.processTabData.stakeholders = stakeholdersContainer.innerHTML;
                            }
                        }
                        break;

                    case 'impact':
                        const impactContainer = document.getElementById('processImpactContainer');
                        if (impactContainer) {
                            impactContainer.style.display = 'block';
                            if (processId != null && window.loadProcessImpact) {
                                // Use current view mode (original or changes) based on toggle state
                                window.loadProcessImpact(processId, currentView);
                            }
                        }
                        break;

                    case 'data':
                        const dataContainer = document.getElementById('processDataContainer');
                        if (dataContainer) {
                            dataContainer.style.display = 'block';
                            if (processId != null && window.loadProcessData) {
                                window.loadProcessData(processId, 'original');
                            }
                        }
                        break;

                    case 'history':
                        const historyContainer = document.getElementById('processHistoryContainer');
                        if (historyContainer) {
                            historyContainer.style.display = 'block';
                            historyContainer.innerHTML = '<div id="processHistoryComponentContainer"></div>';
                            if (processId != null && window.HistoryComponent) {
                                window.HistoryComponent.initialize('Processes', processId, 'processHistoryComponentContainer');
                            } else {
                                historyContainer.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">HISTORY</div><div class="empty">History component not available</div></div>';
                            }
                        }
                        break;

                    case 'change':
                        const changeContainer = document.getElementById('processChangeContainer');
                        if (changeContainer) {
                            changeContainer.style.display = 'block';
                            changeContainer.innerHTML = '<div id="processChangeComponentContainer"></div>';
                            if (processId != null && window.ChangeTabComponent) {
                                window.ChangeTabComponent.initialize('process', processId, 'processChangeComponentContainer');
                            } else {
                                changeContainer.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">CHANGE</div><div class="empty">Change component not available</div></div>';
                            }
                        }
                        break;

                    default:
                        const defaultContainer = document.getElementById('processViewContainer');
                        if (defaultContainer) {
                            defaultContainer.style.display = 'grid';
                            if (processId != null) {
                                load(processId);
                            }
                        }
                        break;
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('processStakeholdersContainer');
                }
            });
        });
        
    });
    
    // Update title only (without reloading entire page) - similar to System.js
    async function updateTitleOnly(processId, viewMode) {
        try {
            const viewParam = viewMode === 'changes' ? 'changes' : null;
            const data = await window.BUDG_API_SERVICE.getProcessById(processId, viewParam);
            console.log('[Process] updateTitleOnly called:', { processId, viewMode, viewParam });
            
            const actualData = data?.data || data;
            console.log('[Process] updateTitleOnly - loaded data:', { id: actualData?.id, primaryname: actualData?.primaryname, expectedId: processId });
            
            const titleElement = document.getElementById('processTitle');
            if (titleElement && actualData) {
                // Verify data is correct for the view
                if (viewMode === 'original' && actualData.id !== processId) {
                    console.warn('[Process] updateTitleOnly - Data ID mismatch! Expected:', processId, 'Got:', actualData.id);
                    // Reload original data to ensure correct name
                    try {
                        const originalData = await window.BUDG_API_SERVICE.getProcessById(processId, null);
                        const originalActualData = originalData?.data || originalData;
                        if (originalActualData && originalActualData.primaryname && originalActualData.id === processId) {
                            const titleText = originalActualData.primaryName || originalActualData.primaryname || originalActualData.name || originalActualData.Name || 'Process';
                            titleElement.textContent = titleText;
                            console.log('[Process] updateTitleOnly - Title corrected to original:', titleText);
                        } else {
                            const titleText = actualData.primaryName || actualData.primaryname || actualData.name || actualData.Name || 'Process';
                            titleElement.textContent = titleText;
                            console.log('[Process] updateTitleOnly - Title updated (fallback):', titleText);
                        }
                    } catch (e) {
                        console.warn('[Process] updateTitleOnly - Failed to reload original data:', e);
                        const titleText = actualData.primaryName || actualData.primaryname || actualData.name || actualData.Name || 'Process';
                        titleElement.textContent = titleText;
                    }
                } else {
                    const titleText = actualData.primaryName || actualData.primaryname || actualData.name || actualData.Name || 'Process';
                    titleElement.textContent = titleText;
                    console.log('[Process] updateTitleOnly - Title updated:', titleText, '(viewMode:', viewMode, ')');
                }
            }
        } catch (error) {
            console.error('[Process] updateTitleOnly - Error updating title:', error);
        }
    }

    // Listen for pending changes view switch events
    document.addEventListener('pendingChangesViewSwitch', async function(event) {
        const { view, facetType, objectId, mappings } = event.detail;
        console.log('[Process] pendingChangesViewSwitch event received, view:', view);
        
        if (facetType === 'Process' && objectId) {
            const processId = parseInt(objectId, 10);
            if (!isNaN(processId)) {
                // Update current view
                currentView = view;
                
                // Update title first (similar to System.js)
                await updateTitleOnly(processId, view);
                
                // Reload main process data (summary tab)
                console.log('[Process] Reloading process data with view:', view);
                await load(processId, view);
                
                // If Impact or Stakeholders tab is currently active, reload it with new view
                const activeTab = document.querySelector('.tab-container .tab.active');
                const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : null;
                if (activeTabName === 'impact' && window.loadProcessImpact) {
                    console.log('[Process] Reloading Impact tab with view:', view);
                    const impactContainer = document.getElementById('processImpactContainer');
                    if (impactContainer && impactContainer.style.display !== 'none') {
                        window.loadProcessImpact(processId, view);
                    }
                } else if (activeTabName === 'stakeholders' && window.ProcessStakeholderView) {
                    console.log('[Process] Reloading Stakeholders tab with view:', view);
                    const stakeholdersContainer = document.getElementById('processStakeholdersContainer');
                    if (stakeholdersContainer && stakeholdersContainer.style.display !== 'none') {
                        window.ProcessStakeholderView.init(processId, view);
                    }
                }
                
                // Also reload documents with new view mode
                if (processDocumentTable) {
                    processDocumentTable.setViewMode(view);
                }
            }
        }
    });
    
    // Render predecessors view (read-only)
    async function renderPredecessorsView(processId, view = null) {
        try {
            // Build URL with view parameter if provided
            let url = `/api/process-impact/${processId}/predecessors`;
            if (view === 'changes') {
                url += '?view=changes';
            }
            // Add cache-busting parameter to ensure fresh data when switching views
            url += (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime();
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            
            if (!response.ok) {
                if (response.status === 404) {
                    return '<div class="empty">This Process has no predecessors.</div>';
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const relationships = await response.json();
            const predecessors = Array.isArray(relationships) ? relationships : (relationships?.data || []);
            
            if (predecessors.length === 0) {
                return '<div class="empty">This Process has no predecessors.</div>';
            }
            
            let html = `
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>Ref.</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Predecessor</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Type</span><i class="fas fa-sort"></i></div></th>
                                <th><div class="th-content"><span>Condition</span><i class="fas fa-sort"></i></div></th>
                            </tr>
                        </thead>
                        <tbody>
            `;
            
            predecessors.forEach(rel => {
                const processName = rel.targetProcessName || rel.targetprocessname || 'Unknown';
                const processRef = rel.targetProcessRef || rel.targetprocessref || rel.refNumber || '';
                const processType = rel.targetProcessType || rel.targetprocesstype || 'Process';
                const relationType = rel.relationTypeName || rel.relationtypename || rel.typeName || '';
                const condition = rel.annotations || rel.condition || '';
                const targetProcessId = rel.targetProcessId || rel.targetprocess_id;
                
                const processLink = targetProcessId 
                    ? `<a href="/view/process/${targetProcessId}" class="relationship-link">${escapeHtml(processName)}</a>`
                    : escapeHtml(processName);
                
                html += `
                    <tr>
                        <td>${escapeHtml(processRef)}</td>
                        <td>${processLink}</td>
                        <td>${escapeHtml(relationType || processType)}</td>
                        <td>${escapeHtml(condition)}</td>
                    </tr>
                `;
            });
            
            html += `
                        </tbody>
                    </table>
                </div>
                <div class="hierarchy-footer">
                    ${predecessors.length} record${predecessors.length !== 1 ? 's' : ''}
                </div>
            `;
            
            return html;
            
        } catch (error) {
            console.error('Error loading predecessors view:', error);
            return '<div class="empty">Error loading predecessors.</div>';
        }
    }

    // Debug auto-load removed (it was causing duplicate load() calls, which could duplicate the Pending Changes toggle)
})();

