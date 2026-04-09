/**
 * Saved Search Widget
 * Displays data visualization from a saved search
 */

class SavedSearchWidget {
    constructor(containerId, widgetData) {
        this.containerId = containerId;
        this.widgetData = widgetData;
    }

    async init() {
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }

        try {
            this.render(container);
            await this.loadData();
            this.attachEventListeners(container);
        } catch (error) {
            console.error('Error initializing saved search widget:', error);
            this.renderError(container);
        }
    }

    render(container) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        const config = this.widgetData.config || {};
        const title = this.widgetData.title || t('widget.savedSearch.title', 'Saved Search Widget');
        const description = this.widgetData.description || '';

        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">${this.escapeHtml(title)}</h3>
                    <div class="dashboard-widget-actions">
                        <!-- Widget controls (drag handle, menu) will be added by setupWidgetControls -->
                    </div>
                </div>
                <div class="dashboard-widget-body">
                    ${description ? `<div class="widget-description">${this.escapeHtml(description)}</div>` : ''}
                    <div class="saved-search-widget-content">
                        <div class="saved-search-loading">${t('message.loading', 'Loading data...')}</div>
                    </div>
                </div>
            </div>
        `;
    }

    async loadData() {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        const container = document.getElementById(this.containerId);
        if (!container) return;

        const contentDiv = container.querySelector('.saved-search-widget-content');
        if (!contentDiv) return;

        const config = this.widgetData.config || {};
        const source = config.source || config.dataSource?.searchId || 0;
        const focus = config.focus || config.facet || '';
        const visualizeAs = config.visualizeAs || config.visualization?.type || 'table';
        const visualizeBy = config.visualizeBy || config.visualization?.visualizeBy || '';
        const display = config.display || config.visualization?.displayColumns || [];

        if (!source || !focus) {
            contentDiv.innerHTML = `<div class="widget-error">${t('error.invalidConfiguration', 'Invalid widget configuration: missing source or focus')}</div>`;
            return;
        }

        try {
            const data = {
                widgetSource: 'savedSearch',
                source: source,
                focus: focus,
                visualizeAs: visualizeAs,
                visualizeBy: visualizeBy,
                display: display
            };

            const response = await fetch('/api/dashboard/new-widget/preview', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${localStorage.getItem('token')}`
                },
                credentials: 'include',
                body: JSON.stringify(data)
            });

            if (!response.ok) {
                const error = await response.json();
                const errorMessage = error.error || t('error.loadFailed', 'Failed to load widget data');
                
                // Check if it's a Forbidden/permission error
                if (response.status === 403 || 
                    errorMessage.toLowerCase().includes('forbidden') || 
                    errorMessage.toLowerCase().includes("don't have permission") ||
                    errorMessage.toLowerCase().includes('access denied') ||
                    errorMessage.toLowerCase().includes('not found or access denied')) {
                    this.renderPermissionError(contentDiv);
                    return;
                }
                
                throw new Error(errorMessage);
            }

            const previewData = await response.json();
            this.renderData(contentDiv, previewData, visualizeAs);
        } catch (error) {
            console.error('Error loading saved search data:', error);
            const errorMessage = error.message || '';
            
            // Check if it's a Forbidden/permission error
            if (errorMessage.toLowerCase().includes('forbidden') || 
                errorMessage.toLowerCase().includes("don't have permission") ||
                errorMessage.toLowerCase().includes('access denied')) {
                this.renderPermissionError(contentDiv);
            } else {
            contentDiv.innerHTML = `<div class="widget-error">${t('error.loadFailed', 'Error loading data')}: ${this.escapeHtml(error.message)}</div>`;
            }
        }
    }

    renderData(container, previewData, visualizeAs) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        if (previewData.error) {
            const errorMessage = previewData.error || '';
            
            // Check if it's a Forbidden/permission error
            if (errorMessage.toLowerCase().includes('forbidden') || 
                errorMessage.toLowerCase().includes("don't have permission") ||
                errorMessage.toLowerCase().includes('access denied') ||
                errorMessage.toLowerCase().includes('not found or access denied')) {
                this.renderPermissionError(container);
            } else {
            container.innerHTML = `<div class="widget-error">${this.escapeHtml(previewData.error)}</div>`;
            }
            return;
        }

        // For charts and tables, always use the selected facet's data
        // For count, we can optionally show all facets breakdown
        const allFacetCounts = previewData.allFacetCounts;
        const hasChartData = previewData.data && Object.keys(previewData.data).length > 0;
        const hasTableData = previewData.data && Array.isArray(previewData.data) && previewData.data.length > 0;

        if (visualizeAs === 'count') {
            // For count, always show the selected facet's count (not all facets)
            this.renderCount(container, previewData);
        } else if (visualizeAs === 'table') {
            // ALWAYS show the selected facet's table data with selected display columns
            // Do NOT fall back to allFacetsTable - show error if no data
            if (hasTableData && previewData.columns && previewData.columns.length > 0) {
                this.renderTable(container, previewData);
            } else {
                container.innerHTML = `<div class="widget-error">${t('message.noData', 'No data available for table. Please check that the selected display columns have data for the filtered objects.')}</div>`;
            }
        } else if (visualizeAs === 'doughnut' || visualizeAs === 'bar') {
            // For charts, ALWAYS use the selected facet's data (grouped by visualizeBy column)
            // Do NOT fall back to allFacetsChart - show error if no data
            this.renderChart(container, previewData, visualizeAs);
        } else {
            container.innerHTML = `<div class="widget-error">${t('error.unknownVisualization', 'Unknown visualization type')}</div>`;
        }
    }

    renderCount(container, previewData) {
        const count = previewData.count || 0;
        const facet = previewData.facet || '';

        container.innerHTML = `
            <div class="saved-search-count">
                <div class="count-number">${count}</div>
                <div class="count-label">${this.escapeHtml(facet)}</div>
            </div>
        `;
    }

    renderAllFacetsCount(container, previewData) {
        const allFacetCounts = previewData.allFacetCounts || {};
        const totalCount = Object.values(allFacetCounts).reduce((sum, count) => sum + (count || 0), 0);
        
        // Format facet names for display
        const formatFacetName = (facetId) => {
            // Convert facet ID to readable name
            const facetMap = {
                'DATASET': 'Data Sets',
                'ATTRIBUTE': 'Attributes',
                'SYSTEM': 'Systems',
                'GLOSSARY': 'Glossary',
                'PEOPLE': 'People',
                'PROCESS': 'Processes',
                'PRODUCT': 'Products',
                'POLICY': 'Policies',
                'LEGAL_ENTITY': 'Legal Entities',
                'BUSINESS_AREA': 'Business Areas',
                'CAPABILITY': 'Capabilities',
                'COMMITTEE': 'Committees',
                'CLIENT': 'Clients',
                'GEOGRAPHY': 'Geographies',
                'REGULATION': 'Regulations',
                'REGULATOR': 'Regulators',
                'REGULATORY_THEME': 'Regulatory Themes'
            };
            return facetMap[facetId] || facetId.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        };

        const facetsList = Object.entries(allFacetCounts)
            .map(([facetId, count]) => ({
                name: formatFacetName(facetId),
                count: count || 0
            }))
            .sort((a, b) => b.count - a.count);

        let html = `
            <div class="saved-search-all-facets-count">
                <div class="total-count">
                    <div class="count-number">${totalCount}</div>
                    <div class="count-label">Total Results</div>
                </div>
                <div class="facets-breakdown">
        `;

        facetsList.forEach(facet => {
            html += `
                <div class="facet-count-item">
                    <span class="facet-name">${this.escapeHtml(facet.name)}</span>
                    <span class="facet-count">${facet.count}</span>
                </div>
            `;
        });

        html += `
                </div>
            </div>
        `;

        container.innerHTML = html;
    }

    renderTable(container, previewData) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        if (!previewData.columns || !previewData.data) {
            container.innerHTML = `<div class="widget-error">${t('message.noData', 'No data available')}</div>`;
            return;
        }

        const facet = previewData.facet || previewData.focus || '';
        let html = '<div class="saved-search-table-container">';
        html += '<table class="saved-search-table"><thead><tr>';
        previewData.columns.forEach(col => {
            html += `<th>${this.formatColumnName(col)}</th>`;
        });
        html += '</tr></thead><tbody>';

        if (previewData.data.length === 0) {
            html += `<tr><td colspan="${previewData.columns.length}" style="text-align: center; padding: 2rem;">${t('message.noDataFound', 'No data found')}</td></tr>`;
        } else {
            previewData.data.forEach(row => {
                html += '<tr>';
                previewData.columns.forEach(col => {
                    let value = row[col] || '';
                    if (['status', 'lifecycle', 'type', 'viewing'].includes(col.toLowerCase())) {
                        const valKey = 'value.' + String(value).toLowerCase();
                        if (window.I18n && window.I18n.t) {
                            const tr = window.I18n.t(valKey);
                            if (tr !== valKey) value = tr;
                        }
                    }
                    html += `<td>${this.escapeHtml(String(value))}</td>`;
                });
                html += '</tr>';
            });
        }

        html += '</tbody></table>';
        if (previewData.count > 0) {
            const showingText = t('table.showingResults', `Showing ${previewData.data.length} of ${previewData.count} results`)
                .replace('{count}', previewData.data.length)
                .replace('{total}', previewData.count);
            html += `<div class="table-footer">${showingText}</div>`;
        }
        if (facet) {
            html += `<div class="table-facet-name">${this.escapeHtml(facet)}</div>`;
        }
        html += '</div>';

        container.innerHTML = html;
    }

    renderAllFacetsTable(container, previewData) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        const allFacetCounts = previewData.allFacetCounts || {};

        // Format facet names for display
        const formatFacetName = (facetId) => {
            const facetMap = {
                'DATASET': 'Data Sets',
                'ATTRIBUTE': 'Attributes',
                'SYSTEM': 'Systems',
                'GLOSSARY': 'Glossary',
                'PEOPLE': 'People',
                'PROCESS': 'Processes',
                'PRODUCT': 'Products',
                'POLICY': 'Policies',
                'LEGAL_ENTITY': 'Legal Entities',
                'BUSINESS_AREA': 'Business Areas',
                'CAPABILITY': 'Capabilities',
                'COMMITTEE': 'Committees',
                'CLIENT': 'Clients',
                'GEOGRAPHY': 'Geographies',
                'REGULATION': 'Regulations',
                'REGULATOR': 'Regulators',
                'REGULATORY_THEME': 'Regulatory Themes'
            };
            return facetMap[facetId] || facetId.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        };

        const facetsList = Object.entries(allFacetCounts)
            .map(([facetId, count]) => ({
                name: formatFacetName(facetId),
                count: count || 0
            }))
            .sort((a, b) => b.count - a.count);

        let html = '<div class="saved-search-table-container">';
        html += '<table class="saved-search-table"><thead><tr>';
        html += '<th>Facet</th>';
        html += '<th>Count</th>';
        html += '</tr></thead><tbody>';

        if (facetsList.length === 0) {
            html += `<tr><td colspan="2" style="text-align: center; padding: 2rem;">${t('message.noDataFound', 'No data found')}</td></tr>`;
        } else {
            facetsList.forEach(facet => {
                html += '<tr>';
                html += `<td>${this.escapeHtml(facet.name)}</td>`;
                html += `<td style="text-align: right; font-weight: bold;">${facet.count}</td>`;
                html += '</tr>';
            });
        }

        html += '</tbody></table>';
        const totalCount = facetsList.reduce((sum, f) => sum + f.count, 0);
        html += `<div class="table-footer">Total: ${totalCount} results across ${facetsList.length} facet(s)</div>`;
        html += '</div>';

        container.innerHTML = html;
    }

    renderChart(container, previewData, chartType) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        if (!previewData.data || Object.keys(previewData.data).length === 0) {
            container.innerHTML = `<div class="widget-error">${t('message.noChartData', 'No chart data available')}</div>`;
            return;
        }

        const facet = previewData.facet || previewData.focus || '';
        const column = previewData.column || '';

        // Destroy existing chart if any
        const existingCanvas = container.querySelector('canvas');
        if (existingCanvas && existingCanvas.chartInstance) {
            existingCanvas.chartInstance.destroy();
        }

        // Generate unique canvas ID to avoid conflicts
        const canvasId = `chart-${this.containerId}-${Date.now()}`;

        container.innerHTML = `
            <div class="saved-search-chart-container">
                <div class="chart-wrapper" style="flex: 1; min-height: 250px; position: relative;">
                    <canvas id="${canvasId}"></canvas>
                </div>
                ${facet ? `<div class="chart-facet-name">${this.escapeHtml(facet)}</div>` : ''}
            </div>
        `;

        const canvas = container.querySelector(`#${canvasId}`);
        if (!canvas) return;

        // Set canvas size for better rendering
        const chartWrapper = container.querySelector('.chart-wrapper');
        if (chartWrapper) {
            canvas.style.width = '100%';
            canvas.style.height = '100%';
        }

        const ctx = canvas.getContext('2d');
        const labels = Object.keys(previewData.data);
        const values = Object.values(previewData.data);

        const chartConfig = {
            type: chartType === 'doughnut' ? 'doughnut' : 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: column || facet,
                    data: values,
                    backgroundColor: this.generateColors(labels.length),
                    borderColor: chartType === 'doughnut' ? '#ffffff' : this.generateColors(labels.length),
                    borderWidth: chartType === 'doughnut' ? 2 : 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                layout: {
                    padding: {
                        top: 10,
                        bottom: 10,
                        left: 10,
                        right: 10
                    }
                },
                plugins: {
                    legend: {
                        position: chartType === 'doughnut' ? 'right' : 'top',
                        display: true,
                        labels: {
                            padding: 10,
                            font: {
                                size: 11
                            },
                            usePointStyle: true,
                            boxWidth: 12
                        }
                    },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        padding: 10,
                        titleFont: {
                            size: 12,
                            weight: 'bold'
                        },
                        bodyFont: {
                            size: 11
                        },
                        cornerRadius: 6
                    }
                },
                scales: chartType === 'bar' ? {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            font: {
                                size: 10
                            },
                            padding: 8
                        },
                        grid: {
                            color: 'rgba(0, 0, 0, 0.05)'
                        }
                    },
                    x: {
                        ticks: {
                            font: {
                                size: 10
                            },
                            padding: 8,
                            maxRotation: 45,
                            minRotation: 0
                        },
                        grid: {
                            display: false
                        }
                    }
                } : undefined
            }
        };

        // Create chart
        if (typeof Chart !== 'undefined') {
            const chart = new Chart(ctx, chartConfig);
            canvas.chartInstance = chart;
        } else {
            console.error('Chart.js not loaded');
            container.innerHTML = `<div class="widget-error">${t('error.chartLibMissing', 'Chart library not available')}</div>`;
        }
    }

    renderAllFacetsChart(container, previewData, chartType) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        const allFacetCounts = previewData.allFacetCounts || {};

        if (!allFacetCounts || Object.keys(allFacetCounts).length === 0) {
            container.innerHTML = `<div class="widget-error">${t('message.noChartData', 'No chart data available')}</div>`;
            return;
        }

        // Format facet names for display
        const formatFacetName = (facetId) => {
            const facetMap = {
                'DATASET': 'Data Sets',
                'ATTRIBUTE': 'Attributes',
                'SYSTEM': 'Systems',
                'GLOSSARY': 'Glossary',
                'PEOPLE': 'People',
                'PROCESS': 'Processes',
                'PRODUCT': 'Products',
                'POLICY': 'Policies',
                'LEGAL_ENTITY': 'Legal Entities',
                'BUSINESS_AREA': 'Business Areas',
                'CAPABILITY': 'Capabilities',
                'COMMITTEE': 'Committees',
                'CLIENT': 'Clients',
                'GEOGRAPHY': 'Geographies',
                'REGULATION': 'Regulations',
                'REGULATOR': 'Regulators',
                'REGULATORY_THEME': 'Regulatory Themes'
            };
            return facetMap[facetId] || facetId.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        };

        // Convert allFacetCounts to chart format
        const chartData = Object.entries(allFacetCounts)
            .map(([facetId, count]) => ({
                label: formatFacetName(facetId),
                value: count || 0
            }))
            .sort((a, b) => b.value - a.value);

        const labels = chartData.map(item => item.label);
        const values = chartData.map(item => item.value);

        // Destroy existing chart if any
        const existingCanvas = container.querySelector('canvas');
        if (existingCanvas && existingCanvas.chartInstance) {
            existingCanvas.chartInstance.destroy();
        }

        // Generate unique canvas ID to avoid conflicts
        const canvasId = `chart-${this.containerId}-${Date.now()}`;

        container.innerHTML = `
            <div class="saved-search-chart-container">
                <div class="chart-wrapper" style="flex: 1; min-height: 250px; position: relative;">
                    <canvas id="${canvasId}"></canvas>
                </div>
                <div class="chart-facet-name">All Facets</div>
            </div>
        `;

        const canvas = container.querySelector(`#${canvasId}`);
        if (!canvas) return;

        // Set canvas size for better rendering
        const chartWrapper = container.querySelector('.chart-wrapper');
        if (chartWrapper) {
            canvas.style.width = '100%';
            canvas.style.height = '100%';
        }

        const ctx = canvas.getContext('2d');

        const chartConfig = {
            type: chartType === 'doughnut' ? 'doughnut' : 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Count',
                    data: values,
                    backgroundColor: this.generateColors(labels.length),
                    borderColor: chartType === 'doughnut' ? '#ffffff' : this.generateColors(labels.length),
                    borderWidth: chartType === 'doughnut' ? 2 : 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                layout: {
                    padding: {
                        top: 10,
                        bottom: 10,
                        left: 10,
                        right: 10
                    }
                },
                plugins: {
                    legend: {
                        position: chartType === 'doughnut' ? 'right' : 'top',
                        display: true,
                        labels: {
                            padding: 10,
                            font: {
                                size: 11
                            },
                            usePointStyle: true,
                            boxWidth: 12
                        }
                    },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        padding: 10,
                        titleFont: {
                            size: 12,
                            weight: 'bold'
                        },
                        bodyFont: {
                            size: 11
                        },
                        cornerRadius: 6,
                        callbacks: {
                            label: function(context) {
                                return context.label + ': ' + context.parsed.y || context.parsed;
                            }
                        }
                    }
                },
                scales: chartType === 'bar' ? {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            font: {
                                size: 10
                            },
                            padding: 8,
                            stepSize: 1
                        },
                        grid: {
                            color: 'rgba(0, 0, 0, 0.05)'
                        }
                    },
                    x: {
                        ticks: {
                            font: {
                                size: 10
                            },
                            padding: 8,
                            maxRotation: 45,
                            minRotation: 0
                        },
                        grid: {
                            display: false
                        }
                    }
                } : undefined
            }
        };

        // Create chart
        if (typeof Chart !== 'undefined') {
            const chart = new Chart(ctx, chartConfig);
            canvas.chartInstance = chart;
        } else {
            console.error('Chart.js not loaded');
            container.innerHTML = `<div class="widget-error">${t('error.chartLibMissing', 'Chart library not available')}</div>`;
        }
    }

    renderAllFacetsChart(container, previewData, chartType) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        const allFacetCounts = previewData.allFacetCounts || {};

        if (!allFacetCounts || Object.keys(allFacetCounts).length === 0) {
            container.innerHTML = `<div class="widget-error">${t('message.noChartData', 'No chart data available')}</div>`;
            return;
        }

        // Format facet names for display
        const formatFacetName = (facetId) => {
            const facetMap = {
                'DATASET': 'Data Sets',
                'ATTRIBUTE': 'Attributes',
                'SYSTEM': 'Systems',
                'GLOSSARY': 'Glossary',
                'PEOPLE': 'People',
                'PROCESS': 'Processes',
                'PRODUCT': 'Products',
                'POLICY': 'Policies',
                'LEGAL_ENTITY': 'Legal Entities',
                'BUSINESS_AREA': 'Business Areas',
                'CAPABILITY': 'Capabilities',
                'COMMITTEE': 'Committees',
                'CLIENT': 'Clients',
                'GEOGRAPHY': 'Geographies',
                'REGULATION': 'Regulations',
                'REGULATOR': 'Regulators',
                'REGULATORY_THEME': 'Regulatory Themes'
            };
            return facetMap[facetId] || facetId.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        };

        // Convert allFacetCounts to chart format
        const chartData = Object.entries(allFacetCounts)
            .map(([facetId, count]) => ({
                label: formatFacetName(facetId),
                value: count || 0
            }))
            .sort((a, b) => b.value - a.value);

        const labels = chartData.map(item => item.label);
        const values = chartData.map(item => item.value);

        // Destroy existing chart if any
        const existingCanvas = container.querySelector('canvas');
        if (existingCanvas && existingCanvas.chartInstance) {
            existingCanvas.chartInstance.destroy();
        }

        // Generate unique canvas ID to avoid conflicts
        const canvasId = `chart-${this.containerId}-${Date.now()}`;

        container.innerHTML = `
            <div class="saved-search-chart-container">
                <div class="chart-wrapper" style="flex: 1; min-height: 250px; position: relative;">
                    <canvas id="${canvasId}"></canvas>
                </div>
                <div class="chart-facet-name">All Facets</div>
            </div>
        `;

        const canvas = container.querySelector(`#${canvasId}`);
        if (!canvas) return;

        // Set canvas size for better rendering
        const chartWrapper = container.querySelector('.chart-wrapper');
        if (chartWrapper) {
            canvas.style.width = '100%';
            canvas.style.height = '100%';
        }

        const ctx = canvas.getContext('2d');

        const chartConfig = {
            type: chartType === 'doughnut' ? 'doughnut' : 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Count',
                    data: values,
                    backgroundColor: this.generateColors(labels.length),
                    borderColor: chartType === 'doughnut' ? '#ffffff' : this.generateColors(labels.length),
                    borderWidth: chartType === 'doughnut' ? 2 : 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                layout: {
                    padding: {
                        top: 10,
                        bottom: 10,
                        left: 10,
                        right: 10
                    }
                },
                plugins: {
                    legend: {
                        position: chartType === 'doughnut' ? 'right' : 'top',
                        display: true,
                        labels: {
                            padding: 10,
                            font: {
                                size: 11
                            },
                            usePointStyle: true,
                            boxWidth: 12
                        }
                    },
                    tooltip: {
                        enabled: true,
                        backgroundColor: 'rgba(0, 0, 0, 0.8)',
                        padding: 10,
                        titleFont: {
                            size: 12,
                            weight: 'bold'
                        },
                        bodyFont: {
                            size: 11
                        },
                        cornerRadius: 6,
                        callbacks: {
                            label: function(context) {
                                const label = context.label || '';
                                const value = context.parsed.y !== undefined ? context.parsed.y : context.parsed;
                                return label + ': ' + value;
                            }
                        }
                    }
                },
                scales: chartType === 'bar' ? {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            font: {
                                size: 10
                            },
                            padding: 8,
                            stepSize: 1
                        },
                        grid: {
                            color: 'rgba(0, 0, 0, 0.05)'
                        }
                    },
                    x: {
                        ticks: {
                            font: {
                                size: 10
                            },
                            padding: 8,
                            maxRotation: 45,
                            minRotation: 0
                        },
                        grid: {
                            display: false
                        }
                    }
                } : undefined
            }
        };

        // Create chart
        if (typeof Chart !== 'undefined') {
            const chart = new Chart(ctx, chartConfig);
            canvas.chartInstance = chart;
        } else {
            console.error('Chart.js not loaded');
            container.innerHTML = `<div class="widget-error">${t('error.chartLibMissing', 'Chart library not available')}</div>`;
        }
    }

    generateColors(count) {
        const colors = [
            '#4A90E2', '#50C878', '#F5A623', '#D0021B', '#9013FE',
            '#BD10E0', '#B8E986', '#7ED321', '#417505', '#F8E71C'
        ];
        const result = [];
        for (let i = 0; i < count; i++) {
            result.push(colors[i % colors.length]);
        }
        return result;
    }

    formatColumnName(columnName) {
        if (window.I18n && window.I18n.t) {
            const tr = window.I18n.t('label.' + columnName);
            if (tr !== 'label.' + columnName) return tr;
        }
        return columnName
            .replace(/([A-Z])/g, ' $1')
            .replace(/^./, str => str.toUpperCase())
            .trim();
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">${this.escapeHtml(this.widgetData.title || 'Saved Search Widget')}</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="widget-error">Error loading widget</div>
                </div>
            </div>
        `;
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    handleEdit() {
        // Get widget ID from container
        const container = document.getElementById(this.containerId);
        if (!container) return;

        const widgetId = this.widgetData.id;
        if (!widgetId) {
            console.error('Widget ID not found');
            return;
        }

        // Use dashboard's edit handler if available
        if (window.dashboard && typeof window.dashboard.handleEditCustomWidget === 'function') {
            window.dashboard.handleEditCustomWidget(widgetId);
        } else {
            console.error('Dashboard edit handler not available');
            alert('Edit functionality is not available. Please refresh the page.');
        }
    }

    async handleDelete() {
        // Get widget ID and container
        const container = document.getElementById(this.containerId);
        if (!container) return;

        const widgetId = this.widgetData.id;
        if (!widgetId) {
            console.error('Widget ID not found');
            return;
        }

        // Use dashboard's delete handler if available (for consistency with other widgets)
        if (window.dashboard && typeof window.dashboard.handleDeleteCustomWidget === 'function') {
            await window.dashboard.handleDeleteCustomWidget(widgetId, container.closest('.dashboard-widget'));
        } else {
            // Fallback to simple confirm and delete
        if (!confirm('Are you sure you want to delete this widget?')) {
            return;
        }

        try {
                const response = await fetch(`/api/dashboard/widgets/${widgetId}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (response.ok) {
                // Remove widget from DOM
                    const widgetElement = container.closest('.dashboard-widget');
                    if (widgetElement) {
                        widgetElement.remove();
                }

                // Reload dashboard
                if (window.dashboard) {
                    await window.dashboard.refresh();
                }
            } else {
                alert('Failed to delete widget');
            }
        } catch (error) {
            console.error('Error deleting widget:', error);
            alert('Error deleting widget');
            }
        }
    }

    renderPermissionError(container) {
        const t = (k, d) => window.I18n && window.I18n.t ? window.I18n.t(k) : d;
        const message = t('widget.savedSearch.permissionError', 'The saved search is either deleted or not shared with you.');
        const deleteButtonText = t('widget.deleteWidget', 'Delete Widget');
        
        container.innerHTML = `
            <div class="widget-permission-error" style="text-align: center; padding: 2rem;">
                <p style="margin-bottom: 1rem; color: var(--text-secondary, #666);">${this.escapeHtml(message)}</p>
                <button class="delete-widget-btn" style="
                    background-color: var(--danger, #dc3545);
                    color: white;
                    border: none;
                    padding: 0.5rem 1.5rem;
                    border-radius: 4px;
                    cursor: pointer;
                    font-size: 0.9rem;
                    font-weight: 500;
                    transition: background-color 0.2s;
                " onmouseover="this.style.backgroundColor='var(--danger-hover, #c82333)'" onmouseout="this.style.backgroundColor='var(--danger, #dc3545)'">
                    ${this.escapeHtml(deleteButtonText)}
                </button>
            </div>
        `;
        
        // Attach delete button event listener
        const deleteBtn = container.querySelector('.delete-widget-btn');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', () => {
                this.handleDelete();
            });
        }
    }

    attachEventListeners(container) {
        // Edit button
        const editBtn = container.querySelector('.dashboard-widget-action-btn[title="Edit"]');
        if (editBtn) {
            editBtn.addEventListener('click', () => {
                this.handleEdit();
            });
        }

        // Delete button
        const deleteBtn = container.querySelector('.dashboard-widget-action-btn[title="Delete"]');
        if (deleteBtn) {
            deleteBtn.addEventListener('click', () => {
                this.handleDelete();
            });
        }
    }
}

