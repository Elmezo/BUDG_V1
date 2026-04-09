/**
 * Bulk Update Page Main Logic
 * Handles loading items, rendering definitions, and executing bulk updates
 * 
 * Phase A: Hardening & Edge Cases
 * Phase B: Workflow Awareness  
 * Phase C: UX Polish & BUDG Consistency
 */

(function () {
    'use strict';

    function tr(key, params) {
        if (window.I18n && typeof window.I18n.t === 'function') {
            return window.I18n.t(key, params);
        }
        return key;
    }

    function applyBulkUpdatePageI18n() {
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(document.body);
        }
        if (window.I18n && typeof window.I18n.t === 'function') {
            document.title = window.I18n.t('bulkUpdate.pageTitle');
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
    let definitions = [];
    let selectedValues = {}; // Map of fieldId -> value for all selected fields
    let isSaving = false;
    let loadedItems = [];
    let editableItemCount = 0;
    let workflowItemCount = 0;
    let attributeSegmentInfo = null; // Segment info for attributes: { commonSegmentId, allSameSegment, segmentIds }

    // View URL mappings per facet
    const VIEW_URL_MAP = {
        'dataset': '/view/dataset/',
        'glossary': '/view/glossary/',
        'system': '/view/system/',
        'process': '/view/process/',
        'policy': '/view/policy/',
        'people': '/view/people/',
        'role': '/view/role/',
        'org-unit': '/view/org-unit/',
        'orgunit': '/view/org-unit/',
        'business-area': '/view/business-area/',
        'businessarea': '/view/business-area/',
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
        'role': 'fa-user-tag',
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

    /**
     * Normalize facet name to match backend expectations
     * Converts display names like "DATA-SETS" to backend names like "dataset"
     * Backend normalizeFacet() only does toLowerCase(), so we need to handle variations here
     */
    function normalizeFacetName(facet) {
        if (!facet) return facet;
        
        // First normalize: lowercase, trim
        let normalized = facet.toLowerCase().trim();
        
        // Remove hyphens and spaces for matching
        const normalizedForMatch = normalized.replace(/[_\s-]/g, '');
        
        // Map common variations to backend facet names
        // Backend expects: dataset, glossary, system, process, etc. (lowercase, no hyphens for most)
        const facetMap = {
            'datasets': 'dataset',
            'dataset': 'dataset',
            'attributes': 'attribute',
            'attribute': 'attribute',
            'businessarea': 'business-area',
            'business-area': 'business-area',
            'orgunit': 'org-unit',
            'org-unit': 'org-unit',
            'legalentity': 'legal-entity',
            'legal-entity': 'legal-entity',
            'regulatorytheme': 'regulatory-theme',
            'regulatory-theme': 'regulatory-theme',
            'physicalfield': 'physical-field',
            'physical-field': 'physical-field'
        };
        
        // Check both normalized and normalizedForMatch
        if (facetMap[normalized]) {
            return facetMap[normalized];
        }
        if (facetMap[normalizedForMatch]) {
            return facetMap[normalizedForMatch];
        }
        
        // For most facets, remove hyphens (backend expects lowercase without hyphens)
        // But keep hyphens for specific facets that backend expects with hyphens
        const keepHyphens = ['business-area', 'org-unit', 'legal-entity', 'regulatory-theme', 'physical-field'];
        if (keepHyphens.includes(normalized)) {
            return normalized;
        }
        
        // Remove hyphens for others
        return normalized.replace(/-/g, '');
    }

    /**
     * Initialize page
     */
    function runBulkUpdateInit() {
        const stored = sessionStorage.getItem('bulkUpdateSelection');
        if (!stored) {
            showError(tr('bulkUpdate.errNoSelectionData'));
            return;
        }

        try {
            selectionData = JSON.parse(stored);
            // Normalize facet name to match backend expectations
            if (selectionData.facet) {
                selectionData.facet = normalizeFacetName(selectionData.facet);
            }
        } catch (e) {
            showError(tr('bulkUpdate.errInvalidSelection'));
            return;
        }

        if (!selectionData.objectIds || selectionData.objectIds.length === 0) {
            showError(tr('bulkUpdate.errNoItemsSelected'));
            return;
        }

        // Filter out null/undefined IDs and ensure they are numbers
        selectionData.objectIds = selectionData.objectIds
            .map(id => {
                // Convert to number if it's a string
                const numId = typeof id === 'string' ? parseInt(id, 10) : id;
                return isNaN(numId) ? null : numId;
            })
            .filter(id => id !== null && id !== undefined);

        if (selectionData.objectIds.length === 0) {
            showError(tr('bulkUpdate.errNoValidIds'));
            return;
        }

        console.log('[BulkUpdate] Loaded selection:', {
            facet: selectionData.facet,
            objectIds: selectionData.objectIds,
            count: selectionData.objectIds.length
        });

        createConfirmationModal();
        injectStyles();
        loadItems();
        
        // Load definitions after a small delay to ensure DOM is ready
        // Also ensure it's called even if loadItems fails
        setTimeout(() => {
            console.log('[BulkUpdate] Calling loadDefinitions from init, facet:', selectionData.facet);
            loadDefinitions();
        }, 100);

        // Wire up buttons
        document.getElementById('saveBtn')?.addEventListener('click', handleSave);
        document.getElementById('saveCloseBtn')?.addEventListener('click', handleSaveAndClose);
        document.getElementById('closeBtn')?.addEventListener('click', handleClose);

        // Keyboard accessibility
        document.addEventListener('keydown', handleGlobalKeydown);
    }

    function init() {
        if (window.i18nReadyPromise) {
            window.i18nReadyPromise
                .then(() => {
                    applyBulkUpdatePageI18n();
                    runBulkUpdateInit();
                })
                .catch(() => runBulkUpdateInit());
        } else {
            applyBulkUpdatePageI18n();
            runBulkUpdateInit();
        }
    }

    /**
     * Inject all custom styles
     */
    function injectStyles() {
        const styles = `
            <style id="bulk-update-custom-styles">
                /* Modal styles */
                .modal-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.5);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 10000;
                    animation: fadeIn 0.2s ease;
                }
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
                .modal-content {
                    background: var(--bu-card-bg, #ffffff);
                    border-radius: 12px;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
                    max-width: 450px;
                    width: 90%;
                    animation: slideUp 0.2s ease;
                }
                @keyframes slideUp {
                    from { transform: translateY(20px); opacity: 0; }
                    to { transform: translateY(0); opacity: 1; }
                }
                .modal-header {
                    padding: 20px 24px 16px;
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    border-bottom: 1px solid var(--bu-border, #e0e6ed);
                }
                .modal-icon { font-size: 1.5rem; color: #f59e0b; }
                .modal-icon.danger { color: #dc3545; }
                .modal-header h3 { margin: 0; font-size: 1.1rem; color: var(--bu-text, #333); }
                .modal-body { padding: 20px 24px; }
                .modal-body p { margin: 0; color: var(--bu-text, #333); line-height: 1.6; }
                .modal-footer {
                    padding: 16px 24px;
                    display: flex;
                    justify-content: flex-end;
                    gap: 12px;
                    border-top: 1px solid var(--bu-border, #e0e6ed);
                }
                .modal-footer .btn { min-width: 100px; }
                .modal-footer .btn-danger { background: #dc3545; color: white; }
                .modal-footer .btn-danger:hover { background: #c82333; }
                
                /* Workflow warning banner */
                .workflow-warning-banner {
                    background: linear-gradient(135deg, #fff3cd 0%, #ffeeba 100%);
                    border: 1px solid #ffc107;
                    border-radius: 8px;
                    padding: 12px 16px;
                    margin-bottom: 16px;
                    display: flex;
                    align-items: center;
                    gap: 12px;
                }
                .workflow-warning-banner i { color: #856404; font-size: 1.2rem; }
                .workflow-warning-banner span { color: #856404; font-size: 0.9rem; }
                [data-theme="dark"] .workflow-warning-banner {
                    background: linear-gradient(135deg, #433a1f 0%, #3d3419 100%);
                    border-color: #ffc107;
                }
                [data-theme="dark"] .workflow-warning-banner i,
                [data-theme="dark"] .workflow-warning-banner span { color: #ffc107; }
                
                /* Workflow row styling */
                .items-table tr.workflow-locked { opacity: 0.7; }
                .items-table tr.workflow-locked td { color: #6c757d; }
                .items-table tr.workflow-locked .name-link { color: #6c757d; }
                [data-theme="dark"] .items-table tr.workflow-locked td,
                [data-theme="dark"] .items-table tr.workflow-locked .name-link { color: #8a9199; }
                .workflow-lock-icon { color: #856404; margin-left: 8px; cursor: help; }
                [data-theme="dark"] .workflow-lock-icon { color: #ffc107; }
                
                /* Helper text styling */
                .helper-text {
                    font-size: 0.8rem;
                    color: #6c757d;
                    margin-top: 8px;
                    font-style: italic;
                }
                [data-theme="dark"] .helper-text { color: #adb5bd; }
                
                /* Definition placeholder */
                .definition-placeholder {
                    padding: 24px;
                    text-align: center;
                    color: #6c757d;
                    font-size: 0.9rem;
                }
                .definition-placeholder i { margin-right: 8px; color: #248567; }
                [data-theme="dark"] .definition-placeholder { color: #adb5bd; }
                
                /* Definition field enhancements */
                .definition-field {
                    padding: 12px 16px;
                    margin: 4px 0;
                    border-radius: 6px;
                    transition: background-color 0.2s ease;
                }
                .definition-field:hover { background-color: rgba(36, 133, 103, 0.05); }
                .definition-field.selected {
                    background-color: rgba(36, 133, 103, 0.1);
                    border-left: 3px solid #248567;
                }
                [data-theme="dark"] .definition-field:hover { background-color: rgba(72, 187, 120, 0.1); }
                [data-theme="dark"] .definition-field.selected { background-color: rgba(72, 187, 120, 0.15); }
                
                /* Definition helper text */
                .definition-helper {
                    font-size: 0.8rem;
                    color: #248567;
                    margin-top: 12px;
                    padding: 8px 12px;
                    background: rgba(36, 133, 103, 0.08);
                    border-radius: 4px;
                    display: none;
                }
                .definition-helper.visible { display: block; }
                .definition-helper i { margin-right: 6px; }
                [data-theme="dark"] .definition-helper { background: rgba(72, 187, 120, 0.1); color: #48bb78; }
                
                /* Disabled button styling */
                .btn:disabled {
                    cursor: not-allowed;
                    opacity: 0.6;
                }
                
                /* Toast styling */
                .toast.info {
                    background: #17a2b8;
                }
                
                /* Search lookup field styles */
                .search-lookup-wrapper {
                    position: relative;
                    width: 100%;
                }
                .search-lookup-input {
                    padding-left: 40px !important;
                    padding-right: 12px !important;
                    width: 100%;
                }
                .search-icon {
                    position: absolute;
                    left: 12px;
                    top: 50%;
                    transform: translateY(-50%);
                    color: #6c757d;
                    pointer-events: none;
                    z-index: 1;
                }
                .search-lookup-results {
                    position: absolute;
                    top: 100%;
                    left: 0;
                    right: 0;
                    background: var(--background-primary, #fff);
                    border: 1px solid var(--border-color, #d0d5dd);
                    border-radius: 8px;
                    box-shadow: 0 10px 20px rgba(0,0,0,0.08);
                    max-height: 300px;
                    overflow-y: auto;
                    z-index: 1000;
                    margin-top: 4px;
                }
                .search-result-item {
                    padding: 12px;
                    cursor: pointer;
                    border-bottom: 1px solid var(--border-color, #e0e6ed);
                    transition: background-color 0.2s;
                }
                .search-result-item:hover,
                .search-result-item.highlighted {
                    background-color: var(--background-secondary, #f8f9fa);
                }
                .search-result-item:last-child {
                    border-bottom: none;
                }
                .search-lookup-input:disabled {
                    background-color: var(--background-secondary, #f5f5f5);
                    cursor: not-allowed;
                    opacity: 0.6;
                }
                [data-theme="dark"] .search-lookup-results {
                    background: var(--background-primary, #1a1a1a);
                    border-color: var(--border-color, #333);
                }
                [data-theme="dark"] .search-result-item {
                    border-color: var(--border-color, #333);
                }
                [data-theme="dark"] .search-result-item:hover,
                [data-theme="dark"] .search-result-item.highlighted {
                    background-color: var(--background-secondary, #2a2a2a);
                }
            </style>
        `;
        document.head.insertAdjacentHTML('beforeend', styles);
    }

    /**
     * Create confirmation modal HTML
     */
    function createConfirmationModal() {
        const modalHtml = `
            <div class="modal-overlay" id="confirmModal" style="display: none;">
                <div class="modal-content" role="dialog" aria-modal="true" aria-labelledby="modalTitle">
                    <div class="modal-header">
                        <i class="fas fa-exclamation-triangle modal-icon"></i>
                        <h3 id="modalTitle">${escapeHtml(tr('bulkUpdate.modalConfirmAction'))}</h3>
                    </div>
                    <div class="modal-body">
                        <p id="modalMessage">${escapeHtml(tr('bulkUpdate.modalConfirmContinue'))}</p>
                    </div>
                    <div class="modal-footer">
                        <button class="btn btn-secondary" id="modalCancelBtn">${escapeHtml(tr('button.cancel'))}</button>
                        <button class="btn btn-primary" id="modalConfirmBtn">${escapeHtml(tr('button.confirm'))}</button>
                    </div>
                </div>
            </div>
        `;
        document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    /**
     * Handle global keyboard events
     */
    function handleGlobalKeydown(e) {
        const modal = document.getElementById('confirmModal');
        if (modal && modal.style.display === 'flex') {
            if (e.key === 'Escape') {
                e.preventDefault();
                document.getElementById('modalCancelBtn')?.click();
            } else if (e.key === 'Enter') {
                e.preventDefault();
                document.getElementById('modalConfirmBtn')?.click();
            }
        }
    }

    /**
     * Show confirmation modal
     */
    function showConfirmationModal(title, message, isDanger = false) {
        return new Promise((resolve) => {
            const modal = document.getElementById('confirmModal');
            const modalTitle = document.getElementById('modalTitle');
            const modalMessage = document.getElementById('modalMessage');
            const confirmBtn = document.getElementById('modalConfirmBtn');
            const cancelBtn = document.getElementById('modalCancelBtn');
            const modalIcon = modal.querySelector('.modal-icon');

            modalTitle.textContent = title;
            modalMessage.innerHTML = message;

            if (isDanger) {
                modalIcon.classList.add('danger');
                confirmBtn.classList.add('btn-danger');
                confirmBtn.classList.remove('btn-primary');
            } else {
                modalIcon.classList.remove('danger');
                confirmBtn.classList.remove('btn-danger');
                confirmBtn.classList.add('btn-primary');
            }

            modal.style.display = 'flex';
            confirmBtn.focus(); // Focus management

            const cleanup = () => {
                confirmBtn.removeEventListener('click', onConfirm);
                cancelBtn.removeEventListener('click', onCancel);
                modal.removeEventListener('click', onOverlayClick);
            };

            const onConfirm = () => {
                modal.style.display = 'none';
                cleanup();
                document.getElementById('saveBtn')?.focus(); // Return focus
                resolve(true);
            };

            const onCancel = () => {
                modal.style.display = 'none';
                cleanup();
                document.getElementById('saveBtn')?.focus(); // Return focus
                resolve(false);
            };

            const onOverlayClick = (e) => {
                if (e.target === modal) {
                    modal.style.display = 'none';
                    cleanup();
                    document.getElementById('saveBtn')?.focus(); // Return focus
                    resolve(false);
                }
            };

            confirmBtn.addEventListener('click', onConfirm);
            cancelBtn.addEventListener('click', onCancel);
            modal.addEventListener('click', onOverlayClick);
        });
    }

    /**
     * Load items for display in the table
     */
    async function loadItems() {
        const facet = selectionData.facet;
        const objectIds = selectionData.objectIds;

        console.log('[BulkUpdate] loadItems called with:', {
            facet: facet,
            objectIds: objectIds,
            rows: selectionData.rows?.length || 0
        });

        // Ensure all IDs are valid numbers and filter out any invalid ones
        const validIds = objectIds
            .map(id => {
                const numId = typeof id === 'string' ? parseInt(id, 10) : id;
                return isNaN(numId) ? null : numId;
            })
            .filter(id => id !== null && id !== undefined && id > 0);

        if (validIds.length === 0) {
            console.error('[BulkUpdate] No valid IDs to load, using stored data');
            renderItemsFromStoredData();
            return;
        }

        console.log('[BulkUpdate] Loading items:', {
            facet: facet,
            ids: validIds,
            count: validIds.length
        });

        try {
            let endpoint;
            if (facet.toLowerCase() === 'role') {
                endpoint = `/api/bulk-update/role-items?ids=${validIds.join(',')}`;
            } else {
                endpoint = `/api/bulk-update/items/${facet}?ids=${validIds.join(',')}`;
            }

            console.log('[BulkUpdate] Fetching from:', endpoint);

            const response = await fetch(endpoint, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('[BulkUpdate] API response:', data);

            if (data.success && data.items && data.items.length > 0) {
                loadedItems = data.items || [];
                console.log('[BulkUpdate] Loaded items from API:', loadedItems.length);
                renderItemsTable(loadedItems, facet);
                
                // For attribute facet, analyze segments
                if (facet.toLowerCase() === 'attribute') {
                    await analyzeAttributeSegments(validIds);
                }
            } else {
                console.warn('[BulkUpdate] API returned success=false or no items, using fallback');
                renderItemsFromStoredData();
            }
        } catch (error) {
            console.error('[BulkUpdate] Error loading items from API:', error);
            console.log('[BulkUpdate] Falling back to stored data');
            renderItemsFromStoredData();
        }
    }

    /**
     * Render items table with workflow awareness
     */
    function renderItemsTable(items, facet) {
        const headerRow = document.getElementById('itemsTableHeader');
        const tbody = document.getElementById('itemsTableBody');
        const noItemsMsg = document.getElementById('noItemsMessage');

        if (!items || items.length === 0) {
            noItemsMsg.style.display = 'flex';
            return;
        }

        noItemsMsg.style.display = 'none';

        workflowItemCount = items.filter(item => item.isUnderWorkflow).length;
        editableItemCount = items.length - workflowItemCount;

        showWorkflowWarningBanner();

        let columns;
        if (facet.toLowerCase() === 'role') {
            columns = ['Role', 'AssignedTo', 'DateAccepted', 'Object', 'RoleAccepted', 'RoleStatus', 'CRStatus'];
        } else {
            columns = ['Name'];
        }

        headerRow.innerHTML = columns.map(col => `<th>${formatColumnName(col)}</th>`).join('');

        tbody.innerHTML = '';
        items.forEach(item => {
            const isUnderWorkflow = item.isUnderWorkflow === true;
            const row = document.createElement('tr');

            if (isUnderWorkflow) {
                row.classList.add('workflow-locked');
            }

            columns.forEach((col, colIndex) => {
                const td = document.createElement('td');
                // Try multiple case variations for column names
                const value = item[col] || item[col.toLowerCase()] || item[col.toUpperCase()] || 
                             (col === 'CRStatus' ? (item.crStatus || item.cr_status || item.CR_Status) : '') ||
                             item.name || '';

                if (col === 'Name' || col === 'Role') {
                    const id = item.ID || item.id;
                    const url = getViewUrl(facet, id);
                    const iconClass = FACET_ICONS[facet.toLowerCase()] || 'fa-file';

                    let lockIconHtml = '';
                    if (isUnderWorkflow && colIndex === 0) {
                        lockIconHtml = `<i class="fas fa-lock workflow-lock-icon" 
                            title="This item is currently part of an active workflow and cannot be updated."></i>`;
                    }

                    td.innerHTML = `<a href="${url}" class="name-link" target="_blank">
                        <i class="fas ${iconClass}"></i>
                        ${escapeHtml(value)}
                    </a>${lockIconHtml}`;
                } else if (col === 'AssignedTo') {
                    const personId = item.ipid || item.PersonId;
                    if (personId) {
                        td.innerHTML = `<a href="/view/people/${personId}" class="name-link" target="_blank">
                            <i class="fas fa-user"></i>
                            ${escapeHtml(value)}
                        </a>`;
                    } else {
                        td.textContent = value || tr('bulkUpdate.na');
                    }
                } else if (col === 'Object') {
                    td.textContent = value || tr('bulkUpdate.na');
                } else if (col === 'DateAccepted') {
                    td.textContent = formatDate(value);
                } else if (col === 'RoleAccepted') {
                    // Display as Yes/No or checkbox icon
                    const isAccepted = value === 'Yes' || value === true || value === 1;
                    td.innerHTML = isAccepted 
                        ? '<i class="fas fa-check-circle" style="color: #28a745;"></i> Yes'
                        : '<i class="fas fa-times-circle" style="color: #dc3545;"></i> No';
                } else if (col === 'CRStatus') {
                    td.textContent = value || tr('bulkUpdate.na');
                } else {
                    td.textContent = value || '';
                }

                row.appendChild(td);
            });

            tbody.appendChild(row);
        });

        // Add helper text below table
        addTableHelperText();
        addSkippedItemsTooltip();
        updateSaveButtonState();
    }

    /**
     * Add helper text below items table
     */
    function addTableHelperText() {
        const cardBody = document.querySelector('.bulk-items-card .card-body');
        if (!cardBody) return;

        // Remove existing helper
        const existing = cardBody.querySelector('.helper-text');
        if (existing) existing.remove();

        const helper = document.createElement('div');
        helper.className = 'helper-text';
        helper.textContent = 'All items are read-only. Click an item name to view its details.';
        cardBody.appendChild(helper);
    }

    /**
     * Show workflow warning banner if any items are under workflow
     */
    function showWorkflowWarningBanner() {
        const existingBanner = document.querySelector('.workflow-warning-banner');
        if (existingBanner) existingBanner.remove();

        if (workflowItemCount > 0) {
            const banner = document.createElement('div');
            banner.className = 'workflow-warning-banner';

            if (editableItemCount === 0) {
                banner.innerHTML = `
                    <i class="fas fa-exclamation-triangle"></i>
                    <span>${escapeHtml(tr('bulkUpdate.workflowBannerAllLocked', { count: workflowItemCount }))}</span>
                `;
            } else {
                banner.innerHTML = `
                    <i class="fas fa-info-circle"></i>
                    <span>${escapeHtml(tr('bulkUpdate.workflowBannerSkipped', { workflow: workflowItemCount, editable: editableItemCount }))}</span>
                `;
            }

            const summaryTab = document.getElementById('summary-tab');
            if (summaryTab) {
                summaryTab.insertBefore(banner, summaryTab.firstChild);
            }
        }
    }

    /**
     * Add tooltip about skipped items
     */
    function addSkippedItemsTooltip() {
        const facet = selectionData.facet?.toLowerCase();
        const facetsWithCRValidation = ['dataset', 'glossary', 'process', 'system'];

        if (!facetsWithCRValidation.includes(facet)) return;

        const cardHeader = document.querySelector('.bulk-items-card .card-header h2');
        if (cardHeader && !cardHeader.querySelector('.info-tooltip')) {
            const tooltip = document.createElement('span');
            tooltip.className = 'info-tooltip';
            tooltip.innerHTML = `<i class="fas fa-info-circle" style="margin-left: 8px; color: #6c757d; cursor: help;"></i>`;
            tooltip.title = tr('bulkUpdate.tooltipSkippedCR');
            cardHeader.appendChild(tooltip);
        }
    }

    /**
     * Render items from stored row data (fallback)
     */
    function renderItemsFromStoredData() {
        const rows = selectionData.rows || [];
        const facet = selectionData.facet;
        const objectIds = selectionData.objectIds || [];

        console.log('[BulkUpdate] renderItemsFromStoredData called:', {
            rowsCount: rows.length,
            objectIdsCount: objectIds.length,
            facet: facet
        });

        // Try to extract items from rows data
        let items = [];
        
        if (rows && rows.length > 0) {
            // Use stored row data
            items = rows.map(row => {
                const id = row.ID || row.id || row.Id || row.Ref;
                const displayName = getObjectDisplayNameFromRow(row);
                const name = displayName || tr('bulkUpdate.itemPrefix', { id });
                return {
                    ID: id,
                    id: id,
                    Name: name,
                    name: name,
                    isUnderWorkflow: false
                };
            });
        } else if (objectIds && objectIds.length > 0) {
            // Fallback: create items from objectIds only
            console.warn('[BulkUpdate] No row data found, creating items from IDs only');
            items = objectIds.map(id => ({
                ID: id,
                id: id,
                Name: tr('bulkUpdate.itemPrefix', { id }),
                name: tr('bulkUpdate.itemPrefix', { id }),
                isUnderWorkflow: false
            }));
        }

        if (items.length === 0) {
            console.error('[BulkUpdate] No items to render');
            const noItemsMsg = document.getElementById('noItemsMessage');
            if (noItemsMsg) {
                noItemsMsg.style.display = 'flex';
            }
            return;
        }

        console.log('[BulkUpdate] Rendering items from stored data:', items.length);
        loadedItems = items;
        editableItemCount = items.length;
        workflowItemCount = 0;
        renderItemsTable(items, facet);
        
        // For attribute facet, try to analyze segments from objectIds
        if (facet && facet.toLowerCase() === 'attribute' && selectionData.objectIds && selectionData.objectIds.length > 0) {
            const validIds = selectionData.objectIds
                .map(id => {
                    const numId = typeof id === 'string' ? parseInt(id, 10) : id;
                    return isNaN(numId) ? null : numId;
                })
                .filter(id => id !== null && id !== undefined && id > 0);
            
            if (validIds.length > 0) {
                analyzeAttributeSegments(validIds);
            }
        }
    }

    /**
     * Analyze segment information for selected attributes
     */
    async function analyzeAttributeSegments(attributeIds) {
        try {
            const response = await fetch(`/api/bulk-update/attribute-segments?ids=${attributeIds.join(',')}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                console.warn('[BulkUpdate] Failed to get attribute segment info, defaulting to null');
                attributeSegmentInfo = null;
                return;
            }

            const data = await response.json();
            if (data.success) {
                attributeSegmentInfo = {
                    commonSegmentId: data.commonSegmentId,
                    allSameSegment: data.allSameSegment,
                    segmentIds: data.segmentIds
                };
                console.log('[BulkUpdate] Attribute segment info:', attributeSegmentInfo);
                
                // Re-render definitions to apply segment-based filtering/disabling
                if (definitions.length > 0) {
                    renderDefinitions();
                }
            } else {
                attributeSegmentInfo = null;
            }
        } catch (error) {
            console.error('[BulkUpdate] Error analyzing attribute segments:', error);
            attributeSegmentInfo = null;
        }
    }

    /**
     * Load definitions for the facet
     */
    async function loadDefinitions() {
        if (!selectionData || !selectionData.facet) {
            console.error('[BulkUpdate] loadDefinitions: selectionData or facet is missing!', selectionData);
            return;
        }

        const facet = selectionData.facet;
        const loadingEl = document.getElementById('definitionLoading');
        const containerEl = document.getElementById('definitionFields');

        console.log('[BulkUpdate] loadDefinitions called for facet:', facet);
        console.log('[BulkUpdate] selectionData:', selectionData);

        if (!containerEl) {
            console.error('[BulkUpdate] definitionFields container not found!');
            return;
        }

        if (loadingEl) {
            loadingEl.style.display = 'block';
        }

        try {
            const response = await fetch(`/api/bulk-update/definitions/${facet}`, {
                method: 'GET',
                credentials: 'include'
            });

            console.log('[BulkUpdate] Definitions API response status:', response.status);

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('[BulkUpdate] Definitions API response data:', data);

            if (data.success) {
                definitions = data.definitions || [];
                console.log('[BulkUpdate] Loaded definitions:', definitions.length, definitions);
                renderDefinitions();
            } else {
                console.error('[BulkUpdate] Definitions API returned success=false:', data.error);
                containerEl.innerHTML = `<div class="no-items-message">
                    <i class="fas fa-exclamation-circle"></i>
                    <span>${data.error || 'No definitions available for this facet'}</span>
                </div>`;
            }
        } catch (error) {
            console.error('[BulkUpdate] Error loading definitions:', error);
            console.error('[BulkUpdate] Error stack:', error.stack);
            if (containerEl) {
                containerEl.innerHTML = `<div class="no-items-message">
                    <i class="fas fa-exclamation-circle"></i>
                    <span>Error loading definitions: ${error.message}</span>
                    <br><small>Please check the console for more details.</small>
                </div>`;
            }
        } finally {
            if (loadingEl) loadingEl.style.display = 'none';
        }
    }

    /**
     * Create a searchable lookup field with magnifying glass icon and autocomplete
     * Works for all field types (LOOKUP, REFERENCE) across all facets
     */
    function createSearchableLookupField(fieldId, fieldName, options = {}) {
        const wrapper = document.createElement('div');
        wrapper.className = 'field-input search-lookup-wrapper';
        wrapper.style.position = 'relative';

        // Magnifying glass icon
        const searchIcon = document.createElement('i');
        searchIcon.className = 'fas fa-search search-icon';
        searchIcon.style.cssText = 'position: absolute; left: 12px; top: 50%; transform: translateY(-50%); color: #6c757d; pointer-events: none; z-index: 1;';

        // Search input field
        const input = document.createElement('input');
        input.type = 'text';
        input.id = `val_${fieldId}`;
        input.className = 'form-input search-lookup-input';
        input.placeholder = `Search ${fieldName}...`;
        input.autocomplete = 'off';
        input.setAttribute('data-field-id', fieldId);
        input.style.cssText = 'padding-left: 40px; padding-right: 12px; width: 100%;';

        // Hidden field to store selected ID
        const hiddenInput = document.createElement('input');
        hiddenInput.type = 'hidden';
        hiddenInput.id = `val_${fieldId}_id`;
        hiddenInput.name = fieldId;

        // Results dropdown
        const resultsDiv = document.createElement('div');
        resultsDiv.className = 'search-lookup-results';
        resultsDiv.style.cssText = 'display: none; position: absolute; top: 100%; left: 0; right: 0; background: var(--background-primary, #fff); border: 1px solid var(--border-color, #d0d5dd); border-radius: 8px; box-shadow: 0 10px 20px rgba(0,0,0,0.08); max-height: 300px; overflow-y: auto; z-index: 1000; margin-top: 4px;';

        wrapper.appendChild(searchIcon);
        wrapper.appendChild(input);
        wrapper.appendChild(hiddenInput);
        wrapper.appendChild(resultsDiv);

        // Debounce timer
        let debounceTimer = null;
        let selectedItem = null;

        // Search function - can accept empty query to show all results
        const performSearch = async (query = '') => {
            try {
                const facet = selectionData.facet;
                let segmentId = null;
                
                // Get segment ID for attribute dataset/glossary fields
                if (facet === 'attribute' && (fieldId === 'dataset' || fieldId === 'glossary')) {
                    if (attributeSegmentInfo && attributeSegmentInfo.allSameSegment) {
                        segmentId = attributeSegmentInfo.commonSegmentId;
                    } else if (attributeSegmentInfo && !attributeSegmentInfo.allSameSegment) {
                        // Field should be disabled, but handle gracefully
                        return;
                    }
                }

                const searchQuery = query && query.trim().length > 0 ? query.trim() : '';
                console.log('[BulkUpdate] Performing search for field:', fieldId, 'query:', searchQuery || '(all items)');
                const results = await searchLookupValues(searchQuery, facet, fieldId, segmentId);
                console.log('[BulkUpdate] Search results for', fieldId, ':', results.length, 'items', results);
                renderSearchResults(results, resultsDiv, input, hiddenInput, fieldId);
            } catch (error) {
                console.error('Error searching lookup values:', error);
                resultsDiv.innerHTML = '<div style="padding: 12px; color: #dc3545;">' + escapeHtml(tr('bulkUpdate.errorLoadingResults')) + '</div>';
                resultsDiv.style.display = 'block';
            }
        };

        // Debounced search on input
        input.addEventListener('input', (e) => {
            const query = e.target.value.trim();
            clearTimeout(debounceTimer);
            
            // If user clears the input, show all results again
            if (query.length === 0) {
                hiddenInput.value = '';
                selectedItem = null;
                // Show all results when input is cleared
                debounceTimer = setTimeout(() => {
                    performSearch('');
                }, 100);
                return;
            }

            // If user is typing and there was a previous selection, clear it
            if (selectedItem && query !== selectedItem.name) {
                hiddenInput.value = '';
                selectedItem = null;
            }

            debounceTimer = setTimeout(() => {
                performSearch(query);
            }, 300);
        });

        // Show all results on focus
        input.addEventListener('focus', (e) => {
            const query = e.target.value.trim();
            // If there's a query, search for it; otherwise show all results
            if (query.length > 0) {
                performSearch(query);
            } else {
                // Show all available items when field is focused
                performSearch('');
            }
        });

        // Close results when clicking outside
        document.addEventListener('click', (e) => {
            if (!wrapper.contains(e.target)) {
                resultsDiv.style.display = 'none';
            }
        });

        // Keyboard navigation
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                resultsDiv.style.display = 'none';
                input.blur();
            } else if (e.key === 'ArrowDown') {
                e.preventDefault();
                const firstResult = resultsDiv.querySelector('.search-result-item');
                if (firstResult) {
                    firstResult.focus();
                    firstResult.classList.add('highlighted');
                }
            }
        });

        // Handle selection from results
        resultsDiv.addEventListener('click', (e) => {
            const resultItem = e.target.closest('.search-result-item');
            if (resultItem) {
                const itemId = resultItem.dataset.itemId;
                const itemName = resultItem.dataset.itemName;
                
                hiddenInput.value = itemId;
                input.value = itemName;
                selectedItem = { id: itemId, name: itemName };
                resultsDiv.style.display = 'none';
                
                // Trigger change event
                const changeEvent = new Event('change', { bubbles: true });
                input.dispatchEvent(changeEvent);
            }
        });

        // Store reference for later use
        wrapper._searchInput = input;
        wrapper._hiddenInput = hiddenInput;
        wrapper._selectedItem = () => selectedItem;

        return wrapper;
    }

    /**
     * Render search results in dropdown
     */
    function renderSearchResults(results, container, input, hiddenInput, fieldId) {
        console.log('[BulkUpdate] renderSearchResults called for field:', fieldId, 'results:', results?.length || 0);
        if (!results || results.length === 0) {
            container.innerHTML = '<div style="padding: 12px; color: #6c757d; text-align: center;">' + escapeHtml(tr('bulkUpdate.noResultsFound')) + '</div>';
            container.style.display = 'block';
            console.log('[BulkUpdate] No results found, showing message');
            return;
        }

        container.innerHTML = '';
        console.log('[BulkUpdate] Rendering', results.length, 'search results');
        results.forEach((item, index) => {
            console.log('[BulkUpdate] Rendering result', index + 1, ':', item);
            const resultItem = document.createElement('div');
            resultItem.className = 'search-result-item';
            resultItem.style.cssText = 'padding: 12px; cursor: pointer; border-bottom: 1px solid var(--border-color, #e0e6ed); transition: background-color 0.2s;';
            resultItem.dataset.itemId = item.id || item.ID || item.Id;
            resultItem.dataset.itemName = item.name || item.Name || item.primaryName || item.PrimaryName || item.displayName || item.DisplayName || '';

            // Get icon based on field type
            let iconClass = 'fa-file';
            if (fieldId === 'dataset' || fieldId === 'system_short_name') {
                iconClass = 'fa-database';
            } else if (fieldId === 'glossary' || fieldId === 'glossary_name') {
                iconClass = 'fa-book';
            } else if (fieldId === 'parent') {
                iconClass = 'fa-sitemap';
            }

            const name = item.name || item.Name || item.primaryName || item.PrimaryName || item.displayName || item.DisplayName || '';
            const description = item.description || item.Description || item.definition || item.Definition || '';
            const systemName = item.systemName || item.SystemName || '';

            let html = `<div style="display: flex; align-items: flex-start; gap: 12px;">`;
            html += `<i class="fas ${iconClass}" style="color: #248567; margin-top: 2px; flex-shrink: 0;"></i>`;
            html += `<div style="flex: 1; min-width: 0;">`;
            html += `<div style="font-weight: 600; color: var(--text-primary, #333); margin-bottom: 4px;">${escapeHtml(name)}</div>`;
            if (systemName) {
                html += `<div style="font-size: 0.85rem; color: var(--text-secondary, #6c757d); margin-bottom: 4px;">System: ${escapeHtml(systemName)}</div>`;
            }
            if (description) {
                html += `<div style="font-size: 0.85rem; color: var(--text-secondary, #6c757d); line-height: 1.4;">${escapeHtml(description)}</div>`;
            }
            html += `</div></div>`;

            resultItem.innerHTML = html;

            // Hover effect
            resultItem.addEventListener('mouseenter', () => {
                resultItem.style.backgroundColor = 'var(--background-secondary, #f8f9fa)';
            });
            resultItem.addEventListener('mouseleave', () => {
                resultItem.style.backgroundColor = 'transparent';
            });

            container.appendChild(resultItem);
        });

        container.style.display = 'block';
        console.log('[BulkUpdate] Search results dropdown displayed with', results.length, 'items');
    }

    /**
     * Search lookup values for any field type across all facets
     * Handles different API endpoints and response formats
     * Special handling for parent fields and dataset reference fields with segment filtering
     */
    async function searchLookupValues(query, facet, fieldId, segmentId = null) {
        try {
            // Special handling for parent field: use filtered parent options endpoint
            if (fieldId === 'parent') {
                const objectIds = loadedItems
                    .filter(item => !item.isUnderWorkflow)
                    .map(item => item.ID || item.id)
                    .filter(id => id !== null && id !== undefined);
                
                if (objectIds.length > 0) {
                    try {
                        const response = await fetch(`/api/bulk-update/parent-options/${facet}?objectIds=${objectIds.join(',')}`, {
                            method: 'GET',
                            credentials: 'include'
                        });
                        if (response.ok) {
                            const responseData = await response.json();
                            let allOptions = [];
                            if (responseData.success && responseData.values && Array.isArray(responseData.values)) {
                                allOptions = responseData.values;
                            } else if (Array.isArray(responseData)) {
                                allOptions = responseData;
                            }
                            
                            // Filter by query
                            if (query && query.trim().length > 0) {
                                const searchTerm = query.toLowerCase();
                                allOptions = allOptions.filter(item => {
                                    const name = (item.name || item.Name || item.primaryName || item.PrimaryName || '').toLowerCase();
                                    const description = (item.description || item.Description || '').toLowerCase();
                                    return name.includes(searchTerm) || description.includes(searchTerm);
                                });
                            }
                            return allOptions;
                        }
                    } catch (error) {
                        console.error('Error loading filtered parent options:', error);
                    }
                }
            }
            
            // Special handling for dataset system_short_name and glossary_name: use filtered reference options
            if (facet === 'dataset' && (fieldId === 'system_short_name' || fieldId === 'glossary_name')) {
                const objectIds = loadedItems
                    .filter(item => !item.isUnderWorkflow)
                    .map(item => item.ID || item.id)
                    .filter(id => id !== null && id !== undefined);
                
                if (objectIds.length > 0) {
                    try {
                        const response = await fetch(`/api/bulk-update/dataset-reference-options/${facet}/${fieldId}?objectIds=${objectIds.join(',')}`, {
                            method: 'GET',
                            credentials: 'include'
                        });
                        if (response.ok) {
                            const responseData = await response.json();
                            let allOptions = [];
                            if (responseData.success && responseData.values && Array.isArray(responseData.values)) {
                                allOptions = responseData.values;
                            } else if (Array.isArray(responseData)) {
                                allOptions = responseData;
                            }
                            
                            // Filter by query
                            if (query && query.trim().length > 0) {
                                const searchTerm = query.toLowerCase();
                                allOptions = allOptions.filter(item => {
                                    const name = (item.name || item.Name || item.primaryName || item.PrimaryName || '').toLowerCase();
                                    const description = (item.description || item.Description || '').toLowerCase();
                                    return name.includes(searchTerm) || description.includes(searchTerm);
                                });
                            }
                            return allOptions;
                        }
                    } catch (error) {
                        console.error('Error loading filtered dataset reference options:', error);
                    }
                }
            }
            
            let data = null;
            const apiMethod = getLookupApiMethod(facet, fieldId);
            const apiEndpoint = getLookupApiEndpoint(facet, fieldId);

            // Build URL with query parameter and optional segmentId
            let url = apiEndpoint;
            if (url) {
                // Handle relative URLs
                if (url.startsWith('/')) {
                    const urlObj = new URL(url, window.location.origin);
                    if (query && query.trim().length > 0) {
                        urlObj.searchParams.set('q', query.trim());
                    }
                    if (segmentId !== null) {
                        urlObj.searchParams.set('segmentId', segmentId);
                    }
                    url = urlObj.pathname + urlObj.search;
                } else {
                    // Absolute URL - append query params
                    let separator = url.includes('?') ? '&' : '?';
                    if (query && query.trim().length > 0) {
                        url += `${separator}q=${encodeURIComponent(query.trim())}`;
                        separator = '&';
                    }
                    if (segmentId !== null) {
                        url += `${separator}segmentId=${segmentId}`;
                    }
                }
            }

            // Try API service method first
            if (apiMethod && window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE[apiMethod] === 'function') {
                try {
                    const allData = await window.BUDG_API_SERVICE[apiMethod]();
                    // Filter results client-side if query is provided
                    if (Array.isArray(allData)) {
                        if (query && query.trim().length > 0) {
                            const searchTerm = query.toLowerCase();
                            data = allData.filter(item => {
                                const name = (item.name || item.Name || item.primaryName || item.PrimaryName || '').toLowerCase();
                                const description = (item.description || item.Description || item.definition || item.Definition || '').toLowerCase();
                                return name.includes(searchTerm) || description.includes(searchTerm);
                            });
                        } else {
                            // Return all data if no query
                            data = allData;
                        }
                    }
                } catch (apiError) {
                    console.warn(`API service method ${apiMethod} failed, trying direct endpoint:`, apiError);
                }
            }

            // If API service didn't work or doesn't support search, try direct endpoint
            if (!data && url) {
                try {
                    const response = await fetch(url, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (response.ok) {
                        const responseData = await response.json();
                        // Handle different response formats
                        if (Array.isArray(responseData)) {
                            data = responseData;
                        } else if (responseData.data && Array.isArray(responseData.data)) {
                            data = responseData.data;
                        } else if (responseData.values && Array.isArray(responseData.values)) {
                            data = responseData.values;
                        } else if (responseData.segments && Array.isArray(responseData.segments)) {
                            data = responseData.segments;
                        }
                        
                        // Filter by query if API doesn't support search parameter
                        if (data && Array.isArray(data) && query && query.trim().length > 0) {
                            const searchTerm = query.toLowerCase();
                            data = data.filter(item => {
                                const name = (item.name || item.Name || item.primaryName || item.PrimaryName || '').toLowerCase();
                                const description = (item.description || item.Description || item.definition || item.Definition || '').toLowerCase();
                                return name.includes(searchTerm) || description.includes(searchTerm);
                            });
                        }
                    } else {
                        console.warn(`[BulkUpdate] Direct endpoint failed with status ${response.status}: ${url}`);
                    }
                } catch (fetchError) {
                    console.warn(`[BulkUpdate] Error fetching from direct endpoint ${url}:`, fetchError);
                }
            }

            // Fallback to bulk-update lookup endpoint
            if (!data) {
                let fallbackUrl = `/api/bulk-update/lookup/${facet}/${fieldId}`;
                const params = [];
                if (query && query.trim().length > 0) {
                    params.push(`q=${encodeURIComponent(query.trim())}`);
                }
                if (segmentId !== null) {
                    params.push(`segmentId=${segmentId}`);
                }
                if (params.length > 0) {
                    fallbackUrl += `?${params.join('&')}`;
                }
                try {
                    const response = await fetch(fallbackUrl, {
                        method: 'GET',
                        credentials: 'include'
                    });
                    if (response.ok) {
                        const responseData = await response.json();
                        if (responseData.success && responseData.values) {
                            data = responseData.values;
                            // Filter client-side if query parameter not supported
                            if (data && Array.isArray(data) && query && query.trim().length > 0) {
                                const searchTerm = query.toLowerCase();
                                data = data.filter(item => {
                                    const name = (item.name || item.Name || item.primaryName || item.PrimaryName || '').toLowerCase();
                                    const description = (item.description || item.Description || '').toLowerCase();
                                    return name.includes(searchTerm) || description.includes(searchTerm);
                                });
                            }
                        }
                    } else {
                        console.warn(`[BulkUpdate] Fallback endpoint failed with status ${response.status}: ${fallbackUrl}`);
                    }
                } catch (fallbackError) {
                    console.warn(`[BulkUpdate] Error fetching from fallback endpoint ${fallbackUrl}:`, fallbackError);
                }
            }

            return data || [];
        } catch (error) {
            console.error('Error searching lookup values:', error);
            return [];
        }
    }

    /**
     * Render definition fields with placeholder and helper
     */
    function renderDefinitions() {
        const container = document.getElementById('definitionFields');
        if (!container) {
            console.error('[BulkUpdate] definitionFields container not found in renderDefinitions!');
            return;
        }

        console.log('[BulkUpdate] renderDefinitions called');
        console.log('[BulkUpdate] definitions:', definitions);
        console.log('[BulkUpdate] definitions.length:', definitions ? definitions.length : 0);
        console.log('[BulkUpdate] editableItemCount:', editableItemCount, 'workflowItemCount:', workflowItemCount);

        container.innerHTML = '';

        // If all items are under workflow, show disabled message
        if (editableItemCount === 0 && workflowItemCount > 0) {
            console.log('[BulkUpdate] All items are under workflow, showing disabled message');
            container.innerHTML = `<div class="no-items-message" style="color: #856404;">
                <i class="fas fa-lock"></i>
                <span>${escapeHtml(tr('bulkUpdate.allWorkflowCannotEdit'))}</span>
            </div>`;
            return;
        }

        if (!definitions || definitions.length === 0) {
            console.warn('[BulkUpdate] No definitions to render');
            container.innerHTML = `<div class="no-items-message">
                <i class="fas fa-exclamation-circle"></i>
                <span>${escapeHtml(tr('bulkUpdate.noDefinitionFields'))}</span>
            </div>`;
            return;
        }

        // Add placeholder message
        const placeholder = document.createElement('div');
        placeholder.className = 'definition-placeholder';
        placeholder.id = 'definitionPlaceholder';
        placeholder.innerHTML = `<i class="fas fa-hand-pointer"></i>${escapeHtml(tr('bulkUpdate.selectDefinitionHint'))}`;
        container.appendChild(placeholder);

        // Add helper text (hidden initially)
        const helper = document.createElement('div');
        helper.className = 'definition-helper';
        helper.id = 'definitionHelper';
        helper.innerHTML = `<i class="fas fa-info-circle"></i>${escapeHtml(tr('bulkUpdate.selectValueHint'))}`;
        container.appendChild(helper);

        console.log('[BulkUpdate] Rendering', definitions.length, 'definition fields');

        definitions.forEach(def => {
            console.log('[BulkUpdate] Rendering field:', def.fieldId, def.displayName, def.fieldType);
            const fieldDiv = document.createElement('div');
            fieldDiv.className = 'definition-field';
            fieldDiv.dataset.fieldId = def.fieldId;

            let labelHtml;
            if (def.fieldId === 'delete_roles') {
                labelHtml = `<label class="field-label delete-warning" for="val_${def.fieldId}">
                    <i class="fas fa-exclamation-triangle" style="color: #dc3545; margin-right: 4px;"></i>
                    ${def.displayName}
                </label>`;
            } else {
                labelHtml = `<label class="field-label" for="val_${def.fieldId}">${def.displayName}</label>`;
            }

            let inputHtml = '';
            let useSearchField = false;
            
            if (def.fieldType === 'CHECKBOX') {
                inputHtml = `<div class="field-input field-checkbox">
                    <input type="checkbox" id="val_${def.fieldId}">
                </div>`;
            } else if (def.fieldType === 'LOOKUP' || def.fieldType === 'REFERENCE') {
                // Use search field for all LOOKUP and REFERENCE fields
                useSearchField = true;
                // Create a placeholder div that will be replaced with the search field
                inputHtml = `<div class="field-input"></div>`;
            } else {
                // Fallback to select for any other field types
                inputHtml = `<div class="field-input">
                    <select id="val_${def.fieldId}">
                        <option value="">${escapeHtml(tr('bulkUpdate.optionPleaseSelect'))}</option>
                    </select>
                </div>`;
            }

            let infoHtml = '';
            if (def.fieldId === 'reassign_to') {
                infoHtml = `<i class="fas fa-info-circle field-info" title="Select a person to reassign roles to"></i>`;
            } else if (def.fieldId === 'delete_roles') {
                infoHtml = `<i class="fas fa-info-circle field-info" title="This will permanently delete the role assignments. This action cannot be undone."></i>`;
            }

            fieldDiv.innerHTML = labelHtml + inputHtml + infoHtml;
            container.appendChild(fieldDiv);

            // Create search field if needed
            if (useSearchField) {
                const inputWrapper = fieldDiv.querySelector('.field-input');
                if (inputWrapper) {
                    console.log('[BulkUpdate] Creating search field for:', def.fieldId, def.displayName);
                    const searchFieldWrapper = createSearchableLookupField(def.fieldId, def.displayName);
                    inputWrapper.replaceWith(searchFieldWrapper);
                    
                    const searchInput = searchFieldWrapper.querySelector('.search-lookup-input');
                    const hiddenInput = searchFieldWrapper.querySelector('input[type="hidden"]');
                    
                    // For attribute facet, check if dataset/glossary fields should be disabled
                    if (selectionData.facet && selectionData.facet.toLowerCase() === 'attribute' && 
                        (def.fieldId === 'dataset' || def.fieldId === 'glossary')) {
                        // Check if attributes belong to different segments
                        if (attributeSegmentInfo && !attributeSegmentInfo.allSameSegment) {
                            searchInput.disabled = true;
                            searchInput.setAttribute('title', 'This field is disabled because selected attributes belong to different segments.');
                            fieldDiv.style.opacity = '0.6';
                            // Add helper text
                            addSegmentHelperText(searchInput, 'Dataset and Glossary fields are disabled because selected attributes belong to different segments.');
                        }
                    }
                    
                    // Add change handler for search field
                    searchInput.addEventListener('change', (e) => {
                        const selectedId = hiddenInput.value;
                        console.log('[BulkUpdate] Search field value changed:', def.fieldId, 'value:', selectedId);
                        handleValueChange(e, def.fieldId, selectedId);
                    });
                }
            } else {
                const input = fieldDiv.querySelector('select, input[type="checkbox"]');
                if (input) {
                    // For attribute facet, check if dataset/glossary fields should be disabled
                    if (selectionData.facet && selectionData.facet.toLowerCase() === 'attribute' && 
                        (def.fieldId === 'dataset' || def.fieldId === 'glossary')) {
                        // Check if attributes belong to different segments
                        if (attributeSegmentInfo && !attributeSegmentInfo.allSameSegment) {
                            input.disabled = true;
                            input.setAttribute('title', 'This field is disabled because selected attributes belong to different segments.');
                            fieldDiv.style.opacity = '0.6';
                            // Add helper text
                            addSegmentHelperText(input, 'Dataset and Glossary fields are disabled because selected attributes belong to different segments.');
                        }
                    }
                    
                    // Load lookup values immediately for dropdowns (fallback for non-search fields)
                    if (def.fieldType !== 'CHECKBOX' && def.lookupTable && !useSearchField) {
                        loadLookupValues(def.fieldId);
                    }
                    // Add change handler (for dropdowns and checkboxes)
                    input.addEventListener('change', (e) => {
                        console.log('[BulkUpdate] Field value changed:', def.fieldId, 'value:', e.target.value || e.target.checked);
                        handleValueChange(e, def.fieldId);
                    });
                    // Also add input handler for immediate feedback (for select elements)
                    if (input.tagName === 'SELECT') {
                        input.addEventListener('input', (e) => {
                            console.log('[BulkUpdate] Field value input:', def.fieldId, 'value:', e.target.value);
                            handleValueChange(e, def.fieldId);
                        });
                    }
                }
            }
        });

        console.log('[BulkUpdate] Finished rendering', definitions.length, 'definition fields');
    }

    // Removed handleDefinitionSelect - no longer needed without radio buttons

    /**
     * Get the API method name for a given facet and fieldId
     * Returns the method name to call on BUDG_API_SERVICE, or null if not found
     */
    function getLookupApiMethod(facet, fieldId) {
        const mapping = {
            'dataset': {
                'type': 'getDatasetTypeList',
                'system_short_name': 'getSystemsList',
                'glossary_name': 'getGlossaryList',
                'lifecycle': 'getDatasetLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'interface': {
                'target_system': 'getSystemsList',
                'source_system': 'getSystemsList',
                'frequency': 'getInterfaceFrequencies',
                'transfer_method': 'getInterfaceTransferMethods',
                'transfer_format': 'getInterfaceTransferFormats',
                'interface_classification': 'getInterfaceClassifications',
                'automation_level': 'getInterfaceAutomation',
                'lifecycle': 'getInterfaceLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'system': {
                'parent': 'getSystemsList',
                'type': 'getSystemTypeList',
                'classification': 'getSystemClassifications',
                'lifecycle': 'getLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'glossary': {
                'parent': 'getGlossaryList',
                'type': 'getGlossaryTypeList',
                'format_type': 'getGlossaryFormatTypeList',
                'security': 'getGlossarySecurityList',
                'kde': 'getGlossaryKdeList',
                'lifecycle': 'getGlossaryLifecycleList',
                'confidentiality': 'getCiaRatings',
                'integrity': 'getCiaRatings',
                'availability': 'getCiaRatings',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'process': {
                'parent': 'getAllProcesses',
                'type': 'getProcessTypeList',
                'lifecycle': 'getProcessLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'policy': {
                'parent': 'getAllPolicies',
                'type': 'getPolicyTypeList',
                'lifecycle': 'getPolicyLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'project': {
                'parent': 'getAllProjects',
                'type': 'getProjectTypeList',
                'classification': 'getProjectClassificationList',
                'lifecycle': 'getProjectLifecycleList',
                'rag': 'getProjectRagList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'people': {
                'org_unit': 'getOrgUnits',
                'lifecycle': 'getLifecycleStatuses'
            },
            'role': {
                'reassign_to': 'getPeople'
            },
            'business-area': {
                'parent': 'getBusinessAreas',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'businessarea': {
                'parent': 'getBusinessAreas',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'committee': {
                'parent': 'getCommittees',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'legal-entity': {
                'parent': 'getLegalEntities',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'legalentity': {
                'parent': 'getLegalEntities',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'product': {
                'parent': 'getProductList',
                'lifecycle': 'getProductLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'capability': {
                'type': 'getCapabilityTypeList',
                'classification': 'getCapabilityClassificationList',
                'lifecycle': 'getCapabilityLifecycleList',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'client': {
                'parent': 'getClients',
                'axon_status': 'getStatusList',
                'axon_viewing': 'getViewingList'
            },
            'attribute': {
                'glossary': 'getGlossaryList'
            }
        };
        
        return mapping[facet]?.[fieldId] || null;
    }

    /**
     * Get direct API endpoint for fields that don't have API service methods
     * Returns the endpoint URL or null if not found
     */
    function getLookupApiEndpoint(facet, fieldId) {
        const endpointMapping = {
            'regulation': {
                'parent': '/api/regulation/parent-picker',
                'business_area': '/api/business-area/list',
                'maturity': '/api/regulation-maturity/list',
                'probability': '/api/regulation-probability/list',
                'impact_rating': '/api/regulation-impact-rating/list',
                'legal_advice': '/api/legal-advice-type/list',
                'stage': '/api/regulation-stage/list',
                'compliance_level': '/api/regulation-compliance-level/list',
                'axon_status': '/api/status/list',
                'axon_viewing': '/api/viewing/list'
            },
            'people': {
                // Profile uses role table, handled by standard bulk-update lookup endpoint
            },
            'role': {
                'change_status': '/api/role-status/list'
            },
            'attribute': {
                'dataset': '/api/dataset/list',
                'glossary': '/api/glossary/list',
                'requirement': '/api/attribute/lookups/requirements',
                'origin': '/api/attribute/lookups/originations',
                'editability': '/api/attribute/lookups/editabilities'
            },
            'dataset': {
                'type': '/api/dataset-type/list',
                'system_short_name': '/api/system/list',
                'glossary_name': '/api/glossary/list',
                'lifecycle': '/api/dataset-lifecycle/list',
                'axon_status': '/api/status/list',
                'axon_viewing': '/api/viewing/list'
            },
            'committee': {
                'classification': '/api/committee-classification/list',
                'lifecycle': '/api/committee-lifecycle/list',
                'type': '/api/committee-type/list'
            },
            'business-area': {
                'lifecycle': '/api/business-area-lifecycle/list'
            },
            'businessarea': {
                'lifecycle': '/api/business-area-lifecycle/list'
            },
            'client': {
                'lifecycle': '/api/client-lifecycle/list'
            },
            'org-unit': {
                'parent': '/api/org-unit/list'
            },
            'orgunit': {
                'parent': '/api/org-unit/list'
            }
        };
        
        // Handle segment field for all facets
        if (fieldId === 'segment') {
            return '/api/segments/accessible';
        }
        
        return endpointMapping[facet]?.[fieldId] || null;
    }

    /**
     * Load lookup values for a dropdown
     * Uses BUDG_API_SERVICE when available, otherwise falls back to direct API calls
     * Special handling for parent field: filters by segment rules
     */
    async function loadLookupValues(fieldId) {
        const facet = selectionData.facet;
        const select = document.getElementById(`val_${fieldId}`);
        if (!select) return;

        select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionLoading')) + '</option>';

        // Note: Special handling for parent, dataset system/glossary, and attribute dataset/glossary fields
        // is now handled in searchLookupValues() function which is called from createSearchableLookupField()
        // The segment filtering logic is integrated into the search functionality

        try {
            let data = null;
            const apiMethod = getLookupApiMethod(facet, fieldId);
            const apiEndpoint = getLookupApiEndpoint(facet, fieldId);

            // Try API service method first
            if (apiMethod && window.BUDG_API_SERVICE && typeof window.BUDG_API_SERVICE[apiMethod] === 'function') {
                try {
                    data = await window.BUDG_API_SERVICE[apiMethod]();
                } catch (apiError) {
                    console.warn(`API service method ${apiMethod} failed, trying direct endpoint:`, apiError);
                    // Fall through to direct endpoint
                }
            }

            // If API service didn't work, try direct endpoint
            if (!data && apiEndpoint) {
                const response = await fetch(apiEndpoint, {
                    method: 'GET',
                    credentials: 'include'
                });
                if (response.ok) {
                    data = await response.json();
                }
            }

            // If still no data, fall back to old bulk-update endpoint
            if (!data) {
                const response = await fetch(`/api/bulk-update/lookup/${facet}/${fieldId}`, {
                    method: 'GET',
                    credentials: 'include'
                });
                const responseData = await response.json();
                if (responseData.success && responseData.values) {
                    data = { values: responseData.values };
                }
            }

            // Handle different response formats
            let values = [];
            if (Array.isArray(data)) {
                values = data;
            } else if (data?.data && Array.isArray(data.data)) {
                values = data.data;
            } else if (data?.values && Array.isArray(data.values)) {
                values = data.values;
            } else if (data?.segments && Array.isArray(data.segments)) {
                values = data.segments;
            }

            // Populate select
            if (values.length > 0) {
                select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionPleaseSelect')) + '</option>';
                values.forEach(item => {
                    const option = document.createElement('option');
                    // Handle different ID field names
                    const id = item.id || item.ID || item.Id || null;
                    if (id === null) {
                        console.warn('Item missing ID field:', item);
                        return;
                    }
                    option.value = id;
                    // Handle different name field names (including lowercase variants from API)
                    option.textContent = item.name || item.Name || item.primaryname || item.primaryName || item.PrimaryName || 
                                        item.longName || item.longname || item.LongName || item.displayName || item.DisplayName || 
                                        String(id);
                    select.appendChild(option);
                });
            } else {
                select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionNoOptions')) + '</option>';
            }
        } catch (error) {
            console.error('Error loading lookup values:', error);
            select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionErrorLoading')) + '</option>';
        }
    }

    /**
     * Load attribute dataset/glossary options with segment filtering
     * - If attributes belong to different segments, disable the field
     * - If attributes belong to same segment, filter options by segment rules
     */
    async function loadAttributeReferenceOptionsWithSegmentFilter(select, fieldId) {
        try {
            // Check if we have segment info
            if (!attributeSegmentInfo) {
                // Try to get segment info from loaded items
                if (loadedItems && loadedItems.length > 0) {
                    const segmentIds = new Set();
                    for (const item of loadedItems) {
                        const segmentId = item.segmentId || item.SegmentId || item.segment_id;
                        if (segmentId !== undefined && segmentId !== null) {
                            segmentIds.add(segmentId);
                        } else {
                            segmentIds.add(1); // Default to Enterprise
                        }
                    }
                    
                    if (segmentIds.size > 1) {
                        // Different segments - disable field
                        select.disabled = true;
                        select.innerHTML = '<option value="">Disabled - attributes belong to different segments</option>';
                        addSegmentHelperText(select, 'Dataset and Glossary fields are disabled because selected attributes belong to different segments.');
                        return;
                    }
                    
                    // All same segment - use the common segment ID
                    const commonSegmentId = Array.from(segmentIds)[0];
                    await loadFilteredAttributeReferenceOptions(select, fieldId, commonSegmentId);
                } else {
                    select.innerHTML = '<option value="">No items loaded</option>';
                }
                return;
            }
            
            // Use attributeSegmentInfo
            if (!attributeSegmentInfo.allSameSegment) {
                // Different segments - disable field
                select.disabled = true;
                select.innerHTML = '<option value="">Disabled - attributes belong to different segments</option>';
                addSegmentHelperText(select, 'Dataset and Glossary fields are disabled because selected attributes belong to different segments.');
                return;
            }
            
            // All same segment - filter by segment
            const segmentId = attributeSegmentInfo.commonSegmentId;
            await loadFilteredAttributeReferenceOptions(select, fieldId, segmentId);
        } catch (error) {
            console.error('Error loading attribute reference options:', error);
            select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionErrorLoading')) + '</option>';
        }
    }

    /**
     * Load filtered dataset or glossary options for attributes based on segment
     */
    async function loadFilteredAttributeReferenceOptions(select, fieldId, segmentId) {
        try {
            const endpoint = fieldId === 'dataset' ? '/api/dataset/list' : '/api/glossary/list';
            const url = `${endpoint}?segmentId=${segmentId}`;
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            let values = [];
            if (Array.isArray(data)) {
                values = data;
            } else if (data.data && Array.isArray(data.data)) {
                values = data.data;
            } else if (data.values && Array.isArray(data.values)) {
                values = data.values;
            }

            // Populate select
            if (values.length > 0) {
                select.disabled = false;
                select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionPleaseSelect')) + '</option>';
                values.forEach(item => {
                    const option = document.createElement('option');
                    const id = item.id || item.ID || item.Id || null;
                    if (id === null) {
                        console.warn('Item missing ID field:', item);
                        return;
                    }
                    option.value = id;
                    option.textContent = item.name || item.Name || item.primaryName || item.PrimaryName || 
                                        item.description || item.Description || 
                                        String(id);
                    select.appendChild(option);
                });
            } else {
                select.disabled = false;
                select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionNoOptions')) + '</option>';
            }
        } catch (error) {
            console.error('Error loading filtered attribute reference options:', error);
            select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionErrorLoading')) + '</option>';
        }
    }

    /**
     * Add helper text explaining why a field is disabled
     */
    function addSegmentHelperText(select, message) {
        const fieldDiv = select.closest('.definition-field');
        if (fieldDiv) {
            let helperText = fieldDiv.querySelector('.attribute-segment-helper');
            if (!helperText) {
                helperText = document.createElement('div');
                helperText.className = 'attribute-segment-helper';
                helperText.style.cssText = 'font-size: 0.8rem; color: #856404; margin-top: 8px; font-style: italic;';
                helperText.innerHTML = `<i class="fas fa-info-circle"></i> ${message}`;
                fieldDiv.appendChild(helperText);
            }
        }
    }

    /**
     * Load parent options with segment filtering
     * - If objects belong to different segments, disable the field
     * - If objects belong to same segment, filter options by segment rules
     */
    async function loadParentOptionsWithSegmentFilter(select, facet) {
        try {
            // Get segment IDs from loaded items
            if (!loadedItems || loadedItems.length === 0) {
                select.innerHTML = '<option value="">No items loaded</option>';
                return;
            }

            const segmentIds = new Set();
            for (const item of loadedItems) {
                const segmentId = item.segmentId || item.SegmentId || item.segment_id;
                if (segmentId !== undefined && segmentId !== null) {
                    segmentIds.add(segmentId);
                } else {
                    // Default to Enterprise (1) if segment not found
                    segmentIds.add(1);
                }
            }

            // If objects belong to different segments, disable the parent field
            if (segmentIds.size > 1) {
                select.disabled = true;
                select.innerHTML = '<option value="">Disabled - objects belong to different segments</option>';
                
                // Add helper text
                const fieldDiv = select.closest('.definition-field');
                if (fieldDiv) {
                    let helperText = fieldDiv.querySelector('.parent-segment-helper');
                    if (!helperText) {
                        helperText = document.createElement('div');
                        helperText.className = 'parent-segment-helper';
                        helperText.style.cssText = 'font-size: 0.8rem; color: #856404; margin-top: 8px; font-style: italic;';
                        helperText.innerHTML = '<i class="fas fa-info-circle"></i> Parent field is disabled because selected objects belong to different segments.';
                        fieldDiv.appendChild(helperText);
                    }
                }
                return;
            }

            // All objects are in the same segment - get filtered parent options
            const objectIds = loadedItems.map(item => item.ID || item.id || item.Id).filter(id => id !== undefined && id !== null);
            if (objectIds.length === 0) {
                select.innerHTML = '<option value="">No valid object IDs</option>';
                return;
            }

            // Call the new endpoint to get filtered parent options
            const response = await fetch(`/api/bulk-update/parent-options/${facet}?objectIds=${objectIds.join(',')}`, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            let values = [];
            if (data.success && data.values && Array.isArray(data.values)) {
                values = data.values;
            } else if (Array.isArray(data)) {
                values = data;
            }

            // Populate select
            if (values.length > 0) {
                select.disabled = false;
                select.innerHTML = '<option value="">' + escapeHtml(tr('bulkUpdate.optionPleaseSelect')) + '</option>';
                values.forEach(item => {
                    const option = document.createElement('option');
                    const id = item.id || item.ID || item.Id || null;
                    if (id === null) {
                        console.warn('Item missing ID field:', item);
                        return;
                    }
                    option.value = id;
                    // Handle different name field names (including lowercase variants from API)
                    option.textContent = item.name || item.Name || item.primaryname || item.primaryName || item.PrimaryName || 
                                        item.longName || item.longname || item.LongName || item.displayName || item.DisplayName || 
                                        String(id);
                    select.appendChild(option);
                });
            } else {
                select.disabled = false;
                select.innerHTML = '<option value="">No parent options available</option>';
            }

            // Remove helper text if it exists (objects are in same segment)
            const fieldDiv = select.closest('.definition-field');
            if (fieldDiv) {
                const helperText = fieldDiv.querySelector('.parent-segment-helper');
                if (helperText) {
                    helperText.remove();
                }
            }
        } catch (error) {
            console.error('Error loading parent options with segment filter:', error);
            select.innerHTML = '<option value="">Error loading parent options</option>';
        }
    }

    /**
     * Handle value input change for any field
     * Supports both select dropdowns and search input fields
     */
    function handleValueChange(event, fieldId, providedValue = null) {
        const input = event.target;
        let value = null;
        
        // Check if this is a search input field (has hidden input for ID)
        const hiddenInput = document.getElementById(`val_${fieldId}_id`);
        if (hiddenInput && input.classList.contains('search-lookup-input')) {
            // This is a search field - get value from hidden input
            value = providedValue !== null ? providedValue : (hiddenInput.value || null);
            if (value === '' || value === null) {
                // Clear the display value if no ID selected
                input.value = '';
            }
        } else if (input.type === 'checkbox') {
            value = input.checked ? true : null;
        } else {
            // For dropdowns, only store value if not empty and not the default "Please select" option
            const trimmedValue = input.value ? input.value.trim() : '';
            value = trimmedValue !== '' && trimmedValue !== tr('bulkUpdate.optionPleaseSelect') ? trimmedValue : null;
        }
        
        console.log('[BulkUpdate] handleValueChange:', {
            fieldId: fieldId,
            value: value,
            inputType: input.type,
            inputValue: input.value || input.checked,
            isSearchField: hiddenInput !== null
        });
        
        // For Dataset facet: Check mutually exclusive fields before updating
        const facet = selectionData?.facet?.toLowerCase();
        if (facet === 'dataset') {
            const mutuallyExclusiveFields = ["system_short_name", "glossary_name", "segment"];
            if (mutuallyExclusiveFields.includes(fieldId) && value !== null) {
                // Check if another mutually exclusive field is already selected
                const otherSelected = mutuallyExclusiveFields
                    .filter(f => f !== fieldId)
                    .some(f => selectedValues[f] !== undefined && selectedValues[f] !== null && selectedValues[f] !== '');
                
                if (otherSelected) {
                    showToast(tr('bulkUpdate.toastDatasetTriple'), 'error');
                    // Reset the input to previous value
                    if (input.type === 'checkbox') {
                        input.checked = false;
                    } else if (input.classList.contains('search-lookup-input')) {
                        // Clear search field
                        input.value = '';
                        const hiddenInput = document.getElementById(`val_${fieldId}_id`);
                        if (hiddenInput) hiddenInput.value = '';
                    } else {
                        input.value = '';
                    }
                    return;
                }
                
                // Clear other mutually exclusive fields if one is being set
                mutuallyExclusiveFields.forEach(key => {
                    if (key !== fieldId && selectedValues[key] !== undefined) {
                        delete selectedValues[key];
                        const otherInput = document.getElementById(`val_${key}`);
                        const otherHiddenInput = document.getElementById(`val_${key}_id`);
                        if (otherInput) {
                            if (otherInput.type === 'checkbox') {
                                otherInput.checked = false;
                            } else if (otherInput.classList.contains('search-lookup-input')) {
                                // Clear search field
                                otherInput.value = '';
                                if (otherHiddenInput) otherHiddenInput.value = '';
                            } else {
                                otherInput.value = ''; // Clear the other dropdown
                            }
                        }
                    }
                });
            }
        }
        
        // For Role facet: Check mutually exclusive role operations
        if (facet === 'role') {
            const roleOperationFields = ["accept_roles", "reassign_to", "change_status", "delete_roles"];
            if (roleOperationFields.includes(fieldId) && value !== null) {
                // Check if another role operation is already selected
                const otherSelected = roleOperationFields
                    .filter(f => f !== fieldId)
                    .some(f => selectedValues[f] !== undefined && selectedValues[f] !== null && selectedValues[f] !== '');
                
                if (otherSelected) {
                    showToast(tr('bulkUpdate.toastOneRoleOp'), 'error');
                    // Reset the input to previous value
                    if (input.type === 'checkbox') {
                        input.checked = false;
                    } else if (input.classList.contains('search-lookup-input')) {
                        // Clear search field
                        input.value = '';
                        const hiddenInput = document.getElementById(`val_${fieldId}_id`);
                        if (hiddenInput) hiddenInput.value = '';
                    } else {
                        input.value = '';
                    }
                    return;
                }
                
                // Clear other role operation fields if one is being set
                roleOperationFields.forEach(key => {
                    if (key !== fieldId && selectedValues[key] !== undefined) {
                        delete selectedValues[key];
                        const otherInput = document.getElementById(`val_${key}`);
                        const otherHiddenInput = document.getElementById(`val_${key}_id`);
                        if (otherInput) {
                            if (otherInput.type === 'checkbox') {
                                otherInput.checked = false;
                            } else if (otherInput.classList.contains('search-lookup-input')) {
                                // Clear search field
                                otherInput.value = '';
                                if (otherHiddenInput) otherHiddenInput.value = '';
                            } else {
                                otherInput.value = '';
                            }
                        }
                    }
                });
            }
        }
        
        // For all facets except org-unit: Check Parent and Segment mutually exclusive
        const orgUnitFacets = ['org-unit', 'orgunit'];
        if (!orgUnitFacets.includes(facet)) {
            const parentSegmentFields = ["parent", "segment"];
            if (parentSegmentFields.includes(fieldId) && value !== null) {
                // Check if the other mutually exclusive field is already selected
                const otherFieldId = fieldId === "parent" ? "segment" : "parent";
                const otherSelected = selectedValues[otherFieldId] !== undefined && 
                                     selectedValues[otherFieldId] !== null && 
                                     selectedValues[otherFieldId] !== '';
                
                if (otherSelected) {
                    showToast(tr('bulkUpdate.msgParentSegmentMutuallyExclusive'), 'error');
                    // Reset the input to previous value
                    if (input.type === 'checkbox') {
                        input.checked = false;
                    } else if (input.classList.contains('search-lookup-input')) {
                        // Clear search field
                        input.value = '';
                        const hiddenInput = document.getElementById(`val_${fieldId}_id`);
                        if (hiddenInput) hiddenInput.value = '';
                    } else {
                        input.value = '';
                    }
                    return;
                }
                
                // Clear the other mutually exclusive field if one is being set
                if (selectedValues[otherFieldId] !== undefined) {
                    delete selectedValues[otherFieldId];
                    const otherInput = document.getElementById(`val_${otherFieldId}`);
                    const otherHiddenInput = document.getElementById(`val_${otherFieldId}_id`);
                    if (otherInput) {
                        if (otherInput.type === 'checkbox') {
                            otherInput.checked = false;
                        } else if (otherInput.classList.contains('search-lookup-input')) {
                            // Clear search field
                            otherInput.value = '';
                            if (otherHiddenInput) otherHiddenInput.value = '';
                        } else {
                            otherInput.value = ''; // Clear the other dropdown
                        }
                    }
                }
            }
        }
        
        // Update selectedValues map
        if (value !== null) {
            selectedValues[fieldId] = value;
        } else {
            // Remove from map if value is cleared
            delete selectedValues[fieldId];
        }
        
        console.log('[BulkUpdate] selectedValues updated:', selectedValues);
        
        // Hide placeholder, show helper when first value is selected
        const placeholder = document.getElementById('definitionPlaceholder');
        const helper = document.getElementById('definitionHelper');
        if (Object.keys(selectedValues).length > 0) {
            if (placeholder) placeholder.style.display = 'none';
            if (helper) helper.classList.add('visible');
        } else {
            if (placeholder) placeholder.style.display = 'block';
            if (helper) helper.classList.remove('visible');
        }
        
        // Immediately update save button state
        updateSaveButtonState();
    }

    /**
     * Update Save button enabled state with tooltip
     */
    function updateSaveButtonState() {
        const saveBtn = document.getElementById('saveBtn');
        const saveCloseBtn = document.getElementById('saveCloseBtn');

        // Enable if at least one field has a value selected
        const hasSelectedValues = Object.keys(selectedValues).length > 0;
        const isEnabled = hasSelectedValues && !isSaving && editableItemCount > 0;

        console.log('[BulkUpdate] updateSaveButtonState:', {
            hasSelectedValues: hasSelectedValues,
            selectedValuesCount: Object.keys(selectedValues).length,
            isSaving: isSaving,
            editableItemCount: editableItemCount,
            isEnabled: isEnabled
        });

        if (saveBtn) {
            const wasDisabled = saveBtn.disabled;
            saveBtn.disabled = !isEnabled;
            if (wasDisabled && !saveBtn.disabled) {
                console.log('[BulkUpdate] Save button enabled!');
            }
            saveBtn.title = isEnabled ? '' : 'Select at least one field value to enable saving.';
        }
        if (saveCloseBtn) {
            const wasDisabled = saveCloseBtn.disabled;
            saveCloseBtn.disabled = !isEnabled;
            if (wasDisabled && !saveCloseBtn.disabled) {
                console.log('[BulkUpdate] Save & Close button enabled!');
            }
            saveCloseBtn.title = isEnabled ? '' : 'Select at least one field value to enable saving.';
        }
    }

    /**
     * Handle Save button
     */
    async function handleSave() {
        if (isSaving) return;

        // 1. Selection Check
        if (!selectionData || !selectionData.objectIds || selectionData.objectIds.length === 0) {
            showToast(tr('bulkUpdate.msgNoSelection'), 'error');
            return;
        }

        // 2. Facet Check
        if (!selectionData.facet) {
            showToast(tr('bulkUpdate.toastUnableFacet'), 'error');
            return;
        }

        // 3. Editable Items Check
        if (editableItemCount === 0) {
            showToast(tr('bulkUpdate.toastAllWorkflow'), 'error');
            return;
        }

        // 4. Value Presence Check
        if (Object.keys(selectedValues).length === 0) {
            showToast(tr('bulkUpdate.toastSelectFieldValue'), 'error');
            return;
        }

        // 5. Mutually Exclusive Fields Validation (for Dataset)
        if (!validateMutuallyExclusiveSelections(selectedValues)) {
            return;
        }

        // All validations passed, show confirmation
        const confirmed = await showSaveConfirmation();
        if (confirmed) {
            await executeBulkUpdate(false);
        }
    }

    /**
     * Handle Save & Close button
     */
    async function handleSaveAndClose() {
        if (isSaving) return;

        // 1. Selection Check
        if (!selectionData || !selectionData.objectIds || selectionData.objectIds.length === 0) {
            showToast(tr('bulkUpdate.msgNoSelection'), 'error');
            return;
        }

        // 2. Facet Check
        if (!selectionData.facet) {
            showToast(tr('bulkUpdate.toastUnableFacet'), 'error');
            return;
        }

        // 3. Editable Items Check
        if (editableItemCount === 0) {
            showToast(tr('bulkUpdate.toastAllWorkflow'), 'error');
            return;
        }

        // 4. Value Presence Check
        if (Object.keys(selectedValues).length === 0) {
            showToast(tr('bulkUpdate.toastSelectFieldValue'), 'error');
            return;
        }

        // 5. Mutually Exclusive Fields Validation (for Dataset)
        if (!validateMutuallyExclusiveSelections(selectedValues)) {
            return;
        }

        // All validations passed, show confirmation
        const confirmed = await showSaveConfirmation();
        if (confirmed) {
            const success = await executeBulkUpdate(true);
            if (success) {
                sessionStorage.removeItem('bulkUpdateSelection');
                window.location.href = '/search.html';
            }
        }
    }

    /**
     * Show appropriate confirmation modal before save
     */
    async function showSaveConfirmation() {
        const itemCount = editableItemCount;
        const selectedFields = Object.keys(selectedValues);
        const fieldCount = selectedFields.length;

        // Check if delete_roles is in the selected fields
        if (selectedFields.includes('delete_roles')) {
            return await showConfirmationModal(
                'Delete Role Assignments',
                `You are about to <strong>permanently delete ${itemCount} role assignment${itemCount > 1 ? 's' : ''}</strong>.<br><br>
                ${workflowItemCount > 0 ? `<em>${workflowItemCount} item${workflowItemCount > 1 ? 's' : ''} under workflow will be skipped.</em><br><br>` : ''}
                This action cannot be undone.<br><br>
                Are you sure you want to continue?`,
                true
            );
        }

        // Build list of fields being updated
        const fieldNames = selectedFields.map(fieldId => {
            const def = definitions.find(d => d.fieldId === fieldId);
            return def ? def.displayName : fieldId;
        }).join(', ');

        return await showConfirmationModal(
            'Confirm Bulk Update',
            `You are about to update <strong>${itemCount} item${itemCount > 1 ? 's' : ''}</strong>.<br><br>
            ${workflowItemCount > 0 ? `<em>${workflowItemCount} item${workflowItemCount > 1 ? 's' : ''} under workflow will be skipped.</em><br><br>` : ''}
            This action cannot be undone.<br><br>
            Do you want to continue?`,
            false
        );
    }

    /**
     * Handle Close button
     */
    function handleClose() {
        sessionStorage.removeItem('bulkUpdateSelection');
        window.location.href = '/search.html';
    }

    /**
     * Validate mutually exclusive fields
     */
    function validateMutuallyExclusiveSelections(updates) {
        const facet = selectionData?.facet?.toLowerCase();
        
        // Validate Dataset mutually exclusive fields
        // System and Glossary can be updated together, but Segment is mutually exclusive with both
        if (facet === 'dataset') {
            const hasSegment = updates['segment'] !== undefined && 
                              updates['segment'] !== null && 
                              updates['segment'] !== '';
            const hasSystem = updates['system_short_name'] !== undefined && 
                             updates['system_short_name'] !== null && 
                             updates['system_short_name'] !== '';
            const hasGlossary = updates['glossary_name'] !== undefined && 
                               updates['glossary_name'] !== null && 
                               updates['glossary_name'] !== '';

            // Segment cannot be updated together with System or Glossary
            if (hasSegment && (hasSystem || hasGlossary)) {
                showToast(tr('bulkUpdate.msgDatasetMutuallyExclusive'), 'error');
                return false;
            }
        }
        
        // Validate Parent and Segment mutually exclusive for all facets except org-unit
        const orgUnitFacets = ['org-unit', 'orgunit'];
        if (!orgUnitFacets.includes(facet)) {
            const hasParent = updates['parent'] !== undefined && 
                             updates['parent'] !== null && 
                             updates['parent'] !== '';
            const hasSegment = updates['segment'] !== undefined && 
                              updates['segment'] !== null && 
                              updates['segment'] !== '';
            
            if (hasParent && hasSegment) {
                showToast(tr('bulkUpdate.msgParentSegmentMutuallyExclusive'), 'error');
                return false;
            }
        }

        return true;
    }

    /**
     * Execute bulk update
     */
    async function executeBulkUpdate(closeAfter) {
        // Check if any fields have values selected
        if (Object.keys(selectedValues).length === 0) {
            showToast(tr('bulkUpdate.toastSelectFieldValue'), 'error');
            return false;
        }

        // Validate mutually exclusive fields for Dataset
        if (!validateMutuallyExclusiveSelections(selectedValues)) {
            return false;
        }

        if (isSaving) return false;
        isSaving = true;

        // Build updates object from all selected values
        // Only include fields that have non-empty values
        const updates = {};
        for (const [fieldId, value] of Object.entries(selectedValues)) {
            if (value !== null && value !== '' && value !== false) {
                updates[fieldId] = value;
            }
        }

        // If no valid updates after filtering, show error
        if (Object.keys(updates).length === 0) {
            showToast(tr('bulkUpdate.toastSelectFieldValue'), 'error');
            isSaving = false;
            return false;
        }

        const saveBtn = document.getElementById('saveBtn');
        const saveCloseBtn = document.getElementById('saveCloseBtn');
        const closeBtn = document.getElementById('closeBtn');

        const originalSaveText = saveBtn?.textContent;
        const originalSaveCloseText = saveCloseBtn?.textContent;

        // Show loading indicator
        const loadingIndicator = document.createElement('div');
        loadingIndicator.id = 'bulkUpdateLoading';
        loadingIndicator.style.cssText = 'position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 10001; background: rgba(255,255,255,0.95); padding: 20px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2); display: flex; align-items: center; gap: 12px;';
        loadingIndicator.innerHTML = '<i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; color: #248567;"></i><span style="font-size: 1rem; color: #333;">' + escapeHtml(tr('bulkUpdate.processing')) + '</span>';
        document.body.appendChild(loadingIndicator);

        if (saveBtn) { saveBtn.textContent = tr('bulkUpdate.saving'); saveBtn.disabled = true; }
        if (saveCloseBtn) { saveCloseBtn.textContent = tr('bulkUpdate.saving'); saveCloseBtn.disabled = true; }
        if (closeBtn) closeBtn.disabled = true;

        try {
            // Filter out workflow-locked items before sending to backend
            const editableObjectIds = loadedItems
                .filter(item => !item.isUnderWorkflow)
                .map(item => item.ID || item.id)
                .filter(id => id !== null && id !== undefined);

            // If no editable items after filtering, show error
            if (editableObjectIds.length === 0) {
                showToast(tr('bulkUpdate.toastAllWorkflow'), 'error');
                isSaving = false;
                const loadingIndicator = document.getElementById('bulkUpdateLoading');
                if (loadingIndicator) loadingIndicator.remove();
                if (saveBtn) { saveBtn.textContent = originalSaveText; saveBtn.disabled = false; }
                if (saveCloseBtn) { saveCloseBtn.textContent = originalSaveCloseText; saveCloseBtn.disabled = false; }
                if (closeBtn) closeBtn.disabled = false;
                return false;
            }

            // For large selections (>100 rows), show additional message
            const itemCount = editableObjectIds.length;
            if (itemCount > 100) {
                loadingIndicator.innerHTML = '<i class="fas fa-spinner fa-spin" style="font-size: 1.5rem; color: #248567;"></i><span style="font-size: 1rem; color: #333;">' + escapeHtml(tr('bulkUpdate.processingMany', { count: itemCount })) + '</span>';
            }

            const response = await fetch('/api/bulk-update', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({
                    facet: selectionData.facet,
                    objectIds: editableObjectIds,
                    updates: updates
                })
            });

            const data = await response.json();

            if (data.success) {
                const message = buildResultMessage(data);
                const toastType = data.skippedRows > 0 ? 'info' : 'success';
                showToast(message, toastType);
                return true;
            } else {
                // Use BUDG-style error messages
                const errorMsg = mapErrorMessage(data.error);
                showToast(errorMsg, 'error');
                return false;
            }
        } catch (error) {
            console.error('Error executing bulk update:', error);
            showToast(tr('bulkUpdate.errorExecuting'), 'error');
            return false;
        } finally {
            isSaving = false;
            // Remove loading indicator
            const loadingIndicator = document.getElementById('bulkUpdateLoading');
            if (loadingIndicator) {
                loadingIndicator.remove();
            }
            if (saveBtn) { saveBtn.textContent = originalSaveText; saveBtn.disabled = false; }
            if (saveCloseBtn) { saveCloseBtn.textContent = originalSaveCloseText; saveCloseBtn.disabled = false; }
            if (closeBtn) closeBtn.disabled = false;
            updateSaveButtonState();
        }
    }

    /**
     * Map backend errors to BUDG-style messages
     */
    function mapErrorMessage(error) {
        if (!error) return tr('bulkUpdate.msgGenericError');

        if (error.includes('permission') || error.includes('unauthorized') || error.includes('Unauthorized')) {
            return tr('bulkUpdate.msgPermissionDenied');
        }
        if (error.includes('mutually') || error.includes('System, Glossary')) {
            return tr('bulkUpdate.msgDatasetMutuallyExclusive');
        }

        return error;
    }

    /**
     * Build result message with BUDG-style formatting
     */
    function buildResultMessage(data) {
        const updatedRows = data.updatedRows || 0;
        const skippedRows = data.skippedRows || 0;

        // Partial success with skipped rows
        if (skippedRows > 0) {
            return tr('bulkUpdate.resultPartialSuccess', { updated: updatedRows, skipped: skippedRows });
        }

        // Full success - use BUDG message
        return tr('bulkUpdate.msgSuccess');
    }

    /**
     * Get view URL for an object
     */
    function getViewUrl(facet, id) {
        const baseUrl = VIEW_URL_MAP[facet.toLowerCase()];
        if (!baseUrl) return '#';

        if (baseUrl.includes('?id=')) return baseUrl + id;
        return baseUrl + id;
    }

    /**
     * Format column name for display
     */
    function formatColumnName(name) {
        return name.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase()).trim();
    }

    /**
     * Format date for display
     */
    function formatDate(dateStr) {
        if (!dateStr) return '';
        try {
            const date = new Date(dateStr);
            return date.toLocaleDateString('en-GB', {
                day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
            });
        } catch (e) {
            return dateStr;
        }
    }

    /**
     * Escape HTML
     */
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Show error message
     */
    function showError(message) {
        const main = document.querySelector('.bulk-update-main');
        if (main) {
            main.innerHTML = `
                <div style="display: flex; flex-direction: column; align-items: center; justify-content: center; height: 50vh; color: #666;">
                    <i class="fas fa-exclamation-triangle" style="font-size: 3rem; margin-bottom: 16px; color: #dc3545;"></i>
                    <p>${message}</p>
                    <button class="btn btn-secondary" onclick="window.location.href='/search.html'" style="margin-top: 16px;">
                        Back to Search
                    </button>
                </div>
            `;
        }
    }

    /**
     * Show toast notification
     */
    function showToast(message, type = 'info') {
        const toast = document.getElementById('toast');
        const toastMsg = document.getElementById('toastMessage');

        if (toast && toastMsg) {
            toastMsg.textContent = message;
            toast.className = 'toast ' + type;
            toast.style.display = 'block';

            const duration = message.length > 100 ? 8000 : 5000;
            setTimeout(() => { toast.style.display = 'none'; }, duration);
        } else {
            alert(message);
        }
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
