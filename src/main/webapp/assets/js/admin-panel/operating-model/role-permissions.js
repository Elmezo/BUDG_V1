// Role Permissions functionality for Admin Panel

const RP_PREFIX = 'adminPanel.operatingModel.rolePermissions.';
const rpT = typeof adminT === 'function' ? function (key, fallback) { return adminT(RP_PREFIX + key, fallback); } : function (k, f) { return f; };
const rpTpl = typeof adminTpl === 'function' ? function (key, fallback, vars) { return adminTpl(RP_PREFIX + key, fallback, vars); } : function (k, f, v) { return f; };
function rpEscape(s) {
    if (s == null) return '';
    const d = document.createElement('div');
    d.textContent = String(s);
    return d.innerHTML;
}
function rpAttrEscape(s) {
    if (s == null) return '';
    return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}

// showNotification: unified implementation in admin-notifications.js (loaded first)

function showRolePermissionsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.rolePermissions');

    // Mirror Roles & Responsibilities container, but render a grouped table like the provided design
    contentArea.innerHTML = `
        <div class="roles-responsibilities-content role-permissions-like">
            <div class="content-header">
                <div class="header-actions">
                    <button class="btn btn-primary enter-edit-mode-btn" onclick="enterRolePermissionsEditMode()">
                        <i class="fas fa-edit"></i>
                        ${rpEscape(rpT('enterEditMode', 'Enter Edit Mode'))}
                    </button>
                </div>
            </div>

            <div class="content-body">
                <div class="data-table-container">
                    <div class="table-responsive">
                        <table class="data-table" id="rolePermissionsTable">
                            <thead>
                                <tr class="header-labels">
                                    <th>${rpEscape(rpT('module', 'Module'))}</th>
                                    <th>${rpEscape(rpT('role', 'Role'))}</th>
                                    <th>${rpEscape(rpT('permission', 'Permission'))}</th>
                                </tr>
                                <tr class="header-search">
                                    <th>
                                        <div class="search-header-cell">
                                            <select class="table-search-select" data-column="module">
                                                <option value="">${rpEscape(rpT('all', 'All'))}</option>
                                            </select>
                </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <select class="table-search-select" data-column="role">
                                                <option value="">${rpEscape(rpT('all', 'All'))}</option>
                                            </select>
                </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <select class="table-search-select" data-column="permission">
                                                <option value="">${rpEscape(rpT('all', 'All'))}</option>
                                            </select>
                                        </div>
                                    </th>
                                </tr>
                            </thead>
                            <tbody id="rolePermissionsTableBody"></tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    `;
    initRolePermissionsTable();
    loadAndRenderPermissionsTable();

    // The header filters will be populated from the loaded permissions dataset
    // via populatePermissionFilters(rows) inside loadAndRenderPermissionsTable().
}

// Initialize column filtering for Role Permissions table (mirrors Roles & Responsibilities UX)
function initRolePermissionsTable() {
    const table = document.getElementById('rolePermissionsTable');
    if (!table) return;

    const moduleInput = table.querySelector('[data-column="module"]');
    const roleInput = table.querySelector('[data-column="role"]');
    const permSelect = table.querySelector('[data-column="permission"]');

    const apply = () => {
        const m = (moduleInput?.value || '').toString();
        const r = (roleInput?.value || '').toString();
        const p = (permSelect?.value || '').toString();

        const rows = table.querySelectorAll('#rolePermissionsTableBody tr');
        rows.forEach(row => {
            if (row.classList.contains('group-row')) return; // handled after
            const mv = (row.getAttribute('data-module-id') || '').toString();
            const rv = (row.getAttribute('data-role-id') || '').toString();
            const pv = (row.getAttribute('data-permission-id') || '').toString();
            const ok = (!m || mv === m) && (!r || rv === r) && (!p || pv === p);
            row.style.display = ok ? '' : 'none';
        });

        // Show group rows if they have any visible items
        const groupRows = table.querySelectorAll('#rolePermissionsTableBody tr.group-row');
        groupRows.forEach(gr => {
            let visible = false;
            for (let n = gr.nextElementSibling; n && !n.classList.contains('group-row'); n = n.nextElementSibling) {
                if (n.style.display !== 'none') { visible = true; break; }
            }
            gr.style.display = visible ? '' : 'none';
        });
    };

    moduleInput && moduleInput.addEventListener('change', apply);
    roleInput && roleInput.addEventListener('change', apply);
    permSelect && permSelect.addEventListener('change', apply);

    // Collapse/expand behavior for groups
    table.querySelectorAll('#rolePermissionsTableBody tr.group-row').forEach(header => {
        header.style.background = 'var(--surface-2, #f5f7f8)';
        header.style.cursor = 'pointer';
        header.addEventListener('click', () => {
            let collapsed = false;
            const icon = header.querySelector('.toggle-icon i');
            for (let n = header.nextElementSibling; n && !n.classList.contains('group-row'); n = n.nextElementSibling) {
                if (n.style.display === 'none') { collapsed = true; break; }
            }
            for (let n = header.nextElementSibling; n && !n.classList.contains('group-row'); n = n.nextElementSibling) {
                n.style.display = collapsed ? '' : 'none';
            }
            if (icon) icon.className = collapsed ? 'fas fa-minus-square' : 'fas fa-plus-square';
        });
    });

    apply();
}

// Enter edit mode for Role Permissions
function enterRolePermissionsEditMode() {
    try {
        updateSystemTitle(rpT('title', 'Role Permissions').toUpperCase(), rpT('editSubtitle', 'Edit Role Permissions'));

        const contentArea = document.querySelector('.content-area');
        if (!contentArea) return;

        // Render edit table shell (simplified editor similar to Roles & Responsibilities)
        contentArea.innerHTML = `
            <div class="roles-responsibilities-content role-permissions-edit">
                <div class="content-header">
                    <div class="header-actions button-area">
                        <button class="btn btn-close edit-mode-action-btn" onclick="closeRolePermissionsEditMode()">${rpEscape(rpT('close', 'Close'))}</button>
                        <button class="btn btn-save edit-mode-action-btn" onclick="saveRolePermissionsChanges()">${rpEscape(rpT('save', 'Save'))}</button>
                        <button class="btn btn-save edit-mode-action-btn" onclick="saveAndCloseRolePermissions()">${rpEscape(rpT('saveAndClose', 'Save & Close'))}</button>
                    </div>
                </div>
                <div class="content-body">
                    <div class="data-table-container">
                        <div class="table-responsive">
                            <table class="data-table" id="rolePermissionsEditTable">
                                <thead>
                                    <tr class="header-labels">
                                        <th>${rpEscape(rpT('module', 'Module'))}</th>
                                        <th>${rpEscape(rpT('role', 'Role'))}</th>
                                        <th>${rpEscape(rpT('permission', 'Permission'))}</th>
                                        <th>${rpEscape(rpT('action', 'Action'))}</th>
                                    </tr>
                                    <tr class="header-search">
                                        <th>
                                            <div class="search-header-cell">
                                                <select class="table-search-select" data-column="module"><option value="">${rpEscape(rpT('all', 'All'))}</option></select>
                                            </div>
                                        </th>
                                        <th>
                                            <div class="search-header-cell">
                                                <select class="table-search-select" data-column="role"><option value="">${rpEscape(rpT('all', 'All'))}</option></select>
                                            </div>
                                        </th>
                                        <th>
                                            <div class="search-header-cell">
                                                <select class="table-search-select" data-column="permission"><option value="">${rpEscape(rpT('all', 'All'))}</option></select>
                                            </div>
                                        </th>
                                        <th>
                                            <div class="search-header-cell">
                                                <div class="action-placeholder"></div>
                                            </div>
                                        </th>
                                    </tr>
                                </thead>
                                <tbody id="rolePermissionsEditBody"></tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>`;

        // Load same dataset and render editable rows
        loadRolePermissionsForEdit();
    } catch (e) {
        console.error('enterRolePermissionsEditMode error:', e);
    }
}

async function loadRolePermissionsForEdit() {
    const tbody = document.getElementById('rolePermissionsEditBody');
    if (!tbody) return;
    const loading = rpEscape(rpT('loading', window.I18n ? window.I18n.t('adminPanel.common.loading') : 'Loading...'));
    tbody.innerHTML = `<tr><td colspan="4" class="loading-row"><div class="loading-spinner"><i class="fas fa-spinner fa-spin"></i> ${loading}</div></td></tr>`;
    try {
        const resp = await fetch('/api/permissions');
        if (!resp.ok) throw new Error('Failed to load permissions');
        const data = await resp.json();
        const rows = data.data || [];
        // cache for name->id lookups
        window._rp_last_rows = rows;
        populateRolePermissionsEditFilters(rows);

        // Flat rendering (no grouping in edit mode)
        const moduleOptions = (await fetchModuleNamesFromDB()).map(n => `<option value="${rpAttrEscape(n)}">${rpEscape(n)}</option>`).join('');
        const roleOptions = (await fetchObjectRolePrimaryNames()).map(n => `<option value="${rpAttrEscape(n)}">${rpEscape(n)}</option>`).join('');
        const permNames = (await fetchPermissionNamesFromDB()).map(p => (p.name || p.Name || p).toString()).filter(Boolean);
        const permCheckboxes = permNames.map(permName => {
            const isView = permName.toLowerCase() === 'view';
            return `<label class="permission-checkbox-label ${isView ? 'view-permission-label' : ''}">
                <input type="checkbox" class="rp-perm-checkbox" value="${rpAttrEscape(permName)}" ${isView ? 'checked disabled' : ''} />
                <span>${rpEscape(permName)}</span>
            </label>`;
        }).join('');

        const html = rows.map(it => {
            const moduleId = it.moduleId ?? '';
            const roleId = it.objectRoleId ?? '';
            // Support both single permissionId and multiple permissionIds
            const permIds = it.permissionIds || (it.permissionId ? [it.permissionId] : []);
            const permIdsJson = JSON.stringify(permIds);
            const moduleName = (it.module || '').toString();
            const role = (it.role || '').toString();
            // Support both single permission and multiple permissions
            const perms = it.permissions || (it.permission ? [it.permission] : []);
            const permsJson = JSON.stringify(perms);
            return `
                <tr class="data-row" data-module-id="${moduleId}" data-role-id="${roleId}" data-permission-ids='${permIdsJson}' data-row-id="${it.id || ''}">
                    <td>
                        <select class="editable-select rp-module-select" style="width: 100%">
                            <option value="">${rpEscape(rpT('selectModule', 'Select Module'))}</option>
                            ${moduleOptions}
                        </select>
                    </td>
                    <td>
                        <select class="editable-select rp-role-select" style="width: 100%" disabled>
                            <option value="">${rpEscape(rpT('selectModuleFirst', 'Select Module First'))}</option>
                        </select>
                    </td>
                    <td>
                        <div class="permission-checkboxes-container">
                            ${permCheckboxes}
                        </div>
                    </td>
                    <td class="action-cell">
                        <div class="action-buttons">
                            <button class="action-btn add-row-btn" title="${rpAttrEscape(rpT('addNewRowTitle', 'Add New Row'))}" onclick="addRolePermissionRowAfter(this)"><i class="fas fa-plus"></i></button>
                            <button class="action-btn delete-row-btn" title="${rpAttrEscape(rpT('deleteRowTitle', 'Delete Row'))}" onclick="deleteRolePermissionRow(this)"><i class="fas fa-trash"></i></button>
                        </div>
                    </td>
                </tr>`;
        }).join('');

        // If no data, create an empty row for editing
        if (!html || rows.length === 0) {
            const emptyRow = `
                <tr class="data-row" data-is-new="true" data-module-id="" data-role-id="" data-permission-ids="[]">
                    <td>
                        <select class="editable-select rp-module-select" style="width: 100%">
                            <option value="">${rpEscape(rpT('selectModule', 'Select Module'))}</option>
                            ${moduleOptions}
                        </select>
                    </td>
                    <td>
                        <select class="editable-select rp-role-select" style="width: 100%" disabled>
                            <option value="">${rpEscape(rpT('selectModuleFirst', 'Select Module First'))}</option>
                        </select>
                    </td>
                    <td>
                        <div class="permission-checkboxes-container">
                            ${permCheckboxes}
                        </div>
                    </td>
                    <td class="action-cell">
                        <div class="action-buttons">
                            <button class="action-btn add-row-btn" title="${rpAttrEscape(rpT('addNewRowTitle', 'Add New Row'))}" onclick="addRolePermissionRowAfter(this)"><i class="fas fa-plus"></i></button>
                            <button class="action-btn delete-row-btn" title="${rpAttrEscape(rpT('deleteRowTitle', 'Delete Row'))}" onclick="deleteRolePermissionRow(this)"><i class="fas fa-trash"></i></button>
                        </div>
                    </td>
                </tr>`;
            tbody.innerHTML = emptyRow;
        } else {
            tbody.innerHTML = html;
        }
        
        // Set selected values to current and store originals + id
        // Match rows by data-row-id attribute (which was set during HTML generation) to ensure correct mapping
        // This ensures each row is isolated and mapped to its correct database record by ID
        Array.from(tbody.querySelectorAll('tr.data-row')).forEach(async (row) => {
            const rowIdAttr = row.getAttribute('data-row-id');
            if (!rowIdAttr || rowIdAttr === '') {
                // New row without ID - will be handled as create on save
                console.log('Row without ID - will be created on save:', row);
                return;
            }
            
            // Find the matching data row by ID (not by index!) - this ensures isolation
            const r = rows.find(rowData => rowData.id != null && String(rowData.id) === rowIdAttr);
            if (!r) {
                console.warn('No matching data found for row with ID:', rowIdAttr, 'Row will be skipped');
                return;
            }
            
            // Log for debugging - each row should be isolated
            console.log(`Mapping row ID ${rowIdAttr} to data: module=${r.module}, role=${r.role}, permissions=${JSON.stringify(r.permissions || r.permission)}`);
            
            const mSel = row.querySelector('.rp-module-select');
            const rSel = row.querySelector('.rp-role-select');
            const permCheckboxes = row.querySelectorAll('.rp-perm-checkbox');
            if (mSel) mSel.value = r.module || '';
            if (rSel) rSel.value = r.role || '';
            
            // Handle multiple permissions - set checked state for checkboxes
            // This is isolated to THIS row only
            if (permCheckboxes.length > 0) {
                const perms = r.permissions || (r.permission ? [r.permission] : []);
                // Ensure View is always in the permissions list for this row
                const permsWithView = [...perms];
                if (!permsWithView.some(p => p.toLowerCase() === 'view')) {
                    permsWithView.push('View');
                }
                
                permCheckboxes.forEach(checkbox => {
                    const isView = checkbox.value.toLowerCase() === 'view';
                    // View is always checked and disabled
                    if (isView) {
                        checkbox.checked = true;
                        checkbox.disabled = true;
                    } else {
                        // Check if this permission is in the row's permissions
                        checkbox.checked = permsWithView.includes(checkbox.value);
                    }
                });
            }
            
            // Set row attributes - each row is isolated by its unique ID
            row.setAttribute('data-id', r.id != null ? String(r.id) : '');
            row.setAttribute('data-row-id', r.id != null ? String(r.id) : '');
            row.setAttribute('data-original-module', r.module || '');
            row.setAttribute('data-original-role', r.role || '');
            
            // Store original permissions as JSON array (including View which is always present)
            const originalPerms = r.permissions || (r.permission ? [r.permission] : []);
            // Ensure View is always in original permissions
            if (!originalPerms.some(p => p.toLowerCase() === 'view')) {
                originalPerms.push('View');
            }
            row.setAttribute('data-original-permissions', JSON.stringify(originalPerms));
            
            // Update row's permission data attributes to match checkboxes
            updateRowPermissionData(row);
            
            // Filter roles based on selected module if module is already selected
            if (mSel && mSel.value && rSel) {
                await filterRolesBySelectedModule(mSel.value, rSel);
                // Restore the role value after filtering
                if (r.role) {
                    rSel.value = r.role;
                }
            }
        });
        
        // Add event listeners to prevent unchecking View permission
        tbody.querySelectorAll('.rp-perm-checkbox').forEach(checkbox => {
            if (checkbox.value.toLowerCase() === 'view') {
                checkbox.addEventListener('click', function(e) {
                    if (!this.checked) {
                        e.preventDefault();
                        this.checked = true;
                    }
                });
            }
        });
        
        initRolePermissionsEditTable();
    } catch (e) {
        console.error('loadRolePermissionsForEdit error:', e);
        tbody.innerHTML = `<tr><td colspan="4" class="error-row">${rpEscape(rpT('errorLoadingData', 'Error loading data'))}</td></tr>`;
    }
}

// Filters for the edit table populated from dataset
function populateRolePermissionsEditFilters(rows) {
    const table = document.getElementById('rolePermissionsEditTable');
    if (!table) return;
    const moduleSelect = table.querySelector('[data-column="module"]');
    const roleSelect = table.querySelector('[data-column="role"]');
    const permSelect = table.querySelector('[data-column="permission"]');

    const uniqueBy = (arr, key) => {
        const map = new Map();
        arr.forEach(item => { const k = item[key]; if (k == null) return; if (!map.has(k)) map.set(k, item); });
        return Array.from(map.values());
    };

    // Use IDs as option values to filter against row data attributes, while showing primary names
    const modules = uniqueBy(rows, 'moduleId');
    if (moduleSelect) {
        const current = moduleSelect.value;
        moduleSelect.innerHTML = '<option value="">' + rpEscape(rpT('all', 'All')) + '</option>' + modules.map(m => `<option value="${rpAttrEscape(m.moduleId)}">${rpEscape(m.module || rpT('unknown', 'Unknown'))}</option>`).join('');
        if (current) moduleSelect.value = current;
    }
    const roles = uniqueBy(rows, 'objectRoleId');
    if (roleSelect) {
        const current = roleSelect.value;
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('all', 'All')) + '</option>' + roles.map(r => `<option value="${rpAttrEscape(r.objectRoleId)}">${rpEscape(r.role || rpT('unknown', 'Unknown'))}</option>`).join('');
        if (current) roleSelect.value = current;
    }
    const perms = uniqueBy(rows, 'permissionId');
    if (permSelect) {
        const current = permSelect.value;
        permSelect.innerHTML = '<option value="">' + rpEscape(rpT('all', 'All')) + '</option>' + perms.map(p => `<option value="${rpAttrEscape(p.permissionId)}">${rpEscape(p.permission || rpT('unknown', 'Unknown'))}</option>`).join('');
        if (current) permSelect.value = current;
    }
}

// Helper function to get selected permissions from a row's checkboxes
// This ensures each row's data is isolated - returns permission NAMES not IDs
function getRowSelectedPermissions(row) {
    if (!row) return [];
    
    // Get all checkboxes in THIS row only (isolated)
    const allCheckboxes = row.querySelectorAll('.rp-perm-checkbox');
    if (allCheckboxes.length === 0) return ['View']; // Always include View
    
    // Collect selected permissions from THIS row's checkboxes only
    const selectedPerms = Array.from(allCheckboxes)
        .filter(cb => cb.checked || cb.disabled) // Include checked or disabled (View) checkboxes
        .map(cb => cb.value);
    
    // Ensure View is always included
    if (!selectedPerms.some(p => p.toLowerCase() === 'view')) {
        selectedPerms.push('View');
    }
    
    return selectedPerms;
}

// Helper function to update row's permission data attributes (for debugging only)
// The actual save uses getRowSelectedPermissions directly
function updateRowPermissionData(row) {
    if (!row) return;
    
    const selectedPerms = getRowSelectedPermissions(row);
    
    // Convert permission names to IDs (for data attribute only)
    const selectedIds = selectedPerms
        .map(name => getIdForPermissionName(name))
        .filter(id => id != null);
    
    // Update row's data attribute - this is isolated to THIS row
    row.setAttribute('data-permission-ids', JSON.stringify(selectedIds));
    
    // Log for debugging
    const rowId = row.getAttribute('data-row-id') || row.getAttribute('data-id') || 'new';
    console.log(`Updated permissions for row ${rowId}:`, selectedPerms);
}

// Apply filters and group toggle behavior for edit table
function initRolePermissionsEditTable() {
    const table = document.getElementById('rolePermissionsEditTable');
    if (!table) return;
    const moduleInput = table.querySelector('[data-column="module"]');
    const roleInput = table.querySelector('[data-column="role"]');
    const permSelect = table.querySelector('[data-column="permission"]');
    const apply = () => {
        const m = (moduleInput?.value || '').toString();
        const r = (roleInput?.value || '').toString();
        const p = (permSelect?.value || '').toString();
        const rows = table.querySelectorAll('#rolePermissionsEditBody tr');
        rows.forEach(row => {
            const mv = (row.getAttribute('data-module-id') || '').toString();
            const rv = (row.getAttribute('data-role-id') || '').toString();
            const pv = (row.getAttribute('data-permission-id') || '').toString();
            const ok = (!m || mv === m) && (!r || rv === r) && (!p || pv === p);
            row.style.display = ok ? '' : 'none';
        });
    };
    moduleInput && moduleInput.addEventListener('change', apply);
    roleInput && roleInput.addEventListener('change', apply);
    permSelect && permSelect.addEventListener('change', apply);
    // Keep row data attributes in sync with dropdown selections and checkbox changes
    // This function ensures each row is completely isolated
    table.addEventListener('change', (e) => {
        const t = e.target;
        const row = t.closest('tr.data-row'); // Ensure we're working with a data row
        if (!row) return;
        
        // Get the unique row ID to ensure isolation
        const rowId = row.getAttribute('data-row-id') || row.getAttribute('data-id');
        if (!rowId && row.getAttribute('data-is-new') !== 'true') {
            console.warn('Row without ID found in change event:', row);
            return;
        }
        
        if (t.tagName && String(t.tagName).toLowerCase() === 'select') {
            if (t.classList.contains('rp-module-select')) {
                const moduleId = getIdForModuleName(t.value);
                row.setAttribute('data-module-id', moduleId || '');
                // Filter roles based on selected module
                const roleSelect = row.querySelector('.rp-role-select');
                if (roleSelect) {
                    filterRolesBySelectedModule(t.value, roleSelect);
                }
            } else if (t.classList.contains('rp-role-select')) {
                const roleId = getIdForRoleName(t.value);
                row.setAttribute('data-role-id', roleId || '');
            }
        } else if (t.tagName && String(t.tagName).toLowerCase() === 'input' && t.type === 'checkbox' && t.classList.contains('rp-perm-checkbox')) {
            // Prevent unchecking View permission
            if (t.value.toLowerCase() === 'view' && !t.checked) {
                t.checked = true;
                return;
            }
            
            // Update row's permission data - this is isolated to THIS row only
            updateRowPermissionData(row);
        }
        apply();
    });
    apply();
}

// Row actions placeholders
// Add new editable row for role-permissions after the clicked row
async function addRolePermissionRowAfter(button) {
    const currentRow = button.closest('tr');
    if (!currentRow) return;
    const tbody = document.getElementById('rolePermissionsEditBody');
    if (!tbody) return;

    // Ensure options are available; fetch if not cached
    if (!Array.isArray(window.cachedModuleNames) || !window.cachedModuleNames.length) {
        window.cachedModuleNames = await fetchModuleNamesFromDB();
    }
    if (!Array.isArray(window.cachedObjectRolePrimaryNames) || !window.cachedObjectRolePrimaryNames.length) {
        window.cachedObjectRolePrimaryNames = await fetchObjectRolePrimaryNames();
    }
    if (!Array.isArray(window.cachedPermissionNames) || !window.cachedPermissionNames.length) {
        window.cachedPermissionNames = await fetchPermissionNamesFromDB();
    }

    const newRow = document.createElement('tr');
    newRow.className = 'data-row new-row';
    newRow.setAttribute('data-is-new', 'true');
    newRow.setAttribute('data-module-id', '');
    newRow.setAttribute('data-role-id', '');
    newRow.setAttribute('data-permission-ids', '[]');
    const moduleOptions = (window.cachedModuleNames || []).map(n => `<option value="${rpAttrEscape(n)}">${rpEscape(n)}</option>`).join('');
    const roleOptions = (window.cachedObjectRolePrimaryNames || []).map(n => `<option value="${rpAttrEscape(n)}">${rpEscape(n)}</option>`).join('');
    const permNames = (window.cachedPermissionNames || []).map(p => (p.name || p.Name || p).toString()).filter(Boolean);
    const permCheckboxes = permNames.map(permName => {
        const isView = permName.toLowerCase() === 'view';
        return `<label class="permission-checkbox-label ${isView ? 'view-permission-label' : ''}">
            <input type="checkbox" class="rp-perm-checkbox" value="${rpAttrEscape(permName)}" ${isView ? 'checked disabled' : ''} />
            <span>${rpEscape(permName)}</span>
        </label>`;
    }).join('');

    newRow.innerHTML = `
        <td>
            <select class="editable-select rp-module-select" style="width: 100%">
                <option value="">${rpEscape(rpT('selectModule', 'Select Module'))}</option>
                ${moduleOptions}
            </select>
        </td>
        <td>
            <select class="editable-select rp-role-select" style="width: 100%" disabled>
                <option value="">${rpEscape(rpT('selectModuleFirst', 'Select Module First'))}</option>
            </select>
        </td>
        <td>
            <div class="permission-checkboxes-container">
                ${permCheckboxes}
            </div>
        </td>
        <td class="action-cell">
            <div class="action-buttons">
                <button class="action-btn add-row-btn" title="${rpAttrEscape(rpT('addNewRowTitle', 'Add New Row'))}" onclick="addRolePermissionRowAfter(this)"><i class="fas fa-plus"></i></button>
                <button class="action-btn delete-row-btn" title="${rpAttrEscape(rpT('deleteRowTitle', 'Delete Row'))}" onclick="deleteRolePermissionRow(this)"><i class="fas fa-trash"></i></button>
            </div>
        </td>`;
    currentRow.insertAdjacentElement('afterend', newRow);
    
    // Setup role filtering for the new row
    const newModuleSelect = newRow.querySelector('.rp-module-select');
    const newRoleSelect = newRow.querySelector('.rp-role-select');
    
    if (newModuleSelect && newRoleSelect) {
        newModuleSelect.addEventListener('change', function() {
            filterRolesBySelectedModule(this.value, newRoleSelect);
        });
    }
    
    // Add event listener to prevent unchecking View permission in the new row
    const newRowCheckboxes = newRow.querySelectorAll('.rp-perm-checkbox');
    newRowCheckboxes.forEach(checkbox => {
        if (checkbox.value.toLowerCase() === 'view') {
            checkbox.addEventListener('click', function(e) {
                if (!this.checked) {
                    e.preventDefault();
                    this.checked = true;
                }
            });
        }
        // Add change listener to update row data when checkbox changes
        checkbox.addEventListener('change', function() {
            updateRowPermissionData(newRow);
        });
    });
    
    // Initialize permission data for the new row
    updateRowPermissionData(newRow);
}

function deleteRolePermissionRow(button) {
    const row = button.closest('tr');
    if (!row) return;
    
    // Check if this is the first data row - clear data instead of removing
    const tbody = document.getElementById('rolePermissionsEditBody');
    const allRows = tbody.querySelectorAll('tr.data-row');
    if (allRows.length <= 1) {
        // Clear the data in the row instead of removing it
        clearRowData(row);
        return;
    }
    
    const idAttr = row.getAttribute('data-id');
    if (idAttr) {
        window._rp_deleted_ids = window._rp_deleted_ids || [];
        window._rp_deleted_ids.push(parseInt(idAttr));
    }
    row.remove();
}

// Clear all data in a row (for the last remaining row)
function clearRowData(row) {
    // Clear module selection
    const moduleSelect = row.querySelector('.rp-module-select');
    if (moduleSelect) {
        moduleSelect.value = '';
    }
    
    // Clear role selection
    const roleSelect = row.querySelector('.rp-role-select');
    if (roleSelect) {
        roleSelect.value = '';
        // Reset to show all roles
        loadAllRolesIntoSelect(roleSelect);
    }
    
    // Clear permission selection (but keep View checked)
    const permCheckboxes = row.querySelectorAll('.rp-perm-checkbox');
    if (permCheckboxes.length > 0) {
        permCheckboxes.forEach(checkbox => {
            const isView = checkbox.value.toLowerCase() === 'view';
            if (!isView) {
                checkbox.checked = false;
            } else {
                // Ensure View remains checked
                checkbox.checked = true;
                checkbox.disabled = true;
            }
        });
    }
    
    // Clear data attributes
    row.setAttribute('data-module-id', '');
    row.setAttribute('data-role-id', '');
    row.setAttribute('data-permission-id', '');
    row.setAttribute('data-original-module', '');
    row.setAttribute('data-original-role', '');
    row.setAttribute('data-original-permission', '');
    
    // If this row has an ID, mark it for deletion
    const idAttr = row.getAttribute('data-id');
    if (idAttr) {
        window._rp_deleted_ids = window._rp_deleted_ids || [];
        window._rp_deleted_ids.push(parseInt(idAttr));
        // Remove the ID so it won't be updated
        row.removeAttribute('data-id');
    }
    
    // Mark as cleared row (not new) so it gets skipped during save
    row.setAttribute('data-is-cleared', 'true');
    row.removeAttribute('data-is-new');
}

function closeRolePermissionsEditMode() {
    try {
        const contentArea = document.querySelector('.content-area');
        if (!contentArea) return;
        showRolePermissionsContent(contentArea);
        updateSystemTitle(rpT('title', 'Role Permissions'), rpT('description', 'Control permissions for each role in the system.'));
    } catch (e) {
        console.error('closeRolePermissionsEditMode error:', e);
    }
}

async function saveRolePermissionsChanges() {
    try {
        const rows = document.querySelectorAll('#rolePermissionsEditBody tr.data-row');
        const changes = [];
        const diagnostics = { missingIds: [], creates: 0, updatesTried: 0, deletes: (window._rp_deleted_ids||[]).length };
        rows.forEach(row => {
            // Skip cleared rows
            if (row.getAttribute('data-is-cleared') === 'true') {
                console.log('Skipping cleared row');
                return;
            }
            
            const idAttr = row.getAttribute('data-id');
            const rowId = row.getAttribute('data-row-id') || idAttr;
            const module = row.querySelector('.rp-module-select')?.value || '';
            const role = row.querySelector('.rp-role-select')?.value || '';
            
            // Get selected permissions directly from checkboxes in THIS row only (isolated)
            // Use the helper function to ensure consistency
            const selectedPermissions = getRowSelectedPermissions(row);
            
            // Log for debugging - ensure isolation
            console.log(`Reading permissions for row ${rowId}:`, selectedPermissions);
            
            // Skip rows with empty data
            if (!module && !role && selectedPermissions.length === 0) {
                console.log('Skipping empty row');
                return;
            }
            
            const moduleId = getIdForModuleName(module);
            const objectRoleId = getIdForRoleName(role);
            
            // Get permission IDs for all selected permissions (including View)
            const permissionIds = selectedPermissions.map(name => getIdForPermissionName(name)).filter(id => id != null);
            
            if (row.getAttribute('data-is-new') === 'true') {
                // Only create if we have valid data (View is always included)
                if (module && role && selectedPermissions.length > 0) {
                    changes.push({ 
                        type: 'create', 
                        moduleId, 
                        objectRoleId, 
                        permissionIds, 
                        module, 
                        role, 
                        permissions: selectedPermissions 
                    });
                    diagnostics.creates++;
                }
            } else if (idAttr) {
                // Parse original permissions from JSON
                let originalPerms = [];
                try {
                    originalPerms = JSON.parse(row.getAttribute('data-original-permissions') || '[]');
                } catch (e) {
                    originalPerms = [];
                }
                
                const original = {
                    module: row.getAttribute('data-original-module') || '',
                    role: row.getAttribute('data-original-role') || '',
                    permissions: originalPerms
                };
                
                // Check if permissions changed (compare sorted arrays, excluding View from comparison since it's always present)
                const originalPermsSorted = original.permissions.filter(p => p.toLowerCase() !== 'view').sort();
                const selectedPermsSorted = selectedPermissions.filter(p => p.toLowerCase() !== 'view').sort();
                const permsChanged = JSON.stringify(selectedPermsSorted) !== JSON.stringify(originalPermsSorted);
                
                // Check if module or role changed
                const moduleChanged = module !== original.module;
                const roleChanged = role !== original.role;
                
                // Only update if something actually changed (each row is isolated by its ID)
                if (moduleChanged || roleChanged || permsChanged) {
                    // Only update if we have valid data
                    if (module && role && selectedPermissions.length > 0) {
                        // Log for debugging - ensure each row update is isolated
                        console.log(`Updating row ID ${idAttr}: module=${module}, role=${role}, permissions=${JSON.stringify(selectedPermissions)}`);
                        changes.push({ 
                            type: 'update', 
                            id: parseInt(idAttr), // This ID ensures the update only affects this specific row (isolation)
                            moduleId, 
                            objectRoleId, 
                            permissionIds, 
                            module, 
                            role, 
                            permissions: selectedPermissions 
                        });
                        diagnostics.updatesTried++;
                    }
                } else {
                    console.log(`Skipping row ID ${idAttr} - no changes detected`);
                }
            } else {
                // Row without ID and not marked as new - this shouldn't happen, but log it
                console.warn('Row without ID and not marked as new:', row);
            }
        });
        const deleted = window._rp_deleted_ids || [];
        deleted.forEach(id => changes.push({ type: 'delete', id }));
        console.debug('RolePermissions SAVE payload:', { changes, diagnostics });
        
        // Check if there are any changes to save
        if (changes.length === 0) {
            // No changes to save - this is fine, just return success
            // Don't show error message as this is a valid state (e.g., after previous save)
            return true;
        }
        
        // Add current user ID to the payload if available
        const payload = { 
            changes,
            currentUserId: getCurrentUserId() // Add current user ID
        };
        
        const resp = await fetch('/api/permissions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!resp.ok) {
            const errText = await resp.text().catch(()=> '');
            console.error('RolePermissions SAVE HTTP error:', resp.status, resp.statusText, errText);
            throw new Error('Failed to save');
        }
        const result = await resp.json();
        if (!result.success) {
            console.error('RolePermissions SAVE server error:', result);
            throw new Error(result.error || 'Save failed');
        }
        console.info('RolePermissions SAVE OK:', result);
        showNotification(rpT('savedSuccess', 'Role permissions saved successfully!'), 'success');
        // reload edit table to update original values and prevent duplicate saves
        loadRolePermissionsForEdit();
        window._rp_deleted_ids = [];
        return true;
    } catch (e) {
        console.error('RolePermissions SAVE exception:', e);
        const errMsg = e.message || '';
        showNotification(rpTpl('errorSaving', 'Error saving role permissions: ' + errMsg, { message: errMsg }), 'error');
        return false;
    }
}

async function saveAndCloseRolePermissions() {
    // Save changes (or do nothing if no changes)
    const saved = await saveRolePermissionsChanges();
    // Always close, regardless of whether there were changes to save
    // This handles the case where user saved changes, then clicks Save & Close
    closeRolePermissionsEditMode();
}

// Fetch module primary names from DB via existing endpoints (V1 implementation)
async function fetchModuleNamesFromDB() {
    try {
        // Prefer cached names from previous roles fetch
        if (Array.isArray(window.cachedModuleNames) && window.cachedModuleNames.length) {
            return window.cachedModuleNames;
        }

        // Try ObjectRoleServlet default (returns moduleNames)
        const resp1 = await fetch('/api/object-roles');
        if (resp1.ok) {
            const data = await resp1.json();
            const list = data.moduleNames || data.data?.moduleNames;
            if (Array.isArray(list) && list.length) {
                window.cachedModuleNames = list;
                return list;
            }
        }

        // Fallback to ModuleServlet to list modules and map primaryname
        const resp2 = await fetch('/api/modules?action=list');
        if (resp2.ok) {
            const data = await resp2.json();
            const list = (data.data || []).map(m => m.primaryname || m.primaryName || m.name || m.Name).filter(Boolean);
            if (list.length) {
                window.cachedModuleNames = list;
                return list;
            }
        }
    } catch (e) {
        console.warn('fetchModuleNamesFromDB error:', e);
    }
    return [];
}

// Fetch permission names from DB (V1 implementation)
async function fetchPermissionNamesFromDB() {
    try {
        if (Array.isArray(window.cachedPermissionNames) && window.cachedPermissionNames.length) {
            return window.cachedPermissionNames;
        }
        const resp = await fetch('/api/permissions/names');
        if (resp.ok) {
            const data = await resp.json();
            const list = data.data || [];
            if (Array.isArray(list) && list.length) {
                window.cachedPermissionNames = list;
                return list;
            }
        }
    } catch (e) {
        console.warn('fetchPermissionNamesFromDB error:', e);
    }
    return [];
}

// Fetch role primarynames from object_role via ObjectRoleServlet (V1 implementation)
async function fetchObjectRolePrimaryNames() {
    try {
        if (Array.isArray(window.cachedObjectRolePrimaryNames) && window.cachedObjectRolePrimaryNames.length) {
            return window.cachedObjectRolePrimaryNames;
        }

        const resp = await fetch('/api/object-roles');
        if (resp.ok) {
            const data = await resp.json();
            const roles = data.roles || data.data?.roles || [];
            const names = Array.from(new Set(roles.map(r => r.primaryname || r.primaryName).filter(Boolean))).sort((a,b)=>a.localeCompare(b));
            if (names.length) {
                window.cachedObjectRolePrimaryNames = names;
                return names;
            }
        }
    } catch (e) {
        console.warn('fetchObjectRolePrimaryNames error:', e);
    }
    return [];
}

// Filter roles based on selected module (similar to Role Assignment)
async function filterRolesBySelectedModule(moduleName, roleSelect) {
    if (!roleSelect) return;
    
    if (!moduleName) {
        // If no module selected, clear role select and show placeholder
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('selectModuleFirst', 'Select Module First')) + '</option>';
        roleSelect.disabled = true;
        return;
    }
    
    try {
        // Get module ID first
        const moduleId = await getModuleIdByName(moduleName);
        if (!moduleId) {
            console.error('Module not found:', moduleName);
            roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('moduleNotFound', 'Module Not Found')) + '</option>';
            roleSelect.disabled = true;
            return;
        }
        
        // Fetch roles for this specific module from object_role table
        const rolesForModule = await fetchRolesByModuleId(moduleId);
        
        // Save current selection
        const currentValue = roleSelect.value;
        
        // Clear and repopulate select
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('selectRole', 'Select Role')) + '</option>';
        rolesForModule.forEach(role => {
            const option = document.createElement('option');
            option.value = role.primaryname;
            option.textContent = role.primaryname;
            roleSelect.appendChild(option);
        });
        
        // Enable the select
        roleSelect.disabled = false;
        
        // Restore selection if valid for this module
        if (currentValue && rolesForModule.some(r => r.primaryname === currentValue)) {
            roleSelect.value = currentValue;
        } else {
            roleSelect.value = '';
        }
    } catch (e) {
        console.error('Error filtering roles by module:', e);
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('errorLoadingRoles', 'Error Loading Roles')) + '</option>';
        roleSelect.disabled = true;
    }
}

// Get module ID by name
async function getModuleIdByName(moduleName) {
    try {
        const resp = await fetch('/api/modules');
        if (!resp.ok) throw new Error('Failed to fetch modules');
        const data = await resp.json();
        console.log('Modules API response:', data);
        
        // API returns modules in 'modules' field, not 'data'
        const modules = data.modules || data.data || [];
        console.log('Available modules:', modules);
        console.log('Looking for module:', moduleName);
        
        const module = modules.find(m => 
            m.primaryName === moduleName || 
            m.primaryname === moduleName || 
            m.name === moduleName
        );
        
        console.log('Found module:', module);
        return module ? module.id : null;
    } catch (e) {
        console.error('Error getting module ID:', e);
        return null;
    }
}

// Fetch roles by module ID from object_role table
async function fetchRolesByModuleId(moduleId) {
    try {
        const resp = await fetch('/api/object-roles');
        if (!resp.ok) throw new Error('Failed to fetch object roles');
        const data = await resp.json();
        const roles = data.roles || data.data?.roles || [];
        return roles.filter(role => role.module === moduleId || role.moduleId === moduleId);
    } catch (e) {
        console.error('Error fetching roles by module ID:', e);
        return [];
    }
}

// Load all roles into select element
async function loadAllRolesIntoSelect(roleSelect) {
    try {
        const currentValue = roleSelect.value;
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('selectModuleFirst', 'Select Module First')) + '</option>';
        roleSelect.disabled = true;
        // Don't populate with all roles - keep disabled until module is selected
    } catch (e) {
        console.error('Error loading all roles:', e);
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('errorLoadingRoles', 'Error Loading Roles')) + '</option>';
        roleSelect.disabled = true;
    }
}

// Get current user ID (placeholder - should be implemented based on your authentication system)
function getCurrentUserId() {
    // TODO: Implement proper user authentication
    // For now, return a default user ID or get from session/localStorage
    return window.currentUserId || 1; // Default user ID
}

// Helper functions for ID lookups (V1 implementation)
function getIdForModuleName(name) {
    try {
        if (!name) return null;
        // If we have the rows cache from last fetch, map by label
        const lastRows = window._rp_last_rows || [];
        const found = lastRows.find(r => (r.module || '') === name);
        if (found && found.moduleId != null) return found.moduleId;
        // Fallback: no direct mapping, return null so server resolves by name
        return null;
    } catch (_) { return null; }
}

function getIdForRoleName(name) {
    try {
        if (!name) return null;
        const lastRows = window._rp_last_rows || [];
        const found = lastRows.find(r => (r.role || '') === name);
        if (found && found.objectRoleId != null) return found.objectRoleId;
        return null;
    } catch (_) { return null; }
}

function getIdForPermissionName(name) {
    try {
        if (!name) return null;
        const lastRows = window._rp_last_rows || [];
        // First check single permission field
        let found = lastRows.find(r => (r.permission || '') === name);
        if (found && found.permissionId != null) return found.permissionId;
        
        // Also check permissions array
        for (const row of lastRows) {
            if (row.permissions && Array.isArray(row.permissions)) {
                const idx = row.permissions.indexOf(name);
                if (idx >= 0 && row.permissionIds && row.permissionIds[idx] != null) {
                    return row.permissionIds[idx];
                }
            }
        }
        
        // Try to find from cached permission names
        if (window.cachedPermissionNames) {
            const cached = window.cachedPermissionNames.find(p => 
                (p.name || p.Name || p) === name
            );
            if (cached && cached.id != null) return cached.id;
        }
        
        return null;
    } catch (_) { return null; }
}

// Load and render permissions table
async function loadAndRenderPermissionsTable() {
    const tbody = document.getElementById('rolePermissionsTableBody');
    if (!tbody) return;
    
    const loading = rpEscape(rpT('loading', window.I18n ? window.I18n.t('adminPanel.common.loading') : 'Loading...'));
    tbody.innerHTML = `<tr><td colspan="3" class="loading-row"><div class="loading-spinner"><i class="fas fa-spinner fa-spin"></i> ${loading}</div></td></tr>`;
    
    try {
        const resp = await fetch('/api/permissions');
        if (!resp.ok) throw new Error('Failed to load permissions');
        const data = await resp.json();
        const rows = data.data || [];
        
        // Group by module and role for display grouping, but keep each row isolated by its ID
        const grouped = {};
        rows.forEach(row => {
            const module = row.module || rpT('unknown', 'Unknown');
            const role = row.role || rpT('unknown', 'Unknown');
            const key = `${module}|${role}`;
            if (!grouped[key]) {
                grouped[key] = { module, role, permissions: [] };
            }
            grouped[key].permissions.push(row);
        });
        
        // Render grouped table - each row is isolated by its unique ID
        let html = '';
        Object.values(grouped).forEach(group => {
            html += `
                <tr class="group-row" data-module-id="${rpAttrEscape(group.permissions[0]?.moduleId || '')}" data-role-id="${rpAttrEscape(group.permissions[0]?.objectRoleId || '')}">
                    <td colspan="3">
                        <div class="group-header">
                            <i class="fas fa-minus-square toggle-icon"></i>
                            <strong>${rpEscape(group.module)} - ${rpEscape(group.role)}</strong>
                        </div>
                    </td>
                </tr>
            `;
            // Show one row per permission record - each row is isolated by its unique ID
            // This ensures that permissions from different rows are not mixed together
            group.permissions.forEach(perm => {
                const permIds = perm.permissionIds || (perm.permissionId ? [perm.permissionId] : []);
                const perms = perm.permissions || (perm.permission ? [perm.permission] : []);
                // Always include View in display for each isolated row
                const permsSet = new Set(perms);
                permsSet.add('View');
                const permsText = rpEscape(Array.from(permsSet).sort().join(', ') || 'View');
                // Each row has its own unique ID to ensure isolation
                html += `
                    <tr class="data-row" data-module-id="${rpAttrEscape(perm.moduleId || '')}" data-role-id="${rpAttrEscape(perm.objectRoleId || '')}" data-permission-ids='${JSON.stringify(permIds)}' data-row-id="${rpAttrEscape(perm.id || '')}" data-permission-record-id="${rpAttrEscape(perm.id || '')}">
                        <td>${rpEscape(perm.module || '')}</td>
                        <td>${rpEscape(perm.role || '')}</td>
                        <td>${permsText}</td>
                    </tr>
                `;
            });
        });
        
        tbody.innerHTML = html || '<tr><td colspan="3">' + rpEscape(rpT('noData', 'No data')) + '</td></tr>';
        populatePermissionFilters(rows);
    } catch (e) {
        console.error('loadAndRenderPermissionsTable error:', e);
        tbody.innerHTML = `<tr><td colspan="3" class="error-row">${rpEscape(rpT('errorLoadingData', 'Error loading data'))}</td></tr>`;
    }
}

// Populate permission filters
function populatePermissionFilters(rows) {
    const table = document.getElementById('rolePermissionsTable');
    if (!table) return;
    
    const moduleSelect = table.querySelector('[data-column="module"]');
    const roleSelect = table.querySelector('[data-column="role"]');
    const permSelect = table.querySelector('[data-column="permission"]');
    
    const uniqueBy = (arr, key) => {
        const map = new Map();
        arr.forEach(item => { 
            const k = item[key]; 
            if (k == null) return; 
            if (!map.has(k)) map.set(k, item); 
        });
        return Array.from(map.values());
    };
    
    const modules = uniqueBy(rows, 'moduleId');
    if (moduleSelect) {
        const current = moduleSelect.value;
        moduleSelect.innerHTML = '<option value="">' + rpEscape(rpT('all', 'All')) + '</option>' + modules.map(m => `<option value="${rpAttrEscape(m.moduleId)}">${rpEscape(m.module || rpT('unknown', 'Unknown'))}</option>`).join('');
        if (current) moduleSelect.value = current;
    }
    
    const roles = uniqueBy(rows, 'objectRoleId');
    if (roleSelect) {
        const current = roleSelect.value;
        roleSelect.innerHTML = '<option value="">' + rpEscape(rpT('all', 'All')) + '</option>' + roles.map(r => `<option value="${rpAttrEscape(r.objectRoleId)}">${rpEscape(r.role || rpT('unknown', 'Unknown'))}</option>`).join('');
        if (current) roleSelect.value = current;
    }
    
    const perms = uniqueBy(rows, 'permissionId');
    if (permSelect) {
        const current = permSelect.value;
        permSelect.innerHTML = '<option value="">' + rpEscape(rpT('all', 'All')) + '</option>' + perms.map(p => `<option value="${rpAttrEscape(p.permissionId)}">${rpEscape(p.permission || rpT('unknown', 'Unknown'))}</option>`).join('');
        if (current) permSelect.value = current;
    }
}
