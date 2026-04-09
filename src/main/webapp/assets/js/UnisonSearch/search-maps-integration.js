/**
 * Maps Integration for Search Page
 * Loads maps.html content into search.html when Maps link is clicked in header
 */

(function() {
    'use strict';

    let mapsContentLoaded = false;
    let mapsContent = null;

    /**
     * Initialize maps integration
     */
    function initMapsIntegration() {
        // Wait for header to be loaded
        const checkHeader = setInterval(() => {
            const mapsLink = document.querySelector('header a[href*="search.html?view=maps"], header a[href*="view=maps"]');
            if (mapsLink) {
                clearInterval(checkHeader);
                setupMapsLink(mapsLink);
                setupSearchLink();
            }
        }, 100);

        // Stop checking after 5 seconds
        setTimeout(() => clearInterval(checkHeader), 5000);
    }

    /**
     * Setup Maps link to load content in search page
     */
    function setupMapsLink(mapsLink) {
        mapsLink.addEventListener('click', async (e) => {
            // If not on search.html, let default navigation happen (href will handle it)
            if (!window.location.pathname.includes('search.html')) {
                return; // Let browser navigate to /search.html?view=maps
            }

            // If already on search.html, prevent default and load maps
            e.preventDefault();
            e.stopPropagation();
            await loadMapsContent();
        });
    }

    /**
     * Setup Search link to restore search view when maps is active
     */
    function setupSearchLink() {
        // Find the Search nav link (href="/search.html" without view=maps)
        const searchLinks = document.querySelectorAll('header a.nav-link[href="/search.html"], header a.nav-link[href*="search.html"]');
        searchLinks.forEach(link => {
            // Skip the maps link
            if (link.href && link.href.includes('view=maps')) return;
            
            link.addEventListener('click', (e) => {
                // Only intercept if we are currently on the maps view
                const mapsActive = document.querySelector('.maps-main-content');
                if (mapsActive && window.location.pathname.includes('search.html')) {
                    e.preventDefault();
                    e.stopPropagation();
                    window.restoreSearchView();
                }
                // Otherwise let default navigation happen
            });
        });
    }

    /**
     * Load maps.html content
     */
    async function loadMapsContent() {
        const contentContainer = document.querySelector('.content-container');
        if (!contentContainer) {
            console.error('[MAPS-INTEGRATION] Content container not found');
            return;
        }

        // Hide existing children (content-header, content-main) instead of destroying them
        Array.from(contentContainer.children).forEach(child => {
            if (!child.classList.contains('maps-main-content')) {
                child.style.display = 'none';
                child.setAttribute('data-hidden-by-maps', 'true');
            }
        });

        // Load CSS for maps if not already loaded
        if (!document.querySelector('link[href*="map-view.css"]')) {
            const mapCss = document.createElement('link');
            mapCss.rel = 'stylesheet';
            mapCss.href = '/assets/css/map-view.css';
            document.head.appendChild(mapCss);
        }

        // Load maps content if not already loaded
        if (!mapsContentLoaded) {
            try {
                const response = await fetch('/maps.html');
                if (!response.ok) {
                    throw new Error('Failed to load maps.html');
                }
                const html = await response.text();
                
                // Extract main content from maps.html
                const parser = new DOMParser();
                const doc = parser.parseFromString(html, 'text/html');
                const mapsMain = doc.querySelector('.maps-main-content');
                
                if (mapsMain) {
                    mapsContent = mapsMain.cloneNode(true);
                    mapsContentLoaded = true;
                } else {
                    throw new Error('Maps content not found in maps.html');
                }
            } catch (error) {
                console.error('[MAPS-INTEGRATION] Error loading maps content:', error);
                 // Restore hidden children on error
                 contentContainer.querySelectorAll('[data-hidden-by-maps]').forEach(child => {
                     child.style.display = '';
                     child.removeAttribute('data-hidden-by-maps');
                 });
                  return;

            }
        }
       // Remove any previously appended maps content, then add fresh
        const existingMaps = contentContainer.querySelector('.maps-main-content');
        if (existingMaps) {
            existingMaps.remove();
        }
        if (mapsContent) {
            contentContainer.appendChild(mapsContent.cloneNode(true));
          }

        // Load Cytoscape if not already loaded
        if (typeof cytoscape === 'undefined') {
            await loadScript('/assets/js/cytoscape.min.js');
        }

        // Load dagre if not already loaded
        if (typeof dagre === 'undefined') {
            await loadScript('/assets/js/dagre.min.js');
        }

        // Load maps.js if not already loaded
        if (!window.mapsJsLoaded) {
            await loadScript('/assets/js/maps.js');
            window.mapsJsLoaded = true;
        }

        // Wait for scripts to load and initialize maps
        const initMapsAfterLoad = () => {
            const mapCanvas = document.getElementById('mapNetworkCanvas');
            if (mapCanvas && typeof cytoscape !== 'undefined') {
                // Call initMaps directly if available (exposed by maps.js)
                if (typeof window.initMaps === 'function') {
                    console.log('[MAPS-INTEGRATION] Initializing maps...');
                    window.initMaps();
                } else {
                    // Fallback: trigger DOMContentLoaded event
                    console.log('[MAPS-INTEGRATION] Triggering DOMContentLoaded event');
                    const event = new Event('DOMContentLoaded', { bubbles: true });
                    document.dispatchEvent(event);
                }
                
                // Verify initialization after a delay
                setTimeout(() => {
                    const canvas = document.getElementById('mapNetworkCanvas');
                    // Check if Cytoscape instance was created (maps.js creates it)
                    if (canvas && !canvas.querySelector('canvas') && typeof cytoscape !== 'undefined') {
                        console.log('[MAPS-INTEGRATION] Maps not initialized, retrying...');
                        if (typeof window.initMaps === 'function') {
                            window.initMaps();
                        } else {
                            const event2 = new Event('DOMContentLoaded', { bubbles: true });
                            document.dispatchEvent(event2);
                        }
                    }
                }, 1000);
            } else if (!mapCanvas) {
                console.error('[MAPS-INTEGRATION] Map canvas not found');
            } else if (typeof cytoscape === 'undefined') {
                console.log('[MAPS-INTEGRATION] Waiting for Cytoscape to load...');
                // Retry after a delay
                setTimeout(initMapsAfterLoad, 300);
            }
        };

        // Start initialization after scripts are loaded
        setTimeout(initMapsAfterLoad, 600);

        // Update URL without reload
        if (window.history && window.history.pushState) {
            window.history.pushState({ view: 'maps' }, 'Maps - BUDG', '/search.html?view=maps');
        }
    }

    /**
     * Load a script dynamically
     */
    function loadScript(src) {
        return new Promise((resolve, reject) => {
            // Check if script already exists
            const existing = document.querySelector(`script[src="${src}"]`);
            if (existing) {
                resolve();
                return;
            }

            const script = document.createElement('script');
            script.src = src;
            script.onload = resolve;
            script.onerror = reject;
            document.body.appendChild(script);
        });
    }

    /**
     * Restore search view (hide maps, show search content)
     */
    window.restoreSearchView = function() {
        const contentContainer = document.querySelector('.content-container');
        if (!contentContainer) return;

        const mapsMain = contentContainer.querySelector('.maps-main-content');
        if (mapsMain) {
            mapsMain.remove();
        }
        // Restore all children that were hidden when maps loaded
        contentContainer.querySelectorAll('[data-hidden-by-maps]').forEach(child => {
            child.style.display = '';
            child.removeAttribute('data-hidden-by-maps');
        });
        // Update URL
        if (window.history && window.history.pushState) {
            window.history.pushState({ view: 'search' }, 'Search - BUDG', '/search.html');
        }
    };

    // Handle browser back/forward buttons
    window.addEventListener('popstate', (e) => {
        if (e.state && e.state.view === 'maps') {
            loadMapsContent();
        } else {
            restoreSearchView();
        }
    });

    // Check URL parameter on page load - load maps automatically
    function checkAndLoadMaps() {
        if (window.location.search.includes('view=maps')) {
            // Wait a bit for page to fully load
            setTimeout(() => {
                loadMapsContent();
            }, 800);
        }
    }

    // Check on page load
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', checkAndLoadMaps);
    } else {
        checkAndLoadMaps();
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initMapsIntegration);
    } else {
        initMapsIntegration();
    }

    // Also listen for headerReady event
    document.addEventListener('headerReady', () => {
        setTimeout(initMapsIntegration, 200);
    });

})();

