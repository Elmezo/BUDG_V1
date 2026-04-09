/**
 * Text Widget
 * Displays custom HTML content
 */

class TextWidget {
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
        } catch (error) {
            console.error('Error initializing text widget:', error);
            this.renderError(container);
        }
    }

    render(container) {
        const config = this.widgetData.config || {};
        const content = config.content || '';
        const title = this.widgetData.title || 'Text Widget';

        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">${this.escapeHtml(title)}</h3>
                    <div class="dashboard-widget-actions">
                        <!-- Widget controls (drag handle, menu) will be added by setupWidgetControls -->
                    </div>
                </div>
                <div class="dashboard-widget-body">
                    <div class="text-widget-content">
                        ${content}
                    </div>
                </div>
            </div>
        `;
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

    handleEdit() {
        // TODO: Implement edit functionality
        console.log('Edit widget:', this.widgetData.id);
        alert('Edit functionality coming soon!');
    }

    async handleDelete() {
        if (!confirm('Are you sure you want to delete this widget?')) {
            return;
        }

        try {
            const response = await fetch(`/api/dashboard/widgets/${this.widgetData.id}`, {
                method: 'DELETE',
                credentials: 'include'
            });

            if (response.ok) {
                // Remove widget from DOM
                const container = document.getElementById(this.containerId);
                if (container) {
                    container.remove();
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

    renderError(container) {
        container.innerHTML = `
            <div class="dashboard-widget">
                <div class="dashboard-widget-header">
                    <h3 class="dashboard-widget-title">Error</h3>
                </div>
                <div class="dashboard-widget-body">
                    <div class="dashboard-widget-empty">
                        Failed to load widget
                    </div>
                </div>
            </div>
        `;
    }

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

