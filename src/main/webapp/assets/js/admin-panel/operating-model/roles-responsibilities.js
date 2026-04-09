// Roles & Responsibilities functionality for Admin Panel

const RR_PREFIX = 'adminPanel.operatingModel.rolesResponsibilities.';
const rrT = typeof adminT === 'function' ? function (key, fallback) { return adminT(RR_PREFIX + key, fallback); } : function (k, f) { return f; };
const rrTpl = typeof adminTpl === 'function' ? function (key, fallback, vars) { return adminTpl(RR_PREFIX + key, fallback, vars); } : function (k, f, v) { return f; };
function rrEscape(s) {
    if (s == null) return '';
    const d = document.createElement('div');
    d.textContent = String(s);
    return d.innerHTML;
}
function rrAttrEscape(s) {
    if (s == null) return '';
    return String(s).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
}

function showRolesResponsibilitiesContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.rolesResponsibilities');

    updateSystemTitle(rrT('pageTitle', 'Roles & Responsibilities'), rrT('pageSubtitle', 'Define and manage organizational roles and their responsibilities within the data governance framework.'));

    const yesLbl = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
    const noLbl = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
    contentArea.innerHTML = `
        <div class="roles-responsibilities-content">
            <div class="content-header">
                <div class="header-actions">
                    <button class="btn btn-primary enter-edit-mode-btn" onclick="enterEditMode()">
                        <i class="fas fa-edit"></i>
                        ${rrEscape(rrT('enterEditMode', 'Enter Edit Mode'))}
                    </button>
                </div>
            </div>

            <div class="content-body">
                <div class="data-table-container">
                    <div class="table-responsive">
                        <table class="data-table" id="rolesTable">
                            <thead>
                                <tr class="header-labels">
                                    <th>${rrEscape(rrT('colPrimaryName', 'Primary Name'))}</th>
                                    <th>${rrEscape(rrT('colDescription', 'Description'))}</th>
                                    <th>${rrEscape(rrT('colDefault', 'Default'))}</th>
                                    <th>${rrEscape(rrT('colFacet', 'Facet'))}</th>
                                    <th>${rrEscape(rrT('colRoleType', 'Role Type'))}</th>
                                </tr>
                                <tr class="header-search">
                                    <th>
                                        <div class="search-header-cell">
                                            <input type="text" class="table-search-input" placeholder="${rrAttrEscape(rrT('searchPrimaryNamePlaceholder', 'Search Primary Name...'))}" data-column="primary-name">
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <input type="text" class="table-search-input" placeholder="${rrAttrEscape(rrT('searchDescriptionPlaceholder', 'Search Description...'))}" data-column="description">
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                        <select class="table-search-select" data-column="default">
                                                <option value="">${rrEscape(rrT('all', 'All'))}</option>
                                            <option value="yes">${rrEscape(yesLbl)}</option>
                                            <option value="no">${rrEscape(noLbl)}</option>
                                        </select>
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <input type="text" class="table-search-input" placeholder="${rrAttrEscape(rrT('searchFacetPlaceholder', 'Search Facet...'))}" data-column="facet">
                                        </div>
                                    </th>
                                    <th>
                                        <div class="search-header-cell">
                                            <select class="table-search-select" data-column="role-type">
                                                <option value="">${rrEscape(rrT('allRoleTypes', 'All Role Types'))}</option>
                                        </select>
                                        </div>
                                    </th>
                                </tr>
                            </thead>
                            <tbody id="rolesTableBody">
                                <tr>
                                    <td colspan="5" class="loading-row">
                                        <div class="loading-spinner">
                                            <i class="fas fa-spinner fa-spin"></i>
                                            ${rrEscape(rrT('loadingRolesData', 'Loading roles data...'))}
                                        </div>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    `;

    // جلب البيانات من قاعدة البيانات
    loadRolesData();

    setTimeout(() => {
        const primaryNameInput = document.querySelector('[data-column="primary-name"]');
        const descriptionInput = document.querySelector('[data-column="description"]');
        const defaultSelect = document.querySelector('[data-column="default"]');
        const facetInput = document.querySelector('[data-column="facet"]');
        const roleTypeSelect = document.querySelector('[data-column="role-type"]');

        if (roleTypeSelect) {
        } else {
            const headerSearch = document.querySelector('.header-search');
            if (headerSearch) {
                const roleTypeHeader = headerSearch.querySelector('th:nth-child(5)');
                if (roleTypeHeader) {
                    roleTypeHeader.innerHTML = `
                        <div class="search-header-cell">
                            <select class="table-search-select" data-column="role-type">
                                <option value="">${rrEscape(rrT('allRoleTypes', 'All Role Types'))}</option>
                            </select>
                        </div>
                    `;

                    if (window.cachedRoleTypes) {
                        updateRoleTypeDropdown(window.cachedRoleTypes);
                    }
                }
            }
        }
    }, 500);
}

// Go directly to Roles & Responsibilities edit mode
function goToRolesResponsibilitiesEditMode() {
    console.log('🚀 Going directly to Roles & Responsibilities edit mode...');

    try {
        // تحديث العنوان
        updateSystemTitle(rrT('editModeTitle', 'Roles & Responsibilities - Edit Mode'), rrT('editModeSubtitle', 'Edit and manage organizational roles and their responsibilities.'));

        // الحصول على منطقة المحتوى
        const contentArea = document.querySelector('.content-area');
        if (!contentArea) {
            return;
        }

        highlightSubmenuItem('adminPanel.submenu.rolesResponsibilities');

        showRolesResponsibilitiesContent(contentArea);

        setTimeout(() => {
            enterEditMode();
        }, 100);

    } catch (error) {
        console.error('❌ Error in goToRolesResponsibilitiesEditMode:', error);
    }
}

// Initialize roles table functionality
function initRolesTable() {
    const table = document.getElementById('rolesTable');
    if (!table) return;

    const primaryNameInput = table.querySelector('[data-column="primary-name"]');
    const descriptionInput = table.querySelector('[data-column="description"]');
    const defaultSelect = table.querySelector('[data-column="default"]');
    const facetInput = table.querySelector('[data-column="facet"]');
    const roleTypeSelect = table.querySelector('[data-column="role-type"]');

    const apply = () => {
        const primaryName = (primaryNameInput?.value || '').toString().toLowerCase();
        const description = (descriptionInput?.value || '').toString().toLowerCase();
        const defaultVal = (defaultSelect?.value || '').toString();
        const facet = (facetInput?.value || '').toString().toLowerCase();
        const roleType = (roleTypeSelect?.value || '').toString().toLowerCase();

        const rows = table.querySelectorAll('#rolesTableBody tr');
        rows.forEach(row => {
            if (row.classList.contains('loading-row')) return;
            
            const primaryNameCell = row.cells[0]?.textContent?.toLowerCase() || '';
            const descriptionCell = row.cells[1]?.textContent?.toLowerCase() || '';
            const defaultCell = row.cells[2]?.textContent?.toLowerCase() || '';
            const facetCell = row.cells[3]?.textContent?.toLowerCase() || '';
            const roleTypeCell = row.cells[4]?.textContent?.toLowerCase() || '';

            const matchesPrimaryName = !primaryName || primaryNameCell.includes(primaryName);
            const matchesDescription = !description || descriptionCell.includes(description);
            const matchesDefault = !defaultVal || defaultCell.includes(defaultVal);
            const matchesFacet = !facet || facetCell.includes(facet);
            // For dropdown, use exact match (case-insensitive)
            const matchesRoleType = !roleType || roleTypeCell === roleType;

            const visible = matchesPrimaryName && matchesDescription && matchesDefault && matchesFacet && matchesRoleType;
            row.style.display = visible ? '' : 'none';
        });
    };

    primaryNameInput && primaryNameInput.addEventListener('input', apply);
    descriptionInput && descriptionInput.addEventListener('input', apply);
    defaultSelect && defaultSelect.addEventListener('change', apply);
    facetInput && facetInput.addEventListener('input', apply);
    roleTypeSelect && roleTypeSelect.addEventListener('change', apply);

    apply();
}

// Filter table by column
function filterTableByColumn() {
    const table = document.getElementById('rolesTable');
    if (!table) return;

    const primaryNameInput = table.querySelector('[data-column="primary-name"]');
    const descriptionInput = table.querySelector('[data-column="description"]');
    const defaultSelect = table.querySelector('[data-column="default"]');
    const facetInput = table.querySelector('[data-column="facet"]');
    const roleTypeSelect = table.querySelector('[data-column="role-type"]');

    const apply = () => {
        const primaryName = (primaryNameInput?.value || '').toString().toLowerCase();
        const description = (descriptionInput?.value || '').toString().toLowerCase();
        const defaultVal = (defaultSelect?.value || '').toString();
        const facet = (facetInput?.value || '').toString().toLowerCase();
        const roleType = (roleTypeSelect?.value || '').toString().toLowerCase();

        const rows = table.querySelectorAll('#rolesTableBody tr');
        rows.forEach(row => {
            if (row.classList.contains('loading-row')) return;
            
            const primaryNameCell = row.cells[0]?.textContent?.toLowerCase() || '';
            const descriptionCell = row.cells[1]?.textContent?.toLowerCase() || '';
            const defaultCell = row.cells[2]?.textContent?.toLowerCase() || '';
            const facetCell = row.cells[3]?.textContent?.toLowerCase() || '';
            const roleTypeCell = row.cells[4]?.textContent?.toLowerCase() || '';

            const matchesPrimaryName = !primaryName || primaryNameCell.includes(primaryName);
            const matchesDescription = !description || descriptionCell.includes(description);
            const matchesDefault = !defaultVal || defaultCell.includes(defaultVal);
            const matchesFacet = !facet || facetCell.includes(facet);
            // For dropdown, use exact match (case-insensitive)
            const matchesRoleType = !roleType || roleTypeCell === roleType;

            const visible = matchesPrimaryName && matchesDescription && matchesDefault && matchesFacet && matchesRoleType;
            row.style.display = visible ? '' : 'none';
        });
    };

    primaryNameInput && primaryNameInput.addEventListener('input', apply);
    descriptionInput && descriptionInput.addEventListener('input', apply);
    defaultSelect && defaultSelect.addEventListener('change', apply);
    facetInput && facetInput.addEventListener('input', apply);
    roleTypeSelect && roleTypeSelect.addEventListener('change', apply);

    apply();
}

// Close edit mode
function closeEditMode() {
    try {
        // إعادة العنوان الأصلي
        updateSystemTitle(rrT('pageTitle', 'Roles & Responsibilities'), rrT('pageSubtitle', 'Define and manage organizational roles and their responsibilities within the data governance framework.'));

        // إزالة أزرار التعديل
        removeEditModeButtons();

        // إظهار زر Enter Edit Mode
        showEnterEditModeButton();

        // إعادة تحويل الجدول إلى البيانات الأصلية
        loadRolesData();

        // إزالة عمود Action من header
        removeActionColumnFromHeader();

        // إعادة تهيئة البحث
        setTimeout(() => {
            initRolesTable();
        }, 100);

    } catch (error) {
        console.error('Error in closeEditMode:', error);
    }
}

// Save changes
async function saveChanges() {
    try {
        const table = document.getElementById('rolesTable');
        if (!table) return;

        const rows = table.querySelectorAll('#rolesTableBody tr');
        const changes = [];
        const newRoles = [];
        const deletedIds = window._roles_deleted_ids || [];
        const existingRolesMap = new Map(); // Track existing roles for duplicate check

        // First pass: collect role IDs that are currently visible in the table
        // (these will be captured with their current names in the second pass)
        const deletedIdsSet = new Set((window._roles_deleted_ids || []).map(id => String(id)));
        const tableRoleIds = new Set();
        rows.forEach(row => {
            const roleId = row.getAttribute('data-role-id');
            if (roleId && row.getAttribute('data-is-new') !== 'true') {
                tableRoleIds.add(String(roleId));
            }
        });

        // Second pass: collect existing roles from database for duplicate validation.
        // Skip deleted roles and roles already present in the table (handled in third pass with current names).
        if (window.originalRolesData && Array.isArray(window.originalRolesData)) {
            window.originalRolesData.forEach(role => {
                if (role.primaryName && role.facet &&
                    !deletedIdsSet.has(String(role.id)) &&
                    !tableRoleIds.has(String(role.id))) {
                    const key = `${(role.facet || '').toLowerCase()}:${(role.primaryName || '').toLowerCase()}`;
                    if (!existingRolesMap.has(key)) {
                        existingRolesMap.set(key, { roleId: role.id, primaryName: role.primaryName, facet: role.facet });
                    }
                }
            });
        }

        // Third pass: collect existing roles from current table rows using their current (possibly edited) names
        // Store them temporarily to check for duplicates, but we'll exclude the current role during validation
        rows.forEach(row => {
            if (row.getAttribute('data-is-cleared') === 'true' || !row.querySelector('[data-field]')) {
                return;
            }
            
            const roleId = row.getAttribute('data-role-id');
            const isNew = row.getAttribute('data-is-new') === 'true';
            
            if (!isNew && roleId) {
                const primaryNameCell = row.querySelector('[data-field="primaryName"]');
                const facetCell = row.querySelector('[data-field="facet"]');
                const primaryName = primaryNameCell?.querySelector('.cell-text')?.textContent?.trim() || '';
                const facet = facetCell?.querySelector('.cell-text')?.textContent?.trim() || '';
                
                if (primaryName && facet) {
                    const key = `${facet.toLowerCase()}:${primaryName.toLowerCase()}`;
                    // Store with roleId to allow exclusion during validation
                    existingRolesMap.set(key, { roleId, primaryName, facet });
                }
            }
        });

        // Third pass: validate and collect changes
        let validationError = null;
        
        for (const row of rows) {
            // Skip cleared rows
            if (row.getAttribute('data-is-cleared') === 'true') {
                continue;
            }

            // Skip empty rows (no data)
            if (!row.querySelector('[data-field]')) {
                continue;
            }

            const roleId = row.getAttribute('data-role-id');
            const isNew = row.getAttribute('data-is-new') === 'true';
            
            // Get values from editable cells
            const primaryNameCell = row.querySelector('[data-field="primaryName"]');
            const descriptionCell = row.querySelector('[data-field="description"]');
            const defaultCell = row.querySelector('[data-field="default"]');
            const facetCell = row.querySelector('[data-field="facet"]');
            const roleTypeCell = row.querySelector('[data-field="roleType"]');

            const primaryName = primaryNameCell?.querySelector('.cell-text')?.textContent?.trim() || '';
            const description = descriptionCell?.querySelector('.cell-text')?.textContent?.trim() || '';
            const defaultVal = defaultCell?.getAttribute('data-value') === 'true' ? 'Yes' : 'No';
            const facet = facetCell?.querySelector('.cell-text')?.textContent?.trim() || '';
            const roleType = roleTypeCell?.querySelector('.cell-text')?.textContent?.trim() || '';

            if (isNew) {
                // Validate mandatory fields for new rows
                const missingFields = [];
                if (!primaryName || primaryName === 'New Role') {
                    missingFields.push('Primary Name');
                }
                if (!facet || facet === 'Unknown' || facet === '') {
                    missingFields.push('Facet');
                }
                if (!roleType || roleType === 'Unknown' || roleType === '') {
                    missingFields.push('Role Type');
                }
                
                // If any mandatory field is missing, set error and stop processing
                if (missingFields.length > 0) {
                    validationError = `The ${missingFields[0]} field is mandatory`;
                    break;
                }
                
                // Check for duplicate role name within the same facet
                const key = `${facet.toLowerCase()}:${primaryName.toLowerCase()}`;
                
                // Check against existing roles from database and current table
                if (existingRolesMap.has(key)) {
                    validationError = `A role with the name "${primaryName}" already exists in the "${facet}" facet`;
                    break;
                }
                
                // Check against other new roles in the same batch
                const duplicateInBatch = newRoles.some(nr => 
                    nr.facet.toLowerCase() === facet.toLowerCase() && 
                    nr.primaryName.toLowerCase() === primaryName.toLowerCase()
                );
                if (duplicateInBatch) {
                    validationError = `A role with the name "${primaryName}" already exists in the "${facet}" facet`;
                    break;
                }
                
                // Add to map to prevent duplicates within the same batch
                existingRolesMap.set(key, { roleId: null, primaryName, facet });
                
                // Add new role (description is optional)
                newRoles.push({
                    primaryName,
                    description: description || '', // Description is optional
                    default: defaultVal,
                    facet,
                    roleType
                });
            } else {
                // For existing rows, validate mandatory fields if modified
                const isModified = row.getAttribute('data-modified') === 'true' || 
                                 row.classList.contains('modified-row');
                
                if (isModified && roleId) {
                    // Validate mandatory fields
                    const missingFields = [];
                    if (!primaryName || primaryName === 'New Role') {
                        missingFields.push('Primary Name');
                    }
                    if (!facet || facet === 'Unknown' || facet === '') {
                        missingFields.push('Facet');
                    }
                    if (!roleType || roleType === 'Unknown' || roleType === '') {
                        missingFields.push('Role Type');
                    }
                    
                    // If any mandatory field is missing, set error and stop processing
                    if (missingFields.length > 0) {
                        validationError = `The ${missingFields[0]} field is mandatory`;
                        break;
                    }
                    
                    // Check for duplicate role name within the same facet (excluding current role)
                    const key = `${facet.toLowerCase()}:${primaryName.toLowerCase()}`;
                    const existing = existingRolesMap.get(key);
                    // Only flag as duplicate if an entry exists with a different roleId
                    // (same roleId means it's the current role being edited, which is allowed)
                    if (existing && existing.roleId && String(existing.roleId) !== String(roleId)) {
                        validationError = `A role with the name "${primaryName}" already exists in the "${facet}" facet`;
                        break;
                    }
                    
                    // Also check other rows in the table for duplicates (in case map has multiple entries with same key)
                    for (const otherRow of rows) {
                        if (otherRow === row || otherRow.getAttribute('data-is-cleared') === 'true') continue;
                        const otherRoleId = otherRow.getAttribute('data-role-id');
                        const otherIsNew = otherRow.getAttribute('data-is-new') === 'true';
                        if (!otherIsNew && otherRoleId && String(otherRoleId) !== String(roleId)) {
                            const otherPrimaryNameCell = otherRow.querySelector('[data-field="primaryName"]');
                            const otherFacetCell = otherRow.querySelector('[data-field="facet"]');
                            const otherPrimaryName = otherPrimaryNameCell?.querySelector('.cell-text')?.textContent?.trim() || '';
                            const otherFacet = otherFacetCell?.querySelector('.cell-text')?.textContent?.trim() || '';
                            if (otherPrimaryName.toLowerCase() === primaryName.toLowerCase() && 
                                otherFacet.toLowerCase() === facet.toLowerCase()) {
                                validationError = `A role with the name "${primaryName}" already exists in the "${facet}" facet`;
                                break;
                            }
                        }
                    }
                    if (validationError) break;
                    
                    // Check against new roles in the same batch
                    const duplicateInBatch = newRoles.some(nr => 
                        nr.facet.toLowerCase() === facet.toLowerCase() && 
                        nr.primaryName.toLowerCase() === primaryName.toLowerCase()
                    );
                    if (duplicateInBatch) {
                        validationError = `A role with the name "${primaryName}" already exists in the "${facet}" facet`;
                        break;
                    }
                    
                    changes.push({
                        id: roleId,
                        primaryName,
                        description: description || '', // Description is optional
                        default: defaultVal,
                        facet,
                        roleType
                    });
                }
            }
        }
        
        // If validation failed, show error and stop saving
        if (validationError) {
            showNotification(validationError, 'error');
            return false;
        }

        // Check if there are any changes to save
        if (changes.length === 0 && newRoles.length === 0 && deletedIds.length === 0) {
            // No changes to save - this is fine, just return success
            return true;
        }

        // Debug: Log what we're sending
        console.log('Sending roles data:', {
            changes: changes.length,
            newRoles: newRoles.length,
            deletedIds: deletedIds.length
        });
        console.log('New roles:', newRoles);
        console.log('Changes:', changes);
        console.log('Deleted IDs:', deletedIds);

        // Send changes to server
        const response = await fetch('/api/roles', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ 
                changes, 
                newRoles, 
                deletedIds 
            })
        });

        if (!response.ok) {
            let errorMessage = 'Failed to save changes';
            try {
                const errorData = await response.json();
                errorMessage = errorData.error || errorData.message || errorMessage;
            } catch (parseError) {
                // If JSON parsing fails, try to get text response
                try {
                    const errorText = await response.text();
                    if (errorText) {
                        // Try to extract error from JSON string if it's JSON
                        try {
                            const parsed = JSON.parse(errorText);
                            errorMessage = parsed.error || parsed.message || errorText;
                        } catch {
                            errorMessage = errorText;
                        }
                    }
                } catch (textError) {
                    errorMessage = `HTTP ${response.status}: ${response.statusText}`;
                }
            }
            throw new Error(errorMessage);
        }

        const result = await response.json();
        
        // Show appropriate message based on server response
        if (result.message === 'No updates to save') {
            // No changes to save - this is fine, just return success
            return true;
        } else {
            showNotification(result.message || rrT('savedSuccess', 'Changes saved successfully!'), 'success');
        }
        
        // Clear deleted IDs
        window._roles_deleted_ids = [];
        
        // Reload data to prevent duplicate saves (similar to role-permissions fix)
        await transformTableToObjectRole();
        
        return true;

    } catch (error) {
        console.error('Error saving changes:', error);
        const errMsg = error.message || '';
        showNotification(rrTpl('errorSaving', 'Error saving changes: ' + errMsg, { message: errMsg }), 'error');
        return false;
    }
}

// showNotification: unified implementation in admin-notifications.js (loaded first)

// Save and close
async function saveAndClose() {
    // Save changes (or do nothing if no changes)
    const saved = await saveChanges();
    // Always close, regardless of whether there were changes to save
    // This handles the case where user saved changes, then clicks Save & Close
    closeEditMode();
}

// Update original data
function updateOriginalData() {
    // Update the original data with current values
    const table = document.getElementById('rolesTable');
    if (!table) return;

    const rows = table.querySelectorAll('#rolesTableBody tr[data-role-id]');
    rows.forEach(row => {
        const roleId = row.getAttribute('data-role-id');
        const primaryName = row.cells[0]?.textContent?.trim() || '';
        const description = row.cells[1]?.textContent?.trim() || '';
        const defaultVal = row.cells[2]?.textContent?.trim() || '';
        const facet = row.cells[3]?.textContent?.trim() || '';
        const roleType = row.cells[4]?.textContent?.trim() || '';

        // Update the original data object
        if (window.originalRolesData) {
            const roleIndex = window.originalRolesData.findIndex(r => r.id == roleId);
            if (roleIndex !== -1) {
                window.originalRolesData[roleIndex] = {
                    id: roleId,
                    primaryName,
                    description,
                    default: defaultVal,
                    facet,
                    roleType
                };
            }
        }
    });
}

// Remove edit mode buttons
function removeEditModeButtons() {
    const editModeButtons = document.querySelectorAll('.edit-mode-action-btn');
    editModeButtons.forEach(btn => btn.remove());
}

// Cancel changes
function cancelChanges() {
    // Restore original data
    if (window.originalRolesData) {
        renderRolesTable(window.originalRolesData);
    }
}

// Sort table
function sortTable(column) {
    console.log('Sorting by:', column.textContent);
}

// Enter edit mode - show second table
async function enterEditMode() {
    if (!checkIsSuperAdmin(window._adminUserRole)) {
        showNotification(rrT('noPermissionEditMode', 'You do not have permission to edit Roles & Responsibilities.'), 'error');
        return;
    }

    // تغيير العنوان إلى OBJECT ROLE
    updateSystemTitle('OBJECT ROLE', 'Object Table');

    // إخفاء زر Enter Edit Mode
    hideEnterEditModeButton();

    // تحويل الجدول إلى البيانات من قاعدة البيانات مع عمود Action
    await transformTableToObjectRole();

    // إضافة أزرار Close, Save, Save & Close
    addEditModeButtons();

    // تفعيل البحث في الجدول الثاني
    setTimeout(() => {
        enableSearchForBothTables();

        // التأكد من وجود جميع عناصر البحث في وضع التعديل
        console.log('🔍 Checking search elements in edit mode...');

        const primaryNameInput = document.querySelector('[data-column="primary-name"]');
        const descriptionInput = document.querySelector('[data-column="description"]');
        const defaultSelect = document.querySelector('[data-column="default"]');
        const facetInput = document.querySelector('[data-column="facet"]');
        const roleTypeSelect = document.querySelector('[data-column="role-type"]');

        if (roleTypeSelect) {
        } else {
            const headerSearch = document.querySelector('.header-search');
            if (headerSearch) {
                const roleTypeHeader = headerSearch.querySelector('th:nth-child(5)');
                if (roleTypeHeader) {
                    roleTypeHeader.innerHTML = `
                        <div class="search-header-cell">
                            <select class="table-search-select" data-column="role-type">
                                <option value="">${rrEscape(rrT('allRoleTypes', 'All Role Types'))}</option>
                            </select>
                        </div>
                    `;

                    if (window.cachedRoleTypes) {
                        updateRoleTypeDropdown(window.cachedRoleTypes);
                    }

                    enableSearchForBothTables();
                }
            }
        }
    }, 100);
}

function hideEnterEditModeButton() {
    const enterEditModeBtn = document.querySelector('.enter-edit-mode-btn');
    if (enterEditModeBtn) {
        enterEditModeBtn.style.display = 'none';
    }
}

function showEnterEditModeButton() {
    const enterEditModeBtn = document.querySelector('.enter-edit-mode-btn');
    if (enterEditModeBtn) {
        enterEditModeBtn.style.display = 'flex';
    }
}

function addEditModeButtons() {
    const buttonArea = document.querySelector('.button-area') || document.querySelector('.header-actions');

    if (buttonArea) {
        const existingButtons = buttonArea.querySelectorAll('.edit-mode-action-btn');
        existingButtons.forEach(btn => btn.remove());

        const closeBtn = document.createElement('button');
        closeBtn.className = 'btn btn-close edit-mode-action-btn';
        closeBtn.textContent = rrT('close', 'Close');
        closeBtn.onclick = closeEditMode;

        const saveBtn = document.createElement('button');
        saveBtn.className = 'btn btn-save edit-mode-action-btn';
        saveBtn.textContent = rrT('save', 'Save');
        saveBtn.onclick = saveChanges;

        const saveAndCloseBtn = document.createElement('button');
        saveAndCloseBtn.className = 'btn btn-save edit-mode-action-btn';
        saveAndCloseBtn.textContent = rrT('saveAndClose', 'Save & Close');
        saveAndCloseBtn.onclick = saveAndClose;

        buttonArea.appendChild(closeBtn);
        buttonArea.appendChild(saveBtn);
        buttonArea.appendChild(saveAndCloseBtn);
    }
}

async function loadRolesFromDatabase() {
    // TODO: Replace with actual API call
    return new Promise((resolve) => {
        setTimeout(() => {
            resolve([
                {
                    id: 1,
                    primaryName: 'Data Steward',
                    description: 'The data steward supports the data owner with decision-making for ensuring data quality, compliance and best practices.',
                    default: 'Yes',
                    facet: 'Data Set',
                    roleType: 'Stewardship Role'
                },
                {
                    id: 2,
                    primaryName: 'Data Owner',
                    description: 'The data owner is accountable for the strategic management of data assets including quality, governance, and compliance.',
                    default: 'No',
                    facet: 'Data Set',
                    roleType: 'Ownership Role'
                },
                {
                    id: 3,
                    primaryName: 'Data Analyst',
                    description: 'The data analyst specializes in analyzing data trends, creating reports, and providing insights for business decisions.',
                    default: 'No',
                    facet: 'Data Set',
                    roleType: 'Stewardship Role'
                },
                {
                    id: 4,
                    primaryName: 'System Steward',
                    description: 'The system steward is responsible for the technical maintenance, security, and optimization of data systems.',
                    default: 'No',
                    facet: 'System',
                    roleType: 'Stewardship Role'
                },
                {
                    id: 5,
                    primaryName: 'Business Owner',
                    description: 'The business owner represents business requirements and ensures data meets organizational objectives.',
                    default: 'No',
                    facet: 'Data Set',
                    roleType: 'Ownership Role'
                },
                {
                    id: 6,
                    primaryName: 'System Business Owner',
                    description: 'The system business owner oversees business aspects of system operations and strategic alignment.',
                    default: 'No',
                    facet: 'System',
                    roleType: 'Ownership Role'
                }
            ]);
        }, 500);
    });
}

function renderRolesTable(roles) {
    const tbody = document.getElementById('rolesTableBody');
    if (!tbody) return;
    
    const headerSearchBefore = document.querySelector('.header-search');
    if (headerSearchBefore) {
        const searchElementsBefore = headerSearchBefore.querySelectorAll('input, select');
        searchElementsBefore.forEach((element, index) => {
            const column = element.getAttribute('data-column');
        });
    }

    // حفظ عناصر البحث الحالية قبل إعادة إنشاء الجدول
    const currentSearchElements = {};
    const headerSearch = document.querySelector('.header-search');
    if (headerSearch) {
        const searchInputs = headerSearch.querySelectorAll('input, select');
        searchInputs.forEach((element, index) => {
            const column = element.getAttribute('data-column');
            if (column) {
                currentSearchElements[column] = {
                    value: element.value,
                    type: element.tagName.toLowerCase()
                };
            }
        });
    }

    tbody.innerHTML = roles.map((role) => {
        const facetSlug = (role.facet ? String(role.facet).toLowerCase().replace(/\s+/g, '-') : 'unknown').replace(/[^a-z0-9-]/g, '') || 'unknown';
        const isStewardship = role.roleType && String(role.roleType).toLowerCase().includes('stewardship');
        return `
        <tr data-role-id="${rrAttrEscape(role.id)}">
            <td>
                <div class="name-cell">
                    <div class="name-primary">${rrEscape(role.primaryName)}</div>
                </div>
            </td>
            <td>
                <div class="description-cell">
                    <div class="description-text">${rrEscape(role.description)}</div>
                </div>
            </td>
            <td>
                <span class="badge ${role.default === 'Yes' || role.default === (window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes') ? 'badge-success' : 'badge-secondary'}">${rrEscape(role.default)}</span>
            </td>
            <td>
                <span class="facet-badge facet-${facetSlug}">${rrEscape(role.facet)}</span>
            </td>
            <td>
                <span class="role-type ${isStewardship ? 'stewardship' : 'ownership'}">${rrEscape(role.roleType)}</span>
            </td>

        </tr>
    `;
    }).join('');

    // إزالة عمود Action من header إذا كان موجوداً
    removeActionColumnFromHeader();

    // فحص عناصر البحث بعد إزالة عمود Action
    const headerSearchAfterRemove = document.querySelector('.header-search');
    if (headerSearchAfterRemove) {
        const searchElementsAfterRemove = headerSearchAfterRemove.querySelectorAll('input, select');
        console.log(`🔍 Search elements AFTER removing Action: ${searchElementsAfterRemove.length}`);
        searchElementsAfterRemove.forEach((element, index) => {
            const column = element.getAttribute('data-column');
            console.log(`  Element ${index + 1}: ${element.tagName} [data-column="${column}"]`);
        });
    }
    
    // التأكد من أن عناصر البحث موجودة في header
    setTimeout(() => {
        console.log('🔍 Ensuring search elements exist in header after render...');
        
        // التحقق من وجود عنصر Role Type
        let roleTypeSelect = document.querySelector('[data-column="role-type"]');
        if (!roleTypeSelect) {
            console.log('🔄 Role Type select missing, recreating in header...');
            const headerSearch = document.querySelector('.header-search');
            if (headerSearch) {
                // Find the 5th header cell (Role Type column)
                const roleTypeHeader = headerSearch.querySelector('th:nth-child(5)');
                if (roleTypeHeader) {
                    roleTypeHeader.innerHTML = `
                        <div class="search-header-cell">
                            <select class="table-search-select" data-column="role-type">
                                <option value="">${rrEscape(rrT('allRoleTypes', 'All Role Types'))}</option>
                            </select>
                        </div>
                    `;
                    console.log('✅ Role Type select element recreated in header');
                    
                    // تحديث dropdown من قاعدة البيانات
                    if (window.cachedRoleTypes) {
                        console.log('🔄 Updating Role Type dropdown with cached data in renderRolesTable');
                        updateRoleTypeDropdown(window.cachedRoleTypes);
                    } else {
                        console.warn('⚠️ No cached role types found in renderRolesTable');
                    }
                    
                    // استعادة القيمة السابقة إذا كانت موجودة
                    roleTypeSelect = document.querySelector('[data-column="role-type"]');
                    if (roleTypeSelect && currentSearchElements['role-type']) {
                        roleTypeSelect.value = currentSearchElements['role-type'].value;
                        console.log('✅ Role Type select value restored:', roleTypeSelect.value);
                    }
                    
                    // فحص إضافي للتأكد من أن العنصر لا يزال موجوداً
                    setTimeout(() => {
                        const verifyRoleType = document.querySelector('[data-column="role-type"]');
                        if (verifyRoleType) {
                            console.log('✅ Role Type select verified after recreation');
                        } else {
                            console.error('❌ Role Type select disappeared after recreation!');
                        }
                    }, 10);
                } else {
                    console.error('❌ Role Type header cell not found (th:nth-child(5))');
                    // Debug: show all header cells
                    const allHeaders = headerSearch.querySelectorAll('th');
                    console.log('Available header cells:', allHeaders.length);
                    allHeaders.forEach((th, index) => {
                        console.log(`Header ${index + 1}:`, th.innerHTML);
                    });
                }
            } else {
                console.error('❌ Header search element not found');
            }
        } else {
            console.log('✅ Role Type select found in header after render');
        }
        
        // التحقق من وجود جميع عناصر البحث الأخرى
        const allSearchElements = document.querySelectorAll('.header-search input, .header-search select');
        console.log('Total search elements found after render:', allSearchElements.length);
        
        // استعادة القيم السابقة
        allSearchElements.forEach((element, index) => {
            const column = element.getAttribute('data-column');
            if (column && currentSearchElements[column]) {
                element.value = currentSearchElements[column].value;
                console.log(`✅ Restored value for ${column}:`, element.value);
            }
        });
        
        // إعادة تهيئة البحث بالكامل بعد إعادة الرندر
        initRolesTable();
        
        // تحديث إضافي لـ dropdown إذا كانت البيانات متوفرة
        if (window.cachedRoleTypes) {
            setTimeout(() => {
                updateRoleTypeDropdown(window.cachedRoleTypes);
                console.log('🔄 Additional Role Type dropdown update after render');
            }, 100);
        }
        
    }, 50);
    
    // فحص نهائي للتأكد من أن عناصر البحث لا تزال موجودة
    setTimeout(() => {
        console.log('🔍 FINAL CHECK - Search elements after complete render:');
        const finalHeaderSearch = document.querySelector('.header-search');
        if (finalHeaderSearch) {
            const finalSearchElements = finalHeaderSearch.querySelectorAll('input, select');
            console.log(`  Total elements: ${finalSearchElements.length}`);
            finalSearchElements.forEach((element, index) => {
                const column = element.getAttribute('data-column');
                const visible = element.offsetParent !== null;
                console.log(`  Element ${index + 1}: ${element.tagName} [data-column="${column}"] visible: ${visible}`);
            });
        }
    }, 100);
    
    console.log('✅ Roles table rendered with search functionality enabled');
}

// Load roles data from database (V1 implementation)
async function loadRolesData() {
    const tbody = document.getElementById('rolesTableBody');
    if (!tbody) return;
    
    try {
        // عرض رسالة تحميل
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="loading-row">
                    <div class="loading-spinner">
                        <i class="fas fa-spinner fa-spin"></i>
                        ${rrEscape(rrT('loadingRolesData', 'Loading roles data...'))}
                    </div>
                </td>
            </tr>
        `;
        
        // جلب البيانات من قاعدة البيانات
        const response = await fetch('/api/object-roles');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            console.log('Successfully loaded roles data from database:', {
                rolesCount: data.roles?.length || 0,
                roleTypesCount: data.roleTypes?.length || 0,
                moduleNamesCount: data.moduleNames?.length || 0
            });
            
            // دمج البيانات مع أسماء modules من قاعدة البيانات
            const rolesWithFacets = data.roles.map(role => {
                const roleType = data.roleTypes.find(type => type.id === role.objectroletypeId);
                return {
                    id: role.id,
                    primaryName: role.primaryname,
                    description: role.description || rrT('noDescriptionAvailable', 'No description available'),
                    default: role.defaultrole ? (window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes') : (window.I18n ? window.I18n.t('adminPanel.common.no') : 'No'),
                    facet: role.moduleName || rrT('unknownModule', 'Unknown Module'), // استخدام moduleName من قاعدة البيانات
                    roleType: roleType ? roleType.primaryname : rrT('unknown', 'Unknown')
                };
            });
            
            // Store original data
            window.originalRolesData = rolesWithFacets;
            
            // عرض البيانات في الجدول
            renderRolesTable(rolesWithFacets);
            
            // انتظار قليل لضمان إنشاء عناصر البحث ثم تحديث dropdown
            setTimeout(() => {
                // فحص عناصر البحث قبل تحديث dropdown
                console.log('🔍 Checking search elements before dropdown update:');
                const preUpdateSearch = document.querySelector('.header-search');
                if (preUpdateSearch) {
                    const preUpdateElements = preUpdateSearch.querySelectorAll('input, select');
                    console.log(`  Elements before update: ${preUpdateElements.length}`);
                    preUpdateElements.forEach((element, index) => {
                        const column = element.getAttribute('data-column');
                        console.log(`    Element ${index + 1}: ${element.tagName} [data-column="${column}"]`);
                    });
                }
                
                // تحديث dropdown أنواع الأدوار من قاعدة البيانات
                console.log('🔄 Updating Role Type dropdown with fresh data from database');
                updateRoleTypeDropdown(data.roleTypes);
                
                // فحص عناصر البحث بعد تحديث dropdown
                setTimeout(() => {
                    console.log('🔍 Checking search elements after dropdown update:');
                    const postUpdateSearch = document.querySelector('.header-search');
                    if (postUpdateSearch) {
                        const postUpdateElements = postUpdateSearch.querySelectorAll('input, select');
                        console.log(`  Elements after update: ${postUpdateElements.length}`);
                        postUpdateElements.forEach((element, index) => {
                            const column = element.getAttribute('data-column');
                            console.log(`    Element ${index + 1}: ${element.tagName} [data-column="${column}"]`);
                        });
                    }
                }, 50);
            }, 100);
            
            // التأكد من أن عناصر البحث لا تختفي بعد تحميل البيانات
            setTimeout(() => {
                console.log('🔍 Final verification after data load...');
                
                const allSearchElements = document.querySelectorAll('.header-search input, .header-search select');
                console.log('Final search elements count:', allSearchElements.length);
                
                allSearchElements.forEach((element, index) => {
                    const column = element.getAttribute('data-column');
                    const type = element.tagName.toLowerCase();
                    const value = element.value;
                    const visible = element.offsetParent !== null;
                    console.log(`Final search element ${index + 1}:`, { column, type, value, visible });
                });
                
                // اختبار نهائي لعنصر Role Type
                const roleTypeSelect = document.querySelector('[data-column="role-type"]');
                if (roleTypeSelect) {
                    console.log('✅ Role Type select FINAL check - FOUND:', roleTypeSelect);
                    console.log('Role Type select FINAL visibility:', roleTypeSelect.offsetParent !== null);
                    console.log('Role Type select options:', Array.from(roleTypeSelect.options).map(opt => opt.textContent));
                    console.log('Role Type select value:', roleTypeSelect.value);
                } else {
                    console.error('❌ Role Type select FINAL check - NOT FOUND!');
                    
                    // إعادة إنشاء عنصر Role Type إذا كان مفقوداً
                    console.log('🔄 Recreating missing Role Type select element in final check...');
                    const headerSearch = document.querySelector('.header-search');
                    if (headerSearch) {
                        const roleTypeHeader = headerSearch.querySelector('th:nth-child(5)');
                        if (roleTypeHeader) {
                            roleTypeHeader.innerHTML = `
                                <div class="search-header-cell">
                                    <select class="table-search-select" data-column="role-type">
                                        <option value="">${rrEscape(rrT('allRoleTypes', 'All Role Types'))}</option>
                                    </select>
                                </div>
                            `;
                            console.log('✅ Role Type select element recreated in final check');
                            
                            // تحديث dropdown من قاعدة البيانات
                            updateRoleTypeDropdown(data.roleTypes);
                        } else {
                            console.error('❌ Role Type header cell not found in final check (th:nth-child(5))');
                            // Debug: show all header cells
                            const allHeaders = headerSearch.querySelectorAll('th');
                            console.log('Available header cells in final check:', allHeaders.length);
                            allHeaders.forEach((th, index) => {
                                console.log(`Header ${index + 1}:`, th.innerHTML);
                            });
                        }
                    } else {
                        console.error('❌ Header search element not found in final check');
                    }
                }
                
                // فحص إضافي للتأكد من أن جميع عناصر البحث تعمل
                setTimeout(() => {
                    console.log('🔍 Additional verification of all search elements...');
                    const finalCheck = document.querySelectorAll('.header-search input, .header-search select');
                    console.log('Final verification - Total search elements:', finalCheck.length);
                    
                    finalCheck.forEach((element, index) => {
                        const column = element.getAttribute('data-column');
                        const type = element.tagName.toLowerCase();
                        const value = element.value;
                        console.log(`Final verification element ${index + 1}:`, { column, type, value });
                    });
                }, 100);
                
            }, 200);
            
            console.log('✅ Roles data loaded and displayed successfully');
        } else {
            throw new Error(data.error || 'Failed to load roles data');
        }
    } catch (error) {
        console.error('Error loading roles data:', error);
        
        // عرض رسالة خطأ للمستخدم
        const tbody = document.getElementById('rolesTableBody');
        if (tbody) {
            const errMsg = error.message || '';
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" class="error-row">
                        <div class="error-message">
                            <i class="fas fa-exclamation-triangle"></i>
                            ${rrEscape(rrTpl('errorLoadingRolesData', 'Error loading roles data: ' + errMsg, { message: errMsg }))}
                        </div>
                    </td>
                </tr>
            `;
        }
    }
}

// Get table names
async function getTableNames() {
    try {
        const response = await fetch('/api/tables');
        const data = await response.json();
        return data.data || [];
    } catch (error) {
        console.error('Error fetching table names:', error);
        return [];
    }
}

// Get module names synchronously
function getModuleNamesSync() {
    return window.cachedModuleNames || [];
}

// Transform table to Object Role format with database data and Action column (V1 implementation)
async function transformTableToObjectRole() {
        const tbody = document.getElementById('rolesTableBody');
        if (!tbody) return;

    try {
        // عرض رسالة تحميل
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="loading-row">
                    <div class="loading-spinner">
                        <i class="fas fa-spinner fa-spin"></i>
                        ${rrEscape(rrT('loadingObjectRoleData', 'Loading Object Role data...'))}
                    </div>
                </td>
            </tr>
        `;

        // جلب البيانات من قاعدة البيانات
        const response = await fetch('/api/object-roles');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        
        if (data.success) {
            console.log('Successfully loaded Object Role data from database:', {
                rolesCount: data.roles?.length || 0,
                roleTypesCount: data.roleTypes?.length || 0,
                moduleNamesCount: data.moduleNames?.length || 0,
                moduleNames: data.moduleNames || []
            });
            
            // تخزين البيانات في cache للاستخدام المتزامن
            window.cachedRoleTypes = data.roleTypes;
            window.cachedModuleNames = data.moduleNames;
            
            console.log('💾 Cached data:', {
                roleTypes: window.cachedRoleTypes,
                moduleNames: window.cachedModuleNames
            });
            
            // دمج البيانات مع أسماء modules من قاعدة البيانات
            const rolesWithFacets = data.roles.map(role => {
                const roleType = data.roleTypes.find(type => type.id === role.objectroletypeId);
                return {
                    id: role.id,
                    primaryName: role.primaryname,
                    description: role.description || rrT('noDescriptionAvailable', 'No description available'),
                    default: role.defaultrole === true || role.defaultrole === 1,
                    facet: role.moduleName || rrT('unknownModule', 'Unknown Module'), // استخدام moduleName من قاعدة البيانات
                    roleType: roleType ? roleType.primaryname : rrT('unknown', 'Unknown')
                };
            });
            
            // تحديث الجدول بالبيانات من قاعدة البيانات مع عمود Action
            const yesL = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
            const noL = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
            tbody.innerHTML = rolesWithFacets.map((role, index) => {
                const facetSlug2 = (role.facet ? String(role.facet).toLowerCase().replace(/\s+/g, '-') : 'unknown').replace(/[^a-z0-9-]/g, '') || 'unknown';
                const isStew = role.roleType && String(role.roleType).toLowerCase().includes('stewardship');
                const descTextareaSafe = String(role.description || '').replace(/&/g, '&amp;').replace(/</g, '&lt;');
                const origDataAttr = rrAttrEscape(JSON.stringify(role));
                return `
                <tr data-role-id="${rrAttrEscape(role.id || index + 1)}" data-original-data="${origDataAttr}">
                    <td>
                        <div class="editable-cell" data-field="primaryName">
                            <span class="cell-text">${rrEscape(role.primaryName)}</span>
                            <input type="text" class="editable-input" value="${rrAttrEscape(role.primaryName)}" style="display: none;">
                    </div>
                </td>
                <td>
                        <div class="editable-cell" data-field="description">
                            <span class="cell-text">${rrEscape(role.description)}</span>
                            <textarea class="editable-textarea" style="display: none;">${descTextareaSafe}</textarea>
                    </div>
                </td>
                <td>
                        <div class="editable-cell clickable-default" data-field="default" data-value="${role.default}" onclick="toggleDefaultValue(this)">
                            <span class="cell-text">${role.default ? rrEscape(yesL) : rrEscape(noL)}</span>
                        </div>
                </td>
                <td>
                        <div class="editable-cell" data-field="facet">
                            <span class="cell-text facet-badge facet-${facetSlug2}">${rrEscape(role.facet)}</span>
                        </div>
                </td>
                <td>
                        <div class="editable-cell" data-field="roleType">
                            <span class="cell-text role-type ${isStew ? 'stewardship' : 'ownership'}">${rrEscape(role.roleType)}</span>
                        </div>
                    </td>
                    <td class="action-cell">
                        <div class="action-buttons">
                            <button class="action-btn add-btn" onclick="addNewRowAfter(this)" title="${rrAttrEscape(rrT('addNewRowTitle', 'Add New Row'))}">
                                <i class="fas fa-plus"></i>
                            </button>
                        </div>
                </td>
            </tr>
        `;
            }).join('');

            // إضافة عمود Action إلى header
        addActionColumnToHeader();

            // تفعيل خانات البحث
            enableSearchFunctionality();
            
            // تفعيل التعديل في جميع الخلايا
            enableEditableCells();
            
            console.log('Object Role table transformed with search functionality enabled');
            
        } else {
            throw new Error(data.error || 'Failed to load Object Role data');
        }
    } catch (error) {
        console.error('Error loading Object Role data:', error);
        
        // عرض رسالة خطأ
        const errMsgOr = error.message || '';
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="error-row">
                    <div class="error-message">
                        <i class="fas fa-exclamation-triangle"></i>
                        ${rrEscape(rrTpl('errorLoadingObjectRoleData', 'Error loading Object Role data: ' + errMsgOr, { message: errMsgOr }))}
                    </div>
                </td>
            </tr>
        `;
    }
}

// Toggle default value on click (V1 implementation)
function toggleDefaultValue(cell) {
    const checkbox = cell.querySelector('.role-checkbox');
    const fromDataAttr = cell.getAttribute('data-value') === 'true';
    const currentValue = (checkbox ? checkbox.checked : fromDataAttr) === true;
    const newValue = !currentValue;
    
    // تحديث القيمة في data attribute
    cell.setAttribute('data-value', newValue);
    
    // مزامنة حالة الcheckbox
    if (checkbox) {
        checkbox.checked = newValue;
    }
    
    // تحديث النص المعروض
    const cellText = cell.querySelector('.cell-text');
    if (cellText) {
        const yes = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
        const no = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
        cellText.textContent = newValue ? yes : no;
    }

    // Mark row as modified
    const row = cell.closest('tr');
    if (row) {
        row.classList.add('modified');
        row.setAttribute('data-modified', 'true');
    }

    console.log(`Default value toggled to: ${newValue ? 'Yes' : 'No'}`);
}

// Make cell editable (V1 implementation)
function makeEditable(cell) {
    // تجاهل خلايا Default لأنها تعمل بالنقر المباشر
    if (cell.classList.contains('clickable-default')) {
        return;
    }
    
    const cellText = cell.querySelector('.cell-text');
    const input = cell.querySelector('.editable-input');
    const textarea = cell.querySelector('.editable-textarea');
    const select = cell.querySelector('.editable-select');
    
    if (cellText) {
        cellText.style.display = 'none';
    }
    
    if (input) {
        input.style.display = 'block';
    input.focus();
    input.select();
    
        // إزالة event listeners القديمة لتجنب التكرار
        input.removeEventListener('blur', input._blurHandler);
        input.removeEventListener('keypress', input._keypressHandler);
        
        // إنشاء event handlers جديدة
        input._blurHandler = function() {
        saveCellValue(cell, input.value);
        };
    
        input._keypressHandler = function(e) {
        if (e.key === 'Enter') {
            saveCellValue(cell, input.value);
            }
        };
        
        // إضافة event listeners
        input.addEventListener('blur', input._blurHandler);
        input.addEventListener('keypress', input._keypressHandler);
    }
    
    if (textarea) {
        textarea.style.display = 'block';
        textarea.focus();
        textarea.select();
        
        // إزالة event listeners القديمة لتجنب التكرار
        textarea.removeEventListener('blur', textarea._blurHandler);
        textarea.removeEventListener('keypress', textarea._keypressHandler);
        
        // إنشاء event handlers جديدة
        textarea._blurHandler = function() {
            saveCellValue(cell, textarea.value);
        };
        
        textarea._keypressHandler = function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                saveCellValue(cell, textarea.value);
            }
        };
        
        // إضافة event listeners
        textarea.addEventListener('blur', textarea._blurHandler);
        textarea.addEventListener('keypress', textarea._keypressHandler);
    }
    
    if (select) {
        select.style.display = 'block';
        select.focus();
        
        console.log(`Select element for ${cell.getAttribute('data-field')}:`, {
            options: Array.from(select.options).map(opt => ({ value: opt.value, text: opt.text, selected: opt.selected })),
            currentValue: select.value,
            selectedIndex: select.selectedIndex
        });
        
        // إزالة event listeners القديمة لتجنب التكرار
        if (select._changeHandler) {
            select.removeEventListener('change', select._changeHandler);
        }
        if (select._blurHandler) {
            select.removeEventListener('blur', select._blurHandler);
        }
        
        // إنشاء event handlers جديدة مع منع التكرار
        select._handled = false;
        select._changeHandler = function() {
            if (select._handled) return;
            select._handled = true;
            console.log('Select changed to:', select.value);
            saveCellValue(cell, select.value);
            if (select._blurHandler) {
                select.removeEventListener('blur', select._blurHandler);
                select._blurHandler = null;
            }
        };
        
        select._blurHandler = function() {
            if (select._handled) return;
            select._handled = true;
            console.log('Select blurred with value:', select.value);
            saveCellValue(cell, select.value);
        };
        
        // إضافة event listeners
        select.addEventListener('change', select._changeHandler);
        select.addEventListener('blur', select._blurHandler);
    } else {
        // إنشاء select element ديناميكياً إذا لم يكن موجوداً (للصفوف القديمة فقط)
        const field = cell.getAttribute('data-field');
        if (field === 'facet') {
            const moduleNames = getModuleNamesSync();
            const currentValue = cell.querySelector('.cell-text').textContent;
            const newSelect = createSelectWithEvents(moduleNames, currentValue, field);
            cell.appendChild(newSelect);
            newSelect.style.display = 'block';
            newSelect.focus();
        } else if (field === 'roleType') {
            const roleTypes = getRoleTypesSync();
            const currentValue = cell.querySelector('.cell-text').textContent;
            const newSelect = createSelectWithEvents(roleTypes.map(rt => rt.primaryname), currentValue, field);
            cell.appendChild(newSelect);
            newSelect.style.display = 'block';
            newSelect.focus();
        } else if (field === 'default') {
            // معالجة checkbox - تبديل الحالة
            console.log('🔍 Processing default field...');
            
            // ضمان وجود checkbox ومزامنته مع data-value
            let existingCheckbox = cell.querySelector('.role-checkbox');
            if (!existingCheckbox) {
                existingCheckbox = document.createElement('input');
                existingCheckbox.type = 'checkbox';
                existingCheckbox.className = 'role-checkbox';
                existingCheckbox.style.display = 'none';
                cell.appendChild(existingCheckbox);
            }
            const currentAttr = cell.getAttribute('data-value');
            if (currentAttr !== null) {
                existingCheckbox.checked = currentAttr === 'true';
            }
            
            // البحث عن checkbox
            const checkbox = cell.querySelector('.role-checkbox');
            if (checkbox) {
                console.log('🔍 Checkbox found:', checkbox);
                console.log('🔍 Current checkbox state:', checkbox.checked);
                
                // تبديل حالة checkbox
                checkbox.checked = !checkbox.checked;
                console.log('🔍 New checkbox state:', checkbox.checked);
                
                // تحديث النص المعروض مباشرة
                const cellTextElement = cell.querySelector('.cell-text');
                console.log('🔍 Cell text element found:', cellTextElement);
                if (cellTextElement) {
                    const newText = checkbox.checked ? 'Yes' : 'No';
                    cellTextElement.textContent = newText;
                    // إظهار النص مرة أخرى لأنه تم إخفاؤه في بداية الدالة
                    cellTextElement.style.display = 'block';
                    console.log('🔍 Updated cell text to:', newText);
                    
                    // تحديث فوري للواجهة
                    cellTextElement.style.visibility = 'visible';
                    cellTextElement.style.opacity = '1';
                    
                    // إضافة تأثير بصري للتأكيد
                    cellTextElement.style.transform = 'scale(1.1)';
                    setTimeout(() => {
                        cellTextElement.style.transform = 'scale(1)';
                    }, 200);
                }
                
                // حفظ القيمة
                saveCellValue(cell, checkbox.checked);
                
                console.log(`✅ Default field updated: ${checkbox.checked ? 'Yes' : 'No'}`);
            } else {
                console.error('❌ No checkbox found in default field after ensureDefaultFieldWorks!');
            }
        }
    }
}

// Save cell value and update display (V1 implementation)
function saveCellValue(cell, value) {
    const field = cell.getAttribute('data-field');
    const cellText = cell.querySelector('.cell-text');
    const input = cell.querySelector('.editable-input');
    const textarea = cell.querySelector('.editable-textarea');
    const select = cell.querySelector('.editable-select');
    
    console.log(`Saving cell value: field=${field}, value=${value}`);
    
    // إخفاء عنصر الإدخال
    if (input) input.style.display = 'none';
    if (textarea) textarea.style.display = 'none';
    if (select) {
        select.style.display = 'none';
        // لا نحذف select element في الصفوف الجديدة، فقط نخفيه
        const row = cell.closest('tr');
        if (row && row.getAttribute('data-is-new') !== 'true') {
            // إزالة select element الديناميكي من DOM بعد الحفظ (للصفوف القديمة فقط) مع حراسة
            if (select.parentNode === cell) {
                try {
                    cell.removeChild(select);
                } catch (e) {
                    console.warn('Safe remove of select failed (already removed?):', e);
                }
            }
        }
    }
    
    // إظهار النص
    if (cellText) {
        cellText.style.display = 'block';
        
        // تحديث النص حسب نوع الحقل
        if (field === 'facet') {
            if (value && value.trim() !== '') {
                cellText.textContent = value;
                cellText.className = `cell-text facet-badge facet-${value.toLowerCase().replace(' ', '-')}`;
            } else {
                cellText.textContent = 'Unknown';
                cellText.className = 'cell-text facet-badge facet-unknown';
            }
        } else if (field === 'roleType') {
            if (value && value.trim() !== '') {
                cellText.textContent = value;
                cellText.className = `cell-text role-type ${value.toLowerCase().includes('stewardship') ? 'stewardship' : 'ownership'}`;
            } else {
                cellText.textContent = 'Unknown';
                cellText.className = 'cell-text role-type ownership';
            }
        } else if (field === 'default') {
            const boolValue = value === true || value === 'true' || value === 'Yes';
            const yes = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
            const no = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
            cellText.textContent = boolValue ? yes : no;
            cellText.className = `cell-text ${boolValue ? 'badge-success' : 'badge-secondary'}`;
            // تحديث data-value attribute
            cell.setAttribute('data-value', boolValue);
        } else {
            cellText.textContent = value || '';
        }
    }
    
    // Mark row as modified
    const row = cell.closest('tr');
    if (row) {
        row.classList.add('modified');
        row.setAttribute('data-modified', 'true');
        
        // تحديث data-original-data إذا كان موجوداً
        const originalDataAttr = row.getAttribute('data-original-data');
        if (originalDataAttr) {
            try {
                const originalData = JSON.parse(originalDataAttr);
                originalData[field] = value;
                row.setAttribute('data-original-data', JSON.stringify(originalData));
            } catch (e) {
                console.warn('Failed to update original data:', e);
            }
        }
    }
    
    console.log(`Cell value saved: ${field} = ${value}`);
}

// Activate editable cells in a new row (V1 implementation)
function activateEditableCells(row) {
    const editableCells = row.querySelectorAll('.editable-cell');
    
    editableCells.forEach(cell => {
        const field = cell.getAttribute('data-field');
        
        // إضافة event listeners للخلايا القابلة للتعديل
        cell.addEventListener('click', function() {
            makeEditable(this);
        });
        
        // إضافة تأثيرات بصرية
        cell.style.cursor = 'pointer';
        cell.title = `Click to edit ${field}`;
        
        // معالجة خاصة لحقل facet و roleType للصفوف الجديدة: ضمان وجود select وخياراته
        if (field === 'facet') {
            let select = cell.querySelector('.editable-select');
            const cellText = cell.querySelector('.cell-text');
            const moduleNames = getModuleNamesSync();
            if (!select) {
                const currentValue = cellText ? cellText.textContent : '';
                select = createSelectWithEvents(moduleNames, currentValue, field);
                cell.appendChild(select);
            } else if (select.options.length === 0) {
                moduleNames.forEach(option => {
                    const opt = document.createElement('option');
                    opt.value = option;
                    opt.textContent = option;
                    select.appendChild(opt);
                });
            }
            // Ensure visibility and interactivity
            if (select) {
                select.style.display = 'inline-block';
                select.style.pointerEvents = 'auto';
                select.disabled = false;
                select.tabIndex = 0;
            }
            // Ensure listeners for pre-existing select
            if (select && !select._listenersAttached) {
                select.addEventListener('click', function() {
                    // Facet select clicked
                });
                select.addEventListener('input', function() {
                    saveCellValue(cell, select.value);
                });
                select.addEventListener('change', function() {
                    saveCellValue(cell, select.value);
                });
                select.addEventListener('blur', function() {
                    saveCellValue(cell, select.value);
                });
                select._listenersAttached = true;
            }
        }
        if (field === 'roleType') {
            let select = cell.querySelector('.editable-select');
            const cellText = cell.querySelector('.cell-text');
            const roleTypes = getRoleTypesSync().map(rt => rt.primaryname || rt);
            if (!select) {
                const currentValue = cellText ? cellText.textContent : '';
                select = createSelectWithEvents(roleTypes, currentValue, field);
                cell.appendChild(select);
            } else if (select.options.length === 0) {
                roleTypes.forEach(option => {
                    const opt = document.createElement('option');
                    opt.value = option;
                    opt.textContent = option;
                    select.appendChild(opt);
                });
            }
            // Ensure visibility and interactivity
            if (select) {
                select.style.display = 'inline-block';
                select.style.pointerEvents = 'auto';
                select.disabled = false;
                select.tabIndex = 0;
            }
            // Ensure listeners for pre-existing select
            if (select && !select._listenersAttached) {
                select.addEventListener('click', function() {
                    console.log('RoleType select clicked');
                });
                select.addEventListener('input', function() {
                    console.log('RoleType select input:', select.value);
                    saveCellValue(cell, select.value);
                });
                select.addEventListener('change', function() {
                    console.log('Select changed to:', select.value);
                    saveCellValue(cell, select.value);
                });
                select.addEventListener('blur', function() {
                    console.log('Select blurred with value:', select.value);
                    saveCellValue(cell, select.value);
                });
                select._listenersAttached = true;
            }
        }

        // معالجة خاصة لحقل default
        if (field === 'default') {
            // التأكد من وجود checkbox
            let checkbox = cell.querySelector('.role-checkbox');
            if (!checkbox) {
                console.log('🔄 Creating checkbox in activateEditableCells for default field');
                checkbox = document.createElement('input');
                checkbox.type = 'checkbox';
                checkbox.className = 'role-checkbox';
                checkbox.setAttribute('data-field', 'default');
                checkbox.style.display = 'none';
                const dataValue = cell.getAttribute('data-value');
                checkbox.checked = dataValue === 'true';
                cell.appendChild(checkbox);
            }
            
            // التأكد من أن النص صحيح
            const cellText = cell.querySelector('.cell-text');
            if (cellText) {
                const dataValue = cell.getAttribute('data-value') === 'true';
                const yes = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
                const no = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
                cellText.textContent = dataValue ? yes : no;
            }
            
            // التأكد من أن النص مرئي
            if (cellText) {
                cellText.style.display = 'block';
                cellText.style.visibility = 'visible';
                cellText.style.opacity = '1';
            }
            
            // التأكد من أن checkbox في الحالة الصحيحة للصفوف الجديدة
            const isNewRow = row.getAttribute('data-is-new') === 'true';
            if (isNewRow) {
                const dataValue = cell.getAttribute('data-value') === 'true';
                checkbox.checked = dataValue;
                console.log('✅ New row default field: checkbox synced from data-value =', dataValue);
            }
            
            console.log('✅ Default field initialized in new row');
            
            // اختبار نهائي للحقل
            testDefaultField(cell);
        }
    });
    
    console.log(`Activated ${editableCells.length - 1} editable cells in new row`);
    
    // اختبار إضافي لحقل default في الصفوف الجديدة
    const defaultCell = row.querySelector('[data-field="default"]');
    if (defaultCell && row.getAttribute('data-is-new') === 'true') {
        console.log('🧪 Testing default field in new row...');
        const checkbox = defaultCell.querySelector('.role-checkbox');
        const cellText = defaultCell.querySelector('.cell-text');
        console.log('🧪 Default field state:', {
            checkboxExists: !!checkbox,
            checkboxChecked: checkbox ? checkbox.checked : 'N/A',
            cellTextExists: !!cellText,
            cellTextContent: cellText ? cellText.textContent : 'N/A',
            cellTextDisplay: cellText ? cellText.style.display : 'N/A'
        });
    }
}

// Add new row after button (V1 implementation)
function addNewRowAfter(button) {
    const currentRow = button.closest('tr');
    const tbody = document.getElementById('rolesTableBody');
    if (!tbody) return;

    const newRow = document.createElement('tr');
    newRow.className = 'new-row';
    newRow.setAttribute('data-is-new', 'true');
    const newRoleLbl = rrT('newRole', 'New Role');
    const newDescLbl = rrT('newRoleDescription', 'New role description');
    const noLbl2 = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
    const unknownLbl = rrT('unknown', 'Unknown');
    newRow.innerHTML = `
        <td>
            <div class="editable-cell" data-field="primaryName">
                <span class="cell-text">${rrEscape(newRoleLbl)}</span>
                <input type="text" class="editable-input" value="${rrAttrEscape(newRoleLbl)}" style="display: none;">
            </div>
        </td>
        <td>
            <div class="editable-cell" data-field="description">
                <span class="cell-text">${rrEscape(newDescLbl)}</span>
                <textarea class="editable-textarea" style="display: none;">${String(newDescLbl).replace(/&/g, '&amp;').replace(/</g, '&lt;')}</textarea>
            </div>
        </td>
        <td>
            <div class="editable-cell clickable-default" data-field="default" data-value="false" onclick="toggleDefaultValue(this)">
                <span class="cell-text">${rrEscape(noLbl2)}</span>
            </div>
        </td>
        <td>
            <div class="editable-cell" data-field="facet">
                <span class="cell-text facet-badge facet-unknown">${rrEscape(unknownLbl)}</span>
                <select class="editable-select" style="display: none;">
                    <option value="">${rrEscape(rrT('selectModule', 'Select Module'))}</option>
                </select>
            </div>
        </td>
        <td>
            <div class="editable-cell" data-field="roleType">
                <span class="cell-text role-type ownership">${rrEscape(unknownLbl)}</span>
                <select class="editable-select" style="display: none;">
                    <option value="">${rrEscape(rrT('selectRoleType', 'Select Role Type'))}</option>
                </select>
            </div>
        </td>
        <td class="action-cell">
            <div class="action-buttons">
                <button class="action-btn add-btn" onclick="addNewRowAfter(this)" title="${rrAttrEscape(rrT('addNewRowTitle', 'Add New Row'))}">
                    <i class="fas fa-plus"></i>
                </button>
            </div>
        </td>
    `;

    currentRow.insertAdjacentElement('afterend', newRow);
    
    // تفعيل التعديل في الصف الجديد
    enableEditableCells();
    
    // تحديث dropdowns في الصف الجديد
    const facetSelect = newRow.querySelector('[data-field="facet"] .editable-select');
    const roleTypeSelect = newRow.querySelector('[data-field="roleType"] .editable-select');
    
    if (facetSelect) {
        const moduleNames = getModuleNamesSync();
        moduleNames.forEach(module => {
            const option = document.createElement('option');
            option.value = module;
            option.textContent = module;
            facetSelect.appendChild(option);
        });
    }
    
    if (roleTypeSelect) {
        const roleTypes = getRoleTypesSync();
        roleTypes.forEach(roleType => {
            const option = document.createElement('option');
            option.value = roleType.primaryname || roleType;
            option.textContent = roleType.primaryname || roleType;
            roleTypeSelect.appendChild(option);
        });
    }
    
    console.log('New row added after current row');
}

// Add new row
function addNewRow() {
    const tbody = document.getElementById('rolesTableBody');
    if (!tbody) return;

    const newRow = document.createElement('tr');
    newRow.className = 'new-row';
    newRow.setAttribute('data-is-new', 'true');
    const newRoleLbl2 = rrT('newRole', 'New Role');
    const newDescLbl2 = rrT('newRoleDescription', 'New role description');
    const noLbl3 = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
    const unknownLbl2 = rrT('unknown', 'Unknown');
    const newRoleTypeLbl = rrT('newRoleType', 'New Role Type');
    newRow.innerHTML = `
        <td>
            <div class="name-cell">
                <div class="name-primary">${rrEscape(newRoleLbl2)}</div>
            </div>
        </td>
        <td>
            <div class="description-cell">
                <div class="description-text">${rrEscape(newDescLbl2)}</div>
            </div>
        </td>
        <td>
            <span class="badge badge-secondary">${rrEscape(noLbl3)}</span>
        </td>
        <td>
            <span class="facet-badge facet-unknown">${rrEscape(unknownLbl2)}</span>
        </td>
        <td>
            <span class="role-type stewardship">${rrEscape(newRoleTypeLbl)}</span>
        </td>
        <td class="action-cell">
            <div class="action-buttons">
                <button class="action-btn add-btn" onclick="addNewRowAfter(this)" title="${rrAttrEscape(rrT('addNewRowTitle', 'Add New Row'))}">
                    <i class="fas fa-plus"></i>
                </button>
            </div>
        </td>
    `;

    tbody.appendChild(newRow);
    activateEditableCells(newRow);
}

// Delete role row (V1 implementation)
function deleteRoleRow(button) {
    const row = button.closest('tr');
    if (!row) return;
    
    const tbody = document.getElementById('rolesTableBody');
    if (!tbody) return;
    
    // Check if this is the last remaining data row
    const allDataRows = tbody.querySelectorAll('tr[data-role-id]');
    const isLastRow = allDataRows.length === 1;
    
    if (isLastRow) {
        // If it's the last row, clear the data instead of deleting
        clearRoleRowData(row);
        console.log('Last row cleared instead of deleted');
    } else {
        // If there are other rows, delete this one
        const roleId = row.getAttribute('data-role-id');
        if (roleId && !row.getAttribute('data-is-new')) {
            // Add to deleted IDs for backend processing
            if (!window._roles_deleted_ids) {
                window._roles_deleted_ids = [];
            }
            window._roles_deleted_ids.push(parseInt(roleId));
        }
        row.remove();
        console.log('Row deleted');
    }
}

// Clear role row data (similar to Role Permissions)
function clearRoleRowData(row) {
    // Clear all select values
    const selects = row.querySelectorAll('select');
    selects.forEach(select => {
        select.value = '';
    });
    
    // Clear all input values
    const inputs = row.querySelectorAll('input[type="text"]');
    inputs.forEach(input => {
        input.value = '';
    });
    
    // Clear all textarea values
    const textareas = row.querySelectorAll('textarea');
    textareas.forEach(textarea => {
        textarea.value = '';
    });
    
    // Reset default field
    const defaultCell = row.querySelector('[data-field="default"]');
    if (defaultCell) {
        defaultCell.setAttribute('data-value', 'false');
        const cellText = defaultCell.querySelector('.cell-text');
        if (cellText) {
            const no = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
            cellText.textContent = no;
        }
    }
    
    // Mark row as cleared
    row.setAttribute('data-is-cleared', 'true');
    row.removeAttribute('data-is-new');
    
    // Add to deleted IDs if it has an ID
    const roleId = row.getAttribute('data-role-id');
    if (roleId && !row.getAttribute('data-is-new')) {
        if (!window._roles_deleted_ids) {
            window._roles_deleted_ids = [];
        }
        window._roles_deleted_ids.push(parseInt(roleId));
    }
    
    console.log('Row data cleared');
}

// Delete row (legacy function)
function deleteRow(button) {
    const row = button.closest('tr');
    if (row) {
        row.remove();
    }
}

// Get table names synchronously (V1 implementation)
function getTableNamesSync() {
    // قائمة بأسماء الجداول المعروفة من قاعدة البيانات
    return [
        'people', 'org_unit', 'role', 'status', 'employment_type', 
        'files', 'follow', 'module', 'module_group', 'object_role',
        'object_role_type', 'permissions', 'people_details', 
        'people_lifecycle_status', 'people_source', 'role_assignment',
        'role_permission', 'role_type', 'revisions'
    ];
}

// Get role types synchronously (V1 implementation)
function getRoleTypesSync() {
    // هذه البيانات ستأتي من cache أو من متغير global
    return window.cachedRoleTypes || [
        { primaryname: 'Stewardship Role' },
        { primaryname: 'Ownership Role' },
        { primaryname: 'User' }
    ];
}

// Enable search functionality (V1 implementation)
function enableSearchFunctionality() {
    const table = document.getElementById('rolesTable');
    if (!table) return;

    // إزالة event listeners القديمة
    const searchInputs = table.querySelectorAll('.table-search-input, .table-search-select');
    searchInputs.forEach(input => {
        input.removeEventListener('input', input._inputHandler);
        input.removeEventListener('change', input._changeHandler);
        
        // إنشاء event handlers جديدة
        input._inputHandler = function() {
            filterEditableTable();
        };
        
        input._changeHandler = function() {
            filterEditableTable();
        };
        
        // إضافة event listeners
        input.addEventListener('input', input._inputHandler);
        input.addEventListener('change', input._changeHandler);
    });
    
    console.log('Search functionality enabled for', searchInputs.length, 'elements');
}

// Enable editable cells (V1 implementation)
function enableEditableCells() {
    const table = document.getElementById('rolesTable');
    if (!table) return;

    const rows = table.querySelectorAll('#rolesTableBody tr');
    rows.forEach(row => {
        // إزالة event listeners القديمة لتجنب التكرار
        const cells = row.querySelectorAll('.editable-cell');
        cells.forEach(cell => {
            cell.removeEventListener('click', cell._clickHandler);
            
            // إنشاء event handler جديد
            cell._clickHandler = function() {
                makeEditable(cell);
            };
            
            // إضافة event listener
            cell.addEventListener('click', cell._clickHandler);
        });
    });
    
    console.log('Editable cells enabled for', rows.length, 'rows');
}

// Filter editable table (V1 implementation)
function filterEditableTable() {
    const rows = document.querySelectorAll('#rolesTableBody tr');
    
    // Get search values from header search inputs
    const primaryNameFilter = document.querySelector('[data-column="primary-name"]')?.value.toLowerCase() || '';
    const descriptionFilter = document.querySelector('[data-column="description"]')?.value.toLowerCase() || '';
    const defaultFilter = document.querySelector('[data-column="default"]')?.value.toLowerCase() || '';
    const facetFilter = document.querySelector('[data-column="facet"]')?.value.toLowerCase() || '';
    const roleTypeFilter = document.querySelector('[data-column="role-type"]')?.value.toLowerCase() || '';
    
        rows.forEach(row => {
        let showRow = true;
        
        // Check Primary Name
        const primaryNameCell = row.querySelector('[data-field="primaryName"]');
        if (primaryNameCell && primaryNameFilter) {
            const primaryName = primaryNameCell.querySelector('.cell-text')?.textContent.toLowerCase() || '';
            if (!primaryName.includes(primaryNameFilter)) {
                showRow = false;
            }
        }
        
        // Check Description
        const descriptionCell = row.querySelector('[data-field="description"]');
        if (descriptionCell && descriptionFilter) {
            const description = descriptionCell.querySelector('.cell-text')?.textContent.toLowerCase() || '';
            if (!description.includes(descriptionFilter)) {
                showRow = false;
            }
        }
        
        // Check Default
        const defaultCell = row.querySelector('[data-field="default"]');
        if (defaultCell && defaultFilter) {
            const isDefault = (defaultCell.querySelector('.role-checkbox')?.checked ?? (defaultCell.getAttribute('data-value') === 'true')) || false;
            const filterValue = defaultFilter.toLowerCase();
            if (filterValue === 'yes' && !isDefault) {
                showRow = false;
            } else if (filterValue === 'no' && isDefault) {
                showRow = false;
            }
        }
        
        // Check Facet
        const facetCell = row.querySelector('[data-field="facet"]');
        if (facetCell && facetFilter) {
            const facet = facetCell.querySelector('.cell-text')?.textContent.toLowerCase() || '';
            if (!facet.includes(facetFilter)) {
                showRow = false;
            }
        }
        
        // Check Role Type
        const roleTypeCell = row.querySelector('[data-field="roleType"]');
        if (roleTypeCell && roleTypeFilter) {
            const roleType = roleTypeCell.querySelector('.cell-text')?.textContent.toLowerCase() || '';
            if (roleTypeFilter.toLowerCase() !== roleType) {
                showRow = false;
            }
        }
        
        row.style.display = showRow ? '' : 'none';
    });
    
    console.log('Editable table filtered with:', {
        primaryName: primaryNameFilter,
        description: descriptionFilter,
        default: defaultFilter,
        facet: facetFilter,
        roleType: roleTypeFilter
    });
}

// Add Action column to table header (V1 implementation)
function addActionColumnToHeader() {
    const headerLabels = document.querySelector('.header-labels');
    const headerSearch = document.querySelector('.header-search');
    
    if (headerLabels && headerSearch) {
        // إضافة عمود Action إلى header labels
        const actionHeader = document.createElement('th');
        actionHeader.textContent = 'Action';
        actionHeader.style.width = '10%';
        headerLabels.appendChild(actionHeader);
        
        // إضافة عمود Action إلى header search
        const actionSearchHeader = document.createElement('th');
        actionSearchHeader.style.width = '10%';
        headerSearch.appendChild(actionSearchHeader);
    }
}

// Remove Action column from table header (V1 implementation)
function removeActionColumnFromHeader() {
    console.log('🔍 Attempting to remove Action column from header...');
    const headerLabels = document.querySelector('.header-labels');
    const headerSearch = document.querySelector('.header-search');
    
    if (headerLabels && headerSearch) {
        // إزالة عمود Action من header labels
        const lastHeader = headerLabels.lastElementChild;
        if (lastHeader && lastHeader.textContent === 'Action') {
            lastHeader.remove();
            console.log('✅ Action column removed from header labels');
        } else {
            console.log('ℹ️ No Action column found in header labels (this is normal for main view)');
        }
        
        // إزالة عمود Action من header search فقط إذا كان موجوداً
        const lastSearchCell = headerSearch.lastElementChild;
        if (lastSearchCell) {
            // Check if this is actually an Action column by looking for Action-related content
            const hasActionContent = lastSearchCell.textContent.includes('Action') || 
                                   lastSearchCell.querySelector('.action-btn') ||
                                   lastSearchCell.querySelector('[onclick*="addNewRowAfter"]');
            
            if (hasActionContent) {
            lastSearchCell.remove();
                console.log('✅ Action column removed from header search');
            } else {
                console.log('ℹ️ Last column is not an Action column, keeping it (this is normal for main view)');
        }
        }
    } else {
        console.warn('⚠️ Header elements not found for Action column removal');
    }
}

// Enable search for both tables
function enableSearchForBothTables() {
    const table = document.getElementById('rolesTable');
    if (!table) return;

    const primaryNameInput = table.querySelector('[data-column="primary-name"]');
    const descriptionInput = table.querySelector('[data-column="description"]');
    const defaultSelect = table.querySelector('[data-column="default"]');
    const facetInput = table.querySelector('[data-column="facet"]');
    const roleTypeSelect = table.querySelector('[data-column="role-type"]');

    const apply = () => {
        const primaryName = (primaryNameInput?.value || '').toString().toLowerCase();
        const description = (descriptionInput?.value || '').toString().toLowerCase();
        const defaultVal = (defaultSelect?.value || '').toString();
        const facet = (facetInput?.value || '').toString().toLowerCase();
        const roleType = (roleTypeSelect?.value || '').toString();

        const rows = table.querySelectorAll('#rolesTableBody tr');
        rows.forEach(row => {
            if (row.classList.contains('loading-row')) return;
            
            const primaryNameCell = row.cells[0]?.textContent?.toLowerCase() || '';
            const descriptionCell = row.cells[1]?.textContent?.toLowerCase() || '';
            const defaultCell = row.cells[2]?.textContent?.toLowerCase() || '';
            const facetCell = row.cells[3]?.textContent?.toLowerCase() || '';
            const roleTypeCell = row.cells[4]?.textContent?.toLowerCase() || '';

            const matchesPrimaryName = !primaryName || primaryNameCell.includes(primaryName);
            const matchesDescription = !description || descriptionCell.includes(description);
            const matchesDefault = !defaultVal || defaultCell.includes(defaultVal);
            const matchesFacet = !facet || facetCell.includes(facet);
            const matchesRoleType = !roleType || roleTypeCell.includes(roleType);

            const visible = matchesPrimaryName && matchesDescription && matchesDefault && matchesFacet && matchesRoleType;
            row.style.display = visible ? '' : 'none';
        });
    };

    primaryNameInput && primaryNameInput.addEventListener('input', apply);
    descriptionInput && descriptionInput.addEventListener('input', apply);
    defaultSelect && defaultSelect.addEventListener('change', apply);
    facetInput && facetInput.addEventListener('input', apply);
    roleTypeSelect && roleTypeSelect.addEventListener('change', apply);

    apply();
}

// Update role type dropdown (V1 implementation)
function updateRoleTypeDropdown(roleTypes) {
    const roleTypeSelect = document.querySelector('[data-column="role-type"]');
    if (!roleTypeSelect) {
        console.warn('Role Type select not found for dropdown update');
        return;
    }

    const currentValue = roleTypeSelect.value;
    roleTypeSelect.innerHTML = '<option value="">' + rrEscape(rrT('allRoleTypes', 'All Role Types')) + '</option>';
    
    if (roleTypes && Array.isArray(roleTypes)) {
    roleTypes.forEach(roleType => {
        const option = document.createElement('option');
            // Handle both string and object formats
            if (typeof roleType === 'string') {
        option.value = roleType;
        option.textContent = roleType;
            } else if (roleType && roleType.primaryname) {
                option.value = roleType.primaryname;
                option.textContent = roleType.primaryname;
            } else if (roleType && roleType.name) {
                option.value = roleType.name;
                option.textContent = roleType.name;
            }
        roleTypeSelect.appendChild(option);
    });
    }

    if (currentValue) {
        roleTypeSelect.value = currentValue;
    }
    
    console.log('Role Type dropdown updated with', roleTypes?.length || 0, 'options');
}

// Create select with events (V1 implementation)
function createSelectWithEvents(options, selectedValue, field) {
    const select = document.createElement('select');
    select.className = 'editable-select';
    select.style.display = 'none';
    
    // إضافة الخيارات
    options.forEach(option => {
        const optionElement = document.createElement('option');
        optionElement.value = option;
        optionElement.textContent = option;
        if (option === selectedValue) {
            optionElement.selected = true;
        }
        select.appendChild(optionElement);
    });
    
    // إضافة event listeners مباشرة
    select.addEventListener('change', function() {
        console.log(`Select change event for ${field}:`, this.value);
        const cell = this.closest('.editable-cell');
        if (cell) {
            saveCellValue(cell, this.value);
        }
    });
    
    select.addEventListener('blur', function() {
        console.log(`Select blur event for ${field}:`, this.value);
        const cell = this.closest('.editable-cell');
        if (cell) {
            saveCellValue(cell, this.value);
        }
    });
    
    return select;
}

// Test default field (V1 implementation)
function testDefaultField(cell) {
    try {
        if (!cell) return;
        const checkbox = cell.querySelector('.role-checkbox');
        const cellText = cell.querySelector('.cell-text');
        const dataAttr = cell.getAttribute('data-value');
        const dataValue = dataAttr === 'true';
        const checkboxChecked = checkbox ? !!checkbox.checked : null;
        const textContent = cellText ? cellText.textContent : null;
        console.log('🧪 testDefaultField:', { dataValue, checkboxChecked, textContent });
        // Ensure checkbox matches data-value if both exist
        if (checkbox && checkboxChecked !== dataValue) {
            checkbox.checked = dataValue;
        }
        // Ensure text reflects the boolean value
        if (cellText) {
            const yes = window.I18n ? window.I18n.t('adminPanel.common.yes') : 'Yes';
            const no = window.I18n ? window.I18n.t('adminPanel.common.no') : 'No';
            cellText.textContent = (checkbox ? checkbox.checked : dataValue) ? yes : no;
            cellText.style.display = 'block';
            cellText.style.visibility = 'visible';
            cellText.style.opacity = '1';
        }
    } catch (e) {
        console.warn('testDefaultField error:', e);
    }
}
