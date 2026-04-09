/**
 * Unified Bulk Selection Management for Unison Search
 * Handles row selection via CTRL+click and selection state management
 * Supports: Bulk Delete, Bulk Update, Bulk Migrate (Selected & All)
 */

(function() {
    'use strict';

    // Selection state (shared across all bulk operations)
    let selectedRows = [];
    let currentFacet = null;
    const STORAGE_KEY = 'bulkSelectionState';
    let dataMigrationEnabled = false; // Feature flag for Data Migration
    let cachedUserRole = null; // Cached user role to avoid repeated API calls
    
    // Observer references for disconnect/reconnect during updates
    let categoryObservers = [];
    
    // Flag to prevent infinite loop during category activation
    let isProcessingCategoryChange = false;
    let lastProcessedCategory = null;

    // Excluded facets per operation
    const EXCLUDED_FACETS = {
        delete: ['role', 'physical-field', 'physicalfield', 'change-request', 'changerequest', 
                 'active-task', 'activetask'],
        update: ['change-request', 'changerequest', 'active-task', 'activetask', 
                 'geography', 'regulator', 'regulatory', 'regulatory-theme', 
                 'regulatorytheme', 'physical-field', 'physicalfield'],
        migrate: ['role', 'change-request', 'changerequest', 'change-requests', 'active-task', 'activetask', 'active-tasks']
    };

    /**
     * Debounce function to limit how often a function can be called
     * @param {Function} func - Function to debounce
     * @param {number} wait - Wait time in milliseconds
     * @returns {Function} Debounced function
     */
    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    /**
     * Initialize selection functionality for the search table
     */
    function initBulkSelection() {
        const tableContainer = document.querySelector('.data-table-wrapper');
        if (!tableContainer) {
            console.warn('[BulkSelection] Table container not found');
            // Retry after delay
            setTimeout(initBulkSelection, 1000);
            return;
        }

        // Load persisted selection from sessionStorage
        loadSelectionFromStorage();

        // Add click handler for row selection
        tableContainer.addEventListener('click', handleRowClick);

        // Add checkbox change handlers
        tableContainer.addEventListener('change', handleCheckboxChange);

        // Add hover effect for rows
        tableContainer.addEventListener('mouseover', handleRowHover);
        tableContainer.addEventListener('mouseout', handleRowHoverOut);

        // Fetch user role early to cache it
        fetchUserRole().catch(err => {
            console.warn('[BulkSelection] Failed to fetch user role during init:', err);
        });

        // Check Data Migration feature flag before wiring buttons
        checkDataMigrationEnabled().then(() => {
            // Wire up all bulk operation buttons
            wireUpBulkButtons();
            // Update button visibility based on current category
            // Use requestAnimationFrame to ensure DOM is ready
            requestAnimationFrame(() => {
                setTimeout(() => {
                    updateBulkButtonsVisibility();
                }, 100);
            });
        });

        // Check if user can access bulk update (for logging/debugging)
        checkBulkUpdateAccess();
    }

    /**
     * Fetch and cache user role from /api/me endpoint
     * @returns {Promise<string|null>} User role or null if not available
     */
    async function fetchUserRole() {
        // Return cached role if available
        if (cachedUserRole !== null) {
            return cachedUserRole;
        }

        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });
            
            if (response.ok) {
                const userData = await response.json();
                const role = userData.role || null;
                cachedUserRole = role;
                return role;
            } else {
                console.warn('[BulkSelection] Failed to fetch user role, status:', response.status);
                cachedUserRole = null;
                return null;
            }
        } catch (error) {
            console.warn('[BulkSelection] Error fetching user role:', error);
            cachedUserRole = null;
            return null;
        }
    }

    /**
     * Check if user is Web User, Admin, or Super Admin
     * @param {string|null} role - User role string
     * @returns {Object} Object with role flags: { isWebUser, isAdmin, isSuperAdmin }
     */
    function checkUserRoleType(role) {
        if (!role) {
            return { isWebUser: true, isAdmin: false, isSuperAdmin: false };
        }

        var R = typeof RoleUtils !== 'undefined' ? RoleUtils : null;
        var isSuperAdmin = R ? R.isSuperAdminRole(role) : (function () {
            var k = String(role).trim().toLowerCase().replace(/[\s\-_]+/g, '');
            return k === 'superadmin' || k === 'suberadmin';
        }());
        var isAdmin = R ? R.isAdminOrSuperAdminRole(role) : (isSuperAdmin || String(role).trim().toLowerCase().replace(/[\s\-_]+/g, '') === 'admin');
        var isWebUser = !isAdmin;

        return { isWebUser, isAdmin, isSuperAdmin };
    }

    /**
     * Check if Data Migration is enabled
     */
    async function checkDataMigrationEnabled() {
        try {
            // Avoid admin endpoint call for guests/web users to prevent expected 403 noise.
            const role = await fetchUserRole();
            const roleType = checkUserRoleType(role);
            if (!roleType.isAdmin && !roleType.isSuperAdmin) {
                dataMigrationEnabled = false;
                return;
            }

            const response = await fetch('/api/admin/environment/data-migration', {
                method: 'GET',
                credentials: 'include'
            });
            
            if (response.ok) {
                const data = await response.json();
                dataMigrationEnabled = data.enabled === true;
            } else if (response.status === 403) {
                // User doesn't have admin access, feature is disabled
                dataMigrationEnabled = false;
            } else {
                // Default to disabled on error
                dataMigrationEnabled = false;
                console.warn('[BulkSelection] Could not check Data Migration status, defaulting to disabled');
            }
        } catch (error) {
            console.warn('[BulkSelection] Error checking Data Migration status:', error);
            dataMigrationEnabled = false;
        }
    }

    /**
     * Wire up all bulk operation buttons
     */
    function wireUpBulkButtons() {
        // Bulk Delete button
        const bulkDeleteBtn = document.querySelector('.bulk-delete');
        if (bulkDeleteBtn) {
            // Don't make visible here - let updateBulkButtonsVisibility handle it
            bulkDeleteBtn.addEventListener('click', handleBulkDeleteClick);
        } else {
            retryButtonSetup('.bulk-delete', handleBulkDeleteClick, 'Bulk delete');
        }

        // Bulk Update button
        const bulkUpdateBtn = document.querySelector('.bulk-update');
        if (bulkUpdateBtn) {
            // Don't make visible here - let updateBulkButtonsVisibility handle it
            bulkUpdateBtn.addEventListener('click', handleBulkUpdateClick);
        } else {
            retryButtonSetup('.bulk-update', handleBulkUpdateClick, 'Bulk update');
        }

        // Bulk Migrate Dropdown - only show if enabled
        const bulkMigrateContainer = document.getElementById('bulkMigrateContainer');
        if (bulkMigrateContainer) {
            // Don't set display here - let updateBulkButtonsVisibility handle it
            // Visibility will be set based on category and dataMigrationEnabled
                
                // Toggle dropdown on button click
                const bulkMigrateBtn = document.getElementById('bulkMigrateBtn');
                const dropdown = document.getElementById('bulkMigrateDropdown');
                
                if (bulkMigrateBtn && dropdown) {
                    bulkMigrateBtn.addEventListener('click', (e) => {
                        e.stopPropagation();
                        dropdown.classList.toggle('show');
                    });
                }
                
                // Wire up dropdown options
                const selectedOption = bulkMigrateContainer.querySelector('[data-option="selected"]');

                if (selectedOption) {
                    selectedOption.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        if (dropdown) dropdown.classList.remove('show');
                        handleBulkMigrateClick(e);
                    });
                }

                const envOption = bulkMigrateContainer.querySelector('[data-option="env"]');
                if (envOption) {
                    envOption.addEventListener('click', (e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        if (dropdown) dropdown.classList.remove('show');
                        handleBulkMigrateEnvClick(e);
                    });
                }
                
                // Close dropdown when clicking outside
                document.addEventListener('click', (e) => {
                    if (bulkMigrateContainer && !bulkMigrateContainer.contains(e.target)) {
                        if (dropdown) dropdown.classList.remove('show');
                    }
                });
        } else {
            // Fallback: try to wire up old buttons if they exist
            const bulkMigrateBtn = document.querySelector('.bulk-migrate');
            if (bulkMigrateBtn && dataMigrationEnabled) {
                retryButtonSetup('.bulk-migrate', handleBulkMigrateClick, 'Bulk migrate');
            }
        }
        
    }

    /**
     * Make button visible
     */
    function makeButtonVisible(btn) {
        if (btn) {
            // Only set display if hidden class is not present
            if (!btn.classList.contains('hidden')) {
                btn.style.setProperty('display', 'inline-flex', 'important');
                btn.style.setProperty('visibility', 'visible', 'important');
            } else {
                // Remove hidden class first, then set display
                btn.classList.remove('hidden');
                btn.removeAttribute('hidden');
                btn.style.setProperty('display', 'inline-flex', 'important');
                btn.style.setProperty('visibility', 'visible', 'important');
            }
            btn.style.opacity = '1';
        }
    }

    /**
     * Hide button
     */
    function makeButtonHidden(btn) {
        if (btn) {
            btn.style.setProperty('display', 'none', 'important');
            btn.style.setProperty('visibility', 'hidden', 'important');
            btn.style.opacity = '0';
            btn.classList.add('hidden');
            btn.setAttribute('hidden', 'true');
        }
    }

    /**
     * Update bulk buttons visibility based on current category and user role
     * First applies category-based restrictions, then applies role-based filtering
     * Hides buttons for CR, Active Tasks, and Physical Fields (all buttons)
     * Hides Bulk Delete and Bulk Migrate for Role
     * Hides Bulk Migrate for People
     * Hides only bulk update button for geography, regulator, and regulatory-theme
     * Role-based filtering:
     * - Web User: Hide all buttons (overrides category rules)
     * - Admin: Hide Bulk Delete only (keeps Bulk Update and Migrate if category allows)
     * - Super Admin: Keep all buttons as determined by category
     */
    // Track last category to prevent redundant updates
    let lastCategoryForButtons = null;
    
    async function updateBulkButtonsVisibility(category) {
        // Get category if not provided
        if (!category) {
            category = getCurrentCategory();
            // Fallback to getActiveCategoryWithFallback if available
            if (!category && typeof getActiveCategoryWithFallback === 'function') {
                category = getActiveCategoryWithFallback();
            }
        }

        // Normalize category using canonical key (if available)
        const canonicalKey = typeof canonicalCategoryKey === 'function' 
            ? canonicalCategoryKey(category) 
            : (category || '').toString().toLowerCase().trim().replace(/[\s_]/g, '-');
        
        // Early return guard: if same category repeated, do nothing
        if (lastCategoryForButtons === canonicalKey) {
            return;
        }
        lastCategoryForButtons = canonicalKey;

        if (!category) {
            // If no category, show buttons by default
            const bulkDeleteBtn = document.querySelector('.bulk-delete');
            const bulkUpdateBtn = document.querySelector('.bulk-update');
            const bulkMigrateContainer = document.getElementById('bulkMigrateContainer');
            
            if (bulkDeleteBtn) makeButtonVisible(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonVisible(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                if (dataMigrationEnabled) {
                    bulkMigrateContainer.style.setProperty('display', 'inline-flex', 'important');
                    bulkMigrateContainer.style.setProperty('visibility', 'visible', 'important');
                    bulkMigrateContainer.classList.remove('hidden');
                } else {
                    bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                    bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                    bulkMigrateContainer.classList.add('hidden');
                }
            }
            // Apply role-based filtering even when no category
            await applyRoleBasedFiltering(bulkDeleteBtn, bulkUpdateBtn, bulkMigrateContainer);
            return;
        }

        // Normalize category name to lowercase for comparison
        const normalizedCategory = category.toLowerCase().replace(/[_\s]/g, '-');
        
        // Check if category is CR, Active Tasks, or Physical Fields (hide all buttons)
        const isCR = normalizedCategory.includes('change-request') || 
                     normalizedCategory.includes('changerequest') ||
                     normalizedCategory === 'change-requests' ||
                     normalizedCategory === 'change-request';
        const isActiveTasks = normalizedCategory.includes('active-task') || 
                             normalizedCategory.includes('activetask') ||
                             normalizedCategory === 'active-tasks';
        const isPhysicalField = normalizedCategory.includes('physical-field') || 
                               normalizedCategory.includes('physicalfield') ||
                               normalizedCategory.includes('physical-fields') ||
                               normalizedCategory.includes('physicalfields') ||
                               normalizedCategory === 'physical-field' ||
                               normalizedCategory === 'physical-fields';

        const shouldHideAll = isCR || isActiveTasks || isPhysicalField;

        // Check if category is Role (hide Bulk Delete and Bulk Migrate)
        const isRole = normalizedCategory === 'role' || normalizedCategory === 'roles';

        // Check if category is People (hide Bulk Migrate only)
        const isPeople = normalizedCategory === 'people';

        // Check if category is geography, regulator, or regulatory-theme (hide only bulk update)
        const isGeography = normalizedCategory === 'geography' || 
                           normalizedCategory === 'geographies';
        const isRegulator = normalizedCategory === 'regulator' || 
                           normalizedCategory === 'regulators';
        const isRegulatoryTheme = normalizedCategory === 'regulatory-theme' || 
                                  normalizedCategory === 'regulatorytheme' || 
                                  normalizedCategory === 'regulatory_theme';

        const shouldHideUpdateOnly = isGeography || isRegulator || isRegulatoryTheme;

        // Get button elements
        const bulkDeleteBtn = document.querySelector('.bulk-delete');
        const bulkUpdateBtn = document.querySelector('.bulk-update');
        const bulkMigrateContainer = document.getElementById('bulkMigrateContainer');

        if (shouldHideAll) {
            if (bulkDeleteBtn) makeButtonHidden(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonHidden(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                bulkMigrateContainer.classList.add('hidden');
            }
        } else if (isRole) {
            if (bulkDeleteBtn) makeButtonHidden(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonVisible(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                bulkMigrateContainer.classList.add('hidden');
            }
        } else if (isPeople) {
            if (bulkDeleteBtn) makeButtonVisible(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonVisible(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                bulkMigrateContainer.classList.add('hidden');
            }
        } else if (shouldHideUpdateOnly) {
            if (bulkDeleteBtn) makeButtonVisible(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonHidden(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                if (dataMigrationEnabled) {
                    bulkMigrateContainer.style.setProperty('display', 'inline-flex', 'important');
                    bulkMigrateContainer.style.setProperty('visibility', 'visible', 'important');
                    bulkMigrateContainer.classList.remove('hidden');
                } else {
                    bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                    bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                    bulkMigrateContainer.classList.add('hidden');
                }
            }
        } else {
            if (bulkDeleteBtn) makeButtonVisible(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonVisible(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                if (dataMigrationEnabled) {
                    bulkMigrateContainer.style.setProperty('display', 'inline-flex', 'important');
                    bulkMigrateContainer.style.setProperty('visibility', 'visible', 'important');
                    bulkMigrateContainer.classList.remove('hidden');
                } else {
                    bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                    bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                    bulkMigrateContainer.classList.add('hidden');
                }
            }
        }

        // Apply role-based filtering after category-based logic
        await applyRoleBasedFiltering(bulkDeleteBtn, bulkUpdateBtn, bulkMigrateContainer);
    }

    /**
     * Apply role-based filtering to bulk buttons
     * This is called after category-based visibility logic
     * @param {HTMLElement} bulkDeleteBtn - Bulk Delete button element
     * @param {HTMLElement} bulkUpdateBtn - Bulk Update button element
     * @param {HTMLElement} bulkMigrateContainer - Bulk Migrate container element
     */
    async function applyRoleBasedFiltering(bulkDeleteBtn, bulkUpdateBtn, bulkMigrateContainer) {
        // Fetch user role
        const userRole = await fetchUserRole();
        const roleType = checkUserRoleType(userRole);

        console.log('[BulkSelection] Applying role-based filtering. Role:', userRole, 'Type:', roleType);

        // Web User: Hide all buttons (overrides all category rules)
        if (roleType.isWebUser) {
            console.log('[BulkSelection] Web User detected - hiding all bulk buttons');
            if (bulkDeleteBtn) makeButtonHidden(bulkDeleteBtn);
            if (bulkUpdateBtn) makeButtonHidden(bulkUpdateBtn);
            if (bulkMigrateContainer) {
                bulkMigrateContainer.style.setProperty('display', 'none', 'important');
                bulkMigrateContainer.style.setProperty('visibility', 'hidden', 'important');
                bulkMigrateContainer.classList.add('hidden');
            }
            return;
        }

        if (roleType.isAdmin && !roleType.isSuperAdmin) {
            if (bulkDeleteBtn) {
                makeButtonHidden(bulkDeleteBtn);
            }
            // Bulk Update and Migrate remain as set by category logic
        }

        if (roleType.isSuperAdmin) {
            // No additional filtering needed - category logic already applied
        }
    }

    /**
     * Retry button setup after delay
     */
    function retryButtonSetup(selector, handler, buttonName) {
        setTimeout(() => {
            const retryBtn = document.querySelector(selector);
            if (retryBtn) {
                // Don't make visible here - let updateBulkButtonsVisibility handle it
                retryBtn.addEventListener('click', handler);
                // Update visibility after wiring up
                requestAnimationFrame(() => {
                    setTimeout(() => {
                        updateBulkButtonsVisibility();
                    }, 100);
                });
            }
        }, 1000);
    }

    /**
     * Check if current user can access bulk update
     * Note: Button is always visible, access is validated on server when clicked
     */
    async function checkBulkUpdateAccess() {
        // Optional: Check access for logging/debugging only
        try {
            const response = await fetch('/api/bulk-update/can-access', {
                method: 'GET',
                credentials: 'include'
            });
            const data = await response.json();
        } catch (error) {
            console.warn('[BulkSelection] Could not check access (will validate on server):', error);
        }
    }

    /**
     * Handle row click for selection
     * - Normal click: Toggle row selection (add/remove from multi-selection)
     * - CTRL+click: Toggle row selection (backward compatibility)
     * - Checkbox clicks are handled separately
     */
    function handleRowClick(event) {
        const row = event.target.closest('tbody tr');
        if (!row || row.classList.contains('no-data-row')) return;

        // Don't handle clicks on checkboxes (they have their own handler)
        if (event.target.type === 'checkbox' || event.target.closest('input[type="checkbox"]')) {
            return;
        }

        // Don't prevent default if clicking on links or buttons
        const isLink = event.target.closest('a, button, .clickable-link');
        if (isLink) {
            return; // Let links work normally
        }

        // Prevent default behavior and text selection
        event.preventDefault();
        event.stopPropagation(); // Prevent other handlers from interfering

        const rowData = getRowData(row);
        
        // Toggle selection on any click (regular or CTRL+click)
        toggleRowSelection(row);
    }

    /**
     * Select single row (replaces previous selection)
     */
    function selectSingleRow(row) {
        const rowData = getRowData(row);
        if (!rowData) return;

        const rowId = getRowId(rowData);
        const rowFacet = getRowFacet(rowData);
        
        // Check if this row is already the only selected row
        if (selectedRows.length === 1 && getRowId(selectedRows[0]) === rowId && row.hasAttribute('data-bulk-selected')) {
            // Already selected, no need to change
            return;
        }

        // Clear all previous selections
        document.querySelectorAll('tbody tr[data-bulk-selected]').forEach(r => {
            r.classList.remove('cellselected');
            r.removeAttribute('data-bulk-selected');
            // Clear checkbox state
            const checkbox = r.querySelector('.row-select-checkbox');
            if (checkbox) {
                checkbox.checked = false;
            }
        });
        
        // Clear selected rows
        selectedRows = [];

        // Select this row
        row.classList.add('cellselected');
        row.setAttribute('data-bulk-selected', 'true');
        selectedRows.push(rowData);
        currentFacet = rowFacet;

        // Save selection to storage
        saveSelectionToStorage();

        // Update checkbox state
        updateCheckboxState(row, true);

        // Update Select All checkbox state
        updateSelectAllCheckboxState();
    }

    /**
     * Toggle row selection state with same-facet validation (for CTRL+click)
     */
    function toggleRowSelection(row) {
        const rowData = getRowData(row);
        if (!rowData) {
            console.warn('[BulkSelection] toggleRowSelection: No row data found');
            return;
        }

        const rowId = getRowId(rowData);
        
        // Check if this row is already in our selectedRows array (more reliable than checking class/attribute)
        const isSelectedInArray = selectedRows.some(r => getRowId(r) === rowId);
        const hasOurAttribute = row.hasAttribute('data-bulk-selected');
        
        // Row is selected if it's in our array AND has our attribute
        const isSelected = isSelectedInArray && hasOurAttribute;

        if (isSelected) {
            // Deselect - remove from our selection
            row.classList.remove('cellselected');
            row.removeAttribute('data-bulk-selected');
            selectedRows = selectedRows.filter(r => getRowId(r) !== rowId);
            
            // If no rows selected, clear current facet
            if (selectedRows.length === 0) {
                currentFacet = null;
            }
        } else {
            // Get facet from row data or active module
            const rowFacet = getRowFacet(rowData);
            
            // Validate same-facet: if we have selected rows, check if this row is from the same facet
            if (selectedRows.length > 0 && currentFacet) {
                if (rowFacet && rowFacet !== currentFacet) {
                    showMessage('Please select objects from the same facet only. Current selection is from ' + currentFacet + ', but this row is from ' + rowFacet + '.', 'error');
                    return;
                }
            }
            
            // Select - add to our selection
            row.classList.add('cellselected');
            row.setAttribute('data-bulk-selected', 'true');
            selectedRows.push(rowData);
            
            // Update current facet if not set
            if (!currentFacet && rowFacet) {
                currentFacet = rowFacet;
            }
        }

        // Save selection to storage
        saveSelectionToStorage();

        // Update checkbox state
        updateCheckboxState(row, !isSelected);

        // Update Select All checkbox state
        updateSelectAllCheckboxState();
    }

    /**
     * Handle checkbox change events
     */
    function handleCheckboxChange(event) {
        const checkbox = event.target;
        if (!checkbox || checkbox.type !== 'checkbox') return;

        // Handle Select All checkbox
        if (checkbox.id === 'select-all-checkbox' || checkbox.classList.contains('select-all-checkbox')) {
            handleSelectAllChange(checkbox);
            return;
        }

        // Handle individual row checkbox
        if (checkbox.classList.contains('row-select-checkbox')) {
            const row = checkbox.closest('tbody tr');
            if (!row) return;

            event.stopPropagation(); // Prevent row click handler from firing

            // Toggle selection based on checkbox state
            const rowData = getRowData(row);
            if (!rowData) return;

            const rowId = getRowId(rowData);
            const isSelected = selectedRows.some(r => getRowId(r) === rowId);

            if (checkbox.checked && !isSelected) {
                // Select the row
                toggleRowSelection(row);
            } else if (!checkbox.checked && isSelected) {
                // Deselect the row
                toggleRowSelection(row);
            }
        }
    }

    /**
     * Handle Select All checkbox change
     */
    function handleSelectAllChange(selectAllCheckbox) {
        const tableContainer = document.querySelector('.data-table-wrapper');
        if (!tableContainer) return;

        const allCheckboxes = tableContainer.querySelectorAll('tbody tr .row-select-checkbox');
        const checked = selectAllCheckbox.checked;

        // Get current category/facet for validation
        const category = getCurrentCategory();
        if (!category) return;

        // Get facet ID for current category
        let currentFacetId = null;
        if (typeof categoryToFacetId === 'function') {
            currentFacetId = categoryToFacetId(category);
        } else if (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function') {
            currentFacetId = window.categoryToFacetId(category);
        } else {
            currentFacetId = category.toUpperCase();
        }

        if (checked) {
            // Select all rows in current facet
            allCheckboxes.forEach(checkbox => {
                const row = checkbox.closest('tbody tr');
                if (!row) return;

                const rowData = getRowData(row);
                if (!rowData) return;

                const rowFacet = getRowFacet(rowData);
                
                // Only select rows from the same facet
                if (rowFacet === currentFacetId) {
                    const rowId = getRowId(rowData);
                    const isSelected = selectedRows.some(r => getRowId(r) === rowId);
                    
                    if (!isSelected) {
                        checkbox.checked = true;
                        toggleRowSelection(row);
                    }
                }
            });
        } else {
            // Deselect all rows in current facet
            allCheckboxes.forEach(checkbox => {
                const row = checkbox.closest('tbody tr');
                if (!row) return;

                const rowData = getRowData(row);
                if (!rowData) return;

                const rowFacet = getRowFacet(rowData);
                
                // Only deselect rows from the same facet
                if (rowFacet === currentFacetId) {
                    const rowId = getRowId(rowData);
                    const isSelected = selectedRows.some(r => getRowId(r) === rowId);
                    
                    if (isSelected) {
                        checkbox.checked = false;
                        toggleRowSelection(row);
                    }
                }
            });
        }
    }

    /**
     * Update checkbox state for a specific row
     */
    function updateCheckboxState(row, checked) {
        if (!row) return;
        const checkbox = row.querySelector('.row-select-checkbox');
        if (checkbox) {
            checkbox.checked = checked;
        }
    }

    /**
     * Update Select All checkbox state based on current selection
     */
    function updateSelectAllCheckboxState() {
        const selectAllCheckbox = document.getElementById('select-all-checkbox') || 
                                  document.querySelector('.select-all-checkbox');
        if (!selectAllCheckbox) return;

        const tableContainer = document.querySelector('.data-table-wrapper');
        if (!tableContainer) return;

        // Get current category/facet
        const category = getCurrentCategory();
        if (!category) return;

        // Get facet ID for current category
        let currentFacetId = null;
        if (typeof categoryToFacetId === 'function') {
            currentFacetId = categoryToFacetId(category);
        } else if (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function') {
            currentFacetId = window.categoryToFacetId(category);
        } else {
            currentFacetId = category.toUpperCase();
        }

        // Get all checkboxes in current facet
        const allCheckboxes = Array.from(tableContainer.querySelectorAll('tbody tr .row-select-checkbox'))
            .filter(checkbox => {
                const row = checkbox.closest('tbody tr');
                if (!row) return false;
                const rowData = getRowData(row);
                if (!rowData) return false;
                const rowFacet = getRowFacet(rowData);
                return rowFacet === currentFacetId;
            });

        if (allCheckboxes.length === 0) {
            selectAllCheckbox.checked = false;
            selectAllCheckbox.indeterminate = false;
            return;
        }

        // Count selected checkboxes in current facet
        const selectedCount = allCheckboxes.filter(checkbox => {
            const row = checkbox.closest('tbody tr');
            if (!row) return false;
            const rowData = getRowData(row);
            if (!rowData) return false;
            const rowId = getRowId(rowData);
            return selectedRows.some(r => getRowId(r) === rowId);
        }).length;

        // Update Select All checkbox state
        if (selectedCount === 0) {
            selectAllCheckbox.checked = false;
            selectAllCheckbox.indeterminate = false;
        } else if (selectedCount === allCheckboxes.length) {
            selectAllCheckbox.checked = true;
            selectAllCheckbox.indeterminate = false;
        } else {
            selectAllCheckbox.checked = false;
            selectAllCheckbox.indeterminate = true;
        }
    }

    /**
     * Get current category from active category item
     */
    function getCurrentCategory() {
        const activeCategoryItem = document.querySelector('.category-item.active, .category-item.current');
        if (activeCategoryItem) {
            return activeCategoryItem.getAttribute('data-category');
        }
        return null;
    }

    /**
     * Sync all checkbox states with current selection
     * Call this after table is generated or selection changes programmatically
     */
    function syncAllCheckboxStates() {
        const tableContainer = document.querySelector('.data-table-wrapper');
        if (!tableContainer) return;

        const allCheckboxes = tableContainer.querySelectorAll('tbody tr .row-select-checkbox');
        allCheckboxes.forEach(checkbox => {
            const row = checkbox.closest('tbody tr');
            if (!row) return;

            const rowData = getRowData(row);
            if (!rowData) {
                checkbox.checked = false;
                return;
            }

            const rowId = getRowId(rowData);
            const isSelected = selectedRows.some(r => getRowId(r) === rowId);
            checkbox.checked = isSelected;
        });

        // Update Select All checkbox state
        updateSelectAllCheckboxState();
    }

    /**
     * Handle row hover effect
     */
    function handleRowHover(event) {
        const row = event.target.closest('tbody tr');
        if (row && !row.classList.contains('no-data-row')) {
            row.classList.add('row-hover');
        }
    }

    /**
     * Handle row hover out
     */
    function handleRowHoverOut(event) {
        const row = event.target.closest('tbody tr');
        if (row) {
            row.classList.remove('row-hover');
        }
    }

    /**
     * Handle Bulk Delete button click
     */
    function handleBulkDeleteClick(event) {
        event.preventDefault();

        // Validate selection
        if (selectedRows.length === 0) {
            showMessage('No selected rows', 'error');
            return;
        }

        // Get facet and validate
        const facet = getFacetFromSelection();
        if (!facet) {
            showMessage('Unable to determine facet. Please try selecting rows again.', 'error');
            return;
        }

        if (!validateSameFacet()) {
            showMessage('Please select objects from the same facet only', 'error');
            return;
        }

        // Check if facet is excluded
        if (isFacetExcluded(facet, 'delete')) {
            showMessage('Bulk Delete is not available for this facet', 'error');
            return;
        }

        // Store selection and navigate
        storeSelectionAndNavigate('bulkDeleteSelection', '/bulk-delete.html', facet);
    }

    /**
     * Handle Bulk Update button click
     */
    function handleBulkUpdateClick(event) {
        event.preventDefault();

        // Validate selection
        if (selectedRows.length === 0) {
            showMessage('No selected rows', 'error');
            return;
        }

        // Get facet and validate
        const facet = getFacetFromSelection();
        if (!facet) {
            showMessage('Unable to determine facet. Please try selecting rows again.', 'error');
            return;
        }

        if (!validateSameFacet()) {
            showMessage('Please select objects from the same facet only', 'error');
            return;
        }

        // Check if facet is excluded
        if (isFacetExcluded(facet, 'update')) {
            showMessage('Bulk Update is not available for this facet', 'error');
            return;
        }

        // Store selection and navigate
        storeSelectionAndNavigate('bulkUpdateSelection', '/bulk-update.html', facet);
    }

    /**
     * Handle Bulk Migrate button click (for selected objects)
     */
    function handleBulkMigrateClick(event) {
        event.preventDefault();

        // Validate selection
        if (selectedRows.length === 0) {
            showMessage('No selected rows', 'error');
            return;
        }

        // Get facet and validate
        const facet = getFacetFromSelection();
        if (!facet) {
            showMessage('Unable to determine facet. Please try selecting rows again.', 'error');
            return;
        }

        if (!validateSameFacet()) {
            showMessage('Please select objects from the same facet only', 'error');
            return;
        }

        // Check if facet is excluded
        if (isFacetExcluded(facet, 'migrate')) {
            showMessage('Bulk Migrate is not available for this facet', 'error');
            return;
        }

        // Store selection and navigate
        storeSelectionAndNavigate('bulkMigrateSelection', '/bulk-migrate.html', facet);
    }

    /**
     * Bulk Migrate ENV: table snapshot ZIP (same package as Admin → Bulk Import ENV).
     */
    async function handleBulkMigrateEnvClick(event) {
        event.preventDefault();
        event.stopPropagation();

        let optionElement = event.target.closest('[data-option="env"]');
        if (!optionElement) {
            const container = document.getElementById('bulkMigrateContainer');
            if (container) {
                optionElement = container.querySelector('[data-option="env"]');
            }
        }
        if (!optionElement) {
            console.error('[BulkSelection] Could not find Bulk Migrate ENV option element');
            return;
        }

        const bulkMigrateBtn = document.getElementById('bulkMigrateBtn');
        let originalText = '';
        let originalDisabled = false;
        if (bulkMigrateBtn) {
            originalText = bulkMigrateBtn.innerHTML;
            originalDisabled = bulkMigrateBtn.disabled;
            bulkMigrateBtn.disabled = true;
            bulkMigrateBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Downloading...';
        }
        optionElement.style.pointerEvents = 'none';
        optionElement.style.opacity = '0.6';

        try {
            const response = await fetch('/api/table-snapshot/export', {
                method: 'GET',
                credentials: 'include'
            });

            if (response.ok) {
                const blob = await response.blob();
                const cd = response.headers.get('Content-Disposition');
                let downloadName = 'table_snapshot.zip';
                if (cd) {
                    const m = cd.match(/filename="?([^";]+)"?/i);
                    if (m) downloadName = m[1].trim();
                }
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = downloadName;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);
                const okMsg = (window.I18n && window.I18n.t('migrate.envSuccess'))
                    || 'Table snapshot downloaded (ZIP).';
                showMessage(okMsg, 'success');
            } else {
                const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
                showMessage('Snapshot export failed: ' + (errorData.error || response.statusText), 'error');
            }
        } catch (e) {
            console.error('[BulkSelection] Table snapshot export error:', e);
            showMessage('Error during snapshot export: ' + e.message, 'error');
        } finally {
            if (bulkMigrateBtn) {
                bulkMigrateBtn.disabled = originalDisabled;
                bulkMigrateBtn.innerHTML = originalText;
            }
            optionElement.style.pointerEvents = '';
            optionElement.style.opacity = '';
        }
    }

    /**
     * Get facet from selection or active module
     */
    function getFacetFromSelection() {
        const activeModule = document.querySelector('.selected-module.active');
        let facet = currentFacet;
        
        if (!facet && activeModule) {
            facet = activeModule.getAttribute('data-facet-id') || activeModule.textContent.trim().toLowerCase();
        }
        
        // If still no facet, try to get from first selected row
        if (!facet && selectedRows.length > 0) {
            facet = getRowFacet(selectedRows[0]);
        }

        return facet;
    }

    /**
     * Validate all selected rows are from the same facet
     */
    function validateSameFacet() {
        if (selectedRows.length <= 1) return true;

        // Check if all rows have the same facet
        const firstFacet = getRowFacet(selectedRows[0]);
        if (!firstFacet) return true; // If no facet info, allow (fallback)

        for (let i = 1; i < selectedRows.length; i++) {
            const rowFacet = getRowFacet(selectedRows[i]);
            if (rowFacet && rowFacet !== firstFacet) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if facet is excluded for the given operation
     */
    function isFacetExcluded(facet, operation) {
        const excludedFacets = EXCLUDED_FACETS[operation] || [];
        const normalizedFacet = facet.toLowerCase().replace(/[_\s]/g, '-');
        return excludedFacets.some(ef => normalizedFacet.includes(ef));
    }

    /**
     * Store selection data and navigate to page
     */
    function storeSelectionAndNavigate(storageKey, targetUrl, facet) {
        // Filter out null/undefined IDs and ensure they are numbers
        const objectIds = selectedRows
            .map(row => getRowId(row))
            .map(id => {
                // Convert to number if it's a string
                const numId = typeof id === 'string' ? parseInt(id, 10) : id;
                return isNaN(numId) ? null : numId;
            })
            .filter(id => id !== null && id !== undefined);

        if (objectIds.length === 0) {
            showMessage('No valid item IDs found in selection', 'error');
            return;
        }

        const selectionData = {
            facet: facet || currentFacet,
            rows: selectedRows,
            objectIds: objectIds
        };
        
        sessionStorage.setItem(storageKey, JSON.stringify(selectionData));

        // Clear selection state (will be restored if user comes back)
        clearSelectionState();

        // Navigate to target page
        window.location.href = targetUrl;
    }

    /**
     * Get row data from data attribute
     */
    function getRowData(row) {
        try {
            const dataAttr = row.getAttribute('data-row-data');
            if (dataAttr) {
                return JSON.parse(dataAttr);
            }
        } catch (e) {
            console.error('[BulkSelection] Error parsing row data:', e);
        }
        return null;
    }

    /**
     * Get row ID from row data
     */
    function getRowId(rowData) {
        if (!rowData) return null;
        // Try different ID field names
        return rowData.ID || rowData.id || rowData.Id || rowData.Ref || null;
    }

    /**
     * Get facet from row data
     */
    function getRowFacet(rowData) {
        if (!rowData) return null;
        // Try to get facet from row data (added by search-table.js)
        if (rowData.facet) return rowData.facet;
        if (rowData.facetId) return rowData.facetId;
        if (rowData.category) {
            // Try to convert category to facet using categoryToFacetId if available
            if (typeof categoryToFacetId === 'function') {
                return categoryToFacetId(rowData.category);
            } else if (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function') {
                return window.categoryToFacetId(rowData.category);
            }
            return rowData.category.toUpperCase();
        }
        return null;
    }

    /**
     * Save selection state to sessionStorage
     */
    function saveSelectionToStorage() {
        try {
            const state = {
                selectedRowIds: selectedRows.map(row => getRowId(row)),
                currentFacet: currentFacet,
                timestamp: Date.now()
            };
            sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        } catch (e) {
            console.error('[BulkSelection] Error saving selection to storage:', e);
        }
    }

    /**
     * Load selection state from sessionStorage and restore visual selection
     */
    function loadSelectionFromStorage() {
        try {
            const stored = sessionStorage.getItem(STORAGE_KEY);
            if (!stored) return;

            const state = JSON.parse(stored);
            
            // Only restore if stored within last 30 minutes (prevent stale data)
            const maxAge = 30 * 60 * 1000; // 30 minutes
            if (Date.now() - state.timestamp > maxAge) {
                sessionStorage.removeItem(STORAGE_KEY);
                return;
            }

            currentFacet = state.currentFacet;
            
            // Restore visual selection by matching row IDs
            if (state.selectedRowIds && state.selectedRowIds.length > 0) {
                const allRows = document.querySelectorAll('tbody tr[data-row-data]');
                allRows.forEach(row => {
                    const rowData = getRowData(row);
                    if (rowData) {
                        const rowId = getRowId(rowData);
                        if (state.selectedRowIds.includes(rowId)) {
                            row.classList.add('cellselected');
                            row.setAttribute('data-bulk-selected', 'true');
                            if (!selectedRows.find(r => getRowId(r) === rowId)) {
                                selectedRows.push(rowData);
                            }
                        }
                    }
                });
            }

            // Sync checkbox states after loading
            setTimeout(() => {
                syncAllCheckboxStates();
            }, 100);
        } catch (e) {
            console.error('[BulkSelection] Error loading selection from storage:', e);
        }
    }

    /**
     * Clear selection state (including storage)
     */
    function clearSelectionState() {
        sessionStorage.removeItem(STORAGE_KEY);
        clearSelection();
    }

    /**
     * Clear all selections
     */
    function clearSelection() {
        // Clear all rows marked with data-bulk-selected
        document.querySelectorAll('tbody tr[data-bulk-selected]').forEach(row => {
            row.classList.remove('cellselected');
            row.removeAttribute('data-bulk-selected');
            // Clear checkbox state
            const checkbox = row.querySelector('.row-select-checkbox');
            if (checkbox) {
                checkbox.checked = false;
            }
        });
        selectedRows = [];
        currentFacet = null;
        sessionStorage.removeItem(STORAGE_KEY);
        
        // Update Select All checkbox state
        updateSelectAllCheckboxState();
    }

    /**
     * Get selected rows
     */
    function getSelectedRows() {
        return [...selectedRows];
    }

    /**
     * Get current facet
     */
    function getSelectedFacet() {
        return currentFacet;
    }

    /**
     * Show message to user
     */
    function showMessage(message, type = 'info') {
        // Try to use existing message functions
        if (type === 'error' && typeof showErrorMessage === 'function') {
            showErrorMessage(message);
        } else if (type === 'success' && typeof showSuccessMessage === 'function') {
            showSuccessMessage(message);
        } else {
            // Fallback to alert
            alert(message);
        }
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initBulkSelection);
    } else {
        // DOM already loaded, initialize after a short delay to ensure table is ready
        setTimeout(initBulkSelection, 500);
    }

    /**
     * Refresh button visibility based on feature flag
     * Can be called when table is regenerated or after search results load
     */
    function refreshBulkMigrateButtons() {
        const bulkMigrateBtn = document.querySelector('.bulk-migrate');

        if (bulkMigrateBtn) {
            if (dataMigrationEnabled) {
                makeButtonVisible(bulkMigrateBtn);
            } else {
                bulkMigrateBtn.style.display = 'none';
                bulkMigrateBtn.style.visibility = 'hidden';
            }
        }
    }

    // Re-initialize when table is regenerated
    const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
            if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                // Check if a new table was added
                for (const node of mutation.addedNodes) {
                    if (node.nodeType === 1 && (node.classList?.contains('search-table') || node.querySelector?.('.search-table'))) {
                        // Clear selection when table changes (new search results)
                        clearSelectionState();
                        // Update button visibility based on current category (this handles all buttons)
                        // Use requestAnimationFrame to ensure DOM is ready
                        requestAnimationFrame(() => {
                            setTimeout(() => {
                                updateBulkButtonsVisibility();
                            }, 150);
                        });
                        // Try to restore selection after a short delay (in case it's just a refresh)
                        setTimeout(() => {
                            loadSelectionFromStorage();
                        }, 100);
                    }
                }
            }
        }
    });

    // Start observing when DOM is ready
    setTimeout(() => {
        const tableContainer = document.querySelector('.data-table-wrapper');
        if (tableContainer) {
            observer.observe(tableContainer, { childList: true, subtree: true });
        }
    }, 1000);

    /**
     * Monitor category-item changes to update button visibility
     */
    function setupCategoryObserver() {
        // Find all category items
        const categoryItems = document.querySelectorAll('.category-item');
        
        if (categoryItems.length === 0) {
            // Retry if category items not found yet
            setTimeout(setupCategoryObserver, 500);
            return;
        }

        // Create debounced update function to prevent rapid-fire triggers
        const debouncedUpdate = debounce((category) => {
            updateBulkButtonsVisibility(category);
        }, 200);

        // Create observer for each category item to watch for class changes
        categoryItems.forEach(item => {
            const categoryObserver = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                        const target = mutation.target;
                        const hasActive = target.classList.contains('active') || target.classList.contains('current');
                        
                        if (hasActive) {
                            const category = target.getAttribute('data-category');
                            
                            // Prevent infinite loop: ignore if already processing this category
                            if (isProcessingCategoryChange && lastProcessedCategory === category) {
                                return;
                            }
                            
                            console.log('[BulkSelection] Category changed to:', category);
                            
                            // Set flag to prevent re-entry
                            isProcessingCategoryChange = true;
                            lastProcessedCategory = category;
                            
                            // Use debounced update to prevent infinite loop
                            debouncedUpdate(category);
                            
                            // Reset flag after a delay
                            setTimeout(() => {
                                isProcessingCategoryChange = false;
                            }, 500);
                        }
                    }
                });
            });

            // Observe class changes on this category item
            categoryObserver.observe(item, {
                attributes: true,
                attributeFilter: ['class']
            });
            
            // Store observer reference for potential disconnect/reconnect
            categoryObservers.push(categoryObserver);
        });

        // Also observe for new category items added dynamically
        const categoryContainer = document.querySelector('.category-sidebar-container') || 
                                  document.querySelector('.sidebar') ||
                                  document.body;
        
        if (categoryContainer) {
            // Create debounced update function for new items
            const debouncedNewItemUpdate = debounce((category) => {
                updateBulkButtonsVisibility(category);
            }, 200);

            const newCategoryObserver = new MutationObserver((mutations) => {
                mutations.forEach((mutation) => {
                    mutation.addedNodes.forEach((node) => {
                        if (node.nodeType === 1 && node.classList && node.classList.contains('category-item')) {
                            // New category item added, set up observer for it
                            const newItemObserver = new MutationObserver((mutations) => {
                                mutations.forEach((mutation) => {
                                    if (mutation.type === 'attributes' && mutation.attributeName === 'class') {
                                        const target = mutation.target;
                                        const hasActive = target.classList.contains('active') || target.classList.contains('current');
                                        
                                        if (hasActive) {
                                            const category = target.getAttribute('data-category');
                                            
                                            // Prevent infinite loop: ignore if already processing this category
                                            if (isProcessingCategoryChange && lastProcessedCategory === category) {
                                                return;
                                            }
                                            
                                            // Set flag to prevent re-entry
                                            isProcessingCategoryChange = true;
                                            lastProcessedCategory = category;
                                            
                                            // Use debounced update to prevent infinite loop
                                            debouncedNewItemUpdate(category);
                                            
                                            // Reset flag after a delay
                                            setTimeout(() => {
                                                isProcessingCategoryChange = false;
                                            }, 500);
                                        }
                                    }
                                });
                            });

                            newItemObserver.observe(node, {
                                attributes: true,
                                attributeFilter: ['class']
                            });
                            
                            // Store observer reference for potential disconnect/reconnect
                            categoryObservers.push(newItemObserver);
                        }
                    });
                });
            });

            newCategoryObserver.observe(categoryContainer, {
                childList: true,
                subtree: true
            });
            
            // Store the new category observer reference
            categoryObservers.push(newCategoryObserver);
        }
    }

    /**
     * Setup event listener for category-item clicks
     */
    function setupCategoryClickListener() {
        // Use event delegation on document or container
        const categoryContainer = document.querySelector('.category-sidebar-container') || 
                                  document.querySelector('.sidebar') ||
                                  document.body;

        if (categoryContainer) {
            categoryContainer.addEventListener('click', (e) => {
                const categoryItem = e.target.closest('.category-item');
                if (categoryItem) {
                    const category = categoryItem.getAttribute('data-category');
                    
                    // Update buttons after a short delay to ensure category is set
                    requestAnimationFrame(() => {
                        setTimeout(() => {
                            updateBulkButtonsVisibility(category);
                        }, 150);
                    });
                }
            });
        } else {
            // Retry if container not found
            setTimeout(setupCategoryClickListener, 500);
        }
    }

    // Setup category observers and listeners when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            setTimeout(() => {
                setupCategoryObserver();
                setupCategoryClickListener();
            }, 1000);
        });
    } else {
        setTimeout(() => {
            setupCategoryObserver();
            setupCategoryClickListener();
        }, 1000);
    }

    // Export functions to window (for backward compatibility and external access)
    window.bulkSelection = {
        init: initBulkSelection,
        getSelectedRows: getSelectedRows,
        getSelectedFacet: getSelectedFacet,
        clearSelection: clearSelectionState,
        loadFromStorage: loadSelectionFromStorage,
        syncCheckboxStates: syncAllCheckboxStates,
        updateButtonsVisibility: updateBulkButtonsVisibility
    };

    // Also export with old names for backward compatibility
    window.bulkDeleteSelection = window.bulkSelection;
    window.bulkUpdateSelection = window.bulkSelection;
    
    // Export with capitalized name for consistency
    window.BulkSelection = window.bulkSelection;

})();

