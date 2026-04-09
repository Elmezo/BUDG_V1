/**
 * Bulk Delete Page Main Logic
 * Handles loading items, rendering table, and executing bulk deletes
 */

(function () {
    'use strict';

    // State
    let selectionData = null;
    let loadedItems = [];
    let isDeleting = false;
    let validationResults = null;
    let validationData = null; // Store validation data to update table
    let isSuperAdmin = true; // Default true until pre-validate returns; Super Admin = final delete, Admin = Mark as Deleted only
    
    // Warning types (must match backend enum)
    const WARNING_TYPE = {
        STAKEHOLDERS: 'STAKEHOLDERS',
        CHILD_OBJECTS: 'CHILD_OBJECTS',
        SYSTEM_DATASETS: 'SYSTEM_DATASETS'
    };

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
     */
    function normalizeFacetName(facet) {
        if (!facet) return facet;
        
        let normalized = facet.toLowerCase().trim();
        const normalizedForMatch = normalized.replace(/[_\s-]/g, '');
        
        const facetMap = {
            'datasets': 'dataset',
            'dataset': 'dataset',
            'businessarea': 'business-area',
            'business-area': 'business-area',
            'orgunit': 'org-unit',
            'org-unit': 'org-unit',
            'legalentity': 'legal-entity',
            'legal-entity': 'legal-entity'
        };
        
        if (facetMap[normalized]) {
            return facetMap[normalized];
        }
        if (facetMap[normalizedForMatch]) {
            return facetMap[normalizedForMatch];
        }
        
        const keepHyphens = ['business-area', 'org-unit', 'legal-entity'];
        if (keepHyphens.includes(normalized)) {
            return normalized;
        }
        
        return normalized.replace(/-/g, '');
    }

    function tr(key, params) {
        if (window.I18n && typeof window.I18n.t === 'function') {
            return window.I18n.t(key, params);
        }
        return key;
    }

    /**
     * Map English backend validation strings to i18n (matches BulkDeleteValidationHelper / BulkDeleteService).
     */
    function translateBulkDeleteBackendMessage(text) {
        if (text == null || text === '') {
            return text;
        }
        if (typeof text !== 'string') {
            return text;
        }
        const s = text.trim();
        const rules = [
            [/^BUDG Status must be set to "Deleted" before this object can be removed\. Current status: (.+)\. Please update the status first\.$/, (m) =>
                tr('bulkDelete.backend.budgStatusMustDeleted', { statusName: m[1] })],
            [/^Unable to determine object status\. Please try again\.$/, () =>
                tr('bulkDelete.backend.unableToDetermineStatus')],
            [/^Database error during validation: (.+)$/s, (m) =>
                tr('bulkDelete.backend.databaseErrorValidation', { detail: m[1] })],
            [/^Unsupported object type: (.+)$/, (m) =>
                tr('bulkDelete.backend.unsupportedObjectType', { type: m[1] })],
            [/^(.+) not found or has already been removed\.$/, (m) =>
                tr('bulkDelete.backend.notFoundOrRemoved', { name: m[1] })],
            [/^(.+?) has (\d+) linked stakeholders?\. These Stakeholders will be removed if you proceed\.$/, (m) =>
                tr('bulkDelete.backend.stakeholderWarning', { entityName: m[1], count: m[2] })],
            [/^(.+?) has (\d+) active relationships? in the Impact tab\. Please remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.impactRelationships', { entityName: m[1], count: m[2] })],
            [/^(.+?) contains (\d+) child objects?\. Remove or reassign children before deletion\.$/, (m) =>
                tr('bulkDelete.backend.childObjects', { entityName: m[1], count: m[2] })],
            [/^System is linked to (\d+) datasets? that are not deleted\. Remove these datasets first\.$/, (m) =>
                tr('bulkDelete.backend.systemLinkedDatasets', { count: m[1] })],
            [/^Geography has (\d+) regulator links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.geographyRegulatorLinks', { count: m[1] })],
            [/^Geography has (\d+) legal entity links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.geographyLegalLinks', { count: m[1] })],
            [/^Geography has (\d+) regulation links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.geographyRegulationLinks', { count: m[1] })],
            [/^Regulator has (\d+) regulation links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.regulatorRegulationLinks', { count: m[1] })],
            [/^Regulator has (\d+) geography links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.regulatorGeographyLinks', { count: m[1] })],
            [/^🔗?\s*Glossary has (\d+) relationships? with other glossaries in the Relationships tab\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.glossaryGlossaryRelationships', { count: m[1] })],
            [/^Glossary has (\d+) alias names?\. Remove all alias names before deletion\.$/, (m) =>
                tr('bulkDelete.backend.glossaryAliasNames', { count: m[1] })],
            [/^Glossary has (\d+) system links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.glossarySystemLinks', { count: m[1] })],
            [/^Glossary has (\d+) data set links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.glossaryDatasetLinks', { count: m[1] })],
            [/^Glossary has (\d+) attribute links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.glossaryAttributeLinks', { count: m[1] })],
            [/^Glossary has (\d+) data quality rule links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.glossaryDqRuleLinks', { count: m[1] })],
            [/^🔗?\s*Data Set has (\d+) relationships? with other data sets in the Relationships tab\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.datasetRelationships', { count: m[1] })],
            [/^📋?\s*Data Set has (\d+) indirect relationships? in the Data Content Summary tab\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.datasetContentSummary', { count: m[1] })],
            [/^📊?\s*Data Set contains (\d+) attribute(?:s)?\. Remove all attributes before deletion\.$/, (m) =>
                tr('bulkDelete.backend.datasetAttributes', { count: m[1] })],
            [/^🔄?\s*Cannot delete person: This person has a Running or Pending Start change request\. Please complete or cancel the change request first\.$/, () =>
                tr('bulkDelete.backend.peopleActiveCr')],
            [/^👥?\s*Person has a manager and (\d+) persons? reporting to them\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.peopleManagerAndReports', { count: m[1] })],
            [/^👥?\s*Person has a manager\. Remove this relationship before deletion\.$/, () =>
                tr('bulkDelete.backend.peopleHasManager')],
            [/^👥?\s*Person has (\d+) persons? reporting to them\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.peopleHasReports', { count: m[1] })],
            [/^👤?\s*Person is a stakeholder of (\d+) objects?\. Remove these stakeholder relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.personStakeholderOf', { count: m[1] })],
            [/^Person has (\d+) data quality rules?\. Remove these rules before deletion\.$/, (m) =>
                tr('bulkDelete.backend.peopleDqRules', { count: m[1] })],
            [/^📜?\s*Regulation has (\d+) regulator links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.regulationRegulatorLinks', { count: m[1] })],
            [/^🔗?\s*(.+?) has (\d+) relationships? with other (.+?) objects\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.genericFacetRelationships', { entityLabel: m[1], count: m[2], otherLabel: m[3] })],
            [/^🔗?\s*Attribute has (\d+) relationships? with Process, Policy, and Project objects\. Remove these relationships before deletion\.$/, (m) =>
                tr('bulkDelete.backend.attributePppRelationships', { count: m[1] })],
            [/^📚?\s*Interface has (\d+) glossary links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.interfaceGlossaryLinks', { count: m[1] })],
            [/^📋?\s*Regulatory Theme contains (\d+) regulations?\. Remove these regulations before deletion\.$/, (m) =>
                tr('bulkDelete.backend.regulatoryThemeRegulations', { count: m[1] })],
            [/^Data Quality Rule has (\d+) attribute links?\. Remove these links before deletion\.$/, (m) =>
                tr('bulkDelete.backend.dqRuleAttributeLinks', { count: m[1] })],
            [/^Data Quality Rule has a Technical Rule Reference\. Remove it before deletion\.$/, () =>
                tr('bulkDelete.backend.dqRuleTechnicalRef')],
            [/^Data Quality Rule has (\d+) technical rule references?\. Remove these before deletion\.$/, (m) =>
                tr('bulkDelete.backend.dqRuleTechnicalRefs', { count: m[1] })],
            [/^Unknown facet: (.+)$/, (m) =>
                tr('bulkDelete.backend.unknownFacet', { facet: m[1] })],
            [/^Successfully deleted (\d+) item\(s\)\.$/, (m) =>
                tr('bulkDelete.backend.successDeletedCount', { count: m[1] })],
            [/^(\d+) item\(s\) were skipped\.$/, (m) =>
                tr('bulkDelete.backend.skippedCount', { count: m[1] })],
            [/^Database error: (.+)$/s, (m) =>
                tr('bulkDelete.backend.databaseError', { detail: m[1] })],
            [/^User did not confirm all warnings$/, () =>
                tr('bulkDelete.backend.userDidNotConfirmWarnings')],
            [/^Object (\d+): User did not confirm all warnings$/, (m) =>
                tr('bulkDelete.backend.objectUserDidNotConfirmWarnings', { id: m[1] })]
        ];

        for (let i = 0; i < rules.length; i++) {
            const re = rules[i][0];
            const fn = rules[i][1];
            const m = s.match(re);
            if (m) {
                return fn(m);
            }
        }
        return text;
    }

    function translateBulkDeleteApiError(text) {
        if (text == null || text === '') {
            return text;
        }
        const t = translateBulkDeleteBackendMessage(text);
        if (t !== text) {
            return t;
        }
        const combined = /^Successfully deleted (\d+) item\(s\)\. (\d+) item\(s\) were skipped\.$/;
        const cm = text.trim().match(combined);
        if (cm) {
            return tr('bulkDelete.backend.successDeletedAndSkipped', { deleted: cm[1], skipped: cm[2] });
        }
        return text;
    }

    function applyPageTranslations() {
        if (window.I18n && typeof window.I18n.applyTranslations === 'function') {
            window.I18n.applyTranslations(document.body);
        }
        if (window.I18n && typeof window.I18n.t === 'function') {
            document.title = window.I18n.t('bulkDelete.pageTitle');
        }
    }

    /**
     * Label for the main action button: Super Admin = "Delete", Admin/WebUser = "Mark as Deleted"
     */
    function getDeleteButtonLabel() {
        return isSuperAdmin ? tr('bulkDelete.delete') : tr('bulkDelete.markAsDeleted');
    }

    function formatItemsSelectedCount(n) {
        if (n === 1) {
            return tr('bulkDelete.itemsSelectedOne');
        }
        return tr('bulkDelete.itemsSelected', { count: n });
    }

    /**
     * Initialize page
     */
    function runBulkDeleteInit() {
        const stored = sessionStorage.getItem('bulkDeleteSelection');
        if (!stored) {
            showError(tr('bulkDelete.errNoSelectionData'));
            return;
        }

        try {
            selectionData = JSON.parse(stored);
            // Normalize facet name to match backend expectations
            if (selectionData.facet) {
                selectionData.facet = normalizeFacetName(selectionData.facet);
            }
        } catch (e) {
            showError(tr('bulkDelete.errInvalidSelection'));
            return;
        }

        if (!selectionData.objectIds || selectionData.objectIds.length === 0) {
            showError(tr('bulkDelete.errNoItemsSelected'));
            return;
        }

        // Filter out null/undefined IDs and ensure they are numbers
        selectionData.objectIds = selectionData.objectIds
            .map(id => {
                const numId = typeof id === 'string' ? parseInt(id, 10) : id;
                return isNaN(numId) ? null : numId;
            })
            .filter(id => id !== null && id !== undefined);

        if (selectionData.objectIds.length === 0) {
            showError(tr('bulkDelete.errNoValidIds'));
            return;
        }

        console.log('[BulkDelete] Loaded selection:', {
            facet: selectionData.facet,
            objectIds: selectionData.objectIds,
            count: selectionData.objectIds.length
        });

        loadItems();
        wireUpButtons();
    }

    function init() {
        if (window.i18nReadyPromise) {
            window.i18nReadyPromise
                .then(() => {
                    applyPageTranslations();
                    runBulkDeleteInit();
                })
                .catch(() => runBulkDeleteInit());
        } else {
            applyPageTranslations();
            runBulkDeleteInit();
        }
    }

    /**
     * Extract row data from a row element or object
     * Handles both DOM elements with data-row-data attribute and plain data objects
     */
    function getRowDataFromElement(row) {
        // If it's already a data object, return it
        if (row && typeof row === 'object' && !row.getAttribute) {
            return row;
        }
        
        // If it's a DOM element or has getAttribute method, try to get data-row-data
        if (row && typeof row.getAttribute === 'function') {
            try {
                const dataAttr = row.getAttribute('data-row-data');
                if (dataAttr) {
                    return JSON.parse(dataAttr);
                }
            } catch (e) {
                console.error('[BulkDelete] Error parsing row data:', e);
            }
        }
        
        // If it's a string, try to parse it
        if (typeof row === 'string') {
            try {
                return JSON.parse(row);
            } catch (e) {
                console.error('[BulkDelete] Error parsing row string:', e);
            }
        }
        
        return null;
    }

    /**
     * Display name from Unison search row payload (align with search-table.js getRowValueForColumn Name fallbacks).
     */
    function getObjectDisplayNameFromRow(rowData) {
        if (!rowData || typeof rowData !== 'object') return '';
        const n = rowData.Name || rowData.name || rowData.PrimaryName || rowData.primaryName || rowData.primaryname
            || rowData['Primary Name'] || rowData['Name']
            || rowData['Short Name'] || rowData.ShortName || rowData.shortName || rowData.shortname
            || rowData.Subject || rowData.subject
            || rowData['Interface Name'] || rowData.interface_name || rowData.InterfaceName
            || rowData.Title || rowData.title
            || rowData.LongName || rowData.longName;
        if (n != null && String(n).trim() !== '') {
            return String(n).trim();
        }
        return '';
    }

    /**
     * Match item by id — API/session may use number or string.
     */
    function findItemByObjectId(objectId) {
        const n = Number(objectId);
        if (Number.isNaN(n)) {
            return loadedItems.find(i => i.id === objectId);
        }
        return loadedItems.find(i => Number(i.id) === n);
    }

    /**
     * Extract person name from row data or API response
     * Handles various field name variations
     */
    function getPersonName(rowData, personId) {
        if (!rowData) return tr('bulkDelete.idLabel', { id: personId });
        
        // Try various field name variations
        const firstName = rowData.First_Name || rowData.first_name || rowData['First Name'] || 
                         rowData.FirstName || rowData.firstName || '';
        const lastName = rowData.Last_Name || rowData.last_name || rowData['Last Name'] || 
                        rowData.LastName || rowData.lastName || '';
        
        const fullName = `${firstName} ${lastName}`.trim();
        return fullName || tr('bulkDelete.idLabel', { id: personId });
    }

    /**
     * Fetch person data from API
     */
    async function fetchPersonById(id) {
        try {
            const apiService = window.BUDG_API_SERVICE;
            if (apiService && typeof apiService.getPersonById === 'function') {
                const response = await apiService.getPersonById(id);
                // Handle response format (may be wrapped in data property)
                return response?.data || response;
            } else {
                // Fallback: direct API call
                const response = await fetch(`/api/people/${id}`, {
                    method: 'GET',
                    credentials: 'include'
                });
                if (response.ok) {
                    const data = await response.json();
                    return data?.data || data;
                }
            }
        } catch (error) {
            console.error(`[BulkDelete] Error fetching person ${id}:`, error);
        }
        return null;
    }

    /**
     * Load items for display in the table
     */
    async function loadItems() {
        const facet = selectionData.facet;
        const objectIds = selectionData.objectIds;

        // Special handling for people facet
        if (facet === 'people') {
            // Try to extract data from stored rows
            if (selectionData.rows && selectionData.rows.length > 0) {
                loadedItems = selectionData.rows.map(row => {
                    const rowData = getRowDataFromElement(row);
                    const id = rowData?.ID || rowData?.id || rowData?.Id || rowData?.Ref || 
                              (typeof row === 'object' && row.ID ? row.ID : 
                               (typeof row === 'object' && row.id ? row.id : null));
                    const personId = id || (typeof row === 'number' ? row : null);
                    
                    if (rowData) {
                        const name = getPersonName(rowData, personId);
                        return { id: personId, name, facet };
                    } else if (personId) {
                        // If we have ID but no row data, we'll fetch it below
                        return { id: personId, name: tr('bulkDelete.idLabel', { id: personId }), facet, needsFetch: true };
                    } else {
                        return { id: null, name: tr('bulkDelete.unknown'), facet };
                    }
                });
                
                // Fetch missing person data for items that need it
                const itemsNeedingFetch = loadedItems.filter(item => item.needsFetch && item.id);
                if (itemsNeedingFetch.length > 0) {
                    await Promise.all(itemsNeedingFetch.map(async (item) => {
                        const personData = await fetchPersonById(item.id);
                        if (personData) {
                            item.name = getPersonName(personData, item.id);
                        }
                        delete item.needsFetch;
                    }));
                }
                
                // Filter out items with null IDs
                loadedItems = loadedItems.filter(item => item.id !== null);
                renderTable();
                return;
            }
            
            // No row data available, fetch from API
            loadedItems = await Promise.all(objectIds.map(async (id) => {
                const personData = await fetchPersonById(id);
                const name = getPersonName(personData, id);
                return { id, name, facet };
            }));
            renderTable();
            return;
        }

        // Use stored row data if available, otherwise fetch from API
        if (selectionData.rows && selectionData.rows.length > 0) {
            loadedItems = selectionData.rows.map(row => {
                const rowData = getRowDataFromElement(row);
                const source = rowData || (typeof row === 'object' && row !== null ? row : null);
                if (!source) {
                    return { id: null, name: tr('bulkDelete.unknown'), facet };
                }
                const id = source.ID || source.id || source.Id || source.Ref;
                const displayName = getObjectDisplayNameFromRow(source);
                return {
                    id: id,
                    name: displayName || tr('bulkDelete.itemPrefix', { id }),
                    facet: facet
                };
            });
            loadedItems = loadedItems.filter(item => item.id !== null && item.id !== undefined);
            renderTable();
            return;
        }

        // Fallback: create items from IDs
        loadedItems = objectIds.map(id => ({
            id: id,
            name: tr('bulkDelete.itemPrefix', { id }),
            facet: facet
        }));
        renderTable();
    }

    /**
     * Render table with items
     */
    function renderTable() {
        const tableBody = document.getElementById('itemsTableBody');
        const tableHeader = document.getElementById('itemsTableHeader');
        const noItemsMessage = document.getElementById('noItemsMessage');
        const itemsCount = document.getElementById('itemsCount');
        const deleteBtn = document.getElementById('deleteBtn');

        if (!tableBody || !tableHeader) return;

        // Update count
        if (itemsCount) {
            itemsCount.textContent = formatItemsSelectedCount(loadedItems.length);
        }

        // Enable/disable delete button
        if (deleteBtn) {
            deleteBtn.disabled = loadedItems.length === 0 || isDeleting;
        }

        if (loadedItems.length === 0) {
            tableBody.innerHTML = '';
            if (noItemsMessage) {
                noItemsMessage.style.display = 'flex';
            }
            return;
        }

        if (noItemsMessage) {
            noItemsMessage.style.display = 'none';
        }

        // Render header
        tableHeader.innerHTML = `
            <th>${escapeHtml(tr('bulkDelete.tableName'))}</th>
            <th>${escapeHtml(tr('bulkDelete.tableFacet'))}</th>
            <th>${escapeHtml(tr('bulkDelete.tableStatus'))}</th>
            <th>${escapeHtml(tr('bulkDelete.tableErrorsWarnings'))}</th>
        `;

        // Render rows
        tableBody.innerHTML = loadedItems.map(item => {
            const viewUrl = VIEW_URL_MAP[item.facet] || '/view/';
            const icon = FACET_ICONS[item.facet] || 'fa-circle';
            
            // Determine status based on validation and delete results
            let status = tr('bulkDelete.statusPending');
            let statusClass = 'status-pending';
            if (item.deleted) {
                status = tr('bulkDelete.statusDeleted');
                statusClass = 'status-deleted';
            } else if (item.error) {
                status = tr('bulkDelete.statusError');
                statusClass = 'status-error';
            } else if (item.validationErrors && item.validationErrors.length > 0) {
                status = tr('bulkDelete.statusBlocked');
                statusClass = 'status-blocked';
            } else if (item.validationWarnings && item.validationWarnings.length > 0) {
                status = tr('bulkDelete.statusWarning');
                statusClass = 'status-warning';
            } else if (item.validated && item.canDelete) {
                status = tr('bulkDelete.statusReady');
                statusClass = 'status-ready';
            }
            
            // Build errors/warnings display - Show ALL errors and warnings
            let errorsHtml = '';
            const errorParts = [];
            const warningParts = [];
            
            // Collect all blocking errors
            if (item.validationErrors && item.validationErrors.length > 0) {
                item.validationErrors.forEach(err => {
                    const line = translateBulkDeleteBackendMessage(err);
                    errorParts.push(`<div style="margin-top: 4px;"><i class="fas fa-times-circle" style="margin-right: 4px;"></i>${escapeHtml(line)}</div>`);
                });
            }
            
            // Collect all warnings (show even if there are errors)
            if (item.validationWarnings && item.validationWarnings.length > 0) {
                item.validationWarnings.forEach(w => {
                    const raw = (typeof w === 'object' && w.message) ? w.message : (typeof w === 'string' ? w : JSON.stringify(w));
                    const warningText = translateBulkDeleteBackendMessage(raw);
                    warningParts.push(`<div style="margin-top: 4px;"><i class="fas fa-exclamation-triangle" style="margin-right: 4px;"></i>${escapeHtml(warningText)}</div>`);
                });
            }
            
            // Build combined display
            if (errorParts.length > 0) {
                // Show all errors first
                errorsHtml = `<div class="error-list" style="color: #dc2626; font-size: 12px; line-height: 1.5;">
                    ${errorParts.join('')}
                </div>`;
                // Also show warnings if they exist
                if (warningParts.length > 0) {
                    errorsHtml += `<div class="warning-list" style="color: #d97706; font-size: 12px; line-height: 1.5; margin-top: 8px;">
                        ${warningParts.join('')}
                    </div>`;
                }
            } else if (warningParts.length > 0) {
                // Show only warnings if no errors
                errorsHtml = `<div class="warning-list" style="color: #d97706; font-size: 12px; line-height: 1.5;">
                    ${warningParts.join('')}
                </div>`;
            } else if (item.error) {
                // Backend may send multiple reasons joined by "; " – show each in same cell
                const ERROR_SEP = '; ';
                const errorReasons = item.error.includes(ERROR_SEP)
                    ? item.error.split(ERROR_SEP).map(s => s.trim()).filter(Boolean)
                    : [item.error];
                const errorLines = errorReasons.map(err =>
                    `<div style="margin-top: 4px;"><i class="fas fa-times-circle" style="margin-right: 4px;"></i>${escapeHtml(err)}</div>`
                ).join('');
                errorsHtml = `<div class="error-list" style="color: #dc2626; font-size: 12px; line-height: 1.5;">
                    ${errorLines}
                </div>`;
            } else if (item.validated && item.canDelete) {
                errorsHtml = `<div style="color: #059669; font-size: 12px;">
                    <i class="fas fa-check-circle"></i> ${escapeHtml(tr('bulkDelete.readyCell'))}
                </div>`;
            }
            
            const rowClass = (item.validationErrors && item.validationErrors.length > 0) ? 'delete-warning' : 
                           (item.validationWarnings && item.validationWarnings.length > 0) ? 'delete-warning-yellow' : '';

            return `
                <tr data-id="${item.id}" class="${rowClass}">
                    <td>
                        <a href="${item.deleted ? '/error/404.html' : viewUrl + item.id}" class="name-link" ${item.deleted ? '' : 'target="_blank"'}>
                            <i class="fas ${icon}"></i>
                            ${escapeHtml(item.name)}
                        </a>
                    </td>
                    <td>${escapeHtml(item.facet || tr('bulkDelete.unknown'))}</td>
                    <td class="status-cell">
                        <span class="${statusClass}">${status}</span>
                    </td>
                    <td>${errorsHtml}</td>
                </tr>
            `;
        }).join('');

        // Add tooltip handlers for error icons
        document.querySelectorAll('.error-icon').forEach(icon => {
            icon.addEventListener('mouseenter', showErrorTooltip);
            icon.addEventListener('mouseleave', hideErrorTooltip);
        });
    }

    /**
     * Show error tooltip
     */
    function showErrorTooltip(event) {
        const icon = event.target;
        const error = icon.getAttribute('data-error');
        if (!error) return;

        // Remove existing tooltip
        const existing = document.querySelector('.tooltip');
        if (existing) {
            existing.remove();
        }

        // Create tooltip
        const tooltip = document.createElement('div');
        tooltip.className = 'tooltip visible';
        tooltip.textContent = error;
        document.body.appendChild(tooltip);

        // Position tooltip
        const rect = icon.getBoundingClientRect();
        tooltip.style.top = (rect.top - tooltip.offsetHeight - 8) + 'px';
        tooltip.style.left = (rect.left + rect.width / 2 - tooltip.offsetWidth / 2) + 'px';
    }

    /**
     * Hide error tooltip
     */
    function hideErrorTooltip() {
        const tooltip = document.querySelector('.tooltip');
        if (tooltip) {
            tooltip.remove();
        }
    }

    /**
     * Wire up button handlers
     */
    function wireUpButtons() {
        const deleteBtn = document.getElementById('deleteBtn');
        const closeBtn = document.getElementById('closeBtn');
        const modalConfirmBtn = document.getElementById('modalConfirmBtn');
        const modalCancelBtn = document.getElementById('modalCancelBtn');
        const modalCloseBtn = document.getElementById('modalCloseBtn'); // X button in header

        if (deleteBtn) {
            deleteBtn.addEventListener('click', handleDeleteClick);
        }

        if (closeBtn) {
            closeBtn.addEventListener('click', handleClose);
        }

        if (modalConfirmBtn) {
            modalConfirmBtn.addEventListener('click', handleModalConfirm);
        }

        if (modalCancelBtn) {
            modalCancelBtn.addEventListener('click', handleModalCancel);
        }

        // Also wire up the X close button in modal header
        if (modalCloseBtn) {
            modalCloseBtn.addEventListener('click', handleModalCancel);
        }

        // Close modal on overlay click
        const modal = document.getElementById('confirmModal');
        if (modal) {
            modal.addEventListener('click', (e) => {
                if (e.target === modal) {
                    handleModalCancel();
                }
            });
        }

        // Keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (modal && modal.style.display !== 'none') {
                if (e.key === 'Escape') {
                    handleModalCancel();
                } else if (e.key === 'Enter') {
                    handleModalConfirm();
                }
            }
        });
    }

    /**
     * Handle Delete button click
     */
    async function handleDeleteClick() {
        if (isDeleting || loadedItems.length === 0) return;

        // First, pre-validate all objects
        await preValidateDelete();
    }

    /**
     * Handle modal confirm (for validation summary)
     */
    async function handleModalConfirm() {
        const modal = document.getElementById('confirmModal');
        if (modal) {
            modal.style.display = 'none';
        }

        // Collect confirmations from validation summary
        const confirmations = collectConfirmations();
        
        await executeBulkDelete(confirmations);
    }

    /**
     * Handle modal cancel
     */
    function handleModalCancel() {
        const modal = document.getElementById('confirmModal');
        if (modal) {
            modal.style.display = 'none';
        }
        
        // Ensure table is visible with validation results
        // Table should already be updated from preValidateDelete, but re-render to be sure
        if (validationData) {
            updateItemsWithValidationResults(validationData);
            renderTable();
            
            // Scroll to table to show validation results
            const tableCard = document.querySelector('.bulk-items-card');
            if (tableCard) {
                tableCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
            }
        }
    }

    /**
     * Pre-validate all objects before deletion
     */
    async function preValidateDelete() {
        const deleteBtn = document.getElementById('deleteBtn');
        if (deleteBtn) {
            deleteBtn.disabled = true;
            deleteBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + escapeHtml(tr('bulkDelete.validating'));
        }

        try {
            const objectIdsParam = selectionData.objectIds.join(',');
            const url = `/api/bulk-delete?facet=${encodeURIComponent(selectionData.facet)}&objectIds=${objectIdsParam}`;
            
            const response = await fetch(url, {
                method: 'GET',
                credentials: 'include'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('[BulkDelete] Validation response:', data);

            if (data.error) {
                showError(translateBulkDeleteApiError(data.error));
                if (deleteBtn) {
                    deleteBtn.disabled = false;
                    deleteBtn.textContent = getDeleteButtonLabel();
                }
                return;
            }

            isSuperAdmin = data.isSuperAdmin === true;
            validationResults = data;
            validationData = data;
            
            // Update loadedItems with validation results immediately
            updateItemsWithValidationResults(data);
            
            // Re-render table to show validation results
            renderTable();
            
            // Show validation summary modal
            showValidationSummary(data);

        } catch (error) {
            console.error('[BulkDelete] Validation error:', error);
            showError(tr('bulkDelete.errValidateFailed', { message: error.message }));
            if (deleteBtn) {
                deleteBtn.disabled = false;
                deleteBtn.textContent = getDeleteButtonLabel();
            }
        }
    }
    
    /**
     * Update loadedItems with validation results
     */
    function updateItemsWithValidationResults(data) {
        const results = data.validationResults || [];
        
        results.forEach(result => {
            const item = findItemByObjectId(result.id);
            if (item) {
                // Update item name if available
                if (result.name && result.name !== 'Unknown') {
                    item.name = result.name;
                }
                
                // Store validation results
                item.validated = true;
                item.canDelete = result.canDelete;
                item.validationErrors = result.blockingErrors || [];
                item.validationWarnings = result.warnings || [];
            }
        });
    }

    /**
     * Show validation summary modal
     */
    function showValidationSummary(data) {
        const modal = document.getElementById('confirmModal');
        if (!modal) return;

        const summary = data.summary || {};
        const results = data.validationResults || [];

        // Update modal header
        const modalHeader = modal.querySelector('.modal-header h3');
        if (modalHeader) {
            modalHeader.textContent = tr('bulkDelete.validationSummary');
        }

        // Build modal body content
        let bodyContent = `
            <div class="validation-summary">
                <div class="summary-stats" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 20px;">
                    <div class="stat-item" style="text-align: center; padding: 12px; background: #f3f4f6; border-radius: 8px;">
                        <div style="font-size: 24px; font-weight: bold; color: #374151;">${summary.total || 0}</div>
                        <div style="font-size: 12px; color: #6b7280; margin-top: 4px;">${escapeHtml(tr('bulkDelete.statTotal'))}</div>
                    </div>
                    <div class="stat-item" style="text-align: center; padding: 12px; background: #d1fae5; border-radius: 8px;">
                        <div style="font-size: 24px; font-weight: bold; color: #065f46;">${summary.canDelete || 0}</div>
                        <div style="font-size: 12px; color: #047857; margin-top: 4px;">${escapeHtml(tr('bulkDelete.statCanDelete'))}</div>
                    </div>
                    <div class="stat-item" style="text-align: center; padding: 12px; background: #fef3c7; border-radius: 8px;">
                        <div style="font-size: 24px; font-weight: bold; color: #92400e;">${summary.hasWarnings || 0}</div>
                        <div style="font-size: 12px; color: #b45309; margin-top: 4px;">${escapeHtml(tr('bulkDelete.statWithWarnings'))}</div>
                    </div>
                    <div class="stat-item" style="text-align: center; padding: 12px; background: #fee2e2; border-radius: 8px;">
                        <div style="font-size: 24px; font-weight: bold; color: #991b1b;">${summary.blocked || 0}</div>
                        <div style="font-size: 12px; color: #b91c1c; margin-top: 4px;">${escapeHtml(tr('bulkDelete.statBlocked'))}</div>
                    </div>
                </div>
                <div class="validation-details" style="max-height: 400px; overflow-y: auto;">
        `;

        // Group results by status
        const canDelete = results.filter(r => r.canDelete && r.warnings.length === 0);
        const withWarnings = results.filter(r => r.canDelete && r.warnings.length > 0);
        const blocked = results.filter(r => !r.canDelete);

        // Show blocked items
        if (blocked.length > 0) {
            bodyContent += `
                <div class="validation-group" style="margin-bottom: 20px;">
                    <h4 style="color: #dc2626; margin: 0 0 12px 0; font-size: 14px; font-weight: 600;">
                        <i class="fas fa-times-circle"></i> ${escapeHtml(tr('bulkDelete.groupBlocked', { count: blocked.length }))}
                    </h4>
                    <div class="items-list" style="background: #fee2e2; padding: 12px; border-radius: 8px;">
            `;
            blocked.forEach(result => {
                const item = findItemByObjectId(result.id);
                const itemName = item ? item.name : tr('bulkDelete.itemPrefix', { id: result.id });
                bodyContent += `
                    <div class="validation-item" style="margin-bottom: 12px; padding: 8px; background: white; border-radius: 4px;">
                        <div style="font-weight: 600; margin-bottom: 8px;">${escapeHtml(itemName)} (${escapeHtml(tr('bulkDelete.idLabel', { id: result.id }))})</div>
                        <div class="item-errors">
                `;
                // Show ALL blocking errors, not just the first one
                if (result.blockingErrors && result.blockingErrors.length > 0) {
                    result.blockingErrors.forEach((error, index) => {
                        const line = translateBulkDeleteBackendMessage(error);
                        bodyContent += `<div style="color: #dc2626; font-size: 13px; margin-top: ${index === 0 ? '0' : '6px'}; line-height: 1.4;">
                            <i class="fas fa-times-circle" style="margin-right: 6px; font-size: 11px;"></i>${escapeHtml(line)}
                        </div>`;
                    });
                }
                // Also show warnings if they exist (even for blocked items)
                if (result.warnings && result.warnings.length > 0) {
                    const firstErrorIndex = result.blockingErrors ? result.blockingErrors.length : 0;
                    result.warnings.forEach((warning, index) => {
                        const raw = (typeof warning === 'object' && warning.message) ? warning.message : (typeof warning === 'string' ? warning : JSON.stringify(warning));
                        const warningText = translateBulkDeleteBackendMessage(raw);
                        bodyContent += `<div style="color: #d97706; font-size: 13px; margin-top: ${firstErrorIndex === 0 && index === 0 ? '0' : '6px'}; line-height: 1.4;">
                            <i class="fas fa-exclamation-triangle" style="margin-right: 6px; font-size: 11px;"></i>${escapeHtml(warningText)}
                        </div>`;
                    });
                }
                bodyContent += `
                        </div>
                    </div>
                `;
            });
            bodyContent += `</div></div>`;
        }

        // Show items with warnings
        if (withWarnings.length > 0) {
            bodyContent += `
                <div class="validation-group" style="margin-bottom: 20px;">
                    <h4 style="color: #d97706; margin: 0 0 12px 0; font-size: 14px; font-weight: 600;">
                        <i class="fas fa-exclamation-triangle"></i> ${escapeHtml(tr('bulkDelete.groupRequiresConfirmation', { count: withWarnings.length }))}
                    </h4>
                    <div class="items-list" style="background: #fef3c7; padding: 12px; border-radius: 8px;">
            `;
            withWarnings.forEach(result => {
                const item = loadedItems.find(i => i.id === result.id);
                const itemName = item ? item.name : tr('bulkDelete.itemPrefix', { id: result.id });
                bodyContent += `
                    <div class="validation-item" style="margin-bottom: 12px; padding: 8px; background: white; border-radius: 4px;" data-id="${result.id}">
                        <div style="font-weight: 600; margin-bottom: 8px;">${escapeHtml(itemName)} (${escapeHtml(tr('bulkDelete.idLabel', { id: result.id }))})</div>
                        <div class="item-warnings">
                `;
                result.warnings.forEach(warning => {
                    const warningId = `warning-${result.id}-${warning.type}`;
                    const msg = translateBulkDeleteBackendMessage(warning.message || '');
                    bodyContent += `
                        <div class="warning-item" style="margin-top: 8px;">
                            <label style="display: flex; align-items: start; cursor: pointer;">
                                <input type="checkbox" class="warning-checkbox" 
                                       data-object-id="${result.id}" 
                                       data-warning-type="${warning.type}"
                                       id="${warningId}"
                                       style="margin-right: 8px; margin-top: 2px;">
                                <span style="font-size: 13px; color: #92400e;">${escapeHtml(msg)}</span>
                            </label>
                        </div>
                    `;
                });
                bodyContent += `
                        </div>
                    </div>
                `;
            });
            bodyContent += `</div></div>`;
        }

        // Show items that can be deleted without warnings
        if (canDelete.length > 0) {
            bodyContent += `
                <div class="validation-group">
                    <h4 style="color: #059669; margin: 0 0 12px 0; font-size: 14px; font-weight: 600;">
                        <i class="fas fa-check-circle"></i> ${escapeHtml(tr('bulkDelete.groupReadyToDelete', { count: canDelete.length }))}
                    </h4>
                    <div class="items-list" style="background: #d1fae5; padding: 12px; border-radius: 8px;">
            `;
            canDelete.forEach(result => {
                const item = findItemByObjectId(result.id);
                const itemName = item ? item.name : tr('bulkDelete.itemPrefix', { id: result.id });
                bodyContent += `
                    <div class="validation-item" style="padding: 8px; background: white; border-radius: 4px; margin-bottom: 4px;">
                        <div style="font-size: 13px; color: #065f46;">${escapeHtml(itemName)} (${escapeHtml(tr('bulkDelete.idLabel', { id: result.id }))})</div>
                    </div>
                `;
            });
            bodyContent += `</div></div>`;
        }

        bodyContent += `
                </div>
            </div>
        `;

        // Update modal body
        const modalBody = modal.querySelector('.modal-body');
        if (modalBody) {
            modalBody.innerHTML = bodyContent;
        }

        // Update footer button text (Super Admin = "Delete All", Admin = "Mark as Deleted All")
        const confirmBtn = document.getElementById('modalConfirmBtn');
        if (confirmBtn) {
            const hasWarningsToConfirm = withWarnings.length > 0;
            const actionAll = isSuperAdmin ? tr('bulkDelete.deleteAll') : tr('bulkDelete.markAsDeletedAll');
            confirmBtn.textContent = hasWarningsToConfirm ? tr('bulkDelete.continueWithSelected') : actionAll;
            confirmBtn.disabled = blocked.length === results.length; // Disable if all blocked
        }

        // Show modal
        modal.style.display = 'flex';

        // Re-wire up button handlers after modal content update
        rewireModalButtons();

        // Reset main button label (Super Admin = "Delete", Admin = "Mark as Deleted")
        const deleteBtn = document.getElementById('deleteBtn');
        if (deleteBtn) {
            deleteBtn.disabled = false;
            deleteBtn.textContent = getDeleteButtonLabel();
        }
    }

    /**
     * Re-wire modal button handlers
     * Called after modal content is updated to ensure buttons work
     */
    function rewireModalButtons() {
        const modalConfirmBtn = document.getElementById('modalConfirmBtn');
        const modalCancelBtn = document.getElementById('modalCancelBtn');
        const modalCloseBtn = document.getElementById('modalCloseBtn');
        
        if (modalConfirmBtn) {
            // Remove all existing listeners by cloning
            const newConfirmBtn = modalConfirmBtn.cloneNode(true);
            modalConfirmBtn.parentNode.replaceChild(newConfirmBtn, modalConfirmBtn);
            newConfirmBtn.addEventListener('click', handleModalConfirm);
        }
        
        if (modalCancelBtn) {
            // Remove all existing listeners by cloning
            const newCancelBtn = modalCancelBtn.cloneNode(true);
            modalCancelBtn.parentNode.replaceChild(newCancelBtn, modalCancelBtn);
            newCancelBtn.addEventListener('click', handleModalCancel);
        }
        
        if (modalCloseBtn) {
            // Remove all existing listeners by cloning
            const newCloseBtn = modalCloseBtn.cloneNode(true);
            modalCloseBtn.parentNode.replaceChild(newCloseBtn, modalCloseBtn);
            newCloseBtn.addEventListener('click', handleModalCancel);
        }
    }

    /**
     * Collect confirmations from validation summary checkboxes
     */
    function collectConfirmations() {
        const confirmations = {};
        
        document.querySelectorAll('.warning-checkbox').forEach(checkbox => {
            if (checkbox.checked) {
                const objectId = parseInt(checkbox.getAttribute('data-object-id'));
                const warningType = checkbox.getAttribute('data-warning-type');
                
                if (!confirmations[objectId]) {
                    confirmations[objectId] = {};
                }
                confirmations[objectId][warningType] = true;
            }
        });
        
        return confirmations;
    }

    /**
     * Execute bulk delete with confirmations
     */
    async function executeBulkDelete(confirmations = null) {
        if (isDeleting) return;

        isDeleting = true;
        const deleteBtn = document.getElementById('deleteBtn');
        if (deleteBtn) {
            deleteBtn.disabled = true;
            deleteBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + escapeHtml(tr('bulkDelete.deleting'));
        }

        try {
            const requestBody = {
                facet: selectionData.facet,
                objectIds: selectionData.objectIds
            };
            
            if (confirmations) {
                requestBody.confirmations = confirmations;
            }

            const response = await fetch('/api/bulk-delete', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                credentials: 'include',
                body: JSON.stringify(requestBody)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();
            console.log('[BulkDelete] Delete response:', data);

            // Update items with delete results
            if (data.results && Array.isArray(data.results)) {
                data.results.forEach(result => {
                    const item = findItemByObjectId(result.id);
                    if (item) {
                        item.deleted = result.deleted;
                        item.error = result.error || null;
                        // Clear validation status after delete attempt
                        if (result.deleted) {
                            item.validated = false;
                            item.validationErrors = [];
                            item.validationWarnings = [];
                        }
                    }
                });
            }

            // Re-render table with delete results
            renderTable();

            // Show success/error message
            if (data.success) {
                const okMsg = data.message ? translateBulkDeleteApiError(data.message) : tr('bulkDelete.successDeleted');
                showSuccess(okMsg);
            } else {
                showError(data.error ? translateBulkDeleteApiError(data.error) : tr('bulkDelete.failedDeleteGeneric'));
            }

        } catch (error) {
            console.error('[BulkDelete] Error:', error);
            showError(tr('bulkDelete.errDeleteFailed', { message: error.message }));
        } finally {
            isDeleting = false;
            if (deleteBtn) {
                deleteBtn.disabled = loadedItems.length === 0;
                deleteBtn.textContent = getDeleteButtonLabel();
            }
        }
    }

    /**
     * Handle Close button click
     */
    function handleClose() {
        // Clear selection from sessionStorage
        sessionStorage.removeItem('bulkDeleteSelection');
        // Navigate back to search page
        window.location.href = '/search.html';
    }

    /**
     * Show error message
     */
    function showError(message) {
        showToast(message, 'error');
    }

    /**
     * Show success message
     */
    function showSuccess(message) {
        showToast(message, 'success');
    }

    /**
     * Show toast message
     */
    function showToast(message, type = 'info') {
        const toast = document.getElementById('toast');
        const toastMessage = document.getElementById('toastMessage');
        
        if (!toast || !toastMessage) return;

        toastMessage.textContent = message;
        toast.className = `toast ${type}`;
        toast.style.display = 'block';

        setTimeout(() => {
            toast.style.display = 'none';
        }, 5000);
    }

    /**
     * Escape HTML to prevent XSS
     */
    function escapeHtml(text) {
        if (text == null) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();


