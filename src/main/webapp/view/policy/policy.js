(function() {
    console.log('=== POLICY VIEW JAVASCRIPT LOADING ===');
    console.log('Policy view JavaScript loaded');
    console.log('Current timestamp:', new Date().toISOString());
    console.log('Window object available:', !!window);
    console.log('Document object available:', !!document);
    console.log('Document ready state:', document.readyState);
    
    // Test function that can be called manually from console
    window.testPolicyLoad = function(id = 1) {
        console.log('=== MANUAL TEST POLICY LOAD ===');
        console.log('Testing with ID:', id);
        load(id).catch(e => console.error('Manual test failed:', e));
    };
    
    function parseId() {
        console.log('Parsing ID from URL:', window.location.href);
        console.log('Pathname:', window.location.pathname);
        console.log('Search:', window.location.search);
        
        // First try URL params (for separate edit pages)
        const urlParams = new URLSearchParams(window.location.search);
        const id = parseInt(urlParams.get('id'), 10);
        console.log('ID from URL params:', id);
        if (!Number.isNaN(id)) return id;
        
        // Fallback to path-based ID (for main view pages)
        const parts = window.location.pathname.split('/').filter(Boolean);
        console.log('Path parts:', parts);
        // expect /view/policy/{id}
        const idx = parts.indexOf('policy');
        console.log('Policy index:', idx);
        if (idx === -1 || parts.length < idx + 2) return null;
        const pathId = parseInt(parts[idx + 1], 10);
        console.log('ID from path:', pathId);
        return Number.isNaN(pathId) ? null : pathId;
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
				console.log('[Policy] No ID found - hiding edit controls');
				hideEditControls();
				return;
			}

			// Use the shared utility function to check if user can edit this object
			if (window.checkCanEditObject) {
				const editCheck = await window.checkCanEditObject('Policy', id);
				const canEditObject = editCheck.canEdit;
				const isAdmin = editCheck.isAdmin;
				
				console.log('[Policy] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
				
				// Show the main edit dropdown only if user can edit this object
				if (canEditObject) {
					console.log('[Policy] User can edit this object - showing edit controls');
					showEditControls();
				} else {
					console.log('[Policy] User cannot edit this object - hiding edit controls');
					hideEditControls();
				}
				
				// Check delete permission
				const permResp = await fetch('/api/user/permissions/Policy', { method: 'GET', credentials: 'include' });
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
			'#deletePolicyBtn'
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
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class=\"empty\">-</span>'}</div></div>`;
    }

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
        return formatDateTime(date);
    }

    function renderDateOnly(date) {
        if (!date) return '<span class="empty">-</span>';
        try {
            const d = typeof date === 'string' || typeof date === 'number' ? new Date(date) : date;
            if (isNaN(d.getTime())) return escapeHtml(String(date));
            // Format as date only (YYYY-MM-DD or locale date format)
            return escapeHtml(d.toLocaleDateString('en-GB', {
                day: '2-digit',
                month: 'short',
                year: 'numeric'
            }));
        } catch (_) {
            return escapeHtml(String(date));
        }
    }

    async function resolveReferences(policy) {
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // User names are now provided directly by the backend API

            // BUDG Viewing from isPublic field
            if (!policy.viewingName && policy.isPublic) {
                const viewingId = policy.isPublic;
                console.log('Resolving BUDG Viewing ID:', viewingId);
                tasks.push((async () => {
                    try {
                        let viewing = await api.getViewingById(viewingId);
                        console.log('Viewing API response:', viewing);
                        if (viewing && viewing.data) viewing = viewing.data;
                        policy.viewingName = viewing?.primaryname || viewing?.Name || viewing?.name || String(viewingId);
                        console.log('Resolved BUDG Viewing name:', policy.viewingName);
                    } catch(e) {
                        console.error('Failed to resolve BUDG Viewing name:', e);
                        policy.viewingName = String(viewingId);
                    }
                })());
            }

        // Status name from status
        if (!policy.statusName && policy.status) {
            const statusId = policy.status;
            tasks.push((async () => {
                try {
                    let status = await api.getStatusById(statusId);
                    if (status && status.data) status = status.data;
                    policy.statusName = status?.primaryname || status?.Name || status?.name || String(statusId);
                    console.log('Resolved status name:', policy.statusName);
                } catch(e) { 
                    console.error('Failed to resolve status name:', e);
                    policy.statusName = String(statusId);
                }
            })());
        }

        // Lifecycle status name from lifecycleStatus
        if (!policy.lifecycleName && policy.lifecycleStatus) {
            const lifecycleId = policy.lifecycleStatus;
            console.log('Resolving lifecycle status ID:', lifecycleId);
            tasks.push((async () => {
                try {
                    let lifecycle = await api.getPolicyLifecycleStatusById(lifecycleId);
                    console.log('Lifecycle API response:', lifecycle);
                    if (lifecycle && lifecycle.data) lifecycle = lifecycle.data;
                    policy.lifecycleName = lifecycle?.primaryname || lifecycle?.Name || lifecycle?.name || String(lifecycleId);
                    console.log('Resolved lifecycle name:', policy.lifecycleName);
                } catch(e) { 
                    console.error('Failed to resolve lifecycle name:', e);
                    policy.lifecycleName = String(lifecycleId);
                }
            })());
        }

        // Policy type name from policyType
        if (!policy.typeName && policy.policyType) {
            const typeId = policy.policyType;
            console.log('Resolving policy type ID:', typeId);
            tasks.push((async () => {
                try {
                    let type = await api.getPolicyTypeById(typeId);
                    console.log('Policy Type API response:', type);
                    if (type && type.data) type = type.data;
                    policy.typeName = type?.primaryname || type?.Name || type?.name || String(typeId);
                    console.log('Resolved policy type name:', policy.typeName);
                } catch(e) { 
                    console.error('Failed to resolve policy type name:', e);
                    policy.typeName = String(typeId);
                }
            })());
        }

        await Promise.all(tasks);
        return policy;
    }


    async function load(id) {
        console.log('=== LOADING POLICY ===');
        console.log('Loading policy with ID:', id);
        console.log('Current URL:', window.location.href);
        console.log('Document ready state:', document.readyState);
        
        const container = document.getElementById('policyViewContainer');
        const titleElement = document.getElementById('policyTitle');
        const breadcrumbElement = document.getElementById('policyBreadcrumb');
        
        console.log('Container found:', container);
        console.log('Title element found:', titleElement);
        console.log('Breadcrumb element found:', breadcrumbElement);
        
        if (!container) {
            console.error('policyViewContainer not found!');
            console.error('Available elements with IDs:', Array.from(document.querySelectorAll('[id]')).map(el => el.id));
            return;
        }
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading...</div>';
        
        try {
            console.log('Calling API: getPolicyById(' + id + ')');
            
            // Check if API service is available
            console.log('=== API SERVICE CHECK ===');
            console.log('BUDG_API_SERVICE available:', !!window.BUDG_API_SERVICE);
            console.log('BUDG_API_SERVICE type:', typeof window.BUDG_API_SERVICE);
            
            if (!window.BUDG_API_SERVICE) {
                throw new Error('BUDG_API_SERVICE not available');
            }
            
            console.log('getPolicyById method available:', typeof window.BUDG_API_SERVICE.getPolicyById);
            console.log('getPolicyById method type:', typeof window.BUDG_API_SERVICE.getPolicyById);
            
            if (typeof window.BUDG_API_SERVICE.getPolicyById !== 'function') {
                throw new Error('getPolicyById method not available');
            }
            
            const data = await window.BUDG_API_SERVICE.getPolicyById(id);
            console.log('=== FULL API RESPONSE ===');
            console.log('API response:', data);
            console.log('Response type:', typeof data);
            console.log('Is array:', Array.isArray(data));
            
            if (data && typeof data === 'object') {
                console.log('Available fields:', Object.keys(data));
                console.log('Policy type:', data.policy_type);
                console.log('Status:', data.status);
                console.log('Lifecycle status:', data.lifecycle_status);
                console.log('Is public:', data.isPublic);
                console.log('Description:', data.description);
                console.log('Ref number:', data.refNumber);
                console.log('Internal:', data.internal);
            }
            
            // Check if data is wrapped in another object
            if (data && data.data) {
                console.log('Data wrapped in .data property:', data.data);
            }
            
            // Extract actual data - handle different response formats
            let actualData = data;
            if (data && data.data) {
                actualData = data.data;
                console.log('Using data.data as actual data');
            } else if (data && data.result) {
                actualData = data.result;
                console.log('Using data.result as actual data');
            } else if (data && data.policy) {
                actualData = data.policy;
                console.log('Using data.policy as actual data');
            } else if (data && data.response) {
                actualData = data.response;
                console.log('Using data.response as actual data');
            } else if (data && data.body) {
                actualData = data.body;
                console.log('Using data.body as actual data');
            }
            
            // If actualData is still an array, take the first element
            if (Array.isArray(actualData) && actualData.length > 0) {
                actualData = actualData[0];
                console.log('Using first array element as actual data');
            }
            
            console.log('Final data to use:', actualData);
            console.log('actualData type:', typeof actualData);
            console.log('actualData is null/undefined:', actualData === null || actualData === undefined);
            
            if (actualData && typeof actualData === 'object') {
                console.log('actualData keys:', Object.keys(actualData));
                console.log('actualData.policy_type:', actualData.policy_type);
                console.log('actualData.status:', actualData.status);
                console.log('actualData.lifecycle_status:', actualData.lifecycle_status);
                console.log('actualData.isPublic:', actualData.isPublic);
                console.log('actualData.description:', actualData.description);
                console.log('actualData.refNumber:', actualData.refNumber);
            }
            
            // Resolve foreign key references
            console.log('=== RESOLVING REFERENCES ===');
            actualData = await resolveReferences(actualData);
            console.log('References resolved, final data:', actualData);
            
            try { 
                window.BUDG_API_SERVICE.logVisit({  entity: 'Policy', entityId: String(id), route: `/view/policy/${id}` });
            } catch(_) {}

            // Update header with policy data
            if (titleElement) {
                titleElement.textContent = actualData.primaryName || actualData.name || vT('facet.policy', 'Policy');
            }
            if (breadcrumbElement) {
                breadcrumbElement.textContent = vT('facet.policy', 'Policy');
            }

            // Field values (debug only when needed)
            if (typeof window.__I18N_DEBUG__ !== 'undefined' && window.__I18N_DEBUG__) {
                console.log('=== FIELD VALUES DEBUG ===', {
                    url: actualData.url,
                    createdByName: actualData.createdByName || actualData.created_by_name,
                    lastUpdatedByName: actualData.lastUpdatedByName || actualData.last_updated_by_name
                });
            }

            const left = `
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${escapeHtml(vT('glossary.sections.definition', 'DEFINITION'))}</div>
                            ${renderItem(vT('label.description', 'Description'), _richHtml(actualData.description))}
                            ${renderItem(vT('label.ref', 'Ref.'), escapeHtml(actualData.refNumber || actualData.ref_number))}
                            ${renderItem(vT('label.internal', 'Internal'), actualData.internal ? vT('common.yes', 'Yes') : vT('common.no', 'No'))}
                            ${renderItem(vT('label.type', 'Type'), escapeHtml(actualData.typeName || actualData.policyType || actualData.policy_type || actualData.type || vT('value.unknown', 'Unknown Type')))}
                            ${renderItem(vT('label.url', 'URL'), actualData.url ? `<a href="${escapeHtml(actualData.url)}" target="_blank">${escapeHtml(actualData.url)}</a>` : '<span class="empty">—</span>')}
                            ${renderItem(vT('label.effectiveDate', 'Effective Date'), renderDateOnly(actualData.effectiveDate || actualData.effective_date))}
                            ${renderItem(vT('label.endDate', 'End Date'), renderDateOnly(actualData.endDate || actualData.end_date))}
                </div>
            `;

            const right = `
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${escapeHtml(vT('glossary.sections.classifications', 'CLASSIFICATIONS'))}</div>
                            ${renderItem(vT('glossary.labels.budgStatus', 'BUDG Status'), renderStatusBadge(actualData.statusName || actualData.status || vT('value.unknown', 'Unknown'), (actualData.statusName || actualData.status) === 'Active'))}
                            ${renderItem(vT('glossary.labels.lifecycle', 'Lifecycle'), renderLifecycleBadge(actualData.lifecycleName || actualData.lifecycleStatus || actualData.Lifecycle_Status || actualData.lifecycle_status || vT('value.unknown', 'Unknown')))}
                            ${renderItem(vT('glossary.labels.budgViewing', 'BUDG Viewing'), renderPublicStatus(actualData.viewingName || actualData.isPublic || actualData.is_public))}
                        </div>
                <div class="view-section" style="padding: 1.25rem;">
                    <div class="section-title">${escapeHtml(vT('glossary.sections.otherInformation', 'OTHER INFORMATION'))}</div>
                            ${renderItem(vT('glossary.labels.createdBy', 'Created By'), renderUserLink(actualData.createdByName || actualData.created_by_name, actualData.createdById || actualData.createdBy_ID || actualData.created_by_id))}
                            ${renderItem(vT('glossary.labels.created', 'Created'), renderDate(actualData.createDatetime || actualData.create_datetime || actualData.createdDate))}
                            ${renderItem(vT('glossary.labels.lastUpdatedBy', 'Last Updated By'), renderUserLink(actualData.lastUpdatedByName || actualData.last_updated_by_name, actualData.lastUpdateUserId || actualData.lastUpdatedUser_ID || actualData.last_updated_user_id))}
                            ${renderItem(vT('glossary.labels.lastUpdated', 'Last Updated'), renderDate(actualData.lastUpdateDatetime || actualData.last_update_datetime || actualData.lastUpdatedDate))}
                            ${renderItem(vT('glossary.labels.segment', 'Segment'), escapeHtml(actualData.segmentName || actualData.segment_name || actualData.segment || ((actualData.segmentId ?? actualData.segment_id ?? actualData.Segment_ID) != null ? vT('common.segmentIdDisplay', 'ID {id}', { id: actualData.segmentId ?? actualData.segment_id ?? actualData.Segment_ID }) : vT('message.notSpecified', 'Not specified'))))}
                </div>
            `;


            // Use view-grid which handles the layout properly - it will stack sections vertically
            // Then wrap in a grid for two-column layout
            // Ensure no overflow constraints that would hide content
            const gridWrapper = `
                <div style="display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 1.25rem; align-items: start; width: 100%; box-sizing: border-box; overflow: visible;">
                    <div style="display: flex; flex-direction: column; gap: 1.25rem; width: 100%; min-width: 0; box-sizing: border-box; overflow: visible; min-height: fit-content;">
                        ${left}
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 1.25rem; width: 100%; min-width: 0; box-sizing: border-box; overflow: visible; min-height: fit-content;">
                        ${right}
                    </div>
                </div>
            `;
            
            container.innerHTML = gridWrapper;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Policy',
                        containerId: 'policyViewContainer',
                        objectId: id,
                        title: vT('card.customFields', 'CUSTOM FIELDS')
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

        } catch (e) {
            console.error('=== ERROR LOADING POLICY ===');
            console.error('Error message:', e.message);
            console.error('Error stack:', e.stack);
            console.error('Error type:', typeof e);
            console.error('Full error object:', e);

            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const errorMessage = isForbidden
                ? 'This object is not available.'
                : `Failed to load policy (id=${id}). Error: ${e.message}`;
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column: 1/-1; color: var(--danger, #b91c1c);\">${errorMessage}</div>`;
            return null;
        }
    }

    async function loadPolicyImpactData(id) {
        const container = document.getElementById('policyImpactContainer');
        if (!container) return Promise.resolve();
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading impact data...</div>';
        
        try {
            // Load impact data from API
            const impactData = await window.BUDG_API_SERVICE.getPolicyImpact(id);
            
            if (!impactData || impactData.length === 0) {
                container.innerHTML = `
                    <div class="view-section" style="grid-column: 1/-1;">
                        <div class="section-title">IMPACT ANALYSIS</div>
                        <div class="empty">No impact data available for this policy.</div>
                    </div>
                `;
            }

            const impactHtml = impactData.map(impact => `
                <div class="view-item">
                    <div class="view-label">${escapeHtml(impact.type)}</div>
                    <div class="view-value">${escapeHtml(impact.description)}</div>
                </div>
            `).join('');

            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1;">
                    <div class="section-title">IMPACT ANALYSIS</div>
                    ${impactHtml}
                </div>
            `;
        } catch (e) {
            container.innerHTML = '<div class="view-section" style="grid-column: 1/-1; color: var(--danger, #b91c1c);">Failed to load impact data.</div>';
        }
    }

    // Function to load policy relationships data
    async function loadPolicyRelationshipsData(policyId) {
        const tbody = document.getElementById('relationshipsTableBody');
        const footer = document.querySelector('#relationshipsContent .hierarchy-footer');
        
        if (!tbody) return Promise.resolve();
        
        console.log('Loading relationships for policy ID:', policyId);
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
            
            // Fetch relationships where this policy is the SOURCE
            console.log('Calling API: getPolicyRelationshipsBySourceId(' + policyId + ')');
            const relationships = await window.BUDG_API_SERVICE.getPolicyRelationshipsBySourceId(policyId);
            console.log('API Response:', relationships);
            
            if (!Array.isArray(relationships) || relationships.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">No relationships found</td></tr>';
                if (footer) footer.textContent = '0 records';
            }

            // Render relationships table
            tbody.innerHTML = relationships.map(function(rel) {
                return `
                    <tr>
                        <td>
                            <span class="view-badge">${escapeHtml(rel.relationTypeName || 'Unknown')}</span>
                        </td>
                        <td>
                            <div class="policy-item">
                                <i class="fas fa-file-alt policy-icon"></i>
                                <span>${escapeHtml(rel.targetPolicyName || 'Unknown')}</span>
                            </div>
                        </td>
                        <td>
                            <span class="description-text">${escapeHtml(rel.description || 'No description')}</span>
                        </td>
                        <td>
                            <button type="button" class="btn btn-sm btn-secondary" onclick="viewPolicy(${rel.targetPolicyId})">
                                <i class="fas fa-eye"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');

            if (footer) {
                footer.textContent = `${relationships.length} record${relationships.length !== 1 ? 's' : ''}`;
            }

        } catch (error) {
            console.error('Failed to load relationships:', error);
            console.error('Error details:', {
                message: error.message,
                status: error.status,
                body: error.body
            });
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ' + error.message + '</td></tr>';
            if (footer) footer.textContent = '0 records';
        }
    }

    // Function to view policy details
    function viewPolicy(policyId) {
        window.location.href = `/view/policy/${policyId}`;
    }

    // Function to delete relationship
    async function deletePolicyRelationship(relationshipId) {
        if (!confirm('Are you sure you want to delete this relationship?')) {
            return;
        }

        try {
            await window.BUDG_API_SERVICE.deletePolicyRelationship(relationshipId);
            
            // Reload relationships data
        const id = parseId();
            if (id) {
                loadPolicyRelationshipsData(id);
            }
            
            // Show success message
            alert('Relationship deleted successfully');
        } catch (error) {
            console.error('Failed to delete relationship:', error);
            alert('Failed to delete relationship: ' + (error.message || 'Unknown error'));
        }
    }

    // Function to load datasets data for DATA tab
    async function loadPolicyDatasetsData(policyId) {
        const tbody = document.getElementById('dataSetsTableBody');
        const footer = document.getElementById('dataSetsFooter');
        
        console.log('Loading datasets for policy ID:', policyId);
        console.log('Table body element:', tbody);
        console.log('Footer element:', footer);
        
        if (!tbody || !footer) {
            console.error('Required elements not found for datasets table');
        }

        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
            
            // Fetch datasets for this policy
            console.log('Calling API: getDatasetsByPolicyId(' + policyId + ')');
            const datasets = await window.BUDG_API_SERVICE.getDatasetsByPolicyId(policyId);
            console.log('API Response:', datasets);
            
            if (!Array.isArray(datasets) || datasets.length === 0) {
                tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">No datasets found for this policy</td></tr>';
                footer.textContent = '0 records';
            }

            // Render datasets table
            tbody.innerHTML = datasets.map(function(dataset) {
                return `
                    <tr>
                        <td>
                            <div class="data-item">
                                <i class="fas fa-database" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                <span>${escapeHtml(dataset.systemName || 'Unknown System')}</span>
                            </div>
                        </td>
                        <td>${escapeHtml(dataset.refNumber || '-')}</td>
                        <td>
                            <div class="data-item">
                                <i class="fas fa-layer-group" style="color: var(--secondary-color, #248567); margin-right: 0.5rem;"></i>
                                <span>${escapeHtml(dataset.primaryName || 'Unknown Dataset')}</span>
                            </div>
                        </td>
                        <td>${escapeHtml(dataset.definition || '-')}</td>
                    </tr>
                `;
            }).join('');

            footer.textContent = `${datasets.length} record${datasets.length !== 1 ? 's' : ''}`;

        } catch (error) {
            console.error('Failed to load datasets:', error);
            console.error('Error details:', {
                message: error.message,
                status: error.status,
                body: error.body
            });
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load datasets: ' + error.message + '</td></tr>';
            footer.textContent = '0 records';
        }
    }

    // Function to load policy hierarchy data
    async function loadPolicyHierarchyData(policyId) {
        const tbody = document.getElementById('relationshipsTbody');
        const footer = document.getElementById('hierarchyFooter');
        
        if (!tbody || !footer) return Promise.resolve();
        
        try {
            const hierarchy = await window.BUDG_API_SERVICE.getPolicyHierarchy(policyId);
            
            if (!Array.isArray(hierarchy) || hierarchy.length === 0) {
                tbody.innerHTML = `<tr><td colspan="3" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data available</td></tr>`;
                footer.textContent = '0 records';
            }

            // Find the current policy (level 0) and organize hierarchy
            const currentPolicy = hierarchy.find(item => item.level === 0);
            const parentPolicies = hierarchy.filter(item => item.level < 0);
            const childPolicies = hierarchy.filter(item => item.level > 0);
            
            const hierarchyGroups = [];
            
            if (parentPolicies.length > 0) {
                parentPolicies.forEach(function(parent) {
                    hierarchyGroups.push({
                        parent: parent,
                        children: [],
                        expanded: true,
                        isParent: true
                    });
                });
            }
            
            if (currentPolicy) {
                hierarchyGroups.push({
                    parent: currentPolicy,
                    children: childPolicies,
                    expanded: true,
                    isCurrent: true
                });
            }

            tbody.innerHTML = hierarchyGroups.map(function(group, groupIndex) {
                const hasChildren = group.children.length > 0;
                const expandIconClass = hasChildren ? 'expand-icon' : 'fas fa-chevron-right';
                const expandState = hasChildren ? (group.expanded ? 'expanded' : 'collapsed') : '';
                const isCurrentPolicy = group.isCurrent;
                const isParentPolicy = group.isParent;
                
                let html = `
                    <tr class="hierarchy-parent ${isCurrentPolicy ? 'current-policy' : ''} ${isParentPolicy ? 'parent-policy' : ''}" data-group="${groupIndex}">
                        <td>
                            <div class="hierarchy-item">
                                <i class="fas fa-chevron-${hasChildren ? 'down' : 'right'} ${expandIconClass} ${expandState}" ${hasChildren ? 'data-group="' + groupIndex + '"' : ''}></i>
                                 <i class="fas fa-file-alt policy-icon"></i>
                                 <span class="parent-name ${isCurrentPolicy ? 'current-name' : ''} ${isParentPolicy ? 'parent-name-style' : ''}">${escapeHtml(group.parent.displayName || group.parent.name || '')}</span>
                                 ${isParentPolicy ? '<span class="relationship-label">(Parent Policy)</span>' : ''}
                            </div>
                        </td>
                        <td>${escapeHtml(group.parent.typeName || '')}</td>
                        <td>${escapeHtml(group.parent.description || '')}</td>
                    </tr>
                `;
                
                if (hasChildren && group.expanded) {
                    group.children.forEach(function(child, childIndex) {
                        const isLastChild = childIndex === group.children.length - 1;
                        html += `
                            <tr class="hierarchy-child hierarchy-row" data-group="${groupIndex}">
                                <td>
                                    <div class="hierarchy-item">
                                        <div class="hierarchy-connector">
                                            <div class="connector-line ${isLastChild ? 'last-child' : ''}"></div>
                                            <div class="connector-l-shape"></div>
                                        </div>
                                         <i class="fas fa-file-alt policy-icon"></i>
                                         <span class="child-name">${escapeHtml(child.displayName || child.name || '')}</span>
                                         <span class="relationship-label">(Child of: ${escapeHtml(group.parent.name || '')})</span>
                                    </div>
                                </td>
                                <td>${escapeHtml(child.typeName || '')}</td>
                                <td>${escapeHtml(child.description || '')}</td>
                            </tr>
                        `;
                    });
                }
                
                return html;
            }).join('');

            // Add click event listeners for expand/collapse
            const expandIcons = tbody.querySelectorAll('.expand-icon');
            expandIcons.forEach(function(icon) {
                icon.addEventListener('click', function() {
                    const groupIndex = parseInt(this.getAttribute('data-group'));
                    const group = hierarchyGroups[groupIndex];
                    
                    if (!group) return;
                    
                    group.expanded = !group.expanded;
                    
                    if (group.expanded) {
                        this.className = 'fas fa-chevron-down expand-icon expanded';
                    } else {
                        this.className = 'fas fa-chevron-right expand-icon collapsed';
                    }
                    
                    const childRows = tbody.querySelectorAll(`tr[data-group="${groupIndex}"].hierarchy-child`);
                    childRows.forEach(function(row) {
                        if (group.expanded) {
                            row.classList.remove('collapsed');
                        } else {
                            row.classList.add('collapsed');
                        }
                    });
                });
            });

            footer.textContent = `${hierarchy.length} record${hierarchy.length !== 1 ? 's' : ''}`;
        } catch (e) {
            console.error('Failed to load hierarchy:', e);
            tbody.innerHTML = `<tr><td colspan="3" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data</td></tr>`;
            footer.textContent = '0 records';
        }
    }

    // Function to setup policy sub-tabs
    function setupPolicySubTabs(policyId) {
        const subTabs = document.querySelectorAll('.sub-tab');
        subTabs.forEach(function(btn){
            btn.addEventListener('click', function(){
                subTabs.forEach(function(b){ b.classList.remove('active'); });
                this.classList.add('active');

                const subTabType = this.getAttribute('data-sub-tab');
                const hierarchyContent = document.getElementById('hierarchyContent');
                const relationshipsContent = document.getElementById('relationshipsContent');
                
                if (subTabType === 'hierarchy') {
                    if (hierarchyContent) hierarchyContent.classList.add('active');
                    if (relationshipsContent) relationshipsContent.classList.remove('active');
                } else if (subTabType === 'relationships') {
                    if (hierarchyContent) hierarchyContent.classList.remove('active');
                    if (relationshipsContent) relationshipsContent.classList.add('active');
                    
                    loadPolicyRelationshipsData(policyId);
                }
            });
        });
    }

    // Function to load policy components hierarchy data - using same logic as glossary
    async function loadPolicyComponentsHierarchy(policyId) {
        console.log('[Policy Hierarchy] Loading policy hierarchy for ID:', policyId);
        const tbody = document.getElementById('componentsHierarchyTbody');
        const footer = document.getElementById('componentsHierarchyFooter');
        
        if (!tbody || !footer) {
            console.log('[Policy Hierarchy] Missing elements: tbody or footer');
            return;
        }
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading policy hierarchy...</td></tr>';
            footer.textContent = 'Loading...';
            
            // Fetch hierarchy data from API (includes ancestors, current, siblings, children, and siblings' children)
            console.log('[Policy Hierarchy] Fetching hierarchy for policy ID:', policyId);
            const hierarchyData = await window.BUDG_API_SERVICE.getPolicyHierarchy(policyId);
            console.log('[Policy Hierarchy] Raw API response:', hierarchyData);
            
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
            
            console.log('[Policy Hierarchy] Processed items:', items);
            console.log('[Policy Hierarchy] Items count:', items.length);
            
            if (items.length === 0) {
                console.warn('[Policy Hierarchy] No hierarchy data found for policy ID:', policyId);
                tbody.innerHTML = '<tr><td colspan="2" style="color:var(--text-muted,#9ca3af);padding:1rem;text-align:center;">No hierarchy data found</td></tr>';
                footer.textContent = '0 records';
                return;
            }

            // Build hierarchy tree structure from flat data with levels
            // Organize by relation type: ancestors (negative levels), current (level 0), siblings (level 0), descendants (positive levels), sibling_children (positive levels)
            const byId = new Map();
            const childrenMap = new Map();
            
            items.forEach(item => {
                const id = item.id;
                const parentId = item.parentId;
                byId.set(id, item);
                if (!childrenMap.has(parentId)) childrenMap.set(parentId, []);
                childrenMap.get(parentId).push(item);
            });
            
            // Find current policy to determine its parent and calculate base depth
            const currentPolicy = items.find(item => item.relation === 'current');
            const currentParentId = currentPolicy ? currentPolicy.parentId : null;
            
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
                    // Ancestors: convert negative level to positive depth (most negative = depth 0)
                    return ancestorDepthOffset + item.level; // -1 becomes (offset-1), -2 becomes (offset-2), etc.
                } else if (item.relation === 'current') {
                    return currentDepth;
                } else if (item.relation === 'sibling') {
                    return currentDepth; // Same depth as current
                } else if (item.relation === 'descendant') {
                    // Descendants: current depth + level (1, 2, 3...)
                    return currentDepth + item.level;
                } else if (item.relation === 'sibling_child') {
                    // Sibling children: sibling depth + level (1, 2, 3...)
                    return currentDepth + item.level;
                }
                return 0;
            }
            
            // Build proper hierarchical tree structure using parent-child relationships
            // This ensures children appear directly under their parents, not just sorted by level
            function buildHierarchicalOrder(items, currentPolicyId) {
                const result = [];
                const processed = new Set();
                
                // Helper function to recursively add node and its children
                function addNodeAndChildren(nodeId, depth) {
                    if (processed.has(nodeId)) return;
                    processed.add(nodeId);
                    
                    const node = byId.get(nodeId);
                    if (!node) return;
                    
                    // Add current node
                    result.push({ node, depth, parentId: node.parentId });
                    
                    // Get and sort children by name
                    const children = (childrenMap.get(nodeId) || []).sort((a, b) => {
                        return (a.name || a.displayName || '').localeCompare(b.name || b.displayName || '');
                    });
                    
                    // Recursively add children
                    children.forEach(child => {
                        addNodeAndChildren(child.id, depth + 1);
                    });
                }
                
                // First, add ancestors in order (from root to current's parent)
                const ancestors = items.filter(item => item.relation === 'ancestor')
                    .sort((a, b) => a.level - b.level); // Most negative first (root ancestor)
                ancestors.forEach(ancestor => {
                    if (!processed.has(ancestor.id)) {
                        addNodeAndChildren(ancestor.id, calculateDepth(ancestor));
                    }
                });
                
                // Then add siblings (before current)
                const siblings = items.filter(item => item.relation === 'sibling')
                    .sort((a, b) => (a.name || a.displayName || '').localeCompare(b.name || b.displayName || ''));
                siblings.forEach(sibling => {
                    if (!processed.has(sibling.id)) {
                        addNodeAndChildren(sibling.id, calculateDepth(sibling));
                    }
                });
                
                // Then add current
                if (currentPolicy) {
                    addNodeAndChildren(currentPolicy.id, calculateDepth(currentPolicy));
                }
                
                // Then add descendants (children of current) - they will be added recursively
                const currentId = currentPolicy ? currentPolicy.id : currentPolicyId;
                const descendants = items.filter(item => item.relation === 'descendant')
                    .filter(item => item.parentId === currentId); // Only direct children
                descendants.forEach(descendant => {
                    if (!processed.has(descendant.id)) {
                        addNodeAndChildren(descendant.id, calculateDepth(descendant));
                    }
                });
                
                // Finally, add sibling children (children of siblings) - they will be added recursively
                const siblingChildren = items.filter(item => item.relation === 'sibling_child');
                const siblingIds = siblings.map(s => s.id);
                siblingChildren
                    .filter(item => siblingIds.includes(item.parentId)) // Only direct children of siblings
                    .forEach(child => {
                        if (!processed.has(child.id)) {
                            addNodeAndChildren(child.id, calculateDepth(child));
                        }
                    });
                
                return result;
            }
            
            // Build hierarchy rows using proper tree structure
            const hierarchyRowsData = buildHierarchicalOrder(items, policyId);
            
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
            
            console.log('[Policy Hierarchy] Processed hierarchy rows:', hierarchyRows);
            
            // Render the hierarchy
            const Mask = window.HierarchyMask;
            const rowsHtml = hierarchyRows.map(({ node, depth, hasChildren, relation }) => {
                const isMaskedNode = Mask ? Mask.isMasked(node) : false;
                const fallbackName = node.name || node.displayName || '';
                const fallbackDesc = node.description || '';
                const name = isMaskedNode ? Mask.PLACEHOLDER : fallbackName;
                const desc = isMaskedNode ? Mask.PLACEHOLDER : fallbackDesc;
                const isCurrent = relation === 'current';
                const id = node.id;
                const parentId = node.parentId || null;

                // Calculate visual depth (indentation)
                const visualDepth = depth;
                const indent = Array(visualDepth).fill('<span class="tree-indent"></span>').join('');
                const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
                const childCount = childrenMap.get(id)?.length || 0;
                const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
                const linkClass = isCurrent ? 'policy-link current-policy-link' : 'policy-link';
                const link = isMaskedNode
                    ? `<span class="${linkClass} masked-node" title="Restricted item"><i class="fas fa-lock masked-lock-icon" aria-hidden="true"></i>${escapeHtml(name)}</span>`
                    : `<a class="${linkClass}" href="/view/policy/${encodeURIComponent(id)}">${escapeHtml(name)}</a>`;
                const rowClasses = `${isCurrent ? 'current-row' : ''}${isMaskedNode ? ' masked-row' : ''}`.trim();

                return `<tr class="${rowClasses}" data-id="${id}" data-parent-id="${parentId || ''}" data-depth="${visualDepth}" data-relation="${relation}"${isMaskedNode ? ' data-masked="true"' : ''}>
                    <td><div class="tree-cell">${indent}${expander}${visualDepth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-file-alt item-icon"></i><span class="policy-name">${link}</span>${countBadge}</div></td>
                    <td><span title="${escapeHtml(desc)}">${escapeHtml(desc || '-')}</span></td>
                </tr>`;
            }).join('');

            tbody.innerHTML = rowsHtml;
            footer.textContent = `${hierarchyRows.length} record${hierarchyRows.length !== 1 ? 's' : ''}`;
            
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
            
            // Initialize interactions (same as glossary)
            initPolicyHierarchyInteractions(tbody.closest('.relationships-hierarchy'), hierarchyRowsObj);
        } catch (e) {
            console.error('[Policy Hierarchy] Failed to load hierarchy:', e);
            console.error('[Policy Hierarchy] Error stack:', e.stack);
            tbody.innerHTML = `<tr><td colspan="2" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load hierarchy data: ${e.message || 'Unknown error'}<br><small>Check console for details</small></td></tr>`;
            footer.textContent = '0 records';
        }
    }
    
    // Initialize policy hierarchy interactions (same as glossary)
    function initPolicyHierarchyInteractions(containerEl, hierarchyRows) {
        const collapsed = new Set();
        const parentMap = hierarchyRows.parentMap;
        const tbody = containerEl ? containerEl.querySelector('tbody') : document.getElementById('componentsHierarchyTbody');
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
                const btn = tr.querySelector('.tree-expander');
                if (btn) {
                    if (collapsed.has(String(id))) {
                        btn.classList.add('collapsed');
                        } else {
                        btn.classList.remove('collapsed');
                    }
                }
            });
        }
        
        tbody.addEventListener('click', (e) => {
            const button = e.target.closest('.tree-expander');
            if (button) {
                const tr = button.closest('tr');
                const id = tr.getAttribute('data-id');
                
                // Toggle collapsed state
                const wasCollapsed = collapsed.has(String(id));
                if (wasCollapsed) {
                    collapsed.delete(String(id));
                } else {
                    collapsed.add(String(id));
                }
                
                // Immediately update visibility
                updateVisibility();
            }
        });
        
        // Initial visibility update
        updateVisibility();
    }

    // Function to load policy components relationships data
    async function loadPolicyComponentsRelationships(policyId) {
        const tbody = document.getElementById('componentsRelationshipsTableBody');
        const footer = document.querySelector('#componentsRelationshipsContent .hierarchy-footer');
        
        if (!tbody) return;
        
        try {
            // Show loading state
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>';
            
            // Fetch relationships where this policy is the SOURCE
            const relationships = await window.BUDG_API_SERVICE.getPolicyRelationshipsBySourceId(policyId);
            
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
                            <div class="policy-item">
                                <i class="fas fa-file-alt policy-icon"></i>
                                <span>${escapeHtml(rel.targetPolicyName || 'Unknown')}</span>
                            </div>
                        </td>
                        <td>
                            <span class="description-text">${escapeHtml(rel.description || 'No description')}</span>
                        </td>
                        <td>
                            <button type="button" class="btn btn-sm btn-secondary" onclick="viewPolicy(${rel.targetPolicyId})">
                                <i class="fas fa-eye"></i>
                            </button>
                        </td>
                    </tr>
                `;
            }).join('');

            if (footer) {
                footer.textContent = `${relationships.length} record${relationships.length !== 1 ? 's' : ''}`;
            }

        } catch (error) {
            console.error('Failed to load components relationships:', error);
            tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">Failed to load relationships: ' + error.message + '</td></tr>';
            if (footer) footer.textContent = '0 records';
        }
    }



    // Make functions globally available
    window.viewPolicy = viewPolicy;
    window.deletePolicyRelationship = deletePolicyRelationship;

    // Initialize page
    document.addEventListener('DOMContentLoaded', function() {
        console.log('Policy page DOM loaded');
        
        // Check if required services are available
        console.log('BUDG_CONFIG available:', !!window.BUDG_CONFIG);
        console.log('BUDG_API_SERVICE available:', !!window.BUDG_API_SERVICE);
        
        if (window.BUDG_API_SERVICE) {
            console.log('API service methods:', Object.getOwnPropertyNames(Object.getPrototypeOf(window.BUDG_API_SERVICE)));
        }
        
        try {
            hideEditsIfUnauthenticated();
            console.log('Policy page loaded');
            
            const backBtn = document.getElementById('backBtn');
            console.log('Back button found:', backBtn);
            
            if (backBtn) {
                backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
            }
            
            // Edit button functionality
            const editBtn = document.getElementById('tabEditBtn');
            console.log('Edit button found:', editBtn);
            
            if (editBtn) {
                editBtn.addEventListener('click', function() {
                    const id = parseId();
                    if (id != null) {
                        // Get the currently active tab
                        const activeTab = document.querySelector('.tab.active');
                        const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                        console.log('Active tab:', tabName);
                        
                        // Navigate to edit page with tab parameter
                        window.location.href = `/view/policy/policy-edit.html?id=${id}&tab=${tabName}`;
                    }
                });
            }
            
            const id = parseId();
            console.log('Policy ID parsed:', id);
            
            if (id != null) {
                load(id).then(async function(data) {
                    const summaryContainer = document.getElementById('policyViewContainer');
                    if (summaryContainer && window.policyTabData) {
                        // Add small delay to ensure all styling is applied
                        setTimeout(function() {
                            window.policyTabData.summary = summaryContainer.innerHTML;
                        }, 100);
                    }

                    // Check and display lock status
                    if (window.ViewLockHelper) {
                        await window.ViewLockHelper.checkAndDisplayLockStatus('policy', id);
                    }

                    // Initialize unified edit dropdown
                    if (window.EditDropdown && id != null) {
                        try {
                            // Check if user can edit this object (has permission AND is stakeholder)
                            let canEditThisObject = false;
                            try {
                                if (window.checkCanEditObject) {
                                    const editCheck = await window.checkCanEditObject('Policy', id);
                                    canEditThisObject = editCheck.canEdit;
                                    console.log('[Policy] EditDropdown: User can edit object?', canEditThisObject, '(isStakeholder:', editCheck.isStakeholder, ')');
                                }
                            } catch (permError) {
                                console.error('[Policy] EditDropdown: Error checking permissions:', permError);
                                canEditThisObject = false; // Fail securely
                            }
                            
                            window.EditDropdown.initialize('policy', id, {
                                container: '.tab-actions',
                                editUrl: `/view/policy/policy-edit.html?id=${id}`,
                                hideEditOption: !canEditThisObject // Hide if user cannot edit
                            });
                        } catch (error) {
                            console.error('Failed to initialize edit dropdown:', error);
                        }
                    }

                    if (window.addFollowButton) {
                        try {
                            await window.addFollowButton('policy', id, null, '.form-actions');
                        } catch (e) {
                            console.error('Failed to initialize follow button for policy:', e);
                        }
                    }
                });
            } else {
                console.error('No valid policy ID found in URL');
            }
        } catch (error) {
            console.error('Error initializing policy page:', error);
        }

        // Tab data storage - make it global
        if (!window.policyTabData) {
            window.policyTabData = {
                summary: null,
                components: null,
                stakeholders: null,
                impact: null,
                data: null,
                history: null,
                change: null
            };
        }

        // Tabs: Summary vs Components vs other tabs
        const tabs = document.querySelectorAll('.tab-container .tab');
        console.log('Found tabs:', tabs.length);
        
        tabs.forEach(function(btn){
            btn.addEventListener('click', function(){
                console.log('Tab clicked:', this.textContent.trim());
                
                tabs.forEach(function(b){ b.classList.remove('active'); });
                this.classList.add('active');

                const body = document.querySelector('.content-body');
                console.log('Content body found:', body);
                
                if (!body) {
                    console.error('Content body not found!');
                    return;
                }

                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                
                // Hide all containers
                const containers = [
                    'policyViewContainer',
                    'policyComponentsContainer',
                    'policyStakeholdersContainer', 
                    'policyImpactContainer',
                    'policyDataContainer',
                    'policyHistoryContainer',
                    'policyChangeContainer'
                ];
                containers.forEach(function(containerId) {
                    const container = document.getElementById(containerId);
                    if (container) container.style.display = 'none';
                });
                
                if (which === 'summary') {
                    const summaryContainer = document.getElementById('policyViewContainer');
                    if (summaryContainer) {
                        summaryContainer.style.display = 'block';
                        
                        // Always reload data when switching to summary tab
                        const policyId = parseId();
                        if (policyId != null) {
                            load(policyId).then(function(data) {
                                // Add small delay to ensure all styling is applied
                                setTimeout(function() {
                                    window.policyTabData.summary = summaryContainer.innerHTML;
                                }, 100);
                            });
                        }
                        
                    }
                } else if (which === 'components') {
                    const componentsContainer = document.getElementById('policyComponentsContainer');
                    if (componentsContainer) {
                        componentsContainer.style.display = 'block';
                        
                        
                        // Always reload data when switching to components tab
                        const policyId = parseId();
                        if (policyId) {
                            console.log('Reloading components tab for policy ID:', policyId);
                            // Always reload hierarchy data
                            loadPolicyComponentsHierarchy(policyId);
                        }
                        
                        // Always reload components data
                        if (policyId) {
                            componentsContainer.innerHTML = `
                                <style>
                                    .relationships-sub-tabs {
                                        display: flex;
                                        border-bottom: 1px solid #e5e7eb;
                                        margin-bottom: 1rem;
                                    }
                                    
                                    .sub-tab {
                                        background: none;
                                        border: none;
                                        padding: 0.75rem 1.5rem;
                                        cursor: pointer;
                                        font-size: 0.875rem;
                                        font-weight: 500;
                                        color: #6b7280;
                                        border-bottom: 2px solid transparent;
                                        transition: all 0.2s ease;
                                    }
                                    
                                    .sub-tab:hover {
                                        color: #374151;
                                        background-color: #f9fafb;
                                    }
                                    
                                    .sub-tab.active {
                                        color: #059669;
                                        border-bottom-color: #059669;
                                        background-color: #f0fdf4;
                                    }
                                    
                                    .sub-tab-content {
                                        display: none;
                                    }
                                    
                                    .sub-tab-content.active {
                                        display: block;
                                    }
                                </style>
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div class="relationships-container">
                                        <div class="relationships-sub-tabs">
                                            <button class="sub-tab active" data-sub-tab="hierarchy">Hierarchy</button>
                                            <button class="sub-tab" data-sub-tab="relationships">Relationships</button>
                                        </div>
                                        <div class="relationships-content">
                                            <div id="componentsHierarchyContent" class="sub-tab-content active">
                                                <div class="relationships-hierarchy">
                                                    <div class="hierarchy-header">
                                                        <div class="hierarchy-title">POLICY HIERARCHY</div>
                                                        <div class="hierarchy-actions">
                                                            <label style="display:flex;align-items:center;gap:.5rem;font-size:.85rem;color:var(--text-muted,#6b7280);">
                                                                <input type="checkbox" checked style="margin-right:.3rem;">
                                                                Show Relationships
                                                            </label>
                                                            <button type="button" class="btn btn-secondary" id="componentsEditBtn"><i class="fas fa-cog"></i></button>
                                                        </div>
                                                    </div>
                                                    <table class="hierarchy-table">
                                                        <thead>
                                                            <tr>
                                                                <th><div class="th-content"><span>Name</span><i class="fas fa-sort"></i></div></th>
                                                                <th><div class="th-content"><span>Description</span><i class="fas fa-sort"></i></div></th>
                                                            </tr>
                                                        </thead>
                                                        <tbody id="componentsHierarchyTbody">
                                                            <tr><td colspan="2" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>
                                                        </tbody>
                                                    </table>
                                                    <div class="hierarchy-footer" id="componentsHierarchyFooter">
                                                        0 records
                                                    </div>
                                                </div>
                                            </div>
                                            <div id="componentsRelationshipsContent" class="sub-tab-content">
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
                                                                    <th><div class="th-content"><span>Relationship Type</span><i class="fas fa-sort"></i></div></th>
                                                                    <th><div class="th-content"><span>Target Policy</span><i class="fas fa-sort"></i></div></th>
                                                                    <th><div class="th-content"><span>Description</span><i class="fas fa-sort"></i></div></th>
                                                                    <th><div class="th-content"><span>Actions</span></div></th>
                                                                </tr>
                                                            </thead>
                                                            <tbody id="componentsRelationshipsTableBody">
                                                                <tr><td colspan="4" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">Loading...</td></tr>
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                    <div class="hierarchy-footer">
                                                        0 records
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            `;

                            // Load hierarchy data immediately
                            loadPolicyComponentsHierarchy(policyId);

                            // Add event listener for components edit button
                            const componentsEditBtn = document.getElementById('componentsEditBtn');
                            if (componentsEditBtn) {
                                componentsEditBtn.addEventListener('click', function() {
                                    console.log('Components edit button clicked');
                                    const id = parseId();
                                    if (id != null) {
                                        // Navigate to edit page with components tab
                                        window.location.href = `/view/policy/policy-edit.html?id=${id}&tab=components`;
                                    }
                                });
                            }

                            // Add sub-tab functionality
                            const subTabs = componentsContainer.querySelectorAll('.sub-tab');
                            subTabs.forEach(function(btn){
                                btn.addEventListener('click', function(){
                                    subTabs.forEach(function(b){ b.classList.remove('active'); });
                                    this.classList.add('active');

                                    const subTabType = this.getAttribute('data-sub-tab');
                                    const hierarchyContent = componentsContainer.querySelector('#componentsHierarchyContent');
                                    const relationshipsContent = componentsContainer.querySelector('#componentsRelationshipsContent');
                                    
                                    if (subTabType === 'hierarchy') {
                                        if (hierarchyContent) hierarchyContent.classList.add('active');
                                        if (relationshipsContent) relationshipsContent.classList.remove('active');
                                        
                                        // Load hierarchy data when switching to hierarchy tab
                                        loadPolicyComponentsHierarchy(policyId);
                                    } else if (subTabType === 'relationships') {
                                        if (hierarchyContent) hierarchyContent.classList.remove('active');
                                        if (relationshipsContent) relationshipsContent.classList.add('active');
                                        
                                        // Load relationships data when switching to relationships tab
                                        loadPolicyComponentsRelationships(policyId);
                                    }
                                });
                            });
                        }
                    }
                } else if (which === 'stakeholders') {
                    const stakeholdersContainer = document.getElementById('policyStakeholdersContainer');
                    if (stakeholdersContainer) {
                        stakeholdersContainer.style.display = 'block';
                        
                        // Always reload data when switching to stakeholders tab
                        const policyId = parseId();
                        if (policyId != null) {
                            // Initialize policy stakeholder view
                            console.log('Creating PolicyStakeholderView for policy with id:', policyId);
                            if (window.PolicyStakeholderView) {
                                window.PolicyStakeholderView.init(policyId);
                                window.policyTabData.stakeholders = stakeholdersContainer.innerHTML;
                            } else {
                                console.error('PolicyStakeholderView class not found');
                                stakeholdersContainer.innerHTML = '<div class="error">Policy stakeholder view not available</div>';
                            }
                        }
                    }
                } else if (which === 'impact') {
                    const impactContainer = document.getElementById('policyImpactContainer');
                    if (impactContainer) {
                        impactContainer.style.display = 'block';
                        
                        // Always reload data when switching to impact tab
                        const policyId = parseId();
                        if (policyId) {
                            if (typeof loadPolicyImpact === 'function') {
                                loadPolicyImpact(policyId);
                            } else {
                                console.error('loadPolicyImpact function not available');
                                impactContainer.innerHTML = '<div class="error">Policy impact view not available</div>';
                            }
                        }
                    }
                } else if (which === 'data') {
                    const dataContainer = document.getElementById('policyDataContainer');
                    if (dataContainer) {
                        dataContainer.style.display = 'block';
                        // Always reload data when switching to data tab
                        const policyId = parseId();
                        if (policyId != null && window.loadPolicyData) {
                            window.loadPolicyData(policyId);
                        } else if (policyId != null) {
                            dataContainer.innerHTML = '<div class="error">PolicyData view not available</div>';
                        }
                    }
                } else if (which === 'history') {
                    const historyContainer = document.getElementById('policyHistoryContainer');
                    if (historyContainer) {
                        historyContainer.style.display = 'block';
                        
                        // Always reload data when switching to history tab
                        const policyId = parseId();
                        if (policyId) {
                            console.log('Loading HISTORY tab for policy ID:', policyId);
                            
                            // Initialize history component for Policy
                            if (window.initializeHistoryComponent) {
                                window.initializeHistoryComponent('Policies', policyId, 'policyHistoryContainer');
                            } else {
                                console.error('History component not available');
                                historyContainer.innerHTML = `
                                    <div class="view-section" style="grid-column:1/-1;">
                                        <div class="section-title">HISTORY</div>
                                        <div class="empty">History component not available</div>
                                    </div>
                                `;
                            }
                        }
                    }
                } else if (which === 'change') {
                    const changeContainer = document.getElementById('policyChangeContainer');
                    if (changeContainer) {
                        changeContainer.style.display = 'grid';
                        
                        // Initialize change tab component
                        const policyId = parseId();
                        if (policyId && window.ChangeTabComponent) {
                            console.log('Loading CHANGE tab for policy ID:', policyId);
                            window.ChangeTabComponent.initialize('policy', policyId, 'policyChangeContainer');
                        } else if (policyId) {
                            changeContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div class="section-title">CHANGE MANAGEMENT</div>
                                    <div class="empty">Change management will be displayed here</div>
                                </div>
                            `;
                            window.policyTabData.change = changeContainer.innerHTML;
                        }
                    }
                } else {
                    // Default to summary
                    const summaryContainer = document.getElementById('policyViewContainer');
                    if (summaryContainer) {
                        summaryContainer.style.display = 'block';
                        const policyId = parseId();
                        if (window.policyTabData.summary === null && policyId != null) {
                            load(policyId).then(function(data) {
                                window.policyTabData.summary = summaryContainer.innerHTML;
                            });
                        } else if (window.policyTabData.summary !== null) {
                            summaryContainer.innerHTML = window.policyTabData.summary;
                        }
                    }
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('policyStakeholdersContainer');
                }
            });
        });
    });
})();