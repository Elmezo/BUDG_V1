// Ownership Transfer functionality for Operational Management

let ownershipTransferState = {
    currentTab: 'dashboard',
    dashboards: [],
    searches: [],
    activeUsers: [],
    selectedDashboards: new Set(),
    selectedSearches: new Set(),
    filters: {
        dashboard: {
            previousOwner: '',
            userInactiveFrom: '',
            dashboardName: '',
            description: '',
            sharing: ''
        },
        search: {
            previousOwner: '',
            userInactiveFrom: '',
            searchName: '',
            description: '',
            sharing: ''
        }
    },
    eventListenersSetup: false
};

function showOwnershipTransferContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.ownershipTransfer');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    
    contentArea.innerHTML = `
        <div class="ownership-transfer-container">
            <div class="ownership-transfer-header">
                <div class="header-content">
                    <button class="back-button" onclick="ownershipTransferBackToOperational()">
                        <i class="fas fa-arrow-left"></i>
                    </button>
                    <div class="header-text">
                        <h1>${T('adminPanel.ownershipTransfer.title', 'Ownership Transfer')}</h1>
                        <p>${T('adminPanel.ownershipTransfer.intro', 'Transfer the ownership for dashboards and shared searches from an inactive user to an active user.')}</p>
                    </div>
                </div>
            </div>
            
            <div class="ownership-transfer-tabs">
                <button class="tab-button ${ownershipTransferState.currentTab === 'dashboard' ? 'active' : ''}" 
                        data-tab="dashboard" onclick="switchOwnershipTab('dashboard')">
                    ${T('adminPanel.ownershipTransfer.tabDashboard', 'DASHBOARD')}
                </button>
                <button class="tab-button ${ownershipTransferState.currentTab === 'search' ? 'active' : ''}" 
                        data-tab="search" onclick="switchOwnershipTab('search')">
                    ${T('adminPanel.ownershipTransfer.tabSearch', 'SEARCH')}
                </button>
            </div>
            
            <div class="ownership-transfer-content">
                <div id="dashboard-tab-content" class="tab-content ${ownershipTransferState.currentTab === 'dashboard' ? 'active' : ''}">
                    ${renderDashboardTab()}
                </div>
                <div id="search-tab-content" class="tab-content ${ownershipTransferState.currentTab === 'search' ? 'active' : ''}">
                    ${renderSearchTab()}
                </div>
            </div>
        </div>
    `;
    
    // Set up event delegation for table row clicks (backup for inline onclick)
    setTimeout(() => {
        setupTableEventDelegation();
    }, 100);
    
    // Load initial data
    loadOwnershipTransferData();
}

function setupTableEventDelegation() {
    // Remove old listeners by cloning (if they exist)
    const dashboardContainer = document.querySelector('#dashboard-tab-content .ownership-table-container');
    if (dashboardContainer) {
        // Remove old listener flag to allow re-attachment
        delete dashboardContainer.dataset.listenerAttached;
        
        // Clone to remove old listeners
        const newContainer = dashboardContainer.cloneNode(true);
        dashboardContainer.parentNode.replaceChild(newContainer, dashboardContainer);
        
        // Add fresh event listener
        newContainer.dataset.listenerAttached = 'true';
        newContainer.addEventListener('click', (e) => {
            // Don't trigger if clicking on filter inputs, headers, or other interactive elements
            if (e.target.tagName === 'INPUT' || 
                e.target.tagName === 'BUTTON' || 
                e.target.closest('input, button, thead')) {
                return;
            }
            const row = e.target.closest('tr.selectable-row');
            if (row && row.dataset.dashboardId) {
                e.stopPropagation();
                e.preventDefault();
                const dashboardId = parseInt(row.dataset.dashboardId);
                console.log('[OwnershipTransfer] Dashboard row clicked:', dashboardId);
                toggleDashboardSelection(dashboardId);
            }
        });
    }
    
    const searchContainer = document.querySelector('#search-tab-content .ownership-table-container');
    if (searchContainer) {
        // Remove old listener flag to allow re-attachment
        delete searchContainer.dataset.listenerAttached;
        
        // Clone to remove old listeners
        const newContainer = searchContainer.cloneNode(true);
        searchContainer.parentNode.replaceChild(newContainer, searchContainer);
        
        // Add fresh event listener
        newContainer.dataset.listenerAttached = 'true';
        newContainer.addEventListener('click', (e) => {
            // Don't trigger if clicking on filter inputs, headers, or other interactive elements
            if (e.target.tagName === 'INPUT' || 
                e.target.tagName === 'BUTTON' || 
                e.target.closest('input, button, thead')) {
                return;
            }
            const row = e.target.closest('tr.selectable-row');
            if (row && row.dataset.searchId) {
                e.stopPropagation();
                e.preventDefault();
                const searchId = parseInt(row.dataset.searchId);
                console.log('[OwnershipTransfer] Search row clicked:', searchId, 'type:', typeof searchId);
                console.log('[OwnershipTransfer] Row element:', row);
                console.log('[OwnershipTransfer] Row classes:', row.className);
                toggleSearchSelection(searchId);
            }
        });
    }
}

function renderDashboardTab() {
    return `
        <div class="ownership-section">
            <div class="section-header">
                <h2>DASHBOARD OWNERSHIP TRANSFER</h2>
                <button id="dashboard-transfer-btn" class="btn-transfer" onclick="openTransferModal('dashboard')" 
                        ${ownershipTransferState.selectedDashboards.size === 0 ? 'disabled' : ''}>
                    Transfer Ownership
                </button>
            </div>
            <div class="ownership-table-container">
                ${renderDashboardTable()}
            </div>
        </div>
    `;
}

function renderSearchTab() {
    return `
        <div class="ownership-section">
            <div class="section-header">
                <h2>SEARCH OWNERSHIP TRANSFER</h2>
                <button id="search-transfer-btn" class="btn-transfer" onclick="openTransferModal('search')" 
                        ${ownershipTransferState.selectedSearches.size === 0 ? 'disabled' : ''}>
                    Transfer Ownership
                </button>
            </div>
            <div class="ownership-table-container">
                ${renderSearchTable()}
            </div>
        </div>
    `;
}

function renderDashboardTable() {
    // Always render table + tbody so refreshTable/loadOwnershipTransferData can update rows without "tbody not found"
    const emptyDashboardMessage = `
        <tr>
            <td colspan="5" class="empty-row">
                <div class="empty-state">
                    <i class="fas fa-info-circle"></i>
                    <p>There are no dashboards available for transfer.</p>
                    <p style="font-size: 0.85rem; color: #6c757d; margin-top: 0.5rem;">
                        Dashboards will appear here if they are owned by inactive users and have been shared with other users (not public or private).
                    </p>
                </div>
            </td>
        </tr>
    `;

    if (ownershipTransferState.dashboards.length === 0) {
        return `
        <table class="ownership-table">
            <thead>
                <tr>
                    <th><div>Previous Owner</div></th>
                    <th><div>User Inactive From</div></th>
                    <th><div>Dashboard Name</div></th>
                    <th><div>Description</div></th>
                    <th><div>Sharing</div></th>
                </tr>
            </thead>
            <tbody id="dashboard-table-body">${emptyDashboardMessage}</tbody>
        </table>`;
    }

    return `
        <table class="ownership-table">
            <thead>
                <tr>
                    <th>
                        <div>Previous Owner</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.dashboard.previousOwner}"
                               oninput="filterOwnershipTable('dashboard', 'previousOwner', this.value)">
                    </th>
                    <th>
                        <div>User Inactive From</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.dashboard.userInactiveFrom}"
                               oninput="filterOwnershipTable('dashboard', 'userInactiveFrom', this.value)">
                    </th>
                    <th>
                        <div>Dashboard Name</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.dashboard.dashboardName}"
                               oninput="filterOwnershipTable('dashboard', 'dashboardName', this.value)">
                    </th>
                    <th>
                        <div>Description</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.dashboard.description}"
                               oninput="filterOwnershipTable('dashboard', 'description', this.value)">
                    </th>
                    <th>
                        <div>Sharing</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.dashboard.sharing}"
                               oninput="filterOwnershipTable('dashboard', 'sharing', this.value)">
                    </th>
                </tr>
            </thead>
            <tbody id="dashboard-table-body">
                ${renderDashboardTableRows()}
            </tbody>
        </table>
    `;
}

function renderDashboardTableRows() {
    const filtered = getFilteredDashboards();
    
    if (filtered.length === 0) {
        return `
            <tr>
                <td colspan="5" class="empty-row">
                    <div class="empty-state">
                        <i class="fas fa-info-circle"></i>
                        <p>No dashboards match the filter criteria.</p>
                    </div>
                </td>
            </tr>
        `;
    }
    
    return filtered.map(dashboard => {
        const dashboardId = dashboard.id;
        const isSelected = ownershipTransferState.selectedDashboards.has(dashboardId);
        const selectedClass = isSelected ? 'selected' : '';
        const selectedStyle = isSelected ? 'background-color: #e3f2fd; border-left: 3px solid #248567;' : '';
        return `
            <tr class="selectable-row ${selectedClass}" 
                data-dashboard-id="${dashboardId}"
                style="${selectedStyle}">
                <td>${escapeHtml(dashboard.previousOwner || 'N/A')}</td>
                <td>${escapeHtml(dashboard.userInactiveFrom || 'N/A')}</td>
                <td>${escapeHtml(dashboard.dashboardName || 'N/A')}</td>
                <td>${escapeHtml(dashboard.description || 'N/A')}</td>
                <td>${escapeHtml(dashboard.sharing || 'N/A')}</td>
            </tr>
        `;
    }).join('');
}

function renderSearchTable() {
    // Always render table + tbody so refreshTable/loadOwnershipTransferData can update rows without "tbody not found"
    const emptySearchMessage = `
        <tr>
            <td colspan="5" class="empty-row">
                <div class="empty-state">
                    <i class="fas fa-info-circle"></i>
                    <p>There are no saved searches available for transfer.</p>
                    <p style="font-size: 0.85rem; color: #6c757d; margin-top: 0.5rem;">
                        Saved searches will appear here if they are owned by inactive users and have been shared with other users (not public or private).
                    </p>
                </div>
            </td>
        </tr>
    `;

    if (ownershipTransferState.searches.length === 0) {
        return `
        <table class="ownership-table">
            <thead>
                <tr>
                    <th><div>Previous Owner</div></th>
                    <th><div>User Inactive From</div></th>
                    <th><div>Search Name</div></th>
                    <th><div>Description</div></th>
                    <th><div>Sharing</div></th>
                </tr>
            </thead>
            <tbody id="search-table-body">${emptySearchMessage}</tbody>
        </table>`;
    }

    return `
        <table class="ownership-table">
            <thead>
                <tr>
                    <th>
                        <div>Previous Owner</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.search.previousOwner}"
                               oninput="filterOwnershipTable('search', 'previousOwner', this.value)">
                    </th>
                    <th>
                        <div>User Inactive From</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.search.userInactiveFrom}"
                               oninput="filterOwnershipTable('search', 'userInactiveFrom', this.value)">
                    </th>
                    <th>
                        <div>Search Name</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.search.searchName}"
                               oninput="filterOwnershipTable('search', 'searchName', this.value)">
                    </th>
                    <th>
                        <div>Description</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.search.description}"
                               oninput="filterOwnershipTable('search', 'description', this.value)">
                    </th>
                    <th>
                        <div>Sharing</div>
                        <input type="text" class="filter-input" placeholder="Filter..." 
                               value="${ownershipTransferState.filters.search.sharing}"
                               oninput="filterOwnershipTable('search', 'sharing', this.value)">
                    </th>
                </tr>
            </thead>
            <tbody id="search-table-body">
                ${renderSearchTableRows()}
            </tbody>
        </table>
    `;
}

function renderSearchTableRows() {
    const filtered = getFilteredSearches();
    
    if (filtered.length === 0) {
        return `
            <tr>
                <td colspan="5" class="empty-row">
                    <div class="empty-state">
                        <i class="fas fa-info-circle"></i>
                        <p>No searches match the filter criteria.</p>
                    </div>
                </td>
            </tr>
        `;
    }
    
    return filtered.map(search => {
        // Ensure search.id is treated as a number consistently
        const searchId = typeof search.id === 'string' ? parseInt(search.id) : search.id;
        const isSelected = ownershipTransferState.selectedSearches.has(searchId);
        const selectedClass = isSelected ? 'selected' : '';
        const selectedStyle = isSelected ? 'background-color: #e3f2fd; border-left: 3px solid #248567;' : '';
        console.log(`[OwnershipTransfer] Rendering search row ID: ${searchId} (type: ${typeof searchId}), selected: ${isSelected}, class: "${selectedClass}"`);
        return `
            <tr class="selectable-row ${selectedClass}" 
                data-search-id="${searchId}"
                style="${selectedStyle}">
                <td>${escapeHtml(search.previousOwner || 'N/A')}</td>
                <td>${escapeHtml(search.userInactiveFrom || 'N/A')}</td>
                <td>${escapeHtml(search.searchName || 'N/A')}</td>
                <td>${escapeHtml(search.description || 'N/A')}</td>
                <td>${escapeHtml(search.sharing || 'N/A')}</td>
            </tr>
        `;
    }).join('');
}

function getFilteredDashboards() {
    const filters = ownershipTransferState.filters.dashboard;
    return ownershipTransferState.dashboards.filter(dashboard => {
        return (!filters.previousOwner || (dashboard.previousOwner || '').toLowerCase().includes(filters.previousOwner.toLowerCase())) &&
               (!filters.userInactiveFrom || (dashboard.userInactiveFrom || '').toLowerCase().includes(filters.userInactiveFrom.toLowerCase())) &&
               (!filters.dashboardName || (dashboard.dashboardName || '').toLowerCase().includes(filters.dashboardName.toLowerCase())) &&
               (!filters.description || (dashboard.description || '').toLowerCase().includes(filters.description.toLowerCase())) &&
               (!filters.sharing || (dashboard.sharing || '').toLowerCase().includes(filters.sharing.toLowerCase()));
    });
}

function getFilteredSearches() {
    const filters = ownershipTransferState.filters.search;
    return ownershipTransferState.searches.filter(search => {
        return (!filters.previousOwner || (search.previousOwner || '').toLowerCase().includes(filters.previousOwner.toLowerCase())) &&
               (!filters.userInactiveFrom || (search.userInactiveFrom || '').toLowerCase().includes(filters.userInactiveFrom.toLowerCase())) &&
               (!filters.searchName || (search.searchName || '').toLowerCase().includes(filters.searchName.toLowerCase())) &&
               (!filters.description || (search.description || '').toLowerCase().includes(filters.description.toLowerCase())) &&
               (!filters.sharing || (search.sharing || '').toLowerCase().includes(filters.sharing.toLowerCase()));
    });
}

function filterOwnershipTable(type, field, value) {
    ownershipTransferState.filters[type][field] = value;
    refreshTable(type);
}

/**
 * Ensure the dashboard/search table markup (including tbody ids) exists inside the tab.
 * Handles: stale cached script, navigation timing, or loadOwnershipTransferData failing to find containers.
 */
function ensureOwnershipTableMounted(type) {
    const tbodyId = type === 'dashboard' ? 'dashboard-table-body' : 'search-table-body';
    if (document.getElementById(tbodyId)) {
        return true;
    }
    const tabId = type === 'dashboard' ? 'dashboard-tab-content' : 'search-tab-content';
    const tab = document.getElementById(tabId);
    if (!tab) {
        return false;
    }
    const section = tab.querySelector('.ownership-section');
    if (!section) {
        return false;
    }
    const container = section.querySelector('.ownership-table-container');
    if (!container) {
        return false;
    }
    container.innerHTML = type === 'dashboard' ? renderDashboardTable() : renderSearchTable();
    return !!document.getElementById(tbodyId);
}

function refreshTable(type) {
    if (type === 'dashboard') {
        if (!ensureOwnershipTableMounted('dashboard')) {
            updateTransferButton('dashboard');
            return;
        }
        const tbody = document.getElementById('dashboard-table-body');
        const rows = renderDashboardTableRows();
        tbody.innerHTML = rows;
        updateTransferButton('dashboard');
    } else {
        if (!ensureOwnershipTableMounted('search')) {
            updateTransferButton('search');
            return;
        }
        const tbody = document.getElementById('search-table-body');
        const rows = renderSearchTableRows();
        tbody.innerHTML = rows;
        updateTransferButton('search');
    }
}

function toggleDashboardSelection(id) {
    console.log('[OwnershipTransfer] toggleDashboardSelection called with id:', id);
    console.log('[OwnershipTransfer] Current selected dashboards before:', Array.from(ownershipTransferState.selectedDashboards));
    
    if (ownershipTransferState.selectedDashboards.has(id)) {
        ownershipTransferState.selectedDashboards.delete(id);
        console.log('[OwnershipTransfer] Deselected dashboard:', id);
    } else {
        ownershipTransferState.selectedDashboards.add(id);
        console.log('[OwnershipTransfer] Selected dashboard:', id);
    }
    
    console.log('[OwnershipTransfer] Current selected dashboards after:', Array.from(ownershipTransferState.selectedDashboards));
    refreshTable('dashboard');
}

function toggleSearchSelection(id) {
    // Ensure id is a number
    const searchId = typeof id === 'string' ? parseInt(id) : id;
    console.log('[OwnershipTransfer] toggleSearchSelection called with id:', searchId, 'type:', typeof searchId);
    console.log('[OwnershipTransfer] Current selected searches:', Array.from(ownershipTransferState.selectedSearches));
    console.log('[OwnershipTransfer] Search IDs in state:', ownershipTransferState.searches.map(s => ({ id: s.id, type: typeof s.id })));
    
    if (ownershipTransferState.selectedSearches.has(searchId)) {
        ownershipTransferState.selectedSearches.delete(searchId);
        console.log('[OwnershipTransfer] Deselected search:', searchId);
    } else {
        ownershipTransferState.selectedSearches.add(searchId);
        console.log('[OwnershipTransfer] Selected search:', searchId);
    }
    
    console.log('[OwnershipTransfer] Updated selected searches:', Array.from(ownershipTransferState.selectedSearches));
    refreshTable('search');
}

function updateTransferButton(type) {
    const buttonId = type === 'dashboard' ? 'dashboard-transfer-btn' : 'search-transfer-btn';
    const button = document.getElementById(buttonId);
    if (button) {
        const count = type === 'dashboard' ? ownershipTransferState.selectedDashboards.size : ownershipTransferState.selectedSearches.size;
        button.disabled = count === 0;
        console.log(`[OwnershipTransfer] Updated ${type} transfer button: disabled=${count === 0}, count=${count}`);
    } else {
        console.warn(`[OwnershipTransfer] Button not found: ${buttonId}`);
    }
}

function switchOwnershipTab(tab) {
    ownershipTransferState.currentTab = tab;
    
    // Update tab buttons
    document.querySelectorAll('.tab-button').forEach(btn => {
        btn.classList.remove('active');
        if (btn.dataset.tab === tab) {
            btn.classList.add('active');
        }
    });
    
    // Update tab content
    document.querySelectorAll('.tab-content').forEach(content => {
        content.classList.remove('active');
    });
    
    if (tab === 'dashboard') {
        document.getElementById('dashboard-tab-content').classList.add('active');
    } else {
        document.getElementById('search-tab-content').classList.add('active');
    }
}

async function loadOwnershipTransferData() {
    try {
        console.log('[OwnershipTransfer] Loading data...');
        
        // Load dashboards, searches, and users in parallel
        const [dashboardsResponse, searchesResponse, usersResponse] = await Promise.all([
            fetch('/admin/api/ownership-transfer/dashboards'),
            fetch('/admin/api/ownership-transfer/searches'),
            fetch('/admin/api/ownership-transfer/users')
        ]);
        
        const dashboards = await dashboardsResponse.json();
        const searches = await searchesResponse.json();
        const users = await usersResponse.json();
        
        console.log('[OwnershipTransfer] Received dashboards:', dashboards);
        console.log('[OwnershipTransfer] Received searches:', searches);
        console.log('[OwnershipTransfer] Received users:', users);
        
        ownershipTransferState.dashboards = dashboards || [];
        ownershipTransferState.searches = searches || [];
        ownershipTransferState.activeUsers = users || [];
        
        console.log('[OwnershipTransfer] State updated - dashboards:', ownershipTransferState.dashboards.length, 'searches:', ownershipTransferState.searches.length);

        // Mount full table into each tab (creates tbody ids if missing), then sync row markup
        ensureOwnershipTableMounted('dashboard');
        ensureOwnershipTableMounted('search');
        refreshTable('dashboard');
        refreshTable('search');

        // Re-attach row click delegation after any innerHTML replace
        setTimeout(() => setupTableEventDelegation(), 50);
        
    } catch (error) {
        console.error('[OwnershipTransfer] Error loading ownership transfer data:', error);
        const otT = typeof adminT === 'function' ? function (k, f) { return adminT(k, f); } : function (k, f) { return f; };
        showNotification(otT('adminPanel.ownershipTransfer.errorLoadingData', 'Error loading data. Please refresh the page.'), 'error');
    }
}

function openTransferModal(type) {
    const selectedIds = type === 'dashboard' 
        ? Array.from(ownershipTransferState.selectedDashboards)
        : Array.from(ownershipTransferState.selectedSearches);
    
    if (selectedIds.length === 0) {
        showNotification('Please select at least one item to transfer.', 'warning');
        return;
    }
    
    const modal = document.createElement('div');
    modal.className = 'ownership-transfer-modal-overlay';
    modal.innerHTML = `
        <div class="ownership-transfer-modal">
            <div class="modal-header">
                <h3>Transfer ${type === 'dashboard' ? 'Dashboard' : 'Search'} Ownership</h3>
                <button class="modal-close" onclick="closeTransferModal()">
                    <i class="fas fa-times"></i>
                </button>
            </div>
            <div class="modal-body">
                <div class="form-group">
                    <label for="new-owner-select">New Owner</label>
                    <select id="new-owner-select" class="form-control">
                        <option value="">Select a user</option>
                        ${ownershipTransferState.activeUsers.map(user => `
                            <option value="${user.id}">${escapeHtml(user.name)} (${escapeHtml(user.email)})</option>
                        `).join('')}
                    </select>
                </div>
                ${type === 'dashboard' ? `
                    <div class="form-group">
                        <label class="checkbox-label">
                            <input type="checkbox" id="transfer-searches" checked>
                            <span>Transfer the ownership of searches within the selected dashboards</span>
                        </label>
                    </div>
                    <div class="info-message">
                        <i class="fas fa-info-circle"></i>
                        <span>The ownership of shared searches will be transferred only if they are owned by inactive users.</span>
                    </div>
                ` : `
                    <div class="warning-message">
                        <i class="fas fa-exclamation-triangle"></i>
                        <span>The specified shared search results of the selected inactive users will be transferred to the new owner.</span>
                    </div>
                `}
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeTransferModal()">Cancel</button>
                <button class="btn btn-primary" onclick="executeTransfer('${type}')">OK</button>
            </div>
        </div>
    `;
    
    document.body.appendChild(modal);
    setTimeout(() => modal.classList.add('active'), 10);
    
    // Close on overlay click
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeTransferModal();
        }
    });
}

function closeTransferModal() {
    const modal = document.querySelector('.ownership-transfer-modal-overlay');
    if (modal) {
        modal.classList.remove('active');
        setTimeout(() => modal.remove(), 300);
    }
}

async function executeTransfer(type) {
    const newOwnerSelect = document.getElementById('new-owner-select');
    const newOwnerId = newOwnerSelect ? parseInt(newOwnerSelect.value) : null;
    
    if (!newOwnerId) {
        showNotification((typeof adminT === 'function' ? adminT : function (_, f) { return f; })('adminPanel.ownershipTransfer.selectNewOwner', 'Please select a new owner.'), 'warning');
        return;
    }
    
    const selectedIds = type === 'dashboard' 
        ? Array.from(ownershipTransferState.selectedDashboards)
        : Array.from(ownershipTransferState.selectedSearches);
    
    const transferSearches = type === 'dashboard' 
        ? document.getElementById('transfer-searches')?.checked || false
        : false;
    
    try {
        const response = await fetch('/admin/api/ownership-transfer/transfer', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                type: type,
                itemIds: selectedIds,
                newOwnerId: newOwnerId,
                transferSearches: transferSearches
            })
        });
        
        const result = await response.json();
        
        if (result.success) {
            showNotification((typeof adminT === 'function' ? adminT : function (_, f) { return f; })('adminPanel.ownershipTransfer.transferredSuccess', 'Ownership transferred successfully.'), 'success');
            closeTransferModal();
            
            // Clear selections
            if (type === 'dashboard') {
                ownershipTransferState.selectedDashboards.clear();
            } else {
                ownershipTransferState.selectedSearches.clear();
            }
            
            // Reload data
            await loadOwnershipTransferData();
        } else {
            showNotification(result.error || (typeof adminT === 'function' ? adminT : function (_, f) { return f; })('adminPanel.ownershipTransfer.failedTransfer', 'Failed to transfer ownership.'), 'error');
        }
    } catch (error) {
        console.error('Error transferring ownership:', error);
        showNotification((typeof adminT === 'function' ? adminT : function (_, f) { return f; })('adminPanel.ownershipTransfer.errorTransfer', 'Error transferring ownership. Please try again.'), 'error');
    }
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// showNotification provided by admin-notifications.js

function ownershipTransferBackToOperational() {
    const operationalManagement = window.I18n ? window.I18n.t('adminPanel.navigation.operationalManagement') : 'Operational Management';
    if (typeof handleNavigation === 'function') {
        handleNavigation(operationalManagement);
        return;
    }
    const contentArea = document.querySelector('.content-area');
    if (contentArea && typeof showOperationalContent === 'function') {
        if (typeof restoreOriginalHeader === 'function') {
            restoreOriginalHeader();
        }
        showOperationalContent(contentArea);
    }
}

// Make all functions globally accessible for onclick handlers
window.toggleDashboardSelection = toggleDashboardSelection;
window.toggleSearchSelection = toggleSearchSelection;
window.filterOwnershipTable = filterOwnershipTable;
window.switchOwnershipTab = switchOwnershipTab;
window.openTransferModal = openTransferModal;
window.closeTransferModal = closeTransferModal;
window.executeTransfer = executeTransfer;
window.ownershipTransferBackToOperational = ownershipTransferBackToOperational;
