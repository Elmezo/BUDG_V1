// Segment Form functionality for create/edit

let segmentFormState = {
    selectedSegmentId: null,
    segmentDetails: null,
    segmentAdminUsers: [],
    assignedOrgUnits: [],
    assignedUsers: [],
    currentTab: 'summary',
    currentAssignmentTab: 'manual',
    editMode: false,
    isDirty: false
};

function markSegmentDirty() {
    segmentFormState.isDirty = true;
}

function initializeSegmentForm(segmentId) {
    if (segmentId) {
        segmentFormState.selectedSegmentId = segmentId;
        segmentFormState.editMode = true;
        loadSegmentDetails(segmentId);
    } else {
        // New segment - clear all state
        segmentFormState.selectedSegmentId = null;
        segmentFormState.segmentDetails = null;
        segmentFormState.segmentAdminUsers = [];
        segmentFormState.assignedOrgUnits = [];
        segmentFormState.assignedUsers = [];
        segmentFormState.currentTab = 'summary';
        segmentFormState.currentAssignmentTab = 'manual';
        segmentFormState.editMode = false;
        showSegmentFormView();
    }
    
    setupFormEventListeners();
}

function setupFormEventListeners() {
    // Navigation
    document.getElementById('backToSegmentsBtn')?.addEventListener('click', showSegmentsList);
    document.getElementById('closeSegmentBtn')?.addEventListener('click', showSegmentsList);
    
    // Tabs
    document.getElementById('summaryTab')?.addEventListener('click', () => switchTab('summary'));
    document.getElementById('assignedUsersTab')?.addEventListener('click', () => switchTab('assigned-users'));
    
    // Assignment tabs
    document.getElementById('adminUsersManualTab')?.addEventListener('click', () => switchAssignmentTab('manual', 'admin'));
    document.getElementById('adminUsersSSOTab')?.addEventListener('click', () => switchAssignmentTab('sso', 'admin'));
    document.getElementById('assignedOrgUnitsTab')?.addEventListener('click', () => switchAssignmentTab('org-units', 'assigned'));
    document.getElementById('assignedUsersManualTab')?.addEventListener('click', () => switchAssignmentTab('manual', 'assigned'));
    document.getElementById('assignedUsersSSOTab')?.addEventListener('click', () => switchAssignmentTab('sso', 'assigned'));
    
    // Actions
    // IMPORTANT: passing saveSegment directly will pass the click event as the first argument,
    // which would be treated as truthy and incorrectly trigger "close after save".
    document.getElementById('saveSegmentBtn')?.addEventListener('click', () => saveSegment(false));
    document.getElementById('saveCloseSegmentBtn')?.addEventListener('click', () => saveSegment(true));
    document.getElementById('deleteSegmentBtn')?.addEventListener('click', deleteSegment);
    document.getElementById('addSegmentAdminUserBtn')?.addEventListener('click', () => openSelectAdminUsersModal());
    document.getElementById('deleteSegmentAdminUserBtn')?.addEventListener('click', deleteSelectedAdminUsers);
    document.getElementById('addAssignedEntityBtn')?.addEventListener('click', () => openAddAssignedEntityModal());
    document.getElementById('deleteAssignedEntityBtn')?.addEventListener('click', deleteSelectedAssignedEntities);
    
    // Row selection for tables
    setupTableRowSelection();
    
    // Modals
    setupModalEventListeners();
}

function setupModalEventListeners() {
    // Admin Users Modal
    document.getElementById('closeAdminUsersModal')?.addEventListener('click', () => closeModal('selectAdminUsersModal'));
    document.getElementById('cancelAdminUsersBtn')?.addEventListener('click', () => closeModal('selectAdminUsersModal'));
    document.getElementById('selectAdminUsersBtn')?.addEventListener('click', confirmSelectAdminUsers);
    document.getElementById('selectAdminUsersModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'selectAdminUsersModal') closeModal('selectAdminUsersModal');
    });
    
    // Org Units Modal
    document.getElementById('closeOrgUnitsModal')?.addEventListener('click', () => closeModal('selectOrgUnitsModal'));
    document.getElementById('cancelOrgUnitsBtn')?.addEventListener('click', () => closeModal('selectOrgUnitsModal'));
    document.getElementById('selectOrgUnitsBtn')?.addEventListener('click', confirmSelectOrgUnits);
    document.getElementById('selectOrgUnitsModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'selectOrgUnitsModal') closeModal('selectOrgUnitsModal');
    });
    
    // Users Modal
    document.getElementById('closeUsersModal')?.addEventListener('click', () => closeModal('selectUsersModal'));
    document.getElementById('cancelUsersBtn')?.addEventListener('click', () => closeModal('selectUsersModal'));
    document.getElementById('selectUsersBtn')?.addEventListener('click', confirmSelectUsers);
    document.getElementById('selectUsersModal')?.addEventListener('click', (e) => {
        if (e.target.id === 'selectUsersModal') closeModal('selectUsersModal');
    });
    
    // Filter inputs
    setupFilterListeners();
}

function setupFilterListeners() {
    // Admin Users filters
    ['filterAdminUserName', 'filterAdminUserOrgUnit', 'filterAdminUserProfile', 'filterAdminUserFunction', 'filterAdminUserEmail'].forEach(id => {
        document.getElementById(id)?.addEventListener('input', filterAdminUsers);
    });
    
    // Org Units filters
    ['filterOrgUnitName', 'filterOrgUnitDesc', 'filterOrgUnitParent'].forEach(id => {
        document.getElementById(id)?.addEventListener('input', filterOrgUnits);
    });
    
    // Users filters
    ['filterUserName', 'filterUserOrgUnit', 'filterUserProfile', 'filterUserFunction', 'filterUserEmail'].forEach(id => {
        document.getElementById(id)?.addEventListener('input', filterUsers);
    });
}

async function loadSegmentDetails(segmentId) {
    try {
        const response = await fetch(`/api/segments/${segmentId}`);
        if (!response.ok) {
            if (response.status === 404) {
                notify('Segment not found', 'error');
                showSegmentsList();
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        segmentFormState.segmentDetails = Array.isArray(data) ? data[0] : data;
        
        // Load related data (don't fail if assignments fail to load)
        try {
            await Promise.all([
                loadSegmentAdminUsers(segmentId),
                loadAssignedOrgUnits(segmentId),
                loadAssignedUsers(segmentId)
            ]);
        } catch (assignmentError) {
            console.warn('Some assignments failed to load:', assignmentError);
        }
        
        showSegmentFormView();
    } catch (error) {
        console.error('Error loading segment details:', error);
        notify('Error loading segment details: ' + error.message, 'error');
        showSegmentsList();
    }
}

function showSegmentFormView() {
    // Populate form fields
    if (segmentFormState.segmentDetails) {
        const name = segmentFormState.segmentDetails.name || segmentFormState.segmentDetails.Name || '';
        const description = segmentFormState.segmentDetails.description || segmentFormState.segmentDetails.Description || '';
        document.getElementById('segmentName').value = name;
        document.getElementById('segmentDescription').value = description;
        document.getElementById('segmentDetailTitle').textContent = 'Edit Segment';
        
        // Check permissions to show/hide management buttons
        getCurrentUser().then(user => {
            const isSuperAdmin = checkIsSuperAdmin(user?.role);
            const isSegmentAdmin = checkIsSegmentAdmin(user, segmentFormState.segmentAdminUsers);
            const canManage = isSuperAdmin || isSegmentAdmin;
            
            console.log('🔐 Permissions check:', { isSuperAdmin, isSegmentAdmin, canManage });
            
            // Show/hide delete button (Super Admin only)
            const deleteBtn = document.getElementById('deleteSegmentBtn');
            if (deleteBtn) {
                deleteBtn.style.display = isSuperAdmin ? 'block' : 'none';
            }
            
            // Show/hide management actions if not allowed
            const saveBtn = document.getElementById('saveSegmentBtn');
            const saveCloseBtn = document.getElementById('saveCloseSegmentBtn');
            const addAdminBtn = document.getElementById('addSegmentAdminUserBtn');
            const deleteAdminBtn = document.getElementById('deleteSegmentAdminUserBtn');
            const addAssignedBtn = document.getElementById('addAssignedEntityBtn');
            const deleteAssignedBtn = document.getElementById('deleteAssignedEntityBtn');
            
            if (saveBtn) saveBtn.style.display = canManage ? 'block' : 'none';
            if (saveCloseBtn) saveCloseBtn.style.display = canManage ? 'block' : 'none';
            if (addAdminBtn) addAdminBtn.style.display = canManage ? 'flex' : 'none';
            if (deleteAdminBtn) deleteAdminBtn.style.display = canManage ? 'flex' : 'none';
            if (addAssignedBtn) addAssignedBtn.style.display = canManage ? 'flex' : 'none';
            if (deleteAssignedBtn) deleteAssignedBtn.style.display = canManage ? 'flex' : 'none';
            
            // Disable inputs if not allowed
            const nameInput = document.getElementById('segmentName');
            const descInput = document.getElementById('segmentDescription');
            if (nameInput) nameInput.disabled = !canManage;
            if (descInput) descInput.disabled = !canManage;
        });
        
        // Enable ASSIGNED USERS tab for existing segments
        const assignedUsersTab = document.getElementById('assignedUsersTab');
        if (assignedUsersTab) {
            assignedUsersTab.disabled = false;
            assignedUsersTab.removeAttribute('title');
            assignedUsersTab.classList.remove('disabled');
        }
    } else {
        document.getElementById('segmentName').value = '';
        document.getElementById('segmentDescription').value = '';
        document.getElementById('segmentDetailTitle').textContent = 'Create Segment';
        document.getElementById('deleteSegmentBtn').style.display = 'none';
        
        // Only Super Admin can create segments
        getCurrentUser().then(user => {
            const isSuperAdmin = checkIsSuperAdmin(user?.role);
            const saveBtn = document.getElementById('saveSegmentBtn');
            const saveCloseBtn = document.getElementById('saveCloseSegmentBtn');
            if (saveBtn) saveBtn.style.display = isSuperAdmin ? 'block' : 'none';
            if (saveCloseBtn) saveCloseBtn.style.display = isSuperAdmin ? 'block' : 'none';
        });
        
        // Disable ASSIGNED USERS tab for new segments
        const assignedUsersTab = document.getElementById('assignedUsersTab');
        if (assignedUsersTab) {
            assignedUsersTab.disabled = true;
            assignedUsersTab.setAttribute('title', 'Save the segment first to assign users');
            assignedUsersTab.classList.add('disabled');
        }
    }
    
    // Reset tabs to default
    segmentFormState.currentTab = 'summary';
    segmentFormState.currentAssignmentTab = 'manual';
    
    // Update tab UI
    document.querySelectorAll('.segment-tab').forEach(btn => {
        btn.classList.remove('active');
        if (!btn.disabled && btn.dataset.tab === 'summary') {
            btn.classList.add('active');
        }
    });
    document.querySelectorAll('.tab-pane').forEach(pane => pane.style.display = 'none');
    const summaryTabContent = document.getElementById('summaryTabContent');
    if (summaryTabContent) {
        summaryTabContent.style.display = 'block';
    }
    
    // Reset assignment tabs for SUMMARY tab
    document.querySelectorAll('#adminUsersManualTab, #adminUsersSSOTab').forEach(btn => {
        if (btn.dataset.assignmentType === 'manual') {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
    
    // Reset assignment tabs for ASSIGNED USERS tab (when it becomes visible)
    document.querySelectorAll('#assignedOrgUnitsTab, #assignedUsersManualTab, #assignedUsersSSOTab').forEach(btn => {
        if (btn.dataset.assignmentType === 'org-units') {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });
    
    renderSegmentAdminUsers();
    renderAssignedEntities();
}

function showSegmentsList() {
    if (typeof showSegmentsContent === 'function') {
        const contentArea = document.querySelector('.content-area');
        showSegmentsContent(contentArea);
    } else {
        // Fallback: reload the page or navigate back
        window.location.reload();
    }
}

function switchTab(tab) {
    console.log('Switching to tab:', tab);
    
    // Check if ASSIGNED USERS tab is disabled
    if (tab === 'assigned-users') {
        const assignedUsersTab = document.getElementById('assignedUsersTab');
        if (assignedUsersTab && assignedUsersTab.disabled) {
            notify('Please save the segment first before assigning users', 'warning');
            return;
        }
    }
    
    segmentFormState.currentTab = tab;
    
    // Update tab buttons
    document.querySelectorAll('.segment-tab').forEach(btn => {
        btn.classList.remove('active');
        if (!btn.disabled && btn.dataset.tab === tab) {
            btn.classList.add('active');
        }
    });
    
    // Update tab content
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.style.display = 'none';
        pane.classList.remove('active');
    });
    
    // Convert tab name to camelCase for ID lookup
    // "assigned-users" -> "assignedUsers"
    const tabIdName = tab.replace(/-([a-z])/g, (match, letter) => letter.toUpperCase());
    const tabContent = document.getElementById(`${tabIdName}TabContent`);
    console.log('Looking for tab content:', `${tabIdName}TabContent`, 'Found:', tabContent);
    
    if (tabContent) {
        tabContent.style.display = 'block';
        tabContent.classList.add('active');
        console.log('Tab content should now be visible');
    } else {
        console.error(`Tab content not found for: ${tabIdName}TabContent`);
    }
    
    // When switching to assigned-users tab, set default assignment tab to 'org-units' and load data
    if (tab === 'assigned-users') {
        console.log('Initializing ASSIGNED USERS tab...');
        segmentFormState.currentAssignmentTab = 'org-units';
        // Update assignment tabs UI
        document.querySelectorAll('#assignedOrgUnitsTab, #assignedUsersManualTab, #assignedUsersSSOTab').forEach(btn => {
            if (btn.dataset.assignmentType === 'org-units') {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
        
        // Load assigned entities if we have a segment ID
        if (segmentFormState.selectedSegmentId) {
            console.log('Loading org units for segment:', segmentFormState.selectedSegmentId);
            loadAssignedOrgUnits(segmentFormState.selectedSegmentId).then(() => {
                console.log('Org units loaded, rendering...');
                renderAssignedEntities();
            });
        } else {
            console.log('No segment ID, rendering empty state');
            renderAssignedEntities();
        }
    }
}

function switchAssignmentTab(type, section) {
    segmentFormState.currentAssignmentTab = type;
    
    // Update assignment tab buttons
    const tabButtons = section === 'admin' 
        ? document.querySelectorAll('#adminUsersManualTab, #adminUsersSSOTab')
        : document.querySelectorAll('#assignedOrgUnitsTab, #assignedUsersManualTab, #assignedUsersSSOTab');
    
    tabButtons.forEach(btn => {
        if (btn && btn.dataset.assignmentType === type) {
            btn.classList.add('active');
        } else if (btn) {
            btn.classList.remove('active');
        }
    });
    
    // Render appropriate content based on section
    if (section === 'admin') {
        renderSegmentAdminUsers();
    } else {
        // Load data for assigned entities if segment ID exists
        if (segmentFormState.selectedSegmentId) {
            if (type === 'org-units') {
                loadAssignedOrgUnits(segmentFormState.selectedSegmentId).then(() => {
                    renderAssignedEntities();
                });
            } else if (type === 'manual') {
                loadAssignedUsers(segmentFormState.selectedSegmentId).then(() => {
                    renderAssignedEntities();
                });
            } else {
                renderAssignedEntities();
            }
        } else {
            renderAssignedEntities();
        }
    }
}

async function loadSegmentAdminUsers(segmentId) {
    try {
        const response = await fetch(`/api/segments/${segmentId}/admin-users`);
        if (!response.ok) {
            if (response.status === 404) {
                segmentFormState.segmentAdminUsers = [];
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        const adminUsers = Array.isArray(data) ? data : (data.data || []);
        segmentFormState.segmentAdminUsers = adminUsers.map(user => ({
            ...user,
            assignment_type: (user.assignment_type || 'Manual').toLowerCase()
        }));
    } catch (error) {
        console.error('Error loading segment admin users:', error);
        segmentFormState.segmentAdminUsers = [];
    }
}

async function loadAssignedOrgUnits(segmentId) {
    try {
        const response = await fetch(`/api/segments/${segmentId}/assigned-org-units`);
        if (!response.ok) {
            if (response.status === 404) {
                segmentFormState.assignedOrgUnits = [];
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        segmentFormState.assignedOrgUnits = Array.isArray(data) ? data : (data.data || []);
    } catch (error) {
        console.error('Error loading assigned org units:', error);
        segmentFormState.assignedOrgUnits = [];
    }
}

async function loadAssignedUsers(segmentId) {
    try {
        const response = await fetch(`/api/segments/${segmentId}/assigned-users`);
        if (!response.ok) {
            if (response.status === 404) {
                segmentFormState.assignedUsers = [];
                return;
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        segmentFormState.assignedUsers = Array.isArray(data) ? data : (data.data || []);
    } catch (error) {
        console.error('Error loading assigned users:', error);
        segmentFormState.assignedUsers = [];
    }
}

function renderSegmentAdminUsers() {
    const tbody = document.getElementById('segmentAdminUsersTableBody');
    if (!tbody) return;
    
    const adminUsers = segmentFormState.segmentAdminUsers.filter(u => {
        const assignmentType = segmentFormState.currentAssignmentTab;
        const userAssignmentType = (u.assignment_type || '').toLowerCase();
        if (assignmentType === 'manual') {
            return userAssignmentType === 'manual' || userAssignmentType === '' || !u.assignment_type;
        } else if (assignmentType === 'sso') {
            return userAssignmentType === 'sso';
        }
        return true;
    });
    
    if (adminUsers.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="5">No segment admin users assigned</td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = adminUsers.map(user => `
        <tr class="selectable-row" data-user-id="${user.user_id || user.id}" onclick="toggleRowSelection(event, this)">
            <td><i class="fas fa-user"></i> ${escapeHtml(user.name || `${user.first_name || ''} ${user.last_name || ''}`.trim())}</td>
            <td><i class="fas fa-sitemap"></i> ${escapeHtml(user.org_unit || 'N/A')}</td>
            <td>${escapeHtml(user.profile || 'N/A')}</td>
            <td>${escapeHtml(user.function || 'N/A')}</td>
            <td>${escapeHtml(user.email || 'N/A')}</td>
        </tr>
    `).join('');
    
    updateDeleteButtonState('deleteSegmentAdminUserBtn', 'segmentAdminUsersTableBody');
}

function renderAssignedEntities() {
    const tbody = document.getElementById('assignedEntitiesTableBody');
    const countElement = document.getElementById('assignedEntitiesCount');
    
    if (!tbody) {
        console.warn('assignedEntitiesTableBody element not found');
        return;
    }
    
    let entities = [];
    if (segmentFormState.currentAssignmentTab === 'org-units') {
        entities = segmentFormState.assignedOrgUnits || [];
        console.log('Rendering org units:', entities);
    } else if (segmentFormState.currentAssignmentTab === 'manual') {
        entities = segmentFormState.assignedUsers || [];
        console.log('Rendering manual users:', entities);
    } else {
        // SSO - future implementation
        entities = [];
        console.log('SSO tab - no entities');
    }
    
    if (entities.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="3">No entities assigned</td>
            </tr>
        `;
        if (countElement) {
            countElement.textContent = '0 records';
        }
        return;
    }
    
    tbody.innerHTML = entities.map(entity => {
        const name = entity.name || entity.Name || `${entity.First_Name || ''} ${entity.Last_Name || ''}`.trim() || 'N/A';
        const description = entity.description || entity.Description || entity.Email || entity.email || '';
        const parent = entity.parent || entity.Parent_Name || entity.Org_Unit_Name || entity.org_unit_name || 'N/A';
        const entityId = entity.id || entity.ID;
        const entityType = segmentFormState.currentAssignmentTab === 'org-units' ? 'org-unit' : 'user';
        const icon = segmentFormState.currentAssignmentTab === 'org-units' ? 'sitemap' : 'user';
        
        return `
            <tr class="selectable-row" data-entity-id="${entityId}" data-entity-type="${entityType}" onclick="toggleRowSelection(event, this)">
                <td><i class="fas fa-${icon}"></i> ${escapeHtml(name)}</td>
                <td>${escapeHtml(description)}</td>
                <td><i class="fas fa-sitemap"></i> ${escapeHtml(parent)}</td>
            </tr>
        `;
    }).join('');
    
    if (countElement) {
        countElement.textContent = `${entities.length} record${entities.length !== 1 ? 's' : ''}`;
    }
    updateDeleteButtonState('deleteAssignedEntityBtn', 'assignedEntitiesTableBody');
}

async function openSelectAdminUsersModal() {
    const modal = document.getElementById('selectAdminUsersModal');
    modal.classList.add('active');
    
    // Load users with Admin profile
    await loadAdminUsers();
}

async function loadAdminUsers() {
    const tbody = document.getElementById('adminUsersModalTableBody');
    if (!tbody) return;
    
    tbody.innerHTML = `
        <tr class="loading-row">
            <td colspan="5"><i class="fas fa-spinner fa-spin"></i> Loading users...</td>
        </tr>
    `;
    
    try {
        const response = await fetch('/api/people');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        const people = Array.isArray(data) ? data : (data.data || []);
        
        // Only Admin-profile users can be selected (System_Role = 1)
        const adminUsers = people.filter(p => {
            // Check if System_Role is 1 (admin)
            const systemRole = p.System_Role || p.system_role;
            // Handle both number and string values, and null/undefined
            if (systemRole == null) return false;
            return Number(systemRole) === 1;
        });
        
        if (adminUsers.length === 0) {
            tbody.innerHTML = `
                <tr class="empty-row">
                    <td colspan="5">
                        <i class="fas fa-info-circle"></i> No users with Admin profile found. 
                        Only users with Admin profile can be assigned as Segment Admin.
                    </td>
                </tr>
            `;
            return;
        }
        
        window.adminUsersModalData = adminUsers;
        renderAdminUsersModal(adminUsers);
    } catch (error) {
        console.error('Error loading admin users:', error);
        tbody.innerHTML = `
            <tr class="error-row">
                <td colspan="5">Error loading users</td>
            </tr>
        `;
    }
}

function renderAdminUsersModal(users) {
    const tbody = document.getElementById('adminUsersModalTableBody');
    if (!tbody) return;
    
    if (users.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="5">No admin users found</td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = users.map(user => {
        // Safely extract and convert all values to strings
        const firstName = String(user.First_Name || user.first_name || '');
        const lastName = String(user.Last_Name || user.last_name || '');
        const name = `${firstName} ${lastName}`.trim() || 'N/A';
        const orgUnit = String(user.Org_Unit_Name || user.org_unit_name || 'N/A');
        // Get role name from role.primaryname via system_role foreign key (API joins role table)
        // API returns: system_role_name (from role.primaryname) and System_Role_Name
        const profile = String(user.system_role_name || user.System_Role_Name || 'N/A');
        const function_ = String(user.Function || user.function || 'N/A');
        const email = String(user.Email || user.email || 'N/A');
        
        return `
            <tr data-user-id="${user.ID || user.id}" onclick="toggleUserSelection(event, this)">
                <td><i class="fas fa-user"></i> ${escapeHtml(name)}</td>
                <td><i class="fas fa-sitemap"></i> ${escapeHtml(orgUnit)}</td>
                <td>${escapeHtml(profile)}</td>
                <td>${escapeHtml(function_)}</td>
                <td>${escapeHtml(email)}</td>
            </tr>
        `;
    }).join('');
}

function toggleUserSelection(event, row) {
    // Backward compatible: some call sites pass only (row)
    if (event && event.tagName && !row) {
        row = event;
        event = null;
    }
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    if (!row) return;
    const tbody = row.closest('tbody');
    const isMulti = !!(event && (event.ctrlKey || event.metaKey));
    if (!isMulti && tbody) {
        tbody.querySelectorAll('tr.selected').forEach(r => {
            if (r !== row) r.classList.remove('selected');
        });
        row.classList.add('selected');
        return;
    }
    row.classList.toggle('selected');
}

function filterAdminUsers() {
    const nameFilter = String(document.getElementById('filterAdminUserName')?.value || '').toLowerCase();
    const orgUnitFilter = String(document.getElementById('filterAdminUserOrgUnit')?.value || '').toLowerCase();
    const profileFilter = String(document.getElementById('filterAdminUserProfile')?.value || '').toLowerCase();
    const functionFilter = String(document.getElementById('filterAdminUserFunction')?.value || '').toLowerCase();
    const emailFilter = String(document.getElementById('filterAdminUserEmail')?.value || '').toLowerCase();
    
    const users = window.adminUsersModalData || [];
    const filtered = users.filter(user => {
        // Safely extract and convert all values to strings
        const firstName = String(user.First_Name || user.first_name || '');
        const lastName = String(user.Last_Name || user.last_name || '');
        const name = `${firstName} ${lastName}`.trim().toLowerCase();
        const orgUnit = String(user.Org_Unit_Name || user.org_unit_name || '').toLowerCase();
        // Get role name from role.primaryname via system_role foreign key for filtering
        const profile = String(user.system_role_name || user.System_Role_Name || '').toLowerCase();
        const function_ = String(user.Function || user.function || '').toLowerCase();
        const email = String(user.Email || user.email || '').toLowerCase();
        
        return name.includes(nameFilter) &&
               orgUnit.includes(orgUnitFilter) &&
               profile.includes(profileFilter) &&
               function_.includes(functionFilter) &&
               email.includes(emailFilter);
    });
    
    renderAdminUsersModal(filtered);
}

function confirmSelectAdminUsers() {
    const selectedRows = document.querySelectorAll('#adminUsersModalTableBody tr.selected');
    const selectedUsers = Array.from(selectedRows).map(row => {
        const userId = row.dataset.userId;
        return window.adminUsersModalData.find(u => (u.ID || u.id) == userId);
    });
    
    // Add to segment admin users
    selectedUsers.forEach(user => {
        const userId = user.ID || user.id;
        if (!segmentFormState.segmentAdminUsers.find(u => (u.user_id || u.id) == userId)) {
            segmentFormState.segmentAdminUsers.push({
                user_id: userId,
                id: userId,
                name: `${user.First_Name || user.first_name || ''} ${user.Last_Name || user.last_name || ''}`.trim(),
                org_unit: user.Org_Unit_Name || user.org_unit_name,
                // Get role name from role.primaryname via system_role foreign key (API joins role table)
                profile: user.system_role_name || user.System_Role_Name || 'N/A',
                function: user.Function || user.function,
                email: user.Email || user.email,
                assignment_type: segmentFormState.currentAssignmentTab === 'sso' ? 'sso' : 'manual'
            });
        }
    });

    markSegmentDirty();
    renderSegmentAdminUsers();
    closeModal('selectAdminUsersModal');
}

function openAddAssignedEntityModal() {
    if (segmentFormState.currentAssignmentTab === 'org-units') {
        openSelectOrgUnitsModal();
    } else if (segmentFormState.currentAssignmentTab === 'manual') {
        openSelectUsersModal();
    } else {
        notify('SSO assignment not yet implemented', 'info');
    }
}

async function openSelectOrgUnitsModal() {
    const modal = document.getElementById('selectOrgUnitsModal');
    modal.classList.add('active');
    await loadOrgUnits();
}

async function loadOrgUnits() {
    const tbody = document.getElementById('orgUnitsModalTableBody');
    if (!tbody) return;
    
    tbody.innerHTML = `
        <tr class="loading-row">
            <td colspan="3"><i class="fas fa-spinner fa-spin"></i> Loading org units...</td>
        </tr>
    `;
    
    try {
        const response = await fetch('/api/org-units');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        const orgUnits = Array.isArray(data) ? data : (data.data || []);
        
        window.orgUnitsModalData = orgUnits;
        renderOrgUnitsModal(orgUnits);
    } catch (error) {
        console.error('Error loading org units:', error);
        tbody.innerHTML = `
            <tr class="error-row">
                <td colspan="3">Error loading org units</td>
            </tr>
        `;
    }
}

function renderOrgUnitsModal(orgUnits) {
    const tbody = document.getElementById('orgUnitsModalTableBody');
    if (!tbody) return;
    
    if (orgUnits.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="3">No org units found</td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = orgUnits.map(orgUnit => {
        // Safely extract and convert all values to strings
        const name = String(orgUnit.Name || orgUnit.name || 'N/A');
        const description = String(orgUnit.Description || orgUnit.description || 'N/A');
        const parent = String(orgUnit.Parent_Name || orgUnit.parent_name || 'N/A');
        
        return `
            <tr data-org-unit-id="${orgUnit.ID || orgUnit.id}" onclick="toggleOrgUnitSelection(event, this)">
                <td><i class="fas fa-sitemap"></i> ${escapeHtml(name)}</td>
                <td>${escapeHtml(description)}</td>
                <td><i class="fas fa-sitemap"></i> ${escapeHtml(parent)}</td>
            </tr>
        `;
    }).join('');
}

function toggleOrgUnitSelection(event, row) {
    // Backward compatible: some call sites may pass only (row)
    if (event && event.tagName && !row) {
        row = event;
        event = null;
    }
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    if (!row) return;
    const tbody = row.closest('tbody');
    const isMulti = !!(event && (event.ctrlKey || event.metaKey));
    if (!isMulti && tbody) {
        tbody.querySelectorAll('tr.selected').forEach(r => {
            if (r !== row) r.classList.remove('selected');
        });
        row.classList.add('selected');
        return;
    }
    row.classList.toggle('selected');
}

function filterOrgUnits() {
    const nameFilter = String(document.getElementById('filterOrgUnitName')?.value || '').toLowerCase();
    const descFilter = String(document.getElementById('filterOrgUnitDesc')?.value || '').toLowerCase();
    const parentFilter = String(document.getElementById('filterOrgUnitParent')?.value || '').toLowerCase();
    
    const orgUnits = window.orgUnitsModalData || [];
    const filtered = orgUnits.filter(orgUnit => {
        // Safely extract and convert all values to strings
        const name = String(orgUnit.Name || orgUnit.name || '').toLowerCase();
        const description = String(orgUnit.Description || orgUnit.description || '').toLowerCase();
        const parent = String(orgUnit.Parent_Name || orgUnit.parent_name || '').toLowerCase();
        
        return name.includes(nameFilter) &&
               description.includes(descFilter) &&
               parent.includes(parentFilter);
    });
    
    renderOrgUnitsModal(filtered);
}

function confirmSelectOrgUnits() {
    const selectedRows = document.querySelectorAll('#orgUnitsModalTableBody tr.selected');
    const selectedOrgUnits = Array.from(selectedRows).map(row => {
        const orgUnitId = row.dataset.orgUnitId;
        return window.orgUnitsModalData.find(o => (o.ID || o.id) == orgUnitId);
    });
    
    // Add to assigned org units
    selectedOrgUnits.forEach(orgUnit => {
        const orgUnitId = orgUnit.ID || orgUnit.id;
        if (!segmentFormState.assignedOrgUnits.find(o => (o.id || o.ID) == orgUnitId)) {
            segmentFormState.assignedOrgUnits.push({
                id: orgUnitId,
                ID: orgUnitId,
                name: orgUnit.Name || orgUnit.name,
                Name: orgUnit.Name || orgUnit.name,
                description: orgUnit.Description || orgUnit.description,
                Description: orgUnit.Description || orgUnit.description,
                parent: orgUnit.Parent_Name || orgUnit.parent_name,
                Parent_Name: orgUnit.Parent_Name || orgUnit.parent_name
            });
        }
    });

    markSegmentDirty();
    renderAssignedEntities();
    closeModal('selectOrgUnitsModal');
}

async function openSelectUsersModal() {
    const modal = document.getElementById('selectUsersModal');
    modal.classList.add('active');
    await loadUsers();
}

async function loadUsers() {
    const tbody = document.getElementById('usersModalTableBody');
    if (!tbody) return;
    
    tbody.innerHTML = `
        <tr class="loading-row">
            <td colspan="5"><i class="fas fa-spinner fa-spin"></i> Loading users...</td>
        </tr>
    `;
    
    try {
        const response = await fetch('/api/people');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        const people = Array.isArray(data) ? data : (data.data || []);
        
        window.usersModalData = people;
        renderUsersModal(people);
    } catch (error) {
        console.error('Error loading users:', error);
        tbody.innerHTML = `
            <tr class="error-row">
                <td colspan="5">Error loading users</td>
            </tr>
        `;
    }
}

function renderUsersModal(users) {
    const tbody = document.getElementById('usersModalTableBody');
    if (!tbody) return;
    
    if (users.length === 0) {
        tbody.innerHTML = `
            <tr class="empty-row">
                <td colspan="5">No users found</td>
            </tr>
        `;
        return;
    }
    
    tbody.innerHTML = users.map(user => {
        // Safely extract and convert all values to strings
        const firstName = String(user.First_Name || user.first_name || '');
        const lastName = String(user.Last_Name || user.last_name || '');
        const name = `${firstName} ${lastName}`.trim() || 'N/A';
        const orgUnit = String(user.Org_Unit_Name || user.org_unit_name || 'N/A');
        // Get role name from role.primaryname via system_role foreign key (API joins role table)
        // API returns: system_role_name (from role.primaryname) and System_Role_Name
        const profile = String(user.system_role_name || user.System_Role_Name || 'N/A');
        const function_ = String(user.Function || user.function || 'N/A');
        const email = String(user.Email || user.email || 'N/A');
        
        return `
            <tr data-user-id="${user.ID || user.id}" onclick="toggleUserSelection(event, this)">
                <td><i class="fas fa-user"></i> ${escapeHtml(name)}</td>
                <td><i class="fas fa-sitemap"></i> ${escapeHtml(orgUnit)}</td>
                <td>${escapeHtml(profile)}</td>
                <td>${escapeHtml(function_)}</td>
                <td>${escapeHtml(email)}</td>
            </tr>
        `;
    }).join('');
}

function filterUsers() {
    const nameFilter = String(document.getElementById('filterUserName')?.value || '').toLowerCase();
    const orgUnitFilter = String(document.getElementById('filterUserOrgUnit')?.value || '').toLowerCase();
    const profileFilter = String(document.getElementById('filterUserProfile')?.value || '').toLowerCase();
    const functionFilter = String(document.getElementById('filterUserFunction')?.value || '').toLowerCase();
    const emailFilter = String(document.getElementById('filterUserEmail')?.value || '').toLowerCase();
    
    const users = window.usersModalData || [];
    const filtered = users.filter(user => {
        // Safely extract and convert all values to strings
        const firstName = String(user.First_Name || user.first_name || '');
        const lastName = String(user.Last_Name || user.last_name || '');
        const name = `${firstName} ${lastName}`.trim().toLowerCase();
        const orgUnit = String(user.Org_Unit_Name || user.org_unit_name || '').toLowerCase();
        // Get role name from role.primaryname via system_role foreign key for filtering
        const profile = String(user.system_role_name || user.System_Role_Name || '').toLowerCase();
        const function_ = String(user.Function || user.function || '').toLowerCase();
        const email = String(user.Email || user.email || '').toLowerCase();
        
        return name.includes(nameFilter) &&
               orgUnit.includes(orgUnitFilter) &&
               profile.includes(profileFilter) &&
               function_.includes(functionFilter) &&
               email.includes(emailFilter);
    });
    
    renderUsersModal(filtered);
}

function confirmSelectUsers() {
    const selectedRows = document.querySelectorAll('#usersModalTableBody tr.selected');
    const selectedUsers = Array.from(selectedRows).map(row => {
        const userId = row.dataset.userId;
        return window.usersModalData.find(u => (u.ID || u.id) == userId);
    });
    
    // Add to assigned users
    selectedUsers.forEach(user => {
        const userId = user.ID || user.id;
        if (!segmentFormState.assignedUsers.find(u => (u.id || u.ID) == userId)) {
            segmentFormState.assignedUsers.push({
                id: userId,
                ID: userId,
                name: `${user.First_Name || user.first_name || ''} ${user.Last_Name || user.last_name || ''}`.trim(),
                description: user.Description || user.description || '',
                Description: user.Description || user.description || '',
                parent: user.Org_Unit_Name || user.org_unit_name,
                Parent_Name: user.Org_Unit_Name || user.org_unit_name
            });
        }
    });

    markSegmentDirty();
    renderAssignedEntities();
    closeModal('selectUsersModal');
}

function closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modal.classList.remove('active');
        // Clear selections
        modal.querySelectorAll('tr.selected').forEach(row => row.classList.remove('selected'));
        // Clear filters
        modal.querySelectorAll('input[type="text"]').forEach(input => input.value = '');
    }
}

async function saveSegment(closeAfterSave = false) {
    const name = document.getElementById('segmentName')?.value.trim();
    const description = document.getElementById('segmentDescription')?.value.trim();
    
    if (!name) {
        notify('Segment name is required', 'error');
        return;
    }
    
    if (!description) {
        notify('Segment description is required', 'error');
        return;
    }
    
    // Validate that at least one admin user is assigned
    if (!segmentFormState.segmentAdminUsers || segmentFormState.segmentAdminUsers.length === 0) {
        notify('You must assign at least one Segment Admin user before saving', 'error');
        // Switch to the SUMMARY tab to show the admin users section
        switchTab('summary');
        return;
    }
    
    try {
        // Format data for backend
        // Enterprise is the default parent segment - all new segments are created under Enterprise
        const segmentData = {
            name: name,
            description: description,
            parent_segment: 'Enterprise', // Default parent segment
            admin_users: segmentFormState.segmentAdminUsers.map(u => ({
                user_id: u.user_id || u.id,
                assignment_type: u.assignment_type || 'manual'
            })),
            assigned_org_units: segmentFormState.assignedOrgUnits.map(o => ({
                id: o.id || o.ID
            })),
            assigned_users: segmentFormState.assignedUsers.map(u => ({
                id: u.id || u.ID
            }))
        };
        
        const url = segmentFormState.selectedSegmentId 
            ? `/api/segments/${segmentFormState.selectedSegmentId}`
            : '/api/segments';
        const method = segmentFormState.selectedSegmentId ? 'PUT' : 'POST';
        
        const response = await fetch(url, {
            method: method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(segmentData)
        });
        
        if (!response.ok) {
            let errMsg = `HTTP error! status: ${response.status}`;
            try {
                const errJson = await response.json();
                if (errJson && (errJson.error || errJson.message)) {
                    errMsg = errJson.error || errJson.message;
                }
            } catch (e) {
                // ignore JSON parsing errors
            }
            throw new Error(errMsg);
        }
        
        const result = await response.json();
        notify('Segment saved successfully', 'success');
        
        // Update selected segment ID if it's a new segment
        const wasNewSegment = !segmentFormState.selectedSegmentId;
        if (result.id && !segmentFormState.selectedSegmentId) {
            segmentFormState.selectedSegmentId = typeof result.id === 'number' ? result.id : parseInt(result.id);
        }
        
        // Enable ASSIGNED USERS tab after saving
        if (wasNewSegment) {
            const assignedUsersTab = document.getElementById('assignedUsersTab');
            if (assignedUsersTab) {
                assignedUsersTab.disabled = false;
                assignedUsersTab.removeAttribute('title');
                assignedUsersTab.classList.remove('disabled');
            }
        }
        
        // Reload segment details to get fresh data from backend
        if (segmentFormState.selectedSegmentId) {
            await loadSegmentDetails(segmentFormState.selectedSegmentId);
        }
        
        // Refresh the segments cube panel to show the new/updated segment
        if (typeof window.updateSegmentsCubeCount === 'function') {
            console.log('🔄 Refreshing segments cube after segment save...');
            await window.updateSegmentsCubeCount();
        }
        
        if (closeAfterSave) {
            showSegmentsList();
        }
    } catch (error) {
        console.error('Error saving segment:', error);
        notify('Error saving segment: ' + error.message, 'error');
    }
}

// Replace the deleteSegment function with this updated version:

async function deleteSegment() {
    if (!segmentFormState.selectedSegmentId) {
        notify('No segment selected', 'warning');
        return;
    }

    // Cannot delete Enterprise segment
    if (segmentFormState.selectedSegmentId === 1) {
        notify('Cannot delete the Enterprise segment', 'error');
        return;
    }

    const segmentName = segmentFormState.segmentDetails?.name || segmentFormState.segmentDetails?.Name || 'this segment';

    try {
        // First, check if segment has objects
        const objectCountsResponse = await fetch(`/api/segments/${segmentFormState.selectedSegmentId}/object-counts`);
        let hasObjects = false;
        let objectCounts = {};
        let totalObjects = 0;

        if (objectCountsResponse.ok) {
            const countsData = await objectCountsResponse.json();
            hasObjects = countsData.hasObjects;
            objectCounts = countsData.counts || {};
            totalObjects = countsData.totalObjects || 0;
        }

        // Show appropriate dialog based on whether segment has objects
        let confirmed = false;
        let targetSegmentId = 1; // Default to Enterprise
        let targetSegmentName = 'Enterprise';

        if (hasObjects) {
            // Show dialog to choose where to move objects (Enterprise or private segment)
            const objectList = Object.entries(objectCounts)
                .map(([type, count]) => `${type}: ${count}`)
                .join(', ');

            const result = await showSegmentDeletionDialog({
                segmentName: segmentName,
                totalObjects: totalObjects,
                objectList: objectList,
                excludeSegmentId: segmentFormState.selectedSegmentId
            });

            if (!result.confirmed) {
                return;
            }

            confirmed = true;
            targetSegmentId = result.targetSegmentId;
            targetSegmentName = result.targetSegmentName || (targetSegmentId === 1 ? 'Enterprise' : `Segment ${targetSegmentId}`);
        } else {
            // Simple confirmation for segments without objects
            confirmed = await showConfirmDialog({
                title: 'Delete Segment',
                message: `Are you sure you want to delete segment "<strong>${segmentName}</strong>"?`,
                details: 'This action cannot be undone.',
                confirmText: 'Delete',
                cancelText: 'Cancel',
                type: 'danger'
            });
        }

        if (!confirmed) {
            return;
        }

        // Delete with reassignment to selected target segment
        const response = await fetch(`/api/segments/${segmentFormState.selectedSegmentId}?targetSegment=${targetSegmentId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || `HTTP error! status: ${response.status}`);
        }

        const result = await response.json();

        if (hasObjects) {
            notify(`Segment deleted successfully. ${totalObjects} object(s) moved to ${targetSegmentName} segment.`, 'success');
        } else {
            notify('Segment deleted successfully', 'success');
        }

        // Refresh the segments cube panel to update the count
        if (typeof window.updateSegmentsCubeCount === 'function') {
            console.log('🔄 Refreshing segments cube after segment delete...');
            await window.updateSegmentsCubeCount();
        }

        showSegmentsList();
    } catch (error) {
        console.error('Error deleting segment:', error);
        notify('Error deleting segment: ' + error.message, 'error');
    }
}

// Show dialog for segment deletion with target segment selection
async function showSegmentDeletionDialog({ segmentName, totalObjects, objectList, excludeSegmentId }) {
    return new Promise(async (resolve) => {
        // Load all segments (excluding the one being deleted)
        let allSegments = [];
        try {
            const response = await fetch('/api/segments');
            if (response.ok) {
                const data = await response.json();
                allSegments = Array.isArray(data) ? data : [];
                // Filter out the segment being deleted
                allSegments = allSegments.filter(seg => seg.id !== excludeSegmentId && seg.ID !== excludeSegmentId);
            }
        } catch (error) {
            console.error('Error loading segments:', error);
        }

        // Ensure Enterprise is available
        const enterpriseSegment = allSegments.find(s => (s.id === 1 || s.ID === 1));
        if (!enterpriseSegment) {
            allSegments.unshift({ id: 1, name: 'Enterprise', ID: 1, Name: 'Enterprise' });
        }

        // Create modal overlay
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.style.cssText = `
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
            animation: fadeIn 0.2s ease-out;
        `;

        // Create modal dialog
        const dialog = document.createElement('div');
        dialog.style.cssText = `
            background: white;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
            max-width: 600px;
            width: 90%;
            max-height: 90vh;
            overflow-y: auto;
            animation: slideIn 0.3s ease-out;
        `;

        // Modal header
        const header = document.createElement('div');
        header.style.cssText = `
            padding: 20px 24px;
            border-bottom: 1px solid #e0e0e0;
        `;
        header.innerHTML = `
            <h3 style="margin: 0; font-size: 18px; font-weight: 600; color: #333; font-family: 'Inter', sans-serif;">
                <i class="fas fa-exclamation-triangle" style="color: #ffc107; margin-right: 8px;"></i>
                Delete Segment with Objects
            </h3>
        `;

        // Modal body
        const body = document.createElement('div');
        body.style.cssText = `
            padding: 24px;
            font-family: 'Inter', sans-serif;
        `;

        // Warning message
        const warningDiv = document.createElement('div');
        warningDiv.style.cssText = `
            background: #fff3cd;
            border: 1px solid #ffc107;
            border-radius: 4px;
            padding: 12px 16px;
            margin-bottom: 20px;
        `;
        warningDiv.innerHTML = `
            <p style="margin: 0 0 8px 0; font-size: 14px; color: #856404; font-weight: 600;">
                <i class="fas fa-exclamation-triangle" style="margin-right: 8px;"></i>
                Warning: Segment "<strong>${segmentName}</strong>" has <strong>${totalObjects}</strong> object(s) assigned
            </p>
            <p style="margin: 0; font-size: 13px; color: #856404;">
                ${objectList}
            </p>
        `;

        // Details message
        const detailsP = document.createElement('p');
        detailsP.style.cssText = `
            margin: 0 0 20px 0;
            font-size: 14px;
            color: #555;
            line-height: 1.6;
        `;
        detailsP.innerHTML = `
            Choose one option before deleting this segment:
            <br><strong>1)</strong> Move all objects to <strong>Enterprise</strong>, or
            <br><strong>2)</strong> Move all objects to <strong>another private segment</strong>.
        `;

        // Target segment selection
        const selectionDiv = document.createElement('div');
        selectionDiv.style.cssText = 'margin-bottom: 20px;';

        // Radio button for Enterprise
        const enterpriseRadio = document.createElement('input');
        enterpriseRadio.type = 'radio';
        enterpriseRadio.name = 'targetSegment';
        enterpriseRadio.id = 'targetEnterprise';
        enterpriseRadio.value = '1';
        enterpriseRadio.checked = true; // Default to Enterprise
        enterpriseRadio.style.cssText = 'margin-right: 8px; cursor: pointer;';

        const enterpriseLabel = document.createElement('label');
        enterpriseLabel.htmlFor = 'targetEnterprise';
        enterpriseLabel.style.cssText = 'cursor: pointer; font-size: 14px; color: #333;';
        enterpriseLabel.textContent = 'Move all objects to Enterprise';

        const enterpriseDiv = document.createElement('div');
        enterpriseDiv.style.cssText = 'margin-bottom: 12px; padding: 12px; background: #f8f9fa; border-radius: 4px;';
        enterpriseDiv.appendChild(enterpriseRadio);
        enterpriseDiv.appendChild(enterpriseLabel);

        // Radio button for private segment
        const privateRadio = document.createElement('input');
        privateRadio.type = 'radio';
        privateRadio.name = 'targetSegment';
        privateRadio.id = 'targetPrivate';
        privateRadio.value = 'private';
        privateRadio.style.cssText = 'margin-right: 8px; cursor: pointer;';

        const privateLabel = document.createElement('label');
        privateLabel.htmlFor = 'targetPrivate';
        privateLabel.style.cssText = 'cursor: pointer; font-size: 14px; color: #333;';
        privateLabel.textContent = 'Move all objects to another private segment';

        const privateDiv = document.createElement('div');
        privateDiv.style.cssText = 'padding: 12px; background: #f8f9fa; border-radius: 4px;';

        // Dropdown for private segments
        const segmentSelect = document.createElement('select');
        segmentSelect.id = 'privateSegmentSelect';
        segmentSelect.style.cssText = `
            width: 100%;
            padding: 8px 12px;
            margin-top: 8px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 14px;
            background: white;
            cursor: pointer;
        `;
        segmentSelect.disabled = true; // Disabled by default (Enterprise selected)

        // Populate dropdown with private segments (exclude Enterprise)
        const privateSegments = allSegments.filter(s => {
            const id = s.id || s.ID;
            return id !== 1;
        });

        if (privateSegments.length === 0) {
            segmentSelect.innerHTML = '<option value="">No private segments available</option>';
            segmentSelect.disabled = true;
            privateRadio.disabled = true;
        } else {
            segmentSelect.innerHTML = '<option value="">-- Select a private segment --</option>';
            privateSegments.forEach(seg => {
                const option = document.createElement('option');
                const segId = seg.id || seg.ID;
                const segName = seg.name || seg.Name || `Segment ${segId}`;
                option.value = segId;
                option.textContent = `${segName} (ID: ${segId})`;
                segmentSelect.appendChild(option);
            });
        }

        // Enable/disable dropdown based on radio selection
        enterpriseRadio.addEventListener('change', () => {
            if (enterpriseRadio.checked) {
                segmentSelect.disabled = true;
                segmentSelect.value = '';
                privateWarning.style.display = 'none';
            }
        });

        privateRadio.addEventListener('change', () => {
            if (privateRadio.checked) {
                segmentSelect.disabled = false;
                if (!segmentSelect.value && privateSegments.length > 0) {
                    segmentSelect.value = privateSegments[0].id || privateSegments[0].ID;
                }
                privateWarning.style.display = 'block';
            }
        });

        privateDiv.appendChild(privateRadio);
        privateDiv.appendChild(privateLabel);
        privateDiv.appendChild(segmentSelect);
        
        const privateWarning = document.createElement('div');
        privateWarning.style.cssText = `
            margin-top: 10px;
            padding: 10px 12px;
            border-radius: 4px;
            border: 1px solid #f5c6cb;
            background: #fff5f5;
            color: #842029;
            font-size: 12px;
            line-height: 1.5;
            display: none;
        `;
        privateWarning.innerHTML = `
            <strong>Warning:</strong> If you move all objects to another private segment, they will be moved even if
            some stakeholders do not have access to that private segment. Those stakeholders will get a
            permission denied page when trying to open those objects by URL.
        `;
        privateDiv.appendChild(privateWarning);

        selectionDiv.appendChild(enterpriseDiv);
        selectionDiv.appendChild(privateDiv);

        body.appendChild(warningDiv);
        body.appendChild(detailsP);
        body.appendChild(selectionDiv);

        // Modal footer
        const footer = document.createElement('div');
        footer.style.cssText = `
            padding: 16px 24px;
            border-top: 1px solid #e0e0e0;
            display: flex;
            justify-content: flex-end;
            gap: 12px;
        `;

        const cancelBtn = document.createElement('button');
        cancelBtn.textContent = 'Cancel';
        cancelBtn.style.cssText = `
            padding: 8px 20px;
            border: 1px solid #ddd;
            background: white;
            color: #555;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            font-family: 'Inter', sans-serif;
            transition: all 0.2s;
        `;
        cancelBtn.onmouseover = () => {
            cancelBtn.style.background = '#f5f5f5';
            cancelBtn.style.borderColor = '#ccc';
        };
        cancelBtn.onmouseout = () => {
            cancelBtn.style.background = 'white';
            cancelBtn.style.borderColor = '#ddd';
        };

        const confirmBtn = document.createElement('button');
        confirmBtn.textContent = 'Delete & Move Objects';
        confirmBtn.style.cssText = `
            padding: 8px 20px;
            border: none;
            background: #ffc107;
            color: #333;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            font-family: 'Inter', sans-serif;
            transition: all 0.2s;
        `;
        confirmBtn.onmouseover = () => {
            confirmBtn.style.background = '#e0a800';
        };
        confirmBtn.onmouseout = () => {
            confirmBtn.style.background = '#ffc107';
        };

        // Add animations
        const style = document.createElement('style');
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes slideIn {
                from {
                    opacity: 0;
                    transform: translateY(-20px);
                }
                to {
                    opacity: 1;
                    transform: translateY(0);
                }
            }
        `;
        document.head.appendChild(style);

        // Event handlers
        const cleanup = () => {
            overlay.style.opacity = '0';
            dialog.style.transform = 'translateY(-20px)';
            dialog.style.opacity = '0';
            setTimeout(() => {
                overlay.remove();
                style.remove();
            }, 200);
        };

        cancelBtn.onclick = () => {
            cleanup();
            resolve({ confirmed: false, targetSegmentId: null });
        };

        confirmBtn.onclick = () => {
            let selectedTargetId = 1; // Default to Enterprise
            let targetSegmentName = 'Enterprise';

            if (privateRadio.checked) {
                const selectedValue = segmentSelect.value;
                if (!selectedValue) {
                    notify('Please select a private segment', 'warning');
                    return;
                }
                selectedTargetId = parseInt(selectedValue, 10);
                const selectedSegment = privateSegments.find(s => (s.id || s.ID) === selectedTargetId);
                targetSegmentName = selectedSegment ? (selectedSegment.name || selectedSegment.Name) : 'selected segment';
            }

            cleanup();
            resolve({ 
                confirmed: true, 
                targetSegmentId: selectedTargetId,
                targetSegmentName: targetSegmentName
            });
        };

        overlay.onclick = (e) => {
            if (e.target === overlay) {
                cleanup();
                resolve({ confirmed: false, targetSegmentId: null });
            }
        };

        // Assemble and show
        footer.appendChild(cancelBtn);
        footer.appendChild(confirmBtn);
        dialog.appendChild(header);
        dialog.appendChild(body);
        dialog.appendChild(footer);
        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        // Focus confirm button
        confirmBtn.focus();
    });
}

// Add this new function to show custom confirmation dialogs
function showConfirmDialog({ title, message, details, confirmText, cancelText, type = 'warning' }) {
    return new Promise((resolve) => {
        // Create modal overlay
        const overlay = document.createElement('div');
        overlay.className = 'modal-overlay';
        overlay.style.cssText = `
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
            animation: fadeIn 0.2s ease-out;
        `;

        // Create modal dialog
        const dialog = document.createElement('div');
        dialog.style.cssText = `
            background: white;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
            max-width: 500px;
            width: 90%;
            animation: slideIn 0.3s ease-out;
        `;

        // Determine color scheme based on type
        const colors = {
            danger: { bg: '#dc3545', hover: '#c82333' },
            warning: { bg: '#ffc107', hover: '#e0a800' },
            info: { bg: '#17a2b8', hover: '#138496' }
        };
        const color = colors[type] || colors.warning;

        // Modal header
        const header = document.createElement('div');
        header.style.cssText = `
            padding: 20px 24px;
            border-bottom: 1px solid #e0e0e0;
        `;
        header.innerHTML = `
            <h3 style="margin: 0; font-size: 18px; font-weight: 600; color: #333; font-family: 'Inter', sans-serif;">
                <i class="fas fa-exclamation-triangle" style="color: ${color.bg}; margin-right: 8px;"></i>
                ${title}
            </h3>
        `;

        // Modal body
        const body = document.createElement('div');
        body.style.cssText = `
            padding: 24px;
            font-family: 'Inter', sans-serif;
        `;
        body.innerHTML = `
            <p style="margin: 0 0 12px 0; font-size: 14px; color: #555; line-height: 1.6;">
                ${message}
            </p>
            ${details ? `<p style="margin: 0; font-size: 13px; color: #777; line-height: 1.5;">${details}</p>` : ''}
        `;

        // Modal footer
        const footer = document.createElement('div');
        footer.style.cssText = `
            padding: 16px 24px;
            border-top: 1px solid #e0e0e0;
            display: flex;
            justify-content: flex-end;
            gap: 12px;
        `;

        const cancelBtn = document.createElement('button');
        cancelBtn.textContent = cancelText;
        cancelBtn.style.cssText = `
            padding: 8px 20px;
            border: 1px solid #ddd;
            background: white;
            color: #555;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            font-family: 'Inter', sans-serif;
            transition: all 0.2s;
        `;
        cancelBtn.onmouseover = () => {
            cancelBtn.style.background = '#f5f5f5';
            cancelBtn.style.borderColor = '#ccc';
        };
        cancelBtn.onmouseout = () => {
            cancelBtn.style.background = 'white';
            cancelBtn.style.borderColor = '#ddd';
        };

        const confirmBtn = document.createElement('button');
        confirmBtn.textContent = confirmText;
        confirmBtn.style.cssText = `
            padding: 8px 20px;
            border: none;
            background: ${color.bg};
            color: white;
            border-radius: 4px;
            cursor: pointer;
            font-size: 14px;
            font-weight: 500;
            font-family: 'Inter', sans-serif;
            transition: all 0.2s;
        `;
        confirmBtn.onmouseover = () => {
            confirmBtn.style.background = color.hover;
        };
        confirmBtn.onmouseout = () => {
            confirmBtn.style.background = color.bg;
        };

        // Add animations
        const style = document.createElement('style');
        style.textContent = `
            @keyframes fadeIn {
                from { opacity: 0; }
                to { opacity: 1; }
            }
            @keyframes slideIn {
                from {
                    opacity: 0;
                    transform: translateY(-20px);
                }
                to {
                    opacity: 1;
                    transform: translateY(0);
                }
            }
        `;
        document.head.appendChild(style);

        // Event handlers
        const cleanup = () => {
            overlay.style.opacity = '0';
            dialog.style.transform = 'translateY(-20px)';
            dialog.style.opacity = '0';
            setTimeout(() => {
                overlay.remove();
                style.remove();
            }, 200);
        };

        cancelBtn.onclick = () => {
            cleanup();
            resolve(false);
        };

        confirmBtn.onclick = () => {
            cleanup();
            resolve(true);
        };

        overlay.onclick = (e) => {
            if (e.target === overlay) {
                cleanup();
                resolve(false);
            }
        };

        // Assemble and show
        footer.appendChild(cancelBtn);
        footer.appendChild(confirmBtn);
        dialog.appendChild(header);
        dialog.appendChild(body);
        dialog.appendChild(footer);
        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        // Focus confirm button
        confirmBtn.focus();
    });
}

// Delete admin users from the UI state only.
// The change is persisted only when the user clicks Save.
async function deleteSelectedAdminUsers() {
    const tableBody = document.getElementById('segmentAdminUsersTableBody');
    if (!tableBody) return;

    const selectedRows = tableBody.querySelectorAll('tr.selected');
    if (selectedRows.length === 0) {
        notify('Please select at least one admin user to delete', 'warning');
        return;
    }

    // Prevent deleting the last admin user
    const totalAdmins = segmentFormState.segmentAdminUsers.length;
    const deletingCount = selectedRows.length;

    if (totalAdmins - deletingCount < 1) {
        notify('Cannot delete all admin users. You must have at least one Segment Admin assigned.', 'error');
        return;
    }

    const confirmed = await showConfirmDialog({
        title: 'Delete Admin Users',
        message: `Are you sure you want to delete <strong>${selectedRows.length}</strong> admin user(s)?`,
        details: 'This action cannot be undone.',
        confirmText: 'Delete',
        cancelText: 'Cancel',
        type: 'danger'
    });

    if (!confirmed) return;

    const currentUser = await getCurrentUser();
    const currentUserId = currentUser?.id || currentUser?.ID || currentUser?.user_id;
    let isDeletingSelf = false;

    if (!segmentFormState.selectedSegmentId) {
        // New segment: just remove from state
        selectedRows.forEach(row => {
            const userId = parseInt(row.dataset.userId);
            segmentFormState.segmentAdminUsers = segmentFormState.segmentAdminUsers.filter(
                u => (u.user_id || u.id) != userId
            );
        });
        markSegmentDirty();
        renderSegmentAdminUsers();
        return;
    }

    // Existing segment: remove from local state only and require Save
    selectedRows.forEach(row => {
        const userId = parseInt(row.dataset.userId);
        if (currentUserId && userId == currentUserId) {
            isDeletingSelf = true;
        }
        segmentFormState.segmentAdminUsers = segmentFormState.segmentAdminUsers.filter(
            u => (u.user_id || u.id) != userId
        );
    });

    markSegmentDirty();
    renderSegmentAdminUsers();

    if (isDeletingSelf) {
        notify('You removed yourself as an administrator. Click Save to apply changes.', 'warning');
    } else {
        notify('Admin user(s) removed. Click Save to apply changes.', 'info');
    }
}

// Delete assigned users/org-units from UI state only.
// The change is persisted only when the user clicks Save.
async function deleteSelectedAssignedEntities() {
    const tableBody = document.getElementById('assignedEntitiesTableBody');
    if (!tableBody) return;

    const selectedRows = tableBody.querySelectorAll('tr.selected');
    if (selectedRows.length === 0) {
        notify('Please select at least one entity to delete', 'warning');
        return;
    }

    const confirmed = await showConfirmDialog({
        title: 'Delete Assigned Entities',
        message: `Are you sure you want to delete <strong>${selectedRows.length}</strong> entity/entities?`,
        details: 'This action cannot be undone.',
        confirmText: 'Delete',
        cancelText: 'Cancel',
        type: 'danger'
    });

    if (!confirmed) return;

    if (!segmentFormState.selectedSegmentId) {
        // If creating new segment, just remove from state
        selectedRows.forEach(row => {
            const entityId = parseInt(row.dataset.entityId);
            const entityType = row.dataset.entityType;

            if (entityType === 'org-unit') {
                segmentFormState.assignedOrgUnits = segmentFormState.assignedOrgUnits.filter(
                    o => (o.id || o.ID) != entityId
                );
            } else if (entityType === 'user') {
                segmentFormState.assignedUsers = segmentFormState.assignedUsers.filter(
                    u => (u.id || u.ID) != entityId
                );
            }
        });
        markSegmentDirty();
        renderAssignedEntities();
        return;
    }

    // Existing segment: remove from local state only and require Save
    selectedRows.forEach(row => {
        const entityId = parseInt(row.dataset.entityId);
        const entityType = row.dataset.entityType;

        if (entityType === 'org-unit') {
            segmentFormState.assignedOrgUnits = segmentFormState.assignedOrgUnits.filter(
                o => (o.id || o.ID) != entityId
            );
        } else if (entityType === 'user') {
            segmentFormState.assignedUsers = segmentFormState.assignedUsers.filter(
                u => (u.id || u.ID) != entityId
            );
        }
    });

    markSegmentDirty();
    renderAssignedEntities();
    notify('Entity/entities removed. Click Save to apply changes.', 'info');
}

// Make showConfirmDialog globally accessible
window.showConfirmDialog = showConfirmDialog;

// Table row selection functionality
function setupTableRowSelection() {
    // This is handled by onclick handlers in the rendered rows
}

function toggleRowSelection(event, row) {
    if (event) {
        event.preventDefault();
        event.stopPropagation();
    }
    const tableBody = row.closest('tbody');
    const isMulti = !!(event && (event.ctrlKey || event.metaKey));
    if (!isMulti && tableBody) {
        tableBody.querySelectorAll('tr.selected').forEach(r => {
            if (r !== row) r.classList.remove('selected');
        });
        row.classList.add('selected');
    } else {
        row.classList.toggle('selected');
    }
    if (tableBody) {
        if (tableBody.id === 'segmentAdminUsersTableBody') {
            updateDeleteButtonState('deleteSegmentAdminUserBtn', 'segmentAdminUsersTableBody');
        } else if (tableBody.id === 'assignedEntitiesTableBody') {
            updateDeleteButtonState('deleteAssignedEntityBtn', 'assignedEntitiesTableBody');
        }
    }
}

function updateDeleteButtonState(buttonId, tableBodyId) {
    const button = document.getElementById(buttonId);
    const tableBody = document.getElementById(tableBodyId);
    if (button && tableBody) {
        const selectedRows = tableBody.querySelectorAll('tr.selected');
        button.disabled = selectedRows.length === 0;
    }
}

// NOTE: There used to be duplicate delete functions below; they were removed to avoid overriding behavior.

/**
 * Get current user information
 */
async function getCurrentUser() {
    try {
        const response = await fetch('/api/me', {
            method: 'GET',
            credentials: 'include'
        });
        if (response.ok) {
            const userData = await response.json();
            console.log('👤 Current user:', userData);
            return userData;
        }
    } catch (error) {
        console.error('Error getting current user:', error);
    }
    return null;
}

/**
 * Check if user is Super Admin
 */
function checkIsSuperAdmin(role) {
    if (typeof RoleUtils !== 'undefined' && RoleUtils.isSuperAdminRole) {
        return RoleUtils.isSuperAdminRole(role);
    }
    if (!role) return false;
    const compact = String(role).toLowerCase().trim().replace(/[\s_-]/g, '');
    return compact === 'superadmin' || compact === 'suberadmin';
}

/**
 * Check if user is Segment Admin for the current segment
 */
function checkIsSegmentAdmin(currentUser, adminUsers) {
    if (!currentUser || !adminUsers) return false;
    const currentUserId = currentUser.id || currentUser.ID || currentUser.user_id;
    return adminUsers.some(u => (u.user_id || u.id) == currentUserId);
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function notify(message, type = 'info') {
    // Check for showNotification function (used in admin panel)
    if (typeof showNotification === 'function') {
        showNotification(message, type);
        return;
    }
    
    // Check for window.showNotification
    if (typeof window.showNotification === 'function') {
        window.showNotification(message, type);
        return;
    }
    
    // Fallback: create a simple notification
    const notification = document.createElement('div');
    notification.style.cssText = `
        position: fixed;
        top: 20px;
        right: 20px;
        padding: 15px 20px;
        background: ${type === 'error' ? '#dc3545' : type === 'success' ? '#28a745' : '#17a2b8'};
        color: white;
        border-radius: 4px;
        box-shadow: 0 2px 10px rgba(0,0,0,0.2);
        z-index: 10000;
        max-width: 400px;
        font-family: 'Inter', sans-serif;
    `;
    notification.textContent = message;
    document.body.appendChild(notification);
    setTimeout(() => {
        notification.style.opacity = '0';
        notification.style.transition = 'opacity 0.3s';
        setTimeout(() => notification.remove(), 300);
    }, 3000);
}


// Make functions globally accessible
window.initializeSegmentForm = initializeSegmentForm;
window.toggleUserSelection = toggleUserSelection;
window.toggleOrgUnitSelection = toggleOrgUnitSelection;
window.toggleRowSelection = toggleRowSelection;

