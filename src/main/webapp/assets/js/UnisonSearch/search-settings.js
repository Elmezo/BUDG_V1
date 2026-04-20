// Settings Dropdown Functions

// Store visible columns state per category
let visibleColumnsByCategory = {};

// Per-category column widths (px), keyed by data-column id
let columnWidthsByCategory = {};

const COLUMN_WIDTHS_STORAGE_KEY = 'unisionSearch_columnWidths';

function loadColumnWidthsFromStorage() {
    try {
        const stored = sessionStorage.getItem(COLUMN_WIDTHS_STORAGE_KEY);
        if (stored) {
            columnWidthsByCategory = JSON.parse(stored);
        }
    } catch (e) {
        columnWidthsByCategory = {};
    }
}

function saveColumnWidthsToStorage() {
    try {
        sessionStorage.setItem(COLUMN_WIDTHS_STORAGE_KEY, JSON.stringify(columnWidthsByCategory));
    } catch (e) {
        // Silent fail
    }
}

function getColumnWidthsForCategory(category) {
    if (!category) {
        return null;
    }
    if (!columnWidthsByCategory[category]) {
        loadColumnWidthsFromStorage();
    }
    const w = columnWidthsByCategory[category];
    return w && typeof w === 'object' ? w : null;
}

function setColumnWidthsForCategory(category, widthsObj) {
    if (!category) {
        return;
    }
    if (widthsObj && typeof widthsObj === 'object') {
        columnWidthsByCategory[category] = Object.assign({}, widthsObj);
    } else {
        delete columnWidthsByCategory[category];
    }
    saveColumnWidthsToStorage();
}

function mergeFacetsApiResponseIntoPreferences(result) {
    if (!result || !result.success || !result.data || !Array.isArray(result.data)) {
        return;
    }
    result.data.forEach(facet => {
        const facetId = facet.facetId;
        let category = null;
        if (window.facetIdToModuleName && typeof window.facetIdToModuleName === 'function') {
            const moduleName = window.facetIdToModuleName(facetId);
            if (moduleName && typeof moduleNameToCategoryKey === 'function') {
                category = moduleNameToCategoryKey(moduleName);
            }
        }
        if (!category) {
            const facetToCategoryMap = {
                'DATASET': 'data-sets',
                'ATTRIBUTE': 'attributes',
                'SYSTEM': 'system',
                'GLOSSARY': 'glossary',
                'DATAQUALITY': 'data-quality',
                'PEOPLE': 'people',
                'ROLE': 'role',
                'BUSINESS_AREA': 'business-area',
                'LEGAL_ENTITY': 'legal-entity',
                'CLIENT': 'client',
                'COMMITTEE': 'committee',
                'POLICY': 'policy',
                'PROCESS': 'process',
                'INTERFACE': 'interface',
                'CAPABILITY': 'capability',
                'PRODUCT': 'product',
                'ORG_UNIT': 'org-unit',
                'GEOGRAPHY': 'geography',
                'REGULATION': 'regulation',
                'REGULATOR': 'regulator',
                'REGULATORY_THEME': 'regulatory-theme',
                'ACTIVE_TASKS': 'active-tasks'
            };
            category = facetToCategoryMap[facetId];
        }
        if (!category) {
            return;
        }
        if (facet.activeFields && facet.activeFields.trim().length > 0) {
            const columns = facet.activeFields
                .split(',')
                .map(col => col.trim())
                .filter(col => col.length > 0);
            if (columns.length > 0) {
                visibleColumnsByCategory[category] = columns;
            }
        }
        if (facet.columnWidths && typeof facet.columnWidths === 'object' && !Array.isArray(facet.columnWidths)) {
            columnWidthsByCategory[category] = Object.assign({}, facet.columnWidths);
        }
    });
    loadVisibleColumnsFromStorage();
    loadColumnWidthsFromStorage();
}

// Load visible columns from sessionStorage (cleared when browser closes)
function loadVisibleColumnsFromStorage() {
    try {
        const stored = sessionStorage.getItem('unisionSearch_visibleColumns');
        if (stored) {
            visibleColumnsByCategory = JSON.parse(stored);
        }
    } catch (e) {
        visibleColumnsByCategory = {};
    }
}

// Save visible columns to sessionStorage (cleared when browser closes)
function saveVisibleColumnsToStorage() {
    try {
        sessionStorage.setItem('unisionSearch_visibleColumns', JSON.stringify(visibleColumnsByCategory));
    } catch (e) {
        // Silent fail
    }
}

// Save column preferences + widths to database (explicit Save Layout / Super Admin default save path)
async function saveColumnPreferencesToDatabase(category, columns, columnWidthsObj) {
    if (!category || !Array.isArray(columns)) return;
    
    const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
    if (!isLoggedIn) {
        return;
    }
    
    try {
        const normalizedCategory = typeof categoryToModule === 'function' ? categoryToModule(category) : category;
        const facetId = window.moduleNameToFacetId ? 
            window.moduleNameToFacetId(normalizedCategory) : 
            normalizedCategory.toUpperCase();
        
        if (!facetId) {
            console.warn('[SETTINGS] Could not map category to facetId:', category);
            return;
        }

        const widthsPayload = {};
        if (columnWidthsObj && typeof columnWidthsObj === 'object') {
            Object.keys(columnWidthsObj).forEach(k => {
                const n = parseInt(columnWidthsObj[k], 10);
                if (!isNaN(n) && n > 0) {
                    widthsPayload[k] = n;
                }
            });
        }
        
        const response = await fetch('/api/unison/columns/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                facetId: facetId,
                columns: columns,
                columnWidths: widthsPayload
            })
        });
        
        if (!response.ok) {
            console.error('[SETTINGS] Failed to save column preferences:', response.status);
            return;
        }
        
        const result = await response.json();
        if (result.success) {
            // saved
        }
    } catch (error) {
        console.error('[SETTINGS] Error saving column preferences to database:', error);
    }
}


// Get visible columns for a category (synchronous - checks memory and sessionStorage)
function getVisibleColumnsForCategory(category) {
    if (!category) return null;

    // If not in memory, try to load from storage
    if (!visibleColumnsByCategory[category]) {
        loadVisibleColumnsFromStorage();
    }

    return visibleColumnsByCategory[category] || null;
}

// Load column preferences from database on initialization
async function initializeColumnPreferencesFromDatabase() {
    // Check if user is logged in
    const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
    if (!isLoggedIn) {
        // For guests, just load from sessionStorage
        loadVisibleColumnsFromStorage();
        loadColumnWidthsFromStorage();
        return;
    }
    
    try {
        const response = await fetch('/api/unison/facets', {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            credentials: 'include'
        });
        
        if (!response.ok) {
            console.error('[SETTINGS] Failed to load facets for column preferences:', response.status);
            loadVisibleColumnsFromStorage();
            loadColumnWidthsFromStorage();
            return;
        }
        
        const result = await response.json();
        if (result.success && result.data && Array.isArray(result.data)) {
            mergeFacetsApiResponseIntoPreferences(result);
        } else {
            loadVisibleColumnsFromStorage();
            loadColumnWidthsFromStorage();
        }
    } catch (error) {
        console.error('[SETTINGS] Error initializing column preferences from database:', error);
        loadVisibleColumnsFromStorage();
        loadColumnWidthsFromStorage();
    }
}

// Set visible columns for a category (staged only; use Save Layout to persist to DB)
function setVisibleColumnsForCategory(category, columns) {
    if (!category || !Array.isArray(columns)) return;

    visibleColumnsByCategory[category] = columns;
    saveVisibleColumnsToStorage();
}

// Get all available columns for current table
function getCurrentTableColumns() {
    const table = document.querySelector('.search-table');
    if (!table) {
        // Try alternative selector
        const altTable = document.querySelector('.data-table-wrapper .search-table');
        if (!altTable) {
            return [];
        }
        const cols = getColumnsFromTable(altTable);
        return cols;
    }
    const cols = getColumnsFromTable(table);
    return cols;
}

// Helper function to extract columns from a table element
function getColumnsFromTable(table) {
    if (!table) {
        return [];
    }

    const headers = Array.from(table.querySelectorAll('th[data-column]'));

    const columns = headers
        .map(th => th.getAttribute('data-column'))
        .filter(col => col != null && col.trim() !== '');

    // Return empty array if no valid columns found
    return columns.length > 0 ? columns : [];
}

// Enhanced function to wait for table to be ready
function waitForTableColumns(maxWait = 3000) {
    return new Promise((resolve) => {
        const startTime = Date.now();
        let lastColumnCount = 0;
        let stableCount = 0;
        let checkCount = 0;

        const checkInterval = setInterval(() => {
            checkCount++;
            const columns = getCurrentTableColumns();
            const currentCount = columns.length;

            // Check if columns are stable (same count for 2 consecutive checks)
            if (currentCount === lastColumnCount && currentCount > 0) {
                stableCount++;
                if (stableCount >= 2) {
                    // Columns are stable, resolve
                    clearInterval(checkInterval);
                    resolve(columns);
                    return;
                }
            } else {
                stableCount = 0;
            }

            lastColumnCount = currentCount;

            // Timeout check
            const elapsed = Date.now() - startTime;
            if (elapsed >= maxWait) {
                clearInterval(checkInterval);
                resolve(columns.length > 0 ? columns : []);
            }
        }, 150); // Check every 150ms
    });
}

async function updateSettingsButtonForCategory(category) {
    const settingsBtn = document.getElementById('settingsBtn');
    if (settingsBtn) {
        // Update the button to reflect current category
        settingsBtn.setAttribute('data-category', category);

        // Show/hide export button based on category
        updateExportButtonVisibility(category);

        // Update bulk buttons visibility based on category
        if (typeof window.bulkSelection !== 'undefined' && 
            typeof window.bulkSelection.updateButtonsVisibility === 'function') {
            window.bulkSelection.updateButtonsVisibility(category);
        }

        // If the columns dropdown is open, refresh it
        const submenu = document.querySelector('.dropdown-submenu');
        if (submenu && submenu.classList.contains('active')) {
            // Wait a bit for table to render, then wait for columns
            setTimeout(async () => {
                await waitForTableColumns(3000);
                await populateColumnsDropdown(true);
            }, 200);
        }
    }
}

// Show/hide export button based on active module
function updateExportButtonVisibility(category) {
    const exportSubmenu = document.getElementById('exportBtn')?.closest('.dropdown-submenu');
    if (!exportSubmenu) return;

    // Check if export is disabled globally
    const isExportDisabled = exportSubmenu.getAttribute('data-export-disabled') === 'true';
    if (isExportDisabled) {
        exportSubmenu.style.display = 'none';
        return;
    }

    // Normalize category name
    const normalizedCategory = categoryToModule(category);

    // Get both export submenus
    const peopleExportSubmenu = document.getElementById('exportSubmenu');
    const otherFacetsExportSubmenu = document.getElementById('exportSubmenuOtherFacets');

    // Show export menu for all facets
    exportSubmenu.style.display = 'block';

    // Show appropriate submenu based on category
    if (normalizedCategory === 'people') {
        // People facet: use existing export menu
        if (peopleExportSubmenu) {
            peopleExportSubmenu.style.display = 'block';
        }
        if (otherFacetsExportSubmenu) {
            otherFacetsExportSubmenu.style.display = 'none';
        }
    } else {
        // Other facets: use new export menu with stakeholders options
        if (peopleExportSubmenu) {
            peopleExportSubmenu.style.display = 'none';
        }
        if (otherFacetsExportSubmenu) {
            otherFacetsExportSubmenu.style.display = 'block';
        }
    }
}

// Observer to watch for table changes
let tableObserver = null;

function initTableObserver() {
    // Watch for table changes in the table container
    const tableContainer = document.querySelector('.data-table-wrapper');
    if (!tableContainer) return;

    // Disconnect existing observer if any
    if (tableObserver) {
        tableObserver.disconnect();
    }

    // Create new observer
    tableObserver = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            if (mutation.type === 'childList') {
                // Check if a table was added
                const addedTable = Array.from(mutation.addedNodes).find(
                    node => node.nodeType === 1 &&
                        (node.classList?.contains('search-table') || node.querySelector?.('.search-table'))
                );

                if (addedTable) {
                    // Table was added, refresh dropdown if it's open
                    setTimeout(() => {
                        refreshDropdownIfOpen();
                        if (typeof refreshSearchColumnResizers === 'function') {
                            refreshSearchColumnResizers();
                        }
                    }, 200);
                }
            }
        });
    });

    // Observe the table container
    tableObserver.observe(tableContainer, {
        childList: true,
        subtree: true
    });
}

function initSettingsDropdown() {
    const settingsBtn = document.getElementById('settingsBtn');
    const settingsDropdown = document.getElementById('settingsDropdown');
    const saveDefaultLayoutBtn = document.getElementById('saveDefaultLayoutBtn');
    const saveLayoutBtn = document.getElementById('saveLayoutBtn');
    const resetLayoutBtn = document.getElementById('resetLayoutBtn');
    const chooseColumnsBtn = document.getElementById('chooseColumnsBtn');

    if (!settingsBtn || !settingsDropdown) {
        return;
    }

    // Load saved columns on init
    loadVisibleColumnsFromStorage();
    loadColumnWidthsFromStorage();

    (async () => {
        const role = await fetchSearchPageUserRole();
        if (saveDefaultLayoutBtn) {
            const R = typeof RoleUtils !== 'undefined' ? RoleUtils : null;
            const isSa = R ? R.isSuperAdminRole(role) : false;
            saveDefaultLayoutBtn.style.display = isSa ? 'flex' : 'none';
        }
    })();

    // Initialize export menu visibility based on current category
    // (updateExportButtonVisibility will handle showing/hiding the appropriate menu)
    const activeCategory = getActiveCategoryWithFallback();
    if (activeCategory) {
        updateExportButtonVisibility(activeCategory);
    }

    // Initialize table observer
    initTableObserver();

    // Re-initialize observer if DOM changes
    setTimeout(() => {
        initTableObserver();
    }, 1000);

    // Toggle dropdown
    settingsBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        settingsDropdown.classList.toggle('show');
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        // Check if click is outside settings dropdown
        if (!settingsBtn.contains(e.target) && !settingsDropdown.contains(e.target)) {
            settingsDropdown.classList.remove('show');

            // Also close all submenus when main dropdown is closed
            document.querySelectorAll('.dropdown-submenu').forEach(submenu => {
                submenu.classList.remove('active');
            });
        } else {
            // If click is inside settings dropdown, check submenus
            const allSubmenus = document.querySelectorAll('.dropdown-submenu');
            allSubmenus.forEach(submenu => {
                // Check if click is outside this submenu and its content
                if (!submenu.contains(e.target)) {
                    submenu.classList.remove('active');
                }
            });
        }
    });

    if (saveLayoutBtn) {
        saveLayoutBtn.addEventListener('click', (e) => handleSaveLayoutClick(e, settingsDropdown));
    }
    if (saveDefaultLayoutBtn) {
        saveDefaultLayoutBtn.addEventListener('click', (e) => handleSaveDefaultLayoutClick(e, settingsDropdown));
    }
    if (resetLayoutBtn) {
        resetLayoutBtn.addEventListener('click', (e) => handleResetLayoutClick(e, settingsDropdown));
    }

    if (typeof initSearchColumnResizers === 'function') {
        initSearchColumnResizers();
    }

    [['saveDefaultLayoutBtn', 'hint.saveDefaultLayout'], ['saveLayoutBtn', 'hint.saveLayout'], ['resetLayoutBtn', 'hint.resetLayout']].forEach(([rowId, hintKey]) => {
        const rowEl = document.getElementById(rowId);
        const hintEl = rowEl && rowEl.querySelector('.settings-layout-hint');
        if (hintEl) {
            const t = window.I18n && typeof window.I18n.t === 'function' ? window.I18n.t(hintKey) : '';
            hintEl.setAttribute('title', t || '');
        }
    });

    // Helper function to open and populate submenu
    async function openAndPopulateSubmenu() {
        const submenu = chooseColumnsBtn.closest('.dropdown-submenu');
        if (!submenu) {
            return;
        }

        // Active class is already added before calling this function

        // Show loading state immediately
        const columnsList = document.getElementById('columnsCheckboxes');
        if (columnsList) {
            columnsList.innerHTML = `<div class="column-checkbox-item" style="padding: 1rem; text-align: center; color: var(--text-secondary);"><i class="fas fa-spinner fa-spin"></i> ${window.I18n?.t('message.loadingColumns') || 'Loading columns...'}</div>`;
        }

        // Wait for table to be ready with longer timeout
        const columns = await waitForTableColumns(3000);

        // Now populate with the columns we found
        if (columns.length > 0) {
            await populateColumnsDropdown();
        } else {
            // Still no columns, try one more time after a delay
            setTimeout(async () => {
                await populateColumnsDropdown();
            }, 500);
        }
    }

    // Choose columns functionality - Click event only
    if (chooseColumnsBtn) {
        chooseColumnsBtn.addEventListener('click', async (e) => {
            e.preventDefault();
            e.stopPropagation();

            const submenu = chooseColumnsBtn.closest('.dropdown-submenu');
            if (submenu) {
                const isCurrentlyActive = submenu.classList.contains('active');
                if (isCurrentlyActive) {
                    // Close the submenu
                    submenu.classList.remove('active');
                } else {
                    // Close other submenus first
                    document.querySelectorAll('.dropdown-submenu').forEach(sm => {
                        if (sm !== submenu) sm.classList.remove('active');
                    });
                    // Open and populate the submenu
                    submenu.classList.add('active');
                    await openAndPopulateSubmenu();
                }
            }
        });
    }

    // Export functionality - Click event to toggle submenu
    const exportBtn = document.getElementById('exportBtn');
    if (exportBtn) {
        exportBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();

            const submenu = exportBtn.closest('.dropdown-submenu');
            if (submenu) {
                const isCurrentlyActive = submenu.classList.contains('active');
                if (isCurrentlyActive) {
                    // Close the submenu
                    submenu.classList.remove('active');
                } else {
                    // Close other submenus first
                    document.querySelectorAll('.dropdown-submenu').forEach(sm => {
                        if (sm !== submenu) sm.classList.remove('active');
                    });
                    // Open the submenu
                    submenu.classList.add('active');
                }
            }
        });
    }
}

async function populateColumnsDropdown(forceRefresh = false) {
    const columnsList = document.getElementById('columnsCheckboxes');
    if (!columnsList) {
        return;
    }

    const activeCategory = getActiveCategoryWithFallback();

    if (!activeCategory) {
        columnsList.innerHTML = `<div class="column-checkbox-item" style="padding: 1rem; text-align: center; color: var(--text-secondary);">${window.I18n?.t('message.noCategorySelected') || 'No category selected'}</div>`;
        return;
    }

    // Get all available columns from current table
    let allColumns = getCurrentTableColumns();

    // If no columns found, wait for table to be ready
    if (allColumns.length === 0) {
        // Only show loading if not already showing
        if (!columnsList.innerHTML.includes('fa-spinner')) {
            columnsList.innerHTML = `<div class="column-checkbox-item" style="padding: 1rem; text-align: center; color: var(--text-secondary);"><i class="fas fa-spinner fa-spin"></i> ${window.I18n?.t('message.loadingColumns') || 'Loading columns...'}</div>`;
        }

        // Wait for table columns with promise-based approach
        allColumns = await waitForTableColumns(3000);

        // Double check - sometimes columns appear after a small delay
        if (allColumns.length === 0) {
            // Try one more time after a short delay
            await new Promise(resolve => setTimeout(resolve, 300));
            allColumns = getCurrentTableColumns();
        }

        if (allColumns.length === 0) {
            columnsList.innerHTML = `<div class="column-checkbox-item" style="padding: 1rem; text-align: center; color: var(--text-secondary);">${window.I18n?.t('message.noColumnsAvailable') || 'No columns available. Please load data first.'}</div>`;
            return;
        }
    }

    // Columns found, render
    renderColumnsCheckboxes(columnsList, activeCategory, allColumns);
}

function renderColumnsCheckboxes(columnsList, activeCategory, allColumns) {
    if (!columnsList || !activeCategory || !allColumns || allColumns.length === 0) {
        return;
    }

    // Get visible columns for this category (or default to default columns)
    let visibleColumns = getVisibleColumnsForCategory(activeCategory);
    const normalizedCategory = categoryToModule(activeCategory);
    if (!visibleColumns || visibleColumns.length === 0) {
        // First time - use default columns from UNISON_DEFAULTS
        const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(activeCategory);
        if (defaultsFromDB && defaultsFromDB.length > 0) {
            visibleColumns = defaultsFromDB.filter(col => allColumns.includes(col));
        } else {
            // Fallback to hardcoded defaults if UNISON_DEFAULTS not available
            if (normalizedCategory === 'dataset' && typeof DATASET_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = DATASET_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'attribute' && typeof ATTRIBUTE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = ATTRIBUTE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'system' && typeof SYSTEM_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = SYSTEM_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'glossary' && typeof GLOSSARY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = GLOSSARY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'people' && typeof PEOPLE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = PEOPLE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'role' && typeof ROLE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = ROLE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'business-area' && typeof BUSINESS_AREA_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = BUSINESS_AREA_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'client' && typeof CLIENT_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = CLIENT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'committee' && typeof COMMITTEE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = COMMITTEE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'policy' && typeof POLICY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = POLICY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'process' && typeof PROCESS_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = PROCESS_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'interface' && typeof INTERFACE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = INTERFACE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'capability' && typeof CAPABILITY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = CAPABILITY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'legal-entity' && typeof LEGAL_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = LEGAL_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'orgunit' && typeof ORGUNIT_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = ORGUNIT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'product' && typeof PRODUCT_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = PRODUCT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'geography' && typeof GEOGRAPHY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = GEOGRAPHY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'regulation' && typeof REGULATION_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = REGULATION_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'regulator' && typeof REGULATOR_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = REGULATOR_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'regulatory-theme' && typeof REGULATORY_THEME_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = REGULATORY_THEME_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else {
                visibleColumns = [...allColumns];
            }
        }
    } else {
        // Filter to only include columns that exist in current table
        visibleColumns = visibleColumns.filter(col => allColumns.includes(col));
        // If no valid columns after filtering, use defaults again
        if (visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(activeCategory);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => allColumns.includes(col));
            } else {
                visibleColumns = [...allColumns];
            }
        }
    }

    // Generate checkboxes for all columns
    const checkboxesHTML = allColumns.map(columnKey => {
        const isVisible = visibleColumns.includes(columnKey);
        const label = prettifyLabel(columnKey);
        // Escape single quotes in columnKey for use in onchange
        const safeColumnKey = columnKey.replace(/'/g, "\\'");
        return `
            <label class="column-checkbox-item" style="display: flex; align-items: center; padding: 0.5rem 1rem; cursor: pointer; white-space: nowrap;">
                <input type="checkbox" 
                       value="${safeColumnKey}" 
                       ${isVisible ? 'checked' : ''} 
                       onchange="toggleSearchColumn('${safeColumnKey}')"
                       style="margin-right: 0.5rem; cursor: pointer;">
                <span>${label}</span>
            </label>
        `;
    }).join('');

    columnsList.innerHTML = `
        <div class="column-checkbox-item" style="padding: 0.75rem 1rem; border-bottom: 1px solid var(--border-color); background: #f9fafb; font-weight: 600; display: flex; justify-content: space-between; align-items: center;">
            <span>${window.I18n?.t('label.selectColumns') || 'Select Columns'}</span>
            <button type="button" 
                    onclick="resetSearchColumnsToDefaults()"
                    style="background: none; border: none; color: #248567; cursor: pointer; font-size: 0.875rem; text-decoration: underline; padding: 0;">
                ${window.I18n?.t('button.showAll') || 'Show All'}
            </button>
        </div>
        ${checkboxesHTML}
    `;
}

// Toggle column visibility
function toggleSearchColumn(columnKey) {
    const activeCategory = getActiveCategoryWithFallback();
    if (!activeCategory) return;

    // Get current visible columns
    const allColumns = getCurrentTableColumns();

    // ALWAYS get current state from checkboxes if dropdown is open
    // This ensures we work with the actual current UI state, not stale saved state
    let visibleColumns = null;
    const checkboxes = document.querySelectorAll('#columnsCheckboxes input[type="checkbox"]');
    if (checkboxes.length > 0) {
        // Get current state from checkboxes - need to account for the one being toggled
        // The checkbox has already toggled when onchange fires, so we need to reverse it
        const clickedCheckbox = Array.from(checkboxes).find(cb => cb.value === columnKey);
        const currentCheckedState = clickedCheckbox ? clickedCheckbox.checked : false;

        // Get all checked checkboxes, but reverse the state of the clicked one
        visibleColumns = Array.from(checkboxes)
            .filter(cb => {
                if (cb.value === columnKey) {
                    // Return the OLD state (opposite of current)
                    return !currentCheckedState;
                }
                return cb.checked;
            })
            .map(cb => cb.value);
        
    }
    
    // If no checkboxes found (dropdown not open), try to get from saved state or table
    if (!visibleColumns || visibleColumns.length === 0) {
        // Try saved state
        visibleColumns = getVisibleColumnsForCategory(activeCategory);
        
        // If no saved state, check table visibility
        if (!visibleColumns || visibleColumns.length === 0) {
            const table = document.querySelector('.search-table');
            if (table) {
                visibleColumns = Array.from(table.querySelectorAll('th[data-column]'))
                    .filter(th => th.style.display !== 'none')
                    .map(th => th.getAttribute('data-column'))
                    .filter(col => col != null && col.trim() !== '');
            }
        }
        
        // If still no visible columns, use defaults from UNISON_DEFAULTS
        if (!visibleColumns || visibleColumns.length === 0) {
            const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(activeCategory);
            if (defaultsFromDB && defaultsFromDB.length > 0) {
                visibleColumns = defaultsFromDB.filter(col => allColumns.includes(col));
            } else {
                // Fallback to hardcoded defaults if UNISON_DEFAULTS not available
                const normalizedCategory = categoryToModule(activeCategory);
                if (normalizedCategory === 'dataset' && typeof DATASET_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = DATASET_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'attribute' && typeof ATTRIBUTE_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = ATTRIBUTE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'system' && typeof SYSTEM_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = SYSTEM_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'glossary' && typeof GLOSSARY_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = GLOSSARY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'people' && typeof PEOPLE_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = PEOPLE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'business-area' && typeof BUSINESS_AREA_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = BUSINESS_AREA_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'client' && typeof CLIENT_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = CLIENT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'committee' && typeof COMMITTEE_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = COMMITTEE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'policy' && typeof POLICY_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = POLICY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'process' && typeof PROCESS_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = PROCESS_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'interface' && typeof INTERFACE_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = INTERFACE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'capability' && typeof CAPABILITY_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = CAPABILITY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'legal-entity' && typeof LEGAL_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = LEGAL_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'orgunit' && typeof ORGUNIT_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = ORGUNIT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'product' && typeof PRODUCT_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = PRODUCT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'geography' && typeof GEOGRAPHY_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = GEOGRAPHY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'regulation' && typeof REGULATION_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = REGULATION_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'regulator' && typeof REGULATOR_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = REGULATOR_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else if (normalizedCategory === 'regulatory-theme' && typeof REGULATORY_THEME_DEFAULT_COLUMNS !== 'undefined') {
                    visibleColumns = REGULATORY_THEME_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
                } else {
                    // Last resort: use all columns
                    visibleColumns = [...allColumns];
                }
            }
        }
    }

    // Ensure visibleColumns is an array and filter to only include columns that exist
    if (!Array.isArray(visibleColumns)) {
        visibleColumns = [];
    }
    visibleColumns = visibleColumns.filter(col => allColumns.includes(col));

    // Toggle the column
    const index = visibleColumns.indexOf(columnKey);
    if (index > -1) {
        // Remove column
        visibleColumns.splice(index, 1);
    } else {
        // Add column while maintaining order
        const allColumnIndex = allColumns.indexOf(columnKey);
        if (allColumnIndex > -1) {
            // Insert at the correct position in allColumns order
            const newVisibleColumns = [];
            for (let i = 0; i < allColumns.length; i++) {
                const col = allColumns[i];
                if (col === columnKey || visibleColumns.includes(col)) {
                    newVisibleColumns.push(col);
                }
            }
            visibleColumns = newVisibleColumns;
        } else {
            visibleColumns.push(columnKey);
        }
    }

    // Save updated visible columns
    setVisibleColumnsForCategory(activeCategory, visibleColumns);

    // Update table display
    updateTableColumnVisibility(activeCategory);
}

function applyStoredColumnWidths(category) {
    if (category == null || category === '') {
        category = typeof getActiveCategoryWithFallback === 'function' ? getActiveCategoryWithFallback() : null;
    }
    if (!category) {
        return;
    }
    console.log('[RESIZE][apply] ' + JSON.stringify({ category: category, widths: getColumnWidthsForCategory(category) }));
    if (window.searchTableWidths && typeof window.searchTableWidths.loadWidthsFromStore === 'function') {
        window.searchTableWidths.loadWidthsFromStore(category);
    }
}

async function fetchSearchPageUserRole() {
    try {
        const r = await fetch('/api/me', { credentials: 'include' });
        if (!r.ok) {
            return null;
        }
        const d = await r.json();
        return d.role || null;
    } catch (err) {
        return null;
    }
}

function getVisibleColumnsOrderedFromDom() {
    const table = document.querySelector('.search-table');
    if (!table) {
        return [];
    }
    return Array.from(table.querySelectorAll('thead th[data-column]'))
        .filter(th => th.style.display !== 'none')
        .map(th => th.getAttribute('data-column'))
        .filter(col => col != null && col.trim() !== '');
}

async function handleSaveLayoutClick(e, settingsDropdown) {
    e.preventDefault();
    e.stopPropagation();
    const category = getActiveCategoryWithFallback();
    if (!category) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.noCategorySelected') || 'No category selected', 'warning');
        }
        return;
    }
    await waitForTableColumns(3000);
    let visibleColumns = getVisibleColumnsOrderedFromDom();
    if (visibleColumns.length === 0) {
        visibleColumns = getVisibleColumnsForCategory(category) || [];
    }
    const allColumns = getCurrentTableColumns();
    visibleColumns = visibleColumns.filter(col => allColumns.includes(col));
    if (visibleColumns.length === 0) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.noColumnsAvailable') || 'No columns available', 'warning');
        }
        return;
    }
    setVisibleColumnsForCategory(category, visibleColumns);
    const widths = getColumnWidthsForCategory(category) || {};
    const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
    if (isLoggedIn) {
        await saveColumnPreferencesToDatabase(category, visibleColumns, widths);
    }
    saveVisibleColumnsToStorage();
    saveColumnWidthsToStorage();
    if (settingsDropdown) {
        settingsDropdown.classList.remove('show');
    }
    if (typeof showToast === 'function') {
        showToast(window.I18n?.t('message.layoutSaved') || 'Layout saved', 'success');
    }
}

async function handleSaveDefaultLayoutClick(e, settingsDropdown) {
    e.preventDefault();
    e.stopPropagation();
    const category = getActiveCategoryWithFallback();
    if (!category) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.noCategorySelected') || 'No category selected', 'warning');
        }
        return;
    }
    await waitForTableColumns(3000);
    const normalizedCategory = typeof categoryToModule === 'function' ? categoryToModule(category) : category;
    const facetId = window.moduleNameToFacetId ?
        window.moduleNameToFacetId(normalizedCategory) :
        String(normalizedCategory).toUpperCase();
    const defRes = await fetch('/api/unison/defaults', {
        method: 'GET',
        credentials: 'include',
        headers: { 'Accept': 'application/json' }
    });
    if (defRes.status === 403) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.forbidden') || 'Forbidden', 'error');
        }
        return;
    }
    if (!defRes.ok) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.loadDefaultsFailed') || 'Could not load defaults', 'error');
        }
        return;
    }
    const defJson = await defRes.json();
    const defaults = defJson.data;
    if (!defaults || !Array.isArray(defaults.facets)) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.invalidDefaults') || 'Invalid defaults', 'error');
        }
        return;
    }
    let visibleColumns = getVisibleColumnsOrderedFromDom();
    if (visibleColumns.length === 0) {
        visibleColumns = getVisibleColumnsForCategory(category) || [];
    }
    const allColumns = getCurrentTableColumns();
    visibleColumns = visibleColumns.filter(col => allColumns.includes(col));
    if (visibleColumns.length === 0) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.noColumnsAvailable') || 'No columns available', 'warning');
        }
        return;
    }
    const idx = defaults.facets.findIndex(f => f.id === facetId);
    if (idx < 0) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.facetNotInDefaults') || 'Facet not found in defaults', 'error');
        }
        return;
    }
    defaults.facets[idx].activeFields = visibleColumns.join(',');
    const w = getColumnWidthsForCategory(category) || {};
    defaults.facets[idx].columnWidths = Object.assign({}, w);
    const saveRes = await fetch('/api/unison/defaults/save', {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        },
        body: JSON.stringify(defaults)
    });
    if (!saveRes.ok) {
        if (typeof showToast === 'function') {
            showToast(window.I18n?.t('message.saveDefaultsFailed') || 'Could not save defaults', 'error');
        }
        return;
    }
    if (window.searchColumnControl && typeof window.searchColumnControl.invalidateUnisonDefaultsCache === 'function') {
        window.searchColumnControl.invalidateUnisonDefaultsCache();
    }
    if (typeof loadUnisonDefaultsForColumns === 'function') {
        await loadUnisonDefaultsForColumns();
    }
    if (settingsDropdown) {
        settingsDropdown.classList.remove('show');
    }
    if (typeof showToast === 'function') {
        showToast(window.I18n?.t('message.defaultLayoutSaved') || 'Default layout saved', 'success');
    }
}

async function handleResetLayoutClick(e, settingsDropdown) {
    e.preventDefault();
    e.stopPropagation();
    const category = getActiveCategoryWithFallback();
    const isLoggedIn = typeof window.isUserLoggedIn === 'function' ? window.isUserLoggedIn() : false;
    if (isLoggedIn) {
        const r = await fetch('/api/unison/facets/reset', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Accept': 'application/json' }
        });
        if (!r.ok) {
            if (typeof showToast === 'function') {
                showToast(window.I18n?.t('message.resetLayoutFailed') || 'Could not reset layout', 'error');
            }
            return;
        }
        const fr = await fetch('/api/unison/facets', {
            method: 'GET',
            credentials: 'include',
            headers: { 'Accept': 'application/json' }
        });
        if (fr.ok) {
            const result = await fr.json();
            mergeFacetsApiResponseIntoPreferences(result);
        }
    } else {
        try {
            sessionStorage.removeItem('unisionSearch_visibleColumns');
            sessionStorage.removeItem(COLUMN_WIDTHS_STORAGE_KEY);
        } catch (err) {
            /* ignore */
        }
        visibleColumnsByCategory = {};
        columnWidthsByCategory = {};
        if (typeof loadUnisonDefaultsForColumns === 'function') {
            await loadUnisonDefaultsForColumns();
        }
        if (category) {
            await resetToDefaultsForCategory(category);
            const defW = window.searchColumnControl?.getDefaultColumnWidthsFromUnisonDefaults?.(category);
            if (defW && typeof defW === 'object') {
                setColumnWidthsForCategory(category, defW);
            } else {
                setColumnWidthsForCategory(category, {});
            }
        }
    }
    if (category) {
        updateTableColumnVisibility(category);
        applyStoredColumnWidths(category);
        if (typeof refreshSearchColumnResizers === 'function') {
            refreshSearchColumnResizers();
        }
        refreshDropdownIfOpen();
    }
    if (settingsDropdown) {
        settingsDropdown.classList.remove('show');
    }
    if (typeof showToast === 'function') {
        showToast(window.I18n?.t('message.layoutReset') || 'Layout reset', 'success');
    }
}

// Update table column visibility
function updateTableColumnVisibility(category) {
    const table = document.querySelector('.search-table');
    if (!table) return;

    const allColumns = getCurrentTableColumns();
    if (allColumns.length === 0) return;

    let visibleColumns = getVisibleColumnsForCategory(category);
    const normalizedCategory = categoryToModule(category);

    // If no saved columns in sessionStorage, use default columns from UNISON_DEFAULTS
    if (!visibleColumns || visibleColumns.length === 0) {
        // Get defaults from UNISON_DEFAULTS
        const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
        if (defaultsFromDB && defaultsFromDB.length > 0) {
            visibleColumns = defaultsFromDB.filter(col => allColumns.includes(col));
        } else {
            // Fallback to hardcoded defaults if UNISON_DEFAULTS not available
            if (normalizedCategory === 'dataset' && typeof DATASET_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = DATASET_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'attribute' && typeof ATTRIBUTE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = ATTRIBUTE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'system' && typeof SYSTEM_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = SYSTEM_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'glossary' && typeof GLOSSARY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = GLOSSARY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'people' && typeof PEOPLE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = PEOPLE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'role' && typeof ROLE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = ROLE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'business-area' && typeof BUSINESS_AREA_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = BUSINESS_AREA_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'client' && typeof CLIENT_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = CLIENT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'committee' && typeof COMMITTEE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = COMMITTEE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'policy' && typeof POLICY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = POLICY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'process' && typeof PROCESS_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = PROCESS_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'interface' && typeof INTERFACE_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = INTERFACE_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'capability' && typeof CAPABILITY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = CAPABILITY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'legal-entity' && typeof LEGAL_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = LEGAL_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'orgunit' && typeof ORGUNIT_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = ORGUNIT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'product' && typeof PRODUCT_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = PRODUCT_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'geography' && typeof GEOGRAPHY_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = GEOGRAPHY_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'regulation' && typeof REGULATION_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = REGULATION_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'regulator' && typeof REGULATOR_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = REGULATOR_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else if (normalizedCategory === 'regulatory-theme' && typeof REGULATORY_THEME_DEFAULT_COLUMNS !== 'undefined') {
                visibleColumns = REGULATORY_THEME_DEFAULT_COLUMNS.filter(col => allColumns.includes(col));
            } else {
                visibleColumns = [...allColumns];
            }
        }
        // Don't save defaults to sessionStorage - only save when user makes changes
    }

    // Filter to only include columns that actually exist in the table
    visibleColumns = visibleColumns.filter(col => allColumns.includes(col));

    // If no valid columns after filtering, use defaults again
    if (visibleColumns.length === 0) {
        const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
        if (defaultsFromDB && defaultsFromDB.length > 0) {
            visibleColumns = defaultsFromDB.filter(col => allColumns.includes(col));
        } else {
            // Fallback to all columns if no defaults available
            visibleColumns = [...allColumns];
        }
    }

    allColumns.forEach(columnKey => {
        const isVisible = visibleColumns.includes(columnKey);
        const displayValue = isVisible ? 'table-cell' : 'none';

        // Update header cells
        const headerCells = table.querySelectorAll(`th[data-column="${columnKey}"]`);
        headerCells.forEach(cell => {
            cell.style.display = displayValue;
        });

        // Update data cells
        const dataCells = table.querySelectorAll(`td[data-column="${columnKey}"]`);
        dataCells.forEach(cell => {
            cell.style.display = displayValue;
        });
    });

    // Update checkbox states in dropdown if it's open
    const checkboxes = document.querySelectorAll('#columnsCheckboxes input[type="checkbox"]');
    if (checkboxes.length > 0) {
        checkboxes.forEach(checkbox => {
            const columnKey = checkbox.value;
            checkbox.checked = visibleColumns.includes(columnKey);
        });
    }

    applyStoredColumnWidths(category);
    if (typeof refreshSearchColumnResizers === 'function') {
        refreshSearchColumnResizers();
    }
}

// Reset to show all columns (defaults)
async function resetToDefaultsForCategory(category) {
    if (!category) return;

    // Wait for table to be ready
    let allColumns = getCurrentTableColumns();

    if (allColumns.length === 0) {
        // Wait for columns to appear
        allColumns = await waitForTableColumns(3000);

        if (allColumns.length === 0) {
            return;
        }
    }

    // ALWAYS get defaults from UNISON_DEFAULTS (no hardcoded fallback)
    let columnsToShow;
    const normalizedCategory = categoryToModule(category);

    // Get defaults from UNISON_DEFAULTS
    const defaultsFromDB = window.searchColumnControl?.getDefaultColumnsFromUnisonDefaults?.(category);
    if (defaultsFromDB && defaultsFromDB.length > 0) {
        columnsToShow = defaultsFromDB.filter(col => allColumns.includes(col));
    } else {
        console.error('[SETTINGS] NO DATABASE DEFAULTS for category:', category);
        console.error('[SETTINGS] Check UNISON_DEFAULTS in app_config table');

        // Special fallback for Active Tasks
        if (category === 'active-tasks' || normalizedCategory === 'activeTasks') {
            // Default columns for Active Tasks based on activeFields in database
            const activeTasksDefaults = ['name', 'title', 'objectType', 'object', 'dueDate', 'dueInDays', 'owner'];
            columnsToShow = activeTasksDefaults.filter(col => allColumns.includes(col));
            if (columnsToShow.length === 0) {
                // If no matches, use all columns
                columnsToShow = [...allColumns];
            }
            console.warn('[SETTINGS] Using Active Tasks fallback defaults:', columnsToShow);
        } else {
            // Show all columns as emergency fallback for other categories
            columnsToShow = [...allColumns];
            console.warn('[SETTINGS] Showing ALL columns as emergency fallback');
        }
    }

    setVisibleColumnsForCategory(category, columnsToShow);

    // Update table display
    updateTableColumnVisibility(category);

    // Reload dropdown with a small delay to ensure DOM is updated
    setTimeout(async () => {
        await populateColumnsDropdown(true);
    }, 100);
}

// Global function for reset button - Show ALL columns
async function resetSearchColumnsToDefaults() {
    const activeCategory = getActiveCategoryWithFallback();
    if (!activeCategory) {
        console.warn('[SETTINGS] No active category found');
        return;
    }



    // Wait for table to be ready
    let allColumns = getCurrentTableColumns();

    if (allColumns.length === 0) {
        // Wait for columns to appear
        allColumns = await waitForTableColumns(3000);

        if (allColumns.length === 0) {
            console.warn('[SETTINGS] No columns found in table');
            return;
        }
    }



    // Set all columns as visible
    setVisibleColumnsForCategory(activeCategory, allColumns);

    // Update table display
    updateTableColumnVisibility(activeCategory);

    // Refresh dropdown to update checkboxes
    setTimeout(async () => {
        await populateColumnsDropdown(true);
    }, 100);
}

// Apply saved column visibility when table is rendered
async function applySavedColumnVisibility(category) {
    if (!category) {
        return;
    }

    // Wait for table to be ready with stable columns
    const columns = await waitForTableColumns(3000);

    // Only proceed if we have columns
    if (columns.length === 0) {
        // Try one more time after a delay
        setTimeout(async () => {
            const retryColumns = await waitForTableColumns(2000);
            if (retryColumns.length > 0) {
                updateTableColumnVisibility(category);
                refreshDropdownIfOpen();
                if (typeof refreshSearchColumnResizers === 'function') {
                    refreshSearchColumnResizers();
                }
            }
        }, 500);
        return;
    }

    // Small delay to ensure table is fully rendered
    setTimeout(() => {
        updateTableColumnVisibility(category);
        refreshDropdownIfOpen();
        if (typeof refreshSearchColumnResizers === 'function') {
            refreshSearchColumnResizers();
        }
    }, 100);
}

// Helper function to refresh dropdown if open
function refreshDropdownIfOpen() {
    const submenu = document.querySelector('.dropdown-submenu');
    if (submenu && submenu.classList.contains('active')) {
        populateColumnsDropdown(true);
    }
}

function initHelpButton() {
    // Placeholder for future implementation
}

// ============================================================================
// RELATED OBJECTS SETTINGS
// ============================================================================

/**
 * Toggle related objects feature
 */
function toggleRelatedObjects(enabled) {
    try {
        // Save to localStorage
        localStorage.setItem('unison_relatedObjects_enabled', enabled ? 'true' : 'false');
        
        // Send to server (if admin)
        fetch('/api/unison-search/related-objects/config', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                enabled: enabled
            })
        }).then(response => {
            if (response.ok) {
            }
        }).catch(err => {
            console.warn('[RelatedObjectsSettings] Could not update server config:', err.message);
        });
        
        
        // Optionally reload search results to apply changes
        if (typeof executeCurrentSearch === 'function') {
            executeCurrentSearch();
        }
        
    } catch (e) {
        console.error('[RelatedObjectsSettings] Error toggling related objects:', e);
    }
}

/**
 * Get current related objects setting
 */
function getRelatedObjectsEnabled() {
    try {
        const stored = localStorage.getItem('unison_relatedObjects_enabled');
        // Default to true if not set
        return stored !== 'false';
    } catch (e) {
        return true;
    }
}

// Export functions
if (typeof window !== 'undefined') {
    window.toggleSearchColumn = toggleSearchColumn;
    window.resetSearchColumnsToDefaults = resetSearchColumnsToDefaults;
    window.applySavedColumnVisibility = applySavedColumnVisibility;
    window.applyStoredColumnWidths = applyStoredColumnWidths;
    window.toggleRelatedObjects = toggleRelatedObjects;
    window.getRelatedObjectsEnabled = getRelatedObjectsEnabled;

    window.onSearchColumnWidthCommitted = function (category, columnKey, widthPx) {
        console.log('[RESIZE][commit] ' + JSON.stringify({ category: category, columnKey: columnKey, widthPx: widthPx }));
        if (!category || !columnKey) {
            return;
        }
        if (!columnWidthsByCategory[category]) {
            columnWidthsByCategory[category] = {};
        }
        columnWidthsByCategory[category][columnKey] = widthPx;
        saveColumnWidthsToStorage();
        console.log('[RESIZE][stored] ' + JSON.stringify(columnWidthsByCategory[category]));
        try {
            var th = document.querySelector('.search-table th[data-column="' + (CSS && CSS.escape ? CSS.escape(columnKey) : columnKey) + '"]');
            if (th) {
                var cs = window.getComputedStyle(th);
                console.log('[RESIZE][th after commit] ' + JSON.stringify({
                    col: columnKey,
                    offsetWidth: th.offsetWidth,
                    computedWidth: cs.width,
                    computedMinWidth: cs.minWidth,
                    computedMaxWidth: cs.maxWidth
                }));
            }
            var styleEl = document.getElementById('search-table-col-widths');
            if (styleEl) {
                console.log('[RESIZE][stylesheet length] ' + styleEl.textContent.length + ' chars');
            }
        } catch (e) { /* noop */ }
    };

    // Debug functions disabled
}


