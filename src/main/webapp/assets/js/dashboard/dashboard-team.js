/**
 * Dashboard Widget: Team
 * Displays team structure (management, peers, reportees)
 */

class TeamWidget {
    constructor(containerId) {
        this.containerId = containerId;
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
            const response = await fetch('/api/dashboard/team', {
                method: 'GET',
                credentials: 'include',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                throw new Error('Failed to fetch team data');
            }

            const data = await response.json();
            this.render(container, data);
        } catch (error) {
            console.error('Error loading team data:', error);
            this.renderError(container);
        }
    }

    render(container, data) {
        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="team">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title clickable" id="teamTitle">Team</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-team-sections">
                        ${this.renderSection('Management', data.management)}
                        ${this.renderSection('Peers', data.peers)}
                        ${this.renderSection('Reportees', data.reportees)}
                    </div>
                </div>
            </div>
        `;

        // Add click handler for title
        document.getElementById('teamTitle').addEventListener('click', () => {
            if (this.userId) {
                DashboardNavigation.navigateToTeam(this.userId);
            }
        });

        // Add click handlers for person names
        this.initPersonLinks();
        
        // Add click handlers for expandable sections
        this.initSectionToggles();
    }
    
    initSectionToggles() {
        document.querySelectorAll('.dashboard-team-section-title.clickable').forEach(title => {
            title.addEventListener('click', (e) => {
                // Toggle section expand/collapse
                const sectionId = title.getAttribute('data-section-id');
                const content = document.getElementById(sectionId);
                const toggleIcon = title.querySelector('.dashboard-team-section-toggle i');
                
                if (content) {
                    const isExpanded = content.classList.contains('expanded');
                    
                    if (isExpanded) {
                        content.classList.remove('expanded');
                        content.classList.add('collapsed');
                        if (toggleIcon) {
                            toggleIcon.classList.remove('fa-chevron-down');
                            toggleIcon.classList.add('fa-chevron-right');
                        }
                    } else {
                        content.classList.remove('collapsed');
                        content.classList.add('expanded');
                        if (toggleIcon) {
                            toggleIcon.classList.remove('fa-chevron-right');
                            toggleIcon.classList.add('fa-chevron-down');
                        }
                    }
                }
            });
        });
    }

    renderSection(title, people) {
        const hasData = people && people.length > 0;
        const sectionId = `team-section-${title.toLowerCase().replace(/\s+/g, '-')}`;
        
        return `
            <div class="dashboard-team-section">
                <h4 class="dashboard-team-section-title clickable" data-section-id="${sectionId}">
                    <span class="dashboard-team-section-toggle">
                        <i class="fas fa-chevron-down"></i>
                    </span>
                    <span class="dashboard-team-section-title-text">${title}</span>
                </h4>
                <div class="dashboard-team-section-content expanded" id="${sectionId}">
                    ${hasData ? `
                        <ul class="dashboard-team-list">
                            ${people.map(person => `
                                <li class="dashboard-team-item clickable" data-person-id="${person.id}">
                                    <div class="dashboard-team-person">
                                        <div class="dashboard-team-person-name">
                                            ${this.escapeHtml(person.firstName)} ${this.escapeHtml(person.lastName)}
                                        </div>
                                        <div class="dashboard-team-person-details">
                                            ${person.functionName ? this.escapeHtml(person.functionName) : ''}
                                            ${person.email ? `<span class="dashboard-team-person-email">${this.escapeHtml(person.email)}</span>` : ''}
                                        </div>
                                    </div>
                                </li>
                            `).join('')}
                        </ul>
                    ` : `
                        <div class="dashboard-team-section-empty">
                            <p>No ${title.toLowerCase()} information available</p>
                        </div>
                    `}
                </div>
            </div>
        `;
    }

    initPersonLinks() {
        document.querySelectorAll('.dashboard-team-item').forEach(item => {
            item.addEventListener('click', () => {
                const personId = item.getAttribute('data-person-id');
                if (personId) {
                    DashboardNavigation.navigateToPerson(personId);
                }
            });
        });
    }

    renderEmpty(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Team</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        <i class="fas fa-users"></i>
                        <p>No team information available</p>
                    </div>
                </div>
            </div>
        `;
    }

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Team</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-error">
                        <i class="fas fa-exclamation-triangle"></i>
                        <p>Failed to load team data</p>
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
window.TeamWidget = TeamWidget;

