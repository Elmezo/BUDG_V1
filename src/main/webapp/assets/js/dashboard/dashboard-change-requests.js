/**
 * Dashboard Widget: Change Requests
 * Displays pie chart of change requests by age with tabs for contributing/raised
 */

class ChangeRequestsWidget {
    constructor(containerId) {
        this.containerId = containerId;
        this.chart = null;
        this.userId = null;
        this.activeType = 'contributing';
    }

    async init(userId) {
        this.userId = userId;
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }

        await this.loadData(container, this.activeType);
    }

    async loadData(container, type) {
        try {
            const response = await fetch(`/api/dashboard/change-requests?type=${type}`, {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch change requests');
            }

            const data = await response.json();
            this.render(container, data);
        } catch (error) {
            console.error('Error loading change requests:', error);
            this.renderError(container);
        }
    }

    render(container, data) {
        if (!data || !data.ageGroups || data.total === 0) {
            this.renderEmpty(container, data.type);
            return;
        }

        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="changeRequests">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title clickable" id="changeRequestsTitle">Change Requests</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-tabs">
                        <button class="dashboard-tab ${data.type === 'contributing' ? 'active' : ''}" data-type="contributing">Contributing</button>
                        <button class="dashboard-tab ${data.type === 'raised' ? 'active' : ''}" data-type="raised">Raised by Me</button>
                    </div>
                    <div class="dashboard-chart-container">
                        <canvas id="changeRequestsChart"></canvas>
                    </div>
                </div>
            </div>
        `;

        // Add click handler for title
        document.getElementById('changeRequestsTitle').addEventListener('click', () => {
            if (this.userId) {
                DashboardNavigation.navigateToChangeRequests(this.userId);
            }
        });

        // Add tab switching
        this.initTabs(container);
        
        this.renderChart(data);
        
        // Ensure widget menu is added after rendering
        // setupWidgetControls should handle this, but we ensure it's called
        setTimeout(() => {
            const widget = container.querySelector('.dashboard-widget');
            if (widget && window.dashboard) {
                // Check if menu already exists
                if (!widget.querySelector('.widget-menu-container')) {
                    window.dashboard.addWidgetMenu(widget);
                    window.dashboard.addDragHandle(widget);
                }
            }
        }, 100);
    }

    renderChart(data) {
        const ctx = document.getElementById('changeRequestsChart');
        if (!ctx) return;

        const labels = data.ageGroups.map(item => `${item.ageRange} (${item.count})`);
        const counts = data.ageGroups.map(item => item.count);
        const colors = [
            'rgba(54, 162, 235, 0.8)',  // Blue for less than 7 days
            'rgba(75, 192, 192, 0.8)',  // Turquoise for 7 to 30 days
            'rgba(153, 102, 255, 0.8)'  // Purple for more than 30 days
        ];

        if (this.chart) {
            this.chart.destroy();
        }

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
                maintainAspectRatio: true,
                cutout: '65%', // Increase inner radius to give more space for center text
                onClick: (event, elements) => {
                    if (this.userId) {
                        DashboardNavigation.navigateToChangeRequests(this.userId);
                    }
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'right',
                        labels: {
                            padding: 15,
                            usePointStyle: true,
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
                                return `${label}: ${value}`;
                            }
                        }
                    }
                }
            },
            plugins: [{
                id: 'centerText',
                afterDraw: function(chart) {
                    const width = chart.width;
                    const height = chart.height;
                    const ctx = chart.ctx;
                    
                    // Get the chart area (excluding padding)
                    const chartArea = chart.chartArea;
                    const centerX = (chartArea.left + chartArea.right) / 2;
                    const centerY = (chartArea.top + chartArea.bottom) / 2;
                    
                    // Calculate font size based on available space in the center
                    const availableWidth = chartArea.right - chartArea.left;
                    const availableHeight = chartArea.bottom - chartArea.top;
                    const fontSize = Math.min(availableWidth / 6, availableHeight / 6, 48);
                    
                    ctx.save();
                    ctx.font = `bold ${fontSize}px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif`;
                    ctx.textBaseline = "middle";
                    ctx.textAlign = "center";
                    ctx.fillStyle = "#2c3e50"; // Dark grey color
                    
                    const text = data.total.toString();
                    
                    // Draw text at exact center of chart area
                    ctx.fillText(text, centerX, centerY);
                    ctx.restore();
                }
            }]
        });
    }

    initTabs(container) {
        const tabs = document.querySelectorAll('.dashboard-tab');
        
        tabs.forEach(tab => {
            tab.addEventListener('click', async () => {
                const type = tab.getAttribute('data-type');
                this.activeType = type;
                
                // Update active tab
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                
                // Reload data for new type
                await this.loadData(container, type);
            });
        });
    }

    renderEmpty(container, type) {
        const typeLabel = type === 'raised' ? 'raised by you' : 'you are contributing to';
        
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Change Requests</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-tabs">
                        <button class="dashboard-tab ${type === 'contributing' ? 'active' : ''}" data-type="contributing">Contributing</button>
                        <button class="dashboard-tab ${type === 'raised' ? 'active' : ''}" data-type="raised">Raised by Me</button>
                    </div>
                    <div class="dashboard-widget-empty">
                        <i class="fas fa-exchange-alt"></i>
                        <p>You do not have any change requests ${typeLabel}</p>
                    </div>
                </div>
            </div>
        `;
        
        // Re-initialize tabs for empty state
        this.initTabs(container);
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Change Requests</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load change requests</p>
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
window.ChangeRequestsWidget = ChangeRequestsWidget;

