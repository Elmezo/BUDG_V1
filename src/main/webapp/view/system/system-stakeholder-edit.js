
// System Stakeholder Edit JavaScript - New Implementation
(function() {
    let currentSystemId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let stakeholdersData = [];
    let originalData = [];
    let rolesData = [];
    let roleStatusesData = [];
    let peopleData = [];
    let currentUserId = null; // Current logged-in user's people ID
    let canEditStakeholders = false; // Whether stakeholder editing is allowed based on workflow
    let isAdminUser = false;
    let hasRoleEditPermission = false;

    function msg(key, opts, fallback) {
        try {
            return (window.I18n && window.I18n.t(key, opts)) || fallback || '';
        } catch (e) {
            return fallback || '';
        }
    }

    // Initialize stakeholder edit
    async function initStakeholderEdit(systemId, viewMode = 'original') {
        currentSystemId = systemId;
        currentViewMode = viewMode || 'original';
        console.log('Initializing stakeholder edit for system:', systemId);
        await fetchCurrentUser();
        await checkUserRolePermission();
        await checkStakeholderEditPermission();
        loadStakeholdersForEdit();
    }
    
    // Fetch current user ID from /api/me
    async function fetchCurrentUser() {
        try {
            const response = await fetch('/api/me', { method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' } });
            if (response.ok) {
                const userData = await response.json();
                currentUserId = userData.id;
                const roleName = (userData.roleName || userData.role || '').toLowerCase();
                isAdminUser = roleName.includes('admin') || roleName === 'administrator' || roleName.includes('super');
                console.log('[SystemStakeholderEdit] Current user ID:', currentUserId, 'isAdmin:', isAdminUser);
            }
        } catch (error) { console.error('[SystemStakeholderEdit] Error fetching current user:', error); }
    }
    
    // Check if user has edit permission via role
    async function checkUserRolePermission() {
        try {
            const response = await fetch('/api/user/permissions/System', { method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' } });
            if (response.ok) {
                const perms = await response.json();
                hasRoleEditPermission = perms.success && (perms.canEdit === true || perms.isAdmin === true);
                console.log('[SystemStakeholderEdit] Role edit permission:', hasRoleEditPermission);
            }
        } catch (error) { hasRoleEditPermission = false; }
    }
    
    // Check if stakeholder editing is allowed based on workflow status
    async function checkStakeholderEditPermission() {
        const hasBasicEditPermission = isAdminUser || hasRoleEditPermission;
        
        console.log('[SystemStakeholderEdit] Permission check:', {
            isAdminUser: isAdminUser,
            hasRoleEditPermission: hasRoleEditPermission,
            hasBasicEditPermission: hasBasicEditPermission
        });
        
        if (!hasBasicEditPermission) {
            canEditStakeholders = false;
            console.log('[SystemStakeholderEdit] No basic edit permission');
            return;
        }
        
        try {
            const response = await fetch(`/api/pending-changes/stakeholder-edit-permission/System/${currentSystemId}`, {
                method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' }
            });
            if (response.ok) {
                const data = await response.json();
                const workflowAllowsEdit = data.canEdit === true;
                canEditStakeholders = hasBasicEditPermission && workflowAllowsEdit;
                console.log('[SystemStakeholderEdit] Final permission:', {
                    workflowAllowsEdit: workflowAllowsEdit,
                    canEditStakeholders: canEditStakeholders,
                    reason: data.reason
                });
            } else {
                canEditStakeholders = hasBasicEditPermission;
                console.log('[SystemStakeholderEdit] Workflow API failed, using basic permission:', canEditStakeholders);
            }
        } catch (error) {
            console.error('[SystemStakeholderEdit] Error checking workflow permission:', error);
            canEditStakeholders = hasBasicEditPermission;
        }
    }

    // Load stakeholders data for editing
    async function loadStakeholdersForEdit() {
        try {
            const container = document.getElementById('systemStakeholdersContainer');
            if (!container) return;

            container.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> Loading stakeholders for editing...</div>';

            // Load dropdown data first (without loading all people)
            await Promise.all([
                loadRoles(),
                loadRoleStatuses()
            ]);

            // Load stakeholders data
            await loadStakeholdersData();

            // Now that we have both stakeholders data and role statuses, try to match status IDs
            matchStatusIds();

            // Load people for existing roles in stakeholders data
            await loadPeopleForExistingRoles();

            // Render the table
            renderEditableStakeholdersTable();

        } catch (error) {
            console.error('Error loading stakeholders for edit:', error);
            const container = document.getElementById('systemStakeholdersContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading stakeholders data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Load stakeholders data from API
    async function loadStakeholdersData() {
        try {
            console.log('Loading stakeholders data for system:', currentSystemId);

            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/system-stakeholder/${currentSystemId}/stakeholders/edit${viewParam}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const responseData = await response.json();
            console.log('Stakeholders API response:', responseData);

            // Handle different API response formats
            const data = responseData.data || responseData;

            // Ensure data is an array
            if (!Array.isArray(data)) {
                console.warn('Invalid data format, using empty array');
                stakeholdersData = [];
            } else {
                // Transform API data to match frontend format (Edit mode - uses StatusID/StatusName)
                stakeholdersData = data.map(item => {
                    let stakeholder = {
                        objectXPeopleId: item.ObjectXPeopleID || item.objectXPeopleId || null,
                        peopleId: item.PeopleID || item.peopleId || null,
                        personName: item.PersonName || item.personName || item.name || '',
                        roleId: item.RoleID || item.roleId || null,
                        roleName: item.RoleName || item.roleName || item.role || '',
                        statusId: item.StatusID || item.statusId || null,
                        statusName: item.StatusName || item.statusName || '',
                        personEmail: item.PersonEmail || item.personEmail || '',
                        delegateOf: item.DelegateOf || item.delegateOf || item.delegate_name || '',
                        delegateIpId: item.DelegateOfId || item.delegateOfId || item.delegate_of_id || item.delegateIpId || null,
                        roleAssignmentValid: item.roleAssignmentValid !== undefined ? item.roleAssignmentValid : true,
                        roleAssignmentWarning: item.roleAssignmentWarning || null,
                        isDefaultOnlyAssignment: item.isDefaultOnlyAssignment === true
                    };

                    console.log('Processing stakeholder item for EDIT mode:', item);
                    console.log('Mapped stakeholder:', stakeholder);
                    console.log('StatusID from item:', item.StatusID, 'StatusName from item:', item.StatusName);

                    // If we got display names instead of IDs, we need to look them up
                    if (!stakeholder.roleId && stakeholder.roleName) {
                        // Find role ID by name
                        const role = rolesData.find(r => r.name === stakeholder.roleName);
                        if (role) {
                            stakeholder.roleId = role.id;
                        }
                    }

                    // Status ID matching will be done later in matchStatusIds() function

                    // For people, we'll need to load them by role if we don't have the ID
                    if (!stakeholder.peopleId && stakeholder.personName && stakeholder.roleId) {
                        // This will be handled later in loadPeopleForExistingRoles
                        console.log('Need to find people ID for:', stakeholder.personName, 'with role:', stakeholder.roleId);
                    }

                    return stakeholder;
                });
            }

            // Store original data for change detection
            originalData = JSON.parse(JSON.stringify(stakeholdersData));
            console.log('Loaded stakeholders data:', stakeholdersData);

        } catch (error) {
            console.error('Error loading stakeholders data:', error);
            stakeholdersData = [];
            originalData = [];
        }
    }

    // Load roles from API
    async function loadRoles() {
        try {
            console.log('Loading roles...');
            const response = await fetch('/api/system/stakeholder/lookup?type=roles', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Roles API response:', data);

            if (Array.isArray(data)) {
                rolesData = data.map(role => ({
                    id: role.RoleID || role.id,
                    name: role.Role || role.name || role.primaryname
                }));
            } else {
                rolesData = [];
            }

            console.log('Loaded roles:', rolesData);
        } catch (error) {
            console.error('Error loading roles:', error);
            rolesData = [];
        }
    }

    // Load role statuses from API
    async function loadRoleStatuses() {
        try {
            console.log('Loading role statuses...');
            const response = await fetch('/api/system/stakeholder/lookup?type=rolestatus', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('Role statuses API response:', data);

            if (Array.isArray(data)) {
                roleStatusesData = data.map(status => ({
                    id: status.ID || status.id,
                    name: status.PrimaryName || status.name || status.primaryname
                }));
            } else {
                roleStatusesData = [];
            }

            console.log('Loaded role statuses:', roleStatusesData);
            console.log('Role statuses mapping check:', data.map(status => ({
                original: status,
                mapped: {
                    id: status.ID || status.id,
                    name: status.PrimaryName || status.name || status.primaryname
                }
            })));
        } catch (error) {
            console.error('Error loading role statuses:', error);
            roleStatusesData = [];
        }
    }

    // Load all people from API
    async function loadAllPeople() {
        try {
            console.log('Loading all people...');
            const response = await fetch('/api/system/stakeholder/lookup?type=people', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('People API response:', data);

            if (Array.isArray(data)) {
                peopleData = data.map(person => ({
                    id: person.PeopleID || person.peopleId || person.id,
                    name: person.Name || person.name || person.personName
                }));
            } else {
                peopleData = [];
            }

            console.log('Loaded people:', peopleData);
        } catch (error) {
            console.error('Error loading people:', error);
            peopleData = [];
        }
    }

    // Load people by role
    async function loadPeopleByRole(roleId) {
        try {
            console.log('Loading people for role:', roleId);
            const response = await fetch(`/api/system/stakeholder/lookup?type=people&roleId=${roleId}&objectId=${currentSystemId}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('People by role API response:', data);

            if (Array.isArray(data)) {
                return data.map(person => ({
                    id: person.PeopleID || person.peopleId || person.id,
                    name: person.Name || person.name || person.personName
                }));
            }

            return [];
        } catch (error) {
            console.error('Error loading people by role:', error);
            return [];
        }
    }

    // Match status IDs after loading both stakeholders and role statuses
    function matchStatusIds() {
        console.log('Matching status IDs for EDIT mode...');
        console.log('Current stakeholdersData:', stakeholdersData);
        console.log('Current roleStatusesData:', roleStatusesData);

        stakeholdersData.forEach((stakeholder, index) => {
            // For edit mode, try to match statusName with available statuses if statusId is missing
            if (!stakeholder.statusId && stakeholder.statusName) {
                console.log(`Looking for status ID for stakeholder ${index} with statusName:`, stakeholder.statusName);
                const status = roleStatusesData.find(s => s.name === stakeholder.statusName);
                if (status) {
                    console.log('Found matching status:', status);
                    stakeholder.statusId = status.id;
                } else {
                    console.log('No matching status found for:', stakeholder.statusName);
                    console.log('Available status names:', roleStatusesData.map(s => s.name));
                }
            }
        });

        console.log('After matching, stakeholdersData:', stakeholdersData);
    }

    // Load people for existing roles in stakeholders data
    async function loadPeopleForExistingRoles() {
        try {
            console.log('Loading people for existing roles in stakeholders data...');

            // Get unique role IDs from existing stakeholders
            const existingRoleIds = [...new Set(stakeholdersData
                .filter(s => s.roleId)
                .map(s => s.roleId))];

            console.log('Existing role IDs:', existingRoleIds);

            // Load people for each existing role and combine them
            const allPeoplePromises = existingRoleIds.map(roleId => loadPeopleByRole(roleId));
            const allPeopleArrays = await Promise.all(allPeoplePromises);

            // Combine all people and remove duplicates
            const allPeople = [];
            const seenIds = new Set();

            allPeopleArrays.forEach(peopleArray => {
                peopleArray.forEach(person => {
                    if (!seenIds.has(person.id)) {
                        seenIds.add(person.id);
                        allPeople.push(person);
                    }
                });
            });

            // Store in peopleData for use in dropdowns
            peopleData = allPeople;
            console.log('Loaded people for existing roles:', peopleData);

            // Now try to match people IDs by name for stakeholders that don't have peopleId
            stakeholdersData.forEach(stakeholder => {
                if (!stakeholder.peopleId && stakeholder.personName) {
                    // Try exact match first
                    let person = peopleData.find(p => p.name === stakeholder.personName);

                    // If no exact match, try partial matching (handle cases where one has email and other doesn't)
                    if (!person) {
                        person = peopleData.find(p => {
                            // Extract name without email from both sides
                            const cleanPersonName = p.name.replace(/\s*\([^)]*\)\s*$/, '').trim();
                            const cleanStakeholderName = stakeholder.personName.replace(/\s*\([^)]*\)\s*$/, '').trim();
                            return cleanPersonName === cleanStakeholderName;
                        });
                    }

                    // If still no match, try checking if stakeholder name is contained in person name
                    if (!person) {
                        person = peopleData.find(p =>
                            p.name.toLowerCase().includes(stakeholder.personName.toLowerCase()) ||
                            stakeholder.personName.toLowerCase().includes(p.name.toLowerCase())
                        );
                    }

                    if (person) {
                        stakeholder.peopleId = person.id;
                        console.log('Found people ID for', stakeholder.personName, ':', person.id, 'matched with:', person.name);
                    } else {
                        console.warn('Could not find people ID for:', stakeholder.personName, 'in available people:', peopleData.map(p => p.name));
                    }
                }
            });

            // If we still have unmatched stakeholders, try loading all people and matching again
            const unmatchedStakeholders = stakeholdersData.filter(s => s.personName && (!s.peopleId || s.peopleId === "null"));
            if (unmatchedStakeholders.length > 0) {
                console.log('Found unmatched stakeholders, loading all people for better matching...');
                await loadAllPeople();

                // Try to match again with all people loaded
                stakeholdersData.forEach(stakeholder => {
                    if (stakeholder.personName && (!stakeholder.peopleId || stakeholder.peopleId === "null")) {
                        // Try exact match first
                        let person = peopleData.find(p => p.name === stakeholder.personName);

                        // If no exact match, try partial matching
                        if (!person) {
                            person = peopleData.find(p => {
                                const cleanPersonName = p.name.replace(/\s*\([^)]*\)\s*$/, '').trim();
                                const cleanStakeholderName = stakeholder.personName.replace(/\s*\([^)]*\)\s*$/, '').trim();
                                return cleanPersonName === cleanStakeholderName;
                            });
                        }

                        if (person) {
                            stakeholder.peopleId = person.id;
                            console.log('Found people ID for', stakeholder.personName, ':', person.id, 'matched with:', person.name);
                        } else {
                            console.warn('Could not find people ID for:', stakeholder.personName, 'even after loading all people.');
                        }
                    }
                });
            }

        } catch (error) {
            console.error('Error loading people for existing roles:', error);
            peopleData = [];
        }
    }

    // Render editable stakeholders table
    function renderEditableStakeholdersTable() {
        const container = document.getElementById('systemStakeholdersContainer');
        if (!container) return;

        // Check for invalid role assignments
        const invalidAssignments = stakeholdersData.filter(s => s.roleAssignmentValid === false);
        let warningBanner = '';
        if (invalidAssignments.length > 0) {
            warningBanner = `
                <div class="alert alert-warning mb-3" role="alert">
                    <div class="d-flex align-items-center">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        <strong>Warning:</strong> Some stakeholders have default roles they are not assigned to.
                    </div>
                    <hr class="my-2">
                    <p class="mb-1"><strong>Issues found:</strong></p>
                    <ul class="mb-0">
                        ${invalidAssignments.map(s => 
                            `<li>${escapeHtml(s.personName || 'Unknown')}: ${escapeHtml(s.roleAssignmentWarning || 'The user is not assigned to this role. Select another user that is assigned to this role.')}</li>`
                        ).join('')}
                    </ul>
                    <hr class="my-2">
                    <small class="text-muted">
                        Please remove these stakeholders or assign them to the roles via the admin panel before saving.
                    </small>
                </div>
            `;
        }

        // Check if the current user holds any role only by default (no formal admin assignment)
        const myDefaultOnlyRecords = currentUserId
            ? stakeholdersData.filter(s => s.peopleId == currentUserId && s.isDefaultOnlyAssignment === true)
            : [];
        let defaultOnlyBanner = '';
        if (myDefaultOnlyRecords.length > 0) {
            defaultOnlyBanner = `
                <div class="alert alert-danger mb-3" role="alert">
                    <div class="d-flex align-items-center">
                        <i class="fas fa-ban me-2"></i>
                        <strong>Cannot Save:</strong>&nbsp;You are not actually assigned to a role you currently hold on this object.
                    </div>
                    <hr class="my-2">
                    <p class="mb-1">You received the following role(s) automatically by default because no one was formally assigned:</p>
                    <ul class="mb-1">
                        ${myDefaultOnlyRecords.map(s => `<li><strong>${escapeHtml(s.roleName || 'Unknown Role')}</strong></li>`).join('')}
                    </ul>
                    <p class="mb-0">You cannot save any stakeholder changes until you either <strong>remove your record</strong> from the list, or ask an administrator to <strong>assign you to this role</strong> from the admin panel.</p>
                </div>
            `;
        }

        let html = `
            <div class="stakeholders-section-title" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem;">
                <h3 style="margin: 0;"><i class="fas fa-users" style="margin-right: 0.5rem; color: var(--secondary-color, #248567);"></i>System Stakeholders</h3>
                <div class="stakeholders-actions" style="display:flex;align-items:center;gap:8px;">
                    ${canEditStakeholders ? `
                    <button type="button" class="btn btn-primary btn-sm btn-add-stakeholder" onclick="SystemStakeholderEdit.showAddStakeholderForm()">
                        <i class="fas fa-user-plus"></i> Add Stakeholder
                    </button>
                    ` : `<span class="text-muted" style="font-size: 0.85rem;"><i class="fas fa-lock"></i> Editing disabled</span>`}
                    ${window._gridSettingsHtml ? window._gridSettingsHtml('editStakeholders') : ''}
                </div>
            </div>
            ${defaultOnlyBanner}
            ${warningBanner}
            <div class="stakeholders-edit-container">
                <table class="stakeholders-edit-table data-table table table-striped">
                    <thead>
                        <tr>
                            <th>Role <span class="required">*</span></th>
                            <th>Name <span class="required">*</span></th>
                            <th>Role Status <span class="required">*</span></th>
                            <th>Delegate Of</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody id="stakeholdersTableBody">
        `;

        if (stakeholdersData.length === 0) {
            html += `
                <tr class="no-data-row">
                    <td colspan="5" class="no-data text-center">
                        <i class="fas fa-users-slash" style="font-size: 2.5rem; opacity: 0.3; margin-bottom: 0.5rem; display: block;"></i>
                        <p style="margin: 0; font-weight: 500;">No stakeholders found</p>
                        <p style="margin: 0.25rem 0 0 0; font-size: 0.875rem; opacity: 0.7;">Click "Add Stakeholder" button above to add new stakeholders</p>
                    </td>
                </tr>
            `;
        } else {
            stakeholdersData.forEach((stakeholder, index) => {
                html += renderStakeholderRow(stakeholder, index);
            });
        }

        html += `
                    </tbody>
                </table>
            </div>
            <div class="stakeholders-validation-errors" id="stakeholdersValidationErrors"></div>
        `;

        container.innerHTML = html;

        // Initialize grid settings dropdown
        if (window._initGridSettings) window._initGridSettings(container);

        // Initialize event listeners for the table
        initTableEventListeners();
        
        // Refresh delegate dropdowns after rendering
        refreshAllDelegateDropdowns();
    }

    // Get available delegates: same system stakeholders with the same role only
    function getAvailableDelegates(currentIndex) {
        const current = stakeholdersData[currentIndex];
        if (!current) return [];
        const currentRoleId = current.roleId != null && current.roleId !== '' ? String(current.roleId) : null;
        if (currentRoleId == null) return [];

        return stakeholdersData
            .filter((s, idx) => {
                if (idx === currentIndex) return false;
                if (!s.objectXPeopleId) return false;
                if (!s.personName || !s.roleName) return false;
                const sRoleId = s.roleId != null && s.roleId !== '' ? String(s.roleId) : null;
                if (sRoleId !== currentRoleId) return false;
                return true;
            })
            .map(s => ({
                value: s.objectXPeopleId,
                label: `${s.personName} - ${s.roleName}`
            }));
    }

    // Refresh all delegate dropdowns
    function refreshAllDelegateDropdowns() {
        stakeholdersData.forEach((stakeholder, index) => {
            const delegateSelect = document.querySelector(`select.stakeholder-delegate[data-index="${index}"]`);
            if (delegateSelect) {
                const currentValue = delegateSelect.value;
                const availableDelegates = getAvailableDelegates(index);
                
                // Rebuild options
                delegateSelect.innerHTML = '<option value="">Select Delegate</option>';
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
                    stakeholdersData[index].delegateIpId = null;
                    stakeholdersData[index].delegateOf = '';
                }
            }
        });
    }

    // Render a single stakeholder row
    function renderStakeholderRow(stakeholder, index) {
        // For existing stakeholders with roleId, we need to show the current person even if we don't have all people loaded
        let peopleOptions = '<option value="">Select Person</option>';

        if (stakeholder.roleId) {
            // If this is existing data, show the current person selection
            if (stakeholder.peopleId && stakeholder.personName) {
                peopleOptions += `<option value="${stakeholder.peopleId}" selected>${escapeHtml(stakeholder.personName)}</option>`;
            }
            // Add other people from peopleData if available (but avoid duplicates)
            if (peopleData && peopleData.length > 0) {
                peopleData.forEach(person => {
                    if (person.id != stakeholder.peopleId) {
                        peopleOptions += `<option value="${person.id}">${escapeHtml(person.name)}</option>`;
                    }
                });
            }
        }

        // Add warning class if role assignment is invalid
        const warningClass = (stakeholder.roleAssignmentValid === false) ? 'role-assignment-warning' : '';
        const warningIcon = (stakeholder.roleAssignmentValid === false) ? 
            `<i class="fas fa-exclamation-triangle text-warning" title="${escapeHtml(stakeholder.roleAssignmentWarning || 'The user is not assigned to this role. Select another user that is assigned to this role.')}"></i>` : '';

        // Build delegate options - only show stakeholders already in the current system
        const availableDelegates = getAvailableDelegates(index);
        const delegateOptions = availableDelegates.map(delegate => {
            const isSelected = stakeholder.delegateIpId == delegate.value;
            return `<option value="${delegate.value}" ${isSelected ? 'selected' : ''}>${escapeHtml(delegate.label)}</option>`;
        }).join('');

        return `
            <tr data-index="${index}" data-id="${stakeholder.objectXPeopleId || ''}" class="${warningClass}">
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <select class="form-select stakeholder-role"
                                onchange="SystemStakeholderEdit.updateStakeholder(${index}, 'roleId', this.value).catch(console.error)"
                                data-original-value="${stakeholder.roleId || ''}">
                            <option value="">Select Role</option>
                            ${rolesData.map(role =>
                                `<option value="${role.id}" ${stakeholder.roleId == role.id ? 'selected' : ''}>${escapeHtml(role.name)}</option>`
                            ).join('')}
                        </select>
                        ${warningIcon}
                    </div>
                    ${stakeholder.roleAssignmentWarning ? `<small class="text-danger d-block mt-1">${escapeHtml(stakeholder.roleAssignmentWarning)}</small>` : ''}
                </td>
                <td>
                    <select class="form-select stakeholder-people"
                            onchange="SystemStakeholderEdit.updateStakeholder(${index}, 'peopleId', this.value).catch(console.error)"
                            data-original-value="${stakeholder.peopleId || ''}"
                            ${!stakeholder.roleId ? 'disabled' : ''}>
                        ${peopleOptions}
                    </select>
                </td>
                <td>
                    <select class="form-select stakeholder-status"
                            onchange="SystemStakeholderEdit.updateStakeholder(${index}, 'statusId', this.value).catch(console.error)"
                            data-original-value="${stakeholder.statusId || ''}">
                        <option value="">Select Status</option>
                        ${(() => {
                            console.log(`Rendering status dropdown for stakeholder ${index}:`, stakeholder);
                            console.log(`Stakeholder statusId: ${stakeholder.statusId}, statusName: ${stakeholder.statusName}`);
                            console.log('Available roleStatusesData:', roleStatusesData);

                            return roleStatusesData.map(status => {
                                const isSelected = stakeholder.statusId == status.id;
                                console.log(`Status ${status.id} (${status.name}): selected = ${isSelected}`);
                                return `<option value="${status.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(status.name)}</option>`;
                            }).join('');
                        })()}
                    </select>
                </td>
                <td>
                    <select class="form-select stakeholder-delegate"
                            onchange="SystemStakeholderEdit.updateStakeholder(${index}, 'delegateIpId', this.value).catch(console.error)"
                            data-index="${index}"
                            data-original-value="${stakeholder.delegateIpId || ''}">
                        <option value="">Select Delegate</option>
                        ${delegateOptions}
                        ${(() => {
                            // If delegateIpId is set but not in available delegates, add it as an option
                            if (stakeholder.delegateIpId && !availableDelegates.find(d => d.value == stakeholder.delegateIpId)) {
                                const delegate = stakeholdersData.find(s => s.objectXPeopleId == stakeholder.delegateIpId);
                                if (delegate) {
                                    return `<option value="${stakeholder.delegateIpId}" selected>${escapeHtml(delegate.personName || 'Unknown')} - ${escapeHtml(delegate.roleName || 'Unknown')}</option>`;
                                } else if (stakeholder.delegateOf) {
                                    return `<option value="${stakeholder.delegateIpId}" selected>${escapeHtml(stakeholder.delegateOf)}</option>`;
                                }
                            }
                            return '';
                        })()}
                    </select>
                </td>
                <td>
                    <div class="btn-group" role="group">
                        ${(() => {
                            if (!canEditStakeholders) return '';
                            if (stakeholder.objectXPeopleId) {
                                return `<button type="button" class="btn btn-danger btn-sm" onclick="SystemStakeholderEdit.deleteStakeholder(${index})" title="Delete stakeholder"><i class="fas fa-minus"></i></button>`;
                            }
                            return '';
                        })()}
                    </div>
                </td>
            </tr>
        `;
    }

    // Initialize table event listeners
    function initTableEventListeners() {
        // Handle role change to update people dropdown
        document.querySelectorAll('.stakeholder-role').forEach(select => {
            select.addEventListener('change', async function() {
                const row = this.closest('tr');
                const index = parseInt(row.dataset.index);
                const roleId = this.value;
                const peopleSelect = row.querySelector('.stakeholder-people');

                if (roleId) {
                    // Load people for this role
                    const peopleForRole = await loadPeopleByRole(roleId);

                    // Merge new people with existing peopleData to avoid validation issues
                    peopleForRole.forEach(person => {
                        const exists = peopleData.find(p => p.id == person.id);
                        if (!exists) {
                            peopleData.push(person);
                        }
                    });

                    // Enable and update people dropdown
                    peopleSelect.disabled = false;
                    peopleSelect.innerHTML = '<option value="">Select Person</option>';

                    peopleForRole.forEach(person => {
                        const option = document.createElement('option');
                        option.value = person.id;
                        option.textContent = person.name;
                        peopleSelect.appendChild(option);
                    });

                    // Clear current selection
                    stakeholdersData[index].peopleId = null;
                    stakeholdersData[index].personName = '';
                } else {
                    // Disable and clear people dropdown when no role selected
                    peopleSelect.disabled = true;
                    peopleSelect.innerHTML = '<option value="">Select Person</option>';

                    // Clear current selection
                    stakeholdersData[index].peopleId = null;
                    stakeholdersData[index].personName = '';
                }
                
                // Refresh delegate dropdowns when role or people changes
                refreshAllDelegateDropdowns();
            });
        });
        
        // Handle delegate dropdown changes
        document.querySelectorAll('.stakeholder-delegate').forEach(select => {
            select.addEventListener('change', function() {
                const row = this.closest('tr');
                const index = parseInt(row.dataset.index);
                const delegateIpId = this.value;
                SystemStakeholderEdit.updateStakeholder(index, 'delegateIpId', delegateIpId).catch(console.error);
            });
        });
    }

    // Show add stakeholder form modal (persist on Save / Save & Close)
    async function showAddStakeholderForm() {
        const activeStatus = roleStatusesData.find(s => (s.name || s.primaryname || '').toLowerCase() === 'active');
        const defaultStatusId = activeStatus ? activeStatus.id : (roleStatusesData[0]?.id || null);
        
        const roleOptions = rolesData.map(role => `<option value="${role.id}">${escapeHtml(role.name)}</option>`).join('');
        const statusOptions = roleStatusesData.map(status => `<option value="${status.id}" ${status.id == defaultStatusId ? 'selected' : ''}>${escapeHtml(status.name)}</option>`).join('');
        const delegateOptions = '';
        
        const modal = document.createElement('div');
        modal.className = 'stakeholder-add-modal-overlay';
        modal.id = 'addStakeholderModal';
        modal.innerHTML = `
            <div class="stakeholder-add-modal">
                <div class="stakeholder-add-modal-header">
                    <span><i class="fas fa-user-plus"></i> Add New Stakeholder</span>
                    <button class="stakeholder-add-modal-close" onclick="SystemStakeholderEdit.closeAddModal()">&times;</button>
                </div>
                <div class="stakeholder-add-modal-body">
                    <form id="addStakeholderForm">
                        <div class="form-group mb-3">
                            <label class="form-label">Role <span class="text-danger">*</span></label>
                            <select class="form-select" id="newStakeholderRole" required><option value="">Select Role</option>${roleOptions}</select>
                        </div>
                        <div class="form-group mb-3">
                            <label class="form-label">Name <span class="text-danger">*</span></label>
                            <select class="form-select" id="newStakeholderPerson" required disabled><option value="">Select Role first</option></select>
                        </div>
                        <div class="form-group mb-3">
                            <label class="form-label">Role Status <span class="text-danger">*</span></label>
                            <select class="form-select" id="newStakeholderStatus" required><option value="">Select Status</option>${statusOptions}</select>
                        </div>
                        <div class="form-group mb-3">
                            <label class="form-label">Delegate Of</label>
                            <select class="form-select" id="newStakeholderDelegate"><option value="">Select Delegate (Optional)</option>${delegateOptions}</select>
                        </div>
                    </form>
                </div>
                <div class="stakeholder-add-modal-footer">
                    <button type="button" class="btn btn-secondary" onclick="SystemStakeholderEdit.closeAddModal()">Cancel</button>
                    <button type="button" class="btn btn-primary" id="saveNewStakeholderBtn" onclick="SystemStakeholderEdit.saveNewStakeholder()"><i class="fas fa-save"></i> Save</button>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
        
        function refreshAddModalDelegateOptions(roleId) {
            const delegateSelect = document.getElementById('newStakeholderDelegate');
            if (!delegateSelect) return;
            const currentValue = delegateSelect.value;
            delegateSelect.innerHTML = '<option value="">Select Delegate (Optional)</option>';
            if (!roleId) return;
            const roleIdStr = String(roleId);
            const sameRoleStakeholders = stakeholdersData.filter(s =>
                s.objectXPeopleId && s.personName && s.roleName &&
                (s.roleId != null && s.roleId !== '' && String(s.roleId) === roleIdStr)
            );
            sameRoleStakeholders.forEach(s => {
                const option = document.createElement('option');
                option.value = s.objectXPeopleId;
                option.textContent = `${s.personName} - ${s.roleName}`;
                delegateSelect.appendChild(option);
            });
            if (currentValue && !sameRoleStakeholders.find(s => String(s.objectXPeopleId) === String(currentValue))) {
                delegateSelect.value = '';
            } else if (currentValue) {
                delegateSelect.value = currentValue;
            }
        }

        document.getElementById('newStakeholderRole').addEventListener('change', async function() {
            const roleId = this.value;
            const personSelect = document.getElementById('newStakeholderPerson');
            refreshAddModalDelegateOptions(roleId);
            if (roleId) {
                personSelect.disabled = true;
                personSelect.innerHTML = '<option value="">Loading...</option>';
                const people = await loadPeopleByRole(roleId);
                if (!people || people.length === 0) {
                    personSelect.innerHTML = '';
                    const opt = document.createElement('option');
                    opt.value = ''; opt.disabled = true; opt.selected = true;
                    opt.textContent = 'No users for this role — check Admin > Role Assignment or segment access';
                    personSelect.appendChild(opt);
                    personSelect.disabled = true;
                } else {
                    people.forEach(person => {
                        if (!peopleData.find(p => p.id == person.id)) { peopleData.push(person); }
                    });
                    personSelect.innerHTML = '<option value="">Select Person</option>';
                    people.forEach(person => { const o = document.createElement('option'); o.value = person.id; o.textContent = person.name; personSelect.appendChild(o); });
                    personSelect.disabled = false;
                }
            } else {
                personSelect.disabled = true;
                personSelect.innerHTML = '<option value="">Select Role first</option>';
            }
        });
        
        if (!document.getElementById('stakeholderModalStyles')) {
            const styles = document.createElement('style');
            styles.id = 'stakeholderModalStyles';
            styles.textContent = `.stakeholder-add-modal-overlay{position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);display:flex;justify-content:center;align-items:center;z-index:10000}.stakeholder-add-modal{background:white;border-radius:8px;width:90%;max-width:500px;box-shadow:0 4px 20px rgba(0,0,0,0.3)}.stakeholder-add-modal-header{display:flex;justify-content:space-between;align-items:center;padding:1rem 1.5rem;border-bottom:1px solid #e5e7eb;font-weight:600;font-size:1.1rem}.stakeholder-add-modal-close{background:none;border:none;font-size:1.5rem;cursor:pointer;color:#6b7280}.stakeholder-add-modal-close:hover{color:#111827}.stakeholder-add-modal-body{padding:1.5rem}.stakeholder-add-modal-footer{display:flex;justify-content:flex-end;gap:0.5rem;padding:1rem 1.5rem;border-top:1px solid #e5e7eb;background:#f9fafb;border-radius:0 0 8px 8px}`;
            document.head.appendChild(styles);
        }
    }
    
    function closeAddModal() { const modal = document.getElementById('addStakeholderModal'); if (modal) modal.remove(); }
    
    function saveNewStakeholder() {
        const roleId = document.getElementById('newStakeholderRole').value;
        const peopleId = document.getElementById('newStakeholderPerson').value;
        const statusId = document.getElementById('newStakeholderStatus').value;
        const delegateIpId = document.getElementById('newStakeholderDelegate').value;
        
        if (!roleId) {
            const m = msg('system.stakeholder.messages.pleaseSelectRole', null, 'Please select a Role');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }
        if (!peopleId) {
            const m = msg('system.stakeholder.messages.pleaseSelectPerson', null, 'Please select a Person');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }
        if (!statusId) {
            const m = msg('system.stakeholder.messages.pleaseSelectRoleStatus', null, 'Please select a Role Status');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }
        if (stakeholdersData.some(s => s.roleId == roleId && s.peopleId == peopleId)) {
            const m = msg('system.stakeholder.messages.personAlreadyAssignedToRole', null, 'This person is already assigned to this role');
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'warning'); } else { alert(m); }
            return;
        }
        
        const role = rolesData.find(r => r.id == roleId);
        const roleName = role ? (role.name || role.primaryname || '') : '';
        const status = roleStatusesData.find(s => s.id == statusId);
        const statusName = status ? (status.name || status.primaryname || '') : '';
        const personSelect = document.getElementById('newStakeholderPerson');
        const personOption = personSelect ? personSelect.options[personSelect.selectedIndex] : null;
        const personName = personOption ? personOption.textContent : '';
        let delegateOf = '';
        if (delegateIpId) {
            const delegate = stakeholdersData.find(s => String(s.objectXPeopleId) === String(delegateIpId));
            delegateOf = delegate ? `${delegate.personName || ''} - ${delegate.roleName || ''}`.trim() : '';
            const delegateSelect = document.getElementById('newStakeholderDelegate');
            if (!delegateOf && delegateSelect && delegateSelect.selectedIndex >= 0) {
                delegateOf = delegateSelect.options[delegateSelect.selectedIndex].textContent || '';
            }
        }
        
        const newStakeholder = {
            objectXPeopleId: null,
            peopleId: peopleId,
            personName: personName,
            roleId: roleId,
            roleName: roleName,
            statusId: statusId,
            statusName: statusName,
            personEmail: '',
            delegateOf: delegateOf,
            delegateIpId: delegateIpId ? parseInt(delegateIpId, 10) : null,
            roleAssignmentValid: true,
            roleAssignmentWarning: null
        };
        // Ensure person is in peopleData so validation can find them
        if (!peopleData.find(p => p.id == peopleId)) {
            peopleData.push({ id: peopleId, name: personName });
        }
        stakeholdersData.push(newStakeholder);
        closeAddModal();
        renderEditableStakeholdersTable();
        if (typeof refreshAllDelegateDropdowns === 'function') {
            refreshAllDelegateDropdowns();
        }
    }

    // Add new stakeholder row (legacy)
    function addNewRow() { showAddStakeholderForm(); }

    // Add new stakeholder row after specific index (legacy)
    function addNewRowAfter(index) {
        // Find Active status ID
        const activeStatus = roleStatusesData.find(s => 
            (s.name || s.primaryname || '').toLowerCase() === 'active'
        );

        const newStakeholder = {
            objectXPeopleId: null, // Will be assigned by backend
            peopleId: null,
            personName: '',
            roleId: null,
            roleName: '',
            statusId: activeStatus ? activeStatus.id : null,  // Set default to Active
            statusName: activeStatus ? (activeStatus.name || activeStatus.primaryname) : 'Active',
            personEmail: '',
            delegateOf: '',
            delegateIpId: null
        };

        // Insert after the specified index
        stakeholdersData.splice(index + 1, 0, newStakeholder);
        renderEditableStakeholdersTable();
        refreshAllDelegateDropdowns();
        console.log('Added new stakeholder row after index:', index);
    }

    // Update stakeholder data
    async function updateStakeholder(index, field, value) {
        if (index >= 0 && index < stakeholdersData.length) {
            stakeholdersData[index][field] = value || null;

            // Update related fields based on selection
            if (field === 'roleId' && value) {
                const role = rolesData.find(r => r.id == value);
                stakeholdersData[index].roleName = role ? role.name : '';
                // Clear person selection when role changes
                stakeholdersData[index].peopleId = null;
                stakeholdersData[index].personName = '';
            } else if (field === 'peopleId' && value) {
                let person = peopleData.find(p => p.id == value);

                // If not found in peopleData, try to get from the dropdown itself
                if (!person) {
                    const row = document.querySelector(`tr[data-index="${index}"]`);
                    if (row) {
                        const peopleSelect = row.querySelector('.stakeholder-people');
                        if (peopleSelect) {
                            const selectedOption = peopleSelect.querySelector(`option[value="${value}"]`);
                            if (selectedOption) {
                                const personName = selectedOption.textContent;
                                person = { id: value, name: personName };
                                // Add to peopleData for future validation
                                peopleData.push(person);
                                console.log(`Person not in peopleData, extracted from dropdown: ${personName}`);
                            }
                        }
                    }
                }

                // If still not found, try to load from API
                if (!person) {
                    const currentRoleId = stakeholdersData[index].roleId;
                    if (currentRoleId) {
                        const rolePeople = await loadPeopleByRole(currentRoleId);
                        person = rolePeople.find(p => p.id == value);

                        // Add to peopleData if found
                        if (person && !peopleData.find(p => p.id == person.id)) {
                            peopleData.push(person);
                        }
                    }
                }

                stakeholdersData[index].personName = person ? person.name : '';
            } else if (field === 'statusId' && value) {
                const status = roleStatusesData.find(s => s.id == value);
                stakeholdersData[index].statusName = status ? status.name : '';
            } else if (field === 'delegateIpId') {
                // Update delegateIpId and find the delegate name for display
                stakeholdersData[index].delegateIpId = value ? parseInt(value) : null;
                if (value) {
                    const delegate = stakeholdersData.find(s => s.objectXPeopleId == value);
                    stakeholdersData[index].delegateOf = delegate ? `${delegate.personName} - ${delegate.roleName}` : '';
                } else {
                    stakeholdersData[index].delegateOf = '';
                }
                // Refresh all delegate dropdowns when a delegate is selected
                refreshAllDelegateDropdowns();
            }

            // Real-time validation for the current row
            validateCurrentRow(index);

            console.log('Updated stakeholder:', index, field, value);
        }
    }

    // Validate current row in real-time
    function validateCurrentRow(index) {
        if (index < 0 || index >= stakeholdersData.length) return;

        const stakeholder = stakeholdersData[index];
        const row = document.querySelector(`tr[data-index="${index}"]`);
        if (!row) return;

        // Remove existing error classes
        row.querySelectorAll('.form-select').forEach(select => {
            select.classList.remove('is-invalid');
        });

        // Validate and add error classes
        const roleSelect = row.querySelector('.stakeholder-role');
        const peopleSelect = row.querySelector('.stakeholder-people');
        const statusSelect = row.querySelector('.stakeholder-status');

        if (!stakeholder.roleId) {
            roleSelect?.classList.add('is-invalid');
        }

        if (!stakeholder.peopleId) {
            peopleSelect?.classList.add('is-invalid');
        }

        if (!stakeholder.statusId) {
            statusSelect?.classList.add('is-invalid');
        }

        // Clear validation errors if all fields are valid
        if (stakeholder.roleId && stakeholder.peopleId && stakeholder.statusId) {
            clearValidationErrors();
        }
    }

    // Delete stakeholder locally (persist on Save / Save & Close)
    async function deleteStakeholder(index) {
        if (index >= 0 && index < stakeholdersData.length) {
            const stakeholder = stakeholdersData[index];
            
            if (!canEditStakeholders) {
                const m = msg('system.stakeholder.messages.noPermissionDeleteStakeholders', null, 'You do not have permission to delete stakeholders');
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                return;
            }
            
            const confirmMessage = `Are you sure you want to delete "${stakeholder.personName || 'this stakeholder'}" as "${stakeholder.roleName || 'stakeholder'}"?`;
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmed) {
                try {
                    stakeholdersData.splice(index, 1);
                    renderEditableStakeholdersTable();
                    refreshAllDelegateDropdowns();
                } catch (error) {
                    const errMsg = error.message || 'Unknown error';
                    const m = msg('system.stakeholder.messages.errorDeletingStakeholder', { message: errMsg }, 'Error deleting stakeholder: ' + errMsg);
                    if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                }
            }
        }
    }

    // Validate stakeholders data
    function validateStakeholders() {
        const errors = [];

        console.log('Validating stakeholders data:', stakeholdersData);
        console.log('Available people data:', peopleData);
        console.log('Available roles data:', rolesData);
        console.log('Available role statuses data:', roleStatusesData);

        stakeholdersData.forEach((stakeholder, index) => {
            const rowNum = index + 1;

            // Validate Role (Required)
            if (!stakeholder.roleId || stakeholder.roleId === null || stakeholder.roleId === '') {
                errors.push(`Row ${rowNum}: Role is required and must be selected`);
            } else {
                // Check if role exists in available roles
                const roleExists = rolesData.find(r => r.id == stakeholder.roleId);
                if (!roleExists) {
                    errors.push(`Row ${rowNum}: Selected role is invalid`);
                }
            }

            // Validate Name (Required)
            if (!stakeholder.peopleId || stakeholder.peopleId === null || stakeholder.peopleId === '') {
                errors.push(`Row ${rowNum}: Name is required and must be selected`);
            } else {
                // Check if person exists in available people
                const personExists = peopleData.find(p => p.id == stakeholder.peopleId);
                if (!personExists) {
                    // Person not in cache — if personName is set it was legitimately selected; add to cache and allow
                    if (stakeholder.personName && stakeholder.personName.trim() !== '') {
                        peopleData.push({ id: stakeholder.peopleId, name: stakeholder.personName });
                        console.log(`Row ${rowNum}: Person not in peopleData cache, restored from stakeholder data: ${stakeholder.personName}`);
                    } else {
                        errors.push(`Row ${rowNum}: Selected person is invalid`);
                    }
                }
            }

            // Validate Role Status (Required)
            if (!stakeholder.statusId || stakeholder.statusId === null || stakeholder.statusId === '') {
                errors.push(`Row ${rowNum}: Role Status is required and must be selected`);
            } else {
                // Check if status exists in available statuses
                const statusExists = roleStatusesData.find(s => s.id == stakeholder.statusId);
                if (!statusExists) {
                    errors.push(`Row ${rowNum}: Selected status is invalid`);
                }
            }

            // Validate role assignment for default roles
            if (stakeholder.roleAssignmentValid === false && stakeholder.roleAssignmentWarning) {
                errors.push(`Row ${rowNum}: ${stakeholder.roleAssignmentWarning}`);
            }

            // Validate person name is not empty (additional check) - more lenient
            if (!stakeholder.personName || stakeholder.personName.trim() === '') {
                // Only show this error if we have a peopleId but no personName
                // This is a soft validation - we allow it if peopleId exists
                if (stakeholder.peopleId) {
                    console.warn(`Row ${rowNum}: Person ID exists but name is empty, attempting to find name...`);
                    // Try to find the person name from peopleData
                    const person = peopleData.find(p => p.id == stakeholder.peopleId);
                    if (person) {
                        stakeholder.personName = person.name;
                        console.log(`Found person name: ${person.name}`);
                    } else {
                        // If still not found, allow it to pass validation if peopleId exists
                        console.log(`Row ${rowNum}: Person ID exists (${stakeholder.peopleId}) but name not found in peopleData, allowing validation to pass`);
                    }
                }
            }

            // Validate role name is not empty (additional check)
            if (!stakeholder.roleName || stakeholder.roleName.trim() === '') {
                errors.push(`Row ${rowNum}: Role name cannot be empty`);
            }
        });

        // Check for duplicate combinations (Role + Person)
        const combinations = [];
        const duplicateRows = [];

        stakeholdersData.forEach((stakeholder, index) => {
            if (stakeholder.roleId && stakeholder.peopleId) {
                const combo = `${stakeholder.roleId}-${stakeholder.peopleId}`;
                const existingIndex = combinations.indexOf(combo);
                if (existingIndex !== -1) {
                    duplicateRows.push(`Row ${index + 1} and Row ${existingIndex + 1}`);
                } else {
                    combinations.push(combo);
                }
            }
        });

        if (duplicateRows.length > 0) {
            errors.push(`Duplicate role-person combinations found: ${duplicateRows.join(', ')}`);
        }

        return errors;
    }

    // Show validation errors
    function showValidationErrors(errors) {
        const errorContainer = document.getElementById('stakeholdersValidationErrors');
        if (errorContainer && errors.length > 0) {
            errorContainer.innerHTML = `
                <div class="alert alert-danger" role="alert">
                    <div class="d-flex align-items-center">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        <h5 class="mb-0">Validation Errors</h5>
                    </div>
                    <hr class="my-2">
                    <p class="mb-2">
                        <strong>Please fix the following errors before saving:</strong>
                    </p>
                    <ul class="mb-0">
                        ${errors.map(error => `<li class="mb-1">${escapeHtml(error)}</li>`).join('')}
                    </ul>
                    <hr class="my-2">
                    <small class="text-muted">
                        <i class="fas fa-info-circle"></i>
                        All fields marked with <span class="text-danger">*</span> are required.
                    </small>
                </div>
            `;
            errorContainer.style.display = 'block';

            // Add shake animation to error container
            errorContainer.classList.add('shake-animation');
            setTimeout(() => {
                errorContainer.classList.remove('shake-animation');
            }, 600);
        }
    }

    // Clear validation errors
    function clearValidationErrors() {
        const errorContainer = document.getElementById('stakeholdersValidationErrors');
        if (errorContainer) {
            errorContainer.style.display = 'none';
            errorContainer.innerHTML = '';
        }
    }

    // Check if data has changed
    function hasDataChanged() {
        return JSON.stringify(stakeholdersData) !== JSON.stringify(originalData);
    }

    // Save stakeholders data
    async function saveStakeholders() {
        // Prevent multiple simultaneous saves
        if (window._stakeholdersSaving) {
            console.log('Save operation already in progress, skipping...');
            return false;
        }

        try {
            window._stakeholdersSaving = true;
            console.log('Starting stakeholders save operation...');

            // Clear previous validation errors
            clearValidationErrors();

            // Comprehensive validation
            const errors = validateStakeholders();
            if (errors.length > 0) {
                showValidationErrors(errors);
                // Scroll to errors
                const errorContainer = document.getElementById('stakeholdersValidationErrors');
                if (errorContainer) {
                    errorContainer.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
                return false;
            }

            // Additional pre-save checks
            const preCheckErrors = performPreSaveChecks();
            if (preCheckErrors.length > 0) {
                showValidationErrors(preCheckErrors);
                return false;
            }

            // Check if data has changed
            if (!hasDataChanged()) {
                console.log('No changes detected in stakeholders data');
                return true;
            }



            // Determine what operations need to be performed
            const operations = determineOperations();
            console.log('Operations to perform:', {
                deletions: operations.deletions.length,
                updates: operations.updates.length,
                insertions: operations.insertions.length
            });

            // Perform operations sequentially with detailed logging
            const results = {
                deletions: { success: 0, failed: 0, errors: [] },
                updates: { success: 0, failed: 0, errors: [] },
                insertions: { success: 0, failed: 0, errors: [] }
            };

            // Handle deletions first
            console.log(`Processing ${operations.deletions.length} deletions...`);
            for (const deletion of operations.deletions) {
                try {
                    console.log('Deleting stakeholder:', deletion.objectXPeopleId);
                    const success = await deleteStakeholderAPI(deletion.objectXPeopleId);
                    if (success) {
                        results.deletions.success++;
                    } else {
                        results.deletions.failed++;
                        results.deletions.errors.push(`Failed to delete stakeholder ${deletion.objectXPeopleId}`);
                    }
                } catch (error) {
                    results.deletions.failed++;
                    results.deletions.errors.push(`Error deleting stakeholder ${deletion.objectXPeopleId}: ${error.message}`);
                    console.error('Deletion error:', error);
                }
            }

            // Handle updates
            console.log(`Processing ${operations.updates.length} updates...`);
            for (const update of operations.updates) {
                try {
                    console.log('Updating stakeholder:', update.objectXPeopleId);
                    const success = await updateStakeholderAPI(update);
                    if (success) {
                        results.updates.success++;
                    } else {
                        results.updates.failed++;
                        results.updates.errors.push(`Failed to update stakeholder ${update.objectXPeopleId}`);
                    }
                } catch (error) {
                    results.updates.failed++;
                    results.updates.errors.push(`Error updating stakeholder ${update.objectXPeopleId}: ${error.message}`);
                    console.error('Update error:', error);
                }
            }

            // Handle insertions
            console.log(`Processing ${operations.insertions.length} insertions...`);
            for (const insertion of operations.insertions) {
                try {
                    console.log('Adding new stakeholder:', insertion);
                    const success = await addStakeholderAPI(insertion);
                    if (success) {
                        results.insertions.success++;
                    } else {
                        results.insertions.failed++;
                        results.insertions.errors.push(`Failed to add new stakeholder`);
                    }
                } catch (error) {
                    results.insertions.failed++;
                    results.insertions.errors.push(`Error adding stakeholder: ${error.message}`);
                    console.error('Insertion error:', error);
                }
            }

            // Check overall results
            const totalSuccess = results.deletions.success + results.updates.success + results.insertions.success;
            const totalFailed = results.deletions.failed + results.updates.failed + results.insertions.failed;

            console.log('Save operation results:', results);

            if (totalFailed === 0) {
                // All operations successful
                originalData = JSON.parse(JSON.stringify(stakeholdersData));
                console.log('All stakeholders saved successfully!');
                return true;
            } else if (totalSuccess > 0) {
                // Partial success
                const errorMessage = `Some operations failed:\n\n` +
                    `Successful: ${totalSuccess}\n` +
                    `Failed: ${totalFailed}\n\n` +
                    `Errors:\n${[...results.deletions.errors, ...results.updates.errors, ...results.insertions.errors].join('\n')}`;

                console.error('Partial save failure:', errorMessage);
                const m = msg('system.stakeholder.messages.someStakeholdersNotSaved', null, 'Some stakeholders could not be saved. Please check the details and try again.') + '\n\n' + errorMessage;
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                return false;
            } else {
                // Complete failure
                const errorMessage = `All operations failed:\n\n` +
                    `Errors:\n${[...results.deletions.errors, ...results.updates.errors, ...results.insertions.errors].join('\n')}`;

                console.error('Complete save failure:', errorMessage);
                const m = msg('system.stakeholder.messages.failedToSaveStakeholders', null, 'Failed to save stakeholders. Please check the details and try again.') + '\n\n' + errorMessage;
                if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
                return false;
            }

        } catch (error) {
            console.error('Critical error during save operation:', error);
            const errMsg = error.message || 'Unknown error';
            const m = msg('system.stakeholder.messages.criticalErrorSavingStakeholders', { message: errMsg }, 'Critical error saving stakeholders: ' + errMsg);
            if (typeof window.showNotification === 'function') { window.showNotification(m, 'error'); } else { alert(m); }
            return false;
        } finally {
            window._stakeholdersSaving = false;
            console.log('Save operation completed.');
        }
    }

    // Perform additional pre-save checks
    function performPreSaveChecks() {
        const errors = [];

        // Check if required data is loaded
        if (!rolesData || rolesData.length === 0) {
            errors.push('Roles data not loaded. Please refresh the page and try again.');
        }

        if (!roleStatusesData || roleStatusesData.length === 0) {
            errors.push('Role statuses data not loaded. Please refresh the page and try again.');
        }

        // Check for network connectivity (basic check)
        if (!navigator.onLine) {
            errors.push('No internet connection detected. Please check your connection and try again.');
        }

        // Validate system ID
        if (!currentSystemId || currentSystemId <= 0) {
            errors.push('Invalid system ID. Please refresh the page and try again.');
        }

        // Business rules validation removed

        return errors;
    }

    // Determine what operations need to be performed
    function determineOperations() {
        const operations = {
            insertions: [],
            updates: [],
            deletions: []
        };

        // Find deletions (items in original but not in current)
        originalData.forEach(original => {
            if (original.objectXPeopleId) {
                const found = stakeholdersData.find(current =>
                    current.objectXPeopleId === original.objectXPeopleId
                );
                if (!found) {
                    operations.deletions.push(original);
                }
            }
        });

        // Find insertions and updates
        stakeholdersData.forEach(current => {
            if (!current.objectXPeopleId) {
                // Check if this stakeholder already exists in originalData by peopleId + roleId
                const existingStakeholder = originalData.find(orig => {
                    const origPeopleId = orig.peopleId ? parseInt(orig.peopleId, 10) : null;
                    const origRoleId = orig.roleId ? parseInt(orig.roleId, 10) : null;
                    const currentPeopleId = current.peopleId ? parseInt(current.peopleId, 10) : null;
                    const currentRoleId = current.roleId ? parseInt(current.roleId, 10) : null;
                    return origPeopleId === currentPeopleId && origRoleId === currentRoleId;
                });
                
                if (existingStakeholder) {
                    // Stakeholder exists - check if it changed
                    const currentStatusId = current.statusId ? parseInt(current.statusId, 10) : null;
                    const existingStatusId = existingStakeholder.statusId ? parseInt(existingStakeholder.statusId, 10) : null;
                    
                    if (currentStatusId !== existingStatusId) {
                        // Status changed - treat as update
                        operations.updates.push({
                            ...current,
                            objectXPeopleId: existingStakeholder.objectXPeopleId
                        });
                    }
                    // If unchanged, skip it (don't add to insertions)
                } else {
                    // Truly new stakeholder
                    operations.insertions.push(current);
                }
            } else {
                // Existing item - check if changed
                const original = originalData.find(orig =>
                    orig.objectXPeopleId === current.objectXPeopleId
                );
                if (original) {
                    const hasChanged = (
                        current.roleId !== original.roleId ||
                        current.peopleId !== original.peopleId ||
                        current.statusId !== original.statusId ||
                        current.delegateIpId !== original.delegateIpId
                    );
                    if (hasChanged) {
                        operations.updates.push(current);
                    }
                }
            }
        });

        return operations;
    }

    // API call to add stakeholder
    async function addStakeholderAPI(stakeholder) {
        try {
            console.log('Adding stakeholder via API:', {
                peopleId: stakeholder.peopleId,
                roleId: stakeholder.roleId,
                statusId: stakeholder.statusId
            });

            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/system-stakeholder/${currentSystemId}/stakeholders/edit${viewParam}`, {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    ipid: stakeholder.peopleId,
                    roleID: stakeholder.roleId,
                    statusID: stakeholder.statusId,
                    delegateIpId: stakeholder.delegateIpId
                })
            });

            console.log('Add API response status:', response.status);

            if (!response.ok) {
                let errorMessage = `HTTP ${response.status}`;
                try {
                    const errorData = await response.json();
                    console.log('Add API error response:', errorData);
                    errorMessage = errorData.error || errorData.message || errorMessage;
                } catch (parseError) {
                    console.log('Could not parse error response:', parseError);
                    errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                }
                throw new Error(errorMessage);
            }

            const responseData = await response.json().catch(() => ({}));
            console.log('Add API success response:', responseData);
            return true;
        } catch (error) {
            console.error('Error adding stakeholder:', {
                stakeholder: stakeholder,
                error: error.message,
                stack: error.stack
            });
            throw error; // Re-throw to be handled by caller
        }
    }

    // API call to update stakeholder
    async function updateStakeholderAPI(stakeholder) {
        try {
            console.log('Updating stakeholder via API:', {
                objectXPeopleId: stakeholder.objectXPeopleId,
                peopleId: stakeholder.peopleId,
                roleId: stakeholder.roleId,
                statusId: stakeholder.statusId
            });

            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/system-stakeholder/${currentSystemId}/stakeholders/edit${viewParam}`, {
                method: 'PUT',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    objectXPeopleId: stakeholder.objectXPeopleId,
                    ipid: stakeholder.peopleId,
                    roleID: stakeholder.roleId,
                    statusID: stakeholder.statusId,
                    delegateIpId: stakeholder.delegateIpId
                })
            });

            console.log('Update API response status:', response.status);

            if (!response.ok) {
                let errorMessage = `HTTP ${response.status}`;
                try {
                    const errorData = await response.json();
                    console.log('Update API error response:', errorData);
                    errorMessage = errorData.error || errorData.message || errorMessage;
                } catch (parseError) {
                    console.log('Could not parse error response:', parseError);
                    errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                }
                throw new Error(errorMessage);
            }

            const responseData = await response.json().catch(() => ({}));
            console.log('Update API success response:', responseData);
            return true;
        } catch (error) {
            console.error('Error updating stakeholder:', {
                stakeholder: stakeholder,
                error: error.message,
                stack: error.stack
            });
            throw error; // Re-throw to be handled by caller
        }
    }

    // API call to delete stakeholder
    async function deleteStakeholderAPI(objectXPeopleId) {
        try {
            console.log('Deleting stakeholder with objectXPeopleId:', objectXPeopleId);

            if (!objectXPeopleId) {
                throw new Error('Invalid objectXPeopleId for deletion');
            }

            const viewParam = currentViewMode === 'changes' ? '&view=changes' : '';
            const response = await fetch(`/api/system-stakeholder/${currentSystemId}/stakeholders/edit?objectXPeopleId=${objectXPeopleId}${viewParam}`, {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            console.log('Delete API response status:', response.status);

            if (!response.ok) {
                let errorMessage = `HTTP ${response.status}`;
                try {
                    const errorData = await response.json();
                    console.log('Delete API error response:', errorData);
                    errorMessage = errorData.error || errorData.message || errorMessage;
                } catch (parseError) {
                    console.log('Could not parse error response:', parseError);
                    errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                }
                throw new Error(errorMessage);
            }

            const responseData = await response.json().catch(() => ({}));
            console.log('Delete API success response:', responseData);
            return true;
        } catch (error) {
            console.error('Error deleting stakeholder:', {
                objectXPeopleId: objectXPeopleId,
                error: error.message,
                stack: error.stack
            });
            throw error; // Re-throw to be handled by caller
        }
    }

    // Escape HTML to prevent XSS
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    // Export functions to global scope
    window.SystemStakeholderEdit = {
        init: initStakeholderEdit,
        addNewRow: addNewRow,
        addNewRowAfter: addNewRowAfter,
        updateStakeholder: updateStakeholder,
        deleteStakeholder: deleteStakeholder,
        saveStakeholders: saveStakeholders,
        hasDataChanged: hasDataChanged,
        getData: () => stakeholdersData,
        showAddStakeholderForm: showAddStakeholderForm,
        closeAddModal: closeAddModal,
        saveNewStakeholder: saveNewStakeholder
    };

})();

