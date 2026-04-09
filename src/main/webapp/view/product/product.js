(function() {
    console.log('=== PRODUCT VIEW JAVASCRIPT LOADING ===');
    console.log('Product view JavaScript loaded');
    console.log('Current timestamp:', new Date().toISOString());
    console.log('Window object available:', !!window);
    console.log('Document object available:', !!document);
    console.log('Document ready state:', document.readyState);
    
    // Test function that can be called manually from console
    window.testProductLoad = function(id = 1) {
        console.log('=== MANUAL TEST PRODUCT LOAD ===');
        console.log('Testing with ID:', id);
        load(id).catch(e => console.error('Manual test failed:', e));
    };
    
    function parseId() {
        // First try URL params (for edit page redirects)
        const urlParams = new URLSearchParams(window.location.search);
        const idFromParams = parseInt(urlParams.get('id'), 10);
        if (!Number.isNaN(idFromParams)) return idFromParams;
        
        // Fallback to path-based ID (for direct navigation)
        const parts = window.location.pathname.split('/').filter(Boolean);
        // expect /view/product/{id}
        const idx = parts.indexOf('product');
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
    }

    function showEditControls() {
        const ids = [
            'tabEditBtn',
            'editStakeholdersBtn'
        ];
        ids.forEach(function(id){
            const el = document.getElementById(id);
            if (el) el.style.display = 'inline-flex';
        });
    }

    async function hideEditsIfUnauthenticated() {
        try {
            const id = parseId();
            if (!id) {
                console.log('[Product] No ID found - hiding edit controls');
                hideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Product', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Product] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Product] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Product] User cannot edit this object - hiding edit controls');
                    hideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Product', { method: 'GET', credentials: 'include' });
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
            '#deleteProductBtn'
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

    // Helper function for HTML escaping - moved to global scope
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

    // Resolve foreign key references
    async function resolveReferences(product) {
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // Status name from status
        if (!product.statusName && product.status) {
            const statusId = product.status;
            tasks.push((async () => {
                try {
                    let status = await api.getStatusById(statusId);
                    if (status && status.data) status = status.data;
                    product.statusName = status?.primaryname || status?.Name || status?.name || String(statusId);
                    console.log('Resolved status name:', product.statusName);
                } catch(e) {
                    console.error('Failed to resolve status name:', e);
                    product.statusName = String(statusId);
                }
            })());
        }

        // Lifecycle status name from lifecycle_status
        if (!product.lifecycleName && product.lifecycle_status) {
            const lifecycleId = product.lifecycle_status;
            console.log('Resolving product lifecycle status ID:', lifecycleId);
            tasks.push((async () => {
                try {
                    let lifecycle = await api.getProductLifecycleById(lifecycleId);
                    console.log('Product Lifecycle API response:', lifecycle);
                    if (lifecycle && lifecycle.data) lifecycle = lifecycle.data;
                    product.lifecycleName = lifecycle?.primaryname || lifecycle?.Name || lifecycle?.name || String(lifecycleId);
                    console.log('Resolved product lifecycle name:', product.lifecycleName);
                } catch(e) {
                    console.error('Failed to resolve product lifecycle name:', e);
                    product.lifecycleName = String(lifecycleId);
                }
            })());
        }

        // Created by person name from createdby_id
        const createdById = product.createdby_id || product.created_by_id || product.createdById || product.createdBy_ID;
        if (!product.createdByName && createdById) {
            const userId = createdById;
            console.log('Resolving Created By ID:', userId);
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(userId);
                    console.log('Person API response:', person);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    product.createdByName = fullName || person?.Email || person?.email || String(userId);
                    console.log('Resolved created by name:', product.createdByName);
                } catch(e) {
                    console.error('Failed to resolve created by name:', e);
                    product.createdByName = String(userId);
                }
            })());
        } else {
            console.log('Created By not resolved - createdByName:', product.createdByName, 'createdById:', createdById);
        }

        // Last updated by person name from lastupdateuser_id
        if (!product.lastUpdatedByName && product.lastupdateuser_id) {
            const userId = product.lastupdateuser_id;
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(userId);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    product.lastUpdatedByName = fullName || person?.Email || person?.email || String(userId);
                    console.log('Resolved last updated by name:', product.lastUpdatedByName);
                } catch(e) {
                    console.error('Failed to resolve last updated by name:', e);
                    product.lastUpdatedByName = String(userId);
                }
            })());
        }

        // BUDG Viewing from is_public field
        if (!product.viewingName && product.is_public) {
            const viewingId = product.is_public;
            console.log('Resolving BUDG Viewing ID:', viewingId);
            tasks.push((async () => {
                try {
                    let viewing = await api.getViewingById(viewingId);
                    console.log('Viewing API response:', viewing);
                    if (viewing && viewing.data) viewing = viewing.data;
                    product.viewingName = viewing?.primaryname || viewing?.Name || viewing?.name || String(viewingId);
                    console.log('Resolved BUDG Viewing name:', product.viewingName);
                } catch(e) {
                    console.error('Failed to resolve BUDG Viewing name:', e);
                    product.viewingName = String(viewingId);
                }
            })());
        }

        await Promise.all(tasks);
        return product;
    }


    // Simple guest detection shared with segment gating
    let _productGuestCache = null;
    async function isGuestVisitorForProduct() {
        if (_productGuestCache !== null) {
            return _productGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _productGuestCache = true;
                return _productGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _productGuestCache = !isAuthenticated || isGuestRole;
            return _productGuestCache;
        } catch (_) {
            _productGuestCache = true;
            return _productGuestCache;
        }
    }

    function renderProductGuestAccessDenied(container, productId, segmentName, segmentId) {
        if (!container) return;
        const title = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
        const message = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.message')) || 'This item is not available for guest users.';
        const code = 'ERR-SEGMENT-NOT-ENTERPRISE';
        const segmentLabel = segmentName || ((segmentId !== undefined && segmentId !== null) ? `ID ${segmentId}` : 'Unknown');
        container.innerHTML = `
            <div class="view-section" style="grid-column: 1/-1; text-align: center; padding: 3rem 1rem;">
                <div class="access-denied-title" style="font-size: 1.5rem; font-weight: 600; margin-bottom: 0.75rem;">${segment_escapeHtml(title)}</div>
                <div class="access-denied-message" style="margin-bottom: 1.25rem; color: var(--text-muted, #4b5563);">
                    ${segment_escapeHtml(message)}
                </div>
                <div class="access-denied-details" style="font-size: 0.95rem; color: var(--text-muted, #6b7280);">
                    <div style="margin-bottom: 0.5rem;">Segment: <strong>${segment_escapeHtml(segmentLabel)}</strong></div>
                    <div>Error code: <code>${code}</code></div>
                </div>
            </div>
        `;
    }

    function segment_escapeHtml(text) {
        if (text == null) return '';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }

    // Load product data
    async function load(id) {
        console.log('Loading product with ID:', id);
        
        if (!window.BUDG_API_SERVICE) {
            console.error('BUDG_API_SERVICE not available');
            return;
        }

        try {
            const data = await window.BUDG_API_SERVICE.getProductById(id);
            try { window.BUDG_API_SERVICE.logVisit({ entity: 'Product', entityId: String(id), route: `/view/product/${id}` }); } catch(_) {}
            console.log('Product data received:', data);
            console.log('Product data fields:', Object.keys(data));
            console.log('Created By fields check:');
            console.log('- createdby_id:', data.createdby_id);
            console.log('- created_by_id:', data.created_by_id);
            console.log('- createdById:', data.createdById);
            console.log('- createdBy_ID:', data.createdBy_ID);
            console.log('Created Date fields check:');
            console.log('- createdatetime:', data.createdatetime);
            console.log('- created_datetime:', data.created_datetime);
            console.log('- created_date:', data.created_date);
            console.log('- createdAt:', data.createdAt);
            console.log('All available fields:', Object.keys(data));
            console.log('Long Name fields check:');
            console.log('- longname:', data.longname);
            console.log('- longName:', data.longName);
            console.log('- long_name:', data.long_name);
            console.log('- longnumber:', data.longnumber);
            
            // Resolve foreign key references
            console.log('=== RESOLVING PRODUCT REFERENCES ===');
            const resolvedData = await resolveReferences(data);
            console.log('References resolved, final data:', resolvedData);
            
            // Guest / segment gating: unauthenticated users may only view Enterprise segment
            try {
                const isGuest = await isGuestVisitorForProduct();
                const segmentName = resolvedData.segmentName || resolvedData.segment_name || resolvedData.segment;
                const segmentId = resolvedData.segmentId ?? resolvedData.segment_id ?? resolvedData.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();

                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Product] Guest visitor blocked from non-Enterprise segment view', {
                        productId: id,
                        segmentName: segmentName,
                        segmentId: segmentId
                    });
                    const container = document.getElementById('productViewContainer');
                    renderProductGuestAccessDenied(container, id, segmentName, segmentId);
                    return null;
                }
            } catch (gateError) {
                console.warn('[Product] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            console.log('=== FIELD VALUES DEBUG ===');
            console.log('Status Name:', resolvedData.statusName);
            console.log('Lifecycle Name:', resolvedData.lifecycleName);
            console.log('BUDG Viewing Name:', resolvedData.viewingName);
            console.log('BUDG Viewing ID:', resolvedData.is_public);
            console.log('Created By Name:', resolvedData.createdByName);
            console.log('Created By ID:', resolvedData.createdby_id || resolvedData.created_by_id || resolvedData.createdById);
            console.log('Created Date:', resolvedData.createdatetime || resolvedData.created_datetime || resolvedData.created_date);
            console.log('Last Updated By Name:', resolvedData.lastUpdatedByName);
            console.log('Last Updated By ID:', resolvedData.lastupdateuser_id);
            console.log('=== RAW DATA DEBUG ===');
            console.log('Raw product data:', data);
            console.log('Available fields:', Object.keys(data));
            
            renderProduct(resolvedData);
            
            
            return resolvedData;
        } catch(error) {
            console.error('Error loading product:', error);
            // Fallback to mock data if API fails
            const mockData = {
                primaryName: 'fgdbh sfgc',
                description: 'fgdbhsfgc',
                longName: (window.I18n?.t('message.notSpecified') || 'Not specified'),
                refNumber: 'PRD-1',
                statusName: 'Active',
                lifecycleName: 'IN PRODUCTION',
                viewingName: 'Public',
                createDatetime: '23-Sep-2025',
                lastUpdatedByName: 'John Admin',
                lastUpdateDatetime: '23-Sep-2025'
            };
            console.log('Using fallback mock data:', mockData);
            renderProduct(mockData);
            return mockData;
        }
    }

    // Render product data
    function renderProduct(data) {
        console.log('Rendering product:', data);
        
        const container = document.getElementById('productViewContainer');
        if (!container) {
            console.error('Product view container not found');
            return;
        }

        // Set page title
        const titleElement = document.getElementById('productTitle');
        if (titleElement && (data.primaryname || data.primaryName || data.name || data.description)) {
            titleElement.textContent = data.primaryname || data.primaryName || data.name || data.description;
        }

        const breadcrumbElement = document.getElementById('productBreadcrumb');
        if (breadcrumbElement) {
            breadcrumbElement.textContent = 'Product';
        }

        // Helper functions
        function escapeHtml(text) {
            if (!text) return '';
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }

        function renderItem(label, value) {
            if (!value || value === '') {
                value = '<span class="empty">-</span>';
            } else if (value === 'Not specified') {
                value = '<span class="empty">Not specified</span>';
            } else if (typeof value === 'string' && value.includes('<')) {
                // Value already contains HTML, don't escape it
                return `
                    <div class="view-item">
                        <div class="view-label">${escapeHtml(label)}</div>
                        <div class="view-value">${value}</div>
                    </div>
                `;
            } else {
                value = escapeHtml(value);
            }
            return `
                <div class="view-item">
                    <div class="view-label">${escapeHtml(label)}</div>
                    <div class="view-value">${value}</div>
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
            return `<span class="user-link">${escapeHtml(name)}</span>`;
        }

        function renderDate(date) {
            if (!date) return '<span class="empty">-</span>';
            return escapeHtml(date);
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

        // Render sections using view-section classes
        const definitionSection = `
            <div class="view-section">
                <div class="section-title">${escapeHtml(vT('glossary.sections.definition', 'DEFINITION'))}</div>
                ${renderItem(vT('label.description', 'Description'), _richHtml(data.description || data.Description || ''))}
                ${renderItem(vT('label.longName', 'Long Name'), escapeHtml(data.longname || data.longName || data.long_name || vT('message.notSpecified', 'Not specified')))}
                ${renderItem(vT('label.ref', 'Ref.'), escapeHtml(data.refnumber || data.refNumber || data.ref_number || vT('message.notSpecified', 'Not specified')))}
            </div>
        `;

        const classificationsSection = `
            <div class="view-section">
                <div class="section-title">${escapeHtml(vT('glossary.sections.classifications', 'CLASSIFICATIONS'))}</div>
                ${renderItem(vT('glossary.labels.budgStatus', 'BUDG Status'), renderStatusBadge(data.statusName || data.status || 'Active', (data.statusName || data.status) === 'Active'))}
                ${renderItem(vT('glossary.labels.lifecycle', 'Lifecycle'), renderLifecycleBadge(data.lifecycleName || data.lifecycle_status || data.lifecycleStatus || 'IN PRODUCTION'))}
                ${renderItem(vT('glossary.labels.budgViewing', 'BUDG Viewing'), renderPublicStatus(data.viewingName || data.is_public || data.isPublic))}
            </div>
        `;

        const otherInfoSection = `
            <div class="view-section">
                <div class="section-title">${escapeHtml(vT('glossary.sections.otherInformation', 'OTHER INFORMATION'))}</div>
                ${renderItem(vT('glossary.labels.createdBy', 'Created By'), renderUserLink(data.createdByName || 'System', data.createdby_id || data.created_by_id || data.createdById))}
                ${renderItem(vT('glossary.labels.created', 'Created'), renderDate(data.createdatetime || data.created_datetime || data.created_date))}
                ${renderItem(vT('glossary.labels.lastUpdatedBy', 'Last Updated By'), renderUserLink(data.lastUpdatedByName || data.last_updated_by_name, data.lastupdateuser_id || data.last_updated_user_id))}
                ${renderItem(vT('glossary.labels.lastUpdated', 'Last Updated'), renderDate(data.lastupdatedatetime || data.last_updated_datetime || data.last_updated_date))}
                ${renderItem(vT('glossary.labels.segment', 'Segment'), escapeHtml(data.segmentName || data.segment_name || data.segment || ((data.segmentId ?? data.segment_id ?? data.Segment_ID) != null ? vT('common.segmentIdDisplay', 'ID {id}', { id: data.segmentId ?? data.segment_id ?? data.Segment_ID }) : vT('message.notSpecified', 'Not specified'))))}
            </div>
        `;
        
        // Render the sections
        container.innerHTML = definitionSection + classificationsSection + otherInfoSection;
    }

    // Show error message
    function showError(message) {
        const normalized = String(message || '');
        const userMessage = normalized.includes('403')
            ? vT('capability.view.objectNotAvailable', 'This object is not available.')
            : normalized;
        const container = document.getElementById('productViewContainer');
        if (container) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">${escapeHtml(vT('message.error', 'Error'))}</div>
                    <div class="empty">${escapeHtml(userMessage)}</div>
                </div>
            `;
        }
    }

        // Tab data storage - make it global
        if (!window.productTabData) {
            window.productTabData = {
                summary: null,
                relationships: null,
                stakeholders: null,
                impact: null,
                history: null,
                change: null
            };
        }

    // Tab handling
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(function(tab) {
        tab.addEventListener('click', function() {
            tabs.forEach(function(t){ t.classList.remove('active'); });
            this.classList.add('active');

            const which = this.getAttribute('data-tab');
            const id = parseId();
            
            console.log('Switching to tab:', which);
            
            // Hide all containers
            const containers = [
                'productViewContainer',
                'productRelationshipsContainer',
                'productStakeholdersContainer',
                'productImpactContainer',
                'productHistoryContainer',
                'productChangeContainer'
            ];
            containers.forEach(function(containerId) {
                const container = document.getElementById(containerId);
                if (container) container.style.display = 'none';
            });
            
            if (which === 'summary') {
                const summaryContainer = document.getElementById('productViewContainer');
                if (summaryContainer) {
                    summaryContainer.style.display = 'block';
                    // Always reload data when switching to summary tab
                    if (id != null) {
                        load(id).then(async function(data) {
                            // Add small delay to ensure all styling is applied
                            setTimeout(function() {
                                window.productTabData.summary = summaryContainer.innerHTML;
                            }, 100);
                            
                            // Check and display lock status
                            if (window.ViewLockHelper) {
                                await window.ViewLockHelper.checkAndDisplayLockStatus('product', id);
                            }
                        });
                    }
                    
                }
            } else if (which === 'relationships') {
                const relationshipsContainer = document.getElementById('productRelationshipsContainer');
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
                        loadProductHierarchy(id);
                        
                        // Cache the content
                        window.productTabData.relationships = relationshipsContainer.innerHTML;
                    }
                }
            } else if (which === 'stakeholders') {
                const stakeholdersContainer = document.getElementById('productStakeholdersContainer');
                if (stakeholdersContainer) {
                    stakeholdersContainer.style.display = 'block';
                    // Always reload data when switching to stakeholders tab
                    if (id != null) {
                        if (window.ProductStakeholderView) {
                            window.ProductStakeholderView.init(id);
                            setTimeout(function() {
                                window.productTabData.stakeholders = stakeholdersContainer.innerHTML;
                            }, 500);
                        } else {
                            console.error('ProductStakeholderView not available');
                            stakeholdersContainer.innerHTML = '<div class="error">Error: Stakeholder view not loaded</div>';
                        }
                    }
                }
            } else if (which === 'impact') {
                const impactContainer = document.getElementById('productImpactContainer');
                if (impactContainer) {
                    impactContainer.style.display = 'block';
                    // Load impact data using product-impact.js
                    if (window.loadProductImpact && typeof window.loadProductImpact === 'function') {
                        window.loadProductImpact(id);
                    } else {
                        console.error('Product impact view functionality not available');
                        impactContainer.innerHTML = `
                            <div class="view-section" style="grid-column:1/-1;">
                                <div class="section-title">IMPACT</div>
                                <div class="empty">No impact data available for this product</div>
                            </div>
                        `;
                    }
                }
            } else if (which === 'history') {
                const historyContainer = document.getElementById('productHistoryContainer');
                if (historyContainer) {
                    historyContainer.style.display = 'block';
                    // Always reload data when switching to history tab
                    if (id != null) {
                        console.log('Loading HISTORY tab for product ID:', id);
                        
                        // Initialize history component for Product
                        if (window.initializeHistoryComponent) {
                            window.initializeHistoryComponent('Products', id, 'productHistoryContainer');
                        } else {
                            console.error('History component not available');
                            historyContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div class="section-title">PRODUCT HISTORY</div>
                                    <div class="empty">History component not available</div>
                                </div>
                            `;
                        }
                    }
                }
            } else if (which === 'change') {
                const changeContainer = document.getElementById('productChangeContainer');
                if (changeContainer) {
                    changeContainer.style.display = 'grid';
                    // Initialize change tab component
                    if (window.ChangeTabComponent && id != null) {
                        window.ChangeTabComponent.initialize('product', id, 'productChangeContainer');
                    } else if (id != null) {
                        changeContainer.innerHTML = `
                            <div class="view-section" style="grid-column:1/-1;">
                                <div class="section-title">CHANGE MANAGEMENT</div>
                                <div class="empty">No change data available for this product</div>
                            </div>
                        `;
                        window.productTabData.change = changeContainer.innerHTML;
                    }
                }
            } else {
                // Default to summary
                const summaryContainer = document.getElementById('productViewContainer');
                if (summaryContainer) {
                    summaryContainer.style.display = 'block';
                    if (window.productTabData.summary === null && id != null) {
                        load(id).then(function(data) {
                            window.productTabData.summary = summaryContainer.innerHTML;
                        });
                    } else if (window.productTabData.summary !== null) {
                        summaryContainer.innerHTML = window.productTabData.summary;
                    }
                }
            }
            if (typeof window.syncStakeholderVisibility === 'function') {
                window.syncStakeholderVisibility('productStakeholdersContainer');
            }
        });
    });

    // Back button
    const backBtn = document.getElementById('backBtn');
    if (backBtn) {
        backBtn.addEventListener('click', function() {
            window.history.back();
        });
    }

    // Edit button
    const editBtn = document.getElementById('tabEditBtn');
    if (editBtn) {
        editBtn.addEventListener('click', function() {
            const id = parseId();
            if (id) {
                // Get the currently active tab
                const activeTab = document.querySelector('.tab.active');
                const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                console.log('Active tab:', tabName);
                
                // Map view tab names to edit tab names
                const tabMapping = {
                    'summary': 'summaryTab',
                    'relationships': 'relationshipsTab',
                    'stakeholders': 'stakeholdersTab',
                    'impact': 'impactTab',
                    'history': 'historyTab',
                    'change': 'changeTab'
                };
                
                const editTabName = tabMapping[tabName] || 'summaryTab';
                console.log('Navigating to edit tab:', editTabName);
                
                // Navigate to edit page with tab parameter
                window.location.href = `/view/product/product-edit.html?id=${id}&tab=${editTabName}`;
            }
        });
    }

    // Function to handle tab parameter from URL
    function handleTabParameter() {
        const urlParams = new URLSearchParams(window.location.search);
        const tabParam = urlParams.get('tab');
        
        if (tabParam) {
            console.log('Tab parameter found:', tabParam);
            
            // Find the tab button with matching data-tab attribute
            const targetTab = document.querySelector(`[data-tab="${tabParam}"]`);
            if (targetTab) {
                console.log('Switching to tab:', tabParam);
                // Small delay to ensure page is loaded
                setTimeout(() => {
                    targetTab.click();
                }, 100);
            } else {
                console.log('Tab not found:', tabParam);
            }
        }
    }

    // Load initial data
    const id = parseId();
    if (id) {
            load(id).then(async function(data) {
                const summaryContainer = document.getElementById('productViewContainer');
                if (summaryContainer && window.productTabData) {
                    // Add small delay to ensure all styling is applied
                    setTimeout(function() {
                        window.productTabData.summary = summaryContainer.innerHTML;
                    }, 100);
                }

                // Initialize unified edit dropdown
                if (window.EditDropdown && id != null) {
                    try {
                        window.EditDropdown.initialize('product', id, {
                            container: '.tab-actions',
                            editUrl: `/view/product/product-edit.html?id=${id}`
                        });
                    } catch (error) {
                        console.error('Failed to initialize edit dropdown:', error);
                    }
                }
                
                // Handle tab parameter after data is loaded
                handleTabParameter();

                // Add follow button to header actions
                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('product', id, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for product:', e);
                    }
                }

                // Render custom fields section
                if (window.CustomFields) {
                    try {
                        await window.CustomFields.renderViewSection({
                            facetId: 'Product',
                            containerId: 'productViewContainer',
                            objectId: id,
                            title: 'CUSTOM FIELDS'
                        });
                    } catch (error) {
                        console.error('Error rendering custom fields:', error);
                    }
                }
            });
    } else {
        showError('No product ID provided');
    }

    // Initialize edit controls
    hideEditsIfUnauthenticated();

    // Function to load product hierarchy data
    // Build family lineage tree for products - following Regulatory Theme pattern
    function buildFamilyLineage(products, currentProductId) {
        const byId = new Map();
        products.forEach(p => {
            const id = parseInt(p.ID ?? p.id ?? p.ID);
            byId.set(id, p);
        });
        
        const currentId = parseInt(currentProductId);
        const currentProduct = byId.get(currentId);
        
        if (!currentProduct) {
            console.log('Current product not found');
            return products;
        }
        
        // Find all ancestors (parents, grandparents, etc.)
        const ancestors = new Set();
        let current = currentProduct;
        
        while (current) {
            const parentId = current.Parent_ID ?? current.parent_id ?? current.parentId ?? current.parentid;
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
            const children = products.filter(p => {
                const parentId = p.Parent_ID ?? p.parent_id ?? p.parentId ?? p.parentid;
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
        if (currentProduct) {
            const currentParentId = currentProduct.Parent_ID ?? currentProduct.parent_id ?? currentProduct.parentId ?? currentProduct.parentid;
            if (currentParentId) {
                const parentId = parseInt(currentParentId);
                const siblings = products.filter(p => {
                    const pParentId = parseInt(p.Parent_ID ?? p.parent_id ?? p.parentId ?? p.parentid);
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
        
        return products.filter(p => {
            const id = parseInt(p.ID ?? p.id);
            return includedIds.has(id);
        });
    }

    // Build hierarchy tree structure following Regulatory Theme pattern
    function buildHierarchyTree(products, rootId) {
        const byParent = new Map();
        const byId = new Map();
        
        products.forEach(p => {
            const id = parseInt(p.ID ?? p.id);
            const parentId = p.Parent_ID ?? p.parent_id ?? p.parentId ?? p.parentid;
            const parentIdNum = parentId ? parseInt(parentId) : null;
            byId.set(id, p);
            
            if (!byParent.has(parentIdNum)) {
                byParent.set(parentIdNum, []);
            }
            byParent.get(parentIdNum).push(p);
        });
        
        const rows = [];
        function buildRows(productId, depth = 0) {
            const currentProduct = byId.get(productId);
            if (currentProduct) {
                const childCount = (byParent.get(productId) || []).length;
                
                rows.push({
                    node: currentProduct,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: childCount > 0
                });
                
                const children = byParent.get(productId) || [];
                children.forEach(p => {
                    buildRows(parseInt(p.ID ?? p.id), depth + 1);
                });
            } else {
                const children = byParent.get(productId) || [];
                children.forEach(p => {
                    buildRows(parseInt(p.ID ?? p.id), depth);
                });
            }
        }
        
        const rootIdNum = rootId ? parseInt(rootId) : null;
        buildRows(rootIdNum);
        
        return { rows, parentMap: byParent };
    }

    // Render product hierarchy table following Regulatory Theme pattern
    function renderProductTable(hierarchyRows, currentId) {
        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.primaryname ?? node.PrimaryName ?? node.primaryName ?? node.Name ?? node.name ?? 'Unnamed Product';
            const desc = node.description ?? node.Description ?? '';
            const isCurrent = String(node.id ?? node.ID) === String(currentId);
            const id = node.id ?? node.ID;
            const parentId = node.parentid ?? node.Parent_ID ?? node.parent_id ?? node.parentId ?? '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'product-link current-product-link' : 'product-link';
            const link = `<a class="${linkClass}" href="/view/product/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            const refNumber = node.refnumber ? `<span class="product-ref">(${escapeHtml(node.refnumber)})</span>` : '';
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-box item-icon"></i><span class="product-name">${link}</span>${refNumber}${countBadge}</div></td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
            </tr>`;
        }).join('');

        return rowsHtml;
    }

    // Initialize product hierarchy interactions following Regulatory Theme pattern
    function initProductInteractions(containerEl, hierarchyRows) {
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

    async function loadProductHierarchy(productId) {
        const container = document.querySelector('.relationships-hierarchy');
        if (!container) return;
        
        try {
            container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</div>';
            
            // Fetch all products from hierarchy endpoint
            console.log('Fetching products from /api/product/hierarchy');
            const response = await fetch('/api/product/hierarchy');
            console.log('Response status:', response.status);
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const products = await response.json();
            console.log('Products loaded for hierarchy:', products);
            
            if (!Array.isArray(products) || products.length === 0) {
                container.innerHTML = '<div style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No products found in database</div>';
                return;
            }

            // Build family lineage (ancestors + current + descendants + siblings)
            const familyTree = buildFamilyLineage(products, productId);
            
            // Find root (top-most ancestor)
            const byId = new Map();
            familyTree.forEach(p => byId.set(parseInt(p.id ?? p.ID), p));
            
            let rootId = parseInt(productId);
            let current = byId.get(rootId);
            while (current) {
                const parentId = current.parentid ?? current.parent_id ?? current.parentId ?? current.Parent_ID ?? current.Parent_ID;
                const parentIdNum = parseInt(parentId);
                if (parentId && !isNaN(parentIdNum) && byId.has(parentIdNum)) {
                    rootId = parentIdNum;
                    current = byId.get(parentIdNum);
                } else {
                    break;
                }
            }
            
            // Build hierarchy tree
            const hierarchyRows = buildHierarchyTree(familyTree, rootId);
            
            // Render table
            const tableHtml = `
                <div class="section-title">PRODUCT HIERARCHY</div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>Product</th>
                                <th>Description</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderProductTable(hierarchyRows, productId)}
                        </tbody>
                    </table>
                </div>
            `;
            
            container.innerHTML = tableHtml;
            
            // Initialize interactions
            initProductInteractions(container, hierarchyRows);
            
        } catch (error) {
            console.error('Failed to load product hierarchy:', error);
            container.innerHTML = `<div style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${error.message || 'Unknown error'}</div>`;
        }
    }

    // Function to load product relationships data
    async function loadProductRelationships(productId) {
        const tbody = document.getElementById('productRelationshipsTableBody');
        const footer = document.querySelector('#productRelationshipsContent .hierarchy-footer');
        
        if (!tbody) return;
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
            
            // Fetch relationships where this product is the SOURCE
            const relationships = await window.BUDG_API_SERVICE.getProductRelationshipsBySourceId(productId);
            
            if (!Array.isArray(relationships) || relationships.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">No relationships found</td></tr>';
                if (footer) footer.textContent = '0 records';
                return;
            }

            // Render relationships table
            tbody.innerHTML = relationships.map(function(rel) {
                return `
                    <tr>
                        <td>
                            <span class="view-badge">${escapeHtml(rel.relationTypeName || 'Unknown')}</span>
                        </td>
                        <td>
                            <div class="product-item">
                                <i class="fas fa-box product-icon"></i>
                                <span>${escapeHtml(rel.targetProductName || 'Unknown')}</span>
                            </div>
                        </td>
                        <td>
                            <span class="view-badge">${escapeHtml(rel.targetProductType || 'Unknown')}</span>
                        </td>
                        <td>
                            <div class="action-buttons">
                                <button type="button" class="btn btn-sm btn-secondary" onclick="viewProduct(${rel.targetProductId})">
                                    <i class="fas fa-eye"></i>
                                </button>
                                <button type="button" class="btn btn-sm btn-danger" onclick="deleteProductRelationship(${rel.id})">
                                    <i class="fas fa-trash"></i>
                                </button>
                            </div>
                        </td>
                    </tr>
                `;
            }).join('');

            if (footer) {
                footer.textContent = `${relationships.length} record${relationships.length !== 1 ? 's' : ''}`;
            }

        } catch (error) {
            console.error('Failed to load product relationships:', error);
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ' + error.message + '</td></tr>';
            if (footer) footer.textContent = '0 records';
        }
    }

    // Function to view product details
    function viewProduct(productId) {
        window.location.href = `/view/product/${productId}`;
    }

    // Function to delete product relationship
    async function deleteProductRelationship(relationshipId) {
        if (!confirm('Are you sure you want to delete this relationship?')) {
            return;
        }

        try {
            await window.BUDG_API_SERVICE.deleteProductRelationship(relationshipId);
            
            // Reload relationships data
            const id = parseId();
            if (id) {
                loadProductRelationships(id);
            }
            
            // Show success message
            alert('Relationship deleted successfully');
        } catch (error) {
            console.error('Failed to delete product relationship:', error);
            alert('Failed to delete relationship: ' + (error.message || 'Unknown error'));
        }
    }

    // Make functions globally available
    window.viewProduct = viewProduct;
    window.deleteProductRelationship = deleteProductRelationship;
})();

