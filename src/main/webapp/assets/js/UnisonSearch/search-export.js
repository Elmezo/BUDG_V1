/**
 * Search Export Module
 * Handles export functionality for People data and other facets in Unison Search
 * Supports PDF, Excel, and CSV formats
 * People facet: uses existing /api/export/people endpoint
 * Other facets: uses new /api/export/unison-search endpoint with stakeholders options
 */

(function () {
    'use strict';

    let exportEnabled = false;

    /**
     * Initialize export functionality
     */
    function initExport() {
        // Check if export is enabled
        checkExportEnabled().then(enabled => {
            exportEnabled = enabled;
            if (enabled) {
                setupExportHandlers();
            } else {
                hideExportMenu();
            }
        }).catch(error => {
            console.error('Error checking export status:', error);
            hideExportMenu();
        });
    }

    /**
     * Check if export is enabled via API
     * @returns {Promise<boolean>}
     */
    async function checkExportEnabled() {
        try {
            const response = await fetch('/admin/api/export-config');
            if (!response.ok) {
                return false;
            }
            const data = await response.json();
            return data.enabled === true;
        } catch (error) {
            console.error('Error fetching export config:', error);
            return false;
        }
    }

    /**
     * Hide export menu if not enabled
     */
    function hideExportMenu() {
        const exportBtn = document.getElementById('exportBtn');
        if (exportBtn) {
            const submenu = exportBtn.closest('.dropdown-submenu');
            if (submenu) {
                // Mark as disabled instead of hiding directly
                // This allows search-settings.js to control visibility based on module
                submenu.setAttribute('data-export-disabled', 'true');
                submenu.style.display = 'none';
            }
        }
    }

    /**
     * Setup export button handlers
     */
    function setupExportHandlers() {
        // People facet handlers (existing functionality - no change)
        const exportPdfBtnPeople = document.getElementById('exportPdfBtnPeople');
        const exportExcelBtnPeople = document.getElementById('exportExcelBtnPeople');
        const exportCsvBtnPeople = document.getElementById('exportCsvBtnPeople');

        if (exportPdfBtnPeople) {
            exportPdfBtnPeople.addEventListener('click', (e) => {
                e.preventDefault();
                exportData('pdf');
            });
        }

        if (exportExcelBtnPeople) {
            exportExcelBtnPeople.addEventListener('click', (e) => {
                e.preventDefault();
                exportData('excel');
            });
        }

        if (exportCsvBtnPeople) {
            exportCsvBtnPeople.addEventListener('click', (e) => {
                e.preventDefault();
                exportData('csv');
            });
        }

        // Other facets: plain export + export with stakeholders (backend includeStakeholders flag)
        const exportPdfBtn = document.getElementById('exportPdfBtn');
        const exportPdfWithStakeholdersBtn = document.getElementById('exportPdfWithStakeholdersBtn');
        const exportExcelBtn = document.getElementById('exportExcelBtn');
        const exportExcelWithStakeholdersBtn = document.getElementById('exportExcelWithStakeholdersBtn');
        const exportCsvBtn = document.getElementById('exportCsvBtn');
        const exportCsvWithStakeholdersBtn = document.getElementById('exportCsvWithStakeholdersBtn');

        if (exportPdfBtn) {
            exportPdfBtn.addEventListener('click', (e) => {
                e.preventDefault();
                exportDataForOtherFacets('pdf', false);
            });
        }

        if (exportPdfWithStakeholdersBtn) {
            exportPdfWithStakeholdersBtn.addEventListener('click', (e) => {
                e.preventDefault();
                exportDataForOtherFacets('pdf', true);
            });
        }

        if (exportExcelBtn) {
            exportExcelBtn.addEventListener('click', (e) => {
                e.preventDefault();
                exportDataForOtherFacets('excel', false);
            });
        }

        if (exportExcelWithStakeholdersBtn) {
            exportExcelWithStakeholdersBtn.addEventListener('click', (e) => {
                e.preventDefault();
                exportDataForOtherFacets('excel', true);
            });
        }

        if (exportCsvBtn) {
            exportCsvBtn.addEventListener('click', (e) => {
                e.preventDefault();
                exportDataForOtherFacets('csv', false);
            });
        }

        if (exportCsvWithStakeholdersBtn) {
            exportCsvWithStakeholdersBtn.addEventListener('click', (e) => {
                e.preventDefault();
                exportDataForOtherFacets('csv', true);
            });
        }
    }

    /**
     * Export data in specified format (for People facet - existing functionality)
     * @param {string} format - Export format (pdf, excel, csv)
     */
    async function exportData(format) {
        if (!exportEnabled) {
            showMessage(window.I18n?.t('message.exportNotEnabled') || 'Export is not enabled', 'error');
            return;
        }

        try {
            // Show loading indicator
            showMessage(window.I18n?.t('message.preparingExport') || 'Preparing export...', 'info');

            // Export selected people only if on People facet and rows are selected; otherwise export all
            let url = `/api/export/people?format=${format}`;
            const activeCategory = typeof getActiveCategoryWithFallback === 'function' ? getActiveCategoryWithFallback() : null;
            const normalizedCategory = (typeof categoryToModule === 'function' ? categoryToModule(activeCategory) : (activeCategory || '')).toLowerCase();
            const selectedRows = window.bulkSelection && typeof window.bulkSelection.getSelectedRows === 'function'
                ? window.bulkSelection.getSelectedRows() : [];
            if (normalizedCategory === 'people' && selectedRows.length > 0) {
                const ids = selectedRows
                    .map(r => r.ID != null ? r.ID : (r.id != null ? r.id : (r.Id != null ? r.Id : null)))
                    .filter(id => id != null && !isNaN(id) && id > 0);
                if (ids.length > 0) {
                    url += '&ids=' + ids.join(',');
                }
            }

            const link = document.createElement('a');
            link.href = url;
            link.download = `people_export.${format === 'excel' ? 'xlsx' : format}`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);

            // Show success message
            setTimeout(() => {
                showMessage(window.I18n?.t('message.exportStarted', { format: format.toUpperCase() }) || `Export to ${format.toUpperCase()} started`, 'success');
            }, 500);

        } catch (error) {
            console.error('Export error:', error);
            showMessage((window.I18n?.t('message.exportFailed', { error: error.message }) || 'Export failed: ' + error.message), 'error');
        }
    }

    /**
     * Get current user name from session or API
     * @returns {Promise<string>}
     */
    async function getCurrentUserName() {
        try {
            // Try to get from sessionStorage
            const userStr = sessionStorage.getItem('currentUser');
            if (userStr) {
                const user = JSON.parse(userStr);
                if (user && (user.First_Name || user.Last_Name)) {
                    const firstName = user.First_Name || '';
                    const lastName = user.Last_Name || '';
                    return `${firstName} ${lastName}`.trim() || user.Email || 'Unknown User';
                }
                if (user && user.name) {
                    return user.name;
                }
            }

            // Try to get from API
            const response = await fetch('/api/user/current');
            if (response.ok) {
                const user = await response.json();
                if (user && (user.First_Name || user.Last_Name)) {
                    const firstName = user.First_Name || '';
                    const lastName = user.Last_Name || '';
                    return `${firstName} ${lastName}`.trim() || user.Email || 'Unknown User';
                }
                if (user && user.name) {
                    return user.name;
                }
            }
        } catch (error) {
            console.error('Error getting user name:', error);
        }

        return window.I18n?.t('common.unknownUser') || 'Unknown User';
    }

    /**
     * Extract table data from DOM - ONLY visible rows and columns
     * @param {boolean} [onlySelected=false] - If true, only include rows with data-bulk-selected
     * @returns {Object} Object containing columns and data rows
     */
    function extractTableData(onlySelected) {
        const table = document.querySelector('.search-table');
        if (!table) {
            return { columns: [], data: [] };
        }

        // Get visible columns from table headers (only active/visible columns)
        const headers = Array.from(table.querySelectorAll('th[data-column]'));
        const columns = headers
            .filter(th => {
                // Only include visible columns - check both display and visibility
                const style = window.getComputedStyle(th);
                const isVisible = style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    style.opacity !== '0';

                // Also check if parent row is visible
                const parentRow = th.closest('tr');
                if (parentRow) {
                    const parentStyle = window.getComputedStyle(parentRow);
                    if (parentStyle.display === 'none' || parentStyle.visibility === 'hidden') {
                        return false;
                    }
                }

                return isVisible;
            })
            .map(th => {
                const columnKey = th.getAttribute('data-column');
                // Get label from header text, clean it up
                let columnLabel = '';
                // Try to get text from header, excluding icons
                const textNodes = Array.from(th.childNodes)
                    .filter(node => node.nodeType === Node.TEXT_NODE ||
                        (node.nodeType === Node.ELEMENT_NODE && !node.classList.contains('fa')))
                    .map(node => node.textContent || '')
                    .join(' ')
                    .trim();
                columnLabel = textNodes || th.textContent.trim() || columnKey;

                return {
                    key: columnKey,
                    label: columnLabel || columnKey
                };
            })
            .filter(col => col.key); // Remove null/undefined columns

        // Get data rows - selected only or all visible
        const tbody = table.querySelector('tbody');
        if (!tbody) {
            return { columns: columns.map(col => col.key), columnLabels: {}, data: [] };
        }

        const allTrs = onlySelected
            ? Array.from(tbody.querySelectorAll('tr[data-bulk-selected]'))
            : Array.from(tbody.querySelectorAll('tr'));

        const rows = onlySelected ? allTrs : allTrs.filter(tr => {
                // Only include visible rows - check multiple conditions
                const style = window.getComputedStyle(tr);
                const isVisible = style.display !== 'none' &&
                    style.visibility !== 'hidden' &&
                    style.opacity !== '0';

                // Check if row is in a hidden parent (pagination, filters, etc.)
                let parent = tr.parentElement;
                while (parent && parent !== table) {
                    const parentStyle = window.getComputedStyle(parent);
                    if (parentStyle.display === 'none' || parentStyle.visibility === 'hidden') {
                        return false;
                    }
                    parent = parent.parentElement;
                }

                // Check if row is actually rendered and visible in viewport
                const rect = tr.getBoundingClientRect();
                // If row has no dimensions, it's likely hidden
                if (rect.height === 0 && rect.width === 0) {
                    return false;
                }

                // Additional check: if row is outside viewport but should be visible
                // (this handles pagination - rows outside current page are hidden)
                const tableRect = table.getBoundingClientRect();
                if (rect.top < tableRect.top - 1000 || rect.bottom > tableRect.bottom + 1000) {
                    // Row is way outside viewport, likely hidden by pagination
                    // But we still want it if it's in the DOM and visible
                    // So we check the computed style instead
                }

                return isVisible;
            });

        const data = rows
            .map(tr => {
                const rowData = {};

                // Extract object ID from row (for fetching stakeholders from backend)
                let objectId = null;

                // First, try to get from data-row-data attribute (most reliable)
                const rowDataAttr = tr.getAttribute('data-row-data');
                if (rowDataAttr) {
                    try {
                        const rowDataObj = JSON.parse(rowDataAttr);
                        // Try common ID field names (case-insensitive search)
                        const keys = Object.keys(rowDataObj);
                        for (let i = 0; i < keys.length; i++) {
                            const key = keys[i];
                            const keyLower = key.toLowerCase();
                            if (keyLower === 'id' ||
                                keyLower.endsWith('_id') ||
                                keyLower.endsWith('id')) {
                                try {
                                    const idValue = rowDataObj[key];
                                    if (typeof idValue === 'number') {
                                        objectId = idValue;
                                    } else if (idValue != null) {
                                        const parsedId = parseInt(idValue);
                                        if (!isNaN(parsedId) && parsedId > 0) {
                                            objectId = parsedId;
                                        }
                                    }
                                    if (objectId != null && objectId > 0) {
                                        break;
                                    }
                                } catch (e) {
                                    // Continue searching
                                }
                            }
                        }
                    } catch (e) {
                        console.warn('[Export] Error parsing row data:', e);
                    }
                }

                // Also try to get from data-ref attribute
                if (!objectId) {
                    const dataRef = tr.getAttribute('data-ref');
                    if (dataRef) {
                        // Try to extract ID from ref (format might be "Dataset 123" or just "123")
                        const match = dataRef.match(/\d+/);
                        if (match) {
                            const parsedId = parseInt(match[0]);
                            if (!isNaN(parsedId) && parsedId > 0) {
                                objectId = parsedId;
                            }
                        }
                    }
                }

                // Also try to get from ID column if visible (check all possible ID column names)
                if (!objectId) {
                    const possibleIdColumns = ['ID', 'id', 'Id', 'objectId', 'object_id', 'Object_ID'];
                    for (const idCol of possibleIdColumns) {
                        const idCell = tr.querySelector(`td[data-column="${idCol}"]`);
                        if (idCell) {
                            const idText = idCell.textContent.trim();
                            const parsedId = parseInt(idText);
                            if (!isNaN(parsedId) && parsedId > 0) {
                                objectId = parsedId;
                                break;
                            }
                        }
                    }
                }

                // Also try to extract from name-link href if available
                if (!objectId) {
                    const nameLink = tr.querySelector('a.name-link');
                    if (nameLink && nameLink.href) {
                        // Extract ID from URL (e.g., /view/dataset/123 or /dataset/123)
                        const match = nameLink.href.match(/\/(\d+)(?:\/|$)/);
                        if (match) {
                            const parsedId = parseInt(match[1]);
                            if (!isNaN(parsedId) && parsedId > 0) {
                                objectId = parsedId;
                            }
                        }
                    }
                }

                // Store object ID for backend fetching
                if (objectId) {
                    rowData._objectId = objectId;
                }

                columns.forEach(col => {
                    // Get cell for this column - only if it's visible
                    const cell = tr.querySelector(`td[data-column="${col.key}"]`);
                    if (cell) {
                        // Check if cell is visible
                        const cellStyle = window.getComputedStyle(cell);
                        if (cellStyle.display === 'none' || cellStyle.visibility === 'hidden') {
                            rowData[col.key] = '';
                            return;
                        }

                        // Get text content, handling nested elements
                        let cellValue = '';

                        // Special handling for stakeholders column - preserve all text with line breaks
                        const isStakeholdersColumn = col.key && (
                            col.key.toLowerCase() === 'stakeholders' ||
                            col.key.toLowerCase() === 'stakeholder'
                        );

                        if (isStakeholdersColumn) {
                            // For stakeholders, get all text preserving line structure
                            // Remove icons and buttons, but keep all text content
                            const clone = cell.cloneNode(true);
                            // Remove icons
                            clone.querySelectorAll('i.fa, i.fas, i.far, i.fal, i.fab').forEach(icon => icon.remove());
                            // Remove buttons
                            clone.querySelectorAll('button').forEach(btn => btn.remove());
                            // Get text content with line breaks preserved
                            cellValue = clone.textContent || clone.innerText || '';
                            // Clean up: replace multiple spaces/newlines with single newline, then trim
                            cellValue = cellValue
                                .replace(/\s+/g, ' ')  // Replace multiple spaces with single space
                                .replace(/\n\s*\n/g, '\n')  // Replace multiple newlines with single newline
                                .split('\n')
                                .map(line => line.trim())
                                .filter(line => line.length > 0)
                                .join('\n')
                                .trim();
                        } else {
                            // For other columns: Priority: name-link > other links > text content
                            const nameLink = cell.querySelector('a.name-link');
                            if (nameLink) {
                                cellValue = nameLink.textContent.trim();
                            } else {
                                // Try to get text from all text nodes, excluding icons
                                const textNodes = Array.from(cell.childNodes)
                                    .filter(node => {
                                        if (node.nodeType === Node.TEXT_NODE) return true;
                                        if (node.nodeType === Node.ELEMENT_NODE) {
                                            // Exclude icons and buttons
                                            return !node.classList.contains('fa') &&
                                                !node.classList.contains('fas') &&
                                                !node.classList.contains('far') &&
                                                !node.classList.contains('fal') &&
                                                node.tagName !== 'BUTTON' &&
                                                node.tagName !== 'I';
                                        }
                                        return false;
                                    })
                                    .map(node => node.textContent || '')
                                    .join(' ')
                                    .trim();

                                cellValue = textNodes || cell.textContent.trim();
                            }
                        }

                        rowData[col.key] = cellValue;
                    } else {
                        rowData[col.key] = '';
                    }
                });
                return rowData;
            })
            .filter(row => {
                // Filter out completely empty rows
                return Object.values(row).some(val => {
                    if (val == null) return false;
                    // Handle different types: string, number, etc.
                    if (typeof val === 'string') {
                        return val.trim() !== '';
                    } else if (typeof val === 'number') {
                        return !isNaN(val);
                    } else if (typeof val === 'boolean') {
                        return true;
                    }
                    // For other types, convert to string and check
                    return String(val).trim() !== '';
                });
            });

        return {
            columns: columns.map(col => col.key),
            columnLabels: columns.reduce((acc, col) => {
                acc[col.key] = col.label;
                return acc;
            }, {}),
            data: data
        };
    }

    /**
     * Find stakeholders column index
     * @param {Array<string>} columns - Array of column keys
     * @returns {number} Index of stakeholders column, or -1 if not found
     */
    function findStakeholdersColumn(columns) {
        const stakeholdersVariations = ['stakeholders', 'stakeholder', 'Stakeholders', 'Stakeholder', 'STAKEHOLDERS'];
        return columns.findIndex(col => {
            const colLower = col.toLowerCase();
            return stakeholdersVariations.some(variation => colLower === variation.toLowerCase());
        });
    }

    /**
     * Export data for other facets (non-People) with stakeholders option
     * @param {string} format - Export format (pdf, excel, csv)
     * @param {boolean} includeStakeholders - Whether to include stakeholders column
     */
    async function exportDataForOtherFacets(format, includeStakeholders) {
        try {
            // Show loading indicator
            showMessage(window.I18n?.t('message.preparingExport') || 'Preparing export...', 'info');

            // Export selected rows only if any are selected; otherwise export all visible
            const hasSelection = window.bulkSelection &&
                typeof window.bulkSelection.getSelectedRows === 'function' &&
                window.bulkSelection.getSelectedRows().length > 0;
            const tableData = extractTableData(!!hasSelection);
            if (!tableData.data || tableData.data.length === 0) {
                const msg = hasSelection
                    ? (window.I18n?.t('message.noRowsSelected') || 'No rows selected. Please select one or more rows to export.')
                    : (window.I18n?.t('message.noDataToExport') || 'No data to export. Please ensure the table has visible data.');
                showMessage(msg, 'error');
                return;
            }

            // If includeStakeholders is true, we'll fetch stakeholders from backend
            // Collect object IDs for backend fetching (must match data array order)
            const objectIds = tableData.data
                .map(row => {
                    // Try to get _objectId first, then try to find ID in row data
                    if (row._objectId) {
                        return row._objectId;
                    }
                    // Try to find ID in row data
                    for (const key in row) {
                        if (key.toLowerCase() === 'id' ||
                            key.toLowerCase().endsWith('_id') ||
                            key.toLowerCase().endsWith('id')) {
                            const idValue = row[key];
                            if (typeof idValue === 'number') {
                                return idValue;
                            } else if (typeof idValue === 'string') {
                                const parsedId = parseInt(idValue);
                                if (!isNaN(parsedId) && parsedId > 0) {
                                    return parsedId;
                                }
                            }
                        }
                    }
                    return null;
                })
                .filter(id => id != null && !isNaN(id) && id > 0);

            console.log('[Export] Collected object IDs for stakeholders:', objectIds);
            console.log('[Export] Total rows:', tableData.data.length, 'Rows with IDs:', objectIds.length);

            // Log for debugging (can be removed in production)
            console.log('[Export] Extracted data:', {
                rowCount: tableData.data.length,
                columnCount: tableData.columns.length,
                columns: tableData.columns,
                hasStakeholders: findStakeholdersColumn(tableData.columns) !== -1
            });

            // Show info message about what's being exported
            if (tableData.data.length > 0) {
                showMessage(window.I18n?.t('message.exportingVisibleRows', { count: tableData.data.length, columns: tableData.columns.length }) || `Exporting ${tableData.data.length} visible row(s) with ${tableData.columns.length} visible column(s)...`, 'info');
            }

            // Get current category
            const activeCategory = typeof getActiveCategoryWithFallback === 'function'
                ? getActiveCategoryWithFallback()
                : 'dataset';

            // Get user name
            const userName = await getCurrentUserName();

            // Prepare columns (remove stakeholders if needed, and remove _objectId from export)
            let columns = [...tableData.columns];
            let columnLabels = { ...tableData.columnLabels };
            const stakeholdersIndex = findStakeholdersColumn(columns);

            if (!includeStakeholders && stakeholdersIndex !== -1) {
                // Remove stakeholders column
                const stakeholdersKey = columns[stakeholdersIndex];
                columns.splice(stakeholdersIndex, 1);
                delete columnLabels[stakeholdersKey];
                console.log('[Export] Removed stakeholders column:', stakeholdersKey);
            } else if (includeStakeholders) {
                // Add stakeholders column if not already present
                if (stakeholdersIndex === -1) {
                    columns.push('stakeholders');
                    columnLabels['stakeholders'] = 'Stakeholders';
                }
            }

            // Filter data to include ONLY the visible columns (remove _objectId and any extra columns)
            const filteredData = tableData.data.map(row => {
                const filteredRow = {};
                columns.forEach(colKey => {
                    // Only include columns that are in the visible columns list
                    filteredRow[colKey] = row[colKey] || '';
                });
                // Remove _objectId from export data (it's only for backend fetching)
                delete filteredRow._objectId;
                return filteredRow;
            });

            // Prepare request data - ONLY visible rows and columns
            const requestData = {
                format: format,
                includeStakeholders: includeStakeholders,
                data: filteredData, // Only visible rows
                columns: columns,    // Only visible columns
                columnLabels: columnLabels,
                category: activeCategory,
                userName: userName,
                objectIds: objectIds  // Object IDs for fetching stakeholders from backend
            };

            // Send POST request to export endpoint
            const response = await fetch('/api/export/unison-search', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(requestData)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || 'Export failed');
            }

            // Get filename from response headers or generate one
            const contentDisposition = response.headers.get('Content-Disposition');
            let filename = `export_${activeCategory}_${new Date().getTime()}.${format === 'excel' ? 'xlsx' : format}`;
            if (contentDisposition) {
                const filenameMatch = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                if (filenameMatch && filenameMatch[1]) {
                    filename = filenameMatch[1].replace(/['"]/g, '');
                }
            }

            // Download file
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);

            // Show success message
            showMessage(window.I18n?.t('message.exportCompleted', { format: format.toUpperCase() }) || `Export to ${format.toUpperCase()} completed`, 'success');

        } catch (error) {
            console.error('Export error:', error);
            showMessage((window.I18n?.t('message.exportFailed', { error: error.message || window.I18n?.t('common.unknownError') || 'Unknown error' }) || 'Export failed: ' + (error.message || 'Unknown error')), 'error');
        }
    }

    /**
     * Show message to user
     * @param {string} message - Message text
     * @param {string} type - Message type (info, success, error)
     */
    function showMessage(message, type) {
        // Check if UnisonSearch message system exists
        if (window.UnisonSearch && window.UnisonSearch.showMessage) {
            window.UnisonSearch.showMessage(message, type);
            return;
        }

        // Fallback to console
        console.log(`[${type.toUpperCase()}] ${message}`);

        // Create simple notification
        const notification = document.createElement('div');
        notification.className = `export-notification export-notification-${type}`;
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            background: ${type === 'success' ? '#10b981' : type === 'error' ? '#ef4444' : '#3b82f6'};
            color: white;
            border-radius: 8px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
            z-index: 10000;
            animation: slideIn 0.3s ease-out;
        `;

        document.body.appendChild(notification);

        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease-out';
            setTimeout(() => {
                document.body.removeChild(notification);
            }, 300);
        }, 3000);
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initExport);
    } else {
        initExport();
    }

    // Expose to window for debugging
    window.SearchExport = {
        checkExportEnabled,
        exportData
    };

})();
