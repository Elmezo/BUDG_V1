/**
 * Lock UI Component - Reusable for all facets
 * Provides "My Locked Items" panel and lock management UI
 */

class LockUI {
    constructor(facetType, objectId, containerSelector = '#editActionsBar') {
        this.facetType = facetType;
        this.objectId = objectId;
        this.containerSelector = containerSelector;
        this.lockManager = null;
        this.panel = null;
        this.lockButton = null;
        this.isInitialized = false;
    }

    /**
     * Initialize the Lock UI component
     */
    async initialize(lockManager) {
        if (this.isInitialized) return;
        
        this.lockManager = lockManager;
        
        // Create and inject lock button
        this.createLockButton();
        
        // Create panel structure
        this.createPanel();
        
        // Setup event listeners
        this.setupEventListeners();
        
        this.isInitialized = true;
        
        // Update button state based on lock status
        await this.updateLockButtonState();
    }

    /**
     * Create the lock indicator button
     */
    createLockButton() {
        const container = document.querySelector(this.containerSelector);
        if (!container) {
            console.error('Lock button container not found:', this.containerSelector);
            return;
        }

        const button = document.createElement('button');
        button.type = 'button';
        button.id = 'lockIndicatorBtn';
        button.className = 'lock-indicator-btn';
        button.innerHTML = `
            <i class="fas fa-lock-open"></i>
            <span class="lock-text">My Locked Items</span>
        `;

        // Insert at the beginning of the container
        container.insertBefore(button, container.firstChild);
        
        this.lockButton = button;
    }

    /**
     * Create the My Locked Items panel
     */
    createPanel() {
        const panel = document.createElement('div');
        panel.id = 'myLockedItemsPanel';
        panel.className = 'my-locked-items-panel';
        panel.innerHTML = `
            <div class="panel-header">
                <span>MY LOCKED ITEMS</span>
                <button class="close-btn" id="closeLockPanelBtn">&times;</button>
            </div>
            <div class="panel-content">
                <div class="locked-items-loading">
                    <i class="fas fa-spinner fa-pulse"></i>
                    <p>Loading locked items...</p>
                </div>
            </div>
        `;

        // Position relative to lock button
        const container = document.querySelector(this.containerSelector);
        if (container) {
            // Make sure container has position relative
            if (getComputedStyle(container).position === 'static') {
                container.style.position = 'relative';
            }
            container.appendChild(panel);
        }

        this.panel = panel;
    }

    /**
     * Setup event listeners
     */
    setupEventListeners() {
        // Toggle panel on button click
        if (this.lockButton) {
            this.lockButton.addEventListener('click', (e) => {
                e.stopPropagation();
                this.togglePanel();
            });
        }

        // Close panel button
        const closeBtn = document.getElementById('closeLockPanelBtn');
        if (closeBtn) {
            closeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.hidePanel();
            });
        }

        // Close panel when clicking outside
        document.addEventListener('click', (e) => {
            if (this.panel && this.panel.classList.contains('show')) {
                if (!this.panel.contains(e.target) && !this.lockButton.contains(e.target)) {
                    this.hidePanel();
                }
            }
        });

        // Close panel on escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.panel && this.panel.classList.contains('show')) {
                this.hidePanel();
            }
        });
    }

    /**
     * Toggle panel visibility
     */
    async togglePanel() {
        if (this.panel.classList.contains('show')) {
            this.hidePanel();
        } else {
            await this.showPanel();
        }
    }

    /**
     * Show the panel and load locked items
     */
    async showPanel() {
        this.panel.classList.add('show');
        await this.loadLockedItems();
    }

    /**
     * Hide the panel
     */
    hidePanel() {
        this.panel.classList.remove('show');
    }

    /**
     * Load and display locked items
     */
    async loadLockedItems() {
        const contentDiv = this.panel.querySelector('.panel-content');
        
        // Show loading state
        contentDiv.innerHTML = `
            <div class="locked-items-loading">
                <i class="fas fa-spinner fa-pulse"></i>
                <p>Loading locked items...</p>
            </div>
        `;

        try {
            const response = await window.BUDG_API_SERVICE.getMyLocks();
            console.log('getMyLocks response:', response);
            const locks = response.locks || [];
            console.log('Locks array:', locks);

            if (locks.length === 0) {
                contentDiv.innerHTML = `
                    <div class="locked-items-empty">
                        <i class="fas fa-lock-open"></i>
                        <p>You don't have any locked items</p>
                    </div>
                `;
                return;
            }

            // Build table
            const tableHTML = `
                <table class="locked-items-table">
                    <thead>
                        <tr>
                            <th>ID</th>
                            <th>Name</th>
                            <th>Type</th>
                            <th>Locked Since</th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${locks.map(lock => this.renderLockRow(lock)).join('')}
                    </tbody>
                </table>
            `;

            contentDiv.innerHTML = tableHTML;

            // Attach event listeners to action buttons
            this.attachRowEventListeners();

        } catch (error) {
            console.error('Failed to load locked items:', error);
            contentDiv.innerHTML = `
                <div class="locked-items-empty">
                    <i class="fas fa-exclamation-triangle"></i>
                    <p>Failed to load locked items</p>
                </div>
            `;
        }
    }

    /**
     * Render a single lock row
     */
    renderLockRow(lock) {
        const isPermanent = lock.isPermanent || false;
        const lockType = isPermanent ? 'permanent' : 'temporary';
        const lockTypeLabel = isPermanent ? 'Permanent' : 'Temporary';
        const lockedSince = lock.createdDatetime ? this.formatTimestamp(lock.createdDatetime) : 'Recently';
        const isCurrentObject = lock.objectId === this.objectId && lock.moduleName === this.getFacetDisplayName(this.facetType);

        return `
            <tr class="${isPermanent ? 'permanent-lock' : ''}" data-lock-id="${lock.lockId}">
                <td>${lock.objectId}</td>
                <td>
                    <span class="object-name" data-object-id="${lock.objectId}" data-module="${lock.moduleName}">
                        ${this.escapeHtml(lock.objectName)}
                    </span>
                    ${isCurrentObject ? '<span style="color: #248567; font-size: 0.75rem;"> (Current)</span>' : ''}
                </td>
                <td>
                    <span class="lock-type-badge ${lockType}">${lockTypeLabel}</span>
                </td>
                <td>${lockedSince}</td>
                <td>
                    <div class="actions-cell">
                        <button class="action-btn pin-btn ${isPermanent ? 'permanent' : ''}" 
                                data-lock-id="${lock.lockId}" 
                                data-is-permanent="${isPermanent}"
                                title="${isPermanent ? 'Remove permanent lock' : 'Make lock permanent'}">
                            <i class="fas fa-thumbtack"></i>
                        </button>
                        <button class="action-btn unlock-btn" 
                                data-lock-id="${lock.lockId}"
                                data-module="${lock.moduleName}"
                                data-object-id="${lock.objectId}"
                                data-object-name="${this.escapeHtml(lock.objectName)}"
                                title="Release lock">
                            <i class="fas fa-lock-open"></i>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    }

    /**
     * Attach event listeners to row action buttons
     */
    attachRowEventListeners() {
        // Pin/Unpin buttons
        const pinButtons = this.panel.querySelectorAll('.pin-btn');
        pinButtons.forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const lockId = btn.dataset.lockId;
                const isPermanent = btn.dataset.isPermanent === 'true';
                await this.togglePermanentLock(lockId, isPermanent);
            });
        });

        // Unlock buttons
        const unlockButtons = this.panel.querySelectorAll('.unlock-btn');
        unlockButtons.forEach(btn => {
            btn.addEventListener('click', async (e) => {
                e.stopPropagation();
                const lockId = btn.dataset.lockId;
                const moduleName = btn.dataset.module;
                const objectId = btn.dataset.objectId;
                const objectName = btn.dataset.objectName;
                await this.releaseLockFromPanel(lockId, moduleName, objectId, objectName);
            });
        });

        // Object name links
        const objectLinks = this.panel.querySelectorAll('.object-name');
        objectLinks.forEach(link => {
            link.addEventListener('click', (e) => {
                e.stopPropagation();
                // Navigate to object view page
                const objectId = link.dataset.objectId;
                const moduleName = link.dataset.module;
                const facetPath = this.getModulePath(moduleName);
                if (facetPath) {
                    window.location.href = `/view/${facetPath}/${objectId}`;
                }
            });
        });
    }

    /**
     * Toggle permanent lock status
     */
    async togglePermanentLock(lockId, currentIsPermanent) {
        const newStatus = !currentIsPermanent;
        const action = newStatus ? 'make permanent' : 'remove permanent status from';
        
        const confirmed = await this.showConfirmation({
            title: newStatus ? 'Make Lock Permanent?' : 'Remove Permanent Lock?',
            icon: newStatus ? '🔒' : '🔓',
            message: newStatus 
                ? 'This will convert the temporary lock to a permanent lock. Only administrators will be able to edit or unlock this object.'
                : 'This will convert the permanent lock back to a temporary lock. The lock will be automatically released when you close the object.',
            confirmText: newStatus ? 'Yes, Make Permanent' : 'Yes, Remove Permanent',
            confirmClass: newStatus ? 'btn-danger' : 'btn-primary',
            warning: newStatus ? 'This action requires careful consideration.' : null
        });

        if (!confirmed) return;

        try {
            // Call API to toggle permanent status
            const response = await fetch(`/api/lock/${lockId}/toggle-permanent`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${localStorage.getItem('budg-auth-token')}`
                },
                body: JSON.stringify({ isPermanent: newStatus })
            });

            if (!response.ok) {
                throw new Error('Failed to update lock status');
            }

            const result = await response.json();
            
            // Show success message
            this.showSuccess(`Lock ${newStatus ? 'made permanent' : 'permanent status removed'} successfully`);
            
            // Reload locked items
            await this.loadLockedItems();
            
            // Update button state if this is the current object
            await this.updateLockButtonState();
            
        } catch (error) {
            console.error('Failed to toggle permanent lock:', error);
            this.showError('Failed to update lock status. Please try again.');
        }
    }

    /**
     * Release lock from panel (uses release by module/object so owners can release their own locks)
     */
    async releaseLockFromPanel(lockId, moduleName, objectId, objectName) {
        const confirmed = await this.showConfirmation({
            title: 'Release Lock?',
            icon: '🔓',
            message: `Are you sure you want to release the lock on "${objectName}"?`,
            confirmText: 'Yes, Release',
            confirmClass: 'btn-danger',
            warning: 'Unsaved changes will be lost if you release this lock.'
        });

        if (!confirmed) return;

        try {
            await window.BUDG_API_SERVICE.releaseLock(moduleName, objectId);
            
            // Check if this is the current object
            const currentLockStatus = await this.lockManager.checkLockStatus();
            if (currentLockStatus && currentLockStatus.locked && currentLockStatus.lockId == lockId) {
                // Redirect to view page
                window.location.href = `/view/${this.facetType}/${this.objectId}`;
                return;
            }
            
            // Reload locked items
            await this.loadLockedItems();
            
            this.showSuccess('Lock released successfully');
            
        } catch (error) {
            console.error('Failed to release lock:', error);
            this.showError('Failed to release lock. Please try again.');
        }
    }

    /**
     * Update lock button state based on current lock
     */
    async updateLockButtonState() {
        if (!this.lockButton || !this.lockManager) return;

        try {
            const lockStatus = await this.lockManager.checkLockStatus();
            const isPermanent = lockStatus?.isPermanent || false;

            if (isPermanent) {
                this.lockButton.className = 'lock-indicator-btn permanent';
                this.lockButton.querySelector('i').className = 'fas fa-lock';
            } else {
                this.lockButton.className = 'lock-indicator-btn';
                this.lockButton.querySelector('i').className = 'fas fa-lock-open';
            }
        } catch (error) {
            console.error('Failed to update lock button state:', error);
        }
    }

    /**
     * Show confirmation dialog
     */
    showConfirmation({ title, icon, message, confirmText, confirmClass, warning }) {
        return new Promise((resolve) => {
            const modal = document.createElement('div');
            modal.className = 'lock-confirmation-modal';
            modal.innerHTML = `
                <div class="modal-dialog">
                    <div class="modal-header">
                        <h3>${this.escapeHtml(title)}</h3>
                    </div>
                    <div class="modal-body">
                        <div class="modal-icon">${icon}</div>
                        <div class="modal-message">${this.escapeHtml(message)}</div>
                        ${warning ? `<div class="modal-warning">${this.escapeHtml(warning)}</div>` : ''}
                    </div>
                    <div class="modal-footer">
                        <button class="btn btn-secondary" data-action="cancel">Cancel</button>
                        <button class="btn ${confirmClass}" data-action="confirm">${this.escapeHtml(confirmText)}</button>
                    </div>
                </div>
            `;

            document.body.appendChild(modal);

            modal.addEventListener('click', (e) => {
                const action = e.target.closest('[data-action]')?.dataset.action;
                if (action === 'confirm') {
                    modal.remove();
                    resolve(true);
                } else if (action === 'cancel' || e.target === modal) {
                    modal.remove();
                    resolve(false);
                }
            });
        });
    }

    /**
     * Show success message
     */
    showSuccess(message) {
        this.showToast(message, 'success');
    }

    /**
     * Show error message
     */
    showError(message) {
        this.showToast(message, 'error');
    }

    /**
     * Show toast notification
     */
    showToast(message, type = 'info') {
        const toast = document.createElement('div');
        toast.className = `lock-toast lock-toast-${type}`;
        toast.style.cssText = `
            position: fixed;
            top: 100px;
            right: 20px;
            background: ${type === 'success' ? '#4caf50' : type === 'error' ? '#f44336' : '#2196f3'};
            color: white;
            padding: 1rem 1.5rem;
            border-radius: 4px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
            z-index: 10001;
            animation: slideInRight 0.3s ease;
        `;
        toast.textContent = message;

        document.body.appendChild(toast);

        setTimeout(() => {
            toast.style.animation = 'slideOutRight 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    /**
     * Helper: Format timestamp
     */
    formatTimestamp(datetime) {
        if (!datetime) return 'Recently';
        try {
            const date = new Date(datetime);
            const day = String(date.getDate()).padStart(2, '0');
            const month = date.toLocaleString('en-US', { month: 'short' });
            const year = date.getFullYear();
            const hours = String(date.getHours()).padStart(2, '0');
            const minutes = String(date.getMinutes()).padStart(2, '0');
            const seconds = String(date.getSeconds()).padStart(2, '0');
            return `${day}-${month}-${year} ${hours}:${minutes}:${seconds}`;
        } catch (e) {
            return 'Recently';
        }
    }

    /**
     * Helper: Get facet display name
     */
    getFacetDisplayName(facetType) {
        const mapping = {
            'dataset': 'Data Sets',
            'system': 'System',
            'interface': 'Interface',
            'glossary': 'Glossary',
            'business-area': 'Business Area',
            'capability': 'Capability',
            'client': 'Client',
            'committee': 'Committee',
            'legal-entity': 'Legal Entity',
            'org-unit': 'Org Unit',
            'people': 'People',
            'policy': 'Policy',
            'process': 'Process',
            'product': 'Product',
            'project': 'Project',
            'geography': 'Geography',
            'regulation': 'Regulation',
            'regulator': 'Regulator',
            'regulatory-theme': 'Regulatory Theme',
            'attribute': 'Attribute'
        };
        return mapping[facetType] || facetType;
    }

    /**
     * Helper: Get module path from name
     */
    getModulePath(moduleName) {
        const mapping = {
            'Data Sets': 'dataset',
            'System': 'system',
            'Interface': 'interface',
            'Glossary': 'glossary',
            'Business Area': 'business-area',
            'Capability': 'capability',
            'Client': 'client',
            'Committee': 'committee',
            'Legal Entity': 'legal-entity',
            'Org Unit': 'org-unit',
            'People': 'people',
            'Policy': 'policy',
            'Process': 'process',
            'Product': 'product',
            'Project': 'project',
            'Geography': 'geography',
            'Regulation': 'regulation',
            'Regulator': 'regulator',
            'Regulatory Theme': 'regulatory-theme',
            'Attribute': 'attribute'
        };
        return mapping[moduleName] || null;
    }

    /**
     * Helper: Escape HTML
     */
    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    /**
     * Cleanup
     */
    destroy() {
        if (this.lockButton) this.lockButton.remove();
        if (this.panel) this.panel.remove();
        this.isInitialized = false;
    }
}

// Make available globally
window.LockUI = LockUI;

