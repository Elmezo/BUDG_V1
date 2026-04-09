(function() {
    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('system-interface') !== -1 ? parts.indexOf('system-interface') : parts.indexOf('interface');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }

    function iT(key, fallback, params) {
        if (!window.I18n || typeof window.I18n.t !== 'function') return fallback != null ? fallback : key;
        const str = window.I18n.t(key, params);
        if (str === key && fallback != null) return fallback;
        return str;
    }

    function ifaceRecordCount(n) {
        const num = Number(n);
        if (num === 1) return iT('systemInterface.view.recordOne', '1 record');
        return iT('systemInterface.view.recordMany', '{n} records', { n: String(num) });
    }

    let _interfaceGuestCache = null;
    async function isGuestVisitorForInterface() {
        if (_interfaceGuestCache !== null) {
            return _interfaceGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _interfaceGuestCache = true;
                return _interfaceGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _interfaceGuestCache = !isAuthenticated || isGuestRole;
            return _interfaceGuestCache;
        } catch (_) {
            _interfaceGuestCache = true;
            return _interfaceGuestCache;
        }
    }

    function renderInterfaceGuestAccessDenied(container, interfaceId, segmentName, segmentId) {
        if (!container) return;
        const title = iT('error.segmentAccess.title', 'Cannot access this item');
        const message = iT('error.segmentAccess.message', 'This item is not available for guest users.');
        const code = 'ERR-SEGMENT-NOT-ENTERPRISE';
        const segmentLabel = segmentName || ((segmentId !== undefined && segmentId !== null)
            ? iT('common.segmentIdDisplay', 'ID {id}', { id: String(segmentId) })
            : iT('common.unknown', 'Unknown'));
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
                    <div style="margin-bottom: 0.5rem;">${esc(iT('label.segment', 'Segment'))}: <strong>${esc(segmentLabel)}</strong></div>
                    <div>${esc(iT('systemInterface.view.errorCodeLabel', 'Error code:'))} <code>${code}</code></div>
                </div>
            </div>
        `;
    }

    // Get active tab from URL parameters
    function getActiveTab() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('tab') || 'summary';
    }

    // Get active subtab from URL parameters
    function getActiveSubTab() {
        const urlParams = new URLSearchParams(window.location.search);
        return urlParams.get('subtab');
    }

    // Update URL for tab and subtab
    function updateURLForTab(tabName, subTabName = null) {
        const id = parseId();
        if (!id) return;
        const url = new URL(window.location);
        url.searchParams.set('tab', tabName);
        if (subTabName) {
            url.searchParams.set('subtab', subTabName);
        } else {
            url.searchParams.delete('subtab');
        }
        window.history.pushState({ tab: tabName, subtab: subTabName }, '', url);
    }

    // Set active tab based on URL parameter
    function setActiveTab() {
        const activeTabName = getActiveTab();
        const targetTab = document.querySelector(`[data-tab="${activeTabName}"]`);

        if (targetTab) {
            // Remove active class from all tabs
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            // Add active class to target tab
            targetTab.classList.add('active');

            // Load content for the active tab
            loadTabContent(activeTabName);
        }
    }

    // Load content for specific tab in view mode
    function loadTabContent(tabName) {
        const id = parseId();
        if (!id) return;
        
        const container = document.getElementById('interfaceViewContainer');
        const stakeholdersContainer = document.getElementById('interfaceStakeholdersContainer');
        const impactContainer = document.getElementById('interfaceImpactContainer');
        const changeContainer = document.getElementById('interfaceChangeContainer');
        
        if (!container) return;
        
        // Hide all containers first
        container.style.display = 'grid';
        if (stakeholdersContainer) stakeholdersContainer.style.display = 'none';
        if (impactContainer) impactContainer.style.display = 'none';
        if (changeContainer) changeContainer.style.display = 'none';
        
        switch(tabName) {
            case 'stakeholders':
                // Hide main container and show stakeholders container
                container.style.display = 'none';
                if (stakeholdersContainer) {
                    stakeholdersContainer.style.display = 'block';
                    // Load stakeholders in view mode
                    if (window.InterfaceStakeholderView) {
                        window.InterfaceStakeholderView.init(id);
                    }
                }
                break;
            case 'impact':
                // Hide main container and show impact container
                container.style.display = 'none';
                if (impactContainer) {
                    impactContainer.style.display = 'block';
                    // Load impact in view mode
                    if (window.loadInterfaceImpact) {
                        window.loadInterfaceImpact(id);
                    }
                }
                break;
            case 'data':
                // Load glossary data for the interface
                // The loadDataTabContent function will handle subtab activation from URL
                loadDataTabContent(id, container);
                break;
            case 'history':
                container.innerHTML = `
                    <div class="view-section" style="grid-column:1/-1;">
                        <div id="interfaceHistoryContainer"></div>
                    </div>
                `;
                // Initialize history component for system interface
                if (window.HistoryComponent && id) {
                    console.log('Initializing history component for System Interface with ID:', id);
                    console.log('HistoryComponent available:', !!window.HistoryComponent);
                    console.log('HistoryComponent methods:', Object.keys(window.HistoryComponent));
                    window.HistoryComponent.initialize('System Interface', id, 'interfaceHistoryContainer');
                } else {
                    console.error('HistoryComponent not available or ID not found:', { HistoryComponent: !!window.HistoryComponent, id });
                }
                break;
            case 'change':
                // Hide main container, show change container
                container.style.display = 'none';
                if (stakeholdersContainer) stakeholdersContainer.style.display = 'none';
                if (impactContainer) impactContainer.style.display = 'none';
                
                if (changeContainer) {
                    changeContainer.style.display = 'grid';
                    if (window.ChangeTabComponent && id) {
                        window.ChangeTabComponent.initialize('system-interface', id, 'interfaceChangeContainer');
                    } else {
                        changeContainer.innerHTML = `
                            <div class="view-section" style="grid-column:1/-1;">
                                <div class="section-title">${escapeHtml(iT('systemInterface.view.changeSection', 'CHANGE'))}</div>
                                <div class="empty-state">
                                    <i class="fas fa-clipboard-list"></i>
                                    <p>${escapeHtml(iT('systemInterface.view.changeNoData', 'No change data available for this interface.'))}</p>
                                </div>
                            </div>
                        `;
                    }
                }
                break;
            case 'summary':
            default:
                // Show main container and load summary content
                container.style.display = 'grid';
                if (stakeholdersContainer) stakeholdersContainer.style.display = 'none';
                load(id);
                break;
        }
    }
function hideEditControls() {
		// Hide the edit dropdown for unauthorized users
		if (window.EditDropdown) {
			window.EditDropdown.hideEditControls();
		}
		// Hide the tab edit button for unauthorized users
		const tabEditBtn = document.getElementById('editBtn');
		if (tabEditBtn) tabEditBtn.style.display = 'none';
		try { document.querySelectorAll('[data-action="edit"]').forEach(function(el){ el.style.display = 'none'; }); } catch(_) {}
	}

	async function hideEditsIfUnauthenticated() {
		try {
			const id = parseId();
			if (!id) {
				console.log('[Interface] No ID found - hiding edit controls');
				hideEditControls();
				return;
			}

			// Use the shared utility function to check if user can edit this object
			if (window.checkCanEditObject) {
				const editCheck = await window.checkCanEditObject('Interface', id);
				const canEditObject = editCheck.canEdit;
				const isAdmin = editCheck.isAdmin;
				
				console.log('[Interface] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
				
				// Show the main edit dropdown only if user can edit this object
				if (canEditObject) {
					console.log('[Interface] User can edit this object - showing edit controls');
					showEditControls();
				} else {
					console.log('[Interface] User cannot edit this object - hiding edit controls');
					hideEditControls();
				}
				
				// Check delete permission
				const permResp = await fetch('/api/user/permissions/Interface', { method: 'GET', credentials: 'include' });
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
			const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin';
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
			'#deleteInterfaceBtn'
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
		const tabEditBtn = document.getElementById('editBtn');
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

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatDate(dateString) {
        if (!dateString) return '';
        try {
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return dateString; // Return original if invalid
            
            const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                          'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
            
            const day = date.getDate().toString().padStart(2, '0');
            const month = months[date.getMonth()];
            const year = date.getFullYear();
            
            return `${day}-${month}-${year}`;
        } catch (e) {
            return dateString; // Return original if formatting fails
        }
    }

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class=\"empty\">-</span>'}</div></div>`;
    }

    function renderPersonLink(name, id) {
        const cleanName = (name || '').trim();
        if (!cleanName) {
            return '<span class="empty">' + escapeHtml(iT('message.notSpecified', 'Not specified')) + '</span>';
        }
        if (!id) {
            return escapeHtml(cleanName);
        }
        return `<a href="/view/people/${encodeURIComponent(id)}" style="color: var(--secondary-color, #248567); text-decoration: none;">${escapeHtml(cleanName)}</a>`;
    }



    async function load(id) {
        const container = document.getElementById('interfaceViewContainer');
        const titleElement = document.getElementById('interfaceTitle');
        const breadcrumbElement = document.getElementById('interfaceBreadcrumb');
        
        // Only render summary content when on the summary tab
        const activeSummaryTab = getActiveTab() === 'summary';
        if (activeSummaryTab) {
            container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + escapeHtml(iT('message.loading', 'Loading...')) + '</div>';
        }
        try {
            let i = await window.BUDG_API_SERVICE.getInterfaceById(id);
            if (i && i.data) i = i.data;

            try {
                const isGuest = await isGuestVisitorForInterface();
                const segmentName = i?.segmentName || i?.segment_name || i?.segment;
                const segmentId = i?.segmentId ?? i?.segment_id ?? i?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Interface] Guest visitor blocked from non-Enterprise segment view', { interfaceId: id, segmentName, segmentId });
                    if (titleElement) {
                        titleElement.textContent = iT('error.segmentAccess.title', 'Cannot access this item');
                    }
                    if (breadcrumbElement) {
                        breadcrumbElement.innerHTML = '';
                    }
                    if (activeSummaryTab) {
                        renderInterfaceGuestAccessDenied(container, id, segmentName, segmentId);
                    }
                    return;
                }
            } catch (gateError) {
                console.warn('[Interface] Error during guest/segment access check, falling back to normal render:', gateError);
            }

            console.log('Interface data loaded:', i);
            console.log('All available fields:', Object.keys(i));
            console.log('System IDs in view:', {
                sourceSystemId: i.sourceSystemId,
                source_system_id: i.source_system_id,
                source_systemID: i.source_systemID,
                Source_systemID: i.Source_systemID,
                targetSystemId: i.targetSystemId,
                target_system_id: i.target_system_id,
                target_systemID: i.target_systemID,
                Target_systemID: i.Target_systemID,
                sourceName: i.sourceName,
                targetName: i.targetName
            });
            console.log('User data fields:', {
                createdByName: i.createdByName,
                lastUpdatedByName: i.lastUpdatedByName,
                created_datetime: i.created_datetime,
                last_updatedtime: i.last_updatedtime,
                createdBy_ID: i.createdBy_ID,
                last_updateuser_id: i.last_updateuser_id
            });
            
            // Update header with interface data
            if (titleElement) {
                titleElement.textContent = `${escapeHtml(i.ref || '')} - ${escapeHtml(i.name || '')}`;
            }
            if (breadcrumbElement) {
                breadcrumbElement.innerHTML = `
                    <span>${escapeHtml(iT('systemInterface.breadcrumb', 'Interface'))}</span>
                    <span class="separator">|</span>
                    <span class="source-system">${escapeHtml(i.sourceName || '')}</span>
                    <i class="fas fa-arrow-right breadcrumb-arrow"></i>
                    <span class="target-system">${escapeHtml(i.targetName || '')}</span>
                `;
            }

            const createdById = i.createdBy_ID || i.createdby_id || i.createdById || null;
            const lastUpdatedById = i.last_updateuser_id || i.lastUpdatedUser_ID || i.lastUpdateUserId || null;
            const createdByName = i.createdByName || '';
            const rawLastUpdatedByName = i.lastUpdatedByName || '';
            const isUnknownLastUpdatedBy = !rawLastUpdatedByName
                || /^unknown(\s+user)?$/i.test(rawLastUpdatedByName.trim());
            const lastUpdatedByName = isUnknownLastUpdatedBy ? '' : rawLastUpdatedByName;
            
            const definitionSection = `
                <div class="left-column">
                    <!-- DEFINITION Card -->
                    <div class="form-section">
                        <h3 class="section-title definition">${escapeHtml(iT('systemInterface.sections.definition', 'DEFINITION'))}</h3>
                        ${renderItem(escapeHtml(iT('systemInterface.labels.name', 'Name')), escapeHtml(i.name || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.ref', 'Ref.')), escapeHtml(i.ref_number || i.ref || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.sourceSystemShortName', 'Source System Short Name')), `<i class="fas fa-layer-group"></i> <a href="/view/system/${i.sourceSystemId || i.source_system_id || i.source_systemID || i.Source_systemID || ''}" style="color: var(--secondary-color, #248567); text-decoration: none;">${escapeHtml(i.sourceName || '')}</a>`)}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.targetSystemShortName', 'Target System Short Name')), `<i class="fas fa-layer-group"></i> <a href="/view/system/${i.targetSystemId || i.target_system_id || i.target_systemID || i.Target_systemID || ''}" style="color: var(--secondary-color, #248567); text-decoration: none;">${escapeHtml(i.targetName || '')}</a>`)}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.description', 'Description')), escapeHtml(i.description || ''))}
                    </div>

                    <!-- OPTIONAL FIELDS Card -->
                    <div class="form-section">
                        <h3 class="section-title optional-fields">${escapeHtml(iT('systemInterface.sections.optionalFields', 'OPTIONAL FIELDS'))}</h3>
                        ${renderItem(escapeHtml(iT('systemInterface.labels.synchronisationControl', 'Synchronisation Control')), i.syncControl ? escapeHtml(i.syncControl) : '<span class="empty">' + escapeHtml(iT('message.notSpecified', 'Not specified')) + '</span>')}
                    </div>
                </div>
            `;

            const classificationsSection = `
                <div class="right-column">
                    <!-- CLASSIFICATIONS Card -->
                    <div class="form-section">
                        <h3 class="section-title classifications">${escapeHtml(iT('systemInterface.sections.classifications', 'CLASSIFICATIONS'))}</h3>
                        ${renderItem(escapeHtml(iT('systemInterface.labels.budgStatus', 'BUDG Status')), `<span class="status-dot ${i.statusName ? 'active' : ''}"></span> ${escapeHtml(i.statusName || '')}`)}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.budgViewing', 'BUDG Viewing')), escapeHtml(i.viewingName || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.lifecycle', 'Lifecycle')), `<span class="lifecycle-badge">${escapeHtml(i.lifecycleName || '')}</span>`)}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.automation', 'Automation')), escapeHtml(i.automationName || ''))}
                    </div>

                    <!-- OTHER INFORMATION Card -->
                    <div class="form-section">
                        <h3 class="section-title classifications">${escapeHtml(iT('systemInterface.sections.otherInformation', 'OTHER INFORMATION'))}</h3>
                        ${renderItem(escapeHtml(iT('systemInterface.labels.frequency', 'Frequency')), escapeHtml(i.frequencyName || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.assetId', 'Asset ID')), escapeHtml(i.assetId || '') || '<span class="empty">' + escapeHtml(iT('message.notSpecified', 'Not specified')) + '</span>')}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.transferMethod', 'Transfer Method')), escapeHtml(i.transferMethodName || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.transferFormat', 'Transfer Format')), escapeHtml(i.transferFormatName || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.interfaceClassification', 'Interface Classification')), escapeHtml(i.classificationName || ''))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.createdBy', 'Created By')), renderPersonLink(createdByName, createdById))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.created', 'Created')), escapeHtml(formatDate(i.created_datetime) || iT('common.unknown', 'Unknown')))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.lastUpdatedBy', 'Last Updated By')), lastUpdatedByName ? renderPersonLink(lastUpdatedByName, lastUpdatedById || createdById) : '<span class="empty">-</span>')}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.lastUpdated', 'Last Updated')), escapeHtml(formatDate(i.last_updatedtime) || iT('common.unknown', 'Unknown')))}
                        ${renderItem(escapeHtml(iT('systemInterface.labels.segment', 'Segment')), escapeHtml(i.segmentName || i.segment_name || i.segment || ((i.segmentId ?? i.segment_id ?? i.Segment_ID) != null ? iT('common.segmentIdDisplay', 'ID {id}', { id: String(i.segmentId ?? i.segment_id ?? i.Segment_ID) }) : iT('message.notSpecified', 'Not specified'))))}
                    </div>
                </div>
            `;

            // Load interface x glossary data
            if (!activeSummaryTab) {
                // Non-summary tab: skip rendering summary content into container
                return;
            }
            try {
                const glossaryData = await window.BUDG_API_SERVICE.getInterfaceXGlossary(id);
                const dataContentSection = `
                    <div class="view-section">
                        <div class="section-title">${escapeHtml(iT('systemInterface.view.dataContentSummary', 'DATA CONTENT SUMMARY'))}</div>
                        ${glossaryData && glossaryData.length > 0 ? `
                            <div class="data-table-wrapper">
                                <table class="data-table">
                                    <thead>
                                        <tr>
                                            <th><div class="th-content"><span>${escapeHtml(iT('systemInterface.table.relationshipType', 'Relationship Type'))}</span></div></th>
                                            <th><div class="th-content"><span>${escapeHtml(iT('systemInterface.table.glossary', 'Glossary'))}</span></div></th>
                                            <th><div class="th-content"><span>${escapeHtml(iT('systemInterface.table.definition', 'Definition'))}</span></div></th>
                                            <th><div class="th-content"><span>${escapeHtml(iT('systemInterface.table.relationshipStatus', 'Relationship Status'))}</span></div></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${glossaryData.map(item => {
                                            const glossaryLink = item.glossaryId != null
                                                ? `<a href="/view/glossary/${encodeURIComponent(item.glossaryId)}" style="color: var(--primary-color, #007bff); text-decoration: none; margin-left: 0.5rem;" title="${escapeHtml(iT('systemInterface.view.viewGlossary', 'View glossary'))}">${escapeHtml(item.glossaryName || '')} <i class="fas fa-external-link-alt" style="font-size: 0.8em;"></i></a>`
                                                : escapeHtml(item.glossaryName || '');
                                            return `
                                            <tr>
                                                <td>${escapeHtml(item.relationshipType || '')}</td>
                                                <td><i class="fas fa-book-open"></i> ${glossaryLink}</td>
                                                <td>${escapeHtml(item.glossaryDefinition || '')}</td>
                                                <td>${escapeHtml(item.relationshipStatus || '')}</td>
                                            </tr>
                                        `;
                                        }).join('')}
                                    </tbody>
                                </table>
                            </div>
                            <div style="text-align: right; color: var(--text-tertiary, #adb5bd); font-size: 0.8rem; margin-top: 0.5rem;">
                                ${escapeHtml(ifaceRecordCount(glossaryData.length))}
                            </div>
                        ` : `
                            <div style="padding: 2rem; text-align: center; color: var(--text-secondary, #6c757d);">
                                <i class="fas fa-info-circle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                                <p>${escapeHtml(iT('systemInterface.view.noDataContentSummary', 'This system interface does not have a data content summary.'))}</p>
                            </div>
                        `}
                    </div>
                `;

                // Documents section
                const noDocsText = iT('message.noDocuments', 'No documents');
                const docsEmpty = '<div class="empty">' + escapeHtml(noDocsText) + '.</div>';
                const documentsTitle = iT('card.documents', 'DOCUMENTS');
                const documentsSection = `
                    <div class="view-section" style="grid-column: 1/-1; margin-top: 2rem;" data-collapsible>
                        <div class="collapsible-header" style="display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.5rem; background: var(--background-secondary, #f8f9fa); border: 1px solid var(--border-color, #e5e7eb); border-radius: 8px; cursor: pointer;">
                            <div style="display: flex; align-items: center; gap: 0.75rem;">
                                <i class="fa-solid fa-file-lines" style="color: var(--secondary-color, #248567);"></i>
                                <h3 style="margin: 0; font-size: 0.875rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">${escapeHtml(documentsTitle)}</h3>
                            </div>
                            <i class="fas fa-chevron-down" style="transition: transform 0.2s;"></i>
                        </div>
                        <div class="collapsible-body" style="display: none; padding: 1.5rem; border: 1px solid var(--border-color, #e5e7eb); border-top: none; border-radius: 0 0 8px 8px;">
                            ${docsEmpty}
                        </div>
                    </div>
                `;
                
                container.innerHTML = `
                    ${definitionSection}
                    ${classificationsSection}
                    ${dataContentSection}
                    ${documentsSection}
                `;
                
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
                
                // Render custom fields section
                if (window.CustomFields) {
                    try {
                        await window.CustomFields.renderViewSection({
                            facetId: 'System Interface',
                            containerId: 'interfaceViewContainer',
                            objectId: id,
                            title: iT('card.customFields', 'CUSTOM FIELDS')
                        });
                    } catch (error) {
                        console.error('Error rendering custom fields:', error);
                    }
                }
                
            } catch (glossaryError) {
                console.error('Error loading glossary data:', glossaryError);
                const dataContentSection = `
                    <div class="view-section">
                        <div class="section-title">${escapeHtml(iT('systemInterface.view.dataContentSummary', 'DATA CONTENT SUMMARY'))}</div>
                        <div style="padding: 2rem; text-align: center; color: var(--text-secondary, #6c757d);">
                            <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                            <p>${escapeHtml(iT('systemInterface.view.failedToLoadGlossaryData', 'Failed to load glossary data'))}</p>
                        </div>
                    </div>
                `;
                const noDocsTextErr = iT('message.noDocuments', 'No documents');
                const docsEmptyErr = '<div class="empty">' + escapeHtml(noDocsTextErr) + '.</div>';
                const documentsTitleErr = iT('card.documents', 'DOCUMENTS');
                const documentsSectionErr = `
                    <div class="view-section" style="grid-column: 1/-1; margin-top: 2rem;" data-collapsible>
                        <div class="collapsible-header" style="display: flex; align-items: center; justify-content: space-between; padding: 1rem 1.5rem; background: var(--background-secondary, #f8f9fa); border: 1px solid var(--border-color, #e5e7eb); border-radius: 8px; cursor: pointer;">
                            <div style="display: flex; align-items: center; gap: 0.75rem;">
                                <i class="fa-solid fa-file-lines" style="color: var(--secondary-color, #248567);"></i>
                                <h3 style="margin: 0; font-size: 0.875rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px;">${escapeHtml(documentsTitleErr)}</h3>
                            </div>
                            <i class="fas fa-chevron-down" style="transition: transform 0.2s;"></i>
                        </div>
                        <div class="collapsible-body" style="display: none; padding: 1.5rem; border: 1px solid var(--border-color, #e5e7eb); border-top: none; border-radius: 0 0 8px 8px;">
                            ${docsEmptyErr}
                        </div>
                    </div>
                `;
                
                container.innerHTML = `
                    ${definitionSection}
                    ${classificationsSection}
                    ${dataContentSection}
                    ${documentsSectionErr}
                `;
                
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
                
                // Render custom fields section
                if (window.CustomFields) {
                    try {
                        await window.CustomFields.renderViewSection({
                            facetId: 'System Interface',
                            containerId: 'interfaceViewContainer',
                            objectId: id,
                            title: iT('card.customFields', 'CUSTOM FIELDS')
                        });
                    } catch (error) {
                        console.error('Error rendering custom fields:', error);
                    }
                }
            }
        } catch (e) {
            console.error('[Interface] Error loading interface:', e);
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const errorMsg = isForbidden ? iT('capability.view.objectNotAvailable', 'This object is not available.') : (e.message || iT('common.unknownError', 'Unknown error'));
            const title = isForbidden ? iT('systemInterface.view.interfaceNotAvailable', 'Interface not available') : iT('systemInterface.view.loadFailed', 'Failed to load interface (id={id}).', { id: String(id) });
            container.innerHTML = `<div class="view-section" style="grid-column: 1/-1; color: var(--danger, #b91c1c); padding: 2rem; text-align: center;">
                <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                <p>${escapeHtml(title)}</p>
                <p style="font-size: 0.9em; color: var(--text-secondary, #6c757d);">${escapeHtml(errorMsg)}</p>
            </div>`;
        }
    }

    async function loadDataTabContent(id, container) {
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + escapeHtml(iT('message.loading', 'Loading...')) + '</div>';
        
        // Create sub-tabs structure
        const dataTabHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="data-container">
                    <div class="data-sub-tabs">
                        <button class="sub-tab active" data-sub-tab="data-within">${escapeHtml(iT('systemInterface.view.dataWithinSubTab', 'Data Within Interface'))}</button>
                        <button class="sub-tab" data-sub-tab="data-outside">${escapeHtml(iT('systemInterface.view.dataOutsideSubTab', 'Data Outside Interface'))}</button>
                    </div>
                    <div class="data-content">
                        <div id="dataWithinContent" class="sub-tab-content active">
                            <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${escapeHtml(iT('message.loading', 'Loading...'))}</div>
                        </div>
                        <div id="dataOutsideContent" class="sub-tab-content">
                            <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${escapeHtml(iT('message.loading', 'Loading...'))}</div>
                        </div>
                    </div>
                </div>
            </div>
        `;
        
        container.innerHTML = dataTabHTML;
        
        // Set up sub-tab event handlers
        const subTabs = container.querySelectorAll('.data-sub-tabs .sub-tab');
        const subTabContents = container.querySelectorAll('.sub-tab-content');
        
        subTabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const targetTab = this.getAttribute('data-sub-tab');
                
                // Remove active class from all tabs and contents
                subTabs.forEach(t => t.classList.remove('active'));
                subTabContents.forEach(c => c.classList.remove('active'));
                
                // Add active class to clicked tab
                this.classList.add('active');
                
                // Update URL with subtab
                updateURLForTab('data', targetTab);
                
                // Show corresponding content
                if (targetTab === 'data-within') {
                    const content = container.querySelector('#dataWithinContent');
                    if (content) {
                        content.classList.add('active');
                        loadDataWithinInterface(id, content);
                    }
                } else if (targetTab === 'data-outside') {
                    const content = container.querySelector('#dataOutsideContent');
                    if (content) {
                        content.classList.add('active');
                        loadDataOutsideInterface(id, content);
                    }
                }
            });
        });
        
        // Load initial content - check URL for subtab first
        const activeSubTab = getActiveSubTab();
        if (activeSubTab === 'data-outside') {
            const dataOutsideContent = container.querySelector('#dataOutsideContent');
            const dataOutsideTab = container.querySelector('[data-sub-tab="data-outside"]');
            if (dataOutsideContent && dataOutsideTab) {
                subTabs.forEach(t => t.classList.remove('active'));
                subTabContents.forEach(c => c.classList.remove('active'));
                dataOutsideTab.classList.add('active');
                dataOutsideContent.classList.add('active');
                loadDataOutsideInterface(id, dataOutsideContent);
            } else {
                // Fallback: load default if elements not ready
                const dataWithinContent = container.querySelector('#dataWithinContent');
                if (dataWithinContent) {
                    loadDataWithinInterface(id, dataWithinContent);
                }
            }
        } else {
            // Default to Data Within Interface
            const dataWithinContent = container.querySelector('#dataWithinContent');
            if (dataWithinContent) {
                loadDataWithinInterface(id, dataWithinContent);
            }
        }
    }

    async function loadDataWithinInterface(interfaceId, container) {
        container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + escapeHtml(iT('message.loading', 'Loading...')) + '</div>';
        
        try {
            const relationships = await window.BUDG_API_SERVICE.getInterfaceDataWithin(interfaceId);
            renderDataFlowTable(relationships, container, iT('systemInterface.view.dataFlowInInterfaces', 'DATA FLOW IN INTERFACES'));
        } catch (error) {
            console.error('Error loading data within interface:', error);
            container.innerHTML = `
                <div style="padding: 2rem; text-align: center; color: var(--danger, #b91c1c);">
                    <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <p>${escapeHtml(iT('systemInterface.view.failedToLoadDataWithin', 'Failed to load data within interface'))}</p>
                </div>
            `;
        }
    }

    async function loadDataOutsideInterface(interfaceId, container) {
        container.innerHTML = '<div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">' + escapeHtml(iT('message.loading', 'Loading...')) + '</div>';
        
        try {
            const relationships = await window.BUDG_API_SERVICE.getInterfaceDataOutside(interfaceId);
            renderDataFlowTable(relationships, container, iT('systemInterface.view.dataFlowOutsideInterfaces', 'DATA FLOW OUTSIDE INTERFACES'));
        } catch (error) {
            console.error('Error loading data outside interface:', error);
            container.innerHTML = `
                <div style="padding: 2rem; text-align: center; color: var(--danger, #b91c1c);">
                    <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <p>${escapeHtml(iT('systemInterface.view.failedToLoadDataOutside', 'Failed to load data outside interface'))}</p>
                </div>
            `;
        }
    }

    function renderDataFlowTable(relationships, container, title) {
        if (!relationships || relationships.length === 0) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">${escapeHtml(title)}</div>
                    <div class="interfaces-table-wrapper">
                        <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">
                            ${escapeHtml(iT('systemInterface.view.noDataFlowRelationships', 'No data flow relationships found.'))}
                        </div>
                    </div>
                </div>
            `;
            return;
        }

        const attrInDsTitle = escapeHtml(iT('systemInterface.view.viewAttributeInDataset', 'View attribute in dataset'));

        const rowsHtml = relationships.map(rel => {
            const sourceAttrLink = (rel.sourceDatasetId && rel.sourceAttributeId) 
                ? `<a href="/view/dataset/${encodeURIComponent(rel.sourceDatasetId)}?tab=attribute&attributeId=${encodeURIComponent(rel.sourceAttributeId)}&modal=true" target="_blank" style="color: #248567; text-decoration: none;" title="${attrInDsTitle}"><i class="fas fa-th"></i> ${escapeHtml(rel.sourceAttribute || '')}</a>`
                : `<i class="fas fa-th"></i> ${escapeHtml(rel.sourceAttribute || '')}`;
                
            const targetAttrLink = (rel.targetDatasetId && rel.targetAttributeId) 
                ? `<a href="/view/dataset/${encodeURIComponent(rel.targetDatasetId)}?tab=attribute&attributeId=${encodeURIComponent(rel.targetAttributeId)}&modal=true" target="_blank" style="color: #248567; text-decoration: none;" title="${attrInDsTitle}"><i class="fas fa-th"></i> ${escapeHtml(rel.targetAttribute || '')}</a>`
                : `<i class="fas fa-th"></i> ${escapeHtml(rel.targetAttribute || '')}`;

            // Handle Source Origination - leave empty if "Unknown"
            const sourceOrigination = (rel.sourceOrigination && rel.sourceOrigination !== 'Unknown') 
                ? escapeHtml(rel.sourceOrigination) 
                : '';
            
            // Handle Target Origination - leave empty if "Unknown"
            const targetOrigination = (rel.targetOrigination && rel.targetOrigination !== 'Unknown') 
                ? escapeHtml(rel.targetOrigination) 
                : '';

            // Format Relationship Type with badge
            const relationshipTypeBadge = rel.relationshipType 
                ? `<span class="view-badge" style="background: var(--primary-50,#e7f5ed); color: var(--primary-700,#195d48); border-color: var(--primary-100,#d2ebde); padding: 0.25rem 0.5rem; border-radius: 4px; font-size: 0.75rem; font-weight: 500;">${escapeHtml(rel.relationshipType)}</span>`
                : '';

            return `
                <tr>
                    <td>${escapeHtml(rel.sourceRef || '')}</td>
                    <td>${escapeHtml(rel.sourceDataSet || '')}</td>
                    <td>${sourceAttrLink}</td>
                    <td>${sourceOrigination}</td>
                    <td>${relationshipTypeBadge}</td>
                    <td>${escapeHtml(rel.targetRef || '')}</td>
                    <td>${escapeHtml(rel.targetDataSet || '')}</td>
                    <td>${targetAttrLink}</td>
                    <td>${targetOrigination}</td>
                </tr>
            `;
        }).join('');

        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title" style="display: flex; justify-content: space-between; align-items: center;">
                    <span>${escapeHtml(title)}</span>
                </div>
                <div class="interfaces-table-wrapper">
                    <table class="interfaces-table">
                        <thead>
                            <tr>
                                <th>${escapeHtml(iT('systemInterface.view.sourceRef', 'Source Ref.'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.sourceDataSet', 'Source Data Set'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.sourceAttribute', 'Source Attribute'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.sourceOrigination', 'Source Origination'))}</th>
                                <th>${escapeHtml(iT('systemInterface.table.relationshipType', 'Relationship Type'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.targetRef', 'Target Ref.'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.targetDataSet', 'Target Data Set'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.targetAttribute', 'Target Attribute'))}</th>
                                <th>${escapeHtml(iT('systemInterface.view.targetOrigination', 'Target Origination'))}</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rowsHtml}
                        </tbody>
                    </table>
                    <div class="table-footer">${escapeHtml(ifaceRecordCount(relationships.length))}</div>
                </div>
            </div>
        `;
    }

    async function loadDocuments(interfaceId) {
        // Find the documents collapsible section
        const documentsSection = document.querySelector('[data-collapsible] .collapsible-body');
        if (!documentsSection) {
            console.warn('Documents section not found');
            return;
        }

        // Create container for document table
        const containerId = 'interfaceDocumentsTableContainer';
        documentsSection.innerHTML = `<div id="${containerId}"></div>`;

        // Initialize document table component (read-only for view page)
        if (typeof DocumentTableComponent !== 'undefined') {
            try {
                new DocumentTableComponent({
                    facetType: 'interface',
                    facetId: interfaceId,
                    container: `#${containerId}`,
                    canEdit: false // Read-only in view mode
                });
            } catch (error) {
                console.error('Error initializing document table:', error);
                const failedToLoadDocs = iT('message.failedToLoadDocuments', 'Failed to load documents.');
                documentsSection.innerHTML = '<div class="empty" style="color:var(--danger,#b91c1c);">' + escapeHtml(failedToLoadDocs) + '</div>';
            }
        } else {
            console.error('DocumentTableComponent not found');
            const docComponentNotAvailable = iT('message.documentComponentNotAvailable', 'Document component not available.');
            documentsSection.innerHTML = '<div class="empty">' + escapeHtml(docComponentNotAvailable) + '</div>';
        }
    }

    document.addEventListener('DOMContentLoaded', function() {
        hideEditsIfUnauthenticated();
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
        
        // Edit button functionality
        const editBtn = document.getElementById('editBtn');
        
        if (editBtn) {
            editBtn.addEventListener('click', async function() {
                const id = parseId();
                if (id != null) {
                    // Check lock status before navigating
                    if (window.ViewLockHelper) {
                        const canEdit = await window.ViewLockHelper.interceptEditButton(
                            'system-interface',
                            id,
                            function() {
                                // Get the currently active tab
                                const activeTab = document.querySelector('.tab.active');
                                const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                                
                                // Get the currently active subtab if exists
                                const activeSubTab = getActiveSubTab();
                                let editUrl = `/view/system-interface/system-interface-edit.html?id=${id}&tab=${activeTabName}`;
                                if (activeSubTab) {
                                    editUrl += `&subtab=${activeSubTab}`;
                                }

                                // Navigate to edit page with tab and subtab parameters
                                window.location.href = editUrl;
                            }
                        );
                        if (!canEdit) return; // Lock check failed, navigation prevented
                    } else {
                        // Fallback if ViewLockHelper not available
                        const activeTab = document.querySelector('.tab.active');
                        const activeTabName = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                        
                        const activeSubTab = getActiveSubTab();
                        let editUrl = `/view/system-interface/system-interface-edit.html?id=${id}&tab=${activeTabName}`;
                        if (activeSubTab) {
                            editUrl += `&subtab=${activeSubTab}`;
                        }
                        window.location.href = editUrl;
                    }
                }
            });
        }
        
        // Check and display lock status on page load
        const id = parseId();
        if (id != null) {
            load(id).then(async () => {
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('system-interface', id);
                }
            });

            // Log visit for recent items
            try { window.BUDG_API_SERVICE.logVisit({ entity: 'Interface', entityId: String(id), route: `/view/system-interface/${id}` }); } catch(_) {}
            
            // Set active tab based on URL parameter first (this will load the correct tab content)
            setActiveTab();
            
            // Initialize components after tab content is loaded
            // Use a small delay to ensure tab content is loaded
            setTimeout(async function(){
                // Initialize unified edit dropdown
                if (window.EditDropdown && id != null) {
                    try {
                        window.EditDropdown.initialize('system-interface', id, {
                            container: '.tab-actions',
                            editUrl: `/view/system-interface/system-interface-edit.html?id=${id}`
                        });
                    } catch (error) {
                        console.error('Failed to initialize edit dropdown:', error);
                    }
                }

                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('interface', id, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for system interface:', e);
                    }
                }
            }, 100);
        }
        // Tabs: Summary vs Stakeholders vs Impact
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(function(btn){
            btn.addEventListener('click', function(){
                tabs.forEach(function(b){ b.classList.remove('active'); });
                this.classList.add('active');

                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                // Update URL when tab changes (clear subtab when switching main tabs)
                updateURLForTab(which);
                loadTabContent(which);
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('interfaceStakeholdersContainer');
                }
            });
        });
        
        // Handle browser back/forward buttons
        window.addEventListener('popstate', function(event) {
            // When user navigates back/forward, update tab based on URL
            setActiveTab();
        });
    });
})();


