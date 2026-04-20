// Business Area View Page JavaScript

// Parse ID from URL (supports both path and query parameters)
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
    
    // Fallback to path-based ID (e.g., /business-area/1)
    const path = window.location.pathname;
    const match = path.match(/\/business-area\/(\d+)$/);
    console.log('Path match:', match);
    
    if (match) {
        const id = parseInt(match[1], 10);
        console.log('Parsed path ID:', id, 'Type:', typeof id);
        if (!Number.isNaN(id)) {
            console.log('Using path ID:', id);
            return id;
        }
    }
    
    console.log('No valid ID found');
    return null;
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
        return escapeHtml(d.toLocaleString());
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
        return '<span class="status-badge status-active">Public</span>';
    } else {
        return '<span class="status-badge status-inactive">Private</span>';
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
async function resolveReferences(businessArea) {
    const api = window.BUDG_API_SERVICE;
    const tasks = [];

    // Status name from Status ID
    if (!businessArea.statusName && (businessArea.Status || businessArea.status)) {
        const statusId = businessArea.Status || businessArea.status;
        tasks.push((async () => {
            try {
                let status = await api.getStatusById(statusId);
                if (status && status.data) status = status.data;
                businessArea.statusName = status?.primaryname || status?.Name || status?.name || String(statusId);
                console.log('Resolved status name:', businessArea.statusName);
            } catch(e) { 
                console.error('Failed to resolve status name:', e);
                businessArea.statusName = String(statusId);
            }
        })());
    }

            // Lifecycle name from Lifecycle ID - Use static mapping from schema first
            if (!businessArea.lifecycleName && (businessArea.Lifecycle || businessArea.lifecycle)) {
                const lifecycleId = businessArea.Lifecycle || businessArea.lifecycle;
                console.log('Resolving business area lifecycle ID:', lifecycleId);
                tasks.push((async () => {
                    try {
                        // Try to get real data from connected database via API
                        let lifecycle;
                        try {
                            // Use API service method directly
                            lifecycle = await api.getBusinessAreaLifecycleById(lifecycleId);
                            console.log('Business Area Lifecycle API response:', lifecycle);
                        } catch(apiError) {
                            console.log('API call failed:', apiError.message);
                            lifecycle = null;
                        }
                        
                        if (lifecycle && typeof lifecycle === 'object' && !lifecycle.error) {
                            businessArea.lifecycleName = lifecycle?.name || lifecycle?.Name || lifecycle?.primaryName || lifecycle?.PrimaryName;
                            console.log('✅ Using real database data for business area lifecycle:', businessArea.lifecycleName);
                        } else {
                            businessArea.lifecycleName = `Business Area Lifecycle ${lifecycleId}`;
                            console.log('⚠️ Using fallback for business area lifecycle:', businessArea.lifecycleName);
                        }
                        console.log('Final resolved business area lifecycle name:', businessArea.lifecycleName);
                    } catch(e) { 
                        console.error('Failed to resolve business area lifecycle name:', e);
                        businessArea.lifecycleName = String(lifecycleId);
                    }
                })());
    }

    // BUDG Viewing from Is_Public field
    if (!businessArea.viewingName && (businessArea.Is_Public || businessArea.isPublic)) {
        const viewingId = businessArea.Is_Public || businessArea.isPublic;
        tasks.push((async () => {
            try {
                let viewing = await api.getViewingById(viewingId);
                if (viewing && viewing.data) viewing = viewing.data;
                businessArea.viewingName = viewing?.Name || viewing?.name || String(viewingId);
                console.log('Resolved BUDG Viewing name:', businessArea.viewingName);
            } catch(e) {
                console.error('Failed to resolve BUDG Viewing name:', e);
                businessArea.viewingName = String(viewingId);
            }
        })());
    }

    // Created by person name from Create_UserID
    const createdById = businessArea.Create_UserID || businessArea.CreateUserID || businessArea.create_user_id || businessArea.createdby_id;
    if (!businessArea.createdByName && createdById) {
        const userId = createdById;
        console.log('Resolving Created By ID:', userId);
        tasks.push((async () => {
            try {
                let person = await api.getPersonById(userId);
                console.log('Person API response:', person);
                if (person && person.data) person = person.data;
                const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                    .filter(Boolean).join(' ').trim();
                businessArea.createdByName = fullName || person?.Email || person?.email || String(userId);
                console.log('Resolved created by name:', businessArea.createdByName);
            } catch(e) {
                console.error('Failed to resolve created by name:', e);
                businessArea.createdByName = String(userId);
            }
        })());
    } else {
        console.log('Created By not resolved - createdByName:', businessArea.createdByName, 'createdById:', createdById);
    }

    // Last Update User name
    if (!businessArea.lastUpdateUserName && businessArea.LastUpdate_UserID) {
        const userId = businessArea.LastUpdate_UserID;
        tasks.push((async () => {
            try {
                let person = await api.getPersonById(userId);
                if (person && person.data) person = person.data;
                const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                    .filter(Boolean).join(' ').trim();
                businessArea.lastUpdateUserName = fullName || person?.Email || person?.email || String(userId);
                console.log('Resolved last update user name:', businessArea.lastUpdateUserName);
            } catch(e) {
                console.error('Failed to resolve last update user name:', e);
                businessArea.lastUpdateUserName = String(userId);
            }
        })());
    }

    await Promise.all(tasks);

    // Leave missing segment metadata visible so failed saves do not look like Enterprise.
    if (!businessArea.segmentName && !businessArea.segment_name && !businessArea.segment) {
        businessArea.segmentName = 'Not Assigned';
        businessArea.segmentId = businessArea.segmentId ?? businessArea.segment_id ?? businessArea.Segment_ID ?? null;
    }

    return businessArea;
}

let _businessAreaGuestCache = null;
async function isGuestVisitorForBusinessArea() {
    if (_businessAreaGuestCache !== null) {
        return _businessAreaGuestCache;
    }
    try {
        const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
        if (!resp.ok) {
            _businessAreaGuestCache = true;
            return _businessAreaGuestCache;
        }
        const me = await resp.json().catch(() => ({}));
        const role = (me.role || '').toString().toLowerCase();
        const isAuthenticated = me.authenticated === true;
        const isGuestRole = role.includes('guest');
        _businessAreaGuestCache = !isAuthenticated || isGuestRole;
        return _businessAreaGuestCache;
    } catch (_) {
        _businessAreaGuestCache = true;
        return _businessAreaGuestCache;
    }
}

function renderBusinessAreaGuestAccessDenied(container, businessAreaId, segmentName, segmentId) {
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


// Load and render business area data
async function load(id, targetTab = null) {
    const container = document.getElementById('businessAreaViewContainer');
    if (!container) {
        console.error('Business area view container not found');
        return;
    }

    // Only show loading in summary container if we're not targeting a specific tab
    if (!targetTab || targetTab === 'summary') {
    container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading...</div>';
    }

    try {
        console.log('=== CALLING API ===');
        console.log('API Service:', window.BUDG_API_SERVICE);
        console.log('Calling getBusinessAreaById with ID:', id);
        
        const rawData = await window.BUDG_API_SERVICE.getBusinessAreaById(id);
        console.log('=== RAW BUSINESS AREA DATA ===');
        console.log('Raw data:', rawData);
        console.log('Raw data type:', typeof rawData);
        console.log('Is array:', Array.isArray(rawData));
        
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

        // Resolve foreign key references
        console.log('=== RESOLVING BUSINESS AREA REFERENCES ===');
        const resolvedData = await resolveReferences(data);

        try {
            const isGuest = await isGuestVisitorForBusinessArea();
            const segmentName = resolvedData?.segmentName || resolvedData?.segment_name || resolvedData?.segment;
            const segmentId = resolvedData?.segmentId ?? resolvedData?.segment_id ?? resolvedData?.Segment_ID;
            const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[BusinessArea] Guest visitor blocked from non-Enterprise segment view', { businessAreaId: id, segmentName, segmentId });
                    renderBusinessAreaGuestAccessDenied(container, id, segmentName, segmentId);
                const titleElement = document.getElementById('businessAreaTitle');
                if (titleElement) {
                    titleElement.removeAttribute('data-i18n');
                    titleElement.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                }
                return;
            }
        } catch (gateError) {
            console.warn('[BusinessArea] Error during guest/segment access check, falling back to normal render:', gateError);
        }

        console.log('References resolved, final data:', resolvedData);
        console.log('=== FIELD VALUES DEBUG ===');
        console.log('Status Name:', resolvedData.statusName);
        console.log('Lifecycle Name:', resolvedData.lifecycleName);
        console.log('BUDG Viewing Name:', resolvedData.viewingName);
        console.log('BUDG Viewing ID:', resolvedData.Is_Public || resolvedData.isPublic);
        console.log('Created By Name:', resolvedData.createdByName);
        console.log('Created By ID:', resolvedData.Create_UserID || resolvedData.CreateUserID || resolvedData.create_user_id || resolvedData.createdby_id);
        console.log('Created Date:', resolvedData.CreateDatetime || resolvedData.createDatetime || resolvedData.created_date);
        console.log('Last Updated By Name:', resolvedData.lastUpdateUserName);
        console.log('Last Updated By ID:', resolvedData.LastUpdate_UserID);
        console.log('=== RAW DATA DEBUG ===');
        console.log('Raw business area data:', data);
        console.log('Available fields:', Object.keys(data));

        try { 
            window.BUDG_API_SERVICE.logVisit({ entity: 'BusinessArea', entityId: String(id), route: `/view/business-area/${id}` });
        } catch(_) {}

        // Update header with business area data (dynamic title policy)
        const titleElement = document.getElementById('businessAreaTitle');
        const breadcrumbElement = document.getElementById('businessAreaBreadcrumb');
        
        // Debug: log available fields to understand data structure
        if (data && typeof data === 'object') {
            console.log('Business Area data fields:', Object.keys(data));
            console.log('Available name fields:', {
                primaryname: data.primaryname,
                name: data.name,
                primaryName: data.primaryName,
                Name: data.Name,
                description: data.description
            });
            console.log('Created By fields check:');
            console.log('- Create_UserID:', data.Create_UserID);
            console.log('- CreateUserID:', data.CreateUserID);
            console.log('- create_user_id:', data.create_user_id);
            console.log('- createdby_id:', data.createdby_id);
            console.log('Created Date fields check:');
            console.log('- CreateDatetime:', data.CreateDatetime);
            console.log('- createDatetime:', data.createDatetime);
            console.log('- created_date:', data.created_date);
            console.log('Last Update fields:', {
                LastUpdateDatetime: data.LastUpdateDatetime,
                lastUpdateDatetime: data.lastUpdateDatetime,
                LastUpdate_UserID: data.LastUpdate_UserID,
                lastUpdateUserID: data.lastUpdateUserID
            });
        }
        
        if (titleElement) {
            const titleText = resolvedData.PrimaryName || resolvedData.primaryName || resolvedData.name || resolvedData.Name || resolvedData.description || 'Business Area';
            console.log('Setting title to:', titleText);
            titleElement.textContent = titleText;
        }
        const baT = (k) => (window.I18n && window.I18n.t(k)) || k;
        if (breadcrumbElement) {
            breadcrumbElement.textContent = baT('businessArea.breadcrumb');
        }

        // Render left column (DESCRIPTION and DEFINITION)
        const left = `
            <div class="view-section">
                <div class="section-title">
                    <i class="fas fa-circle-info" style="margin-right:.4rem;"></i>
                    ${baT('businessArea.sections.description')}
                </div>
                ${renderItem(baT('businessArea.labels.name'), escapeHtml(resolvedData.PrimaryName || resolvedData.primaryName || resolvedData.name))}
            </div>
            <div class="view-section">
                <div class="section-title">${baT('businessArea.sections.definition')}</div>
                ${renderItem(baT('businessArea.labels.businessArea'), _richHtml(resolvedData.Description || resolvedData.description || ''))}
            </div>
        `;

        // Check if record has been updated (Last Updated Date = Created Date if no changes happened)
        const createdDate = resolvedData.CreateDatetime || resolvedData.createDatetime || resolvedData.created_date;
        const lastUpdatedDate = resolvedData.LastUpdateDatetime || resolvedData.lastUpdateDatetime || resolvedData.last_update_date;
        const hasBeenUpdated = lastUpdatedDate && createdDate && 
            new Date(lastUpdatedDate).getTime() !== new Date(createdDate).getTime();
        
        const createdBy = renderUserLink(resolvedData.createdByName || 'System', resolvedData.Create_UserID || resolvedData.CreateUserID || resolvedData.create_user_id || resolvedData.createdby_id);
        const createdAt = formatDateTime(createdDate);
        
        const updatedBy = hasBeenUpdated 
            ? renderUserLink(resolvedData.lastUpdateUserName || resolvedData.last_update_user_name, resolvedData.LastUpdate_UserID || resolvedData.last_update_user_id)
            : createdBy;
        const updatedAt = hasBeenUpdated 
            ? formatDateTime(lastUpdatedDate)
            : createdAt;

        // Render right column (CLASSIFICATIONS)
        const right = `
            <div class="view-section">
                <div class="section-title">
                    <i class="fas fa-layer-group" style="margin-right:.4rem;"></i>
                    ${baT('businessArea.sections.classifications')}
                </div>
                <div class="section-subtitle">${baT('businessArea.sections.basicClassifications')}</div>
                ${renderItem(baT('businessArea.labels.budgStatus'), renderStatusBadge(resolvedData.statusName || resolvedData.Status || resolvedData.status || 'Unknown', (resolvedData.statusName || 'Unknown').toLowerCase().includes('active')))}
                ${renderItem(baT('businessArea.labels.lifecycle'), renderLifecycleBadge(resolvedData.lifecycleName || resolvedData.Lifecycle || resolvedData.lifecycle || 'Unknown'))}
                ${renderItem(baT('businessArea.labels.budgViewing'), renderPublicStatus(resolvedData.viewingName || resolvedData.Is_Public || resolvedData.isPublic))}
                ${renderItem(baT('businessArea.labels.createdBy'), createdBy)}
                ${renderItem(baT('businessArea.labels.created'), createdAt)}
                ${renderItem(baT('businessArea.labels.lastUpdatedBy'), updatedBy)}
                ${renderItem(baT('businessArea.labels.lastUpdated'), updatedAt)}
                ${renderItem(baT('businessArea.labels.segment'), escapeHtml(resolvedData.segmentName || resolvedData.segment_name || resolvedData.segment || ((resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID) != null ? `ID ${resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID}` : 'Not Assigned')))}

            </div>
        `;

        // Render the grid with container structure (matching Glossary pattern)
        container.innerHTML = `
            <div class="business-area-container">
                <div class="left-column">
                    ${left}
                </div>
                <div class="right-column">
                    ${right}
                </div>
            </div>
        `;
        
        // Render custom fields section
        if (window.CustomFields) {
            try {
                await window.CustomFields.renderViewSection({
                    facetId: 'Business Area',
                    containerId: 'businessAreaViewContainer',
                    objectId: id,
                    title: (window.I18n && window.I18n.t('businessArea.sections.customFields')) || 'CUSTOM FIELDS'
                });
            } catch (error) {
                console.error('Error rendering custom fields:', error);
            }
        }

        
        // If we have a target tab that's not summary, hide the summary container
        if (targetTab && targetTab !== 'summary') {
            container.style.display = 'none';
        }

    } catch (error) {
        console.error('Error loading business area:', error);
        const isForbidden = error?.status === 403 || String(error?.message || '').includes('403');
        const message = isForbidden
            ? 'This object is not available.'
            : `Failed to load business area: ${error.message}`;
        showError(message);
    }
    }

// Show error message
function showError(message) {
    const container = document.getElementById('businessAreaViewContainer');
    if (container) {
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">Error</div>
                <div class="empty">${escapeHtml(message)}</div>
            </div>
        `;
    }
}

// Hide edit controls
function hideEditControls() {
    const editBtns = ['tabEditBtn'];
    editBtns.forEach(btnId => {
        const el = document.getElementById(btnId);
        if (el) el.style.display = 'none';
    });
}

// Show edit controls
function showEditControls() {
    const editBtns = ['tabEditBtn'];
    editBtns.forEach(btnId => {
        const el = document.getElementById(btnId);
        if (el) el.style.display = 'inline-flex';
    });
}

// Check user permissions and hide/show edit controls
async function hideEditsIfUnauthenticated() {
    try {
        const id = parseId();
        if (!id) {
            console.log('[Business Area] No ID found - hiding edit controls');
            hideEditControls();
            return;
        }

        // Use the shared utility function to check if user can edit this object
        if (window.checkCanEditObject) {
            const editCheck = await window.checkCanEditObject('Business Area', id);
            const canEditObject = editCheck.canEdit;
            const isAdmin = editCheck.isAdmin;
            
            console.log('[Business Area] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
            
            // Show the main edit dropdown only if user can edit this object
            if (canEditObject) {
                console.log('[Business Area] User can edit this object - showing edit controls');
                showEditControls();
            } else {
                console.log('[Business Area] User cannot edit this object - hiding edit controls');
                hideEditControls();
            }
            
            // Check delete permission
            const permResp = await fetch('/api/user/permissions/Business Areas', { method: 'GET', credentials: 'include' });
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
        // Store user in sessionStorage for future use
        sessionStorage.setItem('currentUser', JSON.stringify(me));
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
        '#deleteBusinessAreaBtn'
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

// Initialize page
document.addEventListener('DOMContentLoaded', async function() {
    console.log('Business Area page DOM loaded');
    
    // Check user permissions
    hideEditsIfUnauthenticated();
    
    // Check if required services are available
    console.log('BUDG_CONFIG available:', !!window.BUDG_CONFIG);
    console.log('BUDG_API_SERVICE available:', !!window.BUDG_API_SERVICE);
    
    if (window.BUDG_API_SERVICE) {
        console.log('API service methods:', Object.getOwnPropertyNames(Object.getPrototypeOf(window.BUDG_API_SERVICE)));
    }
    
    try {
        console.log('Business Area page loaded');
        
        const backBtn = document.getElementById('backBtn');
        console.log('Back button found:', backBtn);
        
        if (backBtn) {
            backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
        }
        
        // Parse ID first before using it
        const id = parseId();
        console.log('Business Area ID parsed:', id);
        
        // Initialize unified edit dropdown
        if (window.EditDropdown && id != null) {
            try {
                // Check if user can edit this object (has permission AND is stakeholder)
                let canEditThisObject = false;
                try {
                    if (window.checkCanEditObject) {
                        const editCheck = await window.checkCanEditObject('Business Area', id);
                        canEditThisObject = editCheck.canEdit;
                        console.log('[Business Area] EditDropdown: User can edit object?', canEditThisObject, '(isStakeholder:', editCheck.isStakeholder, ')');
                    }
                } catch (permError) {
                    console.error('[Business Area] EditDropdown: Error checking permissions:', permError);
                    canEditThisObject = false; // Fail securely
                }
                
                window.EditDropdown.initialize('business-area', id, {
                    container: '.tab-actions',
                    editUrl: `/view/business-area/business-area-edit.html?id=${id}`,
                    hideEditOption: !canEditThisObject // Hide if user cannot edit
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }
        console.log('Business Area ID parsed:', id);
        
        if (id != null) {
            // Wait for i18n so summary and labels render in the correct locale
            if (window.i18nReadyPromise) await window.i18nReadyPromise;
            // Check for tab parameter in URL first
            const urlParams = new URLSearchParams(window.location.search);
            const tabParam = urlParams.get('tab');
            console.log('=== URL PARAMETERS DEBUG (VIEW) ===');
            console.log('URL:', window.location.href);
            console.log('Search params:', window.location.search);
            console.log('Tab parameter:', tabParam);
            
            // Load data but don't show summary tab if we have a specific tab parameter
            load(id, tabParam);
            
            if (tabParam) {
                console.log('Tab parameter found in URL:', tabParam);
                // Switch to the specified tab after a short delay to ensure everything is loaded
                setTimeout(() => {
                    console.log('Switching to tab after delay:', tabParam);
                    switchToTab(tabParam);
                }, 300);
            }
        } else {
            showError('No business area ID provided');
        }

        // Initialize tab data storage
        window.businessAreaTabData = {
            summary: null,
            relationships: null,
            stakeholders: null,
            impact: null,
            history: null,
            change: null
        };

        // Tabs: Summary, Relationships, Stakeholders, Impact, History, Change
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(function(btn){
            btn.addEventListener('click', function(){
                tabs.forEach(function(b){ b.classList.remove('active'); });
                this.classList.add('active');

                // Hide all containers
                const containers = [
                    'businessAreaViewContainer',
                    'businessAreaRelationshipsContainer', 
                    'businessAreaStakeholdersContainer',
                    'businessAreaImpactContainer',
                    'businessAreaHistoryContainer',
                    'businessAreaChangeContainer'
                ];
                containers.forEach(function(containerId) {
                    const container = document.getElementById(containerId);
                    if (container) container.style.display = 'none';
                });

                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                
                // Update URL with current tab
                updateURLWithTab(which);
                
                if (which === 'summary') {
                    const summaryContainer = document.getElementById('businessAreaViewContainer');
                    if (summaryContainer) {
                        summaryContainer.style.display = 'block';
                        // Always reload data when switching to summary tab
                        if (id != null) {
                            load(id).then(async function() {
                                window.businessAreaTabData.summary = summaryContainer.innerHTML;
                                
                                // Check and display lock status
                                if (window.ViewLockHelper) {
                                    await window.ViewLockHelper.checkAndDisplayLockStatus('business-area', id);
                                }
                            });
                        }
                    }
                } else if (which === 'relationships') {
                    const relationshipsContainer = document.getElementById('businessAreaRelationshipsContainer');
                    if (relationshipsContainer) {
                        relationshipsContainer.style.display = 'block';
                        // Always reload data when switching to relationships tab
                        if (id != null) {
                            const relT = (k) => (window.I18n && window.I18n.t(k)) || k;
                        relationshipsContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div class="relationships-container">
                                        <div class="relationships-hierarchy">
                                            <div class="hierarchy-header">
                                                <div class="hierarchy-title">${relT('businessArea.hierarchy.title')}</div>
                                                <div class="hierarchy-actions">
                                                    <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem;color:var(--text-muted,#6b7280);">
                                                        <input type="checkbox" checked style="margin-right:.3rem;">
                                                        ${relT('businessArea.hierarchy.showRelationships')}
                                                    </label>
                                                    <button type="button" class="btn btn-secondary" id="businessAreaEditBtn2"><i class="fas fa-cog"></i></button>
                                                </div>
                                            </div>
                                            <table class="hierarchy-table">
                                                <thead>
                                                    <tr>
                                                        <th><div class="th-content"><span>${relT('businessArea.hierarchy.businessArea')}</span><i class="fas fa-sort"></i></div></th>
                                                        <th><div class="th-content"><span>${relT('businessArea.hierarchy.description')}</span><i class="fas fa-sort"></i></div></th>
                                                    </tr>
                                                </thead>
                                                <tbody id="businessAreaHierarchyTbody">
                                                    <tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${relT('businessArea.hierarchy.loading')}</td></tr>
                                                </tbody>
                                            </table>
                                            <div class="hierarchy-footer" id="businessAreaHierarchyFooter">
                                                ${relT('businessArea.hierarchy.zeroRecords')}
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            `;

                            // Load hierarchy data immediately
                            loadBusinessAreaHierarchy(id);
                            
                            // Cache the content
                            window.businessAreaTabData.relationships = relationshipsContainer.innerHTML;
                        }
                    }
                } else if (which === 'stakeholders') {
                    const stakeholdersContainer = document.getElementById('businessAreaStakeholdersContainer');
                    if (stakeholdersContainer) {
                        stakeholdersContainer.style.display = 'block';
                        // Always reload data when switching to stakeholders tab
                        if (id != null) {
                            // Initialize stakeholder view if not already done
                            if (window.BusinessAreaStakeholderView) {
                                window.BusinessAreaStakeholderView.init(id);
                            } else {
                                stakeholdersContainer.innerHTML = `
                                    <div class="view-section" style="grid-column:1/-1;">
                                        <div class="section-title">${window.I18n?.t('businessArea.stakeholder.sections.stakeholders') || 'STAKEHOLDERS'}</div>
                                        <div class="empty">Loading stakeholders...</div>
                                    </div>
                                `;
                            }
                            window.businessAreaTabData.stakeholders = stakeholdersContainer.innerHTML;
                        }
                    }
                } else if (which === 'impact') {
                    const impactContainer = document.getElementById('businessAreaImpactContainer');
                    if (impactContainer) {
                        impactContainer.style.display = 'block';
                        // Always reload data when switching to impact tab
                        if (id != null) {
                            if (typeof window.loadBusinessAreaImpact === 'function') {
                                window.loadBusinessAreaImpact(id);
                            } else {
                                impactContainer.innerHTML = `
                                    <div class="view-section" style="grid-column:1/-1;">
                                        <div class="section-title">IMPACT ANALYSIS</div>
                                        <div class="empty">Loading impact data...</div>
                                    </div>
                                `;
                            }
                        }
                    }
                } else if (which === 'history') {
                    const historyContainer = document.getElementById('businessAreaHistoryContainer');
                    if (historyContainer) {
                        historyContainer.style.display = 'block';
                        // Initialize history component for business area
                        if (id != null) {
                            historyContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div id="businessAreaHistoryComponentContainer"></div>
                                </div>
                            `;
                            // Initialize history component for business area
                            if (window.HistoryComponent && id) {
                                console.log('Initializing history component for Business Area with ID:', id);
                                window.HistoryComponent.initialize('Business Area', id, 'businessAreaHistoryComponentContainer');
                            } else {
                                console.error('HistoryComponent not available or ID not found:', { HistoryComponent: !!window.HistoryComponent, id });
                            }
                            window.businessAreaTabData.history = historyContainer.innerHTML;
                        }
                    }
                } else if (which === 'change') {
                    const changeContainer = document.getElementById('businessAreaChangeContainer');
                    if (changeContainer) {
                        changeContainer.style.display = 'grid';
                        // Initialize change tab component
                        if (id != null && window.ChangeTabComponent) {
                            window.ChangeTabComponent.initialize('business-area', id, 'businessAreaChangeContainer');
                        } else if (id != null) {
                            changeContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div class="section-title">CHANGE MANAGEMENT</div>
                                    <div class="empty">No change data available for this business area</div>
                                </div>
                            `;
                            window.businessAreaTabData.change = changeContainer.innerHTML;
                        }
                    }
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('businessAreaStakeholdersContainer');
                }
            });
        });
        
    } catch (error) {
        console.error('Error initializing business area page:', error);
        showError('Failed to initialize page');
    }

    // Make functions globally available
    window.viewBusinessArea = function(businessAreaId) {
        window.location.href = `/view/business-area/business-area.html?id=${businessAreaId}`;
    };
});

// Switch to specific tab (global function)
    function switchToTab(tabName) {
        console.log('=== SWITCHING TO TAB IN VIEW ===');
        console.log('Tab name:', tabName);
        
        // Remove active class from all tabs
        document.querySelectorAll('.tab').forEach(tab => {
            tab.classList.remove('active');
        });
        
        // Hide all containers
        const containers = [
            'businessAreaViewContainer',
            'businessAreaRelationshipsContainer', 
            'businessAreaStakeholdersContainer',
            'businessAreaImpactContainer',
            'businessAreaHistoryContainer',
            'businessAreaChangeContainer'
        ];
        containers.forEach(function(containerId) {
            const container = document.getElementById(containerId);
            if (container) container.style.display = 'none';
        });

        // Activate selected tab
        const activeTab = document.querySelector(`[data-tab="${tabName}"]`);
        if (activeTab) {
            activeTab.classList.add('active');
            console.log('Activated tab:', tabName);
        } else {
            console.error('Tab not found:', tabName);
        }
    
    // Update URL with current tab
    updateURLWithTab(tabName);
    
    // Get the business area ID
    const id = parseId();
        
        // Show selected tab content and trigger the tab click logic
        if (tabName === 'summary') {
            const summaryContainer = document.getElementById('businessAreaViewContainer');
            if (summaryContainer) {
                summaryContainer.style.display = 'block';
            // Always reload data when switching to summary tab
            if (id != null) {
                load(id).then(function() {
                    window.businessAreaTabData.summary = summaryContainer.innerHTML;
                });
            }
            }
        } else if (tabName === 'relationships') {
            const relationshipsContainer = document.getElementById('businessAreaRelationshipsContainer');
            if (relationshipsContainer) {
                relationshipsContainer.style.display = 'block';
            // Always reload data when switching to relationships tab
            if (id != null) {
                relationshipsContainer.innerHTML = `
                    <div class="view-section relationships-hierarchy" style="grid-column:1/-1;">
                        <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>
                    </div>
                `;

                // Load hierarchy data immediately
                loadBusinessAreaHierarchy(id);
                
                // Cache the content
                window.businessAreaTabData.relationships = relationshipsContainer.innerHTML;
            }
            }
        } else if (tabName === 'stakeholders') {
            const stakeholdersContainer = document.getElementById('businessAreaStakeholdersContainer');
            if (stakeholdersContainer) {
                stakeholdersContainer.style.display = 'block';
            // Always reload data when switching to stakeholders tab
            if (id != null) {
                // Initialize stakeholder view if not already done
                if (window.BusinessAreaStakeholderView) {
                    window.BusinessAreaStakeholderView.init(id);
                } else {
                    stakeholdersContainer.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1;">
                            <div class="section-title">${window.I18n?.t('businessArea.stakeholder.sections.stakeholders') || 'STAKEHOLDERS'}</div>
                            <div class="empty">Loading stakeholders...</div>
                        </div>
                    `;
                }
                window.businessAreaTabData.stakeholders = stakeholdersContainer.innerHTML;
            }
            }
        } else if (tabName === 'impact') {
            const impactContainer = document.getElementById('businessAreaImpactContainer');
            if (impactContainer) {
                impactContainer.style.display = 'block';
            // Always reload data when switching to impact tab
            if (id != null) {
                if (typeof window.loadBusinessAreaImpact === 'function') {
                    window.loadBusinessAreaImpact(id);
                } else {
                    impactContainer.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1;">
                            <div class="section-title">IMPACT ANALYSIS</div>
                            <div class="empty">Loading impact data...</div>
                        </div>
                    `;
                }
            }
            }
        } else if (tabName === 'history') {
            const historyContainer = document.getElementById('businessAreaHistoryContainer');
            if (historyContainer) {
                historyContainer.style.display = 'block';
            // Initialize history component for business area
            if (id != null) {
                historyContainer.innerHTML = `
                    <div class="view-section" style="grid-column:1/-1;">
                        <div id="businessAreaHistoryComponentContainer"></div>
                    </div>
                `;
                // Initialize history component for business area
                if (window.HistoryComponent && id) {
                    console.log('Initializing history component for Business Area with ID:', id);
                    window.HistoryComponent.initialize('Business Area', id, 'businessAreaHistoryComponentContainer');
                } else {
                    console.error('HistoryComponent not available or ID not found:', { HistoryComponent: !!window.HistoryComponent, id });
                }
                window.businessAreaTabData.history = historyContainer.innerHTML;
            }
            }
        } else if (tabName === 'change') {
            const changeContainer = document.getElementById('businessAreaChangeContainer');
            if (changeContainer) {
                changeContainer.style.display = 'grid';
                // Initialize change tab component
                if (id != null && window.ChangeTabComponent) {
                    window.ChangeTabComponent.initialize('business-area', id, 'businessAreaChangeContainer');
                } else if (id != null) {
                    changeContainer.innerHTML = `
                        <div class="view-section" style="grid-column:1/-1;">
                            <div class="section-title">CHANGE MANAGEMENT</div>
                            <div class="empty">No change data available for this business area</div>
                        </div>
                    `;
                }
            }
        }
    }
// Build family tree hierarchy for business areas - following Regulatory Theme pattern
function buildFamilyLineage(businessAreas, currentBusinessAreaId) {
    const byId = new Map();
    businessAreas.forEach(ba => {
        const id = parseInt(ba.ID ?? ba.id);
        byId.set(id, ba);
    });
    
    const currentId = parseInt(currentBusinessAreaId);
    const currentBusinessArea = byId.get(currentId);
    
    if (!currentBusinessArea) {
        console.log('Current business area not found');
        return businessAreas;
    }
    
    // Find all ancestors (parents, grandparents, etc.)
    const ancestors = new Set();
    let current = currentBusinessArea;
    
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
        const children = businessAreas.filter(ba => {
            const parentId = ba.Parent_ID ?? ba.parent_id ?? ba.parentId;
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
    if (currentBusinessArea) {
        const currentParentId = currentBusinessArea.Parent_ID ?? currentBusinessArea.parent_id ?? currentBusinessArea.parentId;
        if (currentParentId) {
            const parentId = parseInt(currentParentId);
            const siblings = businessAreas.filter(ba => {
                const baParentId = parseInt(ba.Parent_ID ?? ba.parent_id ?? ba.parentId);
                const baId = parseInt(ba.ID ?? ba.id);
                return baParentId === parentId && baId !== currentId;
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
    
    return businessAreas.filter(ba => {
        const id = parseInt(ba.ID ?? ba.id);
        return includedIds.has(id);
    });
}

// Build hierarchy tree structure following Regulatory Theme pattern
function buildHierarchyTree(businessAreas, rootId) {
    const byParent = new Map();
    const byId = new Map();
    
    businessAreas.forEach(ba => {
        const id = parseInt(ba.ID ?? ba.id);
        const parentId = ba.Parent_ID ?? ba.parent_id ?? ba.parentId;
        const parentIdNum = parentId ? parseInt(parentId) : null;
        byId.set(id, ba);
        
        if (!byParent.has(parentIdNum)) {
            byParent.set(parentIdNum, []);
        }
        byParent.get(parentIdNum).push(ba);
    });
    
    const rows = [];
    function buildRows(businessAreaId, depth = 0) {
        const currentBusinessArea = byId.get(businessAreaId);
        if (currentBusinessArea) {
            const childCount = (byParent.get(businessAreaId) || []).length;
            
            rows.push({
                node: currentBusinessArea,
                depth: depth,
                childCount: childCount,
                hasChildren: childCount > 0
            });
            
            const children = byParent.get(businessAreaId) || [];
            children.forEach(ba => {
                buildRows(parseInt(ba.ID ?? ba.id), depth + 1);
            });
        } else {
            const children = byParent.get(businessAreaId) || [];
            children.forEach(ba => {
                buildRows(parseInt(ba.ID ?? ba.id), depth);
            });
        }
    }
    
    const rootIdNum = rootId ? parseInt(rootId) : null;
    buildRows(rootIdNum);
    
    return { rows, parentMap: byParent };
}

// Render business area hierarchy table following Regulatory Theme pattern
function renderBusinessAreaTable(hierarchyRows, currentId) {
    const Mask = window.HierarchyMask;
    const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
        const isMaskedNode = Mask ? Mask.isMasked(node) : false;
        const fallbackName = node.primaryName ?? node.PrimaryName ?? node.Name ?? node.name ?? 'Unnamed Business Area';
        const fallbackDesc = node.description ?? node.Description ?? '';
        const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
        const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
        const isCurrent = String(node.id ?? node.ID) === String(currentId);
        const id = node.id ?? node.ID;
        const parentId = node.parentId ?? node.Parent_ID ?? node.parent_id ?? '';

        const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
        const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
        const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
        const linkClass = isCurrent ? 'ba-link current-ba-link' : 'ba-link';
        const link = isMaskedNode
            ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
            : `<a class="${linkClass}" href="/view/business-area/business-area.html?id=${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
        const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

        return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}"${isMaskedNode ? ' data-masked="true"' : ''}>
            <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-building item-icon"></i><span class="ba-name">${link}</span>${countBadge}</div></td>
            <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
        </tr>`;
    }).join('');

    return rowsHtml;
}

// Initialize business area hierarchy interactions following Regulatory Theme pattern
function initBusinessAreaInteractions(containerEl, hierarchyRows) {
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
function buildDirectLineageTree(businessAreas, currentBusinessAreaId) {
    const byId = new Map();
    businessAreas.forEach(ba => byId.set(parseInt(ba.id), ba));

    const current = byId.get(parseInt(currentBusinessAreaId));
    if (!current) return [];

    const ancestors = new Set();
    const descendants = new Set();
    const visited = new Set(); // Cycle protection

    // Add current business area
    descendants.add(parseInt(currentBusinessAreaId));

    // Find all ancestors with cycle protection
    let parent = current;
    let depth = 0;
    const maxDepth = 10; // Prevent infinite loops
    
    while (parent && parent.parentId && depth < maxDepth) {
        const parentId = parseInt(parent.parentId);
        
        // Skip self-reference and cycle detection
        if (parentId === parseInt(currentBusinessAreaId) || visited.has(parentId)) {
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

    // Find all descendants (including current business area's children and their descendants) with cycle protection
    function findDescendants(businessAreaId, depth = 0) {
        if (depth > maxDepth) return; // Prevent infinite recursion
        
        businessAreas.forEach(ba => {
            const childId = parseInt(ba.id);
            const parentId = parseInt(ba.parentId);
            
            // Skip self-reference and cycle detection
            if (parentId === businessAreaId && childId !== parseInt(currentBusinessAreaId) && !visited.has(childId)) {
                visited.add(childId);
                descendants.add(childId);
                findDescendants(childId, depth + 1); // Recursively find all descendants
            }
        });
    }
    findDescendants(parseInt(currentBusinessAreaId));

    // Find siblings (other children of the same parent) and their descendants
    if (current.parentId) {
        const parentId = parseInt(current.parentId);
        const siblings = businessAreas.filter(ba => {
            const baParentId = parseInt(ba.parentId);
            const baId = parseInt(ba.id);
            return baParentId === parentId && baId !== parseInt(currentBusinessAreaId);
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

    // Include the current business area, all its ancestors, and all its descendants (including siblings and their descendants)
    const includedIds = new Set([parseInt(currentBusinessAreaId), ...ancestors, ...descendants]);

    // Filter business areas to include only the complete family tree
    return businessAreas.filter(ba => {
        const id = parseInt(ba.id);
        return includedIds.has(id);
    });
}

// Function to load business area hierarchy data (global function)
async function loadBusinessAreaHierarchy(businessAreaId) {
    const container = document.querySelector('.relationships-hierarchy');
    if (!container) return;
    
    try {
        container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>';
        
        // Fetch all business areas from hierarchy endpoint
        console.log('Fetching business areas from /api/business-areas/hierarchy');
        const response = await fetch('/api/business-areas/hierarchy');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const businessAreas = await response.json();
        
        console.log('Business areas loaded for hierarchy:', businessAreas);
        
        const hierT = (k) => (window.I18n && window.I18n.t(k)) || k;
        if (!Array.isArray(businessAreas) || businessAreas.length === 0) {
            container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">' + (hierT('businessArea.hierarchy.noBusinessAreas')) + '</div>';
            return;
        }

        // Build direct lineage tree (current + ancestors + descendants + siblings)
        const filteredBusinessAreas = buildDirectLineageTree(businessAreas, businessAreaId);
        
        const noRelatedMsg = hierT('businessArea.hierarchy.noRelatedBusinessAreas');
        const failedMsg = hierT('businessArea.hierarchy.failedToLoadHierarchy');

        if (filteredBusinessAreas.length === 0) {
            container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">' + noRelatedMsg + '</div>';
            return;
        }

        // Build hierarchy tree
        const hierarchyRows = buildHierarchyTree(filteredBusinessAreas, 0);
        const count = filteredBusinessAreas.length;
        const footerText = count === 1
            ? (window.I18n && window.I18n.t('businessArea.hierarchy.record')) || '1 record'
            : (window.I18n && window.I18n.t('businessArea.hierarchy.records', { count })) || count + ' records';

        // Render table
        const tableHtml = `
            <div class="hierarchy-header">
                <div class="hierarchy-title">${hierT('businessArea.hierarchy.title')}</div>
                <div class="hierarchy-actions">
                    <button type="button" class="btn btn-secondary"><i class="fas fa-cog"></i></button>
                </div>
            </div>
            <div class="hierarchy-table-wrapper">
                <table class="hierarchy-table">
                    <thead>
                        <tr>
                            <th>${hierT('businessArea.hierarchy.businessArea')}</th>
                            <th>${hierT('businessArea.hierarchy.description')}</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${renderBusinessAreaTable(hierarchyRows, businessAreaId)}
                    </tbody>
                </table>
            </div>
            <div class="table-footer">
                ${footerText}
            </div>
        `;
        
        container.innerHTML = tableHtml;
        
        // Initialize interactions
        initBusinessAreaInteractions(container, hierarchyRows);
        
    } catch (error) {
        console.error('Failed to load business area hierarchy:', error);
        const failedMsg = (window.I18n && window.I18n.t('businessArea.hierarchy.failedToLoadHierarchy')) || 'Failed to load hierarchy data:';
        const errText = (failedMsg.indexOf('{error}') !== -1) ? failedMsg.replace('{error}', error.message || 'Unknown error') : failedMsg + ' ' + (error.message || 'Unknown error');
        container.innerHTML = '<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + errText + '</div>';
    }
}

// Update URL with current tab (global function)
function updateURLWithTab(tabName) {
    const currentId = parseId();
    if (currentId && tabName) {
        const currentUrl = new URL(window.location);
        currentUrl.searchParams.set('tab', tabName);
        
        // Update URL without page reload
        window.history.pushState({}, '', currentUrl.toString());
        console.log('Updated URL with tab:', tabName, 'New URL:', currentUrl.toString());
    }
}

