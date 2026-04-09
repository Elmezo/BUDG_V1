(function () {
    class DatasetValuesView {
        constructor(containerId, datasetId, view = null) {
            this.containerId = containerId;
            this.datasetId = datasetId;
            this.view = view; // View parameter: 'changes' or null (original)
        }

        async init() {
            const container = document.getElementById(this.containerId);
            if (!container) return;

            const t = (k) => (window.I18n && window.I18n.t(k)) || k;
            const valueUpdatesTitle = t('datasetValues.titles.valueUpdates');
            const sampleSetTitle = t('datasetValues.titles.sampleSet');
            const loadingMeta = t('datasetValues.messages.loadingMetadata');
            const loadingSample = t('datasetValues.messages.loadingSampleSet');

            // Render Layout
            container.innerHTML = `
                <div class="dataset-container">
                    <!-- VALUE UPDATES & AVAILABILITY Card -->
                    <div class="form-card">
                        <div class="card-header">
                            <h3 class="card-title">${valueUpdatesTitle}</h3>
                        </div>
                        <div class="card-body" id="valuesMetadataBody">
                            <i class="fas fa-spinner fa-spin"></i> ${loadingMeta}
                        </div>
                    </div>

                    <!-- VALUES - SAMPLE SET Card -->
                    <div class="form-card" style="margin-top: 2rem;">
                        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center;">
                            <h3 class="card-title">${sampleSetTitle}</h3>
                            <div class="card-actions">
                                <i class="fas fa-cog"></i>
                            </div>
                        </div>
                        <div class="card-body" id="valuesSampleSetBody" style="overflow-x: auto;">
                            <i class="fas fa-spinner fa-spin"></i> ${loadingSample}
                        </div>
                    </div>
                </div>
            `;

            await Promise.all([
                this.loadMetadata(),
                this.loadSampleSet()
            ]);
        }

        async loadMetadata() {
            const t = (k) => (window.I18n && window.I18n.t(k)) || k;
            try {
                const container = document.getElementById('valuesMetadataBody');
                if (!container) {
                    console.warn('[DatasetValuesView] valuesMetadataBody container not found, skipping metadata load');
                    return;
                }
                
                container.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + t('datasetValues.messages.loadingMetadata');
                
                const metadata = await this.fetchMetadataFromApi(this.datasetId);

                container.innerHTML = `
                    ${this.renderItem(t('datasetValues.labels.valueUpdateFrequency'), metadata.frequency)}
                    ${this.renderItem(t('datasetValues.labels.updateFrequencyComments'), metadata.frequencyComments)}
                    ${this.renderItem(t('datasetValues.labels.availability'), metadata.availability)}
                    ${this.renderItem(t('datasetValues.labels.availabilityComments'), metadata.availabilityComments)}
                    ${this.renderItem(t('datasetValues.labels.valuesInBudg'), metadata.valuesInAxon)}
                `;

            } catch (error) {
                console.error('Error loading value metadata:', error);
                const container = document.getElementById('valuesMetadataBody');
                if (container) {
                    container.innerHTML = '<div class="error-text">' + t('datasetValues.messages.failedToLoadMetadata') + '</div>';
                }
            }
        }

        async loadSampleSet() {
            const t = (k) => (window.I18n && window.I18n.t(k)) || k;
            try {
                // 1. Fetch Attributes for headers
                const attributes = await window.BUDG_API_SERVICE.get(`/dataset/${this.datasetId}/attributes`) || [];

                // 2. Fetch Sample Values
                // TODO: Replace with actual API call
                const sampleRows = await this.fetchSampleValuesFromApi(this.datasetId);

                const container = document.getElementById('valuesSampleSetBody');
                if (!container) return;

                if (sampleRows.length === 0) {
                    container.innerHTML = '<div class="empty-state">' + t('datasetValues.messages.noDataAvailable') + '</div>';
                    return;
                }

                // If attributes fetching failed or returned empty, try to infer from first row of sample data
                // details: "Active", "CMD ID", "age", "gender", "Consent Type"
                let headers = attributes.map(a => a['Name attribute'] || a.name || a.PrimaryName);
                if (headers.length === 0 && sampleRows.length > 0) {
                    headers = Object.keys(sampleRows[0]);
                }

                if (headers.length === 0) {
                    container.innerHTML = '<div class="empty-state">' + t('datasetValues.messages.noAttributesDefined') + '</div>';
                    return;
                }

                let html = `
                    <table class="data-table" style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background-color: #a8d5ba; border-bottom: 2px solid #8bc4a3;">
                                ${headers.map(h => `<th style="padding: 12px; text-align: left; font-weight: 600; font-size: 0.75rem; color: #2c3e50; text-transform: uppercase;">${this.escapeHtml(h)}</th>`).join('')}
                            </tr>
                        </thead>
                        <tbody>
                            ${sampleRows.map((row, index) => `
                                <tr style="border-bottom: 1px solid #e5e7eb; background-color: ${index % 2 === 0 ? '#ffffff' : '#f8f9fa'};">
                                    ${headers.map(h => `<td style="padding: 12px; font-size: 0.875rem; color: #111827;">${this.escapeHtml(row[h] || row[h.toLowerCase()] || row[h.toUpperCase()] || '-')}</td>`).join('')}
                                </tr>
                            `).join('')}
                        </tbody>
                    </table>
                    <div style="padding: 10px; text-align: right; color: #6b7280; font-size: 0.8rem;">
                        ${sampleRows.length} ${(window.I18n && window.I18n.t('datasetValues.messages.records')) || 'records'}
                    </div>
                `;

                container.innerHTML = html;

            } catch (error) {
                console.error('Error loading sample set:', error);
                const t = (k) => (window.I18n && window.I18n.t(k)) || k;
                const el = document.getElementById('valuesSampleSetBody');
                if (el) el.innerHTML = '<div class="error-text">' + t('datasetValues.messages.failedToLoadSampleSet') + '</div>';
            }
        }

        renderItem(label, value) {
            return `<div class="view-item"><div class="view-label">${label}</div><div class="view-value">${value ? this.escapeHtml(value) : '<span class="empty">-</span>'}</div></div>`;
        }

        escapeHtml(str) {
            if (str == null) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }

        // Mock API handlers - to be replaced with real backend calls
        async fetchMetadataFromApi(id) {
            // Check if backend endpoint exists, otherwise return mock
            try {
                // Build URL with view parameter if provided
                let url = `/api/dataset-values/${id}/metadata`;
                if (this.view === 'changes') {
                    url += '?view=changes';
                }
                // Add cache-busting parameter to ensure fresh data when switching views
                url += (url.includes('?') ? '&' : '?') + '_t=' + new Date().getTime();
                
                const resp = await fetch(url);
                if (resp.ok) return await resp.json();
            } catch (e) { }

            return {
                frequency: null,
                frequencyComments: null,
                availability: null,
                availabilityComments: null,
                valuesInAxon: 'Please select'
            };
        }
        
        setView(view) {
            this.view = view;
        }

        async fetchSampleValuesFromApi(id) {
            try {
                const resp = await fetch(`/api/dataset-values/${id}/sample`);
                if (resp.ok) return await resp.json();
            } catch (e) { }

            return [];
        }
    }

    // Export to window
    window.DatasetValuesView = DatasetValuesView;
})();
