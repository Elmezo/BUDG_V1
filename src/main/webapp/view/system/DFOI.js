// Data Flow Outside Interfaces Component

function escapeHtml(str) {
    if (str == null) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

async function loadDataFlowOutsideInterfaces(systemId) {
    const container = document.getElementById('systemDataFlowOutsideInterfacesContainer');
    if (!container) {
        return;
    }
    
    container.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading...</div>';
    
    try {
        const data = await window.BUDG_API_SERVICE.getDataFlowOutsideInterfaces(systemId);
        const rows = Array.isArray(data) ? data : [];

        if (rows.length === 0) {
            container.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">DATA FLOW OUTSIDE INTERFACES</div>
                    <div class="interfaces-table-wrapper">
                        <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">
                            No data flow outside interfaces found.
                        </div>
                    </div>
                </div>
            `;
            return;
        }

        const rowsHtml = rows.map((r, index) => {
            const fromLink = `<a href="/view/system/${encodeURIComponent(r.fromId)}" target="_blank" rel="noopener noreferrer" style="color: #248567; text-decoration: none;">${escapeHtml(r.from || '')}</a>`;
            const toLink = `<a href="/view/system/${encodeURIComponent(r.toId)}" target="_blank" rel="noopener noreferrer" style="color: #248567; text-decoration: none;">${escapeHtml(r.to || '')}</a>`;
              const dataAttributesLink = `<a href="/view/system/${encodeURIComponent(systemId)}?tab=DataFlow&filterFrom=${encodeURIComponent(r.fromId)}&filterTo=${encodeURIComponent(r.toId)}" style="color: #248567; text-decoration: none;">${escapeHtml(String(r.dataAttributes ?? 0))}</a>`;

            return `
                <tr>
                    <td><i class="fas fa-server" style="color: #6b7280; margin-right: 0.5rem;"></i>${fromLink}</td>
                    <td><i class="fas fa-server" style="color: #6b7280; margin-right: 0.5rem;"></i>${toLink}</td>
                    <td>${dataAttributesLink}</td>
                </tr>
            `;
        }).join('');

        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title" style="position:relative;">
                    <span>DATA FLOW OUTSIDE INTERFACES</span>
                    <span style="position:absolute;right:0.75rem;top:50%;transform:translateY(-50%);">${window._gridSettingsHtml ? window._gridSettingsHtml('dfoi') : ''}</span>
                </div>
                <div class="interfaces-table-wrapper">
                    <table class="interfaces-table">
                        <thead>
                            <tr>
                                <th><i class="fas fa-project-diagram"></i> From</th>
                                <th><i class="fas fa-bullseye"></i> To</th>
                                <th><i class="fas fa-database"></i> Data Attributes</th>
                            </tr>
                        </thead>
                        <tbody>${rowsHtml}</tbody>
                    </table>
                    <div class="table-footer">${rows.length} record${rows.length !== 1 ? 's' : ''}</div>
                </div>
            </div>
        `;
        if (window._initGridSettings) window._initGridSettings(container);
    } catch (e) {
        container.innerHTML = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">DATA FLOW OUTSIDE INTERFACES</div>
                <div class="interfaces-table-wrapper">
                    <div style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">
                        Failed to load data flow outside interfaces.
                    </div>
                </div>
            </div>
        `;
    }
}

// Load detailed data flow between two systems
async function loadDataFlowDetails(sourceSystemId, targetSystemId) {
    try {
        // Fetch attribute relationships between the two systems
        const response = await fetch(`/api/attribute-relationships?sourceSystem=${sourceSystemId}&targetSystem=${targetSystemId}&normalizeDirection=true`, {
            method: 'GET',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' }
        });
        
        if (!response.ok) {
            return [];
        }
        
        const data = await response.json();
        return Array.isArray(data) ? data : [];
    } catch (error) {
        return [];
    }
}

// Default visible columns (only visible by default, rest are hidden)
const DEFAULT_VISIBLE_COLUMNS = [
    'sourceRef',
    'sourceDataSet',
    'sourceAttribute',
    'sourceOrigination',
    'relationshipType',
    'targetRef',
    'targetDataSet',
    'targetAttribute',
    'targetOrigination'
];

// All available columns with their configurations (in display order)
const ALL_COLUMNS = [
    { id: 'sourceRef', label: 'Source Ref.', icon: 'fa-hashtag' },
    { id: 'sourceDataSet', label: 'Source Data Set', icon: 'fa-database' },
    { id: 'sourceAttribute', label: 'Source Attribute', icon: 'fa-tag' },
    { id: 'sourceAttributeDescription', label: 'Source Attribute Description', icon: 'fa-info-circle' },
    { id: 'sourceGlossary', label: 'Source Glossary', icon: 'fa-book' },
    { id: 'sourceGlossaryDescription', label: 'Source Glossary Description', icon: 'fa-comment' },
    { id: 'sourceMandatory', label: 'Source Mandatory', icon: 'fa-asterisk' },
    { id: 'sourceOrigination', label: 'Source Origination', icon: 'fa-source' },
    { id: 'sourceEditability', label: 'Source Editability', icon: 'fa-edit' },
    { id: 'sourceEditabilityRole', label: 'Source Editability Role', icon: 'fa-user-tag' },
    { id: 'sourceDataType', label: 'Source Data Type', icon: 'fa-code' },
    { id: 'sourceDataLength', label: 'Source Data Length', icon: 'fa-ruler' },
    { id: 'relationshipType', label: 'Relationship Type', icon: 'fa-link' },
    { id: 'targetRef', label: 'Target Ref.', icon: 'fa-hashtag' },
    { id: 'targetDataSet', label: 'Target Data Set', icon: 'fa-database' },
    { id: 'targetAttribute', label: 'Target Attribute', icon: 'fa-tag' },
    { id: 'targetAttributeDescription', label: 'Target Attribute Description', icon: 'fa-info-circle' },
    { id: 'targetGlossary', label: 'Target Glossary', icon: 'fa-book' },
    { id: 'targetGlossaryDescription', label: 'Target Glossary Description', icon: 'fa-comment' },
    { id: 'targetMandatory', label: 'Target Mandatory', icon: 'fa-asterisk' },
    { id: 'targetOrigination', label: 'Target Origination', icon: 'fa-bullseye' },
    { id: 'targetEditability', label: 'Target Editability', icon: 'fa-edit' },
    { id: 'targetEditabilityRole', label: 'Target Editability Role', icon: 'fa-user-tag' },
    { id: 'targetDataType', label: 'Target Data Type', icon: 'fa-code' },
    { id: 'targetDataLength', label: 'Target Data Length', icon: 'fa-ruler' }
];

// Store visible columns state
let visibleColumns = [...DEFAULT_VISIBLE_COLUMNS];
// Store relationships data for column toggling without re-render
let storedRelationships = [];
// Render data flow table with relationship details
function renderDataFlowTable(relationships) {
    // Store relationships for column toggling
    storedRelationships = relationships || [];
    if (!relationships || relationships.length === 0) {
        return `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">DATA FLOW</div>
                <div class="interfaces-table-wrapper">
                    <div style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">
                        No data flow relationships found.
                    </div>
                </div>
            </div>
        `;
    }
    
    // Function to get cell value based on column ID
    const getCellValue = (rel, columnId) => {
        const sourceAttrLink = (rel.sourceDatasetId && rel.sourceAttributeId) 
            ? `<a href="/view/dataset/${encodeURIComponent(rel.sourceDatasetId)}?tab=attribute&attributeId=${encodeURIComponent(rel.sourceAttributeId)}&modal=true" target="_blank" style="color: #248567; text-decoration: none;" title="View attribute in dataset">${escapeHtml(rel.sourceAttribute || '')}</a>`
            : escapeHtml(rel.sourceAttribute || '');
            
        const targetAttrLink = (rel.targetDatasetId && rel.targetAttributeId) 
            ? `<a href="/view/dataset/${encodeURIComponent(rel.targetDatasetId)}?tab=attribute&attributeId=${encodeURIComponent(rel.targetAttributeId)}&modal=true" target="_blank" style="color: #248567; text-decoration: none;" title="View attribute in dataset">${escapeHtml(rel.targetAttribute || '')}</a>`
            : escapeHtml(rel.targetAttribute || '');
        
        const values = {
            'relationshipType': `<span class="view-badge" style="background: var(--primary-50,#e7f5ed); color: var(--primary-700,#195d48); border-color: var(--primary-100,#d2ebde);">${escapeHtml(rel.relationshipType || '')}</span>`,
            'sourceAttribute': sourceAttrLink,
            'sourceAttributeDescription': escapeHtml(rel.sourceAttributeDescription || ''),
            'sourceDataLength': escapeHtml(rel.sourceDataLength || ''),
            'sourceDataSet': escapeHtml(rel.sourceDataSet || ''),
            'sourceDataType': escapeHtml(rel.sourceDataType || ''),
            'sourceEditability': escapeHtml(rel.sourceEditability || ''),
            'sourceEditabilityRole': escapeHtml(rel.sourceEditabilityRole || ''),
            'sourceGlossary': escapeHtml(rel.sourceGlossary || ''),
            'sourceGlossaryDescription': escapeHtml(rel.sourceGlossaryDescription || ''),
            'sourceMandatory': rel.sourceMandatory ? 'Yes' : 'No',
            'sourceOrigination': escapeHtml(rel.sourceOrigination || ''),
            'sourceRef': escapeHtml(rel.sourceRef || ''),
            'targetAttribute': targetAttrLink,
            'targetAttributeDescription': escapeHtml(rel.targetAttributeDescription || ''),
            'targetDataLength': escapeHtml(rel.targetDataLength || ''),
            'targetDataSet': escapeHtml(rel.targetDataSet || ''),
            'targetDataType': escapeHtml(rel.targetDataType || ''),
            'targetEditability': escapeHtml(rel.targetEditability || ''),
            'targetEditabilityRole': escapeHtml(rel.targetEditabilityRole || ''),
            'targetGlossary': escapeHtml(rel.targetGlossary || ''),
            'targetGlossaryDescription': escapeHtml(rel.targetGlossaryDescription || ''),
            'targetMandatory': rel.targetMandatory ? 'Yes' : 'No',
            'targetOrigination': escapeHtml(rel.targetOrigination || ''),
            'targetRef': escapeHtml(rel.targetRef || '')
        };
        
        return values[columnId] || '';
    };
    
    // Render ALL columns but hide non-visible ones
    const rowsHtml = relationships.map((rel, index) => {
        const cells = ALL_COLUMNS.map(col => {
            const isVisible = visibleColumns.includes(col.id);
            const cellValue = getCellValue(rel, col.id);
            return `<td data-column-id="${col.id}" style="display: ${isVisible ? 'table-cell' : 'none'};">${cellValue}</td>`;
        }).join('');
        return `<tr>${cells}</tr>`;
    }).join('');
    
    // Generate table headers for ALL columns (hide non-visible ones)
    const headerCells = ALL_COLUMNS.map(col => {
        const isVisible = visibleColumns.includes(col.id);
        return `<th data-column-id="${col.id}" style="display: ${isVisible ? 'table-cell' : 'none'};"><i class="fas ${col.icon}"></i> ${col.label}</th>`;
    }).join('');
    
    // Generate column selector checkboxes
    const columnCheckboxes = ALL_COLUMNS.map(col => {
        const isChecked = visibleColumns.includes(col.id);
        return `
            <label style="display: flex; align-items: center; padding: 0.5rem 1rem; cursor: pointer; white-space: nowrap;">
                <input type="checkbox" 
                       value="${col.id}" 
                       ${isChecked ? 'checked' : ''} 
                       onchange="toggleColumn('${col.id}')"
                       style="margin-right: 0.5rem; cursor: pointer;">
                <span>${col.label}</span>
            </label>
        `;
    }).join('');
    
    const chooseColumnsText = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('button.chooseColumns') : 'Choose Columns';
    const selectColumnsText = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('dataset.messages.selectColumns') : 'Select Columns';
    const showDefaultsText = (typeof window !== 'undefined' && window.I18n && window.I18n.t) ? window.I18n.t('button.showDefaults') : 'Show Defaults';
    return `
        <div class="view-section" style="grid-column:1/-1;">
            <div class="section-title" style="display: flex; justify-content: space-between; align-items: center;">
                <span>DATA FLOW</span>
                <div style="display:flex;align-items:center;gap:8px;">
                <div style="position: relative;">
                    <button type="button" 
                            id="columnSelectorBtn" 
                            onclick="toggleColumnSelector()"
                            style="background: none; border: 1px solid #d1d5db; border-radius: 0.375rem; padding: 0.5rem 0.75rem; cursor: pointer; color: #374151; font-size: 1rem; display: flex; align-items: center; gap: 0.5rem;">
                        <i class="fas fa-cog"></i>
                        <span style="font-size: 0.875rem;">${chooseColumnsText}</span>
                    </button>
                    <div id="columnSelectorDropdown" 
                         style="display: none; position: absolute; right: 0; top: 100%; margin-top: 0.5rem; background: white; border: 1px solid #d1d5db; border-radius: 0.5rem; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); z-index: 1000; min-width: 250px; max-height: 400px; overflow-y: auto;">
                        <div style="padding: 0.75rem 1rem; border-bottom: 1px solid #e5e7eb; background: #f9fafb; font-weight: 600; display: flex; justify-content: space-between; align-items: center;">
                            <span>${selectColumnsText}</span>
                            <button type="button" 
                                    onclick="resetToDefaults()"
                                    style="background: none; border: none; color: #248567; cursor: pointer; font-size: 0.875rem; text-decoration: underline;">
                                ${showDefaultsText}
                            </button>
                        </div>
                        ${columnCheckboxes}
                    </div>
                </div>
                </div>
            </div>
            <div class="interfaces-table-wrapper">
                <table class="interfaces-table">
                    <thead>
                        <tr>${headerCells}</tr>
                    </thead>
                    <tbody>${rowsHtml}</tbody>
                </table>
                <div class="table-footer">${relationships.length} record${relationships.length !== 1 ? 's' : ''}</div>
            </div>
        </div>
    `;
}

// Add dynamic Data Flow tab
function addDataFlowTab(systemId, filterFrom, filterTo) {
    const tabContainer = document.querySelector('.tab-container');
    if (!tabContainer) {
        return;
    }
    
    // Check if tab already exists
    let existingTab = document.querySelector('.tab[data-tab="dataflow"]');
    if (existingTab) {
        // Activate it and reload content
        document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
        existingTab.classList.add('active');
        
        const contentBody = document.querySelector('.content-body');
        if (contentBody) {
            contentBody.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading data flow...</div>';
            loadDataFlowDetails(filterFrom, filterTo).then(relationships => {
                contentBody.innerHTML = renderDataFlowTable(relationships);
                if (window._initGridSettings) window._initGridSettings(contentBody);
            });
        }
        return;
    }
    
    // Store the currently active tab to return to it later
    const currentTab = document.querySelector('.tab.active');
    if (currentTab) {
        const currentTabName = currentTab.getAttribute('data-tab');
        tabContainer.setAttribute('data-previous-tab', currentTabName);
    }
    
    // Create new tab with close button
    const newTab = document.createElement('div');
    newTab.className = 'tab';
    newTab.setAttribute('data-tab', 'dataflow');
    newTab.setAttribute('data-filter-from', filterFrom);
    newTab.setAttribute('data-filter-to', filterTo);
    newTab.innerHTML = `
        DATA FLOW
        <button type="button" class="tab-close-btn" aria-label="Close tab" style="margin-left: 0.5rem; background: none; border: none; cursor: pointer; color: inherit; font-size: 1rem; padding: 0; line-height: 1;">
            <i class="fas fa-times"></i>
        </button>
    `;
    
    // Add tab to container
    tabContainer.appendChild(newTab);
    
    // Deactivate all tabs
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    newTab.classList.add('active');
    
    // Load data flow content
    const contentBody = document.querySelector('.content-body');
    if (contentBody) {
        contentBody.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading data flow...</div>';
        
        loadDataFlowDetails(filterFrom, filterTo).then(relationships => {
            contentBody.innerHTML = renderDataFlowTable(relationships);
            if (window._initGridSettings) window._initGridSettings(contentBody);
        }).catch(error => {
            contentBody.innerHTML = `
                <div class="view-section" style="grid-column:1/-1;">
                    <div class="section-title">DATA FLOW</div>
                    <div style="text-align:center;padding:2rem;color:var(--danger,#b91c1c);">
                        Failed to load data flow.
                    </div>
                </div>
            `;
        });
    }
    
    // Add close button event listener
    const closeBtn = newTab.querySelector('.tab-close-btn');
    if (closeBtn) {
        closeBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            removeDataFlowTab();
        });
    }
    
    // Add tab click event listener
    newTab.addEventListener('click', () => {
        if (!newTab.classList.contains('active')) {
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            newTab.classList.add('active');
            
            // Get the stored filter values
            const storedFilterFrom = newTab.getAttribute('data-filter-from');
            const storedFilterTo = newTab.getAttribute('data-filter-to');
            
            if (contentBody) {
                contentBody.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading data flow...</div>';
                loadDataFlowDetails(storedFilterFrom, storedFilterTo).then(relationships => {
                    contentBody.innerHTML = renderDataFlowTable(relationships);
                    if (window._initGridSettings) window._initGridSettings(contentBody);
                });
            }
        }
    });
}

// Remove Data Flow tab and return to previous tab
function removeDataFlowTab() {
    const dataFlowTab = document.querySelector('.tab[data-tab="dataflow"]');
    if (!dataFlowTab) return;
    
    const tabContainer = document.querySelector('.tab-container');
    const previousTabName = tabContainer?.getAttribute('data-previous-tab') || 'summary';
    
    // Remove the tab
    dataFlowTab.remove();
    
    // Activate previous tab
    const previousTab = document.querySelector(`.tab[data-tab="${previousTabName}"]`);
    if (previousTab) {
        previousTab.click();
    } else {
        // Fallback to first tab
        const firstTab = document.querySelector('.tab');
        if (firstTab) firstTab.click();
    }
    
    // Clean URL
    const url = new URL(window.location);
    url.searchParams.delete('tab');
    url.searchParams.delete('filterFrom');
    url.searchParams.delete('filterTo');
    window.history.replaceState({}, '', url);
}

// Toggle column visibility without re-rendering
function toggleColumn(columnId) {
    const index = visibleColumns.indexOf(columnId);
    if (index > -1) {
        visibleColumns.splice(index, 1);
    } else {
        // Add column in the correct order based on ALL_COLUMNS
        const allColumnIds = ALL_COLUMNS.map(c => c.id);
        visibleColumns = allColumnIds.filter(id => 
            visibleColumns.includes(id) || id === columnId
        );
    }
    
    // Show/hide columns directly in DOM without re-rendering
    const table = document.querySelector('.interfaces-table');
    if (table) {
        const isVisible = visibleColumns.includes(columnId);
        const displayValue = isVisible ? 'table-cell' : 'none';
        
        // Update all header cells for this column
        const headerCells = table.querySelectorAll(`th[data-column-id="${columnId}"]`);
        headerCells.forEach(cell => {
            cell.style.display = displayValue;
        });
        
        // Update all data cells for this column
        const dataCells = table.querySelectorAll(`td[data-column-id="${columnId}"]`);
        dataCells.forEach(cell => {
            cell.style.display = displayValue;
        });
    }
    
    // Update checkbox state
    const checkbox = document.querySelector(`input[type="checkbox"][value="${columnId}"]`);
    if (checkbox) {
        checkbox.checked = visibleColumns.includes(columnId);
    }
}

// Toggle column selector dropdown
function toggleColumnSelector() {
    console.log('toggleColumnSelector called');
    const dropdown = document.getElementById('columnSelectorDropdown');
    console.log('Dropdown element:', dropdown);
    if (dropdown) {
        const newState = dropdown.style.display === 'none' ? 'block' : 'none';
        dropdown.style.display = newState;
        console.log('Dropdown state changed to:', newState);
    }
}

// Reset to default columns without re-rendering
function resetToDefaults() {
    visibleColumns = [...DEFAULT_VISIBLE_COLUMNS];
    
    // Show/hide all columns directly in DOM without re-rendering
    const table = document.querySelector('.interfaces-table');
    if (table) {
        ALL_COLUMNS.forEach(col => {
            const isVisible = visibleColumns.includes(col.id);
            const displayValue = isVisible ? 'table-cell' : 'none';
            
            // Update all header cells for this column
            const headerCells = table.querySelectorAll(`th[data-column-id="${col.id}"]`);
            headerCells.forEach(cell => {
                cell.style.display = displayValue;
            });
            
            // Update all data cells for this column
            const dataCells = table.querySelectorAll(`td[data-column-id="${col.id}"]`);
            dataCells.forEach(cell => {
                cell.style.display = displayValue;
            });
        });
    }
    
    // Update all checkbox states
    ALL_COLUMNS.forEach(col => {
        const checkbox = document.querySelector(`input[type="checkbox"][value="${col.id}"]`);
        if (checkbox) {
            checkbox.checked = visibleColumns.includes(col.id);
        }
    });
}

// Reload data flow table
function reloadDataFlowTable(callback) {
    console.log('reloadDataFlowTable called');
    const contentBody = document.querySelector('.content-body');
    const dataFlowTab = document.querySelector('.tab[data-tab="dataflow"]');
    
    console.log('contentBody:', !!contentBody, 'dataFlowTab:', !!dataFlowTab);
    
    if (contentBody && dataFlowTab) {
        const filterFrom = dataFlowTab.getAttribute('data-filter-from');
        const filterTo = dataFlowTab.getAttribute('data-filter-to');
        
        console.log('Reloading with filters - From:', filterFrom, 'To:', filterTo);
        
        if (filterFrom && filterTo) {
            // Store scroll position before any DOM changes
            const savedScrollPos = window.scrollY || window.pageYOffset;
            console.log('Saving scroll position before reload:', savedScrollPos);
            
            contentBody.innerHTML = '<div class="view-section" style="grid-column:1/-1;">Loading data flow...</div>';
            // Restore scroll immediately after innerHTML to prevent jump
            window.scrollTo(0, savedScrollPos);
            
            loadDataFlowDetails(filterFrom, filterTo).then(relationships => {
                console.log('Data loaded, re-rendering table with', relationships.length, 'relationships');
                contentBody.innerHTML = renderDataFlowTable(relationships);
                if (window._initGridSettings) window._initGridSettings(contentBody);
                console.log('Table re-rendered');
                
                // Restore scroll after innerHTML
                window.scrollTo(0, savedScrollPos);
                
                // Call callback after table is rendered
                if (callback && typeof callback === 'function') {
                    console.log('Calling callback after table render');
                    // Pass saved scroll position to callback
                    callback(savedScrollPos);
                }
            });
        }
    }
}

// Close dropdown when clicking outside
document.addEventListener('click', function(event) {
    const dropdown = document.getElementById('columnSelectorDropdown');
    const button = document.getElementById('columnSelectorBtn');
    
    if (dropdown && button && dropdown.style.display === 'block') {
        // Check if click is outside both button and dropdown
        if (!dropdown.contains(event.target) && !button.contains(event.target)) {
            console.log('Click outside detected, closing dropdown');
            dropdown.style.display = 'none';
        } else {
            console.log('Click inside dropdown or button, keeping it open');
        }
    }
});

// Export for use in other files
if (typeof window !== 'undefined') {
    window.loadDataFlowOutsideInterfaces = loadDataFlowOutsideInterfaces;
    window.addDataFlowTab = addDataFlowTab;
    window.removeDataFlowTab = removeDataFlowTab;
    window.toggleColumn = toggleColumn;
    window.toggleColumnSelector = toggleColumnSelector;
    window.resetToDefaults = resetToDefaults;
}

