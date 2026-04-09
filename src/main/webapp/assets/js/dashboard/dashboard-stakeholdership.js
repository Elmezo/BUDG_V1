/**
 * Dashboard Widget: Stakeholdership
 * Displays grouped horizontal bar chart of stakeholder/owner counts by facet
 * Each facet has two separate bars: one for stakeholders (blue) and one for owners (orange)
 */

class StakeholdershipWidget {
    constructor(containerId) {
        this.containerId = containerId;
        this.chart = null;
        this.userId = null;
        this.facetLabels = [];
    }

    async init(userId) {
        this.userId = userId;
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }

        try {
            const response = await fetch('/api/dashboard/stakeholdership', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch stakeholdership data');
            }

            const data = await response.json();
            this.render(container, data);
        } catch (error) {
            console.error('Error loading stakeholdership:', error);
            this.renderError(container);
        }
    }

    render(container, data) {
        if (!data || data.length === 0) {
            this.renderEmpty(container);
            return;
        }

        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="stakeholdership">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title clickable" id="stakeholdershipTitle">Stakeholdership</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-chart-container">
                        <canvas id="stakeholdershipChart"></canvas>
                    </div>
                </div>
            </div>
        `;

        // Add click handler for title
        document.getElementById('stakeholdershipTitle').addEventListener('click', () => {
            if (this.userId) {
                DashboardNavigation.navigateToResponsibilities(this.userId, 'all', false);
            }
        });

        this.renderChart(data);
    }

    renderChart(data) {
        const ctx = document.getElementById('stakeholdershipChart');
        if (!ctx) return;

        // Destroy existing chart if it exists
        if (this.chart) {
            this.chart.destroy();
        }

        // Restructure data: each facet appears twice - once for Stakeholder, once for Owner
        const labels = [];
        const values = [];
        const colors = [];
        const facetMap = new Map(); // Map to track which facet each bar belongs to

        data.forEach(item => {
            const facet = item.facet;
            
            // Add Stakeholder bar for this facet
            if (item.stakeholder > 0 || facet === 'Others') {
                labels.push(facet);
                values.push(item.stakeholder);
                colors.push('rgba(54, 162, 235, 0.7)');
                facetMap.set(labels.length - 1, facet);
            }
            
            // Add Owner bar for this facet
            if (item.owner > 0 || facet === 'Others') {
                labels.push(facet);
                values.push(item.owner);
                colors.push('rgba(255, 159, 64, 0.7)');
                facetMap.set(labels.length - 1, facet);
            }
        });

        // Store facet mapping for click handling
        this.facetMap = facetMap;

        this.chart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Count',
                    data: values,
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
                    if (elements.length > 0 && this.userId) {
                        const element = elements[0];
                        const barIndex = element.index;
                        const facetName = facetMap.get(barIndex);
                        if (facetName && facetName !== 'Others') {
                            DashboardNavigation.navigateToResponsibilities(this.userId, facetName, false);
                        }
                    }
                },
                scales: {
                    x: {
                        beginAtZero: true,
                        ticks: {
                            precision: 0,
                            stepSize: 1
                        }
                    }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'bottom',
                        labels: {
                            generateLabels: function(chart) {
                                return [
                                    {
                                        text: 'Stakeholder',
                                        fillStyle: 'rgba(54, 162, 235, 0.7)',
                                        strokeStyle: 'rgba(54, 162, 235, 1)',
                                        lineWidth: 1
                                    },
                                    {
                                        text: 'Owner',
                                        fillStyle: 'rgba(255, 159, 64, 0.7)',
                                        strokeStyle: 'rgba(255, 159, 64, 1)',
                                        lineWidth: 1
                                    }
                                ];
                            }
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: (context) => {
                                const index = context.dataIndex;
                                const label = labels[index];
                                const value = values[index];
                                // Determine if this is stakeholder or owner based on color
                                const isOwner = colors[index].includes('255, 159, 64');
                                const type = isOwner ? 'Owner' : 'Stakeholder';
                                return `${label} - ${type}: ${value}`;
                            }
                        }
                    }
                }
            }
        });
    }

    renderEmpty(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Stakeholdership</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        <i class="fas fa-users"></i>
                        <p>You are not a stakeholder or owner of any objects</p>
                    </div>
                </div>
            </div>
        `;
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Stakeholdership</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load stakeholdership data</p>
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
window.StakeholdershipWidget = StakeholdershipWidget;

