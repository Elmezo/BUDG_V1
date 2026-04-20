// Capability View Page JavaScript
(function() {
    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        // expect /view/capability/{id}
        const idx = parts.indexOf('capability');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

    function t(key, fallback, params) {
        if (!window.I18n || typeof window.I18n.t !== 'function') return fallback != null ? fallback : key;
        const s = window.I18n.t(key, params);
        if (s === key && fallback != null) return fallback;
        return s;
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
                console.log('[Capability] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Capability', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Capability] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Capability] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Capability] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Capability', { method: 'GET', credentials: 'include' });
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
            '#deleteCapabilityBtn'
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
    }

    // Check if user has admin permissions
    async function checkAdminPermissions() {
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!meResp.ok) {
                return false;
            }
            const me = await meResp.json();
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            return isAdmin;
        } catch(e) {
            console.error('Error checking admin permissions:', e);
            return false;
        }
    }

// Escape HTML to prevent XSS
function escapeHtml(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// Render rich HTML safely (description fields from editor)
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

// Render item helper function
function renderItem(label, value) {
    if (value == null || value === '') {
        value = '<span class="empty">-</span>';
    }
    return `
        <div class="view-item">
            <div class="view-label">${label.includes('<') ? label : escapeHtml(label)}</div>
            <div class="view-value">${value}</div>
        </div>
    `;
}

// Format date/time helper
function formatDateTime(dt) {
    if (!dt) return '<span class="empty">-</span>';
    try {
        const d = typeof dt === 'string' || typeof dt === 'number' ? new Date(dt) : dt;
        if (isNaN(d.getTime())) return escapeHtml(String(dt));
        const loc = (window.I18n && window.I18n.currentLocale === 'ar') ? 'ar' : undefined;
        return escapeHtml(d.toLocaleString(loc));
    } catch (_) {
        return escapeHtml(String(dt));
    }
}

// Render status badge
function renderStatusBadge(status, isActive) {
    if (!status) return '<span class="empty">-</span>';
    const badgeClass = isActive ? 'status-active' : 'status-inactive';
    return `<span class="status-badge ${badgeClass}">${escapeHtml(status)}</span>`;
}

// Render lifecycle badge
function renderLifecycleBadge(lifecycle) {
    if (!lifecycle) return '<span class="empty">-</span>';
    return `<span class="lifecycle-badge">${escapeHtml(lifecycle)}</span>`;
}

// Render public status
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
        const pub = t('systemInterface.options.public', 'Public');
        return `<span class="status-badge status-active">${escapeHtml(pub)}</span>`;
    } else {
        const priv = t('systemInterface.options.private', 'Private');
        return `<span class="status-badge status-inactive">${escapeHtml(priv)}</span>`;
    }
}

// Render user link
function renderUserLink(name, id) {
    if (!name) return '<span class="empty">-</span>';
    if (id) {
        return `<a href="/view/people/${id}">${escapeHtml(name)}</a>`;
    }
    return escapeHtml(name);
}

// Resolve foreign key references
async function resolveReferences(capability) {
    const api = window.BUDG_API_SERVICE;
    const tasks = [];

    // Status name from Status ID
    if (!capability.statusName && (capability.Status || capability.status)) {
        const statusId = capability.Status || capability.status;
        tasks.push((async () => {
            try {
                let status = await api.getStatusById(statusId);
                if (status && status.data) status = status.data;
                capability.statusName = status?.primaryname || status?.Name || status?.name || String(statusId);
                console.log('Resolved status name:', capability.statusName);
            } catch(e) { 
                console.error('Failed to resolve status name:', e);
                capability.statusName = String(statusId);
            }
        })());
    }

    // Lifecycle name from Lifecycle ID
    // TEMPORARILY DISABLED - Capability lifecycle API calls causing 404 errors
    // TODO: Re-enable when backend capability lifecycle endpoints are implemented
    /*
    if (!capability.lifecycleName && (capability.Lifecycle || capability.lifecycle)) {
        const lifecycleId = capability.Lifecycle || capability.lifecycle;
        console.log('Resolving capability lifecycle ID:', lifecycleId);
        tasks.push((async () => {
            try {
                // First try the specific endpoint
                let lifecycle;
                try {
                    // Try different endpoint formats for capability lifecycle
                    let response;
                    try {
                        // Try the alternative endpoint format (capability-lifecycles instead of capability-lifecyle)
                        response = await fetch(`${window.BUDG_CONFIG.API_BASE_URL}/capability-lifecycles/${lifecycleId}`, {
                            headers: api.getDefaultHeaders()
                        });
                        if (!response.ok) throw new Error(`HTTP ${response.status}`);
                        response = await response.json();
                        console.log('Capability Lifecycle API response (alternative endpoint):', response);
                    } catch(altError) {
                        console.log('Alternative endpoint failed, trying config endpoint:', altError.message);
                        response = await api.getCapabilityLifecycleById(lifecycleId);
                        console.log('Capability Lifecycle API response (config endpoint):', response);
                    }
                    
                    // Handle different response formats
                    let lifecycleData = response;
                    if (response && response.data) lifecycleData = response.data;
                    
                    // If it's an array, find the matching item
                    if (Array.isArray(lifecycleData)) {
                        lifecycle = lifecycleData.find(l => (l.ID === lifecycleId || l.id === lifecycleId));
                        console.log('Found lifecycle in array:', lifecycle);
                    } else {
                        lifecycle = lifecycleData;
                    }
                } catch(specificError) {
                    console.log('Specific endpoint failed, trying alternative approaches:', specificError.message);
                    
                    // Try multiple different approaches
                    try {
                        // Approach 1: Try the config endpoint directly (capabilities/lifecycle)
                        let response = await fetch(`${window.BUDG_CONFIG.API_BASE_URL}/capabilities/lifecycle`, {
                            headers: api.getDefaultHeaders()
                        });
                        if (response.ok) {
                            let allLifecycles = await response.json();
                            console.log('Capabilities lifecycle endpoint (from config):', allLifecycles);
                            if (allLifecycles && allLifecycles.data) allLifecycles = allLifecycles.data;
                            lifecycle = Array.isArray(allLifecycles) ? 
                                allLifecycles.find(l => l.ID === lifecycleId || l.id === lifecycleId) : allLifecycles;
                            console.log('Found lifecycle from capabilities/lifecycle endpoint:', lifecycle);
                        } else {
                            throw new Error(`Capabilities lifecycle endpoint failed: ${response.status}`);
                        }
                    } catch(directError) {
                        console.log('Direct endpoint failed, trying API method:', directError.message);
                        
                        try {
                            // Approach 2: Try using the API method directly
                            let capabilityLifecycles = await api.getCapabilityLifecycle();
                            console.log('API getCapabilityLifecycle() result:', capabilityLifecycles);
                            if (capabilityLifecycles && !capabilityLifecycles.error) {
                                if (capabilityLifecycles && capabilityLifecycles.data) capabilityLifecycles = capabilityLifecycles.data;
                                lifecycle = Array.isArray(capabilityLifecycles) ? 
                                    capabilityLifecycles.find(l => l.ID === lifecycleId || l.id === lifecycleId) : capabilityLifecycles;
                                console.log('Found lifecycle from API method:', lifecycle);
                            } else {
                                throw new Error('API method returned error or no data');
                            }
                        } catch(apiError) {
                            console.log('API method failed, trying general lifecycle list:', apiError.message);
                            
                            try {
                                // Approach 3: Use general lifecycle list and filter
                            let generalLifecycles = await api.getLifecycleList();
                            console.log('General Lifecycle List:', generalLifecycles);
                            if (generalLifecycles && generalLifecycles.data) generalLifecycles = generalLifecycles.data;
                            
                            // Look for capability-related lifecycle
                            lifecycle = Array.isArray(generalLifecycles) ? 
                                generalLifecycles.find(l => (l.id === lifecycleId || l.ID === lifecycleId) && 
                                    (l.type === 'capability' || l.category === 'capability' || !l.type)) : generalLifecycles;
                            console.log('Found lifecycle from general list:', lifecycle);
                            } catch(generalError) {
                                console.log('All approaches failed:', generalError.message);
                                lifecycle = null;
                            }
                        }
                    }
                }
                
                // Try to get real data from connected database using different approaches
                let foundRealData = false;
                
                // Approach 1: Try to fetch all capability lifecycles and find the matching one
                if (!foundRealData) {
                    try {
                        console.log('Trying to fetch all capability lifecycles from database...');
                        
                        // Try different possible endpoints for getting all capability lifecycles
                        const possibleEndpoints = [
                            '/capability-lifecycles',
                            '/capability-lifecycle',
                            '/capabilitylifecycles',
                            '/capabilitylifecycle',
                            '/capability_lifecycles',
                            '/capability_lifecycle'
                        ];
                        
                        for (const endpoint of possibleEndpoints) {
                            try {
                                console.log(`Trying endpoint: ${endpoint}`);
                                let response = await fetch(`${window.BUDG_CONFIG.API_BASE_URL}${endpoint}`, {
                                    headers: api.getDefaultHeaders()
                                });
                                
                                if (response.ok) {
                                    let allLifecycles = await response.json();
                                    console.log(`Success with ${endpoint}:`, allLifecycles);
                                    
                                    if (allLifecycles && allLifecycles.data) allLifecycles = allLifecycles.data;
                                    
                                    if (Array.isArray(allLifecycles)) {
                                        const matchingLifecycle = allLifecycles.find(l => 
                                            (l.ID === lifecycleId || l.id === lifecycleId)
                                        );
                                        
                                        if (matchingLifecycle) {
                                            capability.lifecycleName = matchingLifecycle.PrimaryName || 
                                                                     matchingLifecycle.primaryName || 
                                                                     matchingLifecycle.Name || 
                                                                     matchingLifecycle.name;
                                            console.log('✅ Found real capability lifecycle from database:', capability.lifecycleName);
                                            foundRealData = true;
                                            break;
                                        }
                                    }
                                }
                            } catch (endpointError) {
                                console.log(`Endpoint ${endpoint} failed:`, endpointError.message);
                            }
                        }
                    } catch (error) {
                        console.log('Failed to fetch capability lifecycles:', error.message);
                    }
                }
                
                // Approach 2: If no real data found, try to query the database directly
                if (!foundRealData) {
                    try {
                        console.log('Trying direct database query approach...');
                        
                        // Try to use a generic query endpoint if available
                        let response = await fetch(`${window.BUDG_CONFIG.API_BASE_URL}/query`, {
                            method: 'POST',
                            headers: {
                                ...api.getDefaultHeaders(),
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify({
                                table: 'capability_lifecyle',
                                where: { ID: lifecycleId },
                                select: ['PrimaryName']
                            })
                        });
                        
                        if (response.ok) {
                            let result = await response.json();
                            console.log('Direct query result:', result);
                            
                            if (result && result.data && result.data.length > 0) {
                                capability.lifecycleName = result.data[0].PrimaryName;
                                console.log('✅ Found capability lifecycle via direct query:', capability.lifecycleName);
                                foundRealData = true;
                            }
                        }
                    } catch (queryError) {
                        console.log('Direct query failed:', queryError.message);
                    }
                }
                
                // Fallback: Use API data or final fallback
                if (!foundRealData) {
                    if (lifecycle && typeof lifecycle === 'object' && !lifecycle.error) {
                        capability.lifecycleName = lifecycle?.PrimaryName || lifecycle?.primaryName || lifecycle?.Name || lifecycle?.name;
                        console.log('⚠️ Using general lifecycle data (not capability-specific):', capability.lifecycleName);
                    } else {
                        capability.lifecycleName = `Capability Lifecycle ${lifecycleId}`;
                        console.log('⚠️ Using final fallback for capability lifecycle:', capability.lifecycleName);
                    }
                }
                console.log('Final resolved capability lifecycle name:', capability.lifecycleName);
            } catch(e) { 
                console.error('Failed to resolve capability lifecycle name:', e);
                capability.lifecycleName = String(lifecycleId);
            }
        })());
    }
    */
    
    // Lifecycle name - Resolve foreign key
    if (!capability.lifecycleName && (capability.Lifecycle || capability.lifecycle)) {
        const lifecycleId = capability.Lifecycle || capability.lifecycle;
        tasks.push((async () => {
            try {
                let lifecycle = await api.getCapabilityLifecycleByIdDirect(lifecycleId);
                if (lifecycle && lifecycle.data) lifecycle = lifecycle.data;
                capability.lifecycleName = lifecycle?.PrimaryName || lifecycle?.primaryName || String(lifecycleId);
                console.log('Resolved capability lifecycle name:', capability.lifecycleName);
            } catch(e) {
                console.error('Failed to resolve capability lifecycle name:', e);
                capability.lifecycleName = String(lifecycleId);
            }
        })());
    }

    // BUDG Viewing from Is_Public field
    if (!capability.viewingName && (capability.Is_Public || capability.isPublic)) {
        const viewingId = capability.Is_Public || capability.isPublic;
        tasks.push((async () => {
            try {
                let viewing = await api.getViewingById(viewingId);
                if (viewing && viewing.data) viewing = viewing.data;
                capability.viewingName = viewing?.Name || viewing?.name || String(viewingId);
                console.log('Resolved BUDG Viewing name:', capability.viewingName);
            } catch(e) {
                console.error('Failed to resolve BUDG Viewing name:', e);
                capability.viewingName = String(viewingId);
            }
        })());
    }

    // Capability Type name - Resolve foreign key
    if (!capability.typeName && (capability.Capability_Type || capability.capabilityType)) {
        const typeId = capability.Capability_Type || capability.capabilityType;
        tasks.push((async () => {
            try {
                let type = await api.getCapabilityTypeById(typeId);
                if (type && type.data) type = type.data;
                capability.typeName = type?.PrimaryName || type?.primaryName || String(typeId);
                console.log('Resolved capability type name:', capability.typeName);
            } catch(e) {
                console.error('Failed to resolve capability type name:', e);
                capability.typeName = String(typeId);
            }
        })());
    }

    // Classification name - Resolve foreign key
    if (!capability.classificationName && (capability.Classification || capability.classification)) {
        const classificationId = capability.Classification || capability.classification;
        tasks.push((async () => {
            try {
                let classification = await api.getCapabilityClassificationById(classificationId);
                if (classification && classification.data) classification = classification.data;
                capability.classificationName = classification?.PrimaryName || classification?.primaryName || String(classificationId);
                console.log('Resolved classification name:', capability.classificationName);
            } catch(e) {
                console.error('Failed to resolve classification name:', e);
                capability.classificationName = String(classificationId);
            }
        })());
    }

    // Last Update User name
    if (!capability.lastUpdateUserName && capability.LastUpdateUser_ID) {
        const userId = capability.LastUpdateUser_ID;
        tasks.push((async () => {
            try {
                let person = await api.getPersonById(userId);
                if (person && person.data) person = person.data;
                const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                    .filter(Boolean).join(' ').trim();
                capability.lastUpdateUserName = fullName || person?.Email || person?.email || String(userId);
                console.log('Resolved last update user name:', capability.lastUpdateUserName);
            } catch(e) {
                console.error('Failed to resolve last update user name:', e);
                capability.lastUpdateUserName = String(userId);
            }
        })());
    }

    await Promise.all(tasks);
    return capability;
}


// Load and render capability data
async function load(id) {
    const container = document.getElementById('capabilityViewContainer');
    if (!container) {
        console.error('Capability view container not found');
        return;
    }

    container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + escapeHtml(t('message.loading', 'Loading...')) + '</div>';

    try {
        console.log('=== CALLING API ===');
        console.log('API Service:', window.BUDG_API_SERVICE);
        console.log('Calling getCapabilityById with ID:', id);
        
        const rawData = await window.BUDG_API_SERVICE.getCapabilityById(id);
        console.log('=== RAW CAPABILITY DATA ===');
        console.log('Raw data:', rawData);
        console.log('Raw data type:', typeof rawData);
        console.log('Is array:', Array.isArray(rawData));
        console.log('Raw data keys:', Object.keys(rawData));
        
        // Handle different response formats like policy page does
        let data = rawData;
        if (rawData && rawData.data) {
            data = rawData.data;
            console.log('Using rawData.data as actual data');
        } else if (rawData && rawData.result) {
            data = rawData.result;
            console.log('Using rawData.result as actual data');
        } else if (rawData && rawData.response) {
            data = rawData.response;
            console.log('Using rawData.response as actual data');
        }
        
        // If data is still an array, take the first element
        if (Array.isArray(data) && data.length > 0) {
            data = data[0];
            console.log('Using first array element as actual data');
        }
        
        console.log('Final processed data:', data);
        console.log('Capability data loaded:', data);

        // Resolve foreign key references
        console.log('=== RESOLVING CAPABILITY REFERENCES ===');
        const resolvedData = await resolveReferences(data);
        
        // Guest / segment gating: unauthenticated users may only view Enterprise segment
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            let isGuest = true;
            if (resp.ok) {
                const me = await resp.json().catch(() => ({}));
                const role = (me.role || '').toString().toLowerCase();
                const isAuthenticated = me.authenticated === true;
                const isGuestRole = role.includes('guest');
                isGuest = !isAuthenticated || isGuestRole;
            }
            if (isGuest) {
                const segmentName = resolvedData.segmentName || resolvedData.segment_name || resolvedData.segment;
                const segmentId = resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (segmentNameLower !== 'enterprise') {
                    console.log('[Capability] Guest visitor blocked from non-Enterprise segment view', {
                        capabilityId: id,
                        segmentName,
                        segmentId
                    });
                    const container = document.getElementById('capabilityViewContainer');
                    if (container) {
                        const title = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                        const message = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.message')) || 'This item is not available for guest users.';
                        const code = 'ERR-SEGMENT-NOT-ENTERPRISE';
                        const segmentLabel = segmentName || ((segmentId !== undefined && segmentId !== null) ? t('capability.view.segmentId', 'ID {id}', { id: segmentId }) : t('value.unknown', 'Unknown'));
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
                    return;
                }
            }
        } catch (gateError) {
            console.warn('[Capability] Error during guest/segment access check, falling back to normal render:', gateError);
        }
        console.log('References resolved, final data:', resolvedData);
        console.log('Final Classification fields after resolving:', {
            Classification: resolvedData.Classification,
            classification: resolvedData.classification,
            classificationName: resolvedData.classificationName
        });

        try { 
            await window.BUDG_API_SERVICE.logVisit({entity: 'Capability', entityId: String(id), route: `/view/capability/${id}` });
        } catch(_) {}

        // Update header with capability data (dynamic title policy)
        const titleElement = document.getElementById('capabilityTitle');
        const breadcrumbElement = document.getElementById('capabilityBreadcrumb');
        
        // Debug: log available fields to understand data structure
        if (data && typeof data === 'object') {
            console.log('Capability data fields:', Object.keys(data));
            console.log('Available name fields:', {
                primaryname: data.primaryname,
                name: data.name,
                primaryName: data.primaryName,
                Name: data.Name,
                description: data.description
            });
            console.log('Classification fields debug:', {
                Classification: data.Classification,
                classification: data.classification,
                classificationName: data.classificationName
            });
            console.log('Foreign key fields debug:', {
                Capability_Type: data.Capability_Type,
                capabilityType: data.capabilityType,
                Lifecycle: data.Lifecycle,
                lifecycle: data.lifecycle
            });
        }
        
        if (titleElement) {
            const titleText = resolvedData.PrimaryName || resolvedData.primaryName || resolvedData.name || resolvedData.Name || resolvedData.description || t('facet.capability', 'Capability');
            console.log('Setting title to:', titleText);
            titleElement.textContent = titleText;
        }
        if (breadcrumbElement) {
            breadcrumbElement.textContent = t('facet.capability', 'Capability');
        }

        const notSpecified = t('message.notSpecified', 'Not specified');
        const unknownLabel = t('value.unknown', 'Unknown');
        const segId = resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID;
        const segmentFallback = (segId != null)
            ? t('capability.view.segmentId', 'ID {id}', { id: segId })
            : notSpecified;

        // Render left column (DEFINITION)
        const left = `
            <div class="view-section">
                <div class="section-title">
                    <i class="fas fa-circle-info" style="margin-right:.4rem;"></i>
                    ${escapeHtml(t('glossary.sections.definition', 'DEFINITION'))}
                </div>
                ${renderItem(t('label.description', 'Description'), _richHtml(resolvedData.Description || resolvedData.description))}
                ${renderItem(t('label.refNumber', 'Ref Number'), escapeHtml(resolvedData.RefNumber || resolvedData.refNumber || resolvedData.ref_number))}
                ${renderItem(t('label.name', 'Name'), escapeHtml(resolvedData.PrimaryName || resolvedData.primaryName || resolvedData.name))}
            </div>
        `;

        // Debug: Check lifecycle values before rendering
        console.log('=== LIFECYCLE DEBUG BEFORE RENDERING ===');
        console.log('resolvedData.lifecycleName:', resolvedData.lifecycleName);
        console.log('resolvedData.Lifecycle:', resolvedData.Lifecycle);
        console.log('Final lifecycle value for rendering:', resolvedData.lifecycleName || resolvedData.Lifecycle || 'Unknown');
        
        // Render right column (CLASSIFICATIONS and OTHER INFORMATION)
        const right = `
            <div class="view-section">
                <div class="section-title">
                    <i class="fas fa-layer-group" style="margin-right:.4rem;"></i>
                    ${escapeHtml(t('glossary.sections.classifications', 'CLASSIFICATIONS'))}
                </div>
                <div class="section-subtitle">${escapeHtml(t('glossary.sections.basicClassifications', 'BASIC CLASSIFICATIONS'))}</div>
                ${renderItem(t('glossary.labels.budgStatus', 'BUDG Status'), renderStatusBadge(resolvedData.statusName || resolvedData.Status || unknownLabel, (resolvedData.statusName || resolvedData.Status) === 'Active'))}
                ${renderItem(t('glossary.labels.lifecycle', 'Lifecycle'), renderLifecycleBadge(resolvedData.lifecycleName || notSpecified))}
                <!-- DEBUG: lifecycleName = ${resolvedData.lifecycleName}, Lifecycle = ${resolvedData.Lifecycle} -->
                ${renderItem(t('glossary.labels.budgViewing', 'BUDG Viewing'), renderPublicStatus(resolvedData.viewingName || resolvedData.Is_Public))}
                ${renderItem(t('glossary.labels.created', 'Created'), formatDateTime(resolvedData.CreateDatetime || resolvedData.createDatetime || resolvedData.created_date))}
                ${renderItem(t('glossary.labels.lastUpdatedBy', 'Last Updated By'), renderUserLink(resolvedData.lastUpdateUserName || resolvedData.last_update_user_name, resolvedData.LastUpdateUser_ID || resolvedData.last_update_user_id))}
                ${renderItem(t('glossary.labels.lastUpdated', 'Last Updated'), formatDateTime(resolvedData.LastUpdateDatetime || resolvedData.lastUpdateDatetime || resolvedData.last_update_date))}
                ${renderItem(t('glossary.labels.segment', 'Segment'), escapeHtml(resolvedData.segmentName || resolvedData.segment_name || resolvedData.segment || segmentFallback))}

            </div>
            <div class="view-section">
                <div class="section-subtitle">${escapeHtml(t('glossary.sections.otherInformation', 'OTHER INFORMATION'))}</div>
                ${renderItem(t('label.classification', 'Classification'), escapeHtml(resolvedData.classificationName || notSpecified))}
                ${renderItem(t('capability.view.capabilityType', 'Capability Type'), escapeHtml(resolvedData.typeName || notSpecified))}
            </div>
        `;

        
        // Render the grid content
        container.innerHTML = left + right;
        
        // Render custom fields section
        if (window.CustomFields) {
            try {
                await window.CustomFields.renderViewSection({
                    facetId: 'Capability',
                    containerId: 'capabilityViewContainer',
                    objectId: id,
                    title: t('card.customFields', 'CUSTOM FIELDS')
                });
            } catch (error) {
                console.error('Error rendering custom fields:', error);
            }
        }

        // Add DOCUMENTS collapsible section
        const noDocsText = window.I18n?.t('message.noDocuments') || 'No documents';
        const docsEmpty = '<div class="empty">' + noDocsText + '.</div>';
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

        // Load Documents
        loadDocuments(id);

    } catch (error) {
        console.error('Error loading capability:', error);
        const isForbidden = error?.status === 403 || String(error?.message || '').includes('403');
        const message = isForbidden
            ? t('capability.view.objectNotAvailable', 'This object is not available.')
            : t('capability.view.loadFailed', 'Failed to load capability: {error}', { error: error.message || '' });
        showError(message);
    }
    }

    async function loadDocuments(capabilityId) {
        // Find the documents collapsible section
        const documentsSection = document.querySelector('[data-collapsible] .collapsible-body');
        if (!documentsSection) {
            console.warn('Documents section not found');
            return;
        }

        // Create container for document table
        const containerId = 'capabilityDocumentsTableContainer';
        documentsSection.innerHTML = `<div id="${containerId}"></div>`;

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                new DocumentTableComponent({
                    facetType: 'capability',
                    facetId: capabilityId,
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

// Show error message
function showError(message) {
    const container = document.getElementById('capabilityViewContainer');
    if (container) {
        const errTitle = t('message.error', 'Error');
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">${escapeHtml(errTitle)}</div>
                <div class="empty">${escapeHtml(message)}</div>
            </div>
        `;
    }
}

    // Initialize the page
    async function init() {
        const id = parseId();
        if (!id) {
            console.error('No capability ID found in URL');
            const errH = escapeHtml(t('message.error', 'Error'));
            const errP = escapeHtml(t('capability.view.noIdInUrl', 'No capability ID found in URL'));
            document.getElementById('capabilityViewContainer').innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);"><h3>' + errH + '</h3><p>' + errP + '</p></div>';
            return;
        }

        // Initialize UI components
        initTabs();
        initEditButton();
        initBackButton();

        // Hide edit controls if user is not admin
        await hideEditsIfUnauthenticated();

        // Load the capability data
        await load(id);
        
        // Check and display lock status
        if (window.ViewLockHelper) {
            await window.ViewLockHelper.checkAndDisplayLockStatus('capability', id);
        }

        // Initialize unified edit dropdown
        if (window.EditDropdown && id != null) {
            try {
                window.EditDropdown.initialize('capability', id, {
                    container: '.tab-actions',
                    editUrl: `/view/capability/capability-edit.html?id=${id}`
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }

        // Initialize follow button in header actions
        if (window.addFollowButton) {
            try {
                await window.addFollowButton('capability', id, null, '.form-actions');
            } catch (e) {
                console.error('Failed to initialize follow button for capability:', e);
            }
        }

        // Load initial tab content (default to summary tab)
        setTimeout(() => {
            const initialTab = document.querySelector('.tab.active');
            if (initialTab) {
                const tabName = initialTab.getAttribute('data-tab');
                loadTabContent(tabName);
            }
        }, 100);
    }

    // Tab switching functionality
    function initTabs() {
        const tabs = document.querySelectorAll('.tab');
        // Only select capability-specific containers
        const capabilityContainers = [
            'capabilityViewContainer',
            'capabilityRelationshipsContainer',
            'capabilityStakeholdersContainer',
            'capabilityImpactContainer',
            'capabilityHistoryContainer',
            'capabilityChangeContainer'
        ];

        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                console.log('🔵 Tab clicked:', tab.getAttribute('data-tab'));
                
                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                // Add active class to clicked tab
                tab.classList.add('active');

                // Get the target container first
                const tabName = tab.getAttribute('data-tab');
                // Map tab names to their actual container IDs
                const tabContainerMap = {
                    'summary': 'capabilityViewContainer',
                    'relationships': 'capabilityRelationshipsContainer',
                    'stakeholders': 'capabilityStakeholdersContainer',
                    'impact': 'capabilityImpactContainer',
                    'history': 'capabilityHistoryContainer',
                    'change': 'capabilityChangeContainer'
                };
                
                const targetContainerId = tabContainerMap[tabName] || `capability${tabName.charAt(0).toUpperCase() + tabName.slice(1)}Container`;
                const targetContainer = document.getElementById(targetContainerId);
                
                console.log('🎯 Target container ID:', targetContainerId);
                console.log('🎯 Target container element:', targetContainer);

                // Hide all capability containers EXCEPT the target one
                capabilityContainers.forEach(containerId => {
                    const container = document.getElementById(containerId);
                    if (container && containerId !== targetContainerId) {
                        console.log('🚫 Hiding container:', containerId);
                        container.style.display = 'none';
                    } else if (container) {
                        console.log('✅ Keeping container visible:', containerId);
                    }
                });

                // Show the target container
                if (targetContainer) {
                    console.log('📦 Setting target container display to grid');
                    targetContainer.style.display = 'grid';
                    console.log('📦 Target container display after setting:', targetContainer.style.display);
                } else {
                    console.error('❌ Target container not found!');
                }

                // Load tab-specific content
                console.log('📄 Loading tab content for:', tabName);
                loadTabContent(tabName);
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('capabilityStakeholdersContainer');
                }
            });
        });
    }

    // Load content for specific tab
    function loadTabContent(tabName) {
        const id = parseId();
        if (!id) return;

        switch(tabName) {
            case 'stakeholders':
                // Load stakeholders in view mode
                if (window.capabilityStakeholderView) {
                    window.capabilityStakeholderView.init(id);
                }
                break;
            case 'relationships':
                // Load relationships tab
                loadCapabilityRelationships(id);
                break;
            case 'history':
                // Initialize history component for capability
                const historyContainer = document.getElementById('capabilityHistoryContainer');
                if (historyContainer) {
                    historyContainer.style.display = 'block';
                    historyContainer.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1;">
                            <div id="capabilityHistoryComponentContainer"></div>
                        </div>
                    `;
                    // Initialize history component for capability
                    if (window.HistoryComponent && id) {
                        console.log('Initializing history component for Capability with ID:', id);
                        window.HistoryComponent.initialize('Capability', id, 'capabilityHistoryComponentContainer');
                    } else {
                        console.error('HistoryComponent not available or ID not found:', { HistoryComponent: !!window.HistoryComponent, id });
                    }
                }
                break;
            case 'impact':
                // Load impact view
                if (window.loadCapabilityImpact) {
                    window.loadCapabilityImpact(id);
                }
                break;
            case 'change':
                // Initialize change tab component
                const changeContainer = document.getElementById('capabilityChangeContainer');
                if (changeContainer) {
                    changeContainer.style.display = 'grid';
                    if (window.ChangeTabComponent) {
                        window.ChangeTabComponent.initialize('capability', id, 'capabilityChangeContainer');
                    } else {
                        changeContainer.innerHTML = '<div class="empty-state"><i class="fas fa-exclamation-triangle"></i><p>Change component not available</p></div>';
                    }
                }
                break;
            case 'summary':
                console.log('📋 Summary tab - checking content...');
                const summaryContainer = document.getElementById('capabilityViewContainer');
                console.log('📋 Summary container:', summaryContainer);
                console.log('📋 Summary container display:', summaryContainer ? summaryContainer.style.display : 'N/A');
                console.log('📋 Summary container children:', summaryContainer ? summaryContainer.children.length : 'N/A');
                
                // Summary content is already loaded in init(), no need to reload
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
                    const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';

                    // Navigate to edit page with tab parameter
                    window.location.href = `/view/capability/capability-edit.html?id=${id}&tab=${activeTabName}`;
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

    // Capability relationships functionality
    async function loadCapabilityRelationships(id) {
        const container = document.getElementById('capabilityRelationshipsContainer');
        if (!container) return;

        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="relationships-container">
                    <div class="relationships-sub-tabs">
                        <button class="sub-tab active" data-sub-tab="hierarchy">Hierarchy</button>
                        <button class="sub-tab" data-sub-tab="relationships">Relationships</button>
                        <button class="sub-tab" data-sub-tab="map">Map</button>
                    </div>
                    <div class="relationships-content">
                        <div id="capabilityHierarchyContent" class="sub-tab-content active">
                            <div class="relationships-hierarchy">
                                <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>
                            </div>
                        </div>
                        <div id="capabilityRelationshipsContent" class="sub-tab-content">
                            <div class="relationships-hierarchy">
                                <div class="hierarchy-header">
                                    <div class="hierarchy-title">RELATIONSHIPS</div>
                                    <div class="hierarchy-actions">
                                        <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i><i class="fas fa-chevron-down" style="margin-left:.3rem;font-size:.7rem;"></i></button>
                                    </div>
                                </div>
                                <div class="data-table-wrapper">
                                    <table class="data-table">
                                        <thead>
                                            <tr>
                                                <th style="text-align: center;"><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                                <th style="text-align: center;"><div class="th-content"><span>Target Capability</span><i class="fas fa-sort"></i></div></th>
                                            </tr>
                                        </thead>
                                        <tbody id="capabilityRelationshipsTableBody">
                                            <tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="hierarchy-footer">
                                    0 records
                                </div>
                            </div>
                        </div>
                        <div id="capabilityMapContent" class="sub-tab-content">
                            ${typeof window.getCapabilityRelationshipsMapSubtabHtml === 'function' ? window.getCapabilityRelationshipsMapSubtabHtml() : ''}
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Initialize sub-tab switching
        initCapabilitySubTabs();

        // Load hierarchy data
        await loadCapabilityHierarchy(id);

        // Load relationships data
        await loadCapabilityRelationshipsData(id);
    }

    // Initialize sub-tab switching for relationships tab
    function initCapabilitySubTabs() {
        const subTabs = document.querySelectorAll('.relationships-sub-tabs .sub-tab');
        const subTabContents = document.querySelectorAll('.relationships-content .sub-tab-content');

        subTabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const targetSubTab = tab.getAttribute('data-sub-tab');
                const id = parseId();

                // Remove active class from all tabs and contents
                subTabs.forEach(t => t.classList.remove('active'));
                subTabContents.forEach(c => c.classList.remove('active'));

                // Add active class to clicked tab
                tab.classList.add('active');

                // Show corresponding content
                const targetContent = document.getElementById(`capability${targetSubTab.charAt(0).toUpperCase() + targetSubTab.slice(1)}Content`);
                if (targetContent) {
                    targetContent.classList.add('active');
                }

                // Initialize map if Map sub-tab is clicked
                if (targetSubTab === 'map' && id && window.CapabilityRelationshipsMap) {
                    const canvas = document.getElementById('capabilityRelationshipsMapCanvas');
                    if (canvas) {
                        // Small delay to ensure DOM is ready
                        setTimeout(() => {
                            window.CapabilityRelationshipsMap.init(id, '#capabilityRelationshipsMapCanvas');
                            // Setup map controls after map is initialized
                            window.CapabilityRelationshipsMap && window.CapabilityRelationshipsMap.initToolbar && window.CapabilityRelationshipsMap.initToolbar();
                        }, 100);
                    }
                }
            });
        });
    }

    // Setup capability map controls
    // Load capability relationships data
    async function loadCapabilityRelationshipsData(id) {
        const tbody = document.getElementById('capabilityRelationshipsTableBody');
        const footer = document.querySelector('#capabilityRelationshipsContent .hierarchy-footer');
        if (!tbody) return;

        try {
            tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';

            const relationships = await window.BUDG_API_SERVICE.getCapabilityRelationshipsBySourceId(id);
            const relationshipsList = relationships?.data || relationships || [];

            if (relationshipsList.length === 0) {
                tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No relationships found</td></tr>';
                if (footer) footer.textContent = '0 records';
                return;
            }

            const rowsHtml = relationshipsList.map(rel => {
                const relationshipType = escapeHtml(rel.relationshipType || rel.Relationship_Type || rel.relationship_type || '');
                const targetCapability = rel.targetCapability || rel.Target_Capability || rel.target_capability || '';
                const targetCapabilityId = rel.targetCapabilityId || rel.Target_Capability_ID || rel.target_capability_id;
                const targetCapabilityName = escapeHtml(rel.targetCapabilityName || rel.Target_Capability_Name || rel.target_capability_name || targetCapability || '');

                const targetLink = targetCapabilityId 
                    ? `<a href="/view/capability/${targetCapabilityId}" class="capability-link">${targetCapabilityName}</a>`
                    : targetCapabilityName || '<span class="empty">-</span>';

                return `
                    <tr>
                        <td>${relationshipType || '<span class="empty">-</span>'}</td>
                        <td>${targetLink}</td>
                    </tr>
                `;
            }).join('');

            tbody.innerHTML = rowsHtml;
            if (footer) footer.textContent = `${relationshipsList.length} record${relationshipsList.length !== 1 ? 's' : ''}`;
        } catch (error) {
            console.error('Failed to load capability relationships:', error);
            tbody.innerHTML = `<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ${error.message || 'Unknown error'}</td></tr>`;
            if (footer) footer.textContent = '0 records';
        }
    }

    // Start when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Capability hierarchy functions - matching Regulatory Theme pattern
    function buildDirectLineageTree(capabilities, currentCapabilityId) {
        console.log('buildDirectLineageTree called with capabilities:', capabilities.length, 'currentCapabilityId:', currentCapabilityId);
        
        const byId = new Map();
        capabilities.forEach(c => {
            const id = parseInt(c.id);
            byId.set(id, c);
            console.log('Added to byId map:', id, '->', c.primaryName, '(parent:', c.parentId, ')');
        });
        
        const currentId = parseInt(currentCapabilityId);
        const currentCapability = byId.get(currentId);
        console.log('Current capability ID (converted):', currentId);
        
        // Find all ancestors
        const ancestors = new Set();
        let current = currentCapability;
        while (current) {
            const parentId = current.parentId;
            const parentIdNum = parseInt(parentId);
            if (parentId && parentId !== 0 && !isNaN(parentIdNum) && byId.has(parentIdNum)) {
                ancestors.add(parentIdNum);
                current = byId.get(parentIdNum);
            } else {
                break;
            }
        }
        
        // Find all descendants
        const descendants = new Set();
        function findDescendants(id) {
            const children = capabilities.filter(c => parseInt(c.parentId) === parseInt(id));
            children.forEach(child => {
                const childId = parseInt(child.id);
                descendants.add(childId);
                findDescendants(childId);
            });
        }
        findDescendants(currentId);
        
        // Find siblings
        if (currentCapability && currentCapability.parentId) {
            const parentId = parseInt(currentCapability.parentId);
            const siblings = capabilities.filter(c => {
                const cParentId = parseInt(c.parentId);
                const cId = parseInt(c.id);
                return cParentId === parentId && cId !== currentId;
            });
            siblings.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                descendants.add(siblingId);
                findDescendants(siblingId);
            });
        }
        
        const includedIds = new Set([currentId, ...ancestors, ...descendants]);
        return capabilities.filter(c => includedIds.has(parseInt(c.id)));
    }

    function buildHierarchyTree(capabilities, rootId) {
        const byParent = new Map();
        const byId = new Map();
        capabilities.forEach(c => {
            const id = parseInt(c.id);
            const parentId = c.parentId ? parseInt(c.parentId) : null;
            byId.set(id, c);
            if (!byParent.has(parentId)) {
                byParent.set(parentId, []);
            }
            byParent.get(parentId).push(c);
        });
        
        const rows = [];
        function buildRows(capabilityId, depth = 0) {
            const currentCapability = byId.get(capabilityId);
            if (currentCapability) {
                const childCount = (byParent.get(capabilityId) || []).length;
                rows.push({
                    node: currentCapability,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                const children = byParent.get(capabilityId) || [];
                children.forEach(c => {
                    buildRows(parseInt(c.id), depth + 1);
                });
            } else {
                const children = byParent.get(capabilityId) || [];
                children.forEach(c => {
                    buildRows(parseInt(c.id), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    function renderCapabilityTable(hierarchyRows, currentId) {
        const Mask = window.HierarchyMask;
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const isMaskedNode = Mask ? Mask.isMasked(node) : false;
            const fallbackName = node.primaryName || 'Unnamed Capability';
            const fallbackDesc = node.description || '';
            const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
            const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
            const isCurrent = String(node.id) === String(currentId);
            const id = node.id;
            const parentId = node.parentId || '';

            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'capability-link current-capability-link' : 'capability-link';
            const link = isMaskedNode
                ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                : `<a class="${linkClass}" href="/view/capability/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            const refNumber = (!isMaskedNode && node.refNumber)
                ? `<span class="capability-ref">(${escapeHtml(node.refNumber)})</span>` : '';
            const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

            return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-cogs item-icon"></i><span class="capability-name">${link}</span>${refNumber}${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    function initCapabilityInteractions(containerEl, hierarchyRows) {
        const collapsed = new Set();
        const parentMap = hierarchyRows.parentMap;
        const tbody = containerEl ? containerEl.querySelector('tbody') : document.getElementById('capabilityHierarchyTbody');
        if (!tbody) return;
        
        // Build a map of parent ID to child rows for faster lookup
        const childrenByParent = new Map();
        tbody.querySelectorAll('tr').forEach(tr => {
            const parentId = tr.getAttribute('data-parent-id');
            if (parentId) {
                if (!childrenByParent.has(parentId)) {
                    childrenByParent.set(parentId, []);
                }
                childrenByParent.get(parentId).push(tr);
            }
        });
        
        function toggleChildren(parentId, isCollapsed) {
            const children = childrenByParent.get(String(parentId)) || [];
            children.forEach(childTr => {
                const childId = childTr.getAttribute('data-id');
                const childParentId = childTr.getAttribute('data-parent-id');
                
                if (isCollapsed) {
                    // Hide this child and all its descendants
                    childTr.style.display = 'none';
                    childTr.classList.add('collapsed');
                    // Recursively hide all descendants
                    toggleChildren(childId, true);
                } else {
                    // Show this child
                    childTr.style.display = '';
                    childTr.classList.remove('collapsed');
                    // Recursively show children if parent is not collapsed
                    if (!collapsed.has(String(childId))) {
                        toggleChildren(childId, false);
                    }
                }
            });
        }
        
        function updateVisibility() {
            // Update all rows based on collapsed state
            tbody.querySelectorAll('tr').forEach(tr => {
                const id = tr.getAttribute('data-id');
                const parentId = tr.getAttribute('data-parent-id');
                
                // Check if any ancestor is collapsed
                let shouldHide = false;
                let currentParentId = parentId;
                const visited = new Set();
                
                while (currentParentId && !shouldHide && !visited.has(currentParentId)) {
                    visited.add(currentParentId);
                    if (collapsed.has(String(currentParentId))) {
                        shouldHide = true;
                        break;
                    }
                    // Find the parent row to get its parent ID
                    const parentRow = Array.from(tbody.querySelectorAll('tr')).find(r => 
                        r.getAttribute('data-id') === currentParentId
                    );
                    if (parentRow) {
                        currentParentId = parentRow.getAttribute('data-parent-id');
                    } else {
                        break;
                    }
                }
                
                if (shouldHide) {
                    tr.style.display = 'none';
                    tr.classList.add('collapsed');
                } else {
                    tr.style.display = '';
                    tr.classList.remove('collapsed');
                }
            });
            
            // Update expander button states
            tbody.querySelectorAll('tr').forEach(tr => {
                const id = tr.getAttribute('data-id');
                const expander = tr.querySelector('.tree-expander');
                if (expander) {
                    const icon = expander.querySelector('i');
                    if (collapsed.has(String(id))) {
                        if (icon) {
                            icon.classList.remove('fa-caret-down');
                            icon.classList.add('fa-caret-right');
                        }
                    } else {
                        if (icon) {
                            icon.classList.remove('fa-caret-right');
                            icon.classList.add('fa-caret-down');
                        }
                    }
                }
            });
        }
        
        // Handle expander button clicks
        containerEl.addEventListener('click', function(e) {
            if (e.target.closest('.tree-expander')) {
                e.preventDefault();
                e.stopPropagation();
                
                const button = e.target.closest('.tree-expander');
                const row = button.closest('tr');
                const id = row.getAttribute('data-id');
                
                // Toggle collapsed state
                if (collapsed.has(String(id))) {
                    collapsed.delete(String(id));
                } else {
                    collapsed.add(String(id));
                }
                
                // Update visibility
                toggleChildren(id, collapsed.has(String(id)));
                updateVisibility();
            }
        });
        
        // Initial visibility update
        updateVisibility();
    }

    // Function to load capability hierarchy data
    async function loadCapabilityHierarchy(capabilityId) {
        // Try to find the hierarchy content container first
        const hierarchyContent = document.getElementById('capabilityHierarchyContent');
        let tbody, footer;
        
        if (hierarchyContent) {
            // Use the hierarchy content container
            const hierarchyContainer = hierarchyContent.querySelector('.relationships-hierarchy');
            if (hierarchyContainer) {
                // Create table structure if it doesn't exist
                if (!hierarchyContainer.querySelector('table')) {
                    hierarchyContainer.innerHTML = `
                        <div class="hierarchy-header">
                            <div class="hierarchy-title">CAPABILITY HIERARCHY</div>
                            <div class="hierarchy-actions">
                                <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                            </div>
                        </div>
                        <div class="hierarchy-table-wrapper">
                            <table class="hierarchy-table">
                                <thead>
                                    <tr>
                                        <th>Capability</th>
                                        <th>Description</th>
                                    </tr>
                                </thead>
                                <tbody id="capabilityHierarchyTbody">
                                    <tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>
                                </tbody>
                            </table>
                        </div>
                        <div class="hierarchy-footer" id="capabilityHierarchyFooter">
                            0 records
                        </div>
                    `;
                }
                tbody = document.getElementById('capabilityHierarchyTbody');
                footer = document.getElementById('capabilityHierarchyFooter');
            }
        }
        
        // Fallback to old selectors
        if (!tbody) tbody = document.getElementById('relationshipsTbody');
        if (!footer) footer = document.getElementById('hierarchyFooter');
        
        if (!tbody || !footer) return;
        
        try {
            tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading capability hierarchy...</td></tr>';
            footer.textContent = 'Loading...';
            
            // Fetch hierarchy data from API (includes ancestors, current, siblings, children, and siblings' children)
            console.log('[Capability Hierarchy] Fetching hierarchy for capability ID:', capabilityId);
            const hierarchyData = await window.BUDG_API_SERVICE.getCapabilityHierarchy(capabilityId);
            console.log('[Capability Hierarchy] Raw API response:', hierarchyData);
            
            // Handle different response formats
            let items = [];
            if (Array.isArray(hierarchyData)) {
                items = hierarchyData;
            } else if (hierarchyData && hierarchyData.data && Array.isArray(hierarchyData.data)) {
                items = hierarchyData.data;
            } else if (hierarchyData && Array.isArray(hierarchyData.items)) {
                items = hierarchyData.items;
            } else if (hierarchyData && typeof hierarchyData === 'object') {
                // Try to extract array from object
                const keys = Object.keys(hierarchyData);
                for (const key of keys) {
                    if (Array.isArray(hierarchyData[key])) {
                        items = hierarchyData[key];
                        break;
                    }
                }
            }
            
            console.log('[Capability Hierarchy] Processed items:', items);
            console.log('[Capability Hierarchy] Items count:', items.length);
            
            if (items.length === 0) {
                console.warn('[Capability Hierarchy] No hierarchy data found for capability ID:', capabilityId);
                tbody.innerHTML = '<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data available</td></tr>';
                footer.textContent = '0 records';
                return;
            }

            console.log('Capability hierarchy data from API:', items);
            console.log('Current capability ID:', capabilityId);
            
            // Build hierarchy tree structure from flat data with levels - matching glossary logic
            const byId = new Map();
            const childrenMap = new Map();
            
            items.forEach(item => {
                const id = item.id;
                const parentId = item.parentId;
                byId.set(id, item);
                if (!childrenMap.has(parentId)) childrenMap.set(parentId, []);
                childrenMap.get(parentId).push(item);
            });
            
            // Find current capability to determine its parent and calculate base depth
            const currentCapability = items.find(item => item.relation === 'current');
            const currentParentId = currentCapability ? currentCapability.parentId : null;
            
            // Find the maximum ancestor level (most negative) to calculate base depth
            const ancestors = items.filter(item => item.relation === 'ancestor');
            const maxAncestorLevel = ancestors.length > 0 ? Math.min(...ancestors.map(a => a.level)) : 0;
            const ancestorDepthOffset = Math.abs(maxAncestorLevel); // How many ancestor levels we have
            
            // Calculate current depth: if has ancestors, depth = ancestorDepthOffset, else 0
            const currentDepth = currentParentId ? ancestorDepthOffset : 0;
            
            // Build tree structure: ancestors -> siblings -> current -> descendants -> siblings' children
            const hierarchyRows = [];
            
            // Helper to calculate depth based on level and relation
            function calculateDepth(item) {
                if (item.relation === 'ancestor') {
                    return ancestorDepthOffset + item.level;
                } else if (item.relation === 'current') {
                    return currentDepth;
                } else if (item.relation === 'sibling') {
                    return currentDepth;
                } else if (item.relation === 'descendant') {
                    return currentDepth + item.level;
                } else if (item.relation === 'sibling_child') {
                    return currentDepth + item.level;
                }
                return 0;
            }
            
            // Build proper hierarchical tree structure using parent-child relationships
            function buildHierarchicalOrder(items, currentCapabilityId) {
                const result = [];
                const processed = new Set();
                
                function addNodeAndChildren(nodeId, depth) {
                    if (processed.has(nodeId)) return;
                    processed.add(nodeId);
                    
                    const node = byId.get(nodeId);
                    if (!node) return;
                    
                    result.push({ node, depth, parentId: node.parentId });
                    
                    const children = (childrenMap.get(nodeId) || []).sort((a, b) => {
                        return (a.name || '').localeCompare(b.name || '');
                    });
                    
                    children.forEach(child => {
                        addNodeAndChildren(child.id, depth + 1);
                    });
                }
                
                const ancestors = items.filter(item => item.relation === 'ancestor')
                    .sort((a, b) => a.level - b.level);
                ancestors.forEach(ancestor => {
                    if (!processed.has(ancestor.id)) {
                        addNodeAndChildren(ancestor.id, calculateDepth(ancestor));
                    }
                });
                
                const siblings = items.filter(item => item.relation === 'sibling')
                    .sort((a, b) => (a.name || '').localeCompare(b.name || ''));
                siblings.forEach(sibling => {
                    if (!processed.has(sibling.id)) {
                        addNodeAndChildren(sibling.id, calculateDepth(sibling));
                    }
                });
                
                if (currentCapability) {
                    addNodeAndChildren(currentCapability.id, calculateDepth(currentCapability));
                }
                
                const currentId = currentCapability ? currentCapability.id : currentCapabilityId;
                const descendants = items.filter(item => item.relation === 'descendant')
                    .filter(item => item.parentId === currentId);
                descendants.forEach(descendant => {
                    if (!processed.has(descendant.id)) {
                        addNodeAndChildren(descendant.id, calculateDepth(descendant));
                    }
                });
                
                const siblingChildren = items.filter(item => item.relation === 'sibling_child');
                const siblingIds = siblings.map(s => s.id);
                siblingChildren
                    .filter(item => siblingIds.includes(item.parentId))
                    .forEach(child => {
                        if (!processed.has(child.id)) {
                            addNodeAndChildren(child.id, calculateDepth(child));
                        }
                    });
                
                return result;
            }
            
            const hierarchyRowsData = buildHierarchicalOrder(items, capabilityId);
            
            hierarchyRowsData.forEach(({ node, depth, parentId }) => {
                const children = childrenMap.get(node.id) || [];
                const hasChildren = children.length > 0;
                
                hierarchyRows.push({
                    node: node,
                    depth: depth,
                    parentId: parentId,
                    hasChildren: hasChildren,
                    relation: node.relation
                });
            });
            
            console.log('Processed capability hierarchy rows:', hierarchyRows);
            
            // Render the hierarchy
            const rowsHtml = hierarchyRows.map(({ node, depth, hasChildren, relation }) => {
                const name = node.name || '';
                const desc = node.description || '';
                const isCurrent = relation === 'current';
                const id = node.id;
                const parentId = node.parentId || null;
                
                const visualDepth = depth;
                const indent = Array(visualDepth).fill('<span class="tree-indent"></span>').join('');
                const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
                const childCount = childrenMap.get(id)?.length || 0;
                const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
                const linkClass = isCurrent ? 'capability-link current-capability-link' : 'capability-link';
                const link = `<a class="${linkClass}" href="/view/capability/${encodeURIComponent(id)}">${escapeHtml(name)}</a>`;
                
                return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId || ''}" data-depth="${visualDepth}" data-relation="${relation}">
                    <td><div class="tree-cell">${indent}${expander}${visualDepth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-cogs item-icon"></i><span class="capability-name">${link}</span>${countBadge}</div></td>
                    <td><span title="${escapeHtml(desc)}">${escapeHtml(desc || '-')}</span></td>
                </tr>`;
            }).join('');

            tbody.innerHTML = rowsHtml;
            const n = hierarchyRows.length;
            footer.textContent = n === 1 ? t('message.record', '1 record') : t('message.records', '{count} records', { count: n });
            
            // Build parent map for interactions
            const parentMap = new Map();
            items.forEach(item => {
                if (item.parentId) {
                    if (!parentMap.has(item.parentId)) {
                        parentMap.set(item.parentId, []);
                    }
                    parentMap.get(item.parentId).push(item.id);
                }
            });
            
            const hierarchyRowsObj = {
                rows: hierarchyRows,
                parentMap: parentMap
            };
            
            // Initialize interactions
            let container = tbody.closest('.relationships-hierarchy');
            if (!container && hierarchyContent) {
                container = hierarchyContent.querySelector('.relationships-hierarchy');
            }
            if (container) {
                initCapabilityInteractions(container, hierarchyRowsObj);
            }
        } catch (e) {
            console.error('[Capability Hierarchy] Failed to load hierarchy:', e);
            console.error('[Capability Hierarchy] Error stack:', e.stack);
            const hierFail = t('message.failedToLoad', 'Failed to load');
            const unk = t('value.unknownError', 'Unknown error');
            const detail = escapeHtml(e.message || unk);
            tbody.innerHTML = `<tr><td colspan="2" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">${escapeHtml(hierFail)}: ${detail}</td></tr>`;
            footer.textContent = t('message.zeroRecords', '0 records');
        }
    }

    // Make functions globally available
    window.viewCapability = function(capabilityId) {
        window.location.href = `/view/capability/${capabilityId}`;
    };

})();


