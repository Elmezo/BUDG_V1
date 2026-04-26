// Dataset Stakeholder Edit JavaScript - New Implementation
(function() {
    let currentDatasetId = null;
    let currentViewMode = 'original'; // 'original' | 'changes'
    let stakeholdersData = [];
    let attributeStakeholdersData = [];
    let originalData = [];
    let originalAttributeData = [];
    let rolesData = [];
    let attributeRolesData = [];
    let roleStatusesData = [];
    /** Admin role assignments: "dataset:roleId" | "attribute:roleId" -> [{ id, name }, ...] */
    let peopleByRoleId = {};

    function roleKey(roleId) {
        return roleId == null || roleId === '' ? '' : String(roleId);
    }

    function peopleScopeKey(stakeholderType) {
        return stakeholderType === 'attribute' ? 'attribute' : 'dataset';
    }

    function roleCacheKey(stakeholderType, roleId) {
        return `${peopleScopeKey(stakeholderType)}:${roleKey(roleId)}`;
    }

    function getPeopleForRole(roleId, stakeholderType = 'dataset') {
        return peopleByRoleId[roleCacheKey(stakeholderType, roleId)] || [];
    }

    function setPeopleForRole(roleId, people, stakeholderType = 'dataset') {
        peopleByRoleId[roleCacheKey(stakeholderType, roleId)] = Array.isArray(people) ? people.slice() : [];
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

    let attributesData = [];
    let currentUserId = null;
    let canEditStakeholders = false;
    let isAdminUser = false;
    let hasRoleEditPermission = false;

    // Initialize stakeholder edit
    async function initStakeholderEdit(datasetId, viewMode = 'original') {
        currentDatasetId = datasetId;
        currentViewMode = viewMode || 'original';
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
        } catch (error) { console.error('[DatasetStakeholderEdit] Error fetching current user:', error); }
    }
    
    async function checkUserRolePermission() {
        try {
            const response = await fetch('/api/user/permissions/Dataset', { method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' } });
            if (response.ok) {
                const perms = await response.json();
                hasRoleEditPermission = perms.success && (perms.canEdit === true || perms.isAdmin === true);
            }
        } catch (error) { hasRoleEditPermission = false; }
    }
    
    async function checkStakeholderEditPermission() {
        const hasBasicEditPermission = isAdminUser || hasRoleEditPermission;
        
        console.log('[DatasetStakeholderEdit] Permission check:', {
            isAdminUser: isAdminUser,
            hasRoleEditPermission: hasRoleEditPermission,
            hasBasicEditPermission: hasBasicEditPermission
        });
        
        if (!hasBasicEditPermission) {
            canEditStakeholders = false;
            console.log('[DatasetStakeholderEdit] No basic edit permission');
            return;
        }
        
        try {
            const response = await fetch(`/api/pending-changes/stakeholder-edit-permission/Dataset/${currentDatasetId}`, {
                method: 'GET', credentials: 'include', headers: { 'Content-Type': 'application/json' }
            });
            if (response.ok) {
                const data = await response.json();
                const workflowAllowsEdit = data.canEdit === true;
                canEditStakeholders = hasBasicEditPermission && workflowAllowsEdit;
                console.log('[DatasetStakeholderEdit] Final permission:', {
                    workflowAllowsEdit: workflowAllowsEdit,
                    canEditStakeholders: canEditStakeholders,
                    reason: data.reason
                });
            } else {
                canEditStakeholders = hasBasicEditPermission;
                console.log('[DatasetStakeholderEdit] Workflow API failed, using basic permission:', canEditStakeholders);
            }
        } catch (error) {
            console.error('[DatasetStakeholderEdit] Error checking workflow permission:', error);
            canEditStakeholders = hasBasicEditPermission;
        }
    }

    // Load stakeholders data for editing
    async function loadStakeholdersForEdit() {
        try {
            const container = document.getElementById('datasetStakeholdersContainer');
            if (!container) return;

            container.innerHTML = '<div class="loading">Loading stakeholders for editing...</div>';

            // Load dropdown data first (without loading all people)
            await Promise.all([
                loadDatasetRoles(),
                loadAttributeRoles(),
                loadRoleStatuses(),
                loadAttributes()
            ]);

            // Load stakeholders data
            await loadStakeholdersData();

            // Load attribute stakeholders data
            await loadAttributeStakeholdersData();

            // Now that we have both stakeholders data and role statuses, try to match status IDs
            matchStatusIds();
            matchAttributeStatusIds();

            // Load people for existing roles in stakeholders data
            await loadPeopleForExistingRoles();

            // Render the table
            renderEditableStakeholdersTable();
            
        } catch (error) {
            const container = document.getElementById('datasetStakeholdersContainer');
            if (container) {
                container.innerHTML = '<div class="error">Error loading stakeholders data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    // Load attributes data
    async function loadAttributes() {
        try {
            
            const response = await fetch(`/api/Attribute/stakeholder/lookup?type=attributes&datasetId=${currentDatasetId}`, {
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

            // Handle different API response formats
            let data;
            if (Array.isArray(responseData)) {
                data = responseData;
            } else if (responseData.data && Array.isArray(responseData.data)) {
                data = responseData.data;
            } else {
                data = [];
            }

            // Transform attributes data to simple format
            attributesData = data.map(item => ({
                id: item.AttributeID || item.id,
                name: item.AttributeName || item.name || ''
            }));

        } catch (error) {
            attributesData = [];
        }
    }

    // Load attribute stakeholders data from API
    async function loadAttributeStakeholdersData() {
        try {
            
            const attributeStakeholdersPromises = attributesData.map(async (attribute) => {
                try {
                    const response = await fetch(`/api/Attribute-stakeholder/${attribute.id}/stakeholders/edit`, {
                        method: 'GET',
                        credentials: 'include',
                        headers: {
                            'Content-Type': 'application/json'
                        }
                    });

                    if (!response.ok) {
                        return [];
                    }

                    const responseData = await response.json();

                    // Handle different API response formats
                    let data;
                    if (Array.isArray(responseData)) {
                        data = responseData;
                    } else if (responseData.data && Array.isArray(responseData.data)) {
                        data = responseData.data;
                    } else {
                        data = [];
                    }

                    // Transform API data to match frontend format with attribute info
                    return data.map(item => {
                        let stakeholder = {
                            objectXPeopleId: item.ObjectXPeopleID || item.objectXPeopleId || null,
                            peopleId: item.PeopleID || item.peopleId || null,
                            personName: item.PersonName || item.personName || item.name || '',
                            roleId: item.RoleID || item.roleId || null,
                            roleName: item.RoleName || item.roleName || item.role || '',
                            statusId: (item.StatusID || item.statusId) != null ? Number(item.StatusID || item.statusId) : null,
                            statusName: (item.StatusName || item.statusName || '').trim(),
                            personEmail: item.PersonEmail || item.personEmail || '',
                            attributeId: attribute.id,
                            attributeName: attribute.name,
                            delegateOf: item.DelegateOf || item.delegateOf || item.delegate_name || '',
                            delegateIpId: item.DelegateOfId || item.delegateOfId || item.delegate_of_id || item.delegateIpId || null
                        };

                        return stakeholder;
                    });
                } catch (error) {
                    return [];
                }
            });

            const allAttributeStakeholders = await Promise.all(attributeStakeholdersPromises);
            attributeStakeholdersData = allAttributeStakeholders.flat();
            
            // Store original attribute data for change detection
            originalAttributeData = JSON.parse(JSON.stringify(attributeStakeholdersData));
            
        } catch (error) {
            attributeStakeholdersData = [];
        }
    }

    // Load stakeholders data from API
    async function loadStakeholdersData() {
        try {
            
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-stakeholder/${currentDatasetId}/stakeholders/edit${viewParam}`, {
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
            
            // Handle different API response formats
            const data = responseData.data || responseData;
            
            // Ensure data is an array
            if (!Array.isArray(data)) {
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
                        statusId: (item.StatusID || item.statusId) != null ? Number(item.StatusID || item.statusId) : null,
                        statusName: (item.StatusName || item.statusName || '').trim(),
                        personEmail: item.PersonEmail || item.personEmail || '',
                        attributeId: null, // Dataset stakeholders don't have attribute
                        attributeName: 'Dataset', // Label for dataset-level stakeholders
                        delegateOf: item.DelegateOf || item.delegateOf || item.delegate_name || '',
                        delegateIpId: item.DelegateOfId || item.delegateOfId || item.delegate_of_id || item.delegateIpId || null,
                        roleAssignmentValid: item.roleAssignmentValid !== undefined ? item.roleAssignmentValid : true,
                        roleAssignmentWarning: item.roleAssignmentWarning || null,
                        isDefaultOnlyAssignment: item.isDefaultOnlyAssignment === true
                    };

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
                    }
                    
                    return stakeholder;
                });
            }

            // Store original data for change detection (only dataset stakeholders for now)
            originalData = JSON.parse(JSON.stringify(stakeholdersData));
            
        } catch (error) {
            stakeholdersData = [];
            originalData = [];
        }
    }

    // Load roles from API (for dataset stakeholders)
    async function loadDatasetRoles() {
        try {
            const response = await fetch('/api/dataset-stakeholder/lookup?type=roles', {
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

            if (Array.isArray(data)) {
                rolesData = data.map(role => ({
                    id: role.RoleID || role.id,
                    name: role.Role || role.name || role.primaryname
                }));
            } else {
                rolesData = [];
            }

            //console.log('Loaded dataset roles:', rolesData);
        } catch (error) {
            console.error('Error loading dataset roles:', error);
            rolesData = [];
        }
    }

    // Load attribute roles from API (for attribute stakeholders)
    async function loadAttributeRoles() {
        try {
            //console.log('Loading attribute roles...');
            const response = await fetch('/api/Attribute/stakeholder/lookup?type=roles', {
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
            //console.log('Attribute Roles API response:', data);

            if (Array.isArray(data)) {
                attributeRolesData = data.map(role => ({
                    id: role.RoleID || role.id,
                    name: role.Role || role.name || role.primaryname
                }));
            } else {
                attributeRolesData = [];
            }

            //console.log('Loaded attribute roles:', attributeRolesData);
        } catch (error) {
            console.error('Error loading attribute roles:', error);
            attributeRolesData = [];
        }
    }

    // Load role statuses from API
    async function loadRoleStatuses() {
        try {
            //console.log('Loading role statuses...');
            const response = await fetch('/api/dataset-stakeholder/lookup?type=rolestatus', {
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
            //console.log('Role statuses API response:', data);

            if (Array.isArray(data)) {
                roleStatusesData = data.map(status => ({
                    id: Number(status.ID || status.id),
                    name: (status.PrimaryName || status.name || status.primaryname || '').trim()
                }));
            } else {
                roleStatusesData = [];
            }

            //console.log('Loaded role statuses:', roleStatusesData);
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
    async function loadPeopleByRole(roleId, stakeholderType = 'dataset') {
        try {
            //console.log('Loading people for role:', roleId, 'stakeholder type:', stakeholderType);
            
            // Use appropriate API based on stakeholder type
            const apiUrl = stakeholderType === 'attribute' 
                ? `/api/Attribute/stakeholder/lookup?type=people&roleId=${roleId}&objectId=${currentDatasetId}`
                : `/api/dataset-stakeholder/lookup?type=people&roleId=${roleId}&objectId=${currentDatasetId}`;
                
            const response = await fetch(apiUrl, {
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
            //console.log('People by role API response:', data);

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
        //console.log('Matching status IDs for EDIT mode...');
        //console.log('Current stakeholdersData:', stakeholdersData);
        //console.log('Current roleStatusesData:', roleStatusesData);

        stakeholdersData.forEach((stakeholder, index) => {
            // For edit mode, try to match statusName with available statuses if statusId is missing
            if (!stakeholder.statusId && stakeholder.statusName) {
                //console.log(`Looking for status ID for stakeholder ${index} with statusName:`, stakeholder.statusName);
                // Try exact match first
                let status = roleStatusesData.find(s => s.name === stakeholder.statusName);
                // If no exact match, try case-insensitive match
                if (!status) {
                    status = roleStatusesData.find(s => s.name.toLowerCase() === stakeholder.statusName.toLowerCase());
                }
                if (status) {
                    //console.log('Found matching status:', status);
                    stakeholder.statusId = Number(status.id);
                } else {
                    //console.log('No matching status found for:', stakeholder.statusName);
                    //console.log('Available status names:', roleStatusesData.map(s => s.name));
                }
            }
        });

        //console.log('After matching, stakeholdersData:', stakeholdersData);
    }

    // Match status IDs for attribute stakeholders after loading both stakeholders and role statuses
    function matchAttributeStatusIds() {
        //console.log('Matching status IDs for attribute stakeholders in EDIT mode...');
        //console.log('Current attributeStakeholdersData:', attributeStakeholdersData);
        //console.log('Current roleStatusesData:', roleStatusesData);

        attributeStakeholdersData.forEach((stakeholder, index) => {
            // For edit mode, try to match statusName with available statuses if statusId is missing
            if (!stakeholder.statusId && stakeholder.statusName) {
                //console.log(`Looking for status ID for attribute stakeholder ${index} with statusName:`, stakeholder.statusName);
                // Try exact match first
                let status = roleStatusesData.find(s => s.name === stakeholder.statusName);
                // If no exact match, try case-insensitive match
                if (!status) {
                    status = roleStatusesData.find(s => s.name.toLowerCase() === stakeholder.statusName.toLowerCase());
                }
                if (status) {
                    //console.log('Found matching status:', status);
                    stakeholder.statusId = Number(status.id);
                } else {
                    //console.log('No matching status found for:', stakeholder.statusName);
                    //console.log('Available status names:', roleStatusesData.map(s => s.name));
                }
            }
        });

        //console.log('After matching, attributeStakeholdersData:', attributeStakeholdersData);
    }

    // Load people for existing roles in stakeholders data
    async function loadPeopleForExistingRoles() {
        try {
            peopleByRoleId = {};

            const datasetRoleIds = [...new Set(stakeholdersData.filter(s => s.roleId).map(s => s.roleId))];
            const attributeRoleIds = [...new Set(attributeStakeholdersData.filter(s => s.roleId).map(s => s.roleId))];

            for (const roleId of datasetRoleIds) {
                const people = await loadPeopleByRole(roleId, 'dataset');
                setPeopleForRole(roleId, people, 'dataset');
            }
            for (const roleId of attributeRoleIds) {
                const people = await loadPeopleByRole(roleId, 'attribute');
                setPeopleForRole(roleId, people, 'attribute');
            }

            stakeholdersData.forEach(stakeholder => {
                if (!stakeholder.roleId || !stakeholder.personName) {
                    return;
                }
                if (stakeholder.peopleId && stakeholder.peopleId !== 'null') {
                    return;
                }
                const list = getPeopleForRole(stakeholder.roleId, 'dataset');
                const person = matchPersonByNameInList(stakeholder.personName, list);
                if (person) {
                    stakeholder.peopleId = person.id;
                } else {
                    console.warn('Could not find people ID for:', stakeholder.personName, 'within admin-assigned dataset users for role', stakeholder.roleId);
                }
            });

            attributeStakeholdersData.forEach(stakeholder => {
                if (!stakeholder.roleId || !stakeholder.personName) {
                    return;
                }
                if (stakeholder.peopleId && stakeholder.peopleId !== 'null') {
                    return;
                }
                const list = getPeopleForRole(stakeholder.roleId, 'attribute');
                const person = matchPersonByNameInList(stakeholder.personName, list);
                if (person) {
                    stakeholder.peopleId = person.id;
                } else {
                    console.warn('Could not find people ID for:', stakeholder.personName, 'within admin-assigned attribute users for role', stakeholder.roleId);
                }
            });
        } catch (error) {
            console.error('Error loading people for existing roles:', error);
            peopleByRoleId = {};
        }
    }

    // Render editable stakeholders table
    function renderEditableStakeholdersTable() {
        const datasetContainer = document.getElementById('datasetStakeholdersContainer');
        const attributeContainer = document.getElementById('attributeStakeholdersContainer');
        // On facets view page only datasetStakeholdersContainer exists; attributeStakeholdersContainer exists only on dataset-edit page.
        const singleContainerMode = !attributeContainer && !!datasetContainer;

        if (!datasetContainer) return;

        // Check for invalid role assignments in dataset stakeholders
        const invalidDatasetAssignments = stakeholdersData.filter(s => s.roleAssignmentValid === false);
        let datasetWarningBanner = '';
        if (invalidDatasetAssignments.length > 0) {
            datasetWarningBanner = `
                <div class="alert alert-warning mb-3" role="alert">
                    <div class="d-flex align-items-center">
                        <i class="fas fa-exclamation-triangle me-2"></i>
                        <strong>Warning:</strong> Some dataset stakeholders have default roles they are not assigned to.
                    </div>
                    <hr class="my-2">
                    <p class="mb-1"><strong>Issues found:</strong></p>
                    <ul class="mb-0">
                        ${invalidDatasetAssignments.map(s => 
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

        // Render Dataset Stakeholders Table
        let datasetHtml = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="stakeholders-section-title">
                    <h3>Dataset Stakeholders</h3>
                    <div class="stakeholders-actions">
                        ${canEditStakeholders ? `
                        <button type="button" class="btn btn-primary btn-sm" onclick="DatasetStakeholderEdit.showAddStakeholderForm()">
                            <i class="fas fa-plus"></i> Add Stakeholder
                        </button>` : `<span class="text-muted" style="font-size: 0.85rem;"><i class="fas fa-lock"></i> Editing disabled</span>`}
                    </div>
                </div>
                ${defaultOnlyBanner}
                ${datasetWarningBanner}
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
                    <tbody id="datasetStakeholdersTableBody">
        `;

        if (stakeholdersData.length === 0) {
            datasetHtml += `
                <tr class="no-data-row">
                    <td colspan="5" class="no-data text-center">
                        No dataset stakeholders found. Click "Add Stakeholder" to add new ones.
                    </td>
                </tr>
            `;
        } else {
            stakeholdersData.forEach((stakeholder, index) => {
                datasetHtml += renderStakeholderRow(stakeholder, index);
            });
        }

        datasetHtml += `
                        </tbody>
                    </table>
                </div>
            </div>
        `;

        // Render Attribute Stakeholders Table
        let attributeHtml = `
            <div class="view-section" style="grid-column: 1/-1;">
                <div class="stakeholders-section-title">
                    <h3>Attribute Stakeholders</h3>
                    <div class="stakeholders-actions">
                        <button type="button" class="btn btn-primary btn-sm" onclick="DatasetStakeholderEdit.addNewAttributeRow()">
                            <i class="fas fa-plus"></i> Add Attribute Stakeholder
                        </button>
                    </div>
                </div>
                <div class="stakeholders-edit-container">
                    <table class="stakeholders-edit-table table table-striped">
                    <thead>
                        <tr>
                            <th>Attribute <span class="required">*</span></th>
                            <th>Role <span class="required">*</span></th>
                            <th>Name <span class="required">*</span></th>
                            <th>Role Status <span class="required">*</span></th>
                            <th>Delegate Of</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody id="attributeStakeholdersTableBody">
        `;

        if (attributeStakeholdersData.length === 0) {
            attributeHtml += `
                <tr class="no-data-row">
                    <td colspan="6" class="no-data text-center">
                        No attribute stakeholders found. Click "Add Attribute Stakeholder" to add new ones.
                    </td>
                </tr>
            `;
        } else {
            attributeStakeholdersData.forEach((stakeholder, index) => {
                attributeHtml += renderStakeholderRow(stakeholder, stakeholdersData.length + index);
            });
        }

        attributeHtml += `
                        </tbody>
                    </table>
                </div>
                <div class="stakeholders-validation-errors" id="stakeholdersValidationErrors"></div>
            </div>
        `;

        if (singleContainerMode) {
            datasetContainer.innerHTML = datasetHtml + attributeHtml;
        } else {
            datasetContainer.innerHTML = datasetHtml;
            attributeContainer.innerHTML = attributeHtml;
        }

        // Initialize event listeners for both tables
        initTableEventListeners();
        
        // Refresh delegate dropdowns after rendering
        refreshAllDelegateDropdowns();
    }

    // Get available delegates: same dataset/attribute stakeholders with the same role only
    function getAvailableDelegates(currentIndex, isAttribute = false) {
        const sourceData = isAttribute ? attributeStakeholdersData : stakeholdersData;
        const sourceIndex = isAttribute ? currentIndex - stakeholdersData.length : currentIndex;
        const current = sourceData[sourceIndex];
        if (!current) return [];
        const currentRoleId = current.roleId != null && current.roleId !== '' ? String(current.roleId) : null;
        if (currentRoleId == null) return [];

        return sourceData
            .filter((s, idx) => {
                const actualIndex = isAttribute ? stakeholdersData.length + idx : idx;
                if (actualIndex === currentIndex) return false;
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
        // Refresh dataset stakeholders
        stakeholdersData.forEach((stakeholder, index) => {
            const delegateSelect = document.querySelector(`select.stakeholder-delegate[data-index="${index}"][data-type="dataset"]`);
            if (delegateSelect) {
                const currentValue = delegateSelect.value;
                const availableDelegates = getAvailableDelegates(index, false);
                
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
        
        // Refresh attribute stakeholders
        attributeStakeholdersData.forEach((stakeholder, index) => {
            const actualIndex = stakeholdersData.length + index;
            const delegateSelect = document.querySelector(`select.stakeholder-delegate[data-index="${actualIndex}"][data-type="attribute"]`);
            if (delegateSelect) {
                const currentValue = delegateSelect.value;
                const availableDelegates = getAvailableDelegates(actualIndex, true);
                
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
                    attributeStakeholdersData[index].delegateIpId = null;
                    attributeStakeholdersData[index].delegateOf = '';
                }
            }
        });
    }

    // Render a single stakeholder row
    function renderStakeholderRow(stakeholder, index) {
        const isDatasetStakeholder = index < stakeholdersData.length;
        const isAttributeStakeholder = index >= stakeholdersData.length;
        const peopleStakeholderType = isDatasetStakeholder ? 'dataset' : 'attribute';

        let peopleOptions = '<option value="">Select Person</option>';

        if (stakeholder.roleId) {
            const rolePeople = getPeopleForRole(stakeholder.roleId, peopleStakeholderType);
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

        // Build status options
        let statusOptions = '<option value="">Select Status</option>';
        if (roleStatusesData && roleStatusesData.length > 0) {
            // Convert stakeholder.statusId to number for proper comparison
            const stakeholderStatusId = stakeholder.statusId != null ? Number(stakeholder.statusId) : null;
            
            roleStatusesData.forEach(status => {
                const statusId = Number(status.id);
                const isSelected = stakeholderStatusId !== null && stakeholderStatusId === statusId;
                statusOptions += `<option value="${status.id}" ${isSelected ? 'selected' : ''}>${escapeHtml(status.name)}</option>`;
            });
        }
        
        // Use appropriate roles data based on stakeholder type
        const currentRolesData = isDatasetStakeholder ? rolesData : attributeRolesData;

        // Add warning class if role assignment is invalid (only for dataset stakeholders)
        const warningClass = (isDatasetStakeholder && stakeholder.roleAssignmentValid === false) ? 'role-assignment-warning' : '';
        const warningIcon = (isDatasetStakeholder && stakeholder.roleAssignmentValid === false) ? 
            `<i class="fas fa-exclamation-triangle text-warning" title="${escapeHtml(stakeholder.roleAssignmentWarning || 'The user is not assigned to this role. Select another user that is assigned to this role.')}"></i>` : '';

        // Build delegate options - only show stakeholders already in the current dataset/attribute
        const availableDelegates = getAvailableDelegates(index, isAttributeStakeholder);
        const delegateOptions = availableDelegates.map(delegate => {
            const isSelected = stakeholder.delegateIpId == delegate.value;
            return `<option value="${delegate.value}" ${isSelected ? 'selected' : ''}>${escapeHtml(delegate.label)}</option>`;
        }).join('');

        return `
            <tr data-index="${index}" data-id="${stakeholder.objectXPeopleId || ''}" data-type="${isDatasetStakeholder ? 'dataset' : 'attribute'}" class="${warningClass}">
                ${isDatasetStakeholder ? '' : `
                <td>
                    <select class="form-select stakeholder-attribute"
                            onchange="DatasetStakeholderEdit.updateAttributeStakeholder(${index - stakeholdersData.length}, 'attributeId', this.value).catch(console.error)"
                            data-original-value="${stakeholder.attributeId || ''}">
                        <option value="">Select Attribute</option>
                        ${attributesData.map(attribute =>
                            `<option value="${attribute.id}" ${stakeholder.attributeId == attribute.id ? 'selected' : ''}>${escapeHtml(attribute.name)}</option>`
                        ).join('')}
                    </select>
                </td>
                `}
                <td>
                    <div class="d-flex align-items-center gap-2">
                        <select class="form-select stakeholder-role"
                                onchange="DatasetStakeholderEdit.${isDatasetStakeholder ? 'updateStakeholder' : 'updateAttributeStakeholder'}(${isDatasetStakeholder ? index : index - stakeholdersData.length}, 'roleId', this.value).catch(console.error)"
                                data-original-value="${stakeholder.roleId || ''}">
                            <option value="">Select Role</option>
                            ${currentRolesData.map(role =>
                                `<option value="${role.id}" ${stakeholder.roleId == role.id ? 'selected' : ''}>${escapeHtml(role.name)}</option>`
                            ).join('')}
                        </select>
                        ${warningIcon}
                    </div>
                    ${(isDatasetStakeholder && stakeholder.roleAssignmentWarning) ? `<small class="text-danger d-block mt-1">${escapeHtml(stakeholder.roleAssignmentWarning)}</small>` : ''}
                </td>
                <td>
                    <select class="form-select stakeholder-people"
                            onchange="DatasetStakeholderEdit.${isDatasetStakeholder ? 'updateStakeholder' : 'updateAttributeStakeholder'}(${isDatasetStakeholder ? index : index - stakeholdersData.length}, 'peopleId', this.value).catch(console.error)"
                            data-original-value="${stakeholder.peopleId || ''}"
                            ${!stakeholder.roleId ? 'disabled' : ''}>
                        ${peopleOptions}
                    </select>
                </td>
                <td>
                    <select class="form-select stakeholder-status"
                            onchange="DatasetStakeholderEdit.${isDatasetStakeholder ? 'updateStakeholder' : 'updateAttributeStakeholder'}(${isDatasetStakeholder ? index : index - stakeholdersData.length}, 'statusId', this.value).catch(console.error)"
                            data-original-value="${stakeholder.statusId || ''}">
                        ${statusOptions}
                    </select>
                </td>
                <td>
                    <select class="form-select stakeholder-delegate"
                            onchange="DatasetStakeholderEdit.${isDatasetStakeholder ? 'updateStakeholder' : 'updateAttributeStakeholder'}(${isDatasetStakeholder ? index : index - stakeholdersData.length}, 'delegateIpId', this.value).catch(console.error)"
                            data-index="${index}"
                            data-type="${isDatasetStakeholder ? 'dataset' : 'attribute'}"
                            data-original-value="${stakeholder.delegateIpId || ''}">
                        <option value="">Select Delegate</option>
                        ${delegateOptions}
                        ${(() => {
                            // If delegateIpId is set but not in available delegates, add it as an option
                            if (stakeholder.delegateIpId && !availableDelegates.find(d => d.value == stakeholder.delegateIpId)) {
                                const allStakeholders = [...stakeholdersData, ...attributeStakeholdersData];
                                const delegate = allStakeholders.find(s => s.objectXPeopleId == stakeholder.delegateIpId);
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
                                return `<button type="button" class="btn btn-danger btn-sm" onclick="DatasetStakeholderEdit.${isDatasetStakeholder ? 'deleteStakeholder' : 'deleteAttributeStakeholder'}(${isDatasetStakeholder ? index : index - stakeholdersData.length})" title="Delete stakeholder"><i class="fas fa-minus"></i></button>`;
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
        // Handle role change to update people dropdown for Dataset Stakeholders
        document.querySelectorAll('#datasetStakeholdersTableBody .stakeholder-role').forEach(select => {
            select.addEventListener('change', async function() {
                const row = this.closest('tr');
                const index = parseInt(row.dataset.index);
                const roleId = this.value;
                const peopleSelect = row.querySelector('.stakeholder-people');

                if (roleId) {
                    const peopleForRole = await loadPeopleByRole(roleId, 'dataset');
                    setPeopleForRole(roleId, peopleForRole, 'dataset');

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

                    if (stakeholdersData[index]) {
                        stakeholdersData[index].peopleId = null;
                        stakeholdersData[index].personName = '';
                    }
                } else {
                    // Disable and clear people dropdown when no role selected
                    peopleSelect.disabled = true;
                    peopleSelect.innerHTML = '<option value="">Select Person</option>';

                    // Clear current selection in dataset stakeholders
                    if (stakeholdersData[index]) {
                        stakeholdersData[index].peopleId = null;
                        stakeholdersData[index].personName = '';
                    }
                }
                
                // Refresh delegate dropdowns when role or people changes
                refreshAllDelegateDropdowns();
            });
        });
        
        // Handle delegate dropdown changes for dataset stakeholders
        document.querySelectorAll('#datasetStakeholdersTableBody .stakeholder-delegate').forEach(select => {
            select.addEventListener('change', function() {
                const row = this.closest('tr');
                const index = parseInt(row.dataset.index);
                const delegateIpId = this.value;
                DatasetStakeholderEdit.updateStakeholder(index, 'delegateIpId', delegateIpId).catch(console.error);
            });
        });
        
        // Handle delegate dropdown changes for attribute stakeholders
        document.querySelectorAll('#attributeStakeholdersTableBody .stakeholder-delegate').forEach(select => {
            select.addEventListener('change', function() {
                const row = this.closest('tr');
                const index = parseInt(row.dataset.index) - stakeholdersData.length;
                const delegateIpId = this.value;
                DatasetStakeholderEdit.updateAttributeStakeholder(index, 'delegateIpId', delegateIpId).catch(console.error);
            });
        });

        // Handle role change to update people dropdown for Attribute Stakeholders
        document.querySelectorAll('#attributeStakeholdersTableBody .stakeholder-role').forEach(select => {
            select.addEventListener('change', async function() {
                const row = this.closest('tr');
                const index = parseInt(row.dataset.index) - stakeholdersData.length; // Convert to attribute index
                const roleId = this.value;
                const peopleSelect = row.querySelector('.stakeholder-people');

                if (roleId) {
                    const peopleForRole = await loadPeopleByRole(roleId, 'attribute');
                    setPeopleForRole(roleId, peopleForRole, 'attribute');

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

                    if (attributeStakeholdersData[index]) {
                        attributeStakeholdersData[index].peopleId = null;
                        attributeStakeholdersData[index].personName = '';
                    }
                } else {
                    // Disable and clear people dropdown when no role selected
                    peopleSelect.disabled = true;
                    peopleSelect.innerHTML = '<option value="">Select Person</option>';

                    // Clear current selection in attribute stakeholders
                    if (attributeStakeholdersData[index]) {
                        attributeStakeholdersData[index].peopleId = null;
                        attributeStakeholdersData[index].personName = '';
                    }
                }
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
                    <button class="stakeholder-add-modal-close" onclick="DatasetStakeholderEdit.closeAddModal()">&times;</button>
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
                    <button type="button" class="btn btn-secondary" onclick="DatasetStakeholderEdit.closeAddModal()">Cancel</button>
                    <button type="button" class="btn btn-primary" id="saveNewStakeholderBtn" onclick="DatasetStakeholderEdit.saveNewStakeholder()"><i class="fas fa-save"></i> Save</button>
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
                    setPeopleForRole(roleId, people, 'dataset');
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
            const selectedPerson = getPeopleForRole(roleId, 'dataset').find(p => String(p.id) === String(peopleId));
            const selectedDelegate = stakeholdersData.find(s => String(s.objectXPeopleId) === String(delegateIpId));

            // Fallback: read the person name directly from the dropdown option if not found in role cache
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
                attributeId: null,
                attributeName: '',
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
            attributeId: null,
            attributeName: '',
            delegateOf: '',
            delegateIpId: null
        };

        // Insert after the specified index
        stakeholdersData.splice(index + 1, 0, newStakeholder);
        renderEditableStakeholdersTable();
        refreshAllDelegateDropdowns();
        //console.log('Added new stakeholder row after index:', index);
    }

    // Add new attribute stakeholder row
    function addNewAttributeRow() {
        const newStakeholder = {
            objectXPeopleId: null, // Will be assigned by backend
            peopleId: null,
            personName: '',
            roleId: null,
            roleName: '',
            statusId: null,
            statusName: '',
            personEmail: '',
            attributeId: null,
            attributeName: '',
            delegateOf: '',
            delegateIpId: null
        };

        attributeStakeholdersData.push(newStakeholder);
        renderEditableStakeholdersTable();
        refreshAllDelegateDropdowns();
        //console.log('Added new attribute stakeholder row');
    }

    // Add new attribute stakeholder row after specific index
    function addNewAttributeRowAfter(index) {
        const newStakeholder = {
            objectXPeopleId: null, // Will be assigned by backend
            peopleId: null,
            personName: '',
            roleId: null,
            roleName: '',
            statusId: null,
            statusName: '',
            personEmail: '',
            attributeId: null,
            attributeName: '',
            delegateOf: '',
            delegateIpId: null
        };

        // Insert after the specified index
        attributeStakeholdersData.splice(index + 1, 0, newStakeholder);
        renderEditableStakeholdersTable();
        refreshAllDelegateDropdowns();
        //console.log('Added new attribute stakeholder row after index:', index);
    }

    // Update stakeholder data (Dataset Stakeholders only)
    async function updateStakeholder(index, field, value) {
        if (index >= 0 && index < stakeholdersData.length) {
            const stakeholder = stakeholdersData[index];
            stakeholder[field] = value || null;

            // Update related fields based on selection
            if (field === 'roleId' && value) {
                const role = rolesData.find(r => r.id == value);
                stakeholder.roleName = role ? role.name : '';
                // Clear person selection when role changes
                stakeholder.peopleId = null;
                stakeholder.personName = '';
            } else if (field === 'peopleId' && value) {
                const rid = stakeholder.roleId;
                let person = getPeopleForRole(rid, 'dataset').find(p => String(p.id) === String(value));

                if (!person) {
                    const row = document.querySelector(`tr[data-index="${index}"]`);
                    if (row) {
                        const peopleSelect = row.querySelector('.stakeholder-people');
                        if (peopleSelect) {
                            const selectedOption = peopleSelect.querySelector(`option[value="${value}"]`);
                            if (selectedOption) {
                                const personName = selectedOption.textContent;
                                person = { id: value, name: personName };
                                const list = getPeopleForRole(rid, 'dataset').slice();
                                if (!list.find(p => String(p.id) === String(value))) {
                                    list.push(person);
                                    setPeopleForRole(rid, list, 'dataset');
                                }
                            }
                        }
                    }
                }

                if (!person && rid) {
                    const rolePeople = await loadPeopleByRole(rid, 'dataset');
                    setPeopleForRole(rid, rolePeople, 'dataset');
                    person = rolePeople.find(p => String(p.id) === String(value));
                }

                stakeholder.personName = person ? person.name : '';
            } else if (field === 'statusId' && value) {
                const status = roleStatusesData.find(s => s.id == value);
                stakeholder.statusName = status ? status.name : '';
            } else if (field === 'delegateIpId') {
                // Update delegateIpId and find the delegate name for display
                stakeholder.delegateIpId = value ? parseInt(value) : null;
                if (value) {
                    const allStakeholders = [...stakeholdersData, ...attributeStakeholdersData];
                    const delegate = allStakeholders.find(s => s.objectXPeopleId == value);
                    stakeholder.delegateOf = delegate ? `${delegate.personName} - ${delegate.roleName}` : '';
                } else {
                    stakeholder.delegateOf = '';
                }
                // Refresh all delegate dropdowns when a delegate is selected
                refreshAllDelegateDropdowns();
            }

            // Real-time validation for the current row
            validateCurrentRow(index);
        }
    }

    // Update attribute stakeholder data (Attribute Stakeholders only)
    async function updateAttributeStakeholder(index, field, value) {
        if (index >= 0 && index < attributeStakeholdersData.length) {
            const stakeholder = attributeStakeholdersData[index];
            stakeholder[field] = value || null;

            // Update related fields based on selection
            if (field === 'attributeId' && value) {
                const attribute = attributesData.find(a => a.id == value);
                stakeholder.attributeId = attribute ? attribute.id : null;
                stakeholder.attributeName = attribute ? attribute.name : '';
            } else if (field === 'roleId' && value) {
                const role = attributeRolesData.find(r => r.id == value);
                stakeholder.roleName = role ? role.name : '';
                // Clear person selection when role changes
                stakeholder.peopleId = null;
                stakeholder.personName = '';
            } else if (field === 'peopleId' && value) {
                const rid = stakeholder.roleId;
                let person = getPeopleForRole(rid, 'attribute').find(p => String(p.id) === String(value));

                if (!person) {
                    const row = document.querySelector(`tr[data-index="${index + stakeholdersData.length}"]`);
                    if (row) {
                        const peopleSelect = row.querySelector('.stakeholder-people');
                        if (peopleSelect) {
                            const selectedOption = peopleSelect.querySelector(`option[value="${value}"]`);
                            if (selectedOption) {
                                const personName = selectedOption.textContent;
                                person = { id: value, name: personName };
                                const list = getPeopleForRole(rid, 'attribute').slice();
                                if (!list.find(p => String(p.id) === String(value))) {
                                    list.push(person);
                                    setPeopleForRole(rid, list, 'attribute');
                                }
                            }
                        }
                    }
                }

                if (!person && rid) {
                    const rolePeople = await loadPeopleByRole(rid, 'attribute');
                    setPeopleForRole(rid, rolePeople, 'attribute');
                    person = rolePeople.find(p => String(p.id) === String(value));
                }

                stakeholder.personName = person ? person.name : '';
            } else if (field === 'statusId' && value) {
                const status = roleStatusesData.find(s => s.id == value);
                stakeholder.statusName = status ? status.name : '';
            } else if (field === 'delegateIpId') {
                // Update delegateIpId and find the delegate name for display
                stakeholder.delegateIpId = value ? parseInt(value) : null;
                if (value) {
                    const allStakeholders = [...stakeholdersData, ...attributeStakeholdersData];
                    const delegate = allStakeholders.find(s => s.objectXPeopleId == value);
                    stakeholder.delegateOf = delegate ? `${delegate.personName} - ${delegate.roleName}` : '';
                } else {
                    stakeholder.delegateOf = '';
                }
                // Refresh all delegate dropdowns when a delegate is selected
                refreshAllDelegateDropdowns();
            }

            // Real-time validation for the current row
            validateCurrentRow(index + stakeholdersData.length);
        }
    }

    // Validate current row in real-time
    function validateCurrentRow(index) {
        const row = document.querySelector(`tr[data-index="${index}"]`);
        if (!row) return;

        // Remove existing error classes
        row.querySelectorAll('.form-select').forEach(select => {
            select.classList.remove('is-invalid');
        });

        // Determine if this is a dataset or attribute stakeholder
        const isDatasetStakeholder = index < stakeholdersData.length;
        const isAttributeStakeholder = index >= stakeholdersData.length;

        let stakeholder;
        if (isDatasetStakeholder) {
            stakeholder = stakeholdersData[index];
        } else {
            stakeholder = attributeStakeholdersData[index - stakeholdersData.length];
        }

        if (!stakeholder) return;

        // Validate and add error classes
        const attributeSelect = row.querySelector('.stakeholder-attribute');
        const roleSelect = row.querySelector('.stakeholder-role');
        const peopleSelect = row.querySelector('.stakeholder-people');
        const statusSelect = row.querySelector('.stakeholder-status');

        // Only validate attribute for attribute stakeholders
        if (isAttributeStakeholder && !stakeholder.attributeId) {
            attributeSelect?.classList.add('is-invalid');
        }

        if (!stakeholder.roleId) {
            roleSelect?.classList.add('is-invalid');
        }

        if (!stakeholder.peopleId) {
            peopleSelect?.classList.add('is-invalid');
        }

        if (!stakeholder.statusId) {
            statusSelect?.classList.add('is-invalid');
        }

        // Clear validation errors if all required fields are valid
        const hasRequiredFields = isAttributeStakeholder 
            ? (stakeholder.attributeId && stakeholder.roleId && stakeholder.peopleId && stakeholder.statusId)
            : (stakeholder.roleId && stakeholder.peopleId && stakeholder.statusId);
            
        if (hasRequiredFields) {
            clearValidationErrors();
        }
    }

    // Delete stakeholder locally (persist on Save / Save & Close)
    async function deleteStakeholder(index) {
        if (index >= 0 && index < stakeholdersData.length) {
            const stakeholder = stakeholdersData[index];
            
            if (!canEditStakeholders) { alert('You do not have permission to delete stakeholders'); return; }
            
            const deleteConfirmMessage = `Are you sure you want to delete "${stakeholder.personName || 'this stakeholder'}" as "${stakeholder.roleName || 'stakeholder'}"?`;
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: deleteConfirmMessage, type: 'warning' })
                : Promise.resolve(confirm(deleteConfirmMessage)));
            if (confirmed) {
                try {
                    stakeholdersData.splice(index, 1);
                    renderEditableStakeholdersTable();
                    refreshAllDelegateDropdowns();
                } catch (error) { alert('Error deleting stakeholder: ' + (error.message || 'Unknown error')); }
            }
        }
    }

    // Delete attribute stakeholder locally (persist on Save / Save & Close)
    async function deleteAttributeStakeholder(index) {
        if (index >= 0 && index < attributeStakeholdersData.length) {
            const stakeholder = attributeStakeholdersData[index];
            
            if (!canEditStakeholders) { alert('You do not have permission to delete stakeholders'); return; }
            
            const deleteConfirmMessage = `Are you sure you want to delete "${stakeholder.personName || 'this stakeholder'}" as "${stakeholder.roleName || 'stakeholder'}"?`;
            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: deleteConfirmMessage, type: 'warning' })
                : Promise.resolve(confirm(deleteConfirmMessage)));
            if (confirmed) {
                try {
                    attributeStakeholdersData.splice(index, 1);
                    renderEditableStakeholdersTable();
                    refreshAllDelegateDropdowns();
                } catch (error) { alert('Error deleting stakeholder: ' + (error.message || 'Unknown error')); }
            }
        }
    }

    // Original deleteAttributeStakeholder - legacy backup
    async function deleteAttributeStakeholderLegacy(index) {
        if (index >= 0 && index < attributeStakeholdersData.length) {
            const stakeholder = attributeStakeholdersData[index];
            const confirmMessage = `Are you sure you want to delete "${stakeholder.personName || 'this attribute stakeholder'}"?`;

            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (confirmed) {
                attributeStakeholdersData.splice(index, 1);
                renderEditableStakeholdersTable();
                refreshAllDelegateDropdowns();
            }
        }
    }

    // Validate dataset stakeholders only
    function validateDatasetStakeholders() {
        const errors = [];
        const seenCombinations = new Set();

        stakeholdersData.forEach((stakeholder, index) => {
            // Check required fields for dataset stakeholders
            if (!stakeholder.roleId) {
                errors.push(`Row ${index + 1}: Role is required`);
            }
            if (!stakeholder.peopleId) {
                errors.push(`Row ${index + 1}: Person is required`);
            }
            if (!stakeholder.statusId) {
                errors.push(`Row ${index + 1}: Status is required`);
            }

            // Check for duplicates (Role + Person combination)
            if (stakeholder.roleId && stakeholder.peopleId) {
                const combination = `dataset-${stakeholder.roleId}-${stakeholder.peopleId}`;
                if (seenCombinations.has(combination)) {
                    errors.push(`Row ${index + 1}: Duplicate combination of Role and Person`);
                } else {
                    seenCombinations.add(combination);
                }
            }
        });

        return errors;
    }

    // Validate attribute stakeholders only
    function validateAttributeStakeholders() {
        const errors = [];
        const seenCombinations = new Set();

        attributeStakeholdersData.forEach((stakeholder, index) => {
            // Check required fields for attribute stakeholders
            if (!stakeholder.attributeId) {
                errors.push(`Row ${index + 1}: Attribute is required and must be selected`);
            }
            if (!stakeholder.roleId) {
                errors.push(`Row ${index + 1}: Role is required`);
            }
            if (!stakeholder.peopleId) {
                errors.push(`Row ${index + 1}: Person is required`);
            }
            if (!stakeholder.statusId) {
                errors.push(`Row ${index + 1}: Status is required`);
            }

            // Check for duplicates (Attribute + Role + Person combination)
            if (stakeholder.attributeId && stakeholder.roleId && stakeholder.peopleId) {
                const combination = `${stakeholder.attributeId}-${stakeholder.roleId}-${stakeholder.peopleId}`;
                if (seenCombinations.has(combination)) {
                    errors.push(`Row ${index + 1}: Duplicate combination of Attribute, Role and Person`);
                } else {
                    seenCombinations.add(combination);
                }
            }
        });

        return errors;
    }

    // Validate stakeholders data (legacy function - now calls both validators)
    function validateStakeholders() {
        const errors = [];

        // Combine dataset and attribute stakeholders for validation
        const allStakeholders = [...stakeholdersData, ...attributeStakeholdersData];

        //console.log('Validating stakeholders data:', allStakeholders);
        //console.log('Available roles data:', rolesData);
        //console.log('Available role statuses data:', roleStatusesData);
        //console.log('Available attributes data:', attributesData);

        allStakeholders.forEach((stakeholder, index) => {
            const rowNum = index + 1;
            const isDatasetStakeholder = index < stakeholdersData.length;
            const isAttributeStakeholder = index >= stakeholdersData.length;

            // Validate Attribute (Required only for attribute stakeholders)
            if (isAttributeStakeholder) {
                if (!stakeholder.attributeId || stakeholder.attributeId === null || stakeholder.attributeId === '') {
                    errors.push(`Row ${rowNum}: Attribute is required and must be selected`);
                } else {
                    // Check if attribute exists in available attributes
                    const attributeExists = attributesData.find(a => a.id == stakeholder.attributeId);
                    if (!attributeExists) {
                        errors.push(`Row ${rowNum}: Selected attribute is invalid`);
                    }
                }
            }

            // Validate Role (Required)
            if (!stakeholder.roleId || stakeholder.roleId === null || stakeholder.roleId === '') {
                errors.push(`Row ${rowNum}: Role is required and must be selected`);
            } else {
                // Check if role exists in available roles (use appropriate roles data)
                const isDatasetStakeholder = index < stakeholdersData.length;
                const currentRolesData = isDatasetStakeholder ? rolesData : attributeRolesData;
                const roleExists = currentRolesData.find(r => r.id == stakeholder.roleId);
                if (!roleExists) {
                    errors.push(`Row ${rowNum}: Selected role is invalid`);
                }
            }

            // Validate Name (Required)
            if (!stakeholder.peopleId || stakeholder.peopleId === null || stakeholder.peopleId === '') {
                errors.push(`Row ${rowNum}: Name is required and must be selected`);
            } else {
                const ptype = isDatasetStakeholder ? 'dataset' : 'attribute';
                const rolePeople = getPeopleForRole(stakeholder.roleId, ptype);
                const personExists = rolePeople.some(p => String(p.id) === String(stakeholder.peopleId))
                    || (rolePeople.length === 0 && !!stakeholder.peopleId);
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
                    const ptype = isDatasetStakeholder ? 'dataset' : 'attribute';
                    const person = getPeopleForRole(stakeholder.roleId, ptype).find(p => String(p.id) === String(stakeholder.peopleId));
                    if (person) {
                        stakeholder.personName = person.name;
                    }
                }
            }

            // Validate role name is not empty (additional check)
            if (!stakeholder.roleName || stakeholder.roleName.trim() === '') {
                errors.push(`Row ${rowNum}: Role name cannot be empty`);
            }
        });

        // Check for duplicate combinations (Attribute + Role + Person) - Only for attribute stakeholders
        const attributeCombinations = [];
        const duplicateRows = [];

        allStakeholders.forEach((stakeholder, index) => {
            const isDatasetStakeholder = index < stakeholdersData.length;
            const isAttributeStakeholder = index >= stakeholdersData.length;
            
            // Only check duplicates for attribute stakeholders
            if (isAttributeStakeholder && stakeholder.attributeId && stakeholder.roleId && stakeholder.peopleId) {
                const combo = `${stakeholder.attributeId}-${stakeholder.roleId}-${stakeholder.peopleId}`;
                const existingIndex = attributeCombinations.indexOf(combo);
                if (existingIndex !== -1) {
                    duplicateRows.push(`Row ${index + 1} and Row ${existingIndex + 1}`);
                } else {
                    attributeCombinations.push(combo);
                }
            }
        });

        if (duplicateRows.length > 0) {
            errors.push(`Duplicate attribute-role-person combinations found in Attribute Stakeholders: ${duplicateRows.join(', ')}`);
        }

        // Additional validation for Attribute Stakeholders completeness
        const attributeStakeholders = allStakeholders.filter((_, index) => index >= stakeholdersData.length);
        const incompleteAttributeStakeholders = attributeStakeholders.filter(stakeholder => 
            !stakeholder.attributeId || !stakeholder.roleId || !stakeholder.peopleId || !stakeholder.statusId
        );

        if (incompleteAttributeStakeholders.length > 0) {
            errors.push(`Attribute Stakeholders must have all required fields: Attribute, Role, Name, and Role Status`);
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
        const stakeholdersChanged = JSON.stringify(stakeholdersData) !== JSON.stringify(originalData);
        const attributeStakeholdersChanged = JSON.stringify(attributeStakeholdersData) !== JSON.stringify(originalAttributeData);
        return stakeholdersChanged || attributeStakeholdersChanged;
    }

    // Save stakeholders data
    async function saveStakeholders() {
        try {
            // Clear previous validation errors
            clearValidationErrors();

            // Check if any data has changed
            const stakeholdersChanged = JSON.stringify(stakeholdersData) !== JSON.stringify(originalData);
            const attributeStakeholdersChanged = JSON.stringify(attributeStakeholdersData) !== JSON.stringify(originalAttributeData);

            if (!stakeholdersChanged && !attributeStakeholdersChanged) {
                alert('No changes detected in stakeholders data.');
                return true;
            }

            // Validate only the changed tables
            let errors = [];
            if (stakeholdersChanged) {
                const datasetErrors = validateDatasetStakeholders();
                errors = errors.concat(datasetErrors);
            }
            if (attributeStakeholdersChanged) {
                const attributeErrors = validateAttributeStakeholders();
                errors = errors.concat(attributeErrors);
            }

            if (errors.length > 0) {
                showValidationErrors(errors);
                // Scroll to errors
                const errorContainer = document.getElementById('stakeholdersValidationErrors');
                if (errorContainer) {
                    errorContainer.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
                return false;
            }

            // Confirm save action
            let confirmMessage = 'Are you sure you want to save changes?';
            if (stakeholdersChanged && attributeStakeholdersChanged) {
                confirmMessage = 'Are you sure you want to save changes to both Dataset and Attribute stakeholders?';
            } else if (stakeholdersChanged) {
                confirmMessage = 'Are you sure you want to save changes to Dataset stakeholders?';
            } else if (attributeStakeholdersChanged) {
                confirmMessage = 'Are you sure you want to save changes to Attribute stakeholders?';
            }

            const confirmed = await (typeof window.showConfirmDialog === 'function'
                ? window.showConfirmDialog({ message: confirmMessage, type: 'warning' })
                : Promise.resolve(confirm(confirmMessage)));
            if (!confirmed) {
                return false;
            }

            // Save each table separately
            let allSuccess = true;

            // Save Dataset Stakeholders if changed
            if (stakeholdersChanged) {
                const datasetOperations = determineDatasetOperations();
                allSuccess = await saveDatasetStakeholders(datasetOperations);
            }

            // Save Attribute Stakeholders if changed (only if dataset save was successful)
            if (allSuccess && attributeStakeholdersChanged) {
                const attributeOperations = determineAttributeOperations();
                allSuccess = await saveAttributeStakeholders(attributeOperations);
            }

            if (allSuccess) {
                // Update original data after successful save
                originalData = JSON.parse(JSON.stringify(stakeholdersData));
                originalAttributeData = JSON.parse(JSON.stringify(attributeStakeholdersData));
                alert('Stakeholders data saved successfully!');

                // Reload data to get updated IDs
                await loadStakeholdersData();
                await loadAttributeStakeholdersData();
                await matchStatusIds();
                await matchAttributeStatusIds();
                await loadPeopleForExistingRoles();
                renderEditableStakeholdersTable();

                return true;
            } else {
                throw new Error('Some operations failed. Please check the logs.');
            }

        } catch (error) {
            console.error('Error saving stakeholders:', error);
            alert('Error saving stakeholders: ' + (error.message || 'Unknown error'));
            return false;
        } finally {
            // Restore loading state
            //console.log('Save operation completed.');
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

        // Validate dataset ID
        if (!currentDatasetId || currentDatasetId <= 0) {
            errors.push('Invalid dataset ID. Please refresh the page and try again.');
        }

        return errors;
    }

    // Determine dataset operations only
    function determineDatasetOperations() {
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
                // New item - insertion
                operations.insertions.push(current);
            } else {
                // Existing item - check if changed
                const original = originalData.find(orig =>
                    orig.objectXPeopleId === current.objectXPeopleId
                );
                if (original) {
                    const hasChanged = (
                        current.roleId !== original.roleId ||
                        current.peopleId !== original.peopleId ||
                        current.statusId !== original.statusId
                    );
                    if (hasChanged) {
                        operations.updates.push(current);
                    }
                }
            }
        });

        return operations;
    }

    // Determine attribute operations only
    function determineAttributeOperations() {
        const operations = {
            insertions: [],
            updates: [],
            deletions: []
        };

        // Find deletions (items in original but not in current)
        originalAttributeData.forEach(original => {
            if (original.objectXPeopleId) {
                const found = attributeStakeholdersData.find(current =>
                    current.objectXPeopleId === original.objectXPeopleId
                );
                if (!found) {
                    operations.deletions.push(original);
                }
            }
        });

        // Find insertions and updates
        attributeStakeholdersData.forEach(current => {
            if (!current.objectXPeopleId) {
                // New item - insertion
                operations.insertions.push(current);
            } else {
                // Existing item - check if changed
                const original = originalAttributeData.find(orig =>
                    orig.objectXPeopleId === current.objectXPeopleId
                );
                if (original) {
                    const hasChanged = (
                        current.attributeId !== original.attributeId ||
                        current.roleId !== original.roleId ||
                        current.peopleId !== original.peopleId ||
                        current.statusId !== original.statusId
                    );
                    if (hasChanged) {
                        operations.updates.push(current);
                    }
                }
            }
        });

        return operations;
    }

    // Save dataset stakeholders only
    async function saveDatasetStakeholders(operations) {
        try {
            // Handle deletions first
            for (const deletion of operations.deletions) {
                const success = await deleteStakeholderAPI(deletion.objectXPeopleId);
                if (!success) {
                    return false;
                }
            }

            // Handle updates
            for (const update of operations.updates) {
                const success = await updateStakeholderAPI(update);
                if (!success) {
                    return false;
                }
            }

            // Handle insertions
            for (const insertion of operations.insertions) {
                const success = await addStakeholderAPI(insertion);
                if (!success) {
                    return false;
                }
            }

            return true;
        } catch (error) {
            return false;
        }
    }

    // Save attribute stakeholders only
    async function saveAttributeStakeholders(operations) {
        try {
            // Handle deletions first
            for (const deletion of operations.deletions) {
                const success = await deleteAttributeStakeholderAPI(deletion.objectXPeopleId);
                if (!success) {
                    return false;
                }
            }

            // Handle updates
            for (const update of operations.updates) {
                const success = await updateAttributeStakeholderAPI(update);
                if (!success) {
                    return false;
                }
            }

            // Handle insertions
            for (const insertion of operations.insertions) {
                const success = await addAttributeStakeholderAPI(insertion);
                if (!success) {
                    return false;
                }
            }

            return true;
        } catch (error) {
            return false;
        }
    }

    // Determine what operations need to be performed (legacy function)
    function determineOperations() {
        const operations = {
            datasetInsertions: [],
            datasetUpdates: [],
            datasetDeletions: [],
            attributeInsertions: [],
            attributeUpdates: [],
            attributeDeletions: []
        };

        // Handle dataset stakeholders
        // Find deletions (items in original but not in current)
        originalData.forEach(original => {
            if (original.objectXPeopleId) {
                const found = stakeholdersData.find(current =>
                    current.objectXPeopleId === original.objectXPeopleId
                );
                if (!found) {
                    operations.datasetDeletions.push(original);
                }
            }
        });

        // Find insertions and updates for dataset stakeholders
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
                        operations.datasetUpdates.push({
                            ...current,
                            objectXPeopleId: existingStakeholder.objectXPeopleId
                        });
                    }
                    // If unchanged, skip it (don't add to insertions)
                } else {
                    // Truly new stakeholder
                    operations.datasetInsertions.push(current);
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
                        operations.datasetUpdates.push(current);
                    }
                }
            }
        });

        // Handle attribute stakeholders
        // Find deletions (items in original but not in current)
        //console.log('CHECKING DELETIONS - Original:', originalAttributeData.length, 'Current:', attributeStakeholdersData.length);
        originalAttributeData.forEach((original, index) => {
            if (original.objectXPeopleId) {
                const found = attributeStakeholdersData.find(current =>
                    current.objectXPeopleId === original.objectXPeopleId
                );
                if (!found) {
                    //console.log('FOUND DELETION:', original);
                    operations.attributeDeletions.push(original);
                }
            }
        });
        //console.log('FINAL DELETIONS:', operations.attributeDeletions.length);

        // Find insertions and updates for attribute stakeholders
        attributeStakeholdersData.forEach(current => {
            if (!current.objectXPeopleId) {
                // Check if this stakeholder already exists in originalAttributeData by peopleId + roleId + attributeId
                const existingStakeholder = originalAttributeData.find(orig => {
                    const origPeopleId = orig.peopleId ? parseInt(orig.peopleId, 10) : null;
                    const origRoleId = orig.roleId ? parseInt(orig.roleId, 10) : null;
                    const origAttributeId = orig.attributeId ? parseInt(orig.attributeId, 10) : null;
                    const currentPeopleId = current.peopleId ? parseInt(current.peopleId, 10) : null;
                    const currentRoleId = current.roleId ? parseInt(current.roleId, 10) : null;
                    const currentAttributeId = current.attributeId ? parseInt(current.attributeId, 10) : null;
                    return origPeopleId === currentPeopleId && origRoleId === currentRoleId && origAttributeId === currentAttributeId;
                });
                
                if (existingStakeholder) {
                    // Stakeholder exists - check if it changed
                    const currentStatusId = current.statusId ? parseInt(current.statusId, 10) : null;
                    const existingStatusId = existingStakeholder.statusId ? parseInt(existingStakeholder.statusId, 10) : null;
                    
                    if (currentStatusId !== existingStatusId) {
                        // Status changed - treat as update
                        operations.attributeUpdates.push({
                            ...current,
                            objectXPeopleId: existingStakeholder.objectXPeopleId
                        });
                    }
                    // If unchanged, skip it (don't add to insertions)
                } else {
                    // Truly new stakeholder
                    operations.attributeInsertions.push(current);
                }
            } else {
                // Existing item - check if changed
                const original = originalAttributeData.find(orig =>
                    orig.objectXPeopleId === current.objectXPeopleId
                );
                if (original) {
                    const hasChanged = (
                        current.roleId !== original.roleId ||
                        current.peopleId !== original.peopleId ||
                        current.statusId !== original.statusId ||
                        current.attributeId !== original.attributeId ||
                        current.delegateIpId !== original.delegateIpId
                    );
                    if (hasChanged) {
                        operations.attributeUpdates.push(current);
                    }
                }
            }
        });

        return operations;
    }

    // API call to add stakeholder
    async function addStakeholderAPI(stakeholder) {
        try {
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-stakeholder/${currentDatasetId}/stakeholders/edit${viewParam}`, {
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
            const viewParam = currentViewMode === 'changes' ? '?view=changes' : '';
            const response = await fetch(`/api/dataset-stakeholder/${currentDatasetId}/stakeholders/edit${viewParam}`, {
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

            if (!objectXPeopleId) {
                console.error('Invalid objectXPeopleId for deletion:', objectXPeopleId);
                return false;
            }

            const viewParam = currentViewMode === 'changes' ? '&view=changes' : '';
            const response = await fetch(`/api/dataset-stakeholder/${currentDatasetId}/stakeholders/edit?objectXPeopleId=${objectXPeopleId}${viewParam}`, {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                console.error('Delete API error:', errorData);
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            return true;
        } catch (error) {
            console.error('Error deleting stakeholder:', error);
            alert(`Failed to delete stakeholder: ${error.message}`);
            return false;
        }
    }

    // API call to add attribute stakeholder
    async function addAttributeStakeholderAPI(stakeholder) {
        try {
            const response = await fetch(`/api/Attribute-stakeholder/${stakeholder.attributeId}/stakeholders/edit`, {
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

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            return true;
        } catch (error) {
            console.error('Error adding attribute stakeholder:', error);
            return false;
        }
    }

    // API call to update attribute stakeholder
    async function updateAttributeStakeholderAPI(stakeholder) {
        try {
            const response = await fetch(`/api/Attribute-stakeholder/${stakeholder.attributeId}/stakeholders/edit`, {
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

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            return true;
        } catch (error) {
            console.error('Error updating attribute stakeholder:', error);
            return false;
        }
    }

    // API call to delete attribute stakeholder
    async function deleteAttributeStakeholderAPI(objectXPeopleId) {
        try {

            if (!objectXPeopleId) {
                console.error('Invalid objectXPeopleId for deletion:', objectXPeopleId);
                return false;
            }

            const response = await fetch(`/api/Attribute-stakeholder/stakeholders/edit?objectXPeopleId=${objectXPeopleId}`, {
                method: 'DELETE',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                const errorData = await response.json().catch(() => ({}));
                console.error('Delete attribute stakeholder API error:', errorData);
                throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
            }

            return true;
        } catch (error) {
            console.error('Error deleting attribute stakeholder:', error);
            alert(`Failed to delete attribute stakeholder: ${error.message}`);
            return false;
        }
    }

    // Escape HTML to prevent XSS
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#039;');
    }

    // Export functions to global scope
    window.DatasetStakeholderEdit = {
        init: initStakeholderEdit,
        addNewRow: addNewRow,
        addNewRowAfter: addNewRowAfter,
        addNewAttributeRow: addNewAttributeRow,
        addNewAttributeRowAfter: addNewAttributeRowAfter,
        updateStakeholder: updateStakeholder,
        updateAttributeStakeholder: updateAttributeStakeholder,
        deleteStakeholder: deleteStakeholder,
        deleteAttributeStakeholder: deleteAttributeStakeholder,
        saveStakeholders: saveStakeholders,
        hasDataChanged: hasDataChanged,
        getData: () => stakeholdersData,
        getAttributeData: () => attributeStakeholdersData,
        getAllData: () => [...stakeholdersData, ...attributeStakeholdersData],
        showAddStakeholderForm: showAddStakeholderForm,
        closeAddModal: closeAddModal,
        saveNewStakeholder: saveNewStakeholder
    };

})();

