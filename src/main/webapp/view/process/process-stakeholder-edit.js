// Process Stakeholder Edit JavaScript - New Implementation
(function() {
    let currentProcessId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let stakeholdersData = [];
    let originalData = [];
    let rolesData = [];
    let roleStatusesData = [];
    let peopleData = [];
    let currentUserId = null;
    let canEditStakeholders = false;
    let isAdminUser = false;
    let hasRoleEditPermission = false;

    // Initialize stakeholder edit
    async function initStakeholderEdit(processId, viewMode = 'original') {
        currentProcessId = processId;
        currentViewMode = viewMode || 'original';
        console.log('Initializing stakeholder edit for process:', processId);
        await fetchCurrentUser();
        await checkUserRolePermission();
        await checkStakeholderEditPermission();
        loadStakeholdersForEdit();
    }
    
    async function fetchCurrentUser() {
        try {
            const response = await fetch('/api/me', { method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' } });
            if (response.ok) {
                const userData = await response.json();
                currentUserId = userData.id;
                const roleName = (userData.roleName || userData.role || '').toLowerCase();
                isAdminUser = roleName.includes('admin') || roleName === 'administrator' || roleName.includes('super');
            }
        } catch (error) { console.error('[ProcessStakeholderEdit] Error fetching current user:', error); }
    }
    
    async function checkUserRolePermission() {
        try {
            const response = await fetch('/api/user/permissions/Process', { method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' } });
            if (response.ok) {
                const perms = await response.json();
                hasRoleEditPermission = perms.success && (perms.canEdit === true || perms.isAdmin === true);
            }
        } catch (error) { hasRoleEditPermission = false; }
    }
    
    async function checkStakeholderEditPermission() {
        const hasBasicEditPermission = isAdminUser || hasRoleEditPermission;
        
        console.log('[ProcessStakeholderEdit] Permission check:', {
            isAdminUser: isAdminUser,
            hasRoleEditPermission: hasRoleEditPermission,
            hasBasicEditPermission: hasBasicEditPermission
        });
        
        if (!hasBasicEditPermission) {
            canEditStakeholders = false;
            console.log('[ProcessStakeholderEdit] No basic edit permission');
            return;
        }
        
        try {
            const response = await fetch(`/api/pending-changes/stakeholder-edit-permission/Process/${currentProcessId}`, {
                method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' }
            });
            if (response.ok) {
                const data = await response.json();
                const workflowAllowsEdit = data.canEdit === true;
                canEditStakeholders = hasBasicEditPermission && workflowAllowsEdit;
                console.log('[ProcessStakeholderEdit] Final permission:', {
                    workflowAllowsEdit: workflowAllowsEdit,
                    canEditStakeholders: canEditStakeholders,
                    reason: data.reason
                });
            } else {
                canEditStakeholders = hasBasicEditPermission;
                console.log('[ProcessStakeholderEdit] Workflow API failed, using basic permission:', canEditStakeholders);
            }
        } catch (error) {
            console.error('[ProcessStakeholderEdit] Error checking workflow permission:', error);
            canEditStakeholders = hasBasicEditPermission;
        }
    }

    // Load stakeholders data for editing
    async function loadStakeholdersForEdit() {
        try {
            const container = document.getElementById('processStakeholdersContainer');
            if (!container) return;

            container.innerHTML = '<div class="loading">Loading stakeholders for editing...</div>';

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
            const container = document.getElementById('processStakeholdersContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading stakeholders data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Load stakeholders data from API
    async function loadStakeholdersData() {
        try {
            console.log('Loading stakeholders data for process:', currentProcessId);

            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/process-stakeholder/${currentProcessId}/stakeholders/edit${viewParam}`, {
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
            const response = await fetch('/api/process-stakeholder/stakeholder/lookup?type=roles', {
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
            const response = await fetch('/api/process-stakeholder/stakeholder/lookup?type=rolestatus', {
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
            const response = await fetch('/api/process-stakeholder/stakeholder/lookup?type=people', {
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
            const response = await fetch(`/api/process-stakeholder/stakeholder/lookup?type=people&roleId=${roleId}&objectId=${currentProcessId}`, {
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
                if (stakeholder.personName && (!stakeholder.peopleId || stakeholder.peopleId === "null")) {
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
                        // If we still can't find a match, we need to load all people to try to find a match
                        console.warn('Could not find people ID for:', stakeholder.personName, 'in available people for roles. Will try to load all people.');
                    }
                }
            });

            // Check if we have any stakeholders without peopleId but with personName
            const needAllPeople = stakeholdersData.some(s => s.personName && (!s.peopleId || s.peopleId === "null"));

            if (needAllPeople) {
                console.log('Loading all people to find matches for stakeholders without peopleId...');
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
        const container = document.getElementById('processStakeholdersContainer');
        if (!container) return;

        // Check for invalid role assignments
        const invalidAssignments = stakeholdersData.filter(s => s.roleAssignmentValid === false);
        let warningBanner = '';
        if (invalidAssignments.length > 0) {
            const warnings = invalidAssignments.map(s => s.roleAssignmentWarning || 'The user is not assigned to this role. Select another user that is assigned to this role.').join('; ');
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
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="stakeholders-section-title">
                    <h3>Process Stakeholders</h3>
                    <div class="stakeholders-actions">
                        ${canEditStakeholders ? `
                        <button type="button" class="btn btn-primary btn-sm" onclick="ProcessStakeholderEdit.showAddStakeholderForm()">
                            <i class="fas fa-plus"></i> Add Stakeholder
                        </button>` : `<span class="text-muted" style="font-size: 0.85rem;"><i class="fas fa-lock"></i> Editing disabled</span>`}
                    </div>
                </div>
                ${defaultOnlyBanner}
                ${warningBanner}
                <div class="stakeholders-edit-container">
                    <table class="stakeholders-edit-table table table-striped">
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
                        No stakeholders found. Click "Add Stakeholder" to add new ones.
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
            </div>
        `;

        container.innerHTML = html;

        // Initialize event listeners for the table
        initTableEventListeners();

        // Check for duplicates after rendering
        checkForDuplicates();
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

        // Add a data attribute for person name to help with debugging
        const personNameAttr = stakeholder.personName ?
            `data-person-name="${escapeHtml(stakeholder.personName)}"` : '';

        // Add warning class if role assignment is invalid
        const warningClass = (stakeholder.roleAssignmentValid === false) ? 'role-assignment-warning' : '';
        const warningIcon = (stakeholder.roleAssignmentValid === false) ? 
            `<i class="fas fa-exclamation-triangle text-warning" title="${escapeHtml(stakeholder.roleAssignmentWarning || 'The user is not assigned to this role. Select another user that is assigned to this role.')}"></i>` : '';

        // Build delegate options - only show stakeholders already in the current process
        const availableDelegates = getAvailableDelegates(index);
        const delegateOptions = availableDelegates.map(delegate => {
            const isSelected = stakeholder.delegateIpId == delegate.value;
            return `<option value="${delegate.value}" ${isSelected ? 'selected' : ''}>${escapeHtml(delegate.label)}</option>`;
        }).join('');

        return `
            <tr data-index="${index}" data-id="${stakeholder.objectXPeopleId || ''}" ${personNameAttr} class="${warningClass}">
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <select class="form-select stakeholder-role"
                                onchange="ProcessStakeholderEdit.updateStakeholder(${index}, 'roleId', this.value)"
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
                    <div class="d-flex align-items-center gap-2">
                        <select class="form-select stakeholder-people"
                                onchange="ProcessStakeholderEdit.updateStakeholder(${index}, 'peopleId', this.value)"
                                data-original-value="${stakeholder.peopleId || ''}"
                                data-person-name="${escapeHtml(stakeholder.personName || '')}"
                                ${!stakeholder.roleId ? 'disabled' : ''}>
                            ${peopleOptions}
                        </select>
                    </div>
                </td>
                <td>
                    <select class="form-select stakeholder-status"
                            onchange="ProcessStakeholderEdit.updateStakeholder(${index}, 'statusId', this.value)"
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
                            onchange="ProcessStakeholderEdit.updateStakeholder(${index}, 'delegateIpId', this.value)"
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
                                return `<button type="button" class="btn btn-danger btn-sm" onclick="ProcessStakeholderEdit.deleteStakeholder(${index})" title="Delete stakeholder"><i class="fas fa-minus"></i></button>`;
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
                ProcessStakeholderEdit.updateStakeholder(index, 'delegateIpId', delegateIpId);
            });
        });
    }

    // Get available delegates: same process stakeholders with the same role only
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
                    <button class="stakeholder-add-modal-close" onclick="ProcessStakeholderEdit.closeAddModal()">&times;</button>
                </div>
                <div class="stakeholder-add-modal-body">
                    <form id="addStakeholderForm">
                        <div class="form-group mb-3"><label class="form-label">Role <span class="text-danger">*</span></label><select class="form-select" id="newStakeholderRole" required><option value="">Select Role</option>${roleOptions}</select></div>
                        <div class="form-group mb-3"><label class="form-label">Name <span class="text-danger">*</span></label><select class="form-select" id="newStakeholderPerson" required disabled><option value="">Select Role first</option></select></div>
                        <div class="form-group mb-3"><label class="form-label">Role Status <span class="text-danger">*</span></label><select class="form-select" id="newStakeholderStatus" required><option value="">Select Status</option>${statusOptions}</select></div>
                        <div class="form-group mb-3"><label class="form-label">Delegate Of</label><select class="form-select" id="newStakeholderDelegate"><option value="">Select Delegate (Optional)</option>${delegateOptions}</select></div>
                    </form>
                </div>
                <div class="stakeholder-add-modal-footer">
                    <button type="button" class="btn btn-secondary" onclick="ProcessStakeholderEdit.closeAddModal()">Cancel</button>
                    <button type="button" class="btn btn-primary" id="saveNewStakeholderBtn" onclick="ProcessStakeholderEdit.saveNewStakeholder()"><i class="fas fa-save"></i> Save</button>
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
            const roleId = this.value; const personSelect = document.getElementById('newStakeholderPerson');
            refreshAddModalDelegateOptions(roleId);
            if (roleId) {
                personSelect.disabled = true; personSelect.innerHTML = '<option value="">Loading...</option>';
                const people = await loadPeopleByRole(roleId);
                if (!people || people.length === 0) {
                    personSelect.innerHTML = '';
                    const opt = document.createElement('option');
                    opt.value = ''; opt.disabled = true; opt.selected = true;
                    opt.textContent = 'No users for this role — check Admin > Role Assignment or segment access';
                    personSelect.appendChild(opt);
                    personSelect.disabled = true;
                } else {
                    people.forEach(p => { if (!peopleData.find(existing => String(existing.id) === String(p.id))) { peopleData.push(p); } });
                    personSelect.innerHTML = '<option value="">Select Person</option>';
                    people.forEach(p => { const o = document.createElement('option'); o.value = p.id; o.textContent = p.name; personSelect.appendChild(o); });
                    personSelect.disabled = false;
                }
            } else { personSelect.disabled = true; personSelect.innerHTML = '<option value="">Select Role first</option>'; }
        });
        
        if (!document.getElementById('stakeholderModalStyles')) {
            const styles = document.createElement('style'); styles.id = 'stakeholderModalStyles';
            styles.textContent = `.stakeholder-add-modal-overlay{position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);display:flex;justify-content:center;align-items:center;z-index:10000}.stakeholder-add-modal{background:white;border-radius:8px;width:90%;max-width:500px;box-shadow:0 4px 20px rgba(0,0,0,0.3)}.stakeholder-add-modal-header{display:flex;justify-content:space-between;align-items:center;padding:1rem 1.5rem;border-bottom:1px solid #e5e7eb;font-weight:600;font-size:1.1rem}.stakeholder-add-modal-close{background:none;border:none;font-size:1.5rem;cursor:pointer;color:#6b7280}.stakeholder-add-modal-close:hover{color:#111827}.stakeholder-add-modal-body{padding:1.5rem}.stakeholder-add-modal-footer{display:flex;justify-content:flex-end;gap:0.5rem;padding:1rem 1.5rem;border-top:1px solid #e5e7eb;background:#f9fafb;border-radius:0 0 8px 8px}`;
            document.head.appendChild(styles);
        }
    }
    
    function closeAddModal() { const modal = document.getElementById('addStakeholderModal'); if (modal) modal.remove(); }
    
    async function saveNewStakeholder() {
        const roleId = document.getElementById('newStakeholderRole').value;
        const peopleId = document.getElementById('newStakeholderPerson').value;
        const statusId = document.getElementById('newStakeholderStatus').value;
        const delegateIpId = document.getElementById('newStakeholderDelegate').value;
        
        if (!roleId) { alert('Please select a Role'); return; }
        if (!peopleId) { alert('Please select a Person'); return; }
        if (!statusId) { alert('Please select a Role Status'); return; }
        if (stakeholdersData.some(s => s.roleId == roleId && s.peopleId == peopleId)) { alert('This person is already assigned to this role'); return; }
        
        const saveBtn = document.getElementById('saveNewStakeholderBtn');
        saveBtn.disabled = true; saveBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Adding...';
        
        try {
            const selectedRole = rolesData.find(r => String(r.id) === String(roleId));
            const selectedStatus = roleStatusesData.find(s => String(s.id) === String(statusId));
            const selectedPerson = peopleData.find(p => String(p.id) === String(peopleId));
            const selectedDelegate = stakeholdersData.find(s => String(s.objectXPeopleId) === String(delegateIpId));

            const personSelectEl = document.getElementById('newStakeholderPerson');
            const personNameFallback = personSelectEl
                ? (personSelectEl.options[personSelectEl.selectedIndex]?.text || '')
                : '';

            stakeholdersData.push({
                objectXPeopleId: null,
                peopleId: peopleId ? parseInt(peopleId, 10) : null,
                personName: selectedPerson ? selectedPerson.name : personNameFallback,
                roleId: roleId ? parseInt(roleId, 10) : null,
                roleName: selectedRole ? selectedRole.name : '',
                statusId: statusId ? parseInt(statusId, 10) : null,
                statusName: selectedStatus ? selectedStatus.name : '',
                personEmail: '',
                delegateOf: selectedDelegate ? `${selectedDelegate.personName} - ${selectedDelegate.roleName}` : '',
                delegateIpId: delegateIpId ? parseInt(delegateIpId, 10) : null,
                roleAssignmentValid: true,
                roleAssignmentWarning: null
            });

            closeAddModal();
            renderEditableStakeholdersTable();
            refreshAllDelegateDropdowns();
        } catch (error) {
            alert('Error adding stakeholder: ' + (error.message || 'Unknown error'));
            saveBtn.disabled = false; saveBtn.innerHTML = '<i class="fas fa-save"></i> Save';
        }
    }

    // Add new stakeholder row (legacy)
    function addNewRow() { showAddStakeholderForm(); }

    // Add new stakeholder row after specific index (legacy)
    function addNewRowAfter(index) {
        const newStakeholder = {
            objectXPeopleId: null, // Will be assigned by backend
            peopleId: null,
            personName: '',
            roleId: null,
            roleName: '',
            statusId: null,
            statusName: '',
            personEmail: '',
            delegateOf: '',
            delegateIpId: null
        };

        // Insert after the specified index
        stakeholdersData.splice(index + 1, 0, newStakeholder);
        renderEditableStakeholdersTable();
        refreshAllDelegateDropdowns();
        console.log('Added new stakeholder row after index:', index);

        // Update save buttons in parent form
        if (window.updateSaveButtons) {
            window.updateSaveButtons();
        }
    }

    // Update stakeholder data
    function updateStakeholder(index, field, value) {
        if (index >= 0 && index < stakeholdersData.length) {
            // Convert value to appropriate type
            let convertedValue = value;
            if (field === 'roleId' || field === 'peopleId' || field === 'statusId') {
                convertedValue = value ? parseInt(value, 10) : null;
            }

            stakeholdersData[index][field] = convertedValue;

            // Update related fields based on selection
            if (field === 'roleId' && value) {
                const role = rolesData.find(r => r.id == value);
                stakeholdersData[index].roleName = role ? role.name : '';
                // Clear person selection when role changes
                stakeholdersData[index].peopleId = null;
                stakeholdersData[index].personName = '';

                // Update the UI to reflect the changes
                const row = document.querySelector(`tr[data-index="${index}"]`);
                if (row) {
                    const peopleSelect = row.querySelector('.stakeholder-people');
                    if (peopleSelect) {
                        peopleSelect.value = '';
                        peopleSelect.setAttribute('data-person-name', '');
                    }
                }
            } else if (field === 'peopleId' && value) {
                // Try to find person in peopleData first
                let person = peopleData.find(p => p.id == value);

                // If not found in peopleData, try to extract from dropdown option
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

                if (person) {
                    stakeholdersData[index].personName = person.name;
                    console.log(`Updated person name for index ${index} to: ${person.name}`);

                    // Update the data attribute in the UI
                    const row = document.querySelector(`tr[data-index="${index}"]`);
                    if (row) {
                        const peopleSelect = row.querySelector('.stakeholder-people');
                        if (peopleSelect) {
                            peopleSelect.setAttribute('data-person-name', person.name);
                        }
                        // Also update the row's data attribute
                        row.setAttribute('data-person-name', person.name);
                    }
                } else {
                    console.warn(`Could not find person with ID ${value} in peopleData`);
                    stakeholdersData[index].personName = '';
                }
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

            // Check for duplicates after update
            checkForDuplicates();

            console.log('Updated stakeholder:', index, field, value);
            console.log('Converted value:', convertedValue);
            console.log('Current stakeholder data:', stakeholdersData[index]);
            console.log('Has data changed:', hasDataChanged());

            // Update save buttons in parent form
            if (window.updateSaveButtons) {
                window.updateSaveButtons();
            }
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

    // Check for duplicates and highlight them
    function checkForDuplicates() {
        // Clear previous duplicate highlighting
        document.querySelectorAll('.duplicate-error').forEach(el => {
            el.classList.remove('duplicate-error');
        });

        // Check for duplicate role-person combinations
        const rolePersonCombinations = [];
        const duplicateRows = [];

        stakeholdersData.forEach((stakeholder, index) => {
            if (stakeholder.roleId && stakeholder.peopleId) {
                const combo = `${stakeholder.roleId}-${stakeholder.peopleId}`;
                const existingIndex = rolePersonCombinations.indexOf(combo);
                if (existingIndex !== -1) {
                    duplicateRows.push(index);
                    duplicateRows.push(existingIndex);
                } else {
                    rolePersonCombinations.push(combo);
                }
            }
        });

        // Highlight duplicate rows
        duplicateRows.forEach(index => {
            const row = document.querySelector(`tr[data-index="${index}"]`);
            if (row) {
                row.classList.add('duplicate-error');
            }
        });

        // Check for duplicate full combinations
        const fullCombinations = [];
        const duplicateFullRows = [];

        stakeholdersData.forEach((stakeholder, index) => {
            if (stakeholder.roleId && stakeholder.peopleId && stakeholder.statusId) {
                const fullCombo = `${stakeholder.roleId}-${stakeholder.peopleId}-${stakeholder.statusId}`;
                const existingIndex = fullCombinations.indexOf(fullCombo);
                if (existingIndex !== -1) {
                    duplicateFullRows.push(index);
                    duplicateFullRows.push(existingIndex);
                } else {
                    fullCombinations.push(fullCombo);
                }
            }
        });

        // Highlight duplicate full combination rows
        duplicateFullRows.forEach(index => {
            const row = document.querySelector(`tr[data-index="${index}"]`);
            if (row) {
                row.classList.add('duplicate-error');
            }
        });
    }

    // Delete stakeholder locally (persist on Save / Save & Close)
    async function deleteStakeholder(index) {
        if (index >= 0 && index < stakeholdersData.length) {
            const stakeholder = stakeholdersData[index];
            
            if (!canEditStakeholders) { alert('You do not have permission to delete stakeholders'); return; }
            
            const confirmMessage = `Are you sure you want to delete "${stakeholder.personName || 'this stakeholder'}" as "${stakeholder.roleName || 'stakeholder'}"?`;
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmed) {
                try {
                    stakeholdersData.splice(index, 1);
                    renderEditableStakeholdersTable();
                    refreshAllDelegateDropdowns();
                } catch (error) { alert('Error deleting stakeholder: ' + (error.message || 'Unknown error')); }
            }
        }
    }

    // Validate stakeholders data
    function validateStakeholders() {
        const errors = [];

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
                    errors.push(`Row ${rowNum}: Selected person is invalid`);
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
                    }
                }
            }

            // Validate role name is not empty (additional check)
            if (!stakeholder.roleName || stakeholder.roleName.trim() === '') {
                errors.push(`Row ${rowNum}: Role name cannot be empty`);
            }
        });

        // Check for duplicate combinations (Role + Person)
        const rolePersonCombinations = [];
        const duplicateRolePersonRows = [];

        stakeholdersData.forEach((stakeholder, index) => {
            if (stakeholder.roleId && stakeholder.peopleId) {
                const combo = `${stakeholder.roleId}-${stakeholder.peopleId}`;
                const existingIndex = rolePersonCombinations.indexOf(combo);
                if (existingIndex !== -1) {
                    duplicateRolePersonRows.push(`Row ${index + 1} and Row ${existingIndex + 1}`);
                } else {
                    rolePersonCombinations.push(combo);
                }
            }
        });

        if (duplicateRolePersonRows.length > 0) {
            errors.push(`Duplicate role-person combinations found: ${duplicateRolePersonRows.join(', ')}. Each person can only have one role.`);
        }

        // Check for duplicate full combinations (Role + Person + Status)
        const fullCombinations = [];
        const duplicateFullRows = [];

        stakeholdersData.forEach((stakeholder, index) => {
            if (stakeholder.roleId && stakeholder.peopleId && stakeholder.statusId) {
                const fullCombo = `${stakeholder.roleId}-${stakeholder.peopleId}-${stakeholder.statusId}`;
                const existingIndex = fullCombinations.indexOf(fullCombo);
                if (existingIndex !== -1) {
                    duplicateFullRows.push(`Row ${index + 1} and Row ${existingIndex + 1}`);
                } else {
                    fullCombinations.push(fullCombo);
                }
            }
        });

        if (duplicateFullRows.length > 0) {
            errors.push(`Duplicate full combinations (Role + Person + Status) found: ${duplicateFullRows.join(', ')}`);
        }

        // Note: Duplicate roles are allowed as different people can have the same role

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
                    <p class="mb-2">Please fix the following errors before saving:</p>
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
        console.log('Checking data changes...');
        console.log('Current stakeholdersData:', stakeholdersData);
        console.log('Original data:', originalData);

        // Compare each stakeholder individually
        if (stakeholdersData.length !== originalData.length) {
            console.log('Length changed:', stakeholdersData.length, 'vs', originalData.length);
            return true;
        }

        for (let i = 0; i < stakeholdersData.length; i++) {
            const current = stakeholdersData[i];
            const original = originalData[i];

            if (!original) {
                console.log('New stakeholder at index', i);
                return true;
            }

            // Check if any field has changed
            const fieldsToCheck = ['roleId', 'peopleId', 'statusId', 'objectXPeopleId'];
            for (const field of fieldsToCheck) {
                const currentValue = current[field];
                const originalValue = original[field];

                // Convert both to numbers for comparison
                const currentNum = currentValue ? parseInt(currentValue, 10) : null;
                const originalNum = originalValue ? parseInt(originalValue, 10) : null;

                if (currentNum !== originalNum) {
                    console.log(`Field ${field} changed:`, originalNum, '->', currentNum);
                    return true;
                }
            }
        }

        console.log('No changes detected');
        return false;
    }

    // Save stakeholders data
    async function saveStakeholders() {
        try {
            console.log('Saving stakeholders...');

            // Show loading state
            console.log('Starting save operation...');

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
            const dataChanged = hasDataChanged();
            console.log('Data changed check result:', dataChanged);

            if (!dataChanged) {
                alert('No changes detected in stakeholders data.');
                return true;
            }

            // Confirm save action
            const confirmMessage = `Are you sure you want to save ${stakeholdersData.length} stakeholder(s)?`;
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (!confirmed) {
                return false;
            }

            // Determine what operations need to be performed
            const operations = determineOperations();
            console.log('Operations to perform:', operations);

            // Log detailed information about operations
            console.log('Deletions:', operations.deletions);
            console.log('Updates:', operations.updates);
            console.log('Insertions:', operations.insertions);

            // Perform operations
            let allSuccess = true;

            try {
                // Handle deletions first
                for (const deletion of operations.deletions) {
                    console.log('Processing deletion:', deletion);
                    const success = await deleteStakeholderAPI(deletion.objectXPeopleId);
                    if (!success) {
                        console.error('Failed to delete stakeholder:', deletion);
                        allSuccess = false;
                        break;
                    }
                }

                // Only proceed with updates and insertions if deletions were successful
                if (allSuccess) {
                    // Handle updates
                    for (const update of operations.updates) {
                        console.log('Processing update:', update);
                        const success = await updateStakeholderAPI(update);
                        if (!success) {
                            console.error('Failed to update stakeholder:', update);
                            allSuccess = false;
                            break;
                        }
                    }

                    // Only proceed with insertions if updates were successful
                    if (allSuccess) {
                        // Handle insertions
                        for (const insertion of operations.insertions) {
                            console.log('Processing insertion:', insertion);
                            const success = await addStakeholderAPI(insertion);
                            if (!success) {
                                console.error('Failed to add stakeholder:', insertion);
                                allSuccess = false;
                                break;
                            }
                        }
                    }
                }
            } catch (operationError) {
                console.error('Error during operations:', operationError);
                allSuccess = false;
            }

            if (allSuccess) {
                // Update original data after successful save
                originalData = JSON.parse(JSON.stringify(stakeholdersData));
                alert('Stakeholders data saved successfully!');

                // Reload data to get updated IDs
                await loadStakeholdersData();
                await loadPeopleForExistingRoles();
                renderEditableStakeholdersTable();
                refreshAllDelegateDropdowns();

                return true;
            } else {
                throw new Error('Some operations failed. Please check the console for more details.');
            }

        } catch (error) {
            console.error('Error saving stakeholders:', error);
            alert('Error saving stakeholders: ' + (error.message || 'Unknown error'));
            return false;
        } finally {
            // Restore loading state
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

        // Validate process ID
        if (!currentProcessId || currentProcessId <= 0) {
            errors.push('Invalid process ID. Please refresh the page and try again.');
        }

        // Business rules validation removed

        return errors;
    }

    // Determine what operations need to be performed
    function determineOperations() {
        console.log('Determining operations...');
        console.log('Current data:', stakeholdersData);
        console.log('Original data:', originalData);

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
                    console.log('Deletion found:', original);
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
                        console.log('Existing stakeholder with status change found:', current);
                        operations.updates.push({
                            ...current,
                            objectXPeopleId: existingStakeholder.objectXPeopleId
                        });
                    }
                    // If unchanged, skip it (don't add to insertions)
                    console.log('Skipping existing stakeholder (unchanged):', current);
                } else {
                    // Truly new stakeholder
                    console.log('Insertion found:', current);
                    operations.insertions.push(current);
                }
            } else {
                // Existing item - check if changed
                const original = originalData.find(orig =>
                    orig.objectXPeopleId === current.objectXPeopleId
                );
                if (original) {
                    // Convert values to numbers for comparison
                    const currentRoleId = current.roleId ? parseInt(current.roleId, 10) : null;
                    const originalRoleId = original.roleId ? parseInt(original.roleId, 10) : null;
                    const currentPeopleId = current.peopleId ? parseInt(current.peopleId, 10) : null;
                    const originalPeopleId = original.peopleId ? parseInt(original.peopleId, 10) : null;
                    const currentStatusId = current.statusId ? parseInt(current.statusId, 10) : null;
                    const originalStatusId = original.statusId ? parseInt(original.statusId, 10) : null;

                    const currentDelegateIpId = current.delegateIpId ? parseInt(current.delegateIpId, 10) : null;
                    const originalDelegateIpId = original.delegateIpId ? parseInt(original.delegateIpId, 10) : null;

                    const hasChanged = (
                        currentRoleId !== originalRoleId ||
                        currentPeopleId !== originalPeopleId ||
                        currentStatusId !== originalStatusId ||
                        currentDelegateIpId !== originalDelegateIpId
                    );

                    console.log('Comparing stakeholder:', {
                        current: { roleId: currentRoleId, peopleId: currentPeopleId, statusId: currentStatusId },
                        original: { roleId: originalRoleId, peopleId: originalPeopleId, statusId: originalStatusId },
                        hasChanged
                    });

                    if (hasChanged) {
                        console.log('Update found:', current);
                        operations.updates.push(current);
                    }
                }
            }
        });

        console.log('Operations determined:', operations);
        return operations;
    }

    // API call to add stakeholder
    async function addStakeholderAPI(stakeholder) {
        try {
            console.log('Adding stakeholder:', stakeholder);
            const payload = {
                ipid: stakeholder.peopleId,
                roleID: stakeholder.roleId,
                statusID: stakeholder.statusId,
                delegateIpId: stakeholder.delegateIpId
            };
            console.log('Add payload:', payload);

            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/process-stakeholder/${currentProcessId}/stakeholders/edit${viewParam}`, {
                method: 'POST',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            return true;
        } catch (error) {
            console.error('Error adding stakeholder:', error);
            return false;
        }
    }

    // API call to update stakeholder
    async function updateStakeholderAPI(stakeholder) {
        try {
            console.log('Updating stakeholder:', stakeholder);
            const payload = {
                objectXPeopleId: stakeholder.objectXPeopleId,
                ipid: stakeholder.peopleId,
                roleID: stakeholder.roleId,
                statusID: stakeholder.statusId,
                delegateIpId: stakeholder.delegateIpId
            };
            console.log('Update payload:', payload);

            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/process-stakeholder/${currentProcessId}/stakeholders/edit${viewParam}`, {
                method: 'PUT',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            return true;
        } catch (error) {
            console.error('Error updating stakeholder:', error);
            return false;
        }
    }

    // API call to delete stakeholder
    async function deleteStakeholderAPI(objectXPeopleId) {
        try {
            console.log(`Deleting stakeholder with objectXPeopleId: ${objectXPeopleId}`);

            // Make sure we have a valid objectXPeopleId
            if (!objectXPeopleId) {
                console.error('Cannot delete stakeholder: Missing objectXPeopleId');
                return false;
            }

            const viewParam = currentViewMode === 'changes' ? '&view=changes' : '';
            const url = `/api/process-stakeholder/${currentProcessId}/stakeholders/edit?objectXPeopleId=${objectXPeopleId}${viewParam}`;
            console.log('DELETE request URL:', url);

            const response = await fetch(url, {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            console.log('DELETE response status:', response.status);

            if (!response.ok) {
                let errorMessage = `HTTP error! status: ${response.status}`;
                try {
                    const errorData = await response.json();
                    errorMessage = errorData.error || errorMessage;
                } catch (e) {
                    console.warn('Could not parse error response as JSON:', e);
                }
                throw new Error(errorMessage);
            }

            console.log('Successfully deleted stakeholder with objectXPeopleId:', objectXPeopleId);
            return true;
        } catch (error) {
            console.error('Error deleting stakeholder:', error);
            alert(`Failed to delete stakeholder: ${error.message}`);
            return false;
        }
    }

    // Escape HTML to prevent XSS
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    // Export functions to global scope
    window.ProcessStakeholderEdit = {
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
