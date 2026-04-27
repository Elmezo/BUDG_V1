/**
 * Bulk Migrate functionality for Dataset facet
 * Handles UI interactions, validation, and ZIP download
 */

(function () {
    'use strict';

    function tr(key, params) {
        if (window.I18n && typeof window.I18n.t === 'function') {
            return window.I18n.t(key, params);
        }
        return key;
    }

    function applyBulkMigratePageI18n() {
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(document.body);
        }
        if (window.I18n && typeof window.I18n.t === 'function') {
            document.title = window.I18n.t('bulkMigrate.pageTitle');
        }
    }

    /** Align with Unison search row payloads (search-table.js Name fallbacks). */
    function getObjectDisplayNameFromRow(rowData) {
        if (!rowData || typeof rowData !== 'object') return '';
        const n = rowData.Name || rowData.name || rowData.PrimaryName || rowData.primaryName || rowData.primaryname
            || rowData['Primary Name'] || rowData['Name']
            || rowData['Short Name'] || rowData.ShortName || rowData.shortName || rowData.shortname
            || rowData['Long Name'] || rowData.LongName || rowData.longName
            || rowData.Subject || rowData.subject
            || rowData['Interface Name'] || rowData.interface_name || rowData.InterfaceName
            || rowData.Title || rowData.title;
        if (n != null && String(n).trim() !== '') {
            return String(n).trim();
        }
        return '';
    }

    // State
    let selectionData = null;
    let availableFacets = [];
    let dependentFacets = []; // Mandatory, read-only facets (BUDG rule)
    let relatedFacets = [];   // Optional, user-selectable facets
    let selectedFacets = new Set();
    let isAllMode = false;

    // View URL mappings per facet
    const VIEW_URL_MAP = {
        'dataset': '/view/dataset/',
        'glossary': '/view/glossary/',
        'system': '/view/system/',
        'process': '/view/process/',
        'policy': '/view/policy/',
        'people': '/view/people/',
        'org-unit': '/view/org-unit/',
        'orgunit': '/view/org-unit/',
        'business-area': '/view/business-area/business-area.html?id=',
        'businessarea': '/view/business-area/business-area.html?id=',
        'business_area': '/view/business-area/business-area.html?id=',
        'client': '/view/client/',
        'committee': '/view/committee/',
        'product': '/view/product/',
        'project': '/view/project/',
        'interface': '/view/system-interface/',
        'capability': '/view/capability/',
        'legal-entity': '/view/LegalEntity/legal-entity.html?id=',
        'legalentity': '/view/LegalEntity/legal-entity.html?id=',
        'regulation': '/view/regulation/',
        'attribute': '/view/attribute/'
    };

    // Facet icons
    const FACET_ICONS = {
        'dataset': 'fa-database',
        'glossary': 'fa-book',
        'system': 'fa-server',
        'process': 'fa-project-diagram',
        'policy': 'fa-file-contract',
        'people': 'fa-user',
        'org-unit': 'fa-sitemap',
        'business-area': 'fa-building',
        'client': 'fa-user-tie',
        'committee': 'fa-users',
        'product': 'fa-box',
        'project': 'fa-folder',
        'interface': 'fa-exchange-alt',
        'capability': 'fa-cogs',
        'legal-entity': 'fa-landmark',
        'regulation': 'fa-gavel',
        'attribute': 'fa-tag'
    };

    // DOM elements
    const facetSubtitle = document.getElementById('facetSubtitle');
    const selectedObjectsTableBody = document.getElementById('selectedObjectsTableBody');
    const noSelectedObjectsMessage = document.getElementById('noSelectedObjectsMessage');
    const relatedObjectsGrid = document.getElementById('relatedObjectsGrid');
    const selectAllCheckbox = document.getElementById('selectAllCheckbox');
    const errorMessage = document.getElementById('errorMessage');
    const warningMessage = document.getElementById('warningMessage');
    const downloadBtn = document.getElementById('downloadBtn');
    const closeBtn = document.getElementById('closeBtn');

    /**
     * Initialize on page load
     */
    function runBulkMigrateInit() {
        console.log('[BulkMigrate] DEBUG: init called');
        console.log('[BulkMigrate] Initializing...');

        // Load selection from sessionStorage
        loadSelection();

        // Setup event listeners
        setupEventListeners();

        // Load available facets
        loadAvailableOptions();
    }

    function init() {
        if (window.i18nReadyPromise) {
            window.i18nReadyPromise
                .then(() => {
                    applyBulkMigratePageI18n();
                    runBulkMigrateInit();
                })
                .catch(() => runBulkMigrateInit());
        } else {
            applyBulkMigratePageI18n();
            runBulkMigrateInit();
        }
    }

    /**
     * Load selection from sessionStorage
     */
    function loadSelection() {
        const stored = sessionStorage.getItem('bulkMigrateSelection');
        console.log('[BulkMigrate] DEBUG: loadSelection - stored data exists:', !!stored);
        if (!stored) {
            showError(tr('bulkMigrate.errNoSelection'));
            return;
        }

        try {
            selectionData = JSON.parse(stored);
            console.log('[BulkMigrate] Loaded selection:', selectionData);

            // Validate source facet is not forbidden before proceeding
            if (!selectionData.facet) {
                showError(tr('bulkMigrate.errMissingFacet'));
                downloadBtn.disabled = true;
                return;
            }

            if (isForbiddenFacet(selectionData.facet)) {
                showError(tr('bulkMigrate.errForbiddenFacet', { facet: formatFacetName(selectionData.facet) }));
                downloadBtn.disabled = true;
                return;
            }

            // Determine if this is "all" mode
            // Check for explicit allMode flag or empty objectIds
            isAllMode = selectionData.allMode === true || !selectionData.objectIds || selectionData.objectIds.length === 0;

            // In selected mode, validate at least one object is present
            if (!isAllMode && (!selectionData.objectIds || selectionData.objectIds.length === 0)) {
                showError(tr('bulkMigrate.errNoObjects'));
                downloadBtn.disabled = true;
                return;
            }

            // Update UI
            if (selectionData.facet) {
                facetSubtitle.textContent = tr('bulkMigrate.headerSubtitle');
            }

            // Render selected objects
            renderSelectedObjects();

            // Pre-select source facet
            if (selectionData.facet) {
                selectedFacets.add(selectionData.facet);
            }

            // Enable Download as soon as we have a valid source selection (do not wait for
            // available-facets metadata; renderRelatedObjects may early-return when facets is empty).
            updateDownloadButton();

            // If "Migrate All Objects" mode, select all facets (will be done after loading available facets)

        } catch (e) {
            console.error('[BulkMigrate] Error loading selection:', e);
            showError(tr('bulkMigrate.errLoadSelection', { message: e.message }));
        }
    }

    /**
     * Render selected objects in table
     */
    function renderSelectedObjects() {
        selectedObjectsTableBody.innerHTML = '';

        // Get objects from rows or objects array
        let objects = [];
        if (selectionData.objects) {
            objects = selectionData.objects;
        } else if (selectionData.rows) {
            // Convert rows to objects
            objects = selectionData.rows.map(row => {
                if (typeof row === 'string') {
                    try {
                        return JSON.parse(row);
                    } catch (e) {
                        return { name: row };
                    }
                }
                return row;
            });
        }

        if (!selectionData || objects.length === 0) {
            noSelectedObjectsMessage.style.display = 'flex';
            return;
        }

        noSelectedObjectsMessage.style.display = 'none';

        // Get facet from selection data
        const facet = selectionData.facet || 'dataset';
        console.log('[BulkMigrate] Building links for facet:', facet);

        // Normalize facet for lookup - try multiple formats
        const facetLower = facet.toLowerCase().trim();
        const facetNormalized = facetLower.replace(/[_\s-]/g, '');
        const facetWithHyphen = facetLower.replace(/[_\s]/g, '-');
        const facetWithUnderscore = facetLower.replace(/[-\s]/g, '_');

        // Try to find view URL in multiple formats
        let viewUrl = VIEW_URL_MAP[facetLower] ||
            VIEW_URL_MAP[facetWithHyphen] ||
            VIEW_URL_MAP[facetWithUnderscore] ||
            VIEW_URL_MAP[facetNormalized] ||
            '/view/';

        console.log('[BulkMigrate] Found view URL:', viewUrl, 'for facet:', facet);

        // Get icon class
        const iconClass = FACET_ICONS[facetLower] ||
            FACET_ICONS[facetWithHyphen] ||
            FACET_ICONS[facetWithUnderscore] ||
            FACET_ICONS[facetNormalized] ||
            'fa-circle';

        const facetDisplayName = formatFacetName(facet);

        objects.forEach(obj => {
            const row = document.createElement('tr');
            const cell = document.createElement('td');

            const objectRow = document.createElement('div');
            objectRow.className = 'object-row';
            objectRow.style.position = 'relative';

            const icon = document.createElement('div');
            icon.className = 'object-icon';
            icon.innerHTML = `<i class="fas ${iconClass}"></i>`;

            // Get object ID for link (before name resolution)
            const objId = obj.id || obj.ID || obj.objectId || obj.Id || obj.Ref;

            // Prefer facet-specific display fields, then generic search-row keys
            let objName;
            if (facetLower === 'legal-entity' || facetLower === 'legalentity' || facetLower === 'legal_entity') {
                objName = obj.LongName || obj.longName || obj.longname ||
                    obj.ShortName || obj.shortName || obj.shortname ||
                    getObjectDisplayNameFromRow(obj) ||
                    obj.name || obj.PrimaryName || obj.Name ||
                    obj.primaryName || obj.primary_name ||
                    obj.text || obj.label || '';
            } else if (facetLower === 'system' || facetLower === 'systems') {
                objName = obj.Name || obj.name ||
                    obj.ShortName || obj.shortName || obj.shortname ||
                    getObjectDisplayNameFromRow(obj) ||
                    obj.PrimaryName || obj.primaryName || obj.primary_name ||
                    obj.text || obj.label || '';
            } else {
                objName = getObjectDisplayNameFromRow(obj) ||
                    obj.name || obj.PrimaryName || obj.Name ||
                    obj.primaryName || obj.primary_name ||
                    obj.text || obj.label || '';
            }
            if (!objName) {
                objName = tr('bulkMigrate.itemPrefix', { id: objId != null ? objId : '?' });
            }

            // Build link URL - handle special cases that use query parameters
            let linkUrl = '#';
            if (objId) {
                if (viewUrl.includes('?id=')) {
                    // URL already has query parameter format (e.g., business-area, legal-entity)
                    linkUrl = `${viewUrl}${objId}`;
                } else {
                    // Standard path format (e.g., /view/dataset/31)
                    linkUrl = `${viewUrl}${objId}`;
                }
                console.log('[BulkMigrate] Built link URL:', linkUrl, 'for object:', objName, 'ID:', objId);
            }

            // Create link for object name
            const nameLink = document.createElement('a');
            nameLink.className = 'object-name-link';
            nameLink.href = linkUrl;
            nameLink.textContent = objName;
            nameLink.target = '_blank';

            // Add tooltip for facet name on hover
            const tooltip = document.createElement('div');
            tooltip.className = 'facet-tooltip';
            tooltip.textContent = tr('bulkMigrate.facetTooltip', { name: facetDisplayName });
            tooltip.style.display = 'none';
            // Append tooltip to body to ensure it's above everything
            document.body.appendChild(tooltip);

            nameLink.addEventListener('mouseenter', function (e) {
                // Calculate position relative to viewport
                const linkRect = nameLink.getBoundingClientRect();
                tooltip.style.display = 'block';
                // Position tooltip above the link
                tooltip.style.left = linkRect.left + 'px';
                tooltip.style.top = (linkRect.top - tooltip.offsetHeight - 8) + 'px';
                tooltip.classList.add('visible');
            });

            nameLink.addEventListener('mouseleave', function () {
                tooltip.style.display = 'none';
                tooltip.classList.remove('visible');
            });

            // Clean up tooltip when row is removed
            row.addEventListener('remove', function () {
                if (tooltip.parentNode) {
                    tooltip.parentNode.removeChild(tooltip);
                }
            });

            objectRow.appendChild(icon);
            objectRow.appendChild(nameLink);
            objectRow.appendChild(tooltip);
            cell.appendChild(objectRow);
            row.appendChild(cell);
            selectedObjectsTableBody.appendChild(row);
        });
    }

    /**
     * Setup event listeners
     */
    function setupEventListeners() {
        downloadBtn.addEventListener('click', handleDownload);
        closeBtn.addEventListener('click', handleClose);
        selectAllCheckbox.addEventListener('change', handleSelectAll);
    }

    /**
     * Load available facets
     */
    async function loadAvailableOptions() {
        if (!selectionData || !selectionData.facet) {
            return;
        }

        try {
            console.log(`[BulkMigrate] DEBUG: loading options for sourceFacet=${selectionData.facet}`);
            // Load available facets
            const facetsResponse = await fetch(`/api/bulk-migrate/available-facets?sourceFacet=${selectionData.facet}`, {
                credentials: 'include'
            });

            if (facetsResponse.ok) {
                const facetsData = await facetsResponse.json();
                availableFacets = facetsData.facets || [];
                dependentFacets = facetsData.dependent || facetsData.mandatoryFacets || []; // Dependent = mandatory
                relatedFacets = facetsData.related || [];

                console.log('[BulkMigrate] Loaded facets - dependent:', dependentFacets, 'related:', relatedFacets);

                // Auto-select dependent facets (mandatory, cannot be deselected)
                dependentFacets.forEach(facet => {
                    selectedFacets.add(facet);
                });

                // If "Migrate All Objects" mode, also select all related facets
                if (isAllMode) {
                    relatedFacets.forEach(facet => {
                        selectedFacets.add(facet);
                    });
                    selectAllCheckbox.checked = true;
                }

                renderRelatedObjects();
            } else {
                // Try to read error message from response
                let errorMessage = `HTTP ${facetsResponse.status}: ${facetsResponse.statusText}`;
                try {
                    const errorData = await facetsResponse.json();
                    if (errorData.error) {
                        errorMessage = errorData.error;
                    }
                } catch (e) {
                    // If response is not JSON, use status text
                }
                console.error('[BulkMigrate] Error loading facets:', facetsResponse.status, errorMessage);
                showError(tr('bulkMigrate.errLoadFacets', { message: errorMessage }));
                // Keep Download usable for source-only export when selection was already valid.
                updateDownloadButton();
            }

        } catch (e) {
            console.error('[BulkMigrate] Error loading options:', e);
            showError(tr('bulkMigrate.errLoadOptions', { message: e.message }));
            updateDownloadButton();
        }
    }

    function renderRelatedObjects() {
        relatedObjectsGrid.innerHTML = '';

        if (availableFacets.length === 0) {
            updateSelectAllCheckbox();
            updateDownloadButton();
            return;
        }

        // Filter out source facet
        const facetsToShow = availableFacets.filter(facet => {
            const normalizedSource = normalizeFacetName(selectionData.facet);
            const normalizedFacet = normalizeFacetName(facet);
            return normalizedFacet !== normalizedSource;
        });

        // Separate dependent and related facets
        const dependentToShow = facetsToShow.filter(f => dependentFacets.includes(f));
        const relatedToShow = facetsToShow.filter(f => !dependentFacets.includes(f));

        // Render dependent facets first (locked, mandatory)
        dependentToShow.forEach(facet => {
            const item = createRelatedObjectItem(facet, true, true); // isDependent=true, isChecked=true
            relatedObjectsGrid.appendChild(item);
        });

        // Then render related facets (optional, user-selectable)
        relatedToShow.forEach(facet => {
            const isSelected = selectedFacets.has(facet);
            const item = createRelatedObjectItem(facet, false, isSelected); // isDependent=false
            relatedObjectsGrid.appendChild(item);
        });

        updateSelectAllCheckbox();
        updateDownloadButton();
    }

    /**
     * Check if facet is dependent (mandatory)
     */
    function isDependentFacet(facet) {
        return dependentFacets.includes(facet);
    }

    /**
     * Create related object checkbox item
     * @param {string} facet - Facet name
     * @param {boolean} isDependent - True if facet is mandatory (locked)
     * @param {boolean} isChecked - True if checkbox should be checked
     */
    function createRelatedObjectItem(facet, isDependent, isChecked) {
        const item = document.createElement('div');
        item.className = 'related-object-item' + (isDependent ? ' dependent' : '');

        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'related-object-checkbox';
        checkbox.id = `facet-${facet}`;
        checkbox.checked = isChecked;
        checkbox.disabled = isDependent; // Dependent facets cannot be unchecked
        checkbox.addEventListener('change', function () {
            if (this.checked) {
                selectedFacets.add(facet);
            } else {
                // Don't allow unchecking dependent facets
                if (!isDependent) {
                    selectedFacets.delete(facet);
                }
            }
            updateSelectAllCheckbox();
            updateDownloadButton();
        });

        const label = document.createElement('label');
        label.className = 'related-object-label';
        label.htmlFor = `facet-${facet}`;
        label.textContent = formatFacetName(facet);

        // Add lock icon for dependent facets
        if (isDependent) {
            const lockIcon = document.createElement('i');
            lockIcon.className = 'fas fa-lock';
            lockIcon.style.marginLeft = '8px';
            lockIcon.style.color = '#64748b';
            lockIcon.style.fontSize = '12px';
            lockIcon.title = tr('bulkMigrate.lockTooltip');
            label.appendChild(lockIcon);
        }

        item.appendChild(checkbox);
        item.appendChild(label);

        return item;
    }

    /**
     * Handle select all checkbox
     */
    function handleSelectAll() {
        const allCheckboxes = relatedObjectsGrid.querySelectorAll('.related-object-checkbox:not(:disabled)');
        allCheckboxes.forEach(checkbox => {
            checkbox.checked = selectAllCheckbox.checked;
            const facet = checkbox.id.replace('facet-', '');
            if (selectAllCheckbox.checked) {
                selectedFacets.add(facet);
            } else {
                // Don't remove mandatory facets
                if (!isDependentFacet(facet)) {
                    selectedFacets.delete(facet);
                }
            }
        });
        updateDownloadButton();
    }

    /**
     * Update select all checkbox state
     */
    function updateSelectAllCheckbox() {
        const allCheckboxes = relatedObjectsGrid.querySelectorAll('.related-object-checkbox:not(:disabled)');
        const checkedCheckboxes = relatedObjectsGrid.querySelectorAll('.related-object-checkbox:not(:disabled):checked');

        if (allCheckboxes.length === 0) {
            selectAllCheckbox.checked = false;
            selectAllCheckbox.indeterminate = false;
        } else if (checkedCheckboxes.length === allCheckboxes.length) {
            selectAllCheckbox.checked = true;
            selectAllCheckbox.indeterminate = false;
        } else if (checkedCheckboxes.length > 0) {
            selectAllCheckbox.checked = false;
            selectAllCheckbox.indeterminate = true;
        } else {
            selectAllCheckbox.checked = false;
            selectAllCheckbox.indeterminate = false;
        }
    }

    /**
     * Update download button state
     */
    function updateDownloadButton() {
        // Button enabled if at least source facet is selected
        const hasSelection = selectedFacets.size > 0;
        downloadBtn.disabled = !hasSelection;
    }

    /**
     * Handle download button click
     */
    async function handleDownload() {
        if (!selectionData) {
            showError(tr('bulkMigrate.errNoSelectionAvailable'));
            return;
        }

        // Validate
        const validation = validateSelection();
        console.log('[BulkMigrate] DEBUG: handleDownload - validation:', validation);
        if (!validation.valid) {
            showError(validation.message);
            return;
        }

        // Disable button
        downloadBtn.disabled = true;
        const originalText = downloadBtn.innerHTML;
        downloadBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + tr('bulkMigrate.downloading');

        try {
            const endpoint = isAllMode ? '/api/bulk-migrate/all' : '/api/bulk-migrate/selected';
            const requestBody = {
                facet: selectionData.facet,
                objectIds: selectionData.objectIds || []
            };

            if (isAllMode) {
                requestBody.exportScope = 'full_tenant';
            }

            if (!isAllMode) {
                requestBody.selectedFacets = Array.from(selectedFacets);
                requestBody.selectedRelationships = []; // Relationships will be auto-discovered
            }

            console.log('[BulkMigrate] DEBUG: Sending migration request to:', endpoint);
            console.log('[BulkMigrate] Sending migration request:', requestBody);

            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(requestBody)
            });

            if (response.ok) {
                // Get ZIP file as blob
                const blob = await response.blob();

                // Trigger download
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `UnisonSearch_${selectionData.facet}.zip`;
                document.body.appendChild(a);
                a.click();
                document.body.removeChild(a);
                window.URL.revokeObjectURL(url);

                showSuccess(tr('bulkMigrate.successZip'));

                // Clear sessionStorage
                sessionStorage.removeItem('bulkMigrateSelection');

                // Redirect to search page after delay
                setTimeout(() => {
                    window.location.href = '/search.html';
                }, 2000);

            } else {
                const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
                showError(tr('bulkMigrate.errMigrationFailed', { message: errorData.error || response.statusText }));
                downloadBtn.disabled = false;
                downloadBtn.innerHTML = originalText;
            }

        } catch (e) {
            console.error('[BulkMigrate] Error during migration:', e);
            showError(tr('bulkMigrate.errDuringMigration', { message: e.message }));
            downloadBtn.disabled = false;
            downloadBtn.innerHTML = originalText;
        }
    }

    /**
     * Forbidden facets that cannot be bulk migrated (mirrors FacetClassificationService)
     */
    const FORBIDDEN_FACETS = new Set([
        'people', 'changerequest', 'change_request', 'change-request',
        'workflow', 'task', 'tasks', 'activetask', 'active_task',
        'physical_field', 'physicalfield'
    ]);

    /**
     * Returns true if the facet is forbidden for bulk migration.
     */
    function isForbiddenFacet(facet) {
        if (!facet) return true;
        const normalized = facet.toLowerCase().replace(/-/g, '_').replace(/\s+/g, '_');
        return FORBIDDEN_FACETS.has(normalized);
    }

    /**
     * Validate selection before migration
     */
    function validateSelection() {
        if (!selectionData || !selectionData.facet) {
            return { valid: false, message: tr('bulkMigrate.validateInvalidSelection') };
        }

        // Validate source facet is not forbidden
        if (isForbiddenFacet(selectionData.facet)) {
            return {
                valid: false,
                message: tr('bulkMigrate.errForbiddenFacet', { facet: formatFacetName(selectionData.facet) })
            };
        }

        // In selected mode, objectIds must be present and non-empty
        if (!isAllMode) {
            if (!selectionData.objectIds || selectionData.objectIds.length === 0) {
                return { valid: false, message: tr('bulkMigrate.validateNoObjects') };
            }

            // Validate each objectId is a positive integer
            for (const id of selectionData.objectIds) {
                if (!Number.isInteger(id) || id <= 0) {
                    return { valid: false, message: tr('bulkMigrate.validateInvalidObjectId', { id }) };
                }
            }
        }

        if (selectedFacets.size === 0) {
            return { valid: false, message: tr('bulkMigrate.validateSelectFacet') };
        }

        // Source facet must always be in the selected facets set
        if (!selectedFacets.has(selectionData.facet)) {
            return {
                valid: false,
                message: tr('bulkMigrate.validateSourceMustInclude', { facet: formatFacetName(selectionData.facet) })
            };
        }

        // Validate no forbidden facets are in the selected set
        for (const facet of selectedFacets) {
            if (isForbiddenFacet(facet)) {
                return {
                    valid: false,
                    message: tr('bulkMigrate.validateForbiddenSelected', { facet: formatFacetName(facet) })
                };
            }
        }

        // Check all mandatory/dependent facets are included
        for (const mandatoryFacet of dependentFacets) {
            if (!selectedFacets.has(mandatoryFacet)) {
                return {
                    valid: false,
                    message: tr('bulkMigrate.validateMandatoryDependency', {
                        mandatory: formatFacetName(mandatoryFacet),
                        source: formatFacetName(selectionData.facet)
                    })
                };
            }
        }

        return { valid: true };
    }

    /**
     * Handle close button click
     */
    function handleClose() {
        if (confirm(tr('bulkMigrate.confirmClose'))) {
            sessionStorage.removeItem('bulkMigrateSelection');
            window.location.href = '/search.html';
        }
    }

    /**
     * Show error message
     */
    function showError(message) {
        errorMessage.textContent = message;
        errorMessage.classList.add('show');
        warningMessage.classList.remove('show');
    }

    /**
     * Show warning message
     */
    function showWarning(message) {
        warningMessage.textContent = message;
        warningMessage.classList.add('show');
        errorMessage.classList.remove('show');
    }

    /**
     * Show success message
     */
    function showSuccess(message) {
        // Use a simple alert for success, or create a success toast
        alert(message);
    }

    /**
     * Capitalize first letter
     */
    function capitalizeFirst(str) {
        if (!str) return '';
        return str.charAt(0).toUpperCase() + str.slice(1);
    }

    /**
     * Normalize facet name for display
     */
    function normalizeFacetName(facet) {
        if (!facet) return '';
        return facet.toLowerCase().replace(/-/g, '_').replace(/\s+/g, '_');
    }

    /**
     * Format facet name for display
     */
    function formatFacetName(facet) {
        if (!facet) return '';
        // Handle special cases
        const nameMap = {
            'roles': 'Roles',
            'legal_entity': 'Legal Entity',
            'business_area': 'Business Area',
            'regulatory_theme': 'Regulatory Theme',
            'system_interface': 'System Interface',
            'process_predecessors': 'Process Predecessors',
            'target_glossary': 'Target Glossary',
            'target_policy': 'Target Policy',
            'target_committee': 'Target Committee',
            'source_attribute': 'Source Attribute',
            'org_unit': 'Org Unit'
        };

        if (nameMap[facet]) {
            return nameMap[facet];
        }

        // Convert snake_case or kebab-case to Title Case
        return facet
            .replace(/_/g, ' ')
            .replace(/-/g, ' ')
            .split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
            .join(' ');
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
