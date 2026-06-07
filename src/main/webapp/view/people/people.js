(function() {
    function parseId() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        const idx = parts.indexOf('people');
        if (idx === -1 || parts.length < idx + 2) return null;
        const id = parseInt(parts[idx + 1], 10);
        return Number.isNaN(id) ? null : id;
    }
    let currentUserPromise = null;

    function getCurrentUserFromSession() {
        const userStr = sessionStorage.getItem('currentUser');
        if (!userStr) return null;
        try {
            const user = JSON.parse(userStr);
            return user && typeof user === 'object' ? user : null;
        } catch (e) {
            console.error('Error parsing user from session:', e);
            return null;
        }
    }

    function extractCurrentUserId(user) {
        if (!user) return null;
        const userId = user.id || user.ID || user.userId || user.UserId || user.user_id || user.User_ID;
        if (userId == null || userId === '' || userId === 'null' || userId === 'undefined') return null;
        const parsedId = parseInt(String(userId), 10);
        return (!isNaN(parsedId) && parsedId > 0) ? parsedId : null;
    }

    async function getCurrentUser(forceRefresh = false) {
        if (forceRefresh) {
            currentUserPromise = null;
        }
        if (!currentUserPromise) {
            currentUserPromise = (async () => {
                try {
                    const response = await fetch('/api/me', { method: 'GET', credentials: 'include' });
                    if (response.ok) {
                        const user = await response.json();
                        sessionStorage.setItem('currentUser', JSON.stringify(user));
                        return user;
                    }
                    console.warn('⚠️ /api/me returned status:', response.status);
                } catch (e) {
                    console.error('❌ Error fetching current user from API:', e);
                }
                return getCurrentUserFromSession();
            })();
        }
        return currentUserPromise;
    }

	function hideEditControls() {
		// Hide the edit dropdown for unauthorized users
		if (window.EditDropdown) {
			window.EditDropdown.hideEditControls();
		}
		// Hide the tab edit button for unauthorized users
		const tabEditBtn = document.getElementById('tabEditBtn');
		if (tabEditBtn) tabEditBtn.style.display = 'none';
		try { document.querySelectorAll('[data-action="edit"]').forEach(function(el){ el.style.display = 'none'; }); } catch(_) {}
	}

	async function hideEditsIfUnauthenticated() {
		try {
			const me = await getCurrentUser();
			if (!me) {
				console.log('Current user not available');
				hideEditControls(); 
				return; 
			}
			const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
			console.log('Normalized role:', role);
			const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
			console.log('Is admin?', isAdmin);
			if (!isAdmin) {
				console.log('Hiding edit controls - user is not admin');
				hideEditControls();
			} else {
				console.log('User is admin - edit controls should be visible');
				showEditControls();
			}
		} catch(e) {
			console.log('Error in hideEditsIfUnauthenticated:', e);
			hideEditControls();
		}
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
		console.log('Edit controls shown for admin user');
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

    // --- Responsibilities row selection (used by Accept / Undo Accept) ---
    let selectedResponsibilityRoleId = null;

    // NOTE: In the responsibilities API, "id" is object_x_people.id (the assignment row id).
    // That's what must be used for accept/undo so the DB row updates correctly.
    function extractResponsibilityRoleId(resp) {
        if (!resp) return null;
        return resp.id ?? resp.ID ?? resp.object_x_people_id ?? resp.objectXPeopleId ??
               resp.roleId ?? resp.role_id ?? resp.roleID ?? resp.Role_ID ?? null;
    }

    function clearResponsibilitySelection() {
        selectedResponsibilityRoleId = null;
        try {
            document.querySelectorAll('.responsibilities-table-content tbody tr.is-selected')
                .forEach(tr => tr.classList.remove('is-selected'));
        } catch (_) {}
    }

    function ensureResponsibilitySelectionStyles() {
        if (document.getElementById('responsibility-row-selection-style')) return;
        const style = document.createElement('style');
        style.id = 'responsibility-row-selection-style';
        style.textContent = `
            .responsibilities-table-content tbody tr { cursor: pointer; }
            .responsibilities-table-content tbody tr.is-selected {
                background: #e7f5ed !important;
                outline: 2px solid #248567;
                outline-offset: -2px;
            }
        `;
        document.head.appendChild(style);
    }

    function addResponsibilityRowSelectionListeners() {
        const tbody = document.querySelector('.responsibilities-table-content tbody');
        if (!tbody) return;

        // Event delegation so it works for all rows
        tbody.addEventListener('click', function (e) {
            const tr = e.target.closest('tr');
            if (!tr) return;

            // Toggle selection
            document.querySelectorAll('.responsibilities-table-content tbody tr')
                .forEach(row => row.classList.remove('is-selected'));
            tr.classList.add('is-selected');

            const roleId = tr.getAttribute('data-role-id');
            selectedResponsibilityRoleId = roleId ? roleId : null;
        });
    }

    function isRoleAccepted(r) {
        return r?.roleAccepted === 'Yes' || r?.roleAccepted === true || r?.roleAccepted === 'true';
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

    function formatDateOnly(value) {
        if (!value) return '';
        try {
            const normalized = typeof value === 'string' ? value.replace(' ', 'T') : value;
            const d = new Date(normalized);
            if (isNaN(d.getTime())) {
                if (typeof value === 'string') {
                    const match = value.match(/^(\d{4}-\d{2}-\d{2})/);
                    return match ? escapeHtml(match[1]) : escapeHtml(value);
                }
                return escapeHtml(String(value));
            }
            return escapeHtml(d.toLocaleDateString());
        } catch (_) {
            return escapeHtml(String(value));
        }
    }

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class=\"empty\">-</span>'}</div></div>`;
    }

    async function resolveReferences(person) {
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // Org unit name from Org_Unit_ID
        if (!person.Org_Unit_Name && (person.Org_Unit_ID || person.org_unit_id)) {
            const ouId = person.Org_Unit_ID ?? person.org_unit_id;
            tasks.push((async () => {
                try {
                    let ou = await api.getOrgUnitById(ouId);
                    if (ou && ou.data) ou = ou.data;
                    person.Org_Unit_Name = ou?.Name || ou?.primaryname || ou?.name || String(ouId);
                } catch(_) { /* ignore */ }
            })());
        }

        // Status name from status_id
        if (!person.Status_Name && (person.status_id || person.Status_ID)) {
            const statusId = person.status_id ?? person.Status_ID;
            tasks.push((async () => {
                try {
                    let s = await api.getStatusById(statusId);
                    if (s && s.data) s = s.data;
                    person.Status_Name = s?.primaryname || s?.Name || s?.name || String(statusId);
                } catch(_) { /* ignore */ }
            })());
        }

        // System role name from System_Role
        if (!person.System_Role_Name && (person.System_Role || person.system_role)) {
            const roleId = person.System_Role ?? person.system_role;
            tasks.push((async () => {
                try {
                    let r = await api.getRoleById(roleId);
                    if (r && r.data) r = r.data;
                    person.System_Role_Name = r?.primaryname || r?.Name || r?.name || String(roleId);
                } catch(_) { /* ignore */ }
            })());
        }

        // Last updated by person id
        if (!person.Last_Updated_By && (person.lastupdateuser_id || person.last_updated_by)) {
            const userId = person.lastupdateuser_id ?? person.last_updated_by;
            tasks.push((async () => {
                try {
                    let u = await api.getPersonById(userId);
                    if (u && u.data) u = u.data;
                    const fullName = [u?.First_Name || u?.first_name, u?.Last_Name || u?.last_name].filter(Boolean).join(' ').trim();
                    person.Last_Updated_By = fullName || u?.Email || u?.email || String(userId);
                } catch(_) { /* ignore */ }
            })());
        }

        await Promise.all(tasks);
        return person;
    }


    async function load(id) {
        const container = document.getElementById('peopleViewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column:1/-1;">${window.I18n ? window.I18n.t('people.loading') : 'Loading...'}</div>`;
        try {
            let p = await window.BUDG_API_SERVICE.getPersonById(id);
             try { window.BUDG_API_SERVICE.logVisit({ entity: 'InvolvedParty', entityId: String(id), route: `/view/people/${id}` }); } catch(_) {}
            if (p && p.data) p = p.data; // unwrap common { success, data }

            p = await resolveReferences(p);
            
            // Show/hide segments tab based on whether viewing own profile
            await updateSegmentsTabVisibility(id);
            const name = `${escapeHtml(p.First_Name || p.first_name || '')} ${escapeHtml(p.Last_Name || p.last_name || '')}`.trim();
            
            // Update the page title with the person's name
            const personDisplayName = document.getElementById('personDisplayName');
            if (personDisplayName) {
                personDisplayName.textContent = name || (window.I18n ? window.I18n.t('facet.people') : 'People');
            }

            const statusName = (p.Status_Name || p.status_name || p.status || '').toString();
            const statusColor = /deleted/i.test(statusName) ? 'var(--required-color)' : /active/i.test(statusName) ? 'var(--secondary-color)' : 'var(--text-secondary)';

            // ABOUT ME section - Description only
            const aboutMe = `
                <div class="view-section" style="padding:10px;">
                    <div class="section-title">${window.I18n ? window.I18n.t('card.aboutMe') : 'ABOUT ME'}</div>
                    <div class="view-item">
                        <div class="view-value">${_richHtml(p.Description || p.description || '')}</div>
                    </div>
                </div>
            `;

            // BUDG DETAILS section with two columns
            const BUDGDetails = `
                <div class="view-section"  style="padding:10px;">
                    <div class="section-title">${window.I18n ? window.I18n.t('card.budgDetails') : 'BUDG DETAILS'}</div>
                    <div class="view-section-content two-column">
                        <div class="view-column">
                            ${renderItem(window.I18n ? window.I18n.t('people.emailAddress') : 'Email Address', `<a href="mailto:${escapeHtml(p.Email || p.email || '')}" style="color: #248567;">${escapeHtml(p.Email || p.email || '')}</a>`)}
                            ${renderItem(window.I18n ? window.I18n.t('people.budgStatus') : 'BUDG Status', `<span style="color: ${statusColor};">${escapeHtml(statusName || (window.I18n ? window.I18n.t('value.active') : 'Active'))}</span>`)}
                        </div>
                        <div class="view-column">
                            ${renderItem(window.I18n ? window.I18n.t('people.profile') : 'Profile', escapeHtml(p.System_Role_Name || p.system_role_name || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.lifecycle') : 'Lifecycle', escapeHtml(p.lifecycle_name || p.Lifecycle_Name || (window.I18n ? window.I18n.t('value.working') : 'Working')))}
                            ${renderItem(window.I18n ? window.I18n.t('people.lastLogin') : 'Last Login', formatDateTime(p.last_user_login || p.Last_Login || p.last_User_LogIn || p.lastLogin || p.last_login))}
                        </div>
                    </div>
                </div>
            `;

            // PERSONAL DETAILS section with two columns
            const personalDetails = `
                <div class="view-section" style="padding:10px;">
                    <div class="section-title">${window.I18n ? window.I18n.t('card.personalDetails') : 'PERSONAL DETAILS'}</div>
                    <div class="view-section-content two-column">
                        <div class="view-column">
                            ${renderItem(window.I18n ? window.I18n.t('people.name') : 'Name', name)}
                            ${renderItem(window.I18n ? window.I18n.t('people.lanId') : 'LAN ID', escapeHtml(p.lan_id || p.LAN_ID || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.location') : 'Location', escapeHtml(p.office_location || p.Office_Location || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.mobile') : 'Mobile', escapeHtml(p.mobile_telephone || p.Mobile_Telephone || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.employeeType') : 'Employee Type', escapeHtml(p.employment_type_name || p.Employment_Type_Name || (window.I18n ? window.I18n.t('value.internal') : 'Internal')))}
                            ${renderItem(window.I18n ? window.I18n.t('people.company') : 'Company', escapeHtml(p.company_name || p.Company_Name || p.external_company_name || p.External_Company_Name || ''))}
                        </div>
                        <div class="view-column">
                            ${(() => {
                                const orgUnitId = p.Org_Unit_ID || p.org_unit_id;
                                const orgUnitName = p.Org_Unit_Name || p.org_unit_name || '';
                                if (orgUnitId && orgUnitName) {
                                    return renderItem(window.I18n ? window.I18n.t('people.orgUnit') : 'Org Unit', `<a href="/view/org-unit/${encodeURIComponent(orgUnitId)}" style="color: #248567;"><i class="fas fa-users" style="margin-right: 5px;"></i>${escapeHtml(orgUnitName)}</a>`);
                                } else if (orgUnitName) {
                                    return renderItem(window.I18n ? window.I18n.t('people.orgUnit') : 'Org Unit', `<span style="color: #6b7280;"><i class="fas fa-users" style="margin-right: 5px;"></i>${escapeHtml(orgUnitName)}</span>`);
                                } else {
                                    return renderItem(window.I18n ? window.I18n.t('people.orgUnit') : 'Org Unit', '');
                                }
                            })()}
                            ${renderItem(window.I18n ? window.I18n.t('people.function') : 'Function', escapeHtml(p.Function_Name || p.function_name || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.functionDescription') : 'Function Description', _richHtml(p.Function_Description || p.function_description || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.internalMailCode') : 'Internal Mail Code', escapeHtml(p.internal_mail_code || p.Internal_Mail_Code || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.telephone') : 'Telephone', escapeHtml(p.office_telephone || p.Office_Telephone || ''))}
                            ${renderItem(window.I18n ? window.I18n.t('people.employedSince') : 'Employed Since', formatDateOnly(p.employed_since || p.Employed_Since || ''))}
                        </div>
                    </div>
                </div>
            `;

            // TEAM SUMMARY section with two columns
            const teamSummary = `
                <div class="view-section" style="padding:10px;">
                    <div class="section-title">${window.I18n ? window.I18n.t('card.teamSummary') : 'TEAM SUMMARY'}</div>
                    <div class="view-section-content two-column">
                        <div class="view-column">
                            ${renderItem(window.I18n ? window.I18n.t('people.assistant') : 'Assistant', window.I18n ? window.I18n.t('people.noAssistant') : 'No assistant')}
                            ${renderItem(window.I18n ? window.I18n.t('people.directReports') : 'Direct Reports', '0')}
                        </div>
                        <div class="view-column">
                            ${renderItem(window.I18n ? window.I18n.t('people.lineManagers') : 'Line Managers', window.I18n ? window.I18n.t('people.noManager') : 'No Manager')}
                            ${renderItem(window.I18n ? window.I18n.t('people.totalReports') : 'Total Reports', '0')}
                        </div>
                    </div>
                </div>
            `;

            // Render in single-column layout with two-column sections
            container.innerHTML = `
                <div class="people-view-container">
                    ${aboutMe}
                    ${BUDGDetails}
                    ${personalDetails}
                    ${teamSummary}
                </div>
                
            `;
            
            // Render custom fields section
            if (window.CustomFields) {
                try {
                    await window.CustomFields.renderViewSection({
                        facetId: 'People',
                        containerId: 'peopleViewContainer',
                        objectId: id,
                        title: window.I18n ? window.I18n.t('card.customFields') : 'CUSTOM FIELDS'
                    });
                } catch (error) {
                    console.error('Error rendering custom fields:', error);
                }
            }

            // Build in-page section navigator based on rendered sections
            buildSectionNavigator();
            
            // Load team summary data first
            await loadTeamSummaryData(id);
            
        } catch (e) {
            const isForbidden = e?.status === 403 || String(e?.message || '').includes('403');
            const errorMsg = isForbidden
                ? 'This object is not available.'
                : (window.I18n ? window.I18n.t('people.failedToLoad', {id: id}) : `Failed to load person (id=${id}).`);
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">${errorMsg}</div>`;
        }
    }

    function buildSectionNavigator() {
        try {
            const body = document.querySelector('.content-body');
            if (!body) return;
            // Remove existing navigator if present
            const existing = body.querySelector('.people-section-nav');
            if (existing) existing.remove();

            const sections = Array.from(body.querySelectorAll('.view-section .section-title'));
            if (sections.length === 0) return;

            // Ensure each section has an id for anchor links
            const links = [];
            sections.forEach((titleEl, index) => {
                const label = (titleEl.textContent || `Section ${index + 1}`).trim();
                const id = `people-sec-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-${index}`;
                const hostSection = titleEl.closest('.view-section');
                if (hostSection) {
                    hostSection.setAttribute('id', id);
                    links.push({ id, label });
                }
            });

            if (!links.length) return;

            const nav = document.createElement('nav');
            nav.className = 'people-section-nav';
            nav.setAttribute('aria-label', 'Section navigation');
            body.appendChild(nav);

            // Smooth scroll behavior
            nav.querySelectorAll('a[data-anchor]').forEach(a => {
                a.addEventListener('click', (e) => {
                    e.preventDefault();
                    const targetId = a.getAttribute('data-anchor');
                    const target = document.getElementById(targetId);
                    if (target) {
                        const headerOffset = 80; // approximate header height
                        const rect = target.getBoundingClientRect();
                        const top = window.scrollY + rect.top - headerOffset;
                        window.scrollTo({ top, behavior: 'smooth' });
                    }
                });
            });

            // Active highlighting on scroll
            const activate = () => {
                const scrollPos = window.scrollY + 100;
                let activeId = null;
                links.forEach(({ id }) => {
                    const el = document.getElementById(id);
                    if (!el) return;
                    const y = el.offsetTop;
                    if (y <= scrollPos) activeId = id;
                });
                nav.querySelectorAll('a[data-anchor]').forEach(a => {
                    a.classList.toggle('active', a.getAttribute('data-anchor') === activeId);
                });
            };
            document.addEventListener('scroll', activate, { passive: true });
            activate();
        } catch (_) {
            // Non-fatal
        }
    }

    async function loadTeamSummaryData(id) {
        try {
            const teamData = await window.BUDG_API_SERVICE.getPersonTeam(id);
            
            if (!teamData) {
                return;
            }

            const management = teamData.management || [];
            const reports = teamData.reports || [];
            
            // Count direct reports
            const directReports = reports.length;
            
            // Count total reports (including indirect reports)
            let totalReports = directReports;
            for (const report of reports) {
                // This would need to be implemented to count indirect reports
                // For now, we'll just use direct reports
            }
            
            // Get line managers
            const lineManagers = management.map(manager => {
                const fullName = `${manager.firstName || ''} ${manager.lastName || ''}`.trim();
                return `<a href="/view/people/${manager.id || manager.ID}" style="color: #248567;">${escapeHtml(fullName)}</a>`;
            }).join(', ');
            
            // Update the team summary section - find the one with "TEAM SUMMARY" title
            const teamSummarySections = document.querySelectorAll('.view-section');
            let teamSummarySection = null;
            for (const section of teamSummarySections) {
                const title = section.querySelector('.section-title');
                if (title && title.textContent === 'TEAM SUMMARY') {
                    if (true) {
                        teamSummarySection = section;
                        break;
                    }
                }
            }
            
            if (teamSummarySection) {
                const teamSummaryHtml = `
                    <div class="view-section">
                        <div class="section-title">${window.I18n ? window.I18n.t('card.teamSummary') : 'TEAM SUMMARY'}</div>
                        ${renderItem(window.I18n ? window.I18n.t('people.assistant') : 'Assistant', window.I18n ? window.I18n.t('people.noAssistant') : 'No assistant')}
                        ${renderItem(window.I18n ? window.I18n.t('people.lineManagers') : 'Line Managers', lineManagers || (window.I18n ? window.I18n.t('message.noLineManagers') : 'No line managers'))}
                        ${renderItem(window.I18n ? window.I18n.t('people.directReports') : 'Direct Reports', directReports.toString())}
                        ${renderItem(window.I18n ? window.I18n.t('people.totalReports') : 'Total Reports', totalReports.toString())}
                    </div>
                `;
                teamSummarySection.outerHTML = teamSummaryHtml;
            }
        } catch (e) {
            console.error('Failed to load team summary data:', e);
            // Keep the default values if loading fails
        }
    }

    async function loadTeamData(id) {
        const container = document.getElementById('peopleTeamContainer');
        if (!container) return;
        
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loadingTeamData') : 'Loading team data...'}</div>`;
        
        try {
            const teamResponse = await window.BUDG_API_SERVICE.getPersonTeam(id);
            
            // Debug: Log the teamResponse structure first
            console.log('Team response received from API:', teamResponse);
            console.log('Team response type:', typeof teamResponse);
            console.log('Is array:', Array.isArray(teamResponse));
            
            // Extract teamData from response (handle both direct array and wrapped response)
            let teamData = teamResponse;
            if (teamResponse && teamResponse.data) {
                teamData = teamResponse.data;
                console.log('Extracted teamData from response.data:', teamData);
            }
            
            if (!teamData || (Array.isArray(teamData) && teamData.length === 0)) {
                container.innerHTML = `
                    <div class="people-section">
                        <div class="people-header">
                            <div class="people-title">${window.I18n ? window.I18n.t('card.team') : 'TEAM'}</div>
                        </div>
                        <div class="people-content">
                            <div class="people-empty">
                                <i class="fas fa-users"></i>
                                <span>${window.I18n ? window.I18n.t('people.noTeamDataAvailable') : 'No team data available'}</span>
                            </div>
                        </div>
                    </div>
                `;
                return;
            }

                // Handle team data structure with reports, management, and peers
                const reports = Array.isArray(teamData.reports) ? teamData.reports : [];
                const management = Array.isArray(teamData.management) ? teamData.management : [];
                const peers = Array.isArray(teamData.peers) ? teamData.peers : [];
                
                console.log('Team data breakdown:');
                console.log('- Reports:', reports.length);
                console.log('- Management:', management.length);
                console.log('- Peers:', peers.length);
                
                // Normalize team members (same as edit page)
                const normalizeTeamMember = (raw, defaultType = '') => {
                    if (!raw) {
                        return {
                            relationshipId: null,
                            id: null,
                            firstName: '',
                            lastName: '',
                            email: '',
                            functionName: '',
                            orgUnitId: null,
                            orgUnitName: '',
                            telephone: '',
                            mobile: '',
                            type: defaultType || '',
                            isNew: false
                        };
                    }

                    const relationType = raw.relationType ?? raw.Relation_Type ?? '';
                    const normalizedType = raw.type ? raw.type : (relationType || defaultType || '');
                    return {
                        ...raw,
                        relationshipId: raw.relationshipId ?? raw.relationship_id ?? raw.Relationship_ID ?? null,
                        id: raw.id ?? raw.ID ?? null,
                        firstName: raw.firstName ?? raw.First_Name ?? '',
                        lastName: raw.lastName ?? raw.Last_Name ?? '',
                        email: raw.email ?? raw.Email ?? '',
                        functionName: raw.functionName ?? raw.Function_Name ?? '',
                        orgUnitId: raw.orgUnitId ?? raw.Org_Unit_ID ?? null,
                        orgUnitName: raw.orgUnitName ?? raw.Org_Unit_Name ?? '',
                        telephone: raw.telephone ?? raw.Telephone ?? '',
                        mobile: raw.mobile ?? raw.Mobile ?? '',
                        type: normalizedType,
                        isNew: false
                    };
                };
                
                // Create team sections using table format (same as edit page)
                const createTeamSection = (title, data, columns, getColLabel, translateType) => {
                    const count = data.length;
                    if (count === 0) {
                        let noFoundMsg = '';
                        if (title === (window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT') || title === 'MANAGEMENT') {
                            noFoundMsg = window.I18n ? window.I18n.t('people.noManagementFound') : 'No management found';
                        } else if (title === (window.I18n ? window.I18n.t('people.peers') : 'PEERS') || title === 'PEERS') {
                            noFoundMsg = window.I18n ? window.I18n.t('people.noPeersFound') : 'No peers found';
                        } else if (title === (window.I18n ? window.I18n.t('people.reports') : 'REPORTS') || title === 'REPORTS') {
                            noFoundMsg = window.I18n ? window.I18n.t('people.noReportsFound') : 'No reports found';
                        } else {
                            noFoundMsg = window.I18n ? window.I18n.t('people.noTeamDataAvailable') : `No ${title.toLowerCase()} found`;
                        }
                        return `
                            <div class="team-section">
                                <div class="team-section-header">
                                    <h3>${title}</h3>
                                </div>
                                <div class="team-table" style="width: 100%; overflow-x: auto;">
                                    <div class="team-empty">
                                        <span>${noFoundMsg}</span>
                                    </div>
                                </div>
                            </div>
                        `;
                    }
                    const headerLabels = getColLabel ? columns.map(c => getColLabel(c)) : columns;
                    const sectionHtml = `
                        <div class="team-section">
                            <div class="team-section-header">
                                <h3>${title}</h3>
                            </div>
                            <div class="team-table" style="width: 100%; overflow-x: auto;">
                                <table class="team-table-content" style="width: 100%; min-width: 1200px; table-layout: auto;">
                                    <thead>
                                        <tr>
                                        ${headerLabels.map(col => `<th style="padding: 12px 8px; text-align: left; border-bottom: 1px solid #ddd; white-space: nowrap; min-width: 120px;">${escapeHtml(col)}</th>`).join('')}
                                        </tr>
                                    </thead>
                                    <tbody>
                                    ${data.map((item, index) => createTeamRow(item, columns, index, translateType)).join('')}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    `;
                    return sectionHtml;
                };
                
                const createTeamRow = (item, columns, index, translateType) => {
                    const getCellValue = (item, column) => {
                        switch (column) {
                            case 'Manager':
                            case 'Name':
                                const fullName = `${item.firstName || ''} ${item.lastName || ''}`.trim();
                                const personId = item.id;
                                return personId ? `<a href="/view/people/${personId}" style="color: #248567;">${escapeHtml(fullName)}</a>` : escapeHtml(fullName);
                            case 'Function':
                                return escapeHtml(item.functionName || '');
                            case 'Org Unit':
                                return escapeHtml(item.orgUnitName || '');
                            case 'Email':
                                return escapeHtml(item.email || '');
                            case 'Telephone':
                                return escapeHtml(item.telephone || '');
                            case 'Mobile':
                                return escapeHtml(item.mobile || '');
                            case 'Type':
                                return escapeHtml(translateType ? translateType(item.type) : (item.type || ''));
                            default:
                                return '';
                        }
                    };
                    
                    return `<tr>
                        ${columns.map(col => `<td style="padding: 12px 8px; border-bottom: 1px solid #eee; white-space: nowrap;">${getCellValue(item, col)}</td>`).join('')}
                    </tr>`;
                };
                
                // Normalize the data
                const getTeamColumnLabel = (col) => {
                    const I = window.I18n && window.I18n.t.bind(window.I18n);
                    if (!I) return col;
                    const keyMap = { Manager: 'people.teamColumnManager', Name: 'people.teamColumnName', Type: 'people.teamColumnType', Function: 'people.function', 'Org Unit': 'people.orgUnit', Email: 'people.emailAddress', Telephone: 'people.telephone', Mobile: 'people.mobile' };
                    return I(keyMap[col] || col) || col;
                };
                const translateTeamType = (val) => {
                    if (!val) return '';
                    const I = window.I18n && window.I18n.t.bind(window.I18n);
                    if (!I) return String(val);
                    const v = String(val).trim();
                    if (/direct\s*report/i.test(v)) return I('people.typeDirectReport');
                    if (/peer/i.test(v)) return I('people.typePeer');
                    if (/manager/i.test(v)) return I('people.teamColumnManager');
                    return v;
                };
                const normalizedManagement = management.map((item) => normalizeTeamMember(item, 'Manager'));
                const normalizedReports = reports.map((item) => normalizeTeamMember(item, 'Direct report'));
                const normalizedPeers = peers.map((item) => normalizeTeamMember(item, 'Peer'));
                
                const managementTitle = window.I18n ? window.I18n.t('people.management') : 'MANAGEMENT';
                const peersTitle = window.I18n ? window.I18n.t('people.peers') : 'PEERS';
                const reportsTitle = window.I18n ? window.I18n.t('people.reports') : 'REPORTS';
                
                const teamHtml = `
                    ${createTeamSection(managementTitle, normalizedManagement, ['Manager', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type'], getTeamColumnLabel, translateTeamType)}
                    ${createTeamSection(peersTitle, normalizedPeers, ['Name', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type'], getTeamColumnLabel, translateTeamType)}
                    ${createTeamSection(reportsTitle, normalizedReports, ['Name', 'Function', 'Org Unit', 'Email', 'Telephone', 'Mobile', 'Type'], getTeamColumnLabel, translateTeamType)}
                `;

            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('card.team') : 'TEAM'}</div>
                    </div>
                    <div class="people-content">
                        ${teamHtml}
                    </div>
                </div>
            `;
        } catch (e) {
            console.error('Failed to load team data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('card.team') : 'TEAM'}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load team data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    async function loadResponsibilitiesData(id) {
        const container = document.getElementById('peopleResponsibilitiesContainer');
        if (!container) return;
        
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loadingResponsibilities') : 'Loading responsibilities...'}</div>`;
        
        try {
            const responsibilitiesData = await window.BUDG_API_SERVICE.getPersonResponsibilities(id);
            
            // Build a lookup map for Object Role Type id -> primaryname
            let objectRoleTypeMap = new Map();
            try {
                // cache across navigations
                if (window._objectRoleTypeMap instanceof Map && window._objectRoleTypeMap.size) {
                    objectRoleTypeMap = window._objectRoleTypeMap;
                } else {
                    const resp = await fetch('/api/object-roles', { credentials: 'include' });
                    if (resp.ok) {
                        const data = await resp.json();
                        const roleTypes = data.roleTypes || data.data?.roleTypes || [];
                        roleTypes.forEach(rt => {
                            const id = rt?.id ?? rt?.ID;
                            const name = rt?.primaryname ?? rt?.primaryName ?? rt?.name ?? rt?.Name;
                            if (id != null && name) {
                                objectRoleTypeMap.set(String(id), String(name));
                            }
                        });
                        window._objectRoleTypeMap = objectRoleTypeMap;
                    }
                }
            } catch(_) { /* non-fatal */ }
            const getRoleTypeName = (resp) => {
                const raw = resp.objectRoleTypeId ?? resp.object_role_type_id ?? resp.objectroletype_id ?? resp.objectroletypeid ?? resp.roleType ?? resp.role_type ?? resp.Role_Type;
                if (raw == null || raw === '') return window.I18n ? window.I18n.t('value.stewardshipRole') : 'Stewardship Role';
                const val = String(raw);
                // number-like means id
                if (/^\d+$/.test(val)) {
                    return objectRoleTypeMap.get(val) || val;
                }
                return val;
            };
            
            // Debug logging
            console.log('Responsibilities data received:', responsibilitiesData);
            
            if (!responsibilitiesData || responsibilitiesData.length === 0) {
                container.innerHTML = `
                    <div class="people-section">
                        <div class="people-header">
                            <div class="people-title">${window.I18n ? window.I18n.t('card.responsibilities') : 'RESPONSIBILITIES'}</div>
                        </div>
                        <div class="people-content">
                            <div class="people-empty">
                                <i class="fas fa-tasks"></i>
                                <span>${window.I18n ? window.I18n.t('people.noResponsibilitiesAssigned') : 'No responsibilities assigned'}</span>
                            </div>
                        </div>
                    </div>
                `;
                return;
            }

            // Group responsibilities by object type for filtering
            const groupedResponsibilities = responsibilitiesData.reduce((groups, resp) => {
                const objectType = resp.objectType || 'Other';
                if (!groups[objectType]) groups[objectType] = [];
                groups[objectType].push(resp);
                return groups;
            }, {});

            // Create facet filter tabs with "All" tab first
            const allTab = `<button class="facet-tab active" data-facet="All">All <span class="badge">${responsibilitiesData.length}</span></button>`;
            const facetTabs = allTab + Object.keys(groupedResponsibilities).map(facet => {
                const count = groupedResponsibilities[facet].length;
                return `<button class="facet-tab" data-facet="${escapeHtml(facet)}">${escapeHtml(facet)} <span class="badge">${count}</span></button>`;
            }).join('');

            const pT = (k) => (window.I18n && window.I18n.t(k)) || k;
            const respAllLabel = pT('people.respAll');
            // Create responsibilities table
            const responsibilitiesTable = `
                <div class="responsibilities-table">
                    <div class="table-header">
                        <h3>${respAllLabel} <span class="badge">${responsibilitiesData.length}</span></h3>
                        <div class="table-actions">
                            <i class="fas fa-cog"></i>
                        </div>
                    </div>
                    <div class="table-filters">
                        ${facetTabs}
                    </div>
                    <table class="responsibilities-table-content">
                        <thead>
                            <tr>
                                <th>${pT('people.respRole')}</th>
                                <th>${pT('people.respRoleType')}</th>
                                <th>${pT('people.respObjectName')}</th>
                                <th>${pT('people.teamColumnType')}</th>
                                <th>${pT('people.respSegment')}</th>
                                <th>${pT('people.respDelegateOf')}</th>
                                <th>${pT('people.budgStatus')}</th>
                                <th>${pT('people.respRoleStatus')}</th>
                                <th>${pT('people.respRoleAccepted')}</th>
                                <th>${pT('people.respRoleAcceptedOn')}</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${responsibilitiesData.map(resp => {
                                // Check if user has segment access (masked if no access)
                                const isMasked = resp.isMasked === true;
                                
                                // Build object name cell - masked or with link
                                let objectNameCell;
                                if (isMasked) {
                                    // Masked name - show lock icon and no link
                                    objectNameCell = `<span class="masked-object" style="color: #9ca3af;" title="You don't have access to this object's segment"><i class="fas fa-lock" style="margin-right: 6px;"></i> ${escapeHtml(resp.objectName || 'XXXXXXX')}</span>`;
                                } else {
                                    // Get object URL if we have objectId
                                    const objectId = resp.objectId || resp.object_id;
                                    const objectUrl = objectId ? getObjectViewUrl(resp.objectType, objectId) : null;
                                    let objectName = resp.objectName;
                                    
                                    // If object name is NULL but we have objectId, it might be deleted
                                    if (!objectName && objectId) {
                                        objectName = 'Unknown Object (may be deleted)';
                                    } else if (!objectName) {
                                        objectName = 'Unknown Object';
                                    }
                                    
                                    const iconClass = getObjectIcon(resp.objectType);
                                    
                                    if (objectUrl) {
                                        // Valid URL - create clickable link
                                        objectNameCell = `<a href="${objectUrl}" class="object-link"><i class="fas fa-${iconClass}"></i> ${escapeHtml(objectName)}</a>`;
                                    } else {
                                        // No URL (missing objectId or unknown type) - show without link
                                        objectNameCell = `<span><i class="fas fa-${iconClass}"></i> ${escapeHtml(objectName)}</span>`;
                                    }
                                }
                                
                                return `
                                <tr data-facet="${escapeHtml(resp.objectType || 'Other')}"
                                    data-role-id="${escapeHtml(extractResponsibilityRoleId(resp) ?? '')}"
                                    style="display: table-row;">
                                    <td>
                                        <div class="role-info">
                                            <strong>${escapeHtml(resp.roleName || 'Unknown Role')}</strong>
                                        </div>
                                    </td>
                                    <td>${escapeHtml(getRoleTypeName(resp))}</td>
                                    <td>${objectNameCell}</td>
                                    <td>${escapeHtml(resp.objectType || 'Unknown')}</td>
                                    <td>${escapeHtml(resp.segmentName || resp.segment_name || 'Unknown')}</td>
                                    <td>${resp.delegate ? escapeHtml(`${resp.delegate.firstName} ${resp.delegate.lastName}`) : ''}</td>
                                    <td>
                                        <span class="status-badge ${getStatusClass(resp.roleStatus)}">
                                            ${escapeHtml(resp.roleStatus || 'Active')}
                                        </span>
                                    </td>
                                    <td>
                                        <span class="status-badge active">
                                            ${escapeHtml(resp.roleStatus || 'Active')}
                                        </span>
                                    </td>
                                    <td>${escapeHtml(resp.roleAccepted || '-')}</td>
                                    <td>${resp.roleAcceptedOn ? formatDate(resp.roleAcceptedOn) : ''}</td>
                                </tr>
                            `}).join('')}
                        </tbody>
                    </table>
                </div>
            `;

            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('card.responsibilities') : 'RESPONSIBILITIES'}</div>
                    </div>
                    <div class="people-content">
                        ${responsibilitiesTable}
                    </div>
                </div>
            `;

            // Add event listeners for facet filtering
            addFacetFilterListeners();

            // Enable row selection (used by Accept / Undo Accept)
            ensureResponsibilitySelectionStyles();
            clearResponsibilitySelection();
            addResponsibilityRowSelectionListeners();
        } catch (e) {
            console.error('Failed to load responsibilities:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('card.responsibilities') : 'RESPONSIBILITIES'}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load responsibilities'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    async function loadFollowingData(id) {
        const container = document.getElementById('peopleFollowingContainer');
        if (!container) return;

        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loadingFollowing') : 'Loading following data...'}</div>`;

        try {
            const response = await window.BUDG_API_SERVICE.getPersonFollowing(id);
            const records = Array.isArray(response?.records) ? response.records : [];
            const facets = Array.isArray(response?.facets) ? response.facets : [];

            const followTitle = (window.I18n && window.I18n.t('tab.following')) || 'FOLLOWING';
            const followEmpty = (window.I18n && window.I18n.t('people.followingEmpty')) || 'Not following anything';
            renderFollowingSection(container, records, facets, {
                title: followTitle,
                emptyMessage: followEmpty,
                wrap: true
            });
        } catch (e) {
            console.error('Failed to load following data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('tab.following') : 'FOLLOWING'}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load following data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    function renderFollowingSection(container, records, facets, options = {}) {
        const title = options.title || '';
        const emptyMessage = options.emptyMessage || 'No records found';
        const wrap = options.wrap !== false;

        const safeRecords = Array.isArray(records) ? records : [];
        const safeFacets = Array.isArray(facets) && facets.length
            ? facets
            : [{ type: 'All', count: safeRecords.length }];

        if (safeRecords.length === 0) {
            if (wrap) {
                container.innerHTML = `
                    <div class="people-section">
                        <div class="people-header">
                            <div class="people-title">${escapeHtml(title)}</div>
                        </div>
                        <div class="people-content">
                            <div class="people-empty">
                                <i class="fas fa-eye"></i>
                                <span>${escapeHtml(emptyMessage)}</span>
                            </div>
                        </div>
                    </div>
                `;
            } else {
                container.innerHTML = `
                    ${title ? `<h4 class="activity-card-title">${escapeHtml(title)}</h4>` : ''}
                    <div class="people-empty">
                        <i class="fas fa-eye"></i>
                        <span>${escapeHtml(emptyMessage)}</span>
                    </div>
                `;
            }
            return;
        }

        const filtersHtml = safeFacets.map((facet, index) => {
            const rawType = facet.type || 'All';
            const encodedType = encodeURIComponent(rawType);
            return `
            <button type="button" class="facet-tab${index === 0 ? ' active' : ''}" data-type="${encodedType}">
                ${escapeHtml(rawType)}
                <span class="facet-count">${facet.count ?? 0}</span>
            </button>
        `;
        }).join('');

        const fT = (k) => (window.I18n && window.I18n.t(k)) || k;
        const tableHtml = `
            <div class="facet-tab-row">
                ${filtersHtml}
            </div>
            <div class="table-responsive">
                <table class="people-table following-table">
                    <thead>
                        <tr>
                            <th>${fT('people.teamColumnType')}</th>
                            <th>${fT('people.ref')}</th>
                            <th>${fT('people.teamColumnName')}</th>
                            <th>${fT('people.description')}</th>
                            <th>${fT('people.followingLastUpdate')}</th>
                            <th>${fT('people.followingComments')}</th>
                            <th>${fT('people.followingReason')}</th>
                            <th>${fT('people.followingIncludeChildren')}</th>
                        </tr>
                    </thead>
                    <tbody></tbody>
                </table>
            </div>
            <div class="table-meta"></div>
        `;

        if (wrap) {
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${escapeHtml(title)}</div>
                    </div>
                    <div class="people-content">
                        ${tableHtml}
                    </div>
                </div>
            `;
        } else {
            container.innerHTML = `
                ${title ? `<h4 class="activity-card-title">${escapeHtml(title)}</h4>` : ''}
                ${tableHtml}
            `;
        }

        const tbody = container.querySelector('tbody');
        const meta = container.querySelector('.table-meta');

        const recordLabel = (n) => {
            const I = window.I18n && window.I18n.t.bind(window.I18n);
            if (n === 1 && I) return I('people.recordOne') || '1 record';
            if (I) return (I('people.recordCount') || '{count} records').replace('{count}', n);
            return n + ' record' + (n === 1 ? '' : 's');
        };
        const renderRows = (rows) => {
            tbody.innerHTML = rows.map(buildFollowingRow).join('');
            meta.textContent = recordLabel(rows.length);
        };

        renderRows(safeRecords);

        container.querySelectorAll('.facet-tab').forEach(button => {
            button.addEventListener('click', () => {
                container.querySelectorAll('.facet-tab').forEach(b => b.classList.remove('active'));
                button.classList.add('active');
                const encoded = button.getAttribute('data-type') || 'All';
                const type = decodeURIComponent(encoded);
                const filtered = type === 'All'
                    ? safeRecords
                    : safeRecords.filter(item => (item.type || '').toLowerCase() === (type || '').toLowerCase());
                renderRows(filtered);
            });
        });
    }

    function buildFollowingRow(record) {
        const yesNo = (v) => (window.I18n && window.I18n.t(v ? 'people.followingYes' : 'people.followingNo')) || (v ? 'Yes' : 'No');
        const includeChildren = yesNo(!!record.includeChildren);
        const lastUpdated = formatDisplayDate(record.lastUpdated);
        const lastUpdatedBy = record.lastUpdatedBy
            ? `<div class="table-subtext">${escapeHtml(record.lastUpdatedBy)}</div>`
            : '';

        const isSystem = (record.type || '').toLowerCase() === 'system';
        const systemRef = record.objectId ?? record.object_id ?? record.id ?? record.reference;
        const reference = isSystem ? systemRef : (record.reference ?? systemRef);

        const comments = record.additionalInfo ?? record.additional_info ?? record.description ?? record.comments;
        const reason = record.reason || record.reasonDescription || record.reason_description || '';
        
        // Check if user has segment access (masked if no access)
        const isMasked = record.isMasked === true;
        
        // Build name cell - masked or normal
        let nameCell;
        if (isMasked) {
            nameCell = `<span class="masked-object" style="color: #9ca3af;" title="You don't have access to this object's segment"><i class="fas fa-lock" style="margin-right: 6px;"></i> ${escapeHtml(record.name || 'XXXXXXX')}</span>`;
        } else {
            nameCell = escapeHtml(record.name || '-');
        }

        return `
            <tr>
                <td>${escapeHtml(record.type || '-')}</td>
                <td>${escapeHtml(reference ?? '-')}</td>
                <td>${nameCell}</td>
                <td>${escapeHtml(record.description || '-')}</td>
                <td>
                    <div>${lastUpdated}</div>
                    ${lastUpdatedBy}
                </td>
                <td>${escapeHtml(comments || '-')}</td>
                <td>${escapeHtml(reason || '-')}</td>
                <td>${includeChildren}</td>
            </tr>
        `;
    }

    function formatDisplayDate(value) {
        if (!value) return '-';
        try {
            return escapeHtml(formatDate(value));
        } catch (_) {
            return escapeHtml(String(value));
        }
    }

    async function loadActivityData(id) {
        const container = document.getElementById('peopleActivityContainer');
        if (!container) return;

        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n ? window.I18n.t('people.loading') : 'Loading activity stream...'}</div>`;

        try {
            const response = await window.BUDG_API_SERVICE.getPersonActivity(id) || {};
            const notification = response.notification || null;
            const stakeholderRecords = Array.isArray(response.stakeholder) ? response.stakeholder : [];
            const followingRecords = Array.isArray(response.following) ? response.following : [];
            const followingFacets = Array.isArray(response.followingFacets) ? response.followingFacets : [];
            
            // Store original records for filtering
            window.originalStakeholderRecords = stakeholderRecords;
            window.originalFollowingRecords = followingRecords;
            window.originalFollowingFacets = followingFacets;

            const aT = (k) => (window.I18n && window.I18n.t(k)) || k;
            // Build HTML with subtabs and filter section
            container.innerHTML = `
                <div class="people-section" style="width: 100%;">
                    <div class="people-header">
                        <div class="people-title">${aT('tab.activity')}</div>
                    </div>
                    <div class="people-content" style="padding: 0;">
                        <div class="relationships-sub-tabs">
                            <button class="sub-tab active" data-sub-tab="my-activity">${aT('people.activityMyActivity')}</button>
                            <button class="sub-tab" data-sub-tab="my-jobs">${aT('people.activityMyJobs')}</button>
                            <button class="sub-tab" data-sub-tab="my-api-tokens">${aT('people.activityMyAPITokens')}</button>
                        </div>
                        <div class="activity-stream-layout" style="display: flex; gap: 20px;">
                            <div class="activity-main-content" style="flex: 1;">
                                <div class="relationships-content">
                                    <div id="myActivityContent" class="sub-tab-content active" style="display:block;"></div>
                                    <div id="myJobsContent" class="sub-tab-content" style="display:none;"></div>
                                    <div id="myAPITokensContent" class="sub-tab-content" style="display:none;"></div>
                                </div>
                            </div>
                            <div class="activity-filter-panel" style="width: 300px; flex-shrink: 0; display: block;">
                                <div class="activity-block" style="margin-bottom: 0;">
                                    <h4>${aT('people.activityFilter')}</h4>
                                    <div class="filter-form" style="padding: 15px 0;">
                                        <div class="form-group" style="margin-bottom: 15px;">
                                            <label class="form-label" style="display: block; margin-bottom: 5px; font-size: 13px; color: #374151; font-weight: 500;">${aT('people.activityFromDate')}</label>
                                            <div class="form-input-group" style="position: relative;">
                                                <input type="date" class="form-input" id="activityFromDate" style="padding-right: 35px; width: 100%;">
                                                <i class="fas fa-calendar" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); color: #6b7280; pointer-events: none;"></i>
                                            </div>
                                        </div>
                                        <div class="form-group" style="margin-bottom: 20px;">
                                            <label class="form-label" style="display: block; margin-bottom: 5px; font-size: 13px; color: #374151; font-weight: 500;">${aT('people.activityToDate')}</label>
                                            <div class="form-input-group" style="position: relative;">
                                                <input type="date" class="form-input" id="activityToDate" style="padding-right: 35px; width: 100%;">
                                                <i class="fas fa-calendar" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); color: #6b7280; pointer-events: none;"></i>
                                            </div>
                                        </div>
                                        <button type="button" class="btn btn-primary" id="activityFilterBtn" style="width: 100%; display: flex; align-items: center; justify-content: center; gap: 8px;">
                                            <i class="fas fa-check"></i>
                                            ${aT('people.activityFilterButton')}
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            // Setup subtab switching
            setupActivitySubTabs(container, id);
            
            // Setup filter functionality
            setupActivityFilter(id);

            // Load initial subtab content (My Activity) - put all current content here
            const myActivityContainer = document.getElementById('myActivityContent');
            if (myActivityContainer) {
                const frequency = notification?.value ? escapeHtml(notification.value) : (window.I18n?.t('message.notSpecified') || 'Not specified');
                const updatedParts = [];
                if (notification?.updatedAt) {
                    updatedParts.push(formatDisplayDate(notification.updatedAt));
                }
                if (notification?.updatedBy) {
                    updatedParts.push(escapeHtml(notification.updatedBy));
                }
                const notificationMeta = updatedParts.join(' \u2022 ');

                const aT2 = (k) => (window.I18n && window.I18n.t(k)) || k;
                myActivityContainer.innerHTML = `
                    <div class="activity-column">
                        <section class="activity-block">
                            <h4>${aT2('people.activityNotifications')}</h4>
                            <div class="notification-row">
                                <span class="label">${aT2('people.activityNotificationFrequency')}</span>
                                <span class="value">${frequency}</span>
                            </div>
                            ${notificationMeta ? `<div class="notification-meta">${notificationMeta}</div>` : ''}
                        </section>
                        <section class="activity-block">
                            <h4>${aT2('people.activityIAmStakeholder')}</h4>
                            <div data-activity-stakeholder></div>
                        </section>
                        <section class="activity-block">
                            <h4>${aT2('people.activityIAmFollowing')}</h4>
                            <div data-activity-following></div>
                        </section>
                    </div>
                `;
                
                const stakeholderContainer = myActivityContainer.querySelector('[data-activity-stakeholder]');
                if (stakeholderContainer) {
                    renderStakeholderSection(stakeholderContainer, stakeholderRecords);
                }

                const followingContainer = myActivityContainer.querySelector('[data-activity-following]');
                if (followingContainer) {
                    const noFollowActivity = (window.I18n && window.I18n.t('people.followingNoActivity')) || 'No following activity recorded';
                    renderFollowingSection(followingContainer, followingRecords, followingFacets, {
                        emptyMessage: noFollowActivity,
                        wrap: false
                    });
                }
            }

            // Leave My Jobs subtab empty - will load when clicked
            const myJobsContainer = document.getElementById('myJobsContent');
            if (myJobsContainer) {
                myJobsContainer.innerHTML = '';
            }

            const myAPITokensContainer = document.getElementById('myAPITokensContent');
            if (myAPITokensContainer) {
                const noTokens = (window.I18n && window.I18n.t('people.activityNoAPITokens')) || 'No API tokens found';
                myAPITokensContainer.innerHTML = `
                    <div class="people-empty">
                        <i class="fas fa-key"></i>
                        <span>${noTokens}</span>
                    </div>
                `;
            }
        } catch (e) {
            console.error('Failed to load activity data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('tab.activity') : 'ACTIVITY STREAM'}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load activity data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    function setupActivitySubTabs(container, id) {
        const subTabs = container.querySelectorAll('.sub-tab');
        
        if (subTabs.length === 0) return;
        
        subTabs.forEach(function(subTab) {
            subTab.addEventListener('click', function(e) {
                e.preventDefault();
                
                // Remove active class from all sub-tabs
                subTabs.forEach(function(t) { t.classList.remove('active'); });
                this.classList.add('active');
                
                const subTabName = this.getAttribute('data-sub-tab');
                
                // Update URL with subtab parameter
                const url = new URL(window.location);
                url.searchParams.set('subtab', subTabName);
                window.history.pushState({}, '', url);
                
                // Hide all sub-tab contents
                const subTabContents = container.querySelectorAll('.sub-tab-content');
                subTabContents.forEach(function(content) {
                    content.classList.remove('active');
                    content.style.display = 'none';
                });
                
                // Show/hide filter panel based on active tab
                const filterPanel = document.querySelector('.activity-filter-panel');
                if (filterPanel) {
                    if (subTabName === 'my-activity') {
                        filterPanel.style.display = 'block';
                    } else {
                        filterPanel.style.display = 'none';
                    }
                }
                
                // Show selected sub-tab content
                let targetSubTab;
                if (subTabName === 'my-activity') {
                    targetSubTab = document.getElementById('myActivityContent');
                } else if (subTabName === 'my-jobs') {
                    targetSubTab = document.getElementById('myJobsContent');
                    // Load jobs data when switching to My Jobs tab
                    if (targetSubTab && (targetSubTab.innerHTML.trim() === '' || targetSubTab.innerHTML.includes('No jobs found'))) {
                        loadMyJobsContent(targetSubTab, id);
                    }
                } else if (subTabName === 'my-api-tokens') {
                    targetSubTab = document.getElementById('myAPITokensContent');
                    // Load API tokens when switching to My API Tokens tab
                    if (targetSubTab && window.userTokensModule) {
                        window.userTokensModule.loadUserTokens(targetSubTab);
                    }
                }
                
                if (targetSubTab) {
                    targetSubTab.classList.add('active');
                    targetSubTab.style.display = 'block';
                }
            });
        });
    }

    function setupActivityFilter(id) {
        const fromDateInput = document.getElementById('activityFromDate');
        const toDateInput = document.getElementById('activityToDate');
        const filterBtn = document.getElementById('activityFilterBtn');
        
        if (!fromDateInput || !toDateInput || !filterBtn) return;
        
        // Set default dates (first day of current month to today)
        const today = new Date();
        const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
        
        // Format dates as YYYY-MM-DD
        const formatDateForInput = (date) => {
            const year = date.getFullYear();
            const month = String(date.getMonth() + 1).padStart(2, '0');
            const day = String(date.getDate()).padStart(2, '0');
            return `${year}-${month}-${day}`;
        };
        
        fromDateInput.value = formatDateForInput(firstDayOfMonth);
        toDateInput.value = formatDateForInput(today);
        
        // Apply filter on button click
        filterBtn.addEventListener('click', function() {
            applyActivityFilter(id);
        });
        
        // Apply filter on Enter key in date inputs
        [fromDateInput, toDateInput].forEach(input => {
            input.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    applyActivityFilter(id);
                }
            });
        });
    }

    function applyActivityFilter(id) {
        const fromDateInput = document.getElementById('activityFromDate');
        const toDateInput = document.getElementById('activityToDate');
        
        if (!fromDateInput || !toDateInput) return;
        
        const fromDate = fromDateInput.value ? new Date(fromDateInput.value + 'T00:00:00') : null;
        const toDate = toDateInput.value ? new Date(toDateInput.value + 'T23:59:59') : null;
        
        // Validate dates
        if (fromDate && toDate && fromDate > toDate) {
            const msg = (window.I18n && window.I18n.t('people.activityDateValidation')) || 'From Date must be before or equal to To Date';
            alert(msg);
            return;
        }
        
        // Get original records
        const originalStakeholder = window.originalStakeholderRecords || [];
        const originalFollowing = window.originalFollowingRecords || [];
        
        // Filter stakeholder records by date range (based on lastUpdated).
        // Records with no lastUpdated are always included — they have no date to compare against.
        let filteredStakeholder = originalStakeholder;
        if (fromDate || toDate) {
            filteredStakeholder = originalStakeholder.filter(record => {
                if (!record.lastUpdated) return true;
                const recordDate = new Date(record.lastUpdated);
                if (fromDate && recordDate < fromDate) return false;
                if (toDate && recordDate > toDate) return false;
                return true;
            });
        }

        // Filter following records by date range (based on lastUpdated).
        // Records with no lastUpdated are always included.
        let filteredFollowing = originalFollowing;
        if (fromDate || toDate) {
            filteredFollowing = originalFollowing.filter(record => {
                if (!record.lastUpdated) return true;
                const recordDate = new Date(record.lastUpdated);
                if (fromDate && recordDate < fromDate) return false;
                if (toDate && recordDate > toDate) return false;
                return true;
            });
        }
        
        // Re-render the sections with filtered data
        const myActivityContainer = document.getElementById('myActivityContent');
        if (myActivityContainer) {
            const stakeholderContainer = myActivityContainer.querySelector('[data-activity-stakeholder]');
            if (stakeholderContainer) {
                renderStakeholderSection(stakeholderContainer, filteredStakeholder);
            }
            
            const followingContainer = myActivityContainer.querySelector('[data-activity-following]');
            if (followingContainer) {
                // Get following facets from stored original data
                const followingFacets = window.originalFollowingFacets || [];
                renderFollowingSection(followingContainer, filteredFollowing, followingFacets, {
                    emptyMessage: 'No following activity recorded',
                    wrap: false
                });
            }
        }
    }

    const MY_JOBS_PAGE_SIZE = 50;

    async function fetchMyJobsPage(personId, offset, limit = MY_JOBS_PAGE_SIZE) {
        const params = new URLSearchParams({
            limit: String(limit),
            offset: String(offset)
        });
        params.append('userId', personId);
        const response = await fetch(`/api/bulk/jobs/history?${params}`, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' }
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        const data = await response.json();
        return Array.isArray(data.jobs) ? data.jobs : [];
    }

    function buildMyJobsRows(jobs) {
        return jobs.map(job => {
            const statusBadge = getJobStatusBadge(job.status);
            const resultsHtml = buildJobResults(job);
            const dateHtml = formatJobDate(job.created_date, job.completed_date);
            const uploadOptionText = getUploadOptionText(job.upload_option);
            const actionsHtml = buildJobActions(job);

            return `
                <tr>
                    <td>
                        <div style="font-weight: 500; color: var(--text-primary, #2c3e50);">${escapeHtml(job.reference_name || 'N/A')}</div>
                    </td>
                    <td>
                        <div style="display: flex; flex-direction: column; gap: 0.25rem;">
                            <span class="entity-badge">
                                ${escapeHtml(job.entity || 'Regulator')}
                            </span>
                            ${uploadOptionText ? `<span class="upload-option-text">${escapeHtml(uploadOptionText)}</span>` : ''}
                        </div>
                    </td>
                    <td>
                        ${statusBadge}
                    </td>
                    <td>
                        <div style="font-weight: 500; color: var(--text-primary, #2c3e50);">${job.items_count || 0}</div>
                    </td>
                    <td>
                        ${resultsHtml}
                    </td>
                    <td>
                        ${dateHtml}
                    </td>
                    <td style="text-align: right; white-space: nowrap; min-width: 120px;">
                        ${actionsHtml}
                    </td>
                </tr>
            `;
        }).join('');
    }

    function renderMyJobsTable(container, state) {
        const jobs = Array.isArray(state.jobs) ? state.jobs : [];
        if (jobs.length === 0) {
            container.innerHTML = `
                <div class="people-empty" style="padding: 3rem;">
                    <i class="fas fa-briefcase" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <span>No jobs found</span>
                </div>
            `;
            return;
        }

        const tableRows = buildMyJobsRows(jobs);
        const canLoadMore = state.hasMore === true;
        const isLoadingMore = state.loadingMore === true;

        container.innerHTML = `
            <div class="jobs-table-container">
                <div class="table-wrapper">
                    <table class="jobs-table">
                        <thead>
                            <tr>
                                <th>Reference</th>
                                <th>Entity</th>
                                <th>Status</th>
                                <th>Items</th>
                                <th>Results</th>
                                <th>Date</th>
                                <th style="text-align: right;">Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${tableRows}
                        </tbody>
                    </table>
                </div>
                <div class="jobs-table-footer" style="display:flex; align-items:center; justify-content:space-between; gap: 1rem; flex-wrap: wrap;">
                    <span>Showing ${jobs.length} job${jobs.length !== 1 ? 's' : ''}</span>
                    ${canLoadMore ? `
                        <button type="button" class="btn btn-secondary" data-action="load-more-jobs" ${isLoadingMore ? 'disabled' : ''}>
                            ${isLoadingMore ? 'Loading...' : 'Load 50 more'}
                        </button>
                    ` : '<span style="color: var(--text-secondary, #6b7280);">All loaded</span>'}
                </div>
            </div>
        `;

        setupJobActionButtons(container);
        const loadMoreBtn = container.querySelector('button[data-action="load-more-jobs"]');
        if (loadMoreBtn) {
            loadMoreBtn.addEventListener('click', () => loadMoreMyJobsContent(container));
        }
    }

    async function loadMoreMyJobsContent(container) {
        const state = container.__myJobsState;
        if (!state || state.loadingMore || !state.hasMore) return;
        state.loadingMore = true;
        renderMyJobsTable(container, state);
        try {
            const nextPage = await fetchMyJobsPage(state.personId, state.offset, MY_JOBS_PAGE_SIZE);
            state.jobs = state.jobs.concat(nextPage);
            state.offset += nextPage.length;
            state.hasMore = nextPage.length === MY_JOBS_PAGE_SIZE;
        } catch (e) {
            console.error('Failed to load more jobs data:', e);
            alert('Failed to load more jobs. Please try again.');
        } finally {
            state.loadingMore = false;
            renderMyJobsTable(container, state);
        }
    }

    async function loadMyJobsContent(container, personId) {
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1; text-align: center; padding: 2rem;">Loading jobs...</div>';

        try {
            // Use personId from URL, not current user
            if (!personId) {
                container.innerHTML = `
                    <div class="people-empty" style="padding: 3rem;">
                        <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                        <span>Person ID not found</span>
                    </div>
                `;
                return;
            }

            const firstPage = await fetchMyJobsPage(personId, 0, MY_JOBS_PAGE_SIZE);
            container.__myJobsState = {
                personId,
                jobs: firstPage,
                offset: firstPage.length,
                hasMore: firstPage.length === MY_JOBS_PAGE_SIZE,
                loadingMore: false
            };
            renderMyJobsTable(container, container.__myJobsState);
        } catch (e) {
            console.error('Failed to load jobs data:', e);
            container.innerHTML = `
                <div class="people-empty" style="padding: 3rem;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 1rem; opacity: 0.5;"></i>
                    <span>Failed to load jobs data</span>
                </div>
            `;
        }
    }

    function getJobStatusBadge(status) {
        const statusLower = (status || '').toLowerCase();
        let bgColor, textColor, borderColor, icon;
        
        if (statusLower.includes('pending')) {
            bgColor = '#fef3c7';
            textColor = '#d97706';
            borderColor = '#fed7aa';
            icon = '⏳';
        } else if (statusLower.includes('processing')) {
            bgColor = '#dbeafe';
            textColor = '#2563eb';
            borderColor = '#bfdbfe';
            icon = '⚙️';
        } else if (statusLower.includes('partially completed')) {
            bgColor = '#fef3c7';
            textColor = '#d97706';
            borderColor = '#fed7aa';
            icon = '⚠';
        } else if (statusLower.includes('completed')) {
            bgColor = '#d1fae5';
            textColor = '#059669';
            borderColor = '#a7f3d0';
            icon = '✓';
        } else if (statusLower.includes('failed')) {
            bgColor = '#fee2e2';
            textColor = '#dc2626';
            borderColor = '#fecaca';
            icon = '✕';
        } else {
            bgColor = '#f3f4f6';
            textColor = '#374151';
            borderColor = '#e5e7eb';
            icon = '•';
        }

        return `
            <span style="display: inline-flex; align-items: center; padding: 0.25rem 0.75rem; border-radius: 999px; font-size: 0.75rem; font-weight: 500; border: 1px solid ${borderColor}; background: ${bgColor}; color: ${textColor};">
                <span style="margin-right: 0.375rem; font-size: 0.75rem;">${icon}</span>
                ${escapeHtml(status || 'Unknown')}
            </span>
        `;
    }

    function buildJobResults(job) {
        const results = [];
        const isDeleteJob = job.upload_option === 'DELETE' || job.upload_option === 'Remove Existing Items';
        const insertedCount = (isDeleteJob ? 0 : (job.inserted || 0));
        if (insertedCount > 0) {
            results.push(`<span class="result-item inserted"><i class="fas fa-check-circle"></i>${insertedCount} inserted</span>`);
        }
        if (job.updated > 0) {
            results.push(`<span class="result-item updated"><i class="fas fa-sync-alt"></i>${job.updated} updated</span>`);
        }
        if (job.deleted > 0) {
            results.push(`<span class="result-item deleted"><i class="fas fa-trash-alt"></i>${job.deleted} deleted</span>`);
        }
        if (job.failed > 0) {
            results.push(`<span class="result-item failed"><i class="fas fa-times-circle"></i>${job.failed} failed</span>`);
        }
        
        if (results.length === 0) {
            return '<span style="color: var(--text-secondary, #6c757d); font-size: 0.75rem;">No results</span>';
        }
        
        return `<div style="display: flex; flex-direction: column; gap: 0.25rem;">${results.join('')}</div>`;
    }

    function formatJobDate(createdDate, completedDate) {
        const formatDate = (dateString) => {
            if (!dateString) return '';
            try {
                const date = new Date(dateString);
                return date.toLocaleString('en-US', {
                    year: 'numeric',
                    month: 'short',
                    day: 'numeric',
                    hour: '2-digit',
                    minute: '2-digit'
                });
            } catch (e) {
                return dateString;
            }
        };

        const created = formatDate(createdDate);
        const completed = formatDate(completedDate);

        if (completed) {
            return `
                <div class="date-created">${escapeHtml(created)}</div>
                <div class="date-completed">
                    <i class="fas fa-check-circle"></i>
                    ${escapeHtml(completed)}
                </div>
            `;
        }
        return `<div class="date-created">${escapeHtml(created || 'N/A')}</div>`;
    }

    function getUploadOptionText(uploadOption) {
        if (!uploadOption) return '';
        if (uploadOption === 'Add New Items' || uploadOption === 'Insert') return 'Upload New Items';
        if (uploadOption === 'Update Existing Items' || uploadOption === 'Update') return 'Upload Update Items';
        if (uploadOption === 'Remove Existing Items' || uploadOption === 'Delete') return 'Upload Delete Items';
        return uploadOption;
    }

    function normalizeBulkEntityForDownload(entity) {
        let entityLower = (entity || 'regulator').toString().trim().toLowerCase();

        if (entityLower.includes(' x ') || entityLower.includes('_x_') || entityLower.startsWith('relationship')) {
            return 'relationships';
        }
        if (entityLower.includes('role')) {
            return 'role';
        }

        // If entity comes from technical_entity, keep underscore-separated key format.
        if (!entityLower.includes(' ') && !entityLower.includes('.')) {
            const compactAlias = {
                legalentity: 'legal',
                legal_entity: 'legal',
                datamigration: 'data_migration',
                datamigrationimport: 'data_migration'
            };
            return compactAlias[entityLower] || entityLower;
        }

        entityLower = entityLower
            .replace(/\s+(upload\s+new|update|delete|upload)\s+.*$/i, '')
            .replace(/\s+\d+\s+items?$/i, '')
            .replace(/\s+items?$/i, '')
            .replace(/\s+/g, '')
            .replace(/\./g, '');

        const aliasMap = {
            legalentity: 'legal',
            datamigrationimport: 'data_migration'
        };
        return aliasMap[entityLower] || entityLower || 'regulator';
    }

    function buildJobActions(job) {
        const entity = (job.technical_entity || job.entity || 'Regulator').toLowerCase();
        const jobId = job.job_id;
        // Show report button if there are failed items OR if job status is 'Failed' OR 'Partially Completed'
        const statusLower = (job.status || '').toLowerCase();
        const hasFailed = (job.failed || 0) > 0 || 
                         statusLower === 'failed' || 
                         statusLower.includes('partially completed');
        const hasDownloadableFile = job.has_downloadable_file !== false;
        const hasReport = hasFailed && hasDownloadableFile;
        
        let actionsHtml = '<div style="display: flex; align-items: center; justify-content: flex-end; gap: 0.5rem; flex-wrap: nowrap; min-width: fit-content;">';
        
        // Report button (only if there are failed items or job status is Failed)
        if (hasReport) {
            actionsHtml += `
                <button 
                    class="job-action-btn job-action-btn-report" 
                    data-entity="${escapeHtml(entity)}" 
                    data-job-id="${jobId}"
                    data-action="report"
                    style="display: inline-flex; align-items: center; padding: 0.375rem 0.75rem; border: none; border-radius: 4px; font-size: 0.875rem; font-weight: 500; cursor: pointer; background: #2563eb; color: white; transition: background 0.2s; white-space: nowrap; flex-shrink: 0;"
                    onmouseover="this.style.background='#1d4ed8'"
                    onmouseout="this.style.background='#2563eb'"
                    title="Download Error Report"
                >
                    <i class="fas fa-download" style="margin-right: 0.375rem; font-size: 0.75rem;"></i>
                    Report
                </button>
            `;
        }
        
        if (hasDownloadableFile) {
            actionsHtml += `
                <button 
                    class="job-action-btn job-action-btn-file" 
                    data-entity="${escapeHtml(entity)}" 
                    data-job-id="${jobId}"
                    data-action="file"
                    style="display: inline-flex; align-items: center; padding: 0.375rem 0.75rem; border: 1px solid var(--border-color, #d1d5db); border-radius: 4px; font-size: 0.875rem; font-weight: 500; cursor: pointer; background: white; color: var(--text-primary, #374151); transition: background 0.2s; white-space: nowrap; flex-shrink: 0;"
                    onmouseover="this.style.background='var(--background-secondary, #f9fafb)'"
                    onmouseout="this.style.background='white'"
                    title="Download Original File"
                >
                    <i class="fas fa-file-download" style="margin-right: 0.375rem; font-size: 0.75rem;"></i>
                    File
                </button>
            `;
        }

        if (!hasReport && !hasDownloadableFile) {
            actionsHtml += '<span style="color: var(--text-secondary, #6b7280); font-size: 0.8rem;">No file</span>';
        }
        
        actionsHtml += '</div>';
        return actionsHtml;
    }

    function setupJobActionButtons(container) {
        const buttons = container.querySelectorAll('.job-action-btn');
        buttons.forEach(button => {
            button.addEventListener('click', async function(e) {
                e.preventDefault();
                const action = this.getAttribute('data-action');
                const entity = this.getAttribute('data-entity') || 'regulator';
                const jobId = this.getAttribute('data-job-id');
                
                if (!jobId) {
                    alert('Error: Job ID is missing');
                    return;
                }
                
                // Disable button during download
                this.disabled = true;
                const originalText = this.innerHTML;
                this.innerHTML = '<i class="fas fa-spinner fa-spin" style="margin-right: 0.375rem;"></i>Loading...';
                
                try {
                    if (action === 'report') {
                        await downloadJobReport(entity, jobId);
                    } else if (action === 'file') {
                        await downloadJobFile(entity, jobId);
                    }
                } catch (err) {
                    console.error('Download error:', err);
                    
                    // Build detailed error message for user
                    let errorMsg = 'Failed to download';
                    if (err.message) {
                        errorMsg += ': ' + err.message;
                    }
                    
                    // Add additional details if available
                    if (err.status) {
                        errorMsg += `\n\nStatus: ${err.status}`;
                    }
                    if (err.url) {
                        errorMsg += `\nURL: ${err.url}`;
                    }
                    if (err.entity && err.normalizedEntity) {
                        errorMsg += `\nEntity: "${err.entity}" (normalized to: "${err.normalizedEntity}")`;
                    }
                    if (err.jobId) {
                        errorMsg += `\nJob ID: ${err.jobId}`;
                    }
                    
                    // Show detailed error to user
                    alert(errorMsg + '\n\nPlease check the browser console for more details.');
                } finally {
                    // Re-enable button
                    this.disabled = false;
                    this.innerHTML = originalText;
                }
            });
        });
    }

    async function downloadJobReport(entity, jobId) {
        try {
            const entityLower = normalizeBulkEntityForDownload(entity);
            
            const response = await fetch(`/api/bulk/${entityLower}/report/${jobId}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const report = await response.json();
            
            // Create blob and download
            const blob = new Blob([JSON.stringify(report, null, 2)], { 
                type: 'application/json' 
            });
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = `job_${jobId}_report.json`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Failed to download report:', err);
            throw err;
        }
    }

    async function downloadJobFile(entity, jobId) {
        try {
            console.log('📥 [downloadJobFile] Starting download:', { entity, jobId });
            
            const entityLower = normalizeBulkEntityForDownload(entity);
            console.log('📥 [downloadJobFile] Original entity (lowercase):', entityLower);
            console.log('📥 [downloadJobFile] Normalized entity:', entity, '->', entityLower);
            
            const downloadUrl = `/api/bulk/${entityLower}/download/${jobId}`;
            console.log('📥 [downloadJobFile] Download URL:', downloadUrl);
            
            const response = await fetch(downloadUrl, {
                method: 'GET',
                credentials: 'include'
            });

            console.log('📥 [downloadJobFile] Response status:', response.status, response.statusText);
            
            if (!response.ok) {
                // Try to get error message from response body
                let errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                let errorDetails = null;
                
                try {
                    // Clone response to read body without consuming the original
                    const responseClone = response.clone();
                    const contentType = response.headers.get('content-type');
                    
                    if (contentType && contentType.includes('application/json')) {
                        const errorData = await responseClone.json();
                        errorDetails = errorData;
                        if (errorData.message) {
                            errorMessage = errorData.message;
                        } else if (errorData.error) {
                            errorMessage = errorData.error;
                        }
                        console.error('📥 [downloadJobFile] Error response body:', errorData);
                    } else {
                        const textResponse = await responseClone.text();
                        if (textResponse && textResponse.trim()) {
                            console.error('📥 [downloadJobFile] Error response text:', textResponse);
                            errorMessage = textResponse.length > 200 ? textResponse.substring(0, 200) + '...' : textResponse;
                        }
                    }
                } catch (parseError) {
                    console.warn('📥 [downloadJobFile] Could not parse error response:', parseError);
                }
                
                const fullError = new Error(errorMessage);
                fullError.status = response.status;
                fullError.url = downloadUrl;
                fullError.entity = entity;
                fullError.normalizedEntity = entityLower;
                fullError.jobId = jobId;
                console.error('📥 [downloadJobFile] Download failed:', {
                    status: response.status,
                    statusText: response.statusText,
                    url: downloadUrl,
                    entity: entity,
                    normalizedEntity: entityLower,
                    jobId: jobId,
                    errorMessage: errorMessage
                });
                throw fullError;
            }

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            
            // Get filename from Content-Disposition header or use default
            const contentDisposition = response.headers.get('Content-Disposition');
            let filename = `job_${jobId}_file.xlsx`;
            if (contentDisposition) {
                // Try different patterns for Content-Disposition header
                // Pattern 1: filename="filename.xlsx" or filename='filename.xlsx'
                let filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/i);
                if (filenameMatch) {
                    filename = filenameMatch[1].replace(/^['"]|['"]$/g, '');
                } else {
                    // Pattern 2: filename*=UTF-8''filename.xlsx
                    filenameMatch = contentDisposition.match(/filename\*=UTF-8''(.+)/i);
                    if (filenameMatch) {
                        filename = decodeURIComponent(filenameMatch[1]);
                    } else {
                        // Pattern 3: filename=filename.xlsx (without quotes)
                        filenameMatch = contentDisposition.match(/filename=([^;]+)/i);
                        if (filenameMatch) {
                            filename = filenameMatch[1].trim();
                        }
                    }
                }
                // Clean up filename (remove any trailing underscores or spaces)
                filename = filename.trim().replace(/[_\s]+$/, '');
            }
            
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
            
            console.log('✅ [downloadJobFile] File downloaded successfully:', filename);
        } catch (err) {
            console.error('❌ [downloadJobFile] Failed to download file:', err);
            console.error('❌ [downloadJobFile] Error details:', {
                message: err.message,
                status: err.status,
                url: err.url,
                entity: err.entity,
                normalizedEntity: err.normalizedEntity,
                jobId: err.jobId,
                stack: err.stack
            });
            throw err;
        }
    }

    function renderStakeholderSection(container, records) {
        if (!container) return;
        const safeRecords = Array.isArray(records) ? records : [];
        if (safeRecords.length === 0) {
            container.innerHTML = `
                <div class="people-empty">
                    <i class="fas fa-info-circle"></i>
                    <span>${window.I18n ? window.I18n.t('people.noStakeholderActivity') : 'No stakeholder activity recorded'}</span>
                </div>
            `;
            return;
        }

        container.innerHTML = buildStakeholderTable(safeRecords);
        
        // Add expand/collapse functionality
        setupStakeholderExpandCollapse(container);
    }

    function setupStakeholderExpandCollapse(container) {
        const parentRows = container.querySelectorAll('.stakeholder-parent-row');
        
        parentRows.forEach(row => {
            row.addEventListener('click', async function(e) {
                // Don't trigger if clicking on a link
                if (e.target.closest('a')) {
                    return;
                }
                
                const detailRowId = this.getAttribute('data-detail-id');
                const detailRow = document.getElementById(detailRowId);
                const expandIcon = this.querySelector('.expand-icon');
                const objectType = this.getAttribute('data-object-type');
                const objectId = this.getAttribute('data-object-id');
                const stakeholderSince = this.getAttribute('data-stakeholder-since');
                
                if (!detailRow) return;
                
                const isExpanded = detailRow.style.display !== 'none';
                
                if (isExpanded) {
                    // Collapse
                    detailRow.style.display = 'none';
                    this.classList.remove('expanded');
                    expandIcon.classList.remove('expanded');
                    expandIcon.classList.remove('fa-minus');
                    expandIcon.classList.add('fa-plus');
                } else {
                    // Expand
                    detailRow.style.display = 'table-row';
                    this.classList.add('expanded');
                    expandIcon.classList.add('expanded');
                    expandIcon.classList.remove('fa-plus');
                    expandIcon.classList.add('fa-minus');
                    
                    // Load history if not already loaded
                    const detailContent = detailRow.querySelector('.stakeholder-detail-content');
                    const loadingDiv = detailContent.querySelector('.loading-history');
                    const historyContainer = detailContent.querySelector('.history-table-container');
                    
                    if (loadingDiv && loadingDiv.style.display !== 'none') {
                        loadingDiv.style.display = 'block';
                        historyContainer.style.display = 'none';
                        
                        await loadObjectHistory(objectType, objectId, historyContainer, stakeholderSince);
                        
                        loadingDiv.style.display = 'none';
                        historyContainer.style.display = 'block';
                    }
                }
            });
        });
    }

    function buildStakeholderTable(records) {
        // Sort by Last Updated, newest first (oldest sinks to the bottom).
        // Records without a date are treated as oldest. Copy first so we don't
        // mutate the caller's array (e.g. window.originalStakeholderRecords).
        const sortedRecords = [...records].sort((a, b) => {
            const dateA = a && a.lastUpdated ? new Date(a.lastUpdated).getTime() : 0;
            const dateB = b && b.lastUpdated ? new Date(b.lastUpdated).getTime() : 0;
            return dateB - dateA;
        });
        const rows = sortedRecords.map((record, index) => {
            const eventsHtml = buildEventSummary(record.events);
            const objectType = escapeHtml(record.type || '-');
            const objectName = escapeHtml(record.name || '-');
            const objectId = record.objectId || record.object_id;
            const reference = escapeHtml(record.reference || '-');
            const rowId = `stakeholder-row-${index}`;
            const detailRowId = `stakeholder-detail-${index}`;
            
            // Check if user has segment access (masked if no access)
            const isMasked = record.isMasked === true;
            
            // Get object icon
            const iconClass = getObjectIcon(record.type) || 'fa-file';
            
            // Build object name with link (no link if masked)
            let objectNameHtml;
            if (isMasked) {
                // Masked name - show lock icon and no link
                objectNameHtml = `<span class="masked-object" style="color: #9ca3af;" title="You don't have access to this object's segment"><i class="fas fa-lock" style="margin-right: 6px;"></i> ${objectName}</span>`;
            } else {
                const objectUrl = getObjectViewUrl(record.type, objectId, reference);
                objectNameHtml = objectUrl 
                    ? `<a href="${objectUrl}" class="object-link"><i class="fas ${iconClass}"></i> ${objectName}</a>`
                    : `<i class="fas ${iconClass}"></i> ${objectName}`;
            }
            
            // Store stakeholderSince timestamp for filtering history
            const stakeholderSince = record.stakeholderSince || null;
            
            return `
                <tr class="stakeholder-parent-row" data-row-id="${rowId}" data-detail-id="${detailRowId}" data-object-type="${escapeHtml(record.type)}" data-object-id="${objectId}" data-module-id="" data-stakeholder-since="${stakeholderSince || ''}">
                    <td style="width: 40px; text-align: center; cursor: pointer;">
                        <i class="fas fa-plus expand-icon" style="color: #248567; transition: transform 0.3s ease;"></i>
                    </td>
                    <td>${objectType}</td>
                    <td>${objectNameHtml}</td>
                    <td>${eventsHtml}</td>
                    <td>${escapeHtml(record.lastUpdatedBy || '-')}</td>
                    <td>${formatDisplayDate(record.lastUpdated)}</td>
                </tr>
                <tr class="stakeholder-detail-row" id="${detailRowId}" style="display: none;">
                    <td colspan="6" style="padding: 0; background-color: #f9fafb;">
                        <div class="stakeholder-detail-content" style="padding: 20px;">
                            <div class="loading-history" style="text-align: center; padding: 20px; color: #6b7280;">
                                <i class="fas fa-spinner fa-spin"></i> Loading history...
                            </div>
                            <div class="history-table-container" style="display: none;"></div>
                        </div>
                    </td>
                </tr>
            `;
        }).join('');

        const objectTypeLabel = window.I18n ? window.I18n.t('people.objectType') : 'Object Type';
        const objectNameLabel = window.I18n ? window.I18n.t('people.objectName') : 'Object Name';
        const eventsLabel = window.I18n ? window.I18n.t('people.events') : 'Events';
        const lastUpdatedByLabel = window.I18n ? window.I18n.t('people.lastUpdatedBy') : 'Last Updated By';
        const lastUpdatedLabel = window.I18n ? window.I18n.t('people.lastUpdated') : 'Last Updated';
        
        const recordCount = records.length;
        const recordLabel = recordCount === 1 
            ? (window.I18n ? window.I18n.t('people.record') : 'record')
            : (window.I18n ? window.I18n.t('people.records') : 'records');

        return `
            <style>
                .stakeholder-parent-row {
                    cursor: pointer;
                    transition: background-color 0.2s ease;
                }
                .stakeholder-parent-row:hover {
                    background-color: #f3f4f6 !important;
                }
                .stakeholder-parent-row.expanded {
                    background-color: #e7f5ed !important;
                }
                .stakeholder-detail-row {
                    background-color: #f9fafb;
                }
                .expand-icon {
                    transition: transform 0.3s ease;
                }
                .expand-icon.expanded {
                    transform: rotate(45deg);
                }
                .stakeholder-detail-content {
                    animation: slideDown 0.3s ease-out;
                }
                @keyframes slideDown {
                    from {
                        opacity: 0;
                        max-height: 0;
                    }
                    to {
                        opacity: 1;
                        max-height: 2000px;
                    }
                }
                .history-detail-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 10px;
                }
                .history-detail-table thead {
                    background-color: #f3f4f6;
                }
                .history-detail-table th {
                    padding: 10px;
                    text-align: left;
                    font-weight: 600;
                    color: #374151;
                    border-bottom: 2px solid #e5e7eb;
                    font-size: 13px;
                }
                .history-detail-table td {
                    padding: 10px;
                    border-bottom: 1px solid #e5e7eb;
                    font-size: 13px;
                    color: #111827;
                }
                .history-detail-table tbody tr:hover {
                    background-color: #f9fafb;
                }
            </style>
            <div class="table-responsive">
                <table class="people-table stakeholder-table">
                    <thead>
                        <tr>
                            <th style="width: 40px;"></th>
                            <th>${objectTypeLabel}</th>
                            <th>${objectNameLabel}</th>
                            <th>${eventsLabel}</th>
                            <th>${lastUpdatedByLabel}</th>
                            <th>${lastUpdatedLabel}</th>
                        </tr>
                    </thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
            <div class="table-meta">${recordCount} ${recordLabel}</div>
        `;
    }

    function getObjectViewUrl(objectType, objectId, reference) {
        if (!objectId) return null;
        
        // Attributes don't have their own view page - return a marker URL
        // The click is handled by a delegated event listener (see setupAttributeLinkHandler)
        if (objectType === 'Attribute') {
            return `#attribute-lookup-${objectId}`;
        }

        const typeMap = {
            'Glossary': '/view/glossary',
            'Dataset': '/view/dataset',
            'Data Set': '/view/dataset',  // Handle "Data Set" with space
            'System': '/view/system',
            'Interface': '/view/interface',
            'System Interface': '/view/system-interface',  // Handle "System Interface"
            'Process': '/view/process',
            'Project': '/view/project',
            'Product': '/view/product',
            'Policy': '/view/policy',
            'Business Area': '/view/business-area',
            'Capability': '/view/capability',
            'Client': '/view/client',
            'Committee': '/view/committee',
            'Legal Entity': '/view/legal',
            'Regulation': '/view/regulation'
        };
        
        const baseUrl = typeMap[objectType];
        if (!baseUrl) return null;
        
        return `${baseUrl}/${objectId}`;
    }

    // Cache for module IDs to avoid repeated API calls
    let moduleIdCache = null;

    async function getModuleIdForObjectType(objectType) {
        // First try cached mapping
        const staticMap = {
            'Glossary': 'Glossary',
            'Dataset': 'Dataset',
            'System': 'System',
            'Interface': 'Interface',
            'Process': 'Process',
            'Project': 'Project',
            'Product': 'Product',
            'Policy': 'Policy',
            'Business Area': 'Business Area',
            'Capability': 'Capability',
            'Client': 'Client',
            'Committee': 'Committee',
            'Legal Entity': 'Legal Entity',
            'Attribute': 'Attribute',
            'Regulation': 'Regulation'
        };
        
        const moduleName = staticMap[objectType];
        if (!moduleName) return null;
        
        // Load module IDs from API if not cached
        if (!moduleIdCache) {
            try {
                const response = await fetch('/api/modules', { credentials: 'include' });
                if (response.ok) {
                    const data = await response.json();
                    const modules = data.modules || data.data || [];
                    moduleIdCache = {};
                    modules.forEach(module => {
                        const name = module.primaryname || module.name || module.primaryName || module.Name;
                        if (name) {
                            moduleIdCache[name] = module.id || module.ID;
                        }
                    });
                }
            } catch (error) {
                console.error('Error fetching modules:', error);
                // Fallback to static mapping if API fails
                return getStaticModuleId(objectType);
            }
        }
        
        return moduleIdCache[moduleName] || getStaticModuleId(objectType);
    }

    function getStaticModuleId(objectType) {
        // Fallback static mapping (may need adjustment based on actual DB)
        const staticIds = {
            'Glossary': 1,
            'Dataset': 2,
            'System': 3,
            'Interface': 4,
            'Process': 5,
            'Project': 6,
            'Product': 7,
            'Policy': 8,
            'Business Area': 9,
            'Capability': 10,
            'Client': 11,
            'Committee': 12,
            'Legal Entity': 13,
            'Attribute': 14,
            'Regulation': 15
        };
        return staticIds[objectType] || null;
    }

    async function loadObjectHistory(objectType, objectId, container, stakeholderSince = null) {
        const moduleId = await getModuleIdForObjectType(objectType);
        if (!moduleId || !objectId) {
            container.innerHTML = '<div style="padding: 20px; text-align: center; color: #6b7280;">Unable to load history: Invalid object type or ID</div>';
            return;
        }

        try {
            const pageSize = 100;
            let page = 1;
            let historyRecords = [];
            let keepLoading = true;

            while (keepLoading) {
                const response = await fetch(`/api/history?module_id=${moduleId}&object_id=${objectId}&page=${page}&limit=${pageSize}`, {
                    credentials: 'include'
                });

                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }

                const data = await response.json();
                const pageRecords = Array.isArray(data.data) ? data.data : [];
                historyRecords = historyRecords.concat(pageRecords);
                keepLoading = pageRecords.length === pageSize;
                page += 1;
            }

            // Show the full history exactly like the HISTORY tab on the object
            // view page. The previous "from !== to" filter accidentally hid
            // legitimate rows whose source DB columns are NULL/empty
            // (e.g. Deleted rows or freshly created Impact links) and made the
            // expanded table look like Impact additions were missing.

            // Sort newest-first: the most recent change is on top and the oldest
            // (including the "Created By" creation row) sinks to the bottom.
            // Many creation rows share the exact same second, so auditidpk DESC is
            // used as a stable tiebreaker.
            historyRecords.sort((a, b) => {
                const dateA = a.date ? new Date(a.date).getTime() : 0;
                const dateB = b.date ? new Date(b.date).getTime() : 0;
                if (dateA !== dateB) return dateB - dateA;
                const pkA = Number(a.auditidpk || a.auditIdPk || 0);
                const pkB = Number(b.auditidpk || b.auditIdPk || 0);
                return pkB - pkA;
            });

            if (historyRecords.length === 0) {
                container.innerHTML = '<div style="padding: 20px; text-align: center; color: #6b7280;">No history records found</div>';
                return;
            }

            const historyRows = historyRecords.map(record => `
                <tr>
                    <td>${escapeHtml(record.object || '-')}</td>
                    <td>${escapeHtml(record.updateType || '-')}</td>
                    <td>${escapeHtml(record.field || '-')}</td>
                    <td>${escapeHtml(record.from || '-')}</td>
                    <td>${escapeHtml(record.to || '-')}</td>
                    <td>${escapeHtml(record.author || '-')}</td>
                    <td>${escapeHtml(record.date || '-')}</td>
                </tr>
            `).join('');

            container.innerHTML = `
                <table class="history-detail-table">
                    <thead>
                        <tr>
                            <th>Object</th>
                            <th>Update Type</th>
                            <th>Field</th>
                            <th>From</th>
                            <th>To</th>
                            <th>Author</th>
                            <th>Date</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${historyRows}
                    </tbody>
                </table>
                <div style="margin-top: 10px; text-align: right; color: #6b7280; font-size: 13px;">
                    ${historyRecords.length} record${historyRecords.length !== 1 ? 's' : ''}
                </div>
            `;
        } catch (error) {
            console.error('Error loading object history:', error);
            container.innerHTML = `<div style="padding: 20px; text-align: center; color: #dc2626;">Error loading history: ${escapeHtml(error.message)}</div>`;
        }
    }
    function buildEventSummary(events = {}) {
        const labels = {
            relationshipsAdded: window.I18n ? window.I18n.t('people.event.relationshipsAdded') : 'Relationships Added',
            relationshipsUpdated: window.I18n ? window.I18n.t('people.event.relationshipsUpdated') : 'Relationships Updated',
            relationshipsDeleted: window.I18n ? window.I18n.t('people.event.relationshipsDeleted') : 'Relationships Deleted',
            detailsAdded: window.I18n ? window.I18n.t('people.event.detailsAdded') : 'Details Added',
            detailsUpdated: window.I18n ? window.I18n.t('people.event.detailsUpdated') : 'Details Updated',
            detailsDeleted: window.I18n ? window.I18n.t('people.event.detailsDeleted') : 'Details Deleted'
        };

        const parts = Object.keys(labels).map(key => {
            const count = Number(events[key] ?? 0);
            return count > 0 ? `<div>${labels[key]} (${count})</div>` : '';
        }).filter(Boolean);

        return parts.length ? parts.join('') : '-';
    }

    // Store selected change request for start/complete actions
    let selectedChangeRequestId = null;
    let selectedChangeRequest = null;
    let selectedContributingChangeRequestId = null;
    let selectedContributingChangeRequest = null;
    let selectedWfTaskId = null;

    async function loadChangeRequestData(id) {
        const container = document.getElementById('peopleChangeContainer');
        if (!container) return;

        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading change requests...</div>';

        function formatStatusBadge(statusName) {
            if (!statusName || statusName.trim() === '') return '<span class="empty">-</span>';
            const statusLower = statusName.toLowerCase();
            let badgeClass = 'status-badge';
            if (statusLower.includes('pending')) {
                badgeClass += ' status-pending';
            } else if (statusLower.includes('running')) {
                badgeClass += ' status-running';
            } else if (statusLower.includes('completed')) {
                badgeClass += ' status-completed';
            } else if (statusLower.includes('cancelled') || statusLower.includes('canceled')) {
                badgeClass += ' status-cancelled';
            }
            return `<span class="${badgeClass}">${escapeHtml(statusName)}</span>`;
        }

        function formatTypeBadge(typeName) {
            if (!typeName || typeName.trim() === '') return '<span class="empty">-</span>';
            return `<span class="view-badge">${escapeHtml(typeName)}</span>`;
        }

        function buildCrTableRows(changeList, rowClass) {
            const list = Array.isArray(changeList) ? changeList : [];
            return list.map(cr => {
                const ref = cr.ref || cr.Ref || cr.id || '';
                const title = cr.primaryName || cr.PrimaryName || cr.title || cr.Title || 'Untitled';
                const description = cr.summary || cr.Summary || cr.description || cr.Description || '';
                const typeName = cr.typeName || cr.TypeName || '';
                const typeDisplay = typeName || '';
                const statusName = cr.statusName || cr.StatusName || '';
                const statusDisplay = statusName || '';
                const crId = cr.id || cr.ID;
                return `
                    <tr data-cr-id="${crId}" class="${rowClass}" style="cursor: pointer;">
                        <td>${escapeHtml(String(ref))}</td>
                        <td>
                            <a href="/view/change-request/change-request-view.html?id=${crId}" class="cr-title-link">
                                <i class="fas fa-comment-dots" style="margin-right: 0.5rem; color: var(--primary-color, #248567);"></i>
                                ${escapeHtml(title)}
                            </a>
                        </td>
                        <td>${escapeHtml(description || '-')}</td>
                        <td>${formatTypeBadge(typeDisplay)}</td>
                        <td>${formatStatusBadge(statusDisplay)}</td>
                    </tr>
                `;
            }).join('');
        }

        function emptyCrTableMessage(message) {
            return `<tr><td colspan="5" class="empty-state" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${escapeHtml(message)}</td></tr>`;
        }

        try {
            const [raisedRes, contributingRes, tasksRes] = await Promise.all([
                window.BUDG_API_SERVICE.getPersonChangeRequests(id),
                window.BUDG_API_SERVICE.getPersonContributingChangeRequests(id).catch(() => []),
                window.BUDG_API_SERVICE.getPersonActiveWorkflowTasks(id).catch(() => [])
            ]);

            const changeData = Array.isArray(raisedRes) ? raisedRes : [];
            const contributingData = Array.isArray(contributingRes) ? contributingRes : [];
            const tasksData = Array.isArray(tasksRes) ? tasksRes : [];

            const raisedRows = changeData.length ? buildCrTableRows(changeData, 'cr-raised-row') : emptyCrTableMessage('No change requests raised by you');
            const contributingRows = contributingData.length
                ? buildCrTableRows(contributingData, 'cr-contributing-row')
                : emptyCrTableMessage('No contributing change requests');

            const taskRows = tasksData.length
                ? tasksData.map((task, idx) => {
                    const tid = Number.parseInt(String(task.taskId != null ? task.taskId : task.id), 10);
                    const crId = task.changeRequestId;
                    const name = task.name || task.taskName || '-';
                    const desc = task.object || task.roleName || '-';
                    const due = task.dueDate || '-';
                    const crTitle = task.title || '';
                    const crLink = crId ? `/view/change-request/change-request-view.html?id=${crId}` : '#';
                    const safeTaskId = Number.isFinite(tid) && tid > 0 ? Math.floor(tid) : '';
                    return `
                        <tr data-task-id="${safeTaskId}" class="wf-task-row" style="cursor: pointer;">
                            <td>${idx + 1}</td>
                            <td>${escapeHtml(name)}</td>
                            <td>${escapeHtml(desc || '-')}</td>
                            <td>${escapeHtml(String(due))}</td>
                            <td>
                                ${crId ? `<a href="${crLink}" class="cr-title-link">
                                    <i class="fas fa-comment-dots" style="margin-right: 0.5rem; color: var(--primary-color, #248567);"></i>
                                    ${escapeHtml(crTitle || 'Change request')}
                                </a>` : escapeHtml(crTitle || '-')}
                            </td>
                        </tr>
                    `;
                }).join('')
                : '<tr><td colspan="5" class="empty-state" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No active workflow tasks</td></tr>';

            container.innerHTML = `
                <div class="people-cr-tab-sections" style="display: flex; flex-direction: column; gap: 1.5rem; grid-column: 1 / -1;">
                    <div class="view-section" style="grid-column: 1/-1;">
                        <div class="section-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                            <div class="section-title">RAISED BY ME</div>
                            <div class="section-actions" style="display: flex; gap: 0.5rem; align-items: center;">
                                <button type="button" id="startWorkflowBtn" class="btn btn-sm btn-primary" disabled style="opacity: 0.5; cursor: not-allowed;">
                                    <i class="fas fa-play"></i> Start
                                </button>
                                <button type="button" id="completeCRBtn" class="btn btn-sm btn-secondary" disabled style="opacity: 0.5; cursor: not-allowed;">
                                    <i class="fas fa-flag-checkered"></i> Complete
                                </button>
                                <button type="button" class="btn-icon" title="Settings"><i class="fas fa-cog"></i></button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Ref</th>
                                        <th>Title</th>
                                        <th>Description</th>
                                        <th>Type</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody id="changeRequestsRaisedTableBody">${raisedRows}</tbody>
                            </table>
                        </div>
                        <div class="table-footer">${changeData.length} record${changeData.length !== 1 ? 's' : ''}</div>
                    </div>

                    <div class="view-section" style="grid-column: 1/-1;">
                        <div class="section-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                            <div class="section-title">CONTRIBUTING</div>
                            <div class="section-actions" style="display: flex; gap: 0.5rem; align-items: center;">
                                <button type="button" id="completeCRContributingBtn" class="btn btn-sm btn-secondary" disabled style="opacity: 0.5; cursor: not-allowed;">
                                    <i class="fas fa-flag-checkered"></i> Complete
                                </button>
                                <button type="button" class="btn-icon" title="Settings"><i class="fas fa-cog"></i></button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>Ref</th>
                                        <th>Title</th>
                                        <th>Description</th>
                                        <th>Type</th>
                                        <th>Status</th>
                                    </tr>
                                </thead>
                                <tbody id="contributingTableBody">${contributingRows}</tbody>
                            </table>
                        </div>
                        <div class="table-footer">${contributingData.length} record${contributingData.length !== 1 ? 's' : ''}</div>
                    </div>

                    <div class="view-section" style="grid-column: 1/-1;">
                        <div class="section-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                            <div class="section-title">WORKFLOW TASKS</div>
                            <div class="section-actions" style="display: flex; gap: 0.5rem; align-items: center;">
                                <button type="button" id="completeWorkflowTaskBtn" class="btn btn-sm btn-secondary" disabled style="opacity: 0.5; cursor: not-allowed;">
                                    <i class="fas fa-file-signature"></i> Complete Task
                                </button>
                                <button type="button" class="btn-icon" title="Settings"><i class="fas fa-cog"></i></button>
                            </div>
                        </div>
                        <div class="data-table-wrapper">
                            <table class="data-table">
                                <thead>
                                    <tr>
                                        <th>No.</th>
                                        <th>Active Task Name</th>
                                        <th>Description</th>
                                        <th>Due Date</th>
                                        <th>Change Request Name</th>
                                    </tr>
                                </thead>
                                <tbody id="workflowTasksTableBody">${taskRows}</tbody>
                            </table>
                        </div>
                        <div class="table-footer">${tasksData.length} record${tasksData.length !== 1 ? 's' : ''}</div>
                    </div>
                </div>
            `;

            window._peopleChangeRequests = changeData;
            window._peopleContributingChangeRequests = contributingData;
            window._peopleWorkflowTasks = tasksData;

            selectedChangeRequestId = null;
            selectedChangeRequest = null;
            selectedContributingChangeRequestId = null;
            selectedContributingChangeRequest = null;
            selectedWfTaskId = null;

            setupCrTableSectionHandlers('changeRequestsRaisedTableBody', 'cr-raised-row', changeData, (cr) => {
                selectedChangeRequest = cr;
                selectedChangeRequestId = cr ? (cr.id || cr.ID) : null;
                clearCrTabRowSelection('contributingTableBody', 'cr-contributing-row');
                clearCrTabRowSelection('workflowTasksTableBody', 'wf-task-row');
                selectedContributingChangeRequest = null;
                selectedContributingChangeRequestId = null;
                selectedWfTaskId = null;
                updateButtonStates(selectedChangeRequest);
                updateContributingCompleteButtonState(null);
                setWorkflowTaskCompleteEnabled(false);
            });

            setupCrTableSectionHandlers('contributingTableBody', 'cr-contributing-row', contributingData, (cr) => {
                selectedContributingChangeRequest = cr;
                selectedContributingChangeRequestId = cr ? (cr.id || cr.ID) : null;
                clearCrTabRowSelection('changeRequestsRaisedTableBody', 'cr-raised-row');
                clearCrTabRowSelection('workflowTasksTableBody', 'wf-task-row');
                selectedChangeRequest = null;
                selectedChangeRequestId = null;
                selectedWfTaskId = null;
                updateButtonStates(null);
                updateContributingCompleteButtonState(selectedContributingChangeRequest);
                setWorkflowTaskCompleteEnabled(false);
            });

            setupWorkflowTasksTableHandlers(tasksData);

            wirePeopleChangeRequestTabToolbar(id);
            updateButtonStates(null);
            updateContributingCompleteButtonState(null);
            setWorkflowTaskCompleteEnabled(false);
        } catch (e) {
            console.error('Failed to load change request data:', e);
            container.innerHTML = `
                <div class="view-section" style="grid-column: 1/-1;">
                    <div class="section-title">CHANGE REQUEST</div>
                    <div class="empty-state" style="text-align: center; padding: 3rem;">
                        <i class="fas fa-exclamation-triangle" style="font-size: 3rem; color: var(--danger, #b91c1c); margin-bottom: 1rem;"></i>
                        <p style="color: var(--danger, #b91c1c);">Failed to load change request data: ${escapeHtml(e.message || 'Unknown error')}</p>
                    </div>
                </div>
            `;
        }
    }

    function clearCrTabRowSelection(tbodyId, rowClass) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody) return;
        tbody.querySelectorAll('tr.' + rowClass).forEach(r => r.classList.remove('selected'));
    }

    function setupCrTableSectionHandlers(tbodyId, rowClass, changeRequests, onSelect) {
        const tbody = document.getElementById(tbodyId);
        if (!tbody) return;

        tbody.addEventListener('click', (e) => {
            const row = e.target.closest('tr.' + rowClass);
            if (!row) return;
            if (e.target.closest('a.cr-title-link')) {
                return;
            }

            const crId = parseInt(row.getAttribute('data-cr-id'), 10);
            if (!crId) return;

            tbody.querySelectorAll('tr.' + rowClass).forEach(r => r.classList.remove('selected'));
            row.classList.add('selected');
            const cr = (changeRequests || []).find(c => (c.id || c.ID) === crId);
            onSelect(cr || null);
        });
    }

    function setupWorkflowTasksTableHandlers(tasks) {
        const tbody = document.getElementById('workflowTasksTableBody');
        if (!tbody) return;

        tbody.addEventListener('click', (e) => {
            const row = e.target.closest('tr.wf-task-row');
            if (!row) return;
            if (e.target.closest('a.cr-title-link')) {
                return;
            }

            const taskId = parseInt(row.getAttribute('data-task-id'), 10);
            if (!taskId) return;

            tbody.querySelectorAll('tr.wf-task-row').forEach(r => r.classList.remove('selected'));
            row.classList.add('selected');
            selectedWfTaskId = taskId;
            clearCrTabRowSelection('changeRequestsRaisedTableBody', 'cr-raised-row');
            clearCrTabRowSelection('contributingTableBody', 'cr-contributing-row');
            selectedChangeRequest = null;
            selectedChangeRequestId = null;
            selectedContributingChangeRequest = null;
            selectedContributingChangeRequestId = null;
            updateButtonStates(null);
            updateContributingCompleteButtonState(null);
            setWorkflowTaskCompleteEnabled(true);
        });
    }

    function setWorkflowTaskCompleteEnabled(enabled) {
        const btn = document.getElementById('completeWorkflowTaskBtn');
        if (!btn) return;
        btn.disabled = !enabled;
        btn.style.opacity = enabled ? '1' : '0.5';
        btn.style.cursor = enabled ? 'pointer' : 'not-allowed';
    }

    function wirePeopleChangeRequestTabToolbar(personId) {
        const startBtn = document.getElementById('startWorkflowBtn');
        if (startBtn) {
            startBtn.onclick = () => {
                if (!selectedChangeRequest) return;
                handleStartWorkflowFromTable(selectedChangeRequest);
            };
        }

        const completeRaised = document.getElementById('completeCRBtn');
        if (completeRaised) {
            completeRaised.onclick = () => {
                if (!selectedChangeRequestId) {
                    alert('Select a change request in Raised by me');
                    return;
                }
                handleCompleteChangeRequestFromTable(selectedChangeRequestId);
            };
        }

        const completeContributing = document.getElementById('completeCRContributingBtn');
        if (completeContributing) {
            completeContributing.onclick = () => {
                if (!selectedContributingChangeRequestId) {
                    alert('Select a change request in Contributing');
                    return;
                }
                handleCompleteChangeRequestFromTable(selectedContributingChangeRequestId);
            };
        }

        const completeTaskBtn = document.getElementById('completeWorkflowTaskBtn');
        if (completeTaskBtn) {
            completeTaskBtn.onclick = () => handleCompleteWorkflowTaskFromPeopleTab(personId);
        }
    }

    async function handleCompleteWorkflowTaskFromPeopleTab(personId) {
        if (!selectedWfTaskId) {
            alert('Select a workflow task');
            return;
        }
        if (!confirm('Complete this workflow task?')) {
            return;
        }
        const btn = document.getElementById('completeWorkflowTaskBtn');
        try {
            if (btn) {
                btn.disabled = true;
                btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Completing...';
            }
            const response = await fetch(`/api/workflow_tasks/${selectedWfTaskId}/complete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ decision: 'complete', comment: '' })
            });
            if (!response.ok) {
                const err = await response.json().catch(() => ({}));
                throw new Error(err.error || 'Failed to complete task');
            }
            await loadChangeRequestData(personId);
        } catch (err) {
            console.error(err);
            alert(err.message || 'Failed to complete task');
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-file-signature"></i> Complete Task';
            }
        }
    }

    function updateContributingCompleteButtonState(changeRequest) {
        const completeBtn = document.getElementById('completeCRContributingBtn');
        if (!completeBtn) return;

        if (!changeRequest) {
            completeBtn.disabled = true;
            completeBtn.style.opacity = '0.5';
            completeBtn.style.cursor = 'not-allowed';
            completeBtn.title = 'Select a running change request to complete';
            completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
            return;
        }

        const statusName = (changeRequest.statusName || changeRequest.StatusName || '').toLowerCase();
        const hasWorkflow = !!(changeRequest.workflowInstanceId || changeRequest.processInstanceId);
        const isRunning = statusName.includes('running');

        if (isRunning && hasWorkflow) {
            completeBtn.disabled = true;
            completeBtn.style.opacity = '0.5';
            completeBtn.style.cursor = 'not-allowed';
            completeBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Checking...';
            checkWorkflowCompletion(changeRequest.id || changeRequest.ID).then(workflowCompleted => {
                completeBtn.disabled = !workflowCompleted;
                if (workflowCompleted) {
                    completeBtn.style.opacity = '1';
                    completeBtn.style.cursor = 'pointer';
                    completeBtn.title = 'Complete selected change request';
                } else {
                    completeBtn.style.opacity = '0.5';
                    completeBtn.style.cursor = 'not-allowed';
                    completeBtn.title = 'Workflow must be completed before completing change request';
                }
                completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
            }).catch(() => {
                completeBtn.disabled = true;
                completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
            });
        } else {
            completeBtn.disabled = true;
            completeBtn.style.opacity = '0.5';
            completeBtn.style.cursor = 'not-allowed';
            completeBtn.title = 'Select a running change request to complete';
            completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
        }
    }

    // Update button states based on selected change request
    function updateButtonStates(changeRequest) {
        const startBtn = document.getElementById('startWorkflowBtn');
        const completeBtn = document.getElementById('completeCRBtn');

        if (!changeRequest) {
            if (startBtn) {
                startBtn.disabled = true;
                startBtn.style.opacity = '0.5';
                startBtn.style.cursor = 'not-allowed';
                startBtn.innerHTML = '<i class="fas fa-play"></i> Start';
            }
            if (completeBtn) {
                completeBtn.disabled = true;
                completeBtn.style.opacity = '0.5';
                completeBtn.style.cursor = 'not-allowed';
                completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
            }
            return;
        }

        const statusName = (changeRequest.statusName || changeRequest.StatusName || '').toLowerCase();
        const hasWorkflow = !!(changeRequest.workflowInstanceId || changeRequest.processInstanceId);
        const isPendingStart = statusName.includes('pending start');
        const isRunning = statusName.includes('running');
        const isCompleted = statusName.includes('completed');
        const isCancelled = statusName.includes('cancelled') || statusName.includes('canceled');

        // Start button: enabled ONLY if row is selected AND "Pending Start" status and no workflow started
        // (changeRequest parameter is only passed when a row is selected)
        if (startBtn) {
            const shouldEnable = isPendingStart && !hasWorkflow;
            startBtn.disabled = !shouldEnable;
            // Update button styling - gray when disabled, green when enabled
            if (shouldEnable) {
                startBtn.style.opacity = '1';
                startBtn.style.cursor = 'pointer';
                startBtn.title = 'Start workflow for selected change request';
            } else {
                startBtn.style.opacity = '0.5';
                startBtn.style.cursor = 'not-allowed';
                startBtn.title = 'Select a change request with "Pending Start" status to start workflow';
            }
        }

        // Complete button: enabled ONLY if row is selected AND "Running" status with completed workflow
        // (changeRequest parameter is only passed when a row is selected)
        if (completeBtn) {
            if (isRunning && hasWorkflow) {
                // Initially disable, then check workflow completion
                completeBtn.disabled = true;
                completeBtn.style.opacity = '0.5';
                completeBtn.style.cursor = 'not-allowed';
                completeBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Checking...';
                // Check if workflow is completed
                checkWorkflowCompletion(changeRequest.id || changeRequest.ID).then(workflowCompleted => {
                    completeBtn.disabled = !workflowCompleted;
                    if (workflowCompleted) {
                        completeBtn.style.opacity = '1';
                        completeBtn.style.cursor = 'pointer';
                        completeBtn.title = 'Complete selected change request';
                    } else {
                        completeBtn.style.opacity = '0.5';
                        completeBtn.style.cursor = 'not-allowed';
                        completeBtn.title = 'Workflow must be completed before completing change request';
                    }
                    completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
                }).catch(error => {
                    console.error('Error checking workflow completion:', error);
                    completeBtn.disabled = true;
                    completeBtn.style.opacity = '0.5';
                    completeBtn.style.cursor = 'not-allowed';
                    completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
                });
            } else {
                completeBtn.disabled = true;
                completeBtn.style.opacity = '0.5';
                completeBtn.style.cursor = 'not-allowed';
                if (isRunning && !hasWorkflow) {
                    completeBtn.title = 'Workflow must be started before completing';
                } else {
                    completeBtn.title = 'Select a running change request to complete';
                }
            }
        }
    }

    // Check if workflow is completed for a change request
    async function checkWorkflowCompletion(crId) {
        try {
            const response = await fetch(`/api/workflow_instances/by-cr/${crId}`, {
                method: 'GET',
                credentials: 'include'
            });
            if (response.ok) {
                const data = await response.json();
                const instance = data.instance;
                if (instance && (instance.status === 'Completed' || instance.status === 'Disabled' || instance.endedAt)) {
                    return true;
                }
            }
            return false;
        } catch (error) {
            console.error('Error checking workflow completion:', error);
            return false;
        }
    }

    // Handle start workflow from table
    async function handleStartWorkflowFromTable(changeRequest) {
        try {
            // Check if workflow already started
            if (changeRequest.workflowInstanceId || changeRequest.processInstanceId) {
                alert('Workflow is already started for this change request');
                return;
            }

            let workflowId = null;
            let workflows = [];
            let usePreSelectedWorkflow = false;

            // Check if CR has a pre-selected workflow (processDefinitionId)
            if (changeRequest.processDefinitionId) {
                const processDefId = typeof changeRequest.processDefinitionId === 'string' 
                    ? parseInt(changeRequest.processDefinitionId) 
                    : changeRequest.processDefinitionId;
                
                // Load the specific workflow to verify it exists
                try {
                    const workflowResponse = await fetch(`/api/process_definitions/${processDefId}`, {
                        credentials: 'include'
                    });
                    if (workflowResponse.ok) {
                        const storedWorkflow = await workflowResponse.json();
                        console.log('Found pre-selected workflow:', storedWorkflow);
                        // Use the pre-selected workflow directly - skip modal
                        workflowId = processDefId;
                        usePreSelectedWorkflow = true;
                        workflows = [storedWorkflow]; // Add to workflows array for validation
                    }
                } catch (error) {
                    console.warn('Could not load pre-selected workflow, will show selection modal:', error);
                }
            }

            // If no pre-selected workflow or we want to show modal anyway, load available workflows
            if (!usePreSelectedWorkflow) {
                // Extract facet information from reference
                const facetInfo = parseFacetFromReference(changeRequest.reference || changeRequest.Reference);
                if (!facetInfo) {
                    alert('Invalid change request reference format');
                    return;
                }

                // Load modules to resolve facet name to Module ID
                const modulesResponse = await fetch('/api/modules', { credentials: 'include' });
                if (!modulesResponse.ok) {
                    throw new Error('Failed to load modules');
                }
                const modulesData = await modulesResponse.json();
                // API returns {groups: [...], modules: [...]} or {success: true, data: [...]}
                const modules = modulesData.modules || modulesData.data || [];
                if (!Array.isArray(modules)) {
                    throw new Error('Invalid modules response format');
                }
                const module = modules.find(m => {
                    const moduleName = (m.name || m.Name || m.primaryName || m.PrimaryName || '').toLowerCase();
                    return moduleName === facetInfo.facetName.toLowerCase();
                });
                const moduleId = module ? (module.id || module.ID) : facetInfo.facetId;

                // Load available workflows
                // Note: API uses entityId (which is the module ID) and endpoint is process_definitions (with underscore)
                const workflowsResponse = await fetch(`/api/process_definitions?entityId=${moduleId}`, {
                    credentials: 'include'
                });
                if (!workflowsResponse.ok) {
                    throw new Error('Failed to load workflows');
                }
                workflows = await workflowsResponse.json();
                // Ensure workflows is an array
                if (!Array.isArray(workflows)) {
                    throw new Error('Invalid workflows response format');
                }

                if (!workflows || workflows.length === 0) {
                    alert('No workflows available for this change request');
                    return;
                }

                // If CR has processDefinitionId, try to find and pre-select it
                if (changeRequest.processDefinitionId) {
                    const processDefId = typeof changeRequest.processDefinitionId === 'string' 
                        ? parseInt(changeRequest.processDefinitionId) 
                        : changeRequest.processDefinitionId;
                    
                    // Try to load the specific workflow if not in the list
                    const existingWorkflow = workflows.find(w => {
                        const wId = typeof w.id === 'string' ? parseInt(w.id) : w.id;
                        return wId === processDefId;
                    });
                    
                    if (!existingWorkflow) {
                        // Load the workflow separately (it might have different entityId)
                        try {
                            const workflowResponse = await fetch(`/api/process_definitions/${processDefId}`, {
                                credentials: 'include'
                            });
                            if (workflowResponse.ok) {
                                const storedWorkflow = await workflowResponse.json();
                                workflows.unshift(storedWorkflow); // Add at beginning
                            }
                        } catch (error) {
                            console.warn('Could not load pre-selected workflow:', error);
                        }
                    }
                }

                // Show workflow selection modal with pre-selection
                const selectedWorkflowId = await showWorkflowSelectionModal(workflows, changeRequest, changeRequest.processDefinitionId);
                if (!selectedWorkflowId) return;
                workflowId = selectedWorkflowId;
            }

            // Validate workflow using the correct endpoint
            const crId = changeRequest.id || changeRequest.ID;
            const validationResponse = await fetch(
                `/api/workflow/validate-start?changeRequestId=${crId}&processDefId=${workflowId}`,
                {
                    method: 'GET',
                    credentials: 'include'
                }
            );

            if (!validationResponse.ok) {
                const errorData = await validationResponse.json().catch(() => ({ error: 'Validation failed' }));
                alert('Workflow validation failed: ' + (errorData.error || 'Unknown error'));
                return;
            }

            const validation = await validationResponse.json();
            if (!validation.valid) {
                const errorMsg = validation.missingRoles && validation.missingRoles.length > 0
                    ? `Missing required roles: ${validation.missingRoles.join(', ')}`
                    : 'Workflow validation failed';
                alert(errorMsg);
                return;
            }

            // Confirm start
            if (!confirm('Are you sure you want to start this workflow?')) {
                return;
            }

            // Start workflow
            let startBtn = document.getElementById('startWorkflowBtn');
            if (startBtn) {
                startBtn.disabled = true;
                startBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Starting...';
            }

            const startResponse = await fetch('/api/workflow_instances', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    processDefinitionId: workflowId,
                    changeRequestId: changeRequest.id || changeRequest.ID
                })
            });

            if (!startResponse.ok) {
                const errorData = await startResponse.json().catch(() => ({ error: 'Failed to start workflow' }));
                throw new Error(errorData.error || 'Failed to start workflow');
            }

            const startResult = await startResponse.json();
            console.log('Workflow started successfully:', startResult);
            
            alert('Workflow started successfully!');
            
            // Wait a bit for database transaction to commit and status to update
            await new Promise(resolve => setTimeout(resolve, 800));
            
            // Reset selection
            selectedChangeRequestId = null;
            selectedChangeRequest = null;
            
            // Reload change requests to update status from "Pending Start" to "Running"
            const personId = parseId();
            if (personId) {
                await loadChangeRequestData(personId);
            }
            
            // Reset button states after reload (no row selected)
            // Note: startBtn already declared earlier in this function
            startBtn = document.getElementById('startWorkflowBtn');
            const completeBtn = document.getElementById('completeCRBtn');
            if (startBtn) {
                startBtn.disabled = true;
                startBtn.style.opacity = '0.5';
                startBtn.style.cursor = 'not-allowed';
                startBtn.innerHTML = '<i class="fas fa-play"></i> Start';
            }
            if (completeBtn) {
                completeBtn.disabled = true;
                completeBtn.style.opacity = '0.5';
                completeBtn.style.cursor = 'not-allowed';
                completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
            }
        } catch (error) {
            console.error('Error starting workflow:', error);
            alert('Failed to start workflow: ' + error.message);
            const startBtn = document.getElementById('startWorkflowBtn');
            if (startBtn) {
                startBtn.disabled = false;
                startBtn.innerHTML = '<i class="fas fa-play"></i> Start';
            }
        }
    }

    // Show workflow selection modal
    function showWorkflowSelectionModal(workflows, changeRequest, preSelectedWorkflowId = null) {
        return new Promise((resolve) => {
            // Create modal
            const modal = document.createElement('div');
            modal.className = 'modal-overlay';
            modal.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                bottom: 0;
                background: rgba(0, 0, 0, 0.5);
                display: flex;
                align-items: center;
                justify-content: center;
                z-index: 10000;
            `;

            const modalContent = document.createElement('div');
            modalContent.className = 'modal-content';
            modalContent.style.cssText = `
                background: white;
                padding: 2rem;
                border-radius: 8px;
                max-width: 500px;
                width: 90%;
                max-height: 80vh;
                overflow-y: auto;
            `;

            // Determine pre-selected value
            let preSelectedValue = '';
            if (preSelectedWorkflowId) {
                const processDefId = typeof preSelectedWorkflowId === 'string' 
                    ? parseInt(preSelectedWorkflowId) 
                    : preSelectedWorkflowId;
                // Find the workflow in the list
                const preSelectedWorkflow = workflows.find(w => {
                    const wId = typeof w.id === 'string' ? parseInt(w.id) : w.id;
                    return wId === processDefId;
                });
                if (preSelectedWorkflow) {
                    preSelectedValue = String(preSelectedWorkflow.id || preSelectedWorkflow.ID);
                }
            }

            modalContent.innerHTML = `
                <div style="margin-bottom: 1.5rem;">
                    <h3 style="margin: 0 0 0.5rem 0;">Select Workflow</h3>
                    <p style="color: var(--text-muted, #6b7280); margin: 0;">Choose a workflow to start for this change request</p>
                </div>
                <select id="workflowSelectModal" class="form-control" style="width: 100%; padding: 0.5rem; margin-bottom: 1rem;">
                    <option value="">-- Select Workflow --</option>
                    ${workflows.map(wf => {
                        const wfId = String(wf.id || wf.ID);
                        const isSelected = preSelectedValue && wfId === preSelectedValue ? 'selected' : '';
                        return `<option value="${wfId}" ${isSelected}>${escapeHtml(wf.name || wf.primaryName || wf.Name || 'Workflow')}</option>`;
                    }).join('')}
                </select>
                <div style="display: flex; gap: 0.5rem; justify-content: flex-end;">
                    <button id="cancelWorkflowModal" class="btn btn-secondary">Cancel</button>
                    <button id="confirmWorkflowModal" class="btn btn-primary" ${preSelectedValue ? '' : 'disabled'}>Start</button>
                </div>
            `;

            const workflowSelect = modalContent.querySelector('#workflowSelectModal');
            const confirmBtn = modalContent.querySelector('#confirmWorkflowModal');
            const cancelBtn = modalContent.querySelector('#cancelWorkflowModal');

            // If workflow is pre-selected, enable the Start button
            if (preSelectedValue) {
                confirmBtn.disabled = false;
            }

            workflowSelect.addEventListener('change', () => {
                confirmBtn.disabled = !workflowSelect.value;
            });

            confirmBtn.addEventListener('click', () => {
                const selectedId = parseInt(workflowSelect.value);
                if (selectedId) {
                    modal.remove();
                    resolve(selectedId);
                }
            });

            cancelBtn.addEventListener('click', () => {
                modal.remove();
                resolve(null);
            });

            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    modal.remove();
                    resolve(null);
                }
            });

            modal.appendChild(modalContent);
            document.body.appendChild(modal);
        });
    }

    // Parse facet from reference (e.g., "System 47" -> { facetName: "System", facetId: 47 })
    function parseFacetFromReference(reference) {
        if (!reference) return null;
        const match = reference.match(/^(\w+)\s+(\d+)$/);
        if (match) {
            return {
                facetName: match[1],
                facetId: parseInt(match[2])
            };
        }
        return null;
    }

    // Handle complete change request from table
    async function handleCompleteChangeRequestFromTable(changeRequestId) {
        if (!changeRequestId) {
            alert('Error: Change request ID not found');
            return;
        }
        
        // Show confirmation dialog
        const confirmed = confirm('Are you sure you want to complete this change request?');
        if (!confirmed) {
            return;
        }
        
        try {
            const completeBtn = document.getElementById('completeCRBtn');
            if (completeBtn) {
                completeBtn.disabled = true;
                completeBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Completing...';
            }

            const response = await fetch(`/api/changerequests/${changeRequestId}/complete`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include'
            });
            
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ error: 'Failed to complete change request' }));
                alert('Error: ' + (errorData.error || 'Failed to complete change request'));
                const completeBtn = document.getElementById('completeCRBtn');
                if (completeBtn) {
                    completeBtn.disabled = false;
                    completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
                }
                return;
            }
            
            const result = await response.json();
            if (result.success) {
                alert('Change request completed successfully!');
                
                // Wait a bit for database transaction to commit
                await new Promise(resolve => setTimeout(resolve, 500));
                
                // Reset selection
                selectedChangeRequestId = null;
                selectedChangeRequest = null;
                
                // Reload change requests to update status from "Running" to "Completed"
                const personId = parseId();
                if (personId) {
                    await loadChangeRequestData(personId);
                }
                
                // Reset button states after reload (no row selected)
                const startBtn = document.getElementById('startWorkflowBtn');
                const completeBtn = document.getElementById('completeCRBtn');
                if (startBtn) {
                    startBtn.disabled = true;
                    startBtn.style.opacity = '0.5';
                    startBtn.style.cursor = 'not-allowed';
                    startBtn.innerHTML = '<i class="fas fa-play"></i> Start';
                }
                if (completeBtn) {
                    completeBtn.disabled = true;
                    completeBtn.style.opacity = '0.5';
                    completeBtn.style.cursor = 'not-allowed';
                    completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
                }
            } else {
                alert('Error: ' + (result.error || 'Failed to complete change request'));
                // Restore button state on error
                const completeBtn = document.getElementById('completeCRBtn');
                if (completeBtn) {
                    completeBtn.disabled = false;
                    completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
                }
            }
        } catch (error) {
            console.error('Error completing change request:', error);
            alert('Error completing change request: ' + error.message);
            // Restore button state on error
            const completeBtn = document.getElementById('completeCRBtn');
            if (completeBtn) {
                completeBtn.disabled = false;
                completeBtn.innerHTML = '<i class="fas fa-flag-checkered"></i> Complete';
            }
        }
    }

    async function loadSegmentsData(id) {
        const container = document.getElementById('peopleSegmentsContainer');
        if (!container) return;
        
        // Verify user is viewing their own profile (for any user type: regular user, admin, web user)
        const currentUserId = await getCurrentUserId();
        const currentIdNum = currentUserId ? parseInt(currentUserId) : null;
        const viewingIdNum = id ? parseInt(id) : null;
        const viewingOwnProfile = currentIdNum !== null && viewingIdNum !== null && currentIdNum === viewingIdNum;
        
        const segT = (k) => (window.I18n && window.I18n.t(k)) || k;
        if (!viewingOwnProfile) {
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${segT('tab.segments')}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${segT('people.segmentsOnlyViewOwn')}</span>
                        </div>
                    </div>
                </div>
            `;
            return;
        }
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">' + (segT('people.segmentsLoading')) + '</div>';
        
        try {
            const segmentsData = await window.BUDG_API_SERVICE.getPersonSegments(id);
            
            if (!segmentsData || segmentsData.length === 0) {
                container.innerHTML = `
                    <div class="people-section">
                        <div class="people-header">
                            <div class="people-title">${segT('tab.segments')}</div>
                        </div>
                        <div class="people-content">
                            <div class="people-empty">
                                <i class="fas fa-layer-group"></i>
                                <span>${window.I18n ? window.I18n.t('message.noData') : 'No segments assigned'}</span>
                            </div>
                        </div>
                    </div>
                `;
                return;
            }

            // Ensure segmentsData is an array
            const segments = Array.isArray(segmentsData) ? segmentsData : [];
            
            // Render segments table
            renderSegmentsTable(container, segments);
        } catch (e) {
            console.error('Failed to load segments data:', e);
            let errorMessage = window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load segments data';
            if (e.response && e.response.status === 403) {
                errorMessage = (window.I18n && window.I18n.t('people.segmentsOnlyViewOwn')) || 'You can only view your own segments';
            }
            const segTitle = (window.I18n && window.I18n.t('people.segmentsTitle')) || 'ASSIGNED SEGMENTS';
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${segTitle}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${errorMessage}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    function renderSegmentsTable(container, segments) {
        const segT = (k) => (window.I18n && window.I18n.t(k)) || k;
        const segTitle = segT('people.segmentsTitle');
        if (!segments || segments.length === 0) {
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${segTitle}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-layer-group"></i>
                            <span>${window.I18n ? window.I18n.t('message.noData') : 'No segments assigned'}</span>
                        </div>
                    </div>
                </div>
            `;
            return;
        }

        // Filter segments based on checkbox
        let showOnlyAdminSegments = false;
        const zeroRec = segT('people.zeroRecords');
        const tableHtml = `
            <div class="people-section">
                <div class="people-header">
                    <div class="people-title">${segTitle}</div>
                </div>
                <div class="people-content">
                    <div class="segments-table-controls" style="display: flex; align-items: center; gap: 15px; margin-bottom: 15px;justify-content: flex-end;">
                        <label style="display: flex; align-items: center; gap: 8px; cursor: pointer; user-select: none;">
                            <input type="checkbox" id="showOnlyAdminSegments" style="cursor: pointer;">
                            <span>${segT('people.segmentsShowOnlyAdmin')}</span>
                        </label>
                        <div class="table-actions">
                            <i class="fas fa-cog" style="cursor: pointer; color: #6b7280;"></i>
                        </div>
                    </div>
                    <div class="table-responsive">
                        <table class="people-table segments-table">
                            <thead>
                                <tr>
                                    <th>${segT('people.segmentsName')}</th>
                                    <th>${segT('people.segmentsDescription')}</th>
                                    <th>${segT('people.segmentsSegmentAdmin')}</th>
                                </tr>
                            </thead>
                            <tbody id="segmentsTableBody">
                            </tbody>
                        </table>
                    </div>
                    <div class="table-meta">
                        <span id="segmentsRecordCount">${zeroRec}</span>
                    </div>
                </div>
            </div>
        `;

        container.innerHTML = tableHtml;

        const tbody = container.querySelector('#segmentsTableBody');
        const recordCount = container.querySelector('#segmentsRecordCount');
        const filterCheckbox = container.querySelector('#showOnlyAdminSegments');

        const segRecordLabel = (n) => {
            const I = window.I18n && window.I18n.t.bind(window.I18n);
            if (n === 0 && I) return I('people.zeroRecords') || '0 records';
            if (n === 1 && I) return I('people.recordOne') || '1 record';
            if (I) return (I('people.recordCount') || '{count} records').replace('{count}', n);
            return n + ' record' + (n === 1 ? '' : 's');
        };
        const renderRows = (filteredSegments) => {
            if (!filteredSegments || filteredSegments.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="3" class="empty-row">
                            ${window.I18n ? window.I18n.t('message.noData') : 'No segments found'}
                        </td>
                    </tr>
                `;
                recordCount.textContent = segRecordLabel(0);
                return;
            }

            tbody.innerHTML = filteredSegments.map(segment => {
                const adminId = segment.adminId || segment.admin_id;
                const adminName = segment.adminName || segment.admin_name || 'N/A';
                const adminLink = adminId && adminName !== 'N/A' 
                    ? `<a href="/view/people/${adminId}" class="object-link">${escapeHtml(adminName)}</a>`
                    : `<span>${escapeHtml(adminName)}</span>`;

                return `
                    <tr>
                        <td>${escapeHtml(segment.name || 'N/A')}</td>
                        <td>${escapeHtml(segment.description || 'N/A')}</td>
                        <td>${adminLink}</td>
                    </tr>
                `;
            }).join('');

            recordCount.textContent = segRecordLabel(filteredSegments.length);
        };

        // Initial render
        renderRows(segments);

        // Filter checkbox handler
        filterCheckbox.addEventListener('change', function() {
            showOnlyAdminSegments = this.checked;
            const filtered = showOnlyAdminSegments 
                ? segments.filter(s => s.isAdmin === true || s.is_admin === true)
                : segments;
            renderRows(filtered);
        });
    }

    function createTeamSection(title, data, columns) {
        const count = data.length;
        const sectionHtml = `
            <div class="team-section">
                <div class="team-section-header">
                    <h3>${title}</h3>
                    <div class="team-section-actions">
                        <i class="fas fa-cog"></i>
                        <i class="fas fa-chevron-down"></i>
                    </div>
                </div>
                <div class="team-table" style="width: 100%; overflow-x: auto;">
                    <table class="team-table-content" style="width: 100%; table-layout: fixed;">
                        <thead>
                            <tr>
                                ${columns.map(col => `<th style="padding: 8px; text-align: left; border-bottom: 1px solid #ddd;">${col}</th>`).join('')}
                            </tr>
                        </thead>
                        <tbody>
                            ${data.map(item => createTeamRow(item, columns)).join('')}
                        </tbody>
                    </table>
                    <div class="team-table-footer">
                        <span class="record-count">${count} record${count !== 1 ? 's' : ''}</span>
                    </div>
                </div>
            </div>
        `;
        return sectionHtml;
    }

    function createTeamRow(item, columns) {
        const getCellValue = (item, column) => {
            switch (column) {
                case 'Manager':
                case 'Name':
                    const fullName = `${item.firstName || ''} ${item.lastName || ''}`.trim();
                    const personId = item.id || item.ID;
                    return `<div class="team-member">
                        <i class="fas fa-user"></i>
                        <a href="/view/people/${personId}" class="team-member-link">${escapeHtml(fullName)}</a>
                    </div>`;
                case 'Function':
                    return escapeHtml(item.functionName || '');
                case 'Org Unit':
                    return escapeHtml(item.orgUnitName || '');
                case 'Email':
                    return escapeHtml(item.email || '');
                case 'Telephone':
                    return escapeHtml(item.telephone || '');
                case 'Mobile':
                    return escapeHtml(item.mobile || '');
                case 'Type':
                    return escapeHtml(item.type || '');
                default:
                    return '';
            }
        };

        return `<tr>
            ${columns.map(col => `<td style="padding: 8px; border-bottom: 1px solid #eee; word-wrap: break-word;">${getCellValue(item, col)}</td>`).join('')}
        </tr>`;
    }

    async function loadRolesData(id) {
        const container = document.getElementById('peopleRolesContainer');
        if (!container) return;
        
        container.innerHTML = '<div class="view-section" style="grid-column: 1/-1;">Loading assigned roles...</div>';
        
        try {
            // Get current user ID
            const currentUserId = await getCurrentUserId();
            if (!currentUserId) {
                console.warn('Current user ID not found, showing all roles');
                const response = await window.BUDG_API_SERVICE.getPersonRoles(id);
                const records = Array.isArray(response?.records) ? response.records : [];
                const facets = Array.isArray(response?.facets) ? response.facets : [];
                renderRolesTable(container, records, facets);
                return;
            }

            // Fetch roles for the person being viewed
            const personRolesResponse = await window.BUDG_API_SERVICE.getPersonRoles(id);

            const allPersonRecords = Array.isArray(personRolesResponse?.records) ? personRolesResponse.records : [];

            // Deduplicate: If the same role appears for different objects within the same facet, show it only once
            // Use a Map to track unique roleName + facet combinations
            const uniqueRoleMap = new Map();
            allPersonRecords.forEach(record => {
                const roleName = (record.roleName || '').toLowerCase();
                const facet = (record.facet || 'Other').toLowerCase();
                const uniqueKey = `${roleName}|${facet}`;
                
                // If we haven't seen this role+facet combination, add it
                // If we have, keep the first occurrence (or you could prefer certain records)
                if (!uniqueRoleMap.has(uniqueKey)) {
                    uniqueRoleMap.set(uniqueKey, record);
                }
            });

            // Convert map values back to array
            const deduplicatedRecords = Array.from(uniqueRoleMap.values());

            // Recalculate facets based on deduplicated records
            const facetCounts = new Map();
            deduplicatedRecords.forEach(record => {
                const facet = record.facet || 'Other';
                facetCounts.set(facet, (facetCounts.get(facet) || 0) + 1);
            });

            const facets = [
                { type: 'All', count: deduplicatedRecords.length },
                ...Array.from(facetCounts.entries())
                    .sort((a, b) => a[0].localeCompare(b[0]))
                    .map(([type, count]) => ({ type, count }))
            ];

            renderRolesTable(container, deduplicatedRecords, facets);
        } catch (e) {
            console.error('Failed to load roles data:', e);
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${window.I18n ? window.I18n.t('tab.roles') : 'ASSIGNED ROLES'}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-exclamation-triangle"></i>
                            <span>${window.I18n ? window.I18n.t('people.failedToLoadTeamData') : 'Failed to load roles data'}</span>
                        </div>
                    </div>
                </div>
            `;
        }
    }

    // Function to update segments tab visibility
    // Tab should ONLY show on the logged-in user's own profile, regardless of user role
    async function updateSegmentsTabVisibility(viewingId) {
        let currentUserId = await getCurrentUserId();
        
        const segmentsTab = document.querySelector('.tab[data-tab="segments"]');
        if (!segmentsTab) {
            console.warn('Segments tab not found in DOM, will retry...');
            // Retry after a short delay in case DOM isn't ready yet
            setTimeout(() => updateSegmentsTabVisibility(viewingId), 100);
            return;
        }
        
        // Normalize IDs to numbers for comparison
        const currentIdNum = currentUserId ? parseInt(String(currentUserId), 10) : null;
        const viewingIdNum = viewingId ? parseInt(String(viewingId), 10) : null;
        const viewingOwnProfile = currentIdNum !== null && viewingIdNum !== null && currentIdNum === viewingIdNum;
        
        console.log('🔍 Segments tab visibility check:', {
            currentUserId: currentUserId,
            currentIdNum: currentIdNum,
            viewingId: viewingId,
            viewingIdNum: viewingIdNum,
            viewingOwnProfile: viewingOwnProfile,
            segmentsTabFound: !!segmentsTab,
            currentTabDisplay: window.getComputedStyle(segmentsTab).display
        });
        
        // Show tab ONLY if viewing own profile (for any user type: regular user, admin, web user)
        if (viewingOwnProfile) {
            // Use !important to ensure it overrides any CSS
            segmentsTab.style.setProperty('display', '', 'important');
            segmentsTab.style.removeProperty('display'); // Remove inline style to let CSS handle it
            segmentsTab.classList.remove('hidden');
        } else {
            // Hide tab when viewing other profiles
            segmentsTab.style.setProperty('display', 'none', 'important');
            segmentsTab.classList.add('hidden');
        }
    }

    // Helper function to get current user ID.
    async function getCurrentUserId() {
        const user = await getCurrentUser();
        const parsedId = extractCurrentUserId(user);
        if (parsedId) {
            console.log('✅ Current user ID:', parsedId, 'Role:', user?.role || user?.Role);
            return parsedId;
        }
        console.warn('⚠️ User ID not found - user may not be logged in');
        return null;
    }
    
    // Helper function to get user ID from sessionStorage (fallback only).
    function getCurrentUserIdFromSession() {
        const parsedId = extractCurrentUserId(getCurrentUserFromSession());
        if (parsedId) {
            return parsedId;
        }
        return null;
    }
    
    // Synchronous version for cases where we can't use async
    function getCurrentUserIdSync() {
        return extractCurrentUserId(getCurrentUserFromSession());
    }

    function renderRolesTable(container, records, facets) {
        const rT = (k) => (window.I18n && window.I18n.t(k)) || k;
        const rolesTitle = rT('people.rolesTitle');
        const safeRecords = Array.isArray(records) ? records : [];
        const safeFacets = Array.isArray(facets) && facets.length
            ? facets
            : [{ type: 'All', count: safeRecords.length }];

        if (safeRecords.length === 0) {
            container.innerHTML = `
                <div class="people-section">
                    <div class="people-header">
                        <div class="people-title">${rolesTitle}</div>
                    </div>
                    <div class="people-content">
                        <div class="people-empty">
                            <i class="fas fa-user-tag"></i>
                                <span>${window.I18n ? window.I18n.t('message.noData') : 'No roles assigned'}</span>
                        </div>
                    </div>
                </div>
            `;
            return;
        }

        const filtersHtml = safeFacets.map((facet, index) => {
            const rawType = facet.type || 'All';
            const encodedType = encodeURIComponent(rawType);
            return `
            <button type="button" class="facet-tab${index === 0 ? ' active' : ''}" data-type="${encodedType}">
                ${escapeHtml(rawType)}
                <span class="facet-count">${facet.count ?? 0}</span>
            </button>
        `;
        }).join('');

        container.innerHTML = `
            <div class="people-section">
                <div class="people-header">
                    <div class="people-title">${rolesTitle}</div>
                </div>
                <div class="people-content">
                    <div class="facet-tab-row">
                        ${filtersHtml}
                    </div>
                    <div class="table-responsive">
                        <table class="people-table roles-table">
                            <thead>
                                <tr>
                                    <th>${rT('people.rolesRole')}</th>
                                    <th>${rT('people.rolesFacet')}</th>
                                    <th>${rT('people.rolesRoleType')}</th>
                                    <th>${rT('people.rolesOwnershipRole')}</th>
                                    <th>${rT('people.rolesAccepted')}</th>
                                </tr>
                            </thead>
                            <tbody></tbody>
                        </table>
                    </div>
                    <div class="table-meta"></div>
                </div>
            </div>
        `;

        const tbody = container.querySelector('tbody');
        const meta = container.querySelector('.table-meta');
        const rolesRecordLabel = (n) => {
            const I = window.I18n && window.I18n.t.bind(window.I18n);
            if (n === 1 && I) return I('people.recordOne') || '1 record';
            if (I) return (I('people.recordCount') || '{count} records').replace('{count}', n);
            return n + ' record' + (n === 1 ? '' : 's');
        };
        const renderRows = (rows) => {
            tbody.innerHTML = rows.map(record => `
                <tr>
                    <td>
                        <div>${escapeHtml(record.roleName || '-')}</div>
                        ${record.roleDescription ? `<div class="table-subtext">${escapeHtml(record.roleDescription)}</div>` : ''}
                    </td>
                    <td>${escapeHtml(record.facet || '-')}</td>
                    <td>${escapeHtml(record.roleType || '-')}</td>
                    <td>${escapeHtml(record.ownershipRole || '-')}</td>
                    <td>${escapeHtml(record.acceptedLabel || record.roleAccepted || '-')}</td>
                </tr>
            `).join('');
            meta.textContent = rolesRecordLabel(rows.length);
        };

        renderRows(safeRecords);

        container.querySelectorAll('.facet-tab').forEach(button => {
            button.addEventListener('click', () => {
                container.querySelectorAll('.facet-tab').forEach(b => b.classList.remove('active'));
                button.classList.add('active');
                const encoded = button.getAttribute('data-type') || 'All';
                const type = decodeURIComponent(encoded);
                const filtered = type === 'All'
                    ? safeRecords
                    : safeRecords.filter(item => (item.facet || '').toLowerCase() === (type || '').toLowerCase());
                renderRows(filtered);
            });
        });
    }

    // Delegated click handler for attribute links (attributes have no view page,
    // so we look up the parent dataset and navigate there with the attribute tab open)
    document.addEventListener('click', function(e) {
        const link = e.target.closest('a[href^="#attribute-lookup-"]');
        if (!link) return;
        e.preventDefault();
        const attrId = link.getAttribute('href').replace('#attribute-lookup-', '');
        if (!attrId) return;
        fetch('/UnisonSearch/attribute?q=' + encodeURIComponent(attrId))
            .then(r => r.json())
            .then(data => {
                if (Array.isArray(data) && data.length > 0) {
                    const attr = data.find(a => String(a.ID || a.id) === String(attrId));
                    if (attr) {
                        const dsId = attr.Dataset_ID || attr.DatasetID || attr['Data Set Name_ID'];
                        if (dsId) {
                            window.open('/view/dataset/' + encodeURIComponent(dsId) + '?tab=attribute&attributeId=' + encodeURIComponent(attrId), '_blank');
                            return;
                        }
                    }
                }
                console.warn('[PEOPLE] Could not find parent dataset for attribute ID:', attrId);
            })
            .catch(err => {
                console.error('[PEOPLE] Error looking up attribute dataset:', err);
            });
    });

    document.addEventListener('DOMContentLoaded', async function() {
        hideEditsIfUnauthenticated();
        const backBtn = document.getElementById('backBtn');
        if (backBtn) backBtn.addEventListener('click', () => window.history.length>1?window.history.back():window.location.assign('/'));
        const urlParams = new URLSearchParams(window.location.search);
        let tabParam = (urlParams.get('tab') || '').toLowerCase();
        const subTabParam = (urlParams.get('subtab') || '').toLowerCase();
        if (subTabParam === 'my-jobs' && tabParam !== 'activity') {
            tabParam = 'activity';
            urlParams.set('tab', 'activity');
            const normalizedUrl = `${window.location.pathname}?${urlParams.toString()}`;
            window.history.replaceState({}, '', normalizedUrl);
        }
        const shouldLoadAbout = tabParam !== 'activity';
        const id = parseId();
        if (id != null) {
            if (window.i18nReadyPromise) await window.i18nReadyPromise;
            updateSegmentsTabVisibility(id);
            if (shouldLoadAbout) {
                load(id).then(async () => {
                    // Check and display lock status
                    if (window.ViewLockHelper) {
                        await window.ViewLockHelper.checkAndDisplayLockStatus('people', id);
                    }
                }).catch(() => {
                    // Still try to update visibility even if load fails
                    updateSegmentsTabVisibility(id);
                });
            }

// Initialize unified edit dropdown
if (window.EditDropdown && id != null) {
    try {
        window.EditDropdown.initialize('people', id, {
            container: '.tab-actions',
            editUrl: `/view/people/people-edit.html?id=${id}`,
            customActions: [
                {
                    category: 'Roles',
                    label: 'Accept',
                    icon: 'fa-check',
                    action: async () => {
                        // Get the active responsibility tab to determine context
                        const activeTab = document.querySelector('.responsibilities-table .facet-tab.active');
                        const activeFacet = activeTab ? activeTab.getAttribute('data-facet') : 'All';

                        try {
                            const response = await window.BUDG_API_SERVICE.getPersonResponsibilities(id);

                            if (!response || response.length === 0) {
                                alert(window.I18n ? window.I18n.t('people.noResponsibilitiesAssigned') : 'No responsibilities found for this person');
                                return;
                            }

                            // If user selected a row, accept that role (no prompt)
                            const selectedId = selectedResponsibilityRoleId;
                            if (selectedId) {
                                const selected = response.find(r => String(extractResponsibilityRoleId(r) ?? '') === String(selectedId));
                                if (!selected) {
                                    alert('Selected role not found. Please reselect the row and try again.');
                                    return;
                                }
                                if (activeFacet && activeFacet !== 'All') {
                                    const facetOk = (selected.objectType || '').toLowerCase() === activeFacet.toLowerCase();
                                    if (!facetOk) {
                                        alert('Selected role is not in the current facet filter. Switch to "All" or select a role in this facet.');
                                        return;
                                    }
                                }
                                if (isRoleAccepted(selected)) {
                                    alert('This role is already accepted.');
                                    return;
                                }

                                const roleId = extractResponsibilityRoleId(selected);
                                if (roleId == null || roleId === '') {
                                    alert('Selected role has no role id.');
                                    return;
                                }

                                const r = await fetch(`/api/people/${id}/roles/${roleId}/accept`, {
                                    method: 'POST',
                                    credentials: 'include',
                                    headers: { 'Content-Type': 'application/json' }
                                });

                                if (r.ok) {
                                    showSuccessMessage('You have accepted 1 role.');
                                    setTimeout(() => {
                                        clearResponsibilitySelection();
                                        loadResponsibilitiesData(id);
                                    }, 800);
                                } else {
                                    alert('Failed to accept role. Please try again.');
                                }
                                return;
                            }

                            // Filter unaccepted roles (fallback: prompt selection)
                            let unacceptedRoles = response.filter(r => !isRoleAccepted(r));

                            // If a specific facet is active (not "All"), filter by that facet
                            if (activeFacet && activeFacet !== 'All') {
                                unacceptedRoles = unacceptedRoles.filter(r =>
                                    (r.objectType || '').toLowerCase() === activeFacet.toLowerCase()
                                );
                            }

                            if (unacceptedRoles.length === 0) {
                                const msg = activeFacet && activeFacet !== 'All'
                                    ? `No unaccepted ${activeFacet} roles found`
                                    : 'All roles have already been accepted';
                                alert(msg);
                                return;
                            }

                            // Build selection list
                            const roleOptions = unacceptedRoles.map((r, idx) =>
                                `${idx + 1}. ${r.roleName || 'Unknown'} - ${r.objectName || 'N/A'} (${r.objectType || 'N/A'})`
                            ).join('\n');

                            const selection = prompt(
                                `Select role to accept:\n\n${roleOptions}\n\nEnter number (1-${unacceptedRoles.length}) or "all" to accept all:`
                            );

                            if (!selection) return;

                            let rolesToAccept = [];

                            if (selection.toLowerCase() === 'all') {
                                rolesToAccept = unacceptedRoles;
                            } else {
                                const index = parseInt(selection) - 1;
                                if (index < 0 || index >= unacceptedRoles.length) {
                                    alert('Invalid selection');
                                    return;
                                }
                                rolesToAccept = [unacceptedRoles[index]];
                            }

                            // Accept the selected role(s)
                            const acceptPromises = rolesToAccept.map(role => {
                                const roleId = extractResponsibilityRoleId(role) ?? (role.roleId || role.id || role.ID);
                                return fetch(`/api/people/${id}/roles/${roleId}/accept`, {
                                    method: 'POST',
                                    credentials: 'include',
                                    headers: { 'Content-Type': 'application/json' }
                                });
                            });

                            const results = await Promise.all(acceptPromises);
                            const allSuccessful = results.every(r => r.ok);

                            if (allSuccessful) {
                                const count = rolesToAccept.length;
                                // Show success message at top of page
                                showSuccessMessage(`You have accepted ${count} role${count > 1 ? 's' : ''}.`);
                                // Reload responsibilities data
                                setTimeout(() => {
                                    clearResponsibilitySelection();
                                    loadResponsibilitiesData(id);
                                }, 1500);
                            } else {
                                alert('Some roles failed to accept. Please try again.');
                            }
                        } catch (error) {
                            console.error('Error accepting role:', error);
                            alert('Failed to accept role. Please try again.');
                        }
                    }
                },
                {
                    category: 'Roles',
                    label: 'Undo Accept',
                    icon: 'fa-times',
                    action: async () => {
                        // Get the active responsibility tab to determine context
                        const activeTab = document.querySelector('.responsibilities-table .facet-tab.active');
                        const activeFacet = activeTab ? activeTab.getAttribute('data-facet') : 'All';

                        try {
                            const response = await window.BUDG_API_SERVICE.getPersonResponsibilities(id);

                            if (!response || response.length === 0) {
                                alert(window.I18n ? window.I18n.t('people.noResponsibilitiesAssigned') : 'No responsibilities found for this person');
                                return;
                            }

                            // If user selected a row, undo that role (no prompt)
                            const selectedId = selectedResponsibilityRoleId;
                            if (selectedId) {
                                const selected = response.find(r => String(extractResponsibilityRoleId(r) ?? '') === String(selectedId));
                                if (!selected) {
                                    alert('Selected role not found. Please reselect the row and try again.');
                                    return;
                                }
                                if (activeFacet && activeFacet !== 'All') {
                                    const facetOk = (selected.objectType || '').toLowerCase() === activeFacet.toLowerCase();
                                    if (!facetOk) {
                                        alert('Selected role is not in the current facet filter. Switch to "All" or select a role in this facet.');
                                        return;
                                    }
                                }
                                if (!isRoleAccepted(selected)) {
                                    alert('This role is not accepted yet.');
                                    return;
                                }

                                const roleId = extractResponsibilityRoleId(selected);
                                if (roleId == null || roleId === '') {
                                    alert('Selected role has no role id.');
                                    return;
                                }

                                const r = await fetch(`/api/people/${id}/roles/${roleId}/undo-accept`, {
                                    method: 'POST',
                                    credentials: 'include',
                                    headers: { 'Content-Type': 'application/json' }
                                });

                                if (r.ok) {
                                    showSuccessMessage('You have undone acceptance for 1 role.');
                                    setTimeout(() => {
                                        clearResponsibilitySelection();
                                        loadResponsibilitiesData(id);
                                    }, 800);
                                } else {
                                    alert('Failed to undo role acceptance. Please try again.');
                                }
                                return;
                            }

                            // Filter accepted roles (fallback: prompt selection)
                            let acceptedRoles = response.filter(r => isRoleAccepted(r));

                            // If a specific facet is active (not "All"), filter by that facet
                            if (activeFacet && activeFacet !== 'All') {
                                acceptedRoles = acceptedRoles.filter(r =>
                                    (r.objectType || '').toLowerCase() === activeFacet.toLowerCase()
                                );
                            }

                            if (acceptedRoles.length === 0) {
                                const msg = activeFacet && activeFacet !== 'All'
                                    ? `No accepted ${activeFacet} roles found`
                                    : 'No accepted roles to undo';
                                alert(msg);
                                return;
                            }

                            // Build selection list
                            const roleOptions = acceptedRoles.map((r, idx) =>
                                `${idx + 1}. ${r.roleName || 'Unknown'} - ${r.objectName || 'N/A'} (${r.objectType || 'N/A'})`
                            ).join('\n');

                            const selection = prompt(
                                `Select role to undo acceptance:\n\n${roleOptions}\n\nEnter number (1-${acceptedRoles.length}) or "all" to undo all:`
                            );

                            if (!selection) return;

                            let rolesToUndo = [];

                            if (selection.toLowerCase() === 'all') {
                                rolesToUndo = acceptedRoles;
                            } else {
                                const index = parseInt(selection) - 1;
                                if (index < 0 || index >= acceptedRoles.length) {
                                    alert('Invalid selection');
                                    return;
                                }
                                rolesToUndo = [acceptedRoles[index]];
                            }

                            // Undo accept for the selected role(s)
                            const undoPromises = rolesToUndo.map(role => {
                                const roleId = extractResponsibilityRoleId(role) ?? (role.roleId || role.id || role.ID);
                                return fetch(`/api/people/${id}/roles/${roleId}/undo-accept`, {
                                    method: 'POST',
                                    credentials: 'include',
                                    headers: { 'Content-Type': 'application/json' }
                                });
                            });

                            const results = await Promise.all(undoPromises);
                            const allSuccessful = results.every(r => r.ok);

                            if (allSuccessful) {
                                const count = rolesToUndo.length;
                                // Show success message at top of page
                                showSuccessMessage(`You have undone acceptance for ${count} role${count > 1 ? 's' : ''}.`);
                                // Reload responsibilities data
                                setTimeout(() => {
                                    clearResponsibilitySelection();
                                    loadResponsibilitiesData(id);
                                }, 1500);
                            } else {
                                alert('Some roles failed to undo. Please try again.');
                            }
                        } catch (error) {
                            console.error('Error undoing role acceptance:', error);
                            alert('Failed to undo role acceptance. Please try again.');
                        }
                    }
                }
            ]
        });
    } catch (error) {
        console.error('Failed to initialize edit dropdown:', error);
    }
}
        }

        // Wire up tab edit button based on active tab
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            tabEditBtn.addEventListener('click', function() {
                if (id != null) {
                    const activeTab = document.querySelector('.tab.active');
                    const tabName = activeTab ? activeTab.getAttribute('data-tab') : 'about';
                    
                    if (tabName === 'about') {
                        // For about tab, navigate to edit page
                        window.location.href = `/view/people/people-edit.html?id=${id}`;
                    } else {
                        // For other tabs, navigate to separate edit page
                        window.location.href = `/view/people/people-edit.html?id=${id}`;
                    }
                }
            });
        }

        // Tabs: About Me vs other tabs
        const tabs = document.querySelectorAll('.tab-container .tab');
        tabs.forEach(function(btn){
            btn.addEventListener('click', function(){
                tabs.forEach(function(b){ b.classList.remove('active'); });
                this.classList.add('active');

                const body = document.querySelector('.content-body');
                if (!body) return;

                const which = this.getAttribute('data-tab') || this.textContent.trim().toLowerCase();
                
                if (which === 'about') {
                    // restore the main people view grid
                    body.innerHTML = '<div id="peopleViewContainer" class="view-grid" style="padding:10px;"></div>';
                    if (id != null) load(id);
                    // rebuild navigator after load completes
                    setTimeout(buildSectionNavigator, 300);
                } else if (which === 'team') {
                    body.innerHTML = '<div id="peopleTeamContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadTeamData(id);
                } else if (which === 'responsibilities') {
                    body.innerHTML = '<div id="peopleResponsibilitiesContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadResponsibilitiesData(id);
                } else if (which === 'following') {
                    body.innerHTML = '<div id="peopleFollowingContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadFollowingData(id);
                } else if (which === 'activity') {
                    body.innerHTML = '<div id="peopleActivityContainer"></div>';
                    if (id != null) {
                        loadActivityData(id).then(() => {
                            const urlSubTab = (new URLSearchParams(window.location.search).get('subtab') || '').toLowerCase();
                            if (urlSubTab === 'my-jobs') {
                                const myJobsTab = document.querySelector('.sub-tab[data-sub-tab="my-jobs"]');
                                if (myJobsTab) {
                                    myJobsTab.click();
                                }
                            }
                        });
                    }
                } else if (which === 'change') {
                    body.innerHTML = '<div id="peopleChangeContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadChangeRequestData(id);
                } else if (which === 'segments') {
                    body.innerHTML = '<div id="peopleSegmentsContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadSegmentsData(id);
                } else if (which === 'roles') {
                    body.innerHTML = '<div id="peopleRolesContainer" class="view-section" style="grid-column:1/-1;"></div>';
                    loadRolesData(id);
                } else {
                    // restore the main people view grid
                    body.innerHTML = '<div id="peopleViewContainer" class="view-grid"></div>';
                    if (id != null) load(id);
                }
            });
        });

        // Activate requested tab/subtab after listeners are wired.
        if (tabParam) {
            const tab = document.querySelector(`.tab[data-tab="${tabParam}"]`);
            if (tab) {
                tab.click();
                if (subTabParam && tabParam !== 'activity') {
                    const subTab = document.querySelector(`.sub-tab[data-sub-tab="${subTabParam}"]`);
                    if (subTab) {
                        subTab.click();
                    }
                }
            }
        }
    });

    // Helper functions for responsibilities
    function getObjectIcon(objectType) {
        const iconMap = {
            'Data Set': 'database',
            'System': 'server',
            'System Interface': 'exchange-alt',
            'Glossary': 'book',
            'Process': 'cogs',
            'Product': 'box',
            'Business Area': 'building',
            'Client': 'user-tie',
            'Committee': 'users',
            'Legal Entity': 'balance-scale',
            'Attribute': 'tag'
        };
        return iconMap[objectType] || 'file';
    }

    function getStatusClass(status) {
        if (!status) return 'active';
        
        // Handle both string and number status values
        const statusStr = typeof status === 'string' ? status : String(status);
        const statusLower = statusStr.toLowerCase();
        
        if (statusLower.includes('pending')) return 'pending';
        if (statusLower.includes('inactive')) return 'inactive';
        if (statusLower.includes('active')) return 'active';
        return 'active';
    }

    function formatDate(dateString) {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    }

    function addFacetFilterListeners() {
        const facetTabs = document.querySelectorAll('.facet-tab');
        const tableRows = document.querySelectorAll('.responsibilities-table-content tbody tr');
        
        facetTabs.forEach(tab => {
            tab.addEventListener('click', function() {
                const selectedFacet = this.getAttribute('data-facet');
                
                // Update active tab
                facetTabs.forEach(t => t.classList.remove('active'));
                this.classList.add('active');
                
                // Filter table rows
                tableRows.forEach(row => {
                    if (selectedFacet === 'All') {
                        // Show all rows
                        row.style.display = 'table-row';
                    } else {
                        const rowFacet = row.getAttribute('data-facet');
                        if (rowFacet === selectedFacet) {
                            row.style.display = 'table-row';
                        } else {
                            row.style.display = 'none';
                        }
                    }
                });

                // If selected row is now hidden, clear selection
                const selectedRow = document.querySelector('.responsibilities-table-content tbody tr.is-selected');
                if (selectedRow && selectedRow.style.display === 'none') {
                    clearResponsibilitySelection();
                }
            });
        });
    }
    // Helper function to show success message at top of page
    function showSuccessMessage(message) {
        // Remove any existing success message
        const existingMsg = document.querySelector('.role-success-message');
        if (existingMsg) {
            existingMsg.remove();
        }

        // Create success message banner
        const banner = document.createElement('div');
        banner.className = 'role-success-message';
        banner.style.cssText = `
            position: fixed;
            top: 60px;
            left: 0;
            right: 0;
            background: #d4edda;
            color: #155724;
            padding: 12px 20px;
            border-bottom: 1px solid #c3e6cb;
            z-index: 9999;
            display: flex;
            align-items: center;
            justify-content: space-between;
            animation: slideDown 0.3s ease-out;
        `;

        banner.innerHTML = `
            <span>${escapeHtml(message)}</span>
            <button onclick="this.parentElement.remove()" style="background: none; border: none; color: #155724; cursor: pointer; font-size: 20px; padding: 0 8px;">&times;</button>
        `;

        document.body.insertBefore(banner, document.body.firstChild);

        // Auto-remove after 5 seconds
        setTimeout(() => {
            if (banner && banner.parentElement) {
                banner.style.animation = 'slideUp 0.3s ease-out';
                setTimeout(() => banner.remove(), 300);
            }
        }, 5000);
    }
})();


