// Search Functionality and suggestions

// Global array to store search conditions
let searchConditions = [];
// Also make it available on window for cross-file access
if (typeof window !== 'undefined') {
    window.searchConditions = searchConditions;
}

// Track whether a search was explicitly committed (Enter / Search button)
let isSearchCommitted = false;

// Loop prevention flags for loadCategoryData
let isLoadingCategory = false;
let loadingCategoryName = null;

// Flag to prevent category-switch handlers from re-fetching data while a search is executing
// (updateAllFacets already handles rendering the active category during a search)
let isSearchExecuting = false;

// Re-entry guard for loadCategoryDataFromUnisonResults
let isLoadingFromUnisonResults = false;
let loadingFromUnisonResultsCategory = null;

/** When set, invoked with `false` to cancel without saving (e.g. opening another inline editor). */
let activeConditionInlineFinish = null;

// Monotonic search token: only the latest response may update state (prevents stale overrides)
let currentSearchToken = 0;

// Store current suggestions data for Enter key handling
let currentSuggestionsData = null;
let currentSuggestionsCategory = null;

// Hierarchical filter options for parent-child relationships
let hierarchicalFilterOptions = {
    childInclusion: 'none', // 'none', 'immediate', 'all'
    applyFilters: 'no-apply' // 'no-apply', 'apply'
};
// Make it available globally
if (typeof window !== 'undefined') {
    window.hierarchicalFilterOptions = hierarchicalFilterOptions;
}

// List of hierarchical facets that support parent-child relationships
const HIERARCHICAL_FACETS = ['GLOSSARY', 'PROCESS', 'POLICY', 'CAPABILITY'];

/**
 * Mobile drawer: toggle .category-sidebar.open (CSS transform). No-op if elements missing.
 */
function initMobileCategorySidebarToggle() {
    const toggle = document.getElementById('mobileCategoryToggle') || document.querySelector('.mobile-category-toggle');
    const sidebar = document.querySelector('.category-sidebar');
    if (!toggle || !sidebar) return;

    const setOpen = (open) => {
        sidebar.classList.toggle('open', !!open);
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    };

    toggle.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        setOpen(!sidebar.classList.contains('open'));
    });

    document.addEventListener('click', (e) => {
        if (!sidebar.classList.contains('open')) return;
        if (toggle.contains(e.target)) return;
        if (sidebar.contains(e.target)) return;
        setOpen(false);
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && sidebar.classList.contains('open')) {
            setOpen(false);
        }
    });

    window.addEventListener('resize', () => {
        if (window.innerWidth > 768) {
            setOpen(false);
        }
    });

    const container = sidebar.querySelector('.category-sidebar-container');
    if (container) {
        container.addEventListener('click', (e) => {
            if (e.target.closest('.category-item') && window.innerWidth <= 768) {
                setOpen(false);
            }
        });
    }
}

function initSearchFunctionality() {
    const searchInput = document.querySelector('.search-main-input');
    const searchBtn = document.querySelector('.search-action-btn');

    if (searchInput && searchBtn) {
        searchBtn.addEventListener('click', () => performSearch());
        searchInput.addEventListener('keypress', async e => {
            if (e.key === 'Enter') {
                // Check if suggestions dropdown is visible and has data
                const dropdown = document.querySelector('.search-suggestions');
                if (dropdown && dropdown.style.display !== 'none' && currentSuggestionsData && currentSuggestionsData.length > 0) {
                    // Display all datasets from suggestions instead of filtered search
                    e.preventDefault();
                    const category = currentSuggestionsCategory || getActiveCategoryWithFallback();
                    const userQuery = searchInput.value.trim();

                    // Hide suggestions dropdown
                    dropdown.style.display = 'none';

                    // Clear search input
                    searchInput.value = '';

                    // Display all suggestions data
                    if (currentSuggestionsData.length > 0) {
                        isSearchCommitted = true; // So Clear button and counter visibility work after Enter
                        // Add one FIND condition so search-counter-btn appears (counter shows number of conditions)
                        const existingFind = searchConditions.find(c => c.operator === 'FIND' && !c.muted);
                        if (!existingFind) {
                            addSearchCondition('FIND', category, userQuery || '*');
                        }
                        const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;
                        generateTable(currentSuggestionsData, null, category);
                        updateCategoryCount(category, currentSuggestionsData.length, currentSuggestionsData.length, false);
                        updateSearchCounter(); // Show clear button and search-counter-btn after displaying suggestions
                        if (typeof cacheModuleData === 'function') {
                            cacheModuleData(category, currentSuggestionsData, signature);
                        }

                        // Clear filtered data state since we're showing all suggestions
                        currentFilteredData = null;
                        currentFilteredCategory = null;

                        // Clear suggestions data to prevent memory leak
                        currentSuggestionsData = null;
                        currentSuggestionsCategory = null;

                        // Execute full cross-facet search so all sidebar facet counts reflect
                        // the complete keyword search (all matching items), not just the first suggestion.
                        try {
                            await executeMultiConditionSearch();
                        } catch (err) {
                            console.error('[Search Enter] Error executing Unison search:', err);
                        }
                    }
                } else {
                    // No suggestions visible, perform normal search
                    e.preventDefault(); // Prevent form submit so page doesn't reload and clear/counter can appear
                    performSearch();
                }
            }
        });
    }

    // Initialize operator dropdown state
    initializeOperatorDropdown();

    initOrgUnitTable();
    initSearchCounter();
    initFilterButton();
    initMobileCategorySidebarToggle();

    // Enable operators if search conditions already exist (e.g., from saved search or URL params)
    if (typeof searchConditions !== 'undefined' && searchConditions.length > 0) {
        enableOperatorOptions();
        updateSearchCounter();
    }
}

/**
 * Initialize operator dropdown - disable AND/OR/NOT until first FIND search
 */
function initializeOperatorDropdown() {
    const operatorSelect = document.querySelector('.search-type-select');
    if (!operatorSelect) {
        // Retry after a short delay if element not found
        setTimeout(() => {
            const retrySelect = document.querySelector('.search-type-select');
            if (retrySelect) {
                retrySelect.value = 'find';
                disableOperatorOptions();
            }
        }, 100);
        return;
    }

    // Set initial value to FIND
    operatorSelect.value = 'find';

    // Disable AND/OR/NOT options initially
    disableOperatorOptions();
}

// ✅ Ensure operators are initialized when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeOperatorDropdown);
} else {
    // DOM already loaded, initialize immediately
    initializeOperatorDropdown();
}

/**
 * Enable AND/OR/NOT options after first FIND search
 */
function enableOperatorOptions() {
    const operatorSelect = document.querySelector('.search-type-select');
    if (!operatorSelect) return;

    const options = operatorSelect.querySelectorAll('option');
    options.forEach(option => {
        if (option.value !== 'find') {
            option.disabled = false;
        }
    });
}

/**
 * Disable AND/OR/NOT options (only FIND available)
 */
function disableOperatorOptions() {
    const operatorSelect = document.querySelector('.search-type-select');
    if (!operatorSelect) return;

    const options = operatorSelect.querySelectorAll('option');
    options.forEach(option => {
        if (option.value !== 'find') {
            option.disabled = true;
        }
    });
}

/**
 * One delegated listener on the sidebar — modules.js calls this after every re-render; previously
 * each call stacked duplicate per-item listeners and one click fired loadCategoryData N times.
 */
function initOrgUnitTable() {
    if (window.__orgUnitTableDelegated) {
        return;
    }
    const sidebarContainer = document.querySelector('.category-sidebar-container');
    if (!sidebarContainer) {
        return;
    }
    window.__orgUnitTableDelegated = true;

    sidebarContainer.addEventListener('click', async (e) => {
        const item = e.target.closest('.category-item');
        if (!item || !sidebarContainer.contains(item)) {
            return;
        }
        if (e.target.closest('.cat-remove-btn')) {
            return;
        }
        const category = item.getAttribute('data-category');
        if (!category) {
            return;
        }

        const categoryItems = sidebarContainer.querySelectorAll('.category-item');
        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');

        categoryItems.forEach(i => i.classList.remove('active', 'current'));
        item.classList.add('active');

        // Preserve FIND query in search input when switching facets
        const searchInput = document.querySelector('.search-main-input');
        if (searchInput && typeof searchConditions !== 'undefined' && searchConditions.length > 0) {
            const activeConditions = searchConditions.filter(c => !c.muted);
            const findCondition = activeConditions.find(c => c.operator === 'FIND');

            if (findCondition && findCondition.query) {
                searchInput.value = findCondition.query;
            }
        }

        if (typeof isMapViewActive === 'function' && isMapViewActive()) {
            if (typeof reloadMapIfActive === 'function') {
                reloadMapIfActive();
            }
        } else if (isSearchExecuting) {
            if (currentUnisonSearchResults &&
                currentUnisonSearchResults.results &&
                Object.keys(currentUnisonSearchResults.results).length > 0) {
                if (dataTable) dataTable.style.display = 'block';
                updateSearchCounter();
                if (typeof searchConditions !== 'undefined' && searchConditions.length > 0) {
                    enableOperatorOptions();
                }
                await loadCategoryDataFromUnisonResults(category);
            }
        } else {
            if (dataTable) dataTable.style.display = 'block';

            if (currentUnisonSearchResults &&
                currentUnisonSearchResults.results &&
                Object.keys(currentUnisonSearchResults.results).length > 0) {

                const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId :
                    (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
                let facetId = catToFacetId ? catToFacetId(category) : category.toUpperCase();

                if (facetId === 'DATA-SETS' || facetId === 'DATA_SETS') {
                    facetId = 'DATASET';
                }

                const facetResult = currentUnisonSearchResults.results[facetId];
                const hasData = facetResult && (
                    (facetResult.count !== undefined && facetResult.count > 0) ||
                    (Array.isArray(facetResult.rows) && facetResult.rows.length > 0) ||
                    (facetResult.ids && (Array.isArray(facetResult.ids) ? facetResult.ids.length > 0 : facetResult.ids.size > 0))
                );

                if (hasData) {
                    updateSearchCounter();
                    if (typeof searchConditions !== 'undefined' && searchConditions.length > 0) {
                        enableOperatorOptions();
                    }
                    await loadCategoryDataFromUnisonResults(category);
                } else {
                    await loadCategoryData(category);
                }
            } else if (typeof searchConditions !== 'undefined' && searchConditions.length > 0) {
                const activeConditions = searchConditions.filter(c => !c.muted);

                updateSearchCounter();
                if (activeConditions.length > 0) {
                    enableOperatorOptions();
                }

                if (activeConditions.length > 0) {
                    const uniqueFacets = new Set(activeConditions.map(c => {
                        const fid = typeof categoryToFacetId === 'function' ? categoryToFacetId(c.category) : c.category.toUpperCase();
                        return fid;
                    }));
                    const hasFindCondition = activeConditions.some(c => c.operator === 'FIND');
                    const shouldUseUnisonSearch = uniqueFacets.size > 1 || hasFindCondition;

                    if (shouldUseUnisonSearch) {
                        await executeMultiConditionSearch();
                    } else {
                        const conditionsForCategory = activeConditions.filter(c => c.category === category);
                        if (conditionsForCategory.length > 0) {
                            await executeSearchWithConditions(category, conditionsForCategory);
                        } else {
                            await loadCategoryData(category);
                        }
                    }
                } else {
                    await loadCategoryData(category);
                }
            } else {
                await loadCategoryData(category);
            }
        }

        const facetIdForFilters = typeof categoryToFacetId === 'function' ? categoryToFacetId(category) : category.toUpperCase();
        if (typeof loadFilterFields === 'function') {
            await loadFilterFields(facetIdForFilters);
        }

        if (typeof updateHierarchicalFilterVisibility === 'function') {
            updateHierarchicalFilterVisibility();
        }

        if (typeof updateAllFacetIndicatorsFromConditions === 'function') {
            setTimeout(() => {
                updateAllFacetIndicatorsFromConditions();
            }, 100);
        }
    });
}

/**
 * Active Tasks are always loaded from /api/active-tasks for the table.
 * Unison traversal often omits or empties ACTIVE_TASKS, which left the table blank after any search.
 */
function isActiveTasksUnisonCategory(category) {
    if (!category || typeof category !== 'string') {
        return false;
    }
    const normalized = typeof categoryToModule === 'function' ? categoryToModule(category) : '';
    const lower = category.toLowerCase();
    return normalized === 'activeTasks' || lower === 'active-tasks' || lower === 'activetasks' || lower === 'active_tasks';
}

/**
 * Load category data from Unison Search results (filtered data).
 * This is called when user clicks on a facet after Unison Search.
 */
async function loadCategoryDataFromUnisonResults(category) {
    // Re-entry guard: prevent concurrent calls for the same category
    if (isLoadingFromUnisonResults && loadingFromUnisonResultsCategory === category) {
        return;
    }

    if (isActiveTasksUnisonCategory(category)) {
        await loadCategoryData(category);
        return;
    }

    if (!currentUnisonSearchResults || !currentUnisonSearchResults.results) {
        console.warn('[loadCategoryDataFromUnisonResults] No Unison Search results available');
        await loadCategoryData(category);
        return;
    }

    isLoadingFromUnisonResults = true;
    loadingFromUnisonResultsCategory = category;

    const dataTable = tableContainer || document.querySelector('.data-table-wrapper');

    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('loadCategoryDataFromUnisonResults');
        }

        if (dataTable) {
            dataTable.classList.add('loading');
            renderSkeleton(dataTable);
        }

        // Get categoryToFacetId function
        const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId :
            (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);

        // Try multiple facet ID formats to find the result
        let facetId = catToFacetId ? catToFacetId(category) : category.toUpperCase();

        // Normalize: "DATA-SETS" -> "DATASET"
        if (facetId === 'DATA-SETS' || facetId === 'DATA_SETS') {
            facetId = 'DATASET';
        }

        let facetResult = currentUnisonSearchResults.results[facetId];

        const availableFacets = Object.keys(currentUnisonSearchResults.results || {});

        // Check if the found result has actual data (count > 0 or has rows)
        const hasData = facetResult && (
            (facetResult.count !== undefined && facetResult.count > 0) ||
            (Array.isArray(facetResult.rows) && facetResult.rows.length > 0) ||
            (facetResult.ids && (Array.isArray(facetResult.ids) ? facetResult.ids.length > 0 : facetResult.ids.size > 0))
        );

        // If not found or has no data, try alternative formats
        if (!facetResult || !hasData) {
            // If only one facet exists in results (single-facet search), don't fall back to other facets
            if (availableFacets.length === 1 && facetResult && !hasData) {
                showNoDataMessage(category);
                currentFilteredData = null;
                currentFilteredCategory = null;

                // For Active Tasks, use backend total (which will be 0 when no results)
                // For other facets, prefer backend total from facetResult, then fallback to cache
                const isActiveTasks = category && (
                    category.toLowerCase() === 'active-tasks' ||
                    category.toLowerCase() === 'activetasks' ||
                    category.toLowerCase() === 'active_tasks'
                );

                const totalCount = (facetResult && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                    ? facetResult.totalCount : 0;
                if (typeof updateCategoryCount === 'function') {
                    updateCategoryCount(category, 0, totalCount, true);
                }
                return;
            }

            const singular = facetId.endsWith('S') ? facetId.slice(0, -1) : null;
            const plural = !facetId.endsWith('S') ? facetId + 'S' : null;

            // Only allow same-facet variants (singular/plural/normalized); never fall back to other facets
            // Also handle specific known mappings
            const alternatives = [
                singular,
                plural,
                facetId.toUpperCase().replace(/-/g, '_'),  // e.g., "BUSINESS-AREA" -> "BUSINESS_AREA"
                facetId.toUpperCase().replace(/-/g, ''),    // e.g., "BUSINESS-AREA" -> "BUSINESSAREA"
                facetId.toUpperCase(),                      // e.g., "attributes" -> "ATTRIBUTES"
                // Handle specific known mappings
                category.toLowerCase() === 'attributes' ? 'ATTRIBUTE' : null,
                category.toLowerCase() === 'attribute' ? 'ATTRIBUTES' : null,
                category.toLowerCase() === 'data-sets' || category.toLowerCase() === 'dataset' ? 'DATASET' : null,
                category.toLowerCase() === 'business-area' ? 'BUSINESS_AREA' : null,
                category.toLowerCase() === 'legal-entity' ? 'LEGAL_ENTITY' : null,
                category.toLowerCase() === 'regulatory-theme' ? 'REGULATORY_THEME' : null,
                category.toLowerCase() === 'org-unit' ? 'ORG_UNIT' : null
            ].filter(Boolean);

            for (const alt of alternatives) {
                const altResult = currentUnisonSearchResults.results[alt];
                if (altResult) {
                    // Check if this alternative has data
                    const altHasData = (altResult.count !== undefined && altResult.count > 0) ||
                        (Array.isArray(altResult.rows) && altResult.rows.length > 0) ||
                        (altResult.ids && (Array.isArray(altResult.ids) ? altResult.ids.length > 0 : altResult.ids.size > 0));
                    if (altHasData) {
                        facetResult = altResult;
                        facetId = alt;
                        break;
                    }
                }
            }
        }

        if (facetResult) {
            const rows = Array.isArray(facetResult.rows) ? facetResult.rows : [];
            const idsArray = facetResult.ids
                ? (Array.isArray(facetResult.ids) ? facetResult.ids : Array.from(facetResult.ids))
                : [];

            let data = rows;

            // Check if rows have correct field names for the category
            // Unison Search API may return fields with different names than expected by the table rendering
            // We need to verify that essential fields exist, otherwise fetch full data
            const normalizedCategory = (typeof categoryToModule === 'function') ? categoryToModule(category) : category.toLowerCase();
            let needsFullDataFetch = false;
            
            if (rows.length > 0) {
                const firstRow = rows[0];
                let hasCorrectFieldNames = false;
                
                // Define expected essential fields for each category
                // These are the minimum fields needed for proper table rendering
                const expectedFieldsMap = {
                    'attribute': ['Ref. attribute', 'Name attribute', 'Definition attribute'],
                    'attributes': ['Ref. attribute', 'Name attribute', 'Definition attribute'],
                    'dataset': ['Ref.', 'Name', 'Definition'],
                    'data-sets': ['Ref.', 'Name', 'Definition'],
                    'system': ['Short Name', 'Description', 'Type'],
                    'glossary': ['Ref.', 'Name', 'Definition', 'Type'],
                    'people': ['First Name', 'Last Name', 'Email'],
                    'role': ['ID', 'Role', 'Description'],
                    'business-area': ['Ref.', 'Name', 'Description'],
                    'legal-entity': ['ShortName', 'LongName', 'Description'],
                    'client': ['PrimaryName', 'LongName', 'Description'],
                    'committee': ['PrimaryName', 'LongName', 'Description'],
                    'interface': ['Ref.', 'Name', 'Description'],
                    'orgunit': ['Ref.', 'Name', 'Description'],
                    'process': ['Ref.', 'Name', 'Description'],
                    'project': ['Ref.', 'Name', 'Description'],
                    'product': ['Ref.', 'Name', 'Description'],
                    'policy': ['Ref.', 'Name', 'Description'],
                    'capability': ['Ref.', 'Name', 'Description'],
                    'geography': ['Name', 'Description'],
                    'regulation': ['Ref.', 'Name', 'Description'],
                    'regulator': ['Name', 'Description'],
                    'regulatory-theme': ['Ref.', 'Name', 'Description'],
                    'change-requests': ['PrimaryName', 'Reference', 'Summary'],
                    'changerequests': ['PrimaryName', 'Reference', 'Summary'],
                    'change-request': ['PrimaryName', 'Reference', 'Summary'],
                    'changerequest': ['PrimaryName', 'Reference', 'Summary']
                };
                
                // Get expected fields for this category
                const expectedFields = expectedFieldsMap[normalizedCategory] || 
                                      expectedFieldsMap[category.toLowerCase()] ||
                                      [];
                
                if (expectedFields.length > 0) {
                    // Count how many expected fields have non-empty values
                    const fieldsWithValues = expectedFields.filter(field => {
                        const value = firstRow[field];
                        return value !== undefined && value !== null && value !== '';
                    }).length;
                    
                    // We need at least half of the expected fields to have values
                    // This ensures we have enough data to display properly
                    const minRequiredFields = Math.max(1, Math.ceil(expectedFields.length / 2));
                    hasCorrectFieldNames = fieldsWithValues >= minRequiredFields;
                    
                    if (!hasCorrectFieldNames) {
                        // Check for alternative field names (different naming conventions)
                        let hasAlternativeFields = false;
                        let alternativeFieldsWithValues = 0;
                        
                        if (normalizedCategory === 'attribute' || normalizedCategory === 'attributes') {
                            // For attributes, check if fields exist without " attribute" suffix
                            const altFields = ['Ref.', 'Name', 'Definition'];
                            alternativeFieldsWithValues = altFields.filter(field => {
                                const value = firstRow[field];
                                return value !== undefined && value !== null && value !== '';
                            }).length;
                            hasAlternativeFields = alternativeFieldsWithValues > 0;
                        } else if (normalizedCategory === 'people') {
                            // For people, check alternative field names (snake_case or camelCase)
                            const altFields = ['First_Name', 'first_name', 'FirstName', 'Last_Name', 'last_name', 'LastName', 'Email', 'email'];
                            alternativeFieldsWithValues = altFields.filter(field => {
                                const value = firstRow[field];
                                return value !== undefined && value !== null && value !== '';
                            }).length;
                            hasAlternativeFields = alternativeFieldsWithValues > 0;
                        } else if (normalizedCategory === 'role') {
                            // For role, check alternative field names
                            const altFields = ['Role', 'role', 'FullName', 'full_name', 'Full_Name', 'Description', 'description'];
                            alternativeFieldsWithValues = altFields.filter(field => {
                                const value = firstRow[field];
                                return value !== undefined && value !== null && value !== '';
                            }).length;
                            hasAlternativeFields = alternativeFieldsWithValues > 0;
                        } else {
                            // For other categories, check if fields exist but are empty
                            hasAlternativeFields = expectedFields.some(field => 
                                firstRow[field] !== undefined && (firstRow[field] === null || firstRow[field] === '')
                            );
                        }
                        
                        // If we have alternative fields but not enough expected fields with values, fetch full data
                        // OR if we don't have enough fields with values at all, fetch full data
                        if (hasAlternativeFields || fieldsWithValues < minRequiredFields) {
                            needsFullDataFetch = true;
                        }
                    }
                } else {
                    // For categories without specific expected fields, check if row has basic structure (ID field)
                    hasCorrectFieldNames = (firstRow.ID !== undefined && firstRow.ID !== null) || 
                                          (firstRow.id !== undefined && firstRow.id !== null);
                    if (!hasCorrectFieldNames && Object.keys(firstRow).length === 0) {
                        needsFullDataFetch = true;
                    }
                }

                // People: Unison often returns a slim row shape (e.g. ID + email, or mixed keys). If we already
                // have an ID and at least two populated display/identity fields, skip the extra fetch — it only
                // spams the console and duplicates work when fetchFacetDataByIds would return the same shape.
                if (normalizedCategory === 'people' && rows.length > 0) {
                    const fr = rows[0];
                    const hasId = fr.ID != null || fr.id != null;
                    const peopleWideKeys = [
                        'First Name', 'Last Name', 'Email',
                        'First_Name', 'Last_Name', 'first_name', 'last_name', 'FirstName', 'LastName', 'email',
                        'Profile Name', 'profile_name', 'System_Role', 'system_role',
                        'Function', 'Org Unit', 'BUDG Status'
                    ];
                    const populatedWide = peopleWideKeys.filter(k => fr[k] != null && fr[k] !== '').length;
                    if (hasId && populatedWide >= 2) {
                        needsFullDataFetch = false;
                    }
                }
                
                // Set window.__UNISON_VERBOSE_LOAD__ = true to see debug logs for fallback fetches.
                if (needsFullDataFetch && (typeof window !== 'undefined' && window.__UNISON_VERBOSE_LOAD__)) {
                    console.debug(`[loadCategoryDataFromUnisonResults] ${category} rows missing correct field names, fetching full data by IDs`);
                }
            }

            // Special handling for role: Use rows from Unison Search directly if they exist
            // Role data from Unison Search uses object_x_people IDs, but /UnisonSearch/role endpoint
            // may return different IDs, causing filtering issues. So we prefer using rows directly.
            if (normalizedCategory === 'role' && rows.length > 0) {
                // For role, even if field names don't match exactly, try to use rows if they have basic structure
                const hasBasicStructure = rows.some(row => (row.ID !== undefined || row.id !== undefined) && 
                                                          (row.Role !== undefined || row.role !== undefined || row.PrimaryName !== undefined));
                if (hasBasicStructure) {
                    // Transform rows to ensure they have the expected field names
                    data = rows.map(row => {
                        const transformed = { ...row };
                        // Map role (lowercase) to Role (capital R) - this is the key field name expected by the table
                        if (transformed.role && !transformed.Role) {
                            transformed.Role = transformed.role;
                        }
                        // Map PrimaryName to Role if Role doesn't exist
                        if (!transformed.Role && transformed.PrimaryName) {
                            transformed.Role = transformed.PrimaryName;
                            transformed.role = transformed.PrimaryName;
                        }
                        // Map primaryname (lowercase) to Role if Role doesn't exist
                        if (!transformed.Role && transformed.primaryname) {
                            transformed.Role = transformed.primaryname;
                            transformed.role = transformed.primaryname;
                        }
                        // Ensure ID field exists
                        if (!transformed.ID && transformed.id) {
                            transformed.ID = transformed.id;
                        }
                        // Ensure Description field exists (may be null but should be present)
                        if (transformed.Description === undefined && transformed.description === undefined) {
                            transformed.Description = transformed.description || null;
                        }
                        // Ensure Full Name, Object Type, Object, Role Accepted fields exist (may be null)
                        if (transformed['Full Name'] === undefined) {
                            transformed['Full Name'] = null;
                        }
                        if (transformed['Object Type'] === undefined) {
                            transformed['Object Type'] = null;
                        }
                        if (transformed['Object'] === undefined) {
                            transformed['Object'] = null;
                        }
                        if (transformed['Role Accepted'] === undefined) {
                            transformed['Role Accepted'] = null;
                        }
                        // Ensure Role type field exists
                        if (transformed['Role type'] === undefined && transformed['Role Type'] === undefined) {
                            // Map role_type to Role type if needed
                            if (transformed['role_type'] && !transformed['Role type']) {
                                transformed['Role type'] = transformed['role_type'];
                            } else {
                                transformed['Role type'] = null;
                            }
                        }
                        // Ensure Date Accepted field exists
                        if (transformed['Date Accepted'] === undefined) {
                            transformed['Date Accepted'] = transformed['date_accepted'] || transformed['dateAccepted'] || transformed['DateAccepted'] || null;
                        }
                        return transformed;
                    });
                    needsFullDataFetch = false; // Don't fetch from endpoint
                }
            }

            // Use rows from Unison Search API directly - they now contain complete data with all columns
            // Only fetch additional data if rows are empty but IDs exist (backwards compatibility)
            // OR if category needs correct field names
            if (((!rows || rows.length === 0) && idsArray.length > 0) || (needsFullDataFetch && idsArray.length > 0)) {
                if (typeof window !== 'undefined' && window.__UNISON_VERBOSE_LOAD__) {
                    if (needsFullDataFetch) {
                        console.debug(`[loadCategoryDataFromUnisonResults] ${category} category: fetching full data with correct field names`);
                    } else {
                        console.debug(`[loadCategoryDataFromUnisonResults] No rows in Unison results for ${category}, fetching by IDs as fallback`);
                    }
                }
                
                // Show loading indicator while fetching
                if (dataTable) {
                    dataTable.classList.add('loading');
                    if (typeof renderSkeleton === 'function') {
                        renderSkeleton(dataTable);
                    }
                }

                try {
                    const fullData = await fetchFacetDataByIds(category, idsArray);
                    
                    if (fullData && fullData.length > 0) {
                        data = fullData;
                        const totalCount = (facetResult && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                            ? facetResult.totalCount : data.length;
                        if (typeof updateCategoryCount === 'function') {
                            updateCategoryCount(category, data.length, totalCount, true);
                        }
                    } else {
                        console.warn(`[loadCategoryDataFromUnisonResults] fetchFacetDataByIds returned empty or invalid data for ${category}`);
                    }
                } catch (error) {
                    console.error(`[loadCategoryDataFromUnisonResults] Error fetching full data:`, error);
                } finally {
                    // Hide loading indicator after fetch completes
                    if (dataTable) {
                        dataTable.classList.remove('loading');
                    }
                }
            }

            // Change requests: normalize columns, resolve referenced object names + segment
            if ((normalizedCategory === 'change-requests' || category === 'change-requests') && data && data.length > 0 && typeof transformChangeRequestData === 'function') {
                data = transformChangeRequestData(data);
                if (typeof enrichChangeRequestDataWithObjectNames === 'function') {
                    data = await enrichChangeRequestDataWithObjectNames(data);
                }
            }

            if (data && data.length > 0) {
                // Apply any display-only filter conditions (not sent to backend) client-side.
                const beforeLen = data.length;
                const filteredData = applyDisplayFiltersToRows(category, data);
                data = filteredData;
                if (filteredData.length !== beforeLen) {
                    // Update sidebar count to reflect the filtered subset
                    const totalCount = (facetResult && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                        ? facetResult.totalCount : beforeLen;
                    if (typeof updateCategoryCount === 'function') {
                        updateCategoryCount(category, filteredData.length, totalCount, true);
                    }
                }

                // Store filtered data
                currentFilteredData = data;
                currentFilteredCategory = category;
                categoryDataCache.set(category, { data: data, category: category });

                // Generate table
                if (typeof generateTable === 'function') {
                    generateTable(data, null, category);
                }

                // Update dashboard with filtered data if dashboard view is active
                if (typeof isDashboardViewActive === 'function' && isDashboardViewActive()) {
                    if (typeof updateDashboardWithFilteredData === 'function') {
                        await updateDashboardWithFilteredData(category, data);
                    }
                }

                // Always use data.length as displayed count - it matches what the table shows.
                // Backend facetResult.count may be 0 for related facets even when results exist.
                const countToUse = (data && data.length > 0) ? data.length : 0;

                const backendTotal = (facetResult && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                    ? facetResult.totalCount : 0;
                const totalCount = Math.max(backendTotal, countToUse);
                if (typeof updateCategoryCount === 'function') {
                    updateCategoryCount(category, countToUse, totalCount, true);
                }

                // Cache data
                const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;
                if (typeof cacheModuleData === 'function') {
                    cacheModuleData(category, data, signature);
                }

                // Update settings button
                if (typeof updateSettingsButtonForCategory === 'function') {
                    updateSettingsButtonForCategory(category).catch(err => { });
                }

                // Reload map if active
                if (typeof reloadMapIfActive === 'function') {
                    reloadMapIfActive();
                }
            } else {
                showNoDataMessage(category);
            }
        } else {
            currentFilteredData = null;
            currentFilteredCategory = null;
            showNoDataMessage(category);

            const totalCount = (facetResult && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                ? facetResult.totalCount : 0;
            if (typeof updateCategoryCount === 'function') {
                updateCategoryCount(category, 0, totalCount, true);
            }
            return;
        }
    } catch (err) {
        console.error('[loadCategoryDataFromUnisonResults] Error:', err);
        showErrorMessage('Failed to load filtered data. Loading all data...');
        // Clear Unison results to prevent infinite loop when calling loadCategoryData
        const tempResults = currentUnisonSearchResults;
        currentUnisonSearchResults = null;
        await loadCategoryData(category);
        // Don't restore results - let fresh load proceed
    } finally {
        // Release re-entry guard
        isLoadingFromUnisonResults = false;
        loadingFromUnisonResultsCategory = null;

        // Hide full page loading indicator
        if (typeof hideLoading === 'function') {
            hideLoading('loadCategoryDataFromUnisonResults');
        }
        if (dataTable) dataTable.classList.remove('loading');

        // Update all facet indicators to preserve has-active-filter for facets with active conditions
        if (typeof updateAllFacetIndicatorsFromConditions === 'function') {
            updateAllFacetIndicatorsFromConditions();
        }
    }
}

/**
 * When category has no data (e.g. segment filter empty): show "0 of total" in sidebar.
 * Uses preserved total from getDisplayCounts so segment filter shows "0 of 67" not "67 of 67".
 */
function refreshFacetCountForNoData(category) {
    if (!category || typeof updateCategoryCount !== 'function') return;
    const canonical = (typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function')
        ? window.canonicalCategoryKey(category) : category;
    const preservedTotal = (typeof window !== 'undefined' && typeof window.resolveZeroFacetTotal === 'function')
        ? window.resolveZeroFacetTotal(canonical) : 0;
    updateCategoryCount(category, 0, preservedTotal, true);
}

async function loadCategoryData(category) {
    // Guard against recursive calls - prevent infinite loop
    if (isLoadingCategory && loadingCategoryName === category) {
        console.warn('[loadCategoryData] Already loading category:', category, '- preventing recursive call');
        return;
    }
    
    isLoadingCategory = true;
    loadingCategoryName = category;

    const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
    const rawQuery = getCurrentQuery();
    // Only use query if a search was explicitly committed or conditions exist
    const query = (isSearchCommitted || (searchConditions && searchConditions.length > 0)) ? rawQuery : '';

    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('loadCategoryData');
        }

        if (dataTable) {
            dataTable.classList.add('loading');
            renderSkeleton(dataTable);
        }

        // IMPORTANT: If we have Unison Search results, use filtered data instead of loading all data
        // This preserves the filter when switching between facets
        // Check that results object actually has data (not empty object)
        // Active Tasks: always load from /api/active-tasks (Unison facet is often missing or empty)
        if (!isActiveTasksUnisonCategory(category) &&
            currentUnisonSearchResults &&
            currentUnisonSearchResults.results &&
            Object.keys(currentUnisonSearchResults.results).length > 0) {
            await loadCategoryDataFromUnisonResults(category);
            return;
        }

        // Check cache first
        const cacheKey = `${category}_${query.trim()}`;
        if (window.searchCache) {
            const cached = window.searchCache.get(cacheKey);
            if (cached) {
                generateTable(cached, null, category);
                const canonical = (typeof window.canonicalCategoryKey === 'function') ? window.canonicalCategoryKey(category) : category;
                const display = (typeof window.getDisplayCounts === 'function') ? window.getDisplayCounts(canonical) : { count: 0, total: 0 };
                const totalForCached = typeof display.total === 'number' && !isNaN(display.total) ? display.total : cached.length;
                updateCategoryCount(category, cached.length, totalForCached, false);
                if (dataTable) dataTable.classList.remove('loading');
                return;
            }
        }

        // Special handling for Active Tasks
        const normalizedCategory = categoryToModule(category);
        if (normalizedCategory === 'activeTasks' || category.toLowerCase() === 'active-tasks' || category.toLowerCase() === 'activetasks') {
            if (typeof loadActiveTasksData === 'function') {
                const data = await loadActiveTasksData();

                if (!validateData(data, category)) {
                    showNoDataMessage(category);
                    if (dataTable) dataTable.classList.remove('loading');
                    if (typeof hideLoading === 'function') {
                        hideLoading('loadCategoryData');
                    }
                    return;
                }

                if (data.length === 0) {
                    showNoDataMessage(category);
                    refreshFacetCountForNoData(category);
                    if (dataTable) dataTable.classList.remove('loading');
                    if (typeof hideLoading === 'function') {
                        hideLoading('loadCategoryData');
                    }
                    return;
                }

                generateTable(data, null, category);
                updateCategoryCount(category, data.length, data.length, false);
                if (dataTable) dataTable.classList.remove('loading');
                if (typeof hideLoading === 'function') {
                    hideLoading('loadCategoryData');
                }
                return;
            } else {
                console.error('[loadCategoryData] loadActiveTasksData function not found');
            }
        }

        // Special handling for Change Requests
        if (normalizedCategory === 'change-requests' || category.toLowerCase() === 'change-requests' ||
            category.toLowerCase() === 'changerequests' || category.toLowerCase() === 'change-request' ||
            category.toLowerCase() === 'changerequest') {
            const data = await fetchCategoryData(category, query);

            if (!validateData(data, category)) {
                showNoDataMessage(category);
                refreshFacetCountForNoData(category);
                if (dataTable) dataTable.classList.remove('loading');
                if (typeof hideLoading === 'function') {
                    hideLoading('loadCategoryData');
                }
                return;
            }

            if (data.length === 0) {
                showNoDataMessage(category);
                refreshFacetCountForNoData(category);
                if (dataTable) dataTable.classList.remove('loading');
                if (typeof hideLoading === 'function') {
                    hideLoading('loadCategoryData');
                }
                return;
            }

            generateTable(data, null, category);
            updateCategoryCount(category, data.length, data.length, false);
            if (dataTable) dataTable.classList.remove('loading');
            if (typeof hideLoading === 'function') {
                hideLoading('loadCategoryData');
            }
            return;
        }

        // Use server-side search if there's a query, otherwise load all data
        const searchQuery = query.trim() ? query : '';
        const data = await fetchCategoryData(category, searchQuery);

        // Store in cache
        if (window.searchCache && data && data.length > 0) {
            window.searchCache.set(cacheKey, data);
        }

        if (!validateData(data, category)) {
            showNoDataMessage(category);
            refreshFacetCountForNoData(category);
            return;
        }

        if (data.length === 0) {
            const selectedItem = getSelectedItem();
            if (selectedItem && selectedItem.category !== category) {
                showRelatedNoDataMessage(category, selectedItem);
            } else {
                showNoDataMessage(category);
            }
            refreshFacetCountForNoData(category);
            return;
        }

        const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;

        // Generate table directly from server results
        generateTable(data, null, category);
        // Update count with segment-filtered data (count = total when segment filter is active)
        updateCategoryCount(category, data.length, data.length, false);
        if (typeof cacheModuleData === 'function') {
            cacheModuleData(category, data, signature);
        }

        // Update settings button for current category
        if (typeof updateSettingsButtonForCategory === 'function') {
            // Don't await - let it run in background
            updateSettingsButtonForCategory(category).catch(err => {
                // Silent fail
            });
        }

        // Update UI to show related search is active
        updateRelatedSearchIndicator(category);

        // Reload map if map view is active
        if (typeof reloadMapIfActive === 'function') {
            reloadMapIfActive();
        }
    } catch (err) {
        showErrorMessage('Failed to load data. Please try again.');
    } finally {
        // Hide full page loading indicator
        if (typeof hideLoading === 'function') {
            hideLoading('loadCategoryData');
        }
        if (dataTable) dataTable.classList.remove('loading');

        // Update all facet indicators to preserve has-active-filter for facets with active conditions
        // This ensures the orange color stays on all facets with active conditions when switching
        if (typeof updateAllFacetIndicatorsFromConditions === 'function') {
            updateAllFacetIndicatorsFromConditions();
        }
        
        // Reset loading flags to allow future loads
        isLoadingCategory = false;
        loadingCategoryName = null;
    }
}

// Store current filtered data for FIND operations
// Use a map to store data per category so each category maintains its own data
let categoryDataCache = new Map(); // category -> { data, category }
let currentFilteredData = null;
let currentFilteredCategory = null;

/**
 * Mapping from CATEGORY_FIELD_MAPPING field keys to the actual DB column names
 * as they appear in the table row data returned by the backend.
 * Used to extract the right display value from a row for bulk-selection labels.
 */
const FIELD_KEY_TO_ROW_COLUMN = {
    name:          ['Short Name', 'Name', 'PrimaryName', 'primaryname'],
    ref:           ['Ref Number', 'Ref.', 'RefNumber', 'refnumber', 'Reference'],
    longName:      ['Long Name', 'LongName'],
    shortName:     ['Short Name', 'ShortName'],
    definition:    ['Definition', 'definition'],
    description:   ['Description', 'description'],
    usage:         ['Usage', 'usage'],
    businessLogic: ['Business Logic', 'Business_Logic'],
    assetId:       ['Asset ID', 'AssetID'],
    aliasName:     ['Alias Name'],
    firstName:     ['First Name', 'First_Name'],
    lastName:      ['Last Name', 'Last_Name'],
    email:         ['Email', 'email'],
    summary:       ['Summary', 'summary'],
};

/**
 * Extract the best display value from a row for a given field key list.
 * Tries each candidate column name in order; falls back to ID.
 */
function _getRowValueForFieldKeys(rowData, fieldKeys) {
    for (const key of fieldKeys) {
        const candidates = FIELD_KEY_TO_ROW_COLUMN[key] || [];
        for (const col of candidates) {
            if (rowData[col] != null && String(rowData[col]).trim() !== '') {
                return String(rowData[col]).trim();
            }
        }
    }
    return null;
}

function getBulkSelectedRowQuery(rowData, category) {
    if (!rowData) return '';

    // Determine which field keys to use:
    // 1. Active "Search in" selection if available
    // 2. Otherwise default to the name/ref fields for the category
    const activeFields = typeof getActiveSearchFields === 'function' ? getActiveSearchFields() : null;
    const fields = category ? getCategoryFields(category) : {};
    const allFieldKeys = Object.keys(fields);

    let priorityKeys = [];
    if (activeFields && Object.keys(activeFields).length > 0) {
        // Use only the fields the user has ticked ON
        priorityKeys = allFieldKeys.filter(k => activeFields[k] === true || activeFields[k] === undefined);
    }
    if (priorityKeys.length === 0) {
        // Default: prefer name, then ref, then anything else available
        priorityKeys = ['name', 'ref', ...allFieldKeys.filter(k => k !== 'name' && k !== 'ref')];
    }

    const val = _getRowValueForFieldKeys(rowData, priorityKeys);
    if (val) return val;

    // Absolute fallback: ID
    const id = rowData.ID || rowData.id || rowData.Id;
    return id != null ? String(id).trim() : '';
}

function buildBulkSelectionCondition(selectedRows, fallbackCategory) {
    if (!Array.isArray(selectedRows) || selectedRows.length === 0) return null;

    const rowCategory = selectedRows[0] && (selectedRows[0].category || selectedRows[0].Category);
    const resolvedCategory = rowCategory || fallbackCategory;

    const terms = [];
    selectedRows.forEach(rowData => {
        const term = getBulkSelectedRowQuery(rowData, resolvedCategory);
        if (term && !terms.includes(term)) {
            terms.push(term);
        }
    });

    if (terms.length === 0) return null;

    // Build the "Search in" field label list based on active searchFields
    const fields = getCategoryFields(resolvedCategory);
    const activeFields = typeof getActiveSearchFields === 'function' ? getActiveSearchFields() : null;
    const fieldNames = [];
    Object.entries(fields).forEach(([key, label]) => {
        const isActive = !activeFields || activeFields[key] === true || activeFields[key] === undefined;
        if (isActive) {
            fieldNames.push(Array.isArray(label) ? label.join(', ') : label);
        }
    });

    const searchFields = (activeFields && Object.keys(activeFields).length > 0)
        ? { ...activeFields }
        : (typeof getEffectiveSearchFieldsForCategory === 'function'
            ? getEffectiveSearchFieldsForCategory(resolvedCategory)
            : null);

    return {
        id: Date.now() + Math.random(),
        operator: 'FIND',
        category: resolvedCategory,
        query: terms[0],
        displayQuery: `Collection ${terms.map(t => `"${t}"`).join(', ')}`,
        bulkTerms: terms,
        fields: fieldNames,
        searchFields: searchFields,
        muted: false
    };
}

async function performSearch() {
    const category = getActiveCategoryWithFallback();
    const query = getCurrentQuery();
    const operatorSelect = document.querySelector('.search-type-select');
    const operator = operatorSelect ? operatorSelect.value.toUpperCase() : 'FIND';

    // Hide suggestions dropdown when search is performed
    const suggestionsDropdown = document.querySelector('.search-suggestions');
    if (suggestionsDropdown) {
        suggestionsDropdown.style.display = 'none';
        suggestionsDropdown.innerHTML = '';
        currentSuggestionsData = null;
        currentSuggestionsCategory = null;
    }

    // If the user typed a keyword, clear any previously selected suggestion item so
    // the keyword search runs independently of whatever row was highlighted before.
    if (query.trim() && typeof setSelectedItem === 'function') {
        setSelectedItem(null);
    }

    // Mark that the user explicitly initiated a search
    isSearchCommitted = true;

    if (typeof window !== 'undefined') {
        if (window.categoryCounts) window.categoryCounts.clear();
        if (window.setFacetCountSource) window.setFacetCountSource('baseline');
        if (typeof window.preloadModuleRowCounts === 'function') {
            try { await window.preloadModuleRowCounts(); } catch (_) { }
        }
    }

    // Check for selected rows first (before processing empty query)
    // But if the user typed a keyword in the search bar, honour the keyword search instead.
    const selectedRows = (typeof window !== 'undefined' && window.bulkSelection && typeof window.bulkSelection.getSelectedRows === 'function')
        ? window.bulkSelection.getSelectedRows()
        : [];

    if (selectedRows && selectedRows.length > 0 && !query.trim()) {
        // User has selected rows - create one logical condition for the whole selection
        // Clear existing conditions first
        searchConditions = [];
        window.searchConditions = searchConditions;

        // Clear related state variables
        currentFilteredData = null;
        currentFilteredCategory = null;
        currentUnisonSearchResults = null;

        // Clear category cache for current category
        if (category) {
            categoryDataCache.delete(category);
        }

        const bulkCondition = buildBulkSelectionCondition(selectedRows, category);
        if (bulkCondition) {
            searchConditions = [bulkCondition];
            window.searchConditions = searchConditions;
            renderSearchConditions();
            updateSearchCounter();
        }
        window._searchBulkSelectionActive = false;

        // Execute search with the new conditions
        if (searchConditions.length > 0) {
            await executeMultiConditionSearch();
            enableOperatorOptions();
        } else {
            // No valid conditions were added, just load category data
            await loadCategoryData(category);
        }
        return;
    }

    // AND/OR/NOT require an active category (facet/tab)
    const isAndOrNot = (operator === 'AND' || operator === 'OR' || operator === 'NOT');
    if (isAndOrNot && (!category || String(category).trim() === '')) {
        if (typeof showToast === 'function') {
            const msg = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t('search.selectCategoryFirst') : 'Select a category first';
            showToast(msg, 'warning');
        }
        return;
    }

    if (!query.trim()) {
        // Empty query: AND/OR/NOT with existing FIND = add condition with empty/sentinel and run; else FIND = select all
        const hasFindCondition = searchConditions.some(function (c) { return c.operator === 'FIND' && !c.muted; });
        const isAndOrNot = (operator === 'AND' || operator === 'OR' || operator === 'NOT');
        if (isAndOrNot && hasFindCondition && category) {
            addSearchCondition(operator, category, '*');
            await executeMultiConditionSearch();
            enableOperatorOptions();
            return;
        }
        // Empty query and no selected rows: Add all objects from current facet as 1 condition
        searchConditions = [];
        window.searchConditions = searchConditions;

        currentFilteredData = null;
        currentFilteredCategory = null;
        currentUnisonSearchResults = null;

        if (category) {
            categoryDataCache.delete(category);
        }

        if (category) {
            addSearchCondition('FIND', category, '*');
            await executeMultiConditionSearch();
            enableOperatorOptions();
        } else {
            await loadCategoryData(category);
        }
        return;
    }

    // Check if user typed operators directly in query (new AST parser mode)
    // Support: AND, OR, NOT, parentheses, quoted strings, field:value, wildcards
    const hasInlineOperators = /\b(AND|OR|NOT)\b/i.test(query) ||
        query.includes('(') || query.includes(')') ||
        /"[^"]*"/.test(query) || // Quoted strings
        /\w+:\S+/.test(query); // Field:value format

    if (hasInlineOperators) {
        // User typed operators directly - use new parser mode
        // Send raw query to backend, it will auto-detect and parse
        // The backend QueryBuilder will handle AST parsing
        // Store the query temporarily so loadCategoryData can use it
        const searchInput = document.querySelector('.search-main-input');
        if (searchInput) {
            // Query is already in the input, loadCategoryData will read it
            await loadCategoryData(category);
            // Clear after search
            searchInput.value = '';
            // Show clear button after inline operator search
            updateSearchCounter();
        }
        return;
    }

    // Legacy mode: using dropdown operators
    // Note: addSearchCondition() now handles all logic including:
    // - FIND replacement
    // - Auto-FIND for AND/OR/NOT
    // - Duplicate detection
    // - UI updates
    // - Reset dropdown to FIND

    // ✅ منطق FIND Replacement:
    // - إذا كان FIND في نفس الـ category: يتم الاستبدال (replace existing FIND)
    // - إذا كان FIND في category مختلف: يتم الاحتفاظ بكليهما (cross-facet search)
    // - بعد أول FIND: يتم تفعيل AND/OR/NOT options
    if (operator === 'FIND') {
        // Add new FIND condition (will replace existing FIND if any)
        addSearchCondition('FIND', category, query);
        await executeMultiConditionSearch();

        // Enable AND/OR/NOT options after first FIND
        enableOperatorOptions();
    } else {
        // AND/OR/NOT - add condition to query builder
        addSearchCondition(operator, category, query);
        await executeMultiConditionSearch();
    }
}

async function performFindFilter(category, query) {
    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('performFindFilter');
        }

        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) {
            dataTable.classList.add('loading');
            renderSkeleton(dataTable);
        }

        let dataToFilter = null;

        // Check if we have cached filtered data for this category
        // First check category cache, then fallback to currentFilteredData
        const cachedData = categoryDataCache.get(category);
        if (cachedData && cachedData.data) {
            // Use cached data for this category
            dataToFilter = cachedData.data;
        } else if (currentFilteredData && currentFilteredCategory === category) {
            // Use cached filtered data
            dataToFilter = currentFilteredData;
        } else if (searchConditions.length > 0) {
            // We have conditions, fetch data with those conditions
            const activeConditions = searchConditions.filter(c => !c.muted && c.operator !== 'FIND');
            if (activeConditions.length > 0) {
                dataToFilter = await fetchCategoryDataWithConditions(category, activeConditions);
            } else {
                // All conditions are muted or only FIND, load all data
                dataToFilter = await fetchCategoryData(category, '');
            }
        } else {
            // First time search - no conditions, load all data for this category
            dataToFilter = await fetchCategoryData(category, '');
        }

        if (!dataToFilter || !Array.isArray(dataToFilter) || dataToFilter.length === 0) {
            showNoDataMessage(category);
            if (dataTable) dataTable.classList.remove('loading');
            return;
        }

        // Filter data locally using performLocalSearch
        const filteredData = performLocalSearch(dataToFilter, query, category);

        if (!filteredData || filteredData.length === 0) {
            showNoDataMessage(category);
            if (dataTable) dataTable.classList.remove('loading');
            return;
        }

        // Store filtered data for next FIND operation
        currentFilteredData = filteredData;
        currentFilteredCategory = category;
        // Also store in category cache
        categoryDataCache.set(category, { data: filteredData, category: category });

        // Generate table with filtered data
        generateTable(filteredData, null, category);
        updateCategoryCount(category, filteredData.length, filteredData.length, false);

        // Update settings button
        if (typeof updateSettingsButtonForCategory === 'function') {
            updateSettingsButtonForCategory(category).catch(err => { });
        }

        // Reload map if active
        if (typeof reloadMapIfActive === 'function') {
            reloadMapIfActive();
        }
    } catch (err) {
        console.error('Error in performFindFilter:', err);
        showErrorMessage('Failed to filter. Please try again.');
    } finally {
        // Hide full page loading indicator
        if (typeof hideLoading === 'function') {
            hideLoading('performFindFilter');
        }
        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) dataTable.classList.remove('loading');
    }
}

// Global variable to store current Unison Search results
let currentUnisonSearchResults = null;

// Make it available on window for cross-file access
if (typeof window !== 'undefined') {
    Object.defineProperty(window, 'currentUnisonSearchResults', {
        get: () => currentUnisonSearchResults,
        set: (value) => { currentUnisonSearchResults = value; }
    });
}

/**
 * Execute Unison Search for a selected item.
 * This finds all related objects across all facets.
 * 
 * @param {Object} selectedItem - Selected item with {id, category, name}
 */
/**
 * Fire-and-forget helper: runs a background Unison search by item ID to refresh the
 * sidebar facet counts without touching currentUnisonSearchResults or the active table.
 * Used by suggestion-selection and Enter-key handlers.
 */
function _loadRelatedCountsForItem(item, activeCategory) {
    if (!item || !item.id) return;
    const unisonSearchFn = typeof executeUnisonSearch === 'function' ? executeUnisonSearch :
        (typeof window !== 'undefined' && typeof window.executeUnisonSearch === 'function' ? window.executeUnisonSearch : null);
    if (!unisonSearchFn) return;

    const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId :
        (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
    const facetId = catToFacetId ? catToFacetId(item.category || activeCategory) : (item.category || activeCategory || '').toUpperCase();
    const normalizedFacetId = (facetId === 'DATA-SETS' || facetId === 'DATA_SETS') ? 'DATASET' : facetId;

    const searches = [{ operator: 'FIND', facet: normalizedFacetId, keyword: String(item.id), filters: {} }];
    unisonSearchFn(searches, { maxDepth: 1 }).then(result => {
        if (!result || !result.success) {
            console.log('[UNISON-DEBUG][_loadRelatedCountsForItem] Search failed for item id=' + item.id, result);
            return;
        }
        console.log('[UNISON-DEBUG][_loadRelatedCountsForItem] Result for item id=' + item.id + ':', Object.keys(result.results || {}));
        // Populate relatedObjects store (used by expand/relate buttons)
        if (result.relatedObjects) {
            if (!window.unisonRelatedObjects) window.unisonRelatedObjects = {};
            Object.assign(window.unisonRelatedObjects, result.relatedObjects);
        }
        // Update sidebar counts only — preserve the existing table/results state
        if (result.results && typeof updateCategoryCount === 'function') {
            const facetIdToCatFn = typeof facetIdToCategory === 'function' ? facetIdToCategory :
                (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null);
            const normFacetKey = (fid) => {
                if (typeof window !== 'undefined' && typeof window.normalizeFacetId === 'function') {
                    return window.normalizeFacetId(fid);
                }
                if (!fid) return fid;
                return fid.toString().toUpperCase().trim().replace(/\s+/g, '_').replace(/-/g, '_');
            };
            const resultFacetKeys = new Set(Object.keys(result.results).map((k) => normFacetKey(k)));

            Object.entries(result.results).forEach(([fid, fr]) => {
                if (!fr) return;
                const cat = facetIdToCatFn ? facetIdToCatFn(fid) : null;
                if (!cat || cat === activeCategory) return; // skip active category — its table is already correct
                const cnt = effectiveFacetResultCount(fr);
                updateCategoryCount(cat, cnt, Math.max(fr.totalCount || 0, cnt), true);
                if (typeof updateFacetIndicator === 'function') {
                    updateFacetIndicator(cat, {
                        ...fr,
                        hasActiveFilter: Boolean(fr.hasActiveFilter || cnt > 0)
                    });
                }
            });

            // Zero unrelated facets immediately; denominator Y from baseline / last search (not 0 of 0)
            const activeCanon = (typeof window.canonicalCategoryKey === 'function')
                ? window.canonicalCategoryKey(activeCategory || item.category || '')
                : (activeCategory || item.category || '');
            const catToFacetFn = typeof categoryToFacetId === 'function' ? categoryToFacetId :
                (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
            document.querySelectorAll('.category-item').forEach((el) => {
                const category = el.getAttribute('data-category');
                if (!category) return;
                const canonical = typeof window.canonicalCategoryKey === 'function'
                    ? window.canonicalCategoryKey(category) : category;
                if (!canonical || canonical === activeCanon) return;
                const facetForCat = catToFacetFn ? catToFacetFn(canonical) : null;
                if (!facetForCat) return;
                if (resultFacetKeys.has(normFacetKey(facetForCat))) return;
                const totalY = (typeof window.resolveZeroFacetTotal === 'function')
                    ? window.resolveZeroFacetTotal(canonical) : 0;
                updateCategoryCount(canonical, 0, totalY, true);
                if (typeof updateFacetIndicator === 'function') {
                    updateFacetIndicator(canonical, { hasActiveFilter: false, count: 0 });
                }
            });
        }
    }).catch(() => { /* background task — ignore errors */ });
}

async function executeUnisonSearchForSelectedItem(selectedItem) {
    if (!selectedItem || !selectedItem.id || !selectedItem.category) {
        console.warn('[Unison Search] Invalid selected item:', selectedItem);
        return;
    }


    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('executeUnisonSearchForSelectedItem');
        }

        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) {
            dataTable.classList.add('loading');
            renderSkeleton(dataTable);
        }

        // Get facet ID for the selected item's category
        const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId :
            (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);

        const facetId = catToFacetId ? catToFacetId(selectedItem.category) : selectedItem.category.toUpperCase();

        // Normalize facet ID
        const normalizedFacetId = (facetId === 'DATA-SETS' || facetId === 'DATA_SETS') ? 'DATASET' : facetId;

        // Create search request with the selected item's ID
        // We'll search for this specific ID to find all related objects
        const searches = [{
            operator: 'FIND',
            facet: normalizedFacetId,
            keyword: selectedItem.id.toString(), // Use ID as keyword - backend should handle this
            filters: {}
        }];


        // Get executeUnisonSearch function
        const unisonSearchFn = typeof executeUnisonSearch === 'function' ? executeUnisonSearch :
            (typeof window !== 'undefined' && typeof window.executeUnisonSearch === 'function' ? window.executeUnisonSearch : null);

        if (!unisonSearchFn) {
            console.error('[Unison Search] executeUnisonSearch function not available');
            await loadCategoryData(selectedItem.category);
            return;
        }

        // Execute Unison Search
        // Direct neighbors only for selected item as well
        const searchToken = ++currentSearchToken;
        const unisonResult = await unisonSearchFn(searches, { maxDepth: 1 });

        if (searchToken !== currentSearchToken) return;

        if (unisonResult.success) {
            // Debug: log dataset count (disabled)
            if (false && unisonResult.results && unisonResult.results.DATASET) {
                // Dataset IDs returned (debug disabled)
            }

            // Update all facets with results
            await updateAllFacets(unisonResult, selectedItem.category);

            // Update all facet indicators and counts from conditions
            // This ensures all facets show updated counts immediately
            if (typeof updateAllFacetIndicatorsFromConditions === 'function') {
                updateAllFacetIndicatorsFromConditions();
            }

            // Update settings button
            if (typeof updateSettingsButtonForCategory === 'function') {
                updateSettingsButtonForCategory(selectedItem.category).catch(err => { });
            }

            // Reload map if active
            if (typeof reloadMapIfActive === 'function') {
                reloadMapIfActive();
            }
        } else {
            console.error('[Unison Search] Search failed:', unisonResult.error);
            showErrorMessage(unisonResult.error || 'Search failed');
            await loadCategoryData(selectedItem.category);
        }
    } catch (err) {
        console.error('[Unison Search] Error executing search for selected item:', err);
        showErrorMessage('Failed to execute Unison search. Loading category data...');
        await loadCategoryData(selectedItem.category);
    } finally {
        // Hide full page loading indicator
        if (typeof hideLoading === 'function') {
            hideLoading('executeUnisonSearchForSelectedItem');
        }
        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) dataTable.classList.remove('loading');
    }
}

/**
 * Check if there are active (non-muted) search conditions for a specific category
 * @param {string} category - The category to check
 * @returns {boolean} - True if there are active conditions for this category
 */
function hasActiveConditionsForCategory(category) {
    // Get current search conditions from global or window scope
    const currentSearchConditions = (typeof window !== 'undefined' && window.searchConditions && Array.isArray(window.searchConditions))
        ? window.searchConditions
        : (typeof searchConditions !== 'undefined' && Array.isArray(searchConditions))
            ? searchConditions
            : [];

    // Check if there are any active (non-muted) conditions for this category
    return currentSearchConditions.some(condition => {
        if (condition.muted) return false;
        return condition.category === category || condition.module === category;
    });
}

/**
 * Get the source facet category from search conditions (first FIND condition)
 * @returns {string|null} The category of the source facet, or null if not found
 */
function getSourceFacetCategory() {
    // Get current search conditions from global or window scope
    const currentSearchConditions = (typeof window !== 'undefined' && window.searchConditions && Array.isArray(window.searchConditions))
        ? window.searchConditions
        : (typeof searchConditions !== 'undefined' && Array.isArray(searchConditions))
            ? searchConditions
            : [];

    // Find the first active (non-muted) condition with FIND operator
    const firstFindCondition = currentSearchConditions.find(condition => {
        if (condition.muted) return false;
        return condition.operator === 'FIND' || condition.operator === 'find';
    });

    // Return the category if found
    if (firstFindCondition && firstFindCondition.category) {
        return firstFindCondition.category;
    }

    return null;
}

/**
 * Visible hit count for a facet. When `rows` is non-empty, it matches the table and
 * reflects segment/security filtering; `ids` can still list objects that were
 * filtered out when rows were built (e.g. 5 ids, 4 rows → use 4). If there are no rows
 * yet, fall back to backend `count` and `ids` length.
 */
function effectiveFacetResultCount(fr) {
    if (!fr || typeof fr !== 'object') return 0;
    const idsLen = fr.ids
        ? (Array.isArray(fr.ids) ? fr.ids.length : (fr.ids.size || 0))
        : 0;
    const rowsLen = Array.isArray(fr.rows) ? fr.rows.length : 0;
    const c = typeof fr.count === 'number' && !isNaN(fr.count) ? fr.count : 0;
    if (rowsLen > 0) {
        return rowsLen;
    }
    return Math.max(c, idsLen);
}

/**
 * Resolve facet result from currentUnisonSearchResults for a sidebar category slug.
 */
function lookupUnisonFacetResultForCategory(category) {
    if (!currentUnisonSearchResults || !currentUnisonSearchResults.results) {
        return null;
    }
    const catToFacetIdFn = typeof categoryToFacetId === 'function' ? categoryToFacetId :
        (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
    if (!catToFacetIdFn) return null;
    const facetId = catToFacetIdFn(category);
    if (!facetId) return null;
    const r = currentUnisonSearchResults.results;
    let norm = typeof window.normalizeFacetId === 'function'
        ? window.normalizeFacetId(facetId)
        : facetId.toString().trim().toUpperCase().replace(/-/g, '_');
    if (norm === 'DATA_SETS' || norm === 'DATA_SET') {
        norm = 'DATASET';
    }
    return r[norm] || r[facetId] || r[facetId.toString().toUpperCase()] || null;
}

/**
 * Resolve a facet entry inside a Unison results map for a sidebar category slug.
 * Uses the same key fallbacks as loadCategoryDataFromUnisonResults so counts stay
 * consistent when the API uses alternate facet keys or reports count 0 while ids/rows exist.
 */
function lookupFacetResultInUnisonMap(category, resultsMap) {
    if (!category || !resultsMap || typeof resultsMap !== 'object') {
        return null;
    }

    const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId
        : (typeof window !== 'undefined' && window.categoryToFacetId);

    let facetId = catToFacetId ? catToFacetId(category) : String(category).toUpperCase();
    if (!facetId) {
        return null;
    }

    if (facetId === 'DATA-SETS' || facetId === 'DATA_SETS') {
        facetId = 'DATASET';
    }

    let facetResult = resultsMap[facetId];
    const availableFacets = Object.keys(resultsMap);

    const hasData = (fr) => fr && (
        (typeof fr.count === 'number' && fr.count > 0) ||
        (Array.isArray(fr.rows) && fr.rows.length > 0) ||
        (fr.ids && (Array.isArray(fr.ids) ? fr.ids.length > 0 : (fr.ids.size || 0) > 0))
    );

    if (!facetResult || !hasData(facetResult)) {
        if (availableFacets.length === 1 && facetResult && !hasData(facetResult)) {
            return facetResult;
        }

        const singular = facetId.endsWith('S') ? facetId.slice(0, -1) : null;
        const plural = !facetId.endsWith('S') ? facetId + 'S' : null;
        const catLower = String(category).toLowerCase();

        const alternatives = [
            singular,
            plural,
            facetId.toUpperCase().replace(/-/g, '_'),
            facetId.toUpperCase().replace(/-/g, ''),
            facetId.toUpperCase(),
            facetId === 'DATAQUALITY' ? 'DATA_QUALITY' : null,
            facetId === 'DATA_QUALITY' ? 'DATAQUALITY' : null,
            catLower === 'attributes' ? 'ATTRIBUTE' : null,
            catLower === 'attribute' ? 'ATTRIBUTES' : null,
            catLower === 'data-sets' || catLower === 'dataset' ? 'DATASET' : null,
            catLower === 'business-area' ? 'BUSINESS_AREA' : null,
            catLower === 'legal-entity' ? 'LEGAL_ENTITY' : null,
            catLower === 'regulatory-theme' ? 'REGULATORY_THEME' : null,
            catLower === 'org-unit' ? 'ORG_UNIT' : null
        ].filter(Boolean);

        for (const alt of alternatives) {
            const altResult = resultsMap[alt];
            if (altResult && hasData(altResult)) {
                return altResult;
            }
        }
    }

    return facetResult || null;
}

/**
 * After updateAllFacets bookkeeping, push counts/indicators from the raw results map
 * into every visible sidebar facet so related facets (e.g. Data Sets when searching System)
 * show correct "X of Y" without requiring a click.
 */
function reconcileSidebarFacetCountsFromUnisonResults(resultsToUse) {
    if (!resultsToUse || typeof document === 'undefined') {
        return;
    }

    const canonicalFn = (typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function')
        ? window.canonicalCategoryKey
        : ((c) => c);

    const applied = [];
    document.querySelectorAll('.category-item[data-category]').forEach((item) => {
        const category = item.getAttribute('data-category');
        if (!category) {
            return;
        }

        const fr = lookupFacetResultInUnisonMap(category, resultsToUse);
        if (!fr) {
            return;
        }

        const cnt = effectiveFacetResultCount(fr);
        const backendTotal = (typeof fr.totalCount === 'number' && !isNaN(fr.totalCount)) ? fr.totalCount : 0;
        const total = Math.max(backendTotal, cnt);

        const canonicalCategory = typeof canonicalFn === 'function' ? canonicalFn(category) : category;

        if (typeof updateCategoryCount === 'function') {
            updateCategoryCount(canonicalCategory, cnt, total, true);
        }
        if (typeof updateFacetIndicator === 'function') {
            updateFacetIndicator(canonicalCategory, fr);
        }
        applied.push({ category: canonicalCategory, count: cnt, total });
    });

    if (typeof window !== 'undefined' && typeof window.unisonSearchDebugLog === 'function') {
        window.unisonSearchDebugLog('ui.reconcileSidebar', {
            appliedCount: applied.length,
            applied
        });
    }
}

/**
 * Update facet indicator for a specific category
 */
function updateFacetIndicator(category, facetResult) {
    const categoryItem = document.querySelector(`.category-item[data-category="${category}"]`);
    if (!categoryItem) return;

    // Remove old badge
    const oldBadge = categoryItem.querySelector('.filter-badge');
    if (oldBadge) oldBadge.remove();

    const hasActiveConditionsLocally = hasActiveConditionsForCategory(category);

    // Always merge with currentUnisonSearchResults so ids/rows count even when
    // facetResult has hasActiveFilter=true but count=0 (raw Gson / second pass),
    // and related facets get orange as soon as the graph returns hits.
    const unisonFr = lookupUnisonFacetResultForCategory(category);
    const localEC = effectiveFacetResultCount(facetResult);
    const unisonEC = effectiveFacetResultCount(unisonFr);
    const effectiveCount = Math.max(localEC, unisonEC);

    const hasActiveFilterFromBackend = !!(facetResult && facetResult.hasActiveFilter && localEC > 0);
    const hasResultsFromUnison = unisonEC > 0;

    const hasActiveFilter = hasActiveFilterFromBackend || hasActiveConditionsLocally || hasResultsFromUnison;

    // Check if this is the source facet (first FIND condition)
    const sourceFacetCategory = getSourceFacetCategory();
    const canonicalCategoryKeyFn = typeof canonicalCategoryKey === 'function' 
        ? canonicalCategoryKey 
        : (typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function' 
            ? window.canonicalCategoryKey 
            : (cat) => cat); // Fallback to identity function
    const isSourceFacet = sourceFacetCategory !== null && 
        (category === sourceFacetCategory || 
         canonicalCategoryKeyFn(category) === canonicalCategoryKeyFn(sourceFacetCategory));

    // Related facets should only be orange when they have actual results.
    // Source facet can stay highlighted based on active condition even if result is zero.
    const shouldHighlightRelatedFacet = effectiveCount > 0;
    const shouldShowIndicator = isSourceFacet ? hasActiveFilter : (hasActiveFilter && shouldHighlightRelatedFacet);

    if (shouldShowIndicator) {
        if (isSourceFacet) {
            // Source facet gets distinct styling (blue/purple)
            categoryItem.classList.add('is-source-facet');
            categoryItem.classList.remove('has-active-filter'); // Remove orange, use blue instead
        } else {
            // Related facets get orange styling
            categoryItem.classList.add('has-active-filter');
            categoryItem.classList.remove('is-source-facet'); // Ensure source facet class is removed for non-source facets
        }

        // Add badge with count if we have a count
        const countToShow = effectiveCount;
        if (countToShow > 0) {
            const badge = document.createElement('span');
            badge.className = 'filter-badge';
            badge.innerHTML = `
                <i class="fas fa-filter"></i>
                <span>${countToShow}</span>
            `;
            badge.title = `${countToShow} filtered results`;
            categoryItem.appendChild(badge);
        }
    } else {
        // Remove both classes if no active filter
        categoryItem.classList.remove('has-active-filter');
        categoryItem.classList.remove('is-source-facet');
    }

    // Note: updateCategoryCount is called in updateAllFacets before this function
    // So we don't need to call it here to avoid duplicate updates
}

/**
 * Clear all facet indicators (highlighting and filter badges) from all category items
 */
function clearAllFacetIndicators() {
    const categoryItems = document.querySelectorAll('.category-item');
    categoryItems.forEach(item => {
        item.classList.remove('has-active-filter');
        item.classList.remove('is-source-facet');
        const badge = item.querySelector('.filter-badge');
        if (badge) {
            badge.remove();
        }
    });
}

/**
 * Update facet indicators for all facets in Unison Search results
 */
function updateFacetIndicators(unisonResults) {
    if (!unisonResults || !unisonResults.results) return;

    for (const [facetId, facetResult] of Object.entries(unisonResults.results)) {
        // Prefer facetIdToCategorySlug from search-utils.js (uses FACET_TO_CATEGORY mapping)
        const facetIdToCategoryFn = (typeof facetIdToCategorySlug === 'function') ? facetIdToCategorySlug :
            (typeof window !== 'undefined' && typeof window.facetIdToCategorySlug === 'function' ? window.facetIdToCategorySlug :
                (typeof facetIdToCategory === 'function' ? facetIdToCategory :
                    (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null)));
        const category = facetIdToCategoryFn ? facetIdToCategoryFn(facetId) : null;
        if (!category) {
            console.warn(`[updateFacetIndicators] No category mapping for facet ${facetId}, skipping`);
            continue;
        }
        updateFacetIndicator(category, facetResult);
    }
}

/**
 * Update all facet indicators based on active search conditions
 * This ensures has-active-filter class is preserved for all facets with active conditions
 * Works regardless of whether currentUnisonSearchResults exists
 */
function updateAllFacetIndicatorsFromConditions() {
    // Get current search conditions
    const currentSearchConditions = (typeof window !== 'undefined' && window.searchConditions && Array.isArray(window.searchConditions))
        ? window.searchConditions
        : (typeof searchConditions !== 'undefined' && Array.isArray(searchConditions))
            ? searchConditions
            : [];

    // Filter to only active (non-muted) conditions
    const activeConditions = currentSearchConditions.filter(c => !c.muted);

    // Note: We continue even if there are no active conditions
    // to update all facets with results from currentUnisonSearchResults

    // Get all unique categories with active conditions
    const categoriesWithConditions = new Set();
    activeConditions.forEach(condition => {
        if (condition.category) {
            categoriesWithConditions.add(condition.category);
        }
    });


    // Get categoryToFacetId and facetIdToCategory functions
    const catToFacetIdFn = typeof categoryToFacetId === 'function' ? categoryToFacetId :
        (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
    const facetIdToCategoryFn = typeof facetIdToCategory === 'function' ? facetIdToCategory :
        (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null);

    // Normalize facet ID helper
    const normalizeFacetIdForResults = (facetId) => {
        if (!facetId) return facetId;
        // Use normalizeFacetIdToCanonical from facet-normalization.js if available (single source of truth)
        if (typeof window !== 'undefined' && typeof window.normalizeFacetIdToCanonical === 'function') {
            return window.normalizeFacetIdToCanonical(facetId) || facetId;
        }
        // Fallback to local normalization for backward compatibility
        const norm = facetId.toString().trim().toUpperCase().replace(/-/g, '_');
        switch (norm) {
            case 'DATA_SETS':
            case 'DATA_SET':
            case 'DATASETS':
                return 'DATASET';
            default:
                return norm;
        }
    };

    // Prefer client-side display-filtered row count for the active table category so we do not
    // overwrite updateAllFacets counts with raw Unison facet counts (e.g. People 4 vs 1 after Profile filter).
    const displayCountForCategory = (category, backendCount) => {
        const canonicalFn = typeof canonicalCategoryKey === 'function' ? canonicalCategoryKey
            : (typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function'
                ? window.canonicalCategoryKey : null);
        if (
            canonicalFn &&
            typeof currentFilteredData !== 'undefined' &&
            Array.isArray(currentFilteredData) &&
            typeof currentFilteredCategory !== 'undefined' &&
            currentFilteredCategory != null &&
            canonicalFn(currentFilteredCategory) === canonicalFn(category)
        ) {
            return currentFilteredData.length;
        }
        return backendCount;
    };

    // Update indicators for each category with active conditions
    categoriesWithConditions.forEach(category => {
        if (typeof updateFacetIndicator !== 'function') {
            console.warn('[updateAllFacetIndicatorsFromConditions] updateFacetIndicator function not available');
            return;
        }

        // Try to get facet result from currentUnisonSearchResults if available
        let facetResult = null;
        if (currentUnisonSearchResults && currentUnisonSearchResults.results && catToFacetIdFn) {
            const facetId = catToFacetIdFn(category);
            const normalizedFacetId = normalizeFacetIdForResults(facetId);
            facetResult = currentUnisonSearchResults.results[normalizedFacetId] ||
                currentUnisonSearchResults.results[facetId];
        }

        if (facetResult) {
            // Update with actual result from Unison Search (prefer row count when rows exist)
            const searchResultCount = effectiveFacetResultCount(facetResult);

            const totalCount = (facetResult.totalCount !== undefined && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                ? Math.max(facetResult.totalCount, searchResultCount) : searchResultCount;
            if (typeof updateCategoryCount === 'function') {
                const filteredCount = displayCountForCategory(category, searchResultCount);
                updateCategoryCount(category, filteredCount, totalCount, true);
            }
            updateFacetIndicator(category, facetResult);
        } else {
            if (typeof updateCategoryCount === 'function') {
                updateCategoryCount(category, 0, 0, true);
            }

            // Update indicator
            updateFacetIndicator(category, { hasActiveFilter: true, count: 0 });
        }
    });

    // Also update ALL visible facets that have results in currentUnisonSearchResults
    // This ensures orange color is preserved for all facets with results, not just those with active conditions
    // AND updates counts for all facets
    if (currentUnisonSearchResults && currentUnisonSearchResults.results) {
        const allCategoryItems = document.querySelectorAll('.category-item');
        allCategoryItems.forEach(item => {
            const category = item.getAttribute('data-category');
            if (!category) {
                return;
            }

            // Try to get facet result for this category
            if (catToFacetIdFn) {
                const facetId = catToFacetIdFn(category);
                const normalizedFacetId = normalizeFacetIdForResults(facetId);
                const facetResult = currentUnisonSearchResults.results[normalizedFacetId] ||
                    currentUnisonSearchResults.results[facetId];

                if (facetResult) {
                    const searchResultCount = effectiveFacetResultCount(facetResult);
                    const totalCount = (facetResult.totalCount !== undefined && typeof facetResult.totalCount === 'number' && !isNaN(facetResult.totalCount))
                        ? Math.max(facetResult.totalCount, searchResultCount) : searchResultCount;
                    if (typeof updateCategoryCount === 'function') {
                        const filteredCount = displayCountForCategory(category, searchResultCount);
                        updateCategoryCount(category, filteredCount, totalCount, true);
                    }
                    updateFacetIndicator(category, facetResult);
                } else if (!categoriesWithConditions.has(category)) {
                    if (typeof updateCategoryCount === 'function') {
                        const canonical = (typeof window.canonicalCategoryKey === 'function') ? window.canonicalCategoryKey(category) : category;
                        const preservedTotal = (typeof window !== 'undefined' && typeof window.resolveZeroFacetTotal === 'function')
                            ? window.resolveZeroFacetTotal(canonical) : 0;
                        updateCategoryCount(category, 0, preservedTotal, true);
                    }
                    // Ensure stale indicator styling is removed when this facet has no
                    // result in the current search cycle and no active condition.
                    if (typeof updateFacetIndicator === 'function') {
                        updateFacetIndicator(category, { hasActiveFilter: false, count: 0, ids: [], rows: [] });
                    }
                }
            }
        });
    } else {
        // No Unison Search results - refresh all facets from cache
        // This ensures counts are updated even when there are no search results
        const allCategoryItems = document.querySelectorAll('.category-item');
        allCategoryItems.forEach(item => {
            const category = item.getAttribute('data-category');
            if (!category) {
                return;
            }

            // Skip if this category has active conditions (will be handled above)
            if (categoriesWithConditions.has(category)) {
                return;
            }

            const canonical = (typeof window.canonicalCategoryKey === 'function') ? window.canonicalCategoryKey(category) : category;
            const display = (typeof window.getDisplayCounts === 'function') ? window.getDisplayCounts(canonical) : null;
            if (display && typeof updateCategoryCount === 'function') {
                const hasActiveSearch = activeConditions.length > 0;
                const hasSelectedItem = typeof getSelectedItem === 'function' && getSelectedItem() !== null;
                const cVal = (hasActiveSearch || hasSelectedItem) ? 0 : display.count;
                let tVal = typeof display.total === 'number' ? display.total : 0;
                if ((hasActiveSearch || hasSelectedItem) && typeof window.resolveZeroFacetTotal === 'function') {
                    const z = window.resolveZeroFacetTotal(canonical);
                    if (z > 0) tVal = Math.max(tVal, z);
                }
                updateCategoryCount(category, cVal, tVal, false);
            }
        });
    }
}

/**
 * Update all facets with Unison Search results.
 * This implements automatic cross-facet filtering.
 * 
 * @param {Object} unisonResults - Results from executeUnisonSearch {results: {FACET_ID: {ids, count, hasActiveFilter}}}
 * @param {string} activeCategory - Currently active category to display in table
 */
async function updateAllFacets(unisonResults, activeCategory) {
    // Normalize facet IDs to avoid duplicates (e.g., DATASET vs DATA_SETS)
    // Uses normalizeFacetIdToCanonical from facet-normalization.js if available (single source of truth)
    const normalizeFacetIdForResults = (facetId) => {
        if (!facetId) return facetId;
        // Use normalizeFacetIdToCanonical from facet-normalization.js if available
        if (typeof window !== 'undefined' && typeof window.normalizeFacetIdToCanonical === 'function') {
            return window.normalizeFacetIdToCanonical(facetId) || facetId;
        }
        // Fallback to local normalization for backward compatibility
        const norm = facetId.toString().trim().toUpperCase().replace(/-/g, '_');
        switch (norm) {
            case 'DATA_SETS':
            case 'DATA_SET':
            case 'DATASETS':
                return 'DATASET';
            case 'CHANGEREQUEST':
            case 'CHANGE_REQUEST':
                return 'CHANGE_REQUESTS';
            default:
                return norm;
        }
    };

    const mergeFacetResults = (existing, incoming) => {
        if (!existing) return incoming;
        if (!incoming) return existing;

        const toArr = (v) => Array.isArray(v) ? v : (v ? Array.from(v) : []);
        const ids = new Set([...toArr(existing.ids), ...toArr(incoming.ids)]);

        const rowsMap = new Map();
        const putRows = (rows) => {
            if (!Array.isArray(rows)) return;
            rows.forEach(r => {
                const rowId = r?.id ?? r?.ID ?? r?.Id ?? null;
                const key = rowId !== null ? rowId : rowsMap.size; // fallback preserves ordering
                if (!rowsMap.has(key)) rowsMap.set(key, r);
            });
        };
        putRows(existing.rows);
        putRows(incoming.rows);

        const mergeDepth = (a, b) => {
            const out = { ...(a || {}) };
            Object.entries(b || {}).forEach(([k, v]) => {
                if (out[k] === undefined) {
                    out[k] = v;
                } else {
                    out[k] = Math.min(out[k], v);
                }
            });
            return out;
        };

        const mergedDepth = mergeDepth(existing.depthById, incoming.depthById);
        const hasActiveFilter = Boolean(existing.hasActiveFilter) || Boolean(incoming.hasActiveFilter);

        // Prefer provided counts; otherwise fall back to rows/ids length
        const countCandidates = [
            existing.count, incoming.count,
            Array.isArray(existing.rows) ? existing.rows.length : undefined,
            Array.isArray(incoming.rows) ? incoming.rows.length : undefined,
            ids.size
        ].filter((n) => typeof n === 'number' && !isNaN(n));
        const mergedCount = countCandidates.length ? Math.max(...countCandidates) : 0;

        return {
            ids: Array.from(ids),
            count: mergedCount,
            hasActiveFilter,
            depthById: mergedDepth,
            rows: Array.from(rowsMap.values())
        };
    };

    // Build normalized results map so UI sees a single dataset facet but data stays merged
    const normalizedResults = {};
    if (unisonResults?.results) {
        Object.entries(unisonResults.results).forEach(([facetId, facetResult]) => {
            const canonical = normalizeFacetIdForResults(facetId);
            if (!normalizedResults[canonical]) {
                normalizedResults[canonical] = facetResult;
            } else {
                normalizedResults[canonical] = mergeFacetResults(normalizedResults[canonical], facetResult);
            }
        });
    }

    // Check if we have results BEFORE storing them (let so we can assign frozen shape later)
    let resultsToUse = normalizedResults;

    // If no results, clear state and show no data for the active category
    // But still update ALL visible facets to refresh their counts
    if (!resultsToUse || Object.keys(resultsToUse).length === 0) {
        console.warn('[Unison Search] No results to update facets');
        // IMPORTANT: Clear all cached results to prevent using stale data
        currentUnisonSearchResults = null;
        currentFilteredData = null;
        currentFilteredCategory = null;

        // Update ALL visible facets, not just the active one
        const allCategoryItems = document.querySelectorAll('.category-item');
        const canonicalKeyFn = typeof canonicalCategoryKey === 'function' ? canonicalCategoryKey : (typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function' ? window.canonicalCategoryKey : (c) => c);
        allCategoryItems.forEach(item => {
            const category = item.getAttribute('data-category');
            if (category) {
                if (typeof updateCategoryCount === 'function') {
                    const canonicalCat = canonicalKeyFn(category);
                    const preservedTotal = (typeof window !== 'undefined' && typeof window.resolveZeroFacetTotal === 'function')
                        ? window.resolveZeroFacetTotal(canonicalCat) : 0;
                    updateCategoryCount(category, 0, preservedTotal, true);
                }
                if (typeof updateFacetIndicator === 'function') {
                    updateFacetIndicator(category, { hasActiveFilter: false, count: 0 });
                }
            }
        });

        if (activeCategory) {
            showNoDataMessage(activeCategory);
        }
        return;
    }

    // Only store results if we actually have data
    currentUnisonSearchResults = { ...unisonResults, results: resultsToUse };

    const resultKeys = Object.keys(resultsToUse || {});
    const resultCounts = {};
    for (const key of resultKeys) {
        const result = resultsToUse[key];
        resultCounts[key] = {
            count: result?.count || 0,
            hasRows: Array.isArray(result?.rows) ? result.rows.length : 0
        };
    }
    // Updating facets (debug disabled)
    if (false) {
        console.log('[Unison Search] Updating facets:', {
            resultKeys: resultKeys,
            resultCounts: resultCounts,
            activeCategory: activeCategory
        });
    }
    // DEBUG LOG
    console.log('[UNISON-DEBUG][updateAllFacets] Facet results from backend:', resultKeys, resultCounts, '| active:', activeCategory);

    // Get all visible category items from the sidebar to update ALL facets, not just those in results
    const allCategoryItems = document.querySelectorAll('.category-item');
    const allCategories = new Set();
    allCategoryItems.forEach(item => {
        const category = item.getAttribute('data-category');
        if (category) {
            allCategories.add(category);
        }
    });


    // Helper function to get category from facetId
    // Prefer facetIdToCategorySlug from search-utils.js (uses FACET_TO_CATEGORY mapping)
    // Fallback to facetIdToCategory from search-api.js
    const facetIdToCategoryFn = (typeof facetIdToCategorySlug === 'function') ? facetIdToCategorySlug :
        (typeof window !== 'undefined' && typeof window.facetIdToCategorySlug === 'function' ? window.facetIdToCategorySlug :
            (typeof facetIdToCategory === 'function' ? facetIdToCategory :
                (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null)));
    const categoryToFacetIdFn = typeof categoryToFacetId === 'function' ? categoryToFacetId :
        (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);

    // Get visible sidebar categories to only update counts for visible modules
    // Use canonical keys to ensure consistent matching
    const visibleSidebarCategories = new Set();
    const canonicalCategoryKeyFn = typeof canonicalCategoryKey === 'function' 
        ? canonicalCategoryKey 
        : (typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function' 
            ? window.canonicalCategoryKey 
            : (cat) => cat ? cat.toString().toLowerCase().trim().replace(/\s+/g, '-').replace(/_/g, '-') : '');
    
    allCategoryItems.forEach(item => {
        const cat = item.getAttribute('data-category');
        if (cat) {
            const canonical = canonicalCategoryKeyFn(cat);
            visibleSidebarCategories.add(canonical);
        }
    });

    // Also use UNISON_DEFAULTS if available to get visible modules
    if (window.UNISON_DEFAULTS && window.UNISON_DEFAULTS.facets) {
        window.UNISON_DEFAULTS.facets.forEach(facet => {
            if (facet.visibility === true) {
                const categoryKey = canonicalCategoryKeyFn(facet.id || facet.slug);
                visibleSidebarCategories.add(categoryKey);
            }
        });
    }

    // Normalize facet ID function
    const normalizeFacetIdFn = typeof normalizeFacetId === 'function'
        ? normalizeFacetId
        : (typeof window !== 'undefined' && typeof window.normalizeFacetId === 'function'
            ? window.normalizeFacetId
            : (fid) => fid ? fid.toString().toUpperCase().trim() : fid);

    // Freeze object shape: ensure every facetResult has { count: number, totalCount: number } before use.
    // Dev assertion: set window.__UNISON_STRICT_FACET_CONTRACT__ = true to throw if backend omits totalCount.
    const isUnisonFacetContractStrict = typeof window !== 'undefined' &&
        (window.__UNISON_STRICT_FACET_CONTRACT__ === true || window.__UNISON_DEV__ === true ||
            (window.location && window.location.hostname === 'localhost'));
    function freezeFacetResultShape(results) {
        if (!results || typeof results !== 'object') return results;
        const frozen = {};
        for (const [facetId, fr] of Object.entries(results)) {
            if (!fr || typeof fr !== 'object') {
                frozen[facetId] = fr;
                continue;
            }
            if (isUnisonFacetContractStrict && !('totalCount' in fr)) {
                throw new Error('Backend contract violated: facetResult missing totalCount for facet "' + facetId + '"');
            }
            // Prefer row length when rows exist (aligned with table and segment filtering).
            const count = effectiveFacetResultCount(fr);
            const backendTotal = typeof fr.totalCount === 'number' && !isNaN(fr.totalCount) ? fr.totalCount : 0;
            let totalCount = Math.max(backendTotal, count);
            if (totalCount === 0 && typeof window !== 'undefined' && typeof window.resolveZeroFacetTotal === 'function'
                && typeof window.facetIdToCategorySlug === 'function' && typeof window.canonicalCategoryKey === 'function') {
                const nf = normalizeFacetIdFn(facetId);
                const slug = window.facetIdToCategorySlug(nf);
                if (slug) {
                    const can = window.canonicalCategoryKey(slug);
                    const z = window.resolveZeroFacetTotal(can);
                    if (z > 0) totalCount = z;
                }
            }
            frozen[facetId] = { ...fr, count, totalCount };
        }
        return frozen;
    }
    resultsToUse = freezeFacetResultShape(resultsToUse || {});

    // Build canonical results map to handle facet ID variations (ATTRIBUTES vs ATTRIBUTE, etc.)
    const resultsByFacetCanonical = {};
    for (const [facetId, facetResult] of Object.entries(resultsToUse || {})) {
        const canonical = normalizeFacetIdFn(facetId);
        resultsByFacetCanonical[canonical] = facetResult;
    }

    // Track categories updated from Unison to prevent cache refresh from overwriting
    const updatedFromUnison = new Set();

    // First, update all facets that have results (only if visible)
    for (const [facetId, facetResult] of Object.entries(resultsToUse)) {
        // Normalize facet ID (ATTRIBUTES → ATTRIBUTE, etc.)
        const normalizedFacetId = normalizeFacetIdFn(facetId);

        // ✅ Use strict mapping - don't fallback to lowercase
        const category = facetIdToCategoryFn ? facetIdToCategoryFn(normalizedFacetId) : null;

        if (!category) {
            console.warn(`[updateAllFacets] No category mapping for facet ${normalizedFacetId}, skipping`);
            continue;
        }

        // Normalize category to canonical key for consistent matching
        const canonicalCategory = canonicalCategoryKeyFn(category);

        // Skip if category is not visible in sidebar (only check if we have sidebar items)
        if (allCategoryItems.length > 0 && !visibleSidebarCategories.has(canonicalCategory)) {
            // Category doesn't exist in sidebar - skip silently
            continue;
        }
        // Shape guaranteed by freezeFacetResultShape: { count: number, totalCount: number }
        const searchResultCount = effectiveFacetResultCount(facetResult);
        const baseTotal = Math.max(facetResult.totalCount || 0, searchResultCount);

        if (typeof window !== 'undefined' && window.setFacetCountSource) window.setFacetCountSource('search');
        if (typeof updateCategoryCount === 'function') {
            updateCategoryCount(canonicalCategory, searchResultCount, baseTotal, true);
            updatedFromUnison.add(canonicalCategory); // Track this category as updated from Unison
        } else {
            console.warn(`[updateAllFacets] updateCategoryCount function not available`);
        }

        // Add visual indicator if facet has active filter (will also update count)
        updateFacetIndicator(canonicalCategory, facetResult);
    }

    // Now update ALL visible facets in sidebar - for facets not in results, refresh their counts from cache
    // Update counts to zero for facets in search but not in results
    // This ensures facets with zero results show zero from the beginning
    // Use global searchConditions or window.searchConditions
    // Define currentSearchConditions BEFORE using it in the forEach loop below
    const currentSearchConditions = (typeof window !== 'undefined' && window.searchConditions && Array.isArray(window.searchConditions))
        ? window.searchConditions
        : (typeof searchConditions !== 'undefined' && Array.isArray(searchConditions))
            ? searchConditions
            : [];

    // This ensures all facets show updated counts immediately, not just the ones in search results
    // Only update visible categories
    allCategories.forEach(category => {
        // Normalize category to canonical key for consistent matching
        const canonicalCategory = canonicalCategoryKeyFn(category);
        
        // Skip if category is not visible in sidebar (only check if we have sidebar items)
        if (allCategoryItems.length > 0 && !visibleSidebarCategories.has(canonicalCategory)) {
            return; // Skip hidden modules
        }

        // Check if this category was already updated above (has a result)
        const catToFacetId = categoryToFacetIdFn ? categoryToFacetIdFn(canonicalCategory) : canonicalCategory.toUpperCase();
        const normalizedFacetId = normalizeFacetIdFn(catToFacetId);
        const hasResult = resultsByFacetCanonical[normalizedFacetId];

                if (!hasResult) {
            if (updatedFromUnison.has(canonicalCategory)) {
                // already updated above
            } else {
                if (typeof updateCategoryCount === 'function') {
                    const preservedTotal = (typeof window !== 'undefined' && typeof window.resolveZeroFacetTotal === 'function')
                        ? window.resolveZeroFacetTotal(canonicalCategory) : 0;
                    updateCategoryCount(canonicalCategory, 0, preservedTotal, true);
                }
            }
        }
    });

    const activeSearchConditionsOnly = currentSearchConditions.filter(c => !c.muted);
    if (activeSearchConditionsOnly.length > 0) {
        // Facets that are part of the *active* query only (muted rows must not drive zeroing / indicators)
        const facetsInSearch = new Set();
        const catToFacetIdFn = typeof categoryToFacetId === 'function' ? categoryToFacetId :
            (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);

        activeSearchConditionsOnly.forEach(condition => {
            if (condition.category) {
                const facetId = catToFacetIdFn ? catToFacetIdFn(condition.category) : condition.category.toUpperCase();
                const normalizedFacetId = normalizeFacetIdForResults(facetId);
                facetsInSearch.add(normalizedFacetId);
            }
        });

        // Set zero counts for facets in search but not in results
        // Also ensure facets with active conditions get their indicators updated
        facetsInSearch.forEach(facetId => {
            // Prefer facetIdToCategorySlug from search-utils.js (uses FACET_TO_CATEGORY mapping)
            // Fallback to facetIdToCategory from search-api.js
            const facetIdToCategoryFn = (typeof facetIdToCategorySlug === 'function') ? facetIdToCategorySlug :
                (typeof window !== 'undefined' && typeof window.facetIdToCategorySlug === 'function' ? window.facetIdToCategorySlug :
                    (typeof facetIdToCategory === 'function' ? facetIdToCategory :
                        (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null)));
            const category = facetIdToCategoryFn ? facetIdToCategoryFn(facetId) : facetId.toLowerCase();

            // Use canonical facet ID to check if results exist
            const canonicalFacet = normalizeFacetIdFn(facetId);
            if (!resultsByFacetCanonical[canonicalFacet]) {
                if (updatedFromUnison.has(category)) {
                } else {
                    if (typeof updateCategoryCount === 'function') {
                        const canonicalCat = canonicalCategoryKeyFn ? canonicalCategoryKeyFn(category) : category;
                        const preservedTotal = (typeof window !== 'undefined' && typeof window.resolveZeroFacetTotal === 'function')
                            ? window.resolveZeroFacetTotal(canonicalCat) : 0;
                        updateCategoryCount(category, 0, preservedTotal, true);
                    }
                }
            } else {
                // Facet has results, skip zeroing

                // Facet is in results, but ensure indicator is updated to reflect active conditions
                // This handles cases where hasActiveFilter from backend might not be accurate
                if (typeof updateFacetIndicator === 'function') {
                    const facetResult = resultsToUse[facetId];
                    updateFacetIndicator(category, facetResult);
                }
            }

            // Update indicator for facets without results - it will check for active conditions locally
            if (!resultsByFacetCanonical[canonicalFacet] && typeof updateFacetIndicator === 'function') {
                updateFacetIndicator(category, { hasActiveFilter: false, count: 0 });
            }
        });

        // Also update indicators for all facets that have active conditions but might not be in facetsInSearch
        // This ensures all facets with conditions are properly highlighted
        const activeConditions = currentSearchConditions.filter(c => !c.muted);
        const categoriesWithConditions = new Set();
        activeConditions.forEach(condition => {
            if (condition.category) {
                categoriesWithConditions.add(condition.category);
            }
        });

        // Update indicators for all categories with active conditions
        categoriesWithConditions.forEach(category => {
            if (typeof updateFacetIndicator === 'function') {
                // Check if we already have a result for this category
                const catToFacetIdFn = typeof categoryToFacetId === 'function' ? categoryToFacetId :
                    (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
                if (catToFacetIdFn) {
                    const facetId = catToFacetIdFn(category);
                    const normalizedFacetId = normalizeFacetIdForResults(facetId);
                    const facetResult = resultsToUse[normalizedFacetId];

                    if (facetResult) {
                        // Update with actual result
                        updateFacetIndicator(category, facetResult);
                    } else {
                        // No result but has active conditions - update to show indicator
                        updateFacetIndicator(category, { hasActiveFilter: true, count: 0 });
                    }
                }
            }
        });
    }

    reconcileSidebarFacetCountsFromUnisonResults(resultsToUse);

    // No multi-source refresh: categoryCounts is only from search; display comes from getDisplayCounts (search then baseline)

    // Load and display data for active category
    if (activeCategory) {
        // Show loading indicator for active category data loading
        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable && !dataTable.classList.contains('loading')) {
            dataTable.classList.add('loading');
            if (typeof renderSkeleton === 'function') {
                renderSkeleton(dataTable);
            }
        }

        // Get categoryToFacetId function (from search-api.js or window)
        const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId :
            (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);

        // ✅ Use strict mapping - get canonical facet ID
        let activeFacetId = catToFacetId ? catToFacetId(activeCategory) : null;

        if (!activeFacetId) {
            console.error(`[Unison Search] No facet ID mapping for category ${activeCategory}`);
            hideLoadingIndicator();
            return;
        }

        const availableResultKeys = Object.keys(resultsToUse || {});
        let activeFacetResult = resultsToUse[activeFacetId];

        // Check if the found result has actual data (count > 0 or has rows)
        const hasData = activeFacetResult && (
            (activeFacetResult.count !== undefined && activeFacetResult.count > 0) ||
            (Array.isArray(activeFacetResult.rows) && activeFacetResult.rows.length > 0) ||
            (activeFacetResult.ids && (Array.isArray(activeFacetResult.ids) ? activeFacetResult.ids.length > 0 : activeFacetResult.ids.size > 0))
        );

        // ✅ No more alternatives - canonical mapping should work now


        if (activeFacetResult) {
            const rows = Array.isArray(activeFacetResult.rows) ? activeFacetResult.rows : [];
            const idsArray = activeFacetResult.ids
                ? (Array.isArray(activeFacetResult.ids) ? activeFacetResult.ids : Array.from(activeFacetResult.ids))
                : [];

            let data = rows;

            // Always fetch full data by IDs to ensure all columns are present
            // The rows from Unison Search may only contain partial data (e.g., only email for people)
            // Fetching by IDs ensures we get complete object data with all columns
            if (idsArray.length > 0) {

                // Show loading indicator while fetching (dataTable already declared above)
                if (dataTable) {
                    dataTable.classList.add('loading');
                    if (typeof renderSkeleton === 'function') {
                        renderSkeleton(dataTable);
                    }
                }

                try {
                    const fullData = await fetchFacetDataByIds(activeCategory, idsArray);
                    if (fullData && fullData.length > 0) {
                        // Use full data instead of partial rows
                        data = fullData;
                    } else if (data && data.length > 0) {
                        // Fallback to rows if fetchFacetDataByIds returns empty
                    }
                } catch (error) {
                    console.error(`[Unison Search] Error fetching full data:`, error);
                    // Fallback to rows if fetch fails
                } finally {
                    // Hide loading indicator after fetch completes
                    if (dataTable) {
                        dataTable.classList.remove('loading');
                    }
                }
            } else if (!data || data.length === 0) {
                // If no IDs and no rows, show no data
            }

            if (data && data.length > 0) {
                // Same as loadCategoryDataFromUnisonResults: apply client-only display filters (isDisplayFilter)
                // after fetchFacetDataByIds. updateAllFacets used to skip this, so People profile filters
                // never affected the table after Apply / Unison refresh.
                let displayData = data;
                if (typeof applyDisplayFiltersToRows === 'function') {
                    displayData = applyDisplayFiltersToRows(activeCategory, data);
                }
                data = displayData;

                currentFilteredData = data;
                currentFilteredCategory = activeCategory;
                categoryDataCache.set(activeCategory, { data: data, category: activeCategory });

                if (typeof generateTable === 'function') {
                    generateTable(data, null, activeCategory);
                }

                // Update dashboard with filtered data if dashboard view is active
                if (typeof isDashboardViewActive === 'function' && isDashboardViewActive()) {
                    if (typeof updateDashboardWithFilteredData === 'function') {
                        await updateDashboardWithFilteredData(activeCategory, data);
                    }
                }

                // Always use data.length as the displayed count - it reflects what's actually shown.
                // activeFacetResult.count from backend may be 0 for related facets (e.g. People, Role)
                // even when there are real results (the backend counts relationships, not objects).
                const countToUse = data.length;

                const backendTotal = (activeFacetResult && typeof activeFacetResult.totalCount === 'number' && !isNaN(activeFacetResult.totalCount))
                    ? activeFacetResult.totalCount : 0;
                // totalCount = max of backend total and actual displayed count
                const totalCount = Math.max(backendTotal, countToUse);
                if (typeof updateCategoryCount === 'function') {
                    updateCategoryCount(activeCategory, countToUse, totalCount, true);
                }

                const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;
                if (typeof cacheModuleData === 'function') {
                    cacheModuleData(activeCategory, data, signature);
                }
            } else {
                showNoDataMessage(activeCategory);
            }
        } else {
            showNoDataMessage(activeCategory);
        }

        // Hide loading indicator after data loading completes (dataTable already declared above)
        if (dataTable) {
            dataTable.classList.remove('loading');
        }
    }
}

async function executeMultiConditionSearch() {

    // Declare at top to avoid TDZ when this function is called from a scope that also has activeConditions (e.g. facet click handler)
    const activeConditions = searchConditions.filter(c => !c.muted);

    // Hide suggestions dropdown when search is executed
    const suggestionsDropdown = document.querySelector('.search-suggestions');
    if (suggestionsDropdown) {
        suggestionsDropdown.style.display = 'none';
        suggestionsDropdown.innerHTML = '';
        currentSuggestionsData = null;
        currentSuggestionsCategory = null;
    }

    if (searchConditions.length === 0) {
        const category = getActiveCategoryWithFallback();
        currentFilteredData = null;
        currentFilteredCategory = null;
        await loadCategoryData(category);
        return;
    }

    // Remember which tab the user is currently viewing before we switch anything
    const userActiveCategory = getActiveCategory();

    // Get the category to display and run search for.
    // For multi-condition (FIND + AND/OR/NOT): prefer root facet (first FIND) so e.g. "FIND data-sets * NOT people *" shows Data Sets (18) not People (6).
    // Priority: 1) First FIND condition's category (root facet), 2) Currently active category in UI, 3) First condition's category, 4) Fallback
    const firstFindCondition = activeConditions.find(c => c.operator === 'FIND');
    let category = firstFindCondition ? firstFindCondition.category : null;
    if (!category) {
        category = userActiveCategory;
        if (!category && activeConditions.length > 0) {
            category = activeConditions[0].category;
        }
        if (!category) {
            category = getActiveCategoryWithFallback();
        }
    }

    // Skip the early switch when the user is on a facet with an active display-filter
    // condition (e.g. People tab while FIND is System). The correct tab will be chosen
    // by the displayCategory resolution after results arrive.
    const userOnDisplayFilterFacet = activeConditions.some(c =>
        c.isDisplayFilter &&
        typeof searchConditionCategoryMatches === 'function' &&
        searchConditionCategoryMatches(c.category, userActiveCategory)
    );
    // Update active category in UI to match the category being searched
    if (category && typeof setActiveCategory === 'function' && !userOnDisplayFilterFacet) {
        setActiveCategory(category);
    }

    if (activeConditions.length === 0) {
        currentFilteredData = null;
        currentFilteredCategory = null;
        await loadCategoryData(category);
        return;
    }

    // Check if we should use Unison Search (cross-facet search)
    // Use Unison Search if there are conditions in different facets OR if there's a FIND condition
    // Note: exclude display-only filter conditions from this calculation — they are never sent to
    // the backend and should not affect which code path is chosen.
    const nonDisplayConditions = activeConditions.filter(c => !c.isDisplayFilter);
    const uniqueFacets = new Set(nonDisplayConditions.map(c => {
        const facetId = typeof categoryToFacetId === 'function' ? categoryToFacetId(c.category) : c.category.toUpperCase();
        return facetId;
    }));

    const hasFindCondition = nonDisplayConditions.some(c => c.operator === 'FIND');
    const shouldUseUnisonSearch = uniqueFacets.size > 1 || hasFindCondition;


    // Try to get executeUnisonSearch from window if not in scope
    const unisonSearchFn = typeof executeUnisonSearch === 'function' ? executeUnisonSearch :
        (typeof window !== 'undefined' && typeof window.executeUnisonSearch === 'function' ? window.executeUnisonSearch : null);

    if (shouldUseUnisonSearch && unisonSearchFn) {
        // Use Unison Search API for cross-facet search
        try {
            // Block category-switch handlers from re-fetching while search is in progress
            isSearchExecuting = true;

            // Show full page loading indicator
            if (typeof showLoading === 'function') {
                showLoading('executeMultiConditionSearch');
            }

            const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
            if (dataTable) {
                dataTable.classList.add('loading');
                renderSkeleton(dataTable);
            }

            // Convert conditions to Unison Search format (backend requires FIND first, then AND/OR/NOT)
            const searches = [];
            // Display-only filter conditions are sent with displayFilter:true so UnisonSearchService
            // registers facetFilters and skips intersection (row-data filtering on that facet).
            activeConditions.forEach(condition => {
                const facetId = typeof categoryToFacetId === 'function' ? categoryToFacetId(condition.category) : condition.category.toUpperCase();
                let filters = condition.filters || {};
                if (Object.keys(filters).length === 0 && typeof buildFiltersObject === 'function') {
                    // Only apply the filter panel's filters when the condition belongs to the
                    // currently active category.  Applying People filters to a System condition
                    // (or vice-versa) sends the wrong WHERE clause to the backend and returns 0.
                    const currentActiveCat = typeof getActiveCategoryWithFallback === 'function'
                        ? getActiveCategoryWithFallback() : null;
                    if (!currentActiveCat || condition.category === currentActiveCat) {
                        const builtFilters = buildFiltersObject();
                        if (Object.keys(builtFilters).length > 0) {
                            filters = builtFilters;
                        }
                    }
                }

                const resolvedSearchFields = (condition.searchFields && Object.keys(condition.searchFields).length > 0)
                    ? condition.searchFields
                    : (typeof getEffectiveSearchFieldsForCategory === 'function'
                        ? getEffectiveSearchFieldsForCategory(condition.category)
                        : null);

                const bulkTerms = Array.isArray(condition.bulkTerms)
                    ? condition.bulkTerms.filter(term => term && String(term).trim() !== '').map(term => String(term).trim())
                    : [];

                if (bulkTerms.length > 0) {
                    bulkTerms.forEach((bulkTerm, index) => {
                        const searchItem = {
                            operator: index === 0 ? (condition.operator || 'FIND').toUpperCase() : 'OR',
                            facet: facetId,
                            keyword: bulkTerm,
                            filters: filters
                        };
                        const il = Number(condition.indentLevel);
                        if (Number.isFinite(il) && il > 0) {
                            searchItem.indentLevel = il;
                        }
                        if (resolvedSearchFields && Object.keys(resolvedSearchFields).length > 0) {
                            searchItem.searchFields = resolvedSearchFields;
                        }
                        if (HIERARCHICAL_FACETS.includes(facetId)) {
                            searchItem.hierarchicalOptions = {
                                childInclusion: hierarchicalFilterOptions.childInclusion,
                                applyFilters: hierarchicalFilterOptions.applyFilters
                            };
                        }
                        if (condition.isDisplayFilter) {
                            searchItem.displayFilter = true;
                        }
                        searches.push(searchItem);
                    });
                    return;
                }

                // Keyword for Unison: backend adds a text "query" filter for any non-empty keyword
                // (see UnisonSearchService.buildSearchDefinition). Stale exactId (e.g. role id) with a
                // broad FIND (* or filter-only rows) must not override — send * so isKeywordEmpty() is true
                // and structured filters alone narrow the facet (e.g. Profile Name: Super Admin).
                const qTrim = condition.query != null ? String(condition.query).trim() : '';
                const hasStructuredFilters = condition.filters && typeof condition.filters === 'object'
                    && Object.keys(condition.filters).length > 0;
                // With filters, a numeric-only query is almost always a leaked role/field id, not a text search.
                const numericQueryWithFilters = hasStructuredFilters && /^\d+$/.test(qTrim);
                let keyword;
                if (condition.exactId != null
                    && !(hasStructuredFilters && (qTrim === '*' || qTrim === '' || condition.isFilterCondition
                        || numericQueryWithFilters))) {
                    keyword = String(condition.exactId);
                } else {
                    keyword = qTrim || '';
                }
                // If keyword is all digits with structured filters: drop text constraint unless this is a
                // true entity-id lookup (exactId set and query text is non-numeric, e.g. display name).
                if (hasStructuredFilters && keyword && /^\d+$/.test(String(keyword).trim())) {
                    if (condition.exactId == null) {
                        keyword = '*';
                    } else if (/^\d+$/.test(String(qTrim || '').trim())) {
                        keyword = '*';
                    }
                }

                const searchItem = {
                    operator: (condition.operator || 'FIND').toUpperCase(),
                    facet: facetId,
                    // If condition was created from an exact autocomplete selection, use its numeric ID
                    // so the backend does a direct ID lookup instead of a broad text match.
                    keyword,
                    filters: filters
                };
                if (condition.isDisplayFilter) {
                    searchItem.displayFilter = true;
                }
                const indentLvl = Number(condition.indentLevel);
                if (Number.isFinite(indentLvl) && indentLvl > 0) {
                    searchItem.indentLevel = indentLvl;
                }
                // Pass the user's "Search in" field selection to the backend so it can
                // restrict the keyword LIKE clauses to only the chosen columns.
                if (resolvedSearchFields && Object.keys(resolvedSearchFields).length > 0) {
                    searchItem.searchFields = resolvedSearchFields;
                }
                if (HIERARCHICAL_FACETS.includes(facetId)) {
                    searchItem.hierarchicalOptions = {
                        childInclusion: hierarchicalFilterOptions.childInclusion,
                        applyFilters: hierarchicalFilterOptions.applyFilters
                    };
                }
                searches.push(searchItem);
            });

            // After muting the root FIND, the first remaining row may still be operator OR (e.g. "OR glossary *").
            // Backend establishes root traversal only for FIND on the first clause; OR first skips rootFacetId and
            // can yield the same cross-facet counts as before. Treat a lone leading OR as FIND.
            if (searches.length > 0) {
                const op0 = (searches[0].operator || '').toUpperCase();
                if (op0 === 'OR') {
                    searches[0] = { ...searches[0], operator: 'FIND' };
                }
            }

            // Ensure FIND is first (backend requires root clause first; drag/reorder may have changed order)
            const findIndex = searches.findIndex(s => (s.operator || '').toUpperCase() === 'FIND');
            if (findIndex > 0) {
                const [findItem] = searches.splice(findIndex, 1);
                searches.unshift(findItem);
            }

            // Always use depth 1 to keep only direct neighbors (avoid pulling second-hop systems/people).
            const maxDepth = 1;
            if (typeof window !== 'undefined' && typeof window.unisonSearchDebugLog === 'function') {
                window.unisonSearchDebugLog('ui.executeMultiConditions.unison', {
                    category,
                    userActiveCategory,
                    uniqueFacets: Array.from(uniqueFacets),
                    maxDepth,
                    searchesSummary: searches.map((s) => ({
                        op: s.operator,
                        facet: s.facet,
                        keywordLen: s.keyword != null ? String(s.keyword).length : 0,
                        filterKeys: s.filters && typeof s.filters === 'object' ? Object.keys(s.filters) : [],
                        indentLevel: s.indentLevel
                    }))
                });
            }
            const searchToken = ++currentSearchToken;
            const unisonResult = await unisonSearchFn(searches, { maxDepth: maxDepth });

            if (searchToken !== currentSearchToken) return;

            if (unisonResult.success) {
                const hasUnisonResults = unisonResult.results && Object.keys(unisonResult.results).length > 0;

                // Always keep empty results to preserve zero-state filters across facets
                let resultToApply = unisonResult;

                // If Unison returned empty, synthesize zero-results for all involved facets
                if (!hasUnisonResults) {
                    console.warn('[Unison Search] Empty results returned. Preserving zero-state for all facets involved.');
                    const emptyResults = {};
                    const uniqueFacets = new Set(activeConditions.map(c => {
                        const facetId = typeof categoryToFacetId === 'function' ? categoryToFacetId(c.category) : c.category.toUpperCase();
                        return facetId;
                    }));
                    const facetIdToCatForEmpty = typeof facetIdToCategory === 'function' ? facetIdToCategory :
                        (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null);
                    uniqueFacets.forEach(facetId => {
                        let totalY = 0;
                        if (facetIdToCatForEmpty && typeof window !== 'undefined' && typeof window.canonicalCategoryKey === 'function'
                            && typeof window.resolveZeroFacetTotal === 'function') {
                            const cat = facetIdToCatForEmpty(facetId);
                            if (cat) {
                                totalY = window.resolveZeroFacetTotal(window.canonicalCategoryKey(cat));
                            }
                        }
                        emptyResults[facetId] = {
                            rows: [],
                            ids: [],
                            count: 0,
                            totalCount: Math.max(0, totalY),
                            hasActiveFilter: true
                        };
                    });
                    resultToApply = { ...unisonResult, results: emptyResults };
                }

                // Determine which category to display in the table after the search.
                // If the user was viewing a tab that has results (e.g. People), keep showing that tab.
                // Otherwise fall back to the root FIND category (data-sets).
                let displayCategory = category;
                if (userActiveCategory && userActiveCategory !== category && resultToApply.results) {
                    const catToFacetIdFn2 = typeof categoryToFacetId === 'function' ? categoryToFacetId : null;
                    const userFacetId = catToFacetIdFn2 ? catToFacetIdFn2(userActiveCategory) : userActiveCategory.toUpperCase();
                    const userFacetResult = userFacetId ? (resultToApply.results[userFacetId] || resultToApply.results[userFacetId.replace('DATA_SETS', 'DATASET')]) : null;
                    const userHasData = userFacetResult && (
                        (typeof userFacetResult.count === 'number' && userFacetResult.count > 0) ||
                        (Array.isArray(userFacetResult.rows) && userFacetResult.rows.length > 0) ||
                        (userFacetResult.ids && (Array.isArray(userFacetResult.ids) ? userFacetResult.ids.length > 0 : false))
                    );
                    if (userHasData) {
                        displayCategory = userActiveCategory;
                        if (typeof setActiveCategory === 'function') {
                            setActiveCategory(displayCategory);
                        }
                    }
                }

                // Update all facets with results (even if zero) to propagate 0s and keep filters
                await updateAllFacets(resultToApply, displayCategory);

                if (typeof window !== 'undefined' && typeof window.unisonSearchDebugLog === 'function') {
                    window.unisonSearchDebugLog('ui.afterUpdateAllFacets', {
                        displayCategory,
                        resultFacetKeys: resultToApply.results ? Object.keys(resultToApply.results) : []
                    });
                }

                // Update facet indicators
                updateFacetIndicators(resultToApply);

                // Update all facet indicators and counts from conditions
                // This ensures all facets show updated counts immediately
                if (typeof updateAllFacetIndicatorsFromConditions === 'function') {
                    updateAllFacetIndicatorsFromConditions();
                }

                // Update dashboard with filtered data if dashboard view is active
                if (typeof isDashboardViewActive === 'function' && isDashboardViewActive()) {
                    // Get filtered data for the active category from search results
                    const catToFacetId = typeof categoryToFacetId === 'function' ? categoryToFacetId :
                        (typeof window !== 'undefined' && typeof window.categoryToFacetId === 'function' ? window.categoryToFacetId : null);
                    if (catToFacetId && resultToApply && resultToApply.results) {
                        let facetId = catToFacetId(category);
                        // Normalize facet ID
                        if (facetId === 'DATA-SETS' || facetId === 'DATA_SETS') {
                            facetId = 'DATASET';
                        }
                        const facetResult = resultToApply.results[facetId];
                        if (facetResult && facetResult.rows && Array.isArray(facetResult.rows) && facetResult.rows.length > 0) {
                            // Update dashboard with filtered data
                            if (typeof updateDashboardWithFilteredData === 'function') {
                                await updateDashboardWithFilteredData(category, facetResult.rows);
                            }
                        } else if (facetResult && facetResult.ids && Array.isArray(facetResult.ids) && facetResult.ids.length > 0) {
                            // If we have IDs but no rows, fetch the data
                            if (typeof fetchFacetDataByIds === 'function') {
                                try {
                                    const filteredData = await fetchFacetDataByIds(category, facetResult.ids);
                                    if (filteredData && filteredData.length > 0 && typeof updateDashboardWithFilteredData === 'function') {
                                        await updateDashboardWithFilteredData(category, filteredData);
                                    }
                                } catch (error) {
                                    console.error('[Unison Search] Error fetching data for dashboard:', error);
                                }
                            }
                        }
                    }
                }

                // Update settings button
                if (typeof updateSettingsButtonForCategory === 'function') {
                    updateSettingsButtonForCategory(category).catch(err => { });
                }

                // Reload map if active
                if (typeof reloadMapIfActive === 'function') {
                    reloadMapIfActive();
                }

                // Ensure Clear and search-counter-btn are visible after search
                if (typeof updateSearchCounter === 'function') {
                    updateSearchCounter();
                }
            } else {
                if (typeof window !== 'undefined') {
                    window.__lastUnisonSearchError = {
                        at: new Date().toISOString(),
                        type: 'unisonResultSuccessFalse',
                        error: unisonResult.error,
                        unisonResult: { success: unisonResult.success, error: unisonResult.error }
                    };
                    if (typeof window.unisonSearchErrorLog === 'function') {
                        window.unisonSearchErrorLog('executeMultiConditionSearch.response', window.__lastUnisonSearchError);
                    }
                }
                showErrorMessage(unisonResult.error || 'Search failed');
            }
        } catch (err) {
            let condSummary = null;
            try {
                condSummary = activeConditions.map((c) => ({
                    op: c.operator,
                    cat: c.category,
                    q: c.query
                }));
            } catch (ignore) { /* empty */ }
            if (typeof window !== 'undefined' && typeof window.unisonSearchErrorLog === 'function') {
                window.unisonSearchErrorLog('executeMultiConditionSearch', {
                    message: err && err.message,
                    stack: err && err.stack,
                    activeConditions: condSummary
                });
            }
            console.error('[Unison Search] Error:', err);
            showErrorMessage('Failed to execute Unison search. Please try again.');
            // Fallback to legacy search
            await executeLegacyMultiConditionSearch(category, activeConditions);
        } finally {
            // Allow category-switch handlers to fetch again after search completes
            isSearchExecuting = false;

            // Hide full page loading indicator
            if (typeof hideLoading === 'function') {
                hideLoading('executeMultiConditionSearch');
            }
            const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
            if (dataTable) dataTable.classList.remove('loading');
            // Always refresh counter/clear visibility after search (Enter or Search button)
            if (typeof updateSearchCounter === 'function') {
                updateSearchCounter();
            }
        }
        return;
    }

    // Legacy mode: single-facet search
    await executeLegacyMultiConditionSearch(category, activeConditions);
    if (typeof updateSearchCounter === 'function') {
        updateSearchCounter();
    }
}

/**
 * Legacy multi-condition search (single facet only)
 */
async function executeLegacyMultiConditionSearch(category, activeConditions) {
    // Filter conditions to only include those relevant to the active category
    const relevantConditions = activeConditions.filter(c => {
        if (c.operator === 'FIND') {
            return c.category === category;
        }
        return c.category === category;
    });


    if (relevantConditions.length === 0) {
        currentFilteredData = null;
        currentFilteredCategory = null;
        await loadCategoryData(category);
        return;
    }

    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('executeLegacyMultiConditionSearch');
        }

        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) {
            dataTable.classList.add('loading');
            renderSkeleton(dataTable);
        }

        // Fetch data with relevant conditions for this category
        console.log('[SEARCH] Calling fetchCategoryDataWithConditions:');
        console.log('  Category:', category);
        const data = await fetchCategoryDataWithConditions(category, relevantConditions);

        // Store filtered data for FIND operations
        currentFilteredData = data;
        currentFilteredCategory = category;
        categoryDataCache.set(category, { data: data, category: category });

        if (!validateData(data, category)) {
            showNoDataMessage(category);
            refreshFacetCountForNoData(category);
            return;
        }

        if (data.length === 0) {
            showNoDataMessage(category);
            refreshFacetCountForNoData(category);
            return;
        }

        const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;

        // Generate table directly from server results
        generateTable(data, null, category);
        updateCategoryCount(category, data.length, data.length, false);
        if (typeof cacheModuleData === 'function') {
            cacheModuleData(category, data, signature);
        }

        // Update settings button for current category
        if (typeof updateSettingsButtonForCategory === 'function') {
            updateSettingsButtonForCategory(category).catch(err => { });
        }

        // Reload map if map view is active
        if (typeof reloadMapIfActive === 'function') {
            reloadMapIfActive();
        }
    } catch (err) {
        showErrorMessage('Failed to search. Please try again.');
    } finally {
        // Hide full page loading indicator
        if (typeof hideLoading === 'function') {
            hideLoading('executeLegacyMultiConditionSearch');
        }
        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) dataTable.classList.remove('loading');
    }
}

/**
 * Normalize query string for comparison
 * @param {string} q - Query string
 * @returns {string} Normalized query
 */
function normalizeQuery(q) {
    if (q == null || q === undefined) return '';
    return String(q).trim().replace(/\s+/g, ' ').toLowerCase();
}

/**
 * Check if a condition is duplicate of existing conditions
 * @param {Object} newCondition - New condition to check
 * @param {Array} existingConditions - Array of existing conditions
 * @returns {boolean} True if duplicate
 */
function isDuplicateCondition(newCondition, existingConditions) {
    const newQ = normalizeQuery(newCondition.query);
    const operatorGroup = ['AND', 'OR', 'NOT'];

    return existingConditions.some(existing => {
        if (existing.muted) return false;

        const exQ = normalizeQuery(existing.query);

        // Case A: exact same operator+category+query
        if (existing.operator === newCondition.operator &&
            existing.category === newCondition.category &&
            exQ === newQ) {
            return true;
        }

        // Case B: existing is FIND and new is AND/OR/NOT with same category+query
        if (existing.operator === 'FIND' &&
            operatorGroup.includes(newCondition.operator) &&
            existing.category === newCondition.category &&
            exQ === newQ) {
            return true;
        }

        // Case C: symmetric - existing is AND/OR/NOT and new is FIND with same category+query
        if (newCondition.operator === 'FIND' &&
            operatorGroup.includes(existing.operator) &&
            existing.category === newCondition.category &&
            exQ === newQ) {
            return true;
        }

        return false;
    });
}

/**
 * Reset operator dropdown to FIND
 */
function resetOperatorToFIND() {
    const operatorSelect = document.querySelector('.search-type-select');
    if (operatorSelect) {
        // Use lowercase 'find' to match HTML option value
        operatorSelect.value = 'find';
    }
}

/**
 * Clear search input and return focus
 */
function clearInput() {
    const searchInput = document.querySelector('.search-main-input');
    if (searchInput) {
        searchInput.value = '';
        // Return focus to input
        searchInput.focus();
    }
}

function addSearchCondition(operator, category, query, options = {}) {
    // When not in batch mode, clear bulk-selection flag so counter shows actual condition count
    if (!window._searchConditionBatchAdd) {
        window._searchBulkSelectionActive = false;
    }
    const normalizedOp = String(operator || '').toUpperCase();
    const isAndOrNotOp = (normalizedOp === 'AND' || normalizedOp === 'OR' || normalizedOp === 'NOT');
    if (isAndOrNotOp && (!category || String(category).trim() === '')) {
        if (typeof showToast === 'function') {
            const msg = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t('search.selectCategoryFirst') : 'Select a category first';
            showToast(msg, 'warning');
        }
        renderSearchConditions();
        updateSearchCounter();
        return;
    }
    // Truly empty: null, '', 'undefined', 'null'. '*' is valid (select all / empty-keyword logic).
    const isEmpty = !query || String(query).trim() === '' || query === 'undefined' || query === 'null';
    const allowEmptyForOperator = (normalizedOp === 'AND' || normalizedOp === 'OR' || normalizedOp === 'NOT');
        if (isEmpty && !allowEmptyForOperator) {
        if (typeof showToast === 'function') {
            const msg = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t('search.enterSearchValue') : 'Enter a value to search';
            showToast(msg, 'error');
        }
        renderSearchConditions();
        updateSearchCounter();
        resetOperatorToFIND();
        clearInput();
        return;
    }
    if (isEmpty && allowEmptyForOperator) {
        query = '*';
    }

    // Get fields being searched for this category, respecting the user's "Search in" selection
    const normalizedCategory = category ? category.toString().trim() : null;
    const fields = getCategoryFields(category);
    const activeFields = typeof getActiveSearchFields === 'function' ? getActiveSearchFields() : null;
    const effectiveSearchFieldsSnapshot = (activeFields && Object.keys(activeFields).length > 0)
        ? { ...activeFields }
        : (typeof getEffectiveSearchFieldsForCategory === 'function'
            ? getEffectiveSearchFieldsForCategory(normalizedCategory)
            : null);
    const fieldNames = [];
    // Helper: push a display label only when the field exists in this facet's mapping
    // and the user has not explicitly unchecked it in the "Search in" selector.
    const addField = (key, label) => {
        if (fields[key] && (!activeFields || activeFields[key] !== false)) {
            fieldNames.push(Array.isArray(fields[key]) ? fields[key].join(', ') : label);
        }
    };
    addField('name',        'Name');
    addField('ref',         'Ref.');
    addField('longName',    'Long Name');
    addField('shortName',   'Short Name');
    addField('definition',  'Definition');
    addField('usage',       'Usage');
    addField('businessLogic', 'Business Logic');
    addField('assetId',     'Asset ID');
    addField('aliasName',   'Alias Name');
    addField('description', 'Description');
    addField('firstName',   'First Name');
    addField('lastName',    'Last Name');
    addField('email',       'Email');
    addField('summary',     'Summary');
    addField('title',       'Title');
    addField('parent',      'Parent');
    addField('objectType',  'Object Type');
    addField('object',      'Object');
    addField('owner',       'Owner');

    // Append custom field labels based on the user's "Search in" selection
    const facetIdForCf = typeof categoryToFacetId === 'function' ? categoryToFacetId(category) : null;
    const cfMetaForFacet = (window.customFieldsMetadata && facetIdForCf)
        ? (window.customFieldsMetadata[facetIdForCf] || [])
        : [];
    cfMetaForFacet.forEach(cf => {
        const cfKey = 'cf_' + cf.id;
        // Include only if the user has not explicitly unchecked it
        if (!activeFields || activeFields[cfKey] !== false) {
            fieldNames.push(cf.displayName || cf.technicalName || ('CF ' + cf.id));
        }
    });

    const normalizedOperator = String(operator).toUpperCase();


    // 2. Normalize query
    const normalizedQuery = normalizeQuery(query);

    // 3. Auto-FIND: If no conditions exist and operator is not FIND, add FIND with broad root (*) then add AND/OR/NOT
    if (searchConditions.length === 0 && normalizedOperator !== 'FIND') {
        const autoFind = {
            id: Date.now() + Math.random() - 1,
            operator: 'FIND',
            category: normalizedCategory,
            query: '*', // Broad root so user's AND/OR/NOT clause is not duplicate (Option B)
            fields: fieldNames,
            muted: false,
            indentLevel: 0
        };

        if (!isDuplicateCondition(autoFind, searchConditions)) {
            searchConditions.unshift(autoFind);
        }
        // No duplicate check for the new AND/OR/NOT here: we used FIND * so the user's clause is always added below
    }

    // 4. Handle FIND replacement: If operator is FIND and FIND exists, replace it
    if (normalizedOperator === 'FIND') {
        const existingFindIndex = searchConditions.findIndex(c =>
            c.operator === 'FIND' && c.category === normalizedCategory && !c.muted
        );

        if (existingFindIndex !== -1) {
            // Same facet: Replace existing FIND in that facet only
            const oldFind = searchConditions[existingFindIndex];
            searchConditions.splice(existingFindIndex, 1);

            // Re-validate all other conditions against the new FIND
            // Remove conditions that would be duplicate with the new FIND
            const newFindCondition = {
                operator: 'FIND',
                category: normalizedCategory,
                query: query
            };

            const conditionsToRemove = [];
            searchConditions.forEach((cond, index) => {
                if (cond.category === normalizedCategory && !cond.muted) {
                    if (isDuplicateCondition(cond, [newFindCondition])) {
                        conditionsToRemove.push(index);
                    }
                }
            });

            // Remove duplicates in reverse order to maintain indices
            conditionsToRemove.reverse().forEach(index => {
                searchConditions.splice(index, 1);
            });
        } else {
            // Different facet: Clear ALL previous conditions (FIND acts like START)
            const hasOtherConditions = searchConditions.some(c => !c.muted);
            if (hasOtherConditions) {
                searchConditions.length = 0; // Clear all previous conditions
            }
        }
    }

    // 5. Create new condition object
    const condition = {
        id: Date.now() + Math.random(),
        operator: normalizedOperator,
        category: normalizedCategory,
        query: query, // Keep original query for display
        fields: fieldNames,
        searchFields: effectiveSearchFieldsSnapshot,
        muted: false,
        indentLevel: 0
    };
    // When user selected a specific item from autocomplete, store its numeric ID so
    // re-searches use the exact ID instead of the display name (which may match multiple records).
    if (options.exactId != null) {
        condition.exactId = options.exactId;
    }

    // 6. Duplicate check using enhanced isDuplicateCondition
    if (isDuplicateCondition(condition, searchConditions)) {
        console.warn('[SEARCH] Skipping duplicate condition:', {
            operator: condition.operator,
            category: condition.category,
            query: condition.query
        });
        if (window._searchConditionBatchAdd) {
            window._searchConditionBatchSkipCount = (window._searchConditionBatchSkipCount || 0) + 1;
        } else if (typeof showToast === 'function') {
            const msg = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t('search.duplicateConditionIgnored') : 'Duplicate condition ignored';
            showToast(msg, 'info');
        }
        renderSearchConditions();
        updateSearchCounter();
        resetOperatorToFIND();
        clearInput();
        return;
    }

    // 7. Add new condition
    // If it's FIND, ensure it's at index 0 (first condition)
    if (normalizedOperator === 'FIND') {
        searchConditions.unshift(condition);
    } else {
        searchConditions.push(condition);
    }

    // 8. Clear filtered data cache when adding new condition
    const currentCategory = getActiveCategoryWithFallback();
    if (currentCategory) {
        categoryDataCache.delete(currentCategory);
    }
    currentFilteredData = null;
    currentFilteredCategory = null;
    currentUnisonSearchResults = null; // Clear Unison Search results

    // 9. Update UI (always, even if condition was skipped)
    renderSearchConditions();
    updateSearchCounter();

    // Enable AND/OR/NOT options if we have at least one condition
    if (searchConditions.length > 0) {
        enableOperatorOptions();
    }

    // 10. Operator dropdown + clear input
    // - After AND/OR/NOT: keep the selected operator so the user can chain another term on the same facet (e.g. OR then OR).
    // - After FIND: default back to FIND; if multiple conditions remain on this facet (e.g. FIND replaced root but OR rows stayed), pre-select OR as a hint for the next term.
    if (normalizedOperator === 'FIND') {
        const sameFacetCount = searchConditions.filter(
            c => c.category === normalizedCategory && !c.muted
        ).length;
        if (sameFacetCount > 1) {
            const operatorSelect = document.querySelector('.search-type-select');
            if (operatorSelect) {
                operatorSelect.value = 'or';
            }
        } else {
            resetOperatorToFIND();
        }
    }
    clearInput();
    isSearchCommitted = false; // wait for explicit search
}

function removeSearchCondition(id) {
    window._searchBulkSelectionActive = false;
    searchConditions = searchConditions.filter(c => c.id !== id);
    renderSearchConditions();
    updateSearchCounter();

    // Disable operators if no conditions remain
    if (searchConditions.length === 0) {
        disableOperatorOptions();
        isSearchCommitted = false;
    }

    // Clear filtered data cache
    currentFilteredData = null;
    currentFilteredCategory = null;
    currentUnisonSearchResults = null; // Clear Unison Search results

    // Re-execute search if conditions remain
    if (searchConditions.length > 0) {
        executeMultiConditionSearch();
    } else {
        const category = getActiveCategoryWithFallback();
        loadCategoryData(category);
    }
}

function clearAllSearchConditions() {
    const preservedCategory = getActiveCategoryWithFallback();
    window._searchBulkSelectionActive = false;
    // Clear search conditions
    searchConditions = [];
    currentFilteredData = null;
    currentFilteredCategory = null;
    currentUnisonSearchResults = null; // Clear Unison Search results
    isSearchCommitted = false;

    // Clear all facet indicators (highlighting and filter badges)
    clearAllFacetIndicators();

    if (typeof window !== 'undefined') {
        if (window.categoryCounts) window.categoryCounts.clear();
        if (window.setFacetCountSource) window.setFacetCountSource('baseline');
        if (typeof window.preloadModuleRowCounts === 'function') {
            window.preloadModuleRowCounts().catch(() => { });
        }
    }

    // Clear search input field
    const searchInput = document.querySelector('.search-main-input');
    if (searchInput) {
        searchInput.value = '';
    }

    // Reset operator select to FIND
    const operatorSelect = document.querySelector('.search-type-select');
    if (operatorSelect) {
        operatorSelect.value = 'find';
    }

    // Disable AND/OR/NOT options when all conditions are cleared
    disableOperatorOptions();

    // Render conditions (will hide query builder container)
    renderSearchConditions();

    // Update counter (will hide clear button)
    updateSearchCounter();

    // Reload table in the same facet the user was already on (do not reset facet selection)
    const category = preservedCategory || getActiveCategoryWithFallback();
    if (typeof setActiveCategory === 'function' && category) {
        setActiveCategory(category);
    }
    loadCategoryData(category);
}

function normalizeSearchConditionIndents() {
    searchConditions.forEach((c, idx) => {
        if (c.operator === 'FIND') {
            c.indentLevel = 0;
            return;
        }
        let maxAllowed = 0;
        if (idx > 0) {
            const prev = searchConditions[idx - 1];
            maxAllowed = Math.max(0, (prev.indentLevel || 0) + 1);
        }
        const cur = c.indentLevel || 0;
        if (cur > maxAllowed) {
            c.indentLevel = maxAllowed;
        }
    });
}

function adjustConditionIndent(id, delta) {
    const idx = searchConditions.findIndex(c => c.id === id);
    if (idx < 0) return;
    const cur = searchConditions[idx];
    if (cur.operator === 'FIND') return;
    const prev = idx > 0 ? searchConditions[idx - 1] : null;
    if (delta > 0 && !prev) return;
    const prevIndent = prev ? (prev.indentLevel || 0) : 0;
    let level = cur.indentLevel || 0;
    if (delta > 0) {
        level = Math.min(level + 1, prevIndent + 1);
    } else {
        level = Math.max(0, level - 1);
    }
    cur.indentLevel = level;
    normalizeSearchConditionIndents();
    renderSearchConditions();
    executeMultiConditionSearch();
}

function updateSearchConditionOperator(id, operator) {
    const condition = searchConditions.find(c => c.id === id);
    if (condition) {
        condition.operator = operator.toUpperCase();
        // Update the button text without re-rendering everything
        const conditionItem = document.querySelector(`[data-condition-id="${id}"]`);
        if (conditionItem) {
            const operatorBtn = conditionItem.querySelector('.search-condition-operator');
            if (operatorBtn) {
                operatorBtn.textContent = condition.operator;
                operatorBtn.setAttribute('data-operator', condition.operator.toLowerCase());
            }
        }

        // Re-execute search
        executeMultiConditionSearch();
    }
}

function dismissOpenConditionInlineEditor() {
    if (typeof activeConditionInlineFinish === 'function') {
        const fn = activeConditionInlineFinish;
        activeConditionInlineFinish = null;
        fn(false);
    }
}

function findSearchConditionById(conditionRef) {
    if (!conditionRef || conditionRef.id == null) return null;
    const id = String(conditionRef.id);
    return searchConditions.find((x) => x != null && String(x.id) === id) || null;
}

/** Re-run Unison / table search after the user commits an inline condition edit. */
function runSearchAfterInlineConditionCommit() {
    if (typeof executeMultiConditionSearch !== 'function') return;
    void executeMultiConditionSearch().catch((err) => {
        console.error('[search-input] executeMultiConditionSearch after inline edit', err);
    });
}

function syncMainSearchInputIfEditedFindRow(editedCondition) {
    if (!editedCondition || String(editedCondition.operator || '').toUpperCase() !== 'FIND') return;
    const findRow = searchConditions.find((c) => c.operator === 'FIND' && !c.muted);
    if (!findRow || String(findRow.id) !== String(editedCondition.id)) return;
    const input = document.querySelector('.search-main-input');
    if (!input) return;
    const q = findRow.query;
    input.value = (q === '*' || q == null || q === '') ? '' : String(q);
}

function getConditionQueryEditableText(condition) {
    if (!condition) return '';
    if (Array.isArray(condition.bulkTerms) && condition.bulkTerms.length > 0) {
        return condition.bulkTerms.join('\n');
    }
    if (typeof condition.displayQuery === 'string' && condition.displayQuery.trim() !== '') {
        return condition.displayQuery;
    }
    return String(condition.query != null ? condition.query : '');
}

function startQueryConditionInlineEdit(condition, editableSpan) {
    dismissOpenConditionInlineEditor();
    const parent = editableSpan.parentNode;
    if (!parent) return;

    const isBulk = Array.isArray(condition.bulkTerms) && condition.bulkTerms.length > 0;
    const initial = getConditionQueryEditableText(condition);

    editableSpan.style.display = 'none';
    const el = isBulk ? document.createElement('textarea') : document.createElement('input');
    el.className = 'search-condition-inline-input';
    if (!isBulk) el.type = 'text';
    el.value = initial;
    el.setAttribute('aria-label', 'Edit search text');
    parent.insertBefore(el, editableSpan.nextSibling);

    let done = false;
    const finish = (commit) => {
        if (done) return;
        done = true;
        if (activeConditionInlineFinish === finish) {
            activeConditionInlineFinish = null;
        }
        const rawValue = el.value;
        el.remove();
        editableSpan.style.display = '';
        if (!commit) return;

        const c = findSearchConditionById(condition);
        if (!c) return;

        if (isBulk) {
            const terms = rawValue.split(/[\n,]+/).map(s => s.trim()).filter(Boolean);
            c.bulkTerms = terms;
            c.displayQuery = terms.join(', ');
            c.query = terms[0] || '*';
        } else {
            const v = rawValue.trim();
            c.query = v || '*';
            c.displayQuery = v;
        }
        if (typeof window !== 'undefined') {
            window.searchConditions = searchConditions;
        }
        syncMainSearchInputIfEditedFindRow(c);
        renderSearchConditions();
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
        runSearchAfterInlineConditionCommit();
    };

    activeConditionInlineFinish = finish;

    el.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter' && !isBulk && !ev.shiftKey) {
            ev.preventDefault();
            finish(true);
        }
        if (ev.key === 'Escape') {
            ev.preventDefault();
            finish(false);
        }
    });
    el.addEventListener('blur', () => {
        window.setTimeout(() => finish(true), 0);
    });

    el.focus();
    if (el.select) el.select();
}

function startCategoryConditionInlineEdit(condition, categorySpan) {
    dismissOpenConditionInlineEditor();
    const parent = categorySpan.parentNode;
    if (!parent) return;

    const slugs = (typeof window !== 'undefined' && window.FACET_TO_CATEGORY)
        ? [...new Set(Object.values(window.FACET_TO_CATEGORY))].sort((a, b) => a.localeCompare(b))
        : [];
    if (slugs.length === 0) return;

    const current = typeof window.canonicalCategoryKey === 'function'
        ? window.canonicalCategoryKey(condition.category)
        : String(condition.category || '').toLowerCase().replace(/\s+/g, '-');

    categorySpan.style.display = 'none';
    const select = document.createElement('select');
    select.className = 'search-condition-inline-select';
    select.setAttribute('aria-label', 'Change category');

    slugs.forEach((slug) => {
        const opt = document.createElement('option');
        opt.value = slug;
        const labelFn = typeof getCategoryDisplayName === 'function' ? getCategoryDisplayName : null;
        opt.textContent = labelFn ? labelFn(slug) : slug;
        if (slug === current) opt.selected = true;
        select.appendChild(opt);
    });

    parent.insertBefore(select, categorySpan.nextSibling);

    let done = false;
    const finish = (commit) => {
        if (done) return;
        done = true;
        if (activeConditionInlineFinish === finish) {
            activeConditionInlineFinish = null;
        }
        const chosenSlug = select.value;
        select.remove();
        categorySpan.style.display = '';
        if (!commit) return;

        const c = findSearchConditionById(condition);
        if (!c) return;
        const newSlug = typeof window.canonicalCategoryKey === 'function'
            ? window.canonicalCategoryKey(chosenSlug)
            : chosenSlug;
        c.category = newSlug;
        if (typeof window !== 'undefined') {
            window.searchConditions = searchConditions;
        }
        renderSearchConditions();
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
        if (typeof setActiveCategory === 'function') {
            setActiveCategory(newSlug);
        }
        runSearchAfterInlineConditionCommit();
    };

    activeConditionInlineFinish = finish;

    select.addEventListener('keydown', (ev) => {
        if (ev.key === 'Escape') {
            ev.preventDefault();
            finish(false);
        }
    });
    select.addEventListener('blur', () => {
        window.setTimeout(() => finish(true), 0);
    });

    select.focus();
}

function startFieldsConditionInlineEdit(condition, fieldsSpan) {
    dismissOpenConditionInlineEditor();
    const parent = fieldsSpan.parentNode;
    if (!parent) return;

    const initial = Array.isArray(condition.fields) ? condition.fields.join(', ') : '';

    fieldsSpan.style.display = 'none';
    const el = document.createElement('input');
    el.type = 'text';
    el.className = 'search-condition-inline-input';
    el.value = initial;
    el.setAttribute('aria-label', 'Edit search fields');
    parent.insertBefore(el, fieldsSpan.nextSibling);

    let done = false;
    const finish = (commit) => {
        if (done) return;
        done = true;
        if (activeConditionInlineFinish === finish) {
            activeConditionInlineFinish = null;
        }
        const rawFields = el.value;
        el.remove();
        fieldsSpan.style.display = '';
        if (!commit) return;

        const c = findSearchConditionById(condition);
        if (!c) return;
        const parts = rawFields.split(',').map(s => s.trim()).filter(Boolean);
        c.fields = parts;
        if (typeof window !== 'undefined') {
            window.searchConditions = searchConditions;
        }
        renderSearchConditions();
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
        runSearchAfterInlineConditionCommit();
    };

    activeConditionInlineFinish = finish;

    el.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter') {
            ev.preventDefault();
            finish(true);
        }
        if (ev.key === 'Escape') {
            ev.preventDefault();
            finish(false);
        }
    });
    el.addEventListener('blur', () => {
        window.setTimeout(() => finish(true), 0);
    });

    el.focus();
    el.select();
}

function renderSearchConditions() {
    const container = document.getElementById('queryBuilderContent');
    const builderContainer = document.getElementById('queryBuilderContainer');
    const allowOperatorChange = false; // lock operator UI

    if (!container || !builderContainer) return;

    // Show all conditions including FIND
    const conditionsToShow = searchConditions;

    if (conditionsToShow.length === 0) {
        builderContainer.style.display = 'none';
        updateSearchCounter();
        return;
    }

    normalizeSearchConditionIndents();

    // Don't auto-show, only show when counter is clicked
    // builderContainer.style.display = 'block';
    container.innerHTML = '';

    conditionsToShow.forEach((condition, index) => {
        const conditionItem = document.createElement('div');
        conditionItem.className = `search-condition-item${condition.muted ? ' muted' : ''}`;
        conditionItem.setAttribute('data-condition-id', condition.id);
        if (condition.operator === 'FIND') {
            condition.indentLevel = 0;
        }
        const indentLevel = Math.max(0, condition.indentLevel || 0);
        conditionItem.style.paddingLeft = `${0.65 + indentLevel * 1.15}rem`;

        const indentWrap = document.createElement('div');
        indentWrap.className = 'search-condition-indent-wrap';
        indentWrap.style.display = 'flex';
        indentWrap.style.flexDirection = 'column';
        indentWrap.style.gap = '0.2rem';
        indentWrap.style.flexShrink = '0';

        const indentBtn = document.createElement('button');
        indentBtn.type = 'button';
        indentBtn.className = 'search-condition-action-btn search-condition-indent-btn';
        indentBtn.innerHTML = '<i class="fas fa-arrow-left"></i>';
        indentBtn.title = 'Indent — group with previous line (AND/OR/NOT combine inside the group, then filter the line above)';
        indentBtn.disabled = condition.operator === 'FIND' || index === 0
            || indentLevel >= ((index > 0 ? (conditionsToShow[index - 1].indentLevel || 0) : 0) + 1);
        indentBtn.addEventListener('click', (ev) => {
            ev.stopPropagation();
            adjustConditionIndent(condition.id, 1);
        });

        const outdentBtn = document.createElement('button');
        outdentBtn.type = 'button';
        outdentBtn.className = 'search-condition-action-btn search-condition-indent-btn';
        outdentBtn.innerHTML = '<i class="fas fa-arrow-right"></i>';
        outdentBtn.title = 'Outdent';
        outdentBtn.disabled = condition.operator === 'FIND' || indentLevel <= 0;
        outdentBtn.addEventListener('click', (ev) => {
            ev.stopPropagation();
            adjustConditionIndent(condition.id, -1);
        });

        indentWrap.appendChild(indentBtn);
        indentWrap.appendChild(outdentBtn);

        // Operator display (button with dropdown)
        const operatorBtn = document.createElement('button');
        operatorBtn.className = 'search-condition-operator';
        operatorBtn.setAttribute('data-operator', condition.operator.toLowerCase());
        operatorBtn.type = 'button';
        operatorBtn.textContent = condition.operator;
        operatorBtn.disabled = true;
        operatorBtn.classList.add('operator-locked');

        // Position dropdown relative to button
        const operatorWrapper = document.createElement('div');
        operatorWrapper.style.position = 'relative';
        operatorWrapper.appendChild(operatorBtn);

        if (allowOperatorChange) {
            // Create dropdown menu for operator selection
            const operatorDropdown = document.createElement('div');
            operatorDropdown.className = 'search-condition-operator-dropdown';
            operatorDropdown.style.display = 'none';
            operatorDropdown.style.position = 'absolute';
            operatorDropdown.style.top = '100%';
            operatorDropdown.style.left = '0';
            operatorDropdown.style.zIndex = '1002';
            operatorDropdown.style.background = 'var(--background-primary)';
            operatorDropdown.style.border = '1px solid var(--border-color)';
            operatorDropdown.style.borderRadius = '4px';
            operatorDropdown.style.boxShadow = '0 4px 12px rgba(0,0,0,0.15)';
            operatorDropdown.style.minWidth = '80px';

            const operators = [
                { value: 'find', label: 'FIND', color: '#6c757d' },
                { value: 'and', label: 'AND', color: '#0d6efd' },
                { value: 'or', label: 'OR', color: '#198754' },
                { value: 'not', label: 'NOT', color: '#dc3545' }
            ];

            operators.forEach(op => {
                const option = document.createElement('button');
                option.type = 'button';
                option.className = 'search-condition-operator-option';
                option.textContent = op.label;
                option.style.width = '100%';
                option.style.padding = '0.5rem';
                option.style.textAlign = 'left';
                option.style.background = op.color;
                option.style.color = 'white';
                option.style.border = 'none';
                option.style.cursor = 'pointer';
                option.style.fontWeight = '600';
                option.style.fontSize = '0.85rem';

                option.addEventListener('mouseenter', () => {
                    option.style.opacity = '0.9';
                });
                option.addEventListener('mouseleave', () => {
                    option.style.opacity = '1';
                });

                option.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (index === 0 && op.value !== 'find') {
                        return; // First condition must be FIND
                    }
                    updateSearchConditionOperator(condition.id, op.value);
                    operatorDropdown.style.display = 'none';
                });

                operatorDropdown.appendChild(option);
            });

            // Toggle dropdown on click (disabled when locked)
            operatorBtn.addEventListener('click', (e) => {
                if (operatorBtn.disabled) return;
                e.stopPropagation();
                if (index === 0) return; // First condition can't be changed

                const isVisible = operatorDropdown.style.display !== 'none';
                // Close all other dropdowns
                document.querySelectorAll('.search-condition-operator-dropdown').forEach(dd => {
                    dd.style.display = 'none';
                });
                operatorDropdown.style.display = isVisible ? 'none' : 'block';
            });

            // Close dropdown when clicking outside
            document.addEventListener('click', (e) => {
                if (!operatorBtn.contains(e.target) && !operatorDropdown.contains(e.target)) {
                    operatorDropdown.style.display = 'none';
                }
            });

            operatorWrapper.appendChild(operatorDropdown);
        }

        // Condition content
        const contentDiv = document.createElement('div');
        contentDiv.className = 'search-condition-content';

        const queryRow = document.createElement('span');
        queryRow.className = 'search-condition-text';
        const hasDisplayQuery = typeof condition.displayQuery === 'string' && condition.displayQuery.trim() !== '';
        const isBulkRow = Array.isArray(condition.bulkTerms) && condition.bulkTerms.length > 0;
        const editableQueryText = isBulkRow
            ? (condition.displayQuery || condition.bulkTerms.join(', '))
            : (hasDisplayQuery ? condition.displayQuery : String(condition.query != null ? condition.query : ''));
        const fullQueryText = isBulkRow
            ? `${editableQueryText} in`
            : (hasDisplayQuery
                ? `${condition.displayQuery} in`
                : `"${condition.query}" in`);

        const queryEditable = document.createElement('span');
        queryEditable.className = 'search-condition-query-editable';
        queryEditable.textContent = editableQueryText;
        queryEditable.title = condition.isFilterCondition
            ? 'Double-click to open Filter panel (structured filters)'
            : 'Double-click to edit search text';

        const querySuffix = document.createElement('span');
        querySuffix.className = 'search-condition-query-suffix';
        querySuffix.textContent = ' in';

        queryRow.appendChild(queryEditable);
        queryRow.appendChild(querySuffix);

        const shouldTruncate = fullQueryText.length > 80;
        if (shouldTruncate) {
            queryRow.classList.add('is-truncated');
        }

        queryEditable.addEventListener('dblclick', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (condition.isFilterCondition) {
                if (typeof setActiveCategory === 'function') {
                    setActiveCategory(condition.category);
                }
                if (typeof openFilterPanel === 'function') {
                    openFilterPanel();
                }
                return;
            }
            startQueryConditionInlineEdit(condition, queryEditable);
        });

        const categorySpan = document.createElement('span');
        categorySpan.className = 'search-condition-category';
        categorySpan.title = 'Double-click to change category';

        // Get category icon and name
        // Use getCategoryDisplayName to convert any category format to proper display name
        const categoryName = typeof getCategoryDisplayName === 'function'
            ? getCategoryDisplayName(condition.category)
            : (condition.category || 'Unknown');
        const categoryIcon = getCategoryIcon(categoryName);

        const icon = document.createElement('i');
        icon.className = categoryIcon;
        categorySpan.appendChild(icon);
        categorySpan.appendChild(document.createTextNode(categoryName));

        categorySpan.addEventListener('dblclick', (e) => {
            e.preventDefault();
            e.stopPropagation();
            startCategoryConditionInlineEdit(condition, categorySpan);
        });

        const fieldsSpan = document.createElement('span');
        fieldsSpan.className = 'search-condition-fields';
        const fieldList = Array.isArray(condition.fields) ? condition.fields : [];
        fieldsSpan.textContent = fieldList.length > 0
            ? `(${fieldList.join(', ')})`
            : '';
        fieldsSpan.title = fieldList.length > 0
            ? 'Double-click to edit field list'
            : 'Double-click to add field list (comma-separated)';

        fieldsSpan.addEventListener('dblclick', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (!Array.isArray(condition.fields)) {
                condition.fields = [];
            }
            startFieldsConditionInlineEdit(condition, fieldsSpan);
        });

        contentDiv.appendChild(queryRow);
        if (shouldTruncate) {
            const expandBtn = document.createElement('button');
            expandBtn.type = 'button';
            expandBtn.className = 'search-condition-expand-btn';
            expandBtn.textContent = 'Show more';
            expandBtn.setAttribute('aria-expanded', 'false');
            expandBtn.addEventListener('click', () => {
                const expanded = queryRow.classList.toggle('expanded');
                expandBtn.textContent = expanded ? 'Show less' : 'Show more';
                expandBtn.setAttribute('aria-expanded', expanded ? 'true' : 'false');
            });
            contentDiv.appendChild(expandBtn);
        }
        contentDiv.appendChild(categorySpan);
        contentDiv.appendChild(fieldsSpan);

        // Actions - 4 buttons as shown in the image
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'search-condition-actions';
        actionsDiv.style.display = 'flex';
        actionsDiv.style.gap = '0.5rem';
        actionsDiv.style.alignItems = 'center';

        // 1. Clear this search button
        const clearBtn = document.createElement('button');
        clearBtn.className = 'search-condition-action-btn';
        clearBtn.innerHTML = '<i class="fas fa-list"></i>';
        clearBtn.title = 'Clear this search';
        clearBtn.addEventListener('click', () => removeSearchCondition(condition.id));

        // 2. Run only this search button
        const runOnlyBtn = document.createElement('button');
        runOnlyBtn.className = 'search-condition-action-btn';
        runOnlyBtn.innerHTML = '<i class="fas fa-play"></i>';
        runOnlyBtn.title = 'Run only this search';
        runOnlyBtn.addEventListener('click', () => runOnlyThisSearch(condition.id));

        // 3. Mute this search button
        const muteBtn = document.createElement('button');
        muteBtn.className = 'search-condition-action-btn';
        muteBtn.innerHTML = `<i class="fas fa-${condition.muted ? 'microphone-slash' : 'microphone'}"></i>`;
        muteBtn.title = condition.muted ? 'Unmute this search' : 'Mute this search';
        muteBtn.setAttribute('data-muted', condition.muted ? 'true' : 'false');
        if (condition.muted) muteBtn.style.opacity = '0.5';
        muteBtn.addEventListener('click', () => toggleMuteSearch(condition.id, muteBtn));

        // 4. Reorder this search button
        const reorderBtn = document.createElement('button');
        reorderBtn.type = 'button';
        reorderBtn.className = 'search-condition-action-btn';
        reorderBtn.setAttribute('data-reorder', 'true');
        reorderBtn.innerHTML = '<i class="fas fa-arrows-alt"></i>';
        reorderBtn.title = 'Reorder this search';
        reorderBtn.setAttribute('data-condition-id', condition.id);
        reorderBtn.setAttribute('data-index', index);
        reorderBtn.addEventListener('mousedown', (e) => {
            e.stopPropagation();
            startReorder(e, condition.id, index);
        });

        actionsDiv.appendChild(clearBtn);
        actionsDiv.appendChild(runOnlyBtn);
        actionsDiv.appendChild(muteBtn);
        actionsDiv.appendChild(reorderBtn);

        conditionItem.appendChild(indentWrap);
        conditionItem.appendChild(operatorWrapper);
        conditionItem.appendChild(contentDiv);
        conditionItem.appendChild(actionsDiv);

        container.appendChild(conditionItem);
    });

    // Add action buttons at the bottom (Refresh and Save search)
    const actionsBar = document.createElement('div');
    actionsBar.className = 'query-builder-actions-bar';
    actionsBar.style.display = 'flex';
    actionsBar.style.justifyContent = 'space-between';
    actionsBar.style.marginTop = '1rem';
    actionsBar.style.paddingTop = '1rem';
    actionsBar.style.borderTop = '1px solid var(--border-color)';

    // Refresh button on the left
    const refreshBtn = document.createElement('button');
    refreshBtn.className = 'query-builder-action-btn refresh-btn';
    refreshBtn.innerHTML = '<i class="fas fa-sync-alt"></i> Refresh';
    refreshBtn.addEventListener('click', () => {
        if (searchConditions.length > 0) {
            executeMultiConditionSearch();
        }
    });

    // Save search button on the right (hidden for web users by applySaveSearchRestrictions)
    const saveBtn = document.createElement('button');
    saveBtn.className = 'query-builder-action-btn save-btn';
    saveBtn.id = 'save-search-btn';
    saveBtn.innerHTML = '<i class="fas fa-save"></i> Save search';
    saveBtn.addEventListener('click', () => saveSearchConditions());

    actionsBar.appendChild(refreshBtn);
    actionsBar.appendChild(saveBtn);
    container.appendChild(actionsBar);

    if (typeof applySaveSearchRestrictions === 'function') {
        applySaveSearchRestrictions();
    }
}

async function runOnlyThisSearch(conditionId) {
    const condition = searchConditions.find(c => c.id === conditionId);
    if (!condition) return;

    // Save current search conditions to restore later
    const originalConditions = [...searchConditions];
    const originalWindowConditions = window.searchConditions ? [...window.searchConditions] : null;

    // Create a copy of the condition and ensure it's 'FIND' operator
    // (since it's now the primary/only condition)
    const singleCondition = {
        ...condition,
        operator: 'FIND' // First/only condition must be FIND
    };

    // Replace searchConditions with just this single condition
    searchConditions = [singleCondition];
    if (typeof window !== 'undefined') {
        window.searchConditions = searchConditions;
    }


    try {
        // Execute multi-condition search which will use Unison Search if appropriate
        // This will show results for the whole facet(s) matching this condition
        await executeMultiConditionSearch();
    } finally {
        // Restore original search conditions after search completes
        // This allows the user to see results for just this condition
        // while keeping other conditions visible in the UI
        searchConditions = originalConditions;
        if (typeof window !== 'undefined') {
            window.searchConditions = originalWindowConditions || originalConditions;
        }

        // Re-render the conditions UI to show all conditions again
        renderSearchConditions();
    }
}

function toggleMuteSearch(conditionId, muteBtn) {
    const condition = searchConditions.find(c => c.id === conditionId);
    if (!condition) return;

    const isMuted = muteBtn.getAttribute('data-muted') === 'true';
    condition.muted = !isMuted;
    muteBtn.setAttribute('data-muted', condition.muted ? 'true' : 'false');

    if (condition.muted) {
        muteBtn.style.opacity = '0.5';
        muteBtn.title = 'Unmute this search';
    } else {
        muteBtn.style.opacity = '1';
        muteBtn.title = 'Mute this search';
    }

    // Re-execute search with active (non-muted) conditions
    executeMultiConditionSearch();
}

let draggedConditionId = null;
let draggedOverConditionId = null;

function startReorder(e, conditionId, index) {
    e.preventDefault();
    e.stopPropagation();
    draggedConditionId = conditionId;

    const conditionItem = document.querySelector(`[data-condition-id="${conditionId}"]`);
    const reorderBtn = e.target.closest('[data-reorder="true"]');

    if (conditionItem) {
        conditionItem.style.opacity = '0.5';
        conditionItem.style.cursor = 'grabbing';
        conditionItem.style.userSelect = 'none';
    }

    if (reorderBtn) {
        reorderBtn.style.cursor = 'grabbing';
    }

    // Prevent text selection during drag
    document.body.style.userSelect = 'none';

    document.addEventListener('mousemove', handleReorderMove);
    document.addEventListener('mouseup', handleReorderEnd);
}

function handleReorderMove(e) {
    const conditionItem = document.querySelector(`[data-condition-id="${draggedConditionId}"]`);
    if (!conditionItem) return;

    const allConditions = document.querySelectorAll('.search-condition-item');
    allConditions.forEach(item => {
        const rect = item.getBoundingClientRect();
        if (e.clientY >= rect.top && e.clientY <= rect.bottom &&
            item.getAttribute('data-condition-id') !== draggedConditionId) {
            item.style.borderTop = '2px solid #248567';
            draggedOverConditionId = item.getAttribute('data-condition-id');
        } else {
            item.style.borderTop = '';
        }
    });
}

function handleReorderEnd(e) {
    // Restore text selection
    document.body.style.userSelect = '';

    if (draggedConditionId && draggedOverConditionId && draggedConditionId !== draggedOverConditionId) {
        const draggedIndex = searchConditions.findIndex(c => c.id === draggedConditionId);
        const targetIndex = searchConditions.findIndex(c => c.id === draggedOverConditionId);

        if (draggedIndex !== -1 && targetIndex !== -1) {
            // Remove the dragged condition first
            const [movedCondition] = searchConditions.splice(draggedIndex, 1);

            // Calculate correct insertion index after removal
            // The border indicator shows insertion BEFORE the target item
            let insertIndex;
            if (draggedIndex < targetIndex) {
                // Dragging backward: target index shifts down by 1 after removal
                insertIndex = targetIndex - 1;
            } else {
                // Dragging forward: target index remains the same after removal
                insertIndex = targetIndex;
            }

            // Insert at the calculated position
            searchConditions.splice(insertIndex, 0, movedCondition);

            // First condition must be FIND
            if (searchConditions[0].operator !== 'FIND') {
                searchConditions[0].operator = 'FIND';
            }
            normalizeSearchConditionIndents();

            // Re-render
            renderSearchConditions();
            executeMultiConditionSearch();
        }
    }

    // Reset
    document.querySelectorAll('.search-condition-item').forEach(item => {
        item.style.opacity = '1';
        item.style.cursor = '';
        item.style.borderTop = '';
        item.style.userSelect = '';
    });

    document.querySelectorAll('[data-reorder="true"]').forEach(btn => {
        btn.style.cursor = '';
    });

    document.removeEventListener('mousemove', handleReorderMove);
    document.removeEventListener('mouseup', handleReorderEnd);
    draggedConditionId = null;
    draggedOverConditionId = null;
}

async function executeSearchWithConditions(category, conditions) {
    console.log('[SEARCH] executeSearchWithConditions called');
    console.log('[SEARCH] Category:', category);
    const activeConditions = conditions.filter(c => !c.muted);

    if (activeConditions.length === 0) {
        await loadCategoryData(category);
        return;
    }

    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('executeSearchWithConditions');
        }

        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) {
            dataTable.classList.add('loading');
            renderSkeleton(dataTable);
        }

        const data = await fetchCategoryDataWithConditions(category, activeConditions);

        if (!validateData(data, category)) {
            showNoDataMessage(category);
            refreshFacetCountForNoData(category);
            return;
        }

        if (data.length === 0) {
            showNoDataMessage(category);
            refreshFacetCountForNoData(category);
            return;
        }

        const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;
        generateTable(data, null, category);
        updateCategoryCount(category, data.length, data.length, false);
        if (typeof cacheModuleData === 'function') {
            cacheModuleData(category, data, signature);
        }

        if (typeof updateSettingsButtonForCategory === 'function') {
            updateSettingsButtonForCategory(category).catch(err => { });
        }

        if (typeof reloadMapIfActive === 'function') {
            reloadMapIfActive();
        }
    } catch (err) {
        showErrorMessage('Failed to search. Please try again.');
    } finally {
        // Hide full page loading indicator
        if (typeof hideLoading === 'function') {
            hideLoading('executeSearchWithConditions');
        }
        const dataTable = tableContainer || document.querySelector('.data-table-wrapper');
        if (dataTable) dataTable.classList.remove('loading');
    }
}

function saveSearchConditions() {
    // Check if saveCurrentSearch function exists (from saved-searches.js)
    if (typeof saveCurrentSearch === 'function') {
        // Use the proper save function that saves to database
        saveCurrentSearch();
    } else {
        // Fallback: Save to localStorage only (for backward compatibility)
        const searchData = {
            conditions: searchConditions.map(c => ({
                operator: c.operator,
                category: c.category,
                query: c.query,
                fields: c.fields,
                indentLevel: c.indentLevel || 0
            })),
            timestamp: new Date().toISOString()
        };

        localStorage.setItem('savedSearchConditions', JSON.stringify(searchData));

        // Show success message
        if (typeof showSuccessMessage === 'function') {
            showSuccessMessage('Search conditions saved to local storage!');
        } else {
            alert('Search conditions saved to local storage!');
        }
    }
}

function updateSearchCounter() {
    const counterBtn = document.getElementById('searchCounter');
    const clearBtn = document.getElementById('clearSearchBtn');
    // Count active (non-muted) conditions; show 1 when this is a bulk-selection search (one logical search)
    const activeCount = searchConditions.filter(c => !c.muted).length;
    const totalCount = searchConditions.length;
    if (totalCount === 0) {
        window._searchBulkSelectionActive = false;
    }
    const displayCount = (window._searchBulkSelectionActive && totalCount > 0) ? 1 : activeCount;

    if (counterBtn) {
        const counterNumber = counterBtn.querySelector('.search-counter-number');
        if (counterNumber) {
            counterNumber.textContent = displayCount;
        }
        if (totalCount > 0) {
            counterBtn.style.display = 'inline-flex';
        } else {
            counterBtn.style.display = 'none';
        }
    }

    // Clear button visible when there is any search activity:
    // 1. Search conditions exist
    // 2. Query in input field (committed or not)
    // 3. Unison search results exist
    // 4. URL has query parameters (searchId, q, etc.)
    if (clearBtn) {
        const hasSearchConditions = totalCount > 0;
        const currentQuery = getCurrentQuery();
        const hasQueryInInput = currentQuery && currentQuery.trim() !== '';
        const hasCommittedSearch = isSearchCommitted;
        const hasUnisonResults = currentUnisonSearchResults &&
            currentUnisonSearchResults.results &&
            Object.keys(currentUnisonSearchResults.results).length > 0;

        // Check for URL parameters
        const urlParams = new URLSearchParams(window.location.search);
        const hasUrlParams = urlParams.has('searchId') || urlParams.has('q') || urlParams.has('category');

        // Show clear button if any search activity exists
        const shouldShowClear = hasSearchConditions || hasQueryInInput || hasCommittedSearch ||
            hasUnisonResults || hasUrlParams;

        if (shouldShowClear) {
            clearBtn.style.display = 'inline-flex';
        } else {
            clearBtn.style.display = 'none';
        }
    }

    // Update the conditions display in the dropdown
    updateSearchCounterUI();
}

/**
 * Update Search Counter UI with improved display
 */
function updateSearchCounterUI() {
    const builderContainer = document.getElementById('queryBuilderContainer');
    if (!builderContainer || builderContainer.style.display === 'none') {
        return;
    }

    if (typeof renderSearchConditions === 'function') {
        renderSearchConditions();
    }
}

/**
 * Toggle mute/unmute for a condition
 */
function toggleMuteCondition(index) {
    if (searchConditions[index]) {
        searchConditions[index].muted = !searchConditions[index].muted;
        window.searchConditions = searchConditions;
        updateSearchCounterUI();
        updateSearchCounter();
        executeMultiConditionSearch();
    }
}

/**
 * Run only this condition (mute all others)
 */
function runOnlyThisCondition(index) {
    searchConditions.forEach((c, i) => {
        c.muted = (i !== index);
    });
    window.searchConditions = searchConditions;
    updateSearchCounterUI();
    updateSearchCounter();
    executeMultiConditionSearch();
}

/**
 * Remove a condition
 */
function removeCondition(index) {
    window._searchBulkSelectionActive = false;
    searchConditions.splice(index, 1);
    window.searchConditions = searchConditions;
    updateSearchCounterUI();
    updateSearchCounter();

    if (searchConditions.length === 0) {
        disableOperatorOptions();
        const category = getActiveCategoryWithFallback();
        loadCategoryData(category);
    } else {
        // Keep operators enabled if conditions remain
        enableOperatorOptions();
        executeMultiConditionSearch();
    }
}

/**
 * Clear all conditions
 */
function clearAllConditions() {
    if (confirm('Clear all search conditions?')) {
        const preservedCategory = getActiveCategoryWithFallback();
        window._searchBulkSelectionActive = false;
        // Clear search conditions
        searchConditions = [];
        window.searchConditions = searchConditions;

        // Clear all filtered data and Unison Search results
        currentFilteredData = null;
        currentFilteredCategory = null;
        currentUnisonSearchResults = null;
        isSearchCommitted = false;

        // Clear all facet indicators (highlighting and filter badges)
        clearAllFacetIndicators();

        if (typeof window !== 'undefined') {
            if (window.categoryCounts) window.categoryCounts.clear();
            if (window.setFacetCountSource) window.setFacetCountSource('baseline');
            if (typeof window.preloadModuleRowCounts === 'function') {
                window.preloadModuleRowCounts().catch(() => { });
            }
        }

        // Clear search input field
        const searchInput = document.querySelector('.search-main-input');
        if (searchInput) {
            searchInput.value = '';
        }

        // Reset operator select to FIND
        const operatorSelect = document.querySelector('.search-type-select');
        if (operatorSelect) {
            operatorSelect.value = 'find';
        }

        // Update UI
        updateSearchCounterUI();
        updateSearchCounter();
        disableOperatorOptions();

        // Re-render search conditions (to remove them from UI)
        renderSearchConditions();

        // Reload table in the same facet the user was already on (do not reset facet selection)
        const category = preservedCategory || getActiveCategoryWithFallback();
        if (typeof setActiveCategory === 'function' && category) {
            setActiveCategory(category);
        }
        loadCategoryData(category);
    }
}

function initSearchCounter() {
    const counterBtn = document.getElementById('searchCounter');
    const clearBtn = document.getElementById('clearSearchBtn');

    if (counterBtn) {
        counterBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            const builderContainer = document.getElementById('queryBuilderContainer');
            if (builderContainer) {
                const isVisible = builderContainer.style.display !== 'none' && builderContainer.style.display !== '';

                if (isVisible) {
                    builderContainer.style.display = 'none';
                } else {
                    builderContainer.style.display = 'block';
                }
            }
        });

        // Close when clicking outside
        document.addEventListener('click', (e) => {
            const builderContainer = document.getElementById('queryBuilderContainer');
            if (builderContainer && counterBtn &&
                !builderContainer.contains(e.target) &&
                !counterBtn.contains(e.target)) {
                builderContainer.style.display = 'none';
            }
        });
    }

    if (clearBtn) {
        clearBtn.addEventListener('click', () => {
            // Redirect to search.html without any query parameters
            // This will refresh the page and clear all search state
            window.location.replace('/search.html');
        });
    }
}

/**
 * Initialize Filter Button and Panel
 */
function initFilterButton() {
    // Guard against double initialization
    if (window.__filterInitialized) {
        console.warn('[Filter] Already initialized - skipping');
        return;
    }

    // Retry mechanism: try to find elements with a delay if not found immediately
    const maxRetries = 3;
    let retryCount = 0;

    function tryInitFilterButton() {
        const filterBtn = document.querySelector('.filter-btn');
        const filterPanel = document.getElementById('filterPanel');
        const filterPanelClose = document.getElementById('filterPanelClose');

        if (!filterBtn || !filterPanel) {
            if (retryCount < maxRetries) {
                retryCount++;
                const missing = [];
                if (!filterBtn) missing.push('button');
                if (!filterPanel) missing.push('panel');
                setTimeout(tryInitFilterButton, 100 * retryCount);
                return;
            } else {
                const missing = [];
                if (!filterBtn) missing.push('button');
                if (!filterPanel) missing.push('panel');
                console.warn(`[Filter] ${missing.join(' and ')} not found after ${maxRetries} retries`);
                return;
            }
        }

        // Mark as initialized after successful setup
        window.__filterInitialized = true;

        // Remove any existing event listeners by cloning the button
        // This prevents duplicate event listeners if initFilterButton is called multiple times
        const newFilterBtn = filterBtn.cloneNode(true);
        filterBtn.parentNode.replaceChild(newFilterBtn, filterBtn);

        // Toggle filter panel on button click
        newFilterBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            e.preventDefault();

            const currentFilterPanel = document.getElementById('filterPanel');
            if (!currentFilterPanel) {
                console.warn('[Filter] Filter panel not found on click');
                return;
            }

            // Check if panel is currently visible
            const computedStyle = window.getComputedStyle(currentFilterPanel);
            const isVisible = computedStyle.display !== 'none' && currentFilterPanel.style.display === 'block';

            if (isVisible) {
                closeFilterPanel();
            } else {
                openFilterPanel();
            }
        });

        // Close button handler
        if (filterPanelClose) {
            // Remove existing listeners by cloning
            const newFilterPanelClose = filterPanelClose.cloneNode(true);
            filterPanelClose.parentNode.replaceChild(newFilterPanelClose, filterPanelClose);

            newFilterPanelClose.addEventListener('click', (e) => {
                e.stopPropagation();
                e.preventDefault();
                closeFilterPanel();
            });
        }

        // Close panel when clicking outside (only add once)
        if (!window.filterPanelClickOutsideHandler) {
            window.filterPanelClickOutsideHandler = (e) => {
                const currentFilterPanel = document.getElementById('filterPanel');
                const currentFilterBtn = document.querySelector('.filter-btn');

                // Check if click is inside filter panel or any of its dropdowns/components
                const isInsidePanel = e.target.closest('#filterPanel') || 
                                     e.target.closest('.filter-panel') ||
                                     e.target.closest('.filter-dropdown-options') ||
                                     e.target.closest('.filter-dropdown-values') ||
                                     e.target.closest('.filter-people-results');
                
                const isFilterButton = e.target.closest('.filter-btn');

                if (currentFilterPanel && !isInsidePanel && !isFilterButton) {
                    closeFilterPanel();
                }
            };
            document.addEventListener('click', window.filterPanelClickOutsideHandler);
        }

        // Close panel on Escape key (only add once)
        if (!window.filterPanelEscapeHandler) {
            window.filterPanelEscapeHandler = (e) => {
                const currentFilterPanel = document.getElementById('filterPanel');
                if (e.key === 'Escape' && currentFilterPanel &&
                    currentFilterPanel.style.display !== 'none') {
                    closeFilterPanel();
                }
            };
            document.addEventListener('keydown', window.filterPanelEscapeHandler);
        }

        // Prevent clicks inside the panel from bubbling up and closing it
        filterPanel.addEventListener('click', (e) => {
            // Don't stop propagation for close button - let it through
            if (!e.target.closest('.filter-panel-close')) {
                e.stopPropagation();
            }
        });
        
        // Initialize all filter panel dropdowns
        initFilterPanelDropdowns();
        
        // Initialize filter action buttons
        initFilterActionButtons();
    }

    // Start initialization
    tryInitFilterButton();
}

/**
 * Initialize filter action buttons (Apply, Clear All)
 */
function initFilterActionButtons() {
    const applyButton = document.getElementById('filterApplyFilters');
    const clearAllButton = document.getElementById('filterClearAll');
    
    if (applyButton) {
        const newApplyButton = applyButton.cloneNode(true);
        applyButton.parentNode.replaceChild(newApplyButton, applyButton);
        newApplyButton.addEventListener('click', async (e) => {
            e.preventDefault();
            e.stopPropagation();
            await applyFiltersAndSearch();
        });
    }
    
    if (clearAllButton) {
        clearAllButton.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (typeof clearAllFilters === 'function') {
                clearAllFilters();
            }
        });
    }
}

/**
 * Human-readable field + value for one activeFilters row (chips / query bar).
 * @param {object} f - activeFilters entry
 * @returns {{ fieldLabel: string, valueLabel: string }}
 */
function computeActiveFilterDisplayParts(f) {
    const fieldLabel = f.fieldName || f.fieldId || '';
    let valueLabel = '';
    if (f.value !== null && f.value !== undefined) {
        if (f.quickFilterLabel) {
            valueLabel = f.quickFilterLabel;
        } else if (typeof f.value === 'object' && !Array.isArray(f.value)) {
            const parts = [];
            if (f.value.from) parts.push('from ' + f.value.from);
            if (f.value.to) parts.push('to ' + f.value.to);
            valueLabel = parts.join(' ') || '';
        } else if (Array.isArray(f.value)) {
            if (f.fieldType === 'PEOPLE' && f.value.length > 0 && typeof f.value[0] === 'object') {
                valueLabel = f.value.map(p => p.name || p.id).join(', ');
            } else if (f.fieldType === 'BOOLEAN') {
                const names = [];
                for (const id of f.value) {
                    const cb = document.querySelector(
                        `input[type="checkbox"][data-filter-id="${f.id}"][value="${id}"]`);
                    const lab = cb && cb.nextElementSibling
                        ? cb.nextElementSibling.textContent.split(' (')[0].trim()
                        : null;
                    if (lab) {
                        names.push(lab);
                    } else if (id === 1 || id === '1') {
                        names.push('Yes');
                    } else if (id === 0 || id === '0') {
                        names.push('No');
                    } else {
                        names.push(String(id));
                    }
                }
                valueLabel = names.join(', ');
            } else if (f.fieldType === 'DROPDOWN' && Array.isArray(f.dropdownLabels)
                    && f.dropdownLabels.length === f.value.length) {
                valueLabel = f.dropdownLabels.join(', ');
            } else if (f.fieldType === 'DROPDOWN') {
                const names = [];
                for (const id of f.value) {
                    const cb = document.querySelector(
                        `input[type="checkbox"][data-filter-id="${f.id}"][value="${id}"]`);
                    const lab = cb && cb.nextElementSibling
                        ? cb.nextElementSibling.textContent.split(' (')[0].trim()
                        : null;
                    names.push(lab || String(id));
                }
                valueLabel = names.join(', ');
            } else {
                valueLabel = f.value.join(', ');
            }
        } else {
            valueLabel = String(f.value);
        }
    }
    return { fieldLabel, valueLabel };
}

/**
 * Build query-bar text for all filter keys on a condition (after Apply updates filters).
 */
function buildDisplayQueryForConditionFilters(filters) {
    if (!filters || typeof filters !== 'object' || Object.keys(filters).length === 0) {
        return '';
    }
    const activeFilterMeta = typeof activeFilters !== 'undefined' ? activeFilters : [];
    const parts = [];
    Object.keys(filters).forEach((fieldId) => {
        const f = activeFilterMeta.find(a => a.fieldId === fieldId);
        if (f) {
            const { fieldLabel, valueLabel } = computeActiveFilterDisplayParts(f);
            parts.push(valueLabel ? `${fieldLabel}: ${valueLabel}` : fieldLabel);
        } else {
            const v = filters[fieldId];
            parts.push(`${fieldId}: ${Array.isArray(v) ? v.join(', ') : String(v)}`);
        }
    });
    return parts.join(' · ');
}

/**
 * True when two condition categories refer to the same facet (handles slug aliases).
 */
function searchConditionCategoryMatches(condCategory, targetCategory) {
    if (!condCategory || !targetCategory) return false;
    if (typeof canonicalCategoryKey === 'function') {
        return canonicalCategoryKey(condCategory) === canonicalCategoryKey(targetCategory);
    }
    return String(condCategory).toLowerCase().trim() === String(targetCategory).toLowerCase().trim();
}

/**
 * Match activeFilters row to a filtersObj key — Object.keys() yields strings; fieldId may be number.
 */
function activeFilterRowForFieldKey(afList, fieldKey) {
    if (!afList || !afList.length || fieldKey == null) return null;
    const ks = String(fieldKey);
    return afList.find(f => f.fieldId != null && String(f.fieldId) === ks) || null;
}

/**
 * Label map lookup resilient to string vs numeric fieldId keys.
 */
function filterLabelMetaLookup(filterLabelMap, fieldId) {
    if (!filterLabelMap) return null;
    return filterLabelMap[fieldId] || filterLabelMap[String(fieldId)] || filterLabelMap[Number(fieldId)] || null;
}

/**
 * Refresh condition.displayQuery when panel filters change but searchConditions already exist.
 */
function syncDisplayQueriesAfterFilterMerge(category) {
    if (!searchConditions || !Array.isArray(searchConditions)) return;
    searchConditions.forEach((condition) => {
        if (!searchConditionCategoryMatches(condition.category, category) || condition.muted) return;
        if (!condition.filters || Object.keys(condition.filters).length === 0) return;
        const filterPart = buildDisplayQueryForConditionFilters(condition.filters);
        if (!filterPart) return;
        const q = condition.query != null ? String(condition.query).trim() : '';
        if (condition.isFilterCondition || !q || q === '*') {
            condition.displayQuery = filterPart;
        } else {
            condition.displayQuery = `${q} · ${filterPart}`;
        }
    });
    window.searchConditions = searchConditions;
}

/**
 * Apply filters and execute search
 */
/**
 * Apply any isDisplayFilter conditions to a row array for a given category.
 * These conditions are stored in searchConditions but are NOT sent to the backend.
 * Instead they are applied here, client-side, to the rows already fetched.
 *
 * @param {string} category  - e.g. 'people'
 * @param {Array}  rows      - the fetched row objects for this category
 * @returns {Array} filtered rows
 */
function applyDisplayFiltersToRows(category, rows) {
    if (!rows || rows.length === 0) return rows;
    const conditionsSource =
        (typeof window !== 'undefined' && Array.isArray(window.searchConditions))
            ? window.searchConditions
            : searchConditions;
    if (!conditionsSource || conditionsSource.length === 0) {
        console.log('[DisplayFilter][DEBUG] no conditions (module len=' +
            (searchConditions ? searchConditions.length : 0) + ' window len=' +
            (typeof window !== 'undefined' && window.searchConditions ? window.searchConditions.length : 'n/a') + ')');
        return rows;
    }

    // Collect all display-filter conditions targeting this category
    const dfConditions = conditionsSource.filter(c =>
        c.isDisplayFilter && !c.muted && searchConditionCategoryMatches(c.category, category) && c.filters && Object.keys(c.filters).length > 0
    );
    const moduleVsWindowMismatch =
        typeof window !== 'undefined' &&
        Array.isArray(window.searchConditions) &&
        searchConditions &&
        window.searchConditions !== searchConditions &&
        window.searchConditions.length !== searchConditions.length;
    console.log('[DisplayFilter] checking category=' + category + ' | conditions count=' + conditionsSource.length +
        ' | dfConditions found=' + dfConditions.length +
        (moduleVsWindowMismatch ? ' | WARN: window.searchConditions length differs from module searchConditions' : ''));
    console.log('[DisplayFilter][DEBUG]', {
        targetCategory: category,
        usingSource: conditionsSource === window.searchConditions ? 'window.searchConditions' : 'module searchConditions',
        conditions: conditionsSource.map(c => ({
            id: c.id,
            op: c.operator,
            category: c.category,
            isDisplayFilter: !!c.isDisplayFilter,
            isFilterCondition: !!c.isFilterCondition,
            muted: !!c.muted,
            filterKeys: c.filters ? Object.keys(c.filters) : [],
            query: c.query != null ? String(c.query).slice(0, 80) : ''
        })),
        dfSkipReason: conditionsSource
            .filter(c => c.filters && Object.keys(c.filters).length > 0 && searchConditionCategoryMatches(c.category, category) && !c.muted)
            .map(c => ({
                id: c.id,
                isDisplayFilter: !!c.isDisplayFilter,
                note: c.isDisplayFilter ? 'ok' : 'has filters but isDisplayFilter=false (merge path or bug)'
            }))
    });
    if (dfConditions.length === 0) return rows;

    // Build lookup maps from activeFilters:
    //   fieldColumnMap[fieldId]      → DB column name   (e.g. 'System_Role')
    //   fieldNameMap[fieldId]        → display field name (e.g. 'Profile Name')
    //   filterValueLabelsMap[fieldId][id] → label string (e.g. 2 → 'Super Admin')
    const fieldColumnMap = {};
    const fieldNameMap = {};
    // value-ID → display label built from dropdownLabels (parallel to the value array stored in activeFilters)
    const filterValueLabelsMap = {};
    if (typeof activeFilters !== 'undefined') {
        activeFilters.forEach(af => {
            if (!af.fieldId) return;
            if (af.fieldColumn) fieldColumnMap[af.fieldId] = af.fieldColumn;
            if (af.fieldName) fieldNameMap[af.fieldId] = af.fieldName;
            // dropdownLabels is a parallel array to af.value e.g. value=[2], dropdownLabels=['Super Admin']
            if (af.value && af.dropdownLabels && Array.isArray(af.value) && Array.isArray(af.dropdownLabels)) {
                const map = {};
                af.value.forEach((v, i) => {
                    if (af.dropdownLabels[i] != null) {
                        map[Number(v)] = af.dropdownLabels[i];
                    }
                });
                if (Object.keys(map).length > 0) {
                    filterValueLabelsMap[af.fieldId] = map;
                }
            }
        });
    }

    console.log('[DisplayFilter] Applying display filters to', category, 'rows:', rows.length,
        '| dfConditions:', dfConditions.map(c => c.filters),
        '| fieldColumnMap:', fieldColumnMap,
        '| fieldNameMap:', fieldNameMap,
        '| filterValueLabelsMap:', filterValueLabelsMap,
        '| sampleRow keys:', rows[0] ? Object.keys(rows[0]).slice(0, 20) : []);

    let filtered = rows;
    dfConditions.forEach(cond => {
        Object.entries(cond.filters).forEach(([fieldId, value]) => {
            if (cond.displayFilterSnapshots) {
                const snap = cond.displayFilterSnapshots[String(fieldId)] || cond.displayFilterSnapshots[fieldId];
                if (snap) {
                    if (snap.fieldColumn) fieldColumnMap[fieldId] = snap.fieldColumn;
                    if (snap.fieldName) fieldNameMap[fieldId] = snap.fieldName;
                    if (snap.dropdownLabels && value != null) {
                        const vals = Array.isArray(value) ? value : [value];
                        const map = {};
                        vals.forEach((v, i) => {
                            if (snap.dropdownLabels[i] != null) {
                                map[Number(v)] = snap.dropdownLabels[i];
                            }
                        });
                        if (Object.keys(map).length > 0) {
                            filterValueLabelsMap[fieldId] = map;
                        }
                    }
                }
            }
            const wMeta = typeof window !== 'undefined' ? window.filterFieldsMetadata : null;
            const ftc = typeof facetIdToCategory === 'function' ? facetIdToCategory
                : (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null);
            const metaMatchesCategory = !!(wMeta && wMeta.facetId && typeof ftc === 'function'
                && searchConditionCategoryMatches(ftc(wMeta.facetId), category));
            if ((!fieldColumnMap[fieldId] && !fieldColumnMap[String(fieldId)]) && metaMatchesCategory
                && wMeta && Array.isArray(wMeta.filterFields)) {
                const ff = wMeta.filterFields.find(f => String(f.id) === String(fieldId));
                if (ff) {
                    if (ff.fieldName) fieldColumnMap[fieldId] = ff.fieldName;
                    if (ff.name) fieldNameMap[fieldId] = ff.name;
                }
            }
            const col = fieldColumnMap[fieldId] || fieldColumnMap[String(fieldId)] || fieldId;
            const allowed = new Set(
                (Array.isArray(value) ? value : [value]).map(v => Number(v))
            );
            if (allowed.size === 0) return;

            // Build an allowed-names set for string comparison fallback
            const labelMap = filterValueLabelsMap[fieldId] || filterValueLabelsMap[String(fieldId)] || {};
            const allowedNames = new Set(
                Array.from(allowed)
                    .map(id => labelMap[id])
                    .filter(name => name != null)
                    .map(name => name.trim().toLowerCase())
            );

            // The display field name (e.g. 'Profile Name') to use as fallback
            const displayFieldName = fieldNameMap[fieldId] || null;

            console.log('[DisplayFilter] col=' + col + ' allowed IDs=' + JSON.stringify(Array.from(allowed)) +
                ' allowedNames=' + JSON.stringify(Array.from(allowedNames)) +
                ' displayFieldName=' + displayFieldName +
                ' | sample row[col]=' + (rows[0] ? (rows[0][col] ?? rows[0][col.toLowerCase()] ?? rows[0][col.toUpperCase()]) : 'N/A') +
                ' sample row[displayField]=' + (rows[0] && displayFieldName ? rows[0][displayFieldName] : 'N/A'));

            filtered = filtered.filter(row => {
                // Try ID-based comparison first (exact column match)
                const rawVal = row[col] ?? row[col.toLowerCase()] ?? row[col.toUpperCase()];
                if (rawVal != null && rawVal !== '') {
                    return allowed.has(Number(rawVal));
                }
                // Fallback: string comparison using the display field name
                if (displayFieldName && allowedNames.size > 0) {
                    const nameVal = row[displayFieldName];
                    if (nameVal != null) {
                        return allowedNames.has(String(nameVal).trim().toLowerCase());
                    }
                }
                return false;
            });
        });
    });
    console.log('[DisplayFilter] Result:', filtered.length, '/', rows.length, 'rows kept');
    return filtered;
}

async function applyFiltersAndSearch() {
    
    // Build filters object
    const filtersObj = typeof buildFiltersObject === 'function' ? buildFiltersObject() : {};
    
    // Category for merge / isDisplayFilter rows:
    // 1) Prefer facetId stored on each activeFilters row (stable; not overwritten when sidebar flips).
    // 2) Else window.currentFilterFacetId from last loadFilterFields (can be wrong after Unison resets tab).
    // 3) Else DOM active category.
    const domCategoryBeforePanel = getActiveCategoryWithFallback();
    let category = domCategoryBeforePanel;

    const afList = typeof activeFilters !== 'undefined' ? activeFilters : [];
    const facetToCatFn = typeof facetIdToCategory === 'function' ? facetIdToCategory
        : (typeof window !== 'undefined' && typeof window.facetIdToCategory === 'function' ? window.facetIdToCategory : null);

    const facetIdsFromAppliedFilters = new Set();
    Object.keys(filtersObj).forEach((fieldKey) => {
        const row = activeFilterRowForFieldKey(afList, fieldKey);
        let fid = row && row.facetId;
        if (!fid && row && typeof window !== 'undefined' && window.filterFieldsMetadata && window.filterFieldsMetadata.facetId) {
            fid = window.filterFieldsMetadata.facetId;
        }
        if (fid) {
            facetIdsFromAppliedFilters.add(String(fid).toUpperCase());
        }
    });
    let categoryFromFilterFacetIds = null;
    if (facetIdsFromAppliedFilters.size === 1 && typeof facetToCatFn === 'function') {
        const onlyFacet = Array.from(facetIdsFromAppliedFilters)[0];
        const fromFacet = facetToCatFn(onlyFacet);
        if (fromFacet) {
            categoryFromFilterFacetIds = typeof canonicalCategoryKey === 'function'
                ? canonicalCategoryKey(fromFacet)
                : fromFacet;
        }
    }

    let categoryFromOpenFilterMetadata = null;
    if (!categoryFromFilterFacetIds && Object.keys(filtersObj).length > 0 && typeof window !== 'undefined'
        && window.filterFieldsMetadata && window.filterFieldsMetadata.facetId && typeof facetToCatFn === 'function') {
        const fromMeta = facetToCatFn(window.filterFieldsMetadata.facetId);
        if (fromMeta) {
            categoryFromOpenFilterMetadata = typeof canonicalCategoryKey === 'function'
                ? canonicalCategoryKey(fromMeta)
                : fromMeta;
        }
    }

    if (categoryFromFilterFacetIds) {
        category = categoryFromFilterFacetIds;
    } else if (categoryFromOpenFilterMetadata) {
        category = categoryFromOpenFilterMetadata;
    } else {
        const panelFacet = typeof window !== 'undefined' ? window.currentFilterFacetId : null;
        if (panelFacet && typeof facetToCatFn === 'function') {
            const fromPanel = facetToCatFn(panelFacet);
            if (fromPanel) {
                category = typeof canonicalCategoryKey === 'function' ? canonicalCategoryKey(fromPanel) : fromPanel;
            }
        }
    }
    if (!category) {
        console.warn('[Filters] No active category');
        return;
    }
    if (typeof setActiveCategory === 'function') {
        setActiveCategory(category);
    }

    const panelFacet = typeof window !== 'undefined' ? window.currentFilterFacetId : null;
    console.log('[FilterApply][DEBUG]', {
        filtersObjKeys: Object.keys(filtersObj),
        filtersObj,
        domCategoryBeforePanel,
        facetIdsFromAppliedFilters: Array.from(facetIdsFromAppliedFilters),
        categoryFromFilterFacetIds,
        categoryFromOpenFilterMetadata,
        metadataFacetId: typeof window !== 'undefined' && window.filterFieldsMetadata ? window.filterFieldsMetadata.facetId : undefined,
        panelFacet,
        windowCurrentFilterFacetId: typeof window !== 'undefined' ? window.currentFilterFacetId : undefined,
        resolvedCategoryForMerge: category,
        domVsResolvedMatch: domCategoryBeforePanel === category
    });
    
    // Check if there are any search conditions or filters
    const hasFilters = Object.keys(filtersObj).length > 0;
    const hasConditions = searchConditions && searchConditions.length > 0;
    
    if (!hasFilters && !hasConditions) {
        closeFilterPanel();
        return;
    }
    
    // If we have filters but no search conditions, create one condition per filter
    // so each filter appears as its own row and the counter reflects the true filter count.
    // First filter uses FIND (root), subsequent filters use AND (intersect) on the same facet.
    if (hasFilters && !hasConditions) {
        searchConditions = [];
        const filterEntries = Object.entries(filtersObj);
        const baseId = Date.now();

        // Build a lookup: fieldId → { fieldLabel, valueLabel } for query-builder display
        const activeFilterMeta = typeof activeFilters !== 'undefined' ? activeFilters : [];
        const filterLabelMap = {};
        activeFilterMeta.forEach(f => {
            if (!f.fieldId) return;
            const { fieldLabel, valueLabel } = computeActiveFilterDisplayParts(f);
            const meta = { fieldLabel, valueLabel };
            filterLabelMap[f.fieldId] = meta;
            filterLabelMap[String(f.fieldId)] = meta;
        });

        filterEntries.forEach(([fieldId, value], index) => {
            const meta = filterLabelMetaLookup(filterLabelMap, fieldId) || { fieldLabel: fieldId, valueLabel: String(value) };
            const displayQuery = meta.valueLabel
                ? `${meta.fieldLabel}: ${meta.valueLabel}`
                : meta.fieldLabel;
            searchConditions.push({
                id: baseId + index,
                operator: index === 0 ? 'FIND' : 'AND',
                category: category,
                query: '*',
                displayQuery,
                fields: [],
                muted: false,
                filters: { [fieldId]: value },
                isFilterCondition: true
            });
        });
        window.searchConditions = searchConditions;
        
        // Render the conditions
        if (typeof renderSearchConditions === 'function') {
            renderSearchConditions();
        }
        
        // Update counter
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
    } else if (hasFilters && hasConditions) {
        // Add filters to existing conditions that match the current category
        let addedToExisting = false;
        searchConditions.forEach(condition => {
            if (searchConditionCategoryMatches(condition.category, category) && !condition.muted) {
                condition.filters = { ...condition.filters, ...filtersObj };
                addedToExisting = true;
            }
        });

        console.log('[FilterApply][DEBUG] merge branch', {
            resolvedCategoryForMerge: category,
            addedToExisting,
            existingCategories: searchConditions.map(c => ({ id: c.id, cat: c.category, op: c.operator }))
        });

        // No existing condition for the current category (e.g. viewing People while
        // the active FIND is for System). Add the filter as a display-only AND condition
        // (isDisplayFilter:true) so it is persisted in saved searches, but the backend
        // skips the intersection phase and only applies it at row-data fetch time.
        // This filters the displayed People WITHOUT reducing the System count.
        if (!addedToExisting) {
            const baseId = Date.now();
            const activeFilterMeta = typeof activeFilters !== 'undefined' ? activeFilters : [];
            const filterLabelMap = {};
            activeFilterMeta.forEach(f => {
                if (!f.fieldId) return;
                if (typeof computeActiveFilterDisplayParts === 'function') {
                    const { fieldLabel, valueLabel } = computeActiveFilterDisplayParts(f);
                    const meta = { fieldLabel, valueLabel };
                    filterLabelMap[f.fieldId] = meta;
                    filterLabelMap[String(f.fieldId)] = meta;
                }
            });
            Object.entries(filtersObj).forEach(([fieldId, value], index) => {
                const meta = filterLabelMetaLookup(filterLabelMap, fieldId) || { fieldLabel: fieldId, valueLabel: String(value) };
                const displayQuery = meta.valueLabel
                    ? `${meta.fieldLabel}: ${meta.valueLabel}`
                    : meta.fieldLabel;
                const afRow = activeFilterRowForFieldKey(activeFilterMeta, fieldId);
                const displayFilterSnapshots = afRow ? {
                    [fieldId]: {
                        fieldColumn: afRow.fieldColumn,
                        fieldName: afRow.fieldName,
                        dropdownLabels: afRow.dropdownLabels
                    }
                } : undefined;
                searchConditions.push({
                    id: baseId + index,
                    operator: 'AND',
                    category: category,
                    query: '*',
                    displayQuery,
                    fields: [],
                    muted: false,
                    filters: { [fieldId]: value },
                    isFilterCondition: true,
                    isDisplayFilter: true,
                    displayFilterSnapshots: displayFilterSnapshots
                });
            });
        }

        window.searchConditions = searchConditions;
        console.log('[FilterApply][DEBUG] after merge / display rows', {
            addedToExisting,
            displayFilterRowsAdded: !addedToExisting,
            searchConditionsSnapshot: searchConditions.map(c => ({
                id: c.id,
                op: c.operator,
                category: c.category,
                isDisplayFilter: !!c.isDisplayFilter,
                filterKeys: c.filters ? Object.keys(c.filters) : []
            }))
        });
        syncDisplayQueriesAfterFilterMerge(category);
        if (typeof renderSearchConditions === 'function') {
            renderSearchConditions();
        }
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
    }

    // Always sync the current "Search in" selection onto matching conditions so
    // the backend uses the right columns even when Apply is the only action taken.
    const currentSearchFields = typeof getActiveSearchFields === 'function' ? getActiveSearchFields() : null;
    const fieldsToSync = (currentSearchFields && Object.keys(currentSearchFields).length > 0)
        ? currentSearchFields
        : (typeof getEffectiveSearchFieldsForCategory === 'function' ? getEffectiveSearchFieldsForCategory(category) : null);
    if (fieldsToSync) {
        searchConditions.forEach(condition => {
            if (searchConditionCategoryMatches(condition.category, category) && !condition.muted) {
                condition.searchFields = { ...fieldsToSync };
            }
        });
        window.searchConditions = searchConditions;
        // Also keep the query-builder labels in sync
        if (typeof renderSearchConditions === 'function') {
            renderSearchConditions();
        }
    }
    
    // Execute search
    await executeMultiConditionSearch();

    console.log('[FilterApply][DEBUG] after executeMultiConditionSearch', {
        searchConditionsLen: searchConditions ? searchConditions.length : 0,
        windowSearchConditionsLen: typeof window !== 'undefined' && window.searchConditions ? window.searchConditions.length : 0,
        sameRef: typeof window !== 'undefined' && window.searchConditions === searchConditions,
        snapshot: (searchConditions || []).map(c => ({
            id: c.id,
            op: c.operator,
            category: c.category,
            isDisplayFilter: !!c.isDisplayFilter,
            filterKeys: c.filters ? Object.keys(c.filters) : []
        }))
    });
    
    // Update active filters chips
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    
    // Close filter panel
    closeFilterPanel();
    
}

/**
 * Close History + My searches toolbar menus (avoid stacking over the filter panel).
 */
function closeSearchToolbarDropdowns() {
    document.querySelectorAll('.history-dropdown-content, .my-searches-dropdown-content').forEach((dd) => {
        dd.style.display = 'none';
    });
}

/**
 * Open the filter panel
 */
async function openFilterPanel() {
    const filterPanel = document.getElementById('filterPanel');
    const filterBtn = document.querySelector('.filter-btn');

    if (!filterPanel) {
        console.warn('[Filter] Filter panel element not found');
        return;
    }
    
    if (!filterBtn) {
        console.warn('[Filter] Filter button element not found');
        return;
    }

    closeSearchToolbarDropdowns();

    // Load filter fields for current facet
    const category = getActiveCategoryWithFallback();
    if (category) {
        const facetId = typeof categoryToFacetId === 'function' ? categoryToFacetId(category) : category.toUpperCase();
        if (typeof loadFilterFields === 'function') {
            await loadFilterFields(facetId);
        }
    }

    // Show the panel (CSS already has positioning)
    filterPanel.style.display = 'flex';
    filterBtn.classList.add('active');
    
}

/**
 * Close the filter panel
 */
function closeFilterPanel() {
    const filterPanel = document.getElementById('filterPanel');
    const filterBtn = document.querySelector('.filter-btn');

    const layerSel = window.FILTER_DROPDOWN_LAYER_SELECTOR || '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results';
    document.querySelectorAll(layerSel).forEach((el) => {
        if (typeof window.resetFilterDropdownFloating === 'function') {
            window.resetFilterDropdownFloating(el);
        }
        el.style.display = 'none';
    });

    if (filterPanel) {
        filterPanel.style.display = 'none';
    }
    if (filterBtn) {
        filterBtn.classList.remove('active');
    }
}

/**
 * Initialize all filter panel dropdowns including hierarchical and general filters
 */
function initFilterPanelDropdowns() {
    // Initialize general filter dropdowns
    initGeneralFilterDropdowns();
    
    // Initialize hierarchical filter dropdowns
    initHierarchicalFilterDropdowns();
    
    // Setup global click handler to close dropdowns when clicking outside (only once)
    if (!window._filterDropdownClickHandler) {
        window._filterDropdownClickHandler = (e) => {
            // Check if click is inside filter panel or any dropdown component
            const isInsideFilterPanel = e.target.closest('#filterPanel') || e.target.closest('.filter-panel');
            const isInsideDropdown = e.target.closest('.filter-dropdown-btn') || 
                                    e.target.closest('.filter-dropdown-options') ||
                                    e.target.closest('.filter-dropdown-values') ||
                                    e.target.closest('.filter-people-results') ||
                                    e.target.closest('.filter-add-btn');
            
            if (!isInsideDropdown && !isInsideFilterPanel) {
                const layerSel = window.FILTER_DROPDOWN_LAYER_SELECTOR || '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results';
                document.querySelectorAll(layerSel).forEach((opt) => {
                    if (typeof window.resetFilterDropdownFloating === 'function') {
                        window.resetFilterDropdownFloating(opt);
                    }
                    opt.style.display = 'none';
                });
            }
        };
        document.addEventListener('click', window._filterDropdownClickHandler);
    }
}

/**
 * Initialize general filter dropdowns (All Fields, Add New, etc.)
 */
function initGeneralFilterDropdowns() {
    // Add New Filter dropdown - special handling
    const addNewBtn = document.getElementById('filterAddNew');
    const addNewOptions = document.getElementById('filterAddNewOptions');
    
    if (addNewBtn && addNewOptions) {
        // Toggle dropdown
        addNewBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            e.preventDefault();
            
            const layerSel = window.FILTER_DROPDOWN_LAYER_SELECTOR || '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results';
            document.querySelectorAll(layerSel).forEach((opt) => {
                if (opt !== addNewOptions) {
                    if (typeof window.resetFilterDropdownFloating === 'function') {
                        window.resetFilterDropdownFloating(opt);
                    }
                    opt.style.display = 'none';
                }
            });
            
            const isVisible = addNewOptions.style.display !== 'none';
            if (isVisible) {
                if (typeof window.resetFilterDropdownFloating === 'function') {
                    window.resetFilterDropdownFloating(addNewOptions);
                }
                addNewOptions.style.display = 'none';
            } else {
                addNewOptions.style.display = 'block';
                if (typeof window.positionFilterDropdownFloating === 'function') {
                    window.positionFilterDropdownFloating(addNewOptions, addNewBtn);
                }
                if (typeof window.initFilterDropdownFloatingListeners === 'function') {
                    window.initFilterDropdownFloatingListeners();
                }
                requestAnimationFrame(() => {
                    if (typeof window.refreshFloatingFilterDropdowns === 'function') {
                        window.refreshFloatingFilterDropdowns();
                    }
                });
            }
        });
        
        addNewOptions.addEventListener('click', (e) => {
            e.stopPropagation();
        });
    }
    
    // Add global click handler to close all dropdowns when clicking outside
    if (!window._filterDropdownCloseHandler) {
        window._filterDropdownCloseHandler = (e) => {
            // Check if click is outside all dropdowns and buttons
            const clickedElement = e.target;
            const isInsideDropdown = clickedElement.closest('.filter-dropdown-options') || 
                                    clickedElement.closest('.filter-dropdown-values') ||
                                    clickedElement.closest('.filter-people-results') ||
                                    clickedElement.closest('.filter-add-btn') ||
                                    clickedElement.closest('.filter-value-btn') ||
                                    clickedElement.closest('.filter-name-btn') ||
                                    clickedElement.closest('.filter-dropdown-btn');
            
            if (!isInsideDropdown) {
                const layerSel = window.FILTER_DROPDOWN_LAYER_SELECTOR || '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results';
                document.querySelectorAll(layerSel).forEach((dropdown) => {
                    if (typeof window.resetFilterDropdownFloating === 'function') {
                        window.resetFilterDropdownFloating(dropdown);
                    }
                    dropdown.style.display = 'none';
                });
            }
        };
        
        // Use capture phase to ensure it runs before other handlers
        document.addEventListener('click', window._filterDropdownCloseHandler, true);
    }
}

/**
 * Generic dropdown initializer
 */
function initDropdown(button, optionsContainer, onSelect) {
    // Prevent duplicate initialization
    if (button._dropdownInitialized) {
        return;
    }
    button._dropdownInitialized = true;
    
    // Toggle dropdown on button click
    button.addEventListener('click', (e) => {
        e.stopPropagation();
        
        const layerSel = window.FILTER_DROPDOWN_LAYER_SELECTOR || '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results';
        document.querySelectorAll(layerSel).forEach((opt) => {
            if (opt !== optionsContainer) {
                if (typeof window.resetFilterDropdownFloating === 'function') {
                    window.resetFilterDropdownFloating(opt);
                }
                opt.style.display = 'none';
            }
        });
        
        const isVisible = optionsContainer.style.display !== 'none';
        if (isVisible) {
            if (typeof window.resetFilterDropdownFloating === 'function') {
                window.resetFilterDropdownFloating(optionsContainer);
            }
            optionsContainer.style.display = 'none';
        } else {
            optionsContainer.style.display = 'block';
            if (typeof window.positionFilterDropdownFloating === 'function') {
                window.positionFilterDropdownFloating(optionsContainer, button);
            }
            if (typeof window.initFilterDropdownFloatingListeners === 'function') {
                window.initFilterDropdownFloatingListeners();
            }
            requestAnimationFrame(() => {
                if (typeof window.refreshFloatingFilterDropdowns === 'function') {
                    window.refreshFloatingFilterDropdowns();
                }
            });
        }
    });
    
    // Prevent clicks on the options container from propagating
    optionsContainer.addEventListener('click', (e) => {
        e.stopPropagation();
    });
    
    // Handle option selection
    optionsContainer.querySelectorAll('.filter-dropdown-option').forEach(option => {
        option.addEventListener('click', (e) => {
            e.stopPropagation();
            e.preventDefault();
            
            const value = option.getAttribute('data-value');
            const text = option.textContent.trim();
            
            // Update button text
            const span = button.querySelector('span');
            if (span) {
                span.textContent = text;
            }
            
            if (onSelect) {
                onSelect(value, text);
            }
            
            if (typeof window.resetFilterDropdownFloating === 'function') {
                window.resetFilterDropdownFloating(optionsContainer);
            }
            optionsContainer.style.display = 'none';
        });
    });
}

/**
 * Initialize hierarchical filter dropdowns for parent-child relationships
 */
function initHierarchicalFilterDropdowns() {
    const relationshipsBtn = document.getElementById('filterRelationshipsBtn');
    const relationshipsOptions = document.getElementById('filterRelationshipsOptions');
    const applyBtn = document.getElementById('filterApplyHierarchicalBtn');
    const applyOptions = document.getElementById('filterApplyHierarchicalOptions');

    if (!relationshipsBtn || !relationshipsOptions || !applyBtn || !applyOptions) {
        console.warn('[HierarchicalFilter] Filter dropdown elements not found');
        return;
    }

    // Initialize relationships dropdown
    initDropdown(relationshipsBtn, relationshipsOptions, (value, text) => {
        hierarchicalFilterOptions.childInclusion = value;
        window.hierarchicalFilterOptions = hierarchicalFilterOptions;
    });

    // Initialize apply filters dropdown
    initDropdown(applyBtn, applyOptions, (value, text) => {
        hierarchicalFilterOptions.applyFilters = value;
        window.hierarchicalFilterOptions = hierarchicalFilterOptions;
    });

    // Update visibility based on current facet
    updateHierarchicalFilterVisibility();
}

/**
 * Update visibility of hierarchical filter controls based on current facet
 */
function updateHierarchicalFilterVisibility() {
    const hierarchicalSection = document.getElementById('hierarchicalFiltersSection');
    
    if (!hierarchicalSection) {
        return;
    }

    // Get current active facet
    const activeFacet = document.querySelector('.category-tab.active');
    const currentFacet = activeFacet ? activeFacet.getAttribute('data-category') : null;
    
    // Check if current facet is hierarchical
    const isHierarchical = currentFacet && HIERARCHICAL_FACETS.includes(currentFacet.toUpperCase());
    
    // Show/hide the hierarchical filter section
    hierarchicalSection.style.display = isHierarchical ? 'block' : 'none';
}

function getCategoryIcon(categoryName) {
    const iconMap = {
        'SYSTEM': 'fas fa-server',
        'ATTRIBUTE': 'fas fa-th',
        'DATASET': 'fas fa-database',
        'GLOSSARY': 'fas fa-book',
        'PEOPLE': 'fas fa-user',
        'ROLE': 'fas fa-user-tie',
        'BUSINESS_AREA': 'fas fa-briefcase',
        'CLIENT': 'fas fa-user-tie',
        'JURISDICTION': 'fas fa-globe',
        'DATAQUALITY': 'fas fa-check-circle'
    };

    const upper = categoryName.toUpperCase();
    return iconMap[upper] || 'fas fa-tag';
}

function getCategoryFields(category) {
    const module = categoryToModule(category);
    return CATEGORY_FIELD_MAPPING[module] || {};
}

function getFieldValue(row, fieldConfig) {
    if (Array.isArray(fieldConfig)) {
        return fieldConfig.map(field => row[field] || '').join(' ').trim();
    }
    return row[fieldConfig] || '';
}

function initSearchSuggestions() {
    // Guard against double initialization
    if (window.__suggestionsInitialized) {
        console.warn('[Suggestions] Already initialized - skipping');
        return;
    }

    const input = document.querySelector('.search-main-input');
    if (!input) {
        console.warn('[Suggestions] Search input not found - retrying in 100ms');
        setTimeout(() => {
            if (!window.__suggestionsInitialized) {
                initSearchSuggestions();
            }
        }, 100);
        return;
    }

    const wrap = input.closest('.search-input-wrapper') || input.parentElement;
    
    // Check for existing dropdown before creating a new one
    let dropdown = wrap.querySelector('.search-suggestions');
    if (!dropdown) {
        dropdown = document.createElement('div');
        dropdown.className = 'search-suggestions';
        dropdown.style.position = 'absolute';
        dropdown.style.zIndex = '1000';
        dropdown.style.top = '100%';
        dropdown.style.left = '0';
        dropdown.style.right = '0';
        dropdown.style.background = 'var(--background-primary)';
        dropdown.style.border = '1px solid var(--border-color)';
        dropdown.style.borderTop = 'none';
        dropdown.style.display = 'none';
        dropdown.style.maxHeight = '260px';
        dropdown.style.overflowY = 'auto';
        dropdown.style.boxShadow = '0 6px 18px rgba(0,0,0,0.12)';
        wrap.style.position = 'relative';
        wrap.appendChild(dropdown);
    }

    let debounceTimer;

    input.addEventListener('input', async () => {
        clearTimeout(debounceTimer);
        const query = input.value.trim();
        const category = getActiveCategoryWithFallback();

        if (!query) {
            dropdown.style.display = 'none';
            dropdown.innerHTML = '';
            currentSuggestionsData = null;
            currentSuggestionsCategory = null;
            return;
        }

        // Debounce for suggestions only (200ms) - no automatic search
        debounceTimer = setTimeout(async () => {
            try {
                const data = await fetchCategoryData(category, query);
                currentSuggestionsCategory = category;
                renderSuggestions(dropdown, data || [], query, category);
            } catch (e) {
                console.error('[Suggestions] Error fetching or rendering data:', e);
                dropdown.style.display = 'none';
                dropdown.innerHTML = '';
                currentSuggestionsData = null;
                currentSuggestionsCategory = null;
            }
        }, 200);
    });

    document.addEventListener('click', (e) => {
        if (!dropdown.contains(e.target) && e.target !== input) {
            dropdown.style.display = 'none';
        }
    }, { passive: true });

    // Mark as initialized after successful setup
    window.__suggestionsInitialized = true;
}

function renderSuggestions(container, data, query, category) {
    container.innerHTML = '';
    const list = document.createElement('div');
    const qLower = query.toLowerCase();
    const matches = [];
    const fields = getCategoryFields(category);

    // Helper function to check match
    const checkMatch = (value) => {
        if (!value) return false;
        const valueLower = value.toLowerCase();

        if (fuzzySearchEnabled) {
            return fuzzyMatch(valueLower, qLower);
        } else {
            return valueLower.includes(qLower);
        }
    };

    for (let i = 0; i < data.length; i++) {
        const row = data[i];
        let displayVal = '';
        let found = false;

        if (fields.name && !found) {
            const nameVal = getFieldValue(row, fields.name);
            if (checkMatch(nameVal)) {
                displayVal = nameVal;
                found = true;
            }
        }

        if (fields.ref && !found) {
            const refVal = getFieldValue(row, fields.ref);
            if (checkMatch(refVal)) {
                displayVal = refVal;
                found = true;
            }
        }

        if (fields.longName && !found) {
            const longNameVal = getFieldValue(row, fields.longName);
            if (checkMatch(longNameVal)) {
                displayVal = longNameVal;
                found = true;
            }
        }

        if (fields.parent && !found) {
            const parentVal = getFieldValue(row, fields.parent);
            if (checkMatch(parentVal)) {
                displayVal = parentVal;
                found = true;
            }
        }

        if (fields.description && !found) {
            const descriptionVal = getFieldValue(row, fields.description);
            if (checkMatch(descriptionVal)) {
                displayVal = descriptionVal;
                found = true;
            }
        }

        if (fields.status && !found) {
            const statusVal = getFieldValue(row, fields.status);
            if (checkMatch(statusVal)) {
                displayVal = statusVal;
                found = true;
            }
        }

        if (fields.alias_names && !found) {
            const aliasVal = getFieldValue(row, fields.alias_names);
            if (checkMatch(aliasVal)) {
                displayVal = aliasVal;
                found = true;
            }
        }

        if (fields.format_type && !found) {
            const formatTypeVal = getFieldValue(row, fields.format_type);
            if (checkMatch(formatTypeVal)) {
                displayVal = formatTypeVal;
                found = true;
            }
        }

        if (found && displayVal) {
            let nameValue = '';
            if (fields.name) {
                nameValue = getFieldValue(row, fields.name);
            }

            matches.push({ row, displayVal: nameValue || displayVal });
            if (matches.length >= 10) break;
        }
    }

    if (matches.length === 0) {
        container.style.display = 'none';
        return;
    }

    matches.forEach(({ row, displayVal }) => {
        const item = document.createElement('div');
        item.className = 'suggestion-item';
        item.style.padding = '0.5rem 0.75rem';
        item.style.cursor = 'pointer';
        item.style.borderTop = '1px solid var(--border-color)';
        item.style.transition = 'background-color 0.2s ease';
        item.textContent = displayVal;
        item.addEventListener('mouseenter', () => {
            item.style.background = 'rgba(72, 187, 120, 0.08)';
        });
        item.addEventListener('mouseleave', () => {
            item.style.background = 'transparent';
        });
        item.addEventListener('click', async () => {
            container.style.display = 'none';

            const searchInput = document.querySelector('.search-main-input');
            if (searchInput) {
                searchInput.value = displayVal;
            }

            // Clear search input after a moment (like Enter key does)
            setTimeout(() => {
                if (searchInput) {
                    searchInput.value = '';
                }
            }, 100);

            if (row.ID || row.id) {
                const selectedItem = {
                    id: row.ID || row.id,
                    category: category,
                    name: displayVal
                };
                setSelectedItem(selectedItem);

                // Add one FIND condition so search-counter-number appears and AND/OR/NOT are enabled
                const hasRelatedCondition = searchConditions.some(c =>
                    c.operator === 'FIND' && c.category === category && (c.query === displayVal || String(c.query).trim() === displayVal || c.exactId === selectedItem.id));
                if (!hasRelatedCondition && typeof addSearchCondition === 'function') {
                    addSearchCondition('FIND', category, displayVal, { exactId: selectedItem.id });
                } else if (!hasRelatedCondition) {
                    // Fallback: ensure counter and operators show when only related selection exists
                    if (searchConditions.length === 0) {
                        searchConditions.push({
                            id: Date.now() + Math.random(),
                            operator: 'FIND',
                            category: category,
                            query: displayVal,
                            exactId: selectedItem.id,
                            fields: [],
                            muted: false
                        });
                        if (window.searchConditions) window.searchConditions = searchConditions;
                        if (typeof renderSearchConditions === 'function') renderSearchConditions();
                        if (typeof updateSearchCounter === 'function') updateSearchCounter();
                        if (typeof enableOperatorOptions === 'function') enableOperatorOptions();
                    }
                }

                // Show notification
                showSelectionNotification(displayVal, category);

                // Display the selected object in table (like Enter key behavior)
                const objectData = [row]; // Single object array
                const signature = typeof getSearchSignature === 'function' ? getSearchSignature() : null;
                generateTable(objectData, null, category);
                updateCategoryCount(category, 1, 1, false);
                updateSearchCounter(); // Show clear button after displaying suggestion
                if (typeof cacheModuleData === 'function') {
                    cacheModuleData(category, objectData, signature);
                }

                // Clear filtered data state
                currentFilteredData = null;
                currentFilteredCategory = null;

                // Execute Unison Search in background to populate related facet counts only.
                // We use the selected item's ID so the backend returns related objects,
                // but we NEVER overwrite currentUnisonSearchResults or the active-category table.
                try {
                    _loadRelatedCountsForItem(selectedItem, category);
                } catch (err) {
                    console.error('[Suggestion] Error setting up Unison search:', err);
                }
            }
            else {
                // Fallback: perform normal search
                performSearch();
            }
        });
        list.appendChild(item);
    });
    container.appendChild(list);
    
    container.style.display = 'block';
    
    // Store suggestions data for Enter key handling
    currentSuggestionsData = data;
}

/**
 * Enhanced search function with fuzzy search support (local filtering)
 */
function performLocalSearch(data, query, category = null) {
    if (!Array.isArray(data) || !query.trim()) return data;

    const queryLower = query.toLowerCase().trim();
    const results = [];
    const searchCategory = category || getActiveCategoryWithFallback();
    const fields = getCategoryFields(searchCategory);

    for (const row of data) {
        let found = false;

        // Helper function to check match (exact or fuzzy)
        const checkMatch = (value) => {
            if (!value) return false;
            const valueLower = value.toLowerCase();

            if (fuzzySearchEnabled) {
                return fuzzyMatch(valueLower, queryLower);
            } else {
                return valueLower.includes(queryLower);
            }
        };

        // Search in name field
        if (!found && fields.name) {
            const nameValue = getFieldValue(row, fields.name);
            if (checkMatch(nameValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in ref field
        if (!found && fields.ref) {
            const refValue = getFieldValue(row, fields.ref);
            if (checkMatch(refValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in longName field
        if (!found && fields.longName) {
            const longNameValue = getFieldValue(row, fields.longName);
            if (checkMatch(longNameValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in parent field
        if (!found && fields.parent) {
            const parentValue = getFieldValue(row, fields.parent);
            if (checkMatch(parentValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in description field
        if (!found && fields.description) {
            const descriptionValue = getFieldValue(row, fields.description);
            if (checkMatch(descriptionValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in status field
        if (!found && fields.status) {
            const statusValue = getFieldValue(row, fields.status);
            if (checkMatch(statusValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in alias_names field
        if (!found && fields.alias_names) {
            const aliasValue = getFieldValue(row, fields.alias_names);
            if (checkMatch(aliasValue)) {
                results.push(row);
                found = true;
            }
        }

        // Search in format_type field
        if (!found && fields.format_type) {
            const formatTypeValue = getFieldValue(row, fields.format_type);
            if (checkMatch(formatTypeValue)) {
                results.push(row);
                found = true;
            }
        }
    }

    return results;
}