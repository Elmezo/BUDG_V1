// Saved Searches Management
// Handles saved search creation, loading, execution, sharing, and recent views

// Namespace to avoid conflicts with other files
const savedSearchesState = {
    cache: null,
    editingSearchId: null
};

function i18nSearch(key, fallback) {
    return (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t(key) : fallback;
}

/**
 * Return the default display field names for a category (e.g. ['Name', 'Ref']).
 * Used as a fallback when a saved search was stored before field information was persisted.
 */
function getCategoryDefaultFieldNames(category) {
    if (!category) return [];
    const fields = typeof getCategoryFields === 'function' ? getCategoryFields(category) : {};
    const names = [];
    if (fields.name) names.push(Array.isArray(fields.name) ? fields.name.join(', ') : 'Name');
    if (fields.ref) names.push(Array.isArray(fields.ref) ? fields.ref.join(', ') : 'Ref');
    if (fields.longName) names.push(Array.isArray(fields.longName) ? fields.longName.join(', ') : 'Long Name');
    if (fields.shortName) names.push(Array.isArray(fields.shortName) ? fields.shortName.join(', ') : 'Short Name');
    if (fields.title) names.push(Array.isArray(fields.title) ? fields.title.join(', ') : 'Title');
    return names;
}

/**
 * Initialize saved searches functionality
 */
function initSavedSearches() {
    // Load saved searches on page load
    loadSavedSearches();

    // Load recent views
    loadRecentViews();

    // Hide Save search button for web users (only Admin/Super Admin can save)
    applySaveSearchRestrictions();
}

/**
 * If current user is not allowed to save (not logged in or web user), hide the Save search button.
 * Only Admin and Super Admin can create saved searches.
 */
async function applySaveSearchRestrictions() {
    try {
        const response = await fetch('/api/me', { method: 'GET', credentials: 'include' });
        if (!response.ok) {
            document.querySelectorAll('.save-btn').forEach(function (btn) {
                btn.style.display = 'none';
            });
            return;
        }
        const userData = await response.json();
        const role = userData.role || '';
        const R = typeof RoleUtils !== 'undefined' ? RoleUtils : null;
        const isSuperAdmin = R ? R.isSuperAdminRole(role) : (function () {
            const k = String(role).trim().toLowerCase().replace(/[\s\-_]+/g, '');
            return k === 'superadmin' || k === 'suberadmin';
        }());
        const isAdmin = R ? R.isAdminOrSuperAdminRole(role) : (isSuperAdmin || String(role).trim().toLowerCase().replace(/[\s\-_]+/g, '') === 'admin');
        const isWebUser = !isAdmin;
        if (isWebUser) {
            document.querySelectorAll('.save-btn').forEach(function (btn) {
                btn.style.display = 'none';
            });
        }
    } catch (e) {
        document.querySelectorAll('.save-btn').forEach(function (btn) {
            btn.style.display = 'none';
        });
    }
}

/**
 * Initialize history button with dropdown
 */
function initHistoryButton() {
    const historyBtn = document.querySelector('.history-btn');
    if (!historyBtn) return;

    const historyWrap = historyBtn.closest('.history-dropdown');
    // Only the panel inside .history-dropdown — not My Searches (also had .history-dropdown-content before)
    let dropdown = historyWrap ? historyWrap.querySelector('.history-dropdown-content') : null;

    // Initialize My Searches dropdown (after resolving History panel so IDs never clash)
    initMySearchesDropdown();

    // Create History dropdown container if it doesn't exist
    if (!dropdown) {
        dropdown = document.createElement('div');
        dropdown.className = 'history-dropdown-content history-dropdown-panel';
        dropdown.style.display = 'none';
        dropdown.setAttribute('role', 'menu');
        dropdown.setAttribute('aria-label', i18nSearch('search.recentHistoryTitle', 'Recent searches'));

        const header = document.createElement('div');
        header.className = 'history-dropdown-header';
        header.setAttribute('data-i18n', 'search.recentHistoryTitle');
        header.textContent = i18nSearch('search.recentHistoryTitle', 'Recent searches');

        const container = document.createElement('div');
        container.id = 'recent-views-container';
        container.className = 'recent-views-inner';

        dropdown.appendChild(header);
        dropdown.appendChild(container);

        if (historyWrap) {
            historyWrap.style.position = 'relative';
            historyWrap.appendChild(dropdown);
        }

        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(dropdown);
        }
    }

    // Toggle dropdown on click
    historyBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const isVisible = dropdown.style.display !== 'none';

        // Close all other dropdowns
        document.querySelectorAll('.history-dropdown-content, .my-searches-dropdown-content').forEach(dd => {
            if (dd !== dropdown) dd.style.display = 'none';
        });

        if (!isVisible && typeof closeFilterPanel === 'function') {
            closeFilterPanel();
        }

        dropdown.style.display = isVisible ? 'none' : 'flex';

        // Reload recent views when opening
        if (!isVisible) {
            loadRecentViews();
        }
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (!historyBtn.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });
}

/**
 * Load all saved searches for current user
 */
async function loadSavedSearches() {
    const container = document.getElementById('saved-searches-container');
    if (!container) return; // Container doesn't exist on this page

    try {
        const response = await fetch('/api/search', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            // If 401, user not logged in - just return silently
            if (response.status === 401) {
                if (container) {
                    container.innerHTML = '<p style="padding: 16px; text-align: center; color: var(--text-secondary);">Please log in to view saved searches</p>';
                }
                return;
            }

            // For other errors, log but don't break
            console.warn('Failed to load saved searches:', response.status);
            return;
        }

        const result = await response.json();
        if (result.success && result.data) {
            savedSearchesState.cache = result.data;
            renderSavedSearches(result.data);
        }
    } catch (error) {
        // Silently handle errors - don't break the UI
        // Only log if it's not an authorization error
        if (!error.message || (!error.message.includes('غير مصرح') && !error.message.includes('unauthorized'))) {
            console.warn('Error loading saved searches:', error.message);
        }
    }
}

/**
 * Load recent views (last 10)
 */
async function loadRecentViews() {
    const container = document.getElementById('recent-views-container');
    if (!container) return;

    try {
        const response = await fetch('/api/search/recent', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            // If 401 (unauthorized), user not logged in - show empty or message
            if (response.status === 401) {
                renderRecentViews([]);
                return;
            }

            // If 404 or empty, just show empty list
            if (response.status === 404) {
                renderRecentViews([]);
                return;
            }

            // Try to get error message
            let errorMessage = 'Failed to load recent views';
            try {
                const errorData = await response.json();
                errorMessage = errorData.error || errorMessage;
            } catch (e) {
                // Ignore JSON parse error
            }

            // For other errors, just show empty list silently
            console.warn('Error loading recent views:', errorMessage);
            renderRecentViews([]);
            return;
        }

        const result = await response.json();
        if (result.success) {
            // Handle both array and object with data property
            const data = Array.isArray(result.data) ? result.data : (result.data || []);
            renderRecentViews(data);
        } else {
            // If not successful but no error, show empty
            renderRecentViews([]);
        }
    } catch (error) {
        // Silently handle errors - don't break the UI
        // Only log if it's not a 401 (unauthorized) error
        if (!error.message || !error.message.includes('غير مصرح')) {
            console.warn('Error loading recent views:', error.message);
        }
        renderRecentViews([]);
    }
}

/**
 * Render saved searches list
 */
function renderSavedSearches(searches) {
    const container = document.getElementById('saved-searches-container');
    if (!container) return;

    let html = '<div class="saved-searches-list">';

    searches.forEach(search => {
        const accessType = search.access_type || 'owner';
        const hitcount = search.hitcount || 0;
        const isPublic = search.is_public || false;
        const isEditing = savedSearchesState.editingSearchId === search.id;

        html += `
            <div class="saved-search-item" data-search-id="${search.id}">
                <div class="search-header">
                    ${isEditing 
                        ? `<input type="text" class="search-name-input" id="edit-name-${search.id}" value="${escapeHtml(search.name || 'Untitled')}">`
                        : `<h4 class="search-name" style="cursor: pointer; user-select: none;" onclick="editSavedSearch(${search.id})" title="Click to edit name">${escapeHtml(search.name || 'Untitled')}</h4>`
                    }
                    <div class="search-meta">
                        <span class="search-hitcount">Views: ${hitcount}</span>
                        <span class="search-access">${accessType}</span>
                        ${isPublic ? '<span class="badge badge-public">Public</span>' : ''}
                    </div>
                </div>
                ${search.description ? `<p class="search-description">${escapeHtml(search.description)}</p>` : ''}
                <div class="search-actions">
                    ${isEditing
                        ? `
                            <button class="btn btn-sm btn-success" onclick="saveSearchName(${search.id})">Save</button>
                            <button class="btn btn-sm btn-secondary" onclick="cancelEditSearch()">Cancel</button>
                        `
                        : `
                            <button class="btn btn-sm btn-primary" onclick="runSavedSearch(${search.id})">Run</button>
                            <button class="btn btn-sm btn-secondary" onclick="editSavedSearch(${search.id})">Edit</button>
                            <button class="btn btn-sm btn-info" onclick="shareSavedSearch(${search.id})">Share</button>
                            <button class="btn btn-sm btn-danger" onclick="deleteSavedSearch(${search.id})">Delete</button>
                        `
                    }
                </div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;

    // Add event listeners for Enter/Escape keys when in edit mode
    if (savedSearchesState.editingSearchId) {
        const nameInput = document.getElementById(`edit-name-${savedSearchesState.editingSearchId}`);
        if (nameInput) {
            nameInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    saveSearchName(savedSearchesState.editingSearchId);
                } else if (e.key === 'Escape') {
                    e.preventDefault();
                    cancelEditSearch();
                }
            });
        }
    }
}

/**
 * Render recent views
 */
function renderRecentViews(searches) {
    const container = document.getElementById('recent-views-container');
    if (!container) return;

    if (!searches || searches.length === 0) {
        container.innerHTML = `<p class="recent-views-empty" data-i18n="search.noRecentSearches">${escapeHtml(i18nSearch('search.noRecentSearches', 'No recent searches'))}</p>`;
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(container);
        }
        return;
    }

    let html = '<div class="recent-views-list" role="list">';

    searches.forEach(search => {
        const lastVisited = search.last_visited ? formatDate(search.last_visited) : 'Unknown';

        html += `
            <button type="button" class="recent-view-item" role="listitem" onclick="runSavedSearch(${search.id})">
                <span class="recent-view-icon" aria-hidden="true"><i class="fas fa-search"></i></span>
                <span class="recent-view-body">
                    <span class="view-name">${escapeHtml(search.name || 'Untitled')}</span>
                    <span class="view-date">${escapeHtml(lastVisited)}</span>
                </span>
            </button>
        `;
    });

    html += '</div>';
    container.innerHTML = html;
}

/**
 * Run a saved search
 */
async function runSavedSearch(searchId) {
    try {
        // Show full page loading indicator
        if (typeof showLoading === 'function') {
            showLoading('runSavedSearch');
        }
        
        const response = await fetch(`/api/search/${searchId}/run`, {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            let errorKey = 'error.savedSearch.runFailed';
            try {
                const errBody = await response.json();
                const serverCode = errBody.error || '';
                if (response.status === 403 || serverCode === 'error.savedSearch.noAccess') {
                    errorKey = 'error.savedSearch.noAccess';
                } else if (response.status === 404) {
                    errorKey = 'error.savedSearch.notFound';
                }
            } catch (_) { /* ignore parse errors */ }
            throw new Error(errorKey);
        }

        const result = await response.json();
        if (result.success && result.data) {
            const conditionDefinition = result.data.conditionDefinition;

            // Update URL with searchId parameter
            if (window.history && window.history.pushState) {
                const url = new URL(window.location);
                url.searchParams.set('searchId', searchId);
                window.history.pushState({ searchId: searchId }, '', url.toString());
            }

            // Execute the search based on the definition format
            if (conditionDefinition && conditionDefinition.searchGroups) {
                // New format: searchGroups
                await executeSearchGroups(conditionDefinition);
            } else {
                // Legacy format: conditions array
                await executeLegacySearch(conditionDefinition);
            }
        }
    } catch (error) {
        console.error('Error running saved search:', error);
        const i18nKey = error.message && error.message.startsWith('error.') ? error.message : 'error.savedSearch.runFailed';
        const msg = (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t(i18nKey) : error.message;
        if (typeof window.showNotification === 'function') {
            window.showNotification(msg, 'error');
        } else if (typeof window.showAdminNotification === 'function') {
            window.showAdminNotification(msg, 'error');
        } else {
            alert(msg);
        }
    } finally {
        // Hide full page loading indicator
        // Note: Loading is also managed by executeSearchGroups/executeLegacySearch,
        // but we ensure cleanup here in case of early errors
        if (typeof hideLoading === 'function') {
            hideLoading('runSavedSearch');
        }
    }
}

/**
 * Execute search with searchGroups format
 */
async function executeSearchGroups(definition) {
    const searchGroups = definition.searchGroups || [];

    if (searchGroups.length === 0) {
        console.warn('No search groups found in definition');
        return;
    }

    // Convert searchGroups to searchConditions format
    const convertedConditions = [];

    // Track reasons for skipping conditions (for debugging)
    const skipReasons = {
        inactiveGroups: 0,
        noSearches: 0,
        inactiveSearches: 0,
        invalidFacetId: 0,
        noFilterGroups: 0,
        noQueries: 0
    };

    for (const group of searchGroups) {
        if (!group.active) {
            skipReasons.inactiveGroups++;
            console.debug('Skipping inactive group:', group);
            continue;
        }

        // Handle both nested format (group.searches[]) and flat format (group.facetId directly)
        const searches = group.searches || [];

        // If group has searches array, use nested format
        if (searches.length > 0) {
            for (const search of searches) {
                if (!search.active) {
                    skipReasons.inactiveSearches++;
                    continue;
                }

                const facetId = search.facetId;
                const category = facetIdToCategory(facetId);

                if (!category) {
                    console.warn('Could not convert facetId to category:', facetId, 'search:', search);
                    skipReasons.invalidFacetId++;
                    continue;
                }

                // Process filterGroups to build query
                const filterGroups = search.filterGroups || [];

                if (filterGroups.length === 0) {
                    console.warn('No filterGroups found for search:', search);
                    skipReasons.noFilterGroups++;
                    continue;
                }

                // Build conditions from filterGroups
                // Each filterGroup can be a separate condition if it has an operator
                for (let i = 0; i < filterGroups.length; i++) {
                    const filterGroup = filterGroups[i];
                    const query = filterGroup.query || filterGroup.value;
                    
                    if (!query) {
                        console.warn('No query found in filterGroup:', filterGroup);
                        skipReasons.noQueries++;
                        continue;
                    }
                    
                    // Get operator from filterGroup, search, or group
                    let operator;
                    if (filterGroup.operator) {
                        operator = filterGroup.operator.toUpperCase();
                    } else if (i === 0) {
                        operator = search.operator || group.operator || 'FIND';
                    } else {
                        operator = 'AND'; // Default for subsequent conditions
                    }
                    
                    // Normalize operator: START -> FIND
                    if (operator === 'START') {
                        operator = 'FIND';
                    }

                    // Restore persisted fields, searchFields and filters; fall back to category defaults
                    const savedFields = Array.isArray(filterGroup.fields) && filterGroup.fields.length > 0
                        ? filterGroup.fields
                        : getCategoryDefaultFieldNames(category);

                    const condition = {
                        id: Date.now() + Math.random() + i,
                        operator: operator,
                        category: category,
                        query: query,
                        fields: savedFields,
                        searchFields: filterGroup.searchFields || null,
                        filters: filterGroup.filters || {},
                        muted: false
                    };

                    convertedConditions.push(condition);
                }
                
                if (filterGroups.length === 0) {
                    console.warn('No filterGroups found for search:', search);
                    skipReasons.noFilterGroups++;
                }
            }
        }
        // Handle flat format: facetId and filterGroups directly on the group
        else if (group.facetId) {
            const facetId = group.facetId;
            const category = facetIdToCategory(facetId);

            if (!category) {
                console.warn('Could not convert facetId to category:', facetId, 'group:', group);
                skipReasons.invalidFacetId++;
                continue;
            }

            // Process filterGroups to build query
            const filterGroups = group.filterGroups || [];

            if (filterGroups.length === 0) {
                console.warn('No filterGroups found for group:', group);
                skipReasons.noFilterGroups++;
                continue;
            }

            // Build conditions from filterGroups
            // Each filterGroup can be a separate condition if it has an operator
            for (let i = 0; i < filterGroups.length; i++) {
                const filterGroup = filterGroups[i];
                const query = filterGroup.query || filterGroup.value;
                
                if (!query) {
                    console.warn('No query found in filterGroup:', filterGroup);
                    skipReasons.noQueries++;
                    continue;
                }
                
                // Get operator from filterGroup or group
                // First condition should be FIND (or whatever is stored)
                // Subsequent conditions use their own operators
                let operator;
                if (filterGroup.operator) {
                    operator = filterGroup.operator.toUpperCase();
                } else if (i === 0) {
                    operator = group.operator || 'FIND';
                } else {
                    operator = 'AND'; // Default for subsequent conditions
                }
                
                // Normalize operator: START -> FIND
                if (operator === 'START') {
                    operator = 'FIND';
                }

                // Restore persisted fields, searchFields and filters; fall back to category defaults
                const savedFields = Array.isArray(filterGroup.fields) && filterGroup.fields.length > 0
                    ? filterGroup.fields
                    : getCategoryDefaultFieldNames(category);

                const condition = {
                    id: Date.now() + Math.random() + i,
                    operator: operator,
                    category: category,
                    query: query,
                    fields: savedFields,
                    searchFields: filterGroup.searchFields || null,
                    filters: filterGroup.filters || {},
                    muted: false
                };

                convertedConditions.push(condition);
            }
            
            if (filterGroups.length === 0) {
                console.warn('No filterGroups found for group:', group);
                skipReasons.noFilterGroups++;
            }
        } else {
            // Group has no searches array and no facetId - skip it
            skipReasons.noSearches++;
            console.warn('Group has no searches array and no facetId:', {
                group: group,
                hasSearchesProperty: 'searches' in group,
                hasFacetIdProperty: 'facetId' in group
            });
            continue;
        }
    }

    if (convertedConditions.length === 0) {
        console.warn('No valid conditions found in searchGroups', {
            totalGroups: searchGroups.length,
            skipReasons: skipReasons,
            definition: definition
        });
        return;
    }

    // Execute the search
    // Use the first category as the target category
    const targetCategory = convertedConditions[0].category;

    // Validate targetCategory before proceeding
    if (!targetCategory) {
        console.error('No valid target category found in converted conditions', {
            convertedConditions: convertedConditions,
            definition: definition
        });
        alert('Error: Could not determine search category. Please check the saved search configuration.');
        return;
    }

    console.log('Executing saved search with category:', targetCategory, 'and conditions:', convertedConditions);

    // Clear existing search conditions ONLY after validation
    // searchConditions is defined in search-input.js as a let variable
    // We need to access it directly (it should be in the same scope if scripts are loaded in order)
    // If not available, try window or create it
    try {
        if (typeof searchConditions !== 'undefined') {
            // Direct access (if in same scope)
            searchConditions.length = 0;
            convertedConditions.forEach(condition => {
                searchConditions.push(condition);
            });
        } else if (typeof window !== 'undefined' && window.searchConditions) {
            // Access via window
            window.searchConditions.length = 0;
            convertedConditions.forEach(condition => {
                window.searchConditions.push(condition);
            });
        } else {
            // Create on window as fallback
            if (typeof window !== 'undefined') {
                window.searchConditions = convertedConditions.slice();
            }
        }
    } catch (e) {
        console.error('Error accessing searchConditions:', e);
        // Fallback: create on window
        if (typeof window !== 'undefined') {
            window.searchConditions = convertedConditions.slice();
        }
    }

    // Update UI to show query builder with conditions
    if (typeof renderSearchConditions === 'function') {
        renderSearchConditions();
    }
    
    if (typeof updateSearchCounter === 'function') {
        updateSearchCounter();
    }
    
    // Switch to the target category before executing search
    // This ensures the UI is in the correct state
    console.log('[SAVED SEARCH] Switching to target category:', targetCategory);
    const categoryItems = document.querySelectorAll('.category-item');
    categoryItems.forEach(item => {
        if (item.getAttribute('data-category') === targetCategory) {
            item.classList.add('active', 'current');
        } else {
            item.classList.remove('active', 'current');
        }
    });
    
    // Show the query builder container
    const builderContainer = document.getElementById('queryBuilderContainer');
    if (builderContainer) {
        builderContainer.style.display = 'block';
    }
    
    // IMPORTANT: ALWAYS use executeMultiConditionSearch() for saved searches
    // This is critical because:
    // 1. It triggers Unison Search API when there are multiple facets
    // 2. It calls updateAllFacets() which updates counts/filters on ALL facets
    // 3. executeSearchWithConditions() only updates the single active facet
    // Without this, saved searches will only show filtered data in the active facet,
    // but other facets will remain unfiltered (the bug user is reporting)
    console.log('[SAVED SEARCH] Executing search with', convertedConditions.length, 'conditions');
    console.log('[SAVED SEARCH] Conditions:', convertedConditions);
    
    if (typeof executeMultiConditionSearch === 'function') {
        await executeMultiConditionSearch();
    } else if (typeof executeSearchWithConditions === 'function') {
        // Fallback (should not happen in normal flow)
        console.warn('[SAVED SEARCH] executeMultiConditionSearch not available, falling back to executeSearchWithConditions');
        console.warn('[SAVED SEARCH] WARNING: This may not filter all facets correctly!');
        await executeSearchWithConditions(targetCategory, convertedConditions);
    } else {
        console.error('[SAVED SEARCH] No search execution function found');
        alert('Error: Could not execute search. Please try again.');
    }
}

/**
 * Execute legacy search format
 */
async function executeLegacySearch(definition) {
    // Handle legacy conditions array format
    // If definition has conditions array, use it directly
    if (definition && Array.isArray(definition.conditions)) {
        // Clear existing search conditions
        if (typeof searchConditions !== 'undefined') {
            searchConditions.length = 0;
        } else if (typeof window !== 'undefined') {
            window.searchConditions = [];
        }

        // Add legacy conditions
        definition.conditions.forEach(condition => {
            if (typeof searchConditions !== 'undefined') {
                searchConditions.push(condition);
            } else if (typeof window !== 'undefined' && window.searchConditions) {
                window.searchConditions.push(condition);
            }
        });

        // Execute the search
        const targetCategory = definition.conditions[0]?.category;

        // Update UI to show query builder with conditions
        if (typeof renderSearchConditions === 'function') {
            renderSearchConditions();
        }
        
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
        
        // Show the query builder container
        const builderContainer = document.getElementById('queryBuilderContainer');
        if (builderContainer) {
            builderContainer.style.display = 'block';
        }

        if (targetCategory) {
            if (typeof executeSearchWithConditions === 'function') {
                await executeSearchWithConditions(targetCategory, definition.conditions);
            } else if (typeof executeMultiConditionSearch === 'function') {
                await executeMultiConditionSearch();
            }
        } else {
            // Try to get active category
            const activeCategory = typeof getActiveCategoryWithFallback === 'function'
                ? getActiveCategoryWithFallback()
                : null;

            if (activeCategory && typeof executeSearchWithConditions === 'function') {
                await executeSearchWithConditions(activeCategory, definition.conditions);
            } else if (typeof executeMultiConditionSearch === 'function') {
                await executeMultiConditionSearch();
            }
        }
    } else {
        console.warn('Legacy search format not recognized:', definition);
    }
}

/**
 * Save current search as a saved search
 */
async function saveCurrentSearch() {
    showSaveSearchModal();
}

/**
 * Show Save Search modal
 */
function showSaveSearchModal() {
    // Remove existing modal if any
    const existingModal = document.getElementById('save-search-modal');
    if (existingModal) {
        existingModal.remove();
    }

    // Create modal overlay
    const modal = document.createElement('div');
    modal.id = 'save-search-modal';
    modal.className = 'save-search-modal-overlay';
    modal.innerHTML = `
        <div class="save-search-modal-content">
            <div class="save-search-modal-header">
                <h3>Save Search</h3>
                <button class="save-search-modal-close" onclick="closeSaveSearchModal()">&times;</button>
            </div>
            <div class="save-search-modal-body">
                <div class="save-search-form-group">
                    <label for="save-search-name">Name: <span class="required">*</span></label>
                    <input type="text" id="save-search-name" class="save-search-input" placeholder="Enter Search Name" required>
                </div>
                <div class="save-search-form-group">
                    <label for="save-search-description">Description: <span class="required">*</span></label>
                    <textarea id="save-search-description" class="save-search-textarea" placeholder="Enter Search Description" required></textarea>
                </div>
                <div class="save-search-form-group">
                    <label class="save-search-checkbox-label">
                        <input type="checkbox" id="save-search-public" class="save-search-checkbox">
                        <span>Share with Public</span>
                    </label>
                </div>
            </div>
            <div class="save-search-modal-footer">
                <button class="save-search-btn-cancel" onclick="closeSaveSearchModal()">Cancel</button>
                <button class="save-search-btn-save" onclick="handleSaveSearch()">Save</button>
            </div>
        </div>
    `;

    document.body.appendChild(modal);

    // Focus on name input
    setTimeout(() => {
        const nameInput = document.getElementById('save-search-name');
        if (nameInput) nameInput.focus();
    }, 100);

    // Close on overlay click
    modal.addEventListener('click', (e) => {
        if (e.target === modal) {
            closeSaveSearchModal();
        }
    });

    // Close on Escape key
    document.addEventListener('keydown', function escapeHandler(e) {
        if (e.key === 'Escape') {
            closeSaveSearchModal();
            document.removeEventListener('keydown', escapeHandler);
        }
    });
}

/**
 * Close Save Search modal
 */
function closeSaveSearchModal() {
    const modal = document.getElementById('save-search-modal');
    if (modal) {
        modal.remove();
    }
}

/**
 * Handle Save Search button click
 */
async function handleSaveSearch() {
    const nameInput = document.getElementById('save-search-name');
    const descriptionInput = document.getElementById('save-search-description');
    const publicCheckbox = document.getElementById('save-search-public');

    if (!nameInput || !descriptionInput) return;

    const name = nameInput.value.trim();
    const description = descriptionInput.value.trim();
    const isPublic = publicCheckbox ? publicCheckbox.checked : false;

    // Validation
    if (!name) {
        alert('Please enter a search name');
        nameInput.focus();
        return;
    }

    if (!description) {
        alert('Please enter a search description');
        descriptionInput.focus();
        return;
    }

    // Build search definition from current search state
    const searchDefinition = buildSearchDefinitionFromCurrentState(name);

    console.log('[SAVE] Saving search with definition:', JSON.stringify(searchDefinition, null, 2));

    try {
        const requestBody = {
            name: name,
            description: description,
            conditionDefinition: searchDefinition,
            isPublic: isPublic
        };

        console.log('[SAVE] Request body:', JSON.stringify(requestBody, null, 2));

        // Use /api/search/ with trailing slash to match /api/search/* pattern
        // This avoids conflict with SearchServlet which uses exact /api/search pattern
        const response = await fetch('/api/search/', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(requestBody)
        });

        console.log('[SAVE] Response status:', response.status, response.statusText);

        if (!response.ok) {
            const errorText = await response.text();
            console.error('[SAVE] Error response:', errorText);
            let errorData = {};
            try {
                errorData = JSON.parse(errorText);
            } catch (e) {
                console.error('[SAVE] Failed to parse error response as JSON');
            }
            throw new Error(errorData.error || errorData.message || 'Failed to save search');
        }

        const result = await response.json();
        console.log('[SAVE] Response result:', result);
        if (result.success) {
            closeSaveSearchModal();
            alert('Search saved successfully');
            await loadSavedSearches();
        } else {
            throw new Error(result.error || result.message || 'Failed to save search');
        }
    } catch (error) {
        console.error('Error saving search:', error);
        alert('Error saving search: ' + error.message);
    }
}

/**
 * Build search definition from current search state
 * This should capture the current search conditions, facets, etc.
 */
function buildSearchDefinitionFromCurrentState(name) {
    // Get current user ID from session
    const userId = getCurrentUserId();

    // Build searchGroups from current searchConditions
    const searchGroups = [];

    // Group conditions by category/facet
    const conditionsByCategory = {};
    if (typeof searchConditions !== 'undefined' && Array.isArray(searchConditions)) {
        searchConditions.forEach(condition => {
            const category = condition.category || condition.module;
            if (!conditionsByCategory[category]) {
                conditionsByCategory[category] = [];
            }
            conditionsByCategory[category].push(condition);
        });
    }

    let firstGroup = true;
    for (const [category, conditions] of Object.entries(conditionsByCategory)) {
        const facetId = categoryToFacetId(category);

        // Build filterGroups from conditions
        // Group conditions by operator to build proper filterGroups
        const filterGroups = [];

        conditions.forEach((condition, idx) => {
            // For each condition, create a filterGroup
            const filterGroup = {
                fields: condition.fields || [],
                searchFields: condition.searchFields || null,
                filters: condition.filters || {},
                condition: 'contains',
                value: condition.query || '',
                query: condition.query || ''
            };
            
            // Store operator in filterGroup for loading later
            if (idx === 0) {
                filterGroup.operator = condition.operator || 'FIND';
            } else {
                filterGroup.operator = condition.operator;
            }
            
            filterGroups.push(filterGroup);
        });

        // Use flat format: facetId and filterGroups directly on the group (not nested in searches)
        // Get the operator from the first condition, NOT from firstGroup logic
        const firstConditionOperator = conditions[0]?.operator || 'FIND';
        
        const searchGroup = {
            operator: firstConditionOperator.toUpperCase(), // Use actual operator, not START
            active: true,
            facetId: facetId,
            filterGroups: filterGroups
        };

        searchGroups.push(searchGroup);
        firstGroup = false;
    }

    console.log('[SAVE] Building search definition:', {
        name: name,
        userId: userId,
        searchGroups: searchGroups
    });

    return {
        userRef: String(userId),
        name: name,
        searchGroups: searchGroups,
        public: false
    };
}

/**
 * Share a saved search
 */
async function shareSavedSearch(searchId) {
    const search = savedSearchesState.cache?.find(s => s.id === searchId);
    if (!search) return;

    // Show sharing dialog
    const shareType = prompt('Share type:\n1. Public (everyone can see)\n2. Limited (specific users)\nEnter 1 or 2:');

    if (shareType === '1') {
        // Share publicly
        await updateSearchSharing(searchId, true, []);
    } else if (shareType === '2') {
        // Share with specific users
        const userIdsInput = prompt('Enter user IDs (comma-separated):');
        if (userIdsInput) {
            const userIds = userIdsInput.split(',').map(id => parseInt(id.trim())).filter(id => !isNaN(id));
            await updateSearchSharing(searchId, false, userIds);
        }
    }
}

/**
 * Update search sharing settings
 */
async function updateSearchSharing(searchId, isPublic, userIds) {
    try {
        const response = await fetch(`/api/search/${searchId}/share`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                isPublic: isPublic,
                userIds: userIds
            })
        });

        if (!response.ok) {
            throw new Error('Failed to update sharing');
        }

        const result = await response.json();
        if (result.success) {
            alert('Sharing updated successfully');
            await loadSavedSearches();
        }
    } catch (error) {
        console.error('Error updating sharing:', error);
        alert('Error updating sharing: ' + error.message);
    }
}

/**
 * Edit a saved search
 */
function editSavedSearch(searchId) {
    // Cancel any existing edit
    if (savedSearchesState.editingSearchId && savedSearchesState.editingSearchId !== searchId) {
        cancelEditSearch();
    }

    savedSearchesState.editingSearchId = searchId;
    
    // Re-render the list to show edit mode
    if (savedSearchesState.cache) {
        renderSavedSearches(savedSearchesState.cache);
    }

    // Focus on the name input field
    setTimeout(() => {
        const nameInput = document.getElementById(`edit-name-${searchId}`);
        if (nameInput) {
            nameInput.focus();
            nameInput.select();
        }
    }, 100);
}

/**
 * Save search name
 */
async function saveSearchName(searchId) {
    const nameInput = document.getElementById(`edit-name-${searchId}`);
    if (!nameInput) return;

    const newName = nameInput.value.trim();
    
    // Validation
    if (!newName) {
        alert('Please enter a search name');
        nameInput.focus();
        return;
    }

    const search = savedSearchesState.cache?.find(s => s.id === searchId);
    if (!search) {
        console.error('Search not found:', searchId);
        cancelEditSearch();
        return;
    }

    try {
        // Parse conditionDefinition if it's a string
        let conditionDefinition = search.condition_definition;
        if (typeof conditionDefinition === 'string') {
            try {
                conditionDefinition = JSON.parse(conditionDefinition);
            } catch (e) {
                console.warn('Failed to parse conditionDefinition:', e);
            }
        }

        const response = await fetch(`/api/search/${searchId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({
                name: newName,
                description: search.description || '',
                conditionDefinition: conditionDefinition,
                isPublic: search.is_public || false
            })
        });

        if (!response.ok) {
            const errorData = await response.json().catch(() => ({}));
            throw new Error(errorData.error || 'Failed to save search');
        }

        const result = await response.json();
        if (result.success) {
            // Update cache
            if (search) {
                search.name = newName;
            }
            
            // Exit edit mode
            cancelEditSearch();
            
            // Reload to refresh
            await loadSavedSearches();
        } else {
            throw new Error(result.error || 'Failed to save search');
        }
    } catch (error) {
        console.error('Error saving search name:', error);
        alert('Error saving search: ' + error.message);
    }
}

/**
 * Cancel edit search
 */
function cancelEditSearch() {
    savedSearchesState.editingSearchId = null;
    
    // Re-render the list to exit edit mode
    if (savedSearchesState.cache) {
        renderSavedSearches(savedSearchesState.cache);
    }
}

/**
 * Delete a saved search
 */
async function deleteSavedSearch(searchId) {
    if (!confirm('Are you sure you want to delete this search?')) {
        return;
    }

    try {
        const response = await fetch(`/api/search/${searchId}`, {
            method: 'DELETE',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to delete search');
        }

        const result = await response.json();
        if (result.success) {
            alert('Search deleted successfully');
            await loadSavedSearches();
        }
    } catch (error) {
        console.error('Error deleting search:', error);
        alert('Error deleting search: ' + error.message);
    }
}

/**
 * Convert facet ID to category slug (matches data-category on .category-item; see FACET_TO_CATEGORY in search-utils.js).
 */
function facetIdToCategory(facetId) {
    if (!facetId) return null;
    const slugFn = typeof facetIdToCategorySlug === 'function'
        ? facetIdToCategorySlug
        : (typeof window !== 'undefined' && typeof window.facetIdToCategorySlug === 'function'
            ? window.facetIdToCategorySlug
            : null);
    if (typeof slugFn === 'function') {
        const slug = slugFn(facetId);
        if (slug) return slug;
    }
    return facetId.toString().toLowerCase().replace(/\s+/g, '-').replace(/_/g, '-');
}

/**
 * Convert category to facet ID
 */
function categoryToFacetId(category) {
    if (!category) return null;

    // ✅ Use categorySlugToFacetId from search-utils.js for consistency
    if (typeof categorySlugToFacetId === 'function') {
        const facetId = categorySlugToFacetId(category);
        if (facetId) {
            return facetId;
        }
    } else if (typeof window !== 'undefined' && typeof window.categorySlugToFacetId === 'function') {
        const facetId = window.categorySlugToFacetId(category);
        if (facetId) {
            return facetId;
        }
    }

    // Fallback to old mapping (for backward compatibility)
    const normalized = category.toString()
        .toLowerCase()
        .trim()
        .replace(/\s+/g, '-');

    const mapping = {
        'dataset': 'DATASET',
        'datasets': 'DATASET',
        'data-set': 'DATASET',
        'data-sets': 'DATASET',
        'attribute': 'ATTRIBUTE',
        'attributes': 'ATTRIBUTE',
        'system': 'SYSTEM',
        'glossary': 'GLOSSARY',
        'dataquality': 'DATAQUALITY',
        'people': 'PEOPLE',
        'role': 'ROLE',
        'business-area': 'BUSINESS_AREA',
        'legal-entity': 'LEGAL_ENTITY',
        'client': 'CLIENT',
        'committee': 'COMMITTEE',
        'policy': 'POLICY',
        'process': 'PROCESS',
        'interface': 'INTERFACE',
        'capability': 'CAPABILITY',
        'product': 'PRODUCT',
        'orgunit': 'ORG_UNIT',
        'org-unit': 'ORG_UNIT',
        'geography': 'GEOGRAPHY',
        'regulation': 'REGULATION',
        'regulator': 'REGULATOR',
        'regulatory-theme': 'REGULATORY_THEME',
        'active-tasks': 'ACTIVE_TASKS',
        'activetasks': 'ACTIVE_TASKS',
        'change-requests': 'CHANGE_REQUESTS',
        'changerequests': 'CHANGE_REQUESTS'
    };
    
    const result = mapping[normalized];
    if (result) {
        return result;
    }
    
    // ✅ No fallback - return null if no mapping exists (strict mapping)
    console.warn(`[categoryToFacetId] No mapping found for category: ${category} (normalized: ${normalized})`);
    return null;
}

/**
 * Get current user ID from session
 */
function getCurrentUserId() {
    // TODO: Get from session or global variable
    // This is a placeholder
    return 1;
}

/**
 * Initialize My Searches dropdown
 */
function initMySearchesDropdown() {
    const historyDropdown = document.querySelector('.history-dropdown');
    if (!historyDropdown) return;

    // Create My Searches button
    const mySearchesBtn = document.createElement('button');
    mySearchesBtn.className = 'my-searches-btn';
    const t = (key, fallback) =>
        (window.I18n && typeof window.I18n.t === 'function') ? window.I18n.t(key) : fallback;
    const mySearchesText = t('label.mySearches', 'My searches');
    mySearchesBtn.innerHTML = `<i class="fas fa-bookmark"></i> ${mySearchesText} <i class="fas fa-chevron-down"></i>`;

    // Panel chrome matches History via CSS (.history-dropdown-panel on .my-searches-dropdown-content)
    const dropdown = document.createElement('div');
    dropdown.className = 'my-searches-dropdown-content history-dropdown-panel';
    dropdown.style.display = 'none';
    dropdown.setAttribute('role', 'menu');
    dropdown.setAttribute('aria-label', t('label.mySearches', 'My searches'));

    const header = document.createElement('div');
    header.className = 'history-dropdown-header';
    header.setAttribute('data-i18n', 'label.mySearches');
    header.textContent = t('label.mySearches', 'My searches');

    const container = document.createElement('div');
    container.id = 'my-searches-dropdown-container';
    container.className = 'recent-views-inner';

    const manageFooter = document.createElement('div');
    manageFooter.className = 'my-searches-dropdown-footer';
    const manageLink = document.createElement('button');
    manageLink.type = 'button';
    manageLink.className = 'my-searches-manage-link';
    manageLink.innerHTML = `<i class="fas fa-cog" aria-hidden="true"></i> <span data-i18n="search.manageSearchesLink">${escapeHtml(t('search.manageSearchesLink', 'Manage searches'))}</span>`;
    manageLink.addEventListener('click', () => {
        window.location.href = '/manage-searches.html';
    });
    manageFooter.appendChild(manageLink);

    dropdown.appendChild(header);
    dropdown.appendChild(container);
    dropdown.appendChild(manageFooter);

    if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
        window.I18n.applyTranslations(dropdown);
    }

    // Wrap button and dropdown in container
    const wrapper = document.createElement('div');
    wrapper.className = 'my-searches-dropdown';
    wrapper.style.position = 'relative';
    wrapper.style.display = 'inline-block';
    wrapper.appendChild(mySearchesBtn);
    wrapper.appendChild(dropdown);

    // Insert after history dropdown
    historyDropdown.parentNode.insertBefore(wrapper, historyDropdown.nextSibling);

    // Toggle dropdown on click
    mySearchesBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const isVisible = dropdown.style.display !== 'none';

        // Close all other dropdowns
        document.querySelectorAll('.history-dropdown-content, .my-searches-dropdown-content').forEach(dd => {
            if (dd !== dropdown) dd.style.display = 'none';
        });

        if (!isVisible && typeof closeFilterPanel === 'function') {
            closeFilterPanel();
        }

        dropdown.style.display = isVisible ? 'none' : 'flex';

        // Load searches when opening
        if (!isVisible) {
            loadMySearchesForDropdown();
        }
    });

    // Close dropdown when clicking outside
    document.addEventListener('click', (e) => {
        if (!mySearchesBtn.contains(e.target) && !dropdown.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });

    // Listen for language changes and update button text
    window.addEventListener('languageChanged', function(e) {
        const mySearchesText = t('label.mySearches', 'My searches');
        mySearchesBtn.innerHTML = `<i class="fas fa-bookmark"></i> ${mySearchesText} <i class="fas fa-chevron-down"></i>`;
        manageLink.innerHTML = `<i class="fas fa-cog" aria-hidden="true"></i> <span data-i18n="search.manageSearchesLink">${escapeHtml(t('search.manageSearchesLink', 'Manage searches'))}</span>`;
        header.setAttribute('data-i18n', 'label.mySearches');
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(dropdown);
        }
    });
}

/**
 * Load my searches for dropdown
 */
async function loadMySearchesForDropdown() {
    const container = document.getElementById('my-searches-dropdown-container');
    if (!container) return;

    try {
        const response = await fetch('/api/search/my', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            // If 401, user not logged in - show empty
            if (response.status === 401) {
                const msg = escapeHtml(i18nSearch('manageSearches.pleaseLoginMy', 'Please log in to view searches'));
                container.innerHTML = `<p class="recent-views-empty">${msg}</p>`;
                return;
            }

            // For other errors, try to get error message
            let errorMessage = 'Failed to load searches';
            try {
                const errorData = await response.json();
                errorMessage = errorData.error || errorMessage;
            } catch (e) {
                // Ignore JSON parse error
            }

            // Show empty list instead of error
            console.warn('Error loading searches for dropdown:', errorMessage);
            const emptyMsg = escapeHtml(i18nSearch('manageSearches.emptyMy', 'No saved searches'));
            container.innerHTML = `<p class="recent-views-empty">${emptyMsg}</p>`;
            return;
        }

        const result = await response.json();
        if (result.success) {
            // Handle both array and object with data property
            const data = Array.isArray(result.data) ? result.data : (result.data || []);
            const searches = data.slice(0, 10); // Last 10

            if (searches.length === 0) {
                const emptyMsg = escapeHtml(i18nSearch('manageSearches.emptyMy', 'No saved searches'));
                container.innerHTML = `<p class="recent-views-empty">${emptyMsg}</p>`;
                return;
            }

            const untitled = i18nSearch('manageSearches.untitled', 'Untitled');
            let html = '<div class="recent-views-list" role="list">';
            searches.forEach(search => {
                const rawDesc = (search.description || '').trim();
                const descShort = rawDesc.length > 72 ? rawDesc.substring(0, 72) + '…' : rawDesc;
                const secondLine = descShort
                    ? `<span class="view-date">${escapeHtml(descShort)}</span>`
                    : '';
                html += `
                    <button type="button" class="recent-view-item my-searches-dropdown-item" role="listitem" data-search-id="${search.id}">
                        <span class="recent-view-icon" aria-hidden="true"><i class="fas fa-bookmark"></i></span>
                        <span class="recent-view-body">
                            <span class="view-name">${escapeHtml(search.name || untitled)}</span>
                            ${secondLine}
                        </span>
                    </button>
                `;
            });
            html += '</div>';
            container.innerHTML = html;

            setTimeout(() => {
                const searchItems = container.querySelectorAll('.my-searches-dropdown-item');
                searchItems.forEach(item => {
                    item.addEventListener('click', async (e) => {
                        e.stopPropagation();
                        const searchId = parseInt(item.getAttribute('data-search-id'), 10);

                        const panel = document.querySelector('.my-searches-dropdown-content');
                        if (panel) {
                            panel.style.display = 'none';
                        }

                        await runSavedSearch(searchId);
                    });
                });
            }, 0);
        } else {
            // If not successful, show empty
            const emptyMsg = escapeHtml(i18nSearch('manageSearches.emptyMy', 'No saved searches'));
            container.innerHTML = `<p class="recent-views-empty">${emptyMsg}</p>`;
        }
    } catch (error) {
        // Silently handle errors - don't break the UI
        // Only log if it's not an authorization error
        if (!error.message || (!error.message.includes('غير مصرح') && !error.message.includes('unauthorized'))) {
            console.warn('Error loading searches for dropdown:', error.message);
        }
        const emptyMsg = escapeHtml(i18nSearch('manageSearches.emptyMy', 'No saved searches'));
        container.innerHTML = `<p class="recent-views-empty">${emptyMsg}</p>`;
    }
}

/**
 * Initialize Quick Link button from admin configuration.
 * Reads the QUICK_LINK setting saved by the admin panel and injects
 * a one-click button into the search controls bar so users can
 * instantly trigger that saved search without opening any dropdown.
 */
async function initQuickLinkButton() {
    try {
        // Primary endpoint dedicated to admin Quick Links (user_quick_link table).
        // Fallback keeps backward compatibility with older deployments.
        let response = await fetch('/api/quick-link/current', { credentials: 'include' });
        if (!response.ok) {
            response = await fetch('/api/search/quick-link', { credentials: 'include' });
        }
        if (!response.ok) return;

        const data = await response.json();
        if (!data || !data.definition) return;

        let quickLink;
        try {
            quickLink = typeof data.definition === 'string'
                ? JSON.parse(data.definition)
                : data.definition;
        } catch (e) {
            return;
        }

        if (!quickLink || !quickLink.savedSearchId) return;

        // Build the button
        const btn = document.createElement('button');
        btn.className = 'quick-link-btn';
        btn.title = quickLink.description || quickLink.savedSearchName || 'Quick Link';
        btn.setAttribute('data-search-id', quickLink.savedSearchId);
        btn.innerHTML = `<i class="fas fa-bolt"></i> <span>${escapeHtml(quickLink.savedSearchName || 'Quick Link')}</span>`;

        btn.addEventListener('click', async () => {
            const searchId = parseInt(btn.getAttribute('data-search-id'));
            if (searchId) {
                await runSavedSearch(searchId);
            }
        });

        // Insert the button at the end of .search-actions, after "My Searches"
        // .search-actions is the parent of .history-dropdown
        const historyDropdown = document.querySelector('.history-dropdown');
        if (historyDropdown && historyDropdown.parentNode) {
            historyDropdown.parentNode.appendChild(btn);
        }
    } catch (error) {
        // Non-critical – silently ignore if config is unavailable
        console.warn('[QuickLink] Could not load Quick Link configuration:', error.message);
    }
}

/**
 * Format date for display
 */
function formatDate(dateString) {
    if (!dateString) return 'Unknown';

    try {
        const date = new Date(dateString);
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        const diffHours = Math.floor(diffMs / 3600000);
        const diffDays = Math.floor(diffMs / 86400000);

        if (diffMins < 1) return 'Just now';
        if (diffMins < 60) return `${diffMins} minute${diffMins > 1 ? 's' : ''} ago`;
        if (diffHours < 24) return `${diffHours} hour${diffHours > 1 ? 's' : ''} ago`;
        if (diffDays < 7) return `${diffDays} day${diffDays > 1 ? 's' : ''} ago`;

        // Format as date
        return date.toLocaleDateString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return dateString;
    }
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

