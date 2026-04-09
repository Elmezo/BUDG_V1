/**
 * Dashboard Widget: Object Counts
 * Displays horizontal bar chart of object counts by facet type
 */

/** Map API facet display names to i18n keys for translation */
const OBJECT_COUNTS_FACET_KEYS = {
    'Data Set': 'facet.dataset', 'Data Sets': 'facet.dataset',
    'System': 'facet.system', 'Glossary': 'facet.glossary', 'Policy': 'facet.policy',
    'Role': 'facet.role', 'Others': 'dashboard.others',
    'Business Area': 'facet.businessArea', 'Legal Entity': 'facet.legalEntity',
    'Committee': 'facet.committee', 'Process': 'facet.process', 'Client': 'facet.client',
    'Capability': 'facet.capability', 'Product': 'facet.product', 'Org Unit': 'facet.orgUnit',
    'People': 'facet.people', 'Geography': 'facet.geography', 'Regulation': 'facet.regulation',
    'Regulator': 'facet.regulator', 'Regulatory Theme': 'facet.regulatoryTheme',
    'Project': 'facet.project', 'Change Requests': 'facet.changeRequests'
};

function translateFacetLabel(label) {
    if (!label) return label;
    if (window.I18n && window.I18n.t) {
        const key = OBJECT_COUNTS_FACET_KEYS[label];
        if (key) {
            const t = window.I18n.t(key);
            if (t !== key) return t;
        }
    }
    return label;
}

class ObjectCountsWidget {
    constructor(containerId) {
        this.containerId = containerId;
        this.chart = null;
    }

    async init() {
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }

        try {
            const response = await fetch('/api/dashboard/object-counts', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch object counts');
            }

            const data = await response.json();
            this.render(container, data);
        } catch (error) {
            console.error('Error loading object counts:', error);
            this.renderError(container);
        }
    }

    render(container, data) {
        if (!data || data.length === 0) {
            this.renderEmpty(container);
            return;
        }

        const titleText = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.objectCounts') : 'Object Counts';
        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="objectCounts">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">${titleText}</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-chart-container">
                        <canvas id="objectCountsChart"></canvas>
                    </div>
                </div>
            </div>
        `;

        this.renderChart(data);
    }

    renderChart(data) {
        const ctx = document.getElementById('objectCountsChart');
        if (!ctx) return;

        const originalLabels = data.map(item => item.facet);
        const labels = data.map(item => translateFacetLabel(item.facet));
        const counts = data.map(item => item.count);
        const colors = this.generateColors(data.length);

        this.chart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Count',
                    data: counts,
                    backgroundColor: colors,
                    borderColor: colors.map(c => c.replace('0.7', '1')),
                    borderWidth: 1
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                layout: {
                    padding: {
                        left: 0,
                        right: 0,
                        top: 0,
                        bottom: 0
                    }
                },
                onClick: (event, elements) => {
                    if (elements.length > 0) {
                        const index = elements[0].index;
                        const facetName = originalLabels[index];
                        if (facetName !== 'Others') {
                            // Navigate to specific facet in Unison search
                            // Store the facet name in sessionStorage so it can be selected after page load
                            sessionStorage.setItem('selectedSearchCategory', facetName);
                            DashboardNavigation.navigateToFacetSearch(facetName);
                        }
                    }
                },
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                return `Count: ${context.parsed.x}`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        beginAtZero: true,
                        ticks: {
                            precision: 0
                        }
                    }
                }
            }
        });
    }

    generateColors(count) {
        const baseColors = [
            'rgba(54, 162, 235, 0.7)',
            'rgba(255, 206, 86, 0.7)',
            'rgba(75, 192, 192, 0.7)',
            'rgba(153, 102, 255, 0.7)',
            'rgba(255, 159, 64, 0.7)',
            'rgba(255, 99, 132, 0.7)'
        ];
        
        const colors = [];
        for (let i = 0; i < count; i++) {
            colors.push(baseColors[i % baseColors.length]);
        }
        return colors;
    }

    renderEmpty(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Object Counts</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        <i class="fas fa-inbox"></i>
                        <p>No objects found</p>
                    </div>
                </div>
            </div>
        `;
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Object Counts</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load object counts</p>
                    </div>
                </div>
            </div>
        `;
    }

    destroy() {
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }
    }
}

// Make available globally
window.ObjectCountsWidget = ObjectCountsWidget;

