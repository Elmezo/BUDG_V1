/**
 * Centralized Loading Manager
 * Handles all loading states with full page blur overlay
 * 
 * Features:
 * - Full page blur effect during loading
 * - Prevents user interaction during loading
 * - Supports multiple concurrent operations
 * - Automatic cleanup on errors
 * - Debug logging for tracking
 */

let loadingOverlay = null;
let activeLoadingCount = 0; // Track multiple concurrent operations
let showTimeout = null; // Delay showing to prevent flicker

/**
 * Initialize loading overlay
 * Creates the overlay element and appends to body
 */
function initLoadingOverlay() {
    if (loadingOverlay) {
        return; // Already initialized
    }
    
    // Create overlay element
    loadingOverlay = document.createElement('div');
    loadingOverlay.className = 'loading-overlay';
    loadingOverlay.setAttribute('aria-hidden', 'true');
    loadingOverlay.setAttribute('role', 'progressbar');
    loadingOverlay.setAttribute('aria-label', 'Loading content');
    
    // Create spinner
    const spinner = document.createElement('div');
    spinner.className = 'loading-spinner';
    loadingOverlay.appendChild(spinner);
    
    // Append to body
    document.body.appendChild(loadingOverlay);
}

/**
 * Show loading indicator
 * @param {string} source - Source of loading operation (for debugging)
 * @param {number} delay - Delay in ms before showing (default: 100ms to prevent flicker)
 */
function showLoading(source = 'unknown', delay = 100) {
    if (!loadingOverlay) {
        initLoadingOverlay();
    }
    
    activeLoadingCount++;
    const timestamp = new Date().toISOString().substr(11, 12);
    
    // Clear any existing timeout
    if (showTimeout) {
        clearTimeout(showTimeout);
    }
    
    // Small delay to prevent flicker on fast loads
    showTimeout = setTimeout(() => {
        if (activeLoadingCount > 0 && loadingOverlay) {
            loadingOverlay.classList.add('active');
            loadingOverlay.setAttribute('aria-hidden', 'false');
            
            // Prevent body scroll
            document.body.style.overflow = 'hidden';
        }
    }, delay);
}

/**
 * Hide loading indicator
 * @param {string} source - Source of loading operation (for debugging)
 */
function hideLoading(source = 'unknown') {
    activeLoadingCount = Math.max(0, activeLoadingCount - 1);
    
    // Clear show timeout if hiding before it shows
    if (showTimeout && activeLoadingCount === 0) {
        clearTimeout(showTimeout);
        showTimeout = null;
    }
    
    // Only hide if no active operations
    if (activeLoadingCount === 0 && loadingOverlay) {
        loadingOverlay.classList.remove('active');
        loadingOverlay.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';
    }
}

/**
 * Force hide all loading (emergency cleanup)
 * Use this when an error occurs or to reset state
 */
function forceHideLoading() {
    const previousCount = activeLoadingCount;
    activeLoadingCount = 0;
    
    // Clear timeout
    if (showTimeout) {
        clearTimeout(showTimeout);
        showTimeout = null;
    }
    
    if (loadingOverlay) {
        loadingOverlay.classList.remove('active');
        loadingOverlay.setAttribute('aria-hidden', 'true');
        document.body.style.overflow = '';
    }
    
    const timestamp = new Date().toISOString().substr(11, 12);
    console.warn(`[Loading Manager] ${timestamp} 🔴 Force hide all loading (was: ${previousCount}, now: 0)`);
}

/**
 * Get current loading state
 * @returns {Object} Current state information
 */
function getLoadingState() {
    return {
        isActive: activeLoadingCount > 0,
        count: activeLoadingCount,
        overlayVisible: loadingOverlay?.classList.contains('active') || false
    };
}

/**
 * Wrap an async function with loading indicator
 * @param {Function} fn - Async function to wrap
 * @param {string} source - Source identifier
 * @returns {Function} Wrapped function
 */
function withLoading(fn, source) {
    return async function(...args) {
        try {
            showLoading(source);
            const result = await fn.apply(this, args);
            return result;
        } catch (error) {
            console.error(`[Loading Manager] Error in ${source}:`, error);
            throw error;
        } finally {
            hideLoading(source);
        }
    };
}

/**
 * Reset loading manager (for testing/debugging)
 */
function resetLoadingManager() {
    forceHideLoading();
    if (loadingOverlay && loadingOverlay.parentNode) {
        loadingOverlay.parentNode.removeChild(loadingOverlay);
    }
    loadingOverlay = null;
    activeLoadingCount = 0;
    showTimeout = null;
}

// Initialize on page load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initLoadingOverlay);
} else {
    // DOM already loaded
    initLoadingOverlay();
}

// Cleanup on page unload
window.addEventListener('beforeunload', () => {
    forceHideLoading();
});

// Emergency cleanup on errors
window.addEventListener('error', (event) => {
    console.error('[Loading Manager] Global error detected, forcing cleanup:', event.error);
    forceHideLoading();
});

// Cleanup on unhandled promise rejections
window.addEventListener('unhandledrejection', (event) => {
    console.error('[Loading Manager] Unhandled promise rejection, forcing cleanup:', event.reason);
    forceHideLoading();
});

// Export functions to window for global access
window.showLoading = showLoading;
window.hideLoading = hideLoading;
window.forceHideLoading = forceHideLoading;
window.getLoadingState = getLoadingState;
window.withLoading = withLoading;
window.resetLoadingManager = resetLoadingManager;

