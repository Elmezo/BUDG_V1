// Role Assignment functionality for Admin Panel - V1 Restoration

var RA_PREFIX = 'adminPanel.operatingModel.roleAssignment';
function raT(key, fallback) {
    if (typeof adminT === 'function') return adminT(RA_PREFIX + '.' + key, fallback);
    return fallback;
}
function raEscape(s) {
    if (s == null) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
}
function raTpl(str, vars) {
    if (!str || !vars) return str;
    var out = str;
    Object.keys(vars).forEach(function (k) {
        out = out.split('{{' + k + '}}').join(String(vars[k]));
    });
    return out;
}

// showNotification: unified implementation in admin-notifications.js (loaded first)

function showRoleAssignmentContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.roleAssignment');
    var h = raEscape;
    var t = raT;
    var budg = typeof adminT === 'function' ? adminT('adminPanel.header.budgManagement', 'BUDG Management') : 'BUDG Management';

    // Update the admin header bar
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (adminHeaderBar) {
        adminHeaderBar.innerHTML = `
            <div class="header-left">
                <button class="back-button" onclick="goBack()">
                    <i class="fas fa-arrow-left"></i>
                </button>
            </div>
            <div class="header-center">
                <h1 class="system-title">${h(t('pageTitle', 'Role Assignment'))}</h1>
                <div class="system-status">
                    <span class="status-dot"></span>
                    ${h(budg)}
                </div>
            </div>
        `;
    }

    contentArea.innerHTML = `
        <div class="role-assignment-content">
            <div class="role-assignment-section">
                <div class="section-header">
                    <div class="section-actions">
                        <button class="btn btn-add" onclick="addRoleAssignment()">
                            <i class="fas fa-plus"></i>
                            ${h(t('add', 'Add'))}
                        </button>
                        <button class="btn btn-delete" onclick="deleteRoleAssignment()">
                            <i class="fas fa-trash"></i>
                            ${h(t('delete', 'Delete'))}
                        </button>
                    </div>
                </div>

                <div class="section-content">
                    <div class="empty-state">
                        <i class="fas fa-info-circle"></i>
                        <p>${h(t('emptyState', 'No roles are assigned. To define a role assignment, click Add.'))}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function addRoleAssignment() {
    var h = raEscape;
    var t = raT;
    var budg = typeof adminT === 'function' ? adminT('adminPanel.header.budgManagement', 'BUDG Management') : 'BUDG Management';
    // Update the admin header bar for configure mode
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (adminHeaderBar) {
        adminHeaderBar.innerHTML = `
            <div class="header-left">
                <button class="back-button" onclick="goBackToRoleAssignment()">
                    <i class="fas fa-arrow-left"></i>
                </button>
            </div>
            <div class="header-center">
                <h1 class="system-title">${h(t('configureTitle', 'Configure Role Assignment'))}</h1>
                <div class="system-status">
                    <span class="status-dot"></span>
                    ${h(budg)}
                </div>
            </div>
            <div class="header-right">
                <button class="btn btn-save-close" onclick="saveAndCloseRoleAssignment()">
                    ${h(t('saveAndClose', 'Save & Close'))}
                </button>
                <button class="btn btn-save" onclick="saveRoleAssignment()">
                    ${h(t('save', 'Save'))}
                </button>
                <button class="btn btn-close" onclick="closeRoleAssignment()">
                    ${h(t('close', 'Close'))}
                </button>
            </div>
        `;
    }
    // Initialize in-memory selection set for user IDs to preserve across edits
    window._ra_current_user_ids = new Set();

    // Update the content area with the configure form
    const contentArea = document.querySelector('.content-area');
    var h = raEscape;
    var t = raT;
    contentArea.innerHTML = `
        <div class="configure-role-assignment-content">
            <!-- FACET ROLE SELECTION Section -->
            <div class="facet-role-section">
                <div class="section-header">
                    <h2>${h(t('facetRoleSelection', 'FACET ROLE SELECTION'))}</h2>
                </div>

                <div class="form-section">
                    <div class="form-group">
                        <label for="facet-select" class="form-label">${h(t('facetLabel', 'Facet'))} <span class="required">*</span></label>
                        <div class="custom-select-container">
                            <div class="custom-select" id="facet-select">
                                <div class="select-trigger">
                                    <span class="select-placeholder">${h(t('selectFacet', 'Select a facet'))}</span>
                                    <i class="fas fa-chevron-up select-arrow"></i>
                                </div>
                                <div class="select-dropdown">
                                    <div class="search-container">
                                        <i class="fas fa-search search-icon"></i>
                                        <input type="text" class="search-input" placeholder="${h(t('searchFacetsPlaceholder', 'Search facets...'))}" id="facet-search">
                                    </div>
                                    <div class="select-options" id="facet-options">
                                        <div class="loading-options">${h(t('loadingFacets', 'Loading facets...'))}</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="form-group">
                        <label for="role-select" class="form-label">${h(t('roleLabel', 'Role'))} <span class="required">*</span></label>
                        <div class="custom-select-container">
                            <div class="custom-select disabled" id="role-select">
                                <div class="select-trigger">
                                    <span class="select-placeholder">${h(t('selectFacetFirst', 'Please select a facet first'))}</span>
                                    <i class="fas fa-chevron-down select-arrow"></i>
                                </div>
                                <div class="select-dropdown">
                                    <div class="select-options" id="role-options">
                                        <div class="disabled-message">${h(t('selectFacetFirst', 'Please select a facet first'))}</div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="validation-message" id="role-validation" style="display: none;">
                            <i class="fas fa-info-circle"></i>
                            <span>${h(t('selectFacetBeforeRole', 'Please select a facet before choosing a role'))}</span>
                        </div>
                    </div>
                </div>
            </div>

            <!-- MEMBERS Section -->
            <div class="members-section">
                <div class="section-header">
                    <h2>${h(t('members', 'MEMBERS'))}</h2>
                    <div class="section-actions">
                        <button class="settings-btn" onclick="openMembersSettings()">
                            <i class="fas fa-cog"></i>
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                </div>

                <div class="members-table-container">
                    <div class="members-validation-message" id="members-validation" style="display: none;">
                        <i class="fas fa-info-circle"></i>
                        <span>select another role</span>
                </div>

                    <table class="members-table">
                        <thead>
                            <tr>
                                <th>User</th>
                                <th>Email</th>
                                <th>Add as Stakeholder</th>
                                <th>Action</th>
                            </tr>
                        </thead>
                        <tbody id="members-table-body">
                            <tr class="member-row disabled-row">
                                <td>
                                    <div class="user-input-container autocomplete-container">
                                        <input type="text" class="user-input disabled" placeholder="Search user..." disabled autocomplete="off">
                                        <button class="clear-input-btn" onclick="clearUserInput(this)" disabled>
                                            <i class="fas fa-times"></i>
                                        </button>
                                        <div class="autocomplete-suggestions" style="display: none;"></div>
                                    </div>
                                </td>
                                <td>
                                    <span class="email-display">-</span>
                                </td>
                                <td>
                                    <label class="checkbox-container">
                                        <input type="checkbox" class="stakeholder-checkbox" disabled>
                                        <span class="checkmark"></span>
                                    </label>
                                </td>
                                <td>
                                    <div class="action-buttons">
                                        <button class="action-btn add-btn disabled" onclick="addMember()" title="Add New Row" disabled>
                                            <i class="fas fa-plus"></i>
                                        </button>
                                        <button class="action-btn remove-btn disabled" onclick="removeMemberRow(this)" title="Remove Row" disabled>
                                            <i class="fas fa-minus"></i>
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    `;

    // Load modules from database, then initialize dropdowns
    // Use async/await for cleaner code
    (async () => {
        try {
            console.log('🔄 [addRoleAssignment] Starting module load process...');
            
            // Wait for DOM to be ready - ensure elements exist
            let retries = 0;
            let optionsContainer = document.getElementById('facet-options');
            while (!optionsContainer && retries < 10) {
                await new Promise(resolve => setTimeout(resolve, 50));
                optionsContainer = document.getElementById('facet-options');
                retries++;
            }
            
            if (!optionsContainer) {
                console.error('❌ [addRoleAssignment] Options container not found after waiting!');
                // Still try to initialize
                initializeCustomSelect();
                initializeRoleSelect();
                initializeMembersTable();
                return;
            }
            
            console.log('✅ [addRoleAssignment] DOM elements found, calling loadFacetModules()...');
            console.log('✅ [addRoleAssignment] loadFacetModules function exists:', typeof loadFacetModules);
            console.log('✅ [addRoleAssignment] loadFacetModules is function:', typeof loadFacetModules === 'function');
            
            let result;
            try {
                console.log('✅ [addRoleAssignment] About to await loadFacetModules()...');
                result = await loadFacetModules();
                console.log('✅ [addRoleAssignment] loadFacetModules() returned:', result);
            } catch (loadError) {
                console.error('❌ [addRoleAssignment] Error calling loadFacetModules():', loadError);
                console.error('❌ [addRoleAssignment] Error stack:', loadError.stack);
                result = undefined;
            }
            console.log('✅ [addRoleAssignment] Final result:', result);
            console.log('✅ [addRoleAssignment] Modules loaded, initializing dropdowns...');
            
            // Wait a moment for DOM to update after loadModules completes
            await new Promise(resolve => setTimeout(resolve, 200));
            
            // Verify options are in DOM
            const updatedOptionsContainer = document.getElementById('facet-options');
            if (updatedOptionsContainer) {
                const options = updatedOptionsContainer.querySelectorAll('.select-option');
                console.log(`✅ [addRoleAssignment] Found ${options.length} options in DOM`);
                console.log(`✅ [addRoleAssignment] Container innerHTML length: ${updatedOptionsContainer.innerHTML.length}`);
                if (options.length === 0) {
                    console.error('❌ [addRoleAssignment] No options found after load!');
                    console.error('❌ [addRoleAssignment] Container HTML:', updatedOptionsContainer.innerHTML.substring(0, 300));
                    console.error('❌ [addRoleAssignment] Container classes:', updatedOptionsContainer.className);
                    console.error('❌ [addRoleAssignment] Parent element:', updatedOptionsContainer.parentElement?.tagName);
                    
                    // Try to reload if no options found
                    console.log('🔄 [addRoleAssignment] Retrying loadFacetModules()...');
                    try {
                        await loadFacetModules();
                        await new Promise(resolve => setTimeout(resolve, 200));
                        const retryOptions = updatedOptionsContainer.querySelectorAll('.select-option');
                        console.log(`🔄 [addRoleAssignment] After retry, found ${retryOptions.length} options`);
                    } catch (retryError) {
                        console.error('❌ [addRoleAssignment] Retry failed:', retryError);
                    }
                }
            } else {
                console.error('❌ [addRoleAssignment] Options container not found after load!');
            }
            
            // Initialize dropdowns
            initializeCustomSelect();
            initializeRoleSelect();
            initializeMembersTable();
        } catch (error) {
            console.error('❌ [addRoleAssignment] Failed to load modules:', error);
            console.error('❌ [addRoleAssignment] Error stack:', error.stack);
            // Still initialize dropdowns even if loading fails
            initializeCustomSelect();
            initializeRoleSelect();
            initializeMembersTable();
        }
    })();
}

function deleteRoleAssignment() {
    const idx = window._ra_selected_index;
    if (idx == null) {
        showNotification(raT('notifySelectToDelete', 'Please select a role assignment to delete'), 'error');
        return;
    }
    const tbody = document.getElementById('roleAssignmentTableBody');
    if (!tbody) return;
    const row = tbody.querySelector(`tr[data-index="${idx}"]`);
    if (!row) return;
    const id = row.getAttribute('data-id');
    if (!id) return;
    
    // Delete without confirmation
    
    fetch(`/api/role-assignments?id=${id}`, { method: 'DELETE' })
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                showNotification(raT('notifyDeletedSuccess', 'Role assignment deleted successfully'), 'success');
                renderRoleAssignmentTable();
            } else {
                const errorMsg = data.error || data.message || 'Failed to delete role assignment';
                showNotification(errorMsg, 'error');
            }
        })
        .catch((e) => {
            showNotification(raTpl(raT('notifyDeleteFailed', 'Failed to delete: {{message}}'), { message: e.message || raT('unknown', 'Unknown error') }), 'error');
        });
}

// Configure Role Assignment Functions
function goBackToRoleAssignment() {
    // Show the role assignment content (this will update the header)
    const contentArea = document.querySelector('.content-area');
    showRoleAssignmentContent(contentArea);
}

// Role Assignment list page (table like: Facet | Role | No Of Users | Last Updated)
function showRoleAssignmentContent(container) {
    var h = raEscape;
    var t = raT;
    var budg = typeof adminT === 'function' ? adminT('adminPanel.header.budgManagement', 'BUDG Management') : 'BUDG Management';
    // Update header to Role Assignment
    const adminHeaderBar = document.querySelector('.admin-header-bar');
    if (adminHeaderBar) {
        adminHeaderBar.innerHTML = `
            <div class="header-left"></div>
            <div class="header-center">
                <h1 class="system-title">${h(t('pageTitle', 'Role Assignment'))}</h1>
                <div class="system-status">
                    <span class="status-dot"></span>
                    ${h(budg)}
                </div>
            </div>
            <div class="header-right">
                <button class="btn btn-save" onclick="addRoleAssignment()">${h(t('add', 'Add'))}</button>
                <button class="btn btn-close" onclick="deleteRoleAssignment()">${h(t('delete', 'Delete'))}</button>
            </div>
        `;
    }

    if (!container) container = document.querySelector('.content-area');
    if (!container) return;

    container.innerHTML = `
        <div class="role-assignment-content">
            <div class="content-body">
                <div class="data-table-container">
                    <div class="table-responsive">
                        <table class="data-table" id="roleAssignmentTable">
                            <thead>
                                <tr class="header-labels">
                                    <th>${h(t('tableFacet', 'Facet'))}</th>
                                    <th>${h(t('tableRole', 'Role'))}</th>
                                    <th>${h(t('tableNoOfUsers', 'No Of Users'))}</th>
                                    <th>${h(t('tableLastUpdated', 'Last Updated'))}</th>
                                </tr>
                            </thead>
                            <tbody id="roleAssignmentTableBody"></tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    `;

    renderRoleAssignmentTable();
}

async function fetchRoleAssignments() {
    try {
        const res = await fetch('/api/role-assignments');
        if (!res.ok) return [];
        return await res.json();
    } catch (_) { return []; }
}

async function renderRoleAssignmentTable() {
    const tbody = document.getElementById('roleAssignmentTableBody');
    if (!tbody) return;
    const items = await fetchRoleAssignments();
    if (!items.length) {
        tbody.innerHTML = '<tr><td colspan="4" style="text-align:center;color:var(--text-secondary)">' + raEscape(raT('noRecords', 'No records')) + '</td></tr>';
        return;
    }
    tbody.innerHTML = items.map((it, idx) => {
        let users = [];
        try { users = it.usersJson ? JSON.parse(it.usersJson) : []; } catch (_) { users = []; }
        const count = users.length;
        const updated = it.lastUpdatedDateString || it.createDateString || '';
        return `
            <tr data-index="${idx}" data-id="${it.id ?? ''}" data-objectroleid="${it.objectRoleId ?? ''}">
                <td>${raEscape(it.facetName ?? '')}</td>
                <td>${raEscape(it.roleName ?? '')}</td>
                <td><a href="javascript:void(0)" onclick="openRoleAssignment(${idx})">${count}</a></td>
                <td>${raEscape(updated)}</td>
            </tr>
        `;
    }).join('');
    // Row selection handling
    Array.from(tbody.querySelectorAll('tr')).forEach(tr => {
        tr.addEventListener('click', function() {
            const idx = Number(this.getAttribute('data-index'));
            setSelectedRoleAssignmentIndex(idx);
        });
    });
}

function formatLocalDateTime(ms) {
    try {
        const d = new Date(Number(ms));
        const pad = n => String(n).padStart(2, '0');
        const yyyy = d.getFullYear();
        const mm = pad(d.getMonth() + 1);
        const dd = pad(d.getDate());
        const hh = pad(d.getHours());
        const mi = pad(d.getMinutes());
        const ss = pad(d.getSeconds());
        return `${yyyy}-${mm}-${dd} ${hh}:${mi}:${ss}`;
    } catch (_) {
        return '';
    }
}

function setSelectedRoleAssignmentIndex(idx) {
    window._ra_selected_index = idx;
    const tbody = document.getElementById('roleAssignmentTableBody');
    if (!tbody) return;
    Array.from(tbody.querySelectorAll('tr')).forEach((tr, i) => {
        if (i === idx) {
            tr.classList.add('selected-row');
            tr.style.backgroundColor = 'rgba(16, 185, 129, 0.12)';
        } else {
            tr.classList.remove('selected-row');
            tr.style.backgroundColor = '';
        }
    });
}

async function openRoleAssignment(index) {
    const items = await fetchRoleAssignments();
    const it = items[index];
    if (!it) return;
    
    console.log('Opening role assignment:', it);
    console.log('Users JSON:', it.usersJson);
    // Open configure page then set selections and members
    addRoleAssignment();
    // Wait for DOM then set selections
    setTimeout(async () => {
        // Load facets first
        await loadFacetModules();
        
        // Set facet selection
        const facetSelect = document.getElementById('facet-select');
        if (facetSelect && it.facetName) {
            facetSelect.dataset.selectedValue = it.moduleId || '';
            facetSelect.dataset.selectedText = it.facetName;
            const trigger = facetSelect.querySelector('.select-trigger .select-placeholder');
            if (trigger) {
                trigger.textContent = it.facetName;
            }
        }
        
        // Load roles for the selected facet
        if (it.moduleId) {
            await loadRolesForFacet(it.moduleId);
            
            // Set role selection after roles are loaded
            setTimeout(() => {
                const roleSelect = document.getElementById('role-select');
                if (roleSelect && it.roleName) {
                    roleSelect.dataset.selectedValue = it.objectRoleId || '';
                    roleSelect.dataset.selectedText = it.roleName;
                    const trigger = roleSelect.querySelector('.select-trigger .select-placeholder');
                    if (trigger) {
                        trigger.textContent = it.roleName;
                    }
                    // Enable role select
                    roleSelect.classList.remove('disabled');
                }
            }, 200);
        } else {
            // Clear roles if no facet selected
            const roleOptions = document.getElementById('role-options');
            if (roleOptions) {
                roleOptions.innerHTML = '<div class="no-results">' + raEscape(raT('selectFacetFirst', 'Please select a facet first')) + '</div>';
            }
        }
        // Populate saved users after a short delay to ensure DOM is ready
        setTimeout(async () => {
            const tbody = document.getElementById('members-table-body');
            if (tbody) {
                tbody.innerHTML = '';
                let users = [];
                try { 
                    users = it.usersJson ? JSON.parse(it.usersJson) : []; 
                    console.log('Loaded users from JSON:', users);
        } catch (e) {
            console.error('Error parsing users JSON:', e);
                    users = []; 
                }
                // Seed in-memory set with existing IDs
                window._ra_current_user_ids = new Set(Array.isArray(users) ? users.map(Number) : []);
                
                console.log('Users array:', users);
                console.log('Users length:', users.length);
                
                if (users && users.length > 0) {
                    // Load existing users with their details
                    console.log('Starting to load users...');
                    for (const userId of users) {
                        console.log('Loading user ID:', userId);
                        try {
                            // Fetch user details from API
                            const userResponse = await fetch(`/api/people/${userId}`);
                            console.log('User response status:', userResponse.status);
                            let userData = null;
                            if (userResponse.ok) {
                                const response = await userResponse.json();
                                console.log('User data loaded:', response);
                                // Extract data from response.data if it exists
                                userData = response.data || response;
                            } else {
                                console.error('Failed to load user data for ID:', userId);
                            }
                            
                            const row = document.createElement('tr');
                            row.className = 'member-row saved-member';
                            const userName = userData ? `${userData.first_name || ''} ${userData.last_name || ''}`.trim() : `User ID: ${userId}`;
                            const userEmail = userData ? (userData.email || '-') : '-';
                            
                            console.log('Creating row for user:', userName, 'Email:', userEmail);
                            
                            row.innerHTML = `
                                <td>
                                    <div class="user-input-container autocomplete-container">
                                        <input type="text" class="user-input" data-person-id="${userId}" value="${userName}" placeholder="Search user..." autocomplete="off">
                                        <button class="clear-input-btn" onclick="clearUserInput(this)" style="display:block"><i class="fas fa-times"></i></button>
                                        <div class="autocomplete-suggestions" style="display: none;"></div>
                                    </div>
                                </td>
                                <td><span class="email-display">${userEmail}</span></td>
                                <td>
                                    <label class="checkbox-container">
                                        <input type="checkbox" class="stakeholder-checkbox">
                                        <span class="checkmark"></span>
                                    </label>
                                </td>
                                <td>
                                    <div class="action-buttons">
                                        <button class="action-btn add-btn" onclick="addMember()" title="Add New Row"><i class="fas fa-plus"></i></button>
                                        <button class="action-btn remove-btn" onclick="removeMemberRow(this)" title="Remove Row"><i class="fas fa-minus"></i></button>
                                    </div>
                                </td>`;
                            tbody.appendChild(row);
                            console.log('Row added to table, total rows:', tbody.children.length);
                            
                            const input = row.querySelector('.user-input');
                            if (input) {
                                initializeAutocomplete(input);
                                console.log('Autocomplete initialized for user:', userName);
                            }
                            
                            // Initialize member row events
                            initializeMemberRowEvents(row);
                        } catch (error) {
                            console.error('Error loading user details:', error);
                            // Create row with just user ID if API fails
                            const row = document.createElement('tr');
                            row.className = 'member-row saved-member';
                            row.innerHTML = `
                                <td>
                                    <div class="user-input-container autocomplete-container">
                                        <input type="text" class="user-input" data-person-id="${userId}" value="User ID: ${userId}" placeholder="Search user..." autocomplete="off">
                                        <button class="clear-input-btn" onclick="clearUserInput(this)" style="display:block"><i class="fas fa-times"></i></button>
                                        <div class="autocomplete-suggestions" style="display: none;"></div>
                                    </div>
                                </td>
                                <td><span class="email-display">-</span></td>
                                <td>
                                    <label class="checkbox-container">
                                        <input type="checkbox" class="stakeholder-checkbox">
                                        <span class="checkmark"></span>
                                    </label>
                                </td>
                                <td>
                                    <div class="action-buttons">
                                        <button class="action-btn add-btn" onclick="addMember()" title="Add New Row"><i class="fas fa-plus"></i></button>
                                        <button class="action-btn remove-btn" onclick="removeMemberRow(this)" title="Remove Row"><i class="fas fa-minus"></i></button>
                                    </div>
                                </td>`;
                            tbody.appendChild(row);
                            const input = row.querySelector('.user-input');
                            if (input) initializeAutocomplete(input);
                            
                            // Initialize member row events
                            initializeMemberRowEvents(row);
                        }
                    }
                } else {
                    console.log('No users found, enabling input for new ones');
                    // No existing users, enable input for new ones
                    enableMembersInput();
                }
                
                // Always add an empty row for adding new users
                console.log('Adding empty row for new users');
                addMember();
            }
        }, 300);
    }, 100);
}

function saveRoleAssignment() {
    const customSelect = document.getElementById('facet-select');
    
    const selectedFacetId = customSelect.dataset.selectedValue;
    const selectedFacetName = customSelect.dataset.selectedText;
    const roleSelect = document.getElementById('role-select');
    const selectedRoleId = roleSelect?.dataset.selectedValue;
    const selectedRoleName = roleSelect?.dataset.selectedText;
    
    if (!selectedFacetId) {
        showNotification(raT('notifySelectFacetBeforeSave', 'Please select a facet before saving.'), 'error');
        return;
    }
    if (!selectedRoleId) {
        showNotification(raT('notifySelectRoleBeforeSave', 'Please select a role before saving.'), 'error');
        return;
    }
    // Collect members from table as People IDs (only from visible, non-empty rows)
    const typedFromInputs = Array.from(document.querySelectorAll('#members-table-body .user-input'))
        .map(function(input){ 
            // Only include inputs that have a person ID and are not empty
            return input && input.dataset && input.dataset.personId && input.value.trim() !== '' ? input.dataset.personId : null; 
        })
        .filter(function(pid){ return pid != null; })
        .map(function(pid){ return Number(pid); });
    
    console.log('Current users from table:', typedFromInputs);
    console.log('Previous users from memory:', window._ra_current_user_ids);
    
    // Use only the current users from the table (this handles deletions automatically)
    const users = typedFromInputs;
    
    // Check if there are any users to save
    if (users.length === 0) {
        showNotification(raT('notifyAddMemberBeforeSave', 'Please add at least one member before saving.'), 'error');
        return;
    }
    
    // Update the in-memory set to match current table state
    window._ra_current_user_ids = new Set(users);
    const apiPayload = {
        objectRoleId: selectedRoleId ? Number(selectedRoleId) : null,
        usersJson: JSON.stringify(users)
    };
    return fetch('/api/role-assignments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(apiPayload)
    }).then(async (r) => {
        if (!r.ok) {
            const text = await r.text().catch(() => '');
            let msg = 'Server error';
            try { 
                const j = JSON.parse(text); 
                if (j && j.error) msg = j.error;
                else if (j && j.message) msg = j.message;
            } catch(_) { 
                if (text) msg = text; 
            }
            showNotification(raTpl(raT('notifySaveFailed', 'Save failed: {{message}}'), { message: msg }), 'error');
            throw new Error(msg);
        }
        const result = await r.json();
        console.log('Save successful:', result);
        
        // Show success message only on successful save
        showNotification(raT('notifySavedSuccess', 'Role assignment saved successfully!'), 'success');
        return result;
    }).catch((e) => {
        showNotification(raTpl(raT('notifySaveServerFailed', 'Failed to save to server: {{message}}'), { message: e && e.message ? e.message : 'unknown error' }), 'error');
        return null;
    });
}

function saveAndCloseRoleAssignment() {
    saveRoleAssignment().then(() => {
        // Go back to role assignment page
    goBackToRoleAssignment();
        // Refresh the table
        setTimeout(renderRoleAssignmentTable, 150);
    });
}

function closeRoleAssignment() {
    // Go back to role assignment page
    goBackToRoleAssignment();
}

// Initialize the role assignment form
function initializeRoleAssignmentForm() {
    // Load facets
    loadFacets();
    
    // Initialize custom select functionality
    initializeCustomSelects();
    
    // Initialize autocomplete for user inputs
    initializeUserInputs();
}

// Prevent multiple simultaneous loads
let isLoadingModules = false;
let modulesLoadPromise = null;

async function loadFacetModules() {
    // CRITICAL: This log must appear if function is called
    console.log('🔵🔵🔵 [loadFacetModules] FUNCTION ENTRY POINT - EXECUTING NOW');
    console.log('🔵🔵🔵 [loadFacetModules] Stack trace:', new Error().stack);
    
    try {
        console.log('🔵 [loadFacetModules] Inside try block - function is executing');
        
        // Wait a bit for DOM to be ready if elements don't exist
        let customSelect = document.getElementById('facet-select');
        let optionsContainer = document.getElementById('facet-options');
        
        if (!customSelect || !optionsContainer) {
            console.warn('⚠️ [loadFacetModules] Elements not found immediately, waiting...');
            for (let i = 0; i < 5; i++) {
                await new Promise(resolve => setTimeout(resolve, 100));
                customSelect = document.getElementById('facet-select');
                optionsContainer = document.getElementById('facet-options');
                if (customSelect && optionsContainer) {
                    console.log('✅ [loadFacetModules] Elements found after waiting');
                    break;
                }
            }
        }
        
        console.log('🔵 [loadFacetModules] Elements found:', {
            customSelect: !!customSelect,
            optionsContainer: !!optionsContainer
        });
        
        if (!customSelect || !optionsContainer) {
            const error = new Error('Custom select elements not found');
            console.error('❌ [loadFacetModules] Custom select elements not found after waiting');
            console.error('❌ [loadFacetModules] Returning rejected promise');
            const rejectedPromise = Promise.reject(error);
            console.error('❌ [loadFacetModules] Rejected promise created:', rejectedPromise);
            return rejectedPromise;
        }
        
        // Check if already loaded and options exist
        const isLoaded = customSelect.dataset.modulesLoaded === 'true';
        console.log('🔵 [loadFacetModules] Already loaded check:', isLoaded);
        
        if (isLoaded) {
            const existingOptions = optionsContainer.querySelectorAll('.select-option');
                console.log('🔵 [loadFacetModules] Existing options count:', existingOptions.length);
                if (existingOptions.length > 0) {
                    console.log(`✅ [loadFacetModules] Modules already loaded with ${existingOptions.length} options`);
                    const modules = window.facetModules || [];
                    console.log('🔵 [loadFacetModules] Returning cached modules:', modules.length);
                return Promise.resolve(modules);
            } else {
                // Flag says loaded but no options - reset flag
                console.warn('⚠️ [loadFacetModules] Flag says loaded but no options found, resetting...');
                customSelect.dataset.modulesLoaded = 'false';
                window.facetModulesLoaded = false;
            }
        }
        
        // If already loading, return the existing promise
        console.log('🔵 [loadFacetModules] Loading check:', {
            isLoadingModules,
            hasPromise: !!modulesLoadPromise
        });
        
        if (isLoadingModules && modulesLoadPromise) {
            console.log('⏳ [loadFacetModules] Modules already loading, waiting for existing request...');
            console.log('⏳ [loadFacetModules] Returning existing promise:', modulesLoadPromise);
            return modulesLoadPromise;
        }

        // Start loading - create promise immediately
        console.log('🚀 [loadFacetModules] Starting new load, setting loading state...');
        isLoadingModules = true;
        customSelect.classList.add('loading');
        const loadingFacets = raT('loadingFacets', 'Loading facets...');
        optionsContainer.innerHTML = '<div class="loading-options">' + raEscape(loadingFacets) + '</div>';
        console.log('🚀 [loadFacetModules] Loading state set, creating promise...');

        // Create and execute promise immediately
        modulesLoadPromise = new Promise(async (resolve, reject) => {
        console.log('🚀 [loadFacetModules] Promise executor running...');
        try {
            console.log('🔄 [loadFacetModules] Starting fetch to /api/modules?action=list');
            console.log('🔄 [loadFacetModules] Options container exists:', !!optionsContainer);
            console.log('🔄 [loadFacetModules] Container current HTML:', optionsContainer.innerHTML.substring(0, 100));
            
            // Make API call to get modules from module table
            const response = await fetch('/api/modules?action=list');
            
            console.log('📡 [loadFacetModules] Response received. Status:', response.status, response.statusText);
            console.log('📡 [loadFacetModules] Response headers:', response.headers.get('content-type'));
            
            if (!response.ok) {
                const errorText = await response.text();
                console.error('❌ [loadFacetModules] HTTP error response body:', errorText);
                throw new Error(`HTTP error! status: ${response.status}, body: ${errorText.substring(0, 200)}`);
            }
            
            const data = await response.json();
            console.log('📦 [loadFacetModules] JSON parsed successfully');
            console.log('📦 [loadFacetModules] Response data:', data);
            console.log('📦 [loadFacetModules] Response keys:', Object.keys(data));
            console.log('📦 [loadFacetModules] Data type:', typeof data.data, 'Is array:', Array.isArray(data.data));
            if (data.data) {
                console.log('📦 [loadFacetModules] Data length:', data.data.length);
                if (data.data.length > 0) {
                    console.log('📦 [loadFacetModules] First module:', data.data[0]);
                }
            }
            
            // Validate response
            if (!data.success) {
                throw new Error(data.message || 'API returned success=false');
            }
            
            if (!data.data || !Array.isArray(data.data)) {
                throw new Error('Invalid response: data is not an array');
            }
            
            if (data.data.length === 0) {
                optionsContainer.innerHTML = '<div class="error-message">' + raEscape(raT('noModulesFound', 'No modules found in database')) + '</div>';
                customSelect.classList.remove('loading', 'success');
                customSelect.classList.add('error');
                customSelect.dataset.modulesLoaded = 'false';
                window.facetModulesLoaded = false;
                throw new Error('No modules found in database');
            }
            
            console.log(`📊 Processing ${data.data.length} modules from database...`);
            
            // Clear loading and add modules
            optionsContainer.innerHTML = '';
            
            // Store modules globally for search functionality
            window.facetModules = data.data;
            
            // Add modules to options - data comes from module table in database
            let addedCount = 0;
            data.data.forEach((module, index) => {
                // Handle both primaryName (camelCase from getter) and primaryname (from Gson field serialization)
                const moduleName = module.primaryName || module.primaryname || module.name || '';
                if (!moduleName) {
                    console.warn(`⚠️ Module ${index} missing name:`, module);
                    return; // Skip modules without names
                }
                
                const option = document.createElement('div');
                option.className = 'select-option';
                option.dataset.value = module.id;
                option.dataset.text = moduleName;
                option.textContent = moduleName;
                optionsContainer.appendChild(option);
                addedCount++;
            });
            
            // Verify options were added
            const verifyOptions = optionsContainer.querySelectorAll('.select-option');
            console.log(`✅ Added ${addedCount} options to DOM. Verified: ${verifyOptions.length} options in container`);
            
            if (addedCount === 0) {
                optionsContainer.innerHTML = '<div class="error-message">' + raEscape(raT('noValidModules', 'No valid modules found (all modules missing names)')) + '</div>';
                customSelect.classList.remove('loading', 'success');
                customSelect.classList.add('error');
                customSelect.dataset.modulesLoaded = 'false';
                window.facetModulesLoaded = false;
                throw new Error('No valid modules found');
            }
            
            // Add success state
            customSelect.classList.remove('loading', 'error');
            customSelect.classList.add('success');
            
            // Set flag to indicate modules are loaded
            customSelect.dataset.modulesLoaded = 'true';
            window.facetModulesLoaded = true;
            
            // Clear loading start time if it exists
            if (customSelect.dataset.loadingStartTime) {
                delete customSelect.dataset.loadingStartTime;
            }
            
            console.log(`✅ Successfully loaded ${addedCount} modules from module table`);
            console.log('Sample module:', data.data[0]);
            
            // Resolve promise with data
            resolve(data.data);
            
        } catch (error) {
            console.error('❌ [loadFacetModules] Error loading modules:', error);
            customSelect.classList.remove('loading', 'success');
            customSelect.classList.add('error');
            optionsContainer.innerHTML = `
                <div class="error-message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>${raEscape(raT('errorPrefix', 'Error:'))} ${raEscape(error.message)}</span>
                </div>
            `;
            customSelect.dataset.modulesLoaded = 'false';
            window.facetModulesLoaded = false;
            
            // Clear loading start time if it exists
            if (customSelect.dataset.loadingStartTime) {
                delete customSelect.dataset.loadingStartTime;
            }
            
            // Reject promise with error
            reject(error);
        } finally {
            console.log('🏁 [loadFacetModules] Finally block executing, cleaning up...');
            isLoadingModules = false;
            // Don't set modulesLoadPromise to null here - let it stay so subsequent calls can use it
        }
        });
        
        console.log('🚀 [loadFacetModules] Promise created:', typeof modulesLoadPromise);
        console.log('🚀 [loadFacetModules] Promise is Promise:', modulesLoadPromise instanceof Promise);
        console.log('🚀 [loadFacetModules] Returning promise...');
        
        if (!modulesLoadPromise) {
            console.error('❌ [loadFacetModules] CRITICAL: modulesLoadPromise is null/undefined!');
            throw new Error('Failed to create modules load promise');
        }
        
        return modulesLoadPromise;
    } catch (error) {
        // Catch any synchronous errors that occur before the promise is created
        console.error('❌ [loadFacetModules] Synchronous error before promise creation:', error);
        console.error('❌ [loadFacetModules] Error stack:', error.stack);
        
        // Try to update UI if elements exist
        const customSelect = document.getElementById('facet-select');
        const optionsContainer = document.getElementById('facet-options');
        if (customSelect && optionsContainer) {
            customSelect.classList.remove('loading', 'success');
            customSelect.classList.add('error');
            optionsContainer.innerHTML = `
                <div class="error-message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>${raEscape(raT('errorPrefix', 'Error:'))} ${raEscape(error.message)}</span>
                </div>
            `;
            customSelect.dataset.modulesLoaded = 'false';
            window.facetModulesLoaded = false;
        }
        
        return Promise.reject(error);
    }
}

async function loadRolesForFacet(facetId) {
    const roleOptions = document.getElementById('role-options');
    if (!roleOptions) return;
    
    // Show loading state
    const loadingRoles = raT('loadingRoles', 'Loading roles...');
    roleOptions.innerHTML = '<div class="loading-options">' + raEscape(loadingRoles) + '</div>';
    
    try {
        // If no facetId, show message to select facet first
        if (!facetId || facetId === 'null') {
            roleOptions.innerHTML = '<div class="no-results">' + raEscape(raT('selectFacetFirst', 'Please select a facet first')) + '</div>';
            return;
        }
        
        // Fetch roles from API
        const response = await fetch(`/api/object-roles?action=getByFacet&facetId=${facetId}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success && data.data) {
            const roles = data.data;
            
            if (roles.length === 0) {
                roleOptions.innerHTML = '<div class="no-results">' + raEscape(raT('noRolesForFacet', 'No roles found for this facet')) + '</div>';
            } else {
                // Build options HTML
                let optionsHTML = '';
                roles.forEach(role => {
                    optionsHTML += `
                        <div class="select-option" data-value="${role.id}" data-text="${role.primaryname}">
                            ${role.primaryname}
                        </div>
                    `;
                });
                roleOptions.innerHTML = optionsHTML;
            }
        } else {
            throw new Error(data.error || 'Failed to load roles');
        }
        
    } catch (error) {
        console.error('Error loading roles:', error);
        roleOptions.innerHTML = `
            <div class="error-message">
                <i class="fas fa-exclamation-triangle"></i>
                <span>Error loading roles: ${error.message}</span>
            </div>
        `;
    }
    
    // No need to re-initialize, just update the options
}

// Initialize custom select functionality
function initializeCustomSelect() {
    const customSelect = document.getElementById('facet-select');
    if (!customSelect) {
        console.error('facet-select element not found');
        return;
    }
    
    const selectTrigger = customSelect.querySelector('.select-trigger');
    const selectDropdown = customSelect.querySelector('.select-dropdown');
    const selectPlaceholder = customSelect.querySelector('.select-placeholder');
    const optionsContainer = customSelect.querySelector('.select-options');
    const searchInput = customSelect.querySelector('.search-input');
    
    if (!selectTrigger || !selectDropdown || !selectPlaceholder || !optionsContainer) {
        console.error('Required elements not found for facet-select:', {
            selectTrigger: !!selectTrigger,
            selectDropdown: !!selectDropdown,
            selectPlaceholder: !!selectPlaceholder,
            optionsContainer: !!optionsContainer
        });
        return;
    }
    
    // Check if options already exist
    const existingOptions = optionsContainer.querySelectorAll('.select-option');
    console.log(`initializeCustomSelect: Found ${existingOptions.length} existing options`);
    
    let selectedValue = '';
    let selectedText = '';
    let options = [];
    let currentIndex = -1;
    
    // Search functionality
    if (searchInput) {
        searchInput.addEventListener('input', function() {
            const searchTerm = this.value.toLowerCase();
            const allOptions = optionsContainer.querySelectorAll('.select-option');
            
            allOptions.forEach(option => {
                const text = option.textContent.toLowerCase();
                if (text.includes(searchTerm)) {
                    option.style.display = 'block';
                } else {
                    option.style.display = 'none';
                }
            });
        });
    }
    
    // Click handler for trigger
    selectTrigger.addEventListener('click', function(e) {
        e.stopPropagation();
        e.preventDefault();
        // Close all other dropdowns first
        closeAllDropdowns();
        toggleDropdown();
    });
        
    // Click handler for options - use event delegation to handle dynamically added options
    optionsContainer.addEventListener('click', function(e) {
        e.stopPropagation();
        const option = e.target.closest('.select-option');
        if (option && !option.classList.contains('loading-options') && !option.classList.contains('error-message')) {
            console.log('Option clicked:', option.dataset.text, option.dataset.value);
            selectOption(option);
        }
    });
    
    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!customSelect.contains(e.target)) {
            closeDropdown();
        }
    });
    
    // Keyboard navigation for dropdown
    selectDropdown.addEventListener('keydown', function(e) {
        switch(e.key) {
            case 'ArrowDown':
                e.preventDefault();
                navigateOptions(1);
                break;
            case 'ArrowUp':
                e.preventDefault();
                navigateOptions(-1);
                break;
            case 'Enter':
                e.preventDefault();
                if (currentIndex >= 0 && options[currentIndex]) {
                    selectOption(options[currentIndex]);
                }
                break;
            case 'Escape':
                e.preventDefault();
                closeDropdown();
                selectTrigger.focus();
                break;
        }
    });
    
    function toggleDropdown() {
        if (selectDropdown.classList.contains('show')) {
            closeDropdown();
        } else {
            // Close other dropdowns before opening this one
            closeAllDropdowns();
            // Check if options are available before opening
            const availableOptions = Array.from(optionsContainer.querySelectorAll('.select-option:not(.loading-options):not(.error-message)'));
            console.log(`Checking for options: Found ${availableOptions.length} options`);
            
            if (availableOptions.length === 0) {
                // Check if modules are already loaded (using flag)
                const modulesLoaded = customSelect.dataset.modulesLoaded === 'true' || window.facetModulesLoaded;
                const isLoading = optionsContainer.querySelector('.loading-options');
                const hasError = optionsContainer.querySelector('.error-message');
                
                console.log('No options found. State:', {
                    modulesLoaded,
                    isLoading: !!isLoading,
                    hasError: !!hasError
                });
                
                if (hasError) {
                    console.error('Error loading modules, cannot open dropdown');
                    // Try to reload on error
                    console.log('Attempting to reload modules after error...');
                    const reloadingFacets = raT('reloadingFacets', 'Reloading facets...');
                    optionsContainer.innerHTML = '<div class="loading-options">' + raEscape(reloadingFacets) + '</div>';
                    customSelect.dataset.modulesLoaded = 'false';
                    window.facetModulesLoaded = false;
                    loadFacetModules().catch(err => {
                        console.error('Failed to reload modules:', err);
                    });
                    return;
                }
                
                if (isLoading) {
                    // Check if loading has been stuck for too long (more than 10 seconds)
                    const loadingStartTime = customSelect.dataset.loadingStartTime;
                    const now = Date.now();
                    if (loadingStartTime && (now - parseInt(loadingStartTime)) > 10000) {
                        console.warn('⚠️ Loading has been stuck for more than 10 seconds, retrying...');
                        const reloadingFacets = raT('reloadingFacets', 'Reloading facets...');
                    optionsContainer.innerHTML = '<div class="loading-options">' + raEscape(reloadingFacets) + '</div>';
                        customSelect.dataset.modulesLoaded = 'false';
                        window.facetModulesLoaded = false;
                        delete customSelect.dataset.loadingStartTime;
                        loadFacetModules().catch(err => {
                            console.error('Failed to reload modules:', err);
                        });
                        return;
                    } else if (!loadingStartTime) {
                        // Mark when loading started
                        customSelect.dataset.loadingStartTime = now.toString();
                    }
                    console.log('Modules still loading, please wait...');
                    // Don't retry immediately - but set a timeout to retry if stuck
                    setTimeout(() => {
                        const stillLoading = optionsContainer.querySelector('.loading-options');
                        const stillNoOptions = optionsContainer.querySelectorAll('.select-option:not(.loading-options):not(.error-message)').length === 0;
                        if (stillLoading && stillNoOptions) {
                            console.warn('⚠️ Still loading after timeout, checking state...');
                            // Check again - if still stuck, retry
                            if (customSelect.dataset.modulesLoaded !== 'true') {
                                console.log('Retrying loadModules()...');
                                loadFacetModules().catch(err => {
                                    console.error('Failed to reload modules:', err);
                                });
                            }
                        }
                    }, 3000);
                    return;
                }
                
                if (!modulesLoaded) {
                    // Not loaded yet, trigger load
                    console.log('No options found, loading modules...');
                    customSelect.dataset.loadingStartTime = Date.now().toString();
                    loadFacetModules().catch(err => {
                        console.error('Failed to load modules:', err);
                        delete customSelect.dataset.loadingStartTime;
                    });
                    return;
                }
                
                // Modules marked as loaded but no options - this is an error state
                console.error('Modules marked as loaded but no options found. Resetting and reloading...');
                customSelect.dataset.modulesLoaded = 'false';
                window.facetModulesLoaded = false;
                optionsContainer.innerHTML = '<div class="loading-options">' + raEscape(raT('reloadingFacets', 'Reloading facets...')) + '</div>';
                loadModules().catch(err => {
                    console.error('Failed to reload modules:', err);
                });
                return;
            }
            // Small delay to ensure other dropdowns are closed
            setTimeout(() => {
                openDropdown();
            }, 10);
        }
    }
    
    function openDropdown() {
        // Ensure all other dropdowns are closed first
        closeAllDropdowns();
        
        // Check if options are loaded - if not, try to load them first
        let currentOptions = Array.from(optionsContainer.querySelectorAll('.select-option:not(.loading-options):not(.error-message)'));
        console.log(`openDropdown: Found ${currentOptions.length} options in container`);
        
        if (currentOptions.length === 0) {
            // Check if modules are marked as loaded
            const modulesLoaded = customSelect.dataset.modulesLoaded === 'true' || window.facetModulesLoaded;
            const isLoading = optionsContainer.querySelector('.loading-options');
            const hasError = optionsContainer.querySelector('.error-message');
            
            console.log('openDropdown state:', {
                modulesLoaded,
                isLoading: !!isLoading,
                hasError: !!hasError,
                containerContent: optionsContainer.innerHTML.substring(0, 150)
            });
            
            if (hasError) {
                console.error('Error message in container, cannot open dropdown');
                return;
            }
            
            if (modulesLoaded && !isLoading) {
                // Modules are loaded but options not found - this shouldn't happen
                console.error('Modules marked as loaded but no options found in DOM. Container HTML:', optionsContainer.innerHTML.substring(0, 200));
                // Don't try to reload again to prevent infinite loop
                return;
            } else if (isLoading) {
                // Limit retries
                const retryCount = parseInt(customSelect.dataset.openRetryCount || '0');
                if (retryCount < 5) {
                    console.log(`Modules still loading, waiting... (retry ${retryCount + 1}/5)`);
                    customSelect.dataset.openRetryCount = (retryCount + 1).toString();
                    setTimeout(() => {
                        openDropdown();
                    }, 500);
                    return;
                } else {
                    console.error('Max retries reached in openDropdown');
                    customSelect.dataset.openRetryCount = '0';
                    return;
                }
            } else {
                // Not loading and not loaded, start loading
                console.warn('No options available in dropdown, attempting to load modules...');
                loadFacetModules().then(() => {
                    // Try opening again after modules load
                    setTimeout(() => {
                        openDropdown();
                    }, 100);
                }).catch(error => {
                    console.error('Failed to reload modules:', error);
                });
                return;
            }
        }
        
        // Reset retry count if we have options
        customSelect.dataset.openRetryCount = '0';
        
        // Update options array
        options = currentOptions;
        
        selectDropdown.classList.add('show');
        selectTrigger.classList.add('active');
        customSelect.classList.add('open');
        
        // Reset navigation
        currentIndex = -1;
        
        // Focus first option if available
        if (options.length > 0) {
            currentIndex = 0;
            highlightOption(options[0]);
        }
        
        // Make dropdown focusable for keyboard navigation
        selectDropdown.setAttribute('tabindex', '0');
        selectDropdown.focus();
        
        console.log(`✅ Dropdown opened with ${options.length} options`);
    }
    
    function closeDropdown() {
        selectDropdown.classList.remove('show');
        selectTrigger.classList.remove('active');
        customSelect.classList.remove('open');
        currentIndex = -1;
        
        // Remove focus from dropdown
        selectDropdown.removeAttribute('tabindex');
        selectTrigger.focus();
        
        // Remove all highlights
        options.forEach(option => option.classList.remove('highlighted'));
    }
    
    function navigateOptions(direction) {
        if (options.length === 0) return;
        
        // Remove current highlight
        if (currentIndex >= 0 && options[currentIndex]) {
            options[currentIndex].classList.remove('highlighted');
        }
        
        // Calculate new index
        currentIndex += direction;
        
        // Wrap around
        if (currentIndex < 0) {
            currentIndex = options.length - 1;
        } else if (currentIndex >= options.length) {
            currentIndex = 0;
        }
        
        // Highlight new option
        if (options[currentIndex]) {
            highlightOption(options[currentIndex]);
        }
    }
    
    function highlightOption(option) {
        option.classList.add('highlighted');
        option.scrollIntoView({ block: 'nearest' });
    }
    
    function selectOption(option) {
        selectedValue = option.dataset.value;
        selectedText = option.dataset.text;
        
        // Update display
        selectPlaceholder.textContent = selectedText;
        selectPlaceholder.classList.add('selected');
        
        // Update selected state
        optionsContainer.querySelectorAll('.select-option').forEach(opt => {
            opt.classList.remove('selected', 'highlighted');
        });
        option.classList.add('selected');
        
        // Close dropdown
        closeDropdown();
        
        // Store selected value for form submission
        customSelect.dataset.selectedValue = selectedValue;
        customSelect.dataset.selectedText = selectedText;
        
        // Dispatch custom change event
        const changeEvent = new CustomEvent('change', {
            detail: {
                value: selectedValue,
                text: selectedText
            }
        });
        customSelect.dispatchEvent(changeEvent);
        
        console.log('Selected facet:', selectedText, 'ID:', selectedValue);
    }
    
    // Store functions globally for external access
    window.facetSelectFunctions = {
        getSelectedValue: () => selectedValue,
        getSelectedText: () => selectedText,
        setSelected: (value, text) => {
            selectedValue = value;
            selectedText = text;
            selectPlaceholder.textContent = text;
            selectPlaceholder.classList.add('selected');
            customSelect.dataset.selectedValue = value;
            customSelect.dataset.selectedText = text;
        }
    };
}

// Initialize role select functionality
function initializeRoleSelect() {
    const customSelect = document.getElementById('role-select');
    if (!customSelect) return;
    
    const selectTrigger = customSelect.querySelector('.select-trigger');
    const selectDropdown = customSelect.querySelector('.select-dropdown');
    const selectPlaceholder = customSelect.querySelector('.select-placeholder');
    const optionsContainer = customSelect.querySelector('.select-options');
    
    if (!selectTrigger || !selectDropdown || !selectPlaceholder || !optionsContainer) return;
    
    let selectedValue = '';
    let selectedText = '';
    let options = [];
    let currentIndex = -1;
    
    // Click handler for trigger
    selectTrigger.addEventListener('click', function(e) {
        e.stopPropagation();
        if (!customSelect.classList.contains('disabled')) {
            // Close all other dropdowns first
            closeAllDropdowns();
            toggleDropdown();
        }
    });
    
    // Click handler for options
    optionsContainer.addEventListener('click', function(e) {
        const option = e.target.closest('.select-option');
        if (option && !option.classList.contains('disabled-message') && !option.classList.contains('loading-options')) {
            selectOption(option);
        }
    });
    
    // Close dropdown when clicking outside
    document.addEventListener('click', function(e) {
        if (!customSelect.contains(e.target)) {
            closeDropdown();
        }
    });
    
    // Keyboard navigation for dropdown
    selectDropdown.addEventListener('keydown', function(e) {
        switch(e.key) {
            case 'ArrowDown':
            e.preventDefault();
            navigateOptions(1);
                break;
            case 'ArrowUp':
            e.preventDefault();
            navigateOptions(-1);
                break;
            case 'Enter':
            e.preventDefault();
                if (currentIndex >= 0 && options[currentIndex]) {
                    selectOption(options[currentIndex]);
                }
                break;
            case 'Escape':
                e.preventDefault();
            closeDropdown();
                selectTrigger.focus();
                break;
        }
    });
    
    function toggleDropdown() {
        if (selectDropdown.classList.contains('show')) {
            closeDropdown();
        } else {
            // Close other dropdowns before opening this one
            closeAllDropdowns();
            // Small delay to ensure other dropdowns are closed
            setTimeout(() => {
                openDropdown();
            }, 10);
        }
    }
    
    function openDropdown() {
        // Ensure all other dropdowns are closed first
        closeAllDropdowns();
        
        selectDropdown.classList.add('show');
        selectTrigger.classList.add('active');
        customSelect.classList.add('open');
        
        // Update options array and reset navigation
        options = Array.from(optionsContainer.querySelectorAll('.select-option'));
        currentIndex = -1;
        
        // Focus first option if available
        if (options.length > 0) {
            currentIndex = 0;
            highlightOption(options[0]);
        }
        
        // Make dropdown focusable for keyboard navigation
        selectDropdown.setAttribute('tabindex', '0');
        selectDropdown.focus();
    }
    
    function closeDropdown() {
        selectDropdown.classList.remove('show');
        selectTrigger.classList.remove('active');
        customSelect.classList.remove('open');
        currentIndex = -1;
        
        // Remove focus from dropdown
        selectDropdown.removeAttribute('tabindex');
        selectTrigger.focus();
        
        // Remove all highlights
        options.forEach(option => option.classList.remove('highlighted'));
    }
    
    function navigateOptions(direction) {
        if (options.length === 0) return;
        
        // Remove current highlight
        if (currentIndex >= 0 && options[currentIndex]) {
            options[currentIndex].classList.remove('highlighted');
        }
        
        // Calculate new index
        currentIndex += direction;
        
        // Wrap around
        if (currentIndex < 0) {
            currentIndex = options.length - 1;
        } else if (currentIndex >= options.length) {
            currentIndex = 0;
        }
        
        // Highlight new option
        if (options[currentIndex]) {
            highlightOption(options[currentIndex]);
        }
    }
    
    function highlightOption(option) {
        option.classList.add('highlighted');
        option.scrollIntoView({ block: 'nearest' });
    }
    
    function selectOption(option) {
        selectedValue = option.dataset.value;
        selectedText = option.dataset.text;
        
        // Update display
        selectPlaceholder.textContent = selectedText;
        selectPlaceholder.classList.add('selected');
        
        // Update selected state
        optionsContainer.querySelectorAll('.select-option').forEach(opt => {
            opt.classList.remove('selected', 'highlighted');
        });
        option.classList.add('selected');
        
        // Close dropdown
        closeDropdown();
        
        // Store selected value for form submission
        customSelect.dataset.selectedValue = selectedValue;
        customSelect.dataset.selectedText = selectedText;
        
        // Dispatch custom change event
        const changeEvent = new CustomEvent('change', {
            detail: {
                value: selectedValue,
                text: selectedText
            }
        });
        customSelect.dispatchEvent(changeEvent);
        
        console.log('Selected role:', selectedText, 'ID:', selectedValue);
    }
    
    // Store functions globally for external access
    window.roleSelectFunctions = {
        getSelectedValue: () => selectedValue,
        getSelectedText: () => selectedText,
        setSelected: (value, text) => {
            selectedValue = value;
            selectedText = text;
            selectPlaceholder.textContent = text;
            selectPlaceholder.classList.add('selected');
            customSelect.dataset.selectedValue = value;
            customSelect.dataset.selectedText = text;
        }
    };
}

// Enable members input when both facet and role are selected
function enableMembersInput() {
    const tbody = document.getElementById('members-table-body');
    if (tbody) {
        // Clear existing content
        tbody.innerHTML = '';
    
        // Add a new empty row for input
    const newRow = document.createElement('tr');
        newRow.className = 'member-row input-row';
    newRow.innerHTML = `
        <td>
            <div class="user-input-container autocomplete-container">
                    <input type="text" class="user-input" placeholder="Enter user name" autocomplete="off">
                    <button class="clear-input-btn" onclick="clearUserInput(this)" style="display: none;">
                    <i class="fas fa-times"></i>
                </button>
                <div class="autocomplete-suggestions" style="display: none;"></div>
            </div>
        </td>
        <td>
            <span class="email-display">-</span>
        </td>
        <td>
            <label class="checkbox-container">
                <input type="checkbox" class="stakeholder-checkbox">
                <span class="checkmark"></span>
            </label>
        </td>
        <td>
            <div class="action-buttons">
                <button class="action-btn add-btn" onclick="addMember()" title="Add New Row">
                    <i class="fas fa-plus"></i>
                </button>
                <button class="action-btn remove-btn" onclick="removeMemberRow(this)" title="Remove Row">
                    <i class="fas fa-minus"></i>
                </button>
            </div>
        </td>
    `;
    
    tbody.appendChild(newRow);
    
    // Initialize autocomplete for the new input
        const userInput = newRow.querySelector('.user-input');
        if (userInput) {
            initializeAutocomplete(userInput);
        }
        
        // Initialize member row events
        initializeMemberRowEvents(newRow);
    }
}

// Initialize members table functionality
function initializeMembersTable() {
    const container = document.getElementById('members-table-body');
    const inputs = container ? Array.from(container.querySelectorAll('.user-input')) : [];
    inputs.forEach(function(input){
        input.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !this.disabled) {
                addMember();
            }
        });
        input.addEventListener('input', function() {
            const clearBtn = this.parentElement.querySelector('.clear-input-btn');
            if (clearBtn) {
                clearBtn.style.display = this.value.trim() ? 'block' : 'none';
            }
        });
        initializeAutocomplete(input);
    });
    const stakeholderCheckbox = document.querySelector('.stakeholder-checkbox');
    if (stakeholderCheckbox) {
        stakeholderCheckbox.addEventListener('change', function() {
            console.log('Stakeholder status changed:', this.checked);
        });
    }
    initializeDependencyLogic();
}

// Initialize dependency logic between Facet and Role
function initializeDependencyLogic() {
    const facetSelect = document.getElementById('facet-select');
    const roleSelect = document.getElementById('role-select');
    
    // Check if already initialized to prevent duplicate event listeners
    if (facetSelect && facetSelect.dataset.dependencyInitialized === 'true') {
        console.log('Dependency logic already initialized, skipping...');
        return;
    }
    
    if (facetSelect) {
        // Listen for facet selection changes
        facetSelect.addEventListener('change', function() {
            const selectedFacet = this.dataset.selectedValue;
            if (selectedFacet) {
                // Clear roles and members when facet changes
                clearRolesAndMembers();
                enableRoleSelection();
                loadRolesForFacet(selectedFacet);
            } else {
                disableRoleSelection();
                disableMembersInput();
            }
        });
        
        // Mark as initialized
        facetSelect.dataset.dependencyInitialized = 'true';
    }
    
    if (roleSelect) {
        // Listen for role selection changes
        roleSelect.addEventListener('change', async function() {
            console.log('Role changed:', this.dataset.selectedValue);
            const selectedRole = this.dataset.selectedValue;
            const selectedFacet = document.getElementById('facet-select').dataset.selectedValue;

            // Always clear members table on change
            clearMembersTable();

            if (selectedRole && selectedFacet) {
                try {
                    const roleIdNum = Number(selectedRole);
                    const alreadyConfigured = await isRoleAlreadyConfigured(roleIdNum);
                    if (alreadyConfigured) {
                        // Prevent opening members and notify user
                        disableMembersInput();
                        showNotification(raT('notifyRoleAlreadyConfigured', 'The selected role is already configured for this facet'), 'error');
                        return;
                    }
                } catch (_) {}
                enableMembersInput();
            } else {
                disableMembersInput();
            }
        });
    }
}

// Initialize user input autocomplete
function initializeUserInputs() {
    const tbody = document.getElementById('members-table-body');
    if (tbody) {
        tbody.querySelectorAll('.user-input').forEach(input => {
            initializeAutocomplete(input);
        });
    }
}

// Helper function to close all dropdowns
function closeAllDropdowns() {
    const facetSelect = document.getElementById('facet-select');
    const roleSelect = document.getElementById('role-select');
    
    if (facetSelect) {
        const facetDropdown = facetSelect.querySelector('.select-dropdown');
        const facetTrigger = facetSelect.querySelector('.select-trigger');
        if (facetDropdown && facetDropdown.classList.contains('show')) {
            facetDropdown.classList.remove('show');
            facetTrigger.classList.remove('active');
            facetSelect.classList.remove('open');
        }
    }
    
    if (roleSelect) {
        const roleDropdown = roleSelect.querySelector('.select-dropdown');
        const roleTrigger = roleSelect.querySelector('.select-trigger');
        if (roleDropdown && roleDropdown.classList.contains('show')) {
            roleDropdown.classList.remove('show');
            roleTrigger.classList.remove('active');
            roleSelect.classList.remove('open');
        }
    }
}

// Check if a role is already configured (has a role_assignment row)
async function isRoleAlreadyConfigured(objectRoleId) {
    try {
        if (!objectRoleId) return false;
        // Use cached assignments if present
        if (!Array.isArray(window._roleAssignmentsCache)) {
            window._roleAssignmentsCache = await fetchRoleAssignments();
        }
        const list = Array.isArray(window._roleAssignmentsCache) ? window._roleAssignmentsCache : [];
        return list.some(function(ra){ return Number(ra.objectRoleId) === Number(objectRoleId); });
    } catch (_) { return false; }
}

// Enable role selection
function enableRoleSelection() {
    const roleSelect = document.getElementById('role-select');
    const roleValidation = document.getElementById('role-validation');
    
    if (roleSelect) {
        roleSelect.classList.remove('disabled');
        roleSelect.querySelector('.select-placeholder').textContent = raT('selectRole', 'Select a role');
        
        // Clear any existing options and show loading
        const optionsContainer = document.getElementById('role-options');
        if (optionsContainer) {
            optionsContainer.innerHTML = '<div class="loading-options">' + raEscape(raT('loadingRoles', 'Loading roles...')) + '</div>';
        }
    }
    
    if (roleValidation) {
        roleValidation.style.display = 'none';
    }
}

// Disable role selection
function disableRoleSelection() {
    const roleSelect = document.getElementById('role-select');
    const roleValidation = document.getElementById('role-validation');
    
    if (roleSelect) {
        roleSelect.classList.add('disabled');
        roleSelect.querySelector('.select-placeholder').textContent = raT('selectFacetFirst', 'Please select a facet first');
        roleSelect.dataset.selectedValue = '';
        roleSelect.dataset.selectedText = '';
        
        // Clear options and show disabled message
        const optionsContainer = document.getElementById('role-options');
        if (optionsContainer) {
            optionsContainer.innerHTML = '<div class="disabled-message">' + raEscape(raT('selectFacetFirst', 'Please select a facet first')) + '</div>';
        }
    }
    
    if (roleValidation) {
        roleValidation.style.display = 'block';
    }
}

// Clear roles and members when facet changes
function clearRolesAndMembers() {
    // Clear roles dropdown
    const roleSelect = document.getElementById('role-select');
    if (roleSelect) {
        roleSelect.dataset.selectedValue = '';
        roleSelect.dataset.selectedText = '';
        
        // Reset role select to initial state
        const rolePlaceholder = roleSelect.querySelector('.select-placeholder');
        if (rolePlaceholder) {
            rolePlaceholder.textContent = raT('selectFacetFirst', 'Please select a facet first');
            rolePlaceholder.classList.remove('selected');
        }
        
        // Clear role options
        const roleOptions = document.getElementById('role-options');
        if (roleOptions) {
            roleOptions.innerHTML = '<div class="disabled-message">' + raEscape(raT('selectFacetFirst', 'Please select a facet first')) + '</div>';
        }
        
        // Remove selected state from role options
        roleOptions.querySelectorAll('.select-option').forEach(opt => {
            opt.classList.remove('selected');
        });
    }
    
    // Clear members table
    clearMembersTable();
}

// Clear members table
function clearMembersTable() {
    const tbody = document.getElementById('members-table-body');
    if (tbody) {
        // Remove all existing rows
        tbody.innerHTML = '';
        
        // Add back the initial disabled row
        const initialRow = document.createElement('tr');
        initialRow.className = 'member-row disabled-row';
        initialRow.innerHTML = `
                <td>
                    <div class="user-input-container autocomplete-container">
                    <input type="text" class="user-input disabled" placeholder="select another role" disabled autocomplete="off">
                        <button class="clear-input-btn" onclick="clearUserInput(this)" disabled>
                            <i class="fas fa-times"></i>
                        </button>
                        <div class="autocomplete-suggestions" style="display: none;"></div>
                    </div>
                </td>
                <td>
                    <span class="email-display">-</span>
                </td>
                <td>
                    <label class="checkbox-container">
                        <input type="checkbox" class="stakeholder-checkbox" disabled>
                        <span class="checkmark"></span>
                    </label>
                </td>
                <td>
                    <div class="action-buttons">
                        <button class="action-btn add-btn disabled" onclick="addMember()" title="Add New Row" disabled>
                            <i class="fas fa-plus"></i>
                        </button>
                        <button class="action-btn remove-btn disabled" onclick="removeMemberRow(this)" title="Remove Row" disabled>
                            <i class="fas fa-minus"></i>
                        </button>
                    </div>
                </td>
        `;
        
        tbody.appendChild(initialRow);
        
        // Re-initialize autocomplete for the new input
        setTimeout(() => {
            const container = document.getElementById('members-table-body');
            const inputs = container ? Array.from(container.querySelectorAll('.user-input')) : [];
            inputs.forEach(function(input){ initializeAutocomplete(input); });
        }, 100);
        
        console.log('Members table cleared and reset to initial state');
    }
}

// Disable members input
function disableMembersInput() {
    const tbody = document.getElementById('members-table-body');
    if (tbody) {
        tbody.querySelectorAll('.user-input').forEach(input => {
            input.disabled = true;
            input.placeholder = 'Please select Facet and Role first';
        });
        tbody.querySelectorAll('.stakeholder-checkbox').forEach(checkbox => {
            checkbox.disabled = true;
        });
        tbody.querySelectorAll('.action-btn').forEach(btn => {
            btn.disabled = true;
            btn.classList.add('disabled');
        });
        tbody.querySelectorAll('.member-row').forEach(row => {
            row.classList.add('disabled-row');
        });
    }
}

// Members Section Functions
function openMembersSettings() {
    console.log('Opening members settings...');
    // Placeholder for members settings functionality
    showNotification(raT('notifyMembersSettingsInfo', 'Members settings functionality will be implemented here.'), 'info');
}

function clearUserInput(button) {
    const userInput = button.parentElement.querySelector('.user-input');
    const row = userInput.closest('tr');
    const emailDisplay = row.querySelector('.email-display');
    
    if (userInput) {
        userInput.value = '';
        delete userInput.dataset.personId;
        userInput.focus();
        button.style.display = 'none';
        
        // Clear the email display when clearing the name
        if (emailDisplay) {
            emailDisplay.textContent = '-';
        }
        
        // Hide any suggestions
        hideSuggestions(userInput);
    }
}

function addMember() {
    const selectedFacet = document.getElementById('facet-select').dataset.selectedValue;
    const selectedRole = document.getElementById('role-select').dataset.selectedValue;
    
    // Check if facet and role are selected
    if (!selectedFacet || !selectedRole) {
        showNotification(raT('notifySelectFacetRoleBeforeAdd', 'Please select both Facet and Role before adding member rows.'), 'error');
        return;
    }
    
    // Create new input row
    const tableBody = document.getElementById('members-table-body');
    if (tableBody) {
        const newRow = document.createElement('tr');
        newRow.className = 'member-row input-row';
        newRow.innerHTML = `
            <td>
                <div class="user-input-container autocomplete-container">
                    <input type="text" class="user-input" placeholder="Enter user name" autocomplete="off">
                    <button class="clear-input-btn" onclick="clearUserInput(this)" style="display: none;">
                        <i class="fas fa-times"></i>
                    </button>
                    <div class="autocomplete-suggestions" style="display: none;"></div>
                </div>
            </td>
            <td>
                <span class="email-display">-</span>
            </td>
            <td>
                <label class="checkbox-container">
                    <input type="checkbox" class="stakeholder-checkbox">
                    <span class="checkmark"></span>
                </label>
            </td>
            <td>
                <div class="action-buttons">
                    <button class="action-btn add-btn" onclick="addMember()" title="Add New Row">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button class="action-btn remove-btn" onclick="removeMemberRow(this)" title="Remove Row">
                        <i class="fas fa-minus"></i>
                    </button>
                </div>
            </td>
        `;
        
        // Add the new row at the end
        tableBody.appendChild(newRow);
        
        // Initialize autocomplete for the new input
        const newInput = newRow.querySelector('.user-input');
        if (newInput) {
            initializeAutocomplete(newInput);
            newInput.focus();
        }
        
        // Initialize event listeners
        initializeMemberRowEvents(newRow);
    }
}

function removeMemberRow(button) {
    const row = button.closest('tr');
    if (row) {
        // Check if this is the first row and if it's the only row
        const tableBody = document.getElementById('members-table-body');
        const allRows = tableBody.querySelectorAll('.member-row');
        
        if (allRows.length === 1) {
            // If it's the only row, just clear the input instead of removing
            const userInput = row.querySelector('.user-input');
            const emailDisplay = row.querySelector('.email-display');
            
            if (userInput) {
                userInput.value = '';
                userInput.focus();
            }
            
            if (emailDisplay) {
                emailDisplay.textContent = '-';
            }
        } else {
            // Remove the row if there are multiple rows
            row.remove();
        }
    }
}

function initializeMemberRowEvents(row) {
    const userInput = row.querySelector('.user-input');
    const clearBtn = row.querySelector('.clear-input-btn');
    
    if (userInput) {
        userInput.addEventListener('input', function() {
            if (clearBtn) {
                clearBtn.style.display = this.value.trim() ? 'block' : 'none';
            }
        });
        
        userInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !this.disabled) {
                addMember();
            }
        });
    }
    
    const stakeholderCheckbox = row.querySelector('.stakeholder-checkbox');
    if (stakeholderCheckbox) {
        stakeholderCheckbox.addEventListener('change', function() {
            console.log('Stakeholder status changed:', this.checked);
        });
    }
}

// Initialize autocomplete for a user input
function initializeAutocomplete(input) {
    if (!input || input.dataset.autocompleteInitialized) return;
    
    input.dataset.autocompleteInitialized = 'true';
    let currentSuggestions = [];
    let selectedIndex = -1;
    let debounceTimer = null;
    
    // Add input event listener
    input.addEventListener('input', function() {
        const query = this.value.trim();
        const row = this.closest('tr');
        const emailDisplay = row.querySelector('.email-display');
        
        // Clear previous timer
        if (debounceTimer) {
            clearTimeout(debounceTimer);
        }
        
        // If input is empty, clear the email display
        if (query.length === 0 && emailDisplay) {
            emailDisplay.textContent = '-';
        }
        
        // Debounce the search
        debounceTimer = setTimeout(() => {
            if (query.length >= 2) {
                searchPeople(query, this, (suggestions, index) => {
                    currentSuggestions = suggestions;
                    selectedIndex = index;
                });
            } else {
                hideSuggestions(this);
            }
        }, 300);
    });
    
    // Add keyboard navigation
    input.addEventListener('keydown', function(e) {
        const suggestions = this.parentElement.querySelector('.autocomplete-suggestions');
        if (!suggestions || suggestions.style.display === 'none') return;
        
        const suggestionItems = suggestions.querySelectorAll('.autocomplete-suggestion');
        
        switch(e.key) {
            case 'ArrowDown':
            e.preventDefault();
            selectedIndex = Math.min(selectedIndex + 1, suggestionItems.length - 1);
            updateHighlight(suggestionItems, selectedIndex);
                break;
                
            case 'ArrowUp':
            e.preventDefault();
            selectedIndex = Math.max(selectedIndex - 1, -1);
            updateHighlight(suggestionItems, selectedIndex);
                break;
                
            case 'Enter':
            e.preventDefault();
            if (selectedIndex >= 0 && suggestionItems[selectedIndex]) {
                suggestionItems[selectedIndex].click();
            }
                break;
                
            case 'Escape':
            e.preventDefault();
            hideSuggestions(this);
            selectedIndex = -1;
                break;
        }
    });
    
    // Hide suggestions when clicking outside
    document.addEventListener('click', function(e) {
        if (!input.parentElement.contains(e.target)) {
            hideSuggestions(input);
        }
    });
    
    // Reposition suggestions on scroll or resize
    const repositionSuggestions = () => {
        const suggestions = input.parentElement.querySelector('.autocomplete-suggestions');
        if (suggestions && suggestions.style.display !== 'none') {
            const inputRect = input.getBoundingClientRect();
            suggestions.style.top = (inputRect.bottom + window.scrollY) + 'px';
            suggestions.style.left = inputRect.left + 'px';
            suggestions.style.width = inputRect.width + 'px';
        }
    };
    
    window.addEventListener('scroll', repositionSuggestions);
    window.addEventListener('resize', repositionSuggestions);
}

async function searchPeople(query, input, callback) {
    try {
        const response = await fetch(`/api/people/search-firstname?firstName=${encodeURIComponent(query)}`);
        const data = await response.json();
        
        if (data.success && data.data) {
            showSuggestions(data.data, input);
            if (callback) {
                callback(data.data, -1);
            }
        } else {
            hideSuggestions(input);
        }
    } catch (error) {
        console.error('Error searching people:', error);
        hideSuggestions(input);
    }
}

function showSuggestions(people, input) {
    const container = input.parentElement;
    const suggestions = container.querySelector('.autocomplete-suggestions');
    
    if (!suggestions) return;
    
    // Clear previous suggestions
    suggestions.innerHTML = '';
    
    if (people.length === 0) {
        suggestions.innerHTML = '<div class="autocomplete-suggestion">' + raEscape(raT('noResultsFound', 'No results found')) + '</div>';
    } else {
        people.forEach((person, index) => {
            const suggestion = document.createElement('div');
            suggestion.className = 'autocomplete-suggestion';
            suggestion.dataset.index = index;
            suggestion.dataset.personId = person.id ?? person.ID ?? '';
            suggestion.innerHTML = `
                <div>
                    <div class="suggestion-name">${person.first_name} ${person.last_name || ''}</div>
                    <div class="suggestion-email">${person.email || 'No email'}</div>
                </div>
            `;
            
            suggestion.addEventListener('click', () => selectSuggestion(suggestion, input));
            suggestions.appendChild(suggestion);
        });
    }
    
    // Position the suggestions dropdown properly
    const inputRect = input.getBoundingClientRect();
    const containerRect = container.getBoundingClientRect();
    
    // Set position to fixed to ensure it appears above all layers
    suggestions.style.position = 'fixed';
    suggestions.style.top = (inputRect.bottom + window.scrollY) + 'px';
    suggestions.style.left = inputRect.left + 'px';
    suggestions.style.width = inputRect.width + 'px';
    suggestions.style.zIndex = '999999';
    suggestions.style.display = 'block';
}

function hideSuggestions(input) {
    const container = input.parentElement;
    const suggestions = container.querySelector('.autocomplete-suggestions');
    if (suggestions) {
        suggestions.style.display = 'none';
    }
}

function updateHighlight(suggestionItems, selectedIndex) {
    suggestionItems.forEach((item, index) => {
        if (index === selectedIndex) {
            item.classList.add('highlighted');
        } else {
            item.classList.remove('highlighted');
        }
    });
}

function selectSuggestion(suggestion, input) {
    const container = input.parentElement;
    const row = input.closest('tr');
    const emailDisplay = row.querySelector('.email-display');
    
    // Get person data from suggestion
    const nameElement = suggestion.querySelector('.suggestion-name');
    const emailElement = suggestion.querySelector('.suggestion-email');
    
    if (nameElement && emailElement) {
        // Set the input value
        input.value = nameElement.textContent.trim();
        // Attach selected person ID to input for saving
        const pid = suggestion.dataset.personId || '';
        if (pid !== '') {
            input.dataset.personId = pid;
            if (window._ra_current_user_ids) {
                try { window._ra_current_user_ids.add(Number(pid)); } catch (_) {}
            }
        }
        
        // Set the email display
    if (emailDisplay) {
            emailDisplay.textContent = emailElement.textContent.trim();
    }
    
        // Hide suggestions
    hideSuggestions(input);
        
        // Focus back on input
        input.focus();
        
        console.log('Selected person:', nameElement.textContent.trim());
    }
}

// Load role assignments
async function loadRoleAssignments() {
    const container = document.getElementById('role-assignments-list');
    if (!container) return;
    
    try {
        const assignments = await fetchRoleAssignments();
        if (assignments.length === 0) {
            container.innerHTML = `
            <div class="empty-state">
                <i class="fas fa-info-circle"></i>
                <p>No roles are assigned. To define a role assignment, click Add.</p>
            </div>
        `;
        } else {
            container.innerHTML = `
                <div class="data-table-container">
        <div class="table-responsive">
                        <table class="data-table" id="roleAssignmentTable">
                <thead>
                                <tr class="header-labels">
                                    <th>Facet</th>
                                    <th>Role</th>
                                    <th>No Of Users</th>
                                    <th>Last Updated</th>
                    </tr>
                </thead>
                            <tbody id="roleAssignmentTableBody"></tbody>
            </table>
                    </div>
        </div>
    `;
            renderRoleAssignmentTable();
        }
    } catch (error) {
        console.error('Error loading role assignments:', error);
        container.innerHTML = `
        <div class="error-state">
            <i class="fas fa-exclamation-triangle"></i>
                <p>${raEscape(raT('failedToLoadRoleAssignments', 'Failed to load role assignments'))}</p>
        </div>
    `;
    }
}

// highlightSubmenuItem / goBack / restoreOriginalHeader — admin-panel main.js (do not redefine globally)