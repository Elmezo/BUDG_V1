/**
 * Lock Initialization Helper
 * Provides a reusable function to initialize locks for any facet edit page
 */

window.LockInitHelper = {
    /**
     * Initialize lock for an edit page
     * @param {string} facetType - The facet type (e.g., 'dataset', 'system', 'glossary')
     * @param {number} objectId - The object ID
     * @param {string} viewPath - The path to redirect to on cancel (e.g., 'dataset', 'system')
     * @returns {Promise<boolean>} - True if lock was acquired, false otherwise
     */
    async initializeLock(facetType, objectId, viewPath) {
        if (!objectId) {
            console.warn('No object ID provided for lock initialization');
            return false;
        }

        // Initialize lock manager
        const lockManager = new window.LockManager(facetType, objectId);
        window.currentLockManager = lockManager;

        // Setup auto-release on page unload
        lockManager.setupBeforeUnload();

        // Check lock status
        const lockStatus = await lockManager.checkLockStatus();
        const status = lockStatus?.status || 'no_lock';

        // Handle lock conflicts
        if (status === 'locked_by_other') {
            const lockedBy = lockStatus.lockedByName || 'another user';
            alert(`This ${facetType} is being edited by ${lockedBy} and is temporarily locked. Please try again later.`);
            // Ensure we redirect to view page after alert is closed
            setTimeout(() => {
                // Fix view path for legal-entity and other facets
                const viewPathFixed = viewPath === 'legal-entity' ? 'LegalEntity' : viewPath;
                window.location.href = `/view/${viewPathFixed}/${viewPathFixed}.html?id=${objectId}`;
            }, 100);
            return false;
        } else if (status === 'permanently_locked') {
            const isSuperAdmin = await lockManager.checkIsSuperAdmin();
            if (!isSuperAdmin) {
                const lockedBy = lockStatus.lockedByName || 'an administrator';
                alert(`This ${facetType} has a permanent lock by ${lockedBy}. Only administrators can edit it.`);
                // Ensure we redirect to view page after alert is closed
                setTimeout(() => {
                    // Fix view path for legal-entity and other facets
                    const viewPathFixed = viewPath === 'legal-entity' ? 'LegalEntity' : viewPath;
                    window.location.href = `/view/${viewPathFixed}/${viewPathFixed}.html?id=${objectId}`;
                }, 100);
                return false;
            }
        }

        // Acquire lock
        const lockResult = await lockManager.acquireLock(false);
        if (!lockResult || !lockResult.success) {
            // Check if we have details about who locked it
            if (lockResult && lockResult.lockedBy) {
                const lockedBy = lockResult.lockedBy;
                if (lockResult.isPermanent) {
                    alert(`The object is currently locked by ${lockedBy}. Try again later.`);
                } else {
                    alert(`The object is currently locked by ${lockedBy}. Try again later.`);
                }
            } else {
                // More detailed error message
                const errorMsg = lockResult?.error || 'Failed to acquire lock. Please try again.';
                console.error('[LockInitHelper] Lock acquisition failed:', lockResult);
                alert(errorMsg);
            }
            // Fix view path for legal-entity and other facets
            const viewPathFixed = viewPath === 'legal-entity' ? 'LegalEntity' : viewPath;
            window.location.href = `/view/${viewPathFixed}/${viewPathFixed}.html?id=${objectId}`;
            return false;
        }

        // Initialize lock UI if available
        if (window.LockUI) {
            const lockUI = new window.LockUI(facetType, objectId, '#editActionsBar');
            window.currentLockUI = lockUI;
            await lockUI.initialize(lockManager);
        }

        // Update global lock count in header after successful acquisition
        if (window.globalLockUI && typeof window.globalLockUI.updateLockCount === 'function') {
            try {
                await window.globalLockUI.updateLockCount();
            } catch (e) {
                console.warn('[LockInitHelper] Failed to update global lock count:', e);
            }
        }

        return true;
    },

    /**
     * Release lock (to be called on save/cancel/close)
     * Always attempts to release the lock to ensure it's not left dangling
     */
    async releaseLock() {
        console.log('🔓 LockInitHelper.releaseLock called');
        try {
            if (window.currentLockManager) {
                console.log('🔓 Releasing lock via currentLockManager...');
                await window.currentLockManager.releaseLock();
                console.log('🔓 Lock release completed via currentLockManager');
            } else {
                console.log('🔓 No currentLockManager found, skipping release');
            }
        } catch (error) {
            console.error('🔓 Error in LockInitHelper.releaseLock:', error);
            // Don't throw - we don't want to block navigation if lock release fails
        }
    }
};

