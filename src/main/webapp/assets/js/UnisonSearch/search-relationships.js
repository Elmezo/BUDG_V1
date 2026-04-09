// Relationships and related indicators

const FRONTEND_RELATIONSHIP_MAP = {
    // Core data objects with full relationships
    'system': ['dataset', 'attribute', 'interface', 'legal-entity', 'people', 'business-area', 'project', 'process', 'policy', 'product'],
    'dataset': ['system', 'attribute', 'glossary', 'people', 'business-area', 'project', 'process', 'policy', 'interface', 'legal-entity'],
    'attribute': ['dataset', 'glossary', 'dataquality', 'people', 'role', 'system', 'process', 'project', 'policy'],
    'glossary': ['attribute', 'dataset', 'policy', 'process', 'project', 'capability', 'business-area', 'legal-entity', 'client', 'product'],
    'dataquality': ['attribute', 'dataset'],
    
    // People and roles
    'people': ['role', 'orgunit', 'org-unit', 'system', 'dataset', 'attribute', 'process', 'project', 'policy'],
    'role': ['people'],
    
    // Process, Project, Policy (BUDG focus)
    'process': ['dataset', 'attribute', 'system', 'glossary', 'policy', 'project', 'people', 'capability'],
    'policy': ['dataset', 'attribute', 'system', 'glossary', 'process', 'project', 'legal-entity', 'business-area'],
    'project': ['dataset', 'attribute', 'system', 'glossary', 'policy', 'process', 'capability', 'committee', 'orgunit', 'org-unit', 'people', 'client', 'product'],
    
    // Organizational
    'business-area': ['system', 'dataset', 'policy'],
    'committee': ['project', 'people'],
    'capability': ['process', 'project'],
    'client': ['legal-entity', 'project'],
    'legal-entity': ['system', 'dataset', 'policy', 'client'],
    'orgunit': ['people', 'project'],
    'org-unit': ['people', 'project'],
    
    // Infrastructure
    'product': ['project', 'system'],
    'interface': ['system', 'dataset']
};

function getRelatedCategories(category) {
    return FRONTEND_RELATIONSHIP_MAP[category] || [];
}

function findRelationshipPath(fromCategory, toCategory) {
    if (fromCategory === toCategory) {
        return [fromCategory];
    }

    // BFS to find shortest path
    const queue = [[fromCategory]];
    const visited = new Set([fromCategory]);

    while (queue.length > 0) {
        const path = queue.shift();
        const current = path[path.length - 1];

        const neighbors = FRONTEND_RELATIONSHIP_MAP[current] || [];
        for (const neighbor of neighbors) {
            if (neighbor === toCategory) {
                return [...path, neighbor];
            }

            if (!visited.has(neighbor)) {
                visited.add(neighbor);
                queue.push([...path, neighbor]);
            }
        }
    }

    return null; // No path found
}

function getRelationshipInfo(fromCategory, toCategory) {
    const path = findRelationshipPath(fromCategory, toCategory);

    if (!path || path.length === 0) {
        return {
            path: null,
            isDirect: false,
            description: `No relationship found between ${fromCategory} and ${toCategory}`
        };
    }

    const isDirect = path.length === 2;
    const pathString = path.map(c => capitalizeFirst(c)).join(' → ');

    let description;
    if (isDirect) {
        description = `Direct relationship: ${pathString}`;
    } else {
        description = `Indirect relationship (${path.length - 1} hops): ${pathString}`;
    }

    return {
        path: pathString,
        isDirect: isDirect,
        hops: path.length - 1,
        description: description,
        pathArray: path
    };
}

function capitalizeFirst(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1);
}

function updateRelatedSearchIndicator(category) {
    const selectedItem = getSelectedItem();
    if (selectedItem && selectedItem.category !== category) {
        // Update the indicator to show the relationship
        const indicator = document.querySelector('.related-search-indicator');
        if (indicator) {
            const textSpan = indicator.querySelector('.indicator-text');
            if (textSpan) {
                textSpan.innerHTML = `Showing <strong>${category}</strong> items related to: <strong>${selectedItem.name}</strong> (${selectedItem.category})`;
            }
        } else {
            // Create new indicator if it doesn't exist
            createRelatedSearchIndicator(selectedItem);
        }
    } else if (!selectedItem) {
        // Remove indicator if no item is selected
        removeRelatedSearchIndicator();
    }
}

function createRelatedSearchIndicator(item) {
    // Remove any existing indicator
    removeRelatedSearchIndicator();

    if (!item) return;

    // Get relationship path info
    const currentCategory = getActiveCategoryWithFallback();
    const relationshipInfo = getRelationshipInfo(item.category, currentCategory);

    // Create new indicator
    const indicator = document.createElement('div');
    indicator.className = 'related-search-indicator';
    indicator.innerHTML = `
        <div class="indicator-content">
            <i class="fas fa-filter"></i>
            <div class="indicator-main">
                <span class="indicator-text">Filtering results related to: <strong>${item.name}</strong> (${item.category})</span>
                ${relationshipInfo.path ? `<div class="relationship-path"><i class="fas fa-route"></i> ${relationshipInfo.path}</div>` : ''}
            </div>
            <button class="clear-filter-btn" onclick="clearSelectedItem()" title="Clear filter">
                <i class="fas fa-times"></i>
            </button>
        </div>
    `;

    // Add styles
    const style = document.createElement('style');
    style.textContent = `
        .related-search-indicator {
            background: linear-gradient(135deg, #e3f2fd 0%, #f0f7ff 100%);
            border-left: 4px solid #2196f3;
            padding: 12px 16px;
            margin-bottom: 16px;
            border-radius: 6px;
            box-shadow: 0 2px 8px rgba(33, 150, 243, 0.15);
            display: flex;
            align-items: center;
            animation: fadeIn 0.3s ease-in-out;
        }

        .related-search-indicator .indicator-content { display: flex; align-items: center; width: 100%; gap: 12px; }
        .related-search-indicator i.fas.fa-filter { color: #2196f3; font-size: 1.2em; flex-shrink: 0; }
        .related-search-indicator .indicator-main { flex-grow: 1; display: flex; flex-direction: column; gap: 4px; }
        .related-search-indicator .indicator-text { font-size: 0.95em; color: #333; }
        .related-search-indicator .relationship-path { font-size: 0.85em; color: #666; display: flex; align-items: center; gap: 6px; padding: 4px 8px; background-color: rgba(255, 255, 255, 0.6); border-radius: 4px; width: fit-content; }
        .related-search-indicator .relationship-path i { color: #4CAF50; font-size: 0.9em; }
        .related-search-indicator .clear-filter-btn { background: none; border: none; color: #666; cursor: pointer; padding: 6px 10px; border-radius: 50%; display: flex; align-items: center; justify-content: center; transition: all 0.2s ease; flex-shrink: 0; }
        .related-search-indicator .clear-filter-btn:hover { background-color: rgba(0,0,0,0.1); color: #333; transform: scale(1.1); }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
    `;
    document.head.appendChild(style);

    // Insert at the top of the table container
    if (tableContainer) {
        tableContainer.insertBefore(indicator, tableContainer.firstChild);
    } else {
        // If tableContainer not available, try to insert before the search table
        const searchTable = document.querySelector('.search-table');
        if (searchTable) {
            searchTable.parentNode.insertBefore(indicator, searchTable);
        } else {
            // Last resort - add to body
            document.body.appendChild(indicator);
        }
    }
}

function removeRelatedSearchIndicator() {
    const indicator = document.querySelector('.related-search-indicator');
    if (indicator) {
        indicator.remove();
    }
}

/**
 * Get descriptive text about the relationship between two categories
 * @param {string} currentCategory - The current category
 * @param {string} relatedCategory - The related category
 * @returns {string} - Description of the relationship
 */
function getRelationshipText(currentCategory, relatedCategory) {
    const current = currentCategory.toLowerCase();
    const related = relatedCategory.toLowerCase();

    // Define relationship descriptions
    const relationships = {
        'dataset': {
            'system': 'Datasets must be linked to the selected System via MasterSource field',
            'glossary': 'Datasets must reference the selected Glossary',
            'attribute': 'Datasets must contain the selected Attribute',
            'interface': 'Datasets must be from systems referenced in the selected Interface'
        },
        'attribute': {
            'dataset': 'Attributes must belong to the selected Dataset',
            'system': 'Attributes must belong to Datasets from the selected System',
            'glossary': 'Attributes must reference the selected Glossary'
        },
        'system': {
            'dataset': 'Systems must be referenced by the selected Dataset',
            'interface': 'Systems must be used in the selected Interface',
            'attribute': 'Systems must be referenced by Datasets containing the selected Attribute'
        },
        'glossary': {
            'dataset': 'Glossary entries must be referenced by the selected Dataset',
            'attribute': 'Glossary entries must be referenced by the selected Attribute'
        },
        'interface': {
            'system': 'Interfaces must use the selected System as source or target',
            'dataset': 'Interfaces must connect to systems referenced by the selected Dataset'
        }
    };

    // Return the relationship description if available
    if (relationships[current] && relationships[current][related]) {
        return relationships[current][related];
    }

    // Default message
    return `No direct relationship found between ${current} and ${related}`;
}

function showSelectionNotification(itemName, category) {
    // Remove existing notification
    const existingNotification = document.querySelector('.selection-notification');
    if (existingNotification) {
        existingNotification.remove();
    }

    // Get current category to suggest what to do next
    const currentCategory = getActiveCategoryWithFallback();
    const nextStepMessage = getNextStepMessage(category, currentCategory);

    // Create notification element
    const notification = document.createElement('div');
    notification.className = 'selection-notification';
    notification.innerHTML = `
        <div class="notification-content">
            <div class="notification-header">
                <i class="fas fa-filter"></i>
                <span class="notification-title">Item Selected for Related Search</span>
                <button class="close-notification-btn" onclick="this.parentElement.parentElement.parentElement.remove();">×</button>
            </div>
            <div class="notification-body">
                <p><strong>${itemName}</strong> from <strong>${category}</strong> is now selected.</p>
                <p class="next-step-hint">${nextStepMessage}</p>
            </div>
        </div>
    `;

    // Add styles
    const style = document.createElement('style');
    style.textContent = `
        .selection-notification { position: fixed; top: 20px; right: 20px; background: #fff; color: #333; border-radius: 4px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); z-index: 1000; font-size: 14px; max-width: 350px; border-left: 4px solid #4CAF50; animation: slideIn 0.3s ease-out; }
        @keyframes slideIn { from { opacity: 0; transform: translateX(30px); } to { opacity: 1; transform: translateX(0); } }
        .selection-notification .notification-content { padding: 0; }
        .selection-notification .notification-header { display: flex; align-items: center; padding: 12px 16px; background: #f9f9f9; border-bottom: 1px solid #eee; border-radius: 4px 4px 0 0; }
        .selection-notification .notification-header i { color: #4CAF50; margin-right: 8px; }
        .selection-notification .notification-title { flex-grow: 1; font-weight: 600; }
        .selection-notification .close-notification-btn { background: none; border: none; color: #999; font-size: 20px; cursor: pointer; padding: 0; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center; border-radius: 50%; transition: background-color 0.2s ease; }
        .selection-notification .close-notification-btn:hover { background-color: rgba(0,0,0,0.05); color: #666; }
        .selection-notification .notification-body { padding: 12px 16px; }
        .selection-notification .next-step-hint { margin-top: 8px; color: #666; font-size: 13px; }
    `;
    document.head.appendChild(style);

    // Add to page
    document.body.appendChild(notification);

    // Auto remove after 8 seconds
    setTimeout(() => {
        if (notification.parentElement) {
            notification.remove();
        }
    }, 8000);
}

function getNextStepMessage(selectedCategory, currentCategory) {
    // If we're already in the same category, suggest switching to another
    if (selectedCategory === currentCategory) {
        const relatedCategories = getRelatedCategories(selectedCategory);
        if (relatedCategories.length > 0) {
            return `Click on a different module tab (like <strong>${relatedCategories.join('</strong>, <strong>')}</strong>) to see related items.`;
        } else {
            return 'Click on a different module tab to see related items.';
        }
    } else {
        // If we're in a different category, explain what's happening
        return `Now showing only ${currentCategory} items related to this ${selectedCategory}.`;
    }
}


