// Advanced Filter System for Unison Search
// Manages dynamic filters per facet with dropdown, date range, and people search filters

// Global state
let activeFilters = [];
let filterFieldsMetadata = {};
let filterIdCounter = 0;
// Tracks which search fields (name, ref, etc.) are enabled per facet
let activeSearchFields = {}; // e.g. { name: true, ref: true, cf_42: true }
let currentFilterFacetId = null; // the facet currently loaded in the filter panel
// Custom field metadata per facet: { [facetId]: [{ id, displayName, customFieldName }] }
let customFieldsMetadata = {};

/** Layers that escape .filter-panel-content overflow via fixed positioning */
const FILTER_DROPDOWN_LAYER_SELECTOR = '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results';

// Make available globally (re-assign window.* whenever the local let is replaced — same object ref must stay in sync)
function syncFilterMetadataGlobals() {
    if (typeof window !== 'undefined') {
        window.activeFilters = activeFilters;
        window.filterFieldsMetadata = filterFieldsMetadata;
        window.activeSearchFields = activeSearchFields;
        window.customFieldsMetadata = customFieldsMetadata;
    }
}
if (typeof window !== 'undefined') {
    syncFilterMetadataGlobals();
}

/**
 * Load filter fields metadata for a given facet.
 * Also resets activeFilters and re-renders the field selector when the facet changes.
 */
async function loadFilterFields(facetId) {
    if (!facetId) {
        console.warn('[Filters] No facet ID provided');
        return;
    }

    // If the facet changed, clear existing filter rows so stale filters from a
    // previous facet are not carried over to the new one.
    if (currentFilterFacetId && currentFilterFacetId !== facetId) {
        const container = document.getElementById('filterRowsContainer');
        if (container) container.innerHTML = '';
        activeFilters = [];
        window.activeFilters = activeFilters;
        if (typeof updateFilterBadge === 'function') updateFilterBadge();
        if (typeof renderActiveFiltersChips === 'function') renderActiveFiltersChips();
    }

    currentFilterFacetId = facetId;
    if (typeof window !== 'undefined') {
        window.currentFilterFacetId = facetId;
    }
    console.log('[FilterApply][DEBUG] loadFilterFields', { facetId, windowCurrentFilterFacetId: typeof window !== 'undefined' ? window.currentFilterFacetId : null });

    try {
        // Fetch static filter fields and custom field metadata in parallel
        const [filterResponse, cfResponse] = await Promise.all([
            fetch(`/UnisonSearch/api/filter-fields?facetId=${facetId}`),
            fetch(`/api/custom-fields/metadata?facetId=${facetId}`)
        ]);

        if (!filterResponse.ok) {
            console.error('[Filters] Failed to load filter fields:', filterResponse.status);
            filterFieldsMetadata = {};
            syncFilterMetadataGlobals();
            return;
        }

        const data = await filterResponse.json();
        filterFieldsMetadata = data;
        syncFilterMetadataGlobals();

        // Backfill facetId on filter rows created before facetId was stored (stable Apply resolution).
        if (data.facetId && Array.isArray(activeFilters)) {
            activeFilters.forEach((f) => {
                if (!f.facetId) {
                    f.facetId = data.facetId;
                }
            });
            window.activeFilters = activeFilters;
        }

        // Store custom field metadata — endpoint returns { success, data: [...] }
        if (cfResponse.ok) {
            try {
                const cfData = await cfResponse.json();
                // Only keep searchable fields so non-searchable CFs don't appear
                const cfList = (cfData.data || []).filter(cf => cf.searchable !== false);
                customFieldsMetadata[facetId] = cfList;
                window.customFieldsMetadata = customFieldsMetadata;
            } catch (e) {
                customFieldsMetadata[facetId] = [];
            }
        } else {
            customFieldsMetadata[facetId] = [];
        }

        // Render the "Search in" field selector for the new facet
        renderSearchFieldSelector(facetId);

        // Update "Add new filter" dropdown with available fields
        updateAddFilterDropdown();
        
        // Initialize quick filters
        if (typeof initQuickFilters === 'function') {
            initQuickFilters(facetId);
        }
        
    } catch (error) {
        console.error('[Filters] Error loading filter fields:', error);
        filterFieldsMetadata = {};
        syncFilterMetadataGlobals();
    }
}

/**
 * Render (or re-render) the "Search in" checkboxes at the top of the filter panel.
 * Users can select which fields (Name, Ref, etc.) their keyword searches against.
 */
function renderSearchFieldSelector(facetId) {
    const panel = document.getElementById('filterPanel');
    if (!panel) return;

    // Remove any existing selector
    const existing = panel.querySelector('.search-field-selector-section');
    if (existing) existing.remove();

    // Resolve the category module from the facet id so we can read CATEGORY_FIELD_MAPPING
    const category = typeof facetIdToCategory === 'function' ? facetIdToCategory(facetId) : null;
    const fields = (typeof getCategoryFields === 'function' && category)
        ? getCategoryFields(category)
        : {};

    // Build a list of { key, label } for checkable fields.
    // The order here determines the display order in the "Search in" row.
    const searchableKeys = [
        'name', 'ref', 'longName', 'shortName',
        'definition', 'usage', 'businessLogic', 'assetId', 'aliasName',
        'description', 'firstName', 'lastName', 'email', 'summary',
        'title', 'parent', 'objectType', 'object', 'owner'
    ];
    const fieldEntries = [];
    searchableKeys.forEach(key => {
        if (fields[key] !== undefined) {
            const raw = fields[key];
            let label;
            if (Array.isArray(raw)) {
                label = raw.join(' / ');
            } else {
                // Convert snake_case / camelCase display labels to readable form
                label = String(raw).replace(/_/g, ' ');
            }
            fieldEntries.push({ key, label });
        }
    });

    // Append custom field entries (keyed as "cf_<id>") after the static ones
    const cfList = (window.customFieldsMetadata && window.customFieldsMetadata[facetId]) || [];
    cfList.forEach(cf => {
        const cfKey = 'cf_' + cf.id;
        const cfLabel = cf.displayName || cf.customFieldName || ('CF ' + cf.id);
        fieldEntries.push({ key: cfKey, label: cfLabel, isCf: true });
    });

    // If there are no fields to show, don't render the selector
    if (fieldEntries.length === 0) return;

    // Initialise activeSearchFields: keep previous selection if same facet, else default all ON
    const prevFields = window.activeSearchFields || {};
    const newFields = {};
    fieldEntries.forEach(({ key }) => {
        // Default: true (all on); preserve user selection if it was set for this facet
        newFields[key] = (prevFields[key] !== undefined) ? prevFields[key] : true;
    });
    activeSearchFields = newFields;
    window.activeSearchFields = activeSearchFields;

    // Build the DOM section
    const section = document.createElement('div');
    section.className = 'search-field-selector-section';

    const sectionTitle = document.createElement('div');
    sectionTitle.className = 'search-field-selector-title';
    sectionTitle.textContent = 'Search in:';
    section.appendChild(sectionTitle);

    const checkboxesRow = document.createElement('div');
    checkboxesRow.className = 'search-field-selector-checkboxes';

    fieldEntries.forEach(({ key, label, isCf }) => {
        const item = document.createElement('label');
        item.className = 'search-field-selector-item' + (isCf ? ' search-field-selector-item--cf' : '');

        const cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.className = 'search-field-selector-cb';
        cb.checked = activeSearchFields[key] !== false;
        cb.setAttribute('data-field-key', key);
        cb.addEventListener('change', () => {
            activeSearchFields[key] = cb.checked;
            window.activeSearchFields = activeSearchFields;
        });

        const span = document.createElement('span');
        span.textContent = label;

        item.appendChild(cb);
        item.appendChild(span);
        checkboxesRow.appendChild(item);
    });

    section.appendChild(checkboxesRow);

    // Insert at the top of the filter panel content, before any other content
    const panelContent = panel.querySelector('.filter-panel-content');
    if (panelContent) {
        panelContent.insertBefore(section, panelContent.firstChild);
    } else {
        panel.insertBefore(section, panel.firstChild);
    }
}

/**
 * Returns the current activeSearchFields map ({ name: true, ref: false, ... }).
 * Returns null if no facet has been loaded yet (meaning: use all fields by default).
 */
function getActiveSearchFields() {
    if (Object.keys(activeSearchFields).length === 0) return null;
    return activeSearchFields;
}

/**
 * Search fields sent to the API when the filter panel has never been opened.
 * Backend treats missing searchFields as "keyword OR every searchable column + every custom field",
 * which produces false positives (e.g. People keyword "Sally" matching unrelated CF text).
 * This returns only static facet columns from CATEGORY_FIELD_MAPPING (no cf_* keys).
 */
function getEffectiveSearchFieldsForCategory(category) {
    if (!category || typeof getCategoryFields !== 'function') {
        return null;
    }
    const raw = activeSearchFields;
    if (raw && Object.keys(raw).length > 0) {
        return { ...raw };
    }
    const fields = getCategoryFields(category);
    if (!fields || typeof fields !== 'object') {
        return null;
    }
    const out = {};
    Object.keys(fields).forEach((k) => {
        if (!k.startsWith('cf_')) {
            out[k] = true;
        }
    });
    return Object.keys(out).length > 0 ? out : null;
}

/**
 * Update the "Add new filter" dropdown with available filter fields
 */
function updateAddFilterDropdown() {
    const addNewOptions = document.getElementById('filterAddNewOptions');
    if (!addNewOptions) {
        return;
    }
    
    // Clear existing options
    addNewOptions.innerHTML = '';
    
    if (!filterFieldsMetadata.filterFields || filterFieldsMetadata.filterFields.length === 0) {
        const noFiltersOption = document.createElement('div');
        noFiltersOption.className = 'filter-dropdown-option disabled';
        noFiltersOption.textContent = 'No filters available';
        addNewOptions.appendChild(noFiltersOption);
        return;
    }
    
    // Add options for each available filter
    filterFieldsMetadata.filterFields.forEach(field => {
        // Check if this filter is already added
        const isAlreadyAdded = activeFilters.some(f => f.fieldId === field.id);
        
        const option = document.createElement('div');
        option.className = 'filter-dropdown-option';
        option.setAttribute('data-value', field.id);
        option.setAttribute('data-field-name', field.name);
        option.setAttribute('data-field-type', field.type);
        option.textContent = field.name;
        
        if (isAlreadyAdded) {
            option.classList.add('disabled');
            option.style.opacity = '0.5';
            option.style.cursor = 'not-allowed';
        } else {
            option.addEventListener('click', (e) => {
                e.stopPropagation();
                // Close the dropdown after selection
                const addNewOptions = document.getElementById('filterAddNewOptions');
                if (addNewOptions) {
                    resetFilterDropdownFloating(addNewOptions);
                    addNewOptions.style.display = 'none';
                }
                addFilterRow(field);
            });
        }
        
        addNewOptions.appendChild(option);
    });
    
}

/**
 * Add a new filter row to the UI
 */
function addFilterRow(filterField) {
    const container = document.getElementById('filterRowsContainer');
    if (!container) {
        console.error('[Filters] Filter rows container not found');
        return;
    }
    
    const filterId = `filter-${filterIdCounter++}`;
    
    // Create filter row
    const filterRow = document.createElement('div');
    filterRow.className = 'filter-row active-filter-row';
    filterRow.setAttribute('data-filter-id', filterId);
    
    // Create collapse button
    const collapseBtn = document.createElement('button');
    collapseBtn.className = 'filter-collapse-btn';
    collapseBtn.innerHTML = '<i class="fas fa-chevron-down"></i>';
    collapseBtn.title = 'Collapse/Expand filter';
    collapseBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        filterRow.classList.toggle('filter-row-collapsed');
    });
    filterRow.appendChild(collapseBtn);
    
    // Create left column - filter name
    const leftCol = document.createElement('div');
    leftCol.className = 'filter-field filter-field-name';
    
    const nameButton = document.createElement('button');
    nameButton.className = 'filter-dropdown-btn filter-name-btn';
    nameButton.innerHTML = `<span>${filterField.name}</span><i class="fas fa-chevron-down"></i>`;
    nameButton.addEventListener('click', (e) => {
        e.stopPropagation();
        showFieldChangerDropdown(nameButton, filterId);
    });
    leftCol.appendChild(nameButton);
    
    // Create right column - filter value selector
    const rightCol = document.createElement('div');
    rightCol.className = 'filter-field filter-field-value';
    
    // Create value selector based on filter type
    const valueSelector = createValueSelector(filterField, filterId);
    rightCol.appendChild(valueSelector);
    
    // Add remove button
    const removeBtn = document.createElement('button');
    removeBtn.className = 'filter-remove-btn';
    removeBtn.innerHTML = '<i class="fas fa-times"></i>';
    removeBtn.title = 'Remove filter';
    removeBtn.addEventListener('click', () => {
        removeFilter(filterId);
    });
    rightCol.appendChild(removeBtn);
    
    filterRow.appendChild(leftCol);
    filterRow.appendChild(rightCol);
    
    container.appendChild(filterRow);
    
    // Remember which facet this row belongs to — window.currentFilterFacetId can be
    // overwritten later when the sidebar switches (e.g. Unison resets to FIND facet).
    const facetForRow = (filterFieldsMetadata && filterFieldsMetadata.facetId)
        ? filterFieldsMetadata.facetId
        : currentFilterFacetId;

    // Add to active filters
    activeFilters.push({
        id: filterId,
        fieldId: filterField.id,
        fieldName: filterField.name,
        fieldType: filterField.type,
        fieldColumn: filterField.fieldName,
        value: null,
        facetId: facetForRow || null
    });
    window.activeFilters = activeFilters;
    
    // Update dropdown to mark this filter as added
    updateAddFilterDropdown();
    
    // Update active filters chips
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    
    // Update badge
    if (typeof updateFilterBadge === 'function') {
        updateFilterBadge();
    }
}

/**
 * Create value selector UI based on filter type
 */
function createValueSelector(filterField, filterId) {
    const container = document.createElement('div');
    container.className = 'filter-value-selector';
    container.setAttribute('data-filter-id', filterId);
    
    switch (filterField.type) {
        case 'DROPDOWN':
            return createDropdownValueSelector(filterField, filterId);
        case 'DATE_RANGE':
            return createDateRangeValueSelector(filterField, filterId);
        case 'PEOPLE':
            return createPeopleValueSelector(filterField, filterId);
        case 'BOOLEAN':
            return createBooleanValueSelector(filterField, filterId);
        case 'TEXT':
            return createTextValueSelector(filterField, filterId);
        default:
            return createTextValueSelector(filterField, filterId);
    }
}

/**
 * Create dropdown value selector with checkboxes
 */
function createDropdownValueSelector(filterField, filterId) {
    const container = document.createElement('div');
    container.className = 'filter-dropdown-value-container';
    
    const button = document.createElement('button');
    button.className = 'filter-dropdown-btn filter-value-btn';
    button.setAttribute('data-filter-id', filterId);
    button.innerHTML = '<span>Select options</span><i class="fas fa-chevron-down"></i>';
    
    const dropdown = document.createElement('div');
    dropdown.className = 'filter-dropdown-values';
    dropdown.style.display = 'none';
    dropdown.setAttribute('data-filter-id', filterId);
    
    // Add search box for filtering values
    const searchBox = document.createElement('input');
    searchBox.type = 'text';
    searchBox.className = 'filter-values-search';
    searchBox.placeholder = 'Search...';
    dropdown.appendChild(searchBox);
    
    // Add loading message
    const loadingMsg = document.createElement('div');
    loadingMsg.className = 'filter-values-loading';
    loadingMsg.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Loading...';
    dropdown.appendChild(loadingMsg);
    
    // Add values container
    const valuesContainer = document.createElement('div');
    valuesContainer.className = 'filter-values-list';
    dropdown.appendChild(valuesContainer);
    
    container.appendChild(button);
    container.appendChild(dropdown);
    
    // Toggle dropdown on button click
    button.addEventListener('click', async (e) => {
        e.stopPropagation();
        
        document.querySelectorAll(FILTER_DROPDOWN_LAYER_SELECTOR).forEach((d) => {
            if (d !== dropdown) {
                resetFilterDropdownFloating(d);
                d.style.display = 'none';
            }
        });
        
        const isVisible = dropdown.style.display !== 'none';
        if (isVisible) {
            resetFilterDropdownFloating(dropdown);
            dropdown.style.display = 'none';
        } else {
            dropdown.style.display = 'block';
            positionFilterDropdownFloating(dropdown, button);
            initFilterDropdownFloatingListeners();
            requestAnimationFrame(() => refreshFloatingFilterDropdowns());
        }
        
        // Load values if not loaded yet
        if (!isVisible && valuesContainer.children.length === 0) {
            await loadDropdownValues(filterField, filterId, valuesContainer, loadingMsg);
        }
        
        // Update highlights when opening
        if (!isVisible) {
            setTimeout(() => {
                if (typeof updateValueHighlight === 'function') {
                    updateValueHighlight(filterId, filterField);
                }
            }, 100);
        }
    });
    
    // Prevent clicks inside dropdown from closing it
    dropdown.addEventListener('click', (e) => {
        e.stopPropagation();
    });
    
    // Search functionality
    searchBox.addEventListener('input', (e) => {
        const searchTerm = e.target.value.toLowerCase();
        const items = valuesContainer.querySelectorAll('.filter-value-item');
        items.forEach(item => {
            const text = item.textContent.toLowerCase();
            item.style.display = text.includes(searchTerm) ? 'flex' : 'none';
        });
    });
    
    return container;
}

/**
 * Load dropdown values from API
 */
async function loadDropdownValues(filterField, filterId, container, loadingMsg) {
    try {
        loadingMsg.style.display = 'block';
        
        const response = await fetch(filterField.valuesEndpoint || 
            `/UnisonSearch/api/filter-values/${filterField.id}?facetId=${filterFieldsMetadata.facetId}`);
        
        if (!response.ok) {
            console.error('[Filters] Failed to load values:', response.status);
            container.innerHTML = '<div class="filter-error">Failed to load values</div>';
            loadingMsg.style.display = 'none';
            return;
        }
        
        const data = await response.json();
        loadingMsg.style.display = 'none';
        
        if (!data.values || data.values.length === 0) {
            container.innerHTML = '<div class="filter-no-values">No values available</div>';
            return;
        }
        
        // Clear container
        container.innerHTML = '';
        
        // Add Select All / Deselect All buttons
        const actionsDiv = document.createElement('div');
        actionsDiv.className = 'filter-value-actions';
        const selectAllBtn = document.createElement('button');
        selectAllBtn.className = 'filter-action-btn';
        selectAllBtn.textContent = 'Select All';
        selectAllBtn.addEventListener('click', () => {
            const checkboxes = container.querySelectorAll(`input[type="checkbox"][data-filter-id="${filterId}"]`);
            checkboxes.forEach(cb => cb.checked = true);
            updateFilterValue(filterId, filterField);
        });
        const deselectAllBtn = document.createElement('button');
        deselectAllBtn.className = 'filter-action-btn';
        deselectAllBtn.textContent = 'Deselect All';
        deselectAllBtn.addEventListener('click', () => {
            const checkboxes = container.querySelectorAll(`input[type="checkbox"][data-filter-id="${filterId}"]`);
            checkboxes.forEach(cb => cb.checked = false);
            updateFilterValue(filterId, filterField);
        });
        actionsDiv.appendChild(selectAllBtn);
        actionsDiv.appendChild(deselectAllBtn);
        container.appendChild(actionsDiv);
        
        // Filter out zero-count values (optional - can be toggled)
        const showZeroCount = false; // Set to true to show all values
        const filteredValues = showZeroCount 
            ? data.values 
            : data.values.filter(v => !v.count || v.count > 0);
        
        // Add checkbox for each value
        filteredValues.forEach(value => {
            const item = document.createElement('div');
            item.className = 'filter-value-item';
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = `${filterId}-value-${value.id}`;
            checkbox.value = value.id;
            checkbox.setAttribute('data-filter-id', filterId);
            checkbox.addEventListener('change', () => {
                updateFilterValue(filterId, filterField);
            });
            
            const label = document.createElement('label');
            label.htmlFor = checkbox.id;
            label.textContent = value.name;
            if (value.count !== undefined && value.count !== null) {
                label.textContent += ` (${value.count})`;
            }
            
            // Update highlight on change
            checkbox.addEventListener('change', () => {
                updateFilterValue(filterId, filterField);
                updateValueHighlight(filterId, filterField);
            });
            
            item.appendChild(checkbox);
            item.appendChild(label);
            container.appendChild(item);
        });

        // Restore selection from quick filter / saved value and capture display labels
        const filterAfterLoad = activeFilters.find(f => f.id === filterId);
        if (filterAfterLoad && filterAfterLoad.fieldType === 'DROPDOWN' && Array.isArray(filterAfterLoad.value)) {
            container.querySelectorAll(`input[type="checkbox"][data-filter-id="${filterId}"]`).forEach(cb => {
                cb.checked = filterAfterLoad.value.includes(parseInt(cb.value, 10));
            });
            updateFilterValue(filterId, filterField);
        }

        requestAnimationFrame(() => refreshFloatingFilterDropdowns());
        
    } catch (error) {
        console.error('[Filters] Error loading dropdown values:', error);
        container.innerHTML = '<div class="filter-error">Error loading values</div>';
        loadingMsg.style.display = 'none';
    }
}

/**
 * Update value highlight
 */
function updateValueHighlight(filterId, filterField) {
    const filter = activeFilters.find(f => f.id === filterId);
    if (!filter) return;
    
    const container = document.querySelector(`.filter-dropdown-values[data-filter-id="${filterId}"]`);
    if (!container) return;
    
    const items = container.querySelectorAll('.filter-value-item');
    items.forEach(item => {
        const checkbox = item.querySelector('input[type="checkbox"]');
        if (!checkbox) return;
        
        const value = parseInt(checkbox.value);
        if (filter.value && Array.isArray(filter.value) && filter.value.includes(value)) {
            item.classList.add('filter-value-selected');
        } else {
            item.classList.remove('filter-value-selected');
        }
    });
}

/**
 * Update filter value when checkboxes change
 */
function updateFilterValue(filterId, filterField) {
    const filter = activeFilters.find(f => f.id === filterId);
    if (!filter) {
        return;
    }
    
    const checkboxes = document.querySelectorAll(`input[type="checkbox"][data-filter-id="${filterId}"]:checked`);
    const selectedValues = Array.from(checkboxes).map(cb => parseInt(cb.value, 10));
    const selectedLabels = Array.from(checkboxes).map(cb => {
        const lab = cb.nextElementSibling;
        if (lab && lab.textContent) {
            return lab.textContent.split(' (')[0].trim();
        }
        return String(cb.value);
    });

    filter.value = selectedValues.length > 0 ? selectedValues : null;
    // Human-readable labels for query bar / chips (parallel to filter.value IDs)
    if (filterField && filterField.type === 'DROPDOWN' && selectedValues.length > 0) {
        filter.dropdownLabels = selectedLabels;
    } else {
        delete filter.dropdownLabels;
    }
    // Manual checkbox change overrides quick-filter display string
    if (filter.quickFilterLabel) {
        delete filter.quickFilterLabel;
    }
    window.activeFilters = activeFilters;
    
    // Update button text
    const button = document.querySelector(`.filter-value-btn[data-filter-id="${filterId}"]`);
    if (button && button.querySelector('span')) {
        if (selectedValues.length === 0) {
            button.querySelector('span').textContent =
                filterField && filterField.type === 'BOOLEAN' ? 'Select option' : 'Select options';
        } else if (selectedValues.length === 1) {
            // Show the selected value name
            const checkbox = document.querySelector(`input[value="${selectedValues[0]}"][data-filter-id="${filterId}"]`);
            const label = checkbox ? checkbox.nextElementSibling : null;
            const valueName = label ? label.textContent.split(' (')[0] : selectedValues[0];
            button.querySelector('span').textContent = valueName;
        } else {
            button.querySelector('span').textContent = `${selectedValues.length} selected`;
        }
    }
    
    // Update highlights
    updateValueHighlight(filterId, filterField);
    
    console.log('[Filters] Updated filter value:', filterId, selectedValues);
    
    // Update active filters chips
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    
    // Update badge
    if (typeof updateFilterBadge === 'function') {
        updateFilterBadge();
    }
}

/**
 * Create date range value selector
 */
function createDateRangeValueSelector(filterField, filterId) {
    const container = document.createElement('div');
    container.className = 'filter-date-range';
    
    // Quick Date Presets
    const presetsContainer = document.createElement('div');
    presetsContainer.className = 'date-presets';
    const presets = [
        { label: 'Today', days: 0 },
        { label: 'This Week', days: 7 },
        { label: 'This Month', days: 30 },
        { label: 'Last 30 Days', days: 30, fromToday: false },
        { label: 'This Year', days: 365 }
    ];
    
    presets.forEach(preset => {
        const btn = document.createElement('button');
        btn.className = 'date-preset-btn';
        btn.textContent = preset.label;
        btn.addEventListener('click', () => {
            const today = new Date();
            const fromDate = new Date(today);
            if (preset.fromToday === false) {
                fromDate.setDate(today.getDate() - preset.days);
            } else if (preset.days === 0) {
                // Today only
                fromDate.setDate(today.getDate());
            } else if (preset.days === 365) {
                // This year
                fromDate.setMonth(0, 1);
            } else {
                fromDate.setDate(today.getDate() - preset.days);
            }
            
            const fromInput = container.querySelector(`input[data-date-type="from"]`);
            const toInput = container.querySelector(`input[data-date-type="to"]`);
            
            if (fromInput) {
                fromInput.value = fromDate.toISOString().split('T')[0];
            }
            if (toInput && preset.days !== 0) {
                toInput.value = today.toISOString().split('T')[0];
            }
            
            updateDateRangeValue(filterId);
        });
        presetsContainer.appendChild(btn);
    });
    container.appendChild(presetsContainer);
    
    // From date
    const fromLabel = document.createElement('span');
    fromLabel.textContent = 'FROM';
    fromLabel.className = 'filter-date-label';
    
    const fromInput = document.createElement('input');
    fromInput.type = 'date';
    fromInput.className = 'filter-date-input';
    fromInput.setAttribute('data-filter-id', filterId);
    fromInput.setAttribute('data-date-type', 'from');
    fromInput.addEventListener('change', () => {
        updateDateRangeValue(filterId);
    });
    
    const fromButton = document.createElement('button');
    fromButton.className = 'filter-date-btn';
    fromButton.innerHTML = '<i class="fas fa-calendar"></i>';
    fromButton.addEventListener('click', () => {
        fromInput.showPicker();
    });
    
    // To date
    const toLabel = document.createElement('span');
    toLabel.textContent = 'TO';
    toLabel.className = 'filter-date-label';
    
    const toInput = document.createElement('input');
    toInput.type = 'date';
    toInput.className = 'filter-date-input';
    toInput.setAttribute('data-filter-id', filterId);
    toInput.setAttribute('data-date-type', 'to');
    toInput.addEventListener('change', () => {
        updateDateRangeValue(filterId);
    });
    
    const toButton = document.createElement('button');
    toButton.className = 'filter-date-btn';
    toButton.innerHTML = '<i class="fas fa-calendar"></i>';
    toButton.addEventListener('click', () => {
        toInput.showPicker();
    });
    
    container.appendChild(fromLabel);
    container.appendChild(fromInput);
    container.appendChild(fromButton);
    container.appendChild(toLabel);
    container.appendChild(toInput);
    container.appendChild(toButton);
    
    return container;
}

/**
 * Update date range filter value
 */
function updateDateRangeValue(filterId) {
    const filter = activeFilters.find(f => f.id === filterId);
    if (!filter) {
        return;
    }
    
    const fromInput = document.querySelector(`input[data-filter-id="${filterId}"][data-date-type="from"]`);
    const toInput = document.querySelector(`input[data-filter-id="${filterId}"][data-date-type="to"]`);
    
    const fromValue = fromInput ? fromInput.value : null;
    const toValue = toInput ? toInput.value : null;
    
    if (fromValue || toValue) {
        filter.value = {
            from: fromValue,
            to: toValue
        };
    } else {
        filter.value = null;
    }
    
    window.activeFilters = activeFilters;
}

/**
 * Create people search value selector
 */
function createPeopleValueSelector(filterField, filterId) {
    const container = document.createElement('div');
    container.className = 'filter-people-search';
    
    const searchInput = document.createElement('input');
    searchInput.type = 'text';
    searchInput.className = 'filter-people-input';
    searchInput.placeholder = 'Search and add people...';
    searchInput.setAttribute('data-filter-id', filterId);
    
    const resultsDropdown = document.createElement('div');
    resultsDropdown.className = 'filter-people-results';
    resultsDropdown.style.display = 'none';
    resultsDropdown.setAttribute('data-filter-id', filterId);
    
    const selectedPeople = document.createElement('div');
    selectedPeople.className = 'filter-selected-people';
    selectedPeople.setAttribute('data-filter-id', filterId);
    
    container.appendChild(searchInput);
    container.appendChild(resultsDropdown);
    container.appendChild(selectedPeople);
    
    // Search functionality
    let searchTimeout;
    searchInput.addEventListener('input', (e) => {
        clearTimeout(searchTimeout);
        const query = e.target.value.trim();
        
        if (query.length < 2) {
            resetFilterDropdownFloating(resultsDropdown);
            resultsDropdown.style.display = 'none';
            return;
        }
        
        searchTimeout = setTimeout(async () => {
            await searchPeople(query, filterId, resultsDropdown);
        }, 300);
    });
    
    // Close dropdown when clicking outside (use event delegation to avoid memory leaks)
    const closeHandler = (e) => {
        if (!container.contains(e.target)) {
            resetFilterDropdownFloating(resultsDropdown);
            resultsDropdown.style.display = 'none';
        }
    };
    
    // Store handler reference for cleanup
    container._closeHandler = closeHandler;
    document.addEventListener('click', closeHandler);
    
    return container;
}

/**
 * Search for people
 */
async function searchPeople(query, filterId, resultsContainer) {
    try {
        console.log('[Filters] Searching people:', query);
        
        const response = await fetch(`/UnisonSearch/api/people/search?query=${encodeURIComponent(query)}&limit=10`);
        
        if (!response.ok) {
            console.error('[Filters] People search failed:', response.status);
            return;
        }
        
        const data = await response.json();
        
        resultsContainer.innerHTML = '';
        
        if (!data || data.length === 0) {
            resultsContainer.innerHTML = '<div class="filter-no-results">No people found</div>';
            resultsContainer.style.display = 'block';
            const inputEl = document.querySelector(`.filter-people-input[data-filter-id="${filterId}"]`);
            if (inputEl) {
                positionFilterDropdownFloating(resultsContainer, inputEl);
                initFilterDropdownFloatingListeners();
                requestAnimationFrame(() => refreshFloatingFilterDropdowns());
            }
            return;
        }
        
        data.forEach(person => {
            const item = document.createElement('div');
            item.className = 'filter-people-item';
            item.textContent = person.name || `${person.First_Name} ${person.Last_Name}`;
            item.setAttribute('data-person-id', person.ID || person.id);
            item.addEventListener('click', () => {
                selectPerson(filterId, person);
                resetFilterDropdownFloating(resultsContainer);
                resultsContainer.style.display = 'none';
            });
            resultsContainer.appendChild(item);
        });
        
        resultsContainer.style.display = 'block';
        const inputEl = document.querySelector(`.filter-people-input[data-filter-id="${filterId}"]`);
        if (inputEl) {
            positionFilterDropdownFloating(resultsContainer, inputEl);
            initFilterDropdownFloatingListeners();
            requestAnimationFrame(() => refreshFloatingFilterDropdowns());
        }
        
    } catch (error) {
        console.error('[Filters] Error searching people:', error);
    }
}

/**
 * Select a person for the filter
 */
function selectPerson(filterId, person) {
    const filter = activeFilters.find(f => f.id === filterId);
    if (!filter) {
        return;
    }
    
    // Initialize value as array if not exists
    if (!Array.isArray(filter.value)) {
        filter.value = [];
    }
    
    const personId = person.ID || person.id;
    const personName = person.name || `${person.First_Name} ${person.Last_Name}`;
    
    // Check if already added
    if (filter.value.some(p => p.id === personId)) {
        return;
    }
    
    filter.value.push({
        id: personId,
        name: personName
    });
    
    window.activeFilters = activeFilters;
    
    // Update UI
    const selectedContainer = document.querySelector(`.filter-selected-people[data-filter-id="${filterId}"]`);
    if (selectedContainer) {
        addSelectedPersonChip(selectedContainer, filterId, personId, personName);
    }
    
    // Clear search input
    const searchInput = document.querySelector(`.filter-people-input[data-filter-id="${filterId}"]`);
    if (searchInput) {
        searchInput.value = '';
    }
}

/**
 * Add a chip for selected person
 */
function addSelectedPersonChip(container, filterId, personId, personName) {
    const chip = document.createElement('div');
    chip.className = 'filter-person-chip';
    chip.setAttribute('data-person-id', personId);
    
    const nameSpan = document.createElement('span');
    nameSpan.textContent = personName;
    
    const removeBtn = document.createElement('button');
    removeBtn.className = 'filter-chip-remove';
    removeBtn.innerHTML = '<i class="fas fa-times"></i>';
    removeBtn.addEventListener('click', () => {
        removePerson(filterId, personId, chip);
    });
    
    chip.appendChild(nameSpan);
    chip.appendChild(removeBtn);
    container.appendChild(chip);
}

/**
 * Remove a person from the filter
 */
function removePerson(filterId, personId, chipElement) {
    const filter = activeFilters.find(f => f.id === filterId);
    if (filter && Array.isArray(filter.value)) {
        filter.value = filter.value.filter(p => p.id !== personId);
        if (filter.value.length === 0) {
            filter.value = null;
        }
        window.activeFilters = activeFilters;
    }
    
    chipElement.remove();
    console.log('[Filters] Removed person:', personId, 'from filter:', filterId);
}

/**
 * Create text value selector
 */
function createBooleanValueSelector(filterField, filterId) {
    const container = document.createElement('div');
    container.className = 'filter-dropdown-value-container';
    
    const button = document.createElement('button');
    button.className = 'filter-dropdown-btn filter-value-btn';
    button.setAttribute('data-filter-id', filterId);
    button.innerHTML = '<span>Select option</span><i class="fas fa-chevron-down"></i>';
    
    const dropdown = document.createElement('div');
    dropdown.className = 'filter-dropdown-values';
    dropdown.style.display = 'none';
    dropdown.setAttribute('data-filter-id', filterId);
    
    // Add Yes/No checkboxes
    const options = [
        { id: '1', name: 'Yes' },
        { id: '0', name: 'No' }
    ];
    
    options.forEach(option => {
        const item = document.createElement('div');
        item.className = 'filter-value-item';
        
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.id = `${filterId}-value-${option.id}`;
        checkbox.value = option.id;
        checkbox.setAttribute('data-filter-id', filterId);
        checkbox.addEventListener('change', () => {
            // Single choice: Yes xor No (boolean is not multi-select)
            if (checkbox.checked) {
                dropdown.querySelectorAll(`input[type="checkbox"][data-filter-id="${filterId}"]`).forEach((cb) => {
                    if (cb !== checkbox) cb.checked = false;
                });
            }
            updateFilterValue(filterId, filterField);
            // Close immediately after picking a value (or clearing the only selection)
            resetFilterDropdownFloating(dropdown);
            dropdown.style.display = 'none';
        });
        
        const label = document.createElement('label');
        label.htmlFor = checkbox.id;
        label.textContent = option.name;
        
        item.appendChild(checkbox);
        item.appendChild(label);
        dropdown.appendChild(item);
    });
    
    // Toggle dropdown
    button.addEventListener('click', (e) => {
        e.stopPropagation();
        
        document.querySelectorAll(FILTER_DROPDOWN_LAYER_SELECTOR).forEach((opt) => {
            if (opt !== dropdown) {
                resetFilterDropdownFloating(opt);
                opt.style.display = 'none';
            }
        });
        
        const isVisible = dropdown.style.display !== 'none';
        if (isVisible) {
            resetFilterDropdownFloating(dropdown);
            dropdown.style.display = 'none';
        } else {
            dropdown.style.display = 'block';
            positionFilterDropdownFloating(dropdown, button);
            initFilterDropdownFloatingListeners();
            requestAnimationFrame(() => refreshFloatingFilterDropdowns());
        }
    });
    
    container.appendChild(button);
    container.appendChild(dropdown);
    
    return container;
}

function createTextValueSelector(filterField, filterId) {
    const input = document.createElement('input');
    input.type = 'text';
    input.className = 'filter-text-input';
    input.placeholder = `Enter ${filterField.name.toLowerCase()}...`;
    input.setAttribute('data-filter-id', filterId);
    input.addEventListener('input', (e) => {
        const filter = activeFilters.find(f => f.id === filterId);
        if (filter) {
            filter.value = e.target.value.trim() || null;
            window.activeFilters = activeFilters;
        }
    });
    
    return input;
}

/**
 * Remove a filter row
 */
function removeFilterRow(filterId) {
    const filterRow = document.querySelector(`.filter-row[data-filter-id="${filterId}"]`);
    if (filterRow) {
        filterRow.remove();
    }
    
    // Remove from active filters
    activeFilters = activeFilters.filter(f => f.id !== filterId);
    window.activeFilters = activeFilters;
    
    // Update add filter dropdown
    updateAddFilterDropdown();
    
    // Update chips and badge
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    if (typeof updateFilterBadge === 'function') {
        updateFilterBadge();
    }
}

/**
 * Clear all filters
 */
function clearAllFilters() {
    console.log('[Filters] Clearing all filters');
    
    // Remove all filter rows from DOM
    const container = document.getElementById('filterRowsContainer');
    if (container) {
        container.innerHTML = '';
    }
    
    // Clear active filters array
    activeFilters = [];
    window.activeFilters = activeFilters;
    
    // Update add filter dropdown
    updateAddFilterDropdown();
    
    // Update active filters chips
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    
    // Update badge
    if (typeof updateFilterBadge === 'function') {
        updateFilterBadge();
    }

    // Remove filter-created search conditions so the counter and query builder stay in sync
    if (typeof searchConditions !== 'undefined' && Array.isArray(searchConditions)) {
        searchConditions = searchConditions.filter(c => !c.isFilterCondition);
        if (typeof window !== 'undefined') {
            window.searchConditions = searchConditions;
        }
        if (typeof updateSearchCounter === 'function') {
            updateSearchCounter();
        }
        if (typeof renderSearchConditions === 'function') {
            renderSearchConditions();
        }
    }
}

/**
 * Build filters object for backend
 */
function buildFiltersObject() {
    const filtersObj = {};
    
    activeFilters.forEach(filter => {
        if (filter.value !== null && filter.value !== undefined) {
            // Use filter ID as key (backend will map to column name using FilterMetadataConfig)
            const fieldKey = filter.fieldId;
            
            // Ensure numeric values are integers (not floats) for ID-based filters
            let value = filter.value;
            if (Array.isArray(value)) {
                // Convert array of numbers to integers
                value = value.map(v => {
                    if (typeof v === 'number') {
                        return Math.floor(v); // Convert to integer
                    }
                    const num = parseInt(v, 10);
                    return isNaN(num) ? v : num;
                });
            } else if (typeof value === 'number') {
                value = Math.floor(value); // Convert to integer
            }
            
            filtersObj[fieldKey] = value;
        }
    });
    
    return filtersObj;
}

/**
 * Check if any filters are active
 */
function hasActiveFilters() {
    return activeFilters.some(f => f.value !== null && f.value !== undefined);
}

/**
 * Update filter count badge on filter button
 */
function updateFilterBadge() {
    const filterBtn = document.querySelector('.filter-btn');
    if (!filterBtn) return;
    
    let badge = filterBtn.querySelector('.filter-badge');
    if (!badge) {
        badge = document.createElement('span');
        badge.className = 'filter-badge';
        filterBtn.appendChild(badge);
    }
    
    const count = activeFilters.filter(f => f.value !== null && f.value !== undefined).length;
    if (count > 0) {
        badge.textContent = count;
        badge.classList.add('active');
    } else {
        badge.classList.remove('active');
    }
}

/**
 * Render active filters as chips
 */
function renderActiveFiltersChips() {
    const container = document.getElementById('activeFiltersChips');
    const list = container?.querySelector('.active-filters-list');
    if (!container || !list) return;
    
    const hasFilters = hasActiveFilters();
    container.style.display = hasFilters ? 'block' : 'none';
    
    // Update badge
    updateFilterBadge();
    
    if (!hasFilters) {
        list.innerHTML = '';
        return;
    }
    
    list.innerHTML = '';
    
    activeFilters.forEach(filter => {
        if (filter.value === null || filter.value === undefined) return;
        
        const chip = document.createElement('div');
        chip.className = 'filter-chip';
        
        let displayText = filter.fieldName || filter.fieldId;
        let valueText = '';
        
        if (Array.isArray(filter.value)) {
            if (filter.value.length > 0) {
                if (filter.fieldType === 'PEOPLE' && typeof filter.value[0] === 'object') {
                    valueText = `: ${filter.value.map(p => p.name || p.id).join(', ')}`;
                } else if (filter.fieldType === 'DROPDOWN' && Array.isArray(filter.dropdownLabels)
                        && filter.dropdownLabels.length === filter.value.length) {
                    valueText = `: ${filter.dropdownLabels.join(', ')}`;
                } else if (filter.value.length === 1) {
                    // Try to get the name from checkbox label
                    const checkbox = document.querySelector(`input[value="${filter.value[0]}"][data-filter-id="${filter.id}"]`);
                    if (checkbox && checkbox.nextElementSibling) {
                        const labelText = checkbox.nextElementSibling.textContent.split(' (')[0];
                        valueText = `: ${labelText}`;
                    } else {
                        valueText = `: ${filter.value[0]}`;
                    }
                } else {
                    const fromDom = filter.fieldType === 'DROPDOWN' && filter.value.every(id => {
                        const cb = document.querySelector(`input[type="checkbox"][data-filter-id="${filter.id}"][value="${id}"]`);
                        return cb && cb.nextElementSibling;
                    });
                    if (fromDom) {
                        const names = filter.value.map(id => {
                            const cb = document.querySelector(`input[type="checkbox"][data-filter-id="${filter.id}"][value="${id}"]`);
                            return cb.nextElementSibling.textContent.split(' (')[0].trim();
                        });
                        valueText = `: ${names.join(', ')}`;
                    } else {
                        valueText = `: ${filter.value.length} selected`;
                    }
                }
            }
        } else if (filter.value && typeof filter.value === 'object') {
            // Date range
            if (filter.value.from && filter.value.to) {
                valueText = `: ${filter.value.from} to ${filter.value.to}`;
            } else if (filter.value.from) {
                valueText = `: from ${filter.value.from}`;
            } else if (filter.value.to) {
                valueText = `: until ${filter.value.to}`;
            }
        } else {
            valueText = `: ${filter.value}`;
        }
        
        chip.innerHTML = `
            <span class="filter-chip-text">${displayText}${valueText}</span>
            <button class="filter-chip-remove" data-filter-id="${filter.id}" title="Remove filter">
                <i class="fas fa-times"></i>
            </button>
        `;
        
        // Remove filter on chip click
        chip.querySelector('.filter-chip-remove').addEventListener('click', () => {
            removeFilter(filter.id);
        });
        
        list.appendChild(chip);
    });
}

/**
 * Remove a specific filter row from DOM
 */
function removeFilterRow(filterId) {
    const filterRow = document.querySelector(`.filter-row[data-filter-id="${filterId}"]`);
    if (filterRow) {
        filterRow.remove();
    }
    
    // Remove from active filters
    activeFilters = activeFilters.filter(f => f.id !== filterId);
    window.activeFilters = activeFilters;
    
    // Update add filter dropdown
    updateAddFilterDropdown();
    
    // Update chips and badge
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    if (typeof updateFilterBadge === 'function') {
        updateFilterBadge();
    }
}

/**
 * Remove a specific filter (from chip click - auto-applies)
 */
function removeFilter(filterId) {
    removeFilterRow(filterId);
    
    // Auto-apply if filters changed (when called from chip)
    if (typeof applyFiltersAndSearch === 'function') {
        applyFiltersAndSearch();
    }
}

/**
 * Initialize quick filters
 */
function initQuickFilters(facetId) {
    const section = document.getElementById('quickFiltersSection');
    const buttons = section?.querySelector('.quick-filters-buttons');
    if (!section || !buttons) return;
    
    // Quick filters per facet — values match DB lookup table IDs
    const quickFilters = {
        // dataset_lifecycle: 1=Draft, 2=Approved, 3=Obsolete
        'DATASET': [
            { label: 'Draft',     field: 'lifecycle', value: 1 },
            { label: 'Approved',  field: 'lifecycle', value: 2 },
            { label: 'Obsolete',  field: 'lifecycle', value: 3 },
            { label: 'This Month', field: 'createdDate', type: 'thisMonth' }
        ],
        // glossary_lifecycle: 1=Draft, 2=Being validated, 3=Approved, 4=Obsolete
        'GLOSSARY': [
            { label: 'Draft',           field: 'lifecycle', value: 1 },
            { label: 'Being Validated', field: 'lifecycle', value: 2 },
            { label: 'Approved',        field: 'lifecycle', value: 3 },
            { label: 'Obsolete',        field: 'lifecycle', value: 4 }
        ],
        // system_lifecycle — values loaded from DB at runtime via FilterValuesServlet
        'SYSTEM': [
            { label: 'This Month', field: 'createdDate', type: 'thisMonth' }
        ],
        // process_lifecycle_status: 1=In Production, 2=Draft, 3=Obsolete
        'PROCESS': [
            { label: 'In Production', field: 'lifecycle', value: 1 },
            { label: 'Draft',         field: 'lifecycle', value: 2 },
            { label: 'Obsolete',      field: 'lifecycle', value: 3 }
        ],
        // policy_lifecycle_status: 1=Draft, 2=Enforced, 3=Obsolete
        'POLICY': [
            { label: 'Draft',    field: 'lifecycle', value: 1 },
            { label: 'Enforced', field: 'lifecycle', value: 2 },
            { label: 'Obsolete', field: 'lifecycle', value: 3 }
        ],
        // project_lifecycle: 1=Initiation, 2=Execution, 3=Closed
        'PROJECT': [
            { label: 'Initiation', field: 'lifecycle', value: 1 },
            { label: 'Execution',  field: 'lifecycle', value: 2 },
            { label: 'Closed',     field: 'lifecycle', value: 3 }
        ],
        // capability_lifecyle: 1=In Effect, 3=Obsolete
        'CAPABILITY': [
            { label: 'In Effect', field: 'lifecycle', value: 1 },
            { label: 'Obsolete',  field: 'lifecycle', value: 3 }
        ],
        // client_lifecycle: 1=In Production, 2=Obsolete
        'CLIENT': [
            { label: 'In Production', field: 'lifecycle', value: 1 },
            { label: 'Obsolete',      field: 'lifecycle', value: 2 }
        ],
        // committee_lifecycle: 1=In Effect, 2=Obsolete
        'COMMITTEE': [
            { label: 'In Effect', field: 'lifecycle', value: 1 },
            { label: 'Obsolete',  field: 'lifecycle', value: 2 }
        ],
        // business_area_lifecycle: 1=Operating, 2=Obsolete
        'BUSINESS_AREA': [
            { label: 'Operating', field: 'lifecycle', value: 1 },
            { label: 'Obsolete',  field: 'lifecycle', value: 2 }
        ],
        // product_lifecycle: 1=In Production, 2=Obsolete
        'PRODUCT': [
            { label: 'In Production', field: 'lifecycle', value: 1 },
            { label: 'Obsolete',      field: 'lifecycle', value: 2 }
        ],
        // interface_lifecycle: 1=In Production, 2=Being Decommissioned, 3=Decommissioned
        'INTERFACE': [
            { label: 'In Production',       field: 'lifecycle', value: 1 },
            { label: 'Being Decommissioned',field: 'lifecycle', value: 2 },
            { label: 'Decommissioned',       field: 'lifecycle', value: 3 }
        ],
        // regulation_status: 1=Active, 2=Obsolete, 3=Deleted
        'REGULATION': [
            { label: 'Active',   field: 'status', value: 1 },
            { label: 'Obsolete', field: 'status', value: 2 }
        ],
        // changerequeststatus: 1=Pending Start, 2=Running, 3=Paused, 4=Completed, 5=Cancelled
        'CHANGE_REQUESTS': [
            { label: 'Pending Start', field: 'type', value: 1 },
            { label: 'Running',       field: 'type', value: 2 },
            { label: 'Completed',     field: 'type', value: 4 }
        ],
        'CHANGE_REQUEST': [
            { label: 'Pending Start', field: 'type', value: 1 },
            { label: 'Running',       field: 'type', value: 2 },
            { label: 'Completed',     field: 'type', value: 4 }
        ]
    };
    
    const filters = quickFilters[facetId] || [];
    section.style.display = filters.length > 0 ? 'block' : 'none';
    
    buttons.innerHTML = '';
    filters.forEach(qf => {
        const btn = document.createElement('button');
        btn.className = 'quick-filter-btn';
        btn.textContent = qf.label;
        btn.addEventListener('click', () => applyQuickFilter(qf));
        buttons.appendChild(btn);
    });
}

/**
 * Apply quick filter
 */
function applyQuickFilter(quickFilter) {
    // Find or create filter
    let filter = activeFilters.find(f => f.fieldId === quickFilter.field);
    
    if (!filter) {
        // filterFieldsMetadata shape: { facetId, filterFields: [...] }
        const filterFields = filterFieldsMetadata.filterFields || [];
        const filterField = filterFields.find(f => f.id === quickFilter.field);
        if (!filterField) {
            console.warn('[Filters] Quick filter field not found in metadata:', quickFilter.field);
            return;
        }
        
        addFilterRow(filterField);
        filter = activeFilters[activeFilters.length - 1];
    }
    
    if (!filter) return;
    
    const filterId = filter.id;
    
    // Set value based on type
    if (quickFilter.type === 'thisMonth') {
        const now = new Date();
        const firstDay = new Date(now.getFullYear(), now.getMonth(), 1);
        filter.value = {
            from: firstDay.toISOString().split('T')[0],
            to: now.toISOString().split('T')[0]
        };
        // Sync date inputs in the DOM (already rendered by addFilterRow)
        const filterRow = document.querySelector(`.filter-row[data-filter-id="${filterId}"]`);
        if (filterRow) {
            const fromInput = filterRow.querySelector(`input[data-date-type="from"]`);
            const toInput = filterRow.querySelector(`input[data-date-type="to"]`);
            if (fromInput) fromInput.value = filter.value.from;
            if (toInput) toInput.value = filter.value.to;
        }
    } else {
        // DROPDOWN quick filters: wrap in array for consistency with checkbox-based filters
        filter.value = Array.isArray(quickFilter.value) ? quickFilter.value : [quickFilter.value];
        // Store the human-readable label so query-builder can display it
        filter.quickFilterLabel = quickFilter.label || null;
        
        // Update button text (values may not be loaded yet since dropdown is lazy-loaded)
        const btn = document.querySelector(`.filter-value-btn[data-filter-id="${filterId}"]`);
        if (btn && btn.querySelector('span')) {
            const count = filter.value.length;
            btn.querySelector('span').textContent = count === 1 ? '1 selected' : `${count} selected`;
        }
        
        // If dropdown values are already loaded, sync checkboxes
        const checkboxes = document.querySelectorAll(`input[type="checkbox"][data-filter-id="${filterId}"]`);
        if (checkboxes.length > 0) {
            checkboxes.forEach(cb => {
                cb.checked = filter.value.includes(parseInt(cb.value));
            });
        }
    }
    
    window.activeFilters = activeFilters;
    
    if (typeof renderActiveFiltersChips === 'function') {
        renderActiveFiltersChips();
    }
    if (typeof updateFilterBadge === 'function') {
        updateFilterBadge();
    }
    
    // Auto-apply quick filter
    if (typeof applyFiltersAndSearch === 'function') {
        applyFiltersAndSearch();
    }
}

/**
 * Dropdowns inside .filter-panel-content are clipped by overflow-y:auto.
 * Float open layers with fixed positioning to the viewport (same pattern as popovers).
 */
function positionFilterDropdownFloating(dropdown, trigger) {
    if (!dropdown || !trigger) return;
    const rect = trigger.getBoundingClientRect();
    const gap = 4;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    let width = Math.max(rect.width, 220);
    let left = rect.left;
    if (left + width > vw - 8) left = Math.max(8, vw - 8 - width);
    if (left < 8) left = 8;
    const spaceBelow = vh - rect.bottom - gap;
    const cs = window.getComputedStyle(dropdown);
    const defaultMax = parseFloat(cs.maxHeight) || 300;
    const maxH = Math.min(defaultMax, Math.max(120, spaceBelow - 8));

    dropdown.style.position = 'fixed';
    dropdown.style.top = (rect.bottom + gap) + 'px';
    dropdown.style.left = left + 'px';
    dropdown.style.width = width + 'px';
    dropdown.style.right = 'auto';
    dropdown.style.bottom = 'auto';
    dropdown.style.maxHeight = maxH + 'px';
    dropdown.style.zIndex = '10050';
    dropdown.dataset.filterFloating = '1';
    dropdown._filterFloatTrigger = trigger;
}

function resetFilterDropdownFloating(dropdown) {
    if (!dropdown || dropdown.dataset.filterFloating !== '1') return;
    dropdown.style.position = '';
    dropdown.style.top = '';
    dropdown.style.left = '';
    dropdown.style.width = '';
    dropdown.style.right = '';
    dropdown.style.bottom = '';
    dropdown.style.maxHeight = '';
    dropdown.style.zIndex = '';
    delete dropdown.dataset.filterFloating;
    delete dropdown._filterFloatTrigger;
}

function refreshFloatingFilterDropdowns() {
    document.querySelectorAll(
        '.filter-dropdown-values[data-filter-floating="1"], .filter-dropdown-options[data-filter-floating="1"], .filter-people-results[data-filter-floating="1"]'
    ).forEach((d) => {
        if (d.style.display === 'none') return;
        const t = d._filterFloatTrigger;
        if (t && document.contains(t)) {
            positionFilterDropdownFloating(d, t);
        }
    });
}

function initFilterDropdownFloatingListeners() {
    if (window.__filterDropdownFloatListeners) return;
    window.__filterDropdownFloatListeners = true;
    const onMove = () => {
        refreshFloatingFilterDropdowns();
    };
    window.addEventListener('scroll', onMove, true);
    window.addEventListener('resize', onMove);
}

// Export functions for use in other modules
if (typeof window !== 'undefined') {
    window.loadFilterFields = loadFilterFields;
    window.clearAllFilters = clearAllFilters;
    window.buildFiltersObject = buildFiltersObject;
    window.hasActiveFilters = hasActiveFilters;
    window.updateAddFilterDropdown = updateAddFilterDropdown;
    window.renderActiveFiltersChips = renderActiveFiltersChips;
    window.removeFilter = removeFilter;
    window.initQuickFilters = initQuickFilters;
    window.updateFilterBadge = updateFilterBadge;
    window.updateValueHighlight = updateValueHighlight;
    window.getActiveSearchFields = getActiveSearchFields;
    window.getEffectiveSearchFieldsForCategory = getEffectiveSearchFieldsForCategory;
    window.renderSearchFieldSelector = renderSearchFieldSelector;
    window.FILTER_DROPDOWN_LAYER_SELECTOR = FILTER_DROPDOWN_LAYER_SELECTOR;
    window.positionFilterDropdownFloating = positionFilterDropdownFloating;
    window.resetFilterDropdownFloating = resetFilterDropdownFloating;
    window.refreshFloatingFilterDropdowns = refreshFloatingFilterDropdowns;
    window.initFilterDropdownFloatingListeners = initFilterDropdownFloatingListeners;
}

/**
 * Show a floating dropdown on a filter name button so the user can swap the field.
 */
function showFieldChangerDropdown(anchor, filterId) {
    // Close all other open filter dropdowns first
    const layerSel = window.FILTER_DROPDOWN_LAYER_SELECTOR ||
        '.filter-dropdown-values, .filter-dropdown-options, .filter-people-results, .filter-name-changer';
    document.querySelectorAll(layerSel).forEach((opt) => {
        if (typeof resetFilterDropdownFloating === 'function') resetFilterDropdownFloating(opt);
        opt.style.display = 'none';
    });

    // Reuse or create the shared changer dropdown
    let changer = document.getElementById('filterNameChangerOptions');
    if (!changer) {
        changer = document.createElement('div');
        changer.id = 'filterNameChangerOptions';
        changer.className = 'filter-dropdown-options filter-name-changer';
        changer.style.display = 'none';
        changer.addEventListener('click', (e) => e.stopPropagation());
        document.body.appendChild(changer);
    }

    // Populate options from available fields
    changer.innerHTML = '';
    const fields = (filterFieldsMetadata && filterFieldsMetadata.filterFields) || [];
    const currentEntry = activeFilters.find(f => f.id === filterId);
    const currentFieldId = currentEntry ? currentEntry.fieldId : null;

    fields.forEach(field => {
        const option = document.createElement('div');
        option.className = 'filter-dropdown-option' + (field.id === currentFieldId ? ' selected' : '');
        option.textContent = field.name;

        // Disable if another row already uses this field (and it's not the current one)
        const usedByOther = activeFilters.some(f => f.fieldId === field.id && f.id !== filterId);
        if (usedByOther) {
            option.classList.add('disabled');
            option.style.opacity = '0.5';
            option.style.cursor = 'not-allowed';
        } else {
            option.addEventListener('click', (e) => {
                e.stopPropagation();
                changer.style.display = 'none';
                if (typeof resetFilterDropdownFloating === 'function') resetFilterDropdownFloating(changer);
                if (field.id !== currentFieldId) {
                    replaceFilterField(filterId, field);
                }
            });
        }
        changer.appendChild(option);
    });

    if (fields.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'filter-dropdown-option disabled';
        empty.textContent = 'No fields available';
        changer.appendChild(empty);
    }

    changer.style.display = 'block';
    if (typeof positionFilterDropdownFloating === 'function') {
        positionFilterDropdownFloating(changer, anchor);
    }
    if (typeof initFilterDropdownFloatingListeners === 'function') {
        initFilterDropdownFloatingListeners();
    }
}

/**
 * Replace the field of an existing filter row with a new one, preserving its position.
 */
function replaceFilterField(filterId, newField) {
    const filterEntry = activeFilters.find(f => f.id === filterId);
    if (!filterEntry) return;

    // Update the in-memory record
    filterEntry.fieldId = newField.id;
    filterEntry.fieldName = newField.name;
    filterEntry.fieldType = newField.type;
    filterEntry.fieldColumn = newField.fieldName;
    filterEntry.value = null;

    // Update the name button label
    const filterRow = document.querySelector(`.active-filter-row[data-filter-id="${filterId}"]`);
    if (!filterRow) return;
    const nameSpan = filterRow.querySelector('.filter-name-btn span');
    if (nameSpan) nameSpan.textContent = newField.name;

    // Replace the value selector (keep the remove button)
    const rightCol = filterRow.querySelector('.filter-field-value');
    if (rightCol) {
        const removeBtn = rightCol.querySelector('.filter-remove-btn');
        rightCol.innerHTML = '';
        const newValueSelector = createValueSelector(newField, filterId);
        rightCol.appendChild(newValueSelector);
        if (removeBtn) rightCol.appendChild(removeBtn);
    }

    // Refresh related UI
    updateAddFilterDropdown();
    if (typeof renderActiveFiltersChips === 'function') renderActiveFiltersChips();
    if (typeof updateFilterBadge === 'function') updateFilterBadge();
}
