/**
 * Change Request History Component
 * Displays field change history for a Change Request in a simple table format.
 * Uses: Field | From | To | Author | Date Updated
 *
 * Usage: initChangeRequestHistory('changeRequestHistoryContainer', changeRequestId)
 */
(function () {
    'use strict';

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatDateForDisplay(dateString) {
        if (!dateString) return '';
        try {
            const datePattern = /^(\d{2})-([A-Za-z]{3})-(\d{4}) (\d{2}):(\d{2}):(\d{2})$/;
            if (datePattern.test(dateString)) return dateString;
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

    async function loadHistoryData(changeRequestId) {
        const response = await fetch(`/api/changerequests/${changeRequestId}/history?page=1&limit=100`, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' }
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${await response.text()}`);
        }
        const result = await response.json();
        if (!result.success) {
            throw new Error(result.error || 'Failed to load history');
        }
        return result.data || [];
    }

    function renderHistoryTable(container, records) {
        const t = (key) => (window.I18n && window.I18n.t(key)) || key;
        const h = {
            field: t('history.field') || 'Field',
            from: t('history.from') || 'From',
            to: t('history.to') || 'To',
            author: t('history.author') || 'Author',
            dateUpdated: t('history.dateUpdated') || 'Date Updated',
            loading: t('history.loading') || 'Loading...',
            zeroRecords: t('message.zeroRecords') || '0 records',
            records: (n) => (window.I18n && window.I18n.t('message.records', { count: n })) || `${n} record${n !== 1 ? 's' : ''}`,
            noRecords: t('message.noHistoryRecords') || 'No history records found for this change request.'
        };

        const rowsHtml = Array.isArray(records) && records.length > 0
            ? records.map(r => `
                <tr>
                    <td>${escapeHtml(r.field || '')}</td>
                    <td>${escapeHtml(r.from || '')}</td>
                    <td>${escapeHtml(r.to || '')}</td>
                    <td>${escapeHtml(r.author || '')}</td>
                    <td>${escapeHtml(formatDateForDisplay(r.date || r.lastChange || ''))}</td>
                </tr>
            `).join('')
            : `<tr><td colspan="5" style="text-align:center;padding:2rem;color:var(--text-muted,#9ca3af);">${h.noRecords}</td></tr>`;

        return `
            <div class="history-section">
                <div class="history-header">
                    <div class="history-title">HISTORY</div>
                    <div class="history-actions">
                        <button type="button" class="btn btn-secondary btn-sm" id="crHistorySettingsBtn" title="Settings">
                            <i class="fas fa-cog"></i>
                        </button>
                        <button type="button" class="btn btn-secondary btn-sm" id="crHistoryDropdownBtn" title="Options">
                            <i class="fas fa-chevron-down"></i>
                        </button>
                    </div>
                </div>
                <div class="history-table-wrapper">
                    <table class="history-table cr-history-table">
                        <thead>
                            <tr>
                                <th>${h.field}</th>
                                <th>${h.from}</th>
                                <th>${h.to}</th>
                                <th>${h.author}</th>
                                <th>${h.dateUpdated}</th>
                            </tr>
                        </thead>
                        <tbody id="crHistoryTableBody">
                            ${rowsHtml}
                        </tbody>
                    </table>
                    <div class="table-footer" id="crHistoryFooter">${records && records.length ? h.records(records.length) : h.zeroRecords}</div>
                </div>
            </div>
        `;
    }

    /**
     * Initialize the Change Request History component.
     * @param {string} containerId - ID of the container element (e.g. 'changeRequestHistoryContainer')
     * @param {string|number} changeRequestId - The Change Request ID
     */
    window.initChangeRequestHistory = async function (containerId, changeRequestId) {
        const container = document.getElementById(containerId);
        if (!container) {
            console.error('Change Request History: container not found:', containerId);
            return;
        }
        if (!changeRequestId) {
            console.error('Change Request History: changeRequestId is required');
            container.innerHTML = '<div class="empty-state"><i class="fas fa-exclamation-triangle"></i> Change request ID not provided.</div>';
            return;
        }

        const t = (key) => (window.I18n && window.I18n.t(key)) || key;
        const loadingMsg = t('history.loading') || 'Loading...';
        container.innerHTML = `
            <div class="history-section">
                <div class="history-header">
                    <div class="history-title">HISTORY</div>
                    <div class="history-actions">
                        <button type="button" class="btn btn-secondary btn-sm" disabled><i class="fas fa-cog"></i></button>
                        <button type="button" class="btn btn-secondary btn-sm" disabled><i class="fas fa-chevron-down"></i></button>
                    </div>
                </div>
                <div class="history-table-wrapper">
                    <table class="history-table cr-history-table">
                        <thead>
                            <tr>
                                <th>Field</th>
                                <th>From</th>
                                <th>To</th>
                                <th>Author</th>
                                <th>Date Updated</th>
                            </tr>
                        </thead>
                        <tbody>
                            <tr><td colspan="5" style="text-align:center;padding:2rem;"><i class="fas fa-spinner fa-spin"></i> ${loadingMsg}</td></tr>
                        </tbody>
                    </table>
                    <div class="table-footer" id="crHistoryFooter">${loadingMsg}</div>
                </div>
            </div>
        `;

        try {
            const records = await loadHistoryData(changeRequestId);
            container.innerHTML = renderHistoryTable(container, records);

            // Placeholder handlers for settings/dropdown
            const settingsBtn = document.getElementById('crHistorySettingsBtn');
            const dropdownBtn = document.getElementById('crHistoryDropdownBtn');
            if (settingsBtn) settingsBtn.addEventListener('click', () => console.log('CR History settings clicked'));
            if (dropdownBtn) dropdownBtn.addEventListener('click', () => console.log('CR History dropdown clicked'));
        } catch (err) {
            console.error('Change Request History: failed to load:', err);
            const errMsg = (window.I18n && window.I18n.t('message.failedToLoadHistory')) || 'Failed to load history';
            container.innerHTML = `
                <div class="history-section">
                    <div class="history-header">
                        <div class="history-title">HISTORY</div>
                    </div>
                    <div class="empty-state" style="padding:2rem;color:var(--danger,#b91c1c);">
                        <i class="fas fa-exclamation-triangle"></i>
                        ${escapeHtml(errMsg)}: ${escapeHtml(err.message)}
                    </div>
                </div>
            `;
        }
    };
})();
