(function() {
    function parseViewRoute() {
        // Expect /view/{entity}/{id}
        const parts = window.location.pathname.split('/').filter(Boolean);
        const viewIdx = parts.indexOf('view');
        if (viewIdx === -1 || parts.length < viewIdx + 3) return null;
        const entity = decodeURIComponent(parts[viewIdx + 1] || '').toLowerCase();
        const id = parseInt(parts[viewIdx + 2], 10);
        if (!entity || Number.isNaN(id)) return null;
        return { entity, id };
    }

    function setHeader(icon, title) {
        const iconEl = document.getElementById('viewIcon');
        if (iconEl) iconEl.className = `fas ${icon}`;
        const titleEl = document.getElementById('viewTitle');
        if (titleEl) titleEl.textContent = title;
    }

    function renderItem(label, valueHtml) {
        return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${valueHtml ?? '<span class="empty">-</span>'}</div></div>`;
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

    function renderHierarchy(h) {
        if (!h || (!h.parentId && !h.parentName)) return '<span class="empty">No parent</span>';
        const parent = escapeHtml(h.parentName || `#${h.parentId}`);
        return `<div class="hierarchy"><span class="crumb">${parent}</span></div>`;
    }

    async function renderSystem(id) {
        setHeader('fa-desktop', 'System View');
        const container = document.getElementById('viewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n?.t('message.loading') || 'Loading...'}</div>`;
        try {
            const data = await window.BUDG_API_SERVICE.getSystemById(id);
            const cia = data.cia || {};
            const ciaHtml = `<div class="cia-inline">
                <div class="cia-item"><span class="view-badge">C</span> ${escapeHtml(cia.c ?? '')}</div>
                <div class="cia-item"><span class="view-badge">I</span> ${escapeHtml(cia.i ?? '')}</div>
                <div class="cia-item"><span class="view-badge">A</span> ${escapeHtml(cia.a ?? '')}</div>
            </div>`;

            const left = `
                <div class="view-section">
                    <div class="section-title">DEFINITION</div>
                    ${renderItem('Short Name', escapeHtml(data.name))}
                    ${renderItem('Long Name', escapeHtml(data.longName))}
                    ${renderItem('Description', _richHtml(data.description))}
                    ${renderItem('URL', data.url ? `<a href="${escapeHtml(data.url)}" target="_blank">${escapeHtml(data.url)}</a>` : '')}
                    ${renderItem('Hierarchy', renderHierarchy(data.hierarchy))}
                </div>
            `;

            const right = `
                <div class="view-section">
                    <div class="section-title">CLASSIFICATIONS</div>
                    ${renderItem('BUDG Status', escapeHtml(data.statusName))}
                    ${renderItem('Lifecycle', escapeHtml(data.lifecycleName))}
                    ${renderItem('BUDG Viewing', escapeHtml(data.viewingName))}
                    ${renderItem('Type', escapeHtml(data.typeName))}
                    ${renderItem('Classification', escapeHtml(data.classificationName))}
                    ${renderItem('CIA Rating', ciaHtml)}
                    ${renderItem('Asset ID', escapeHtml(data.assetId))}
                </div>
            `;

            // Placeholder sections for future expansions to match requested fields
            const fullWidth = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">OTHER INFORMATION</div>
                    ${renderItem('Data Content Summary', '<span class="empty">N/A</span>')}
                    ${renderItem('Documents', '<span class="empty">No documents</span>')}
                </div>
            `;

            container.innerHTML = left + right + fullWidth;
            
            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('system', id, null, '.form-actions');
            }
        } catch (e) {
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column: 1/-1; color: var(--danger, #b91c1c);\">Failed to load system (id=${id}).</div>`;
        }
    }

    function renderChips(values) {
        if (!values || values.length === 0) return '<span class="empty">-</span>';
        return values.map(v => `<span class="view-badge">${escapeHtml(v)}</span>`).join(' ');
    }

    async function renderDataset(id) {
        setHeader('fa-database', 'Data Set View');
        const container = document.getElementById('viewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n?.t('message.loading') || 'Loading...'}</div>`;
        try {
            const d = await window.BUDG_API_SERVICE.getDatasetById(id);
            const left = `
                <div class="view-section">
                    <div class="section-title">DETAILS</div>
                    ${renderItem('Name', escapeHtml(d.name))}
                    ${renderItem('System Short Name', escapeHtml(d.systemName))}
                    ${renderItem('Ref', escapeHtml(d.ref))}
                    ${renderItem('Glossary Name', escapeHtml(d.glossaryName))}
                    ${renderItem('Definition', escapeHtml(d.definition))}
                    ${renderItem('Usage', escapeHtml(d.usage))}
                </div>
            `;

            const score = [];
            if (d.dqScore != null) score.push(`Score: ${escapeHtml(d.dqScore)}`);
            if (d.dqGreen != null) score.push(`Green: ${escapeHtml(d.dqGreen)}`);
            if (d.dqAmber != null) score.push(`Amber: ${escapeHtml(d.dqAmber)}`);

            const right = `
                <div class="view-section">
                    <div class="section-title">CLASSIFICATIONS</div>
                    ${renderItem('BUDG Status', escapeHtml(d.statusName))}
                    ${renderItem('Type', escapeHtml(d.typeName))}
                    ${renderItem('BUDG Viewing', escapeHtml(d.viewingName))}
                    ${renderItem('Lifecycle', escapeHtml(d.lifecycleName))}
                </div>
            `;

            const bottom = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">OTHER</div>
                    ${renderItem('Documents', '<span class="empty">No documents</span>')}
                    ${renderItem('Scorecard', score.length ? score.join(' | ') : '<span class="empty">N/A</span>')}
                </div>
            `;

            container.innerHTML = left + right + bottom;
            
            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('dataset', id, null, '.form-actions');
            }
        } catch (e) {
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column:1/-1;color:var(--danger,#b91c1c);\">Failed to load dataset (id=${id}).</div>`;
        }
    }

    async function enforceViewRoleUi() {
        try {
            const resp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!resp.ok) {
                hideEditControls();
                return;
            }
            const me = await resp.json();
            const role = (me.role || '').toString().toLowerCase();
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            if (!isAdmin) hideEditControls();
        } catch (_) {
            hideEditControls();
        }
    }

    function hideEditControls() {
        const editPrimaryBtn = document.getElementById('editPrimaryBtn');
        const editDropdownToggle = document.getElementById('editDropdownToggle');
        const editDropdown = document.getElementById('editDropdown');
        if (editPrimaryBtn) editPrimaryBtn.style.display = 'none';
        if (editDropdownToggle) editDropdownToggle.style.display = 'none';
        if (editDropdown) editDropdown.style.display = 'none';
    }

    document.addEventListener('DOMContentLoaded', function() {
        const backBtn = document.getElementById('backBtn');
        if (backBtn) {
            backBtn.addEventListener('click', function() {
                window.history.length > 1 ? window.history.back() : window.location.assign('/');
            });
        }

        // Enforce role-based UI for view pages
        enforceViewRoleUi();

        const route = parseViewRoute();
        if (!route) return;
        const entity = route.entity;
        switch (entity) {
            case 'system':
            case 'systems':
                renderSystem(route.id);
                break;
            case 'dataset':
            case 'datasets':
                renderDataset(route.id);
                break;
            case 'interface':
            case 'interfaces':
                setHeader('fa-plug', 'System Interface View');
                document.getElementById('viewContainer').innerHTML = `<div class="view-section" style="grid-column:1/-1;">${window.I18n?.t('message.comingSoon') || 'System Interface view coming next.'}</div>`;
                break;
            case 'glossary':
            case 'glossaries':
                setHeader('fa-book', 'Glossary View');
                document.getElementById('viewContainer').innerHTML = `<div class="view-section" style="grid-column:1/-1;">${window.I18n?.t('message.comingSoon') || 'Glossary view coming next.'}</div>`;
                break;
            /* Added from EDITOR - new entity types */
            case 'policy':
            case 'policies':
                renderPolicy(route.id);
                break;
            case 'process':
            case 'processes':
                renderProcess(route.id);
                break;
            case 'project':
            case 'projects':
                renderProject(route.id);
                break;
            case 'business-area':
            case 'business area':
            case 'business areas':
                renderBusinessArea(route.id);
                break;
            default:
                setHeader('fa-eye', 'View');
                document.getElementById('viewContainer').innerHTML = `<div class="view-section" style="grid-column:1/-1;">${window.I18n?.t('message.notImplemented') || 'This entity view is not implemented yet.'}</div>`;
        }
    });

    /* Added from EDITOR: resolvePolicyReferences */
    async function resolvePolicyReferences(policy) {
        console.log('=== RESOLVING POLICY REFERENCES ===');
        console.log('Policy data before resolution:', policy);
        
        const api = window.BUDG_API_SERVICE;
        const tasks = [];

        // Created by person name - check multiple possible field names
        const createdById = policy.createdBy_ID || policy.created_by_id || policy.createdBy || policy.created_by || policy.createdById;
        if (!policy.createdByName && createdById) {
            console.log('Resolving created by ID:', createdById);
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(createdById);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    policy.createdByName = fullName || person?.Email || person?.email || String(createdById);
                    console.log('Resolved created by name:', policy.createdByName);
                } catch(e) { 
                    console.error('Failed to resolve created by name:', e);
                    policy.createdByName = String(createdById);
                }
            })());
        }

        // Last updated by person name - check multiple possible field names
        const lastUpdatedById = policy.lastUpdatedUser_ID || policy.last_updated_user_id || policy.lastUpdatedBy || policy.last_updated_by || policy.lastUpdateUserId;
        if (!policy.lastUpdatedByName && lastUpdatedById) {
            console.log('Resolving last updated by ID:', lastUpdatedById);
            tasks.push((async () => {
                try {
                    let person = await api.getPersonById(lastUpdatedById);
                    if (person && person.data) person = person.data;
                    const fullName = [person?.First_Name || person?.first_name, person?.Last_Name || person?.last_name]
                        .filter(Boolean).join(' ').trim();
                    policy.lastUpdatedByName = fullName || person?.Email || person?.email || String(lastUpdatedById);
                    console.log('Resolved last updated by name:', policy.lastUpdatedByName);
                } catch(e) { 
                    console.error('Failed to resolve last updated by name:', e);
                    policy.lastUpdatedByName = String(lastUpdatedById);
                }
            })());
        }

        // Status name - check multiple possible field names
        const statusId = policy.status_id || policy.Status_ID || policy.status;
        if (!policy.statusName && statusId) {
            console.log('Resolving status ID:', statusId);
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

        // Lifecycle status name - check multiple possible field names
        const lifecycleId = policy.lifecycle_status_id || policy.Lifecycle_Status_ID || policy.lifecycleStatus || policy.lifecycle_status;
        if (!policy.lifecycleName && lifecycleId) {
            console.log('Resolving lifecycle ID:', lifecycleId);
            tasks.push((async () => {
                try {
                    let lifecycle = await api.getStatusById(lifecycleId);
                    if (lifecycle && lifecycle.data) lifecycle = lifecycle.data;
                    policy.lifecycleName = lifecycle?.primaryname || lifecycle?.Name || lifecycle?.name || String(lifecycleId);
                    console.log('Resolved lifecycle name:', policy.lifecycleName);
                } catch(e) { 
                    console.error('Failed to resolve lifecycle name:', e);
                    policy.lifecycleName = String(lifecycleId);
                }
            })());
        }

        // Policy type name - check multiple possible field names
        const typeId = policy.policy_type_id || policy.Policy_Type_ID || policy.policyType || policy.policy_type;
        if (!policy.typeName && typeId) {
            console.log('Resolving policy type ID:', typeId);
            tasks.push((async () => {
                try {
                    // Get policy type list and find the matching type
                    const typeList = await api.getPolicyTypeList();
                    console.log('Policy type list:', typeList);
                    
                    let typeData = null;
                    if (typeList && Array.isArray(typeList)) {
                        typeData = typeList.find(t => t.id === typeId || t.ID === typeId);
                    } else if (typeList && typeList.data && Array.isArray(typeList.data)) {
                        typeData = typeList.data.find(t => t.id === typeId || t.ID === typeId);
                    }
                    
                    if (typeData) {
                        policy.typeName = typeData.primaryname || typeData.Name || typeData.name || String(typeId);
                        console.log('Resolved policy type name:', policy.typeName);
                    } else {
                        console.log('Policy type not found in list, using ID as name');
                        policy.typeName = String(typeId);
                    }
                } catch(e) { 
                    console.error('Failed to resolve policy type name:', e);
                    policy.typeName = String(typeId);
                }
            })());
        }

        console.log('Starting resolution tasks:', tasks.length);
        await Promise.all(tasks);
        console.log('All resolution tasks completed');
        console.log('Policy data after resolution:', policy);
        return policy;
    }

    /* Added from EDITOR: renderPolicy */
    async function renderPolicy(id) {
        console.log('=== RENDERING POLICY (BASIC SYSTEM) ===');
        console.log('Policy ID:', id);
        
        setHeader('fa-file-alt', 'Policy View');
        const container = document.getElementById('viewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n?.t('message.loading') || 'Loading...'}</div>`;
        
        try {
            console.log('Calling API: getPolicyById(' + id + ')');
            const data = await window.BUDG_API_SERVICE.getPolicyById(id);
            console.log('API Response:', data);
            console.log('API Response keys:', Object.keys(data));
            console.log('API Response values:', Object.values(data));
            
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
            }
            
            // If actualData is still an array, take the first element
            if (Array.isArray(actualData) && actualData.length > 0) {
                actualData = actualData[0];
                console.log('Using first array element as actual data');
            }
            
            console.log('Final data to use:', actualData);
            
            // Resolve foreign key references
            console.log('Resolving references...');
            actualData = await resolvePolicyReferences(actualData);
            console.log('References resolved, final data:', actualData);
            
            // Update page title with policy name=$
            const titleEl = document.getElementById('viewTitle');
            if (titleEl && actualData.primaryName) {
                titleEl.textContent = `${actualData.refNumber || 'POL'}: ${actualData.primaryName}`;
            }

            // Left column - Description
            const left = `
                <div class="view-section">
                    <div class="section-title">DESCRIPTION</div>
                    ${renderItem('Description', _richHtml(actualData.description))}
                    ${renderItem('Ref.', escapeHtml(actualData.refNumber || actualData.ref_number))}
                    ${renderItem('Internal', actualData.internal ? 'Yes' : 'No')}
                    ${renderItem('Type', escapeHtml(actualData.typeName || actualData.policy_type || actualData.type))}
                    ${renderItem('URL', actualData.url ? `<a href="${escapeHtml(actualData.url)}" target="_blank">${escapeHtml(actualData.url)}</a>` : '')}
                </div>
            `;

            // Right column - Classifications
            const right = `
                <div class="view-section">
                    <div class="section-title">CLASSIFICATIONS</div>
                    <div class="section-subtitle">BASIC CLASSIFICATIONS</div>
                    ${renderItem('BUDG Status', escapeHtml(actualData.statusName || actualData.status))}
                    ${renderItem('Lifecycle', escapeHtml(actualData.lifecycleName || actualData.lifecycle_status))}
                    ${renderItem('BUDG Viewing', actualData.isPublic ? 'Public' : 'Private')}
                    ${renderItem('Created By', escapeHtml(actualData.createdByName || actualData.created_by_name))}
                    ${renderItem('Created', actualData.createDatetime || actualData.create_datetime || actualData.createdDate || '-')}
                    ${renderItem('Last Updated By', escapeHtml(actualData.lastUpdatedByName || actualData.last_updated_by_name))}
                    ${renderItem('Last Updated', actualData.lastUpdateDatetime || actualData.last_update_datetime || actualData.lastUpdatedDate || '-')}
                </div>
            `;

            container.innerHTML = left + right;
            console.log('Policy rendered successfully');
            
            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('policy', id, null, '.form-actions');
            }

        } catch (e) {
            console.error('Error loading policy:', e);
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column:1/-1;color:var(--danger,#b91c1c);\">Failed to load policy (id=${id}). Error: ${e.message}</div>`;
        }
    }

    /* Added from EDITOR: renderProcess */
    async function renderProcess(id) {
        console.log('=== RENDERING PROCESS (BASIC SYSTEM) ===');
        console.log('Process ID:', id);
        
        setHeader('fa-project-diagram', 'Process View');
        const container = document.getElementById('viewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n?.t('message.loading') || 'Loading...'}</div>`;
        
        try {
            console.log('Calling API: getProcessById(' + id + ')');
            const data = await window.BUDG_API_SERVICE.getProcessById(id);
            console.log('API Response:', data);
            console.log('API Response keys:', Object.keys(data));
            console.log('API Response values:', Object.values(data));
            
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
            
            // If actualData is still an array, take the first element
            if (Array.isArray(actualData) && actualData.length > 0) {
                actualData = actualData[0];
                console.log('Using first array element as actual data');
            }
            
            console.log('Final data to use:', actualData);
            
            // Update page title with process name
            const titleEl = document.getElementById('viewTitle');
            if (titleEl && actualData.primaryName) {
                titleEl.textContent = `${actualData.refNumber || 'PROC'}: ${actualData.primaryName}`;
            }

            // Left column - Definition
            const left = `
                <div class="view-section">
                    <div class="section-title">DEFINITION</div>
                    ${renderItem('Description', _richHtml(actualData.description || actualData.primaryName || actualData.name))}
                    ${renderItem('Ref.', escapeHtml(actualData.refNumber || actualData.ref_number))}
                    ${renderItem('Classification', escapeHtml(actualData.classification || (window.I18n?.t('message.notSpecified') || 'Not specified')))}
                    ${renderItem('Automation', escapeHtml(actualData.automation || (window.I18n?.t('message.notSpecified') || 'Not specified')))}
                    ${renderItem('Input Description', _richHtml(actualData.inputDescription))}
                    ${renderItem('Output Description', _richHtml(actualData.outputDescription))}
                    ${renderItem('Permissions', actualData.permissions || (window.I18n?.t('message.notSpecified') || 'Not specified'))}
                    ${renderItem('Duration', escapeHtml(actualData.duration || (window.I18n?.t('message.notSpecified') || 'Not specified')))}
                </div>
            `;

            // Right column - Classifications
            const right = `
                <div class="view-section">
                    <div class="section-title">CLASSIFICATIONS</div>
                    <div class="section-subtitle">BASIC CLASSIFICATIONS</div>
                    ${renderItem('BUDG Status', escapeHtml(actualData.statusName || actualData.status))}
                    ${renderItem('Lifecycle', escapeHtml(actualData.lifecycleName || actualData.lifecycleStatus))}
                    ${renderItem('Type', escapeHtml(actualData.typeName || actualData.type))}
                    ${renderItem('Step Type', escapeHtml(actualData.stepTypeName || actualData.stepType))}
                    ${renderItem('BUDG Viewing', escapeHtml(actualData.viewingName || actualData.viewing || 'Public'))}
                    <div class="section-subtitle">OTHER INFORMATION</div>
                    ${renderItem('Created By', escapeHtml(actualData.createdByName || actualData.created_by_name))}
                    ${renderItem('Created', actualData.createDatetime || actualData.create_datetime || actualData.createdDate || '-')}
                    ${renderItem('Last Updated By', escapeHtml(actualData.lastUpdatedByName || actualData.last_updated_by_name))}
                    ${renderItem('Last Updated', actualData.lastUpdateDatetime || actualData.last_update_datetime || actualData.lastUpdatedDate || '-')}
                    ${renderItem('Last Approved Date', escapeHtml(actualData.lastApprovedDate || 'Not Available'))}
                    ${renderItem('Next Review Date', escapeHtml(actualData.nextReviewDate || 'Not Enforced'))}
                </div>
            `;

            container.innerHTML = left + right;
            console.log('Process rendered successfully');
            
            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('process', id, null, '.form-actions');
            }

        } catch (e) {
            console.error('Error loading process:', e);
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column:1/-1;color:var(--danger,#b91c1c);\">Failed to load process (id=${id}). Error: ${e.message}</div>`;
        }
    }

    /* Added from EDITOR: renderProject */
    async function renderProject(id) {
        console.log('=== RENDERING PROJECT (BASIC SYSTEM) ===');
        console.log('Project ID:', id);
        
        setHeader('fa-folder', 'Project View');
        const container = document.getElementById('viewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n?.t('message.loading') || 'Loading...'}</div>`;
        
        try {
            console.log('Calling API: getProjectById(' + id + ')');
            const data = await window.BUDG_API_SERVICE.getProjectById(id);
            console.log('API Response:', data);
            console.log('API Response keys:', Object.keys(data));
            console.log('API Response values:', Object.values(data));
            
            // Extract actual data - handle different response formats
            let actualData = data;
            if (data && data.data) {
                actualData = data.data;
                console.log('Using data.data as actual data');
            } else if (data && data.result) {
                actualData = data.result;
                console.log('Using data.result as actual data');
            } else if (data && data.project) {
                actualData = data.project;
                console.log('Using data.project as actual data');
            }
            
            // If actualData is still an array, take the first element
            if (Array.isArray(actualData) && actualData.length > 0) {
                actualData = actualData[0];
                console.log('Using first array element as actual data');
            }
            
            console.log('Final data to use:', actualData);
            
            // Update page title with project name
            const titleEl = document.getElementById('viewTitle');
            if (titleEl && actualData.primaryname) {
                titleEl.textContent = `${actualData.refnumber || 'PROJ'}: ${actualData.primaryname}`;
            }

            // Left column - Definition
            const left = `
                <div class="view-section">
                    <div class="section-title"><i class="fa-solid fa-circle-info"></i>DEFINITION</div>
                    ${renderItem('', escapeHtml(actualData.primaryname || actualData.name || ''))}
                    ${renderItem('Ref.', escapeHtml(actualData.refnumber || actualData.ref_number))}
                    ${renderItem('Type', actualData.typeName || actualData.project_type_name || actualData.type || '<span class="empty">-</span>')}
                    ${renderItem('Classification', actualData.classificationName || actualData.classification_name || '<span class="empty">Not specified</span>')}
                </div>
            `;

            // Right column - Classifications
            const right = `
                <div class="view-section">
                    <div class="section-title"><i class="fa-solid fa-layer-group"></i>CLASSIFICATIONS</div>
                    <div class="section-subtitle">BASIC CLASSIFICATIONS</div>
                    ${renderItem('BUDG Status:', actualData.statusName || actualData.status || '<span class="empty">-</span>')}
                    ${renderItem('Lifecycle:', actualData.lifecycleName || actualData.lifecycle_status || '<span class="empty">-</span>')}
                    ${renderItem('BUDG Viewing:', actualData.viewingName || actualData.viewing || (actualData.is_public ? 'Public' : 'Private'))}
                    <div class="section-subtitle">OTHER INFORMATION</div>
                    ${renderItem('RAG:', actualData.ragName || actualData.rag || '<span class="empty">-</span>')}
                    ${renderItem('Start Date:', actualData.startdate || actualData.start_date || '<span class="empty">-</span>')}
                    ${renderItem('End Date:', actualData.enddate || actualData.end_date || '<span class="empty">-</span>')}
                    ${renderItem('Created By:', actualData.createdByName || actualData.created_by_name || '<span class="empty">-</span>')}
                    ${renderItem('Created:', actualData.createdatetime || actualData.created_datetime || '<span class="empty">-</span>')}
                    ${renderItem('Last Updated By:', actualData.lastUpdatedByName || actualData.last_updated_by_name || '<span class="empty">-</span>')}
                    ${renderItem('Last Updated:', actualData.lastupdatedatetime || actualData.last_updated_datetime || '<span class="empty">-</span>')}
                </div>
            `;

            container.innerHTML = left + right;
            console.log('Project rendered successfully');
            
            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('project', id, null, '.form-actions');
            }

        } catch (e) {
            console.error('Error loading project:', e);
            container.innerHTML = `<div class=\"view-section\" style=\"grid-column:1/-1;color:var(--danger,#b91c1c);\">Failed to load project (id=${id}). Error: ${e.message}</div>`;
        }
    }

    /* Added from EDITOR: renderBusinessArea */
    async function renderBusinessArea(id) {
        console.log('=== RENDERING BUSINESS AREA ===');
        console.log('Business Area ID:', id);
        
        setHeader('fa-briefcase', 'Business Area View');
        const container = document.getElementById('viewContainer');
        container.innerHTML = `<div class="view-section" style="grid-column: 1/-1;">${window.I18n?.t('message.loading') || 'Loading...'}</div>`;
        
        try {
            console.log('Calling API: getBusinessAreaById(' + id + ')');
            const data = await window.BUDG_API_SERVICE.getBusinessAreaById(id);
            console.log('API Response:', data);
            
            // Extract actual data - handle different response formats
            let actualData = data;
            if (data && data.data) {
                actualData = data.data;
                console.log('Using data.data as actual data');
            }
            
            console.log('Actual data:', actualData);
            console.log('Actual data keys:', Object.keys(actualData));
            
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
                            <div class="view-label">${label.includes('<') ? label : escapeHtml(label)}</div>
                            <div class="view-value">${value}</div>
                        </div>
                    `;
                } else {
                    value = escapeHtml(value);
                }
                return `
                    <div class="view-item">
                        <div class="view-label">${label.includes('<') ? label : escapeHtml(label)}</div>
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
                try {
                    const d = typeof date === 'string' || typeof date === 'number' ? new Date(date) : date;
                    if (isNaN(d.getTime())) return escapeHtml(String(date));
                    // Format as date only (DD/MM/YYYY) instead of full timestamp
                    return escapeHtml(d.toLocaleDateString('en-GB'));
                } catch (_) {
                    return escapeHtml(String(date));
                }
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
                    return '<span class="status-badge status-active">Public</span>';
                } else {
                    return '<span class="status-badge status-inactive">Private</span>';
                }
            }

            // Left column (DESCRIPTION and DEFINITION stacked)
            const left = `
                <div class="view-section">
                    <div class="section-title">
                        <i class="fas fa-cog" style="margin-right:.4rem;"></i>
                        DESCRIPTION
                        <i class="fas fa-chevron-down" style="margin-left: auto; font-size: 0.8rem;"></i>
                    </div>
                    ${renderItem('<i class="fas fa-briefcase"></i> busniess area', escapeHtml(actualData.primaryname || actualData.primaryName || actualData.name || 'busniss'))}
                </div>
                <div class="view-section">
                    <div class="section-title">DEFINITION</div>
                    ${renderItem('Definition', _richHtml(actualData.description))}
                </div>
            `;

            // Right column (CLASSIFICATIONS)
            const right = `
                <div class="view-section">
                    <div class="section-title">CLASSIFICATIONS</div>
                    <div class="section-subtitle">BASIC CLASSIFICATIONS</div>
                    ${renderItem('BUDG Status', '<span class="status-indicator active"></span>Active')}
                    ${renderItem('Lifecycle', '<span class="lifecycle-badge">OPERATING</span>')}
                    ${renderItem('BUDG Viewing', 'Public')}
                    ${renderItem('Created', '01-Oct-2025')}
                    ${renderItem('Last Updated By', '<a href="#" class="user-link">John Admin</a>')}
                    ${renderItem('Last Updated', '01-Oct-2025')}
                </div>
            `;

            container.innerHTML = left + right;
            console.log('Business Area rendered successfully');
            
            // Add follow button
            if (window.addFollowButton) {
                await window.addFollowButton('business-area', id, null, '.form-actions');
            }

        } catch (e) {
            console.error('Error loading business area:', e);
            container.innerHTML = `<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">Failed to load business area (id=${id}). Error: ${e.message}</div>`;
        }
    }
})();


