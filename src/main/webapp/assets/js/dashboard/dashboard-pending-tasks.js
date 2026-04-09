/**
 * Dashboard Widget: Pending Tasks
 * Displays pending tasks for the current user
 * Uses the same function as Unison Search Service (loadActiveTasksData)
 */

class PendingTasksWidget {
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
            // Use the same function as Unison Search Service
            let tasks = [];
            if (typeof window.loadActiveTasksData === 'function') {
                tasks = await window.loadActiveTasksData();
            } else {
                // Fallback: direct API call if function not available
                const response = await fetch('/api/active-tasks', {
                    method: 'GET',
                    headers: { 'Accept': 'application/json' },
                    credentials: 'include'
                });

                if (response.ok) {
                    tasks = await response.json();
                    if (!Array.isArray(tasks)) {
                        tasks = [];
                    }
                }
            }

            this.render(container, tasks);
        } catch (error) {
            console.error('Error loading pending tasks:', error);
            this.renderEmpty(container);
        }
    }

    render(container, tasks) {
        if (!tasks || tasks.length === 0) {
            this.renderEmpty(container);
            return;
        }

        // Calculate overdue count
        const overdueCount = tasks.filter(task => 
            task.isOverdue === true || (task.dueInDays !== null && task.dueInDays !== undefined && task.dueInDays < 0)
        ).length;
        const totalCount = tasks.length;

        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="pendingTasks">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title clickable" id="pendingTasksTitle">
                        Pending Tasks | ${totalCount}${overdueCount > 0 ? ` (${overdueCount} Overdue)` : ''}
                    </h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="pending-tasks-list">
                        ${tasks.map(task => {
                            const taskName = this.escapeHtml(task.name || '');
                            const changeRequestTitle = this.escapeHtml(task.title || task.object || '');
                            const changeRequestId = task.changeRequestId;
                            const dueInDays = task.dueInDays !== null && task.dueInDays !== undefined ? task.dueInDays : null;
                            const isOverdue = task.isOverdue === true || (dueInDays !== null && dueInDays < 0);
                            
                            // Calculate time remaining or overdue
                            let timeText = '';
                            if (dueInDays !== null && dueInDays !== undefined) {
                                if (dueInDays < 0) {
                                    // Overdue
                                    const overdueDays = Math.abs(dueInDays);
                                    timeText = ` - <span class="pending-tasks-overdue-text">Overdue by ${overdueDays} day${overdueDays !== 1 ? 's' : ''}.</span>`;
                                } else if (dueInDays > 0) {
                                    // Still has time
                                    timeText = ` - <span class="pending-tasks-remaining-text">Due in ${dueInDays} day${dueInDays !== 1 ? 's' : ''}.</span>`;
                                } else if (dueInDays === 0) {
                                    // Due today
                                    timeText = ` - <span class="pending-tasks-due-today-text">Due today.</span>`;
                                }
                            }

                            // Build change request link
                            let changeRequestLink = changeRequestTitle;
                            if (changeRequestId) {
                                changeRequestLink = `<a href="/view/change-request/change-request-view.html?id=${changeRequestId}" class="pending-task-cr-link" onclick="event.stopPropagation();">${changeRequestTitle}</a>`;
                            }

                            return `
                                <div class="pending-task-item" data-task-id="${task.taskId || task.id}">
                                    <div class="pending-task-text">
                                        Your workflow step <strong>'${taskName}'</strong> for change request 
                                        ${changeRequestLink}${timeText}
                                    </div>
                                </div>
                            `;
                        }).join('')}
                    </div>
                </div>
            </div>
        `;

        // Add click handler for title
        const titleElement = document.getElementById('pendingTasksTitle');
        if (titleElement) {
            titleElement.addEventListener('click', () => {
                if (this.userId && window.DashboardNavigation) {
                    window.DashboardNavigation.navigateToMyAccount(this.userId, 'tasks');
                }
            });
        }

        // Add click handlers for task items
        container.querySelectorAll('.pending-task-item').forEach(item => {
            item.addEventListener('click', () => {
                const taskId = item.getAttribute('data-task-id');
                if (taskId) {
                    // Navigate to task details if needed
                    console.log('Task clicked:', taskId);
                }
            });
        });
    }

    renderEmpty(container) {
        container.innerHTML = `
            <div class="dashboard-widget" data-widget-id="pendingTasks">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Pending Tasks | 0</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        <p>You do not have any pending task.</p>
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
        // No cleanup needed
    }
}

// Make available globally
window.PendingTasksWidget = PendingTasksWidget;

