/**
 * Dashboard Drag and Drop — pointer events (mouse + touch), order/visibility synced to server when authenticated.
 */

class DashboardDragDrop {
    constructor() {
        this.draggedContainer = null;
        this.dragOverContainer = null;
        this.dragImage = null;
        this.widgetOrder = null;
        this.hiddenWidgets = [];
        this.dashboardId = null;
        this.isDragging = false;
        this.startX = 0;
        this.startY = 0;
        this.offsetX = 0;
        this.offsetY = 0;
        this._saveTimer = null;
        this.mouseMoveHandler = null;
        this.mouseUpHandler = null;
    }

    /**
     * Dashboard key for API: "main" or numeric id string
     */
    getDashboardKey() {
        if (window.dashboard && window.dashboard.currentDashboardId != null) {
            return String(window.dashboard.currentDashboardId);
        }
        return 'main';
    }

    localCacheKey() {
        return 'budg_dash_layout_' + this.getDashboardKey();
    }

    /**
     * Load order/hidden from server, then namespaced localStorage, then legacy keys (main only).
     */
    async loadPrefsFromServer() {
        const key = this.getDashboardKey();

        const apply = (order, hidden) => {
            this.widgetOrder = order && order.length ? order : null;
            this.hiddenWidgets = Array.isArray(hidden) ? hidden : [];
        };

        try {
            const r = await fetch('/api/dashboard/layout-prefs?key=' + encodeURIComponent(key), {
                credentials: 'include'
            });
            if (r.ok && r.status !== 204) {
                const data = await r.json();
                if (data && Array.isArray(data.widgetOrder) && Array.isArray(data.hiddenWidgets)) {
                    apply(data.widgetOrder, data.hiddenWidgets);
                    try {
                        localStorage.setItem(this.localCacheKey(), JSON.stringify({
                            widgetOrder: data.widgetOrder,
                            hiddenWidgets: data.hiddenWidgets
                        }));
                    } catch (e) { /* ignore */ }
                    return;
                }
            }
        } catch (e) {
            console.warn('[DragDrop] layout-prefs fetch failed:', e);
        }

        try {
            const cached = localStorage.getItem(this.localCacheKey());
            if (cached) {
                const data = JSON.parse(cached);
                if (data && Array.isArray(data.widgetOrder) && Array.isArray(data.hiddenWidgets)) {
                    apply(data.widgetOrder, data.hiddenWidgets);
                    return;
                }
            }
        } catch (e) { /* ignore */ }

        if (key === 'main') {
            const order = this.loadWidgetOrderLegacy();
            let hidden = null;
            try {
                const saved = localStorage.getItem('dashboardHiddenWidgets');
                if (saved !== null) {
                    hidden = JSON.parse(saved);
                }
            } catch (err) {
                hidden = null;
            }
            if (order && order.length) {
                apply(order, Array.isArray(hidden) ? hidden : ['pendingTasks']);
                this.scheduleSaveToServer();
                return;
            }
            if (hidden !== null && Array.isArray(hidden)) {
                apply(null, hidden);
                this.scheduleSaveToServer();
                return;
            }
            apply(null, ['pendingTasks']);
            return;
        }

        apply(null, []);
    }

    loadWidgetOrderLegacy() {
        try {
            const saved = localStorage.getItem('dashboardWidgetOrder');
            return saved ? JSON.parse(saved) : null;
        } catch (e) {
            return null;
        }
    }

    scheduleSaveToServer() {
        if (this._saveTimer) {
            clearTimeout(this._saveTimer);
        }
        this._saveTimer = setTimeout(() => this.persistToServer(), 500);
    }

    async persistToServer() {
        this._saveTimer = null;
        const key = this.getDashboardKey();
        const grid = document.querySelector('.dashboard-grid');
        let order = this.widgetOrder;
        if (grid) {
            order = Array.from(grid.children)
                .filter(child => child.style.display !== 'none')
                .map((child) => {
                    const widgetId = this.getWidgetId(child);
                    return widgetId || child.id || null;
                })
                .filter(id => id !== null);
        }
        const body = {
            widgetOrder: order || [],
            hiddenWidgets: Array.isArray(this.hiddenWidgets) ? this.hiddenWidgets : []
        };
        try {
            localStorage.setItem(this.localCacheKey(), JSON.stringify(body));
        } catch (e) { /* ignore */ }

        try {
            const r = await fetch('/api/dashboard/layout-prefs?key=' + encodeURIComponent(key), {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(body)
            });
            if (!r.ok) {
                console.warn('[DragDrop] layout-prefs save failed:', r.status);
            }
        } catch (e) {
            console.warn('[DragDrop] layout-prefs save error:', e);
        }
    }

    init() {
        this.applyHiddenWidgets();
    }

    setupDragAndDrop() {
        const grid = document.querySelector('.dashboard-grid');
        if (!grid) {
            console.warn('[DragDrop] Dashboard grid not found');
            return;
        }

        const containers = grid.querySelectorAll('[id$="Widget"]');
        if (containers.length === 0) {
            console.warn('[DragDrop] No widget containers found');
            return;
        }

        containers.forEach(container => {
            if (container.dataset.dragInitialized === 'true') {
                return;
            }

            container.dataset.dragInitialized = 'true';
            const containerRef = container;

            const widgetHeader = containerRef.querySelector('.dashboard-widget-header');
            if (!widgetHeader) {
                return;
            }

            widgetHeader.style.cursor = 'grab';
            widgetHeader.style.userSelect = 'none';
            widgetHeader.style.touchAction = 'none';

            const pointerDownHandler = (e) => {
                if (e.pointerType === 'mouse' && e.button !== 0) {
                    return;
                }
                if (!e.target.closest('.dashboard-widget-header')) {
                    return;
                }
                if (e.target.closest('.widget-menu-button') ||
                    e.target.closest('.widget-menu-dropdown') ||
                    e.target.closest('button') ||
                    e.target.closest('a')) {
                    return;
                }

                e.preventDefault();
                e.stopPropagation();

                this.isDragging = true;
                this.draggedContainer = containerRef;

                const rect = containerRef.getBoundingClientRect();
                this.offsetX = e.clientX - rect.left;
                this.offsetY = e.clientY - rect.top;
                this.startX = e.clientX;
                this.startY = e.clientY;

                const dragImage = containerRef.cloneNode(true);
                dragImage.id = 'drag-image-' + Date.now();
                dragImage.style.position = 'fixed';
                dragImage.style.top = (e.clientY - this.offsetY) + 'px';
                dragImage.style.left = (e.clientX - this.offsetX) + 'px';
                dragImage.style.width = containerRef.offsetWidth + 'px';
                dragImage.style.opacity = '0.9';
                dragImage.style.transform = 'rotate(2deg)';
                dragImage.style.boxShadow = '0 8px 24px rgba(0, 0, 0, 0.3)';
                dragImage.style.zIndex = '10000';
                dragImage.style.pointerEvents = 'none';
                dragImage.style.cursor = 'grabbing';
                document.body.appendChild(dragImage);
                this.dragImage = dragImage;

                containerRef.style.opacity = '0.3';
                containerRef.classList.add('dragging');

                document.addEventListener('pointermove', this.pointerMoveHandler);
                document.addEventListener('pointerup', this.pointerUpHandler);
                document.addEventListener('pointercancel', this.pointerUpHandler);
            };

            widgetHeader.addEventListener('pointerdown', pointerDownHandler);
        });

        this.pointerMoveHandler = (e) => {
            if (!this.isDragging || !this.draggedContainer) return;

            e.preventDefault();

            if (this.dragImage) {
                this.dragImage.style.top = (e.clientY - this.offsetY) + 'px';
                this.dragImage.style.left = (e.clientX - this.offsetX) + 'px';
            }

            this.updateDragOverContainer(e.clientX, e.clientY);
        };

        this.pointerUpHandler = (e) => {
            if (!this.isDragging) return;

            e.preventDefault();
            e.stopPropagation();

            const wasDragged = this.draggedContainer;
            const wasOver = this.dragOverContainer;

            this.isDragging = false;

            if (this.dragImage) {
                this.dragImage.remove();
                this.dragImage = null;
            }

            if (this.draggedContainer) {
                this.draggedContainer.style.opacity = '';
                this.draggedContainer.classList.remove('dragging');
            }

            document.removeEventListener('pointermove', this.pointerMoveHandler);
            document.removeEventListener('pointerup', this.pointerUpHandler);
            document.removeEventListener('pointercancel', this.pointerUpHandler);

            if (wasDragged && wasOver && wasDragged !== wasOver) {
                const g = document.querySelector('.dashboard-grid');
                if (g) {
                    const allContainers = Array.from(g.children);
                    const draggedIndex = allContainers.indexOf(wasDragged);
                    const dropIndex = allContainers.indexOf(wasOver);

                    if (draggedIndex !== -1 && dropIndex !== -1 && draggedIndex !== dropIndex) {
                        if (draggedIndex < dropIndex) {
                            g.insertBefore(wasDragged, wasOver.nextSibling);
                        } else {
                            g.insertBefore(wasDragged, wasOver);
                        }
                        this.saveWidgetOrder();
                    }
                }
            }

            document.querySelectorAll('[id$="Widget"]').forEach(c => {
                c.classList.remove('drag-over');
            });

            this.draggedContainer = null;
            this.dragOverContainer = null;
        };
    }

    updateDragOverContainer(mouseX, mouseY) {
        if (!this.draggedContainer) return;

        const grid = document.querySelector('.dashboard-grid');
        if (!grid) return;

        const containers = Array.from(grid.querySelectorAll('[id$="Widget"]'));

        for (let c of containers) {
            if (c === this.draggedContainer) continue;
            if (c.style.display === 'none') continue;

            const rect = c.getBoundingClientRect();

            if (rect.width === 0 && rect.height === 0) continue;

            const isOver = mouseX >= rect.left && mouseX <= rect.right &&
                mouseY >= rect.top && mouseY <= rect.bottom;

            if (isOver) {
                if (this.dragOverContainer !== c) {
                    document.querySelectorAll('[id$="Widget"]').forEach(container => {
                        container.classList.remove('drag-over');
                    });

                    c.classList.add('drag-over');
                    this.dragOverContainer = c;
                }
                return;
            }
        }

        if (this.dragOverContainer) {
            document.querySelectorAll('[id$="Widget"]').forEach(c => {
                c.classList.remove('drag-over');
            });
            this.dragOverContainer = null;
        }
    }

    getWidgetId(element) {
        if (element.classList.contains('dashboard-widget')) {
            return element.dataset.widgetId || null;
        }

        const widget = element.querySelector('.dashboard-widget');
        if (widget && widget.dataset.widgetId) {
            return widget.dataset.widgetId;
        }

        const containerId = element.id || element.closest('[id$="Widget"]')?.id;
        if (containerId) {
            return containerId.replace('Widget', '').replace('widget', '');
        }

        return null;
    }

    saveWidgetOrder() {
        const grid = document.querySelector('.dashboard-grid');
        if (!grid) return;

        const order = Array.from(grid.children)
            .filter(child => child.style.display !== 'none')
            .map((child) => {
                const widgetId = this.getWidgetId(child);
                return widgetId || child.id || null;
            })
            .filter(id => id !== null);

        this.widgetOrder = order;
        try {
            localStorage.setItem(this.localCacheKey(), JSON.stringify({
                widgetOrder: order,
                hiddenWidgets: this.hiddenWidgets
            }));
        } catch (e) { /* ignore */ }
        this.scheduleSaveToServer();
    }

    loadWidgetOrder() {
        return this.widgetOrder;
    }

    restoreWidgetOrder() {
        if (!this.widgetOrder || this.widgetOrder.length === 0) return;

        const grid = document.querySelector('.dashboard-grid');
        if (!grid) return;

        const containers = Array.from(grid.children);
        const containerMap = new Map();
        containers.forEach(container => {
            const id = this.getWidgetId(container) || container.id;
            if (id) {
                containerMap.set(id, container);
            }
        });

        this.widgetOrder.forEach(id => {
            const el = containerMap.get(id);
            if (el && el.style.display !== 'none') {
                grid.appendChild(el);
            }
        });
    }

    loadHiddenWidgets() {
        return Array.isArray(this.hiddenWidgets) ? this.hiddenWidgets : [];
    }

    saveHiddenWidgets() {
        try {
            localStorage.setItem(this.localCacheKey(), JSON.stringify({
                widgetOrder: this.widgetOrder || [],
                hiddenWidgets: this.hiddenWidgets
            }));
        } catch (e) { /* ignore */ }
        this.scheduleSaveToServer();
    }

    hideWidget(widgetId) {
        if (!this.hiddenWidgets.includes(widgetId)) {
            this.hiddenWidgets.push(widgetId);
            this.saveHiddenWidgets();
        }

        const container = document.getElementById(`${widgetId}Widget`);
        if (container) {
            container.style.display = 'none';
        }
    }

    showWidget(widgetId) {
        const wasHidden = this.hiddenWidgets.includes(widgetId);
        if (wasHidden) {
            this.hiddenWidgets = this.hiddenWidgets.filter(id => id !== widgetId);
            this.saveHiddenWidgets();
        }

        const container = document.getElementById(`${widgetId}Widget`);
        if (container) {
            container.style.display = '';

            if (container.children.length === 0) {
                this.initializeWidgetIfNeeded(widgetId, container);
            }
        }
    }

    initializeWidgetIfNeeded(widgetId, container) {
        const widgetMap = {
            'pendingTasks': { class: 'PendingTasksWidget', needsUserId: true }
        };

        const widgetInfo = widgetMap[widgetId];
        if (!widgetInfo) return;

        const WidgetClass = window[widgetInfo.class];
        if (!WidgetClass) {
            console.warn(`Widget class ${widgetInfo.class} not found for ${widgetId}`);
            return;
        }

        let userId = null;
        if (window.dashboard && window.dashboard.currentUser) {
            userId = window.dashboard.currentUser.id;
        }

        const widget = new WidgetClass(`${widgetId}Widget`);
        if (widgetInfo.needsUserId) {
            widget.init(userId).catch(err => {
                console.error(`Error initializing ${widgetId} widget:`, err);
            });
        } else {
            widget.init().catch(err => {
                console.error(`Error initializing ${widgetId} widget:`, err);
            });
        }

        if (window.dashboard) {
            window.dashboard.widgets[widgetId] = widget;
        }
    }

    isWidgetHidden(widgetId) {
        return this.hiddenWidgets.includes(widgetId);
    }

    applyHiddenWidgets() {
        this.hiddenWidgets.forEach(widgetId => {
            const container = document.getElementById(`${widgetId}Widget`);
            if (container) {
                container.style.setProperty('display', 'none', 'important');
            }
        });

        const allWidgetContainers = document.querySelectorAll('[id$="Widget"]');
        allWidgetContainers.forEach(container => {
            const widgetId = container.id.replace('Widget', '');
            if (!this.hiddenWidgets.includes(widgetId)) {
                if (container.style.display === 'none' || container.style.getPropertyValue('display') === 'none') {
                    container.style.removeProperty('display');
                }
            }
        });
    }

    reset() {
        const key = this.getDashboardKey();
        localStorage.removeItem(this.localCacheKey());
        localStorage.removeItem('dashboardWidgetOrder');
        localStorage.removeItem('dashboardHiddenWidgets');
        this.widgetOrder = null;
        this.hiddenWidgets = key === 'main' ? ['pendingTasks'] : [];

        fetch('/api/dashboard/layout-prefs?key=' + encodeURIComponent(key), {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ widgetOrder: [], hiddenWidgets: this.hiddenWidgets })
        }).catch(() => { /* ignore */ });

        document.querySelectorAll('[id$="Widget"]').forEach(container => {
            container.style.display = '';
        });

        window.location.reload();
    }
}

window.DashboardDragDrop = DashboardDragDrop;
