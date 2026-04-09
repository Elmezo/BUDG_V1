(function() {
    function parseId() {
        // Primary: support query param pattern /regulation.html?id=123
        try {
            const params = new URLSearchParams(window.location.search);
            const qId = params.get('id');
            if (qId) {
                const n = parseInt(qId, 10);
                if (!Number.isNaN(n)) return n;
            }
        } catch(_) {}

        // Fallback: support route pattern /view/regulation/123
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('regulation');
        if (idx !== -1 && parts.length >= idx + 2) {
            const id = parseInt(parts[idx + 1], 10);
            return Number.isNaN(id) ? null : id;
        }
        return null;
    }

    let _regulationGuestCache = null;
    async function isGuestVisitorForRegulation() {
        if (_regulationGuestCache !== null) {
            return _regulationGuestCache;
        }
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                _regulationGuestCache = true;
                return _regulationGuestCache;
            }
            const me = await resp.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            const isAuthenticated = me.authenticated === true;
            const isGuestRole = role.includes('guest');
            _regulationGuestCache = !isAuthenticated || isGuestRole;
            return _regulationGuestCache;
        } catch (_) {
            _regulationGuestCache = true;
            return _regulationGuestCache;
        }
    }

    function renderRegulationGuestAccessDenied(container, regulationId, segmentName, segmentId) {
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

    // Robust hide function for edit controls
    function hideEditControls() {
        // Hide the edit dropdown for unauthorized users
        if (window.EditDropdown) {
            window.EditDropdown.hideEditControls();
        }
        const selectors = [
            '#editBtn',
            '#editBtn2',
            '#tabEditBtn',
            'editStakeholdersBtn'
        ];
        try {
            const nodes = document.querySelectorAll(selectors.join(','));
            nodes.forEach(el => {
                if (!el) return;
                try {
                    el.style.setProperty('display', 'none', 'important');
                } catch (_) {
                    el.style.display = 'none';
                }
                el.setAttribute('data-hidden-by', 'hideEditControls');
                try { if (typeof el.blur === 'function') el.blur(); } catch(_) {}
            });
        } catch (err) {
            console.error('hideEditControls error:', err);
        }
    }

    // Ensure hide edit controls with DOM observation
    function ensureHideEditControls(options = {}) {
        const timeout = typeof options.timeout === 'number' ? options.timeout : 15000;
        hideEditControls();

        const observer = new MutationObserver(mutations => {
            let found = false;
            for (const m of mutations) {
                for (const node of m.addedNodes) {
                    if (!(node instanceof Element)) continue;
                    if (node.matches && node.matches('#editBtn,#editBtn2,#tabEditBtn,#editStakeholdersBtn,[data-action="edit"]')) {
                        found = true;
                    } else if (node.querySelector && node.querySelector('#editBtn,#editBtn2,#tabEditBtn,#editStakeholdersBtn,[data-action="edit"]')) {
                        found = true;
                    }
                }
            }
            if (found) hideEditControls();
        });

        const target = document.body || document.documentElement;
        try {
            observer.observe(target, { childList: true, subtree: true });
        } catch (e) {
            // ignore observe errors
        }

        setTimeout(() => {
            try { observer.disconnect(); } catch(_) {}
        }, timeout);
    }

    // Auto-run on load - check auth first
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => hideEditsIfUnauthenticated());
    } else {
        hideEditsIfUnauthenticated();
    }

    async function hideEditsIfUnauthenticated() {
        try {
            const id = parseId();
            if (!id) {
                console.log('[Regulation] No ID found - hiding edit controls');
                ensureHideEditControls();
                return;
            }

            // Use the shared utility function to check if user can edit this object
            if (window.checkCanEditObject) {
                const editCheck = await window.checkCanEditObject('Regulation', id);
                const canEditObject = editCheck.canEdit;
                const isAdmin = editCheck.isAdmin;
                
                console.log('[Regulation] Can edit object?', canEditObject, '(isAdmin:', isAdmin, ', isStakeholder:', editCheck.isStakeholder, ')');
                
                // Show the main edit dropdown only if user can edit this object
                if (canEditObject) {
                    console.log('[Regulation] User can edit this object - showing edit controls');
                    showEditControls();
                } else {
                    console.log('[Regulation] User cannot edit this object - hiding edit controls');
                    ensureHideEditControls();
                }
                
                // Check delete permission
                const permResp = await fetch('/api/user/permissions/Regulation', { method: 'GET', credentials: 'include' });
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
                ensureHideEditControls(); 
                return; 
            }
            const me = await meResp.json();
            // Cache current user for name resolving fallback
            try { window.__BUDG_CURRENT_USER__ = me; } catch(_) {}
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            if (!isAdmin) {
                ensureHideEditControls();
            } else {
                showEditControls();
            }
        } catch (e) {
            ensureHideEditControls();
        }
    }
    
    function hideDeleteControls() {
        // Hide delete buttons - only admin/super admin can delete
        const deleteSelectors = [
            '[data-action="delete"]',
            '.delete-btn',
            '.btn-delete',
            '#deleteBtn',
            '#deleteRegulationBtn'
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
        const selectors = [
            '#editBtn',
            '#editBtn2',
            '#tabEditBtn',
            '#editStakeholdersBtn',
            '[data-action="edit"]'
        ];
        try {
            const nodes = document.querySelectorAll(selectors.join(','));
            nodes.forEach(el => {
                if (!el) return;
                el.style.display = '';
                el.style.setProperty('display', '', 'important');
                el.removeAttribute('data-hidden-by');
            });
            console.log('Edit controls shown for admin user');
        } catch (err) {
            console.error('showEditControls error:', err);
        }
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
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class="empty">-</span>'}</div></div>`;
    }

    // Helper function to format date
    function formatDate(dateString) {
        if (!dateString) return '';
        try {
            return new Date(dateString).toLocaleDateString('en-GB');
        } catch (error) {
            return dateString;
        }
    }

    async function load(id) {
        const container = document.getElementById('glossaryViewContainer');
        const titleElement = document.getElementById('glossaryTitle');
        const breadcrumbElement = document.getElementById('glossaryBreadcrumb');
        
        // Update title immediately to show loading state
        if (titleElement) {
            titleElement.removeAttribute('data-i18n');
            titleElement.textContent = 'Loading...';
        }
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading...</div>';
        
        try {
            // Fetch regulation data
            const response = await fetch(`/api/regulation/${id}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            const regulation = await response.json();

            try {
                const isGuest = await isGuestVisitorForRegulation();
                const segmentName = regulation?.segmentName || regulation?.segment_name || regulation?.segment;
                const segmentId = regulation?.segmentId ?? regulation?.segment_id ?? regulation?.Segment_ID;
                const segmentNameLower = (segmentName || '').toString().toLowerCase();
                if (isGuest && segmentNameLower !== 'enterprise') {
                    console.log('[Regulation] Guest visitor blocked from non-Enterprise segment view', { regulationId: id, segmentName, segmentId });
                    renderRegulationGuestAccessDenied(container, id, segmentName, segmentId);
                    if (titleElement) {
                        titleElement.removeAttribute('data-i18n');
                        titleElement.textContent = (window.I18n && window.I18n.t && window.I18n.t('error.segmentAccess.title')) || 'Cannot access this item';
                    }
                    document.title = 'BUDG';
                    return;
                }
            } catch (gateError) {
                console.warn('[Regulation] Error during guest/segment access check, falling back to normal render:', gateError);
            }
            
            console.log('Regulation data:', regulation);

            // Update header with regulation data - ensure it's always updated
            if (titleElement) {
                const regulationName = regulation.primaryName || regulation.PrimaryName || regulation.name || regulation.Name || 'Unnamed Regulation';
                // Remove data-i18n attribute to prevent i18n from overwriting
                titleElement.removeAttribute('data-i18n');
                titleElement.textContent = escapeHtml(regulationName);
                // Also update page title
                document.title = `BUDG - ${regulationName}`;
                
                // Stop observing if observer was set
                if (window._regulationTitleObserver) {
                    window._regulationTitleObserver.disconnect();
                    delete window._regulationTitleObserver;
                }
            }
            if (breadcrumbElement) {
                breadcrumbElement.textContent = (window.I18n && window.I18n.t('regulation.page.title')) || 'Regulation';
            }

            const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;

            // Get additional reference data
            let lookupData = {};
            
            // Load all regulation lookup tables based on regulationsc.sql
            // Note: Some APIs may not exist yet, so we handle 404s gracefully
            try {
                const [
                    statusResp,
                    stageResp,
                    maturityResp,
                    probabilityResp,
                    impactRatingResp,
                    complianceLevelResp,
                    legalAdviceTypeResp,
                    viewingResp,
                    parentResp
                ] = await Promise.allSettled([
                    fetch('/api/regulation-status/list'),
                    fetch('/api/regulation-stage/list'),
                    fetch('/api/regulation-maturity/list'),
                    fetch('/api/regulation-probability/list'),
                    fetch('/api/regulation-impact-rating/list'),
                    fetch('/api/regulation-compliance-level/list'),
                    fetch('/api/legal-advice-type/list'),
                    fetch('/api/viewing/list'),
                    fetch('/api/regulation/parent-picker')
                ]);

                // Process each response, handling both success and failure
                if (statusResp.status === 'fulfilled' && statusResp.value.ok) {
                    lookupData.statusList = await statusResp.value.json();
                }
                if (stageResp.status === 'fulfilled' && stageResp.value.ok) {
                    lookupData.stageList = await stageResp.value.json();
                }
                if (maturityResp.status === 'fulfilled' && maturityResp.value.ok) {
                    lookupData.maturityList = await maturityResp.value.json();
                }
                if (probabilityResp.status === 'fulfilled' && probabilityResp.value.ok) {
                    lookupData.probabilityList = await probabilityResp.value.json();
                }
                if (impactRatingResp.status === 'fulfilled' && impactRatingResp.value.ok) {
                    lookupData.impactRatingList = await impactRatingResp.value.json();
                }
                if (complianceLevelResp.status === 'fulfilled' && complianceLevelResp.value.ok) {
                    lookupData.complianceLevelList = await complianceLevelResp.value.json();
                }
                if (legalAdviceTypeResp.status === 'fulfilled' && legalAdviceTypeResp.value.ok) {
                    lookupData.legalAdviceTypeList = await legalAdviceTypeResp.value.json();
                }
                if (viewingResp.status === 'fulfilled' && viewingResp.value.ok) {
                    lookupData.viewingList = await viewingResp.value.json();
                }
                if (parentResp.status === 'fulfilled' && parentResp.value.ok) {
                    lookupData.parentRegulations = await parentResp.value.json();
                }
            } catch (error) {
                console.log('Could not load some lookup data:', error);
            }

            // Load people to resolve LastUpdate_UserID → Name
            try {
                if (window.BUDG_API_SERVICE && window.BUDG_API_SERVICE.getPeople) {
                    const people = await window.BUDG_API_SERVICE.getPeople();
                    lookupData.people = Array.isArray(people?.data) ? people.data : (Array.isArray(people) ? people : []);
                } else {
                    const peopleResp = await fetch('/api/people');
                    if (peopleResp.ok) {
                        const people = await peopleResp.json();
                        lookupData.people = Array.isArray(people?.data) ? people.data : (Array.isArray(people) ? people : []);
                    }
                }
            } catch (error) {
                console.log('Could not load people data:', error);
            }

            // Helper function to resolve lookup values
            const resolveLookupValue = (list, id, defaultValue = (typeof window !== 'undefined' && window.I18n && window.I18n.t ? window.I18n.t('message.notSpecified') : 'Not specified')) => {
                if (!id || !list || !Array.isArray(list)) return defaultValue;
                const item = list.find(i => (i.id === id) || (i.ID === id));
                return item ? (item.primaryName || item.PrimaryName || item.name || item.Name || defaultValue) : defaultValue;
            };

            const resolveUserName = (userId) => {
                if (userId == null || !lookupData.people || !Array.isArray(lookupData.people)) return '';
                const uidStr = String(userId);
                const uidNum = Number(userId);
                const person = lookupData.people.find(p => {
                    const ids = [p.ID, p.Id, p.id, p.userId, p.UserID, p.User_Id, p.User_ID]
                        .filter(v => v != null)
                        .map(v => [String(v), Number(v)]).flat();
                    return ids.includes(uidStr) || ids.includes(uidNum);
                });
                if (!person) return '';
                const first = person.First_Name || person.firstName || person.FirstName || '';
                const last = person.Last_Name || person.lastName || person.LastName || '';
                const primary = person.primaryName || person.PrimaryName || person.Name || person.name || '';
                const name = `${first} ${last}`.trim();
                return (name || primary || '').trim();
            };

            const resolveUserNameWithFallback = (userId) => {
                let name = resolveUserName(userId);
                if (name) return name;
                try {
                    const me = window.__BUDG_CURRENT_USER__ || {};
                    const meIds = [me.id, me.ID, me.userId, me.UserID].filter(v => v != null).map(v => String(v));
                    if (meIds.includes(String(userId))) {
                        const first = me.firstName || me.First_Name || '';
                        const last = me.lastName || me.Last_Name || '';
                        const primary = me.primaryName || me.PrimaryName || me.name || me.Name || '';
                        const nm = `${first || ''} ${last || ''}`.trim() || primary;
                        if (nm) return nm;
                    }
                } catch(_) {}
                return '';
            };

            const definitionSection = `
                <div class="view-section">
                    <div class="section-title">${t('regulation.sections.definition')}</div>
                    <div class="view-item"><div class="view-value" style="flex:1">${_richHtml(regulation.description || regulation.Description || '')}</div></div>
                    ${renderItem(t('regulation.labels.ref'), escapeHtml(regulation.refNumber || regulation.RefNumber || ''))}
                    ${renderItem(t('regulation.labels.shortName'), escapeHtml(regulation.shortName || regulation.ShortName || '') || '<span class="empty">' + (window.I18n?.t('message.notSpecified') || 'Not specified') + '</span>')}
                    ${renderItem(t('regulation.labels.additionalInfo'), escapeHtml(regulation.additionalInfo || regulation.AdditionalInfo || '') || '<span class="empty">' + (window.I18n?.t('message.notSpecified') || 'Not specified') + '</span>')}
                    ${renderItem(t('regulation.labels.legalAdviceGuideline'), resolveLookupValue(lookupData.legalAdviceTypeList, regulation.legalAdviceTypeId || regulation.LegalAdviceType_ID))}
                </div>
            `;

            // Load regulator data
            let regulatorTableRows = '';
            try {
                const regulatorResponse = await fetch(`/api/regulation-x-regulator/regulation/${id}`);
                if (regulatorResponse.ok) {
                    const regulators = await regulatorResponse.json();
                    if (Array.isArray(regulators) && regulators.length > 0) {
                        regulatorTableRows = regulators.map(reg => {
                            const relationType = reg.relationType || 'Unknown Type';
                            const name = reg.regulatorName || 'Unnamed Regulator';
                            const nameLink = reg.regulatorId ? 
                                `<a href="/view/regulator/${reg.regulatorId}" class="regulator-link">${escapeHtml(name)}</a>` : 
                                escapeHtml(name);
                            
                            // Add inheritance indicator if from parent regulation
                            const inheritanceIndicator = reg.inheritanceLevel > 0 ? 
                                `<span class="inheritance-badge" title="Inherited from parent regulation: ${reg.sourceRegulationName}">↑</span>` : '';
                            
                            return `
                                <tr>
                                    <td>${escapeHtml(relationType)}</td>
                                    <td>${nameLink}${inheritanceIndicator}</td>
                                </tr>
                            `;
                        }).join('');
                    } else {
                        regulatorTableRows = `
                            <tr>
                                <td colspan="2" style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">
                                    ${t('regulation.view.noRegulatorsFound')}
                                </td>
                            </tr>
                        `;
                    }
                } else {
                    regulatorTableRows = `
                        <tr>
                            <td colspan="2" style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">
                                ${t('regulation.view.unableToLoadRegulatorData')}
                            </td>
                        </tr>
                    `;
                }
            } catch (error) {
                console.error('Error loading regulator data:', error);
                regulatorTableRows = `
                    <tr>
                        <td colspan="2" style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">
                            ${t('regulation.view.errorLoadingRegulatorData')}
                        </td>
                    </tr>
                `;
            }

            // Regulator section
            const regulatorSection = `
                <div class="view-section regulator-section">
                    <div class="section-title">${t('regulation.sections.regulator')}</div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>${t('regulation.labels.relationshipType')}</th>
                                    <th>${t('regulation.labels.regulator')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${regulatorTableRows}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;

            // Load geography data
            let geographyTableRows = '';
            try {
                const geographyResponse = await fetch(`/api/regulation-x-regulator-x-geography/regulation/${id}`);
                if (geographyResponse.ok) {
                    const geographies = await geographyResponse.json();
                    console.log('Geography data received:', geographies);
                    if (Array.isArray(geographies) && geographies.length > 0) {
                        geographyTableRows = geographies.map(geo => {
                            console.log('Processing geography item:', geo);
                            const geographyName = geo.geographyName || 'Unnamed Geography';
                            const regulatorName = geo.regulatorName || 'Unknown Regulator';
                            // Use description from regulation_x_regulator_x_geography table (relationDescription)
                            const description = geo.relationDescription || '';
                            console.log('Description for', geographyName, ':', description);
                            
                            const geographyLink = geo.geographyId ? 
                                `<a href="/view/geography/${geo.geographyId}" class="geography-link">${escapeHtml(geographyName)}</a>` : 
                                escapeHtml(geographyName);
                            
                            const regulatorLink = geo.regulatorId ? 
                                `<a href="/view/regulator/${geo.regulatorId}" class="regulator-link">${escapeHtml(regulatorName)}</a>` : 
                                escapeHtml(regulatorName);
                            
                            // Add inheritance indicator if from parent regulation
                            const inheritanceIndicator = geo.inheritanceLevel > 0 ? 
                                `<span class="inheritance-badge" title="${t('regulation.view.inheritedFromParent', { name: geo.sourceRegulationName || '' })}">↑</span>` : '';
                            
                            return `
                                <tr>
                                    <td>${geographyLink}${inheritanceIndicator}</td>
                                    <td>${regulatorLink}</td>
                                    <td><span title="${escapeHtml(description)}">${escapeHtml(description)}</span></td>
                                </tr>
                            `;
                        }).join('');
                    } else {
                        geographyTableRows = `
                            <tr>
                                <td colspan="3" style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">
                                    ${t('regulation.view.noGeographyConnected')}
                                </td>
                            </tr>
                        `;
                    }
                } else {
                    geographyTableRows = `
                        <tr>
                            <td colspan="3" style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">
                                ${t('regulation.view.unableToLoadGeographyData')}
                            </td>
                        </tr>
                    `;
                }
            } catch (error) {
                console.error('Error loading geography data:', error);
                geographyTableRows = `
                    <tr>
                        <td colspan="3" style="text-align: center; padding: 1rem; color: var(--text-secondary, #64748b);">
                            ${t('regulation.view.errorLoadingGeographyData')}
                        </td>
                    </tr>
                `;
            }

            // Geography section
            const geographySection = `
                <div class="view-section geography-section">
                    <div class="section-title">${t('regulation.sections.geography')}</div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>${t('regulation.labels.geography')}</th>
                                    <th>${t('regulation.labels.regulator')}</th>
                                    <th>${t('regulation.labels.description')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${geographyTableRows}
                            </tbody>
                        </table>
                    </div>
                </div>
            `;

            // Classifications sidebar
            const classificationsSection = `
                <div class="view-section sidebar-section">
                    <div class="section-title">${t('regulation.sections.classifications')}</div>
                    
                    <div class="classifications-group">
                        <div class="group-title">${t('regulation.sections.basicClassifications')}</div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.budgStatus')}</span>
                            <span class="classification-value">
                                <span class="status-dot obsolete"></span>
                                ${escapeHtml(resolveLookupValue(lookupData.statusList, regulation.regulationStatusId || regulation.RegulationStatus_ID))}
                            </span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.stage')}</span>
                            <span class="classification-value">${escapeHtml(resolveLookupValue(lookupData.stageList, regulation.regulationStageId || regulation.RegulationStage_ID))}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.maturity')}</span>
                            <span class="classification-value">${escapeHtml(resolveLookupValue(lookupData.maturityList, regulation.regulationMaturityId || regulation.RegulationMaturity_ID))}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.probability')}</span>
                            <span class="classification-value">${escapeHtml(resolveLookupValue(lookupData.probabilityList, regulation.regulationProbabilityId || regulation.RegulationProbability_ID))}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.complianceLevel')}</span>
                            <span class="classification-value">${escapeHtml(resolveLookupValue(lookupData.complianceLevelList, regulation.complianceLevelId || regulation.ComplianceLevel_ID))}</span>
                        </div>
                    </div>

                    <div class="classifications-group">
                        <div class="group-title">${t('regulation.sections.otherInformation')}</div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.created')}</span>
                            <span class="classification-value">${escapeHtml(
                                regulation.createDateTime || regulation.CreateDatetime ? 
                                formatDate(regulation.createDateTime || regulation.CreateDatetime) : 
                                formatDate(new Date().toISOString())
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.lastUpdatedBy')}</span>
                            <span class="classification-value">${(() => {
                                const uid = regulation.lastUpdateUserId || regulation.LastUpdate_UserID;
                                const nm = resolveUserNameWithFallback(uid);
                                return escapeHtml(nm || '—');
                            })()}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.lastUpdated')}</span>
                            <span class="classification-value">${escapeHtml(
                                regulation.lastUpdateDateTime || regulation.LastUpdateDatetime ? 
                                formatDate(regulation.lastUpdateDateTime || regulation.LastUpdateDatetime) : 
                                formatDate(new Date().toISOString())
                            )}</span>
                        </div>
                    </div>

                    <div class="classifications-group">
                        <div class="group-title">${t('regulation.sections.otherDetails')}</div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.accessControl') || 'Access Control'}</span>
                            <span class="classification-value">${escapeHtml(
                                resolveLookupValue(lookupData.viewingList, regulation.isPublic || regulation.Is_Public)
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.publicationDate')}</span>
                            <span class="classification-value">${escapeHtml(
                                regulation.publicationDate ? formatDate(regulation.publicationDate) : '—'
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.complianceDeadline')}</span>
                            <span class="classification-value">${escapeHtml(
                                regulation.complianceDate ? formatDate(regulation.complianceDate) : '—'
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.commentsDate') || 'Comments Date'}</span>
                            <span class="classification-value">${escapeHtml(
                                regulation.commentsDate ? formatDate(regulation.commentsDate) : '—'
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.finalisationDate') || 'Finalization Date'}</span>
                            <span class="classification-value">${escapeHtml(
                                regulation.finalisationDate ? formatDate(regulation.finalisationDate) : '—'
                            )}</span>
                        </div>
                        <div class="classification-item">
                            <span class="classification-label">${t('regulation.labels.segment')}</span>
                            <span class="classification-value">${escapeHtml(regulation.segmentName || regulation.segment_name || regulation.segment || ((regulation.segmentId ?? regulation.segment_id ?? regulation.Segment_ID) != null ? `ID ${regulation.segmentId ?? regulation.segment_id ?? regulation.Segment_ID}` : (window.I18n?.t('message.notSpecified') || 'Not specified')))}</span>
                        </div>
                    </div>
                </div>
            `;

            container.innerHTML = `
                <div class="glossary-container">
                    <div class="left-column">
                        ${definitionSection}
                        ${regulatorSection}
                        ${geographySection}
                    </div>
                    <div class="right-column">
                        ${classificationsSection}
                    </div>
                </div>
            `;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'Regulation',
                        containerId: 'glossaryViewContainer',
                        objectId: id,
                        title: t('regulation.sections.customFields')
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

        } catch (e) {
            console.error('Error loading regulation:', e);
            const t = (key, params) => (window.I18n && window.I18n.t(key, params)) || key;
            const backToCreate = t('regulation.page.backToCreate');
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            if (isForbidden) {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>Regulation Not Available</h3>
                        <p>This object is not available.</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulation.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            } else if (String(e?.message || '').includes('404')) {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('regulation.messages.notFound')}</h3>
                        <p>${t('regulation.page.doesNotExist', { id })}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulation.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    <div class="view-section" style="color: var(--danger, #b91c1c); text-align: center; padding: 2rem;">
                        <h3>${t('regulation.messages.errorLoading')}</h3>
                        <p>${(window.I18n && window.I18n.t('message.failedToLoad')) || 'Failed to load'} regulation (id=${id}). ${e.message || (window.I18n && window.I18n.t('message.error')) || 'Unknown error occurred.'}</p>
                        <button class="btn btn-primary" onclick="window.location.href='/regulation.html'">
                            ${backToCreate}
                        </button>
                    </div>
                `;
            }
        }
    }

    // Regulation hierarchy functions
    async function loadRegulationHierarchy(currentRegulationId) {
        try {
            console.log('Loading regulation hierarchy for regulation ID:', currentRegulationId);
            
            // Fetch all regulations
            const response = await fetch('/api/regulation/hierarchy');
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            
            const regulations = await response.json();
            console.log('All regulations loaded for hierarchy:', regulations);
            
            if (!Array.isArray(regulations) || regulations.length === 0) {
                const tbody = document.querySelector('.hierarchy-table tbody');
                const footer = document.querySelector('.table-footer');
                const noRegs = (window.I18n && window.I18n.t('regulation.messages.noRegulationsFound')) || 'No regulations found.';
                const zeroRec = (window.I18n && window.I18n.t('message.zeroRecords')) || '0 records';
                if (tbody) {
                    tbody.innerHTML = `
                        <tr>
                            <td colspan="3" style="text-align: center; padding: 2rem;">
                                <i class="fas fa-balance-scale" style="font-size: 2rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                                <p>${noRegs}</p>
                            </td>
                        </tr>
                    `;
                }
                if (footer) footer.textContent = zeroRec;
                return;
            }
            
            // Build hierarchy tree
            const hierarchyRows = buildRegulationHierarchyTree(regulations, parseInt(currentRegulationId));
            
            // Render the hierarchy table
            const html = renderRegulationTable(hierarchyRows, currentRegulationId);
            
            // Update the container with the new HTML
            const container = document.querySelector('.view-section');
            if (container) {
                container.outerHTML = html;
                const newContainer = document.querySelector('.view-section');
                if (newContainer) {
                    initRegulationInteractions(newContainer, hierarchyRows);
                }
            }
            
        } catch (error) {
            console.error('Error loading regulation hierarchy:', error);
            const tbody = document.querySelector('.hierarchy-table tbody');
            const footer = document.querySelector('.table-footer');
            if (tbody) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="3" style="text-align: center; padding: 2rem; color: var(--danger, #dc3545);">
                            <i class="fas fa-exclamation-triangle" style="font-size: 2rem; margin-bottom: 1rem; display: block;"></i>
                            <p>Error loading regulation hierarchy: ${error.message}</p>
                        </td>
                    </tr>
                `;
            }
            if (footer) footer.textContent = (window.I18n && window.I18n.t('message.failedToLoad')) || 'Error loading data';
        }
    }

    function buildRegulationHierarchyTree(regulations, currentRegulationId) {
        console.log('Building regulation hierarchy tree for regulation ID:', currentRegulationId);
        
        // Create maps for easy lookup
        const byId = {};
        const parentMap = {};
        
        regulations.forEach(regulation => {
            const id = parseInt(regulation.id);
            byId[id] = regulation;
            
            if (regulation.parentId) {
                const parentId = parseInt(regulation.parentId);
                if (!parentMap[parentId]) {
                    parentMap[parentId] = [];
                }
                parentMap[parentId].push(regulation);
            }
        });
        
        // Find the lineage for the current regulation
        const lineage = buildRegulationLineageTree(regulations, currentRegulationId);
        
        if (lineage.length === 0) {
            // Fallback: show all regulations
            console.log('No lineage found, showing all regulations');
            const hierarchyRows = {
                rows: regulations.map(regulation => ({
                    node: regulation,
                    depth: 0,
                    childCount: parentMap[regulation.id] ? parentMap[regulation.id].length : 0,
                    hasChildren: parentMap[regulation.id] && parentMap[regulation.id].length > 0
                })),
                parentMap: parentMap
            };
            return hierarchyRows;
        }
        
        // Build the tree structure
        const hierarchyRows = {
            rows: [],
            parentMap: parentMap
        };
        
        function buildRows(nodes, depth = 0) {
            nodes.forEach(node => {
                const nodeId = parseInt(node.id);
                const childCount = parentMap[nodeId] ? parentMap[nodeId].length : 0;
                const hasChildren = childCount > 0;
                
                hierarchyRows.rows.push({
                    node: node,
                    depth: depth,
                    childCount: childCount,
                    hasChildren: hasChildren
                });
                
                // Add children recursively
                if (hasChildren) {
                    buildRows(parentMap[nodeId], depth + 1);
                }
            });
        }
        
        // Start with the root of the lineage
        const rootRegulation = lineage[0];
        buildRows([rootRegulation], 0);
        
        return hierarchyRows;
    }

    function buildRegulationLineageTree(regulations, currentRegulationId) {
        const byId = {};
        regulations.forEach(reg => {
            byId[parseInt(reg.id)] = reg;
        });
        
        const currentId = parseInt(currentRegulationId);
        const currentRegulation = byId[currentId];
        
        if (!currentRegulation) {
            console.log('Current regulation not found in data');
            return [];
        }
        
        // Find ancestors
        const ancestors = [];
        let current = currentRegulation;
        
        while (current && current.parentId) {
            const parentId = parseInt(current.parentId);
            const parent = byId[parentId];
            if (parent) {
                ancestors.unshift(parent);
                current = parent;
            } else {
                break;
            }
        }
        
        // Build parent map for finding descendants
        const parentMap = {};
        regulations.forEach(reg => {
            if (reg.parentId) {
                const parentId = parseInt(reg.parentId);
                if (!parentMap[parentId]) {
                    parentMap[parentId] = [];
                }
                parentMap[parentId].push(reg);
            }
        });
        
        // Find descendants of current regulation
        const descendants = [];
        function findDescendants(nodeId) {
            const children = parentMap[nodeId] || [];
            children.forEach(child => {
                descendants.push(child);
                findDescendants(parseInt(child.id));
            });
        }
        findDescendants(currentId);
        
        // Find siblings (other children of the same parent)
        const siblings = [];
        if (currentRegulation.parentId) {
            const parentId = parseInt(currentRegulation.parentId);
            const siblingsOfCurrent = parentMap[parentId] || [];
            siblingsOfCurrent.forEach(sibling => {
                const siblingId = parseInt(sibling.id);
                if (siblingId !== currentId) {
                    siblings.push(sibling);
                    // Find descendants of siblings (nephews/nieces and their descendants)
                    findDescendants(siblingId);
                }
            });
        }
        
        // Return the complete lineage: ancestors + current + descendants (including siblings and their descendants)
        return [...ancestors, currentRegulation, ...descendants];
    }

    function renderRegulationTable(hierarchyRows, currentId) {
        const formatDate = (dateString) => {
            if (!dateString) return '';
            try {
                return new Date(dateString).toLocaleDateString();
            } catch (error) {
                return dateString;
            }
        };

        const rowsHtml = hierarchyRows.rows.map(({ node, depth, childCount, hasChildren }) => {
            const name = node.primaryName || 'Unnamed Regulation';
            const shortName = node.shortName || '';
            const desc = node.description || '';
            const refNumber = node.refNumber || '';
            const regulationStatus = node.regulationStatus || '';
            const regulationStage = node.regulationStage || '';
            const complianceLevel = node.complianceLevel || '';
            const isCurrent = String(node.id) === String(currentId);
            const id = node.id;
            const parentId = node.parentId || '';
            
            const indent = Array(depth).fill('<span class="tree-indent"></span>').join('');
            const expander = hasChildren ? `<button type="button" class="tree-expander" aria-label="Toggle"><i class="fas fa-caret-down"></i></button>` : '<span class="tree-placeholder"></span>';
            const countBadge = hasChildren ? `<span class="child-count" title="Children">${childCount}</span>` : '';
            const linkClass = isCurrent ? 'regulation-link current-regulation-link' : 'regulation-link';
            const componentLink = `<a class="${linkClass}" href="/view/regulation/${encodeURIComponent(id)}" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
            
            // Get regulatory themes from backend data
            const regulatoryTheme = node.regulatoryThemes || '';
            
            return `<tr class="${isCurrent ? 'current-row' : ''}" data-id="${id}" data-parent-id="${parentId}" data-depth="${depth}">
                <td><span class="ref-number">${escapeHtml(refNumber)}</span></td>
                <td><div class="tree-cell">${indent}${expander}${depth>0?'<span class="tree-branch"></span>':''}<i class="fas fa-balance-scale item-icon"></i><span class="regulation-name">${componentLink}</span>${countBadge}</div></td>
                <td>${escapeHtml(shortName)}</td>
                <td><span title="${escapeHtml(desc)}">${escapeHtml(desc)}</span></td>
                <td>${escapeHtml(regulationStatus)}</td>
                <td>${escapeHtml(regulationStage)}</td>
                <td>${escapeHtml(complianceLevel)}</td>
                <td>${escapeHtml(regulatoryTheme)}</td>
            </tr>`;
        }).join('');

        return `
            <div class="view-section">
                <div class="section-title">REGULATION HIERARCHY</div>
                <div class="hierarchy-table-wrapper">
                    <table class="hierarchy-table">
                        <thead>
                            <tr>
                                <th>Ref.</th>
                                <th>Component</th>
                                <th>Short Name</th>
                                <th>Description</th>
                                <th>BUDG Status</th>
                                <th>Stage</th>
                                <th>Compliance Level</th>
                                <th>Regulatory Theme</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rowsHtml}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${hierarchyRows.rows.length} record${hierarchyRows.rows.length !== 1 ? 's' : ''}
                </div>
            </div>
        `;
    }

    function initRegulationInteractions(containerEl, hierarchyRows) {
        const parentMap = hierarchyRows.parentMap;
        
        // Handle expand/collapse functionality
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
                const isExpanded = icon.classList.contains('fa-caret-down');
                icon.classList.toggle('fa-caret-down', !isExpanded);
                icon.classList.toggle('fa-caret-right', isExpanded);
                
                // Find all child rows
                let nextRow = row.nextElementSibling;
                while (nextRow && parseInt(nextRow.dataset.depth) > currentDepth) {
                    const childDepth = parseInt(nextRow.dataset.depth);
                    
                    if (childDepth === currentDepth + 1) {
                        // Direct children - toggle visibility
                        nextRow.style.display = isExpanded ? 'none' : '';
                        
                        // If collapsing, also collapse any expanded grandchildren
                        if (isExpanded) {
                            const childExpander = nextRow.querySelector('.tree-expander i');
                            if (childExpander && childExpander.classList.contains('fa-caret-down')) {
                                childExpander.classList.remove('fa-caret-down');
                                childExpander.classList.add('fa-caret-right');
                            }
                        }
                    } else if (childDepth > currentDepth + 1) {
                        // Grandchildren and deeper - hide when collapsing
                        if (isExpanded) {
                            nextRow.style.display = 'none';
                        }
                    }
                    
                    nextRow = nextRow.nextElementSibling;
                }
            }
        });
    }

    // Tab switching logic
    document.addEventListener('DOMContentLoaded', function() {
        hideEditsIfUnauthenticated();
        console.log('Regulation page loaded');
        
        // Initialize title element immediately to avoid showing static text
        const titleElement = document.getElementById('glossaryTitle');
        if (titleElement) {
            // Remove data-i18n to prevent i18n from overwriting
            titleElement.removeAttribute('data-i18n');
            titleElement.textContent = 'Loading...';
            
            // Watch for i18n trying to overwrite and prevent it
            const observer = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    if (mutation.type === 'childList' || mutation.type === 'characterData') {
                        // If i18n tries to set a static text, restore Loading... or actual title
                        const currentText = titleElement.textContent;
                        if (currentText && currentText.trim() !== '' && currentText !== 'Loading...') {
                            // Title was set by load() function, keep it
                            return;
                        }
                        // If it's empty or was reset, set back to Loading...
                        if (!currentText || currentText.trim() === '') {
                            titleElement.textContent = 'Loading...';
                        }
                    }
                });
            });
            
            observer.observe(titleElement, {
                childList: true,
                characterData: true,
                subtree: true
            });
            
            // Stop observing after data is loaded (will be stopped in load() function)
            window._regulationTitleObserver = observer;
        }
        
        const backBtn = document.getElementById('backBtn');
        console.log('Back button found:', backBtn);
        
        if (backBtn) backBtn.addEventListener('click', () => window.history.length > 1 ? window.history.back() : window.location.assign('/'));
        
        // Edit button functionality
        const editBtn = document.getElementById('editBtn');
        if (editBtn) {
            editBtn.addEventListener('click', function() {
                const id = parseId();
                if (id != null) {
                    // Get current active tab
                    const activeTab = document.querySelector('.tab.active');
                    const currentTab = activeTab ? activeTab.getAttribute('data-tab') : 'summary';
                    
                    // Define tab mapping: view tab -> edit tab (or 'summary' if not available)
                    const tabMapping = {
                        'summary': 'summary',
                        'components': 'components',
                        'relationships': 'relationships',
                        'stakeholders': 'stakeholders',
                        'impact': 'impact',
                        'history': 'summary', // fallback to summary
                        'change': 'summary'  // fallback to summary
                    };
                    
                    const targetTab = tabMapping[currentTab] || 'summary';
                    
                    // Navigate to edit page with tab parameter
                    window.location.href = `/view/regulation/regulation-edit.html?id=${id}&tab=${targetTab}`;
                }
            });
        }

        // Initialize unified edit dropdown
        const regulationId = parseId();
        if (window.EditDropdown && regulationId != null) {
            try {
                window.EditDropdown.initialize('regulation', regulationId, {
                    container: '.tab-actions',
                    editUrl: `/view/regulation/regulation-edit.html?id=${regulationId}`
                });
            } catch (error) {
                console.error('Failed to initialize edit dropdown:', error);
            }
        }
        
        if (regulationId != null) {
            load(regulationId).then(async function(){
                // Check and display lock status
                if (window.ViewLockHelper) {
                    await window.ViewLockHelper.checkAndDisplayLockStatus('regulation', regulationId);
                }
                
                if (window.addFollowButton) {
                    try {
                        await window.addFollowButton('regulation', regulationId, null, '.form-actions');
                    } catch (e) {
                        console.error('Failed to initialize follow button for regulation:', e);
                    }
                }
            });
            try { 
                window.BUDG_API_SERVICE?.logVisit({ 
                    entity: 'Regulation', 
                    entityId: String(regulationId), 
                    route: `/view/regulation/${regulationId}` 
                }); 
            } catch(_) {}
        }

        // Tab functionality
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const tabName = this.getAttribute('data-tab');
                console.log('Tab clicked:', tabName);

                // Remove active class from all tabs
                tabs.forEach(t => t.classList.remove('active'));
                this.classList.add('active');

                // Get regulation ID
                const regulationId = parseId();

                // Handle tab content
                const container = document.getElementById('glossaryViewContainer');
                if (!container) return;

                // Hide change container if it exists (when switching to other tabs)
                const existingChangeContainer = document.getElementById('regulationChangeContainer');
                if (existingChangeContainer) {
                    existingChangeContainer.style.display = 'none';
                }
                
                // Show main container for all tabs except change
                if (tabName !== 'change') {
                    container.style.display = 'grid';
                }

                // Tab-specific content handling
                switch (tabName) {
                    case 'summary':
                        // Show summary - reload regulation
                        if (regulationId) {
                            load(regulationId);
                        }
                        break;

                    case 'components':
                        // Show regulation hierarchy
                        container.innerHTML = `
                            <div class="view-section">
                                <div class="section-title">REGULATION HIERARCHY</div>
                                <div class="hierarchy-table-wrapper">
                                    <table class="hierarchy-table">
                                        <thead>
                                            <tr>
                                                <th>Regulation</th>
                                                <th>Description</th>
                                                <th>Last Updated</th>
                                            </tr>
                                        </thead>
                                        <tbody>
                                            <tr>
                                                <td colspan="3" style="text-align: center; padding: 2rem;">
                                                    <i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; color: var(--secondary-color, #248567);"></i>
                                                    <p style="margin-top: 0.5rem;">Loading regulation hierarchy...</p>
                                                </td>
                                            </tr>
                                        </tbody>
                                    </table>
                                </div>
                                <div class="table-footer">Loading...</div>
                            </div>
                        `;
                        loadRegulationHierarchy(regulationId).catch(error => {
                            console.error('Failed to load regulation hierarchy:', error);
                        });
                        break;

                    case 'relationships':
                        loadRelationshipsMainTab(container, regulationId).catch(error => {
                            console.error('Failed to load regulation relationships:', error);
                        });
                        break;

                    case 'stakeholders':
                        container.innerHTML = '<div id="regulationStakeholdersContainer"></div>';
                        // Initialize stakeholder view mode
                        if (window.regulationStakeholderView && window.regulationStakeholderView.init) {
                            window.regulationStakeholderView.init(regulationId);
                        }
                        break;

                    case 'impact':
                        loadRegulationImpactData(regulationId);
                        break;

                    case 'history':
                        container.innerHTML = '<div id="regulationHistoryContainer"></div>';
                        // Initialize history component
                        if (window.HistoryComponent && window.HistoryComponent.create) {
                            window.HistoryComponent.create('Regulation', regulationId, 'regulationHistoryContainer');
                        } else {
                            console.error('HistoryComponent not loaded');
                            container.innerHTML = `
                                <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; color: var(--error, #dc2626); margin-bottom: 1rem; display: block;"></i>
                                    <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                                        History component failed to load.
                                    </p>
                                </div>
                            `;
                        }
                        break;

                    case 'change':
                        // Create or get change container
                        let changeContainer = document.getElementById('regulationChangeContainer');
                        if (!changeContainer) {
                            // Create the container if it doesn't exist
                            changeContainer = document.createElement('div');
                            changeContainer.id = 'regulationChangeContainer';
                            changeContainer.className = 'view-grid';
                            changeContainer.style.display = 'grid';
                            // Insert it into the content body
                            const contentBody = document.querySelector('.content-body');
                            if (contentBody) {
                                contentBody.appendChild(changeContainer);
                            } else {
                                container.appendChild(changeContainer);
                            }
                        } else {
                            changeContainer.style.display = 'grid';
                        }
                        
                        // Hide main container and show change container
                        container.style.display = 'none';
                        
                        // Clear any existing content before initializing
                        changeContainer.innerHTML = '';
                        
                        // Initialize change tab component with sub-tabs (Change Requests and Workflow)
                        if (window.ChangeTabComponent && regulationId) {
                            try {
                                window.ChangeTabComponent.initialize('regulation', regulationId, 'regulationChangeContainer');
                            } catch (error) {
                                console.error('Failed to initialize change tab component:', error);
                                changeContainer.innerHTML = `
                                    <div class="view-section" style="grid-column:1/-1;">
                                        <div class="section-title">CHANGE</div>
                                        <div class="empty-state">
                                            <i class="fas fa-exclamation-triangle"></i>
                                            <p>Failed to initialize change component: ${error.message || 'Unknown error'}</p>
                                        </div>
                                    </div>
                                `;
                            }
                        } else {
                            console.error('ChangeTabComponent not available or regulationId missing', {
                                hasComponent: !!window.ChangeTabComponent,
                                regulationId: regulationId
                            });
                            changeContainer.innerHTML = `
                                <div class="view-section" style="grid-column:1/-1;">
                                    <div class="section-title">CHANGE</div>
                                    <div class="empty-state">
                                        <i class="fas fa-exclamation-triangle"></i>
                                        <p>Change component not available. Please ensure change-tab-component.js is loaded.</p>
                                    </div>
                                </div>
                            `;
                        }
                        break;

                    default:
                        container.innerHTML = `
                            <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                                <i class="fas fa-info-circle" style="font-size: 3rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                                <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                                    ${tabName.replace('-', ' ').toUpperCase()} tab content will be available soon.
                                </p>
                            </div>
                        `;
                        break;
                }
                if (typeof window.syncStakeholderVisibility === 'function') {
                    window.syncStakeholderVisibility('regulationStakeholdersContainer');
                }
            });
        });
    });


    // Load relationships main tab with sub-tabs
    async function loadRelationshipsMainTab(container, regulationId) {
        // Create sub-tabs structure
        container.innerHTML = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="sub-tab-container" style="margin-bottom: 1.5rem; border-bottom: 1px solid var(--border-color, #e9ecef);">
                    <button class="sub-tab active" data-subtab="relationships" style="padding: 0.75rem 1.5rem; border: none; background: none; color: var(--secondary-color, #248567); border-bottom: 2px solid var(--secondary-color, #248567); font-weight: 600; cursor: pointer; margin-right: 1rem;">
                        Relationships
                    </button>
                    <button class="sub-tab" data-subtab="regulatory-themes" style="padding: 0.75rem 1.5rem; border: none; background: none; color: var(--text-secondary, #64748b); border-bottom: 2px solid transparent; font-weight: 500; cursor: pointer;">
                        Regulatory Themes
                    </button>
                </div>
                <div id="relationshipsSubTabContent"></div>
            </div>
        `;

        // Add sub-tab click handlers
        const subTabs = container.querySelectorAll('.sub-tab');
        const subTabContent = container.querySelector('#relationshipsSubTabContent');
        
        subTabs.forEach(subTab => {
            subTab.addEventListener('click', function() {
                const subTabName = this.getAttribute('data-subtab');
                
                // Update active sub-tab styling
                subTabs.forEach(tab => {
                    tab.classList.remove('active');
                    tab.style.color = 'var(--text-secondary, #64748b)';
                    tab.style.borderBottomColor = 'transparent';
                });
                this.classList.add('active');
                this.style.color = 'var(--secondary-color, #248567)';
                this.style.borderBottomColor = 'var(--secondary-color, #248567)';
                
                // Load appropriate sub-tab content
                switch(subTabName) {
                    case 'relationships':
                        loadRelationshipsSubTab(subTabContent, regulationId).catch(error => {
                            console.error('Failed to load relationships sub-tab:', error);
                        });
                        break;
                    case 'regulatory-themes':
                        loadRegulatoryThemesSubTab(subTabContent, regulationId).catch(error => {
                            console.error('Failed to load regulatory themes sub-tab:', error);
                        });
                        break;
                }
            });
        });

        // Load default sub-tab (relationships)
        loadRelationshipsSubTab(subTabContent, regulationId).catch(error => {
            console.error('Failed to load default relationships sub-tab:', error);
        });
    }

    // Load relationships sub-tab
    async function loadRelationshipsSubTab(container, regulationId) {
        try {
            // Show loading state
            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1; padding: 2rem; text-align: center;">
                    <div class="loading-spinner" style="margin-bottom: 1rem;">
                        <i class="fas fa-spinner fa-spin" style="font-size: 2rem; color: var(--secondary-color, #248567);"></i>
                    </div>
                    <p style="color: var(--text-secondary, #64748b);">Loading relationships...</p>
                </div>
            `;

            const response = await fetch(`/api/regulation-relationships/${regulationId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const relationships = await response.json();
            console.log('Relationships data received:', relationships);

            if (!Array.isArray(relationships) || relationships.length === 0) {
                container.innerHTML = `
                    <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                        <i class="fas fa-project-diagram" style="font-size: 3rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                        <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                            No relationships found for this regulation.
                        </p>
                    </div>
                `;
                return;
            }

            // Build relationships table
            const relationshipsTableHtml = `
                <div class="view-section" style="grid-column: 1/-1;">
                    <div class="section-title">RELATIONSHIPS</div>
                    <div class="data-table-wrapper">
                        <table class="data-table">
                            <thead>
                                <tr>
                                    <th>Relationship Type</th>
                                    <th>Related Regulation</th>
                                    <th>Description</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${relationships.map(rel => {
                                    const relationTypeName = rel.effectiveRelationTypeName || 'Unknown Type';
                                    const relatedRegulationName = rel.relatedRegulationName || 'Unnamed Regulation';
                                    const relatedRefNumber = rel.relatedRefNumber || '';
                                    const description = rel.relationshipDescription || '';
                                    
                                    // Create link to related regulation
                                    const relatedRegulationLink = rel.relatedRegulationId ? 
                                        `<a href="/view/regulation/${rel.relatedRegulationId}" class="regulation-link">${escapeHtml(relatedRegulationName)}</a>` : 
                                        escapeHtml(relatedRegulationName);
                                    
                                    // Add ref number if available
                                    const refNumberDisplay = relatedRefNumber ? 
                                        `<span class="ref-number">${escapeHtml(relatedRefNumber)}</span>` : '';
                                    
                                    return `
                                        <tr>
                                            <td>${escapeHtml(relationTypeName)}</td>
                                            <td>${relatedRegulationLink} ${refNumberDisplay}</td>
                                            <td><span title="${escapeHtml(description)}">${escapeHtml(description)}</span></td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                    <div class="table-footer">
                        ${relationships.length} relationship${relationships.length !== 1 ? 's' : ''}
                    </div>
                </div>
            `;

            container.innerHTML = relationshipsTableHtml;

        } catch (error) {
            console.error('Error loading relationships:', error);
            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; color: var(--error, #dc2626); margin-bottom: 1rem; display: block;"></i>
                    <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                        Error loading relationships data.
                    </p>
                    <p style="font-size: 0.875rem; color: var(--text-secondary, #64748b); margin-top: 0.5rem;">
                        ${error.message}
                    </p>
                </div>
            `;
        }
    }

    // Load regulatory themes sub-tab
    async function loadRegulatoryThemesSubTab(container, regulationId) {
        try {
            // Show loading state
            container.innerHTML = `
                <div style="padding: 2rem; text-align: center;">
                    <div class="loading-spinner" style="margin-bottom: 1rem;">
                        <i class="fas fa-spinner fa-spin" style="font-size: 2rem; color: var(--secondary-color, #248567);"></i>
                    </div>
                    <p style="color: var(--text-secondary, #64748b);">Loading regulatory themes...</p>
                </div>
            `;

            const response = await fetch(`/api/regulation-x-regulatorytheme/regulation/${regulationId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const regulatoryThemes = await response.json();
            console.log('Regulatory themes data received:', regulatoryThemes);

            if (!Array.isArray(regulatoryThemes) || regulatoryThemes.length === 0) {
                container.innerHTML = `
                    <div style="padding: 3rem; text-align: center;">
                        <i class="fas fa-file-contract" style="font-size: 3rem; color: var(--text-secondary, #64748b); margin-bottom: 1rem; display: block;"></i>
                        <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                            No regulatory themes found for this regulation.
                        </p>
                    </div>
                `;
                return;
            }

            // Build regulatory themes table
            const regulatoryThemesTableHtml = `
                <div class="section-title">REGULATORY THEME</div>
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Ref</th>
                                <th>Regulations</th>
                                <th>Name</th>
                                <th>Description</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${regulatoryThemes.map(theme => {
                                const themeName = theme.regulatoryTheme || 'Unnamed Theme';
                                const refNumber = theme.refNumber || '—';
                                const description = theme.description || '';
                                const allRegulations = theme.allRegulations || '';
                                const regulationCount = theme.regulationCount || 0;
                                
                                // Make ref number clickable
                                const refLink = theme.regulatoryThemeId ? 
                                    `<a href="/view/regulatory-theme/${theme.regulatoryThemeId}" class="regulation-link">${escapeHtml(refNumber)}</a>` : 
                                    escapeHtml(refNumber);
                                
                                // Display regulations with count indicator if there are multiple
                                let regulationsDisplay = '';
                                if (regulationCount > 1) {
                                    regulationsDisplay = `
                                        <span class="regulations-summary expandable-regulations" data-theme-id="${theme.regulatoryThemeId}" data-expanded="false">
                                            <i class="fas fa-plus-circle expand-icon" style="color: var(--secondary-color, #248567); margin-right: 0.25rem;"></i>
                                            ${regulationCount} regulations
                                        </span>
                                    `;
                                } else if (regulationCount === 1) {
                                    regulationsDisplay = `<span title="${escapeHtml(allRegulations)}">${escapeHtml(allRegulations)}</span>`;
                                } else {
                                    regulationsDisplay = '<span class="empty">No regulations</span>';
                                }
                                
                                return `
                                    <tr>
                                        <td>${refLink}</td>
                                        <td>${regulationsDisplay}</td>
                                        <td>${escapeHtml(themeName)}</td>
                                        <td><span title="${escapeHtml(description)}">${escapeHtml(description)}</span></td>
                                    </tr>
                                `;
                            }).join('')}
                        </tbody>
                    </table>
                </div>
                <div class="table-footer">
                    ${regulatoryThemes.length} regulatory theme${regulatoryThemes.length !== 1 ? 's' : ''}
                </div>
            `;
            
            container.innerHTML = regulatoryThemesTableHtml;
            
            // Add click handlers for expandable regulations
            const expandableElements = container.querySelectorAll('.expandable-regulations');
            expandableElements.forEach(element => {
                element.addEventListener('click', async function(e) {
                    e.preventDefault();
                    const themeId = this.getAttribute('data-theme-id');
                    const isExpanded = this.getAttribute('data-expanded') === 'true';
                    const icon = this.querySelector('.expand-icon');
                    const row = this.closest('tr');
                    
                    if (isExpanded) {
                        // Collapse: Remove expanded row and change icon
                        const expandedRow = row.nextElementSibling;
                        if (expandedRow && expandedRow.classList.contains('expanded-regulations-row')) {
                            expandedRow.remove();
                        }
                        icon.className = 'fas fa-plus-circle expand-icon';
                        this.setAttribute('data-expanded', 'false');
                    } else {
                        // Expand: Load and show detailed regulations
                        try {
                            icon.className = 'fas fa-spinner fa-spin expand-icon';
                            const detailedRegulations = await loadDetailedRegulations(themeId);
                            
                            const expandedRowHtml = `
                                <tr class="expanded-regulations-row">
                                    <td colspan="4" style="padding: 0; border: none;">
                                        <div class="expanded-regulations-container" style="background: var(--background-secondary, #f8f9fa); padding: 1rem; border-left: 3px solid var(--secondary-color, #248567);">
                                            <div class="expanded-regulations-table">
                                                <table class="data-table" style="margin: 0;">
                                                    <thead>
                                                        <tr style="background: var(--background-primary, #fff);">
                                                            <th>Root Regulation</th>
                                                            <th>Ref.</th>
                                                            <th>Regulation</th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        ${detailedRegulations.map(reg => {
                                                            const regulationName = reg.regulation || 'Unnamed Regulation';
                                                            const refNumber = reg.refNumber || '—';
                                                            
                                                            // Show parent regulation as root regulation, or the regulation itself if no parent
                                                            const parentRegulation = reg.parentRegulation;
                                                            let rootRegulationDisplay;
                                                            if (parentRegulation && parentRegulation.trim() !== '') {
                                                                rootRegulationDisplay = escapeHtml(parentRegulation);
                                                            } else {
                                                                rootRegulationDisplay = escapeHtml(regulationName);
                                                            }
                                                            
                                                            // Make ref number clickable
                                                            const refLink = reg.regulationId ? 
                                                                `<a href="/view/regulation/${reg.regulationId}" class="regulation-link">${escapeHtml(refNumber)}</a>` : 
                                                                escapeHtml(refNumber);
                                                            
                                                            return `
                                                                <tr>
                                                                    <td>${rootRegulationDisplay}</td>
                                                                    <td>${refLink}</td>
                                                                    <td>${escapeHtml(regulationName)}</td>
                                                                </tr>
                                                            `;
                                                        }).join('')}
                                                    </tbody>
                                                </table>
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                            `;
                            
                            row.insertAdjacentHTML('afterend', expandedRowHtml);
                            icon.className = 'fas fa-minus-circle expand-icon';
                            this.setAttribute('data-expanded', 'true');
                            
                        } catch (error) {
                            console.error('Error loading detailed regulations:', error);
                            icon.className = 'fas fa-exclamation-triangle expand-icon';
                            icon.style.color = 'var(--error, #dc2626)';
                        }
                    }
                });
            });
            
        } catch (error) {
            console.error('Error loading regulatory themes:', error);
            container.innerHTML = `
                <div style="padding: 3rem; text-align: center;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; color: var(--error, #dc2626); margin-bottom: 1rem; display: block;"></i>
                    <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                        Error loading regulatory themes data.
                    </p>
                    <p style="font-size: 0.875rem; color: var(--text-secondary, #64748b); margin-top: 0.5rem;">
                        ${error.message}
                    </p>
                </div>
            `;
        }
    }

    // Load detailed regulations for a specific regulatory theme
    async function loadDetailedRegulations(regulatoryThemeId) {
        try {
            const response = await fetch(`/api/regulation-x-regulatorytheme/regulatory-theme/${regulatoryThemeId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const regulations = await response.json();
            console.log('Detailed regulations data received for theme', regulatoryThemeId, ':', regulations);
            
            return regulations || [];
            
        } catch (error) {
            console.error('Error loading detailed regulations for theme', regulatoryThemeId, ':', error);
            throw error;
        }
    }


    // Load Regulation Impact data
    function loadRegulationImpactData(regulationId) {
        const container = document.getElementById('glossaryViewContainer');
        if (!container) {
            console.error('Regulation view container not found');
            return;
        }
        
        container.innerHTML = '<div id="regulationImpactContainer"></div>';
        
        if (window.loadRegulationImpact) {
            window.loadRegulationImpact(regulationId);
        } else {
            console.error('loadRegulationImpact function not found');
            const impactContainer = document.getElementById('regulationImpactContainer');
            if (impactContainer) {
                impactContainer.innerHTML = `
                    <div class="view-section" style="grid-column: 1/-1; padding: 3rem; text-align: center;">
                        <i class="fas fa-exclamation-triangle" style="font-size: 3rem; color: var(--error, #dc2626); margin-bottom: 1rem; display: block;"></i>
                        <p style="font-size: 1.125rem; color: var(--text-primary, #1e293b); margin: 0;">
                            Error loading Impact data.
                        </p>
                    </div>
                `;
            }
        }
    }

    // Make functions globally available
})();
