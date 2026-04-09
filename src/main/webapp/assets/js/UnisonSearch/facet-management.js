// Facet Management UI
// Handles drag-and-drop ordering, visibility toggle, activeFields configuration

let facetManagementInitialized = false;
let currentFacets = [];

/**
 * Initialize facet management UI
 */
async function initFacetManagement() {
    if (facetManagementInitialized) return;
    
    // Check if user is admin for "Save Default Layout" button
    const isAdmin = await checkIfAdmin();
    
    // Load user's facet configuration
    await loadUserFacets(isAdmin);
    
    facetManagementInitialized = true;
}

/**
 * Load user's facet configuration from server
 */
async function loadUserFacets(isAdmin) {
    try {
        const response = await fetch('/api/unison/facets', {
            method: 'GET',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to load facets');
        }

        const result = await response.json();
        if (result.success && result.data) {
            currentFacets = result.data;
            renderFacetManagementUI(currentFacets, isAdmin);
        }
    } catch (error) {
        console.error('Error loading facets:', error);
        showFacetError('Failed to load facet configuration');
    }
}

/**
 * Render facet management UI
 */
function renderFacetManagementUI(facets, isAdmin) {
    const container = document.getElementById('facet-management-container');
    if (!container) {
        console.error('Facet management container not found');
        return;
    }

    // Sort facets by ordering (active first, then by ordering)
    const sortedFacets = [...facets].sort((a, b) => {
        if (a.active !== b.active) {
            return b.active ? 1 : -1; // Active first
        }
        return (a.ordering || 0) - (b.ordering || 0);
    });

    let html = `
        <div class="facet-management-header">
            <h3>Facet Configuration</h3>
            <div class="facet-management-actions">
                <button class="btn btn-secondary" onclick="resetFacetLayout()">Reset Layout</button>
                ${isAdmin ? '<button class="btn btn-primary" onclick="saveDefaultLayout()">Save Default Layout</button>' : ''}
            </div>
        </div>
        <div class="facet-list" id="facet-list">
    `;

    sortedFacets.forEach((facet, index) => {
        const facetName = getFacetDisplayName(facet.facetId);
        html += `
            <div class="facet-item ${facet.active ? 'active' : 'inactive'}" data-facet-id="${facet.facetId}" draggable="true">
                <div class="facet-drag-handle">☰</div>
                <div class="facet-content">
                    <div class="facet-header">
                        <label class="facet-toggle">
                            <input type="checkbox" ${facet.active ? 'checked' : ''} 
                                   onchange="toggleFacetVisibility('${facet.facetId}', this.checked)">
                            <span class="facet-name">${facetName}</span>
                        </label>
                    </div>
                    <div class="facet-fields" style="display: ${facet.active ? 'block' : 'none'}">
                        <label>Active Fields:</label>
                        <input type="text" class="facet-fields-input" 
                               value="${facet.activeFields || ''}" 
                               placeholder="e.g., refNumber,name,description"
                               onchange="updateFacetFields('${facet.facetId}', this.value)">
                    </div>
                </div>
                <div class="facet-ordering" style="display: none;">${facet.ordering || 0}</div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;

    // Initialize drag and drop
    initDragAndDrop();
}

/**
 * Initialize drag and drop for facet reordering
 */
function initDragAndDrop() {
    const facetList = document.getElementById('facet-list');
    if (!facetList) return;

    let draggedElement = null;

    facetList.querySelectorAll('.facet-item').forEach(item => {
        item.addEventListener('dragstart', (e) => {
            draggedElement = item;
            item.classList.add('dragging');
            e.dataTransfer.effectAllowed = 'move';
        });

        item.addEventListener('dragend', () => {
            item.classList.remove('dragging');
            draggedElement = null;
        });

        item.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'move';
            
            const afterElement = getDragAfterElement(facetList, e.clientY);
            if (afterElement == null) {
                facetList.appendChild(draggedElement);
            } else {
                facetList.insertBefore(draggedElement, afterElement);
            }
        });

        item.addEventListener('drop', (e) => {
            e.preventDefault();
            updateFacetOrdering();
        });
    });
}

/**
 * Get element after which to insert dragged element
 */
function getDragAfterElement(container, y) {
    const draggableElements = [...container.querySelectorAll('.facet-item:not(.dragging)')];
    
    return draggableElements.reduce((closest, child) => {
        const box = child.getBoundingClientRect();
        const offset = y - box.top - box.height / 2;
        
        if (offset < 0 && offset > closest.offset) {
            return { offset: offset, element: child };
        } else {
            return closest;
        }
    }, { offset: Number.NEGATIVE_INFINITY }).element;
}

/**
 * Update facet ordering based on current DOM order
 */
function updateFacetOrdering() {
    const facetList = document.getElementById('facet-list');
    if (!facetList) return;

    const items = facetList.querySelectorAll('.facet-item');
    let ordering = 1;

    items.forEach(item => {
        const facetId = item.getAttribute('data-facet-id');
        const isActive = item.classList.contains('active');
        const orderingElement = item.querySelector('.facet-ordering');
        
        if (isActive) {
            orderingElement.textContent = ordering;
            ordering++;
        } else {
            orderingElement.textContent = '0';
        }
    });

    // Save updated ordering
    saveFacetLayout();
}

/**
 * Toggle facet visibility
 */
async function toggleFacetVisibility(facetId, isActive) {
    const facet = currentFacets.find(f => f.facetId === facetId);
    if (!facet) return;

    facet.active = isActive;
    
    // Update UI
    const facetItem = document.querySelector(`[data-facet-id="${facetId}"]`);
    if (facetItem) {
        if (isActive) {
            facetItem.classList.add('active');
            facetItem.classList.remove('inactive');
            facetItem.querySelector('.facet-fields').style.display = 'block';
            // Set ordering if activating
            if (!facet.ordering || facet.ordering === 0) {
                facet.ordering = getNextOrdering();
            }
        } else {
            facetItem.classList.remove('active');
            facetItem.classList.add('inactive');
            facetItem.querySelector('.facet-fields').style.display = 'none';
            facet.ordering = 0;
        }
    }

    await saveFacetLayout();
}

/**
 * Update facet active fields
 */
async function updateFacetFields(facetId, activeFields) {
    const facet = currentFacets.find(f => f.facetId === facetId);
    if (facet) {
        facet.activeFields = activeFields;
        await saveFacetLayout();
    }
}

/**
 * Get next ordering value for active facets
 */
function getNextOrdering() {
    const activeFacets = currentFacets.filter(f => f.active && f.ordering > 0);
    if (activeFacets.length === 0) return 1;
    return Math.max(...activeFacets.map(f => f.ordering || 0)) + 1;
}

/**
 * Save facet layout to server
 */
async function saveFacetLayout() {
    try {
        // Update ordering from DOM
        const facetList = document.getElementById('facet-list');
        if (facetList) {
            const items = facetList.querySelectorAll('.facet-item');
            let ordering = 1;
            items.forEach(item => {
                const facetId = item.getAttribute('data-facet-id');
                const facet = currentFacets.find(f => f.facetId === facetId);
                if (facet) {
                    if (facet.active) {
                        facet.ordering = ordering;
                        ordering++;
                    } else {
                        facet.ordering = 0;
                    }
                }
            });
        }

        const response = await fetch('/api/unison/facets/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify({ facets: currentFacets })
        });

        if (!response.ok) {
            throw new Error('Failed to save facets');
        }

        const result = await response.json();
        if (result.success) {
            showFacetSuccess('Facet layout saved successfully');
        }
    } catch (error) {
        console.error('Error saving facets:', error);
        showFacetError('Failed to save facet layout');
    }
}

/**
 * Reset facet layout to defaults
 */
async function resetFacetLayout() {
    if (!confirm('Are you sure you want to reset your facet layout to defaults?')) {
        return;
    }

    try {
        const response = await fetch('/api/unison/facets/reset', {
            method: 'POST',
            headers: { 'Accept': 'application/json' },
            credentials: 'include'
        });

        if (!response.ok) {
            throw new Error('Failed to reset facets');
        }

        const result = await response.json();
        if (result.success) {
            showFacetSuccess('Facet layout reset to defaults');
            await loadUserFacets(await checkIfAdmin());
        }
    } catch (error) {
        console.error('Error resetting facets:', error);
        showFacetError('Failed to reset facet layout');
    }
}

/**
 * Save default layout (SuperAdmin only)
 */
async function saveDefaultLayout() {
    if (!confirm('Save current layout as default for all users?')) {
        return;
    }

    try {
        // Get current facets configuration
        const defaults = {
            facets: currentFacets.map(f => ({
                id: f.facetId,
                visibility: f.active,
                activeFields: f.activeFields || ''
            }))
        };

        const response = await fetch('/api/unison/defaults/save', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            credentials: 'include',
            body: JSON.stringify(defaults)
        });

        if (!response.ok) {
            if (response.status === 403) {
                throw new Error('Admin access required');
            }
            throw new Error('Failed to save defaults');
        }

        const result = await response.json();
        if (result.success) {
            showFacetSuccess('Default layout saved successfully');
        }
    } catch (error) {
        console.error('Error saving defaults:', error);
        showFacetError(error.message || 'Failed to save default layout');
    }
}

/**
 * Check if current user is admin
 */
async function checkIfAdmin() {
    // TODO: Implement admin check based on your authentication system
    // This is a placeholder - implement based on your role system
    return false;
}

/**
 * Get display name for facet ID
 */
function getFacetDisplayName(facetId) {
    // Map facet IDs to translation keys
    const facetKeyMap = {
        'DATASET': 'facet.dataset',
        'ATTRIBUTE': 'facet.attribute',
        'SYSTEM': 'facet.system',
        'GLOSSARY': 'facet.glossary',
        'DATAQUALITY': 'facet.dataQuality',
        'PEOPLE': 'facet.people',
        'ROLE': 'facet.role',
        'BUSINESS_AREA': 'facet.businessArea',
        'LEGAL_ENTITY': 'facet.legalEntity',
        'CLIENT': 'facet.client',
        'COMMITTEE': 'facet.committee',
        'POLICY': 'facet.policy',
        'PROCESS': 'facet.process',
        'INTERFACE': 'facet.interface',
        'CAPABILITY': 'facet.capability',
        'PRODUCT': 'facet.product',
        'ORG_UNIT': 'facet.orgUnit',
        'GEOGRAPHY': 'facet.geography',
        'REGULATION': 'facet.regulation',
        'REGULATOR': 'facet.regulator',
        'REGULATORY_THEME': 'facet.regulatoryTheme',
        'ACTIVE_TASKS': 'facet.activeTasks',
        'ACTIVETASKS': 'facet.activeTasks',
        'PROJECT': 'facet.project',
        'CHANGE_REQUESTS': 'facet.changeRequests',
        'CHANGE_REQUEST': 'facet.changeRequests'
    };
    
    // Fallback names if translation is not available
    const fallbackNames = {
        'DATASET': 'Dataset',
        'ATTRIBUTE': 'Attribute',
        'SYSTEM': 'System',
        'GLOSSARY': 'Glossary',
        'DATAQUALITY': 'Data Quality',
        'PEOPLE': 'People',
        'ROLE': 'Role',
        'BUSINESS_AREA': 'Business Area',
        'LEGAL_ENTITY': 'Legal Entity',
        'CLIENT': 'Client',
        'COMMITTEE': 'Committee',
        'POLICY': 'Policy',
        'PROCESS': 'Process',
        'INTERFACE': 'Interface',
        'CAPABILITY': 'Capability',
        'PRODUCT': 'Product',
        'ORG_UNIT': 'Org Unit',
        'GEOGRAPHY': 'Geography',
        'REGULATION': 'Regulation',
        'REGULATOR': 'Regulator',
        'REGULATORY_THEME': 'Regulatory Theme',
        'ACTIVE_TASKS': 'Active Tasks',
        'ACTIVETASKS': 'Active Tasks',
        'PROJECT': 'Project',
        'CHANGE_REQUESTS': 'Change Requests',
        'CHANGE_REQUEST': 'Change Requests'
    };
    
    const translationKey = facetKeyMap[facetId];
    if (translationKey && window.I18n && typeof window.I18n.t === 'function') {
        const translated = window.I18n.t(translationKey);
        // If translation exists (doesn't return the key itself), use it
        if (translated !== translationKey) {
            return translated;
        }
    }
    
    // Fallback to English name
    if (fallbackNames[facetId]) {
        return fallbackNames[facetId];
    }
    
    // Last resort: convert underscores to spaces and capitalize words
    return facetId
        .replace(/_/g, ' ')
        .split(' ')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
        .join(' ');
}

/**
 * Show success message
 */
function showFacetSuccess(message) {
    // TODO: Implement toast/notification system
    console.log('Success:', message);
    alert(message);
}

/**
 * Show error message
 */
function showFacetError(message) {
    // TODO: Implement toast/notification system
    console.error('Error:', message);
    alert('Error: ' + message);
}

