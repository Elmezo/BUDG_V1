/**
 * Initialization Guards - Prevent duplicate initialization
 * 
 * This module provides guards to ensure components are initialized only once.
 * Each component checks its guard before initializing.
 */

(function() {
    'use strict';
    
    // Initialization flags
    const initFlags = {
        filter: false,
        lock: false,
        modules: false,
        segments: false,
        sidebar: false,
        search: false
    };
    
    /**
     * Check if a component is already initialized
     * @param {string} component - Component name (e.g., 'filter', 'lock')
     * @returns {boolean} true if already initialized
     */
    function isInitialized(component) {
        return initFlags[component] === true;
    }
    
    /**
     * Mark a component as initialized
     * @param {string} component - Component name
     */
    function markInitialized(component) {
        if (initFlags[component]) {
            console.warn(`[InitGuard] ${component} already initialized - skipping duplicate init`);
            return false;
        }
        initFlags[component] = true;
        console.log(`[InitGuard] ${component} initialized`);
        return true;
    }
    
    /**
     * Reset a component's initialization flag (for testing/re-init)
     * @param {string} component - Component name
     */
    function resetInit(component) {
        initFlags[component] = false;
        console.log(`[InitGuard] ${component} reset`);
    }
    
    /**
     * Reset all initialization flags
     */
    function resetAll() {
        for (const key in initFlags) {
            initFlags[key] = false;
        }
        console.log('[InitGuard] All components reset');
    }
    
    /**
     * Safely add event listener (removes existing before adding)
     * @param {Element} element - DOM element
     * @param {string} event - Event name
     * @param {Function} handler - Event handler
     * @param {Object} options - Event listener options
     */
    function safeAddEventListener(element, event, handler, options = {}) {
        if (!element) return;
        
        // Remove existing listener first (if any)
        element.removeEventListener(event, handler, options);
        
        // Add new listener
        element.addEventListener(event, handler, options);
    }
    
    // Export to window
    if (typeof window !== 'undefined') {
        window.InitGuard = {
            isInitialized,
            markInitialized,
            resetInit,
            resetAll,
            safeAddEventListener
        };
    }
})();

