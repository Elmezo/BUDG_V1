

class LockManager {
    constructor(facetType, objectId) {
        this.facetType = facetType;
        this.objectId = objectId;
        this.currentLockId = null;
        this.isLockAcquired = false;
        this.lockStatus = null;
    }

    /**
     * Check current lock status
     */
    async checkLockStatus() {
        try {
            this.lockStatus = await window.BUDG_API_SERVICE.checkLock(this.facetType, this.objectId);
            return this.lockStatus;
        } catch (error) {
            console.error('Failed to check lock status:', error);
            return { status: 'no_lock', locked: false };
        }
    }

    /**
     * Acquire a lock on the object
     * @param {boolean} isPermanent - Whether to create a permanent lock
     * @returns {Promise<{success: boolean, lockedBy?: string, isPermanent?: boolean, error?: string}>} - Result object with success status and error details
     */
    async acquireLock(isPermanent = false) {
        try {
            if (window.__BUDG_DEBUG__) console.log('🔒 Attempting to acquire lock for:', this.facetType, 'ID:', this.objectId);

            // First check current lock status
            const lockStatus = await this.checkLockStatus();
            const status = lockStatus?.status || 'no_lock';
            if (window.__BUDG_DEBUG__) console.log('🔒 Current lock status:', status, lockStatus);

            if (status === 'locked_by_other') {
                // Locked by another user
                console.warn('⚠️ Object is locked by another user:', lockStatus.lockedByName);
                return {
                    success: false,
                    lockedBy: lockStatus.lockedByName || 'another user',
                    isPermanent: lockStatus.isPermanent || false
                };
            } else if (status === 'permanently_locked') {
                // Check if user is super admin
                console.log('🔒 Permanent lock detected, checking if user is super admin...');
                const isSuperAdmin = await this.checkIsSuperAdmin();
                console.log('🔒 Is super admin:', isSuperAdmin);
                if (!isSuperAdmin) {
                    console.warn('⚠️ User is not super admin, cannot edit permanently locked object');
                    return {
                        success: false,
                        lockedBy: lockStatus.lockedByName || 'an administrator',
                        isPermanent: true
                    };
                }
                // Super admin can edit - acquire lock (will update existing)
                console.log('✅ Super admin can proceed with permanently locked object');
            }

            // Acquire or refresh lock
            if (window.__BUDG_DEBUG__) console.log('🔒 Calling API to acquire lock...');
            const result = await window.BUDG_API_SERVICE.acquireLock(this.facetType, this.objectId, isPermanent);
            if (window.__BUDG_DEBUG__) console.log('🔒 API response:', result);

            if (result && result.success) {
                this.currentLockId = result.lockId;
                this.isLockAcquired = true;
                if (window.__BUDG_DEBUG__) console.log('✅ Lock acquired successfully:', this.currentLockId);
                return { success: true };
            }

            // Check if API returned error details with lockedBy
            if (result && result.lockedBy) {
                console.error('❌ Lock acquisition failed - locked by:', result.lockedBy);
                return {
                    success: false,
                    lockedBy: result.lockedBy,
                    isPermanent: result.isPermanent || false,
                    error: result.error
                };
            }

            console.error('❌ Lock acquisition failed. Result:', result);
            return {
                success: false,
                error: result?.error || 'Failed to acquire lock'
            };
        } catch (error) {
            console.error('❌ Exception during lock acquisition:', error);
            console.error('Error details:', {
                message: error.message,
                status: error.status,
                body: error.body
            });
            
            // Try to extract error details from the error object
            let errorDetails = { success: false, error: 'Failed to acquire lock' };
            if (error.body) {
                try {
                    const errorBody = typeof error.body === 'string' ? JSON.parse(error.body) : error.body;
                    if (errorBody.lockedBy) {
                        errorDetails.lockedBy = errorBody.lockedBy;
                        errorDetails.isPermanent = errorBody.isPermanent || false;
                    }
                    if (errorBody.error) {
                        errorDetails.error = errorBody.error;
                    }
                } catch (e) {
                    // Ignore parse errors
                }
            }
            
            return errorDetails;
        }
    }

    /**
     * Release the lock
     * @param {boolean} force - If true, release even permanent locks
     */
    async releaseLock(force = false) {
        console.log('🔓 releaseLock called, isLockAcquired:', this.isLockAcquired, 'force:', force);

        const clearLocalState = () => {
            this.isLockAcquired = false;
            this.currentLockId = null;
        };

        try {
            // Check current lock status
            const lockStatus = await this.checkLockStatus();
            console.log('🔓 Lock status before release:', lockStatus);

            // Skip if no lock exists on this object
            if (!lockStatus || lockStatus.status === 'no_lock') {
                console.log('🔓 No lock exists on server, skipping release');
                clearLocalState();
                return;
            }

            // Only release if the lock is owned by current user
            // or if we think we have the lock (isLockAcquired)
            // Backend returns 'locked_by_self' when current user owns the lock
            if (lockStatus.status === 'locked_by_self' || this.isLockAcquired) {
                console.log('🔓 Calling API to release lock for:', this.facetType, this.objectId);
                const result = await window.BUDG_API_SERVICE.releaseLock(this.facetType, this.objectId);
                if (result && result.success === false) {
                    console.warn('🔓 Lock release API returned success: false:', result?.error || 'Unknown error');
                } else {
                    console.log('🔓 Lock released successfully');
                }
                clearLocalState();
            } else {
                console.log('🔓 Lock not owned by current user (status:', lockStatus.status, '), not releasing');
                clearLocalState();
            }
        } catch (error) {
            console.error('🔓 Failed to release lock:', error);
            
            console.warn('🔓 Lock release failed, but continuing:', error.message || error.body?.error || error);
            
            // Still mark as released locally so we don't block the user
            this.isLockAcquired = false;
            this.currentLockId = null;
        }
    }

    /**
     * Check if current user is super admin
     */
    async checkIsSuperAdmin() {
        try {
            const meResp = await fetch('/api/me', { method: 'GET', credentials: 'include' });
            if (meResp.ok) {
                const me = await meResp.json();
                const role = (me.role || '').toString().toLowerCase();
                return role === 'admin' || role === 'super admin' || role === 'super-admin' || role === 'super_admin' || role === 'suber admin';
            }
        } catch (error) {
            console.error('Failed to check admin status:', error);
        }
        return false;
    }

    /**
     * Setup handlers to release the lock when the browser tab is closed
     * or the user navigates away.
     *
     * ═══════════════════════════════════════════════════════════════════
     * STATE MACHINE
     * ═══════════════════════════════════════════════════════════════════
     *
     *   ┌───────┐  beforeunload   ┌───────────┐  pagehide   ┌──────────┐
     *   │ idle  │ ──────────────► │ releasing  │ ──────────► │ released │
     *   └───────┘                 └───────────┘              └──────────┘
     *       ▲                          │
     *       │    timeout (page alive)  │
     *       └──────────────────────────┘
     *          re-acquire lock
     *
     *  idle      – lock is held, user is editing normally
     *  releasing – beforeunload fired, beacon sent, waiting to confirm
     *  released  – pagehide/unload fired, page is actually going away
     *
     * ═══════════════════════════════════════════════════════════════════
     * WHY THIS DESIGN
     * ═══════════════════════════════════════════════════════════════════
     *
     *  • beforeunload is the MOST RELIABLE event for tab close across
     *    all browsers. We send the beacon here immediately.
     *  • BUT beforeunload fires BEFORE the "Leave page?" dialog. If the
     *    user clicks "Stay", the page survives. A 1.5 s timeout detects
     *    this and re-acquires the lock.
     *  • pagehide fires only when the page is ACTUALLY being discarded.
     *    It cancels the re-acquire timer so we don't re-acquire after
     *    a genuine close.
     *  • visibilitychange is NOT used — it fires on tab switch /
     *    minimize and would release the lock while the user is editing.
     *  • The state resets to 'idle' after re-acquire, so repeated
     *    close-then-Stay cycles work correctly.
     *
     * ═══════════════════════════════════════════════════════════════════
     * AUTHENTICATION
     * ═══════════════════════════════════════════════════════════════════
     *
     *  sendBeacon is same-origin, so the browser automatically attaches
     *  all cookies (including the httpOnly ACCESS_TOKEN). The server's
     *  AuthFilter reads the cookie — no localStorage token needed.
     *  We include facetType + objectId in the body for the server to
     *  identify which lock to release.
     */
    setupBeforeUnload() {
        const facetType = this.facetType;
        const objectId = this.objectId;
        const self = this;

        // ── State ──────────────────────────────────────────────────────
        let state = 'idle';          // 'idle' | 'releasing' | 'released'
        let reacquireTimer = null;   // setTimeout handle for re-acquire

        // ────────────────────────────────────────────────────────────────
        // fireBeacon — send the lock-release beacon (fire-and-forget)
        // Returns true if the beacon was queued successfully.
        // ────────────────────────────────────────────────────────────────
        function fireBeacon() {
            if (!self.isLockAcquired) {
                console.log('🔓 [fireBeacon] Lock not held locally — skipping');
                return false;
            }

            console.log('🔓 [fireBeacon] Sending release beacon for:', facetType, objectId);

            try {
                // sendBeacon sends cookies automatically (same-origin).
                // Body carries facetType + objectId for the server endpoint.
                if (navigator.sendBeacon) {
                    const url = `/api/lock/${facetType}/${objectId}/release-beacon`;
                    const body = new Blob(
                        [JSON.stringify({ facetType: facetType, objectId: objectId })],
                        { type: 'application/json' }
                    );
                    const ok = navigator.sendBeacon(url, body);
                    console.log('🔓 [fireBeacon] sendBeacon queued:', ok);
                    if (ok) return true;
                }
            } catch (e) {
                console.warn('🔓 [fireBeacon] sendBeacon error:', e);
            }

            // Fallback: synchronous XHR (may be blocked by modern browsers
            // during unload, but harmless to try).
            try {
                console.log('🔓 [fireBeacon] Falling back to sync XHR');
                const xhr = new XMLHttpRequest();
                xhr.open('DELETE', `/api/lock/${facetType}/${objectId}`, false);
                xhr.setRequestHeader('Content-Type', 'application/json');
                xhr.send();
                console.log('🔓 [fireBeacon] Sync XHR status:', xhr.status);
                return xhr.status >= 200 && xhr.status < 300;
            } catch (e) {
                console.warn('🔓 [fireBeacon] Sync XHR error:', e);
                return false;
            }
        }

        // ────────────────────────────────────────────────────────────────
        // reacquireLock — called when the page survived (user clicked
        // "Stay"). Re-acquires the lock and resets state to 'idle'.
        // ────────────────────────────────────────────────────────────────
        async function reacquireLock() {
            console.log('🔓 [reacquire] Page still alive — re-acquiring lock');
            try {
                const result = await window.BUDG_API_SERVICE.acquireLock(
                    facetType, objectId, false
                );
                if (result && result.success) {
                    console.log('🔓 [reacquire] Lock re-acquired successfully');
                } else {
                    console.warn('🔓 [reacquire] Re-acquire returned:', result);
                }
            } catch (e) {
                console.warn('🔓 [reacquire] Failed to re-acquire:', e);
            }
        }

        // ════════════════════════════════════════════════════════════════
        // EVENT: beforeunload
        // ════════════════════════════════════════════════════════════════
        // Fires FIRST on every tab close / navigation / refresh.
        // If a "Leave page?" dialog is shown, it fires BEFORE the dialog.
        // Most reliable event across all desktop browsers.
        // ════════════════════════════════════════════════════════════════
        window.addEventListener('beforeunload', () => {
            // Only act from 'idle' state — ignore if already releasing/released
            if (state !== 'idle') {
                console.log('🔓 [beforeunload] Ignoring — state is:', state);
                return;
            }
            if (!self.isLockAcquired) {
                console.log('🔓 [beforeunload] Lock not held — skipping');
                return;
            }

            // Transition: idle → releasing
            state = 'releasing';
            console.log('🔓 [beforeunload] State: idle → releasing');

            // Send the release beacon NOW (most reliable moment)
            fireBeacon();

            // Schedule re-acquire: if the page is still alive after 1.5 s,
            // the user clicked "Stay" on the dirty-form dialog.
            // We re-acquire the lock and reset to 'idle'.
            if (reacquireTimer) clearTimeout(reacquireTimer);
            reacquireTimer = setTimeout(async () => {
                reacquireTimer = null;
                if (state === 'releasing') {
                    // Page survived! User clicked "Stay".
                    // Reset state to allow future close attempts.
                    state = 'idle';
                    console.log('🔓 [beforeunload-timer] State: releasing → idle');
                    await reacquireLock();
                }
                // If state is 'released', pagehide already fired — do nothing.
            }, 1500);
        });

        // ════════════════════════════════════════════════════════════════
        // EVENT: pagehide
        // ════════════════════════════════════════════════════════════════
        // Fires when the page is ACTUALLY being discarded (tab close,
        // navigation, refresh). Does NOT fire when a "Leave page?"
        // dialog is merely shown. This is the authoritative signal.
        // ════════════════════════════════════════════════════════════════
        window.addEventListener('pagehide', () => {
            // Cancel any pending re-acquire — page is really going away
            if (reacquireTimer) {
                clearTimeout(reacquireTimer);
                reacquireTimer = null;
            }

            if (state === 'released') {
                console.log('🔓 [pagehide] Already released — skipping');
                return;
            }
            if (!self.isLockAcquired) {
                console.log('🔓 [pagehide] Lock not held — skipping');
                state = 'released';
                return;
            }

            // If beforeunload already sent the beacon, just confirm state.
            // If beforeunload didn't fire (rare edge case), send beacon now.
            if (state === 'idle') {
                console.log('🔓 [pagehide] Beacon not yet sent — sending now');
                fireBeacon();
            } else {
                console.log('🔓 [pagehide] Beacon already sent via beforeunload');
            }

            // Final transition → released
            state = 'released';
            console.log('🔓 [pagehide] State: → released (page discarded)');
        });

        // ════════════════════════════════════════════════════════════════
        // EVENT: unload
        // ════════════════════════════════════════════════════════════════
        // Last-resort fallback for very old browsers that do not fire
        // pagehide reliably. Same logic as pagehide.
        // ════════════════════════════════════════════════════════════════
        window.addEventListener('unload', () => {
            if (reacquireTimer) {
                clearTimeout(reacquireTimer);
                reacquireTimer = null;
            }

            if (state === 'released' || !self.isLockAcquired) {
                return;
            }

            if (state === 'idle') {
                console.log('🔓 [unload] Beacon not yet sent — sending now');
                fireBeacon();
            }

            state = 'released';
            console.log('🔓 [unload] State: → released');
        });
    }
}

// Export for use in other scripts
window.LockManager = LockManager;


