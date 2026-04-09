// Related Objects Search Functionality
// Handles "Search Related Objects in Unison" feature and expandable panels

let selectedRows = [];
let relatedSearchDefinition = null;

// ============================================================================
// EXPANDABLE RELATED OBJECTS PANEL
// ============================================================================

/**
 * Check if a specific row has related objects
 */
function hasRelatedObjects(category, objectId) {
    if (!window.unisonRelatedObjects || !category || !objectId) {
        return false;
    }
    
    // Normalize category to facet ID
    const facetId = (typeof categorySlugToFacetId === 'function') ? 
                    categorySlugToFacetId(category) : 
                    category.toUpperCase().replace(/-/g, '_');
    
    // Check if this facet has related objects
    const facetRelated = window.unisonRelatedObjects[facetId];
    return facetRelated && Object.keys(facetRelated).length > 0;
}

/**
 * Toggle related objects panel for a specific row
 */
function toggleRelatedObjectsPanel(row, category, objectId) {
    const existingPanel = row.nextElementSibling;
    
    if (existingPanel && existingPanel.classList.contains('related-objects-panel')) {
        // Collapse
        existingPanel.remove();
        const expandBtn = row.querySelector('.expand-related i');
        if (expandBtn) {
            expandBtn.classList.remove('icon-chevron-up');
            expandBtn.classList.add('icon-chevron-down');
        }
    } else {
        // Expand
        const panel = createRelatedObjectsPanel(category, objectId);
        row.insertAdjacentElement('afterend', panel);
        const expandBtn = row.querySelector('.expand-related i');
        if (expandBtn) {
            expandBtn.classList.remove('icon-chevron-down');
            expandBtn.classList.add('icon-chevron-up');
        }
        
        // Lazy load details if not cached
        loadRelatedObjectsDetails(category, objectId, panel);
    }
}

/**
 * Create related objects panel DOM
 */
function createRelatedObjectsPanel(category, objectId) {
    const facetId = (typeof categorySlugToFacetId === 'function') ? 
                    categorySlugToFacetId(category) : 
                    category.toUpperCase().replace(/-/g, '_');
    
    const relatedData = window.unisonRelatedObjects?.[facetId] || {};
    
    const panel = document.createElement('tr');
    panel.classList.add('related-objects-panel');
    
    const td = document.createElement('td');
    // Use a large colspan to span all columns
    td.setAttribute('colspan', '100');
    
    td.innerHTML = `
        <div class="related-objects-container">
            <h4 class="related-objects-title">Related Objects</h4>
            <div class="related-objects-grid">
                ${renderRelatedObjectsGrid(relatedData)}
            </div>
        </div>
    `;
    
    panel.appendChild(td);
    return panel;
}

/**
 * Render related objects as a grid of facet cards
 */
function renderRelatedObjectsGrid(relatedData) {
    if (!relatedData || Object.keys(relatedData).length === 0) {
        return '<p class="no-related">No related objects found</p>';
    }
    
    let html = '';
    for (const [facet, ids] of Object.entries(relatedData)) {
        const categorySlug = (typeof facetIdToCategorySlug === 'function') ? 
                            facetIdToCategorySlug(facet) : 
                            facet.toLowerCase().replace(/_/g, '-');
        const count = Array.isArray(ids) ? ids.length : (ids.size || 0);
        
        html += `
            <div class="related-facet-card" data-facet="${categorySlug}">
                <div class="facet-icon">${getFacetIcon(categorySlug)}</div>
                <div class="facet-info">
                    <span class="facet-name">${getFacetDisplayName(categorySlug)}</span>
                    <span class="facet-count">${count} object${count !== 1 ? 's' : ''}</span>
                </div>
                <button class="btn-sm view-related" data-facet="${categorySlug}">
                    View
                </button>
            </div>
        `;
    }
    
    return html;
}

/**
 * Get facet icon (placeholder - customize per facet)
 */
function getFacetIcon(categorySlug) {
    const icons = {
        'data-sets': '📊',
        'dataset': '📊',
        'attributes': '🏷️',
        'attribute': '🏷️',
        'system': '💻',
        'glossary': '📖',
        'people': '👤',
        'role': '👥',
        'org-unit': '🏢',
        'business-area': '💼',
        'legal-entity': '⚖️',
        'client': '🤝',
        'committee': '👨‍👩‍👧‍👦',
        'policy': '📋',
        'process': '⚙️',
        'project': '📁',
        'interface': '🔌',
        'product': '📦',
        'capability': '⭐',
        'geography': '🌍',
        'regulation': '📜',
        'regulator': '🏛️',
        'regulatory-theme': '🏛️',
        'active-tasks': '✅',
        'change-requests': '🔄'
    };
    return icons[categorySlug] || '📄';
}

/**
 * Get facet display name (human-readable, i18n when available)
 */
function getFacetDisplayName(categorySlug) {
    if (typeof window !== 'undefined' && typeof window.getFacetDisplayName === 'function') {
        return window.getFacetDisplayName(categorySlug);
    }
    if (typeof window !== 'undefined' && typeof window.getCategoryDisplayName === 'function') {
        return window.getCategoryDisplayName(categorySlug);
    }
    return categorySlug ? (categorySlug.charAt(0).toUpperCase() + categorySlug.slice(1)) : '';
}

/**
 * Lazy load related objects details (if not in initial response)
 */
async function loadRelatedObjectsDetails(category, objectId, panel) {
    const facetId = (typeof categorySlugToFacetId === 'function') ? 
                    categorySlugToFacetId(category) : 
                    category.toUpperCase().replace(/-/g, '_');
    
    // Check if details already loaded
    if (window.unisonRelatedObjects?.[facetId]) {
        return; // Already have data
    }
    
    // Show loading state
    const container = panel.querySelector('.related-objects-grid');
    if (container) {
        container.innerHTML = '<div class="loading">Loading related objects...</div>';
    }
    
    try {
        const response = await fetch(`/api/unison-search/related-objects?facet=${facetId}&id=${objectId}`, {
            credentials: 'include'
        });
        const data = await response.json();
        
        // Cache in window object
        if (!window.unisonRelatedObjects) {
            window.unisonRelatedObjects = {};
        }
        if (!window.unisonRelatedObjects[facetId]) {
            window.unisonRelatedObjects[facetId] = {};
        }
        window.unisonRelatedObjects[facetId][objectId] = data;
        
        // Re-render
        if (container) {
            container.innerHTML = renderRelatedObjectsGrid(data);
        }
    } catch (error) {
        console.error('[RelatedObjects] Error loading details:', error);
        if (container) {
            container.innerHTML = '<div class="error">Failed to load related objects</div>';
        }
    }
}

// Export functions to window
if (typeof window !== 'undefined') {
    window.hasRelatedObjects = hasRelatedObjects;
    window.toggleRelatedObjectsPanel = toggleRelatedObjectsPanel;
}

// ============================================================================
// EXISTING "SEARCH RELATED OBJECTS" FUNCTIONALITY
// ============================================================================

/**
 * Initialize related objects functionality
 */
function initRelatedObjects() {
    // Add "Search Related Objects" button to grid
    addRelatedObjectsButton();
    
    // Listen for row selection in grid
    listenForRowSelection();
}

/**
 * Add "Search Related Objects in Unison" button to the UI
 */
function addRelatedObjectsButton() {
    // Find the grid/table container
    const tableContainer = document.querySelector('.data-table-wrapper') || 
                          document.querySelector('.table-container');
    
    if (!tableContainer) return;
    
    // Check if button already exists
    if (document.getElementById('search-related-objects-btn')) return;
    
    const button = document.createElement('button');
    button.id = 'search-related-objects-btn';
    button.className = 'btn btn-primary search-related-btn';
    button.textContent = 'Search Related Objects in Unison';
    button.style.display = 'none'; // Hidden until rows are selected
    button.onclick = () => openRelatedObjectsSearch();
    
    // Insert button near the table
    const header = tableContainer.querySelector('.table-header') || 
                   tableContainer.querySelector('h3') ||
                   tableContainer;
    header.insertAdjacentElement('afterend', button);
}

/**
 * Listen for row selection in the grid
 */
function listenForRowSelection() {
    // This depends on your grid implementation
    // Listen for selection events from your data table
    document.addEventListener('click', (e) => {
        // Check if a table row was clicked
        const row = e.target.closest('tr[data-id]');
        if (row) {
            handleRowSelection(row);
        }
    });
    
    // Also listen for checkbox selection if your grid uses checkboxes
    document.addEventListener('change', (e) => {
        if (e.target.type === 'checkbox' && e.target.closest('tr[data-id]')) {
            const row = e.target.closest('tr[data-id]');
            handleRowSelection(row, e.target.checked);
        }
    });
}

/**
 * Handle row selection
 */
function handleRowSelection(row, isSelected) {
    const rowId = row.getAttribute('data-id');
    const category = getCurrentCategory();
    
    if (!rowId || !category) return;
    
    // Get row data
    const rowData = extractRowData(row);
    
    if (isSelected === false) {
        // Remove from selection
        selectedRows = selectedRows.filter(r => !(r.id === rowId && r.category === category));
    } else {
        // Add to selection (if not already selected)
        const exists = selectedRows.some(r => r.id === rowId && r.category === category);
        if (!exists) {
            selectedRows.push({
                id: rowId,
                category: category,
                facetId: categoryToFacetId(category),
                data: rowData
            });
        }
    }
    
    // Update button visibility
    updateRelatedObjectsButton();
}

/**
 * Extract data from table row
 */
function extractRowData(row) {
    const data = {};
    const cells = row.querySelectorAll('td');
    
    cells.forEach((cell, index) => {
        const header = getColumnHeader(index);
        if (header) {
            data[header] = cell.textContent.trim();
        }
    });
    
    return data;
}

/**
 * Get column header for a column index
 */
function getColumnHeader(index) {
    const table = document.querySelector('.data-table') || 
                  document.querySelector('table');
    if (!table) return null;
    
    const headers = table.querySelectorAll('th');
    if (headers[index]) {
        return headers[index].textContent.trim();
    }
    return null;
}

/**
 * Get current active category
 */
function getCurrentCategory() {
    const activeCategory = document.querySelector('.category-item.active');
    if (activeCategory) {
        return activeCategory.getAttribute('data-category');
    }
    return null;
}

/**
 * Update related objects button visibility
 */
function updateRelatedObjectsButton() {
    const button = document.getElementById('search-related-objects-btn');
    if (button) {
        button.style.display = selectedRows.length > 0 ? 'block' : 'none';
        button.textContent = `Search Related Objects (${selectedRows.length} selected)`;
    }
}

/**
 * Open related objects search dialog
 */
function openRelatedObjectsSearch() {
    if (selectedRows.length === 0) {
        alert('Please select at least one row');
        return;
    }
    
    // Create search definition from selected rows
    relatedSearchDefinition = createSearchFromSelectedRows(selectedRows);
    
    // Display the search definition UI
    showRelatedObjectsSearchUI(relatedSearchDefinition);
}

/**
 * Create search definition from selected rows
 */
function createSearchFromSelectedRows(rows) {
    const searchGroups = [];
    
    // Group rows by facet/category
    const rowsByFacet = {};
    rows.forEach(row => {
        const facetId = row.facetId || categoryToFacetId(row.category);
        if (!rowsByFacet[facetId]) {
            rowsByFacet[facetId] = [];
        }
        rowsByFacet[facetId].push(row);
    });
    
    let firstGroup = true;
    for (const [facetId, facetRows] of Object.entries(rowsByFacet)) {
        const searchGroup = {
            operator: firstGroup ? 'START' : 'AND',
            active: true,
            searches: [{
                operator: 'START',
                active: true,
                facetId: facetId,
                readOnly: true, // Mark as non-editable
                filterGroups: facetRows.map(row => ({
                    field: 'id',
                    condition: 'equals',
                    value: String(row.id),
                    readOnly: true // Non-editable
                }))
            }]
        };
        
        searchGroups.push(searchGroup);
        firstGroup = false;
    }
    
    return {
        searchGroups: searchGroups,
        selectedRows: rows // Keep reference to original rows
    };
}

/**
 * Show related objects search UI
 */
function showRelatedObjectsSearchUI(searchDefinition) {
    // Create or show modal/dialog
    let modal = document.getElementById('related-objects-modal');
    if (!modal) {
        modal = createRelatedObjectsModal();
        document.body.appendChild(modal);
    }
    
    // Render search definition
    renderRelatedSearchDefinition(modal, searchDefinition);
    
    // Show modal
    modal.style.display = 'block';
}

/**
 * Create related objects modal
 */
function createRelatedObjectsModal() {
    const modal = document.createElement('div');
    modal.id = 'related-objects-modal';
    modal.className = 'modal';
    modal.innerHTML = `
        <div class="modal-content">
            <div class="modal-header">
                <h3>Search Related Objects</h3>
                <span class="close" onclick="closeRelatedObjectsModal()">&times;</span>
            </div>
            <div class="modal-body" id="related-objects-content">
                <!-- Content will be rendered here -->
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" onclick="closeRelatedObjectsModal()">Cancel</button>
                <button class="btn btn-primary" onclick="executeRelatedObjectsSearch()">Search</button>
                <button class="btn btn-info" onclick="addAdditionalFilters()">Add Filters</button>
            </div>
        </div>
    `;
    return modal;
}

/**
 * Render related search definition in modal
 */
function renderRelatedSearchDefinition(modal, searchDefinition) {
    const content = modal.querySelector('#related-objects-content');
    if (!content) return;
    
    let html = '<div class="related-search-definition">';
    html += '<h4>Selected Objects (Non-editable)</h4>';
    
    searchDefinition.searchGroups.forEach((group, groupIndex) => {
        group.searches.forEach((search, searchIndex) => {
            html += `<div class="search-item ${search.readOnly ? 'read-only' : ''}">`;
            html += `<div class="search-facet">Facet: ${search.facetId}</div>`;
            
            if (search.filterGroups && search.filterGroups.length > 0) {
                html += '<div class="search-filters">';
                search.filterGroups.forEach(filter => {
                    html += `<div class="filter-item ${filter.readOnly ? 'read-only' : ''}">`;
                    html += `${filter.field} ${filter.condition} ${filter.value}`;
                    if (filter.readOnly) {
                        html += ' <span class="badge">Read-only</span>';
                    }
                    html += '</div>';
                });
                html += '</div>';
            }
            
            html += '</div>';
        });
    });
    
    html += '</div>';
    content.innerHTML = html;
}

/**
 * Execute related objects search
 */
async function executeRelatedObjectsSearch() {
    if (!relatedSearchDefinition) return;
    
    // Convert search definition to executable format
    // For now, execute the first search group
    const firstGroup = relatedSearchDefinition.searchGroups[0];
    if (!firstGroup || !firstGroup.searches || firstGroup.searches.length === 0) {
        alert('No valid search definition');
        return;
    }
    
    const firstSearch = firstGroup.searches[0];
    const category = facetIdToCategory(firstSearch.facetId);
    
    // Build query from filterGroups
    // This is simplified - full implementation would handle all operators
    const filters = firstSearch.filterGroups || [];
    if (filters.length > 0) {
        // Execute search with ID filters
        // This would need to be integrated with your search system
        console.log('Executing related objects search:', relatedSearchDefinition);
        alert('Related objects search execution - to be integrated with search system');
    }
    
    closeRelatedObjectsModal();
}

/**
 * Add additional filters to the search
 */
function addAdditionalFilters() {
    // Show dialog to add AND/OR/NOT filters
    const operator = prompt('Enter operator (AND/OR/NOT):');
    if (!operator) return;
    
    const facetId = prompt('Enter facet ID:');
    if (!facetId) return;
    
    const field = prompt('Enter field name:');
    const condition = prompt('Enter condition (contains/equals/etc):');
    const value = prompt('Enter value:');
    
    if (field && condition && value) {
        // Add to search definition
        const newGroup = {
            operator: operator.toUpperCase(),
            active: true,
            searches: [{
                operator: 'START',
                active: true,
                facetId: facetId,
                filterGroups: [{
                    field: field,
                    condition: condition,
                    value: value,
                    readOnly: false // Editable
                }]
            }]
        };
        
        relatedSearchDefinition.searchGroups.push(newGroup);
        
        // Re-render UI
        const modal = document.getElementById('related-objects-modal');
        if (modal) {
            renderRelatedSearchDefinition(modal, relatedSearchDefinition);
        }
    }
}

/**
 * Close related objects modal
 */
function closeRelatedObjectsModal() {
    const modal = document.getElementById('related-objects-modal');
    if (modal) {
        modal.style.display = 'none';
    }
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
 * Convert facet ID to category
 */
function facetIdToCategory(facetId) {
    const mapping = {
        'DATASET': 'dataset',
        'ATTRIBUTE': 'attribute',
        'SYSTEM': 'system',
        'GLOSSARY': 'glossary',
        'DATAQUALITY': 'dataquality',
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
        'ORG_UNIT': 'orgunit',
        'GEOGRAPHY': 'geography',
        'REGULATION': 'regulation',
        'REGULATOR': 'regulator',
        'REGULATORY_THEME': 'regulatory-theme'
    };
    return mapping[facetId] || facetId.toLowerCase();
}

