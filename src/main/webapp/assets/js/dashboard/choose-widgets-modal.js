/**
 * Choose Widgets Modal
 * Allows users to show/hide widgets in the dashboard
 */
class ChooseWidgetsModal {
    constructor() {
        this.modal = document.getElementById('chooseWidgetsModal');
        this.widgetList = document.getElementById('chooseWidgetsList');
        
        // Default system widgets
        this.defaultWidgets = [
            { id: 'objectCounts', name: 'Object Counts', type: 'default' },
            { id: 'rolesNotAccepted', name: 'Roles Not Accepted', type: 'default' },
            { id: 'stakeholdership', name: 'Stakeholdership', type: 'default' },
            { id: 'savedSearches', name: 'Saved Searches', type: 'default' },
            { id: 'changeRequests', name: 'Change Requests', type: 'default' },
            { id: 'team', name: 'Team', type: 'default' },
            { id: 'pendingTasks', name: 'Pending Tasks', type: 'default' }
        ];
        
        // Custom widgets loaded from database
        this.customWidgets = [];
        
        // Combined list
        this.widgets = [...this.defaultWidgets];
        
        this.init();
    }

    init() {
        if (!this.modal) return;
        
        // Close button
        const closeBtn = document.getElementById('chooseWidgetsModalClose');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => this.close());
        }
        
        // Cancel button
        const cancelBtn = document.getElementById('chooseWidgetsCancel');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', () => this.close());
        }
        
        // OK button
        const okBtn = document.getElementById('chooseWidgetsOK');
        if (okBtn) {
            okBtn.addEventListener('click', () => this.handleSave());
        }
        
        // Close on overlay click
        this.modal.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.close();
            }
        });
    }

    async open() {
        if (!this.modal) return;
        
        // Load custom widgets from database before rendering
        await this.loadCustomWidgets();
        
        this.modal.style.display = 'flex';
        this.renderWidgetList();
    }

    /**
     * Get translated display name for a widget (default widgets use dashboard.* keys, custom use name as-is)
     */
    getWidgetDisplayName(widget) {
        if (window.I18n && window.I18n.t) {
            const key = 'dashboard.' + widget.id;
            const t = window.I18n.t(key);
            if (t !== key) return t;
        }
        return widget.name;
    }

    /**
     * Check if we're in a new dashboard (not main dashboard)
     */
    isNewDashboard() {
        // If currentDashboardId is set and not null, we're in a new dashboard
        return window.dashboard && window.dashboard.currentDashboardId !== null;
    }
    
    /**
     * Load custom widgets from database
     */
    async loadCustomWidgets() {
        try {
            // Get dashboard ID if we're in a new dashboard
            let url = '/api/dashboard/widgets';
            if (this.isNewDashboard() && window.dashboard && window.dashboard.currentDashboardId) {
                url += `?dashboardId=${window.dashboard.currentDashboardId}`;
            }
            
            const response = await fetch(url, {
                credentials: 'include'
            });
            
            if (!response.ok) {
                console.error('Failed to load custom widgets');
                this.customWidgets = [];
                if (this.isNewDashboard()) {
                    this.widgets = [];
                } else {
                    this.widgets = [...this.defaultWidgets];
                }
                return;
            }
            
            const allWidgets = await response.json();
            
            // Filter only custom widgets (text widgets and saved search widgets)
            this.customWidgets = allWidgets
                .filter(w => w.type === 'text' || w.type === 'saved_search')
                .map(widget => ({
                    id: `widget${widget.id}`,
                    name: widget.title,
                    type: 'custom',
                    widgetId: widget.id,
                    widgetType: widget.type
                }));
            
            // Combine widgets based on dashboard type
            // For new dashboards, only show custom widgets
            // For main dashboard, show both default and custom widgets
            if (this.isNewDashboard()) {
                // New dashboard - only custom widgets
                this.widgets = [...this.customWidgets];
            } else {
                // Main dashboard - both default and custom widgets
                this.widgets = [...this.defaultWidgets, ...this.customWidgets];
            }
            
            console.log('Loaded custom widgets:', this.customWidgets);
        } catch (error) {
            console.error('Error loading custom widgets:', error);
            this.customWidgets = [];
            if (this.isNewDashboard()) {
                this.widgets = [];
            } else {
                this.widgets = [...this.defaultWidgets];
            }
        }
    }

    close() {
        if (!this.modal) return;
        this.modal.style.display = 'none';
    }

    renderWidgetList() {
        if (!this.widgetList) return;
        
        // Load hidden widgets from localStorage directly to get the actual state
        // Do this BEFORE creating any DashboardDragDrop instance to avoid side effects
        let hiddenWidgets = [];
        try {
            const saved = localStorage.getItem('dashboardHiddenWidgets');
            if (saved !== null && saved !== '') {
                // There's a saved preference - parse and use it exactly
                hiddenWidgets = JSON.parse(saved);
                // If pendingTasks is NOT in the list, it means user explicitly showed it (checked)
                // If pendingTasks IS in the list, it means user explicitly hid it (unchecked)
            } else {
                // No preference saved yet - use defaults
                // All widgets except pendingTasks are shown by default
                hiddenWidgets = ['pendingTasks'];
            }
        } catch (e) {
            console.error('Error parsing hiddenWidgets from localStorage:', e);
            hiddenWidgets = ['pendingTasks']; // Default to hidden on error
        }
        
        // Get dragDrop instance for later use (but don't let it modify state)
        let dragDrop = null;
        if (window.dashboard && window.dashboard.dragDrop) {
            dragDrop = window.dashboard.dragDrop;
        }
        
        this.widgetList.innerHTML = '';
        
        this.widgets.forEach(widget => {
            // Check both localStorage AND actual DOM visibility
            const isInHiddenList = hiddenWidgets.includes(widget.id);
            
            // Also check if widget container is actually visible in DOM
            // For custom widgets, the ID already includes "widget" prefix, so we just add "Widget" suffix
            // For default widgets, we add "Widget" suffix to the ID
            const containerId = widget.id + 'Widget';
            const widgetContainer = document.getElementById(containerId);
            const isActuallyVisible = widgetContainer && widgetContainer.style.display !== 'none' && widgetContainer.offsetParent !== null;
            
            // Widget is hidden if it's in the hidden list OR if it's not visible in DOM
            const isHidden = isInHiddenList || !isActuallyVisible;
            
            // Checkbox is checked if widget is NOT hidden (i.e., it's shown)
            const isChecked = !isHidden;
            
            // Debug: Log pendingTasks state for troubleshooting
            if (widget.id === 'pendingTasks') {
                console.log('Pending Tasks checkbox state:', {
                    widgetId: widget.id,
                    isInHiddenList,
                    isActuallyVisible,
                    isHidden,
                    isChecked,
                    hiddenWidgets: hiddenWidgets,
                    containerDisplay: widgetContainer ? widgetContainer.style.display : 'no container',
                    localStorageValue: localStorage.getItem('dashboardHiddenWidgets')
                });
            }
            
            const item = document.createElement('div');
            item.className = 'choose-widget-item';
            
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.id = `widget-${widget.id}`;
            checkbox.checked = isChecked;
            checkbox.dataset.widgetId = widget.id;
            
            const label = document.createElement('label');
            label.htmlFor = checkbox.id;
            label.className = 'choose-widget-label';
            
            const nameSpan = document.createElement('span');
            nameSpan.className = 'choose-widget-name';
            nameSpan.textContent = this.getWidgetDisplayName(widget);
            
            label.appendChild(nameSpan);
            
            // Add badge for widget type
            if (widget.type === 'custom') {
                const customBadge = document.createElement('span');
                customBadge.className = 'choose-widget-badge custom-badge';
                customBadge.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('widget.custom') : 'Custom';
                label.appendChild(customBadge);
            } else {
                const defaultBadge = document.createElement('span');
                defaultBadge.className = 'choose-widget-badge';
                defaultBadge.textContent = (window.I18n && window.I18n.t) ? window.I18n.t('widget.default') : '(Default)';
                label.appendChild(defaultBadge);
            }
            
            item.appendChild(checkbox);
            item.appendChild(label);
            
            this.widgetList.appendChild(item);
        });
    }

    handleSave() {
        if (!this.widgetList) return;
        
        const checkboxes = this.widgetList.querySelectorAll('input[type="checkbox"]');
        
        let dragDrop = null;
        if (window.dashboard && window.dashboard.dragDrop) {
            dragDrop = window.dashboard.dragDrop;
        } else {
            console.error('DashboardDragDrop not available');
            this.close();
            return;
        }
        
        // Get all checked and unchecked widgets
        const checkedWidgets = [];
        const uncheckedWidgets = [];
        
        checkboxes.forEach(checkbox => {
            const widgetId = checkbox.dataset.widgetId;
            if (checkbox.checked) {
                checkedWidgets.push(widgetId);
            } else {
                uncheckedWidgets.push(widgetId);
            }
        });
        
        // Update hidden widgets list based on checkboxes
        // Set hiddenWidgets to exactly match uncheckedWidgets
        dragDrop.hiddenWidgets = [...uncheckedWidgets]; // Create a copy
        dragDrop.saveHiddenWidgets();
        
        // Debug: Log save state for pendingTasks
        if (checkedWidgets.includes('pendingTasks') || uncheckedWidgets.includes('pendingTasks')) {
            console.log('Saving Pending Tasks state:', {
                checked: checkedWidgets.includes('pendingTasks'),
                unchecked: uncheckedWidgets.includes('pendingTasks'),
                hiddenWidgets: dragDrop.hiddenWidgets,
                pendingTasksInHidden: dragDrop.hiddenWidgets.includes('pendingTasks'),
                savedToLocalStorage: localStorage.getItem('dashboardHiddenWidgets')
            });
        }
        
        // Apply visibility changes directly (don't call showWidget/hideWidget to avoid double-saving)
        checkedWidgets.forEach(widgetId => {
            const containerId = widgetId + 'Widget';
            const container = document.getElementById(containerId);
            if (container) {
                container.style.display = '';
                // Initialize if needed
                if (container.children.length === 0 && dragDrop.initializeWidgetIfNeeded) {
                    dragDrop.initializeWidgetIfNeeded(widgetId, container);
                }
            }
        });
        
        uncheckedWidgets.forEach(widgetId => {
            const containerId = widgetId + 'Widget';
            const container = document.getElementById(containerId);
            if (container) {
                container.style.display = 'none';
            }
        });
        
        // Restore widget order after showing/hiding
        dragDrop.restoreWidgetOrder();
        
        // Initialize any newly shown widgets that haven't been initialized yet
        checkedWidgets.forEach(widgetId => {
            const containerId = widgetId + 'Widget';
            const container = document.getElementById(containerId);
            if (container && container.children.length === 0) {
                // Widget is shown but not initialized, trigger initialization
                if (dragDrop.initializeWidgetIfNeeded) {
                    dragDrop.initializeWidgetIfNeeded(widgetId, container);
                }
            }
        });
        
        this.close();
    }
}

// Initialize modal when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        window.ChooseWidgetsModal = new ChooseWidgetsModal();
    });
} else {
    window.ChooseWidgetsModal = new ChooseWidgetsModal();
}

