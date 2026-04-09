/**
 * Dashboard Widget: Roles Not Accepted
 * Displays pie chart of unaccepted roles by facet for current user
 */

/** Map API facet display names to i18n keys for translation */
const ROLES_FACET_KEYS = {
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

function translateRolesFacetLabel(label) {
    if (!label) return label;
    if (window.I18n && window.I18n.t) {
        const key = ROLES_FACET_KEYS[label];
        if (key) {
            const t = window.I18n.t(key);
            if (t !== key) return t;
        }
    }
    return label;
}

class RolesNotAcceptedWidget {
    constructor(containerId) {
        this.containerId = containerId;
        this.chart = null;
        this.userId = null;
    }

    async init(userId) {
        this.userId = userId;
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }

        try {
            const response = await fetch('/api/dashboard/roles-not-accepted', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch roles not accepted');
            }

            const data = await response.json();
            this.render(container, data);
        } catch (error) {
            console.error('Error loading roles not accepted:', error);
            this.renderError(container);
        }
    }

    render(container, data) {
        // Destroy existing chart before re-rendering
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }

        if (!data || !data.facets || data.facets.length === 0 || data.total === 0) {
            this.renderEmpty(container);
            return;
        }

        const titleBase = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.rolesNotAccepted') : 'Roles Not Accepted';
        const outOfText = (window.I18n && window.I18n.t) ? window.I18n.t('dashboard.rolesNotAcceptedOutOf', { n: data.total, total: data.total }) : `${data.total} out of ${data.total}`;
        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="rolesNotAccepted">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title clickable" id="rolesNotAcceptedTitle">
                        ${titleBase} | ${outOfText}
                    </h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-chart-container">
                        <canvas id="rolesNotAcceptedChart"></canvas>
                    </div>
                </div>
            </div>
        `;

        // Add click handler for title
        const titleElement = document.getElementById('rolesNotAcceptedTitle');
        if (titleElement) {
            titleElement.addEventListener('click', () => {
                if (this.userId) {
                    // Navigate to all responsibilities
                    window.location.href = `/view/people/${this.userId}?tab=responsibilities&subtab=all&filter=roleAccepted:No`;
                }
            });
        }

        // Render chart after DOM is ready
        setTimeout(() => {
            this.renderChart(data);
        }, 50);
    }

    renderChart(data) {
        // Destroy existing chart if it exists
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }

        const ctx = document.getElementById('rolesNotAcceptedChart');
        if (!ctx) {
            console.error('Chart canvas not found');
            return;
        }

        const originalLabels = data.facets.map(item => item.facet);
        const labels = data.facets.map(item => translateRolesFacetLabel(item.facet));
        const counts = data.facets.map(item => item.count);
        const colors = this.generateColors(data.facets.length);

        try {
            this.chart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: labels,
                    datasets: [{
                        data: counts,
                        backgroundColor: colors,
                        borderColor: '#fff',
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    cutout: '70%',
                    layout: {
                        padding: {
                            left: 0,
                            right: 0,
                            top: 0,
                            bottom: 0
                        }
                    },
                    onClick: (event, elements) => {
                        if (elements.length > 0 && this.userId) {
                            const index = elements[0].index;
                            const facetName = originalLabels[index];
                            if (facetName !== 'Others') {
                                // Navigate to specific facet in responsibilities
                                // Use the exact facet name as it appears in the data (e.g., "Data Set", "Business Area")
                                // Store the facet name in sessionStorage so it can be selected after page load
                                sessionStorage.setItem('selectedResponsibilityFacet', facetName);
                                window.location.href = `/view/people/${this.userId}?tab=responsibilities&subtab=${encodeURIComponent(facetName)}&filter=roleAccepted:No`;
                            }
                        }
                    },
                    plugins: {
                        legend: {
                            display: true,
                            position: 'right',
                            labels: {
                                boxWidth: 15,
                                padding: 15,
                                font: {
                                    size: 12
                                }
                            }
                        },
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    const label = context.label || '';
                                    const value = context.parsed || 0;
                                    return `${label} ( ${value} )`;
                                }
                            }
                        }
                    }
                },
                plugins: [{
                    id: 'centerText',
                    beforeDraw: function(chart) {
                        const width = chart.width;
                        const height = chart.height;
                        const ctx = chart.ctx;
                        ctx.restore();
                        
                        // Total count
                        const total = data.total;
                        const fontSize = (height / 160).toFixed(2) * 40;
                        ctx.font = `bold ${fontSize}px Arial`;
                        ctx.textBaseline = 'middle';
                        ctx.textAlign = 'center';
                        ctx.fillStyle = '#333';
                        ctx.fillText(total, width / 2, height / 2);
                        
                        ctx.save();
                    }
                }]
            });
        } catch (error) {
            console.error('Error rendering chart:', error);
        }
    }

    // Legend is now handled by Chart.js directly

    generateColors(count) {
        const baseColors = [
            'rgba(54, 162, 235, 0.8)',
            'rgba(75, 192, 192, 0.8)',
            'rgba(153, 102, 255, 0.8)',
            'rgba(255, 159, 64, 0.8)',
            'rgba(255, 99, 132, 0.8)',
            'rgba(255, 206, 86, 0.8)'
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
                    <h3 class="dashboard-widget-title">Roles Not Accepted</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        <i class="fas fa-check-circle"></i>
                        <p>You have accepted all your roles</p>
                    </div>
                </div>
            </div>
        `;
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Roles Not Accepted</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load roles data</p>
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
window.RolesNotAcceptedWidget = RolesNotAcceptedWidget;

