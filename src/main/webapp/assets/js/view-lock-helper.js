/**
 * Shared utility for checking and displaying lock status on view pages
 * Works across all facets
 */

window.ViewLockHelper = {
    /**
     * Check lock status and display indicators on view page
     * @param {string} facetType - The facet type (e.g., 'dataset', 'system', 'glossary')
     * @param {number} objectId - The object ID
     */
    async checkAndDisplayLockStatus(facetType, objectId) {
        try {
            const lockInfo = await window.BUDG_API_SERVICE.checkLock(facetType, objectId);
            const status = lockInfo?.status || 'no_lock';
            
            // Show lock indicator if locked by someone else (not the current user)
            if (status === 'locked_by_other' || status === 'permanently_locked') {
                const lockedBy = lockInfo.lockedByName || 'Unknown';
                const isPermanent = lockInfo.isPermanent || false;
                
                // Add red lock icon in upper left corner for permanent locks by other users
                // Show for permanent locks regardless of status (permanently_locked or locked_by_other)
                if (isPermanent) {
                    this.showTopLockIndicator(lockedBy);
                }
                
                // Add lock indicator next to edit button
                this.showEditButtonLockIndicator(lockedBy, isPermanent, lockInfo);
            }
        } catch (error) {
            console.error('Failed to check lock status:', error);
        }
    },

    /**
     * Show red lock icon in form-actions for permanent locks
     * @param {string} lockedBy - Name of user who locked the object
     */
    showTopLockIndicator(lockedBy) {
        let topLockIndicator = document.getElementById('topLockIndicator');
        if (!topLockIndicator) {
            const formActions = document.querySelector('.form-actions');
            if (formActions) {
                topLockIndicator = document.createElement('div');
                topLockIndicator.id = 'topLockIndicator';
                topLockIndicator.className = 'view-lock-indicator-top';
                topLockIndicator.style.cssText = 'background: #dc3545; color: white; padding: 8px 12px; border-radius: 4px; display: flex; align-items: center; gap: 8px;';
                topLockIndicator.innerHTML = `
                    <i class="fas fa-lock"></i>
                    <span>Locked by ${this.escapeHtml(lockedBy)}</span>
                `;
                // Insert at the beginning of form-actions
                formActions.insertBefore(topLockIndicator, formActions.firstChild);
            }
        }
    },

    /**
     * Show lock indicator next to edit button
     * @param {string} lockedBy - Name of user who locked the object
     * @param {boolean} isPermanent - Whether lock is permanent
     * @param {object} lockInfo - Full lock info object
     */
    showEditButtonLockIndicator(lockedBy, isPermanent, lockInfo) {
        const tabEditBtn = document.getElementById('tabEditBtn');
        if (tabEditBtn) {
            const lockText = isPermanent ? 'Permanently Locked' : 'Locked';
            const lockedSince = lockInfo.createdDatetime ? new Date(lockInfo.createdDatetime).toLocaleString() : '';
            
            let lockIndicator = document.getElementById('lockIndicator');
            if (!lockIndicator) {
                lockIndicator = document.createElement('div');
                lockIndicator.id = 'lockIndicator';
                lockIndicator.className = isPermanent ? 'view-lock-indicator' : 'view-lock-indicator temporary';
                const lockTypeText = isPermanent ? 'Permanent' : 'Temporary';
                lockIndicator.title = `Locked by: ${lockedBy}\nLock type: ${lockTypeText}\n${lockedSince ? 'Since: ' + lockedSince : ''}`;
                lockIndicator.innerHTML = `
                    <i class="fas fa-${isPermanent ? 'lock' : 'lock-open'}"></i>
                    <span>${lockText} by ${this.escapeHtml(lockedBy)}</span>
                `;
                tabEditBtn.parentElement.appendChild(lockIndicator);
            }
        }
    },

    /**
     * Intercept edit button click and check lock status
     * @param {string} facetType - The facet type
     * @param {number} objectId - The object ID
     * @param {function} onEditAllowed - Callback when edit is allowed (navigate to edit page)
     */
    async interceptEditButton(facetType, objectId, onEditAllowed) {
        try {
            const lockInfo = await window.BUDG_API_SERVICE.checkLock(facetType, objectId);
            if (lockInfo && lockInfo.status !== 'no_lock' && lockInfo.status !== 'locked_by_self') {
                const lockedBy = lockInfo.lockedByName || 'another user';
                const isPermanent = lockInfo.isPermanent || false;
                const message = `The object is currently locked by ${lockedBy}. ${isPermanent ? 'This is a permanent lock.' : 'Please try again later.'}`;
                alert(message);
                return false; // Prevent navigation
            }
            // Lock check passed, allow edit
            if (onEditAllowed) {
                onEditAllowed();
            }
            return true;
        } catch (error) {
            console.error('Failed to check lock:', error);
            // On error, allow edit to continue
            if (onEditAllowed) {
                onEditAllowed();
            }
            return true;
        }
    },

    /**
     * Escape HTML to prevent XSS
     * @param {string} str - String to escape
     * @returns {string} Escaped string
     */
    escapeHtml(str) {
        if (str == null) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
};

