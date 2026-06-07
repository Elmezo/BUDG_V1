/**
 * Dynamic History Component - Reusable across different facets
 * 
 * Usage:
 * 1. Include this file in your HTML
 * 2. Include history.css for styling
 * 3. Call createHistoryComponent('YourFacetName', objectId, 'containerId')
 * 4. The component will handle all functionality automatically
 * 
 * Example:
 * createHistoryComponent('Systems', 123, 'systemHistoryContainer');
 * createHistoryComponent('Projects', 456, 'projectHistoryContainer');
 */

(function () {
    'use strict';

    // Escape HTML to prevent XSS
    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Normalize From/To audit cell values so JSON nulls and the literal string
    // "null" render as an empty cell rather than the word "null".
    function normalizeAuditCell(value) {
        if (value == null) return '';
        const trimmed = String(value).trim();
        if (trimmed.toLowerCase() === 'null') return '';
        return value;
    }

    // True when an audit record belongs to the Dataset value-info subsection
    // (per-cell uploads/manual entries). These rows are surfaced under their
    // own "History Values" subsection on the Dataset facet.
    function isDatasetValueRow(record) {
        return record && String(record.object || '').trim() === 'Dataset Value Info';
    }

    // Render a "History Values" sub-table below the main history table for the
    // Dataset facet. Removes any previous instance before rendering so repeated
    // filter clicks do not stack tables.
    function renderDatasetValueRows(rows) {
        const wrapper = document.querySelector('.history-table-wrapper');
        if (!wrapper) return;
        const existing = document.getElementById('historyValuesSection');
        if (existing) existing.remove();
        if (!Array.isArray(rows) || rows.length === 0) return;

        const section = document.createElement('div');
        section.id = 'historyValuesSection';
        section.className = 'history-values-section';
        section.style.cssText = 'margin-top:1rem;';

        const heading = document.createElement('div');
        heading.className = 'history-values-title';
        heading.style.cssText = 'font-weight:600;margin:0.75rem 0 0.5rem 0;font-size:0.95rem;';
        heading.textContent = (window.I18n && window.I18n.t('history.valuesTitle')) || 'History Values';
        section.appendChild(heading);

        const table = document.createElement('table');
        table.className = 'history-table history-values-table';
        table.innerHTML =
            '<thead><tr>'
            + '<th>Object</th><th>Event</th><th>Update Type</th><th>Field</th>'
            + '<th>From</th><th>To</th><th>Author</th><th>Date</th><th>Last Changed</th>'
            + '</tr></thead><tbody></tbody>';

        const tbody = table.querySelector('tbody');
        tbody.innerHTML = rows.map(record => `
            <tr>
                <td>${escapeHtml(record.object || '')}</td>
                <td>${escapeHtml(record.event || '')}</td>
                <td><span class="update-type-badge ${record.updateType?.toLowerCase().replace(/\s+/g, '-') || ''}">${escapeHtml(record.updateType || '')}</span></td>
                <td>${escapeHtml(record.field || '')}</td>
                <td>${escapeHtml(normalizeAuditCell(record.from))}</td>
                <td>${escapeHtml(normalizeAuditCell(record.to))}</td>
                <td>${escapeHtml(record.author || '')}</td>
                <td>${escapeHtml(formatDateForDisplay(record.date || ''))}</td>
                <td>${escapeHtml(formatDateForDisplay(record.lastChange || ''))}</td>
            </tr>
        `).join('');

        section.appendChild(table);
        wrapper.appendChild(section);
    }

    // Column visibility for history table (order matches thead)
    const HISTORY_COLUMN_KEYS = ['object', 'event', 'updateType', 'field', 'from', 'to', 'author', 'date', 'lastChange'];
    const HISTORY_COLUMN_LABELS = ['Object', 'Event', 'Update Type', 'Field', 'From', 'To', 'Author', 'Date', 'Last Changed'];
    const HISTORY_COLUMN_VISIBILITY_STORAGE_KEY = 'historyColumnVisibility';

    function getHistoryColumnVisibility() {
        try {
            const raw = localStorage.getItem(HISTORY_COLUMN_VISIBILITY_STORAGE_KEY);
            if (raw) {
                const parsed = JSON.parse(raw);
                const out = {};
                HISTORY_COLUMN_KEYS.forEach((key, i) => { out[key] = parsed[key] !== false; });
                return out;
            }
        } catch (e) { /* ignore */ }
        const def = {};
        HISTORY_COLUMN_KEYS.forEach(k => { def[k] = true; });
        return def;
    }

    function setHistoryColumnVisibility(visibility) {
        try {
            localStorage.setItem(HISTORY_COLUMN_VISIBILITY_STORAGE_KEY, JSON.stringify(visibility));
        } catch (e) { /* ignore */ }
        applyHistoryColumnVisibility();
    }

    function applyHistoryColumnVisibility() {
        const table = document.querySelector('.history-table');
        if (!table) return;
        const theadTr = table.querySelector('thead tr');
        const visibility = getHistoryColumnVisibility();
        HISTORY_COLUMN_KEYS.forEach((key, index) => {
            const visible = visibility[key] !== false;
            const display = visible ? '' : 'none';
            if (theadTr && theadTr.children[index]) theadTr.children[index].style.display = display;
            table.querySelectorAll('tbody tr').forEach(tr => {
                if (tr.children[index]) tr.children[index].style.display = display;
            });
        });
    }

    // Create the history component HTML structure
    function createHistoryComponent(facetName, objectId, containerId, additionalHistoryData = null) {
        const container = document.getElementById(containerId);
        if (!container) {
            console.error(`Container with ID '${containerId}' not found`);
            return;
        }

        const t = (key) => (window.I18n && window.I18n.t(key)) || key;
        const h = {
            sectionTitle: t('history.sectionTitle'),
            fromDate: t('history.fromDate'),
            toDate: t('history.toDate'),
            filter: t('history.filter'),
            showAll: t('history.showAll'),
            object: t('history.object'),
            event: t('history.event'),
            updateType: t('history.updateType'),
            field: t('history.field'),
            from: t('history.from'),
            to: t('history.to'),
            author: t('history.author'),
            date: t('history.date'),
            lastChanged: t('history.lastChanged'),
            loading: t('history.loading'),
            zeroRecords: t('message.zeroRecords')
        };

        container.innerHTML = `
            <div class="history-section">
                <div class="history-header">
                    <div class="history-title">${h.sectionTitle}</div>
                    <div class="history-actions">
                        <button type="button" class="btn btn-secondary btn-sm" id="historyExportCsvBtn" title="Export CSV">
                            <i class="fas fa-file-csv"></i>
                        </button>
                        <button type="button" class="btn btn-secondary btn-sm" id="historyExportJsonBtn" title="Export JSON">
                            <i class="fas fa-file-code"></i>
                        </button>
                        <button type="button" class="btn btn-secondary btn-sm" id="historySettingsBtn" title="Settings">
                            <i class="fas fa-cog"></i>
                        </button>
                        <button type="button" class="btn btn-secondary btn-sm" id="historyDropdownBtn" title="Options">
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                </div>
                
                <div class="history-filters">
                        <div class="filter-group">
                            <label class="filter-label">${h.fromDate} <span class="required">*</span></label>
                            <div class="date-input-group">
                                <input type="date" class="form-input" id="historyFromDate">
                                <i class="fas fa-calendar-alt date-icon" id="historyFromDateIcon"></i>
                            </div>
                        </div>
                        <div class="filter-group">
                            <label class="filter-label">${h.toDate} <span class="required">*</span></label>
                            <div class="date-input-group">
                                <input type="date" class="form-input" id="historyToDate">
                        <button type="button" class="btn btn-primary" id="historyCompareBtn">
                            <i class="fas fa-layer-group"></i>
                            ${h.filter}
                        </button>
                        <button type="button" class="btn btn-secondary" id="historyShowAllBtn" title="Show All Data">
                            <i class="fas fa-list"></i>
                            ${h.showAll}
                        </button>
                            </div>
                        </div>
                </div>

                <div class="history-table-wrapper">
                    <table class="history-table">
                        <thead>
                            <tr>
                                <th><i class="fas fa-cube"></i> ${h.object}</th>
                                <th><i class="fas fa-bolt"></i> ${h.event}</th>
                                <th><i class="fas fa-edit"></i> ${h.updateType}</th>
                                <th><i class="fas fa-tag"></i> ${h.field}</th>
                                <th><i class="fas fa-arrow-left"></i> ${h.from}</th>
                                <th><i class="fas fa-arrow-right"></i> ${h.to}</th>
                                <th><i class="fas fa-user"></i> ${h.author}</th>
                                <th><i class="fas fa-clock"></i> ${h.date}</th>
                                <th><i class="fas fa-history"></i> ${h.lastChanged}</th>
                            </tr>
                        </thead>
                        <tbody id="historyTableBody">
                            <tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">${h.loading}</td></tr>
                        </tbody>
                    </table>
                    <div class="table-footer" id="historyFooter">${h.zeroRecords}</div>
                </div>
            </div>
        `;

        // تاريخ النهاردة
        const today = new Date();

        // تحويل لتنسيق yyyy-mm-dd
        const formattedToday = today.toISOString().split('T')[0];

        // أول يوم في الشهر الحالي
        const firstDay = new Date(today.getFullYear(), today.getMonth(), 1);
        const formattedFirstDay = firstDay.toISOString().split('T')[0];

        // وضع القيم في الـ input
        document.getElementById('historyFromDate').value = formattedFirstDay;
        document.getElementById('historyToDate').value = formattedToday;


        setupHistoryEventListeners(facetName, objectId);

        // Apply saved column visibility to table (headers and any existing rows)
        applyHistoryColumnVisibility();

        // Auto-load data on component creation
        loadInitialData(facetName, objectId, additionalHistoryData);
    }

    // Load initial data without date constraints
    async function loadInitialData(facetName, objectId, additionalHistoryData = null) {
        const tbody = document.getElementById('historyTableBody');
        const footer = document.getElementById('historyFooter');

        if (!tbody || !footer) return;

        try {
            showLoading();
            footer.textContent = window.I18n?.t('message.loading') || 'Loading...';

            // Get module_id and object_id dynamically
            const moduleId = await getModuleId();
            const currentObjectId = getCurrentObjectId();

            console.log('🔍 Loading initial history data with:', {
                moduleId,
                currentObjectId,
                facetName,
                path: window.location.pathname,
                hasAdditionalData: additionalHistoryData !== null
            });

            if (!moduleId) {
                throw new Error('Unable to determine module_id. Please check if the module exists in the system.');
            }

            if (!currentObjectId) {
                throw new Error('Unable to determine object_id from URL. Please check the URL format.');
            }

            // Load all pages from backend API without date filtering
            const historyData = await loadAllHistoryPages(moduleId, currentObjectId);

            // Merge with additional history data if provided
            let mergedHistoryData = Array.isArray(historyData) ? historyData : [];
            if (additionalHistoryData && Array.isArray(additionalHistoryData)) {
                mergedHistoryData = sortHistoryAscending([...mergedHistoryData, ...additionalHistoryData]);
            }

            // Store data globally for filtering
            window.currentHistoryData = mergedHistoryData;

            // Render all data without date filtering
            renderAllHistoryData(mergedHistoryData, tbody, footer);

        } catch (e) {
            console.error('Failed to load initial history data:', e);
            const failedMsg = window.I18n?.t('message.failedToLoadHistory') || 'Failed to load history data:';
            tbody.innerHTML = '<tr><td colspan="9" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">' + failedMsg + ' ' + escapeHtml(e.message) + '</td></tr>';
            footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
        }
    }

    // Render all history data without filtering
    function renderAllHistoryData(historyData, tbody, footer) {
        // Ensure historyData is an array
        const dataArray = Array.isArray(historyData) ? historyData : [];

        // Pull Dataset Value Info rows into a dedicated subsection rendered below
        // the main table; they would otherwise dominate the main feed.
        const mainRows = dataArray.filter(r => !isDatasetValueRow(r));
        const valueRows = dataArray.filter(isDatasetValueRow);

        if (mainRows.length === 0 && valueRows.length === 0) {
            const noHistoryMsg = window.I18n?.t('message.noHistoryRecords') || 'No history records found';
            tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + noHistoryMsg + '</td></tr>';
            footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
            renderDatasetValueRows([]);
            return;
        }

        const rowsHtml = mainRows.map(record => `
            <tr>
                <td>${escapeHtml(record.object || '')}</td>
                <td>${escapeHtml(record.event || '')}</td>
                <td><span class="update-type-badge ${record.updateType?.toLowerCase().replace(/\s+/g, '-') || ''}">${escapeHtml(record.updateType || '')}</span></td>
                <td>${escapeHtml(record.field || '')}</td>
                <td>${escapeHtml(normalizeAuditCell(record.from))}</td>
                <td>${escapeHtml(normalizeAuditCell(record.to))}</td>
                <td>${escapeHtml(record.author || '')}</td>
                <td>${escapeHtml(formatDateForDisplay(record.date || ''))}</td>
                <td>${escapeHtml(formatDateForDisplay(record.lastChange || ''))}</td>
            </tr>
        `).join('');

        tbody.innerHTML = rowsHtml || '<tr><td colspan="9" style="text-align:center;padding:1rem;color:var(--text-muted,#9ca3af);">' + ((window.I18n && window.I18n.t('history.noNonValueRecords')) || 'No non-value history records') + '</td></tr>';
        const totalCount = mainRows.length + valueRows.length;
        footer.textContent = (window.I18n && window.I18n.t('message.records', { count: totalCount })) || `${totalCount} record${totalCount !== 1 ? 's' : ''}`;
        applyHistoryColumnVisibility();
        renderDatasetValueRows(valueRows);
    }

    // Setup event listeners for the history component
    function setupHistoryEventListeners(facetName, objectId) {
        const compareBtn = document.getElementById('historyCompareBtn');
        const fromDateInput = document.getElementById('historyFromDate');
        const toDateInput = document.getElementById('historyToDate');
        const fromDateIcon = document.getElementById('historyFromDateIcon');

        // Compare button functionality - now acts as a filter
        if (compareBtn) {
            compareBtn.addEventListener('click', function () {
                // Disable button during processing
                compareBtn.disabled = true;
                compareBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + (window.I18n?.t('message.filtering') || 'Filtering...');

                const fromDate = fromDateInput?.value;
                const toDate = toDateInput?.value;

                try {
                    // Enhanced validation with detailed checks
                    const validation = validateDates(fromDate, toDate);
                    if (!validation.valid) {
                        showValidationError(validation.message);
                        resetCompareButton();
                        return;
                    }

                    // Additional business logic validation
                    const businessValidation = validateBusinessRules(fromDate, toDate);
                    if (!businessValidation.valid) {
                        showValidationError(businessValidation.message);
                        resetCompareButton();
                        return;
                    }

                    // Clear any previous errors
                    clearValidationErrors();

                    // Filter existing data instead of loading new data
                    filterExistingData(fromDate, toDate);
                    resetCompareButton();

                } catch (error) {
                    console.error('Error in filter button:', error);
                    showValidationError('An unexpected error occurred. Please try again.');
                    resetCompareButton();
                }
            });
        }

        // Helper functions for validation UI
        function showValidationError(message) {
            // Remove existing error messages
            clearValidationErrors();

            // Create error message element
            const errorDiv = document.createElement('div');
            errorDiv.className = 'validation-error';
            errorDiv.style.cssText = `
                color: var(--danger, #dc3545);
                background: var(--danger-light, #f8d7da);
                border: 1px solid var(--danger, #dc3545);
                border-radius: 4px;
                padding: 0.75rem;
                margin: 0.5rem 0;
                font-size: 0.875rem;
                display: flex;
                align-items: center;
                gap: 0.5rem;
            `;
            errorDiv.innerHTML = `
                <i class="fas fa-exclamation-triangle"></i>
                <span>${escapeHtml(message)}</span>
            `;

            // Insert error message after filters
            const filters = document.querySelector('.history-filters');
            if (filters) {
                filters.insertAdjacentElement('afterend', errorDiv);
            }
        }

        function clearValidationErrors() {
            const existingErrors = document.querySelectorAll('.validation-error');
            existingErrors.forEach(error => error.remove());
        }

        function resetCompareButton() {
            compareBtn.disabled = false;
            compareBtn.innerHTML = '<i class="fas fa-layer-group"></i> Filter';
        }

        // Filter existing data by date range
        function filterExistingData(fromDate, toDate) {
            const tbody = document.getElementById('historyTableBody');
            const footer = document.getElementById('historyFooter');

            if (!tbody || !footer) {
                console.error('Table elements not found');
                return;
            }

            // Check if we have data to filter
            if (!window.currentHistoryData || !Array.isArray(window.currentHistoryData)) {
                console.warn('No data available for filtering');
                tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + (window.I18n?.t('history.noDataForFiltering') || 'No data available for filtering') + '</td></tr>';
                footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
                renderDatasetValueRows([]);
                return;
            }

            // Apply date filtering to existing data
            const from = new Date(fromDate);
            from.setHours(0, 0, 0, 0); // Start of day

            const to = new Date(toDate);
            to.setHours(23, 59, 59, 999); // End of day

            console.log('Filtering data - From:', from, 'To:', to);

            const filteredData = window.currentHistoryData.filter(record => {
                if (!record.date) return false;

                // Try to parse different date formats
                let recordDate;
                try {
                    // Handle DD-MMM-YYYY HH:mm:ss format
                    if (record.date.includes('-') && record.date.split('-').length === 3) {
                        recordDate = new Date(record.date);
                    } else {
                        recordDate = new Date(record.date);
                    }
                } catch (e) {
                    console.warn('Failed to parse date:', record.date);
                    return false;
                }

                console.log('Checking record date:', record.date, 'Parsed:', recordDate);
                const isInRange = recordDate >= from && recordDate <= to;
                console.log('Is in range:', isInRange);

                return isInRange;
            });

            // Render filtered data
            if (filteredData.length === 0) {
                tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + (window.I18n?.t('history.noRecordsForRange') || 'No records found for the selected date range') + '</td></tr>';
                footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
                renderDatasetValueRows([]);
                return;
            }

            const mainFiltered = filteredData.filter(r => !isDatasetValueRow(r));
            const valueFiltered = filteredData.filter(isDatasetValueRow);

            const rowsHtml = mainFiltered.map(record => `
                <tr>
                    <td>${escapeHtml(record.object || '')}</td>
                    <td>${escapeHtml(record.event || '')}</td>
                    <td><span class="update-type-badge ${record.updateType?.toLowerCase().replace(/\s+/g, '-') || ''}">${escapeHtml(record.updateType || '')}</span></td>
                    <td>${escapeHtml(record.field || '')}</td>
                    <td>${escapeHtml(normalizeAuditCell(record.from))}</td>
                    <td>${escapeHtml(normalizeAuditCell(record.to))}</td>
                    <td>${escapeHtml(record.author || '')}</td>
                    <td>${escapeHtml(formatDateForDisplay(record.date || ''))}</td>
                    <td>${escapeHtml(formatDateForDisplay(record.lastChange || ''))}</td>
                </tr>
            `).join('');

            tbody.innerHTML = rowsHtml || '<tr><td colspan="9" style="text-align:center;padding:1rem;color:var(--text-muted,#9ca3af);">' + ((window.I18n && window.I18n.t('history.noNonValueRecords')) || 'No non-value history records') + '</td></tr>';
            footer.textContent = ((window.I18n && window.I18n.t('message.records', { count: filteredData.length })) || `${filteredData.length} record${filteredData.length !== 1 ? 's' : ''}`) + ' (filtered)';
            applyHistoryColumnVisibility();
            renderDatasetValueRows(valueFiltered);
        }

        // Show all data without filtering
        function showAllData() {
            const tbody = document.getElementById('historyTableBody');
            const footer = document.getElementById('historyFooter');

            if (!tbody || !footer) {
                console.error('Table elements not found');
                return;
            }

            // Check if we have data to show
            if (!window.currentHistoryData || !Array.isArray(window.currentHistoryData)) {
                console.warn('No data available to show');
                tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + (window.I18n?.t('history.noDataAvailable') || 'No data available') + '</td></tr>';
                footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
                return;
            }

            // Clear any validation errors
            clearValidationErrors();

            // Render all data
            renderAllHistoryData(window.currentHistoryData, tbody, footer);
        }

        function validateBusinessRules(fromDate, toDate) {
            const from = new Date(fromDate);
            const to = new Date(toDate);
            const now = new Date();
            const oneYearAgo = new Date();
            oneYearAgo.setFullYear(now.getFullYear() - 1);

            // Check if dates are not in the future
            if (from > now || to > now) {
                return {
                    valid: false,
                    message: 'Dates cannot be in the future'
                };
            }

            // Check if date range is not too old (more than 1 year)
            if (to < oneYearAgo) {
                return {
                    valid: false,
                    message: 'Date range cannot be older than 1 year'
                };
            }

            // Check if date range is not too large (more than 6 months)
            const sixMonthsAgo = new Date();
            sixMonthsAgo.setMonth(now.getMonth() - 6);
            if (from < sixMonthsAgo) {
                return {
                    valid: false,
                    message: 'Date range cannot span more than 6 months'
                };
            }

            // Check if dates are reasonable (not before 2020)
            const minDate = new Date('2020-01-01');
            if (from < minDate) {
                return {
                    valid: false,
                    message: 'Date cannot be before 2020'
                };
            }

            return { valid: true };
        }

        // Calendar icon click handlers
        if (fromDateIcon && fromDateInput) {
            fromDateIcon.addEventListener('click', function () {
                fromDateInput.focus();
                fromDateInput.showPicker();
            });
        }

        // Real-time validation for date inputs
        if (fromDateInput) {
            fromDateInput.addEventListener('input', debounce(function () {
                validateDateInput(fromDateInput, 'From Date');
            }, 500));

            fromDateInput.addEventListener('blur', function () {
                validateDateInput(fromDateInput, 'From Date');
            });
        }

        if (toDateInput) {
            toDateInput.addEventListener('input', debounce(function () {
                validateDateInput(toDateInput, 'To Date');
            }, 500));

            toDateInput.addEventListener('blur', function () {
                validateDateInput(toDateInput, 'To Date');
            });
        }

        // Real-time validation function for date inputs
        function validateDateInput(input, fieldName) {
            const value = input.value;

            // Clear previous validation styles
            input.classList.remove('is-valid', 'is-invalid');

            if (!value) {
                return; // Don't show error for empty fields during typing
            }

            // Try to parse the date
            try {
                const date = new Date(value);
                if (isNaN(date.getTime())) {
                    input.classList.add('is-invalid');
                    showFieldError(input, `${fieldName} is not a valid date`);
                } else {
                    input.classList.add('is-valid');
                    clearFieldError(input);
                }
            } catch (e) {
                input.classList.add('is-invalid');
                showFieldError(input, `${fieldName} is not a valid date`);
            }
        }

        function showFieldError(input, message) {
            clearFieldError(input);

            const errorDiv = document.createElement('div');
            errorDiv.className = 'field-error';
            errorDiv.style.cssText = `
                color: var(--danger, #dc3545);
                font-size: 0.75rem;
                margin-top: 0.25rem;
                display: flex;
                align-items: center;
                gap: 0.25rem;
            `;
            errorDiv.innerHTML = `
                <i class="fas fa-exclamation-circle"></i>
                <span>${escapeHtml(message)}</span>
            `;

            input.parentNode.insertBefore(errorDiv, input.nextSibling);
        }

        function clearFieldError(input) {
            const existingError = input.parentNode.querySelector('.field-error');
            if (existingError) {
                existingError.remove();
            }
        }


        // No auto-filtering - only filter when Compare button is clicked

        // Export buttons functionality
        const exportCsvBtn = document.getElementById('historyExportCsvBtn');
        if (exportCsvBtn) {
            exportCsvBtn.addEventListener('click', function () {
                exportHistoryData('csv');
            });
        }

        const exportJsonBtn = document.getElementById('historyExportJsonBtn');
        if (exportJsonBtn) {
            exportJsonBtn.addEventListener('click', function () {
                exportHistoryData('json');
            });
        }

        // Settings and Dropdown: column visibility panel
        const settingsBtn = document.getElementById('historySettingsBtn');
        const dropdownBtn = document.getElementById('historyDropdownBtn');
        let columnVisibilityPanel = null;

        function toggleColumnVisibilityPanel(e) {
            if (e) e.stopPropagation();
            if (columnVisibilityPanel) {
                columnVisibilityPanel.remove();
                columnVisibilityPanel = null;
                document.removeEventListener('click', closeColumnVisibilityPanel);
                return;
            }
            const visibility = getHistoryColumnVisibility();
            columnVisibilityPanel = document.createElement('div');
            columnVisibilityPanel.className = 'history-column-visibility-panel';
            columnVisibilityPanel.style.cssText = 'position:absolute;top:100%;right:0;margin-top:4px;background:var(--bg-secondary,#fff);border:1px solid var(--border,#ddd);border-radius:6px;box-shadow:0 4px 12px rgba(0,0,0,0.15);padding:8px 12px;min-width:180px;z-index:1050;';
            const title = document.createElement('div');
            title.style.cssText = 'font-weight:600;margin-bottom:8px;font-size:0.875rem;';
            title.textContent = (window.I18n && window.I18n.t('history.columnVisibility')) || 'Show columns';
            columnVisibilityPanel.appendChild(title);
            HISTORY_COLUMN_KEYS.forEach((key, i) => {
                const label = document.createElement('label');
                label.style.cssText = 'display:flex;align-items:center;gap:8px;cursor:pointer;padding:4px 0;font-size:0.875rem;';
                const cb = document.createElement('input');
                cb.type = 'checkbox';
                cb.checked = visibility[key] !== false;
                cb.dataset.columnKey = key;
                cb.addEventListener('change', function () {
                    const v = getHistoryColumnVisibility();
                    v[key] = cb.checked;
                    setHistoryColumnVisibility(v);
                });
                label.appendChild(cb);
                label.appendChild(document.createTextNode(HISTORY_COLUMN_LABELS[i]));
                columnVisibilityPanel.appendChild(label);
            });
            const parent = (settingsBtn || dropdownBtn).closest('.history-actions');
            if (parent) {
                parent.style.position = 'relative';
                parent.appendChild(columnVisibilityPanel);
            } else {
                document.body.appendChild(columnVisibilityPanel);
            }
            function closeColumnVisibilityPanel() {
                if (columnVisibilityPanel) {
                    columnVisibilityPanel.remove();
                    columnVisibilityPanel = null;
                }
                document.removeEventListener('click', closeColumnVisibilityPanel);
            }
            document.addEventListener('click', closeColumnVisibilityPanel);
            setTimeout(function () { columnVisibilityPanel.addEventListener('click', function (ev) { ev.stopPropagation(); }); }, 0);
        }

        if (settingsBtn) {
            settingsBtn.addEventListener('click', toggleColumnVisibilityPanel);
        }
        if (dropdownBtn) {
            dropdownBtn.addEventListener('click', toggleColumnVisibilityPanel);
        }

        // Show All button functionality
        const showAllBtn = document.getElementById('historyShowAllBtn');
        if (showAllBtn) {
            showAllBtn.addEventListener('click', function () {
                showAllData();
            });
        }
    }

    // Cache for history data
    const historyCache = new Map();

    // Loading states
    function showLoading() {
        const tbody = document.getElementById('historyTableBody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:2rem;"><i class="fas fa-spinner fa-spin"></i> ' + (window.I18n?.t('history.loading') || 'Loading history...') + '</td></tr>';
        }
    }

    // Helper function to convert date format from YYYY-MM-DD to DD-MMM-YYYY HH:mm:ss
    function convertDateFormat(dateString) {
        if (!dateString) return null;

        try {
            // If it's already in DD-MMM-YYYY HH:mm:ss format, return as is
            const datePattern = /^(\d{2})-([A-Za-z]{3})-(\d{4}) (\d{2}):(\d{2}):(\d{2})$/;
            if (datePattern.test(dateString)) {
                return dateString;
            }

            // If it's in YYYY-MM-DD format, convert to DD-MMM-YYYY HH:mm:ss
            const isoPattern = /^(\d{4})-(\d{2})-(\d{2})$/;
            if (isoPattern.test(dateString)) {
                const date = new Date(dateString);
                const day = String(date.getDate()).padStart(2, '0');
                const month = date.toLocaleString('en-US', { month: 'short' });
                const year = date.getFullYear();

                // For "To Date", set time to 23:59:59, for "From Date" set to 00:00:00
                const time = dateString.includes('23:59:59') ? '23:59:59' : '00:00:00';
                return `${day}-${month}-${year} ${time}`;
            }

            return dateString;
        } catch (e) {
            return dateString;
        }
    }

    // Enhanced date validation
    function validateDates(fromDate, toDate) {
        // Check if dates are provided
        if (!fromDate || !toDate) {
            return {
                valid: false,
                message: 'Please select both From Date and To Date'
            };
        }

        // Check if dates are not empty strings
        if (fromDate.trim() === '' || toDate.trim() === '') {
            return {
                valid: false,
                message: 'Please enter valid dates (dates cannot be empty)'
            };
        }

        // Parse dates
        const from = new Date(fromDate);
        const to = new Date(toDate);

        // Check if dates are valid
        if (isNaN(from.getTime()) || isNaN(to.getTime())) {
            return {
                valid: false,
                message: 'Please enter valid dates'
            };
        }

        // Check if from date is not later than to date
        if (from > to) {
            return {
                valid: false,
                message: 'From Date cannot be later than To Date'
            };
        }

        // Allow same dates - removed the validation that prevented this

        return { valid: true };
    }

    // Debounce function for performance
    function debounce(func, wait) {
        let timeout;
        return function executedFunction(...args) {
            const later = () => {
                clearTimeout(timeout);
                func(...args);
            };
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
        };
    }

    // Get cache key (no date filtering in cache)
    function getCacheKey(moduleId, objectId, fromDate, toDate, page = 1) {
        return `${moduleId}-${objectId}-${page}`;
    }

    // Load history data with retry mechanism
    async function loadHistoryDataWithRetry(facetName, objectId, fromDate, toDate, maxRetries = 3) {
        for (let i = 0; i < maxRetries; i++) {
            try {
                return await loadHistoryData(facetName, objectId, fromDate, toDate);
            } catch (error) {
                console.warn(`Attempt ${i + 1} failed:`, error.message);
                if (i === maxRetries - 1) throw error;
                await new Promise(resolve => setTimeout(resolve, 1000 * (i + 1)));
            }
        }
    }

    // Load history data with date filtering
    async function loadHistoryData(facetName, objectId, fromDate, toDate, page = 1) {
        const tbody = document.getElementById('historyTableBody');
        const footer = document.getElementById('historyFooter');

        if (!tbody || !footer) return;

        try {
            // Validate dates
            const dateValidation = validateDates(fromDate, toDate);
            if (!dateValidation.valid) {
                throw new Error(dateValidation.message);
            }

            showLoading();
            footer.textContent = window.I18n?.t('message.loading') || 'Loading...';

            // Get module_id and object_id dynamically
            const moduleId = await getModuleId();
            const currentObjectId = getCurrentObjectId();

            console.log('🔍 Loading history data with:', {
                moduleId,
                currentObjectId,
                fromDate,
                toDate,
                facetName,
                path: window.location.pathname
            });

            if (!moduleId) {
                throw new Error('Unable to determine module_id. Please check if the module exists in the system.');
            }

            if (!currentObjectId) {
                throw new Error('Unable to determine object_id from URL. Please check the URL format.');
            }

            // Check cache first
            const cacheKey = getCacheKey(moduleId, currentObjectId, fromDate, toDate, page);
            if (historyCache.has(cacheKey)) {
                const cachedData = historyCache.get(cacheKey);
                renderHistoryData(cachedData, tbody, footer, fromDate, toDate);
                return;
            }

            // Load every page from the backend so the full creation row is included
            const historyData = await loadAllHistoryPages(moduleId, currentObjectId);

            // Cache the data
            historyCache.set(cacheKey, historyData);

            // Render the data with frontend filtering
            renderHistoryData(historyData, tbody, footer, fromDate, toDate);

        } catch (e) {
            console.error('Failed to load history data:', e);
            tbody.innerHTML = '<tr><td colspan="9" style="color:var(--danger,#b91c1c);padding:1rem;text-align:center;">Failed to load history data: ' + escapeHtml(e.message) + '</td></tr>';
            footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
        }
    }

    // Helper function to format date for display
    function formatDateForDisplay(dateString) {
        if (!dateString) return '';

        try {
            // If already in DD-MMM-YYYY HH:mm:ss format, return as is
            const datePattern = /^(\d{2})-([A-Za-z]{3})-(\d{4}) (\d{2}):(\d{2}):(\d{2})$/;
            if (datePattern.test(dateString)) {
                return dateString;
            }

            // Convert from other formats to DD-MMM-YYYY HH:mm:ss
            const date = new Date(dateString);
            if (isNaN(date.getTime())) return dateString;

            const day = String(date.getDate()).padStart(2, '0');
            const month = date.toLocaleString('en-US', { month: 'short' });
            const year = date.getFullYear();
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            const seconds = String(date.getSeconds()).padStart(2, '0');

            return `${day}-${month}-${year} ${hours}:${minutes}:${seconds}`;
        } catch (e) {
            return dateString;
        }
    }

    // Render history data with frontend filtering
    function renderHistoryData(historyData, tbody, footer, fromDate, toDate) {
        // Ensure historyData is an array
        const dataArray = Array.isArray(historyData) ? historyData : [];

        // Apply frontend date filtering
        let filteredData = dataArray;
        if (fromDate && toDate) {
            const from = new Date(fromDate);
            from.setHours(0, 0, 0, 0); // Start of day

            const to = new Date(toDate);
            to.setHours(23, 59, 59, 999); // End of day

            console.log('Date filtering - From:', from, 'To:', to);

            filteredData = dataArray.filter(record => {
                if (!record.date) return false;

                // Try to parse different date formats
                let recordDate;
                try {
                    // Handle DD-MMM-YYYY HH:mm:ss format
                    if (record.date.includes('-') && record.date.split('-').length === 3) {
                        recordDate = new Date(record.date);
                    } else {
                        recordDate = new Date(record.date);
                    }
                } catch (e) {
                    console.warn('Failed to parse date:', record.date);
                    return false;
                }

                console.log('Checking record date:', record.date, 'Parsed:', recordDate);
                const isInRange = recordDate >= from && recordDate <= to;
                console.log('Is in range:', isInRange);

                return isInRange;
            });
        }

        if (filteredData.length === 0) {
            const noRangeMsg = window.I18n?.t('history.noRecordsForRange') || 'No history records found for the selected date range';
            tbody.innerHTML = '<tr><td colspan="9" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">' + noRangeMsg + '</td></tr>';
            footer.textContent = window.I18n?.t('message.zeroRecords') || '0 records';
            renderDatasetValueRows([]);
            return;
        }

        const mainFiltered = filteredData.filter(r => !isDatasetValueRow(r));
        const valueFiltered = filteredData.filter(isDatasetValueRow);

        const rowsHtml = mainFiltered.map(record => `
            <tr>
                <td>${escapeHtml(record.object || '')}</td>
                <td>${escapeHtml(record.event || '')}</td>
                <td><span class="update-type-badge ${record.updateType?.toLowerCase().replace(/\s+/g, '-') || ''}">${escapeHtml(record.updateType || '')}</span></td>
                <td>${escapeHtml(record.field || '')}</td>
                <td>${escapeHtml(normalizeAuditCell(record.from))}</td>
                <td>${escapeHtml(normalizeAuditCell(record.to))}</td>
                <td>${escapeHtml(record.author || '')}</td>
                <td>${escapeHtml(formatDateForDisplay(record.date || ''))}</td>
                <td>${escapeHtml(formatDateForDisplay(record.lastChange || ''))}</td>
            </tr>
        `).join('');

        tbody.innerHTML = rowsHtml || '<tr><td colspan="9" style="text-align:center;padding:1rem;color:var(--text-muted,#9ca3af);">' + ((window.I18n && window.I18n.t('history.noNonValueRecords')) || 'No non-value history records') + '</td></tr>';
        footer.textContent = (window.I18n && window.I18n.t('message.records', { count: filteredData.length })) || `${filteredData.length} record${filteredData.length !== 1 ? 's' : ''}`;
        applyHistoryColumnVisibility();
        renderDatasetValueRows(valueFiltered);
    }



    // Cache for modules data
    let modulesCache = null;

    // Get module_id based on current page/facet using dynamic API lookup
    async function getModuleId() {
        const path = window.location.pathname;

        // Extract facet name from path - handle both with and without trailing slash
        const pathMatch = path.match(/\/view\/([^\/]+)(?:\/|$)/);
        if (!pathMatch) {
            console.warn('Could not extract facet name from path:', path);
            return null;
        }

        const facetName = pathMatch[1];
        console.log('Extracted facet name from path:', facetName);

        // Special handling for facet name mapping
        let mappedFacetName = facetName;
        const facetMappings = {
            'dataset': 'Data Sets',
            'system': 'System',
            'project': 'Project',
            'people': 'People',
            'business-area': 'Business Area',
            'capability': 'Capability',
            'client': 'Client',
            'committee': 'Committee',
            'legal-entity': 'Legal Entity',
            'org-unit': 'Org Unit',
            'policy': 'Policy',
            'process': 'Process',
            'product': 'Product',
            'glossary': 'Glossary',
            'system-interface': 'Interface',
            'regulation': 'Regulation',
            'regulatory-theme': 'RegulatoryTheme',
            'regulator': 'Regulator',
            'geography': 'Geography'
        };

        if (facetMappings[facetName.toLowerCase()]) {
            mappedFacetName = facetMappings[facetName.toLowerCase()];
            console.log(`Mapped ${facetName} to ${mappedFacetName}`);
        }

        // Load modules data if not cached
        if (!modulesCache) {
            try {
                const response = await fetch('/api/modules');
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                const data = await response.json();
                modulesCache = data.modules || [];
                console.log('Loaded modules:', modulesCache.map(m => m.primaryName));
            } catch (error) {
                console.error('Failed to load modules:', error);
                return null;
            }
        }

        // Normalize facet name for matching
        const normalizedFacetName = mappedFacetName.toLowerCase().replace(/[-_]/g, ' ');

        // Find module by primaryName (case-insensitive, handle spaces and hyphens)
        const module = modulesCache.find(m => {
            if (!m.primaryName) return false;
            const normalizedModuleName = m.primaryName.toLowerCase();
            const normalizedFacet = normalizedFacetName;

            // Direct match
            if (normalizedModuleName === normalizedFacet) return true;

            // Match with spaces/hyphens converted
            const moduleNoSpaces = normalizedModuleName.replace(/\s+/g, '');
            const facetNoSpaces = normalizedFacet.replace(/\s+/g, '');
            if (moduleNoSpaces === facetNoSpaces) return true;

            // Match with spaces/hyphens converted to dashes
            const moduleWithDashes = normalizedModuleName.replace(/\s+/g, '-');
            const facetWithDashes = normalizedFacet.replace(/\s+/g, '-');
            if (moduleWithDashes === facetWithDashes) return true;

            return false;
        });

        if (module) {
            console.log(`Found module: ${module.primaryName} with ID: ${module.id}`);
            console.log('Module details:', module);
            return module.id;
        }

        // Fallback: try partial matching for compound names
        const partialMatch = modulesCache.find(m => {
            if (!m.primaryName) return false;
            const normalizedModuleName = m.primaryName.toLowerCase();
            const normalizedFacet = normalizedFacetName;

            // Check if module name contains facet name or vice versa
            return normalizedModuleName.includes(normalizedFacet) ||
                normalizedFacet.includes(normalizedModuleName.replace(/\s+/g, ''));
        });

        if (partialMatch) {
            console.log(`Found module by partial match: ${partialMatch.primaryName} with ID: ${partialMatch.id}`);
            return partialMatch.id;
        }

        console.warn(`Could not find module for facet: ${facetName} (mapped to: ${mappedFacetName})`);
        console.log('Available modules:', modulesCache.map(m => m.primaryName));
        return null;
    }

    // Get current object_id from URL
    function getCurrentObjectId() {
        const path = window.location.pathname;
        const search = window.location.search;

        // Try to get from URL path (e.g., /view/system/4)
        const pathMatch = path.match(/\/view\/[^\/]+\/(\d+)/);
        if (pathMatch) {
            return pathMatch[1];
        }

        // Try to get from query parameter (e.g., ?system_id=4)
        const urlParams = new URLSearchParams(search);
        const objectIdParams = ['system_id', 'project_id', 'people_id', 'business_area_id',
            'capability_id', 'client_id', 'committee_id', 'dataset_id',
            'legal_entity_id', 'org_unit_id', 'policy_id', 'process_id',
            'product_id', 'glossary_id', 'system_interface_id', 'regulation_id',
            'regulatory_theme_id', 'regulator_id', 'geography_id', 'id'];

        for (const param of objectIdParams) {
            const value = urlParams.get(param);
            if (value) {
                return value;
            }
        }

        // Try to get from any numeric parameter
        for (const [key, value] of urlParams.entries()) {
            if (value && /^\d+$/.test(value)) {
                return value;
            }
        }

        console.warn('Could not determine object_id from URL:', path, search);
        return null;
    }

    // Sort history records newest-first so the most recent change is at the top
    // and the "Created By" row (the very first audit entry for the object) appears
    // at the very bottom of the feed.
    //
    // Many creation audit rows share the exact same second, so we pin the
    // "Created By" row to the very bottom, then sort the rest by date DESC with
    // auditidpk DESC as a stable tiebreaker. The name is preserved for backwards
    // compatibility with existing call sites.
    function sortHistoryAscending(records) {
        if (!Array.isArray(records)) return [];
        const isCreatedByRow = (r) => String((r && r.field) || '').trim().toLowerCase() === 'created by';
        return records.slice().sort((a, b) => {
            const aCreated = isCreatedByRow(a);
            const bCreated = isCreatedByRow(b);
            if (aCreated && !bCreated) return 1;
            if (!aCreated && bCreated) return -1;
            const dateA = a && a.date ? new Date(a.date).getTime() : 0;
            const dateB = b && b.date ? new Date(b.date).getTime() : 0;
            if (dateA !== dateB) return dateB - dateA;
            const pkA = Number((a && (a.auditidpk || a.auditIdPk)) || 0);
            const pkB = Number((b && (b.auditidpk || b.auditIdPk)) || 0);
            return pkB - pkA;
        });
    }

    // Load every page of history for an object and return the sorted oldest-first list.
    async function loadAllHistoryPages(moduleId, objectId) {
        const pageSize = 100;
        let page = 1;
        let all = [];
        // Safety cap to avoid runaway loops on a malformed backend response.
        const maxPages = 1000;
        while (page <= maxPages) {
            const pageRecords = await loadHistoryFromAPI(moduleId, objectId, null, null, page, pageSize);
            if (!Array.isArray(pageRecords) || pageRecords.length === 0) break;
            all = all.concat(pageRecords);
            if (pageRecords.length < pageSize) break;
            page += 1;
        }
        return sortHistoryAscending(all);
    }

    // Load history data from backend API with pagination (no date filtering)
    async function loadHistoryFromAPI(moduleId, objectId, fromDate, toDate, page = 1, limit = 100) {
        try {
            const params = new URLSearchParams({
                module_id: moduleId,
                object_id: objectId,
                page: page,
                limit: limit
            });

            const response = await fetch(`/api/history?${params.toString()}`, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const result = await response.json();

            console.log('📡 API response:', result);
            console.log('📊 Data type:', typeof result.data);
            console.log('📊 Data length:', Array.isArray(result.data) ? result.data.length : 'Not an array');

            if (!result.success) {
                throw new Error(result.error || 'API returned error');
            }

            // Handle different response formats
            let data = result.data;

            // If data is a string, try to parse it
            if (typeof data === 'string') {
                try {
                    data = JSON.parse(data);
                } catch (e) {
                    console.warn('Failed to parse data string:', e);
                    data = [];
                }
            }

            // Ensure we return an array
            if (Array.isArray(data)) {
                return data;
            } else if (data && typeof data === 'object') {
                // If data is an object, try to extract array from it
                const possibleArrays = ['records', 'items', 'list', 'data'];
                for (const key of possibleArrays) {
                    if (Array.isArray(data[key])) {
                        return data[key];
                    }
                }
                // If no array found, return empty array
                return [];
            } else {
                return [];
            }

        } catch (error) {
            console.error('Error loading history from API:', error);
            throw error;
        }
    }

    // Export functionality
    function exportHistoryData(format = 'csv') {
        const tbody = document.getElementById('historyTableBody');
        if (!tbody) return;

        const rows = tbody.querySelectorAll('tr');
        if (rows.length === 0) {
            alert('No data to export');
            return;
        }

        const data = [];
        rows.forEach(row => {
            const cells = row.querySelectorAll('td');
            if (cells.length > 0) {
                data.push({
                    object: cells[0]?.textContent || '',
                    event: cells[1]?.textContent || '',
                    updateType: cells[2]?.textContent || '',
                    field: cells[3]?.textContent || '',
                    from: cells[4]?.textContent || '',
                    to: cells[5]?.textContent || '',
                    author: cells[6]?.textContent || '',
                    date: cells[7]?.textContent || '',
                    lastChange: cells[8]?.textContent || ''
                });
            }
        });

        if (format === 'csv') {
            exportToCSV(data);
        } else if (format === 'json') {
            exportToJSON(data);
        }
    }

    // Header display label -> data key (API returns camelCase: updateType, lastChange)
    const exportHeaderToKey = {
        'Object': 'object',
        'Event': 'event',
        'Update Type': 'updateType',
        'Field': 'field',
        'From': 'from',
        'To': 'to',
        'Author': 'author',
        'Date': 'date',
        'Last Changed': 'lastChange'
    };

    function exportToCSV(data) {
        const headers = ['Object', 'Event', 'Update Type', 'Field', 'From', 'To', 'Author', 'Date', 'Last Changed'];
        const csvContent = [
            headers.join(','),
            ...data.map(row => headers.map(header => {
                const key = exportHeaderToKey[header] || header.toLowerCase().replace(/\s+/g, '');
                const value = row[key] ?? '';
                return `"${String(value).replace(/"/g, '""')}"`;
            }).join(','))
        ].join('\n');

        downloadFile(csvContent, 'history-data.csv', 'text/csv');
    }

    function exportToJSON(data) {
        const jsonContent = JSON.stringify(data, null, 2);
        downloadFile(jsonContent, 'history-data.json', 'application/json');
    }

    function downloadFile(content, filename, mimeType) {
        const blob = new Blob([content], { type: mimeType });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    // Initialize history component with default data.
    // createHistoryComponent already triggers loadInitialData which fetches every
    // page; calling loadHistoryData here as well caused a duplicate fetch and an
    // initial flicker. Just defer to createHistoryComponent.
    async function initializeHistoryComponent(facetName, objectId, containerId) {
        createHistoryComponent(facetName, objectId, containerId);
    }

    // Make functions globally available
    window.HistoryComponent = {
        create: createHistoryComponent,
        initialize: initializeHistoryComponent,
        loadData: loadHistoryData,
        loadDataWithRetry: loadHistoryDataWithRetry,
        exportData: exportHistoryData,
        validateDates: validateDates,
        clearCache: () => historyCache.clear()
    };

    // Backward compatibility - also expose the main function directly
    window.createHistoryComponent = createHistoryComponent;
    window.initializeHistoryComponent = initializeHistoryComponent;

    // Debug: Log that the component is loaded
    console.log('HistoryComponent loaded successfully');
    console.log('Available functions:', Object.keys(window.HistoryComponent));

    // Ensure the component is available globally
    if (typeof window !== 'undefined') {
        window.HistoryComponent = window.HistoryComponent || {
            create: createHistoryComponent,
            initialize: initializeHistoryComponent,
            loadData: loadHistoryData
        };
        console.log('HistoryComponent ensured to be available globally');
    }

})();
