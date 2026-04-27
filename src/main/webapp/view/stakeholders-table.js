//this is a comment

// Interactive Stakeholders Table Component
class StakeholdersTable {
    constructor(containerId, entityType, entityId) {
        this.containerId = containerId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.stakeholders = [];
        this.isEditMode = false;
        this.originalStakeholders = [];
        this.roles = [];
        this.people = [];
        this.statuses = [];
        this.rolePeopleByRoleId = {}; // cache: roleId -> people list from API/assignments
        this.userCanEdit = false; // Track if user has edit permissions

    }

    async checkUserPermissions() {
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (!meResp.ok) {
                console.log('API /api/me failed:', meResp.status);
                this.userCanEdit = false;
                this.hideEditButton();
                return;
            }
            const me = await meResp.json();
            console.log('User data from /api/me:', me);
            const role = (me.role || me.Role || me.userRole || '').toString().toLowerCase();
            console.log('Normalized role:', role);
            const isAdmin = role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            console.log('Is admin?', isAdmin);
            
            // Check role-based edit permission for this entity type
            let hasRoleEditPermission = false;
            if (!isAdmin) {
                try {
                    // Map entity type to module name
                    const moduleNameMap = {
                        'system': 'System',
                        'dataset': 'Data Sets',
                        'interface': 'Interface',
                        'glossary': 'Glossary',
                        'policy': 'Policy',
                        'process': 'Process',
                        'project': 'Project',
                        'product': 'Product',
                        'client': 'Client',
                        'legal': 'Legal Entity',
                        'legal-entity': 'Legal Entity',
                        'legalentity': 'Legal Entity',
                        'capability': 'Capability',
                        'business-area': 'Business Area',
                        'businessarea': 'Business Area',
                        'committee': 'Committee',
                        'regulation': 'Regulation',
                        'regulator': 'Regulator',
                        'geography': 'Geography',
                        'regulatory-theme': 'Regulatory Theme',
                        'regulatorytheme': 'Regulatory Theme'
                    };
                    const moduleName = moduleNameMap[this.entityType.toLowerCase()] || this.entityType;
                    
                    const permResp = await fetch(`/api/user/permissions/${encodeURIComponent(moduleName)}`, { 
                        method: 'GET', 
                        credentials: 'include' 
                    });
                    if (permResp.ok) {
                        const permData = await permResp.json();
                        if (permData.success) {
                            hasRoleEditPermission = permData.canEdit === true || permData.isAdmin === true;
                            console.log(`[StakeholdersTable] Role edit permission for ${moduleName}:`, hasRoleEditPermission);
                        }
                    }
                } catch (permError) {
                    console.warn('[StakeholdersTable] Error checking role permissions:', permError);
                }
            }
            
            // Check stakeholder status if user has role permission and entityId is available
            if (!isAdmin && hasRoleEditPermission && this.entityId) {
                try {
                    const moduleName = moduleNameMap[this.entityType.toLowerCase()] || this.entityType;
                    const stakeholderResp = await fetch(
                        `/api/check-stakeholder/${encodeURIComponent(moduleName)}/${this.entityId}`, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    
                    if (stakeholderResp.ok) {
                        const stakeholderData = await stakeholderResp.json();
                        if (!stakeholderData.isStakeholder) {
                            hasRoleEditPermission = false; // Deny if not stakeholder
                            console.warn('[StakeholdersTable] User is not stakeholder on object', this.entityId);
                        }
                    } else {
                        // If API fails, deny access for security
                        hasRoleEditPermission = false;
                        console.warn('[StakeholdersTable] Error checking stakeholder status');
                    }
                } catch (stakeholderError) {
                    console.warn('[StakeholdersTable] Error checking stakeholder:', stakeholderError);
                    hasRoleEditPermission = false; // Fail securely
                }
            }
            
            this.userCanEdit = isAdmin || hasRoleEditPermission;
            console.log('User can edit stakeholders:', this.userCanEdit, 'isAdmin:', isAdmin, 'hasRolePermission:', hasRoleEditPermission);

            // Show or hide edit button based on permissions
            if (this.userCanEdit) {
                this.showEditButton();
            } else {
                this.hideEditButton();
            }
        } catch (e) {
            console.log('Error checking user permissions:', e);
            this.userCanEdit = false;
            this.hideEditButton();
        }
    }

    async loadStakeholders() {
        try {
            // Check user permissions first
            await this.checkUserPermissions();

            // Load stakeholders data
            let stakeholders = [];
            switch (this.entityType) {
                case 'system':
                    stakeholders = await window.BUDG_API_SERVICE.getSystemStakeholders(this.entityId);
                    break;
                case 'dataset':
                    stakeholders = await window.BUDG_API_SERVICE.getDatasetStakeholders(this.entityId);
                    break;
                case 'interface':
                    stakeholders = await window.BUDG_API_SERVICE.getInterfaceStakeholders(this.entityId);
                    break;
                case 'glossary':
                    stakeholders = await window.BUDG_API_SERVICE.getGlossaryStakeholders(this.entityId);
                    break;
            }

            // Map the API response to our internal format
            this.stakeholders = (stakeholders || []).map(stakeholder => {
                return {
                    object_x_ipid: stakeholder.object_x_ipid || stakeholder.id || null,
                    roleId: stakeholder.roleId || stakeholder.role_id || stakeholder.roleID || null,
                    role: stakeholder.role || stakeholder.roleName || stakeholder.role_name || '',
                    peopleId: stakeholder.peopleId || stakeholder.people_id || stakeholder.ipid || null,
                    name: stakeholder.name || stakeholder.personName || stakeholder.person_name ||
                        (stakeholder.firstName && stakeholder.lastName ? `${stakeholder.firstName} ${stakeholder.lastName}` : '') ||
                        (stakeholder.First_Name && stakeholder.Last_Name ? `${stakeholder.First_Name} ${stakeholder.Last_Name}` : ''),
                    delegateOf: stakeholder.delegateOf || stakeholder.delegate_name || '',
                    delegateIpId: stakeholder.delegateIpId || stakeholder.delegateOfId || stakeholder.delegate_of_id || stakeholder.delegate_ipid || null,
                    roleAccepted: stakeholder.roleAccepted || stakeholder.accepted || stakeholder.AcceptedID || 'True',
                    statusId: stakeholder.statusId || stakeholder.status_id || stakeholder.statusID || null,
                    orgUnit: stakeholder.orgUnit || stakeholder.org_unit || stakeholder.orgUnitName || '',
                    dateAccepted: stakeholder.dateAccepted || stakeholder.date_accepted || stakeholder.DateAccepted || null
                };
            });

            // Store original data for cancel functionality
            this.originalStakeholders = JSON.parse(JSON.stringify(this.stakeholders));

            // Load lookup data for edit mode
            await this.loadLookupData();

            // Per-role person lists from role_assignment APIs (no global people merge)
            await this.prefetchRoleAssignmentCaches();

            this.render();

            // Re-check permissions after rendering to ensure edit button visibility
            await this.checkUserPermissions();
        } catch (error) {
            console.error('Failed to load stakeholders:', error);
            this.stakeholders = [];
            this.render();
        }
    }

    async loadLookupData() {
        try {
            this.rolePeopleByRoleId = {};
            // Load roles specific to the entity type first, fall back to generic resolver
            try {
                let rolesResponse;
                switch (this.entityType) {
                    case 'system':
                        rolesResponse = await window.BUDG_API_SERVICE.getSystemRoles(this.entityId);
                        break;
                    case 'dataset':
                        rolesResponse = await window.BUDG_API_SERVICE.getDatasetRoles(this.entityId);
                        break;
                    case 'interface':
                        rolesResponse = await window.BUDG_API_SERVICE.getInterfaceRoles(this.entityId);
                        break;
                    case 'glossary':
                        rolesResponse = await window.BUDG_API_SERVICE.getGlossaryRoles(this.entityId);
                        break;
                    default:
                        rolesResponse = await window.BUDG_API_SERVICE.getRolesForModule(this.entityType, this.entityId);
                        break;
                }
                const rawRoles = Array.isArray(rolesResponse) ? rolesResponse : (rolesResponse?.data || []);
                this.roles = rawRoles.map(role => ({
                    id: role.id ?? role.ID ?? role.roleId ?? role.RoleID ?? null,
                    primaryname: role.primaryname ?? role.name ?? role.roleName ?? role.PrimaryName ?? role.role ?? ''
                })).filter(role => role.id != null);
            } catch (error) {
                console.warn('Failed to load roles, using empty array:', error);
                this.roles = [];
            }

            // Person dropdowns use per-role caches only (see prefetchRoleAssignmentCaches / updatePeopleFromRole).
            this.people = [];

            // Load statuses
            try {
                let statusesResponse;
                switch (this.entityType) {
                    case 'system':
                        statusesResponse = await window.BUDG_API_SERVICE.getSystemStatuses(this.entityId);
                        break;
                    case 'dataset':
                        statusesResponse = await window.BUDG_API_SERVICE.getDatasetStatuses(this.entityId);
                        break;
                    case 'interface':
                        statusesResponse = await window.BUDG_API_SERVICE.getInterfaceStatuses(this.entityId);
                        break;
                    case 'glossary':
                        statusesResponse = await window.BUDG_API_SERVICE.getGlossaryStatuses(this.entityId);
                        break;
                    default:
                        statusesResponse = await window.BUDG_API_SERVICE.getStatusesForDropdown();
                        break;
                }
                const rawStatuses = Array.isArray(statusesResponse) ? statusesResponse : (statusesResponse?.data || []);
                this.statuses = rawStatuses.map(status => ({
                    id: status.id ?? status.ID ?? status.statusId ?? null,
                    name: status.primaryname ?? status.name ?? status.PrimaryName ?? ''
                })).filter(status => status.id != null);
                if (this.statuses.length === 0) throw new Error('No statuses returned');
            } catch (error) {
                console.warn('Failed to load statuses, using default:', error);
                this.statuses = [
                    { id: 1, name: 'Active' },
                    { id: 0, name: 'Inactive' }
                ];
            }

            // Lookup data loaded
        } catch (error) {
            console.error('Failed to load lookup data:', error);
            this.roles = [];
            this.people = [];
            this.statuses = [
                { id: 1, name: 'Active' },
                { id: 0, name: 'Inactive' }
            ];

            // Show user-friendly error message
            this.showMessage('Failed to load data for editing. Some features may not work properly.', 'error');
        }
    }

    /** Populate rolePeopleByRoleId from role-assignment APIs for all roles present on loaded stakeholders. */
    async prefetchRoleAssignmentCaches() {
        const roleIds = [...new Set(
            this.stakeholders.map(s => s.roleId).filter(id => id != null && String(id).trim() !== '')
        )];
        await Promise.all(roleIds.map(async (rid) => {
            try {
                this.rolePeopleByRoleId[rid] = await this.getUsersByRoleFromAPI(rid);
            } catch (e) {
                console.warn('[StakeholdersTable] prefetch role people failed for role', rid, e);
                this.rolePeopleByRoleId[rid] = [];
            }
        }));
    }

    async getStakeholdersData() {
        try {
            let stakeholders = [];
            switch (this.entityType) {
                case 'system':
                    stakeholders = await window.BUDG_API_SERVICE.getSystemStakeholders(this.entityId);
                    break;
                case 'dataset':
                    stakeholders = await window.BUDG_API_SERVICE.getDatasetStakeholders(this.entityId);
                    break;
                case 'interface':
                    stakeholders = await window.BUDG_API_SERVICE.getInterfaceStakeholders(this.entityId);
                    break;
                case 'glossary':
                    stakeholders = await window.BUDG_API_SERVICE.getGlossaryStakeholders(this.entityId);
                    break;
            }
            return stakeholders;
        } catch (error) {
            console.warn('Failed to get stakeholders data:', error);
            return [];
        }
    }

    render() {
        const container = document.getElementById(this.containerId);
        if (!container) return;

        // Helper for translation
        const t = (key, defaultVal) => {
            if (window.I18n && typeof window.I18n.t === 'function') {
                const tr = window.I18n.t(key);
                if (tr !== key) return tr;
            }
            return defaultVal;
        };

        const editingAllowed = ['dataset', 'system', 'glossary', 'interface'].includes(this.entityType);
        const canEdit = editingAllowed && this.userCanEdit;
        container.innerHTML = `
            <div class="stakeholders-table-container">
                <div class="stakeholders-header">
                    <div class="section-title">
                        <span>${t('stakeholder.title', 'Direct Stakeholders')}</span>
                    </div>
                    ${this.isEditMode || !canEdit ? '' : `<button type="button" class="btn btn-primary" id="editStakeholdersBtn"><i class="fas fa-edit"></i> ${t('button.edit', 'Edit')}</button>`}
                </div>
                ${!this.isEditMode ? `
                <div class="stakeholders-hint" style="margin: 0.5rem 0 0.75rem 0; color: var(--text-muted,#6b7280); font-size: 0.9rem; display:flex; gap:0.5rem; align-items:flex-start;">
                    <i class="fas fa-circle-info" style="margin-top: 0.2rem; color: var(--primary-500,#248567);"></i>
                    <div>
                        <div>${t('stakeholder.hint', 'To assign a delegate, select the Delegate Of field and add a user from the list of stakeholders. To assign a user as a delegate to an object, the user must be in the list of stakeholders.')}</div>
                    </div>
                </div>
                ` : ''}
                <div class="data-table-wrapper">
                    <table class="data-table stakeholders-table">
                        <thead>
                            <tr>
                                <th><div class="th-content"><span>${t('stakeholder.role', 'Role')}</span></div></th>
                                <th><div class="th-content"><span>${t('stakeholder.name', 'Name')}</span></div></th>
                                ${!this.isEditMode ? `<th><div class="th-content"><span>${t('stakeholder.orgUnit', 'Org Unit')}</span></div></th>` : ''}
                                <th><div class="th-content"><span>${t('stakeholder.roleStatus', 'Role Status')}</span></div></th>
                                <th><div class="th-content"><span>${t('stakeholder.delegateOf', 'Delegate Of')}</span></div></th>
                                ${!this.isEditMode ? `<th><div class="th-content"><span>${t('stakeholder.dateAccepted', 'Date Accepted')}</span></div></th>` : ''}
                                ${this.isEditMode ? `<th><div class="th-content"><span>${t('label.action', 'Action')}</span></div></th>` : ''}
                            </tr>
                        </thead>
                        <tbody id="stakeholdersTableBody">
                            ${this.renderTableBody(t)}
                        </tbody>
                    </table>
                </div>
                ${this.isEditMode && canEdit ? this.renderEditControls(t) : ''}
            </div>
        `;

        this.attachEventListeners();
    }

    showEditButton() {
        const editBtn = document.getElementById('editStakeholdersBtn');
        if (editBtn) {
            editBtn.style.display = '';
            editBtn.style.setProperty('display', '', 'important');
            console.log('Edit stakeholders button shown for admin user');
        }
    }

    hideEditButton() {
        const editBtn = document.getElementById('editStakeholdersBtn');
        if (editBtn) {
            editBtn.style.display = 'none';
            editBtn.style.setProperty('display', 'none', 'important');
            console.log('Edit stakeholders button hidden for non-admin user');
        }
    }

    renderTableBody(t) {
        t = t || ((k, v) => v);
        const colspan = this.isEditMode ? 5 : 6;
        if (this.stakeholders.length === 0) {
            return `<tr><td colspan="${colspan}" style="color:var(--text-muted,#9ca3af);padding:1rem;">${t('stakeholder.noStakeholders', 'No stakeholders')}</td></tr>`;
        }

        let html = '';
        this.stakeholders.forEach((stakeholder, index) => {
            html += this.renderStakeholderRow(stakeholder, index, t);
        });
        return html;
    }

    renderStakeholderRow(stakeholder, index, t) {
        if (this.isEditMode) {
            return this.renderEditModeRow(stakeholder, index, t); // Pass t if needed
        } else {
            return this.renderViewModeRow(stakeholder, index, t); // Pass t if needed
        }
    }

    renderViewModeRow(stakeholder, index, t) {
        t = t || ((k, v) => v);
        const role = stakeholder.role || 'N/A';
        const name = stakeholder.name || 'N/A';
        const orgUnit = stakeholder.orgUnit || 'N/A';
        // Prefer status via statusId -> statuses lookup; fallback to roleAccepted
        const resolvedStatusById = (() => {
            const rawId = stakeholder.statusId || stakeholder.status_id || stakeholder.statusID;
            if (rawId == null || rawId === '') return null;
            const match = Array.isArray(this.statuses)
                ? this.statuses.find(s => String(s.id) === String(rawId))
                : null;
            if (!match) return null;
            return match.name || match.primaryname || null;
        })();
        let status = resolvedStatusById || stakeholder.statusName || (
            stakeholder.roleAccepted === 'True' ? 'Active' :
                (stakeholder.roleAccepted === 'False' ? 'Inactive' : (stakeholder.roleAccepted || 'Active'))
        );

        // Translate status value if it's one of the common ones
        if (status) {
            const statusKey = 'value.' + String(status).toLowerCase();
            status = t(statusKey, status);
        }

        // Get delegate name - check multiple possible field names
        const delegateOf = stakeholder.delegateOf || stakeholder.delegate_name || stakeholder.delegate_of || '-';
        const dateAccepted = stakeholder.dateAccepted ? new Date(stakeholder.dateAccepted).toLocaleDateString() : 'N/A';

        return `
            <tr data-index="${index}">
                <td><span class="view-value">${role}</span></td>
                <td><span class="view-value">${name}</span></td>
                <td><span class="view-value">${orgUnit}</span></td>
                <td><span class="view-value">${status}</span></td>
                <td><span class="view-value">${delegateOf}</span></td>
                <td><span class="view-value">${dateAccepted}</span></td>
            </tr>
        `;
    }

    renderEditModeRow(stakeholder, index, t) {
        t = t || ((k, v) => v);
        // Ensure arrays exist and are valid
        const roles = Array.isArray(this.roles) ? this.roles : [];
        const people = Array.isArray(this.people) ? this.people : [];
        const statuses = Array.isArray(this.statuses) ? this.statuses : [];

        // Render edit mode row

        const roleOptions = roles.map(role => {
            const isSelected = stakeholder.roleId == role.id || stakeholder.role === (role.primaryname || role.name);
            return `<option value="${role.id}" ${isSelected ? 'selected' : ''}>${role.primaryname || role.name}</option>`;
        }).join('');

        // Build people list bound to the selected role only.
        // Prefer cached users for this role if available; otherwise render only currently selected person
        let allPeople = Array.isArray(this.rolePeopleByRoleId[stakeholder.roleId]) ? [...this.rolePeopleByRoleId[stakeholder.roleId]] : [];
        if (allPeople.length === 0 && stakeholder.peopleId && stakeholder.name) {
            allPeople = [{
                id: stakeholder.peopleId,
                First_Name: stakeholder.name.split(' ')[0] || '',
                Last_Name: stakeholder.name.split(' ').slice(1).join(' ') || '',
                name: stakeholder.name
            }];
        }

        const peopleOptions = allPeople.map(person => {
            const personName = this.getPersonDisplayName(person);
            const isSelected = stakeholder.peopleId == person.id || stakeholder.name === personName;
            return `<option value="${person.id}" ${isSelected ? 'selected' : ''}>${personName || 'Unknown'}</option>`;
        }).join('');

        const statusOptions = statuses.map(status => {
            const statusName = status.name || status.primaryname;
            const isSelected = stakeholder.statusId == status.id ||
                stakeholder.roleAccepted === statusName ||
                (stakeholder.roleAccepted === 'True' && statusName === 'Active') ||
                (stakeholder.roleAccepted === 'False' && statusName === 'Inactive');
            return `<option value="${status.id}" ${isSelected ? 'selected' : ''}>${statusName}</option>`;
        }).join('');

        // Build delegate options - only show stakeholders already in the current facet
        const availableDelegates = this.getAvailableDelegates(index);
        const delegateOptions = availableDelegates.map(delegate => {
            const isSelected = stakeholder.delegateIpId == delegate.value;
            return `<option value="${delegate.value}" ${isSelected ? 'selected' : ''}>${delegate.label}</option>`;
        }).join('');

        return `
            <tr data-index="${index}">
                <td>
                    <div class="select-with-search">
                        <select class="form-control" data-field="roleId" data-index="${index}">
                            <option value="">${t('placeholder.selectRole', 'Select Role')}</option>
                            ${roleOptions}
                        </select>
                    </div>
                </td>
                <td>
                    <div class="select-with-search">
                        <select class="form-control searchable-select" data-field="peopleId" data-index="${index}">
                            <option value="">${t('placeholder.selectPerson', 'Select Person')}</option>
                            ${peopleOptions}
                        </select>
                    </div>
                </td>
                <td>
                    <select class="form-control" data-field="statusId" data-index="${index}">
                        <option value="">${t('placeholder.selectStatus', 'Select Status')}</option>
                        ${statusOptions}
                    </select>
                </td>
                <td>
                    <select class="form-control" data-field="delegateIpId" data-index="${index}">
                        <option value="">${t('placeholder.selectDelegate', 'Select Delegate')}</option>
                        ${delegateOptions}
                    </select>
                </td>
                <td>
                    <button type="button" class="btn btn-sm btn-danger" data-action="remove" data-index="${index}">
                        <i class="fas fa-minus"></i>
                    </button>
                </td>
            </tr>
        `;
    }

    renderEditControls(t) {
        t = t || ((k, v) => v);
        return `
            <div class="stakeholders-edit-controls">
                <div class="edit-actions">
                    <button type="button" class="btn btn-success" id="addStakeholderBtn">
                        <i class="fas fa-plus"></i> ${t('stakeholder.add', 'Add Stakeholder')}
                    </button>
                </div>
                <div class="save-actions">
                    <button type="button" class="btn btn-primary" id="saveStakeholdersBtn">
                        <i class="fas fa-save"></i> ${t('button.save', 'Save')}
                    </button>
                    <button type="button" class="btn btn-secondary" id="cancelEditBtn">
                        <i class="fas fa-times"></i> ${t('button.cancel', 'Cancel')}
                    </button>
                </div>
            </div>
        `;
    }

    attachEventListeners() {
        if (this.isEditMode) {
            this.attachEditModeListeners();
        } else {
            this.attachViewModeListeners();
        }
    }

    attachViewModeListeners() {
        const editBtn = document.getElementById('editStakeholdersBtn');
        if (editBtn) {
            editBtn.addEventListener('click', async () => {
                // Show loading state
                const originalText = editBtn.innerHTML;
                editBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';
                editBtn.disabled = true;

                try {
                    await this.enterEditMode();
                } finally {
                    // Restore button state if edit mode wasn't entered
                    if (!this.isEditMode) {
                        editBtn.innerHTML = originalText;
                        editBtn.disabled = false;
                    }
                }
            });
        }
    }

    attachEditModeListeners() {
        // Add stakeholder button
        const addBtn = document.getElementById('addStakeholderBtn');
        if (addBtn) {
            addBtn.addEventListener('click', () => this.addNewStakeholder());
        }

        // Save button
        const saveBtn = document.getElementById('saveStakeholdersBtn');
        if (saveBtn) {
            saveBtn.addEventListener('click', () => this.saveStakeholders());
        }

        // Cancel button
        const cancelBtn = document.getElementById('cancelEditBtn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => this.cancelEdit());
        }

        // Remove buttons
        const removeBtns = document.querySelectorAll('[data-action="remove"]');
        removeBtns.forEach(btn => {
            btn.addEventListener('click', (e) => {
                const index = parseInt(e.target.closest('button').dataset.index);
                this.removeStakeholder(index);
            });
        });

        // Field change listeners
        const selects = document.querySelectorAll('select[data-field]');
        selects.forEach(select => {
            select.addEventListener('change', async (e) => {
                const field = e.target.dataset.field;
                const index = parseInt(e.target.dataset.index);
                const value = e.target.value;

                // Update the stakeholder field
                this.updateStakeholderField(index, field, value);

                // Special handling for role selection
                if (field === 'roleId') {
                    // Show loading state
                    const peopleSelect = document.querySelector(`select[data-field="peopleId"][data-index="${index}"]`);
                    if (peopleSelect) {
                        const originalText = peopleSelect.innerHTML;
                        peopleSelect.innerHTML = '<option value="">Loading people...</option>';
                        peopleSelect.disabled = true;

                        try {
                            await this.updatePeopleFromRole(index, value);
                        } finally {
                            peopleSelect.disabled = false;
                        }
                    }
                }

                // Special handling for delegate selection - update all delegate dropdowns when stakeholders change
                if (field === 'delegateIpId' || field === 'peopleId' || field === 'roleId') {
                    // Refresh all delegate dropdowns to reflect current stakeholder list
                    this.refreshAllDelegateDropdowns();
                }
            });
        });

        // Auto-fetch org unit when people is selected
        const peopleSelects = document.querySelectorAll('select[data-field="peopleId"]');
        peopleSelects.forEach(select => {
            select.addEventListener('change', (e) => {
                const index = parseInt(e.target.dataset.index);
                const peopleId = e.target.value;
                if (peopleId) {
                    this.updateOrgUnitFromPeople(index, peopleId);
                }
            });
        });

        // Removed search inputs; dropdowns remain without client-side filtering
    }

    // Mode switching methods
    async enterEditMode() {
        // Allow edit mode for all entity types, including dataset
        // Ensure lookup data is loaded before entering edit mode
        if (!Array.isArray(this.roles) || !Array.isArray(this.people) || !Array.isArray(this.statuses)) {
            try {
                await this.loadLookupData();
            } catch (error) {
                console.error('Failed to load lookup data for edit mode:', error);
                this.showMessage('Failed to load data for editing. Please try again.', 'error');
                return;
            }
        }

        this.isEditMode = true;
        this.render();

        // After initial render, for each row with a role, load its assigned users and apply to the dropdown
        const tasks = [];
        this.stakeholders.forEach((s, idx) => {
            if (s && s.roleId) {
                tasks.push(this.updatePeopleFromRole(idx, s.roleId));
            } else {
                // ensure people dropdown is empty until role chosen
                this.updatePeopleDropdown(idx, []);
            }
        });
        try { await Promise.all(tasks); } catch (e) { /* ignore per-row errors */ }
        
        // Refresh delegate dropdowns after entering edit mode
        this.refreshAllDelegateDropdowns();
    }

    cancelEdit() {
        this.isEditMode = false;
        this.stakeholders = JSON.parse(JSON.stringify(this.originalStakeholders));
        this.render();
    }

    // CRUD operations
    addNewStakeholder() {
        // Find Active status ID
        const activeStatus = this.statuses.find(s => 
            (s.name || s.primaryname || '').toLowerCase() === 'active'
        );

        const newStakeholder = {
            object_x_ipid: null,
            roleId: null,
            role: '',
            peopleId: null,
            name: '',
            delegateOf: '',
            delegateIpId: null,
            roleAccepted: 'True',
            statusId: activeStatus ? activeStatus.id : null,  // Set default to Active
            orgUnit: '',
            dateAccepted: null
        };

        this.stakeholders.push(newStakeholder);
        this.render();
        
        // Refresh delegate dropdowns after adding new stakeholder
        // (Note: new stakeholders won't appear in delegate dropdowns until they're saved and have object_x_ipid)
        this.refreshAllDelegateDropdowns();
    }

    refreshAllDelegateDropdowns() {
        // Update all delegate dropdowns to reflect current stakeholder list
        if (!this.isEditMode) return;
        
        this.stakeholders.forEach((stakeholder, index) => {
            const delegateSelect = document.querySelector(`select[data-field="delegateIpId"][data-index="${index}"]`);
            if (delegateSelect) {
                const currentValue = delegateSelect.value;
                const availableDelegates = this.getAvailableDelegates(index);
                
                // Rebuild options
                delegateSelect.innerHTML = `<option value="">${window.I18n?.t('placeholder.selectDelegate') || 'Select Delegate'}</option>`;
                availableDelegates.forEach(delegate => {
                    const option = document.createElement('option');
                    option.value = delegate.value;
                    option.textContent = delegate.label;
                    if (currentValue == delegate.value) {
                        option.selected = true;
                    }
                    delegateSelect.appendChild(option);
                });
                
                // If current value is no longer available, clear it
                if (currentValue && !availableDelegates.find(d => d.value == currentValue)) {
                    delegateSelect.value = '';
                    this.stakeholders[index].delegateIpId = null;
                    this.stakeholders[index].delegateOf = '';
                }
            }
        });
    }

    removeStakeholder(index) {
        if (index >= 0 && index < this.stakeholders.length) {
            // Get the object_x_ipid of the stakeholder being removed
            const removedStakeholder = this.stakeholders[index];
            const removedId = removedStakeholder?.object_x_ipid;
            
            this.stakeholders.splice(index, 1);
            
            // If removed stakeholder was selected as a delegate, clear those references
            if (removedId) {
                this.stakeholders.forEach(s => {
                    if (s.delegateIpId == removedId) {
                        s.delegateIpId = null;
                        s.delegateOf = '';
                    }
                });
            }
            
            this.render();
        }
    }

    updateStakeholderField(index, field, value) {
        if (index >= 0 && index < this.stakeholders.length) {
            this.stakeholders[index][field] = value;

            // Update related fields
            if (field === 'roleId') {
                const role = this.roles.find(r => r.id == value);
                this.stakeholders[index].role = role ? (role.primaryname || role.name) : '';
            } else if (field === 'peopleId') {
                const rid = this.stakeholders[index].roleId;
                const rolePeople = Array.isArray(this.rolePeopleByRoleId[rid]) ? this.rolePeopleByRoleId[rid] : [];
                const person = rolePeople.find(p => String(p.id) === String(value))
                    || this.people.find(p => String(p.id) === String(value));
                this.stakeholders[index].name = person ? this.getPersonDisplayName(person) : '';
            } else if (field === 'statusId') {
                const status = this.statuses.find(s => s.id == value);
                this.stakeholders[index].roleAccepted = status ? (status.primaryname || status.name) : 'True';
            } else if (field === 'delegateIpId') {
                // Update delegateIpId and find the delegate name for display
                this.stakeholders[index].delegateIpId = value ? parseInt(value) : null;
                if (value) {
                    const delegate = this.stakeholders.find(s => s.object_x_ipid == value);
                    this.stakeholders[index].delegateOf = delegate ? `${delegate.name} - ${delegate.role}` : '';
                } else {
                    this.stakeholders[index].delegateOf = '';
                }
            }
        }
    }

    async updateOrgUnitFromPeople(index, peopleId) {
        try {
            const rid = this.stakeholders[index]?.roleId;
            const rolePeople = rid != null ? (this.rolePeopleByRoleId[rid] || []) : [];
            const person = rolePeople.find(p => String(p.id) === String(peopleId))
                || this.people.find(p => String(p.id) === String(peopleId));
            if (person && person.Org_Unit_ID) {
                // Fetch org unit name from the person's org unit ID
                const orgUnit = await window.BUDG_API_SERVICE.getOrgUnitById(person.Org_Unit_ID);
                if (orgUnit) {
                    this.stakeholders[index].orgUnit = orgUnit.Name || orgUnit.name || '';
                    // Update the display in the table
                    const row = document.querySelector(`tr[data-index="${index}"]`);
                    if (row) {
                        const orgUnitCell = row.querySelector('.readonly-field');
                        if (orgUnitCell) {
                            orgUnitCell.textContent = this.stakeholders[index].orgUnit;
                        }
                    }
                }
            }
        } catch (error) {
            console.error('Failed to fetch org unit:', error);
        }
    }

    async updatePeopleFromRole(index, roleId) {
        try {
            if (!roleId) {
                // Clear people dropdown if no role selected
                this.updatePeopleDropdown(index, []);
                return;
            }

            console.log('Updating people for role ID:', roleId);

            // Try to get users from role_assignment table using the API
            let assignedPeople = [];
            try {
                assignedPeople = await this.getUsersByRoleFromAPI(roleId);
                console.log('Got users from role_assignment API:', assignedPeople);
            } catch (error) {
                console.warn('Failed to get users from role_assignment API:', error);
            }

            console.log('Final assigned people for role:', assignedPeople);
            // Cache per role id to use during re-rendering
            this.rolePeopleByRoleId[roleId] = assignedPeople;
            this.updatePeopleDropdown(index, assignedPeople);

        } catch (error) {
            console.error('Failed to fetch people from role:', error);
            // Fallback to showing all people
            this.updatePeopleDropdown(index, []);
        }
    }

    getPersonDisplayName(person) {
        if (!person) return 'Unknown';

        // Try multiple ways to get the person's name
        if (person.First_Name && person.Last_Name) {
            return `${person.First_Name} ${person.Last_Name}`;
        } else if (person.firstName && person.lastName) {
            return `${person.firstName} ${person.lastName}`;
        } else if (person.name) {
            return person.name;
        } else if (person.fullName) {
            return person.fullName;
        } else if (person.displayName) {
            return person.displayName;
        } else if (person.personName) {
            return person.personName;
        } else if (person.person_name) {
            return person.person_name;
        } else {
            // Try constructing from any available fields
            const first = person.First_Name || person.firstName || person.FirstName || person.first_name;
            const last = person.Last_Name || person.lastName || person.LastName || person.last_name;
            const combined = [first, last].filter(Boolean).join(' ').trim();
            return combined || 'Unknown';
        }
    }

    getAvailableDelegates(currentIndex) {
        // Return stakeholders that can be selected as delegates: same entity and same role only
        const current = this.stakeholders[currentIndex];
        if (!current) return [];
        const currentRoleId = current.roleId != null && current.roleId !== '' ? String(current.roleId) : null;
        if (currentRoleId == null) return [];

        return this.stakeholders
            .filter((s, idx) => {
                if (idx === currentIndex) return false;
                if (!s.object_x_ipid) return false;
                if (!s.name || !s.role) return false;
                const sRoleId = s.roleId != null && s.roleId !== '' ? String(s.roleId) : null;
                if (sRoleId !== currentRoleId) return false;
                return true;
            })
            .map(s => ({
                value: s.object_x_ipid,
                label: `${s.name} - ${s.role}`
            }));
    }

    updatePeopleDropdown(index, peopleList) {
        const peopleSelect = document.querySelector(`select[data-field="peopleId"][data-index="${index}"]`);
        if (!peopleSelect) return;

        // Store current selection
        const currentValue = peopleSelect.value;
        const stakeholder = this.stakeholders[index];

        // Create a combined list that includes existing stakeholder person if not in people list
        const allPeople = [...peopleList];

        // If stakeholder has a peopleId but the person is not in the people list, add them
        if (stakeholder && stakeholder.peopleId && stakeholder.name && !peopleList.find(p => p.id == stakeholder.peopleId)) {
            allPeople.push({
                id: stakeholder.peopleId,
                First_Name: stakeholder.name.split(' ')[0] || '',
                Last_Name: stakeholder.name.split(' ').slice(1).join(' ') || '',
                name: stakeholder.name
            });
        }

        // Clear existing options except the first one
        peopleSelect.innerHTML = '<option value="">Select Person</option>';

        // Add new options - allow all names (even placeholders) so user sees options
        allPeople.forEach(person => {
            const personName = this.getPersonDisplayName(person) || 'Unknown';
            const option = document.createElement('option');
            option.value = person.id;
            option.textContent = personName;

            // Restore selection if it's still valid
            if (currentValue == person.id) {
                option.selected = true;
            }

            peopleSelect.appendChild(option);
        });

        // If current selection is not in the new list, clear it
        if (currentValue && !allPeople.find(p => p.id == currentValue)) {
            peopleSelect.value = '';
            this.updateStakeholderField(index, 'peopleId', '');
        }
    }

    async getUsersByRoleFromAPI(roleId) {
        try {
            let users = [];
            switch (this.entityType) {
                case 'system':
                    users = await window.BUDG_API_SERVICE.getSystemUsersByRole(this.entityId, roleId);
                    break;
                case 'dataset':
                    users = await window.BUDG_API_SERVICE.getDatasetUsersByRole(this.entityId, roleId);
                    break;
                case 'interface':
                    users = await window.BUDG_API_SERVICE.getInterfaceUsersByRole(this.entityId, roleId);
                    break;
                case 'glossary':
                    users = await window.BUDG_API_SERVICE.getGlossaryUsersByRole(this.entityId, roleId);
                    break;
            }

            // Convert API response to our internal format and filter out people without real names
            return (users || []).map(user => ({
                id: user.id,
                First_Name: user.name ? user.name.split(' ')[0] : '',
                Last_Name: user.name ? user.name.split(' ').slice(1).join(' ') : '',
                name: user.name || '',
                Email: user.email || ''
            })).filter(user => {
                // Only include users with real names (not empty or "Person X")
                const fullName = user.name || `${user.First_Name} ${user.Last_Name}`.trim();
                return fullName && !fullName.startsWith('Person ') && fullName !== 'Unknown';
            });
        } catch (error) {
            console.error('Failed to get users by role from API:', error);
            return [];
        }
    }

    async saveStakeholders() {
        try {
            // Drop fully empty rows (no role and no person)
            const filteredRows = this.stakeholders.filter(s => {
                const hasRole = s.roleId && String(s.roleId).trim() !== '';
                const hasPerson = s.peopleId && String(s.peopleId).trim() !== '';
                return hasRole || hasPerson;
            });

            // Validate every remaining row requires both Role and Person
            let invalidIndex = -1;
            for (let i = 0; i < filteredRows.length; i++) {
                const s = filteredRows[i];
                const hasRole = s.roleId && String(s.roleId).trim() !== '';
                const hasPerson = s.peopleId && String(s.peopleId).trim() !== '';
                if (!hasRole || !hasPerson) { invalidIndex = i; break; }
            }
            if (invalidIndex !== -1) {
                this.showMessage(`Row ${invalidIndex + 1}: Please select Role then Person.`, 'error');
                return;
            }

            // Uniqueness validation: prevent duplicate (roleId, peopleId) combinations
            // 1) Check within the current table state (filteredRows)
            const pairSeen = new Set();
            for (let i = 0; i < filteredRows.length; i++) {
                const s = filteredRows[i];
                const key = `${parseInt(s.roleId)}|${parseInt(s.peopleId)}`;
                if (pairSeen.has(key)) {
                    this.showMessage(`Row ${i + 1}: This person already has the selected role.`, 'error');
                    return;
                }
                pairSeen.add(key);
            }

            // 2) If new row (no object_x_ipid), ensure not already exists in originalStakeholders
            for (let i = 0; i < filteredRows.length; i++) {
                const s = filteredRows[i];
                if (!s.object_x_ipid) {
                    const exists = this.originalStakeholders.some(o => parseInt(o.roleId) === parseInt(s.roleId) && parseInt(o.peopleId || o.ipid) === parseInt(s.peopleId));
                    if (exists) {
                        this.showMessage(`Row ${i + 1}: This person already has this role (duplicate).`, 'error');
                        return;
                    }
                }
            }

            // Build inserts/updates/deletes payload
            const payload = { inserts: [], updates: [], deletes: [] };

            // Current rows â†’ inserts/updates
            filteredRows.forEach(s => {
                const base = {
                    roleId: s.roleId ? parseInt(s.roleId) : null,
                    ipid: s.peopleId ? parseInt(s.peopleId) : null,
                    delegateIpId: s.delegateIpId ? parseInt(s.delegateIpId) : null,
                    accepted: (String(s.roleAccepted).toLowerCase() === 'true' || String(s.roleAccepted).toLowerCase() === 'active') ? 1 : 0,
                    statusId: s.statusId != null && String(s.statusId) !== '' ? parseInt(s.statusId) : null
                };

                // Backend validation requires role + ipid
                if (!base.roleId || !base.ipid) return;

                const original = this.originalStakeholders.find(o => o.object_x_ipid === s.object_x_ipid);
                if (!original || !s.object_x_ipid) {
                    payload.inserts.push(base);
                } else {
                    const changed = (
                        parseInt(original.roleId || 0) !== base.roleId ||
                        parseInt(original.peopleId || original.ipid || 0) !== base.ipid ||
                        (original.statusId != null ? parseInt(original.statusId) : null) !== base.statusId ||
                        (String(original.roleAccepted).toLowerCase() === 'true' || String(original.roleAccepted).toLowerCase() === 'active' ? 1 : 0) !== base.accepted ||
                        (original.delegateIpId ? parseInt(original.delegateIpId) : null) !== base.delegateIpId
                    );
                    if (changed) {
                        payload.updates.push({ objectXPeopleId: s.object_x_ipid, ...base });
                    }
                }
            });

            // Deleted rows
            this.originalStakeholders.forEach(o => {
                const still = filteredRows.find(s => s.object_x_ipid === o.object_x_ipid);
                if (!still && o.object_x_ipid) payload.deletes.push({ objectXPeopleId: o.object_x_ipid });
            });

            // If nothing to save
            if (payload.inserts.length === 0 && payload.updates.length === 0 && payload.deletes.length === 0) {
                this.showMessage('No changes to save.', 'info');
                return;
            }

            // Send to API per module
            const sendPayload = payload;
            let result;
            switch (this.entityType) {
                case 'system':
                    result = await window.BUDG_API_SERVICE.saveSystemStakeholders(this.entityId, sendPayload);
                    break;
                case 'dataset':
                    result = await window.BUDG_API_SERVICE.saveDatasetStakeholders(this.entityId, sendPayload);
                    break;
                case 'interface':
                    result = await window.BUDG_API_SERVICE.saveInterfaceStakeholders(this.entityId, sendPayload);
                    break;
                case 'glossary':
                    result = await window.BUDG_API_SERVICE.saveGlossaryStakeholders(this.entityId, sendPayload);
                    break;
            }

            if (result && result.success) {
                // Reload stakeholders data from server
                await this.loadStakeholders();
                this.isEditMode = false;
                this.render();

                // Show success message
                this.showMessage('Stakeholders saved successfully!', 'success');
            } else {
                throw new Error(result?.error || result?.errorMessage || 'Save failed');
            }
        } catch (error) {
            console.error('Failed to save stakeholders:', error);
            const serverMsg = (error && error.body && (error.body.error || error.body.message)) || error.message || 'Unknown error';
            this.showMessage(`Failed to save stakeholders: ${serverMsg}`, 'error');
        }
    }

    showMessage(message, type = 'info') {
        // Create a simple toast notification
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        toast.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 12px 20px;
            border-radius: 4px;
            color: white;
            font-weight: 500;
            z-index: 1000;
            background-color: ${type === 'success' ? '#248567' : type === 'error' ? '#ef4444' : '#248567'};
        `;

        document.body.appendChild(toast);

        setTimeout(() => {
            document.body.removeChild(toast);
        }, 3000);
    }
}

// Export for use in other files
window.StakeholdersTable = StakeholdersTable;


