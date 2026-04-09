/**
 * Global Error Handler for Authentication
 * Intercepts and handles authentication errors globally
 */

(function() {
    'use strict';
    
    // Store original fetch
    const originalFetch = window.fetch;
    
    // Track authentication state — only true after a successful /api/me response.
    // The "Session Ended" modal must ONLY fire when a user who WAS authenticated
    // later receives a 401 (session expired). It must NOT fire for guest/anonymous
    // users who never had a session.
    let isAuthenticated = false;
    let authCheckInProgress = false;
    
    // Track if logout modal has been shown to prevent multiple modals
    let logoutModalShown = false;
    
    // Called by header.js once /api/me succeeds — marks this page-load as authenticated.
    window.markUserAuthenticated = function() {
        isAuthenticated = true;
    };
    
    // Override fetch to handle auth errors globally
    window.fetch = async function(url, options = {}) {
        const response = await originalFetch(url, options);
        
        // Handle authentication errors for API endpoints
        if (response.status === 401 && url.includes('/api/')) {
            // Auth check endpoints — always silence (never trigger the modal)
            if (url.includes('/api/me') || url.includes('/api/refresh')) {
                return response;
            }
            
            // Only show "Session Ended" if this user WAS authenticated on this page load.
            // If isAuthenticated is false the user is a guest/anonymous visitor —
            // the 401 is expected and must not produce any modal.
            if (isAuthenticated && !logoutModalShown) {
                // On public pages (index, search) the session expiry should NOT force the
                // user to the login page — they can remain as a guest. Just reload so the
                // page re-initialises in full guest mode without any stale authenticated state.
                const currentPath = (window.location.pathname || '').toLowerCase();
                const isPublicPage = currentPath === '/' ||
                                     currentPath === '/index.html' ||
                                     currentPath === '/search.html';

                if (isPublicPage) {
                    // Silently reload → auth fails from scratch → guest view shown
                    logoutModalShown = true; // prevent repeat triggers before reload
                    window.location.reload();
                } else if (window.showLogoutMessage) {
                    logoutModalShown = true;
                    window.showLogoutMessage('Your session has expired. You have been logged out.');
                }
            }
        }
        
        return response;
    };
    
    // Global error handler for unhandled promise rejections
    window.addEventListener('unhandledrejection', function(event) {
        const error = event.reason;
        
        // Suppress authentication-related errors
        if (error && typeof error === 'object') {
            if (error.message && (
                error.message.includes('401') ||
                error.message.includes('Unauthorized') ||
                error.message.includes('authentication')
            )) {
                event.preventDefault();
                return;
            }
        }
        
        // Suppress network errors that might be auth-related
        if (error && typeof error === 'string') {
            if (error.includes('401') || error.includes('Unauthorized')) {
                event.preventDefault();
                return;
            }
        }
    });
    
    // Global error handler for JavaScript errors
    window.addEventListener('error', function(event) {
        const error = event.error;
        
        // Suppress authentication-related errors
        if (error && error.message) {
            if (error.message.includes('401') || 
                error.message.includes('Unauthorized') ||
                error.message.includes('authentication')) {
                event.preventDefault();
                return;
            }
        }
    });
    
})();
