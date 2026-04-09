/**
 * Dashboard Widget: Saved Searches
 * Displays recent and most viewed saved searches with tabs
 */

class SavedSearchesWidget {
    constructor(containerId) {
        this.containerId = containerId;
        this.activeTab = 'recent';
    }

    async init() {
        const container = document.getElementById(this.containerId);
        if (!container) {
            console.error(`Container ${this.containerId} not found`);
            return;
        }

        try {
            const response = await fetch('/api/dashboard/saved-searches', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch saved searches');
            }

            const data = await response.json();
            this.render(container, data);
        } catch (error) {
            console.error('Error loading saved searches:', error);
            this.renderError(container);
        }
    }

    render(container, data) {
        if ((!data.recent || data.recent.length === 0) && (!data.mostViewed || data.mostViewed.length === 0)) {
            this.renderEmpty(container);
            return;
        }

        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="savedSearches">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title clickable" id="savedSearchesTitle">Saved Searches</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-tabs">
                        <button class="dashboard-tab active" data-tab="recent">Recent</button>
                        <button class="dashboard-tab" data-tab="mostViewed">Most Viewed</button>
                    </div>
                    <div class="dashboard-tab-content">
                        <div id="recentSearches" class="dashboard-tab-pane active">
                            ${this.renderSearchList(data.recent)}
                        </div>
                        <div id="mostViewedSearches" class="dashboard-tab-pane">
                            ${this.renderSearchList(data.mostViewed, false)}
                        </div>
                    </div>
                </div>
            </div>
        `;

        // Add click handler for title
        document.getElementById('savedSearchesTitle').addEventListener('click', () => {
            DashboardNavigation.navigateToManageSearches();
        });

        // Add tab switching
        this.initTabs();
    }

    renderSearchList(searches, showHits = false) {
        if (!searches || searches.length === 0) {
            return '<div class="dashboard-widget-empty"><p>No saved searches</p></div>';
        }

        return `
            <div class="dashboard-table-wrapper">
                <table class="dashboard-table">
                    <thead>
                        <tr>
                            <th>Name</th>
                            <th>Description</th>
                            ${showHits ? '<th>Hits</th>' : ''}
                        </tr>
                    </thead>
                    <tbody>
                        ${searches.map(search => `
                            <tr class="clickable" data-search-id="${search.id}">
                                <td><strong>${this.escapeHtml(search.name)}</strong></td>
                                <td>${this.escapeHtml(search.description || '')}</td>
                                ${showHits ? `<td>${search.hits || 0}</td>` : ''}
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;
    }

    initTabs() {
        const tabs = document.querySelectorAll('.dashboard-tab');
        const panes = document.querySelectorAll('.dashboard-tab-pane');

        tabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const targetTab = tab.getAttribute('data-tab');
                
                // Update active tab
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                
                // Update active pane
                panes.forEach(pane => {
                    if (pane.id === `${targetTab}Searches`) {
                        pane.classList.add('active');
                    } else {
                        pane.classList.remove('active');
                    }
                });
                
                this.activeTab = targetTab;
            });
        });

        // Add click handlers for search rows
        document.querySelectorAll('.dashboard-table tbody tr').forEach(row => {
            row.addEventListener('click', () => {
                const searchId = row.getAttribute('data-search-id');
                if (searchId) {
                    DashboardNavigation.loadSavedSearch(searchId);
                }
            });
        });
    }

    renderEmpty(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Saved Searches</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        <i class="fas fa-search"></i>
                        <p>You have no saved searches</p>
                    </div>
                </div>
            </div>
        `;
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Saved Searches</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load saved searches</p>
                    </div>
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

    destroy() {
        // No chart to destroy
    }
}

// Make available globally
window.SavedSearchesWidget = SavedSearchesWidget;

