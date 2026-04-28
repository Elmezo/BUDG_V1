// Business Area Stakeholder Edit JavaScript - New Implementation
(function() {
    let currentBusinessAreaId = null;
    let stakeholdersData = [];
    let originalData = [];
    let rolesData = [];
    let roleStatusesData = [];
    /** Admin role assignments only: roleId string -> [{ id, name }, ...] */
    let peopleByRoleId = {};

    function roleKey(roleId) {
        return roleId == null || roleId === '' ? '' : String(roleId);
    }

    function getPeopleForRole(roleId) {
        return peopleByRoleId[roleKey(roleId)] || [];
    }

    function setPeopleForRole(roleId, people) {
        peopleByRoleId[roleKey(roleId)] = Array.isArray(people) ? people.slice() : [];
    }

    function matchPersonByNameInList(personName, list) {
        if (!personName || !Array.isArray(list) || list.length === 0) {
            return null;
        }
        let person = list.find(p => p.name === personName);
        if (!person) {
            person = list.find(p => {
                const cleanPersonName = p.name.replace(/\s*\([^)]*\)\s*$/, '').trim();
                const cleanStakeholderName = personName.replace(/\s*\([^)]*\)\s*$/, '').trim();
                return cleanPersonName === cleanStakeholderName;
            });
        }
        if (!person) {
            person = list.find(p =>
                p.name.toLowerCase().includes(personName.toLowerCase()) ||
                personName.toLowerCase().includes(p.name.toLowerCase())
            );
        }
        return person || null;
    }

    let isSaving = false;
    let currentUserId = null;

    async function fetchCurrentUser() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (response.ok) {
                const userData = await response.json();
                const id = userData.id ?? userData.userId ?? userData.peopleId;
                currentUserId = id != null ? parseInt(id, 10) : null;
                if (!Number.isInteger(currentUserId) || currentUserId <= 0) {
                    currentUserId = null;
                }
            }
        } catch (e) {
            console.warn('business-area-stakeholder-edit: could not load /api/me', e);
            currentUserId = null;
        }
    }

    // Initialize stakeholder edit
    function initStakeholderEdit(businessAreaId) {
        currentBusinessAreaId = businessAreaId;
        console.log('=== INITIALIZING STAKEHOLDER EDIT ===');
        console.log('Business Area ID:', businessAreaId);
        console.log('Current Business Area ID set to:', currentBusinessAreaId);
        loadStakeholdersForEdit();
    }

    // Load stakeholders data for editing
    async function loadStakeholdersForEdit() {
        try {
            console.log('=== LOADING STAKEHOLDERS FOR EDIT ===');
            console.log('Looking for container: stakeholdersContainer');
            const container = document.getElementById('stakeholdersContainer');
            if (!container) {
                console.error('Stakeholders container not found');
                return;
            }
            console.log('Container found:', container);

            container.innerHTML = '<div class="loading">Loading stakeholders for editing...</div>';

            await fetchCurrentUser();

            // Load dropdown data first (without loading all people)
            console.log('Loading dropdown data...');
            await Promise.all([
                loadRoles(),
                loadRoleStatuses()
            ]);
            console.log('Dropdown data loaded');

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
            const container = document.getElementById('stakeholdersContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading stakeholders data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Load stakeholders data from API
    async function loadStakeholdersData() {
        try {
            console.log('=== LOADING STAKEHOLDERS DATA FROM API ===');
            console.log('Loading stakeholders data for business area:', currentBusinessAreaId);

            const apiUrl = `/api/businessarea-stakeholder/${currentBusinessAreaId}/stakeholders/edit`;
            console.log('API URL:', apiUrl);

            const response = await fetch(apiUrl, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            console.log('Response status:', response.status);
            console.log('Response ok:', response.ok);

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const responseData = await response.json();
            console.log('=== STAKEHOLDERS API RESPONSE ===');
            console.log('Stakeholders API response:', responseData);

            // Handle different API response formats
            const data = responseData.data || responseData;
            console.log('Extracted data:', data);
            console.log('Data is array:', Array.isArray(data));

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
            const response = await fetch('/api/businessarea/stakeholder/lookup?type=roles', {
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
            const response = await fetch('/api/businessarea/stakeholder/lookup?type=rolestatus', {
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


    // Load people by role
    async function loadPeopleByRole(roleId) {
        try {
            console.log('Loading people for role:', roleId);
            const response = await fetch(`/api/businessarea/stakeholder/lookup?type=people&roleId=${roleId}&objectId=${currentBusinessAreaId}`, {
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

            peopleByRoleId = {};

            const existingRoleIds = [...new Set(stakeholdersData
                .filter(s => s.roleId)
                .map(s => s.roleId))];

            console.log('Existing role IDs:', existingRoleIds);

            for (const roleId of existingRoleIds) {
                const people = await loadPeopleByRole(roleId);
                setPeopleForRole(roleId, people);
            }

            stakeholdersData.forEach(stakeholder => {
                if (!stakeholder.roleId || !stakeholder.personName) {
                    return;
                }
                if (stakeholder.peopleId && stakeholder.peopleId !== 'null') {
                    return;
                }
                const list = getPeopleForRole(stakeholder.roleId);
                const person = matchPersonByNameInList(stakeholder.personName, list);
                if (person) {
                    stakeholder.peopleId = person.id;
                    console.log('Found people ID for', stakeholder.personName, ':', person.id, 'matched with:', person.name);
                } else {
                    console.warn('Could not find people ID for:', stakeholder.personName, 'within admin-assigned users for role', stakeholder.roleId);
                }
            });

            console.log('Loaded people per role:', peopleByRoleId);
        } catch (error) {
            console.error('Error loading people for existing roles:', error);
            peopleByRoleId = {};
        }
    }


    // Render editable stakeholders table
    function renderEditableStakeholdersTable() {
        console.log('=== RENDERING EDITABLE STAKEHOLDERS TABLE ===');
        const container = document.getElementById('stakeholdersContainer');
        if (!container) {
            console.error('Stakeholders container not found for rendering');
            return;
        }
        console.log('Container found for rendering:', container);
        console.log('Stakeholders data to render:', stakeholdersData);

        // Check for invalid role assignments
        const invalidAssignments = stakeholdersData.filter(s => s.roleAssignmentValid === false);
        let warningBanner = '';
        if (invalidAssignments.length > 0) {
            warningBanner = `
                <div class="alert alert-warning mb-3" role="alert">
                    <div class="d-flex align-items-center">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        <strong>Warning:</strong> Some stakeholders have roles they are not assigned to.
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
                    <h3>Business Area Stakeholders</h3>
                    <div class="stakeholders-actions">
                        <button type="button" class="btn btn-primary btn-sm" onclick="BusinessAreaStakeholderEdit.addNewRow()">
                            <i class="fas fa-plus"></i> Add Stakeholder
                        </button>
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
        
        // Refresh delegate dropdowns after rendering
        refreshAllDelegateDropdowns();
    }

    // Get available delegates: same business area stakeholders with the same role only
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
            const rolePeople = getPeopleForRole(stakeholder.roleId);
            const selectedId = stakeholder.peopleId != null && stakeholder.peopleId !== '' && stakeholder.peopleId !== 'null'
                ? String(stakeholder.peopleId)
                : '';
            const idsInRole = new Set(rolePeople.map(p => String(p.id)));

            rolePeople.forEach(person => {
                const sel = selectedId && String(person.id) === selectedId ? ' selected' : '';
                peopleOptions += `<option value="${person.id}"${sel}>${escapeHtml(person.name)}</option>`;
            });

            if (selectedId && !idsInRole.has(selectedId) && stakeholder.personName) {
                peopleOptions += `<option value="${stakeholder.peopleId}" selected>${escapeHtml(stakeholder.personName)}</option>`;
            }
        }

        // Add a data attribute for person name to help with debugging
        const personNameAttr = stakeholder.personName ?
            `data-person-name="${escapeHtml(stakeholder.personName)}"` : '';

        // Add warning class if role assignment is invalid
        const warningClass = (stakeholder.roleAssignmentValid === false) ? 'role-assignment-warning' : '';
        const warningIcon = (stakeholder.roleAssignmentValid === false) ? 
            `<i class="fas fa-exclamation-triangle text-warning" title="${escapeHtml(stakeholder.roleAssignmentWarning || 'The user is not assigned to this role. Select another user that is assigned to this role.')}"></i>` : '';

        // Build delegate options - only show stakeholders already in the current business area
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
                                onchange="BusinessAreaStakeholderEdit.updateStakeholder(${index}, 'roleId', this.value)"
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
                            onchange="BusinessAreaStakeholderEdit.updateStakeholder(${index}, 'peopleId', this.value)"
                            data-original-value="${stakeholder.peopleId || ''}"
                            data-person-name="${escapeHtml(stakeholder.personName || '')}"
                            ${!stakeholder.roleId ? 'disabled' : ''}>
                        ${peopleOptions}
                    </select>
                </td>
                <td>
                    <select class="form-select stakeholder-status"
                            onchange="BusinessAreaStakeholderEdit.updateStakeholder(${index}, 'statusId', this.value)"
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
                            onchange="BusinessAreaStakeholderEdit.updateStakeholder(${index}, 'delegateIpId', this.value)"
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
                        <button type="button"
                                class="btn btn-success btn-sm"
                                onclick="BusinessAreaStakeholderEdit.addNewRowAfter(${index})"
                                title="Add stakeholder after this row">
                            <i class="fas fa-plus"></i>
                        </button>
                        <button type="button"
                                class="btn btn-danger btn-sm"
                                onclick="BusinessAreaStakeholderEdit.deleteStakeholder(${index})"
                                title="Delete stakeholder">
                            <i class="fas fa-minus"></i>
                        </button>
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
                    const peopleForRole = await loadPeopleByRole(roleId);
                    setPeopleForRole(roleId, peopleForRole || []);

                    if (!peopleForRole || peopleForRole.length === 0) {
                        peopleSelect.innerHTML = '';
                        const opt = document.createElement('option');
                        opt.value = ''; opt.disabled = true; opt.selected = true;
                        opt.textContent = 'No users for this role — check Admin > Role Assignment or segment access';
                        peopleSelect.appendChild(opt);
                        peopleSelect.disabled = true;
                    } else {
                        peopleSelect.disabled = false;
                        peopleSelect.innerHTML = '<option value="">Select Person</option>';
                        peopleForRole.forEach(person => {
                            const option = document.createElement('option');
                            option.value = person.id;
                            option.textContent = person.name;
                            peopleSelect.appendChild(option);
                        });
                    }

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
                BusinessAreaStakeholderEdit.updateStakeholder(index, 'delegateIpId', delegateIpId);
            });
        });
    }

    // Add new stakeholder row
    function addNewRow() {
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

        stakeholdersData.push(newStakeholder);
        renderEditableStakeholdersTable();
        refreshAllDelegateDropdowns();
        console.log('Added new stakeholder row');
    }

    // Add new stakeholder row after specific index
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
    function updateStakeholder(index, field, value) {
        if (index >= 0 && index < stakeholdersData.length) {
            stakeholdersData[index][field] = value || null;

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
                const rid = stakeholdersData[index].roleId;
                let person = getPeopleForRole(rid).find(p => String(p.id) === String(value));

                if (!person) {
                    const row = document.querySelector(`tr[data-index="${index}"]`);
                    if (row) {
                        const peopleSelect = row.querySelector('.stakeholder-people');
                        if (peopleSelect) {
                            const selectedOption = peopleSelect.querySelector(`option[value="${value}"]`);
                            if (selectedOption) {
                                const personName = selectedOption.textContent;
                                person = { id: value, name: personName };
                                const list = getPeopleForRole(rid).slice();
                                if (!list.find(p => String(p.id) === String(value))) {
                                    list.push(person);
                                    setPeopleForRole(rid, list);
                                }
                                console.log(`Person merged into role cache from dropdown: ${personName}`);
                            }
                        }
                    }
                }

                if (person) {
                    stakeholdersData[index].personName = person.name;
                    console.log(`Updated person name for index ${index} to: ${person.name}`);

                    const row = document.querySelector(`tr[data-index="${index}"]`);
                    if (row) {
                        const peopleSelect = row.querySelector('.stakeholder-people');
                        if (peopleSelect) {
                            peopleSelect.setAttribute('data-person-name', person.name);
                        }
                        row.setAttribute('data-person-name', person.name);
                    }
                } else {
                    console.warn(`Could not find person with ID ${value} for role ${rid}`);
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

            console.log('Updated stakeholder:', index, field, value);
            console.log('Current stakeholder data:', stakeholdersData[index]);
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
            clearStakeholderValidationErrors();
        }
    }

    // Delete stakeholder
    async function deleteStakeholder(index) {
        if (index >= 0 && index < stakeholdersData.length) {
            const stakeholder = stakeholdersData[index];
            const confirmMessage = `Are you sure you want to delete "${stakeholder.personName || 'this stakeholder'}"?`;

            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmed) {
                stakeholdersData.splice(index, 1);
                renderEditableStakeholdersTable();
                refreshAllDelegateDropdowns();
                console.log('Deleted stakeholder at index:', index);
            }
        }
    }

    // Validate stakeholders data
    function validateStakeholders() {
        const errors = [];

        // Allow zero stakeholders; no error if list is empty

        // Determine what operations will be performed
        const operations = determineOperations();
        
        // Create sets for quick lookup
        const updatesSet = new Set(operations.updates.map(s => s.objectXPeopleId).filter(id => id !== null));
        const deletionsSet = new Set(operations.deletions.map(s => s.objectXPeopleId).filter(id => id !== null));
        
        // For insertions, we need to identify them by checking if they're new (no objectXPeopleId)
        // and match them with operations.insertions by comparing key fields
        const isInsertion = (stakeholder) => {
            if (stakeholder.objectXPeopleId) return false; // Has ID, so it's existing
            // Check if this matches any insertion by comparing peopleId, roleId, and statusId
            return operations.insertions.some(ins => 
                ins.peopleId == stakeholder.peopleId &&
                ins.roleId == stakeholder.roleId &&
                ins.statusId == stakeholder.statusId
            );
        };

        stakeholdersData.forEach((stakeholder, index) => {
            const rowNum = index + 1;
            
            // Skip validation if this stakeholder is being deleted
            if (stakeholder.objectXPeopleId && deletionsSet.has(stakeholder.objectXPeopleId)) {
                return; // Skip validation for stakeholders being deleted
            }
            
            // Check if this stakeholder is being inserted or updated
            const isBeingInserted = isInsertion(stakeholder);
            const isBeingUpdated = stakeholder.objectXPeopleId && updatesSet.has(stakeholder.objectXPeopleId);
            
            // Only validate stakeholders that are being added or updated
            if (!isBeingInserted && !isBeingUpdated) {
                return; // Skip validation for unchanged existing stakeholders
            }

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
                const rolePeople = getPeopleForRole(stakeholder.roleId);
                const personExists = rolePeople.some(p => String(p.id) === String(stakeholder.peopleId))
                    || (rolePeople.length === 0 && stakeholder.peopleId);
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

            // Validate role assignment for default roles (only for stakeholders being added/updated)
            if (stakeholder.roleAssignmentValid === false && stakeholder.roleAssignmentWarning) {
                errors.push(`Row ${rowNum}: ${stakeholder.roleAssignmentWarning}`);
            }

            // Validate person name is not empty only if peopleId is set
            if (stakeholder.peopleId && (!stakeholder.personName || stakeholder.personName.trim() === '')) {
                errors.push(`Row ${rowNum}: Person name is missing for selected person`);
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
        
        // Check for duplicate objectXPeopleId (existing stakeholders)
        const existingIds = [];
        const duplicateIds = [];
        
        stakeholdersData.forEach((stakeholder, index) => {
            if (stakeholder.objectXPeopleId) {
                if (existingIds.includes(stakeholder.objectXPeopleId)) {
                    duplicateIds.push(`Row ${index + 1}`);
                } else {
                    existingIds.push(stakeholder.objectXPeopleId);
                }
            }
        });
        
        if (duplicateIds.length > 0) {
            errors.push(`Duplicate existing stakeholders found: ${duplicateIds.join(', ')}`);
        }

        return errors;
    }

    // Show validation errors (stakeholder-specific)
    function showStakeholderValidationErrors(errors, isSaveError = false) {
        const errorContainer = document.getElementById('stakeholdersValidationErrors');
        if (errorContainer && errors.length > 0) {
            const title = isSaveError ? 'Stakeholder Save Errors' : 'Stakeholder Validation Errors';
            const message = isSaveError 
                ? 'The following errors occurred while saving stakeholders:'
                : 'Please fix the following stakeholder errors before saving:';
            
            errorContainer.innerHTML = `
                <div class="alert alert-danger" role="alert">
                    <div class="d-flex align-items-center">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        <h5 class="mb-0">${title}</h5>
                    </div>
                    <hr class="my-2">
                    <p class="mb-2">${message}</p>
                    <ul class="mb-0">
                        ${errors.map(error => `<li class="mb-1">${escapeHtml(error)}</li>`).join('')}
                    </ul>
                    ${!isSaveError ? `
                    <hr class="my-2">
                    <small class="text-muted">
                        <i class="fas fa-info-circle"></i>
                        All stakeholder fields marked with <span class="text-danger">*</span> are required.
                    </small>
                    ` : ''}
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

    // Clear stakeholder validation errors
    function clearStakeholderValidationErrors() {
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
    async function saveStakeholders(showConfirmation = true) {
        // Prevent double save operations
        if (isSaving) {
            console.log('Stakeholders save operation already in progress, ignoring duplicate request');
            return false;
        }
        
        isSaving = true;
        
        try {
            console.log('Saving stakeholders...');

            // Show loading state
            console.log('Starting save operation...');

            // Clear previous validation errors
            clearStakeholderValidationErrors();

            // Comprehensive validation
            const errors = validateStakeholders();
            if (errors.length > 0) {
                showStakeholderValidationErrors(errors);
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
                showStakeholderValidationErrors(preCheckErrors);
                return false;
            }

            // Check if data has changed
            if (!hasDataChanged()) {
                console.log('No changes detected in stakeholders data.');
                return true;
            }

            // Determine what operations need to be performed
            const operations = determineOperations();
            console.log('Operations to perform:', operations);

            // Log detailed information about operations
            console.log('Deletions:', operations.deletions);
            console.log('Updates:', operations.updates);
            console.log('Insertions:', operations.insertions);

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
                    await deleteStakeholderAPI(deletion.objectXPeopleId);
                    results.deletions.success++;
                } catch (error) {
                    results.deletions.failed++;
                    const errorMsg = error.message || 'Unknown error';
                    results.deletions.errors.push(`Error deleting stakeholder: ${errorMsg}`);
                    console.error('Deletion error:', error);
                }
            }

            // Handle updates
            console.log(`Processing ${operations.updates.length} updates...`);
            for (const update of operations.updates) {
                try {
                    console.log('Updating stakeholder:', update.objectXPeopleId);
                    await updateStakeholderAPI(update);
                    results.updates.success++;
                } catch (error) {
                    results.updates.failed++;
                    const errorMsg = error.message || 'Unknown error';
                    results.updates.errors.push(`Error updating stakeholder ${update.objectXPeopleId}: ${errorMsg}`);
                    console.error('Update error:', error);
                }
            }

            // Handle insertions
            console.log(`Processing ${operations.insertions.length} insertions...`);
            for (const insertion of operations.insertions) {
                try {
                    console.log('Adding new stakeholder:', insertion);
                    await addStakeholderAPI(insertion);
                    results.insertions.success++;
                } catch (error) {
                    results.insertions.failed++;
                    const errorMsg = error.message || 'Unknown error';
                    results.insertions.errors.push(`Error adding stakeholder: ${errorMsg}`);
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

                // Reload data to get updated IDs
                await loadStakeholdersData();
                await loadPeopleForExistingRoles();
                renderEditableStakeholdersTable();
                refreshAllDelegateDropdowns();

                return true;
            } else {
                // Some operations failed - show detailed error message
                const allErrors = [
                    ...results.deletions.errors,
                    ...results.updates.errors,
                    ...results.insertions.errors
                ];
                
                const errorMessage = `Some stakeholder operations failed:\n\n` +
                    `Successful: ${totalSuccess}\n` +
                    `Failed: ${totalFailed}\n\n` +
                    `Errors:\n${allErrors.join('\n')}`;

                console.error('Save operation failed:', errorMessage);
                
                // Show error to user
                showStakeholderValidationErrors(allErrors, true);
                const errorContainer = document.getElementById('stakeholdersValidationErrors');
                if (errorContainer) {
                    errorContainer.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
                
                return false;
            }

        } catch (error) {
            console.error('Error saving stakeholders:', error);
            console.error('Stakeholder save error: ' + (error.message || 'Unknown error'));
            return false;
        } finally {
            // Restore loading state
            console.log('Save operation completed.');
            
            // Reset saving flag
            isSaving = false;
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

        // Validate business area ID
        if (!currentBusinessAreaId || currentBusinessAreaId <= 0) {
            errors.push('Invalid business area ID. Please refresh the page and try again.');
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

            const response = await fetch(`/api/businessarea-stakeholder/${currentBusinessAreaId}/stakeholders/edit`, {
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

            const response = await fetch(`/api/businessarea-stakeholder/${currentBusinessAreaId}/stakeholders/edit`, {
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

            const response = await fetch(`/api/businessarea-stakeholder/${currentBusinessAreaId}/stakeholders/edit?objectXPeopleId=${objectXPeopleId}`, {
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
    window.BusinessAreaStakeholderEdit = {
        init: initStakeholderEdit,
        addNewRow: addNewRow,
        addNewRowAfter: addNewRowAfter,
        updateStakeholder: updateStakeholder,
        deleteStakeholder: deleteStakeholder,
        saveStakeholders: saveStakeholders,
        hasDataChanged: hasDataChanged,
        hasStakeholderChanges: hasDataChanged,
        getData: () => stakeholdersData
    };

})();