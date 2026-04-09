/**
 * Global Lock UI Component
 * Accessible from main header navigation
 * Shows all locked items across all facets for the current user
 */

class GlobalLockUI {
    constructor() {
        this.panel = null;
        this.lockButton = null;
        this.isInitialized = false;
        this.isAuthenticated = false;
    }

    /**
     * Initialize the Global Lock UI component
     */
    async initialize() {
        if (this.isInitialized) return;
        
        // Check if user is authenticated
        this.isAuthenticated = await this.checkAuthentication();
        
        if (!this.isAuthenticated) {
            this.hideLockToggleButton();
            return;
        }
        
        // Check if user is a guest - guests should never see lock button
        const isGuest = await this.checkIfGuest();
        if (isGuest) {
            this.hideLockToggleButton();
            return;
        }
        
        // Wait for header to be ready
        await this.waitForHeader();
        
        // Get lock button from header
        this.lockButton = document.getElementById('globalLockToggle');
        
        if (!this.lockButton) {
            // Header might still be loading, try again after a short delay
            setTimeout(() => {
                this.lockButton = document.getElementById('globalLockToggle');
                if (this.lockButton) {
                    // Hide button by default - will only show when there are locked items
                    this.lockButton.style.display = 'none';
                    this.createPanel();
                    this.setupEventListeners();
                    this.updateLockCount();
                    this.isInitialized = true;
                } else {
                    // Button still not found - header might not have the button
                    // This is not a critical error, just log it
                }
            }, 500);
            return;
        }
        
        
        // Show lock button on all pages (homepage, unison, create, view, edit, admin panel)
        // In admin panel and view pages, always show (static), otherwise will be controlled by updateLockCount
        const isAdminPanel = this.isAdminPanel();
        const isEdit = this.isEditPage();
        const isViewPage = !isEdit && !isAdminPanel;
        
        if (isAdminPanel || isViewPage) {
            this.lockButton.style.display = 'flex';
            this.lockButton.style.visibility = 'visible';
        }
        
        // Create panel structure
        this.createPanel();
        
        // Setup event listeners
        this.setupEventListeners();
        
        // Listen for page navigation to handle temporary locks
        this.setupNavigationListeners();
        
        this.isInitialized = true;
        
        // Update lock count
        await this.updateLockCount();
    }

    /**
     * Setup listeners for page navigation to handle temporary locks
     */
    setupNavigationListeners() {
        // Listen for beforeunload to detect page close/navigation
        window.addEventListener('beforeunload', () => {
            // This will be handled by the server-side lock cleanup
            // But we can also update the UI immediately
        });
        
        // Listen for popstate (back/forward navigation)
        window.addEventListener('popstate', () => {
            setTimeout(() => {
                this.updateLockCount();
            }, 100);
        });
        
        // Listen for hashchange (if using hash-based navigation)
        window.addEventListener('hashchange', () => {
            console.log('🔒 Hash change detected - updating lock display');
            setTimeout(() => {
                this.updateLockCount();
            }, 100);
        });
        
        // Monitor URL changes (for SPA navigation)
        let lastUrl = window.location.href;
        setInterval(() => {
            const currentUrl = window.location.href;
            if (currentUrl !== lastUrl) {
                lastUrl = currentUrl;
                setTimeout(() => {
                    this.updateLockCount();
                }, 100);
            }
        }, 500);
    }

    /**
     * Check if user is authenticated (real session, not anonymous guest JSON)
     */
    async checkAuthentication() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });
            if (!response.ok) {
                return false;
            }
            const me = await response.json().catch(() => ({}));
            const role = (me.role || '').toString().toLowerCase();
            return me.authenticated === true && !role.includes('guest');
        } catch (error) {
            return false;
        }
    }

    hideLockToggleButton() {
        const lockBtn = document.getElementById('globalLockToggle');
        if (lockBtn) {
            lockBtn.style.display = 'none';
            lockBtn.style.visibility = 'hidden';
            lockBtn.style.pointerEvents = 'none';
        }
    }
    
    /**
     * Check if user is a guest or has no real session
     */
    async checkIfGuest() {
        try {
            const response = await fetch('/api/me', {
                method: 'GET',
                credentials: 'include'
            });
            
            if (!response.ok) {
                return true;
            }
            
            const userData = await response.json();
            if (userData.authenticated !== true) {
                return true;
            }
            const role = (userData.role || '').toString().toLowerCase();
            return role.includes('guest');
        } catch (error) {
            return true;
        }
    }

    /**
     * Wait for header to be injected
     */
    waitForHeader() {
        return new Promise((resolve) => {
            // Check if button already exists
            if (document.getElementById('globalLockToggle')) {
                resolve();
                return;
            }
            
            // Listen for headerReady event
            const headerReadyHandler = () => {
                // After headerReady, poll for the button with a short delay
                // to ensure DOM is updated
                let attempts = 0;
                const maxAttempts = 10;
                const pollInterval = 100;
                
                const checkButton = () => {
                    attempts++;
                    if (document.getElementById('globalLockToggle')) {
                        resolve();
                    } else if (attempts < maxAttempts) {
                        setTimeout(checkButton, pollInterval);
                    } else {
                        // Button still not found after polling, resolve anyway
                        // (will be handled gracefully in initialize)
                        resolve();
                    }
                };
                
                // Start polling after a short delay to allow DOM to update
                setTimeout(checkButton, 50);
            };
            
            window.addEventListener('headerReady', headerReadyHandler, { once: true });
            
            // Also poll periodically in case headerReady event was missed
            let pollAttempts = 0;
            const maxPollAttempts = 50; // 5 seconds total (50 * 100ms)
            const pollCheck = setInterval(() => {
                pollAttempts++;
                if (document.getElementById('globalLockToggle')) {
                    clearInterval(pollCheck);
                    window.removeEventListener('headerReady', headerReadyHandler);
                    resolve();
                } else if (pollAttempts >= maxPollAttempts) {
                    clearInterval(pollCheck);
                    window.removeEventListener('headerReady', headerReadyHandler);
                    resolve();
                }
            }, 100);
        });
    }

    /**
     * Create the My Locked Items panel
     */
    createPanel() {
        const panel = document.createElement('div');
        panel.id = 'globalLockedItemsPanel';
        panel.className = 'global-locked-items-panel';
        panel.innerHTML = `
            <div class="panel-header">
                <h3><i class="fas fa-lock"></i> MY LOCKED ITEMS</h3>
                <div class="panel-header-actions">
                    <button class="release-all-btn" id="releaseAllLocksBtn" title="Release All Locks">
                        <i class="fas fa-unlock-alt"></i> Release All
                    </button>
                    <button class="close-btn" id="closeGlobalLockPanelBtn">
                        <i class="fas fa-times"></i>
                    </button>
                </div>
            </div>
            <div class="panel-content">
                <div class="locked-items-loading">
                    <i class="fas fa-spinner fa-pulse"></i>
                    <p>Loading locked items...</p>
                </div>
            </div>
        `;

        document.body.appendChild(panel);
        this.panel = panel;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Toggle panel on lock button click - use event delegation to handle button recreation
        if (this.lockButton) {
            // Remove any existing listeners by cloning (prevents duplicates)
            const clickHandler = (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.togglePanel();
            };
            
            // Remove old listener if it exists
            if (this._clickHandler) {
                this.lockButton.removeEventListener('click', this._clickHandler);
            }
            
            this._clickHandler = clickHandler;
            this.lockButton.addEventListener('click', this._clickHandler);
        } else {
            console.error('Lock button not found when setting up event listeners');
        }

        // Close button
        const closeBtn = this.panel.querySelector('#closeGlobalLockPanelBtn');
        if (closeBtn) {
            closeBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.closePanel();
            });
        }

        // Release All button
        const releaseAllBtn = this.panel.querySelector('#releaseAllLocksBtn');
        if (releaseAllBtn) {
            releaseAllBtn.addEventListener('click', async (e) => {
                e.preventDefault();
                e.stopPropagation();
                await this.releaseAllLocks();
            });
        }

        // Close on outside click
        if (!this._outsideClickHandler) {
            this._outsideClickHandler = (e) => {
                if (this.panel && 
                    this.panel.classList.contains('open') && 
                    !this.panel.contains(e.target) && 
                    this.lockButton &&
                    !this.lockButton.contains(e.target)) {
                    this.closePanel();
                }
            };
            document.addEventListener('click', this._outsideClickHandler);
        }

        // Close on escape key
        if (!this._escapeKeyHandler) {
            this._escapeKeyHandler = (e) => {
                if (e.key === 'Escape' && this.panel && this.panel.classList.contains('open')) {
                    this.closePanel();
                }
            };
            document.addEventListener('keydown', this._escapeKeyHandler);
        }
    }

    /**
     * Toggle panel visibility
     */
    async togglePanel() {
        if (!this.panel) {
            console.error('Panel not initialized');
            return;
        }
        
        if (this.panel.classList.contains('open')) {
            this.closePanel();
        } else {
            await this.openPanel();
        }
    }

    /**
     * Open the panel and load locks
     */
    async openPanel() {
        if (!this.panel) {
            console.error('Panel not initialized');
            return;
        }

        if (window.notificationPanel && typeof window.notificationPanel.closePanel === 'function') {
            window.notificationPanel.closePanel();
        }
        if (window.globalSegmentsCubePanel && typeof window.globalSegmentsCubePanel.closePanel === 'function') {
            window.globalSegmentsCubePanel.closePanel();
        }
        const profileDropdown = document.getElementById('profileDropdown');
        if (profileDropdown) profileDropdown.classList.remove('show');
        const myItemsDd = document.getElementById('myItemsDropdown');
        if (myItemsDd) { myItemsDd.classList.remove('show'); myItemsDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }
        const createDd = document.getElementById('createDropdown');
        if (createDd) { createDd.classList.remove('show'); createDd.querySelectorAll('.dropdown-item.has-submenu.active').forEach(i => i.classList.remove('active')); }

        this.panel.classList.add('open');
        await this.loadLockedItems();
    }

    /**
     * Close the panel
     */
    closePanel() {
        if (!this.panel) {
            return;
        }
        console.log('Removing open class from panel');
        this.panel.classList.remove('open');
    }

    /**
     * Update the lock count badge
     */
    /**
     * Check if current page is an edit page
     */
    isEditPage() {
        const url = window.location.href.toLowerCase();
        const pathname = window.location.pathname.toLowerCase();
        const searchParams = new URLSearchParams(window.location.search);
        
        // Check for edit in URL parameters
        if (searchParams.get('tab') === 'edit') {
            return true;
        }
        
        // Check for edit in pathname (e.g., system-edit.html, people-edit.html)
        if (pathname.includes('-edit.html') || pathname.includes('/edit')) {
            return true;
        }
        
        // Check if URL contains 'edit'
        if (url.includes('tab=edit') || url.includes('?edit') || url.includes('&edit')) {
            return true;
        }
        
        return false;
    }

    /**
     * Check if current page is admin panel
     */
    isAdminPanel() {
        const pathname = window.location.pathname.toLowerCase();
        return pathname.includes('admin-panel') || document.body.classList.contains('admin-panel-page');
    }

    /**
     * Get current page entity info (entityType and entityId) from URL
     */
    getCurrentPageEntityInfo() {
        const path = window.location.pathname;
        const params = new URLSearchParams(window.location.search);
        let entityType = null;
        let entityId = null;
        
        // Try to extract from path pattern: /view/{entityType}/{id}
        const pathMatch = path.match(/\/view\/([^\/]+)\/(\d+)/);
        if (pathMatch) {
            entityType = pathMatch[1];
            entityId = parseInt(pathMatch[2], 10);
        } else {
            // Try to extract from path pattern: /view/{entityType}/{entityType}.html?id={id}
            const htmlMatch = path.match(/\/view\/([^\/]+)\/[^\/]+\.html/);
            if (htmlMatch) {
                entityType = htmlMatch[1];
                // Get ID from query parameter
                const queryId = params.get('id');
                if (queryId) {
                    entityId = parseInt(queryId, 10);
                }
            } else {
                // Fallback: try to get entity type from path and ID from query
                const parts = path.split('/').filter(Boolean);
                const viewIdx = parts.indexOf('view');
                if (viewIdx >= 0 && parts.length > viewIdx + 1) {
                    entityType = parts[viewIdx + 1];
                    // Remove .html if present
                    entityType = entityType.replace(/\.html$/, '');
                }
                const queryId = params.get('id');
                if (queryId) {
                    entityId = parseInt(queryId, 10);
                } else {
                    // Last resort: try to parse ID from last path segment
                    if (parts.length > 0) {
                        const maybeId = parseInt(parts[parts.length - 1], 10);
                        if (!Number.isNaN(maybeId)) {
                            entityId = maybeId;
                        }
                    }
                }
            }
        }
        
        // Validate results
        if (Number.isNaN(entityId)) {
            entityId = null;
        }
        
        return { entityType, entityId };
    }

    /**
     * Check if the current page entity matches the unlocked entity
     */
    isCurrentPageEntity(moduleName, objectId) {
        const currentEntity = this.getCurrentPageEntityInfo();
        if (!currentEntity.entityType || !currentEntity.entityId) {
            return false;
        }
        
        // Convert moduleName to facet path for comparison
        const facetPath = this.getFacetPath(moduleName);
        
        // Compare entity type (case-insensitive) and object ID
        const currentEntityTypeLower = currentEntity.entityType.toLowerCase();
        const facetPathLower = facetPath.toLowerCase();
        
        return currentEntityTypeLower === facetPathLower && 
               currentEntity.entityId === parseInt(objectId, 10);
    }

    async updateLockCount() {
        if (!this.isAuthenticated) {
            this.hideLockToggleButton();
            return;
        }
        try {
            const response = await window.BUDG_API_SERVICE.getMyLocks();
            const locks = response.locks || [];
            
            // Separate temporary and permanent locks
            const temporaryLocks = locks.filter(lock => !lock.isPermanent);
            const permanentLocks = locks.filter(lock => lock.isPermanent);
            
            const isEdit = this.isEditPage();
            
            // Log all locks for debugging
            if (false) console.log('🔒 All locks:', locks.map(l => ({
                id: l.lockId,
                name: l.objectName,
                type: l.isPermanent ? 'PERMANENT' : 'TEMPORARY'
            })));
            
            // Lock icon visibility rules:
            // - Edit pages: show if there are ANY locks (temporary or permanent)
            // - View pages: ALWAYS show (static) - show all locks in panel
            // - Admin panel: ALWAYS show (static)
            
            let locksToShow;
            if (isEdit) {
                // Edit page: show all locks
                locksToShow = locks;
            } else {
                // View page: show all locks (but icon always visible)
                locksToShow = locks;
            }
            
            const count = locksToShow.length;
            const isAdminPanel = this.isAdminPanel();
            const isViewPage = !isEdit && !isAdminPanel;
            
            const badge = document.getElementById('globalLockBadge');
            if (badge) {
                if (count > 0) {
                    badge.textContent = count;
                    badge.style.display = 'flex';
                } else {
                    badge.textContent = '';
                    badge.style.display = 'none';
                }
            }
            
            if (this.lockButton) {
                // Always show lock icon in view pages and admin panel (static)
                // Show in edit pages only if there are locks
                if (isAdminPanel || isViewPage || count > 0) {
                    this.lockButton.style.display = 'flex';
                    this.lockButton.style.visibility = 'visible';
                    this.lockButton.style.pointerEvents = 'auto';
                } else {
                    // Force hide the lock button only on edit pages with no locks
                    this.lockButton.style.display = 'none';
                    this.lockButton.style.visibility = 'hidden';
                    this.lockButton.style.pointerEvents = 'none';
                    console.log(`🔒 Lock button HIDDEN: no locks on edit page`);
                }
            }
            
            // Also update the global lock button in case it's a different reference
            const globalLockBtn = document.getElementById('globalLockToggle');
            if (globalLockBtn) {
                if (isAdminPanel || isViewPage || count > 0) {
                    // Always show in view pages and admin panel, or if there are locks
                    globalLockBtn.style.display = 'flex';
                    globalLockBtn.style.visibility = 'visible';
                } else if (count === 0) {
                    // Hide only on edit pages with no locks
                    globalLockBtn.style.display = 'none';
                    globalLockBtn.style.visibility = 'hidden';
                }
            }
        } catch (error) {
            console.error('Failed to update lock count:', error);
            // On error, keep lock icon visible in view pages and admin panel (static)
            const isEdit = this.isEditPage();
            const isAdminPanel = this.isAdminPanel();
            const isViewPage = !isEdit && !isAdminPanel;
            
            if (isAdminPanel || isViewPage) {
                // In admin panel and view pages, always show the lock icon even on error
                if (this.lockButton) {
                    this.lockButton.style.display = 'flex';
                    this.lockButton.style.visibility = 'visible';
                }
                const globalLockBtn = document.getElementById('globalLockToggle');
                if (globalLockBtn) {
                    globalLockBtn.style.display = 'flex';
                    globalLockBtn.style.visibility = 'visible';
                }
            } else {
                // On edit pages, hide on error if no locks
                if (this.lockButton) {
                    this.lockButton.style.display = 'none';
                    this.lockButton.style.visibility = 'hidden';
                }
                const globalLockBtn = document.getElementById('globalLockToggle');
                if (globalLockBtn) {
                    globalLockBtn.style.display = 'none';
                    globalLockBtn.style.visibility = 'hidden';
                }
            }
        }
    }

    /**
     * Load and display locked items
     */
    async loadLockedItems() {
        const content = this.panel.querySelector('.panel-content');
        
        try {
            content.innerHTML = `
                <div class="locked-items-loading">
                    <i class="fas fa-spinner fa-pulse"></i>
                    <p>Loading locked items...</p>
                </div>
            `;
            
            const response = await window.BUDG_API_SERVICE.getMyLocks();
            const locks = response.locks || [];
            
            if (locks.length === 0) {
                content.innerHTML = `
                    <div class="no-locks-message">
                        <i class="fas fa-unlock-alt"></i>
                        <p>You don't have any locked items</p>
                    </div>
                `;
                return;
            }
            
            // Render locks table
            content.innerHTML = `
                <div class="locks-table-wrapper">
                    <table class="global-locks-table">
                        <thead>
                            <tr>
                                <th>Facet</th>
                                <th>Name</th>
                                <th>Type</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody id="globalLocksTableBody">
                            ${locks.map(lock => this.renderLockRow(lock)).join('')}
                        </tbody>
                    </table>
                </div>
            `;
            
            // Attach event listeners to action buttons
            this.attachActionListeners();
            
        } catch (error) {
            console.error('Failed to load locked items:', error);
            content.innerHTML = `
                <div class="error-message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>Failed to load locked items</p>
                    <button class="btn-retry" onclick="window.globalLockUI.loadLockedItems()">
                        <i class="fas fa-redo"></i> Retry
                    </button>
                </div>
            `;
        }
    }

    /**
     * Render a single lock row
     */
    renderLockRow(lock) {
        const facetName = this.getFacetDisplayName(lock.moduleName);
        const objectName = this.escapeHtml(lock.objectName || 'Unknown');
        const lockType = lock.isPermanent ? 'Permanent' : 'Temporary';
        const lockTypeClass = lock.isPermanent ? 'permanent' : 'temporary';
        const facetPath = this.getFacetPath(lock.moduleName);
        
        return `
            <tr data-lock-id="${lock.lockId}">
                <td>
                    <span class="facet-badge">${facetName}</span>
                </td>
                <td>
                    <a href="/view/${facetPath}/${lock.objectId}" class="lock-object-link" target="_blank">
                        ${objectName}
                    </a>
                </td>
                <td>
                    <span class="lock-type-badge ${lockTypeClass}">${lockType}</span>
                </td>
                <td class="actions-cell">
                    ${!lock.isPermanent ? `
                        <button class="action-btn make-permanent" data-lock-id="${lock.lockId}" title="Make Permanent">
                            <i class="fas fa-thumbtack"></i>
                        </button>
                    ` : `
                        <button class="action-btn make-temporary" data-lock-id="${lock.lockId}" title="Make Temporary">
                            <i class="fas fa-thumbtack unpinned"></i>
                        </button>
                    `}
                    <button class="action-btn unlock" data-lock-id="${lock.lockId}" data-module="${lock.moduleName}" data-object-id="${lock.objectId}" title="Unlock">
                        <i class="fas fa-unlock"></i>
                    </button>
                    <button class="action-btn goto" data-module="${lock.moduleName}" data-object-id="${lock.objectId}" title="Go to Edit">
                        <i class="fas fa-arrow-right"></i>
                    </button>
                </td>
            </tr>
        `;
    }

    /**
     * Attach event listeners to action buttons
     */
    attachActionListeners() {
        const tbody = document.getElementById('globalLocksTableBody');
        if (!tbody) return;
        
        // Make permanent/temporary
        tbody.querySelectorAll('.make-permanent, .make-temporary').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const lockId = e.currentTarget.dataset.lockId;
                const isPermanent = e.currentTarget.classList.contains('make-permanent');
                await this.togglePermanentLock(lockId, isPermanent);
            });
        });
        
        // Unlock
        tbody.querySelectorAll('.unlock').forEach(btn => {
            btn.addEventListener('click', async (e) => {
                const lockId = e.currentTarget.dataset.lockId;
                const moduleName = e.currentTarget.dataset.module;
                const objectId = e.currentTarget.dataset.objectId;
                await this.releaseLock(lockId, moduleName, objectId);
            });
        });
        
        // Go to edit page
        tbody.querySelectorAll('.goto').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const moduleName = e.currentTarget.dataset.module;
                const objectId = e.currentTarget.dataset.objectId;
                const facetPath = this.getFacetPath(moduleName);
                window.location.href = `/view/${facetPath}/${objectId}?tab=edit`;
            });
        });
    }

    /**
     * Toggle permanent lock status
     */
    async togglePermanentLock(lockId, makePermanent) {
        try {
            await window.BUDG_API_SERVICE.togglePermanentLock(lockId, makePermanent);
            this.showToast(makePermanent ? 'Lock made permanent' : 'Lock made temporary', 'success');
            await this.loadLockedItems();
            await this.updateLockCount();
        } catch (error) {
            console.error('Failed to toggle lock permanence:', error);
            this.showToast('Failed to update lock', 'error');
        }
    }

    /**
     * Release a lock
     */
    async releaseLock(lockId, moduleName, objectId) {
        if (!confirm('Are you sure you want to release this lock?')) {
            return;
        }
        
        try {
            await window.BUDG_API_SERVICE.releaseLock(moduleName, objectId);
            this.showToast('Lock released successfully', 'success');
            await this.loadLockedItems();
            await this.updateLockCount();
            
            // Check if we need to redirect to view page
            // Only redirect if:
            // 1. Current page is an edit page
            // 2. The unlocked entity matches the current page entity
            if (this.isEditPage() && this.isCurrentPageEntity(moduleName, objectId)) {
                const facetPath = this.getFacetPath(moduleName);
                const viewUrl = `/view/${facetPath}/${objectId}`;
                window.location.href = viewUrl;
                return; // Exit early to prevent further execution
            }
        } catch (error) {
            const errorMsg = error.body?.error || error.message || 'Unknown error';
            console.error('Failed to release lock:', error);
            this.showToast('Failed to release lock: ' + errorMsg, 'error');
        }
    }

    /**
     * Release all non-permanent locks at once
     */
    async releaseAllLocks() {
        if (!confirm('Are you sure you want to release ALL your non-permanent locks?')) {
            return;
        }
        
        const btn = this.panel.querySelector('#releaseAllLocksBtn');
        if (btn) {
            btn.disabled = true;
            btn.innerHTML = '<i class="fas fa-spinner fa-pulse"></i> Releasing...';
        }
        
        try {
            const result = await window.BUDG_API_SERVICE.releaseAllMyLocks();
            const count = result.releasedCount || 0;
            this.showToast(`${count} lock(s) released successfully`, 'success');
            await this.loadLockedItems();
            await this.updateLockCount();
            
            // If we are on an edit page, the current lock was just released — redirect to view
            if (this.isEditPage()) {
                const currentEntity = this.getCurrentPageEntityInfo();
                if (currentEntity.entityType && currentEntity.entityId) {
                    const facetPath = currentEntity.entityType;
                    const viewUrl = `/view/${facetPath}/${currentEntity.entityId}`;
                    window.location.href = viewUrl;
                    return;
                }
            }
        } catch (error) {
            console.error('Failed to release all locks:', error);
            this.showToast('Failed to release locks', 'error');
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-unlock-alt"></i> Release All';
            }
        }
    }

    /**
     * Show toast notification
     */
    showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `global-lock-toast ${type}`;
        toast.innerHTML = `
            <i class="fas fa-${type === 'success' ? 'check-circle' : 'exclamation-circle'}"></i>
            <span>${message}</span>
        `;
        
        document.body.appendChild(toast);
        
        setTimeout(() => toast.classList.add('show'), 10);
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    /**
     * Get display name for facet
     */
    getFacetDisplayName(moduleName) {
        const facetMap = {
            'Data Sets': 'Dataset',
            'dataset': 'Dataset',
            'System': 'System',
            'system': 'System',
            'Interface': 'Interface',
            'system-interface': 'Interface',
            'Glossary': 'Glossary',
            'glossary': 'Glossary',
            'Business Area': 'Business Area',
            'business-area': 'Business Area',
            'Policy': 'Policy',
            'policy': 'Policy',
            'Process': 'Process',
            'process': 'Process',
            'Project': 'Project',
            'project': 'Project',
            'Product': 'Product',
            'product': 'Product',
            'People': 'People',
            'people': 'People',
            'Legal Entity': 'Legal Entity',
            'legal-entity': 'Legal Entity',
            'Geography': 'Geography',
            'geography': 'Geography',
            'Regulation': 'Regulation',
            'regulation': 'Regulation',
            'Regulator': 'Regulator',
            'regulator': 'Regulator',
            'Regulatory Theme': 'Regulatory Theme',
            'regulatory-theme': 'Regulatory Theme'
        };
        return facetMap[moduleName] || moduleName;
    }

    /**
     * Get facet path for URLs
     */
    getFacetPath(moduleName) {
        const pathMap = {
            'Data Sets': 'dataset',
            'dataset': 'dataset',
            'System': 'system',
            'system': 'system',
            'Interface': 'system-interface',
            'system-interface': 'system-interface',
            'Glossary': 'glossary',
            'glossary': 'glossary',
            'Business Area': 'business-area',
            'business-area': 'business-area',
            'Policy': 'policy',
            'policy': 'policy',
            'Process': 'process',
            'process': 'process',
            'Project': 'project',
            'project': 'project',
            'Product': 'product',
            'product': 'product',
            'People': 'people',
            'people': 'people',
            'Legal Entity': 'LegalEntity',
            'legal-entity': 'LegalEntity',
            'Geography': 'geography',
            'geography': 'geography',
            'Regulation': 'regulation',
            'regulation': 'regulation',
            'Regulator': 'regulator',
            'regulator': 'regulator',
            'Regulatory Theme': 'regulatory-theme',
            'regulatory-theme': 'regulatory-theme'
        };
        return pathMap[moduleName] || 'dataset';
    }

    /**
     * Escape HTML to prevent XSS
     */
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Initialize global lock UI when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
    if (!window.globalLockUI) {
        window.globalLockUI = new GlobalLockUI();
    }
    await window.globalLockUI.initialize();
});

// Re-run initialization after header-based login so the lock icon
// appears on all pages once the user is authenticated.
window.addEventListener('auth:login', async () => {
    try {
        if (!window.globalLockUI) {
            window.globalLockUI = new GlobalLockUI();
        }
        await window.globalLockUI.initialize();
    } catch (e) {
        console.error('Failed to initialize GlobalLockUI after login event', e);
    }
});

